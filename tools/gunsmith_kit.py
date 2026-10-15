"""Shared art and checks for the Gunsmith set's generators (gen_gunsmith.py, gen_gunsmith_attachments.py): one clean texture
atlas of material ramps (the same construction as gen_shoddy.py's), the part builders that texture from it, a world-space
z-fight check, display transforms and a small software renderer for previews.

Art rules (the user's): clean, deliberate texturing with no random noise; clear material reads. Metal is shaded as metal:
round parts are octagons whose faces step from a bright top through mid tones to a dark underside, plates get a crisp
highlight line along their top edge. Wood carries a few placed grain lines, a lighter top edge, darker end grain on cuts.
Every swatch's UV cut starts at its top-left corner, so a part's top edge always gets the swatch's highlight row.

Model space is in pixels: the bore runs along z at x = 8 with the muzzle toward -z, +x is the gun's right, +y up."""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, ry, rz, write, model, ref, display, display_matrix, placed, centred, bounds,
                    png, element_rotation, mul, apply, IDENTITY)

FRAMES = 6
# Where long guns put the shooting hand in first person (the M4's grip point, item space pixels), and pistols (the Glock's).
FP_RIFLE_GRIP = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
FP_PISTOL_GRIP = (-0.020 * 16, 0.131 * 16, -0.181 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))

# Metal and plastic ramps, brightest to darkest (highlight, light, mid, low, dark).
RAMPS = {
    "blue": (0x8A93AC, 0x5A6278, 0x495065, 0x3E4453, 0x252C3D),     # blued steel: iron_chain, lantern
    "iron": (0x8E8C8C, 0x676161, 0x595858, 0x494848, 0x343438),     # dark wrought iron: anvil, hopper
    "case": (0xB4B8BE, 0x8F959D, 0x747A83, 0x5C6169, 0x41454C),     # case-hardened action grey
    "pipe": (0xE2E2E2, 0xBDBDBD, 0x9C9C9C, 0x7C7F7A, 0x5E5E5E),     # bright raw steel: iron_bars, iron_ingot
    "steel": (0xEEF2F5, 0xCBD2D8, 0xA9B2BA, 0x87909A, 0x646C75),    # brushed stainless
    "brass": (0xF2DC8E, 0xD4AE52, 0xB08A3E, 0x8C6A2A, 0x5E4418),    # gold_block and gold_ingot, toned down
    "copper": (0xFBA894, 0xE3826C, 0xC26B4C, 0x9A5038, 0x6D3421),   # copper_block
    "servo": (0xC4CCD4, 0xA0A8B0, 0x70767E, 0x50555E, 0x3E424A),    # the Skullcrane's own metal (gen_skullcrane.py)
    "rubber": (0x5D5D5D, 0x4A4A4F, 0x3F3E42, 0x343438, 0x2D2D32),   # hopper_outside, darkest end
    "black": (0x55585F, 0x3A3C42, 0x2C2E33, 0x222328, 0x16171A),    # black polymer
    "orange": (0xFFC07A, 0xF4973E, 0xDC7820, 0xB55E16, 0x84420E),   # signal-orange plastic: orange_terracotta, brightened
    "yellow": (0xFFE584, 0xF8C83A, 0xE2AA1C, 0xB98814, 0x88620C),   # tool-yellow plastic: yellow_concrete
    "amber": (0xF6CE7C, 0xE6AA4C, 0xCC8C30, 0xA66C22, 0x7A4E16),    # latex band: honeycomb
    "leather": (0xB07C4E, 0x93603A, 0x7A4C2C, 0x613A22, 0x452818), # leather
    "red": (0xF07060, 0xD8483A, 0xB8342C, 0x922622, 0x6A1A18),      # the flare's paper hull: red_wool
    "bone": (0xF4EEDC, 0xE2D9C0, 0xCBC0A2, 0xA99E80, 0x857B60),     # a pale paper hull
}
WOODS = {
    "oak": ((0x67502C, 0x7E6237, 0x967441, 0x9F844D, 0xAF8F55, 0xB8945F, 0xC29D62), 1.0),
    "spruce": ((0x553A1F, 0x5A4424, 0x614B2E, 0x70522E, 0x7A5A34, 0x82613A, 0x886539), 1.0),
    # Walnut: dark oak's planks lifted so a side face still reads as wood next to blued steel.
    "walnut": ((0x3A2411, 0x452B13, 0x523418, 0x5C3B1C, 0x684421, 0x744C25, 0x7E542A), 1.25),
}
# Wraps: highlight, body, shadow.
WRAPS = {
    "tape_black": (0x3A3A42, 0x1C1C20, 0x0E0F14),
    "string": (0xF7F7F7, 0xDBDBDB, 0xA9A9A0),
    "line": (0xF2EEDC, 0xD6CFB4, 0xA8A088),    # fishing line on a reel
    "wire": (0xE3826C, 0xB26247, 0x6D3421),
    "cable": (0xE62008, 0xBD2008, 0x941400),
    "spring": (0xE2E2E2, 0x9C9C9C, 0x4E4E4E),
}
WRAP_PERIOD = {"tape_black": (0, 1, 1, 2), "string": (0, 1, 2), "line": (0, 1, 1, 2), "wire": (0, 1, 2), "cable": (1, 1, 1),
               "spring": (0, 1, 2)}
