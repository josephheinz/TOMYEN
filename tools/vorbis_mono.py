"""A small pure-Python Ogg Vorbis encoder for short mono sound effects.

Why: the Homebrew ffmpeg here has no libvorbis, and ffmpeg's built-in "vorbis" encoder only writes stereo. Minecraft
only positions and attenuates mono sounds, so import_gun_sounds.py encodes with this instead.

It writes a plain, spec-conformant Vorbis I stream (checked against ffmpeg's decoder and JOrbis, which Minecraft uses):
  - one block size (512), so no window switching; 5.8 ms hops keep pre-echo on gunshot attacks negligible;
  - floor 1 with just its two implicit posts, both at the top of the table: a flat floor of exactly 1.0;
  - residue 1 in partitions of 8 coefficients. Each partition picks a class, i.e. a quantiser step from a power-of-two
    ladder, so the step follows the local level (block floating point per partition). The step keeps each
    partition's quantisation noise about SNR_DB under its own level; quiet partitions become class 0 and cost a few
    bits. Every class has its own scalar codebook with Huffman lengths built from this file's statistics.
It is not a perceptual encoder, so files are a few times larger than libvorbis output at similar quality; for short
effects that is a few tens of kilobytes."""
import math
import struct

BLOCK = 512
HALF = BLOCK // 2
PART = 8
K = 63  # quantised values run -K..K in every class book
SNR_DB = 36.0
# Finest step on the ladder (decoded spectrum units, about PCM units): around 16-bit resolution.
MIN_EXP = -17
# With this scale the decoder's inverse MDCT and overlap-add give back the input at unity gain (checked against
# ffmpeg and JOrbis, which agree).
SCALE = 2.0 / HALF


# ---------------------------------------------------------------------------------------------------------- bit I/O

class Bits:
    """Vorbis packs bits least-significant first."""

    def __init__(self):
        self.out = bytearray()
        self.acc = 0
        self.n = 0

    def write(self, value, bits):
        if bits == 0:
            return
        value &= (1 << bits) - 1
        self.acc |= value << self.n
        self.n += bits
        while self.n >= 8:
            self.out.append(self.acc & 0xFF)
            self.acc >>= 8
            self.n -= 8

    def code(self, word, length):
        """A Huffman codeword: the decoder walks the tree from the codeword's top bit."""
        for i in range(length - 1, -1, -1):
            self.write((word >> i) & 1, 1)

    def bytes(self):
        out = bytearray(self.out)
        if self.n:
            out.append(self.acc & 0xFF)
        return bytes(out)


def ilog(v):
    return v.bit_length() if v > 0 else 0


def pack_float(x):
    """Vorbis float32: 21-bit mantissa, 10-bit biased exponent, sign."""
    sign = 0
    if x < 0:
        sign, x = 1, -x
    if x == 0:
        return 0
    exp = 788
    while x < (1 << 20):
        x *= 2
        exp -= 1
    while x >= (1 << 21):
        x /= 2
        exp += 1
    mant = int(round(x))
    if mant >= (1 << 21):
        mant //= 2
        exp += 1
    return (sign << 31) | (exp << 21) | mant


# --------------------------------------------------------------------------------------------------------- huffman

def huffman_lengths(counts):
    """Code lengths for the used entries (count > 0); unused entries get 0. At least two entries must be used."""
    import heapq
    heap = [(c, i, [i]) for i, c in enumerate(counts) if c > 0]
    lengths = [0] * len(counts)
    heapq.heapify(heap)
    tie = len(counts)
    while len(heap) > 1:
        c1, _, s1 = heapq.heappop(heap)
        c2, _, s2 = heapq.heappop(heap)
        for i in s1 + s2:
            lengths[i] += 1
        heapq.heappush(heap, (c1 + c2, tie, s1 + s2))
        tie += 1
    if max(lengths) > 32:
        raise ValueError("codeword too long")
    return lengths


def make_words(lengths):
    """Codewords in the order the decoder assigns them (libvorbis _make_words)."""
    marker = [0] * 33
    words = [0] * len(lengths)
    for i, length in enumerate(lengths):
        if length <= 0:
            continue
        entry = marker[length]
        if length < 32 and (entry >> length):
            raise ValueError("overpopulated code")
        words[i] = entry
        for j in range(length, 0, -1):
            if marker[j] & 1:
                if j == 1:
                    marker[1] += 1
                else:
                    marker[j] = marker[j - 1] << 1
                break
            marker[j] += 1
        for j in range(length + 1, 33):
            if (marker[j] >> 1) == entry:
                entry = marker[j]
                marker[j] = marker[j - 1] << 1
            else:
                break
    return words


