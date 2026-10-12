"""Generates gun attachment models, wraps each gun's item model so fitted attachments render, and paints textures."""
import json, math, os, struct, sys, zlib, random
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
import gunkit

ASSETS = sys.argv[1]  # .../assets/mymod
MODELS = os.path.join(ASSETS, "models/item")
ITEMS = os.path.join(ASSETS, "items")
TEX = os.path.join(ASSETS, "textures")

# Red dot positions are mirrored in GunAim (window centre and eye distance), so move both together.
# Mount points in each gun's model space (pixels; muzzle toward -z, x centred on 8):
#   muzzle (z, y) and suppressor length, laser (front z, underside y), optic (front z, top y),
#   grip (centre z, underside y: on the rail behind the laser, clear of the magazine)
GUNS = {
    "glock":        dict(muzzle=(0.0, 10.1), sup=5.5, laser=(0.4, 8.0),  optic=(9.8, 11.2), switch=True),
    "m1911":        dict(muzzle=(-8.5, 10.0), sup=5.5, laser=(-6.0, 8.0), optic=(1.8, 11.0)),
    "revolver_357": dict(muzzle=None, laser=(-8.6, 8.5), optic=(-0.6, 11.7)),
    "mac10":        dict(muzzle=(-9.0, 8.6), sup=6.0, laser=(-8.8, 8.15), optic=(-0.5, 10.4), grip=(-5.0, 7.0)),
    "mp5":          dict(muzzle=(-13.0, 8.75), sup=6.5, laser=(-7.5, 6.5), optic=(3.0, 10.3), grip=(-2.5, 6.5)),
    "m4":           dict(muzzle=(-14.0, 8.0), sup=7.5, laser=(-6.5, 6.1), optic=(5.0, 10.4), grip=(-1.5, 6.1)),
    "akm":          dict(muzzle=(-13.0, 8.2), sup=7.5, laser=(-1.0, 6.6), optic=(10.0, 10.2), grip=(3.2, 6.6)),
    "galil":        dict(muzzle=(-15.0, 8.2), sup=7.5, laser=(-2.0, 6.6), optic=(3.3, 10.2), grip=(1.8, 6.6)),
    "awp":          dict(muzzle=(-16.0, 8.9), sup=9.0, laser=(-6.5, 6.5), optic=None, grip=(-2.5, 6.5)),
    # Shotguns: an optic on the receiver, a laser on the barrel clamp under the magazine tube (see gen_shotguns.py).
    "remington_870": dict(muzzle=None, laser=(-13.1, 6.55), optic=(7.4, 10.1)),
    "spas12":       dict(muzzle=None, laser=(-12.85, 6.95), optic=(7.5, 10.6)),
    # The M249's optic sits on the feed cover's rail (see gen_m249.py and COVER_HINGE below).
    "m249":         dict(muzzle=(-15.9, 9.0), sup=7.5, laser=(-3.9, 6.4), optic=(5.6, 11.85), grip=(1.8, 6.4)),
}
# Iron sights (by element name) taken off a gun while an optic is fitted, and parts cut down to sit flush instead.
IRON_SIGHTS = {
    "glock": ["front_sight", "rear_sight"],
    "m1911": ["front_sight", "rear_sight"],
    "revolver_357": ["front_sight", "rear_sight"],
    "mac10": ["front_sight", "rear_sight"],
    "mp5": ["front_hood_left", "front_hood_right", "front_hood_top", "front_sight_post", "rear_sight_base", "rear_sight_drum"],
    "m4": ["front_sight_tower", "front_sight_post", "rear_sight", "rear_sight_aperture"],
    "akm": ["front_sight_post", "front_sight_ear_left", "front_sight_ear_right", "rear_sight_leaf"],
    "galil": ["front_sight_post", "front_sight_ears", "carry_handle", "carry_handle~l", "carry_handle~r", "carry_handle~bpp",
              "carry_handle~bnp", "rear_sight"],
    "spas12": ["front_sight", "front_sight_ear", "rear_sight_base", "rear_sight", "rear_sight_aperture", "rear_sight_ear"],
    "m249": ["front_sight_post", "rear_sight", "rear_sight_aperture"],
}
# The AK's rear sight block also carries the gas tube, so it stays, cut down to just under the dust cover's top.
IRON_SIGHT_TRIMS = {"akm": {"rear_sight_base": 10.18}}

# Pistols whose optic (and switch) sit on the slide: how far back the slide is in each racking frame
# (the mymod:gun_animation frames 1-6), so those parts ride along with it.
SLIDE_TRAVEL = {"glock": [0.417, 0.833, 1.25, 1.667, 2.083, 2.5], "m1911": [0.417, 0.833, 1.25, 1.667, 2.083, 2.5]}

# Guns whose optic rail is on a hinged cover: the hinge (y, z) and how far open the cover is (degrees, front up) in each
# mymod:gun_animation frame, so a fitted optic swings up with it (the M249's feed cover, frames 7-16).
COVER_HINGE = {"m249": dict(hinge=(10.9, 13.3), angles={**{f: 12.5 * (f - 6) for f in range(7, 13)}, **{f: 75.0 for f in range(13, 17)}})}

CONTEXTS = ["thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
            "head", "gui", "ground", "fixed", "on_shelf"]

# Swatches in attachment_parts.png: a 32x32 texture in 2x2-unit cells (4x4 texels each). u0, v0, u1, v1 in model UV units.
def cell(col, row):
    return (col * 2, row * 2, col * 2 + 2, row * 2 + 2)
