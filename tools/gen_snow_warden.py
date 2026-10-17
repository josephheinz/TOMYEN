"""Builds the Snow Warden and what comes of it: its model (the cube layout in PARTS, written into
client/snowwarden/SnowWardenModel.java between the GENERATED markers), its texture and its two glow layers (the carved
face; the chest crystals and tendrils, which brighten as it charges the frost blast), the item sprites (Frozen Heart,
Snow Globe, the spawn egg) and the Glacial Froststeel armor (its four item sprites and its worn layers, recoloured from
froststeel's own art), with their item models and definitions and the armor's equipment asset. On the data side it
writes the Snow Warden's loot table, the frost blast damage type, the recipes (the Snow Globe; the four smithing
upgrades from froststeel to glacial, which take no template) and their advancements, the tags (armor slots, freeze
immunity, powder snow walking, frost blast through shields), and merges the lang entries and the sounds (vanilla
warden, snow golem, snow, glass and iron golem sounds, re-pitched) into en_us.json and sounds.json.

The textures are painted by where each texel sits on the warden at rest, not cube by cube: the packed snow is laid in
level bands (each with a bright lip along its top) running unbroken across neighbouring cubes, lighter toward the top;
ice is a clear blue that brightens to its rims; the pumpkin is ribbed orange with rime along its top edges. No noise
or dithering; a rerun gives the same files.

The model is checked for z-fighting (tools/coplanar.py) at rest and through its walk, swing, slam, charge, blast,
throw, roar and sniff poses. Its extents and cube count are printed.

With --preview DIR it also renders, into DIR, preview_snow_warden.png: the warden from the front, three-quarters, side
and back; mid-stride; swinging; raising and slamming its fists; charging and firing the frost blast; throwing; roaring;
sniffing; the head and the chest up close; and the item sprites. The render uses the same cubes, pivots and UV layout
as the Java model, and a Python twin of SnowWardenModel.pose.

Usage: python3 tools/gen_snow_warden.py src/main/resources/assets/tomyen [--preview DIR]"""
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
# size). Cube sizes are whole pixels; positions can be fractional. Rotations here are the rest pose;
# SnowWardenModel.pose adds to them.
#
# The legs hang from the root; the body (with the head and arms on it) pivots about the hips, so it can hunch, lean back
# to charge and throw itself forward to slam without dragging the legs along.
TEX_W, TEX_H = 256, 64
GROUND = 24.0

HIP = (0.0, 9.0, 1.0)            # the body bends about this
LEG_PIVOT = (5.5, 9.0, 1.0)      # (x mirrored for the right leg)
ARM_PIVOT = (12.5, -15.0, 0.0)   # the shoulder, in model space (x mirrored for the right arm)
HEAD_PIVOT = (0.0, -17.0, -5.0)  # the top front of the chest, in model space
BODY_REST = 0.1                  # the hunch
ARM_REST_Z = 0.1                 # the arms hang a little out from the sides
TENDRIL_REST = (0.0, 0.0, -0.45)


def body_cube(key, frm, size):
    """A cube on the body, given where it sits in model space at rest (before the hunch)."""
    return (key, tuple(frm[i] - HIP[i] for i in range(3)), size)


def mirror(part, name, parent):
    """The left-hand twin of a right-hand part: x flipped, and the turns about y and z reversed."""
    _, _, (px, py, pz), (rx, ry, rz), cubes = part
    return (name, parent, (-px, py, pz), (rx, -ry, -rz), [(k, (-(f[0] + s[0]), f[1], f[2]), s) for k, f, s in cubes])


def mirrored_cubes(cubes):
    """The same cubes with their left-hand twins added (x flipped), for parts on the middle line."""
    return cubes + [(k, (-(f[0] + s[0]), f[1], f[2]), s) for k, f, s in cubes]


