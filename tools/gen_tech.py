"""Paints tech scrap, silicon, computer chips, the Neuralink and its modules, and writes their item models. The Neuralink has a flat icon for
inventories and hands, and a small 3D implant for when it's worn: an item on the head slot is drawn by
vanilla's custom-head layer with the "head" display transform, scaled here so 1 model unit = 1 head pixel.
In that space the head fills 4..12 on every axis; +x is the wearer's right, +y up and +z the back."""
import json, os, struct, sys, zlib

sys.path.insert(0, os.path.dirname(__file__))
from zfight import check

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")


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


def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    return [(0, 0, 0, 0) if ch == "." else rgba(palette[ch]) for r in rows for ch in r]


# A broken-off corner of a circuit board: copper traces, a chip, redstone solder and a loose wire.
TECH_SCRAP = [
    "................",
    ".oooooooo.......",
    ".ohhhhhhgo......",
    ".ohcccgcggoo....",
    ".ohgggcgcgggo...",
    ".oggsksksgcggo..",
    ".ogckkkkkscggo..",
    ".ogcskkkkkgcgo..",
    ".ogckkkkkscggo..",
    ".ogcsksksgccgo..",
    ".ogcgggggggcgo..",
    ".ogrrgcccggo.y..",
    ".ogdrgcgggoyy...",
    ".oggggcggo..y...",
    "..oooooooo..y...",
    "................",
]
TECH_PALETTE = {"o": 0x1B3D1E, "g": 0x2E7D32, "h": 0x4CAF50, "c": 0xD08A3A, "k": 0x1E1E22,
                "s": 0xC0C6CE, "r": 0xE3261C, "d": 0x7E0F0A, "y": 0xE8C547}

# The band seen from above: open at the front, glowing pods at the ears and a module at the back.
NEURALINK = [
    "................",
    "................",
    "...cc......cc...",
    "..ocbo....obco..",
    "..owwo....owwo..",
    ".owwo......owwo.",
    ".owwo......owwo.",
    ".owwo......owwo.",
    ".owwo......owwo.",
    ".owwmo....omwwo.",
    "..owwmoooomwwo..",
    "..oowwwccwwwoo..",
    "...oowwbbwwoo...",
    ".....oooooo.....",
    "................",
    "................",
]
NEURALINK_PALETTE = {"o": 0x8A929C, "w": 0xF4F7FA, "m": 0xC3C9D1, "c": 0x3EF2FF, "b": 0x1AA6C0}

png(os.path.join(TEX, "item/tech_scrap.png"), 16, 16, sprite(TECH_SCRAP, TECH_PALETTE))
# A faceted chunk of refined silicon: blue-grey and glassy, catching the light on its top faces.
SILICON = [
    "................",
    "................",
    "......oooo......",
    "....oohhhlo.....",
    "...ohhllllmoo...",
    "..ohllllmmmmmo..",
    "..olllmmmmmmdo..",
    ".ohlllmmmmmddo..",
    ".olllmmmmmdddo..",
    ".ollmmmmmddddo..",
    "..olmmmmdddddo..",
    "..ommmmddddoo...",
    "...ommddooo.....",
    "....oooo........",
    "................",
    "................",
]
SILICON_PALETTE = {"o": 0x23272F, "h": 0xD8E4F5, "l": 0x9AAAC4, "m": 0x6A7891, "d": 0x48536A}

# A microchip: a black package with silver legs on every side and a gold die mark.
COMPUTER_CHIP = [
    "................",
    "...s.s.s.s.s....",
    "...s.s.s.s.s....",
    "..oooooooooooo..",
    "ssokgkkkkkkkkoss",
    "..okkkkkkkkkko..",
    "ssokkkkkkkkkkoss",
    "..okkkllllkkko..",
    "ssokkkllllkkkoss",
    "..okkkllllkkko..",
    "ssokkkkkkkkkkoss",
    "..okkkkkkkkkko..",
    "..oooooooooooo..",
    "...s.s.s.s.s....",
    "...s.s.s.s.s....",
    "................",
]
COMPUTER_CHIP_PALETTE = {"o": 0x3A3F47, "k": 0x15171C, "s": 0xC9CED6, "g": 0xE0B04A, "l": 0x2A2E36}

png(os.path.join(TEX, "item/silicon.png"), 16, 16, sprite(SILICON, SILICON_PALETTE))
png(os.path.join(TEX, "item/computer_chip.png"), 16, 16, sprite(COMPUTER_CHIP, COMPUTER_CHIP_PALETTE))

png(os.path.join(TEX, "item/neuralink.png"), 16, 16, sprite(NEURALINK, NEURALINK_PALETTE))

