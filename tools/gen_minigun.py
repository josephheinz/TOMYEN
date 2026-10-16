"""Builds the power armor Minigun's assets: the gun, its three Weapon Station parts, the Hogfire Gun Mount, and the
spin-up, spin, fire and spin-down sounds.

An M134-style rotary gun at power armor scale, every part built for its shape:
- the turning group: six barrels (each a cross of two bars, so it reads round), a twelve-sided muzzle shroud ring, a
  spider clamp inside it and a middle clamp (round plates), and the cluster hub they all run back into;
- the rotor housing behind the hub (octagonal, with a bolted front flange and a power-armor-yellow band), and a vented
  hand guard over the top of the barrels;
- the receiver (chamfered), with a top cover and a carry handle with a rubber sleeve; the drive motor on its right with
  five cooling fins; the feeder on its left, from which a chute loops out and down, showing the linked brass rounds in
  it, into the ammo box slung under the receiver, which has a feed stub out the back for a backpack line;
- rear spade grips: two vertical rubber grips in a frame with a yellow butterfly trigger between them.
Materials: blued steel, darker parkerized parts, bright steel barrels, brass rounds with copper tips, rubber grips, an
olive ammo box and yellow accents, all as smooth ramps from vanilla colours (smooth basalt, polished blackstone, iron,
netherite, gold, copper, green terracotta, yellow concrete, black wool). No noise.

It's held at the hip: the shooting hand on the right spade grip, the off hand on the carry handle.

The turning group is its own set of models, posed at six angles 10 degrees apart (everything in it repeats every 60
degrees), picked by tomyen:minigun_barrels, so it turns as the gun spins up; with tomyen:minigun_heat past HOT the front
of each barrel glows. tomyen:minigun_flash picks a muzzle flash while it fires. The Hogfire Gun Mount is the same
turning group on a short receiver and motor, its chute dropping into an ammo can on a saddle plate; HogfireGearLayer
draws it on the pig's shoulders (keep GUN_BORE_Y and GUN_MUZZLE_Z there equal to BY and MUZZLE here).

Every model, together with the layers drawn over it, is checked for z-fighting in world space (tools/coplanar.py):
no two faces may share a plane, face the same way and overlap, whatever their rotations.

Model space is in pixels: the bore runs along z at x = 8, y = 9 with the muzzle toward -z, +x is the gun's right.
GunDisplay and GunHands mirror the first-person transform and grip points printed at the end, so move them together.
With ffmpeg on the path (and without --models-only) it also synthesises the four sounds.

Usage: python3 tools/gen_minigun.py src/main/resources/assets/tomyen [--models-only]"""
import math, os, random, shutil, struct, subprocess, sys, tempfile, wave

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rz, write, model, ref, range_dispatch, composite, display, placed,
                    centred, bounds_centre, display_matrix, DIAGONAL, mix, flash_models, element_rotation, apply)
from coplanar import check_elements

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

# Where long guns put the shooting hand (gen_napalm.py's), pulled in for a gun held at the hip by its rear spade grip.
FP_GRIP_TARGET = (-0.022 * 16 + 0.6, 0.119 * 16 - 0.2, 0.156 * 16 - 0.8)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
# Third person, the arms hang this far below the crossbow hold (HumanoidMinigunPoseMixin), so the gun is turned back up.
TP_DROP = 43.0

# ============================================================================================================ atlas
# One smooth ramp per material (lighter at the top), no speckle; pictures only where a pattern is the point.

K = Kit("minigun", "item/minigun", seed=134)


def flat(colour):
    return lambda x, y, w, h: K.rgb(colour)


def ramp(top, bottom):
    return lambda x, y, w, h: K.rgb(mix(top, bottom, y / max(1, h - 1)))