def build():
    # The body. Belly and chest are two great packed snowballs, the chest the wider; a hump of snow between the
    # shoulders and a bulge down the back; the hips under the belly, where the legs go in.
    torso = [
        body_cube("belly", (-8.0, -5.0, -6.0), (16, 14, 12)),
        body_cube("hips", (-7.0, 5.0, -5.0), (14, 5, 10)),
        body_cube("chest", (-11.0, -19.0, -7.0), (22, 15, 14)),
        body_cube("hump", (-9.0, -22.0, -4.5), (18, 4, 11)),
        body_cube("back", (-8.0, -16.0, 6.0), (16, 11, 3)),
        # Drifts packed on lower down: a lip of snow round the front of the belly, and a bank across the small of the back.
        body_cube("belly_lip", (-7.0, 0.0, -6.75), (14, 2, 1)),
        body_cube("back_drift", (-6.0, -3.5, 5.0), (12, 7, 2)),
        # The ice collarbone along the top of the chest, and the glowing crystals in it: a tall one in the middle, a
        # smaller one each side, and a stub below.
        body_cube("collar", (-8.0, -19.5, -8.5), (16, 2, 2)),
        body_cube("crystal_core", (-3.0, -15.0, -8.5), (6, 8, 2)),
        body_cube("crystal_low", (-1.5, -7.5, -8.0), (3, 3, 2)),
    ]
    # Each side: three icicle ribs curving round in front of the crystals (a bar, its end running back into the chest,
    # and its tip dipping toward the middle), a side crystal, and icicles hanging off the chest where it overhangs the
    # belly.
    side = [
        body_cube("crystal_side", (-6.5, -13.0, -8.0), (3, 5, 2)),
        body_cube("rib_upper", (-10.0, -16.0, -10.0), (7, 1, 1)),
        body_cube("rib_mid", (-10.0, -13.0, -10.0), (8, 1, 1)),
        body_cube("rib_low", (-9.5, -10.0, -10.0), (7, 1, 1)),
        body_cube("rib_end", (-10.75, -15.75, -9.25), (1, 1, 2)),
        body_cube("rib_end", (-10.75, -12.75, -9.25), (1, 1, 2)),
        body_cube("rib_end", (-10.25, -9.75, -9.25), (1, 1, 2)),
        body_cube("rib_tip", (-4.0, -15.5, -10.25), (2, 1, 1)),
        body_cube("rib_tip", (-3.0, -12.5, -10.25), (2, 1, 1)),
        body_cube("rib_tip", (-3.5, -9.5, -10.25), (2, 1, 1)),
        body_cube("icicle_long", (-10.5, -4.5, -5.0), (1, 4, 1)),
        body_cube("icicle_mid", (-9.0, -4.5, -2.0), (1, 3, 1)),
        body_cube("icicle_short", (-10.0, -4.5, 2.0), (1, 2, 1)),
    ]
    body = ("body", None, HIP, (BODY_REST, 0.0, 0.0), torso + mirrored_cubes(side))

    rel = lambda p, base=HIP: tuple(p[i] - base[i] for i in range(3))
    # Ice spines standing up off the hump and leaning back: a tall pair either side of the middle, a short pair outside.
    spine_big = ("right_spine", "body", rel((-2.5, -21.0, 3.5)), (-0.55, 0.0, 0.18), [
        ("spine_base", (-1.5, -5.0, -1.5), (3, 6, 3)),
        ("spine_tip", (-0.5, -8.0, -0.5), (1, 3, 1))])
    spine_small = ("right_spine_small", "body", rel((-6.5, -19.5, 3.0)), (-0.4, 0.0, 0.42), [
        ("spine_small", (-1.0, -4.0, -1.0), (2, 5, 2)),
        ("spine_small_tip", (-0.5, -6.0, -0.5), (1, 2, 1))])

    # The head: a frosted carved pumpkin, ribbed, with a band of lobes round its sides and a raised crown, the stem with
    # its curl and a leaf, snow lying on the back of it and icicles off its chin.
    head = ("head", "body", rel(HEAD_PIVOT), (-BODY_REST, 0.0, 0.0), [
        ("pumpkin", (-6.0, -9.0, -10.0), (12, 10, 11)),
        ("pumpkin_band", (-6.5, -8.0, -9.5), (13, 8, 10)),
        ("pumpkin_crown", (-5.0, -9.5, -9.0), (10, 1, 9)),
        ("stem", (-1.0, -12.0, -6.0), (2, 3, 2)),
        ("stem_curl", (-0.5, -12.75, -7.25), (1, 1, 2)),
        ("leaf", (1.0, -10.25, -8.0), (3, 1, 2)),
        ("snow_cap", (-5.5, -9.75, -4.5), (11, 1, 4)),
        ("chin_icicle", (-4.0, 0.5, -9.5), (1, 2, 1)),
        ("chin_icicle_long", (-0.5, 0.5, -9.5), (1, 3, 1)),
        ("chin_icicle", (3.0, 0.5, -9.5), (1, 2, 1))])
    # Ice tendrils where the warden's are: a knob on the pumpkin's side, a shard rising from it, and a branch.
    tendril = ("right_tendril", "head", (-6.0, -5.5, -5.0), TENDRIL_REST, [
        ("tendril_knob", (-1.5, -1.0, -1.0), (2, 2, 2)),
        ("tendril_shard", (-1.0, -7.0, -0.5), (1, 6, 1)),
        ("tendril_branch", (-3.0, -4.0, -0.5), (2, 1, 1)),
        ("tendril_twig", (-3.0, -6.0, -0.5), (1, 2, 1))])

    # The arms: a snow pauldron capped with ice and a spike, icicles off its outer edge; a long stick (a twig still on
    # it) through an ice bracer with a raised ring; and a packed-snow fist with an ice knuckle plate and three spikes.
    arm = ("right_arm", "body", rel((-ARM_PIVOT[0], ARM_PIVOT[1], ARM_PIVOT[2])), (-BODY_REST, 0.0, ARM_REST_Z), [
        ("pauldron", (-4.5, -4.0, -5.0), (9, 6, 10)),
        ("pauldron_plate", (-4.0, -5.0, -4.0), (8, 1, 8)),
        ("pauldron_spike", (-2.0, -7.5, -1.0), (2, 3, 2)),
        ("icicle_mid", (-4.0, 1.5, -3.0), (1, 3, 1)),
        ("icicle_long", (-4.0, 1.5, 0.0), (1, 4, 1)),
        ("icicle_short", (-4.0, 1.5, 3.0), (1, 2, 1)),
        ("stick", (-1.5, 1.0, -1.5), (3, 20, 3)),
        ("twig", (-5.0, 6.0, -0.5), (4, 1, 1)),
        ("twig_tip", (-5.0, 4.0, -0.5), (1, 2, 1)),
        ("bracer", (-2.5, 13.0, -2.5), (5, 6, 5)),
        ("bracer_band", (-3.0, 15.0, -3.0), (6, 2, 6)),
        ("fist", (-3.5, 20.0, -3.5), (7, 8, 7)),
        ("knuckle_plate", (-3.0, 22.0, -4.25), (6, 5, 1)),
        ("knuckle_spike", (-2.5, 23.0, -6.0), (1, 1, 2)),
        ("knuckle_spike", (-0.5, 23.0, -6.0), (1, 1, 2)),
        ("knuckle_spike", (1.5, 23.0, -6.0), (1, 1, 2))])

    # The legs: short and thick, packed snow, an ice plate over the knee, a broad snowball foot with two ice claws.
    foot_top = GROUND - LEG_PIVOT[1] - 4.0
    leg = ("right_leg", None, (-LEG_PIVOT[0], LEG_PIVOT[1], LEG_PIVOT[2]), (0.0, 0.0, 0.0), [
        ("thigh", (-3.5, -1.0, -3.5), (7, 8, 7)),
        ("shin", (-3.0, 6.5, -3.0), (6, 6, 6)),
        ("foot", (-4.0, foot_top, -5.0), (8, 4, 9)),
        ("knee_plate", (-2.0, 1.5, -4.25), (4, 5, 1)),
        ("claw", (-3.5, foot_top + 1.75, -6.25), (2, 2, 2)),
        ("claw", (1.5, foot_top + 1.75, -6.25), (2, 2, 2))])
    return [
        body,
        spine_big, mirror(spine_big, "left_spine", "body"),
        spine_small, mirror(spine_small, "left_spine_small", "body"),
        head, tendril, mirror(tendril, "left_tendril", "head"),
        arm, mirror(arm, "left_arm", "body"),
        leg, mirror(leg, "left_leg", None),
    ]


PARTS = build()


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
    fw = lambda k: 2 * (sizes[k][0] + sizes[k][2])
    fh = lambda k: sizes[k][2] + sizes[k][1]
    import random
    rng = random.Random(7)
    orders = [sorted(sizes, key=lambda k: (-fh(k), -fw(k), k)), sorted(sizes, key=lambda k: (-fw(k), -fh(k), k)),
              sorted(sizes, key=lambda k: (-fw(k) * fh(k), k))]
    for attempt in range(400):
        if attempt >= len(orders):
            order = sorted(sizes, key=lambda k: (-fw(k) * fh(k), k))
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
    assert max(sky) <= TEX_H, f"texture too small: {max(sky)}"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


# ---------------------------------------------------------------------------------------------- posing
def rest_pose(model):
    return {name: {"pivot": list(pivot), "rot": list(rot), "scale": [1.0, 1.0, 1.0], "visible": True}
            for name, _, pivot, rot, _ in model}


