"""Paints tungsten and uranium and writes everything around them.

Textures follow vanilla's look: the ores are vanilla's own stone and deepslate with crystals drawn into them in
vanilla's ore shading (a highlight up and to the left, a dark edge down and to the right, the rock darkened round
them), the raw storage blocks and the tungsten block take their shading from vanilla's raw iron block and iron block,
the Hazmat suit its silhouettes from leather armour; the rest are hand-drawn
sprites in vanilla's palette style. Each has its own shape so it reads apart from iron, gold and copper at a glance:
wolframite blades in the ore and a spiky crystal cluster for raw tungsten, a squat dark-bevelled ingot, a faceted
carbide chunk; yellow-green tablets in a black pitchblende matrix for the uranium ores, a glowing yellowcake heap,
green fuel pellets (enriched) against a dull grey slug (depleted). The 5.56 rounds and 120mm sabots are recoloured
from this mod's AP round and APFSDS shell.

Also the Centrifuge's block textures, rotor (UVs match CentrifugeRenderer) and GUI (slots match CentrifugeMenu),
the Radiation Sickness icon; block states, block and item models and item definitions; loot tables (Fortune works
on the ores); crafting recipes with recipe-book advancements; tags (merged into the shared vanilla and c: ones);
the ore features; and with ffmpeg on the path the Geiger counter's clicks and the Centrifuge's hum.

The vanilla textures are read from the Minecraft client jar Loom has downloaded (see gen_longswords.client_jar).
With --preview PATH it also writes a sheet of the new textures beside their closest vanilla ones, scaled up.

Usage: python3 tools/gen_ores_tu.py src/main/resources/assets/tomyen [--preview path.png]"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave, zipfile

sys.path.insert(0, os.path.dirname(__file__))
ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
if PREVIEW in ARGS:
    ARGS.remove(PREVIEW)
sys.argv = [sys.argv[0]] + ARGS[1:2]  # gen_longswords reads an optional jar path from argv[2]... and ignores argv[1]
sys.argv.insert(1, ARGS[0] if ARGS else "")
from gen_longswords import client_jar, read_png
from gunkit import png

ASSETS = ARGS[0] if ARGS else sys.exit(__doc__.rsplit("Usage: ", 1)[1])
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
MYDATA = os.path.join(DATA, "tomyen")
TEX = os.path.join(ASSETS, "textures")
CLEAR = (0, 0, 0, 0)
JAR = zipfile.ZipFile(client_jar())


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge_tag(path, values):
    """Adds values to a tag file, keeping what's there (other generators and hand edits share these)."""
    old = json.load(open(path))["values"] if os.path.exists(path) else []
    write_json(path, {"values": old + [v for v in values if v not in old]})


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def lum(c):
    return (c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114) / 255.0


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


class Canvas:
    def __init__(self, w, h, px=None):
        self.w, self.h = w, h
        self.px = list(px) if px else [CLEAR] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def copy(self):
        return Canvas(self.w, self.h, self.px)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        png(path, self.w, self.h, self.px)


def vanilla(name):
    w, h, px = read_png(JAR.read(f"assets/minecraft/textures/{name}.png"))
    return Canvas(w, min(h, w) if name.startswith(("block/", "item/")) else h, px[:w * (min(h, w) if name.startswith(("block/", "item/")) else h)])


def ours(name):
    w, h, px = read_png(open(os.path.join(TEX, name + ".png"), "rb").read())
    return Canvas(w, h, px)


def ramp(colours, t):
    """A colour along a ramp of hex colours, dark to light, for t in 0..1 (no blending: vanilla keeps palettes small)."""
    t = max(0.0, min(0.999, t))
    return rgba(colours[int(t * len(colours))])


def remap(c, colours, lo=0.0, hi=1.0, where=lambda p: True):
    """Recolours a canvas's opaque pixels by brightness onto a ramp, stretching lo..hi brightness to the whole ramp."""
    out = c.copy()
    for i, p in enumerate(c.px):
        if p[3] and where(p):
            out.px[i] = ramp(colours, (lum(p) - lo) / (hi - lo))[:3] + (p[3],)
    return out


def sprite(rows, palette, base=None):
    """A sprite from rows of palette letters ('.' leaves the base, or clear)."""
    c = base.copy() if base else Canvas(len(rows[0]), len(rows))
    assert all(len(r) == c.w for r in rows) and len(rows) == c.h, [len(r) for r in rows]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


def stamp(c, x0, y0, rows, palette, shadow=0.72):
    """Draws an ore crystal into rock, darkening the rock just below and right of it as vanilla's ores do."""
    cells = [(x0 + x, y0 + y, ch) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch != "."]
    filled = {(x, y) for x, y, _ in cells}
    for x, y, _ in cells:
        for dx, dy in ((1, 0), (0, 1), (1, 1)):
            if (x + dx, y + dy) not in filled and 0 <= x + dx < 16 and 0 <= y + dy < 16:
                c.set(x + dx, y + dy, shade(c.get(x + dx, y + dy), shadow))
                filled.add((x + dx, y + dy))
    for x, y, ch in cells:
        c.set(x % 16, y % 16, rgba(palette[ch]))


# ================================================================================================= ores and blocks
# Wolframite: chunky black-brown blades, lit along one edge, with a single steely glint at the tip.
WOLFRAMITE = {"g": 0xE0E4EA, "L": 0x9A97A0, "M": 0x5E5A64, "d": 0x2A2630, "b": 0x6A4C36}
BLADES = [
    (1, 1, ["...gL", "..LMd", ".LMd.", "LMd..", "Md..."]),
    (10, 0, [".gL", "LMd", "Md.", "b.."]),
    (9, 7, ["gL..", "LML.", "dMML", ".dMd", "..b."]),
    (2, 9, ["..gL", ".LMd", "LMd.", "Mb.."]),
    (12, 12, ["LM.", "dML", ".dd"]),
    (6, 5, ["gL", "Md"]),
]


def tungsten_ore():
    c = vanilla("block/deepslate")
    for x, y, rows in BLADES:
        stamp(c, x, y, rows, WOLFRAMITE, 0.7)
    return c


