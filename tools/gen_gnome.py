"""Builds the Gnome army: the gnome and Gnome Captain models (the cube layouts in TROOP and CAPTAIN, written into
client/gnome/GnomeModel.java between the GENERATED markers), their entity textures (three coat colours for the troops,
the captain's own, and a glazed-ceramic statue twin of each), the Garden Gnome block (its block model is the troop's
statue, cube for cube, posed and scaled into the block; its texture is the blue troop's statue texture), the two spawn
egg sprites, and the item and block models, block states and item definitions. On the data side it writes the loot
tables (the gnome, the captain and the block), and merges the lang entries and the gnomes' sounds (vanilla ones,
re-pitched) into en_us.json and sounds.json.

The textures are painted at twice the model's pixel density (128x128 for the model's 64x64 UV space), like painted
ceramic garden gnomes: smooth ramps from a small palette, a regular wave through the beard, no noise or dithering; a
rerun gives the same files. The statue textures are the same paint under a glaze: paler, with a soft specular band
across every face and a darker rim, so a gnome visibly sets into ceramic as it freezes at dawn.

Both models are checked for z-fighting (tools/coplanar.py) at rest and through their walk, march, carry, panic, poke
and freeze poses, and the block model is checked too (tools/zfight.py's element check). Their extents are printed.

With --preview DIR it also renders, into DIR, preview_gnome.png: a troop from the front, three-quarters and side at
rest; mid-waddle; marching; carrying a crop; panicking; the captain from the front and three-quarters, marching and
poking; the statue twin; the head up close; the Garden Gnome block (rendered from its JSON, as the game reads it); and
the spawn eggs. The render uses the same cubes, pivots and UV layout as the Java model, and a Python twin of
GnomeModel.pose.

Usage: python3 tools/gen_gnome.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(path):
    """8-bit RGBA, non-interlaced only."""
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride, out, prev = w * 4, [], bytearray(w * 4)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out += [tuple(line[x * 4:x * 4 + 4]) for x in range(w)]
        prev = line
    return w, h, out


CLEAR = (0, 0, 0, 0)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


# ---------------------------------------------------------------------------------------------- the models
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size). Model space is the game's: y
# down, the front toward -z, the ground at y 24. Cube sizes are whole pixels (the texture is laid out in whole pixels);
# positions can be fractional. No cube is mirrored, so the block model can reuse every cube's UVs as they are.
#
# Both models share one part tree (the troop's captain-only parts are empty) and one UV layout (the union of their
# cube keys), so one Java model class poses either. Rotations here are the rest pose; GnomeModel.pose adds to them.
S = 2                   # texels per model pixel
TEX_W, TEX_H = 64, 64   # the model's UV space; the images are S times that
GROUND = 24.0


def parts(captain):
    hat = [("hat_base", (-3.5, -1.5, -3.0), (7, 3, 6))]
    if captain:
        hat.append(("hat_band", (-4.0, -0.25, -3.5), (8, 1, 7)))
    # The cone, narrowing a pixel a step: the captain's steps are taller, and its tip ends in a finer point.
    if captain:
        seg1, seg2, seg3 = ("cap_seg1", (-3.0, -3.0, -2.5), (6, 3, 5)), ("cap_seg2", (-2.5, -3.0, -2.0), (5, 3, 4)), \
            ("cap_seg3", (-1.5, -3.0, -1.5), (3, 3, 3))
        tip = [("hat_tip", (-1.0, -2.0, -1.0), (2, 2, 2)), ("cap_point", (-0.5, -4.0, -0.5), (1, 2, 1))]
        bends = (-0.03, -0.06, -0.08, -0.12)
    else:
        seg1, seg2, seg3 = ("hat_seg1", (-3.0, -2.0, -2.5), (6, 2, 5)), ("hat_seg2", (-2.5, -2.0, -2.0), (5, 2, 4)), \
            ("hat_seg3", (-1.5, -2.0, -1.5), (3, 2, 3))
        tip = [("hat_tip", (-1.0, -2.0, -1.0), (2, 2, 2))]
        bends = (-0.06, -0.14, -0.2, -0.28)
    arm_r = [("sleeve", (-2.0, -0.5, -1.0), (2, 4, 2)), ("cuff", (-2.5, 2.0, -1.5), (3, 1, 3)), ("hand", (-2.0, 3.5, -1.25), (2, 2, 2))]
    arm_l = [("sleeve", (0.0, -0.5, -1.0), (2, 4, 2)), ("cuff", (-0.5, 2.0, -1.5), (3, 1, 3)), ("hand", (0.0, 3.5, -1.25), (2, 2, 2))]
    if captain:
        arm_r.append(("epaulette", (-2.5, -1.0, -1.5), (3, 1, 3)))
        arm_l.append(("epaulette", (-0.5, -1.0, -1.5), (3, 1, 3)))
    coat = "cap_coat" if captain else "coat"
    return [
        ("right_leg", None, (-1.5, 19.5, 0.0), (0.0, 0.0, 0.0), [
            ("trouser", (-1.0, 0.0, -1.0), (2, 3, 2)),
            ("boot", (-1.5, 2.5, -2.5), (3, 2, 4))]),
        ("right_toe", "right_leg", (0.0, 3.0, -2.5), (-0.55, 0.0, 0.0), [
            ("toe", (-1.0, -0.5, -1.5), (2, 1, 2))]),
        ("left_leg", None, (1.5, 19.5, 0.0), (0.0, 0.0, 0.0), [
            ("trouser", (-1.0, 0.0, -1.0), (2, 3, 2)),
            ("boot", (-1.5, 2.5, -2.5), (3, 2, 4))]),
        ("left_toe", "left_leg", (0.0, 3.0, -2.5), (-0.55, 0.0, 0.0), [
            ("toe", (-1.0, -0.5, -1.5), (2, 1, 2))]),
        ("body", None, (0.0, 19.0, 0.0), (0.0, 0.0, 0.0), [
            (coat, (-3.5, -7.0, -2.5), (7, 7, 5)),
            ("hem", (-4.0, -1.5, -3.0), (8, 2, 6)),
            ("belt", (-4.0, -2.5, -3.0), (8, 1, 6)),
            ("buckle", (-1.0, -3.0, -3.25), (2, 2, 1))]),
        ("head", "body", (0.0, -7.0, 0.0), (0.0, 0.0, 0.0), [
            ("face", (-3.0, -6.0, -2.75), (6, 6, 5)),
            ("nose", (-1.5, -3.5, -4.0), (3, 2, 2)),
            ("brows", (-2.5, -4.5, -3.0), (5, 1, 1)),
            ("beard", (-4.0, -2.0, -3.5), (8, 3, 2)),
            ("beard_chest", (-2.5, 1.0, -3.5), (5, 2, 2)),
            ("ear", (-4.0, -3.5, -0.5), (1, 2, 1)),
            ("ear", (3.0, -3.5, -0.5), (1, 2, 1))]),
        ("moustache_r", "head", (-0.5, -1.75, -3.6), (0.0, 0.0, -0.42), [
            ("moustache", (-3.0, -0.5, -0.5), (3, 1, 1))]),
        ("moustache_l", "head", (0.5, -1.75, -3.6), (0.0, 0.0, 0.42), [
            ("moustache", (0.0, -0.5, -0.5), (3, 1, 1))]),
        ("beard_tip", "head", (0.0, 2.5, -2.25), (0.0, 0.0, 0.0), [
            ("beard_tip", (-1.5, 0.0, -1.0), (3, 2, 2))]),
        ("hat", "head", (0.0, -6.0, 0.0), (0.0, 0.0, 0.0), hat),
        ("hat_1", "hat", (0.0, -1.5, 0.0), (bends[0], 0.0, 0.0), [seg1]),
        ("hat_2", "hat_1", (0.0, seg1[1][1], 0.0), (bends[1], 0.0, 0.05), [seg2]),
        ("hat_3", "hat_2", (0.0, seg2[1][1], 0.0), (bends[2], 0.0, 0.04), [seg3]),
        ("hat_tip", "hat_3", (0.0, seg3[1][1], 0.0), (bends[3], 0.0, 0.08), tip),
        ("right_arm", "body", (-3.5, -6.5, 0.0), (0.0, 0.0, 0.1), arm_r),
        ("left_arm", "body", (3.5, -6.5, 0.0), (0.0, 0.0, -0.1), arm_l),
        ("pitchfork", "right_arm", (-1.0, 4.5, -0.25), (0.0, 0.0, 0.0), [
            ("shaft", (-0.5, -16.0, -0.5), (1, 20, 1)),
            ("crossbar", (-2.5, -13.0, -0.75), (5, 1, 1)),
            ("tine", (-2.5, -16.0, -0.5), (1, 3, 1)),
            ("tine", (1.5, -16.0, -0.5), (1, 3, 1))] if captain else []),
        ("pennant", "pitchfork", (0.0, -12.0, 0.5), (0.0, 0.0, 0.0), [
            ("pennant", (0.0, 0.0, 0.0), (0, 4, 6))] if captain else []),
    ]


TROOP = parts(False)
CAPTAIN = parts(True)


def tree(model):
    t = {}
    for name, parent, _, _, _ in model:
        t.setdefault(parent or "root", []).append(name)
    return t


def pack():
    """Texture offsets (in model pixels) for each cube key, shelf-packed into the texture: the union of both models."""
    sizes = {}
    for model in (TROOP, CAPTAIN):
        for _, _, _, _, cubes in model:
            for key, _, size in cubes:
                assert sizes.setdefault(key, size) == size, key
                assert all(float(c).is_integer() for c in size), key
    # Skyline packing: tallest first, each at the lowest spot along the top edge of what's placed so far.
    order = sorted(sizes, key=lambda k: (-(sizes[k][2] + sizes[k][1]), -(sizes[k][0] + sizes[k][2]), k))
    sky = [0] * TEX_W
    offs = {}
    for k in order:
        w, h, d = sizes[k]
        fw, fh = 2 * (w + d), d + h
        best = min(range(TEX_W - fw + 1), key=lambda x: (max(sky[x:x + fw]), x))
        y = max(sky[best:best + fw])
        offs[k] = (best, y)
        for x in range(best, best + fw):
            sky[x] = y + fh
    assert max(sky) <= TEX_H, f"texture too small: {max(sky)}"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


def faces(u, v, w, h, d):
    """A model cube's faces in the texture (model pixels), as (u, v, width, height), laid out as the game lays them
    out. 'top' and 'bottom' are as seen in the world; in both the front edge is the last row. In 'right' (the cube's
    -x side) the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, key, painters):
    """painters: face name (or 'default') -> f(x, y, fw, fh) -> colour, an RGBA tuple, or None for clear. x, y and the
    face size are in texels."""
    (u, v), (w, h, d) = CUBES[key]
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name, painters.get("default"))
        if f is None:
            continue
        W, H = fw * S, fh * S
        for y in range(H):
            for x in range(W):
                c = f(x, y, W, H)
                canvas.set(fu * S + x, fv * S + y, rgba(c) if c is not None and len(c) == 3 else (c or CLEAR))


