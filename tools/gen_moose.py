"""Builds the Moose: its model (the cube layout in PARTS, written into client/moose/MooseModel.java between the GENERATED
markers), its three coats (bull, cow and calf; one UV layout, so the bull's antlers and the saddle are painted on all
three), the item sprites (Moose Antler, Antler Handle, Antler Hook, Raw and Cooked Venison, the spawn egg) with their
item models and definitions. On the data side it writes the loot tables (the moose, and the extra roll a bull with a full
rack of antlers gets), the recipes (venison cooking, antler handles and hooks, and the antler-handled stone, copper, iron
and bloom steel tools), the tags (moose food, saddle, powder snow, freeze immunity, floating when ridden), and merges
the lang entries and the moose's sounds (vanilla cow, camel, ravager, goat and polar bear sounds, re-pitched) into
en_us.json and sounds.json.

The coats are painted by where each texel sits on the moose at rest, not cube by cube: one smooth ramp from the pale,
sun-bleached hump down to the dark belly, fading into the grey-tan stockings below the knees, so the shading runs
unbroken across neighbouring cubes. No noise or dithering; a rerun gives the same files.

The model is checked for z-fighting (tools/coplanar.py) at rest and through its walk, gallop, browse, pawing wind-up,
rear-and-stomp and look poses, saddled and not. Its extents and cube counts are printed.

With --preview DIR it also renders, into DIR, preview_moose.png: a bull from the front, three-quarters and side; a cow
and a calf; mid-stride; browsing; pawing before a charge; mid-charge; a cow rearing and slamming down; a saddled bull;
the head up close; the antlers from above; and the item sprites. The render uses the same cubes, pivots and UV layout as
the Java model, and a Python twin of MooseModel.pose.

Usage: python3 tools/gen_moose.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
VANILLA_DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "minecraft"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


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


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size). Model space is the game's: y
# down, the front toward -z, the ground at y 24, one model pixel a sixteenth of a block (the renderer draws it at full
# size). Cube sizes are whole pixels; positions can be fractional. Rotations here are the rest pose; MooseModel.pose
# adds to them.
#
# The legs hang from the root (so the body can rear up about its hips without dragging the hind legs along); the front
# legs' pivots are moved round with the body in the pose, so they stay at its shoulders. The bull's antlers and the
# saddle are on every moose's model, shown or hidden by the pose.
S = 1                   # texels per model pixel: vanilla's density, as the moose is drawn at full size
TEX_W, TEX_H = 128, 128
GROUND = 24.0

BODY_PIVOT = (0.0, 2.0, 9.0)          # the hips: the body rears up about them
FRONT_PIVOT = (3.75, -2.5, -11.75)      # (x mirrored for the right leg)
HIND_PIVOT = (3.5, -2.0, 10.5)
HIND_TILT = 0.22                       # the thigh angles back, the shin back upright: the hock sits behind
NECK_PIVOT = (0.0, -6.0, -14.0)
NECK_REST = -0.1
HEAD_REST = 0.82
CROWN_REST = -(NECK_REST + HEAD_REST)  # cancels the head's tilt, so the ears and antlers are laid out level with the world
BELL_REST = -(NECK_REST + HEAD_REST)
EAR_REST = (-0.6, 0.45, -0.9)


def body_cube(key, frm, size):
    """A cube on the body, given where it sits in model space at rest."""
    return (key, tuple(frm[i] - BODY_PIVOT[i] for i in range(3)), size)


def mirror(part, name, parent):
    """The left-hand twin of a right-hand part: x flipped, and the turns about y and z reversed."""
    _, _, (px, py, pz), (rx, ry, rz), cubes = part
    return (name, parent, (-px, py, pz), (rx, -ry, -rz), [(k, (-(f[0] + s[0]), f[1], f[2]), s) for k, f, s in cubes])


def hind_shin_drop():
    """Where the hind shin's pivot ends up below the hip, so the hind hooves come to rest exactly on the ground."""
    y, z = 13.0, 0.5
    return HIND_PIVOT[1] + y * math.cos(HIND_TILT) - z * math.sin(HIND_TILT)