def heat(along_u):
    """A barrel's glowing front: white-hot at the muzzle end through orange to a dull red."""
    def f(x, y, w, h):
        t = (x / max(1, w - 1)) if along_u else (y / max(1, h - 1))
        col = mix(0xFFE08A, 0xFF7A26, min(1.0, t * 1.6)) if t < 0.62 else mix(0xFF7A26, 0x9A2E1C, (t - 0.62) / 0.38)
        return K.rgb(col)
    return f


BRASS = (0xC2952F, 0xE6BE52, 0xA8791F)      # a round lit from above: side, middle, shadow side
COPPER = (0xB0603A, 0xD87A52, 0x9C4529)
LINK = 0x34343A
CHUTE_FLOOR = 0x17171B


def belt(x, y, w, h):
    """Three linked rounds side by side, bullets toward v = 0 (the muzzle): copper tip, brass case, darker rim, with
    the steel links between them."""
    col = x % 4
    if col == 0:
        return K.rgb(LINK if y >= 3 else CHUTE_FLOOR)
    c = col - 1
    if y == 0:
        return K.rgb(COPPER[1] if c == 1 else CHUTE_FLOOR)
    if y <= 2:
        return K.rgb(COPPER[c])
    if y == 3:
        return K.rgb(mix(BRASS[c], 0x000000, 0.22))
    if y == h - 1:
        return K.rgb(mix(BRASS[c], 0x000000, 0.35))
    return K.rgb(BRASS[c])


