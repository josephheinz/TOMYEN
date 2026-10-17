"""Paints the Bathtub Boat and the Bathtub, and writes everything around them.

The boat is a code-defined entity model (BathtubBoatModel) and the Bathtub is a two-block decorative block (a bed-like head
and foot); both, and the 3D item models of the Bathtub and the Bathtub Boat, are cut from one 128x128 texture
(textures/block/bathtub.png, in the block atlas so the JSON models can use it; the entity renderer binds it directly).
This script is where their geometry lives: SPEC lists every part and box (pivots and sizes in model pixels, y down,
front at -z, feet at y 24), UVs are packed from it, `--java-into` rewrites BathtubBoatModel's createBodyLayer body (`--java` prints it), and the
block and item models are converted from the same boxes, so the texture and the models can't drift apart.

The look: a white enamel clawfoot tub (34 px long, a flared basin under stepped, rounded walls and a rolled rim) on four
brass ball-and-claw feet, a chrome faucet and hot and cold taps at the front end, an overflow, a drain plug on its chain,
and a rubber duck on the rim. At the back an outboard motor is clamped over the rim: a charcoal cowling with a red and
orange brand stripe, a pull-cord, a tiller reaching forward to the driver's right hand, the leg, an anti-cavitation
plate, the gearcase and a three-bladed propeller. A kerosene can sits behind the driver with its fuel line over the rim.
Two oars come out only when it's paddled with an empty tank. Materials are flat ramps with a crisp edge light: glossy
enamel (a highlight row and a reflection on the long sides), brass, chrome (light and dark bands), rubber, painted
metal. No noise.

Every run checks for z-fighting (tools/coplanar.py) in the rest pose, with the oars out, and with the motor tilted up
and the feet mid-step; and the block and item models with tools/zfight.py and the world-space check of gunsmith_kit.

Also: the outboard motor and propeller icons, the item model definitions, the bathtub's blockstate and loot table,
the recipes with their recipe-book advancements, and the rubber duck's squeak (synthesised, written mono by
vorbis_mono.py).

`--preview DIR` renders the boat from several angles (with stand-in riders), on land, and the item models (side,
three-quarter, first person and inventory icon) to PNGs in DIR.

Usage: python3 tools/gen_bathtub_boat.py src/main/resources/assets/tomyen [--java | --java-into MODEL.java] [--check] [--no-sound]
       [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from coplanar import Face, fights  # noqa: E402

ASSETS = sys.argv[1]  # .../assets/tomyen
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
TEX = os.path.join(ASSETS, "textures")
CLEAR = (0, 0, 0, 0)
TEX_W = TEX_H = 128
# JSON model UVs are in sixteenths of the texture.
UV_SCALE = 16.0 / TEX_W


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def rgb(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def save(self, path):
        png(path, self.w, self.h, self.px)


# --- Materials ----------------------------------------------------------------------------------------------------
# Each: the side ramp from the top row down (the first entry is the lit edge), the top and the underside.
def ramp(*hexes):
    return [rgb(h) for h in hexes]


MATERIALS = {
    # Glossy white enamel outside, a cooler shadowed white inside.
    "enamel": {"sides": ramp(0xFFFFFF, 0xF4F7FA, 0xF4F7FA, 0xF0F4F7, 0xE6ECF1, 0xD2DAE2), "top": rgb(0xFFFFFF),
               "bottom": rgb(0xAEB9C4)},
    "enamel_in": {"sides": ramp(0xF6F8FA, 0xE9EEF2, 0xE9EEF2, 0xE3E9EE, 0xDAE1E7, 0xCDD5DD), "top": rgb(0xE6ECF1),
                  "bottom": rgb(0xAEB9C4)},
    "basin": {"sides": ramp(0xE9EEF3, 0xDCE3EA, 0xD0D8E0, 0xC3CCD5, 0xB5C0CA), "top": rgb(0xE2E8EE), "bottom": rgb(0xA3AEB9)},
    "rim": {"sides": ramp(0xFFFFFF, 0xF1F5F8, 0xD9E0E7), "top": rgb(0xFFFFFF), "bottom": rgb(0xC7D0D9)},
    "brass": {"sides": ramp(0xF8E296, 0xE2BC55, 0xC99B3C, 0xA87D2B, 0x82601E), "top": rgb(0xF0CF6E), "bottom": rgb(0x6E5018)},
    "chrome": {"sides": ramp(0xFFFFFF, 0xDDE4EA, 0x98A4B0, 0x6E7985, 0xC6CFD8), "top": rgb(0xF4F7FA), "bottom": rgb(0x77828E)},
    "rubber": {"sides": ramp(0x4A4A52, 0x34343B, 0x2A2A30, 0x212126), "top": rgb(0x3C3C44), "bottom": rgb(0x18181C)},
    "duck": {"sides": ramp(0xFFF1A0, 0xFFDE59, 0xFACB38, 0xF0B526, 0xDD9C18), "top": rgb(0xFFE36E), "bottom": rgb(0xC98A12)},
    "beak": {"sides": ramp(0xFFC07A, 0xFF9B33, 0xF0801F, 0xD96A14), "top": rgb(0xFFA544), "bottom": rgb(0xB85810)},
    "cowl": {"sides": ramp(0x5B626C, 0x3B4048, 0x33373E, 0x2C3036, 0x25282D, 0x1E2125), "top": rgb(0x454B54),
             "bottom": rgb(0x17191C)},
    "metal": {"sides": ramp(0x8A929C, 0x5E656F, 0x535A63, 0x484E56, 0x3D424A), "top": rgb(0x6A727C), "bottom": rgb(0x2F3339)},
    "prop": {"sides": ramp(0xF0F4F7, 0xC4CCD4, 0xAAB3BC, 0x8E98A3, 0x737D88), "top": rgb(0xD6DDE3), "bottom": rgb(0x66707B)},
    "can": {"sides": ramp(0xB9C0C8, 0x8E96A0, 0x828A94, 0x747C86, 0x666E78), "top": rgb(0x9AA2AB), "bottom": rgb(0x555C66)},
    "can_cap": {"sides": ramp(0x4A5058, 0x2E333A, 0x23272E), "top": rgb(0x3A3F47), "bottom": rgb(0x1B1E23)},
    "hose": {"sides": ramp(0x50535A, 0x34363C, 0x2A2C31), "top": rgb(0x44464C), "bottom": rgb(0x1E1F23)},
    "wood": {"sides": ramp(0xD8B27A, 0xC29A63, 0xB38A55, 0xA27B49, 0x8E6B3E), "top": rgb(0xCDA56C), "bottom": rgb(0x7A5A33)},
    "soap": {"sides": ramp(0xFFE4EC, 0xF9C9D6, 0xF0B2C3, 0xE49EB2), "top": rgb(0xFCD6E0), "bottom": rgb(0xC98A9E)},
    "hot": {"sides": ramp(0xFF8A80, 0xE0473C, 0xC1352C), "top": rgb(0xF0584C), "bottom": rgb(0x9A2820)},
    "cold": {"sides": ramp(0x9CC6FF, 0x3E86E8, 0x2F6CC4), "top": rgb(0x5296F0), "bottom": rgb(0x24559C)},
}
BORE = rgb(0x16181B)
STRIPE_RED = rgb(0xE5432C)
STRIPE_ORANGE = rgb(0xF59B2C)
STRIPE_WHITE = rgb(0xF6F2E8)
AMBER = rgb(0xE0A93A)
AMBER_DARK = rgb(0xB8832A)

# --- The spec -------------------------------------------------------------------------------------------------------
# A part: name, parent, pivot (absolute, model space), rest rotation (radians) and boxes in the part's own frame. A box:
# min corner, size, material, per-face material overrides and decals.


def part(name, parent, pivot, boxes, rot=(0.0, 0.0, 0.0)):
    return {"name": name, "parent": parent, "pivot": pivot, "rot": rot, "boxes": boxes}


def box(x, y, z, w, h, d, mat, faces=None, decals=()):
    return {"at": (x, y, z), "size": (w, h, d), "mat": mat, "faces": dict(faces or {}), "decals": tuple(decals)}


# Everything above the feet stands this much higher than the heights below are written (the legs' length).
LIFT = 1.0


def hb(x0, x1, h0, h1, z0, z1, mat, faces=None, decals=(), pivot=(0.0, 0.0, 0.0)):
    """A box by its extents, with heights above the ground (h, before LIFT) instead of y, made relative to a part's
    pivot."""
    px, py, pz = pivot
    return box(x0 - px, 24.0 - h1 - LIFT - py, z0 - pz, x1 - x0, h1 - h0, z1 - z0, mat, faces, decals)


# Plan outlines of the tub, each a list of runs out from the middle: (outer half-width, inner half-width, z0, z1) for the
# side walls and the corner steps, then the end piece (half-width, z0, z1) spanning the full width. Mirrored to both
# sides and both ends.
WALL_RUNS = [(7.5, 6.5, 0.0, 12.5), (6.5, 5.5, 12.5, 14.0), (5.5, 4.0, 14.0, 15.25)]
WALL_END = (4.0, 15.25, 16.5)
RIM_RUNS = [(8.0, 6.5, 0.0, 12.5), (7.0, 5.5, 12.5, 14.0), (6.0, 4.0, 14.0, 15.25)]
RIM_END = (4.5, 15.25, 17.0)
BASIN_RUNS = [(6.5, 12.5), (5.5, 14.0), (4.0, 15.25)]
BASIN = (3.5, 7.0)
WALLS = (7.0, 12.5)
RIM = (12.5, 14.0)
FLOOR = 7.0


def ring(runs, end, h0, h1, mat, inner_mat):
    """A hollow ring of wall: straight sides, stepped round corners and end pieces, each face outside painted `mat` and
    inside `inner_mat`. The side runs span both ends (z0 0 means the middle)."""
    out = []
    for i, (hw_out, hw_in, z0, z1) in enumerate(runs):
        for sx in (1, -1):
            outer = "xpos" if sx > 0 else "xneg"
            inner = "xneg" if sx > 0 else "xpos"
            x0, x1 = (hw_in, hw_out) if sx > 0 else (-hw_out, -hw_in)
            if i == 0:
                out.append(hb(x0, x1, h0, h1, -z1, z1, mat, {inner: inner_mat}))
                continue
            for sz in (1, -1):
                # A corner step: its end facing away from the middle shows outside, the one facing it inside.
                facing_in = "front" if sz > 0 else "back"
                za, zb = (z0, z1) if sz > 0 else (-z1, -z0)
                out.append(hb(x0, x1, h0, h1, za, zb, mat, {inner: inner_mat, facing_in: inner_mat}))
    hw, z0, z1 = end
    out.append(hb(-hw, hw, h0, h1, z0, z1, mat, {"front": inner_mat}))
    out.append(hb(-hw, hw, h0, h1, -z1, -z0, mat, {"back": inner_mat}))
    return out


def basin():
    h0, h1 = BASIN
    out = []
    prev = 0.0
    for i, (hw, z1) in enumerate(BASIN_RUNS):
        if i == 0:
            out.append(hb(-hw, hw, h0, h1, -z1, z1, "basin", {"top": "enamel_in"}, decals=("drain",)))
        else:
            out.append(hb(-hw, hw, h0, h1, prev, z1, "basin", {"top": "enamel_in"}))
            out.append(hb(-hw, hw, h0, h1, -z1, -prev, "basin", {"top": "enamel_in"}))
        prev = z1
    return out


def faucet():
    """The chrome deck plate on the front rim, the spout arching back over the tub, and the hot and cold taps."""
    out = [
        hb(-3.25, 3.25, 14.0, 14.5, -16.75, -15.25, "chrome"),       # deck plate on the rim's end
        hb(-0.5, 0.5, 14.5, 17.5, -16.5, -15.5, "chrome"),           # riser
        hb(-0.5, 0.5, 16.5, 17.5, -15.5, -13.25, "chrome"),          # spout arm
        hb(-0.5, 0.5, 15.5, 16.5, -14.0, -13.25, "chrome", decals=("bore_down",)),  # spout tip
    ]
    for side, cap in ((1, "hot"), (-1, "cold")):
        cx = 2.25 * side
        out += [
            hb(cx - 0.375, cx + 0.375, 14.5, 15.75, -16.375, -15.625, "chrome"),       # stem
            hb(cx - 1.0, cx + 1.0, 15.75, 16.25, -16.25, -15.75, "chrome"),            # handle, across
            hb(cx - 0.25, cx + 0.25, 15.7, 16.35, -17.0, -15.0, "chrome"),             # handle, along
            hb(cx - 0.3, cx + 0.3, 16.35, 16.6, -16.3, -15.7, cap),                    # hot or cold button
        ]
    return out


def drain():
    """The overflow on the inside of the front end, and the plug on its chain lying on the floor beside the drain."""
    return [
        hb(-0.75, 0.75, 10.0, 11.5, -15.25, -15.0, "chrome"),
        hb(-0.15, 0.15, 7.3, 10.0, -15.0, -14.7, "chrome"),
        hb(-0.15, 0.15, 7.0, 7.3, -15.0, -13.4, "chrome"),
        hb(-0.75, 0.75, 7.0, 7.6, -13.4, -12.2, "rubber"),
    ]


def can():
    """A kerosene can behind the driver, its fuel line up the end wall and over the rim to the motor bracket."""
    return [
        hb(0.25, 3.75, 7.0, 10.75, 10.75, 13.25, "can", decals=("can_band",)),
        hb(1.25, 2.75, 10.75, 11.5, 11.5, 12.5, "can_cap"),            # handle
        hb(0.5, 1.0, 10.75, 11.25, 11.0, 11.5, "can_cap"),             # cap
        hb(2.25, 2.55, 9.5, 9.8, 13.25, 15.25, "hose"),                # fuel line to the end wall...
        hb(2.25, 2.55, 9.8, 14.3, 14.95, 15.25, "hose"),               # ...up it...
        hb(2.25, 2.55, 14.0, 14.3, 15.25, 16.75, "hose"),              # ...and over the rim
    ]


CAN = can()

TUB = basin() + ring(WALL_RUNS, WALL_END, WALLS[0], WALLS[1], "enamel", "enamel_in") \
    + ring(RIM_RUNS, RIM_END, RIM[0], RIM[1], "rim", "rim") + faucet() + drain()

# Claw feet: a flared shoulder up under the basin, the leg, the ball and two talons gripping it. Pivots at the top.
FOOT_X, FOOT_Z = 4.75, 10.5
FOOT_PIVOT_H = BASIN[0] + LIFT


def foot(name, sx, sz):
    pivot = (FOOT_X * sx, 24.0 - FOOT_PIVOT_H, FOOT_Z * sz)
    boxes = [
        box(-1.5, -0.25, -1.5, 3.0, 1.25, 3.0, "brass"),               # knee flare (its top tucked into the basin)
        box(-0.875, 1.0, -0.875, 1.75, 1.75, 1.75, "brass"),           # leg
        box(-1.125, 2.75, -1.125, 2.25, 0.5, 2.25, "brass"),           # ankle cuff
        box(-1.25, 3.25, -1.25, 2.5, 1.25, 2.5, "brass"),              # ball
        box(-0.5, 2.5, -1.75, 1.0, 2.0, 0.5, "brass"),                 # front talon
        box(1.25 if sx > 0 else -1.75, 2.5, -0.5, 0.5, 2.0, 1.0, "brass"),   # outer talon
    ]
    # Splayed out from the tub: the foot's bottom swings away from the middle.
    return part(name, None, pivot, boxes, rot=(-0.2 * sz, 0.0, -0.24 * sx))


FEET = [foot("front_left_foot", 1, -1), foot("front_right_foot", -1, -1), foot("back_left_foot", 1, 1),
        foot("back_right_foot", -1, 1)]

# The rubber duck sits on the left rim near the front, facing forward. Pivot at the middle of its bottom.
DUCK_PIVOT = (7.25, 24.0 - RIM[1] - LIFT, -10.5)


def duck_boxes():
    p = DUCK_PIVOT
    x, z = p[0], p[2]
    return [
        hb(x - 1.25, x + 1.25, 14.0, 15.75, z - 1.5, z + 1.75, "duck", pivot=p),                          # body
        hb(x - 0.875, x + 0.875, 15.75, 17.5, z - 1.75, z, "duck", decals=("eyes",), pivot=p),             # head
        hb(x - 0.5, x + 0.5, 16.0, 16.5, z - 2.5, z - 1.75, "beak", pivot=p),                              # beak
        hb(x - 0.625, x + 0.625, 15.5, 16.5, z + 1.75, z + 2.25, "duck", pivot=p),                         # tail
        hb(x + 1.25, x + 1.5, 14.5, 15.5, z - 0.5, z + 1.25, "duck", pivot=p),                             # left wing
        hb(x - 1.5, x - 1.25, 14.5, 15.5, z - 0.5, z + 1.25, "duck", pivot=p),                             # right wing
    ]


# The outboard motor. The transom bracket is clamped over the back rim (static); the motor tilts up on the bracket's
# hinge (motor_tilt) and swivels on its own post (motor), which carries the cowling, the tiller, the leg and the propeller.
BRACKET = [
    hb(-1.75, 1.75, 9.0, 14.75, 17.0, 17.75, "metal"),                 # back plate, outside the rim
    hb(-1.75, 1.75, 14.0, 14.75, 15.5, 17.0, "metal"),                 # hook over the rim
    hb(-1.5, -0.5, 12.75, 13.25, 14.75, 15.25, "chrome"),              # clamp screw handles, inside
    hb(0.5, 1.5, 12.75, 13.25, 14.75, 15.25, "chrome"),
]
TILT_PIVOT = (0.0, 24.0 - 14.5 - LIFT, 17.75)
SWIVEL_PIVOT = (0.0, 24.0 - 14.5 - LIFT, 18.25)


def motor_boxes():
    p = SWIVEL_PIVOT
    return [
        hb(-2.5, 2.5, 15.0, 19.5, 17.75, 24.25, "cowl", decals=("stripe", "seam"), pivot=p),    # lower cowling
        hb(-2.25, 2.25, 19.5, 20.5, 18.0, 24.0, "cowl", decals=("cap",), pivot=p),             # top cap
        hb(-2.0, 2.0, 15.5, 19.0, 24.25, 24.75, "cowl", decals=("badge",), pivot=p),           # rounded back
        hb(-0.75, 0.75, 17.5, 18.0, 17.25, 17.75, "rubber", pivot=p),                         # pull-cord handle
        hb(-1.5, 1.5, 10.0, 15.0, 18.5, 21.5, "metal", pivot=p),                              # exhaust housing
        hb(-1.0, 1.0, 3.5, 10.0, 19.0, 21.0, "metal", pivot=p),                               # leg
        hb(-2.0, 2.0, 3.0, 3.5, 18.0, 22.5, "metal", pivot=p),                                # anti-cavitation plate
        hb(-1.25, 1.25, 0.5, 3.0, 17.75, 23.0, "metal", decals=("intake",), pivot=p),         # gearcase
        hb(-0.75, 0.75, 1.0, 2.5, 17.0, 17.75, "metal", pivot=p),                             # gearcase nose
        hb(-0.375, 0.375, -1.5, 0.5, 20.0, 22.5, "metal", pivot=p),                           # skeg
    ]


# The tiller: from the cowling's front right corner forward and across to the hand of the driver up on the caddy, past
# their back, tipped up a touch.
TILLER_PIVOT = (-2.0, 24.0 - 16.75 - LIFT, 17.75)
TILLER_YAW = 0.5
TILLER_DROOP = -0.08
TILLER = [
    box(-0.5, -0.5, -5.0, 1.0, 1.0, 5.0, "metal"),                     # arm
    box(-0.85, -0.85, -5.5, 1.7, 1.7, 0.5, "chrome"),                  # throttle collar
    box(-0.75, -0.75, -8.25, 1.5, 1.5, 2.75, "rubber"),                # twist grip
]
PROP_PIVOT = (0.0, 24.0 - 1.75 - LIFT, 23.0)
PROP_PITCH = 0.4


def blade(k):
    return part("blade_%d" % k, "propeller", PROP_PIVOT, [box(-0.75, -3.5, 0.25, 1.5, 2.75, 0.5, "prop")],
                rot=(0.0, PROP_PITCH, k * 2.0 * math.pi / 3.0))


# Oars, out only while it's paddled with an empty tank. Pivots in the oarlocks on the rim beside the driver.
OAR_PIVOT_H = RIM[1] + 1.0 + LIFT
# Just ahead of the caddy, so the handles' sweep stays clear of it.
OAR_Z = 6.0


def oar(name, sx):
    pivot = (7.75 * sx, 24.0 - OAR_PIVOT_H, OAR_Z)
    if sx > 0:
        boxes = [box(-3.0, -0.375, -0.375, 11.0, 0.75, 0.75, "wood"), box(7.5, -1.5, -0.25, 3.5, 3.0, 0.5, "wood")]
    else:
        boxes = [box(-8.0, -0.375, -0.375, 11.0, 0.75, 0.75, "wood"), box(-11.0, -1.5, -0.25, 3.5, 3.0, 0.5, "wood")]
    return part(name, None, pivot, boxes, rot=(0.0, 0.0, 0.32 * sx))


# The bath caddy: a slatted board across the rim at the stern, the driver's seat (up where they can see over whoever's
# in the tub in front), with a bar of soap on it.
CADDY_Z = (8.0, 14.0)
CADDY = [hb(-8.5, 8.5, RIM[1], RIM[1] + 1.0, z0, z1, "wood") for z0, z1 in ((8.0, 10.75), (11.25, 14.0))] + [
    hb(5.0, 7.0, RIM[1] + 1.0, RIM[1] + 1.75, 8.5, 10.0, "soap"),
]


SPEC = [
    part("tub", None, (0.0, 0.0, 0.0), TUB + BRACKET + CAN + CADDY),
    *FEET,
    part("duck", None, DUCK_PIVOT, duck_boxes()),
    part("motor_tilt", None, TILT_PIVOT, []),
    part("motor", "motor_tilt", SWIVEL_PIVOT, motor_boxes()),
    part("tiller", "motor", TILLER_PIVOT, TILLER, rot=(TILLER_DROOP, TILLER_YAW, 0.0)),
    part("propeller", "motor", PROP_PIVOT, [box(-0.75, -0.75, -0.25, 1.5, 1.5, 1.25, "prop"),
                                            box(-0.4, -0.4, 1.0, 0.8, 0.8, 0.5, "chrome")]),
    blade(0), blade(1), blade(2),
    oar("left_oar", 1), oar("right_oar", -1),
]
OARS = {"left_oar", "right_oar"}
BLOCK_PARTS = {"tub", "front_left_foot", "front_right_foot", "back_left_foot", "back_right_foot", "duck"}
# The block keeps the tub, feet and duck; the motor bracket and the can are the boat's.
BOAT_ONLY_BOXES = set(id(b) for b in BRACKET + CAN + CADDY)


# --- UV packing ------------------------------------------------------------------------------------------------------
def uv_size(b):
    w, h, d = (int(math.ceil(v - 1e-6)) for v in b["size"])
    return 2 * (w + d), d + h


def share_key(b):
    return (b["size"], b["mat"], tuple(sorted(b["faces"].items())), b["decals"])


def pack():
    """Skyline packing: each box's region goes wherever along the top edge leaves it highest up. Boxes of the same size,
    materials and decals (the feet, the taps, the blades, mirrored steps) share a region."""
    boxes = [b for p in SPEC for b in p["boxes"]]
    shared = {}
    for b in boxes:
        shared.setdefault(share_key(b), []).append(b)
    order = sorted(shared.values(), key=lambda group: (-uv_size(group[0])[1], -uv_size(group[0])[0]))
    sky = [0] * TEX_W
    for group in order:
        bw, bh = uv_size(group[0])
        best = None
        for x in range(0, TEX_W - bw + 1):
            y = max(sky[x:x + bw])
            if best is None or y < best[1]:
                best = (x, y)
        x, y = best
        assert y + bh <= TEX_H, "texture overflow placing %s" % (group[0],)
        for b in group:
            b["uv"] = (x, y)
        for i in range(x, x + bw):
            sky[i] = y + bh
    return max(sky)


def faces(b, exact=False):
    """Face rectangles of a box's texture (x0, y0, x1, y1), named as seen on the boat: top, bottom, the -x side (the
    boat's right), front (-z), the +x side and back. Painting uses whole texels; models use the exact sizes."""
    u, v = b["uv"]
    if exact:
        w, h, d = b["size"]
    else:
        w, h, d = (int(math.ceil(s - 1e-6)) for s in b["size"])
    return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "xneg": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
            "xpos": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


