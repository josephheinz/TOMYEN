"""Paints the Skullcrane (its entity texture and the glow layer for its lenses and antigrav emitter, UVs matching
client/skullcrane/SkullcraneModel), its pack leader's amber-eyed, bronze-crested version and the Servo Drone's
brass-and-green repaint of it, the worn monocle and backpack (client/skullcrane/ServoGearRenderer), the spawn eggs,
the Servo Arm, Optic Lens and Leader Core items and the Grapple Claw, Optic Monocle, Servo Arm Backpack and Servo Drone
made from them, and writes their item models and definitions. Everything is
flat-shaded from fixed palettes, so a rerun gives the same files. On the data side it writes the Skullcrane's loot
table, the recipes that use its drops (with recipe-book advancements), and adds both to the mymod:holy_vulnerable and
minecraft:undead entity tags.

Usage: python3 tools/gen_skullcrane.py src/main/resources/assets/mymod"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
MC_DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "minecraft"))


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(path):
    """8-bit RGBA, non-interlaced only."""
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride, out, prev = w * 4, [], bytearray(w * 4)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out += [tuple(line[x * 4:x * 4 + 4]) for x in range(w)]
        prev = line
    return w, h, out


CLEAR = (0, 0, 0, 0)


def clamp(v): return max(0, min(255, int(round(v))))




def col(c, shade=1.0, a=255):
    return (clamp(c[0] * shade), clamp(c[1] * shade), clamp(c[2] * shade), a)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, u, v, w, h, f):
        """f(x, y) -> colour, with x, y relative to the rectangle; None leaves it as it is."""
        for y in range(h):
            for x in range(w):
                c = f(x, y)
                if c is not None:
                    self.set(u + x, v + y, c)

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


# ---------------------------------------------------------------------------------------------- palette
# Item sprites below use these.
BONE = (226, 216, 190)
BONE_LIGHT = (240, 234, 214)
BONE_SHADE = (190, 178, 148)
BONE_STAIN = (172, 156, 120)
SOCKET = (22, 16, 14)
TOOTH = (236, 228, 200)
METAL = (112, 118, 126)
METAL_LIGHT = (160, 168, 176)
METAL_DARK = (62, 66, 74)
BOLT = (196, 200, 204)
BRASS = (186, 146, 70)
BRASS_DARK = (126, 94, 40)
LENS = (214, 26, 30)
LENS_HOT = (255, 140, 110)

# The entity textures are flat-shaded from a handful of tones each, light to dark: no noise, no dithering.
BONE_RAMP = [(242, 236, 216), (226, 216, 190), (204, 191, 160), (174, 159, 128), (128, 111, 88)]
MOUTH = (74, 52, 44)
STEEL_RAMP = [(150, 157, 168), (98, 104, 115), (64, 68, 77), (42, 44, 50)]
BRASS_RAMP = [(236, 196, 112), (192, 146, 66), (136, 97, 42), (92, 64, 28)]
RED_LENS = {"lens": (220, 32, 36), "hot": (255, 168, 140), "rim": (120, 18, 22)}
GREEN_LENS = {"lens": (56, 214, 98), "hot": (206, 255, 218), "rim": (24, 104, 48)}
CYAN_EMITTER = {"lens": (96, 214, 250), "hot": (224, 250, 255), "rim": (40, 90, 112)}
GREEN_EMITTER = {"lens": (110, 236, 150), "hot": (226, 255, 232), "rim": (36, 96, 56)}
AMBER_LENS = {"lens": (255, 168, 30), "hot": (255, 240, 180), "rim": (140, 76, 10)}
BRONZE_RAMP = [(214, 152, 84), (172, 110, 52), (122, 74, 34), (82, 48, 22)]


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out, fractional
    sizes and all. 'top' and 'bottom' are as seen in the world (the model is upside down); in both, the front edge is
    the last row. In 'right' the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, u, v, w, h, d, painters):
    """painters: face name -> f(x, y, fw, fh) -> colour, with x, y the texel within the face and fw, fh its size
    rounded up; missing faces stay clear. A texel goes to the face its middle falls in, so on a fractional cube a texel
    straddling two faces shows on both."""
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name)
        if f is None:
            continue
        for py in range(math.floor(fv), math.ceil(fv + fh)):
            for px in range(math.floor(fu), math.ceil(fu + fw)):
                x, y = px + 0.5 - fu, py + 0.5 - fv
                if 0 <= x < fw and 0 <= y < fh:
                    canvas.set(px, py, f(int(x), int(y), math.ceil(fw), math.ceil(fh)))


def tone(ramp, i):
    return ramp[max(0, min(len(ramp) - 1, i))] + (255,)


def flat(c):
    return lambda x, y, w, h: c + (255,)


def skull_textures(bone, metal, lens, emitter, trim=None, arms=1.0):
    """The skullcrane's texture and glow layer (UVs as in client/skullcrane/SkullcraneModel), in the given ramps; with
    a trim ramp, a leader's, with its crest and horn plates and its arms that much longer."""
    tex, glow = Canvas(64, 32), Canvas(64, 32)

    # Cranium, 10 x 8 x 10 at (0, 0). Lit from above: a pale crown, shading down the sides to a dark underside.
    # One crack runs from the crown down over the left brow.
    crack = {("top", 6, 6), ("top", 6, 7), ("top", 7, 8), ("top", 7, 9), ("front", 7, 0), ("front", 8, 1)}

    def cracked(name, f):
        return lambda x, y, w, h: tone(bone, 4) if (name, x, y) in crack else f(x, y, w, h)

    def crown(x, y, w, h):
        edge = min(x, y, w - 1 - x, h - 1 - y)
        return tone(bone, 0 if edge >= 1 else 1)

    def side(x, y, w, h):
        return tone(bone, 1 if y < 5 else 2 if y < 7 else 3)

    def back(x, y, w, h):
        return tone(bone, 1 if y < 3 else 2 if y < 6 else 3)

    def underside(x, y, w, h):
        if 1 <= x <= 8 and y >= 5:  # the roof of the mouth, over the jaw
            return MOUTH + (255,)
        return tone(bone, 3)

    # The face, drawn out: 0-4 the bone ramp, D the dark of the sockets, nose and mouth. The lenses cover the sockets'
    # middles (columns 2-3 and 6-7, rows 4-5); the bottom row is the upper teeth.
    FACE = ["0000000000",
            "1111111111",
            "1122112211",
            "12DD22DD21",
            "1DDD33DDD1",
            "1DDD33DDD1",
            "2233DD3322",
            "3003003003"]

    def face(x, y, w, h):
        c = FACE[y][x]
        return MOUTH + (255,) if c == "D" else tone(bone, int(c))

    paint(tex, 0, 0, 10, 8, 10, {"top": cracked("top", crown), "front": cracked("front", face), "right": side, "left": side,
                                 "back": back, "bottom": underside})

    # Jaw, 8 x 2 x 5 at (0, 18): lower teeth along the front, the inside of the mouth on top.
    def chin(x, y, w, h):
        if y == 0:
            return tone(bone, 3) if x in (0, 2, 5, 7) else tone(bone, 0)
        return tone(bone, 2)

    paint(tex, 0, 18, 8, 2, 5, {"top": flat(MOUTH), "front": chin, "right": lambda x, y, w, h: tone(bone, 2 + y),
                                "left": lambda x, y, w, h: tone(bone, 2 + y), "back": flat(bone[3]), "bottom": flat(bone[3])})

    # Lenses, 2 x 2 x 1 at (40, 0): solid colour with one bright glint, a darker rim round the sides.
    def lens_face(x, y, w, h):
        return lens["hot" if (x, y) == (0, 0) else "lens"] + (255,)

    rim = flat(lens["rim"])
    paint(tex, 40, 0, 2, 2, 1, {"front": lens_face, "right": rim, "left": rim, "top": rim, "bottom": rim, "back": rim})
    paint(glow, 40, 0, 2, 2, 1, {"front": lens_face})

    # Emitter, 3 x 1 x 3 at (40, 4): a dark housing with a glowing plate underneath.
    def plate(x, y, w, h):
        return emitter["hot" if (x, y) == (1, 1) else "lens"] + (255,)

    housing = flat(metal[2])
    paint(tex, 40, 4, 3, 1, 3, {"top": housing, "right": housing, "left": housing, "front": housing, "back": housing,
                                "bottom": plate})
    paint(glow, 40, 4, 3, 1, 3, {"bottom": plate})

    # Arm segments at (40, 10), (48, 10) and (56, 10), and the pincer prong at (60, 10): plain metal, a highlight
    # down one edge, a darker band at the tip end.
    def strut(lit):
        return lambda x, y, w, h: tone(metal, 2 if y == h - 1 else 0 if lit and x == 0 and w > 1 else 1)

    def prong(x, y, w, h):
        return tone(metal, 0 if y == h - 1 else 1)

    for u, (t, length) in zip((40, 48, 56), ((1.5, 4.5), (1.25, 4.0), (1.0, 3.5))):
        paint(tex, u, 10, t, length * arms, t, {"front": strut(True), "right": strut(False), "left": strut(False),
                                                "back": strut(False), "top": flat(metal[2]), "bottom": flat(metal[2])})
    paint(tex, 60, 10, 0.5, 2.5, 0.5, {name: prong for name in ("front", "right", "left", "back", "top", "bottom")})
    if trim:
        # The crest, 2 x 2 x 8 at (26, 18), and a horn plate, 1 x 4 x 3 at (46, 18): bronze, lit along the top.
        def plate(x, y, w, h):
            return tone(trim, 0 if y == 0 else 1 if y < h - 1 else 2)

        sides = {name: plate for name in ("front", "right", "left", "back")}
        paint(tex, 26, 18, 2, 2, 8, dict(sides, top=flat(trim[0]), bottom=flat(trim[3])))
        paint(tex, 46, 18, 1, 4, 3, dict(sides, top=flat(trim[0]), bottom=flat(trim[3])))
    return tex, glow


