"""Builds the Shrimp with a Gun: its model (the cube layout in PARTS, written into client/shrimp/ShrimpModel.java between
the GENERATED markers) and entity texture; its spawn egg, Raw Shrimp and Cooked Shrimp sprites; and the Shrimp Pistol's
3D item model (its own atlas, the gun at rest plus six frames of the slide drawn back, chosen by tomyen:gun_animation,
and the muzzle flash layers), whose first-person transform, grip and loading points are printed for
client/shrimp/ShrimpClient.java. On the data side it writes the shrimp's loot table, the cooking recipes (furnace, smoker
and campfire) with recipe-book advancements and the tomyen:saltwater_fish item tag (the fish shrimp bait draws in). Its
sounds (vanilla silverfish, guardian, turtle and crossbow sounds re-pitched, and the pipe pistol's recordings pitched up
for its gun) and lang entries are merged into sounds.json and en_us.json.

The creature: a mantis shrimp the size of a man, standing up on four spindly walking legs with its tail curled down
behind it onto the ground as a prop; a plated abdomen of three segments rising into a broad carapace with a keel down the
back; a small head with a pointed rostrum, mouthparts, two long antennae, a pair of paddle-like antennal scales and two
big striped eyes on stalks that swivel independently; and two raptorial claws (a heavy merus, a thin spiny propodus,
the club and the folding hook of the dactyl) gripping a stubby pistol: a blued slide over a dark frame, a brass muzzle
ring, loading gate and trigger guard, and a bleached driftwood grip.

Texture: smooth ramps of a pink-orange shell (lighter on top and on the belly plates, a pale rim along every segment's
edge, darker underneath), red-orange antennae darkening to the tips, teal eyes with a pale midband, gunmetal, brass and
driftwood for the gun. No noise or dithering; a rerun writes the same files.

The model is checked for z-fighting at rest and in the aiming pose (tools/coplanar.py); the item model with
tools/zfight.py's check (via gunkit) and coplanar.py. With --preview DIR it renders preview_shrimp.png (front, side,
three-quarters and back at rest; walking; aiming; the kick of a shot; the reload; the claw snap; and the head and the gun
up close), preview_shrimp_texture.png and preview_shrimp_items.png, using the same cubes, pivots and UV layout as the
Java model and a Python twin of its animation.

Usage: python3 tools/gen_shrimp.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


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
    """A smooth colour from a list, t in 0..1 (no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


# ---------------------------------------------------------------------------------------------- palette
# The shell, light to dark: a cooked-prawn pink running into a deeper red-orange.
SHELL = [(255, 214, 190), (250, 178, 148), (240, 144, 112), (222, 112, 86), (184, 82, 66), (136, 54, 50)]
EDGE = (255, 234, 216)          # the palest shell: tips, strike faces
RIM = (252, 206, 182)           # the pale rim along each segment's lower edge
BELLY = [(255, 226, 206), (246, 196, 170), (230, 166, 140)]
JOINT = (150, 70, 62)           # the soft membrane between leg segments
ANTENNA = [(246, 128, 86), (214, 84, 58), (160, 52, 44)]   # root to tip
EYE = [(150, 226, 204), (96, 186, 168), (56, 128, 124), (30, 74, 80)]
MIDBAND = (236, 230, 160)       # the mantis shrimp's band of pale rows across the middle of each eye
PUPIL = (18, 30, 36)
MOUTH = (96, 40, 40)
# The gun: blued gunmetal, brass fittings, a bleached driftwood grip.
GUNMETAL = [(152, 160, 172), (116, 124, 136), (86, 92, 104), (62, 66, 76), (40, 42, 50)]
BRASS = [(246, 224, 146), (216, 180, 86), (172, 136, 60), (124, 94, 40)]
WOOD = [(190, 160, 120), (160, 130, 94), (130, 102, 72), (98, 76, 54)]
BORE = (14, 14, 18)

TEX_W, TEX_H = 128, 128


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out. 'top' and
    'bottom' are as seen in the world (the model is upside down); in both the front edge is the last row. In 'right'
    (the cube's -x side, the creature's right) the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, uv, size, painters):
    """painters: face name -> f(x, y, fw, fh) -> colour (or None for clear)."""
    u, v = uv
    w, h, d = size
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name)
        if f is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = f(x, y, fw, fh)
                canvas.set(fu + x, fv + y, rgba(c) if c is not None and len(c) == 3 else (c or CLEAR))


# ---------------------------------------------------------------------------------------------- linear algebra
def rot_matrix(rx, ry, rz):
    # Rz * Ry * Rx, as ModelPart (Quaternionf.rotationZYX).
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def vsub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def vadd(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def dist(a, b):
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored). Mirrored cubes
# share their texture with the unmirrored one of the same key. Model space is the game's: y down, the front toward -z,
# feet at y 24. This is the one place the shape is defined: it's written into ShrimpModel.java, painted here and drawn
# by the preview. Rest rotations that make two things meet (the claws on the gun, the feet on the ground) are solved
# below rather than typed in.
SIDES = (("right", -1, False), ("left", 1, True))

LEAN_HIPS, LEAN_ABDOMEN, LEAN_THORAX = 0.0, 0.14, 0.16
LEAN = LEAN_HIPS + LEAN_ABDOMEN + LEAN_THORAX
# The tail curls down and back from the hips to lie on the ground: each piece turns against the last.
TAIL = (-1.05, 0.45, 0.62)
# Where the arms hang from (thorax space), and the gun from the arms (arms space): the grip's top, at chest height,
# ahead of the right shoulder.
ARMS_PIVOT = (0.0, -4.5, -3.5)
GUN_PIVOT = (-1.2, 7.4, -5.6)
SHOULDER_X = 5.6
# Where each claw holds the gun (gun space): the right club round the grip, the left hook over the front of the frame.
RIGHT_HOLD = (-1.9, 2.6, 1.2)
LEFT_HOLD = (1.9, -1.4, -4.8)
UPPER_LEN, FORE_LEN = 7.0, 7.0
# Legs: hip joints (world, at rest), thigh rotations, the shin's length, and where each foot comes down.
LEGS = {"front": ((3.0, 13.2, -1.6), (-0.55, 1.05), 9), "back": ((3.0, 13.2, 1.8), (0.55, 1.05), 9)}
FEET = {"front": (8.4, 24.0, -6.0), "back": (8.4, 24.0, 6.2)}
THIGH_LEN = 5


def solve(chain_end, rest, ranges, target, weight=None):
    """Coarse-to-fine grid search for the rotations (in ranges) that bring chain_end(rotations) closest to target."""
    best = None
    steps = [0.1, 0.025, 0.006]
    centre = rest
    span = [(lo, hi) for lo, hi in ranges]
    for k, step in enumerate(steps):
        axes = []
        for (lo, hi), c in zip(span, centre):
            if k:
                lo, hi = max(lo, c - steps[k - 1] * 2), min(hi, c + steps[k - 1] * 2)
            n = int(round((hi - lo) / step))
            axes.append([lo + i * (hi - lo) / max(1, n) for i in range(n + 1)])
        grid = [[]]
        for ax in axes:
            grid = [g + [v] for g in grid for v in ax]
        for g in grid:
            err = dist(chain_end(g), target) + (weight(g) if weight else 0.0)
            if best is None or err < best[0]:
                best = (err, g)
        centre = best[1]
    return best[1], dist(chain_end(best[1]), target)


