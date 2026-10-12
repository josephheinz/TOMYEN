"""Paints the tank and its rounds, and writes everything around them.

Textures: the tank's entity texture (512x512; UVs match TankModel: the deck, turret, bustle, cheeks and skirts have
areas of their own, the small parts borrow from plain swatches of olive, track and dark metal), and the icons for the
tank, the APFSDS and HE rounds and the bank's shell. Also their item models and definitions, and on the data side the
crafting recipes with their recipe-book advancements. With ffmpeg on the path it synthesises the sounds the crew hears
inside the turret: the gun going off, the breech, the rammer and the spent case stub. ffmpeg's own Vorbis encoder
only writes stereo, which the game plays without placing it in the world, so these are only ever played to the crew
(the world outside hears the tank through pitched vanilla sounds, see sounds.json).

The tank used to be painted by gen_bank.py; it lives here now.

Usage: python3 tools/gen_tank.py src/main/resources/assets/mymod"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave, zlib

ASSETS = sys.argv[1]  # .../assets/mymod
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
TEX = os.path.join(ASSETS, "textures")
rnd = random.Random(120)
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

    def get(self, x, y):
        return self.px[y * self.w + x]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def rect(self, x0, y0, x1, y1, c, mottle=0.0):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, shade(c, 1.0 + rnd.uniform(-mottle, mottle)) if mottle else c)

    def shade_rect(self, x0, y0, x1, y1, f):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < self.w and 0 <= y < self.h:
                    self.set(x, y, shade(self.get(x, y), f))

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


def box_faces(u, v, w, h, d):
    """Face rectangles (x0, y0, x1, y1) of a model box's texture. On the top face the first row is the back (+z)
    and the last the front; on the side faces the column next to the front face is the front end. "right" is the
    -x side, which is the tank's right."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# --- Palette --------------------------------------------------------------------------------------------------
OLIVE = rgba(0x56603A)
OLIVE_LIGHT = rgba(0x707C4A)
OLIVE_DARK = rgba(0x3E462A)
CAMO_DARK = rgba(0x3A3F26)
CAMO_BROWN = rgba(0x5A4E34)
TRACK = rgba(0x2C2E31)
TRACK_LIGHT = rgba(0x4C4F54)
RUBBER = rgba(0x1E1F21)
METAL = rgba(0x5E6268)
METAL_DARK = rgba(0x34373B)
BLACK = rgba(0x101112)
GLASS = rgba(0x1A2A38)
GLASS_HI = rgba(0x6FA8C8)
LAMP = rgba(0xFFF2B0)
TAIL = rgba(0xC0302A)
MUD = rgba(0x4A3E2C)
INTERIOR = rgba(0xC8D2B8)


# Blotchy three-tone camouflage from layered value noise, the same pattern wherever it's sampled.
def _lattice(seed):
    r = random.Random(seed)
    return [[r.random() for _ in range(40)] for _ in range(40)]


NOISE = [_lattice(1), _lattice(2)]


def noise(layer, x, y, scale):
    fx, fy = x / scale, y / scale
    ix, iy = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    g = NOISE[layer]
    a, b = g[iy % 40][ix % 40], g[iy % 40][(ix + 1) % 40]
    c, d = g[(iy + 1) % 40][ix % 40], g[(iy + 1) % 40][(ix + 1) % 40]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def camo(x, y):
    n = noise(0, x, y, 11.0) * 0.7 + noise(1, x, y, 5.0) * 0.3
    if n > 0.64:
        base = CAMO_DARK
    elif n < 0.3:
        base = CAMO_BROWN
    else:
        base = OLIVE
    return shade(base, 1.0 + rnd.uniform(-0.04, 0.04))


def camo_rect(c, x0, y0, x1, y1):
    for y in range(y0, y1):
        for x in range(x0, x1):
            c.set(x, y, camo(x, y))


def edge_light(c, x0, y0, x1, y1):
    """A light top edge and a dark bottom edge on a side face, as if worn and lit from above."""
    for x in range(x0, x1):
        c.set(x, y0, shade(c.get(x, y0), 1.25))
        c.set(x, y1 - 1, shade(c.get(x, y1 - 1), 0.75))


def bolts(c, x0, x1, y, step, colour=None):
    for x in range(x0, x1, step):
        c.set(x, y, colour or OLIVE_LIGHT)


