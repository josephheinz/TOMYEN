"""Builds the crucifix: an ebony cross with gold inlay, budded gold terminals, a titulus, a radiant halo and
a stepped Golgotha base with a skull, carrying a carved ivory corpus of Christ. Writes the item model (and
the raised pose used while charging), its texture atlas, and a 32x32 inventory sprite.

Everything is laid out in design units (half a model pixel each): x across with the corpus centred on 0, y up
from the base, z toward the viewer with the cross's front face at z = 1. The corpus faces +z (south),
which is the side the camera sees in first person."""
import json, math, os, random, struct, sys, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from zfight import check as zfight_check

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
rnd = random.Random(33)

K = 0.5      # model pixels per design unit
Y0 = -1.5    # model y of the design origin


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rgb(hx, a=255, jitter=0):
    r, g, b = (hx >> 16) & 255, (hx >> 8) & 255, hx & 255
    d = rnd.randint(-jitter, jitter) if jitter else 0
    c = lambda v: max(0, min(255, v + d))
    return (c(r), c(g), c(b), a)


def mix(a, b, t):
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    return sum(int(ca[i] + (cb[i] - ca[i]) * t) << s for i, s in enumerate((16, 8, 0)))


# ----------------------------------------------------------------------------------------------- texture atlas
# 64x64 texels; swatch rectangles are in model UV units (0-16), four texels each.
W = 64
atlas = [(0, 0, 0, 0)] * (W * W)
SW = {}


def swatch(name, u0, v0, u1, v1, f, picture=False):
    """f(x, y, w, h) -> colour for texel (x, y) of a w x h texel swatch. Picture swatches map whole onto a face."""
    SW[name] = ((u0, v0, u1, v1), picture)
    w, h = (u1 - u0) * 4, (v1 - v0) * 4
    for y in range(h):
        for x in range(w):
            atlas[(v0 * 4 + y) * W + u0 * 4 + x] = f(x, y, w, h)


EBONY, EBONY_GRAIN, EBONY_HI = 0x2A1810, 0x1B0F0A, 0x3B2418
GOLD, GOLD_HI, GOLD_LO, GOLD_DEEP = 0xD6AB43, 0xF7E08E, 0xA27A25, 0x6E4E14
IVORY, IVORY_HI, IVORY_SHADE, IVORY_DEEP = 0xE9D3B2, 0xF7EAD4, 0xCDB08A, 0xA98A66
HAIR, HAIR_HI = 0x4A2E1C, 0x63412A
CLOTH, CLOTH_SHADE, CLOTH_DEEP = 0xF3EEE2, 0xD8D0BE, 0xB9AE98
BLOOD, BLOOD_DARK = 0xA0101A, 0x6A0810
THORN, THORN_HI = 0x3E3A1E, 0x5C5630
BONE, BONE_SHADE, BONE_DARK = 0xE4DCC6, 0xBDB298, 0x3A3226
PARCHMENT, INK = 0xEADFC0, 0x2A1A10


def wood(vertical):
    def f(x, y, w, h):
        a, b = (x, y) if vertical else (y, x)
        streak = (a * 7 + int(3 * math.sin(b * 0.35 + a))) % 5
        return rgb(EBONY_GRAIN if streak == 0 else EBONY_HI if streak == 3 else EBONY, jitter=3)
    return f


swatch("wood_v", 0, 0, 2, 8, wood(True))
swatch("wood_h", 2, 0, 10, 2, wood(False))
swatch("wood_end", 2, 2, 4, 4, lambda x, y, w, h: rgb(EBONY_GRAIN if int(math.hypot(x - 3.5, y - 3.5)) % 2 else EBONY, jitter=2))
swatch("gold", 4, 2, 6, 4, lambda x, y, w, h: rgb(GOLD_HI if y == 0 or x == 0 else GOLD_LO if y == h - 1 else GOLD, jitter=5))
# Engraved: a running scroll of dark lines in the gold.
swatch("gold_engraved", 6, 2, 8, 4, lambda x, y, w, h: rgb(GOLD_DEEP if (x + 2 * int(1.5 * math.sin(y * 0.9))) % 4 == 0 else
                                                             GOLD_HI if (x + y) % 7 == 0 else GOLD, jitter=4))