# --- Painting --------------------------------------------------------------------------------------------------------
def paint_face(c, rect, mat, face):
    m = MATERIALS[mat]
    x0, y0, x1, y1 = rect
    if face in ("top", "bottom"):
        base = m[face]
        for y in range(y0, y1):
            for x in range(x0, x1):
                col = base
                if face == "top" and (x in (x0, x1 - 1) or y in (y0, y1 - 1)) and x1 - x0 > 2 and y1 - y0 > 2:
                    col = shade(base, 0.96)
                c.set(x, y, col)
        return
    rows = m["sides"]
    h = y1 - y0
    for y in range(y0, y1):
        t = y - y0
        if h == 1:
            col = rows[1]
        elif t == 0:
            col = rows[0]
        else:
            # The rest of the ramp spread over the remaining rows.
            k = 1 + (t - 1) * (len(rows) - 2) / max(1, h - 2) if h > 2 else len(rows) - 1
            lo = int(math.floor(k))
            hi = min(len(rows) - 1, lo + 1)
            col = mix(rows[lo], rows[hi], k - lo)
        for x in range(x0, x1):
            c.set(x, y, col)


def decal(c, b, name):
    f = faces(b)
    if name == "drain":
        # A chrome-ringed drain hole in the floor near the front, beside where the plug lies. On the top face u runs
        # with x (from x0) and v runs toward -z (from z1).
        x0, _, z0 = b["at"]
        u0, v0, _, _ = f["top"]
        z1 = z0 + b["size"][2]
        for dz in range(2):
            for dx in range(2):
                z = -11.0 - dz
                x = -1.0 + dx
                c.set(u0 + int(x - x0), v0 + int(z1 - z), BORE)
        for (x, z) in ((-2, -10), (-1, -10), (0, -10), (1, -10), (-2, -13), (-1, -13), (0, -13), (1, -13),
                       (-2, -11), (-2, -12), (1, -11), (1, -12)):
            c.set(u0 + int(x - x0), v0 + int(z1 - z), MATERIALS["chrome"]["sides"][1])
    elif name == "bore_down":
        x0, y0, x1, y1 = f["bottom"]
        for x in range(x0, x1):
            for y in range(y0, y1):
                c.set(x, y, BORE)
    elif name == "eyes":
        # A black eye with a white glint on each side of the head.
        for side in ("xneg", "xpos"):
            x0, y0, x1, y1 = f[side]
            # On xneg u runs toward -z (front is the high-u end's opposite): the eye sits toward the beak.
            ex = x1 - 1 if side == "xneg" else x0
            c.set(ex, y0 + 1, BORE)
    elif name == "can_band":
        for side in ("xneg", "xpos", "front", "back"):
            x0, y0, x1, y1 = f[side]
            for x in range(x0, x1):
                c.set(x, y0 + 1, AMBER)
                c.set(x, y0 + 2, AMBER_DARK)
    elif name == "stripe":
        # The brand stripe along both sides of the cowling, a red band over an orange one with a white pinstripe between,
        # sweeping up at the back. Sides: on xpos u runs toward +z, on xneg toward -z.
        for side in ("xneg", "xpos"):
            x0, y0, x1, y1 = f[side]
            n = x1 - x0
            for i in range(n):
                along = i if side == "xpos" else n - 1 - i   # 0 at the front of the cowl
                x = x0 + i
                lift = 1 if along >= n - 2 else 0
                c.set(x, y0 + 2 - lift, STRIPE_RED)
                c.set(x, y0 + 3 - lift, STRIPE_WHITE if along % 7 != 6 else STRIPE_RED)
                c.set(x, y0 + 4 - lift, STRIPE_ORANGE)
        x0, y0, x1, y1 = f["front"]
        for x in range(x0, x1):
            c.set(x, y0 + 2, STRIPE_RED)
            c.set(x, y0 + 4, STRIPE_ORANGE)
    elif name == "seam":
        # The split line between the upper and lower cowling.
        for side in ("xneg", "xpos", "front"):
            x0, y0, x1, y1 = f[side]
            for x in range(x0, x1):
                c.set(x, y1 - 2, shade(MATERIALS["cowl"]["sides"][-1], 0.8))
    elif name == "cap":
        x0, y0, x1, y1 = f["top"]
        for x in range(x0 + 1, x1 - 1):
            c.set(x, y0 + 1, rgb(0x5A616B))
    elif name == "badge":
        x0, y0, x1, y1 = f["back"]
        cx = (x0 + x1) // 2
        for x in range(x0, x1):
            c.set(x, y0 + 1, STRIPE_RED)
            c.set(x, y0 + 2, STRIPE_ORANGE)
        for y in range(y0 + 1, y0 + 3):
            c.set(cx - 1, y, STRIPE_WHITE)
    elif name == "intake":
        for side in ("xneg", "xpos"):
            x0, y0, x1, y1 = f[side]
            for i, x in enumerate(range(x0 + 1, x0 + 4)):
                c.set(x, y0 + 1, BORE)