def build():
    body = ("body", None, BODY_PIVOT, (0.0, 0.0, 0.0), [
        # The barrel, the deep chest and its low brisket, the humped withers with a dark crest of hair along the top,
        # a narrower loin over the back, the rump (lower than the shoulders) and the croup on top of it, a sagging
        # belly, and a muscle plate over each shoulder and haunch.
        body_cube("barrel", (-7.0, -5.0, -10.0), (14, 12, 20)),
        body_cube("chest", (-6.5, -6.5, -15.5), (13, 14, 6)),
        body_cube("brisket", (-5.0, 5.5, -16.5), (10, 4, 5)),
        body_cube("hump", (-6.0, -8.5, -14.0), (12, 5, 12)),
        body_cube("withers", (-4.5, -11.5, -12.5), (9, 4, 8)),
        body_cube("crest", (-1.5, -12.5, -13.5), (3, 2, 11)),
        body_cube("loin", (-5.5, -6.5, -4.0), (11, 2, 12)),
        body_cube("rump", (-6.5, -5.5, 7.5), (13, 11, 7)),
        body_cube("croup", (-5.0, -7.0, 8.5), (10, 2, 5)),
        body_cube("belly", (-4.0, 6.5, -8.0), (8, 2, 15)),
        body_cube("shoulder", (-7.5, -3.5, -14.0), (1, 10, 7)),
        body_cube("shoulder", (6.5, -3.5, -14.0), (1, 10, 7)),
        body_cube("haunch", (-7.5, -4.0, 8.0), (1, 9, 6)),
        body_cube("haunch", (6.5, -4.0, 8.0), (1, 9, 6))])
    rel = lambda p: tuple(p[i] - BODY_PIVOT[i] for i in range(3))
    tail = ("tail", "body", rel((0.0, -4.5, 14.5)), (0.35, 0.0, 0.0), [("tail", (-1.5, 0.0, -0.5), (3, 5, 2))])
    neck = ("neck", "body", rel(NECK_PIVOT), (NECK_REST, 0.0, 0.0), [
        ("neck", (-4.0, -4.0, -9.0), (8, 9, 10)),
        ("throat", (-3.0, 3.0, -8.0), (6, 4, 7)),
        ("mane", (-1.0, -6.5, -9.5), (2, 3, 10))])
    # The head hangs from the end of the short neck: a long, heavy muzzle ending in the drooping, overhanging nose, the
    # lower lip on its own (to chew), big ears out to the sides, and the bell (the dewlap) under the throat.
    head = ("head", "neck", (0.0, 0.0, -8.5), (HEAD_REST, 0.0, 0.0), [
        ("skull", (-3.5, -4.0, -6.0), (7, 7, 7)),
        ("muzzle", (-2.5, -3.0, -14.0), (5, 6, 8)),
        ("bridge", (-2.0, -3.75, -13.0), (4, 1, 6)),
        ("nose", (-3.5, -1.5, -17.5), (7, 7, 4))])
    jaw = ("jaw", "head", (0.0, 3.0, -5.0), (0.0, 0.0, 0.0), [("jaw", (-2.5, 0.0, -11.0), (5, 2, 10))])
    # The crown of the head (no cubes of its own): the ears and antlers hang from it, laid out level with the world.
    crown = ("crown", "head", (0.0, -4.0, -2.5), (CROWN_REST, 0.0, 0.0), [])
    ear_r = ("right_ear", "crown", (-3.25, -1.5, 2.5), EAR_REST, [("ear", (-1.5, -6.0, -0.5), (3, 6, 1))])
    bell = ("bell", "head", (0.0, 4.0, -4.5), (BELL_REST, 0.0, 0.0), [("bell", (-1.0, 0.0, -1.5), (2, 7, 3))])
    bell_tip = ("bell_tip", "bell", (0.0, 7.0, 0.0), (0.0, 0.0, 0.0), [("bell_tip", (-0.5, 0.0, -1.0), (1, 4, 2))])
    # The antlers (a bull's): a knobbed burr on the skull, a short beam rising outward, and the broad palm on its end,
    # tilted up toward its outer edge, with a forward brow lobe and a rear lobe, a row of tines along the rim pointing up
    # and out, one more off the rear lobe and a brow tine pointing ahead.
    antler_r = ("right_antler", "crown", (-2.5, 0.0, 0.0), (0.0, 0.0, 0.0), [
        ("burr", (-1.5, -1.5, -1.0), (2, 2, 2))])
    beam_r = ("right_beam", "right_antler", (-1.0, -0.5, 0.0), (0.0, 0.0, 0.25), [("beam", (-6.0, -1.0, -0.75), (6, 2, 2))])
    palm_r = ("right_palm", "right_beam", (-5.5, -0.25, 0.25), (0.55, 0.15, 0.45), [
        ("palm", (-9.0, -1.0, -4.0), (9, 2, 9)),
        ("lobe_front", (-5.5, -0.5, -7.5), (5, 1, 4)),
        ("lobe_rear", (-7.5, -0.5, 4.5), (6, 1, 3)),
        ("tine_short", (-8.75, -4.5, -3.25), (1, 4, 1)),
        ("tine_long", (-8.75, -5.5, -0.75), (1, 5, 1)),
        ("tine_long", (-8.75, -5.5, 1.75), (1, 5, 1)),
        ("tine_short", (-8.75, -4.5, 3.75), (1, 4, 1)),
        ("tine_short", (-6.75, -4.0, 6.25), (1, 4, 1)),
        ("brow_tine", (-3.25, -0.75, -10.25), (1, 1, 3))])
    # The front legs: a muscled forearm in two steps, and a long, thin cannon on its own (it folds at the knee) ending
    # in a big cloven hoof. The hind: a heavy thigh angled back, the hock jutting behind, a longer cannon.
    front_r = ("right_front_leg", None, (-FRONT_PIVOT[0], FRONT_PIVOT[1], FRONT_PIVOT[2]), (0.0, 0.0, 0.0), [
        ("forearm_upper", (-2.5, 0.0, -2.5), (5, 9, 5)),
        ("forearm", (-2.0, 8.25, -2.0), (4, 7, 4))])
    front_shin_r = ("right_front_shin", "right_front_leg", (0.0, 14.0, 0.0), (0.0, 0.0, 0.0), [
        ("front_cannon", (-1.5, -0.5, -1.5), (3, 10, 3)),
        ("hoof", (-2.0, 9.5, -2.75), (4, 3, 4))])
    hind_r = ("right_hind_leg", None, (-HIND_PIVOT[0], HIND_PIVOT[1], HIND_PIVOT[2]), (HIND_TILT, 0.0, 0.0), [
        ("thigh", (-2.5, 0.0, -3.0), (5, 10, 6)),
        ("gaskin", (-2.0, 8.0, -2.0), (4, 6, 4))])
    shin_y = hind_shin_drop()
    hoof_y = GROUND - shin_y - 3.0
    hind_shin_r = ("right_hind_shin", "right_hind_leg", (0.0, 13.0, 0.5), (-HIND_TILT, 0.0, 0.0), [
        ("hind_cannon", (-1.5, -0.5, -1.5), (3, 11, 3)),
        ("hock", (-1.0, -1.5, 0.5), (2, 3, 3)),
        ("hoof", (-2.0, round(hoof_y, 4), -2.75), (4, 3, 4))])
    # The saddle, shown once one's on: a red wool blanket over the back with flaps down the sides, the leather seat
    # with a raised pommel and cantle, girth straps round the barrel, and iron stirrups.
    saddle = ("saddle", "body", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [
        body_cube("blanket", (-7.5, -7.0, -3.5), (15, 1, 10)),
        body_cube("flap", (-8.0, -6.5, -3.0), (1, 7, 9)),
        body_cube("flap", (7.0, -6.5, -3.0), (1, 7, 9)),
        body_cube("seat", (-4.0, -8.5, -2.0), (8, 2, 8)),
        body_cube("pommel", (-2.0, -10.5, -2.5), (4, 2, 2)),
        body_cube("cantle", (-3.5, -10.0, 5.0), (7, 2, 2)),
        body_cube("girth", (-8.25, -6.0, 2.0), (1, 15, 2)),
        body_cube("girth", (7.25, -6.0, 2.0), (1, 15, 2)),
        body_cube("girth_band", (-8.5, 8.75, 2.25), (17, 1, 2)),
        body_cube("stirrup", (-8.5, 0.0, -1.0), (1, 3, 2)),
        body_cube("stirrup", (7.5, 0.0, -1.0), (1, 3, 2))])
    return [
        body, tail, neck, head, jaw, crown, ear_r, mirror(ear_r, "left_ear", "crown"), bell, bell_tip,
        antler_r, beam_r, palm_r,
        mirror(antler_r, "left_antler", "crown"), mirror(beam_r, "left_beam", "left_antler"), mirror(palm_r, "left_palm", "left_beam"),
        front_r, front_shin_r, mirror(front_r, "left_front_leg", None), mirror(front_shin_r, "left_front_shin", "left_front_leg"),
        hind_r, hind_shin_r, mirror(hind_r, "left_hind_leg", None), mirror(hind_shin_r, "left_hind_shin", "left_hind_leg"),
        saddle,
    ]


PARTS = build()
ANTLER_PARTS = {"right_antler", "right_beam", "right_palm", "left_antler", "left_beam", "left_palm"}
SADDLE_PARTS = {"saddle"}


def tree(model):
    t = {}
    for name, parent, _, _, _ in model:
        t.setdefault(parent or "root", []).append(name)
    return t


def pack():
    """Texture offsets (in model pixels) for each cube key, skyline-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS:
        for key, _, size in cubes:
            assert sizes.setdefault(key, size) == size, key
            assert all(float(c).is_integer() for c in size), key
    # Skyline packing, trying a few orders (tallest first, widest first, biggest first) and keeping the first that fits.
    fw = lambda k: 2 * (sizes[k][0] + sizes[k][2])
    fh = lambda k: sizes[k][2] + sizes[k][1]
    # Then, if none fits, the same with the order shuffled (from a fixed seed, so a rerun packs the same way).
    import random
    rng = random.Random(7)
    orders = [sorted(sizes, key=lambda k: (-fh(k), -fw(k), k)), sorted(sizes, key=lambda k: (-fw(k), -fh(k), k)),
              sorted(sizes, key=lambda k: (-fw(k) * fh(k), k))]
    for attempt in range(400):
        if attempt >= len(orders):
            order = sorted(sizes, key=lambda k: (-fw(k) * fh(k), k))
            # Keep the biggest pieces first, shuffle the rest.
            head, rest = order[:4], order[4:]
            rng.shuffle(rest)
            orders.append(head + rest)
        sky = [0] * TEX_W
        offs = {}
        for k in orders[attempt]:
            best = min(range(TEX_W - fw(k) + 1), key=lambda x: (max(sky[x:x + fw(k)]) + fh(k), x))
            y = max(sky[best:best + fw(k)])
            offs[k] = (best, y)
            for x in range(best, best + fw(k)):
                sky[x] = y + fh(k)
        if max(sky) <= TEX_H:
            break
    assert max(sky) <= TEX_H, f"texture too small: {max(sky)} (area {sum(fw(k) * fh(k) for k in sizes)})"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


# ---------------------------------------------------------------------------------------------- posing
def rest_pose(model):
    return {name: {"pivot": list(pivot), "rot": list(rot), "scale": [1.0, 1.0, 1.0], "visible": True}
            for name, _, pivot, rot, _ in model}


REAR_MAX = -0.52


def stomp_rear(s):
    """The body's rear through a stomp, s 0..1: up onto the hind legs (to 0.45), slammed down (by 0.6), settled."""
    if s <= 0.0 or s >= 1.0:
        return 0.0
    if s < 0.45:
        return REAR_MAX * smooth(s / 0.45)
    if s < 0.6:
        k = (s - 0.45) / 0.15
        return REAR_MAX + (0.07 - REAR_MAX) * k * k
    return 0.07 * (1.0 - smooth((s - 0.6) / 0.4))


