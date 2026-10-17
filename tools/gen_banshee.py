"""Builds the Banshee: her model (the cube layout in PARTS, written into client/banshee/BansheeModel.java between the
GENERATED markers), her entity texture (translucent: every texel carries its own alpha) and the glow layer for her
eyes, her spawn egg and the Ectoplasm she leaves, and their item models and definitions. On the data side it writes
her loot table, Ectoplasm's recipes (with recipe-book advancements) and adds her to the tomyen:holy_vulnerable entity
tag (and so to tomyen:evil, which holds it). Her sounds (vanilla ghast, vex and allay sounds, re-pitched) and lang
entries are merged into sounds.json and en_us.json.

She's a gaunt, floating woman: a narrow bodice under a torn shawl, a long gown that frays into five trailing wisps
where her legs should be, thin arms in bell sleeves reaching forward with long bony fingers, and long ash-white hair
falling to her waist round a hollow-eyed face whose jaw can drop right open. The texture is painted in smooth ramps of
spectral blue-white (skin), grey-blue (gown) and silver (hair); the alpha falls away down the gown and toward the
hair and wisp tips, so she fades out at the edges. Her eye sockets are black with a pale pinpoint in each, which glows.
No noise, no dithering; a rerun gives the same files.

The model is checked for z-fighting at rest and mid-wail (tools/coplanar.py), and its extents are printed (the
wisps should end a little below y 24, the height she hovers at).

With --preview DIR it renders, into DIR, preview_banshee.png (front, side and three-quarters at rest; reaching for a
chill touch; the wail's wind-up and the scream; a holy banishing; and the face up close at rest and screaming) and
preview_banshee_texture.png, alpha-blended over a night sky, back faces culled as the game culls them, using the same
cubes, pivots and UV layout as the Java model and a Python twin of its animation.

Usage: python3 tools/gen_banshee.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None


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


# ---------------------------------------------------------------------------------------------- palette
# Light to dark. Skin: spectral blue-white. Gown: a grey-blue shroud. Hair: silver to ash.
SKIN = [(238, 244, 246), (214, 226, 232), (186, 202, 214), (152, 170, 188), (118, 136, 158)]
GOWN = [(170, 186, 206), (138, 156, 182), (108, 126, 158), (82, 98, 134), (60, 72, 108)]
HAIR = [(242, 244, 246), (216, 222, 228), (184, 192, 204), (148, 158, 174), (114, 124, 144)]
SOCKET = (18, 22, 34)
SOCKET_RIM = (74, 86, 108)
PUPIL = (96, 176, 196)
MOUTH = (14, 16, 26)
MOUTH_RIM = (66, 76, 98)
NAIL = (120, 134, 160)

# Alphas: solid enough to read in the face and hands, fading down the gown and to the tips of the hair and wisps.
A_SKIN, A_FACE = 228, 236
A_HAIR_ROOT, A_HAIR_TIP = 220, 130
A_GOWN_TOP, A_GOWN_HEM = 212, 150
A_WISP_ROOT, A_WISP_TIP = 160, 64

TEX_W, TEX_H = 128, 64


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1 (no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def alpha_ramp(a0, a1, t):
    return int(round(a0 + (a1 - a0) * max(0.0, min(1.0, t))))


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out. 'top' and
    'bottom' are as seen in the world (the model is upside down); in both the front edge is the last row. In 'right'
    (the cube's -x side, her right) the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, uv, size, painters):
    """painters: face name -> f(x, y, fw, fh) -> (r, g, b, a) or None for clear."""
    u, v = uv
    w, h, d = size
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name)
        if f is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = f(x, y, fw, fh)
                canvas.set(fu + x, fv + y, c or CLEAR)


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored, grow). grow is a
# CubeDeformation: the box is drawn that much bigger (or smaller, if negative) all round, its texture unchanged, which
# is how the arms and fingers come out thinner than a whole texel. Mirrored cubes share their texture with the
# unmirrored one of the same key. Model space is the game's: y down, the front toward -z, y 24 at the entity's feet
# (she hovers, so that's a little off the ground). This is the one place the shape is defined: it's written into
# BansheeModel.java, painted here, and drawn by the preview.
SIDES = (("right", -1, False), ("left", 1, True))
ARM_REST = (-1.42, 0.0, 0.0)
WISPS = ((-2.2, -1.6, -1), (2.2, -1.6, 1), (-2.4, 1.9, -1), (2.4, 1.9, 1))   # x, z, outward side (-x is her right)
WISP_REST = 0.5
# The wisps lean in toward each other, so the gown tapers to a tail instead of standing on legs.
WISP_IN = 0.3
TAIL_REST = 0.6


def parts():
    p = [
        # The body leans a little forward from the waist; she's built down from it.
        ("body", None, (0.0, 9.0, 0.0), (0.1, 0.0, 0.0), [
            ("waist", (-2.5, -4.0, -1.5), (5, 4, 3), False, 0.0),
            ("chest", (-3.0, -9.0, -2.0), (6, 5, 4), False, 0.0),
            ("bust", (-2.5, -7.4, -2.6), (5, 2, 1), False, 0.0),
            ("shawl", (-4.0, -9.6, -2.5), (8, 2, 5), False, 0.0),
            ("skirt_top", (-3.5, -0.6, -2.4), (7, 5, 5), False, 0.0)]),
        ("neck", "body", (0.0, -9.2, 0.2), (-0.15, 0.0, 0.0), [
            ("neck", (-1.0, -2.8, -1.0), (2, 3, 2), False, 0.0)]),
        ("head", "neck", (0.0, -2.4, 0.0), (0.12, 0.0, 0.0), [
            ("skull", (-3.5, -7.0, -3.5), (7, 7, 7), False, 0.0),
            ("brow", (-3.0, -5.1, -4.1), (6, 1, 1), False, 0.0),
            ("cheek", (-3.3, -2.9, -3.8), (1, 1, 1), False, 0.0),
            ("cheek", (2.3, -2.9, -3.8), (1, 1, 1), True, 0.0),
            ("crown", (-4.0, -7.6, -3.7), (8, 2, 8), False, 0.0)]),
        ("jaw", "head", (0.0, -0.6, -0.4), (0.04, 0.0, 0.0), [
            ("jaw", (-2.5, -0.3, -3.35), (5, 2, 4), False, 0.0)]),
        ("hair_back", "head", (0.0, -6.0, 3.65), (0.1, 0.0, 0.0), [
            ("hair_back", (-3.5, 0.0, 0.0), (7, 10, 1), False, 0.0)]),
        ("hair_back_low", "hair_back", (0.0, 9.6, 0.1), (0.12, 0.0, 0.0), [
            ("hair_back_low", (-3.0, 0.0, 0.0), (6, 9, 1), False, 0.0)]),
        ("skirt", "body", (0.0, 4.0, 0.1), (0.05, 0.0, 0.0), [
            ("skirt", (-4.5, 0.0, -3.5), (9, 6, 7), False, 0.0)]),
        ("tail", "skirt", (0.0, 5.4, 2.9), (TAIL_REST, 0.0, 0.0), [
            ("wisp_a", (-1.5, 0.0, -1.5), (3, 4, 3), False, -0.1)]),
        ("tail_mid", "tail", (0.0, 3.5, 0.0), (0.4, 0.0, 0.0), [
            ("wisp_b", (-1.0, 0.0, -1.0), (2, 4, 2), False, 0.0)]),
        ("tail_tip", "tail_mid", (0.0, 3.6, 0.0), (0.4, 0.0, 0.0), [
            ("wisp_c", (-0.5, 0.0, -0.5), (1, 4, 1), False, 0.0)]),
    ]
    for i, (x, z, out) in enumerate(WISPS):
        p += [
            (f"wisp_{i}", "skirt", (x, 5.5, z), (WISP_REST, 0.0, out * WISP_IN), [
                ("wisp_a", (-1.5, 0.0, -1.5), (3, 4, 3), out > 0, 0.0)]),
            (f"wisp_{i}_tip", f"wisp_{i}", (0.0, 3.6, 0.0), (0.3, 0.0, 0.0), [
                ("wisp_b", (-1.0, 0.0, -1.0), (2, 4, 2), out > 0, 0.0)]),
        ]
    for side, s, mirror in SIDES:
        p += [
            # Long hair framing the face, from the temples to the waist.
            (side + "_hair", "head", (s * 3.6, -6.2, 0.3), (0.0, 0.0, -s * 0.08), [
                ("hair_side", (-0.5 + s * 0.5, 0.0, -3.0), (1, 9, 6), mirror, 0.0)]),
            (side + "_hair_low", side + "_hair", (0.0, 8.6, 0.0), (0.05, 0.0, -s * 0.05), [
                ("hair_side_low", (-0.5 + s * 0.5, 0.0, -2.5), (1, 8, 5), mirror, 0.0)]),
            # Arms reaching forward from under the shawl.
            (side + "_arm", "body", (s * 3.9, -8.2, -0.2), (ARM_REST[0], s * 0.1, -s * 0.08), [
                ("upper_arm", (-1.0, -0.5, -1.0), (2, 6, 2), mirror, -0.25),
                ("sleeve", (-1.5, -0.9, -1.5), (3, 7, 3), mirror, 0.0)]),
            (side + "_forearm", side + "_arm", (0.0, 5.4, 0.0), (-0.2, 0.0, 0.0), [
                ("forearm", (-1.0, 0.0, -1.0), (2, 6, 2), mirror, -0.35)]),
            (side + "_hand", side + "_forearm", (0.0, 5.5, 0.0), (0.15, 0.0, 0.0), [
                ("palm", (-1.0, 0.0, -0.5), (2, 2, 1), mirror, -0.1)]),
            (side + "_fingers", side + "_hand", (0.0, 1.7, 0.0), (0.35, 0.0, 0.0), [
                ("finger", (-1.15, -0.3, -0.5), (1, 4, 1), mirror, -0.3),
                ("finger_long", (-0.5, -0.3, -0.5), (1, 5, 1), mirror, -0.3),
                ("finger", (0.15, -0.3, -0.5), (1, 4, 1), mirror, -0.3)]),
        ]
    return p


PARTS = parts()
TREE = {}
for _name, _parent, *_ in PARTS:
    TREE.setdefault(_parent or "root", []).append(_name)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS}


def pack():
    """Texture offsets for each cube key, shelf-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS:
        for key, _, size, _, _ in cubes:
            assert sizes.setdefault(key, size) == size, key
    order = sorted(sizes, key=lambda k: (-(sizes[k][2] + sizes[k][1]), k))
    offs, x, y, shelf = {}, 0, 0, 0
    for k in order:
        w, h, d = sizes[k]
        fw, fh = 2 * (w + d), d + h
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        offs[k] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= TEX_H, "texture too small"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