def paint():
    c = Canvas(TEX_W, TEX_H)
    for p in SPEC:
        for b in p["boxes"]:
            for name, rect in faces(b).items():
                paint_face(c, rect, b["faces"].get(name, b["mat"]), name)
            for d in b["decals"]:
                decal(c, b, d)
    # Gloss on the long outside walls: a reflection a third of the way along, just under the lit top row.
    for p in SPEC:
        for b in p["boxes"]:
            if b["mat"] == "enamel" and b["size"][2] > 20:
                for side in ("xneg", "xpos"):
                    if b["faces"].get(side) == "enamel_in":
                        continue
                    x0, y0, x1, y1 = faces(b)[side]
                    n = x1 - x0
                    for x in list(range(x0 + n // 4, x0 + n // 4 + 5)) + list(range(x0 + n // 4 + 7, x0 + n // 4 + 9)):
                        c.set(x, y0 + 1, rgb(0xFFFFFF))
                        c.set(x, y0 + 2, rgb(0xFBFCFD))
    return c


# --- Java ----------------------------------------------------------------------------------------------------------------
def jf(v):
    s = ("%.4f" % v).rstrip("0")
    return (s + "0" if s.endswith(".") else s) + "f"


def camel(name):
    head, *rest = name.split("_")
    return head + "".join(s.capitalize() for s in rest)


def by_name():
    return {p["name"]: p for p in SPEC}


def java():
    parts = by_name()
    parents = {p["parent"] for p in SPEC}
    lines = []
    for p in SPEC:
        parent = "root" if p["parent"] is None else camel(p["parent"])
        cubes = "CubeListBuilder.create()"
        for b in p["boxes"]:
            (x, y, z), (w, h, d) = b["at"], b["size"]
            cubes += "\n\t\t\t\t.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)" % (b["uv"] + tuple(jf(v) for v in (x, y, z, w, h, d)))
        ppx, ppy, ppz = p["pivot"]
        if p["parent"] is not None:
            qx, qy, qz = parts[p["parent"]]["pivot"]
            ppx, ppy, ppz = ppx - qx, ppy - qy, ppz - qz
        rx, ry, rz = p["rot"]
        pose = ("PartPose.offset(%s, %s, %s)" % (jf(ppx), jf(ppy), jf(ppz)) if (rx, ry, rz) == (0, 0, 0)
                else "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(jf(v) for v in (ppx, ppy, ppz, rx, ry, rz)))
        declare = "PartDefinition %s = " % camel(p["name"]) if p["name"] in parents else ""
        lines.append("\t\t%s%s.addOrReplaceChild(\"%s\", %s,\n\t\t\t\t%s);" % (declare, parent, p["name"], cubes, pose))
    return "\n".join(lines)


# --- Posing ----------------------------------------------------------------------------------------------------------------
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rotation(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rxm = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    rym = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    rzm = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(mat_mul(rzm, rym), rxm)


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def part_mats(show, poses):
    """World matrix of every shown part (model space), posed: poses add to the rest rotation."""
    parts = by_name()
    mats = {}
    for p in SPEC:
        if not show(p["name"]):
            continue
        if p["parent"] is not None and p["parent"] not in mats:
            continue
        if p["parent"] is None:
            parent = translate(0, 0, 0)
            px, py, pz = p["pivot"]
        else:
            parent = mats[p["parent"]]
            qx, qy, qz = parts[p["parent"]]["pivot"]
            px, py, pz = p["pivot"][0] - qx, p["pivot"][1] - qy, p["pivot"][2] - qz
        rx, ry, rz = p["rot"]
        dx, dy, dz = poses.get(p["name"], (0.0, 0.0, 0.0))
        mats[p["name"]] = mat_mul(mat_mul(parent, translate(px, py, pz)), rotation(rx + dx, ry + dy, rz + dz))
    return mats


def world_boxes(show, poses):
    mats = part_mats(show, poses)
    out = []
    for p in SPEC:
        if p["name"] not in mats:
            continue
        m = mats[p["name"]]
        for b in p["boxes"]:
            (x, y, z), (w, h, d) = b["at"], b["size"]
            corners = [apply(m, (x + w * i, y + h * j, z + d * k)) for i in (0, 1) for j in (0, 1) for k in (0, 1)]
            out.append((corners, b, p["name"]))
    return out


# Corner index = i*4 + j*2 + k for (x, y, z) at (min/max). Each face: four corners in the order vanilla maps to
# (u2, v1), (u1, v1), (u1, v2), (u2, v2) of its rectangle.
FACE_CORNERS = {
    "top": (5, 1, 0, 4),
    "bottom": (6, 2, 3, 7),
    "xneg": (0, 1, 3, 2),
    "front": (4, 0, 2, 6),
    "xpos": (5, 4, 6, 7),
    "back": (1, 5, 7, 3),
}
FACE_NORMALS = {"top": (0, -1, 0), "bottom": (0, 1, 0), "xneg": (-1, 0, 0), "xpos": (1, 0, 0), "front": (0, 0, -1),
                "back": (0, 0, 1)}


# --- Z-fighting --------------------------------------------------------------------------------------------------------
POSES = {
    "rest": (lambda n: n not in OARS, {}),
    "paddling": (lambda n: True, {"left_oar": (0.0, -0.5, 0.25), "right_oar": (0.0, 0.5, -0.25)}),
    "ashore": (lambda n: n not in OARS, {"motor_tilt": (1.15, 0, 0), "front_left_foot": (0.45, 0, 0),
                                         "back_right_foot": (0.45, 0, 0), "front_right_foot": (-0.45, 0, 0),
                                         "back_left_foot": (-0.45, 0, 0), "motor": (0, 0.35, 0)}),
}


def check_coplanar():
    problems = set()
    for pose, (show, poses) in POSES.items():
        scene = world_boxes(show, poses)
        faces_ = []
        for k, (corners, b, pname) in enumerate(scene):
            for fname, idx in FACE_CORNERS.items():
                pts = [corners[i] for i in idx]
                a, bb, cc = pts[0], pts[1], pts[2]
                # The outward normal: the box's own axis, carried by the part's rotation.
                n0 = FACE_NORMALS[fname]
                origin = corners[0]
                ax = [corners[4][i] - origin[i] for i in range(3)]
                ay = [corners[2][i] - origin[i] for i in range(3)]
                az = [corners[1][i] - origin[i] for i in range(3)]
                def unit(v):
                    l = math.sqrt(sum(c * c for c in v)) or 1.0
                    return [c / l for c in v]
                ux, uy, uz = unit(ax), unit(ay), unit(az)
                n = [n0[0] * ux[i] + n0[1] * uy[i] + n0[2] * uz[i] for i in range(3)]
                faces_.append(Face("%s:%s%s.%s" % (pname, b["at"], b["size"], fname), pts, n))
        for a, bb, area in fights(faces_):
            problems.add((pose, a, bb, area))
    for p in sorted(problems):
        print("z-fight (%s): %s / %s, %.3f px^2" % p)
    print("z-fight check: %d problem(s)" % len(problems))
    return len(problems)


# --- JSON models ---------------------------------------------------------------------------------------------------------
def rot3(rx, ry, rz):
    m = rotation(rx, ry, rz)
    return [row[:3] for row in m[:3]]


def m3mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def to_euler_deg(m):
    """Degrees (x, y, z) with Rz*Ry*Rx == m, the order Minecraft applies a JSON element's x/y/z rotation in."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    a = math.atan2(m[2][1], m[2][2])
    c = math.atan2(m[1][0], m[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def r4(v):
    v = round(v, 4)
    return 0.0 if v == 0 else v


def json_elements(show, box_filter, offset):
    """The shown boxes as JSON model elements. Model space (y down, x mirrored by the entity renderer) to JSON space:
    x' = 8 - x, y' = 24 - y, z' = z + offset; a part's rotation R becomes S R S with S = diag(-1, -1, 1)."""
    mats = part_mats(show, {})
    parts = by_name()
    out = []
    for p in SPEC:
        if p["name"] not in mats:
            continue
        m = mats[p["name"]]
        rot = [row[:3] for row in m[:3]]
        t = [m[0][3], m[1][3], m[2][3]]
        S = [[-1, 0, 0], [0, -1, 0], [0, 0, 1]]
        rj = m3mul(m3mul(S, rot), S)
        turned = any(abs(rot[i][j] - (1 if i == j else 0)) > 1e-6 for i in range(3) for j in range(3))
        for b in p["boxes"]:
            if not box_filter(b):
                continue
            (x, y, z), (w, h, d) = b["at"], b["size"]
            # The box carried to its part's origin, unrotated; the element's rotation turns it about that origin.
            lo = (x + t[0], y + t[1], z + t[2])
            hi = (lo[0] + w, lo[1] + h, lo[2] + d)
            frm = [8 - hi[0], 24 - hi[1], lo[2] + offset]
            to = [8 - lo[0], 24 - lo[1], hi[2] + offset]
            fr = faces(b, exact=True)

            def uv(rect, flip):
                u0, v0, u1, v1 = (c * UV_SCALE for c in rect)
                return [r4(u1), r4(v1), r4(u0), r4(v0)] if flip else [r4(u0), r4(v0), r4(u1), r4(v1)]
            e = OrderedDict()
            e["name"] = p["name"]
            e["from"] = [r4(v) for v in frm]
            e["to"] = [r4(v) for v in to]
            if turned:
                ex, ey, ez = (r4(v) for v in to_euler_deg(rj))
                rot_o = OrderedDict()
                rot_o["x"], rot_o["y"], rot_o["z"] = ex, ey, ez
                rot_o["origin"] = [r4(8 - t[0]), r4(24 - t[1]), r4(t[2] + offset)]
                e["rotation"] = rot_o
            e["faces"] = OrderedDict([
                ("north", {"uv": uv(fr["front"], False), "texture": "#tub"}),
                ("east", {"uv": uv(fr["xneg"], False), "texture": "#tub"}),
                ("south", {"uv": uv(fr["back"], False), "texture": "#tub"}),
                ("west", {"uv": uv(fr["xpos"], False), "texture": "#tub"}),
                ("up", {"uv": uv(fr["top"], True), "texture": "#tub"}),
                ("down", {"uv": uv(fr["bottom"], True), "texture": "#tub"}),
            ])
            out.append(e)
    return out


def split_at(elements, z):
    """Cuts elements crossing the plane z' = z in two (unrotated ones only; rotated ones go whole to one side), keeping
    each half's texture: the cut face of an enamel wall shows its end grain, which no one sees under the next half."""
    a, b = [], []
    for e in elements:
        z0, z1 = e["from"][2], e["to"][2]
        if "rotation" in e or z1 <= z + 1e-6:
            (a if (z0 + z1) / 2 < z else b).append(e)
            continue
        if z0 >= z - 1e-6:
            b.append(e)
            continue
        t = (z - z0) / (z1 - z0)
        for half, lo, hi in ((a, z0, z), (b, z, z1)):
            f = json.loads(json.dumps(e), object_pairs_hook=OrderedDict)
            f["from"][2], f["to"][2] = r4(lo), r4(hi)
            s0, s1 = (0.0, t) if half is a else (t, 1.0)
            for name in ("east", "west", "up", "down"):
                u0, v0, u1, v1 = f["faces"][name]["uv"]
                if name == "east":      # u runs toward -z' on east... from z1 at u0 to z0 at u1
                    f["faces"][name]["uv"] = [r4(u0 + (u1 - u0) * (1 - s1)), v0, r4(u0 + (u1 - u0) * (1 - s0)), v1]
                elif name == "west":    # u runs toward +z' from z0
                    f["faces"][name]["uv"] = [r4(u0 + (u1 - u0) * s0), v0, r4(u0 + (u1 - u0) * s1), v1]
                elif name == "up":      # v runs toward +z' from z0
                    f["faces"][name]["uv"] = [u0, r4(v0 + (v1 - v0) * s0), u1, r4(v0 + (v1 - v0) * s1)]
                else:                   # down: v runs toward -z' from z1
                    f["faces"][name]["uv"] = [u0, r4(v0 + (v1 - v0) * (1 - s1)), u1, r4(v0 + (v1 - v0) * (1 - s0))]
            if half is a:
                del f["faces"]["south"]
            else:
                del f["faces"]["north"]
            half.append(f)
    return a, b


def block_display():
    return None


FP_ROTATION = [18, 150, 0]
FP_SHIFT = [-2.0, 4.0, -1.0]


def item_displays(elements, fp_scale, gui_scale):
    from gunkit import display, centred
    lo = [min(min(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
    hi = [max(max(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    gui = [30, 225, 0]
    # Held out in front, turned so the motor end points back past the hand and the inside of the tub shows.
    fp = FP_ROTATION
    fp_t = [round(v + d, 3) for v, d in zip(centred(fp, fp_scale, c), FP_SHIFT)]
    return OrderedDict([
        ("thirdperson_righthand", display([75, 45, 0], centred([75, 45, 0], 0.22, c, 2.5), 0.22)),
        ("thirdperson_lefthand", display([75, 45, 0], centred([75, 45, 0], 0.22, c, 2.5), 0.22)),
        ("firstperson_righthand", display(fp, fp_t, fp_scale)),
        ("firstperson_lefthand", display(fp, fp_t, fp_scale)),
        ("gui", display(gui, centred(gui, gui_scale, c), gui_scale)),
        ("ground", display([0, 0, 0], centred([0, 0, 0], 0.2, c, 1.5), 0.2)),
        ("fixed", display([0, 90, 0], centred([0, 90, 0], gui_scale * 1.2, c), round(gui_scale * 1.2, 3))),
    ])


def model_json(elements, displays=None, parent=None):
    m = OrderedDict()
    if parent:
        m["parent"] = parent
    m["textures"] = OrderedDict([("tub", "tomyen:block/bathtub"), ("particle", "tomyen:block/bathtub")])
    if displays:
        m["display"] = displays
    m["elements"] = elements
    return m


def block_models():
    show = lambda n: n in BLOCK_PARTS
    keep = lambda b: id(b) not in BOAT_ONLY_BOXES
    # Facing north: the head (faucet end) is the block north of the foot. Head z' = z + 16, foot z' = z.
    elements = json_elements(show, keep, 16.0)
    head, foot_ = split_at(elements, 16.0)
    for e in foot_:
        for k in ("from", "to"):
            e[k][2] = r4(e[k][2] - 16.0)
        if "rotation" in e:
            e["rotation"]["origin"][2] = r4(e["rotation"]["origin"][2] - 16.0)
    return head, foot_


def item_models():
    show_tub = lambda n: n in BLOCK_PARTS
    keep_tub = lambda b: id(b) not in BOAT_ONLY_BOXES
    tub = json_elements(show_tub, keep_tub, 7.0)
    boat = json_elements(lambda n: n not in OARS, lambda b: True, 7.0)
    return tub, boat


# --- Icons ----------------------------------------------------------------------------------------------------------------
PALETTE = {
    ".": CLEAR, "k": rgb(0x15171A), "c": rgb(0x3B4048), "C": rgb(0x5B626C), "d": rgb(0x25282D), "r": STRIPE_RED,
    "o": STRIPE_ORANGE, "w": STRIPE_WHITE, "m": rgb(0x535A63), "M": rgb(0x8A929C), "n": rgb(0x3D424A), "p": rgb(0xC4CCD4),
    "P": rgb(0xF0F4F7), "q": rgb(0x8E98A3), "Q": rgb(0x66707B), "b": rgb(0x2A2A30), "s": rgb(0xDDE4EA),
}
ICONS = {
    "outboard_motor": [
        "................",
        "....kkkkkkk.....",
        "...kCCCCCCCk....",
        "..kCccccccccbb..",
        "..kcrrrrrrrrcbk.",
        "..kcwwwwwwwwck..",
        "..kcoooooooock..",
        "..kccccccccck...",
        "...kdddddddk....",
        ".....kMmnk......",
        ".....kMmnk......",
        "...kkkMmnkkk....",
        "....kMmmmnk.....",
        "...kpkMmnnk.....",
        "...kPpqQkk......",
        "....kk.kk.......",
    ],
    "propeller": [
        "................",
        "......kk........",
        ".....kPpk.......",
        ".....kPpk.......",
        ".....kpqk.......",
        "......kpqk......",
        "......kpqk......",
        "......kssk......",
        ".....kpsQqk.....",
        "....kPpkkqQk....",
        "...kPpk..kqQk...",
        "..kPpk....kqQk..",
        "..kpk......kQk..",
        "...k........k...",
        "................",
        "................",
    ],
}


def sprite(rows):
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            c.set(x, y, PALETTE[ch])
    return c


# --- Data -------------------------------------------------------------------------------------------------------------------
def advancement(name, category, unlock_items):
    write_json(os.path.join(DATA, "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", OrderedDict([("conditions", {"items": [{"items": unlock_items}]}),
                                      ("trigger", "minecraft:inventory_changed")])),
            ("has_the_recipe", OrderedDict([("conditions", {"recipes": "tomyen:" + name}), ("trigger", "minecraft:recipe_unlocked")])),
        ])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]}),
    ]))


def shaped(name, result, pattern, key, unlock, count=1, category="misc", folder="misc"):
    write_json(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, folder, unlock)


def shapeless(name, result, ingredients, unlock, category="misc", folder="misc"):
    write_json(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", 1), ("id", result)]))]))
    advancement(name, folder, unlock)


def data():
    shaped("propeller", "tomyen:propeller", [" P ", " S ", "P P"], {"P": "tomyen:steel_plate", "S": "tomyen:steel_ingot"},
           "tomyen:steel_plate")
    shaped("propeller_from_tungsten", "tomyen:propeller", [" T ", " S ", "T T"],
           {"T": "tomyen:tungsten_ingot", "S": "tomyen:steel_ingot"}, "tomyen:tungsten_ingot", count=2)
    shaped("outboard_motor", "tomyen:outboard_motor", ["PEP", "RAS", " X "],
           {"P": "tomyen:steel_plate", "E": "tomyen:empty_canister", "R": "tomyen:rubber", "A": "tomyen:servo_arm",
            "S": "tomyen:steel_ingot", "X": "tomyen:propeller"}, "tomyen:propeller", category="equipment",
           folder="transportation")
    shaped("bathtub", "tomyen:bathtub", ["P P", "QWQ", "N N"],
           {"P": "tomyen:steel_plate", "Q": "minecraft:quartz", "W": "minecraft:white_dye", "N": "minecraft:gold_nugget"},
           "tomyen:steel_plate", category="building", folder="decorations")
    shapeless("bathtub_boat", "tomyen:bathtub_boat", ["tomyen:bathtub", "tomyen:outboard_motor"], "tomyen:outboard_motor",
              category="misc", folder="transportation")
    # The propeller's other use: rotors for the Skullcrane's servo drone, in place of the bone struts.
    shaped("servo_drone_from_propellers", "tomyen:servo_drone", [" L ", "XCX", "A A"],
           {"L": "tomyen:optic_lens", "X": "tomyen:propeller", "C": "tomyen:computer_chip", "A": "tomyen:servo_arm"},
           "tomyen:propeller")
    # Loot: only the head drops the tub (the bed's own table, 26.3 format: match_block).
    write_json(os.path.join(DATA, "loot_table", "blocks", "bathtub.json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([
            ("condition", {"type": "minecraft:survives_explosion"}),
            ("entries", [OrderedDict([
                ("type", "minecraft:item"),
                ("condition", OrderedDict([("type", "minecraft:match_block"), ("blocks", "tomyen:bathtub"),
                                           ("state", {"part": "head"})])),
                ("name", "tomyen:bathtub")])]),
            ("rolls", 1)])]),
        ("random_sequence", "tomyen:blocks/bathtub"),
    ]))


