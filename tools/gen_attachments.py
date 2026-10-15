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
    "m249":         dict(muzzle=(-15.9, 9.0), sup=7.5, laser=(-2.6, 6.1), optic=(9.6, 11.4), grip=(3.0, 6.1)),
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
    "m249": ["front_sight_post", "rear_sight_ring", "rear_sight_ear"],
}
# The AK's rear sight block also carries the gas tube, so it stays, cut down to just under the dust cover's top.
IRON_SIGHT_TRIMS = {"akm": {"rear_sight_base": 10.18}}

# The Gunsmith set (tools/gen_gunsmith.py): its own guns take only Gunsmith attachments (own=True). GUNSMITH lists which of
# those each gun takes, and where they mount (pixels, as above), mirroring the fit lists in gunsmith/Gunsmith.java:
#   scope2 / scope4: on the optic mount; flashlight: on the laser's mount; bipod: (front z, underside y);
#   sling: front and rear swivels ((z, y), (z, y)); bayonet: (ring z, ring centre y, ring radius, blade top y, handle length);
#   choke: (muzzle z, bore y, barrel radius); compensator: on the muzzle mount.
GUNS.update({
    "lever_action": dict(own=True, muzzle=None, laser=(-11.2, 8.5), optic=(0.6, 11.0)),
    "harpoon_gun": dict(own=True, muzzle=None, laser=(-6.9, 7.07), optic=None),
    "nail_gun": dict(own=True, muzzle=None, laser=(-9.0, 8.2), optic=None),
})
IRON_SIGHTS["lever_action"] = ["rear_sight"]
GUNSMITH = {
    "m4": dict(scope2=True, scope4=True, flashlight=True, bipod=(-6.5, 6.1), sling=((-2.0, 6.1), (21.5, 5.0)),
               bayonet=(-11.4, 8.0, 0.55, 7.3, 2.6), compensator=True),
    "akm": dict(scope2=True, scope4=True, flashlight=True, bipod=(-1.0, 6.6), sling=((-4.0, 7.8), (23.0, 5.6)),
                bayonet=(-11.5, 8.2, 0.55, 7.5, 1.9), compensator=True),
    "galil": dict(scope2=True, scope4=True, flashlight=True, bipod=(-2.0, 6.6), sling=((-4.0, 5.9), (24.0, 4.4)),
                  bayonet=(-12.9, 8.2, 0.55, 7.5, 1.9), compensator=True),
    "awp": dict(flashlight=True, bipod=(-6.5, 6.5), sling=((-3.0, 6.5), (25.0, 3.4)), compensator=True),
    "m249": dict(scope2=True, flashlight=True, sling=((-1.0, 6.1), (25.0, 4.5)), compensator=True),
    "mp5": dict(flashlight=True, sling=((-5.0, 6.5), (21.0, 3.4)), compensator=True),
    "glock": dict(flashlight=True, compensator=True),
    "m1911": dict(flashlight=True, compensator=True),
    "mac10": dict(flashlight=True, compensator=True),
    "revolver_357": dict(flashlight=True),
    "remington_870": dict(scope2=True, flashlight=True, sling=((-13.8, 7.08), (26.4, 3.35)), bayonet=(-14.15, 7.7, 0.78, 7.6, 0.0),
                          choke=(-15.9, 9.0, 0.55)),
    "spas12": dict(scope2=True, flashlight=True, sling=((-14.6, 6.3), (26.0, 3.22)), bayonet=(-15.1, 7.6, 0.86, 7.5, 0.0),
                   choke=(-15.9, 9.2, 0.62)),
    "lever_action": dict(scope2=True, scope4=True, flashlight=True, bipod=(-5.6, 7.95), sling=((-4.0, 7.95), (18.0, 3.7)),
                         bayonet=(-13.95, 10.0, 0.72, 9.2, 0.0)),
    "harpoon_gun": dict(flashlight=True, sling=((-8.0, 7.15), (9.2, 6.9))),
    "nail_gun": dict(flashlight=True),
}

