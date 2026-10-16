"""Builds silver and holy silver: every texture, block state, model and item definition, the Holy Silver armor's
equipment layers, and on the data side the loot tables, recipes (with recipe-book advancements), ore worldgen and the
tags the generator owns. With --preview PATH it also draws a sheet putting each texture beside its closest vanilla
ones, to check they read as their own thing at a glance.

The art is built the vanilla way from vanilla's own textures, read from the Minecraft client jar (see
gen_longswords.client_jar()): ores are vanilla stone and deepslate with silver veined through them; the swords, tools,
armor and armor layers are vanilla iron's, their shading kept and their metal recoloured; the raw silver, ingot,
nugget and blocks are drawn here in vanilla's palette structure and lighting (top-left light, a dark rim at the lower
right). What sets silver apart from iron at a glance:

  * cool white-silver with blue shadows, where iron is neutral grey;
  * ore: thin bright veins and flakes running on the diagonal, not iron's round blobs;
  * raw silver: a cluster of rounded beads, not a single rough lump; the raw block is the same beads packed tight;
  * ingot: a square-ended bullion bar seen from the front, with a hallmark stamped in its top face, where vanilla's
    ingots lie on the diagonal; nugget: three beads;
  * block: polished, with a bevelled rim and a diagonal sheen, not iron's stacked plates;
  * the silver sword's grip is wrapped in dark blue leather.

Holy silver is warm white rather than cool, with a few deliberate lines of gold: an outline, a cross where silver has
its hallmark, gilded hilts, and on the worn armor a brow band, a chest cross, a belt and boot cuffs. Its ingot,
nugget, armor and rounds carry a small golden sparkle in the corner.

Usage: python3 tools/gen_silver.py src/main/resources/assets/tomyen [--preview out.png]"""
import json, os, struct, sys, zipfile
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gen_longswords import client_jar, displays as long_sword_displays, long_sword, read_png
from gunkit import png

ASSETS = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else None
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
CLEAR = (0, 0, 0, 0)

# ======================================================================================================= palettes

# Silver: darkest (outline) to white. Cool, with blue in the shadows.
SILVER = [(38, 42, 58), (66, 74, 96), (100, 110, 134), (138, 149, 170), (176, 186, 204), (210, 218, 232), (236, 241, 249),
          (255, 255, 255)]
# Raw silver: a touch duller and darker, tarnished.
RAW = [(40, 42, 54), (64, 69, 84), (94, 101, 118), (126, 134, 151), (160, 168, 184), (196, 203, 216), (228, 233, 241),
       (250, 252, 255)]
# Holy silver: warm white, no blue.
HOLY = [(92, 86, 84), (138, 134, 132), (176, 174, 172), (206, 205, 202), (228, 228, 224), (243, 243, 238), (252, 252, 247),
        (255, 255, 255)]
# Gold filigree, from vanilla gold's own ramp, darkest to brightest.
GOLD = [(117, 56, 4), (178, 104, 17), (220, 152, 19), (246, 202, 52), (253, 236, 104), (255, 250, 200)]
# The silver sword's grip: dark blue leather.
LEATHER = [(18, 20, 36), (30, 36, 66), (46, 56, 98), (66, 80, 134)]
SPARKLE_CORE, SPARKLE_ARM = (255, 252, 226, 255), (250, 214, 92, 255)


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def saturation(c):
    hi, lo = max(c[:3]), min(c[:3])
    return 0.0 if hi == 0 else (hi - lo) / hi


# ======================================================================================================= vanilla

JAR = None


def vanilla(path):
    """A vanilla texture's (w, h, pixels). Also honours tRNS on greyscale and RGB PNGs (vanilla's iron ingot and
    chestplate are greyscale with a transparent grey), which read_png doesn't."""
    data = JAR.read("assets/minecraft/textures/" + path + ".png")
    w, h, px = read_png(data)
    colour, trns, pos = None, None, 8
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            colour = body[9]
        elif kind == b"tRNS":
            trns = body
        pos += 12 + n
    if trns is not None and colour == 0:
        grey = struct.unpack(">H", trns[:2])[0]
        px = [CLEAR if p[:3] == (grey,) * 3 else p for p in px]
    elif trns is not None and colour == 2:
        key = struct.unpack(">HHH", trns[:6])
        px = [CLEAR if p[:3] == key else p for p in px]
    return w, h, px


def mod_texture(path):
    with open(os.path.join(ASSETS, "textures", path + ".png"), "rb") as f:
        return read_png(f.read())


# ======================================================================================================= helpers


def ramp_map(px, pick, ramp, lo=0, hi=None):
    """Recolours the picked pixels onto ramp[lo..hi], keeping their shading: each distinct brightness among them takes
    the ramp entry at the same rank, so vanilla's light and shadow fall where they did."""
    hi = len(ramp) - 1 if hi is None else hi
    levels = sorted({round(lum(c)) for c in px if c[3] and pick(c)})
    out = list(px)
    for i, c in enumerate(px):
        if c[3] and pick(c):
            rank = levels.index(round(lum(c)))
            t = rank / max(1, len(levels) - 1)
            out[i] = rgba(ramp[lo + round(t * (hi - lo))], c[3])
    return out


def is_grey(c):
    return saturation(c) < 0.12


def neighbours(w, h, x, y):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if 0 <= x + dx < w and 0 <= y + dy < h:
            yield x + dx, y + dy
        else:
            yield None


def edge(px, w, h, x, y):
    """Whether an opaque pixel touches transparency (or the canvas edge)."""
    for n in neighbours(w, h, x, y):
        if n is None or px[n[1] * w + n[0]][3] == 0:
            return True
    return False


