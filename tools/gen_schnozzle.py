"""Paints the Schnozzle (its entity texture, UVs matching client/schnozzle/SchnozzleModel), its spawn egg, the Giant
Booger and Nose Hair items, and writes their item models and definitions. On the data side it writes the Schnozzle's
loot table, the booger and nose hair recipes (with recipe-book advancements) and the tomyen:holy_vulnerable entity tag.

Usage: python3 tools/gen_schnozzle.py src/main/resources/assets/tomyen"""
import json, math, os, random, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
rnd = random.Random(23)


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


def clamp(v): return max(0, min(255, int(round(v))))


def mix(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def col(c, shade=1.0, jitter=0, a=255):
    d = rnd.uniform(-jitter, jitter) if jitter else 0
    return (clamp(c[0] * shade + d), clamp(c[1] * shade + d), clamp(c[2] * shade + d), a)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, u, v, w, h, f):
        """f(x, y) -> colour, with x, y relative to the rectangle; None leaves it as it is."""
        for y in range(h):
            for x in range(w):
                c = f(x, y)
                if c is not None:
                    self.set(u + x, v + y, c)

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


def faces(u, v, w, h, d):
    """A model cube's faces in the standard layout, as integer rectangles. 'top' and 'bottom' are as seen in the world
    (the model is upside down); in both, the front edge is the last row."""
    w, h, d = math.ceil(w), math.ceil(h), math.ceil(d)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


# ---------------------------------------------------------------------------------------------- palette
SKIN = (233, 178, 152)
SKIN_LIGHT = (246, 199, 175)
SKIN_SHADE = (206, 146, 122)
ROSY = (226, 132, 120)
PORE = (192, 128, 106)
VEIN_BLUE = (150, 120, 170)
VEIN_RED = (198, 96, 98)
MEAT = (156, 28, 36)
MEAT_HI = (206, 54, 58)
MEAT_DARK = (96, 14, 22)
FAT = (232, 214, 160)
CARTILAGE = (238, 228, 218)
HOLE = (34, 8, 10)
HOLE_RIM = (96, 30, 32)
HAIR = (38, 28, 22)
HAIR_HI = (74, 58, 44)
BOOGER = (128, 168, 62)
BOOGER_HI = (186, 214, 104)
BOOGER_DARK = (80, 112, 36)

tex = Canvas(128, 128)


def skin_face(rosy=0.0, shade=1.0, pores=0.07):
    """Skin with pores, blotches of redness, and lighter and darker mottling."""
    def f(x, y):
        base = mix(SKIN, ROSY, rosy)
        n = rnd.random()
        if n < pores:
            return col(PORE, shade, 4)
        if n < pores + 0.05:
            return col(mix(base, SKIN_LIGHT, 0.6), shade, 3)
        if n < pores + 0.10:
            return col(mix(base, SKIN_SHADE, 0.7), shade, 3)
        return col(base, shade, 5)
    return f


def paint_cube(u, v, w, h, d, top=None, bottom=None, front=None, back=None, side=None, rosy=0.0):
    sides = {"top": (top, 1.06), "bottom": (bottom, 0.82), "front": (front, 1.0), "back": (back, 0.94),
             "right": (side, 0.95), "left": (side, 0.95)}
    for name, (fn, shade) in sides.items():
        rect = faces(u, v, w, h, d)[name]
        tex.rect(*rect, fn or skin_face(rosy, shade))


def veins(u, v, w, h, count, colour=None):
    """A few thin veins wandering across a face."""
    for _ in range(count):
        c = colour or (VEIN_BLUE if rnd.random() < 0.6 else VEIN_RED)
        x, y = rnd.randrange(w), rnd.randrange(h)
        dx = rnd.choice((-1, 1))
        for _ in range(rnd.randint(5, 12)):
            if 0 <= x < w and 0 <= y < h:
                tex.set(u + x, v + y, col(mix(tex.get(u + x, v + y)[:3], c, 0.55)))
            if rnd.random() < 0.55:
                x += dx
            else:
                y += rnd.choice((-1, 1))
            if rnd.random() < 0.15:
                dx = -dx


def skin_veins(u, v, w, h, d, n=1):
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        if fw >= 4 and fh >= 4:
            veins(fu, fv, fw, fh, n)