# Uranium: yellow-green autunite tablets set in black pitchblende.
URANINITE = {"k": 0x1F211A, "K": 0x33362A, "d": 0x5C7A12, "M": 0x9CC222, "L": 0xD3EE46, "h": 0xF6FFA4}
TABLETS = [
    (2, 2, [".kK.", "khLk", "kLMd", ".Kdk"]),
    (10, 1, ["kK.", "hLk", "Mdk"]),
    (6, 7, [".kKk.", "khLLk", "kLMMd", "Kkddk"]),
    (12, 9, ["kk", "hL", "Md"]),
    (1, 11, ["Kk.", "hLk", "LMd", ".dk"]),
    (9, 13, ["khL", "KMd"]),
]


def uranium_ore(base):
    c = vanilla(base)
    for x, y, rows in TABLETS:
        stamp(c, x, y, rows, URANINITE, 0.74)
    return c


GUNMETAL = [0x15171B, 0x262A30, 0x363B43, 0x474D56, 0x5B626C, 0x737B86, 0x9099A4]
YELLOWCAKE = [0x4A3E08, 0x7A6812, 0xA8901C, 0xC9B026, 0xE2CC36, 0xF4E46A]


def raw_tungsten_block():
    """Vanilla's raw iron block lumps, in wolframite, with a few steely blades catching the light."""
    c = remap(vanilla("block/raw_iron_block"), [0x1A1719, 0x2B2628, 0x3B3433, 0x4C4442, 0x5E5652], 0.35, 0.85)
    for x, y in ((2, 4), (9, 3), (12, 11), (4, 12), (7, 8)):
        c.set(x, y, rgba(0xC8CCD4))
        c.set(x + 1, y - 1, rgba(0x8C9098))
    return c


def raw_uranium_block():
    """Vanilla's raw iron block lumps as compacted yellowcake, matte, with glowing green flecks."""
    c = remap(vanilla("block/raw_iron_block"), [0x6E6012, 0x9C8A1E, 0xBBA62C, 0xD1BE3E], 0.35, 0.85)
    for x, y in ((3, 4), (10, 2), (13, 9), (6, 12), (1, 14), (9, 7), (14, 14)):
        c.set(x, y, rgba(0x9CFF3A))
    return c


def tungsten_block():
    """Vanilla's iron block in dark gunmetal, with a heavy bevelled frame and a rivet in each corner."""
    c = remap(vanilla("block/iron_block"), GUNMETAL[1:5], 0.55, 1.0)
    for i in range(16):
        for x, y, col in ((i, 0, 0x8C95A0), (0, i, 0x8C95A0), (i, 15, 0x15171B), (15, i, 0x15171B)):
            c.set(x, y, rgba(col))
        for x, y, col in ((i, 1, 0x6B737E), (1, i, 0x6B737E), (i, 14, 0x262A30), (14, i, 0x262A30)):
            if 0 < i < 15:
                c.set(x, y, rgba(col))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, rgba(0xB5BEC9))
        c.set(x + 1, y, rgba(0x5B626C))
        c.set(x, y + 1, rgba(0x5B626C))
        c.set(x + 1, y + 1, rgba(0x15171B))
    return c


# ================================================================================================= items
RAW_TUNGSTEN = sprite([
    "................",
    "........o.......",
    ".......oho......",
    "....o..ohLo.....",
    "...oho.oLMdo....",
    "...ohLooLMdo.o..",
    "...oLMdoLMMdoho.",
    "..ooLMdoLMdooLMo",
    ".obboMdoMMdoLMdo",
    ".obbbodoMddoLMdo",
    ".obBbbooMddoMddo",
    "..obBbbboddoMdo.",
    "..obbBbbbboodo..",
    "...oobbbbbbboo..",
    ".....ooooooo....",
    "................",
], {"o": 0x121316, "h": 0xD6DAE2, "L": 0x8C919B, "M": 0x5B606A, "d": 0x353840, "b": 0x4A3A2E, "B": 0x6A5442})

RAW_URANIUM = sprite([
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    "....ooYyyYoo....",
    "...oYyhyyYgYo...",
    "..oYyhhyYyyYYo..",
    "..oyhyyYgYyyYo..",
    ".oYyyYyyyYyYgYo.",
    ".oyYgyyYyyYyYYo.",
    ".oYYyYYyYgYYYdo.",
    "..oYdYYdYYdYdo..",
    "...ooddoddddo...",
    ".....ooooooo....",
    "................",
], {"o": 0x4A4010, "d": 0x8A7612, "Y": 0xC9B01E, "y": 0xE6D23A, "h": 0xFFF6A0, "g": 0x9CFF3A})

TUNGSTEN_INGOT = sprite([
    "................",
    "................",
    "................",
    "................",
    "......ooooooooo.",
    ".....ohhhhhhhLo.",
    "....ohLLLLLLLdo.",
    "...ohLLLLLLLMdo.",
    "..oooooooooooddo",
    "..oBMMMMMMMModdo",
    "..oBMMMMMMMModdo",
    "..oBMMMMMMMModo.",
    "..oDDDDDDDDDoo..",
    "..ooooooooooo...",
    "................",
    "................",
], {"o": 0x121418, "h": 0xB9C0CA, "L": 0x7F8894, "M": 0x5C6470, "d": 0x3A4048, "B": 0x707986, "D": 0x2A2E35})

TUNGSTEN_NUGGET = sprite([
    "................",
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    ".....ohhLo......",
    ".....oMMdo......",
    "......oooo.oooo.",
    "..oooo....ohhLo.",
    ".ohhLo....oMMdo.",
    ".oMMdo.....oooo.",
    "..oooo..........",
    "................",
    "................",
    "................",
], {"o": 0x121418, "h": 0xB9C0CA, "L": 0x7F8894, "M": 0x5C6470, "d": 0x3A4048})

TUNGSTEN_CARBIDE = sprite([
    "................",
    "................",
    "......oooo......",
    "....oohhLLoo....",
    "...ohhhhLLLMo...",
    "..ohhhhhLLLMMo..",
    "..oSSSSSdddDDo..",
    ".oSSSSSSdddDDDo.",
    ".oSSSSSSdddDDDo.",
    ".oSSSSSSdddDDDo.",
    "..oSSSSSdddDDo..",
    "...ooSSSdddoo...",
    ".....oooooo.....",
    "................",
    "................",
    "................",
], {"o": 0x111214, "h": 0xB8BCC4, "L": 0x9498A0, "M": 0x767A82, "S": 0x62666D, "d": 0x45484E, "D": 0x2C2E32})

