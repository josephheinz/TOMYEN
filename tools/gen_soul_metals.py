"""Makes magmasteel, soul steel and the Soul Vacuum: textures, models, item definitions, recipes, tags, loot tables,
lang and sounds.

Magmasteel and soul steel are drawn from vanilla's own art, read at generation time from the Minecraft client jar, so
they sit in a vanilla inventory: magmasteel from iron (ingot, nugget, block, tools, armor icons and armor layers) in a
charred, near-black steel; soul steel from netherite in a pale grey-teal whose highlights lean toward vanilla soul
fire's cyan. Then each item gets a few pixels of its own (MARKS) so it can't be taken for iron or netherite at a
glance: magmasteel's glowing seams and cracks, notches and a chipped ingot; soul steel's etched runes and wisps of
soul fire rising off it. The magmasteel block and worn armor show the cracks of vanilla's magma block instead, and the
soul steel block a rune. Tool handles keep their own colours, and the models use vanilla's parents (cube_all,
item/handheld, item/generated).

The Soul Vacuum and the Soul Charge are new kinds of item, so they're drawn from scratch: the vacuum is a 3D model
(gunkit) held like a gun, with a glass canister whose soul glow and nozzle light grow with the mymod:soul_fill model
property; the charge is a 16x16 sprite.

On the data side: shaped recipes in vanilla's patterns with recipe-book advancements, block loot tables, and entries in
the tool, armor, mining, beacon and repair tags (merged into existing tag files). Lang entries are merged into en_us.
With ffmpeg on the path it synthesises the vacuum's motor loop and the soul wail (stereo: ffmpeg's own Vorbis encoder
only takes two channels).

Usage: python3 tools/gen_soul_metals.py src/main/resources/assets/mymod"""
import glob, json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave, zipfile, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import Kit, check, display, placed, centred, bounds_centre, model, ref, range_dispatch, png, write

ASSETS = sys.argv[1]  # .../assets/mymod
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
TAGS = os.path.normpath(os.path.join(DATA, "..", "minecraft", "tags"))
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models"), os.path.join(ASSETS, "items")
ROOT = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", ".."))


# ======================================================================================================= vanilla jar


def client_jar():
    """The Minecraft client jar: loom's shared cache, else the project's own."""
    candidates = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar")))
    candidates += sorted(glob.glob(os.path.join(ROOT, ".gradle", "loom-cache", "**", "minecraft-client*.jar"), recursive=True))
    for path in candidates:
        with zipfile.ZipFile(path) as z:
            if "assets/minecraft/textures/item/iron_ingot.png" in z.namelist():
                return path
    raise SystemExit("no Minecraft client jar found (run ./gradlew build once)")


JAR = zipfile.ZipFile(client_jar())