# ---------------------------------------------------------------------------------------------- palettes
HAT = [(240, 104, 86), (220, 70, 58), (190, 46, 44), (150, 32, 36)]
BEARD = [(252, 250, 244), (238, 235, 227), (219, 214, 204), (192, 186, 178)]
SKIN = [(255, 224, 198), (247, 202, 174), (228, 172, 144), (198, 140, 116)]
NOSE = [(255, 196, 176), (243, 156, 138), (218, 118, 106), (180, 88, 82)]
BLUSH = (240, 158, 146)
EYE = (46, 34, 42)
GLINT = (248, 250, 255)
COATS = {
    "blue": [(112, 158, 224), (82, 126, 198), (60, 98, 168), (44, 72, 130)],
    "green": [(128, 182, 106), (98, 152, 80), (72, 120, 60), (52, 90, 46)],
    "ochre": [(242, 196, 96), (222, 164, 64), (192, 132, 46), (152, 100, 34)],
}
CAPTAIN_COAT = [(160, 110, 200), (132, 84, 174), (104, 62, 144), (76, 44, 108)]
GOLD = [(255, 238, 156), (248, 208, 96), (218, 166, 58), (170, 122, 38)]
BELT = [(136, 96, 62), (112, 76, 48), (88, 60, 38), (66, 44, 28)]
TROUSERS = [(154, 128, 92), (130, 106, 74), (106, 86, 60), (84, 66, 46)]
BOOTS = [(116, 80, 56), (92, 62, 44), (72, 48, 34), (52, 36, 26)]
SOLE = (40, 30, 24)
WOOD = [(182, 140, 92), (156, 116, 74), (128, 94, 60)]
IRON = [(226, 230, 234), (190, 196, 202), (150, 156, 164)]
GLAZE = (250, 248, 242)


# ---------------------------------------------------------------------------------------------- painters
def vramp(cols, top=0.1, bottom=0.55):
    """A plain vertical ramp down a face."""
    return lambda x, y, w, h: ramp(cols, top + (bottom - top) * y / max(1, h - 1))


def solid(c):
    return lambda x, y, w, h: c


def beard_wave(x, y, w, h, top=0.0, bottom=0.35, phase=0):
    """White beard: a gentle ramp down the face, with soft, regular strands waving down it (every fourth column a
    shade darker, the strands swaying a texel either way as they go)."""
    c = ramp(BEARD, top + (bottom - top) * y / max(1, h - 1))
    sway = (0, 1, 1, 0, -1, -1)[(y + phase) % 6]
    k = (x + sway) % 4
    return shade(c, 0.95) if k == 0 else shade(c, 1.02) if k == 2 else c


