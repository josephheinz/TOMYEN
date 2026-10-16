"""Builds the Fish Gun and the Leaf Blower / Leaf Blower-Vac assets: one shared texture atlas, the held item models (the
blower-vac with its mode lever up for blow and down for suck), the Vacuum Kit's sprite, the client rigs (display transforms
and hand holds, so GunDisplay and GunHands stay on the models), the engine sounds, and the lang and sounds.json entries.

The Fish Gun is a pneumatic fish cannon: a wide copper barrel with a brass bell muzzle and a fish decal down each side, a
loading hopper with a hinged lid on top, a brass receiver with a pressure gauge tilted back at the shooter, a red compressed
air tank slung under the barrel with its fill valve and handwheel, a walnut pistol grip and raked foregrip, and a skeleton
stock of brass tubes ending in a walnut butt.

The Leaf Blower is a two-stroke backpack-less blower: an orange fan volute with a vented cover, the finned engine behind it
with the recoil starter and its T-handle on the left, the muffler and its perforated guard on the right, a fuel tank
underneath, an air filter at the back, a loop handle over the top with the throttle trigger, and a two-piece blower tube
with a rubber support grip and a flat nozzle. The Blower-Vac swaps the tube for a ribbed intake with a round, grilled
mouth, hangs a canvas collection bag underneath and adds the yellow blow/suck lever.

Art follows gunsmith_kit.py: clean ramps, placed detail, no noise. Every model goes through the world-space coplanar check.

Model space is in pixels: the bore (or tube) runs along z at x = 8 with the muzzle toward -z, +x is the right, +y up.

Usage: python3 tools/gen_fishgun_blower.py src/main/resources/assets/tomyen [--no-sounds]"""
import json, math, os, random, sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import placed, rotate, move, rx, ry, rz, write, model, ref, display, sprite, png, mix
from gunsmith_kit import (Atlas, Parts, RAMPS, WOODS, WRAPS, BLACK, CLEAR, paint_materials, bore_picture, screw_picture,
                          check_all, gun_displays, fp_point, FP_RIFLE_GRIP)

ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
SOUNDS = "--no-sounds" not in sys.argv
ASSETS = ARGS[0]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CLIENT = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client")

# ============================================================================================================== atlas

RAMPS_HERE = dict(RAMPS)
RAMPS_HERE.update({
    # Painted pressure-vessel red, a touch deeper than the flare's paper hull.
    "tank": (0xF26A5A, 0xD23E30, 0xB02E26, 0x8A221E, 0x601816),
    # Light grey moulded plastic for the blower tubes.
    "plastic": (0xE8EAEC, 0xC9CDD2, 0xACB2B8, 0x8E949B, 0x6C7279),
    # The fuel tank: translucent-looking milky white with an amber tint.
    "milk": (0xFBF6EA, 0xEDE4CC, 0xD8CCAE, 0xBDAE8C, 0x968866),
    # Burnished copper for the barrel: vanilla's copper block reads pink at this size, so warmer and more orange.
    "copper": (0xF8B884, 0xE4925A, 0xC4703E, 0x9A532C, 0x6C381E),
    # Waxed canvas for the collection bag.
    "canvas": (0xC9B98A, 0xAE9C6C, 0x948256, 0x786842, 0x58492C),
})
K = Atlas("wacky", "item/wacky_gear", seed=7419, size=128)
paint_materials(K, ramps=RAMPS_HERE, woods={"walnut": WOODS["walnut"]}, wraps={k: WRAPS[k] for k in ("string", "tape_black")})
for ramp in ("brass", "copper", "pipe", "black"):
    K.add(f"screw_{ramp}", 4, 4, screw_picture(ramp), picture=True)
K.add("bore_bell", 16, 16, bore_picture("brass", 5.6), picture=True)


def gauge_picture(i, j):
    """A pressure gauge's face: a cream dial with a green, amber and red arc round its top, tick marks, the needle
    standing well into the green (it's always charged) and a brass hub."""
    hi, lt, md, lo, dk = RAMPS["brass"]
    x, y = i - 7.5, j - 7.5
    d = math.hypot(x, y)
    if d > 7.9:
        return CLEAR
    if d > 6.9:
        return lt if y < 0 else md
    a = math.degrees(math.atan2(y, x))       # 0 = right, -90 = up
    if 4.6 < d < 6.0 and -200 < a - 360 * (a > 90) < 20:
        arc = a - 360 * (a > 90)
        return 0x4E9A3A if arc < -110 else 0xE0A020 if arc < -40 else 0xC8342A
    if 6.0 <= d <= 6.9 and int((a + 400) / 22.5) % 2 == 0:
        return 0x3A3A3A
    # The needle, from the hub up and to the left.
    nx, ny = math.cos(math.radians(-128)), math.sin(math.radians(-128))
    along = x * nx + y * ny
    if 0 < along < 5.6 and abs(x * ny - y * nx) < 0.75:
        return 0x16171A
    if d < 1.3:
        return md
    return 0xF4EEDC


K.add("gauge_face", 16, 16, gauge_picture, picture=True)