swatch("gold_dark", 8, 2, 10, 4, lambda x, y, w, h: rgb(GOLD_LO if (x + y) % 3 else GOLD_DEEP, jitter=3))
swatch("skin", 10, 0, 12, 2, lambda x, y, w, h: rgb(IVORY, jitter=3))
swatch("skin_shade", 12, 0, 14, 2, lambda x, y, w, h: rgb(IVORY_SHADE, jitter=3))
swatch("skin_light", 14, 0, 16, 2, lambda x, y, w, h: rgb(IVORY_HI, jitter=2))
swatch("hair", 8, 4, 10, 6, lambda x, y, w, h: rgb(HAIR_HI if x % 3 == 0 else HAIR, jitter=4))
swatch("beard", 8, 6, 10, 8, lambda x, y, w, h: rgb(HAIR_HI if (x + y) % 3 == 0 else HAIR, jitter=3))
swatch("cloth", 10, 6, 12, 8, lambda x, y, w, h: rgb(CLOTH_SHADE if x % 3 == 2 else CLOTH, jitter=2))
swatch("cloth_shade", 12, 6, 14, 8, lambda x, y, w, h: rgb(CLOTH_DEEP if x % 3 == 2 else CLOTH_SHADE, jitter=2))
swatch("thorn", 14, 2, 16, 4, lambda x, y, w, h: rgb(THORN_HI if (x * 3 + y) % 4 == 0 else THORN, jitter=4))
swatch("blood", 14, 4, 15, 5, lambda x, y, w, h: rgb(BLOOD if y < 2 else BLOOD_DARK))
swatch("nail", 15, 4, 16, 5, lambda x, y, w, h: rgb(0x8A8F95 if x == 1 and y == 1 else 0x55595E))
swatch("halo", 14, 5, 16, 7, lambda x, y, w, h: rgb(0xFFE9A8))
swatch("gem_red", 8, 8, 9, 9, lambda x, y, w, h: rgb(0xFF7A80 if (x, y) == (1, 1) else 0xC8102A))
swatch("gem_white", 9, 8, 10, 9, lambda x, y, w, h: rgb(0xFFFFFF if (x, y) == (1, 1) else 0xFFF2C8))
swatch("bone", 4, 8, 6, 10, lambda x, y, w, h: rgb(BONE_SHADE if y >= h - 2 else BONE, jitter=3))

# The titulus: INRI in dark letters on parchment, with a thin gold border.
LETTERS = {"I": ["#", "#", "#", "#", "#"], "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
           "R": ["###.", "#..#", "###.", "#.#.", "#..#"]}
def plaque(x, y, w, h):
    if x in (0, w - 1) or y in (0, h - 1):
        return rgb(GOLD)
    cx = 2
    for ch in "INRI":
        glyph = LETTERS[ch]
        gw = len(glyph[0])
        if cx <= x < cx + gw and 1 <= y - 1 < 6 and y - 2 < 5 and y >= 2:
            return rgb(INK) if glyph[y - 2][x - cx] == "#" else rgb(PARCHMENT, jitter=3)
        cx += gw + 1
    return rgb(PARCHMENT, jitter=3)
swatch("plaque", 0, 8, 4, 10, plaque, picture=True)

# Skull front: brow, two dark sockets, a nose hole and a row of teeth.
def skull_face(x, y, w, h):
    if y in (2, 3) and x in (1, 2, 5, 6):
        return rgb(BONE_DARK)
    if y == 4 and x in (3, 4):
        return rgb(BONE_DARK)
    if y == 6 and 1 <= x <= 6:
        return rgb(BONE_DARK if x % 2 else BONE)
    return rgb(BONE_SHADE if y == 7 or y == 1 else BONE, jitter=2)
swatch("skull_face", 6, 8, 8, 10, skull_face, picture=True)


