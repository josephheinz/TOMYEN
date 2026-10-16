"""Paints the banking textures and writes their item/block models: credit and debit cards, the ATM (the banker's job
site) and the banker's villager outfit (suit, tie and a green eyeshade). The bank's tank is painted by gen_tank.py.

Villager outfit UVs follow vanilla's VillagerModel: a box at (u, v) of size (w, h, d) puts its top face at
(u+d, v), its front at (u+d, v+d), sides at (u, v+d) and (u+d+w, v+d), and its back at (u+2d+w, v+d)."""
import json, os, random, struct, sys, zlib

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
rnd = random.Random(11)
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


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def rect(self, x0, y0, x1, y1, c, mottle=0.0):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, shade(c, 1.0 + rnd.uniform(-mottle, mottle)) if mottle else c)

    def save(self, path):
        png(path, self.w, self.h, self.px)


def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# --- Credit cards ----------------------------------------------------------------------------------------
CARD = [
    "................",
    "................",
    "................",
    ".oooooooooooooo.",
    "obbbbbbbbbbbbbbo",
    "obhhhhhhhhhhhhbo",
    "obggbbbbbbbbbbbo",
    "obgGbbbbbbbbbbbo",
    "obbbbbbbbbbbbbbo",
    "obwwbwwbwwbwwbbo",
    "obbbbbbbbbbbbbbo",
    "obbbbbbbbbblLbbo",
    "obbbbbbbbbbbbbbo",
    ".oooooooooooooo.",
    "................",
    "................",
]
CARDS = {
    "credit_card": {"o": 0x1A2F5C, "b": 0x2E5FB8, "h": 0x5B86D6, "w": 0xF2F2F2},
    "gold_credit_card": {"o": 0x6B4E14, "b": 0xD4A93A, "h": 0xF0D27A, "w": 0xFFF6DC},
    "platinum_credit_card": {"o": 0x2A2E36, "b": 0xB8BEC8, "h": 0xE8ECF2, "w": 0x2A2E36},
}
for name, colours in CARDS.items():
    palette = {"g": 0xE8C25A, "G": 0xB8902E, "l": 0xE04040, "L": 0xF0A030, **colours}
    sprite(CARD, palette).save(os.path.join(TEX, "item", name + ".png"))

# The debit card: slate, with an emerald stripe and the bank's emerald in place of the card network logo.
DEBIT_CARD = CARD[:9] + [
    "obwwbwwbwwbbEbbo",
    "obbbbbbbbbbEEebo",
    "obbbbbbbbbbEeebo",
    "obbbbbbbbbbbebbo",
] + CARD[13:]
sprite(DEBIT_CARD, {"o": 0x14181E, "b": 0x323A44, "h": 0x1F9E58, "w": 0xD0D6DC, "g": 0xE8C25A, "G": 0xB8902E,
                    "e": 0x17DD62, "E": 0xB8FFD2}).save(os.path.join(TEX, "item", "debit_card.png"))

# --- ATM -------------------------------------------------------------------------------------------------
STEEL = rgba(0x8A9099)
STEEL_LIGHT = rgba(0xB4BAC3)
STEEL_DARK = rgba(0x555B64)
SCREEN = rgba(0x0E2A1A)
GLOW = rgba(0x4CE08A)
GLOW_DIM = rgba(0x2A8A52)
BLACK = rgba(0x15171B)
KEY = rgba(0xD6DAE0)
EMERALD = rgba(0x17DD62)


def steel_face():
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, STEEL, 0.04)
    c.rect(0, 0, 16, 1, STEEL_LIGHT)
    c.rect(0, 0, 1, 16, STEEL_LIGHT)
    c.rect(0, 15, 16, 16, STEEL_DARK)
    c.rect(15, 0, 16, 16, STEEL_DARK)
    return c


side = steel_face()
for y in (4, 11):  # panel seams
    side.rect(1, y, 15, y + 1, STEEL_DARK)
side.save(os.path.join(TEX, "block", "atm_side.png"))

top = steel_face()
for y in range(4, 12, 2):  # vents
    top.rect(4, y, 12, y + 1, STEEL_DARK)
top.save(os.path.join(TEX, "block", "atm_top.png"))

front = steel_face()
front.rect(2, 2, 14, 8, BLACK)  # screen bezel
front.rect(3, 3, 13, 7, SCREEN)
front.rect(4, 4, 10, 5, GLOW)  # "WELCOME" and a menu line
front.rect(4, 6, 8, 7, GLOW_DIM)
front.set(11, 6, GLOW)
for row in range(3):  # keypad, three by three, with a green "enter" key
    for col in range(3):
        front.set(3 + col * 2, 9 + row * 2, KEY if (row, col) != (2, 2) else EMERALD)