def round_face(cols, cx, cy, r, light=(-0.35, -0.45)):
    """A face painted as if bulging: lighter toward a highlight up and to the left of the centre, darker at the rim."""
    def f(x, y, w, h):
        dx, dy = (x + 0.5 - cx(w)) / r(w, h), (y + 0.5 - cy(h)) / r(w, h)
        d = math.sqrt((dx - light[0]) ** 2 + (dy - light[1]) ** 2)
        return ramp(cols, 0.05 + 0.6 * min(1.0, d / 1.5))
    return f


def over(f, pixels):
    """f with some texels replaced: pixels maps (x, y) -> colour."""
    return lambda x, y, w, h: pixels.get((x, y), f(x, y, w, h))


def star(cx, cy, col, edge):
    """A small five-point star (5x5 texels) centred at cx, cy."""
    pts = {(0, -2): col, (-1, -1): edge, (0, -1): col, (1, -1): edge, (-2, 0): col, (-1, 0): col, (0, 0): col, (1, 0): col, (2, 0): col,
           (-1, 1): col, (0, 1): col, (1, 1): col, (-1, 2): col, (1, 2): col}
    return {(cx + dx, cy + dy): c for (dx, dy), c in pts.items()}


def paint_gnome(tex, coat_cols, captain):
    P = lambda key, painters: paint(tex, key, painters)
    trim = GOLD if captain else [lerp(c, (255, 255, 255), 0.3) for c in coat_cols]

    # The head: skin, two bright little eyes under the brows, rosy cheeks, white hair round the back under the hat.
    def face_front(x, y, w, h):
        # w, h = 12, 12 texels; the hat covers rows 0-2, the brows rows 3-4, the beard from row 8.
        c = ramp(SKIN, 0.15 + 0.35 * y / (h - 1))
        for ex in (1, 9):
            if ex <= x <= ex + 1 and 5 <= y <= 7:
                return GLINT if (x, y) == (ex + (1 if ex == 1 else 0), 5) else EYE
        if y == 7 and x in (0, 11) or y == 8 and x in (0, 1, 10, 11):
            return lerp(c, BLUSH, 0.6)
        return c

    def face_back(x, y, w, h):
        if y >= 3:
            return beard_wave(x, y, w, h, 0.1, 0.5)
        return ramp(SKIN, 0.4)

    def face_right(x, y, w, h):
        return beard_wave(x, y, w, h, 0.1, 0.5) if x < 5 and y >= 3 else ramp(SKIN, 0.2 + 0.35 * y / (h - 1))

    def face_left(x, y, w, h):
        return beard_wave(x, y, w, h, 0.1, 0.5) if x >= 5 and y >= 3 else ramp(SKIN, 0.2 + 0.35 * y / (h - 1))
    P("face", {"front": face_front, "back": face_back, "right": face_right, "left": face_left, "top": solid(SKIN[2]),
               "bottom": solid(SKIN[3])})

    nose = round_face(NOSE, lambda w: w / 2, lambda h: h / 2, lambda w, h: max(w, h) / 2)
    P("nose", {"default": lambda x, y, w, h: ramp(NOSE, 0.35 + 0.35 * y / max(1, h - 1)), "front": over(nose, {(1, 1): (255, 236, 226), (2, 1): (255, 222, 208)}),
               "top": solid(NOSE[1]), "bottom": solid(NOSE[3])})
    P("brows", {"default": lambda x, y, w, h: ramp(BEARD, 0.05 + 0.4 * y / max(1, h - 1)), "bottom": solid(BEARD[3]), "top": solid(BEARD[0])})
    P("beard", {"default": beard_wave, "front": lambda x, y, w, h: beard_wave(x, y, w, h, 0.0, 0.3),
                "top": lambda x, y, w, h: beard_wave(x, y, w, h, 0.0, 0.1), "bottom": solid(BEARD[3])})
    P("beard_chest", {"default": lambda x, y, w, h: beard_wave(x, y, w, h, 0.3, 0.6, 2), "bottom": solid(BEARD[3]), "top": solid(BEARD[1])})
    P("beard_tip", {"default": lambda x, y, w, h: beard_wave(x, y, w, h, 0.55, 0.9, 4), "bottom": solid(BEARD[3]), "top": solid(BEARD[2])})
    P("moustache", {"default": lambda x, y, w, h: BEARD[0] if y == 0 else BEARD[1], "bottom": solid(BEARD[2]), "top": solid(BEARD[0])})
    P("ear", {"default": vramp(SKIN, 0.2, 0.6), "front": lambda x, y, w, h: lerp(ramp(SKIN, 0.3), BLUSH, 0.45) if y >= 1 else ramp(SKIN, 0.2),
              "top": solid(SKIN[1]), "bottom": solid(SKIN[3])})

    # The hat: one red ramp running the whole way up the cone (light at the tip, darker toward the brim), a darker
    # band round the brim edge; the captain's has a gold band and a gold star.
    def hat_face(top, bottom, edge=False):
        def f(x, y, w, h):
            c = ramp(HAT, top + (bottom - top) * y / max(1, h - 1))
            return shade(c, 0.84) if edge and y >= h - 2 else c
        return f
    base_front = hat_face(0.45, 0.75, edge=not captain)
    if captain:
        base_front = over(base_front, star(7, 2, GOLD[1], GOLD[2]))
    P("hat_base", {"default": hat_face(0.45, 0.75, edge=not captain), "front": base_front, "top": solid(HAT[2]), "bottom": solid(HAT[3])})
    P("hat_seg1", {"default": hat_face(0.36, 0.45), "top": solid(HAT[1]), "bottom": solid(HAT[3])})
    P("hat_seg2", {"default": hat_face(0.26, 0.36), "top": solid(HAT[1]), "bottom": solid(HAT[2])})
    P("hat_seg3", {"default": hat_face(0.14, 0.26), "top": solid(HAT[0]), "bottom": solid(HAT[2])})
    P("hat_tip", {"default": hat_face(0.04, 0.14), "top": solid(HAT[0]), "bottom": solid(HAT[2])})
    P("cap_seg1", {"default": hat_face(0.36, 0.45), "top": solid(HAT[1]), "bottom": solid(HAT[3])})
    P("cap_seg2", {"default": hat_face(0.26, 0.36), "top": solid(HAT[1]), "bottom": solid(HAT[2])})
    P("cap_seg3", {"default": hat_face(0.14, 0.26), "top": solid(HAT[0]), "bottom": solid(HAT[2])})
    P("cap_point", {"default": hat_face(0.0, 0.04), "top": solid(HAT[0]), "bottom": solid(HAT[1])})
    P("hat_band", {"default": lambda x, y, w, h: GOLD[1] if y == 0 else GOLD[2], "top": solid(GOLD[2]), "bottom": solid(GOLD[3])})

    # The coat: a ramp darkening toward the hem, a placket down the front (the captain's double-breasted, with two
    # rows of gold buttons), a seam down the back; the hem's bottom edge piped in a lighter tone (gold for the captain).
    def coat_front(x, y, w, h):
        c = ramp(coat_cols, 0.1 + 0.45 * y / (h - 1))
        if captain:
            if x in (3, 10) and y in (6, 9, 12):
                return GOLD[1]
            if x in (3, 10) and y in (7, 10):
                return GOLD[3]
            return c
        if x == 7:
            return shade(c, 0.82)
        if x == 8 and y in (9, 12):
            return trim[1]
        return c

    def coat_back(x, y, w, h):
        c = ramp(coat_cols, 0.15 + 0.45 * y / (h - 1))
        return shade(c, 0.86) if x in (6, 7) and y >= h - 5 else c
    P("cap_coat" if captain else "coat", {"default": vramp(coat_cols, 0.15, 0.6), "front": coat_front, "back": coat_back,
                                          "top": solid(coat_cols[1]), "bottom": solid(coat_cols[3])})

    def hem(x, y, w, h):
        if y == h - 1:
            return trim[1]
        if y == h - 2:
            return trim[2] if captain else shade(trim[1], 0.9)
        return ramp(coat_cols, 0.5 + 0.2 * y / max(1, h - 1))
    P("hem", {"default": hem, "top": solid(coat_cols[2]), "bottom": solid(coat_cols[3])})

    def belt(x, y, w, h):
        c = ramp(BELT, 0.2 + 0.4 * y / max(1, h - 1))
        return shade(c, 1.18) if y == 0 and x % 3 == 1 else c
    P("belt", {"default": belt, "top": solid(BELT[1]), "bottom": solid(BELT[3])})

    def buckle(x, y, w, h):
        if 1 <= x <= w - 2 and 1 <= y <= h - 2:
            return BELT[2]
        return GOLD[0] if x + y <= 2 else GOLD[1] if x + y < w + h - 4 else GOLD[2]
    P("buckle", {"default": solid(GOLD[2]), "front": buckle, "top": solid(GOLD[1]), "bottom": solid(GOLD[3])})

    # Arms: coat sleeves, a lighter (or gold) turned-back cuff, skin mittens with a thumb.
    P("sleeve", {"default": vramp(coat_cols, 0.1, 0.5), "top": solid(coat_cols[1]), "bottom": solid(coat_cols[3])})
    P("cuff", {"default": lambda x, y, w, h: trim[1] if y == 0 else trim[2], "top": solid(trim[1]), "bottom": solid(trim[3] if len(trim) > 3 else trim[2])})

    def mitten(x, y, w, h):
        c = ramp(SKIN, 0.15 + 0.45 * y / max(1, h - 1))
        return shade(c, 0.86) if x == 1 and y >= 1 else c
    P("hand", {"default": vramp(SKIN, 0.15, 0.6), "front": mitten, "top": solid(SKIN[1]), "bottom": solid(SKIN[3])})
    P("epaulette", {"default": lambda x, y, w, h: GOLD[1] if y == 0 else GOLD[2] if x % 2 == 0 else GOLD[3],
                    "top": lambda x, y, w, h: GOLD[0] if min(x, y, w - 1 - x, h - 1 - y) >= 1 else GOLD[1], "bottom": solid(GOLD[3])})

    # Legs: trousers, then stout boots with a dark sole and curled-up toes.
    P("trouser", {"default": vramp(TROUSERS, 0.15, 0.6), "top": solid(TROUSERS[1]), "bottom": solid(TROUSERS[3])})

    def boot(x, y, w, h):
        return SOLE if y == h - 1 else ramp(BOOTS, 0.1 + 0.4 * y / max(1, h - 1))
    P("boot", {"default": boot, "top": lambda x, y, w, h: BOOTS[1] if min(x, y, w - 1 - x, h - 1 - y) == 0 else BOOTS[2],
               "bottom": solid(SOLE)})
    P("toe", {"default": vramp(BOOTS, 0.0, 0.3), "top": solid(BOOTS[0]), "bottom": solid(SOLE)})

    # The captain's pitchfork: an ash shaft, iron crossbar and tines, and a red swallow-tailed pennant with a gold edge.
    P("shaft", {"default": lambda x, y, w, h: WOOD[[0, 1, 1, 2, 1, 0][y % 6]] if x == 0 else WOOD[1], "top": solid(IRON[0]), "bottom": solid(WOOD[2])})
    P("crossbar", {"default": lambda x, y, w, h: IRON[0] if y == 0 else IRON[1], "bottom": solid(IRON[2])})
    P("tine", {"default": lambda x, y, w, h: IRON[0] if y < 2 else IRON[1] if y < 4 else IRON[2], "top": solid(IRON[0])})

    def pennant(x, y, w, h):
        # w = 12 texels along the flag (away from the shaft at x 0 on the left face), h = 8. A swallow-tail notch
        # cut into the far end; a gold edge along the shaft and the top.
        far = w - 1 - x
        if far < 4 and abs(y - (h - 1) / 2) < (4 - far) * 0.8:
            return None
        if x == 0 or y == 0:
            return GOLD[1]
        return ramp(HAT, 0.3 + 0.3 * y / (h - 1))

    def pennant_mirrored(x, y, w, h):
        return pennant(w - 1 - x, y, w, h)
    P("pennant", {"left": pennant, "right": pennant_mirrored})