def face(x, y, w, h):
    """The face, eyes closed and head bowed: long hair parted at the middle, brows, nose, moustache and beard."""
    if x <= 1 or x >= 14 or y <= 1 or (y == 2 and not 5 <= x <= 10) or (y == 3 and x in (2, 13)):
        return rgb(HAIR_HI if (x + y) % 4 == 0 else HAIR, jitter=3)
    if y == 2 and x in (7, 8):
        return rgb(HAIR)  # the parting
    if y in (3, 4) and x in (4, 11) or (y == 3 and x == 9):
        return rgb(BLOOD)  # drops from the thorns
    if y == 6 and (3 <= x <= 6 or 9 <= x <= 12):
        return rgb(HAIR)  # brows
    if y == 7 and (4 <= x <= 6 or 9 <= x <= 11):
        return rgb(IVORY_DEEP)  # closed lids
    if y == 8 and (4 <= x <= 6 or 9 <= x <= 11):
        return rgb(IVORY_SHADE)
    if 7 <= y <= 10 and x in (7, 8):
        return rgb(IVORY_HI if x == 7 else IVORY_SHADE)  # nose
    if y == 10 and x in (6, 9):
        return rgb(IVORY_SHADE)
    if y == 11 and 4 <= x <= 11:
        return rgb(HAIR)  # moustache
    if y == 12 and 6 <= x <= 9:
        return rgb(0x6A3A30 if 7 <= x <= 8 else HAIR)
    if y >= 12 or (y >= 9 and x in (2, 3, 12, 13)):
        return rgb(HAIR_HI if (x * 3 + y) % 5 == 0 else HAIR, jitter=2)  # beard
    return rgb(IVORY_SHADE if x in (2, 3, 12, 13) else IVORY, jitter=2)
swatch("face", 4, 4, 8, 8, face, picture=True)


def torso(x, y, w, h):
    """Chest: pectorals, sternum, ribs showing at the sides, and the wound in his right side (viewer's left)."""
    if 2 <= x <= 3 and 9 <= y <= 10:
        return rgb(BLOOD)
    if x == 3 and 11 <= y <= 13:
        return rgb(BLOOD_DARK)
    if y == 6 and (2 <= x <= 6 or 9 <= x <= 13):
        return rgb(IVORY_DEEP)  # under the pectorals
    if y < 6 and (x in (7, 8)):
        return rgb(IVORY_SHADE)  # sternum
    if y >= 8 and (x <= 4 or x >= 11) and (y + (x % 2)) % 2 == 0:
        return rgb(IVORY_SHADE)  # ribs
    if y <= 1 and (x <= 2 or x >= 13):
        return rgb(IVORY_SHADE)
    return rgb(IVORY_HI if 3 <= y <= 4 and x in (4, 11) else IVORY, jitter=2)
swatch("torso", 10, 2, 14, 6, torso, picture=True)


def abdomen(x, y, w, h):
    if x in (7, 8) and y in (9, 10):
        return rgb(IVORY_DEEP)  # navel
    if x in (7, 8) and y < 14:
        return rgb(IVORY_SHADE)  # centre line
    if y in (3, 7, 11) and 4 <= x <= 11:
        return rgb(IVORY_SHADE)
    if x <= 1 or x >= 14:
        return rgb(IVORY_SHADE)
    return rgb(IVORY, jitter=2)
swatch("abdomen", 0, 10, 4, 14, abdomen, picture=True)

png(os.path.join(TEX, "item/crucifix_parts.png"), W, W, atlas)

# ----------------------------------------------------------------------------------------------- elements
FACES = ["north", "east", "south", "west", "up", "down"]
DIRS = {"north": ("x", "y"), "south": ("x", "y"), "east": ("z", "y"), "west": ("z", "y"), "up": ("x", "z"), "down": ("x", "z")}
elements = []


def uv_for(name, du, dv):
    (u0, v0, u1, v1), picture = SW[name]
    if picture:
        return [u0, v0, u1, v1]
    # One texel per design unit, cut from a varying spot in the swatch, stretched if the face is bigger.
    su, sv = min(u1 - u0, du * 0.25), min(v1 - v0, dv * 0.25)
    ou = rnd.uniform(0, (u1 - u0) - su)
    ov = rnd.uniform(0, (v1 - v0) - sv)
    return [round(u0 + ou, 3), round(v0 + ov, 3), round(u0 + ou + su, 3), round(v0 + ov + sv, 3)]


def B(x0, y0, z0, x1, y1, z1, mat, rot=None, emit=False, name=None, **face_mats):
    """A box in design units. mat: swatch for every face; face_mats override per face (None drops a face)."""
    lo = [8 + x0 * K, Y0 + y0 * K, 8 + z0 * K]
    hi = [8 + x1 * K, Y0 + y1 * K, 8 + z1 * K]
    e = OrderedDict([("from", [round(v, 4) for v in lo]), ("to", [round(v, 4) for v in hi])])
    if name:
        e["name"] = name
    if rot:
        axis, angle, (ox, oy, oz) = rot
        e["rotation"] = OrderedDict([("angle", angle), ("axis", axis),
                                     ("origin", [round(8 + ox * K, 4), round(Y0 + oy * K, 4), round(8 + oz * K, 4)])])
    if emit:
        e["shade"] = False
        e["light_emission"] = 15
    size = {"x": abs(x1 - x0), "y": abs(y1 - y0), "z": abs(z1 - z0)}
    e["faces"] = OrderedDict()
    for f in FACES:
        m = face_mats.get(f, mat)
        if m is None:
            continue
        a, b = DIRS[f]
        e["faces"][f] = OrderedDict([("uv", uv_for(m, size[a], size[b])), ("texture", "#parts")])
    elements.append(e)
    return e


