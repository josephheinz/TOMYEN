#!/usr/bin/env python3
"""Big Red Boots (see com.jheinz.tomyen.bigredboots and client/bigredboots): the worn model, its texture, the item
sprite, and the item's data.

Run from anywhere: python3 tools/gen_big_red_boots.py [--preview out.png]

The worn model. Each boot is built from layered cubes on the leg (BOOT below, the right boot in the right leg's own
space: y down from the hip, the front toward -z, the outside toward -x; the left boot is its mirror image). Every
layer steps in a little from the one under or around it, so the outline reads round: a wide dark sole, a big domed
toe bulb sticking well out in front, a tall round shaft up the shin and a rolled rim on top. The boots sit to the
outside of each leg (their inner sides stop just short of the body's centre line), so the pair never overlap. Each
cube's size is a whole number of half pixels, so at the texture's two texels a pixel every face covers whole texels.
The cubes are written into BigRedBootsModel.java between the GENERATED markers.

The texture is baked, not painted per face: each texel's colour comes from where it sits on the boot, lit as the
smooth, round rubber boot the cubes stand in for (a soft blend of a superellipsoid toe and an elliptic cylinder for
the shaft gives the normal), so the shading runs on from cube to cube without seams. Glossy red: a clean diffuse ramp
from deep red to bright tomato red, a soft specular highlight (a band down the shaft, a spot on the toe), the rim's
top catching the light, a darker sole and a touch of darkening where the boot meets the ground. No noise.

Z-fighting: no two faces of the boots at rest (both of them, plus the legs and trouser layer they're worn over) may
share a plane, face the same way and overlap (tools/coplanar.py). The generator stops if any do.

The item sprite is a flat 16x16 drawn in vanilla's boots style (both boots toe-left, dark outline, lit from the top
left), in the same reds.

Also written: the item model and definition, the recipe (four rubber, red dye and a pair of leather boots) and its
recipe-book unlock, the foot_armor tag entry (boot enchantments) and the lang.

The preview (--preview) shows the boots on a Steve-proportioned figure from the front, the side and at three quarters,
a walking frame, the boots on an armor stand, close-ups, and the item sprite at 1x and 10x."""
import json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png  # noqa: E402
from coplanar import Face, fights  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tomyen")
DATA = os.path.join(RES, "data")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client", "bigredboots", "BigRedBootsModel.java")
PREVIEW = os.path.abspath(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None

# ================================================================================================== the model
# (name, material, x0, x1, y0, y1, z0, z1) in the right leg's space: pivot at the hip, y down (the ground is y 12),
# the toe toward -z, the outside toward -x. The leg itself is x -2..2, z -2..2 (its trouser layer 0.25 further out).
GROUND = 12.0
LEG_PIVOT = 1.9          # the legs' pivots are at x -1.9 (right) and +1.9 (left)
BOOT = [
    # The sole: a dark slab sunk half a pixel into the ground, its corners stepped in, just inside the foot's bulge;
    # under the toe it follows the toe out, under the heel it runs in to cover the foot.
    ("sole", "sole", -5.125, 0.875, 11.0, 12.5, -6.25, 1.25),
    ("sole_long", "sole", -4.625, 0.375, 11.125, 12.625, -6.625, 0.875),
    ("sole_wide", "sole", -5.375, 1.125, 11.25, 12.75, -5.625, 0.375),
    ("sole_heel", "sole", -3.75, 1.75, 11.0625, 12.5625, -2.375, 3.625),
    # The toe: a big round bulb set to the outside (so from the front the two toes splay apart): three crossed blocks
    # round its widest band, then layers stepping in up to its crown, which runs back into the front of the shaft.
    ("toe", "toe", -5.25, 0.75, 9.8125, 11.3125, -5.5, 1.5),
    ("toe_long", "toe", -5.0, 0.5, 9.5, 11.5, -6.0, 1.0),
    ("toe_narrow", "toe", -4.5, 0.0, 9.625, 11.625, -6.5, 0.5),
    ("toe_upper", "toe", -4.875, 0.625, 8.75, 10.25, -5.25, 0.75),
    ("toe_upper_long", "toe", -4.375, 0.125, 8.25, 9.75, -5.75, 0.25),
    ("toe_dome", "toe", -4.125, -0.125, 7.875, 9.375, -4.875, -0.375),
    ("toe_crown", "toe", -3.625, -0.625, 7.625, 8.625, -4.25, -0.75),
    # The heel: the foot under the shaft, bulging a little out behind it.
    ("heel", "toe", -3.6875, 1.8125, 9.375, 11.375, -2.4375, 3.5625),
    # The shaft: a round tube from the heel up the shin, three crossed blocks for a twelve-sided cross-section.
    ("shaft", "shaft", -3.125, 1.875, 5.25, 11.25, -2.5, 2.5),
    ("shaft_wide", "shaft", -3.75, 1.25, 5.5, 11.0, -1.75, 1.75),
    ("shaft_deep", "shaft", -2.625, 1.375, 5.375, 10.875, -3.0, 3.0),
    # The rolled rim on top, standing proud of the shaft all round.
    ("rim", "rim", -3.6875, 1.8125, 4.75, 5.75, -2.75, 2.75),
    ("rim_wide", "rim", -4.0, 1.5, 5.0, 6.0, -2.125, 2.375),
    ("rim_deep", "rim", -2.875, 1.125, 4.5, 5.5, -3.5, 3.5),
]

SCALE = 2                 # texels per model pixel
TEX_W, TEX_H = 128, 64    # the texture's size in model pixels (the PNG is SCALE times that)


def size(c):
    return (c[3] - c[2], c[5] - c[4], c[7] - c[6])


for c in BOOT:
    for s in size(c):
        assert abs(s * SCALE - round(s * SCALE)) < 1e-9 and s > 0, (c[0], s)
    assert c[3] < LEG_PIVOT, c  # never past the body's centre line (x 1.9 here), so the two boots never meet


def pack():
    """Integer texture offsets (in model pixels) for each cube's box unwrap, shelf-packed."""
    order = sorted(BOOT, key=lambda c: -(size(c)[2] + size(c)[1]))
    offs, x, y, shelf = {}, 0, 0, 0
    for c in order:
        w, h, d = size(c)
        fw, fh = math.ceil(2 * (w + d)), math.ceil(d + h)
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        offs[c[0]] = (x, y)
        x += fw + 1
        shelf = max(shelf, fh + 1)
    assert y + shelf <= TEX_H, "texture too small"
    return offs


UV = pack()


def cube_polys(c, mirror=False, origin=(0.0, 0.0, 0.0), matrix=None):
    """The six faces of a cube as the game builds them (ModelPart.Cube): for each, its corners (posed into model space
    by matrix and origin), its uv rect (u0, v0, u1, v1) in model pixels as given to the Polygon (corner 1 sits at
    (u0, v0), corner 0 at (u1, v0), corner 2 at (u0, v1)) and its outward normal (unposed, before mirroring)."""
    name, _, x0, x1, y0, y1, z0, z1 = c
    w, h, d = size(c)
    u, v = UV[name]
    if mirror:
        x0, x1 = -x1, -x0
        x0, x1 = x1, x0
    m = matrix or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]

    def P(x, y, z):
        return tuple(origin[i] + m[i][0] * x + m[i][1] * y + m[i][2] * z for i in range(3))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    return [
        ("top", [l1, l0, t0, t1], (u1, v0, u2, v1), (0, -1, 0)),
        ("bottom", [t2, t3, l3, l2], (u2, v1, u22, v0), (0, 1, 0)),
        ("west", [t0, l0, l3, t3], (u0, v1, u1, v2), (-1, 0, 0)),
        ("north", [t1, t0, t3, t2], (u1, v1, u2, v2), (0, 0, -1)),
        ("east", [l1, t1, t2, l2], (u2, v1, u3, v2), (1, 0, 0)),
        ("south", [l0, l1, l2, l3], (u3, v1, u4, v2), (0, 0, 1)),
    ]


