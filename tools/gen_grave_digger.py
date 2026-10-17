"""Builds the Grave Digger: its model (the cube layout in PARTS, written into client/gravedigger/GraveDiggerModel.java
between the GENERATED markers), its entity texture and the glow layer for its eyes and lantern, its spawn egg, the
Gravedigger's Shovel item, and their item models and definitions. On the data side it writes the grave digger's loot
table, the block tags that say what it can dig (tomyen:grave_digger_diggable, tomyen:grave_digger_soft_stone) and what
it never can (tomyen:grave_digger_proof), and adds it and its shovel to the entity and item tags it belongs in. Its
sounds (vanilla zombie, shovel and gravel sounds, re-pitched) and lang entries are merged into sounds.json and
en_us.json.

The texture is painted in smooth ramps from a few limited palettes: pallid grey-green skin over a gaunt skull with
sunken sockets and pale pinprick eyes that glow, a dusty dark-brown greatcoat with a ragged, mud-hemmed skirt and patched
sleeves, a grimy shirt, a rope belt, tweed flat cap, dark wool trousers, muddy boots, an ash-wood shovel with a riveted
iron socket and a worn, mud-caked spade blade, and a small lantern on his hip. No noise, no dithering; a rerun gives the
same files.

The model is checked for z-fighting at rest (tools/coplanar.py), and its extents are printed (it should stand on y 24).

With --preview DIR it also renders, into DIR, preview_grave_digger.png (front, side and three-quarters at rest; a
shambling walk; three frames of the overhead shovel chop; digging down, forward and up; the head up close) and
preview_grave_digger_texture.png, plus preview_gravedigger_shovel.png (the item next to vanilla's iron shovel), using
the same cubes, pivots and UV layout as the Java model and a Python twin of its animation.

Usage: python3 tools/gen_grave_digger.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zlib, zipfile
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
DATA_MC = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "minecraft"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png_bytes(data):
    """8-bit RGBA or RGB, non-interlaced only."""
    pos, idat, w, h, ct = 8, b"", 0, 0, 6
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
            ct = body[9]
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = 4 if ct == 6 else 3
    raw = zlib.decompress(idat)
    stride, out, prev = w * bpp, [], bytearray(w * bpp)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out += [tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)]
        prev = line
    return w, h, out


def read_png(path):
    return read_png_bytes(open(path, "rb").read())


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


# ---------------------------------------------------------------------------------------------- palette
# Every material is a short ramp, light to dark; faces shade down them smoothly.
SKIN = [(152, 160, 136), (130, 139, 116), (109, 118, 97), (89, 97, 80), (70, 77, 64), (52, 57, 48)]
COAT = [(112, 98, 82), (94, 81, 68), (77, 66, 56), (62, 53, 45), (48, 41, 35), (35, 30, 26)]
BRUISE = [(112, 104, 104), (88, 78, 82)]   # the rotting tip of his nose
PATCH = [(112, 100, 78), (94, 83, 64), (76, 67, 52)]
SHIRT = [(172, 162, 136), (146, 137, 113), (120, 112, 92)]
TROUSERS = [(86, 88, 98), (70, 72, 81), (56, 57, 65), (43, 44, 51)]
CAP = [(128, 110, 86), (108, 92, 71), (89, 75, 58), (70, 59, 46)]
BOOT = [(94, 66, 45), (76, 53, 36), (59, 41, 28), (43, 30, 21)]
MUD = [(122, 98, 66), (100, 80, 54), (80, 64, 43)]
SOLE = [(44, 36, 30), (32, 26, 22)]
WOOD = [(156, 118, 74), (132, 98, 60), (110, 80, 49), (88, 64, 39), (66, 48, 29)]
IRON = [(200, 202, 204), (168, 170, 174), (136, 138, 142), (106, 108, 112), (78, 80, 84), (56, 58, 62)]
RUST = [(156, 92, 54), (124, 70, 42), (96, 54, 34)]
ROPE = [(170, 146, 100), (142, 120, 80), (112, 94, 62)]
HAIR = [(132, 130, 120), (108, 106, 98), (84, 82, 76), (62, 61, 57)]
TOOTH = (198, 188, 150)
TOOTH_SHADE = (162, 152, 118)
MOUTH = (40, 26, 24)
SOCKET = [(46, 50, 42), (30, 33, 28)]
EYE = (206, 232, 150)          # pale, ghostly green pinpricks: they glow
EYE_HOT = (236, 250, 206)
NAIL = (168, 160, 112)
STITCH = (150, 136, 104)
LANTERN = [(84, 84, 88), (62, 62, 66), (42, 42, 46)]
GLASS = [(250, 214, 120), (236, 176, 82), (210, 140, 60)]   # lit from inside: glows
FLAME = (255, 240, 190)

TEX_W, TEX_H = 128, 64


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1 (no banding beyond the texel grid, no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def down(cols, t0, t1):
    """A face shading down a ramp from t0 at its top row to t1 at its bottom."""
    return lambda x, y, w, h: ramp(cols, t0 + (t1 - t0) * (y / max(1, h - 1)))


def across(cols, t0, t1):
    """A face shading along its width (for long faces lying on their side)."""
    return lambda x, y, w, h: ramp(cols, t0 + (t1 - t0) * (x / max(1, w - 1)))


def flat(c):
    return lambda x, y, w, h: c


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out. 'top' and
    'bottom' are as seen in the world (the model is upside down); in both the front edge is the last row. In 'right'
    (the cube's -x side, the creature's right) the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, uv, size, painters):
    """painters: face name -> f(x, y, fw, fh) -> colour (or None for clear)."""
    u, v = uv
    w, h, d = size
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name)
        if f is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = f(x, y, fw, fh)
                canvas.set(fu + x, fv + y, rgba(c) if c is not None and len(c) == 3 else (c or CLEAR))


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored). Mirrored cubes
# share their texture with the unmirrored one of the same key. Model space is the game's: y down, the front toward
# -z, feet at y 24. This is the one place the shape is defined: it's written into client/gravedigger/GraveDiggerModel.java
# (between the GENERATED markers), painted here, and drawn by the preview.
#
# A gaunt, stooped corpse of a gravedigger. A narrow chest leaning forward under a hunch of shoulder blades, in a
# long greatcoat: a turned-up collar, lapels open on a grimy shirt, a rope belt, a pocket with a trowel stuck in it,
# a little lantern hanging off his hip, and the coat's skirt split into a back panel and two front flaps that hang
# to his knees and swing with his legs. A long skull thrust forward on a thin neck: a heavy brow over sunken sockets,
# a crooked nose, a hanging jaw, grey hair straggling out under a tweed flat cap with a short peak. Bony arms in
# patched sleeves with ragged cuffs; the right carries the shovel over his shoulder, the left reaches out. Thin legs
# in dark trousers and big, muddy hobnailed boots. The shovel: a D-grip, a long ash shaft, an iron collar and riveted
# socket, and a broad spade blade with foot treads and a worn point.
LEAN = 0.22
SIDES = (("right", -1, False), ("left", 1, True))
# Rest poses of the arms and shovel (the shovel over his right shoulder; the left arm reaching).
R_ARM = (-0.95, 0.0, 0.18)
R_FOREARM = (-1.55, 0.0, 0.0)
SHOVEL = (-0.35, 0.0, 0.0)
L_ARM = (-1.2, 0.0, -0.1)
L_FOREARM = (-0.35, 0.0, 0.0)


def parts():
    p = [
        ("body", None, (0.0, 12.0, 0.5), (LEAN, 0.0, 0.0), [
            ("torso", (-3.5, -11.0, -2.0), (7, 11, 4), False),
            ("hump", (-3.0, -11.5, 0.5), (6, 4, 2), False),
            ("collar", (-3.5, -13.0, 1.5), (7, 2, 2), False),
            ("lapel", (-3.0, -10.5, -2.6), (2, 5, 1), False),
            ("lapel", (1.0, -10.5, -2.6), (2, 5, 1), True),
            ("belt", (-4.0, -2.0, -2.5), (8, 1, 5), False),
            ("pocket", (-3.0, -5.5, -2.5), (2, 3, 1), False),
            ("trowel", (-2.5, -7.5, -2.3), (1, 3, 1), False)]),
        ("lantern", "body", (5.0, -1.5, -0.5), (-LEAN, 0.0, 0.0), [
            ("lantern_hook", (-0.5, -1.0, -0.5), (1, 1, 1), False),
            ("lantern_cap", (-1.5, 0.0, -1.5), (3, 1, 3), False),
            ("lantern_glass", (-1.0, 1.0, -1.0), (2, 3, 2), False),
            ("lantern_base", (-1.5, 4.0, -1.5), (3, 1, 3), False)]),
        ("coat_back", "body", (0.0, -1.0, 2.0), (-LEAN + 0.1, 0.0, 0.0), [
            ("coat_back", (-4.5, 0.0, -0.5), (9, 10, 1), False)]),
        ("head", "body", (0.0, -11.0, -1.0), (-LEAN + 0.05, 0.0, 0.1), [
            ("neck", (-1.5, -1.5, -1.5), (3, 2, 3), False),
            ("skull", (-3.5, -9.5, -4.0), (7, 8, 7), False),
            ("brow", (-3.0, -7.0, -4.6), (6, 1, 1), False),
            ("nose", (-0.5, -6.0, -4.8), (1, 2, 1), False),
            ("ear", (-4.5, -6.0, -1.0), (1, 2, 1), False),
            ("ear", (3.5, -6.0, -1.0), (1, 2, 1), True),
            ("hair", (-4.0, -7.5, 0.5), (8, 4, 3), False)]),
        ("jaw", "head", (0.0, -2.5, -1.0), (0.1, 0.0, 0.0), [
            ("jaw", (-2.5, 0.0, -3.3), (5, 2, 4), False)]),
        ("cap", "head", (0.0, -9.5, -0.5), (0.12, 0.0, 0.0), [
            ("crown", (-4.0, -2.0, -4.5), (8, 2, 8), False),
            ("peak", (-3.5, -0.5, -7.0), (7, 1, 3), False),
            ("button", (-0.5, -2.5, -1.5), (1, 1, 1), False)]),
        # The shovel, in his right fist: the shaft runs through the fist along -z to the blade, the D-grip behind.
        ("shovel", "right_forearm", (0.0, 7.5, 0.0), SHOVEL, [
            ("shaft", (-0.5, -0.5, -10.0), (1, 1, 12), False),
            ("grip_side", (-1.5, -0.5, 2.0), (1, 1, 2), False),
            ("grip_side", (0.5, -0.5, 2.0), (1, 1, 2), True),
            ("grip_bar", (-1.5, -0.5, 4.0), (3, 1, 1), False),
            ("ferrule", (-1.0, -1.0, -11.0), (2, 2, 3), False),
            ("socket", (-1.0, -0.5, -13.0), (2, 1, 2), False),
            ("blade", (-2.5, -0.5, -19.0), (5, 1, 6), False),
            ("tread", (-2.5, -1.5, -14.0), (2, 1, 2), False),
            ("tread", (0.5, -1.5, -14.0), (2, 1, 2), True),
            ("tip", (-1.5, -0.5, -20.0), (3, 1, 1), False),
            ("point", (-0.5, -0.5, -21.0), (1, 1, 1), False)]),
    ]
    for side, s, mirror in SIDES:
        arm, forearm = (R_ARM, R_FOREARM) if side == "right" else (L_ARM, L_FOREARM)
        p += [
            # The coat's front flaps hang from the belt either side, and swing forward with the legs.
            ("coat_" + side, "body", (s * 2.4, -1.0, -2.1), (-LEAN, 0.0, 0.0), [
                ("coat_flap", (-1.5, 0.0, -0.5), (3, 9, 1), mirror)]),
            (side + "_arm", "body", (s * 5.0, -10.0, 0.0), arm, [
                ("shoulder", (-2.0, -2.0, -2.0), (4, 3, 4), mirror),
                ("sleeve", (-1.5, -1.5, -1.5), (3, 7, 3), mirror)]),
            (side + "_forearm", side + "_arm", (0.0, 5.0, 0.0), forearm, [
                ("cuff", (-2.0, -0.5, -2.0), (4, 3, 4), mirror),
                ("forearm", (-1.0, 0.0, -1.0), (2, 6, 2), mirror),
                ("hand", (-1.5, 6.0, -1.5), (3, 3, 3), mirror)]
             # The reaching hand's long, crooked fingers.
             + ([("finger", (-1.3, 9.0, -1.3), (1, 3, 1), False), ("finger", (0.3, 9.0, -1.3), (1, 3, 1), True)]
                if side == "left" else [])),
            (side + "_leg", None, (s * 2.2, 12.0, 0.5), (0.0, 0.0, 0.0), [
                ("thigh", (-1.5, 0.0, -1.5), (3, 6, 3), mirror)]),
            (side + "_shin", side + "_leg", (0.0, 6.0, 0.0), (0.0, 0.0, 0.0), [
                ("shin", (-1.5, 0.0, -1.5), (3, 3, 3), mirror),
                ("boot", (-2.0, 2.0, -2.5), (4, 3, 5), mirror),
                ("toecap", (-2.0, 3.0, -3.5), (4, 2, 1), mirror),
                ("sole", (-2.0, 5.0, -4.0), (4, 1, 7), mirror)]),
        ]
    # Parents first, so the Java can add children to them.
    order, done = [], set()
    pending = list(p)
    while pending:
        for part in list(pending):
            if part[1] is None or part[1] in done:
                order.append(part)
                done.add(part[0])
                pending.remove(part)
    return order


PARTS = parts()
TREE = {}
for _name, _parent, *_ in PARTS:
    TREE.setdefault(_parent or "root", []).append(_name)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS}


def pack():
    """Texture offsets for each cube key, shelf-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS:
        for key, _, size, _ in cubes:
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
HEM = [3, 1, 2, 0, 3, 2, 1, 3, 0, 2, 3, 1, 2, 0, 1, 3]


