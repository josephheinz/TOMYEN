#!/usr/bin/env python3
"""Fusion gear, batch 3 (com.jheinz.tomyen.fusion.gear and client/fusion/gear): the worn 3D models of the Astral Visor,
the Phantasmal Chestplate, the Wolfhide Carrier and the Revenant Mail, their textures and glow textures, their 16x16
item sprites (the carriers with a sprite per armor plate), item models and definitions.

Run from anywhere: python3 tools/gen_fusion_gear.py [--preview out.png]

The models are cubes in the humanoid model's own parts (head, body, arms; y down from the part's pivot, the front toward
-z, the wearer's right toward -x), so ArmorRenderer copies the wearer's pose onto them, as the TV Helmet and the Scrap
armor do. The chestplates come wide and slim-armed (the arm cubes squeezed onto a three-pixel arm). Every cube is a
whole number of half pixels on each side, so at the textures' two texels a pixel every face covers whole texels. The
cubes and texture offsets go into FusionGearModel.java between the GENERATED markers.

What each one is, as a fusion of what went in:
 * Astral Visor (Cathode TV Helmet + Starlight Lens): the TV's silhouette (cabinet, stepped bulging screen, tube
   housing out the back, rabbit ears) remade in night-blue lacquer with brass trim; its screen is a window on a violet
   nebula and stars (glowing), the knobs are gone for the Starlight Lens itself, a brass spyglass with a star crystal
   lens run out past the cabinet's right front, and the antennas end in glowing star crystals.
 * Phantasmal Chestplate (Ectoplasm + Holy Silver Chestplate): holy silver plate with its gold trim and cross, the
   cross's heart a glowing ectoplasm gem; every plate's lower edge dissolves into pale ghost-teal, and translucent
   ectoplasm drips from the breastplate and the tassets, wisps trail off the pauldrons and down the back, and a glowing
   seam runs down the spine.
 * Wolfhide Carrier (Werewolf Pelt + Plate Carrier): the carrier's olive plate pockets, webbing, flap and mag pouches,
   worn over the pelt: a shaggy fur mantle and wrap, fur pauldrons, a wolf's tail down the back, a necklace of bone
   claws, and the wolf's own head over the right shoulder with amber eyes that glow.
 * Revenant Mail (both): the phantasmal plate gone pewter and its gold gone brass, over a frost-tipped charcoal fur
   mantle, the carrier's webbing cummerbund and pouches round the waist, the wolf's head now bare bone with ghost-cyan
   eyes, ectoplasm seeping from the seams.

Textures are baked from where each texel sits (each material a short ramp, lit from above, a bevel on each face's
edges, fur in regular locks with ragged cut-out tips, a soft sheen across metal); no noise. Glow textures (drawn with
the eyes render type: full bright, alpha blended) hold the ectoplasm, gems, eyes, stars and crystals; ectoplasm wisps are
only in the glow texture, so they're see-through.

Z-fighting: no two faces may share a plane, face the same way and overlap (tools/coplanar.py), across each piece worn
with the other (visor with each chestplate), over the player's skin and outer layer (wide and slim), and against
vanilla leggings and boots. The generator stops if any do.

The preview (--preview) shows each piece on Steve from the front, three quarters, the side and behind, a walking pose,
the glow alone in the dark, the item sprites and the textures."""
import json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
PREVIEW = os.path.abspath(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None
sys.argv = sys.argv[:1]
from gen_longswords import client_jar, read_png  # noqa: E402
from gunkit import png  # noqa: E402
from coplanar import Face, fights  # noqa: E402
import zipfile  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tomyen")
JAVA = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client", "fusion", "gear", "FusionGearModel.java")
TEX_DIR = os.path.join(ASSETS, "textures", "entity", "fusion_gear")
SCALE = 2
JAR = zipfile.ZipFile(client_jar())


# ================================================================================================== maths
def unit(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def ramp(cols, t):
    """cols light to dark; t 0 (light) .. 1 (dark)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


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
    x, y, z = rot
    return mmul(rot_z(z), mmul(rot_y(y), rot_x(x)))


# ================================================================================================== model machinery
# (as tools/gen_tv_jetpack.py: a cube is (name, material, x0, x1, y0, y1, z0, z1) in its part's space)
HUMANOID_PIVOTS = {"head": (0.0, 0.0, 0.0), "body": (0.0, 0.0, 0.0), "right_arm": (-5.0, 2.0, 0.0), "left_arm": (5.0, 2.0, 0.0),
                   "right_leg": (-1.9, 12.0, 0.0), "left_leg": (1.9, 12.0, 0.0)}


class Part:
    def __init__(self, name, cubes, parent=None, offset=None, rot=(0.0, 0.0, 0.0)):
        self.name, self.cubes, self.parent, self.rot = name, cubes, parent, rot
        self.offset = offset if offset is not None else HUMANOID_PIVOTS.get(name, (0.0, 0.0, 0.0))


def size(c):
    return (c[3] - c[2], c[5] - c[4], c[7] - c[6])


def pack(parts, tex_w, tex_h):
    cubes = [c for p in parts for c in p.cubes]
    names = [c[0] for c in cubes]
    assert len(names) == len(set(names)), "cube names must be unique: %s" % sorted(n for n in names if names.count(n) > 1)
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
    """The six faces as ModelPart.Cube builds them: (face, part-space corners, posed corners, uv rect, normal)."""
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


def face_texels(c, face):
    """(width, height) of a face in texels, along s and t."""
    w, h, d = size(c)
    return {"top": (w, d), "bottom": (w, d), "west": (d, h), "east": (d, h), "north": (w, h), "south": (w, h)}[face]


class Model:
    def __init__(self, name, parts, tex_w, tex_h):
        self.name, self.parts, self.tex_w, self.tex_h = name, parts, tex_w, tex_h
        self.uv = pack(parts, tex_w, tex_h)

    def pose(self, part, root_poses=None):
        root_poses = root_poses or {}
        if part.parent is None:
            pivot, rot = root_poses.get(part.name, (part.offset, part.rot))
            return pivot, part_matrix(rot)
        parent = next(p for p in self.parts if p.name == part.parent)
        po, pm = self.pose(parent, root_poses)
        origin = tuple(po[i] + mvec(pm, part.offset)[i] for i in range(3))
        return origin, mmul(pm, part_matrix(part.rot))

    def faces(self, root_poses=None):
        out = []
        for part in self.parts:
            origin, m = self.pose(part, root_poses)
            for c in part.cubes:
                for face, verts, posed, rect, n in cube_polys(c, self.uv[c[0]], origin, m):
                    out.append((part, c, face, verts, posed, rect, n, mvec(m, n)))
        return out

    def bake(self, painter):
        """painter(part, cube, face, point, normal, s, t) -> rgb / rgba / None, at each texel's centre."""
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
                    col = painter(part, c, face, p, n, s, t)
                    if col is None:
                        continue
                    alpha = col[3] if len(col) > 3 else 255
                    if alpha <= 0:
                        continue
                    tex[ty * w + tx] = tuple(int(round(max(0, min(255, k)))) for k in col[:3]) + (int(alpha),)
        return tex

    def java(self, method, doc):
        lines = [f"\t/** {doc} */", f"\tpublic static LayerDefinition {method}() {{",
                 "\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
        mine = {p.name: p for p in self.parts if p.parent is None}
        children = [p for p in self.parts if p.parent is not None]

        def builder(part):
            b = "CubeListBuilder.create()"
            for c in part.cubes if part else []:
                u, v = self.uv[c[0]]
                w, h, d = size(c)
                b += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({jf(c[2])}, {jf(c[4])}, {jf(c[6])}, {jf(w)}, {jf(h)}, {jf(d)})"
            return b

        def pose_of(part, name):
            if part is not None and part.parent is not None:
                o, r = part.offset, part.rot
                if any(r):
                    return f"PartPose.offsetAndRotation({jf(o[0])}, {jf(o[1])}, {jf(o[2])}, {jf(r[0])}, {jf(r[1])}, {jf(r[2])})"
                return f"PartPose.offset({jf(o[0])}, {jf(o[1])}, {jf(o[2])})"
            o = HUMANOID_PIVOTS.get(name, (0.0, 0.0, 0.0))
            return "PartPose.ZERO" if o == (0.0, 0.0, 0.0) else f"PartPose.offset({jf(o[0])}, {jf(o[1])}, {jf(o[2])})"

        for name in ["head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"]:
            part = mine.get(name)
            parent = "head" if name == "hat" else "root"
            var = name in ("head",) or any(ch.parent == name for ch in children)
            text = f"{parent}.addOrReplaceChild(\"{name}\", {builder(part)}, {pose_of(part, name)});"
            lines.append(("\t\tPartDefinition %s = " % name if var else "\t\t") + text)
            for ch in children:
                if ch.parent == name:
                    lines.append(f"\t\t{name}.addOrReplaceChild(\"{ch.name}\", {builder(ch)}, {pose_of(ch, ch.name)});")
        lines.append(f"\t\treturn LayerDefinition.create(mesh, {self.tex_w}, {self.tex_h});")
        lines.append("\t}")
        return "\n".join(lines)


def jf(v):
    v = round(v, 6)
    return f"{v:.1f}f" if v == int(v) else f"{v:g}f"


# ================================================================================================== palettes
# Light to dark. Silver and gold are holy silver's own (its chestplate sprite and armor layer); the ectoplasm is the
# Ectoplasm sprite's; fur the Werewolf Pelt's; olive the Plate Carrier's; brass and crystal the Starlight Lens and star
# crystal's family.
SILVER = [(255, 255, 255), (246, 246, 242), (228, 229, 226), (198, 200, 204), (150, 156, 168)]
GOLD = [(255, 236, 140), (250, 214, 92), (226, 170, 52), (178, 104, 17), (122, 66, 10)]
ECTO = [(236, 255, 250), (184, 240, 234), (128, 214, 212), (76, 170, 176), (34, 92, 104)]
GHOST = (198, 246, 238)
FUR = [(146, 138, 128), (122, 113, 104), (96, 91, 90), (77, 73, 74), (52, 49, 52)]
FUR_DARK = [(118, 116, 122), (90, 88, 94), (66, 64, 70), (48, 46, 52), (30, 29, 34)]
FROST = (206, 222, 232)
OLIVE = [(132, 146, 84), (116, 128, 74), (91, 102, 51), (66, 74, 36), (42, 47, 23)]
BONE = [(250, 246, 232), (232, 224, 200), (204, 192, 162), (164, 150, 120), (112, 100, 78)]
PEWTER = [(170, 182, 202), (132, 144, 168), (100, 110, 136), (72, 80, 104), (44, 50, 68)]
BRASS = [(255, 232, 150), (230, 186, 96), (190, 140, 60), (138, 94, 40), (86, 56, 26)]
LACQUER = [(92, 96, 170), (64, 64, 132), (44, 42, 100), (30, 28, 72), (18, 16, 44)]
STARGLASS = [(40, 34, 84), (28, 24, 64), (18, 16, 46), (12, 10, 30), (6, 6, 18)]
CRYSTAL = [(236, 255, 255), (164, 236, 249), (99, 208, 238), (88, 102, 205), (76, 52, 168)]
LEATHER = [(150, 104, 64), (124, 82, 48), (98, 64, 38), (72, 46, 28), (48, 30, 18)]
AMBER = (255, 186, 52)
CYAN_EYE = (150, 255, 248)

FACE_LIGHT = {"top": 0.12, "north": 0.32, "west": 0.42, "east": 0.42, "south": 0.5, "bottom": 0.72}


def name_phase(name):
    return (sum(ord(ch) * (i + 3) for i, ch in enumerate(name)) % 97) / 97.0


def edges(c, face, s, t):
    """Texel distances to the face's edges: (left, right, top, bottom)."""
    fw, fh = face_texels(c, face)
    W, H = fw * SCALE, fh * SCALE
    return s * W, (1 - s) * W, t * H, (1 - t) * H


def bevel(c, face, s, t, k=0.14):
    l, r, tp, b = edges(c, face, s, t)
    v = 0.0
    if tp < 1:
        v -= k
    elif b < 1:
        v += k
    if l < 1 or r < 1:
        v += k * 0.4
    return v


def metal(ramp_, c, face, p, s, t, trim=None, fade=None):
    """Plate metal: lit from above, a soft diagonal sheen on the front, a bevel; trim on the face's border."""
    v = FACE_LIGHT[face] + bevel(c, face, s, t)
    if face in ("north", "south", "east", "west"):
        v += 0.22 * (t - 0.5)
        sheen = max(0.0, 1.0 - abs((s * 0.7 + t) - 0.62) / 0.22)
        v -= 0.2 * sheen
    l, r, tp, b = edges(c, face, s, t)
    use = ramp_
    if trim is not None and face not in ("top", "bottom") and min(l, r, tp, b) < 1:
        use = trim
        v -= 0.05
    col = ramp(use, v)
    if fade is not None and face != "top":
        y0, y1 = c[4], c[5]
        k = smooth(0.5, 1.0, (p[1] - y0) / (y1 - y0)) if face != "bottom" else 1.0
        col = lerp(col, fade, 0.55 * k)
    return col


def fur(ramp_, c, face, p, s, t, lock=False, frost=None):
    """Fur: regular locks across the face, darker at the roots, lighter at the tips; lock cubes end in a ragged edge."""
    ph = name_phase(c[0])
    fw, fh = face_texels(c, face)
    sx = s * fw * SCALE
    v = FACE_LIGHT[face] * 0.8 + 0.12
    if face in ("north", "south", "east", "west"):
        v += 0.1 * math.cos(2 * math.pi * (sx / 3.0 + ph)) - 0.28 * (t - 0.5)
        if lock and (1 - t) * fh * SCALE < 1.0 and int(sx + ph * 3) % 2 == 0:
            return None
    elif face == "top":
        v += 0.06 * math.cos(2 * math.pi * (sx / 3.0 + t * 1.5 + ph))
    col = ramp(ramp_, v)
    if frost is not None and face in ("north", "south", "east", "west"):
        col = lerp(col, frost, 0.5 * smooth(0.55, 1.0, t))
    return col


def olive(c, face, s, t, webbing=False):
    v = FACE_LIGHT[face] + bevel(c, face, s, t, 0.12) + 0.1
    if webbing and face in ("north", "south"):
        fw, fh = face_texels(c, face)
        row = int(t * fh * SCALE)
        if 3 <= row < fh * SCALE - 2:
            k = (row - 3) % 4
            v += (0.18, -0.06, 0.0, 0.22)[k]
            col = int(s * fw * SCALE)
            if k == 1 and col % 4 == 1:
                v += 0.18
    return ramp(OLIVE, v)


def gem_base(c, face, s, t, ramp_=ECTO):
    d = math.hypot(s - 0.35, t - 0.35)
    return ramp(ramp_, 0.35 + d * 0.9 + (0.25 if face != "north" else 0.0))


def gem_glow(c, face, s, t, ramp_=ECTO, alpha=220):
    d = math.hypot(s - 0.4, t - 0.4)
    if face == "north":
        return ramp(ramp_, 0.1 + d * 0.8) + (int(alpha * (1.0 - 0.5 * d)),)
    return ramp(ramp_, 0.5) + (int(alpha * 0.45),)


def wisp(c, face, t, base=ECTO, alpha=170):
    """Ectoplasm: only in the glow texture, so it's see-through; strongest where it leaves the plate."""
    if face == "top":
        return ramp(base, 0.2) + (alpha,)
    if face == "bottom":
        return ramp(base, 0.5) + (int(alpha * 0.4),)
    k = 1.0 - smooth(0.0, 1.0, t)
    return ramp(base, 0.15 + 0.5 * (1 - k)) + (int(40 + (alpha - 40) * k),)


def skull_detail(c, face, s, t, base, eyes):
    """The wolf's eyes (two texels each on the skull's front) and teeth (along the snout's lower front edge)."""
    fw, fh = face_texels(c, face)
    x, y = int(s * fw * SCALE), int(t * fh * SCALE)
    if c[0].endswith("skull") and face == "north" and y in (2, 3) and x in (1, 2, 5, 6):
        return "eye"
    if c[0].endswith("snout") and face == "north" and y == fh * SCALE - 1 and x % 2 == 0:
        return "tooth"
    return None


# ================================================================================================== the pieces
def mirror(cubes, suffix="_l"):
    """A right-arm cube list as the left arm's: x flipped."""
    return [(c[0] + suffix, c[1], -c[3], -c[2], c[4], c[5], c[6], c[7]) for c in cubes]


def slim(cubes, side):
    """Arm cubes squeezed onto a slim arm (as tools/gen_scrap_armor.py): the outer side in a pixel, the inner side
    kept, what's between scaled, rounded to whole half pixels about its middle."""
    def squeeze(x):
        if side == "right":
            return x + 1.0 if x <= -3.0 else x if x >= 1.0 else -2.0 + (x + 3.0) * 0.75
        return x - 1.0 if x >= 3.0 else x if x <= -1.0 else -1.0 + (x + 1.0) * 0.75
    out = []
    for c in cubes:
        a, b = squeeze(c[2]), squeeze(c[3])
        mid, w = (a + b) / 2.0, max(0.5, round((b - a) * 2.0) / 2.0)
        mid = round(mid * 4) / 4
        out.append((c[0], c[1], mid - w / 2.0, mid + w / 2.0) + tuple(c[4:]))
    return out


# --- The Phantasmal Chestplate --------------------------------------------------------------------------------
PH_BODY = [
    ("cuirass", "silver", -4.75, 4.75, -0.75, 9.25, -2.75, 2.75),
    ("breastplate", "silver_trim", -4.25, 4.25, 0.25, 7.25, -3.25, -2.75),
    ("keel", "silver", -0.75, 0.75, 0.75, 6.75, -3.5, -3.0),
    ("setting", "gold", -1.75, 1.75, 2.25, 5.25, -3.75, -3.25),
    ("gem", "gem", -1.0, 1.0, 2.75, 4.75, -4.25, -3.75),
    ("cross_r", "gold", -3.25, -1.75, 3.25, 4.25, -3.5, -3.0),
    ("cross_l", "gold", 1.75, 3.25, 3.25, 4.25, -3.5, -3.0),
    ("backplate", "silver_trim", -4.25, 4.25, 0.25, 8.25, 2.75, 3.25),
    ("spine", "gem", -0.5, 0.5, 1.25, 7.75, 3.25, 3.75),
    ("fauld_upper", "silver_trim", -4.75, 4.75, 9.25, 10.75, -3.0, 3.0),
    ("fauld_lower", "silver_trim", -5.0, 5.0, 10.75, 11.75, -3.25, 3.25),
    ("drip_1", "wisp", -3.25, -2.75, 7.25, 9.25, -3.25, -2.75),
    ("drip_2", "wisp", -1.5, -1.0, 7.25, 8.25, -3.25, -2.75),
    ("drip_3", "wisp", 1.25, 1.75, 7.25, 9.75, -3.25, -2.75),
    ("drip_4", "wisp", 2.75, 3.25, 7.25, 8.25, -3.25, -2.75),
    ("drip_5", "wisp", -2.25, -1.75, 11.75, 13.25, -3.25, -2.75),
    ("drip_6", "wisp", 2.0, 2.5, 11.75, 12.75, -3.25, -2.75),
    ("trail_1", "wisp", -3.0, -2.0, 8.25, 11.25, 3.25, 3.75),
    ("trail_2", "wisp", 1.5, 2.5, 8.25, 12.75, 3.25, 3.75),
    ("trail_3", "wisp", -0.5, 0.5, 11.75, 14.25, 2.75, 3.25),
]
PH_ARM = [
    ("pauldron_top", "silver_trim", -4.25, 1.75, -3.25, -0.75, -3.0, 3.0),
    ("pauldron_mid", "silver_trim", -4.75, 1.25, -1.25, 0.75, -3.25, 3.25),
    ("pauldron_low", "silver_trim", -4.25, 0.75, 0.25, 2.25, -2.75, 2.75),
    ("stud", "gem", -5.25, -4.75, -0.75, 0.25, -0.5, 0.5),
    ("vambrace", "silver_trim", -3.5, 1.5, 5.25, 8.75, -2.5, 2.5),
    ("vam_gem", "gem", -4.0, -3.5, 6.5, 7.5, -0.5, 0.5),
    ("arm_wisp_1", "wisp", -4.75, -4.25, 0.75, 3.75, -2.0, -1.5),
    ("arm_wisp_2", "wisp", -4.75, -4.25, 0.75, 4.75, 0.5, 1.0),
    ("arm_wisp_3", "wisp", -3.0, -2.0, 8.75, 10.75, -2.5, -2.0),
]

# --- The Wolfhide Carrier -------------------------------------------------------------------------------------
WOLF_HEAD = [
    ("skull", "fur_dark", -2.0, 2.0, -3.0, 0.0, -2.0, 1.5),
    ("snout", "fur_dark", -1.25, 1.25, -1.5, 0.0, -4.0, -2.0),
    ("nose", "nose", -0.5, 0.5, -1.5, -1.0, -4.5, -4.0),
    ("jaw", "fur_dark", -1.0, 1.0, 0.0, 0.5, -3.5, -1.0),
    ("ear_r", "fur_dark", -2.0, -1.0, -4.5, -3.0, -0.5, 0.5),
    ("ear_l", "fur_dark", 1.0, 2.0, -4.5, -3.0, -0.5, 0.5),
    ("ruff", "fur", -2.5, 2.5, -2.5, 1.0, 1.5, 3.0),
]
WH_BODY = [
    ("fur_wrap", "fur", -4.75, 4.75, 1.75, 11.75, -2.75, 2.75),
    ("front_panel", "olive_web", -3.75, 3.75, 1.25, 9.75, -3.25, -2.25),
    ("back_panel", "olive_web", -3.75, 3.75, 0.75, 9.25, 2.25, 3.25),
    ("mantle", "fur", -5.25, 5.25, -0.75, 1.75, -3.5, 3.5),
    ("pocket_flap", "olive", -3.25, 3.25, 1.75, 3.25, -3.75, -3.25),
    ("pouch_r", "olive", -3.25, -1.25, 6.25, 9.25, -4.25, -3.25),
    ("pouch_m", "olive", -1.0, 1.0, 6.25, 9.25, -4.25, -3.25),
    ("pouch_l", "olive", 1.25, 3.25, 6.25, 9.25, -4.25, -3.25),
    ("claw_1", "bone", -2.25, -1.75, 3.25, 4.75, -3.75, -3.25),
    ("claw_2", "bone", -0.25, 0.25, 3.25, 5.25, -3.75, -3.25),
    ("claw_3", "bone", 1.75, 2.25, 3.25, 4.75, -3.75, -3.25),
    ("lock_fr", "fur_lock", -4.75, -3.75, 1.75, 3.75, -4.0, -3.0),
    ("lock_fl", "fur_lock", 3.75, 4.75, 1.75, 3.75, -4.0, -3.0),
    ("lock_sr", "fur_lock", -5.25, -4.75, 1.75, 3.25, -1.5, 0.5),
    ("lock_sl", "fur_lock", 4.75, 5.25, 1.75, 3.25, -1.5, 0.5),
    ("lock_br", "fur_lock", -3.0, -1.0, 1.75, 3.25, 3.0, 3.5),
    ("lock_bl", "fur_lock", 1.0, 3.0, 1.75, 3.75, 3.0, 3.5),
    ("buckle", "olive", -1.5, 1.5, 9.25, 10.25, -3.5, -3.0),
]
TAIL = [
    ("tail_root", "fur", -1.25, 1.25, 0.0, 2.5, -1.0, 1.0),
    ("tail_mid", "fur", -1.5, 1.5, 2.5, 5.0, -1.25, 1.25),
    ("tail_tip", "fur_tip", -1.0, 1.0, 5.0, 7.0, -0.75, 0.75),
]
WH_ARM = [
    ("fur_pauldron", "fur", -4.25, 1.25, -2.75, 0.75, -2.75, 2.75),
    ("plock_1", "fur_lock", -4.25, -3.25, 0.75, 2.75, -2.25, -1.25),
    ("plock_2", "fur_lock", -4.25, -3.25, 0.75, 3.25, -0.5, 0.5),
    ("plock_3", "fur_lock", -4.25, -3.25, 0.75, 2.25, 1.25, 2.25),
    ("plock_4", "fur_lock", -2.5, -1.0, 0.75, 2.25, -2.75, -2.25),
    ("arm_band", "olive", -3.5, 1.5, 4.25, 5.75, -2.5, 2.5),
    ("band_claw", "bone", -4.0, -3.5, 4.75, 6.75, -0.25, 0.25),
]

# --- The Revenant Mail ----------------------------------------------------------------------------------------
RV_BODY = [
    ("cuirass", "pewter", -4.75, 4.75, 0.75, 8.75, -2.75, 2.75),
    ("mantle", "fur_frost", -5.25, 5.25, -0.75, 1.75, -3.5, 3.5),
    ("breastplate", "pewter_trim", -4.25, 4.25, 2.25, 7.25, -3.25, -2.75),
    ("keel", "pewter", -0.75, 0.75, 3.25, 6.75, -3.5, -3.0),
    ("setting", "brass", -1.75, 1.75, 2.75, 5.75, -3.75, -3.25),
    ("gem", "gem", -1.0, 1.0, 3.25, 5.25, -4.25, -3.75),
    ("cross_r", "brass", -3.25, -1.75, 3.75, 4.75, -3.5, -3.0),
    ("cross_l", "brass", 1.75, 3.25, 3.75, 4.75, -3.5, -3.0),
    ("webbing", "olive_web", -5.0, 5.0, 7.75, 9.25, -3.0, 3.0),
    ("pouch_r", "olive", -3.75, -1.75, 7.25, 9.75, -4.0, -3.0),
    ("pouch_l", "olive", 1.75, 3.75, 7.25, 9.75, -4.0, -3.0),
    ("backplate", "pewter_trim", -4.25, 4.25, 1.75, 7.25, 2.75, 3.25),
    ("spine", "gem", -0.5, 0.5, 2.25, 6.75, 3.25, 3.75),
    ("fauld_upper", "pewter_trim", -4.75, 4.75, 9.25, 10.75, -3.25, 3.25),
    ("fauld_lower", "pewter_trim", -5.25, 5.25, 10.75, 11.75, -3.5, 3.5),
    ("lock_fr", "fur_lock", -4.75, -3.75, 1.75, 3.75, -4.0, -3.0),
    ("lock_fl", "fur_lock", 3.75, 4.75, 1.75, 3.75, -4.0, -3.0),
    ("lock_br", "fur_lock", -3.0, -1.0, 1.75, 3.25, 3.25, 3.75),
    ("lock_bl", "fur_lock", 1.0, 3.0, 1.75, 3.75, 3.25, 3.75),
    ("drip_1", "wisp", -1.25, -0.75, 7.25, 8.75, -3.25, -2.75),
    ("drip_2", "wisp", 2.5, 3.0, 11.75, 13.25, -3.5, -3.0),
    ("drip_3", "wisp", -2.0, -1.5, 11.75, 12.75, -3.5, -3.0),
    ("trail_1", "wisp", -3.0, -2.0, 7.25, 10.75, 3.25, 3.75),
    ("trail_2", "wisp", 1.5, 2.5, 11.75, 14.75, 3.0, 3.5),
]
RV_ARM = [
    ("fur_under", "fur_frost", -4.25, 1.25, -1.25, 1.75, -2.75, 2.75),
    ("pauldron_top", "pewter_trim", -4.5, 1.5, -3.25, -1.25, -3.0, 3.0),
    ("pauldron_mid", "pewter_trim", -5.0, 1.0, -1.75, 0.25, -3.25, 3.25),
    ("stud", "gem", -5.5, -5.0, -1.25, -0.25, -0.5, 0.5),
    ("plock_1", "fur_lock", -4.25, -3.25, 1.75, 3.75, -2.25, -1.25),
    ("plock_2", "fur_lock", -4.25, -3.25, 1.75, 4.25, -0.5, 0.5),
    ("plock_3", "fur_lock", -4.25, -3.25, 1.75, 3.25, 1.25, 2.25),
    ("vambrace", "pewter_trim", -3.5, 1.5, 5.25, 8.75, -2.5, 2.5),
    ("vam_gem", "gem", -4.0, -3.5, 6.5, 7.5, -0.5, 0.5),
    ("arm_wisp", "wisp", -3.0, -2.0, 8.75, 11.25, -2.5, -2.0),
]


def rename(cubes, prefix):
    return [(prefix + c[0],) + tuple(c[1:]) for c in cubes]


# Every arm cube is nudged an eighth of a pixel outward, down and back: then no arm face can share a plane with a body
# cube's, the player's or vanilla armor's (all on quarter pixels) whichever way the arms swing. Too small to see.
NUDGE = (0.125, 0.125, 0.125)


def nudge(cubes, side):
    dx = -NUDGE[0] if side == "right" else NUDGE[0]
    return [(c[0], c[1], c[2] + dx, c[3] + dx, c[4] + NUDGE[1], c[5] + NUDGE[1], c[6] + NUDGE[2], c[7] + NUDGE[2]) for c in cubes]


def chest_parts(body, arm, head_mat=None, tail_mat=None, slim_arms=False):
    right, left = list(arm), mirror(arm)
    if slim_arms:
        right, left = slim(right, "right"), slim(left, "left")
    right, left = nudge(right, "right"), nudge(left, "left")
    parts = [Part("body", body), Part("right_arm", right, offset=(-5.0, 2.5 if slim_arms else 2.0, 0.0)),
             Part("left_arm", left, offset=(5.0, 2.5 if slim_arms else 2.0, 0.0))]
    if head_mat is not None:
        cubes = [(c[0], head_mat if c[1] == "fur_dark" else c[1]) + tuple(c[2:]) for c in WOLF_HEAD]
        # Over the right shoulder, snout forward, turned a little out.
        x = (-1.0 if slim_arms else -1.5) - NUDGE[0]
        y = (-3.25 if head_mat == "fur_dark" else -3.75) + NUDGE[1]
        parts.append(Part("wolf_head", rename(cubes, "wolf_"), parent="right_arm", offset=(x, y, 0.25 + NUDGE[2]),
                          rot=(0.0, 0.3, 0.0)))
    if tail_mat is not None:
        parts.append(Part("tail", [(c[0], tail_mat if c[1] == "fur" else c[1]) + tuple(c[2:]) for c in TAIL], parent="body",
                          offset=(0.0, 9.25, 3.75), rot=(0.3, 0.0, 0.0)))
    return parts


# --- The Astral Visor -----------------------------------------------------------------------------------------
SCREEN = (-5.75, 3.25, -10.25, -2.25)   # the glass's front: x0 x1 y0 y1
VISOR_CUBES = [
    ("cabinet", "lacquer", -7.5, 7.5, -11.5, 1.0, -6.0, 5.5),
    ("cabinet_round", "lacquer", -6.5, 6.5, -12.0, -0.5, -5.5, 5.0),
    ("bezel", "brass", -6.25, 3.75, -10.75, -1.75, -6.5, -5.5),
    ("glass", "glass", -5.75, 3.25, -10.25, -2.25, -7.0, -6.0),
    ("bulge", "glass", -5.25, 2.75, -9.75, -2.75, -7.5, -6.5),
    ("crown", "glass", -4.25, 1.75, -8.75, -3.75, -7.75, -7.25),
    # The Starlight Lens, mounted where the knobs were and run out past the front.
    ("scope_mount", "brass_dark", 4.25, 6.25, -10.25, -6.25, -7.0, -6.0),
    ("scope_tube", "brass", 4.5, 6.0, -9.75, -8.25, -10.0, -7.0),
    ("scope_band", "leather", 4.25, 6.25, -10.0, -8.0, -8.5, -7.5),
    ("scope_ring", "brass", 4.25, 6.25, -10.0, -8.0, -10.5, -10.0),
    ("scope_lens", "crystal", 4.5, 6.0, -9.75, -8.25, -11.0, -10.5),
    ("dial", "brass", 4.5, 6.0, -5.25, -3.75, -6.5, -6.0),
    ("dial_gem", "crystal", 5.0, 5.5, -4.75, -4.25, -7.0, -6.5),
    ("housing", "lacquer_dark", -5.0, 5.0, -10.5, -1.5, 5.5, 8.5),
    ("housing_cap", "brass", -3.5, 3.5, -9.0, -3.0, 8.5, 10.0),
    ("antenna_base", "brass_dark", -1.5, 1.5, -13.0, -12.0, -0.5, 1.5),
]
ANTENNA = [("rod", "brass", -0.25, 0.25, -9.0, 0.0, -0.25, 0.25), ("tip", "crystal", -0.75, 0.75, -10.5, -9.0, -0.75, 0.75),
           ("tip_point", "crystal", -0.25, 0.25, -11.5, -10.5, -0.25, 0.25)]


def visor_parts():
    parts = [Part("head", VISOR_CUBES)]
    for side, name in ((-1, "antenna_right"), (1, "antenna_left")):
        parts.append(Part(name, [(f"{name}_{c[0]}",) + c[1:] for c in ANTENNA], parent="head",
                          offset=(side * 0.75, -12.5, 0.5), rot=(-0.25, 0.0, side * 0.5)))
    return parts


# The star map on the visor's screen, in head space (x, y, size): size 2 is a bright star with a cross of light.
STARS = [(-4.6, -9.4, 1), (-2.9, -8.9, 2), (-0.6, -9.6, 1), (1.9, -9.1, 1), (2.6, -7.7, 2), (-4.9, -6.9, 1), (-3.3, -5.6, 1),
         (-1.4, -6.6, 2), (0.6, -5.4, 1), (2.1, -4.6, 1), (-4.1, -3.4, 1), (-2.2, -3.9, 2), (0.1, -3.1, 1), (1.6, -2.7, 1),
         (-0.9, -8.1, 1), (1.2, -7.2, 1)]


def starfield(x, y):
    """Glow at a point of the screen: a violet-to-blue nebula wash and stars."""
    for sx, sy, size_ in STARS:
        d = math.hypot(x - sx, y - sy)
        if d < 0.3:
            return (255, 255, 255, 255)
        if size_ == 2 and d < 0.8 and (abs(x - sx) < 0.3 or abs(y - sy) < 0.3):
            return (200, 236, 255, 230)
    d1 = math.hypot((x + 1.2) / 3.6, (y + 6.4) / 2.4)
    d2 = math.hypot((x - 1.6) / 2.2, (y + 4.0) / 1.8)
    k1 = max(0.0, 1.0 - d1)
    k2 = max(0.0, 1.0 - d2)
    if k1 + k2 < 0.08:
        return (60, 70, 160, 70)
    col = lerp((126, 74, 210), (80, 150, 240), k2 / (k1 + k2))
    col = lerp(col, (230, 210, 255), 0.35 * max(k1, k2) ** 2)
    return col + (int(80 + 120 * min(1.0, k1 + k2)),)


# ================================================================================================== painters
def painter_for(scheme):
    """scheme: 'phantasmal' | 'wolfhide' | 'revenant' | 'visor'."""
    eye_col = AMBER if scheme == "wolfhide" else CYAN_EYE

    def base(part, c, face, p, n, s, t):
        mat = c[1]
        if mat == "silver":
            return metal(SILVER, c, face, p, s, t, fade=GHOST)
        if mat == "silver_trim":
            return metal(SILVER, c, face, p, s, t, trim=GOLD, fade=GHOST)
        if mat == "gold":
            return metal(GOLD, c, face, p, s, t)
        if mat == "pewter":
            return metal(PEWTER, c, face, p, s, t, fade=(150, 210, 214))
        if mat == "pewter_trim":
            return metal(PEWTER, c, face, p, s, t, trim=BRASS, fade=(150, 210, 214))
        if mat in ("brass", "brass_dark"):
            col = metal(BRASS, c, face, p, s, t)
            return lerp(col, BRASS[4], 0.3) if mat == "brass_dark" else col
        if mat == "gem":
            return gem_base(c, face, s, t)
        if mat == "wisp":
            return None
        if mat in ("fur", "fur_lock", "fur_tip"):
            col = fur(FUR, c, face, p, s, t, lock=mat == "fur_lock")
            if col is not None and mat == "fur_tip":
                col = lerp(col, (196, 188, 176), 0.5 * t + 0.2)
            if col is not None and scheme == "revenant":
                col = lerp(col, FROST, 0.35 * smooth(0.4, 1.0, t))
            return col
        if mat == "fur_frost":
            return fur(FUR_DARK, c, face, p, s, t, frost=FROST)
        if mat == "fur_dark":
            if skull_detail(c, face, s, t, FUR_DARK, eye_col) == "eye":
                return (20, 14, 8)
            if skull_detail(c, face, s, t, FUR_DARK, eye_col) == "tooth":
                return BONE[1]
            return fur(FUR, c, face, p, s, t)
        if mat == "bone":
            kind = skull_detail(c, face, s, t, BONE, eye_col)
            if kind == "eye":
                return (16, 30, 34)
            if kind == "tooth":
                return BONE[0]
            v = FACE_LIGHT[face] + bevel(c, face, s, t) + 0.15 * (t - 0.5)
            return ramp(BONE, v)
        if mat == "nose":
            return ramp([(70, 60, 62), (40, 34, 36), (20, 16, 18)], FACE_LIGHT[face]) if scheme == "wolfhide" else ramp(BONE, 0.7)
        if mat == "olive":
            return olive(c, face, s, t)
        if mat == "olive_web":
            return olive(c, face, s, t, webbing=True)
        if mat == "leather":
            v = FACE_LIGHT[face] + bevel(c, face, s, t)
            return ramp(LEATHER, v)
        if mat in ("lacquer", "lacquer_dark"):
            v = FACE_LIGHT[face] + bevel(c, face, s, t, 0.1) + 0.2 * (t - 0.5)
            l, r, tp, b = edges(c, face, s, t)
            # A thin brass pinstripe one texel in from each edge of the cabinet's faces.
            if c[0] == "cabinet" and face in ("north", "east", "west", "south") and 1 <= min(l, r, tp, b) < 2:
                return ramp(BRASS, v + 0.1)
            col = ramp(LACQUER, v + (0.25 if mat == "lacquer_dark" else 0.0))
            if mat == "lacquer_dark" and face in ("east", "west", "top") and int(t * face_texels(c, face)[1] * SCALE) % 3 == 1:
                col = ramp(BRASS, v + 0.35)   # the vents: brass slats
            return col
        if mat == "glass":
            v = FACE_LIGHT[face] * 0.6 + 0.4 * math.hypot(s - 0.3, t - 0.3)
            return ramp(STARGLASS, v)
        if mat == "crystal":
            d = math.hypot(s - 0.35, t - 0.35)
            return ramp(CRYSTAL, 0.2 + d + FACE_LIGHT[face] * 0.5)
        raise ValueError(mat)

    def glow(part, c, face, p, n, s, t):
        mat = c[1]
        if mat == "wisp":
            return wisp(c, face, t, ECTO, 175 if scheme == "phantasmal" else 190)
        if mat == "gem":
            return gem_glow(c, face, s, t)
        if mat in ("silver", "silver_trim", "pewter", "pewter_trim") and face not in ("top", "bottom"):
            y0, y1 = c[4], c[5]
            k = smooth(0.6, 1.0, (p[1] - y0) / (y1 - y0))
            if k > 0.05:
                return ECTO[1] + (int(70 * k),)
            return None
        if mat in ("fur_dark", "bone") and skull_detail(c, face, s, t, None, None) == "eye":
            return eye_col + (255,)
        if mat == "glass":
            col = starfield(p[0], p[1])
            if face != "north":
                return col[:3] + (int(col[3] * 0.45),)
            return col
        if mat == "crystal":
            d = math.hypot(s - 0.4, t - 0.4)
            return ramp(CRYSTAL, 0.05 + d * 0.9) + (int(230 * (1.0 - 0.4 * d)),)
        return None

    return base, glow


# ================================================================================================== build
MODELS = OrderedDict()
MODELS["astral_visor"] = (Model("astral_visor", visor_parts(), 128, 64), "visor",
                          "createAstralVisor", "The Astral Visor, on the head.")
for slim_arms in (False, True):
    sfx = "_slim" if slim_arms else ""
    doc = " (slim arms)" if slim_arms else ""
    MODELS["phantasmal_chestplate" + sfx] = (Model("phantasmal_chestplate" + sfx, chest_parts(PH_BODY, PH_ARM, slim_arms=slim_arms), 128, 64),
                                             "phantasmal", "createPhantasmalChestplate" + ("Slim" if slim_arms else ""),
                                             "The Phantasmal Chestplate" + doc + ".")
    MODELS["wolfhide_carrier" + sfx] = (Model("wolfhide_carrier" + sfx, chest_parts(WH_BODY, WH_ARM, head_mat="fur_dark", tail_mat="fur",
                                                                                     slim_arms=slim_arms), 128, 64),
                                        "wolfhide", "createWolfhideCarrier" + ("Slim" if slim_arms else ""),
                                        "The Wolfhide Carrier" + doc + ".")
    MODELS["revenant_mail" + sfx] = (Model("revenant_mail" + sfx, chest_parts(RV_BODY, RV_ARM, head_mat="bone", tail_mat="fur_tip",
                                                                              slim_arms=slim_arms), 128, 64),
                                     "revenant", "createRevenantMail" + ("Slim" if slim_arms else ""), "The Revenant Mail" + doc + ".")

GLOW_MIN_ALPHA = 100


def settle_glow(base_tex, glow_tex):
    """Over an opaque base texel, glow must be opaque too: the eyes pass is translucent, and in 26.3 a translucent
    fragment at the same depth as the opaque one under it is culled (only the TV Helmet's fully opaque frames show).
    So there the glow is blended onto the base and written opaque, and a faint one (below GLOW_MIN_ALPHA) is dropped.
    Where the base is clear (the ectoplasm wisps) the glow keeps its alpha and is see-through."""
    out = []
    for b, g in zip(base_tex, glow_tex):
        if g[3] == 0 or b[3] == 0:
            out.append(g)
        elif g[3] < GLOW_MIN_ALPHA:
            out.append((0, 0, 0, 0))
        else:
            a = g[3] / 255.0
            out.append(tuple(int(round(b[i] * (1 - a) + g[i] * a)) for i in range(3)) + (255,))
    return out


TEXTURES = {}
for key, (model, scheme, method, doc) in MODELS.items():
    base, glow = painter_for(scheme)
    base_tex = model.bake(base)
    TEXTURES[key] = (base_tex, settle_glow(base_tex, model.bake(glow)))
    png(os.path.join(TEX_DIR, key + ".png"), model.tex_w * SCALE, model.tex_h * SCALE, TEXTURES[key][0])
    png(os.path.join(TEX_DIR, key + "_glow.png"), model.tex_w * SCALE, model.tex_h * SCALE, TEXTURES[key][1])


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


def player_faces(slim_arms):
    out = box_faces("player:head", -4, 4, -8, 0, -4, 4) + box_faces("player:hat", -4.5, 4.5, -8.5, 0.5, -4.5, 4.5)
    out += box_faces("player:body", -4, 4, 0, 12, -2, 2) + box_faces("player:jacket", -4.25, 4.25, -0.25, 12.25, -2.25, 2.25)
    ay = 2.5 if slim_arms else 2.0
    for side, px in (("right", -5.0), ("left", 5.0)):
        w = 3 if slim_arms else 4
        x0 = (-2.0 if slim_arms else -3.0) if side == "right" else -1.0
        out += box_faces(f"player:{side}_arm", x0, x0 + w, -2, 10, -2, 2, (px, ay, 0))
        out += box_faces(f"player:{side}_sleeve", x0 - 0.25, x0 + w + 0.25, -2.25, 10.25, -2.25, 2.25, (px, ay, 0))
    for side, px in (("right", -1.9), ("left", 1.9)):
        out += box_faces(f"player:{side}_leg", -2, 2, 0, 12, -2, 2, (px, 12, 0))
        out += box_faces(f"player:{side}_pants", -2.25, 2.25, -0.25, 12.25, -2.25, 2.25, (px, 12, 0))
        # Vanilla leggings (inflated 0.5) and boots (1.0) on the legs.
        out += box_faces(f"vanilla:{side}_leggings", -2.5, 2.5, -0.5, 12.5, -2.5, 2.5, (px, 12, 0))
        out += box_faces(f"vanilla:{side}_boots", -3, 3, -1, 13, -3, 3, (px, 12, 0))
    out += box_faces("vanilla:leggings_body", -4.5, 4.5, -0.5, 12.5, -2.5, 2.5)
    return out


def model_faces(model, tag):
    return [Face(f"{tag}:{c[0]}.{face}", posed, pn) for part, c, face, _, posed, _, _, pn in model.faces()]


def check_zfighting():
    total = 0
    visor = model_faces(MODELS["astral_visor"][0], "astral_visor")
    for key, (model, scheme, _, _) in MODELS.items():
        if key == "astral_visor":
            continue
        slim_arms = key.endswith("_slim")
        faces = model_faces(model, key) + visor + player_faces(slim_arms)
        hits = [h for h in fights(faces) if not (h[0].split(":")[0] in ("player", "vanilla") and h[1].split(":")[0] in ("player", "vanilla"))]
        for h in hits:
            print("z-fight:", key, h)
        total += len(hits)
    if total:
        raise SystemExit(f"fusion gear: {total} coplanar overlapping face pairs")
    print("fusion gear: " + ", ".join(f"{k} {sum(len(p.cubes) for p in m.parts)} cubes" for k, (m, *_) in MODELS.items()) + "; no z-fighting")


check_zfighting()


# ================================================================================================== Java
def write_java():
    body = "\n\n".join(model.java(method, doc) for model, _, method, doc in MODELS.values())
    src = open(JAVA).read()
    start, end = "\t// BEGIN GENERATED by tools/gen_fusion_gear.py\n", "\t// END GENERATED\n"
    a, b = src.index(start) + len(start), src.index(end)
    with open(JAVA, "w") as out:
        out.write(src[:a] + body + "\n" + src[b:])


write_java()


# ================================================================================================== item sprites
def C(hx):
    return ((hx >> 16) & 255, (hx >> 8) & 255, hx & 255)


def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [(i, len(r)) for i, r in enumerate(rows) if len(r) != 16]
    return [(palette[ch] + (255,)) if ch != "." else (0, 0, 0, 0) for r in rows for ch in r]


PHANTASMAL_SPRITE = sprite([
    "................",
    ".............a..",
    ".bbbbb....bbbbb.",
    ".bcdeb....bcdeb.",
    ".bdeefb..bfdeeb.",
    ".beeedfbbfeeeeb.",
    ".bffecdggeeeffb.",
    ".GGfddgTqgeefGG.",
    "...Gddgqrgeeg...",
    "...GedggeeefG...",
    "...hpeeeeeeph...",
    "...hppqppqpph...",
    "...hqqrqqrqqh...",
    "....hrrqqrrh....",
    ".....hrhhrh.....",
    "......r..r......",
], {"a": (250, 214, 92), "b": (206, 205, 202), "c": (255, 255, 255), "d": (252, 252, 247), "e": (243, 243, 238),
    "f": (228, 228, 224), "G": (178, 104, 17), "g": (34, 92, 104), "T": (236, 255, 250), "q": (128, 214, 212),
    "r": (76, 170, 176), "h": (52, 128, 136), "p": (200, 240, 232)})

WOLF_PAL = {"a": (42, 47, 23), "b": (91, 102, 51), "c": (116, 128, 74), "d": (28, 28, 28), "e": (66, 74, 36),
            "F": (45, 43, 46), "G": (60, 57, 59), "H": (77, 73, 74), "I": (96, 91, 90), "J": (122, 113, 104),
            "K": (238, 230, 208), "L": (190, 178, 150)}
WOLFHIDE_ROWS = [
    "..F.F......F.F..",
    ".FHFHF....FHFHF.",
    ".FIJIF....FIJIF.",
    ".FHIHHF..FHHIHF.",
    ".FGHIHF..FHIHGF.",
    "..FGHHabbaHHGF..",
    "..FGabccccbaGF..",
    "..FHabccdcbaHF..",
    "..FGaeeeeeeaGF..",
    "..FHabbbbbbaHF..",
    "..FGaeKeeKeaGF..",
    "..FHabLbbLbaHF..",
    "..FGaeeeeeeaGF..",
    ".FHGabbbbbbaGHF.",
    ".FJFaaaaaaaaFJF.",
    "..F..........F..",
]
REV_PAL = {"b": (38, 42, 56), "c": (156, 168, 190), "d": (120, 132, 156), "e": (92, 102, 126), "f": (68, 76, 98),
           "G": (200, 160, 80), "g": (20, 70, 90), "T": (220, 255, 255), "q": (120, 230, 235), "r": (60, 170, 190),
           "o": (66, 74, 36), "O": (100, 112, 58), "1": (36, 34, 40), "3": (66, 64, 70), "4": (96, 96, 104),
           "5": (200, 214, 226)}
REVENANT_ROWS = [
    "..1.1......1.1..",
    ".13531....13531.",
    ".14541....14541.",
    ".b343b....b343b.",
    ".bc4ddb..bdd4cb.",
    ".bcddeebbeeddcb.",
    ".beedcdggdeeeeb.",
    ".GGeddgTqgdeeGG.",
    "...Gddgqrgeeg...",
    "...GoOoggoOoG...",
    "...boOooooOob...",
    "...bdeeffeedb...",
    "...brqeffeqrb...",
    "....brqqqqrb....",
    ".....brbbrb.....",
    "......r..r......",
]
VISOR_SPRITE = sprite([
    "..S..........S..",
    "...y........y...",
    "....y......y....",
    ".....y....y.....",
    "......YccY......",
    ".dddddddddddddd.",
    ".deeeeeeeeeeeed.",
    ".dfggggggggfffd.",
    ".dfgnnpnnmgyyyyV",
    ".dfgnmnnpngYYYYW",
    ".dfgpnnmnngfbfd.",
    ".dfgnnmnnpgfjfd.",
    ".dfggggggggfkfd.",
    ".dlllllllllllld.",
    ".dddddddddddddd.",
    "..dd........dd..",
], {"S": (196, 250, 255), "y": (230, 186, 96), "Y": (138, 94, 40), "c": (30, 28, 72), "d": (18, 16, 44),
    "e": (92, 96, 170), "f": (52, 52, 112), "g": (190, 140, 60), "n": (28, 24, 64), "m": (110, 70, 190),
    "p": (236, 240, 255), "V": (164, 236, 249), "W": (88, 102, 205), "b": (138, 94, 40), "j": (99, 208, 238),
    "k": (196, 250, 255), "l": (38, 36, 86)})

PLATE_COLOURS = {1: ((63, 71, 96), (44, 50, 66)), 2: ((180, 186, 194), (139, 145, 153)), 3: ((220, 198, 154), (194, 168, 120))}


def with_plate(rows, level, cells):
    """The sprite with the plate showing at these cells: (row, col, 'P' light | 'Q' dark)."""
    out = [list(r) for r in rows]
    for r, c, ch in cells:
        out[r][c] = ch
    return ["".join(r) for r in out]


SPRITES = {"phantasmal_chestplate": PHANTASMAL_SPRITE, "astral_visor": VISOR_SPRITE,
           "wolfhide_carrier": sprite(WOLFHIDE_ROWS, WOLF_PAL), "revenant_mail": sprite(REVENANT_ROWS, REV_PAL)}
# A plate in the carrier shows in its pocket: the Wolfhide Carrier's along the top of the pocket under the flap, the
# Revenant Mail's in place of its webbing's middle.
WOLF_PLATE = [(5, 7, "P"), (5, 8, "P"), (6, 6, "Q"), (6, 7, "Q"), (6, 8, "Q"), (6, 9, "Q")]
REV_PLATE = [(10, 5, "P"), (10, 6, "Q"), (10, 7, "Q"), (10, 8, "Q"), (10, 9, "Q"), (10, 10, "P")]
for level, (light, dark) in PLATE_COLOURS.items():
    SPRITES[f"wolfhide_carrier_plated_level_{level}"] = sprite(with_plate(WOLFHIDE_ROWS, level, WOLF_PLATE), dict(WOLF_PAL, P=light, Q=dark))
    SPRITES[f"revenant_mail_plated_level_{level}"] = sprite(with_plate(REVENANT_ROWS, level, REV_PLATE), dict(REV_PAL, P=light, Q=dark))
for name, px in SPRITES.items():
    png(os.path.join(ASSETS, "textures", "item", name + ".png"), 16, 16, px)


# ================================================================================================== item models
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(json.dumps(obj, indent=2) + "\n")


for name in SPRITES:
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
for name in ("phantasmal_chestplate", "astral_visor"):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})
for name in ("wolfhide_carrier", "revenant_mail"):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([
        ("type", "minecraft:select"), ("property", "minecraft:component"), ("component", "tomyen:armor_plate"),
        ("cases", [{"when": f"tomyen:armor_plate_level_{lv}",
                    "model": {"type": "minecraft:model", "model": f"tomyen:item/{name}_plated_level_{lv}"}} for lv in (1, 2, 3)]),
        ("fallback", {"type": "minecraft:model", "model": "tomyen:item/" + name})])})


