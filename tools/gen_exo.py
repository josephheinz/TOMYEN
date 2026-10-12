"""Paints the exoskeleton and its modules, and writes their item models. The worn suit is a code-defined model
(ExoskeletonModel) on a 64x64 texture of four 32x32 materials, one per quadrant: frame (top left), plate (top
right), joint (bottom left) and light (bottom right). Every cube of the model unwraps inside its material's
quadrant, so each material is just a tileable patch. The glow texture holds only the light quadrant, drawn
again full-bright over the rest.

Usage: python3 tools/gen_exo.py src/main/resources/assets/mymod"""
import json, os, struct, sys, zlib

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


def noise(x, y, seed):
    """A repeatable -1..1 value per pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 127.5 - 1.0


def shade(hex_colour, amount):
    r, g, b, a = rgba(hex_colour)
    clamp = lambda v: max(0, min(255, int(round(v + amount))))
    return (clamp(r), clamp(g), clamp(b), a)


def frame(x, y):
    # Gunmetal beams: a dark seam every 6 rows and a rivet between seams.
    if y % 6 == 5:
        return shade(0x353A42, 0)
    if x % 8 == 2 and y % 6 == 2:
        return shade(0x9AA2AC, 0)
    return shade(0x4A505A, 7 * noise(x, y, 1))


def plate(x, y):
    # Brushed steel panels with a lit top edge and a dark seam.
    if y % 8 == 7 or x % 10 == 9:
        return shade(0x6E767F, 0)
    if y % 8 == 0:
        return shade(0xC2CAD2, 0)
    return shade(0x9AA3AD, 5 * noise(x, y, 2))


def joint(x, y):
    # Dark rubber-and-steel bellows: alternating ridges.
    return shade(0x3A3F48 if y % 3 == 0 else 0x262A31, 4 * noise(x, y, 3))


def light(x, y):
    # Cyan running lights, brighter down the middle of each 4-pixel band.
    return shade(0xC8FDFF if y % 4 in (1, 2) and x % 4 in (1, 2) else 0x3EF2FF, 0)


suit = [(0, 0, 0, 0)] * (64 * 64)
glow = [(0, 0, 0, 0)] * (64 * 64)
for y in range(64):
    for x in range(64):
        lx, ly = x % 32, y % 32
        if y < 32:
            suit[y * 64 + x] = frame(lx, ly) if x < 32 else plate(lx, ly)
        elif x < 32:
            suit[y * 64 + x] = joint(lx, ly)
        else:
            suit[y * 64 + x] = glow[y * 64 + x] = light(lx, ly)
png(os.path.join(TEX, "entity/exoskeleton/exoskeleton.png"), 64, 64, suit)
png(os.path.join(TEX, "entity/exoskeleton/exoskeleton_glow.png"), 64, 64, glow)

# The suit from the front: shoulder pads either side of a collar with the glowing core, struts down the arms
# with elbow joints and cuffs, the belt, and the legs' struts with knee joints down to the boots.
EXOSKELETON = [
    "................",
    "......oooo......",
    "..ooo.oFFo.ooo..",
    ".oPPPofccfoPPPo.",
    ".opppofPPfopppo.",
    ".ofo.ofppfo.ofo.",
    ".ofo.ofppfo.ofo.",
    ".ojo.ofppfo.ojo.",
    ".ofo.offffo.ofo.",
    ".opo.offffo.opo.",
    "....ojjjjjjo....",
    ".....ofoofo.....",
    ".....ojoojo.....",
    ".....ofoofo.....",
    "....oppooppo....",
    "....oooooooo....",
]
EXOSKELETON_PALETTE = {"o": 0x1E2228, "f": 0x5A616B, "F": 0x8A929C, "p": 0x9AA3AD, "P": 0xC9D0D8,
                       "j": 0x2E333B, "c": 0x3EF2FF}
png(os.path.join(TEX, "item/exoskeleton.png"), 16, 16, sprite(EXOSKELETON, EXOSKELETON_PALETTE))

# Exoskeleton modules: a heavy gunmetal cartridge with brass contacts and the module's symbol in its colour.
MODULE_BODY = [
    "................",
    "................",
    "..oooooooooooo..",
    "..offffffffffo..",
    "..ofkkkkkkkkfo..",
    "..ofk......kfo..",
    "..ofk......kfo..",
    "..ofk......kfo..",
    "..ofk......kfo..",
    "..ofk......kfo..",
    "..ofkkkkkkkkfo..",
    "..offffffffffo..",
    "..oyoyoyoyoyoo..",
    "...y.y.y.y.y....",
    "................",
    "................",
]
MODULE_SYMBOLS = {
    # A riveted plate; a double chevron pointing up.
    "reinforced_plating_module": (0xB0BEC5, ["aaaaaa", "a.aa.a", "aaaaaa", "a.aa.a", "aaaaaa"]),
    "servo_legs_module": (0xFFB300, ["..aa..", ".a..a.", "a.aa.a", ".a..a.", "a....a"]),
}
for name, (accent, symbol) in MODULE_SYMBOLS.items():
    rows = [list(r) for r in MODULE_BODY]
    for sy, line in enumerate(symbol):
        for sx, ch in enumerate(line):
            rows[5 + sy][5 + sx] = "a" if ch == "a" else "k"
    palette = {"o": 0x1E2228, "f": 0x5A616B, "k": 0x0E1014, "y": 0xC9A23A, "a": accent}
    png(os.path.join(TEX, "item/%s.png" % name), 16, 16, sprite(["".join(r) for r in rows], palette))

MODELS = os.path.join(ASSETS, "models/item")
ITEMS = os.path.join(ASSETS, "items")
for name in ["exoskeleton", *MODULE_SYMBOLS]:
    write_json(os.path.join(MODELS, name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
    write_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})
print("ok")
