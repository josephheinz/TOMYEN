"""Makes froststeel, voidsteel and Void Flux: textures, models, item definitions, recipes, tags, loot tables and lang;
and the data for the long swords of the mod's four metals (magmasteel, soul steel, froststeel, voidsteel), whose
textures and models tools/gen_longswords.py draws from each metal's sword afterwards.

Like magmasteel and soul steel (tools/gen_soul_metals.py), both metals are drawn from vanilla's own art, read at
generation time from the Minecraft client jar, and recoloured along a ramp by brightness so vanilla's shading carries
over: froststeel from iron, in a pale ice blue-white over deep glacier blue (cooler and paler than diamond's mint, and
blue where iron is grey); voidsteel from diamond's tools and armor and gold's ingot and nugget, in a deep black-purple
that lifts to violet at its edges (where netherite is a warm charcoal). Then each item gets a few pixels of its own
(MARKS): froststeel's crystals of frost standing off its edges and rime along them; voidsteel's specks of starlight
and a glint of ender pearl teal (the pearl's own colours, read from the jar). The froststeel block shows packed ice's
streaks through it and its worn armor a rime of frost along every edge; the voidsteel block and armor a scatter of
stars. Tool handles keep their own colours, and the models use vanilla's parents (cube_all, item/handheld,
item/generated).

Void Flux is vanilla's glowstone dust pile in ender pearl teal, its highlights the pale yellow of end stone.

On the data side: shaped recipes in vanilla's patterns (and long swords' three swords on the diagonal) with
recipe-book advancements, block loot tables, and entries in the tool, armor, mining, beacon, repair and long sword
tags (merged into existing tag files). Lang entries are merged into en_us. The sounds are vanilla's.

Usage: python3 tools/gen_frost_void_metals.py src/main/resources/assets/tomyen
       python3 tools/gen_longswords.py src/main/resources/assets/tomyen"""
import glob, json, os, struct, sys, zipfile, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import png, ref, write

ASSETS = sys.argv[1]  # .../assets/tomyen
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
TAGS = os.path.normpath(os.path.join(DATA, "..", "minecraft", "tags"))
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models"), os.path.join(ASSETS, "items")
ROOT = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", ".."))


# ======================================================================================================= vanilla jar


def client_jar():
    """The Minecraft client jar: loom's shared cache, else the project's own (or, in a worktree, the main checkout's)."""
    candidates = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar")))
    roots = [ROOT]
    marker = os.sep + os.path.join(".claude", "worktrees") + os.sep
    if marker in ROOT:
        roots.append(ROOT.split(marker)[0])
    for root in roots:
        candidates += sorted(glob.glob(os.path.join(root, ".gradle", "loom-cache", "**", "minecraft-client*.jar"), recursive=True))
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
    """A tool's wooden handle: warm and saturated, unlike the greys and cyans of the metal."""
    r, g, b = c[:3]
    return max(r, g, b) > 0 and (max(r, g, b) - min(r, g, b)) / max(r, g, b) > 0.25 and r - b > 20 and g >= b


def colours(path):
    """A vanilla texture's opaque colours, darkest first."""
    _, _, px = vanilla(path)
    return sorted({c[:3] for c in px if c[3] > 0}, key=lum)


# Froststeel: iron's greys (outline 0x18, then 0x44, 0x6B, 0x96, 0xBE, 0xD8 and white) as glacier ice: a deep blue
# outline and edge, a clear ice blue through the middle tones, and frost white at the top. Its stops sit on iron's
# own greys so each keeps one clean colour.
FROST = [(0.0, hexrgb(0x0E1730)), (24 / 255, hexrgb(0x16254A)), (68 / 255, hexrgb(0x2D4F86)), (107 / 255, hexrgb(0x4E7DB8)),
         (150 / 255, hexrgb(0x86B2E0)), (190 / 255, hexrgb(0xB4D3F2)), (216 / 255, hexrgb(0xD3E6FA)), (1.0, hexrgb(0xF4FAFF))]


def froststeel(c, x, y):
    return ramp(FROST, lum(c) / 255.0)


RIME = hexrgb(0xDCEEFF)


def settled_frost(w, h, px):
    """Frost settled on the item's upper edges, as snow lies on grass: each texel of the dark outline with nothing
    above it turns frost white (not the wooden handle's)."""
    top = [i for i in range(w, w * h) if px[i][3] and px[i - w][3] == 0 and lum(px[i]) < 70 and not is_handle(px[i])]
    top += [i for i in range(w) if px[i][3] and lum(px[i]) < 70 and not is_handle(px[i])]
    for i in top:
        px[i] = RIME + (px[i][3],)