# ================================================================================================== tags and lang
DATA = os.path.join(RES, "data")
CHESTS = ["tomyen:phantasmal_chestplate", "tomyen:wolfhide_carrier", "tomyen:revenant_mail"]
TAGS = {
    "minecraft/tags/item/chest_armor.json": CHESTS,
    "minecraft/tags/item/head_armor.json": ["tomyen:astral_visor"],
    "minecraft/tags/item/enchantable/chest_armor.json": CHESTS,
    "minecraft/tags/item/enchantable/head_armor.json": ["tomyen:astral_visor"],
    "minecraft/tags/item/enchantable/armor.json": CHESTS + ["tomyen:astral_visor"],
    "minecraft/tags/item/enchantable/durability.json": CHESTS + ["tomyen:astral_visor"],
    "minecraft/tags/item/enchantable/equippable.json": CHESTS + ["tomyen:astral_visor"],
    "minecraft/tags/item/enchantable/vanishing.json": CHESTS + ["tomyen:astral_visor"],
    # Holy silver underneath: they count as its chestplate against the evil (HolySilverArmor).
    "tomyen/tags/item/counts_as_holy_silver.json": ["tomyen:phantasmal_chestplate", "tomyen:revenant_mail"],
}
LANG = OrderedDict([
    ("item.tomyen.phantasmal_chestplate", "Phantasmal Chestplate"),
    ("item.tomyen.phantasmal_chestplate.phase", "One hit in five passes straight through you"),
    ("item.tomyen.phantasmal_chestplate.holy", "Still holy silver: wards off evil like its chestplate"),
    ("item.tomyen.wolfhide_carrier", "Wolfhide Carrier"),
    ("item.tomyen.wolfhide_carrier.night", "By night: faster and stronger"),
    ("item.tomyen.wolfhide_carrier.moon", "Under a full moon: more so, and higher jumps"),
    ("item.tomyen.wolfhide_carrier.wakes", "The hide stirs with the night."),
    ("item.tomyen.wolfhide_carrier.wakes_moon", "The full moon wakes the wolf in the hide!"),
    ("item.tomyen.astral_visor", "Astral Visor"),
    ("item.tomyen.astral_visor.vision", "Night vision"),
    ("item.tomyen.astral_visor.sight", "Outlines creatures nearby, through walls"),
    ("item.tomyen.astral_visor.warning", "Warns of falling meteors and UFOs"),
    ("item.tomyen.astral_visor.meteor", "\u2604 Meteor: impact %sm %s in %ss"),
    ("item.tomyen.astral_visor.ufo", "\u25c9 UFO: %sm %s"),
    ("item.tomyen.astral_visor.dir.ahead", "ahead"),
    ("item.tomyen.astral_visor.dir.ahead_right", "ahead, right"),
    ("item.tomyen.astral_visor.dir.right", "to the right"),
    ("item.tomyen.astral_visor.dir.behind_right", "behind, right"),
    ("item.tomyen.astral_visor.dir.behind", "behind you"),
    ("item.tomyen.astral_visor.dir.behind_left", "behind, left"),
    ("item.tomyen.astral_visor.dir.left", "to the left"),
    ("item.tomyen.astral_visor.dir.ahead_left", "ahead, left"),
    ("item.tomyen.revenant_mail", "Revenant Mail"),
    ("item.tomyen.revenant_mail.ward", "Once a day, dying in it keeps all your gear on you"),
    ("item.tomyen.revenant_mail.ready", "Death ward: ready"),
    ("item.tomyen.revenant_mail.spent", "Death ward: spent, returns at dawn"),
    ("item.tomyen.revenant_mail.kept", "Your Revenant Mail refuses to let go. Your gear stays with you."),
    ("item.tomyen.revenant_mail.title", "The Mail Holds"),
    ("item.tomyen.revenant_mail.subtitle", "Nothing was lost. The ward returns at dawn."),
])


