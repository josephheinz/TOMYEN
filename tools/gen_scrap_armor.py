#!/usr/bin/env python3
"""Scrap armor, worn (see com.jheinz.tomyen.client.survival.ScrapArmorModel): the 3D model of all four pieces, its
texture, and the z-fighting check. The items, sprites, recipes and the rest of the kit come from gen_survival.py.

Run from anywhere: python3 tools/gen_scrap_armor.py [--preview out.png]

The model. Scavenged junk bolted and strapped over the body, built from cubes in the humanoid model's parts (each in
its part's own space: y down from the part's pivot, the front toward -z, the wearer's right toward -x), so every
piece takes the wearer's pose. It's meant to look mismatched, so nothing is mirrored: the right and left arms, legs
and boots each carry different junk. Cube sizes are whole half pixels, so at the texture's two texels a pixel every
face covers whole texels. The cubes go into ScrapArmorModel.java between the GENERATED markers, one layer per piece
plus a slim-armed chestplate (the arm cubes squeezed to a three-pixel arm).

The texture is baked, not painted per face: each texel's colour comes from its cube's material and where it sits
(on its face and in the part), so a hubcap's rings, a fan cover's wires or a saw blade's teeth run across the stepped
cubes they're built from. Every material is a short ramp with a lit top edge and a shadowed bottom edge on each face;
rust is placed in deliberate blooms and streaks running down from rivets, not noise. The saw blade's round outline and
teeth are cut out of its plate (the armor render type is alpha-tested).

Z-fighting: no two faces may share a plane, face the same way and overlap (tools/coplanar.py), across all four pieces
worn together, against the player's skin and outer layer (wide and slim), and against vanilla armor in the other
slots. The generator stops if any do.

The preview (--preview) shows the set on Steve from the front, the side, at three quarters, from the back and walking;
each piece alone from front and back; the item sprites; and the texture."""
import hashlib, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png  # noqa: E402
from coplanar import Face, fights  # noqa: E402