def assets(tex):
    tex.save(os.path.join(TEX, "block", "bathtub.png"))
    head, foot_ = block_models()
    write_json(os.path.join(ASSETS, "models", "block", "bathtub_head.json"), model_json(head))
    write_json(os.path.join(ASSETS, "models", "block", "bathtub_foot.json"), model_json(foot_))
    variants = OrderedDict()
    for facing, y in (("east", 90), ("north", 0), ("south", 180), ("west", 270)):
        for half in ("foot", "head"):
            v = OrderedDict([("model", "tomyen:block/bathtub_" + half)])
            if y:
                v["y"] = y
            variants["facing=%s,part=%s" % (facing, half)] = v
    write_json(os.path.join(ASSETS, "blockstates", "bathtub.json"), {"variants": variants})
    tub, boat = item_models()
    write_json(os.path.join(ASSETS, "models", "item", "bathtub.json"), model_json(tub, item_displays(tub, 0.3, 0.42)))
    write_json(os.path.join(ASSETS, "models", "item", "bathtub_boat.json"), model_json(boat, item_displays(boat, 0.28, 0.38)))
    for name in ("bathtub", "bathtub_boat", "outboard_motor", "propeller"):
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": OrderedDict([("type", "minecraft:model"), ("model", "tomyen:item/" + name)])})
    for name, rows in ICONS.items():
        sprite(rows).save(os.path.join(TEX, "item", name + ".png"))
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    return head, foot_, tub, boat