tex, glow = skull_textures(BONE_RAMP, STEEL_RAMP, RED_LENS, CYAN_EMITTER)
tex.save("entity/skullcrane/skullcrane.png")
glow.save("entity/skullcrane/skullcrane_glow.png")
# The Servo Drone: the same skull with brass arms and green lenses and emitter.
tex, glow = skull_textures(BONE_RAMP, BRASS_RAMP, GREEN_LENS, GREEN_EMITTER)
tex.save("entity/skullcrane/servo_drone.png")
glow.save("entity/skullcrane/servo_drone_glow.png")
# The pack leader: amber lenses and emitter, a bronze crest and horns, longer arms.
tex, glow = skull_textures(BONE_RAMP, STEEL_RAMP, AMBER_LENS, AMBER_LENS, trim=BRONZE_RAMP, arms=1.3)
tex.save("entity/skullcrane/skullcrane_leader.png")
glow.save("entity/skullcrane/skullcrane_leader_glow.png")

# Worn gear (client/skullcrane/ServoGearRenderer): the monocle's band, rim and lens; the backpack's spine pack, power
# cell, arm segments and claw prongs.
gear = Canvas(64, 32)
gear_glow = Canvas(64, 32)
LEATHER = (96, 64, 42)
CELL = {"lens": (96, 214, 250), "hot": (224, 250, 255)}
ALL = ("top", "bottom", "front", "back", "right", "left")


