"""Paints the oil industry and the Hogfire gear, and writes everything around them.

Textures: the ores (bauxite, oil shale and its deepslate form), the oil seep, crude oil (animated still and flowing),
steel and aluminium blocks, the Pump Jack's skid, Refinery and Metal Press faces, every industry and gear item, the
moving parts' entity textures (Pump Jack rig, Metal Press ram, Hogfire gear; UVs match PumpJackRenderer,
MetalPressRenderer and HogfireGearLayer) and the Refinery and Pump Jack GUIs (slot positions match their menus).
The ores, oil seep, metal blocks and basic materials (raw bauxite, shale chunk, ingots, plate, sheet, rubber, tar)
are vanilla textures recoloured, read from the Minecraft client jar; the machines and everything else are drawn here.

Also writes their block states, block/item models and item definitions, and on the data side their loot tables,
crafting recipes with recipe-book advancements, and tool tags. With ffmpeg on the path it synthesises the Hogfire's
engine loop (assets/mymod/sounds/hogfire_engine.ogg) too.

Usage: python3 tools/gen_industry.py src/main/resources/assets/mymod [path/to/minecraft-client.jar]"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave, zipfile, zlib

sys.path.insert(0, os.path.dirname(__file__))
from gen_longswords import client_jar, read_png

ASSETS = sys.argv[1]  # .../assets/mymod
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
TEX = os.path.join(ASSETS, "textures")
BLOCK = os.path.join(TEX, "block")
ITEM = os.path.join(TEX, "item")
rnd = random.Random(26)
CLEAR = (0, 0, 0, 0)


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def noisy(self, x0, y0, x1, y1, c, amount=0.12):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, shade(c, 1.0 - amount / 2 + rnd.random() * amount))

    def save(self, path):
        png(path, self.w, self.h, self.px)


def sprite(rows, palette):
    """A 16x16 item sprite from rows of palette letters ('.' is clear)."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# --- Block textures ---------------------------------------------------------------------------------------------
# The ores, the oil seep, the metal blocks and the basic industry items are vanilla textures recoloured, read from
# the Minecraft client jar Loom has downloaded (gen_longswords.client_jar() finds it). A gradient map recolours them:
# each texel's luminance picks a colour off the material's ramp, so vanilla's per-texel shading carries over.
JAR = zipfile.ZipFile(client_jar())