def hazard(x, y, w, h):
    return K.rgb(0xF0AF15 if ((x + y) // 2) % 2 == 0 else 0x1B1B1F)


def vent(x, y, w, h):
    """Blued steel with three cross slots, each with a lit lip below it."""
    base = mix(0x5C6170, 0x3E414D, y / (h - 1))
    if 2 <= x <= w - 3:
        if y % 5 in (2, 3) and y < 15:
            return K.rgb(0x15161A)
        if y % 5 == 4 and y < 15:
            return K.rgb(mix(base, 0xFFFFFF, 0.12))
    return K.rgb(base)


def grip(x, y, w, h):
    """Rubber with moulded finger ridges."""
    if y % 3 == 2:
        return K.rgb(0x111114)
    return K.rgb(mix(0x34343A, 0x1E1E22, abs(x - (w - 1) / 2) / ((w - 1) / 2)))


def bore(x, y, w, h):
    return K.rgb(mix(0x060606, 0x5A5E68, max(0.0, min(1.0, (math.hypot(x - 7.5, y - 7.5) - 3.0) / 4.0))))


K.swatch("blued", 0, 0, 4, 4, ramp(0x5C6170, 0x3E414D))        # smooth basalt cooled toward blue
K.swatch("blued_dark", 4, 0, 8, 4, ramp(0x3E404B, 0x272830))   # polished blackstone
K.swatch("steel", 8, 0, 12, 4, ramp(0x9A9EA8, 0x5E626C))       # lodestone / iron ingot greys
K.swatch("park", 12, 0, 16, 4, ramp(0x4C4C46, 0x2E2E2A))       # parkerized: netherite greys
K.swatch("rubber", 0, 4, 4, 8, ramp(0x2C2C31, 0x141418))       # black wool
K.swatch("olive", 4, 4, 8, 8, ramp(0x646C38, 0x474D26))        # green terracotta
K.swatch("olive_dark", 8, 4, 12, 8, ramp(0x474E27, 0x33391C))
K.swatch("bore", 12, 4, 16, 8, bore, picture=True)
# The hot front of a barrel, as a picture so the ramp runs the face's whole length: u along it for the side faces,
# v along it for the top and bottom.
K.swatch("hot_u", 0, 8, 8, 10, heat(True), picture=True)
K.swatch("hot_v", 8, 8, 10, 16, heat(False), picture=True)
K.swatch("belt", 10, 8, 13, 11, belt, picture=True)
K.swatch("hazard", 13, 8, 16, 9, hazard, picture=True)
K.swatch("yellow", 13, 9, 16, 10, ramp(0xF4B41A, 0xC58A12))     # yellow concrete to terracotta
K.swatch("vent", 0, 10, 2, 14, vent, picture=True)
K.swatch("grip", 2, 10, 4, 12, grip, picture=True)
K.swatch("brass", 4, 10, 6, 12, ramp(0xE6BE52, 0xA8791F))       # gold ingot to yellow terracotta
K.swatch("bolt", 6, 10, 8, 12, ramp(0xB4B6BE, 0x7A7C84))
K.swatch("shadow", 2, 12, 4, 14, flat(0x15161A))
K.save(TEX)

# ===================================================================================================== dimensions

BX, BY = 8.0, 9.0
MUZZLE = -15.2            # the front of the muzzle shroud, where the flash starts
BARREL_TIP = -14.6
BARREL_R = 1.8            # barrel centres from the axis
BARREL_W, BARREL_T = 0.56, 0.3   # half sizes of a barrel's two crossed bars
BARREL_END = 2.4          # where the barrels go into the hub
HOT_LENGTH = 5.4          # how much of each barrel glows when hot
SHROUD = (-15.2, -12.6)
SHROUD_A, SHROUD_T = 3.1, 0.5
SPIDER = (-13.6, -13.0)
MID_CLAMP = (-5.8, -4.8)
HUB = (1.0, 3.6)
HOUSING = (3.6, 9.8)
FRAMES = 6
FRAME_STEP = 10.0         # degrees between barrel frames; the group repeats after 60
GRIP = (11.0, 8.1, 23.1)          # the shooting hand round the right spade grip
SUPPORT = (8.0, 15.7, 14.1)       # the off hand round the carry handle's sleeve
T15 = math.tan(math.radians(15))
T22 = math.tan(math.radians(22.5))


def dodeca(out, z0, z1, r, mat, cap=None, name=None):
    """A twelve-sided plate or prism round the bore: six bands through the axis 30 degrees apart, each as wide as a side,
    so their ends make up the dodecagon; ends staggered a hair so the overlapping caps don't fight."""
    s = r * T15
    cap = cap or mat
    for k in range(6):
        d = 0.01 * k
        K.box(out, BX - r, BY - s, z0 + d, BX + r, BY + s, z1 - d, mat, north=cap, south=cap,
              rot=("z", 30.0 * k, (BX, BY, z0)) if k else None, name=name if k == 0 else None)


# ===================================================================================================== turning group


def barrels(angle, hot):
    """The turning group, turned `angle` degrees about the bore. Hot, the front of each barrel glows."""
    out = []
    for i in range(6):
        one = []
        cx, cy = BX, BY + BARREL_R
        for k, (hw, hh) in enumerate(((BARREL_W, BARREL_T), (BARREL_T, BARREL_W))):
            tip = BARREL_TIP + 0.01 * k
            face = "bore" if k == 0 else "steel"
            nm = ("barrel_hot" if hot else "barrel") if i == 0 and k == 0 else None
            if hot:
                g = 0.01
                K.box(one, cx - hw - g, cy - hh - g, tip, cx + hw + g, cy + hh + g, BARREL_TIP + HOT_LENGTH, "hot_u", emit=True,
                      north="bore" if k == 0 else "hot_v", up="hot_v", down="hot_v", south=None, name=nm)
                K.box(one, cx - hw, cy - hh, BARREL_TIP + HOT_LENGTH, cx + hw, cy + hh, BARREL_END, "steel", north=None, south=None)
            else:
                K.box(one, cx - hw, cy - hh, tip, cx + hw, cy + hh, BARREL_END, "steel", north=face, south=None, name=nm)
        out += rotate(one, rz(60.0 * i), (BX, BY, 0.0))
    # The muzzle shroud: a ring of twelve plates, dark inside.
    for k in range(12):
        plate = []
        d = 0.01 * (k % 2)
        hw = SHROUD_A * T15
        K.box(plate, BX - hw, BY + SHROUD_A - SHROUD_T, SHROUD[0] + d, BX + hw, BY + SHROUD_A, SHROUD[1] - d, "blued",
              down="shadow", north="blued_dark", name="muzzle_shroud" if k == 0 else None)
        out += rotate(plate, rz(30.0 * k), (BX, BY, 0.0)) if k else plate
    dodeca(out, SPIDER[0], SPIDER[1], 2.55, "blued_dark", name="spider_clamp")
    dodeca(out, MID_CLAMP[0], MID_CLAMP[1], 2.6, "blued", cap="blued_dark", name="barrel_clamp")
    dodeca(out, HUB[0], HUB[1], 2.9, "blued", cap="blued_dark", name="cluster_hub")
    return rotate(out, rz(angle), (BX, BY, 0.0)) if angle else out


# ===================================================================================================== static parts


def housing():
    """The rotor housing: an octagon behind the hub with a bolted front flange and a yellow band."""
    out = []
    K.rod(out, HOUSING[0], HOUSING[1], 3.3, "blued", cap="blued_dark", cx=BX, cy=BY, name="rotor_housing")
    K.rod(out, 3.3, 4.3, 3.6, "park", cx=BX, cy=BY, name="housing_flange")
    K.rod(out, 8.2, 9.2, 3.45, "yellow", cx=BX, cy=BY, name="housing_band")
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        x, y = BX + sx * 2.33, BY + sy * 2.33
        K.box(out, x - 0.25, y - 0.25, 3.0, x + 0.25, y + 0.25, 3.3, "bolt", south=None, name="flange_bolt")
    return out


def hand_guard():
    """A vented half-shroud over the top of the barrels, from the housing to just short of the middle clamp."""
    out = []
    hw = 3.55 * T22
    for k, a in enumerate((0, 45, -45)):
        d = 0.02 * (k > 0) + 0.01 * (k == 2)
        K.box(out, BX - hw, BY + 3.15, -4.2 + d, BX + hw, BY + 3.55, 3.8 - d, "blued", up="vent", down="shadow",
              north="blued_dark", south=None, rot=("z", a, (BX, BY, 0.0)) if a else None, name="hand_guard" if k == 0 else None)
    return out


def receiver(z1=19.4):
    out = []
    K.chamfered(out, 4.4, 4.6, 9.2, 11.6, 12.6, z1, 0.9, "blued", cap="blued_dark", name="receiver")
    K.box(out, 5.8, 12.6, 10.0, 10.2, 13.0, z1 - 0.6, "park", down=None, name="top_cover")
    return out


def handle():
    out = []
    for z in (11.0, 16.4):
        K.box(out, 7.3, 13.0, z, 8.7, 15.2, z + 1.0, "park", up=None, down=None, name="carry_handle_post")
    K.box(out, 7.15, 15.2, 10.6, 8.85, 16.2, 17.8, "park", name="carry_handle")
    K.box(out, 7.0, 15.05, 12.2, 9.0, 16.35, 16.0, "grip", up="rubber", down="rubber", name="carry_handle_sleeve")
    return out


MOTOR = (13.5, 7.6)


def motor(z0=10.2, z1=17.6, fins=5):
    """The drive motor down the right side: a parkerized can, steel cooling fins, a bracket to the receiver, an end cap."""
    out = []
    mx, my = MOTOR
    K.rod(out, z0, z1, 1.7, "park", cap="blued_dark", cx=mx, cy=my, name="drive_motor")
    step = (z1 - z0 - 1.4) / (fins - 1)
    for k in range(fins):
        z = z0 + 0.7 + step * k
        K.box(out, 11.7, my - 2.1, z, mx + 2.1, my + 2.1, z + 0.3, "steel", name="cooling_fin" if k == 0 else None)
    K.box(out, 11.6, my - 1.4, z0 + 0.5, 12.0, my + 1.4, z1 - 0.5, "blued_dark", west=None, name="motor_bracket")
    K.box(out, mx - 0.9, my - 0.9, z1, mx + 0.9, my + 0.9, z1 + 0.7, "steel", north=None, name="motor_cap")
    return out


def feeder(z0=10.8, z1=16.4):
    """The feeder/delinker on the left, the chute's throat, a yellow cover latch."""
    out = []
    K.box(out, 2.8, 6.2, z0, 4.4, 11.6, z1, "park", east=None, name="feeder")
    K.box(out, 1.8, 7.2, 11.4, 2.8, 10.8, 15.8, "blued_dark", east=None, name="feed_throat")
    K.box(out, 3.0, 11.6, 12.8, 4.2, 12.0, 14.4, "yellow", down=None, name="feeder_latch")
    return out


CHUTE_R = 3.3
CHUTE_C = (1.8, BY - CHUTE_R)
BELT_Z = (12.0, 15.2)


def chute(segments=4, sweep=180.0):
    """The flexible feed chute looping out of the throat and down, segment by segment: the belt of linked rounds
    between two parkerized side rails, standing a little proud of it."""
    out = []
    cx, cy = CHUTE_C
    step = sweep / segments
    length = 2 * CHUTE_R * math.sin(math.radians(step / 2)) + 0.6
    for k in range(segments):
        seg = []
        top = cy + CHUTE_R
        K.box(seg, cx - length / 2, top - 0.6, BELT_Z[0], cx + length / 2, top + 0.6, BELT_Z[1], "brass", up="belt",
              down="shadow", north=None, south=None, name="feed_chute_belt" if k == 0 else None)
        d = 0.02 * (k % 2)
        for z0, z1 in ((BELT_Z[0] - 0.4 - d, BELT_Z[0] + d), (BELT_Z[1] - d, BELT_Z[1] + 0.4 + d)):
            K.box(seg, cx - length / 2 - 0.05, top - 0.9, z0, cx + length / 2 + 0.05, top + 0.95, z1, "park",
                  name="feed_chute_rail" if k == 0 and z0 < 12 else None)
        out += rotate(seg, rz(step * (k + 0.5)), (cx, cy, 0.0))
    return out


def ammo_box():
    """The ammo box slung under the receiver: the chute feeds its left side; a feed stub out of the back takes the
    line from a backpack."""
    out = []
    K.box(out, 1.4, CHUTE_C[1] - CHUTE_R - 1.5, 11.4, 2.6, CHUTE_C[1] - CHUTE_R + 1.5, 15.8, "blued_dark", name="box_feed_mouth")
    K.box(out, 2.4, -0.9, 10.6, 9.8, 3.4, 16.8, "olive", up=None, name="ammo_box")
    K.box(out, 2.2, 3.4, 10.4, 10.0, 4.3, 17.0, "olive_dark", name="ammo_box_lid")
    K.box(out, 5.6, 4.3, 12.0, 8.4, 4.7, 15.0, "park", up=None, down=None, name="ammo_box_hanger")
    K.box(out, 9.8, 1.6, 13.0, 10.15, 3.2, 14.4, "yellow", west=None, name="ammo_box_latch")
    K.box(out, 5.0, 0.4, 10.1, 7.2, 1.4, 10.6, "rubber", south=None, name="ammo_box_handle")
    K.box(out, 5.2, 0.6, 16.8, 7.0, 2.4, 18.8, "park", north=None, name="backpack_feed_stub")
    K.box(out, 4.9, 0.3, 17.8, 7.3, 2.7, 18.4, "yellow", name="feed_stub_collar")
    return out


def spade_grips():
    """The rear spade grips: a back plate, top and bottom bars, two upright rubber grips, a yellow butterfly trigger."""
    out = []
    K.box(out, 4.8, 5.0, 19.4, 11.2, 12.0, 20.2, "park", name="spade_back_plate")
    K.box(out, 4.4, 11.0, 20.2, 11.6, 12.0, 24.0, "park", north=None, name="spade_top_bar")
    K.box(out, 4.4, 4.2, 20.2, 11.6, 5.2, 24.0, "park", north=None, name="spade_bottom_bar")
    for x in (5.0, 11.0):
        K.box(out, x - 0.7, 5.2, 22.4, x + 0.7, 11.0, 23.8, "grip", up=None, down=None, name="spade_grip")
    K.box(out, 7.2, 10.4, 20.2, 8.8, 11.0, 23.2, "blued_dark", up=None, north=None, name="trigger_housing")
    K.box(out, 6.2, 9.4, 22.8, 9.8, 10.4, 23.4, "yellow", name="butterfly_trigger")
    return out


def body():
    return housing() + hand_guard() + receiver() + handle() + motor() + feeder() + chute() + ammo_box() + spade_grips()


# ============================================================================================================ mount


def mount_body():
    """The Hogfire mount: the housing and hand guard on a short receiver and motor, the chute dropping a quarter turn
    into an ammo can on the saddle plate, a pylon under the receiver, and the straps."""
    out = housing() + hand_guard() + receiver(z1=14.6) + motor(10.2, 14.2, 3) + feeder(10.4, 14.4) + chute(2, 90.0)
    ex = CHUTE_C[0] - CHUTE_R
    K.box(out, ex - 1.5, 4.9, 11.4, ex + 1.5, 6.1, 15.8, "blued_dark", name="can_feed_mouth")
    K.box(out, ex - 2.2, 1.5, 10.6, ex + 2.2, 4.9, 16.6, "olive", name="ammo_can")
    K.box(out, ex + 2.2, 2.4, 13.0, ex + 2.5, 4.0, 14.2, "yellow", west=None, name="ammo_can_latch")
    K.box(out, 6.4, 1.5, 9.8, 9.6, 4.6, 14.0, "park", up=None, name="pylon")
    K.box(out, ex - 2.6, 0.9, 8.0, 12.0, 1.5, 16.8, "olive_dark", name="saddle_plate")
    for z in (9.0, 15.0):
        K.box(out, ex - 2.8, 0.6, z, 12.2, 1.6, z + 0.8, "rubber", name="strap")
    return out


# ======================================================================================================= displays


def corners(elements):
    for e in elements:
        rot = element_rotation(e)
        f, t = e["from"], e["to"]
        for x in (f[0], t[0]):
            for y in (f[1], t[1]):
                for z in (f[2], t[2]):
                    p = [x, y, z]
                    if rot:
                        m, o = rot
                        p = [v + o[i] for i, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))]
                    yield p


