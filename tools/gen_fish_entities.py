#!/usr/bin/env python3
"""Living fish: the entity models, entity textures, bucket and spawn egg sprites, loot tables, tags and lang for the
swimming fish of mymod's fishing catch (the item sprites, species data and recipes are tools/gen_fish.py's).

Run from anywhere: python3 tools/gen_fish_entities.py [preview.png]

Geometry. Every fish is rebuilt from the small 3D cube models its item had before the items became flat sprites (git
fa7cc0b, tools/gen_fish.py): a handful of boxes (body, head, tail stock, eye) and zero-thickness fin planes whose
outline is cut out of the texture. The geometry below is that generator's, unchanged, in its model space: x runs tail
(low) to head (high), y up, z across the fish centred on 0, in pixels. Each fish's RIG then splits it into ModelPart
groups: a body root, a separately pivoted tail (and, for the eels, gulper and ghoulfish, a chain of them), and the bits
that move on their own (catfish whiskers, the anglerfish's lure, the pixie minnow's wings, the squidling's arms). Fins
that run across a joint (the eels' skirts and crests) are cut at it, each piece keeping the whole fin's painting.

The second catch (largemouth bass to cometfish) had no old item models: they're built new here in the same
construction. The flounder is built standing on its edge like the rest, its eyed side on +z, and flagged flat in its
RIG, so FishEntityRenderer always lays it on its side (eyes up) and its tail beats up and down.

Entity model space is vanilla's: y down, the head toward -z, the lowest point on y = 24 (the ground). It is built at two
units to the old pixel, so the old two-texels-a-pixel painting maps one texel to a unit, and the layer is then scaled
(MeshTransformer.scaling) to the fish's size in the world, from a tetra the size of a small tropical fish to a swordfish
as long as a large salmon. Box sizes are rounded to whole units so their faces sit on whole texels.

Textures. Each box face gets its own patch in vanilla's box-UV layout and every texel is coloured from the old model
point it lands on with the old painters, so bands, spots and stripes run on unbroken across boxes and both flanks match
(no noise: the same flat, posterised ramps). Fins are one face each (entity cutout draws it from both sides, so there's
no back face to fight with it). Glowing parts (and a few glowing skin colours) also go into <fish>_glow.png, drawn by an
emissive layer.

Written:
- src/client/.../client/fishing/fish/FishModelLayers.java: every fish's LayerDefinition and its renderer numbers;
- textures/entity/fish/<fish>.png and <fish>_glow.png;
- item sprites, models and definitions for <fish>_bucket (vanilla's fish bucket recoloured; the lava fish in a lava
  bucket) and <fish>_spawn_egg (vanilla's cod egg recoloured);
- loot_table/entities/<fish>.json (vanilla cod's: the fish, cooked if it died burning, and now and then bone meal);
- the vanilla fish entity tags; lang for entities, buckets and eggs.

Checks: every model at rest goes through tools/coplanar.py, and the script stops on any coplanar overlapping faces.

The preview (optional argument) draws every fish from the side and three-quarters at its true relative size next to
vanilla's cod and a one-block ruler, with two frames of its swimming wag."""
import json, math, os, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png, write, rx, ry, rz, apply  # noqa: E402
from coplanar import Face, fights  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data", "mymod")
ENTITY_TEXTURES = os.path.join(ASSETS, "textures", "entity", "fish")
ITEM_TEXTURES = os.path.join(ASSETS, "textures", "item", "fish")
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "mymod", "client", "fishing", "fish", "FishModelLayers.java")

D = 2              # texels per old model pixel (and entity model units per old pixel)
FACES = ["north", "east", "south", "west", "up", "down"]
HOLD = 45.0        # the old items' tilt in hand (unused here; the old Fish class still takes it)


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



# ------------------------------------------------------------------------------------------------ the second catch
#
# Built new in the same construction (no old item model behind these): a body box, a head box, a tail stock, fin planes
# and an eye box standing proud of the head on both flanks.


def spotted_fin(spot, every=3):
    """A fin painter with dark spots in a regular lattice over the usual rays (crappie fins)."""
    def paint(f, part, face, p, i, j, w, h):
        c = paint_fin(f, part, face, p, i, j, w, h)
        if c is None:
            return None
        q = (p[0], p[1]) if part.axis == "z" else (p[2], p[1])
        if (int(math.floor(q[0] * 2)) + 2 * int(math.floor(q[1] * 2))) % every == 0:
            return spot
        return c
    return paint


def largemouth_bass():
    def pattern(x, y, t, face, part):
        if face in ("north", "south") and part.name == "head" and x > 13.0 and -1.25 <= y < -0.75:
            return C("2a2a18")                       # the big mouth's gape, running back past the eye
        if face in ("north", "south", "up") and part.name in ("body", "head") and 0.42 < t < 0.6 and (x % 2.0) < 1.5:
            return C("2e3e1e")                       # the dark, blotchy lateral band
        return None
    f = Fish("largemouth_bass", "Largemouth Bass",
             [(0.2, C("eeeed4")), (0.38, C("d8dcb0")), (0.55, C("9aac62")), (0.75, C("6a8a3a")), (1.01, C("4a6a2a"))], -2.75, 2.75,
             dict(base=C("5e7238"), light=C("8a9a58"), ray=C("3e5228"), edge=C("2e3e1e")),
             cooked="cooked_largemouth_bass", title_cooked="Cooked Largemouth Bass", pattern=pattern,
             gill=(11.5, 0.15, 0.85, C("3a4a22")), eye=dict(pupil=C("141414"), shine=C("f0f0d0"), rim=C("8a7a30")))
    f.parts = [
        zbox("body", "skin", 3, -2.75, 11, 2.75, 1.75),
        zbox("head", "skin", 11, -2.25, 15, 2.0, 1.5),
        zbox("jaw", "skin", 13, -2.5, 15.5, -1.5, 1.25),
        zbox("wrist", "skin", 1.5, -1.25, 3, 1.25, 1.0),
        fin("tail", [(2.0, -1.0), (-1.25, -3.25), (-0.5, 0.0), (-1.25, 3.25), (2.0, 1.0)], root=(2.5, 0.0)),
        fin("dorsal", [(4.0, 2.5), (4.75, 4.75), (6.0, 4.25), (6.75, 3.5), (7.5, 4.5), (9.0, 4.25), (9.5, 2.5)], root=(7, 0)),
        fin("anal", [(4.0, -2.5), (4.75, -4.25), (6.5, -4.0), (7.0, -2.5)], root=(5.5, 0)),
        fin("pelvic", [(9.0, -2.5), (9.75, -4.0), (11.0, -3.75), (11.0, -2.5)], root=(10, 0)),
        zbox("eye", "eye", 12.5, 0.25, 13.5, 1.25, 1.75),
    ]
    return f


def bluegill():
    def pattern(x, y, t, face, part):
        if part.name == "head" and face in ("north", "south") and 9.5 <= x < 10.5 and -0.25 <= y < 1.25:
            # The ear flap: a dark tab on the gill cover, edged in blue.
            return C("3a6ad0") if y >= 1.0 or x < 9.75 else C("1a1a2a")
        if face in ("north", "south") and t < 0.32 and x > 6.0:
            return C("e8a040")                       # the orange breast
        if face in ("north", "south", "up") and part.name == "body" and t > 0.35 and (x % 1.5) < 0.5:
            return C("3a5248")                       # faint vertical bars
        return None
    f = Fish("bluegill", "Bluegill",
             [(0.2, C("e8c060")), (0.35, C("c8c890")), (0.6, C("7a9a80")), (0.8, C("4a6a62")), (1.01, C("3a5a5a"))], -3.25, 3.25,
             dict(base=C("4a6a62"), light=C("7a9a8a"), ray=C("2e4a4a"), edge=C("1e3434")),
             cooked="cooked_bluegill", title_cooked="Cooked Bluegill", pattern=pattern,
             eye=dict(pupil=C("141414"), shine=C("e8f0e8"), rim=C("4a5a40")))
    f.parts = [
        zbox("body", "skin", 3, -3.25, 9.5, 3.25, 1.25),
        zbox("head", "skin", 9.5, -2.25, 12, 2.5, 1.0),
        zbox("wrist", "skin", 1.75, -1.0, 3, 1.0, 0.75),
        fin("tail", [(2.0, -0.75), (-0.25, -2.75), (0.5, 0.0), (-0.25, 2.75), (2.0, 0.75)], root=(2.5, 0.0)),
        fin("dorsal", [(3.5, 3.0), (4.0, 5.0), (8.0, 5.25), (9.5, 3.0)], ray=0.14, root=(6.5, 0)),
        fin("anal", [(3.5, -3.0), (4.0, -4.75), (6.5, -4.5), (7.0, -3.0)], root=(5, 0)),
        zbox("eye", "eye", 10.5, 0.5, 11.5, 1.5, 1.25),
    ]
    return f