def rotated_box(cx, cy, z0, z1, hw, hh, angle, mat, emit=False):
    """A flat box centred on (cx, cy), turned about z."""
    return B(cx - hw, cy - hh, z0, cx + hw, cy + hh, z1, mat, rot=("z", angle, (cx, cy, z0)), emit=emit)


def rounded(cx, cy, cz, r, depth_lo, depth_hi, mat, step=0.0):
    """A disc-like boss: two crossed square boxes (one turned 45 degrees), slightly offset in depth."""
    B(cx - r * 0.82, cy - r * 0.82, depth_lo, cx + r * 0.82, cy + r * 0.82, depth_hi, mat)
    rotated_box(cx, cy, depth_lo + 0.02, depth_hi - 0.02, r * 0.82, r * 0.82, 45, mat)


# --- the cross: ebony, upright and crossbar (the crossbar a hair thinner so their faces never coincide)
UP_X, UP_Y0, UP_Y1 = 2.0, 3.3, 33.0
BAR_X, BAR_Y0, BAR_Y1 = 12.0, 21.5, 25.5
B(-UP_X, UP_Y0, -1.0, UP_X, UP_Y1, 1.0, "wood_v", up="wood_end", down="wood_end", name="upright")
B(-BAR_X, BAR_Y0, -0.9, BAR_X, BAR_Y1, 0.9, "wood_h", east="wood_end", west="wood_end", name="crossbar")

# Gold inlay along every edge of both faces, in pieces that meet without overlapping.
def inlay(z0, z1):
    s = 0.4
    for sx in (-1, 1):
        B(sx * UP_X - (s if sx > 0 else 0), UP_Y0 + 0.6, z0, sx * UP_X + (0 if sx > 0 else s), BAR_Y0, z1, "gold")
        B(sx * UP_X - (s if sx > 0 else 0), BAR_Y1, z0, sx * UP_X + (0 if sx > 0 else s), UP_Y1 - 0.6, z1, "gold")
        x_in, x_out = sx * UP_X, sx * (BAR_X - 0.6)
        xa, xb = min(x_in, x_out), max(x_in, x_out)
        bz0, bz1 = z0 - 0.1 * (1 if z0 > 0 else -1), z1 - 0.1 * (1 if z0 > 0 else -1)
        B(xa, BAR_Y0, min(bz0, bz1), xb, BAR_Y0 + s, max(bz0, bz1), "gold")
        B(xa, BAR_Y1 - s, min(bz0, bz1), xb, BAR_Y1, max(bz0, bz1), "gold")
inlay(1.0, 1.12)
inlay(-1.12, -1.0)

# Back: an engraved gold lozenge at the crossing with a white stone.
rotated_box(0, 23.5, -1.3, -1.0, 2.0, 2.0, 45, "gold_engraved")
B(-0.6, 22.9, -1.55, 0.6, 24.1, -1.25, "gem_white", emit=True)


def terminal(cx, cy, direction):
    """A budded (trefoil) gold end: an engraved lozenge with three round buds pointing outward and a ruby."""
    dx, dy = direction
    rotated_box(cx, cy, -1.25, 1.25, 2.0, 2.0, 45, "gold_engraved")
    B(cx - 1.25, cy - 1.25, -1.35, cx + 1.25, cy + 1.25, 1.35, "gold")  # collar where it meets the wood
    px, py = -dy, dx  # perpendicular
    for bx, by in [(cx + dx * 3.0, cy + dy * 3.0), (cx + px * 2.5 + dx * 1.2, cy + py * 2.5 + dy * 1.2),
                   (cx - px * 2.5 + dx * 1.2, cy - py * 2.5 + dy * 1.2)]:
        rounded(bx, by, 0, 1.15, -1.0, 1.0, "gold")
        B(bx - 0.35, by - 0.35, 1.0, bx + 0.35, by + 0.35, 1.2, "gold_dark")  # a pearl bead on each bud
    B(cx - 0.6, cy - 0.6, 1.35, cx + 0.6, cy + 0.6, 1.65, "gem_red", emit=True)
    B(cx - 0.6, cy - 0.6, -1.65, cx + 0.6, cy + 0.6, -1.35, "gem_red", emit=True)


