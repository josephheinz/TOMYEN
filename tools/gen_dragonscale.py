"""Paints dragonscale and writes everything around it: the armor (icons, worn layers for adults, leggings and babies,
the wings the chestplate spreads, the helmet's horns), the two scales, the Fossilized Dragon Bone and Dragon Scale
Fossil blocks and the Dragonbone Long Sword; their item definitions, models and block states; and on the data side
the equipment asset, loot tables, recipes with recipe-book advancements and the dragon fossil worldgen.

Everything starts from vanilla art read out of the Minecraft client jar (see gen_longswords.client_jar()): the
netherite armor icons and layers give the shapes and their shading, vanilla's elytra the wings' outline, the bone
block the fossils' grain, the turtle scute the scales' outline and the iron sword the long sword. The colours are a
small red dragon palette (see palettes()): oxblood to crimson scales with an ember sheen that slides from gold to
orange across each piece, and warm ivory bone. On top go details vanilla's pieces don't have, so nothing reads as a
recolour: overlapping scale rows in tidy staggered courses, ivory horns and a crest on the helmet, an ivory segmented
belly plate and a spine ridge on the chestplate, knee guards on the leggings, claws on the boots, scalloped red
membranes and dark finger bones on the wings, vertebra rings and a marrow core on the fossil bone, and a faintly
rusty scale imprint pressed into the scale fossil. Shading is flat ramps, no noise.

With --preview PATH it also writes a board comparing each texture with the vanilla one it started from, and the
icons with vanilla's other red things (red-dyed leather, redstone).

Usage: python3 tools/gen_dragonscale.py src/main/resources/assets/mymod [--preview out.png] [path/to/client.jar]"""
import os, sys, zipfile
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
import gen_longswords
from gen_longswords import read_png
from gunkit import png, write

ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if "--preview" in ARGS:
    i = ARGS.index("--preview")
    PREVIEW = ARGS[i + 1]
    del ARGS[i:i + 2]
ASSETS = ARGS[0] if ARGS else None
sys.argv = [sys.argv[0]] + ARGS  # client_jar() reads an explicit jar from argv[2]
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod")) if ASSETS else None
CLEAR = (0, 0, 0, 0)

