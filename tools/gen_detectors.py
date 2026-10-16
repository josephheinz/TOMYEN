"""Paints the detectors and writes everything around them (see com.jheinz.tomyen.detector).

Every sprite is a flat 16x16 item texture in vanilla's manner: each part a shape with a one-texel outline, a
highlight along its upper-left edge and a shadow along its lower-right one, and small palettes with no noise. Parts
borrow vanilla's colours where they are made of vanilla things (the stick, the string, the iron and copper nuggets, the
amethyst shard), and the two books start from vanilla's own book sprite, recoloured and given their own marks. Moving
parts are separate frames picked by the tomyen:detector item model property (see DetectorProperty):

  prospectors_pendulum_0..6   the bob from far left to far right (reading "pendulum", -1..1)
  metal_detector_0..3         the LED: off, green, amber, red (reading "signal")
  meteorite_scanner           a dark screen; _0.._7 its arrow, ahead then clockwise (reading "bearing", 8 sectors)
  dowsing_rod / fae_dowsing_rod: idle, then _ahead (dipping), _right, _left (reading "bearing", 4 sectors)
  ufo_radar, _contact, _alert  animated sweeps (8 frames each), by reading "signal" 0, 0.5, 1
  lunar_almanac_0..7          the moon on its cover, by vanilla's minecraft:time moon_phase source
  ledger

Also: item models and definitions, recipes with recipe-book advancements, sounds.json entries (vanilla sounds
re-pitched) and the English names and HUD strings, merged into the shared files.

With --preview PATH it also writes a sheet of every sprite, scaled up beside the vanilla items they're made from,
and mockups of each gadget's HUD panel as DetectorHud draws it.

Usage: python3 tools/gen_detectors.py src/main/resources/assets/tomyen [--preview path.png]"""
import json, math, os, sys, zipfile

sys.path.insert(0, os.path.dirname(__file__))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
ARGS = [a for a in sys.argv[1:] if not a.startswith("--") and a != PREVIEW]
sys.argv = [sys.argv[0]]
from gen_longswords import client_jar, read_png
from gunkit import png

ASSETS = ARGS[0] if ARGS else sys.exit(__doc__.rsplit("Usage: ", 1)[1])
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
TEX = os.path.join(ASSETS, "textures", "item")
JAR = zipfile.ZipFile(client_jar())
CLEAR = (0, 0, 0, 0)


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


class Canvas:
    def __init__(self, w=16, h=16, px=None):
        self.w, self.h = w, h
        self.px = list(px) if px else [CLEAR] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = rgba(c) if isinstance(c, int) else c

    def copy(self):
        return Canvas(self.w, self.h, self.px)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        png(path, self.w, self.h, self.px)


def vanilla(name):
    w, h, px = read_png(JAR.read(f"assets/minecraft/textures/{name}.png"))
    return Canvas(w, h, px)


# ================================================================================================= shapes
# A shape is a set of (x, y) texels. paint() gives it vanilla's bevel: the edge texels the outline colour, inner
# texels touching the edge at the top or left the highlight, at the bottom or right the shadow, the rest the base.

def ellipse(cx, cy, rx, ry):
    return {(x, y) for x in range(16) for y in range(16) if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0}


def rect(x0, y0, x1, y1):
    """Texels x0..x1, y0..y1 inclusive."""
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


def rounded(x0, y0, x1, y1):
    return rect(x0, y0, x1, y1) - {(x0, y0), (x1, y0), (x0, y1), (x1, y1)}


def line(x0, y0, x1, y1):
    """The texels of a line, one per step along its longer axis."""
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    return [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]


def thick(points):
    """A line one texel wider, so it has room for an outline either side of its core."""
    out = set()
    for x, y in points:
        out |= {(x, y), (x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)}
    return out


def paint(c, shape, ramp, light=(-1, -1)):
    """ramp: outline, shadow, base, highlight[, specular]. light: the direction the light comes from."""
    outline, shadow, base, high = ramp[:4]
    for x, y in shape:
        n = [(x + dx, y + dy) in shape for dx, dy in ((0, -1), (-1, 0), (0, 1), (1, 0))]
        if not all(n):
            c.set(x, y, outline)
        elif (x + light[0], y) not in shape or (x, y + light[1]) not in shape:
            c.set(x, y, high)
        elif (x - light[0], y) not in shape or (x, y - light[1]) not in shape:
            c.set(x, y, shadow)
        else:
            c.set(x, y, base)


