#!/usr/bin/env python3
"""Fish: textures, item models, item definitions, species data, lang, recipes and tags for the fishing catch.

Run from anywhere: python3 tools/gen_fish.py [preview.png]

Every fish is a small 3D item model built silhouette-first from a handful of boxes (body, head, tail stock, eye) and
zero-thickness fin planes whose outline is cut out of the texture (tails, dorsal and anal fins, spikes, teeth). Model
space: x runs tail (low) to head (high), y up, z across the fish centred on 0; parts are moved so the fish's bounding
box is centred on (8, 8, 8).

Textures are painted in model space: each face gets its own patch of the fish's atlas (2 texels a pixel) and every
texel is coloured from the model point it lands on, so the bands, spots and stripes run on unbroken across boxes and
both flanks match. Bodies are banded belly to back with a short, posterised ramp per fish (no per-texel noise); fins
are cut-out planes with fanned rays and a rim. A cooked fish reuses its raw model with a cooked texture (the same
painting pushed through cooked cod's ramp, eyes gone white).

Displays: the inventory shows a 3/4 view with the head to the upper right (like vanilla's fish), held fish hang by
the tail as vanilla's sprites do, and dropped fish are small.

Also written: data/mymod/fishing_species/<fish>.json (where each fish is caught, read by the fishing system), the
cooking recipes (furnace, smoker, campfire), the crafting uses, lang entries, and the vanilla #minecraft:fishes tag.

Every model is checked for coplanar overlapping faces (tools/coplanar.py) and the script stops on any hit."""
import json, math, os, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png, write, display, display_matrix, apply, set_rotation, rx, ry, rz, element_rotation  # noqa: E402
from coplanar import check_elements  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data", "mymod")
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")
TEXTURES = os.path.join(ASSETS, "textures", "item", "fish")
SPECIES = os.path.join(DATA, "fishing_species")
LANG = os.path.join(ASSETS, "lang", "en_us.json")

D = 2              # texels per model pixel
FACES = ["north", "east", "south", "west", "up", "down"]
HOLD = 45.0        # the fish's tilt in hand and in the slot: head to the upper right, like vanilla's sprites


# ================================================================================================== colour helpers


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3]) + (c[3],)


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def steps(stops, t, n=None):
    """A posterised ramp: stops is a list of colours, t in 0..1 picks one (n: number of steps, default len)."""
    t = max(0.0, min(0.999, t))
    return stops[int(t * len(stops))]


def inside_poly(q, poly):
    x, y = q
    hit = False
    n = len(poly)
    for i in range(n):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % n]
        if (y0 > y) != (y1 > y):
            if x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
                hit = not hit
    return hit


# ========================================================================================================= parts


class Part:
    def __init__(self, name, mat, lo, hi, faces, rot=None, emit=False, poly=None, axis=None, **kw):
        self.name, self.mat, self.lo, self.hi, self.faces = name, mat, list(lo), list(hi), faces
        self.rot, self.emit, self.poly, self.axis, self.kw = rot, emit, poly, axis, kw

    def inside(self, p):
        if not self.poly:
            return True
        q = (p[0], p[1]) if self.axis == "z" else (p[2], p[1])
        return inside_poly(q, self.poly)

    def centre(self):
        return [(self.lo[a] + self.hi[a]) / 2 for a in range(3)]


def box(name, mat, x0, y0, z0, x1, y1, z1, drop=(), rot=None, emit=False, **kw):
    return Part(name, mat, (x0, y0, z0), (x1, y1, z1), [f for f in FACES if f not in drop], rot, emit, **kw)


def zbox(name, mat, x0, y0, x1, y1, half, **kw):
    """A box centred across the fish: z from -half to +half."""
    return box(name, mat, x0, y0, -half, x1, y1, half, **kw)


def fin(name, poly, z=0.0, mat="fin", emit=False, root=None, **kw):
    """A zero-thickness plane at depth z whose outline is the polygon (x, y) points; rays fan from root."""
    xs, ys = [p[0] for p in poly], [p[1] for p in poly]
    if root is None:
        root = ((min(xs) + max(xs)) / 2, min(ys) - 2)
    return Part(name, mat, (min(xs), min(ys), z), (max(xs), max(ys), z), ["north", "south"], None, emit, poly=poly,
                axis="z", root=root, **kw)


def side_fin(name, poly, x, mat="fin", emit=False, root=None, **kw):
    """A zero-thickness plane across the fish at x; polygon in (z, y)."""
    zs, ys = [p[0] for p in poly], [p[1] for p in poly]
    if root is None:
        root = (0.0, 0.0)
    return Part(name, mat, (x, min(ys), min(zs)), (x, max(ys), max(zs)), ["east", "west"], None, emit, poly=poly,
                axis="x", root=root, **kw)


def tail_root(poly):
    xs, ys = [p[0] for p in poly], [p[1] for p in poly]
    return (max(xs) + 1.0, (min(ys) + max(ys)) / 2)