def vanilla(path):
    """The 16x16 pixels of assets/minecraft/textures/<path>.png, a single-colour transparency key applied (some
    vanilla items, iron_ingot among them, are greyscale with one grey keyed out)."""
    data = JAR.read("assets/minecraft/textures/" + path + ".png")
    w, h, px = read_png(data)
    if (w, h) != (16, 16):
        sys.exit(f"vanilla {path}.png is {w}x{h}, not 16x16")
    colour, pos, key = data[25], 8, None
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        if kind == b"tRNS" and colour in (0, 2):
            key = struct.unpack(">%dH" % (n // 2), data[pos + 8:pos + 8 + n])
            key = key * 3 if colour == 0 else key
        pos += 12 + n
    return [p[:3] + (0,) if key and p[:3] == key else p for p in px]


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def gradient(c, stops):
    """Texel c recoloured: stops are (source luminance, colour) pairs, ascending; between two the colour blends."""
    v = lum(c)
    if v <= stops[0][0]:
        return rgba(stops[0][1], c[3])
    for (v0, c0), (v1, c1) in zip(stops, stops[1:]):
        if v <= v1:
            t = (v - v0) / (v1 - v0)
            a, b = rgba(c0), rgba(c1)
            return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (c[3],)
    return rgba(stops[-1][1], c[3])


def recolour(px, stops):
    return [gradient(p, stops) if p[3] else CLEAR for p in px]


def chromatic(c):
    return max(c[:3]) - min(c[:3]) > 20


def canvas(px):
    c = Canvas(16, 16)
    c.px = list(px)
    return c


def ore(base, ore_of, stops):
    """A vanilla ore's specks laid on vanilla stone (or deepslate), the specks' coloured texels recoloured. Where
    the ore texture differs from its rock it is speck, the rock's re-shaded grey round each speck included."""
    rock, specked = vanilla("block/" + base), vanilla("block/" + ore_of)
    out = list(rock)
    for i, (r, o) in enumerate(zip(rock, specked)):
        if o != r:
            out[i] = gradient(o, stops) if chromatic(o) else o
    return canvas(out)


# Iron ore's specks run from luminance 104 to 200, lapis's from 51 to 147.
BAUXITE_SPECKS = ((104, 0x4F1E14), (128, 0x7A3222), (152, 0x9C4630), (180, 0xB8644A), (200, 0xD08E74))
SHALE_SPECKS = ((51, 0x070607), (75, 0x141012), (100, 0x221B1C), (125, 0x342A2E), (147, 0x4E4258))


def oil_stain(c, t):
    """Texel c soaked in crude by t (0 dry, 1 black with oil), its shading kept."""
    wet = gradient(c, ((40, 0x080606), (90, 0x15100E), (130, 0x221A16), (180, 0x3A2E28)))
    return tuple(round(c[i] + (wet[i] - c[i]) * t) for i in range(3)) + (c[3],)


def oil_seep_top():
    """Vanilla coarse dirt, black puddles of crude soaking into it, a glint of light on the larger ones."""
    rr = random.Random(202)
    px = vanilla("block/coarse_dirt")
    out = []
    pools = ((5, 5, 3.0), (11, 10, 2.5), (4, 12, 1.6), (12, 3, 1.3))
    for y in range(16):
        for x in range(16):
            d = min(math.hypot(x - cx, y - cy) - r for cx, cy, r in pools) + rr.random() * 0.8
            out.append(oil_stain(px[y * 16 + x], 1.0 if d < 0 else max(0.0, 0.75 - d * 0.35)))
    c = canvas(out)
    for cx, cy, r in pools[:2]:
        c.set(int(cx - r * 0.4), int(cy - r * 0.4), rgba(0x5C5458))
        c.set(int(cx - r * 0.4) + 1, int(cy - r * 0.4), rgba(0x3B3438))
    return c


def oil_seep_side():
    """Vanilla dirt soaked black at the top, the crude running down it in streaks."""
    rr = random.Random(203)
    px = vanilla("block/dirt")
    runs = {x: 5 + rr.randint(3, 9) for x in (2, 6, 7, 11, 14)}
    out = []
    for y in range(16):
        for x in range(16):
            t = 1.0 if y < 3 else max(0.0, 1.0 - (y - 2) * 0.22 - rr.random() * 0.2)
            if y < runs.get(x, 0):
                t = max(t, 0.9)
            out.append(oil_stain(px[y * 16 + x], t))
    return canvas(out)


# Vanilla iron block runs from luminance 176 to 242, its ingot from 53 to 255.
STEEL_BLOCK = ((176, 0x3E454E), (205, 0x58606A), (230, 0x737C87), (242, 0x8E97A2))
ALUMINIUM_BLOCK = ((176, 0x9EA7B1), (205, 0xBEC6CE), (230, 0xD8DEE4), (242, 0xEEF2F6))
STEEL_INGOT = ((53, 0x1E2228), (94, 0x343A42), (130, 0x515963), (168, 0x6B737E), (216, 0x8E96A0), (255, 0xCDD3DA))
ALUMINIUM_INGOT = ((53, 0x505862), (94, 0x7D8590), (130, 0xA3ABB5), (168, 0xC0C7CF), (216, 0xDDE2E7), (255, 0xFFFFFF))
DURALUMIN_INGOT = ((53, 0x3E362C), (94, 0x6E6252), (130, 0x958774), (168, 0xB4A591), (216, 0xD8CAB5), (255, 0xFFF8EC))


def oil_still(frames=32):
    """Thick black oil with slow iridescent swirls drifting across it."""
    c = Canvas(16, 16 * frames)
    for f in range(frames):
        t = f / frames * math.tau
        for y in range(16):
            for x in range(16):
                v = math.sin(x * 0.7 + t) + math.sin(y * 0.55 - t) + math.sin((x + y) * 0.4 + t * 2)
                base = mix(rgba(0x0B0909), rgba(0x1C1715), (v + 3) / 6)
                if v > 2.2:
                    base = mix(base, rgba(0x3A2F4A), 0.6)
                elif v > 1.9:
                    base = mix(base, rgba(0x2E3A2A), 0.5)
                c.set(x, f * 16 + y, base)
    return c


def oil_flow(frames=32):
    c = Canvas(32, 32 * frames)
    for f in range(frames):
        for y in range(32):
            for x in range(32):
                yy = (y + f) % 32
                v = math.sin(x * 0.45) * 0.6 + math.sin(yy * 0.4 + x * 0.1) + math.sin((yy - x) * 0.25)
                base = mix(rgba(0x0B0909), rgba(0x221B18), (v + 2.6) / 5.2)
                if v > 1.8:
                    base = mix(base, rgba(0x3A2F4A), 0.5)
                c.set(x, f * 32 + y, base)
    return c


STEEL = rgba(0x5E6670)
STEEL_LIGHT = rgba(0x8C949F)
STEEL_DARK = rgba(0x3B4148)
STEEL_DEEP = rgba(0x262A30)
RIVET = rgba(0xB7BEC7)


def plate_block(base, light, dark, seed, seams=True):
    rnd.seed(seed)
    c = Canvas(16, 16)
    c.noisy(0, 0, 16, 16, base, 0.08)
    for i in range(16):
        c.set(i, 0, light)
        c.set(0, i, light)
        c.set(i, 15, dark)
        c.set(15, i, dark)
    if seams:
        for i in range(1, 15):
            c.set(i, 7, dark)
            c.set(i, 8, light)
    for x, y in ((2, 2), (13, 2), (2, 12), (13, 12)):
        c.set(x, y, light)
        c.set(x + 1, y + 1, dark)
    return c


def skid_side():
    """The Pump Jack's skid: a steel I-beam frame, yellow and black hazard stripes along the top."""
    c = Canvas(16, 16)
    c.noisy(0, 0, 16, 16, STEEL_DARK, 0.1)
    c.rect(0, 0, 16, 1, STEEL_LIGHT)
    for x in range(16):
        c.set(x, 1, rgba(0xE8B92E) if (x // 2) % 2 == 0 else rgba(0x1E1E1E))
        c.set(x, 2, rgba(0xE8B92E) if ((x + 1) // 2) % 2 == 0 else rgba(0x1E1E1E))
    c.rect(0, 3, 16, 4, STEEL_DEEP)
    return c


def skid_top():
    c = plate_block(STEEL, STEEL_LIGHT, STEEL_DARK, 303, seams=False)
    # Diamond tread.
    for y in range(2, 14, 3):
        for x in range(2 + (y // 3) % 2 * 2, 14, 4):
            c.set(x, y, STEEL_LIGHT)
            c.set(x + 1, y + 1, STEEL_DARK)
    return c


def refinery_side():
    c = plate_block(rgba(0x6E7A84), rgba(0x9AA6B0), rgba(0x46505A), 401)
    # A pipe running up the side, with a flange.
    for y in range(1, 15):
        c.set(10, y, rgba(0x9A5B2E))
        c.set(11, y, rgba(0xC97A42))
        c.set(12, y, rgba(0x7A4524))
    c.rect(9, 5, 14, 6, rgba(0xB0B8C0))
    c.rect(9, 11, 14, 12, rgba(0xB0B8C0))
    return c


def refinery_front(lit):
    c = refinery_side()
    rnd.seed(402)
    # A sight glass showing the column's level, and a burner door below.
    c.rect(2, 2, 7, 9, STEEL_DEEP)
    for y in range(3, 8):
        for x in range(3, 6):
            c.set(x, y, rgba(0xD9A441) if y > 4 else rgba(0x2A3640))
    c.rect(2, 10, 9, 15, STEEL_DEEP)
    for y in range(11, 14):
        for x in range(3, 8):
            if lit:
                c.set(x, y, rgba(0xFFC233) if rnd.random() < 0.4 else rgba(0xFF7A1F) if rnd.random() < 0.6 else rgba(0xD9480F))
            else:
                c.set(x, y, rgba(0x1A1210))
    # A pressure gauge.
    c.rect(9, 2, 14, 7, rgba(0xE8ECEF))
    c.set(11, 3, rgba(0xD02020) if lit else STEEL_DARK)
    c.set(12, 4, rgba(0xD02020) if lit else STEEL_DARK)
    c.set(11, 4, STEEL_DEEP)
    return c


def refinery_top():
    c = plate_block(rgba(0x6E7A84), rgba(0x9AA6B0), rgba(0x46505A), 403, seams=False)
    # The flare stack's mouth in the back corner.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 11.5, y - 4.5)
            if d < 2.2:
                c.set(x, y, rgba(0x14100E))
            elif d < 3.2:
                c.set(x, y, STEEL_DEEP)
    return c


def press_side():
    """The press frame: pillars at the edges, the anvil bed low, the crosshead up top, open between."""
    c = Canvas(16, 16)
    c.noisy(0, 0, 2, 16, rgba(0x4C5560), 0.1)
    c.noisy(14, 0, 16, 16, rgba(0x4C5560), 0.1)
    c.noisy(0, 13, 16, 16, rgba(0x2E3238), 0.1)
    c.noisy(0, 0, 16, 3, rgba(0x2E3238), 0.1)
    for x in range(16):
        c.set(x, 13, STEEL_LIGHT)
    return c


def press_top():
    c = plate_block(rgba(0x2E3238), rgba(0x4C5560), STEEL_DEEP, 501, seams=False)
    return c


def press_bed():
    c = Canvas(16, 16)
    c.noisy(0, 0, 16, 16, rgba(0x2B2E33), 0.12)
    for i in range(16):
        c.set(i, 0, rgba(0x55595F))
    return c


def press_cylinder():
    c = Canvas(16, 16)
    for x in range(16):
        tone = 0.75 + 0.35 * math.sin(x / 15 * math.pi)
        for y in range(16):
            c.set(x, y, shade(rgba(0xB02A20), tone))
    for x in range(16):
        c.set(x, 0, rgba(0xD9DDE1))
        c.set(x, 15, rgba(0x55595F))
    return c


os.makedirs(BLOCK, exist_ok=True)
ore("stone", "iron_ore", BAUXITE_SPECKS).save(os.path.join(BLOCK, "bauxite_ore.png"))
ore("stone", "lapis_ore", SHALE_SPECKS).save(os.path.join(BLOCK, "oil_shale.png"))
ore("deepslate", "deepslate_lapis_ore", SHALE_SPECKS).save(os.path.join(BLOCK, "deepslate_oil_shale.png"))
oil_seep_top().save(os.path.join(BLOCK, "oil_seep_top.png"))
oil_seep_side().save(os.path.join(BLOCK, "oil_seep_side.png"))
oil_still().save(os.path.join(BLOCK, "crude_oil_still.png"))
oil_flow().save(os.path.join(BLOCK, "crude_oil_flow.png"))
write_json(os.path.join(BLOCK, "crude_oil_still.png.mcmeta"), {"animation": {"frametime": 4}})
write_json(os.path.join(BLOCK, "crude_oil_flow.png.mcmeta"), {"animation": {"frametime": 3}})
canvas(recolour(vanilla("block/iron_block"), STEEL_BLOCK)).save(os.path.join(BLOCK, "steel_block.png"))
canvas(recolour(vanilla("block/iron_block"), ALUMINIUM_BLOCK)).save(os.path.join(BLOCK, "aluminium_block.png"))
# The skid's side was painted from the random stream the hand-drawn aluminium block left (seed 302, one draw a
# texel): replay it so the machines keep their textures.
rnd.seed(302)
for _ in range(256):
    rnd.random()
skid_side().save(os.path.join(BLOCK, "pump_jack_skid_side.png"))
skid_top().save(os.path.join(BLOCK, "pump_jack_skid_top.png"))
refinery_side().save(os.path.join(BLOCK, "refinery_side.png"))
refinery_front(False).save(os.path.join(BLOCK, "refinery_front.png"))
refinery_front(True).save(os.path.join(BLOCK, "refinery_front_on.png"))
refinery_top().save(os.path.join(BLOCK, "refinery_top.png"))
plate_block(STEEL_DARK, STEEL, STEEL_DEEP, 404, seams=False).save(os.path.join(BLOCK, "refinery_bottom.png"))
press_side().save(os.path.join(BLOCK, "metal_press_side.png"))
press_top().save(os.path.join(BLOCK, "metal_press_top.png"))
press_bed().save(os.path.join(BLOCK, "metal_press_bed.png"))
press_cylinder().save(os.path.join(BLOCK, "metal_press_cylinder.png"))

# --- Item textures ----------------------------------------------------------------------------------------------
def flattened(px, thinner):
    """Vanilla's ingot pressed flat: its side faces `thinner` texels shorter, the top face brought down onto them.
    In each column the texels under the top face's white front edge are the side's; the ends, with no edge, lose
    theirs from the middle, keeping their top and bottom texels. The result moves up by half the cut, to sit where the ingot did."""
    out = [CLEAR] * 256
    for x in range(16):
        col = [(y, px[y * 16 + x]) for y in range(16) if px[y * 16 + x][3]]
        if not col:
            continue
        edge = [i for i, (_, p) in enumerate(col) if lum(p) >= 250]
        cut = edge[-1] + 1 if edge else 1
        side = len(col) - cut - 1   # texels between the edge (or the end's top texel) and the bottom outline
        if edge and side < thinner:
            sys.exit(f"vanilla iron_ingot column {x} is too short to flatten: layout changed")
        kept = [p for _, p in col[:cut] + col[cut + min(thinner, side):]]
        bottom = col[-1][0] - thinner // 2
        for i, p in enumerate(kept):
            out[(bottom - len(kept) + 1 + i) * 16 + x] = p
    return out


# A jerrycan: handle on top, cap at the corner, a coloured band and label.
CANISTER = [
    "................",
    "....oooo.oo.....",
    "...o....oCCo....",
    "...oooooooCo....",
    "..ohhhhhhhhoo...",
    "..ohllllllllo...",
    "..ohlxlllxllo...",
    "..obbbbbbbbbo...",
    "..obwwwwwwwbo...",
    "..obwkkkkkwbo...",
    "..obwwwwwwwbo...",
    "..obbbbbbbbbo...",
    "..ohlxlllxllo...",
    "..ommmmmmmmmo...",
    "...ooooooooo....",
    "................",
]


def canister(band, label, mark=None):
    c = sprite(CANISTER, {"o": 0x23272E, "h": 0xA8B0B9, "l": 0x7D8590, "m": 0x555C66, "x": 0x5E6670, "C": 0xC23B2E,
                          "b": band, "w": label, "k": mark if mark is not None else label})
    return c


BUCKET = [
    "................",
    "................",
    "....oooooooo....",
    "...o........o...",
    "..o..........o..",
    "..ohhhhhhhhhhho.",
    "..oKKKKKKKKKKo..",
    "..oKkKKKKKKKKo..",
    "...ollllllllo...",
    "...olllllllmo...",
    "...ollllllmmo...",
    "....olllllmo....",
    "....ollllmmo....",
    ".....oooooo.....",
    "................",
    "................",
]

ITEMS = {}
# Raw iron's texels run from luminance 53 to 246, flint's from 14 to 168, resin's from 66 to 225, slime's 69 to 255.
RAW_BAUXITE = ((53, 0x34140D), (79, 0x562216), (118, 0x7E3424), (150, 0x9C4632), (185, 0xB45E48), (230, 0xCC8670),
               (246, 0xE0AE9A))
SHALE_CHUNK = ((14, 0x0E0B0A), (34, 0x1E1916), (61, 0x342C27), (86, 0x4A4038), (127, 0x6A5E52), (168, 0x8E8274))
TAR = ((66, 0x050404), (92, 0x0F0C0B), (117, 0x1A1513), (147, 0x2A2220), (184, 0x4A3E3A), (225, 0xBFB3AA))
RUBBER = ((69, 0x0E0E10), (100, 0x1C1D20), (120, 0x26282B), (159, 0x34363A), (183, 0x44474C), (233, 0x7A7E86),
          (255, 0xA0A4AB))
INGOT = vanilla("item/iron_ingot")
ITEMS["bauxite"] = canvas(recolour(vanilla("item/raw_iron"), RAW_BAUXITE))
ITEMS["shale_chunk"] = canvas(recolour(vanilla("item/flint"), SHALE_CHUNK))
ITEMS["steel_ingot"] = canvas(recolour(INGOT, STEEL_INGOT))
ITEMS["aluminium_ingot"] = canvas(recolour(INGOT, ALUMINIUM_INGOT))
ITEMS["duralumin_ingot"] = canvas(recolour(INGOT, DURALUMIN_INGOT))
ITEMS["steel_plate"] = canvas(recolour(flattened(INGOT, 2), STEEL_INGOT))
ITEMS["duralumin_sheet"] = canvas(recolour(flattened(INGOT, 3), DURALUMIN_INGOT))
ITEMS["rubber"] = canvas(recolour(vanilla("item/slime_ball"), RUBBER))
ITEMS["tar"] = canvas(recolour(vanilla("item/resin_clump"), TAR))
ITEMS["crude_oil_bucket"] = sprite(BUCKET, {"o": 0x2B2B2B, "h": 0xC6C6C6, "K": 0x110D0C, "k": 0x4A4044, "l": 0x9C9C9C,
                                            "m": 0x6F6F6F})
ITEMS["empty_canister"] = canister(0x5E6670, 0x7D8590)
ITEMS["crude_oil_canister"] = canister(0x1A1513, 0x2E2622, 0x0A0808)
ITEMS["kerosene_canister"] = canister(0xE0A93A, 0xF6E3B0, 0xC07A1A)
ITEMS["rocket_fuel_canister"] = canister(0xD8402E, 0xF2E6DE, 0x1E1E1E)

ITEMS["combustion_chamber"] = sprite([
    "................",
    "......oooo......",
    ".....oCccCo.....",
    "....ohhhhhho....",
    "...ohllllllmo...",
    "..ohlllllllmmo..",
    "..ohllrrrllmmo..",
    "..ohlrRRRrlmmo..",
    "..ohllrrrllmmo..",
    "..ohlllllllmmo..",
    "...ohlllllmmo...",
    "....ohllllmo....",
    ".....ommmmo.....",
    "......oooo......",
    "................",
    "................",
], {"o": 0x22262C, "h": 0xC2C9D1, "l": 0x8E96A0, "m": 0x5A626C, "C": 0xC97A42, "c": 0xE8A066, "r": 0xC0402A, "R": 0xFFB13B})
ITEMS["rocket_nozzle"] = sprite([
    "................",
    "......oooo......",
    "......ohmo......",
    "......ohmo......",
    ".....ohllmo.....",
    ".....ohllmo.....",
    "....ohlllmmo....",
    "....ohllllmo....",
    "...ohlllllmmo...",
    "...ohpllllpmo...",
    "..ohpplllppmmo..",
    "..ohpppppppmmo..",
    ".ohppppppppppmo.",
    ".oooooooooooooo.",
    "................",
    "................",
], {"o": 0x1E2024, "h": 0xB8C0C8, "l": 0x7E868F, "m": 0x4C535C, "p": 0x6E5A8A})
ITEMS["turbopump"] = sprite([
    "................",
    "................",
    "...oooo.........",
    "..ocCCco..oooo..",
    "..oCccCooohhlo..",
    "..oCcxCoolllmo..",
    "..oCccCoollkmo..",
    "..ocCCcooolllmo.",
    "...oooo.ohllllo.",
    "........ohlkllo.",
    ".......ohhllllo.",
    "......oohlllmmo.",
    "......omoooooo..",
    "......omo.......",
    "......ooo.......",
    "................",
], {"o": 0x22262C, "c": 0xC97A42, "C": 0x9A5B2E, "x": 0x3B2A20, "h": 0xC2C9D1, "l": 0x8E96A0, "m": 0x5A626C, "k": 0x2E7D32})
ITEMS["fuel_tank"] = sprite([
    "................",
    "................",
    ".....oooooo.....",
    "....ohhhhhho....",
    "...ohllllllmo...",
    "...ohllllllmo...",
    "...orrrrrrrro...",
    "...okwkwkwkwo...",
    "...orrrrrrrro...",
    "...ohllllllmo...",
    "...ohllllllmo...",
    "...ohllllllmo...",
    "....ommmmmmo....",
    ".....oooooo.....",
    "................",
    "................",
], {"o": 0x3A3328, "h": 0xFFF6E8, "l": 0xD9CBB6, "m": 0xA89A86, "r": 0xC0402A, "k": 0x1E1E1E, "w": 0xF2D23A})
ITEMS["wing_spar"] = sprite([
    "................",
    "..............o.",
    ".............oho",
    "............ohlo",
    "...........ohlo.",
    "..........ohlo..",
    ".........ohlo...",
    "........orrlo...",
    ".......ohlo.....",
    "......ohlo......",
    ".....ohlo.......",
    "....orrlo.......",
    "...ohlo.........",
    "..ohlo..........",
    ".ohlo...........",
    ".oo.............",
], {"o": 0x3E362C, "h": 0xFBEBD8, "l": 0xB9AA96, "r": 0x737B86})
ITEMS["wing_skin"] = sprite([
    "................",
    "................",
    "..oooooooooooo..",
    "..ohhhhhhhhhhmo.",
    "..ohlklllklllmo.",
    "..ohllllllllmo..",
    "..ohlklllklllmo.",
    "..ohllllllllmo..",
    "..ohlklllklllmo.",
    "..ohllllllllmo..",
    "..ohlklllklllmo.",
    "..obbbbbbbbbbo..",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
], {"o": 0x3E362C, "h": 0xFFF6E8, "l": 0xE3D6C4, "m": 0xB9AA96, "k": 0xA08F78, "b": 0x26282B})
ITEMS["rocket_engine"] = sprite([
    "................",
    "...oooooooo.....",
    "..ohhhhhhhho....",
    ".ohlllrrlllmo...",
    ".ohlllrrlllmooo.",
    ".ohllllllllmopp.",
    ".ohllllllllmoppo",
    ".ohlcclllllmoppo",
    ".ohlcclllllmopp.",
    ".ohllllllllmooo.",
    ".ommmmmmmmmmo...",
    "..oooooooooo....",
    "...oyo..oyo.....",
    "...oYo..oYo.....",
    "....o....o......",
    "................",
], {"o": 0x22262C, "h": 0xC2C9D1, "l": 0x8E96A0, "m": 0x5A626C, "r": 0xC0402A, "c": 0xC97A42, "p": 0x6E5A8A,
    "y": 0xFFB13B, "Y": 0xFF6A1F})
ITEMS["pig_wings"] = sprite([
    "................",
    "................",
    "................",
    "o.............o.",
    "oo...........oo.",
    "ohoo.......oohho",
    "ohllo.....ollhho",
    "ohlllo...olllho.",
    ".ohlllo.olllho..",
    ".ohllllollllho..",
    "..ohlllklllho...",
    "..ohhlkkklhho...",
    "...oooooooooo...",
    "................",
    "................",
    "................",
], {"o": 0x3E362C, "h": 0xFFF6E8, "l": 0xD9CBB6, "k": 0x737B86})
ITEMS["pig_flight_harness"] = sprite([
    "................",
    "................",
    "....oooooooo....",
    "...ossssssssoo..",
    "..osSSSSSSSSso..",
    "..obbbbbbbbbbo..",
    "..obpbbbbbbpbo..",
    "..obbbbbbbbbbo..",
    "..oo.oo..oo.oo..",
    "..ob.ob..ob.bo..",
    "..ob.ob..ob.bo..",
    "..ob.ob..ob.bo..",
    "..okkok..okkko..",
    "..oo.oo..oo.oo..",
    "................",
    "................",
], {"o": 0x2A1C12, "s": 0x6B3F23, "S": 0x8A5530, "b": 0x7A4A2A, "p": 0xC2C9D1, "k": 0xA8B0B9})
ITEMS["pump_jack"] = sprite([
    "................",
    "..ooooooooooooo.",
    ".oYyyyyyyyyyyyo.",
    ".oYooooo.oooooo.",
    ".oYo...ooo......",
    ".oyo...oso..oo..",
    "..r...o.s.o.oko.",
    "..r..o..s..ooko.",
    "..r.o...s....oo.",
    "..ro....s...ooo.",
    ".ooo....s..oggo.",
    ".oho...ososoggo.",
    "oohhooooooooooo.",
    "ohhhhhhhhhhhhhho",
    "oddddddddddddddo",
    "oooooooooooooooo",
], {"o": 0x1E2024, "Y": 0xF2C94C, "y": 0xC99A1E, "r": 0xD9DEE4, "s": 0x7C838E, "k": 0x2A2A2A, "g": 0x3E5A7A,
    "h": 0x8C949F, "d": 0x4C535C})

for name, canvas in ITEMS.items():
    canvas.save(os.path.join(ITEM, name + ".png"))

# --- Entity textures --------------------------------------------------------------------------------------------


def paint_box(c, u, v, w, h, d, colour, amount=0.1, edge=True):
    """Fills a box's UV net (as Minecraft unwraps it from texOffs u, v) with a noisy colour, the face edges darker."""
    faces = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h),
             (u + d + w + d, v + d, w, h)]
    for x0, y0, fw, fh in faces:
        c.noisy(x0, y0, x0 + fw, y0 + fh, colour, amount)
        if edge and fw > 1 and fh > 1:
            for x in range(x0, x0 + fw):
                c.set(x, y0 + fh - 1, shade(c.get(x, y0 + fh - 1), 0.75))
            for y in range(y0, y0 + fh):
                c.set(x0, y, shade(c.get(x0, y), 1.15))


def pump_jack_rig():
    rnd.seed(601)
    c = Canvas(128, 64)
    blue, yellow, steel, dark, black = rgba(0x3E5A7A), rgba(0xF2C94C), rgba(0x7C838E), rgba(0x454B53), rgba(0x26282B)
    paint_box(c, 0, 0, 2, 26, 2, steel)  # legs
    paint_box(c, 8, 0, 2, 3, 24, blue)  # walking beam
    paint_box(c, 60, 0, 4, 11, 3, yellow)  # horse head
    paint_box(c, 74, 0, 4, 1, 2, yellow)
    paint_box(c, 86, 0, 7, 1, 2, dark)  # equaliser
    paint_box(c, 104, 0, 1, 16, 1, steel)  # pitmans
    paint_box(c, 108, 0, 1, 12, 2, dark)  # crank arms
    paint_box(c, 114, 0, 1, 16, 1, rgba(0xD9DEE4), 0.04)  # polished rod
    paint_box(c, 60, 14, 3, 5, 6, black)  # counterweights
    paint_box(c, 78, 14, 6, 6, 6, blue)  # gearbox
    paint_box(c, 102, 17, 4, 3, 2, rgba(0x4F6B4A))  # motor
    paint_box(c, 0, 30, 4, 4, 4, rgba(0x8E2A22))  # wellhead
    paint_box(c, 16, 30, 8, 4, 14, dark)  # skid tail
    paint_box(c, 60, 30, 8, 1, 1, steel)  # crank axle
    # Hazard stripes down the counterweights' faces.
    for y in range(20, 25):
        for x in range(60, 78):
            if (x + y) % 4 < 2:
                c.set(x, y, yellow)
    return c


def press_ram():
    rnd.seed(602)
    c = Canvas(64, 32)
    paint_box(c, 0, 0, 10, 3, 10, rgba(0x3B4148))
    paint_box(c, 40, 0, 2, 6, 2, rgba(0xD9DEE4), 0.04)
    # The die's face, hatched.
    for y in range(10):
        for x in range(20, 30):
            if (x + y) % 3 == 0:
                c.set(x, y, rgba(0x55595F))
    return c


def hogfire_gear():
    rnd.seed(603)
    c = Canvas(64, 64)
    leather, dark_leather, steel, skin, spar = rgba(0x7A4A2A), rgba(0x4E2E18), rgba(0x9AA2AC), rgba(0xE3D6C4), rgba(0x737B86)
    paint_box(c, 0, 0, 10, 8, 1, leather)  # belly bands
    for y in range(1, 9):  # a buckle
        c.set(5, y, steel if y in (4, 5) else c.get(5, y))
    paint_box(c, 22, 0, 3, 1, 9, leather)  # spine strap
    paint_box(c, 0, 10, 8, 1, 6, dark_leather)  # seat pad
    paint_box(c, 46, 0, 1, 4, 4, steel)  # flank plates
    paint_box(c, 0, 17, 16, 1, 1, spar)  # wing spars
    paint_box(c, 0, 19, 15, 1, 8, skin, 0.05)  # wing skins
    for x in range(0, 46, 4):  # rib rivets
        for y in range(19, 28):
            if y % 3 == 0:
                c.set(x, y, rgba(0xA08F78))
    paint_box(c, 0, 28, 5, 5, 9, rgba(0x5E6670))  # engine body
    for x in range(0, 28):  # a red stripe round it
        c.set(x, 36, rgba(0xC0402A))
        c.set(x, 37, rgba(0xC0402A))
    paint_box(c, 28, 28, 3, 1, 3, rgba(0xC97A42))  # turbopump
    paint_box(c, 40, 28, 4, 4, 2, rgba(0x4C535C))  # nozzle throat
    paint_box(c, 28, 34, 6, 6, 2, rgba(0x5E4E78))  # heat-blued bell
    paint_box(c, 44, 34, 2, 2, 6, rgba(0xF2F2F2), 0.04)  # fuel pods
    for x in range(44, 60):
        if x % 3 == 0:
            c.set(x, 39, rgba(0xC0402A))
    # The flame: white-hot at the throat, orange to red outward.
    for u, v, w, h, d in ((0, 42, 3, 3, 5), (16, 42, 2, 2, 4)):
        for y in range(v, v + d + h):
            for x in range(u, u + 2 * d + 2 * w):
                t = rnd.random()
                c.set(x, y, rgba(0xFFF4C2) if t < 0.3 else rgba(0xFFB13B) if t < 0.7 else rgba(0xFF6A1F))
    return c


pump_jack_rig().save(os.path.join(TEX, "entity/industry/pump_jack.png"))
press_ram().save(os.path.join(TEX, "entity/industry/metal_press_ram.png"))
hogfire_gear().save(os.path.join(TEX, "entity/hogfire/gear.png"))

# --- GUIs -------------------------------------------------------------------------------------------------------
K, W, BG, SHADOW, SLOT, SLOT_DARK = rgba(0), rgba(0xFFFFFF), rgba(0xC6C6C6), rgba(0x555555), rgba(0x8B8B8B), rgba(0x373737)
GUI_W, GUI_H = 176, 166


def panel():
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
    g.set(2, 2, W)
    g.set(GUI_W - 3, GUI_H - 3, SHADOW)
    for row in range(3):
        for col in range(9):
            slot(g, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(g, 8 + col * 18, 142)
    return g


def slot(g, x, y, big=False):
    """A slot whose item sits at (x, y): an 18x18 inset (26x26 for an output)."""
    pad = 5 if big else 1
    x0, y0, x1, y1 = x - pad, y - pad, x + 16 + pad, y + 16 + pad
    g.rect(x0, y0, x1, y1, SLOT)
    for i in range(x0, x1):
        g.set(i, y0, SLOT_DARK)
        g.set(i, y1 - 1, W)
    for i in range(y0, y1):
        g.set(x0, i, SLOT_DARK)
        g.set(x1 - 1, i, W)


def refinery_gui():
    g = panel()
    for i in range(3):
        slot(g, 26 + i * 18, 17)
    slot(g, 44, 53)
    slot(g, 116, 22)
    slot(g, 116, 48)
    slot(g, 134, 48)
    # The empty arrow and flame outlines, where RefineryScreen draws the progress.
    for y in range(35, 51):
        for x in range(86, 110):
            if 39 <= y <= 46 and x < 102 or 102 <= x < 102 + (8 - abs(y - 42)):
                g.set(x, y, SLOT)
    for y in range(37, 51):
        half = (y - 37) // 2
        for x in range(52 - half // 2, 53 + half // 2):
            g.set(x, y, SLOT)
    return g


def pump_jack_gui():
    g = panel()
    slot(g, 44, 35)
    slot(g, 116, 35)
    # The oil gauge between them, recessed.
    g.rect(67, 36, 109, 50, SLOT_DARK)
    g.rect(68, 37, 108, 49, rgba(0x2A2420))
    for x in range(67, 109):
        g.set(x, 49, W)
    return g


refinery_gui().save(os.path.join(TEX, "gui/container/refinery.png"))
pump_jack_gui().save(os.path.join(TEX, "gui/container/pump_jack.png"))

# --- Models, block states and item definitions ------------------------------------------------------------------
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
MODELS_ITEM = os.path.join(ASSETS, "models", "item")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
ITEM_DEFS = os.path.join(ASSETS, "items")


def item_def(name, model):
    write_json(os.path.join(ITEM_DEFS, name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


def cube_all_block(name, texture=None):
    write_json(os.path.join(MODELS_BLOCK, name + ".json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": "mymod:block/" + (texture or name)}})
    write_json(os.path.join(BLOCKSTATES, name + ".json"), {"variants": {"": {"model": "mymod:block/" + name}}})
    item_def(name, "mymod:block/" + name)


for name in ("bauxite_ore", "oil_shale", "deepslate_oil_shale", "steel_block", "aluminium_block"):
    cube_all_block(name)

write_json(os.path.join(MODELS_BLOCK, "oil_seep.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": "mymod:block/oil_seep_top", "side": "mymod:block/oil_seep_side", "bottom": "minecraft:block/dirt"}})
write_json(os.path.join(BLOCKSTATES, "oil_seep.json"), {"variants": {"": {"model": "mymod:block/oil_seep"}}})
item_def("oil_seep", "mymod:block/oil_seep")

# Crude oil: like water, the block model only gives the particles; the fluid renderer draws the rest.
write_json(os.path.join(MODELS_BLOCK, "crude_oil.json"), {"textures": {"particle": "mymod:block/crude_oil_still"}})
write_json(os.path.join(BLOCKSTATES, "crude_oil.json"), {"variants": {"": {"model": "mymod:block/crude_oil"}}})

FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
for model, textures in (("refinery", "refinery_front"), ("refinery_on", "refinery_front_on")):
    write_json(os.path.join(MODELS_BLOCK, model + ".json"), {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "top": "mymod:block/refinery_top", "bottom": "mymod:block/refinery_bottom", "side": "mymod:block/refinery_side",
        "front": "mymod:block/" + textures}})
write_json(os.path.join(BLOCKSTATES, "refinery.json"), {"variants": {
    "facing=%s,lit=%s" % (facing, lit): dict({"model": "mymod:block/" + ("refinery_on" if lit == "true" else "refinery")},
                                              **({"y": rot} if rot else {}))
    for facing, rot in FACINGS for lit in ("false", "true")}})
item_def("refinery", "mymod:block/refinery")


def face(texture, uv=None, cull=None):
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, texture, uv_side=None, top="#top", cull=None):
    faces = {}
    for d in ("north", "south", "east", "west"):
        faces[d] = face(texture, uv_side)
    faces["up"] = face(top)
    faces["down"] = face("#bottom")
    return {"from": frm, "to": to, "faces": faces}


# The Pump Jack's block is just its skid; the rig on top is drawn by PumpJackRenderer.
write_json(os.path.join(MODELS_BLOCK, "pump_jack.json"), {
    "parent": "minecraft:block/block",
    "textures": {"particle": "mymod:block/pump_jack_skid_top", "top": "mymod:block/pump_jack_skid_top",
                 "side": "mymod:block/pump_jack_skid_side", "bottom": "mymod:block/refinery_bottom"},
    "elements": [box([0, 0, 0], [16, 4, 16], "#side", [0, 0, 16, 4])]})
write_json(os.path.join(BLOCKSTATES, "pump_jack.json"), {"variants": {
    "facing=%s,active=%s" % (facing, active): dict({"model": "mymod:block/pump_jack"}, **({"y": rot} if rot else {}))
    for facing, rot in FACINGS for active in ("false", "true")}})
write_json(os.path.join(MODELS_ITEM, "pump_jack.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/pump_jack"}})
item_def("pump_jack", "mymod:item/pump_jack")

# The Metal Press: anvil bed, four pillars, crosshead and the cylinder on top; the ram is MetalPressRenderer's.
press_elements = [
    {"from": [1, 0, 1], "to": [15, 6, 15], "faces": {
        "north": face("#bed", [1, 10, 15, 16]), "south": face("#bed", [1, 10, 15, 16]), "east": face("#bed", [1, 10, 15, 16]),
        "west": face("#bed", [1, 10, 15, 16]), "up": face("#bed", [1, 1, 15, 15]), "down": face("#top", cull="down")}},
]
for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
    press_elements.append({"from": [x, 0, z], "to": [x + 2, 13, z + 2], "faces": {
        d: face("#side", [0, 3, 2, 16]) for d in ("north", "south", "east", "west")} | {"down": face("#top")}})
press_elements.append({"from": [0, 13, 0], "to": [16, 16, 16], "faces": {
    "north": face("#side", [0, 0, 16, 3]), "south": face("#side", [0, 0, 16, 3]), "east": face("#side", [0, 0, 16, 3]),
    "west": face("#side", [0, 0, 16, 3]), "up": face("#top", cull="up"), "down": face("#top")}})
press_elements.append({"from": [5, 16, 5], "to": [11, 20, 11], "faces": {
    d: face("#cylinder", [5, 0, 11, 4]) for d in ("north", "south", "east", "west")} | {"up": face("#cylinder", [5, 5, 11, 11])}})
write_json(os.path.join(MODELS_BLOCK, "metal_press.json"), {
    "parent": "minecraft:block/block",
    "textures": {"particle": "mymod:block/metal_press_top", "top": "mymod:block/metal_press_top",
                 "side": "mymod:block/metal_press_side", "bed": "mymod:block/metal_press_bed",
                 "cylinder": "mymod:block/metal_press_cylinder"},
    "elements": press_elements})
write_json(os.path.join(BLOCKSTATES, "metal_press.json"), {"variants": {
    "powered=false": {"model": "mymod:block/metal_press"}, "powered=true": {"model": "mymod:block/metal_press"}}})
item_def("metal_press", "mymod:block/metal_press")

for name in ITEMS:
    if name == "pump_jack":
        continue
    write_json(os.path.join(MODELS_ITEM, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
    item_def(name, "mymod:item/" + name)

# --- Loot tables ------------------------------------------------------------------------------------------------
LOOT = os.path.join(DATA, "loot_table", "blocks")


def self_drop(name):
    write_json(os.path.join(LOOT, name + ".json"), {"type": "minecraft:block", "pools": [{
        "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{"type": "minecraft:item", "name": "mymod:" + name}], "rolls": 1}],
        "random_sequence": "mymod:blocks/" + name})


def ore_drop(name, item, count=None):
    modifiers = []
    if count:
        modifiers.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}})
    modifiers += [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                  {"type": "minecraft:explosion_decay"}]
    write_json(os.path.join(LOOT, name + ".json"), {"type": "minecraft:block", "pools": [{
        "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": "mymod:" + name},
            {"type": "minecraft:item", "modifier": modifiers, "name": item}]}],
        "rolls": 1}], "random_sequence": "mymod:blocks/" + name})


for name in ("steel_block", "aluminium_block", "pump_jack", "refinery", "metal_press"):
    self_drop(name)
ore_drop("bauxite_ore", "mymod:bauxite", (1, 2))
ore_drop("oil_shale", "mymod:shale_chunk", (1, 3))
ore_drop("deepslate_oil_shale", "mymod:shale_chunk", (1, 3))
# Dug up without silk touch, a seep is only so much oily dirt: a lump of tar.
write_json(os.path.join(LOOT, "oil_seep.json"), {"type": "minecraft:block", "pools": [{
    "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": "mymod:oil_seep"},
        {"type": "minecraft:item", "modifier": [{"type": "minecraft:explosion_decay"}], "name": "mymod:tar"}]}],
    "rolls": 1}], "random_sequence": "mymod:blocks/oil_seep"})