def fish_decal(facing_left):
    """A cartoon fish in profile, sunny yellow with an orange tail and fins, a big eye and two bubbles: the gun's mascot."""
    def f(i, j):
        w, h = 24, 10
        u = (i + 0.5) / w
        if not facing_left:
            u = 1.0 - u
        v = (j + 0.5) / h - 0.5
        # Head at u ~ 0.2 (toward the muzzle), tail at the right.
        body = (u - 0.42) ** 2 / 0.29 ** 2 + v ** 2 / 0.36 ** 2
        tail = 0.68 <= u <= 0.92 and abs(v) < (u - 0.66) * 1.45 and abs(v) > (u - 0.80) * 1.2
        eye = (u - 0.24) ** 2 * 22 + (v + 0.08) ** 2 * 7 < 0.12
        pupil = (u - 0.235) ** 2 * 22 + (v + 0.08) ** 2 * 7 < 0.04
        bubble = any((u - bu) ** 2 * 24 + (v - bv) ** 2 * 10 < r for bu, bv, r in ((0.05, -0.3, 0.06), (0.1, 0.05, 0.035)))
        if pupil:
            return 0x16171A
        if eye:
            return 0xF8F6F0
        if body < 1.0:
            if body > 0.72:
                return 0xC07018
            fin = abs(u - 0.47) < 0.07 and v > 0.05
            return 0xF09A28 if fin else (0xFFE070 if v < -0.08 else 0xF6C838)
        if tail:
            return 0xF09A28
        if bubble:
            return 0xCFEFFF
        return CLEAR
    return f


K.add("fish_decal_l", 24, 10, fish_decal(True), picture=True)
K.add("fish_decal_r", 24, 10, fish_decal(False), picture=True)


def vent_picture(rings):
    """A round cover's face: radial intake slots round a hub, a raised rim. rings: slot rows."""
    hi, lt, md, lo, dk = RAMPS_HERE["orange"]

    def f(i, j):
        x, y = i - 7.5, j - 7.5
        d = math.hypot(x, y)
        if d > 7.9:
            return CLEAR
        if d > 7.0:
            return lt if y < 0 else md
        if d < 2.2:
            return 0x2C2E33 if d > 1.4 else 0x55585F
        a = math.atan2(y, x)
        if rings and 2.8 < d < 6.4 and int((a + math.pi) / (2 * math.pi) * 12) % 2 == 0:
            return 0x1C1D21
        return md
    return f


K.add("fan_cover", 16, 16, vent_picture(True), picture=True)


def intake_picture(i, j):
    """The vacuum's mouth seen end on: a black throat behind a cross-hatched steel guard."""
    x, y = i - 7.5, j - 7.5
    d = math.hypot(x, y)
    if d > 7.9:
        return CLEAR
    if d > 6.8:
        return RAMPS_HERE["orange"][2]
    if (i % 4 == 1 or j % 4 == 1) and d < 6.8:
        return RAMPS["pipe"][1] if (i + j) % 2 else RAMPS["pipe"][2]
    return 0x101112


K.add("intake_guard", 16, 16, intake_picture, picture=True)


def starter_picture(i, j):
    """The recoil starter's cover: a domed disc with three curved slots and the cord's grommet."""
    hi, lt, md, lo, dk = RAMPS_HERE["orange"]
    x, y = i - 7.5, j - 7.5
    d = math.hypot(x, y)
    if d > 7.9:
        return CLEAR
    if d > 6.9:
        return md
    if d < 1.6:
        return 0x2C2E33
    a = math.degrees(math.atan2(y, x)) % 120
    if 3.4 < d < 5.6 and 15 < a < 85:
        return 0x1C1D21
    return lt if x + y < 0 else md


K.add("starter_face", 16, 16, starter_picture, picture=True)


def brand_picture(i, j):
    """The volute's right cover: an orange disc with a black badge band across it and two pale 'letters' bars."""
    hi, lt, md, lo, dk = RAMPS_HERE["orange"]
    x, y = i - 7.5, j - 7.5
    d = math.hypot(x, y)
    if d > 7.9:
        return CLEAR
    if d > 6.9:
        return md
    if abs(y) < 1.3 and abs(x) < 6.2:
        if abs(y) < 0.6 and -4.5 <= x <= 4.5 and int(x + 7) % 3 != 0:
            return 0xF4EEDC
        return 0x1C1D21
    if d < 1.6:
        return 0x2C2E33
    return lt if y < 0 else md


K.add("brand_disc", 16, 16, brand_picture, picture=True)


def perforated(i, j):
    """A muffler guard: brushed steel punched with a neat grid of holes."""
    if i % 3 == 1 and j % 3 == 1:
        return 0x16171A
    return RAMPS["pipe"][1] if j < 2 else RAMPS["pipe"][2]


K.add("perforated", 16, 16, perforated, picture=True)


def louvres(i, j):
    """An air filter cover: horizontal louvres in black plastic."""
    return 0x101112 if j % 4 == 2 else 0x3A3C42 if j % 4 == 1 else 0x2C2E33


K.add("louvres", 16, 16, louvres, picture=True)


def warning_picture(i, j):
    """A hazard label: yellow with a black border, a black triangle and two lines of 'small print'."""
    if i in (0, 15) or j in (0, 9):
        return 0x16171A
    if 2 <= i <= 6 and j >= 2 and abs(i - 4) <= (j - 2) * 0.45 + 0.2 and j <= 7:
        return 0x16171A
    if i >= 8 and j in (3, 6) and i <= 13:
        return 0x16171A
    return 0xF8C83A


K.add("warning", 16, 10, warning_picture, picture=True)


def zipper_picture(i, j):
    """The bag's zip: a dark track with brass teeth."""
    if j in (0, 3):
        return 0x3E3A30
    return RAMPS["brass"][1] if i % 2 == 0 else 0x2A2620


K.add("zipper", 32, 4, zipper_picture, picture=True)


def slot_picture(i, j):
    """The flat nozzle's mouth: a dark slot with a bevelled orange lip."""
    if 2 <= j <= 5 and 1 <= i <= 14:
        return 0x101112
    return RAMPS_HERE["orange"][1] if j < 2 else RAMPS_HERE["orange"][3]


K.add("nozzle_slot", 16, 8, slot_picture, picture=True)
K.add("checker_rubber", 12, 16, lambda i, j: (RAMPS["rubber"][2] if i in (0, 11) or j in (0, 15) else
                                                RAMPS["rubber"][1] if (i + j) % 3 == 0 else RAMPS["rubber"][3]),
      picture=True)