def pose(model, walk=0.0, speed=0.0, age=0.0, yaw=0.0, pitch=0.0, browse=0.0, windup=0.0, paw_clock=None, charge=0.0,
         stomp=0.0, antlers=1.0, saddled=False, bull=True, baby=False):
    """Python twin of MooseModel.pose. walk and speed: the walk animation's position and speed; yaw and pitch the head's
    (radians); browse, windup and charge blends 0..1 (paw_clock the wind-up's own clock, age if not given); stomp the
    progress through a stomp 0..1; antlers the rack's growth 0..1 (a bull's)."""
    p = rest_pose(model)
    sin, cos = math.sin, math.cos
    amp = min(1.0, speed)
    stride = walk * 0.55
    s, c = sin(stride), cos(stride)
    swing = (0.5 + 0.3 * charge) * amp
    flex = (0.75 + 0.35 * charge) * amp
    rear = stomp_rear(stomp)
    up = clamp(rear / REAR_MAX)
    agitated = max(windup, charge, up)
    paw = sin((age if paw_clock is None else paw_clock) * 0.45)

    # The body: a gentle lift on each step, dipping toward the front when it lowers its head to charge, and rearing up
    # about the hips to stomp.
    tilt = rear + 0.05 * windup + 0.04 * charge
    body = p["body"]
    body["pivot"][1] -= abs(s) * 0.5 * amp
    body["rot"][0] = tilt

    # The front legs hang from the shoulders wherever the body has put them.
    bx, by, bz = body["pivot"]
    dy, dz = FRONT_PIVOT[1] - BODY_PIVOT[1], FRONT_PIVOT[2] - BODY_PIVOT[2]
    for side in ("right", "left"):
        leg = p[side + "_front_leg"]
        leg["pivot"][1] = by + dy * cos(tilt) - dz * sin(tilt)
        leg["pivot"][2] = bz + dy * sin(tilt) + dz * cos(tilt)

    # The walk: diagonal pairs, long and loping; each lower leg folds as it swings forward. A gallop when charging.
    phase = {"right_front": c, "left_front": -c, "right_hind": -c, "left_hind": c}
    lift = {"right_front": s, "left_front": -s, "right_hind": -s, "left_hind": s}
    for leg in phase:
        p[leg + "_leg"]["rot"][0] += phase[leg] * swing
        p[leg + "_shin"]["rot"][0] += max(0.0, lift[leg]) * (flex if "front" in leg else flex * 0.7)
    # Pawing the ground before a charge: the right forehoof lifted forward, struck down and dragged back.
    p["right_front_leg"]["rot"][0] += windup * (-0.3 + 0.4 * paw)
    p["right_front_shin"]["rot"][0] += windup * (0.2 + 0.7 * max(0.0, -paw))
    # Rearing: both forelegs follow the body up, folded at the knee, the left a beat behind; then thrown down straight.
    for side, lag in (("right", 0.0), ("left", 0.12)):
        u = clamp(up - lag) / (1.0 - lag)
        p[side + "_front_leg"]["rot"][0] += rear - 0.95 * u
        p[side + "_front_shin"]["rot"][0] += 1.45 * u
        p[side + "_hind_leg"]["rot"][0] += -0.18 * up
        p[side + "_hind_shin"]["rot"][0] += 0.2 * up

    # The head: nodding with the stride, looking about, stretched up high to browse, dropped low with the antlers
    # levelled to charge, tucked in as it rears.
    neck, head = p["neck"], p["head"]
    neck["rot"][0] += 0.35 * pitch + 0.05 * sin(stride * 2.0) * amp - 0.9 * browse + 0.35 * windup + 0.3 * charge + 0.25 * up
    neck["rot"][1] = 0.45 * yaw
    head["rot"][0] += 0.45 * pitch - 0.45 * browse + 0.5 * windup + 0.45 * charge - 0.15 * up
    head["rot"][1] = 0.4 * yaw
    tilt_change = (neck["rot"][0] - NECK_REST) + (head["rot"][0] - HEAD_REST)
    # Chewing while it browses.
    p["jaw"]["rot"][0] = browse * (0.06 + 0.14 * max(0.0, sin(age * 0.8)))
    # Ears: a twitch now and then; pinned back when it's angry.
    for side, sign in (("right", 1.0), ("left", -1.0)):
        ear = p[side + "_ear"]
        twitch = max(0.0, sin(age * 0.11 + (0.0 if sign > 0 else 2.0))) ** 8
        ear["rot"][0] += -0.55 * agitated + 0.25 * browse
        ear["rot"][2] += sign * (0.25 * twitch - 0.2 * agitated)
    # The bell hangs straight down whatever the head does, and swings with the stride.
    bell = p["bell"]
    bell["rot"][0] = BELL_REST - tilt_change - rear + 0.2 * s * amp + 0.05 * sin(age * 0.09)
    bell["rot"][2] = 0.1 * c * amp
    p["bell_tip"]["rot"][0] = 0.15 * sin(stride - 0.8) * amp + 0.04 * sin(age * 0.09 - 0.6)
    # The tail flicks; it goes up when the moose is angry.
    tail = p["tail"]
    tail["rot"][0] += -0.7 * agitated
    tail["rot"][2] = 0.2 * sin(age * 0.13) * (1.0 - 0.5 * agitated)
    # The rack (a bull's) grows back from nubs after it's shed; a calf's head is big for its body.
    for name in ANTLER_PARTS:
        p[name]["visible"] = bull and antlers > 0.0
    for name in ("right_antler", "left_antler"):
        g = 0.25 + 0.75 * clamp(antlers)
        p[name]["scale"] = [g, g, g]
    if baby:
        head["scale"] = [1.3, 1.3, 1.3]
    p["saddle"]["visible"] = saddled
    return p


def rot_matrix(rx, ry, rz):
    # Rz * Ry * Rx, as Quaternionf.rotationZYX.
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


IDENT = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def placed_cubes(model, p):
    """Every visible cube with its part's world transform: (part name, k, key, from, size, matrix, origin). As
    ModelPart.translateAndRotate: move to the pivot, turn, then scale."""
    t = tree(model)
    cubes = {name: c for name, _, _, _, c in model}
    out = []

    def walk(name, matrix, origin):
        for child in t.get(name, []):
            entry = p[child]
            if not entry["visible"]:
                continue
            o = tuple(origin[i] + apply(matrix, entry["pivot"])[i] for i in range(3))
            sx, sy, sz = entry["scale"]
            m = mul(mul(matrix, rot_matrix(*entry["rot"])), [[sx, 0, 0], [0, sy, 0], [0, 0, sz]])
            for k, (key, frm, size) in enumerate(cubes[child]):
                out.append((child, k, key, frm, size, m, o))
            walk(child, m, o)
    walk("root", IDENT, (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size):
    """The six faces of a cube as the game builds them: (corners, uv rect (u0, v0, u1, v1), face name). Texture
    (u0, v0) sits at corners[1], u runs toward corners[0], v toward corners[2]."""
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    P = lambda x, y, z: tuple(o[i] + apply(m, (x, y, z))[i] for i in range(3))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1, "down"), ([t2, t3, l3, l2], u2, v1, u22, v0, "up"),
             ([t0, l0, l3, t3], u0, v1, u1, v2, "west"), ([t1, t0, t3, t2], u1, v1, u2, v2, "north"),
             ([l1, t1, t2, l2], u2, v1, u3, v2, "east"), ([l0, l1, l2, l3], u3, v1, u4, v2, "south")]
    return [(verts, (a0, b0, a1, b1), name) for verts, a0, b0, a1, b1, name in polys]