def face_dims(part, face):
    dx, dy, dz = (part.hi[a] - part.lo[a] for a in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def face_point(face, lo, hi, fu, fv):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    if face == "north":
        return (x1 - fu * (x1 - x0), y1 - fv * (y1 - y0), z0)
    if face == "south":
        return (x0 + fu * (x1 - x0), y1 - fv * (y1 - y0), z1)
    if face == "east":
        return (x1, y1 - fv * (y1 - y0), z1 - fu * (z1 - z0))
    if face == "west":
        return (x0, y1 - fv * (y1 - y0), z0 + fu * (z1 - z0))
    if face == "up":
        return (x0 + fu * (x1 - x0), y1, z0 + fv * (z1 - z0))
    return (x0 + fu * (x1 - x0), y0, z1 - fv * (z1 - z0))


# ========================================================================================================== fish


class Fish:
    def __init__(self, name, title, bands, yb, yt, fins, eye=None, cooked=None, pattern=None, gill=None, mats=None,
                 edge=0.8, hold=HOLD, gui_cap=1.0, title_cooked=None):
        self.name, self.title = name, title
        self.bands, self.yb, self.yt = bands, yb, yt
        self.fins = fins                         # dict: base, light, ray, edge
        self.eye = eye or dict(pupil=C("1a1a1e"), shine=C("e8eef0"))
        self.cooked = cooked                     # cooked item name or None
        self.title_cooked = title_cooked
        self.pattern, self.gill = pattern, gill
        self.mats = mats or {}
        self.edge = edge
        self.hold, self.gui_cap = hold, gui_cap
        self.parts = []

    def t(self, y):
        return max(0.0, min(1.0, (y - self.yb) / (self.yt - self.yb)))

    def tx(self, x, y):
        """Belly (0) to back (1) at this point; fish whose body steps up and down override it."""
        return self.t(y)

    def band(self, t):
        for top, c in self.bands:
            if t <= top:
                return c
        return self.bands[-1][1]


# ------------------------------------------------------------------------------------------------------ painters


def paint_skin(f, part, face, p, i, j, w, h):
    x, y, z = p
    t = f.tx(x, y)
    c = f.band(t)
    if f.pattern:
        pc = f.pattern(x, y, t, face, part)
        if pc is not None:
            c = pc
    if f.gill and face in ("north", "south"):
        gx, g0, g1, gc = f.gill
        if gx <= x < gx + 0.5 and g0 <= t <= g1:
            c = gc
    # A darker bottom row on each flank: the cube reads as a solid with an underside, like vanilla's outlines.
    if face in ("north", "south", "east", "west") and j == h - 1 and h > 2:
        c = shade(c, f.edge)
    if face == "down":
        c = shade(c, 0.86)
    return c


def paint_fin(f, part, face, p, i, j, w, h):
    if not part.inside(p):
        return None
    cols = part.kw.get("colors") or f.fins
    step = 1.0 / D
    if part.axis == "z":
        nb = [(p[0] + dx, p[1] + dy, p[2]) for dx, dy in ((step, 0), (-step, 0), (0, step), (0, -step))]
        q = (p[0], p[1])
    else:
        nb = [(p[0], p[1] + dy, p[2] + dz) for dz, dy in ((step, 0), (-step, 0), (0, step), (0, -step))]
        q = (p[2], p[1])
    if any(not part.inside(n) for n in nb):
        return cols["edge"]
    rx_, ry_ = part.kw["root"]
    ang = math.atan2(q[1] - ry_, q[0] - rx_)
    dist = math.hypot(q[1] - ry_, q[0] - rx_)
    reach = part.kw.get("reach")
    if reach is None:
        xs = [v[0] for v in part.poly]
        ys = [v[1] for v in part.poly]
        reach = max(math.hypot(px - rx_, py - ry_) for px, py in zip(xs, ys))
        part.kw["reach"] = reach
    ray_w = part.kw.get("ray", 0.2)
    if ray_w and int(math.floor(ang / ray_w)) % 2:
        return cols["ray"]
    if dist > reach * part.kw.get("tip", 0.72):
        return cols["light"]
    return cols["base"]


def paint_eye(f, part, face, p, i, j, w, h):
    e = part.kw.get("colors") or f.eye
    if face not in ("north", "south"):
        return e.get("rim", shade(e["pupil"], 1.4))
    cx, cy, _ = part.centre()
    s = min(part.hi[0] - part.lo[0], part.hi[1] - part.lo[1])
    dx, dy = p[0] - cx, p[1] - cy
    if "iris" in e and max(abs(dx), abs(dy)) > s / 2 - 0.5 / D - 1e-6 and s > 1.01:
        return e["iris"]
    if dx > 0 and dy > 0 and max(abs(dx), abs(dy)) <= 0.5:
        return e["shine"]
    return e["pupil"]


def paint_solid(f, part, face, p, i, j, w, h):
    c = part.kw["color"]
    g = part.kw.get("grad")
    if g:
        axis, c1, n = g
        a = {"x": 0, "y": 1, "z": 2}[axis]
        span = part.hi[a] - part.lo[a]
        t = (p[a] - part.lo[a]) / span if span else 0
        k = min(n - 1, int(t * n))
        c = mixc(c, c1, k / max(1, n - 1))
    if part.poly and not part.inside(p):
        return None
    if face == "down":
        c = shade(c, 0.85)
    elif face in ("north", "south", "east", "west") and j == h - 1 and h > 2 and part.kw.get("rimmed", True):
        c = shade(c, 0.85)
    return c


def paint_teeth(f, part, face, p, i, j, w, h):
    if not part.inside(p):
        return None
    return part.kw.get("color", C("ece6cf"))


PAINTERS = dict(skin=paint_skin, fin=paint_fin, eye=paint_eye, solid=paint_solid, teeth=paint_teeth)

# Cooked: cooked cod's ramp, keyed by brightness.
COOKED = [C("5c3822"), C("7a4f33"), C("986d4e"), C("ae8b67"), C("c6a274"), C("d6c5ad"), C("e2e5c6")]
COOKED_FIN = [C("4a2c1b"), C("6b4329"), C("8a5a37"), C("a5754a")]


def cook(c, fin=False):
    if c is None:
        return None
    l = lum(c) / 255.0
    if fin:
        return steps(COOKED_FIN, l * 1.05)
    base = steps(COOKED, 0.08 + l * 0.95)
    # A hint of the fish's own colour keeps cooked fish telling apart.
    return mixc(base, shade(c, 0.85), 0.3)


def painter_for(f, part, cooked):
    fn = f.mats.get(part.mat) or PAINTERS[part.mat]
    if not cooked:
        return fn
    if part.mat == "eye":
        return lambda *a: C("efe9dc") if a[2] in ("north", "south") else C("b9ad96")
    is_fin = part.mat in ("fin",)
    return lambda *a: cook(fn(*a), fin=is_fin)


# ========================================================================================================= atlas


def faces_of(f):
    out = []
    for part in f.parts:
        for face in part.faces:
            du, dv = face_dims(part, face)
            w, h = max(1, int(math.ceil(du * D - 1e-6))), max(1, int(math.ceil(dv * D - 1e-6)))
            out.append((part, face, w, h))
    return out


def pack(rects):
    """Shelf-packs (w, h) rectangles; returns (size, positions)."""
    order = sorted(range(len(rects)), key=lambda k: (-rects[k][1], -rects[k][0]))
    for size in (32, 64, 128, 256):
        pos, x, y, shelf, ok = {}, 0, 0, 0, True
        for k in order:
            w, h = rects[k]
            if x + w > size:
                x, y, shelf = 0, y + shelf, 0
            if y + h > size or w > size:
                ok = False
                break
            pos[k] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            return size, pos
    raise SystemExit("atlas too big")


def separate_fins(f):
    """Fin planes sharing a depth whose rectangles overlap are stepped apart by a hundredth of a pixel, so their
    (mostly transparent) quads aren't coplanar; and no two of them may have opaque texels on top of each other."""
    planes = [p for p in f.parts if p.poly]
    for k, a in enumerate(planes):
        for b in planes[:k]:
            if a.axis != b.axis or abs(a.lo[2 if a.axis == "z" else 0] - b.lo[2 if b.axis == "z" else 0]) > 0.005:
                continue
            ia = (0, 1) if a.axis == "z" else (2, 1)
            if all(min(a.hi[i], b.hi[i]) > max(a.lo[i], b.lo[i]) for i in ia):
                n = 0.25 / D
                lo0, hi0 = [max(a.lo[i], b.lo[i]) for i in ia], [min(a.hi[i], b.hi[i]) for i in ia]
                y = lo0[1] + n / 2
                while y < hi0[1]:
                    x = lo0[0] + n / 2
                    while x < hi0[0]:
                        q = (x, y)
                        if inside_poly(q, a.poly) and inside_poly(q, b.poly):
                            raise SystemExit(f"{f.name}: fins {a.name} and {b.name} overlap at {q}")
                        x += n
                    y += n
                axis = 2 if a.axis == "z" else 0
                a.lo[axis] += 0.01 * (k + 1)
                a.hi[axis] += 0.01 * (k + 1)


def build(f):
    """Model elements (centred) and the raw and cooked atlases."""
    separate_fins(f)
    lo =[min(p.lo[a] for p in f.parts) for a in range(3)]
    hi = [max(p.hi[a] for p in f.parts) for a in range(3)]
    shift = [round((8 - (lo[a] + hi[a]) / 2) * 4) / 4 for a in range(3)]
    shift[2] = 8.0
    fl = faces_of(f)
    size, pos = pack([(w, h) for _, _, w, h in fl])
    atlases = {}
    for cooked in ((False, True) if f.cooked else (False,)):
        px = [(0, 0, 0, 0)] * (size * size)
        for k, (part, face, w, h) in enumerate(fl):
            ax, ay = pos[k]
            fn = painter_for(f, part, cooked)
            for j in range(h):
                for i in range(w):
                    p = face_point(face, part.lo, part.hi, (i + 0.5) / w, (j + 0.5) / h)
                    c = fn(f, part, face, p, i, j, w, h)
                    if c is not None:
                        px[(ay + j) * size + ax + i] = c
        atlases[cooked] = px
    elements = []
    for k, (part, face, w, h) in enumerate(fl):
        if face == part.faces[0]:
            e = OrderedDict()
            e["name"] = part.name
            e["from"] = [round(part.lo[a] + shift[a], 4) for a in range(3)]
            e["to"] = [round(part.hi[a] + shift[a], 4) for a in range(3)]
            if part.emit:
                e["shade"] = False
                e["light_emission"] = 15
            e["faces"] = OrderedDict()
            if part.rot:
                axis, angle, origin = part.rot
                m = {"x": rx, "y": ry, "z": rz}[axis](angle)
                set_rotation(e, m, [origin[a] + shift[a] for a in range(3)])
            elements.append(e)
        ax, ay = pos[k]
        uv = [ax * 16 / size, ay * 16 / size, (ax + w) * 16 / size, (ay + h) * 16 / size]
        elements[-1]["faces"][face] = OrderedDict([("uv", [round(v, 4) for v in uv]), ("texture", "#0")])
    return elements, size, atlases


# ====================================================================================================== displays


def corners(e):
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    pts = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    r = element_rotation(e)
    if r:
        m, o = r
        pts = [tuple(o[a] + v for a, v in enumerate(apply(m, [q[k] - o[k] for k in range(3)]))) for q in pts]
    return pts


def fit(elements, rotation, fill, cap):
    m = display_matrix(rotation)
    pts = [apply(m, [q[a] - 8 for a in range(3)]) for e in elements for q in corners(e)]
    xs, ys = [v[0] for v in pts], [v[1] for v in pts]
    s = min(cap, fill / max(max(xs) - min(xs), max(ys) - min(ys)))
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    return round(s, 3), [round(-cx * s, 3), round(-cy * s, 3), 0.0]


GUI_ROT = [18.0, -28.0, HOLD]


def displays(f, elements):
    length = max(max(q[0] for q in corners(e)) for e in elements) - min(min(q[0] for q in corners(e)) for e in elements)
    k = max(0.62, min(1.15, 15.0 / length))
    gui_rot = [GUI_ROT[0], GUI_ROT[1], f.hold]
    gs, gt = fit(elements, gui_rot, 15.5, f.gui_cap)
    fs, ft = fit(elements, [0, 180, f.hold], 15.0, 1.0)
    out = OrderedDict()
    tp = display([0, 0, f.hold], [0.0, 3.0, 1.0], round(0.55 * k, 3))
    fp = display([0, -90, 25 + f.hold], [1.13, 3.2, 1.13], round(0.68 * k, 3))
    # Both hands take the same values: the game mirrors the left-hand transform itself.
    out["thirdperson_righthand"] = out["thirdperson_lefthand"] = tp
    out["firstperson_righthand"] = out["firstperson_lefthand"] = fp
    out["ground"] = display([0, 0, 0], [0.0, 2.0, 0.0], round(0.42 * k, 3))
    out["gui"] = display(gui_rot, gt, gs)
    out["fixed"] = display([0, 180, f.hold], ft, fs)
    return out


# ======================================================================================================= palette

FIN_CLEAR = dict(base=C("c9b9a0"), light=C("e0d4bd"), ray=C("ab9a80"), edge=C("8b7a62"))


def spots(points, r, colour, halo=None, hr=None):
    """A pattern helper: discs at fixed points (x, y)."""
    def fn(x, y):
        for sx, sy in points:
            d = math.hypot(x - sx, y - sy)
            if d <= r:
                return colour
            if halo and d <= hr:
                return halo
        return None
    return fn


# ------------------------------------------------------------------------------------------------------- species


def brook_trout():
    red = spots([(4.6, -0.6), (7.4, 0.1), (9.6, -0.9), (6.0, -1.4), (11.4, 0.3), (3.6, 0.4)], 0.32, C("c8473a"),
                C("6a8fc2"), 0.62)
    def pattern(x, y, t, face, part):
        if t > 0.62 and face in ("north", "south", "up"):
            # Vermiculation: pale wavy worm-tracks over the dark back.
            if (int(math.floor(x * 2 + 2 * math.sin(y * 2.2))) % 4) == 0:
                return C("b9b46a")
        if face in ("north", "south"):
            return red(x, y)
        return None
    f = Fish("brook_trout", "Brook Trout",
             [(0.2, C("ead6b0")), (0.38, C("d8853f")), (0.62, C("6e7a3e")), (1.01, C("3c4d2b"))], -2.5, 2.5,
             dict(base=C("c8763c"), light=C("e39a53"), ray=C("a85f2e"), edge=C("f2ead8")),
             cooked="cooked_brook_trout", title_cooked="Cooked Brook Trout", pattern=pattern,
             gill=(11.0, 0.15, 0.85, C("2f3d22")))
    dorsal = dict(base=C("5a6634"), light=C("8e9150"), ray=C("44502a"), edge=C("2f3a1f"))
    f.parts = [
        zbox("body", "skin", 3, -2.5, 11, 2.5, 1.75),
        zbox("head", "skin", 11, -2, 14.5, 2, 1.5),
        zbox("wrist", "skin", 1.5, -1.25, 3, 1.25, 1.0),
        fin("tail", [(2.0, -1.0), (-1.0, -3.0), (-0.5, 0.0), (-1.0, 3.0), (2.0, 1.0)], root=(3.0, 0.0)),
        fin("dorsal", [(6.0, 2.0), (8.0, 4.75), (9.75, 4.5), (9.5, 2.0)], colors=dorsal),
        fin("adipose", [(3.0, 2.0), (3.5, 3.0), (4.5, 2.0)], colors=dorsal, ray=0),
        fin("anal", [(4.0, -2.0), (5.0, -4.0), (7.25, -3.75), (7.5, -2.0)], root=(6, 0)),
        fin("pelvic", [(8.5, -2.0), (9.5, -3.75), (10.75, -3.5), (10.5, -2.0)], root=(10, 0)),
        zbox("eye", "eye", 12.5, 0.25, 13.5, 1.25, 1.75),
    ]
    return f


def mud_catfish():
    def pattern(x, y, t, face, part):
        if face in ("north", "south") and 0.4 < t < 0.85:
            # Soft dark mottling in a regular staggered pattern.
            if (int(x * 1.0) + int((y + 3) * 1.2)) % 3 == 0 and int(x * 2) % 2 == 0:
                return C("3b3328")
        return None
    whisk = dict(color=C("2a2420"), grad=("x", C("6b5a48"), 3), rimmed=False)
    f = Fish("mud_catfish", "Mud Catfish",
             [(0.22, C("d8c79a")), (0.4, C("9a8a66")), (0.7, C("5e5443")), (1.01, C("463f33"))], -2.5, 2.0,
             dict(base=C("5a5040"), light=C("776a55"), ray=C("463e31"), edge=C("2f2a22")),
             cooked="cooked_mud_catfish", title_cooked="Cooked Mud Catfish", pattern=pattern,
             gill=(10.0, 0.25, 0.9, C("383228")), eye=dict(pupil=C("1b1916"), shine=C("c9c2a8")))
    f.parts = [
        zbox("body", "skin", 3, -2.5, 10, 2.0, 2.0),
        zbox("head", "skin", 10, -2.5, 14.5, 1.25, 2.5),
        zbox("wrist", "skin", 0.5, -1.75, 3, 1.25, 1.25),
        fin("tail", [(1.5, -1.5), (-1.25, -3.0), (-1.75, -0.25), (-1.25, 2.5), (1.5, 1.0)], root=(2.5, -0.25)),
        fin("dorsal", [(7.0, 1.75), (7.75, 4.25), (9.0, 4.0), (9.75, 1.75)]),
        fin("anal", [(1.0, -1.75), (1.5, -3.75), (7.0, -3.5), (7.5, -2.25)], root=(4, 0)),
        zbox("eye", "eye", 12.0, -0.25, 13.0, 0.75, 2.75),
        # Barbels: a long pair from the upper jaw sweeping back and down, and a short pair under the chin.
        box("whisker_l", "solid", 14.0, 0.25, -2.25, 18.5, 0.75, -1.75, rot=("z", -30, (14.0, 0.5, -2.0)), **whisk),
        box("whisker_r", "solid", 14.0, 0.25, 1.75, 18.5, 0.75, 2.25, rot=("z", -30, (14.0, 0.5, 2.0)), **whisk),
        box("chin_l", "solid", 13.0, -4.5, -1.25, 13.5, -2.5, -0.75, **whisk),
        box("chin_r", "solid", 13.0, -4.5, 0.75, 13.5, -2.5, 1.25, **whisk),
    ]
    return f


def pike():
    def pattern(x, y, t, face, part):
        if face in ("north", "south") and 0.3 < t < 0.85 and x < 12.5:
            # Rows of pale bean-shaped spots.
            row = int(math.floor((y + 2) / 1.0))
            ox = 0.75 if row % 2 else 0.0
            u = (x + ox) % 1.5
            v = (y + 2) % 1.0
            if 0.25 <= u < 1.0 and 0.25 <= v < 0.75:
                return C("cfd38c")
        return None
    f = Fish("pike", "Pike",
             [(0.25, C("e9e6c8")), (0.45, C("8f9a52")), (0.75, C("56683a")), (1.01, C("3a4a2a"))], -1.75, 1.75,
             dict(base=C("8c6a3a"), light=C("b88c4c"), ray=C("6c5130"), edge=C("4a3720")),
             cooked="cooked_pike", title_cooked="Cooked Pike", pattern=pattern, gill=(12.0, 0.15, 0.85, C("2e3a20")),
             eye=dict(pupil=C("161616"), shine=C("f0e6a0"), rim=C("c9a840")))
    f.parts = [
        zbox("body", "skin", 2, -1.75, 12, 1.75, 1.5),
        zbox("head", "skin", 12, -1.5, 15, 1.25, 1.25),
        zbox("jaw_top", "skin", 15, -0.25, 18, 0.75, 1.0),
        zbox("jaw_bottom", "skin", 15, -1.25, 17.5, -0.5, 0.9),
        fin("teeth", [(15.25, -0.5), (15.5, -0.25), (15.75, -0.5), (16.0, -0.25), (16.25, -0.5), (16.5, -0.25),
                      (16.75, -0.5), (17.0, -0.25), (17.25, -0.5)], mat="teeth"),
        zbox("wrist", "skin", 0.5, -1.0, 2, 1.0, 1.0),
        fin("tail", [(1.5, -0.75), (-2.0, -3.0), (-1.0, 0.0), (-2.0, 3.0), (1.5, 0.75)], root=(2.5, 0.0)),
        fin("dorsal", [(2.5, 1.5), (3.75, 3.75), (5.5, 3.25), (5.75, 1.5)], root=(4, -1)),
        fin("anal", [(2.5, -1.5), (3.75, -3.75), (5.5, -3.25), (5.75, -1.5)], root=(4, 1)),
        zbox("eye", "eye", 13.5, 0.0, 14.5, 1.0, 1.5),
    ]
    return f


def mackerel():
    def pattern(x, y, t, face, part):
        if t > 0.55 and face in ("north", "south", "up") and part.name != "snout":
            # Tiger bars: dark wavy stripes across the blue-green back.
            if (x + 0.6 * math.sin(y * 2.6)) % 1.5 < 0.5:
                return C("1e3340")
        return None
    f = Fish("mackerel", "Mackerel",
             [(0.3, C("eef0ee")), (0.5, C("b9c7c8")), (0.62, C("4d9a8f")), (1.01, C("2f6f78"))], -2.0, 2.0,
             dict(base=C("5c7f86"), light=C("8eb0b0"), ray=C("44646c"), edge=C("2a4048")),
             cooked="cooked_mackerel", title_cooked="Cooked Mackerel", pattern=pattern,
             gill=(11.0, 0.2, 0.8, C("8e9ea4")))
    f.parts = [
        zbox("body", "skin", 3, -2.0, 11, 2.0, 1.5),
        zbox("head", "skin", 11, -1.5, 14, 1.5, 1.25),
        zbox("snout", "skin", 14, -0.75, 15, 0.75, 1.0),
        zbox("wrist", "skin", 0.5, -0.75, 3, 0.75, 0.75),
        fin("tail", [(1.25, -0.5), (-2.25, -3.5), (-0.75, 0.0), (-2.25, 3.5), (1.25, 0.5)], root=(2.0, 0.0)),
        fin("dorsal", [(7.75, 1.75), (8.75, 3.75), (10.5, 3.5), (10.75, 1.75)]),
        fin("finlets_top", [(3.25, 1.5), (3.75, 2.5), (4.25, 1.75), (4.75, 2.5), (5.25, 1.75), (5.75, 2.5),
                            (6.25, 1.5)], ray=0),
        fin("finlets_bottom", [(3.25, -1.5), (3.75, -2.5), (4.25, -1.75), (4.75, -2.5), (5.25, -1.75),
                               (5.75, -2.5), (6.25, -1.5)], ray=0, root=(5, 0)),
        zbox("eye", "eye", 12.25, 0.0, 13.25, 1.0, 1.5),
    ]
    return f


def swordfish():
    def pattern(x, y, t, face, part):
        return None
    bill = dict(color=C("3c3f52"), grad=("x", C("8c90a8"), 4))
    f = Fish("swordfish", "Swordfish",
             [(0.3, C("d6dbe6")), (0.48, C("9aa3bb")), (0.7, C("4b4f73")), (1.01, C("2c2d4a"))], -2.25, 2.25,
             dict(base=C("3a3c5e"), light=C("5c5f8a"), ray=C("2c2d4a"), edge=C("1c1d33")),
             cooked="swordfish_steak", title_cooked="Swordfish Steak", pattern=pattern,
             gill=(12.0, 0.2, 0.85, C("26273f")), eye=dict(pupil=C("101018"), shine=C("dfe8ff"), rim=C("4a5a8a")),
             gui_cap=1.0)
    f.parts = [
        zbox("body", "skin", 3, -2.25, 12, 2.25, 1.75),
        zbox("head", "skin", 12, -1.75, 14.5, 1.75, 1.5),
        box("bill_base", "solid", 14.5, -0.25, -0.6, 18.5, 0.75, 0.6, **bill),
        box("bill_tip", "solid", 18.5, 0.0, -0.35, 23.0, 0.5, 0.35, color=C("6e728c"), grad=("x", C("b4b8cc"), 3)),
        zbox("wrist", "skin", 0.5, -0.75, 3, 0.75, 0.9),
        box("keel", "solid", 0.75, -0.25, -1.15, 2.75, 0.25, 1.15, color=C("2a2b45"), rimmed=False),
        fin("tail", [(1.25, -0.5), (-0.5, -2.5), (-2.75, -4.75), (-1.25, -1.5), (-0.75, 0.0), (-1.25, 1.5),
                     (-2.75, 4.75), (-0.5, 2.5), (1.25, 0.5)], root=(2.0, 0.0)),
        fin("dorsal", [(8.0, 2.0), (10.25, 6.25), (11.0, 6.0), (10.75, 4.0), (12.0, 2.0)], root=(10, -1)),
        fin("pectoral", [(10.5, -1.75), (8.5, -4.75), (9.25, -4.75), (12.0, -1.75)], z=-0.5, root=(11, 0)),
        zbox("eye", "eye", 13.0, 0.25, 14.0, 1.25, 1.75),
    ]
    return f


def anglerfish():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("body", "head"):
            # Warty skin: a regular scatter of pale bumps.
            u, v = (x * 1.0) % 2.0, (y + (1.0 if int(x / 2) % 2 else 0.0)) % 2.0
            if 0.5 <= u < 1.0 and 0.5 <= v < 1.0:
                return C("7a6a4a")
        return None
    lure = dict(color=C("f6ff9a"), rimmed=False)
    f = Fish("anglerfish", "Anglerfish",
             [(0.3, C("6a5a42")), (0.55, C("4e4232")), (1.01, C("3a3126"))], -3.0, 3.0,
             dict(base=C("4a3e2e"), light=C("6a5a42"), ray=C("3a3126"), edge=C("221c15")),
             pattern=pattern, eye=dict(pupil=C("101010"), shine=C("e0f0a0"), rim=C("2a2a1a")))
    f.parts = [
        zbox("body", "skin", 2, -3.0, 9, 3.0, 2.5),
        zbox("head", "skin", 9, -0.5, 13, 3.0, 2.25),
        zbox("jaw", "skin", 9, -3.5, 13.5, -1.25, 2.5),
        box("mouth", "solid", 9.0, -1.25, -2.0, 12.5, -0.5, 2.0, color=C("4a1f22"), rimmed=False),
        fin("teeth_top", [(9.75, -0.5), (10.25, -1.0), (10.75, -0.5), (11.25, -1.25), (11.75, -0.5), (12.25, -1.0),
                          (12.75, -0.5)], z=-2.1, mat="teeth"),
        fin("teeth_bottom", [(10.0, -1.25), (10.5, -0.75), (11.0, -1.25), (11.5, -0.5), (12.0, -1.25), (12.5, -0.75),
                             (13.0, -1.25)], z=2.1, mat="teeth"),
        zbox("wrist", "skin", 0.25, -1.25, 2, 1.25, 1.0),
        fin("tail", [(1.0, -1.0), (-1.5, -2.75), (-2.25, 0.0), (-1.5, 2.75), (1.0, 1.0)], root=(2.0, 0.0)),
        box("stalk", "solid", 10.75, 3.0, -0.25, 11.25, 6.5, 0.25, rot=("z", -30, (11.0, 3.0, 0.0)),
            color=C("5a4a36"), rimmed=False),
        box("lure", "solid", 12.0, 5.75, -0.75, 13.5, 7.25, 0.75, emit=True, **lure),
        zbox("eye", "eye", 11.0, 1.5, 12.0, 2.5, 2.5),
    ]
    return f


def glowfin_tetra():
    def pattern(x, y, t, face, part):
        if x < 5.0 and t < 0.5:
            return C("d63a3a") if t > 0.12 else C("a82c2c")
        return None
    neon = dict(color=C("4ff7ff"), grad=("x", C("2fb6ff"), 2), rimmed=False)
    f = Fish("glowfin_tetra", "Glowfin Tetra",
             [(0.12, C("cfd6d8")), (0.5, C("e3e8e6")), (0.72, C("8a9a8e")), (1.01, C("5c6c66"))], -2.0, 2.0,
             dict(base=C("7fe9ff"), light=C("c8fbff"), ray=C("49c9e8"), edge=C("e6ffff")),
             pattern=pattern, eye=dict(pupil=C("141414"), shine=C("e8ffff"), rim=C("3fb8d0")), gui_cap=1.05)
    f.parts = [
        zbox("body", "skin", 2, -2.0, 7.5, 2.0, 1.0),
        zbox("head", "skin", 7.5, -1.5, 9.5, 1.5, 0.85),
        zbox("wrist", "skin", 1.0, -0.75, 2, 0.75, 0.5),
        fin("tail", [(1.5, -0.5), (-1.5, -2.25), (-0.5, 0.0), (-1.5, 2.25), (1.5, 0.5)], root=(2.25, 0.0), emit=True),
        fin("dorsal", [(4.0, 1.75), (4.75, 3.75), (5.75, 3.5), (6.0, 1.75)], emit=True),
        fin("anal", [(2.5, -1.75), (3.0, -3.0), (5.5, -2.75), (5.5, -1.75)], root=(4, 0), emit=True),
        box("stripe", "solid", 3.0, 0.25, -1.1, 7.75, 0.85, 1.1, emit=True, **neon),
        zbox("eye", "eye", 8.0, 0.1, 9.0, 1.1, 1.1),
    ]
    return f


def parrotfish():
    def pattern(x, y, t, face, part):
        if part.name == "head" and face in ("north", "south"):
            # Face paint: two pink bands sweeping back from the beak.
            for c0 in (0.25, -1.0):
                if abs(y - 0.45 * (x - 12.0) - c0) < 0.3:
                    return C("f08aae")
            return None
        if face in ("north", "south", "up") and part.name == "body" and t > 0.22:
            # Big scales: a curved darker rim along the back of each, rows staggered.
            row = int(math.floor((y + 3) / 1.25))
            u = (x + (0.75 if row % 2 else 0.0)) % 1.5
            v = (y + 3) % 1.25
            if u < 0.5 and v >= 0.25:
                return C("2aa49a") if t < 0.62 else C("17807d")
        return None
    beak = dict(color=C("e9e2cc"), rimmed=False)
    f = Fish("parrotfish", "Parrotfish",
             [(0.22, C("f4b3c6")), (0.42, C("8fe0c8")), (0.62, C("37c2ae")), (1.01, C("1f9a95"))], -2.75, 2.75,
             dict(base=C("e86f9a"), light=C("f6a8c2"), ray=C("c9547f"), edge=C("37c2ae")),
             cooked="cooked_parrotfish", title_cooked="Cooked Parrotfish", pattern=pattern,
             eye=dict(pupil=C("1b1420"), shine=C("fff4c0"), iris=C("f2c14e")))
    f.parts = [
        zbox("body", "skin", 3, -2.75, 10, 2.75, 1.75),
        zbox("head", "skin", 10, -2.0, 12.5, 2.25, 1.5),
        zbox("beak", "solid", 12.5, -1.25, 13.5, 0.25, 1.0, **beak),
        zbox("wrist", "skin", 1.5, -1.25, 3, 1.25, 1.0),
        fin("tail", [(2.0, -1.0), (-0.25, -3.25), (0.75, -1.0), (0.5, 0.0), (0.75, 1.0), (-0.25, 3.25), (2.0, 1.0)],
            root=(2.5, 0.0)),
        fin("dorsal", [(3.5, 2.5), (4.0, 3.75), (10.0, 3.5), (10.5, 2.5)], ray=0.12),
        fin("anal", [(3.5, -2.5), (4.0, -3.75), (7.0, -3.5), (7.5, -2.5)], root=(5.5, 0)),
        zbox("eye", "eye", 10.75, 0.25, 12.0, 1.5, 1.75),
    ]
    return f


def ice_char():
    frost = spots([(4.0, 1.0), (6.0, 1.6), (8.0, 0.9), (10.0, 1.5), (5.0, -0.2), (7.0, 0.3), (9.0, -0.3),
                   (11.5, 0.6)], 0.35, C("eef8ff"))
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up"):
            return frost(x, y)
        return None
    crystal = dict(base=C("a6e4ff"), light=C("dff6ff"), ray=C("7cc8f0"), edge=C("f4fcff"))
    f = Fish("ice_char", "Ice Char",
             [(0.25, C("f2b8a0")), (0.42, C("e48a6e")), (0.7, C("6f8fae")), (1.01, C("4a6a8c"))], -2.25, 2.25,
             dict(base=C("d97a5e"), light=C("eba07f"), ray=C("b9604a"), edge=C("ffffff")),
             cooked="cooked_ice_char", title_cooked="Cooked Ice Char", pattern=pattern,
             gill=(11.0, 0.15, 0.85, C("3a5470")), eye=dict(pupil=C("101820"), shine=C("e8f6ff"), rim=C("8ab8d8")))
    f.parts = [
        zbox("body", "skin", 3, -2.25, 11, 2.25, 1.5),
        zbox("head", "skin", 11, -1.75, 14, 1.75, 1.25),
        zbox("wrist", "skin", 1.5, -1.0, 3, 1.0, 0.9),
        fin("tail", [(2.0, -0.75), (-1.5, -3.25), (0.0, 0.0), (-1.5, 3.25), (2.0, 0.75)], root=(2.5, 0.0),
            colors=dict(base=C("6f8fae"), light=C("9fbcd6"), ray=C("587898"), edge=C("dff2ff"))),
        # Ice crystals for a dorsal fin: jagged shards.
        fin("crystal", [(5.5, 2.0), (6.0, 4.0), (6.75, 3.0), (7.5, 5.5), (8.25, 3.25), (9.0, 4.5), (9.5, 2.0)],
            colors=crystal, ray=0, emit=False),
        fin("anal", [(4.5, -2.0), (5.25, -3.75), (7.0, -3.5), (7.25, -2.0)], root=(6, 0)),
        fin("pelvic", [(8.5, -2.0), (9.25, -3.5), (10.75, -3.25), (10.75, -2.0)], root=(10, 0)),
        zbox("eye", "eye", 12.25, 0.25, 13.25, 1.25, 1.5),
    ]
    return f


def bog_eel():
    segs = [(15.0, 19.0, -1.25, 1.0, 1.0, 0.25), (11.0, 15.0, -1.25, 1.25, 1.25, 0.75),
            (7.0, 11.0, -1.25, 1.25, 1.15, 0.0), (3.5, 7.0, -1.1, 1.1, 1.05, -0.75), (0.5, 3.5, -0.9, 0.9, 0.85, -0.25)]
    def top(x):
        for x0, x1, y0, y1, hz, off in segs:
            if x0 <= x <= x1:
                return y1 + off
        return 0.0

    def bottom(x):
        for x0, x1, y0, y1, hz, off in segs:
            if x0 <= x <= x1:
                return y0 + off
        return 0.0

    def offset(x):
        for x0, x1, y0, y1, hz, off in segs:
            if x0 <= x <= x1:
                return off
        return 0.0

    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and (x % 2.5) < 0.75 and y > (top(x) + bottom(x)) / 2 - 0.25:
            return C("2e3420")
        return None

    f = Fish("bog_eel", "Bog Eel",
             [(0.3, C("c9b56a")), (0.5, C("6f7a3a")), (1.01, C("4a5428"))], -1.25, 1.25,
             dict(base=C("8a9448"), light=C("a8b05a"), ray=C("6f7a3a"), edge=C("2a2e16")),
             cooked="cooked_bog_eel", title_cooked="Cooked Bog Eel", pattern=pattern,
             eye=dict(pupil=C("1a1a10"), shine=C("e8e0a0"), rim=C("a89a40")))
    f.tx = lambda x, y: max(0.0, min(1.0, (y - offset(x) + 1.25) / 2.5))
    f.parts = []
    for k, (x0, x1, y0, y1, hz, off) in enumerate(segs):
        f.parts.append(zbox("head" if k == 0 else "seg%d" % k, "skin", x0, y0 + off, x1, y1 + off, hz))
    # The fin skirt: one plane running round the back half of the body, following the segments' steps.
    pts_top, pts_bot = [], []
    for x0, x1, y0, y1, hz, off in reversed(segs[1:]):
        pts_top += [(x0, y1 + off + 1.0), (x1, y1 + off + 1.0)]
        pts_bot += [(x0, y0 + off - 1.0), (x1, y0 + off - 1.0)]
    tip = (-2.75, -0.75)
    skirt = [tip] + pts_top + [(13.0, top(13.0) - 0.25), (13.0, bottom(13.0) + 0.25)] + list(reversed(pts_bot))
    skirt = [(x, y) for x, y in skirt if x <= 13.0]
    f.parts.append(fin("skirt", skirt, root=(6.0, 0.0), ray=0.0, tip=0.0))
    f.parts.append(zbox("eye", "eye", 17.0, 0.25, 17.75, 1.0, 1.1))
    f.parts.append(box("mouth", "solid", 18.25, -0.75, -0.75, 19.25, -0.25, 0.75, color=C("2a2e16"), rimmed=False))
    return f


def cave_blindfish():
    def pattern(x, y, t, face, part):
        if face in ("north", "south"):
            if part.name == "head" and 9.0 <= x < 9.5 and 0.2 < t < 0.8:
                return C("e88c95")   # gill slits flushed pink
            if part.name == "head" and 10.0 <= x < 11.0 and 0.55 < t < 0.75:
                return C("d7c7c4")   # a sealed-over socket where an eye would be
            if 0.35 < t < 0.45:
                return C("efdcd6")   # the lateral line of sensory pits
        return None
    f = Fish("cave_blindfish", "Cave Blindfish",
             [(0.3, C("f6e6e2")), (0.6, C("eed5d0")), (1.01, C("e2c3c0"))], -1.75, 1.75,
             dict(base=C("f0d8d4"), light=C("fbf0ee"), ray=C("e0bfbb"), edge=C("d2a8a6")),
             cooked="cooked_cave_blindfish", title_cooked="Cooked Cave Blindfish", pattern=pattern, edge=0.9,
             gui_cap=1.05)
    f.parts = [
        zbox("body", "skin", 2, -1.75, 8.5, 1.75, 1.25),
        zbox("head", "skin", 8.5, -1.5, 11.5, 1.25, 1.1),
        zbox("wrist", "skin", 1.0, -0.75, 2.0, 0.75, 0.6),
        fin("tail", [(1.5, -0.5), (-0.75, -2.25), (-1.75, 0.0), (-0.75, 2.25), (1.5, 0.5)], root=(2.25, 0.0)),
        fin("dorsal", [(4.0, 1.5), (5.0, 3.0), (6.75, 2.75), (7.0, 1.5)]),
        # Long trailing pectoral feelers, swept back below the body.
        fin("feelers", [(9.0, -1.5), (4.5, -3.75), (5.0, -4.0), (9.75, -1.5)], root=(9.5, -1.5), ray=0),
        fin("anal", [(2.5, -1.5), (3.0, -2.75), (4.75, -2.75), (5.0, -1.5)], root=(3.75, 0)),
        box("snout_tip", "solid", 11.5, -1.0, -0.75, 12.0, 0.5, 0.75, color=C("e8c8c4"), rimmed=False),
    ]
    return f


def sculk_gulper():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up"):
            # Sculk freckles: a sparse, regular scatter of glowing cyan dots.
            u, v = x % 2.0, (y + 0.5 * (int(x / 2) % 2) + 4) % 1.5
            if 0.5 <= u < 1.0 and 0.5 <= v < 1.0:
                return C("29dfeb")
        return None
    tendril = dict(base=C("1fb3c2"), light=C("7af6ff"), ray=C("158a96"), edge=C("0b4a54"))
    f = Fish("sculk_gulper", "Sculk Gulper",
             [(0.4, C("123a44")), (0.7, C("0c2b33")), (1.01, C("081d24"))], -3.0, 2.5,
             dict(base=C("0f3a44"), light=C("176a76"), ray=C("0a2a32"), edge=C("29dfeb")),
             pattern=pattern, eye=dict(pupil=C("0a0a0a"), shine=C("7af6ff"), rim=C("0c2b33")))
    f.parts = [
        zbox("jaw_top", "skin", 8, 0.0, 14, 2.5, 2.0),
        zbox("jaw_bottom", "skin", 8, -3.0, 14.5, -0.75, 2.25),
        box("gullet", "solid", 8.0, -0.75, -1.75, 13.5, 0.0, 1.75, color=C("2a0f1c"), rimmed=False),
        fin("fangs_top", [(9.0, 0.0), (9.5, -0.6), (10.25, 0.0), (11.0, -0.6), (11.75, 0.0), (12.5, -0.6),
                          (13.25, 0.0)], z=-1.85, mat="teeth", color=C("d8f4f0")),
        fin("fangs_bottom", [(9.5, -0.75), (10.0, -0.2), (10.75, -0.75), (11.5, -0.2), (12.25, -0.75),
                             (13.0, -0.2), (13.75, -0.75)], z=1.85, mat="teeth", color=C("d8f4f0")),
        zbox("throat", "skin", 6, -2.5, 8, 2.0, 1.75),
        zbox("body", "skin", 2, -1.25, 6, 1.25, 1.25),
        zbox("tail_stock", "skin", -1, -0.6, 2, 0.6, 0.6),
        fin("whip", [(0.0, -0.5), (-4.5, -1.5), (-3.0, 0.0), (-4.5, 1.5), (0.0, 0.5)], root=(0.5, 0.0), emit=True,
            colors=tendril, ray=0),
        fin("tendril_l", [(9.0, 2.5), (9.25, 4.75), (10.0, 3.75), (10.5, 5.0), (11.0, 2.5)], z=-1.0, emit=True,
            colors=tendril, ray=0),
        fin("tendril_r", [(9.0, 2.5), (9.25, 4.75), (10.0, 3.75), (10.5, 5.0), (11.0, 2.5)], z=1.0, emit=True,
            colors=tendril, ray=0),
        zbox("eye", "eye", 12.5, 1.25, 13.25, 2.0, 2.25),
    ]
    return f


def pixie_minnow():
    def pattern(x, y, t, face, part):
        # Iridescent: the flank shifts teal to violet to pink head to tail, in three clean bands.
        if face in ("north", "south", "up") and t > 0.3:
            if x > 5.5:
                return C("62d7cf") if t < 0.7 else C("3fb0b8")
            if x > 3.5:
                return C("a782e0") if t < 0.7 else C("8862c8")
            return C("f29ad8") if t < 0.7 else C("d77ac0")
        return None
    wing = dict(base=C("c8b4ff"), light=C("f5ecff"), ray=C("a68cf0"), edge=C("ffffff"))
    f = Fish("pixie_minnow", "Pixie Minnow",
             [(0.3, C("f6f0ff")), (1.01, C("c8b4ff"))], -1.25, 1.25,
             dict(base=C("f29ad8"), light=C("ffd0f0"), ray=C("d77ac0"), edge=C("ffffff")),
             pattern=pattern, eye=dict(pupil=C("2a1840"), shine=C("ffffff")), gui_cap=1.1)
    wing_poly = [(3.0, 1.0), (2.75, 3.5), (3.75, 4.75), (5.25, 4.5), (6.0, 3.25), (5.25, 2.5), (6.25, 1.0)]
    f.parts = [
        zbox("body", "skin", 2, -1.25, 6.5, 1.25, 0.85),
        zbox("head", "skin", 6.5, -1.0, 8.25, 1.0, 0.7),
        zbox("wrist", "skin", 1.0, -0.5, 2.0, 0.5, 0.4),
        fin("tail", [(1.5, -0.4), (-1.25, -1.75), (-0.5, 0.0), (-1.25, 1.75), (1.5, 0.4)], root=(2.0, 0.0)),
        # Fairy wings: two glowing fins rising from the back.
        fin("wing_l", wing_poly, z=-0.5, colors=wing, emit=True, root=(4.5, 0.5), ray=0.35),
        fin("wing_r", [(x - 0.5, y - 0.25) for x, y in wing_poly], z=0.5, colors=wing, emit=True, root=(4.0, 0.25),
            ray=0.35),
        zbox("eye", "eye", 7.0, 0.0, 7.75, 0.75, 0.95),
    ]
    return f


def moonscale_koi():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("body", "head"):
            # Pale gold patches like a koi's, and a silver crescent on the head.
            if part.name == "head" and face in ("north", "south") and 0.6 < t:
                d = math.hypot(x - 12.0, y - 1.0)
                d2 = math.hypot(x - 12.5, y - 1.25)
                if d < 1.0 and d2 >= 0.9:
                    return C("c9dcff")
            if math.hypot(x - 6.0, y - 1.0) < 1.6 or math.hypot(x - 9.0, y + 0.75) < 1.25:
                return C("f2c76a") if t > 0.4 else C("f8dc9a")
            # Moon scales: soft blue-silver crescents along the back.
            if t > 0.55:
                row = int(math.floor((y + 3) / 1.25))
                u = (x + (0.75 if row % 2 else 0.0)) % 1.5
                if u < 0.5 and (y + 3) % 1.25 >= 0.5:
                    return C("c7d5f2")
        return None
    f = Fish("moonscale_koi", "Moonscale Koi",
             [(0.3, C("fbfbff")), (0.65, C("eef1fa")), (1.01, C("dfe6f6"))], -2.25, 2.5,
             dict(base=C("e6ecfa"), light=C("ffffff"), ray=C("c3cfee"), edge=C("9fb4e6")),
             pattern=pattern, edge=0.88, eye=dict(pupil=C("101830"), shine=C("ffffff"), iris=C("6f8fd6")))
    f.parts = [
        zbox("body", "skin", 3, -2.25, 10.5, 2.5, 1.75),
        zbox("head", "skin", 10.5, -1.75, 13.5, 1.75, 1.5),
        zbox("wrist", "skin", 1.5, -1.0, 3, 1.0, 0.9),
        # A veil tail: long flowing lobes.
        fin("tail", [(2.0, -0.75), (-0.5, -2.5), (-3.0, -4.5), (-2.0, -2.0), (-3.75, -0.5), (-1.5, 0.0),
                     (-3.75, 0.75), (-2.0, 2.25), (-3.0, 4.0), (-0.5, 2.5), (2.0, 0.75)], root=(2.5, 0.0),
            ray=0.16),
        fin("dorsal", [(4.5, 2.25), (5.5, 4.0), (9.75, 3.75), (10.25, 2.25)], ray=0.14),
        fin("pectoral", [(10.5, -1.75), (7.75, -4.75), (9.0, -4.5), (11.75, -1.75)], z=-0.5, root=(11, -1)),
        box("barbels", "solid", 13.0, -1.5, -1.25, 14.75, -1.0, 1.25, color=C("e6d3a8"), rimmed=False),
        zbox("eye", "eye", 11.75, 0.0, 13.25, 1.5, 1.75),
    ]
    return f


def magma_eel():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up"):
            # Cooling crust: a glowing lateral seam, and a crack across each plate.
            if 0.4 <= t < 0.6 and (x % 1.5) < 1.0:
                return C("ff9a2e")
            cx = x % 4.0
            if abs(cx - (2.0 + 0.5 * (int(math.floor(y * 2)) % 2))) < 0.25 and t > 0.25:
                return C("e2531a")
        return None
    glow = dict(color=C("ffd24a"), grad=("y", C("ff7a1a"), 2), rimmed=False)
    flame = dict(base=C("ff7a1a"), light=C("ffd24a"), ray=C("e2531a"), edge=C("fff0a0"))
    f = Fish("magma_eel", "Magma Eel",
             [(0.3, C("5a1e14")), (0.6, C("3a1612")), (1.01, C("2a100e"))], -1.25, 1.25,
             flame, pattern=pattern, eye=dict(pupil=C("ffe680"), shine=C("ffffff"), rim=C("ff7a1a")))
    f.parts = [
        zbox("head", "skin", 15.5, -1.25, 19.0, 1.25, 1.25),
        zbox("joint1", "solid", 15.0, -0.75, 15.5, 0.75, 0.75, emit=True, **glow),
        zbox("seg1", "skin", 11.0, -1.25, 15.0, 1.25, 1.15),
        zbox("joint2", "solid", 10.5, -0.75, 11.0, 0.75, 0.75, emit=True, **glow),
        zbox("seg2", "skin", 6.5, -1.1, 10.5, 1.1, 1.05),
        zbox("joint3", "solid", 6.0, -0.6, 6.5, 0.6, 0.6, emit=True, **glow),
        zbox("seg3", "skin", 2.5, -0.9, 6.0, 0.9, 0.85),
        fin("tail", [(3.0, -0.75), (-0.5, -1.75), (-2.5, -0.5), (-1.25, 0.0), (-2.75, 0.75), (-0.5, 1.75),
                     (3.0, 0.75)], root=(3.5, 0.0), emit=True, colors=flame, ray=0.3),
        fin("flame", [(7.0, 1.0), (7.5, 2.5), (8.5, 1.75), (9.25, 3.25), (10.25, 2.0), (11.5, 3.5), (12.5, 2.25),
                      (13.5, 3.0), (14.0, 1.0)], emit=True, colors=flame, ray=0, root=(10.5, 0.0)),
        box("eye", "eye", 17.0, 0.25, -1.4, 18.0, 1.0, 1.4, emit=True),
    ]
    return f


def ember_puffer():
    def pattern(x, y, t, face, part):
        # Basalt hide split by three glowing seams that zigzag round the body.
        if face in ("north", "south", "up", "down"):
            for sx in (3.75, 5.5, 7.25):
                if abs(x - (sx + (0.5 if int(math.floor(y + 4)) % 2 else 0.0))) < 0.25:
                    return C("ff8a2a")
        return None
    spikes = dict(base=C("ffb14a"), light=C("ffe08a"), ray=C("ff8a2a"), edge=C("fff2c0"))

    def spiky(cx, cy, r0, r1, n, rot=0.0):
        pts = []
        for k in range(n * 2):
            a = math.pi * k / n + rot
            r = r1 if k % 2 == 0 else r0
            pts.append((round(cx + r * math.cos(a), 3), round(cy + r * math.sin(a), 3)))
        return pts

    f = Fish("ember_puffer", "Ember Puffer",
             [(0.35, C("6a4a3a")), (0.7, C("3e3434")), (1.01, C("2c2626"))], -3.0, 3.0,
             spikes, pattern=pattern, eye=dict(pupil=C("1a0a00"), shine=C("ffe08a"), iris=C("ff8a2a")))
    f.parts = [
        zbox("core", "skin", 2.5, -3.0, 8.5, 3.0, 2.5),
        zbox("girth", "skin", 2.0, -2.5, 9.0, 2.5, 2.75),
        zbox("cheeks", "skin", 3.0, -2.0, 8.0, 2.0, 3.25),
        fin("spikes", spiky(5.5, 0.0, 2.6, 5.4, 8, 0.2), emit=True, root=(5.5, 0.0), ray=0, tip=0.8),
        side_fin("spikes_x", spiky(0.0, 0.0, 2.6, 5.2, 7, 0.45), 5.25, emit=True, root=(0.0, 0.0), ray=0, tip=0.8,
                 colors=spikes),
        fin("tail", [(2.25, -0.75), (-0.75, -2.0), (0.0, 0.0), (-0.75, 2.0), (2.25, 0.75)], z=0.6, root=(3.0, 0.0)),
        box("mouth", "solid", 9.0, -0.75, -0.75, 9.75, 0.25, 0.75, color=C("8a4a3a"), rimmed=False),
        box("eye", "eye", 7.0, 0.5, -3.5, 8.25, 1.75, 3.5),
    ]
    return f


def glowing_mutant_carp():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name == "body":
            # Big carp scales, bronze gone sickly.
            if (x % 1.5) < 0.5 and ((y + (0.75 if int(x / 1.5) % 2 else 0)) % 1.5) < 0.5:
                return C("6a6a2a")
        return None
    glow = dict(color=C("9dff4a"), grad=("y", C("5fe02a"), 2), rimmed=False)
    f = Fish("glowing_mutant_carp", "Glowing Mutant Carp",
             [(0.25, C("cfd08a")), (0.55, C("8e8e44")), (1.01, C("5e6a2e"))], -2.75, 2.75,
             dict(base=C("7a7a3a"), light=C("a6a65a"), ray=C("5e5e2c"), edge=C("3a3a1a")),
             pattern=pattern, gill=(10.0, 0.1, 0.9, C("4a4a22")),
             eye=dict(pupil=C("d6ff6a"), shine=C("ffffff"), rim=C("5fe02a")))
    f.parts = [
        zbox("body", "skin", 3, -2.75, 10, 2.75, 2.0),
        # Two heads, the upper one bigger, splayed apart.
        zbox("head_a", "skin", 10, 0.25, 13.5, 3.0, 1.6),
        zbox("head_b", "skin", 10, -3.0, 13.0, -0.5, 1.5),
        zbox("wrist", "skin", 1.5, -1.25, 3, 1.25, 1.1),
        fin("tail", [(2.0, -1.0), (-1.0, -3.25), (0.0, 0.0), (-1.0, 3.25), (2.0, 1.0)], root=(2.5, 0.0)),
        fin("dorsal", [(5.0, 2.5), (5.75, 4.25), (8.75, 4.0), (9.25, 2.5)]),
        box("lesion", "solid", 5.0, -1.25, -2.15, 7.75, 0.75, 2.15, emit=True, **glow),
        box("eye_a", "eye", 12.0, 1.5, -1.85, 13.0, 2.5, 1.85, emit=True),
        box("eye_b", "eye", 11.25, -1.75, -1.75, 12.0, -1.0, 1.75, emit=True),
    ]
    return f


def starlit_gar():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up"):
            # Ganoid diamonds over a night-sky body, with a scatter of star glints.
            for sx, sy in ((3.5, 0.5), (5.75, -0.5), (7.25, 0.75), (9.5, -0.25), (11.0, 0.5), (4.75, -1.0),
                           (8.5, 1.0), (13.0, 0.25)):
                if abs(x - sx) < 0.3 and abs(y - sy) < 0.3:
                    return C("fff6c8")
            if ((x + y) % 1.5) < 0.25 or ((x - y) % 1.5) < 0.25:
                return C("3a4a8a") if t > 0.4 else C("6a7ab0")
        return None
    star = dict(base=C("5a6ab8"), light=C("c8d0ff"), ray=C("3a4a8a"), edge=C("fff6c8"))
    f = Fish("starlit_gar", "Starlit Gar",
             [(0.3, C("8a96c8")), (0.55, C("2a3270")), (1.01, C("1a1f4a"))], -1.5, 1.5,
             star, pattern=pattern, eye=dict(pupil=C("0a0a20"), shine=C("fff6c8"), rim=C("c8b46a")))
    f.parts = [
        zbox("body", "skin", 2, -1.5, 12, 1.5, 1.25),
        zbox("head", "skin", 12, -1.25, 14, 1.0, 1.0),
        box("snout", "solid", 14.0, -0.5, -0.5, 20.0, 0.25, 0.5, color=C("2a3270"), grad=("x", C("4a5aa0"), 3)),
        fin("teeth", [(14.5, -0.5), (14.75, -0.85), (15.0, -0.5), (15.5, -0.85), (15.75, -0.5), (16.25, -0.85),
                      (16.5, -0.5), (17.0, -0.85), (17.25, -0.5), (17.75, -0.85), (18.0, -0.5)], z=0.0,
            mat="teeth", color=C("e8e8ff")),
        zbox("wrist", "skin", 0.5, -0.75, 2, 0.75, 0.75),
        fin("tail", [(1.25, -0.6), (-1.0, -2.25), (-2.25, 0.0), (-1.0, 2.25), (1.25, 0.6)], root=(2.0, 0.0),
            emit=True),
        fin("dorsal", [(2.5, 1.25), (3.25, 3.0), (4.75, 2.75), (5.0, 1.25)], emit=True),
        fin("anal", [(2.5, -1.25), (3.25, -3.0), (4.75, -2.75), (5.0, -1.25)], root=(3.75, 1), emit=True),
        zbox("eye", "eye", 12.5, -0.15, 13.5, 0.85, 1.25),
    ]
    return f


def abyssal_squidling():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("mantle", "tip"):
            # Rows of bioluminescent photophores.
            if (x % 1.5) < 0.5 and (abs(y - 1.0) < 0.25 or abs(y + 1.0) < 0.25):
                return C("ff5ad8") if int(x / 1.5) % 2 else C("4af0ff")
        return None
    arm = dict(color=C("3a1f5a"), grad=("x", C("6a3a9a"), 3), rimmed=False)
    f = Fish("abyssal_squidling", "Abyssal Squidling",
             [(0.3, C("6a3a9a")), (0.6, C("4a2a78")), (1.01, C("2e1a52"))], -2.0, 2.0,
             dict(base=C("5a3a9a"), light=C("8a6ad0"), ray=C("3a2470"), edge=C("4af0ff")),
             pattern=pattern, eye=dict(pupil=C("1a0020"), shine=C("ffffff"), iris=C("ff5ad8")))
    f.parts = [
        zbox("mantle", "skin", 5, -2.0, 13, 2.0, 2.0),
        zbox("tip", "skin", 13, -1.25, 15, 1.25, 1.25),
        fin("fins", [(11.0, 0.0), (13.5, 3.75), (15.75, 0.0), (13.5, -3.75)], root=(13.5, 0.0), ray=0.25),
        zbox("head", "skin", 3, -1.5, 5, 1.5, 1.5),
        box("eyes", "eye", 3.25, -0.25, -1.75, 4.75, 1.25, 1.75, emit=True),
        box("arm1", "solid", -2.5, 0.5, -1.25, 3.0, 1.0, -0.75, **arm),
        box("arm2", "solid", -2.0, 0.75, 0.75, 3.0, 1.25, 1.25, **arm),
        box("arm3", "solid", -2.25, -1.25, -1.0, 3.0, -0.75, -0.5, **arm),
        box("arm4", "solid", -1.75, -1.0, 0.5, 3.0, -0.5, 1.0, **arm),
        box("tentacle_a", "solid", -6.0, -0.25, -0.25, 3.0, 0.25, 0.25, rot=("z", 12, (3.0, 0.0, 0.0)),
            color=C("3a1f5a"), grad=("x", C("ff5ad8"), 3), rimmed=False),
        box("tentacle_b", "solid", -6.0, -0.4, -0.4, 3.0, 0.1, 0.1, rot=("z", -10, (3.0, 0.0, 0.0)),
            color=C("3a1f5a"), grad=("x", C("4af0ff"), 3), rimmed=False),
    ]
    return f


def ghoulfish():
    def pattern(x, y, t, face, part):
        if face in ("north", "south") and part.name in ("head", "body"):
            # Rotting patches with bone showing through.
            if math.hypot(x - 7.25, y + 0.5) < 0.9 or math.hypot(x - 10.0, y - 1.25) < 0.6:
                return C("d8d4c0")
            if math.hypot(x - 7.25, y + 0.5) < 1.3:
                return C("4a5a3a")
        return None
    bone = dict(color=C("e2dcc4"), rimmed=False)
    rib = [(1.5, 0.25), (1.5, 1.75), (2.0, 1.75), (2.0, 0.25), (2.75, 0.25), (2.75, 2.0), (3.25, 2.0), (3.25, 0.25),
           (4.0, 0.25), (4.0, 2.25), (4.5, 2.25), (4.5, 0.25), (5.25, 0.25), (5.25, 2.25), (5.75, 2.25), (5.75, 0.25),
           (6.0, 0.25), (6.0, -0.25), (5.75, -0.25), (5.75, -2.0), (5.25, -2.0), (5.25, -0.25), (4.5, -0.25),
           (4.5, -2.0), (4.0, -2.0), (4.0, -0.25), (3.25, -0.25), (3.25, -1.75), (2.75, -1.75), (2.75, -0.25),
           (2.0, -0.25), (2.0, -1.5), (1.5, -1.5)]
    ribs = dict(base=C("e2dcc4"), light=C("f2eedc"), ray=C("c8c0a4"), edge=C("a8a088"))
    f = Fish("ghoulfish", "Ghoulfish",
             [(0.3, C("a8b098")), (0.6, C("7f8a6e")), (1.01, C("5e6a52"))], -2.5, 2.5,
             dict(base=C("5e6a52"), light=C("7f8a6e"), ray=C("4a5440"), edge=C("2e3428")),
             pattern=pattern, gill=(9.0, 0.15, 0.85, C("3a4232")),
             eye=dict(pupil=C("6ff0ff"), shine=C("ffffff"), rim=C("1a3a4a")))
    f.parts = [
        zbox("head", "skin", 9, -1.0, 13.5, 2.25, 1.6),
        zbox("jaw", "skin", 9.5, -2.5, 13.75, -1.25, 1.4),
        box("maw", "solid", 9.25, -1.25, -1.25, 13.25, -1.0, 1.25, color=C("1a1a14"), rimmed=False),
        fin("fangs", [(10.0, -1.0), (10.25, -1.5), (10.5, -1.0), (11.25, -1.0), (11.5, -1.6), (11.75, -1.0),
                      (12.5, -1.0), (12.75, -1.5), (13.0, -1.0)], z=1.5, mat="teeth", color=C("e2dcc4")),
        zbox("body", "skin", 6, -2.5, 9, 2.5, 1.75),
        box("spine", "solid", 0.5, -0.25, -0.3, 6.0, 0.25, 0.3, **bone),
        fin("ribs_l", rib, z=-0.9, colors=ribs, ray=0, tip=2.0, root=(4, 0)),
        fin("ribs_r", rib, z=0.9, colors=ribs, ray=0, tip=2.0, root=(4, 0)),
        fin("tail", [(1.0, -0.4), (-1.0, -2.5), (-0.5, -1.25), (-1.75, -0.5), (-0.75, 0.0), (-2.0, 1.0),
                     (-0.75, 1.5), (-1.25, 2.75), (1.0, 0.4)], root=(1.5, 0.0), ray=0.3),
        fin("dorsal", [(6.25, 2.5), (6.75, 4.0), (7.25, 3.0), (7.75, 4.25), (8.5, 3.25), (8.75, 2.5)], ray=0),
        box("eye", "eye", 11.5, 0.5, -1.85, 12.5, 1.5, 1.85, emit=True),
    ]
    return f


def old_boot():
    leather = [(0.3, C("4a3020")), (0.65, C("6a4630")), (1.01, C("80583a"))]
    def pattern(x, y, t, face, part):
        if part.name == "shaft" and face in ("north", "south", "east") and (y % 1.5) < 0.5 and x > 3.5:
            return C("d8c8a0")   # lace
        return None
    f = Fish("old_boot", "Old Boot", leather, -1.5, 9.0,
             dict(base=C("2f6a2a"), light=C("4a8f3a"), ray=C("234f20"), edge=C("1a3a18")),
             pattern=pattern, hold=20.0)
    f.parts = [
        zbox("shaft", "skin", 0.5, 2.0, 4.5, 8.5, 2.0),
        zbox("cuff", "skin", 0.25, 8.5, 4.75, 9.25, 2.25),
        zbox("foot", "skin", 0.0, 0.0, 9.0, 3.0, 2.25),
        zbox("toe", "skin", 9.0, 0.0, 10.5, 2.25, 2.0),
        box("sole", "solid", -0.25, -0.75, -2.5, 10.75, 0.0, 2.5, color=C("2a2420"), rimmed=False),
        box("heel", "solid", 0.0, -1.5, -2.25, 2.5, -0.75, 2.25, color=C("3a3028"), rimmed=False),
        fin("kelp", [(2.0, 9.0), (1.5, 11.0), (2.5, 12.5), (2.0, 13.75), (3.0, 12.5), (2.5, 11.0), (3.0, 9.0)],
            z=-0.5, root=(2.5, 8.0), ray=0),
    ]
    return f


FISH = [brook_trout, mud_catfish, pike, mackerel, swordfish, anglerfish, glowfin_tetra, parrotfish, ice_char, bog_eel,
        cave_blindfish, sculk_gulper, pixie_minnow, moonscale_koi, magma_eel, ember_puffer, glowing_mutant_carp,
        starlit_gar, abyssal_squidling, ghoulfish, old_boot]


# ======================================================================================================== species

OCEAN_WARM = ["minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"]
COLD = ["minecraft:frozen_river", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:cold_ocean",
        "minecraft:deep_cold_ocean", "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes",
        "minecraft:grove", "minecraft:snowy_beach", "minecraft:snowy_slopes"]
FEYWILD = ["mymod:feywild_glade", "mymod:feywild_mushroom_wilds"]
OVERWORLD = "minecraft:overworld"
NETHER = "minecraft:the_nether"

# name: (weight, difficulty, behaviour, rod tier, extra keys)
SPECIES_DATA = OrderedDict([
    ("brook_trout", (30, 1, "steady", 0, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "#minecraft:is_forest",
                                                                        "#minecraft:is_taiga", "minecraft:meadow",
                                                                        "minecraft:cherry_grove"]))),
    ("mud_catfish", (24, 2, "sinker", 0, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "minecraft:swamp",
                                                                        "minecraft:mangrove_swamp", "minecraft:plains",
                                                                        "minecraft:sunflower_plains",
                                                                        "minecraft:savanna"]))),
    ("pike", (12, 3, "darting", 1, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "#minecraft:is_forest",
                                                                  "#minecraft:is_taiga"]))),
    ("mackerel", (30, 1, "steady", 0, dict(dimension=OVERWORLD, biomes="#minecraft:is_ocean", count=[1, 2]))),
    ("swordfish", (3, 5, "darting", 3, dict(dimension=OVERWORLD, biomes=OCEAN_WARM + ["minecraft:deep_ocean"],
                                            time="day"))),
    ("anglerfish", (8, 3, "sinker", 1, dict(dimension=OVERWORLD, biomes="#minecraft:is_deep_ocean", time="night"))),
    ("glowfin_tetra", (14, 3, "darting", 0, dict(dimension=OVERWORLD, biomes=OCEAN_WARM + [
        "minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle", "minecraft:lush_caves"],
        count=[1, 2]))),
    ("parrotfish", (18, 2, "steady", 0, dict(dimension=OVERWORLD, biomes=OCEAN_WARM, time="day"))),
    ("ice_char", (22, 2, "steady", 0, dict(dimension=OVERWORLD, biomes=COLD))),
    ("bog_eel", (16, 3, "thrasher", 0, dict(dimension=OVERWORLD, biomes=["minecraft:swamp", "minecraft:mangrove_swamp"]))),
    ("cave_blindfish", (18, 2, "steady", 0, dict(dimension=OVERWORLD, biomes=["minecraft:lush_caves",
                                                                           "minecraft:dripstone_caves"], max_y=40))),
    ("sculk_gulper", (8, 4, "sinker", 2, dict(dimension=OVERWORLD, biomes="minecraft:deep_dark", max_y=-20))),
    ("pixie_minnow", (24, 3, "darting", 0, dict(dimension="mymod:feywild", biomes=FEYWILD, count=[1, 3]))),
    ("moonscale_koi", (5, 4, "floater", 3, dict(dimension="mymod:feywild", biomes=FEYWILD, time="night"))),
    ("magma_eel", (14, 4, "thrasher", 3, dict(fluid="lava", dimension=NETHER, biomes="#minecraft:is_nether"))),
    ("ember_puffer", (10, 3, "floater", 3, dict(fluid="lava", dimension=NETHER, biomes=[
        "minecraft:nether_wastes", "minecraft:basalt_deltas", "minecraft:crimson_forest"]))),
    # Uranium lies between y -48 and 0, in any Overworld biome: the carp lives in the water down there with it.
    ("glowing_mutant_carp", (10, 3, "thrasher", 1, dict(dimension=OVERWORLD, max_y=0))),
    ("starlit_gar", (3, 5, "darting", 4, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "#minecraft:is_ocean"],
                                              time="night", weather="clear"))),
    ("abyssal_squidling", (2, 5, "thrasher", 4, dict(dimension=OVERWORLD, biomes="#minecraft:is_deep_ocean",
                                                     time="night"))),
    ("ghoulfish", (20, 4, "thrasher", 1, dict(dimension=OVERWORLD, time="night", weather="thunder"))),
    ("old_boot", (4, 1, "sinker", 0, dict(dimension=OVERWORLD))),
])