BLACK = 0x101112
LENS, LENS_HOT = 0xD61A1E, 0xFF8C6E
CLEAR = (0, 0, 0, 0)


def lift(hx, k):
    return sum(min(255, int(((hx >> s) & 255) * k)) << s for s in (16, 8, 0))


class Atlas(Kit):
    """A shelf-packed atlas (see gen_shoddy.py): UV cuts start at a swatch's top-left corner; flat colours are cut from inside
    a 4 x 4 block so mipmapping never bleeds a neighbour in."""

    def __init__(self, key, texture, seed, size=128):
        super().__init__(key, texture, seed=seed, size=size)
        self.skyline = [0] * self.size

    def add(self, name, w, h, f, picture=False):
        assert name not in self.sw, name
        y, x = min((max(self.skyline[x:x + w]), x) for x in range(0, self.size - w + 1, 4))
        assert y + h <= self.size, ("atlas full", name)
        for i in range(x, x + w):
            self.skyline[i] = y + h
        for j in range(h):
            for i in range(w):
                c = f(i, j)
                self.atlas[(y + j) * self.size + x + i] = c if isinstance(c, tuple) else self.rgb(c)
        t = 16.0 / self.size
        self.sw[name] = ((x * t, y * t, (x + w) * t, (y + h) * t), picture)

    def flat(self, name, hx):
        self.add(name, 4, 4, lambda i, j: hx, picture=True)
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


def grain(base, pattern, jogs):
    def tone(along, across):
        t = pattern[across]
        for k, a, b in jogs:
            if a <= along < b:
                if across == k:
                    t = pattern[k - 1]
                elif across == k + 1:
                    t = pattern[k]
        return base[t]
    return tone


GRAIN = (6, 5, 4, 2, 4, 4, 3, 4, 4, 5, 2, 4, 4, 4, 3, 4, 4, 2, 4, 5, 4, 3, 4, 4)
GRAIN_JOGS = ((3, 9, 18), (10, 3, 11), (14, 19, 27), (17, 6, 14), (21, 14, 23))


def paint_materials(K, ramps=RAMPS, woods=WOODS, wraps=WRAPS):
    for ramp, (hi, lt, md, lo, dk) in ramps.items():
        for tone, hx in zip(("hi", "lt", "md", "lo", "dk"), (hi, lt, md, lo, dk)):
            K.flat(f"{ramp}_{tone}", hx)
        K.add(f"{ramp}_side", 4, 16, lambda i, j, c=(hi, lt, md): c[min(j, 2)])
        K.add(f"{ramp}_top", 16, 4, lambda i, j, c=(hi, lt): c[min(i, 1)])
    for sp, (planks, k) in woods.items():
        base = [lift(c, k) for c in planks]
        g = grain(base, GRAIN, GRAIN_JOGS)
        K.add(f"{sp}_rows", 32, 24, lambda i, j, g=g: g(i, j))
        gv = grain(base, (4,) + GRAIN[1:], GRAIN_JOGS)
        K.add(f"{sp}_cols", 24, 32, lambda i, j, g=gv: g(j, i))

        def end(i, j, b=base):
            if i in (0, 7) or j in (0, 7):
                return b[1]
            d = math.hypot(i - 3.5, j - 3.5)
            return b[2] if 1.4 < d < 2.4 or d < 0.8 else b[3]
        K.add(f"{sp}_end", 8, 8, end, picture=True)
    for kind, cols in wraps.items():
        period = WRAP_PERIOD[kind]
        K.add(f"{kind}_cols", 16, 4, lambda i, j, c=cols, p=period: c[p[i % len(p)]])
        K.add(f"{kind}_rows", 4, 16, lambda i, j, c=cols, p=period: c[p[j % len(p)]])
        K.flat(f"{kind}_md", cols[1])
    K.flat("black", BLACK)