def quads(model, p, labels=False):
    out = []
    for part, k, key, frm, size, m, o in placed_cubes(model, p):
        (u, v), _ = CUBES[key]
        centre = tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3))
        for verts, uv, name in cube_faces(m, o, u, v, frm, size):
            if labels:
                fc = tuple(sum(q[i] for q in verts) / 4 for i in range(3))
                out.append((verts, uv, f"{part}.{key}{k}.{name}", tuple(fc[i] - centre[i] for i in range(3))))
            else:
                out.append((verts, uv))
    return out


# ---------------------------------------------------------------------------------------------- z-fighting
from coplanar import Face, fights


def check(label, p):
    faces_ = [Face(name, verts, n) for verts, uv, name, n in quads(PARTS, p, labels=True)]
    hits = fights(faces_)
    for h in hits:
        print(f"z-fight ({label}):", h)
    return len(hits)


FULL = dict(antlers=1.0, saddled=True, bull=True)
POSES = [("rest", {}), ("browse", {"browse": 1.0, "age": 5.0}), ("look", {"yaw": 0.7, "pitch": -0.3}),
         ("look down", {"yaw": -0.5, "pitch": 0.4}), ("half antlers", {"antlers": 0.4})]
POSES += [(f"walk {w:.1f}", {"walk": w, "speed": 0.7, "age": w * 6}) for w in [i * 0.8 for i in range(15)]]
POSES += [(f"windup {a:.0f}", {"windup": 1.0, "age": a}) for a in range(0, 14, 2)]
POSES += [(f"charge {w:.1f}", {"walk": w, "speed": 1.0, "charge": 1.0, "age": w * 3}) for w in [i * 0.9 for i in range(12)]]
POSES += [(f"stomp {s_:.2f}", {"stomp": s_}) for s_ in [i * 0.05 for i in range(1, 20)]]
_bad = 0
for _label, _kw in POSES:
    _bad += check(_label, pose(PARTS, **{**FULL, **_kw}))
if _bad:
    raise SystemExit(f"moose: {_bad} coplanar overlapping face pairs")


def extents(p):
    pts = [q for verts, _ in quads(PARTS, p) for q in verts]
    return [(min(q[i] for q in pts), max(q[i] for q in pts)) for i in range(3)]


def count(names):
    return sum(len(c) for n, _, _, _, c in PARTS if n not in names)


_ext = extents(pose(PARTS, **FULL))
print(f"moose: {count(ANTLER_PARTS | SADDLE_PARTS)} cubes for a cow, {count(SADDLE_PARTS)} for a bull, "
      f"{count(set())} with the saddle; x {_ext[0][0]:.1f}..{_ext[0][1]:.1f}, top y {_ext[1][0]:.1f} "
      f"({(GROUND - _ext[1][0]) / 16:.2f} blocks tall), bottom y {_ext[1][1]:.2f}, z {_ext[2][0]:.1f}..{_ext[2][1]:.1f}")
_ext = extents(pose(PARTS, bull=False))
print(f"  without antlers: top y {_ext[1][0]:.1f} ({(GROUND - _ext[1][0]) / 16:.2f} blocks)")


# ---------------------------------------------------------------------------------------------- the coats
# Each texel is painted by where it is on the moose at rest (its model-space point, and its place on its own cube), so
# one ramp runs smoothly over neighbouring cubes. Cubes that share a key (the two sides' legs, ears, antler pieces) are
# painted from the right-hand one; the paint depends only on height and length along the body, so it suits both.
COATS = {
    # A bull: near-black brown, its hump and back sun-bleached to a greyer brown.
    "bull": [(122, 98, 78), (96, 74, 57), (74, 55, 42), (56, 41, 32), (42, 31, 25)],
    # A cow: a warmer, lighter brown.
    "cow": [(146, 114, 86), (120, 92, 68), (96, 72, 53), (74, 55, 41), (57, 42, 32)],
    # A calf: reddish fawn.
    "calf": [(190, 132, 86), (166, 112, 70), (140, 92, 58), (114, 74, 47), (90, 58, 38)],
}
HUMP_BLEACH = {"bull": (150, 132, 112), "cow": (164, 140, 112), "calf": (206, 160, 112)}
STOCKING = [(206, 190, 166), (184, 166, 140), (160, 142, 116), (132, 116, 94)]
HAIR = [(60, 45, 35), (46, 34, 27), (35, 26, 21)]
MUZZLE = [(150, 122, 98), (128, 102, 80), (106, 84, 66), (84, 66, 52)]
LIP = (176, 150, 126)
NOSTRIL = (34, 25, 21)
EYE = (22, 16, 14)
EAR_INNER = [(198, 172, 146), (172, 146, 122)]
HOOF = [(92, 86, 82), (64, 60, 58), (44, 41, 40), (30, 28, 28)]
ANTLER = [(244, 236, 214), (226, 212, 180), (202, 182, 146), (170, 148, 112), (132, 112, 84)]
BURR = [(132, 112, 88), (106, 88, 68), (84, 70, 56)]
LEATHER = [(184, 124, 74), (158, 102, 58), (130, 82, 46), (100, 62, 36), (74, 46, 28)]
WOOL = [(196, 70, 56), (170, 52, 44), (140, 40, 36), (108, 30, 30)]
WOOL_STRIPE = [(236, 218, 176), (210, 190, 148)]
IRON = [(222, 226, 230), (184, 190, 198), (140, 146, 156), (104, 110, 120)]

BODY_KEYS = {"barrel", "chest", "brisket", "hump", "withers", "loin", "rump", "croup", "belly", "shoulder", "haunch", "neck",
             "throat", "tail"}
LEG_KEYS = {"forearm_upper", "forearm", "front_cannon", "thigh", "gaskin", "hind_cannon", "hock"}
ANTLER_KEYS = {"burr", "beam", "palm", "lobe_front", "lobe_rear", "tine_short", "tine_long", "brow_tine"}


def coat_at(variant, p):
    """The coat at a point at rest: light along the top, dark under the belly, the hump and back bleached paler, and
    the legs fading into pale stockings from just below the belly."""
    pal = COATS[variant]
    x, y, z = p
    t = (y + 13.0) / 22.0
    col = ramp(pal, 0.05 + 0.8 * t)
    # The pale saddle of hair over the hump and the back.
    bleach = smooth((-5.0 - y) / 6.0) * (0.55 if z < 2.0 else 0.25)
    col = lerp(col, HUMP_BLEACH[variant], bleach * 0.6)
    # Stockings: pale from the knees down.
    if y > 9.0:
        k = smooth((y - 10.0) / 5.0)
        col = lerp(col, ramp(STOCKING, 0.1 + 0.6 * clamp((y - 12.0) / 10.0)), k)
    return col