# ================================================================================================== the texture
def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(cols, t):
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


RED = [(128, 16, 16), (178, 24, 22), (218, 36, 28), (240, 58, 42), (250, 92, 70)]
SOLE = [(108, 14, 14), (136, 20, 18), (158, 24, 22)]
SHINE = (255, 196, 178)


def unit(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


# The smooth shapes the cubes stand in for, whose normals light the texture: the toe bulb is an ellipsoid (its lower
# half a straight-sided drum, so the sides drop to the sole), the shaft an elliptic cylinder.
TOE_CENTRE, TOE_RADII = (-2.25, 10.4, -2.6), (3.2, 2.9, 3.9)
SHAFT_AXIS, SHAFT_RADII = (-0.875, 0.0), (2.9, 3.0)
RIM_TOP = 4.5


def shape_normal(material, x, y, z):
    if material == "toe":
        d = [(p - c) / (r * r) for p, c, r in zip((x, y, z), TOE_CENTRE, TOE_RADII)]
        d[1] = min(d[1], 0.0)
        return unit(d)
    n = unit(((x - SHAFT_AXIS[0]) / SHAFT_RADII[0] ** 2, 0.0, (z - SHAFT_AXIS[1]) / SHAFT_RADII[1] ** 2))
    if material == "rim":
        # The rolled rim turns up to the light over its top.
        lift = max(0.0, min(1.0, (RIM_TOP + 1.5 - y) / 1.5))
        n = unit((n[0] * (1 - 0.7 * lift), -1.2 * lift, n[2] * (1 - 0.7 * lift)))
    return n


LIGHT = unit((-0.5, -0.8, -0.6))      # from above, in front and outside (y down)
VIEW = unit((-0.2, -0.3, -1.0))
HALF = unit(tuple(LIGHT[i] + VIEW[i] for i in range(3)))


def colour(material, p, face_normal):
    x, y, z = p
    if material == "sole":
        # Matte and dark, a shade lighter up its edge.
        t = 0.2 + 0.8 * max(0.0, min(1.0, (12.0 - y) / 1.0))
        return ramp(SOLE, 1.0 if face_normal[1] < 0 else t)
    n = shape_normal(material, x, y, z)
    # Lean the smooth normal a little toward the face's own, so each layer's top still reads as a step up.
    n = unit(tuple(0.85 * n[i] + 0.15 * face_normal[i] for i in range(3)))
    diffuse = max(0.0, sum(n[i] * LIGHT[i] for i in range(3)))
    t = 0.38 + 0.6 * diffuse
    # Darker low down, where the boot meets the ground.
    t -= 0.2 * max(0.0, min(1.0, (y - 10.25) / 1.75))
    c = ramp(RED, t)
    # Gloss: a broad soft sheen and a tighter highlight inside it.
    nh = max(0.0, sum(n[i] * HALF[i] for i in range(3)))
    shine = 0.35 * nh ** 10 + 0.6 * nh ** 40
    return lerp(c, SHINE, min(0.85, shine))


def bake():
    w, h = TEX_W * SCALE, TEX_H * SCALE
    tex = [(0, 0, 0, 0)] * (w * h)
    for c in BOOT:
        for face, verts, (ua, va, ub, vb), fn in cube_polys(c):
            # Corner 1 at (ua, va), corner 0 at (ub, va), corner 2 at (ua, vb).
            A, eu, ev = verts[1], [verts[0][i] - verts[1][i] for i in range(3)], [verts[2][i] - verts[1][i] for i in range(3)]
            for ty in range(int(round(min(va, vb) * SCALE)), int(round(max(va, vb) * SCALE))):
                for tx in range(int(round(min(ua, ub) * SCALE)), int(round(max(ua, ub) * SCALE))):
                    s = ((tx + 0.5) / SCALE - ua) / (ub - ua)
                    t = ((ty + 0.5) / SCALE - va) / (vb - va)
                    p = tuple(A[i] + eu[i] * s + ev[i] * t for i in range(3))
                    col = colour(c[1], p, fn)
                    tex[ty * w + tx] = tuple(int(round(max(0, min(255, k)))) for k in col) + (255,)
    return tex


TEXTURE = bake()
png(os.path.join(ASSETS, "textures", "entity", "big_red_boots.png"), TEX_W * SCALE, TEX_H * SCALE, TEXTURE)


def tex_at(u, v):
    """The texture's colour at (u, v) in model pixels."""
    x = min(TEX_W * SCALE - 1, max(0, int(u * SCALE)))
    y = min(TEX_H * SCALE - 1, max(0, int(v * SCALE)))
    return TEXTURE[y * TEX_W * SCALE + x]


# ================================================================================================== z-fighting
def leg_boxes():
    """The leg and its trouser layer (HumanoidModel / PlayerModel) in the right leg's space."""
    return [("leg", -2.0, 2.0, 0.0, 12.0, -2.0, 2.0), ("trousers", -2.25, 2.25, -0.25, 12.25, -2.25, 2.25)]


def box_faces(label, x0, x1, y0, y1, z0, z1, dx):
    x0, x1 = x0 + dx, x1 + dx
    return [Face(label + ".top", [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
            Face(label + ".bottom", [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0)),
            Face(label + ".west", [(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)], (-1, 0, 0)),
            Face(label + ".east", [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], (1, 0, 0)),
            Face(label + ".north", [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1)),
            Face(label + ".south", [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1))]


def check_zfighting():
    faces, legs = [], []
    for side, dx, mirror in (("right", -LEG_PIVOT, False), ("left", LEG_PIVOT, True)):
        for c in BOOT:
            x0, x1 = (-c[3], -c[2]) if mirror else (c[2], c[3])
            faces += box_faces(f"{side}.{c[0]}", x0, x1, c[4], c[5], c[6], c[7], dx)
        for name, *b in leg_boxes():
            legs += box_faces(f"{side}.{name}", *b, dx)
    leg_labels = {f.label for f in legs}
    hits = [h for h in fights(faces + legs) if not (h[0] in leg_labels and h[1] in leg_labels)]
    if hits:
        for h in hits:
            print("z-fight: big_red_boots", h)
        raise SystemExit(f"big_red_boots: {len(hits)} coplanar overlapping face pairs")
    xs = [p[0] for f in faces for p in f.pts]
    print(f"big_red_boots: {len(BOOT)} cubes a boot, no z-fighting; the pair spans x {min(xs):.2f}..{max(xs):.2f}, "
          f"gap at the centre {min(abs(p[0]) for f in faces for p in f.pts) * 2:.3f} px")


check_zfighting()


# ================================================================================================== Java
def f(v):
    return f"{v:.1f}f" if v == int(v) else f"{v:g}f"


def java_layer():
    lines = []
    for side, pivot, mirror in (("right_leg", -LEG_PIVOT, False), ("left_leg", LEG_PIVOT, True)):
        builder = "CubeListBuilder.create()"
        if mirror:
            builder += ".mirror()"
        for c in BOOT:
            u, v = UV[c[0]]
            x0 = -c[3] if mirror else c[2]
            w, h, d = size(c)
            builder += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({f(x0)}, {f(c[4])}, {f(c[6])}, {f(w)}, {f(h)}, {f(d)})"
        lines.append(f"\t\troot.addOrReplaceChild(\"{side}\", {builder},\n\t\t\t\tPartPose.offset({f(pivot)}, 12.0f, 0.0f));")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_big_red_boots.py\n", "\t\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)

# ================================================================================================== item sprite
PALETTE = {
    "O": (86, 10, 12),     # outline
    "S": (112, 16, 16),    # sole
    "D": (150, 22, 20),    # shadow
    "M": (192, 30, 26),    # mid
    "R": (224, 46, 34),    # base
    "L": (242, 82, 60),    # light
    "H": (255, 176, 156),  # highlight
}
# One boot, toe to the left (as vanilla draws boots): a rolled rim, the shaft, a big round toe bulb and the sole. The
# sprite is a pair: one boot behind, a shade darker, and the other in front of it, lower and to the right.
BOOT_SPRITE = [
    "....OOOOO",
    "...OHHLRO",
    "...ODMMDO",
    "....OLRMO",
    "....OHRMO",
    "....OHRMO",
    "...OOLRMO",
    ".OOLLRRMO",
    "OLHHLRRMO",
    "OLHLRRRDO",
    "ORRRRRMDO",
    "OSSSSSSSO",
    ".OOOOOOO.",
]
BEHIND = {"H": "L", "L": "R", "R": "M", "M": "D", "D": "D", "S": "S", "O": "O"}
PAIR = ((0, 1, True), (7, 2, False))   # (x, y, behind)


def sprite():
    px = [(0, 0, 0, 0)] * 256
    for ox, oy, behind in PAIR:
        for j, row in enumerate(BOOT_SPRITE):
            for i, ch in enumerate(row):
                if ch != ".":
                    px[(oy + j) * 16 + ox + i] = PALETTE[BEHIND[ch] if behind else ch] + (255,)
    return px


SPRITE = sprite()
png(os.path.join(ASSETS, "textures", "item", "big_red_boots.png"), 16, 16, SPRITE)


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


write(os.path.join(ASSETS, "models", "item", "big_red_boots.json"),
      OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/big_red_boots"})]))
write(os.path.join(ASSETS, "items", "big_red_boots.json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/big_red_boots"}})

# Rubber from the refinery, red dye and a pair of leather boots to pull them over.
write(os.path.join(DATA, "tomyen", "recipe", "big_red_boots.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "equipment"),
    ("key", OrderedDict([("B", "minecraft:leather_boots"), ("D", "minecraft:red_dye"), ("R", "tomyen:rubber")])),
    ("pattern", ["RDR", "RBR"]),
    ("result", OrderedDict([("count", 1), ("id", "tomyen:big_red_boots")]))]))
write(os.path.join(DATA, "tomyen", "advancement", "recipes", "combat", "big_red_boots.json"), OrderedDict([
    ("parent", "minecraft:recipes/root"),
    ("criteria", OrderedDict([
        ("has_rubber", {"conditions": {"items": [{"items": "tomyen:rubber"}]}, "trigger": "minecraft:inventory_changed"}),
        ("has_the_recipe", {"conditions": {"recipes": "tomyen:big_red_boots"}, "trigger": "minecraft:recipe_unlocked"})])),
    ("requirements", [["has_rubber", "has_the_recipe"]]),
    ("rewards", {"recipes": ["tomyen:big_red_boots"]})]))
# Boot enchantments (Feather Falling, Depth Strider, Frost Walker, Soul Speed...) go on through the foot_armor tag.
tag_add(os.path.join(DATA, "minecraft", "tags", "item", "foot_armor.json"), "tomyen:big_red_boots")

merge(LANG, OrderedDict([
    ("item.tomyen.big_red_boots", "Big Red Boots"),
    ("item.tomyen.big_red_boots.falls", "Big rubber feet soak up half of every fall"),
    ("item.tomyen.big_red_boots.stomp", "Land from 4+ blocks up to stomp nearby mobs away"),
    ("item.tomyen.big_red_boots.waddle", "Waddles a little; never tramples farmland"),
]))


# ================================================================================================== preview
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def figure_quads(walk=0.0, stand=False):
    """Every face of the figure and the boots in model space: (corners, colour function (s, t) -> rgb, normal).
    walk: the legs' swing (radians, the right leg forward). stand: an armor stand instead of Steve."""
    out = []
    leg_rot = {"right": -walk, "left": walk}

    def flat(colour):
        return lambda s, t: colour

    def box(x0, y0, z0, w, h, d, col, origin=(0, 0, 0), matrix=None, tops=None):
        c = ("_", "_", x0, x0 + w, y0, y0 + h, z0, z0 + d)
        m = matrix or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        for face, verts, _, n in cube_polys_plain(c, origin, m):
            colr = (tops or {}).get(face, col)
            out.append((verts, flat(colr), tuple(sum(m[i][k] * n[k] for k in range(3)) for i in range(3))))

    if stand:
        wood, stone = (162, 130, 78), (150, 150, 150)
        box(-6, 23, -6, 12, 1, 12, stone)
        box(-1, -7, -1, 2, 7, 2, wood)
        box(-6, 0, -1.5, 12, 3, 3, wood)
        box(-3, 3, -1, 2, 7, 2, wood)
        box(1, 3, -1, 2, 7, 2, wood)
        box(-4, 10, -1, 8, 2, 2, wood)
        for s_, x in (("right", -LEG_PIVOT), ("left", LEG_PIVOT)):
            box(-1, 0, -1, 2, 11, 2, wood, origin=(x, 12, 0), matrix=rot_x(leg_rot[s_]))
        leg_y = 11.0   # the armor stand's armor model hangs its legs from y 11
    else:
        skin, shirt, pants, hair = (190, 140, 108), (40, 170, 170), (58, 62, 150), (70, 46, 30)
        box(-4, -8, -4, 8, 8, 8, skin, tops={"top": hair})
        box(-4, 0, -2, 8, 12, 4, shirt)
        for x, s in ((-5, -1), (5, 1)):
            arm = rot_x(walk if s < 0 else -walk)
            box(-3 if s < 0 else -1, -2, -2, 4, 12, 4, skin, origin=(x, 2, 0), matrix=arm)
        for s_, x in (("right", -LEG_PIVOT), ("left", LEG_PIVOT)):
            box(-2, 0, -2, 4, 12, 4, pants, origin=(x, 12, 0), matrix=rot_x(leg_rot[s_]))
        leg_y = 12.0
    for s_, x, mirror in (("right", -LEG_PIVOT, False), ("left", LEG_PIVOT, True)):
        m = rot_x(leg_rot[s_])
        for c in BOOT:
            for face, verts, (ua, va, ub, vb), n in cube_polys(c, mirror, (x, leg_y, 0), m):
                def sample(s, t, ua=ua, va=va, ub=ub, vb=vb):
                    return tex_at(ua + (ub - ua) * s, va + (vb - va) * t)[:3]
                nn = (-n[0], n[1], n[2]) if mirror else n
                out.append((verts, sample, tuple(sum(m[i][k] * nn[k] for k in range(3)) for i in range(3))))
    return out


def cube_polys_plain(c, origin, m):
    saved = UV.get("_")
    UV["_"] = (0, 0)
    polys = cube_polys(c, False, origin, m)
    if saved is None:
        del UV["_"]
    return polys


def render(quads, view, scale, width, height, focus=None, ground=24.0):
    """Orthographic. view: 'front' (facing it), 'side' (from its right), 'three' (three quarters), 'back'."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    img = [(44, 50, 60)] * (width * height)
    zbuf = [1e9] * (width * height)
    if focus:
        ox = width / 2 - (focus[0] * ca - focus[2] * sa) * scale
        oy = height / 2 - focus[1] * scale
    else:
        ox, oy = width / 2, height - 24 - ground * scale
    gy = int(oy + ground * scale)
    for y in range(max(0, gy), height):
        for x in range(width):
            img[y * width + x] = (70, 86, 58)
    light = unit((0.3, -1.0, -0.6))
    for verts, sample, n in quads:
        # Back faces are culled, as the game culls the figure (and the boots' hidden faces are inside them anyway).
        nview = n[0] * sa + n[2] * ca
        if nview > 1e-6:
            continue
        shade = 0.55 + 0.45 * max(0.0, sum(n[i] * light[i] for i in range(3)))
        P = [(ox + (p[0] * ca - p[2] * sa) * scale, oy + p[1] * scale, p[0] * sa + p[2] * ca) for p in verts]
        A, B, C = P[1], P[0], P[2]  # s runs from corner 1 to corner 0, t from corner 1 to corner 2
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
                    zbuf[k] = depth
                    col = sample(min(s, 0.9999), min(t, 0.9999))
                    img[k] = tuple(int(col[i] * shade) for i in range(3))
    return img


def board(images, w, h, cols):
    rows = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows)
    for k, im in enumerate(images):
        bx, by = (k % cols) * w, (k // cols) * h
        for y in range(h):
            for x in range(w):
                c = im[y * w + x]
                out[(by + y) * w * cols + bx + x] = tuple(c[:3]) + (255,)
    return out, w * cols, h * rows


if PREVIEW:
    W, H = 300, 380
    rest, walking, stand = figure_quads(), figure_quads(walk=0.45), figure_quads(stand=True, walk=0.0)
    feet = (0.0, 20.5, -1.5)
    views = [render(rest, "front", 10, W, H), render(rest, "side", 10, W, H), render(rest, "three", 10, W, H),
             render(walking, "three", 10, W, H), render(walking, "side", 10, W, H), render(stand, "three", 10, W, H),
             render(rest, "three", 18, W, H, focus=feet), render(rest, "side", 18, W, H, focus=(0.0, 20.5, -1.5)),
             render(rest, "front", 18, W, H, focus=feet)]
    # The item sprite at 1x and 10x.
    icon = [(44, 50, 60)] * (W * H)
    for y in range(16):
        for x in range(16):
            c = SPRITE[y * 16 + x]
            if c[3]:
                icon[(20 + y) * W + 20 + x] = c[:3]
                for yy in range(10):
                    for xx in range(10):
                        icon[(80 + y * 10 + yy) * W + 70 + x * 10 + xx] = c[:3]
    views.append(icon)
    # The texture at 1x.
    tex = [(44, 50, 60)] * (W * H)
    for y in range(TEX_H * SCALE):
        for x in range(min(W, TEX_W * SCALE)):
            c = TEXTURE[y * TEX_W * SCALE + x]
            if c[3]:
                tex[(20 + y) * W + x] = c[:3]
    views.append(tex)
    out, bw, bh = board(views, W, H, 4)
    png(PREVIEW, bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