def ragged(base, cut_rows, phase=0):
    """Cloth with a torn hem: the last rows of a face go missing column by column."""
    def f(x, y, w, h):
        k = HEM[(x + phase) % len(HEM)] * cut_rows // 3
        if (h - 1 - y) < k:
            return None
        return base(x, y, w, h)
    return f


def muddy(base, rows, phase=0, cols=MUD):
    """The lowest rows of a face caked in mud, its top edge stepping up and down."""
    def f(x, y, w, h):
        edge = h - rows + (1 if HEM[(x + phase) % len(HEM)] >= 2 else 0)
        if y >= edge:
            return ramp(cols, (y - edge) / max(1, rows))
        return base(x, y, w, h)
    return f


def outline_bottom(base, cols, t):
    def f(x, y, w, h):
        return ramp(cols, t) if y == h - 1 else base(x, y, w, h)
    return f


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    glow = Canvas(TEX_W, TEX_H)
    P = lambda key, painters: paint(tex, *CUBES[key], painters)

    def box(cols, top, side0, side1, bottom):
        """A plain box in one material: lit top, sides shading down, dark underside."""
        s = down(cols, side0, side1)
        return {"top": flat(ramp(cols, top)), "bottom": flat(ramp(cols, bottom)), "front": s, "back": s, "right": s,
                "left": s}

    # ---- the coat
    def torso_front(x, y, w, h):
        # Open down the middle on a grimy shirt, with a dark drawstring at the throat; the coat either side.
        if 2 <= x <= 4 and y < h - 2:
            if x == 3 and y in (1, 2):
                return COAT[5]
            return ramp(SHIRT, 0.1 + 0.8 * y / (h - 1) + (0.25 if x != 3 else 0.0))
        return ramp(COAT, 0.15 + 0.6 * y / (h - 1))

    def torso_back(x, y, w, h):
        # A seam down the middle and a square patch, stitched round, between the shoulder blades.
        if 1 <= x <= 3 and 4 <= y <= 6:
            return PATCH[0] if y == 4 else PATCH[1] if y == 5 else PATCH[2]
        if x == 5:
            return ramp(COAT, 0.45 + 0.4 * y / (h - 1))
        return ramp(COAT, 0.2 + 0.6 * y / (h - 1))
    P("torso", {"front": torso_front, "back": torso_back, "right": down(COAT, 0.2, 0.8), "left": down(COAT, 0.2, 0.8),
                "top": flat(COAT[2]), "bottom": flat(COAT[4])})
    P("hump", box(COAT, 0.05, 0.1, 0.5, 0.6))
    P("collar", {"top": flat(COAT[1]), "back": outline_bottom(down(COAT, 0.05, 0.4), COAT, 0.7),
                 "right": down(COAT, 0.1, 0.5), "left": down(COAT, 0.1, 0.5), "front": flat(COAT[4]),
                 "bottom": flat(COAT[4])})

    def lapel_front(x, y, w, h):
        # Turned back, catching the light at the top; its outer edge in shadow.
        return ramp(COAT, (0.0 if x == 0 else 0.15) + 0.5 * y / (h - 1))
    P("lapel", {"front": lapel_front, "top": flat(COAT[0]), "bottom": flat(COAT[3]), "right": flat(COAT[3]),
                "left": flat(COAT[2]), "back": flat(COAT[4])})

    def rope(x, y, w, h):
        # Twisted: a regular diagonal lay, light and dark strands.
        return ROPE[0] if (x + y) % 3 == 0 else ROPE[1] if (x + y) % 3 == 1 else ROPE[2]
    P("belt", {"front": rope, "back": rope, "right": rope, "left": rope, "top": flat(ROPE[1]), "bottom": flat(ROPE[2])})
    P("pocket", {"front": lambda x, y, w, h: COAT[0] if y == 0 else ramp(COAT, 0.35 + 0.3 * y / (h - 1)),
                 "top": flat(COAT[5]), "bottom": flat(COAT[4]), "right": flat(COAT[3]), "left": flat(COAT[3]),
                 "back": flat(COAT[4])})
    # The trowel's wooden handle sticking out of the pocket, its iron ferrule at the bottom.
    P("trowel", {"front": lambda x, y, w, h: IRON[3] if y == h - 1 else ramp(WOOD, 0.1 + 0.3 * y), "back": down(WOOD, 0.3, 0.7),
                 "right": down(WOOD, 0.2, 0.6), "left": down(WOOD, 0.2, 0.6), "top": flat(WOOD[1]), "bottom": flat(IRON[4])})

    # The coat's skirt: dust-brown going darker down to a ragged, mud-caked hem.
    def skirt(t0, phase):
        return ragged(muddy(down(COAT, t0, t0 + 0.55), 2, phase), 3, phase + 5)
    P("coat_back", {"back": skirt(0.2, 0), "front": skirt(0.5, 3), "right": skirt(0.3, 7), "left": skirt(0.3, 11),
                    "top": flat(COAT[2]), "bottom": flat(COAT[5])})
    P("coat_flap", {"front": skirt(0.15, 2), "back": skirt(0.55, 9), "right": skirt(0.3, 4), "left": skirt(0.35, 6),
                    "top": flat(COAT[2]), "bottom": flat(COAT[5])})

    # ---- the lantern (its glass glows)
    P("lantern_hook", box(LANTERN, 0.0, 0.2, 0.6, 1.0))
    P("lantern_cap", box(LANTERN, 0.0, 0.3, 0.6, 1.0))
    P("lantern_base", box(LANTERN, 0.2, 0.4, 0.9, 1.0))

    def glass(x, y, w, h):
        # Amber panes lit from inside, a bright flame low in the middle, a dark iron frame down each corner.
        if x in (0, w - 1) and w > 1 and False:
            return LANTERN[1]
        if y == h - 2:
            return FLAME
        return ramp(GLASS, abs(y - (h - 2)) / (h - 1))
    gl = {"front": glass, "back": glass, "right": glass, "left": glass, "top": flat(GLASS[2]), "bottom": flat(GLASS[2])}
    P("lantern_glass", gl)
    paint(glow, *CUBES["lantern_glass"], gl)

    # ---- the head
    EYES = {(2, 4): EYE, (4, 4): EYE_HOT}
    SOCKETS = {(1, 3), (2, 3), (1, 4), (4, 3), (5, 3), (5, 4)}

    def skull_front(x, y, w, h):
        if (x, y) in EYES:
            return EYES[(x, y)]
        if (x, y) in SOCKETS:
            return SOCKET[1] if y == 3 else SOCKET[0]
        if y == 5 and x in (0, 1, 5, 6):
            return SKIN[1]                       # cheekbones catching the light
        if y == 6 and x in (1, 2, 4, 5):
            return SKIN[4]                       # hollow cheeks
        if y >= 7:
            return MOUTH
        if y == 2:
            return SKIN[3]                       # the shadow under the brow
        # The face falls away into shadow at the temples and down the jaw.
        return ramp(SKIN, 0.1 + 0.3 * y / (h - 1) + 0.12 * abs(x - 3))

    def skull_side(front_last):
        # Hair straggling down the back two thirds, a sunken temple near the front.
        def f(x, y, w, h):
            back = (w - 1 - x) if front_last else x     # 0 at the back
            if back <= 3 and y <= 3 + (3 - back) // 2:
                return ramp(HAIR, 0.15 + 0.15 * y + 0.1 * (3 - back))
            if y in (2, 3) and back >= w - 2:
                return SKIN[3]
            return ramp(SKIN, 0.25 + 0.45 * y / (h - 1))
        return f

    def skull_back(x, y, w, h):
        return ramp(HAIR, 0.1 + 0.6 * y / (h - 1)) if y <= 5 else ramp(SKIN, 0.55 + 0.1 * y / h)

    def skull_top(x, y, w, h):
        return ramp(HAIR, 0.1 + 0.2 * (1 - y / (h - 1)))
    P("skull", {"front": skull_front, "right": skull_side(True), "left": skull_side(False), "back": skull_back,
                "top": skull_top, "bottom": flat(SKIN[4])})
    su, sv = faces(*CUBES["skull"][0], *CUBES["skull"][1])["front"][:2]
    for (x, y), c in EYES.items():
        glow.set(su + x, sv + y, rgba(c))

    P("neck", {"front": lambda x, y, w, h: SKIN[3] if x == 1 else SKIN[2], "back": flat(SKIN[3]), "right": flat(SKIN[3]),
               "left": flat(SKIN[3]), "top": flat(SKIN[3]), "bottom": flat(SKIN[4])})
    P("brow", {"front": lambda x, y, w, h: SKIN[1], "top": flat(SKIN[1]), "bottom": flat(SOCKET[1]),
               "right": flat(SKIN[2]), "left": flat(SKIN[2]), "back": flat(SKIN[3])})
    # A crooked nose going dark at the tip, the nostrils black.
    P("nose", {"front": lambda x, y, w, h: BRUISE[0] if y == 0 else BRUISE[1], "right": flat(BRUISE[1]),
               "left": flat(BRUISE[0]), "top": flat(SKIN[2]), "bottom": flat(MOUTH), "back": flat(SKIN[3])})
    P("ear", {"front": flat(SKIN[3]), "back": flat(SKIN[4]), "right": flat(SKIN[3]), "left": flat(SKIN[3]),
              "top": flat(SKIN[2]), "bottom": flat(SKIN[4])})

    def hair_side(x, y, w, h):
        # Lank grey strands, light and dark in turn, the ends straggling.
        return ramp(HAIR, (0.1 if x % 2 == 0 else 0.45) + 0.4 * y / (h - 1))
    P("hair", {"back": ragged(hair_side, 2, 1), "right": ragged(hair_side, 2, 4), "left": ragged(hair_side, 2, 8),
               "front": ragged(hair_side, 2, 3), "top": flat(HAIR[1]), "bottom": None})

    def jaw_front(x, y, w, h):
        if y == 0:
            return TOOTH if x % 2 == 0 else TOOTH_SHADE if x in (1, 3) else MOUTH
        return SKIN[3]
    P("jaw", {"front": jaw_front, "right": lambda x, y, w, h: SKIN[3] if y else MOUTH,
              "left": lambda x, y, w, h: SKIN[3] if y else MOUTH, "back": flat(SKIN[4]),
              "top": flat(MOUTH), "bottom": flat(SKIN[4])})

    # The flat cap: tweed, lighter where it catches the light, a seam down the crown, the peak's underside in shadow.
    def crown_top(x, y, w, h):
        if x == w // 2 and y < h - 1:
            return CAP[2]
        return ramp(CAP, 0.05 + 0.35 * (1 - y / (h - 1)))
    P("crown", {"top": crown_top, "front": down(CAP, 0.1, 0.5), "back": down(CAP, 0.4, 0.8), "right": down(CAP, 0.25, 0.65),
                "left": down(CAP, 0.25, 0.65), "bottom": flat(CAP[3])})
    P("peak", {"top": lambda x, y, w, h: CAP[2] if y == h - 1 else ramp(CAP, 0.1 + 0.15 * y), "bottom": flat(COAT[5]),
               "front": flat(CAP[2]), "right": flat(CAP[3]), "left": flat(CAP[3]), "back": flat(CAP[3])})
    P("button", box(CAP, 0.0, 0.2, 0.4, 0.6))

    # ---- arms: patched coat sleeves, ragged cuffs, bony grey forearms and hands with yellowed nails
    P("shoulder", {"top": flat(COAT[1]), "front": down(COAT, 0.15, 0.45), "back": down(COAT, 0.3, 0.6),
                   "right": down(COAT, 0.15, 0.5), "left": down(COAT, 0.3, 0.6), "bottom": flat(COAT[4])})

    def sleeve_out(x, y, w, h):
        # A square elbow patch, stitched round.
        if 3 <= y <= 4:
            return PATCH[0] if y == 3 else PATCH[1]
        return ramp(COAT, 0.2 + 0.5 * y / (h - 1))
    P("sleeve", {"right": sleeve_out, "left": sleeve_out, "front": down(COAT, 0.2, 0.6), "back": down(COAT, 0.3, 0.7),
                 "top": flat(COAT[2]), "bottom": flat(COAT[4])})
    cuff = lambda ph, t0: ragged(down(COAT, t0, t0 + 0.4), 2, ph)
    P("cuff", {"front": cuff(0, 0.2), "back": cuff(4, 0.4), "right": cuff(9, 0.3), "left": cuff(13, 0.3),
               "top": flat(COAT[2]), "bottom": None})

    def forearm(x, y, w, h):
        return ramp(SKIN, 0.2 + 0.45 * y / (h - 1) + (0.15 if x == w - 1 else 0.0))
    P("forearm", {"front": forearm, "back": forearm, "right": forearm, "left": forearm, "top": flat(SKIN[3]),
                  "bottom": flat(SKIN[4])})

    def hand_side(x, y, w, h):
        # Bony knuckles lit along the top, the back of the hand darkening below.
        return SKIN[1] if y == 0 else ramp(SKIN, 0.4 + 0.25 * y / (h - 1))
    P("hand", {"front": hand_side, "back": hand_side, "right": hand_side, "left": hand_side, "top": flat(SKIN[2]),
               "bottom": flat(SKIN[4])})
    # Long fingers, darkening down to yellowed, broken nails.
    finger = lambda x, y, w, h: NAIL if y == h - 1 else ramp(SKIN, 0.45 + 0.2 * y)
    P("finger", {"front": finger, "back": finger, "right": finger, "left": finger, "top": flat(SKIN[3]),
                 "bottom": flat(NAIL)})

    # ---- legs: dark wool trousers, a lighter patch on the knee, big muddy hobnailed boots
    def thigh_front(x, y, w, h):
        if y in (4, 5) and x <= 1:
            return PATCH[2] if y == 4 else PATCH[1]
        return ramp(TROUSERS, 0.1 + 0.6 * y / (h - 1))
    P("thigh", {"front": thigh_front, "back": down(TROUSERS, 0.3, 0.9), "right": down(TROUSERS, 0.2, 0.8),
                "left": down(TROUSERS, 0.2, 0.8), "top": flat(TROUSERS[1]), "bottom": flat(TROUSERS[3])})
    P("shin", {k: muddy(down(TROUSERS, 0.3, 0.8), 1, ph, MUD) for k, ph in
               (("front", 1), ("back", 5), ("right", 9), ("left", 3))} | {"top": flat(TROUSERS[2]), "bottom": flat(TROUSERS[3])})

    def boot_side(ph):
        def f(x, y, w, h):
            if y == 0:
                return BOOT[0]
            return muddy(down(BOOT, 0.15, 0.6), 1, ph)(x, y, w, h)
        return f

    def boot_front(x, y, w, h):
        # Crossed laces up the front.
        if y < h - 1 and x in (1, 2):
            return ROPE[2] if (x + y) % 2 == 0 else BOOT[3]
        return boot_side(2)(x, y, w, h)
    P("boot", {"front": boot_front, "back": boot_side(6), "right": boot_side(10), "left": boot_side(13),
               "top": lambda x, y, w, h: BOOT[3] if 1 <= x <= w - 2 and 1 <= y <= h - 2 else BOOT[1],
               "bottom": flat(SOLE[1])})
    P("toecap", {"top": lambda x, y, w, h: BOOT[0], "front": muddy(down(BOOT, 0.1, 0.4), 1, 4),
                 "right": muddy(down(BOOT, 0.2, 0.5), 1, 7), "left": muddy(down(BOOT, 0.2, 0.5), 1, 2),
                 "back": flat(BOOT[3]), "bottom": flat(SOLE[1])})

    def sole_side(x, y, w, h):
        return MUD[2] if x % 3 == 1 else SOLE[0]
    P("sole", {"top": lambda x, y, w, h: MUD[1] if x in (0, w - 1) or y in (0, h - 1) else SOLE[0],
               "front": sole_side, "back": sole_side, "right": sole_side, "left": sole_side,
               # Hobnails on the underside.
               "bottom": lambda x, y, w, h: IRON[3] if x % 2 == 1 and y % 2 == 1 else SOLE[1]})

    # ---- the shovel
    # The shaft lies along z, so its long faces are 'right', 'left' (12 wide) and 'top', 'bottom' (12 deep). Ash,
    # darkened and polished where his fist has worn it (near the grip end).
    def shaft_long(x, y, w, h, along):
        # along: 0 at the blade end, 1 at the grip end.
        worn = 0.35 if along > 0.75 else 0.0
        return ramp(WOOD, 0.15 + 0.35 * (1 - along) + worn + (0.15 if (x + y) % 5 == 0 else 0))
    P("shaft", {"right": lambda x, y, w, h: shaft_long(x, y, w, h, x / (w - 1)),        # front edge last column
                "left": lambda x, y, w, h: shaft_long(x, y, w, h, 1 - x / (w - 1)),
                "top": lambda x, y, w, h: shaft_long(x, y, w, h, 1 - y / (h - 1)),      # front edge last row
                "bottom": lambda x, y, w, h: shaft_long(x, y, w, h, 1 - y / (h - 1)),
                "front": flat(WOOD[2]), "back": flat(WOOD[3])})
    P("grip_side", box(WOOD, 0.1, 0.3, 0.6, 0.8))
    P("grip_bar", {"top": flat(WOOD[0]), "bottom": flat(WOOD[2]), "front": flat(WOOD[1]), "back": flat(WOOD[0]),
                   "right": flat(WOOD[2]), "left": flat(WOOD[2])})

    P("ferrule", {"right": across(IRON, 0.5, 0.2), "left": across(IRON, 0.2, 0.5), "top": down(IRON, 0.1, 0.4),
                 "bottom": down(IRON, 0.5, 0.8), "front": flat(IRON[3]), "back": flat(IRON[2])})

    def socket(x, y, w, h):
        return IRON[0] if (x, y) in ((0, 0), (1, 1)) else ramp(IRON, 0.45 + 0.1 * y)     # a rivet head each side
    P("socket", {"top": socket, "bottom": socket, "right": flat(IRON[3]), "left": flat(IRON[3]), "front": flat(IRON[4]),
                 "back": flat(IRON[3])})

    def blade_face(x, y, w, h, dark=0.0):
        # Darker and rusty at the socket end (first row), worn bright at the cutting edge (last row), caked in mud
        # across the lower third.
        t = y / (h - 1)
        if y == h - 1:
            return IRON[0]
        if t >= 0.6:
            return MUD[0] if (x + y) % 3 else MUD[1]
        if y == 0 and x in (0, w - 1):
            return RUST[1]
        return ramp(IRON, 0.25 + dark + 0.25 * (1 - t))
    P("blade", {"top": blade_face, "bottom": lambda x, y, w, h: blade_face(x, y, w, h, 0.15),
                "front": flat(IRON[0]), "back": flat(IRON[3]), "right": across(IRON, 0.5, 0.1),
                "left": across(IRON, 0.1, 0.5)})
    P("tread", box(IRON, 0.1, 0.3, 0.5, 0.7))
    edge = {"top": flat(IRON[1]), "bottom": flat(IRON[2]), "front": flat(IRON[0]), "back": flat(IRON[2]),
            "right": flat(IRON[1]), "left": flat(IRON[1])}
    P("tip", edge)
    P("point", edge)

    tex.save("entity/grave_digger/grave_digger.png")
    glow.save("entity/grave_digger/grave_digger_glow.png")
    return tex