# Neuralink modules: a white cartridge with gold contacts and a dark window showing the module's symbol in its
# colour, with a matching status light.
MODULE_BODY = [
    "................",
    "................",
    "...oooooooooo...",
    "..owwwwwwwwwwo..",
    "..owkkkkkkkkwo..",
    "..owk......kwo..",
    "..owk......kwo..",
    "..owk......kwo..",
    "..owk......kwo..",
    "..owkkkkkkkkwo..",
    "..owwwwaawwwwo..",
    "..ommmmmmmmmmo..",
    "..oyoyoyoyoyoo..",
    "...y.y.y.y.y....",
    "................",
    "................",
]
MODULE_SYMBOLS = {
    # A target box for ESP, an eye for night vision, a gem for x-ray.
    "esp_module": (0xFF4D6A, ["aa..aa", "a....a", "a....a", "aa..aa"]),
    "night_vision_module": (0x5CFF7A, [".aaaa.", "a.aa.a", "a.aa.a", ".aaaa."]),
    "xray_module": (0xC77DFF, ["..aa..", ".a..a.", ".a..a.", "..aa.."]),
}
for name, (accent, symbol) in MODULE_SYMBOLS.items():
    rows = [list(r) for r in MODULE_BODY]
    for sy, line in enumerate(symbol):
        for sx, ch in enumerate(line):
            rows[5 + sy][5 + sx] = "a" if ch == "a" else "k"
    palette = {"o": 0x8A929C, "w": 0xF4F7FA, "m": 0xC3C9D1, "k": 0x101820, "y": 0xE0B04A, "a": accent}
    png(os.path.join(TEX, "item/%s.png" % name), 16, 16, sprite(["".join(r) for r in rows], palette))

# Parts atlas for the worn model: 4x4 swatches of white shell, grey trim and glowing cyan.
SWATCHES = {"white": (0, 0xECEFF3, 0xFFFFFF), "trim": (4, 0xB4BBC5, 0xCDD3DB), "glow": (8, 0x3EF2FF, 0xC8FDFF)}
parts = [(0, 0, 0, 0)] * (16 * 16)
for x0, base, light in SWATCHES.values():
    for y in range(4):
        for x in range(4):
            parts[y * 16 + x0 + x] = rgba(light if (x + y) < 2 or (x == 1 and y == 1) else base)
png(os.path.join(TEX, "item/neuralink_parts.png"), 16, 16, parts)


def box(name, frm, to, swatch, skip=(), glow=False):
    x0 = SWATCHES[swatch][0]
    uv = [x0 + 0.5, 0.5, x0 + 3.5, 3.5]
    element = {"from": frm, "to": to, "name": name,
               "faces": {f: {"uv": uv, "texture": "#parts"}
                         for f in ("north", "east", "south", "west", "up", "down") if f not in skip}}
    if glow:
        element["shade"] = False
        element["light_emission"] = 15
    return element


# A white band hugs the head at ear height, from just in front of each ear round the back of the skull,
# with a pod over each ear and a module at the back. The head's sides are x = 4 and 12 and its back z = 12.
# Everything stands at least 0.7 px proud so the hat layer of a skin (0.5 px out) doesn't swallow it.
BAND_Y = [6.6, 7.8]
ELEMENTS = [
    box("right_band", [12.0, BAND_Y[0], 7.5], [12.7, BAND_Y[1], 12.0], "white", skip=("west", "south")),
    box("back_band", [3.3, BAND_Y[0], 12.0], [12.7, BAND_Y[1], 12.7], "white", skip=("north",)),
    box("left_band", [3.3, BAND_Y[0], 7.5], [4.0, BAND_Y[1], 12.0], "white", skip=("east", "south")),
    box("right_pod", [12.7, 5.8, 7.6], [13.4, 8.6, 10.4], "white", skip=("west",)),
    box("right_cap", [13.4, 6.2, 8.0], [13.7, 8.2, 10.0], "trim", skip=("west",)),
    box("right_light", [13.7, 6.8, 8.6], [13.85, 7.6, 9.4], "glow", skip=("west",), glow=True),
    box("left_pod", [2.6, 5.8, 7.6], [3.3, 8.6, 10.4], "white", skip=("east",)),
    box("left_cap", [2.3, 6.2, 8.0], [2.6, 8.2, 10.0], "trim", skip=("east",)),
    box("left_light", [2.15, 6.8, 8.6], [2.3, 7.6, 9.4], "glow", skip=("east",), glow=True),
    box("back_module", [6.4, 5.6, 12.7], [9.6, 8.8, 13.4], "white", skip=("north",)),
    box("back_trim", [6.8, 6.0, 13.4], [9.2, 8.4, 13.55], "trim", skip=("north",)),
    box("back_light", [7.2, 7.0, 13.55], [8.8, 7.4, 13.7], "glow", skip=("north",), glow=True),
]

clashes = check(ELEMENTS)
for clash in clashes:
    print("z-fighting:", clash)
if clashes:
    sys.exit(1)

MODELS = os.path.join(ASSETS, "models/item")
write_json(os.path.join(MODELS, "neuralink_worn.json"), {
    "textures": {"parts": "mymod:item/neuralink_parts", "particle": "mymod:item/neuralink_parts"},
    "elements": ELEMENTS,
    # 1.6 undoes the custom-head layer's 0.625 so model units are head pixels.
    "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.6, 1.6, 1.6]}},
})
write_json(os.path.join(MODELS, "neuralink.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/neuralink"}})
write_json(os.path.join(MODELS, "tech_scrap.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/tech_scrap"}})

for name in [*MODULE_SYMBOLS, "silicon", "computer_chip"]:
    write_json(os.path.join(MODELS, name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
ITEMS = os.path.join(ASSETS, "items")
for name in ["tech_scrap", "silicon", "computer_chip", *MODULE_SYMBOLS]:
    write_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})
write_json(os.path.join(ITEMS, "neuralink.json"), {"model": {
    "type": "minecraft:select",
    "property": "minecraft:display_context",
    "cases": [{"when": ["head"], "model": {"type": "minecraft:model", "model": "mymod:item/neuralink_worn"}}],
    "fallback": {"type": "minecraft:model", "model": "mymod:item/neuralink"},
}})
print("ok:", len(ELEMENTS), "elements")
