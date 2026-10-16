"""Builds the drills' assets: the Hand Drill, Steel Drill, Tungsten Carbide Drill and Star Drill, their bits, the motor
and crank sounds, recipes with recipe-book advancements, tags and language entries.

Every drill is a big two-handed mining drill of one family: a long stepped conical bit whose tiers turn a little more each
step, with a pair of ground flights on every tier that wind down it like an auger; a radial chuck of chunky clamp jaws
and a toothed drive ring behind it; a boxy framed body with recessed panels and proud trim; and a cross of handles at
the back with end caps. The tiers differ in material and fittings: the Hand Drill is wood framed in brass with an iron
bit and a side crank, the Steel Drill a steel motor drill with a finned motor, an exhaust and a kerosene tank, the
Tungsten Carbide Drill a heavy gunmetal one in hazard yellow with a bigger bit, tank and reinforced chuck, and the Star
Drill a sleek star steel one whose bit tip, chuck inserts and core glow with star crystal.

Textures are clean: one smooth ramp per material, lit from the top with a bright specular top edge on metals, grain
lines on wood, ribs on rubber, no speckle. Every face's texture is cut from the top of its swatch so the highlight edge
always shows. A geometric check (coplanar()) refuses any two faces that share a plane, face the same way and overlap,
whatever their rotations.

The spinning parts (bit, chuck, drive ring) are their own models, one per frame, which tomyen:drill_spin picks
(DrillSpin on the client). There are FRAMES of them: a motor drill's bit is symmetric over half a turn, which its frames
cover 15 degrees apiece; the Hand Drill's crank and bit go once round together over its frames. In the inventory, on the
ground and in item frames the drill shows its first frame.

The drills are held two-handed at the chest: the main hand on the bottom handle of the rear cross, the off hand on its
left handle (or, on the Hand Drill, on the crank's knob, which goes round with it). The first-person grip points printed
at the end go in DrillHands; keep them in step with the models.

Model space is in pixels: the bit runs along z at x = AX, y = AY with its tip toward -z, +x is the drill's right.

Usage: python3 tools/gen_drills.py src/main/resources/assets/tomyen"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, rotate, rx, rz, write, model, ref, range_dispatch, composite, display, display_matrix, apply,
                    placed, centred, bounds_centre, look, mix, copy, element_rotation, FACES, DIRS)

ASSETS = sys.argv[1]
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models", "item"), os.path.join(ASSETS, "items")

AX, AY = 8.0, 8.0          # the drill's axis
FRAMES = 12                # spin frames, every drill (DrillSpin.FRAMES)
MOTOR_STEP = 15.0          # degrees a motor drill's bit turns per frame: half a turn (its symmetry) over the frames
HAND_STEP = 360.0 / FRAMES # the Hand Drill's crank and bit, per frame: once round over the frames

# First person: held low at the chest, the bit pointing ahead a little in toward the crosshair.
FP_ROT = [0, 25, 0]
FP_SCALE = 0.58
FP_GRIP_TARGET = (2.4, -2.4, -3.0)
# Third person, in the crossbow hold: the main hand's grip point lands in the hand.
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
TP_SCALE = 0.55


def _icon_rotation():
    """The reference's three-quarter view: the bit toward the viewer and down to the left, the top and left flank showing."""
    m = [-0.8, -0.42, 0.42]
    n = math.sqrt(sum(v * v for v in m))
    m = [v / n for v in m]
    up = [0.0, 1.0, 0.0]
    d = sum(up[i] * m[i] for i in range(3))
    top = [up[i] - d * m[i] for i in range(3)]
    n = math.sqrt(sum(v * v for v in top))
    return look(m, [v / n for v in top])


GUI_ROT = _icon_rotation()


# ============================================================================================================ paint


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def metal(hi, a, b, lo):
    """A metal ramp lit from above: a bright specular top row, a smooth fall from a to b, a dark bottom row."""
    def f(x, y, w, h):
        return rgba(hi if y == 0 else lo if y == h - 1 else mix(a, b, (y - 1) / (h - 3)))
    return f


def grained(hi, a, b, grain, vertical=False):
    """Wood: a ramp with a lit top row and three gently waving grain lines, along u (or along v when vertical)."""
    def f(x, y, w, h):
        c = hi if y == 0 else mix(a, b, y / (h - 1))
        along, across = (y, x) if vertical else (x, y)
        for k, line in enumerate((3, 8, 12)):
            if across == line + round(0.8 * math.sin(along * 0.45 + k * 2.1)):
                c = mix(c, grain, 0.5)
        return rgba(c)
    return f


def rings(a, b, ring):
    """End grain (a picture): growth rings round the middle."""
    def f(x, y, w, h):
        r = math.hypot(x + 0.5 - w / 2, y + 0.5 - h / 2)
        c = mix(a, b, min(1.0, r / (w / 2)))
        if int(r) in (2, 5):
            c = mix(c, ring, 0.45)
        return rgba(c)
    return f


def recess(shadow, a, b):
    """A recessed panel: the frame's shadow across its top two rows, then a soft dark ramp."""
    def f(x, y, w, h):
        return rgba(shadow if y < 2 else mix(a, b, (y - 2) / (h - 3)))
    return f


def ribbed(a, b, rib):
    """Rubber grip: a dark ramp ribbed every fourth row."""
    def f(x, y, w, h):
        c = mix(a, b, y / (h - 1))
        return rgba(mix(c, rib, 0.6) if y % 4 == 3 else c)
    return f


