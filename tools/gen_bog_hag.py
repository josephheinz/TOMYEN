"""Builds the Bog Hag: her model (the cube layout in PARTS, written into client/hag/BogHagModel.java between the
GENERATED markers), her entity texture and the glow layer for her eyes and lantern, the sprites of what comes of her
(the Hag Charm, Bog Muck, Hexbreaker Tea and her spawn egg), the Hag's Curse and Hex Ward effect icons, and their item
models and definitions. On the data side it writes her loot table, the Bog Muck to mud recipe (with its recipe-book
advancement), and adds her to the tomyen:holy_vulnerable entity tag. Her sounds (vanilla witch sounds, pitched down
and roughened with a little evoker and slime) and the lang entries are merged into sounds.json and en_us.json.

The texture is painted in smooth ramps: sickly grey-green skin gone darker in the hollows (with a few deliberate rot
patches, not noise), lank grey hair, a faded bruise-plum dress torn ragged at the hem and sleeves, a sackcloth apron,
a shawl of swamp moss hanging in strands, a gnarled dark staff with a little rodent skull hung from its crook, an iron
lantern with a marsh-light glass, yellowed nails and one snaggle tooth. Her eyes and the lantern glass glow. No noise,
no dithering; a rerun gives the same files.

The model is checked for z-fighting at rest and through her walk, hex, summon and staff-rap poses (tools/coplanar.py),
and its extents are printed (she should stand on y 24).

With --preview DIR it also renders, into DIR, preview_bog_hag.png (front, side and three-quarters at rest; mid-stride;
the hex wound up and thrown; the summon raised and slammed; the staff rap; and the head up close from three sides)
and preview_bog_hag_texture.png, using the same cubes, pivots and UV layout as the Java model, and a Python twin of
its animation; and preview_bog_hag_items.png, the item sprites and effect icons enlarged.

Usage: python3 tools/gen_bog_hag.py src/main/resources/assets/tomyen [--preview DIR]"""
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


# ---------------------------------------------------------------------------------------------- palette
# Skin: a sickly grey-green, light to dark. Hollows and the undersides run down the ramp; nothing is speckled.
SKIN = [(168, 174, 132), (142, 150, 110), (117, 125, 90), (93, 100, 72), (70, 76, 55), (50, 55, 40)]
ROT = (84, 92, 52)          # the deliberate rot patches: darker, yellower
ROT_DARK = (62, 66, 38)
# The dress: a faded bruise-plum, light to dark.
DRESS = [(100, 78, 90), (82, 63, 75), (66, 50, 61), (51, 38, 48), (37, 27, 35)]
# Sackcloth apron.
SACK = [(146, 126, 94), (122, 104, 77), (98, 82, 60), (76, 63, 46)]
# Swamp moss, light to dark.
MOSS = [(126, 148, 62), (102, 124, 49), (80, 101, 40), (61, 79, 31), (44, 58, 24)]
# Lank grey hair.
HAIR = [(192, 188, 174), (164, 160, 146), (134, 130, 119), (104, 101, 93), (78, 76, 70)]
# The staff: gnarled dark wood (mangrove-dark), light to dark.
WOOD = [(124, 94, 64), (102, 76, 52), (82, 60, 41), (62, 45, 31), (44, 31, 22)]
BONE = [(230, 220, 190), (200, 188, 156), (164, 152, 122), (120, 110, 86)]
IRON = [(112, 110, 116), (84, 82, 90), (60, 58, 66), (40, 39, 46)]
NAIL = [(198, 182, 114), (150, 134, 80), (98, 86, 52)]
MOUTH = (52, 26, 30)
TOOTH = (222, 208, 152)
TWINE = (170, 150, 108)
# What glows: her eyes and the lantern's marsh-light.
EYE = (196, 255, 76)
EYE_HOT = (248, 255, 186)
LIGHT = (204, 244, 118)
LIGHT_HOT = (246, 255, 204)
LIGHT_EDGE = (150, 196, 74)
GLOWS = (EYE, EYE_HOT, LIGHT, LIGHT_HOT, LIGHT_EDGE)