K.save(TEX)

P = Parts(K)
tube, post, metal, flat, wood, wrap, screw, sticker = P.tube, P.post, P.metal, P.flat, P.wood, P.wrap, P.screw, P.sticker


def turned(parts, axis, angle, pivot):
    m = {"x": rx, "y": ry, "z": rz}[axis](angle)
    return rotate(parts, m, pivot)


def side_disc(out, x0, x1, r, ramp, cy, cz, west=None, east=None, name=None):
    """A short round part along x (a cover, a cap): an octagonal tube built along z and turned a quarter. west and east:
    picture stickers on its outer faces (or None)."""
    disc = []
    tube(disc, x0, x1, r, ramp, cx=0.0, cy=0.0, name=name)
    rotate(disc, ry(90), (0.0, 0.0, 0.0))
    # ry(90) takes (x, y, z) to (z, y, -x): the tube's z run becomes x, centred on x = 0 across z.
    move(disc, dy=cy, dz=cz)
    out += disc
    s = r * 1.0
    if west:
        sticker(out, x0 - 0.03, cy - s, cz - s, x0 - 0.02, cy + s, cz + s, west, "west", name=(name or "disc") + "_face")
    if east:
        sticker(out, x1 + 0.02, cy - s, cz - s, x1 + 0.03, cy + s, cz + s, east, "east", name=(name or "disc") + "_face")


# ========================================================================================================== Fish Gun

FG_BY = 10.0          # the barrel's axis
FG_TY = 4.6           # the air tank's axis
FG_MUZZLE = -15.4
FG_GRIP = (8.0, 4.6, 10.9)
FG_SUPPORT = (8.0, 4.4, -8.3)


def fish_gun():
    out = []
    # --- barrel and bell ---
    tube(out, -11.4, 5.0, 2.5, "copper", cy=FG_BY, caps=(False, False), name="barrel")
    tube(out, -12.6, -11.2, 2.9, "brass", cy=FG_BY, caps=(False, True), name="bell_throat")
    tube(out, -13.9, -12.6, 3.4, "brass", cy=FG_BY, caps=(False, True), name="bell")
    tube(out, FG_MUZZLE, -13.9, 3.9, "brass", cy=FG_BY, end="brass_lo", name="bell_mouth")
    sticker(out, 8.0 - 3.3, FG_BY - 3.3, FG_MUZZLE - 0.06, 8.0 + 3.3, FG_BY + 3.3, FG_MUZZLE - 0.05, "bore_bell", "north",
            name="bore")
    for z in (-6.2, 2.6):
        tube(out, z, z + 0.8, 2.75, "brass", cy=FG_BY, name="barrel_band")
    # The mascot down each flank of the barrel (on the level band's flat sides).
    sticker(out, 8.0 - 2.5 - 0.03, FG_BY - 1.0, -11.0, 8.0 - 2.5 - 0.02, FG_BY + 1.0, -6.4, "fish_decal_l", "west", name="decal")
    sticker(out, 8.0 + 2.5 + 0.02, FG_BY - 1.0, -11.0, 8.0 + 2.5 + 0.03, FG_BY + 1.0, -6.4, "fish_decal_r", "east", name="decal")
    flat(out, 7.65, 13.85, -15.1, 8.35, 14.7, -14.5, "brass", "hi", name="front_sight")
    # --- the loading hopper: where the fish go in ---
    metal(out, 5.9, 11.5, -3.4, 10.1, 13.4, 1.9, "copper", name="hopper")
    metal(out, 5.7, 13.4, -3.6, 10.3, 13.9, 2.1, "brass", name="hopper_lid")
    tube(out, 5.5, 10.5, 0.32, "steel", cx=0.0, cy=0.0, caps=(True, True), name="hinge")
    hinge = out[-4:]
    rotate(hinge, ry(90), (0.0, 0.0, 0.0))
    move(hinge, dy=13.95, dz=2.25)
    flat(out, 7.4, 13.0, -4.0, 8.6, 14.25, -3.6, "brass", "hi", name="latch")
    for x in (6.2, 9.8):
        screw(out, x, 13.9, -3.0, "up", ramp="brass", s=0.18)
    # --- receiver, gauge, sights ---
    metal(out, 5.3, 6.9, 4.4, 10.7, 12.9, 9.8, "brass", name="receiver")
    metal(out, 5.6, 7.2, 9.8, 10.4, 12.6, 10.3, "copper", name="receiver_plate")
    for y, z in ((8.0, 5.4), (11.8, 5.4), (8.0, 8.8), (11.8, 8.8)):
        screw(out, 5.3, y, z, "west", ramp="brass")
        screw(out, 10.7, y, z, "east", ramp="brass")
    for x0 in (6.8, 8.6):
        flat(out, x0, 12.9, 4.6, x0 + 0.6, 13.6, 5.0, "brass", "md", name="rear_sight")
    post(out, 12.9, 14.3, 0.42, "brass", cx=8.0, cz=7.4, name="gauge_stem")
    gauge = []
    tube(gauge, -0.5, 0.5, 2.0, "brass", cx=0.0, cy=0.0, caps=(True, False), name="gauge")
    sticker(gauge, -1.95, -1.95, 0.52, 1.95, 1.95, 0.53, "gauge_face", "south", name="gauge_face")
    rotate(gauge, rx(-22), (0.0, 0.0, 0.0))
    out += move(gauge, dx=8.0, dy=16.1, dz=7.4)
    # The pressure relief valve: a red knob on the receiver's right.
    flat(out, 10.7, 10.3, 6.4, 11.0, 11.0, 7.1, "brass", "lo", name="relief_valve")
    flat(out, 11.0, 10.15, 6.25, 11.6, 11.15, 7.25, "red", "md", name="relief_knob")
    # --- the air tank ---
    tube(out, -4.0, 5.6, 2.2, "tank", cy=FG_TY, end="tank_lo", name="air_tank")
    tube(out, -4.8, -4.0, 1.75, "tank", cy=FG_TY, caps=(True, False), end="tank_md", name="tank_dome")
    tube(out, 5.6, 6.3, 1.75, "tank", cy=FG_TY, caps=(False, True), end="tank_md", name="tank_dome")
    tube(out, -6.2, -4.8, 0.5, "brass", cy=FG_TY, name="fill_valve")
    tube(out, -6.7, -6.2, 1.3, "black", cy=FG_TY, end="black_md", name="handwheel")
    for z in (-2.6, 3.0):
        tube(out, z, z + 0.6, 2.36, "black", cy=FG_TY, name="tank_strap")
    for z in (-2.6, 3.0):
        metal(out, 7.3, 6.7, z - 0.1, 8.7, 7.7, z + 0.7, "black", name="tank_mount")
    # Regulator on the tank's left, its hose up into the receiver.
    metal(out, 5.0, 4.0, 3.9, 6.2, 6.2, 5.3, "brass", name="regulator")
    post(out, 6.2, 9.0, 0.36, "rubber", cx=5.45, cz=4.6, name="air_hose")
    # --- grip, trigger, foregrip ---
    grip = []
    wood(grip, 6.95, 1.6, 9.4, 9.05, 7.0, 11.9, "walnut", upright=True, name="grip")
    metal(grip, 6.85, 1.2, 9.3, 9.15, 1.6, 12.0, "brass", name="grip_cap")
    out += turned(grip, "x", -16, (8.0, 7.0, 10.6))
    metal(out, 7.7, 4.5, 6.4, 8.3, 4.8, 9.6, "brass", name="trigger_guard")
    metal(out, 7.7, 4.8, 6.4, 8.3, 6.9, 6.7, "brass", name="trigger_guard")
    flat(out, 7.85, 5.2, 7.7, 8.15, 6.9, 8.0, "steel", "md", rot=("x", -12, (8.0, 6.9, 7.85)), name="trigger")
    fore = []
    wood(fore, 7.0, 2.2, -9.3, 9.0, 7.6, -7.3, "walnut", upright=True, name="foregrip")
    metal(fore, 6.9, 1.8, -9.4, 9.1, 2.2, -7.2, "brass", name="foregrip_cap")
    out += turned(fore, "x", 10, (8.0, 7.6, -8.3))
    metal(out, 6.85, 7.2, -9.6, 9.15, 7.85, -7.0, "brass", name="foregrip_clamp")
    # --- skeleton stock ---
    for y in (11.3, 7.9):
        tube(out, 10.3, 19.2, 0.55, "brass", cy=y, caps=(False, False), name="stock_tube")
    wood(out, 6.8, 5.2, 19.0, 9.2, 13.6, 21.8, "walnut", name="butt")
    metal(out, 6.7, 5.0, 21.8, 9.3, 13.8, 22.6, "rubber", name="butt_pad")
    return out