def stripes(yellow, yellow_dark, black):
    """Hazard stripes, four texels wide, on the diagonal."""
    def f(x, y, w, h):
        return rgba(mix(yellow, yellow_dark, y / (h - 1)) if ((x + y) // 4) % 2 == 0 else black)
    return f


def glow(centre, edge):
    """A soft radial glow (a picture), bright in the middle."""
    def f(x, y, w, h):
        return rgba(mix(centre, edge, min(1.0, math.hypot(x + 0.5 - w / 2, y + 0.5 - h / 2) / (w / 2))))
    return f


class DrillKit(Kit):
    """A drill's atlas: 128 texels square, swatches of 16 x 16 texels (2 x 2 model UV units) eight to a row. top: the
    material for up faces of a material; grain: (along u, along v, end) swatches of a grained material."""

    def __init__(self, name, seed):
        super().__init__(name, "item/" + name, seed=seed, size=128)
        self.n, self.top, self.grain = 0, {}, {}

    def paint(self, name, f, picture=False):
        u, v = (self.n % 8) * 2, (self.n // 8) * 2
        self.n += 1
        self.swatch(name, u, v, u + 2, v + 2, f, picture)

    def metal(self, name, hi, a, b, lo, top=None):
        self.paint(name, metal(hi, a, b, lo))
        if top:
            self.paint(name + "_top", metal(*top))
            self.top[name] = name + "_top"

    def wood(self, name, hi, a, b, grain, end):
        self.paint(name + "_h", grained(hi, a, b, grain))
        self.paint(name + "_v", grained(hi, a, b, grain, vertical=True))
        self.paint(name + "_end", rings(*end), picture=True)
        self.grain[name] = (name + "_h", name + "_v", name + "_end")

    def uv(self, name, du, dv):
        """Two texels a pixel, cut from the top of the swatch (so its lit edge shows) at a varying spot along u."""
        (u0, v0, u1, v1), picture = self.sw[name]
        if picture:
            return [u0, v0, u1, v1]
        t = 16.0 / self.size
        su = min(u1 - u0, max(du * 2 * t, t))
        sv = min(v1 - v0, max(dv * 2 * t, 2 * t))
        ou = self.rnd.uniform(0, (u1 - u0) - su)
        return [round(u0 + ou, 4), v0, round(u0 + ou + su, 4), round(v0 + sv, 4)]

    def save(self, textures_root):
        super().save(textures_root)


def zrot(angle, cx=AX, cy=AY, z=0.0):
    a = ((angle + 180.0) % 360.0) - 180.0
    return None if abs(a) < 1e-9 else ("z", round(a, 4), (cx, cy, z))


def part(k, out, x0, y0, z0, x1, y1, z1, mat, rot=None, emit=False, name=None, **faces):
    """A box of one material: a grained one runs its grain along the box's longest side (end grain on the ends), a
    metal with a top variant uses it on the up face. faces: per-face overrides (None drops the face)."""
    fm = {}
    base = mat
    if mat in k.grain:
        h, v, end = k.grain[mat]
        ext = {"x": abs(x1 - x0), "y": abs(y1 - y0), "z": abs(z1 - z0)}
        long = max(ext, key=ext.get)
        for f in FACES:
            a, b = DIRS[f]
            fm[f] = h if a == long else v if b == long else end
        base = h
    elif mat in k.top:
        fm["up"] = k.top[mat]
    fm.update(faces)
    return k.box(out, x0, y0, z0, x1, y1, z1, base, rot=rot, emit=emit, name=name, **fm)


def square(k, out, z0, z1, half, mat, angle=0.0, emit=False, name=None, cx=AX, cy=AY, **faces):
    """A square bar along z on the axis (or about cx, cy), turned by angle."""
    return part(k, out, cx - half, cy - half, z0, cx + half, cy + half, z1, mat, rot=zrot(angle, cx, cy, z0), emit=emit,
                name=name, **faces)


def star8(k, out, z0, z1, half, mat, angle=0.0, inner=0.9, emit=False, name=None, cx=AX, cy=AY):
    """Two squares an eighth turn apart: a round-ish eight-pointed section. The second is set in at its ends."""
    square(k, out, z0, z1, half, mat, angle, emit=emit, name=name, cx=cx, cy=cy)
    square(k, out, z0 + 0.03, z1 - 0.03, half * inner, mat, angle + 45, emit=emit, cx=cx, cy=cy)


# ======================================================================================================== z-fight

_CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}
_NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def _faces(elements):
    for i, e in enumerate(elements):
        r = element_rotation(e)
        for face in e["faces"]:
            q = _CORNERS[face](e["from"], e["to"])
            n = _NORMALS[face]
            if r:
                m, o = r
                q = [[v + o[a] for a, v in enumerate(apply(m, [p[c] - o[c] for c in range(3)]))] for p in q]
                n = apply(m, list(n))
            yield i, face, [list(p) for p in q], list(n)


def _clip(subject, clipper):
    """Sutherland-Hodgman: the part of convex polygon subject inside convex (counter-clockwise) polygon clipper."""
    out = subject
    for k in range(len(clipper)):
        a, b = clipper[k], clipper[(k + 1) % len(clipper)]
        inside = lambda p: (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) >= -1e-9
        inp, out = out, []
        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            if inside(q):
                if not inside(p):
                    out.append(_cross(a, b, p, q))
                out.append(q)
            elif inside(p):
                out.append(_cross(a, b, p, q))
        if not out:
            return []
    return out


def _cross(a, b, p, q):
    d1 = (b[0] - a[0], b[1] - a[1])
    d2 = (q[0] - p[0], q[1] - p[1])
    den = d1[0] * d2[1] - d1[1] * d2[0]
    t = ((a[0] - p[0]) * d1[1] - (a[1] - p[1]) * d1[0]) / den if abs(den) > 1e-12 else 0.0
    return (p[0] + t * d2[0], p[1] + t * d2[1])


def _area(poly):
    return sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly))) / 2


def coplanar(elements, gap=0.015):
    """Every pair of faces, whatever their elements' rotations, that face the same way, lie within gap of one plane and
    overlap: these z-fight. Returns (element, face, element, face, overlap area)."""
    faces = list(_faces(elements))
    hits = []
    for x in range(len(faces)):
        i, fi, qi, ni = faces[x]
        di = sum(ni[c] * qi[0][c] for c in range(3))
        # A basis in the plane.
        ref = (1, 0, 0) if abs(ni[0]) < 0.9 else (0, 1, 0)
        u = [ni[1] * ref[2] - ni[2] * ref[1], ni[2] * ref[0] - ni[0] * ref[2], ni[0] * ref[1] - ni[1] * ref[0]]
        un = math.sqrt(sum(v * v for v in u))
        u = [v / un for v in u]
        v = [ni[1] * u[2] - ni[2] * u[1], ni[2] * u[0] - ni[0] * u[2], ni[0] * u[1] - ni[1] * u[0]]
        flat = lambda q: [(sum(p[c] * u[c] for c in range(3)), sum(p[c] * v[c] for c in range(3))) for p in q]
        pi = flat(qi)
        if _area(pi) < 0:
            pi.reverse()
        for y in range(x + 1, len(faces)):
            j, fj, qj, nj = faces[y]
            if i == j or sum(ni[c] * nj[c] for c in range(3)) < 0.9999:
                continue
            if abs(sum(ni[c] * qj[0][c] for c in range(3)) - di) > gap:
                continue
            pj = flat(qj)
            if _area(pj) < 0:
                pj.reverse()
            overlap = _clip(pj, pi)
            a = abs(_area(overlap)) if len(overlap) >= 3 else 0.0
            if a > 1e-3:
                hits.append((i, elements[i].get("name", fi), fi, j, elements[j].get("name", fj), fj, round(a, 4)))
    return hits


def check(name, elements):
    hits = coplanar(elements)
    for h in hits:
        print("z-fight:", name, h)
    if hits:
        raise SystemExit(name + " has coplanar overlapping faces")
    for e in elements:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (name, e)


# ============================================================================================================ parts