terminal(0, UP_Y1 + 1.4, (0, 1))
terminal(-(BAR_X + 1.4), 23.5, (-1, 0))
terminal(BAR_X + 1.4, 23.5, (1, 0))

# Titulus above the head: a parchment plaque reading INRI, in a gold frame.
B(-2.7, 27.6, 1.04, 2.7, 30.0, 1.32, "plaque", name="titulus")
B(-3.0, 30.0, 0.95, 3.0, 30.35, 1.42, "gold")
B(-3.0, 27.25, 0.95, 3.0, 27.6, 1.42, "gold")
B(-3.0, 27.6, 0.95, -2.7, 30.0, 1.42, "gold")
B(2.7, 27.6, 0.95, 3.0, 30.0, 1.42, "gold")

# Golgotha: a stepped base, a skull and crossed bones at the foot of the cross.
B(-5.5, 0.0, -3.5, 5.5, 1.4, 3.5, "gold_engraved", name="base")
B(-4.2, 1.4, -2.6, 4.2, 2.6, 2.6, "wood_h", up="wood_end", down="wood_end")
B(-3.0, 2.6, -1.8, 3.0, UP_Y0, 1.8, "gold")
B(-1.0, 1.5, 2.6, 1.0, 3.1, 4.2, "bone", south="skull_face", name="skull")
B(-0.7, 1.4, 3.1, 0.7, 1.75, 4.05, "bone_dark" if "bone_dark" in SW else "bone")
for angle, z in [(32, 2.62), (-32, 2.66)]:
    rotated_box(0, 2.4, z, z + 0.3, 2.3, 0.22, angle, "bone")
    for sx in (-1, 1):
        ex, ey = sx * 2.3 * math.cos(math.radians(angle)), 2.4 + sx * 2.3 * math.sin(math.radians(angle))
        B(ex - 0.3, ey - 0.3, z - 0.05, ex + 0.3, ey + 0.3, z + 0.38, "bone")

# --- halo: a glowing disc behind the head, with rays
HX, HY = -0.55, 23.85
for k, z in enumerate((1.30, 1.33, 1.36)):
    rotated_box(HX, HY, z, z + 0.05, 1.9, 1.9, k * 30, "halo", emit=True)
for k in range(12):
    angle = k * 30 + 15
    length = 4.6 if k % 2 == 0 else 3.6
    a = math.radians(angle)
    mx, my = HX + math.cos(a) * length / 2, HY + math.sin(a) * length / 2
    rotated_box(mx, my, 1.22, 1.27, length / 2, 0.16, angle, "halo", emit=True)

# --- the corpus, carved in ivory
SKIN = "skin"
# Footrest (suppedaneum) the feet are nailed to.
B(-1.7, 3.8, 1.02, 1.7, 4.55, 3.6, "wood_h", up="wood_end")
B(-1.7, 3.55, 1.03, 1.7, 3.8, 3.7, "gold")

# Head, bowed toward his right shoulder (viewer's left): everything on it shares one turn about the neck.
NECK = (0.0, 22.3, 2.5)
TILT = ("z", 20, NECK)
B(-1.3, 22.4, 1.6, 1.3, 25.4, 4.2, "hair", south="face", down="skin_shade", rot=TILT, name="head")
B(-0.2, 23.5, 4.2, 0.2, 24.25, 4.45, "skin_light", rot=TILT)                  # nose
B(-0.95, 22.05, 3.6, 0.95, 22.9, 4.35, "beard", rot=TILT)                    # beard point
B(-1.6, 21.7, 1.75, -1.3, 24.9, 3.7, "hair", rot=TILT)                       # long hair, sides and back
B(1.3, 21.7, 1.75, 1.6, 24.9, 3.7, "hair", rot=TILT)
B(-1.45, 21.55, 1.42, 1.45, 25.2, 1.62, "hair", rot=TILT)
# Crown of thorns: a twisted band round the brow with thorns sticking out.
B(-1.45, 24.55, 4.2, 1.45, 25.0, 4.5, "thorn", rot=TILT)
B(-1.45, 24.6, 1.3, 1.45, 25.05, 1.42, "thorn", rot=TILT)
B(-1.75, 24.6, 1.42, -1.6, 25.05, 4.5, "thorn", rot=TILT)
B(1.6, 24.6, 1.42, 1.75, 25.05, 4.5, "thorn", rot=TILT)
for tx, ty, tz in [(-1.0, 25.0, 4.45), (0.3, 25.0, 4.45), (1.1, 24.95, 4.4), (-1.75, 25.0, 3.2), (1.75, 25.0, 2.2),
                   (-0.4, 25.05, 4.47), (1.7, 24.95, 3.9)]:
    B(tx - 0.07, ty, tz - 0.07, tx + 0.07, ty + 0.4, tz + 0.07, "thorn", rot=TILT)