KEY_ORDER = ["item", "weight", "difficulty", "behavior", "min_rod_tier", "fluid", "dimension", "biomes", "time",
             "weather", "min_y", "max_y", "count"]


def species_json(name):
    weight, difficulty, behavior, tier, extra = SPECIES_DATA[name]
    obj = dict(item="mymod:" + name, weight=weight, difficulty=difficulty, behavior=behavior, min_rod_tier=tier)
    obj.update(extra)
    return OrderedDict((k, obj[k]) for k in KEY_ORDER if k in obj)


# ========================================================================================================= recipes


def recipe_with_unlock(name, body, category, unlock):
    write(os.path.join(DATA, "recipe", name + ".json"), body)
    adv = OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", OrderedDict([("conditions", OrderedDict([("items", [OrderedDict([("items", unlock)])])])),
                                      ("trigger", "minecraft:inventory_changed")])),
            ("has_the_recipe", OrderedDict([("conditions", OrderedDict([("recipes", "mymod:" + name)])),
                                            ("trigger", "minecraft:recipe_unlocked")]))])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", OrderedDict([("recipes", ["mymod:" + name])]))])
    write(os.path.join(DATA, "advancement", "recipes", category, name + ".json"), adv)


def cooking(raw, cooked):
    for kind, suffix, ticks in (("smelting", "", 200), ("smoking", "_from_smoking", 100),
                                ("campfire_cooking", "_from_campfire_cooking", 600)):
        recipe_with_unlock(cooked + suffix, OrderedDict([
            ("type", "minecraft:" + kind), ("category", "food"), ("cookingtime", ticks), ("experience", 0.35),
            ("ingredient", "mymod:" + raw), ("result", OrderedDict([("id", "mymod:" + cooked)]))]), "food", "mymod:" + raw)