# Voidsteel: diamond's shading (outline 0x08 2520, then up through its cyans to a pale mint) as a black-purple that
# lifts to violet only at its brightest, so it reads dark like netherite but plainly purple.
VOID = [(0.0, hexrgb(0x05030A)), (28 / 225, hexrgb(0x08040F)), (47 / 225, hexrgb(0x120A22)), (74 / 225, hexrgb(0x1C1032)),
        (104 / 225, hexrgb(0x28164A)), (149 / 225, hexrgb(0x351F5E)), (176 / 225, hexrgb(0x452878)), (1.0, hexrgb(0x7B57B8))]
DIAMOND_PEAK = 225  # diamond's brightest highlight (0xA4FDF0); voidsteel's ramp is spread over diamond's range


def voidsteel(c, x, y):
    return ramp(VOID, lum(c) / DIAMOND_PEAK)


def voidsteel_from_gold(c, x, y):
    """For what diamond doesn't have (an ingot, a nugget): gold's, its range spread over the same ramp."""
    return ramp(VOID, (lum(c) - 40) / 200.0 * 0.85)


def voidsteel_layer(c, x, y):
    """The worn armor: diamond's layers are bright all over, so taken a shade down to keep the set dark."""
    return ramp(VOID, lum(c) / DIAMOND_PEAK * 0.82)


# Ender pearl teal for voidsteel's glint, and end stone's pale yellow for Void Flux: their colours by brightness.
PEARL = colours("item/ender_pearl")
END_STONE = colours("block/end_stone")


def pick(cols, q):
    return cols[min(len(cols) - 1, int(q * len(cols)))]


# What sets each item apart from the vanilla one it's drawn from: a handful of pixels, as "x,y role" pairs. Roles:
# x clears a pixel, o adds or darkens one to the outline. Froststeel's c and C are frost crystals standing off an edge
# (white, and a paler blue at their roots), r rime on an edge (frost white); voidsteel's s and S are stars (white
# violet, and a dimmer violet), t and T a glint of ender pearl teal (bright, and its deeper teal).
ACCENTS = {
    "froststeel": {"o": hexrgb(0x16254A) + (255,), "c": hexrgb(0xFFFFFF) + (255,), "C": hexrgb(0xBFE0FF) + (255,),
                   "r": hexrgb(0xF4FAFF) + (255,)},
    "voidsteel": {"o": hexrgb(0x07040D) + (255,), "s": hexrgb(0xF3E9FF) + (255,), "S": hexrgb(0xB48DF0) + (255,),
                  "t": pick(PEARL, 0.92) + (255,), "T": pick(PEARL, 0.75) + (255,)},
}
MARKS = {
    "froststeel": {
        # A crystal of frost standing proud of an edge, here and there.
        "sword": "10,2c",
        "pickaxe": "8,1c",
        "axe": "6,3c",
        "shovel": "10,2c",
        "hoe": "10,1c",
        "ingot": "10,1c",
    },
    "voidsteel": {
        # Stars down the blade (repeated along a long sword's), the pearl's glint at the point.
        "sword": "11,4s 9,6S 14,1t",
        "pickaxe": "7,3s 13,6S 13,10t",
        "axe": "8,4s 11,6S 10,2t",
        "shovel": "11,5s 12,3t",
        "hoe": "8,2s 11,4S 7,2t",
        "helmet": "9,4s 10,6S 6,5t",
        "chestplate": "7,8s 10,10S 3,3t",
        "leggings": "9,4s 10,9S 5,3t",
        "boots": "11,7s 4,9S 4,4t",
        "ingot": "5,6s 9,4S 3,9S 12,6t",
        "nugget": "7,7S 9,6t",
    },
}


def apply_marks(px, w, metal, item):
    for mark in MARKS[metal].get(item, "").split():
        xy, role = mark[:-1], mark[-1]
        x, y = (int(v) for v in xy.split(","))
        px[y * w + x] = (0, 0, 0, 0) if role == "x" else ACCENTS[metal][role]


