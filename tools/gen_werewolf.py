"""Paints the Werewolf (its entity texture and the glow layer for its eyes, UVs matching client/werewolf/WerewolfModel),
its spawn egg, the Werewolf Pelt and the Feral Lining exoskeleton module made from it, and writes their item models and
definitions. On the data side it writes the werewolf's loot table, the pelt's recipes (with recipe-book advancements)
and adds the werewolf to the mymod:holy_vulnerable entity tag. Its sounds (vanilla wolf and cat sounds, pitched down)
and lang entries are merged into sounds.json and en_us.json.

Everything is flat-shaded from short fixed ramps: dark wolf-grey fur (between vanilla's woods and ashen wolves), a
cream chest and muzzle, amber eyes, and scraps of a plains villager's brown robe still round its waist and thighs so it
reads as the villager it was. No noise, no dithering; a rerun gives the same files.

With --preview DIR it also renders, into DIR, preview_werewolf_texture.png (the texture, enlarged) and
preview_werewolf_model.png (the model drawn from the front, the side and three-quarters, at rest, mid-swipe and
howling), using the same cubes, pivots and UV layout as the Java model.

(--detail front|side|three|back adds a larger single view, preview_werewolf_detail.png; the items go in
preview_werewolf_items.png.)

Usage: python3 tools/gen_werewolf.py src/main/resources/assets/mymod [--preview DIR [--detail VIEW]]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
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
# Fur, light to dark: a dusky grey-brown between vanilla's woods and ashen wolves, darker than either.
FUR = [(150, 139, 125), (122, 111, 99), (98, 89, 80), (76, 68, 62), (54, 48, 45)]
# The cream of a wolf's chest and muzzle, toned down to sit with the fur.
CREAM = [(204, 190, 166), (176, 162, 139)]
NOSE = (34, 29, 29)
SKIN = (70, 56, 53)       # palms and soles
CLAW = (226, 214, 190)
CLAW_SHADE = (178, 164, 142)
EYE = (255, 182, 46)
EYE_HOT = (255, 236, 160)
MOUTH = [(132, 50, 50), (92, 32, 34)]
TOOTH = (236, 230, 212)
INNER_EAR = (140, 98, 90)
# A plains villager's robe (vanilla's type/plains.png), light to dark.
ROBE = [(113, 84, 77), (90, 66, 61), (68, 50, 46), (53, 37, 31)]

TEX_W, TEX_H = 128, 64


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


def band(rows, y, h):
    """Picks from rows (a list of (fraction, colour)) by how far down the face y is: a few flat bands, no gradient."""
    t = (y + 0.5) / h
    for limit, colour in rows:
        if t <= limit:
            return colour
    return rows[-1][1]


# ---------------------------------------------------------------------------------------------- the model's cubes
# Mirrors client/werewolf/WerewolfModel: name -> (texOffs, (w, h, d)). The left limbs and ear reuse the right ones,
# mirrored.
CUBES = OrderedDict([
    ("chest", ((0, 0), (13, 10, 9))),
    ("waist", ((0, 19), (8, 8, 6))),
    ("head", ((44, 0), (8, 7, 7))),
    ("snout", ((74, 0), (4, 3, 6))),
    ("jaw", ((74, 9), (4, 2, 6))),
    ("ear", ((96, 0), (3, 4, 1))),
    ("tail", ((96, 8), (4, 4, 8))),
    ("arm", ((0, 33), (4, 19, 4))),
    ("leg", ((16, 33), (5, 16, 5))),
])


def fur_side(y, h, top=1, mid=2, low=3):
    return band([(0.3, FUR[top]), (0.75, FUR[mid]), (1.0, FUR[low])], y, h)


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    glow = Canvas(TEX_W, TEX_H)

    # Chest: broad shoulders, a dark mane down the back, a cream ruff on the breast, a sawtoothed fringe at the bottom.
    def chest_front(x, y, w, h):
        middle = abs(x - (w - 1) / 2)
        if y == h - 1 and x % 2 == 1:
            return FUR[3]
        # A cream ruff: broad under the chin, tapering to a point down the breast.
        reach = [3, 3, 3, 2, 2, 1, 0][y] if y < 7 else -1
        if middle <= reach:
            return CREAM[0] if middle < reach or reach == 0 else CREAM[1]
        return fur_side(y, h)

    def chest_side(x, y, w, h):
        if y == h - 1 and x % 2 == 0:
            return FUR[4]
        return fur_side(y, h)

    def chest_back(x, y, w, h):
        middle = abs(x - (w - 1) / 2)
        if y == h - 1 and x % 2 == 1:
            return FUR[4]
        return FUR[4] if middle <= 2.0 else FUR[3]

    def chest_top(x, y, w, h):
        # Back edge (first rows) darkest: the mane over the shoulders.
        middle = abs(x - (w - 1) / 2)
        if middle <= 2.0 and y <= h - 3:
            return FUR[3]
        return FUR[1] if y >= h - 2 else FUR[2]

    paint(tex, *CUBES["chest"], {"front": chest_front, "back": chest_back, "right": chest_side, "left": chest_side,
                                 "top": chest_top, "bottom": lambda x, y, w, h: FUR[3]})

    # Waist: lean, a cream belly over the remains of the robe: a dark belt and a torn brown band.
    def waist_band(y, h, base):
        if y <= 1:
            return base
        if y == 2:
            return ROBE[3]
        return ROBE[1] if y < h - 1 else ROBE[2]

    def waist_front(x, y, w, h):
        if y <= 1 and 2 <= x <= w - 3:
            return CREAM[1]
        # Torn: a ragged tongue of robe missing above the belt.
        return waist_band(y, h, FUR[2])

    paint(tex, *CUBES["waist"], {
        "front": waist_front,
        "back": lambda x, y, w, h: waist_band(y, h, FUR[3]),
        "right": lambda x, y, w, h: waist_band(y, h, FUR[2]),
        "left": lambda x, y, w, h: waist_band(y, h, FUR[2]),
        "top": lambda x, y, w, h: FUR[2],
        "bottom": lambda x, y, w, h: ROBE[2]})

    # Head: a dark brow over amber eyes, fur cheeks, a darker crown stripe.
    def head_front(x, y, w, h):
        # Forehead, a heavy dark brow, amber eyes just above the snout (which covers the middle of rows 3 to 5).
        if y == 1 and 1 <= x <= w - 2:
            return FUR[4]
        if y == 2 and x in (1, 2, w - 3, w - 2):
            return EYE_HOT if x in (2, w - 3) else EYE
        if y == 0:
            return FUR[1]
        return FUR[2] if y < 5 else FUR[3]

    def head_side(x, y, w, h):
        return FUR[1] if y == 0 else FUR[2] if y < 5 else FUR[3]

    def head_top(x, y, w, h):
        middle = abs(x - (w - 1) / 2)
        return FUR[3] if middle <= 1.0 and y <= h - 2 else FUR[1]

    paint(tex, *CUBES["head"], {"front": head_front, "back": lambda x, y, w, h: FUR[3], "right": head_side, "left": head_side,
                                "top": head_top, "bottom": lambda x, y, w, h: CREAM[1]})
    hu, hv = faces(*CUBES["head"][0], *CUBES["head"][1])["front"][:2]
    for x in (1, 2, 5, 6):
        glow.set(hu + x, hv + 2, rgba(EYE_HOT if x in (2, 5) else EYE))

    # Snout: a black nose on a cream muzzle, the roof of the mouth underneath with a row of teeth at the front.
    def snout_front(x, y, w, h):
        if y == 0:
            return NOSE if 1 <= x <= 2 else FUR[1]
        return CREAM[0] if y == 1 else CREAM[1]

    def snout_side(front_col):
        # A dark lip line along the bottom with a fang at the front; the red of the mouth only shows when it opens.
        def f(x, y, w, h):
            if y == h - 1:
                return TOOTH if x == front_col(w) else FUR[3]
            return FUR[1] if y == 0 else CREAM[1]
        return f

    snout_right, snout_left = snout_side(lambda w: w - 1), snout_side(lambda w: 0)

    def snout_top(x, y, w, h):
        return FUR[1] if y < h - 1 else FUR[0]

    def snout_bottom(x, y, w, h):
        return TOOTH if y == h - 1 and x % 3 == 0 else MOUTH[0]

    paint(tex, *CUBES["snout"], {"front": snout_front, "back": lambda x, y, w, h: FUR[3], "right": snout_right, "left": snout_left,
                                 "top": snout_top, "bottom": snout_bottom})

    # Jaw: teeth along the top front, a cream chin, the tongue-red inside (seen when it opens).
    def jaw_front(x, y, w, h):
        return (TOOTH if x % 3 == 0 else CREAM[1]) if y == 0 else CREAM[1]

    def jaw_side(front_col):
        return lambda x, y, w, h: (TOOTH if x == front_col(w) else CREAM[1]) if y == 0 else FUR[2]

    paint(tex, *CUBES["jaw"], {"front": jaw_front, "back": lambda x, y, w, h: FUR[3],
                               "right": jaw_side(lambda w: w - 1), "left": jaw_side(lambda w: 0),
                               "top": lambda x, y, w, h: MOUTH[0], "bottom": lambda x, y, w, h: CREAM[1]})

    # Ears: pointed, with a dark tip and a skin-pink inside. Clear where the point narrows.
    def ear_shape(x, y, w):
        return y > 0 or x == w // 2

    def ear_front(x, y, w, h):
        if not ear_shape(x, y, w):
            return None
        if y == 0:
            return FUR[4]
        return INNER_EAR if x == w // 2 and y >= 2 else FUR[2]

    def ear_back(x, y, w, h):
        if not ear_shape(x, y, w):
            return None
        return FUR[4] if y == 0 else FUR[3]

    paint(tex, *CUBES["ear"], {"front": ear_front, "back": ear_back,
                               "right": lambda x, y, w, h: None if y == 0 else FUR[3],
                               "left": lambda x, y, w, h: None if y == 0 else FUR[3],
                               "top": lambda x, y, w, h: FUR[4] if x == w // 2 else None,
                               "bottom": lambda x, y, w, h: FUR[3]})

    # Tail: bushy, lighter along the top, cream underneath, a pale tip.
    def tail_side_right(x, y, w, h):
        tip = x >= w - 3
        return (FUR[1] if tip else FUR[2]) if y < h - 1 else (CREAM[1] if tip else FUR[3])

    def tail_side_left(x, y, w, h):
        tip = x <= 2
        return (FUR[1] if tip else FUR[2]) if y < h - 1 else (CREAM[1] if tip else FUR[3])

    paint(tex, *CUBES["tail"], {
        # The tail runs back along +z: its "front" face is the root (hidden in the body), its "back" the tip.
        "front": lambda x, y, w, h: FUR[3],
        "back": lambda x, y, w, h: CREAM[0] if 1 <= x <= w - 2 and 1 <= y <= h - 2 else FUR[1],
        "right": tail_side_left, "left": tail_side_right,
        "top": lambda x, y, w, h: FUR[1] if y <= 2 else FUR[2] if y < h - 1 else FUR[3],
        "bottom": lambda x, y, w, h: CREAM[1] if y <= 2 else FUR[3]})

    # Arms: long and ropy, darkening down to big hands with pale claws.
    def arm_face(claws):
        def f(x, y, w, h):
            if y >= h - 3:
                if y == h - 1 and claws and x % 2 == 0:
                    return CLAW
                return FUR[4]
            return band([(0.3, FUR[1]), (0.6, FUR[2]), (1.0, FUR[3])], y, h - 3)
        return f

    def arm_bottom(x, y, w, h):
        # Palm, with claw tips along the front edge (the last row).
        if y == h - 1:
            return CLAW_SHADE if x % 2 == 0 else SKIN
        return SKIN

    paint(tex, *CUBES["arm"], {"front": arm_face(True), "back": arm_face(False), "right": arm_face(False),
                               "left": arm_face(False), "top": lambda x, y, w, h: FUR[1], "bottom": arm_bottom})

    # Legs: the torn robe still over the thighs, with a ragged hem, fur shins, dark feet with claws at the front.
    def leg_face(claws, phase):
        def f(x, y, w, h):
            hem = 5 if (x + phase) % 2 == 0 else 6
            if y < hem:
                return ROBE[1] if y < hem - 1 else ROBE[2]
            if y >= h - 3:
                if y == h - 1 and claws and x % 2 == 0:
                    return CLAW
                return FUR[4]
            return FUR[2] if y < h - 6 else FUR[3]
        return f

    paint(tex, *CUBES["leg"], {"front": leg_face(True, 0), "back": leg_face(False, 1), "right": leg_face(False, 1),
                               "left": leg_face(False, 0), "top": lambda x, y, w, h: ROBE[2],
                               "bottom": lambda x, y, w, h: CLAW_SHADE if y == h - 1 and x % 2 == 0 else SKIN})

    tex.save("entity/werewolf/werewolf.png")
    glow.save("entity/werewolf/werewolf_eyes.png")
    return tex


TEXTURE = paint_entity()


# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# Werewolf Pelt: a stretched hide, legs splayed and a bushy tail, dark grey fur with a cream belly stripe.
PELT = ["................",
        "..kk........kk..",
        ".kddk......kddk.",
        ".kdmmkkkkkkmmdk.",
        "..kmmllllllmmk..",
        "...kmllccllmk...",
        "...kmlcccclmk...",
        "...kmlcccclmk...",
        "...kmlcccclmk...",
        "...kmllccllmk...",
        "..kmmllllllmmk..",
        ".kdmmkmmmmkmmdk.",
        ".kddk.kmmk.kddk.",
        "..kk..kmmk..kk..",
        ".......kk.......",
        "................"]
sprite(PELT, {"k": FUR[4], "d": FUR[3], "m": FUR[2], "l": FUR[1], "c": CREAM[1]}).save("item/werewolf_pelt.png")

# Feral Lining Module: the exoskeleton modules' gunmetal cartridge (tools/gen_exo.py), with a paw print in pelt brown.
MODULE_BODY = ["................",
               "................",
               "..oooooooooooo..",
               "..offffffffffo..",
               "..ofkkkkkkkkfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofkkkkkkkkfo..",
               "..offffffffffo..",
               "..oyoyoyoyoyoo..",
               "...y.y.y.y.y....",
               "................",
               "................"]
PAW = [".a..a.", "a....a", "..aa..", ".aaaa.", ".aaaa."]
rows = [list(r) for r in MODULE_BODY]
for sy, line in enumerate(PAW):
    for sx, ch in enumerate(line):
        rows[5 + sy][5 + sx] = "a" if ch == "a" else "k"


def hexrgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


sprite(["".join(r) for r in rows], {"o": hexrgb(0x1E2228), "f": hexrgb(0x5A616B), "k": hexrgb(0x0E1014), "y": hexrgb(0xC9A23A),
                                    "a": hexrgb(0xB07A4A)}).save("item/feral_lining_module.png")

# Spawn egg: the shared egg shape (as the other mod eggs use), dark fur with a cream belly band and amber eyes.
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
        egg.px[i] = rgba(FUR[4])
    elif (x, y) in ((6, 6), (10, 6)):
        egg.px[i] = rgba(EYE)
    elif y == round(cy) + 3:
        egg.px[i] = rgba(ROBE[1])
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = rgba(FUR[1] if light > 5 else FUR[2] if light > 0 else FUR[3])
egg.save("item/werewolf_spawn_egg.png")

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


for name in ("werewolf_pelt", "feral_lining_module", "werewolf_spawn_egg"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(DATA, "loot_table/entities/werewolf.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:werewolf_pelt", 1, 2), drop("minecraft:bone", 0, 2)]),
    ("random_sequence", "mymod:entities/werewolf")]))


def advancement(name, ingredient, category="misc"):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
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
    advancement(name, ingredients[0], category)


# A pelt tans down to leather (and so into everything leather goes into: the hazmat suit, the pig flight harness, the
# servo arm backpack...); two of them line an exoskeleton, as the other modules are made.
shapeless("leather_from_werewolf_pelt", ["mymod:werewolf_pelt"], "minecraft:leather", 2)
shapeless("feral_lining_module", ["mymod:werewolf_pelt", "mymod:werewolf_pelt", "minecraft:leather", "mymod:computer_chip",
                                  "mymod:tech_scrap"], "mymod:feral_lining_module", 1, "equipment")


def tag_add(path, entry):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


# Silver and holy light bite it (it isn't undead: the sun doesn't burn it).
tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), "mymod:werewolf")


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


# Vanilla sounds, pitched down: there's no wolf howl in the game any more, so a big wolf's whine, slowed right down,
# stands in, carrying four times as far as most sounds.
SOUNDS = OrderedDict([
    ("werewolf_growl", OrderedDict([("subtitle", "subtitles.mymod.werewolf_growl"),
                                    ("sounds", vanilla(["mob/wolf/angry/growl%d" % i for i in (1, 2, 3)], 0.6))])),
    ("werewolf_howl", OrderedDict([("subtitle", "subtitles.mymod.werewolf_howl"),
                                   ("sounds", vanilla(["mob/wolf/big/whine", "mob/wolf/classic/whine"], 0.5, attenuation_distance=64))])),
    ("werewolf_hurt", OrderedDict([("subtitle", "subtitles.mymod.werewolf_hurt"),
                                   ("sounds", vanilla(["mob/wolf/big/hurt%d" % i for i in (1, 2, 3)], 0.65))])),
    ("werewolf_death", OrderedDict([("subtitle", "subtitles.mymod.werewolf_death"),
                                    ("sounds", vanilla(["mob/wolf/big/death"], 0.55))])),
    ("werewolf_hiss", OrderedDict([("subtitle", "subtitles.mymod.werewolf_hiss"),
                                   ("sounds", vanilla(["mob/cat/hiss%d" % i for i in (1, 2, 3)], 0.6))])),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.mymod.werewolf", "Werewolf"),
    ("item.mymod.werewolf_spawn_egg", "Werewolf Spawn Egg"),
    ("item.mymod.werewolf_pelt", "Werewolf Pelt"),
    ("item.mymod.feral_lining_module", "Feral Lining Module"),
    ("exo_module.mymod.feral_lining", "Feral Lining"),
    ("exo_module.mymod.feral_lining.description", "+10% speed and +1 attack damage"),
    ("item.mymod.holy_silver_ingot.cure", "Use on a cursed villager: lifts the werewolf curse"),
    ("item.mymod.crucifix.undead", "Only harms the undead and the unholy; triple damage to vampires, werewolves, the Schnozzle "
                                   "and skullcranes"),
    ("gamerule.mymod.werewolves", "Werewolves"),
    ("gamerule.mymod.werewolves.description", "Whether villagers can carry the werewolf curse, and whether cursed villagers "
                                              "turn into werewolves on full-moon nights."),
    ("subtitles.mymod.werewolf_growl", "Something growls"),
    ("subtitles.mymod.werewolf_howl", "Werewolf howls"),
    ("subtitles.mymod.werewolf_hurt", "Werewolf hurts"),
    ("subtitles.mymod.werewolf_death", "Werewolf dies"),
    ("subtitles.mymod.werewolf_hiss", "Something hisses"),
    ("message.mymod.werewolf.cured", "The curse lifts"),
    ("commands.mymod.werewolf.cursed", "%s now carries the werewolf curse"),
    ("commands.mymod.werewolf.cured", "Lifted the werewolf curse from %s"),
    ("commands.mymod.werewolf.is_werewolf", "%s is a werewolf"),
    ("commands.mymod.werewolf.is_cursed", "%s carries the werewolf curse"),
    ("commands.mymod.werewolf.not_cursed", "%s isn't cursed"),
    ("commands.mymod.werewolf.unrolled", "%s hasn't been checked for the curse yet"),
    ("commands.mymod.werewolf.transforming", "%s is changing"),
    ("commands.mymod.werewolf.reverted", "%s is a villager again"),
    ("commands.mymod.werewolf.no_target", "No villager or werewolf there (look at one, or name targets)"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- preview
# The model as client/werewolf/WerewolfModel builds it. Each part: pivot, rotation (x, y, z), cubes (texOffs, from,
# size, mirror) and children. Kept in step with the Java by hand.
BODY_LEAN = 0.2
CHEST_LEAN = 0.3


def rest_pose():
    return {
        "body": [(0.0, 9.0, 1.0), (BODY_LEAN, 0.0, 0.0)],
        "chest": [(0.0, -7.0, 0.0), (CHEST_LEAN, 0.0, 0.0)],
        "head": [(0.0, -9.0, -3.0), (-(BODY_LEAN + CHEST_LEAN), 0.0, 0.0)],
        "jaw": [(0.0, -1.0, -5.0), (0.0, 0.0, 0.0)],
        "right_ear": [(-2.5, -7.0, -1.5), (0.0, 0.0, -0.25)],
        "left_ear": [(2.5, -7.0, -1.5), (0.0, 0.0, 0.25)],
        "tail": [(0.0, -1.5, 2.5), (-1.1, 0.0, 0.0)],
        "right_arm": [(-8.5, -4.0, -5.0), (-0.15, 0.0, 0.12)],
        "left_arm": [(8.5, -4.0, -5.0), (-0.15, 0.0, -0.12)],
        "right_leg": [(-3.0, 8.0, 2.0), (0.0, 0.0, 0.0)],
        "left_leg": [(3.0, 8.0, 2.0), (0.0, 0.0, 0.0)],
    }


TREE = {
    "root": ["body", "right_arm", "left_arm", "right_leg", "left_leg"],
    "body": ["chest", "tail"],
    "chest": ["head"],
    "head": ["jaw", "right_ear", "left_ear"],
}
PART_CUBES = {
    "body": [("waist", (-4.0, -8.0, -3.0), False)],
    "chest": [("chest", (-6.5, -10.0, -4.5), False)],
    "head": [("head", (-4.0, -7.0, -5.0), False), ("snout", (-2.0, -4.0, -11.0), False)],
    "jaw": [("jaw", (-2.0, 0.0, -6.0), False)],
    "right_ear": [("ear", (-1.5, -4.0, -0.5), False)],
    "left_ear": [("ear", (-1.5, -4.0, -0.5), True)],
    "tail": [("tail", (-2.0, -2.0, 0.0), False)],
    "right_arm": [("arm", (-2.0, -2.0, -2.0), False)],
    "left_arm": [("arm", (-2.0, -2.0, -2.0), True)],
    "right_leg": [("leg", (-2.5, 0.0, -2.5), False)],
    "left_leg": [("leg", (-2.5, 0.0, -2.5), True)],
}


def animate(pose, walk=0.0, walk_speed=0.0, attack=0.0, howl=0.0, aggressive=False, age=0.0):
    """Python twin of WerewolfModel.setupAnim, for the preview."""
    swing = math.cos(walk * 0.6662) * 1.2 * walk_speed
    pose["right_leg"][1] = (swing, 0.0, 0.0)
    pose["left_leg"][1] = (-swing, 0.0, 0.0)
    for side, sign in (("right_arm", 1), ("left_arm", -1)):
        x, y, z = pose[side][1]
        pose[side][1] = (x - sign * swing * 0.8 - (0.5 if aggressive else 0.0), y, z)
    (bx, by, bz), brot = pose["body"]
    pose["body"][0] = (bx, by + abs(math.cos(walk * 0.6662)) * walk_speed, bz)
    if aggressive:
        for ear in ("right_ear", "left_ear"):
            x, y, z = pose[ear][1]
            pose[ear][1] = (x - 0.35, y, z)
    # Tail swaying, and lifting as it runs.
    tx, ty, tz = pose["tail"][1]
    pose["tail"][1] = (tx + 0.1 * math.sin(age * 0.1) + 0.4 * walk_speed, ty + 0.15 * math.sin(age * 0.07), tz)
    if attack > 0.0:
        # A claw swipe: the right arm hauled up and back, then raked down and across.
        s = math.sin(attack * math.pi)
        raise_ = math.sin(math.sqrt(attack) * math.pi)
        x, y, z = pose["right_arm"][1]
        pose["right_arm"][1] = (x - 2.2 * raise_ + 0.8 * s, y + 0.5 * s, z + 0.4 * raise_)
        jx, jy, jz = pose["jaw"][1]
        pose["jaw"][1] = (jx + 0.35 * s, jy, jz)
    if howl > 0.0:
        bx, by, bz = pose["body"][1]
        pose["body"][1] = (bx - 0.25 * howl, by, bz)
        cx_, cy_, cz_ = pose["chest"][1]
        pose["chest"][1] = (cx_ - 0.2 * howl, cy_, cz_)
        hx, hy, hz = pose["head"][1]
        pose["head"][1] = (hx - 0.6 * howl, hy * (1.0 - howl), hz)
        jx, jy, jz = pose["jaw"][1]
        pose["jaw"][1] = (jx + 0.55 * howl, jy, jz)
        for side, sign in (("right_arm", 1), ("left_arm", -1)):
            x, y, z = pose[side][1]
            pose[side][1] = (x + 0.35 * howl, y, z + sign * 0.35 * howl)
        for ear in ("right_ear", "left_ear"):
            x, y, z = pose[ear][1]
            pose[ear][1] = (x - 0.4 * howl, y, z)
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
    """Every textured face of the posed model: (corners in model space, uv rect, normal), as MC's ModelPart.Cube."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot = pose[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for cube, frm, mirror in PART_CUBES.get(child, []):
                (u, v), (w, h, d) = CUBES[cube]
                out.extend(cube_faces(m, o, u, v, frm, (w, h, d), mirror))
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
    result = []
    for verts, a0, b0, a1, b1 in polys:
        # Polygon.remap: vertex 0 gets (u1, v0), 1 (u0, v0), 2 (u0, v1), 3 (u1, v1).
        result.append((verts, (a0, b0, a1, b1)))
    return result