# A Hogfire drops a pig's pork (its gear drops from code).
write_json(os.path.join(DATA, "loot_table", "entities", "hogfire.json"), {"type": "minecraft:entity", "pools": [{
    "entries": [{"type": "minecraft:item", "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}},
        {"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties", "entity": "this",
                                                                "predicate": {"flags": {"is_on_fire": True}}}]},
        {"function": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
         "enchantment": "minecraft:looting"}], "name": "minecraft:porkchop"}], "rolls": 1}],
    "random_sequence": "mymod:entities/hogfire"})

# --- Crafting recipes and their recipe-book advancements -------------------------------------------------------
RECIPES = os.path.join(DATA, "recipe")
ADVANCEMENTS = os.path.join(DATA, "advancement", "recipes")


def advancement(name, category, unlock_item):
    write_json(os.path.join(ADVANCEMENTS, category, name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": unlock_item}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"}},
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": ["mymod:" + name]}})


def shaped(name, result, pattern, key, unlock, count=1, category="misc"):
    write_json(os.path.join(RECIPES, name + ".json"), {"type": "minecraft:crafting_shaped", "category": category,
                                                        "key": key, "pattern": pattern,
                                                        "result": {"count": count, "id": result}})
    advancement(name, category, unlock)


def shapeless(name, result, ingredients, unlock, count=1, category="misc"):
    write_json(os.path.join(RECIPES, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": category,
                                                        "ingredients": ingredients, "result": {"count": count, "id": result}})
    advancement(name, category, unlock)