# --- Sound ------------------------------------------------------------------------------------------------------------------
RATE = 44100


def squeak():
    """A rubber duck's squeak: air forced through the reed in its bottom. A buzzy tone (a narrow pulse, rich in
    harmonics, through a formant round 2.6 kHz) that swoops up fast, holds with a wobble and drops as the duck refills."""
    n = int(0.32 * RATE)
    xs = []
    phase = 0.0
    lp = 0.0
    bp1 = bp2 = 0.0
    for i in range(n):
        t = i / RATE
        u = t / 0.32
        f0 = 780 + 620 * math.sin(math.pi * min(1.0, u * 1.6)) ** 0.6 - 260 * max(0.0, u - 0.6) / 0.4
        f0 *= 1.0 + 0.035 * math.sin(2 * math.pi * 31 * t)
        phase = (phase + f0 / RATE) % 1.0
        pulse = 1.0 if phase < 0.18 else -0.22
        # A two-pole resonator for the formant, mixed with a little of the raw pulse.
        w = 2 * math.pi * 2600 / RATE
        r = 0.93
        y = pulse + 2 * r * math.cos(w) * bp1 - r * r * bp2
        bp2, bp1 = bp1, y
        lp += 0.35 * (pulse - lp)
        env = min(1.0, t / 0.012) * min(1.0, (0.32 - t) / 0.06)
        xs.append((0.12 * y + 0.5 * lp) * env)
    top = max(abs(x) for x in xs) or 1.0
    return [x / top * 0.8 for x in xs]