def stick(c, points, ramp):
    """A stick along these texels, vanilla style: the core alternating light and mid, a dark edge above or left of
    it and the darkest below or right."""
    outline, dark, mid, light = ramp
    core = set(points)
    edge = thick(points) - core
    for x, y in edge:
        below_right = (x - 1, y) in core or (x, y - 1) in core
        c.set(x, y, outline if below_right else dark)
    for i, (x, y) in enumerate(points):
        c.set(x, y, light if i % 2 == 0 else mid)


# ================================================================================================= palettes
IRON = [0x393C40, 0x738D8D, 0xA2B0BE, 0xD9DFE7, 0xF2F2F2]            # vanilla iron nugget
COPPER = [0x6D3421, 0x9C4E31, 0xC15A36, 0xE77C56, 0xFC9982]          # vanilla copper nugget
STEEL = [0x22272D, 0x48515B, 0x6C7783, 0x95A1AD, 0xC3CCD5]           # the mod's steel, a cool blue-grey
GUNMETAL = [0x1A1D21, 0x30353B, 0x454C53, 0x5F6870]
RUBBER = [0x0E0E10, 0x1E1F22, 0x2E3034, 0x45484D]
WOOD = [0x281E0B, 0x493615, 0x684E1E, 0x896727]                        # vanilla stick
GLIMMER = [0x241D4A, 0x3F4A8C, 0x6C82C8, 0x9FBBF2]                     # glimmerwood, pale and blue
AMETHYST = [0x54398A, 0x8D6ACC, 0xB38EF3, 0xFECBE6]                    # vanilla amethyst shard
FAE = [0x6E2A6E, 0xB84FB0, 0xF08CD8, 0xFFD6F5]
CYAN_SCREEN = [0x0A1A22, 0x12465A, 0x5CE1E6, 0xD6FCFF]
GREEN_SCREEN = [0x08160D, 0x173D22, 0x5CFF7A, 0xC8FFD2]
STRING = 0xD9D9D9


# ================================================================================================= sprites
PENDULUM_ANGLES = [-26, -17, -8, 0, 8, 17, 26]


def pendulum(angle):
    c = Canvas()
    # The copper finger knob the string hangs from.
    paint(c, ellipse(8.0, 1.6, 2.1, 1.5), COPPER[:2] + COPPER[2:4])
    c.set(7, 1, COPPER[4])
    a = math.radians(angle)
    length = 7.2
    ex, ey = 7.5 + math.sin(a) * length, 2.6 + math.cos(a) * length
    for x, y in line(7, 3, round(ex - 0.5), round(ey) - 1)[:-1]:
        c.set(x, y, STRING)
    bx, by = round(ex - 0.5), round(ey)
    # The bob: an iron drop with a copper cap, pointing down.
    bob = ellipse(bx + 0.5, by + 1.9, 2.5, 2.1) | {(bx, by + 4)}
    paint(c, bob, IRON[:4])
    c.set(bx - 1, by + 1, IRON[4])
    for x in range(bx - 1, bx + 2):
        if (x, by) in bob:
            c.set(x, by, COPPER[3] if x < bx + 1 else COPPER[2])
    c.set(bx, by - 1, COPPER[1])
    c.set(bx, by + 4, IRON[0])
    return c


def metal_detector(level):
    c = Canvas()
    # The rubber grip at the top right, the control box just below it, the shaft, and the copper search coil.
    stick(c, line(5, 11, 11, 5), [IRON[0], IRON[1], IRON[2], IRON[3]])
    stick(c, line(12, 3, 14, 1), RUBBER)
    paint(c, rounded(9, 3, 13, 7), GUNMETAL)
    c.set(10, 4, GUNMETAL[3])
    led = [0x3A2626, 0x4CD964, 0xFFD23F, 0xFF5A4C][level]
    c.set(11, 5, led)
    c.set(12, 5, 0x1D5A55)
    c.set(11, 6, 0x0F2E2E)
    c.set(12, 6, 0x123A3A)
    coil = ellipse(4.6, 12.9, 4.6, 2.5)
    paint(c, coil, COPPER[:4])
    for x in range(3, 7):
        c.set(x, 13, COPPER[0])
    for x in (2, 3):
        c.set(x, 12, COPPER[4])
    return c


ARROW_UP = ["..X..", ".XXX.", "X.X.X", "..X..", "..X.."]
ARROW_UR = [".XXXX", "...XX", "..X.X", ".X..X", "X...."]


def rotate(rows):
    return ["".join(rows[len(rows) - 1 - x][y] for x in range(len(rows))) for y in range(len(rows[0]))]


