"""Paints power armor and writes its models: the worn suit's texture and glow, the piece, fusion core and plating
kit icons, and the Power Armor Station's textures, block model and item model.

The suit is a code-defined T-51b (PowerArmorModel) on a 256x256 texture laid out as a 4x4 grid of 64x64 tiles:
paint, trim and metal for each mark (tiles 0-8: Mk I battered grey with rust streaks and mismatched patch plates,
Mk II clean factory grey with hazard stripes and stencils, Mk III dark polished gunmetal with reinforcing plate),
rubber (9) and soot (10), then two tiles split into 32x32 small materials: visor glass, port bore, lamp, fusion
core (11), jet flame and emitter lens (12). Every cube unwraps inside its material's tile, so each tile is a
tileable patch. The glow texture holds only the lamp and the jet flame, drawn again full-bright over the rest.

Usage: python3 tools/gen_power_armor.py src/main/resources/assets/tomyen"""
import json, math, os, struct, sys, zlib

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
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


def rgba(hex_colour):
    return ((hex_colour >> 16) & 255, (hex_colour >> 8) & 255, hex_colour & 255, 255)


def noise(x, y, seed):
    """A repeatable -1..1 value per pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 127.5 - 1.0


def blotch(x, y, seed, scale):
    """Smoother noise: the average of a few cells, for rust patches and wear."""
    total = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            total += noise((x // scale) + dx, (y // scale) + dy, seed)
    return total / 4.0


def shade(hex_colour, amount):
    r, g, b, a = rgba(hex_colour)
    clamp = lambda v: max(0, min(255, int(round(v + amount))))
    return (clamp(r), clamp(g), clamp(b), a)


def mix(c0, c1, t):
    a, b = rgba(c0), rgba(c1)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


# --- Mk I: battered grey T-51b plate, rust streaking down from rivets and dents; mismatched patch plates in a
# different, older paint; rough iron. ------------------------------------------------------------------------------

def mk1_paint(x, y):
    if y % 16 == 15 or x % 16 == 15:
        return shade(0x4A4C50, 0)
    if x % 16 in (2, 13) and y % 4 == 1:
        return shade(0xA8ACAE, 0)
    # Rust streaks run down from a few rivets.
    if noise(x, 0, 14) > 0.72 and y % 16 > 2 and noise(x, y // 3, 15) > -0.4:
        return shade(0x7A4A2A, 10 * noise(x, y, 12) - (y % 16) * 1.5)
    if blotch(x, y, 11, 3) > 0.6:
        return shade(0x5E6064, 8 * noise(x, y, 16))
    return shade(0x7C8084, 9 * noise(x, y, 13))


def mk1_trim(x, y):
    # Patch plates: scavenged sheet in faded olive, rivets round the edge, rusting through.
    if x % 32 in (1, 30) or y % 32 in (1, 30):
        return shade(0xA09A7A, 0) if (x + y) % 4 == 0 else shade(0x4A4632, 0)
    if blotch(x, y, 22, 2) > 0.35:
        return shade(0x7A4A2A, 8 * noise(x, y, 23))
    return shade(0x6E6A4A, 8 * noise(x, y, 24))


def mk1_metal(x, y):
    if x % 8 == 3 and y % 8 == 3:
        return shade(0xA0A098, 0)
    if x % 8 == 4 and y % 8 == 4:
        return shade(0x2E2E30, 0)
    if noise(x, y, 31) > 0.8:
        return shade(0x7A4A2A, 0)
    return shade(0x5A5C60, 10 * noise(x, y, 32))


# --- Mk II: clean factory-grey T-51b with panel lines, rivet rows and stencil marks; yellow-and-black hazard
# stripes; brushed steel. ---------------------------------------------------------------------------------------

def mk2_paint(x, y):
    if y % 16 == 15 or x % 20 == 19:
        return shade(0x5C6266, 0)
    if y % 16 == 0:
        return shade(0xC0C6CA, 0)
    if y % 16 == 2 and x % 5 == 2:
        return shade(0x6A7074, 0)
    # A stencilled serial here and there.
    if 6 <= y % 32 <= 8 and 4 <= x % 40 <= 14 and (x % 3 != 0 or y % 32 == 7):
        return shade(0x3A3E42, 0)
    return shade(0x8E9498, 4 * noise(x, y, 42))


def mk2_trim(x, y):
    stripe = ((x + y) // 3) % 2 == 0
    return shade(0xD6B030 if stripe else 0x222222, 5 * noise(x, y, 51))


def mk2_metal(x, y):
    return shade(0x6E7478, 4 * noise(x, y // 6, 61) + 3 * noise(x, y, 62))


# --- Mk III: dark polished gunmetal with bevelled panel edges; heavy reinforcing plate with bright bolts;
# polished dark steel. ------------------------------------------------------------------------------------------

def mk3_paint(x, y):
    if y % 16 == 15 or x % 16 == 15:
        return shade(0x2A2E34, 0)
    if y % 16 == 0 or x % 16 == 0:
        return shade(0x7A828A, 0)
    glint = 22 if (x + y) % 32 in (3, 4) else 0
    return shade(0x4A5058, glint + 3 * noise(x, y, 71))


def mk3_trim(x, y):
    if (x % 12, y % 12) in ((2, 2), (9, 2), (2, 9), (9, 9)):
        return shade(0xB8C0C8, 0)
    if y % 12 == 0:
        return shade(0x646C74, 0)
    if y % 12 == 11:
        return shade(0x1E2226, 0)
    return shade(0x3C4248, 3 * noise(x, y, 81))


def mk3_metal(x, y):
    glint = 40 if (x + y) % 16 in (0, 1) else 0
    return shade(0x33373C, glint + 4 * noise(x, y, 91))


def rubber(x, y):
    return shade(0x3A3A3C if y % 3 == 0 else 0x262628, 4 * noise(x, y, 101))


def soot(x, y):
    return shade(0x2A2622, 10 * noise(x, y, 111) + (12 if blotch(x, y, 112, 3) > 0.5 else 0))


# --- The small materials (32x32). Only the lamp and the jet flame glow. -------------------------------------------

def visor(x, y):
    # Dark tinted glass with a diagonal glint.
    return shade(0x8A9AA2, 0) if (x - y) % 11 == 0 else shade(0x1C262C, 4 * noise(x, y, 121))


def bore(x, y):
    d = math.hypot(x % 8 - 3.5, y % 8 - 3.5)
    return rgba(0x0A0A0C) if d < 2.2 else shade(0x3A3E44, 0) if d < 3.2 else shade(0x55595E, 0)


def lamp(x, y):
    return rgba(0xFFF6D8 if (x + y) % 3 else 0xFFE8A8)


def core(x, y):
    # The fusion core behind its window: yellow with the canister's dark bands.
    return shade(0x3A3A30, 0) if y % 6 == 0 else mix(0xF0D850, 0xB89A20, 0.5 + 0.5 * math.sin(x * 0.7))


def flame(x, y):
    # A jet exhaust down the flame's sides (rows 3 to 10 of its unwrap): a blue-white core at the nozzle through
    # orange to a ragged, smoky tip.
    t = max(0.0, min(1.0, (y - 3) / 7.0))
    if t > 0.65 and noise(x, y, 131) > 0.35:
        return shade(0x5A4A3C, 0)
    core_column = x % 6 in (2, 3)
    if t < 0.25:
        return mix(0xDDEBFF, 0xFFF4D0, t / 0.25) if core_column else mix(0xFFE8A0, 0xFFC050, t / 0.25)
    return mix(0xFFC860 if core_column else 0xFF9A30, 0xC8401A, (t - 0.25) / 0.75)


def emitter(x, y):
    # The unibeam's lens, recessed and dark until it fires.
    d = math.hypot(x % 8 - 3.5, y % 8 - 3.5)
    return shade(0x4A6070, 0) if 2.5 < d < 3.2 else shade(0x1A2430, 6 * noise(x, y, 141))


BIG = [mk1_paint, mk1_trim, mk1_metal, mk2_paint, mk2_trim, mk2_metal, mk3_paint, mk3_trim, mk3_metal, rubber, soot]
# (u, v) of each 32x32 glowing material.
SMALL = {(192, 128): visor, (224, 128): bore, (192, 160): lamp, (224, 160): core, (32, 192): flame, (0, 224): emitter}
GLOWING = {lamp, flame}

suit = [CLEAR] * (256 * 256)
glow = [CLEAR] * (256 * 256)
for index, material in enumerate(BIG):
    u0, v0 = index % 4 * 64, index // 4 * 64
    for y in range(64):
        for x in range(64):
            suit[(v0 + y) * 256 + u0 + x] = material(x, y)
for (u0, v0), material in SMALL.items():
    for y in range(32):
        for x in range(32):
            suit[(v0 + y) * 256 + u0 + x] = material(x, y)
            if material in GLOWING:
                glow[(v0 + y) * 256 + u0 + x] = material(x, y)
png(os.path.join(TEX, "entity/power_armor/power_armor.png"), 256, 256, suit)
png(os.path.join(TEX, "entity/power_armor/power_armor_glow.png"), 256, 256, glow)


# --- Item icons. -------------------------------------------------------------------------------------------------

def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    return [CLEAR if ch == "." else rgba(palette[ch]) for r in rows for ch in r]


def mirrored(rows):
    return [r[::-1] for r in rows]


HELMET = [
    "................",
    "....oooooooo....",
    "...oPPPPPPPPo...",
    "..oPppppppppPo..",
    "..opppttttpppo..",
    "..oppmmmmmmppo..",
    ".oopmvvvvvvmpoo.",
    ".omopmmmmmmpomo.",
    ".omopmddddmpomo.",
    ".oo.pmdmmdmp.oo.",
    "....opmmmmpo....",
    "....oppttppo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]
TORSO = [
    "................",
    ".oooo......oooo.",
    "oPPPPooooooPPPPo",
    "opppPPPPPPPPpppo",
    "opppppmmmmpppppo",
    ".oppppmvvmppppo.",
    ".oppppmvvmppppo.",
    "..oppppmmppppo..",
    "..otttttttttto..",
    "..oddddddddddo..",
    "..odmdmdmdmdmo..",
    "..oddddddddddo..",
    "..ommmmmmmmmmo..",
    "..ommttmmttmmo..",
    "...oooooooooo...",
    "................",
]
ARM = [
    "................",
    ".....ooooo......",
    "....oPPPPPo.....",
    "....opppppo.....",
    "....ottttto.....",
    ".....oddo.......",
    ".....odmo.......",
    ".....ommo.......",
    "....oPPPPo......",
    "....opppPo......",
    "....otttto......",
    "....opppPo......",
    "....ommmmo......",
    "....ommmmo......",
    ".....ovvo.......",
    "......oo........",
]
LEG = [
    "................",
    ".....oooooo.....",
    ".....oPPPPo.....",
    ".....oppppo.....",
    ".....opppPo.....",
    ".....ommmmo.....",
    "....omttttmo....",
    ".....ommmmo.....",
    ".....oppppo.....",
    ".....opppPo.....",
    ".....otttto.....",
    "....oPPPPPPo....",
    "...opppppppo....",
    "...oddddddddo...",
    "...oooooooooo...",
    "................",
]
PIECE_SPRITES = {"helmet": HELMET, "torso": TORSO, "right_arm": ARM, "left_arm": mirrored(ARM),
                 "right_leg": LEG, "left_leg": mirrored(LEG)}
# All grey T-51b: battered with rust and patches, clean with hazard stripes, or dark polished gunmetal.
TIER_PALETTES = {
    "mk1": {"o": 0x1C1D1F, "p": 0x7C8084, "P": 0x9EA2A6, "t": 0x7A4A2A, "m": 0x55575B, "v": 0x2A3238, "d": 0x3E4044},
    "mk2": {"o": 0x1A1C1E, "p": 0x8E9498, "P": 0xB8BEC2, "t": 0xD6B030, "m": 0x6A7074, "v": 0x2A3238, "d": 0x4A5054},
    "mk3": {"o": 0x101214, "p": 0x4A5058, "P": 0x6E767E, "t": 0x2E3338, "m": 0x3A3F45, "v": 0x2A3238, "d": 0x262A2E},
}
names = []
for piece, rows in PIECE_SPRITES.items():
    for tier, palette in TIER_PALETTES.items():
        name = "power_armor_%s_%s" % (piece, tier)
        names.append(name)
        png(os.path.join(TEX, "item/%s.png" % name), 16, 16, sprite(rows, palette))

FUSION_CORE = [
    "................",
    "......oooo......",
    ".....ommmmo.....",
    "....oMmmmmMo....",
    "....oggggggo....",
    "....oGggggGo....",
    "....oggGGggo....",
    "....oGggggGo....",
    "....oggggggo....",
    "....oggGGggo....",
    "....oGggggGo....",
    "....oggggggo....",
    "....oMmmmmMo....",
    ".....ommmmo.....",
    "......oooo......",
    "................",
]
png(os.path.join(TEX, "item/fusion_core.png"), 16, 16,
    sprite(FUSION_CORE, {"o": 0x1E2024, "m": 0x6A7078, "M": 0xA8B0B8, "g": 0x9AE23A, "G": 0xE8FF8A}))
names.append("fusion_core")


def kit_rows():
    middles = ["PPPPPPPPPP", "ppppppppPP", "pmppppppmP", "tttttttttt", "pppccppppP", "ppcCCcpppP", "pppccppppP",
               "tttttttttt", "pmppppppmP", "pppppppppp"]
    rows = ["................", "................", "..oooooooooooo.."]
    rows += ["..o" + m + "o.." for m in middles]
    rows += ["..oooooooooooo..", "................", "................", "................"][:16 - len(rows)]
    return rows


png(os.path.join(TEX, "item/mk2_plating_kit.png"), 16, 16,
    sprite(kit_rows(), {"o": 0x1A1C1E, "p": 0x8C949A, "P": 0xB4BCC2, "m": 0x5C6268, "t": 0xF2C230, "c": 0x2AB8C8, "C": 0xA8F4FF}))
png(os.path.join(TEX, "item/mk3_plating_kit.png"), 16, 16,
    sprite(kit_rows(), {"o": 0x101214, "p": 0x4A5058, "P": 0x6E767E, "m": 0x33373C, "t": 0x2E3338, "c": 0x4A3A40, "C": 0x8A7A80}))
names += ["mk2_plating_kit", "mk3_plating_kit"]

MODELS = os.path.join(ASSETS, "models/item")
ITEMS = os.path.join(ASSETS, "items")
for name in names:
    write_json(os.path.join(MODELS, name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
    write_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


# --- The Power Armor Station. -------------------------------------------------------------------------------------

def frame_texture(x, y):
    # Industrial yellow paint over steel, worn at the edges, with a bolt at each end.
    if x in (0, 15) or y in (0, 15):
        return shade(0x8A6A18, 0)
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        return shade(0x4A4A44, 0)
    if blotch(x, y, 121, 2) > 0.6:
        return shade(0x7A7A74, 8 * noise(x, y, 122))
    return shade(0xD8A520, 8 * noise(x, y, 123))


def base_texture(x, y):
    # Diamond plate: raised studs on a dark steel floor.
    if (x + y) % 4 == 0 and (x - y) % 8 == 0 or (x + y) % 4 == 2 and (x - y) % 8 == 4:
        return shade(0x8A9096, 0)
    return shade(0x4A4E52, 6 * noise(x, y, 131))


def hazard_texture(x, y):
    return shade(0xF2C230 if ((x + y) // 4) % 2 == 0 else 0x1A1A1A, 5 * noise(x, y, 141))


def dark_texture(x, y):
    # Hydraulics: dark steel with a polished ram down the middle.
    if x in (6, 7, 8, 9):
        return shade(0xB8C0C8, 10 if x in (7, 8) else 0)
    return shade(0x2E3236, 6 * noise(x, y, 151) + (10 if y % 4 == 0 else 0))


for name, painter in [("power_armor_station_frame", frame_texture), ("power_armor_station_base", base_texture),
                      ("power_armor_station_hazard", hazard_texture), ("power_armor_station_hydraulic", dark_texture)]:
    png(os.path.join(TEX, "block/%s.png" % name), 16, 16, [painter(x, y) for y in range(16) for x in range(16)])


def element(frm, to, texture, faces="nsewud", side_uv=None):
    """A box with every face given explicitly (the frame is taller than a block, so automatic UVs would run off)."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    uvs = {
        "north": [16 - x1, 16 - min(y1, 16), 16 - x0, 16 - min(y0, 16)] if y1 <= 16 else [x0, 0, x1, 16],
        "south": [x0, 16 - min(y1, 16), x1, 16 - min(y0, 16)] if y1 <= 16 else [x0, 0, x1, 16],
        "east": [16 - z1, 16 - min(y1, 16), 16 - z0, 16 - min(y0, 16)] if y1 <= 16 else [z0, 0, z1, 16],
        "west": [z0, 16 - min(y1, 16), z1, 16 - min(y0, 16)] if y1 <= 16 else [z0, 0, z1, 16],
        "up": [x0, z0, x1, z1],
        "down": [x0, 16 - z1, x1, 16 - z0],
    }
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {}
    for key in faces:
        face = names[key]
        uv = uvs[face]
        out[face] = {"uv": [max(0, min(16, v)) for v in uv], "texture": "#" + (side_uv if side_uv and face not in ("up", "down") else texture)}
    return {"from": list(frm), "to": list(to), "faces": out}