def parts(arm=None, leg=None):
    arm = arm or {"right": (-1.0, 0.0, 0.3, -1.2), "left": (-1.0, 0.0, -0.3, -1.2)}
    leg = leg or {k: (0.0, 0.0) for k in ("right_front", "left_front", "right_back", "left_back")}
    t1, t2, t3 = TAIL
    p = [
        ("hips", None, (0.0, 12.0, 0.5), (LEAN_HIPS, 0.0, 0.0), [
            ("hips", (-4.0, -2.0, -3.5), (8, 4, 7), False)]),
        ("tail", "hips", (0.0, 1.0, 2.5), (t1, 0.0, 0.0), [
            ("tail", (-3.0, -1.5, 0.0), (6, 3, 5), False)]),
        ("tail_end", "tail", (0.0, 0.0, 4.5), (t2, 0.0, 0.0), [
            ("tail_end", (-2.5, -1.2, 0.0), (5, 2, 4), False)]),
        ("fan", "tail_end", (0.0, 0.0, 3.6), (t3, 0.0, 0.0), [
            ("telson", (-1.5, -0.5, 0.0), (3, 1, 6), False)]),
        ("abdomen", "hips", (0.0, -2.0, 0.0), (LEAN_ABDOMEN, 0.0, 0.0), [
            ("segment_low", (-4.5, -4.5, -4.0), (9, 5, 8), False),
            ("segment_high", (-5.0, -8.5, -4.5), (10, 5, 9), False)]),
        ("swimmeret_low", "abdomen", (0.0, -0.3, -4.0), (-0.35, 0.0, 0.0), [
            ("swimmeret", (-3.0, 0.0, -1.0), (6, 3, 1), False)]),
        ("swimmeret_high", "abdomen", (0.0, -4.3, -4.5), (-0.35, 0.0, 0.0), [
            ("swimmeret", (-3.0, 0.0, -1.0), (6, 3, 1), False)]),
        ("thorax", "abdomen", (0.0, -8.0, 0.0), (LEAN_THORAX, 0.0, 0.0), [
            ("carapace", (-5.5, -7.0, -5.0), (11, 7, 10), False),
            ("keel", (-1.0, -7.5, 3.5), (2, 7, 2), False)]),
        ("head", "thorax", (0.0, -7.0, -2.5), (0.0, 0.0, 0.0), [
            ("head", (-3.5, -3.0, -4.0), (7, 3, 6), False),
            ("rostrum", (-1.0, -2.6, -6.0), (2, 2, 2), False),
            ("mouth", (-2.0, 0.0, -4.0), (4, 2, 1), False)]),
        ("arms", "thorax", ARMS_PIVOT, (-LEAN, 0.0, 0.0), []),
        ("gun", "arms", GUN_PIVOT, (0.0, 0.0, 0.0), [
            ("frame", (-1.0, -2.0, -6.0), (2, 2, 7), False),
            ("barrel", (-1.0, -4.0, -10.0), (2, 2, 5), False),
            ("muzzle", (-1.5, -4.5, -10.6), (3, 3, 1), False),
            ("front_sight", (-0.5, -5.5, -10.4), (1, 1, 1), False),
            ("guard", (-0.5, 1.0, -4.0), (1, 1, 3), False),
            ("guard_post", (-0.4, -0.3, -4.6), (1, 2, 1), False),
            ("trigger", (-0.5, 0.0, -2.6), (1, 1, 1), False)]),
        ("slide", "gun", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [
            ("slide", (-1.5, -4.5, -5.5), (3, 3, 7), False),
            ("gate", (-1.0, -5.0, -3.4), (2, 1, 2), False),
            ("rear_sight", (-1.0, -5.4, 0.4), (2, 1, 1), False),
            ("hammer", (-0.5, -4.0, 1.5), (1, 2, 1), False)]),
        ("grip", "gun", (0.0, -0.2, 0.6), (0.32, 0.0, 0.0), [
            ("grip", (-1.5, 0.0, -1.5), (3, 6, 3), False)]),
    ]
    for side, s, mirror in SIDES:
        ax, ay, az, fx = arm[side]
        p += [
            (side + "_eye_stalk", "head", (s * 2.0, -2.8, -3.0), (0.15, 0.0, s * 0.25), [
                ("eye_stalk", (-0.5, -3.0, -0.5), (1, 3, 1), mirror)]),
            (side + "_eye", side + "_eye_stalk", (0.0, -3.0, 0.0), (-0.15, 0.0, -s * 0.15), [
                ("eye", (-1.5, -4.0, -1.5), (3, 4, 3), mirror)]),
            (side + "_antenna", "head", (s * 1.0, -2.0, -4.2), (0.42, 0.0, s * 0.42), [
                ("antenna", (-0.5, -14.0, -0.5), (1, 14, 1), mirror)]),
            (side + "_scale", "head", (s * 2.6, -1.2, -3.6), (0.25, -s * 0.45, 0.0), [
                ("scale", (-1.0, -0.5, -4.0), (2, 1, 4), mirror)]),
            (side + "_arm", "arms", (s * SHOULDER_X, 0.0, 0.0), (ax, ay, az), [
                ("upper_arm", (-1.5, -1.0, -1.5), (3, 8, 3), mirror)]),
            (side + "_fore", side + "_arm", (0.0, UPPER_LEN, 0.0), (fx, 0.0, 0.0), [
                ("fore", (-1.0, 0.0, -1.0), (2, 7, 2), mirror)]),
            (side + "_club", side + "_fore", (0.0, FORE_LEN, 0.0), (0.0, 0.0, 0.0), [
                ("club", (-1.5, -1.2, -1.5), (3, 3, 3), mirror)]),
            (side + "_dactyl", side + "_fore", (0.0, FORE_LEN - 0.5, -1.4), (-0.18, 0.0, 0.0), [
                ("dactyl", (-0.5, -5.5, -0.5), (1, 5, 1), mirror)]),
        ]
        for end in ("front", "back"):
            (hx, hy, hz), (lx, lz), shin = LEGS[end]
            rx, rz = leg[side + "_" + end]
            p += [
                (f"{side}_{end}_leg", None, (s * hx, hy, hz), (lx, 0.0, -s * lz), [
                    ("thigh", (-1.0, -0.5, -1.0), (2, THIGH_LEN + 1, 2), mirror)]),
                (f"{side}_{end}_shin", f"{side}_{end}_leg", (0.0, THIGH_LEN, 0.0), (rx, 0.0, rz), [
                    ("shin", (-0.5, 0.0, -0.5), (1, shin, 1), mirror)]),
            ]
        p += [
            (side + "_uropod", "fan", (s * 1.2, 0.0, 0.6), (0.0, s * 0.42, 0.0), [
                ("uropod", (-1.0, -0.4, 0.0), (2, 1, 5), mirror)]),
            (side + "_outer_uropod", "fan", (s * 1.6, 0.0, 0.2), (0.0, s * 0.95, 0.0), [
                ("outer_uropod", (-1.0, -0.3, 0.0), (2, 1, 4), mirror)]),
        ]
    return p


def tree(parts_):
    t = {}
    for name, parent, *_ in parts_:
        t.setdefault(parent or "root", []).append(name)
    return t


def world(parts_, part, local, pose=None):
    """Where a point given in a part's own space is in model space (pose: name -> [pivot, rot], default the rest)."""
    info = {name: (parent, pivot, rot) for name, parent, pivot, rot, _ in parts_}
    chain = []
    while part:
        chain.append(part)
        part = info[part][0]
    m, o = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0)
    for name in reversed(chain):
        pivot, rot = pose[name] if pose else info[name][1:]
        o = vadd(o, apply(m, pivot))
        m = mul(m, rot_matrix(*rot))
    return vadd(o, apply(m, local))


def solve_rig():
    """The claws' rest rotations that put them on the gun, and the shins' that put the feet on the ground."""
    base = parts()
    arm, leg = {}, {}
    for side, s, _ in SIDES:
        hold = RIGHT_HOLD if s < 0 else LEFT_HOLD
        target = world(base, "gun", hold)

        def tip(g, side=side):
            trial = parts(arm={**{"right": (-1, 0, 0, -1), "left": (-1, 0, 0, -1)}, side: (g[0], 0.0, g[1], g[2])})
            return world(trial, side + "_fore", (0.0, FORE_LEN, 0.0))
        # Elbows out and down, not tucked in: a little cost on rolling the arm inward.
        g, err = solve(tip, (-1.0, s * -0.3, -1.2), [(-2.6, 0.4), (-1.2, 1.2), (-2.6, 0.0)], target,
                       lambda g, s=s: 0.4 * max(0.0, s * g[1]))
        arm[side] = (round(g[0], 3), 0.0, round(g[1], 3), round(g[2], 3))
        print(f"  {side} claw: rotations {arm[side]}, misses its hold by {err:.2f} px")
        for end in ("front", "back"):
            fx, fy, fz = FEET[end]
            foot = (s * fx, fy, fz)

            def toe(g, side=side, end=end):
                trial = parts(leg={**{k: (0.0, 0.0) for k in ("right_front", "left_front", "right_back", "left_back")},
                                   side + "_" + end: (g[0], g[1])})
                return world(trial, f"{side}_{end}_shin", (0.0, LEGS[end][2], 0.0))
            g, err = solve(toe, (0.0, s * 0.8), [(-1.6, 1.6), (-2.4, 2.4)], foot)
            leg[side + "_" + end] = (round(g[0], 3), round(g[1], 3))
            print(f"  {side} {end} leg: shin {leg[side + '_' + end]}, foot misses by {err:.2f} px")
    return arm, leg


print("solving the rig:")
ARM, LEG = solve_rig()
PARTS = parts(ARM, LEG)
TREE = tree(PARTS)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS}