def read_png(data):
    """(w, h, [(r, g, b, a)]) from PNG bytes: 8-bit, or palette/grey at lower depths; not interlaced."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, plte, trns = 8, b"", None, None
    while pos < len(data):
        n, t = struct.unpack(">I4s", data[pos:pos + 8])
        d = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if t == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", d)
        elif t == b"PLTE":
            plte = [tuple(d[i:i + 3]) for i in range(0, len(d), 3)]
        elif t == b"tRNS":
            trns = d
        elif t == b"IDAT":
            idat += d
    assert interlace == 0
    raw = zlib.decompress(idat)
    chans = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bpp = max(1, chans * depth // 8)
    stride = (w * chans * depth + 7) // 8
    px, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        if depth < 8:
            vals = [(byte >> (8 - depth * (k + 1))) & ((1 << depth) - 1) for byte in line for k in range(8 // depth)][:w * chans]
        else:
            vals = list(line)
        for x in range(w):
            if ctype == 6:
                px.append(tuple(vals[x * 4:x * 4 + 4]))
            elif ctype == 2:
                rgb = tuple(vals[x * 3:x * 3 + 3])
                # A tRNS chunk here names one colour (16 bits a channel) as see-through.
                key = struct.unpack(">HHH", trns) if trns else None
                px.append(rgb + (0 if key == rgb else 255,))
            elif ctype == 4:
                px.append((vals[x * 2],) * 3 + (vals[x * 2 + 1],))
            elif ctype == 0:
                g = vals[x] * 255 // ((1 << depth) - 1)
                px.append((g, g, g, 0 if trns and struct.unpack(">H", trns)[0] == vals[x] else 255))
            else:
                r, g, b = plte[vals[x]]
                px.append((r, g, b, trns[vals[x]] if trns and vals[x] < len(trns) else 255))
    return w, h, px


def vanilla(path):
    return read_png(JAR.read(f"assets/minecraft/textures/{path}.png"))


# ======================================================================================================= recolouring


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def hexrgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(stops, t):
    """Colour at t (0-1) along stops [(t, (r, g, b))]."""
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return lerp(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0.0)
    return stops[-1][1]


def is_handle(c):
    """A tool's wooden (or netherite's dark red) handle: warm and saturated, unlike the greys and purples of the metal."""
    r, g, b = c[:3]
    return max(r, g, b) > 0 and (max(r, g, b) - min(r, g, b)) / max(r, g, b) > 0.25 and r - b > 20 and g >= b


# Magmasteel: iron's shapes and shading in charred, near-black steel. On the block and the worn armor the glow comes
# from vanilla's magma block, its cracks showing through; on the items it's placed by hand (MARKS).
MAGMA_STEEL = [(0.0, hexrgb(0x0A0707)), (0.35, hexrgb(0x1C1514)), (0.6, hexrgb(0x2F2523)), (0.85, hexrgb(0x4A3C38)),
               (1.0, hexrgb(0x6F5D57))]
_, _, MAGMA = vanilla("block/magma")  # 16x48: three animation frames; the first is the pattern


def magma_cracks(x, y):
    """How strongly the magma block's glow shows at (x, y), and its colour: its brightest cracks fully, its orange
    seams faintly, its dark crust not at all."""
    c = MAGMA[(y % 16) * 16 + x % 16]
    if c[0] >= 0xF0:
        return 0.9, c[:3]
    if c[0] >= 0xE0:
        return 0.15, c[:3]
    return 0.0, c[:3]


def magmasteel(c, x, y):
    return ramp(MAGMA_STEEL, lum(c) / 255.0)


def magmasteel_veined(c, x, y):
    t = lum(c) / 255.0
    weight, glow = magma_cracks(x, y)
    # Leave the outline and the darkest shading alone, so the shape reads as vanilla's.
    if t < 0.3:
        weight = 0.0
    return lerp(ramp(MAGMA_STEEL, t), glow, weight * (0.6 + 0.4 * t))


def soul_ramp():
    """Pale grey-teal steel, its highlights warmed toward vanilla soul fire's cyan (its colours by brightness, the
    dimmest, ember-like ones dropped)."""
    _, _, fire = vanilla("block/soul_fire_0")
    cols = sorted({c[:3] for c in fire if c[3] > 0 and lum(c) > 60}, key=lum)
    pick = lambda q: cols[min(len(cols) - 1, int(q * len(cols)))]
    return [(0.0, hexrgb(0x16262A)), (0.3, hexrgb(0x3C5659)), (0.55, hexrgb(0x6A8A8B)),
            (0.8, lerp(hexrgb(0x98B6B4), pick(0.5), 0.15)), (1.0, lerp(hexrgb(0xCFE4E0), pick(0.8), 0.25))]


SOUL_STEEL = soul_ramp()
NETHERITE_PEAK = 0x86  # netherite's brightest highlight; soul steel's ramp is spread over netherite's range


def soul_steel(c, x, y):
    return ramp(SOUL_STEEL, lum(c) / NETHERITE_PEAK)


def soul_steel_from_iron(c, x, y):
    """For what netherite doesn't have (a nugget): iron's, its range spread over the same ramp."""
    return ramp(SOUL_STEEL, lum(c) / 255.0 * 0.95)


# What sets each item apart from the vanilla one it's drawn from: a handful of pixels, as "x,y role" pairs. Roles:
# x clears a pixel (a notch or a chip), o adds or darkens one to the outline. Magmasteel's g, G and e are its glowing
# seams and cracks (magma orange, its white-hot yellow, a dull ember); soul steel's r and R are a rune etched into it
# (lit, and the dark of the groove), w and W wisps of soul fire coming off it.
ACCENTS = {
    "magmasteel": {"o": hexrgb(0x0A0606) + (255,), "g": hexrgb(0xE66410) + (255,), "G": hexrgb(0xFBAA59) + (255,),
                   "e": hexrgb(0x9A3410) + (255,)},
    "soul_steel": {"o": hexrgb(0x16262A) + (255,), "r": hexrgb(0x74F1F5) + (255,), "R": hexrgb(0x1C5C66) + (255,),
                   "w": hexrgb(0x74F1F5) + (150,), "W": hexrgb(0xCFFDFF) + (210,)},
}
MARKS = {
    "magmasteel": {
        # A molten fuller down the blade, a notch in its edge, a clipped point and embers in the guard.
        "sword": "15,0x 13,2G 12,3g 11,3o 11,4g 10,5G 9,6g 8,7e 3,7g",
        # A hooked tip, a short prong, a glowing seam along the head.
        "pickaxe": "5,4o 7,3g 8,3G 9,3g 13,8g 13,9G 13,11x",
        # A molten cutting edge and a notch behind it.
        "axe": "9,2g 10,2G 8,3g 7,4G 7,5g 10,1x 11,5e",
        "shovel": "13,3g 12,4g 11,5G 10,6e 12,2x",
        "hoe": "7,2g 8,2G 9,2g 6,2x 6,3o",
        # A crest along the top, a crack down the brow.
        "helmet": "7,2o 8,2o 7,3e 8,3e 8,4g 8,5G 7,6g",
        # A crack down the breastbone, a split hem.
        "chestplate": "7,7g 8,8G 7,9g 8,10g 7,11e 7,13x 8,13x",
        "leggings": "8,3G 5,6g 4,8g 10,7g 10,10e",
        "boots": "2,10g 3,10G 13,10g 12,10G 4,6g 10,6g",
        # A chipped corner and a glowing crack across the top.
        "ingot": "10,2x 11,2x 11,3o 12,3o 4,6g 5,6G 6,7g 7,7g 8,8G 9,8e 9,9g 9,10e 12,6g 13,6e",
        "nugget": "7,7G 8,8g 6,9e",
    },
    "soul_steel": {
        # A rune down the blade and soul fire flickering off the point.
        "sword": "12,3r 11,4R 10,5r 4,8r 10,1w 11,0W",
        "pickaxe": "8,3r 7,3R 13,8r 8,1w 9,0W",
        "axe": "9,4r 8,4R 7,1w 6,0W",
        "shovel": "12,4r 11,5R 14,1w 15,0W",
        "hoe": "9,2r 8,2R 5,1w 4,0W",
        # A plume of soul fire and a rune on the brow.
        "helmet": "7,5r 8,5r 7,6R 7,2w 8,1W 6,1w",
        # A rune diamond on the breast.
        "chestplate": "7,8r 6,9r 8,9r 7,10r 7,9R",
        "leggings": "6,3r 7,3r 4,8r 10,8r",
        "boots": "4,5r 11,5r 4,1W 5,2w 11,1W 10,2w",
        "ingot": "6,6r 7,5r 8,6r 7,7r 7,6R 4,2w 3,1W",
        "nugget": "7,7r 7,4w 8,3W",
    },
}


def apply_marks(px, w, metal, item):
    for mark in MARKS[metal].get(item, "").split():
        xy, role = mark[:-1], mark[-1]
        x, y = (int(v) for v in xy.split(","))
        px[y * w + x] = (0, 0, 0, 0) if role == "x" else ACCENTS[metal][role]


def recolour(src, dst, fn, handles=True, marks=None):
    """A vanilla texture recoloured by fn(colour, x, y), keeping tool handles; marks: (metal, item) to mark up."""
    w, h, px = vanilla(src)
    out = []
    for i, c in enumerate(px):
        if c[3] == 0:
            out.append((0, 0, 0, 0))
        elif handles and is_handle(c):
            out.append(c)
        else:
            out.append(fn(c, i % w, i // w) + (c[3],))
    if marks:
        apply_marks(out, w, *marks)
    png(os.path.join(TEX, dst + ".png"), w, h, out)


def rune_block(base, dst):
    """Soul steel's block: netherite's, pale, with a rune etched in the middle and wisps of soul fire in the corners."""
    w, h, px = vanilla(base)
    out = [soul_steel(c, i % w, i // w) + (255,) for i, c in enumerate(px)]
    rune = ["..r..",
            ".rRr.",
            "r.R.r",
            ".rRr.",
            "..r.."]
    for y, row in enumerate(rune):
        for x, ch in enumerate(row):
            if ch != ".":
                out[(y + 5) * w + x + 5] = ACCENTS["soul_steel"][ch]
    for x, y in ((2, 2), (13, 13), (3, 12), (12, 3)):
        out[y * w + x] = lerp(out[y * w + x][:3], hexrgb(0x74F1F5), 0.45) + (255,)
    png(os.path.join(TEX, dst + ".png"), w, h, out)


# ========================================================================================================= metals

# Metal: (the vanilla metal it's drawn from, its recolouring, the recolouring of its block and worn armor).
METALS = OrderedDict([("magmasteel", ("iron", magmasteel, magmasteel_veined)),
                      ("soul_steel", ("netherite", soul_steel, soul_steel))])
TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]


def item_model(name, parent):
    write(os.path.join(MODELS, "item", name + ".json"), OrderedDict([
        ("parent", parent), ("textures", OrderedDict(layer0="mymod:item/" + name))]))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


def block_files(name):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": "mymod:block/" + name}}})
    write(os.path.join(MODELS, "block", name + ".json"), OrderedDict([
        ("parent", "minecraft:block/cube_all"), ("textures", OrderedDict(all="mymod:block/" + name))]))
    write(os.path.join(ITEMS, name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:block/" + name)])})
    write(os.path.join(DATA, "loot_table", "blocks", name + ".json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                                ("entries", [{"type": "minecraft:item", "name": "mymod:" + name}]), ("rolls", 1)])]),
        ("random_sequence", "mymod:blocks/" + name)]))


for metal, (base, fn, worn) in METALS.items():
    recolour(f"item/{base}_ingot", f"item/{metal}_ingot", fn, marks=(metal, "ingot"))
    # Netherite has no nugget, so soul steel's is drawn from iron's.
    recolour("item/iron_nugget", f"item/{metal}_nugget", fn if base == "iron" else soul_steel_from_iron, marks=(metal, "nugget"))
    if metal == "soul_steel":
        rune_block(f"block/{base}_block", f"block/{metal}_block")
    else:
        recolour(f"block/{base}_block", f"block/{metal}_block", worn)
    for part in TOOLS + ARMOR:
        recolour(f"item/{base}_{part}", f"item/{metal}_{part}", fn, marks=(metal, part))
    # Iron's armor layers are all pale highlights; taken a shade down, magmasteel's read as dark steel, not copper.
    layer_fn = (lambda c, x, y: worn(tuple(v * 0.8 for v in c[:3]), x, y)) if base == "iron" else worn
    for layer in ("humanoid", "humanoid_leggings"):
        recolour(f"entity/equipment/{layer}/{base}", f"entity/equipment/{layer}/{metal}", layer_fn, handles=False)
    for name in [f"{metal}_ingot", f"{metal}_nugget"] + [f"{metal}_{p}" for p in ARMOR]:
        item_model(name, "minecraft:item/generated")
    for tool in TOOLS:
        item_model(f"{metal}_{tool}", "minecraft:item/handheld")
    block_files(f"{metal}_block")
    write(os.path.join(ASSETS, "equipment", metal + ".json"), {"layers": OrderedDict([
        ("humanoid", [{"texture": "mymod:" + metal}]), ("humanoid_leggings", [{"texture": "mymod:" + metal}])])})

# ===================================================================================================== soul charge

# Soul lantern flame colours, darkest to brightest.
SOUL_HEX = [0x0B3A40, 0x03969A, 0x2AC9CF, 0x74F1F5, 0xCFFDFF]
SOUL = [hexrgb(h) for h in SOUL_HEX]


def soul_charge():
    """A wisp of soul fire caught in a small stoppered glass bead: a bright core, a flame licking up, a dark rim."""
    rows = [
        "................",
        "......ss........",
        ".....sbbs.......",
        "......sbs..s....",
        "....RRRRRRs.....",
        "...RgcbbcgR.....",
        "..RgcbWWbcgR....",
        "..RcbWWWWbcR....",
        "..RcbWWWWbcR....",
        "..RgcbWWbcgR....",
        "...RgccccgR.....",
        "....RRRRRR......",
        ".....kKKk.......",
        "......kk........",
        "................",
        "................",
    ]
    pal = {"R": SOUL[0] + (255,), "g": (0x9C, 0xC8, 0xCC, 255), "c": SOUL[1] + (255,), "b": SOUL[2] + (255,),
           "W": SOUL[4] + (255,), "s": SOUL[3] + (255,), "k": (0x3E, 0x44, 0x53, 255), "K": (0x49, 0x50, 0x65, 255)}
    px = []
    for row in rows:
        assert len(row) == 16, row
        px += [(0, 0, 0, 0) if ch == "." else pal[ch] for ch in row]
    png(os.path.join(TEX, "item", "soul_charge.png"), 16, 16, px)
    item_model("soul_charge", "minecraft:item/generated")


soul_charge()

# ===================================================================================================== soul vacuum

LEVELS = 5  # canister glow: empty, then four steps of fill
FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)  # where long guns put the shooting hand (gen_shotguns.py)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
GRIP = (8.0, 4.6, 11.4)  # the top of the grip, under the hand


def vacuum_kit():
    k = Kit("vacuum", "item/soul_vacuum", seed=77, size=64)
    steel = [0x23262B, 0x33373E, 0x474C55, 0x5E6570, 0x7C8490]

    def metal(x, y, w, h):
        i = 2 + (1 if (x + y * 3) % 7 == 0 else 0) - (1 if (x * 5 + y) % 11 == 0 else 0)
        return k.rgb(steel[i], jitter=4)

    def plate(x, y, w, h):
        if x == 0 or y == 0:
            return k.rgb(steel[3])
        if x == w - 1 or y == h - 1:
            return k.rgb(steel[0])
        if (x, y) in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            return k.rgb(steel[4])
        return k.rgb(steel[2], jitter=3)

    def dark(x, y, w, h):
        return k.rgb(steel[1], jitter=3)

    def rubber(x, y, w, h):
        return k.rgb(0x1A1A1E if y % 3 else 0x26262C, jitter=2)

    def glass(x, y, w, h):
        # Clear glass (mostly see-through) with a bright streak and pale rims.
        if x == 0 or x == w - 1:
            return (0xB8, 0xE4, 0xE8, 200)
        if x in (2, 3) and y % 9 < 6:
            return (0xEC, 0xFF, 0xFF, 170)
        return (0xA8, 0xDA, 0xE0, 70)

    def soul(level):
        def f(x, y, w, h):
            hi = SOUL_HEX[min(4, 1 + level)]
            lo = SOUL_HEX[min(3, level)]
            return k.rgb(hi if (x * 3 + y * 5) % 7 < 3 else lo, jitter=6)
        return f

    def nozzle_glow(level):
        def f(x, y, w, h):
            edge = x in (0, w - 1) or y in (0, h - 1)
            if level == 0:
                return k.rgb(0x101417 if not edge else 0x23262B)
            return k.rgb(SOUL_HEX[min(4, level)] if not edge else SOUL_HEX[max(0, level - 2)])
        return f

    def chip(x, y, w, h):
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            return k.rgb(0x1E3A1E)
        return k.rgb(0x2F6A2F if (x + y) % 3 else 0xC8B04A, jitter=4)

    def redstone(x, y, w, h):
        return k.rgb(0xFF2A1A if (x + y) % 3 == 0 else 0xAA0F0A, jitter=8)

    k.swatch("metal", 0, 0, 8, 4, metal)
    k.swatch("plate", 8, 0, 12, 4, plate, picture=True)
    k.swatch("dark", 12, 0, 16, 4, dark)
    k.swatch("rubber", 0, 4, 4, 8, rubber)
    k.swatch("glass", 4, 4, 8, 8, glass, picture=True)
    k.swatch("chip", 8, 4, 10, 6, chip, picture=True)
    k.swatch("redstone", 10, 4, 11, 5, redstone, picture=True)
    for level in range(LEVELS):
        k.swatch(f"soul_{level}", 0 + level * 2, 8, 2 + level * 2, 12, soul(level))
        k.swatch(f"glow_{level}", 0 + level * 2, 12, 2 + level * 2, 14, nozzle_glow(level), picture=True)
    return k


def vacuum(k, level):
    out = []
    # Housing, with a motor drum behind it and a vented cap.
    k.chamfered(out, 5.2, 4.8, 3.5, 10.8, 10.2, 13.5, 0.8, "metal", cap="plate")
    k.rod(out, 13.4, 17.0, 2.9, "dark", cy=7.6, cap="plate")
    k.rod(out, 17.0, 17.6, 2.2, "metal", cy=7.6)
    # Side panel: a computer chip and a redstone lamp under glass.
    k.sticker(out, 10.8, 6.0, 6.0, 10.85, 8.6, 9.0, "chip", "east")
    k.sticker(out, 10.8, 8.9, 10.0, 10.85, 9.7, 10.8, "redstone", "east")
    # The canister on top: glass between two steel collars, the souls glowing inside.
    k.rod(out, 4.2, 5.2, 2.6, "metal", cy=12.6)
    k.rod(out, 11.2, 12.2, 2.6, "metal", cy=12.6)
    k.rod(out, 5.2, 11.2, 2.3, "glass", cy=12.6, caps=(False, False))
    if level > 0:
        top = 10.6 + 3.6 * level / (LEVELS - 1)
        core = k.box(out, 6.6, 10.6, 5.6, 9.4, top, 10.8, f"soul_{level}", emit=True)
        core["light_emission"] = 3 + 3 * level
    # Nozzle: a long tube from the housing, a flared mouth, and the throat lit by the canister.
    k.rod(out, -11.0, 3.5, 1.3, "metal", cy=7.4)
    k.rod(out, -12.5, -11.0, 1.8, "dark", cy=7.4, cap="metal")
    k.rod(out, -14.0, -12.5, 2.3, "metal", cy=7.4, cap="metal")
    glow = k.sticker(out, 6.3, 6.3, -14.05, 9.7, 8.5, -14.0, f"glow_{level}", "north", emit=level > 0)
    if level > 0:
        glow["light_emission"] = 3 * level + 3
    # Grip under the housing, raked back, with a trigger.
    k.box(out, 6.6, 0.0, 10.0, 9.4, 5.0, 12.8, "rubber", rot=("x", -15, (8, 5.0, 11.4)))
    k.box(out, 7.6, 3.0, 8.2, 8.4, 4.8, 8.8, "dark")
    return out


def vacuum_displays(elements):
    """Held like the shotguns: first person turned -8 degrees with the grip where the M4's is, third person in the
    crossbow hold (AvatarSoulVacuumPoseMixin), side on in the inventory."""
    fp_rot, fp_scale, gui_scale = [0, -8, 0], 0.6, 0.48
    fp = display(fp_rot, placed(fp_rot, fp_scale, GRIP, FP_GRIP_TARGET), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, GRIP, TP_GRIP_TARGET), 0.65)
    c = bounds_centre(elements)
    gui_rot = [0, -90, 0]
    return OrderedDict([
        ("thirdperson_righthand", tp), ("thirdperson_lefthand", tp),
        ("firstperson_righthand", fp), ("firstperson_lefthand", fp),
        ("gui", display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale)),
        ("ground", display([0, 0, 0], centred([0, 0, 0], 0.35, c, 2.0), 0.35)),
        ("fixed", display(gui_rot, centred(gui_rot, gui_scale * 1.3, c), gui_scale * 1.3)),
    ])


K = vacuum_kit()
frames = [vacuum(K, level) for level in range(LEVELS)]
for level, elements in enumerate(frames):
    check(f"soul_vacuum_{level}", elements)
DISPLAYS = vacuum_displays(frames[-1])
K.save(TEX)
for level, elements in enumerate(frames):
    write(os.path.join(MODELS, "item", f"soul_vacuum_{level}.json"), model(K, elements, DISPLAYS))
# Empty below the first soul; then a step brighter each quarter.
write(os.path.join(ITEMS, "soul_vacuum.json"), {"model": range_dispatch("mymod:soul_fill", [
    (0.01, ref("soul_vacuum_1")), (0.26, ref("soul_vacuum_2")), (0.51, ref("soul_vacuum_3")), (0.76, ref("soul_vacuum_4"))],
    ref("soul_vacuum_0"))})

# ========================================================================================================== recipes

RECIPES = os.path.join(DATA, "recipe")
ADVANCEMENTS = os.path.join(DATA, "advancement", "recipes")


def advancement(name, category, unlock_item):
    write(os.path.join(ADVANCEMENTS, category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": unlock_item}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shaped(name, result, pattern, key, unlock, count=1, category="misc", book=None):
    write(os.path.join(RECIPES, name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, book or category, unlock)


def shapeless(name, result, ingredients, unlock, count=1, category="misc"):
    write(os.path.join(RECIPES, name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, category, unlock)


TOOL_PATTERNS = {"sword": ["X", "X", "#"], "pickaxe": ["XXX", " # ", " # "], "axe": ["XX", "X#", " #"],
                 "shovel": ["X", "#", "#"], "hoe": ["XX", " #", " #"]}
ARMOR_PATTERNS = {"helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"],
                  "boots": ["X X", "X X"]}
for metal in METALS:
    ingot, nugget, block = f"mymod:{metal}_ingot", f"mymod:{metal}_nugget", f"mymod:{metal}_block"
    shaped(f"{metal}_block", block, ["###", "###", "###"], {"#": ingot}, ingot, category="building")
    shapeless(f"{metal}_ingot_from_{metal}_block", ingot, [block], block, 9)
    shaped(f"{metal}_ingot_from_nuggets", ingot, ["###", "###", "###"], {"#": nugget}, nugget)
    shapeless(f"{metal}_nugget", nugget, [ingot], ingot, 9)
    for tool, pattern in TOOL_PATTERNS.items():
        # Weapons file under combat in the recipe book, the rest under tools (equipment); both are equipment recipes.
        shaped(f"{metal}_{tool}", f"mymod:{metal}_{tool}", pattern, {"X": ingot, "#": "minecraft:stick"}, ingot,
               category="equipment", book="combat" if tool == "sword" else "equipment")
    for piece, pattern in ARMOR_PATTERNS.items():
        shaped(f"{metal}_{piece}", f"mymod:{metal}_{piece}", pattern, {"X": ingot}, ingot, category="equipment", book="combat")
shaped("soul_vacuum", "mymod:soul_vacuum", ["PGP", "ILC", " RI"],
       {"P": "mymod:steel_plate", "G": "minecraft:glass", "I": "mymod:steel_ingot", "L": "minecraft:soul_lantern",
        "C": "mymod:computer_chip", "R": "minecraft:redstone"}, "minecraft:soul_lantern", category="equipment")

# ============================================================================================================= tags


def merge_tag(path, values):
    """Adds values to a tag file, keeping what's there and its order."""
    try:
        with open(path) as f:
            tag = json.load(f, object_pairs_hook=OrderedDict)
    except FileNotFoundError:
        tag = OrderedDict([("values", [])])
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write(path, tag)