def bit(k, tiers, tip, core, flight, tip_mat, twist=40.0, flight_out=0.8, glow_from=None):
    """The stepped conical bit: tiers (z0, z1, half) from the back, each an eight-pointed bar turned twist past the one
    behind, with two ground flights a half turn apart standing out of it and running a little past its front, so they
    wind down the bit like an auger; then a pointed tip (z0, half). Tiers from glow_from on are tip_mat and glow."""
    out = []
    for i, (z0, z1, h) in enumerate(tiers):
        a = twist * i
        lit = glow_from is not None and i >= glow_from
        star8(k, out, z0, z1, h, tip_mat if lit else core, a, emit=lit, name=f"tier_{i}")
        for s in (0, 180):
            part(k, out, AX + h * 0.5, AY - 0.32, z0 - 0.3, AX + h + flight_out, AY + 0.32, z1 - 0.4, flight,
                 rot=zrot(a + 22.5 + s), name="flight" if s == 0 else None)
    a = twist * len(tiers)
    z0, h = tip
    front = tiers[-1][0]
    lit = glow_from is not None
    square(k, out, z0 + (front - z0) * 0.45, front + 0.02, h, tip_mat, a + 22.5, emit=lit, name="tip")
    square(k, out, z0, z0 + (front - z0) * 0.45 + 0.02, h * 0.5, tip_mat, a + 67.5, emit=lit)
    return out


def chuck(k, out, z0, z1, core_half, jaw_r, jaw_w, core_mat, jaw_mat, jaw_front=None, ring=None, insert=None):
    """The chuck: a collar on the axis with eight chunky clamp jaws round it (four bars through the axis, each two
    jaws, staggered a hair along z), and behind it the toothed drive ring (half, tooth radius, material, z length).
    insert: (material, glow) bars poking out of each jaw's face."""
    star8(k, out, z0 + 0.4, z1, core_half, core_mat, 22.5, inner=0.95, name="chuck_collar")
    for j in range(4):
        d = 0.03 * j
        part(k, out, AX - jaw_r, AY - jaw_w / 2, z0 + d, AX + jaw_r, AY + jaw_w / 2, z1 - 0.5 - d, jaw_mat,
             rot=zrot(22.5 + 45 * j), name="jaw" if j == 0 else None, **({"north": jaw_front} if jaw_front else {}))
        if insert:
            mat, lit = insert
            part(k, out, AX - jaw_r - 0.35, AY - 0.45, z0 + 0.6 + d, AX + jaw_r + 0.35, AY + 0.45, z1 - 1.2 - d, mat,
                 rot=zrot(22.5 + 45 * j), emit=lit, name="insert" if j == 0 else None)
    if ring:
        half, tooth, mat, length = ring
        star8(k, out, z1, z1 + length, half, mat, 0, inner=0.95, name="drive_ring")
        for j in range(4):
            d = 0.03 * j
            part(k, out, AX - tooth, AY - 0.6, z1 + 0.1 + d, AX + tooth, AY + 0.6, z1 + length - 0.1 - d, mat, rot=zrot(45 * j))
    return out


def framed_body(k, out, z0, z1, plate, core, post, frame_mat, panel_mat, post_mat, band_mat, bands, plate_depth=1.0,
                chamfer=False):
    """The boxy body from z0 to z1: a frame plate at each end (half plate), the recessed panel box between (half core),
    a trim post along each long edge standing proud of the panels (outer half post; turned an eighth for a chamfered
    edge), and bands round the middle at the given z."""
    part(k, out, AX - plate, AY - plate, z0, AX + plate, AY + plate, z0 + plate_depth, frame_mat, name="front_plate")
    part(k, out, AX - plate, AY - plate, z1 - plate_depth, AX + plate, AY + plate, z1, frame_mat, name="rear_plate")
    part(k, out, AX - core, AY - core, z0 + plate_depth, AX + core, AY + core, z1 - plate_depth, panel_mat, name="panels",
         north=None, south=None)
    s = 1.3
    for sx in (-1, 1):
        for sy in (-1, 1):
            if chamfer:
                c = core - 0.15
                h = 0.85
                part(k, out, AX + sx * c - h, AY + sy * c - h, z0 + plate_depth, AX + sx * c + h, AY + sy * c + h,
                     z1 - plate_depth, post_mat, rot=zrot(45, AX + sx * c, AY + sy * c, z0), name="trim", north=None, south=None)
            else:
                x = (AX + sx * (post - s), AX + sx * post)
                y = (AY + sy * (post - s), AY + sy * post)
                part(k, out, min(x), min(y), z0 + plate_depth, max(x), max(y), z1 - plate_depth, post_mat, name="post",
                     north=None, south=None)
    for bz, bw in bands:
        bh = core + 0.18
        part(k, out, AX - bh, AY - bh, bz, AX + bh, AY + bh, bz + bw, band_mat, name="band")
    return out


def rear_cross(k, out, z0, reach, hub_mat, bar_mat, cap_mat, grips=None, cap_glow=False):
    """The cross of handles at the back from z0: a hub on the axis, a bar across and a bar up and down through it
    out to reach, and a cap on every end. grips: (material, [(axis, sign)]) sleeves on the handles held."""
    star8(k, out, z0, z0 + 2.2, 2.3, hub_mat, 0, name="hub")
    square(k, out, z0 + 2.2, z0 + 2.8, 1.2, hub_mat, 45)
    zc0, zc1 = z0 + 0.4, z0 + 1.8
    part(k, out, AX - reach, AY - 0.8, zc0, AX + reach, AY + 0.8, zc1, bar_mat, name="cross_bar")
    part(k, out, AX - 0.8, AY - reach, zc0 + 0.05, AX + 0.8, AY + reach, zc1 - 0.05, bar_mat, name="cross_upright")
    c = 1.15
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        x, y = AX + dx * (reach + 0.5), AY + dy * (reach + 0.5)
        part(k, out, x - c, y - c, zc0 - 0.35, x + c, y + c, zc1 + 0.35, cap_mat, emit=cap_glow, name="cap")
    if grips:
        mat, which = grips
        for axis, sign in which:
            a, b = 3.0, reach - 0.8
            if axis == "y":
                y0, y1 = sorted((AY + sign * a, AY + sign * b))
                part(k, out, AX - 1.0, y0, zc0 - 0.12, AX + 1.0, y1, zc1 + 0.12, mat, name="grip")
            else:
                x0, x1 = sorted((AX + sign * a, AX + sign * b))
                part(k, out, x0, AY - 1.0, zc0 - 0.12, x1, AY + 1.0, zc1 + 0.12, mat, name="grip")
    # The handles held: the bottom one (main hand) and the left one (off hand), halfway along their free length.
    return (AX, AY - (reach + 3.0) / 2 - 0.4, (zc0 + zc1) / 2), (AX - (reach + 3.0) / 2 - 0.4, AY, (zc0 + zc1) / 2)


def spun(parts, angle):
    return rotate(copy(parts), rz(angle), (AX, AY, 0))


class Drill:
    """One drill: its static body, its spinning parts per frame, grip points and the bit on its own."""

    def __init__(self, name, kit, body, moving, grip, support, bit_part):
        self.name, self.kit, self.body, self.moving = name, kit, body, moving
        self.grip, self.support, self.bit_part = grip, support, bit_part


# ======================================================================================================= the drills
# Palettes from vanilla: oak and dark oak planks, gold block (brass), iron block and ingot, anvil and heavy core
# (gunmetal), yellow concrete, and the mod's steel, tungsten carbide, star steel, star crystal and rubber.