def swing_curve(p):
    """A fist swung overhead and down, p 0..1: (the arm's turn, the body's twist into it, its lean)."""
    if p <= 0.0 or p >= 1.0:
        return 0.0, 0.0, 0.0
    if p < 0.4:
        k = smooth(p / 0.4)
        return -2.3 * k, -0.3 * k, -0.08 * k
    if p < 0.6:
        k = (p - 0.4) / 0.2
        return -2.3 + 2.8 * k * k, -0.3 + 0.6 * k, -0.08 + 0.3 * k
    k = smooth((p - 0.6) / 0.4)
    return 0.5 * (1.0 - k), 0.3 * (1.0 - k), 0.22 * (1.0 - k)


def slam_curve(p):
    """Both fists raised high and slammed down, p 0..1: (the arms' turn, the body's lean)."""
    if p <= 0.0 or p >= 1.0:
        return 0.0, 0.0
    if p < 0.5:
        k = smooth(p / 0.5)
        return -2.9 * k, -0.2 * k
    if p < 0.6:
        k = (p - 0.5) / 0.1
        return -2.9 + 2.6 * k * k, -0.2 + 0.65 * k
    k = smooth((p - 0.6) / 0.4)
    return -0.3 * (1.0 - k), 0.45 * (1.0 - k)


def throw_curve(p):
    """An arm wound back and a snowball lobbed, p 0..1: (the arm's turn, the body's twist, its lean)."""
    if p <= 0.0 or p >= 1.0:
        return 0.0, 0.0, 0.0
    if p < 0.55:
        k = smooth(p / 0.55)
        return -2.6 * k, -0.35 * k, -0.1 * k
    if p < 0.7:
        k = (p - 0.55) / 0.15
        return -2.6 + 2.0 * k, -0.35 + 0.65 * k, -0.1 + 0.3 * k
    k = smooth((p - 0.7) / 0.3)
    return -0.6 * (1.0 - k), 0.3 * (1.0 - k), 0.2 * (1.0 - k)


def blast_thrust(b):
    """The chest thrown forward as the frost blast fires, b 0..1 through the blast."""
    if b <= 0.0 or b >= 1.0:
        return 0.0
    return b / 0.2 if b < 0.2 else 1.0 - smooth((b - 0.2) / 0.8)


def roar_envelope(r):
    if r <= 0.0 or r >= 1.0:
        return 0.0
    return smooth(r / 0.2) * (1.0 - smooth((r - 0.8) / 0.2))


def pose(model, walk=0.0, speed=0.0, age=0.0, yaw=0.0, pitch=0.0, swing=0.0, swing_left=False, slam=0.0, charge=0.0,
         blast=0.0, throw=0.0, roar=0.0, sniff=0.0):
    """Python twin of SnowWardenModel.pose. walk and speed: the walk animation's position and speed; yaw and pitch the
    head's (radians); swing, slam, blast, throw and roar progress through those, 0..1; charge and sniff blends 0..1."""
    p = rest_pose(model)
    sin, cos = math.sin, math.cos
    amp = min(1.0, speed)
    stride = walk * 0.5
    s, c = sin(stride), cos(stride)
    breathe = sin(age * 0.06)
    arm_turn, twist, lean = swing_curve(swing)
    slam_arms, slam_lean = slam_curve(slam)
    throw_arm, throw_twist, throw_lean = throw_curve(throw)
    thrust = blast_thrust(blast)
    howl = roar_envelope(roar)

    body = p["body"]
    # Short, heavy steps: the body bobs and sways with them.
    body["pivot"][1] += abs(s) * 0.8 * amp
    body["rot"][0] += 0.015 * breathe + lean + slam_lean + throw_lean - 0.22 * charge + 0.3 * thrust - 0.18 * howl + 0.1 * sniff
    body["rot"][1] += 0.05 * s * amp + (-twist if swing_left else twist) + throw_twist
    body["rot"][2] += 0.05 * s * amp

    p["right_leg"]["rot"][0] += c * 0.55 * amp
    p["left_leg"]["rot"][0] -= c * 0.55 * amp

    # The arms: swinging with the stride, hanging a little out; raised for the blows; spread to charge and roar.
    spread = 0.7 * charge + 0.9 * howl + 0.03 * breathe
    for side, sign in (("right", 1.0), ("left", -1.0)):
        arm = p[side + "_arm"]
        arm["rot"][0] += (-c if sign > 0 else c) * 0.4 * amp + slam_arms + 0.35 * charge - 0.5 * howl
        arm["rot"][2] += sign * (spread + 0.25 * slam_arms / 2.9)
    swung = p["left_arm" if swing_left else "right_arm"]
    swung["rot"][0] += arm_turn
    p["right_arm"]["rot"][0] += throw_arm

    # The head: looking about, thrown back to roar (shaking), tipped down and turning to sniff, up as it charges.
    head = p["head"]
    head["rot"][0] += 0.7 * pitch - 0.3 * charge - 0.55 * howl + 0.35 * sniff
    head["rot"][1] += 0.8 * yaw + 0.45 * sin(age * 0.25) * sniff
    head["rot"][2] += 0.08 * sin(age * 1.3) * howl
    # The tendrils quiver, and flare out when it roars, sniffs or charges.
    flare = 0.5 * howl + 0.3 * sniff + 0.25 * charge
    for side, sign in (("right", 1.0), ("left", -1.0)):
        t = p[side + "_tendril"]
        t["rot"][2] += -sign * (0.08 * sin(age * 0.15 + (0.0 if sign > 0 else 1.7)) + flare)
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
    """Every visible cube with its part's world transform: (part name, k, key, from, size, matrix, origin)."""
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
    """The six faces of a cube as the game builds them: (corners, uv rect (u0, v0, u1, v1), face name)."""
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


POSES = [("rest", {}), ("look", {"yaw": 0.7, "pitch": -0.3}), ("look down", {"yaw": -0.5, "pitch": 0.4})]
POSES += [(f"walk {w:.1f}", {"walk": w, "speed": 0.8, "age": w * 6}) for w in [i * 0.7 for i in range(14)]]
POSES += [(f"swing {s_:.2f}", {"swing": s_}) for s_ in [i * 0.07 for i in range(1, 14)]]
POSES += [(f"swing left {s_:.2f}", {"swing": s_, "swing_left": True}) for s_ in [0.3, 0.5, 0.7]]
POSES += [(f"slam {s_:.2f}", {"slam": s_}) for s_ in [i * 0.06 for i in range(1, 17)]]
POSES += [(f"charge {a:.0f}", {"charge": 1.0, "age": a}) for a in range(0, 40, 6)]
POSES += [(f"blast {b:.2f}", {"blast": b, "charge": 1.0 - b}) for b in [i * 0.15 for i in range(1, 7)]]
POSES += [(f"throw {t:.2f}", {"throw": t}) for t in [i * 0.08 for i in range(1, 12)]]
POSES += [(f"roar {r:.2f}", {"roar": r, "age": r * 44}) for r in [i * 0.1 for i in range(1, 10)]]
POSES += [(f"sniff {a:.0f}", {"sniff": 1.0, "age": a}) for a in range(0, 30, 5)]
_bad = 0
for _label, _kw in POSES:
    _bad += check(_label, pose(PARTS, **_kw))
