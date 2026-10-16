"""Paints the meteorite blocks and items, and writes everything around them.

Every texture starts from vanilla art read out of the Minecraft client jar Loom has downloaded (see
gen_longswords.client_jar()): its shapes and the light-to-dark order of its tones are kept and snapped onto short ramps
of our own (four to seven tones, no noise), and each then gets a few deliberate marks that tell it apart at a glance
from the vanilla block or item it came from:

- Meteorite Stone: blackstone's grain smoothed into broad patches of warm fusion-crust black, with two cooling cracks
  and two glassy pits. Polished stone and bricks are polished blackstone's, in the same crust colours.
- Meteoric Iron Ore: the stone above, with iron ore's flecks as nickel-iron in a Widmanstatten crosshatch, steel and
  lilac, each set in a dark seam.
- Star Crystal: amethyst's block and budding block in violet, blue and cyan with a glint or two. The buds and
  clusters are their own shape: fans of slim needles splaying out from the base like a starburst (where amethyst is
  a clump of upright shards), the tallest tipped white.
- Raw meteoric iron: raw iron's lump in fusion crust with a bite out of it, metal on its brightest face, two pits.
- Meteoric iron ingot: iron's ingot in lilac steel with etched diagonals. Star Steel ingot: netherite's chunkier
  ingot in night blue with one star on it.
- Star Steel tools and armour (items and the worn equipment textures): diamond's, the gem in night blue with a cyan
  edge.
- Star crystal shard: a four-pointed crystal star, each point lit on one side of its ridge.
- Star Lantern: a meteoric iron frame round a glowing blue pane with a white star at its heart. Star Steel block:
  netherite's plates in night blue, one star.
- Starlight Lens: the spyglass, its copper turned to meteoric steel and its amethyst lens to star crystal.

Models and block states are vanilla's own (cube_all for the stones, ores and crystal blocks; the amethyst buds' cross
models and facing variants; vanilla's stairs, slab and wall templates), and so are the loot tables (ore, silk touch
and cluster drops are copied from iron ore and amethyst). Also writes recipes with recipe-book advancements (crafting
and stonecutting), tags (merged into the shared vanilla tag files), the worn armour's equipment asset, sounds.json
entries (vanilla sounds, re-pitched) and language entries (merged into en_us.json).

Usage: python3 tools/gen_meteorites.py src/main/resources/assets/tomyen [path/to/minecraft-client.jar] [--preview out.png]
The preview sets each new texture beside the vanilla one it's closest to, eight times size."""
import json, math, os, sys, zipfile
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
PREVIEW = None
if "--preview" in sys.argv:
    i = sys.argv.index("--preview")
    PREVIEW = sys.argv[i + 1]
    del sys.argv[i:i + 2]
from gen_longswords import client_jar, read_png
from gunkit import png, write

ASSETS = sys.argv[1] if len(sys.argv) > 1 else None
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen")) if ASSETS else None
CLEAR = (0, 0, 0, 0)

# ======================================================================================================== colour