TEXTURE = paint_entity()
GLOWING = set(GLASS) | {FLAME, EYE, EYE_HOT}


# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# The Gravedigger's Shovel: vanilla's shovel layout (blade top right, handle running down to the bottom left) and its
# iron and wood colours, but its own silhouette: a long, broad spade blade tapering to a worn point with mud caked
# across its end, a dark riveted socket, a long shaft and a T-grip across the end. Drawn in the shovel's own frame
# (along the handle, and across it) and rasterised, then outlined as vanilla outlines its tools.
V_IRON = [(255, 255, 255), (216, 216, 216), (193, 193, 193), (150, 150, 150), (110, 110, 110)]
V_WOOD = {"light": (137, 103, 39), "mid": (104, 78, 30), "dark": (40, 30, 11), "edge": (73, 54, 21)}
V_OUTLINE = (24, 24, 24)


def shovel_sprite():
    c = Canvas(16, 16)
    kind = {}
    r2 = math.sqrt(2.0)
    for y in range(16):
        for x in range(16):
            # Base of the blade (where the socket meets it) at (8.5, 7.5); 'along' runs up and right toward the tip,
            # 'across' from the upper-left edge (negative) to the lower-right (positive).
            px, py = x + 0.5 - 8.5, y + 0.5 - 7.5
            along, across = (px - py) / r2, (px + py) / r2
            half = 2.75 - max(0.0, along - 4.6) * 1.15
            if 0.0 <= along <= 6.9 and abs(across) <= half:
                kind[(x, y)] = ("blade", along, across)
            elif -1.6 <= along < 0.0 and abs(across) <= 0.75:
                kind[(x, y)] = ("socket", along, across)
            elif -9.6 <= along < -1.6 and abs(across) <= 0.75:
                kind[(x, y)] = ("shaft", along, across)
            elif -11.1 <= along < -9.6 and abs(across) <= 2.2:
                kind[(x, y)] = ("grip", along, across)
    for (x, y), (k, along, across) in kind.items():
        if k == "blade":
            if along > 4.2 and across > -1.2:
                col_ = MUD[0] if along > 5.2 else MUD[1]
            else:
                col_ = ramp(V_IRON, (across + 2.75) / 5.5 * 0.85 + 0.1 * along / 6.9)
        elif k == "socket":
            col_ = (193, 193, 193) if (x, y) == (7, 8) else (68, 68, 68)
        elif k == "shaft":
            col_ = V_WOOD["light"] if across < -0.2 else V_WOOD["mid"] if across < 0.35 else V_WOOD["dark"]
        else:
            col_ = V_WOOD["mid"] if across < 0.0 else V_WOOD["edge"]
        c.set(x, y, rgba(col_))
    # Outline: iron gets vanilla's near-black, wood its dark brown.
    for y in range(16):
        for x in range(16):
            if (x, y) in kind:
                continue
            near = [kind[(x + dx, y + dy)][0] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y + dy) in kind]
            if near:
                c.set(x, y, rgba(V_OUTLINE if ("blade" in near or "socket" in near) else V_WOOD["dark"]))
    return c