# ======================================================================================================= Leaf Blower

LB_BY = 6.0           # the blower tube's axis
LB_NOZZLE = -15.9
LB_GRIP = (8.0, 15.8, 14.6)
LB_SUPPORT = (8.0, 2.8, -2.9)
LB_VOLUTE = (8.6, 8.2)  # its centre (z, y)


def engine(out):
    """Everything behind the fan: block, fins, plug, starter, muffler, fuel tank, filter and the loop handle."""
    metal(out, 5.4, 4.2, 13.0, 10.6, 11.2, 19.6, "iron", name="engine")
    for k in range(5):
        z = 13.6 + 1.1 * k
        metal(out, 5.8, 11.2, z, 10.2, 12.8, z + 0.6, "iron", down=None, name="cooling_fin")
    post(out, 11.2, 13.3, 0.32, "steel", cx=6.0, cz=19.1, name="spark_plug")
    post(out, 13.0, 14.1, 0.48, "black", cx=6.0, cz=19.1, name="plug_boot")
    # Orange shroud panels down both sides of the engine.
    metal(out, 5.0, 4.6, 13.3, 5.4, 10.9, 19.3, "orange", name="shroud")
    metal(out, 10.6, 4.6, 13.3, 11.0, 10.9, 19.3, "orange", name="shroud")
    # Recoil starter and its T-handle on the left.
    side_disc(out, 3.9, 5.0, 2.6, "orange", 7.6, 16.4, west="starter_face", name="starter")
    metal(out, 2.5, 7.05, 15.0, 3.3, 8.15, 17.8, "black", name="pull_handle")
    wrap(out, 3.3, 7.45, 16.25, 3.88, 7.75, 16.55, "string", name="pull_cord")
    # Muffler and its guard on the right, the exhaust out of its back.
    metal(out, 11.0, 5.2, 13.9, 12.4, 9.6, 18.9, "pipe", name="muffler")
    sticker(out, 12.42, 5.5, 14.2, 12.43, 9.3, 18.6, "perforated", "east", name="muffler_guard")
    tube(out, 18.9, 19.9, 0.45, "black", cx=11.7, cy=6.2, name="exhaust")
    # The fuel tank underneath, its red cap at the back.
    metal(out, 5.2, 1.4, 12.4, 10.8, 4.2, 19.8, "milk", name="fuel_tank")
    sticker(out, 5.17, 1.8, 13.4, 5.18, 3.8, 16.6, "warning", "west", name="warning")
    tube(out, 19.8, 20.5, 0.75, "red", cx=6.8, cy=2.8, name="fuel_cap")
    # The air filter cover and its choke lever.
    metal(out, 5.6, 5.0, 19.6, 10.4, 10.6, 20.8, "black", south="louvres", name="air_filter")
    flat(out, 9.2, 9.6, 20.8, 9.8, 10.2, 21.6, "yellow", "md", name="choke")
    # The loop handle: two posts and the rubber grip, the throttle trigger under it, the stop switch on top.
    metal(out, 7.1, 12.4, 7.8, 8.9, 15.3, 9.2, "black", name="handle_post")
    metal(out, 7.1, 12.8, 19.0, 8.9, 15.3, 20.2, "black", name="handle_post")
    metal(out, 6.9, 15.0, 7.6, 9.1, 16.6, 20.4, "rubber", east="checker_rubber", west="checker_rubber", name="handle_grip")
    flat(out, 7.6, 14.0, 13.6, 8.4, 15.0, 15.0, "red", "md", rot=("x", 12, (8.0, 15.0, 14.3)), name="throttle")
    flat(out, 7.5, 16.6, 9.6, 8.5, 17.0, 10.4, "red", "lt", name="stop_switch")