def bore_picture(ramp, hole=4.6):
    _, lt, _, lo, dk = RAMPS[ramp]

    def f(i, j):
        d = math.hypot(i - 7.5, j - 7.5)
        return BLACK if d < hole else dk if d < hole + 1.0 else lo if d < hole + 2.0 else lt if d < 7.9 else CLEAR
    return f


def screw_picture(ramp):
    hi, lt, md, lo, dk = RAMPS[ramp]
    return lambda i, j: ((lt, lt, lt, md), (lt, dk, md, lo), (lt, md, dk, lo), (md, lo, lo, lo))[j][i]


# ======================================================================================================== builders


class Parts:
    """Part builders bound to one atlas."""

    def __init__(self, K):
        self.K = K

    def tone(self, ramp, t):
        return f"{ramp}_{t}"

    def tube(self, out, z0, z1, r, ramp, cx=8.0, cy=8.0, caps=(True, True), name=None, cross=False, end=None):
        """A round bar along z: a regular octagon of four bands whose faces step from the highlight on top to the dark
        underside, or (cross=True) a plus of two bands for small round parts. Band ends are staggered a hair."""
        hi, lt, md, lo, dk = (self.tone(ramp, t) for t in ("hi", "lt", "md", "lo", "dk"))
        end = end or lt
        if cross:
            h = r * 0.6
            bands = [(0, r, h, dict(east=md, west=md, up=lt, down=lo)), (0, h, r, dict(east=md, west=md, up=hi, down=dk))]
        else:
            a = 2 * r * math.tan(math.pi / 8)
            bands = [(0, r, a / 2, dict(east=md, west=md, up=None, down=None)),
                     (0, a / 2, r, dict(east=None, west=None, up=hi, down=dk)),
                     (45, r, a / 2, dict(east=lt, west=lo, up=None, down=None)),
                     (-45, r, a / 2, dict(east=lo, west=lt, up=None, down=None))]
        first = None
        for k, (angle, w, h, faces) in enumerate(bands):
            d = 0.01 * k
            e = self.K.box(out, cx - w, cy - h, z0 + d, cx + w, cy + h, z1 - d, md, name=(name if k == 0 else None),
                           rot=("z", angle, (cx, cy, z0)) if angle else None,
                           north=end if caps[0] else None, south=end if caps[1] else None, **faces)
            first = first or e
        return first

    def post(self, out, y0, y1, r, ramp, cx=8.0, cz=8.0, name=None, top=None, bottom=None):
        """A round upright: an octagon of four bands along y, shaded by which way each face looks."""
        hi, lt, md, lo, dk = (self.tone(ramp, t) for t in ("hi", "lt", "md", "lo", "dk"))
        a = 2 * r * math.tan(math.pi / 8)
        top = top or lt
        bottom = bottom or dk
        bands = [(0, r, a / 2, dict(east=md, west=lo, north=None, south=None)),
                 (0, a / 2, r, dict(east=None, west=None, north=lo, south=lt)),
                 (45, r, a / 2, dict(east=lt, west=lo, north=None, south=None)),
                 (-45, r, a / 2, dict(east=md, west=lo, north=None, south=None))]
        for k, (angle, w, d, faces) in enumerate(bands):
            s = 0.01 * k
            self.K.box(out, cx - w, y0 + s, cz - d, cx + w, y1 - s, cz + d, md, name=(name if k == 0 else None),
                       rot=("y", angle, (cx, y0, cz)) if angle else None, up=top, down=bottom, **faces)

    def metal(self, out, x0, y0, z0, x1, y1, z1, ramp, rot=None, name=None, **faces):
        """A plate, strap or block: highlight lines along its top edges, the underside dark."""
        f = dict(north=self.tone(ramp, "side"), south=self.tone(ramp, "side"), east=self.tone(ramp, "side"),
                 west=self.tone(ramp, "side"), up=self.tone(ramp, "top"), down=self.tone(ramp, "lo"))
        f.update(faces)
        return self.K.box(out, x0, y0, z0, x1, y1, z1, self.tone(ramp, "md"), rot=rot, name=name, **f)

    def flat(self, out, x0, y0, z0, x1, y1, z1, ramp, tone="md", rot=None, name=None, **faces):
        """A block of one flat tone (small parts where a highlight line would be noise)."""
        return self.K.box(out, x0, y0, z0, x1, y1, z1, self.tone(ramp, tone), rot=rot, name=name, **faces)

    def wood(self, out, x0, y0, z0, x1, y1, z1, sp, rot=None, name=None, upright=False, **faces):
        if upright:
            f = dict(north=f"{sp}_cols", south=f"{sp}_cols", east=f"{sp}_cols", west=f"{sp}_cols", up=f"{sp}_end",
                     down=f"{sp}_end")
        else:
            f = dict(north=f"{sp}_end", south=f"{sp}_end", east=f"{sp}_rows", west=f"{sp}_rows", up=f"{sp}_cols",
                     down=f"{sp}_cols")
        f.update(faces)
        return self.K.box(out, x0, y0, z0, x1, y1, z1, f"{sp}_rows", rot=rot, name=name, **f)

    def wrap(self, out, x0, y0, z0, x1, y1, z1, kind, rot=None, name=None, upright=False, **faces):
        if upright:
            f = dict(north=f"{kind}_rows", south=f"{kind}_rows", east=f"{kind}_rows", west=f"{kind}_rows",
                     up=f"{kind}_md", down=f"{kind}_md")
        else:
            f = dict(north=f"{kind}_md", south=f"{kind}_md", east=f"{kind}_cols", west=f"{kind}_cols",
                     up=f"{kind}_rows", down=f"{kind}_rows")
        f.update(faces)
        return self.K.box(out, x0, y0, z0, x1, y1, z1, f"{kind}_md", rot=rot, name=name, **f)

    def screw(self, out, x, y, z, facing, ramp="pipe", s=0.2, h=0.08):
        side = self.tone(ramp, "md")
        pic = "screw_" + ramp
        if facing == "east":
            return self.K.box(out, x, y - s, z - s, x + h, y + s, z + s, side, east=pic, west=None)
        if facing == "west":
            return self.K.box(out, x - h, y - s, z - s, x, y + s, z + s, side, west=pic, east=None)
        if facing == "south":
            return self.K.box(out, x - s, y - s, z, x + s, y + s, z + h, side, south=pic, north=None)
        if facing == "north":
            return self.K.box(out, x - s, y - s, z - h, x + s, y + s, z, side, north=pic, south=None)
        if facing == "up":
            return self.K.box(out, x - s, y, z - s, x + s, y + h, z + s, side, up=pic, down=None)
        if facing == "down":
            return self.K.box(out, x - s, y - h, z - s, x + s, y, z + s, side, down=pic, up=None)
        raise ValueError(facing)

    def sticker(self, out, x0, y0, z0, x1, y1, z1, mat, face, emit=False, name=None):
        return self.K.sticker(out, x0, y0, z0, x1, y1, z1, mat, face, emit=emit, name=name)