REPO = os.path.dirname(HERE)
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tomyen")
JAVA = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client", "survival", "ScrapArmorModel.java")
TEXTURE_PATH = os.path.join(ASSETS, "textures", "entity", "scrap_armor.png")
PREVIEW = os.path.abspath(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None

SCALE = 2           # texels per model pixel
TEX_W = 128         # the texture's width in model pixels (its height is whatever the cubes need)
PIVOTS = {"head": (0.0, 0.0, 0.0), "body": (0.0, 0.0, 0.0), "right_arm": (-5.0, 2.0, 0.0), "left_arm": (5.0, 2.0, 0.0),
          "right_leg": (-1.9, 12.0, 0.0), "left_leg": (1.9, 12.0, 0.0)}
SLIM_ARM_Y = 2.5    # slim arms hang half a pixel lower


class Cube:
    """A cube in a part: its box (x0..x1, y0..y1, z0..z1) in the part's space or, when it's tilted, in the space of
    its own child part (pivot, then rz degrees about z). opts tune its material."""

    def __init__(self, name, mat, x0, x1, y0, y1, z0, z1, pivot=None, rz=0.0, **opts):
        self.name, self.mat = name, mat
        self.box = (x0, x1, y0, y1, z0, z1)
        self.pivot, self.rz, self.opts = pivot, rz, opts
        self.part = None

    def size(self):
        x0, x1, y0, y1, z0, z1 = self.box
        return (x1 - x0, y1 - y0, z1 - z0)

    def to_part(self, p):
        """A point in the cube's own space into its part's space."""
        if self.pivot is None:
            return p
        a = math.radians(self.rz)
        c, s = math.cos(a), math.sin(a)
        return (self.pivot[0] + c * p[0] - s * p[1], self.pivot[1] + s * p[0] + c * p[1], self.pivot[2] + p[2])


def K(*args, **kw):
    return Cube(*args, **kw)


# ================================================================================================== the pieces
# Every part's own space. Head: the head is x -4..4, y -8..0, z -4..4 (its hat layer 0.5 further out). Body: x -4..4,
# y 0..12, z -2..2 (jacket 0.25 out). Arms: x -3..1 (right, wide; slim -2..1) or -1..3 (left), y -2..10, z -2..2
# (sleeves 0.25 out). Legs: x -2..2, y 0..12, z -2..2 (trousers 0.25 out), the ground at y 12.

HELMET = {"head": [
    # A dented galvanized pot, its crown knocked off-centre, a copper patch riveted over a split at the front.
    K("pot", "galv", -4.75, 4.75, -8.75, -5.25, -4.75, 4.75, rust=1),
    K("pot_crown", "galv", -4.25, 3.75, -9.25, -8.25, -4.0, 4.0, rivets=False),
    K("pot_patch", "copper", -4.25, -1.25, -8.25, -6.25, -5.25, -4.75),
    # A riveted rusty band for a brim, two bolts through it.
    K("brim", "rust", -5.25, 5.25, -5.75, -4.75, -5.25, 5.25, rivet_row=True),
    K("brim_bolt_r", "bolt", -3.75, -3.25, -5.5, -5.0, -5.75, -5.25),
    K("brim_bolt_l", "bolt", 2.75, 3.25, -5.5, -5.0, -5.75, -5.25),
    # The neck guard behind, a galvanized flap with a bolt over the right ear, a fan cover over the left.
    K("neck_guard", "rust", -4.25, 3.75, -4.75, -1.75, 4.75, 5.25),
    K("ear_flap", "galv", -5.25, -4.75, -4.75, -1.75, -2.25, 1.25, rivets=False, rust=1),
    K("ear_bolt", "bolt", -5.75, -5.25, -3.5, -3.0, -0.75, -0.25),
    K("fan_cover", "grille", 4.75, 5.25, -4.75, -1.25, -2.0, 1.5, centre=(-3.0, -0.25)),
    K("fan_bar_v", "steel", 5.0, 5.5, -4.5, -1.5, -0.5, 0.0),
    K("fan_bar_h", "steel", 5.25, 5.75, -3.25, -2.75, -1.75, 1.25),
    K("fan_hub", "steel", 5.5, 6.0, -3.5, -2.5, -0.75, 0.25),
    # Welding goggles on a leather strap round the head: two brass-rimmed cups (the left one bigger, its glass
    # cracked) and a bridge.
    K("goggle_r", "goggle", -3.75, -1.25, -4.5, -2.5, -5.75, -4.75),
    K("goggle_l", "goggle", 0.75, 3.75, -4.75, -2.25, -5.75, -4.75, crack=True),
    K("goggle_bridge", "steel", -1.25, 0.75, -3.75, -3.25, -5.25, -4.75),
    K("goggle_strap", "leather", -4.75, 4.75, -4.25, -3.25, -4.75, 4.75),
    # A short antenna with a red bulb, screwed into the crown at the back left.
    K("antenna", "steel", 2.75, 3.25, -12.25, -9.75, 1.75, 2.25),
    K("antenna_nut", "steel", 2.5, 3.5, -9.75, -9.25, 1.5, 2.5),
    K("antenna_bulb", "bulb", 2.5, 3.5, -13.0, -12.0, 1.5, 2.5),
    # A bent spoon for a crest, its bowl tipped forward.
    K("spoon_handle", "chrome", -0.25, 0.25, -11.0, -9.0, -3.75, -3.25),
    K("spoon_bowl", "chrome", -0.75, 0.75, -12.5, -11.0, -4.25, -3.75),
    # The pot's own stubby handle still sticking out the back, with its hanging ring.
    K("pot_handle", "bakelite", 1.75, 2.75, -7.75, -6.75, 4.75, 7.25),
    K("pot_handle_ring", "steel", 1.5, 3.0, -8.0, -6.5, 7.25, 7.75),
]}

HUB = (-1.0, 0.0)   # the hubcap's centre (y, z) on the right arm

CHEST = {
    "body": [
        # A rusty drum-iron wrap round the torso, under everything.
        K("drum", "iron", -4.75, 4.75, -0.75, 9.75, -2.75, 2.75),
        # Overlapping plates on the front: galvanized steel on the right, a copper sheet over it on the left, and a
        # road-sign fragment knocked crooked over the belly.
        K("galv_plate", "galv", -4.25, 0.75, 0.5, 5.5, -3.25, -2.25, rust=1),
        K("copper_sheet", "copper", -0.5, 4.5, 0.75, 5.75, -3.75, -2.75),
        K("road_sign", "sign", -3.5, 3.5, -2.0, 2.0, -0.25, 0.25, pivot=(-0.5, 7.25, -3.25), rz=-7.0),
        # The back: a big galvanized plate, a blue car-door panel patched over its lower left.
        K("back_plate", "galv", -4.25, 4.25, 0.75, 7.75, 2.75, 3.25, rust=2),
        K("door_patch", "blue", 0.5, 4.5, 5.25, 9.25, 3.0, 3.5),
        # A leather belt round the chest with a brass buckle, and a bandolier from the right shoulder to the left hip.
        K("chest_belt", "leather", -5.0, 5.0, 6.0, 7.0, -3.75, 3.75),
        K("chest_buckle", "buckle", -3.5, -2.0, 5.75, 7.25, -4.25, -3.75, metal="brass"),
        K("bandolier", "leather", -0.75, 0.75, -5.75, 5.75, -0.25, 0.25, pivot=(0.25, 4.75, -4.0), rz=-34.0, stitch=True),
        # Bolt heads through the plates.
        K("bolt_galv_top", "bolt", -4.0, -3.5, 0.75, 1.25, -3.75, -3.25),
        K("bolt_galv_low", "bolt", -4.0, -3.5, 4.25, 4.75, -3.75, -3.25),
        K("bolt_copper_top", "bolt", 3.5, 4.0, 1.25, 1.75, -4.25, -3.75),
        K("bolt_copper_low", "bolt", 3.5, 4.0, 4.75, 5.25, -4.25, -3.75),
        K("bolt_back_r", "bolt", -3.75, -3.25, 1.25, 1.75, 3.25, 3.75),
        K("bolt_back_l", "bolt", 3.25, 3.75, 1.25, 1.75, 3.25, 3.75),
    ],
    "right_arm": [
        # A galvanized pauldron with a chrome hubcap bolted on its outside: stepped discs for a domed, round cap.
        K("pauldron", "galv", -3.75, 1.75, -3.0, 1.0, -3.0, 3.0, rivets=False),
        K("hubcap_tall", "hubcap", -4.25, -3.75, -3.5, 1.5, -1.5, 1.5, centre=HUB),
        K("hubcap_wide", "hubcap", -4.0, -3.5, -2.75, 0.75, -2.0, 2.0, centre=HUB),
        K("hubcap_lip", "hubcap", -3.875, -3.375, -2.5, 0.5, -2.5, 2.5, centre=HUB),
        K("hubcap_dome", "hubcap", -4.75, -4.25, -2.5, 0.5, -1.0, 1.0, centre=HUB),
        K("hubcap_cap", "hubcap", -5.25, -4.75, -1.5, -0.5, -0.5, 0.5, centre=HUB),
        # A section of rusty pipe round the upper arm, a galvanized bracer strapped on the forearm.
        K("arm_pipe", "pipe", -3.5, 1.5, 2.5, 5.0, -2.5, 2.5),
        K("bracer", "galv", -3.5, 1.5, 6.0, 9.0, -2.5, 2.5, rivets=False, rust=1),
        K("bracer_strap", "leather", -3.75, 1.75, 7.25, 7.75, -2.75, 2.75),
        K("bracer_bolt", "bolt", -4.0, -3.5, 6.25, 6.75, -0.25, 0.25),
    ],
    "left_arm": [
        # A cut length of tire draped over the shoulder, a circular saw blade standing up out of it.
        K("tire_top", "rubber", -1.75, 3.75, -3.25, -1.75, -3.0, 3.0, tread="tire"),
        K("tire_side", "rubber", 3.25, 4.75, -2.75, 1.25, -2.75, 2.75, tread="tire"),
        K("tire_corner", "rubber", 2.25, 4.25, -3.5, -1.0, -2.5, 2.5, tread="tire"),
        K("saw_blade", "saw", 1.75, 2.25, -5.5, 0.0, -2.75, 2.75, centre=(-2.75, 0.0), radius=2.75),
        # A copper sheet bent round the upper arm, a ribbed tin can on the forearm tied on with copper wire.
        K("arm_copper", "copper", -0.5, 3.5, 1.5, 5.0, -2.5, 2.0),
        K("arm_can", "tin", -1.5, 3.5, 5.5, 8.5, -2.5, 2.5, label=(6.25, 7.75)),
        K("arm_wire", "wire", -1.75, 3.75, 6.75, 7.25, -2.75, 2.75),
    ],
}

LEGGINGS = {
    "body": [
        # A belt with a steel buckle off to the left, a rusty tasset hanging over the right hip, a tin can holster
        # on the back.
        K("belt", "leather", -4.5, 4.5, 9.75, 11.25, -2.5, 2.5, stitch=True),
        K("belt_buckle", "buckle", 0.25, 2.25, 9.5, 11.5, -3.25, -2.25, metal="steel"),
        K("tasset", "rust", -4.5, -1.0, 10.25, 12.75, -3.25, -2.75),
        K("tasset_bolt_out", "bolt", -4.0, -3.5, 10.75, 11.25, -3.75, -3.25),
        K("tasset_bolt_in", "bolt", -2.0, -1.5, 10.75, 11.25, -3.75, -3.25),
        K("hip_can", "tin", 1.25, 3.75, 9.25, 11.75, 2.25, 3.75, label=(10.0, 11.0)),
    ],
    "right_leg": [
        # A ribbed tin can round the thigh (two crossed blocks for its round), strapped on; a flattened soda can
        # strapped over the knee.
        K("thigh_can", "tin", -2.75, 1.75, 0.75, 4.75, -2.75, 2.75),
        K("thigh_can_round", "tin", -3.25, 1.25, 1.0, 4.5, -2.5, 2.5),
        K("thigh_strap", "leather", -3.5, 1.5, 2.5, 3.0, -3.25, 3.25),
        K("knee_can", "can", -2.5, 1.0, 5.0, 8.0, -3.25, -2.25),
        K("knee_strap", "leather", -2.75, 1.25, 6.25, 6.75, -2.5, 2.5),
    ],
    "left_leg": [
        # A section of flanged iron pipe over the front and outside of the thigh, bolted at the top flange; a
        # leather knee pad studded with bottle caps.
        K("thigh_pipe", "pipe", -1.75, 2.75, 0.5, 5.0, -2.75, 1.75),
        K("pipe_flange_top", "pipe", -1.25, 3.25, 0.25, 1.25, -3.25, 2.75, flange=True),
        K("flange_bolt_in", "bolt", 0.0, 0.5, 0.5, 1.0, -3.75, -3.25),
        K("flange_bolt_out", "bolt", 2.0, 2.5, 0.5, 1.0, -3.75, -3.25),
        K("pipe_strap", "leather", -1.5, 3.5, 2.75, 3.25, -3.25, 2.75),
        K("knee_pad", "leather", -1.25, 2.75, 5.25, 8.25, -2.75, -2.25, stitch=True),
        K("knee_pad_strap", "leather", -1.5, 2.5, 6.25, 6.75, -2.5, 2.5),
        K("cap_red", "cap", -0.75, 0.25, 5.75, 6.75, -3.25, -2.75, colour=(176, 42, 38)),
        K("cap_blue", "cap", 0.75, 1.75, 6.25, 7.25, -3.25, -2.75, colour=(44, 86, 156)),
        K("cap_gold", "cap", -0.5, 0.5, 6.75, 7.75, -3.25, -2.75, colour=(200, 160, 62)),
    ],
}

BOOTS = {
    "right_leg": [
        # A worn work boot on a tire-rubber sole that stands out round it, a galvanized toe cap and shin guard, a
        # rusty plate on the outside, a strap with a buckle.
        K("boot", "boot", -2.75, 1.75, 9.25, 11.75, -3.25, 2.75),
        K("sole", "rubber", -3.25, 1.75, 11.75, 12.75, -3.75, 3.25, tread="sole"),
        K("toe_cap", "galv", -2.5, 1.5, 10.25, 12.25, -4.0, -3.0, rivets=False),
        K("shin_guard", "galv", -2.25, 1.25, 7.0, 10.0, -3.5, -2.5, rivets=False, rust=1),
        K("shin_bolt_top", "bolt", -1.75, -1.25, 7.5, 8.0, -4.0, -3.5),
        K("shin_bolt_low", "bolt", 0.25, 0.75, 9.0, 9.5, -4.0, -3.5),
        K("ankle_plate", "rust", -3.0, -2.5, 7.5, 10.0, -2.0, 1.5),
        K("boot_strap", "leather", -3.25, 1.75, 8.0, 9.0, -3.75, 2.75),
        K("boot_buckle", "buckle", -3.75, -3.25, 7.75, 9.25, -1.0, 0.5, metal="steel"),
    ],
    "left_leg": [
        # The odd boot: a rusty dented toe, a shin guard cut from a blue car door, a galvanized strip down the
        # outside, copper wire round it all and a cuff of inner tube at the top.
        K("boot", "boot", -1.75, 2.75, 9.25, 11.75, -3.25, 2.75),
        K("sole", "rubber", -1.75, 3.25, 11.75, 12.75, -3.75, 3.25, tread="sole"),
        K("toe_cap", "rust", -1.25, 2.25, 10.25, 12.25, -4.0, -3.0),
        K("shin_panel", "blue", -1.5, 2.5, 8.0, 10.5, -3.75, -2.75),
        K("outer_strip", "galv", 2.75, 3.25, 7.75, 10.75, -2.25, 1.75, rivets=False),
        K("boot_wire", "wire", -1.75, 3.75, 8.5, 9.0, -4.0, 3.0),
        K("cuff", "rubber", -1.5, 3.0, 7.5, 8.0, -3.0, 3.0),
        K("panel_bolt_top", "bolt", -0.75, -0.25, 9.5, 10.0, -4.25, -3.75),
        K("panel_bolt_low", "bolt", 1.25, 1.75, 9.5, 10.0, -4.25, -3.75),
    ],
}

# The arm pieces sit an eighth of a pixel down and forward of the grid everything else is on: the arms hang against
# the body, and this keeps their faces off the body pieces' planes (and the leggings', and vanilla armor's) at rest.
ARM_NUDGE = (0.125, -0.125)
for _cube in CHEST["right_arm"] + CHEST["left_arm"]:
    _x0, _x1, _y0, _y1, _z0, _z1 = _cube.box
    _cube.box = (_x0, _x1, _y0 + ARM_NUDGE[0], _y1 + ARM_NUDGE[0], _z0 + ARM_NUDGE[1], _z1 + ARM_NUDGE[1])
    if "centre" in _cube.opts:
        _cube.opts["centre"] = (_cube.opts["centre"][0] + ARM_NUDGE[0], _cube.opts["centre"][1] + ARM_NUDGE[1])

PIECES = {"helmet": HELMET, "chestplate": CHEST, "leggings": LEGGINGS, "boots": BOOTS}
SLOT_OF = {"helmet": "head", "chestplate": "chest", "leggings": "legs", "boots": "feet"}


def slim_arm(cube, side):
    """The cube squeezed onto a slim arm: the outer side moves in a pixel, the inner side stays, and whatever's
    between is scaled to fit (then rounded to whole half pixels about its middle)."""
    x0, x1, y0, y1, z0, z1 = cube.box

    def squeeze(x):
        if side == "right":
            return x + 1.0 if x <= -3.0 else x if x >= 1.0 else -2.0 + (x + 3.0) * 0.75
        return x - 1.0 if x >= 3.0 else x if x <= -1.0 else -1.0 + (x + 1.0) * 0.75
    a, b = squeeze(x0), squeeze(x1)
    mid, w = (a + b) / 2.0, max(0.5, round((b - a) * 2.0) / 2.0)
    return Cube(cube.name + "_slim", cube.mat, mid - w / 2.0, mid + w / 2.0, y0, y1, z0, z1, **dict(cube.opts))


CHEST_SLIM = {"body": CHEST["body"], "right_arm": [slim_arm(c, "right") for c in CHEST["right_arm"]],
              "left_arm": [slim_arm(c, "left") for c in CHEST["left_arm"]]}

LAYERS = [("helmet", "createHelmet", HELMET), ("chestplate", "createChestplate", CHEST),
          ("chestplate_slim", "createChestplateSlim", CHEST_SLIM), ("leggings", "createLeggings", LEGGINGS),
          ("boots", "createBoots", BOOTS)]

for layer, _, parts in LAYERS:
    for part, cubes in parts.items():
        for c in cubes:
            c.part = part
            for s in c.size():
                assert s > 0 and abs(s * SCALE - round(s * SCALE)) < 1e-9, (layer, c.name, s)
            if part.endswith("_leg"):
                inner = c.box[1] if part == "right_leg" else -c.box[0]
                assert inner <= 1.75, (layer, c.name)  # clear of the other leg's pieces


def all_cubes():
    seen, out = set(), []
    for _, _, parts in LAYERS:
        for cubes in parts.values():
            for c in cubes:
                if id(c) not in seen:
                    seen.add(id(c))
                    out.append(c)
    return out


def uid(c):
    return f"{c.part}.{c.name}"


# ================================================================================================== texture layout
def pack():
    """Integer texture offsets (in model pixels) for each cube's box unwrap, shelf-packed, and the height needed."""
    cubes = sorted(all_cubes(), key=lambda c: -(c.size()[2] + c.size()[1]))
    offs, x, y, shelf = {}, 0, 0, 0
    for c in cubes:
        w, h, d = c.size()
        fw, fh = math.ceil(2 * (w + d)), math.ceil(d + h)
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        offs[uid(c)] = (x, y)
        x += fw + 1
        shelf = max(shelf, fh + 1)
    height = y + shelf
    return offs, int(math.ceil(height / 16.0) * 16)


UV, TEX_H = pack()


def cube_polys(box, uv, origin=(0.0, 0.0, 0.0), matrix=None, to_part=None):
    """The six faces of a box as the game builds them (ModelPart.Cube): for each, its corners (through to_part, then
    posed by matrix and origin), its uv rect (u0, v0, u1, v1) in texture pixels (corner 1 at (u0, v0), corner 0 at
    (u1, v0), corner 2 at (u0, v1)) and its outward normal (before posing)."""
    x0, x1, y0, y1, z0, z1 = box
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    u, v = uv
    m = matrix or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    tp = to_part or (lambda p: p)

    def P(x, y, z):
        q = tp((x, y, z))
        return tuple(origin[i] + m[i][0] * q[0] + m[i][1] * q[1] + m[i][2] * q[2] for i in range(3))
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


# ================================================================================================== materials
def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def ramp(cols, t):
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


GALV = [(66, 72, 80), (96, 104, 112), (128, 136, 144), (160, 168, 174), (196, 202, 206)]
RUST = [(70, 34, 20), (104, 50, 27), (140, 72, 37), (174, 102, 52), (204, 138, 80)]
IRON = [(40, 38, 38), (58, 54, 52), (78, 72, 68), (100, 92, 86), (126, 116, 106)]
COPPER = [(92, 44, 26), (136, 68, 38), (180, 102, 58), (212, 140, 90), (238, 182, 134)]
VERDIGRIS = (84, 150, 128)
SIGN_Y = [(126, 98, 38), (164, 132, 56), (198, 164, 76), (222, 192, 102), (238, 214, 138)]
SIGN_K = [(30, 28, 28), (42, 40, 38), (56, 52, 50), (72, 68, 64), (90, 86, 80)]
BLUE = [(30, 46, 72), (44, 66, 98), (62, 90, 126), (86, 116, 150), (116, 146, 176)]
LEATHER = [(30, 22, 18), (44, 32, 26), (60, 44, 34), (78, 58, 44), (98, 76, 58)]
BOOT = [(30, 28, 27), (42, 39, 37), (56, 52, 49), (72, 67, 62), (90, 84, 77)]
RUBBER = [(16, 16, 18), (26, 26, 28), (38, 38, 40), (52, 52, 55), (70, 70, 74)]
STEEL = [(62, 66, 72), (96, 102, 108), (138, 144, 150), (182, 188, 192), (224, 228, 230)]
CHROME = [(54, 60, 68), (100, 108, 118), (156, 164, 172), (206, 212, 218), (244, 246, 248)]
TIN = [(84, 86, 88), (116, 118, 120), (148, 150, 152), (178, 180, 180), (206, 208, 206)]
LABEL = [(104, 40, 34), (136, 56, 44), (166, 78, 60), (190, 106, 84)]
CREAM = (214, 202, 172)
CAN = [(90, 18, 20), (132, 28, 28), (172, 42, 38), (202, 70, 58), (226, 118, 102)]
GLASS = [(14, 28, 22), (22, 44, 34), (34, 66, 50), (56, 96, 76), (150, 192, 170)]
BRASS = [(80, 58, 22), (120, 88, 34), (162, 126, 52), (200, 164, 80), (230, 204, 126)]
BAKELITE = [(12, 10, 10), (24, 20, 20), (38, 32, 30), (56, 48, 44), (82, 72, 66)]
BULB = [(90, 10, 10), (150, 20, 18), (206, 40, 30), (238, 92, 72), (255, 196, 178)]
GRILLE_BACK = (26, 28, 30)


def hash01(*key):
    h = hashlib.md5("|".join(str(k) for k in key).encode()).digest()
    return h[0] / 255.0, h[1] / 255.0, h[2] / 255.0, h[3] / 255.0


class Texel:
    """Where a texel sits: its face, its place on the face (a across, b down, in pixels from the face's top left as
    seen from outside; fw, fh the face's size), its texel indices (i, j of W, H) and its point in the part (P)."""
    __slots__ = ("cube", "face", "a", "b", "fw", "fh", "i", "j", "W", "H", "P", "plate", "side")


def face_coords(face, box, p):
    x0, x1, y0, y1, z0, z1 = box
    if face == "north":
        return p[0] - x0, p[1] - y0, x1 - x0, y1 - y0
    if face == "south":
        return x1 - p[0], p[1] - y0, x1 - x0, y1 - y0
    if face == "west":
        return p[2] - z0, p[1] - y0, z1 - z0, y1 - y0
    if face == "east":
        return z1 - p[2], p[1] - y0, z1 - z0, y1 - y0
    if face == "top":
        return p[0] - x0, p[2] - z0, x1 - x0, z1 - z0
    return p[0] - x0, z1 - p[2], x1 - x0, z1 - z0


def thin_faces(c):
    """The faces across a cube's thinnest side: a plate's two broad faces."""
    w, h, d = c.size()
    m = min(w, h, d)
    if m == d:
        return ("north", "south")
    if m == w:
        return ("west", "east")
    return ("top", "bottom")


def light(tx):
    """The face's own shading as a ramp position: a soft top-to-bottom fall on the sides, a lit top edge, a
    shadowed bottom edge and darker side edges; tops bright, bottoms dark."""
    if tx.face == "top":
        t = 0.72
        if min(tx.a, tx.fw - tx.a, tx.b, tx.fh - tx.b) < 0.5 and tx.fw > 1 and tx.fh > 1:
            t += 0.08
        return t
    if tx.face == "bottom":
        return 0.3
    t = 0.56 + 0.14 * (0.5 - tx.b / tx.fh) * min(1.0, tx.fh / 3.0)
    if tx.fh >= 1.0:
        if tx.j == 0:
            t += 0.2
        elif tx.j == tx.H - 1:
            t -= 0.14
    if tx.fw >= 1.0 and (tx.i == 0 or tx.i == tx.W - 1):
        t -= 0.06
    return t


def rivet(tx, inset=1):
    """'hi' or 'lo' where a plate face's corner rivets are (a bright head and its shadow below), else None."""
    if not tx.plate or tx.fw < 2.5 or tx.fh < 2.5:
        return None
    for ri in (inset, tx.W - 1 - inset):
        for rj in (inset, tx.H - 1 - inset):
            if tx.i == ri and tx.j == rj:
                return "hi"
            if tx.i == ri and tx.j == rj + 1:
                return "lo"
    return None


def rivet_streak(tx, inset=1):
    """How far down a rust streak from one of the top rivets this texel is (1 at its head), or 0."""
    if not tx.plate or tx.side is False or tx.fw < 2.5 or tx.fh < 2.5:
        return 0.0
    for ri in (inset, tx.W - 1 - inset):
        if tx.i == ri and inset + 2 <= tx.j <= inset + 5:
            return 1.0 - (tx.j - inset - 2) / 4.0
    return 0.0


def rust_blooms(tx, count):
    """Deliberate rust on a plate face: a few soft-edged blooms (dark core, orange body, a pale fringe) with a short
    drip under each. Returns a colour or None."""
    if not tx.plate or count <= 0 or tx.fw * tx.fh < 5:
        return None
    for k in range(count):
        h = hash01(tx.cube.name, tx.cube.part, tx.face, k)
        cx, cy = tx.fw * (0.22 + 0.56 * h[0]), tx.fh * (0.25 + 0.5 * h[1])
        r = 0.65 + 0.6 * h[2]
        dx, dy = (tx.a - cx) / (r * 1.35), (tx.b - cy) / r
        d = math.sqrt(dx * dx + dy * dy)
        if d < 0.5:
            return RUST[1]
        if d < 0.85:
            return RUST[2]
        if d < 1.05:
            return RUST[3]
        # The drip: one texel wide, straight down from the bloom, fading out.
        if tx.side and abs(tx.a - cx) < 0.25 and cy < tx.b < cy + r + 1.5:
            f = (tx.b - cy - r) / 1.5
            return RUST[2] if f < 0.4 else RUST[3]
    return None


def edge_chip(tx, seed):
    """Paint chipped off along a painted plate's edges (the metal shows through): whole one-pixel runs, picked by
    position, so they read as chips rather than speckle."""
    if not tx.plate:
        return False
    near = min(tx.a, tx.fw - tx.a, tx.b, tx.fh - tx.b)
    if near >= 0.5:
        return False
    along = tx.b if (tx.a < 0.5 or tx.fw - tx.a < 0.5) else tx.a
    return hash01(seed, tx.face, int(along), tx.a < 0.5, tx.b < 0.5)[0] > 0.62


def m_galv(tx):
    r = rivet(tx) if tx.cube.opts.get("rivets", True) else None
    if r:
        return STEEL[4] if r == "hi" else GALV[0]
    bloom = rust_blooms(tx, tx.cube.opts.get("rust", 0))
    if bloom:
        return bloom
    t = light(tx)
    # Galvanizing's faint spangle: broad soft bands, a shade either way.
    P = tx.P
    t += 0.05 * math.sin(P[0] * 1.1 + P[1] * 0.8 + P[2] * 0.6) * math.sin(P[1] * 0.9 - P[0] * 0.5)
    c = ramp(GALV, t)
    s = rivet_streak(tx) if tx.cube.opts.get("rivets", True) else 0.0
    if s:
        c = lerp(c, RUST[2], 0.25 + 0.45 * s)
    return c


def m_rust(tx):
    r = rivet(tx) if tx.plate else None
    if tx.cube.opts.get("rivet_row") and tx.side and tx.fh >= 1.0:
        # A row of rivets along the brim's middle, one every two pixels.
        if tx.j == tx.H // 2 - 1 + (tx.H % 2) and (tx.i % 4) == 1:
            return IRON[4]
        if tx.j == tx.H // 2 + (tx.H % 2) and (tx.i % 4) == 1:
            return RUST[0]
    if r:
        return IRON[3] if r == "hi" else RUST[0]
    t = light(tx) - 0.06
    P = tx.P
    # Pitting: two or three broad darker patches over the plate, and flaky lighter rust round them.
    f = math.sin(P[0] * 1.3 + P[2] * 0.9 + 0.7) * math.cos(P[1] * 1.1 - P[0] * 0.4 + P[2] * 0.3)
    if f > 0.62:
        t -= 0.22
    elif f > 0.5:
        t -= 0.1
    elif f < -0.72:
        t += 0.1
    return ramp(RUST, t)


def m_iron(tx):
    """Drum iron: dark steel with raised hoops every three pixels and rust running down from them."""
    t = light(tx) - 0.05
    P = tx.P
    if tx.side:
        band = P[1] % 3.0
        if band < 0.5:
            t += 0.22
        elif band < 1.0:
            t -= 0.12
        # Rust streaks below the hoops at a few places round the drum.
        key = P[0] if tx.face in ("north", "south") else P[2]
        for col in (-3.25, -0.75, 1.75, 3.75):
            if abs(key - col) < 0.25 and 1.0 <= band < 2.75:
                return lerp(ramp(IRON, t), RUST[2], 0.75 - 0.25 * (band - 1.0))
        if 1.0 <= band < 2.0 and abs(((key + 10.0) % 2.5) - 1.25) < 0.25:
            return lerp(ramp(IRON, t), RUST[1], 0.35)
    return ramp(IRON, t)


def m_copper(tx):
    r = rivet(tx)
    if r:
        return STEEL[3] if r == "hi" else COPPER[0]
    t = light(tx)
    P = tx.P
    # Hammered: soft broad waves of light.
    t += 0.06 * math.sin(P[0] * 1.4 + P[1] * 0.7) * math.cos(P[1] * 1.2 - P[2] * 0.5)
    c = ramp(COPPER, t)
    # Verdigris gathers along the bottom edge and under the rivets.
    if tx.side and tx.fh - tx.b < 0.5:
        return lerp(c, VERDIGRIS, 0.65)
    if tx.side and tx.fh - tx.b < 1.0 and tx.i % 3 != 1:
        return lerp(c, VERDIGRIS, 0.35)
    s = rivet_streak(tx)
    if s:
        return lerp(c, VERDIGRIS, 0.2 + 0.4 * s)
    return c


def m_sign(tx):
    """A road-sign fragment: faded yellow and black hazard stripes on the face, chipped at the edges to galvanized
    steel; bare steel on the cut edges and the back."""
    if tx.face != "north":
        return ramp(GALV, light(tx) - 0.05)
    r = rivet(tx)
    if r:
        return STEEL[4] if r == "hi" else SIGN_K[0]
    if edge_chip(tx, tx.cube.name):
        return ramp(GALV, light(tx))
    t = light(tx)
    stripe = int(math.floor((tx.a + tx.b) / 1.5)) % 2
    c = ramp(SIGN_K if stripe else SIGN_Y, t)
    bloom = rust_blooms(tx, 1)
    return bloom or c


def m_blue(tx):
    """A panel cut from a blue car door: faded paint, two long scratches through to the metal, chipped edges."""
    if not tx.plate:
        return ramp(GALV, light(tx) - 0.05)
    if tx.face in ("south", "west", "top"):
        return ramp(GALV, light(tx) - 0.08)
    r = rivet(tx)
    if r:
        return STEEL[4] if r == "hi" else BLUE[0]
    if edge_chip(tx, tx.cube.name):
        return ramp(GALV, light(tx))
    for k, (x0, slope) in enumerate(((0.6, 0.55), (2.2, 0.35))):
        if abs(tx.a - (x0 + slope * tx.b)) < 0.25 and 0.6 < tx.b < tx.fh - 0.6:
            return TIN[3] if k == 0 else TIN[2]
    bloom = rust_blooms(tx, 1)
    if bloom:
        return bloom
    return ramp(BLUE, light(tx) - 0.04)


def m_leather(tx):
    t = light(tx) - 0.04
    c = ramp(LEATHER, t)
    if tx.cube.opts.get("stitch") and tx.side and tx.fh >= 1.5 and tx.fw >= 1.5:
        # Stitching inset along the long edges.
        if (tx.j == 1 or tx.j == tx.H - 2) and tx.i % 2 == 0 and 0 < tx.i < tx.W - 1:
            return lerp(c, (196, 168, 120), 0.6)
    elif tx.side and tx.H == 2 and tx.W > 2:
        # A narrow strap: a crease down its middle at every other texel, so it isn't flat.
        if tx.j == 1 and tx.i % 4 == 2:
            return ramp(LEATHER, t - 0.12)
    return c


def m_boot(tx):
    t = light(tx) - 0.02
    P = tx.P
    if tx.side:
        # Creases across the instep and scuffs on the toe.
        if abs((P[1] - 9.25) % 1.5 - 0.75) < 0.25 and tx.face == "north":
            t -= 0.12
        if tx.face == "north" and tx.b > tx.fh - 1.0:
            t += 0.08
    return ramp(BOOT, t)


def m_rubber(tx):
    t = light(tx) - 0.05
    tread = tx.cube.opts.get("tread")
    if tread == "sole":
        if tx.side:
            # Tire tread round the sole's edge: blocks one pixel wide, grooves between.
            if tx.i % 2 == 1:
                t -= 0.18
            if tx.j == 0:
                t += 0.12
        elif tx.face == "top":
            t = 0.42
    elif tread == "tire":
        outer = tx.face == "east" or tx.face == "top"
        if outer:
            # Chunky tread blocks, staggered.
            if ((tx.i // 2) + (tx.j // 2)) % 2 == 0:
                t += 0.14
            else:
                t -= 0.08
            if tx.i % 2 == 0 and tx.j % 2 == 0:
                t += 0.06
        else:
            # The sidewall: a raised ring line.
            if tx.j == tx.H // 2:
                t += 0.12
    return ramp(RUBBER, t)


def m_steel(tx):
    return ramp(STEEL, light(tx))


def m_chrome(tx):
    return ramp(CHROME, light(tx) + 0.08)


def m_bolt(tx):
    return {"top": STEEL[4], "bottom": STEEL[0], "north": STEEL[3], "south": STEEL[2]}.get(tx.face, STEEL[2])


def m_buckle(tx):
    metal = BRASS if tx.cube.opts.get("metal") == "brass" else STEEL
    if tx.plate and tx.face in ("north", "west", "east") and tx.W >= 3 and tx.H >= 3:
        edge = tx.i in (0, tx.W - 1) or tx.j in (0, tx.H - 1)
        if not edge:
            # The frame's hole: the strap shows through, crossed by the prong.
            if tx.i == tx.W // 2:
                return metal[3]
            return LEATHER[0]
        return metal[4] if tx.j == 0 else metal[1] if tx.j == tx.H - 1 else metal[3]
    return ramp(metal, light(tx))


def disc(tx, centre):
    """Distance and angle of the texel from a disc's centre, in the plane of the disc (y, z for a side disc)."""
    cy, cz = centre
    dy, dz = tx.P[1] - cy, tx.P[2] - cz
    return math.sqrt(dy * dy + dz * dz), math.atan2(dy, dz)


def m_hubcap(tx):
    """A chrome hubcap seen on its outer (west) faces: a polished centre cap, a dark ring, a dished face with five
    lug holes, a bright raised ring and a lip; lit from the top front."""
    if tx.face != "west":
        return ramp(CHROME, light(tx) + 0.02)
    centre = tx.cube.opts["centre"]
    r, ang = disc(tx, centre)
    # Specular sweep: brighter toward the top front.
    spec = 0.12 * max(0.0, -math.sin(ang + 0.6))
    if r < 0.55:
        return CHROME[4] if tx.P[1] < centre[0] else CHROME[3]
    if r < 0.95:
        return CHROME[1]
    if r < 1.9:
        for k in range(5):
            a = k * 2 * math.pi / 5 + 0.3
            ly, lz = centre[0] + 1.42 * math.sin(a), centre[1] + 1.42 * math.cos(a)
            if abs(tx.P[1] - ly) < 0.3 and abs(tx.P[2] - lz) < 0.3:
                return CHROME[0]
        return ramp(CHROME, 0.5 + (r - 0.95) * 0.15 + spec)
    if r < 2.35:
        return ramp(CHROME, 0.86 + spec)
    return ramp(CHROME, 0.4 + spec - (0.12 if r > 2.75 else 0.0))


def m_saw(tx):
    """A circular saw blade cut out of its plate: sixteen hooked teeth round the rim, a brushed-steel body with a
    darker ring, an arbor hole. None is see-through."""
    centre, radius = tx.cube.opts["centre"], tx.cube.opts["radius"]
    r, ang = disc(tx, centre)
    if r > radius:
        return None
    if r > radius - 0.6:
        phase = (ang / (2 * math.pi) * 16.0) % 1.0
        if phase > 0.55:
            return None
        return STEEL[1] if phase > 0.4 else STEEL[3]
    if tx.face not in ("west", "east"):
        return STEEL[2]
    if r < 0.4:
        return (20, 20, 22)
    if r < 0.75:
        return STEEL[3]
    if abs(r - 1.75) < 0.2:
        return STEEL[1]
    return ramp(STEEL, 0.55 + 0.25 * math.cos(2 * ang + 0.8) + (0.08 if tx.P[1] < centre[0] else -0.04))


def m_goggle(tx):
    """A brass-rimmed goggle cup, its dark green glass on the front with a glint, the left one cracked."""
    if tx.face != "north":
        return ramp(BRASS, light(tx) - 0.05)
    if tx.i in (0, tx.W - 1) or tx.j in (0, tx.H - 1):
        return BRASS[4] if tx.j == 0 else BRASS[2] if tx.j < tx.H - 1 else BRASS[1]
    if tx.cube.opts.get("crack"):
        # A crack from the top right corner across the glass.
        path = {(tx.W - 2, 1), (tx.W - 3, 2), (tx.W - 3, 3), (tx.W - 4, 3)}
        if (tx.i, tx.j) in path:
            return GLASS[4]
    if tx.i - tx.j == 1 and tx.i < tx.W // 2 + 1:
        return GLASS[3]
    return ramp(GLASS, 0.55 - 0.35 * tx.b / tx.fh)


def m_grille(tx):
    """An old fan's wire cover: wire rings and spokes over darkness, in a galvanized frame."""
    if tx.face != "east":
        return ramp(GALV, light(tx))
    r, ang = disc(tx, tx.cube.opts["centre"])
    if r > 1.7:
        return ramp(GALV, light(tx) + 0.05)
    for ring in (0.6, 1.15, 1.6):
        if abs(r - ring) < 0.17:
            return STEEL[4] if tx.P[1] < tx.cube.opts["centre"][0] else STEEL[3]
    if r > 0.35 and abs(math.sin(2 * (ang + 0.4))) < 0.22:
        return STEEL[3]
    return GRILLE_BACK


def m_tin(tx):
    """Tin cans: ribbed light tin, a torn faded label band (red with a cream stripe), bright cut rims."""
    P = tx.P
    if tx.face in ("top", "bottom"):
        return ramp(TIN, 0.85 if tx.face == "top" else 0.35)
    t = light(tx)
    lo, hi = tx.cube.opts.get("label", (99.0, 99.0))
    on_label = lo <= P[1] < hi
    if on_label:
        # The label's torn off part of the way round.
        tear = tx.fw * 0.62 + 0.5 * math.sin(P[1] * 4.0)
        if tx.face == "south" or (tx.face in ("west", "east") and tx.a > tear):
            on_label = False
    if on_label:
        mid = (lo + hi) / 2.0
        if abs(P[1] - mid) < 0.25:
            return lerp(CREAM, (180, 168, 140), 0.3 if tx.j % 2 else 0.0)
        return ramp(LABEL, t - 0.1)
    # Corrugation: a bright rib and its shadow every pixel and a half.
    rib = (P[1] * 2.0) % 3.0
    if rib < 1.0:
        t += 0.12
    elif rib < 2.0:
        t -= 0.1
    return ramp(TIN, t)


def m_can(tx):
    """A flattened soda can: red paint, a silver rim top and bottom, a crushed crease across it."""
    if not tx.plate or tx.face != "north":
        return ramp(TIN, light(tx))
    if tx.j == 0 or tx.j == tx.H - 1:
        return TIN[4] if tx.j == 0 else TIN[1]
    t = light(tx)
    # One crushed crease across it: a shadowed fold with a lit edge above.
    fold = tx.fh * 0.5 + 0.3 * (tx.a - tx.fw / 2.0)
    if 0.0 <= tx.b - fold < 0.5:
        t -= 0.2
    elif -0.5 <= tx.b - fold < 0.0:
        t += 0.12
    return ramp(CAN, t)


def m_cap(tx):
    """A bottle cap: its colour with a glint and a shadow, tin round the edge."""
    col = tx.cube.opts["colour"]
    if tx.face == "north":
        # Two texels a side: a lit top left, the colour, and a shadowed bottom right.
        if (tx.i, tx.j) == (0, 0):
            return lerp(col, (255, 255, 255), 0.4)
        if (tx.i, tx.j) == (1, 1):
            return lerp(col, (0, 0, 0), 0.3)
        return col
    return TIN[3] if tx.face == "top" else TIN[1]


def m_wire(tx):
    """Twisted copper wire: a bright diagonal every third texel along it, darker below."""
    if tx.face == "bottom":
        return COPPER[0]
    k = (tx.i + tx.j) % 3
    return COPPER[3] if k == 0 else COPPER[2] if k == 1 else COPPER[1]


def m_pipe(tx):
    """Rusty iron pipe: dark iron under broad rust; a flange's face has a bolt circle line."""
    t = light(tx) - 0.04
    P = tx.P
    f = math.sin(P[0] * 1.2 + P[1] * 0.9) * math.cos(P[2] * 1.1 - P[1] * 0.6)
    base = ramp(IRON, t)
    if f > 0.15:
        base = lerp(base, ramp(RUST, t), min(1.0, (f - 0.15) * 2.5))
    if tx.cube.opts.get("flange") and tx.side and tx.H >= 2 and tx.j == tx.H // 2:
        base = lerp(base, IRON[0], 0.4)
    elif not tx.cube.opts.get("flange") and tx.side and tx.j == 1:
        base = lerp(base, IRON[4], 0.3)
    return base


def m_bakelite(tx):
    return ramp(BAKELITE, light(tx))


def m_bulb(tx):
    if tx.face == "north" and (tx.i, tx.j) == (0, 0):
        return BULB[4]
    return ramp(BULB, light(tx) + 0.05)


MATERIALS = {"galv": m_galv, "rust": m_rust, "iron": m_iron, "copper": m_copper, "sign": m_sign, "blue": m_blue,
             "leather": m_leather, "boot": m_boot, "rubber": m_rubber, "steel": m_steel, "chrome": m_chrome,
             "bolt": m_bolt, "buckle": m_buckle, "hubcap": m_hubcap, "saw": m_saw, "goggle": m_goggle,
             "grille": m_grille, "tin": m_tin, "can": m_can, "cap": m_cap, "wire": m_wire, "pipe": m_pipe,
             "bakelite": m_bakelite, "bulb": m_bulb}


def bake():
    w, h = TEX_W * SCALE, TEX_H * SCALE
    tex = [(0, 0, 0, 0)] * (w * h)
    for c in all_cubes():
        plate = thin_faces(c)
        for face, verts, (ua, va, ub, vb), _ in cube_polys(c.box, UV[uid(c)]):
            A = verts[1]
            eu = [verts[0][i] - verts[1][i] for i in range(3)]
            ev = [verts[2][i] - verts[1][i] for i in range(3)]
            tx_lo, tx_hi = int(round(min(ua, ub) * SCALE)), int(round(max(ua, ub) * SCALE))
            ty_lo, ty_hi = int(round(min(va, vb) * SCALE)), int(round(max(va, vb) * SCALE))
            for ty in range(ty_lo, ty_hi):
                for tx_ in range(tx_lo, tx_hi):
                    s = ((tx_ + 0.5) / SCALE - ua) / (ub - ua)
                    t = ((ty + 0.5) / SCALE - va) / (vb - va)
                    p = tuple(A[i] + eu[i] * s + ev[i] * t for i in range(3))
                    tx = Texel()
                    tx.cube, tx.face = c, face
                    tx.a, tx.b, tx.fw, tx.fh = face_coords(face, c.box, p)
                    tx.W, tx.H = max(1, int(round(tx.fw * SCALE))), max(1, int(round(tx.fh * SCALE)))
                    tx.i = min(tx.W - 1, int(tx.a * SCALE))
                    tx.j = min(tx.H - 1, int(tx.b * SCALE))
                    tx.P = c.to_part(p)
                    tx.plate = face in plate
                    tx.side = face not in ("top", "bottom")
                    col = MATERIALS[c.mat](tx)
                    if col is not None:
                        tex[ty * w + tx_] = tuple(int(round(max(0, min(255, k)))) for k in col) + (255,)
    return tex


TEXTURE = bake()
png(TEXTURE_PATH, TEX_W * SCALE, TEX_H * SCALE, TEXTURE)


def tex_at(u, v):
    x = min(TEX_W * SCALE - 1, max(0, int(u * SCALE)))
    y = min(TEX_H * SCALE - 1, max(0, int(v * SCALE)))
    return TEXTURE[y * TEX_W * SCALE + x]


# ================================================================================================== z-fighting
def box_faces(label, box, origin):
    x0, x1, y0, y1, z0, z1 = box
    ox, oy, oz = origin
    x0, x1, y0, y1, z0, z1 = x0 + ox, x1 + ox, y0 + oy, y1 + oy, z0 + oz, z1 + oz
    return [Face(label + ".top", [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0)),
            Face(label + ".bottom", [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0)),
            Face(label + ".west", [(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)], (-1, 0, 0)),
            Face(label + ".east", [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], (1, 0, 0)),
            Face(label + ".north", [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1)),
            Face(label + ".south", [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1))]


def inflate(box, k):
    x0, x1, y0, y1, z0, z1 = box
    return (x0 - k, x1 + k, y0 - k, y1 + k, z0 - k, z1 + k)


HEAD, BODY, LEG = (-4, 4, -8, 0, -4, 4), (-4, 4, 0, 12, -2, 2), (-2, 2, 0, 12, -2, 2)
ARMS = {("right_arm", False): (-3, 1, -2, 10, -2, 2), ("left_arm", False): (-1, 3, -2, 10, -2, 2),
        ("right_arm", True): (-2, 1, -2, 10, -2, 2), ("left_arm", True): (-1, 2, -2, 10, -2, 2)}


def pivot(part, slim):
    x, y, z = PIVOTS[part]
    return (x, SLIM_ARM_Y if slim and part.endswith("_arm") else y, z)


def player_faces(slim):
    out = []
    for part, box, layer in (("head", HEAD, 0.5), ("body", BODY, 0.25), ("right_leg", LEG, 0.25), ("left_leg", LEG, 0.25),
                             ("right_arm", ARMS[("right_arm", slim)], 0.25), ("left_arm", ARMS[("left_arm", slim)], 0.25)):
        out += box_faces(f"player:{part}", box, pivot(part, slim))
        out += box_faces(f"player:{part}_layer", inflate(box, layer), pivot(part, slim))
    return out


def vanilla_faces():
    """Vanilla armor in each slot (helmet inflated 1 with its hat 1.5, chestplate 1, leggings 0.5, boots 1)."""
    out = []
    out += box_faces("vanilla:helmet:head", inflate(HEAD, 1.0), PIVOTS["head"])
    out += box_faces("vanilla:helmet:hat", inflate(HEAD, 1.5), PIVOTS["head"])
    out += box_faces("vanilla:chestplate:body", inflate(BODY, 1.0), PIVOTS["body"])
    for arm in ("right_arm", "left_arm"):
        out += box_faces(f"vanilla:chestplate:{arm}", inflate(ARMS[(arm, False)], 1.0), PIVOTS[arm])
    out += box_faces("vanilla:leggings:body", inflate(BODY, 0.5), PIVOTS["body"])
    for leg in ("right_leg", "left_leg"):
        out += box_faces(f"vanilla:leggings:{leg}", inflate(LEG, 0.5), PIVOTS[leg])
        out += box_faces(f"vanilla:boots:{leg}", inflate(LEG, 1.0), PIVOTS[leg])
    return out


def cube_faces(piece, c, slim):
    origin = pivot(c.part, slim)
    return [Face(f"scrap:{piece}:{uid(c)}.{face}", verts, n if not c.rz else
                 (math.cos(math.radians(c.rz)) * n[0] - math.sin(math.radians(c.rz)) * n[1],
                  math.sin(math.radians(c.rz)) * n[0] + math.cos(math.radians(c.rz)) * n[1], n[2]))
            for face, verts, _, n in cube_polys(c.box, (0, 0), origin, None, c.to_part)]


def check_zfighting():
    total = 0
    for slim in (False, True):
        faces = []
        for piece, parts in (("helmet", HELMET), ("chestplate", CHEST_SLIM if slim else CHEST), ("leggings", LEGGINGS),
                             ("boots", BOOTS)):
            for cubes in parts.values():
                for c in cubes:
                    faces += cube_faces(piece, c, slim)
        faces += player_faces(slim) + vanilla_faces()
        hits = []
        for a, b, area in fights(faces):
            ka, kb = a.split(":"), b.split(":")
            if ka[0] != "scrap" and kb[0] != "scrap":
                continue
            # One item per slot: our piece never meets vanilla armor in its own slot.
            if {ka[0], kb[0]} == {"scrap", "vanilla"} and ka[1] == kb[1]:
                continue
            hits.append((a, b, area))
        for h in hits:
            print("z-fight:", "slim" if slim else "wide", h)
        total += len(hits)
    if total:
        raise SystemExit(f"scrap armor: {total} coplanar overlapping face pairs")


check_zfighting()


# ================================================================================================== Java
def jf(v):
    return f"{v:.1f}f" if v == int(v) else f"{v:g}f"


PART_POSES = {"head": "PartPose.ZERO", "body": "PartPose.ZERO", "right_arm": "PartPose.offset(-5.0f, 2.0f, 0.0f)",
              "left_arm": "PartPose.offset(5.0f, 2.0f, 0.0f)", "right_leg": "PartPose.offset(-1.9f, 12.0f, 0.0f)",
              "left_leg": "PartPose.offset(1.9f, 12.0f, 0.0f)"}
VARS = {"head": "head", "body": "body", "right_arm": "rightArm", "left_arm": "leftArm", "right_leg": "rightLeg",
        "left_leg": "leftLeg"}


def java_cubes(cubes, indent):
    out = "CubeListBuilder.create()"
    for c in cubes:
        u, v = UV[uid(c)]
        x0, _, y0, _, z0, _ = c.box
        w, h, d = c.size()
        out += f"\n{indent}.texOffs({u}, {v}).addBox({jf(x0)}, {jf(y0)}, {jf(z0)}, {jf(w)}, {jf(h)}, {jf(d)})"
    return out


def java_layer(piece, method, parts):
    ind = "\t\t\t\t"
    lines = [f"\t/** The {piece.replace('_', ' ')}'s layer. */",
             f"\tpublic static LayerDefinition {method}() {{",
             "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot();"]
    for part in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
        cubes = parts.get(part, [])
        straight = [c for c in cubes if c.pivot is None]
        tilted = [c for c in cubes if c.pivot is not None]
        needs_var = part == "head" or tilted
        decl = f"PartDefinition {VARS[part]} = " if needs_var else ""
        lines.append(f"\t\t{decl}root.addOrReplaceChild(\"{part}\", {java_cubes(straight, ind)}, {PART_POSES[part]});")
        if part == "head":
            lines.append("\t\thead.addOrReplaceChild(\"hat\", CubeListBuilder.create(), PartPose.ZERO);")
        for c in tilted:
            px, py, pz = c.pivot
            lines.append(f"\t\t{VARS[part]}.addOrReplaceChild(\"{c.name}\", {java_cubes([c], ind)},\n{ind}PartPose.offsetAndRotation("
                         f"{jf(px)}, {jf(py)}, {jf(pz)}, 0.0f, 0.0f, {jf(round(math.radians(c.rz), 5))}));")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    lines.append("\t}")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t// BEGIN GENERATED by tools/gen_scrap_armor.py\n", "\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + "\n\n".join(java_layer(p, m, parts) for p, m, parts in LAYERS) + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)

for piece, method, parts in LAYERS:
    print(f"scrap armor {piece}: {sum(len(v) for v in parts.values())} cubes")
print(f"no z-fighting; texture {TEX_W * SCALE}x{TEX_H * SCALE}")


# ================================================================================================== preview
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def mat_mul_vec(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def load_skin():
    import gen_survival
    return gen_survival.vanilla("entity/player/wide/steve")


def figure_quads(pieces, skin=None, walk=0.0):
    """(corners, sampler (s, t) -> rgba, normal, cull) for the figure (if skin) and the given pieces' cubes."""
    out = []
    rot = {"right_arm": walk, "left_arm": -walk, "right_leg": -walk, "left_leg": walk}

    def pose(part):
        return pivot(part, False), rot_x(rot.get(part, 0.0))
    if skin is not None:
        def skin_sampler(ua, va, ub, vb):
            def f(s, t):
                return skin.get(min(63, int(ua + (ub - ua) * s)), min(63, int(va + (vb - va) * t)))
            return f
        for part, box, uv, layer_uv, k in (
                ("head", HEAD, (0, 0), (32, 0), 0.5), ("body", BODY, (16, 16), (16, 32), 0.25),
                ("right_arm", ARMS[("right_arm", False)], (40, 16), (40, 32), 0.25),
                ("left_arm", ARMS[("left_arm", False)], (32, 48), (48, 48), 0.25),
                ("right_leg", LEG, (0, 16), (0, 32), 0.25), ("left_leg", LEG, (16, 48), (0, 48), 0.25)):
            origin, m = pose(part)
            for b_, uv_, cull in ((box, uv, True), (inflate(box, k), layer_uv, False)):
                # The outer layer is unwrapped at the un-inflated size.
                size = (box[1] - box[0], box[3] - box[2], box[5] - box[4])
                for face, verts, (ua, va, ub, vb), n in cube_polys(b_, uv_, origin, m):
                    if b_ is not box:
                        w, h, d = size
                        u, v = uv_
                        rects = {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v + d, u + d + w + w, v),
                                 "west": (u, v + d, u + d, v + d + h), "north": (u + d, v + d, u + d + w, v + d + h),
                                 "east": (u + d + w, v + d, u + d + w + d, v + d + h),
                                 "south": (u + d + w + d, v + d, u + d + w + d + w, v + d + h)}
                        ua, va, ub, vb = rects[face]
                    out.append((verts, skin_sampler(ua, va, ub, vb), mat_mul_vec(m, n), cull))
    for piece in pieces:
        for part, cubes in PIECES[piece].items():
            origin, m = pose(part)
            for c in cubes:
                for face, verts, (ua, va, ub, vb), n in cube_polys(c.box, UV[uid(c)], origin, m, c.to_part):
                    def sample(s, t, ua=ua, va=va, ub=ub, vb=vb):
                        return tex_at(ua + (ub - ua) * s, va + (vb - va) * t)
                    if c.rz:
                        a_ = math.radians(c.rz)
                        n = (math.cos(a_) * n[0] - math.sin(a_) * n[1], math.sin(a_) * n[0] + math.cos(a_) * n[1], n[2])
                    out.append((verts, sample, mat_mul_vec(m, n), c.mat != "saw"))
    return out


def render(quads, yaw, pitch, scale, width, height, centre=(0.0, 12.0, 0.0), bg=(44, 50, 60)):
    """Orthographic, from yaw degrees round (0: facing the figure's front, 90: from its right) and pitch degrees
    above, with Minecraft's face shading (top 1.0, bottom 0.5, front and back 0.8, sides 0.6)."""
    ya, pa = math.radians(yaw), math.radians(pitch)
    cy_, sy_, cp, sp = math.cos(ya), math.sin(ya), math.cos(pa), math.sin(pa)

    def view(p):
        # Model space: x to the wearer's left... seen from the front, -x is on the viewer's left.
        x, y, z = p[0] - centre[0], p[1] - centre[1], p[2] - centre[2]
        vx = x * cy_ - z * sy_
        vz = x * sy_ + z * cy_
        vy = y * cp - vz * sp
        vz2 = vz * cp + y * sp
        return vx, vy, vz2
    img = [bg + (255,)] * (width * height)
    zbuf = [1e9] * (width * height)
    for verts, sample, n, cull in quads:
        nv = view((n[0] + centre[0], n[1] + centre[1], n[2] + centre[2]))
        if cull and nv[2] > 1e-6:
            continue
        lit = 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] < 0 else 0.5) * n[1] ** 2
        P = []
        for p in verts:
            vx, vy, vz = view(p)
            P.append((width / 2 + vx * scale, height / 2 + vy * scale, vz))
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
                if depth < zbuf[k] - 1e-4:
                    col = sample(min(s, 0.9999), min(t, 0.9999))
                    if len(col) == 4 and col[3] == 0:
                        continue
                    zbuf[k] = depth
                    img[k] = tuple(min(255, int(col[i] * lit)) for i in range(3)) + (255,)
    return img


if PREVIEW:
    import gen_fishing_rods as R
    import gen_survival
    skin = load_skin()
    everything = ["helmet", "chestplate", "leggings", "boots"]
    full, walking = figure_quads(everything, skin), figure_quads(everything, skin, walk=0.5)
    W, H, S, FIG = 300, 420, 8.5, (0.0, 6.0, 0.0)
    panels = [(render(full, 0, 6, S, W, H, centre=FIG), "FRONT"), (render(full, 90, 6, S, W, H, centre=FIG), "SIDE (RIGHT)"),
              (render(full, 35, 12, S, W, H, centre=FIG), "THREE QUARTERS"), (render(full, 180, 6, S, W, H, centre=FIG), "BACK"),
              (render(full, -35, 12, S, W, H, centre=FIG), "3/4 LEFT"), (render(walking, 30, 10, S, W, H, centre=FIG), "WALKING"),
              (render(full, 145, 12, S, W, H, centre=FIG), "3/4 BACK"),
              (render(full, 20, 8, 26, W, H, centre=(0.0, -3.0, 0.0)), "HELMET CLOSE")]
    centres = {"helmet": (0.0, -5.0, 0.0), "chestplate": (0.0, 5.0, 0.0), "leggings": (0.0, 15.0, 0.0), "boots": (0.0, 21.0, 0.0)}
    scales = {"helmet": 22, "chestplate": 17, "leggings": 18, "boots": 22}
    for piece in everything:
        q = figure_quads([piece])
        panels.append((render(q, 35, 14, scales[piece], W, H, centre=centres[piece]), piece.upper() + " ALONE"))
        panels.append((render(q, 215, 14, scales[piece], W, H, centre=centres[piece]), piece.upper() + " BACK"))
    # The item sprites and the texture.
    sprites = gen_survival.scrap_armor_sprites()
    cols = 4
    rows = (len(panels) + 1 + cols - 1) // cols
    sheet = R.Img(W * cols, H * rows + 300, (30, 33, 40, 255))
    for k, (img, title) in enumerate(panels):
        bx, by = (k % cols) * W, (k // cols) * H
        for y in range(H):
            for x in range(W):
                sheet.px[(by + y) * sheet.w + bx + x] = img[y * W + x]
        R.text(sheet, bx + 6, by + 6, title, "#e9eff5", 2)
    k = len(panels)
    bx, by = (k % cols) * W, (k // cols) * H
    sheet.fill(bx, by, bx + W, by + H, (139, 139, 139, 255))
    for n, piece in enumerate(everything):
        img = sprites[piece]
        sheet.paste(img, bx + 10 + n * 18, by + 10, 1)
        sheet.paste(img, bx + 10 + (n % 2) * 140, by + 40 + (n // 2) * 180, 8)
    R.text(sheet, bx + 6, by + H - 14, "SPRITES 1X AND 8X", "#e9eff5", 1)
    ty = H * rows
    tex = R.Img(TEX_W * SCALE, TEX_H * SCALE)
    tex.px = list(TEXTURE)
    sheet.paste(tex, 6, ty + 6, 1)
    R.text(sheet, TEX_W * SCALE + 16, ty + 10, "TEXTURE 1X", "#e9eff5", 2)
    png(PREVIEW, sheet.w, sheet.h, sheet.px)
    print("preview written to", PREVIEW)