def arrows():
    up, ur = ARROW_UP, ARROW_UR
    out = []
    for i in range(4):
        out += [up, ur]
        up, ur = rotate(up), rotate(ur)
    return out


def scanner(direction):
    """direction: None for no signal, else 0..7 clockwise from ahead."""
    c = Canvas()
    paint(c, rounded(2, 2, 14, 15), STEEL[:4])
    c.set(3, 3, STEEL[4])
    c.set(4, 3, STEEL[4])
    # The antenna, the lens and two buttons.
    for y in range(0, 2):
        c.set(12, y, STEEL[2])
    c.set(12, 0, CYAN_SCREEN[3])
    c.set(13, 1, STEEL[0])
    paint(c, rect(3, 3, 13, 10), [0x10161A, CYAN_SCREEN[0], CYAN_SCREEN[0], CYAN_SCREEN[0]])
    for x in range(4, 13):
        c.set(x, 4, 0x0E2630)
    paint(c, ellipse(6.5, 12.5, 1.9, 1.9), [0x0B2A33, 0x1F7E95, 0x3FB5C6, 0x9EF2F5])
    c.set(6, 12, CYAN_SCREEN[3])
    c.set(10, 12, 0xC2453C)
    c.set(12, 12, 0x3E9E57)
    c.set(10, 13, STEEL[0])
    c.set(12, 13, STEEL[0])
    if direction is None:
        for x in (6, 8, 10):
            c.set(x, 7, 0x1F6878)
    else:
        rows = arrows()[direction]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "X":
                    c.set(6 + x, 5 + y, CYAN_SCREEN[2])
        # The arrowhead's point, brightest.
        tip = {0: (8, 5), 1: (10, 5), 2: (10, 7), 3: (10, 9), 4: (8, 9), 5: (6, 9), 6: (6, 7), 7: (6, 5)}[direction]
        c.set(*tip, CYAN_SCREEN[3])
    return c


def dowsing_rod(pose, fae):
    """pose: idle, ahead (dipping), right or left."""
    c = Canvas()
    wood = GLIMMER if fae else WOOD
    # The two handles, spread at the bottom, meeting at the fork.
    stick(c, line(2, 14, 6, 9), wood)
    stick(c, line(13, 14, 9, 9), wood)
    stick(c, line(6, 9, 7, 8), wood)
    stick(c, line(9, 9, 8, 8), wood)
    tip = {"idle": (7, 1), "ahead": (7, 4), "right": (11, 2), "left": (3, 2)}[pose]
    stick(c, line(7, 7, *tip), wood)
    # The charm bound into the fork: an amethyst shard, or for the fae rod a bead of fae dust.
    gem = FAE if fae else AMETHYST
    paint(c, ellipse(8.0, 8.6, 1.6, 1.6), [gem[0], gem[1], gem[2], gem[3]])
    c.set(7, 8, gem[3])
    if pose == "ahead":
        tx, ty = tip
        c.set(tx, ty - 1, gem[3])
        c.set(tx - 1, ty - 2, gem[2])
        c.set(tx + 1, ty - 2, gem[2])
        c.set(tx, ty - 3, 0xFFFFFF if fae else gem[2])
    if fae:
        for x, y in ((12, 5), (3, 6)):
            c.set(x, y, FAE[3])
        c.set(tip[0], tip[1], FAE[2])
    return c


RADAR_BLIP = (9, 8)


def radar_frame(frame, mode):
    """One frame of the radar's sweep: mode 0 quiet, 1 a contact, 2 an alert."""
    c = Canvas()
    # The copper coil antenna on top.
    c.set(8, 0, STEEL[3])
    paint(c, rect(7, 1, 9, 3), COPPER[:4])
    for y in (1, 3):
        c.set(8, y, COPPER[3])
    c.set(8, 2, COPPER[1])
    c.set(8, 4, STEEL[2])
    paint(c, rounded(1, 5, 14, 15), STEEL[:4])
    c.set(2, 6, STEEL[4])
    scope = ellipse(7.5, 10.0, 4.6, 4.6)
    paint(c, scope, [0x10161A, GREEN_SCREEN[0], GREEN_SCREEN[0], GREEN_SCREEN[0]])
    for x, y in scope:
        if (x, y) in ellipse(7.5, 10.0, 3.6, 3.6) and (x == 7 or y == 10):
            c.set(x, y, 0x123020)
    a = math.radians(frame * 45)
    for t in (1, 2, 3):
        c.set(round(7.0 + math.sin(a) * t), round(9.5 - math.cos(a) * t), GREEN_SCREEN[2] if t < 3 else GREEN_SCREEN[1])
    c.set(7, 10, GREEN_SCREEN[3])
    # The blip lights as the sweep passes (frames 1-2), then fades.
    if mode:
        bright = frame in (1, 2, 3)
        colour = (0xFF4A4A if bright else 0x7A2020) if mode == 2 else (GREEN_SCREEN[3] if bright else 0x2E7A45)
        c.set(*RADAR_BLIP, colour)
    # The alarm lamp, blinking in an alert.
    c.set(13, 7, (0xFF3B30 if frame % 2 == 0 else 0x5A1A16) if mode == 2 else 0x3A2626)
    c.set(13, 8, STEEL[0])
    return c