# ========================================================================================================== checks


def corners(e, face):
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def face_polygon(e, face):
    pts = [list(p) for p in corners(e, face)]
    n = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
         "down": (0, -1, 0)}[face]
    r = element_rotation(e)
    if r:
        m, o = r
        pts = [[o[a] + v for a, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))] for p in pts]
        n = apply(m, list(n))
    return pts, n


def clip_area(a, b):
    def area(p):
        return 0.5 * sum(p[i][0] * p[(i + 1) % len(p)][1] - p[(i + 1) % len(p)][0] * p[i][1] for i in range(len(p)))
    if area(a) < 0:
        a = a[::-1]
    if area(b) < 0:
        b = b[::-1]
    out = a
    for i in range(len(b)):
        c, d = b[i], b[(i + 1) % len(b)]
        inside = lambda p: (d[0] - c[0]) * (p[1] - c[1]) - (d[1] - c[1]) * (p[0] - c[0]) >= -1e-9
        src, out = out, []
        for j in range(len(src)):
            p, q = src[j], src[(j + 1) % len(src)]
            if inside(q):
                if not inside(p):
                    out.append(cut(p, q, c, d))
                out.append(q)
            elif inside(p):
                out.append(cut(p, q, c, d))
        if not out:
            return 0.0
    return abs(area(out))