def pack():
    """Texture offsets for each cube key, shelf-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS:
        for key, _, size, _ in cubes:
            assert sizes.setdefault(key, size) == size, key
    order = sorted(sizes, key=lambda k: (-(sizes[k][2] + sizes[k][1]), k))
    offs, x, y, shelf = {}, 0, 0, 0
    for k in order:
        w, h, d = sizes[k]
        fw, fh = 2 * (w + d), d + h
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        offs[k] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= TEX_H, "texture too small"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


def cube_count():
    return sum(len(c) for _, _, _, _, c in PARTS)


# ---------------------------------------------------------------------------------------------- painting
def shell_side(top=0.1, bottom=0.65, rim=True):
    """A shell face: lighter where the light catches its upper edge, a smooth ramp down, and a pale rim along its lower
    edge (where each plate overlaps the next)."""
    def f(x, y, w, h):
        if rim and h > 2 and y == h - 1:
            return RIM
        return ramp(SHELL, top + (bottom - top) * (y / max(1, h - 1)))
    return f


def flat(c):
    return lambda x, y, w, h: c


def belly(x, y, w, h):
    """A pale belly plate down the front: lighter in the middle, the pale rim along the bottom."""
    if y == h - 1:
        return RIM
    middle = abs(x - (w - 1) / 2) / max(1.0, (w - 1) / 2)
    return ramp(BELLY, 0.15 + 0.5 * middle + 0.35 * y / max(1, h - 1))


def banded(x, y, w, h, period=3, shade=0.3):
    """A leg segment: shell banded by dark joints every few texels, lighter toward the root."""
    if y % period == period - 1:
        return JOINT
    return ramp(SHELL, shade + 0.4 * y / max(1, h - 1))


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    P = lambda key, painters: paint(tex, *CUBES[key], painters)
    under = flat(SHELL[4])

    # Hips: the first abdominal segment, the legs' roots under it.
    P("hips", {"top": flat(SHELL[2]), "bottom": under, "front": belly, "back": shell_side(0.2, 0.7),
               "right": shell_side(0.2, 0.7), "left": shell_side(0.2, 0.7)})

    # The abdomen's two segments: belly plates down the front, the shell darker toward the back.
    for key in ("segment_low", "segment_high"):
        P(key, {"top": flat(SHELL[1]), "bottom": under, "front": belly,
                "back": shell_side(0.25, 0.75), "right": shell_side(0.1, 0.6), "left": shell_side(0.1, 0.6)})

    # Swimmerets: little fringed paddles, open along the bottom edge.
    def fringe(x, y, w, h):
        if y == h - 1 and x % 2 == 1:
            return None
        return ramp(BELLY, 0.3 + 0.6 * y / max(1, h - 1))
    P("swimmeret", {"front": fringe, "back": fringe, "right": fringe, "left": fringe, "top": flat(BELLY[1]),
                    "bottom": flat(BELLY[2])})

    # The carapace: the broad shield, a darker groove down its middle on top, a pale lateral line on the flanks.
    def carapace_top(x, y, w, h):
        middle = abs(x - (w - 1) / 2)
        if middle < 0.6:
            return SHELL[3]
        return ramp(SHELL, 0.05 + 0.1 * middle / ((w - 1) / 2) + 0.15 * (1 - y / max(1, h - 1)))

    def carapace_flank(x, y, w, h):
        if y == h - 1:
            return RIM
        return ramp(SHELL, 0.1 + 0.55 * y / max(1, h - 1))

    def carapace_front(x, y, w, h):
        if y == h - 1:
            return RIM
        middle = abs(x - (w - 1) / 2) / ((w - 1) / 2)
        return ramp(SHELL, 0.12 + 0.3 * middle + 0.3 * y / max(1, h - 1))
    P("carapace", {"top": carapace_top, "bottom": under, "front": carapace_front, "back": shell_side(0.3, 0.8),
                   "right": carapace_flank, "left": carapace_flank})
    P("keel", {"top": flat(SHELL[2]), "bottom": under, "front": shell_side(0.4, 0.8, False),
               "back": shell_side(0.45, 0.85, False), "right": shell_side(0.35, 0.8, False), "left": shell_side(0.35, 0.8, False)})

    # Tail: two segments darkening to the fan; the fan pale-edged with a dark band across each blade.
    for key, t in (("tail", 0.2), ("tail_end", 0.3)):
        P(key, {"top": lambda x, y, w, h, t=t: ramp(SHELL, t + 0.2 * y / max(1, h - 1)), "bottom": flat(BELLY[2]),
                "front": shell_side(t, t + 0.4), "back": shell_side(t, t + 0.4),
                "right": shell_side(t, t + 0.4), "left": shell_side(t, t + 0.4)})

    def blade(x, y, w, h):
        # Top of a tail blade: along its length (rows), a dark band two thirds of the way out, the tip pale.
        if y == 0:
            return EDGE
        if y == 2:
            return SHELL[4]
        return ramp(SHELL, 0.2 + 0.25 * (1 - y / max(1, h - 1)))
    for key in ("telson", "uropod", "outer_uropod"):
        P(key, {"top": blade, "bottom": flat(BELLY[2]), "front": flat(SHELL[3]), "back": flat(EDGE),
                "right": flat(SHELL[3]), "left": flat(SHELL[3])})

    # Head: a small plate, the rostrum pointed and pale-tipped, the mouthparts dark and feathery.
    P("head", {"top": lambda x, y, w, h: ramp(SHELL, 0.05 + 0.25 * abs(x - (w - 1) / 2) / ((w - 1) / 2)),
               "bottom": under, "front": carapace_front, "back": shell_side(0.2, 0.6),
               "right": shell_side(0.1, 0.55), "left": shell_side(0.1, 0.55)})
    P("rostrum", {"top": flat(SHELL[1]), "bottom": under, "front": flat(EDGE), "back": flat(SHELL[2]),
                  "right": flat(SHELL[2]), "left": flat(SHELL[2])})

    def mouth(x, y, w, h):
        # Feeding legs: dark stripes hanging down, the gaps between them darker still.
        return MOUTH if x % 2 else lerp(JOINT, SHELL[3], 0.3 + 0.4 * y / max(1, h - 1))
    P("mouth", {k: mouth for k in ("front", "back", "right", "left", "top", "bottom")})

    # Eyes: on banded stalks; each eye teal with a pale midband and a dark pseudopupil looking out of it.
    P("eye_stalk", {k: (lambda x, y, w, h: banded(x, y, w, h, 2, 0.2)) for k in ("front", "back", "right", "left")}
      | {"top": flat(SHELL[1]), "bottom": flat(SHELL[3])})

    def eye_side(pupil):
        def f(x, y, w, h):
            if pupil and x == w // 2 and y in (0, 3):
                return PUPIL
            if y == 1:
                return MIDBAND
            if y == 2:
                return lerp(MIDBAND, EYE[1], 0.35)
            return ramp(EYE, 0.1 + 0.6 * abs(y - 1.5) / 2.5 + (0.25 if y == 3 else 0.0))
        return f
    P("eye", {"front": eye_side(True), "back": eye_side(False), "right": eye_side(False), "left": eye_side(False),
              "top": flat(EYE[0]), "bottom": flat(EYE[3])})

    # Antennae: red-orange, darkening to the tips, ringed every few texels; scales pale paddles with a fringed edge.
    def antenna(x, y, w, h):
        t = 1 - y / max(1, h - 1)
        c = ramp(ANTENNA, t)
        return lerp(c, (0, 0, 0), 0.18) if y % 3 == 0 else c
    P("antenna", {k: antenna for k in ("front", "back", "right", "left")} | {"top": flat(ANTENNA[2]),
                                                                              "bottom": flat(ANTENNA[0])})

    def scale_top(x, y, w, h):
        return RIM if y == h - 1 else ramp(SHELL, 0.2 + 0.3 * (1 - y / max(1, h - 1)))
    P("scale", {"top": scale_top, "bottom": flat(SHELL[3]), "front": flat(RIM), "back": flat(SHELL[3]),
                "right": flat(SHELL[2]), "left": flat(SHELL[2])})

    # Claws: a heavy merus, light on top; a thin propodus with a row of pale spines down its front; the club, deep
    # red-orange with a pale strike face; the dactyl's hook, pale at the point.
    P("upper_arm", {"top": flat(SHELL[1]), "bottom": flat(SHELL[4]), "front": shell_side(0.1, 0.55),
                    "back": shell_side(0.3, 0.8), "right": shell_side(0.15, 0.65), "left": shell_side(0.15, 0.65)})

    def fore_front(x, y, w, h):
        return EDGE if y % 2 == 0 and x == 0 else ramp(SHELL, 0.15 + 0.35 * y / max(1, h - 1))
    P("fore", {"top": flat(SHELL[1]), "bottom": flat(SHELL[3]), "front": fore_front,
               "back": shell_side(0.35, 0.75, False), "right": shell_side(0.2, 0.6, False), "left": shell_side(0.2, 0.6, False)})

    def club_side(x, y, w, h):
        return ramp(SHELL, 0.45 + 0.4 * y / max(1, h - 1))
    P("club", {"front": lambda x, y, w, h: EDGE if 0 < x < w - 1 and 0 < y < h - 1 else SHELL[3],
               "back": club_side, "right": club_side, "left": club_side, "top": flat(SHELL[3]), "bottom": flat(EDGE)})

    def hook(x, y, w, h):
        return EDGE if y == 0 else ramp(SHELL, 0.25 + 0.5 * y / max(1, h - 1))
    P("dactyl", {k: hook for k in ("front", "back", "right", "left")} | {"top": flat(EDGE), "bottom": flat(SHELL[3])})

    # Legs: spindly, banded at the joints, darkening toward the feet.
    P("thigh", {k: (lambda x, y, w, h: banded(x, y, w, h, 3, 0.15)) for k in ("front", "back", "right", "left")}
      | {"top": flat(SHELL[2]), "bottom": flat(SHELL[3])})
    P("shin", {k: (lambda x, y, w, h: JOINT if y % 4 == 3 else ramp(SHELL, 0.3 + 0.6 * y / max(1, h - 1)))
               for k in ("front", "back", "right", "left")} | {"top": flat(SHELL[2]), "bottom": flat(SHELL[5])})

    # ------------------------------------------------------------------------------------------ the gun
    def metal_side(cols=GUNMETAL, top=0.0, bottom=0.75):
        def f(x, y, w, h):
            if y == 0:
                return cols[0]
            return ramp(cols, top + 0.2 + (bottom - top - 0.2) * y / max(1, h - 1))
        return f

    def metal_top(cols=GUNMETAL):
        return lambda x, y, w, h: ramp(cols, 0.15 + 0.15 * abs(x - (w - 1) / 2) / max(1.0, (w - 1) / 2))

    def slide_side(x, y, w, h):
        # Serrations at the back of the slide (the last columns, the back edge being... either end: both flanks), a
        # highlight along the top edge.
        if y == 0:
            return GUNMETAL[0]
        if y == h - 1:
            return GUNMETAL[3]
        return ramp(GUNMETAL, 0.3 + 0.2 * y / max(1, h - 1))

    def slide_flank(back_first):
        def f(x, y, w, h):
            back = x if back_first else w - 1 - x
            if 0 < y < h - 1 and back < 3 and back % 2 == 0:
                return GUNMETAL[4]
            return slide_side(x, y, w, h)
        return f
    # In 'right' the front edge is the last column (the back the first), in 'left' the first.
    P("slide", {"top": metal_top(), "bottom": flat(GUNMETAL[4]), "front": flat(GUNMETAL[2]),
                "back": flat(GUNMETAL[3]), "right": slide_flank(True), "left": slide_flank(False)})
    P("frame", {"top": flat(GUNMETAL[3]), "bottom": flat(GUNMETAL[4]), "front": flat(GUNMETAL[3]),
                "back": flat(GUNMETAL[3]), "right": metal_side(GUNMETAL, 0.4, 0.95), "left": metal_side(GUNMETAL, 0.4, 0.95)})
    P("barrel", {"top": metal_top(), "bottom": flat(GUNMETAL[3]), "front": flat(BORE), "back": flat(GUNMETAL[3]),
                 "right": metal_side(), "left": metal_side()})

    def muzzle_front(x, y, w, h):
        return BORE if x == 1 and y == 1 else BRASS[1]
    P("muzzle", {"front": muzzle_front, "back": flat(BRASS[2]), "top": flat(BRASS[0]), "bottom": flat(BRASS[3]),
                 "right": metal_side(BRASS), "left": metal_side(BRASS)})
    P("front_sight", {k: flat(GUNMETAL[1]) for k in ("front", "back", "right", "left")} | {"top": flat(GUNMETAL[0]),
                                                                                            "bottom": flat(GUNMETAL[3])})
    P("rear_sight", {"front": flat(GUNMETAL[2]), "back": flat(GUNMETAL[2]), "top": flat(GUNMETAL[1]),
                     "bottom": flat(GUNMETAL[3]), "right": flat(GUNMETAL[2]), "left": flat(GUNMETAL[2])})
    P("gate", {"top": lambda x, y, w, h: BRASS[0] if y == 0 else BRASS[1], "bottom": flat(BRASS[3]),
               "front": flat(BRASS[2]), "back": flat(BRASS[2]), "right": flat(BRASS[2]), "left": flat(BRASS[2])})
    P("hammer", {k: flat(GUNMETAL[2]) for k in ("front", "back", "right", "left")} | {"top": flat(GUNMETAL[1]),
                                                                                       "bottom": flat(GUNMETAL[3])})
    for key in ("guard", "guard_post"):
        P(key, {k: metal_side(BRASS, 0.2, 0.8) for k in ("front", "back", "right", "left")}
          | {"top": flat(BRASS[1]), "bottom": flat(BRASS[3])})
    P("trigger", {k: flat(GUNMETAL[1]) for k in ("front", "back", "right", "left", "top", "bottom")})

    def grain(x, y, w, h):
        # Driftwood: grain lines down its length, a darker line every few texels, lighter on the upper part.
        base = ramp(WOOD, 0.1 + 0.5 * y / max(1, h - 1))
        if y == 0:
            return WOOD[0]
        return lerp(base, WOOD[3], 0.5) if x == 1 or (x == w - 1 and y > h // 2) else base
    P("grip", {"front": grain, "back": grain, "right": grain, "left": grain, "top": flat(WOOD[1]),
               "bottom": lambda x, y, w, h: BRASS[2] if x in (0, w - 1) or y in (0, h - 1) else WOOD[2]})

    tex.save("entity/shrimp/shrimp.png")
    return tex


TEXTURE = paint_entity()


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "shrimp", "ShrimpModel.java"))


def fj(v):
    return f"{v:.3f}f".replace("-0.000f", "0.000f")


def java_layer():
    """createBodyLayer's body, from PARTS."""
    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in PARTS:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size, mirror in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}){'.mirror()' if mirror else ''}.addBox({', '.join(fj(c) for c in frm)}, " \
                       f"{', '.join(fj(c) for c in size)}){'.mirror(false)' if mirror else ''}"
        pose = (f"PartPose.offsetAndRotation({', '.join(fj(c) for c in pivot)}, {', '.join(fj(c) for c in rot)})"
                if any(rot) else f"PartPose.offset({', '.join(fj(c) for c in pivot)})")
        used = any(p == name for _, p, *_ in PARTS)
        decl = f"PartDefinition {var} = " if used else ""
        lines.append(f"\t\t{decl}{('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", {builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_shrimp.py\n", "\t\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- posing
RELOAD_TICKS = 70
ROUND_EVERY = 12
ROUNDS = 4
# The gun hangs this far below level when it isn't aimed.
LOW = 0.45


def rest_pose():
    return {name: [pivot, rot] for name, _, pivot, rot, _ in PARTS}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


def ramp01(v, a, b):
    """0 before a, 1 after b, smooth between."""
    t = max(0.0, min(1.0, (v - a) / (b - a)))
    return t * t * (3 - 2 * t)


def reload_curves(reload):
    """(tilt, dip, press, rack) through a reload: the gun tipped over to show its gate, the left claw dipping to its belly
    for each round and coming back up to press it in (press peaks as each goes in), and the slide drawn back at the end."""
    if reload <= 0.0:
        return 0.0, 0.0, 0.0, 0.0
    e = reload * RELOAD_TICKS
    tilt = ramp01(e, 0, 6) * (1 - ramp01(e, 60, 68))
    last = ROUND_EVERY * ROUNDS
    c = ((e - 6) % ROUND_EVERY) / ROUND_EVERY
    dip = math.sin(math.pi * c) ** 2 * ramp01(e, 0, 4) * (1 - ramp01(e, last, last + 6))
    press = max(0.0, 1 - abs(((e - 6 + ROUND_EVERY / 2) % ROUND_EVERY) - ROUND_EVERY / 2) / 2.0) if e < last + 2 else 0.0
    rack = ramp01(e, 52, 58) * (1 - ramp01(e, 59, 61))
    return tilt, dip, press, rack


def snap_open(attack):
    """How far the dactyl is flung open through a snap: out as the claw winds up, shut as it strikes."""
    return attack / 0.35 if attack < 0.35 else max(0.0, 1.0 - (attack - 0.35) / 0.12)


def animate(pose, walk=0.0, speed=0.0, age=0.0, yaw=0.0, pitch=0.0, swim=0.0, aim=0.0, recoil=0.0, reload=0.0, attack=0.0):
    """Python twin of ShrimpModel.setupAnim, for the preview. yaw and pitch in degrees, as the render state has them."""
    yaw = max(-50.0, min(50.0, yaw)) * math.pi / 180
    pitch = max(-45.0, min(45.0, pitch)) * math.pi / 180
    phase = walk * 0.9
    # Four spindly legs in diagonal pairs (right front with left back), each lifted outward as it swings forward;
    # in the water they paddle.
    for k, (leg, s, front) in enumerate((("right_front", -1, True), ("left_front", 1, True), ("right_back", -1, False),
                                         ("left_back", 1, False))):
        offset = 0.0 if front == (s < 0) else math.pi
        swing = math.sin(phase + offset) * speed
        lift = max(0.0, math.cos(phase + offset)) * speed
        add(pose, leg + "_leg", dx=0.55 * swing + swim * 0.45 * math.sin(age * 0.5 + k * 1.3), dz=-s * 0.35 * lift)
        add(pose, leg + "_shin", dz=s * 0.25 * lift)
    add(pose, "hips", dx=0.3 * swim, dz=0.05 * math.sin(phase) * speed, pos=(0, abs(math.sin(phase)) * 0.6 * speed, 0))
    add(pose, "tail", dx=swim * 0.35 * math.sin(age * 0.4), dy=0.12 * math.sin(age * 0.07) + 0.15 * math.sin(phase) * speed)
    add(pose, "tail_end", dx=swim * 0.3 * math.sin(age * 0.4 - 0.8))
    add(pose, "fan", dx=swim * 0.25 * math.sin(age * 0.4 - 1.6))
    for k, name in enumerate(("swimmeret_low", "swimmeret_high")):
        add(pose, name, dx=-(0.15 + 0.5 * swim) * (0.5 + 0.5 * math.sin(age * (0.25 + 0.5 * swim) + k * 1.7)))

    tilt, dip, press, rack = reload_curves(reload)
    add(pose, "head", dy=yaw * 0.6, dx=pitch * 0.4)
    # The eyes swivel each on its own, until it takes aim (both lock on) or reloads (both look down at the gun).
    free = 1.0 - max(aim, tilt)
    add(pose, "right_eye_stalk", dy=free * 0.6 * math.sin(age * 0.071), dx=free * 0.25 * math.sin(age * 0.093 + 1.0) + 0.5 * tilt)
    add(pose, "left_eye_stalk", dy=free * 0.6 * math.sin(age * 0.053 + 2.1), dx=free * 0.25 * math.sin(age * 0.081 + 4.0) + 0.5 * tilt)
    add(pose, "right_antenna", dz=0.1 * math.sin(age * 0.09), dx=0.08 * math.sin(age * 0.13 + 1.0))
    add(pose, "left_antenna", dz=-0.1 * math.sin(age * 0.09 + 2.0), dx=0.08 * math.sin(age * 0.11 + 3.0))
    add(pose, "right_scale", dz=-0.12 * math.sin(age * 0.2))
    add(pose, "left_scale", dz=0.12 * math.sin(age * 0.2 + 1.0))
    add(pose, "thorax", dx=-0.06 * recoil)

    # The gun: held low until it takes aim, then levelled at where it's looking; each shot flips it up and throws the
    # slide back; reloading, it's tipped over on its side and brought up toward its eyes to thumb rounds into the gate.
    add(pose, "arms", dx=(1 - aim) * LOW + aim * pitch - 0.35 * recoil - 0.3 * tilt, dy=aim * yaw * 0.5)
    add(pose, "gun", dz=-0.6 * tilt)
    add(pose, "slide", pos=(0, 0, 1.5 * recoil + 2.5 * rack))
    add(pose, "left_arm", dx=0.9 * dip + 0.2 * rack, dz=0.25 * dip)
    add(pose, "left_fore", dx=-0.5 * dip)
    add(pose, "left_dactyl", dx=-0.6 * press)
    if attack > 0.0:
        # The snap: the left claw lets go of the gun and punches forward, the dactyl flung open and slammed shut.
        thrust = math.sin(attack * math.pi)
        add(pose, "left_arm", dx=-1.3 * thrust, dz=-0.3 * thrust)
        add(pose, "left_fore", dx=0.7 * thrust)
        add(pose, "left_dactyl", dx=-1.9 * snap_open(attack))
        add(pose, "thorax", dy=-0.25 * thrust)
    return pose


def quads(pose, labels=False):
    """Every textured face of the posed model: (corners in model space, uv rect[, label, outward normal])."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot = pose[child]
            o = vadd(origin, apply(matrix, pivot))
            m = mul(matrix, rot_matrix(*rot))
            for k, (cube, frm, size, mirror) in enumerate(PART_CUBES.get(child, [])):
                (u, v), _ = CUBES[cube]
                for verts, uv in cube_faces(m, o, u, v, frm, size, mirror):
                    if labels:
                        centre = vadd(o, apply(m, tuple(frm[j] + size[j] / 2 for j in range(3))))
                        fc = tuple(sum(p[i] for p in verts) / 4 for i in range(3))
                        out.append((verts, uv, f"{child}.{cube}{k}", vsub(fc, centre)))
                    else:
                        out.append((verts, uv))
            walk(child, m, o)

    walk("root", [[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size, mirror):
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    if mirror:
        x0, x1 = x1, x0
    P = lambda x, y, z: vadd(o, apply(m, (x, y, z)))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1), ([t2, t3, l3, l2], u2, v1, u22, v0), ([t0, l0, l3, t3], u0, v1, u1, v2),
             ([t1, t0, t3, t2], u1, v1, u2, v2), ([l1, t1, t2, l2], u2, v1, u3, v2), ([l0, l1, l2, l3], u3, v1, u4, v2)]
    return [(verts, (a0, b0, a1, b1)) for verts, a0, b0, a1, b1 in polys]


# Z-fighting: no two faces may share a plane, face the same way and overlap, at rest or aiming.
from coplanar import Face, fights
for _label, _pose in (("rest", rest_pose()), ("aiming", animate(rest_pose(), aim=1.0))):
    _hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(_pose, labels=True)])
    if _hits:
        for h_ in _hits:
            print("z-fight: shrimp", _label, h_)
        raise SystemExit(f"shrimp ({_label}): {len(_hits)} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, _ in quads(pose) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
_gun = sum(len(PART_CUBES[n]) for n in ("gun", "slide", "grip"))
print(f"shrimp: {cube_count()} cubes ({cube_count() - _gun} creature, {_gun} gun); at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, "
      f"top y {ext[1][0]:.1f}, feet y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")
_m = world(PARTS, "gun", (0.0, -3.0, -10.6), animate(rest_pose(), aim=1.0))
print(f"  muzzle when aimed: {(_m[0] / 16):.2f} right?, {((24 - _m[1]) / 16):.2f} up, {(-_m[2] / 16):.2f} forward (blocks)")


# ---------------------------------------------------------------------------------------------- preview rendering
def render(pose, view, scale=8, width=300, height=380, light=(0.35, -0.8, -0.5), focus=None, texture=None):
    """Orthographic render. view: 'front' (looking at its face), 'side' (from its right), 'three' (three-quarters),
    'back', or an angle in radians. focus: a model point to centre (default: feet at the bottom)."""
    texture = texture or TEXTURE
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "left": -math.pi / 2,
             "three_left": -math.pi / 5}.get(view, view)
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs = [p[0] * ca - p[2] * sa for verts, _ in quads(pose) for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs) + max(xs)) / 2 * scale, height - 20, 24.0
    gy = int(oy + (24.0 - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    for verts, (ua, va, ub, vb) in quads(pose):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = vsub(B, A)
        e2 = vsub(C, A)
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        shade = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = ub - ua, vb - va
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua + (i + 0.5) * du / nu), TEX_W - 1)
                tv = min(int(va + (j + 0.5) * dv / nv), TEX_H - 1)
                col = texture.get(int(tu), int(tv))
                if col[3] == 0:
                    continue
                corners = []
                for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s_, t_ = a / nu, b / nv
                    p = tuple(A[k] + e1[k] * s_ + e2[k] * t_ for k in range(3))
                    x = p[0] * ca - p[2] * sa
                    z = p[0] * sa + p[2] * ca
                    corners.append((ox + x * scale, oy + (p[1] - ly) * scale, z))
                depth = sum(c[2] for c in corners) / 4
                c = (int(col[0] * shade), int(col[1] * shade), int(col[2] * shade), 255)
                fill(img, zbuf, width, height, corners, depth, c)
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
        s = 1 if cross > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return sign != 0


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k, im in enumerate(images):
        bx, by = (k % cols) * w, (k // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


def preview_entity(path):
    W, H = 300, 420
    r = lambda pose, view, **kw: render(pose, view, scale=kw.pop("scale", 9), width=W, height=H, **kw)
    views = [r(rest_pose(), "front"), r(rest_pose(), "side"), r(rest_pose(), "three"), r(rest_pose(), "back"),
             r(animate(rest_pose(), walk=1.0, speed=1.0, age=10), "three_left"),
             r(animate(rest_pose(), aim=1.0, age=20), "three"),
             r(animate(rest_pose(), aim=1.0, recoil=1.0, age=20), "side"),
             r(animate(rest_pose(), reload=0.2, age=30), "three"),
             r(animate(rest_pose(), reload=0.82, age=30), "three"),
             r(animate(rest_pose(), aim=0.3, attack=0.42, age=40), "three"),
             r(animate(rest_pose(), swim=1.0, walk=3.0, speed=0.6, age=15), "side")]
    head = world(PARTS, "head", (0.0, -2.0, -2.0))
    gun = world(PARTS, "gun", (0.0, -1.0, -3.0), animate(rest_pose(), aim=1.0))
    views += [r(rest_pose(), "three", scale=24, focus=head), r(animate(rest_pose(), aim=1.0), "side", scale=24, focus=gun),
              r(animate(rest_pose(), aim=1.0), "three_left", scale=24, focus=gun)]
    out, bw, bh = board(views, W, H, 7)
    png(path, bw, bh, out)


def preview_texture(path):
    s = 4
    big = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
           for y in range(TEX_H * s) for x in range(TEX_W * s)]
    for y in range(TEX_H * s):
        for x in range(TEX_W * s):
            c = TEXTURE.get(x // s, y // s)
            if c[3]:
                big[y * TEX_W * s + x] = c
    png(path, TEX_W * s, TEX_H * s, big)


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    preview_entity(os.path.join(PREVIEW, "preview_shrimp.png"))
    preview_texture(os.path.join(PREVIEW, "preview_shrimp_texture.png"))
    print("entity preview written to", PREVIEW)


# ================================================================================================ the Shrimp Pistol
# The item model: the gun the shrimp holds, in the item pixel space the other guns use (the bore along z at x = 8, the
# muzzle toward -z, +x the gun's right). Its own atlas; the slide is the moving part (drawn back over six frames for the
# loading animation, see ShotgunAnimation), and the muzzle flash layers are gunkit's.
from gunkit import (Kit, check, move, rotate, rx, write, model, ref, range_dispatch, display, display_matrix, placed, centred,
                    bounds, flash_models, with_flash, png as kit_png)
from coplanar import check_elements

FRAMES = 6
FP_PISTOL_GRIP = (-0.020 * 16, 0.131 * 16, -0.181 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
SP_BY = 10.0
SP_TRAVEL = 1.8
SP_GRIP = (8.0, 6.2, 5.4)        # where the shooting hand closes
SP_PORT = (8.0, 11.6, 2.8)       # the loading gate on top of the slide
SP_EJECT = (9.2, 11.0, 3.6)
SP_WORK = (8.0, 10.4, 7.0)       # the brass knob on the back of the slide that the hand draws back
SP_MUZZLE = -3.9


def hexc(c):
    return (c[0] << 16) | (c[1] << 8) | c[2]


class Atlas(Kit):
    """Swatches shelf-packed in texels; UV cuts start at a swatch's top-left corner (so its first row, a highlight, lands
    on a face's top edge). Flat colours are cut from inside a 4 x 4 block so mipmapping never bleeds a neighbour in."""

    def __init__(self):
        super().__init__("shrimp_pistol", "item/shrimp_pistol", seed=7, size=64)
        self.skyline = [0] * self.size

    def add(self, name, w, h, f, picture=False):
        y, x = min((max(self.skyline[x:x + w]), x) for x in range(0, self.size - w + 1, 4))
        assert y + h <= self.size, ("atlas full", name)
        for i in range(x, x + w):
            self.skyline[i] = y + h
        for j in range(h):
            for i in range(w):
                c = f(i, j)
                self.atlas[(y + j) * self.size + x + i] = c if isinstance(c, tuple) and len(c) == 4 else rgba(c)
        t = 16.0 / self.size
        self.sw[name] = ((x * t, y * t, (x + w) * t, (y + h) * t), picture)

    def flat(self, name, c):
        self.add(name, 4, 4, lambda i, j: c, picture=True)
        (u0, v0, u1, v1), _ = self.sw[name]
        t = 16.0 / self.size
        self.sw[name] = ((u0 + t, v0 + t, u1 - t, v1 - t), True)

    def uv(self, name, du, dv):
        (u0, v0, u1, v1), picture = self.sw[name]
        if picture:
            return [round(u0, 4), round(v0, 4), round(u1, 4), round(v1, 4)]
        t = 16.0 / self.size
        su, sv = min(u1 - u0, max(du * 2 * t, t)), min(v1 - v0, max(dv * 2 * t, t))
        return [round(u0, 4), round(v0, 4), round(u0 + su, 4), round(v0 + sv, 4)]


K = Atlas()
METALS = {"gun": GUNMETAL, "brass": BRASS + [BRASS[3]], "wood": WOOD + [WOOD[3]]}
for key, cols in METALS.items():
    for k, c in enumerate(cols[:5]):
        K.flat(f"{key}_{k}", c)
    # Sides: a highlight line along the top edge, then a smooth fall. Tops: lit along one edge.
    K.add(f"{key}_side", 4, 12, lambda i, j, c=cols: c[0] if j == 0 else ramp(c, 0.2 + 0.6 * j / 11))
    K.add(f"{key}_top", 12, 4, lambda i, j, c=cols: c[0] if i == 0 else ramp(c, 0.15 + 0.2 * i / 11))
# Driftwood along its grain (rows run along the part), the first row the bleached top edge.
K.add("wood_grain", 24, 12, lambda i, j: WOOD[0] if j == 0 else lerp(ramp(WOOD, 0.15 + 0.5 * j / 11), WOOD[3], 0.45)
      if j in (3, 7) or (j == 5 and 6 <= i < 17) else ramp(WOOD, 0.15 + 0.5 * j / 11))
K.add("wood_upright", 12, 24, lambda i, j: lerp(ramp(WOOD, 0.1 + 0.6 * j / 23), WOOD[3], 0.4) if i == 4 or (i == 8 and j > 9) else
      ramp(WOOD, 0.1 + 0.6 * j / 23))
TWINE = [(222, 204, 160), (192, 172, 128), (150, 130, 94)]
K.add("rope", 4, 12, lambda i, j: TWINE[j % 3])


def slide_flank(i, j):
    """The slide's flank, 28 x 8 (four texels a pixel), front at the left: a highlight edge, a dark bottom edge, and
    four serrations cut in at the back."""
    if j == 0:
        return GUNMETAL[0]
    if j == 7:
        return GUNMETAL[4]
    if 19 <= i <= 26 and 1 <= j <= 6 and (i - 19) % 2 == 0:
        return GUNMETAL[4]
    return ramp(GUNMETAL, 0.25 + 0.3 * j / 7)


K.add("slide_flank", 28, 8, slide_flank, picture=True)
K.add("slide_flank_r", 28, 8, lambda i, j: slide_flank(27 - i, j), picture=True)


def bore_picture(i, j):
    d = math.hypot(i - 3.5, j - 3.5)
    return BORE if d < 2.2 else BRASS[3] if d < 2.9 else BRASS[1] if d < 3.9 else (0, 0, 0, 0)


K.add("bore", 8, 8, bore_picture, picture=True)
K.save(TEX)


def metal(out, x0, y0, z0, x1, y1, z1, mat, rot=None, name=None, **faces):
    f = dict(north=f"{mat}_side", south=f"{mat}_side", east=f"{mat}_side", west=f"{mat}_side", up=f"{mat}_top",
             down=f"{mat}_3")
    f.update(faces)
    return K.box(out, x0, y0, z0, x1, y1, z1, f"{mat}_2", rot=rot, name=name, **f)


def tube(out, z0, z1, r, mat, cy=SP_BY, name=None, caps=(True, True)):
    """A round bar along z, an octagon of four bands shaded bright on top to dark beneath (as gen_shoddy's tube)."""
    a = 2 * r * math.tan(math.pi / 8)
    bands = [(0, r, a / 2, dict(east=f"{mat}_2", west=f"{mat}_2", up=None, down=None)),
             (0, a / 2, r, dict(east=None, west=None, up=f"{mat}_0", down=f"{mat}_4")),
             (45, r, a / 2, dict(east=f"{mat}_1", west=f"{mat}_3", up=None, down=None)),
             (-45, r, a / 2, dict(east=f"{mat}_3", west=f"{mat}_1", up=None, down=None))]
    for k, (angle, w, h, faces) in enumerate(bands):
        d = 0.01 * k
        K.box(out, 8 - w, cy - h, z0 + d, 8 + w, cy + h, z1 - d, f"{mat}_2", name=name if k == 0 else None,
              rot=("z", angle, (8, cy, z0)) if angle else None,
              north=f"{mat}_1" if caps[0] else None, south=f"{mat}_1" if caps[1] else None, **faces)


def shrimp_pistol(travel):
    """A stubby salvaged pistol: a short blued barrel in a brass muzzle ring, a dark frame, a slide with serrations at
    the back, a brass loading gate on top and a brass knob to rack it by, a raked driftwood grip bound with rope, and a
    brass trigger guard."""
    out = []
    tube(out, SP_MUZZLE + 0.6, 1.5, 0.75, "gun", name="barrel", caps=(False, False))
    tube(out, SP_MUZZLE, SP_MUZZLE + 0.6, 1.05, "brass", name="muzzle")
    K.sticker(out, 8 - 0.95, SP_BY - 0.95, SP_MUZZLE - 0.02, 8 + 0.95, SP_BY + 0.95, SP_MUZZLE - 0.01, "bore", "north", name="bore")
    metal(out, 7.82, SP_BY + 1.0, SP_MUZZLE + 0.1, 8.18, SP_BY + 1.6, SP_MUZZLE + 0.5, "gun", name="front_sight")
    metal(out, 7.1, 8.0, -1.4, 8.9, 9.35, 6.0, "gun", name="frame", east="gun_3", west="gun_3", up="gun_3")
    # Trigger and guard.
    metal(out, 7.86, 6.9, 2.3, 8.14, 7.15, 4.5, "brass", name="guard")
    metal(out, 7.86, 7.15, 2.3, 8.14, 8.0, 2.55, "brass", name="guard")
    metal(out, 7.89, 7.3, 3.45, 8.11, 8.0, 3.7, "gun", name="trigger")
    # The grip, raked back, its parts turned together.
    grip = []
    K.box(grip, 7.2, 3.6, 4.1, 8.8, 8.2, 6.8, "wood_upright", name="grip", up="wood_2", down="wood_3")
    K.box(grip, 7.15, 5.4, 4.05, 8.85, 6.1, 6.85, "rope", name="rope", up=None, down=None)
    metal(grip, 7.1, 3.3, 4.0, 8.9, 3.6, 6.9, "brass", name="grip_cap")
    out += rotate(grip, rx(-15), (8.0, 8.2, 5.4))
    # The slide and everything on it.
    slide = []
    metal(slide, 6.9, 9.35, -0.6, 9.1, 11.4, 6.6, "gun", name="slide", east="slide_flank_r", west="slide_flank",
          north="gun_2", south="gun_3")
    metal(slide, 7.5, 11.4, 2.0, 8.5, 11.75, 3.6, "brass", name="gate")
    for x0 in (7.35, 8.25):
        metal(slide, x0, 11.4, 5.7, x0 + 0.4, 11.95, 6.15, "gun", name="rear_sight")
    metal(slide, 7.6, 9.9, 6.6, 8.4, 10.9, 7.1, "brass", name="knob")
    out += move(slide, dz=travel)
    return out


def gun_displays(grip, elements, fp_scale):
    fp_rot = [0, -6, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, FP_PISTOL_GRIP), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, grip, TP_GRIP_TARGET), 0.65)
    lo, hi = bounds(elements)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    gui_scale = round(15.0 / max(hi[2] - lo[2], hi[1] - lo[1]), 3)
    gui_rot = [0, -90, 0]
    return OrderedDict([
        ("thirdperson_righthand", tp), ("thirdperson_lefthand", tp),
        ("firstperson_righthand", fp), ("firstperson_lefthand", fp),
        ("gui", display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale)),
        ("ground", display([0, 0, 0], centred([0, 0, 0], 0.5, c, 2.0), 0.5)),
        ("fixed", display(gui_rot, centred(gui_rot, gui_scale * 1.3, c), round(gui_scale * 1.3, 3)))])


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