def radar(mode):
    strip = Canvas(16, 16 * 8)
    for f in range(8):
        frame = radar_frame(f, mode)
        for y in range(16):
            for x in range(16):
                strip.set(x, f * 16 + y, frame.get(x, y))
    return strip


BOOK_NAVY = {"312104": 0x0B1330, "522e10": 0x23336E, "654b17": 0x31468C, "543e13": 0x283B78, "44250a": 0x31468C, "161005": 0x060A1A}
BOOK_GREEN = {"312104": 0x0E2615, "522e10": 0x1F5230, "654b17": 0x2C6E40, "543e13": 0x255F37, "44250a": 0x2C6E40, "161005": 0x07140B}
MOON_LIT, MOON_EDGE, MOON_DARK = 0xEEF2FF, 0xB5C2EE, 0x1A2654
MOON_DISC = [".XXX.", "XXXXX", "XXXXX", "XXXXX", ".XXX."]


def recolour_book(mapping):
    book = vanilla("item/book")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            p = book.get(x, y)
            if p[3]:
                key = "%02x%02x%02x" % p[:3]
                c.set(x, y, mapping.get(key, p))
    return c


def almanac(phase):
    c = recolour_book(BOOK_NAVY)
    # The moon on the cover, lit as tonight's is (phase 0 full, 4 new; waning light on the left).
    phi = phase / 8 * 2 * math.pi
    lit = set()
    for y, row in enumerate(MOON_DISC):
        for x, ch in enumerate(row):
            if ch != "X":
                continue
            u, v = (x - 2) / 2.5, (y - 2) / 2.5
            w = math.sqrt(max(0.0, 1 - v * v))
            if (u <= math.cos(phi) * w + 0.05) if phi <= math.pi else (u >= -math.cos(phi) * w - 0.05):
                lit.add((x, y))
    ox, oy = 6, 3
    for y, row in enumerate(MOON_DISC):
        for x, ch in enumerate(row):
            if ch == "X":
                edge = any((x + dx, y + dy) not in lit and 0 <= x + dx < 5 and 0 <= y + dy < 5 and MOON_DISC[y + dy][x + dx] == "X"
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                c.set(ox + x, oy + y, (MOON_EDGE if edge else MOON_LIT) if (x, y) in lit else MOON_DARK)
    # A silver clasp over the fore edge.
    for x, y, col in ((13, 6, 0xD8E4EC), (14, 6, 0x9AA6B2), (13, 7, 0x9AA6B2)):
        c.set(x, y, col)
    return c


def ledger():
    c = recolour_book(BOOK_GREEN)
    # A gold corner, an emerald set in the cover, and a red ribbon marking the page.
    for x, y, col in ((7, 1, 0xFDF55F), (8, 1, 0xE9B115), (6, 2, 0xE9B115), (9, 2, 0xB26411)):
        c.set(x, y, col)
    for x, y, col in ((7, 5, 0xA8F5C3), (8, 5, 0x17DD62), (7, 6, 0x17DD62), (8, 6, 0x0C7A33)):
        c.set(x, y, col)
    for x, y, col in ((4, 13, 0xC2302A), (4, 14, 0x8A1E1A), (5, 15, 0x8A1E1A)):
        c.set(x, y, col)
    return c


SPRITES = {}
for i, a in enumerate(PENDULUM_ANGLES):
    SPRITES[f"prospectors_pendulum_{i}"] = pendulum(a)
for i in range(4):
    SPRITES[f"metal_detector_{i}"] = metal_detector(i)
SPRITES["meteorite_scanner"] = scanner(None)
for i in range(8):
    SPRITES[f"meteorite_scanner_{i}"] = scanner(i)
for fae in (False, True):
    name = "fae_dowsing_rod" if fae else "dowsing_rod"
    SPRITES[name] = dowsing_rod("idle", fae)
    for pose in ("ahead", "right", "left"):
        SPRITES[f"{name}_{pose}"] = dowsing_rod(pose, fae)
for mode, suffix in ((0, ""), (1, "_contact"), (2, "_alert")):
    SPRITES["ufo_radar" + suffix] = radar(mode)
for i in range(8):
    SPRITES[f"lunar_almanac_{i}"] = almanac(i)
SPRITES["ledger"] = ledger()
ANIMATED = {"ufo_radar", "ufo_radar_contact", "ufo_radar_alert"}

for name, canvas in SPRITES.items():
    canvas.save(os.path.join(TEX, name + ".png"))
    if name in ANIMATED:
        with open(os.path.join(TEX, name + ".png.mcmeta"), "w") as f:
            json.dump({"animation": {"frametime": 3}}, f, indent=2)
            f.write("\n")


# ================================================================================================= models
def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


HANDHELD = {"metal_detector", "dowsing_rod", "fae_dowsing_rod"}


def model(texture, parent="minecraft:item/generated"):
    write_json(os.path.join(ASSETS, "models", "item", texture + ".json"), {"parent": parent, "textures": {"layer0": "tomyen:item/" + texture}})
    return {"type": "minecraft:model", "model": "tomyen:item/" + texture}


def dispatch(prop, entries, fallback, scale=None):
    out = {"type": "minecraft:range_dispatch", "property": prop}
    out.update({k: v for k, v in (scale or {}).items()})
    out["entries"] = [{"threshold": t, "model": m} for t, m in entries]
    out["fallback"] = fallback
    return out


def define(name, item_model):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": item_model})


# The pendulum: seven bob positions over -1..1, the middle one also the resting sprite.
pendulum_models = [model(f"prospectors_pendulum_{i}") for i in range(7)]
define("prospectors_pendulum", dict(dispatch("tomyen:detector", [(-1.0 + 2 * (i + 1) / 7.0, pendulum_models[i + 1]) for i in range(6)],
                                             pendulum_models[0]), reading="pendulum"))
metal = [model(f"metal_detector_{i}", "minecraft:item/handheld") for i in range(4)]
define("metal_detector", dict(dispatch("tomyen:detector", [(0.02, metal[1]), (0.45, metal[2]), (0.75, metal[3])], metal[0]), reading="signal"))
scan_none = model("meteorite_scanner")
define("meteorite_scanner", dict(dispatch("tomyen:detector", [(i / 8.0, model(f"meteorite_scanner_{i}")) for i in range(8)], scan_none),
                                 reading="bearing", sectors=8))
for name in ("dowsing_rod", "fae_dowsing_rod"):
    idle = model(name, "minecraft:item/handheld")
    poses = {p: model(f"{name}_{p}", "minecraft:item/handheld") for p in ("ahead", "right", "left")}
    define(name, dict(dispatch("tomyen:detector", [(0.0, poses["ahead"]), (0.25, poses["right"]), (0.5, idle), (0.75, poses["left"])], idle),
                      reading="bearing", sectors=4))
radar_models = [model(n) for n in ("ufo_radar", "ufo_radar_contact", "ufo_radar_alert")]
define("ufo_radar", dict(dispatch("tomyen:detector", [(0.25, radar_models[1]), (0.75, radar_models[2])], radar_models[0]), reading="signal"))
moon = [model(f"lunar_almanac_{i}") for i in range(8)]
define("lunar_almanac", dict(dispatch("minecraft:time", [(i / 8.0, moon[i]) for i in range(8)], moon[0]), source="moon_phase", wobble=False))
define("ledger", model("ledger"))


# ================================================================================================= recipes
def advancement(name, category, unlock):
    write_json(os.path.join(DATA, "advancement", "recipes", category, name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": unlock}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"}},
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": ["tomyen:" + name]}})


def shaped(name, pattern, key, unlock):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": "equipment", "key": key,
                                                              "pattern": pattern, "result": {"count": 1, "id": "tomyen:" + name}})
    advancement(name, "tools", unlock)