def recolour(src, dst, fn, handles=True, marks=None, post=None):
    """A vanilla texture recoloured by fn(colour, x, y), keeping tool handles; marks: (metal, item) to mark up; post: a
    last pass over (w, h, pixels)."""
    w, h, px = vanilla(src)
    out = []
    for i, c in enumerate(px):
        if c[3] == 0:
            out.append((0, 0, 0, 0))
        elif handles and is_handle(c):
            out.append(c)
        else:
            out.append(fn(c, i % w, i // w) + (c[3],))
    if post:
        post(w, h, out)
    if marks:
        apply_marks(out, w, *marks)
    png(os.path.join(TEX, dst + ".png"), w, h, out)


# ================================================================================================ blocks and layers

_, _, PACKED_ICE = vanilla("block/packed_ice")
PACKED_ICE_PEAK = max(lum(c) for c in PACKED_ICE)


def frost_block(c, x, y):
    """Froststeel's block: iron's, with packed ice's bright streaks running through it."""
    base = ramp(FROST, lum(c) / 255.0 * 0.78)
    ice = PACKED_ICE[(y % 16) * 16 + x % 16]
    return lerp(base, hexrgb(0xF4FAFF), 0.55) if lum(ice) >= PACKED_ICE_PEAK - 12 else base


def rime(w, h, px):
    """Frost along every edge of the worn armor: each opaque pixel next to a clear one (the edges of each piece's faces
    in the layer's net) lifts toward frost white."""
    edge = []
    for y in range(h):
        for x in range(w):
            if px[y * w + x][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and px[ny * w + nx][3] == 0:
                    edge.append(y * w + x)
                    break
    for i in edge:
        px[i] = lerp(px[i][:3], hexrgb(0xF4FAFF), 0.6) + (px[i][3],)


def stars(density, seed):
    """A sparse, even scatter of stars over a texture's mid tones: no two side by side, a few dim to every bright one."""
    def post(w, h, px):
        taken = set()
        for y in range(h):
            for x in range(w):
                i = y * w + x
                if px[i][3] == 0 or lum(px[i]) < 30 or lum(px[i]) > 120:
                    continue
                v = ((x * 73856093) ^ (y * 19349663) ^ seed) % 1000
                if v >= density * 1000 or any((x + dx, y + dy) in taken for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    continue
                taken.add((x, y))
                px[i] = ACCENTS["voidsteel"]["s" if v % 4 == 0 else "S"][:3] + (px[i][3],)
    return post


def void_block():
    """Voidsteel's block: diamond's, black-purple, with a few stars and one glint of ender pearl hand-placed in it."""
    marks = "3,3s 11,2S 6,8S 12,10s 4,12S 9,13S 13,6S 8,5t"
    w, h, px = vanilla("block/diamond_block")
    # Diamond's block is bright all over: taken down a good deal to stay as dark as the tools.
    out = [ramp(VOID, lum(c) / DIAMOND_PEAK * 0.72) + (255,) for i, c in enumerate(px)]
    for mark in marks.split():
        xy, role = mark[:-1], mark[-1]
        x, y = (int(v) for v in xy.split(","))
        out[y * w + x] = ACCENTS["voidsteel"][role]
    png(os.path.join(TEX, "block", "voidsteel_block.png"), w, h, out)


# ========================================================================================================= metals

# Metal: (the vanilla metal its tools and armor are drawn from, its recolouring; its ingot and nugget's source and
# their recolouring).
METALS = OrderedDict([("froststeel", ("iron", froststeel, "iron", froststeel)),
                      ("voidsteel", ("diamond", voidsteel, "gold", voidsteel_from_gold))])
TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]


def item_model(name, parent):
    write(os.path.join(MODELS, "item", name + ".json"), OrderedDict([
        ("parent", parent), ("textures", OrderedDict(layer0="tomyen:item/" + name))]))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


def block_files(name):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": "tomyen:block/" + name}}})
    write(os.path.join(MODELS, "block", name + ".json"), OrderedDict([
        ("parent", "minecraft:block/cube_all"), ("textures", OrderedDict(all="tomyen:block/" + name))]))
    write(os.path.join(ITEMS, name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", "tomyen:block/" + name)])})
    write(os.path.join(DATA, "loot_table", "blocks", name + ".json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                                ("entries", [{"type": "minecraft:item", "name": "tomyen:" + name}]), ("rolls", 1)])]),
        ("random_sequence", "tomyen:blocks/" + name)]))


for metal, (base, fn, ingot_base, ingot_fn) in METALS.items():
    frost = settled_frost if metal == "froststeel" else None
    recolour(f"item/{ingot_base}_ingot", f"item/{metal}_ingot", ingot_fn, handles=False, marks=(metal, "ingot"), post=frost)
    recolour(f"item/{ingot_base}_nugget", f"item/{metal}_nugget", ingot_fn, handles=False, marks=(metal, "nugget"), post=frost)
    if metal == "voidsteel":
        void_block()
    else:
        recolour(f"block/{base}_block", f"block/{metal}_block", frost_block)
    frost = settled_frost if metal == "froststeel" else None
    for part in TOOLS + ARMOR:
        recolour(f"item/{base}_{part}", f"item/{metal}_{part}", fn, marks=(metal, part), post=frost)
    for layer in ("humanoid", "humanoid_leggings"):
        if metal == "froststeel":
            # Iron's layers are all pale highlights; taken a shade down they keep the glacier blue, then the rime.
            recolour(f"entity/equipment/{layer}/{base}", f"entity/equipment/{layer}/{metal}",
                     lambda c, x, y: froststeel(tuple(v * 0.78 for v in c[:3]), x, y), handles=False, post=rime)
        else:
            recolour(f"entity/equipment/{layer}/{base}", f"entity/equipment/{layer}/{metal}", voidsteel_layer, handles=False,
                     post=stars(0.03, 0x5EED if layer == "humanoid" else 0xC0DE))
    for name in [f"{metal}_ingot", f"{metal}_nugget"] + [f"{metal}_{p}" for p in ARMOR]:
        item_model(name, "minecraft:item/generated")
    for tool in TOOLS:
        item_model(f"{metal}_{tool}", "minecraft:item/handheld")
    block_files(f"{metal}_block")
    write(os.path.join(ASSETS, "equipment", metal + ".json"), {"layers": OrderedDict([
        ("humanoid", [{"texture": "tomyen:" + metal}]), ("humanoid_leggings", [{"texture": "tomyen:" + metal}])])})

# ======================================================================================================= void flux

# Glowstone dust's pile: its shadows in the ender pearl's deep teals, its light grains the pale yellow of end stone.
VOID_FLUX = [(0.0, PEARL[0]), (0.45, pick(PEARL, 0.45)), (0.75, pick(PEARL, 0.85)), (0.9, pick(END_STONE, 0.6)),
             (1.0, END_STONE[-1])]
_, _, GLOWSTONE = vanilla("item/glowstone_dust")
GLOWSTONE_RANGE = (min(lum(c) for c in GLOWSTONE if c[3]), max(lum(c) for c in GLOWSTONE if c[3]))
recolour("item/glowstone_dust", "item/void_flux",
         lambda c, x, y: ramp(VOID_FLUX, (lum(c) - GLOWSTONE_RANGE[0]) / (GLOWSTONE_RANGE[1] - GLOWSTONE_RANGE[0])), handles=False)
item_model("void_flux", "minecraft:item/generated")

# ========================================================================================================== recipes

RECIPES = os.path.join(DATA, "recipe")
ADVANCEMENTS = os.path.join(DATA, "advancement", "recipes")


def advancement(name, category, unlock_item, criterion="has_item"):
    write(os.path.join(ADVANCEMENTS, category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            (criterion, {"conditions": {"items": [{"items": unlock_item}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [[criterion, "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def shaped(name, result, pattern, key, unlock, count=1, category="misc", book=None, criterion="has_item"):
    write(os.path.join(RECIPES, name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, book or category, unlock, criterion)


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
    ingot, nugget, block = f"tomyen:{metal}_ingot", f"tomyen:{metal}_nugget", f"tomyen:{metal}_block"
    shaped(f"{metal}_block", block, ["###", "###", "###"], {"#": ingot}, ingot, category="building")
    shapeless(f"{metal}_ingot_from_{metal}_block", ingot, [block], block, 9)
    shaped(f"{metal}_ingot_from_nuggets", ingot, ["###", "###", "###"], {"#": nugget}, nugget)
    shapeless(f"{metal}_nugget", nugget, [ingot], ingot, 9)
    for tool, pattern in TOOL_PATTERNS.items():
        # Weapons file under combat in the recipe book, the rest under tools (equipment); both are equipment recipes.
        shaped(f"{metal}_{tool}", f"tomyen:{metal}_{tool}", pattern, {"X": ingot, "#": "minecraft:stick"}, ingot,
               category="equipment", book="combat" if tool == "sword" else "equipment")
    for piece, pattern in ARMOR_PATTERNS.items():
        shaped(f"{metal}_{piece}", f"tomyen:{metal}_{piece}", pattern, {"X": ingot}, ingot, category="equipment", book="combat")
shapeless("void_flux", "tomyen:void_flux", ["minecraft:ender_pearl", "minecraft:end_stone"], "minecraft:ender_pearl")

# Long swords: three of the metal's swords on the diagonal, as vanilla tiers' are (data/tomyen/recipe/iron_long_sword.json).
LONG_SWORD_METALS = ["magmasteel", "soul_steel", "froststeel", "voidsteel"]
for metal in LONG_SWORD_METALS:
    sword = f"tomyen:{metal}_sword"
    shaped(f"{metal}_long_sword", f"tomyen:{metal}_long_sword", ["S  ", " S ", "  S"], {"S": sword}, sword,
           category="equipment", book="combat", criterion=f"has_{metal}_sword")

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
    merge_tag(os.path.join(DATA, "tags", "item", f"repairs_{metal}.json"), [f"tomyen:{metal}_ingot"])
for kind in TOOLS:
    merge_tag(os.path.join(TAGS, "item", kind + "s.json"), [f"tomyen:{m}_{kind}" for m in METALS])
for piece, tag in zip(ARMOR, ["head_armor", "chest_armor", "leg_armor", "foot_armor"]):
    merge_tag(os.path.join(TAGS, "item", tag + ".json"), [f"tomyen:{m}_{piece}" for m in METALS])
merge_tag(os.path.join(TAGS, "item", "beacon_payment_items.json"), [f"tomyen:{m}_ingot" for m in METALS])
blocks = [f"tomyen:{m}_block" for m in METALS]
merge_tag(os.path.join(TAGS, "block", "mineable", "pickaxe.json"), blocks)
merge_tag(os.path.join(TAGS, "block", "needs_iron_tool.json"), ["tomyen:froststeel_block"])
# Voidsteel's block takes a diamond pickaxe, as netherite's does.
merge_tag(os.path.join(TAGS, "block", "needs_diamond_tool.json"), ["tomyen:voidsteel_block"])
merge_tag(os.path.join(TAGS, "block", "beacon_base_blocks.json"), blocks)
# Long swords are swords (sweeping, cobwebs, every sword enchantment) through tomyen:long_swords.
merge_tag(os.path.join(DATA, "tags", "item", "long_swords.json"), [f"tomyen:{m}_long_sword" for m in LONG_SWORD_METALS])

# ============================================================================================================= lang

LANG = OrderedDict()
NAMES = {"froststeel": "Froststeel", "voidsteel": "Voidsteel"}
for metal, title in NAMES.items():
    LANG[f"item.tomyen.{metal}_ingot"] = f"{title} Ingot"
    LANG[f"item.tomyen.{metal}_nugget"] = f"{title} Nugget"
    LANG[f"block.tomyen.{metal}_block"] = f"Block of {title}"
    for part in TOOLS + ARMOR:
        LANG[f"item.tomyen.{metal}_{part}"] = f"{title} {part.capitalize()}"
for metal, title in (("magmasteel", "Magmasteel"), ("soul_steel", "Soul Steel"), ("froststeel", "Froststeel"),
                     ("voidsteel", "Voidsteel")):
    LANG[f"item.tomyen.{metal}_long_sword"] = f"{title} Long Sword"
LANG.update({
    "item.tomyen.froststeel.chills": "Hits slow and freeze",
    "item.tomyen.froststeel.shatters": "Shatters frozen targets: +2 hearts",
    "item.tomyen.froststeel.armor_piece": "Each piece: -20% freeze damage",
    "item.tomyen.froststeel.armor_set": "Full set: can't freeze, walk on powder snow and freeze water underfoot",
    "item.tomyen.voidsteel.vault": "Sends what it mines to your bank vault",
    "item.tomyen.voidsteel.blink": "Sneak-use to blink ahead",
    "item.tomyen.voidsteel.armor_piece": "Each piece: 5% chance projectiles pass through you",
    "item.tomyen.voidsteel.armor_set": "Full set: the void returns you to solid ground (every 5 minutes)",
    "item.tomyen.void_flux": "Void Flux",
    "message.tomyen.voidsteel.rescued": "The void spits you back out",
    "container.tomyen.foundry.temperature_max": "Temperature: %s°C (no hotter than %s°C)",
})
lang_path = os.path.join(ASSETS, "lang", "en_us.json")
with open(lang_path) as f:
    lang = json.load(f, object_pairs_hook=OrderedDict)
lang.update(LANG)
with open(lang_path, "w") as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)
    f.write("\n")
print("frost and void metals done")