def merge_data():
    for path, values in TAGS.items():
        full = os.path.join(DATA, path)
        tag = json.load(open(full), object_pairs_hook=OrderedDict) if os.path.exists(full) else OrderedDict([("values", [])])
        for v in values:
            if v not in tag["values"]:
                tag["values"].append(v)
        write_json(full, tag)
    path = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(path, encoding="utf-8"), object_pairs_hook=OrderedDict)
    lang.update(LANG)
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")


merge_data()


# ================================================================================================== preview
def vanilla(name):
    return read_png(JAR.read("assets/minecraft/textures/%s.png" % name))


STEVE = vanilla("entity/player/wide/steve")
BG, GROUND = (44, 50, 60), (70, 86, 58)


def skin_sampler(rect):
    ua, va, ub, vb = rect
    w, h, px = STEVE

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


def model_quads(model, tex, glow, poses):
    tw = model.tex_w * SCALE
    out = []
    for part, c, face, verts, posed, rect, n, pn in model.faces(poses):
        out.append((posed, tex_sampler(tex, tw, rect), pn, False))
        out.append((posed, tex_sampler(glow, tw, rect), pn, True))
    return out


def render(quads, yaw, scale_px, width, height, focus=(0.0, 6.0, 0.0), pitch=0.15, ground=24.0, bg=BG, dark=False):
    ca, sa = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def view(p):
        x = p[0] * ca - p[2] * sa
        z = p[0] * sa + p[2] * ca
        return x, p[1] * cp - z * sp, p[1] * sp + z * cp

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
    for pass_glow in (False, True):
        for verts, sample, n, glow in quads:
            if glow != pass_glow:
                continue
            P = [view(p) for p in verts]
            P = [(ox + q[0] * scale_px, oy + q[1] * scale_px, q[2] - (0.01 if glow else 0.0)) for q in P]
            shade_f = 1.0 if glow else (0.55 + 0.45 * max(0.0, dot(n, light))) * (0.25 if dark else 1.0)
            A, B, Cc = P[1], P[0], P[2]
            eu, ev = (B[0] - A[0], B[1] - A[1]), (Cc[0] - A[0], Cc[1] - A[1])
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
                    depth = A[2] + (B[2] - A[2]) * s + (Cc[2] - A[2]) * t
                    k = py * width + px
                    col = sample(min(s, 0.9999), min(t, 0.9999))
                    if col is None:
                        continue
                    if not glow:
                        if depth < zbuf[k] - 1e-6:
                            zbuf[k] = depth
                            img[k] = tuple(int(col[i] * shade_f) for i in range(3))
                    elif depth < zbuf[k] + 0.05:
                        a = col[3] / 255.0
                        img[k] = tuple(int(img[k][i] * (1 - a) + col[i] * a) for i in range(3))
    return img, width, height