GEIGER_COUNTER = sprite([
    "................",
    ".............oo.",
    "............oSso",
    "...........oSso.",
    "..........oSso..",
    ".........oSso...",
    "..ooooooooKo....",
    ".oYYYYYYYYyo....",
    ".oYkkkkkkYyo....",
    ".oYkwwwrkYyo....",
    ".oYkwwrwkYyo..c.",
    ".oYkkkkkkYyo.c..",
    ".oYYgYYYKYyocc..",
    ".oyyyyyyyyyo....",
    "..oooooooooo....",
    "................",
], {"o": 0x2E2408, "Y": 0xF2C41C, "y": 0xB88A10, "k": 0x1A1A1A, "w": 0xE6E8D8, "r": 0xD42020, "g": 0x6CFF3A,
    "K": 0x3A3A3A, "S": 0x9AA2AC, "s": 0x5E6670, "c": 0x262626})

HAZMAT_HELMET = sprite([
    "................",
    ".....oooooo.....",
    "...ooYYYYYYoo...",
    "..oYYhYYYYYYyo..",
    "..oYhYYYYYYYyo..",
    ".oYYoooooooYYyo.",
    ".oYoGgGGGGGoYyo.",
    ".oYoGgGGGGGoYyo.",
    ".oYoGGGGGGgoYyo.",
    ".oYyoooooooyYyo.",
    ".oYYyykkkyyYYyo.",
    "..oYyykKkyyYyo..",
    "..oyYyykyyYyyo..",
    "...oyyyyyyyyo...",
    "....oooooooo....",
    "................",
], {"o": 0x3A2A06, "Y": 0xF2C81E, "y": 0xC69512, "h": 0xFFF08A, "G": 0x2C4A5E, "g": 0x9FD3E8, "k": 0x262626,
    "K": 0x5A5A5A})

ENRICHED_PELLET = ([".oooo.", "oLhhLo", "oMLLMo", "oGGGgo", "oGGGgo", "oGGGgo", ".oooo."],
                   {"o": 0x1F3A0C, "h": 0xF3FFB8, "L": 0xC8FF5A, "M": 0x8FD62E, "G": 0x6BD12A, "g": 0x3E8E1A})


def enriched_uranium():
    """Three glowing fuel pellets: two behind, one in front."""
    c = Canvas(16, 16)
    rows, palette = ENRICHED_PELLET
    for x0, y0 in ((2, 3), (8, 2), (5, 8)):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    c.set(x0 + x, y0 + y, rgba(palette[ch]))
    return c


def capsule(c, x0, y0, x1, y1, r, colours, outline):
    """A round-ended rod from (x0, y0) to (x1, y1), lit from the upper left."""
    dx, dy = x1 - x0, y1 - y0
    length = math.hypot(dx, dy)
    nx, ny = -dy / length, dx / length  # across the rod, toward the lower right when the rod runs up-right
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            t = max(0.0, min(length, (px * dx + py * dy) / length))
            ax, ay = px - dx * t / length, py - dy * t / length
            d = math.hypot(ax, ay)
            if d <= r + 0.75:
                across = (ax * nx + ay * ny) / (r + 0.75)
                c.set(x, y, rgba(outline) if d > r - 0.25 else ramp(colours, 0.5 - across * 0.6))
    return c


def depleted_uranium():
    """A dull, blunt slug of metal."""
    return capsule(Canvas(16, 16), 4.5, 11.5, 11.5, 4.5, 2.6, [0x30332A, 0x44483A, 0x5A5E4C, 0x72765F, 0x8C9076], 0x16180F)


def fuel_rod():
    """A steel-clad rod with the fuel glowing green through three windows, capped at both ends."""
    c = capsule(Canvas(16, 16), 2.5, 13.5, 13.5, 2.5, 1.4, [0x4A515A, 0x6B737E, 0x8E97A2, 0xB8C0CA], 0x1B1E22)
    for i in range(16):
        for x, y in ((i, 15 - i), (i + 1, 15 - i)):
            p = c.get(x, y)
            if p[3] and p[:3] != rgba(0x1B1E22)[:3] and i in (5, 6, 8, 9, 11):
                c.set(x, y, rgba(0x8CFF3A if lum(p) > 0.45 else 0x4FC21E))
    for x, y in ((2, 12), (3, 12), (2, 13), (12, 2), (13, 2), (13, 3)):
        c.set(x, y, rgba(0x2A2E33))
    return c


def hazmat_item(name, details):
    """A leather armour piece's silhouette in yellow rubber, with its own details on top."""
    c = remap(vanilla("item/" + name), [0x3A2A06, 0xB08410, 0xD9A816, 0xEBC020], 0.1, 0.95)
    for (x, y), col in details.items():
        c.set(x, y, rgba(col))
    return c


def hazmat_suit():
    pick = vanilla("item/leather_chestplate")
    details = {}
    for y in range(16):
        for x in (7, 8):
            if pick.get(x, y)[3] and lum(pick.get(x, y)) > 0.3 and 3 <= y <= 13:
                details[(x, y)] = 0x262626 if x == 7 else 0x4A4A4A
    for x in range(16):
        if pick.get(x, 10)[3] and lum(pick.get(x, 10)) > 0.3:
            details[(x, 10)] = 0x262626
    # A trefoil badge on the chest.
    details.update({(4, 5): 0x262626, (5, 5): 0x262626, (4, 6): 0x262626, (5, 6): 0xF2C81E})
    return hazmat_item("leather_chestplate", details)