if _bad:
    raise SystemExit(f"snow warden: {_bad} coplanar overlapping face pairs")


def extents(p):
    pts = [q for verts, _ in quads(PARTS, p) for q in verts]
    return [(min(q[i] for q in pts), max(q[i] for q in pts)) for i in range(3)]


_ext = extents(pose(PARTS))
print(f"snow warden: {sum(len(c) for _, _, _, _, c in PARTS)} cubes; x {_ext[0][0]:.1f}..{_ext[0][1]:.1f}, top y {_ext[1][0]:.1f} "
      f"({(GROUND - _ext[1][0]) / 16:.2f} blocks tall), bottom y {_ext[1][1]:.2f}, z {_ext[2][0]:.1f}..{_ext[2][1]:.1f}")


# ---------------------------------------------------------------------------------------------- the texture
SNOW = [(252, 253, 255), (238, 244, 250), (222, 232, 243), (204, 218, 234), (182, 199, 222), (160, 180, 208)]
ICE = [(236, 250, 255), (196, 232, 252), (150, 204, 244), (112, 172, 232), (84, 140, 214), (64, 110, 190)]
CRYSTAL = [(236, 255, 255), (170, 244, 255), (98, 218, 246), (46, 168, 226), (28, 116, 190)]
PUMPKIN = [(240, 166, 52), (226, 142, 30), (204, 118, 20), (176, 94, 14), (144, 72, 10)]
PUMPKIN_GROOVE = (128, 64, 10)
CARVED = (44, 24, 14)
RIME = [(250, 252, 255), (226, 238, 250)]
STEM = [(118, 94, 50), (96, 74, 38), (74, 56, 28)]
LEAF = [(120, 168, 70), (92, 138, 52), (68, 108, 40)]
WOOD = [(150, 112, 50), (128, 94, 40), (104, 76, 32), (80, 58, 24)]
GLOW_FACE = [(236, 255, 255), (170, 242, 255), (96, 214, 244)]
GLOW_CRYSTAL = [(240, 255, 255), (176, 246, 255), (104, 220, 248), (50, 160, 222)]
TENDRIL_GLOW = (40, 110, 130)

SNOW_KEYS = {"belly", "belly_lip", "back_drift", "hips", "chest", "hump", "back", "pauldron", "fist", "thigh", "shin", "foot", "snow_cap"}
ICE_KEYS = {"collar", "rib_upper", "rib_mid", "rib_low", "rib_end", "rib_tip", "icicle_long", "icicle_mid",
            "icicle_short", "spine_base", "spine_tip", "spine_small", "spine_small_tip", "pauldron_plate", "pauldron_spike",
            "bracer", "bracer_band", "knuckle_plate", "knuckle_spike", "knee_plate", "claw", "chin_icicle",
            "chin_icicle_long", "tendril_knob", "tendril_shard", "tendril_branch", "tendril_twig"}
CRYSTAL_KEYS = {"crystal_core", "crystal_side", "crystal_low"}
PUMPKIN_KEYS = {"pumpkin", "pumpkin_band", "pumpkin_crown"}
WOOD_KEYS = {"stick", "twig", "twig_tip"}
TENDRIL_KEYS = {"tendril_knob", "tendril_shard", "tendril_branch", "tendril_twig"}
POINTED = {"icicle_long", "icicle_mid", "icicle_short", "chin_icicle", "chin_icicle_long"}   # hang point-down
SPIKES = {"spine_tip", "spine_small_tip", "pauldron_spike", "tendril_shard", "tendril_twig"}  # point up

# The carved face on the pumpkin's front (12 x 10): narrowed eyes slanting down toward the middle, a small nose, and a
# jagged grin with its teeth.
FACE_W = 12
_EYE = {(1, 2), (2, 2), (1, 3), (2, 3), (3, 3), (4, 3), (3, 4), (4, 4)}
FACE = _EYE | {(FACE_W - 1 - x, y) for x, y in _EYE} | {(5, 5), (6, 5)}
FACE |= {(x, 6) for x in (2, 3, 5, 6, 8, 9)} | {(x, 7) for x in range(2, 10)} | {(x, 8) for x in (3, 4, 7, 8)}


def snow_at(y, face):
    """Packed snow at a height (model space, at rest): laid in level bands five pixels deep, each with a bright lip
    along its top and darkening a touch toward its foot; the whole a little lighter toward the top."""
    band = math.floor((y + 30.0) / 5.0)
    within = (y + 30.0) - band * 5.0
    t = 0.12 + 0.35 * clamp((y + 24.0) / 48.0) + 0.06 * (band % 2)
    if within < 1.0 and face != "down":
        t -= 0.12
    else:
        t += 0.05 * within / 5.0
    if face == "up":
        t -= 0.1
    elif face == "down":
        t += 0.18
    return ramp(SNOW, t)


def ice_at(key, face, l, size):
    """Clear ice, brightest at its rims; icicles and spikes paling toward their points."""
    w, h, d = size
    lx, ly, lz = l
    if face in ("up", "down"):
        rim = min(lx, w - lx, lz, d - lz)
    elif face in ("north", "south"):
        rim = min(lx, w - lx, ly, h - ly)
    else:
        rim = min(lz, d - lz, ly, h - ly)
    t = 0.45
    if key in POINTED:
        t = 0.65 - 0.5 * (ly / h)          # the point (ly high) palest
    elif key in SPIKES:
        t = 0.15 + 0.5 * (ly / h)          # the point (ly 0) palest
    elif key in ("pauldron_plate", "knuckle_plate", "knee_plate", "bracer", "bracer_band", "collar"):
        t = 0.35 + 0.25 * (ly / max(1, h))
    if min(w, h, d) > 1 and rim < 0.75:
        t -= 0.3
    if face == "down":
        t += 0.2
    elif face == "up":
        t -= 0.1
    return ramp(ICE, t)


