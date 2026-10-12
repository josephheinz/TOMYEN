"""Paints the banking and tank textures and writes their item/block models: credit cards, the ATM (the
banker's job site), the banker's villager outfit (suit, tie and a green eyeshade), the tank's entity
texture (UVs match TankModel) and the tank and shell icons.

Villager outfit UVs follow vanilla's VillagerModel: a box at (u, v) of size (w, h, d) puts its top face at
(u+d, v), its front at (u+d, v+d), sides at (u, v+d) and (u+d+w, v+d), and its back at (u+2d+w, v+d)."""
import json, os, random, struct, sys, zlib

ASSETS = sys.argv[1]  # .../assets/mymod
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

# --- Tank and shell icons --------------------------------------------------------------------------------
TANK_ICON = [
    "................",
    "................",
    "................",
    "................",
    "......oooo......",
    ".....oTTTTo.....",
    ".....oTTTTooooo.",
    "...ooHHHHHHHoo..",
    "..oHHGGGGGGGGHo.",
    "..oGGGGGGGGGGGo.",
    ".okkkkkkkkkkkkko",
    ".okwkkwkkwkkwkko",
    ".okkkkkkkkkkkkko",
    "..ooooooooooooo.",
    "................",
    "................",
]
sprite(TANK_ICON, {"o": 0x22261A, "T": 0x6B7A3C, "H": 0x8A9A52, "G": 0x5A6830, "k": 0x3A3A3A, "w": 0x8A8A8A}
       ).save(os.path.join(TEX, "item", "tank.png"))
SHELL = [
    "................",
    "............ss..",
    "...........sSSs.",
    "..........sSSs..",
    ".........bbSs...",
    "........bBBb....",
    ".......bBBb.....",
    "......bBBb......",
    ".....bBBb.......",
    "....bBBb........",
    "...bBBb.........",
    "...dBb..........",
    "...dd...........",
    "................",
    "................",
    "................",
]
sprite(SHELL, {"s": 0x4A4E56, "S": 0x9AA0AA, "b": 0x8A6420, "B": 0xD8A848, "d": 0x5A4010}).save(os.path.join(TEX, "item", "tank_shell.png"))

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
    "textures": {"front": "mymod:block/atm_front", "side": "mymod:block/atm_side", "top": "mymod:block/atm_top"},
})
write_json(os.path.join(ASSETS, "blockstates", "atm.json"), {"variants": {
    "facing=north": {"model": "mymod:block/atm"},
    "facing=east": {"model": "mymod:block/atm", "y": 90},
    "facing=south": {"model": "mymod:block/atm", "y": 180},
    "facing=west": {"model": "mymod:block/atm", "y": 270},
}})
write_json(os.path.join(ASSETS, "items", "atm.json"), {"model": {"type": "minecraft:model", "model": "mymod:block/atm"}})
for name in [*CARDS, "tank", "tank_shell"]:
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


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

# --- Tank ------------------------------------------------------------------------------------------------
OLIVE = rgba(0x5E6B34)
OLIVE_LIGHT = rgba(0x7A8A46)
OLIVE_DARK = rgba(0x434D24)
TRACK = rgba(0x34363A)
TRACK_LIGHT = rgba(0x5A5E64)
METAL = rgba(0x6E737A)
LAMP = rgba(0xFFF2B0)


def tank():
    c = Canvas(128, 128)
    # Tracks: tread plates across the top and bottom, links along the sides with road wheels.
    for face, (x0, y0, x1, y1) in box_faces(0, 0, 6, 10, 40).items():
        c.rect(x0, y0, x1, y1, TRACK, 0.06)
        if face in ("top", "bottom"):
            for y in range(y0, y1, 2):
                c.rect(x0, y, x1, y + 1, TRACK_LIGHT)
        elif face in ("right", "left"):
            for x in range(x0, x1, 2):
                c.rect(x, y0, x + 1, y0 + 1, TRACK_LIGHT)
                c.rect(x, y1 - 1, x + 1, y1, TRACK_LIGHT)
            for x in range(x0 + 2, x1 - 3, 6):
                c.rect(x, y0 + 3, x + 4, y0 + 7, METAL)
                c.set(x + 1, y0 + 4, TRACK_LIGHT)
        else:
            c.rect(x0 + 1, y0 + 2, x1 - 1, y1 - 2, METAL)
    # Hull: olive plate with panel lines, a bit of wear, and lighter edges on top.
    for face, (x0, y0, x1, y1) in box_faces(0, 50, 20, 11, 36).items():
        c.rect(x0, y0, x1, y1, OLIVE, 0.06)
        if face == "top":
            c.rect(x0, y0, x1, y0 + 1, OLIVE_LIGHT)
            for y in range(y0 + 6, y1, 10):
                c.rect(x0 + 1, y, x1 - 1, y + 1, OLIVE_DARK)
        elif face in ("right", "left"):
            c.rect(x0, y0, x1, y0 + 1, OLIVE_LIGHT)
            for x in range(x0 + 8, x1, 9):
                c.rect(x, y0 + 1, x + 1, y1, OLIVE_DARK)
        else:
            c.rect(x0, y0, x1, y0 + 1, OLIVE_LIGHT)
            c.rect(x0 + 2, y0 + 4, x1 - 2, y0 + 5, OLIVE_DARK)
    # Turret: olive, with a gold emerald (the bank's mark) on each side.
    for face, (x0, y0, x1, y1) in box_faces(0, 97, 16, 7, 16).items():
        c.rect(x0, y0, x1, y1, OLIVE, 0.06)
        if face != "top":
            c.rect(x0, y0, x1, y0 + 1, OLIVE_LIGHT)
        if face in ("right", "left"):
            mx, my = (x0 + x1) // 2, (y0 + y1) // 2
            for dx, dy in ((0, -2), (-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
                c.set(mx + dx, my + dy, rgba(0x17DD62) if (dx, dy) != (0, -1) else rgba(0x9BF5BE))
        if face == "top":
            # The hatch ring, round where the crew stands.
            c.rect(x0 + 4, y0 + 4, x1 - 4, y1 - 4, OLIVE_DARK)
            c.rect(x0 + 5, y0 + 5, x1 - 5, y1 - 5, rgba(0x1A1C14))
    for face, (x0, y0, x1, y1) in box_faces(64, 97, 6, 5, 2).items():  # mantlet
        c.rect(x0, y0, x1, y1, OLIVE_DARK, 0.05)
    for face, (x0, y0, x1, y1) in box_faces(80, 97, 3, 3, 20).items():  # barrel
        c.rect(x0, y0, x1, y1, OLIVE_DARK, 0.05)
        if face == "front":
            c.rect(x0, y0, x1, y1, rgba(0x101010))
            c.rect(x0, y0, x1, y0 + 1, OLIVE_DARK)
    for face, (x0, y0, x1, y1) in box_faces(92, 0, 6, 6, 1).items():  # hatch lid
        c.rect(x0, y0, x1, y1, OLIVE, 0.05)
    for face, (x0, y0, x1, y1) in box_faces(92, 8, 2, 2, 4).items():  # exhaust
        c.rect(x0, y0, x1, y1, rgba(0x2A2A2A))
    for face, (x0, y0, x1, y1) in box_faces(92, 16, 2, 2, 1).items():  # headlights
        c.rect(x0, y0, x1, y1, LAMP if face == "front" else METAL)
    return c


tank().save(os.path.join(TEX, "entity", "tank", "tank.png"))
print("ok")