def hazmat_leggings():
    """Black rubber cuffs at the ankles."""
    pick = vanilla("item/leather_leggings")
    bottom = max(i // 16 for i, p in enumerate(pick.px) if p[3])
    return hazmat_item("leather_leggings", {(x, bottom - 1): 0x262626 for x in range(16)
                                            if pick.get(x, bottom - 1)[3] and lum(pick.get(x, bottom - 1)) > 0.3})


def hazmat_boots():
    """Black rubber boots with yellow cuffs."""
    boots = vanilla("item/leather_boots")
    c = remap(boots, [0x0E0E0E, 0x1E1E1E, 0x2E2E2E, 0x444444, 0x5E5E5E], 0.1, 0.95)
    top = min(i // 16 for i, p in enumerate(boots.px) if p[3])
    for i, p in enumerate(boots.px):
        if p[3] and i // 16 <= top + 1 and lum(p) > 0.3:
            c.px[i] = rgba(0xD9A816 if i // 16 == top + 1 else 0xEBC020)
    return c


def bullet(head, tip):
    """This mod's AP round with another penetrator: its copper head and black tip recoloured."""
    c = ours("item/bullet_556_ap")
    copper = {0x4F2412: 0, 0x8C4426: 1, 0xC06A3E: 2, 0xEBA071: 3}
    paint = {0x0B0B0C: 0, 0x1C1C1E: 1, 0x2E2F32: 1, 0x55575C: 2}
    for i, p in enumerate(c.px):
        key = (p[0] << 16) | (p[1] << 8) | p[2]
        if p[3] and key in copper:
            c.px[i] = rgba(head[copper[key]])
        elif p[3] and key in paint:
            c.px[i] = rgba(tip[paint[key]])
    return c


def sabot(dart, petals):
    """This mod's APFSDS shell with another dart and coloured sabot petals."""
    c = ours("item/apfsds_shell")
    darts = {0x2A2C30: 0, 0x5A5E64: 1, 0x6E737A: 2, 0xA8AEB6: 3}
    sabots = {0x3E462A: 0, 0x707C4A: 1}
    for i, p in enumerate(c.px):
        key = (p[0] << 16) | (p[1] << 8) | p[2]
        if p[3] and key in darts:
            c.px[i] = rgba(dart[darts[key]])
        elif p[3] and key in sabots:
            c.px[i] = rgba(petals[sabots[key]])
    return c


def radiation_icon():
    """The trefoil, black on a yellow disc, for the Radiation Sickness effect."""
    c = Canvas(18, 18)
    for y in range(18):
        for x in range(18):
            dx, dy = x + 0.5 - 9, y + 0.5 - 9
            r = math.hypot(dx, dy)
            angle = math.degrees(math.atan2(-dy, dx)) % 360
            blade = any(abs((angle - a + 180) % 360 - 180) < 30 for a in (90, 210, 330))
            if r > 8.3:
                continue
            if r > 7.4:
                c.set(x, y, rgba(0x2A2006))
            elif r < 1.6 or 2.6 < r < 6.6 and blade:
                c.set(x, y, rgba(0x1A1A1A))
            else:
                c.set(x, y, rgba(0xFFE45A if dx + dy < -4 else 0xF2C81E if dx + dy < 5 else 0xC69512))
    return c


# ================================================================================================= hazmat suit worn
def hazmat_equipment(layer):
    """Leather's worn texture in yellow rubber; the outer layer adds the hood's visor and filter, black gloves and boots."""
    c = remap(vanilla(f"entity/equipment/{layer}/leather"), [0x8A6410, 0xB08410, 0xD9A816, 0xEBC020], 0.3, 1.0)
    if layer == "humanoid":
        c.rect(9, 10, 15, 13, rgba(0x2C4A5E))  # visor, on the head's front face
        c.set(9, 10, rgba(0x9FD3E8))
        c.set(10, 10, rgba(0x9FD3E8))
        c.set(9, 11, rgba(0x9FD3E8))
        c.rect(11, 14, 13, 16, rgba(0x262626))  # filter
        for x in range(40, 56):  # gloves: the arm's lowest rows
            for y in range(29, 32):
                if c.get(x, y)[3]:
                    c.set(x, y, rgba(0x1E1E1E if y > 29 else 0x2E2E2E))
        for x in range(0, 16):  # boots: the leg's lowest rows
            for y in range(28, 32):
                if c.get(x, y)[3]:
                    c.set(x, y, rgba(0x1E1E1E if y > 28 else 0x2E2E2E))
    return c


# ================================================================================================= centrifuge
# A small steel palette, light to dark, and the hazard yellow.
STEEL = {"h": 0xB4BCC6, "L": 0x98A0AA, "M": 0x7D8590, "d": 0x5E6670, "D": 0x444B54, "o": 0x2A2F35}
HAZARD = 0xF2C41C


def centrifuge_housing(front=False, lit=False):
    """The housing's side, its 12 texels in the lower rows: a bright rim, a hazard band, then a plain steel panel
    shading down to a dark foot. The sides have a vent; the front a gauge and a lamp."""
    c = Canvas(16, 16)
    bands = ["h", "o", "y", "y", "o", "L", "L", "M", "M", "M", "d", "D"]
    for row, band in enumerate(bands):
        for x in range(16):
            colour = (HAZARD if (x + row) % 4 < 2 else 0x262626) if band == "y" else STEEL[band]
            c.set(x, 4 + row, rgba(colour))
    for y in range(4, 16):  # rounded corners: the panel's edge columns a shade darker
        for x in (0, 15):
            if c.get(x, y)[:3] != rgba(HAZARD)[:3]:
                c.set(x, y, shade(c.get(x, y), 0.85))
    if front:
        c.rect(3, 9, 8, 13, rgba(STEEL["o"]))
        c.rect(4, 10, 7, 12, rgba(0xE6E8D8))
        c.set(6, 10, rgba(0xD42020))
        c.set(5, 11, rgba(0xD42020))
        c.rect(10, 10, 13, 12, rgba(STEEL["o"]))
        c.rect(11, 10, 12, 11, rgba(0x8CFF4A if lit else 0x3A5A2E))
        c.set(11, 11, rgba(0x4FC21E if lit else 0x2E4A22))
    else:
        for y in (9, 11):
            c.rect(4, y, 12, y + 1, rgba(STEEL["D"]))
    return c


def centrifuge_top():
    """The open top: a steel rim round a dark bowl the rotor spins in."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            c.set(x, y, rgba(STEEL["h"] if edge == 0 else STEEL["L"] if edge == 1 else STEEL["o"] if edge == 2 else 0x1B1F24))
    return c


def box_faces(u, v, w, h, d):
    """A model box's faces as Minecraft unwraps them from texOffs u, v: (x0, y0, width, height, along) for each,
    where along says which way the face's texels run along the box's x ("w"), z ("d"), or neither (an end)."""
    return [(u + d, v, w, d, "wd"), (u + d + w, v, w, d, "wd"),  # top, bottom
            (u, v + d, d, h, "d"), (u + d, v + d, w, h, "w"), (u + d + w, v + d, d, h, "d"), (u + d + w + d, v + d, w, h, "w")]


def rotor_box(c, u, v, w, h, d, tubes):
    """Paints a rotor box flat: light on top, darker round the sides. With tubes, the two texels at each end of
    its length are a green sample tube."""
    for i, (x0, y0, fw, fh, along) in enumerate(box_faces(u, v, w, h, d)):
        for y in range(y0, y0 + fh):
            for x in range(x0, x0 + fw):
                colour = STEEL["L"] if i == 0 else STEEL["d"]
                if tubes:
                    length = w if w > d else d
                    if w > d and "w" in along:
                        k = x - x0
                    elif d > w and along == "wd":
                        k = y - y0
                    elif d > w and along == "d":
                        k = x - x0
                    else:
                        k = 0  # an end cap
                    if k < 2 or k >= length - 2:
                        colour = 0x8CE04A if i == 0 else 0x4FA81E
                c.set(x, y, rgba(colour))


def centrifuge_rotor():
    """Hub and two arms (CentrifugeRenderer): steel, with a green sample tube at each end of the arms."""
    c = Canvas(32, 32)
    for i, (x0, y0, fw, fh, _) in enumerate(box_faces(0, 0, 2, 3, 2)):
        c.rect(x0, y0, x0 + fw, y0 + fh, rgba(STEEL["h"] if i == 0 else STEEL["M"]))
    rotor_box(c, 0, 6, 12, 1, 2, True)
    rotor_box(c, 0, 10, 2, 1, 12, True)
    return c


# ================================================================================================= gui
K, W, BG, SHADOW, SLOT, SLOT_DARK = rgba(0), rgba(0xFFFFFF), rgba(0xC6C6C6), rgba(0x555555), rgba(0x8B8B8B), rgba(0x373737)
GUI_W, GUI_H = 176, 166


def slot(g, x, y):
    g.rect(x - 1, y - 1, x + 17, y + 17, SLOT)
    for i in range(x - 1, x + 17):
        g.set(i, y - 1, SLOT_DARK)
        g.set(i, y + 16, W)
    for i in range(y - 1, y + 17):
        g.set(x - 1, i, SLOT_DARK)
        g.set(x + 16, i, W)


def centrifuge_gui():
    g = Canvas(256, 256)
    g.rect(1, 1, GUI_W - 1, GUI_H - 1, BG)
    for x in range(2, GUI_W - 2):
        g.set(x, 0, K)
        g.set(x, GUI_H - 1, K)
        g.set(x, 1, W)
        g.set(x, GUI_H - 2, SHADOW)
    for y in range(2, GUI_H - 2):
        g.set(0, y, K)
        g.set(GUI_W - 1, y, K)
        g.set(1, y, W)
        g.set(GUI_W - 2, y, SHADOW)
    for x, y in ((1, 1), (GUI_W - 2, GUI_H - 2)):
        g.set(x, y, K)
    for row in range(3):
        for col in range(9):
            slot(g, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(g, 8 + col * 18, 142)
    for x, y in ((56, 17), (56, 53), (116, 22), (116, 48)):  # CentrifugeMenu
        slot(g, x, y)
    # The empty flame and arrow, where CentrifugeScreen draws the progress.
    for y in range(37, 51):
        half = (y - 37) // 2
        for x in range(63 - half // 2, 64 + half // 2):
            g.set(x, y, SLOT)
    for y in range(35, 51):
        for x in range(82, 106):
            if 39 <= y <= 46 and x < 98 or 98 <= x < 98 + (8 - abs(y - 42)):
                g.set(x, y, SLOT)
    return g


# ================================================================================================= write textures
BLOCK_TEXTURES = {
    "tungsten_ore": tungsten_ore(),
    "uranium_ore": uranium_ore("block/stone"),
    "deepslate_uranium_ore": uranium_ore("block/deepslate"),
    "raw_tungsten_block": raw_tungsten_block(),
    "raw_uranium_block": raw_uranium_block(),
    "tungsten_block": tungsten_block(),
    "centrifuge_housing": centrifuge_housing(),
    "centrifuge_front": centrifuge_housing(True, False),
    "centrifuge_front_on": centrifuge_housing(True, True),
    "centrifuge_top": centrifuge_top(),
}
ITEM_TEXTURES = {
    "raw_tungsten": RAW_TUNGSTEN,
    "tungsten_ingot": TUNGSTEN_INGOT,
    "tungsten_nugget": TUNGSTEN_NUGGET,
    "tungsten_carbide": TUNGSTEN_CARBIDE,
    "raw_uranium": RAW_URANIUM,
    "enriched_uranium": enriched_uranium(),
    "depleted_uranium": depleted_uranium(),
    "uranium_fuel_rod": fuel_rod(),
    "geiger_counter": GEIGER_COUNTER,
    "hazmat_helmet": HAZMAT_HELMET,
    "hazmat_suit": hazmat_suit(),
    "hazmat_leggings": hazmat_leggings(),
    "hazmat_boots": hazmat_boots(),
    "bullet_556_tungsten": bullet([0x1A1D22, 0x3E4550, 0x6A7480, 0xA9B3BF], [0xC8CED6, 0xE6EBF0, 0xFFFFFF]),
    "bullet_556_depleted_uranium": bullet([0x1B1D16, 0x34372C, 0x4E5243, 0x6A6E5A], [0x8A6A0A, 0xD9B21A, 0xFFE45A]),
    "tungsten_apfsds_shell": sabot([0x121418, 0x3A4048, 0x5C6470, 0xD8E0EA], [0x4A5A70, 0x7E92AE]),
    "du_apfsds_shell": sabot([0x16180F, 0x3A3D31, 0x585C49, 0x9A9E85], [0x8A7212, 0xE2C02A]),
}
for name, canvas in BLOCK_TEXTURES.items():
    canvas.save(os.path.join(TEX, "block", name + ".png"))
for name, canvas in ITEM_TEXTURES.items():
    canvas.save(os.path.join(TEX, "item", name + ".png"))
radiation_icon().save(os.path.join(TEX, "mob_effect", "radiation_sickness.png"))
hazmat_equipment("humanoid").save(os.path.join(TEX, "entity", "equipment", "humanoid", "hazmat.png"))
hazmat_equipment("humanoid_leggings").save(os.path.join(TEX, "entity", "equipment", "humanoid_leggings", "hazmat.png"))
centrifuge_rotor().save(os.path.join(TEX, "entity", "uranium", "centrifuge_rotor.png"))
centrifuge_gui().save(os.path.join(TEX, "gui", "container", "centrifuge.png"))
write_json(os.path.join(ASSETS, "equipment", "hazmat.json"), {"layers": {
    "humanoid": [{"texture": "tomyen:hazmat"}], "humanoid_leggings": [{"texture": "tomyen:hazmat"}]}})

# ================================================================================================= preview
if PREVIEW:
    S = 6
    pairs = [
        ("block/deepslate_iron_ore", BLOCK_TEXTURES["tungsten_ore"]),
        ("block/deepslate_diamond_ore", BLOCK_TEXTURES["tungsten_ore"]),
        ("block/emerald_ore", BLOCK_TEXTURES["uranium_ore"]),
        ("block/deepslate_emerald_ore", BLOCK_TEXTURES["deepslate_uranium_ore"]),
        ("block/raw_iron_block", BLOCK_TEXTURES["raw_tungsten_block"]),
        ("block/raw_gold_block", BLOCK_TEXTURES["raw_uranium_block"]),
        ("block/netherite_block", BLOCK_TEXTURES["tungsten_block"]),
        ("item/raw_iron", ITEM_TEXTURES["raw_tungsten"]),
        ("item/raw_gold", ITEM_TEXTURES["raw_uranium"]),
        ("item/iron_ingot", ITEM_TEXTURES["tungsten_ingot"]),
        ("item/netherite_ingot", ITEM_TEXTURES["tungsten_ingot"]),
        ("item/iron_nugget", ITEM_TEXTURES["tungsten_nugget"]),
        ("item/netherite_scrap", ITEM_TEXTURES["tungsten_carbide"]),
        ("item/emerald", ITEM_TEXTURES["enriched_uranium"]),
        ("item/copper_ingot", ITEM_TEXTURES["depleted_uranium"]),
        ("item/blaze_rod", ITEM_TEXTURES["uranium_fuel_rod"]),
        ("item/compass_00", ITEM_TEXTURES["geiger_counter"]),
        ("item/golden_helmet", ITEM_TEXTURES["hazmat_helmet"]),
        ("item/golden_chestplate", ITEM_TEXTURES["hazmat_suit"]),
        ("item/golden_leggings", ITEM_TEXTURES["hazmat_leggings"]),
        ("item/leather_boots", ITEM_TEXTURES["hazmat_boots"]),
    ]
    mod_refs = [("item/bullet_556_ap", "bullet_556_tungsten"), ("item/bullet_556_green_tip", "bullet_556_depleted_uranium"),
                ("item/apfsds_shell", "tungsten_apfsds_shell"), ("item/he_shell", "du_apfsds_shell")]
    cols = len(pairs) + len(mod_refs)
    gw, gh = cols * (16 * S + 6), 2 * 16 * S + 6 + 18 * S
    sheet = Canvas(gw, gh, [rgba(0x8B8B8B)] * (gw * gh))

    def put(canvas, ox, oy, s=S):
        for y in range(canvas.h):
            for x in range(canvas.w):
                p = canvas.get(x, y)
                if p[3]:
                    sheet.rect(ox + x * s, oy + y * s, ox + x * s + s, oy + y * s + s, p[:3] + (255,))

    for i, (ref, mine) in enumerate(pairs):
        put(vanilla(ref), i * (16 * S + 6), 0)
        put(mine, i * (16 * S + 6), 16 * S + 6)
    for j, (ref, mine) in enumerate(mod_refs):
        i = len(pairs) + j
        put(ours(ref), i * (16 * S + 6), 0)
        put(ITEM_TEXTURES[mine], i * (16 * S + 6), 16 * S + 6)
    put(radiation_icon(), 0, 2 * (16 * S + 6), S)
    for k, name in enumerate(("centrifuge_front_on", "centrifuge_housing", "centrifuge_top")):
        put(BLOCK_TEXTURES[name], (k + 1) * (16 * S + 6), 2 * (16 * S + 6))
    put(hazmat_equipment("humanoid"), 5 * (16 * S + 6), 2 * (16 * S + 6), 3)
    put(hazmat_equipment("humanoid_leggings"), 5 * (16 * S + 6) + 200, 2 * (16 * S + 6), 3)
    put(centrifuge_rotor(), 5 * (16 * S + 6) + 400, 2 * (16 * S + 6), 3)
    sheet.save(PREVIEW)
    print("preview written to", PREVIEW)

# ================================================================================================= models and states
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
MODELS_ITEM = os.path.join(ASSETS, "models", "item")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
ITEM_DEFS = os.path.join(ASSETS, "items")


def item_def(name, model):
    write_json(os.path.join(ITEM_DEFS, name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


for name in ("tungsten_ore", "uranium_ore", "deepslate_uranium_ore", "raw_tungsten_block", "raw_uranium_block", "tungsten_block"):
    write_json(os.path.join(MODELS_BLOCK, name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "tomyen:block/" + name}})
    write_json(os.path.join(BLOCKSTATES, name + ".json"), {"variants": {"": {"model": "tomyen:block/" + name}}})
    item_def(name, "tomyen:block/" + name)

for name in ITEM_TEXTURES:
    write_json(os.path.join(MODELS_ITEM, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
    item_def(name, "tomyen:item/" + name)


def face(texture, uv, cull=None):
    f = {"texture": texture, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def centrifuge_model(front):
    """One box, the housing (12 high); the rotor in its open top is CentrifugeRenderer's."""
    return {"parent": "minecraft:block/block", "textures": {
        "particle": "tomyen:block/centrifuge_housing", "side": "tomyen:block/centrifuge_housing", "front": "tomyen:block/" + front,
        "top": "tomyen:block/centrifuge_top", "bottom": "minecraft:block/iron_block"},
        "elements": [{"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
            "north": face("#front", [0, 4, 16, 16], "north"), "south": face("#side", [0, 4, 16, 16], "south"),
            "east": face("#side", [0, 4, 16, 16], "east"), "west": face("#side", [0, 4, 16, 16], "west"),
            "up": face("#top", [0, 0, 16, 16]), "down": face("#bottom", [0, 0, 16, 16], "down")}}]}


write_json(os.path.join(MODELS_BLOCK, "centrifuge.json"), centrifuge_model("centrifuge_front"))
write_json(os.path.join(MODELS_BLOCK, "centrifuge_on.json"), centrifuge_model("centrifuge_front_on"))
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
write_json(os.path.join(BLOCKSTATES, "centrifuge.json"), {"variants": {
    "facing=%s,lit=%s" % (facing, lit): dict({"model": "tomyen:block/" + ("centrifuge_on" if lit == "true" else "centrifuge")},
                                              **({"y": rot} if rot else {}))
    for facing, rot in FACINGS for lit in ("false", "true")}})
item_def("centrifuge", "tomyen:block/centrifuge")

# ================================================================================================= loot tables
LOOT = os.path.join(MYDATA, "loot_table", "blocks")


def self_drop(name):
    write_json(os.path.join(LOOT, name + ".json"), {"type": "minecraft:block", "pools": [{
        "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": "tomyen:" + name}], "rolls": 1}],
        "random_sequence": "tomyen:blocks/" + name})