def sounds():
    import vorbis_mono
    path = os.path.join(ASSETS, "sounds", "rubber_duck_squeak.ogg")
    vorbis_mono.encode(squeak(), path)
    print("wrote", path)


# --- Preview ---------------------------------------------------------------------------------------------------------------
BG = rgb(0x2B3138)


def render(scene, tex, yaw, pitch, w, h, scale, target, extra=()):
    """Draws the boxes looking at `target` (a model-space point, put in the middle of the picture)."""
    img = [BG] * (w * h)
    depth = [1e9] * (w * h)
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    light = (-0.45, 0.75, -0.5)
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)

    def view(p):
        x, y, z = -p[0], 24.0 - p[1], p[2]
        x, z = x * cy_ - z * sy_, x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    tx, ty, _ = view(target)
    centre = (w / 2 - tx * scale, h / 2 + ty * scale)
    items = [(corners, b, None) for corners, b, _ in scene] + [(corners, None, colour) for corners, colour in extra]
    for corners, b, colour in items:
        v = [view(c) for c in corners]
        for name, idx in FACE_CORNERS.items():
            if b is not None:
                rect = faces(b)[name]
                fx0, fy0, fx1, fy1 = faces(b, exact=True)[name]
                uvs = [(fx1, fy0), (fx0, fy0), (fx0, fy1), (fx1, fy1)]
            else:
                rect, uvs = None, [(0, 0)] * 4
            q = [v[i] for i in idx]
            e1 = [q[1][k] - q[0][k] for k in range(3)]
            e2 = [q[3][k] - q[0][k] for k in range(3)]
            n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
            nl = math.sqrt(sum(c * c for c in n)) or 1.0
            n = tuple(c / nl for c in n)
            lit = 0.6 + 0.45 * abs(sum(n[k] * light[k] for k in range(3)))
            for tri in ((0, 1, 2), (0, 2, 3)):
                raster(img, depth, w, h, [q[i] for i in tri], [uvs[i] for i in tri], rect, tex, lit, scale, centre, colour)
    return img