BODY_Z = (2.0, 12.6)
CROSS_Z = 12.6
REACH = 8.2

# ---------------------------------------------------------------------------------------------------- Hand Drill
# Closest to the reference: an oak body framed in light oak with brass bands and dark recessed panels, an iron bit in a
# polished chuck, dark oak cross handles with light caps, and a brass gear wheel on the left turned by a crank.
HAND = DrillKit("hand_drill", 701)
HAND.wood("oak", 0xD8B47A, 0xB8945F, 0x8F7040, 0x7A5A30, (0xA88A58, 0x6E5430, 0x5A4220))
HAND.wood("dark_oak", 0x6A4A28, 0x4F3218, 0x2E1D0C, 0x241608, (0x4A2F17, 0x2A1A0B, 0x1A1006))
HAND.paint("panel", recess(0x1E140A, 0x3E2912, 0x2C1C0C))
HAND.metal("brass", 0xFFF0A8, 0xF2C04A, 0xB07A22, 0x6E4610, top=(0xFFF6C8, 0xFFE07A, 0xE0A838, 0xB07A22))
HAND.metal("iron", 0xFFFFFF, 0xE2E2E2, 0xA8A8A8, 0x6E6E6E, top=(0xFFFFFF, 0xF2F2F2, 0xD8D8D8, 0xB8B8B8))
HAND.metal("gunmetal", 0x9AA2B0, 0x6A707A, 0x3D4046, 0x26282C, top=(0xB0B8C4, 0x7C838E, 0x5A6068, 0x3D4046))
HAND.metal("edge", 0xFFFFFF, 0xD0D6DE, 0x98A0AA, 0x5E656F)
HAND.metal("cap", 0xFFF2CC, 0xF0CC88, 0xD0A060, 0x9A7038, top=(0xFFF8E0, 0xFADBA0, 0xE8BC78, 0xD0A060))
HAND.save(TEX)

WHEEL = (AX - 4.95, AY + 0.3, 7.2)     # the crank's gear wheel, on the left flank
WHEEL_R = 3.5
CRANK_R = 3.1


def hand_bit():
    return bit(HAND, [(-4.8, -1.8, 2.9), (-7.4, -4.8, 2.4), (-9.9, -7.4, 1.9), (-12.2, -9.9, 1.4), (-14.2, -12.2, 0.95)],
               (-15.8, 0.6), "gunmetal", "edge", "gunmetal")


def hand_spinning():
    out = hand_bit()
    chuck(HAND, out, -2.6, 1.0, 2.9, 5.0, 2.3, "iron", "iron", ring=(3.6, 4.5, "brass", 1.0))
    return out


def hand_drill():
    k = HAND
    body = framed_body(k, [], BODY_Z[0], BODY_Z[1], 4.75, 4.2, 4.5, "oak", "panel", "oak", "brass", [(4.6, 0.9), (9.4, 0.9)])
    part(k, body, AX - 1.6, AY + 4.38, 5.9, AX + 1.6, AY + 4.62, 8.7, "brass", name="nameplate")
    # The gear wheel's bearing: a brass boss on the left flank, and a bracket under it.
    part(k, body, WHEEL[0] + 0.55, WHEEL[1] - 1.0, WHEEL[2] - 1.0, AX - 4.2, WHEEL[1] + 1.0, WHEEL[2] + 1.0, "brass", name="boss")
    grip, support = rear_cross(k, body, CROSS_Z, REACH, "iron", "dark_oak", "cap")
    still = hand_spinning()

    def moving(frame):
        crank = []
        wx, wy, wz = WHEEL
        s = WHEEL_R / math.sqrt(2) * 1.08
        part(k, crank, wx - 0.3, wy - s, wz - s, wx + 0.3, wy + s, wz + s, "brass", name="gear_wheel")
        part(k, crank, wx - 0.27, wy - s, wz - s, wx + 0.27, wy + s, wz + s, "brass", rot=("x", 45, (wx, wy, wz)))
        part(k, crank, wx - 0.9, wy - 0.7, wz - 0.7, wx - 0.3, wy + 0.7, wz + 0.7, "iron", name="crank_hub")
        part(k, crank, wx - 1.5, wy - 0.45, wz - 0.5, wx - 0.9, wy + CRANK_R + 0.5, wz + 0.5, "iron", name="crank_arm")
        part(k, crank, wx - 4.1, wy + CRANK_R - 0.7, wz - 0.7, wx - 1.5, wy + CRANK_R + 0.7, wz + 0.7, "dark_oak", name="knob")
        part(k, crank, wx - 4.6, wy + CRANK_R - 0.55, wz - 0.55, wx - 4.1, wy + CRANK_R + 0.55, wz + 0.55, "cap")
        rotate(crank, rx(-HAND_STEP * frame), WHEEL)
        return spun(still, HAND_STEP * frame) + crank

    knobs = []
    for f in range(FRAMES):
        a = math.radians(-HAND_STEP * f)
        y, z = CRANK_R * math.cos(a), CRANK_R * math.sin(a)
        knobs.append((WHEEL[0] - 2.9, WHEEL[1] + y, WHEEL[2] + z))
    return Drill("hand_drill", k, body, moving, grip, knobs, hand_bit)


# --------------------------------------------------------------------------------------------- the motor drills


def motor_drill(name, k, bit_fn, chuck_args, body_args, extras):
    body = framed_body(k, [], BODY_Z[0], BODY_Z[1], *body_args["frame"], **body_args.get("kw", {}))
    grip, support = rear_cross(k, body, CROSS_Z, REACH, *body_args["cross"], **body_args.get("cross_kw", {}))
    extras(k, body)
    still = bit_fn()
    chuck(k, still, *chuck_args[0], **chuck_args[1])

    def moving(frame):
        return spun(still, MOTOR_STEP * frame)

    return Drill(name, k, body, moving, grip, [support] * FRAMES, bit_fn)


def finned_motor(k, out, block, fin, fins=4):
    """A motor block on top of the body with cooling fins across it."""
    part(k, out, AX - 3.0, AY + 4.75, 3.6, AX + 3.0, AY + 7.2, 10.6, block, name="motor")
    for i in range(fins):
        z = 4.2 + i * 1.5
        part(k, out, AX - 3.5, AY + 4.95, z, AX + 3.5, AY + 7.7, z + 0.5, fin, name="fin" if i == 0 else None)


def side_tank(k, out, cx, cy, z0, z1, r, mat, strap, cap):
    """A fuel tank along z beside the body: an octagonal drum, two straps round it and a filler cap on top."""
    a = 2 * r * math.tan(math.pi / 8)
    for i, (angle, w, h) in enumerate([(0, r, a / 2), (0, a / 2, r), (45, r, a / 2), (-45, r, a / 2)]):
        d = 0.03 * i
        part(k, out, cx - w, cy - h, z0 + d, cx + w, cy + h, z1 - d, mat, rot=zrot(angle, cx, cy, z0), name="tank" if i == 0 else None)
    for z in (z0 + 1.0, z1 - 1.6):
        part(k, out, cx - r - 0.2, cy - r - 0.2, z, cx + r + 0.2, cy + r + 0.2, z + 0.6, strap, name="strap")
    part(k, out, cx - 0.6, cy + r - 0.3, z0 + 1.9, cx + 0.6, cy + r + 0.6, z0 + 3.1, cap, name="filler_cap")