def cut(p, q, c, d):
    a1, b1 = q[1] - p[1], p[0] - q[0]
    a2, b2 = d[1] - c[1], c[0] - d[0]
    det = a1 * b2 - a2 * b1
    if abs(det) < 1e-12:
        return q
    c1, c2 = a1 * p[0] + b1 * p[1], a2 * c[0] + b2 * c[1]
    return [(b2 * c1 - b1 * c2) / det, (a1 * c2 - a2 * c1) / det]


def check_overlaps(name, elements, only=None, allowed=()):
    """Z-fighting in world space: two faces looking the same way in the same plane whose outlines overlap (catches parts
    turned about different pivots too). only: indices of the elements to check against all the others (new parts added to
    an existing model), or None for every pair; allowed: (name, face, name, face) overlaps the model already had."""
    hits = [h for h in find_overlaps(elements, only) if h[:4] not in allowed]
    if hits:
        for h in hits:
            print("coplanar overlap:", name, h)
        raise SystemExit(name + " has coplanar overlapping faces")


def find_overlaps(elements, only=None):
    faces = []
    for i, e in enumerate(elements):
        for face in e["faces"]:
            pts, n = face_polygon(e, face)
            faces.append((i, face, pts, n, sum(n[k] * pts[0][k] for k in range(3))))
    hits = []
    for x in range(len(faces)):
        i, fa, pa, na, da = faces[x]
        for y in range(x + 1, len(faces)):
            j, fb, pb, nb, db = faces[y]
            if only is not None and i not in only and j not in only:
                continue
            if i == j or sum(na[k] * nb[k] for k in range(3)) < 0.9999 or abs(da - db) > 2e-3:
                continue
            ax = max(range(3), key=lambda k: abs(na[k]))
            keep = [k for k in range(3) if k != ax]
            area = clip_area([[p[k] for k in keep] for p in pa], [[p[k] for k in keep] for p in pb])
            if area > 1e-4:
                hits.append((elements[i].get("name", i), fa, elements[j].get("name", j), fb, round(area, 4)))
    return hits


def check_all(name, elements):
    check(name, elements)
    check_overlaps(name, elements)


# ======================================================================================================== displays


def gun_displays(grip, elements, fp_scale, pistol=False, fp_yaw=None, gui_fill=15.0, gui_rot=None):
    """elements: the gun at rest (the inventory icon and the ground and frame views are centred on it)."""
    fp_rot = [0, fp_yaw if fp_yaw is not None else (-6 if pistol else -8), 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, FP_PISTOL_GRIP if pistol else FP_RIFLE_GRIP), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, grip, TP_GRIP_TARGET), 0.65)
    lo, hi = bounds(elements)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    gui_rot = gui_rot or [0, -90, 0]
    if gui_rot == [0, -90, 0]:
        gui_scale = round(gui_fill / max(hi[2] - lo[2], hi[1] - lo[1]), 3)
    else:
        gui_scale = round(gui_fill / max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]), 3)
    ground = 0.5 if pistol else 0.32
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], ground, c, 2.0), ground),
        "fixed": display(gui_rot, centred(gui_rot, gui_scale * 1.3, c), round(gui_scale * 1.3, 3)),
    }


def fp_point(displays, p):
    """A model point (pixels) in the held item's space (blocks), under the first-person display transform: what GunMotion.onGun
    computes in game."""
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


def fp_vector(displays, v):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    return [round(sum(m[i][k] * v[k] for k in range(3)) * s / 16, 4) for i in range(3)]


def rot_point(p, axis, angle, pivot):
    m = {"x": rx, "y": ry, "z": rz}[axis](angle)
    rel = [p[k] - pivot[k] for k in range(3)]
    return [round(v + pivot[k], 4) for k, v in enumerate(apply(m, rel))]


# ========================================================================================================= preview

LIGHTS = [[v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (0.2, 1.0, -0.7)],
          [v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (-0.2, 1.0, 0.7)]]


def lightness(n):
    return min(1.0, 0.4 + 0.6 * sum(max(0.0, sum(n[k] * l[k] for k in range(3))) for l in LIGHTS))