# --- The tank ---------------------------------------------------------------------------------------------------
def tank():
    c = Canvas(512, 512)

    # Swatches the small parts borrow from: olive camo, track links, and dark metal (gun tube, MG, antennas).
    camo_rect(c, 0, 436, 256, 512)
    c.rect(0, 230, 256, 340, TRACK, 0.05)
    for y in range(230, 340, 4):
        c.rect(0, y, 256, y + 1, TRACK_LIGHT)
        for x in range(1, 256, 6):
            c.set(x, y + 2, METAL)
    for y in range(344, 430):
        for x in range(0, 200):
            c.set(x, y, shade(OLIVE_DARK, 1.0 + rnd.uniform(-0.03, 0.03) + 0.06 * math.sin(y * 0.9)))
    c.rect(150, 364, 200, 430, METAL_DARK, 0.05)

    # Deck: camo, the engine deck's grilles at the back, the driver's hatch and periscopes at the front, anti-slip
    # walkways over the fenders.
    faces = box_faces(0, 0, 56, 10, 110)
    for name, (x0, y0, x1, y1) in faces.items():
        if name == "bottom":
            c.rect(x0, y0, x1, y1, OLIVE_DARK, 0.04)
        else:
            camo_rect(c, x0, y0, x1, y1)
    x0, y0, x1, y1 = faces["top"]
    for y in range(y0 + 2, y0 + 30, 2):  # engine deck grilles
        c.rect(x0 + 12, y, x1 - 12, y + 1, BLACK)
    c.rect(x0 + 11, y0 + 1, x1 - 11, y0 + 2, OLIVE_DARK)
    c.rect(x0 + 11, y0 + 30, x1 - 11, y0 + 31, OLIVE_DARK)
    for y in range(y0, y1):  # fender walkways
        for x in list(range(x0 + 1, x0 + 10)) + list(range(x1 - 10, x1 - 1)):
            if (x + y) % 3 == 0:
                c.set(x, y, shade(c.get(x, y), 1.15))
    c.rect(x0 + 22, y1 - 14, x1 - 22, y1 - 6, OLIVE_DARK)  # driver's hatch
    c.rect(x0 + 23, y1 - 13, x1 - 23, y1 - 7, OLIVE)
    for px in (x0 + 21, x0 + 27, x1 - 22):  # periscopes
        c.rect(px, y1 - 16, px + 2, y1 - 14, GLASS)
    c.rect(x0, y1 - 1, x1, y1, OLIVE_LIGHT)
    for name in ("right", "left"):
        fx0, fy0, fx1, fy1 = faces[name]
        edge_light(c, fx0, fy0, fx1, fy1)
        for x in range(fx0 + 8, fx1, 14):  # tie-down points
            c.set(x, fy0 + 3, METAL_DARK)
    bx0, by0, bx1, by1 = faces["back"]  # the rear plate: grille, tail lights, tow pintle
    edge_light(c, bx0, by0, bx1, by1)
    for x in range(bx0 + 12, bx1 - 12, 2):
        c.rect(x, by0 + 2, x + 1, by1 - 2, BLACK)
    for lx in (bx0 + 2, bx1 - 4):
        c.rect(lx, by0 + 2, lx + 2, by0 + 4, TAIL)
        c.rect(lx, by0 + 5, lx + 2, by0 + 6, rgba(0xE0A030))
    c.rect(bx0 + 26, by1 - 3, bx0 + 30, by1 - 1, METAL_DARK)

    # Turret: camo, panel seams and lifting eyes on the roof, the bank's emerald on each side.
    faces = box_faces(0, 120, 44, 12, 50)
    for name, (x0, y0, x1, y1) in faces.items():
        if name == "bottom":
            c.rect(x0, y0, x1, y1, OLIVE_DARK)
        else:
            camo_rect(c, x0, y0, x1, y1)
        if name in ("right", "left", "front", "back"):
            edge_light(c, x0, y0, x1, y1)
    x0, y0, x1, y1 = faces["top"]
    c.rect(x0, y0 + 20, x1, y0 + 21, OLIVE_DARK)
    c.rect(x0 + 22, y0, x0 + 23, y0 + 20, OLIVE_DARK)
    for ex, ey in ((x0 + 2, y0 + 2), (x1 - 3, y0 + 2), (x0 + 2, y1 - 3), (x1 - 3, y1 - 3)):
        c.set(ex, ey, METAL_DARK)
    c.rect(x0, y1 - 1, x1, y1, OLIVE_LIGHT)
    for name in ("right", "left"):
        fx0, fy0, fx1, fy1 = faces[name]
        mx, my = (fx0 + fx1) // 2, (fy0 + fy1) // 2
        for dx, dy in ((0, -2), (-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
            c.set(mx + dx, my + dy, rgba(0x17DD62) if (dx, dy) != (0, -1) else rgba(0x9BF5BE))
        bolts(c, fx0 + 2, fx1 - 1, fy1 - 2, 6, METAL_DARK)

    # Bustle: the stowage basket, its mesh over khaki kit bags.
    faces = box_faces(192, 120, 38, 10, 14)
    for name, (x0, y0, x1, y1) in faces.items():
        camo_rect(c, x0, y0, x1, y1)
        if name in ("right", "left", "back", "top"):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if noise(1, x * 2, y * 2, 4.0) > 0.55:
                        c.set(x, y, shade(rgba(0x8A7A52), 1.0 + rnd.uniform(-0.08, 0.08)))
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if (x - x0) % 3 == 0 or (y - y0) % 3 == 0:
                        c.set(x, y, METAL_DARK)

    # Cheeks: the composite armour's face plates, bolted.
    faces = box_faces(192, 144, 36, 10, 8)
    for name, (x0, y0, x1, y1) in faces.items():
        camo_rect(c, x0, y0, x1, y1)
    x0, y0, x1, y1 = faces["front"]
    c.shade_rect(x0, y0, x1, y1, 0.85)
    bolts(c, x0 + 1, x1, y0 + 1, 4, OLIVE_LIGHT)
    bolts(c, x0 + 1, x1, y1 - 2, 4, OLIVE_LIGHT)
    edge_light(c, x0, y0, x1, y1)

    # Side skirts: panels with seams and bolts along the top, mud splashed up from the bottom.
    faces = box_faces(300, 120, 1, 9, 96)
    for name, (x0, y0, x1, y1) in faces.items():
        camo_rect(c, x0, y0, x1, y1)
    for name in ("right", "left"):
        x0, y0, x1, y1 = faces[name]
        for x in range(x0 + 15, x1, 16):
            c.rect(x, y0, x + 1, y1, OLIVE_DARK)
        bolts(c, x0 + 3, x1, y0 + 1, 4, METAL_DARK)
        for y in range(y0 + 4, y1):
            for x in range(x0, x1):
                if rnd.random() < (y - y0 - 3) / 8.0:
                    c.set(x, y, shade(MUD, 1.0 + rnd.uniform(-0.1, 0.1)))
        edge_light(c, x0, y0, x1, y1)

    # Road wheels and the idler: rubber tyre, olive disc, hub.
    faces = box_faces(260, 230, 8, 10, 10)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, RUBBER, 0.05)
    for name in ("right", "left"):
        x0, y0, x1, y1 = faces[name]
        for y in range(y0, y1):
            for x in range(x0, x1):
                r = math.hypot(x - x0 - 4.5, y - y0 - 4.5)
                c.set(x, y, RUBBER if r > 3.6 else METAL_DARK if r < 1.2 else OLIVE_DARK if r < 2.0 else OLIVE)
    # Drive sprocket: teeth round a dark ring, olive disc, hub.
    faces = box_faces(260, 252, 9, 12, 12)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, METAL_DARK, 0.05)
        if name in ("top", "bottom", "front", "back"):
            for y in range(y0, y1, 2):
                c.rect(x0, y, x1, y + 1, METAL)
    for name in ("right", "left"):
        x0, y0, x1, y1 = faces[name]
        for y in range(y0, y1):
            for x in range(x0, x1):
                dx, dy = x - x0 - 5.5, y - y0 - 5.5
                r = math.hypot(dx, dy)
                angle = math.atan2(dy, dx)
                if r > 4.6:
                    colour = METAL if math.sin(angle * 6) > 0 else METAL_DARK
                elif r > 3.8:
                    colour = BLACK
                else:
                    colour = METAL_DARK if r < 1.3 else OLIVE
                c.set(x, y, colour)

    # Muzzle: the bore.
    faces = box_faces(204, 344, 4, 4, 3)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, OLIVE_DARK)
    x0, y0, x1, y1 = faces["front"]
    c.rect(x0, y0, x1, y1, METAL_DARK)
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, BLACK)
    # Fume extractor: banded at both ends.
    faces = box_faces(150, 344, 5, 5, 12)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, OLIVE, 0.03)
    for name in ("right", "left", "top", "bottom"):
        x0, y0, x1, y1 = faces[name]
        if name in ("right", "left"):
            c.rect(x0, y0, x0 + 1, y1, OLIVE_DARK)
            c.rect(x1 - 1, y0, x1, y1, OLIVE_DARK)
        else:
            c.rect(x0, y0, x1, y0 + 1, OLIVE_DARK)
            c.rect(x0, y1 - 1, x1, y1, OLIVE_DARK)

    # Smoke launchers: a 2x2 block of tube mouths.
    faces = box_faces(300, 280, 4, 5, 8)
    for name, (x0, y0, x1, y1) in faces.items():
        camo_rect(c, x0, y0, x1, y1)
    x0, y0, x1, y1 = faces["front"]
    c.rect(x0, y0, x1, y1, OLIVE_DARK)
    for tx, ty in ((x0, y0 + 1), (x0 + 2, y0 + 1), (x0, y0 + 3), (x0 + 2, y0 + 3)):
        c.set(tx, ty, BLACK)
        c.set(tx + 1, ty, BLACK)

    # Gunner's sight and commander's viewer: armoured boxes with glass at the front.
    for (u, v, w, h, d) in ((330, 280, 7, 4, 7), (360, 280, 5, 5, 5)):
        faces = box_faces(u, v, w, h, d)
        for name, (x0, y0, x1, y1) in faces.items():
            camo_rect(c, x0, y0, x1, y1)
            if name != "top" and name != "bottom":
                edge_light(c, x0, y0, x1, y1)
        x0, y0, x1, y1 = faces["front"]
        c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GLASS)
        c.set(x0 + 1, y0 + 1, GLASS_HI)

    # Commander's cupola: vision blocks all round, the hatch opening on top.
    faces = box_faces(300, 300, 12, 2, 12)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, OLIVE, 0.04)
    for name in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = faces[name]
        for x in range(x0 + 1, x1 - 1, 3):
            c.set(x, y0, GLASS)
            c.set(x + 1, y0, GLASS_HI)
    x0, y0, x1, y1 = faces["top"]
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, BLACK)
    # Hatch lids: camo outside with a hinge and handle, the pale-painted inside under the commander's.
    for (u, v, w, d) in ((350, 300, 12, 12), (400, 300, 10, 10)):
        faces = box_faces(u, v, w, 1, d)
        for name, (x0, y0, x1, y1) in faces.items():
            camo_rect(c, x0, y0, x1, y1)
        x0, y0, x1, y1 = faces["top"]
        c.rect(x0, y0, x1, y0 + 1, METAL_DARK)  # hinge, at the back
        c.rect(x0 + 2, y1 - 3, x1 - 2, y1 - 2, OLIVE_LIGHT)  # grab handle
        x0, y0, x1, y1 = faces["bottom"]
        c.rect(x0, y0, x1, y1, INTERIOR, 0.03)
        c.rect(x0 + 2, y0 + 2, x0 + 4, y1 - 2, METAL)
    # Headlights: a lamp behind a guard.
    faces = box_faces(450, 300, 4, 2, 2)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, OLIVE_DARK)
    x0, y0, x1, y1 = faces["front"]
    c.rect(x0, y0, x1, y1, LAMP)
    c.rect(x0 + 1, y0, x0 + 2, y1, METAL_DARK)
    # Exhaust grille.
    faces = box_faces(300, 320, 24, 4, 2)
    for name, (x0, y0, x1, y1) in faces.items():
        c.rect(x0, y0, x1, y1, METAL_DARK)
    x0, y0, x1, y1 = faces["back"]
    for x in range(x0, x1, 2):
        c.rect(x, y0, x + 1, y1, BLACK)
    # Stowage bins: lid line and latches.
    faces = box_faces(300, 330, 2, 7, 20)
    for name, (x0, y0, x1, y1) in faces.items():
        camo_rect(c, x0, y0, x1, y1)
    for name in ("right", "left"):
        x0, y0, x1, y1 = faces[name]
        c.rect(x0, y0 + 1, x1, y0 + 2, OLIVE_DARK)
        for x in (x0 + 4, x1 - 5):
            c.rect(x, y0 + 2, x + 1, y0 + 4, METAL)
        edge_light(c, x0, y0, x1, y1)
    # Mantlet: the gun shield, with the coax port on the tank's right of the gun.
    faces = box_faces(350, 330, 12, 8, 4)
    for name, (x0, y0, x1, y1) in faces.items():
        camo_rect(c, x0, y0, x1, y1)
    x0, y0, x1, y1 = faces["front"]
    c.shade_rect(x0, y0, x1, y1, 0.85)
    c.rect(x0 + 4, y0 + 3, x1 - 4, y1 - 3, BLACK)
    c.set(x0 + 1, y0 + 3, BLACK)
    edge_light(c, x0, y0, x1, y1)
    return c