def hexc(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def ramp(stops, t):
    """A colour t (0..1) along a ramp of evenly spaced stops."""
    t = min(1.0, max(0.0, t)) * (len(stops) - 1)
    i = min(int(t), len(stops) - 2)
    f = t - i
    a, b = hexc(stops[i]), hexc(stops[i + 1])
    return tuple(int(round(a[k] + (b[k] - a[k]) * f)) for k in range(3)) + (255,)


def recolour(px, stops, lo=None, hi=None, keep=None, gamma=1.0, by_rank=False):
    """Each opaque pixel's brightness, stretched over the texture's own range (or lo..hi), mapped onto the ramp
    (gamma above 1 keeps more of it toward the dark end). By rank, the texture's distinct tones are spread evenly
    over the ramp instead, darkest to lightest, whatever their brightness."""
    ls = [lum(p) for p in px if p[3] > 0 and (keep is None or keep(p))]
    lo = min(ls) if lo is None else lo
    hi = max(ls) if hi is None else hi
    tones = sorted(set(round(v, 3) for v in ls))
    out = []
    for p in px:
        if p[3] == 0 or (keep is not None and not keep(p)):
            out.append(p)
        else:
            if by_rank:
                v = tones.index(round(lum(p), 3)) / max(1, len(tones) - 1)
            else:
                v = min(1.0, max(0.0, (lum(p) - lo) / max(1.0, hi - lo)))
            out.append(ramp(stops, v ** gamma)[:3] + (p[3],))
    return out


def shade(p, f):
    return tuple(max(0, min(255, int(v * f))) for v in p[:3]) + (p[3],)


def blend(p, q, t):
    return tuple(int(p[k] + (q[k] - p[k]) * t) for k in range(3)) + (p[3],)


def saturation(p):
    mx, mn = max(p[:3]), min(p[:3])
    return 0 if mx == 0 else (mx - mn) / mx


class Tex:
    def __init__(self, w, h, px=None):
        self.w, self.h = w, h
        self.px = list(px) if px else [CLEAR] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def opaque(self, x, y):
        return self.get(x, y)[3] > 0

    def copy(self):
        return Tex(self.w, self.h, self.px)

    def save(self, path):
        png(path, self.w, self.h, self.px)


JAR = None


def vanilla(path):
    w, h, px = read_png(JAR.read("assets/minecraft/textures/" + path + ".png"))
    return Tex(w, h, px)


def vanilla_json(path):
    return json.loads(JAR.read("assets/minecraft/" + path + ".json"), object_pairs_hook=OrderedDict)


def vanilla_data(path):
    return json.loads(JAR.read("data/minecraft/" + path + ".json"), object_pairs_hook=OrderedDict)


# ======================================================================================================= palettes

# Short ramps, four or five tones each, so every texture stays clean.
# Fusion crust: near-black with a warm cast (blackstone is a cool blue-grey).
CRUST = [0x15100F, 0x221A17, 0x31261F, 0x45372D]
CRUST_POLISHED = [0x1D1613, 0x2B211C, 0x3B2F27, 0x504035]
CRACK, CRACK_LIP = hexc(0x0A0707), hexc(0x5C3A26)
PIT, GLINT = hexc(0x080609), hexc(0x9C97BC)
# Nickel-iron: cool steel with a lilac sheen.
NICKEL = [0x3A3750, 0x6B6888, 0x9D9AC0, 0xCFCCEA]
# Star crystal: deep violet through blue to cyan and near white.
CRYSTAL = [0x2A1758, 0x4C34A8, 0x5E7FE0, 0x63D0EE, 0xC4FAFF]
# Star Steel: night blue, a cyan edge.
NIGHT = [0x0A0E2E, 0x16225E, 0x24398F, 0x3A63C4, 0x8BE6FF]
# The same, for diamond gear (seven tones, one for each of diamond's).
NIGHT_GEAR = [0x04061A, 0x0A1038, 0x141E5C, 0x1D2B78, 0x263A96, 0x3F6CCB, 0xA8F2FF]
STAR = hexc(0xF4FBFF)


def nearest(stops, v):
    """The ramp stop nearest v (0..1): posterised, never a colour between."""
    return hexc(stops[min(len(stops) - 1, max(0, int(round(v * (len(stops) - 1)))))]) + (255,)


def smoothed(t, wrap=True):
    """Each pixel's brightness averaged with its eight neighbours (wrapping round, for a tiling block texture)."""
    out = []
    for y in range(16):
        for x in range(16):
            total = 0.0
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    total += lum(t.get((x + dx) % 16, (y + dy) % 16)) * (2 if dx == dy == 0 else 1)
            out.append(total / 10.0)
    return out


def posterise(levels, stops):
    """Brightness levels stretched over their range and snapped to the ramp's stops."""
    lo, hi = min(levels), max(levels)
    return [nearest(stops, (v - lo) / max(1.0, hi - lo)) for v in levels]


def twinkle(t, x, y, arm=None):
    """A four-pointed glint: a white centre and one-pixel arms."""
    arm = arm or hexc(CRYSTAL[3])
    t.set(x, y, STAR + (255,))
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if t.opaque(x + dx, y + dy):
            t.set(x + dx, y + dy, arm + (255,))


def line(t, points, colour, lip=None):
    """A crack along these pixels, with a lighter lip under each."""
    for x, y in points:
        t.set(x, y, colour + (255,))
    if lip:
        for x, y in points:
            if (x, y + 1) not in points:
                t.set(x, y + 1, lip + (255,))


def pit(t, x, y, wide=False):
    """A glassy pit: a dark hollow, a glint on its upper left."""
    t.set(x, y, PIT + (255,))
    if wide:
        t.set(x + 1, y, PIT + (255,))
    t.set(x - 1, y - 1, GLINT + (255,))


# ========================================================================================================= blocks


def meteorite_stone():
    """Blackstone's grain smoothed into broad patches of fusion crust, two cracks and two pits."""
    t = Tex(16, 16, posterise(smoothed(vanilla("block/blackstone")), CRUST))
    line(t, [(1, 5), (2, 5), (3, 6), (4, 6), (5, 7)], CRACK, CRACK_LIP)
    line(t, [(10, 11), (11, 12), (12, 12), (13, 13)], CRACK, CRACK_LIP)
    pit(t, 11, 4, wide=True)
    pit(t, 4, 12)
    return t


def polished_meteorite_stone():
    return Tex(16, 16, posterise([lum(p) for p in vanilla("block/polished_blackstone").px], CRUST_POLISHED))


def meteorite_bricks():
    return Tex(16, 16, posterise([lum(p) for p in vanilla("block/polished_blackstone_bricks").px], CRUST))


def meteoric_iron_ore(stone):
    """The meteorite stone, with iron ore's flecks as nickel-iron etched in a crosshatch of bright lamellae."""
    ore = vanilla("block/iron_ore")
    t = stone.copy()
    # Iron ore's flecks are its warm (tan) pixels; the stone round them is grey.
    flecks = {(x, y) for y in range(16) for x in range(16) if ore.get(x, y)[0] - ore.get(x, y)[2] > 18}
    for x, y in flecks:
        # Two sets of crossing diagonals: the Widmanstatten pattern.
        tone = 3 if (x + y) % 3 == 0 else 1 if (x - y) % 3 == 0 else 2
        t.set(x, y, hexc(NICKEL[tone]) + (255,))
    # Each fleck sits in a dark seam below and to its right.
    for x, y in flecks:
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if (nx, ny) not in flecks and nx < 16 and ny < 16:
                t.set(nx, ny, hexc(CRUST[0]) + (255,))
    return t


def star_crystal_block():
    t = Tex(16, 16, posterise(smoothed(vanilla("block/amethyst_block")), CRYSTAL[1:4]))
    twinkle(t, 4, 4)
    twinkle(t, 11, 10)
    return t


def budding_star_crystal():
    t = Tex(16, 16, posterise([lum(p) for p in vanilla("block/budding_amethyst").px], CRYSTAL[:4]))
    twinkle(t, 12, 4)
    return t


def needle_fan(height, needles):
    """A bud or cluster: slim crystal needles splaying out from the base like a starburst (amethyst is a clump of
    upright shards). Each needle has a lit face, a bright ridge and a shadowed face; the middle one, the tallest,
    ends in a white point."""
    t = Tex(16, 16)
    spread = {1: [0], 3: [-30, 0, 30], 5: [-52, -25, 0, 25, 52]}[needles]
    for a in sorted(spread, key=lambda a: -abs(a)):  # the outer ones first, the middle one over them
        length = height * (1.0 - abs(a) / 110.0)
        ux, uy = math.sin(math.radians(a)), -math.cos(math.radians(a))
        sx, sy = 7.5 + ux * 1.5, 15.5
        steps = int(length * 4)
        for i in range(steps + 1):
            s = i / 4.0
            half = 1.3 * (1.0 - s / length) + 0.3
            for w in (-1, 0, 1):
                x = int(math.floor(sx + ux * s - uy * w * half * 0.7))
                y = int(math.floor(sy + uy * s + ux * w * half * 0.7))
                if 0 <= x < 16 and 0 <= y < 16:
                    face = 3 if w == 0 else 2 if w < 0 else 1
                    t.set(x, y, hexc(CRYSTAL[face]) + (255,))
        tip = (int(math.floor(sx + ux * length)), int(math.floor(sy + uy * length)))
        if a == 0 and 0 <= tip[1] < 16:
            t.set(*tip, STAR + (255,))
    return t


def star_lantern():
    """A meteoric-iron frame round a pane of star crystal glowing brightest at the middle, with a star at its heart."""
    t = Tex(16, 16)
    glow = [0x2F5FC0, 0x3E78D8, 0x62C0F0]
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                t.set(x, y, hexc(NICKEL[0]) + (255,))
            elif edge == 1:
                t.set(x, y, hexc(NICKEL[2] if x in (1, 14) and y in (1, 14) else NICKEL[1]) + (255,))
            else:
                t.set(x, y, nearest(glow, 1.0 - math.hypot(x - 7.5, y - 7.5) / 8.0))
    # The star: a 2x2 heart, arms two wide then one, a pale halo in the corners between them.
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        t.set(x, y, STAR + (255,))
    for d in range(1, 5):
        for x, y in ((7, 7 - d), (8, 7 - d), (7, 8 + d), (8, 8 + d), (7 - d, 7), (7 - d, 8), (8 + d, 7), (8 + d, 8)):
            if d <= 2 or (d <= 4 and (x in (7, 8) and (y < 7 and x == 7 or y > 8 and x == 8) or y in (7, 8) and (x < 7 and y == 8 or x > 8 and y == 7))):
                t.set(x, y, STAR + (255,))
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        t.set(x, y, hexc(0xA6F0FA) + (255,))
    return t


def star_steel_block():
    t = Tex(16, 16, posterise([lum(p) for p in vanilla("block/netherite_block").px], NIGHT[:4]))
    twinkle(t, 11, 4, arm=hexc(NIGHT[4]))
    return t


# ========================================================================================================== items


def outline(t, colour):
    """Darkens the opaque pixels on the sprite's edge, the way vanilla items are outlined."""
    edge = [(x, y) for y in range(t.h) for x in range(t.w) if t.opaque(x, y)
            and not all(t.opaque(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in edge:
        t.set(x, y, colour)


def interior(t, x, y):
    return all(t.opaque(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))


def raw_meteoric_iron():
    """Raw iron's lump in fusion crust, with a bite out of its outline, metal showing on its brightest face, and two
    pits."""
    src = vanilla("item/raw_iron")
    t = Tex(16, 16, recolour(src.px, CRUST + [0x5A483C], by_rank=True))
    ls = sorted(lum(p) for p in src.px if p[3])
    for y in range(16):
        for x in range(16):
            if src.get(x, y)[3] and lum(src.get(x, y)) >= ls[int(len(ls) * 0.8)]:
                t.set(x, y, hexc(NICKEL[2]) + (255,))
    for x, y in ((12, 3), (13, 4), (12, 4)):
        t.set(x, y, CLEAR)
    outline(t, hexc(CRUST[0]) + (255,))
    spots = [(x, y) for y in range(16) for x in range(16) if interior(t, x, y) and interior(t, x - 1, y - 1)]
    for x, y in (spots[len(spots) // 3], spots[2 * len(spots) // 3]):
        pit(t, x, y)
    return t


def meteoric_iron_ingot():
    """Iron's ingot in lilac steel, a few etched lines across its top face."""
    src = vanilla("item/iron_ingot")
    t = Tex(16, 16, recolour(src.px, NICKEL, by_rank=True))
    ls = sorted(lum(p) for p in src.px if p[3])
    for y in range(16):
        for x in range(16):
            p = src.get(x, y)
            if p[3] and lum(p) >= ls[int(len(ls) * 0.55)] and interior(t, x, y) and (x + y) % 4 == 0:
                t.set(x, y, hexc(NICKEL[1]) + (255,))
    return t


def star_steel_ingot():
    """Netherite's chunkier ingot in night blue, one star caught on its face."""
    t = Tex(16, 16, recolour(vanilla("item/netherite_ingot").px, NIGHT, by_rank=True))
    bright = [(x, y) for y in range(16) for x in range(16) if interior(t, x, y) and lum(t.get(x, y)) > 90]
    if bright:
        twinkle(t, *min(bright, key=lambda p: p[1] * 2 - p[0]), arm=hexc(NIGHT[4]))
    return t


def star_crystal_shard():
    """A four-pointed crystal star, each point split along its ridge into a lit side and a shadowed one."""
    t = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 7.5, y + 0.5 - 7.5
            if (abs(dx) / 6.6) ** 0.7 + (abs(dy) / 7.6) ** 0.7 > 1.0:
                continue
            ridge = abs(dx) < 0.6 or abs(dy) < 0.6
            lit = (dx < 0) if abs(dy) > abs(dx) else (dy < 0)
            t.set(x, y, hexc(CRYSTAL[4 if ridge and lit else 3 if lit else 1]) + (255,))
    outline(t, hexc(CRYSTAL[0]) + (255,))
    t.set(7, 7, STAR + (255,))
    return t


def is_gem(p):
    """A diamond tool or armour pixel that's the gem, not the stick or its brown outline."""
    return p[3] > 0 and (p[2] > p[0] + 8 or p[1] > p[0] + 8)


def edge_glint(t, src):
    """Brightens the gem's upper-left edge to cyan, the way light catches a polished edge."""
    for y in range(t.h):
        for x in range(t.w):
            if is_gem(src.get(x, y)) and not is_gem(src.get(x - 1, y - 1)) and src.get(x - 1, y - 1)[3] > 0:
                t.set(x, y, hexc(NIGHT_GEAR[5]) + (255,))


def star_steel(path):
    """Diamond's tool or armour piece, the gem in night blue edged with cyan."""
    src = vanilla(path)
    t = Tex(src.w, src.h, recolour(src.px, NIGHT_GEAR, keep=is_gem, by_rank=True))
    edge_glint(t, src)
    return t


def star_steel_equipment(path):
    src = vanilla(path)
    return Tex(src.w, src.h, recolour(src.px, NIGHT_GEAR, by_rank=True))


def is_copper(p):
    return p[3] > 0 and p[0] > p[2] + 20


def starlight_lens(path):
    """The spyglass: copper to meteoric steel, the amethyst lens to star crystal."""
    src = vanilla(path)
    px = recolour(src.px, NICKEL, keep=is_copper, by_rank=True)
    px = recolour(px, CRYSTAL[1:], keep=lambda p: p[3] > 0 and not is_copper(p) and p[2] > p[1], by_rank=True)
    return Tex(src.w, src.h, px)


def meteor_glow():
    """The meteor's billboard: a soft round glow, white, tinted by the renderer."""
    t = Tex(32, 32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16
            if d < 1:
                t.set(x, y, (255, 255, 255, int(255 * (1 - d * d) ** 2)))
    return t


# ======================================================================================================== preview


def preview(pairs, path, scale=8):
    """Each (vanilla, ours) pair side by side, scaled up, on a mid grey; pairs in rows of four."""
    per_row = 4
    cell = 16 * scale
    gap = scale * 2
    rows = (len(pairs) + per_row - 1) // per_row
    w = per_row * (2 * cell + gap * 2) + gap
    h = rows * (cell + gap) + gap
    out = [(0x6B, 0x6B, 0x6B, 255)] * (w * h)
    for i, (a, b) in enumerate(pairs):
        ox = gap + (i % per_row) * (2 * cell + gap * 2)
        oy = gap + (i // per_row) * (cell + gap)
        for k, tex in enumerate((a, b)):
            for y in range(cell):
                for x in range(cell):
                    p = tex.get(x * tex.w // cell, y * tex.h // cell)
                    if p[3]:
                        bg = out[(oy + y) * w + ox + k * cell + x]
                        out[(oy + y) * w + ox + k * cell + x] = blend(bg, p[:3] + (255,), p[3] / 255)
    png(path, w, h, out)


# ======================================================================================================= textures


def textures():
    """Every texture: {path under textures/: (Tex, the vanilla texture it's set beside in the preview)}."""
    stone = meteorite_stone()
    out = OrderedDict()
    out["block/meteorite_stone"] = (stone, "block/blackstone")
    out["block/meteoric_iron_ore"] = (meteoric_iron_ore(stone), "block/iron_ore")
    out["block/polished_meteorite_stone"] = (polished_meteorite_stone(), "block/polished_blackstone")
    out["block/meteorite_bricks"] = (meteorite_bricks(), "block/polished_blackstone_bricks")
    out["block/star_crystal_block"] = (star_crystal_block(), "block/amethyst_block")
    out["block/budding_star_crystal"] = (budding_star_crystal(), "block/budding_amethyst")
    out["block/small_star_crystal_bud"] = (needle_fan(5, 3), "block/small_amethyst_bud")
    out["block/medium_star_crystal_bud"] = (needle_fan(8, 3), "block/medium_amethyst_bud")
    out["block/large_star_crystal_bud"] = (needle_fan(11, 5), "block/large_amethyst_bud")
    out["block/star_crystal_cluster"] = (needle_fan(14, 5), "block/amethyst_cluster")
    out["block/star_lantern"] = (star_lantern(), "block/sea_lantern")
    out["block/star_steel_block"] = (star_steel_block(), "block/netherite_block")
    out["item/raw_meteoric_iron"] = (raw_meteoric_iron(), "item/raw_iron")
    out["item/meteoric_iron_ingot"] = (meteoric_iron_ingot(), "item/iron_ingot")
    out["item/star_steel_ingot"] = (star_steel_ingot(), "item/netherite_ingot")
    out["item/star_crystal_shard"] = (star_crystal_shard(), "item/amethyst_shard")
    for piece in (("sword", "shovel", "pickaxe", "axe", "hoe", "helmet", "chestplate", "leggings", "boots")):
        out["item/star_steel_" + piece] = (star_steel("item/diamond_" + piece), "item/netherite_" + piece)
    out["item/starlight_lens"] = (starlight_lens("item/spyglass"), "item/spyglass")
    out["item/starlight_lens_model"] = (starlight_lens("item/spyglass_model"), "item/spyglass_model")
    out["entity/equipment/humanoid/star_steel"] = (star_steel_equipment("entity/equipment/humanoid/diamond"),
                                                  "entity/equipment/humanoid/netherite")
    out["entity/equipment/humanoid_leggings/star_steel"] = (star_steel_equipment("entity/equipment/humanoid_leggings/diamond"),
                                                           "entity/equipment/humanoid_leggings/netherite")
    out["entity/meteor"] = (meteor_glow(), None)
    return out


def main():
    global JAR
    if not ASSETS:
        sys.exit(__doc__.rsplit("Usage: ", 1)[1])
    jar = client_jar()
    print("vanilla textures from", jar)
    with zipfile.ZipFile(jar) as z:
        JAR = z
        tex = textures()
        for path, (t, _) in tex.items():
            t.save(os.path.join(ASSETS, "textures", path + ".png"))
        if PREVIEW:
            preview([(vanilla(v), t) for t, v in tex.values() if v and t.w == 16], PREVIEW)
            print("preview written to", PREVIEW)
        write_data()
    print("meteorite assets written")


# ========================================================================================== models and block states

M = "tomyen:"
FAMILIES = [  # (base block, stairs, slab, wall or None)
    ("meteorite_stone", "meteorite_stone_stairs", "meteorite_stone_slab", "meteorite_stone_wall"),
    ("polished_meteorite_stone", "polished_meteorite_stone_stairs", "polished_meteorite_stone_slab", None),
    ("meteorite_bricks", "meteorite_brick_stairs", "meteorite_brick_slab", "meteorite_brick_wall"),
]
CUBES = ["meteorite_stone", "meteoric_iron_ore", "polished_meteorite_stone", "meteorite_bricks", "star_crystal_block",
         "budding_star_crystal", "star_lantern", "star_steel_block"]
# Ours, and the vanilla amethyst stage each copies its block state, item model and loot from.
CRYSTALS = [("small_star_crystal_bud", "small_amethyst_bud"), ("medium_star_crystal_bud", "medium_amethyst_bud"),
            ("large_star_crystal_bud", "large_amethyst_bud"), ("star_crystal_cluster", "amethyst_cluster")]
TOOLS = ["sword", "shovel", "pickaxe", "axe", "hoe"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
FLAT_ITEMS = ["raw_meteoric_iron", "meteoric_iron_ingot", "star_steel_ingot", "star_crystal_shard"] + ["star_steel_" + a for a in ARMOR]


def renamed(obj, names):
    """A vanilla JSON with vanilla ids swapped for ours: every string equal to a key of names becomes its value."""
    if isinstance(obj, dict):
        return OrderedDict((k, renamed(v, names)) for k, v in obj.items())
    if isinstance(obj, list):
        return [renamed(v, names) for v in obj]
    if isinstance(obj, str):
        return names.get(obj, obj)
    return obj


def item_def(name, model):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", model)])})


def block_model(name, parent, **textures):
    write(os.path.join(ASSETS, "models", "block", name + ".json"), OrderedDict([("parent", parent), ("textures", OrderedDict(textures))]))


def item_model(name, parent, layer0):
    write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([("parent", parent), ("textures", OrderedDict(layer0=layer0))]))


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def models():
    for name in CUBES:
        block_model(name, "minecraft:block/cube_all", all=M + "block/" + name)
        blockstate(name, {"variants": {"": {"model": M + "block/" + name}}})
        item_def(name, M + "block/" + name)
    # Stairs, slabs and walls: vanilla blackstone's block states, pointed at our models on vanilla's templates.
    for base, stairs, slab, wall in FAMILIES:
        tex = M + "block/" + base
        for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            block_model(stairs + suffix, "minecraft:block/" + parent, bottom=tex, side=tex, top=tex)
        blockstate(stairs, renamed(vanilla_json("blockstates/blackstone_stairs"), {
            "minecraft:block/blackstone_stairs" + s: M + "block/" + stairs + s for s in ("", "_inner", "_outer")}))
        item_def(stairs, M + "block/" + stairs)
        block_model(slab, "minecraft:block/slab", bottom=tex, side=tex, top=tex)
        block_model(slab + "_top", "minecraft:block/slab_top", bottom=tex, side=tex, top=tex)
        blockstate(slab, renamed(vanilla_json("blockstates/blackstone_slab"), {
            "minecraft:block/blackstone_slab": M + "block/" + slab, "minecraft:block/blackstone_slab_top": M + "block/" + slab + "_top",
            "minecraft:block/blackstone": M + "block/" + base}))
        item_def(slab, M + "block/" + slab)
        if wall:
            for suffix, parent in (("_post", "template_wall_post"), ("_side", "template_wall_side"),
                                   ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")):
                block_model(wall + suffix, "minecraft:block/" + parent, wall=tex)
            blockstate(wall, renamed(vanilla_json("blockstates/blackstone_wall"), {
                "minecraft:block/blackstone_wall" + s: M + "block/" + wall + s for s in ("_post", "_side", "_side_tall")}))
            item_def(wall, M + "block/" + wall + "_inventory")
    # Buds and clusters: the amethyst stages' cross models, facing variants and held models.
    for ours, theirs in CRYSTALS:
        block_model(ours, "minecraft:block/cross", cross=M + "block/" + ours)
        blockstate(ours, renamed(vanilla_json("blockstates/" + theirs), {"minecraft:block/" + theirs: M + "block/" + ours}))
        write(os.path.join(ASSETS, "models", "item", ours + ".json"),
              renamed(vanilla_json("models/item/" + theirs), {"minecraft:block/" + theirs: M + "block/" + ours}))
        item_def(ours, M + "item/" + ours)
        write(os.path.join(ASSETS, "textures", "block", ours + ".png.mcmeta"), json.loads(JAR.read("assets/minecraft/textures/block/" + theirs + ".png.mcmeta")))
    for name in FLAT_ITEMS:
        item_model(name, "minecraft:item/generated", M + "item/" + name)
        item_def(name, M + "item/" + name)
    for tool in TOOLS:
        item_model("star_steel_" + tool, "minecraft:item/handheld", M + "item/star_steel_" + tool)
        item_def("star_steel_" + tool, M + "item/star_steel_" + tool)
    # The Starlight Lens: the spyglass's flat icon and its in-hand tube, retextured.
    item_model("starlight_lens", "minecraft:item/generated", M + "item/starlight_lens")
    write(os.path.join(ASSETS, "models", "item", "starlight_lens_in_hand.json"), OrderedDict([
        ("parent", "minecraft:item/spyglass_in_hand"), ("textures", OrderedDict(spyglass=M + "item/starlight_lens_model"))]))
    write(os.path.join(ASSETS, "items", "starlight_lens.json"), renamed(vanilla_json("items/spyglass"), {
        "minecraft:item/spyglass": M + "item/starlight_lens", "minecraft:item/spyglass_in_hand": M + "item/starlight_lens_in_hand"}))
    write(os.path.join(ASSETS, "equipment", "star_steel.json"), {"layers": OrderedDict([
        ("humanoid", [{"texture": M + "star_steel"}]), ("humanoid_leggings", [{"texture": M + "star_steel"}])])})


# ===================================================================================================== loot tables


def loot(name, obj):
    write(os.path.join(DATA, "loot_table", "blocks", name + ".json"), obj)


def self_drop(name):
    """Vanilla's plain block drop (blackstone wall's), unless an explosion destroys it."""
    loot(name, renamed(vanilla_data("loot_table/blocks/blackstone_wall"), {
        "minecraft:blackstone_wall": M + name, "minecraft:blocks/blackstone_wall": M + "blocks/" + name}))


def loot_tables():
    for base, stairs, slab, wall in FAMILIES:
        for name in (base, stairs, wall):
            if name:
                self_drop(name)
        loot(slab, renamed(vanilla_data("loot_table/blocks/blackstone_slab"), {
            "minecraft:blackstone_slab": M + slab, "minecraft:blocks/blackstone_slab": M + "blocks/" + slab}))
    for name in ("star_crystal_block", "star_lantern", "star_steel_block"):
        self_drop(name)
    # Iron ore's: silk touch keeps the ore, otherwise raw iron with Fortune.
    loot("meteoric_iron_ore", renamed(vanilla_data("loot_table/blocks/iron_ore"), {
        "minecraft:iron_ore": M + "meteoric_iron_ore", "minecraft:raw_iron": M + "raw_meteoric_iron",
        "minecraft:blocks/iron_ore": M + "blocks/meteoric_iron_ore"}))
    # Amethyst's: budding drops nothing; buds only with silk touch; a cluster gives shards (four by pickaxe, Fortune).
    loot("budding_star_crystal", renamed(vanilla_data("loot_table/blocks/budding_amethyst"), {
        "minecraft:blocks/budding_amethyst": M + "blocks/budding_star_crystal"}))
    for ours, theirs in CRYSTALS:
        loot(ours, renamed(vanilla_data("loot_table/blocks/" + theirs), {
            "minecraft:" + theirs: M + ours, "minecraft:amethyst_shard": M + "star_crystal_shard",
            "minecraft:blocks/" + theirs: M + "blocks/" + ours}))


# ===================================================================================== recipes and their advancements


def advancement(name, book, unlock):
    write(os.path.join(DATA, "advancement", "recipes", book, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": unlock}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": M + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": [M + name]})]))


def shaped(name, result, pattern, key, unlock, count=1, category="misc", book="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, book, unlock)


def shapeless(name, result, ingredients, unlock, count=1, category="misc", book="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, book, unlock)


def stonecutting(result, source, count=1):
    name = f"{result}_from_{source}_stonecutting"
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:stonecutting"), ("ingredient", M + source), ("result", OrderedDict([("count", count), ("id", M + result)]))]))
    advancement(name, "building_blocks", M + source)


def recipes():
    building = dict(category="building", book="building_blocks")
    stone, polished = M + "meteorite_stone", M + "polished_meteorite_stone"
    shaped("polished_meteorite_stone", polished, ["##", "##"], {"#": stone}, stone, 4, **building)
    shaped("meteorite_bricks", M + "meteorite_bricks", ["##", "##"], {"#": polished}, polished, 4, **building)
    for base, stairs, slab, wall in FAMILIES:
        shaped(stairs, M + stairs, ["#  ", "## ", "###"], {"#": M + base}, M + base, 4, **building)
        shaped(slab, M + slab, ["###"], {"#": M + base}, M + base, 6, **building)
        if wall:
            shaped(wall, M + wall, ["###", "###"], {"#": M + base}, M + base, 6, book="decorations")
    # Stonecutting down the chain: stone to polished to bricks, and each of them to its own shapes and those after.
    for i, (source, _, _, _) in enumerate(FAMILIES):
        for base, stairs, slab, wall in FAMILIES[i:]:
            if base != source:
                stonecutting(base, source)
            stonecutting(stairs, source)
            stonecutting(slab, source, 2)
            if wall:
                stonecutting(wall, source)

    shard, ingot, steel = M + "star_crystal_shard", M + "meteoric_iron_ingot", M + "star_steel_ingot"
    shaped("star_crystal_block", M + "star_crystal_block", ["##", "##"], {"#": shard}, shard, **building)
    shaped("star_lantern", M + "star_lantern", ["NSN", "SGS", "NSN"],
           {"N": "minecraft:iron_nugget", "S": shard, "G": "minecraft:glass"}, shard, category="building", book="decorations")
    shaped("starlight_lens", M + "starlight_lens", [" S ", " I ", " I "], {"S": shard, "I": ingot}, shard,
           category="equipment", book="tools")
    shaped("star_steel_block", M + "star_steel_block", ["###", "###", "###"], {"#": steel}, steel, **building)
    shapeless("star_steel_ingot_from_star_steel_block", steel, [M + "star_steel_block"], M + "star_steel_block", 9)
    stick = "minecraft:stick"
    tool = dict(category="equipment", book="tools")
    shaped("star_steel_sword", M + "star_steel_sword", ["X", "X", "#"], {"X": steel, "#": stick}, steel, category="equipment", book="combat")
    shaped("star_steel_shovel", M + "star_steel_shovel", ["X", "#", "#"], {"X": steel, "#": stick}, steel, **tool)
    shaped("star_steel_pickaxe", M + "star_steel_pickaxe", ["XXX", " # ", " # "], {"X": steel, "#": stick}, steel, **tool)
    shaped("star_steel_axe", M + "star_steel_axe", ["XX", "X#", " #"], {"X": steel, "#": stick}, steel, **tool)
    shaped("star_steel_hoe", M + "star_steel_hoe", ["XX", " #", " #"], {"X": steel, "#": stick}, steel, **tool)
    armor = dict(category="equipment", book="combat")
    shaped("star_steel_helmet", M + "star_steel_helmet", ["XXX", "X X"], {"X": steel}, steel, **armor)
    shaped("star_steel_chestplate", M + "star_steel_chestplate", ["X X", "XXX", "XXX"], {"X": steel}, steel, **armor)
    shaped("star_steel_leggings", M + "star_steel_leggings", ["XXX", "X X", "X X"], {"X": steel}, steel, **armor)
    shaped("star_steel_boots", M + "star_steel_boots", ["X X", "X X"], {"X": steel}, steel, **armor)


# ============================================================================================================ tags


def merge_tag(path, values):
    """Adds values to a tag file, keeping whatever's there (other features share the vanilla tag files)."""
    full = os.path.normpath(os.path.join(DATA, "..", path + ".json"))
    existing = []
    if os.path.exists(full):
        with open(full) as f:
            existing = json.load(f)["values"]
    write(full, {"values": existing + [v for v in values if v not in existing]})


def tags():
    stones = [n for family in FAMILIES for n in family if n] + ["meteoric_iron_ore"]
    crystals = ["star_crystal_block", "budding_star_crystal"] + [c for c, _ in CRYSTALS]
    merge_tag("minecraft/tags/block/mineable/pickaxe", [M + n for n in stones + crystals + ["star_lantern", "star_steel_block"]])
    # Meteorite stone and meteoric iron ore need drills (tomyen:needs_drill, needs_powered_drill): see tools/gen_drills.py.
    merge_tag("minecraft/tags/block/needs_diamond_tool", [M + "star_steel_block"])
    merge_tag("minecraft/tags/block/beacon_base_blocks", [M + "star_steel_block"])
    merge_tag("minecraft/tags/block/crystal_sound_blocks", [M + "star_crystal_block", M + "budding_star_crystal"])
    for kind in ("block", "item"):
        merge_tag(f"minecraft/tags/{kind}/ores", [M + "meteoric_iron_ore"])
        merge_tag(f"minecraft/tags/{kind}/stairs", [M + f[1] for f in FAMILIES])
        merge_tag(f"minecraft/tags/{kind}/slabs", [M + f[2] for f in FAMILIES])
        merge_tag(f"minecraft/tags/{kind}/walls", [M + f[3] for f in FAMILIES if f[3]])
    for tool, tag in zip(TOOLS, ("swords", "shovels", "pickaxes", "axes", "hoes")):
        merge_tag("minecraft/tags/item/" + tag, [M + "star_steel_" + tool])
    for piece, tag in zip(ARMOR, ("head_armor", "chest_armor", "leg_armor", "foot_armor")):
        merge_tag("minecraft/tags/item/" + tag, [M + "star_steel_" + piece])
    write(os.path.join(DATA, "tags", "item", "star_steel_tools.json"), {"values": [M + "star_steel_" + t for t in TOOLS]})
    write(os.path.join(DATA, "tags", "item", "star_steel_tool_materials.json"), {"values": [M + "star_steel_ingot"]})


# ================================================================================================= sounds and lang


def merge_json(path, entries):
    """Adds (or updates) keys in a JSON object file, keeping its order and everything else in it."""
    with open(path) as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def sounds():
    merge_json(os.path.join(ASSETS, "sounds.json"), OrderedDict([
        # The rush of air past a falling meteor: the elytra's wind, high and thin.
        ("meteor_whistle", OrderedDict([("subtitle", "subtitles.tomyen.meteor_whistle"), ("sounds", [
            OrderedDict([("name", "minecraft:item/elytra/elytra_loop"), ("pitch", 1.7)])])])),
        # A deep, slowed explosion.
        ("meteor_impact", OrderedDict([("subtitle", "subtitles.tomyen.meteor_impact"), ("sounds", [
            OrderedDict([("name", f"minecraft:random/explode{i}"), ("pitch", 0.55)]) for i in range(1, 5)])])),
    ]))


BLOCK_NAMES = OrderedDict([
    ("meteorite_stone", "Meteorite Stone"), ("meteorite_stone_stairs", "Meteorite Stone Stairs"),
    ("meteorite_stone_slab", "Meteorite Stone Slab"), ("meteorite_stone_wall", "Meteorite Stone Wall"),
    ("polished_meteorite_stone", "Polished Meteorite Stone"), ("polished_meteorite_stone_stairs", "Polished Meteorite Stone Stairs"),
    ("polished_meteorite_stone_slab", "Polished Meteorite Stone Slab"), ("meteorite_bricks", "Meteorite Bricks"),
    ("meteorite_brick_stairs", "Meteorite Brick Stairs"), ("meteorite_brick_slab", "Meteorite Brick Slab"),
    ("meteorite_brick_wall", "Meteorite Brick Wall"), ("meteoric_iron_ore", "Meteoric Iron Ore"),
    ("star_crystal_block", "Block of Star Crystal"), ("budding_star_crystal", "Budding Star Crystal"),
    ("small_star_crystal_bud", "Small Star Crystal Bud"), ("medium_star_crystal_bud", "Medium Star Crystal Bud"),
    ("large_star_crystal_bud", "Large Star Crystal Bud"), ("star_crystal_cluster", "Star Crystal Cluster"),
    ("star_lantern", "Star Lantern"), ("star_steel_block", "Block of Star Steel"),
])
ITEM_NAMES = OrderedDict([
    ("raw_meteoric_iron", "Raw Meteoric Iron"), ("meteoric_iron_ingot", "Meteoric Iron Ingot"),
    ("star_steel_ingot", "Star Steel Ingot"), ("star_crystal_shard", "Star Crystal Shard"), ("starlight_lens", "Starlight Lens"),
] + [("star_steel_" + p, "Star Steel " + p.capitalize()) for p in TOOLS + ARMOR])


def lang():
    entries = OrderedDict()
    for name, text in BLOCK_NAMES.items():
        entries["block.tomyen." + name] = text
    for name, text in ITEM_NAMES.items():
        entries["item.tomyen." + name] = text
    entries.update([
        ("entity.tomyen.meteor", "Meteor"),
        ("subtitles.tomyen.meteor_whistle", "Meteor whistles"),
        ("subtitles.tomyen.meteor_impact", "Meteor crashes"),
        ("message.tomyen.meteor_falling", "A meteor is falling!"),
        ("gamerule.tomyen.meteor_showers", "Meteor showers"),
        ("gamerule.tomyen.meteor_showers.description", "Whether meteors fall near players on clear nights."),
        ("gamerule.tomyen.meteor_chance", "Meteor chance"),
        ("gamerule.tomyen.meteor_chance.description", "The chance, in percent, of a meteor falling near each player in a night."),
    ])
    merge_json(os.path.join(ASSETS, "lang", "en_us.json"), entries)


def write_data():
    models()
    loot_tables()
    recipes()
    tags()
    sounds()
    lang()


if __name__ == "__main__":
    main()