def meat(x, y):
    n = rnd.random()
    if n < 0.05:
        return col(FAT, 1.0, 6)
    if n < 0.08:
        return col(CARTILAGE, 1.0, 4)
    if n < 0.40:
        return col(MEAT_DARK, 1.0, 6)
    if n < 0.65:
        return col(MEAT_HI, 1.0, 8)
    return col(MEAT, 1.0, 8)


def torn_side(depth):
    """Skin with a ragged band of raw flesh along the top edge, where it was torn off the face."""
    edge = [rnd.randint(0, depth) for _ in range(64)]
    def f(x, y):
        if y < edge[x % 64]:
            return meat(x, y)
        if y == edge[x % 64]:
            return col(MEAT_DARK, 1.0, 4)
        return skin_face(0.1)(x, y)
    return f


# Base slab, with the nostril holes in its underside (front edge last row).
def base_bottom(x, y, w=14, d=12):
    for cx in (3.5, 10.5):
        dx, dy = (x + 0.5 - cx) / 2.6, (y + 0.5 - 6.0) / 3.8
        r = dx * dx + dy * dy
        if r < 0.55:
            return col(HOLE, 1.0, 3)
        if r < 0.85:
            return col(HOLE_RIM, 1.0, 4)
        if r < 1.15:
            return col(ROSY, 0.85, 4)
    return skin_face(0.25, 0.85)(x, y)


paint_cube(0, 0, 14, 3, 12, bottom=base_bottom, rosy=0.15)
paint_cube(0, 15, 12, 7, 13, rosy=0.2)
skin_veins(0, 15, 12, 7, 13, 2)
paint_cube(0, 35, 10, 7, 11, rosy=0.05)
skin_veins(0, 35, 10, 7, 11, 2)
paint_cube(0, 53, 8, 7, 9, top=meat)
skin_veins(0, 53, 8, 7, 9, 1)

# The tip: rosy and a little shiny.
def tip_front(x, y):
    if (x - 3.5) ** 2 + (y - 1.5) ** 2 < 2.2:
        return col(mix(ROSY, (255, 230, 220), 0.6), 1.0, 3)
    return skin_face(0.65)(x, y)


paint_cube(56, 0, 11, 7, 3, front=tip_front, rosy=0.55)
paint_cube(56, 10, 8, 5, 1, front=tip_front, rosy=0.7)
veins(56 + 3, 3, 11, 7, 2, VEIN_RED)

# The stump: raw meat on top, a ragged torn edge round the sides.
paint_cube(56, 16, 9, 2, 8, top=meat, bottom=meat, front=torn_side(1), back=torn_side(1), side=torn_side(1))
tex.rect(92, 16, 36, 14, meat)

# Ridges smoothing the bridge.
paint_cube(56, 26, 7, 7, 1, rosy=0.05)
paint_cube(74, 26, 6, 7, 1)

# Nostril wings: flushed toward the bottom edge.
def wing(x, y):
    return skin_face(min(1.0, 0.1 + y / 14.0))(x, y)


paint_cube(0, 69, 3, 9, 10, front=wing, back=wing, side=wing, rosy=0.3)
veins(10, 79, 3, 9, 1, VEIN_BLUE)

# Nostril pads: dark holes with a flesh rim.
def nostril_hole(x, y):
    dx, dy = (x + 0.5 - 2.0) / 2.0, (y + 0.5 - 3.5) / 3.5
    r = dx * dx + dy * dy
    return col(HOLE, 1.0, 3) if r < 0.6 else col(HOLE_RIM, 1.0, 4) if r < 0.95 else col(ROSY, 0.8, 4)


paint_cube(30, 69, 4, 1, 7, bottom=nostril_hole, top=nostril_hole, front=lambda x, y: col(HOLE_RIM),
           back=lambda x, y: col(HOLE_RIM), side=lambda x, y: col(HOLE_RIM))

# Nose hairs.
tex.rect(56, 69, 8, 6, lambda x, y: col(HAIR_HI if (x + y) % 3 == 0 else HAIR, 1.0, 4))