tank().save(os.path.join(TEX, "entity", "tank", "tank.png"))

# --- Icons ------------------------------------------------------------------------------------------------------
TANK_ICON = [
    "................",
    "................",
    "................",
    "................",
    "........a.......",
    "......oooooo....",
    "TTTTTTTHHHHHo...",
    "....ooHHHHHHHo..",
    "..oHHHGGGGGGGHo.",
    ".oGGGGGGGGGGGGGo",
    ".ossssssssssssso",
    ".okwkkwkkwkkwkko",
    "..kkkkkkkkkkkkk.",
    "...ooooooooooo..",
    "................",
    "................",
]
sprite(TANK_ICON, {"o": 0x22261A, "T": 0x3E462A, "H": 0x707C4A, "G": 0x56603A, "s": 0x4A5232, "k": 0x2C2E31, "w": 0x6E737A,
                   "a": 0x22261A}).save(os.path.join(TEX, "item", "tank.png"))
APFSDS = [
    "................",
    "..............f.",
    ".............fF.",
    "............dDf.",
    "...........dDd..",
    "..........sSs...",
    ".........sSs....",
    "........sSs.....",
    ".......dDd......",
    "......bBBb......",
    ".....bBBBb......",
    "....bBBBb.......",
    "...bBBBb........",
    "...cBBb.........",
    "...cc...........",
    "................",
]
sprite(APFSDS, {"f": 0x5A5E64, "F": 0xA8AEB6, "d": 0x2A2C30, "D": 0x6E737A, "s": 0x3E462A, "S": 0x707C4A, "b": 0x5E4A20,
                "B": 0x8A7A52, "c": 0x5A4010}).save(os.path.join(TEX, "item", "apfsds_shell.png"))