TEX_W, TEX_H = 128, 64


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1 (no banding beyond the texel grid, no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def down(cols, y, h, top=0.1, bottom=0.75):
    """A material down a face: lighter where the light catches the top, darker underneath."""
    return ramp(cols, top + (bottom - top) * (y / max(1, h - 1)))


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
# -z, feet at y 24. This is the one place the shape is defined: it's written into client/hag/BogHagModel.java
# (between the GENERATED markers), painted here, and drawn by the preview.
#
# A hunched old hag. Her long dress hangs straight from the hips to just off the ground, torn ragged at the hem, and
# her bony bare feet poke out under it, long-toed. Above the hips her back bends right over: a narrow chest, a great
# hump with knuckles of spine standing out of it, and a shawl of swamp moss over her shoulders hanging in strands down
# her front and in a drape off the hump. A sackcloth apron. Her neck thrusts the head forward and up, so she peers at
# you level: a long face under a heavy brow, glowing eyes, a long hooked nose with a wart, a chin that curls up to
# meet it, one snaggle tooth, and lank grey hair hanging down her back and either side of the face. Bony arms in torn
# sleeves, long yellow nails. Her right hand grips a gnarled staff taller than she is, crooked at the top with a little
# skull hung off the crook; her left holds out an iron lantern burning with marsh-light.
LEAN = 0.75
SIDES = (("right", -1, False), ("left", 1, True))


def staff_angles(arm, forearm):
    """The staff's rotation in its hand that stands it straight up at rest (the chain above it, undone)."""
    return (-(LEAN + arm[0] + forearm[0]), 0.0, -arm[2])


R_ARM = (-0.72, 0.0, 0.6)
R_FOREARM = (-0.75, 0.0, 0.0)
L_ARM = (-1.35, 0.0, -0.15)
L_FOREARM = (-0.8, 0.0, 0.0)


def parts():
    p = [
        ("hips", None, (0.0, 13.0, 0.5), (0.0, 0.0, 0.0), [
            ("skirt_top", (-3.5, -2.0, -3.0), (7, 6, 6), False),
            ("skirt_hem", (-4.5, 3.5, -3.5), (9, 6, 7), False)]),
        ("torso", "hips", (0.0, -2.0, 0.0), (LEAN, 0.0, 0.0), [
            ("chest", (-4.0, -9.0, -2.5), (8, 9, 5), False),
            ("hump", (-2.5, -10.0, 1.0), (5, 7, 4), False),
            ("knob", (-1.0, -11.0, 2.0), (2, 2, 2), False),
            ("knob", (-1.0, -9.0, 4.0), (2, 2, 2), False),
            ("apron", (-3.0, -3.5, -3.0), (6, 4, 1), False)]),
        ("shawl", "torso", (0.0, -9.0, 0.0), (0.0, 0.0, 0.0), [
            ("mantle", (-5.5, -0.5, -3.0), (11, 3, 6), False),
            ("strand_long", (1.2, 2.5, -3.5), (2, 6, 1), False),
            ("strand_mid", (-3.3, 2.5, -3.5), (2, 5, 1), False),
            ("strand_thin", (-0.5, 2.5, -3.5), (1, 3, 1), False),
            ("drape", (-4.5, 2.5, 5.0), (9, 4, 1), False)]),
        ("neck", "torso", (0.0, -9.0, -1.0), (-0.6, 0.0, 0.0), [
            ("neck", (-1.5, -5.0, -2.0), (3, 5, 3), False)]),
        ("head", "neck", (0.0, -4.5, -1.0), (-0.15, 0.0, 0.0), [
            ("skull", (-3.0, -7.0, -3.5), (6, 7, 6), False),
            ("brow", (-3.5, -5.0, -4.5), (7, 1, 1), False),
            ("nose", (-1.0, -4.0, -7.0), (2, 3, 4), False),
            ("nose_tip", (-1.0, -1.0, -8.0), (2, 2, 2), False),
            ("wart", (1.0, -3.0, -6.0), (1, 1, 1), False),
            ("chin", (-1.5, 0.0, -6.0), (3, 2, 2), False),
            ("tooth", (1.5, -1.0, -4.0), (1, 2, 1), False),
            ("hair_cap", (-3.5, -8.0, -3.0), (7, 1, 5), False),
            ("hair_back", (-3.5, -8.0, 2.0), (7, 9, 2), False),
            ("hair_side", (-4.0, -7.0, -2.5), (1, 9, 4), False),
            ("hair_side", (3.0, -7.0, -2.5), (1, 9, 4), True)]),
        # The staff arm.
        ("right_arm", "torso", (-5.0, -8.0, -0.5), R_ARM, [
            ("upper_arm", (-1.1, -0.5, -1.0), (2, 7, 2), False),
            ("sleeve", (-1.5, -1.0, -1.5), (3, 4, 3), False)]),
        ("right_forearm", "right_arm", (0.0, 6.5, 0.0), R_FOREARM, [
            ("forearm", (-1.0, 0.0, -1.0), (2, 7, 2), False)]),
        ("right_hand", "right_forearm", (0.0, 6.5, 0.0), (0.0, 0.0, 0.0), [
            ("hand", (-1.5, 0.0, -1.5), (3, 2, 3), False),
            ("nail", (-1.5, 2.0, -1.5), (1, 2, 1), False),
            ("nail", (0.5, 2.0, -1.5), (1, 2, 1), False)]),
        ("staff", "right_hand", (0.0, 1.0, 0.0), staff_angles(R_ARM, R_FOREARM), [
            ("shaft", (-1.0, -15.0, -1.0), (2, 22, 2), False)]),
        ("staff_top", "staff", (0.0, -15.0, 0.0), (0.0, 0.0, -0.22), [
            ("knot", (-1.5, -2.0, -1.5), (3, 2, 3), False),
            ("staff_upper", (-1.0, -7.0, -1.0), (2, 5, 2), False),
            ("crook", (-1.0, -9.0, -5.0), (2, 2, 6), False),
            ("hook", (-1.0, -7.0, -5.0), (2, 3, 2), False),
            ("fetish", (-1.5, -4.0, -5.5), (3, 3, 3), False)]),
        # The lantern arm.
        ("left_arm", "torso", (5.0, -8.0, -0.5), L_ARM, [
            ("upper_arm", (-0.9, -0.5, -1.0), (2, 7, 2), True),
            ("sleeve", (-1.5, -1.0, -1.5), (3, 4, 3), True)]),
        ("left_forearm", "left_arm", (0.0, 6.5, 0.0), L_FOREARM, [
            ("forearm", (-1.0, 0.0, -1.0), (2, 7, 2), True)]),
        ("left_hand", "left_forearm", (0.0, 6.5, 0.0), (0.0, 0.0, 0.0), [
            ("hand", (-1.5, 0.0, -1.5), (3, 2, 3), True),
            ("nail", (-1.5, 2.0, -1.5), (1, 2, 1), True),
            ("nail", (0.5, 2.0, -1.5), (1, 2, 1), True)]),
        ("lantern", "left_hand", (0.0, 1.0, 0.0), (-(LEAN + L_ARM[0] + L_FOREARM[0]), 0.0, -L_ARM[2]), [
            ("bail", (-0.5, 0.0, -0.5), (1, 2, 1), False),
            ("cap", (-2.0, 2.0, -2.0), (4, 1, 4), False),
            ("glass", (-1.5, 3.0, -1.5), (3, 4, 3), False),
            ("cap", (-2.0, 7.0, -2.0), (4, 1, 4), False),
            ("post", (-1.9, 2.5, -1.9), (1, 5, 1), False),
            ("post", (0.9, 2.5, -1.9), (1, 5, 1), False),
            ("post", (-1.9, 2.5, 0.9), (1, 5, 1), False),
            ("post", (0.9, 2.5, 0.9), (1, 5, 1), False)]),
    ]
    for side, s, mirror in SIDES:
        p += [
            (side + "_leg", None, (s * 2.0, 14.0, 0.5), (0.0, 0.0, 0.0), [
                ("shin", (-1.0, 0.0, -1.0), (2, 9, 2), mirror)]),
            (side + "_foot", side + "_leg", (0.0, 9.0, 0.0), (0.0, 0.0, 0.0), [
                ("foot", (-1.5, 0.0, -4.0), (3, 1, 5), mirror),
                ("toe", (-1.5, 0.0, -5.0), (1, 1, 1), mirror),
                ("toe", (0.5, 0.0, -5.0), (1, 1, 1), mirror)]),
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
# A fixed tear pattern for ragged hems (how many rows each column loses, out of 5): deliberate, not random.
HEM = [2, 4, 1, 3, 5, 2, 0, 3, 4, 1, 3, 5, 2, 4, 0, 3, 1, 4]
MUD = (72, 58, 40)


def ragged(base, cut_rows, phase=0, flip=False):
    """Cloth (or moss, or hair) with a torn edge: the last rows of a face (the first, flipped) go missing column by
    column."""
    def f(x, y, w, h):
        k = HEM[(x + phase) % len(HEM)] * cut_rows // 5
        row = (h - 1 - y) if not flip else y
        if row < k:
            return None
        return base(x, y, w, h)
    return f


def const(c):
    return lambda x, y, w, h: c


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    glow = Canvas(TEX_W, TEX_H)

    def P(key, painters, glowing=None):
        paint(tex, *CUBES[key], painters)
        if glowing:
            paint(glow, *CUBES[key], glowing)

    # --- the dress -------------------------------------------------------------------------------------------
    def folds(x, y, w, h, top=0.1, bottom=0.8):
        """The dress down a face, with a soft fold every third column (a step darker, its edge a half step)."""
        c = down(DRESS, y, h, top, bottom)
        if x % 3 == 2:
            c = lerp(c, DRESS[4], 0.35)
        elif x % 3 == 0:
            c = lerp(c, DRESS[0], 0.15)
        return c

    def skirt_top(x, y, w, h):
        if y == 0:
            return TWINE if x % 2 == 0 else lerp(TWINE, DRESS[3], 0.5)  # the drawstring
        return folds(x, y, w, h, 0.1, 0.4)
    P("skirt_top", {"top": const(DRESS[2]), "front": skirt_top, "back": skirt_top, "right": skirt_top, "left": skirt_top,
                    "bottom": const(DRESS[4])})

    def hem(x, y, w, h):
        c = folds(x, y, w, h, 0.35, 0.9)
        # Mud wicked up the last rows.
        if y >= h - 3:
            c = lerp(c, MUD, 0.25 + 0.2 * (y - (h - 3)))
        return c
    P("skirt_hem", {"top": const(DRESS[3]), "front": ragged(hem, 3, 0), "back": ragged(hem, 3, 7), "right": ragged(hem, 3, 3),
                    "left": ragged(hem, 3, 11)})

    def bodice(x, y, w, h):
        # Laces down the middle, crossing every other row.
        if x in (3, 4) and 1 <= y <= 6:
            return TWINE if (x + y) % 2 == 0 else lerp(DRESS[3], TWINE, 0.3)
        return folds(x, y, w, h, 0.15, 0.6)
    P("chest", {"top": const(DRESS[1]), "bottom": const(DRESS[3]), "front": bodice,
                "back": lambda x, y, w, h: folds(x, y, w, h, 0.3, 0.7), "right": lambda x, y, w, h: folds(x, y, w, h, 0.25, 0.75),
                "left": lambda x, y, w, h: folds(x, y, w, h, 0.25, 0.75)})

    # The hump: the dress pulled tight over it (lighter, no folds), a sackcloth patch sewn on with big stitches.
    def hump_back(x, y, w, h):
        if 1 <= x <= 3 and 2 <= y <= 5:
            if x in (1, 3) or y in (2, 5):
                return TWINE if (x + y) % 2 == 0 else SACK[2]
            return SACK[1]
        return down(DRESS, y, h, 0.0, 0.55)
    P("hump", {"top": lambda x, y, w, h: lerp(DRESS[0], DRESS[1], y / (h - 1)), "back": hump_back,
               "right": lambda x, y, w, h: down(DRESS, y, h, 0.15, 0.65), "left": lambda x, y, w, h: down(DRESS, y, h, 0.15, 0.65),
               "front": const(DRESS[2]), "bottom": const(DRESS[4])})
    # Knuckles of spine, standing out bare through the torn dress.
    P("knob", {"top": const(SKIN[1]), "bottom": const(SKIN[4]), "front": const(SKIN[2]), "back": const(SKIN[2]),
               "right": const(SKIN[3]), "left": const(SKIN[3])})

    def apron(x, y, w, h):
        c = down(SACK, y, h, 0.0, 0.7)
        if y == 2 and 1 <= x <= 4 or y == 3 and x in (1, 4):
            c = SACK[3]  # the pocket's seam
        if (x, y) == (4, 1):
            c = lerp(c, MUD, 0.5)  # an old stain
        return c
    P("apron", {"front": apron, "back": const(SACK[3]), "top": const(SACK[1]), "bottom": const(SACK[3]),
                "right": const(SACK[2]), "left": const(SACK[2])})

    # --- the moss shawl --------------------------------------------------------------------------------------
    def moss_top(x, y, w, h):
        # Lighter along the shoulders' crest (the middle rows), darker toward the edges, with a darker fibre every
        # third column, offset row by row so the fibres lie diagonally.
        crest = abs(y - (h - 1) / 2) / ((h - 1) / 2)
        c = ramp(MOSS, 0.05 + 0.4 * crest)
        return lerp(c, MOSS[3], 0.3) if (x + y) % 3 == 0 else c

    def moss_side(x, y, w, h):
        c = down(MOSS, y, h, 0.1, 0.85)
        return lerp(c, MOSS[0], 0.25) if (x // 2) % 2 == 0 and y == 0 else c
    P("mantle", {"top": moss_top, "bottom": const(MOSS[4]), "front": ragged(moss_side, 2, 1), "back": ragged(moss_side, 2, 5),
                 "right": ragged(moss_side, 2, 9), "left": ragged(moss_side, 2, 13)})

    def strand(x, y, w, h):
        if y == h - 1 and w > 1 and x == 1:
            return None  # a tapered tip
        return down(MOSS, y, h, 0.2, 0.95)
    for key in ("strand_long", "strand_mid", "strand_thin"):
        P(key, {"front": strand, "back": strand, "right": strand, "left": strand, "top": const(MOSS[1]), "bottom": const(MOSS[3])})
    P("drape", {"front": ragged(moss_side, 3, 2), "back": ragged(moss_side, 3, 6), "right": ragged(moss_side, 3, 4),
                "left": ragged(moss_side, 3, 8), "top": const(MOSS[1])})

    # --- skin ------------------------------------------------------------------------------------------------
    def skin(top=0.1, bottom=0.7):
        return lambda x, y, w, h: down(SKIN, y, h, top, bottom)

    def neck_front(x, y, w, h):
        return SKIN[3] if x != 1 else down(SKIN, y, h, 0.15, 0.5)  # two cords of tendon either side of the throat
    P("neck", {"front": neck_front, "back": const(SKIN[3]), "right": skin(0.2, 0.6), "left": skin(0.2, 0.6), "top": const(SKIN[2]),
               "bottom": const(SKIN[4])})

    # The face. Rows: 0-1 forehead (a wrinkle), 2 the brow's shadow, 3 the eyes, 4 bags under them, 5 hollow cheeks
    # (a rot patch on her right), 6 the mouth, a dark slit under the nose.
    def face(x, y, w, h):
        if y == 3 and x in (1, 4):
            return EYE
        if y == 0:
            return SKIN[1]
        if y == 1:
            return SKIN[2] if 1 <= x <= 4 else SKIN[1]
        if y == 2:
            return SKIN[3] if 1 <= x <= 4 else SKIN[2]
        if y == 3:
            return SKIN[4] if x in (2, 3) else SKIN[2]
        if y == 4:
            return SKIN[3] if x in (1, 4) else SKIN[1]
        if y == 5:
            if x == 0:
                return ROT
            return SKIN[2] if x in (1, 4) else SKIN[1]
        return MOUTH if 1 <= x <= 4 else SKIN[3]

    def eye_glow(x, y, w, h):
        return EYE_HOT if y == 3 and x in (1, 4) else None

    def temple(front_last):
        def f(x, y, w, h):
            edge = (w - 1 - x) if front_last else x
            if edge == 0 and y == 3:
                return SKIN[3]  # crow's feet at the corner of the eye
            if edge == 1 and y == 5 and front_last:
                return ROT_DARK
            return down(SKIN, y, h, 0.15, 0.6)
        return f
    P("skull", {"front": face, "right": temple(True), "left": temple(False), "back": const(HAIR[3]), "top": const(HAIR[2]),
                "bottom": const(SKIN[4])}, {"front": eye_glow})

    def brow_front(x, y, w, h):
        return HAIR[2] if x in (1, 2, 4, 5) else SKIN[2]  # bristling grey brows, a deep frown between them
    P("brow", {"front": brow_front, "top": const(SKIN[1]), "bottom": const(SKIN[4]), "right": const(SKIN[2]), "left": const(SKIN[2]),
               "back": const(SKIN[3])})
    P("nose", {"top": lambda x, y, w, h: lerp(SKIN[0], SKIN[1], 1 - y / (h - 1)), "front": skin(0.05, 0.4), "right": skin(0.2, 0.55),
               "left": skin(0.2, 0.55), "bottom": const(SKIN[4]), "back": const(SKIN[3])})
    P("nose_tip", {"top": const(SKIN[0]), "front": skin(0.1, 0.4), "right": const(SKIN[2]), "left": const(SKIN[2]),
                   "bottom": lambda x, y, w, h: MOUTH if y == 0 else SKIN[4], "back": const(SKIN[3])})
    P("wart", {"top": const((112, 118, 70)), "front": const(ROT), "right": const(ROT), "left": const(ROT), "back": const(ROT),
               "bottom": const(ROT_DARK)})

    def chin_front(x, y, w, h):
        return HAIR[1] if (x, y) == (1, 1) else SKIN[2 if y == 0 else 3]  # one bristle
    P("chin", {"front": chin_front, "top": const(SKIN[1]), "bottom": const(SKIN[4]), "right": const(SKIN[3]), "left": const(SKIN[3]),
               "back": const(SKIN[4])})
    P("tooth", {"top": const(TOOTH), "front": lambda x, y, w, h: TOOTH if y == 0 else (176, 152, 98),
                "right": const((190, 172, 120)), "left": const((190, 172, 120)), "back": const((176, 152, 98)),
                "bottom": const((160, 136, 86))})

    # --- hair ------------------------------------------------------------------------------------------------
    STRANDS = [1, 2, 1, 3, 2, 1, 2, 3, 1, 2]

    def hair(x, y, w, h, top=0.0, bottom=0.6):
        c = down(HAIR, y, h, top, bottom)
        return lerp(c, HAIR[4], 0.12 * STRANDS[x % len(STRANDS)])

    P("hair_cap", {"top": lambda x, y, w, h: HAIR[2] if x == 3 else lerp(HAIR[0], HAIR[1], 1 - y / (h - 1)),  # a parting
                   "front": const(HAIR[1]), "back": const(HAIR[2]), "right": const(HAIR[2]), "left": const(HAIR[2]),
                   "bottom": const(HAIR[4])})
    P("hair_back", {"back": ragged(lambda x, y, w, h: hair(x, y, w, h, 0.0, 0.9), 5, 2), "front": ragged(lambda x, y, w, h: HAIR[3], 4, 2),
                    "right": ragged(hair, 4, 5), "left": ragged(hair, 4, 9), "top": const(HAIR[1]), "bottom": const(HAIR[4])})
    P("hair_side", {"right": ragged(hair, 4, 4), "left": ragged(hair, 4, 4), "front": ragged(hair, 3, 1), "back": ragged(hair, 3, 3),
                    "top": const(HAIR[1])})

    # --- arms and hands --------------------------------------------------------------------------------------
    def bony(top=0.1, bottom=0.65):
        def f(x, y, w, h):
            c = down(SKIN, y, h, top, bottom)
            return lerp(c, SKIN[4], 0.25) if x == w - 1 else c  # the bone's shadowed edge
        return f
    P("upper_arm", {"front": bony(), "back": bony(), "right": bony(), "left": bony(), "top": const(SKIN[2]), "bottom": const(SKIN[3])})

    def sleeve(x, y, w, h):
        return folds(x, y, w, h, 0.1, 0.6)
    P("sleeve", {"front": ragged(sleeve, 2, 3), "back": ragged(sleeve, 2, 8), "right": ragged(sleeve, 2, 0), "left": ragged(sleeve, 2, 5),
                 "top": const(DRESS[1])})

    def forearm(x, y, w, h):
        if y in (4, 5) and x == 0:
            return ROT  # a rotten patch by the wrist
        c = down(SKIN, y, h, 0.15, 0.6)
        return lerp(c, SKIN[4], 0.2) if x == w - 1 else c
    P("forearm", {"front": forearm, "back": bony(), "right": forearm, "left": bony(), "top": const(SKIN[2]), "bottom": const(SKIN[3])})

    def knuckles(x, y, w, h):
        return SKIN[0] if y == h - 1 and x % 2 == 0 else SKIN[1]
    P("hand", {"top": knuckles, "front": lambda x, y, w, h: SKIN[1] if y == 0 else SKIN[2], "right": skin(0.2, 0.5),
               "left": skin(0.2, 0.5), "back": const(SKIN[2]), "bottom": const(SKIN[3])})

    def nail(x, y, w, h):
        return ramp(NAIL, y / max(1, h - 1))
    P("nail", {"front": nail, "back": nail, "right": nail, "left": nail, "top": const(NAIL[0]), "bottom": const(NAIL[2])})

    # --- legs and feet ---------------------------------------------------------------------------------------
    def shin_front(x, y, w, h):
        if y == h - 2:
            return SKIN[1]  # the ankle knob
        c = down(SKIN, y, h, 0.25, 0.7)
        return lerp(c, SKIN[0], 0.25) if x == 0 else c  # the shin bone's ridge
    P("shin", {"front": shin_front, "back": skin(0.35, 0.8), "right": skin(0.3, 0.75), "left": skin(0.3, 0.75), "top": const(SKIN[3]),
               "bottom": const(SKIN[4])})

    def foot_top(x, y, w, h):
        c = lerp(SKIN[1], SKIN[2], 1 - y / (h - 1))
        return lerp(c, SKIN[3], 0.4) if x == 1 and y >= 2 else c  # a tendon down the instep
    P("foot", {"top": foot_top, "front": const(SKIN[2]), "right": const(lerp(SKIN[2], MUD, 0.3)),
               "left": const(lerp(SKIN[2], MUD, 0.3)), "back": const(SKIN[3]), "bottom": const(MUD)})
    P("toe", {"top": const(SKIN[1]), "front": const(NAIL[1]), "right": const(SKIN[2]), "left": const(SKIN[2]), "bottom": const(MUD),
              "back": const(SKIN[3])})

    # --- the staff -------------------------------------------------------------------------------------------
    def grain(x, y, w, h):
        """Gnarled wood along a face: alternate columns lit and shaded, a darker line of grain twisting round it."""
        c = WOOD[1 + (x % 2)]
        if (y + 2 * x) % 7 == 0:
            c = lerp(c, WOOD[4], 0.35)
        return c

    def shaft(x, y, w, h):
        # Twine wrapped above and below her grip (her hand closes round shaft rows 14-16), a knot, mud on the foot.
        if y in (12, 13, 17):
            return TWINE if (x + y) % 2 == 0 else lerp(TWINE, WOOD[3], 0.4)
        if y in (5, 6) and x == 0:
            return WOOD[4] if y == 5 else WOOD[0]
        if y >= h - 3:
            return lerp(grain(x, y, w, h), MUD, 0.3 + 0.2 * (y - (h - 3)))
        return grain(x, y, w, h)
    P("shaft", {"front": shaft, "back": shaft, "right": lambda x, y, w, h: shaft(x + 1, y, w, h),
                "left": lambda x, y, w, h: shaft(x + 1, y, w, h), "top": const(WOOD[2]), "bottom": const(MUD)})
    P("knot", {"top": lambda x, y, w, h: WOOD[3] if (x, y) == (1, 1) else WOOD[1], "front": const(WOOD[2]),
               "right": lambda x, y, w, h: WOOD[4] if (x, y) == (1, 0) else WOOD[2], "left": const(WOOD[2]), "back": const(WOOD[3]),
               "bottom": const(WOOD[3])})
    P("staff_upper", {"front": grain, "back": grain, "right": lambda x, y, w, h: grain(x + 1, y, w, h),
                      "left": lambda x, y, w, h: grain(x + 1, y, w, h), "top": const(WOOD[1]), "bottom": const(WOOD[3])})
    P("crook", {"top": lambda x, y, w, h: WOOD[0] if x == 0 else WOOD[1], "bottom": const(WOOD[3]), "front": const(WOOD[2]),
                "back": const(WOOD[2]), "right": lambda x, y, w, h: grain(y, x, w, h), "left": lambda x, y, w, h: grain(y, x, w, h)})
    P("hook", {"front": grain, "back": grain, "right": grain, "left": grain, "top": const(WOOD[1]), "bottom": const(WOOD[3])})

    # The little skull hung from the crook: a rat's, by the teeth.
    def fetish_front(x, y, w, h):
        if y == 1 and x in (0, 2):
            return (40, 30, 26)  # the eye sockets
        if y == 2:
            return BONE[2] if x == 1 else BONE[1]
        return BONE[0]
    P("fetish", {"front": fetish_front, "top": const(BONE[0]), "right": const(BONE[1]), "left": const(BONE[1]),
                 "back": const(BONE[2]), "bottom": lambda x, y, w, h: TOOTH if y == h - 1 and x != 1 else BONE[3]})

    # --- the lantern -----------------------------------------------------------------------------------------
    P("bail", {"front": const(IRON[1]), "back": const(IRON[2]), "right": const(IRON[1]), "left": const(IRON[2]),
               "top": const(IRON[0]), "bottom": const(IRON[2])})
    P("cap", {"top": lambda x, y, w, h: IRON[2] if x in (0, w - 1) and y in (0, h - 1) else IRON[0], "bottom": const(IRON[2]),
              "front": const(IRON[1]), "back": const(IRON[1]), "right": const(IRON[1]), "left": const(IRON[1])})

    def pane(x, y, w, h):
        if x == 1 and y in (1, 2):
            return LIGHT_HOT
        if y in (0, h - 1):
            return LIGHT_EDGE
        return LIGHT
    lit = {"front": pane, "back": pane, "right": pane, "left": pane, "top": const(LIGHT), "bottom": const(LIGHT_EDGE)}
    P("glass", lit, lit)
    P("post", {"front": const(IRON[2]), "back": const(IRON[2]), "right": const(IRON[3]), "left": const(IRON[2]),
               "top": const(IRON[1]), "bottom": const(IRON[3])})

    tex.save("entity/bog_hag/bog_hag.png")
    glow.save("entity/bog_hag/bog_hag_glow.png")
    return tex, glow


TEXTURE, GLOW = paint_entity()


# ---------------------------------------------------------------------------------------------- items and icons
import herbalism_art as art


def grid_save(g, rel):
    png(os.path.join(TEX, rel), g.w, g.h, g.px)


def hexrgb(c):
    return (c[0] << 16) | (c[1] << 8) | c[2]


# Hag Charm: a forked twig bound with twine and hung on a loop of string, a tuft of swamp moss round the fork, a glass
# eye-bead set in it glowing marsh-green, and a little bone dangling below.
HAG_CHARM = art.Grid().rows([
    "................",
    "......ssss......",
    ".....s....s.....",
    "......ssss......",
    "...ww..tt..ww...",
    "....ww.TT.ww....",
    ".....wwttww.....",
    "....mMwkkwMm....",
    "...mMMkEhkMMm...",
    "..mMMmkhEkmMMm..",
    "...mMmmkkmmMm...",
    "....m..wW..m....",
    ".......bb.......",
    "......bBBd......",
    ".......dd.......",
    "................",
], art.pal(s=hexrgb(TWINE), t=0xb09468, T=0x7c6444, w=hexrgb(WOOD[1]), W=hexrgb(WOOD[3]), E=hexrgb(LIGHT_HOT), e=hexrgb(LIGHT),
           h=hexrgb(LIGHT_EDGE), k=0x23201a, m=hexrgb(MOSS[1]), M=hexrgb(MOSS[3]), b=hexrgb(BONE[0]), B=hexrgb(BONE[1]), d=hexrgb(BONE[3])))

# Bog Muck: a glistening glob of swamp-bottom ooze, a green sheen on its top and a bubble swelling out of its side.
BOG_MUCK = art.Grid().rows([
    "................",
    "................",
    "................",
    "................",
    "................",
    "......aaaa......",
    "....aaHggaaa....",
    "...aAHgGgaaAa...",
    "..aAAgggAAAbba..",
    "..aAAAAAAAbobA..",
    "..dAAAAAAAAbbd..",
    "..dAAAAAAAAAAd..",
    "...ddAAAAAAAd...",
    ".....dddAddd....",
    "........dd......",
    "................",
], art.pal(a=0x5a4a34, A=0x46382a, d=0x2c231a, g=0x6e8040, G=0x96ac58, H=0xc4d690, b=0x7a664a, o=0xa89070))

# Hexbreaker Tea: the herbalism bottle (tools/herbalism_art.py) filled with a pale mint-green brew, a sprig of water
# mint stuck through the cork.
HEXBREAKER_TEA = art.bottle(0x7cc8a0, 0x3f8a6a, 0xc8f0d8)
HEXBREAKER_TEA.stamp(9, 0, [".hH", "hg.", "g.."], art.pal(g=0x3f8a52, h=0x5fae6e, H=0x8fd29a))

# Effect icons (18x18). Hag's Curse: a hag's slit eye, glowing marsh-green, weeping muck. Hex Ward: a ring of pale
# mint light round a sprig of water mint.
HAGS_CURSE_ICON = art.Grid(18, 18).rows([
    "..................",
    "..................",
    "..................",
    "..................",
    "......kkkkkk......",
    "....kkyyyyyykk....",
    "...kyyGGEEGGyyk...",
    "..kyyGEEkkEEGyyk..",
    "..kyGEEEkkEEEGyk..",
    "..kyyGEEkkEEGyyk..",
    "...kyyGGEEGGyyk...",
    "....kkyyyyyykk....",
    "......kkddkk......",
    "........dd........",
    "........d.........",
    "........M.........",
    "..................",
    "..................",
], art.pal(k=0x23201a, y=0xb8b48a, G=0x6e9a2a, E=0xc6f25a, d=0x5a4a34, M=0x3a2e22))
HEX_WARD_ICON = art.Grid(18, 18).rows([
    "..................",
    "......cccccc......",
    "....ccCCCCCCcc....",
    "...cCC......CCc...",
    "..cC....hH....Cc..",
    "..cC...hHg....Cc..",
    ".cC....hg.hH...Cc.",
    ".cC.....g.hHg..Cc.",
    ".cC.....gg.g...Cc.",
    ".cC......g.....Cc.",
    ".cC....hHg.....Cc.",
    "..cC...hg......Cc.",
    "..cC....g.....Cc..",
    "...cCC..g...CCc...",
    "....ccCCCCCCcc....",
    "......cccccc......",
    "..................",
    "..................",
], art.pal(c=0x5aa888, C=0xb6f0d8, g=0x3f8a52, h=0x5fae6e, H=0x8fd29a))

for _g, _rel in ((HAG_CHARM, "item/hag_charm.png"), (BOG_MUCK, "item/bog_muck.png"), (HEXBREAKER_TEA, "item/hexbreaker_tea.png"),
                 (HAGS_CURSE_ICON, "mob_effect/hags_curse.png"), (HEX_WARD_ICON, "mob_effect/hex_ward.png")):
    grid_save(_g, _rel)

# Spawn egg: the shared egg shape (as the other mod eggs use): her grey-green skin above, glowing eyes, a band of moss
# and her plum dress below.
_w, _h, _cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
_mask = [p[3] > 0 for p in _cart_egg]
_xs = [i % _w for i in range(_w * _h) if _mask[i]]
_ys = [i // _w for i in range(_w * _h) if _mask[i]]
_cx, _cy = (min(_xs) + max(_xs)) / 2, (min(_ys) + max(_ys)) / 2
EGG = Canvas(_w, _h)
for _i in range(_w * _h):
    if not _mask[_i]:
        continue
    _x, _y = _i % _w, _i // _w
    _outline = not all(0 <= _x + dx < _w and 0 <= _y + dy < _h and _mask[(_y + dy) * _w + _x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    _light = (_cx - _x) + (_cy - _y)
    if _outline:
        EGG.px[_i] = rgba(SKIN[5] if _y < round(_cy) + 2 else DRESS[4])
    elif (_x, _y) in ((6, 6), (9, 6)):
        EGG.px[_i] = rgba(EYE)
    elif _y in (round(_cy) + 1, round(_cy) + 2):
        EGG.px[_i] = rgba(MOSS[1] if _y == round(_cy) + 1 else MOSS[3])
    elif _y > round(_cy) + 2:
        EGG.px[_i] = rgba(DRESS[1] if _light > 2 else DRESS[2])
    else:
        EGG.px[_i] = rgba(SKIN[1] if _light > 5 else SKIN[2] if _light > 0 else SKIN[3])
EGG.save("item/bog_hag_spawn_egg.png")

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


for name in ("hag_charm", "bog_muck", "hexbreaker_tea", "bog_hag_spawn_egg"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def entry(item, low, high, weight=None, looting=0.0):
    modifiers = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}]
    if looting:
        modifiers.append({"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
                          "enchantment": "minecraft:looting"})
    e = OrderedDict([("type", "minecraft:item"), ("name", item)])
    if weight:
        e["weight"] = weight
    e["modifier"] = modifiers
    return e


# Water mint from the swamps she haunts (what brews Hexbreaker Tea), one other herb of her gathering, a gob or two of
# her muck, and now and then (killed by a player: a third of the time, more with Looting) her charm.
write(os.path.join(DATA, "loot_table/entities/bog_hag.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        {"rolls": 1, "entries": [entry("tomyen:water_mint", 1, 3, looting=1.0)]},
        {"rolls": 1, "entries": [entry("tomyen:yarrow", 1, 2, 4), entry("tomyen:garlic", 1, 2, 3), entry("tomyen:chamomile", 1, 2, 3),
                                 entry("tomyen:wolfsbane", 1, 1, 2), entry("tomyen:ginseng", 1, 1, 1),
                                 {"type": "minecraft:empty", "weight": 3}]},
        {"rolls": 1, "entries": [entry("tomyen:bog_muck", 0, 2, looting=1.0)]},
        OrderedDict([
            ("rolls", 1),
            ("condition", {"type": "minecraft:all_of", "terms": [
                {"type": "minecraft:killed_by_player"},
                OrderedDict([("type", "minecraft:random_chance_with_enchanted_bonus"),
                             ("enchanted_chance", {"type": "minecraft:linear", "base": 0.41, "per_level_above_first": 0.08}),
                             ("enchantment", "minecraft:looting"), ("unenchanted_chance", 0.33)])]}),
            ("entries", [{"type": "minecraft:item", "name": "tomyen:hag_charm"}])]),
    ]),
    ("random_sequence", "tomyen:entities/bog_hag")]))


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


# Bog Muck packs down into mud (and so into packed mud and mud bricks). Hexbreaker Tea is brewed, not crafted: water
# mint in the Kettle (KettleBlock.brews).
shapeless("mud_from_bog_muck", ["tomyen:bog_muck"] * 4, "minecraft:mud", 2, "building")


def tag_add(path, entry_):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry_ not in values:
        values.append(entry_)
    write(path, {"values": values})


# Silver and holy light bite her (and so she's evil, which takes in the holy-vulnerable). She isn't undead: the sun
# doesn't burn her and healing doesn't hurt her.
tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), "tomyen:bog_hag")


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