SUP_BODY, SUP_CAP, MOUNT, LASER_BODY = cell(0, 0), cell(1, 0), cell(2, 0), cell(3, 0)
LASER_FRONT, DOT_HOUSING, DOT_EMITTER, SWITCH = cell(4, 0), cell(5, 0), cell(6, 0), cell(7, 0)
OILCAN_BODY, OILCAN_CAP, BRAKE, FLASH = cell(0, 1), cell(1, 1), cell(2, 1), cell(3, 1)
GREEN_LASER_BODY, GREEN_LASER_FRONT, KOBRA_BODY, EOTECH_BODY = cell(4, 1), cell(5, 1), cell(6, 1), cell(7, 1)
GLASS_BLUE, GLASS_AMBER, RETICLE, BOLT = cell(0, 2), cell(1, 2), cell(2, 2), cell(3, 2)
RUBBER, OPTIC_BODY, KOBRA_DIAL, EOTECH_BUTTON = cell(4, 2), cell(5, 2), cell(6, 2), cell(7, 2)
GRIP_BODY, GRIP_SIDE, GRIP_CAP = cell(0, 3), cell(1, 3), cell(2, 3)


def box(x0, y0, z0, x1, y1, z1, swatch, only=None, emit=False, rot=None, **faces):
    """rot: (axis, angle, origin). only: the faces to keep (e.g. a pane of glass needs just north and south)."""
    el = OrderedDict([("from", [round(x0, 4), round(y0, 4), round(z0, 4)]), ("to", [round(x1, 4), round(y1, 4), round(z1, 4)])])
    if rot:
        axis, angle, origin = rot
        el["rotation"] = OrderedDict([("angle", angle), ("axis", axis), ("origin", [round(v, 4) for v in origin])])
    if emit:
        el["shade"] = False
        el["light_emission"] = 15
    el["faces"] = OrderedDict()
    for f in ["north", "east", "south", "west", "up", "down"]:
        if only is None or f in only:
            el["faces"][f] = OrderedDict([("uv", list(faces.get(f, swatch))), ("texture", "#parts")])
    return el


def tube(cx, cy, z0, z1, inner, thickness, swatch, sides=8):
    """A hollow octagonal tube along z: slats around the axis, open to look through."""
    width = 2 * (inner + thickness) * math.tan(math.pi / sides) + 0.02
    return [box(cx - width / 2, cy + inner, z0, cx + width / 2, cy + inner + thickness, z1, swatch,
                rot=("z", k * 360.0 / sides, (cx, cy, z0)) if k else None) for k in range(sides)]


def glass(x0, y0, x1, y1, z, swatch):
    return box(x0, y0, z, x1, y1, z + 0.02, swatch, only=("north", "south"))


def glow(x, y, z, size):
    """One glowing reticle speck, facing the eye."""
    h = size / 2
    return box(x - h, y - h, z, x + h, y + h, z + 0.01, RETICLE, only=("south",), emit=True)


def suppressor(z, y, length):
    z0 = z - length
    return [
        # Two crossed boxes round it off; their ends are staggered so the end caps don't z-fight.
        box(7.2, y - 0.6, z0, 8.8, y + 0.6, z - 0.02, SUP_BODY, north=SUP_CAP, south=SUP_CAP),
        box(7.4, y - 0.8, z0 + 0.04, 8.6, y + 0.8, z - 0.04, SUP_BODY, north=SUP_CAP, south=SUP_CAP),
        # Mount collar where it threads onto the muzzle.
        box(7.1, y - 0.7, z - 0.8, 8.9, y + 0.7, z, MOUNT),
    ]


def oil_can(z, y):
    return [
        # Adapter threaded onto the muzzle, then the fat filter can.
        box(7.5, y - 0.5, z - 1.0, 8.5, y + 0.5, z, MOUNT),
        box(6.8, y - 0.9, z - 5.5, 9.2, y + 0.9, z - 1.0, OILCAN_BODY, north=OILCAN_CAP, south=OILCAN_CAP),
        box(7.1, y - 1.2, z - 5.46, 8.9, y + 1.2, z - 1.04, OILCAN_BODY, north=OILCAN_CAP, south=OILCAN_CAP),
    ]


def muzzle_brake(z, y):
    return [box(7.3, y - 0.65, z - 2.2, 8.7, y + 0.65, z, BRAKE, north=SUP_CAP, south=SUP_CAP)]


def flash_hider(z, y):
    return [box(7.45, y - 0.55, z - 2.0, 8.55, y + 0.55, z, FLASH, north=SUP_CAP, south=SUP_CAP)]


def laser(zf, ytop, body=LASER_BODY, front=LASER_FRONT):
    return [
        box(7.5, ytop - 1.0, zf, 8.5, ytop - 0.15, zf + 2.4, body, north=front),
        box(7.65, ytop - 0.2, zf + 0.5, 8.35, ytop + 0.05, zf + 1.9, MOUNT),
    ]


def green_laser(zf, ytop):
    return laser(zf, ytop, GREEN_LASER_BODY, GREEN_LASER_FRONT)


def holographic(zf, ytop):
    """EOTech: a long base with a hooded window you look through front and back hoops of (window centre ytop+1.225, zf+1.4)."""
    return [
        box(7.3, ytop - 0.05, zf, 8.7, ytop + 0.45, zf + 2.8, EOTECH_BODY),
        box(7.2, ytop + 0.45, zf, 7.4, ytop + 2.0, zf + 0.3, EOTECH_BODY),
        box(8.6, ytop + 0.45, zf, 8.8, ytop + 2.0, zf + 0.3, EOTECH_BODY),
        box(7.2, ytop + 0.45, zf + 2.5, 7.4, ytop + 2.0, zf + 2.8, EOTECH_BODY),
        box(8.6, ytop + 0.45, zf + 2.5, 8.8, ytop + 2.0, zf + 2.8, EOTECH_BODY),
        box(7.2, ytop + 2.0, zf, 8.8, ytop + 2.25, zf + 2.8, EOTECH_BODY),
    ]