def every(f):
    return {name: f for name in ALL}


def lens_glint(x, y, w, h):
    return (RED_LENS["hot"] if (x, y) == (0, 0) else RED_LENS["lens"]) + (255,)


paint(gear, 0, 0, 8.6, 0.6, 8.6, every(flat(LEATHER)))
paint(gear, 36, 0, 3, 0.5, 0.5, every(flat(STEEL_RAMP[0])))
paint(gear, 44, 0, 0.5, 2.5, 0.5, every(flat(STEEL_RAMP[0])))
for canvas in (gear, gear_glow):
    paint(canvas, 48, 0, 2, 2.5, 0.3, every(lens_glint))


# Spine pack, 4 x 8 x 2 at (0, 10): plain steel, a highlight along the top edge and a darker border.
def casing(x, y, w, h):
    if y == 0:
        return tone(STEEL_RAMP, 0)
    return tone(STEEL_RAMP, 2 if x in (0, w - 1) or y == h - 1 else 1)


paint(gear, 0, 10, 4, 8, 2, every(casing))
for canvas in (gear, gear_glow):
    paint(canvas, 12, 10, 2, 3, 0.5, every(lambda x, y, w, h: CELL["hot" if (x, y) == (0, 1) else "lens"] + (255,)))


# Arm segments (each running 0.25 back past its joint) at (18, 10), (24, 10), (30, 10) and (36, 10), and a claw prong
# at (40, 10): a highlight down one edge, a darker band at the far end.
def gear_strut(lit):
    return lambda x, y, w, h: tone(STEEL_RAMP, 2 if y == 0 else 0 if lit and x == 0 and w > 1 else 1)