def volute(out, vacuum):
    """The fan's snail housing (a round drum across the body) and the outlet collar."""
    z, y = LB_VOLUTE
    side_disc(out, 5.0, 11.0, 4.6, "orange", y, z, west="fan_cover", east="brand_disc", name="volute")
    tube(out, 1.0, 5.0, 2.2, "orange", cy=LB_BY, caps=(True, False), name="outlet")


def blower_tube(out):
    tube(out, -6.2, 1.0, 1.75, "plastic", cy=LB_BY, caps=(False, False), name="tube")
    tube(out, -7.0, -6.2, 2.0, "orange", cy=LB_BY, name="tube_joint")
    tube(out, -14.4, -7.0, 1.6, "plastic", cy=LB_BY, caps=(False, False), name="tube")
    metal(out, 6.3, 4.25, -14.6, 9.7, 7.75, -13.4, "orange", name="nozzle_neck")
    metal(out, 5.6, 5.0, LB_NOZZLE, 10.4, 7.0, -14.6, "orange", north="nozzle_slot", name="nozzle")


def support_grip(out):
    tube(out, -3.5, -2.1, 1.95, "black", cy=LB_BY, name="grip_clamp")
    grip = []
    post(grip, 0.6, 4.4, 0.62, "rubber", cx=8.0, cz=-2.8, name="support_grip")
    post(grip, 0.2, 0.6, 0.8, "black", cx=8.0, cz=-2.8, name="support_knob")
    out += turned(grip, "x", -8, (8.0, 4.4, -2.8))


def intake_tube(out):
    """The Blower-Vac's ribbed intake and its round, grilled mouth."""
    tube(out, -13.0, 1.0, 2.05, "plastic", cy=LB_BY, caps=(False, False), name="intake")
    for z in (-10.6, -7.4, -1.0):
        tube(out, z, z + 0.7, 2.25, "black", cy=LB_BY, name="intake_rib")
    tube(out, -14.5, -13.0, 2.6, "orange", cy=LB_BY, caps=(False, True), name="mouth_neck")
    tube(out, LB_NOZZLE, -14.5, 3.0, "orange", cy=LB_BY, end="orange_lo", name="mouth")
    sticker(out, 8.0 - 2.9, LB_BY - 2.9, LB_NOZZLE - 0.06, 8.0 + 2.9, LB_BY + 2.9, LB_NOZZLE - 0.05, "intake_guard", "north",
            name="mouth_guard")


def bag(out):
    """The canvas collection bag slung under the body on an elbow from the fan, with its zip and a strap."""
    post(out, 0.9, 3.9, 1.5, "orange", cx=8.0, cz=6.6, name="bag_elbow")
    post(out, 0.3, 0.9, 1.85, "black", cx=8.0, cz=6.6, name="bag_collar")
    # Stacked slabs, each a little narrower and shorter than the one it bulges out of, so the bag reads stuffed and soft.
    metal(out, 4.4, -4.6, 3.4, 11.6, 0.3, 21.0, "canvas", name="bag")
    metal(out, 3.7, -4.0, 4.2, 12.3, -0.4, 20.2, "canvas", name="bag_belly")
    metal(out, 5.0, -5.5, 4.4, 11.0, -4.6, 20.0, "canvas", name="bag_bottom")
    metal(out, 4.9, -4.3, 2.7, 11.1, -0.2, 21.7, "canvas", name="bag_ends")
    sticker(out, 3.67, -1.6, 5.0, 3.68, -1.0, 19.4, "zipper", "west", name="zipper")
    wrap(out, 3.55, -4.75, 11.6, 12.45, 0.42, 12.4, "tape_black", name="bag_strap")
    flat(out, 7.2, 0.3, 19.2, 8.8, 1.1, 20.4, "brass", "md", name="bag_buckle")


def mode_lever(out, suck):
    """The yellow blow/suck lever on the volute's top left: forward to blow, back to suck."""
    z, y = LB_VOLUTE
    metal(out, 5.4, y + 4.4, z - 1.0, 6.8, y + 5.0, z + 1.0, "black", name="lever_base")
    arm = []
    flat(arm, 5.75, y + 5.0, z - 0.25, 6.45, y + 7.4, z + 0.25, "yellow", "md", name="lever")
    flat(arm, 5.6, y + 7.4, z - 0.45, 6.6, y + 8.0, z + 0.45, "yellow", "lt", name="lever_knob")
    out += turned(arm, "x", 35 if suck else -35, (6.1, y + 5.0, z))


def leaf_blower(vacuum=False, suck=False):
    out = []
    volute(out, vacuum)
    engine(out)
    if vacuum:
        intake_tube(out)
        bag(out)
        mode_lever(out, suck)
    else:
        blower_tube(out)
    support_grip(out)
    return out