for metal in METALS:
    merge_tag(os.path.join(DATA, "tags", "item", f"repairs_{metal}.json"), [f"mymod:{metal}_ingot"])
for kind in TOOLS:
    merge_tag(os.path.join(TAGS, "item", kind + "s.json"), [f"mymod:{m}_{kind}" for m in METALS])
for piece, tag in zip(ARMOR, ["head_armor", "chest_armor", "leg_armor", "foot_armor"]):
    merge_tag(os.path.join(TAGS, "item", tag + ".json"), [f"mymod:{m}_{piece}" for m in METALS])
merge_tag(os.path.join(TAGS, "item", "beacon_payment_items.json"), [f"mymod:{m}_ingot" for m in METALS])
blocks = [f"mymod:{m}_block" for m in METALS]
merge_tag(os.path.join(TAGS, "block", "mineable", "pickaxe.json"), blocks)
merge_tag(os.path.join(TAGS, "block", "needs_iron_tool.json"), blocks)
merge_tag(os.path.join(TAGS, "block", "beacon_base_blocks.json"), blocks)
# What the Soul Vacuum can't take (bosses, and the tank, which is a vehicle), and the elites that fight it.
merge_tag(os.path.join(DATA, "tags", "entity_type", "soul_vacuum_immune.json"),
          ["minecraft:wither", "minecraft:ender_dragon", "minecraft:warden", "minecraft:elder_guardian", "mymod:tank"])
