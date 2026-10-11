"""Paints the vampire (player-skin layout), its cloak and eyes, the light orb, the bleeding icon and the vampire
spawn egg, and writes the egg item model. The crucifix is tools/gen_crucifix.py."""
import json, math, os, random, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
rnd = random.Random(11)


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


def rgb(hx, a=255, jitter=0):
    r, g, b = (hx >> 16) & 255, (hx >> 8) & 255, hx & 255
    d = rnd.randint(-jitter, jitter) if jitter else 0
    c = lambda v: max(0, min(255, v + d))
    return (c(r), c(g), c(b), a)


CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def rect(self, u, v, w, h, f):
        """f(x, y) -> colour, with x, y relative to the rectangle; None leaves it clear."""
        for y in range(h):
            for x in range(w):
                c = f(x, y)
                if c is not None:
                    self.px[(v + y) * self.w + u + x] = c

    def cube(self, u, v, w, h, d, top=None, bottom=None, right=None, front=None, left=None, back=None, side=None):
        """Paints a model cube's faces in the standard layout; side paints right, left and back unless given."""
        faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                 "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        given = dict(top=top, bottom=bottom, right=right or side, front=front, left=left or side, back=back or side)
        for name, f in given.items():
            if f:
                self.rect(*faces[name], f)

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


# ---------------------------------------------------------------------------------------------- vampire skin
SKIN, SKIN_SHADE, SKIN_DARK = 0xDAD6D1, 0xC5C0BB, 0xA8A29E
HAIR, BROW = 0x121014, 0x1E1A20
EYE, EYE_HOT = 0xB01010, 0xFF3030
LIP, FANG = 0x5A0E14, 0xF4F1EA
COAT, COAT_HI, COAT_DARK = 0x17141B, 0x231F29, 0x0D0B10
SHIRT, VEST, VEST_DARK, GOLD = 0xE6E2DA, 0x6E0F1A, 0x4E0A12, 0xC9A13A
TROUSER, SHOE, SHOE_HI = 0x141217, 0x09090B, 0x2C2C32

skin = Canvas(64, 64)
hair = lambda x, y: rgb(HAIR, jitter=4)


def face(x, y):
    if y == 0 or (y == 1 and x not in (2, 5)) or (y == 2 and x in (0, 7)):
        return rgb(HAIR, jitter=3)  # hairline with a widow's peak
    if y == 3 and x in (1, 2, 5, 6):
        return rgb(BROW)
    if y == 4 and x in (1, 2, 5, 6):
        return rgb(EYE_HOT if x in (2, 5) else EYE)
    if y == 5 and x in (3, 4):
        return rgb(SKIN_SHADE)
    if y == 6 and 2 <= x <= 5:
        return rgb(LIP)
    if y == 7:
        return rgb(FANG) if x in (2, 5) else rgb(SKIN_SHADE)
    return rgb(SKIN_SHADE if x in (0, 7) else SKIN, jitter=2)


def head_side(back_cols):
    return lambda x, y: rgb(HAIR, jitter=3) if y <= 2 or x in back_cols else rgb(SKIN_SHADE if y > 5 else SKIN, jitter=2)


skin.cube(0, 0, 8, 8, 8, top=hair, bottom=lambda x, y: rgb(SKIN_SHADE), front=face, back=hair,
          right=head_side((0, 1, 2)), left=head_side((5, 6, 7)))


def coat_front(x, y):
    if y >= 10:
        return rgb(COAT_DARK, jitter=2)
    if x in (0, 1, 6, 7):
        return rgb(COAT, jitter=2)
    if y == 0 or (y <= 2 and x in (2, 5)):
        return rgb(SHIRT)
    if y <= 2:
        return rgb(VEST if y == 1 else VEST_DARK)  # cravat
    if x in (2, 5) and y <= 6:
        return rgb(COAT_HI)  # lapels
    if x == 4 and y in (4, 6, 8):
        return rgb(GOLD)
    return rgb(VEST if x in (3, 4) or y > 6 else VEST_DARK, jitter=3)