def cube_count():
    return sum(len(c) for _, _, _, _, c in PARTS)


# ---------------------------------------------------------------------------------------------- painting
# A torn hem: how many texels each column loses off the end of the cloth (repeating, so it reads as tatters, not noise).
HEM = [2, 0, 1, 3, 1, 0, 2, 1, 3, 0, 1, 2, 0, 3, 1, 2]


def shade_face(name):
    """Light from above and in front: tops brightest, then fronts, sides, backs, undersides."""
    return {"top": -0.12, "front": 0.0, "right": 0.08, "left": 0.08, "back": 0.16, "bottom": 0.3}[name]


def skin(t, a=A_SKIN):
    return rgba(ramp(SKIN, t), a)


def hair(t, a):
    return rgba(ramp(HAIR, t), a)


def gown(t, a):
    return rgba(ramp(GOWN, t), a)


def strands(x, t0, t1, y, h):
    """Hair falling in deliberate strands: every third column a shade darker, lighter at the root."""
    base = t0 + (t1 - t0) * (y / max(1, h - 1))
    return base + (0.18 if x % 3 == 2 else 0.0)


def painters_for(key, w, h, d):
    """face name -> painter, for each cube key."""
    P = {}

    def all_faces(f):
        return {n: (lambda n_: (lambda x, y, fw, fh: f(n_, x, y, fw, fh)))(n) for n in
                ("top", "bottom", "right", "left", "front", "back")}

    if key in ("skull",):
        def face(x, y, fw, fh):
            # 7 x 7: fringe, forehead, brow shadow, hollow sockets with a pinpoint each, gaunt cheeks, a dark mouth.
            if x in (0, 6) and y <= 4:
                return hair(0.35 + 0.08 * y, A_HAIR_ROOT)
            if y == 0:
                return hair(0.3 if x != 3 else 0.55, A_HAIR_ROOT)
            if y in (3, 4) and x in (1, 2, 4, 5):
                return rgba(SOCKET, 250)
            if y == 2 and x in (1, 2, 4, 5):
                return rgba(SOCKET_RIM, A_FACE)
            if y == 5:
                if x in (1, 5):
                    return rgba(SOCKET_RIM, A_FACE)   # the dark running down from the sockets
                if x == 3:
                    return skin(0.45, A_FACE)
                return skin(0.6, A_FACE)       # hollow cheeks
            if y == 6:
                if 2 <= x <= 4:
                    return rgba(MOUTH_RIM, A_FACE)
                return skin(0.5, A_FACE)
            if y == 2:
                return skin(0.3, A_FACE)
            return skin(0.12 + 0.05 * y, A_FACE)

        def side(front_col):
            def f(x, y, fw, fh):
                # Skin at the temple and cheek, hair over the rest.
                temple = x >= fw - 2 if front_col == "last" else x <= 1
                if temple and y >= 1:
                    return skin(0.35 + 0.05 * y, A_SKIN)
                return hair(strands(x, 0.25, 0.6, y, fh), A_HAIR_ROOT)
            return f

        def bottom(x, y, fw, fh):
            # Under the skull: the roof of the mouth toward the front, shadowed neck behind.
            if y >= fh - 3 and 1 <= x <= fw - 2:
                return rgba(MOUTH, 245)
            return skin(0.85, A_SKIN)

        P = {"front": face, "right": side("last"), "left": side("first"),
             "back": lambda x, y, fw, fh: hair(strands(x, 0.3, 0.65, y, fh), A_HAIR_ROOT),
             "top": lambda x, y, fw, fh: hair(0.2 if x != fw // 2 else 0.5, A_HAIR_ROOT), "bottom": bottom}
    elif key == "crown":
        def f(n, x, y, fw, fh):
            if n == "front" and y == fh - 1 and HEM[x % len(HEM)] >= 2:
                return None   # a ragged fringe
            if n == "top":
                return hair(0.12 if x != fw // 2 else 0.45, A_HAIR_ROOT)
            return hair(strands(x, 0.15, 0.4, y, fh) + shade_face(n), A_HAIR_ROOT)
        P = all_faces(f)
    elif key in ("hair_side", "hair_back"):
        def f(n, x, y, fw, fh):
            if n == "top":
                return hair(0.25, A_HAIR_ROOT)
            if n == "bottom":
                return hair(0.6, 190)
            return hair(strands(x, 0.15, 0.55, y, fh) + shade_face(n) * 0.5, alpha_ramp(A_HAIR_ROOT, 185, y / max(1, fh - 1)))
        P = all_faces(f)
    elif key in ("hair_side_low", "hair_back_low"):
        def f(n, x, y, fw, fh):
            if n in ("top", "bottom"):
                return hair(0.6, 170) if n == "top" else None
            if fh - 1 - y < HEM[(x * 3) % len(HEM)]:
                return None   # ragged ends
            return hair(strands(x, 0.45, 0.85, y, fh) + shade_face(n) * 0.5, alpha_ramp(185, A_HAIR_TIP, y / max(1, fh - 1)))
        P = all_faces(f)
    elif key == "brow":
        def f(n, x, y, fw, fh):
            return skin(0.05 if n in ("front", "top") else 0.7, A_FACE)
        P = all_faces(f)
    elif key == "cheek":
        P = all_faces(lambda n, x, y, fw, fh: skin(0.08 + shade_face(n), A_FACE))
    elif key == "jaw":
        def f(n, x, y, fw, fh):
            if n == "top":
                # The inside of the mouth, seen when the jaw drops: a black throat with a grey-blue rim.
                if x in (0, fw - 1) or y == fh - 1:
                    return rgba(MOUTH_RIM, 240)
                return rgba(MOUTH, 248)
            if n == "front":
                if y == 0:
                    return rgba(MOUTH_RIM, A_FACE) if 1 <= x <= fw - 2 else skin(0.4, A_FACE)
                return skin(0.2, A_FACE)
            if n == "back":
                return rgba(MOUTH, 240)
            return skin(0.35 + shade_face(n), A_SKIN)
        P = all_faces(f)
    elif key == "neck":
        def f(n, x, y, fw, fh):
            if n == "front" and y < 2:
                # The throat, in the jaw's shadow: what shows behind it when it drops open.
                return rgba(MOUTH if y == 0 else MOUTH_RIM, 245)
            return skin(0.35 + shade_face(n) + 0.1 * y / max(1, fh - 1), A_SKIN)
        P = all_faces(f)
    elif key in ("upper_arm", "forearm"):
        P = all_faces(lambda n, x, y, fw, fh: skin(0.2 + shade_face(n) + 0.25 * y / max(1, fh - 1), A_SKIN))
    elif key == "palm":
        P = all_faces(lambda n, x, y, fw, fh: skin(0.15 + shade_face(n), A_FACE))
    elif key in ("finger", "finger_long"):
        def f(n, x, y, fw, fh):
            if n in ("top", "bottom"):
                return rgba(NAIL, A_FACE) if n == "bottom" else skin(0.3, A_FACE)
            if y >= fh - 1:
                return rgba(NAIL, A_FACE)   # long grey nails
            return skin(0.15 + 0.3 * y / max(1, fh - 1) + shade_face(n), A_FACE)
        P = all_faces(f)
    elif key == "sleeve":
        def f(n, x, y, fw, fh):
            if n == "top":
                return gown(0.2, A_GOWN_TOP)
            if n == "bottom":
                return None   # open: the arm comes out of it
            if fh - 1 - y < HEM[(x + 5) % len(HEM)] - 1:
                return None
            return gown(0.15 + 0.5 * y / max(1, fh - 1) + shade_face(n) + (0.12 if x % 2 else 0.0),
                        alpha_ramp(A_GOWN_TOP, 160, y / max(1, fh - 1)))
        P = all_faces(f)
    elif key in ("chest", "waist", "bust", "skirt_top"):
        def f(n, x, y, fw, fh):
            t = {"chest": 0.15, "bust": 0.05, "waist": 0.45, "skirt_top": 0.3}[key]
            if key == "chest" and n == "front" and y <= 1 and abs(x - (fw - 1) / 2) <= 1.5 - y:
                return skin(0.25 + 0.1 * y, A_SKIN)   # a low neckline
            if key == "waist" and n != "top" and n != "bottom" and y == 0:
                return gown(0.75, A_GOWN_TOP)         # a cord at the waist
            if key == "chest" and n == "front" and x == fw // 2 and y >= 2:
                return gown(0.55, A_GOWN_TOP)         # the bodice's seam
            folds = 0.1 if (key == "skirt_top" and x % 2 == 1 and n in ("front", "back", "left", "right")) else 0.0
            return gown(t + shade_face(n) + folds + 0.15 * y / max(1, fh - 1), A_GOWN_TOP)
        P = all_faces(f)
    elif key == "shawl":
        def f(n, x, y, fw, fh):
            if n in ("front", "back", "left", "right") and y == fh - 1 and HEM[(x * 5) % len(HEM)] >= 2:
                return None   # a frayed edge
            return gown(0.35 + shade_face(n) + (0.1 if x % 3 == 1 else 0.0), 222)
        P = all_faces(f)
    elif key == "skirt":
        def f(n, x, y, fw, fh):
            if n == "bottom":
                return gown(0.7, 140)
            if n == "top":
                return gown(0.3, A_GOWN_TOP)
            if fh - 1 - y < HEM[(x * 7 + 3) % len(HEM)] - 1:
                return None
            folds = 0.12 if x % 2 == 1 else 0.0
            return gown(0.25 + 0.4 * y / max(1, fh - 1) + shade_face(n) + folds,
                        alpha_ramp(A_GOWN_TOP - 10, A_GOWN_HEM, y / max(1, fh - 1)))
        P = all_faces(f)
    elif key in ("wisp_a", "wisp_b", "wisp_c"):
        k = {"wisp_a": 0, "wisp_b": 1, "wisp_c": 2}[key]

        def f(n, x, y, fw, fh):
            if n == "top":
                return gown(0.4, A_WISP_ROOT)
            # Along the whole wisp, root to tip: lighter and less transparent at the root.
            t = (k + y / max(1, fh - 1)) / 3.0
            if key == "wisp_c" and n != "bottom" and fh - 1 - y < (1 if x % 2 else 2):
                return None
            if n == "bottom":
                return gown(0.6 + 0.1 * k, alpha_ramp(A_WISP_ROOT, A_WISP_TIP, (k + 1) / 3.0))
            return gown(0.35 + 0.35 * t + shade_face(n) * 0.6, alpha_ramp(A_WISP_ROOT, A_WISP_TIP, t))
        P = all_faces(f)
    else:
        raise SystemExit("no painter for " + key)
    return P


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    glow = Canvas(TEX_W, TEX_H)
    for key, (uv, size) in CUBES.items():
        paint(tex, uv, size, painters_for(key, *size))
    # The glow layer: the pinpoints in her sockets, bright; the dark of her mouth, faintly.
    (u, v), (w, h, d) = CUBES["skull"]
    fu, fv, fw, fh = faces(u, v, w, h, d)["front"]
    for x in range(fw):
        for y in range(fh):
            c = tex.get(fu + x, fv + y)
            # A cold glint deep in each black socket, low and toward the nose.
            if (x, y) in ((2, 4), (4, 4)):
                glow.set(fu + x, fv + y, rgba(PUPIL))
    return tex, glow


TEXTURE, GLOW = paint_entity()
TEXTURE.save("entity/banshee/banshee.png")
GLOW.save("entity/banshee/banshee_eyes.png")


# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# Ectoplasm: a quivering, glowing gob of spirit, pale cyan-white at the heart to a deep teal rim, with a wisp
# trailing off the top and a drip below.
ECTO = ["................",
        ".........kk.....",
        "........kwk.....",
        ".......kwk......",
        "......kkwkk.....",
        "....kkmllllkk...",
        "...kmllhhhllmk..",
        "..kmlhhwwhhllmk.",
        "..kmlhwwwhhlmmk.",
        "..kmllhhhhllmmk.",
        "...kmllllllmmk..",
        "...kkmmmllmmkk..",
        ".....kkmmmkk....",
        ".......kmk......",
        ".......kdk......",
        "........k......."]
sprite(ECTO, {"k": (34, 92, 104), "d": (56, 130, 140), "m": (76, 170, 176), "l": (128, 214, 212), "h": (184, 240, 234),
              "w": (236, 255, 250)}).save("item/ectoplasm.png")

# Spawn egg: the shared egg shape (as the other mod eggs use), shroud-pale with two black sockets and a gaping mouth.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = rgba(GOWN[4])
    elif (x, y) in ((6, 7), (7, 7), (9, 7), (10, 7), (6, 8), (10, 8)):
        egg.px[i] = rgba(SOCKET)
    elif (x, y) in ((7, 8), (9, 8)):
        egg.px[i] = rgba(PUPIL)
    elif x == 8 and y in (10, 11, 12):
        egg.px[i] = rgba(MOUTH)
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = rgba(SKIN[0] if light > 5 else SKIN[1] if light > 1 else SKIN[2] if light > -4 else SKIN[3])
egg.save("item/banshee_spawn_egg.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge(path, entries):
    """Adds entries to a JSON object file others also write (lang, sounds.json), keeping what's there and its order."""
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


for name in ("ectoplasm", "banshee_spawn_egg"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(DATA, "loot_table/entities/banshee.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("tomyen:ectoplasm", 1, 2)]),
    ("random_sequence", "tomyen:entities/banshee")]))


def advancement(name, ingredient, category="misc"):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def shapeless(name, ingredients, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, ingredients[0], category)


# A banshee is a soul that wouldn't go: two gobs of her, bound into soul sand or soil, make a Soul Charge (for soul
# steel and the Soulbound infusion), a good deal cheaper than three ghoulfish. And it's the stuff phantom membranes
# are made of, near enough, so it patches elytra and brews slow falling.
shapeless("soul_charge_from_ectoplasm", ["tomyen:ectoplasm", "tomyen:ectoplasm", "#minecraft:soul_fire_base_blocks"],
          "tomyen:soul_charge", 1)
shapeless("phantom_membrane_from_ectoplasm", ["tomyen:ectoplasm", "tomyen:ectoplasm"], "minecraft:phantom_membrane", 1)


def tag_add(path, entry):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


# Holy things one-shot her (see Banshee#hurtServer); holy light and silver treat her as they treat the evil.
tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), "tomyen:banshee")


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


# Vanilla sounds, re-pitched: a ghast's moans pitched up are a woman's sobbing; its screams, up a little, are the wail
# (heard 96 blocks off: it's a fixed-range event, see Banshees); a vex's hurt, down, her pain; an allay's death, slowed,
# her coming apart.
SOUNDS = OrderedDict([
    ("banshee_ambient", OrderedDict([("subtitle", "subtitles.tomyen.banshee_ambient"),
                                     ("sounds", vanilla(["mob/ghast/moan%d" % i for i in range(1, 8)], 1.45, volume=0.6))])),
    ("banshee_wail_windup", OrderedDict([("subtitle", "subtitles.tomyen.banshee_wail_windup"),
                                         ("sounds", vanilla(["mob/ghast/charge"], 0.8, attenuation_distance=32))])),
    ("banshee_wail", OrderedDict([("subtitle", "subtitles.tomyen.banshee_wail"),
                                  ("sounds", vanilla(["mob/ghast/scream%d" % i for i in range(1, 6)], 1.15, attenuation_distance=96))])),
    ("banshee_touch", OrderedDict([("subtitle", "subtitles.tomyen.banshee_touch"),
                                   ("sounds", vanilla(["mob/vex/charge%d" % i for i in (1, 2, 3)], 0.6))])),
    ("banshee_hurt", OrderedDict([("subtitle", "subtitles.tomyen.banshee_hurt"),
                                  ("sounds", vanilla(["mob/vex/hurt1", "mob/vex/hurt2"], 0.7))])),
    ("banshee_death", OrderedDict([("subtitle", "subtitles.tomyen.banshee_death"),
                                   ("sounds", vanilla(["mob/ghast/death"], 1.4))])),
    ("banshee_banished", OrderedDict([("subtitle", "subtitles.tomyen.banshee_banished"),
                                      ("sounds", vanilla(["mob/allay/death1", "mob/allay/death2"], 0.5, attenuation_distance=48))])),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.tomyen.banshee", "Banshee"),
    ("item.tomyen.banshee_spawn_egg", "Banshee Spawn Egg"),
    ("item.tomyen.ectoplasm", "Ectoplasm"),
    ("subtitles.tomyen.banshee_ambient", "Banshee sobs"),
    ("subtitles.tomyen.banshee_wail_windup", "Banshee draws breath"),
    ("subtitles.tomyen.banshee_wail", "Banshee wails"),
    ("subtitles.tomyen.banshee_touch", "Banshee's chill touch"),
    ("subtitles.tomyen.banshee_hurt", "Banshee hurts"),
    ("subtitles.tomyen.banshee_death", "Banshee fades"),
    ("subtitles.tomyen.banshee_banished", "Banshee is banished"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "banshee", "BansheeModel.java"))


def f(v):
    return f"{v:.2f}f".replace("-0.00f", "0.00f")


def java_layer():
    """createBodyLayer's body, from PARTS."""
    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in PARTS:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size, mirror, grow in cubes:
            (u, v), _ = CUBES[key]
            deform = f", new CubeDeformation({f(grow)})" if grow else ""
            builder += f"\n\t\t\t\t.texOffs({u}, {v}){'.mirror()' if mirror else ''}.addBox({', '.join(f(c) for c in frm)}, " \
                       f"{', '.join(f(c) for c in size)}{deform}){'.mirror(false)' if mirror else ''}"
        pose = (f"PartPose.offsetAndRotation({', '.join(f(c) for c in pivot)}, {', '.join(f(c) for c in rot)})"
                if any(rot) else f"PartPose.offset({', '.join(f(c) for c in pivot)})")
        lines.append(f"\t\tPartDefinition {var} = {('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", "
                     f"{builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_banshee.py\n", "\t\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose():
    return {name: [pivot, rot, (1.0, 1.0, 1.0)] for name, _, pivot, rot, _ in PARTS}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


def animate(pose, age=0.0, speed=0.0, attack=0.0, windup=0.0, scream=0.0, death=0.0, holy=False):
    """Python twin of BansheeModel.setupAnim, for the preview. Keep the two in step."""
    alive = 1.0 - death
    flare = max(windup, scream)
    banish = death if holy else 0.0
    fade = death if not holy else 0.0
    reach = math.sin(attack * math.pi)
    tremble = scream * math.sin(age * 2.7)

    # Drifting: a slow bob and sway, leaning into her movement; rising and rearing back to wail.
    add(pose, "body", dx=0.25 * speed + 0.12 * reach - 0.15 * flare + 0.35 * fade - 0.4 * banish,
        dz=0.04 * math.sin(age * 0.05) * alive + 0.03 * tremble,
        pos=(0.25 * tremble, 1.0 * math.sin(age * 0.08) * alive - 2.0 * flare + 5.0 * fade - 6.0 * banish, 0))
    add(pose, "neck", dx=-0.1 * flare + 0.5 * fade - 0.3 * banish)
    add(pose, "head", dx=0.1 * reach - 0.2 * flare + 0.4 * fade - 0.4 * banish, dz=0.08 * math.sin(age * 0.045) * alive)
    # The jaw hangs slack, trembling; it drops open through the wind-up and unhinges for the scream.
    gape = max(0.55 * windup + 0.75 * scream, banish)
    add(pose, "jaw", dx=0.04 * math.sin(age * 0.11) * alive + gape + 0.1 * tremble, pos=(0, 1.3 * max(scream, banish), 0.3 * scream))
    pose["jaw"][2] = (1.0, 1.0 + 0.35 * max(scream, banish), 1.0)

    for side, s in (("right", -1), ("left", 1)):
        phase = 0.0 if s < 0 else 1.9
        # Hair: drifting as if under water, streaming behind as she moves, flaring out wide to wail.
        add(pose, side + "_hair", dx=0.06 * math.sin(age * 0.06 + phase) + 0.3 * speed + 0.5 * flare + 0.4 * banish,
            dz=-s * (0.05 * math.sin(age * 0.07 + phase) + 0.85 * flare + 0.12 * tremble + 0.6 * banish - 0.08 * fade))
        add(pose, side + "_hair_low", dx=0.08 * math.sin(age * 0.06 + phase - 0.9) + 0.2 * speed + 0.25 * flare,
            dz=-s * (0.06 * math.sin(age * 0.08 + phase - 0.7) + 0.35 * flare + 0.15 * tremble))
        # Arms: reaching forward, swaying; thrust out to touch; flung wide and back to wail; up when banished.
        arm = side + "_arm"
        add(pose, arm, dx=0.1 * math.sin(age * 0.07 + phase) * alive - 0.45 * reach + 0.85 * flare + 1.0 * fade - 1.6 * banish,
            dy=s * 0.25 * reach - s * 0.4 * flare,
            dz=-s * (0.03 * math.sin(age * 0.09 + phase) + 1.0 * flare + 0.1 * tremble + 0.6 * banish))
        add(pose, side + "_forearm", dx=0.1 * math.sin(age * 0.07 + phase + 0.8) * alive + 0.15 * reach - 0.2 * flare + 0.2 * fade)
        add(pose, side + "_hand", dx=0.1 * math.sin(age * 0.09 + phase) * alive - 0.25 * flare)
        add(pose, side + "_fingers", dx=0.12 * math.sin(age * 0.1 + phase) * alive - 0.45 * reach - 0.5 * flare + 0.3 * fade)
    add(pose, "hair_back", dx=0.05 * math.sin(age * 0.05) + 0.45 * speed + 1.0 * flare + 0.15 * tremble + 0.8 * banish)
    add(pose, "hair_back_low", dx=0.07 * math.sin(age * 0.05 - 0.8) + 0.25 * speed + 0.35 * flare + 0.15 * tremble)

    # The gown sways; its wisps undulate in waves running root to tip, trail out behind as she moves and whip about
    # in the scream.
    whip = 1.0 + 2.0 * scream
    add(pose, "skirt", dx=0.04 * math.sin(age * 0.07) * alive + 0.15 * speed - 0.1 * flare)
    for i, (x, z, out) in enumerate(WISPS):
        ph = i * 1.3
        add(pose, f"wisp_{i}", dx=0.12 * whip * math.sin(age * 0.1 + ph) + 0.5 * speed + 0.3 * flare - 0.2 * fade,
            dz=out * (0.1 * math.cos(age * 0.08 + ph) - 0.12 * flare))
        add(pose, f"wisp_{i}_tip", dx=0.25 * whip * math.sin(age * 0.1 + ph - 0.9) + 0.3 * speed)
    add(pose, "tail", dx=0.1 * whip * math.sin(age * 0.09 + 0.5) + 0.4 * speed, dz=0.1 * math.sin(age * 0.07))
    add(pose, "tail_mid", dx=0.2 * whip * math.sin(age * 0.09 - 0.4) + 0.2 * speed, dz=0.15 * math.sin(age * 0.07 - 0.6))
    add(pose, "tail_tip", dx=0.3 * whip * math.sin(age * 0.09 - 1.3) + 0.2 * speed, dz=0.2 * math.sin(age * 0.07 - 1.2))
    return pose


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


def quads(pose):
    """Every textured face of the posed model: (corners in model space, uv rect, label, outward normal)."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot, scale = pose[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            ms = mul(m, [[scale[0], 0, 0], [0, scale[1], 0], [0, 0, scale[2]]])
            for k, (cube, frm, size, mirror, grow) in enumerate(PART_CUBES.get(child, [])):
                (u, v), _ = CUBES[cube]
                gfrm = tuple(c - grow for c in frm)
                gsize = tuple(c + 2 * grow for c in size)
                centre = tuple(o[i] + apply(ms, tuple(gfrm[j] + gsize[j] / 2 for j in range(3)))[i] for i in range(3))
                for verts, uv in cube_faces(ms, o, u, v, gfrm, gsize, size, mirror):
                    fc = tuple(sum(p[i] for p in verts) / 4 for i in range(3))
                    out.append((verts, uv, f"{child}.{cube}{k}", tuple(fc[i] - centre[i] for i in range(3))))
            walk(child, m, o)

    walk("root", [[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, gsize, size, mirror):
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + gsize[0], y0 + gsize[1], z0 + gsize[2]
    if mirror:
        x0, x1 = x1, x0
    P = lambda x, y, z: tuple(o[i] + apply(m, (x, y, z))[i] for i in range(3))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1), ([t2, t3, l3, l2], u2, v1, u22, v0), ([t0, l0, l3, t3], u0, v1, u1, v2),
             ([t1, t0, t3, t2], u1, v1, u2, v2), ([l1, t1, t2, l2], u2, v1, u3, v2), ([l0, l1, l2, l3], u3, v1, u4, v2)]
    return [(verts, (a0, b0, a1, b1)) for verts, a0, b0, a1, b1 in polys]


# Z-fighting: no two faces may share a plane, face the same way and overlap, at rest or mid-wail.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from coplanar import Face, fights
for _label, _pose in (("rest", rest_pose()), ("wail", animate(rest_pose(), age=17.0, windup=1.0, scream=1.0)),
                      ("reach", animate(rest_pose(), age=40.0, attack=0.5, speed=0.6))):
    _hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(_pose)])
    if _hits:
        for h_ in _hits:
            print("z-fight: banshee", _label, h_)
        raise SystemExit(f"banshee ({_label}): {len(_hits)} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, *_ in quads(pose) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
print(f"banshee: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f}, "
      f"bottom y {ext[1][1]:.2f} (entity feet 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


# ---------------------------------------------------------------------------------------------- preview
SKY_TOP, SKY_BOTTOM = (22, 26, 44), (44, 50, 72)


def render(pose, view, scale=8, width=300, height=420, light=(0.35, -0.8, -0.5), focus=None, tint=1.0):
    """Orthographic render, alpha-blended back to front over a night sky, back faces culled. view: 'front' (looking at
    her face), 'side' (from her right), 'three' (three-quarters) or 'back'. focus: a model point to centre."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    frags = [[] for _ in range(width * height)]
    qs = quads(pose)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs = [p[0] * ca - p[2] * sa for verts, *_ in qs for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs) + max(xs)) / 2 * scale, height - 40, 24.0
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    for verts, (ua, va, ub, vb), label, normal in qs:
        # Back-face culling, as entityTranslucentCull does: skip faces turned away from the viewer.
        if normal[0] * sa + normal[2] * ca > 1e-6:
            continue
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        shade = 0.6 + 0.4 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = ub - ua, vb - va
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua + (i + 0.5) * du / nu), TEX_W - 1)
                tv = min(int(va + (j + 0.5) * dv / nv), TEX_H - 1)
                col = TEXTURE.get(int(tu), int(tv))
                if col[3] < 26:   # the shader discards alpha under 0.1
                    continue
                g = GLOW.get(int(tu), int(tv))
                corners = []
                for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a / nu, b / nv
                    p = tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))
                    x = p[0] * ca - p[2] * sa
                    z = p[0] * sa + p[2] * ca
                    corners.append((ox + x * scale, oy + (p[1] - ly) * scale, z))
                depth = sum(c[2] for c in corners) / 4
                k_ = shade
                c = (col[0] * k_ + g[0] * (g[3] / 255), col[1] * k_ + g[1] * (g[3] / 255), col[2] * k_ + g[2] * (g[3] / 255))
                fill(frags, width, height, corners, depth, (c, col[3] / 255 * tint))
    img = []
    for py in range(height):
        sky = lerp(SKY_TOP, SKY_BOTTOM, py / height)
        for px in range(width):
            c = list(sky)
            for depth, (rgb, a) in sorted(frags[py * width + px], key=lambda q: -q[0]):
                c = [c[k] * (1 - a) + rgb[k] * a for k in range(3)]
            img.append((min(255, int(c[0])), min(255, int(c[1])), min(255, int(c[2])), 255))
    # The ground she hovers over, 0.6 blocks below her feet.
    gy = int(oy + (24.0 + 9.6 - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    return img


def fill(frags, width, height, corners, depth, colour):
    xs_ = [c[0] for c in corners]
    ys_ = [c[1] for c in corners]
    for py in range(max(0, int(min(ys_))), min(height, int(math.ceil(max(ys_))) + 1)):
        for px in range(max(0, int(min(xs_))), min(width, int(math.ceil(max(xs_))) + 1)):
            if inside(corners, px + 0.5, py + 0.5):
                frags[py * width + px].append((depth, colour))


def inside(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cross) < 1e-9:
            continue
        s = 1 if cross > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return sign != 0


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k, im in enumerate(images):
        bx, by = (k % cols) * w, (k // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    rest = lambda: animate(rest_pose(), age=10.0)
    face_pts = [p for verts, uv, label, n in quads(rest()) if label.startswith("head.skull") for p in verts]
    centre = tuple(sum(p[i] for p in face_pts) / len(face_pts) for i in range(3))
    scream = animate(rest_pose(), age=13.0, windup=1.0, scream=1.0)
    s_pts = [p for verts, uv, label, n in quads(scream) if label.startswith("head.skull") for p in verts]
    s_centre = tuple(sum(p[i] for p in s_pts) / len(s_pts) for i in range(3))
    views = [render(rest(), "front", scale=7), render(rest(), "side", scale=7), render(rest(), "three", scale=7),
             render(animate(rest_pose(), age=30.0, speed=0.8, attack=0.5), "three", scale=7),
             render(animate(rest_pose(), age=20.0, windup=1.0), "three", scale=7),
             render(scream, "front", scale=7),
             render(animate(rest_pose(), age=20.0, death=0.6, holy=True), "three", scale=7, tint=0.7),
             render(rest(), "three", scale=22, focus=centre),
             render(scream, "front", scale=22, focus=(s_centre[0], s_centre[1] + 1.0, s_centre[2]))]
    out, bw, bh = board(views, 300, 420, 3)
    png(os.path.join(PREVIEW, "preview_banshee.png"), bw, bh, out)
    s = 4
    big = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
           for y in range(TEX_H * s) for x in range(TEX_W * s)]
    for y in range(TEX_H * s):
        for x in range(TEX_W * s):
            c = TEXTURE.get(x // s, y // s)
            if c[3]:
                a = c[3] / 255
                bg = big[y * TEX_W * s + x]
                big[y * TEX_W * s + x] = tuple(int(bg[k] * (1 - a) + c[k] * a) for k in range(3)) + (255,)
    png(os.path.join(PREVIEW, "preview_banshee_texture.png"), TEX_W * s, TEX_H * s, big)
    print("preview written to", PREVIEW)
print("done")