merge_tag(os.path.join(DATA, "tags", "entity_type", "soul_vacuum_resistant.json"),
          ["mymod:schnozzle", "mymod:vampire", "minecraft:ravager", "minecraft:evoker", "minecraft:piglin_brute",
           "minecraft:iron_golem"])

# ============================================================================================================= lang

LANG = OrderedDict()
NAMES = {"magmasteel": "Magmasteel", "soul_steel": "Soul Steel"}
for metal, title in NAMES.items():
    LANG[f"item.mymod.{metal}_ingot"] = f"{title} Ingot"
    LANG[f"item.mymod.{metal}_nugget"] = f"{title} Nugget"
    LANG[f"block.mymod.{metal}_block"] = f"Block of {title}"
    for part in TOOLS + ARMOR:
        LANG[f"item.mymod.{metal}_{part}"] = f"{title} {part.capitalize()}"
LANG.update({
    "item.mymod.magmasteel.auto_smelt": "Smelts what it mines (not with Silk Touch)",
    "item.mymod.magmasteel.ignites": "Sets what it hits alight",
    "item.mymod.magmasteel.armor_piece": "Each piece: -15% fire damage",
    "item.mymod.magmasteel.armor_set": "Full set: fire immunity, and walk on lava",
    "item.mymod.soul_steel.lifesteal": "Kills heal you 2 hearts",
    "item.mymod.soul_steel.armor_set": "Full set: weaker monsters dread you",
    "item.mymod.soul_charge": "Soul Charge",
    "item.mymod.soul_vacuum": "Soul Vacuum",
    "item.mymod.soul_vacuum.souls": "Souls: %s/%s",
    "item.mymod.soul_vacuum.weak_souls": "(%s weak: two make a charge)",
    "item.mymod.soul_vacuum.use": "Hold use to suck up a mob's soul",
    "item.mymod.soul_vacuum.release": "Sneak-use to release souls as Soul Charges",
    "item.mymod.soul_vacuum.full": "The canister is full: sneak-use to release the souls",
    "item.mymod.soul_vacuum.empty": "No souls to release",
    "item.mymod.soul_vacuum.resists": "%s resists! Weaken it below a quarter of its health",
    "subtitles.mymod.soul_vacuum_loop": "Soul Vacuum whines",
    "subtitles.mymod.soul_wail": "Soul wails",
})
lang_path = os.path.join(ASSETS, "lang", "en_us.json")
with open(lang_path) as f:
    lang = json.load(f, object_pairs_hook=OrderedDict)