def shapeless(name, ingredients, result, count=1, unlock=None, category="misc"):
    recipe_with_unlock(name, OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]), category, unlock or ingredients[0])


def write_recipes():
    for build_fish in FISH:
        f = build_fish()
        if f.cooked:
            cooking(f.name, f.cooked)
    # Fish oil: rendered from oily fish into a bottle; burns like coal, and four of them make a canister of bio-crude
    # for the Refinery (kerosene, then rocket fuel or napalm).
    shapeless("fish_oil_from_mackerel", ["mymod:mackerel", "minecraft:glass_bottle"], "mymod:fish_oil")
    shapeless("fish_oil_from_bog_eel", ["mymod:bog_eel", "minecraft:glass_bottle"], "mymod:fish_oil")
    shapeless("fish_oil_from_magma_eel", ["mymod:magma_eel", "minecraft:glass_bottle", "minecraft:glass_bottle",
                                          "minecraft:glass_bottle"], "mymod:fish_oil", 3)
    shapeless("crude_oil_canister_from_fish_oil", ["mymod:empty_canister", "mymod:fish_oil", "mymod:fish_oil",
                                                   "mymod:fish_oil", "mymod:fish_oil"], "mymod:crude_oil_canister",
              unlock="mymod:fish_oil")
    # The anglerfish's lure lights ink.
    shapeless("glow_ink_sac_from_anglerfish", ["mymod:anglerfish", "minecraft:ink_sac", "minecraft:ink_sac"],
              "minecraft:glow_ink_sac", 2)
    shapeless("fae_dust_from_pixie_minnow", ["mymod:pixie_minnow"], "mymod:fae_dust")
    # Ice char freeze packed ice hard enough for the Froststeel quench.
    shapeless("blue_ice_from_ice_char", ["mymod:ice_char", "minecraft:packed_ice", "minecraft:packed_ice",
                                         "minecraft:packed_ice", "minecraft:packed_ice"], "minecraft:blue_ice")
    # A drowned soul wrung out of three ghoulfish, into the Foundry for soul steel.
    shapeless("soul_charge_from_ghoulfish", ["mymod:ghoulfish", "mymod:ghoulfish", "mymod:ghoulfish",
                                             "#minecraft:soul_fire_base_blocks"], "mymod:soul_charge")
    shapeless("blaze_powder_from_ember_puffer", ["mymod:ember_puffer"], "minecraft:blaze_powder", 2)
    shapeless("sculk_from_sculk_gulper", ["mymod:sculk_gulper"], "minecraft:sculk", 2)
    shapeless("leather_from_old_boot", ["mymod:old_boot"], "minecraft:leather")
    # The banker pays for the prize fish (listed in its trade sets, levels 3 and 4).
    for level, fish, emeralds in ((3, "moonscale_koi", 4), (4, "starlit_gar", 6)):
        write(os.path.join(DATA, "villager_trade", "banker", str(level), fish + "_emerald.json"), OrderedDict([
            ("gives", OrderedDict([("id", "minecraft:emerald"), ("count", emeralds)])), ("max_uses", 8),
            ("reputation_discount", 0.05), ("wants", OrderedDict([("id", "mymod:" + fish)])), ("xp", 15)]))