front.rect(9, 9, 13, 10, BLACK)  # card slot with its light
front.set(13, 9, GLOW)
front.rect(9, 11, 12, 12, STEEL_DARK)  # receipt printer
front.rect(9, 13, 14, 14, BLACK)  # cash dispenser
front.save(os.path.join(TEX, "block", "atm_front.png"))

write_json(os.path.join(ASSETS, "models", "block", "atm.json"), {
    "parent": "minecraft:block/orientable",
    "textures": {"front": "tomyen:block/atm_front", "side": "tomyen:block/atm_side", "top": "tomyen:block/atm_top"},
})
write_json(os.path.join(ASSETS, "blockstates", "atm.json"), {"variants": {
    "facing=north": {"model": "tomyen:block/atm"},
    "facing=east": {"model": "tomyen:block/atm", "y": 90},
    "facing=south": {"model": "tomyen:block/atm", "y": 180},
    "facing=west": {"model": "tomyen:block/atm", "y": 270},
}})
write_json(os.path.join(ASSETS, "items", "atm.json"), {"model": {"type": "minecraft:model", "model": "tomyen:block/atm"}})
for name in CARDS:
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


# --- Banker villager outfit ------------------------------------------------------------------------------
def box_faces(u, v, w, h, d):
    """Face rectangles (x0, y0, x1, y1) of a model box's texture: top, bottom, right, front, left, back."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


SUIT = rgba(0x2B2F3A)
SUIT_DARK = rgba(0x1E2129)
SHIRT = rgba(0xEDEDE8)
TIE = rgba(0x1E7A3C)
BUTTON = rgba(0xD8B040)
VISOR = rgba(0x2E9A4A, 210)
VISOR_EDGE = rgba(0x1C6A30, 230)
BAND = rgba(0x1C6A30)


def outfit():
    c = Canvas(64, 64)
    # Body (under the robe) and the robe ("jacket") itself: a dark suit all round.
    for u, v, w, h, d in ((16, 20, 8, 12, 6), (0, 38, 8, 20, 6)):
        for face, (x0, y0, x1, y1) in box_faces(u, v, w, h, d).items():
            c.rect(x0, y0, x1, y1, SUIT, 0.05)
    # The robe's front: shirt and tie in a V between the lapels, buttons below, and a pocket square.
    fx, fy = 6, 44
    for row in range(7):
        half = max(0, 3 - row // 2)
        c.rect(fx + 4 - half, fy + row, fx + 4 + half, fy + row + 1, SHIRT)
    c.rect(fx + 3, fy, fx + 5, fy + 6, TIE)
    c.rect(fx + 3, fy + 6, fx + 5, fy + 7, shade(TIE, 0.8))
    for row in range(1, 7):  # lapel edges
        c.set(fx + 3 - max(0, 3 - row // 2) - 1, fy + row, SUIT_DARK)
        c.set(fx + 4 + max(0, 3 - row // 2), fy + row, SUIT_DARK)
    for y in (fy + 9, fy + 12):
        c.set(fx + 4, y, BUTTON)
    c.rect(fx + 6, fy + 2, fx + 8, fy + 3, SHIRT)
    c.rect(fx, fy + 19, fx + 8, fy + 20, SUIT_DARK)  # hem
    # Sleeves: suit, with white cuffs at the hand end; the crossed hands stay bare.
    for face, (x0, y0, x1, y1) in box_faces(44, 22, 4, 8, 4).items():
        if face == "top":
            c.rect(x0, y0, x1, y1, SUIT)
        elif face != "bottom":
            c.rect(x0, y0, x1, y1 - 1, SUIT, 0.05)
            c.rect(x0, y1 - 1, x1, y1, SHIRT)
    # Green eyeshade: a band round the hat at the brow, and the visor out front on the brim. The brim's top
    # face is at (31, 48); its last rows are the front edge, over the face.
    for face, (x0, y0, x1, y1) in box_faces(32, 0, 8, 10, 8).items():
        if face not in ("top", "bottom"):
            c.rect(x0, y0 + 4, x1, y0 + 5, BAND)
    for y in range(48 + 10, 48 + 16):
        reach = 3 + (y - 58)
        for x in range(31 + 8 - reach - 2, 31 + 8 + reach + 2):
            c.set(x, y, VISOR_EDGE if y == 63 or abs(x - 38.5) > reach + 1 else VISOR)
    return c


outfit().save(os.path.join(TEX, "entity", "villager", "profession", "banker.png"))
outfit().save(os.path.join(TEX, "entity", "zombie_villager", "profession", "banker.png"))

print("ok")