def ore_drop(name, item):
    write_json(os.path.join(LOOT, name + ".json"), {"type": "minecraft:block", "pools": [{
        "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": "tomyen:" + name},
            {"type": "minecraft:item", "modifier": [
                {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"type": "minecraft:explosion_decay"}], "name": item}]}],
        "rolls": 1}], "random_sequence": "tomyen:blocks/" + name})


for name in ("raw_tungsten_block", "raw_uranium_block", "tungsten_block", "centrifuge"):
    self_drop(name)
ore_drop("tungsten_ore", "tomyen:raw_tungsten")
ore_drop("uranium_ore", "tomyen:raw_uranium")
ore_drop("deepslate_uranium_ore", "tomyen:raw_uranium")

# ================================================================================================= recipes
RECIPES = os.path.join(MYDATA, "recipe")
ADVANCEMENTS = os.path.join(MYDATA, "advancement", "recipes")


def advancement(name, category, unlock_item):
    write_json(os.path.join(ADVANCEMENTS, category, name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": unlock_item}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"}},
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": ["tomyen:" + name]}})


def shaped(name, result, pattern, key, unlock, count=1, category="misc"):
    write_json(os.path.join(RECIPES, name + ".json"), {"type": "minecraft:crafting_shaped", "category": category,
                                                        "key": key, "pattern": pattern, "result": {"count": count, "id": result}})
    advancement(name, category, unlock)