lang.update(LANG)
with open(lang_path, "w") as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)
    f.write("\n")

# =========================================================================================================== sounds

sounds_path = os.path.join(ASSETS, "sounds.json")
with open(sounds_path) as f:
    sounds = json.load(f, object_pairs_hook=OrderedDict)
for name in ("soul_vacuum_loop", "soul_wail"):
    sounds[name] = OrderedDict([("subtitle", "subtitles.mymod." + name), ("sounds", ["mymod:" + name])])
write(sounds_path, sounds)

RATE = 22050


def save_ogg(path, samples):
    peak = max(abs(s) for s in samples) or 1.0
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "sound.wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(b"".join(struct.pack("<h", int(s / peak * 26000)) for s in samples))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "4", path],
                       check=True)
    print("wrote", path)


def vacuum_loop():
    """Two seconds that loop: a motor whine (whole cycles of each tone, so it joins up) over a rushing hiss of air."""
    rr = random.Random(31)
    n = RATE * 2
    hiss, low, out = 0.0, 0.0, []
    for i in range(n):
        t = i / RATE
        white = rr.uniform(-1, 1)
        hiss = hiss * 0.6 + white * 0.4  # a bright rush of air
        low = low * 0.97 + white * 0.03
        whine = (0.30 * math.sin(math.tau * 420 * t) + 0.12 * math.sin(math.tau * 840 * t) + 0.06 * math.sin(math.tau * 1260 * t))
        wobble = 0.85 + 0.15 * math.sin(math.tau * 7 * t)
        out.append(whine * wobble + hiss * 0.35 + low * 1.5)
    return out