for u, t, length in ((18, 1.5, 5.5), (24, 1.25, 5.0), (30, 1.25, 4.5), (36, 1.0, 4.0)):
    paint(gear, u, 10, t, length + 0.25, t, dict(every(gear_strut(False)), front=gear_strut(True)))
paint(gear, 40, 10, 0.5, 2.0, 0.5, every(lambda x, y, w, h: tone(STEEL_RAMP, 0 if y == 0 else 1)))
gear.save("entity/skullcrane/servo_gear.png")
gear_glow.save("entity/skullcrane/servo_gear_glow.png")

# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                c.set(x, y, palette[ch])
    return c


# A servo arm: three struts with joints, a brass piston, and a claw at the top right.
SERVO_ARM = ["................",
             "...........k.k..",
             "..........k...k.",
             "...........jjj..",
             "..........jJJj..",
             ".........ssj....",
             "........ss......",
             ".......jjp......",
             "......jJJjp.....",
             ".....ss..p......",
             "....ss..........",
             "...jj...........",
             "..jJJj..........",
             "..jjj...........",
             ".bb.............",
             "................"]
sprite(SERVO_ARM, {"s": col(METAL), "j": col(METAL_DARK), "J": col(BOLT), "p": col(BRASS), "k": col(METAL_LIGHT),
                   "b": col(BRASS_DARK)}).save("item/servo_arm.png")

# An optic lens: a red disc in a steel ring, with a glint.
lens_item = Canvas(16, 16)
for y in range(16):
    for x in range(16):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        if r < 4.6:
            light = (8 - x) + (8 - y)
            lens_item.set(x, y, col(LENS_HOT if r < 1.2 else LENS, 1.0 + 0.04 * light))
        elif r < 6.2:
            lens_item.set(x, y, col(METAL_LIGHT if (x + y) % 5 == 0 else METAL))
        elif r < 7.0:
            lens_item.set(x, y, col(METAL_DARK))
for x, y in ((6, 5), (5, 6), (6, 6)):
    lens_item.set(x, y, col((255, 230, 220)))
lens_item.save("item/optic_lens.png")

# Spawn eggs: the shopping cart egg's outline, repainted bone with lens spots and a band: red and steel for the
# skullcrane, amber and bronze for its leader.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
SPOTS = [(6, 6), (10, 6)]


def spawn_egg(lens, hot, band, name):
    egg = Canvas(w, h)
    for i in range(w * h):
        if not mask[i]:
            continue
        x, y = i % w, i // w
        outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if outline:
            egg.px[i] = col((70, 64, 56))
        elif any(abs(x - sx) + abs(y - sy) <= 1 for sx, sy in SPOTS):
            egg.px[i] = col(lens if (x, y) not in SPOTS else hot)
        elif y == round(cy) + 2:
            egg.px[i] = col(band)
        else:
            light = (cx - x) + (cy - y)
            egg.px[i] = col(BONE_LIGHT if light > 5 else BONE if light > 1 else BONE_SHADE if light > -4 else BONE_STAIN)
    egg.save("item/%s.png" % name)


spawn_egg(LENS, LENS_HOT, METAL, "skullcrane_spawn_egg")
spawn_egg(AMBER_LENS["lens"], AMBER_LENS["hot"], BRONZE_RAMP[1], "skullcrane_leader_spawn_egg")

# Leader Core: an amber core in a bronze cage, with a glint.
LEADER_CORE = ["................",
               "......kkkk......",
               "....kkBBBBkk....",
               "...kBAAAAAABk...",
               "...kAHHAAAAAk...",
               "..kBAHWAAAAABk..",
               "..kBAAAAAAAABk..",
               "..kBAAAAAAAABk..",
               "..kBAAAAAAADBk..",
               "..kBAAAAAADDBk..",
               "...kAAAADDDDk...",
               "...kBADDDDDBk...",
               "....kkBBBBkk....",
               "......kkkk......",
               "................",
               "................"]