# ======================================================================================================= palette


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def scaled(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (255,)


def palettes():
    """The red dragon's ramps. Scales run outline to highlight with the hue walking as they brighten: oxblood
    (a touch of blue) in the shadows, crimson in the body, warming toward orange at the top, so they read as hide
    rather than redstone's flat pure red. The sheen ends are ember gold and ember orange. Bone is a warm ivory, for
    the belly plate, horns, claws and blade; ancient is the weathered brick red of buried scales; fossil is a plain
    warm stone grey."""
    scale = [(24, 6, 10, 255), (52, 10, 20, 255), (80, 15, 25, 255), (110, 21, 29, 255), (142, 30, 33, 255),
             (172, 46, 38, 255)]
    ember = (250, 182, 88, 255)
    glow = (234, 102, 42, 255)
    bone = [(112, 84, 66, 255), (170, 142, 112, 255), (214, 194, 160, 255), (240, 228, 200, 255)]
    ancient = [(44, 20, 18, 255), (86, 38, 30, 255), (120, 54, 42, 255), (148, 74, 56, 255), (178, 108, 84, 255)]
    fossil = [(48, 44, 42, 255), (76, 71, 67, 255), (104, 98, 92, 255), (132, 125, 117, 255), (160, 153, 144, 255)]
    return scale, ember, glow, bone, ancient, fossil


SCALE = EMBER = GLOW = BONE = ANCIENT = FOSSIL = None


def sheen(x, y, w, h):
    """The ember sheen at a texel: gold at the upper left of a piece, orange at the lower right."""
    t = (x / max(1, w - 1) + y / max(1, h - 1)) / 2
    return mix(EMBER, GLOW, t)


def scale_shade(x, y, cell_w=4, cell_h=3):
    """Overlapping scales in staggered courses: each scale a light rim along the top, a plain body, and dark
    corners where the next course tucks in under it. Returns a step on the ramp: +1, 0 or -1."""
    row = y // cell_h
    cx = (x + (row % 2) * (cell_w // 2)) % cell_w
    cy = y % cell_h
    if cy == 0:
        return 1 if 0 < cx < cell_w - 1 else 0
    if cy == cell_h - 1:
        return -1 if cx in (0, cell_w - 1) else 0
    return 0


def luminance(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def tones(px):
    """Each opaque texel's brightness as 0..1 within the image's own range, for reusing vanilla's shading."""
    ls = [luminance(c) for c in px if c[3]]
    lo, hi = min(ls), max(ls)
    return [None if not c[3] else (luminance(c) - lo) / max(1.0, hi - lo) for c in px]


# ===================================================================================================== armor


def smooth_tones(px, w, h):
    """Vanilla's shading, blurred over a 3x3 block within the piece and cut into three bands (0, 1, 2), so its
    dithering doesn't come through as grain."""
    lum = tones(px)
    bands = [None] * (w * h)
    for y in range(h):
        for x in range(w):
            if lum[y * w + x] is None:
                continue
            near = [lum[ny * w + nx] for ny in range(max(0, y - 1), min(h, y + 2)) for nx in range(max(0, x - 1), min(w, x + 2))
                    if lum[ny * w + nx] is not None]
            v = sum(near) / len(near)
            bands[y * w + x] = 0 if v < 0.4 else 1 if v < 0.65 else 2
    return lum, bands


def iridescent(c, level, x, y, w, h):
    """A ramp colour with the sheen: the brighter the level, the more it leans ember gold (upper left of a piece)
    or orange (lower right), shifting smoothly across it."""
    return mix(c, sheen(x, y, w, h), 0.035 * level)


def dragonscale(px, w, h, outline=0.12, cell=(4, 3)):
    """Vanilla netherite turned to dragonscale: vanilla's darkest texels stay as the outline, the rest take a level
    on the red ramp from vanilla's own shading, smoothed into three bands, with the scale courses on top, and
    the iridescent sheen over all."""
    lum, bands = smooth_tones(px, w, h)
    out = list(px)
    for y in range(h):
        for x in range(w):
            if lum[y * w + x] is None:
                continue
            if lum[y * w + x] < outline:
                out[y * w + x] = SCALE[0]
                continue
            level = max(1, min(5, 2 + bands[y * w + x] + scale_shade(x, y, *cell)))
            out[y * w + x] = iridescent(SCALE[level], level, x, y, w, h)
    return out


def put(px, w, x, y, c):
    if 0 <= x < w and 0 <= y * w + x < len(px):
        px[y * w + x] = c


def outline_new(px, w, h, before):
    """Gives any texel added past vanilla's outline its own dark edge where it meets transparency."""
    added = [i for i in range(w * h) if px[i][3] and not before[i][3]]
    for i in added:
        x, y = i % w, i // w
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not px[ny * w + nx][3]:
                px[ny * w + nx] = SCALE[0]


def icon_helmet(src):
    px = dragonscale(src, 16, 16)
    before = list(px)
    # Ivory horns sweeping up and out from the top corners, darker at the root; a ridge down the crown between them.
    for (x, y), c in {(4, 2): BONE[1], (5, 2): BONE[0], (3, 1): BONE[2], (4, 1): BONE[1], (2, 0): BONE[3],
                      (3, 0): BONE[2]}.items():
        put(px, 16, x, y, c)
        put(px, 16, 15 - x, y, c)
    put(px, 16, 7, 2, BONE[2])
    put(px, 16, 8, 2, BONE[1])
    outline_new(px, 16, 16, before)
    return px


def icon_chestplate(src):
    px = dragonscale(src, 16, 16)
    before = list(px)
    # Shoulder spikes, and the pale segmented belly plate down the middle.
    for (x, y), c in {(1, 1): BONE[2], (2, 1): SCALE[3], (14, 1): BONE[2], (13, 1): SCALE[3], (0, 0): BONE[3],
                      (15, 0): BONE[3]}.items():
        put(px, 16, x, y, c)
    for y in range(7, 13):
        for x in (6, 7, 8, 9):
            if px[y * 16 + x][3] and px[y * 16 + x] != SCALE[0]:
                band = BONE[2] if y % 2 else BONE[1]
                put(px, 16, x, y, band if x in (7, 8) else scaled(band, 0.8))
    outline_new(px, 16, 16, before)
    return px


def icon_leggings(src):
    px = dragonscale(src, 16, 16)
    before = list(px)
    # Fins at the hips, pale knee guards.
    for (x, y) in ((2, 3), (2, 4), (13, 3), (13, 4)):
        put(px, 16, x, y, SCALE[3])
    put(px, 16, 1, 3, BONE[2])
    put(px, 16, 14, 3, BONE[2])
    for x in (4, 5, 10, 11):
        put(px, 16, x, 8, BONE[2])
        put(px, 16, x, 9, BONE[1])
    outline_new(px, 16, 16, before)
    return px


def icon_boots(src):
    px = dragonscale(src, 16, 16)
    before = list(px)
    # Bone claws out past the toes, and a cuff ridge.
    for (x, y) in ((0, 11), (0, 12), (15, 11), (15, 12)):
        put(px, 16, x, y, BONE[3] if y == 12 else BONE[1])
    for x in (4, 10):
        put(px, 16, x, 4, BONE[2])
    outline_new(px, 16, 16, before)
    return px


def worn_layer(src, w, h, kind):
    """A worn armor texture (64x32 adult or leggings): the dragonscale turn of vanilla's, plus the details on their
    faces. UVs are vanilla's humanoid armor model's."""
    px = dragonscale(src, w, h)

    def paint(x, y, c):
        if px[y * w + x][3] and px[y * w + x] != SCALE[0]:
            px[y * w + x] = c

    if kind == "humanoid":
        # Helmet crest: down the middle of the crown (top face 8..16 x 0..8) and up the back (24..32 x 8..16).
        for y in range(0, 8):
            paint(11, y, BONE[2] if y % 2 == 0 else BONE[1])
            paint(12, y, BONE[1] if y % 2 == 0 else BONE[0])
        for y in range(8, 12):
            paint(27, y, BONE[1])
            paint(28, y, BONE[0])
        # Chestplate belly plate: the body's front (20..28 x 20..32), banded like a dragon's underside.
        for y in range(20, 32):
            for x in range(22, 26):
                band = BONE[2] if (y - 20) % 2 == 0 else BONE[1]
                paint(x, y, band if x in (23, 24) else scaled(band, 0.82))
        # Spine ridge down the back (32..40 x 20..32): a pale peak every other course.
        for y in range(20, 32):
            paint(35, y, BONE[1] if y % 3 == 0 else SCALE[4])
            paint(36, y, BONE[0] if y % 3 == 0 else SCALE[3])
        # Shoulder plates: a pale rim across the top of each arm's outer face (40..44 x 20..22).
        for x in range(40, 44):
            paint(x, 20, BONE[1])
        # Boot claws: three pale tips along the foot's front (4..8 x 31).
        for x in (4, 6, 7):
            paint(x, 31, BONE[3])
    else:
        # Knee guards on each leg's front (4..8 x 25..27).
        for x in range(4, 8):
            paint(x, 25, BONE[2])
            paint(x, 26, BONE[1])
    return px


def baby_layer(src):
    """The baby layer keeps to the shared look: dragonscale shading and scales, without the per-face details."""
    return dragonscale(src, 64, 64)


# ===================================================================================================== wings


def wings(src):
    """Vanilla's elytra outline, as a dragon's wing: a red membrane brightening toward the edge, three thin finger
    bones fanning from the root at the middle of the top, darker than the membrane, the trailing edge cut into
    scallops between their tips, and the ember sheen along the leading edge."""
    w, h = 64, 32
    out = [CLEAR] * (w * h)
    mask = [src[i][3] > 0 for i in range(w * h)]
    # The wing face: x 35..46, y 2..21 (the rest of the elytra's texture is its thin edges).
    x0, x1, y0, y1 = 35, 46, 2, 21
    root = (40, 2)
    tips = [(37, 20), (41, 21), (45, 19)]

    def on_bone(x, y):
        for tx, ty in tips:
            # Distance from the segment root-tip.
            ax, ay, bx, by = root[0], root[1], tx, ty
            t = max(0.0, min(1.0, ((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2)))
            if abs(x - (ax + t * (bx - ax))) + abs(y - (ay + t * (by - ay))) < 0.55:
                return True
        return False

    for y in range(h):
        for x in range(w):
            if not mask[y * w + x]:
                continue
            if not (x0 <= x <= x1 and y0 <= y <= y1):
                out[y * w + x] = SCALE[2]
                continue
            # Scallops: near the bottom, cut away texels far from any finger tip.
            if y >= y1 - 1:
                nearest = min(abs(x - tx) for tx, ty in tips)
                if nearest > (2 if y == y1 else 3):
                    continue
            if y == y0:
                out[y * w + x] = sheen(x - x0, 0, x1 - x0 + 1, 1)
            elif on_bone(x, y):
                out[y * w + x] = SCALE[1] if y < 12 else SCALE[0]
            else:
                depth = (y - y0) / (y1 - y0)
                out[y * w + x] = SCALE[2] if depth < 0.35 else SCALE[3] if depth < 0.75 else SCALE[4]
    return out


def horns():
    """The horn cubes' texture (UVs in DragonHornsLayer): ivory, the 2x4x2 base at (0, 0) ringed with ridges and
    darker toward the skull, the 1x3x1 tip at (0, 6) paling to its point."""
    w, h = 16, 16
    out = [CLEAR] * (w * h)
    for y in range(0, 6):
        for x in range(0, 8):
            ridge = y % 2 == 0
            lit = x // 2 % 2 == 0
            out[y * w + x] = BONE[(1 if ridge else 0) + (1 if lit and y < 4 else 0)]
    for y in range(6, 10):
        for x in range(0, 4):
            out[y * w + x] = [BONE[1], BONE[2], BONE[2], BONE[3]][y - 6] if x % 2 == 0 else [BONE[1], BONE[1], BONE[2], BONE[2]][y - 6]
    return out


# ===================================================================================================== items


def scale_shape(scute):
    """A single scale: as wide as the turtle scute at its widest, but a shield rather than an oval, its top a round
    arc and its lower half tapering to a point."""
    xs = [i % 16 for i, c in enumerate(scute) if c[3]]
    half = (max(xs) - min(xs) + 1) / 2 - 1
    mask = [False] * 256
    for y in range(2, 15):
        if y <= 6:
            r = half * max(0.0, 1 - ((6 - y) / 4.5) ** 2) ** 0.5
        else:
            r = half * (13.6 - y) / 7.6
        for x in range(16):
            if abs(x - 7.5) <= r:
                mask[y * 16 + x] = True
    return mask


def scale_item(mask, ancient):
    """A scale: the outline darker, two growth lines arcing across it below the root, a highlight at the upper left
    (ancient ones weathered brick red and dull, ender ones glossy crimson with an ember glint)."""
    ramp = ANCIENT if ancient else SCALE[2:]
    out = [CLEAR] * 256
    for y in range(16):
        for x in range(16):
            if not mask[y * 16 + x]:
                continue
            edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16 and mask[(y + dy) * 16 + x + dx])
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            # Growth lines bow downward like the scale's own rim.
            line = round(y - 0.12 * (x - 7.5) ** 2) in (6, 10)
            if edge:
                c = SCALE[0] if not ancient else ANCIENT[0]
            elif line:
                c = ramp[1]
            else:
                c = ramp[3] if x < 7 and y < 9 else ramp[2]
            out[y * 16 + x] = c
    for (x, y) in ((5, 5), (5, 4), (6, 4)):
        if mask[y * 16 + x]:
            out[y * 16 + x] = ANCIENT[4] if ancient else EMBER
    return out


def long_sword(iron):
    """The Dragonbone Long Sword: the iron sword's blade in bone, its hilt in dragonscale, laid out three blades long
    like every long sword (gen_longswords.long_sword)."""
    lum = tones(iron)
    px = []
    for i, c in enumerate(iron):
        if not c[3]:
            px.append(c)
            continue
        x, y = i % 16, i // 16
        v = lum[i]
        blade = x - y >= gen_longswords.BASE and x + y in gen_longswords.BAND
        if v < 0.15:
            px.append(SCALE[0] if not blade else BONE[0])
        elif blade:
            px.append(BONE[min(3, 1 + int(v * 3))])
        else:
            px.append(SCALE[min(4, 2 + int(v * 3))])
    return gen_longswords.long_sword(px)


# ===================================================================================================== blocks


def fossil_bone_side(bone):
    """Bone block grain in the fossil ramp, cut into vertebrae by two rings."""
    lum = tones(bone)
    out = [FOSSIL[1 + min(2, int(lum[i] * 3))] for i in range(256)]
    for x in range(16):
        for ring in (5, 11):
            out[ring * 16 + x] = FOSSIL[0]
            out[(ring + 1) * 16 + x] = FOSSIL[4] if x % 4 else FOSSIL[3]
    return out


def fossil_bone_top(top):
    """The bone block's end ring in the fossil ramp, round a dark rust-brown marrow core."""
    lum = tones(top)
    out = [FOSSIL[1 + min(2, int(lum[i] * 3))] for i in range(256)]
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 2:
                out[y * 16 + x] = ANCIENT[0]
            elif d < 3:
                out[y * 16 + x] = ANCIENT[1]
            elif d >= 7:
                out[y * 16 + x] = FOSSIL[3]
    return out


def scale_fossil():
    """Fossil stone with the scales pressed into it: the imprint in tidy staggered courses over the whole face (each
    scale a lit lower rim and a shadowed top, as a hollow is lit), faintly rusty where the scales lay, and three
    scales still there, weathered brick red, on a diagonal."""
    rust = (150, 70, 50, 255)
    stain = [mix(c, rust, 0.16) for c in FOSSIL]
    out = []
    for y in range(16):
        for x in range(16):
            row = y // 4
            cx, cy = (x + (row % 2) * 2) % 4, y % 4
            if cy == 0:
                c = stain[0] if cx in (0, 3) else stain[1]
            elif cy == 3:
                c = stain[1] if cx in (0, 3) else stain[3]
            else:
                c = stain[2]
            out.append(c)
    for row, col in ((0, 1), (1, 2), (2, 3)):
        # A scale's cell: course row, cell col (odd courses sit two texels over).
        for dy in range(4):
            for dx in range(4):
                x, y = (col * 4 + dx - (row % 2) * 2) % 16, row * 4 + dy
                if dy == 0:
                    c = ANCIENT[0] if dx in (0, 3) else ANCIENT[4]
                elif dy == 3:
                    c = ANCIENT[0] if dx in (0, 3) else ANCIENT[2]
                else:
                    c = ANCIENT[3]
                out[y * 16 + x] = c
    return out


# ===================================================================================================== json


def block_models():
    write(os.path.join(ASSETS, "models", "block", "fossilized_dragon_bone.json"), OrderedDict([
        ("parent", "minecraft:block/cube_column"),
        ("textures", OrderedDict([("end", "mymod:block/fossilized_dragon_bone_top"), ("side", "mymod:block/fossilized_dragon_bone_side")]))]))
    write(os.path.join(ASSETS, "blockstates", "fossilized_dragon_bone.json"), {"variants": OrderedDict([
        ("axis=x", OrderedDict([("model", "mymod:block/fossilized_dragon_bone"), ("x", 90), ("y", 90)])),
        ("axis=y", {"model": "mymod:block/fossilized_dragon_bone"}),
        ("axis=z", OrderedDict([("model", "mymod:block/fossilized_dragon_bone"), ("x", 90)]))])})
    write(os.path.join(ASSETS, "models", "block", "dragon_scale_fossil.json"), OrderedDict([
        ("parent", "minecraft:block/cube_all"), ("textures", {"all": "mymod:block/dragon_scale_fossil"})]))
    write(os.path.join(ASSETS, "blockstates", "dragon_scale_fossil.json"), {"variants": {"": {"model": "mymod:block/dragon_scale_fossil"}}})
    for name in ("fossilized_dragon_bone", "dragon_scale_fossil"):
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:block/" + name)])})


def item_model(name, parent="minecraft:item/generated", display=None):
    model = OrderedDict([("parent", parent), ("textures", {"layer0": "mymod:item/" + name})])
    if display:
        model["display"] = display
    write(os.path.join(ASSETS, "models", "item", name + ".json"), model)
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + name)])})