SHOVEL_ITEM = shovel_sprite()
SHOVEL_ITEM.save("item/gravedigger_shovel.png")

# Spawn egg: the shared egg shape (as the other mod eggs use), coat brown with corpse-green blotches, a cap-tweed band.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
SPOTS = [(6, 6), (10, 8), (7, 11)]
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = rgba(COAT[5])
    elif any(abs(x - sx) + abs(y - sy) <= 1 for sx, sy in SPOTS):
        egg.px[i] = rgba(SKIN[1] if (x, y) in SPOTS else SKIN[3])
    elif y == round(cy) - 3:
        egg.px[i] = rgba(CAP[1])
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = rgba(COAT[0] if light > 5 else COAT[1] if light > 0 else COAT[3])
egg.save("item/grave_digger_spawn_egg.png")

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


for name, parent in (("grave_digger_spawn_egg", "minecraft:item/generated"), ("gravedigger_shovel", "minecraft:item/handheld")):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", parent), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def looting(low, high, extra=1.0):
    return [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
            {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": extra},
             "enchantment": "minecraft:looting"}]


def rare(base, per_level):
    """Killed by a player, with a chance looting raises."""
    return {"type": "minecraft:all_of", "terms": [
        {"type": "minecraft:killed_by_player"},
        {"type": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
         "unenchanted_chance": base, "enchanted_chance": {"type": "minecraft:linear", "base": base + per_level,
                                                          "per_level_above_first": per_level}}]}