def glaze(tex):
    """The statue twin: the same paint under a ceramic glaze. Paler and a touch cooler; the top edge of every side face
    catching the light in a bright line (the glaze pooling thin over an edge); the up-facing faces brighter; and on every
    face big enough for one, a soft round specular spot up and to the left. The eyes' glints go (glazed eyes are painted
    dots)."""
    out = Canvas(tex.w, tex.h)
    for key, ((u, v), (w, h, d)) in CUBES.items():
        for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
            W, H = fw * S, fh * S
            for y in range(H):
                for x in range(W):
                    c = tex.get(fu * S + x, fv * S + y)
                    if c[3] == 0:
                        continue
                    rgb = c[:3]
                    if rgb == GLINT:
                        rgb = EYE
                    rgb = lerp(rgb, GLAZE, 0.12)
                    if name == "top":
                        rgb = lerp(rgb, GLAZE, 0.14)
                    elif name != "bottom" and y == 0 and H >= 4:
                        rgb = lerp(rgb, GLAZE, 0.3)
                    if W >= 6 and H >= 6 and name in ("front", "back", "left", "right"):
                        r = min(W, H) * 0.24
                        dist = math.hypot(x + 0.5 - W * 0.32, (y + 0.5 - H * 0.3) * 1.3) / r
                        k = max(0.0, 1.0 - dist)
                        rgb = lerp(rgb, GLAZE, 0.5 * k * k * (3 - 2 * k))
                    out.set(fu * S + x, fv * S + y, rgba(rgb))
    return out


TEXTURES = {}
for variant, cols in COATS.items():
    t = Canvas(TEX_W * S, TEX_H * S)
    paint_gnome(t, cols, False)
    TEXTURES[variant] = t
cap = Canvas(TEX_W * S, TEX_H * S)
paint_gnome(cap, CAPTAIN_COAT, True)
TEXTURES["captain"] = cap
STATUES = {k: glaze(v) for k, v in TEXTURES.items()}
for k in TEXTURES:
    name = "gnome_captain" if k == "captain" else f"gnome_{k}"
    TEXTURES[k].save(f"entity/gnome/{name}.png")
    STATUES[k].save(f"entity/gnome/{name}_statue.png")