def holy_trim(px, w, h, pick):
    """Holy silver from vanilla metal: the picked pixels onto the warm white ramp, and their outline (the darkest
    pixels on the silhouette's edge) one line of gold."""
    out = ramp_map(px, pick, HOLY, 2, 7)
    darkest = min((lum(c) for c in px if c[3] and pick(c)), default=0)
    for y in range(h):
        for x in range(w):
            c = px[y * w + x]
            if c[3] and pick(c) and lum(c) <= darkest + 1 and edge(px, w, h, x, y):
                out[y * w + x] = rgba(GOLD[1])
    return out


def sparkle(px, w, x, y):
    """A small four-pointed golden sparkle centred at (x, y): holy silver's mark on every item icon."""
    out = list(px)
    for dx, dy, c in ((0, 0, SPARKLE_CORE), (1, 0, SPARKLE_ARM), (-1, 0, SPARKLE_ARM), (0, 1, SPARKLE_ARM), (0, -1, SPARKLE_ARM)):
        if out[(y + dy) * w + x + dx][3] == 0:
            out[(y + dy) * w + x + dx] = c
    return out


def drawn(rows, palette, extra=None):
    """A texture drawn as text: '.' clear, digits index the palette, other letters come from extra."""
    px = []
    for row in rows:
        assert len(row) == 16, row
        for ch in row:
            if ch == ".":
                px.append(CLEAR)
            elif ch.isdigit():
                px.append(rgba(palette[int(ch)]))
            else:
                px.append(rgba(extra[ch]))
    assert len(px) == 256
    return px


# ======================================================================================================= ores

# Veins over stone: '.' leaves the stone, a-f are the vein's tones (a its blue-black shadow on the stone below it, f
# a white glint). They run up and to the right in thin streaks with flakes between, where iron sits in round blobs.
VEINS = [
    "................",
    "...........f....",
    "..........ed....",
    "...e.....dc.....",
    "..edb...dcb.....",
    ".dcb...cba......",
    ".ba....ba.......",
    "............e...",
    "...........edb..",
    ".....f....dcb...",
    "....edb..dcb..e.",
    "...dcb..cba..edb",
    "...ba.........ba",
    ".......e........",
    "......edb.......",
    ".....dcba.......",
]
VEIN_TONES = {"a": (50, 56, 76), "b": (104, 114, 138), "c": (164, 174, 194), "d": (208, 216, 230), "e": (236, 241, 250),
              "f": (255, 255, 255)}
# Deepslate is darker: the shadow goes darker with it.
DEEP_VEIN_TONES = dict(VEIN_TONES, a=(30, 33, 46), b=(84, 92, 114))


def ore(base, tones, shift=(0, 0)):
    w, h, px = vanilla("block/" + base)
    out = list(px)
    for y in range(16):
        for x in range(16):
            ch = VEINS[(y - shift[1]) % 16][(x - shift[0]) % 16]
            if ch != ".":
                out[y * 16 + x] = rgba(tones[ch])
    return out


# ======================================================================================================= raw silver


def beads(lumps, palette, glints=True, tile=False):
    """Rounded beads, lit from the upper left: lumps are (cx, cy, r). Each pixel belongs to the bead standing highest
    there; a bead's lower right rim and the crease where one tucks under another go dark, and each gets one glint.
    Shading is a smooth fall across each bead, no noise. Tiled, the beads wrap round the edges and fill the square."""
    def offset(a, b):
        d = a - b
        return (d + 8) % 16 - 8 if tile else d

    owner = {}
    for y in range(16):
        for x in range(16):
            best = None
            for i, (cx, cy, r) in enumerate(lumps):
                d = (offset(x + 0.5, cx) ** 2 + offset(y + 0.5, cy) ** 2) ** 0.5
                height = r - d
                if (height > 0 or tile) and (best is None or height + 0.15 * r > best[0]):
                    best = (height + 0.15 * r, i)
            if best:
                owner[(x, y)] = best[1]

    def at(x, y):
        return owner.get((x % 16, y % 16)) if tile else owner.get((x, y))

    px = [CLEAR] * 256
    for (x, y), i in owner.items():
        cx, cy, r = lumps[i]
        # Light from the upper left: -1 lit, +1 shadowed.
        lit = (offset(x + 0.5, cx) + offset(y + 0.5, cy)) / (r * 1.5)
        tone = round(4.2 - lit * 2.6)
        below, right = at(x, y + 1), at(x + 1, y)
        if below is None or right is None:
            tone = 0 if (below is None and right is None) or lit > 0.1 else min(tone, 2)
        elif below != i or right != i:
            tone = min(tone, 1)
        px[y * 16 + x] = rgba(palette[max(0, min(len(palette) - 2, tone))])
    if glints:
        for i, (cx, cy, r) in enumerate(lumps):
            gx, gy = int(cx - r * 0.45) % 16, int(cy - r * 0.45) % 16
            if at(gx, gy) == i:
                px[gy * 16 + gx] = rgba(palette[-1])
    return px


RAW_LUMPS = [(6.5, 9.5, 4.3), (10.6, 6.2, 3.4), (11.8, 11.4, 2.7), (4.0, 4.8, 2.6)]
NUGGET_LUMPS = [(7.0, 7.4, 2.3), (9.6, 9.4, 1.9), (5.6, 10.0, 1.7)]
# The raw block: big beads packed edge to edge, wrapping round so the block tiles.
RAW_BLOCK_LUMPS = [(4.0, 4.0, 4.6), (12.0, 3.0, 4.2), (8.0, 10.0, 5.0), (15.5, 9.5, 4.2), (2.0, 13.5, 4.0), (12.5, 15.0, 4.0)]