PISTOL_FRAMES = [shrimp_pistol(SP_TRAVEL * k / FRAMES) for k in range(FRAMES + 1)]
PISTOL_DISPLAYS = gun_displays(SP_GRIP, [e for f in PISTOL_FRAMES for e in f], 0.65)
for _i, _elements in enumerate(PISTOL_FRAMES):
    check(f"shrimp_pistol_f{_i}", _elements)
    check_elements(f"shrimp_pistol_f{_i}", _elements)
    write(os.path.join(MODELS, "shrimp_pistol" + (f"_f{_i}" if _i else "") + ".json"), model(K, _elements, PISTOL_DISPLAYS))
_flashes = flash_models(MODELS, "shrimp_pistol", SP_MUZZLE, SP_BY, PISTOL_DISPLAYS)
_body = range_dispatch("tomyen:gun_animation", [(k - 0.5, ref(f"shrimp_pistol_f{k}")) for k in range(1, FRAMES + 1)]
                       + [(FRAMES + 0.5, ref("shrimp_pistol"))], ref("shrimp_pistol"))
write(os.path.join(ITEMS, "shrimp_pistol.json"), {"model": with_flash(_body, _flashes)})
_t = PISTOL_DISPLAYS["firstperson_righthand"]
print(f"shrimp_pistol: {len(PISTOL_FRAMES[0])} elements; GunDisplay translation {_t['translation']} yaw {_t['rotation'][1]} "
      f"scale 0.65; grip {fp_point(PISTOL_DISPLAYS, SP_GRIP)}; gui scale {PISTOL_DISPLAYS['gui']['scale'][0]}")