STEEL_I, ALU_I, DURA_I = "mymod:steel_ingot", "mymod:aluminium_ingot", "mymod:duralumin_ingot"
PLATE, SHEET = "mymod:steel_plate", "mymod:duralumin_sheet"
shaped("steel_block", "mymod:steel_block", ["###", "###", "###"], {"#": STEEL_I}, STEEL_I, category="building")
shapeless("steel_ingot_from_steel_block", STEEL_I, ["mymod:steel_block"], "mymod:steel_block", 9)
shaped("aluminium_block", "mymod:aluminium_block", ["###", "###", "###"], {"#": ALU_I}, ALU_I, category="building")
shapeless("aluminium_ingot_from_aluminium_block", ALU_I, ["mymod:aluminium_block"], "mymod:aluminium_block", 9)
shaped("empty_canister", "mymod:empty_canister", [" N ", "S S", "SSS"], {"N": "minecraft:iron_nugget", "S": STEEL_I}, STEEL_I, 2)
shapeless("crude_oil_canister_from_bucket", "mymod:crude_oil_canister", ["mymod:empty_canister", "mymod:crude_oil_bucket"],
          "mymod:crude_oil_bucket")
shaped("pump_jack", "mymod:pump_jack", ["SSS", "RPR", "SCS"],
       {"S": STEEL_I, "R": "minecraft:redstone", "P": "minecraft:piston", "C": "mymod:empty_canister"}, STEEL_I)