def shapeless(name, ingredients, unlock):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "equipment",
                                                              "ingredients": ingredients, "result": {"count": 1, "id": "tomyen:" + name}})
    advancement(name, "tools", unlock)


shaped("prospectors_pendulum", [" C", " S", " N"], {"C": "minecraft:copper_nugget", "S": "minecraft:string", "N": "minecraft:iron_nugget"},
       "minecraft:iron_nugget")
shaped("dowsing_rod", ["S S", " A ", " S "], {"S": "minecraft:stick", "A": "minecraft:amethyst_shard"}, "minecraft:amethyst_shard")
shapeless("fae_dowsing_rod", ["tomyen:dowsing_rod", "tomyen:fae_dust", "tomyen:fae_dust"], "tomyen:fae_dust")
# The metal detector is assembled on the Scrap Bench (scrapbench/BenchRecipes.java).
shaped("meteorite_scanner", ["SOS", "SXS", "SRS"], {"S": "tomyen:steel_ingot", "O": "tomyen:optic_lens", "X": "tomyen:computer_chip",
                                                    "R": "minecraft:redstone"}, "tomyen:optic_lens")
shaped("ufo_radar", ["SKS", "XTX", "SRS"], {"S": "tomyen:steel_ingot", "K": "tomyen:copper_coil", "X": "tomyen:computer_chip",
                                            "T": "tomyen:cathode_tube", "R": "minecraft:redstone"}, "tomyen:cathode_tube")