HE = [
    "................",
    "................",
    "............nn..",
    "...........nNNn.",
    "..........yYYy..",
    ".........sSSs...",
    "........sSSs....",
    ".......sSSs.....",
    "......bBBb......",
    ".....bBBb.......",
    "....bBBb........",
    "...bBBb.........",
    "...cBb..........",
    "...cc...........",
    "................",
    "................",
]
sprite(HE, {"n": 0x5A5E64, "N": 0xA8AEB6, "y": 0xB8902E, "Y": 0xE8C25A, "s": 0x3E462A, "S": 0x707C4A, "b": 0x8A6420,
            "B": 0xD8A848, "c": 0x5A4010}).save(os.path.join(TEX, "item", "he_shell.png"))
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

for name in ("tank", "tank_shell", "apfsds_shell", "he_shell"):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})

# --- Recipes and their recipe-book advancements --------------------------------------------------------------------
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


def shaped(name, result, pattern, key, unlock, count=1, category="misc", folder="misc"):
    write_json(os.path.join(RECIPES, name + ".json"), {"type": "minecraft:crafting_shaped", "category": category,
                                                        "key": key, "pattern": pattern,
                                                        "result": {"count": count, "id": result}})
    advancement(name, folder, unlock)


# Expensive: armour plate (iron blocks), the engine (a blast furnace), running gear (minecarts), the gun's recoil
# system (pistons), and the fire control (tech scrap and a computer chip).
shaped("tank", "mymod:tank", ["BCB", "PFP", "MSM"],
       {"B": "minecraft:iron_block", "C": "mymod:computer_chip", "P": "minecraft:piston", "F": "minecraft:blast_furnace",
        "M": "minecraft:minecart", "S": "mymod:tech_scrap"}, "minecraft:iron_block")