def shapeless(name, result, ingredients, unlock, count=1, category="misc"):
    write_json(os.path.join(RECIPES, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": category,
                                                        "ingredients": ingredients, "result": {"count": count, "id": result}})
    advancement(name, category, unlock)


def storage(block, item, unlock_category="building"):
    shaped(block.split(":")[1], block, ["###", "###", "###"], {"#": item}, item, category=unlock_category)
    shapeless(item.split(":")[1] + "_from_" + block.split(":")[1], item, [block], block, 9)


storage("tomyen:raw_tungsten_block", "tomyen:raw_tungsten")
storage("tomyen:raw_uranium_block", "tomyen:raw_uranium")
storage("tomyen:tungsten_block", "tomyen:tungsten_ingot")
shaped("tungsten_ingot_from_nuggets", "tomyen:tungsten_ingot", ["###", "###", "###"], {"#": "tomyen:tungsten_nugget"}, "tomyen:tungsten_nugget")
shapeless("tungsten_nugget", "tomyen:tungsten_nugget", ["tomyen:tungsten_ingot"], "tomyen:tungsten_ingot", 9)
STEEL_I, CARBIDE = "tomyen:steel_ingot", "tomyen:tungsten_carbide"
shaped("tungsten_apfsds_shell", "tomyen:tungsten_apfsds_shell", [" W ", " S ", "GCG"],
       {"W": "tomyen:tungsten_ingot", "S": STEEL_I, "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"}, "tomyen:tungsten_ingot", 2,
       category="equipment")