def kobra(zf, ytop):
    """Kobra: the reticle box down the right side, a frame of thin posts on the left (window centre ytop+1.05, zf+1.2)."""
    return [
        box(7.4, ytop - 0.05, zf, 8.6, ytop + 0.35, zf + 2.4, MOUNT),
        box(8.45, ytop + 0.35, zf, 9.0, ytop + 1.75, zf + 2.4, KOBRA_BODY),
        box(7.2, ytop + 0.35, zf, 7.45, ytop + 1.75, zf + 0.4, KOBRA_BODY),
        box(7.2, ytop + 0.35, zf + 2.0, 7.45, ytop + 1.75, zf + 2.4, KOBRA_BODY),
        box(7.2, ytop + 1.75, zf, 9.0, ytop + 2.0, zf + 0.4, KOBRA_BODY),
        box(7.2, ytop + 1.75, zf + 2.0, 9.0, ytop + 2.0, zf + 2.4, KOBRA_BODY),
    ]


def red_dot(zf, ytop):
    """Micro red dot: a round tube on a riser and rail clamp, turrets and a brightness dial (window centre ytop+0.95, zf+0.35)."""
    cy = ytop + 0.95
    return [
        box(7.35, ytop - 0.05, zf + 0.15, 8.65, ytop + 0.25, zf + 2.05, MOUNT),
        box(8.65, ytop - 0.05, zf + 0.6, 8.8, ytop + 0.3, zf + 1.6, MOUNT),
        box(8.8, ytop + 0.02, zf + 0.95, 8.95, ytop + 0.22, zf + 1.25, BOLT),
        box(7.62, ytop + 0.25, zf + 0.45, 8.38, cy - 0.5, zf + 1.75, OPTIC_BODY),
        *tube(8, cy, zf + 0.25, zf + 1.85, 0.5, 0.12, OPTIC_BODY),
        *tube(8, cy, zf, zf + 0.3, 0.56, 0.1, OPTIC_BODY),
        *tube(8, cy, zf + 1.8, zf + 2.1, 0.54, 0.1, OPTIC_BODY),
        box(7.78, cy + 0.6, zf + 0.85, 8.22, cy + 0.92, zf + 1.25, OPTIC_BODY, up=SUP_CAP),
        box(8.6, cy - 0.22, zf + 0.85, 8.92, cy + 0.22, zf + 1.25, OPTIC_BODY, east=SUP_CAP),
        box(7.08, cy - 0.18, zf + 1.2, 7.4, cy + 0.18, zf + 1.56, RUBBER),
        glass(7.58, cy - 0.42, 8.42, cy + 0.42, zf + 0.2, GLASS_BLUE),
        glow(8, cy, zf + 0.23, 0.07),
    ]


def holographic(zf, ytop):
    """EOTech: a battery body on a QD mount, a chamfered hood, one big window (window centre ytop+1.275, zf+1.4)."""
    cy = ytop + 1.275
    ring = [glow(8 + 0.3 * math.cos(a * math.pi / 8), cy + 0.3 * math.sin(a * math.pi / 8), zf + 1.03, 0.045) for a in range(16)]
    return [
        box(7.35, ytop - 0.05, zf + 0.2, 8.65, ytop + 0.12, zf + 2.7, MOUNT),
        box(7.1, ytop - 0.02, zf + 1.7, 7.35, ytop + 0.22, zf + 2.5, MOUNT),
        box(7.03, ytop + 0.04, zf + 1.1, 7.13, ytop + 0.17, zf + 2.45, BOLT),
        box(7.3, ytop + 0.12, zf + 0.1, 8.7, ytop + 0.5, zf + 2.8, EOTECH_BODY),
        box(7.6, ytop + 0.15, zf - 0.1, 8.4, ytop + 0.45, zf + 0.1, RUBBER),
        box(7.55, ytop + 0.18, zf + 2.8, 7.85, ytop + 0.42, zf + 2.9, EOTECH_BUTTON),
        box(8.15, ytop + 0.18, zf + 2.8, 8.45, ytop + 0.42, zf + 2.9, EOTECH_BUTTON),
        # Hood: side walls, roof with chamfered edges, lips framing the front and back openings.
        box(7.15, ytop + 0.5, zf + 0.05, 7.35, ytop + 1.95, zf + 2.75, EOTECH_BODY),
        box(8.65, ytop + 0.5, zf + 0.05, 8.85, ytop + 1.95, zf + 2.75, EOTECH_BODY),
        box(7.3, ytop + 1.95, zf + 0.05, 8.7, ytop + 2.15, zf + 2.75, EOTECH_BODY),
        box(7.12, ytop + 1.92, zf + 0.05, 7.38, ytop + 2.12, zf + 2.75, EOTECH_BODY, rot=("z", 45, (7.25, ytop + 2.02, zf))),
        box(8.62, ytop + 1.92, zf + 0.05, 8.88, ytop + 2.12, zf + 2.75, EOTECH_BODY, rot=("z", -45, (8.75, ytop + 2.02, zf))),
        box(7.35, ytop + 0.5, zf, 8.65, ytop + 0.6, zf + 0.15, EOTECH_BODY),
        box(7.35, ytop + 0.5, zf + 2.65, 8.65, ytop + 0.6, zf + 2.8, EOTECH_BODY),
        glass(7.35, ytop + 0.6, 8.65, ytop + 1.95, zf + 1.0, GLASS_BLUE),
        *ring,
        glow(8, cy, zf + 1.03, 0.06),
    ]