def raw_block():
    """Raw silver's beads, packed into a block."""
    return beads(RAW_BLOCK_LUMPS, RAW, tile=True)


# ======================================================================================================= ingot, nugget, blocks

# A bullion bar from the front and a little above: a top face narrowing to the back between chamfered ends, with
# the hallmark ('h' its stamped shadow, 'l' the lit lip beside it), a bright edge, and a front face shading from left
# to right.
INGOT = [
    "................",
    "................",
    "................",
    "................",
    "....11111111....",
    "...1456666541...",
    "..145666h666541.",
    ".14566666l666541",
    "1777777777777661",
    "1555544444433331",
    "1444433333332221",
    "1333322222221110",
    ".00000000000000.",
    "................",
    "................",
    "................",
]
INGOT_TONES = {"h": SILVER[3], "l": SILVER[7]}

# Holy silver's: the same bar rimmed in gold, a gold cross where the hallmark was and a gold band along its edge.
HOLY_INGOT = [
    "................",
    "................",
    "................",
    "................",
    "....GGGGGGGG....",
    "...G4566c6654G..",
    "..G4566ccc6654G.",
    ".G456666c666654G",
    "GggggggggggggggG",
    "G555544444433330",
    "G444433333332220",
    "G333322222221110",
    ".GGGGGGGGGGGGGG.",
    "................",
    "................",
    "................",
]
HOLY_INGOT_TONES = {"G": GOLD[1], "g": GOLD[3], "c": GOLD[4]}


def ingot(rows, palette, tones):
    return drawn(rows, palette, tones)


def silver_block(holy):
    """A stack of cast bars, laid like bricks: each lit along its top edge and left end, dark along the bottom and
    right, its face falling from light to shade, with a hallmark. Unlike iron's plain plates. Holy silver's bars are
    white, laid in gold."""
    pal = HOLY if holy else SILVER
    px = []
    for y in range(16):
        for x in range(16):
            row, by = y // 4, y % 4
            bx = (x + (4 if row % 2 else 0)) % 8
            if by == 3 or bx == 7:
                t = -1 if holy else (1 if by == 3 and bx == 7 else 2)
            elif by == 0:
                t = 7 if bx == 0 else 6
            elif bx == 0:
                t = 6
            elif bx == 6:
                t = 3 if by == 2 else 4
            else:
                t = 5 if by == 1 else 4
            if t >= 0 and by == 2 and bx == 3:
                t = 3   # the hallmark
            px.append(rgba(GOLD[2] if t == -1 and by == 3 else GOLD[1] if t == -1 else pal[t]))
    return px


# ======================================================================================================= weapons, tools, armor



def silver_sword():
    """Iron's sword in silver, its grip wrapped in dark blue leather."""
    w, h, px = vanilla("item/iron_sword")
    out = ramp_map(px, is_grey, SILVER)
    return ramp_map(out, lambda c: c[3] and not is_grey(c), LEATHER)


def holy_metal(path):
    """A vanilla iron item in holy silver: white metal edged in gold, its wooden parts gilded, with the sparkle."""
    w, h, px = vanilla(path)
    out = holy_trim(px, w, h, is_grey)
    out = ramp_map(out, lambda c: c[3] and not is_grey(c) and c not in [rgba(g) for g in GOLD], GOLD, 0, 4)
    return out


def holy_tool(path):
    """A vanilla iron tool in holy silver: white head edged in gold; the wooden handle stays wood."""
    w, h, px = vanilla(path)
    return holy_trim(px, w, h, is_grey)


def holy_armor_layer(path):
    """An iron armor layer in holy silver: smooth white plate on five tones, and a few lines of gold: a band round the
    helmet's brow, a cross on the chest, the boots' cuffs and the leggings' belt."""
    w, h, px = vanilla(path)
    out = ramp_map(px, lambda c: True, HOLY, 2, 6)

    def gild(x, y, tone=2):
        if out[y * w + x][3]:
            out[y * w + x] = rgba(GOLD[tone])

    def top_edge(x0, x1, y0, y1):
        # The first opaque texel down each column of a region: the piece's upper rim there.
        for x in range(x0, x1):
            for y in range(y0, y1):
                if out[y * w + x][3]:
                    gild(x, y)
                    break

    if path.endswith("humanoid/iron"):
        # Player-model layout: the head's four sides are y 8-15, the body's front x 20-27 y 20-31, the legs x 0-15.
        for x in range(0, 32):
            gild(x, 8)
        for y in range(22, 30):
            gild(23, y, 3)
            gild(24, y, 2)
        for x in range(21, 27):
            gild(x, 24, 3)
        top_edge(0, 16, 20, 32)
    elif path.endswith("humanoid_leggings/iron"):
        top_edge(16, 40, 16, 32)
    return out


# ======================================================================================================= rounds and the crucifix

COPPER_TIP = lambda c: c[0] > c[1] * 1.35 and c[0] > 70           # the FMJ's copper jacket, not its brass case
RED_HULL = lambda c: c[0] > c[1] * 2.0                            # the buckshot shell's red hull


def silver_round(name, pick, holy):
    w, h, px = mod_texture("item/" + name)
    out = ramp_map(px, pick, HOLY if holy else SILVER, 2 if holy else 1, 7 if holy else 6)
    return sparkle(out, w, 3, 3) if holy else out