# Pistols whose optic (and switch) sit on the slide: how far back the slide is in each racking frame
# (the mymod:gun_animation frames 1-6), so those parts ride along with it.
SLIDE_TRAVEL = {"glock": [0.417, 0.833, 1.25, 1.667, 2.083, 2.5], "m1911": [0.417, 0.833, 1.25, 1.667, 2.083, 2.5]}

# Guns whose optic rail is on a hinged cover: the hinge (y, z) and how far the cover is turned about x (degrees) in each
# mymod:gun_animation frame, so a fitted optic swings up with it (the M249's feed cover, hinged at its front, its back end
# rising, frames 7-16).
COVER_HINGE = {"m249": dict(hinge=(10.6, 7.3), angles={**{f: -12.5 * (f - 6) for f in range(7, 13)}, **{f: -75.0 for f in range(13, 17)}})}

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
    """cases: (attachment item id, model id or model node) pairs."""
    return OrderedDict([("type", "minecraft:select"), ("property", "minecraft:component"), ("component", component),
                        ("cases", [OrderedDict([("when", item), ("model", model if isinstance(model, dict)
                                                                 else OrderedDict([("type", "minecraft:model"), ("model", model)]))])
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



# ------------------------------------------------------------------------------------------------ Gunsmith attachments

# Their swatches, in rows 4-6 of the atlas: flat, deliberate tones, no jitter.
SCOPE_BODY, SCOPE_RING, RETICLE_DARK, FL_BODY = cell(0, 4), cell(1, 4), cell(2, 4), cell(3, 4)
FL_LENS, FL_HEAD, BIPOD_LEG, SLING_STRAP = cell(4, 4), cell(5, 4), cell(6, 4), cell(7, 4)
SLING_HW, BLADE, BLADE_GRIP, CHOKE = cell(0, 5), cell(1, 5), cell(2, 5), cell(3, 5)
COMP_BODY, COMP_PORT, SCOPE_TOP, BLADE_SPINE = cell(4, 5), cell(5, 5), cell(6, 5), cell(7, 5)
# Where each scope's eyepiece window is relative to its mount (GunAim mirrors these): up, and back from the mount's front.
SCOPE2_WINDOW = (1.25, 7.3)
SCOPE4_WINDOW = (1.35, 9.4)


def oct_tube(zf, zb, cy, r, swatch, cap=None, caps=(True, True)):
    """A solid octagonal bar along z centred on (8, cy): four bands, ends staggered a hair so the caps never fight."""
    a = 2 * r * math.tan(math.pi / 8)
    out = []
    for k, (angle, w, h) in enumerate([(0, r, a / 2), (0, a / 2, r), (45, r, a / 2), (-45, r, a / 2)]):
        d = 0.01 * k
        faces = {}
        if cap:
            faces["north"] = faces["south"] = cap
        only = tuple(f for f in ("north", "east", "south", "west", "up", "down")
                     if not (f == "north" and not caps[0]) and not (f == "south" and not caps[1]))
        out.append(box(8 - w, cy - h, zf + d, 8 + w, cy + h, zb - d, swatch, only=only,
                       rot=("z", angle, (8, cy, zf)) if angle else None, **faces))
    return out


def hunting_scope(zf, ytop, power):
    """A hunting scope in two steel rings on a clamp base: a matte tube, a flared objective bell (bigger on the 4x), windage
    and elevation turrets, a ribbed ocular bell with a rubber eyecup, blue-tinted glass front and back, and a black duplex
    reticle on the rear glass (the window GunAim lines up with the eye)."""
    long4 = power == 4
    cy = ytop + (SCOPE4_WINDOW if long4 else SCOPE2_WINDOW)[0]
    rear = zf + (SCOPE4_WINDOW if long4 else SCOPE2_WINDOW)[1]
    tube_f, tube_b = zf - (1.0 if long4 else 0.4), rear - 1.6
    bell_r = 0.95 if long4 else 0.75
    out = []
    out += oct_tube(tube_f, tube_b, cy, 0.5, SCOPE_BODY, caps=(False, False))
    out += oct_tube(tube_f - (1.7 if long4 else 1.1), tube_f, cy, bell_r, SCOPE_BODY, cap=SCOPE_RING, caps=(True, False))
    out += oct_tube(tube_f - 0.5, tube_f + 0.02, cy, bell_r - 0.12 if long4 else 0.62, SCOPE_BODY, caps=(False, False))
    out += oct_tube(tube_b, rear - 0.3, cy, 0.68, SCOPE_BODY, cap=SCOPE_RING, caps=(True, False))
    out += oct_tube(rear - 0.3, rear, cy, 0.72, RUBBER, cap=RUBBER, caps=(False, True))
    # Turrets: elevation on top, windage on the right, a ring of grip on each.
    tz = (tube_f + tube_b) / 2 - 0.3
    out.append(box(7.68, cy + 0.45, tz, 8.32, cy + 1.0, tz + 0.64, SCOPE_BODY, up=SCOPE_TOP))
    out.append(box(7.62, cy + 0.85, tz - 0.04, 8.38, cy + 0.95, tz + 0.68, SCOPE_RING, up=SCOPE_TOP))
    out.append(box(8.45, cy - 0.32, tz, 9.0, cy + 0.32, tz + 0.64, SCOPE_BODY, east=SCOPE_TOP))
    # Rings and their clamp base on the rail.
    for rz in (tube_f + 0.5, tube_b - 1.0):
        out.append(box(7.35, ytop - 0.05, rz - 0.35, 8.65, ytop + 0.3, rz + 0.35, MOUNT))
        out.append(box(7.75, ytop + 0.3, rz - 0.25, 8.25, cy - 0.45, rz + 0.25, SCOPE_RING))
        out += oct_tube(rz - 0.3, rz + 0.3, cy, 0.62, SCOPE_RING, caps=(True, True))
        out.append(box(8.62, ytop + 0.02, rz - 0.15, 8.8, ytop + 0.24, rz + 0.15, BOLT))
    # Glass, front and back, and the duplex reticle just in front of the rear glass.
    gr = 0.58 if not long4 else 0.6
    out.append(glass(8 - gr, cy - gr, 8 + gr, cy + gr, rear - 0.12, GLASS_BLUE))
    fr = bell_r - 0.1
    out.append(glass(8 - fr, cy - fr, 8 + fr, cy + fr, tube_f - (1.6 if long4 else 1.0), GLASS_BLUE))
    t = 0.035
    # Each line on its own plane, a hair apart, so where they cross they never fight.
    for k, (x0, x1, y0, y1) in enumerate(((8 - gr, 8 + gr, cy - t, cy + t), (8 - t, 8 + t, cy - gr, cy + gr),
                                          (8 - gr, 8 - 0.25, cy - 2 * t, cy + 2 * t), (8 + 0.25, 8 + gr, cy - 2 * t, cy + 2 * t),
                                          (8 - 2 * t, 8 + 2 * t, cy - gr, cy - 0.25), (8 - 2 * t, 8 + 2 * t, cy + 0.25, cy + gr))):
        z = rear - 0.16 - 0.004 * k
        out.append(box(x0, y0, z, x1, y1, z + 0.01, RETICLE_DARK, only=("south",)))
    return out


def flashlight(zf, ytop):
    """A weapon light on a rail clamp under the barrel: an anodised body, a knurled bell head with a glowing lens, a tail
    switch, and the clamp with its thumb screw."""
    cy = ytop - 0.75
    out = []
    out.append(box(7.6, ytop - 0.3, zf + 0.6, 8.4, ytop + 0.02, zf + 2.4, MOUNT))
    out.append(box(8.4, ytop - 0.25, zf + 1.2, 8.62, ytop - 0.05, zf + 1.8, BOLT))
    for k, (angle, w, h) in enumerate([(0, 0.5, 0.21), (0, 0.21, 0.5), (45, 0.5, 0.21), (-45, 0.5, 0.21)]):
        out.append(box(8 - w, cy - h, zf + 0.9 + 0.01 * k, 8 + w, cy + h, zf + 3.3 - 0.01 * k, FL_BODY,
                       rot=("z", angle, (8, cy, zf)) if angle else None, only=("east", "south", "west", "up", "down")))
    for k, (angle, w, h) in enumerate([(0, 0.66, 0.27), (0, 0.27, 0.66), (45, 0.66, 0.27), (-45, 0.66, 0.27)]):
        out.append(box(8 - w, cy - h, zf + 0.01 * k, 8 + w, cy + h, zf + 0.92 - 0.01 * k, FL_HEAD,
                       rot=("z", angle, (8, cy, zf)) if angle else None, only=("east", "south", "west", "up", "down")))
    out.append(box(7.42, cy - 0.58, zf - 0.03, 8.58, cy + 0.58, zf - 0.02, FL_LENS, only=("north",), emit=True))
    out.append(box(7.7, cy - 0.3, zf + 3.28, 8.3, cy + 0.3, zf + 3.55, RUBBER))
    return out


def bipod(zf, ytop, deployed):
    """A folding bipod on a rail clamp: two steel legs with telescoping lower halves and rubber feet. Folded they lie forward
    along the barrel; deployed (braced) they swing down and splay."""
    out = [box(7.32, ytop - 0.45, zf - 0.2, 8.68, ytop + 0.02, zf + 1.4, MOUNT), box(8.68, ytop - 0.35, zf + 0.4, 8.88, ytop - 0.1, zf + 0.8, BOLT)]
    pivot = (8, ytop - 0.3, zf + 0.2)
    for side in (-1, 1):
        x = 8 + side * 0.48
        leg = [box(x - 0.17, ytop - 4.2, zf + 0.05, x + 0.17, ytop - 0.3, zf + 0.39, BIPOD_LEG),
               box(x - 0.12, ytop - 5.6, zf + 0.1, x + 0.12, ytop - 4.15, zf + 0.34, BIPOD_LEG),
               box(x - 0.22, ytop - 5.9, zf - 0.02, x + 0.22, ytop - 5.55, zf + 0.46, RUBBER)]
        if deployed:
            m = gunkit.mul(gunkit.rz(side * 14), gunkit.rx(10))
        else:
            m = gunkit.rx(-86 + side * 2)
        out += gunkit.rotate(leg, m, (x, pivot[1], pivot[2]))
    return out


def sling(front, rear):
    """A webbing sling hanging between a swivel under the fore-end and one under the stock: three straps sagging between them,
    and the two swivel loops."""
    (fz, fy), (bz, by) = front, rear
    sag = 2.2 + 0.04 * (bz - fz)
    low = (fz + (bz - fz) * 0.42, min(fy, by) - sag)
    pts = [(fz, fy - 0.55), (fz + (low[0] - fz) * 0.55, (fy - 0.55 + low[1]) / 2 - 0.5), low,
           ((low[0] + bz) / 2, (low[1] + by - 0.45) / 2 - 0.4), (bz, by - 0.45)]
    out = []
    for k, ((z0, y0), (z1, y1)) in enumerate(zip(pts, pts[1:])):
        length = math.hypot(z1 - z0, y1 - y0)
        angle = math.degrees(math.atan2(y1 - y0, z1 - z0))
        d = 0.01 * (k % 2)
        strap = box(7.68 + d, y0 - 0.06, z0, 8.32 - d, y0 + 0.06, z0 + length + 0.05, SLING_STRAP)
        out += gunkit.rotate([strap], gunkit.rx(-angle), (8, y0, z0))
    for z, y in ((fz, fy), (bz, by)):
        out.append(box(7.62, y - 0.6, z - 0.32, 8.38, y - 0.45, z + 0.32, SLING_HW))
        out.append(box(7.62, y - 0.45, z - 0.32, 8.38, y + 0.02, z - 0.17, SLING_HW))
        out.append(box(7.62, y - 0.45, z + 0.17, 8.38, y + 0.02, z + 0.32, SLING_HW))
    return out


def bayonet(ring_z, ring_y, ring_r, blade_top, handle):
    """A bayonet: a socket ring round the barrel (or the magazine tube) at ring_z, a neck down to the blade, a polished blade
    running forward under the muzzle with a darker spine, and on a knife bayonet a ribbed handle reaching back to the lug."""
    out = oct_tube(ring_z, ring_z + 0.7, ring_y, ring_r, SLING_HW, caps=(True, True))
    out.append(box(7.75, blade_top - 0.1, ring_z + 0.1, 8.25, ring_y - ring_r + 0.05, ring_z + 0.6, SLING_HW))
    out.append(box(7.62, blade_top - 0.3, ring_z - 0.25, 8.38, blade_top + 0.12, ring_z + 0.665, BLADE_GRIP))
    out.append(box(7.88, blade_top - 0.95, ring_z - 8.6, 8.12, blade_top, ring_z - 0.25, BLADE, up=BLADE_SPINE))
    tip = box(7.89, blade_top - 0.95, ring_z - 9.6, 8.11, blade_top - 0.05, ring_z - 8.6, BLADE, up=BLADE_SPINE)
    out += gunkit.rotate([tip], gunkit.rx(-22), (8, blade_top - 0.95, ring_z - 8.6))
    if handle > 0:
        out.append(box(7.67, blade_top - 0.85, ring_z + 0.75, 8.33, blade_top + 0.05, ring_z + 0.75 + handle, BLADE_GRIP))
        out.append(box(7.65, blade_top - 0.9, ring_z + 0.75 + handle, 8.35, blade_top + 0.1, ring_z + 1.0 + handle, SLING_HW))
    return out


def choke(z, y, r):
    """An extended choke tube threaded into the muzzle: a knurled band and a plain front with its narrowed bore."""
    out = oct_tube(z - 1.6, z + 0.02, y, r + 0.08, CHOKE, cap=SUP_CAP, caps=(True, False))
    out += oct_tube(z - 0.7, z - 0.3, y, r + 0.16, SLING_HW, caps=(True, True))
    return out


def compensator(z, y):
    """A compensator: a short black body with three ports cut in its top and a narrowed front."""
    out = oct_tube(z - 2.4, z, y, 0.62, COMP_BODY, cap=SUP_CAP, caps=(True, False))
    for k in range(3):
        zz = z - 2.0 + 0.6 * k
        out.append(box(7.75, y + 0.62, zz, 8.25, y + 0.64, zz + 0.32, COMP_PORT, only=("up",)))
    return out


def gunsmith_layers(gun, spec, fits, case, on_slide):
    """The Gunsmith attachments' layers for one gun: each slot its own select (fitted items in a slot never overlap, so a
    second select on the same component only ever adds the one that's fitted)."""
    layers = []
    muzzle = []
    if fits.get("compensator") and spec.get("muzzle"):
        muzzle.append(case("compensator", compensator(*spec["muzzle"])))
    if fits.get("bayonet"):
        muzzle.append(case("bayonet", bayonet(*fits["bayonet"])))
    if fits.get("choke"):
        muzzle.append(case("shotgun_choke", choke(*fits["choke"])))
    if muzzle:
        layers.append(select("mymod:muzzle_attachment", muzzle))
    under = []
    if fits.get("flashlight"):
        under.append(case("flashlight", flashlight(*spec["laser"])))
    if fits.get("bipod"):
        folded = case("bipod", bipod(*fits["bipod"], False))
        deployed = case("bipod_deployed", bipod(*fits["bipod"], True))
        # Swung down while the holder is braced on it (mymod:bipod, the M249's, eases 0-3 as they crouch and stand).
        under.append((folded[0], OrderedDict([("type", "minecraft:range_dispatch"), ("property", "mymod:bipod"),
                                              ("entries", [OrderedDict([("threshold", 1.5), ("model", OrderedDict(
                                                  [("type", "minecraft:model"), ("model", deployed[1])]))])]),
                                              ("fallback", OrderedDict([("type", "minecraft:model"), ("model", folded[1])]))])))
    if under:
        layers.append(select("mymod:underbarrel_attachment", under))
    if fits.get("sling"):
        layers.append(select("mymod:grip_attachment", [case("rifle_sling", sling(*fits["sling"]))]))
    optics = []
    if fits.get("scope2") and spec.get("optic"):
        optics.append(("hunting_scope_2x", lambda: hunting_scope(*spec["optic"], 2)))
    if fits.get("scope4") and spec.get("optic"):
        optics.append(("hunting_scope_4x", lambda: hunting_scope(*spec["optic"], 4)))
    if optics:
        layers.append(on_slide("mymod:optic_attachment", optics))
    return layers


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
    own = spec.get("own", False)
    if spec.get("muzzle"):
        z, y = spec["muzzle"]
        layers.append(select("mymod:muzzle_attachment", [
            case("suppressor", suppressor(z, y, spec["sup"])), case("oil_can_suppressor", oil_can(z, y)),
            case("muzzle_brake", muzzle_brake(z, y)), case("flash_hider", flash_hider(z, y))]))
    if not own:
        layers.append(select("mymod:underbarrel_attachment", [
            case("laser_sight", laser(*spec["laser"])), case("green_laser_sight", green_laser(*spec["laser"]))]))
    if spec.get("grip"):
        gz, gy = spec["grip"]
        layers.append(select("mymod:grip_attachment", [
            case("vertical_grip", vertical_grip(gz, gy)), case("angled_grip", angled_grip(gz, gy)),
            case("stubby_grip", stubby_grip(gz, gy)), case("handstop", handstop(gz, gy))]))
    if spec.get("optic") and not own:
        o = spec["optic"]
        layers.append(on_slide("mymod:optic_attachment", [
            ("red_dot_sight", lambda: red_dot(*o)), ("holographic_sight", lambda: holographic(*o)), ("kobra_sight", lambda: kobra(*o))]))
    if spec.get("switch"):
        layers.append(on_slide("mymod:fire_control_attachment", [("glock_switch", glock_switch)]))
    layers += gunsmith_layers(gun, spec, GUNSMITH.get(gun, {}), case, on_slide)

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

# The Gunsmith attachments' swatches: flat tones with a highlight row where a part has a top edge.
def flat_fill(swatch, top, body, bottom=None):
    fill(*swatch, lambda x, y: rgb(top if y == 0 else bottom if bottom is not None and y == 3 else body))
flat_fill(SCOPE_BODY, 0x3C3F44, 0x222428, 0x1A1B1E)
flat_fill(SCOPE_RING, 0x8A9098, 0x5E646C, 0x464B52)
fill(*RETICLE_DARK, lambda x, y: rgb(0x0C0C0E))
flat_fill(FL_BODY, 0x50534A, 0x373A32, 0x2C2E27)
fill(*FL_LENS, lambda x, y: rgb(0xFFF8E4 if 1 <= x <= 2 and 1 <= y <= 2 else 0xF2E2B4))
fill(*FL_HEAD, lambda x, y: rgb(0x2A2C30 if (x + y) % 2 else 0x41444A))
flat_fill(BIPOD_LEG, 0xB0B6BE, 0x80868E, 0x60666E)
fill(*SLING_STRAP, lambda x, y: rgb(0x4E5530 if y in (0, 3) else 0x6E7744 if x % 2 == 0 else 0x666F3E))
flat_fill(SLING_HW, 0xA6ACB4, 0x7C828A, 0x5C6168)
flat_fill(BLADE, 0xF4F7FA, 0xC6CCD2, 0x9AA0A8)
fill(*BLADE_GRIP, lambda x, y: rgb(0x2E3034 if y % 2 == 0 else 0x1C1D20))
fill(*CHOKE, lambda x, y: rgb(0x8E949C if (x + y) % 2 else 0x5E636A))
flat_fill(COMP_BODY, 0x44474C, 0x26282B, 0x1C1D20)
fill(*COMP_PORT, lambda x, y: rgb(0x050505))
fill(*SCOPE_TOP, lambda x, y: rgb(0x4A4E54 if x in (0, 2) else 0x2A2C30))
flat_fill(BLADE_SPINE, 0x7E848C, 0x6A7078, 0x5A6068)
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


# The Gunsmith attachments' icons: drawn row by row, one palette.
GS_PAL = {"k": 0x16171A, "K": 0x2A2C30, "g": 0x3C3F44, "s": 0x5E646C, "S": 0x8A9098, "w": 0xC6CCD2, "W": 0xF4F7FA,
          "b": 0x9CC8E8, "B": 0x6D8FA8, "y": 0xFFF4D8, "Y": 0xF2E2B4, "o": 0x373A32, "O": 0x50534A, "l": 0x6E7744,
          "L": 0x4E5530, "r": 0x2E3034, "c": 0x8E949C, "C": 0x5E636A, "m": 0x45484D, "M": 0x6A6E74}


def gs_icon(name, rows):
    pixels = []
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, row)
        for x, ch in enumerate(row):
            if ch != ".":
                pixels.append((x, y, GS_PAL[ch]))
    icon(name, pixels)


gs_icon("hunting_scope_2x", [
    "................", "................", "................", "................", ".......gg.......",
    "..ggKKKKKKKKgg..", ".bgSsssssssssgK.", ".bgsssssssssgkK.", ".BgKKKKKKKKKKgk.", "..gg.mm..mm.gg..",
    "....MmmM.MmmM...", "....mmmmmmmmm...", "................", "................", "................",
    "................"])
gs_icon("hunting_scope_4x", [
    "................", "................", "................", "......gg........", ".gg...gg........",
    "bgSgKKKKKKKKKgg.", "bgsssssssssssgKk", "bgsssssssssssgKk", "BgKgKKKKKKKKKgkk", ".gg..mm...mm.gg.",
    "....MmmM..MmmM..", "....mmmmmmmmmm..", "................", "................", "................",
    "................"])
gs_icon("flashlight", [
    "................", "................", "................", "................", "................",
    "......MMMMM.....", "..KKKKmmmmm.....", ".yOOOOOOOOOOk...", ".yOooooooooOk...", ".YoooooooooOk...",
    "..KKKKooooooo...", "................", "................", "................", "................",
    "................"])
gs_icon("bipod", [
    "................", "................", "....MMMMMM......", "....mmmmmm......", ".....sS.Ss......",
    ".....sS.Ss......", "....sS...Ss.....", "....sS...Ss.....", "...sS.....Ss....", "...sS.....Ss....",
    "..sS.......Ss...", "..sS.......Ss...", ".kkK.......Kkk..", ".kk.........kk..", "................",
    "................"])
gs_icon("rifle_sling", [
    "................", ".SS.............", ".S.S............", ".SllL...........", "..lLlL..........",
    "...lLlL.........", "....lLlL........", ".....lLlL.......", "......lLlL......", ".......lLlL.....",
    "........lLlL....", ".........lLlL...", "..........lLlS..", "...........S.S..", "............SS..",
    "................"])
gs_icon("bayonet", [
    "...............W", "..............Ww", ".............Wws", "............Wws.", "...........Wws..",
    "..........Wws...", ".........Wws....", "........Wws.....", ".......Wws......", "......MSs.......",
    ".....MMSM.......", "....rrM.........", "...rrr..........", "..rrr...........", ".rrr............",
    "................"])
gs_icon("shotgun_choke", [
    "................", "................", "................", "................", "................",
    "...KcCcCcCcSSSk.", "..kKCcCcCcCMMMk.", "..kKcCcCcCcMMMk.", "...KCcCcCcCSSSk.", "................",
    "................", "................", "................", "................", "................",
    "................"])
gs_icon("compensator", [
    "................", "................", "................", "................", "................",
    "....gkgkgkgg....", "...gKKKKKKKKgk..", "...KKKKKKKKKKkk.", "...KKKKKKKKKKkk.", "...gKKKKKKKKgk..",
    "................", "................", "................", "................", "................",
    "................"])

# Item models for the attachment items.
for name in ["hunting_scope_2x", "hunting_scope_4x", "flashlight", "bipod", "rifle_sling", "bayonet", "shotgun_choke", "compensator",
             "suppressor", "laser_sight", "red_dot_sight", "glock_switch", "oil_can_suppressor", "muzzle_brake", "flash_hider",
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