def fit(elements, rot, size=15.0):
    """The display scale at which these elements, turned by rot, span `size` pixels of a 16-pixel slot."""
    m = display_matrix(rot)
    pts = [[sum(m[i][k] * p[k] for k in range(3)) for i in range(2)] for p in corners(elements)]
    span = max(max(p[i] for p in pts) - min(p[i] for p in pts) for i in range(2))
    return round(size / span, 3)


def gun_displays(elements, fp_scale):
    fp_rot = [0, -6, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, GRIP, FP_GRIP_TARGET), fp_scale)
    tp_rot = [TP_DROP, 0, 0]
    tp = display(tp_rot, placed(tp_rot, 0.6, GRIP, TP_GRIP_TARGET), 0.6)
    c = bounds_centre(elements)
    gui_rot = [20, -125, 0]
    gui_scale = fit(elements, gui_rot, 16.0)
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], 0.3, c, 2.0), 0.3),
        "fixed": display([0, -90, 0], centred([0, -90, 0], gui_scale * 1.25, c), gui_scale * 1.25),
    }


def part_displays(elements, rot=DIAGONAL):
    c = bounds_centre(elements)
    rot = list(rot)
    scale = fit(elements, rot)
    return {
        "gui": display(rot, centred(rot, scale, c), scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], scale * 0.7, c, 2.0), scale * 0.7),
        "fixed": display(rot, centred(rot, scale * 1.2, c), scale * 1.2),
        "thirdperson_righthand": display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8),
        "thirdperson_lefthand": display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8),
        "firstperson_righthand": display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8),
        "firstperson_lefthand": display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8),
    }


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