def board_rows(rows, pad=6, bg=(30, 33, 40)):
    width = max(sum(p[1] for p in row) + pad * (len(row) + 1) for row in rows)
    height = sum(max(p[2] for p in row) + pad for row in rows) + pad
    out = [bg] * (width * height)
    y0 = pad
    for row in rows:
        x0 = pad
        for img, w, h in row:
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


WALK = {"right_arm": ((-5, 2, 0), (-0.7, 0, 0.1)), "left_arm": ((5, 2, 0), (0.7, 0, -0.1)),
        "right_leg": ((-1.9, 12, 0), (0.6, 0, 0)), "left_leg": ((1.9, 12, 0), (-0.6, 0, 0))}


def figure(chest, poses=None, helmet=True):
    poses = poses or {}
    quads = steve_quads(poses)
    if helmet:
        m = MODELS["astral_visor"][0]
        quads += model_quads(m, *TEXTURES["astral_visor"], {"head": poses.get("head", ((0, 0, 0), (0, 0, 0)))})
    if chest:
        m = MODELS[chest][0]
        arm_y = 2.5 if chest.endswith("_slim") else 2.0
        rp = {k: v for k, v in poses.items() if k in ("body", "right_arm", "left_arm")}
        for arm, x in (("right_arm", -5), ("left_arm", 5)):
            if arm not in rp:
                rp[arm] = ((x, arm_y, 0), (0, 0, 0))
        quads += model_quads(m, *TEXTURES[chest], rp)
    return quads