write(os.path.join(DATA, "loot_table/entities/grave_digger.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:rotten_flesh", "modifier": looting(0, 2)}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:bone", "modifier": looting(0, 1)}]},
        # Now and then his shovel, well used.
        {"rolls": 1, "condition": rare(0.085, 0.02), "entries": [{"type": "minecraft:item", "name": "tomyen:gravedigger_shovel",
                                                                 "modifier": {"type": "minecraft:set_damage",
                                                                              "damage": {"type": "minecraft:uniform",
                                                                                         "min": 0.3, "max": 0.8}}}]},
        # Rarely, the soul of somebody he buried.
        {"rolls": 1, "condition": rare(0.025, 0.01), "entries": [{"type": "minecraft:item", "name": "tomyen:soul_charge"}]},
    ]),
    ("random_sequence", "tomyen:entities/grave_digger")]))


def tag_add(path, *entries):
    """Adds entries to a tag file, leaving whatever else is in it."""
    obj = json.load(open(path), object_pairs_hook=OrderedDict) if os.path.exists(path) else OrderedDict([("values", [])])
    for entry in entries:
        if entry not in obj["values"]:
            obj["values"].append(entry)
    write(path, obj)


def vanilla_tag_add(path, *entries):
    """Adds to a vanilla tag (never replacing it)."""
    obj = json.load(open(path), object_pairs_hook=OrderedDict) if os.path.exists(path) else OrderedDict([("values", []), ("replace", False)])
    for entry in entries:
        if entry not in obj["values"]:
            obj["values"].append(entry)
    write(path, obj)