shapeless("lunar_almanac", ["minecraft:book", "minecraft:clock", "tomyen:silver_nugget"], "minecraft:clock")
shapeless("ledger", ["minecraft:book", "minecraft:emerald", "minecraft:ink_sac"], "minecraft:emerald")


# ================================================================================================= sounds
def merge_json(path, entries):
    """Adds keys to a shared JSON object file, keeping what's there and its order (other generators share it)."""
    data = json.load(open(path))
    for k, v in entries.items():
        data[k] = v
    write_json(path, data)


def sound(name, *variants, subtitle=None):
    return {"subtitle": "subtitles.tomyen." + (subtitle or name), "sounds": [dict(v) for v in variants]}


def v(name, pitch=1.0, volume=1.0):
    out = {"name": "minecraft:" + name}
    if volume != 1.0:
        out["volume"] = volume
    if pitch != 1.0:
        out["pitch"] = pitch
    return out


merge_json(os.path.join(ASSETS, "sounds.json"), {
    "pendulum_tune": sound("pendulum_tune", v("block/amethyst/resonate1", 1.3), v("block/amethyst/resonate2", 1.3)),
    "pendulum_tick": sound("pendulum_tick", v("block/chain/step1", 1.8, 0.6), v("block/chain/step2", 1.8, 0.6), v("block/chain/step3", 1.8, 0.6)),
    "pendulum_found": sound("pendulum_found", v("note/icechime", 1.5, 0.6)),
    "metal_detector_beep": sound("metal_detector_beep", v("note/bit", 1.0, 0.7)),
    "meteorite_scanner_ping": sound("meteorite_scanner_ping", v("note/pling", 1.2, 0.5), v("block/amethyst/shimmer", 1.6, 0.3)),
    "meteorite_scanner_arrived": sound("meteorite_scanner_arrived", v("block/beacon/activate", 1.6, 0.6)),
    "dowsing_rod_twitch": sound("dowsing_rod_twitch", v("block/bamboo_wood/step1", 1.4, 0.7), v("block/cherry_wood/step1", 1.3, 0.7)),
    "ufo_radar_sweep": sound("ufo_radar_sweep", v("note/hat", 0.6, 0.5)),
    "ufo_radar_contact": sound("ufo_radar_contact", v("note/bell", 1.4, 0.6)),
    "ufo_radar_alarm": sound("ufo_radar_alarm", v("note/bit", 1.6), v("note/bit", 1.2)),
    "lunar_almanac_open": sound("lunar_almanac_open", v("item/book/open_flip1"), v("item/book/open_flip2"), v("item/book/open_flip3")),
    "lunar_almanac_omen": sound("lunar_almanac_omen", v("ambient/cave/cave13", 0.8, 0.5), v("ambient/cave/cave16", 0.8, 0.5)),
    "ledger_open": sound("ledger_open", v("item/book/open_flip1", 1.1), v("block/vault/insert", 1.4, 0.5)),
})