sprite(LEADER_CORE, {"k": col(BRONZE_RAMP[3]), "B": col(BRONZE_RAMP[1]), "A": col(AMBER_LENS["lens"]),
                     "H": col(AMBER_LENS["hot"]), "W": col((255, 252, 236)), "D": col((206, 116, 20))}).save("item/leader_core.png")

# Grapple Claw: a claw on a coil of cable, the cable trailing off to a grip.
GRAPPLE = ["................",
           "..........k..k..",
           ".........k.kk.k.",
           "..........jJJj..",
           "..........jjjj..",
           "...........cc...",
           "..........c..c..",
           ".........c....c.",
           "........c.cc.c..",
           ".......c.c..c...",
           "......c..c.c....",
           ".....c....c.....",
           "....hh..........",
           "...hHh..........",
           "..hHh...........",
           "..hh............"]
sprite(GRAPPLE, {"k": col(METAL_LIGHT), "j": col(METAL_DARK), "J": col(BOLT), "c": col((60, 60, 66)), "h": col(BRASS_DARK),
                 "H": col(BRASS)}).save("item/grapple_claw.png")

# Optic Monocle: a red lens in a steel rim, with a chain hanging off it.
monocle = Canvas(16, 16)
for y in range(16):
    for x in range(16):
        r = math.hypot(x + 0.5 - 7, y + 0.5 - 6)
        if r < 3.4:
            monocle.set(x, y, col(LENS_HOT if r < 1.0 else LENS, 1.0 + 0.05 * ((7 - x) + (6 - y))))
        elif r < 4.8:
            monocle.set(x, y, col(METAL_LIGHT if (x + y) % 4 == 0 else METAL))
for x, y in ((6, 4), (5, 5)):
    monocle.set(x, y, col((255, 230, 220)))
for i, (x, y) in enumerate(((10, 10), (11, 11), (11, 12), (12, 13), (12, 14), (13, 15))):
    monocle.set(x, y, col(BRASS if i % 2 else BRASS_DARK))
monocle.save("item/optic_monocle.png")

# Servo Arm Backpack: a steel pack with a glowing cell and two arms curling up off its top.
BACKPACK = ["................",
            "..k..........k..",
            ".k.k........k.k.",
            "..j..........j..",
            "..a..........a..",
            "..a..........a..",
            "..J..........J..",
            "...a........a...",
            "...MMMMMMMMMM...",
            "...MbmmmmmmbM...",
            "...MmmmCCmmmM...",
            "...MmmmCCmmmM...",
            "...MmmmCCmmmM...",
            "...MbmmmmmmbM...",
            "...MMMMMMMMMM...",
            "................"]
sprite(BACKPACK, {"k": col(METAL_LIGHT), "j": col(METAL_DARK), "J": col(BOLT), "a": col(METAL), "M": col(METAL_DARK), "m": col(METAL),
                  "b": col(BOLT), "C": col((110, 220, 255))}).save("item/servo_arm_backpack.png")

# Servo Drone: a little brass-plated skull with green lenses and arms tucked under.
DRONE = ["................",
         "................",
         ".....BBBBBB.....",
         "....BBBBBBBBp...",
         "....BBBBBBBBp...",
         "....BggBBggBp...",
         "....BGgBBGgB....",
         "....BBBssBBB....",
         ".....tttttt.....",
         ".....jjjjjj.....",
         "......eeee......",
         "....a.a..a.a....",
         "....a.a..a.a....",
         "...k.k....k.k...",
         "................",
         "................"]
sprite(DRONE, {"B": col(BONE), "p": col((200, 160, 80)), "g": col((60, 200, 90)), "G": col((180, 255, 190)), "s": col(SOCKET),
               "t": col(TOOTH), "j": col(BONE_SHADE), "e": col((100, 230, 140)), "a": col((190, 150, 70)),
               "k": col(METAL_LIGHT)}).save("item/servo_drone.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


for name in ("skullcrane_spawn_egg", "skullcrane_leader_spawn_egg", "servo_arm", "optic_lens", "leader_core", "grapple_claw",
             "optic_monocle", "servo_arm_backpack", "servo_drone"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(DATA, "loot_table/entities/skullcrane.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:servo_arm", 0, 2), drop("mymod:optic_lens", 0, 1), drop("minecraft:bone", 0, 2),
               drop("mymod:tech_scrap", 0, 1, 0.5)]),
    ("random_sequence", "mymod:entities/skullcrane")]))