shaped("refinery", "mymod:refinery", ["SGS", "CBC", "SKS"],
       {"S": STEEL_I, "G": "minecraft:glass", "C": "minecraft:copper_ingot", "B": "minecraft:blast_furnace", "K": "minecraft:bricks"},
       STEEL_I)
shaped("metal_press", "mymod:metal_press", ["SPS", "S S", "OOO"],
       {"S": STEEL_I, "P": "minecraft:piston", "O": "minecraft:obsidian"}, STEEL_I, category="redstone")
shaped("combustion_chamber", "mymod:combustion_chamber", ["PCP", "PBP", "PPP"],
       {"P": PLATE, "C": "minecraft:copper_ingot", "B": "minecraft:blaze_rod"}, PLATE)
shaped("rocket_nozzle", "mymod:rocket_nozzle", [" P ", "POP", "P P"], {"P": PLATE, "O": "minecraft:obsidian"}, PLATE)
shaped("turbopump", "mymod:turbopump", ["PXP", "CRC", "PCP"],
       {"P": PLATE, "X": "mymod:computer_chip", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, "mymod:computer_chip")
shaped("fuel_tank", "mymod:fuel_tank", ["DTD", "DCD", "DDD"], {"D": SHEET, "T": "mymod:tar", "C": "mymod:empty_canister"}, SHEET)
shaped("wing_spar", "mymod:wing_spar", ["IPI"], {"I": DURA_I, "P": PLATE}, DURA_I)
shaped("wing_skin", "mymod:wing_skin", ["DDD", "RRR"], {"D": SHEET, "R": "mymod:rubber"}, SHEET, 2)
shaped("rocket_engine", "mymod:rocket_engine", ["PFP", "UCP", "PNP"],
       {"P": PLATE, "F": "mymod:fuel_tank", "U": "mymod:turbopump", "C": "mymod:combustion_chamber", "N": "mymod:rocket_nozzle"},
       "mymod:combustion_chamber", category="equipment")
shaped("pig_wings", "mymod:pig_wings", ["S S", "KSK", "K K"], {"S": "mymod:wing_spar", "K": "mymod:wing_skin"}, "mymod:wing_spar",
       category="equipment")
shaped("pig_flight_harness", "mymod:pig_flight_harness", ["L L", "PSP", "L L"],
       {"L": "minecraft:leather", "P": PLATE, "S": "minecraft:saddle"}, PLATE, category="equipment")

# --- Tags (shared with gen_silver.py and hand edits, so entries are added to what's there; the pickaxe tag is by hand) --
TAGS = os.path.normpath(os.path.join(DATA, "..", "minecraft", "tags"))


def add_to_tag(path, values):
    existing = []
    if os.path.exists(path):
        with open(path) as f:
            existing = json.load(f)["values"]
    write_json(path, {"values": existing + [v for v in values if v not in existing]})


add_to_tag(os.path.join(TAGS, "block", "mineable", "shovel.json"), ["mymod:oil_seep"])
add_to_tag(os.path.join(TAGS, "block", "needs_stone_tool.json"), ["mymod:bauxite_ore", "mymod:aluminium_block"])
add_to_tag(os.path.join(TAGS, "block", "needs_iron_tool.json"), ["mymod:steel_block"])
add_to_tag(os.path.join(TAGS, "block", "ores.json"), ["mymod:bauxite_ore", "mymod:oil_shale", "mymod:deepslate_oil_shale"])
add_to_tag(os.path.join(TAGS, "item", "ores.json"), ["mymod:bauxite_ore", "mymod:oil_shale", "mymod:deepslate_oil_shale"])

# --- The Hogfire's engine loop ------------------------------------------------------------------------------------


def engine_sound(path):
    """Two seconds of rocket: brown noise for the roar over a low pulsing rumble, seamless when looped."""
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found: skipping " + path)
        return
    rate, seconds = 22050, 2.0
    n = int(rate * seconds)
    rr = random.Random(9)
    brown, samples = 0.0, []
    for i in range(n):
        brown = brown * 0.985 + rr.uniform(-1, 1) * 0.15
        t = i / rate
        # Whole cycles of each tone in two seconds, so the loop joins up.
        rumble = 0.35 * math.sin(math.tau * 46 * t) * (0.7 + 0.3 * math.sin(math.tau * 6 * t))
        crackle = rr.uniform(-1, 1) * 0.08 if rr.random() < 0.02 else 0.0
        samples.append(brown * 2.2 + rumble + crackle)
    # Crossfade the tail into the head so the noise doesn't click at the loop point.
    fade = int(rate * 0.1)
    for i in range(fade):
        w = i / fade
        samples[i] = samples[i] * w + samples[n - fade + i] * (1 - w)
    samples = samples[:n - fade]
    peak = max(abs(s) for s in samples)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "engine.wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(rate)
            out.writeframes(b"".join(struct.pack("<h", int(s / peak * 26000)) for s in samples))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "4", path], check=True)


engine_sound(os.path.join(ASSETS, "sounds", "hogfire_engine.ogg"))
print("industry assets written")