# ======================================================================================================== outputs

CREDIT = "tomyen {} - generated by tools/gen_fishgun_blower.py"
RIGS = OrderedDict()


def raised(displays, grip, lift, out=0.0):
    """The first-person transform moved so the grip sits `lift` pixels higher (and `out` further right) than a rifle's:
    the blower's handle is on top, and held at a rifle's grip height its body would hang off the bottom of the screen."""
    t = displays["firstperson_righthand"]
    target = (FP_RIFLE_GRIP[0] + out, FP_RIFLE_GRIP[1] + lift, FP_RIFLE_GRIP[2])
    d = OrderedDict(displays)
    d["firstperson_righthand"] = d["firstperson_lefthand"] = display(t["rotation"], placed(t["rotation"], t["scale"][0], grip, target),
                                                                     t["scale"][0])
    return d


def write_model(name, elements, displays):
    write(os.path.join(MODELS, name + ".json"), model(K, elements, displays, credit=CREDIT.format(name)))


def report(name, elements, displays, grip, support, muzzle):
    t = displays["firstperson_righthand"]
    print(f"{name}: {len(elements)} elements; firstperson {t['translation']} rot {t['rotation'][1]} scale {t['scale'][0]}; "
          f"grip {fp_point(displays, grip)} support {fp_point(displays, support)} muzzle {fp_point(displays, muzzle)}")
    RIGS[name] = dict(display=(t["translation"], t["rotation"][1], t["scale"][0]), grip=fp_point(displays, grip),
                      support=fp_point(displays, support), muzzle=fp_point(displays, muzzle))


fg = fish_gun()
check_all("fish_gun", fg)
FG_DISPLAYS = gun_displays(FG_GRIP, fg, 0.46, gui_fill=15.5)
write_model("fish_gun", fg, FG_DISPLAYS)
write(os.path.join(ITEMS, "fish_gun.json"), {"model": ref("fish_gun")})
report("fish_gun", fg, FG_DISPLAYS, FG_GRIP, FG_SUPPORT, (8.0, FG_BY, FG_MUZZLE))

lb = leaf_blower()
check_all("leaf_blower", lb)
LB_DISPLAYS = raised(gun_displays(LB_GRIP, lb, 0.48, gui_fill=15.0, gui_rot=[18, -125, 0]), LB_GRIP, 4.5, 0.8)
write_model("leaf_blower", lb, LB_DISPLAYS)
write(os.path.join(ITEMS, "leaf_blower.json"), {"model": ref("leaf_blower")})
report("leaf_blower", lb, LB_DISPLAYS, LB_GRIP, LB_SUPPORT, (8.0, LB_BY, LB_NOZZLE))

vac = leaf_blower(vacuum=True)
vac_suck = leaf_blower(vacuum=True, suck=True)
check_all("leaf_vacuum", vac)
check_all("leaf_vacuum_suck", vac_suck)
LV_DISPLAYS = raised(gun_displays(LB_GRIP, vac, 0.48, gui_fill=15.0, gui_rot=[18, -125, 0]), LB_GRIP, 4.5, 0.8)
write_model("leaf_vacuum", vac, LV_DISPLAYS)
write_model("leaf_vacuum_suck", vac_suck, LV_DISPLAYS)
write(os.path.join(ITEMS, "leaf_vacuum.json"), {"model": OrderedDict([
    ("type", "minecraft:condition"), ("property", "minecraft:has_component"), ("component", "tomyen:blower_suck"),
    ("on_true", ref("leaf_vacuum_suck")), ("on_false", ref("leaf_vacuum"))])})
report("leaf_vacuum", vac, LV_DISPLAYS, LB_GRIP, LB_SUPPORT, (8.0, LB_BY, LB_NOZZLE))

# The Vacuum Kit: an inventory-only part, a flat sprite: a coil of ribbed hose round the yellow-capped motor pod with the
# folded canvas bag behind.
VK = {"o": (0x1A, 0x1B, 0x1E, 255), "g": (0xC9, 0xCD, 0xD2, 255), "G": (0x8E, 0x94, 0x9B, 255), "k": (0x2C, 0x2E, 0x33, 255),
      "y": (0xF8, 0xC8, 0x3A, 255), "Y": (0xB9, 0x88, 0x14, 255), "c": (0xAE, 0x9C, 0x6C, 255), "C": (0x78, 0x68, 0x42, 255),
      "r": (0xF4, 0x97, 0x3E, 255), "R": (0xB5, 0x5E, 0x16, 255), "w": (0xE8, 0xEA, 0xEC, 255)}
sprite(os.path.join(TEX, "item", "vacuum_kit.png"), [
    "................",
    "........oooo....",
    ".......occcco...",
    "......occCccCo..",
    "..oooooccccCco..",
    ".ogwgGgoCccccCo.",
    "ogGkkkGgoCcCcco.",
    "ogkoooOkGo.oCo..",
    "ogkorrrokgo.o...",
    "oGkoryyrokGo....",
    "ogkoryYrokgo....",
    "oGkorRRrokGo....",
    ".oGkoooookgo....",
    "..oGgkkkgGo.....",
    "...ooGgGoo......",
    ".....ooo........",
], {**VK, "O": VK["o"]})
write(os.path.join(MODELS, "vacuum_kit.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/vacuum_kit"}})
write(os.path.join(ITEMS, "vacuum_kit.json"), {"model": ref("vacuum_kit")})

# ======================================================================================================= client rigs


def vec(p):
    return "new Vector3f({:.4f}f, {:.4f}f, {:.4f}f)".format(*p)