class Book:
    def __init__(self, counts, step=None):
        self.lengths = huffman_lengths(counts)
        self.words = make_words(self.lengths)
        self.step = step  # None: a scalar (class) book with no lookup

    def write_header(self, b):
        n = len(self.lengths)
        b.write(0x564342, 24)
        b.write(1, 16)  # dimensions
        b.write(n, 24)
        b.write(0, 1)  # not ordered
        sparse = any(l == 0 for l in self.lengths)
        b.write(1 if sparse else 0, 1)
        for l in self.lengths:
            if sparse:
                b.write(1 if l else 0, 1)
                if l:
                    b.write(l - 1, 5)
            else:
                b.write(l - 1, 5)
        if self.step is None:
            b.write(0, 4)
            return
        b.write(1, 4)  # lookup type 1: value = minimum + multiplicand * delta
        b.write(pack_float(-K * self.step), 32)
        b.write(pack_float(self.step), 32)
        bits = ilog(2 * K)
        b.write(bits - 1, 4)
        b.write(0, 1)  # not sequence_p
        for m in range(2 * K + 1):
            b.write(m, bits)

    def put(self, b, entry):
        b.code(self.words[entry], self.lengths[entry])


# ------------------------------------------------------------------------------------------------------------ mdct

WINDOW = [math.sin(math.pi / 2 * math.sin((i + 0.5) / BLOCK * math.pi) ** 2) for i in range(BLOCK)]
COS = [[math.cos(2 * math.pi / BLOCK * (n + 0.5 + BLOCK / 4) * (k + 0.5)) * SCALE for n in range(BLOCK)]
       for k in range(HALF)]


def mdct(block):
    xs = [x * w for x, w in zip(block, WINDOW)]
    return [sum(map(float.__mul__, row, xs)) for row in COS]


# ------------------------------------------------------------------------------------------------------------- ogg

def crc_table():
    table = []
    for i in range(256):
        r = i << 24
        for _ in range(8):
            r = ((r << 1) ^ 0x04C11DB7) if r & 0x80000000 else (r << 1)
        table.append(r & 0xFFFFFFFF)
    return table


CRC = crc_table()


def page(segments, body, granule, serial, seq, flags):
    head = struct.pack("<4sBBqIIIB", b"OggS", 0, flags, granule, serial, seq, 0, len(segments)) + bytes(segments)
    data = bytearray(head + body)
    crc = 0
    for byte in data:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ CRC[((crc >> 24) ^ byte) & 0xFF]
    data[22:26] = struct.pack("<I", crc)
    return bytes(data)