if PREVIEW:
    W, H = 260, 400
    rows = []
    for chest in ("phantasmal_chestplate", "wolfhide_carrier", "revenant_mail"):
        q = figure(chest, helmet=chest == "revenant_mail")
        row = [render(q, a, 8.0, W, H, focus=(0, 9.0, 0)) for a in (0.0, math.pi / 5, math.pi / 2, math.pi * 0.8)]
        row.append(render(figure(chest, WALK, helmet=False), math.pi * 0.3, 8.0, W, H, focus=(0, 9.0, 0)))
        row.append(render(q, math.pi / 5, 8.0, W, H, focus=(0, 9.0, 0), bg=(8, 10, 16), ground=None, dark=True))
        rows.append(row)
    visor = figure(None)
    rows.append([render(visor, a, 9.0, 300, 300, focus=(0, -4.0, 0), ground=None) for a in (0.0, math.pi / 4, math.pi / 2, math.pi * 0.85)]
                + [render(visor, math.pi / 5, 9.0, 300, 300, focus=(0, -4.0, 0), ground=None, bg=(8, 10, 16), dark=True)])
    rows.append([render(figure("revenant_mail_slim", helmet=False), a, 8.0, W, H, focus=(0, 9.0, 0)) for a in (0.0, math.pi / 4)]
                + [render(figure("wolfhide_carrier_slim", helmet=False), math.pi / 4, 8.0, W, H, focus=(0, 9.0, 0))])
    sprites = []
    for name, px in SPRITES.items():
        sprites.append(blow_up(px, 16, 16, 8))
    rows.append(sprites[:6])
    rows.append(sprites[6:] + [blow_up(px, 16, 16, 1) for px in list(SPRITES.values())[:4]])
    out, bw, bh = board_rows(rows)
    png(PREVIEW, bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
