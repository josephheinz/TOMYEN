#!/usr/bin/env python3
"""The Cathode TV Helmet and the Soda-Bottle Jetpack (see com.jheinz.mymod.tvhelmet / sodajetpack and their client
packages): both worn models, their textures, the helmet's glowing screen frames (one per emote frame) and the emote
wheel's icons, the flat 16x16 item sprites (the helmet, the jetpack and the Fizz Charge), and the items' data: item
models and definitions, the crafting recipes with their recipe-book advancements, tags, sounds and lang.

Run from anywhere: python3 tools/gen_tv_jetpack.py [--preview out.png]

The models. Each is a list of cubes in the humanoid model's own parts (the helmet in the head, the jetpack in the
body), so ArmorRenderer can copy the wearer's pose onto them. Every cube is a whole number of half pixels on each side,
so at the textures' two texels a pixel every face covers whole texels. The cubes, their texture offsets and the layer
are written into TvHelmetModel.java and SodaJetpackModel.java between the GENERATED markers.

 * The TV Helmet is the Cathode Walker's set shrunk onto a head, and still comically big: a jungle-wood cabinet
   (a second, narrower block rounds its top edges), a grey bezel round a stepped, bulging glass screen on the left
   of the front, two knobs, a speaker grille and a red power light on the control strip to the right, a deep tube
   housing out the back, and two chrome rabbit-ear antennas splayed from a base on top (their own child parts, so
   they can lean).
 * The Soda-Bottle Jetpack is two two-litre bottles upside down on a cardboard backing, cola on the right and
   lemon-lime on the left, lashed together with two bands of duct tape. Their caps feed a black hose junction into an
   orange funnel nozzle at the bottom. Leather straps go over the shoulders and down the chest, with a chest strap and
   buckle and a bottle-cap pressure gauge on the left strap.

The textures are baked, not painted per face: each texel's colour comes from where it sits on the model, so shading
runs on across cubes (the bottles are lit as cylinders, the funnel as a cone). Wood has smooth, wavy grain bands and
light and dark bevels, plastic and chrome have soft highlights, and there is no noise anywhere but the static on the
screen. Wood, bezel and chrome colours are the Cathode Walker's (vanilla jungle planks and Steve's shoe greys).

The screen frames are glow textures the size of the helmet's texture, with only the screen's texels painted. Each
emote is drawn on an 18 x 16 canvas (the screen's 9 x 8 pixels at two texels each) and mapped across the stepped
glass by position, so the picture runs over the bulge. The side faces of the steps carry the edge of the picture,
dimmed. The power light glows red in every frame. The emotes and their frames: happy (+blink), laugh XD (two bounce
frames), angry (+blink), sad (a falling tear), surprised (+blink), cat :3 (+blink), cool shades (a moving glint),
skull (jaw chatter), heart (a beat), question (a bob), colour bars, and four frames of static. Each emote's first
frame is also written at 18 x 16 as the emote wheel's icon (textures/gui/tv_emote).

Z-fighting: no two faces may share a plane, face the same way and overlap (tools/coplanar.py), checked across both
models together, worn over the player's head, hat layer, body, jacket, arms and sleeves at rest. The generator stops
if any do.

The preview (--preview) shows Steve wearing both from the front, the side, three quarters and behind, the jetpack in
flight with foam and fizz particles, every screen frame close up, the emote wheel icons, and the item sprites at 1x and
8x."""
import json, math, os, random, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
PREVIEW = os.path.abspath(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None
# gen_longswords reads sys.argv (at import and in client_jar()), so it sees none of ours.
sys.argv = sys.argv[:1]
from gen_longswords import client_jar, read_png  # noqa: E402
from gunkit import png  # noqa: E402
from coplanar import Face, fights  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
SOUNDS = os.path.join(ASSETS, "sounds.json")
CLIENT = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "mymod", "client")
TV_JAVA = os.path.join(CLIENT, "tvhelmet", "TvHelmetModel.java")
JP_JAVA = os.path.join(CLIENT, "sodajetpack", "SodaJetpackModel.java")
SCALE = 2