def holy_crucifix(name):
    """The gold crucifix in holy silver: its gold goes white, the deepest gold staying as filigree."""
    w, h, px = mod_texture("item/" + name)
    gold = lambda c: c[3] and saturation(c) > 0.25 and c[0] > c[2] * 1.4 and abs(c[0] - c[1]) < 90 and lum(c) > 60
    levels = sorted({round(lum(c)) for c in px if gold(c)})
    keep = set(levels[:max(1, len(levels) // 4)])
    return ramp_map(px, lambda c: gold(c) and round(lum(c)) not in keep, HOLY, 3, 7), w, h


# ======================================================================================================= all textures


def textures():
    """Every texture: {path under textures/: (w, h, pixels)}."""
    t = OrderedDict()
    t["block/silver_ore"] = (16, 16, ore("stone", VEIN_TONES))
    t["block/deepslate_silver_ore"] = (16, 16, ore("deepslate", DEEP_VEIN_TONES, (5, 7)))
    t["block/raw_silver_block"] = (16, 16, raw_block())
    t["block/silver_block"] = (16, 16, silver_block(False))
    t["block/holy_silver_block"] = (16, 16, silver_block(True))
    t["item/raw_silver"] = (16, 16, beads(RAW_LUMPS, RAW))
    t["item/silver_ingot"] = (16, 16, ingot(INGOT, SILVER, INGOT_TONES))
    t["item/holy_silver_ingot"] = (16, 16, sparkle(ingot(HOLY_INGOT, HOLY, HOLY_INGOT_TONES), 16, 13, 2))
    t["item/silver_nugget"] = (16, 16, beads(NUGGET_LUMPS, SILVER))
    t["item/holy_silver_nugget"] = (16, 16, sparkle(holy_nugget(), 16, 11, 4))
    t["item/silver_sword"] = (16, 16, silver_sword())
    t["item/holy_silver_sword"] = (16, 16, holy_metal("item/iron_sword"))
    for tool in ("shovel", "pickaxe", "axe", "hoe"):
        t["item/holy_silver_" + tool] = (16, 16, holy_tool("item/iron_" + tool))
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        t["item/holy_silver_" + piece] = (16, 16, sparkle(holy_tool("item/iron_" + piece), 16, 13, 2))
    for layer in ("humanoid", "humanoid_leggings", "humanoid_baby"):
        w, h, _ = vanilla(f"entity/equipment/{layer}/iron")
        t[f"entity/equipment/{layer}/holy_silver"] = (w, h, holy_armor_layer(f"entity/equipment/{layer}/iron"))
    t["item/bullet_556_silver"] = (16, 16, silver_round("bullet_556_fmj", COPPER_TIP, False))
    t["item/bullet_556_holy_silver"] = (16, 16, silver_round("bullet_556_fmj", COPPER_TIP, True))
    t["item/shell_12g_silver_buckshot"] = (16, 16, silver_round("shell_12g_buckshot", RED_HULL, False))
    t["item/shell_12g_holy_silver_buckshot"] = (16, 16, silver_round("shell_12g_buckshot", RED_HULL, True))
    for name in ("crucifix", "crucifix_parts"):
        px, w, h = holy_crucifix(name)
        t["item/holy_silver_" + name] = (w, h, px)
    for name in ("silver_sword", "holy_silver_sword"):
        t["item/" + name.replace("sword", "long_sword")] = (32, 32, long_sword(t["item/" + name][2]))
    return t


def holy_nugget():
    px = beads(NUGGET_LUMPS, HOLY[1:], glints=False)
    out = list(px)
    for y in range(16):
        for x in range(16):
            if px[y * 16 + x][3] and edge(px, 16, 16, x, y) and (y * 16 + x + 16 >= 256 or px[y * 16 + x + 16][3] == 0
                                                                   or x == 15 or px[y * 16 + x + 1][3] == 0):
                out[y * 16 + x] = rgba(GOLD[1])
    for cx, cy, r in NUGGET_LUMPS:
        out[int(cy - r * 0.45) * 16 + int(cx - r * 0.45)] = rgba(GOLD[5])
    return out


# ======================================================================================================= preview


def preview(path, tex):
    """Each of ours beside the closest vanilla ones, 6x, on a mid grey; vanilla on the left of each row."""
    rows = [
        (["block/stone", "block/iron_ore", "block/gold_ore", "block/copper_ore", "block/diamond_ore"], ["block/silver_ore"]),
        (["block/deepslate", "block/deepslate_iron_ore", "block/deepslate_gold_ore", "block/deepslate_copper_ore"],
         ["block/deepslate_silver_ore"]),
        (["item/raw_iron", "item/raw_gold", "item/raw_copper", "block/raw_iron_block"], ["item/raw_silver", "block/raw_silver_block"]),
        (["item/iron_ingot", "item/gold_ingot", "item/copper_ingot", "item/netherite_ingot"], ["item/silver_ingot", "item/holy_silver_ingot"]),
        (["item/iron_nugget", "item/gold_nugget", "item/copper_nugget"], ["item/silver_nugget", "item/holy_silver_nugget"]),
        (["block/iron_block", "block/gold_block", "block/quartz_block_side"], ["block/silver_block", "block/holy_silver_block"]),
        (["item/iron_sword", "item/golden_sword", "item/iron_pickaxe", "item/golden_pickaxe"],
         ["item/silver_sword", "item/holy_silver_sword", "item/holy_silver_pickaxe", "item/holy_silver_axe",
          "item/holy_silver_shovel", "item/holy_silver_hoe"]),
        (["item/iron_helmet", "item/iron_chestplate", "item/golden_chestplate"],
         ["item/holy_silver_helmet", "item/holy_silver_chestplate", "item/holy_silver_leggings", "item/holy_silver_boots"]),
        (["mod:item/bullet_556_fmj", "mod:item/shell_12g_buckshot", "mod:item/crucifix"],
         ["item/bullet_556_silver", "item/bullet_556_holy_silver", "item/shell_12g_silver_buckshot",
              "item/shell_12g_holy_silver_buckshot", "item/holy_silver_crucifix"]),
        ([], ["item/silver_long_sword", "item/holy_silver_long_sword"]),
        (["entity/equipment/humanoid/iron"], ["entity/equipment/humanoid/holy_silver"]),
        (["entity/equipment/humanoid_leggings/iron"], ["entity/equipment/humanoid_leggings/holy_silver", "item/holy_silver_crucifix_parts"]),
    ]
    scale, pad = 6, 8

    def image(name):
        if name.startswith("mod:"):
            return mod_texture(name[4:])
        if name in tex:
            return tex[name]
        return vanilla(name)

    laid, width, height = [], 0, pad
    for left, right in rows:
        x, row_h, cells = pad, 0, []
        for i, name in enumerate(left + right):
            w, h, px = image(name)
            sc = scale if w <= 32 else 3
            if i == len(left) and left:
                x += pad * 3
            cells.append((x, w, h, px, sc, i >= len(left)))
            x += w * sc + pad
            row_h = max(row_h, h * sc)
        laid.append((height, cells))
        width, height = max(width, x), height + row_h + pad
    out = [(96, 96, 104, 255)] * (width * height)
    for top, cells in laid:
        for x0, w, h, px, sc, ours in cells:
            if ours:
                for yy in range(top - 2, top + h * sc + 2):
                    for xx in range(x0 - 2, x0 + w * sc + 2):
                        out[yy * width + xx] = (120, 112, 80, 255)
            for y in range(h * sc):
                for x in range(w * sc):
                    c = px[(y // sc) * w + x // sc]
                    if c[3] == 0:
                        c = (139, 139, 147, 255) if ((x // (sc * 2)) + (y // (sc * 2))) % 2 else (128, 128, 136, 255)
                    out[(top + y) * width + x0 + x] = c
    png(path, width, height, out)
    print("preview", path)


# ======================================================================================================= main


def main():
    global JAR
    if not ASSETS:
        sys.exit(__doc__.rsplit("Usage: ", 1)[1])
    # client_jar() takes a jar path from the second argument; ours is the assets folder alone.
    sys.argv[2:] = []
    JAR = zipfile.ZipFile(client_jar())
    tex = textures()
    for name, (w, h, px) in tex.items():
        png(os.path.join(ASSETS, "textures", name + ".png"), w, h, px)
    if PREVIEW:
        preview(PREVIEW, tex)
    write_assets()
    write_data()
    print("silver done")


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


def tag(path, values):
    """Adds values to a tag file shared with other generators and hand edits, keeping what's there."""
    existing = []
    if os.path.exists(path):
        with open(path) as f:
            existing = json.load(f)["values"]
    write(path, {"values": existing + [v for v in values if v not in existing]})


def m(name):
    return "tomyen:" + name


BLOCKS = ["silver_ore", "deepslate_silver_ore", "raw_silver_block", "silver_block", "holy_silver_block"]
GENERATED = ["raw_silver", "silver_ingot", "silver_nugget", "holy_silver_ingot", "holy_silver_nugget", "bullet_556_silver",
             "bullet_556_holy_silver", "shell_12g_silver_buckshot", "shell_12g_holy_silver_buckshot", "holy_silver_helmet",
             "holy_silver_chestplate", "holy_silver_leggings", "holy_silver_boots"]
HANDHELD = ["silver_sword", "holy_silver_sword", "holy_silver_shovel", "holy_silver_pickaxe", "holy_silver_axe", "holy_silver_hoe"]
LONG_SWORDS = ["silver_long_sword", "holy_silver_long_sword"]
ARMOR = ["holy_silver_helmet", "holy_silver_chestplate", "holy_silver_leggings", "holy_silver_boots"]
TOOLS = ["holy_silver_shovel", "holy_silver_pickaxe", "holy_silver_axe", "holy_silver_hoe"]

LANG = OrderedDict([
    ("block.tomyen.silver_ore", "Silver Ore"),
    ("block.tomyen.deepslate_silver_ore", "Deepslate Silver Ore"),
    ("block.tomyen.raw_silver_block", "Block of Raw Silver"),
    ("block.tomyen.silver_block", "Block of Silver"),
    ("block.tomyen.holy_silver_block", "Block of Holy Silver"),
    ("item.tomyen.raw_silver", "Raw Silver"),
    ("item.tomyen.silver_ingot", "Silver Ingot"),
    ("item.tomyen.silver_nugget", "Silver Nugget"),
    ("item.tomyen.holy_silver_ingot", "Holy Silver Ingot"),
    ("item.tomyen.holy_silver_nugget", "Holy Silver Nugget"),
    ("item.tomyen.silver_sword", "Silver Sword"),
    ("item.tomyen.silver_long_sword", "Silver Long Sword"),
    ("item.tomyen.holy_silver_sword", "Holy Silver Sword"),
    ("item.tomyen.holy_silver_long_sword", "Holy Silver Long Sword"),
    ("item.tomyen.holy_silver_shovel", "Holy Silver Shovel"),
    ("item.tomyen.holy_silver_pickaxe", "Holy Silver Pickaxe"),
    ("item.tomyen.holy_silver_axe", "Holy Silver Axe"),
    ("item.tomyen.holy_silver_hoe", "Holy Silver Hoe"),
    ("item.tomyen.holy_silver_helmet", "Holy Silver Helmet"),
    ("item.tomyen.holy_silver_chestplate", "Holy Silver Chestplate"),
    ("item.tomyen.holy_silver_leggings", "Holy Silver Leggings"),
    ("item.tomyen.holy_silver_boots", "Holy Silver Boots"),
    ("item.tomyen.holy_silver_crucifix", "Holy Silver Crucifix"),
    ("item.tomyen.bullet_556_silver", "5.56mm Silver"),
    ("item.tomyen.bullet_556_holy_silver", "5.56mm Holy Silver"),
    ("item.tomyen.shell_12g_silver_buckshot", "12 Gauge Silver Buckshot"),
    ("item.tomyen.shell_12g_holy_silver_buckshot", "12 Gauge Holy Silver Buckshot"),
    ("item.tomyen.evil_bane", "\u00d7%s damage to the undead and the unholy"),
    ("item.tomyen.evil_bane.holy_fire", "Sets them alight with holy fire"),
    ("item.tomyen.holy_silver_armor.piece", "Each piece wards off %s%% of evil damage"),
    ("item.tomyen.holy_silver_armor.full_set",
     "Full set: immune to evil, smites evil attackers and cures Bleeding"),
    ("item.tomyen.silver.consecrate", "Drop it in crucifix light to consecrate it"),
    ("subtitles.tomyen.holy_silver_consecrate", "Silver is consecrated"),
    ("subtitles.tomyen.holy_silver_ward", "Holy silver wards off evil"),
    ("subtitles.tomyen.item.armor.equip_holy_silver", "Holy silver armor clinks"),
])


def sound(name, file, pitch, volume=1.0):
    return OrderedDict([("name", "minecraft:" + file), ("pitch", pitch), ("volume", volume)])


SOUNDS = OrderedDict([
    # A rising swell, like a choir drawing breath, and a bell's ring under it.
    ("holy_silver_consecrate", OrderedDict([("subtitle", "subtitles.tomyen.holy_silver_consecrate"), ("sounds", [
        sound("holy_silver_consecrate", "block/conduit/activate", 1.35, 0.9),
        sound("holy_silver_consecrate", "block/beacon/activate", 1.7, 0.8)])])),
    # A high crystal ring as the blow glances off.
    ("holy_silver_ward", OrderedDict([("subtitle", "subtitles.tomyen.holy_silver_ward"), ("sounds", [
        sound("holy_silver_ward", f"block/amethyst/resonate{i}", 1.7, 0.9) for i in range(1, 5)])])),
    ("item.armor.equip_holy_silver", OrderedDict([("subtitle", "subtitles.tomyen.item.armor.equip_holy_silver"), ("sounds", [
        sound("equip", f"item/armor/equip_gold{i}", 1.25) for i in range(1, 7)])])),
])


def write_assets():
    for name in BLOCKS:
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": m("block/" + name)}}})
        write(os.path.join(ASSETS, "models", "block", name + ".json"),
              OrderedDict([("parent", "minecraft:block/cube_all"), ("textures", {"all": m("block/" + name)})]))
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"),
                                                                                   ("model", m("block/" + name))])})
    for parent, names in (("minecraft:item/generated", GENERATED), ("minecraft:item/handheld", HANDHELD)):
        for name in names:
            write(os.path.join(ASSETS, "models", "item", name + ".json"),
                  OrderedDict([("parent", parent), ("textures", {"layer0": m("item/" + name)})]))
            write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"),
                                                                                       ("model", m("item/" + name))])})
    ds = long_sword_displays()
    for name in LONG_SWORDS:
        write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([
            ("parent", "minecraft:item/handheld"), ("textures", OrderedDict(layer0=m("item/" + name))), ("display", ds)]))
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"),
                                                                                   ("model", m("item/" + name))])})

    # The holy silver crucifix: the crucifix's models with the holy silver textures.
    parts = OrderedDict([("parts", m("item/holy_silver_crucifix_parts")), ("particle", m("item/holy_silver_crucifix_parts"))])
    write(os.path.join(ASSETS, "models", "item", "holy_silver_crucifix.json"),
          OrderedDict([("parent", m("item/crucifix")), ("textures", parts)]))
    write(os.path.join(ASSETS, "models", "item", "holy_silver_crucifix_raised.json"),
          OrderedDict([("parent", m("item/crucifix_raised")), ("textures", parts)]))
    write(os.path.join(ASSETS, "models", "item", "holy_silver_crucifix_icon.json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": m("item/holy_silver_crucifix")})]))
    with open(os.path.join(ASSETS, "items", "crucifix.json")) as f:
        crucifix = f.read()
    with open(os.path.join(ASSETS, "items", "holy_silver_crucifix.json"), "w") as f:
        f.write(crucifix.replace("tomyen:item/crucifix", "tomyen:item/holy_silver_crucifix"))

    write(os.path.join(ASSETS, "equipment", "holy_silver.json"), {"layers": OrderedDict(
        (layer, [{"texture": m("holy_silver")}]) for layer in ("humanoid", "humanoid_baby", "humanoid_leggings"))})
    merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)
    merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