print(f"  ShotgunAnimation points (pixels): support {SP_GRIP} port {SP_PORT} ejection {SP_EJECT} travel {SP_TRAVEL} work {SP_WORK}")


# ================================================================================================ sprites
def sprite_canvas(f):
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            col = f(x, y)
            if col is not None:
                c.set(x, y, rgba(col))
    return c


def curled_shrimp(body, rim, outline, eye, antenna):
    """A shrimp curled into a C, open to the right: head and antennae at the top right, the tail fan at the bottom
    right. body: a ramp, light to dark; the segments ringed with rim."""
    cx, cy = 7.6, 8.4
    head, tail = 300.0, 38.0

    def where(x, y):
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        r = math.hypot(dx, dy)
        ang = math.degrees(math.atan2(dy, dx)) % 360
        return r, ang

    def part(x, y):
        r, ang = where(x, y)
        if tail <= ang <= head:
            t = (head - ang) / (head - tail)
            outer = 6.9 - 0.6 * t
            inner = outer - (4.2 - 2.0 * t)
            if inner <= r <= outer:
                return "body", t, (r - inner) / (outer - inner), ang
        # The fan spreads out past the tail's end.
        if 8 <= ang < tail and 2.6 <= r <= 7.2 - (tail - ang) * 0.03:
            return "fan", 1.0, 0.5, ang
        return None

    mask = [[part(x, y) for x in range(16)] for y in range(16)]

    def f(x, y):
        p = mask[y][x]
        if p is None:
            near = any(0 <= x + a < 16 and 0 <= y + b < 16 and mask[y + b][x + a] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            return outline if near else None
        kind, t, across, ang = p
        if kind == "fan":
            r, _ = where(x, y)
            return rim if r > 6.0 else body[1] if r > 4.4 else body[2]
        # Segment rings every 32 degrees of the curl; the back (outer edge) lit, the belly (inner edge) darker.
        if (ang - tail) % 32 < 5 and t > 0.12:
            return rim
        if (x, y) == eye:
            return (16, 18, 22)
        return ramp(body, 0.1 + 0.75 * (1 - across) * 0.7 + 0.25 * t)
    c = sprite_canvas(f)
    # Antennae: two thin lines from the head sweeping up and off to the right.
    for (x, y) in antenna:
        c.set(x, y, rgba(lerp(outline, body[3], 0.5)))
    return c


ANTENNAE = [(12, 2), (13, 1), (14, 1), (15, 0), (12, 4), (13, 4), (14, 3), (15, 3)]
RAW = [(198, 196, 196), (170, 172, 178), (140, 144, 154), (112, 116, 128)]
curled_shrimp(RAW, (226, 190, 186), (72, 76, 88), (11, 4), ANTENNAE).save("item/raw_shrimp.png")
COOKED = [(255, 186, 150), (246, 146, 108), (226, 108, 78), (190, 78, 60)]
curled_shrimp(COOKED, (255, 230, 212), (128, 48, 40), (11, 4), ANTENNAE).save("item/cooked_shrimp.png")

# Spawn egg: the shared egg shape, the shell with pale segment rims, two teal eyes near the top and a gunmetal band.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    light = (cx - x) + (cy - y)
    if outline:
        egg.px[i] = rgba(SHELL[5])
    elif (x, y) in ((round(cx) - 2, min(ys) + 3), (round(cx) + 1, min(ys) + 3)):
        egg.px[i] = rgba(EYE[2])
    elif y == round(cy) + 2:
        egg.px[i] = rgba(GUNMETAL[2] if light > 0 else GUNMETAL[3])
    elif (y - min(ys)) % 3 == 2 and y < round(cy) + 2:
        egg.px[i] = rgba(RIM)
    else:
        egg.px[i] = rgba(ramp(SHELL, 0.1 if light > 5 else 0.3 if light > 1 else 0.5 if light > -4 else 0.7))
egg.save("item/shrimp_spawn_egg.png")

for name in ("raw_shrimp", "cooked_shrimp", "shrimp_spawn_egg"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


# ================================================================================================ data
def jwrite(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge(path, entries):
    """Adds entries to a JSON object file others also write (lang, sounds.json), keeping what's there and its order."""
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    jwrite(path, obj)


def count(low, high):
    return {"type": "minecraft:uniform", "min": low, "max": high}


jwrite(os.path.join(DATA, "loot_table/entities/shrimp.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:raw_shrimp", "modifier": [
            {"type": "minecraft:set_count", "count": count(1, 3)},
            # Killed on fire, or with a fire-aspect weapon, it comes out cooked (as vanilla's fish do).
            {"type": "minecraft:furnace_smelt", "condition": {"type": "minecraft:any_of", "terms": [
                {"type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:flags": {"is_on_fire": True}}},
                {"type": "minecraft:entity_properties", "entity": "direct_attacker", "predicate": {"minecraft:equipment": {
                    "mainhand": {"predicates": {"minecraft:enchantments": [{"enchantments": "#minecraft:smelts_loot"}]}}}}}]}},
            {"type": "minecraft:enchanted_count_increase", "count": count(0.0, 1.0), "enchantment": "minecraft:looting"}]}]},
        # Its pistol, now and then, and never in good condition.
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:shrimp_pistol", "modifier": [
            {"type": "minecraft:set_damage", "damage": count(0.35, 0.8)}]}],
         "condition": {"type": "minecraft:all_of", "terms": [
             {"type": "minecraft:killed_by_player"},
             {"type": "minecraft:random_chance_with_enchanted_bonus", "unenchanted_chance": 0.06,
              "enchanted_chance": {"type": "minecraft:linear", "base": 0.08, "per_level_above_first": 0.02},
              "enchantment": "minecraft:looting"}]}},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:crude_round", "modifier": [
            {"type": "minecraft:set_count", "count": count(0, 2)}]}],
         "condition": {"type": "minecraft:killed_by_player"}}]),
    ("random_sequence", "tomyen:entities/shrimp")]))