def rig_class(package, name, doc, rigs):
    lines = [f"package com.jheinz.tomyen.client.{package};", "", "import org.joml.Vector3f;", "", "/**", f" * {doc}",
             " * Generated by tools/gen_fishgun_blower.py from the models. DISPLAY is the first-person transform (translation in",
             " * pixels, yaw in degrees, scale); GRIP, SUPPORT and MUZZLE are in the held item's space (blocks).", " */",
             f"public final class {name} {{"]
    for key, r in rigs:
        up = key.upper()
        tr, yaw, sc = r["display"]
        lines.append(f"\t// {key}")
        lines.append(f"\tpublic static final float[] {up}_DISPLAY = {{{tr[0]:.4f}f, {tr[1]:.4f}f, {tr[2]:.4f}f, {yaw:.1f}f, {sc:.3f}f}};")
        lines.append(f"\tpublic static final Vector3f {up}_GRIP = {vec(r['grip'])};")
        lines.append(f"\tpublic static final Vector3f {up}_SUPPORT = {vec(r['support'])};")
        lines.append(f"\tpublic static final Vector3f {up}_MUZZLE = {vec(r['muzzle'])};")
    lines += ["", f"\tprivate {name}() {{", "\t}", "}", ""]
    path = os.path.join(CLIENT, package, name + ".java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write("\n".join(lines))


rig_class("fishgun", "FishGunRig", "Where the Fish Gun's hands and bell muzzle are.", [("fish_gun", RIGS["fish_gun"])])
rig_class("leafblower", "LeafBlowerRig", "Where the Leaf Blower's and Blower-Vac's hands and nozzle are (both share the body).",
          [("leaf_blower", RIGS["leaf_blower"]), ("leaf_vacuum", RIGS["leaf_vacuum"])])

# ============================================================================================================== lang


def merge(path, entries):
    with open(path, encoding="utf-8") as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("item.tomyen.fish_gun", "Fish Gun"),
    ("item.tomyen.fish_gun.loaded", "Loaded: %s"),
    ("item.tomyen.fish_gun.empty", "No raw fish to fire"),
    ("item.tomyen.fish_gun.hint", "Fires raw fish from your inventory: the heavier the fish, the harder it hits"),
    ("item.tomyen.fish_gun.select_hint", "%s: pick which fish to fire (trophies only when picked)"),
    ("item.tomyen.fish_gun.selected", "Loading %s"),
    ("item.tomyen.fish_gun.no_fish", "No raw fish to load"),
    ("item.tomyen.fish_gun.only_trophies", "Only trophies left: pick one with %s to fire it"),
    ("item.tomyen.fish_gun.bruised", "Bruised from being fired out of a fish gun"),
    ("hud.tomyen.fish_gun.no_fish", "No fish"),
    ("hud.tomyen.fish_gun.trophy", "Trophy"),
    ("hud.tomyen.fish_gun.loading", "Loading..."),
    ("hud.tomyen.fish_gun.class.pea", "Pea-shooter"),
    ("hud.tomyen.fish_gun.class.slap", "Slapper"),
    ("hud.tomyen.fish_gun.class.thump", "Thumper"),
    ("hud.tomyen.fish_gun.class.haymaker", "Haymaker"),
    ("hud.tomyen.fish_gun.class.cannonball", "CANNONBALL"),
    ("entity.tomyen.fish_round", "Flying Fish"),
    ("death.attack.tomyen.fish_gun", "%1$s was slapped into next week by %2$s's fish"),
    ("death.attack.tomyen.fish_gun.item", "%1$s was slapped into next week by %2$s's fish"),
    ("subtitles.tomyen.fish_gun_fire", "Fish Gun goes FWOOMP"),
    ("subtitles.tomyen.fish_gun_whoosh", "Fish whooshes"),
    ("subtitles.tomyen.fish_splat", "Fish splats"),
    ("item.tomyen.leaf_blower", "Leaf Blower"),
    ("item.tomyen.leaf_vacuum", "Leaf Blower-Vac"),
    ("item.tomyen.vacuum_kit", "Vacuum Kit"),
    ("item.tomyen.vacuum_kit.hint", "Combine with a Leaf Blower at a crafting table"),
    ("item.tomyen.leaf_blower.fuel", "Fuel: %s%%"),
    ("item.tomyen.leaf_blower.hint", "Hold use to blow; punch to refuel (kerosene canister or fish oil)"),
    ("item.tomyen.leaf_vacuum.hint", "%s: switch between blow and suck; sneak and punch to open the bag"),
    ("item.tomyen.leaf_vacuum.bag", "Bag: %s / %s"),
    ("item.tomyen.leaf_blower.tank_full", "The tank's full"),
    ("item.tomyen.leaf_blower.no_fuel", "No kerosene canister or fish oil to fuel it"),
    ("item.tomyen.leaf_vacuum.mode", "Mode: %s"),
    ("item.tomyen.leaf_vacuum.mode.blow", "Blow"),
    ("item.tomyen.leaf_vacuum.mode.suck", "Suck"),
    ("hud.tomyen.leaf_blower.fuel", "Fuel %s%%"),
    ("hud.tomyen.leaf_blower.empty", "Out of fuel"),
    ("hud.tomyen.leaf_vacuum.blow", "BLOW"),
    ("hud.tomyen.leaf_vacuum.suck", "SUCK"),
    ("hud.tomyen.leaf_vacuum.bag", "Bag %s/%s"),
    ("subtitles.tomyen.leaf_blower_engine", "Leaf blower roars"),
    ("subtitles.tomyen.leaf_blower_start", "Pull cord rips, engine catches"),
]))

# ============================================================================================================ sounds