def sound(subtitle, *groups):
    return OrderedDict([("subtitle", "subtitles.tomyen." + subtitle), ("sounds", [s_ for g_ in groups for s_ in g_])])


WITCH = "entity/witch/"
SOUNDS = OrderedDict([
    ("bog_hag_ambient", sound("bog_hag_ambient", vanilla([WITCH + "ambient%d" % i for i in range(1, 6)], 0.72))),
    ("bog_hag_cackle", sound("bog_hag_cackle", vanilla([WITCH + "celebrate"], 0.78), vanilla([WITCH + "ambient4"], 0.9))),
    ("bog_hag_cast", sound("bog_hag_cast", vanilla([WITCH + "throw%d" % i for i in (1, 2, 3)], 0.7),
                           vanilla(["mob/evocation_illager/prepare_attack1", "mob/evocation_illager/prepare_attack2"], 1.3, volume=0.5))),
    ("bog_hag_summon", sound("bog_hag_summon", vanilla(["mob/evocation_illager/prepare_summon"], 0.7))),
    ("bog_hag_hurt", sound("bog_hag_hurt", vanilla([WITCH + "hurt%d" % i for i in (1, 2, 3)], 0.75))),
    ("bog_hag_death", sound("bog_hag_death", vanilla([WITCH + "death%d" % i for i in (1, 2, 3)], 0.68))),
    ("hex_splat", sound("hex_splat", vanilla(["mob/slime/small%d" % i for i in range(1, 6)], 0.6))),
    ("hags_curse", sound("hags_curse", vanilla(["mob/evocation_illager/cast1", "mob/evocation_illager/cast2"], 0.6))),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.tomyen.bog_hag", "Bog Hag"),
    ("entity.tomyen.hex_glob", "Hex"),
    ("item.tomyen.bog_hag_spawn_egg", "Bog Hag Spawn Egg"),
    ("item.tomyen.hag_charm", "Hag Charm"),
    ("item.tomyen.bog_muck", "Bog Muck"),
    ("item.tomyen.hexbreaker_tea", "Hexbreaker Tea"),
    ("item.tomyen.hexbreaker_tea.desc", "Lifts a Hag's Curse, and wards off another for five minutes"),
    ("effect.tomyen.hags_curse", "Hag's Curse"),
    ("effect.tomyen.hex_ward", "Hex Ward"),
    ("hud.tomyen.hags_curse.cursed", "A hag's curse falls on you: bad luck at the water and in the mine"),
    ("hud.tomyen.hags_curse.deepened", "The hag's curse deepens"),
    ("hud.tomyen.hags_curse.warded", "Your Hex Ward turns the hag's curse aside"),
    ("hud.tomyen.hags_curse.junk", "Cursed luck: it was only junk on the line"),
    ("subtitles.tomyen.bog_hag_ambient", "Bog Hag mutters"),
    ("subtitles.tomyen.bog_hag_cackle", "Bog Hag cackles"),
    ("subtitles.tomyen.bog_hag_cast", "Bog Hag hurls a hex"),
    ("subtitles.tomyen.bog_hag_summon", "Bog Hag calls up the bog"),
    ("subtitles.tomyen.bog_hag_hurt", "Bog Hag hurts"),
    ("subtitles.tomyen.bog_hag_death", "Bog Hag dies"),
    ("subtitles.tomyen.hex_splat", "Hex bursts"),
    ("subtitles.tomyen.hags_curse", "A curse falls"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "hag", "BogHagModel.java"))


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


_src = open(JAVA).read()
_start, _end = "\t\t// BEGIN GENERATED by tools/gen_bog_hag.py\n", "\t\t// END GENERATED\n"
_a, _b = _src.index(_start) + len(_start), _src.index(_end)
_src = _src[:_a] + java_layer() + "\n" + _src[_b:]
with open(JAVA, "w") as _out:
    _out.write(_src)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose():
    return {name: [pivot, rot] for name, _, pivot, rot, _ in PARTS}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


HEX_RELEASE = 14.0 / 24.0
SUMMON_RELEASE = 26.0 / 40.0


def animate(pose, walk=0.0, walk_speed=0.0, age=0.0, attack=0.0, spell=0, progress=0.0, aggressive=False, head_yaw=0.0, head_pitch=0.0):
    """Python twin of BogHagModel.setupAnim, for the preview. spell: 0 none, 1 hex, 2 summon; progress 0 to 1."""
    stride = math.cos(walk * 0.6)
    step = math.sin(walk * 0.6)
    swing = stride * 0.45 * walk_speed
    bob = abs(step) * walk_speed
    breathe = math.sin(age * 0.07)
    menace = 1.0 if aggressive else 0.0

    add(pose, "neck", dy=head_yaw * 0.4)
    add(pose, "head", dy=head_yaw * 0.6, dx=head_pitch * 0.6)
    # A hobbling shuffle: short steps, the hips rolling, the hump bobbing; the staff arm plants the staff with the
    # right foot, the lantern arm swings a little and the lantern swings on its bail behind it.
    add(pose, "hips", dz=0.06 * stride * walk_speed, pos=(0, 0.6 * bob, 0))
    add(pose, "torso", dx=0.03 * breathe + 0.08 * bob - 0.1 * menace, dz=-0.05 * stride * walk_speed)
    add(pose, "right_leg", dx=swing)
    add(pose, "left_leg", dx=-swing)
    add(pose, "right_foot", dx=-max(0.0, swing) * 0.5)
    add(pose, "left_foot", dx=-max(0.0, -swing) * 0.5)
    add(pose, "right_arm", dx=-0.3 * stride * walk_speed - 0.2 * menace)
    add(pose, "left_arm", dx=0.12 * stride * walk_speed - 0.25 * menace)
    add(pose, "shawl", dx=-0.04 * breathe)
    add(pose, "head", dz=0.04 * math.sin(age * 0.05))
    # Hex: the staff hauled up and back over her head, then thrust out at the target as the hex flies.
    if spell == 1:
        wind = smooth(progress / HEX_RELEASE) if progress < HEX_RELEASE else 1.0 - smooth((progress - HEX_RELEASE) / (1 - HEX_RELEASE))
        thrust = math.sin(min(1.0, max(0.0, (progress - HEX_RELEASE) / (1 - HEX_RELEASE))) * math.pi) if progress >= HEX_RELEASE else 0.0
        add(pose, "right_arm", dx=-1.9 * wind - 0.7 * thrust, dz=0.35 * wind)
        add(pose, "right_forearm", dx=0.55 * wind + 0.5 * thrust)
        add(pose, "torso", dx=-0.35 * wind + 0.25 * thrust)
        add(pose, "neck", dx=-0.1 * wind)
        add(pose, "head", dx=-0.15 * wind + 0.1 * thrust)
        add(pose, "left_arm", dx=0.35 * wind, dz=0.3 * wind)
    # Summon: both arms flung up, staff and lantern high, rearing back; then the staff slammed down, bent double.
    if spell == 2:
        rise = smooth(progress / SUMMON_RELEASE)
        slam = smooth((progress - SUMMON_RELEASE) / 0.12) if progress >= SUMMON_RELEASE else 0.0
        recover = smooth((progress - 0.82) / 0.18) if progress >= 0.82 else 0.0
        up = rise * (1.0 - slam)
        down_ = slam * (1.0 - recover)
        add(pose, "right_arm", dx=-2.2 * up - 0.15 * down_, dz=0.4 * up)
        add(pose, "right_forearm", dx=0.6 * up)
        add(pose, "left_arm", dx=-1.4 * up, dz=-0.5 * up)
        add(pose, "left_forearm", dx=0.4 * up)
        add(pose, "torso", dx=-0.45 * up + 0.2 * down_)
        add(pose, "neck", dx=-0.25 * up + 0.1 * down_)
        add(pose, "head", dx=-0.3 * up)
        add(pose, "hips", pos=(0, 0.5 * down_, 0))
    # A rap of the staff: swung across in front of her, low.
    if attack > 0.0:
        a = math.sin(attack * math.pi)
        r = math.sin(math.sqrt(attack) * math.pi)
        add(pose, "right_arm", dx=-0.9 * r, dy=0.7 * a, dz=0.5 * r)
        add(pose, "torso", dy=0.25 * a)
    # The lantern hangs plumb from her hand whatever her arm does, swinging a little behind the motion.
    chain = sum(pose[p][1][0] for p in ("hips", "torso", "left_arm", "left_forearm", "left_hand"))
    lag = 0.25 * math.sin(walk * 0.6 - 1.0) * walk_speed + 0.06 * math.sin(age * 0.09)
    pose["lantern"][1] = (-chain + lag, 0.0, -pose["left_arm"][1][2] + 0.05 * math.sin(age * 0.07))
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


# Z-fighting: no two faces may share a plane, face the same way and overlap, at rest or in any of her poses.
from coplanar import Face, fights

CHECK_POSES = [("rest", rest_pose())]
for _i in range(8):
    CHECK_POSES.append((f"walk{_i}", animate(rest_pose(), walk=_i * 1.3, walk_speed=0.8, age=_i * 7.0)))
for _p in (0.3, HEX_RELEASE, 0.8):
    CHECK_POSES.append((f"hex{_p:.2f}", animate(rest_pose(), spell=1, progress=_p, aggressive=True)))
for _p in (0.4, SUMMON_RELEASE + 0.1, 0.95):
    CHECK_POSES.append((f"summon{_p:.2f}", animate(rest_pose(), spell=2, progress=_p, aggressive=True)))
CHECK_POSES.append(("rap", animate(rest_pose(), attack=0.4, aggressive=True)))
_bad = 0
for _name, _pose in CHECK_POSES:
    _hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(_pose, labels=True)])
    for h_ in _hits:
        print(f"z-fight ({_name}): bog hag", h_)
    _bad += len(_hits)
