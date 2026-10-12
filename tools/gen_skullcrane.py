"""Paints the Skullcrane (its entity texture and the glow layer for its lenses and antigrav emitter, UVs matching
client/skullcrane/SkullcraneModel), its spawn egg, the Servo Arm and Optic Lens items, and writes their item models and
definitions. On the data side it writes the Skullcrane's loot table, the recipes that use its drops (with recipe-book
advancements), and adds it to the mymod:holy_vulnerable and minecraft:undead entity tags.

Usage: python3 tools/gen_skullcrane.py src/main/resources/assets/mymod"""
import json, math, os, random, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
MC_DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "minecraft"))
rnd = random.Random(31)


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
BONE = (226, 216, 190)
BONE_LIGHT = (240, 234, 214)
BONE_SHADE = (190, 178, 148)
BONE_STAIN = (172, 156, 120)
CRACK = (92, 80, 62)
SOCKET = (22, 16, 14)
TOOTH = (236, 228, 200)
GUM = (120, 100, 78)
METAL = (112, 118, 126)
METAL_LIGHT = (160, 168, 176)
METAL_DARK = (62, 66, 74)
BOLT = (196, 200, 204)
BRASS = (186, 146, 70)
BRASS_DARK = (126, 94, 40)
RUBBER = (30, 30, 34)
LENS = (214, 26, 30)
LENS_HOT = (255, 140, 110)
LENS_RIM = (70, 20, 20)
EMITTER = (110, 220, 255)
EMITTER_HOT = (220, 250, 255)

tex = Canvas(64, 64)
glow = Canvas(64, 64)


def bone(shade=1.0, stain=0.08):
    """Old bone: pale, mottled, with brownish stains."""
    def f(x, y):
        n = rnd.random()
        if n < stain:
            return col(BONE_STAIN, shade, 5)
        if n < stain + 0.12:
            return col(BONE_LIGHT, shade, 3)
        if n < stain + 0.24:
            return col(BONE_SHADE, shade, 4)
        return col(BONE, shade, 5)
    return f


def metal(base=METAL, shade=1.0, bolts=False):
    def f(x, y):
        if bolts and (x in (0, max(0, f.w - 1))) and (y in (0, max(0, f.h - 1))):
            return col(BOLT, shade, 3)
        n = rnd.random()
        if n < 0.12:
            return col(mix(base, METAL_LIGHT, 0.5), shade, 4)
        if n < 0.24:
            return col(mix(base, METAL_DARK, 0.5), shade, 4)
        return col(base, shade, 6)
    f.w, f.h = 99, 99
    return f


def paint_cube(canvas, u, v, w, h, d, fn, front=None, top=None, bottom=None, back=None):
    shades = {"top": 1.08, "bottom": 0.78, "front": 1.0, "back": 0.9, "right": 0.94, "left": 0.94}
    special = {"front": front, "top": top, "bottom": bottom, "back": back}
    for name, rect in faces(u, v, w, h, d).items():
        f = special.get(name) or fn(shades[name])
        canvas.rect(*rect, f)


def crack(u, v, w, h, n, length=(3, 7)):
    """Hairline cracks wandering across a face."""
    for _ in range(n):
        x, y = rnd.randrange(w), rnd.randrange(h)
        for _ in range(rnd.randint(*length)):
            if 0 <= x < w and 0 <= y < h:
                tex.set(u + x, v + y, col(CRACK, 1.0, 6))
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((0, 1, 1))


# Cranium, with the face on the front: sockets, the nose hole, cracks, and a bolted plate scar.
def face(x, y):
    # 10 wide, 8 tall: sockets on rows 4-7 in columns 1-3 and 6-8, the nose hole between them below.
    for cx in (2.5, 7.5):
        dx, dy = (x + 0.5 - cx) / 1.6, (y + 0.5 - 5.6) / 1.6
        r = dx * dx + dy * dy
        if r < 0.8:
            return col(SOCKET, 1.0, 3)
        if r < 1.3:
            return col(BONE_SHADE, 0.75, 4)
    if x in (4, 5) and y >= 6:
        return col(SOCKET, 1.0, 3)
    if x in (4, 5) and y == 5:
        return col(BONE_SHADE, 0.75, 3)
    return bone(1.0, 0.05)(x, y)


paint_cube(tex, 0, 0, 10, 8, 10, lambda s: bone(s), front=face)
for name, rect in faces(0, 0, 10, 8, 10).items():
    if name != "bottom":
        crack(*rect, 2 if name in ("top", "left", "right") else 1)