# ================================================================================================= language
LANG = {
    "item.tomyen.prospectors_pendulum": "Prospector's Pendulum",
    "item.tomyen.prospectors_pendulum.hint": "Right-click an ore onto it to tune it; held, it swings toward that ore",
    "item.tomyen.prospectors_pendulum.tooltip_tuned": "Tuned to %s",
    "item.tomyen.prospectors_pendulum.tuned": "The pendulum hums: tuned to %s",
    "item.tomyen.prospectors_pendulum.untuned": "Untuned: right-click an ore onto it, or use it with an ore in your other hand",
    "item.tomyen.metal_detector": "Metal Detector",
    "item.tomyen.metal_detector.hint": "Beeps faster near metal ore, meteorite fragments, buried caches and dropped scrap",
    "item.tomyen.meteorite_scanner": "Meteorite Scanner",
    "item.tomyen.meteorite_scanner.hint": "Points to the nearest meteorite crash site found so far",
    "item.tomyen.dowsing_rod": "Dowsing Rod",
    "item.tomyen.dowsing_rod.hint": "Tugs toward the nearest Fae Ring, if one is close",
    "item.tomyen.fae_dowsing_rod": "Fae Dowsing Rod",
    "item.tomyen.fae_dowsing_rod.hint": "Points to the nearest Fae Ring, and says how far",
    "item.tomyen.ufo_radar": "UFO Radar",
    "item.tomyen.ufo_radar.hint": "Shows saucers nearby; carried anywhere, it warns of new contacts, abductions and landings",
    "item.tomyen.lunar_almanac": "Lunar Almanac",
    "item.tomyen.lunar_almanac.hint": "Tonight's moon, and the nights until the next full moon",
    "item.tomyen.lunar_almanac.reading": "%s: %s",
    "item.tomyen.ledger": "Ledger",
    "item.tomyen.ledger.hint": "Keeps your books: vault, cards, debt and when it's due",
    "item.tomyen.ledger.vault": "Vault: %s emeralds",
    "item.tomyen.ledger.cards": "Credit cards: %s of %s charged",
    "detector.tomyen.pendulum.untuned": "Untuned",
    "detector.tomyen.pendulum.tune_hint": "Right-click an ore onto it",
    "detector.tomyen.pendulum.tuned": "Tuned: %s",
    "detector.tomyen.pendulum.still": "Hangs still: none within %s",
    "detector.tomyen.pendulum.pull_level": "%s %s",
    "detector.tomyen.pendulum.pull_below": "%s %s, below",
    "detector.tomyen.pendulum.pull_above": "%s %s, above",
    "detector.tomyen.pendulum.pull.strong": "Strong pull",
    "detector.tomyen.pendulum.pull.steady": "Steady pull",
    "detector.tomyen.pendulum.pull.faint": "Faint pull",
    "detector.tomyen.metal.none": "No signal",
    "detector.tomyen.metal.signal": "Signal %s%%",
    "detector.tomyen.metal.sweep": "Sweep it low and slow",
    "detector.tomyen.metal.find": "%s, %s",
    "detector.tomyen.metal.below": "%s down",
    "detector.tomyen.metal.above": "%s up",
    "detector.tomyen.metal.level": "level",
    "detector.tomyen.metal.kind.ore": "Ore",
    "detector.tomyen.metal.kind.meteorite": "Meteoric",
    "detector.tomyen.metal.kind.cache": "A cache",
    "detector.tomyen.metal.kind.buried": "Buried",
    "detector.tomyen.metal.kind.scrap": "Scrap",
    "detector.tomyen.scanner.wrong_world": "No crash sites in this world",
    "detector.tomyen.scanner.none": "No crash site logged",
    "detector.tomyen.scanner.none_range": "within %s blocks",
    "detector.tomyen.scanner.site": "%s Crash site, %s m",
    "detector.tomyen.scanner.here": "Crash site right here",
    "detector.tomyen.scanner.dig": "The core lies under the crater",
    "detector.tomyen.scanner.meteor": "Meteor falling! %s m %s",
    "detector.tomyen.rod.feywild": "The rod spins wildly",
    "detector.tomyen.rod.wrong_world": "No fae rings in this world",
    "detector.tomyen.rod.none": "Nothing stirs",
    "detector.tomyen.rod.none_range": "No ring within %s blocks",
    "detector.tomyen.rod.here": "The rod dips: a ring is here",
    "detector.tomyen.rod.ring": "%s Fae ring, %s m",
    "detector.tomyen.rod.tug": "%s %s",
    "detector.tomyen.rod.tug.strong": "Strong tug",
    "detector.tomyen.rod.tug.steady": "Steady tug",
    "detector.tomyen.rod.tug.faint": "Faint tug",
    "detector.tomyen.rod.awake": "Rings are awake tonight",
    "detector.tomyen.rod.asleep": "Rings wake at nightfall",
    "detector.tomyen.radar.clear": "Clear for %s blocks",
    "detector.tomyen.radar.contact": "1 contact",
    "detector.tomyen.radar.contacts": "%s contacts",
    "detector.tomyen.radar.new_contact": "UFO Radar: contact, %s m %s",
    "detector.tomyen.radar.abducting": "Abduction! %s m %s",
    "detector.tomyen.radar.landing": "UFO landing, %s m %s",
    "detector.tomyen.moon.none": "No moon here",
    "detector.tomyen.moon.full_moon": "Full Moon",
    "detector.tomyen.moon.waning_gibbous": "Waning Gibbous",
    "detector.tomyen.moon.third_quarter": "Third Quarter",
    "detector.tomyen.moon.waning_crescent": "Waning Crescent",
    "detector.tomyen.moon.new_moon": "New Moon",
    "detector.tomyen.moon.waxing_crescent": "Waxing Crescent",
    "detector.tomyen.moon.first_quarter": "First Quarter",
    "detector.tomyen.moon.waxing_gibbous": "Waxing Gibbous",
    "detector.tomyen.moon.full_now": "Full moon: they walk tonight",
    "detector.tomyen.moon.full_tonight": "Full moon tonight",
    "detector.tomyen.moon.full_in_one": "Full moon tomorrow night",
    "detector.tomyen.moon.full_in": "Full moon in %s nights",
    "detector.tomyen.almanac.omen": "Something is wrong here...",
    "detector.tomyen.almanac.hunted": "Something is hunting nearby",
    "detector.tomyen.ledger.vault": "Vault: %s emeralds",
    "detector.tomyen.ledger.vault_unknown": "Vault: ?",
    "detector.tomyen.ledger.cards": "Cards: %s / %s charged",
    "detector.tomyen.ledger.debt_free": "Debt free",
    "detector.tomyen.ledger.owed": "Owed: %s emeralds",
    "detector.tomyen.ledger.due": "Collectors in %s",
    "detector.tomyen.ledger.hunting": "Collectors are hunting you!",
    "detector.tomyen.ore.coal": "Coal",
    "detector.tomyen.ore.iron": "Iron",
    "detector.tomyen.ore.copper": "Copper",
    "detector.tomyen.ore.gold": "Gold",
    "detector.tomyen.ore.redstone": "Redstone",
    "detector.tomyen.ore.lapis": "Lapis",
    "detector.tomyen.ore.diamond": "Diamond",
    "detector.tomyen.ore.emerald": "Emerald",
    "detector.tomyen.ore.quartz": "Quartz",
    "detector.tomyen.ore.ancient_debris": "Ancient Debris",
    "detector.tomyen.ore.silver": "Silver",
    "detector.tomyen.ore.fae_silver": "Fae Silver",
    "detector.tomyen.ore.tungsten": "Tungsten",
    "detector.tomyen.ore.uranium": "Uranium",
    "detector.tomyen.ore.meteoric_iron": "Meteoric Iron",
    "detector.tomyen.ore.bauxite": "Bauxite",
    "detector.tomyen.ore.oil_shale": "Oil Shale",
    "detector.tomyen.ore.dragon_scale_fossil": "Dragon Fossil",
    "subtitles.tomyen.pendulum_tune": "Pendulum hums",
    "subtitles.tomyen.pendulum_tick": "Pendulum ticks",
    "subtitles.tomyen.pendulum_found": "Pendulum chimes",
    "subtitles.tomyen.metal_detector_beep": "Metal detector beeps",
    "subtitles.tomyen.meteorite_scanner_ping": "Scanner pings",
    "subtitles.tomyen.meteorite_scanner_arrived": "Scanner locks on",
    "subtitles.tomyen.dowsing_rod_twitch": "Dowsing rod twitches",
    "subtitles.tomyen.ufo_radar_sweep": "Radar blips",
    "subtitles.tomyen.ufo_radar_contact": "Radar contact",
    "subtitles.tomyen.ufo_radar_alarm": "Radar alarm",
    "subtitles.tomyen.lunar_almanac_open": "Almanac pages turn",
    "subtitles.tomyen.lunar_almanac_omen": "Something feels wrong",
    "subtitles.tomyen.ledger_open": "Ledger opens",
}
merge_json(os.path.join(ASSETS, "lang", "en_us.json"), LANG)

if PREVIEW:
    import preview_detectors
    preview_detectors.render(PREVIEW, SPRITES, LANG, vanilla, Canvas)
print(f"wrote {len(SPRITES)} sprites")