def render(elements, atlases, project, turn, w, h, img=None, depth=None):
    """Rasterises elements; atlases maps a texture key ("#gun") to (pixels, size)."""
    img = img or [None] * (w * h)
    depth = depth or [-1e9] * (w * h)
    for e in elements:
        for face, f in e["faces"].items():
            pts, n = face_polygon(e, face)
            shade = 1.0 if e.get("light_emission") else lightness(turn(n))
            atlas, size = atlases[f["texture"]]
            sp = [project(p) for p in pts]
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (sp[i] for i in tri)
                ta, tb, tc = (uvs[i] for i in tri)
                area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
                if area <= 1e-9:
                    continue
                xs0, xs1 = max(0, int(min(a[0], b[0], c[0]))), min(w - 1, int(max(a[0], b[0], c[0])) + 1)
                ys0, ys1 = max(0, int(min(a[1], b[1], c[1]))), min(h - 1, int(max(a[1], b[1], c[1])) + 1)
                for py in range(ys0, ys1 + 1):
                    for px in range(xs0, xs1 + 1):
                        qx, qy = px + 0.5, py + 0.5
                        l1 = ((b[0] - qx) * (c[1] - qy) - (b[1] - qy) * (c[0] - qx)) / area
                        l2 = ((c[0] - qx) * (a[1] - qy) - (c[1] - qy) * (a[0] - qx)) / area
                        l3 = 1 - l1 - l2
                        if l1 < -1e-6 or l2 < -1e-6 or l3 < -1e-6:
                            continue
                        z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                        i = py * w + px
                        if z <= depth[i]:
                            continue
                        tu = l1 * ta[0] + l2 * tb[0] + l3 * tc[0]
                        tv = l1 * ta[1] + l2 * tb[1] + l3 * tc[1]
                        tx = min(size - 1, max(0, int(tu / 16 * size)))
                        ty = min(size - 1, max(0, int(tv / 16 * size)))
                        col = atlas[ty * size + tx]
                        if col[3] < 128:
                            continue
                        depth[i] = z
                        img[i] = tuple(int(col[k] * shade) for k in range(3))
    return img


def ortho(view, scale, w, h, centre):
    def project(p):
        v = apply(view, [p[k] - centre[k] for k in range(3)])
        return (w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2])
    return project


def first_person(displays, w, h, fov=70.0):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    f = (h / 2) / math.tan(math.radians(fov / 2))

    def project(p):
        q = apply(m, [s * (p[k] / 16 - 0.5) for k in range(3)])
        x, y, z = (q[k] + t["translation"][k] / 16 + (0.56, -0.52, -0.72)[k] for k in range(3))
        return (w / 2 + x / -z * f, h / 2 - y / -z * f, z)
    return project, lambda n: apply(m, n)


def gui_icon(displays, px):
    t = displays["gui"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    k = px / 16.0

    def project(p):
        v = apply(m, [s * (p[a] - 8) for a in range(3)])
        return (px / 2 + (v[0] + t["translation"][0]) * k, px / 2 - (v[1] + t["translation"][1]) * k, v[2])
    return project, lambda n: apply(m, n)


def blit(out, ow, img, w, h, ox, oy, k=1):
    for y in range(h * k):
        for x in range(w * k):
            c = img[(y // k) * w + x // k]
            if c is not None:
                out[(oy + y) * ow + ox + x] = c + (255,)


SIDE = [[0, 0, -1], [0, 1, 0], [1, 0, 0]]                 # from the right, muzzle to the right
TILT = [[1, 0, 0], [0, math.cos(-0.35), -math.sin(-0.35)], [0, math.sin(-0.35), math.cos(-0.35)]]
RIGHT_FRONT = mul(mul(TILT, SIDE), ry(-35))
LEFT_REAR = mul(mul(TILT, SIDE), ry(-145))


def compose(rows, path, gap=10, bg=(58, 60, 66, 255)):
    W = max(sum(w * k + gap for _, w, _, k in row) for row in rows) + gap
    heights = [max(h * k for _, _, h, k in row) + gap for row in rows]
    H = sum(heights) + gap
    out = [bg] * (W * H)
    y = gap
    for row, rh in zip(rows, heights):
        x = gap
        for img, w, h, k in row:
            blit(out, W, img, w, h, x, y + (rh - gap - h * k) // 2, k)
            x += w * k + gap
        y += rh
    png(path, W, H, out)