def crystal_at(face, l, size):
    """A glowing crystal: facets catching light down one diagonal, deeper down the other."""
    w, h, d = size
    lx, ly, lz = l
    if face in ("north", "south"):
        a, b, aw, bh = lx, ly, w, h
    elif face in ("west", "east"):
        a, b, aw, bh = lz, ly, d, h
    else:
        a, b, aw, bh = lx, lz, w, d
    u, v = a / aw, b / bh
    t = 0.15 + 0.55 * clamp(0.5 * (u + v))
    if abs(u - v) < 0.12 or abs(u + v - 1.0) < 0.12:
        t -= 0.15   # the facet edges
    return ramp(CRYSTAL, t)


def pumpkin_at(key, face, l, size):
    w, h, d = size
    lx, ly, lz = l
    if face == "north" and key == "pumpkin" and (int(lx), int(ly)) in FACE:
        return CARVED
    if face in ("up", "down"):
        # Ribs running front to back on the top; the crown frosted over.
        col = ramp(PUMPKIN, 0.35 + (0.25 if face == "down" else 0.0))
        if key == "pumpkin_crown" and face == "up":
            edge = min(lx, w - lx, lz, d - lz)
            return RIME[0] if edge >= 1.0 else RIME[1]
        if face == "up" and int(lx) % 3 == 0 and key == "pumpkin":
            col = shade(col, 0.88)
        if face == "up" and min(lx, w - lx, lz, d - lz) < 1.0:
            return RIME[1]
        return col
    across = lx if face in ("north", "south") else lz
    span = w if face in ("north", "south") else d
    # Vertical ribs every three pixels, darker grooves between them; lighter toward the top, frost along the top edge.
    groove = int(across) % 3 == 0 and 0 < int(across) < span - 1
    t = 0.2 + 0.45 * (ly / h)
    if ly < 1.0 and key == "pumpkin":
        return RIME[1]
    col = ramp(PUMPKIN, t)
    if groove and not (face == "north" and key == "pumpkin"):
        col = lerp(col, PUMPKIN_GROOVE, 0.45)
    elif face == "north" and key == "pumpkin" and int(across) in (0, span - 1):
        col = lerp(col, PUMPKIN_GROOVE, 0.3)
    return col


def wood_at(key, face, l, size):
    w, h, d = size
    lx, ly, lz = l
    if face in ("up", "down"):
        return WOOD[3] if key == "stick" else WOOD[2]
    across = lx if face in ("north", "south") else lz
    # Bark: a lighter stripe down one side of each face, the grain darker beside it; frosted at the top.
    if key == "stick" and ly < 1.0:
        return RIME[1]
    t = 0.2 if int(across) == 0 else 0.55 if int(across) == int(max(w, d)) - 1 else 0.35
    return ramp(WOOD, t)


def paint(key, face, p, l, size):
    w, h, d = size
    lx, ly, lz = l
    if key in SNOW_KEYS:
        col = snow_at(p[1], face)
        if key == "fist" and face == "north":
            col = shade(col, 0.96)
        return col
    if key in CRYSTAL_KEYS:
        return crystal_at(face, l, size)
    if key in ICE_KEYS:
        return ice_at(key, face, l, size)
    if key in PUMPKIN_KEYS:
        return pumpkin_at(key, face, l, size)
    if key in ("stem", "stem_curl"):
        return ramp(STEM, 0.2 + 0.6 * ly / max(1, h)) if face != "up" else STEM[0]
    if key == "leaf":
        if face == "up":
            return LEAF[1] if int(lx) == 1 else LEAF[0]
        return LEAF[2]
    if key in WOOD_KEYS:
        return wood_at(key, face, l, size)
    raise KeyError(key)


def glow_face(key, face, p, l, size):
    """The face layer: the carved holes glowing ice-white, brightest in the middle of each."""
    lx, ly, lz = l
    if key == "pumpkin" and face == "north":
        cell = (int(lx), int(ly))
        if cell in FACE:
            inner = all((cell[0] + a, cell[1] + b) in FACE for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            return GLOW_FACE[0] if inner else GLOW_FACE[1] if cell[1] in (3, 7) else GLOW_FACE[2]
    return None


def glow_crystal(key, face, p, l, size):
    """The crystal layer: the chest crystals, and a faint light in the tendrils."""
    if key in CRYSTAL_KEYS:
        c = crystal_at(face, l, size)
        k = sum(c) / (3 * 255)
        return ramp(GLOW_CRYSTAL, 1.0 - k)
    if key in ("tendril_shard", "tendril_twig"):
        return TENDRIL_GLOW
    return None


def paint_texture(fn):
    """Paints every cube's six faces from its rest-pose placement; fn returns a colour or None (clear)."""
    tex = Canvas(TEX_W, TEX_H)
    done = set()
    p = pose(PARTS)
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
            for ty in range(int(round(lo_v)), int(round(hi_v))):
                for tx in range(int(round(lo_u)), int(round(hi_u))):
                    s_ = ((tx + 0.5) - ua) / (ub - ua)
                    t_ = ((ty + 0.5) - va) / (vb - va)
                    pt = tuple(wv[1][i] + (wv[0][i] - wv[1][i]) * s_ + (wv[2][i] - wv[1][i]) * t_ for i in range(3))
                    lp = tuple(lv[1][i] + (lv[0][i] - lv[1][i]) * s_ + (lv[2][i] - lv[1][i]) * t_ for i in range(3))
                    col = fn(key, name, pt, lp, size)
                    if col is not None:
                        tex.set(tx, ty, rgba(col))
    return tex


TEXTURE = paint_texture(paint)
FACE_GLOW = paint_texture(glow_face)
CRYSTAL_GLOW = paint_texture(glow_crystal)
TEXTURE.save("entity/snow_warden/snow_warden.png")
FACE_GLOW.save("entity/snow_warden/snow_warden_face.png")
CRYSTAL_GLOW.save("entity/snow_warden/snow_warden_crystals.png")


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "snowwarden", "SnowWardenModel.java"))


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


if os.path.exists(JAVA):
    src = open(JAVA).read()
    start, end = "\t// BEGIN GENERATED by tools/gen_snow_warden.py\n", "\t// END GENERATED\n"
    a, b = src.index(start) + len(start), src.index(end)
    src = src[:a] + java_layer() + "\n" + src[b:]
    with open(JAVA, "w") as out:
        out.write(src)
else:
    print("note: SnowWardenModel.java not found; the layer wasn't written")


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


HEART_ICE = [(244, 255, 255), (196, 246, 255), (140, 226, 248), (88, 192, 236), (54, 150, 212), (36, 110, 176)]