# Built facing north (the front at z = 0): the base plate, two uprights at the back joined by a crossbar two
# blocks up, a back panel with the clamps that hold the suit's torso, and shoulder arms reaching forward.
station = {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": "tomyen:block/power_armor_station_frame",
        "frame": "tomyen:block/power_armor_station_frame",
        "base": "tomyen:block/power_armor_station_base",
        "hazard": "tomyen:block/power_armor_station_hazard",
        "hydraulic": "tomyen:block/power_armor_station_hydraulic",
    },
    "elements": [
        element((0, 0, 0), (16, 3, 16), "base", side_uv="hazard"),
        element((0, 3, 13), (3, 32, 16), "frame"),
        element((13, 3, 13), (16, 32, 16), "frame"),
        element((3, 29, 13), (13, 32, 16), "frame"),
        element((4, 6, 15), (12, 27, 16), "hydraulic"),
        element((3, 3, 14), (13, 5, 16), "hydraulic"),
        element((1, 23, 6), (3, 25, 13), "hydraulic"),
        element((13, 23, 6), (15, 25, 13), "hydraulic"),
        element((1, 12, 9), (3, 14, 13), "hydraulic"),
        element((13, 12, 9), (15, 14, 13), "hydraulic"),
    ],
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.36, 0.36, 0.36]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    },
}
write_json(os.path.join(ASSETS, "models/block/power_armor_station.json"), station)
write_json(os.path.join(ASSETS, "blockstates/power_armor_station.json"), {"variants": {
    "facing=north": {"model": "tomyen:block/power_armor_station"},
    "facing=east": {"model": "tomyen:block/power_armor_station", "y": 90},
    "facing=south": {"model": "tomyen:block/power_armor_station", "y": 180},
    "facing=west": {"model": "tomyen:block/power_armor_station", "y": 270},
}})
write_json(os.path.join(ITEMS, "power_armor_station.json"),
           {"model": {"type": "minecraft:model", "model": "tomyen:block/power_armor_station"}})
print("ok")