def render(pose, view, scale=8, width=260, height=380, light=(0.35, -0.8, -0.5)):
    """Orthographic render. view: 'front' (looking at its face), 'side' (from its right) or 'three' (three-quarters)."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    ox, oy = width * 0.47, height - 20
    lx, ly, lz = light
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    for verts, (ua, va, ub, vb) in quads(pose):
        # vertex 1 is (ua, va); 0 is (ub, va); 2 is (ua, vb).
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        shade = 0.55 + 0.45 * abs(n[0] * lx + n[1] * ly + n[2] * lz) / (nl * ln)
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
                for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a / nu, b / nv
                    p = tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))
                    # Turn the model about y for the view, then project: x right, y down, depth away.
                    x = p[0] * ca - p[2] * sa
                    z = p[0] * sa + p[2] * ca
                    corners.append((ox + x * scale, oy + (p[1] - 24.0) * scale, z))
                depth = sum(c[2] for c in corners) / 4
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
    s = 5
    big = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
           for y in range(TEX_H * s) for x in range(TEX_W * s)]
    for y in range(TEX_H * s):
        for x in range(TEX_W * s):
            c = TEXTURE.get(x // s, y // s)
            if c[3]:
                big[y * TEX_W * s + x] = c
    png(os.path.join(PREVIEW, "preview_werewolf_texture.png"), TEX_W * s, TEX_H * s, big)
    views = [render(rest_pose(), "front"), render(rest_pose(), "side"), render(rest_pose(), "three"),
             render(animate(rest_pose(), walk=1.2, walk_speed=0.9, aggressive=True, attack=0.45), "three"),
             render(animate(rest_pose(), howl=1.0), "side"), render(rest_pose(), "back")]
    out, bw, bh = board(views, 260, 380, 3)
    png(os.path.join(PREVIEW, "preview_werewolf_model.png"), bw, bh, out)
    # The items, enlarged: pelt, module, spawn egg.
    s = 10
    items = [read_png(os.path.join(TEX, "item", n + ".png"))[2] for n in ("werewolf_pelt", "feral_lining_module", "werewolf_spawn_egg")]
    sheet = [(52, 56, 64, 255)] * (16 * s * 3 * 16 * s)
    for k, px in enumerate(items):
        for y in range(16 * s):
            for x in range(16 * s):
                c = px[(y // s) * 16 + x // s]
                if c[3]:
                    sheet[y * 16 * s * 3 + k * 16 * s + x] = c
    png(os.path.join(PREVIEW, "preview_werewolf_items.png"), 16 * s * 3, 16 * s, sheet)
    if "--detail" in sys.argv:
        view = sys.argv[sys.argv.index("--detail") + 1]
        png(os.path.join(PREVIEW, "preview_werewolf_detail.png"), 520, 760,
            render(rest_pose(), view, scale=16, width=520, height=760))
    print("preview written to", PREVIEW)
print("done")