# The Garden Gnome block wears the blue troop's glaze.
STATUES["blue"].save("block/garden_gnome.png")


# ---------------------------------------------------------------------------------------------- posing
def rest_pose(model):
    return {name: [list(pivot), list(rot)] for name, _, pivot, rot, _ in model}


def pose(model, walk=0.0, speed=0.0, age=0.0, yaw=0.0, pitch=0.0, carrying=False, marching=False, panic=False, attack=0.0,
         shudder=0.0, shudder_age=0.0, captain=False):
    """Python twin of GnomeModel.pose. walk and speed are the walk animation's position and speed; yaw and pitch the
    head's (radians); attack 0..1 through a poke; shudder 0..1 while setting into a statue."""
    p = rest_pose(model)
    sin, cos = math.sin, math.cos
    stride = walk * 1.0
    amp = min(1.0, speed * 1.8)
    swing = cos(stride)
    roll = sin(stride) * 0.17 * amp
    bob = abs(sin(stride)) * 0.7 * amp
    idle = sin(age * 0.06)
    quake = sin(shudder_age * 2.9) * shudder
    # A rolling waddle from the hips, rising a little on each step.
    p["right_leg"][1][0] = swing * 0.85 * amp
    p["left_leg"][1][0] = -swing * 0.85 * amp
    p["body"][0][1] -= bob
    p["body"][1] = [0.06 * amp + (0.22 if panic else 0.0) + 0.03 * quake, 0.0, roll + 0.06 * quake]
    p["head"][1] = [pitch + 0.04 * sin(shudder_age * 3.7) * shudder, yaw, -roll * 0.6]
    # The hat lags behind the waddle, more toward its tip, and sways a little at rest.
    p["hat_1"][1][0] += -0.05 * amp
    p["hat_1"][1][2] += -roll * 0.4 + 0.02 * idle
    p["hat_2"][1][0] += -0.08 * amp + 0.04 * sin(stride * 2.0) * amp
    p["hat_2"][1][2] += -roll * 0.5 + 0.03 * idle
    p["hat_3"][1][0] += -0.06 * amp
    p["hat_3"][1][2] += -roll * 0.4 + 0.03 * idle
    p["hat_tip"][1][0] += -0.1 * amp + 0.06 * sin(stride * 2.0 - 0.8) * amp
    p["hat_tip"][1][2] += -roll * 0.6 + 0.05 * sin(age * 0.06 + 1.0)
    # The beard's point bounces forward on every step.
    p["beard_tip"][1] = [-(0.04 + 0.28 * abs(sin(stride)) * amp), 0.0, -roll * 0.5]
    # Arms: swinging opposite the legs (stiff and high on the march), hugging a crop, flailing in a panic.
    r, l = p["right_arm"][1], p["left_arm"][1]
    arm_swing = 1.0 if marching else 0.6
    r[0] = -swing * arm_swing * amp
    l[0] = swing * arm_swing * amp
    if marching:
        r[2] += 0.08
        l[2] -= 0.08
    if carrying:
        r[:] = [-1.15 + 0.06 * sin(stride * 2.0) * amp, 0.0, -0.32]
        l[:] = [-1.15 + 0.06 * sin(stride * 2.0) * amp, 0.0, 0.32]
    if panic:
        r[:] = [-2.5 + 0.35 * sin(age * 0.9), 0.0, 0.85]
        l[:] = [-2.5 + 0.35 * sin(age * 0.9 + 1.7), 0.0, -0.85]
    poke = sin(attack * math.pi)
    if captain:
        # The pitchfork shouldered upright, pumped on the march; levelled forward to poke.
        r[:] = [-0.45 - (0.18 * max(0.0, sin(stride)) * amp if marching else 0.0) - 0.9 * poke, 0.0, 0.1]
        p["pitchfork"][1] = [-r[0] + 1.25 * poke, 0.0, -r[2]]
        p["pennant"][1] = [0.0, 0.3 + 0.3 * sin(age * 0.25) + 0.25 * amp * sin(age * 0.5), 0.0]
    elif not carrying and not panic:
        r[0] -= 1.3 * poke
    return p


def rot_matrix(rx, ry, rz):
    # Rz * Ry * Rx, as Quaternionf.rotationZYX.
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


IDENT = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def placed_cubes(model, p):
    """Every cube with its part's world transform: (part name, k, key, from, size, matrix, origin)."""
    t = tree(model)
    cubes = {name: c for name, _, _, _, c in model}
    out = []

    def walk(name, matrix, origin):
        for child in t.get(name, []):
            pivot, rot = p[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for k, (key, frm, size) in enumerate(cubes[child]):
                out.append((child, k, key, frm, size, m, o))
            walk(child, m, o)
    walk("root", IDENT, (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size):
    """The six faces of a cube as the game builds them: (corners, uv rect (u0, v0, u1, v1), face name)."""
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    P = lambda x, y, z: tuple(o[i] + apply(m, (x, y, z))[i] for i in range(3))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1, "down"), ([t2, t3, l3, l2], u2, v1, u22, v0, "up"),
             ([t0, l0, l3, t3], u0, v1, u1, v2, "west"), ([t1, t0, t3, t2], u1, v1, u2, v2, "north"),
             ([l1, t1, t2, l2], u2, v1, u3, v2, "east"), ([l0, l1, l2, l3], u3, v1, u4, v2, "south")]
    return [(verts, (a0, b0, a1, b1), name) for verts, a0, b0, a1, b1, name in polys]


def quads(model, p, labels=False):
    out = []
    for part, k, key, frm, size, m, o in placed_cubes(model, p):
        (u, v), _ = CUBES[key]
        centre = tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3))
        for verts, uv, name in cube_faces(m, o, u, v, frm, size):
            if labels:
                fc = tuple(sum(q[i] for q in verts) / 4 for i in range(3))
                out.append((verts, uv, f"{part}.{key}{k}.{name}", tuple(fc[i] - centre[i] for i in range(3))))
            else:
                out.append((verts, uv))
    return out


# Z-fighting: no two faces may share a plane, face the same way and overlap, at rest or anywhere through the poses.
from coplanar import Face, fights


def check(model, label, p):
    faces_ = []
    for verts, uv, name, n in quads(model, p, labels=True):
        if math.sqrt(sum(c * c for c in n)) < 1e-6:
            # A zero-thickness cube's faces sit on its centre: take the normal from the corners.
            e1 = tuple(verts[1][i] - verts[0][i] for i in range(3))
            e2 = tuple(verts[2][i] - verts[0][i] for i in range(3))
            n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        faces_.append(Face(name, verts, n))
    hits = fights(faces_)
    # The pennant is one pane, drawn from both sides: its two faces share their plane by design.
    hits = [h for h in hits if not (h[0].startswith("pennant.") and h[1].startswith("pennant."))]
    for h in hits:
        print(f"z-fight ({label}):", h)
    return len(hits)