def advancement(name, ingredient, category):
    jwrite(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


for suffix, kind, ticks in (("", "smelting", 200), ("_from_smoking", "smoking", 100), ("_from_campfire_cooking", "campfire_cooking", 600)):
    name = "cooked_shrimp" + suffix
    jwrite(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:" + kind), ("category", "food"), ("cookingtime", ticks), ("experience", 0.35),
        ("ingredient", "tomyen:raw_shrimp"), ("result", {"id": "tomyen:cooked_shrimp"})]))
    advancement(name, "tomyen:raw_shrimp", "food")

# The sea fish, which come to shrimp bait (every species file that puts its fish in an ocean or on a beach).
jwrite(os.path.join(DATA, "tags/item/saltwater_fish.json"), OrderedDict([("replace", False), ("values", [
    "minecraft:cod", "minecraft:pufferfish", "minecraft:tropical_fish", "tomyen:mackerel", "tomyen:red_snapper", "tomyen:tuna",
    "tomyen:mahi_mahi", "tomyen:swordfish", "tomyen:barracuda", "tomyen:flounder", "tomyen:parrotfish", "tomyen:sturgeon",
    "tomyen:ice_char", "tomyen:glowfin_tetra", "tomyen:anglerfish", "tomyen:abyssal_squidling", "tomyen:starlit_gar"])]))


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