# Tentacles: six tapering segments, pink at the root to bruised purple at the tip, ringed, with pale suckers
# down the front.
SEGMENTS = [(3.0, 4.5), (2.6, 4.5), (2.2, 4.0), (1.8, 4.0), (1.4, 3.5), (1.0, 3.5)]
for i, (w, l) in enumerate(SEGMENTS):
    u, v = i * 16, 96
    base = mix((228, 168, 148), (168, 92, 124), i / 5.0)
    tex.rect(u, v, 16, 10, lambda x, y: col(base, 0.88 if y % 2 else 1.0, 5))
    fu, fv, fw, fh = faces(u, v, w, l, w)["front"]
    for y in range(fv, fv + fh):
        for x in range(fu, fu + fw):
            if (y - fv) % 2 == 1 and (x - fu) == fw // 2:
                tex.set(x, y, col((246, 214, 204), 1.0, 3))
    fu, fv, fw, fh = faces(u, v, w, l, w)["top"]
    tex.rect(fu, fv, fw, fh, lambda x, y: col(base, 0.7, 4))

tex.save("entity/schnozzle/schnozzle.png")

# ---------------------------------------------------------------------------------------------- items
booger = Canvas(16, 16)
BLOB = ["................",
        "......####......",
        "....########....",
        "...##########...",
        "..############..",
        "..#############.",
        ".##############.",
        ".##############.",
        ".#############..",
        "..############..",
        "..###########...",
        "...#########....",
        "....#######.....",
        ".....####.......",
        "......##........",
        "................"]
for y, row in enumerate(BLOB):
    for x, ch in enumerate(row):
        if ch != "#":
            continue
        edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16 and BLOB[y + dy][x + dx] == "#")
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        light = (8 - x) + (7 - y)
        if edge:
            c = col(BOOGER_DARK, 0.8)
        elif 3 <= x <= 5 and 3 <= y <= 5 and x + y in (7, 8):
            c = col((236, 246, 196))
        elif light > 4:
            c = col(BOOGER_HI, 1.0, 6)
        elif light > -4:
            c = col(BOOGER, 1.0, 8)
        else:
            c = col(BOOGER_DARK, 1.0, 6)
        booger.set(x, y, c)
# A few dark crusty flecks.
for x, y in ((9, 6), (6, 9), (11, 9), (8, 11)):
    booger.set(x, y, col((92, 84, 40)))
booger.save("item/giant_booger.png")

hair = Canvas(16, 16)
# One long, coarse, wiry nose hair with a curl at the end and the root bulb at the bottom.
HAIR_PATH = [(4, 14), (4, 13), (5, 12), (5, 11), (6, 10), (6, 9), (7, 8), (7, 7), (8, 6), (8, 5), (9, 4), (10, 3),
             (11, 3), (12, 4), (12, 5), (11, 6), (10, 5)]
for i, (x, y) in enumerate(HAIR_PATH):
    hair.set(x, y, col(HAIR_HI if i % 4 == 1 else HAIR))
    if i < 9:
        hair.set(x + 1, y, col(HAIR, 0.8))
hair.set(3, 15, col((236, 222, 200)))
hair.set(4, 15, col((214, 196, 172)))
hair.save("item/nose_hair.png")

# Spawn egg: the shopping cart egg's outline, repainted skin-tone with booger-green spots.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
SPOTS = [(6, 5), (10, 6), (5, 10), (9, 11), (11, 9)]
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = col((120, 64, 52))
    elif any(abs(x - sx) + abs(y - sy) <= (1 if k % 2 else 0) for k, (sx, sy) in enumerate(SPOTS)):
        egg.px[i] = col(BOOGER if (x + y) % 3 else BOOGER_DARK)
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = col(SKIN_LIGHT if light > 5 else SKIN if light > 1 else SKIN_SHADE if light > -4 else ROSY)
egg.save("item/schnozzle_spawn_egg.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


for name in ("schnozzle_spawn_egg", "giant_booger", "nose_hair"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(DATA, "loot_table/entities/schnozzle.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("tomyen:giant_booger", 1, 3), drop("tomyen:nose_hair", 2, 5, 2.0), drop("minecraft:slime_ball", 0, 2)]),
    ("random_sequence", "tomyen:entities/schnozzle")]))


def shapeless(name, ingredient, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", [ingredient]),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    write(os.path.join(DATA, "advancement/recipes/misc", name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


shapeless("string_from_nose_hair", "tomyen:nose_hair", "minecraft:string", 2)
shapeless("slime_ball_from_giant_booger", "tomyen:giant_booger", "minecraft:slime_ball", 2)

write(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), {"values": ["tomyen:vampire", "tomyen:schnozzle", "tomyen:skullcrane"]})
print("done")