POSES = [("rest", {}), ("carry", {"carrying": True}), ("panic", {"panic": True, "age": 7.0}),
         ("freeze", {"shudder": 0.6, "shudder_age": 3.0})]
POSES += [(f"walk {w:.1f}", {"walk": w, "speed": 0.6, "age": w * 9}) for w in [i * 0.45 for i in range(14)]]
POSES += [(f"march {w:.1f}", {"walk": w, "speed": 0.5, "marching": True, "age": w * 7}) for w in [i * 0.6 for i in range(10)]]
POSES += [(f"poke {a:.2f}", {"attack": a}) for a in (0.25, 0.5, 0.75)]
POSES += [("look", {"yaw": 0.6, "pitch": -0.3})]
_bad = 0
for _model, _cap in ((TROOP, False), (CAPTAIN, True)):
    for _label, _kw in POSES:
        _bad += check(_model, ("captain " if _cap else "") + _label, pose(_model, captain=_cap, **_kw))
if _bad:
    raise SystemExit(f"gnome: {_bad} coplanar overlapping face pairs")


def extents(model, p):
    pts = [q for verts, _ in quads(model, p) for q in verts]
    return [(min(q[i] for q in pts), max(q[i] for q in pts)) for i in range(3)]


for _name, _model in (("gnome", TROOP), ("gnome captain", CAPTAIN)):
    ext = extents(_model, pose(_model, captain=_model is CAPTAIN))
    print(f"{_name}: {sum(len(c) for _, _, _, _, c in _model)} cubes; x {ext[0][0]:.1f}..{ext[0][1]:.1f}, "
          f"top y {ext[1][0]:.1f} ({GROUND - ext[1][0]:.1f} px tall), bottom y {ext[1][1]:.2f}, z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "gnome", "GnomeModel.java"))


def f(v):
    return f"{v:.2f}f".replace("-0.00f", "0.00f")


def java_layer(model, method):
    """A createXLayer method, from the part list."""
    t = tree(model)
    lines = [f"\tpublic static LayerDefinition {method}() {{", "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in model:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({', '.join(f(c) for c in frm)}, {', '.join(f(c) for c in size)})"
        pose_ = (f"PartPose.offsetAndRotation({', '.join(f(c) for c in pivot)}, {', '.join(f(c) for c in rot)})"
                 if any(rot) else f"PartPose.offset({', '.join(f(c) for c in pivot)})")
        used = t.get(name) is not None
        lines.append(f"\t\t{'PartDefinition ' + var + ' = ' if used else ''}{('p_' + parent) if parent else 'root'}"
                     f".addOrReplaceChild(\"{name}\", {builder},\n\t\t\t\t{pose_});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    lines.append("\t}")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t// BEGIN GENERATED by tools/gen_gnome.py\n", "\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer(TROOP, "createTroopLayer") + "\n\n" + java_layer(CAPTAIN, "createCaptainLayer") + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- the Garden Gnome block
# The troop's statue, cube for cube: model space turned the right way up (a half turn about z, as the entity renderer
# turns it), scaled to stand a little under a block tall, facing north (the block state turns it to face whoever
# placed it). Each cube becomes an element: its box in the part's own frame, turned about the part's pivot.
from gunkit import to_euler

BLOCK_POSE = pose(TROOP, age=20.0)
_ext = extents(TROOP, BLOCK_POSE)
BLOCK_SCALE = round(14.5 / (GROUND - _ext[1][0]), 4)
FLIP = [[-1, 0, 0], [0, -1, 0], [0, 0, 1]]


def to_block(p):
    return (8.0 - p[0] * BLOCK_SCALE, (GROUND - p[1]) * BLOCK_SCALE, 8.0 + p[2] * BLOCK_SCALE)


def block_elements():
    k = 16.0 / TEX_W
    elements = []
    for part, n, key, frm, size, m, o in placed_cubes(TROOP, BLOCK_POSE):
        (u, v), (w, h, d) = CUBES[key]
        origin = to_block(o)
        corner_a = (-frm[0] * BLOCK_SCALE, -frm[1] * BLOCK_SCALE, frm[2] * BLOCK_SCALE)
        corner_b = (-(frm[0] + w) * BLOCK_SCALE, -(frm[1] + h) * BLOCK_SCALE, (frm[2] + d) * BLOCK_SCALE)
        lo = [origin[i] + min(corner_a[i], corner_b[i]) for i in range(3)]
        hi = [origin[i] + max(corner_a[i], corner_b[i]) for i in range(3)]
        u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
        v0, v1, v2 = v, v + d, v + d + h
        # Model faces to block faces (model -x is the block's east, model -y its up), and their UVs in the block
        # model's orientation (see the derivation in the docstring of check_block_uvs).
        uvs = {"up": (u2, v1, u1, v0), "down": (u22, v0, u2, v1), "east": (u0, v1, u1, v2), "north": (u1, v1, u2, v2),
               "west": (u2, v1, u3, v2), "south": (u3, v1, u4, v2)}
        e = OrderedDict([("name", f"{part}.{key}"), ("from", [round(c, 4) for c in lo]), ("to", [round(c, 4) for c in hi])])
        r = mul(FLIP, mul(m, FLIP))
        x, y, z = (round(a_, 3) for a_ in to_euler(r))
        x, y, z = (0.0 if abs(a_) < 1e-3 else a_ for a_ in (x, y, z))
        if x or y or z:
            e["rotation"] = OrderedDict([("x", x), ("y", y), ("z", z), ("origin", [round(c, 4) for c in origin])])
        e["faces"] = OrderedDict((name, {"uv": [round(c * k, 4) for c in uv], "texture": "#gnome"}) for name, uv in uvs.items())
        elements.append(e)
    return elements


ELEMENTS = block_elements()
from coplanar import check_elements
check_elements("garden_gnome block", ELEMENTS)
print(f"garden gnome block: {len(ELEMENTS)} elements, scale {BLOCK_SCALE}")


# ---------------------------------------------------------------------------------------------- spawn eggs
def egg_sprite(base, spots, outline):
    """The shopping cart egg's outline, repainted: a smooth light-to-dark ramp across the shell, and spots."""
    w, h, cart = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
    mask = [p_[3] > 0 for p_ in cart]
    xs = [i % w for i in range(w * h) if mask[i]]
    ys = [i // w for i in range(w * h) if mask[i]]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    egg = Canvas(w, h)
    for i in range(w * h):
        if not mask[i]:
            continue
        x, y = i % w, i // w
        edge = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            egg.px[i] = rgba(outline)
            continue
        c = ramp(base, 0.5 - ((cx - x) + (cy - y)) / 14.0)
        for (sx, sy), col_ in spots.items():
            if (x, y) == (sx, sy):
                c = col_
        egg.px[i] = rgba(c)
    return egg


R, W_, G = HAT[1], BEARD[0], GOLD[1]
EGGS = {
    "gnome_spawn_egg": egg_sprite(COATS["blue"], {(7, 4): R, (8, 4): R, (7, 5): R, (6, 9): W_, (7, 9): W_, (7, 10): W_, (10, 8): R, (5, 12): R},
                                  (34, 52, 96)),
    "gnome_captain_spawn_egg": egg_sprite(CAPTAIN_COAT, {(7, 3): R, (7, 4): R, (8, 4): R, (7, 5): R, (6, 9): G, (9, 10): G, (8, 9): W_,
                                                         (7, 10): W_, (10, 7): R, (5, 12): G}, (58, 32, 82)),
}
for name, sprite in EGGS.items():
    sprite.save(f"item/{name}.png")


# ---------------------------------------------------------------------------------------------- json
MODELS = os.path.join(ASSETS, "models")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f_:
        json.dump(obj, f_, indent=2, ensure_ascii=False)
        f_.write("\n")


def merge(path, entries):
    """Adds entries to a JSON object file others also write (lang, sounds.json), keeping what's there and its order."""
    with open(path) as f_:
        obj = json.load(f_, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


for name in EGGS:
    write(os.path.join(MODELS, "item", name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})

write(os.path.join(MODELS, "block", "garden_gnome.json"), OrderedDict([
    ("parent", "minecraft:block/block"),
    ("ambientocclusion", False),
    ("textures", OrderedDict([("gnome", "tomyen:block/garden_gnome"), ("particle", "tomyen:block/garden_gnome")])),
    ("elements", ELEMENTS),
    ("display", OrderedDict([
        ("gui", {"rotation": [20, 205, 0], "translation": [0, 0.5, 0], "scale": [0.72, 0.72, 0.72]}),
        ("ground", {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]}),
        ("fixed", {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}),
        ("head", {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]}),
        ("thirdperson_righthand", {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}),
        ("thirdperson_lefthand", {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}),
        ("firstperson_righthand", {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}),
        ("firstperson_lefthand", {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]})]))]))
write(os.path.join(ASSETS, "blockstates", "garden_gnome.json"), {"variants": OrderedDict(
    (f"facing={facing}", {"model": "tomyen:block/garden_gnome", **({"y": y} if y else {})})
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)))})
write(os.path.join(ASSETS, "items", "garden_gnome.json"), {"model": {"type": "minecraft:model", "model": "tomyen:block/garden_gnome"}})