def heart_sprite():
    """The Frozen Heart: a heart of pale cyan ice, lit from the top left, a bright glint on its left lobe and one clean
    facet line running down from the cleft to the point."""
    mask = set()
    for y in range(16):
        for x in range(16):
            # The heart curve, (x^2 + y^2 - 1)^3 - x^2 y^3 <= 0, scaled to fill the sprite.
            u = (x + 0.5 - 8.0) / 5.9
            v = -(y + 0.5 - 8.1) / 5.9
            if (u * u + v * v - 1.0) ** 3 - u * u * v ** 3 <= 0.0:
                mask.add((x, y))
    glint = {(4, 4), (5, 4), (4, 5)}

    def fill(x, y):
        if (x, y) in glint:
            return HEART_ICE[0]
        # Light from the top left: a smooth wash across, deeper toward the bottom right.
        t = 0.15 + 0.6 * clamp(((x - 2) + (y - 2)) / 20.0)
        if (x - 1, y) not in mask or (x, y - 1) not in mask:
            t -= 0.12
        if (x + 1, y) not in mask or (x, y + 1) not in mask:
            t += 0.2
        # The facet: the line from the cleft down to the point, one shade brighter on its left.
        if x == 7 and 5 <= y <= 12:
            t -= 0.1
        elif x == 8 and 5 <= y <= 12:
            t += 0.08
        return ramp(HEART_ICE, t)
    return outlined(mask, fill, (22, 66, 108))


GLASS = [(236, 248, 255), (206, 230, 246), (180, 212, 236)]
BASE_WOOD = [(150, 102, 58), (122, 80, 44), (96, 62, 34), (66, 42, 24)]


def globe_sprite():
    """The Snow Globe: a glass dome (pale sky inside, a white glint along its upper left) on a turned wooden base. In it
    a little snowy scene: a drift of snow, a spruce, a tiny pumpkin-headed snowman and a few flakes in the air."""
    cx, cy, r = 7.5, 6.5, 6.0
    dome = {(x, y) for y in range(16) for x in range(16) if math.hypot(x + 0.5 - 8.0, y + 0.5 - 7.0) <= r and y <= 11}
    base = set()
    for y, (x0, x1) in ((11, (3, 12)), (12, (2, 13)), (13, (2, 13)), (14, (3, 12))):
        base |= {(x, y) for x in range(x0, x1 + 1)}
    snow = {(x, y) for x in range(3, 13) for y in range(9, 11) if (x, y) in dome} | {(x, 8) for x in range(5, 11)}
    tree = {(5, 3), (4, 4), (5, 4), (6, 4), (5, 5), (4, 6), (5, 6), (6, 6), (3, 7), (4, 7), (5, 7), (6, 7), (7, 7)}
    trunk = {(5, 8)}
    man = {(9, 7), (10, 7), (9, 6), (10, 6)}
    head = {(9, 5), (10, 5)}
    flakes = {(7, 3), (11, 3), (8, 5), (3, 5), (12, 6)}
    mask = dome | base

    def fill(x, y):
        if (x, y) in base and y >= 11:
            if y == 11:
                return BASE_WOOD[0]
            if y == 14:
                return BASE_WOOD[3]
            return BASE_WOOD[1] if x < 8 else BASE_WOOD[2]
        if (x, y) in head:
            return PUMPKIN[1] if x == 9 else PUMPKIN[3]
        if (x, y) in man:
            return SNOW[0] if x == 9 else SNOW[3]
        if (x, y) in tree:
            return (52, 104, 66) if x <= 5 else (38, 82, 54)
        if (x, y) in trunk:
            return WOOD[2]
        if (x, y) in snow:
            return SNOW[0] if y == 8 or (y == 9 and (x, 8) not in snow) else SNOW[2]
        if (x, y) in flakes:
            return (255, 255, 255)
        # The glass: pale blue sky, a glint arc along the upper left.
        d = math.hypot(x + 0.5 - 8.0, y + 0.5 - 7.0)
        if d > r - 1.0:
            ang = math.atan2(y + 0.5 - 7.0, x + 0.5 - 8.0)
            if -2.6 < ang < -1.5:
                return GLASS[0]
            return GLASS[2]
        return ramp([(198, 226, 244), (172, 206, 232)], (y - 1) / 9.0)
    return outlined(mask, fill, (60, 74, 92))


def egg_sprite():
    """The shopping cart egg's outline, repainted: packed snow going grey-blue down the egg, a pumpkin-orange cap and
    glowing cyan spots."""
    w, h, cart = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
    mask = [p_[3] > 0 for p_ in cart]
    ys = [i // w for i in range(w * h) if mask[i]]
    top = min(ys)
    egg = Canvas(w, h)
    spots = {(6, 9): CRYSTAL[1], (9, 10): CRYSTAL[1], (7, 12): CRYSTAL[2], (10, 7): CRYSTAL[2]}
    for i in range(w * h):
        if not mask[i]:
            continue
        x, y = i % w, i // w
        edge = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            egg.px[i] = rgba((52, 66, 92))
            continue
        if y <= top + 3:
            c = ramp(PUMPKIN, 0.1 + 0.15 * (y - top) + (0.2 if x > 8 else 0.0))
        else:
            c = ramp(SNOW, 0.05 + 0.6 * (y - top) / 12.0 + (0.12 if x > 9 else 0.0))
        egg.px[i] = rgba(spots.get((x, y), c))
    return egg


SPRITES = {
    "frozen_heart": heart_sprite(),
    "snow_globe": globe_sprite(),
    "snow_warden_spawn_egg": egg_sprite(),
}
for _name, _s in SPRITES.items():
    _s.save(f"item/{_name}.png")


# Glacial Froststeel: froststeel's own art, recoloured. Each texel's brightness is laid onto a glacial ramp (deep teal
# outline, through glacier cyan, to a glowing white-cyan), so every line and highlight of the original stays where it
# was and the pale steel comes out a bright, icy cyan.
GLACIAL = [(8, 34, 58), (14, 74, 108), (28, 132, 172), (74, 196, 226), (150, 238, 252), (212, 255, 255), (250, 255, 255)]
ARMOR_PIECES = ["helmet", "chestplate", "leggings", "boots"]


def glacial(src_rel, dst_rel):
    w, h, px = read_png(os.path.join(TEX, src_rel))
    lum = lambda c: (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0
    out = Canvas(w, h)
    for i, c in enumerate(px):
        if c[3] == 0:
            continue
        out.px[i] = rgba(ramp(GLACIAL, lum(c)), c[3])
    out.save(dst_rel)
    return out


GLACIAL_SPRITES = {}
for _piece in ARMOR_PIECES:
    GLACIAL_SPRITES[f"glacial_froststeel_{_piece}"] = glacial(f"item/froststeel_{_piece}.png", f"item/glacial_froststeel_{_piece}.png")
for _layer in ("humanoid", "humanoid_leggings"):
    glacial(f"entity/equipment/{_layer}/froststeel.png", f"entity/equipment/{_layer}/glacial_froststeel.png")


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


for name in list(SPRITES) + list(GLACIAL_SPRITES):
    write(os.path.join(MODELS, "item", name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})

write(os.path.join(ASSETS, "equipment", "glacial_froststeel.json"), {"layers": {
    "humanoid": [{"texture": "tomyen:glacial_froststeel"}],
    "humanoid_leggings": [{"texture": "tomyen:glacial_froststeel"}]}})


def uniform(low, high):
    return {"type": "minecraft:uniform", "min": low, "max": high}


def looting(high=1.0):
    return {"type": "minecraft:enchanted_count_increase", "count": uniform(0.0, high), "enchantment": "minecraft:looting"}


# What it leaves: always its Frozen Heart; a heap of snowballs, packed ice and a little blue ice; sometimes its pumpkin.
write(os.path.join(DATA, "loot_table/entities/snow_warden.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:frozen_heart"}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:snowball",
                                  "modifier": [{"type": "minecraft:set_count", "count": uniform(6, 16)}, looting(3.0)]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:packed_ice",
                                  "modifier": [{"type": "minecraft:set_count", "count": uniform(2, 5)}, looting()]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:blue_ice",
                                  "modifier": [{"type": "minecraft:set_count", "count": uniform(0, 2)}, looting()]}]},
        {"rolls": 1, "condition": {"type": "minecraft:random_chance", "chance": 0.35},
         "entries": [{"type": "minecraft:item", "name": "minecraft:carved_pumpkin"}]}]),
    ("random_sequence", "tomyen:entities/snow_warden")]))