# He's a zombie (and so undead: smite, inverted healing, evil to silver and holy light), he burns by day, and holy
# light hits him three times as hard.
tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), "tomyen:grave_digger")
tag_add(os.path.join(DATA, "tags/entity_type/evil.json"), "tomyen:grave_digger")
vanilla_tag_add(os.path.join(DATA_MC, "tags/entity_type/zombies.json"), "tomyen:grave_digger")
vanilla_tag_add(os.path.join(DATA_MC, "tags/entity_type/burn_in_daylight.json"), "tomyen:grave_digger")
# His shovel is a shovel: enchantable, Star Forge infusable.
vanilla_tag_add(os.path.join(DATA_MC, "tags/item/shovels.json"), "tomyen:gravedigger_shovel")

# What he can dig, quickly (soft ground) and slowly (natural stone), and what he never can, whatever the other two say.
# Datapacks can change all three.
write(os.path.join(DATA, "tags/block/grave_digger_diggable.json"), {"values": ["#minecraft:mineable/shovel"]})
write(os.path.join(DATA, "tags/block/grave_digger_soft_stone.json"), {"values": [
    "#minecraft:base_stone_overworld", "minecraft:netherrack", "minecraft:sandstone", "minecraft:red_sandstone"]})
write(os.path.join(DATA, "tags/block/grave_digger_proof.json"), {"values": [
    "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:reinforced_deepslate", "minecraft:bedrock",
    "minecraft:barrier", "minecraft:end_portal_frame", "#minecraft:beacon_base_blocks",
    "tomyen:steel_block", "tomyen:silver_block", "tomyen:holy_silver_block", "tomyen:tungsten_block",
    "tomyen:aluminium_block", "tomyen:bloom_steel_block", "tomyen:magmasteel_block", "tomyen:soul_steel_block",
    "tomyen:star_steel_block", "tomyen:froststeel_block", "tomyen:voidsteel_block", "tomyen:raw_silver_block",
    "tomyen:raw_tungsten_block", "tomyen:raw_uranium_block"]})


def vanilla(names, pitch, volume=1.0, **extra):
    out = []
    for n in names:
        e = OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)])
        if volume != 1.0:
            e["volume"] = volume
        e.update(extra)
        out.append(e)
    return out