def yellow_perch():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("body", "head") and x < 11 and t > 0.4:
            if ((x + 0.3 * y) % 1.75) < 0.6:
                return C("3a4a1e")                   # six or seven dark saddles, narrowing toward the belly
        return None
    orange = dict(base=C("e87a30"), light=C("f0a050"), ray=C("c85a20"), edge=C("a04818"))
    spiny = dict(base=C("8a8a38"), light=C("b8a850"), ray=C("6a6a28"), edge=C("3a3a14"))
    f = Fish("yellow_perch", "Yellow Perch",
             [(0.2, C("f4f0dc")), (0.4, C("e8d470")), (0.7, C("e0b838")), (1.01, C("8a7a28"))], -2.0, 2.0,
             dict(base=C("b8a850"), light=C("d8c870"), ray=C("8a8a38"), edge=C("5a5420")),
             cooked="cooked_yellow_perch", title_cooked="Cooked Yellow Perch", pattern=pattern,
             gill=(10.75, 0.15, 0.85, C("6a6020")))
    f.parts = [
        zbox("body", "skin", 3, -2.0, 10.5, 2.0, 1.25),
        zbox("head", "skin", 10.5, -1.5, 13.5, 1.5, 1.0),
        zbox("wrist", "skin", 1.5, -1.0, 3, 1.0, 0.75),
        fin("tail", [(2.0, -0.75), (-1.0, -2.75), (0.0, 0.0), (-1.0, 2.75), (2.0, 0.75)], root=(2.5, 0.0)),
        fin("dorsal", [(6.5, 2.0), (7.0, 4.0), (9.5, 3.5), (10.0, 2.0)], colors=spiny, root=(8, 0)),
        fin("dorsal_soft", [(3.5, 2.0), (4.0, 3.5), (5.75, 3.25), (6.0, 2.0)], root=(4.75, 0)),
        fin("anal", [(3.5, -2.0), (4.0, -3.5), (5.5, -3.25), (5.75, -2.0)], colors=orange, root=(4.5, 0)),
        fin("pelvic", [(9.0, -2.0), (9.5, -3.5), (10.5, -3.25), (10.5, -2.0)], colors=orange, root=(9.75, 0)),
        zbox("eye", "eye", 11.5, 0.0, 12.5, 1.0, 1.25),
    ]
    return f


def walleye():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("body",) and t > 0.6 and (x % 2.5) < 1.0:
            return C("4e4a20")                       # dark saddles over the olive-gold back
        return None

    def tail_fin(f, part, face, p, i, j, w, h):
        c = paint_fin(f, part, face, p, i, j, w, h)
        if c is not None and p[1] < -2.0 and p[0] < -0.25:
            return C("f4f0e4")                       # the white tip of the lower lobe
        return c
    f = Fish("walleye", "Walleye",
             [(0.2, C("f0ead0")), (0.45, C("c8b868")), (0.75, C("9a8a40")), (1.01, C("6a6430"))], -2.0, 2.0,
             dict(base=C("7a7238"), light=C("a89c58"), ray=C("5a5428"), edge=C("3a3618")),
             cooked="cooked_walleye", title_cooked="Cooked Walleye", pattern=pattern,
             gill=(12.25, 0.15, 0.85, C("5a5428")),
             # The walleye's eye: big, milky and glassy (it sees in the dark).
             eye=dict(pupil=C("c8d4d0"), shine=C("ffffff"), iris=C("8a9a94"), rim=C("6a7470")))
    f.mats = {"walleye_tail": tail_fin}
    f.parts = [
        zbox("body", "skin", 3, -2.0, 12, 2.0, 1.25),
        zbox("head", "skin", 12, -1.5, 15.5, 1.5, 1.0),
        zbox("wrist", "skin", 1.5, -1.0, 3, 1.0, 0.75),
        fin("tail", [(2.0, -0.75), (-1.25, -3.0), (-0.25, 0.0), (-1.25, 3.0), (2.0, 0.75)], mat="walleye_tail", root=(2.5, 0.0)),
        fin("dorsal", [(8.5, 2.0), (9.0, 4.0), (11.25, 3.5), (11.5, 2.0)], root=(10, 0)),
        fin("dorsal_soft", [(4.0, 2.0), (4.5, 3.5), (7.0, 3.25), (7.5, 2.0)], root=(5.75, 0)),
        fin("anal", [(4.0, -2.0), (4.5, -3.5), (6.5, -3.25), (7.0, -2.0)], root=(5.5, 0)),
        zbox("eye", "eye", 13.0, -0.25, 14.0, 0.75, 1.25),
    ]
    return f


def crappie():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("body", "head") and t > 0.15:
            if (int(math.floor(x * 2)) * 7 + int(math.floor((y + 4) * 2)) * 3) % 5 == 0:
                return C("2e342a")                   # the black crappie's speckling
        return None
    f = Fish("crappie", "Crappie",
             [(0.3, C("eef0e6")), (0.6, C("c8ccbc")), (0.85, C("8a9480")), (1.01, C("5a6a58"))], -3.0, 3.0,
             dict(base=C("8a9080"), light=C("b8bcac"), ray=C("5a6050"), edge=C("3a4034")),
             cooked="cooked_crappie", title_cooked="Cooked Crappie", pattern=pattern, edge=0.86,
             eye=dict(pupil=C("141414"), shine=C("f0f0e8"), rim=C("6a7060")))
    f.mats = {"spotfin": spotted_fin(C("2e342a"))}
    f.parts = [
        zbox("body", "skin", 3, -3.0, 9.5, 3.0, 1.0),
        zbox("head", "skin", 9.5, -2.25, 12.5, 2.25, 0.75),
        zbox("snout", "skin", 12.5, -0.75, 13.5, 0.75, 0.5),
        zbox("wrist", "skin", 1.5, -1.0, 3, 1.0, 0.5),
        fin("tail", [(2.0, -0.75), (-0.5, -2.75), (0.5, 0.0), (-0.5, 2.75), (2.0, 0.75)], root=(2.5, 0.0)),
        fin("dorsal", [(4.0, 2.75), (4.75, 5.25), (7.5, 5.0), (8.0, 2.75)], mat="spotfin", root=(6, 0)),
        fin("anal", [(3.5, -2.75), (4.25, -5.0), (7.0, -4.75), (7.5, -2.75)], mat="spotfin", root=(5.5, 0)),
        zbox("eye", "eye", 10.5, 0.25, 11.5, 1.25, 1.0),
    ]
    return f


def sturgeon():
    def pattern(x, y, t, face, part):
        return None
    scute = dict(color=C("e2dec8"), rimmed=False)
    f = Fish("sturgeon", "Sturgeon",
             [(0.25, C("d8d2bc")), (0.5, C("8a887a")), (0.8, C("5a5850")), (1.01, C("4a4a42"))], -1.75, 1.75,
             dict(base=C("4e4e44"), light=C("6e6e60"), ray=C("34342c"), edge=C("1e1e18")),
             cooked="sturgeon_steak", title_cooked="Sturgeon Steak", pattern=pattern,
             eye=dict(pupil=C("101010"), shine=C("d8d8c8"), rim=C("4a4a42")))
    f.parts = [
        zbox("snout", "skin", 15, -0.75, 18, 0.5, 0.75),
        zbox("head", "skin", 12.5, -1.5, 15, 1.25, 1.25),
        zbox("body", "skin", 3, -1.75, 12.5, 1.75, 1.5),
        zbox("tail_stock", "skin", -1, -0.75, 3, 1.0, 0.75),
        # A shark's tail: the upper lobe carries the spine on up and back, the lower one is short.
        fin("tail", [(1.0, -0.5), (-0.5, -2.0), (-1.5, -0.5), (-5.0, 2.75), (-4.0, 3.25), (1.0, 1.0)], root=(1.5, 0.0)),
        fin("dorsal", [(4.0, 1.75), (4.5, 3.0), (6.0, 2.75), (6.0, 1.75)], root=(5, 0)),
        fin("anal", [(4.0, -1.75), (4.5, -2.75), (5.75, -2.5), (6.0, -1.75)], root=(5, 0)),
        box("barbel_l", "solid", 15.5, -1.75, -0.75, 16.0, -0.75, -0.25, color=C("8a7a5a"), rimmed=False),
        box("barbel_r", "solid", 15.5, -1.75, 0.25, 16.0, -0.75, 0.75, color=C("8a7a5a"), rimmed=False),
        zbox("eye", "eye", 13.5, 0.0, 14.0, 0.5, 1.5),
    ]
    # Bony scutes: a row down the back and one along each flank.
    for k, x in enumerate((4.0, 5.5, 7.0, 8.5, 10.0, 11.5)):
        f.parts.append(box("scute_top%d" % k, "solid", x, 1.75, -0.5, x + 1.0, 2.25, 0.5, **scute))
        f.parts.append(box("scute_l%d" % k, "solid", x, -0.25, -2.0, x + 1.0, 0.25, -1.5, **scute))
        f.parts.append(box("scute_r%d" % k, "solid", x, -0.25, 1.5, x + 1.0, 0.25, 2.0, **scute))
    return f