B(-0.55, 21.6, 1.9, 0.55, 22.6, 2.9, SKIN, name="neck")

# Torso: chest with pectorals and ribs, a narrower belly, rounded shoulders.
B(-2.1, 17.5, 1.3, 2.1, 21.6, 3.3, "skin_shade", south="torso", up=SKIN, name="chest")
B(-1.95, 19.6, 3.3, -0.1, 21.0, 3.5, SKIN, down="skin_shade")
B(0.1, 19.6, 3.3, 1.95, 21.0, 3.5, SKIN, down="skin_shade")
B(-1.75, 15.6, 1.4, 1.75, 17.5, 3.05, "skin_shade", south="abdomen")
for sx in (-1, 1):
    B(min(sx * 1.7, sx * 2.75), 20.1, 1.45, max(sx * 1.7, sx * 2.75), 21.65, 3.1, SKIN)

# Arms stretched out along the crossbar, rising a little to the nailed hands.
def arm(sx):
    sh = (sx * 2.4, 21.0, 2.25)
    upper_len, fore_len = 4.2, 3.6
    a1, a2 = 12.0 * sx, 22.0 * sx
    x0 = sh[0]
    B(min(x0, x0 + sx * upper_len), sh[1] - 0.6, 1.65, max(x0, x0 + sx * upper_len), sh[1] + 0.6, 2.85, SKIN,
      down="skin_shade", rot=("z", a1, sh))
    ex = sh[0] + sx * upper_len * math.cos(math.radians(12.0))
    ey = sh[1] + upper_len * math.sin(math.radians(12.0))
    el = (ex, ey, 2.25)
    B(min(ex, ex + sx * fore_len), ey - 0.5, 1.75, max(ex, ex + sx * fore_len), ey + 0.5, 2.75, SKIN,
      down="skin_shade", rot=("z", a2, el))
    B(min(ex - sx * 0.3, ex + sx * 0.4), ey - 0.55, 1.7, max(ex - sx * 0.3, ex + sx * 0.4), ey + 0.55, 2.8, "skin_shade",
      rot=("z", a2, el))  # elbow
    hx = ex + sx * fore_len
    B(min(hx, hx + sx * 1.6), ey - 0.55, 1.6, max(hx, hx + sx * 1.6), ey + 0.65, 2.55, SKIN, rot=("z", a2, el))  # hand
    B(min(hx + sx * 0.35, hx + sx * 1.35), ey + 0.65, 1.7, max(hx + sx * 0.35, hx + sx * 1.35), ey + 0.85, 2.4,
      "skin_shade", rot=("z", a2, el))  # curled fingers
    nx = hx + sx * 0.8
    B(nx - 0.28, ey - 0.28, 2.55, nx + 0.28, ey + 0.28, 2.8, "nail", rot=("z", a2, el))
    B(nx - 0.12, ey - 0.9, 2.5, nx + 0.12, ey - 0.28, 2.6, "blood", rot=("z", a2, el))
arm(-1)
arm(1)

# Loincloth (perizoma): wrapped round the hips, knotted at his right hip with the end hanging down.
B(-2.0, 13.4, 1.25, 2.0, 15.9, 3.25, "cloth", down="cloth_shade", name="loincloth")
B(-2.15, 15.65, 1.2, 2.15, 16.1, 3.35, "cloth_shade")
B(-1.4, 12.6, 2.2, 1.1, 13.4, 3.35, "cloth")
B(-2.55, 11.4, 2.45, -1.35, 15.2, 3.5, "cloth_shade", south="cloth")
B(-2.7, 14.6, 2.3, -1.6, 15.7, 3.6, "cloth_shade")