def kobra(zf, ytop):
    """Kobra: the reticle housing down the right side with its dial, post frames, amber glass (window centre ytop+1.05, zf+1.2)."""
    cy = ytop + 1.05
    chevron = [glow(8, cy, zf + 0.18, 0.045)] + [glow(8 + side * i * 0.05, cy - i * 0.05, zf + 0.18, 0.045) for i in range(1, 6) for side in (-1, 1)]
    return [
        box(7.4, ytop - 0.05, zf, 8.6, ytop + 0.3, zf + 2.4, MOUNT),
        box(7.15, ytop, zf + 0.9, 7.4, ytop + 0.25, zf + 1.5, BOLT),
        box(8.45, ytop + 0.3, zf + 0.05, 9.05, ytop + 1.8, zf + 2.35, KOBRA_BODY),
        box(8.9, ytop + 1.62, zf + 0.05, 9.12, ytop + 1.84, zf + 2.35, KOBRA_BODY, rot=("z", -45, (9.01, ytop + 1.73, zf))),
        box(9.05, ytop + 0.75, zf + 0.95, 9.3, ytop + 1.35, zf + 1.55, KOBRA_DIAL),
        box(9.05, ytop + 0.75, zf + 0.95, 9.3, ytop + 1.35, zf + 1.55, KOBRA_DIAL, rot=("x", 45, (9.175, ytop + 1.05, zf + 1.25))),
        box(7.2, ytop + 0.3, zf, 7.45, ytop + 1.8, zf + 0.3, KOBRA_BODY),
        box(7.2, ytop + 0.3, zf + 2.1, 7.45, ytop + 1.8, zf + 2.4, KOBRA_BODY),
        box(7.2, ytop + 1.8, zf, 9.05, ytop + 2.05, zf + 0.3, KOBRA_BODY),
        box(7.2, ytop + 1.8, zf + 2.1, 9.05, ytop + 2.05, zf + 2.4, KOBRA_BODY),
        glass(7.45, ytop + 0.3, 8.45, ytop + 1.8, zf + 0.15, GLASS_AMBER),
        *chevron,
    ]