def painter(variant):
    pal = COATS[variant]

    def paint(key, face, p, l, size):
        w, h, d = size
        lx, ly, lz = l
        if key in BODY_KEYS or key in LEG_KEYS:
            col = coat_at(variant, p)
            if key in ("shoulder", "haunch") and face in ("west", "east"):
                # A soft highlight on the bulge of the muscle.
                k = 1.0 - min(1.0, math.hypot((lz - d / 2) / (d / 2), (ly - h * 0.4) / (h * 0.6)))
                col = lerp(col, HUMP_BLEACH[variant], 0.18 * smooth(k))
            if key == "tail":
                col = lerp(col, HAIR[1], 0.35 + 0.4 * ly / h)
            return shade(col, 0.82) if face == "down" else col
        if key in ("crest", "mane"):
            # Coarse dark hair, lightening a little toward the ridge.
            col = ramp(HAIR, 0.15 + 0.7 * ly / h) if face != "up" else HAIR[0]
            return col
        if key == "skull":
            col = lerp(coat_at(variant, p), pal[1], 0.3)
            # Small dark eyes set high on the sides, toward the front, under a lighter brow.
            if face in ("west", "east"):
                ez, ey = 1.6, 2.6
                if abs(lz - ez) < 1.0 and abs(ly - ey) < 0.6:
                    return EYE
                if abs(lz - ez) < 1.0 and abs(ly - (ey - 1.0)) < 0.6:
                    return lerp(col, HUMP_BLEACH[variant], 0.4)
            return shade(col, 0.82) if face == "down" else col
        if key in ("muzzle", "nose", "jaw", "bridge"):
            # Toward the nose the brown lightens to a soft grey-brown; the overhanging nose is darkest underneath.
            t = 0.15 + 0.55 * ly / max(1, h)
            col = ramp(MUZZLE, t)
            if key == "muzzle":
                col = lerp(coat_at(variant, p), col, 0.4 + 0.6 * clamp(1.0 - lz / d))
            if key == "nose" and face == "north":
                # Two long nostrils, slanting apart, and the pale lip line along the bottom.
                cx = w / 2
                for sign in (-1, 1):
                    nx = cx + sign * 1.7
                    if abs(lx - nx) < 0.6 and 1.4 < ly < 3.6:
                        return NOSTRIL
                if ly > h - 1.0:
                    return LIP
            if key == "jaw" and face in ("north", "west", "east") and ly < 1.0:
                return LIP
            if face == "down":
                return shade(col, 0.78)
            return col
        if key == "ear":
            if face == "north":
                # The open ear: pale inside, a dark rim of hair.
                rim = lx < 0.6 or lx > w - 0.6 or ly < 0.6
                return lerp(pal[2], HAIR[0], 0.3) if rim else ramp(EAR_INNER, ly / h)
            return lerp(coat_at(variant, p), pal[1], 0.4)
        if key in ("bell", "bell_tip"):
            # A hank of dark hair, combed into regular strands.
            base = ramp(HAIR, 0.1 + 0.6 * (ly / h if key == "bell" else 0.6 + 0.4 * ly / h))
            return shade(base, 0.9) if face in ("west", "east") and int(lz * 2) % 3 == 1 else base
        if key == "hoof":
            # Dark horn with a lighter band at the top and the cleft down the middle of the front and back.
            col = ramp(HOOF, 0.1 + 0.7 * ly / h)
            if face in ("north", "south") and abs(lx - w / 2) < 0.5:
                return HOOF[3]
            if face == "up":
                return lerp(coat_at(variant, p), STOCKING[2], 0.3)
            return col
        if key in ANTLER_KEYS:
            return antler_colour(key, face, l, size)
        if key in ("blanket", "flap"):
            # Red wool with a cream stripe along every edge.
            edge = min(lx, w - lx, lz, d - lz) if face in ("up", "down") else min(ly, h - ly) if key == "flap" else 99
            if face in ("west", "east") and key == "flap":
                edge = min(ly, h - ly, lz, d - lz)
            if 0.8 < edge < 1.8:
                return WOOL_STRIPE[0] if face != "down" else WOOL_STRIPE[1]
            return ramp(WOOL, 0.15 + 0.5 * ly / max(1, h)) if face != "down" else WOOL[3]
        if key in ("seat", "pommel", "cantle", "girth", "girth_band"):
            col = ramp(LEATHER, 0.1 + 0.6 * ly / max(1, h))
            if key == "seat" and face == "up":
                k = 1.0 - min(1.0, math.hypot((lx - w / 2) / (w / 2), (lz - d / 2) / (d / 2)))
                col = lerp(LEATHER[2], LEATHER[0], smooth(k))
            if key in ("pommel", "cantle") and face == "up":
                col = LEATHER[0]
            if key == "girth" and face in ("west", "east") and abs(ly - 7.5) < 1.0:
                return IRON[1]
            return shade(col, 0.85) if face == "down" else col
        if key == "stirrup":
            return IRON[0] if ly < 0.5 or face == "up" else ramp(IRON, 0.2 + 0.6 * ly / h)
        raise KeyError(key)
    return paint


def antler_colour(key, face, l, size):
    """Bone: cream, warmer and darker toward the skull, pale toward the rims; every tine whitening to its point. The
    burr at the base is a knobbed brown ring."""
    w, h, d = size
    lx, ly, lz = l
    if key == "burr":
        k = (int(lx * 2) + int(ly * 2) + int(lz * 2)) % 2
        return BURR[0 if face == "up" else 1 + k]
    if key == "beam":
        t = 0.75 - 0.35 * (w - lx) / w if face not in ("up", "down") else 0.5
        return ramp(ANTLER, t + (0.15 if face == "down" else 0.0))
    if key in ("tine_short", "tine_long"):
        # Pointing up (-y): the tip is ly 0.
        return ramp(ANTLER, 0.05 + 0.5 * ly / h) if face != "down" else ANTLER[3]
    if key == "brow_tine":
        return ramp(ANTLER, 0.05 + 0.45 * lz / d)
    # The palm and its lobes: a smooth wash from the warm centre to the pale rim; darker underneath.
    if face == "down":
        return ANTLER[3]
    rim = min(lx, w - lx, lz, d - lz) if face == "up" else min(lx, w - lx) if face in ("north", "south") else min(lz, d - lz)
    t = 0.45 - 0.35 * smooth(1.0 - rim / 2.5)
    if key == "palm":
        # The palm runs a little warmer toward the beam (its inner edge, x high on the right-hand palm).
        t += 0.15 * clamp(lx / w)
    return ramp(ANTLER, t)


def paint_coat(variant):
    """Paints every cube's six faces from its rest-pose placement (all parts shown, so the antlers and saddle too)."""
    tex = Canvas(TEX_W * S, TEX_H * S)
    paint = painter(variant)
    done = set()
    p = pose(PARTS, **FULL)
    for part, k, key, frm, size, m, o in placed_cubes(PARTS, p):
        if key in done:
            continue
        done.add(key)
        (u, v), _ = CUBES[key]
        world = cube_faces(m, o, u, v, frm, size)
        local = cube_faces(IDENT, (0.0, 0.0, 0.0), u, v, (0.0, 0.0, 0.0), size)
        for (wv, (ua, va, ub, vb), name), (lv, _, _) in zip(world, local):
            lo_u, hi_u = sorted((ua, ub))
            lo_v, hi_v = sorted((va, vb))
            for ty in range(int(round(lo_v * S)), int(round(hi_v * S))):
                for tx in range(int(round(lo_u * S)), int(round(hi_u * S))):
                    s_ = ((tx + 0.5) / S - ua) / (ub - ua)
                    t_ = ((ty + 0.5) / S - va) / (vb - va)
                    pt = tuple(wv[1][i] + (wv[0][i] - wv[1][i]) * s_ + (wv[2][i] - wv[1][i]) * t_ for i in range(3))
                    lp = tuple(lv[1][i] + (lv[0][i] - lv[1][i]) * s_ + (lv[2][i] - lv[1][i]) * t_ for i in range(3))
                    tex.set(tx, ty, rgba(paint(key, name, pt, lp, size)))
    return tex


TEXTURES = {v: paint_coat(v) for v in COATS}
for _v, _t in TEXTURES.items():
    _t.save(f"entity/moose/moose_{_v}.png")


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "moose", "MooseModel.java"))


def f(v):
    s_ = f"{v:.4f}".rstrip("0")
    s_ = s_ + "0" if s_.endswith(".") else s_
    return ("0.0" if s_ == "-0.0" else s_) + "f"