def red_snapper():
    def pattern(x, y, t, face, part):
        return None
    f = Fish("red_snapper", "Red Snapper",
             [(0.25, C("f8d0c8")), (0.45, C("f08880")), (0.75, C("e05a5a")), (1.01, C("c83a48"))], -2.75, 2.75,
             dict(base=C("e05a68"), light=C("f88a90"), ray=C("c03848"), edge=C("8a2030")),
             cooked="cooked_red_snapper", title_cooked="Cooked Red Snapper", pattern=pattern,
             gill=(11.0, 0.15, 0.85, C("a83040")), eye=dict(pupil=C("1a0808"), shine=C("ffe0e0"), iris=C("d02020")))
    f.parts = [
        zbox("body", "skin", 3, -2.75, 10.5, 2.75, 1.5),
        zbox("head", "skin", 10.5, -2.0, 13.5, 2.25, 1.25),
        zbox("snout", "skin", 13.5, -1.25, 14.5, 0.25, 1.0),
        zbox("wrist", "skin", 1.5, -1.0, 3, 1.0, 1.0),
        fin("tail", [(2.0, -0.75), (-1.25, -3.25), (0.0, 0.0), (-1.25, 3.25), (2.0, 0.75)], root=(2.5, 0.0)),
        fin("dorsal", [(3.5, 2.5), (4.5, 4.75), (9.5, 4.25), (10.25, 2.5)], ray=0.12, root=(7, 0)),
        fin("anal", [(3.5, -2.5), (4.25, -4.25), (6.25, -4.0), (6.75, -2.5)], root=(5, 0)),
        fin("pelvic", [(9.0, -2.5), (9.75, -4.25), (10.75, -4.0), (10.75, -2.5)], root=(10, 0)),
        zbox("eye", "eye", 11.5, 0.5, 12.5, 1.5, 1.5),
    ]
    return f


def flounder():
    """Built standing on its edge like the other fish, its eyed side (both eyes) on +z; the renderer lays it flat on that
    side's opposite, so it lies on the bed looking up, as a flounder does (RIG flat)."""
    spots = [(4.0, 1.5), (6.5, -1.0), (8.5, 2.0), (10.0, -1.75), (5.0, -2.5), (9.0, 0.25)]

    def pattern(x, y, t, face, part):
        if face == "north":
            return C("eeeadc")                       # the blind side, pale
        if face == "south" and part.name in ("body", "head"):
            for sx, sy in spots:
                d = math.hypot(x - sx, y - sy)
                if d < 0.4:
                    return C("d8c8a0")               # ocellated spots: a pale centre in a dark ring
                if d < 0.85:
                    return C("4a3824")
            if ((int(math.floor(x * 2)) * 3 + int(math.floor((y + 4) * 2)) * 5) % 7) == 0:
                return C("6a5034")                   # fine mottling
        return None
    f = Fish("flounder", "Flounder",
             [(0.3, C("7a6040")), (0.7, C("947852")), (1.01, C("a88a60"))], -3.5, 3.5,
             dict(base=C("8a6e48"), light=C("a88a60"), ray=C("6a5234"), edge=C("4a3a24")),
             cooked="cooked_flounder", title_cooked="Cooked Flounder", pattern=pattern, edge=0.9,
             eye=dict(pupil=C("141414"), shine=C("e8e0c8"), rim=C("5a4830")))
    f.parts = [
        zbox("body", "skin", 2, -3.5, 12, 3.5, 0.75),
        zbox("head", "skin", 12, -2.25, 13.5, 2.0, 0.5),
        zbox("wrist", "skin", 0.75, -1.0, 2, 1.0, 0.5),
        # The fringe fins round the edge of the disc, top and bottom.
        fin("dorsal", [(2.25, 2.0), (2.25, 3.25), (3.0, 4.75), (7.0, 5.25), (11.0, 4.75), (13.0, 2.75), (12.0, 2.0)],
            ray=0.1, root=(7.5, 0)),
        fin("anal", [(2.25, -2.0), (2.25, -3.25), (3.0, -4.75), (7.0, -5.0), (10.5, -4.25), (12.0, -2.5), (12.0, -2.0)],
            ray=0.1, root=(7, 0)),
        fin("tail", [(1.5, -0.75), (-0.75, -2.25), (-1.75, -1.0), (-1.75, 1.0), (-0.75, 2.25), (1.5, 0.75)], root=(2.0, 0.0)),
        box("eye", "eye", 12.25, 0.75, 0.25, 13.0, 1.5, 0.75),
        box("eye_b", "eye", 11.0, 1.75, 0.5, 11.75, 2.5, 1.0),
    ]
    return f


def tuna():
    def pattern(x, y, t, face, part):
        return None
    yellow = dict(base=C("e8c030"), light=C("f8e070"), ray=C("c89a20"), edge=C("8a6a10"))
    f = Fish("tuna", "Tuna",
             [(0.3, C("eef0f2")), (0.5, C("b8c4d0")), (0.62, C("3a6ab0")), (0.8, C("1e3a6a")), (1.01, C("142848"))], -2.75, 2.75,
             dict(base=C("1e3a6a"), light=C("3a5a8a"), ray=C("142848"), edge=C("0a1428")),
             cooked="tuna_steak", title_cooked="Tuna Steak", pattern=pattern,
             gill=(11.75, 0.15, 0.85, C("142848")), eye=dict(pupil=C("101018"), shine=C("e8f0ff"), rim=C("5a6a8a")))
    f.parts = [
        zbox("body", "skin", 3, -2.75, 11.5, 2.75, 2.0),
        zbox("head", "skin", 11.5, -2.0, 14.5, 2.0, 1.5),
        zbox("snout", "skin", 14.5, -1.0, 15.5, 0.75, 1.0),
        zbox("wrist", "skin", 0.5, -0.75, 3, 0.75, 0.75),
        box("keel", "solid", 1.0, -0.25, -1.25, 2.5, 0.25, 1.25, color=C("142848"), rimmed=False),
        # A crescent tail, stiff and swept.
        fin("tail", [(1.0, -0.5), (-0.5, -2.0), (-2.5, -4.5), (-1.5, -1.75), (-1.0, 0.0), (-1.5, 1.75), (-2.5, 4.5),
                     (-0.5, 2.0), (1.0, 0.5)], root=(1.5, 0.0)),
        fin("dorsal", [(8.5, 2.5), (9.5, 4.5), (10.25, 4.25), (11.0, 2.5)], root=(9.75, 0)),
        fin("dorsal_2", [(5.5, 2.5), (6.25, 4.75), (6.75, 4.5), (7.0, 2.5)], colors=yellow, root=(6.25, 0)),
        fin("anal", [(5.5, -2.5), (6.25, -4.5), (6.75, -4.25), (7.0, -2.5)], colors=yellow, root=(6.25, 0)),
        # Yellow finlets down the back and belly toward the tail.
        fin("finlets_top", [(3.0, 2.5), (3.5, 3.25), (4.0, 2.75), (4.5, 3.25), (5.0, 2.75), (5.25, 2.5)], colors=yellow, ray=0,
            root=(4, 0)),
        fin("finlets_bottom", [(3.0, -2.5), (3.5, -3.25), (4.0, -2.75), (4.5, -3.25), (5.0, -2.75), (5.25, -2.5)],
            colors=yellow, ray=0, root=(4, 0)),
        fin("pectoral_l", [(11.0, -0.25), (7.0, -2.0), (7.5, -2.5), (11.5, -1.25)], z=-2.25, root=(11, -1)),
        fin("pectoral_r", [(11.0, -0.25), (7.0, -2.0), (7.5, -2.5), (11.5, -1.25)], z=2.25, root=(11, -1)),
        zbox("eye", "eye", 12.5, 0.25, 13.5, 1.25, 1.75),
    ]
    return f


def mahi_mahi():
    def pattern(x, y, t, face, part):
        if face in ("north", "south") and part.name in ("body", "head") and 0.2 < t < 0.75:
            if (int(math.floor(x * 2)) + int(math.floor((y + 4) * 2)) * 3) % 5 == 0:
                return C("3a8ae0")                   # bright blue flecks
        return None
    blue = dict(base=C("2a6ac8"), light=C("5a9ae8"), ray=C("1a4a9a"), edge=C("0e2a5a"))
    gold = dict(base=C("d8c030"), light=C("f0e070"), ray=C("b89a20"), edge=C("6a5a10"))
    f = Fish("mahi_mahi", "Mahi-Mahi",
             [(0.25, C("f0e8a0")), (0.5, C("d8d040")), (0.7, C("6ac060")), (1.01, C("2aa08a"))], -2.25, 3.0,
             blue, cooked="cooked_mahi_mahi", title_cooked="Cooked Mahi-Mahi", pattern=pattern,
             eye=dict(pupil=C("141414"), shine=C("f0ffe8"), rim=C("2a6a5a")))
    f.parts = [
        zbox("body", "skin", 3, -2.25, 11, 2.5, 1.0),
        # The bull's blunt, square forehead.
        zbox("head", "skin", 11, -2.0, 14, 3.0, 0.75),
        zbox("wrist", "skin", 1.0, -0.75, 3, 0.75, 0.5),
        fin("tail", [(1.5, -0.5), (-1.0, -2.5), (-2.5, -4.25), (-1.25, -1.25), (-0.75, 0.0), (-1.25, 1.25), (-2.5, 4.25),
                     (-1.0, 2.5), (1.5, 0.5)], colors=gold, root=(2.0, 0.0)),
        # One dorsal fin from the forehead to the tail.
        fin("dorsal", [(3.0, 2.0), (3.5, 3.5), (12.5, 4.5), (13.5, 3.25), (13.5, 2.5)], ray=0.1, root=(8, 0)),
        fin("anal", [(3.0, -2.0), (3.5, -3.25), (8.5, -3.25), (9.0, -2.0)], ray=0.12, root=(6, 0)),
        zbox("eye", "eye", 12.25, 0.25, 13.25, 1.25, 1.0),
    ]
    return f