if _bad:
    raise SystemExit(f"bog hag: {_bad} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, _ in quads(pose) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
_staff = [p for verts, uv, label, n in quads(rest_pose(), labels=True) if label.startswith("staff.") for p in verts]
_lamp = [p for verts, uv, label, n in quads(rest_pose(), labels=True) if label.startswith("lantern.") for p in verts]
print(f"staff foot y {max(p[1] for p in _staff):.2f}, lantern bottom y {max(p[1] for p in _lamp):.2f}")
print(f"bog hag: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f}, "
      f"feet y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


def render(pose, view, scale=8, width=300, height=380, light=(0.35, -0.8, -0.5), focus=None, night=False):
    """Orthographic render. view: 'front' (looking at her face), 'side' (from her right), 'three' (three-quarters),
    'left' (from her left) or 'back'. focus: a model point to centre (default: feet at the bottom). night: dim
    everything but what glows."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "left": -math.pi / 2,
             "three_left": -math.pi / 5}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    bg = (22, 26, 34, 255) if night else (46, 52, 64, 255)
    img = [bg] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs = [p[0] * ca - p[2] * sa for verts, _ in quads(pose) for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs) + max(xs)) / 2 * scale, height - 20, 24.0
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
        if night:
            shade *= 0.35
        du, dv = ub - ua, vb - va
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua + (i + 0.5) * du / nu), TEX_W - 1)
                tv = min(int(va + (j + 0.5) * dv / nv), TEX_H - 1)
                col = TEXTURE.get(int(tu), int(tv))
                if col[3] == 0:
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
                if g[3]:
                    c = (g[0], g[1], g[2], 255)
                else:
                    c = (int(col[0] * shade), int(col[1] * shade), int(col[2] * shade), 255)
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


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    S = 8
    views = [render(rest_pose(), "front", scale=S), render(rest_pose(), "side", scale=S), render(rest_pose(), "three", scale=S),
             render(animate(rest_pose(), walk=1.3, walk_speed=0.9, age=10), "three", scale=S),
             render(animate(rest_pose(), spell=1, progress=HEX_RELEASE - 0.02, aggressive=True), "side", scale=S),
             render(animate(rest_pose(), spell=1, progress=0.75, aggressive=True), "three", scale=S),
             render(animate(rest_pose(), spell=2, progress=SUMMON_RELEASE - 0.02, aggressive=True), "three", scale=S),
             render(animate(rest_pose(), spell=2, progress=0.78, aggressive=True), "side", scale=S),
             render(animate(rest_pose(), attack=0.45, aggressive=True), "front", scale=S),
             render(rest_pose(), "back", scale=S), render(rest_pose(), "three_left", scale=S),
             render(rest_pose(), "three", scale=S, night=True)]
    head_pts = [p for verts, uv, label, n in quads(rest_pose(), labels=True) if label.startswith("head.") for p in verts]
    centre = tuple(sum(p[i] for p in head_pts) / len(head_pts) for i in range(3))
    views += [render(rest_pose(), "front", scale=24, focus=centre), render(rest_pose(), "left", scale=24, focus=centre),
              render(rest_pose(), "three_left", scale=24, focus=centre)]
    out, bw, bh = board(views, 300, 380, 5)
    png(os.path.join(PREVIEW, "preview_bog_hag.png"), bw, bh, out)
    s = 4
    big = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
           for y in range(TEX_H * s) for x in range(TEX_W * s)]
    for y in range(TEX_H * s):
        for x in range(TEX_W * s):
            c = TEXTURE.get(x // s, y // s)
            if c[3]:
                big[y * TEX_W * s + x] = c
    png(os.path.join(PREVIEW, "preview_bog_hag_texture.png"), TEX_W * s, TEX_H * s, big)
    # The item sprites and effect icons at 8x, on a checker.
    sprites = [HAG_CHARM, BOG_MUCK, HEXBREAKER_TEA, HAGS_CURSE_ICON, HEX_WARD_ICON]
    k, cell = 8, 18 * 8 + 8
    sheet = [(40, 44, 52, 255) if ((x // 16) + (y // 16)) % 2 == 0 else (52, 56, 64, 255) for y in range(cell) for x in range(cell * 6)]
    for n, sp in enumerate(sprites + [None]):
        w_, h_, px_ = (EGG.w, EGG.h, EGG.px) if sp is None else (sp.w, sp.h, sp.px)
        for y in range(h_ * k):
            for x in range(w_ * k):
                c = px_[(y // k) * w_ + x // k]
                if c[3]:
                    sheet[(y + 4) * cell * 6 + n * cell + 4 + x] = c
    png(os.path.join(PREVIEW, "preview_bog_hag_items.png"), cell * 6, cell, sheet)
    print("preview written to", PREVIEW)
print("done")