def soul_wail():
    """A ghostly wail: a voice-like tone sliding down an octave with a slow vibrato, breathy, swelling and fading,
    with a soft echo."""
    rr = random.Random(5)
    n = int(RATE * 1.6)
    phase, breath, out = 0.0, 0.0, []
    for i in range(n):
        t = i / RATE
        f = 660 * (0.5 ** (t / 1.6)) * (1 + 0.025 * math.sin(math.tau * 5.5 * t))
        phase += math.tau * f / RATE
        voice = math.sin(phase) + 0.45 * math.sin(2 * phase + 0.6) + 0.2 * math.sin(3 * phase + 1.1)
        breath = breath * 0.8 + rr.uniform(-1, 1) * 0.2
        env = min(1.0, t / 0.25) * max(0.0, 1.0 - max(0.0, t - 0.9) / 0.7)
        out.append((voice * 0.8 + breath * 0.5) * env)
    delay = int(RATE * 0.18)
    for i in range(delay, n):
        out[i] += out[i - delay] * 0.35
    return out


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping the sounds")
else:
    save_ogg(os.path.join(ASSETS, "sounds", "soul_vacuum_loop.ogg"), vacuum_loop())
    save_ogg(os.path.join(ASSETS, "sounds", "soul_wail.ogg"), soul_wail())
print("soul metals done")