# A diamond-tipped dart in an iron sabot over a gunpowder charge in a copper case; HE swaps the dart for TNT.
shaped("apfsds_shell", "mymod:apfsds_shell", [" D ", " I ", "GCG"],
       {"D": "minecraft:diamond", "I": "minecraft:iron_ingot", "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"},
       "mymod:tank", 2, "equipment", "combat")
shaped("he_shell", "mymod:he_shell", [" T ", " I ", "GCG"],
       {"T": "minecraft:tnt", "I": "minecraft:iron_ingot", "G": "minecraft:gunpowder", "C": "minecraft:copper_ingot"},
       "mymod:tank", 2, "equipment", "combat")


# --- Sounds -------------------------------------------------------------------------------------------------------
RATE = 22050


def write_ogg(path, samples):
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found: skipping " + path)
        return
    peak = max(1e-9, max(abs(s) for s in samples))
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "sound.wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(b"".join(struct.pack("<h", int(s / peak * 30000)) for s in samples))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "5",
                        path], check=True)


def lowpass(samples, alpha):
    out, y = [], 0.0
    for s in samples:
        y += alpha * (s - y)
        out.append(y)
    return out


def cannon():
    """The gun from inside the turret: a sharp crack into a deep, rolling boom, with a long rumbling tail."""
    rr = random.Random(5)
    n = int(RATE * 2.8)
    noise_ = [rr.uniform(-1, 1) for _ in range(n)]
    boom = lowpass(noise_, 0.035)
    rumble = lowpass(lowpass(noise_, 0.01), 0.05)
    out = []
    for i in range(n):
        t = i / RATE
        crack = noise_[i] * math.exp(-t / 0.012) * 1.2
        body = boom[i] * math.exp(-t / 0.35) * 9.0
        sub = math.sin(math.tau * (55.0 - 25.0 * min(1.0, t / 0.6)) * t) * math.exp(-t / 0.45) * 0.9
        tail = rumble[i] * math.exp(-t / 1.1) * 30.0 * min(1.0, t / 0.08)
        out.append(crack + body + sub + tail)
    return out