def vertical_grip(zc, yt):
    """A straight grip on a rail clamp: two crossed boxes make a rounded body, finger grooves on the front, a wider end cap."""
    return [
        box(7.35, yt - 0.45, zc - 1.0, 8.65, yt, zc + 1.0, MOUNT),
        box(7.3, yt - 0.3, zc - 0.7, 7.35, yt - 0.1, zc + 0.7, BOLT),
        box(7.5, yt - 3.8, zc - 0.6, 8.5, yt - 0.45, zc + 0.6, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        box(7.4, yt - 3.7, zc - 0.5, 8.6, yt - 0.5, zc + 0.5, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        *[box(7.55, yt - 1.15 - i * 0.75, zc - 0.68, 8.45, yt - 0.85 - i * 0.75, zc - 0.6, GRIP_CAP) for i in range(4)],
        box(7.42, yt - 4.05, zc - 0.68, 8.58, yt - 3.8, zc + 0.68, GRIP_CAP),
    ]


def angled_grip(zc, yt):
    """An angled foregrip: a long low wedge, deepest at the front, its face sloping up toward the shooter."""
    return [
        box(7.35, yt - 0.4, zc - 1.7, 8.65, yt, zc + 1.5, MOUNT),
        box(7.45, yt - 1.2, zc - 1.6, 8.55, yt - 0.4, zc + 1.3, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        box(7.45, yt - 2.3, zc - 1.6, 8.55, yt - 1.2, zc - 0.7, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        box(7.45, yt - 1.85, zc - 0.7, 8.55, yt - 1.2, zc + 0.15, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        box(7.45, yt - 1.45, zc + 0.15, 8.55, yt - 1.2, zc + 0.8, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        # The sloped face, hinged at its top rear corner.
        box(7.46, yt - 1.2, zc - 0.9, 8.54, yt - 0.9, zc + 1.4, GRIP_BODY, rot=("x", -22.5, (8, yt - 0.9, zc + 1.4))),
        box(7.4, yt - 2.45, zc - 1.65, 8.6, yt - 2.2, zc - 0.65, GRIP_CAP),
    ]


def stubby_grip(zc, yt):
    """A short, fat vertical grip."""
    return [
        box(7.35, yt - 0.45, zc - 0.95, 8.65, yt, zc + 0.95, MOUNT),
        box(7.45, yt - 2.1, zc - 0.7, 8.55, yt - 0.45, zc + 0.7, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        box(7.35, yt - 2.0, zc - 0.6, 8.65, yt - 0.5, zc + 0.6, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE),
        box(7.33, yt - 2.4, zc - 0.8, 8.67, yt - 2.1, zc + 0.8, GRIP_CAP),
    ]


def handstop(zc, yt):
    """A small clamp with a nub at its front for the hand to brace against."""
    return [
        box(7.4, yt - 0.35, zc - 1.0, 8.6, yt, zc + 0.6, MOUNT),
        box(7.48, yt - 1.15, zc - 1.0, 8.52, yt - 0.35, zc - 0.45, GRIP_BODY, east=GRIP_SIDE, west=GRIP_SIDE,
            rot=("x", 22.5, (8, yt - 0.35, zc - 0.7))),
    ]


def glock_switch():
    """Replaces the slide's back plate (the slide's rear face is at z 16, y 9-11.2), selector lever out to the right."""
    return [
        box(7.4, 9.25, 16.0, 8.6, 10.95, 16.4, SWITCH),
        box(8.6, 10.25, 16.05, 9.3, 10.6, 16.35, SWITCH),
    ]


def rot_xyz(rx, ry, rz):
    """JOML rotationXYZ: R = Rx * Ry * Rz."""
    def rx_m(a): c, s = math.cos(a), math.sin(a); return [[1, 0, 0], [0, c, -s], [0, s, c]]
    def ry_m(a): c, s = math.cos(a), math.sin(a); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    def rz_m(a): c, s = math.cos(a), math.sin(a); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]
    def mul(a, b): return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    d = math.pi / 180
    return mul(mul(rx_m(rx * d), ry_m(ry * d)), rz_m(rz * d))


def shifted_display(display, dz):
    """Display transforms that undo shifting every element by dz pixels along z (see the -16..32 element limit)."""
    out = OrderedDict()
    for ctx in CONTEXTS:
        t = display.get(ctx, {})
        rot = t.get("rotation", [0, 0, 0]); tr = t.get("translation", [0, 0, 0]); sc = t.get("scale", [1, 1, 1])
        if dz == 0:
            if ctx in display:
                out[ctx] = t
            continue
        r = rot_xyz(*rot)
        d = [0.0, 0.0, sc[2] * dz]
        rd = [sum(r[i][k] * d[k] for k in range(3)) for i in range(3)]
        out[ctx] = OrderedDict([("rotation", rot), ("translation", [round(tr[i] - rd[i], 4) for i in range(3)]), ("scale", sc)])
    return out


def shift_z(elements, dz):
    for e in elements:
        e["from"][2] = round(e["from"][2] + dz, 4)
        e["to"][2] = round(e["to"][2] + dz, 4)
        if "rotation" in e:
            e["rotation"]["origin"][2] = round(e["rotation"]["origin"][2] + dz, 4)


def write_model(gun, name, elements, display):
    zmin = min(e["from"][2] for e in elements)
    dz = 16.0 if zmin < -16 else 0.0
    shift_z(elements, dz)
    model = OrderedDict([("textures", OrderedDict([("parts", "mymod:item/attachment_parts"), ("particle", "mymod:item/attachment_parts")])),
                         ("elements", elements), ("display", shifted_display(display, dz))])
    os.makedirs(os.path.join(MODELS, "attachment"), exist_ok=True)
    with open(os.path.join(MODELS, "attachment", f"{gun}_{name}.json"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    return f"mymod:item/attachment/{gun}_{name}"


def select(component, cases):
    """cases: (attachment item id, model id) pairs."""
    return OrderedDict([("type", "minecraft:select"), ("property", "minecraft:component"), ("component", component),
                        ("cases", [OrderedDict([("when", item), ("model", OrderedDict([("type", "minecraft:model"), ("model", model)]))])
                                   for item, model in cases]),
                        ("fallback", OrderedDict(type="minecraft:empty"))])


def without_iron_sights(gun, tree):
    """The gun's model tree with every model it uses (each animation frame) swapped for a copy without iron sights."""
    tree = json.loads(json.dumps(tree), object_pairs_hook=OrderedDict)
    def walk(node):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:model" and node["model"].startswith("mymod:item/"):
                name = node["model"][len("mymod:item/"):]
                write_without_iron_sights(gun, name)
                node["model"] = f"mymod:item/no_iron_sights/{name}"
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)
    walk(tree)
    return tree


def write_without_iron_sights(gun, name):
    model = json.load(open(os.path.join(MODELS, f"{name}.json")), object_pairs_hook=OrderedDict)
    removed = set(IRON_SIGHTS[gun])
    model["elements"] = [e for e in model["elements"] if e.get("name") not in removed]
    for e in model["elements"]:
        top = IRON_SIGHT_TRIMS.get(gun, {}).get(e.get("name"))
        if top is not None:
            e["to"][1] = top
    os.makedirs(os.path.join(MODELS, "no_iron_sights"), exist_ok=True)
    with open(os.path.join(MODELS, "no_iron_sights", f"{name}.json"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")


for gun, spec in GUNS.items():
    base = json.load(open(os.path.join(MODELS, f"{gun}.json")), object_pairs_hook=OrderedDict)
    display = base["display"]
    layers = []
    def case(name, elements):
        return (f"mymod:{name}", write_model(gun, name, elements, display))

    def on_slide(component, parts):
        """parts: (attachment name, element builder). On a slide-mounted pistol the layer follows the racking frames."""
        resting = select(component, [case(name, build()) for name, build in parts])
        cover = COVER_HINGE.get(gun)
        if cover:
            hy, hz = cover["hinge"]
            entries = []
            for frame, angle in sorted(cover["angles"].items()):
                cases = [(f"mymod:{name}", write_model(gun, f"{name}_o{frame}", gunkit.rotate(build(), gunkit.rx(angle), (8.0, hy, hz)),
                                                       display)) for name, build in parts]
                entries.append(OrderedDict([("threshold", frame - 0.5), ("model", select(component, cases))]))
            entries.append(OrderedDict([("threshold", max(cover["angles"]) + 0.5), ("model", resting)]))
            return OrderedDict([("type", "minecraft:range_dispatch"), ("property", "mymod:gun_animation"), ("entries", entries),
                                ("fallback", resting)])
        travel = SLIDE_TRAVEL.get(gun)
        if not travel:
            return resting
        entries = []
        for frame, dz in enumerate(travel, start=1):
            cases = []
            for name, build in parts:
                elements = build()
                shift_z(elements, dz)
                cases.append((f"mymod:{name}", write_model(gun, f"{name}_f{frame}", elements, display)))
            entries.append(OrderedDict([("threshold", frame - 0.5), ("model", select(component, cases))]))
        # Frames past 6 are the magazine coming out, with the slide forward.
        entries.append(OrderedDict([("threshold", len(travel) + 0.5), ("model", resting)]))
        return OrderedDict([("type", "minecraft:range_dispatch"), ("property", "mymod:gun_animation"), ("entries", entries),
                            ("fallback", resting)])
    if spec.get("muzzle"):
        z, y = spec["muzzle"]
        layers.append(select("mymod:muzzle_attachment", [
            case("suppressor", suppressor(z, y, spec["sup"])), case("oil_can_suppressor", oil_can(z, y)),
            case("muzzle_brake", muzzle_brake(z, y)), case("flash_hider", flash_hider(z, y))]))
    layers.append(select("mymod:underbarrel_attachment", [
        case("laser_sight", laser(*spec["laser"])), case("green_laser_sight", green_laser(*spec["laser"]))]))
    if spec.get("grip"):
        gz, gy = spec["grip"]
        layers.append(select("mymod:grip_attachment", [
            case("vertical_grip", vertical_grip(gz, gy)), case("angled_grip", angled_grip(gz, gy)),
            case("stubby_grip", stubby_grip(gz, gy)), case("handstop", handstop(gz, gy))]))
    if spec.get("optic"):
        o = spec["optic"]
        layers.append(on_slide("mymod:optic_attachment", [
            ("red_dot_sight", lambda: red_dot(*o)), ("holographic_sight", lambda: holographic(*o)), ("kobra_sight", lambda: kobra(*o))]))
    if spec.get("switch"):
        layers.append(on_slide("mymod:fire_control_attachment", [("glock_switch", glock_switch)]))

    path = os.path.join(ITEMS, f"{gun}.json")
    item = json.load(open(path), object_pairs_hook=OrderedDict)
    gun_model = item["model"]
    if gun_model.get("type") == "minecraft:composite":
        gun_model = gun_model["models"][0]  # already wrapped: rebuild the attachment layers
    if gun_model.get("type") == "minecraft:condition" and gun_model.get("component") == "mymod:optic_attachment":
        gun_model = gun_model["on_false"]  # and the iron-sight switch
    if gun in IRON_SIGHTS:
        gun_model = OrderedDict([("type", "minecraft:condition"), ("property", "minecraft:has_component"),
                                 ("component", "mymod:optic_attachment"),
                                 ("on_true", without_iron_sights(gun, gun_model)), ("on_false", gun_model)])
    item["model"] = OrderedDict([("type", "minecraft:composite"), ("models", [gun_model] + layers)])
    with open(path, "w") as f:
        json.dump(item, f, indent=2)
        f.write("\n")

# ------------------------------------------------------------------------------------------- textures


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


rnd = random.Random(5)


def rgb(h, a=255, jitter=0):
    r, g, b = (h >> 16) & 255, (h >> 8) & 255, h & 255
    d = rnd.randint(-jitter, jitter) if jitter else 0
    c = lambda v: max(0, min(255, v + d))
    return (c(r), c(g), c(b), a)


# Attachment parts atlas: 32x32 texels, two per model UV unit, so each swatch is 4x4 texels.
W = 32
px = [(0, 0, 0, 0)] * (W * W)
def fill(u0, v0, u1, v1, f):
    for y in range(v0 * 2, v1 * 2):
        for x in range(u0 * 2, u1 * 2):
            px[y * W + x] = f(x - u0 * 2, y - v0 * 2)
fill(*SUP_BODY, lambda x, y: rgb(0x2A2C2F if y % 2 == 0 else 0x1F2023, jitter=3))
fill(*SUP_CAP, lambda x, y: rgb(0x0C0C0D if 1 <= x <= 2 and 1 <= y <= 2 else 0x2E3033, jitter=2))
fill(*MOUNT, lambda x, y: rgb(0x45484D, jitter=4))
fill(*LASER_BODY, lambda x, y: rgb(0x2B2D30 if y > 0 else 0x3A3D41, jitter=3))
fill(*LASER_FRONT, lambda x, y: rgb(0xFF3020 if 1 <= x <= 2 and 1 <= y <= 2 else 0x1E1F21))
fill(*DOT_HOUSING, lambda x, y: rgb(0x303236 if y > 0 else 0x404348, jitter=3))
fill(*DOT_EMITTER, lambda x, y: rgb(0xFF2A1E if 1 <= x <= 2 and 1 <= y <= 2 else 0x5A1410))
fill(*SWITCH, lambda x, y: rgb(0x161618 if y > 0 else 0x26272A, jitter=2))
fill(*OILCAN_BODY, lambda x, y: rgb(0xE8E4D8 if y == 1 else 0x2F5A9E if y != 3 else 0x22457A, jitter=4))
fill(*OILCAN_CAP, lambda x, y: rgb(0x9AA0A6 if 1 <= x <= 2 and 1 <= y <= 2 else 0x5C6066, jitter=3))
fill(*BRAKE, lambda x, y: rgb(0x111214 if x in (1, 3) and y in (1, 2) else 0x6A6E74, jitter=4))
fill(*FLASH, lambda x, y: rgb(0x0A0A0B if x % 2 == 1 and y > 0 else 0x26282B, jitter=2))
fill(*GREEN_LASER_BODY, lambda x, y: rgb(0x9A8862 if y > 0 else 0xB09C74, jitter=4))
fill(*GREEN_LASER_FRONT, lambda x, y: rgb(0x40FF50 if 1 <= x <= 2 and 1 <= y <= 2 else 0x2A2620))
fill(*KOBRA_BODY, lambda x, y: rgb(0x2E3328 if y > 0 else 0x3C4234, jitter=3))
fill(*EOTECH_BODY, lambda x, y: rgb(0x1E1F21 if y > 0 else 0x2E3033, jitter=3))
fill(*GLASS_BLUE, lambda x, y: rgb(0x9CC8E8, 60))
fill(*GLASS_AMBER, lambda x, y: rgb(0xE8C27A, 70))
fill(*RETICLE, lambda x, y: rgb(0xFF2A1E))
fill(*BOLT, lambda x, y: rgb(0x8A8F95 if y < 2 else 0x6A6E74, jitter=3))
fill(*RUBBER, lambda x, y: rgb(0x151517 if (x + y) % 2 else 0x1C1C1F))
fill(*OPTIC_BODY, lambda x, y: rgb(0x2A2C30 if y > 0 else 0x3C3F44, jitter=2))
fill(*KOBRA_DIAL, lambda x, y: rgb(0x1C1F18 if x % 2 else 0x2A2E24))
fill(*EOTECH_BUTTON, lambda x, y: rgb(0x4A4D52 if y > 0 else 0x5C6066, jitter=2))
fill(*GRIP_BODY, lambda x, y: rgb(0x1E1F21 if x % 2 else 0x26272A, jitter=2))
fill(*GRIP_SIDE, lambda x, y: rgb(0x151517 if (x + y) % 2 else 0x232427))
fill(*GRIP_CAP, lambda x, y: rgb(0x303236 if y == 0 else 0x2A2B2E, jitter=2))
png(os.path.join(TEX, "item/attachment_parts.png"), W, W, px)


def icon(name, pixels):
    """pixels: list of (x, y, hex) on a 16x16 transparent canvas."""
    canvas = [(0, 0, 0, 0)] * 256
    for x, y, h in pixels:
        canvas[y * 16 + x] = rgb(h)
    png(os.path.join(TEX, f"item/{name}.png"), 16, 16, canvas)


def line_pixels(points, colour):
    return [(x, y, colour) for x, y in points]


# Suppressor: a long black tube on the diagonal, threaded collar at the bottom-left.
sup = []
for i in range(11):
    x, y = 3 + i, 12 - i
    for dx, dy, c in [(0, 0, 0x2E3033), (1, 0, 0x232528), (0, 1, 0x1A1B1D), (-1, 0, 0x3E4146), (0, -1, 0x3E4146), (1, 1, 0x141516)]:
        sup.append((x + dx, y + dy, c))
sup += [(13, 1, 0x0B0B0C), (14, 1, 0x2E3033), (13, 0, 0x3E4146), (14, 2, 0x1A1B1D)]
sup += [(1, 13, 0x6A6E74), (2, 13, 0x55595E), (2, 14, 0x45484D), (1, 14, 0x55595E), (3, 14, 0x45484D), (2, 12, 0x6A6E74)]
icon("suppressor", sup)

# Laser sight: a small dark box with a red beam shooting out of its front.
las = []
for x in range(3, 10):
    for y in range(7, 11):
        las.append((x, y, 0x3A3D41 if y == 7 else 0x2B2D30 if y < 10 else 0x1E1F21))
for x in range(5, 8):
    las.append((x, 6, 0x55595E))
las += [(10, 8, 0xFF3020), (10, 9, 0xC02018), (2, 8, 0x45484D), (2, 9, 0x45484D)]
for x in range(11, 16):
    las.append((x, 8, 0xFF5040 if x % 2 else 0xFF3020))
icon("laser_sight", las)

# Red dot sight: a hood on a rail mount, with the red dot in the window.
rd = []
for x in range(2, 14):
    rd += [(x, 12, 0x45484D), (x, 13, 0x35383C)]
for y in range(4, 12):
    rd += [(3, y, 0x404348), (4, y, 0x303236), (12, y, 0x303236), (11, y, 0x404348)]
for x in range(3, 13):
    rd += [(x, 3, 0x505358), (x, 4, 0x303236)]
for x in range(5, 11):
    for y in range(5, 12):
        rd.append((x, y, 0x6D8FA8 if (x + y) % 5 else 0x86A6BD))
rd += [(7, 8, 0xFF2A1E), (8, 8, 0xFF2A1E), (7, 9, 0xFF2A1E), (8, 9, 0xFF2A1E)]
icon("red_dot_sight", rd)

# Glock switch: a small black back plate with the selector nub.
sw = []
for x in range(4, 12):
    for y in range(4, 12):
        sw.append((x, y, 0x26272A if y == 4 or x == 4 else 0x161618 if (x, y) != (11, 11) else 0x0C0C0D))
for x in range(6, 10):
    for y in range(6, 10):
        sw.append((x, y, 0x0E0E10))
sw += [(10, 6, 0x45484D), (11, 6, 0x45484D), (12, 6, 0x55595E), (12, 7, 0x45484D)]
icon("glock_switch", sw)

# Oil can suppressor: a fat blue filter can with a white band, adapter at the bottom-left.
oc = []
for i in range(8):
    x, y = 5 + i, 10 - i
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 3:
                band = (i == 3 or i == 4)
                c = 0xE8E4D8 if band else (0x3B6CB8 if dx + dy < 0 else 0x2F5A9E if dx + dy < 2 else 0x22457A)
                oc.append((x + dx, y + dy, c))
oc += [(2, 13, 0x6A6E74), (3, 12, 0x5C6066), (3, 13, 0x45484D), (4, 12, 0x45484D), (2, 12, 0x7A7E84)]
icon("oil_can_suppressor", oc)

# Muzzle brake: a short steel block with two ports, seen from the side.
mb = []
for x in range(4, 12):
    for y in range(6, 10):
        port = x in (6, 9) and y in (7, 8)
        mb.append((x, y, 0x111214 if port else 0x8A8F95 if y == 6 else 0x6A6E74 if y < 9 else 0x4E5257))
mb += [(12, 7, 0x2E3033), (12, 8, 0x2E3033), (3, 7, 0x45484D), (3, 8, 0x45484D)]
icon("muzzle_brake", mb)

# Flash hider: a slotted birdcage.
fh = []
for x in range(4, 12):
    for y in range(6, 10):
        slot = x % 2 == 1 and x > 4 and 7 <= y <= 8
        fh.append((x, y, 0x050505 if slot else 0x3A3C40 if y == 6 else 0x26282B))
fh += [(12, 7, 0x0A0A0B), (12, 8, 0x0A0A0B), (3, 7, 0x45484D), (3, 8, 0x45484D)]
icon("flash_hider", fh)

# Green laser: a tan box with a green beam.
gl = []
for x in range(3, 10):
    for y in range(7, 11):
        gl.append((x, y, 0xB09C74 if y == 7 else 0x9A8862 if y < 10 else 0x7A6B4C))
for x in range(5, 8):
    gl.append((x, 6, 0x55595E))
gl += [(10, 8, 0x40FF50), (10, 9, 0x20C030), (2, 8, 0x45484D), (2, 9, 0x45484D)]
for x in range(11, 16):
    gl.append((x, 8, 0x70FF80 if x % 2 else 0x40FF50))
icon("green_laser_sight", gl)

# EOTech: a black box with a big hooded window and the ring reticle.
eo = []
for x in range(2, 14):
    eo += [(x, 12, 0x2E3033), (x, 13, 0x1E1F21), (x, 3, 0x3A3C40), (x, 4, 0x1E1F21)]
for y in range(4, 12):
    eo += [(2, y, 0x2E3033), (3, y, 0x1E1F21), (13, y, 0x1E1F21), (12, y, 0x2E3033)]
for x in range(4, 12):
    for y in range(5, 12):
        eo.append((x, y, 0x6D8FA8 if (x + y) % 5 else 0x86A6BD))
for x, y in [(7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)]:
    eo.append((x, y, 0xFF2A1E))
eo.append((7, 8, 0xFF2A1E))
icon("holographic_sight", eo)

# Kobra: olive frame with the reticle box on the right and a chevron in the window.
kb = []
for x in range(2, 14):
    kb += [(x, 12, 0x45484D), (x, 13, 0x35383C), (x, 3, 0x3C4234)]
for y in range(4, 12):
    kb += [(3, y, 0x3C4234), (11, y, 0x2E3328), (12, y, 0x3C4234), (13, y, 0x2E3328)]
for x in range(4, 11):
    for y in range(4, 12):
        kb.append((x, y, 0x7E9A8C if (x + y) % 5 else 0x92AE9F))
kb += [(7, 6, 0xFF2A1E), (6, 7, 0xFF2A1E), (8, 7, 0xFF2A1E), (5, 8, 0xFF2A1E), (9, 8, 0xFF2A1E)]
icon("kobra_sight", kb)

# Grips: black polymer shapes hanging from a rail clamp along the top.
def grip_icon(name, body):
    pixels = []
    for x in range(3, 13):
        pixels += [(x, 3, 0x6A6E74), (x, 4, 0x45484D)]
    for x, y in body:
        edge = (x - 1, y) not in body
        pixels.append((x, y, 0x3A3C40 if edge else 0x232427 if (x + y) % 2 else 0x1A1B1D))
    icon(name, pixels)
grip_icon("vertical_grip", {(x, y) for x in range(6, 10) for y in range(5, 14)} | {(x, 14) for x in range(5, 11)})
grip_icon("angled_grip", {(x, y) for x in range(3, 13) for y in range(5, 7)} | {(x, y) for y in range(7, 12) for x in range(3, 12 - (y - 7) * 2)})
grip_icon("stubby_grip", {(x, y) for x in range(5, 11) for y in range(5, 10)} | {(x, 10) for x in range(4, 12)})
grip_icon("handstop", {(x, 5) for x in range(4, 12)} | {(x, y) for y in range(6, 9) for x in range(4 + (y - 6), 7 + (y - 6))})

# Laser dot particles: soft red and green dots.
def dot_texture(name, core, mid, edge):
    dot = []
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            dot.append(rgb(core) if d < 1.6 else rgb(mid) if d < 2.6 else rgb(edge, 200) if d < 3.4 else (0, 0, 0, 0))
    png(os.path.join(TEX, f"particle/{name}.png"), 8, 8, dot)
dot_texture("laser_dot", 0xFF5A48, 0xFF2A1A, 0xD01810)
dot_texture("green_laser_dot", 0x9AFFA0, 0x40FF50, 0x18C028)

# Item models for the attachment items.
for name in ["suppressor", "laser_sight", "red_dot_sight", "glock_switch", "oil_can_suppressor", "muzzle_brake", "flash_hider",
             "green_laser_sight", "holographic_sight", "kobra_sight", "vertical_grip", "angled_grip", "stubby_grip", "handstop"]:
    with open(os.path.join(MODELS, f"{name}.json"), "w") as f:
        json.dump(OrderedDict([("parent", "minecraft:item/generated"), ("textures", OrderedDict(layer0=f"mymod:item/{name}"))]), f, indent=2)
        f.write("\n")
    with open(os.path.join(ITEMS, f"{name}.json"), "w") as f:
        json.dump(OrderedDict(model=OrderedDict([("type", "minecraft:model"), ("model", f"mymod:item/{name}")])), f, indent=2)
        f.write("\n")

os.makedirs(os.path.join(ASSETS, "particles"), exist_ok=True)
for name in ["laser_dot", "green_laser_dot"]:
    with open(os.path.join(ASSETS, f"particles/{name}.json"), "w") as f:
        json.dump({"textures": [f"mymod:{name}"]}, f, indent=2)
        f.write("\n")
print("done")