CREDIT = "tomyen minigun - generated by tools/gen_minigun.py"
everything = body() + barrels(0, True)
DISPLAYS = gun_displays(everything, 0.5)


def out_model(name, elements, displays=None):
    check(name, elements)
    check_elements(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, displays or DISPLAYS, credit=CREDIT))
    return ref(name)


gun_body = out_model("minigun", body())
cool = [out_model(f"minigun_barrels_{k}", barrels(-FRAME_STEP * k, False)) for k in range(FRAMES)]
hot = [out_model(f"minigun_barrels_hot_{k}", barrels(-FRAME_STEP * k, True)) for k in range(FRAMES)]
# What's drawn together mustn't fight either: each barrel frame over the body, and over the mount.
for k in range(FRAMES):
    for hot_frame in (False, True):
        check_elements(f"minigun frame {k}", body(), barrels(-FRAME_STEP * k, hot_frame))
        check_elements(f"mount frame {k}", mount_body(), barrels(-FRAME_STEP * k, hot_frame))


def turning(frames):
    return range_dispatch("tomyen:minigun_barrels", [(k - 0.5, frames[k]) for k in range(1, FRAMES)], frames[0])


HOT = 0.6
barrel_layer = range_dispatch("tomyen:minigun_heat", [(HOT, turning(hot))], turning(cool))
flashes = flash_models(MODELS, "minigun", MUZZLE, BY, DISPLAYS)
flash_layer = range_dispatch("tomyen:minigun_flash", [(k + 0.5, ref(f)) for k, f in enumerate(flashes)],
                             {"type": "minecraft:empty"})