def equipment_asset():
    write(os.path.join(ASSETS, "equipment", "dragonscale.json"), {"layers": OrderedDict([
        ("humanoid", [{"texture": "mymod:dragonscale"}]),
        ("humanoid_baby", [{"texture": "mymod:dragonscale"}]),
        ("humanoid_leggings", [{"texture": "mymod:dragonscale"}]),
        ("wings", [{"texture": "mymod:dragonscale"}])])})


def advancement(path, recipe, item, criterion):
    write(os.path.join(DATA, "advancement", "recipes", path, recipe + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            (criterion, OrderedDict([("conditions", {"items": [{"items": item}]}), ("trigger", "minecraft:inventory_changed")])),
            ("has_the_recipe", OrderedDict([("conditions", {"recipes": "mymod:" + recipe}), ("trigger", "minecraft:recipe_unlocked")]))])),
        ("requirements", [[criterion, "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + recipe]})]))


def shaped(name, pattern, key, category="equipment"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", 1), ("id", "mymod:" + name)]))]))


def data():
    scales = "#mymod:dragon_scales"
    for piece, pattern in (("helmet", ["SSS", "SNS"]), ("chestplate", ["SNS", "SSS", "SSS"]),
                           ("leggings", ["SSS", "SNS", "S S"]), ("boots", ["SNS", "S S"])):
        name = "dragonscale_" + piece
        shaped(name, pattern, OrderedDict([("N", "minecraft:netherite_scrap"), ("S", scales)]))
        advancement("combat", name, scales, "has_dragon_scale")
    shaped("dragonbone_long_sword", [" SB", "SBS", "BS "], OrderedDict([("B", "mymod:fossilized_dragon_bone"), ("S", scales)]))
    advancement("combat", "dragonbone_long_sword", "mymod:fossilized_dragon_bone", "has_fossilized_dragon_bone")

    def table(kind, name, pools):
        write(os.path.join(DATA, "loot_table", name + ".json"), OrderedDict([
            ("type", "minecraft:" + kind), ("pools", pools), ("random_sequence", "mymod:" + name)]))

    table("block", "blocks/fossilized_dragon_bone", [OrderedDict([
        ("condition", {"type": "minecraft:survives_explosion"}),
        ("entries", [OrderedDict([("type", "minecraft:item"), ("name", "mymod:fossilized_dragon_bone")])]),
        ("rolls", 1)])])
    table("block", "blocks/dragon_scale_fossil", [OrderedDict([
        ("entries", [OrderedDict([("type", "minecraft:alternatives"), ("children", [
            OrderedDict([("type", "minecraft:item"), ("condition", "minecraft:tool/can_silk_touch"), ("name", "mymod:dragon_scale_fossil")]),
            OrderedDict([("type", "minecraft:item"), ("modifier", [
                OrderedDict([("type", "minecraft:set_count"), ("count", OrderedDict([("type", "minecraft:uniform"), ("min", 1), ("max", 2)]))]),
                OrderedDict([("type", "minecraft:apply_bonus"), ("enchantment", "minecraft:fortune"), ("formula", "minecraft:ore_drops")]),
                {"type": "minecraft:explosion_decay"}]), ("name", "mymod:ancient_dragon_scale")])])])]),
        ("rolls", 1)])])
    table("entity", "entities/ender_dragon_scales", [OrderedDict([
        ("rolls", 1),
        ("entries", [OrderedDict([("type", "minecraft:item"), ("name", "mymod:ender_dragon_scale"), ("modifier", [
            OrderedDict([("type", "minecraft:set_count"), ("count", OrderedDict([("type", "minecraft:uniform"), ("min", 12), ("max", 16)]))])])])])])])

    write(os.path.join(DATA, "worldgen", "feature", "dragon_fossil.json"), OrderedDict([
        ("type", "mymod:dragon_fossil"),
        ("spine_length", OrderedDict([("type", "minecraft:uniform"), ("min_inclusive", 10), ("max_inclusive", 15)])),
        ("scale_fossils", OrderedDict([("type", "minecraft:uniform"), ("min_inclusive", 2), ("max_inclusive", 4)])),
        ("integrity", 0.85)]))
    write(os.path.join(DATA, "worldgen", "feature", "dragon_fossil_deep.json"), OrderedDict([
        ("type", "mymod:dragon_fossil"),
        ("spine_length", OrderedDict([("type", "minecraft:uniform"), ("min_inclusive", 13), ("max_inclusive", 18)])),
        ("scale_fossils", OrderedDict([("type", "minecraft:uniform"), ("min_inclusive", 3), ("max_inclusive", 5)])),
        ("integrity", 0.9)]))
    write(os.path.join(DATA, "worldgen", "placed_feature", "dragon_fossil_buried.json"), OrderedDict([
        ("feature", "mymod:dragon_fossil"),
        ("placement", [
            OrderedDict([("type", "minecraft:rarity_filter"), ("chance", 48)]),
            {"type": "minecraft:in_square"},
            OrderedDict([("type", "minecraft:heightmap"), ("heightmap", "OCEAN_FLOOR_WG")]),
            OrderedDict([("type", "minecraft:offset"), ("x", 0), ("y", OrderedDict([
                ("type", "minecraft:uniform"), ("min_inclusive", -15), ("max_inclusive", -10)])), ("z", 0)]),
            {"type": "minecraft:biome"}])]))
    write(os.path.join(DATA, "worldgen", "placed_feature", "dragon_fossil_deep.json"), OrderedDict([
        ("feature", "mymod:dragon_fossil_deep"),
        ("placement", [
            OrderedDict([("type", "minecraft:rarity_filter"), ("chance", 64)]),
            {"type": "minecraft:in_square"},
            OrderedDict([("type", "minecraft:height_range"), ("height", OrderedDict([
                ("type", "minecraft:uniform"), ("min_inclusive", {"absolute": -56}), ("max_inclusive", {"absolute": -12})]))]),
            {"type": "minecraft:biome"}])]))


# ===================================================================================================== preview


def board(path, rows, s=8):
    """Rows of (width, height, pixels) images side by side, scaled up, on a grey board with a checker under
    transparency. A None in a row leaves a gap."""
    gap = 6
    widths = [sum((im[0] * s if im else 6 * s) + gap for im in row) + gap for row in rows]
    heights = [max(im[1] * s for im in row if im) + gap for row in rows]
    W, H = max(widths), sum(heights) + gap
    out = [(70, 72, 80, 255)] * (W * H)
    oy = gap
    for row, rh in zip(rows, heights):
        ox = gap
        for im in row:
            if im is None:
                ox += 6 * s + gap
                continue
            w, h, px = im
            for y in range(h * s):
                for x in range(w * s):
                    c = px[(y // s) * w + x // s]
                    out[(oy + y) * W + ox + x] = c[:3] + (255,) if c[3] else ((98, 100, 110, 255) if ((x // 4) + (y // 4)) % 2 else (86, 88, 98, 255))
            ox += w * s + gap
        oy += rh
    png(path, W, H, out)


RED_DYE = (0xB0, 0x2E, 0x26)


def dyed(base, overlay, dye):
    """A leather piece as the game draws it dyed: the greyscale base multiplied by the dye, the undyed overlay on top."""
    w, h, px = base
    out = [c if not c[3] else tuple(c[i] * dye[i] // 255 for i in range(3)) + (255,) for c in px]
    out = [o if o[3] else c for c, o in zip(out, overlay[2])]
    return (w, h, out)


def crop(im, x0, y0, x1, y1):
    w, h, px = im
    return (x1 - x0, y1 - y0, [px[y * w + x] for y in range(y0, y1) for x in range(x0, x1)])


def figure(armor, leggings, back=False):
    """A flat front (or back) view of a player in the armor: the faces of each box laid side by side."""
    w = 16
    out = [CLEAR] * (w * 32)

    def blit(src, sx, sy, dx, dy, cw, ch, mirror=False):
        for y in range(ch):
            for x in range(cw):
                c = src[2][(sy + y) * src[0] + sx + (cw - 1 - x if mirror else x)]
                if c[3]:
                    out[(dy + y) * w + dx + x] = c
    f = 24 if back else 8
    blit(leggings, 28 if back else 20, 20, 4, 8, 8, 12)    # waist
    blit(leggings, 12 if back else 4, 20, 4, 20, 4, 12)    # legs
    blit(leggings, 12 if back else 4, 20, 8, 20, 4, 12, True)
    blit(armor, 12 if back else 4, 20, 4, 20, 4, 12)       # boots
    blit(armor, 12 if back else 4, 20, 8, 20, 4, 12, True)
    blit(armor, 32 if back else 20, 20, 4, 8, 8, 12)       # body
    blit(armor, 52 if back else 44, 20, 0, 8, 4, 12)       # arms
    blit(armor, 52 if back else 44, 20, 12, 8, 4, 12, True)
    blit(armor, f, 8, 4, 0, 8, 8)                          # head
    return (w, 32, out)


# ===================================================================================================== main


def main():
    global SCALE, EMBER, GLOW, BONE, ANCIENT, FOSSIL
    if not ASSETS:
        sys.exit(__doc__.rsplit("Usage: ", 1)[1])
    jar = gen_longswords.client_jar()
    print("vanilla textures from", jar)
    with zipfile.ZipFile(jar) as z:
        def tex(path):
            return read_png(z.read("assets/minecraft/textures/" + path + ".png"))
        SCALE, EMBER, GLOW, BONE, ANCIENT, FOSSIL = palettes()
        icons = {p: tex("item/netherite_" + p) for p in ("helmet", "chestplate", "leggings", "boots")}
        armor_v = tex("entity/equipment/humanoid/netherite")
        leggings_v = tex("entity/equipment/humanoid_leggings/netherite")
        baby_v = tex("entity/equipment/humanoid_baby/netherite")
        elytra_v = tex("entity/equipment/wings/elytra")
        scute_v = tex("item/turtle_scute")
        bone_side_v = tex("block/bone_block_side")
        bone_top_v = tex("block/bone_block_top")
        iron_sword_v = tex("item/iron_sword")
        compare = {n: tex("item/" + n) for n in ("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
                                                 "armadillo_scute", "netherite_sword", "redstone")}
        compare["redstone_block"] = tex("block/redstone_block")
        for p in ("helmet", "chestplate", "leggings", "boots"):
            compare["leather_" + p] = dyed(tex("item/leather_" + p), tex("item/leather_" + p + "_overlay"), RED_DYE)
        for (w, h, _), name in ((armor_v, "humanoid"), (leggings_v, "leggings"), (elytra_v, "elytra"), (baby_v, "baby")):
            if (w, h) != {"humanoid": (64, 32), "leggings": (64, 32), "elytra": (64, 32), "baby": (64, 64)}[name]:
                sys.exit(f"vanilla {name} texture is {w}x{h}: layout changed")

    T = os.path.join(ASSETS, "textures")
    out = {}
    out["helmet"] = icon_helmet(icons["helmet"][2])
    out["chestplate"] = icon_chestplate(icons["chestplate"][2])
    out["leggings"] = icon_leggings(icons["leggings"][2])
    out["boots"] = icon_boots(icons["boots"][2])
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        png(os.path.join(T, "item", f"dragonscale_{piece}.png"), 16, 16, out[piece])
        item_model("dragonscale_" + piece)
    armor = worn_layer(armor_v[2], 64, 32, "humanoid")
    leggings = worn_layer(leggings_v[2], 64, 32, "leggings")
    baby = baby_layer(baby_v[2])
    png(os.path.join(T, "entity", "equipment", "humanoid", "dragonscale.png"), 64, 32, armor)
    png(os.path.join(T, "entity", "equipment", "humanoid_leggings", "dragonscale.png"), 64, 32, leggings)
    png(os.path.join(T, "entity", "equipment", "humanoid_baby", "dragonscale.png"), 64, 64, baby)
    wing = wings(elytra_v[2])
    png(os.path.join(T, "entity", "equipment", "wings", "dragonscale.png"), 64, 32, wing)
    horn = horns()
    png(os.path.join(T, "entity", "equipment", "dragonscale_horns.png"), 16, 16, horn)
    equipment_asset()

    drop = scale_shape(scute_v[2])
    ancient, ender = scale_item(drop, True), scale_item(drop, False)
    png(os.path.join(T, "item", "ancient_dragon_scale.png"), 16, 16, ancient)
    png(os.path.join(T, "item", "ender_dragon_scale.png"), 16, 16, ender)
    item_model("ancient_dragon_scale")
    item_model("ender_dragon_scale")

    side, top, fossil = fossil_bone_side(bone_side_v[2]), fossil_bone_top(bone_top_v[2]), scale_fossil()
    png(os.path.join(T, "block", "fossilized_dragon_bone_side.png"), 16, 16, side)
    png(os.path.join(T, "block", "fossilized_dragon_bone_top.png"), 16, 16, top)
    png(os.path.join(T, "block", "dragon_scale_fossil.png"), 16, 16, fossil)
    block_models()

    sword = long_sword(iron_sword_v[2])
    png(os.path.join(T, "item", "dragonbone_long_sword.png"), gen_longswords.SIZE, gen_longswords.SIZE, sword)
    item_model("dragonbone_long_sword", "minecraft:item/handheld", gen_longswords.displays())
    data()

    if PREVIEW:
        def im(px, w=16, h=16):
            return (w, h, px)
        netherite_ls = read_png(open(os.path.join(T, "item", "netherite_long_sword.png"), "rb").read())
        board(PREVIEW, [
            [icons[p] for p in ("helmet", "chestplate", "leggings", "boots")] + [None]
            + [compare["diamond_" + p] for p in ("helmet", "chestplate", "leggings", "boots")] + [None]
            + [im(out[p]) for p in ("helmet", "chestplate", "leggings", "boots")],
            [compare["leather_" + p] for p in ("helmet", "chestplate", "leggings", "boots")] + [None]
            + [compare["redstone"], compare["redstone_block"], None]
            + [im(out[p]) for p in ("helmet", "chestplate", "leggings", "boots")] + [None, im(ancient), im(ender)],
            [scute_v, compare["armadillo_scute"], None, im(ancient), im(ender), None, bone_side_v, bone_top_v, None,
             im(side), im(top), im(fossil)],
            [netherite_ls, im(sword, 32, 32), None, figure(armor_v, leggings_v), figure(armor_v, leggings_v, True), None,
             figure((64, 32, armor), (64, 32, leggings)), figure((64, 32, armor), (64, 32, leggings), True), None,
             crop(elytra_v, 22, 0, 48, 22), crop((64, 32, wing), 22, 0, 48, 22), im(horn)],
            [armor_v, (64, 32, armor), leggings_v, (64, 32, leggings)],
        ])
        print("preview", PREVIEW)
    print("dragonscale done")


if __name__ == "__main__":
    main()