shaped("du_apfsds_shell", "tomyen:du_apfsds_shell", [" D ", " S ", "GCG"],
       {"D": "tomyen:depleted_uranium", "S": STEEL_I, "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"}, "tomyen:depleted_uranium",
       2, category="equipment")
shaped("uranium_fuel_rod", "tomyen:uranium_fuel_rod", ["S", "E", "S"], {"S": STEEL_I, "E": "tomyen:enriched_uranium"},
       "tomyen:enriched_uranium")
shaped("fusion_core_from_fuel_rods", "tomyen:fusion_core", ["CFC", "GBG", "CFC"],
       {"C": "tomyen:computer_chip", "F": "tomyen:uranium_fuel_rod", "G": "minecraft:glowstone", "B": "minecraft:blaze_rod"},
       "tomyen:uranium_fuel_rod")
shaped("geiger_counter", "tomyen:geiger_counter", ["IGI", "CXC", "IRI"],
       {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "C": "minecraft:copper_ingot", "X": "tomyen:computer_chip",
        "R": "minecraft:redstone"}, "tomyen:computer_chip", category="equipment")
RUBBER, LEATHER = "tomyen:rubber", "minecraft:leather"
shaped("hazmat_helmet", "tomyen:hazmat_helmet", ["RRR", "RGR"], {"R": RUBBER, "G": "minecraft:glass_pane"}, RUBBER, category="equipment")
shaped("hazmat_suit", "tomyen:hazmat_suit", ["R R", "RLR", "RRR"], {"R": RUBBER, "L": LEATHER}, RUBBER, category="equipment")
shaped("hazmat_leggings", "tomyen:hazmat_leggings", ["RRR", "L L", "R R"], {"R": RUBBER, "L": LEATHER}, RUBBER, category="equipment")
shaped("hazmat_boots", "tomyen:hazmat_boots", ["R R", "L L"], {"R": RUBBER, "L": LEATHER}, RUBBER, category="equipment")
shaped("centrifuge", "tomyen:centrifuge", ["SXS", "GPG", "SSS"],
       {"S": STEEL_I, "X": "tomyen:computer_chip", "G": "minecraft:glass", "P": "minecraft:piston"}, STEEL_I)

# ================================================================================================= tags
MC_TAGS, C_TAGS, MY_TAGS = os.path.join(DATA, "minecraft", "tags"), os.path.join(DATA, "c", "tags"), os.path.join(MYDATA, "tags")
ORES = ["tomyen:tungsten_ore", "tomyen:uranium_ore", "tomyen:deepslate_uranium_ore"]
URANIUM_ORES = ["tomyen:uranium_ore", "tomyen:deepslate_uranium_ore"]
merge_tag(os.path.join(MC_TAGS, "block", "mineable", "pickaxe.json"),
          ORES + ["tomyen:raw_tungsten_block", "tomyen:raw_uranium_block", "tomyen:tungsten_block", "tomyen:centrifuge"])
# Tungsten ore needs a drill (tomyen:needs_drill): tools/gen_drills.py writes that tag, the drill and its recipe.
merge_tag(os.path.join(MC_TAGS, "block", "needs_iron_tool.json"),
          URANIUM_ORES + ["tomyen:raw_uranium_block", "tomyen:raw_tungsten_block", "tomyen:tungsten_block"])