def barracuda():
    def pattern(x, y, t, face, part):
        if face in ("north", "south") and part.name == "jaw_top" and y < 0.25 and int(math.floor(x * 4)) % 2 == 0:
            return C("f0f0e8")                       # fangs along the upper jaw
        if face in ("north", "south", "up") and part.name in ("body",) and t > 0.55 and (x % 1.75) < 0.6:
            return C("2a3440")                       # dark bars over the back
        if face in ("north", "south") and part.name == "body" and t < 0.35:
            if (int(math.floor(x * 2)) * 5 + int(math.floor((y + 2) * 4))) % 7 == 0:
                return C("1a1e22")                   # scattered black spots low on the flank
        return None
    f = Fish("barracuda", "Barracuda",
             [(0.3, C("e8eef2")), (0.55, C("aab8c4")), (0.8, C("6a7a8a")), (1.01, C("4a5a6a"))], -1.5, 1.5,
             dict(base=C("4a5a6a"), light=C("7a8a9a"), ray=C("2a3440"), edge=C("1a2028")),
             cooked="cooked_barracuda", title_cooked="Cooked Barracuda", pattern=pattern,
             eye=dict(pupil=C("101010"), shine=C("f0f0e0"), iris=C("c8b048")))
    f.parts = [
        zbox("body", "skin", 3, -1.5, 12, 1.5, 1.0),
        zbox("head", "skin", 12, -1.25, 15, 1.25, 0.75),
        zbox("jaw_top", "skin", 15, 0.0, 16.5, 0.75, 0.5),
        # The underbite: the lower jaw juts past the upper.
        zbox("jaw_bottom", "skin", 15, -0.75, 17.25, 0.0, 0.5),
        zbox("wrist", "skin", 1.0, -0.75, 3, 0.75, 0.5),
        fin("tail", [(1.5, -0.5), (-1.0, -2.25), (-2.0, -2.75), (-0.75, 0.0), (-2.0, 2.75), (-1.0, 2.25), (1.5, 0.5)],
            root=(2.0, 0.0)),
        fin("dorsal", [(8.0, 1.5), (8.5, 3.0), (9.5, 2.75), (10.0, 1.5)], root=(9, 0)),
        fin("dorsal_2", [(3.5, 1.5), (4.0, 2.75), (5.0, 2.5), (5.25, 1.5)], root=(4.5, 0)),
        fin("anal", [(3.5, -1.5), (4.0, -2.75), (5.0, -2.5), (5.25, -1.5)], root=(4.5, 0)),
        zbox("eye", "eye", 13.0, 0.25, 13.75, 1.0, 1.0),
    ]
    return f


def mooshroom_minnow():
    def pattern(x, y, t, face, part):
        if face in ("north", "south", "up") and part.name in ("body", "head"):
            for sx, sy in ((3.0, 0.5), (5.0, -0.25), (4.25, 0.9), (6.25, 0.5), (2.5, -0.5)):
                if math.hypot(x - sx, y - sy) < 0.4:
                    return C("f4ece4")               # a mooshroom's white blotches
        return None

    def cap(f, part, face, p, i, j, w, h):
        if face == "down":
            return C("9a1a1a")
        if (int(math.floor(p[0] * 2)) + int(math.floor(p[2] * 2)) * 2 + (0 if face == "up" else 1)) % 3 == 0:
            return C("ffffff")
        return C("e83a3a")
    f = Fish("mooshroom_minnow", "Mooshroom Minnow",
             [(0.35, C("ecd8c4")), (0.6, C("d03636")), (1.01, C("a81c1c"))], -1.25, 1.25,
             dict(base=C("b82424"), light=C("e0c8b8"), ray=C("8a1414"), edge=C("5a0c0c")),
             pattern=pattern, eye=dict(pupil=C("141414"), shine=C("ffffff")), gui_cap=1.1)
    f.mats = {"cap": cap}
    f.parts = [
        zbox("body", "skin", 2, -1.25, 6.5, 1.25, 0.85),
        zbox("head", "skin", 6.5, -1.0, 8.25, 1.0, 0.7),
        zbox("wrist", "skin", 1.0, -0.5, 2.0, 0.5, 0.4),
        fin("tail", [(1.5, -0.4), (-1.25, -1.75), (-0.5, 0.0), (-1.25, 1.75), (1.5, 0.4)], root=(2.0, 0.0)),
        # A little red mushroom growing from its back.
        box("stem", "solid", 4.0, 1.25, -0.25, 4.5, 2.25, 0.25, color=C("e8dcc8"), rimmed=False),
        box("cap", "cap", 3.25, 2.25, -1.0, 5.25, 3.25, 1.0),
        zbox("eye", "eye", 7.0, 0.0, 7.75, 0.75, 0.95),
    ]
    return f


def cometfish():
    def pattern(x, y, t, face, part):
        if part.name == "head":
            return C("fff0b0") if x > 12.25 else C("ffb84a")       # the blazing comet head
        if face in ("north", "south", "up") and part.name == "body":
            if abs(((x + 0.4 * y) % 2.5) - 1.25) < 0.25 and 0.15 < t < 0.85:
                return C("ff8a2a")                   # cracks in the crust, still glowing
        return None
    flame = dict(base=C("ff9a2a"), light=C("ffe08a"), ray=C("e85a1a"), edge=C("fff0b0"))
    f = Fish("cometfish", "Cometfish",
             [(0.3, C("2a2436")), (0.7, C("3a3448")), (1.01, C("4a4260"))], -2.0, 2.0,
             flame, pattern=pattern, eye=dict(pupil=C("3a1a08"), shine=C("fff6c8"), rim=C("e85a1a")))
    f.parts = [
        zbox("head", "skin", 10, -1.75, 13.5, 1.75, 1.25),
        zbox("body", "skin", 3, -2.0, 10, 2.0, 1.5),
        zbox("wrist", "skin", 1.0, -0.75, 3, 0.75, 0.75),
        # The trail: long streaming flames for a tail.
        fin("tail", [(1.5, -0.5), (-3.0, -2.5), (-2.0, -1.0), (-5.5, 0.0), (-2.0, 1.0), (-3.0, 2.5), (1.5, 0.5)],
            root=(2.0, 0.0), emit=True, ray=0.3),
        fin("dorsal", [(4.0, 2.0), (5.0, 3.5), (6.0, 2.75), (7.0, 3.75), (8.5, 2.0)], emit=True, ray=0, root=(6, 0)),
        zbox("eye", "eye", 11.5, 0.25, 12.25, 1.0, 1.5),
    ]
    return f


FISH = [brook_trout, mud_catfish, pike, mackerel, swordfish, anglerfish, glowfin_tetra, parrotfish, ice_char, bog_eel,
        cave_blindfish, sculk_gulper, pixie_minnow, moonscale_koi, magma_eel, ember_puffer, glowing_mutant_carp,
        starlit_gar, abyssal_squidling, ghoulfish, largemouth_bass, bluegill, yellow_perch, walleye, crappie, sturgeon,
        red_snapper, flounder, tuna, mahi_mahi, barracuda, mooshroom_minnow, cometfish]


# ============================================================================================================= rigs
#
# length: the fish's whole length in the world, in pixels (vanilla's cod is 15, a medium salmon 25, a large one 37 and a
#   small tropical fish about 7).
# groups: (name, parent, pivot in old pixels, parts) - everything not listed stays on the body. The model animates
#   parts by name: tail, tail2, tail3 (a chain that wags), whisker_*, lure, wing_*, tentacle_*.
# cuts: fins split at these x positions (pieces <fin>_0, _1, ... from the tail forward), for fins running over a joint.
# glow: skin and trim colours that also glow, besides the parts the old model already lit.
# bucket: which vanilla fish bucket's fish is recoloured for its bucket sprite.