def paginate(groups, serial, max_body=4096):
    """groups: lists of (packet, granule after it); each group starts a fresh page and ends its last one.
    Packets may span pages (continuation flag), as Ogg allows."""
    pages = []
    seq = 0
    first = True
    for g, packets in enumerate(groups):
        last_group = g == len(groups) - 1
        segs, body, granule, cont = [], b"", -1, False
        for k, (packet, after) in enumerate(packets):
            n = len(packet)
            lacing = [255] * (n // 255) + [n % 255]
            pos = 0
            for i, lv in enumerate(lacing):
                if len(segs) == 255 or (len(body) >= max_body and i == 0):
                    flags = (0x02 if first else 0) | (0x01 if cont else 0)
                    pages.append(page(segs, body, granule, serial, seq, flags))
                    seq += 1
                    first = False
                    cont = i > 0
                    segs, body, granule = [], b"", -1
                segs.append(lv)
                body += packet[pos:pos + lv]
                pos += lv
            granule = after
        flags = (0x02 if first else 0) | (0x01 if cont else 0) | (0x04 if last_group else 0)
        pages.append(page(segs, body, granule, serial, seq, flags))
        seq += 1
        first = False
    return pages


# ---------------------------------------------------------------------------------------------------------- encoder

def encode(samples, path, rate=44100, vendor="my-mod vorbis_mono"):
    """Writes float samples (about -1..1, mono) to `path` as Ogg Vorbis."""
    n = len(samples)
    blocks = (n + HALF - 1) // HALF + 1  # block k covers [(k-1)*HALF, (k+1)*HALF); packet k returns [(k-1)*HALF, k*HALF)
    padded = [0.0] * HALF + list(samples) + [0.0] * (blocks * HALF + HALF - n)
    floor_abs = 2.0 ** MIN_EXP

    # Pass 1: transform, choose classes, quantise.
    frames = []
    used = set()
    for k in range(blocks):
        spec = mdct(padded[k * HALF:k * HALF + BLOCK])
        parts = []
        for p in range(0, HALF, PART):
            v = spec[p:p + PART]
            peak = max(abs(x) for x in v)
            if peak < floor_abs:
                parts.append((None, None))
                continue
            rms = math.sqrt(sum(x * x for x in v) / PART)
            step = max(rms * math.sqrt(12) * 10 ** (-SNR_DB / 20), peak / (K + 0.49), floor_abs)
            exp = math.ceil(math.log2(step))
            q = [int(round(x / 2.0 ** exp)) for x in v]
            if not any(q):
                parts.append((None, None))
                continue
            parts.append((exp, q))
            used.add(exp)
        frames.append(parts)

    exps = sorted(used)
    classes = len(exps) + 1  # class 0: silent partition
    if classes > 64:
        raise ValueError("too many classes")
    cls_of = {e: i + 1 for i, e in enumerate(exps)}

    # Statistics for the Huffman codes.
    class_counts = [1] * classes
    value_counts = {e: [0] * (2 * K + 1) for e in exps}
    for parts in frames:
        if all(e is None for e, _ in parts):
            continue
        for e, q in parts:
            class_counts[0 if e is None else cls_of[e]] += 1
            if e is not None:
                for x in q:
                    value_counts[e][x + K] += 1
    for e in exps:
        for x in (-1, 0, 1):
            value_counts[e][x + K] += 1
    classbook = Book(class_counts)
    books = [Book(value_counts[e], 2.0 ** e) for e in exps]

    # Headers.
    ident = Bits()
    ident.write(1, 8)
    for c in b"vorbis":
        ident.write(c, 8)
    ident.write(0, 32)
    ident.write(1, 8)
    ident.write(rate, 32)
    ident.write(0, 32)
    ident.write(0, 32)
    ident.write(0, 32)
    ident.write(ilog(BLOCK) - 1, 4)
    ident.write(ilog(BLOCK) - 1, 4)
    ident.write(1, 1)

    comment = Bits()
    comment.write(3, 8)
    for c in b"vorbis":
        comment.write(c, 8)
    comment.write(len(vendor), 32)
    for c in vendor.encode():
        comment.write(c, 8)
    comment.write(0, 32)
    comment.write(1, 1)

    setup = Bits()
    setup.write(5, 8)
    for c in b"vorbis":
        setup.write(c, 8)
    allbooks = [classbook] + books
    setup.write(len(allbooks) - 1, 8)
    for bk in allbooks:
        bk.write_header(setup)
    setup.write(0, 6)  # time domain transforms: one placeholder
    setup.write(0, 16)
    setup.write(0, 6)  # one floor
    setup.write(1, 16)  # floor type 1
    setup.write(0, 5)  # no partitions: just the two implicit posts
    setup.write(0, 2)  # multiplier 1
    setup.write(8, 4)  # rangebits
    setup.write(0, 6)  # one residue
    setup.write(1, 16)  # residue type 1
    setup.write(0, 24)
    setup.write(HALF, 24)
    setup.write(PART - 1, 24)
    setup.write(classes - 1, 6)
    setup.write(0, 8)  # classbook
    for c in range(classes):
        setup.write(0 if c == 0 else 1, 3)  # cascade: pass 0 only
        setup.write(0, 1)
    for c in range(1, classes):
        setup.write(c, 8)  # book c for class c, pass 0
    setup.write(0, 6)  # one mapping
    setup.write(0, 16)  # mapping type 0
    setup.write(0, 1)  # one submap
    setup.write(0, 1)  # no coupling
    setup.write(0, 2)
    setup.write(0, 8)  # submap: time (unused)
    setup.write(0, 8)  # floor 0
    setup.write(0, 8)  # residue 0
    setup.write(0, 6)  # one mode
    setup.write(0, 1)  # blockflag: short
    setup.write(0, 16)
    setup.write(0, 16)
    setup.write(0, 8)  # mapping 0
    setup.write(1, 1)

    # Audio packets.
    packets = []
    for parts in frames:
        b = Bits()
        b.write(0, 1)  # audio packet; one mode, so no mode bits
        if all(e is None for e, _ in parts):
            b.write(0, 1)  # floor unused: the whole frame is silent
        else:
            b.write(1, 1)
            b.write(255, 8)
            b.write(255, 8)
            for e, q in parts:
                c = 0 if e is None else cls_of[e]
                classbook.put(b, c)
                if c:
                    book = books[c - 1]
                    for x in q:
                        book.put(b, x + K)
        packets.append(b.bytes())

    # Pages: identification alone, then comment + setup, then audio; the last page's granule trims the padding.
    audio = [(p, min(n, k * HALF)) for k, p in enumerate(packets)]
    audio[-1] = (audio[-1][0], n)
    groups = [[(ident.bytes(), 0)], [(comment.bytes(), 0), (setup.bytes(), 0)], audio]
    out = paginate(groups, 0x6D796D64)
    with open(path, "wb") as f:
        f.write(b"".join(out))