# A jagged crack running from the crown down the forehead.
fu, fv, _, _ = faces(0, 0, 10, 8, 10)["front"]
for x, y in ((6, 0), (6, 1), (5, 2), (5, 3)):
    tex.set(fu + x, fv + y, col(CRACK))
paint_cube(tex, 40, 0, 8, 6, 1, lambda s: bone(s * 0.95))
paint_cube(tex, 40, 7, 10, 1.5, 1, lambda s: bone(s * 1.04, 0.03))

# Upper jaw: a row of teeth along the front with dark gaps, bone round the rest.
def teeth(x, y):
    if y == 0:
        return col(GUM, 1.0, 4)
    return col(SOCKET) if x % 2 == 1 and rnd.random() < 0.5 else col(TOOTH, 1.0, 5)


paint_cube(tex, 0, 18, 8, 1.5, 5, lambda s: bone(s * 0.95), front=teeth)

# Optic lenses: red with a hot centre and a dark rim; the same on the glow layer.
def lens(c):
    def f(x, y):
        if (x, y) == (1, 1):
            return col(LENS_HOT)
        if x == 1 or y == 1:
            return col(LENS, 1.1)
        return col(LENS if c is tex else LENS, 0.85)
    return f


for canvas in (tex, glow):
    rect = faces(26, 18, 2.5, 2.5, 0.5)
    for name, (ru, rv, rw, rh) in rect.items():
        if name == "front":
            canvas.rect(ru, rv, rw, rh, lens(canvas))
        elif canvas is tex:
            canvas.rect(ru, rv, rw, rh, lambda x, y: col(LENS_RIM, 1.0, 4))

# Bolted plates on the crown and the temple.
paint_cube(tex, 40, 10, 5, 0.6, 6, lambda s: metal(METAL, s))
fu, fv, fw, fh = faces(40, 10, 5, 0.6, 6)["top"]
for x, y in ((0, 0), (fw - 1, 0), (0, fh - 1), (fw - 1, fh - 1)):
    tex.set(fu + x, fv + y, col(BOLT))
paint_cube(tex, 34, 18, 0.6, 4, 5, lambda s: metal(METAL, s))
for name in ("right", "left"):
    fu, fv, fw, fh = faces(34, 18, 0.6, 4, 5)[name]
    for x, y in ((0, 0), (fw - 1, 0), (0, fh - 1), (fw - 1, fh - 1)):
        tex.set(fu + x, fv + y, col(BOLT))

# Mandible: lower teeth along the top front edge, bone below.
def lower_teeth(x, y):
    if y == 0:
        return col(SOCKET) if x % 2 == 0 and rnd.random() < 0.4 else col(TOOTH, 1.0, 5)
    return bone(0.9)(x, y)


paint_cube(tex, 0, 25, 7, 1.5, 5, lambda s: bone(s * 0.95), front=lower_teeth)
crack(*faces(0, 25, 7, 1.5, 5)["bottom"], 1, (2, 4))
# Hinge pistons: brass rods with dark ends.
paint_cube(tex, 24, 25, 0.8, 2.2, 0.8, lambda s: (lambda x, y: col(BRASS_DARK if y == 2 else BRASS, s, 6)))

# Antigrav emitter: a dark housing with a vented rim, and a glowing plate underneath.
paint_cube(tex, 46, 18, 4, 1.5, 4, lambda s: (lambda x, y: col(METAL_DARK if (x + y) % 2 else METAL, s, 5)))
def emitter(c):
    def f(x, y):
        if (x, y) == (1, 1):
            return col(EMITTER_HOT)
        return col(EMITTER, 0.9 if (x + y) % 2 else 1.0, 6)
    return f


paint_cube(tex, 46, 24, 3, 0.5, 3, lambda s: (lambda x, y: col(METAL_DARK, s, 4)), bottom=emitter(tex), top=emitter(tex))
for name in ("bottom", "top"):
    glow.rect(*faces(46, 24, 3, 0.5, 3)[name], emitter(glow))

# Cables: black rubber with a red band near the end.
paint_cube(tex, 58, 0, 0.8, 3.5, 0.8, lambda s: (lambda x, y: col((150, 30, 30) if y == 2 else RUBBER, s, 4)))

# Arms: gunmetal servo housings with a bright bolt, struts with grip rings, brass pistons alongside.
for s in range(3):
    paint_cube(tex, s * 8, 32, 2, 2, 2, lambda sh: (lambda x, y: col(BOLT if (x, y) == (0, 0) else METAL_DARK, sh, 5)))
    paint_cube(tex, s * 8, 36, 1.2, 4, 1.2, lambda sh: (lambda x, y: col(METAL_LIGHT if y % 3 == 2 else METAL, sh, 6)))