coat = lambda x, y: rgb(COAT_HI if x == 0 else COAT, jitter=2)
skin.cube(16, 16, 8, 12, 4, top=coat, bottom=lambda x, y: rgb(COAT_DARK), front=coat_front,
          side=lambda x, y: rgb(COAT, jitter=2), back=lambda x, y: rgb(COAT_DARK if x in (3, 4) and y > 6 else COAT, jitter=2))


def sleeve(x, y):
    if y == 9:
        return rgb(SHIRT)
    if y >= 10:
        return rgb(SKIN_SHADE if y == 11 else SKIN, jitter=2)
    return rgb(COAT_HI if y == 0 else COAT, jitter=2)


for u, v in [(40, 16), (32, 48)]:
    skin.cube(u, v, 4, 12, 4, top=coat, bottom=lambda x, y: rgb(SKIN_SHADE), front=sleeve, side=sleeve)


def trouser(x, y):
    if y >= 10:
        return rgb(SHOE_HI if y == 10 and x in (1, 2) else SHOE)
    return rgb(TROUSER, jitter=2)


for u, v in [(0, 16), (16, 48)]:
    skin.cube(u, v, 4, 12, 4, top=lambda x, y: rgb(TROUSER), bottom=lambda x, y: rgb(SHOE), front=trouser, side=trouser)
skin.save("entity/vampire/vampire.png")

eyes = Canvas(64, 64)
for x, hot in [(9, False), (10, True), (13, True), (14, False)]:
    eyes.px[12 * 64 + x] = rgb(0xFF5050 if hot else 0xE01818)
eyes.save("entity/vampire/vampire_eyes.png")

# ---------------------------------------------------------------------------------------------- cloak
# All red satin: a deep crimson outside, a brighter scarlet lining, both with soft vertical sheen.
OUTER, OUTER_SHEEN, OUTER_FOLD = 0x7A0B17, 0x9A1420, 0x56060F
LINING, LINING_SHEEN, LINING_FOLD = 0xB3141F, 0xD8323A, 0x86101A
CLASP_GOLD, CLASP_GOLD_HI, STONE, STONE_HI = 0xC9A13A, 0xF0D27A, 0xD0102A, 0xFF6A78
cloak = Canvas(128, 64)


def satin(base, sheen, fold, period=5):
    def f(x, y):
        k = x % period
        return rgb(sheen if k == 1 else fold if k == period - 1 else base, jitter=2)
    return f


outer = satin(OUTER, OUTER_SHEEN, OUTER_FOLD)
lining = satin(LINING, LINING_SHEEN, LINING_FOLD, 4)
edge = lambda x, y: rgb(OUTER_FOLD)


def hemmed(f, h):
    """A darker band along the hem."""
    return lambda x, y: rgb(OUTER_FOLD) if y >= h - 1 else f(x, y)


cloak.cube(0, 0, 18, 3, 7, top=satin(OUTER, OUTER_SHEEN, OUTER_FOLD, 6), bottom=lining, front=outer, side=outer)  # mantle
cloak.cube(0, 10, 18, 8, 1, top=edge, bottom=edge, front=lining, back=outer, side=edge)  # back, front face is the lining
cloak.cube(0, 19, 20, 8, 1, top=edge, bottom=edge, front=lining, back=outer, side=edge)
cloak.cube(0, 28, 22, 6, 1, top=edge, bottom=edge, front=hemmed(lining, 6), back=hemmed(outer, 6), side=edge)
# Side panels: the first side region is the outward face (mirroring swaps it for the left panel).
for v, h in [(0, 11), (19, 11)]:
    cloak.cube(64, v, 1, h, 8, top=edge, bottom=edge, front=edge, back=edge, right=outer, left=lining)