write(os.path.join(ITEMS, "minigun.json"), {"model": composite(gun_body, barrel_layer, flash_layer)})

THREE_QUARTER = (20, -125, 0)

# The mount: drawn on the pig in no display context at all; in the inventory it shows one still model, centred.
mount = out_model("hogfire_gun_mount", mount_body())
icon = mount_body() + barrels(0, False)
out_model("hogfire_gun_mount_icon", icon, part_displays(icon, THREE_QUARTER))
write(os.path.join(ITEMS, "hogfire_gun_mount.json"), {"model": {
    "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [{"when": ["none"], "model": composite(mount, barrel_layer, flash_layer)}],
    "fallback": ref("hogfire_gun_mount_icon")}})

# Weapon Station parts: the turning group; the motor, feeder, chute and ammo box; the housing, receiver and grips.
for name, elements, rot in (("minigun_barrel_cluster", barrels(0, False), DIAGONAL),
                            ("minigun_feed_motor", motor() + feeder() + chute() + ammo_box(), (25, -150, 0)),
                            ("minigun_housing", housing() + hand_guard() + receiver() + handle() + spade_grips(), THREE_QUARTER)):
    out_model(name, elements, part_displays(elements, rot))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})

t = DISPLAYS["firstperson_righthand"]
print(f"minigun: {len(body())} body + {len(barrels(0, False))} turning elements ({len(barrels(0, True))} hot); "
      f"mount {len(mount_body())}")