def event(subtitle, sounds):
    return OrderedDict([("subtitle", "subtitles.tomyen." + subtitle), ("sounds", sounds)])


SOUNDS = OrderedDict([
    # Clicks and chitters: the silverfish, slowed; the guardian's landed flopping for its hurt and death.
    ("shrimp_ambient", event("shrimp_ambient", vanilla(["mob/silverfish/say%d" % i for i in (1, 2, 3, 4)], 0.55))),
    ("shrimp_hurt", event("shrimp_hurt", vanilla(["mob/silverfish/hit%d" % i for i in (1, 2, 3)], 0.6)
                          + vanilla(["mob/guardian/land_hit1"], 1.1))),
    ("shrimp_death", event("shrimp_death", vanilla(["mob/silverfish/kill"], 0.5) + vanilla(["mob/guardian/land_death"], 1.0))),
    ("shrimp_step", event("shrimp_step", vanilla(["mob/turtle/walk%d" % i for i in (1, 2, 3, 4, 5)], 1.4))),
    ("shrimp_swim", event("shrimp_swim", vanilla(["entity/fish/swim%d" % i for i in (1, 2, 3, 4)], 0.8))),
    ("shrimp_snap", event("shrimp_snap", vanilla(["mob/zombie/woodbreak"], 1.8, volume=0.8)
                          + vanilla(["item/shield/block%d" % i for i in (1, 2, 3)], 1.6))),
    ("shrimp_cock", event("shrimp_cock", vanilla(["random/click"], 1.5, volume=0.8))),
    ("shrimp_reload_round", event("shrimp_reload_round", vanilla(["item/crossbow/loading_middle%d" % i for i in (1, 2, 3, 4)], 1.5))),
    ("shrimp_reload_rack", event("shrimp_reload_rack", vanilla(["item/crossbow/loading_end"], 1.25))),
    # The pipe pistol's own recordings, pitched up: a smaller, snappier gun.
    ("shrimp_pistol_shot", event("shrimp_pistol_shot", [OrderedDict([("name", "tomyen:pipe_pistol_shot"), ("pitch", 1.15)]),
                                                         OrderedDict([("name", "tomyen:pipe_pistol_shot_2"), ("pitch", 1.2)]),
                                                         OrderedDict([("name", "tomyen:pipe_pistol_shot_3"), ("pitch", 1.25)])])),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.tomyen.shrimp", "Shrimp with a Gun"),
    ("item.tomyen.shrimp_spawn_egg", "Shrimp with a Gun Spawn Egg"),
    ("item.tomyen.raw_shrimp", "Raw Shrimp"),
    ("item.tomyen.cooked_shrimp", "Cooked Shrimp"),
    ("item.tomyen.shrimp_pistol", "Shrimp Pistol"),
    ("item.tomyen.shrimp_pistol.hint", "Four Crude Rounds, loaded one at a time through the gate; the slide is racked once it's full"),
    ("tooltip.tomyen.bait.attracts.saltwater_fish", "Draws in sea fish (cod, mackerel, snapper, tuna, swordfish)"),
    ("tooltip.tomyen.bait.attracted_size.saltwater_fish", "Bigger sea fish +%s"),
    ("subtitles.tomyen.shrimp_ambient", "Shrimp clicks"),
    ("subtitles.tomyen.shrimp_hurt", "Shrimp hurts"),
    ("subtitles.tomyen.shrimp_death", "Shrimp dies"),
    ("subtitles.tomyen.shrimp_step", "Shrimp scuttles"),
    ("subtitles.tomyen.shrimp_swim", "Shrimp swims"),
    ("subtitles.tomyen.shrimp_snap", "Shrimp claw snaps"),
    ("subtitles.tomyen.shrimp_cock", "Shrimp cocks its pistol"),
    ("subtitles.tomyen.shrimp_reload_round", "Shrimp loads a round"),
    ("subtitles.tomyen.shrimp_reload_rack", "Shrimp racks its pistol"),
    ("subtitles.tomyen.shrimp_pistol_shot", "Shrimp pistol fires"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ================================================================================================ item preview
def item_preview(path):
    """The pistol at rest and racked, side on and from above-right, rendered from its elements; and the sprites."""
    atlas = K.atlas
    size = K.size

    def draw(elements, view, scale, w, h):
        img = [(58, 60, 66, 255)] * (w * h)
        zb = [1e9] * (w * h)
        yaw, pitch = view
        from gunkit import element_rotation, apply as kapply
        from coplanar import _CORNERS, _DIRS
        cy_, sy_ = math.cos(yaw), math.sin(yaw)
        cp, sp = math.cos(pitch), math.sin(pitch)

        def proj(p):
            x, y, z = p[0] - 8, p[1] - 8, p[2] - 4
            x, z = x * cy_ - z * sy_, x * sy_ + z * cy_
            y, z = y * cp - z * sp, y * sp + z * cp
            return (w / 2 + x * scale, h / 2 - y * scale, z)
        for e in elements:
            rot = element_rotation(e)
            for face, spec in e["faces"].items():
                pts = _CORNERS[face](e["from"], e["to"])
                n = _DIRS[face]
                if rot:
                    m, o = rot
                    pts = [tuple(v + o[k] for k, v in enumerate(kapply(m, [p[k] - o[k] for k in range(3)]))) for p in pts]
                    n = kapply(m, n)
                light = 0.6 + 0.4 * max(0.0, n[1] * 0.8 + n[0] * 0.3 - n[2] * 0.2)
                u0, v0, u1, v1 = spec["uv"]
                sc = [proj(p) for p in pts]
                steps = 32
                for j in range(steps):
                    for i in range(steps):
                        a, b = (i + 0.5) / steps, (j + 0.5) / steps
                        tu = int((u0 + (u1 - u0) * a) / 16 * size)
                        tv = int((v0 + (v1 - v0) * b) / 16 * size)
                        c = atlas[min(size - 1, max(0, tv)) * size + min(size - 1, max(0, tu))]
                        if c[3] == 0:
                            continue
                        quad = []
                        for (da, db) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                            aa, bb = (i + da) / steps, (j + db) / steps
                            top = tuple(sc[0][k] + (sc[1][k] - sc[0][k]) * aa for k in range(3))
                            bot = tuple(sc[3][k] + (sc[2][k] - sc[3][k]) * aa for k in range(3))
                            quad.append(tuple(top[k] + (bot[k] - top[k]) * bb for k in range(3)))
                        depth = sum(q[2] for q in quad) / 4
                        fill(img, zb, w, h, quad, -depth, (int(c[0] * light), int(c[1] * light), int(c[2] * light), 255))
        return img
    W, H = 360, 240
    views = [draw(PISTOL_FRAMES[0], (math.pi / 2, 0.0), 22, W, H), draw(PISTOL_FRAMES[FRAMES], (math.pi / 2, 0.0), 22, W, H),
             draw(PISTOL_FRAMES[0], (math.pi / 2 + 0.6, 0.45), 20, W, H), draw(PISTOL_FRAMES[0], (-math.pi / 2 + 0.5, 0.35), 20, W, H)]
    sprites = Canvas(W, H)
    sprites.px = [(58, 60, 66, 255)] * (W * H)
    for k, name in enumerate(("raw_shrimp", "cooked_shrimp", "shrimp_spawn_egg")):
        _, _, px = read_png(os.path.join(TEX, "item", name + ".png"))
        for y in range(16 * 6):
            for x in range(16 * 6):
                c = px[(y // 6) * 16 + x // 6]
                if c[3]:
                    sprites.set(20 + k * 110 + x, 60 + y, c)
    out, bw, bh = board(views + [sprites.px], W, H, 3)
    png(path, bw, bh, out)


if PREVIEW:
    item_preview(os.path.join(PREVIEW, "preview_shrimp_items.png"))
print("done")