# Legs together, knees bent forward a little, the right foot crossed over the left, one nail through both.
HIP_Y, HIP_Z = 13.6, 2.3
THIGH, SHIN = 4.0, 3.9
a_thigh, a_shin = -12.0, 15.0
knee_y = HIP_Y - THIGH * math.cos(math.radians(-a_thigh))
knee_z = HIP_Z + THIGH * math.sin(math.radians(-a_thigh))
for x0, x1 in [(-1.75, -0.12), (0.12, 1.75)]:
    B(x0, HIP_Y - THIGH, HIP_Z - 0.8, x1, HIP_Y, HIP_Z + 0.8, SKIN, rot=("x", a_thigh, (0, HIP_Y, HIP_Z)))
for x0, x1 in [(-1.5, -0.15), (0.15, 1.5)]:
    B(x0, knee_y - SHIN, knee_z - 0.65, x1, knee_y + 0.2, knee_z + 0.65, SKIN, west="skin_shade",
      rot=("x", a_shin, (0, knee_y, knee_z)))
    B(x0 + 0.1, knee_y - 0.45, knee_z - 0.05, x1 - 0.1, knee_y + 0.45, knee_z + 0.85, "skin_light",
      rot=("x", a_shin, (0, knee_y, knee_z)))  # kneecap
foot_y = knee_y - SHIN * math.cos(math.radians(a_shin))
foot_z = knee_z - SHIN * math.sin(math.radians(a_shin))
B(-0.15, foot_y - 1.7, foot_z - 0.6, 1.05, foot_y + 0.1, foot_z + 0.45, SKIN, down="skin_shade")          # left foot, behind
B(-0.95, foot_y - 1.55, foot_z + 0.35, 0.3, foot_y + 0.15, foot_z + 1.35, "skin_light", down="skin_shade")  # right foot over it
B(-0.32, foot_y - 0.95, foot_z + 1.35, 0.32, foot_y - 0.35, foot_z + 1.6, "nail")
B(-0.12, foot_y - 1.5, foot_z + 1.33, 0.12, foot_y - 0.95, foot_z + 1.45, "blood")

# ----------------------------------------------------------------------------------------------- checks + output
fights = zfight_check(elements)
if fights:
    for f in fights:
        print("z-fight:", f)
    raise SystemExit("crucifix model has z-fighting")

zs = [v for e in elements for v in (e["from"][2], e["to"][2])]
ys = [v for e in elements for v in (e["from"][1], e["to"][1])]
xs = [v for e in elements for v in (e["from"][0], e["to"][0])]
assert min(xs) >= -16 and max(xs) <= 32 and min(ys) >= -16 and max(ys) <= 32 and min(zs) >= -16 and max(zs) <= 32


