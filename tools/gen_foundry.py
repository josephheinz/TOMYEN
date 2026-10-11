"""Paints the Foundry: its block faces (riveted steel over a firebrick band, with a window onto the lava that
glows when lit) and its GUI background in vanilla's container style. Slot and arrow positions match
FoundryMenu and FoundryScreen."""
import os, random, struct, sys, zlib

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
rnd = random.Random(7)


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
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

    def save(self, path):
        png(path, self.w, self.h, self.px)


# --- Block faces ---------------------------------------------------------------------------------------
STEEL = rgba(0x5A606A)
STEEL_LIGHT = rgba(0x7C838E)
STEEL_DARK = rgba(0x3A3E45)
STEEL_DEEP = rgba(0x2A2D33)
RIVET = rgba(0xB4BAC3)
BRICK = rgba(0x8E3B2A)
BRICK_DARK = rgba(0x6B2A1E)
MORTAR = rgba(0x3B2A24)


def steel_plate(c, x0, y0, x1, y1):
    """A plate with a bevel: light top/left edges, dark bottom/right, slightly mottled."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            c.set(x, y, shade(STEEL, 0.94 + rnd.random() * 0.1))
    for x in range(x0, x1):
        c.set(x, y0, STEEL_LIGHT)
        c.set(x, y1 - 1, STEEL_DARK)
    for y in range(y0, y1):
        c.set(x0, y, STEEL_LIGHT)
        c.set(x1 - 1, y, STEEL_DARK)


def rivet(c, x, y):
    c.set(x, y, RIVET)
    c.set(x + 1, y + 1, STEEL_DEEP)


def firebrick(c, y0, y1):
    """A band of refractory brick, two courses high, offset like bricks are."""
    for y in range(y0, y1):
        course = (y - y0) // 3
        for x in range(16):
            joint_row = (y - y0) % 3 == 2
            joint_col = (x + (4 if course % 2 else 0)) % 8 == 7
            if joint_row or joint_col:
                c.set(x, y, MORTAR)
            else:
                c.set(x, y, shade(BRICK if (x + course) % 5 else BRICK_DARK, 0.92 + rnd.random() * 0.16))


def side():
    c = Canvas(16, 16)
    steel_plate(c, 0, 0, 16, 10)
    # A seam down the middle of the plate.
    for y in range(1, 9):
        c.set(8, y, STEEL_DARK)
        c.set(9, y, STEEL_LIGHT)
    for x, y in ((2, 2), (12, 2), (2, 6), (12, 6)):
        rivet(c, x, y)
    for x in range(16):
        c.set(x, 10, STEEL_DEEP)
    firebrick(c, 11, 16)
    return c


def top():
    c = Canvas(16, 16)
    steel_plate(c, 0, 0, 16, 16)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(c, x, y)
    # A round chimney vent with a grate, sooted around the rim.
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 3.2:
                c.set(x, y, rgba(0x14100E) if (x + y) % 2 == 0 else rgba(0x2B1E18))
            elif d < 4.3:
                c.set(x, y, STEEL_DEEP)
            elif d < 5.3:
                c.set(x, y, shade(STEEL_DARK, 0.85 + rnd.random() * 0.2))
    return c


def bottom():
    c = Canvas(16, 16)
    steel_plate(c, 0, 0, 16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, shade(c.get(x, y), 0.75))
    return c


def lava(x, y, lit):
    if not lit:
        # Dark, with a few dull embers.
        return rgba(0x5A1508) if rnd.random() < 0.12 else rgba(0x1A0D0A if rnd.random() < 0.6 else 0x24120C)
    r = rnd.random()
    heat = 0.55 + 0.45 * (1 - y / 16) + (r - 0.5) * 0.35
    if heat > 0.95:
        return rgba(0xFFF27A)
    if heat > 0.8:
        return rgba(0xFFC233)
    if heat > 0.62:
        return rgba(0xFF8A1F)
    return rgba(0xD9480F)


def front(lit):
    c = side()
    # The furnace door: a heavy frame around a window onto the lava.
    c.rect(2, 3, 14, 15, STEEL_DEEP)
    c.rect(3, 4, 13, 14, STEEL_DARK)
    for y in range(5, 13):
        for x in range(4, 12):
            c.set(x, y, lava(x, y, lit))
    # Window bars.
    for y in range(5, 13):
        c.set(6, y, STEEL_DEEP)
        c.set(9, y, STEEL_DEEP)
    for x in range(3, 13):
        c.set(x, 4, STEEL_LIGHT if x > 3 else STEEL_DARK)
    # A temperature gauge above the door, its needle up in the red when lit.
    c.rect(6, 0, 10, 3, RIVET)
    c.set(6, 0, STEEL_LIGHT)
    c.set(9, 2, STEEL_DARK)
    c.set(7, 1, rgba(0xD02020) if lit else STEEL_DEEP)
    c.set(8, 1, rgba(0xD02020) if lit else STEEL_DARK)
    return c


BLOCK = os.path.join(TEX, "block")
side().save(os.path.join(BLOCK, "foundry_side.png"))
rnd.seed(7)
top().save(os.path.join(BLOCK, "foundry_top.png"))
bottom().save(os.path.join(BLOCK, "foundry_bottom.png"))
rnd.seed(8)
front(False).save(os.path.join(BLOCK, "foundry_front.png"))
rnd.seed(8)
front(True).save(os.path.join(BLOCK, "foundry_front_on.png"))

# --- GUI ---------------------------------------------------------------------------------------------
# Vanilla's container palette.
K, W, BG, SHADOW, SLOT, SLOT_DARK = rgba(0), rgba(0xFFFFFF), rgba(0xC6C6C6), rgba(0x555555), rgba(0x8B8B8B), rgba(0x373737)
WIDTH, HEIGHT = 176, 166
# Matches FoundryMenu / FoundryScreen.
INPUT_X, INPUT_Y = 44, 17
FUEL_X, FUEL_Y = 53, 53
OUTPUT_X, OUTPUT_Y = 116, 35
ARROW_X, ARROW_Y = 80, 35
TANK_X0, TANK_Y0, TANK_X1, TANK_Y1 = 20, 16, 34, 71
# Vanilla's burn_progress arrow: rows of (first column, last column).
ARROW = [(15, 15), (15, 16), (15, 17), (15, 18), (15, 19), (15, 20), (1, 21), (1, 22), (1, 21), (15, 20), (15, 19),
         (15, 18), (15, 17), (15, 16), (15, 15)]

g = Canvas(256, 256)
# Panel: black outline with clipped corners, white bevel top-left, grey shadow bottom-right.
g.rect(1, 1, WIDTH - 1, HEIGHT - 1, BG)
for x in range(2, WIDTH - 2):
    g.set(x, 0, K)
    g.set(x, HEIGHT - 1, K)
for y in range(2, HEIGHT - 2):
    g.set(0, y, K)
    g.set(WIDTH - 1, y, K)
for x, y in ((1, 1), (WIDTH - 2, 1), (1, HEIGHT - 2), (WIDTH - 2, HEIGHT - 2)):
    g.set(x, y, K)
for i in (1, 2):
    for x in range(2, WIDTH - 3):
        g.set(x, i, W)
    for y in range(2, HEIGHT - 3):
        g.set(i, y, W)
    for x in range(3, WIDTH - 2):
        g.set(x, HEIGHT - 1 - i, SHADOW)
    for y in range(3, HEIGHT - 2):
        g.set(WIDTH - 1 - i, y, SHADOW)
g.set(3, 3, W)
g.set(WIDTH - 4, HEIGHT - 4, SHADOW)


def slot(x, y, size=18):
    """A recessed slot whose item sits at (x, y): dark top/left edge, white bottom/right."""
    pad = (size - 16) // 2
    x0, y0 = x - pad, y - pad
    g.rect(x0, y0, x0 + size, y0 + size, SLOT)
    for i in range(size - 1):
        g.set(x0 + i, y0, SLOT_DARK)
        g.set(x0, y0 + i, SLOT_DARK)
        g.set(x0 + size - 1, y0 + 1 + i, W)
        g.set(x0 + 1 + i, y0 + size - 1, W)


for i in range(2):
    slot(INPUT_X + i * 18, INPUT_Y)
slot(FUEL_X, FUEL_Y)
slot(OUTPUT_X, OUTPUT_Y, 26)
for row in range(3):
    for col in range(9):
        slot(8 + col * 18, 84 + row * 18)
for col in range(9):
    slot(8 + col * 18, 142)

for dy, (a, b) in enumerate(ARROW):
    for x in range(a, b + 1):
        g.set(ARROW_X + x, ARROW_Y + dy, SLOT)

# The lava tank: a recessed glass column with measuring ticks; the screen fills it with lava.
g.rect(TANK_X0, TANK_Y0, TANK_X1, TANK_Y1, rgba(0x2A2420))
for x in range(TANK_X0, TANK_X1):
    g.set(x, TANK_Y0, SLOT_DARK)
    g.set(x, TANK_Y1 - 1, W)
for y in range(TANK_Y0, TANK_Y1):
    g.set(TANK_X0, y, SLOT_DARK)
    g.set(TANK_X1 - 1, y, W)
for y in range(TANK_Y0 + 6, TANK_Y1 - 2, 6):
    g.set(TANK_X1 + 1, y, SHADOW)
    g.set(TANK_X1 + 2, y, SHADOW)
# A small flame between the tank and the fuel slot: lava goes in here.
FLAME = ["..#..", ".###.", "#####", "##.##", ".###."]
for dy, line in enumerate(FLAME):
    for dx, ch in enumerate(line):
        if ch == "#":
            g.set(FUEL_X - 13 + dx, FUEL_Y + 5 + dy, SHADOW)

g.save(os.path.join(TEX, "gui/container/foundry.png"))
print("ok")