def clank(modes, decay, length, thunk=0.0, seed=1):
    """A struck piece of steel: a few inharmonic ringing modes over a click, and optionally a low thud."""
    rr = random.Random(seed)
    n = int(RATE * length)
    out = []
    for i in range(n):
        t = i / RATE
        s = sum(a * math.sin(math.tau * f * t) * math.exp(-t / (decay * d)) for f, a, d in modes)
        s += rr.uniform(-1, 1) * math.exp(-t / 0.004) * 0.8
        if thunk:
            s += math.sin(math.tau * 85.0 * t) * math.exp(-t / 0.06) * thunk
        out.append(s)
    return out


def breech():
    """The breech block sliding home: a scrape and a heavy steel clack."""
    scrape = [random.Random(2).uniform(-1, 1) for _ in range(int(RATE * 0.12))]
    scrape = [s * 0.25 * (i / len(scrape)) for i, s in enumerate(lowpass(scrape, 0.3))]
    hit = clank([(380, 1.0, 1.0), (1040, 0.6, 0.6), (2210, 0.35, 0.4), (3480, 0.2, 0.3)], 0.09, 0.5, 1.2, 3)
    return scrape + hit


def ram():
    """The round pushed up into the chamber: a rising slide, then the thump of it seating."""
    rr = random.Random(4)
    n = int(RATE * 0.45)
    slide = lowpass([rr.uniform(-1, 1) for _ in range(n)], 0.15)
    slide = [s * 0.6 * math.sin(math.pi * i / n) for i, s in enumerate(slide)]
    seat = clank([(240, 1.0, 1.0), (760, 0.5, 0.5), (1650, 0.25, 0.3)], 0.07, 0.35, 1.5, 6)
    return slide + seat


def casing():
    """The spent case stub dropping on the turret floor: a bright clink and two bounces."""
    n = int(RATE * 0.9)
    out = [0.0] * n
    for start, gain in ((0.0, 1.0), (0.17, 0.55), (0.29, 0.3), (0.36, 0.15)):
        hit = clank([(1830, 1.0, 1.0), (2740, 0.7, 0.8), (4310, 0.45, 0.6), (5920, 0.25, 0.4)], 0.12, 0.5, 0.3, int(start * 100))
        offset = int(start * RATE)
        for i, s in enumerate(hit):
            if offset + i < n:
                out[offset + i] += s * gain
    return out


SOUNDS = os.path.join(ASSETS, "sounds")
write_ogg(os.path.join(SOUNDS, "tank_cannon_cabin.ogg"), cannon())
write_ogg(os.path.join(SOUNDS, "tank_breech.ogg"), breech())
write_ogg(os.path.join(SOUNDS, "tank_ram.ogg"), ram())
write_ogg(os.path.join(SOUNDS, "tank_casing.ogg"), casing())
print("tank assets written")