# ---------------------------------------------------------------------------------------------------- Steel Drill
STEEL = DrillKit("steel_drill", 702)
STEEL.metal("steel", 0xE8F0F7, 0xA4AEB9, 0x6E7884, 0x48515B, top=(0xF4F8FC, 0xC4CCD4, 0xA4AEB9, 0x828D99))
STEEL.metal("steel_dark", 0x828D99, 0x4D5661, 0x2C323A, 0x16191E, top=(0x9AA4AE, 0x5C6672, 0x48515B, 0x2C323A))
STEEL.paint("panel", recess(0x16191E, 0x48515B, 0x323840))
STEEL.metal("polished", 0xFFFFFF, 0xE6EAEE, 0xA8B0B8, 0x707880, top=(0xFFFFFF, 0xF4F6F8, 0xDCE0E4, 0xB8C0C8))
STEEL.metal("bit", 0xA4AEB9, 0x6E7884, 0x3C434C, 0x22272E)
STEEL.metal("edge", 0xFFFFFF, 0xDDE4EA, 0xA4AEB9, 0x6E7884)
STEEL.metal("red", 0xF6A08A, 0xE0503C, 0xA82C1E, 0x6E1A10, top=(0xFFB8A0, 0xF06A52, 0xD84A34, 0xB83424))
STEEL.paint("rubber", ribbed(0x45484E, 0x222327, 0x0E0E10))
STEEL.metal("hose", 0x5A5E65, 0x313338, 0x1E1F23, 0x0E0E10)
STEEL.save(TEX)


def steel_bit():
    return bit(STEEL, [(-4.8, -1.8, 2.9), (-7.4, -4.8, 2.4), (-9.9, -7.4, 1.9), (-12.2, -9.9, 1.4), (-14.2, -12.2, 0.95)],
               (-15.8, 0.6), "bit", "edge", "edge")


def steel_extras(k, out):
    finned_motor(k, out, "steel_dark", "steel")
    # The exhaust: a stack up from the motor's left rear, its outlet turned back.
    part(k, out, AX - 2.6, AY + 6.4, 10.0, AX - 1.4, AY + 9.6, 11.2, "steel_dark", name="exhaust")
    part(k, out, AX - 2.75, AY + 9.1, 10.0, AX - 1.25, AY + 9.9, 12.2, "steel_dark", name="exhaust_outlet",
         south="hose")
    side_tank(k, out, AX + 6.6, AY - 1.0, 3.4, 11.0, 1.9, "red", "steel_dark", "polished")
    # The fuel line: up from the tank's rear and across into the motor.
    part(k, out, AX + 6.2, AY + 0.8, 9.6, AX + 7.0, AY + 6.4, 10.4, "hose", name="fuel_line")
    part(k, out, AX + 3.0, AY + 5.7, 9.7, AX + 6.6, AY + 6.3, 10.3, "hose")
    # The trigger, in front of the bottom handle.
    part(k, out, AX - 0.4, AY - 6.2, CROSS_Z - 0.5, AX + 0.4, AY - 4.75, CROSS_Z + 0.4, "red", name="trigger")


def steel_drill():
    return motor_drill("steel_drill", STEEL, steel_bit,
                       ((-2.6, 1.0, 2.9, 5.0, 2.3, "steel_dark", "polished"), {"ring": (3.6, 4.5, "steel_dark", 1.0)}),
                       {"frame": (4.75, 4.2, 4.5, "steel", "panel", "steel_dark", "steel_dark", [(6.9, 0.8)]),
                        "cross": ("steel_dark", "steel", "polished"),
                        "cross_kw": {"grips": ("rubber", [("y", -1), ("x", -1)])}},
                       steel_extras)


# ------------------------------------------------------------------------------------------ Tungsten Carbide Drill
CARBIDE = DrillKit("tungsten_carbide_drill", 703)
CARBIDE.metal("gunmetal", 0x9299AB, 0x4E5259, 0x313436, 0x202020, top=(0xA4ABBA, 0x5E656F, 0x4E5259, 0x3D3F43))
CARBIDE.metal("gunmetal_dark", 0x5E656F, 0x313436, 0x202020, 0x121212)
CARBIDE.paint("panel", recess(0x111214, 0x2C2E32, 0x1E2022))
CARBIDE.metal("yellow", 0xFFF0A0, 0xFFD040, 0xE8A814, 0xA87008, top=(0xFFF6C0, 0xFFDC60, 0xF2B016, 0xD89A10))
CARBIDE.paint("hazard", stripes(0xFFD040, 0xE8A814, 0x1E2022))
CARBIDE.metal("carbide", 0xB8BCC4, 0x767A82, 0x45484E, 0x2C2E32)
CARBIDE.metal("carbide_tip", 0xFFFFFF, 0xE4E8EE, 0xB8BCC4, 0x8A9098)
CARBIDE.metal("polished", 0xFFFFFF, 0xD8DCE2, 0x9498A0, 0x5C6068, top=(0xFFFFFF, 0xE8ECF0, 0xC4C8CE, 0xA0A4AC))
CARBIDE.paint("rubber", ribbed(0x45484E, 0x222327, 0x0E0E10))
CARBIDE.metal("hose", 0x5A5E65, 0x313338, 0x1E1F23, 0x0E0E10)
CARBIDE.paint("glow", glow(0xD8FFC0, 0x46D83A), picture=True)
CARBIDE.save(TEX)


def carbide_bit():
    return bit(CARBIDE, [(-5.2, -1.8, 3.4), (-8.0, -5.2, 2.8), (-10.6, -8.0, 2.2), (-12.9, -10.6, 1.6), (-14.6, -12.9, 1.1)],
               (-15.9, 0.75), "carbide", "carbide_tip", "carbide_tip", flight_out=0.9)


def carbide_extras(k, out):
    finned_motor(k, out, "gunmetal_dark", "gunmetal", fins=5)
    part(k, out, AX - 2.6, AY + 6.4, 10.0, AX - 1.4, AY + 9.8, 11.2, "gunmetal_dark", name="exhaust")
    part(k, out, AX - 2.75, AY + 9.3, 10.0, AX - 1.25, AY + 10.1, 12.2, "yellow", name="exhaust_outlet", south="hose")
    side_tank(k, out, AX + 7.3, AY - 1.2, 3.0, 11.6, 2.5, "yellow", "gunmetal_dark", "polished")
    part(k, out, AX + 6.9, AY + 1.2, 10.15, AX + 7.7, AY + 6.4, 10.95, "hose", name="fuel_line")
    part(k, out, AX + 3.0, AY + 5.7, 10.25, AX + 7.3, AY + 6.3, 10.85, "hose")
    # Hazard stripes down the front plate's flanks, and the fuel rod window on the left.
    for sx in (-1, 1):
        x = AX + sx * 4.85
        part(k, out, min(x, x + sx * 0.2), AY - 3.6, 2.2, max(x, x + sx * 0.2), AY + 3.6, 2.8, "hazard", name="hazard")
    part(k, out, AX - 4.42, AY - 1.0, 6.0, AX - 4.2, AY + 1.6, 8.6, "glow", emit=True, name="fuel_window",
         east=None, north=None, south=None, up=None, down=None)
    part(k, out, AX - 0.4, AY - 6.2, CROSS_Z - 0.5, AX + 0.4, AY - 4.75, CROSS_Z + 0.4, "yellow", name="trigger")