def pool(item, low, high, looting=1.0, chance=None, player=False, looting_chance=0.0):
    """A loot pool of one item (26.3's format: an entry's functions are its "modifier", a pool's conditions one
    "condition", all_of for several)."""
    entry = OrderedDict([("type", "minecraft:item"), ("name", item)])
    mods = []
    if high > 1 or low != 1:
        mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}})
    if looting:
        mods.append({"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
                     "enchantment": "minecraft:looting"})
    if mods:
        entry["modifier"] = mods
    p_ = OrderedDict([("rolls", 1), ("entries", [entry])])
    conditions = []
    if player:
        conditions.append({"type": "minecraft:killed_by_player"})
    if chance is not None:
        conditions.append({"type": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                           "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + looting_chance,
                                                                            "per_level_above_first": looting_chance}})
    if len(conditions) == 1:
        p_["condition"] = conditions[0]
    elif conditions:
        p_["condition"] = {"type": "minecraft:all_of", "terms": conditions}
    return p_


write(os.path.join(DATA, "loot_table/entities/gnome.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [pool("tomyen:fae_dust", 0, 2), pool("tomyen:garden_gnome", 1, 1, looting=0, chance=0.05, player=True, looting_chance=0.03)]),
    ("random_sequence", "tomyen:entities/gnome")]))
write(os.path.join(DATA, "loot_table/entities/gnome_captain.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [pool("tomyen:fae_dust", 1, 3), pool("minecraft:gold_nugget", 1, 3),
               pool("tomyen:garden_gnome", 1, 1, looting=0, chance=0.2, player=True, looting_chance=0.05)]),
    ("random_sequence", "tomyen:entities/gnome_captain")]))
write(os.path.join(DATA, "loot_table/blocks/garden_gnome.json"), OrderedDict([
    ("type", "minecraft:block"),
    ("pools", [OrderedDict([("rolls", 1), ("entries", [{"type": "minecraft:item", "name": "tomyen:garden_gnome"}]),
                            ("condition", {"type": "minecraft:survives_explosion"})])]),
    ("random_sequence", "tomyen:blocks/garden_gnome")]))
write(os.path.join(DATA, "tags/block/gnome_homes.json"), {"values": ["tomyen:fae_mushroom", "tomyen:fae_grass_block"]})


def vanilla(names, pitch, volume=None, **extra):
    out = []
    for n in names:
        e = OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)])
        if volume is not None:
            e["volume"] = volume
        e.update(extra)
        out.append(e)
    return out


def event(name, sounds, subtitle=True):
    e = OrderedDict()
    if subtitle:
        e["subtitle"] = f"subtitles.tomyen.{name}"
    e["sounds"] = sounds
    return name, e


SOUNDS = OrderedDict([
    event("gnome_ambient", vanilla(["mob/villager/idle1", "mob/villager/idle2", "mob/villager/idle3"], 1.9, 0.6)),
    event("gnome_hurt", vanilla(["mob/villager/hit1", "mob/villager/hit2", "mob/villager/hit3", "mob/villager/hit4"], 1.9, 0.7)),
    event("gnome_death", vanilla(["mob/villager/death"], 2.0, 0.8)),
    event("gnome_giggle", vanilla(["mob/villager/yes1", "mob/villager/yes2", "mob/villager/yes3"], 2.0, 0.7)),
    event("gnome_scatter", vanilla(["mob/villager/no1", "mob/villager/no2", "mob/villager/no3"], 2.0, 0.8)),
    event("gnome_poke", vanilla(["item/trident/pierce1", "item/trident/pierce2", "item/trident/pierce3"], 1.8, 0.5)),
    event("gnome_step", vanilla(["note/hat"], 1.6, 0.18), subtitle=False),
    event("gnome_freeze", vanilla(["block/decorated_pot/insert1", "block/decorated_pot/insert2", "block/decorated_pot/insert3",
                                   "block/decorated_pot/insert4"], 1.3, 0.9)),
    event("gnome_thaw", vanilla(["block/calcite/break1", "block/calcite/break2", "block/calcite/break3", "block/calcite/break4"], 1.5, 0.8)),
    event("gnome_shatter", vanilla(["block/decorated_pot/shatter1", "block/decorated_pot/shatter2", "block/decorated_pot/shatter3",
                                    "block/decorated_pot/shatter4", "block/decorated_pot/shatter5"], 1.25)),
    event("gnome_captain_order", vanilla(["note/flute"], 1.35, 0.9, attenuation_distance=32)),
    event("gnome_captain_drum", vanilla(["note/snare"], 1.5, 0.35), subtitle=False),
    event("gnome_rout", vanilla(["mob/villager/no1", "mob/villager/no2", "mob/villager/no3"], 1.6)),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.tomyen.gnome", "Gnome"),
    ("entity.tomyen.gnome_captain", "Gnome Captain"),
    ("item.tomyen.gnome_spawn_egg", "Gnome Spawn Egg"),
    ("item.tomyen.gnome_captain_spawn_egg", "Gnome Captain Spawn Egg"),
    ("block.tomyen.garden_gnome", "Garden Gnome"),
    ("subtitles.tomyen.gnome_ambient", "Gnome mutters"),
    ("subtitles.tomyen.gnome_hurt", "Gnome hurts"),
    ("subtitles.tomyen.gnome_death", "Gnome dies"),
    ("subtitles.tomyen.gnome_giggle", "Gnome giggles"),
    ("subtitles.tomyen.gnome_scatter", "Gnomes scatter"),
    ("subtitles.tomyen.gnome_poke", "Gnome pokes"),
    ("subtitles.tomyen.gnome_freeze", "Gnome turns to ceramic"),
    ("subtitles.tomyen.gnome_thaw", "Gnome statue cracks"),
    ("subtitles.tomyen.gnome_shatter", "Gnome statue shatters"),
    ("subtitles.tomyen.gnome_captain_order", "Gnome Captain toots an order"),
    ("subtitles.tomyen.gnome_rout", "Gnomes flee in disarray"),
]))