SOUNDS = OrderedDict([
    ("grave_digger_ambient", OrderedDict([("subtitle", "subtitles.tomyen.grave_digger_ambient"),
                                          ("sounds", vanilla(["mob/zombie/say%d" % i for i in (1, 2, 3)], 0.72))])),
    ("grave_digger_hurt", OrderedDict([("subtitle", "subtitles.tomyen.grave_digger_hurt"),
                                       ("sounds", vanilla(["mob/zombie/hurt%d" % i for i in (1, 2)], 0.75))])),
    ("grave_digger_death", OrderedDict([("subtitle", "subtitles.tomyen.grave_digger_death"),
                                        ("sounds", vanilla(["mob/zombie/death"], 0.7))])),
    ("grave_digger_step", OrderedDict([("sounds", vanilla(["mob/husk/step%d" % i for i in range(1, 6)], 0.8))])),
    # The shovel biting into the ground: a spade's scrape over gravel.
    ("grave_digger_dig", OrderedDict([("subtitle", "subtitles.tomyen.grave_digger_dig"),
                                      ("sounds", vanilla(["item/shovel/flatten%d" % i for i in (1, 2, 3, 4)], 0.75)
                                       + vanilla(["dig/gravel%d" % i for i in (1, 2, 3, 4)], 0.7, 0.8))])),
    # The same from under the floor: low, soft and dull.
    ("grave_digger_dig_muffled", OrderedDict([("subtitle", "subtitles.tomyen.grave_digger_dig_muffled"),
                                              ("sounds", vanilla(["dig/gravel%d" % i for i in (1, 2, 3, 4)], 0.45, 0.6)
                                               + vanilla(["item/shovel/flatten%d" % i for i in (1, 2)], 0.4, 0.45))])),
    # The flat of the shovel on somebody's head.
    ("grave_digger_clang", OrderedDict([("subtitle", "subtitles.tomyen.grave_digger_clang"),
                                        ("sounds", vanilla(["mob/zombie/metal%d" % i for i in (1, 2, 3)], 1.25, 0.7))])),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.tomyen.grave_digger", "Grave Digger"),
    ("item.tomyen.grave_digger_spawn_egg", "Grave Digger Spawn Egg"),
    ("item.tomyen.gravedigger_shovel", "Gravedigger's Shovel"),
    ("item.tomyen.gravedigger_shovel.night", "Digs faster by night"),
    ("item.tomyen.gravedigger_shovel.night_now", "Night shift: digging faster"),
    ("item.tomyen.gravedigger_shovel.bones", "Sometimes turns up old bones"),
    ("subtitles.tomyen.grave_digger_ambient", "Grave Digger groans"),
    ("subtitles.tomyen.grave_digger_hurt", "Grave Digger hurts"),
    ("subtitles.tomyen.grave_digger_death", "Grave Digger dies"),
    ("subtitles.tomyen.grave_digger_dig", "Shovel digs"),
    ("subtitles.tomyen.grave_digger_dig_muffled", "Something digs below"),
    ("subtitles.tomyen.grave_digger_clang", "Shovel clangs"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "gravedigger", "GraveDiggerModel.java"))


def f(v):
    return f"{v:.2f}f".replace("-0.00f", "0.00f")


def java_layer():
    """createBodyLayer's body, from PARTS."""
    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in PARTS:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size, mirror in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}){'.mirror()' if mirror else ''}.addBox({', '.join(f(c) for c in frm)}, " \
                       f"{', '.join(f(c) for c in size)}){'.mirror(false)' if mirror else ''}"
        pose = (f"PartPose.offsetAndRotation({', '.join(f(c) for c in pivot)}, {', '.join(f(c) for c in rot)})"
                if any(rot) else f"PartPose.offset({', '.join(f(c) for c in pivot)})")
        lines.append(f"\t\tPartDefinition {var} = {('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", "
                     f"{builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_grave_digger.py\n", "\t\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose():
    return {name: [pivot, rot] for name, _, pivot, rot, _ in PARTS}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


def put(pose, part, rx, ry, rz):
    pose[part][1] = (rx, ry, rz)


# The chop's two key poses, (part, wound up, struck): rotations the parts are blended toward.
CHOP = [("right_arm", (-2.9, 0.0, 0.25), (-1.3, 0.0, 0.1)),
        ("right_forearm", (-0.45, 0.0, 0.0), (-0.15, 0.0, 0.0)),
        ("shovel", (0.7, 0.0, 0.0), (1.55, 0.0, 0.0)),
        ("left_arm", (-2.6, 0.0, -0.45), (-0.95, 0.0, -0.3)),
        ("left_forearm", (-0.5, 0.0, 0.0), (-0.3, 0.0, 0.0))]


def chop_weights(attack):
    """How far into the wind-up and into the strike a chop is, 0 to 1 each: up over the first 30%, down by 55%, then
    easing back to rest."""
    if attack < 0.3:
        return attack / 0.3, 0.0
    if attack < 0.55:
        t = (attack - 0.3) / 0.25
        return 1.0 - t, t
    return 0.0, 1.0 - (attack - 0.55) / 0.45


# Dig modes, as GraveDigger.DigMode's ordinals.
DIG_NONE, DIG_DOWN, DIG_FORWARD, DIG_UP = 0, 1, 2, 3


def animate(pose, walk=0.0, walk_speed=0.0, attack=0.0, aggressive=False, age=0.0, dig=DIG_NONE):
    """Python twin of GraveDiggerModel.setupAnim, for the preview."""
    stride = math.cos(walk * 0.6662)
    speed = walk_speed
    hunt = 1.0 if aggressive else 0.0
    # A shambling, dragging walk: the right leg takes the full stride, the left a shorter one.
    r_leg = stride * 0.9 * speed
    l_leg = -stride * 0.6 * speed
    add(pose, "right_leg", dx=r_leg)
    add(pose, "left_leg", dx=l_leg)
    add(pose, "right_shin", dx=max(0.0, -r_leg) * 1.1)
    add(pose, "left_shin", dx=max(0.0, -l_leg) * 1.1)
    add(pose, "body", dz=0.07 * stride * speed, dx=0.08 * hunt, pos=(0, abs(math.sin(walk * 0.6662)) * 0.8 * speed, 0))
    add(pose, "head", dz=0.05 * math.sin(age * 0.045), dx=-0.08 * hunt)
    add(pose, "jaw", dx=0.12 * max(0.0, math.sin(age * 0.07)) + 0.12 * hunt)
    # The coat's flaps follow the legs forward; the back panel kicks out behind.
    add(pose, "coat_right", dx=min(0.0, r_leg) * 0.9)
    add(pose, "coat_left", dx=min(0.0, l_leg) * 0.9)
    add(pose, "coat_back", dx=max(0.0, r_leg, l_leg) * 0.5 + 0.12 * speed)
    # The lantern swings.
    add(pose, "lantern", dx=-0.3 * math.sin(walk * 0.6662) * speed + 0.04 * math.sin(age * 0.09),
        dz=-0.1 * stride * speed)
    # The left arm reaches and gropes; the right carries the shovel, bobbing.
    add(pose, "left_arm", dx=0.12 * stride * speed + 0.06 * math.sin(age * 0.067) - 0.25 * hunt)
    add(pose, "left_forearm", dx=-0.1 * math.sin(age * 0.067 + 1.0))
    add(pose, "right_arm", dx=-0.06 * stride * speed)

    if attack > 0.0:
        # An overhead chop: the shovel hauled up off the shoulder and back over his head, then brought down hard in
        # front of him, blade first, his whole back bending into it; then back onto the shoulder.
        wind, strike = chop_weights(attack)
        for part, w_pose, s_pose in CHOP:
            r = pose[part][1]
            r = tuple(r[i] + (w_pose[i] - r[i]) * wind for i in range(3))
            pose[part][1] = tuple(r[i] + (s_pose[i] - r[i]) * strike for i in range(3))
        add(pose, "body", dx=-0.15 * wind + 0.3 * strike)
        add(pose, "head", dx=0.1 * wind - 0.2 * strike)
        add(pose, "jaw", dx=0.4 * wind)

    if dig != DIG_NONE:
        # Digging: both hands on the shaft, stabbing the blade in and levering it back, over and over.
        cycle = math.sin(age * 0.5)
        stab = max(0.0, cycle)
        lever = max(0.0, -cycle)
        aim = {DIG_DOWN: (-0.55, 0.55), DIG_FORWARD: (-1.35, 0.05), DIG_UP: (-2.6, -0.3)}[dig]
        put(pose, "right_arm", aim[0] + 0.35 * stab - 0.25 * lever, 0.0, 0.15)
        put(pose, "right_forearm", -0.35 - 0.3 * lever, 0.0, 0.0)
        put(pose, "shovel", 1.25 + 0.2 * lever, 0.0, 0.0)
        put(pose, "left_arm", aim[0] + 0.25 + 0.3 * stab - 0.2 * lever, 0.0, -0.35)
        put(pose, "left_forearm", -0.6, 0.0, 0.0)
        add(pose, "body", dx=aim[1] * 0.4 + 0.1 * stab)
        add(pose, "head", dx=-aim[1] * 0.3)
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


def quads(pose, labels=False):
    """Every textured face of the posed model: (corners in model space, uv rect[, label, outward normal])."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot = pose[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for k, (cube, frm, size, mirror) in enumerate(PART_CUBES.get(child, [])):
                (u, v), _ = CUBES[cube]
                for verts, uv in cube_faces(m, o, u, v, frm, size, mirror):
                    if labels:
                        centre = tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3))
                        fc = tuple(sum(p[i] for p in verts) / 4 for i in range(3))
                        out.append((verts, uv, f"{child}.{cube}{k}", tuple(fc[i] - centre[i] for i in range(3))))
                    else:
                        out.append((verts, uv))
            walk(child, m, o)

    walk("root", [[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size, mirror):
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
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


# Z-fighting: no two faces of the model at rest may share a plane, face the same way and overlap.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from coplanar import Face, fights
_hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(rest_pose(), labels=True)])
if _hits:
    for h_ in _hits:
        print("z-fight: grave digger", h_)
    raise SystemExit(f"grave digger: {len(_hits)} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, _ in quads(pose) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
print(f"grave digger: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f}, "
      f"feet y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


def render(pose, view, scale=8, width=300, height=380, light=(0.35, -0.8, -0.5), focus=None):
    """Orthographic render. view: 'front' (looking at its face), 'side' (from its right), 'three' (three-quarters),
    'back' or 'left' (from its left). focus: a model point to centre (default: feet at the bottom)."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "left": -math.pi / 2,
             "three_back": math.pi * 0.8}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs_ = [p[0] * ca - p[2] * sa for verts, _ in quads(pose) for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs_) + max(xs_)) / 2 * scale, height - 20, 24.0
    gy = int(oy + (24.0 - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    for verts, (ua, va, ub, vb) in quads(pose):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        shade = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = ub - ua, vb - va
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua + (i + 0.5) * du / nu), TEX_W - 1)
                tv = min(int(va + (j + 0.5) * dv / nv), TEX_H - 1)
                col = TEXTURE.get(int(tu), int(tv))
                if col[3] == 0:
                    continue
                corners = []
                for (a_, b_) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a_ / nu, b_ / nv
                    p = tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))
                    x = p[0] * ca - p[2] * sa
                    z = p[0] * sa + p[2] * ca
                    corners.append((ox + x * scale, oy + (p[1] - ly) * scale, z))
                depth = sum(c[2] for c in corners) / 4
                k_ = 1.0 if col[:3] in GLOWING else shade
                c = (int(col[0] * k_), int(col[1] * k_), int(col[2] * k_), 255)
                fill(img, zbuf, width, height, corners, depth, c)
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