def java_layer():
    t = tree(PARTS)
    lines = ["\tpublic static LayerDefinition createLayer() {", "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in PARTS:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({', '.join(f(c) for c in frm)}, {', '.join(f(c) for c in size)})"
        pose_ = (f"PartPose.offsetAndRotation({', '.join(f(c) for c in pivot)}, {', '.join(f(c) for c in rot)})"
                 if any(rot) else f"PartPose.offset({', '.join(f(c) for c in pivot)})")
        used = t.get(name) is not None
        lines.append(f"\t\t{'PartDefinition ' + var + ' = ' if used else ''}{('p_' + parent) if parent else 'root'}"
                     f".addOrReplaceChild(\"{name}\", {builder},\n\t\t\t\t{pose_});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    lines.append("\t}")
    return "\n".join(lines)


def java_constants():
    return "\n".join([
        f"\tstatic final float BODY_PIVOT_Y = {f(BODY_PIVOT[1])};",
        f"\tstatic final float BODY_PIVOT_Z = {f(BODY_PIVOT[2])};",
        f"\tstatic final float FRONT_PIVOT_Y = {f(FRONT_PIVOT[1])};",
        f"\tstatic final float FRONT_PIVOT_Z = {f(FRONT_PIVOT[2])};",
        f"\tstatic final float NECK_REST = {f(NECK_REST)};",
        f"\tstatic final float HEAD_REST = {f(HEAD_REST)};",
        f"\tstatic final float BELL_REST = {f(BELL_REST)};",
        f"\tstatic final float REAR_MAX = {f(REAR_MAX)};"])


if os.path.exists(JAVA):
    src = open(JAVA).read()
    start, end = "\t// BEGIN GENERATED by tools/gen_moose.py\n", "\t// END GENERATED\n"
    a, b = src.index(start) + len(start), src.index(end)
    src = src[:a] + java_constants() + "\n\n" + java_layer() + "\n" + src[b:]
    with open(JAVA, "w") as out:
        out.write(src)
else:
    print("note: MooseModel.java not found; the layer wasn't written")


# ---------------------------------------------------------------------------------------------- item sprites
def sprite(f_):
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            col = f_(x, y)
            if col is not None:
                c.set(x, y, rgba(col))
    return c


def outlined(mask, fill, outline):
    """A sprite from a mask (a set of (x, y)): each pixel filled by fill(x, y); a one-pixel outline round it."""
    def f_(x, y):
        if (x, y) in mask:
            return fill(x, y)
        if any((x + a, y + b) in mask for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            return outline
        return None
    return sprite(f_)


def antler_sprite():
    """A shed palmate antler: the knobbed burr at the bottom left, the short beam rising from it, and the broad palm
    fanning out above, its outer rim drawn out into a row of short points. Bone-coloured, lit from the top left."""
    cx, cy = 3.0, 14.0
    mask, rim = set(), set()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r, a = math.hypot(dx, dy), math.degrees(math.atan2(-dy, dx))   # a: 0 to the right, 90 straight up
            if 6.0 <= r <= 10.6 and 8.0 <= a <= 84.0:
                mask.add((x, y))
            # The points: short tapering spikes out past the rim, every 15 degrees.
            for k, ang in enumerate((14.0, 29.0, 44.0, 59.0, 74.0)):
                reach = 12.8 if k in (1, 2, 3) else 12.0
                if 10.0 <= r <= reach and abs(a - ang) <= 4.6 * (reach - r) / (reach - 10.0) + 0.8:
                    mask.add((x, y))
            # The beam: from the burr out to the palm's inner edge.
            if r <= 6.6 and 35.0 <= a <= 62.0 and r >= 1.5:
                mask.add((x, y))
    burr = {(1, 13), (2, 13), (1, 14), (2, 14), (3, 14), (2, 15), (1, 12)}
    mask |= burr

    def fill(x, y):
        if (x, y) in burr:
            return BURR[(x + y) % 2]
        if (x - 1, y) not in mask and (x, y - 1) not in mask:
            return ANTLER[1]
        if (x, y - 1) not in mask:
            return ANTLER[1]
        if (x + 1, y) not in mask or (x, y + 1) not in mask:
            return ANTLER[4]
        r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        return ramp(ANTLER, 0.75 - 0.4 * clamp((r - 4.0) / 8.0))
    return outlined(mask, fill, (78, 62, 46))


def handle_sprite():
    """An antler handle: a pale tine sawn off and smoothed into a grip, a knob at the butt, bound with leather at the
    end that takes the tool head, lying corner to corner as vanilla's sticks do."""
    mask = set()
    for i in range(10):
        x, y = 4 + i, 13 - i
        mask.update({(x, y), (x - 1, y), (x, y - 1)})
    mask.update({(2, 13), (2, 14), (3, 14), (3, 13)})   # the knob at the butt (the burr end)
    wrap = {(x, y) for (x, y) in mask if 10 <= x <= 13}

    def fill(x, y):
        if (x, y) in wrap:
            return LEATHER[1] if (x + y) % 2 == 0 else LEATHER[3]
        if x <= 3 and y >= 13:
            return BURR[(x + y) % 2]
        if (x, y - 1) not in mask or (x - 1, y) not in mask:
            return ANTLER[0]
        if (x + 1, y) not in mask or (x, y + 1) not in mask:
            return ANTLER[3]
        return ANTLER[1]
    return outlined(mask, fill, (92, 76, 58))


def hook_sprite():
    """An antler hook: a carved bone J with a barb, and a turn of line through the eye at the top."""
    mask = {(10, 1), (11, 1), (10, 2), (11, 2), (10, 3), (11, 3)}
    for y in range(3, 11):
        mask.update({(10, y), (11, y)})
    curve = [(9, 11), (10, 11), (11, 11), (8, 12), (9, 12), (10, 12), (6, 12), (7, 12), (5, 11), (6, 11), (4, 10), (5, 10),
             (4, 9), (4, 8), (5, 9), (3, 8), (3, 7)]
    mask.update(curve)

    def fill(x, y):
        if y <= 2:
            return (210, 204, 190) if (x, y) == (10, 1) else (176, 166, 150)
        side = x == 10 or (x, y) in curve[:6]
        return ramp(ANTLER, 0.1 if side else 0.45 + 0.1 * (y > 10))
    s = outlined(mask, fill, (92, 76, 58))
    # The line knotted on at the eye.
    for (x, y) in [(12, 1), (13, 0)]:
        s.set(x, y, rgba((226, 226, 220)))
    return s


def venison_sprite(raw):
    """A venison steak cut across the haunch: a lean, dark oval lying corner to corner, a thin rim of fat along one edge,
    and the round of bone in it (beef has no bone, mutton's runs out the end). Cooked: browned, with grill marks."""
    meat = [(196, 70, 74), (166, 48, 56), (134, 34, 44), (100, 24, 34)] if raw else \
        [(184, 118, 76), (152, 90, 56), (120, 66, 42), (88, 46, 30)]
    fat = [(240, 222, 214), (220, 196, 186)] if raw else [(236, 200, 140), (212, 170, 110)]
    outline = (78, 18, 28) if raw else (62, 34, 22)
    # A rotated ellipse, long axis bottom-left to top-right.
    ca, sa = math.cos(-math.pi / 4), math.sin(-math.pi / 4)

    def local(x, y):
        dx, dy = x + 0.5 - 8.0, y + 0.5 - 8.0
        return (dx * ca - dy * sa) / 7.0, (dx * sa + dy * ca) / 4.6
    mask = {(x, y) for y in range(16) for x in range(16) if sum(c * c for c in local(x, y)) <= 1.0}
    bone = (9, 6)

    def fill(x, y):
        u, v = local(x, y)
        r = math.hypot(x + 0.5 - bone[0], y + 0.5 - bone[1])
        if r < 0.8:
            return (150, 96, 70) if raw else (120, 76, 50)
        if r < 1.8:
            return (236, 226, 204) if raw else (220, 204, 172)
        if v < -0.72:
            return fat[0] if v < -0.85 else fat[1]
        if not raw and (x - y) % 4 == 0 and -0.5 < v < 0.75 and abs(u) < 0.7:
            return meat[3]
        return ramp(meat, 0.15 + 0.45 * (v + 1) / 2 + 0.15 * max(0.0, u))
    return outlined(mask, fill, outline)


def egg_sprite():
    """The shopping cart egg's outline, repainted: dark moose brown going down to pale stockings, cream antler spots."""
    w, h, cart = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
    mask = [p_[3] > 0 for p_ in cart]
    xs = [i % w for i in range(w * h) if mask[i]]
    ys = [i // w for i in range(w * h) if mask[i]]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    egg = Canvas(w, h)
    spots = {(6, 4): ANTLER[1], (5, 5): ANTLER[1], (9, 4): ANTLER[1], (10, 5): ANTLER[1], (7, 9): ANTLER[2],
             (9, 11): STOCKING[1], (5, 11): STOCKING[1]}
    for i in range(w * h):
        if not mask[i]:
            continue
        x, y = i % w, i // w
        edge = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            egg.px[i] = rgba((30, 22, 18))
            continue
        c = ramp(COATS["bull"], 0.3 - ((cx - x) + (cy - y)) / 16.0)
        if y >= max(ys) - 3:
            c = lerp(c, STOCKING[2], 0.6)
        egg.px[i] = rgba(spots.get((x, y), c))
    return egg


SPRITES = {
    "moose_antler": antler_sprite(),
    "antler_handle": handle_sprite(),
    "antler_hook": hook_sprite(),
    "raw_venison": venison_sprite(True),
    "cooked_venison": venison_sprite(False),
    "moose_spawn_egg": egg_sprite(),
}
for _name, _s in SPRITES.items():
    _s.save(f"item/{_name}.png")


# ---------------------------------------------------------------------------------------------- json
MODELS = os.path.join(ASSETS, "models")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f_:
        json.dump(obj, f_, indent=2, ensure_ascii=False)
        f_.write("\n")


def merge(path, entries):
    """Adds entries to a JSON object file others also write (lang, sounds.json), keeping what's there and its order."""
    with open(path) as f_:
        obj = json.load(f_, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


def tag_add(path, entries):
    """Adds entries to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    for e in entries:
        if e not in values:
            values.append(e)
    write(path, {"values": values})


for name in SPRITES:
    parent = "minecraft:item/handheld" if name == "antler_handle" else "minecraft:item/generated"
    write(os.path.join(MODELS, "item", name + ".json"),
          OrderedDict([("parent", parent), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def uniform(low, high):
    return {"type": "minecraft:uniform", "min": low, "max": high}


def looting(high=1.0):
    return {"type": "minecraft:enchanted_count_increase", "count": uniform(0.0, high), "enchantment": "minecraft:looting"}


SMELTS = {"type": "minecraft:furnace_smelt", "condition": {"type": "minecraft:any_of", "terms": [
    {"type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:flags": {"is_on_fire": True}}},
    {"type": "minecraft:entity_properties", "entity": "direct_attacker", "predicate": {"minecraft:equipment": {
        "mainhand": {"predicates": {"minecraft:enchantments": [{"enchantments": "#minecraft:smelts_loot"}]}}}}}]}}

# The moose: more leather than a cow (two to four), and two to four venison (cooked if it died burning).
write(os.path.join(DATA, "loot_table/entities/moose.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:leather",
                                  "modifier": [{"type": "minecraft:set_count", "count": uniform(2, 4)}, looting()]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:raw_venison",
                                  "modifier": [{"type": "minecraft:set_count", "count": uniform(2, 4)}, SMELTS, looting()]}]}]),
    ("random_sequence", "tomyen:entities/moose")]))
# A bull with a full rack also rolls this: one antler, sometimes both.
write(os.path.join(DATA, "loot_table/entities/moose_antlers.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:moose_antler",
                                         "modifier": [{"type": "minecraft:set_count", "count": uniform(1, 2)}]}]}]),
    ("random_sequence", "tomyen:entities/moose_antlers")]))


def advancement(name, ingredient, category):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


for suffix, kind, ticks in (("", "smelting", 200), ("_from_smoking", "smoking", 100), ("_from_campfire_cooking", "campfire_cooking", 600)):
    name = "cooked_venison" + suffix
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:" + kind), ("category", "food"), ("cookingtime", ticks), ("experience", 0.35),
        ("ingredient", "tomyen:raw_venison"), ("result", {"id": "tomyen:cooked_venison"})]))
    advancement(name, "tomyen:raw_venison", "food")


def shapeless(name, ingredients, result, count_, category):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count_), ("id", result)]))]))
    advancement(name, ingredients[0], category)


# An antler saws into four hardy handles; carved down with a flint, into three fish hooks.
shapeless("antler_handle", ["tomyen:moose_antler"], "tomyen:antler_handle", 4, "misc")
shapeless("antler_hook", ["tomyen:moose_antler", "minecraft:flint", "minecraft:string"], "tomyen:antler_hook", 3, "equipment")

# Antler-handled tools: the usual recipe with antler handles for sticks. The same tool, half again as durable.
HANDLED = [
    ("stone", "#minecraft:stone_tool_materials", "minecraft", 131),
    ("copper", "#minecraft:copper_tool_materials", "minecraft", 190),
    ("iron", "#minecraft:iron_tool_materials", "minecraft", 250),
    ("bloom_steel", "tomyen:bloom_steel_ingot", "tomyen", 400),
]
SHAPES = {
    "pickaxe": ["XXX", " # ", " # "],
    "axe": ["XX", "X#", " #"],
    "shovel": ["X", "#", "#"],
    "hoe": ["XX", " #", " #"],
    "sword": ["X", "X", "#"],
}
for material, ingredient, ns, durability in HANDLED:
    for tool, pattern in SHAPES.items():
        name = f"antler_handled_{material}_{tool}"
        write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
            ("type", "minecraft:crafting_shaped"), ("category", "equipment"),
            ("key", OrderedDict([("#", "tomyen:antler_handle"), ("X", ingredient)])),
            ("pattern", pattern),
            ("result", OrderedDict([("count", 1), ("id", f"{ns}:{material}_{tool}"), ("components", OrderedDict([
                ("minecraft:max_damage", durability * 3 // 2),
                ("minecraft:lore", [{"translate": "item.tomyen.antler_handle.fitted", "color": "gray", "italic": False}])]))]))]))
        advancement(name, "tomyen:antler_handle", "equipment")

# What a moose eats (to calm it, to breed it, to grow a calf): fruit, berries, and the leaves it browses on.
write(os.path.join(DATA, "tags/item/moose_food.json"), OrderedDict([("replace", False), ("values", [
    "minecraft:apple", "minecraft:sweet_berries", "minecraft:glow_berries", "#minecraft:leaves"])]))
# It takes a saddle; walks on powder snow without sinking or freezing; and floats with a rider in deep water.
tag_add(os.path.join(VANILLA_DATA, "tags/entity_type/can_equip_saddle.json"), ["tomyen:moose"])
tag_add(os.path.join(VANILLA_DATA, "tags/entity_type/powder_snow_walkable_mobs.json"), ["tomyen:moose"])
tag_add(os.path.join(VANILLA_DATA, "tags/entity_type/freeze_immune_entity_types.json"), ["tomyen:moose"])
tag_add(os.path.join(VANILLA_DATA, "tags/entity_type/can_float_while_ridden.json"), ["tomyen:moose"])


def vanilla(names, pitch, volume=None, **extra):
    out = []
    for n in names:
        e = OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)])
        if volume is not None:
            e["volume"] = volume
        e.update(extra)
        out.append(e)
    return out


def event(name, sounds, subtitle=True):
    e = OrderedDict()
    if subtitle:
        e["subtitle"] = f"subtitles.tomyen.{name}"
    e["sounds"] = sounds
    return name, e


def numbered(path, n):
    return [f"{path}{i}" for i in range(1, n + 1)]


SOUNDS = OrderedDict([
    # A long, low groan: a cow's moo and a camel's grumble, slowed right down.
    event("moose_ambient", vanilla(numbered("mob/cow/say", 4), 0.55, 0.9) + vanilla(numbered("mob/camel/ambient", 8), 0.6, 0.8)),
    # The bull's bellow before a charge: the moody cow's call dropped low, under a ravager's roar, carrying far.
    event("moose_bellow", vanilla(numbered("mob/cow/moody/ambient", 9), 0.5, 1.4, attenuation_distance=40)
          + vanilla(numbered("mob/ravager/roar", 4), 1.25, 0.7, attenuation_distance=40)),
    event("moose_hurt", vanilla(numbered("mob/cow/hurt", 3), 0.6) + vanilla(numbered("mob/camel/hurt", 4), 0.75)),
    event("moose_death", vanilla(numbered("mob/camel/death", 2), 0.6) + vanilla(numbered("mob/ravager/death", 3), 1.2, 0.8)),
    event("moose_step", vanilla(numbered("mob/camel/step", 6), 0.8, 0.5), subtitle=False),
    # A snort and a stamp: a warning before it comes at you.
    event("moose_snort", vanilla(numbered("mob/polarbear/warning", 3), 0.7, 0.9) + vanilla(numbered("mob/goat/pre_ram", 4), 0.55)),
    event("moose_charge_hit", vanilla(numbered("mob/goat/impact", 3), 0.65, 1.2) + vanilla(numbered("mob/ravager/stun", 3), 1.1, 0.6)),
    event("moose_stomp", vanilla(numbered("mob/ravager/step", 5), 0.75, 1.3) + vanilla(numbered("mob/goat/impact", 3), 1.0, 0.7)),
    event("moose_eat", vanilla(numbered("mob/camel/eat", 5), 0.75, 0.8) + vanilla(numbered("mob/horse/eat", 5), 0.6, 0.6)),
    event("moose_shed", vanilla(numbered("mob/goat/horn_break", 4), 0.55)),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.tomyen.moose", "Moose"),
    ("item.tomyen.moose_spawn_egg", "Moose Spawn Egg"),
    ("item.tomyen.moose_antler", "Moose Antler"),
    ("item.tomyen.antler_handle", "Antler Handle"),
    ("item.tomyen.antler_handle.fitted", "Antler handle: half again as durable"),
    ("item.tomyen.antler_hook", "Antler Hook"),
    ("item.tomyen.raw_venison", "Raw Venison"),
    ("item.tomyen.cooked_venison", "Cooked Venison"),
    ("subtitles.tomyen.moose_ambient", "Moose groans"),
    ("subtitles.tomyen.moose_bellow", "Moose bellows"),
    ("subtitles.tomyen.moose_hurt", "Moose hurts"),
    ("subtitles.tomyen.moose_death", "Moose dies"),
    ("subtitles.tomyen.moose_snort", "Moose snorts"),
    ("subtitles.tomyen.moose_charge_hit", "Moose rams"),
    ("subtitles.tomyen.moose_stomp", "Moose stomps"),
    ("subtitles.tomyen.moose_eat", "Moose browses"),
    ("subtitles.tomyen.moose_shed", "Moose sheds an antler"),
]))


# ---------------------------------------------------------------------------------------------- preview
def render(p, view, tex, scale=5, width=360, height=320, light=(0.35, -0.8, -0.5), focus=None, background=(46, 52, 64)):
    """Orthographic render. view: 'front', 'side' (from its right), 'three' (three-quarters), 'back' or 'top'. focus: a
    model point to centre (default: the ground near the bottom)."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "top": 0.0, "rthree": -math.pi / 4}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [rgba(background)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        # Centre the moose (its middle is about 10 pixels ahead of the origin).
        ox, oy, ly = width * 0.5 - (-10.0 * -sa) * scale, height - 14, GROUND
    if view != "top":
        gy = int(oy + (GROUND - ly) * scale)
        for x in range(width):
            if 0 <= gy < height:
                img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    for verts, (ua, va, ub, vb) in quads(PARTS, p):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = (ub - ua) * S, (vb - va) * S
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua * S + (i + 0.5) * du / nu), TEX_W * S - 1)
                tv = min(int(va * S + (j + 0.5) * dv / nv), TEX_H * S - 1)
                col = tex.get(tu, tv)
                if col[3] == 0:
                    continue
                c = rgba(shade(col, lit))
                corners = []
                for (a_, b_) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s_, t_ = a_ / nu, b_ / nv
                    q = tuple(A[k] + e1[k] * s_ + e2[k] * t_ for k in range(3))
                    if view == "top":
                        x, z, dep = q[0], q[2], q[1]
                        corners.append((ox + x * scale, height / 2 + z * scale, dep))
                    else:
                        x = q[0] * ca - q[2] * sa
                        z = q[0] * sa + q[2] * ca
                        corners.append((ox + x * scale, oy + (q[1] - ly) * scale, z))
                fill(img, zbuf, width, height, corners, sum(c_[2] for c_ in corners) / 4, c)
    return img


def fill(img, zbuf, width, height, corners, depth, colour):
    xs_ = [c[0] for c in corners]
    ys_ = [c[1] for c in corners]
    for py in range(max(0, int(min(ys_))), min(height, int(math.ceil(max(ys_))) + 1)):
        for px in range(max(0, int(min(xs_))), min(width, int(math.ceil(max(xs_))) + 1)):
            if inside(corners, px + 0.5, py + 0.5) and depth < zbuf[py * width + px]:
                zbuf[py * width + px] = depth
                img[py * width + px] = colour


def inside(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cross) < 1e-9:
            continue
        s_ = 1 if cross > 0 else -1
        if sign == 0:
            sign = s_
        elif s_ != sign:
            return False
    return sign != 0


def sprites_panel(width, height):
    img = [(46, 52, 64, 255)] * (width * height)
    k = 6
    for n, name in enumerate(SPRITES):
        canvas = SPRITES[name]
        bx, by = 12 + (n % 3) * (16 * k + 14), 20 + (n // 3) * (16 * k + 30)
        for y in range(16 * k):
            for x in range(16 * k):
                pxl = canvas.get(x // k, y // k)
                checker = (56, 62, 74, 255) if ((x // 12) + (y // 12)) % 2 else (50, 56, 68, 255)
                if 0 <= by + y < height and 0 <= bx + x < width:
                    img[(by + y) * width + bx + x] = pxl if pxl[3] else checker
    return img


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k_, im in enumerate(images):
        bx, by = (k_ % cols) * w, (k_ // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    bull, cow, calf = TEXTURES["bull"], TEXTURES["cow"], TEXTURES["calf"]
    P = lambda **kw: pose(PARTS, **kw)
    only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
    views = [
        ("bull front", lambda: render(P(), "front", bull)),
        ("bull three", lambda: render(P(age=10), "three", bull)),
        ("bull side", lambda: render(P(), "side", bull)),
        ("cow three", lambda: render(P(bull=False, age=30), "three", cow)),
        ("calf three", lambda: render(P(bull=False, baby=True, age=20), "three", calf, scale=8)),
        ("walk", lambda: render(P(walk=1.4, speed=0.7, age=8), "side", bull)),
        ("walk2", lambda: render(P(walk=4.3, speed=0.7, age=20), "three", cow)),
        ("browse", lambda: render(P(browse=1.0, age=3), "side", bull)),
        ("windup", lambda: render(P(windup=1.0, age=-3.5), "three", bull)),
        ("charge", lambda: render(P(walk=2.2, speed=1.0, charge=1.0, age=8), "side", bull)),
        ("stomp up", lambda: render(P(bull=False, stomp=0.45, age=4), "side", cow)),
        ("stomp slam", lambda: render(P(bull=False, stomp=0.6, age=6), "three", cow)),
        ("saddled", lambda: render(P(saddled=True, age=12), "rthree", bull)),
        ("head", lambda: render(P(age=12), "three", bull, scale=10, focus=(0.0, -3.0, -28.0))),
        ("antlers top", lambda: render(P(), "top", bull, scale=6, focus=(0.0, 0.0, -10.0))),
        ("sprites", lambda: sprites_panel(360, 320)),
    ]
    imgs = [fn() for name, fn in views if not only or name in only]
    out, bw, bh = board(imgs, 360, 320, 4)
    png(os.path.join(PREVIEW, "preview_moose.png"), bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