merge(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("fish_gun_whoosh", {"subtitle": "subtitles.tomyen.fish_gun_whoosh", "sounds": [
        {"name": "minecraft:entity/wind_charge/wind_burst1", "pitch": 0.6}, {"name": "minecraft:entity/wind_charge/wind_burst2", "pitch": 0.6},
        {"name": "minecraft:entity/wind_charge/wind_burst3", "pitch": 0.6}]}),
    ("fish_splat", {"subtitle": "subtitles.tomyen.fish_splat", "sounds": [
        {"name": "minecraft:mob/slime/big1", "pitch": 1.1}, {"name": "minecraft:mob/slime/big2", "pitch": 1.1},
        {"name": "minecraft:mob/slime/big3", "pitch": 1.1}, {"name": "minecraft:mob/slime/big4", "pitch": 1.1},
        {"name": "minecraft:block/honeyblock/break1", "pitch": 0.8}, {"name": "minecraft:block/honeyblock/break2", "pitch": 0.8},
        {"name": "minecraft:block/mud/break1", "pitch": 1.2}, {"name": "minecraft:block/mud/break2", "pitch": 1.2}]}),
    ("leaf_blower_engine", {"subtitle": "subtitles.tomyen.leaf_blower_engine", "sounds": ["tomyen:leaf_blower_engine"]}),
    ("leaf_blower_start", {"subtitle": "subtitles.tomyen.leaf_blower_start", "sounds": ["tomyen:leaf_blower_start"]}),
]))

RATE = 44100


def lowpass(xs, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    y, out = 0.0, []
    for x in xs:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(xs, cutoff):
    return [x - l for x, l in zip(xs, lowpass(xs, cutoff))]


def resonator(xs, freq, q):
    """A two-pole resonance (a pipe's ring) run over xs."""
    w = 2 * math.pi * freq / RATE
    r = math.exp(-w / (2 * q))
    a1, a2 = -2 * r * math.cos(w), r * r
    y1 = y2 = 0.0
    out = []
    for x in xs:
        y = x - a1 * y1 - a2 * y2
        out.append(y)
        y2, y1 = y1, y
    return out


def two_stroke(seconds, rpm_hz, rough=0.0, seed=21):
    """A two-stroke's note: a firing pulse every cycle (a sharp crack of noise into the exhaust's ringing), the buzz of the
    expansion chamber, and the rush of air through the fan. Whole cycles in `seconds` so it loops."""
    sr = random.Random(seed)
    n = int(RATE * seconds)
    period = RATE / rpm_hz
    pulses = [0.0] * n
    k = 0
    while True:
        at = int(k * period + (sr.uniform(-1, 1) * rough * period if k else 0))
        if at >= n:
            break
        amp = 1.0 + sr.uniform(-0.15, 0.15) - (sr.random() < rough * 0.6) * 0.7
        for i in range(min(n - at, int(period))):
            t = i / RATE
            pulses[at + i] += amp * (sr.uniform(-1, 1) * math.exp(-t / 0.0015) + 0.6 * math.exp(-t / 0.004))
        k += 1
    exhaust = [0.6 * a + 0.4 * b for a, b in zip(resonator(pulses, rpm_hz * 2.0, 6.0), resonator(pulses, 420.0, 4.0))]
    buzz = lowpass(highpass(exhaust, 120), 3800)
    air = lowpass(highpass([sr.uniform(-1, 1) for _ in range(n)], 500), 2600)
    peak = max(abs(v) for v in buzz) or 1.0
    return [b / peak + a * 0.22 for b, a in zip(buzz, air)]


def loop_fade(xs, fade):
    n = len(xs)
    f = int(RATE * fade)
    for i in range(f):
        w = i / f
        xs[i] = xs[i] * w + xs[n - f + i] * (1 - w)
    return xs[:n - f]


def engine_loop():
    """The blower at full throttle (the game pitches it down for idle): about 7000 rpm, every cycle a firing stroke."""
    return loop_fade(two_stroke(1.2, 116.0, rough=0.04), 0.1)


def pull_start():
    """The pull cord ripping out (a rising zip of the starter pawls), two coughs, then the engine catching and settling."""
    sr = random.Random(8)
    out = []
    zip_n = int(RATE * 0.38)
    noise = [sr.uniform(-1, 1) for _ in range(zip_n)]
    teeth = []
    for i in range(zip_n):
        t = i / RATE
        rate = 40 + 260 * t / 0.38
        ph = (rate * t) % 1.0
        teeth.append(noise[i] * (0.25 + 0.75 * math.exp(-ph / 0.08)))
    zip_s = highpass(lowpass(teeth, 4200), 700)
    env = [min(1.0, i / (RATE * 0.02)) * (1.0 - 0.3 * i / zip_n) for i in range(zip_n)]
    out += [z * e * 0.9 for z, e in zip(zip_s, env)]
    out += [0.0] * int(RATE * 0.08)
    for gap in (0.17, 0.14):
        cough = two_stroke(0.09, 60.0, rough=0.3, seed=sr.randint(0, 999))
        out += [c * 0.7 * math.exp(-i / (RATE * 0.05)) for i, c in enumerate(cough)]
        out += [0.0] * int(RATE * gap)
    run = two_stroke(1.0, 95.0, rough=0.12, seed=5)
    rise = int(RATE * 0.12)
    tail = int(RATE * 0.35)
    for i, v in enumerate(run):
        e = min(1.0, i / rise) * (1.0 if i < len(run) - tail else (len(run) - i) / tail)
        out.append(v * 0.8 * e)
    return out


def encode(name, samples, gain=0.9):
    import vorbis_mono
    peak = max(abs(v) for v in samples) or 1.0
    xs = [math.tanh(1.3 * v / peak) / math.tanh(1.3) * gain for v in samples]
    path = os.path.join(ASSETS, "sounds", name + ".ogg")
    vorbis_mono.encode(xs, path)
    print("wrote", path)


if SOUNDS:
    encode("leaf_blower_engine", engine_loop(), 0.8)
    encode("leaf_blower_start", pull_start(), 0.85)
print("fish gun and leaf blower assets written")