for kind in ("block", "item"):
    merge_tag(os.path.join(MC_TAGS, kind, "ores.json"), ORES)
    merge_tag(os.path.join(C_TAGS, kind, "ores.json"), ["#c:ores/tungsten", "#c:ores/uranium"])
    merge_tag(os.path.join(C_TAGS, kind, "ores", "tungsten.json"), ["tomyen:tungsten_ore"])
    merge_tag(os.path.join(C_TAGS, kind, "ores", "uranium.json"), URANIUM_ORES)
    merge_tag(os.path.join(C_TAGS, kind, "ores_in_ground", "stone.json"), ["tomyen:uranium_ore"])
    merge_tag(os.path.join(C_TAGS, kind, "ores_in_ground", "deepslate.json"), ["tomyen:tungsten_ore", "tomyen:deepslate_uranium_ore"])
    merge_tag(os.path.join(C_TAGS, kind, "storage_blocks.json"),
              ["#c:storage_blocks/tungsten", "#c:storage_blocks/raw_tungsten", "#c:storage_blocks/raw_uranium"])
    merge_tag(os.path.join(C_TAGS, kind, "storage_blocks", "tungsten.json"), ["tomyen:tungsten_block"])
    merge_tag(os.path.join(C_TAGS, kind, "storage_blocks", "raw_tungsten.json"), ["tomyen:raw_tungsten_block"])
    merge_tag(os.path.join(C_TAGS, kind, "storage_blocks", "raw_uranium.json"), ["tomyen:raw_uranium_block"])
merge_tag(os.path.join(MY_TAGS, "block", "uranium_ores.json"), URANIUM_ORES)
merge_tag(os.path.join(C_TAGS, "item", "raw_materials.json"), ["#c:raw_materials/tungsten", "#c:raw_materials/uranium"])
merge_tag(os.path.join(C_TAGS, "item", "raw_materials", "tungsten.json"), ["tomyen:raw_tungsten"])
merge_tag(os.path.join(C_TAGS, "item", "raw_materials", "uranium.json"), ["tomyen:raw_uranium"])
merge_tag(os.path.join(C_TAGS, "item", "ingots.json"), ["#c:ingots/tungsten"])
merge_tag(os.path.join(C_TAGS, "item", "ingots", "tungsten.json"), ["tomyen:tungsten_ingot"])
merge_tag(os.path.join(C_TAGS, "item", "nuggets.json"), ["#c:nuggets/tungsten"])
merge_tag(os.path.join(C_TAGS, "item", "nuggets", "tungsten.json"), ["tomyen:tungsten_nugget"])
merge_tag(os.path.join(MY_TAGS, "item", "tungsten_tool_materials.json"), ["tomyen:tungsten_carbide"])
merge_tag(os.path.join(MY_TAGS, "item", "repairs_hazmat_suit.json"), ["tomyen:rubber"])
for tag, item in (("head_armor", "hazmat_helmet"), ("chest_armor", "hazmat_suit"), ("leg_armor", "hazmat_leggings"),
                  ("foot_armor", "hazmat_boots")):
    merge_tag(os.path.join(MC_TAGS, "item", tag + ".json"), ["tomyen:" + item])

# ================================================================================================= worldgen


def ore_feature(name, size, targets):
    write_json(os.path.join(MYDATA, "worldgen", "feature", name + ".json"), {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.5, "size": size,
        "targets": [{"state": state, "target": {"predicate_type": "minecraft:tag_match", "tag": tag}} for state, tag in targets]})


def ore_placement(name, count, low, high):
    write_json(os.path.join(MYDATA, "worldgen", "placed_feature", name + ".json"), {"feature": "tomyen:" + name, "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": low},
                                                      "max_inclusive": {"absolute": high}}},
        {"type": "minecraft:biome"}]})


# Tungsten: deepslate only, three small veins a chunk from y -64 to -16. Uranium: two smaller ones a chunk from -48
# to 0, in stone or deepslate: several times rarer than diamond.
ore_feature("ore_tungsten", 5, [("tomyen:tungsten_ore", "minecraft:deepslate_ore_replaceables")])
ore_placement("ore_tungsten", 3, -64, -16)
ore_feature("ore_uranium", 5, [("tomyen:uranium_ore", "minecraft:stone_ore_replaceables"),
                               ("tomyen:deepslate_uranium_ore", "minecraft:deepslate_ore_replaceables")])
ore_placement("ore_uranium", 2, -48, 0)

# ================================================================================================= sounds


def ogg(path, samples, rate):
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found: skipping " + path)
        return
    peak = max(abs(s) for s in samples) or 1.0
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "sound.wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(rate)
            out.writeframes(b"".join(struct.pack("<h", int(s / peak * 26000)) for s in samples))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "4", path],
                       check=True)


def geiger_click(seed):
    """A Geiger tube's tick: a sharp crack of noise and a ringing ping off the speaker cone, gone in 25 ms."""
    rate, rr = 22050, random.Random(seed)
    n = int(rate * 0.04)
    ring = 2600 + seed * 300
    return [((rr.uniform(-1, 1) * math.exp(-i / (rate * 0.002))) + 0.6 * math.sin(math.tau * ring * i / rate) * math.exp(-i / (rate * 0.005)))
            for i in range(n)], rate


def centrifuge_hum():
    """A second and a half of motor whine over a low rotor thrum, with a little air noise."""
    rate, rr = 22050, random.Random(5)
    n = int(rate * 1.5)
    samples, noise = [], 0.0
    for i in range(n):
        t = i / rate
        noise = noise * 0.9 + rr.uniform(-1, 1) * 0.1
        envelope = min(1.0, t / 0.08, (1.5 - t) / 0.08)
        samples.append(envelope * (0.5 * math.sin(math.tau * 120 * t) + 0.25 * math.sin(math.tau * 240 * t)
                                   + 0.18 * math.sin(math.tau * 960 * t) + 0.08 * math.sin(math.tau * 1440 * t)
                                   + 0.3 * math.sin(math.tau * 30 * t) * math.sin(math.tau * 120 * t) + noise * 0.6))
    return samples, rate


for k in range(1, 4):
    ogg(os.path.join(ASSETS, "sounds", f"geiger_click{k}.ogg"), *geiger_click(k))
ogg(os.path.join(ASSETS, "sounds", "centrifuge_hum.ogg"), *centrifuge_hum())
print("tungsten and uranium assets written")