def raster(img, depth, w, h, pts, uvs, rect, tex, lit, scale, centre, colour):
    sp = [(centre[0] + p[0] * scale, centre[1] - p[1] * scale, p[2]) for p in pts]
    minx, maxx = max(0, int(min(p[0] for p in sp))), min(w - 1, int(max(p[0] for p in sp)) + 1)
    miny, maxy = max(0, int(min(p[1] for p in sp))), min(h - 1, int(max(p[1] for p in sp)) + 1)
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = sp
    area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(area) < 1e-6:
        return
    for py in range(miny, maxy + 1):
        for px in range(minx, maxx + 1):
            x, y = px + 0.5, py + 0.5
            w0 = ((bx - x) * (cy - y) - (cx - x) * (by - y)) / area
            w1 = ((cx - x) * (ay - y) - (ax - x) * (cy - y)) / area
            w2 = 1.0 - w0 - w1
            if w0 < -1e-4 or w1 < -1e-4 or w2 < -1e-4:
                continue
            z = w0 * az + w1 * bz + w2 * cz
            i = py * w + px
            if z >= depth[i]:
                continue
            if colour is not None:
                depth[i] = z
                img[i] = shade(colour, lit)
                continue
            u = w0 * uvs[0][0] + w1 * uvs[1][0] + w2 * uvs[2][0]
            v = w0 * uvs[0][1] + w1 * uvs[1][1] + w2 * uvs[2][1]
            tu = min(rect[2] - 1, max(rect[0], int(math.floor(u))))
            tv = min(rect[3] - 1, max(rect[1], int(math.floor(v))))
            col = tex.get(tu, tv)
            if col[3] == 0:
                continue
            depth[i] = z
            img[i] = shade(col, lit)


# BathtubBoat.DRIVER_SEAT and FRONT_SEAT, and DRIVER_RAISE: the driver sits up on the caddy.
DRIVER_SEAT = -0.6875
FRONT_SEAT = 0.26
DRIVER_RAISE = (RIM[1] + 1.0 - FLOOR - 1.0) / 16.0
FRONT_SINK = 2.0 / 16.0
# Hips a pixel above the floor: a seated player's hips are 0.15 blocks above where they're attached to the vehicle.
RIDE_HEIGHT = (FLOOR + LIFT + 1.0) / 16.0 - 0.15