def carbide_drill():
    return motor_drill("tungsten_carbide_drill", CARBIDE, carbide_bit,
                       ((-2.8, 1.0, 3.2, 5.5, 2.7, "gunmetal_dark", "polished"),
                        {"jaw_front": "yellow", "ring": (4.0, 5.0, "yellow", 1.0)}),
                       {"frame": (4.75, 4.2, 4.5, "gunmetal", "panel", "yellow", "yellow", [(6.4, 0.7), (8.6, 0.7)]),
                        "cross": ("gunmetal_dark", "gunmetal", "yellow"),
                        "cross_kw": {"grips": ("rubber", [("y", -1), ("x", -1)])}},
                       carbide_extras)


# ---------------------------------------------------------------------------------------------------- Star Drill
STAR = DrillKit("star_drill", 704)
STAR.metal("star_steel", 0x67ACE5, 0x3355B2, 0x1B2A6E, 0x0F1743, top=(0x8CC4F0, 0x4A70C8, 0x3355B2, 0x29429B))
STAR.metal("star_dark", 0x3355B2, 0x1B2A6E, 0x0F1743, 0x0A0E2E)
STAR.paint("panel", recess(0x0A0E2E, 0x1B2A6E, 0x111C4E))
STAR.metal("trim", 0xFFFFFF, 0xD3DBEA, 0x9AA6C0, 0x5E6A88, top=(0xFFFFFF, 0xE6ECF6, 0xC4CEE0, 0x9AA6C0))
STAR.metal("crystal", 0xF4FBFF, 0xB8F2FF, 0x63D0EE, 0x3A9CC8)
STAR.metal("violet", 0xA678F1, 0x7A5BB5, 0x4C34A8, 0x2A1758)
STAR.paint("core", glow(0xF4FBFF, 0x63D0EE), picture=True)
STAR.paint("rubber", ribbed(0x3A3C52, 0x1C1E2C, 0x0A0B12))
STAR.save(TEX)


def star_bit():
    return bit(STAR, [(-4.8, -1.8, 2.9), (-7.4, -4.8, 2.4), (-9.9, -7.4, 1.9), (-12.2, -9.9, 1.4), (-14.2, -12.2, 0.95)],
               (-15.9, 0.65), "star_dark", "trim", "crystal", glow_from=3)


def star_extras(k, out):
    # A smooth cowl over the motor instead of fins, stepped down at the front.
    part(k, out, AX - 2.8, AY + 4.75, 4.0, AX + 2.8, AY + 6.6, 11.2, "star_steel", name="cowl")
    part(k, out, AX - 1.9, AY + 6.6, 5.4, AX + 1.9, AY + 7.5, 10.4, "trim", name="cowl_top")
    part(k, out, AX - 0.5, AY + 7.5, 6.4, AX + 0.5, AY + 7.8, 9.6, "crystal", emit=True, name="cowl_crystal")
    # A slender tank of star steel on the right with a glowing gauge, and the star crystal core shining from both flanks.
    side_tank(k, out, AX + 6.4, AY - 1.0, 3.6, 10.8, 1.7, "star_steel", "trim", "trim")
    part(k, out, AX + 8.1, AY - 1.5, 5.6, AX + 8.2, AY - 0.5, 8.6, "core", emit=True, name="gauge",
         west=None, north=None, south=None, up=None, down=None)
    for sx in (-1, 1):
        x0, x1 = (AX - 4.42, AX - 4.2) if sx < 0 else (AX + 4.2, AX + 4.42)
        part(k, out, x0, AY - 1.4, 5.2, x1, AY + 1.4, 9.4, "core", emit=True, name="star_core",
             **{("east" if sx < 0 else "west"): None, "north": None, "south": None, "up": None, "down": None})
    part(k, out, AX - 0.4, AY - 6.2, CROSS_Z - 0.5, AX + 0.4, AY - 4.75, CROSS_Z + 0.4, "violet", name="trigger")


def star_drill():
    return motor_drill("star_drill", STAR, star_bit,
                       ((-2.6, 1.0, 2.9, 5.0, 2.2, "violet", "trim"),
                        {"insert": ("crystal", True), "ring": (3.6, 4.4, "star_dark", 1.0)}),
                       {"frame": (4.75, 4.2, 4.5, "trim", "panel", "trim", "trim", [(7.0, 0.6)]), "kw": {"chamfer": True},
                        "cross": ("star_dark", "star_steel", "crystal"),
                        "cross_kw": {"grips": ("rubber", [("y", -1), ("x", -1)]), "cap_glow": True}},
                       star_extras)


# ======================================================================================================= displays


def fitted(rotation, elements, size, axes=(0, 1)):
    """Scale and translation that fit the elements, turned by rotation, into size pixels of the slot (along the given
    axes: the slot's x and y, or all three for an item that tumbles on the ground), centred."""
    m = display_matrix(rotation)
    pts = []
    for e in elements:
        corners = [[(e["from"], e["to"])[(i >> a) & 1][a] for a in range(3)] for i in range(8)]
        if e.get("rotation"):
            rm, o = element_rotation(e)
            corners = [[v + o[a] for a, v in enumerate(apply(rm, [c[a] - o[a] for a in range(3)]))] for c in corners]
        pts += [apply(m, [v - 8 for v in c]) for c in corners]
    lo = [min(p[a] for p in pts) for a in range(3)]
    hi = [max(p[a] for p in pts) for a in range(3)]
    scale = round(size / max(hi[a] - lo[a] for a in axes), 3)
    return scale, [round(-(lo[a] + hi[a]) / 2 * scale, 3) for a in range(3)]


def still_displays(elements, size=16.0, rot=None):
    """In the inventory, in item frames and on the ground."""
    rot = [round(v, 2) for v in (rot or GUI_ROT)]
    scale, tr = fitted(rot, elements, size)
    flat_scale, flat_tr = fitted([0, 0, 0], elements, size * 0.6, (0, 1, 2))
    return [("gui", display(rot, tr, scale)), ("ground", display([0, 0, 0], [flat_tr[0], flat_tr[1] + 2.0, flat_tr[2]], flat_scale)),
            ("fixed", display(rot, tr, scale))]