def write(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def display(rot, tr, sc):
    return OrderedDict([("rotation", rot), ("translation", tr), ("scale", [sc, sc, sc])])


write(os.path.join(MODELS, "crucifix.json"), OrderedDict([
    ("textures", OrderedDict([("parts", "tomyen:item/crucifix_parts"), ("particle", "tomyen:item/crucifix_parts")])),
    ("elements", elements),
    ("display", OrderedDict([
        ("thirdperson_righthand", display([0, 0, 0], [0, 2.0, 1.5], 0.5)),
        ("firstperson_righthand", display([-6, -12, 3], [0.5, 3.0, -1.5], 0.52)),
        ("ground", display([0, 0, 0], [0, 2, 0], 0.4)),
        ("head", display([0, 180, 0], [0, 13, 7], 0.9)),
        ("fixed", display([0, 0, 0], [0, 0, 0], 0.75)),
    ])),
]))
# Held up and out toward the middle of the screen while charging a beam.
write(os.path.join(MODELS, "crucifix_raised.json"), OrderedDict([("parent", "tomyen:item/crucifix"), ("display", OrderedDict([
    ("thirdperson_righthand", display([-70, 0, 0], [0, 4, 3], 0.55)),
    ("firstperson_righthand", display([0, 0, 0], [-4.5, 5.5, -5.5], 0.62)),
]))]))
write(os.path.join(MODELS, "crucifix_icon.json"), OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/crucifix"})]))
write(os.path.join(ITEMS, "crucifix.json"), {"model": OrderedDict([
    ("type", "minecraft:select"), ("property", "minecraft:display_context"),
    ("cases", [{"when": ["gui"], "model": {"type": "minecraft:model", "model": "tomyen:item/crucifix_icon"}}]),
    ("fallback", OrderedDict([("type", "minecraft:condition"), ("property", "minecraft:using_item"),
                              ("on_true", {"type": "minecraft:model", "model": "tomyen:item/crucifix_raised"}),
                              ("on_false", {"type": "minecraft:model", "model": "tomyen:item/crucifix"})]))])})

# ----------------------------------------------------------------------------------------------- 32x32 sprite
S = 32
spr = [(0, 0, 0, 0)] * (S * S)
def put(x, y, c):
    if 0 <= x < S and 0 <= y < S:
        spr[y * S + x] = rgb(c) if isinstance(c, int) else c
def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(x, y, c)
# Ebony cross outlined in gold.
cross = set()
for y in range(3, 27):
    for x in range(13, 19):
        cross.add((x, y))
for y in range(8, 14):
    for x in range(3, 29):
        cross.add((x, y))
for x, y in cross:
    edge = any((x + dx, y + dy) not in cross for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    lit = (x - 1, y) not in cross or (x, y - 1) not in cross
    put(x, y, (GOLD_HI if lit else GOLD_LO) if edge else (EBONY_GRAIN if (x * 3 + y) % 5 == 0 else EBONY))
# Budded terminals with rubies.
for cx, cy in [(15.5, 1.5), (1.5, 10.5), (29.5, 10.5)]:
    for y in range(S):
        for x in range(S):
            d = abs(x - cx) + abs(y - cy)
            if d <= 2.6:
                put(x, y, GOLD_HI if x < cx and y < cy else GOLD if d < 2 else GOLD_LO)
    put(int(cx), int(cy), 0xC8102A)
# Titulus.
rect(13, 3, 18, 4, PARCHMENT)
for x in (14, 15, 17):
    put(x, 3, INK)
put(16, 4, INK)
# Halo round the head.
for y in range(S):
    for x in range(S):
        d = math.hypot(x - 15.0, y - 8.0)
        if 2.2 <= d <= 3.3:
            put(x, y, mix(0xFFF2C0, 0xE8C25A, (d - 2.2) / 1.1))
# Head, bowed to his right (the viewer's left): hair, face, beard.
rect(14, 7, 16, 9, IVORY)
put(14, 6, HAIR); put(15, 6, HAIR); put(16, 6, HAIR); put(13, 7, HAIR); put(13, 8, HAIR); put(16, 7, HAIR)
put(15, 9, HAIR); put(14, 9, IVORY_SHADE); put(14, 6, THORN); put(16, 6, THORN)
# Arms out along the bar to the nailed hands.
for x in range(5, 14):
    y = 9 if x < 9 else 10
    put(x, y, IVORY); put(x, y + 1, IVORY_SHADE)
for x in range(18, 27):
    y = 9 if x > 22 else 10
    put(x, y, IVORY); put(x, y + 1, IVORY_SHADE)
put(5, 9, 0x8A8F95); put(26, 9, 0x8A8F95); put(5, 10, BLOOD); put(26, 10, BLOOD)
# Torso, wound in the side, loincloth with its knot, legs and the nailed feet.
rect(14, 10, 17, 15, IVORY)
for y in range(10, 16):
    put(17, y, IVORY_SHADE)
put(15, 12, IVORY_SHADE); put(16, 12, IVORY_SHADE); put(14, 13, BLOOD)
rect(14, 16, 17, 17, CLOTH); put(17, 17, CLOTH_SHADE); put(13, 16, CLOTH_SHADE); put(13, 17, CLOTH); put(13, 18, CLOTH_SHADE)
rect(15, 18, 16, 23, IVORY)
for y in range(18, 24):
    put(16, y, IVORY_SHADE)
put(15, 20, IVORY_SHADE)
rect(15, 24, 16, 24, IVORY); put(15, 24, 0x8A8F95); put(16, 25, BLOOD)
# Golgotha: stepped gold base with the skull.
rect(11, 27, 20, 28, GOLD); rect(9, 29, 22, 30, GOLD_LO); rect(8, 31, 23, 31, GOLD_DEEP)
rect(14, 27, 17, 29, BONE); put(14, 28, BONE_DARK); put(16, 28, BONE_DARK); put(15, 29, BONE_SHADE)
png(os.path.join(TEX, "item/crucifix.png"), S, S, spr)
print(f"crucifix: {len(elements)} elements")