# ====================================================================================================== fish oil


def fish_oil_texture():
    """Vanilla's honey bottle, its honey recoloured to a clear greenish amber oil."""
    from gen_longswords import client_jar, read_png
    with zipfile.ZipFile(client_jar()) as z:
        w, h, px = read_png(z.read("assets/minecraft/textures/item/honey_bottle.png"))
    ramp = [C("5a5a1e"), C("8a8a2a"), C("b8b23a"), C("d8d266"), C("f0eca0")]
    out = []
    for k, c in enumerate(px):
        r, g, b, a = c
        if a and k // w >= 5 and r > g + 25 and r > b + 60:      # honey (below the cork): saturated orange
            out.append(steps(ramp, lum(c) / 255.0 * 1.1))
        else:
            out.append(c)
    png(os.path.join(TEXTURES, "fish_oil.png"), w, h, out)
    write(os.path.join(MODELS, "fish_oil.json"), OrderedDict([("parent", "minecraft:item/generated"),
                                                               ("textures", OrderedDict(layer0="mymod:item/fish/fish_oil"))]))
    write(os.path.join(ITEMS, "fish_oil.json"), {"model": OrderedDict([("type", "minecraft:model"),
                                                                        ("model", "mymod:item/fish_oil")])})


# ========================================================================================================= preview