# ================================================================================================== maths
def unit(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(cols, t):
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def scale(c, f):
    return tuple(max(0, min(255, c[i] * f)) for i in range(3))


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mmul(p, q):
    return [[sum(p[i][k] * q[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mvec(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


IDENTITY = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def part_matrix(rot):
    """ModelPart's rotation: rotationZYX(z, y, x), so a point is turned about x, then y, then z."""
    x, y, z = rot
    return mmul(rot_z(z), mmul(rot_y(y), rot_x(x)))


# ================================================================================================== vanilla palettes
JAR = zipfile.ZipFile(client_jar())


def vanilla(name):
    return read_png(JAR.read("assets/minecraft/textures/%s.png" % name))


STEVE = vanilla("entity/player/wide/steve")


def steve(x, y):
    w, h, px = STEVE
    return px[y * w + x][:3]


_, _, _planks = vanilla("block/jungle_planks")
WOOD = sorted({p[:3] for p in _planks}, key=lambda c: -sum(c))    # light to dark
GREYS = [steve(5, 30), steve(4, 30), steve(4, 31), steve(0, 31)]  # Steve's shoes, light to dark
CHROME = [(236, 238, 240), (206, 208, 212), (162, 164, 170), (112, 114, 120)]
DARK_GLASS = [(52, 62, 68), (34, 40, 45), (24, 28, 32), (14, 16, 18)]


# ================================================================================================== model machinery
class Part:
    """Cubes in one model part: (name, material, x0, x1, y0, y1, z0, z1) in the part's space (y down, the front toward
    -z, the wearer's right toward -x). parent: the humanoid part it hangs from, or None for a root part."""

    def __init__(self, name, cubes, parent=None, offset=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
        self.name, self.cubes, self.parent, self.offset, self.rot = name, cubes, parent, offset, rot


def size(c):
    return (c[3] - c[2], c[5] - c[4], c[7] - c[6])


def pack(parts, tex_w, tex_h):
    """Integer texture offsets (in model pixels) for each cube's box unwrap, shelf-packed."""
    cubes = [c for p in parts for c in p.cubes]
    names = [c[0] for c in cubes]
    assert len(names) == len(set(names)), "cube names must be unique"
    for c in cubes:
        for s in size(c):
            assert s > 0 and abs(s * SCALE - round(s * SCALE)) < 1e-9, (c[0], s)
    order = sorted(cubes, key=lambda c: -(size(c)[2] + size(c)[1]))
    offs, x, y, shelf = {}, 0, 0, 0
    for c in order:
        w, h, d = size(c)
        fw, fh = math.ceil(2 * (w + d)), math.ceil(d + h)
        if x + fw > tex_w:
            x, y, shelf = 0, y + shelf, 0
        offs[c[0]] = (x, y)
        x += fw + 1
        shelf = max(shelf, fh + 1)
    assert y + shelf <= tex_h, "texture too small"
    return offs


def cube_polys(c, uv, origin=(0.0, 0.0, 0.0), matrix=None):
    """The six faces of a cube as the game builds them (ModelPart.Cube): for each, its name, its corners in the part's
    space, the same corners posed (by matrix, then origin), its uv rect (u0, v0, u1, v1) in model pixels (corner 1 sits
    at (u0, v0), corner 0 at (u1, v0), corner 2 at (u0, v1)), and its outward normal in the part's space."""
    name, _, x0, x1, y0, y1, z0, z1 = c
    w, h, d = size(c)
    u, v = uv
    m = matrix or IDENTITY
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    faces = [
        ("top", [l1, l0, t0, t1], (u1, v0, u2, v1), (0, -1, 0)),
        ("bottom", [t2, t3, l3, l2], (u2, v1, u22, v0), (0, 1, 0)),
        ("west", [t0, l0, l3, t3], (u0, v1, u1, v2), (-1, 0, 0)),
        ("north", [t1, t0, t3, t2], (u1, v1, u2, v2), (0, 0, -1)),
        ("east", [l1, t1, t2, l2], (u2, v1, u3, v2), (1, 0, 0)),
        ("south", [l0, l1, l2, l3], (u3, v1, u4, v2), (0, 0, 1)),
    ]
    out = []
    for face, verts, rect, n in faces:
        posed = [tuple(origin[i] + mvec(m, p)[i] for i in range(3)) for p in verts]
        out.append((face, verts, posed, rect, n))
    return out


class Model:
    def __init__(self, name, parts, tex_w, tex_h):
        self.name, self.parts, self.tex_w, self.tex_h = name, parts, tex_w, tex_h
        self.uv = pack(parts, tex_w, tex_h)

    def pose(self, part, root_poses=None):
        """(origin, matrix) of a part in model space; root_poses maps a root part's name to its (pivot, rot) override."""
        root_poses = root_poses or {}
        if part.parent is None:
            pivot, rot = root_poses.get(part.name, (part.offset, part.rot))
            return pivot, part_matrix(rot)
        parent = next(p for p in self.parts if p.name == part.parent)
        po, pm = self.pose(parent, root_poses)
        origin = tuple(po[i] + mvec(pm, part.offset)[i] for i in range(3))
        return origin, mmul(pm, part_matrix(part.rot))

    def faces(self, root_poses=None):
        """Every face: (part, cube, face name, part-space corners, posed corners, uv rect, part-space normal, posed
        normal)."""
        out = []
        for part in self.parts:
            origin, m = self.pose(part, root_poses)
            for c in part.cubes:
                for face, verts, posed, rect, n in cube_polys(c, self.uv[c[0]], origin, m):
                    out.append((part, c, face, verts, posed, rect, n, mvec(m, n)))
        return out

    def bake(self, painter):
        """A texture from painter(cube, face, point, normal, s, t) -> rgb, rgba or None (left clear), sampled at each
        texel's centre; s and t run 0..1 across the face (s from corner 1 to corner 0, t from corner 1 to corner 2)."""
        w, h = self.tex_w * SCALE, self.tex_h * SCALE
        tex = [(0, 0, 0, 0)] * (w * h)
        for part, c, face, verts, _, (ua, va, ub, vb), n, _ in self.faces():
            A = verts[1]
            eu = [verts[0][i] - verts[1][i] for i in range(3)]
            ev = [verts[2][i] - verts[1][i] for i in range(3)]
            for ty in range(int(round(min(va, vb) * SCALE)), int(round(max(va, vb) * SCALE))):
                for tx in range(int(round(min(ua, ub) * SCALE)), int(round(max(ua, ub) * SCALE))):
                    s = ((tx + 0.5) / SCALE - ua) / (ub - ua)
                    t = ((ty + 0.5) / SCALE - va) / (vb - va)
                    p = tuple(A[i] + eu[i] * s + ev[i] * t for i in range(3))
                    col = painter(c, face, p, n, s, t)
                    if col is None:
                        continue
                    alpha = col[3] if len(col) > 3 else 255
                    tex[ty * w + tx] = tuple(int(round(max(0, min(255, k)))) for k in col[:3]) + (alpha,)
        return tex

    def java(self, extra_parts):
        """The createLayer() body. extra_parts: (name, parent) of empty humanoid parts to add."""
        lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
        declared = {"root"}
        humanoid_pivots = {"head": (0.0, 0.0, 0.0), "body": (0.0, 0.0, 0.0), "right_arm": (-5.0, 2.0, 0.0),
                           "left_arm": (5.0, 2.0, 0.0), "right_leg": (-1.9, 12.0, 0.0), "left_leg": (1.9, 12.0, 0.0)}
        mine = {p.name: p for p in self.parts}

        def builder(part):
            b = "CubeListBuilder.create()"
            for c in part.cubes if part else []:
                u, v = self.uv[c[0]]
                w, h, d = size(c)
                b += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({jf(c[2])}, {jf(c[4])}, {jf(c[6])}, {jf(w)}, {jf(h)}, {jf(d)})"
            return b

        def pose_of(name, part):
            if part is not None and part.parent is not None:
                o, r = part.offset, part.rot
                if any(r):
                    return f"PartPose.offsetAndRotation({jf(o[0])}, {jf(o[1])}, {jf(o[2])}, {jf(r[0])}, {jf(r[1])}, {jf(r[2])})"
                return f"PartPose.offset({jf(o[0])}, {jf(o[1])}, {jf(o[2])})"
            o = humanoid_pivots.get(name, (0.0, 0.0, 0.0))
            return "PartPose.ZERO" if o == (0.0, 0.0, 0.0) else f"PartPose.offset({jf(o[0])}, {jf(o[1])}, {jf(o[2])})"

        order = ["head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"]
        parents = {"hat": "head"}
        children = [p for p in self.parts if p.parent is not None]
        for name in order:
            part = mine.get(name)
            parent = parents.get(name, "root")
            needs_var = name == "head" or any(ch.parent == name for ch in children)
            text = f"root.addOrReplaceChild(\"{name}\", {builder(part)}, {pose_of(name, part)});" if parent == "root" \
                else f"{parent}.addOrReplaceChild(\"{name}\", {builder(part)}, {pose_of(name, part)});"
            lines.append(("\t\tPartDefinition %s = " % name if needs_var else "\t\t") + text)
            declared.add(name)
            for ch in children:
                if ch.parent == name:
                    lines.append(f"\t\t{name}.addOrReplaceChild(\"{ch.name}\", {builder(ch)}, {pose_of(ch.name, ch)});")
        lines.append(f"\t\treturn LayerDefinition.create(mesh, {self.tex_w}, {self.tex_h});")
        return "\n".join(lines)


def jf(v):
    v = round(v, 6)
    return f"{v:.1f}f" if v == int(v) else f"{v:g}f"


def write_java(path, body, tool="tools/gen_tv_jetpack.py"):
    src = open(path).read()
    start, end = f"\t\t// BEGIN GENERATED by {tool}\n", "\t\t// END GENERATED\n"
    a, b = src.index(start) + len(start), src.index(end)
    with open(path, "w") as out:
        out.write(src[:a] + body + "\n" + src[b:])


# ================================================================================================== the TV helmet
# Head space: the head is x -4..4, y -8..0, z -4..4 (its hat layer half a pixel further out); front toward -z.
SCREEN_X0, SCREEN_Y0 = -5.75, -10.25    # the glass's front face, top left: 9 x 8 pixels, 18 x 16 texels
CANVAS_W, CANVAS_H = 18, 16
TV_CUBES = [
    # The cabinet, and a narrower block over it to round its top edges.
    ("cabinet", "wood", -7.0, 7.0, -11.5, 1.0, -6.0, 5.5),
    ("cabinet_round", "wood", -6.5, 6.5, -12.0, -0.5, -5.5, 5.0),
    # The screen on the left of the front: a grey bezel, the glass, the bulge and its crown, each standing out of the
    # last.
    ("bezel", "bezel", -6.25, 3.75, -10.75, -1.75, -6.5, -5.5),
    ("glass", "glass", -5.75, 3.25, -10.25, -2.25, -7.0, -6.0),
    ("bulge", "glass", -5.25, 2.75, -9.75, -2.75, -7.5, -6.5),
    ("crown", "glass", -4.25, 1.75, -8.75, -3.75, -7.75, -7.25),
    # Two knobs on the control strip to the right.
    ("knob_channel", "knob", 4.5, 6.0, -10.0, -8.5, -7.0, -6.0),
    ("knob_volume", "knob", 4.5, 6.0, -7.5, -6.0, -7.0, -6.0),
    # The tube housing, out the back.
    ("housing", "housing", -5.0, 5.0, -10.5, -1.5, 5.5, 8.5),
    ("housing_cap", "housing", -3.5, 3.5, -9.0, -3.0, 8.5, 10.0),
    # The antennas' base on top.
    ("antenna_base", "base", -1.5, 1.5, -13.0, -12.0, -0.5, 1.5),
]
ANTENNA = [("rod", "rod", -0.25, 0.25, -9.0, 0.0, -0.25, 0.25), ("tip", "tip", -0.5, 0.5, -9.75, -8.75, -0.5, 0.5)]
TV_PARTS = [Part("head", TV_CUBES)]
for _side, _name in ((-1, "antenna_right"), (1, "antenna_left")):
    TV_PARTS.append(Part(_name, [(f"{_name}_{c[0]}",) + c[1:] for c in ANTENNA], parent="head",
                         offset=(_side * 0.75, -12.5, 0.5), rot=(-0.25, 0.0, _side * 0.5)))
TV = Model("tv_helmet", TV_PARTS, 128, 64)

LED = (4.75, 5.25, -4.25, -3.75)          # the power light, x0 x1 y0 y1 on the cabinet's front
GRILLE = (4.25, 6.25, -3.25, -0.75)       # the speaker grille's slots
PANEL = (3.75, 6.75, -10.75, -0.25)       # the control strip's darker inset
BADGE = (-2.5, -0.5, -1.25, -0.75)        # a little chrome maker's badge under the screen


def inside(p, box, axes=(0, 1)):
    return box[0] <= p[axes[0]] <= box[1] and box[2] <= p[axes[1]] <= box[3]


def wood_colour(face, p, n, s, t, base, c):
    """Veneer: a smooth ramp, wavy grain bands running the long way, light and dark bevels at the face's edges."""
    x, y, z = p
    across = z if face in ("top", "bottom") else y
    along = x if face in ("top", "bottom", "north", "south") else z
    tone = base + 0.07 * math.sin(across * 2.1 + 0.9 * math.sin(along * 0.35 + across * 0.2))
    if face not in ("top", "bottom"):
        tone += 0.12 * (t - 0.5) * (1 if face != "south" else 0.5)
    # Bevels: half a pixel in from the edge.
    w, h, d = size(c)
    span_s = {"top": w, "bottom": w, "north": w, "south": w, "west": d, "east": d}[face]
    span_t = {"top": d, "bottom": d, "north": h, "south": h, "west": h, "east": h}[face]
    es, et = min(s, 1 - s) * span_s, min(t, 1 - t) * span_t
    if et < 0.5 and face not in ("top", "bottom"):
        tone += -0.14 if t < 0.5 else 0.14
    elif es < 0.5:
        tone += 0.06
    elif face in ("top", "bottom") and et < 0.5:
        tone += 0.06
    return ramp(WOOD, tone)


def tv_painter(c, face, p, n, s, t):
    name, mat = c[0], c[1]
    x, y, z = p
    if mat == "wood":
        base = {"top": 0.22, "bottom": 0.78, "north": 0.38, "south": 0.62, "west": 0.46, "east": 0.46}[face]
        if name == "cabinet" and face == "north":
            if inside(p, LED):
                return (96, 22, 20)
            if inside(p, BADGE):
                return CHROME[1] if y < -1.0 else CHROME[2]
            if inside(p, PANEL):
                # The control strip: a darker inset with a pale rim, the grille's slots low down.
                rim = min(x - PANEL[0], PANEL[1] - x, y - PANEL[2], PANEL[3] - y)
                if rim < 0.5:
                    return ramp(WOOD, 0.2 if (x - PANEL[0] < 0.5 or y - PANEL[2] < 0.5) else 0.75)
                if inside(p, GRILLE):
                    row = int((y - GRILLE[2]) * SCALE)
                    return GREYS[3] if row % 2 == 0 else ramp(WOOD, 0.85)
                return ramp(WOOD, 0.7 + 0.08 * (y - PANEL[2]) / (PANEL[3] - PANEL[2]))
        return wood_colour(face, p, n, s, t, base, c)
    if mat == "bezel":
        if face == "north":
            # The bezel's face: lit from the top left, a soft groove round the glass.
            edge = min(x - c[2], c[3] - x, y - c[4], c[5] - y)
            tone = 0.35 + 0.25 * ((x - c[2]) / 10 + (y - c[4]) / 9) * 0.5
            if edge < 0.5:
                tone -= 0.15
            return ramp(GREYS, tone)
        return ramp(GREYS, {"top": 0.2, "bottom": 0.85, "west": 0.5, "east": 0.6, "south": 0.9}[face])
    if mat == "glass":
        # Dark glass (the glow frames cover it): a soft highlight curving over the top left of the bulge.
        if face == "north":
            r = math.hypot((x - (SCREEN_X0 + 1.5)) / 1.2, (y - (SCREEN_Y0 + 1.2)) / 1.0)
            if 1.0 <= r < 1.8:
                return lerp(DARK_GLASS[1], DARK_GLASS[0], 0.8)
            return DARK_GLASS[1]
        return DARK_GLASS[2]
    if mat == "knob":
        if face == "north":
            cx, cy = (c[2] + c[3]) / 2, (c[4] + c[5]) / 2
            r = math.hypot(x - cx, y - cy)
            # A pointer from the centre up and to the right.
            if 0.2 < x - cx < 0.75 and -0.75 < y - cy < -0.2:
                return CHROME[0]
            return ramp(CHROME, 0.25 + r * 0.7)
        return ramp(GREYS, {"top": 0.1, "bottom": 0.8, "west": 0.4, "east": 0.5, "south": 0.9}[face])
    if mat == "housing":
        # Dark hardboard, with vent slots along the top and sides of the big block.
        tone = {"top": 0.82, "bottom": 0.98, "north": 0.9, "south": 0.88, "west": 0.92, "east": 0.92}[face]
        dark = ramp(WOOD, 1.0)
        col = lerp(dark, (40, 26, 18), tone - 0.8)
        if name == "housing" and face in ("top", "west", "east"):
            along = z
            if 6.0 <= along <= 8.0 and int((along - 6.0) * SCALE) % 2 == 0 and (face == "top" and -3.5 <= x <= 3.5 or
                                                                                 face != "top" and -9.0 <= y <= -3.0):
                return (20, 14, 10)
        if name == "housing_cap" and face == "south":
            if int((y + 9.0) * SCALE) % 3 == 1 and -2.5 <= x <= 2.5:
                return (20, 14, 10)
        return col
    if mat == "base":
        return ramp(GREYS, {"top": 0.3, "bottom": 0.9, "north": 0.5, "south": 0.7, "west": 0.6, "east": 0.6}[face])
    if mat == "rod":
        return ramp(CHROME, 0.2 + 0.5 * (y + 9.0) / 9.0 + (0.15 if face in ("east", "south") else 0.0))
    if mat == "tip":
        return CHROME[0] if face == "top" else CHROME[1] if face in ("north", "west") else CHROME[2]
    raise ValueError(mat)


TV_TEXTURE = TV.bake(tv_painter)
png(os.path.join(ASSETS, "textures", "entity", "tv_helmet.png"), TV.tex_w * SCALE, TV.tex_h * SCALE, TV_TEXTURE)

# ---------------------------------------------------------------------------------------------- the emotes
PHOSPHOR = (222, 244, 255)
SCAN = [(10, 22, 34), (4, 12, 20)]
COLOURS = {
    "X": PHOSPHOR, "x": scale(PHOSPHOR, 0.55),
    "R": (255, 64, 72), "r": (176, 30, 40), "P": (255, 160, 200), "B": (120, 190, 255), "b": (70, 120, 200),
    "K": (8, 10, 14), "W": (246, 244, 232), "w": (176, 174, 164), "Y": (255, 222, 96), "G": (170, 255, 170),
}


def sym(rows):
    """Full 18-wide rows from left halves (9 wide), mirrored."""
    assert all(len(r) == 9 for r in rows) and len(rows) == CANVAS_H, rows
    return [r + r[::-1] for r in rows]


def overlay(rows, marks):
    rows = [list(r) for r in rows]
    for (x, y), ch in marks.items():
        rows[y][x] = ch
    return ["".join(r) for r in rows]


def shift(rows, dy):
    blank = "." * CANVAS_W
    return (rows[dy:] + [blank] * dy) if dy > 0 else ([blank] * -dy + rows[:dy])


E = "........."
HAPPY = sym([E, E, E, ".....XX..", ".....XX..", ".....XX..", E, E, E, "...X.....", "...xX....", "....xXX..",
             "......XXX", E, E, E])
HAPPY_BLINK = sym([E, E, E, E, E, "....xXXx.", E, E, E, "...X.....", "...xX....", "....xXX..", "......XXX", E, E, E])
LAUGH = sym([E, E, E, "....X..X.", ".....XX..", ".....XX..", "....X..X.", E, "...XXXXXX", "...XRRRRR", "...xXRRRR",
             "....xXRRP", ".....xXXX", E, E, E])
ANGRY = sym([E, E, "...RR....", "....RRR..", "......RR.", ".....XX..", ".....XX..", E, E, E, "......XXX", "....xXX..",
             "...xX....", "...X.....", E, E])
ANGRY_BLINK = sym([E, E, "...RR....", "....RRR..", "......RR.", E, "....xXXx.", E, E, E, "......XXX", "....xXX..",
                   "...xX....", "...X.....", E, E])
SAD = sym([E, E, E, "......xX.", "....xX...", E, ".....XX..", ".....XX..", E, E, E, "......XXX", "....xXX..",
           "...xX....", "...X.....", E])
SAD_A = overlay(SAD, {(5, 8): "b", (5, 9): "B"})
SAD_B = overlay(SAD, {(5, 10): "b", (5, 11): "B"})
SURPRISED = sym([E, ".....XX..", E, "....xXXx.", "....X..X.", "....X..X.", "....xXXx.", E, E, ".......xX", "......X..",
                 "......X..", ".......xX", E, E, E])
SURPRISED_BLINK = sym([E, ".....XX..", E, E, E, "....xXXx.", E, E, E, ".......xX", "......X..", "......X..", ".......xX",
                       E, E, E])
CAT = sym([E, E, E, E, ".....XX..", ".....XX..", E, E, "..PP.....", "..PP.....", "......X.X", ".......X.", E, E, E, E])
CAT_BLINK = sym([E, E, E, E, E, "....xXXx.", E, E, "..PP.....", "..PP.....", "......X.X", ".......X.", E, E, E, E])
COOL = sym([E, E, E, "..XXXXXXX", "..XKKKKX.", "..XKKKKX.", "...XKKX..", "....XX...", E, E, E, E, E, E, E, E])
COOL = overlay(COOL, {(x, 11): "X" for x in range(6, 12)} | {(12, 10): "X", (13, 10): "x"})
COOL_A = overlay(COOL, {(3, 4): "W", (4, 5): "w"})
COOL_B = overlay(COOL, {(11, 4): "W", (12, 5): "w"})
SKULL = sym([E, ".....WWWW", "...WWWWWW", "..WWWWWWW", "..WWWWWWW", "..WKKKWWW", "..WKKKWWW", "..WKKKWWW", "..wWWWWWK",
             "...wWWWWW", "....wWWWW", "....WKWKW", "....wWWWW", E, E, E])
SKULL_OPEN = sym([E, ".....WWWW", "...WWWWWW", "..WWWWWWW", "..WWWWWWW", "..WKKKWWW", "..WKKKWWW", "..WKKKWWW",
                  "..wWWWWWK", "...wWWWWW", "....wWWWW", "....KKKKK", "....WKWKW", "....wWWWW", E, E])
HEART = sym([E, E, "...RRR...", "..RRRRR..", ".RRRRRRRR", ".RRRRRRRR", ".RRRRRRRR", "..RRRRRRR", "...RRRRRR",
             "....RRRRR", ".....RRRR", "......RRR", ".......RR", "........R", E, E])
HEART = overlay(HEART, {(3, 3): "P", (2, 4): "P", (2, 5): "P"})
HEART_SMALL = sym([E, E, E, "....RR...", "...RRRR..", "..RRRRRRR", "..RRRRRRR", "...RRRRRR", "....RRRRR", ".....RRRR",
                   "......RRR", ".......RR", "........R", E, E, E])
HEART_SMALL = overlay(HEART_SMALL, {(4, 4): "P", (3, 5): "P"})
QUESTION = ["." * 18, "......YYYYYY......", ".....YYYYYYYY.....", ".....YY....YY.....", "..........YYY.....",
            ".........YYY......", "........YYY.......", "........YY........", "........YY........", "." * 18,
            "........YY........", "........YY........", "." * 18, "." * 18, "." * 18, "." * 18]
BAR_WIDTHS = [3, 2, 3, 2, 3, 2, 3]
BARS_TOP = [(200, 200, 200), (200, 200, 0), (0, 200, 200), (0, 200, 0), (200, 0, 200), (200, 0, 0), (0, 0, 200)]
BARS_LOW = [(0, 0, 200), (16, 16, 16), (200, 0, 200), (16, 16, 16), (0, 200, 200), (16, 16, 16), (200, 200, 200)]
STATIC_GREYS = [(36, 38, 40), (86, 88, 90), (138, 140, 142), (186, 188, 190), (232, 234, 236)]
TINTS = {"angry": [(36, 12, 14), (22, 6, 8)], "heart": [(34, 12, 26), (20, 6, 14)], "sad": [(8, 18, 40), (4, 10, 26)],
         "cat": [(30, 16, 34), (18, 8, 22)]}


def ascii_frame(rows, tint=None):
    scan = tint or SCAN

    def f(i, j):
        ch = rows[j][i]
        return COLOURS[ch] if ch != "." else scan[j % 2]
    return f


def bars_frame(i, j):
    k, edge = 0, BAR_WIDTHS[0]
    while i >= edge:
        k += 1
        edge += BAR_WIDTHS[k]
    return (BARS_LOW if j >= 13 else BARS_TOP)[k]


def static_frame(seed):
    rnd = random.Random(seed)
    grid = [[STATIC_GREYS[rnd.choice((0, 0, 1, 1, 2, 2, 3, 4))] for _ in range(CANVAS_W)] for _ in range(CANVAS_H)]
    roll = rnd.randrange(CANVAS_H)
    for i in range(CANVAS_W):
        grid[roll][i] = scale(grid[roll][i], 1.25)
    return lambda i, j: grid[j][i]


# (emote, [frames]) in the order of TvEmote in Java.
EMOTES = OrderedDict([
    ("happy", [("happy", ascii_frame(HAPPY)), ("happy_blink", ascii_frame(HAPPY_BLINK))]),
    ("laugh", [("laugh", ascii_frame(LAUGH)), ("laugh_1", ascii_frame(shift(LAUGH, 1)))]),
    ("angry", [("angry", ascii_frame(ANGRY, TINTS["angry"])), ("angry_blink", ascii_frame(ANGRY_BLINK, TINTS["angry"]))]),
    ("sad", [("sad", ascii_frame(SAD_A, TINTS["sad"])), ("sad_1", ascii_frame(SAD_B, TINTS["sad"]))]),
    ("surprised", [("surprised", ascii_frame(SURPRISED)), ("surprised_blink", ascii_frame(SURPRISED_BLINK))]),
    ("cat", [("cat", ascii_frame(CAT, TINTS["cat"])), ("cat_blink", ascii_frame(CAT_BLINK, TINTS["cat"]))]),
    ("cool", [("cool", ascii_frame(COOL_A)), ("cool_1", ascii_frame(COOL_B))]),
    ("skull", [("skull", ascii_frame(SKULL)), ("skull_1", ascii_frame(SKULL_OPEN))]),
    ("heart", [("heart", ascii_frame(HEART, TINTS["heart"])), ("heart_1", ascii_frame(HEART_SMALL, TINTS["heart"]))]),
    ("question", [("question", ascii_frame(QUESTION)), ("question_1", ascii_frame(shift(QUESTION, 1)))]),
    ("bars", [("bars", bars_frame)]),
    ("static", [("static_%d" % k, static_frame(2000 + k)) for k in range(4)]),
])
DARK_CORNERS = {(0, 0), (1, 0), (0, 1), (17, 0), (16, 0), (17, 1), (0, 15), (1, 15), (0, 14), (17, 15), (16, 15), (17, 14)}


def canvas_at(f, x, y):
    i = max(0, min(CANVAS_W - 1, int(math.floor((x - SCREEN_X0) * SCALE))))
    j = max(0, min(CANVAS_H - 1, int(math.floor((y - SCREEN_Y0) * SCALE))))
    if (i, j) in DARK_CORNERS:
        return DARK_GLASS[3]
    return f(i, j)


def glow_painter(f):
    def paint(c, face, p, n, s, t):
        x, y, z = p
        if c[0] == "cabinet" and face == "north" and inside(p, LED):
            return (255, 70, 56)
        if c[1] != "glass" or face == "south":
            return None
        if face == "north":
            return canvas_at(f, x, y)
        # The step's sides carry the edge of the picture, dimmed; pulled half a texel in so they sample the screen.
        cx = min(max(x, SCREEN_X0 + 0.25), SCREEN_X0 + 9 - 0.25)
        cy = min(max(y, SCREEN_Y0 + 0.25), SCREEN_Y0 + 8 - 0.25)
        return scale(canvas_at(f, cx, cy), 0.7)
    return paint


FRAMES = OrderedDict()
for emote, frames in EMOTES.items():
    for fname, f in frames:
        FRAMES[fname] = TV.bake(glow_painter(f))
        png(os.path.join(ASSETS, "textures", "entity", "tv_helmet", f"screen_{fname}.png"), TV.tex_w * SCALE,
            TV.tex_h * SCALE, FRAMES[fname])
    # The wheel's icon: the first frame, flat.
    first = frames[0][1]
    icon = [((DARK_GLASS[3] if (i, j) in DARK_CORNERS else first(i, j)) + (255,)) for j in range(CANVAS_H) for i in range(CANVAS_W)]
    icon = [tuple(int(v) for v in px) for px in icon]
    png(os.path.join(ASSETS, "textures", "gui", "tv_emote", f"{emote}.png"), CANVAS_W, CANVAS_H, icon)

# ================================================================================================== the jetpack
# Body space: the body is x -4..4, y 0..12, z -2..2 (its jacket a quarter pixel further out); the back toward +z.
BOTTLE_Z = 5.0
JP_CUBES = []


def bottle(tag, cx, mat):
    zc = BOTTLE_Z
    return [
        (f"{tag}_base", f"{mat}_clear", cx - 1.25, cx + 1.25, 0.5, 1.5, zc - 1.25, zc + 1.25),
        (f"{tag}_body", mat, cx - 1.75, cx + 1.75, 1.0, 7.0, zc - 1.5, zc + 1.5),
        (f"{tag}_body_deep", mat, cx - 1.5, cx + 1.5, 1.25, 6.75, zc - 1.75, zc + 1.75),
        (f"{tag}_shoulder", mat, cx - 1.25, cx + 1.25, 7.0, 8.0, zc - 1.25, zc + 1.25),
        (f"{tag}_neck", f"{mat}_clear", cx - 0.75, cx + 0.75, 8.0, 8.5, zc - 0.75, zc + 0.75),
        (f"{tag}_cap", f"{mat}_cap", cx - 1.0, cx + 1.0, 8.5, 9.5, zc - 1.0, zc + 1.0),
    ]


JP_CUBES += bottle("cola", -1.875, "cola")
JP_CUBES += bottle("lime", 1.875, "lime")
JP_CUBES += [
    # The cardboard backing the bottles are taped to, and two bands of duct tape round the pair.
    ("board", "board", -3.5, 3.5, 1.0, 9.0, 2.75, 3.25),
    ("tape_top", "tape", -4.0, 4.0, 1.5, 2.5, 3.0, 7.0),
    ("tape_low", "tape", -4.0, 4.0, 5.5, 6.5, 3.0, 7.0),
    # The hose junction under the caps, and the funnel nozzle flaring out below it.
    ("hose", "hose", -2.25, 2.25, 9.5, 10.5, 4.5, 5.5),
    ("funnel_throat", "funnel", -0.75, 0.75, 10.5, 11.5, 4.25, 5.75),
    ("funnel_flare", "funnel", -1.25, 1.25, 11.5, 12.0, 3.75, 6.25),
    ("funnel_wide", "funnel", -1.75, 1.75, 12.0, 12.5, 3.25, 6.75),
    ("funnel_rim", "funnel", -2.0, 2.0, 12.5, 13.0, 3.0, 7.0),
]
for _side, _tag in ((-1, "right"), (1, "left")):
    x0, x1 = (-2.75, -1.25) if _side < 0 else (1.25, 2.75)
    JP_CUBES += [
        (f"strap_{_tag}_shoulder", "strap", x0, x1, -0.75, -0.25, -2.75, 2.75),
        (f"strap_{_tag}_front", "strap", x0, x1, -0.25, 7.75, -2.75, -2.25),
        (f"strap_{_tag}_back", "strap", x0, x1, -0.25, 3.75, 2.25, 2.75),
    ]
JP_CUBES += [
    ("chest_strap", "strap_dark", -3.5, 3.5, 5.0, 6.0, -3.25, -2.75),
    ("buckle", "buckle", -0.75, 0.75, 4.75, 6.25, -3.75, -3.25),
    ("gauge", "gauge", 1.0, 3.0, 1.5, 3.5, -3.25, -2.75),
]
JP = Model("soda_jetpack", [Part("body", JP_CUBES)], 64, 64)
NOZZLE = (0.0, 13.0, BOTTLE_Z)   # where the fizz comes out, body space

LIGHT = unit((-0.35, -0.8, 0.5))     # from above, behind and to the right (y down, +z behind)
VIEW = unit((-0.2, -0.3, 1.0))
HALF = unit(tuple(LIGHT[i] + VIEW[i] for i in range(3)))
BOTTLES = {
    "cola": dict(cx=-1.875, body=[(46, 20, 10), (78, 36, 18), (112, 56, 28), (150, 84, 48)], clear=(186, 160, 140),
                 foam=(226, 206, 170), label=[(150, 20, 24), (204, 34, 38), (236, 70, 64)], stripe=(250, 246, 240),
                 cap=[(132, 20, 20), (196, 36, 36), (236, 84, 76)]),
    "lime": dict(cx=1.875, body=[(28, 80, 36), (52, 128, 60), (88, 170, 92), (140, 210, 140)], clear=(176, 220, 176),
                 foam=(220, 244, 210), label=[(176, 140, 24), (230, 196, 54), (250, 228, 120)], stripe=(60, 150, 66),
                 cap=[(30, 64, 140), (52, 104, 200), (110, 160, 236)]),
}


def shade(cols, n, gloss=0.0, base=0.35, span=0.65):
    diffuse = max(0.0, dot(n, LIGHT))
    c = ramp(cols, base + span * diffuse)
    if gloss:
        nh = max(0.0, dot(n, HALF))
        c = lerp(c, (255, 255, 255), min(0.8, gloss * (0.35 * nh ** 8 + 0.7 * nh ** 36)))
    return c


def blend_normal(n_shape, n_face, k=0.2):
    return unit(tuple((1 - k) * n_shape[i] + k * n_face[i] for i in range(3)))


def jp_painter(c, face, p, n, s, t):
    name, mat = c[0], c[1]
    x, y, z = p
    kind = mat.split("_")[0]
    if kind in BOTTLES:
        b = BOTTLES[kind]
        radial = unit((x - b["cx"], 0.0, z - BOTTLE_Z))
        nn = blend_normal(radial, n, 0.25) if face not in ("top", "bottom") else blend_normal(radial, n, 0.7)
        if mat.endswith("_cap"):
            # Ridged round the edge: every other texel a shade darker.
            col = shade(b["cap"], nn, gloss=0.4)
            if face not in ("top", "bottom") and int((s * size(c)[0 if face in ("north", "south") else 2]) * SCALE) % 2:
                col = scale(col, 0.88)
            return col
        if mat.endswith("_clear"):
            return shade([scale(b["clear"], 0.6), scale(b["clear"], 0.85), b["clear"]], nn, gloss=1.0, base=0.3)
        # The body: an air gap at the top (the bottle is upside down), a line of foam, the drink, and a label.
        if y < 1.75:
            return shade([scale(b["clear"], 0.6), scale(b["clear"], 0.85), b["clear"]], nn, gloss=1.0, base=0.3)
        if y < 2.25 and face != "top":
            return shade([scale(b["foam"], 0.7), b["foam"], (255, 255, 255)], nn, base=0.4)
        if 2.75 <= y <= 5.25 and face not in ("top", "bottom"):
            angle = math.atan2(x - b["cx"], z - BOTTLE_Z)
            # Cola gets a swoosh that rises round the bottle, lemon-lime a plain band.
            wave = 4.0 + (0.5 * math.sin(angle * 0.5) if kind == "cola" else 0.0)
            if abs(y - wave) < 0.3:
                return shade([scale(b["stripe"], 0.7), b["stripe"], b["stripe"]], nn, base=0.45, span=0.55)
            return shade(b["label"], nn, gloss=0.3, base=0.4)
        return shade(b["body"], nn, gloss=1.0)
    if mat == "board":
        # Corrugated cardboard: faint flutes running down it.
        tone = 0.92 if int((x + 4.0) * SCALE) % 2 else 1.0
        col = scale((196, 160, 110), tone * (0.72 + 0.28 * max(0.0, dot(n, LIGHT))))
        return col if face not in ("top", "bottom") else scale(col, 0.9)
    if mat == "tape":
        # Silver duct tape: a soft sheen, darker torn edges top and bottom.
        nn = n
        if face not in ("top", "bottom"):
            # The band wraps the bottles: bow its normal out round the pair.
            nn = blend_normal(unit((x / 4.0, 0.0, (z - BOTTLE_Z) / 2.0)), n, 0.4)
        col = shade([(104, 106, 112), (148, 150, 156), (188, 190, 196), (222, 224, 228)], nn, gloss=0.5)
        if face not in ("top", "bottom") and (t < 0.12 or t > 0.88):
            col = scale(col, 0.82)
        return col
    if mat == "hose":
        return shade([(18, 18, 20), (36, 36, 40), (60, 60, 66)], n, gloss=0.6)
    if mat == "funnel":
        cone = unit((x / 2.0, 0.55, (z - BOTTLE_Z) / 2.0))
        if face == "bottom" and name == "funnel_rim":
            # The mouth: the rim's lip, and the dark inside of the funnel.
            if min(s, 1 - s, t, 1 - t) * 4.0 < 0.5:
                return (238, 132, 40)
            depth = max(abs(x), abs(z - BOTTLE_Z)) / 2.0
            return lerp((56, 26, 8), (140, 64, 16), depth)
        nn = blend_normal(cone, n, 0.35) if face != "bottom" else n
        return shade([(150, 62, 12), (212, 104, 26), (240, 140, 44), (255, 186, 110)], nn, gloss=0.7)
    if mat in ("strap", "strap_dark"):
        cols = [(70, 42, 22), (104, 64, 34), (136, 88, 48), (162, 112, 66)]
        if mat == "strap_dark":
            cols = [scale(k, 0.82) for k in cols]
        col = shade(cols, n, base=0.4, span=0.5)
        # Darker toward the long edges, and a stitched seam down the middle: a pale dash every other texel.
        w, h, d = size(c)
        dims = {"top": (w, d), "bottom": (w, d), "north": (w, h), "south": (w, h), "west": (d, h), "east": (d, h)}[face]
        long_s = dims[0] >= dims[1]
        across, along = (t, s) if long_s else (s, t)
        cross_len, along_len = (dims[1], dims[0]) if long_s else (dims[0], dims[1])
        if cross_len >= 1.5:
            edge = min(across, 1 - across) * cross_len
            if edge < 0.5:
                col = scale(col, 0.82)
            elif int(along * along_len * SCALE) % 2 == 0:
                return lerp(col, (226, 196, 150), 0.45)
        return col
    if mat == "buckle":
        if face == "north":
            if 0.3 < s < 0.7 and 0.3 < t < 0.7:
                return (60, 62, 66)
            return CHROME[1] if s + t < 1.0 else CHROME[2]
        return CHROME[2]
    if mat == "gauge":
        if face == "north":
            i, j = int(s * 4), int(t * 4)
            if i in (0, 3) or j in (0, 3):
                return (196, 36, 36) if (i + j) % 3 else (150, 24, 24)
            # The dial: white, its needle pointing up and right (bottle cap faces sit with u mirrored, so i is flipped).
            return (220, 40, 40) if (i, j) == (1, 1) else (244, 242, 232)
        return (164, 30, 30)
    raise ValueError(mat)


JP_TEXTURE = JP.bake(jp_painter)
png(os.path.join(ASSETS, "textures", "entity", "soda_jetpack.png"), JP.tex_w * SCALE, JP.tex_h * SCALE, JP_TEXTURE)


# ================================================================================================== z-fighting
def box_faces(label, x0, x1, y0, y1, z0, z1, origin=(0, 0, 0)):
    ox, oy, oz = origin
    x0, x1, y0, y1, z0, z1 = x0 + ox, x1 + ox, y0 + oy, y1 + oy, z0 + oz, z1 + oz
    return [Face(label + ".top", [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
            Face(label + ".bottom", [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0)),
            Face(label + ".west", [(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)], (-1, 0, 0)),
            Face(label + ".east", [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], (1, 0, 0)),
            Face(label + ".north", [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1)),
            Face(label + ".south", [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1))]


def player_faces():
    out = []
    out += box_faces("player.head", -4, 4, -8, 0, -4, 4)
    out += box_faces("player.hat", -4.5, 4.5, -8.5, 0.5, -4.5, 4.5)
    out += box_faces("player.body", -4, 4, 0, 12, -2, 2)
    out += box_faces("player.jacket", -4.25, 4.25, -0.25, 12.25, -2.25, 2.25)
    for side, px in (("right", -5.0), ("left", 5.0)):
        x0 = -3.0 if side == "right" else -1.0
        out += box_faces(f"player.{side}_arm", x0, x0 + 4, -2, 10, -2, 2, (px, 2, 0))
        out += box_faces(f"player.{side}_sleeve", x0 - 0.25, x0 + 4.25, -2.25, 10.25, -2.25, 2.25, (px, 2, 0))
    for side, px in (("right", -1.9), ("left", 1.9)):
        out += box_faces(f"player.{side}_leg", -2, 2, 0, 12, -2, 2, (px, 12, 0))
    return out


def model_faces(model, tag):
    out = []
    for part, c, face, _, posed, _, _, pn in model.faces():
        out.append(Face(f"{tag}.{c[0]}.{face}", posed, pn))
    return out


def check_zfighting():
    faces = model_faces(TV, "tv") + model_faces(JP, "jetpack")
    body = player_faces()
    labels = {f.label for f in body}
    hits = [h for h in fights(faces + body) if not (h[0] in labels and h[1] in labels)]
    if hits:
        for h in hits:
            print("z-fight:", h)
        raise SystemExit(f"tv/jetpack: {len(hits)} coplanar overlapping face pairs")
    print(f"tv_helmet: {sum(len(p.cubes) for p in TV.parts)} cubes, soda_jetpack: {len(JP_CUBES)} cubes; no z-fighting")


check_zfighting()

# ================================================================================================== Java
write_java(TV_JAVA, TV.java([]))
write_java(JP_JAVA, JP.java([]))

# ================================================================================================== item sprites


def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    return [(palette[ch] + (255,)) if ch != "." else (0, 0, 0, 0) for r in rows for ch in r]


TV_SPRITE = sprite([
    "..B..........B..",
    "...A........A...",
    "....A......A....",
    ".....A....A.....",
    "......AggA......",
    ".OOOOOOOOOOOOOO.",
    ".OLLLLLLLLLLLLO.",
    ".OWGGGGGGGGWWWO.",
    ".OWGSFSSFSGWKWO.",
    ".OWGSSSSSSGWkWO.",
    ".OWGFSSSSFGWKWO.",
    ".OWGSFFFFSGWkWO.",
    ".OWGGGGGGGGWRWO.",
    ".OwwwwwwwwwwwwO.",
    ".OOOOOOOOOOOOOO.",
    "..OO........OO..",
], {"O": (58, 34, 20), "W": (150, 96, 56), "w": (116, 72, 40), "L": (186, 128, 80), "G": GREYS[2], "S": (22, 38, 58),
    "F": PHOSPHOR, "K": CHROME[1], "k": CHROME[3], "A": CHROME[1], "B": CHROME[0], "g": GREYS[1], "R": (236, 56, 48)})
JP_SPRITE = sprite([
    "................",
    "...OOO....OOO...",
    "..OcCHO..OgGhO..",
    "..OcCHO..OgGhO..",
    ".OTTTTTTTTTTTTO.",
    "..ORRWO..OYYyO..",
    "..ORRRO..OYYYO..",
    ".OtTTTTTTTTTTtO.",
    "..OcCHO..OgGhO..",
    "...OcO....OgO...",
    "...OPO....OBO...",
    "....OOOkkOOO....",
    "......OFFO......",
    ".....OFFFfO.....",
    "....OFFFFFfO....",
    "....OOOOOOOO....",
], {"O": (40, 28, 22), "c": (70, 34, 16), "C": (112, 56, 28), "H": (200, 150, 110), "g": (44, 112, 52),
    "G": (88, 170, 92), "h": (180, 236, 180), "T": (176, 178, 184), "t": (120, 122, 128), "R": (204, 34, 38),
    "W": (250, 246, 240), "Y": (230, 196, 54), "y": (60, 150, 66), "P": (196, 36, 36), "B": (52, 104, 200),
    "k": (36, 36, 40), "F": (240, 140, 44), "f": (186, 86, 20)})
FIZZ_SPRITE = sprite([
    "................",
    "......OOOO......",
    "......OPpO......",
    ".......OO.......",
    "......OwwO......",
    ".....OwbbwO.....",
    "....OwCbCCwO....",
    "....OHCCbCcO....",
    "....OLLLLLLO....",
    "....OLlLLlLO....",
    "....OLLLLLLO....",
    "....OHCbCCcO....",
    "....OHCCCbcO....",
    "....OwCCCCcO....",
    ".....OOOOOO.....",
    "................",
], {"O": (40, 30, 26), "P": (210, 46, 40), "p": (150, 26, 24), "w": (214, 226, 230), "b": (238, 228, 200),
    "C": (120, 62, 30), "c": (82, 40, 18), "H": (196, 140, 96), "L": (110, 196, 90), "l": (244, 244, 236)})
png(os.path.join(ASSETS, "textures", "item", "tv_helmet.png"), 16, 16, TV_SPRITE)
png(os.path.join(ASSETS, "textures", "item", "soda_jetpack.png"), 16, 16, JP_SPRITE)
png(os.path.join(ASSETS, "textures", "item", "fizz_charge.png"), 16, 16, FIZZ_SPRITE)


# ================================================================================================== data
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def merge(path, entries):
    with open(path) as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


def tag_add(path, entry):
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


for item in ("tv_helmet", "soda_jetpack", "fizz_charge"):
    write(os.path.join(ASSETS, "models", "item", f"{item}.json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": f"mymod:item/{item}"})]))
    write(os.path.join(ASSETS, "items", f"{item}.json"), {"model": {"type": "minecraft:model", "model": f"mymod:item/{item}"}})


def advancement(category, recipe, criteria):
    crit = OrderedDict((f"has_{item.split(':')[1]}", {"conditions": {"items": [{"items": item}]},
                                                      "trigger": "minecraft:inventory_changed"}) for item in criteria)
    crit["has_the_recipe"] = {"conditions": {"recipes": f"mymod:{recipe}"}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, "mymod", "advancement", "recipes", category, f"{recipe}.json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", crit), ("requirements", [list(crit.keys())]),
        ("rewards", {"recipes": [f"mymod:{recipe}"]})]))


# The TV Helmet: a Cathode Tube behind a glass pane, copper wiring and redstone, in a wooden cabinet.
write(os.path.join(DATA, "mymod", "recipe", "tv_helmet.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "equipment"),
    ("key", OrderedDict([("C", "minecraft:copper_ingot"), ("G", "minecraft:glass_pane"), ("P", "#minecraft:planks"),
                         ("R", "minecraft:redstone"), ("T", "mymod:cathode_tube")])),
    ("pattern", ["CRC", "GTG", "PPP"]),
    ("result", OrderedDict([("count", 1), ("id", "mymod:tv_helmet")]))]))
advancement("combat", "tv_helmet", ["mymod:cathode_tube"])
# The Fizz Charge: sugar and slime shaken up in a bottle.
write(os.path.join(DATA, "mymod", "recipe", "fizz_charge.json"), OrderedDict([
    ("type", "minecraft:crafting_shapeless"), ("category", "misc"),
    ("ingredients", ["minecraft:sugar", "minecraft:sugar", "minecraft:slime_ball", "minecraft:glass_bottle"]),
    ("result", OrderedDict([("count", 1), ("id", "mymod:fizz_charge")]))]))
advancement("misc", "fizz_charge", ["minecraft:slime_ball", "mymod:soda_jetpack"])
# Helmet enchantments go on through head_armor.
tag_add(os.path.join(DATA, "minecraft", "tags", "item", "head_armor.json"), "mymod:tv_helmet")


# Sounds: vanilla sounds, repitched.
def ev(subtitle, *sounds):
    return OrderedDict([("subtitle", "subtitles.mymod." + subtitle),
                        ("sounds", [OrderedDict([("name", "minecraft:" + n)] + list(extra.items())) for n, extra in sounds])])


def many(base, count, **extra):
    return [(f"{base}{i}", extra) for i in range(1, count + 1)]


merge(SOUNDS, OrderedDict([
    ("tv_helmet_toggle", ev("tv_helmet_toggle", ("random/click", {"pitch": 1.3, "volume": 0.6}),
                            ("block/copper_bulb/toggle", {"pitch": 1.5, "volume": 0.5}))),
    ("tv_helmet_channel", ev("tv_helmet_channel", ("random/click_stereo", {"pitch": 1.8, "volume": 0.4}),
                             ("random/fizz", {"pitch": 2.0, "volume": 0.15}))),
    ("soda_jetpack_thrust", ev("soda_jetpack_thrust", ("block/bubble_column/upwards_inside", {"pitch": 1.6, "volume": 0.7}),
                               ("random/fizz", {"pitch": 1.8, "volume": 0.35}),
                               *many("block/bubble_column/bubble", 3, pitch=1.4, volume=0.6))),
    ("soda_jetpack_sputter", ev("soda_jetpack_sputter", ("random/pop", {"pitch": 0.6, "volume": 0.7}),
                                *many("block/candle/extinguish", 3, pitch=1.4), *many("mob/slime/small", 5, pitch=1.6, volume=0.6))),
    ("soda_jetpack_spin", ev("soda_jetpack_spin", ("block/bubble_column/whirlpool_inside", {"pitch": 1.5}))),
    ("soda_jetpack_flat", ev("soda_jetpack_flat", *many("item/bottle/empty", 2, pitch=0.6), ("random/fizz", {"pitch": 0.5, "volume": 0.6}))),
    ("soda_jetpack_shake", ev("soda_jetpack_shake", *many("item/bundle/drop_contents", 3, pitch=1.5, volume=0.5),
                              *many("item/bottle/fill", 4, pitch=1.7, volume=0.5))),
    ("soda_jetpack_charged", ev("soda_jetpack_charged", ("random/pop", {"pitch": 1.2}), ("random/fizz", {"pitch": 1.5, "volume": 0.5}))),
    ("soda_jetpack_refuel", ev("soda_jetpack_refuel", *many("item/bottle/fill", 4, pitch=1.3, volume=0.6))),
    ("soda_jetpack_crash", ev("soda_jetpack_crash", *many("block/decorated_pot/shatter", 5, pitch=1.6, volume=0.6),
                              ("random/pop", {"pitch": 0.5}))),
    ("fizz_charge_burp", ev("fizz_charge_burp", ("random/burp", {"pitch": 1.4}))),
]))

merge(LANG, OrderedDict([
    ("item.mymod.tv_helmet", "Cathode TV Helmet"),
    ("item.mymod.tv_helmet.screen", "Your face is a screen: pick emotes with the Emote Wheel key"),
    ("item.mymod.tv_helmet.vision", "Static night vision, toggled with the TV Night Vision key"),
    ("item.mymod.tv_helmet.water", "Heavy, and shorts out in water and rain"),
    ("item.mymod.soda_jetpack", "Soda-Bottle Jetpack"),
    ("item.mymod.soda_jetpack.fly", "Hold Jump in the air for weak, wobbly thrust"),
    ("item.mymod.soda_jetpack.fuel", "Burns sugar (thrust) and slime (longer bursts), or Fizz Charges"),
    ("item.mymod.soda_jetpack.shake", "Gone flat? Sneak and hold Use, hands empty, to shake it"),
    ("item.mymod.fizz_charge", "Fizz Charge"),
    ("item.mymod.fizz_charge.fuel", "Jetpack fuel: sugar and slime in one, burns longest"),
    ("key.category.mymod.tv_helmet", "My Mod: TV Helmet"),
    ("key.mymod.tv_emote", "Emote Wheel (tap: next)"),
    ("key.mymod.tv_night_vision", "TV Night Vision"),
    ("tv_helmet.mymod.emote.happy", "Happy"),
    ("tv_helmet.mymod.emote.laugh", "XD"),
    ("tv_helmet.mymod.emote.angry", "Angry"),
    ("tv_helmet.mymod.emote.sad", "Sad"),
    ("tv_helmet.mymod.emote.surprised", "Surprised"),
    ("tv_helmet.mymod.emote.cat", ":3"),
    ("tv_helmet.mymod.emote.cool", "Cool"),
    ("tv_helmet.mymod.emote.skull", "Skull"),
    ("tv_helmet.mymod.emote.heart", "Heart"),
    ("tv_helmet.mymod.emote.question", "?"),
    ("tv_helmet.mymod.emote.bars", "Colour Bars"),
    ("tv_helmet.mymod.emote.static", "Static"),
    ("tv_helmet.mymod.night_vision.on", "TV night vision: on"),
    ("tv_helmet.mymod.night_vision.off", "TV night vision: off"),
    ("tv_helmet.mymod.shorted", "Your TV shorted out!"),
    ("tv_helmet.mymod.wheel.hint", "Release to pick"),
    ("soda_jetpack.mymod.flat", "Your jetpack went flat! Sneak and hold Use (hands empty) to shake it"),
    ("soda_jetpack.mymod.charged", "Jetpack pressurised!"),
    ("soda_jetpack.mymod.empty", "Out of fizz: carry sugar, slime or Fizz Charges"),
    ("hud.mymod.soda_jetpack.flat", "FLAT"),
    ("hud.mymod.soda_jetpack.shake", "SHAKE!"),
    ("subtitles.mymod.tv_helmet_toggle", "TV clicks"),
    ("subtitles.mymod.tv_helmet_channel", "TV changes channel"),
    ("subtitles.mymod.soda_jetpack_thrust", "Jetpack fizzes"),
    ("subtitles.mymod.soda_jetpack_sputter", "Jetpack sputters"),
    ("subtitles.mymod.soda_jetpack_spin", "Jetpack spins out"),
    ("subtitles.mymod.soda_jetpack_flat", "Jetpack goes flat"),
    ("subtitles.mymod.soda_jetpack_shake", "Bottles rattle"),
    ("subtitles.mymod.soda_jetpack_charged", "Jetpack pops"),
    ("subtitles.mymod.soda_jetpack_refuel", "Jetpack glugs"),
    ("subtitles.mymod.soda_jetpack_crash", "Jetpack crashes"),
    ("subtitles.mymod.fizz_charge_burp", "Burp"),
]))


# ================================================================================================== preview
STEVE_TEX = STEVE
BG, GROUND = (44, 50, 60), (70, 86, 58)


def skin_sampler(rect, layer_alpha=False):
    ua, va, ub, vb = rect
    w, h, px = STEVE_TEX

    def sample(s, t):
        u, v = ua + (ub - ua) * s, va + (vb - va) * t
        c = px[min(h - 1, int(v)) * w + min(w - 1, int(u))]
        return c if c[3] else None
    return sample


def tex_sampler(tex, tw, rect):
    ua, va, ub, vb = rect

    def sample(s, t):
        u, v = ua + (ub - ua) * s, va + (vb - va) * t
        c = tex[min(len(tex) // tw - 1, int(v * SCALE)) * tw + min(tw - 1, int(u * SCALE))]
        return c if c[3] else None
    return sample


def steve_quads(poses):
    """Steve's skin (base layer) on a figure posed by poses: part name -> (pivot, rot)."""
    parts = {"head": ((0, 0, 0), (-4, -8, -4, 8, 8, 8), (0, 0)), "body": ((0, 0, 0), (-4, 0, -2, 8, 12, 4), (16, 16)),
             "right_arm": ((-5, 2, 0), (-3, -2, -2, 4, 12, 4), (40, 16)), "left_arm": ((5, 2, 0), (-1, -2, -2, 4, 12, 4), (32, 48)),
             "right_leg": ((-1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 16)), "left_leg": ((1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (16, 48))}
    out = []
    for name, (pivot, (x, y, z, w, h, d), uv) in parts.items():
        pv, rot = poses.get(name, (pivot, (0, 0, 0)))
        m = part_matrix(rot)
        c = (name, "skin", x, x + w, y, y + h, z, z + d)
        for face, verts, posed, rect, n in cube_polys(c, uv, pv, m):
            out.append((posed, skin_sampler(rect), mvec(m, n), False))
    return out


def model_quads(model, tex, poses, emissive=None):
    tw = model.tex_w * SCALE
    out = []
    for part, c, face, verts, posed, rect, n, pn in model.faces(poses):
        out.append((posed, tex_sampler(tex, tw, rect), pn, False))
        if emissive is not None:
            out.append((posed, tex_sampler(emissive, tw, rect), pn, True))
    return out


def figure(poses=None, frame="happy", jetpack=True, helmet=True, shake=0.0):
    poses = poses or {}
    quads = steve_quads(poses)
    if helmet:
        quads += model_quads(TV, TV_TEXTURE, {"head": poses.get("head", ((0, 0, 0), (0, 0, 0)))}, FRAMES[frame])
    if jetpack:
        body = poses.get("body", ((0, 0, 0), (0, 0, 0)))
        quads += model_quads(JP, JP_TEXTURE, {"body": ((body[0][0] + shake, body[0][1], body[0][2]), body[1])})
    return quads


def render(quads, yaw, scale_px, width, height, focus=(0.0, 6.0, 0.0), pitch=0.15, ground=24.0, bg=BG):
    """Orthographic, looking at the figure from yaw (0: from the front) and a little above."""
    ca, sa = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def view(p):
        # Turn the figure about y (yaw), then tip the camera down (pitch); x right, y down, depth away from camera.
        x = p[0] * ca - p[2] * sa
        z = p[0] * sa + p[2] * ca
        y = p[1] * cp - z * sp
        d = p[1] * sp + z * cp
        return x, y, d

    fx, fy, _ = view(focus)
    ox, oy = width / 2 - fx * scale_px, height / 2 - fy * scale_px
    img = [bg] * (width * height)
    zbuf = [1e9] * (width * height)
    if ground is not None:
        gy = view((0, ground, 0))[1] * scale_px + oy
        for y in range(max(0, int(gy)), height):
            for x in range(width):
                img[y * width + x] = GROUND
    light = unit((0.3, -1.0, -0.6))
    for verts, sample, n, glow in quads:
        nv = view(n)
        if nv[2] > 1e-6:
            continue
        shade_f = 1.0 if glow else 0.55 + 0.45 * max(0.0, dot(n, light))
        P = [view(p) for p in verts]
        P = [(ox + q[0] * scale_px, oy + q[1] * scale_px, q[2] - (0.02 if glow else 0.0)) for q in P]
        A, B, C = P[1], P[0], P[2]
        eu, ev = (B[0] - A[0], B[1] - A[1]), (C[0] - A[0], C[1] - A[1])
        det = eu[0] * ev[1] - eu[1] * ev[0]
        if abs(det) < 1e-9:
            continue
        xs, ys = [p[0] for p in P], [p[1] for p in P]
        for py in range(max(0, int(min(ys))), min(height, int(math.ceil(max(ys))) + 1)):
            for px in range(max(0, int(min(xs))), min(width, int(math.ceil(max(xs))) + 1)):
                qx, qy = px + 0.5 - A[0], py + 0.5 - A[1]
                s = (qx * ev[1] - qy * ev[0]) / det
                t = (eu[0] * qy - eu[1] * qx) / det
                if not (0 <= s <= 1 and 0 <= t <= 1):
                    continue
                depth = A[2] + (B[2] - A[2]) * s + (C[2] - A[2]) * t
                k = py * width + px
                if depth < zbuf[k] - 1e-6:
                    col = sample(min(s, 0.9999), min(t, 0.9999))
                    if col is None:
                        continue
                    zbuf[k] = depth
                    img[k] = tuple(int(col[i] * shade_f) for i in range(3))
    return img, width, height, (ox, oy, view, scale_px)


def disc(img, w, h, cx, cy, r, col, ring=None):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if 0 <= x < w and 0 <= y < h:
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if ring is not None and r - 1.2 <= d <= r:
                    img[y * w + x] = ring
                elif d <= r and ring is None:
                    img[y * w + x] = col


def particles(panel, nozzle, seed=7):
    """Foam, fizz and droplets spraying from the nozzle (body space -> screen)."""
    img, w, h, (ox, oy, view, sc) = panel
    nx, ny, _ = view(nozzle)
    nx, ny = ox + nx * sc, oy + ny * sc
    rnd = random.Random(seed)
    for k in range(26):
        dist = 6 + rnd.uniform(0.0, 1.0) ** 0.8 * 130
        x = nx + rnd.uniform(-0.35, 0.35) * dist * 0.6
        y = ny + dist
        kind = rnd.random()
        if kind < 0.45:
            disc(img, w, h, x, y, 3 + dist / 30, (236, 230, 214))
            disc(img, w, h, x - 1, y - 1, 1.5 + dist / 60, (255, 255, 250))
        elif kind < 0.75:
            disc(img, w, h, x, y, 3 + rnd.random() * 2, None, ring=(200, 230, 255))
        else:
            disc(img, w, h, x, y, 2, (112, 56, 28))
    return panel


def board_rows(rows, pad=6, bg=(30, 33, 40)):
    width = max(sum(p[1] for p in row) + pad * (len(row) + 1) for row in rows)
    height = sum(max(p[2] for p in row) + pad for row in rows) + pad
    out = [bg] * (width * height)
    y0 = pad
    for row in rows:
        x0 = pad
        for img, w, h, *_ in row:
            for y in range(h):
                for x in range(w):
                    out[(y0 + y) * width + x0 + x] = img[y * w + x]
            x0 += w + pad
        y0 += max(p[2] for p in row) + pad
    return [c + (255,) for c in out], width, height


def blow_up(px, w, h, k, bg=BG):
    out = [bg] * (w * k * h * k)
    for y in range(h * k):
        for x in range(w * k):
            c = px[(y // k) * w + x // k]
            if len(c) < 4 or c[3]:
                out[y * w * k + x] = tuple(int(v) for v in c[:3])
    return out, w * k, h * k


if PREVIEW:
    W, H = 300, 420
    rest = figure()
    views = [render(rest, a, 8.5, W, H, focus=(0, 8.0, 0)) for a in (0.0, math.pi / 2, math.pi / 5, math.pi * 0.8)]
    # In flight: legs dangling, arms flung out, a little tilt; foam spraying from the nozzle.
    flying = {"right_arm": ((-5, 2, 0), (-0.3, 0, 0.55)), "left_arm": ((5, 2, 0), (0.2, 0, -0.65)),
              "right_leg": ((-1.9, 12, 0), (0.25, 0, 0.08)), "left_leg": ((1.9, 12, 0), (-0.15, 0, -0.08)),
              "head": ((0, 0, 0), (0.15, 0.2, 0.0))}
    fly_quads = figure(flying, frame="laugh")
    flight = [particles(render(fly_quads, a, 7.0, W, H, focus=(0, 10.0, 0), ground=None, bg=(120, 170, 220)), NOZZLE, seed)
              for a, seed in ((math.pi * 0.62, 3), (math.pi * 0.85, 5), (math.pi * 1.0, 9))]
    shake = render(figure({"right_arm": ((-5, 2, 0), (-0.9, 0, 0.2)), "left_arm": ((5, 2, 0), (-0.9, 0, -0.2))}, frame="cool",
                          shake=0.6), math.pi * 0.75, 8.5, W, H, focus=(0, 8.0, 0))
    rows = [views, flight + [shake]]
    # Every screen frame, the helmet alone close up.
    frames = []
    for fname in FRAMES:
        q = model_quads(TV, TV_TEXTURE, {}, FRAMES[fname])
        frames.append(render(q, 0.35, 7.0, 170, 150, focus=(0.5, -6.0, -2.0), ground=None, pitch=0.2))
    for k in range(0, len(frames), 7):
        rows.append(frames[k:k + 7])
    # Close-ups of the jetpack from behind and the helmet from the side.
    jp_only = model_quads(JP, JP_TEXTURE, {})
    tv_only = model_quads(TV, TV_TEXTURE, {}, FRAMES["heart"])
    rows.append([render(jp_only, math.pi * 0.85, 14, 300, 300, focus=(0, 6.0, 4.0), ground=None, pitch=0.25),
                 render(jp_only, math.pi * 0.5, 14, 300, 300, focus=(0, 6.0, 2.0), ground=None, pitch=0.1),
                 render(tv_only, math.pi * 0.62, 11, 300, 300, focus=(0, -6.0, 1.5), ground=None, pitch=0.25),
                 render(tv_only, -0.5, 11, 300, 300, focus=(0, -6.0, -1.0), ground=None, pitch=-0.25)])
    # The wheel icons at 3x and the sprites at 1x and 8x.
    icons = []
    for emote in EMOTES:
        first = EMOTES[emote][0][1]
        px = [(DARK_GLASS[3] if (i, j) in DARK_CORNERS else first(i, j)) for j in range(CANVAS_H) for i in range(CANVAS_W)]
        icons.append(blow_up(px, CANVAS_W, CANVAS_H, 3))
    rows.append(icons[:12])
    sprites = []
    for spr in (TV_SPRITE, JP_SPRITE, FIZZ_SPRITE):
        sprites.append(blow_up(spr, 16, 16, 1))
        sprites.append(blow_up(spr, 16, 16, 8))
    rows.append(sprites)
    out, bw, bh = board_rows(rows)
    png(PREVIEW, bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