def held_displays(grip, elements):
    fp = display(FP_ROT, placed(FP_ROT, FP_SCALE, grip, FP_GRIP_TARGET), FP_SCALE)
    tp = display([0, 0, 0], placed([0, 0, 0], TP_SCALE, grip, TP_GRIP_TARGET), TP_SCALE)
    return OrderedDict([("thirdperson_righthand", tp), ("thirdperson_lefthand", tp), ("firstperson_righthand", fp),
                        ("firstperson_lefthand", fp)] + still_displays(elements))


def part_displays(elements):
    """A loose bit: on the diagonal in the inventory, held like any small item."""
    c = bounds_centre(elements)
    held = [("thirdperson_righthand", display([0, 0, 0], centred([0, 0, 0], 0.6, c, 2.0), 0.6)),
            ("thirdperson_lefthand", display([0, 0, 0], centred([0, 0, 0], 0.6, c, 2.0), 0.6)),
            ("firstperson_righthand", display([0, -8, 0], centred([0, -8, 0], 0.6, c, 3.0), 0.6)),
            ("firstperson_lefthand", display([0, -8, 0], centred([0, -8, 0], 0.6, c, 3.0), 0.6))]
    return OrderedDict(held + still_displays(elements, 14.0))


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 3) for i in range(3)]


# ========================================================================================================= output
DRILLS = [hand_drill(), steel_drill(), carbide_drill(), star_drill()]
BITS = {"iron_drill_bit": DRILLS[0], "steel_drill_bit": DRILLS[1], "carbide_drill_bit": DRILLS[2], "star_drill_bit": DRILLS[3]}
STILL = ["gui", "ground", "fixed"]
HANDS = {}

for d in DRILLS:
    credit = f"tomyen {d.name} - generated by tools/gen_drills.py"
    whole = d.body + d.moving(0)
    displays = held_displays(d.grip, whole)
    write(os.path.join(MODELS, d.name + ".json"), model(d.kit, d.body, displays, credit=credit))
    frames = []
    for f in range(FRAMES):
        parts = d.moving(f)
        check(f"{d.name}_spin_{f}", d.body + parts)
        write(os.path.join(MODELS, f"{d.name}_spin_{f}.json"), model(d.kit, parts, displays, credit=credit))
        frames.append(ref(f"{d.name}_spin_{f}"))
    still = composite(ref(d.name), frames[0])
    spinning = composite(ref(d.name), range_dispatch("tomyen:drill_spin", [(f, frames[f]) for f in range(1, FRAMES)], frames[0]))
    write(os.path.join(ITEMS, d.name + ".json"), {"model": OrderedDict([
        ("type", "minecraft:select"), ("property", "minecraft:display_context"),
        ("cases", [OrderedDict([("when", STILL), ("model", still)])]), ("fallback", spinning)])})
    HANDS[d.name] = {"grip": fp_point(displays, d.grip), "support": [fp_point(displays, s) for s in d.support]}
    print(f"{d.name}: {len(whole)} elements ({len(d.body)} body, {len(whole) - len(d.body)} spinning), {FRAMES} frames")
    print(f"   grip {HANDS[d.name]['grip']}  support {HANDS[d.name]['support'] if d.name == 'hand_drill' else HANDS[d.name]['support'][0]}")
if os.environ.get("DRILL_HANDS"):
    write(os.environ["DRILL_HANDS"], HANDS)

for name, d in BITS.items():
    bit_elements = d.bit_part()
    check(name, bit_elements)
    write(os.path.join(MODELS, name + ".json"), model(d.kit, bit_elements, part_displays(bit_elements),
                                                      credit=f"tomyen {name} - generated by tools/gen_drills.py"))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


# ========================================================================================================== data


def write_json(path, obj):
    write(path, obj)


def merge_tag(path, values):
    """Adds values to a tag file, keeping whatever's there (other features share the vanilla tag files)."""
    full = os.path.join(DATA, path + ".json")
    existing = json.load(open(full))["values"] if os.path.exists(full) else []
    write(full, {"values": existing + [v for v in values if v not in existing]})


def unmerge_tag(path, values):
    full = os.path.join(DATA, path + ".json")
    if os.path.exists(full):
        existing = json.load(open(full))["values"]
        write(full, {"values": [v for v in existing if v not in values]})