# Front panels: the north face looks outward, the south face shows the lining.
for v in [0, 12]:
    cloak.cube(84, v, 5, 11, 1, top=edge, bottom=edge, front=outer, back=lining, side=edge)


cloak.cube(0, 36, 10, 9, 1, top=edge, bottom=edge, side=edge,
           front=satin(LINING, LINING_SHEEN, LINING_FOLD, 3), back=satin(OUTER, OUTER_SHEEN, OUTER_FOLD, 3))
cloak.cube(24, 36, 5, 9, 1, top=edge, bottom=edge, side=edge,
           front=satin(LINING, LINING_SHEEN, LINING_FOLD, 3), back=satin(OUTER, OUTER_SHEEN, OUTER_FOLD, 3))
gold = lambda x, y: rgb(CLASP_GOLD_HI if y == 0 else CLASP_GOLD, jitter=4)
cloak.cube(40, 36, 2, 2, 1, top=gold, bottom=gold, front=gold, side=gold)
cloak.cube(48, 36, 1, 1, 1, top=lambda x, y: rgb(STONE_HI), bottom=lambda x, y: rgb(STONE), front=lambda x, y: rgb(STONE_HI),
           side=lambda x, y: rgb(STONE))
cloak.cube(54, 36, 8, 1, 1, top=gold, bottom=gold, front=gold, side=gold)
cloak.save("entity/vampire/vampire_cloak.png")

# ---------------------------------------------------------------------------------------------- light orb
# Drawn additively (black adds nothing): a hot white core fading out through gold.
orb = Canvas(32, 32)
def orb_px(x, y):
    d = math.hypot(x - 15.5, y - 15.5) / 15.5
    if d >= 1.0:
        return (0, 0, 0, 0)
    k = (1.0 - d) ** 1.8
    core = max(0.0, 1.0 - d * 2.2)
    r, g, b = 255 * min(1, k * 1.4), 235 * min(1, k * 1.3) + 20 * core, 170 * k + 85 * core
    return (int(min(255, r)), int(min(255, g)), int(min(255, b)), int(255 * min(1, k * 1.5)))
orb.rect(0, 0, 32, 32, orb_px)
orb.save("entity/light_orb.png")

# ---------------------------------------------------------------------------------------------- bleeding icon
icon = Canvas(18, 18)
def drop(x, y):
    cx, cy = 8.5, 11.0
    if y >= 11:
        inside = math.hypot(x - cx, y - cy) <= 5.0
    else:
        inside = abs(x - cx) <= (y - 2) * 0.55 and y >= 2
    if not inside:
        return None
    shine = x <= cx - 1.5 and 8 <= y <= 12
    return rgb(0xE85050 if shine and x == int(cx - 2) else 0xB0101A if x < cx + 2 else 0x7A0A12)
icon.rect(0, 0, 18, 18, drop)
icon.save("mob_effect/bleeding.png")

# ---------------------------------------------------------------------------------------------- spawn egg
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
# Same egg outline as the cart's, repainted: a near-black shell lit from the upper left, with blood-red spots.
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
SPOTS = [(6, 5), (9, 4), (10, 8), (5, 10), (8, 12), (11, 11)]
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = rgb(0x07060A)
    elif any(abs(x - sx) + abs(y - sy) <= (1 if k % 2 else 0) for k, (sx, sy) in enumerate(SPOTS)):
        egg.px[i] = rgb(0xB0141F if (x + y) % 3 else 0x7E0C16)
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = rgb(0x3A3046 if light > 5 else 0x281F31 if light > 1 else 0x1B1522 if light > -4 else 0x110D16)
egg.save("item/vampire_spawn_egg.png")

# ---------------------------------------------------------------------------------------------- models
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


write(os.path.join(MODELS, "vampire_spawn_egg.json"), OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/vampire_spawn_egg"})]))
write(os.path.join(ITEMS, "vampire_spawn_egg.json"), {"model": {"type": "minecraft:model", "model": "mymod:item/vampire_spawn_egg"}})
print("done")