LIGHTS = [[v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (0.2, 1.0, -0.7)],
          [v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (-0.2, 1.0, 0.7)]]


def lightness(n):
    return min(1.0, 0.4 + 0.6 * sum(max(0.0, sum(n[k] * l[k] for k in range(3))) for l in LIGHTS))


QUAD = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
          "down": (0, -1, 0)}


def render(elements, atlas, size, project, turn, w, h):
    img = [None] * (w * h)
    depth = [-1e9] * (w * h)
    for e in elements:
        r = element_rotation(e)
        for face, fc in e["faces"].items():
            pts = [list(p) for p in QUAD[face](e["from"], e["to"])]
            n = NORMAL[face]
            if r:
                m, o = r
                pts = [[o[a] + v for a, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))] for p in pts]
                n = apply(m, list(n))
            lit = 1.0 if e.get("light_emission") else lightness(turn(n))
            sp = [project(p) for p in pts]
            u0, v0, u1, v1 = fc["uv"]
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
                        img[i] = tuple(int(col[k] * lit) for k in range(3))
    return img


def ortho(view, scale, w, h, centre):
    def project(p):
        v = apply(view, [p[k] - centre[k] for k in range(3)])
        return (w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2])
    return project


def gui_view(disp, px):
    t = disp["gui"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    k = px / 16.0

    def project(p):
        v = apply(m, [s * (p[a] - 8) for a in range(3)])
        return (px / 2 + (v[0] + t["translation"][0]) * k, px / 2 - (v[1] + t["translation"][1]) * k, v[2])
    return project, lambda n: apply(m, n)


def first_person(disp, w, h, fov=70.0):
    t = disp["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    f = (h / 2) / math.tan(math.radians(fov / 2))

    def project(p):
        q = apply(m, [s * (p[k] / 16 - 0.5) for k in range(3)])
        x, y, z = (q[k] + t["translation"][k] / 16 + (0.56, -0.52, -0.72)[k] for k in range(3))
        return (w / 2 + x / -z * f, h / 2 - y / -z * f, z)
    return project, lambda n: apply(m, n)


def third_person(disp, w, h, scale):
    """The right hand seen from the side: the display transform, then the arm's pose (roughly down and forward)."""
    t = disp["thirdperson_righthand"]
    m = display_matrix(t["rotation"])
    arm = rx(-80)
    s = t["scale"][0]
    view = ry(-90)

    def project(p):
        q = apply(m, [s * (p[k] - 8) for k in range(3)])
        q = [q[k] + t["translation"][k] for k in range(3)]
        q = apply(arm, q)
        v = apply(view, q)
        return (w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2])
    return project, lambda n: apply(view, apply(arm, apply(m, n)))


def blit(out, ow, img, w, h, ox, oy, k=1, bg=None):
    for y in range(h * k):
        for x in range(w * k):
            c = img[(y // k) * w + x // k]
            if c is None:
                c = bg
            if c is not None:
                out[(oy + y) * ow + ox + x] = tuple(c[:3]) + (255,)


def preview(path, built):
    cell = []
    for f, elements, size, atlases, disp in built:
        lo = [min(min(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
        hi = [max(max(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
        c = [(lo[a] + hi[a]) / 2 for a in range(3)]
        imgs = []
        project, turn = gui_view(disp, 160)
        imgs.append((render(elements, atlases[False], size, project, turn, 160, 160), 160, 160, 1, None))
        sw, sh = 200, 120
        k = min(9.0, (sw - 10) / (hi[0] - lo[0]), (sh - 10) / (hi[1] - lo[1]))
        imgs.append((render(elements, atlases[False], size, ortho(ry(0), k, sw, sh, c), lambda n: n, sw, sh), sw, sh, 1,
                     None))
        project, turn = gui_view(disp, 32)
        imgs.append((render(elements, atlases[False], size, project, turn, 32, 32), 32, 32, 3, (139, 139, 139)))
        if f.cooked:
            imgs.append((render(elements, atlases[True], size, project, turn, 32, 32), 32, 32, 3, (139, 139, 139)))
        else:
            imgs.append(([None] * (32 * 32), 32, 32, 3, None))
        project, turn = first_person(disp, 192, 108)
        imgs.append((render(elements, atlases[False], size, project, turn, 192, 108), 192, 108, 1, (90, 110, 130)))
        cell.append(imgs)
    gap = 8
    cw = sum(w * k + gap for _, w, _, k, _ in cell[0]) + gap
    ch = 160 + gap
    cols = 2
    rows = (len(cell) + cols - 1) // cols
    W, H = cw * cols, ch * rows + gap
    out = [(52, 54, 60, 255)] * (W * H)
    for n, imgs in enumerate(cell):
        ox, oy = (n % cols) * cw + gap, (n // cols) * ch + gap
        for img, w, h, k, bg in imgs:
            blit(out, W, img, w, h, ox, oy + (160 - h * k) // 2, k, bg)
            ox += w * k + gap
    png(path, W, H, out)
    print("preview:", path)


# =========================================================================================================== main


def merge_lang(entries):
    with open(LANG) as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(LANG, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def main():
    preview_path = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else None
    built = []
    lang = OrderedDict()
    tag_values = []
    for make in FISH:
        f = make()
        elements, size, atlases = build(f)
        check_elements(f.name, elements)
        disp = displays(f, elements)
        png(os.path.join(TEXTURES, f.name + ".png"), size, size, atlases[False])
        model = OrderedDict([("credit", "mymod fish - generated by tools/gen_fish.py"), ("texture_size", [size, size]),
                             ("textures", OrderedDict([("0", "mymod:item/fish/" + f.name),
                                                       ("particle", "mymod:item/fish/" + f.name)])),
                             ("display", disp), ("elements", elements)])
        write(os.path.join(MODELS, f.name + ".json"), model)
        write(os.path.join(ITEMS, f.name + ".json"),
              {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + f.name)])})
        lang["item.mymod." + f.name] = f.title
        if f.name != "old_boot":
            tag_values.append("mymod:" + f.name)
        if f.cooked:
            png(os.path.join(TEXTURES, f.cooked + ".png"), size, size, atlases[True])
            write(os.path.join(MODELS, f.cooked + ".json"), OrderedDict([
                ("parent", "mymod:item/" + f.name),
                ("textures", OrderedDict([("0", "mymod:item/fish/" + f.cooked), ("particle", "mymod:item/fish/" + f.cooked)]))]))
            write(os.path.join(ITEMS, f.cooked + ".json"),
                  {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + f.cooked)])})
            lang["item.mymod." + f.cooked] = f.title_cooked
            tag_values.append("mymod:" + f.cooked)
        write(os.path.join(SPECIES, f.name + ".json"), species_json(f.name))
        n = sum(1 for e in elements)
        print(f"{f.name}: {n} elements, atlas {size}")
        built.append((f, elements, size, atlases, disp))
    fish_oil_texture()
    lang["item.mymod.fish_oil"] = "Fish Oil"
    lang["item.mymod.glowing_mutant_carp.desc"] = "Faintly radioactive"
    lang["item.mymod.sculk_gulper.desc"] = "Full of swallowed experience"
    merge_lang(lang)
    write(os.path.join(RES, "data", "minecraft", "tags", "item", "fishes.json"),
          OrderedDict([("replace", False), ("values", tag_values)]))
    write_recipes()
    if preview_path:
        preview(preview_path, built)


if __name__ == "__main__":
    main()