RIG = {
    "brook_trout": dict(length=15, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "mud_catfish": dict(length=18, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"]),
                                           ("whisker_left", "body", (14.0, 0.5, -2.0), ["whisker_l"]),
                                           ("whisker_right", "body", (14.0, 0.5, 2.0), ["whisker_r"])]),
    "pike": dict(length=20, bucket="salmon", groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"])]),
    "mackerel": dict(length=13, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "swordfish": dict(length=38, bucket="salmon", groups=[("tail", "body", (3, 0, 0), ["wrist", "keel", "tail"])]),
    "anglerfish": dict(length=13, bucket="pufferfish", groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"]),
                                                              ("lure", "body", (11.0, 3.0, 0.0), ["stalk", "lure"])]),
    "glowfin_tetra": dict(length=7, bucket="tropical_fish", groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"])]),
    "parrotfish": dict(length=15, bucket="tropical_fish", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "ice_char": dict(length=15, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "bog_eel": dict(length=22, bucket="salmon", cuts={"skirt": [3.5, 7.0, 11.0]},
                    groups=[("tail", "body", (11.0, 0, 0), ["seg2", "skirt_2"]),
                            ("tail2", "tail", (7.0, 0, 0), ["seg3", "skirt_1"]),
                            ("tail3", "tail2", (3.5, 0, 0), ["seg4", "skirt_0"])]),
    "cave_blindfish": dict(length=9, groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"])]),
    "sculk_gulper": dict(length=15, bucket="pufferfish", glow=["29dfeb"],
                         groups=[("tail", "body", (6, 0, 0), ["body"]),
                                 ("tail2", "tail", (2, 0, 0), ["tail_stock", "whip"])]),
    "pixie_minnow": dict(length=7, bucket="tropical_fish",
                         groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"]),
                                 ("wing_left", "body", (4.5, 1.0, -0.5), ["wing_l"]),
                                 ("wing_right", "body", (4.0, 0.75, 0.5), ["wing_r"])]),
    "moonscale_koi": dict(length=17, bucket="tropical_fish", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "magma_eel": dict(length=22, bucket="salmon", glow=["ff9a2e", "e2531a"], cuts={"flame": [11.0]},
                      groups=[("tail", "body", (11.0, 0, 0), ["joint2", "seg2", "flame_0"]),
                              ("tail2", "tail", (6.5, 0, 0), ["joint3", "seg3"]),
                              ("tail3", "tail2", (3.0, 0, 0), ["tail"])]),
    "ember_puffer": dict(length=9, bucket="pufferfish", glow=["ff8a2a"],
                         groups=[("tail", "body", (2.25, 0, 0.6), ["tail"])]),
    "glowing_mutant_carp": dict(length=16, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "starlit_gar": dict(length=24, bucket="salmon", glow=["fff6c8"], groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"])]),
    "abyssal_squidling": dict(length=16, bucket="pufferfish", glow=["ff5ad8", "4af0ff"],
                              groups=[("tentacle_0", "body", (3.0, 0.75, -1.0), ["arm1"]),
                                      ("tentacle_1", "body", (3.0, 1.0, 1.0), ["arm2"]),
                                      ("tentacle_2", "body", (3.0, -1.0, -0.75), ["arm3"]),
                                      ("tentacle_3", "body", (3.0, -0.75, 0.75), ["arm4"]),
                                      ("tentacle_4", "body", (3.0, 0.0, 0.0), ["tentacle_a"]),
                                      ("tentacle_5", "body", (3.0, -0.15, -0.15), ["tentacle_b"])]),
    "ghoulfish": dict(length=15, groups=[("tail", "body", (6, 0, 0), ["spine", "ribs_l", "ribs_r"]),
                                         ("tail2", "tail", (1.0, 0, 0), ["tail"])]),
    # The second catch.
    "largemouth_bass": dict(length=17, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "bluegill": dict(length=10, bucket="tropical_fish", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "yellow_perch": dict(length=12, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "walleye": dict(length=18, bucket="salmon", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "crappie": dict(length=11, bucket="tropical_fish", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "sturgeon": dict(length=40, bucket="salmon", groups=[("tail", "body", (3, 0, 0), ["tail_stock", "tail"])]),
    "red_snapper": dict(length=18, groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    # Drawn lying on its side (FishModelLayers.Shape.flat): the tail's wag becomes the up-and-down beat of a flatfish.
    "flounder": dict(length=16, flat=True, groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"])]),
    "tuna": dict(length=36, bucket="salmon", groups=[("tail", "body", (3, 0, 0), ["wrist", "keel", "tail"])]),
    "mahi_mahi": dict(length=30, bucket="tropical_fish", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "barracuda": dict(length=28, bucket="salmon", groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
    "mooshroom_minnow": dict(length=7, bucket="tropical_fish", groups=[("tail", "body", (2, 0, 0), ["wrist", "tail"])]),
    "cometfish": dict(length=16, glow=["fff0b0", "ffb84a", "ff8a2a"], groups=[("tail", "body", (3, 0, 0), ["wrist", "tail"])]),
}

# The fish swimming in lava: their buckets hold lava.
LAVA = {"magma_eel", "ember_puffer"}


# ======================================================================================================= building

def split_fins(f, cuts):
    """Fins running over a joint become one piece per group, each painted as part of the whole fin."""
    out = []
    for p in f.parts:
        xs = cuts.get(p.name)
        if not xs:
            out.append(p)
            continue
        rx_, ry_ = p.kw["root"]
        p.kw["reach"] = max(math.hypot(px - rx_, py - ry_) for px, py in p.poly)
        bounds = [p.lo[0]] + list(xs) + [p.hi[0]]
        for k in range(len(bounds) - 1):
            out.append(Part("%s_%d" % (p.name, k), p.mat, (bounds[k], p.lo[1], p.lo[2]), (bounds[k + 1], p.hi[1], p.hi[2]),
                            p.faces, p.rot, p.emit, poly=p.poly, axis=p.axis, **p.kw))
    f.parts = out


def coplanar_with(p, a, lo, hi, others):
    """Whether box p, resized to lo..hi on axis a, would put a face in the same plane as a face of another part that
    looks the same way (or any fin there, fins being seen from both sides), overlapping it."""
    rest = [b for b in range(3) if b != a]
    for q in others:
        if q is p:
            continue
        if q.poly:
            if (2 if q.axis == "z" else 0) != a:
                continue
            planes = [(q.lo[a], (lo, hi))]
            pairs = [(lo, q.lo[a]), (hi, q.lo[a])]
        else:
            pairs = [(lo, q.lo[a]), (hi, q.hi[a])]
        if not any(abs(x - y) < 1e-6 for x, y in pairs):
            continue
        if all(min(p.hi[b], q.hi[b]) - max(p.lo[b], q.lo[b]) > 1e-6 for b in rest):
            return True
    return False


def quantize(f):
    """Box sizes to whole entity units (half pixels), keeping each box's centre; fin planes grow out to the grid.
    A box is rounded to the nearer whole size unless that puts one of its faces flush with another's, in which case
    it takes the other one."""
    for p in f.parts:
        if p.poly:
            for a in ((0, 1) if p.axis == "z" else (2, 1)):
                p.lo[a] = math.floor(p.lo[a] * D + 1e-6) / D
                p.hi[a] = math.ceil(p.hi[a] * D - 1e-6) / D
    for p in f.parts:
        if p.poly:
            continue
        for a in range(3):
            size = p.hi[a] - p.lo[a]
            if abs(round(size * D) - size * D) < 1e-6:
                continue
            c = (p.lo[a] + p.hi[a]) / 2
            down, up = max(1, math.floor(size * D)), math.ceil(size * D)
            options = [down, up] if size * D - down <= up - size * D else [up, down]
            for units in options:
                lo, hi = c - units / D / 2, c + units / D / 2
                if not coplanar_with(p, a, lo, hi, f.parts):
                    break
            p.lo[a], p.hi[a] = lo, hi


def separate_fins(f):
    """Fin planes sharing a depth whose rectangles overlap are stepped apart by a hundredth of a pixel, so their
    (mostly transparent) quads aren't coplanar; and no two of them may have opaque texels on top of each other."""
    planes = [p for p in f.parts if p.poly]
    for k, a in enumerate(planes):
        for b in planes[:k]:
            axis = 2 if a.axis == "z" else 0
            if a.axis != b.axis or abs(a.lo[axis] - b.lo[axis]) > 0.005:
                continue
            ia = (0, 1) if a.axis == "z" else (2, 1)
            if all(min(a.hi[i], b.hi[i]) > max(a.lo[i], b.lo[i]) for i in ia):
                if a.poly is not b.poly:
                    n = 0.25 / D
                    lo0, hi0 = [max(a.lo[i], b.lo[i]) for i in ia], [min(a.hi[i], b.hi[i]) for i in ia]
                    y = lo0[1] + n / 2
                    while y < hi0[1]:
                        x = lo0[0] + n / 2
                        while x < hi0[0]:
                            if inside_poly((x, y), a.poly) and inside_poly((x, y), b.poly):
                                raise SystemExit(f"{f.name}: fins {a.name} and {b.name} overlap at {(x, y)}")
                            x += n
                        y += n
                a.lo[axis] += 0.01 * (k + 1)
                a.hi[axis] += 0.01 * (k + 1)


# Old model space (x tail->head, y up, z across, pixels) to entity model space (y down, head toward -z, D units a pixel).
def to_model(v):
    return (-D * v[2], -D * v[1], -D * v[0])


def to_old(v):
    return (-v[2] / D, -v[1] / D, -v[0] / D)


# Old face names to the entity cube's faces (named as Minecraft's Direction for the cube's sides: DOWN is min y).
OLD_TO_MODEL = {"up": "DOWN", "down": "UP", "south": "WEST", "north": "EAST", "east": "NORTH", "west": "SOUTH"}
MODEL_TO_OLD = {v: k for k, v in OLD_TO_MODEL.items()}


def mat_mul(p, q):
    return [[sum(p[i][k] * q[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def euler_zyx(m):
    """(x, y, z) radians with m = Rz(z) Ry(y) Rx(x): ModelPart's rotation order."""
    b = -math.asin(max(-1.0, min(1.0, m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    return (a, b, c)


def rot_xyz(a, b, c):
    """The matrix ModelPart builds from xRot, yRot, zRot (radians)."""
    d = math.degrees
    return mat_mul(rz(d(c)), mat_mul(ry(d(b)), rx(d(a))))


class Node:
    """A ModelPart: its pivot (old pixels), parent, rest rotation (radians, model axes) and cubes."""

    def __init__(self, name, parent, anchor, rot=(0.0, 0.0, 0.0)):
        self.name, self.parent, self.anchor, self.rot = name, parent, tuple(anchor), rot
        self.cubes, self.children = [], []


class Cube:
    def __init__(self, part, origin, size, faces):
        self.part, self.origin, self.size, self.faces = part, origin, size, faces   # model units, local to the node
        self.tex = (0, 0)


def face_rect(cube, face):
    """(u, v, width, height) of a face in vanilla's box UV layout, from the cube's texture offset."""
    u, v = cube.tex
    w, h, d = cube.size
    return {"DOWN": (u + d, v, w, d), "UP": (u + d + w, v, w, d), "WEST": (u, v + d, d, h), "NORTH": (u + d, v + d, w, h),
            "EAST": (u + d + w, v + d, d, h), "SOUTH": (u + 2 * d + w, v + d, w, h)}[face]


def face_point(cube, face, s, t):
    """The cube-local point at (s, t) across a face's texture rect (0..1 from its top left), as ModelPart maps it."""
    x0, y0, z0 = cube.origin
    w, h, d = cube.size
    if face == "DOWN":
        return (x0 + s * w, y0, z0 + d - t * d)
    if face == "UP":
        return (x0 + s * w, y0 + h, z0 + d - t * d)
    if face == "WEST":
        return (x0, y0 + t * h, z0 + d - s * d)
    if face == "NORTH":
        return (x0 + s * w, y0 + t * h, z0)
    if face == "EAST":
        return (x0 + w, y0 + t * h, z0 + s * d)
    return (x0 + w - s * w, y0 + t * h, z0 + d)


def used_rect(cube):
    """The patch of texture a cube draws from (its visible faces), and where its texture offset sits relative to it."""
    w, h, d = cube.size
    if cube.faces == ["WEST"]:
        return (d, h), (0, -d)
    if cube.faces == ["NORTH"]:
        return (w, h), (0, 0)
    return (2 * d + 2 * w, d + h), (0, 0)


def pack(cubes):
    """Shelf-packs the cubes' texture patches; returns the (square) texture size."""
    rects = [used_rect(c)[0] for c in cubes]
    order = sorted(range(len(cubes)), key=lambda k: (-rects[k][1], -rects[k][0]))
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
            for k, c in enumerate(cubes):
                off = used_rect(c)[1]
                c.tex = (pos[k][0] + off[0], pos[k][1] + off[1])
            return size
    raise SystemExit("texture too big")


class Rig:
    """A fish built as entity model parts: nodes (root first), cubes, texture and glow texture, and its numbers."""

    def __init__(self, f):
        self.f = f
        rig = RIG[f.name]
        split_fins(f, rig.get("cuts", {}))
        quantize(f)
        separate_fins(f)
        lo = [min(p.lo[a] for p in f.parts) for a in range(3)]
        hi = [max(p.hi[a] for p in f.parts) for a in range(3)]
        self.old_length = hi[0] - lo[0]
        self.k = rig["length"] / self.old_length                  # world pixels per old pixel
        self.scale = round(self.k / D, 4)                          # the layer's MeshTransformer scaling
        # The body's pivot: halfway along the fish, on its centre line; its lowest point on the ground (y = 24).
        self.body_anchor = ((lo[0] + hi[0]) / 2, 0.0, 0.0)
        self.body_y = 24.0 + D * lo[1]
        self.height = (hi[1] - lo[1]) * self.k / 16.0               # blocks
        self.half_width = max(abs(lo[2]), abs(hi[2])) * self.k / 16.0
        self.length = self.old_length * self.k / 16.0
        body = Node("body", None, self.body_anchor)
        self.nodes = {"body": body}
        member = {}
        for name, parent, pivot, parts in rig.get("groups", []):
            node = Node(name, self.nodes[parent], pivot)
            self.nodes[parent].children.append(node)
            self.nodes[name] = node
            for p in parts:
                member[p] = name
        names = {p.name for p in f.parts}
        for p in member:
            if p not in names:
                raise SystemExit(f"{f.name}: rig names a missing part {p}")
        self.cubes = []
        for p in f.parts:
            node = self.nodes[member.get(p.name, "body")]
            if p.rot:
                axis, angle, origin = p.rot
                m_old = {"x": rx, "y": ry, "z": rz}[axis](angle)
                # The same rotation in model axes: P m P^-1, P the (orthonormal) axis map.
                P = [[0, 0, -1], [0, -1, 0], [-1, 0, 0]]
                child = Node(p.name, node, origin, euler_zyx(mat_mul(P, mat_mul(m_old, P))))
                node.children.append(child)
                self.nodes[p.name] = child
                node = child
            a = to_model([p.lo[i] - node.anchor[i] for i in range(3)])
            b = to_model([p.hi[i] - node.anchor[i] for i in range(3)])
            origin = tuple(round(min(a[i], b[i]), 4) for i in range(3))
            size = tuple(int(round(abs(a[i] - b[i]))) for i in range(3))
            for i in range(3):
                if abs(abs(a[i] - b[i]) - size[i]) > 1e-6:
                    raise SystemExit(f"{f.name}: {p.name} isn't a whole number of units")
            if p.poly:
                faces = ["WEST"] if p.axis == "z" else ["NORTH"]
            else:
                faces = [OLD_TO_MODEL[o] for o in FACES if o in p.faces]
                faces = [x for x in ("DOWN", "UP", "WEST", "NORTH", "EAST", "SOUTH") if x in faces]
            cube = Cube(p, origin, size, faces)
            node.cubes.append(cube)
            self.cubes.append((node, cube))
        self.size = pack([c for _, c in self.cubes])
        self.paint()

    def offset(self, node):
        """A node's pivot relative to its parent's, in model units (the body's is absolute)."""
        if node.parent is None:
            return (0.0, self.body_y, 0.0)
        return to_model([node.anchor[i] - node.parent.anchor[i] for i in range(3)])

    def paint(self):
        f, n = self.f, self.size
        glow_cols = {C(h)[:3] for h in RIG[f.name].get("glow", [])}
        self.tex = [(0, 0, 0, 0)] * (n * n)
        self.glow = [(0, 0, 0, 0)] * (n * n)
        for node, cube in self.cubes:
            part = cube.part
            fn = f.mats.get(part.mat) or PAINTERS[part.mat]
            for face in cube.faces:
                u, v, w, h = face_rect(cube, face)
                old_face = MODEL_TO_OLD[face]
                for j in range(h):
                    for i in range(w):
                        q = face_point(cube, face, (i + 0.5) / w, (j + 0.5) / h)
                        p = to_old(q)
                        p = tuple(p[k] + node.anchor[k] for k in range(3))
                        c = fn(f, part, old_face, p, i, j, w, h)
                        if c is None:
                            continue
                        k = (v + j) * n + u + i
                        if not (0 <= v + j < n and 0 <= u + i < n):
                            raise SystemExit(f"{f.name}: {part.name} {face} off the texture")
                        self.tex[k] = c
                        if part.emit or (part.mat in ("skin", "solid") and c[:3] in glow_cols):
                            self.glow[k] = c
        self.glows = any(c[3] for c in self.glow)

    # ------------------------------------------------------------------------------------------------- posing

    def world_quads(self, pose=None):
        """Every drawn face: (node, cube, face, corners as model points), posed with pose {node: (dx, dy, dz)}."""
        pose = pose or {}
        out = []

        def walk(node, m, o):
            off = self.offset(node)
            o2 = tuple(o[i] + apply(m, off)[i] for i in range(3))
            r = node.rot
            extra = pose.get(node.name, (0.0, 0.0, 0.0))
            m2 = mat_mul(m, rot_xyz(r[0] + extra[0], r[1] + extra[1], r[2] + extra[2]))
            for cube in node.cubes:
                for face in cube.faces:
                    pts = [face_point(cube, face, s, t) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
                    pts = [tuple(o2[i] + apply(m2, p)[i] for i in range(3)) for p in pts]
                    out.append((node, cube, face, pts, m2))
            for ch in node.children:
                walk(ch, m2, o2)

        walk(self.nodes["body"], [[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))
        return out

    def check(self):
        normals = {"DOWN": (0, -1, 0), "UP": (0, 1, 0), "WEST": (-1, 0, 0), "EAST": (1, 0, 0), "NORTH": (0, 0, -1),
                   "SOUTH": (0, 0, 1)}
        faces = []
        for node, cube, face, pts, m in self.world_quads():
            n = apply(m, normals[face])
            label = f"{node.name}.{cube.part.name}.{face}"
            faces.append(Face(label, pts, n))
            if len(cube.faces) == 1:
                # A fin is drawn from both sides, so it can fight faces looking either way.
                faces.append(Face(label + "(back)", pts, tuple(-x for x in n)))
        hits = fights(faces)
        if hits:
            for h in hits[:20]:
                print("z-fight:", self.f.name, h)
            raise SystemExit(f"{self.f.name}: {len(hits)} coplanar overlapping face pairs")


# ============================================================================================================ java


def jf(v):
    v = round(v, 4) + 0.0                             # + 0.0: no "-0.0F"
    if v == int(v):
        return "%.1fF" % v
    return ("%.4f" % v).rstrip("0") + "F"


def camel(name):
    head, *rest = name.split("_")
    return head + "".join(w.capitalize() for w in rest)


def java_layer(rig):
    f = rig.f
    out = [f"\tprivate static LayerDefinition {camel(f.name)}() {{",
           "\t\tMeshDefinition mesh = new MeshDefinition();",
           "\t\tPartDefinition root = mesh.getRoot();"]
    var = {}

    def emit(node, parent_var):
        v = "p%d" % len(var)
        var[node.name] = v
        lines = ["CubeListBuilder.create()"]
        for cube in node.cubes:
            x, y, z = cube.origin
            w, h, d = cube.size
            faces = "" if len(cube.faces) == 6 else ", " + "_".join(cube.faces)
            lines.append(f".texOffs({cube.tex[0]}, {cube.tex[1]}).addBox({jf(x)}, {jf(y)}, {jf(z)}, {jf(w)}, {jf(h)}, "
                         f"{jf(d)}{faces})")
        off = rig.offset(node)
        if any(abs(r) > 1e-6 for r in node.rot):
            pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(jf(c) for c in off + tuple(node.rot))
        else:
            pose = "PartPose.offset(%s, %s, %s)" % tuple(jf(c) for c in off)
        decl = f"PartDefinition {v} = " if node.children else ""
        body = ("\n\t\t\t\t" + "\n\t\t\t\t".join(lines[1:])) if len(lines) > 1 else ""
        out.append(f"\t\t{decl}{parent_var}.addOrReplaceChild(\"{node.name}\", {lines[0]}{body},\n\t\t\t\t{pose});")
        for ch in node.children:
            emit(ch, v)

    emit(rig.nodes["body"], "root")
    out.append(f"\t\treturn LayerDefinition.create(mesh, {rig.size}, {rig.size}).apply(MeshTransformer.scaling({jf(rig.scale)}));")
    out.append("\t}")
    return "\n".join(out)


def java_file(rigs):
    shapes = []
    for r in rigs:
        shapes.append(f"\t\t\tnew Shape(\"{r.f.name}\", FishModelLayers::{camel(r.f.name)}, {str(r.glows).lower()}, "
                      f"{jf(r.height)}, {jf(r.half_width)}, {jf(max(0.15, min(0.8, 0.3 * r.length)))}, "
                      f"{str(RIG[r.f.name].get('flat', False)).lower()})")
    return f"""package com.jheinz.mymod.client.fishing.fish;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;

/**
 * The living fish's models: each one's cubes (rebuilt from its old 3D item model), its layer's scale, and the numbers
 * its renderer needs. Generated by tools/gen_fish_entities.py, which also paints the textures to match: change the
 * shapes there, not here.
 */
public final class FishModelLayers {{
	/**
	 * A fish's model: its layer, whether it has a glow texture, its height and half its width in blocks (to lay it on
	 * its side when it flops on land), its shadow's radius, and whether it's a flatfish that always lies on its side.
	 */
	public record Shape(String id, Supplier<LayerDefinition> layer, boolean glows, float height, float halfWidth, float shadow,
			boolean flat) {{
	}}

	/** Fins are single faces, drawn from both sides by the entity cutout render type. */
	private static final Set<Direction> WEST = EnumSet.of(Direction.WEST);
	private static final Set<Direction> NORTH = EnumSet.of(Direction.NORTH);

	public static final List<Shape> SHAPES = List.of(
{(","+chr(10)).join(shapes)});

	private FishModelLayers() {{
	}}

{(chr(10) + chr(10)).join(java_layer(r) for r in rigs)}
}}
"""


# ================================================================================================= item sprites

BUCKET_GREYS = {C(h)[:3] for h in ("353535", "d8d8d8", "a8a8a8", "727272", "969696", "ffffff", "787878", "989898")}
WATER = {C(h)[:3] for h in ("234fcc", "446fe9", "345fda", "2e58d3", "5a82f3")}
EGG_WATER = [C("2f457a"), C("355a8b"), C("3e76a3"), C("4b98c4"), C("63c9ff")]
EGG_LAVA = [C("7f3e2c"), C("a3402a"), C("cc4628"), C("e38c3f"), C("e4d25c")]


def fish_ramp(f):
    """The fish's colours dark to light: fin edge, back to belly bands, light fin."""
    cols = [c for _, c in f.bands] + [f.fins["base"], f.fins["edge"]]
    cols = sorted({c[:3]: c for c in cols}.values(), key=lum)
    return cols


def recolour(px, keep, ramp):
    """Pixels not in keep (and not black) mapped by brightness rank onto ramp."""
    src = sorted({c[:3] for c in px if c[3] and c[:3] not in keep and c[:3] != (0, 0, 0)}, key=lambda c: lum(c + (255,)))
    mapping = {}
    for k, c in enumerate(src):
        t = k / max(1, len(src) - 1)
        mapping[c] = ramp[min(len(ramp) - 1, int(round(t * (len(ramp) - 1))))]
    return [(mapping[c[:3]][:3] + (255,)) if c[3] and c[:3] in mapping else c for c in px]


def bucket_sprite(f, vanilla):
    """Vanilla's fish bucket with its fish recoloured to this one; lava fish in a lava bucket instead."""
    template = vanilla("%s_bucket" % RIG[f.name].get("bucket", "cod"))
    fish = recolour(template, BUCKET_GREYS | WATER, fish_ramp(f))
    if f.name not in LAVA:
        return fish
    water = vanilla("water_bucket")
    lava = vanilla("lava_bucket")
    out = list(lava)
    for k, c in enumerate(template):
        if c[3] and c[:3] not in BUCKET_GREYS and c[:3] not in WATER and c != water[k]:
            out[k] = fish[k]
    return out


def egg_sprite(f, vanilla):
    """Vanilla's cod spawn egg, its shell in the fish's colours (and its water lava for the lava fish)."""
    px = vanilla("cod_spawn_egg")
    water = {c[:3] for c in EGG_WATER}
    out = recolour(px, water, fish_ramp(f))
    if f.name in LAVA:
        order = [c[:3] for c in EGG_WATER]
        out = [(EGG_LAVA[order.index(c[:3])][:3] + (255,)) if c[3] and c[:3] in water else c for c in out]
    return out


# ======================================================================================================== data


def loot_table(name):
    smelt = OrderedDict([("type", "minecraft:furnace_smelt"), ("condition", OrderedDict([
        ("type", "minecraft:any_of"), ("terms", [
            OrderedDict([("type", "minecraft:entity_properties"), ("entity", "this"),
                         ("predicate", {"minecraft:flags": {"is_on_fire": True}})]),
            OrderedDict([("type", "minecraft:entity_properties"), ("entity", "direct_attacker"),
                         ("predicate", {"minecraft:equipment": {"mainhand": {"predicates": {
                             "minecraft:enchantments": [{"enchantments": "#minecraft:smelts_loot"}]}}}})])])]))])
    return OrderedDict([
        ("type", "minecraft:entity"),
        ("pools", [
            OrderedDict([("entries", [OrderedDict([("type", "minecraft:item"), ("modifier", smelt), ("name", "mymod:" + name)])]),
                         ("rolls", 1)]),
            OrderedDict([("condition", OrderedDict([("type", "minecraft:random_chance"), ("chance", 0.05)])),
                         ("entries", [OrderedDict([("type", "minecraft:item"), ("name", "minecraft:bone_meal")])]),
                         ("rolls", 1)])]),
        ("random_sequence", "mymod:entities/" + name)])


def item_files(name, texture):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", OrderedDict(layer0="mymod:item/fish/" + texture))]))
    write(os.path.join(ITEMS, name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + name)])})


def tag_add(path, values):
    obj = OrderedDict([("replace", False), ("values", [])])
    if os.path.exists(path):
        with open(path) as fh:
            obj = json.load(fh, object_pairs_hook=OrderedDict)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(path, obj)


def merge_lang(entries):
    with open(LANG) as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(LANG, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


# ======================================================================================================= preview
#
# Orthographic, every texel drawn as its own little quad (like the game samples it), lit from above the way entities
# are, glow texels full bright. Everything at one scale, so the fish keep their sizes relative to vanilla's cod.

PX = 5.0            # preview pixels per world pixel (1/16 block)


def wag(rig, phase):
    """The model's swimming pose at sin(0.6 t) = phase (mirrors FishEntityModel.setupAnim)."""
    pose = {}
    chain = [n for n in ("tail", "tail2", "tail3") if n in rig.nodes]
    eel = len(chain) > 1
    for k, n in enumerate(chain):
        amp = (0.3 + 0.08 * k) if eel else 0.45
        lag = math.sin(math.asin(max(-1, min(1, phase))) - 0.8 * k)
        pose[n] = (0.0, -amp * lag, 0.0)
    return pose


def render_quads(quads, sample, view, w, h, cx, cy, scale):
    img = [None] * (w * h)
    depth = [1e9] * (w * h)
    light = (0.2, -1.0, -0.5)   # model space: y down, so this is from above
    ln = math.sqrt(sum(c * c for c in light))
    for pts, uvrect, glow_k in quads:
        A, B, Cc, Dd = pts
        e1 = [B[i] - A[i] for i in range(3)]
        e2 = [Dd[i] - A[i] for i in range(3)]
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.55 + 0.45 * abs(sum(n[i] * light[i] for i in range(3))) / (nl * ln)
        u, v, fw, fh = uvrect
        for j in range(fh):
            for i in range(fw):
                col, glowing = sample(u + i, v + j)
                if col is None:
                    continue
                corners = []
                for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a / fw, b / fh
                    p = [A[k] + e1[k] * s + e2[k] * t for k in range(3)]
                    q = apply(view, p)
                    corners.append((cx + q[0] * scale, cy + q[1] * scale, q[2]))
                z = sum(c[2] for c in corners) / 4
                k_ = 1.0 if glowing else lit
                fill(img, depth, w, h, corners, z, tuple(int(col[c] * k_) for c in range(3)))
    return img


def fill(img, depth, w, h, corners, z, colour):
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    for py in range(max(0, int(min(ys))), min(h, int(math.ceil(max(ys))) + 1)):
        for px in range(max(0, int(min(xs))), min(w, int(math.ceil(max(xs))) + 1)):
            if inside4(corners, px + 0.5, py + 0.5) and z < depth[py * w + px]:
                depth[py * w + px] = z
                img[py * w + px] = colour


def inside4(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cr = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cr) < 1e-9:
            continue
        s = 1 if cr > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return sign != 0


# Views (model space, y down): from the fish's left flank (+x), head to the left; and three-quarters from the front.
SIDE = ry(90)
THREE = mat_mul(rx(25), ry(130))
ABOVE = mat_mul(rx(60), ry(90))                 # steeply from above, to see the wag


def rig_quads(rig, pose, scale):
    n = rig.size
    out = []
    for node, cube, face, pts, _ in rig.world_quads(pose):
        pts = [tuple(c * scale for c in (p[0], p[1] - 24.0, p[2])) for p in pts]
        out.append((pts, face_rect(cube, face), None))

    def sample(x, y):
        if not (0 <= x < n and 0 <= y < n):
            return None, False
        c = rig.tex[y * n + x]
        if c[3] < 128:
            return None, False
        g = rig.glow[y * n + x]
        return c, bool(g[3])
    return out, sample


def cod_quads(png_px, tw):
    """Vanilla's cod (CodModel), to check the UV mapping against a texture we know and for scale."""
    cubes = [((0, 22, 0), (0, 0), (-1, -2, 0), (2, 4, 7), None), ((0, 22, 0), (11, 0), (-1, -2, -3), (2, 4, 3), None),
             ((0, 22, -3), (0, 0), (-1, -2, -1), (2, 3, 1), None),
             ((0, 22, 7), (22, 3), (0, -2, 0), (0, 4, 4), None), ((0, 20, 0), (20, -6), (0, -1, -1), (0, 1, 6), None)]
    out = []
    for off, tex, origin, size, _ in cubes:
        c = Cube(None, origin, size, ["DOWN", "UP", "WEST", "NORTH", "EAST", "SOUTH"])
        c.tex = tex
        for face in c.faces:
            u, v, fw, fh = face_rect(c, face)
            if fw == 0 or fh == 0:
                continue
            pts = [face_point(c, face, s, t) for s, t in ((0, 0), (1, 0), (1, 1), (0, 1))]
            pts = [(p[0] + off[0], p[1] + off[1] - 24.0, p[2] + off[2]) for p in pts]
            out.append((pts, (u, v, fw, fh), None))

    def sample(x, y):
        if not (0 <= x < tw and 0 <= y < tw):
            return None, False
        c = png_px[y * tw + x]
        return (c, False) if c[3] >= 128 else (None, False)
    return out, sample


def preview(path, rigs, vanilla_entity):
    """A row per fish (vanilla's cod first): side, three-quarters, then its wag from above at both ends of the swing.
    One scale throughout, with a one-block ruler at the top."""
    tw, _, cod_px = vanilla_entity("fish/cod")
    cod = cod_quads(cod_px, tw)
    rows = [(cod, cod, cod)] + [(rig_quads(r, {}, r.scale), rig_quads(r, wag(r, 1.0), r.scale),
                                 rig_quads(r, wag(r, -1.0), r.scale)) for r in rigs]
    cells = [[(rest, SIDE), (rest, THREE), (wag_a, ABOVE), (wag_b, ABOVE)] for rest, wag_a, wag_b in rows]
    W = H = 0
    for row in cells:
        for (quads, _), vm in row:
            pts = [apply(vm, p) for q in quads for p in q[0]]
            W = max(W, (max(p[0] for p in pts) - min(p[0] for p in pts)) * PX)
            H = max(H, (max(p[1] for p in pts) - min(p[1] for p in pts)) * PX)
    W, H, gap = int(W) + 10, int(H) + 10, 6
    per_row = 2                                      # fish side by side
    sheet_w = per_row * (4 * (W + gap) + gap)
    sheet_h = 24 + (len(cells) + per_row - 1) // per_row * (H + gap) + gap
    out = [(46, 50, 58, 255)] * (sheet_w * sheet_h)
    for x in range(gap, gap + int(16 * PX)):
        for y in range(8, 12):
            out[y * sheet_w + x] = (220, 220, 220, 255)
    for n, row in enumerate(cells):
        oy = 24 + n // per_row * (H + gap)
        for col, ((quads, sample), vm) in enumerate(row):
            pts = [apply(vm, p) for q in quads for p in q[0]]
            cx = W / 2 - (max(p[0] for p in pts) + min(p[0] for p in pts)) / 2 * PX
            cy = H / 2 - (max(p[1] for p in pts) + min(p[1] for p in pts)) / 2 * PX
            img = render_quads(quads, sample, vm, W, H, cx, cy, PX)
            ox = (n % per_row) * (4 * (W + gap) + gap) + gap + col * (W + gap)
            for y in range(H):
                for x in range(W):
                    c = img[y * W + x]
                    out[(oy + y) * sheet_w + ox + x] = (c + (255,)) if c else (58, 62, 72, 255)
    png(path, sheet_w, sheet_h, out)
    print("preview:", path)


# ========================================================================================================== main


def main():
    preview_path = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else None
    del sys.argv[1:]                                  # gen_longswords.client_jar() reads argv[2] as a jar path
    from gen_longswords import client_jar, read_png
    jar = zipfile.ZipFile(client_jar())

    def vanilla(n):
        return read_png(jar.read("assets/minecraft/textures/item/%s.png" % n))[2]

    def vanilla_entity(n):
        return read_png(jar.read("assets/minecraft/textures/entity/%s.png" % n))

    rigs = []
    lang = OrderedDict()
    for make in FISH:
        f = make()
        rig = Rig(f)
        rig.check()
        rigs.append(rig)
        n = rig.size
        png(os.path.join(ENTITY_TEXTURES, f.name + ".png"), n, n, rig.tex)
        glow_path = os.path.join(ENTITY_TEXTURES, f.name + "_glow.png")
        if rig.glows:
            png(glow_path, n, n, rig.glow)
        elif os.path.exists(glow_path):
            os.remove(glow_path)
        png(os.path.join(ITEM_TEXTURES, f.name + "_bucket.png"), 16, 16, bucket_sprite(f, vanilla))
        png(os.path.join(ITEM_TEXTURES, f.name + "_spawn_egg.png"), 16, 16, egg_sprite(f, vanilla))
        item_files(f.name + "_bucket", f.name + "_bucket")
        item_files(f.name + "_spawn_egg", f.name + "_spawn_egg")
        write(os.path.join(DATA, "loot_table", "entities", f.name + ".json"), loot_table(f.name))
        lang["entity.mymod." + f.name] = f.title
        lang["item.mymod.%s_bucket" % f.name] = "Bucket of " + f.title
        lang["item.mymod.%s_spawn_egg" % f.name] = f.title + " Spawn Egg"
        cubes = len(rig.cubes)
        print(f"{f.name}: {cubes} cubes, texture {n}, scale {rig.scale}, {rig.length:.2f} x {rig.height:.2f} blocks "
              f"(half width {rig.half_width:.2f}){', glows' if rig.glows else ''}")
    merge_lang(lang)
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as fh:
        fh.write(java_file(rigs))
    water = ["mymod:" + r.f.name for r in rigs if r.f.name not in LAVA]
    tags = os.path.join(RES, "data", "minecraft", "tags", "entity_type")
    # Vanilla's fish tags: aquatic (Impaling, among others), axolotl prey, not frightening a pufferfish, breathing
    # water, and (all of them, lava fish too) not being scooped into boats.
    for tag in ("aquatic", "axolotl_hunt_targets", "not_scary_for_pufferfish", "can_breathe_under_water"):
        tag_add(os.path.join(tags, tag + ".json"), water)
    tag_add(os.path.join(tags, "cannot_be_pushed_onto_boats.json"), ["mymod:" + r.f.name for r in rigs])
    if preview_path:
        preview(preview_path, rigs, vanilla_entity)


if __name__ == "__main__":
    main()