# The leader: more of everything, and now and then (killed by a player) its Leader Core.
write(os.path.join(DATA, "loot_table/entities/skullcrane_leader.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:servo_arm", 2, 4), drop("mymod:optic_lens", 1, 3), drop("minecraft:bone", 1, 4),
               drop("mymod:tech_scrap", 1, 2, 1.0),
               OrderedDict([
                   ("condition", {"type": "minecraft:all_of", "terms": [
                       {"type": "minecraft:killed_by_player"},
                       OrderedDict([("type", "minecraft:random_chance_with_enchanted_bonus"),
                                    ("enchanted_chance", {"type": "minecraft:linear", "base": 0.45, "per_level_above_first": 0.1}),
                                    ("enchantment", "minecraft:looting"), ("unenchanted_chance", 0.35)])]}),
                   ("entries", [{"type": "minecraft:item", "name": "mymod:leader_core"}]),
                   ("rolls", 1)])]),
    ("random_sequence", "mymod:entities/skullcrane_leader")]))


def advancement(name, ingredient, category="misc"):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shapeless(name, ingredients, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, ingredients[0], category)


def shaped(name, pattern, key, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, next(iter(key.values())), category)


shapeless("tech_scrap_from_servo_arm", ["mymod:servo_arm"], "mymod:tech_scrap", 2)
shapeless("computer_chip_from_optic_lens", ["mymod:optic_lens", "mymod:tech_scrap"], "mymod:computer_chip", 1)
shaped("spyglass_from_optic_lens", ["L", "C", "C"], OrderedDict([("L", "mymod:optic_lens"), ("C", "minecraft:copper_ingot")]),
       "minecraft:spyglass", 1, "equipment")

shaped("grapple_claw", ["A", "L", "T"], OrderedDict([("A", "mymod:servo_arm"), ("L", "minecraft:lead"), ("T", "minecraft:tripwire_hook")]),
       "mymod:grapple_claw", 1, "equipment")
shaped("optic_monocle", [" S ", "GLG"], OrderedDict([("L", "mymod:optic_lens"), ("S", "minecraft:string"), ("G", "minecraft:gold_nugget")]),
       "mymod:optic_monocle", 1, "equipment")
shaped("servo_arm_backpack", ["A A", "ILI", " C "], OrderedDict([("A", "mymod:servo_arm"), ("I", "minecraft:iron_ingot"),
       ("L", "minecraft:leather"), ("C", "mymod:computer_chip")]), "mymod:servo_arm_backpack", 1, "equipment")
shaped("servo_drone", [" L ", "BCB", "A A"], OrderedDict([("L", "mymod:optic_lens"), ("B", "minecraft:bone"), ("C", "mymod:computer_chip"),
       ("A", "mymod:servo_arm")]), "mymod:servo_drone", 1, "misc")


def tag_add(path, entry):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


# A Leader Core overclocks a Servo Arm Backpack: it fetches items from further off (see ServoGear).
OVERCLOCKED_LORE = [OrderedDict([("translate", key), ("color", color), ("italic", False)]) for key, color in (
    ("item.mymod.servo_arm_backpack.pickup", "gray"), ("item.mymod.servo_arm_backpack.overclocked", "gold"),
    ("item.mymod.servo_gear.socket", "gray"))]
write(os.path.join(DATA, "recipe", "servo_arm_backpack_overclocked.json"), OrderedDict([
    ("type", "minecraft:crafting_shapeless"), ("category", "equipment"),
    ("ingredients", ["mymod:servo_arm_backpack", "mymod:leader_core"]),
    ("result", OrderedDict([("count", 1), ("id", "mymod:servo_arm_backpack"), ("components", OrderedDict([
        ("minecraft:custom_data", {"overclocked": True}), ("minecraft:enchantment_glint_override", True),
        ("minecraft:lore", OVERCLOCKED_LORE)]))]))]))
advancement("servo_arm_backpack_overclocked", "mymod:leader_core", "equipment")

for entity in ("mymod:skullcrane", "mymod:skullcrane_leader"):
    tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), entity)
    tag_add(os.path.join(MC_DATA, "tags/entity_type/undead.json"), entity)
print("done")