def enlarge(canvas_px, w, h, s, bg=((40, 44, 52, 255), (52, 56, 64, 255))):
    big = [bg[((x // (s * 4)) + (y // (s * 4))) % 2] for y in range(h * s) for x in range(w * s)]
    for y in range(h * s):
        for x in range(w * s):
            c = canvas_px[(y // s) * w + x // s]
            if c[3]:
                big[y * w * s + x] = c
    return big


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    S = 7
    views = [render(rest_pose(), "front", scale=S), render(rest_pose(), "side", scale=S), render(rest_pose(), "three", scale=S),
             render(rest_pose(), "back", scale=S),
             render(animate(rest_pose(), walk=1.2, walk_speed=0.9), "side", scale=S),
             render(animate(rest_pose(), walk=3.6, walk_speed=0.9, aggressive=True), "three", scale=S),
             render(animate(rest_pose(), attack=0.3, aggressive=True), "side", scale=S),
             render(animate(rest_pose(), attack=0.45, aggressive=True), "side", scale=S),
             render(animate(rest_pose(), attack=0.55, aggressive=True), "three", scale=S),
             render(animate(rest_pose(), dig=DIG_DOWN, age=3.0), "side", scale=S),
             render(animate(rest_pose(), dig=DIG_FORWARD, age=3.0), "side", scale=S),
             render(animate(rest_pose(), dig=DIG_UP, age=3.0), "side", scale=S)]
    head_pts = [p for verts, uv, label, n in quads(rest_pose(), labels=True) if label.startswith(("head.", "cap.", "jaw."))
                for p in verts]
    centre = tuple(sum(p[i] for p in head_pts) / len(head_pts) for i in range(3))
    views += [render(rest_pose(), "front", scale=20, focus=centre), render(rest_pose(), "three", scale=20, focus=centre),
              render(rest_pose(), "side", scale=20, focus=centre)]
    out, bw, bh = board(views, 300, 380, 5)
    png(os.path.join(PREVIEW, "preview_grave_digger.png"), bw, bh, out)
    png(os.path.join(PREVIEW, "preview_grave_digger_texture.png"), TEX_W * 4, TEX_H * 4, enlarge(TEXTURE.px, TEX_W, TEX_H, 4))
    # The shovel item beside vanilla's iron shovel, and the spawn egg.
    client = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
    _, _, iron = read_png_bytes(zipfile.ZipFile(client).read("assets/minecraft/textures/item/iron_shovel.png"))
    item = SHOVEL_ITEM
    row = [CLEAR] * (16 * 3 * 16)
    for y in range(16):
        for x in range(16):
            row[y * 48 + x] = iron[y * 16 + x]
            row[y * 48 + 16 + x] = item.px[y * 16 + x]
            row[y * 48 + 32 + x] = egg.px[y * 16 + x]
    png(os.path.join(PREVIEW, "preview_gravedigger_shovel.png"), 48 * 12, 16 * 12, enlarge(row, 48, 16, 12))
    print("preview written to", PREVIEW)
print("done")