# ---------------------------------------------------------------------------------------------- preview
def render(model, p, view, tex, scale=9, width=300, height=380, light=(0.35, -0.8, -0.5), focus=None, background=(46, 52, 64),
           extra=None):
    """Orthographic render. view: 'front', 'side' (from its right), 'three' (three-quarters) or 'back'. focus: a model
    point to centre (default: the ground at the bottom). extra: more (corners, colour) quads, already in model space."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [rgba(background)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        ox, oy, ly = width * 0.5, height - 16, GROUND
    gy = int(oy + (GROUND - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    qs = quads(model, p) if model else []
    for verts, (ua, va, ub, vb) in qs + (extra or []):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = (ub - ua) * S, (vb - va) * S
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua * S + (i + 0.5) * du / nu), TEX_W * S - 1)
                tv = min(int(va * S + (j + 0.5) * dv / nv), TEX_H * S - 1)
                col = tex.get(tu, tv)
                if col[3] == 0:
                    continue
                c = rgba(shade(col, lit))
                corners = []
                for (a_, b_) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s_, t_ = a_ / nu, b_ / nv
                    q = tuple(A[k] + e1[k] * s_ + e2[k] * t_ for k in range(3))
                    x = q[0] * ca - q[2] * sa
                    z = q[0] * sa + q[2] * ca
                    corners.append((ox + x * scale, oy + (q[1] - ly) * scale, z))
                fill(img, zbuf, width, height, corners, sum(c_[2] for c_ in corners) / 4, c)
    return img


def fill(img, zbuf, width, height, corners, depth, colour):
    xs_ = [c[0] for c in corners]
    ys_ = [c[1] for c in corners]
    for py in range(max(0, int(min(ys_))), min(height, int(math.ceil(max(ys_))) + 1)):
        for px in range(max(0, int(min(xs_))), min(width, int(math.ceil(max(xs_))) + 1)):
            if inside(corners, px + 0.5, py + 0.5) and depth < zbuf[py * width + px]:
                zbuf[py * width + px] = depth
                img[py * width + px] = colour


def inside(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cross) < 1e-9:
            continue
        s_ = 1 if cross > 0 else -1
        if sign == 0:
            sign = s_
        elif s_ != sign:
            return False
    return sign != 0


def block_quads(elements):
    """The block model's faces as the game reads its JSON (each face's default UV direction, then the face's uv
    rect, then the element's rotation), mapped back into entity model space for the same rasterizer. Independent of
    cube_faces, so a mismatch between the block and the entity shows in the preview."""
    from gunkit import element_rotation
    out = []
    inv = lambda b: ((8.0 - b[0]) / BLOCK_SCALE, GROUND - b[1] / BLOCK_SCALE, (b[2] - 8.0) / BLOCK_SCALE)
    for e in elements:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        # Corners in the order: (0, 0), (1, 0), (1, 1), (0, 1) of the face's default uv (u right, v down).
        corners = {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
                   "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
                   "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
                   "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
                   "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
                   "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}
        rot = element_rotation(e)
        for name, face in e["faces"].items():
            pts = corners[name]
            if rot:
                m, o = rot
                pts = [tuple(o[k] + apply(m, tuple(q[i] - o[i] for i in range(3)))[k] for k in range(3)) for q in pts]
            u0, v0, u1, v1 = (c * TEX_W / 16.0 for c in face["uv"])
            # render() takes verts[1] as (u0, v0), verts[0] as (u1, v0), verts[2] as (u0, v1).
            out.append(([inv(pts[1]), inv(pts[0]), inv(pts[3]), inv(pts[2])], (u0, v0, u1, v1)))
    return out


def sprites_panel(width, height):
    img = [(46, 52, 64, 255)] * (width * height)
    k = 7
    for n, name in enumerate(EGGS):
        canvas = EGGS[name]
        bx, by = (width - 16 * k) // 2, 30 + n * (16 * k + 40)
        for y in range(16 * k):
            for x in range(16 * k):
                pxl = canvas.get(x // k, y // k)
                checker = (56, 62, 74, 255) if ((x // 14) + (y // 14)) % 2 else (50, 56, 68, 255)
                img[(by + y) * width + bx + x] = pxl if pxl[3] else checker
    return img


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k_, im in enumerate(images):
        bx, by = (k_ % cols) * w, (k_ // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    blue, cap_tex = TEXTURES["blue"], TEXTURES["captain"]
    T, C = TROOP, CAPTAIN
    views = [
        render(T, pose(T), "front", blue), render(T, pose(T), "three", blue), render(T, pose(T), "side", blue),
        render(T, pose(T, walk=1.1, speed=0.6, age=10), "three", TEXTURES["green"]),
        render(T, pose(T, walk=2.6, speed=0.5, marching=True, age=20), "side", TEXTURES["ochre"]),
        render(T, pose(T, carrying=True, walk=0.4, speed=0.4), "three", blue),
        render(T, pose(T, panic=True, age=7), "front", TEXTURES["green"]),
        render(C, pose(C, captain=True), "front", cap_tex, scale=8), render(C, pose(C, captain=True, age=30), "three", cap_tex, scale=8),
        render(C, pose(C, captain=True, walk=1.3, speed=0.5, marching=True, age=44), "side", cap_tex, scale=8),
        render(C, pose(C, captain=True, attack=0.5, age=12), "three", cap_tex, scale=8),
        render(T, pose(T, walk=1.4, speed=0.6), "three", STATUES["blue"]),
        render(C, pose(C, captain=True), "three", STATUES["captain"], scale=8),
        render(T, pose(T), "front", blue, scale=26, focus=(0.0, 9.0, 0.0)),
        render(None, None, "three", STATUES["blue"], extra=block_quads(ELEMENTS)),
        sprites_panel(300, 380),
    ]
    out, bw, bh = board(views, 300, 380, 4)
    png(os.path.join(PREVIEW, "preview_gnome.png"), bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