# The frost blast: scales with difficulty like any mob's attack; the freezing hurt sound. It's in bypasses_shield (a
# raised shield only halves it: see SnowWarden.frostBlast) but not is_freezing, so froststeel doesn't shrug it off.
write(os.path.join(DATA, "damage_type/frost_blast.json"), OrderedDict([
    ("effects", "freezing"), ("exhaustion", 0.0), ("message_id", "tomyen.frost_blast"), ("scaling", "when_caused_by_living_non_player")]))
tag_add(os.path.join(VANILLA_DATA, "tags/damage_type/bypasses_shield.json"), ["tomyen:frost_blast"])


def advancement(name, criteria, category):
    crit = OrderedDict()
    for cname, item in criteria:
        crit[cname] = {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"}
    crit["has_the_recipe"] = {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", crit),
        ("requirements", [["has_the_recipe"] + [c for c, _ in criteria]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


# The Snow Globe: a dome of glass over the Frozen Heart, on a base of a snow block between two wooden slabs.
write(os.path.join(DATA, "recipe/snow_globe.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "misc"),
    ("key", OrderedDict([("G", "minecraft:glass"), ("H", "tomyen:frozen_heart"), ("S", "minecraft:snow_block"),
                         ("W", "#minecraft:wooden_slabs")])),
    ("pattern", ["GGG", "GHG", "WSW"]),
    ("result", OrderedDict([("count", 1), ("id", "tomyen:snow_globe")]))]))
advancement("snow_globe", [("has_frozen_heart", "tomyen:frozen_heart")], "misc")

# Glacial Froststeel: a froststeel piece and a Frozen Heart in the smithing table. No template: 26.3's
# smithing_transform takes its template as optional, and an empty template slot matches a recipe without one.
for piece in ARMOR_PIECES:
    name = f"glacial_froststeel_{piece}_smithing"
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:smithing_transform"),
        ("addition", "tomyen:frozen_heart"),
        ("base", f"tomyen:froststeel_{piece}"),
        ("result", {"id": f"tomyen:glacial_froststeel_{piece}"})]))
    advancement(name, [("has_frozen_heart", "tomyen:frozen_heart")], "combat")

for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
    tag_add(os.path.join(VANILLA_DATA, "tags/item", tag + ".json"), [f"tomyen:glacial_froststeel_{piece}"])
# It can't freeze, and walks on powder snow as a snow golem does.
tag_add(os.path.join(VANILLA_DATA, "tags/entity_type/freeze_immune_entity_types.json"), ["tomyen:snow_warden"])
tag_add(os.path.join(VANILLA_DATA, "tags/entity_type/powder_snow_walkable_mobs.json"), ["tomyen:snow_warden"])


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


def numbered(path, n, sep=""):
    return [f"{path}{sep}{i}" for i in range(1, n + 1)]