paint_cube(tex, 24, 32, 0.6, 3, 0.6, lambda s: (lambda x, y: col(BRASS if y < 2 else (200, 200, 205), s, 5)))
paint_cube(tex, 28, 32, 2, 1.5, 2, lambda s: (lambda x, y: col(METAL_DARK, s, 4)))
paint_cube(tex, 36, 32, 0.7, 1.6, 0.7, lambda s: (lambda x, y: col(METAL_LIGHT, s, 6)))
paint_cube(tex, 40, 32, 0.6, 1.3, 0.6, lambda s: (lambda x, y: col(METAL_DARK if y == 1 else METAL_LIGHT, s, 4)))

tex.save("entity/skullcrane/skullcrane.png")
glow.save("entity/skullcrane/skullcrane_glow.png")

# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                c.set(x, y, palette[ch])
    return c


# A servo arm: three struts with joints, a brass piston, and a claw at the top right.
SERVO_ARM = ["................",
             "...........k.k..",
             "..........k...k.",
             "...........jjj..",
             "..........jJJj..",
             ".........ssj....",
             "........ss......",
             ".......jjp......",
             "......jJJjp.....",
             ".....ss..p......",
             "....ss..........",
             "...jj...........",
             "..jJJj..........",
             "..jjj...........",
             ".bb.............",
             "................"]
sprite(SERVO_ARM, {"s": col(METAL), "j": col(METAL_DARK), "J": col(BOLT), "p": col(BRASS), "k": col(METAL_LIGHT),
                   "b": col(BRASS_DARK)}).save("item/servo_arm.png")

# An optic lens: a red disc in a steel ring, with a glint.
lens_item = Canvas(16, 16)
for y in range(16):
    for x in range(16):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        if r < 4.6:
            light = (8 - x) + (8 - y)
            lens_item.set(x, y, col(LENS_HOT if r < 1.2 else LENS, 1.0 + 0.04 * light, 4))
        elif r < 6.2:
            lens_item.set(x, y, col(METAL_LIGHT if (x + y) % 5 == 0 else METAL, 1.0, 5))
        elif r < 7.0:
            lens_item.set(x, y, col(METAL_DARK))
for x, y in ((6, 5), (5, 6), (6, 6)):
    lens_item.set(x, y, col((255, 230, 220)))
lens_item.save("item/optic_lens.png")

# Spawn egg: the shopping cart egg's outline, repainted bone with red lens spots and a steel band.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
SPOTS = [(6, 6), (10, 6)]
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = col((70, 64, 56))
    elif any(abs(x - sx) + abs(y - sy) <= 1 for sx, sy in SPOTS):
        egg.px[i] = col(LENS if (x, y) not in SPOTS else LENS_HOT)
    elif y == round(cy) + 2:
        egg.px[i] = col(METAL)
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = col(BONE_LIGHT if light > 5 else BONE if light > 1 else BONE_SHADE if light > -4 else BONE_STAIN)
egg.save("item/skullcrane_spawn_egg.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


for name in ("skullcrane_spawn_egg", "servo_arm", "optic_lens"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(DATA, "loot_table/entities/skullcrane.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:servo_arm", 0, 2), drop("mymod:optic_lens", 0, 1), drop("minecraft:bone", 0, 2),
               drop("mymod:tech_scrap", 0, 1, 0.5)]),
    ("random_sequence", "mymod:entities/skullcrane")]))


def advancement(name, ingredient):
    write(os.path.join(DATA, "advancement/recipes/misc", name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shapeless(name, ingredients, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, ingredients[0])


def shaped(name, pattern, key, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, next(iter(key.values())))


shapeless("tech_scrap_from_servo_arm", ["mymod:servo_arm"], "mymod:tech_scrap", 2)
shapeless("computer_chip_from_optic_lens", ["mymod:optic_lens", "mymod:tech_scrap"], "mymod:computer_chip", 1)
shaped("spyglass_from_optic_lens", ["L", "C", "C"], OrderedDict([("L", "mymod:optic_lens"), ("C", "minecraft:copper_ingot")]),
       "minecraft:spyglass", 1, "equipment")


def tag_add(path, entry):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), "mymod:skullcrane")
tag_add(os.path.join(MC_DATA, "tags/entity_type/undead.json"), "mymod:skullcrane")
print("done")