print(f"   firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}; gui scale {DISPLAYS['gui']['scale'][0]}")
print(f"   grip {fp_point(DISPLAYS, GRIP)}  support {fp_point(DISPLAYS, SUPPORT)}  muzzle {fp_point(DISPLAYS, (BX, BY, MUZZLE))}")

# =========================================================================================================== sounds
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


def write_ogg(name, samples, gain=1.0):
    """Mono written as stereo (ffmpeg's built-in Vorbis encoder only writes stereo), so MinigunSound fades these with
    distance itself, as the flamethrower's roar does."""
    peak = max(abs(v) for v in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(math.tanh(1.4 * v / peak) / math.tanh(1.4) * 31000 * gain)) for v in samples)
    path = os.path.join(ASSETS, "sounds", name + ".ogg")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(frames)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "4", path],
                       check=True)
    print("wrote", path)


def motor(freqs, seed, whirr=0.5):
    """The motor and the barrels going round: a whine (with a fifth and an octave over it) following freqs, one value a
    sample, and a rush of air chopped at the rate the barrels pass (a sixth of the whine's)."""
    sr = random.Random(seed)
    air = lowpass(highpass([sr.uniform(-1, 1) for _ in freqs], 600), 3200)
    out, phase = [], 0.0
    for i, f in enumerate(freqs):
        phase += math.tau * f / RATE
        whine = math.sin(phase) + 0.35 * math.sin(phase * 1.5) + 0.25 * math.sin(phase * 2.0)
        chop = 0.5 + 0.5 * math.sin(phase / 6.0)
        out.append(whine * 0.5 + air[i] * whirr * (0.4 + 0.6 * chop) * min(1.0, f / 600.0))
    return out