SOUNDS = OrderedDict([
    # A low, slow rumble: the warden's ambient groans dropped further, under a stray's rattle.
    event("snow_warden_ambient", vanilla(numbered("mob/warden/ambient", 12, "_"), 0.7) + vanilla(numbered("mob/stray/idle", 4), 0.5, 0.6)),
    event("snow_warden_hurt", vanilla(numbered("mob/warden/hurt", 4, "_"), 0.75) + vanilla(numbered("entity/snowman/hurt", 3), 0.5, 0.8)),
    event("snow_warden_death", vanilla(numbered("mob/warden/death", 2, "_"), 0.7) + vanilla(numbered("entity/snowman/death", 3), 0.45)),
    event("snow_warden_step", vanilla(numbered("mob/warden/step", 4, "_"), 0.8, 0.8) + vanilla(numbered("step/snow", 4), 0.6, 0.9),
          subtitle=False),
    event("snow_warden_sniff", vanilla(numbered("mob/warden/sniff", 4, "_"), 0.75)),
    event("snow_warden_roar", vanilla(numbered("mob/warden/roar", 5, "_"), 0.7, 1.4, attenuation_distance=48)),
    event("snow_warden_swing", vanilla(numbered("mob/warden/attack_impact", 2, "_"), 0.8, 1.2)),
    event("snow_warden_slam", vanilla(numbered("random/explode", 4), 0.6, 0.8) + vanilla(numbered("mob/warden/attack_impact", 2, "_"), 0.6, 1.4)
          + vanilla(numbered("block/powder_snow/break", 7), 0.5, 1.0)),
    event("snow_warden_blast_charge", vanilla(numbered("mob/warden/sonic_charge", 4), 0.8, 1.4, attenuation_distance=32)),
    event("snow_warden_blast", vanilla(numbered("mob/warden/sonic_boom", 4), 0.85, 2.0, attenuation_distance=40)
          + vanilla(numbered("random/glass", 3), 0.6, 1.2)),
    event("snow_warden_throw", vanilla(["mob/irongolem/throw"], 0.6, 1.2)),
    event("frost_boulder_burst", vanilla(numbered("block/powder_snow/break", 7), 0.6, 1.4) + vanilla(numbered("dig/snow", 4), 0.5, 1.2)),
    event("snow_globe_shake", vanilla(numbered("random/glass", 3), 1.8, 0.35) + vanilla(numbered("block/powder_snow/step", 10), 1.2, 0.8)),
    event("frost_thorns", vanilla(numbered("block/amethyst/break", 4), 1.4, 0.7)),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.tomyen.snow_warden", "Snow Warden"),
    ("entity.tomyen.frost_boulder", "Frost Boulder"),
    ("item.tomyen.snow_warden_spawn_egg", "Snow Warden Spawn Egg"),
    ("item.tomyen.frozen_heart", "Frozen Heart"),
    ("item.tomyen.frozen_heart.hint", "Hardens froststeel armor at a smithing table"),
    ("item.tomyen.snow_globe", "Snow Globe"),
    ("item.tomyen.snow_globe.hint", "Something stirs inside..."),
    ("item.tomyen.glacial_froststeel_helmet", "Glacial Froststeel Helmet"),
    ("item.tomyen.glacial_froststeel_chestplate", "Glacial Froststeel Chestplate"),
    ("item.tomyen.glacial_froststeel_leggings", "Glacial Froststeel Leggings"),
    ("item.tomyen.glacial_froststeel_boots", "Glacial Froststeel Boots"),
    ("item.tomyen.glacial_froststeel.counts", "Counts as froststeel for its set bonus"),
    ("item.tomyen.glacial_froststeel.thorns", "Frost thorns: melee attackers are slowed and frozen, more for each piece"),
    ("death.attack.tomyen.frost_blast", "%1$s was frozen solid"),
    ("death.attack.tomyen.frost_blast.player", "%1$s was frozen solid by %2$s"),
    ("subtitles.tomyen.snow_warden_ambient", "Snow Warden rumbles"),
    ("subtitles.tomyen.snow_warden_hurt", "Snow Warden cracks"),
    ("subtitles.tomyen.snow_warden_death", "Snow Warden crumbles"),
    ("subtitles.tomyen.snow_warden_sniff", "Snow Warden sniffs"),
    ("subtitles.tomyen.snow_warden_roar", "Snow Warden roars"),
    ("subtitles.tomyen.snow_warden_swing", "Snow Warden strikes"),
    ("subtitles.tomyen.snow_warden_slam", "Snow Warden slams the ground"),
    ("subtitles.tomyen.snow_warden_blast_charge", "Snow Warden draws in the cold"),
    ("subtitles.tomyen.snow_warden_blast", "Frost blast"),
    ("subtitles.tomyen.snow_warden_throw", "Snow Warden hurls a snowball"),
    ("subtitles.tomyen.frost_boulder_burst", "Snowball bursts"),
    ("subtitles.tomyen.snow_globe_shake", "Snow Globe shakes"),
    ("subtitles.tomyen.frost_thorns", "Frost bites"),
]))


# ---------------------------------------------------------------------------------------------- preview
def render(p, view, layers, scale=4, width=360, height=320, light=(0.35, -0.8, -0.5), focus=None, background=(46, 52, 64)):
    """Orthographic render. view: 'front', 'side' (from its right), 'three' (three-quarters), 'back'. layers: the base
    texture, then glow textures drawn over it (added, as the eyes render type does)."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "rthree": -math.pi / 4}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [rgba(background)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        ox, oy, ly = width * 0.5, height - 14, GROUND
    gy = int(oy + (GROUND - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    base, glows = layers[0], layers[1:]
    for verts, (ua, va, ub, vb) in quads(PARTS, p):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = (ub - ua), (vb - va)
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua + (i + 0.5) * du / nu), TEX_W - 1)
                tv = min(int(va + (j + 0.5) * dv / nv), TEX_H - 1)
                col = base.get(tu, tv)
                if col[3] == 0:
                    continue
                c = shade(col, lit)
                for g, k in glows:
                    gc = g.get(tu, tv)
                    if gc[3]:
                        c = tuple(min(255, int(c[q] * 0.5 + gc[q] * k)) for q in range(3))
                c = rgba(c)
                corners = []
                for (a_, b_) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s_, t_ = a_ / nu, b_ / nv
                    q = tuple(A[k_] + e1[k_] * s_ + e2[k_] * t_ for k_ in range(3))
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
    k = 5
    allsprites = list(SPRITES.values()) + list(GLACIAL_SPRITES.values())
    for n, canvas in enumerate(allsprites):
        bx, by = 10 + (n % 4) * (16 * k + 8), 14 + (n // 4) * (16 * k + 16)
        for y in range(16 * k):
            for x in range(16 * k):
                pxl = canvas.get(x // k, y // k)
                checker = (56, 62, 74, 255) if ((x // 10) + (y // 10)) % 2 else (50, 56, 68, 255)
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
    L = [TEXTURE, (FACE_GLOW, 0.9), (CRYSTAL_GLOW, 0.6)]
    HOT = [TEXTURE, (FACE_GLOW, 0.9), (CRYSTAL_GLOW, 1.0)]
    P = lambda **kw: pose(PARTS, **kw)
    only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
    views = [
        ("front", lambda: render(P(), "front", L)),
        ("three", lambda: render(P(age=10), "three", L)),
        ("side", lambda: render(P(), "side", L)),
        ("back", lambda: render(P(), "back", L)),
        ("walk", lambda: render(P(walk=2.2, speed=0.9, age=8), "three", L)),
        ("swing up", lambda: render(P(swing=0.4), "side", L)),
        ("swing down", lambda: render(P(swing=0.6), "three", L)),
        ("slam up", lambda: render(P(slam=0.5), "three", L)),
        ("slam down", lambda: render(P(slam=0.6), "side", L)),
        ("charge", lambda: render(P(charge=1.0, age=12), "three", HOT)),
        ("blast", lambda: render(P(charge=0.8, blast=0.2), "side", HOT)),
        ("throw", lambda: render(P(throw=0.55), "rthree", L)),
        ("roar", lambda: render(P(roar=0.5, age=20), "front", L)),
        ("sniff", lambda: render(P(sniff=1.0, age=8), "three", L)),
        ("head", lambda: render(P(), "three", L, scale=10, focus=(0.0, -21.0, -10.0))),
        ("chest", lambda: render(P(), "front", HOT, scale=9, focus=(0.0, -11.0, -8.0))),
        ("sprites", lambda: sprites_panel(360, 320)),
    ]
    imgs = [fn() for name, fn in views if not only or name in only]
    out, bw, bh = board(imgs, 360, 320, 4)
    png(os.path.join(PREVIEW, "preview_snow_warden.png"), bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