def merge_json(path, entries):
    with open(path) as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def advancement(name, category, unlock_items):
    criteria = OrderedDict()
    for i, item in enumerate(unlock_items):
        criteria["has_item" if i == 0 else f"has_item_{i}"] = {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"}
    criteria["has_the_recipe"] = {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, "tomyen", "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria), ("requirements", [list(criteria)]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def shaped(name, pattern, key, unlock, category="equipment"):
    write(os.path.join(DATA, "tomyen", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", {"count": 1, "id": "tomyen:" + name})]))
    advancement(name, category, unlock)


M = "tomyen:"
# Bits: twisted bars of their metal, the zig-zag of the pattern the twist. The iron bit unlocks with tech scrap, so it
# shows up around the Weapon Station and the first guns.
shaped("iron_drill_bit", [" I", "I ", " I"], {"I": "minecraft:iron_ingot"}, [M + "tech_scrap"], category="misc")
shaped("steel_drill_bit", [" S", "S ", " S"], {"S": M + "steel_ingot"}, [M + "steel_ingot"], category="misc")
shaped("carbide_drill_bit", [" C", "S ", " C"], {"C": M + "tungsten_carbide", "S": M + "steel_ingot"}, [M + "tungsten_carbide"],
       category="misc")
shaped("star_drill_bit", [" X", "S ", " X"], {"X": M + "star_crystal_shard", "S": M + "star_steel_ingot"},
       [M + "star_crystal_shard", M + "star_steel_ingot"], category="misc")
# The drills: a bit up top right, the works in the middle, the grip bottom left.
shaped("hand_drill", [" CB", "STI", "S  "],
       {"B": M + "iron_drill_bit", "C": "minecraft:copper_ingot", "S": "minecraft:stick", "T": M + "tech_scrap", "I": "minecraft:iron_ingot"},
       [M + "iron_drill_bit", M + "tech_scrap"])
shaped("steel_drill", ["PAB", "ECP", "R  "],
       {"B": M + "steel_drill_bit", "P": M + "steel_plate", "A": M + "servo_arm", "E": M + "empty_canister", "C": M + "computer_chip",
        "R": M + "rubber"}, [M + "steel_drill_bit", M + "servo_arm"])
shaped("tungsten_carbide_drill", [" TB", "CDT", "P  "],
       {"B": M + "carbide_drill_bit", "T": M + "tungsten_carbide", "C": M + "computer_chip", "D": M + "steel_drill", "P": M + "steel_plate"},
       [M + "carbide_drill_bit", M + "tungsten_carbide"])
shaped("star_drill", [" XB", "SDX", "S  "],
       {"B": M + "star_drill_bit", "X": M + "star_crystal_shard", "S": M + "star_steel_ingot", "D": M + "tungsten_carbide_drill"},
       [M + "star_drill_bit"])

# Tags. Blocks in tomyen:needs_drill take any drill (the Hand Drill is enough), tomyen:needs_powered_drill a running
# Steel Drill or better. Every vanilla tool tier counts them incorrect, so pickaxes chip at them slowly for nothing.
DRILL_ITEMS = [M + d.name for d in DRILLS]
merge_tag("tomyen/tags/item/drills", DRILL_ITEMS)
merge_tag("minecraft/tags/item/pickaxes", ["#tomyen:drills"])
unmerge_tag("minecraft/tags/item/pickaxes", [M + "tungsten_carbide_drill"])
merge_tag("tomyen/tags/item/star_steel_tools", [M + "star_drill"])
merge_tag("tomyen/tags/item/repairs_hand_drill", ["minecraft:iron_ingot"])
merge_tag("tomyen/tags/item/repairs_steel_drill", [M + "steel_ingot"])
merge_tag("tomyen/tags/block/needs_drill", [M + "meteorite_stone", M + "tungsten_ore"])
merge_tag("tomyen/tags/block/needs_powered_drill", [M + "meteoric_iron_ore"])
unmerge_tag("minecraft/tags/block/needs_diamond_tool", [M + "tungsten_ore"])
unmerge_tag("minecraft/tags/block/needs_iron_tool", [M + "meteoric_iron_ore"])
merge_tag("tomyen/tags/block/incorrect_for_hand_drill", ["#minecraft:needs_diamond_tool", "#tomyen:needs_powered_drill"])
merge_tag("tomyen/tags/block/incorrect_for_powered_drill", [])
for tier in ("wooden", "stone", "copper", "iron", "gold", "diamond", "netherite"):
    merge_tag(f"minecraft/tags/block/incorrect_for_{tier}_tool", ["#tomyen:needs_drill", "#tomyen:needs_powered_drill"])

merge_json(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("item.tomyen.hand_drill", "Hand Drill"),
    ("item.tomyen.steel_drill", "Steel Drill"),
    ("item.tomyen.tungsten_carbide_drill", "Tungsten Carbide Drill"),
    ("item.tomyen.star_drill", "Star Drill"),
    ("item.tomyen.iron_drill_bit", "Iron Drill Bit"),
    ("item.tomyen.steel_drill_bit", "Steel Drill Bit"),
    ("item.tomyen.carbide_drill_bit", "Carbide Drill Bit"),
    ("item.tomyen.star_drill_bit", "Star Drill Bit"),
    ("item.tomyen.drill.hand_hint", "Hand-cranked: needs no fuel. Bores meteorite stone and tungsten ore"),
    ("item.tomyen.drill.powered_hint", "Bores any drill-only rock while it has fuel"),
    ("item.tomyen.drill.fuel", "Fuel: %s%% (%s / %s)"),
    ("item.tomyen.drill.empty", "Out of fuel: digs like a stone pickaxe"),
    ("item.tomyen.drill.refuel", "Use to refuel with %s"),
    ("item.tomyen.drill.mode_wide", "Mode: 3×3 (sneak to dig one block)"),
    ("item.tomyen.drill.mode_single", "Mode: single block"),
    ("item.tomyen.drill.toggle_hint", "Sneak and use to switch 1×1 / 3×3"),
    ("item.tomyen.drill.switched_wide", "Drilling 3×3"),
    ("item.tomyen.drill.switched_single", "Drilling one block"),
    ("item.tomyen.drill.refuelled", "Refuelled: %s%%"),
    ("item.tomyen.drill.tank_full", "The tank's too full for another %s"),
    ("item.tomyen.drill.no_fuel", "No fuel to refill it with"),
    ("item.tomyen.drill.or", "%s or %s"),
    ("subtitles.tomyen.drill_motor", "Drill whirrs"),
    ("subtitles.tomyen.hand_drill_crank", "Drill gears click"),
]))
merge_json(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("drill_motor", {"subtitle": "subtitles.tomyen.drill_motor", "sounds": ["tomyen:drill_motor"]}),
    ("hand_drill_crank", {"subtitle": "subtitles.tomyen.hand_drill_crank", "sounds": ["tomyen:hand_drill_crank"]}),
]))
write(os.path.join(ASSETS, "particles", "drill_spark.json"), {"textures": [f"minecraft:spark_{i}" for i in range(7, -1, -1)]})

# ========================================================================================================= sounds
RATE = 44100


def lowpass(xs, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    y, out = 0.0, []
    for x in xs:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(xs, cutoff):
    return [x - l for x, l in zip(xs, lowpass(xs, cutoff))]


def write_ogg(name, samples, gain=1.0):
    """ffmpeg's built-in Vorbis encoder only writes stereo, which Minecraft doesn't fade with distance, so DrillSound
    fades itself, as the flamethrower's roar does."""
    peak = max(abs(v) for v in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(math.tanh(1.3 * v / peak) / math.tanh(1.3) * 30000 * gain)) for v in samples)
    path = os.path.join(ASSETS, "sounds", name + ".ogg")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(frames)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "4", path],
                       check=True)
    print("wrote", path)


def motor(seconds=1.0):
    """The motor's whirr, seamless when looped: a whining fundamental with its harmonics (whole cycles in the loop), the
    buzz of the gear teeth meshing, a little brush noise, and a slow wobble as it labours."""
    sr = random.Random(11)
    n = int(RATE * seconds)
    noise = highpass([sr.uniform(-1, 1) for _ in range(n)], 2500)
    noise = lowpass(noise, 7000)
    out = []
    for i in range(n):
        t = i / RATE
        wobble = 1.0 + 0.08 * math.sin(math.tau * 6 * t)
        whine = (0.55 * math.sin(math.tau * 220 * t) + 0.3 * math.sin(math.tau * 440 * t) + 0.18 * math.sin(math.tau * 660 * t)
                 + 0.1 * math.sin(math.tau * 1320 * t))
        # The gears: a rough pulse train at 110 Hz, its edges softened.
        gear = math.tanh(6 * math.sin(math.tau * 110 * t)) * 0.18
        out.append((whine + gear) * wobble + noise[i] * 0.35)
    return out


def crank(seconds=1.0):
    """The Hand Drill's gears turning: soft ticks of the teeth (16 a second) over a faint grinding hum, louder twice a
    turn where the crank pushes hardest."""
    sr = random.Random(13)
    n = int(RATE * seconds)
    rough = lowpass(highpass([sr.uniform(-1, 1) for _ in range(n)], 600), 3000)
    out = []
    period = RATE // 16
    for i in range(n):
        t = i / RATE
        since = (i % period) / RATE
        tick = sr.uniform(-1, 1) * math.exp(-since / 0.003) * 0.9 + math.sin(math.tau * 1900 * since) * math.exp(-since / 0.006) * 0.5
        push = 0.75 + 0.25 * math.cos(math.tau * 2 * t)
        out.append((tick + rough[i] * 0.25 + 0.12 * math.sin(math.tau * 90 * t)) * push)
    return out


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping sounds")
else:
    write_ogg("drill_motor", motor(), 0.8)
    write_ogg("hand_drill_crank", crank(), 0.6)
print("drill assets written")