def clunk(n, seed, at=0.0, gain=1.0):
    """The drive engaging or letting go: a dull knock."""
    sr = random.Random(seed)
    body = lowpass([sr.uniform(-1, 1) for _ in range(n)], 500)
    out = []
    for i in range(n):
        t = i / RATE - at
        out.append(0.0 if t < 0 else body[i] * math.exp(-t / 0.05) * 3.0 * gain + math.sin(math.tau * 95 * t) * math.exp(-t / 0.08) * gain)
    return out


TOP = 960.0                       # the whine at full speed, Hz


def spin_up(seconds=0.8):
    n = int(RATE * seconds)
    rise = [180.0 + (TOP - 180.0) * (1 - (1 - i / n) ** 2.2) for i in range(n)]
    out = [a + b for a, b in zip(motor(rise, 21), clunk(n, 22))]
    fade = int(RATE * 0.08)
    for i in range(fade):
        out[n - fade + i] *= 1.0 - 0.3 * i / fade
    return out


def spin(seconds=1.0):
    """Seamless when looped: the whine and the chop both go round a whole number of times a second."""
    return motor([TOP] * int(RATE * seconds), 23)


def spin_down(seconds=1.6):
    n = int(RATE * seconds)
    fall = [TOP * math.exp(-2.1 * i / n) for i in range(n)]
    out = motor(fall, 24)
    for i in range(n):
        out[i] *= (1.0 - i / n) ** 1.2
    return [a + b for a, b in zip(out, clunk(n, 25, at=seconds - 0.25, gain=0.35))]


def fire(seconds=1.0, rate=20):
    """Seamless when looped: `rate` shots a second, each a short crack and a thump, over the motor, so held fire is one
    tearing roar."""
    sr = random.Random(26)
    n = int(RATE * seconds)
    noise = [sr.uniform(-1, 1) for _ in range(n)]
    crack = highpass(noise, 2200)
    thump = lowpass(noise, 420)
    out = [v * 0.25 for v in spin(seconds)]
    gap = n // rate
    for k in range(rate):
        start = k * gap + sr.randint(0, gap // 8)
        g = 0.85 + sr.random() * 0.3
        # The last shots' tails wrap round to the start.
        for i in range(start, start + gap * 2):
            t, j = (i - start) / RATE, i % n
            out[j] += (crack[j] * math.exp(-t / 0.005) * 1.3 + thump[j] * math.exp(-t / 0.03) * 4.0
                       + math.sin(math.tau * 70 * t) * math.exp(-t / 0.025) * 0.8) * g
    return out


if "--models-only" in sys.argv:
    print("--models-only: skipping sounds")
elif shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping sounds")
else:
    write_ogg("minigun_spin_up", spin_up(), 0.75)
    write_ogg("minigun_spin", spin(), 0.6)
    write_ogg("minigun_fire", fire(), 0.95)
    write_ogg("minigun_spin_down", spin_down(), 0.7)
print("minigun assets written")