def write_data():
    data = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
    mine, vanilla_tags, c_tags = os.path.join(data, "tomyen"), os.path.join(data, "minecraft", "tags"), os.path.join(data, "c", "tags")

    # --- Loot: the ores drop raw silver (more with Fortune), the blocks themselves ---------------------------------------
    for ore_name in ("silver_ore", "deepslate_silver_ore"):
        write(os.path.join(mine, "loot_table", "blocks", ore_name + ".json"), OrderedDict([
            ("type", "minecraft:block"),
            ("pools", [OrderedDict([("entries", [OrderedDict([("type", "minecraft:alternatives"), ("children", [
                OrderedDict([("type", "minecraft:item"), ("condition", "minecraft:tool/can_silk_touch"), ("name", m(ore_name))]),
                OrderedDict([("type", "minecraft:item"), ("modifier", [
                    {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"type": "minecraft:explosion_decay"}]), ("name", m("raw_silver"))])])])]), ("rolls", 1)])]),
            ("random_sequence", m("blocks/" + ore_name))]))
    for block in ("raw_silver_block", "silver_block", "holy_silver_block"):
        write(os.path.join(mine, "loot_table", "blocks", block + ".json"), OrderedDict([
            ("type", "minecraft:block"),
            ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                                    ("entries", [{"type": "minecraft:item", "name": m(block)}]), ("rolls", 1)])]),
            ("random_sequence", m("blocks/" + block))]))

    # --- Recipes and their recipe-book advancements -----------------------------------------------------------------
    recipes, advancements = os.path.join(mine, "recipe"), os.path.join(mine, "advancement", "recipes")

    def advancement(name, category, unlock):
        write(os.path.join(advancements, category, name + ".json"), OrderedDict([
            ("parent", "minecraft:recipes/root"),
            ("criteria", OrderedDict([
                ("has_item", {"conditions": {"items": [{"items": unlock}]}, "trigger": "minecraft:inventory_changed"}),
                ("has_the_recipe", {"conditions": {"recipes": m(name)}, "trigger": "minecraft:recipe_unlocked"})])),
            ("requirements", [["has_item", "has_the_recipe"]]),
            ("rewards", {"recipes": [m(name)]})]))

    def shaped(name, result, pattern, key, unlock, count=1, category="misc", group=None, book=None):
        recipe = OrderedDict([("type", "minecraft:crafting_shaped"), ("category", category)])
        if group:
            recipe["group"] = group
        recipe.update([("key", key), ("pattern", pattern), ("result", {"count": count, "id": result})])
        write(os.path.join(recipes, name + ".json"), recipe)
        # Recipe-book folders: weapons, armor and rounds under combat like the mod's others, tools under tools.
        if book is None and category == "equipment":
            book = "tools" if any(tool in name for tool in ("shovel", "pickaxe", "axe", "hoe")) else "combat"
        advancement(name, book or category, unlock)

    def shapeless(name, result, ingredients, unlock, count=1, category="misc"):
        write(os.path.join(recipes, name + ".json"), OrderedDict([
            ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
            ("result", {"count": count, "id": result})]))
        advancement(name, category, unlock)

    def cooking(name, kind, ingredient, result, xp, ticks, unlock, group=None):
        recipe = OrderedDict([("type", "minecraft:" + kind), ("category", "misc"), ("cookingtime", ticks), ("experience", xp)])
        if group:
            recipe["group"] = group
        recipe.update([("ingredient", ingredient), ("result", {"id": result})])
        write(os.path.join(recipes, name + ".json"), recipe)
        advancement(name, "misc", unlock)

    ingot, nugget, holy, holy_nugget = m("silver_ingot"), m("silver_nugget"), m("holy_silver_ingot"), m("holy_silver_nugget")
    for source, unlock in (("raw_silver", m("raw_silver")), ("silver_ore", m("silver_ore")),
                           ("deepslate_silver_ore", m("deepslate_silver_ore"))):
        cooking(f"silver_ingot_from_smelting_{source}", "smelting", m(source), ingot, 0.7, 200, unlock, "silver_ingot")
        cooking(f"silver_ingot_from_blasting_{source}", "blasting", m(source), ingot, 0.7, 100, unlock, "silver_ingot")
    cooking("silver_nugget_from_smelting", "smelting", [m("silver_sword"), m("silver_long_sword")], nugget, 0.1, 200,
            m("silver_sword"))
    holy_gear = [m(n) for n in ["holy_silver_sword", "holy_silver_long_sword"] + TOOLS + ARMOR]
    cooking("holy_silver_nugget_from_smelting", "smelting", holy_gear, holy_nugget, 0.1, 200, holy)

    nine = ["###", "###", "###"]
    for metal, block, small, unlock_category in (("raw_silver", "raw_silver_block", None, "building"),
                                                  ("silver_ingot", "silver_block", "silver_nugget", "building"),
                                                  ("holy_silver_ingot", "holy_silver_block", "holy_silver_nugget", "building")):
        shaped(block, m(block), nine, {"#": m(metal)}, m(metal), category=unlock_category)
        shapeless(f"{metal}_from_{block}", m(metal), [m(block)], m(block), 9)
        if small:
            shapeless(small, m(small), [m(metal)], m(metal), 9)
            shaped(f"{metal}_from_nuggets", m(metal), nine, {"#": m(small)}, m(small), group=metal)

    stick = "minecraft:stick"
    shaped("silver_sword", m("silver_sword"), ["X", "X", "#"], {"#": stick, "X": "#tomyen:silver_tool_materials"}, ingot,
           category="equipment")
    shaped("holy_silver_sword", m("holy_silver_sword"), ["X", "X", "#"], {"#": stick, "X": "#tomyen:holy_silver_tool_materials"},
           holy, category="equipment")
    for sword in ("silver", "holy_silver"):
        shaped(f"{sword}_long_sword", m(f"{sword}_long_sword"), ["S  ", " S ", "  S"], {"S": m(f"{sword}_sword")},
               m(f"{sword}_sword"), category="equipment")
    tool_x = "#tomyen:holy_silver_tool_materials"
    for tool, pattern in (("shovel", ["X", "#", "#"]), ("pickaxe", ["XXX", " # ", " # "]), ("axe", ["XX", "X#", " #"]),
                          ("hoe", ["XX", " #", " #"])):
        shaped(f"holy_silver_{tool}", m(f"holy_silver_{tool}"), pattern, {"#": stick, "X": tool_x}, holy, category="equipment")
    for piece, pattern in (("helmet", ["XXX", "X X"]), ("chestplate", ["X X", "XXX", "XXX"]), ("leggings", ["XXX", "X X", "X X"]),
                           ("boots", ["X X", "X X"])):
        shaped(f"holy_silver_{piece}", m(f"holy_silver_{piece}"), pattern, {"X": holy}, holy, category="equipment")
    shaped("holy_silver_crucifix", m("holy_silver_crucifix"), [" H ", "HCH", " H "], {"H": holy, "C": m("crucifix")}, holy,
           category="equipment")
    # As plain buckshot, with silver shot for the iron.
    for metal, small in (("silver", nugget), ("holy_silver", holy_nugget)):
        shaped(f"shell_12g_{metal}_buckshot", m(f"shell_12g_{metal}_buckshot"), ["NNN", "PGP", " C "],
               {"C": "minecraft:copper_nugget", "G": "minecraft:gunpowder", "N": small, "P": "minecraft:paper"}, small, 6,
               category="equipment")

    # --- Worldgen: a little rarer than iron, at y -32..48, and as much again under mountains and badlands -------------
    write(os.path.join(mine, "worldgen", "feature", "ore_silver.json"), OrderedDict([
        ("type", "minecraft:ore"), ("discard_chance_on_air_exposure", 0.0), ("size", 8),
        ("targets", [
            {"state": m("silver_ore"), "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
            {"state": m("deepslate_silver_ore"),
             "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}])]))
    for placed, count in (("ore_silver", 6), ("ore_silver_extra", 6)):
        write(os.path.join(mine, "worldgen", "placed_feature", placed + ".json"), OrderedDict([
            ("feature", m("ore_silver")),
            ("placement", [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                           {"type": "minecraft:height_range", "height": OrderedDict([
                               ("type", "minecraft:trapezoid"), ("min_inclusive", {"absolute": -32}),
                               ("max_inclusive", {"absolute": 48})])},
                           {"type": "minecraft:biome"}])]))

    # --- Tags ------------------------------------------------------------------------------------------------------
    t = os.path.join(mine, "tags")
    write(os.path.join(t, "entity_type", "evil.json"), {"values": ["#tomyen:holy_vulnerable", "#minecraft:undead"]})
    write(os.path.join(t, "damage_type", "evil.json"), {"values": [m("bleeding")]})
    write(os.path.join(t, "item", "silver_ores.json"), {"values": [m("silver_ore"), m("deepslate_silver_ore")]})
    write(os.path.join(t, "item", "silver_tool_materials.json"), {"values": [ingot]})
    write(os.path.join(t, "item", "holy_silver_tool_materials.json"), {"values": [holy]})
    write(os.path.join(t, "item", "repairs_holy_silver_armor.json"), {"values": [holy]})
    tag(os.path.join(t, "item", "long_swords.json"), [m(n) for n in LONG_SWORDS])

    ores, blocks = [m("silver_ore"), m("deepslate_silver_ore")], [m(n) for n in BLOCKS]
    tag(os.path.join(vanilla_tags, "block", "mineable", "pickaxe.json"), blocks)
    tag(os.path.join(vanilla_tags, "block", "needs_iron_tool.json"), blocks)
    tag(os.path.join(vanilla_tags, "block", "ores.json"), ores)
    tag(os.path.join(vanilla_tags, "item", "ores.json"), ores)
    tag(os.path.join(vanilla_tags, "item", "swords.json"), [m("silver_sword"), m("holy_silver_sword")])
    for tool, tag_name in (("shovel", "shovels"), ("pickaxe", "pickaxes"), ("axe", "axes"), ("hoe", "hoes")):
        tag(os.path.join(vanilla_tags, "item", tag_name + ".json"), [m("holy_silver_" + tool)])
    for piece, tag_name in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"),
                            ("boots", "foot_armor")):
        tag(os.path.join(vanilla_tags, "item", tag_name + ".json"), [m("holy_silver_" + piece)])
    tag(os.path.join(vanilla_tags, "item", "trimmable_armor.json"), [m(n) for n in ARMOR])
    tag(os.path.join(vanilla_tags, "item", "beacon_payment_items.json"), [ingot])
    # Fabric's convention tags, for other mods' machines and recipes.
    for kind, values in (("block/ores/silver", ores), ("item/ores/silver", ores), ("item/raw_materials/silver", [m("raw_silver")]),
                         ("item/ingots/silver", [ingot]), ("item/nuggets/silver", [nugget]),
                         ("block/storage_blocks/silver", [m("silver_block")]), ("item/storage_blocks/silver", [m("silver_block")]),
                         ("block/storage_blocks/raw_silver", [m("raw_silver_block")]),
                         ("item/storage_blocks/raw_silver", [m("raw_silver_block")])):
        tag(os.path.join(c_tags, kind + ".json"), values)
if __name__ == "__main__":
    main()