def rider_boxes(z, colour_body, colour_skin, raise_=0.0):
    """A stand-in for a seated player (riding pose: legs forward), hips at RIDE_HEIGHT + 0.15 (+ raise_) blocks."""
    hip = (RIDE_HEIGHT + 0.15 + raise_) * 16.0

    def b(x0, x1, h0, h1, z0, z1):
        return [(x0 if i == 0 else x1, 24.0 - (h0 if j == 1 else h1), z0 if k == 0 else z1) for i in (0, 1) for j in (0, 1)
                for k in (0, 1)]
    return [
        (b(-4, 4, hip, hip + 12, z - 2, z + 2), colour_body),
        (b(-4, 4, hip + 12, hip + 20, z - 4, z + 4), colour_skin),
        (b(-8, -4, hip + 2, hip + 12, z - 2, z + 2), colour_body),
        (b(4, 8, hip + 2, hip + 12, z - 2, z + 2), colour_body),
        (b(-4, 0, hip - 2, hip + 2, z - 12, z), rgb(0x3B4A8C)),
        (b(0, 4, hip - 2, hip + 2, z - 12, z), rgb(0x3B4A8C)),
    ]


def save_sheet(path, views, w, h):
    out = Canvas(w * len(views), h)
    for k, img in enumerate(views):
        for y in range(h):
            for x in range(w):
                out.set(k * w + x, y, img[y * w + x])
    out.save(path)


def preview(out_dir, tex, tub, boat):
    os.makedirs(out_dir, exist_ok=True)
    w, h, s = 520, 400, 8.5
    mid = (0.0, 24.0 - 9.0, 3.0)
    rest = world_boxes(lambda n: n not in OARS, {})
    riders = rider_boxes(-DRIVER_SEAT * 16.0, rgb(0x3E8E7E), rgb(0xC69C7A), DRIVER_RAISE) \
        + rider_boxes(-FRONT_SEAT * 16.0, rgb(0x9B4A4A), rgb(0xC69C7A), -FRONT_SINK)
    views = [
        render(rest, tex, math.radians(-35), math.radians(-20), w, h, s, mid),
        render(rest, tex, math.radians(145), math.radians(-20), w, h, s, mid),
        render(rest, tex, math.radians(-90), math.radians(-4), w, h, s, mid),
        render(rest, tex, math.radians(-20), math.radians(-70), w, h, s, mid),
    ]
    save_sheet(os.path.join(out_dir, "boat.png"), views[:2], w, h)
    save_sheet(os.path.join(out_dir, "boat2.png"), views[2:], w, h)
    show, poses = POSES["ashore"]
    paddle_show, paddle_poses = POSES["paddling"]
    views = [
        render(rest, tex, math.radians(-35), math.radians(-20), w, h, s, mid, riders),
        render(rest, tex, math.radians(150), math.radians(-25), w, h, s, mid, riders),
        render(world_boxes(show, poses), tex, math.radians(-60), math.radians(-12), w, h, s, mid),
        render(world_boxes(paddle_show, paddle_poses), tex, math.radians(-25), math.radians(-30), w, h, s, mid, riders),
    ]
    save_sheet(os.path.join(out_dir, "boat_riders.png"), views[:2], w, h)
    save_sheet(os.path.join(out_dir, "boat_land.png"), views[2:], w, h)
    # Close-ups: the faucet end, the motor, a foot.
    views = [
        render(rest, tex, math.radians(-150), math.radians(-30), w, h, 20.0, (0.0, 24.0 - 13.0, -13.0)),
        render(rest, tex, math.radians(35), math.radians(-20), w, h, 18.0, (0.0, 24.0 - 11.0, 19.0)),
    ]
    save_sheet(os.path.join(out_dir, "boat_close.png"), views, w, h)
    views = [
        render(rest, tex, math.radians(-50), math.radians(-12), w, h, 40.0, (FOOT_X, 24.0 - 3.0, -FOOT_Z)),
        render(rest, tex, math.radians(-30), math.radians(-25), w, h, 30.0, DUCK_PIVOT),
    ]
    save_sheet(os.path.join(out_dir, "boat_close2.png"), views, w, h)
    tex.save(os.path.join(out_dir, "texture.png"))
    # The item models, through the same software renderer the gun previews use.
    from gunsmith_kit import render as render_elements, first_person, gui_icon, compose, ortho, SIDE, RIGHT_FRONT, LEFT_REAR
    atlases = {"#tub": (tex.px, TEX_W)}
    rows = []
    for name, elements in (("bathtub", tub), ("bathtub_boat", boat)):
        m = json.load(open(os.path.join(ASSETS, "models", "item", name + ".json")))
        lo = [min(min(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
        hi = [max(max(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
        c = [(lo[a] + hi[a]) / 2 for a in range(3)]
        cells = []
        for view in (SIDE, RIGHT_FRONT, LEFT_REAR):
            img = render_elements(m["elements"], atlases, ortho(view, 9.0, 400, 260, c), lambda n: n, 400, 260)
            cells.append(([(58, 60, 66) if v is None else v for v in img], 400, 260, 1))
        project, turn = first_person(m["display"], 480, 300)
        fp = render_elements(m["elements"], atlases, project, turn, 480, 300)
        cells.append(([(110, 130, 160) if v is None else v for v in fp], 480, 300, 1))
        project, turn = gui_icon(m["display"], 48)
        ic = render_elements(m["elements"], atlases, project, turn, 48, 48)
        cells.append(([(139, 139, 139) if v is None else v for v in ic], 48, 48, 3))
        rows.append(cells)
    # The block, both halves side by side as placed facing north.
    hm = json.load(open(os.path.join(ASSETS, "models", "block", "bathtub_head.json")))
    fm = json.load(open(os.path.join(ASSETS, "models", "block", "bathtub_foot.json")))
    els = json.loads(json.dumps(hm["elements"]))
    for e in json.loads(json.dumps(fm["elements"])):
        e["from"][2] += 16
        e["to"][2] += 16
        if "rotation" in e:
            e["rotation"]["origin"][2] += 16
        els.append(e)
    cells = []
    for view in (RIGHT_FRONT, LEFT_REAR):
        img = render_elements(els, atlases, ortho(view, 9.0, 400, 260, [8, 8, 16]), lambda n: n, 400, 260)
        cells.append(([(58, 60, 66) if v is None else v for v in img], 400, 260, 1))
    rows.append(cells)
    icons = []
    for name in ICONS:
        c = sprite(ICONS[name])
        icons.append(([p[:3] if p[3] else (139, 139, 139) for p in c.px], 16, 16, 6))
    rows.append(icons)
    compose(rows, os.path.join(out_dir, "items.png"))


def check_models(head, foot_, tub, boat):
    from gunsmith_kit import check_all
    n = 0
    for name, elements in (("bathtub_head", head), ("bathtub_foot", foot_), ("bathtub item", tub), ("bathtub_boat item", boat)):
        try:
            check_all(name, elements)
        except SystemExit as e:
            print(e)
            n += 1
        lo = [min(min(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
        hi = [max(max(e["from"][a], e["to"][a]) for e in elements) for a in range(3)]
        if min(lo) < -16 or max(hi) > 32:
            print("%s: out of the -16..32 model range: %s %s" % (name, lo, hi))
            n += 1
    print("model checks: %d problem(s)" % n)
    return n


if __name__ == "__main__":
    used = pack()
    count = sum(len(p["boxes"]) for p in SPEC)
    print("boxes: %d (oars %d), texture rows used: %d of %d" % (count, sum(len(p["boxes"]) for p in SPEC if p["name"] in OARS),
                                                               used, TEX_H))
    if "--java" in sys.argv:
        print(java())
        sys.exit(0)
    if "--java-into" in sys.argv:
        # Rewrites the generated part of BathtubBoatModel.createBodyLayer.
        path = sys.argv[sys.argv.index("--java-into") + 1]
        src = open(path).read()
        begin = "\t\t// BEGIN GENERATED (tools/gen_bathtub_boat.py)\n"
        end = "\t\t// END GENERATED\n"
        i, j = src.index(begin) + len(begin), src.index(end)
        open(path, "w").write(src[:i] + java() + "\n" + src[j:])
        print("wrote", path)
    tex = paint()
    head, foot_, tub, boat = assets(tex)
    data()
    if "--no-sound" not in sys.argv:
        sounds()
    problems = check_coplanar() + check_models(head, foot_, tub, boat)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1], tex, tub, boat)
    if "--check" in sys.argv and problems:
        sys.exit(1)
