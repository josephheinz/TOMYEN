"""Builds the energy weapons' assets: the Laser Rifle, the Railgun and the Ray Gun (each with the glowing parts that
tomyen:energy_glow lights up as it heats or charges), the Weapon Station parts (Laser Emitter, Laser Rifle Housing,
Railgun Rails), and the Copper Coil, Uranium Power Cell and Rail Slug sprites; the crafting recipes for coils and cells
(with recipe-book advancements), the laser and railgun damage types, the synthesised sounds (laser shot, railgun charge
and shot, the alien zap; with ffmpeg on the path) and the re-pitched vanilla ones, and language entries.

Model space is in pixels: each gun's bore runs along z at x = 8 with the muzzle toward -z, +x its right. GunDisplay,
GunHands and GunAim mirror the first-person transforms, grip points and sight printed at the end, so move them
together. Clean flat paint, one smooth ramp per material, no speckle. The Railgun and its glow layers are also checked
for z-fighting in world space (tools/coplanar.py), whatever their cubes' rotations.

Usage: python3 tools/gen_energy_weapons.py src/main/resources/assets/tomyen"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, ry, rz, mul, apply, look, write, model, ref, range_dispatch, composite, display,
                    display_matrix, placed, centred, bounds_centre, DIAGONAL, png, mix, sprite)
from coplanar import check_elements

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))

# Where long guns put the shooting hand (the M4's grip point, in the held item's space), and handguns (the Glock's).
FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
FP_PISTOL_TARGET = (-0.020 * 16, 0.131 * 16, -0.181 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
HIDDEN = display([0, 0, 0], [0, 0, 0], 0)


def flat(k, colour):
    return lambda x, y, w, h: k.rgb(colour)


def ramp(k, top, bottom):
    return lambda x, y, w, h: k.rgb(mix(top, bottom, y / max(1, h - 1)))


def glow_disc(k, centre, rim):
    return lambda x, y, w, h: k.rgb(mix(centre, rim, min(1.0, math.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / (w / 2))))


# ======================================================================================================= displays


def gun_displays(grip, fp_scale, fp_target=FP_GRIP_TARGET, yaw=-8, tp_scale=0.65):
    fp_rot = [0, yaw, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, fp_target), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], tp_scale, grip, TP_GRIP_TARGET), tp_scale)
    return {"thirdperson_righthand": tp, "thirdperson_lefthand": tp, "firstperson_righthand": fp, "firstperson_lefthand": fp}


def still_displays(elements, scale, rot=(0, -90, 0)):
    c = bounds_centre(elements)
    rot = list(rot)
    return {
        "gui": display(rot, centred(rot, scale, c), scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], scale * 0.75, c, 2.0), scale * 0.75),
        "fixed": display(rot, centred(rot, scale * 1.3, c), scale * 1.3),
    }


def part_displays(elements, scale, rot=DIAGONAL):
    c = bounds_centre(elements)
    d = still_displays(elements, scale, rot)
    hand = display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8)
    first = display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8)
    d.update({"thirdperson_righthand": hand, "thirdperson_lefthand": hand, "firstperson_righthand": first, "firstperson_lefthand": first})
    return d


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


def report(name, displays, grip, support=None, extra=""):
    t = displays["firstperson_righthand"]
    line = f"{name}: firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}; grip {fp_point(displays, grip)}"
    if support:
        line += f" support {fp_point(displays, support)}"
    print(line + extra)


def glow_levels(kit, name, parts, displays, thresholds, credit):
    """Writes the glow overlays (item/<name>_glow_<n>) with the gun's held displays; returns the energy_glow dispatch."""
    entries = []
    for i, (threshold, elements) in enumerate(zip(thresholds, parts)):
        check(f"{name}_glow_{i}", elements)
        overlay = dict(displays)
        for ctx in ("gui", "ground", "fixed"):
            overlay[ctx] = displays.get(ctx, HIDDEN)
        write(os.path.join(MODELS, f"{name}_glow_{i}.json"), model(kit, elements, overlay, credit=credit))
        entries.append((threshold, composite(ref(name), ref(f"{name}_glow_{i}"))))
    return range_dispatch("tomyen:energy_glow", entries, ref(name))


# =================================================================================================== laser rifle
L = Kit("laser_rifle", "item/laser_rifle", seed=2077)
L.swatch("panel", 0, 0, 4, 4, ramp(L, 0xDDE2E6, 0xAEB5BD))
L.swatch("metal", 4, 0, 8, 4, ramp(L, 0x4C535B, 0x31363C))
L.swatch("dark", 8, 0, 12, 4, flat(L, 0x202428))
L.swatch("copper", 12, 0, 16, 4, ramp(L, 0xE39256, 0xA65A2A))
L.swatch("rubber", 0, 4, 4, 8, flat(L, 0x1E1F21))
L.swatch("lens", 4, 4, 8, 8, glow_disc(L, 0xF0FFFF, 0x30C8F0), picture=True)
L.swatch("cell", 8, 4, 12, 8, glow_disc(L, 0xE8FFD0, 0x5CD832), picture=True)


def reticle(x, y, w, h):
    """The sight's window: a dark frame round clear glass, a cyan dot in the middle."""
    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
        return L.rgb(0x202428)
    if abs(x - (w - 1) / 2) <= 0.6 and abs(y - (h - 1) / 2) <= 0.6:
        return L.rgb(0x60F0FF)
    return (0, 0, 0, 0)


L.swatch("reticle", 12, 4, 16, 8, reticle, picture=True)
L.swatch("coil_warm", 0, 8, 4, 12, ramp(L, 0xFFB050, 0xE06A1E))
L.swatch("coil_hot", 4, 8, 8, 12, ramp(L, 0xFFF0C0, 0xFFB050))
L.save(TEX)

LBY = 9.4                    # the bore
L_GRIP = (8.0, 6.0, 9.4)
L_SUPPORT = (8.0, 8.0, -5.6)
L_SIGHT = (8.0, 12.8, 4.2)   # the middle of the sight's window
L_COILS = ((-11.6, -10.4), (-9.4, -8.2))


def laser_front():
    out = []
    L.rod(out, -13.0, -3.0, 1.3, "metal", cy=LBY, cap="dark", name="emitter_shroud")
    L.sticker(out, 7.05, LBY - 0.95, -13.03, 8.95, LBY + 0.95, -13.02, "lens", "north", emit=True, name="lens")
    for z0, z1 in L_COILS:
        L.rod(out, z0, z1, 1.6, "copper", cy=LBY, cap="copper", name="coil")
    return out


def laser_back():
    out = []
    L.chamfered(out, 6.4, 6.6, -3.0, 9.6, 11.4, 12.0, 0.8, "panel", cap="metal", name="body")
    L.box(out, 7.25, 3.4, 7.8, 8.75, 7.2, 10.2, "rubber", rot=("x", -15, (8.0, 7.2, 9.0)), name="grip")
    L.box(out, 7.75, 6.0, 6.2, 8.25, 6.6, 6.8, "metal", name="trigger")
    L.box(out, 6.9, 4.6, 1.5, 9.1, 6.6, 5.0, "metal", name="cell", up=None)
    for x0, x1, face in ((6.88, 6.89, "west"), (9.11, 9.12, "east")):
        L.sticker(out, x0, 4.9, 2.0, x1, 6.3, 4.5, "cell", face, emit=True)
    L.box(out, 7.0, 6.2, 12.0, 9.0, 10.6, 18.5, "panel", name="stock")
    L.box(out, 7.0, 6.2, 18.5, 9.0, 10.6, 19.2, "rubber", name="butt_pad")
    L.box(out, 7.3, 11.4, 2.5, 8.7, 11.9, 5.5, "metal", name="sight_base", down=None)
    L.box(out, 6.9, 11.9, 4.0, 9.1, 13.7, 4.4, "reticle", name="sight", up="dark", down=None, east="dark", west="dark")
    return out


def laser_glow(hot):
    out = []
    for z0, z1 in L_COILS:
        L.rod(out, z0 - 0.02, z1 + 0.02, 1.65, "coil_hot" if hot else "coil_warm", cy=LBY, emit=True)
    return out


L_CREDIT = "tomyen laser rifle - generated by tools/gen_energy_weapons.py"
laser = laser_front() + laser_back()
check("laser_rifle", laser)
laser_displays = gun_displays(L_GRIP, 0.55)
laser_displays.update(still_displays(laser, 0.42))
write(os.path.join(MODELS, "laser_rifle.json"), model(L, laser, laser_displays, credit=L_CREDIT))
write(os.path.join(ITEMS, "laser_rifle.json"), {"model": glow_levels(L, "laser_rifle", [laser_glow(False), laser_glow(True)], laser_displays,
                                                                     [0.45, 0.8], L_CREDIT)})
front = laser_front()
check("laser_emitter", front)
write(os.path.join(MODELS, "laser_emitter.json"), model(L, front, part_displays(front, 0.75), credit=L_CREDIT))
write(os.path.join(ITEMS, "laser_emitter.json"), {"model": ref("laser_emitter")})
back = laser_back()
check("laser_rifle_housing", back)
write(os.path.join(MODELS, "laser_rifle_housing.json"), model(L, back, part_displays(back, 0.5), credit=L_CREDIT))
write(os.path.join(ITEMS, "laser_rifle_housing.json"), {"model": ref("laser_rifle_housing")})
report("laser_rifle", laser_displays, L_GRIP, L_SUPPORT, f"; sight window {L_SIGHT}, mount front 4.0 top 11.4")

# ======================================================================================================= railgun
# A sleek white sci-fi rifle, after the user's side-view sketch. From the back: a long flat charcoal stock with a squared
# rubber butt and a white tube along its lowered front deck; a thumbhole loop running from the stock down and forward to
# a raked pistol grip, the trigger inside the open hollow, which is lined in white; a clean white receiver with a fire
# selector and two indicator dots, a pointed cheek piece over the stock, and a black block scope with a side turret and
# a top knob, overhanging forward with a slanted front. Two long white rails, segmented into panels by dark grooves,
# run forward with an even gap between them, each ending in a chamfered tip (the upper one sloping down, the lower one
# up). Black contact pads face each other across the gap, three per rail and a small one at each tip, and a dark vent
# panel hangs under the lower rail's front half. White and light grey, soft grey shading, charcoal and black accents.
# Charging (energy_glow): the pads light from dim teal to white-hot cyan, a reactor window at the back of the gap and the
# slots in the receiver's flanks glow with them, the selector's "on" dot lights, and near full charge arcs jump the gap.
R = Kit("railgun", "item/railgun", seed=1908, size=256)


def bands(k, rows, across=False):
    """A picture of bands, top to bottom (left to right if across): rows is a list of (fraction, colour or (start,
    end) ramp)."""
    def f(x, y, w, h):
        t, start = ((x + 0.5) / w if across else (y + 0.5) / h), 0.0
        for i, (frac, c) in enumerate(rows):
            if t <= start + frac or i == len(rows) - 1:
                if isinstance(c, tuple):
                    return k.rgb(mix(c[0], c[1], min(1.0, max(0.0, (t - start) / frac))))
                return k.rgb(c)
            start += frac
    return f


def disc(k, rings, bg=None):
    """A round picture: rings is a list of (outer radius as a fraction of the half-width, colour or (inner, outer)
    ramp); outside the last ring it's bg (transparent if None)."""
    def f(x, y, w, h):
        r = math.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / (w / 2)
        inner = 0.0
        for outer, c in rings:
            if r <= outer:
                if isinstance(c, tuple):
                    return k.rgb(mix(c[0], c[1], (r - inner) / max(1e-6, outer - inner)))
                return k.rgb(c)
            inner = outer
        return (0, 0, 0, 0) if bg is None else k.rgb(bg)
    return f


# ----------------------------------------------------------------------------------------------- the layout
X0, X1 = 6.8, 9.2              # the rails, side to side
UP = (9.8, 2.2, 1)             # upper rail: inner face y, height, which way is out
LO = (8.2, 2.1, -1)            # lower rail
MUZZLE = -15.8
UP_BACK, LO_BACK = 2.63, 1.4   # where each rail's back end sits (in the receiver, in the loop's front)
SEAMS = (-2.0, -4.38, -6.63, -8.8, -10.99)
GROOVE = 0.12
BEVEL = 0.3
NOSE = 0.48                    # the tips' blunt noses; the slope's band is NOSE * sqrt 2 thick
PADS = ((-1.79, 0.74), (-7.37, -4.42), (-12.84, -10.1))
TIP_PAD = (-15.6, -14.84)
PAD_T, PAD_R = 0.28, 0.14
RCV_X = (6.5, 9.5)             # the receiver
RCV_FRONT, RCV_TOP, RCV_BOT = 1.0, 11.67, 8.2
CHEEK = (6.0, 9.89, 9.9, 11.6)
STOCK = (8.1, 15.37, 7.04, 10.2)     # z front, z back, bottom, the deck
COMB = (12.27, 10.73)                # where the stock's comb starts, and its top
BUTT = 15.96
LOOP_X = (6.85, 9.15)
HOLE = (3.47, 7.68, 6.4, 7.6)         # the thumbhole: z front, z back, bottom, top
LOOP_BOT, POST_BACK = 5.7, 8.74
FRONT_X = (6.7, 9.3)                 # the loop's front, round the lower rail's back
GRIP = (2.75, 4.45, 3.45, 5.95)      # the pistol grip, before its rake: z front, z back, bottom, top
GRIP_RAKE = 12
R_GRIP = (8.0, 4.6, 3.9)
R_SUPPORT = (8.0, 5.45, -8.0)        # under the vent panel
SCOPE_MOUNT = (2.95, 12.0)           # the scope mount's front (z) and the plate it stands on (y)
R_SIGHT = (8.0, 13.4, 7.56)          # the middle of the scope's eyepiece
UNDER = (-11.1, -0.6, 5.45, 6.25)    # the vent panel: z front, z back, bottom, top
SEL = (2.7, 9.25, 5.25, 10.25)       # the selector decal: z front, y bottom, z back, y top
DOTS_Y = (SEL[3] - 0.27 * (SEL[3] - SEL[1]), SEL[3] - 0.73 * (SEL[3] - SEL[1]))   # on, off
DOT_Z = SEL[2] - 1.5
R_FP_SCALE, R_FP_YAW, R_FP_SHIFT = 0.46, 12, (-1.6, 0.6, 0.0)
R_ICON_SCALE = 0.62
ARC_Z = (-15.4, RCV_FRONT - 0.02)
ARC_Y = (LO[0] + 0.02, UP[0] - 0.02)


_cursor = [0, 0, 0]     # u, v, row height (UV units; a unit is 16 texels)


def sw(name, f, w=1, h=1, picture=True):
    """Adds a swatch w x h units big at the next free spot in the atlas."""
    u, v, rh = _cursor
    if u + w > 16:
        u, v, rh = 0, v + rh, 0
    R.swatch(name, u, v, u + w, v + h, f, picture=picture)
    _cursor[:] = [u + w, v, max(rh, h)]


# The palette.
W_HI, W_TOP, W_MID, W_LOW, W_SH = 0xFFFFFF, 0xF3F5F7, 0xE2E6EA, 0xCDD2D8, 0x9EA5AE      # white enamel
W_END, W_IN = 0xBDC3CA, 0xB0B6BE                                                    # groove walls, gap faces
TIP_SIDE, TIP_TOP = 0xDCE1E6, 0xEEF1F4                                              # the tip caps, a shade cooler
C_HI, C_TOP, C_BOT, C_LO = 0x6E737A, 0x4A4E54, 0x3A3D42, 0x26282C                   # charcoal
K_HI, K_TOP, K_BOT, K_LO = 0x4E535A, 0x26282C, 0x1B1C1F, 0x0E0F11                   # black
SEAM = 0x1A1B1E
GLOW = [(0x0E3F48, 0x082A30), (0x1C8A9C, 0x0C4A56), (0x6FEEFF, 0x18B4D0), (0xFFFFFF, 0x8FF4FF)]   # off .. white-hot

# Enamel and metal.
sw("w_side", bands(R, [(0.06, W_HI), (0.82, (W_TOP, W_LOW)), (0.06, W_SH), (0.06, 0x868E98)]))        # square lower edge
sw("w_side_lo", bands(R, [(0.07, W_HI), (0.06, 0xF7F8FA), (0.81, (W_TOP, W_LOW)), (0.06, W_SH)]))     # square upper edge
sw("w_body", bands(R, [(0.05, W_HI), (0.89, (W_TOP, W_LOW)), (0.06, W_SH)]))                            # tall panels
sw("w_top", bands(R, [(0.1, W_HI), (0.8, (0xF8F9FA, W_TOP)), (0.1, W_HI)], across=True))
sw("w_end", ramp(R, W_END, 0xA6ADB5))
sw("w_in", bands(R, [(0.08, W_LOW), (0.84, (W_IN, 0xA3AAB2)), (0.08, W_LOW)], across=True))
sw("w_edge", ramp(R, W_HI, 0xEEF1F4), picture=False)
sw("w_flat", ramp(R, W_TOP, W_MID), picture=False)
sw("tip_side", flat(R, TIP_SIDE), picture=False)
sw("tip_top", bands(R, [(0.1, W_HI), (0.8, (TIP_TOP, 0xE0E4E8)), (0.1, W_HI)], across=True))
sw("seam", flat(R, SEAM), picture=False)
sw("c_side", bands(R, [(0.05, C_HI), (0.89, (C_TOP, C_BOT)), (0.06, C_LO)]))
sw("c_top", bands(R, [(0.1, C_HI), (0.8, (0x52565C, C_TOP)), (0.1, C_HI)], across=True))
sw("c_flat", ramp(R, C_TOP, C_BOT), picture=False)
sw("c_edge", ramp(R, 0x80858C, 0x62676E), picture=False)
sw("c_end", ramp(R, C_BOT, C_LO))
sw("k_side", bands(R, [(0.07, K_HI), (0.86, (K_TOP, K_BOT)), (0.07, K_LO)]))
sw("k_top", bands(R, [(0.1, K_HI), (0.8, (0x2C2E32, K_TOP)), (0.1, K_HI)], across=True))
sw("k_flat", ramp(R, K_TOP, K_BOT), picture=False)
sw("k_edge", ramp(R, 0x5C6168, 0x45494F), picture=False)
sw("rubber", bands(R, [(0.05, 0x55595F), (0.9, (0x2C2E32, 0x1E2023)), (0.05, 0x111214)]))
sw("rubber_flat", ramp(R, 0x2C2E32, 0x1E2023), picture=False)
sw("lining", ramp(R, 0xF4F6F8, 0xDDE1E6), picture=False)


def frame_square(x, y, w, h):
    """White round the edges, charcoal inside: a hole's corner fillet (only one edge of it ever shows)."""
    d = min(x, y, w - 1 - x, h - 1 - y)
    return R.rgb(0xEEF1F4 if d < 6 else C_TOP)


sw("fillet", frame_square)
# Pads: black, the face toward the gap with a recessed emitter slot (dark teal) down its middle.


def pad_face(x, y, w, h):
    t = abs((x + 0.5) / w - 0.5) * 2               # across the pad
    if t > 0.86:
        return R.rgb(K_HI)
    if t > 0.5:
        return R.rgb(K_BOT)
    if t > 0.38:
        return R.rgb(0x08090A)
    return R.rgb(mix(0x10343A, 0x0A2227, abs((y + 0.5) / h - 0.5) * 2))


sw("pad_face", pad_face)
sw("pad_side", bands(R, [(0.15, K_HI), (0.85, (K_TOP, K_LO))]))
sw("pad_side_up", bands(R, [(0.85, (K_LO, K_TOP)), (0.15, K_HI)]))
# Glass: the scope's lenses, dark with a cool sheen.


def glass(x, y, w, h):
    u, v = (x + 0.5) / w, (y + 0.5) / h
    if min(u, v, 1 - u, 1 - v) < 0.08:
        return R.rgb(0x08090A)
    s = u + (1 - v)
    if 0.55 < s < 0.75:
        return R.rgb(0x5C8CA0)
    return R.rgb(mix(0x1A3440, 0x0A1218, v))


sw("glass", glass)
def _turret(x, y, w, h):
    """The turret's cap: a black rim, a dark face with a coin slot (two parallel diagonal lines), like the sketch."""
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = math.hypot(x - cx, y - cy) / (w / 2)
    if r > 1.0:
        return (0, 0, 0, 0)
    if r > 0.86:
        return R.rgb(0x0C0D0F)
    if r > 0.76:
        return R.rgb(0x5A5F66)
    d = ((x - cx) + (y - cy)) / (w / 2) / math.sqrt(2)      # across the slot
    along = ((x - cx) - (y - cy)) / (w / 2) / math.sqrt(2)
    if abs(along) < 0.5 and (0.08 < abs(d) < 0.2):
        return R.rgb(0x0C0D0F)
    return R.rgb(mix(0x34373C, 0x1E2023, (y + 0.5) / h))


sw("turret_cap", _turret)
sw("knob_cap", disc(R, [(0.35, (0x5A5F66, 0x3A3D42)), (0.55, 0x0C0D0F), (0.86, (0x34373C, 0x232528)), (1.0, 0x0C0D0F)]))
sw("dot_off", flat(R, 0x2A2D31), picture=False)
sw("dot_on", flat(R, 0x7FF6FF), picture=False)
sw("knurl", lambda x, y, w, h: R.rgb(0x3E4248 if (x // 2) % 2 else 0x1E2023))
# The reactor window at the back of the gap and the slots in the receiver's flanks, unlit: a dark recess with a lit lip.


def recess(x, y, w, h):
    if y == 0 or x == 0 or x == w - 1:
        return R.rgb(0x0C0D0F)
    if y == h - 1:
        return R.rgb(0x8A929C)
    return R.rgb(mix(0x10282E, 0x0A1418, y / (h - 1)))


sw("recess", recess)
for i, (hi, lo) in enumerate(GLOW[1:], 1):
    # A pad lit: brightest along the face toward the gap (the side's lower edge on the upper rail, its upper on the lower).
    sw("glow_face_%d" % i, lambda x, y, w, h, hi=hi, lo=lo: R.rgb(mix(hi, lo, min(1.0, abs((x + 0.5) / w - 0.5) * 2.2))))
    sw("glow_dn_%d" % i, ramp(R, lo, hi))
    sw("glow_up_%d" % i, ramp(R, hi, lo))
    sw("glow_win_%d" % i, lambda x, y, w, h, hi=hi, lo=lo: R.rgb(mix(hi, lo, min(1.0, abs((y + 0.5) / h - 0.5) * 2.0))))


def framed(face_w, face_h, outline, frame, field, slats=0):
    """A panel's side, drawn to the face's size in pixels: a dark outline, a lighter frame, a recessed dark field (its
    upper lip in shadow, the lower lit), with optional long vent slats."""
    def f(x, y, w, h):
        px, py = (x + 0.5) / w * face_w, (y + 0.5) / h * face_h
        e = min(px, face_w - px, py, face_h - py)
        if e < outline:
            return R.rgb(0x111214)
        if e < outline + frame:
            return R.rgb(mix(0x5C6168, 0x464A50, py / face_h))
        if min(py - outline - frame, face_h - outline - frame - py) < 0.05:
            return R.rgb(0x121315 if py < face_h / 2 else 0x6A6F76)
        if slats and int((py - outline - frame) / (face_h - 2 * (outline + frame)) * (2 * slats + 1)) % 2:
            return R.rgb(0x16171A)
        return R.rgb(mix(0x34373C, 0x2A2D31, py / face_h))
    return f


sw("under_side", framed(UNDER[1] - UNDER[0] - 0.65, UNDER[3] - UNDER[2], 0.05, 0.11, 0, slats=1), w=8)


def grille(x, y, w, h):
    """The vent panel's underside: dark slats across it."""
    u = (x + 0.5) / w
    if u < 0.12 or u > 0.88:
        return R.rgb(0x464A50)
    return R.rgb(0x111214 if (y // 3) % 2 else 0x34373C)


sw("grille", grille)


def selector_plate(face_w, face_h):
    """The selector on the receiver's flank: a rounded plate with the lever's slot and its two marks, and a short tick
    after each indicator dot standing in for the on/off labels. Drawn to the face's size; clear elsewhere."""
    def f(x, y, w, h):
        px, py = (x + 0.5) / w * face_w, (y + 0.5) / h * face_h
        # The plate: a rounded slot 1.0 x 0.42, at the left.
        cx, cy, hw, hh = 0.62, face_h / 2, 0.5, 0.21
        dx, dy = max(0.0, abs(px - cx) - (hw - hh)), abs(py - cy)
        r = math.hypot(dx, dy)
        if r <= hh:
            if r > hh - 0.06:
                return R.rgb(0x2A2D31)
            if abs(px - 0.36) < 0.08 and dy < 0.08:
                return R.rgb(0x2A2D31)                # the "o": a pivot
            return R.rgb(mix(0xDDE1E6, 0xC6CBD1, py / face_h))
        # Ticks after the dots (the dots themselves are little cubes).
        for ty in (face_h * 0.27, face_h * 0.73):
            if 1.7 < px < 2.1 and abs(py - ty) < 0.045:
                return R.rgb(0x6A7078)
        return (0, 0, 0, 0)
    return f


sw("selector", selector_plate(SEL[2] - SEL[0], SEL[3] - SEL[1]), w=2)


def arcs(level):
    """The arcs across the gap, side on, drawn over the gap's whole length (u = 0 at the back, the receiver; v = 0 at
    the top): jagged bolts jumping between each pair of pads, and at full charge a long arc snaking down the gap."""
    jag = random.Random(11 + level)
    za, zb, ya, yb = ARC_Z[0], ARC_Z[1], ARC_Y[0], ARC_Y[1]
    lines = []
    for p0, p1 in PADS + (TIP_PAD,):
        n = 1 if level == 2 else 2
        for k in range(n):
            z = p0 + (p1 - p0) * (k + 1) / (n + 1) + jag.uniform(-0.3, 0.3)
            pts, y = [], ya
            while y < yb:
                pts.append((z + jag.uniform(-0.18, 0.18), y))
                y += jag.uniform(0.12, 0.22)
            pts.append((z, yb))
            lines.append(pts)
    if level == 3:
        pts, z = [], zb
        while z > za:
            pts.append((z, (ya + yb) / 2 + jag.uniform(-0.32, 0.32)))
            z -= jag.uniform(0.35, 0.8)
        pts.append((za, (ya + yb) / 2))
        lines.append(pts)
    core, halo = (0.05, 0.13) if level == 2 else (0.08, 0.2)

    def seg_dist(p, a, b):
        ax, ay = b[0] - a[0], b[1] - a[1]
        t = max(0.0, min(1.0, ((p[0] - a[0]) * ax + (p[1] - a[1]) * ay) / max(1e-9, ax * ax + ay * ay)))
        return math.hypot(p[0] - a[0] - t * ax, p[1] - a[1] - t * ay)

    def f(x, y, w, h):
        p = (zb - (x + 0.5) / w * (zb - za), yb - (y + 0.5) / h * (yb - ya))
        d = min(seg_dist(p, a, b) for pts in lines for a, b in zip(pts, pts[1:]) if abs(a[0] - p[0]) < 1.2 or abs(b[0] - p[0]) < 1.2) \
            if any(abs(a[0] - p[0]) < 1.2 for pts in lines for a in pts) else 9.0
        if d <= core:
            return R.rgb(0xFFFFFF)
        if d <= halo:
            return R.rgb(mix(0xB8FAFF, 0x30D0EC, (d - core) / (halo - core)))
        return (0, 0, 0, 0)
    return f




def ry_(rail, v):
    """A height v above a rail's inner face (into the rail) as model y; negative v reaches into the gap."""
    return rail[0] + rail[2] * v


SAME = object()


def rbox(out, rail, x0, v0, z0, x1, v1, z1, mat, rot=None, inner=SAME, outer=SAME, **faces):
    """A box on a rail, its heights in v (see ry_); 'inner' and 'outer' name the faces toward and away from the gap
    (None drops one, as for the other faces)."""
    ya, yb = ry_(rail, v0), ry_(rail, v1)
    i_face, o_face = ("down", "up") if rail[2] > 0 else ("up", "down")
    faces[i_face] = mat if inner is SAME else inner
    faces[o_face] = mat if outer is SAME else outer
    return R.box(out, x0, min(ya, yb), z0, x1, max(ya, yb), z1, mat, rot=rot, **faces)


def prism(out, cx, cy, z0, z1, h, mat, caps=(True, True), axis="z", x=None, ends=None):
    """A square prism turned 45 degrees, centred on (cx, cy) along z (or on (z=cx, y=cy) along x when axis is 'x'),
    half-diagonal h: fills a bevel's or a chamfer's triangle. Its ends are set in a hair from z0 and z1."""
    s = h / math.sqrt(2)
    if axis == "z":
        return R.box(out, cx - s, cy - s, z0 + 0.02, cx + s, cy + s, z1 - 0.02, mat, rot=("z", 45, (cx, cy, z0)),
                     north=mat if caps[0] else None, south=mat if caps[1] else None)
    x0, x1 = x
    end = ends or mat
    return R.box(out, x0, cy - s, cx - s, x1, cy + s, cx + s, mat, rot=("x", 45, (x0, cy, cx)),
                 east=end if caps[1] else None, west=end if caps[0] else None)


def bevel_box(out, x0, y0, z0, x1, y1, z1, r, side, top, bottom, edge, cap, corners="tltrblbr", name=None, caps=(True, True)):
    """A box along z with the chosen long edges (tl, tr, bl, br) bevelled at 45 degrees by r, the bevels in a bright
    edge material so each catches a crisp highlight. Faces hidden inside are left off."""
    tl, tr, bl, br = ("tl" in corners), ("tr" in corners), ("bl" in corners), ("br" in corners)
    ends = dict(north=cap if caps[0] else None, south=cap if caps[1] else None)
    lx = x0 + r if (tl or bl) else x0
    rx_ = x1 - r if (tr or br) else x1
    e = R.box(out, lx, y0, z0, rx_, y1, z1, side, name=name, up=top, down=bottom,
              west=None if lx > x0 else side, east=None if rx_ < x1 else side, **ends)
    if lx > x0:
        R.box(out, x0, y0 + (r if bl else 0), z0, lx, y1 - (r if tl else 0), z1, side, east=None,
              up=None if tl else top, down=None if bl else bottom, **ends)
    if rx_ < x1:
        R.box(out, rx_, y0 + (r if br else 0), z0, x1, y1 - (r if tr else 0), z1, side, west=None,
              up=None if tr else top, down=None if br else bottom, **ends)
    for flag, cx, cy in ((tl, x0 + r, y1 - r), (tr, x1 - r, y1 - r), (bl, x0 + r, y0 + r), (br, x1 - r, y0 + r)):
        if flag:
            prism(out, cx, cy, z0, z1, r, edge, caps)
    return e


def west_uv_flip(e):
    """Mirrors the west face's picture so it reads the same way round as the east's (u = 0 at the back)."""
    f = e["faces"].get("west")
    if f:
        u0, v0, u1, v1 = f["uv"]
        f["uv"] = [u1, v0, u0, v1]


sw("arc_2", arcs(2), w=8)
sw("arc_3", arcs(3), w=8)
R.save(TEX)


# ------------------------------------------------------------------------------------------------- the rails


def rail(out, r, back):
    """One rail: a dark spine inside, white panels round it split by grooves (their outer long edges bevelled), and the
    chamfered tip cap. r is UP or LO."""
    yin, H, s = r
    side = "w_side" if s > 0 else "w_side_lo"
    outer_c = "tltr" if s > 0 else "blbr"
    # The spine, seen down the grooves.
    rbox(out, r, 6.95, 0.15, SEAMS[-1] - 0.4, 9.05, H - 0.15, back - 0.05, "seam", name="spine" if s > 0 else "spine_lo")
    # The panels, back to front, then the tip.
    joins = [back] + list(SEAMS)
    for i, (zb, zf) in enumerate(zip(joins, joins[1:])):
        z0, z1 = zf + GROOVE / 2, zb - (GROOVE / 2 if i else 0)
        panel(out, r, z0, z1, side, outer_c, caps=(True, i > 0 or s > 0), name=("upper" if s > 0 else "lower") + "_rail" if i == 0 else None)
    tip(out, r, SEAMS[-1] - GROOVE / 2, side, outer_c)
    for p in PADS + (TIP_PAD,):
        pad(out, r, *p)


def panel(out, r, z0, z1, side, outer_c, caps=(True, True), name=None):
    yin, H, s = r
    y_in, y_out = ry_(r, 0), ry_(r, H)
    if s > 0:
        bevel_box(out, X0, y_in, z0, X1, y_out, z1, BEVEL, side, "w_top", "w_in", "w_edge", "w_end", outer_c, name=name, caps=caps)
    else:
        bevel_box(out, X0, y_out, z0, X1, y_in, z1, BEVEL, side, "w_top", "w_in", "w_edge", "w_end", outer_c, name=name, caps=caps)


def tip(out, r, zt, side, outer_c):
    """The tip cap: full height back to where the slope starts, then a slope down (or up) to a blunt nose. The slope is a
    band turned 45 degrees resting on two stepped blocks; the band's lower end lands on the nose block's top edge."""
    yin, H, s = r
    nb, t = NOSE, NOSE * math.sqrt(2)
    p1 = MUZZLE + H - nb                       # where the slope meets the outer face
    # The full-height part, bevelled like the panels.
    if s > 0:
        bevel_box(out, X0, ry_(r, 0), p1, X1, ry_(r, H), zt, BEVEL, "tip_side", "tip_top", "w_in", "w_edge", "w_end", outer_c,
                  name="upper_tip", caps=(False, True))
    else:
        bevel_box(out, X0, ry_(r, H), p1, X1, ry_(r, 0), zt, BEVEL, "tip_side", "tip_top", "w_in", "w_edge", "w_end", outer_c,
                  name="lower_tip", caps=(False, True))
    # The steps under the band: the nose block, then one more.
    z1 = MUZZLE + 2 * nb - 0.04
    h1 = z1 - MUZZLE + 0.95 * nb
    rbox(out, r, X0, 0, MUZZLE, X1, nb, z1, "tip_side", inner="w_in", outer=None, north="tip_side", south=None)
    rbox(out, r, X0, 0, z1, X1, h1, p1, "tip_side", inner="w_in", outer=None, north=None, south=None)
    # The band: its outer face is the slope, from the nose's top edge to p1.
    length = (H - nb) * math.sqrt(2)
    yq = ry_(r, nb)
    if s > 0:
        R.box(out, X0 + 0.01, yq - t, MUZZLE, X1 - 0.01, yq, MUZZLE + length, "tip_side", rot=("x", -45, (8.0, yq, MUZZLE)),
              up="tip_top", down=None, north=None, south=None)
    else:
        R.box(out, X0 + 0.01, yq, MUZZLE, X1 - 0.01, yq + t, MUZZLE + length, "tip_side", rot=("x", 45, (8.0, yq, MUZZLE)),
              down="tip_top", up=None, north=None, south=None)
    # The angled seam parallel to the slope, on both flanks.
    off = 0.9                                     # how far back of the slope, along z
    for x0, x1, face in ((X0 - 0.02, X0 - 0.01, "west"), (X1 + 0.01, X1 + 0.02, "east")):
        zs = MUZZLE - nb + off                    # where the seam line crosses the inner face (v = 0)
        a = 0.08 * math.sqrt(2)                   # set in from each end so the corners stay on the flank
        y0 = ry_(r, 0)
        if s > 0:
            R.box(out, x0, y0 - 0.1, zs + a, x1, y0, zs + (H - BEVEL) * math.sqrt(2) - a, "seam", rot=("x", -45, (8.0, y0, zs)),
                  **{f: None for f in FACES_ALL if f != face})
        else:
            R.box(out, x0, y0, zs + a, x1, y0 + 0.1, zs + (H - BEVEL) * math.sqrt(2) - a, "seam", rot=("x", 45, (8.0, y0, zs)),
                  **{f: None for f in FACES_ALL if f != face})


FACES_ALL = ("north", "east", "south", "west", "up", "down")


def pad(out, r, za, zb):
    """A contact pad on the rail's inner face: a black block with its gap-side ends chamfered, the face toward the gap
    carrying an emitter slot."""
    yin, H, s = r
    side = "pad_side" if s > 0 else "pad_side_up"
    t, c = PAD_T, PAD_R
    rbox(out, r, 7.1, -t, za + c, 8.9, 0, zb - c, "k_flat", inner="pad_face", outer=None, east=side, west=side, north=None, south=None,
         name="pad")
    rbox(out, r, 7.11, -(t - c), za, 8.89, 0, zb, "k_flat", inner=None, outer=None, east=side, west=side, north=side, south=side)
    for zc in (za + c, zb - c):
        prism(out, zc, ry_(r, -(t - c)), 0, 0, c, "k_edge", axis="x", x=(7.12, 8.88))


def pad_glow(out, r, za, zb, level):
    """A lit pad: a shell just over the pad's block, its faces glowing (brightest toward the gap)."""
    yin, H, s = r
    t, c = PAD_T, PAD_R
    rbox(out, r, 7.08, -t - 0.02, za + c, 8.92, 0, zb - c, "glow_face_%d" % level, inner="glow_face_%d" % level, outer=None,
         east=("glow_dn_%d" if s > 0 else "glow_up_%d") % level, west=("glow_dn_%d" if s > 0 else "glow_up_%d") % level,
         north=None, south=None, emit=True)


def under_panel(out):
    """The dark vent panel hung under the lower rail's front half, its back end chamfered."""
    zf, zb, yb, yt = UNDER
    c = 0.65
    e = R.box(out, 7.0, yb, zf, 9.0, yt, zb - c, "c_flat", east="under_side", west="under_side", down="grille", north="c_side", up=None,
              south=None, name="vent_panel")
    west_uv_flip(e)
    prism(out, zb - c, yb + c, 0, 0, c, "c_edge", axis="x", x=(7.01, 8.99))


def rail_assembly():
    """Both rails with their pads and the vent panel: the Railgun Rails part, and the front of the gun."""
    out = []
    rail(out, UP, UP_BACK)
    rail(out, LO, LO_BACK)
    under_panel(out)
    return out


# ------------------------------------------------------------------------------------------------ the body


def receiver(out):
    x0, x1 = RCV_X
    # The main block: bevelled along its top, its front-bottom corner chamfered at the back of the gap.
    c = 0.5
    bevel_box(out, x0, RCV_BOT + c, RCV_FRONT, x1, RCV_TOP, CHEEK[0], BEVEL, "w_body", "w_top", None, "w_edge", "w_end", "tltr",
              name="receiver")
    R.box(out, x0, RCV_BOT, RCV_FRONT + c, x1, RCV_BOT + c, CHEEK[0], "w_body", up=None, down="w_in", north=None, south=None)
    prism(out, RCV_FRONT + c, RCV_BOT + c, 0, 0, c, "w_edge", axis="x", x=(x0 + 0.01, x1 - 0.01))
    # Behind it, under the cheek piece, down to the loop.
    R.box(out, x0, RCV_BOT, CHEEK[0], x1, CHEEK[2], STOCK[0], "w_body", north=None, up=None, down="w_in", south="w_end",
          name="receiver_rear")
    # The cheek piece: narrower, pointed at the back over the stock's deck.
    zf, zb, yb, yt = CHEEK
    ch = 0.75
    bevel_box(out, 6.6, yb, zf, 9.4, yt, zb - ch, BEVEL, "w_body", "w_top", "w_in", "w_edge", "w_end", "tltr", name="cheek",
              caps=(False, False))
    R.box(out, 6.6, yb, zb - ch, 9.4, yt - ch, zb, "w_body", north=None, up=None, south="w_end")
    prism(out, zb - ch, yt - ch, 0, 0, ch, "w_edge", axis="x", x=(6.61, 9.39))
    # The scope's base plate on top.
    R.box(out, 6.9, RCV_TOP, UP_BACK, 9.1, UP[0] + UP[1], 5.85, "w_flat", up="w_top", east="w_side_lo", west="w_side_lo",
          down=None, north=None, name="scope_plate")
    # The reactor window at the back of the gap, and a slot low in each flank (glowing as it charges).
    R.sticker(out, 7.2, 8.78, RCV_FRONT - 0.02, 8.8, 9.62, RCV_FRONT - 0.01, "recess", "north", name="reactor_window")
    for xa, xb, face in ((x0 - 0.02, x0 - 0.01, "west"), (x1 + 0.01, x1 + 0.02, "east")):
        R.sticker(out, xa, 8.62, 1.75, xb, 8.98, 3.35, "recess", face)
    # The fire selector, its lever, and the on/off dots, both flanks.
    for sx, xa, xb, face in ((-1, x0 - 0.02, x0 - 0.01, "west"), (1, x1 + 0.01, x1 + 0.02, "east")):
        e = R.sticker(out, xa, SEL[1], SEL[0], xb, SEL[3], SEL[2], "selector", face, name="selector" if sx > 0 else None)
        if face == "west":
            west_uv_flip(e)
        px = (x1, x1 + 0.1) if sx > 0 else (x0 - 0.1, x0)
        cz, cy = SEL[2] - 0.62, (SEL[1] + SEL[3]) / 2
        R.box(out, px[0], cy - 0.06, cz - 0.42, px[1], cy + 0.06, cz + 0.02, "k_flat", rot=("x", 28, (8.0, cy, cz)),
              **{"east" if sx > 0 else "west": "k_flat", "west" if sx > 0 else "east": None})
        dx = (x1, x1 + 0.06) if sx > 0 else (x0 - 0.06, x0)
        for k, dy in enumerate(DOTS_Y):
            R.box(out, dx[0], dy - 0.11, DOT_Z - 0.11, dx[1], dy + 0.11, DOT_Z + 0.11, "dot_off",
                  **{"east" if sx > 0 else "west": "dot_off", "west" if sx > 0 else "east": None},
                  name=("dot_on" if k == 0 else "dot_off") if sx > 0 else None)


def stock(out):
    zf, zb, yb, deck = STOCK
    bevel_box(out, X0, yb, zf, X1, deck, zb, BEVEL, "c_side", "c_top", "c_flat", "c_edge", "c_end", name="stock", caps=(True, False))
    bevel_box(out, X0, deck, COMB[0], X1, COMB[1], zb, 0.25, "c_side", "c_top", None, "c_edge", "c_end", "tltr", name="comb",
              caps=(True, False))
    bevel_box(out, 6.7, yb - 0.14, zb, 9.3, COMB[1] + 0.12, BUTT, BEVEL, "rubber", "rubber", "rubber", "rubber_flat", "rubber_flat",
              name="butt_pad")
    # The white tube along the deck, into the cheek piece.
    R.rod(out, CHEEK[1] - 0.05, COMB[0], 0.38, "w_flat", cy=deck + 0.38, cap="w_end", caps=(False, False), name="tube")


def loop(out):
    """The thumbhole loop: a band under the receiver, a post down from the stock, a band along the bottom, a wider
    front round the lower rail's back end; the hollow lined in white, its corners filleted."""
    lx0, lx1 = LOOP_X
    hf, hb, hy0, hy1 = HOLE
    R.box(out, lx0, hy1, hf, lx1, RCV_BOT, hb, "c_side", down="lining", up=None, north=None, south=None, name="loop_top")
    R.box(out, lx0, LOOP_BOT, hf, lx1, hy0, hb, "c_side", up="lining", down="c_flat", north=None, south=None, name="loop_bottom")
    # The rear post, its lower back corner chamfered, with a fillet up into the stock's underside.
    ch = 0.6
    R.box(out, lx0, LOOP_BOT, hb, lx1, RCV_BOT, POST_BACK - ch, "c_side", north="lining", up=None, down="c_flat", south=None,
          name="loop_post")
    R.box(out, lx0, LOOP_BOT + ch, POST_BACK - ch, lx1, STOCK[2], POST_BACK, "c_side", north=None, up=None, down=None, south="c_end")
    prism(out, POST_BACK - ch, LOOP_BOT + ch, 0, 0, ch, "c_edge", axis="x", x=(lx0 + 0.01, lx1 - 0.01))
    prism(out, POST_BACK, STOCK[2], 0, 0, 0.45, "c_edge", axis="x", x=(lx0 + 0.02, lx1 - 0.02))
    # The front, wider, round the lower rail; its lower front corner chamfered.
    fx0, fx1 = FRONT_X
    R.box(out, fx0, LOOP_BOT, RCV_FRONT + ch, fx1, RCV_BOT, hf, "c_side", south="lining", up=None, down="c_flat", north=None,
          name="loop_front")
    R.box(out, fx0, LOOP_BOT + ch, RCV_FRONT, fx1, RCV_BOT, RCV_FRONT + ch, "c_side", south=None, up=None, down=None,
          north="c_end")
    prism(out, RCV_FRONT + ch, LOOP_BOT + ch, 0, 0, ch, "c_edge", axis="x", x=(fx0 + 0.01, fx1 - 0.01))
    # The hollow's corner fillets and the white rim round it on both flanks.
    for zc, yc in ((hf, hy0), (hf, hy1), (hb, hy0), (hb, hy1)):
        prism(out, zc, yc, 0, 0, 0.32, "lining", axis="x", x=(lx0 + 0.03, lx1 - 0.03), ends="fillet")
    rim = 0.18
    for xa, xb, face, fxa, fxb in ((lx0 - 0.02, lx0 - 0.01, "west", fx0 - 0.02, fx0 - 0.01), (lx1 + 0.01, lx1 + 0.02, "east", fx1 + 0.01, fx1 + 0.02)):
        R.sticker(out, xa, hy1, hf, xb, hy1 + rim, hb, "lining", face)
        R.sticker(out, xa, hy0 - rim, hf, xb, hy0, hb, "lining", face)
        R.sticker(out, xa, hy0 - rim, hb, xb, hy1 + rim, hb + rim, "lining", face)
        R.sticker(out, fxa, hy0 - rim, hf - rim, fxb, hy1 + rim, hf, "lining", face)
    # The trigger, hanging into the hollow.
    R.box(out, 7.85, hy0 + 0.55, 6.1, 8.15, hy1, 6.45, "k_flat", rot=("x", -12, (8.0, hy1, 6.3)), up=None, name="trigger")
    R.box(out, 7.87, hy0 + 0.35, 5.95, 8.13, hy0 + 0.6, 6.3, "k_flat", rot=("x", -12, (8.0, hy1, 6.3)))


def grip(out):
    """The pistol grip under the loop, raked back, its four vertical edges bevelled; white down its front, a flared
    charcoal base. Built lying along z, then stood up and raked."""
    g = []
    zf, zb, yb, yt = GRIP
    L = yt - yb
    # Lying down: its length along z (0..L), its depth along -y (so standing it up puts the front at zf).
    bevel_box(g, 7.15, -zb, 0.0, 8.85, -zf, L, 0.3, "c_side", "w_body", "c_side", "c_edge", "c_end", caps=(True, False),
              name="grip")
    R.box(g, 7.05, -zb - 0.15, -0.25, 8.95, -zf + 0.15, 0.0, "c_flat", north="c_end", south=None, east="c_side", west="c_side",
          up="c_edge", down="c_side")
    rotate(g, rx(-90), (8.0, 0.0, 0.0))
    move(g, dy=yb)
    # A palm swell filling the corner behind the grip's top, under the loop.
    prism(g, zb, LOOP_BOT, 0, 0, 0.75, "c_edge", axis="x", x=(7.3, 8.7), ends="c_side")
    rotate(g, rx(-GRIP_RAKE), (8.0, yt, (zf + zb) / 2))
    out += g


def scope(out):
    mz, top = SCOPE_MOUNT
    R.box(out, 7.3, top, mz, 8.7, 12.8, 5.6, "k_side", up=None, down=None, name="scope_mount")
    bevel_box(out, 7.05, 12.8, 1.3, 8.95, 14.0, 6.95, 0.25, "k_side", "k_top", "k_flat", "k_edge", "k_flat", name="scope")
    R.box(out, 7.2, 12.95, 6.95, 8.8, 13.85, 7.55, "k_side", up="k_top", north=None, south="k_flat", name="eyepiece")
    R.sticker(out, 7.35, 13.05, 7.56, 8.65, 13.75, 7.57, "glass", "south", name="eyepiece_glass")
    # The hood overhanging forward, its front slanted (the lower edge set back).
    c = 0.35
    R.box(out, 7.15, 12.95, -0.1 + c, 8.85, 13.7, 1.3, "k_side", up="k_top", down="k_flat", north=None, south=None, name="hood")
    R.box(out, 7.15, 12.95 + c, -0.1, 8.85, 13.7, -0.1 + c, "k_side", up="k_top", down=None, north="k_flat", south=None)
    prism(out, -0.1 + c, 12.95 + c, 0, 0, c, "k_edge", axis="x", x=(7.16, 8.84))
    R.sticker(out, 7.35, 13.36, -0.12, 8.65, 13.64, -0.11, "glass", "north", name="objective")
    # The turrets on the flanks (the sketch's round dial), and the knob on top.
    for sx in (-1, 1):
        t = []
        a, b = (8.95, 9.5) if sx > 0 else (6.5, 7.05)
        R.rod(t, a, b, 0.64, "k_side", cx=0.0, cy=12.86, cap="k_flat", name="turret" if sx > 0 else None)
        rotate(t, ry(90), (0.0, 12.86, 0.0))
        move(t, dz=4.25)
        out += t
        if sx > 0:
            R.sticker(out, 9.51, 12.22, 3.61, 9.52, 13.5, 4.89, "turret_cap", "east")
        else:
            R.sticker(out, 6.48, 12.22, 3.61, 6.49, 13.5, 4.89, "turret_cap", "west")
    k = []
    R.rod(k, 14.0, 14.42, 0.3, "knurl", cx=8.0, cy=0.0, cap="k_flat", caps=(False, True), name="knob")
    rotate(k, rx(-90), (8.0, 0.0, 0.0))
    move(k, dz=5.6)
    out += k
    R.sticker(out, 7.7, 14.43, 5.3, 8.3, 14.44, 5.9, "knob_cap", "up")


def railgun_body():
    out = []
    receiver(out)
    stock(out)
    loop(out)
    grip(out)
    scope(out)
    return out


def railgun_glow(level):
    """Charging: the pads light (shells just over them), the reactor window and the flank slots glow with them, the "on"
    dot lights, and from half charge arcs jump the gap."""
    out = []
    for r in (UP, LO):
        for p in PADS + (TIP_PAD,):
            pad_glow(out, r, p[0], p[1], level)
    win = "glow_win_%d" % level
    R.sticker(out, 7.2, 8.78, RCV_FRONT - 0.04, 8.8, 9.62, RCV_FRONT - 0.03, win, "north", emit=True)
    x0, x1 = RCV_X
    for xa, xb, face in ((x0 - 0.04, x0 - 0.03, "west"), (x1 + 0.03, x1 + 0.04, "east")):
        R.sticker(out, xa, 8.62, 1.75, xb, 8.98, 3.35, win, face, emit=True)
    on = DOTS_Y[0]
    R.sticker(out, x1 + 0.07, on - 0.12, DOT_Z - 0.12, x1 + 0.08, on + 0.12, DOT_Z + 0.12, "dot_on", "east", emit=True)
    R.sticker(out, x0 - 0.08, on - 0.12, DOT_Z - 0.12, x0 - 0.07, on + 0.12, DOT_Z + 0.12, "dot_on", "west", emit=True)
    if level >= 2:
        e = R.box(out, 8.0, ARC_Y[0], ARC_Z[0], 8.0, ARC_Y[1], ARC_Z[1], "arc_%d" % level, emit=True,
                  north=None, south=None, up=None, down=None)
        west_uv_flip(e)
    return out


R_CREDIT = "tomyen railgun - generated by tools/gen_energy_weapons.py"
railgun = rail_assembly() + railgun_body()
check("railgun", railgun)
check_elements("railgun", railgun)
R_GLOWS = [railgun_glow(1), railgun_glow(2), railgun_glow(3)]
for i, layer in enumerate(R_GLOWS):
    check_elements(f"railgun+glow_{i}", railgun, layer)
# Turned a touch toward the middle of the screen and set a little further in, so the rails and the gap between them
# show in first person.
railgun_displays = gun_displays(R_GRIP, R_FP_SCALE, (FP_GRIP_TARGET[0] + R_FP_SHIFT[0], FP_GRIP_TARGET[1] + R_FP_SHIFT[1],
                                                      FP_GRIP_TARGET[2] + R_FP_SHIFT[2]), yaw=R_FP_YAW)
# The icon, frame and dropped item at a shallow three-quarters, like a product shot: mostly the left side (the gap
# between the rails shows; the flanks match), the muzzle a little toward the viewer, seen from a little above, the
# muzzle raised toward the slot's upper left to fill it.
R_ICON = mul(rz(-38), display_matrix([14, 108, 0]))
railgun_displays.update(still_displays(railgun, R_ICON_SCALE, rot=look(apply(R_ICON, [0, 0, -1]), apply(R_ICON, [0, 1, 0]))))
write(os.path.join(MODELS, "railgun.json"), model(R, railgun, railgun_displays, credit=R_CREDIT))
write(os.path.join(ITEMS, "railgun.json"), {"model": glow_levels(R, "railgun", R_GLOWS, railgun_displays, [0.15, 0.5, 0.9], R_CREDIT)})
rail_part = rail_assembly()
check("railgun_rails", rail_part)
check_elements("railgun_rails", rail_part)
write(os.path.join(MODELS, "railgun_rails.json"), model(R, rail_part, part_displays(rail_part, 0.5), credit=R_CREDIT))
write(os.path.join(ITEMS, "railgun_rails.json"), {"model": ref("railgun_rails")})
report("railgun", railgun_displays, R_GRIP, R_SUPPORT,
       f"; scope eyepiece {R_SIGHT}, mount front {SCOPE_MOUNT[0]} top {SCOPE_MOUNT[1]}")
print("railgun cubes:", len(railgun), "+ glow", [len(g) for g in R_GLOWS])


# ======================================================================================================= ray gun
G = Kit("ray_gun", "item/ray_gun", seed=1953)
G.swatch("silver", 0, 0, 4, 4, ramp(G, 0xE6EAEE, 0xA9B1BB))
G.swatch("alloy", 4, 0, 8, 4, ramp(G, 0x857AA6, 0x4C4468))
G.swatch("alloy_dark", 8, 0, 12, 4, flat(G, 0x2E2840))
G.swatch("tip", 12, 0, 16, 4, glow_disc(G, 0xF0FFE0, 0x6CE83C), picture=True)
G.swatch("ring_warm", 0, 4, 4, 8, ramp(G, 0xB8FF8C, 0x6CE83C))
G.swatch("ring_hot", 4, 4, 8, 8, ramp(G, 0xF4FFE8, 0xB8FF8C))
G.save(TEX)

GBY = 10.0
G_GRIP = (8.0, 7.0, 5.3)
G_RINGS = ((-4.6, -4.0, 1.5), (-2.8, -2.3, 1.2))


def ray_gun():
    out = []
    G.chamfered(out, 6.9, 8.6, -1.0, 9.1, 11.4, 6.0, 0.6, "silver", cap="silver", name="body")
    G.rod(out, 2.4, 6.4, 1.7, "alloy", cy=GBY, cap="alloy_dark", name="bulb")
    G.rod(out, -6.0, -1.0, 0.75, "silver", cy=GBY, name="barrel", caps=(False, True))
    for z0, z1, r in G_RINGS:
        G.rod(out, z0, z1, r, "alloy", cy=GBY, name="ring")
    G.rod(out, -7.0, -6.0, 1.0, "tip", cy=GBY, emit=True, name="tip")
    G.box(out, 7.35, 5.3, 4.2, 8.65, 8.8, 6.2, "alloy_dark", rot=("x", -18, (8.0, 8.8, 5.2)), name="grip")
    G.box(out, 7.75, 7.6, 2.6, 8.25, 8.6, 3.1, "silver", name="trigger")
    return out


def ray_glow(hot):
    out = []
    for z0, z1, r in G_RINGS:
        G.rod(out, z0 - 0.02, z1 + 0.02, r + 0.05, "ring_hot" if hot else "ring_warm", cy=GBY, emit=True)
    return out


G_CREDIT = "tomyen ray gun - generated by tools/gen_energy_weapons.py"
gun = ray_gun()
check("ray_gun", gun)
ray_displays = gun_displays(G_GRIP, 0.65, FP_PISTOL_TARGET, yaw=-6)
ray_displays.update(still_displays(gun, 0.75))
write(os.path.join(MODELS, "ray_gun.json"), model(G, gun, ray_displays, credit=G_CREDIT))
write(os.path.join(ITEMS, "ray_gun.json"), {"model": glow_levels(G, "ray_gun", [ray_glow(False), ray_glow(True)], ray_displays,
                                                                 [0.4, 0.8], G_CREDIT)})
report("ray_gun", ray_displays, G_GRIP)

# ======================================================================================================= sprites
ITEM_TEX = os.path.join(TEX, "item")


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def flat_item(name, rows, pal):
    sprite(os.path.join(ITEM_TEX, name + ".png"), rows, {k: rgba(v) for k, v in pal.items()})
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


# Copper wire wound on a black rubber former, seen end-on at a slant.
flat_item("copper_coil", [
    "................",
    "................",
    ".....oooooo.....",
    "...oorrrrrroo...",
    "..ohhcccccccdo..",
    "..orccccccccdo..",
    ".ohhcccccccccdo.",
    ".orcccrrrrcccdo.",
    ".ohccr....rccdo.",
    ".orccr....rcddo.",
    ".ohcccrrrrccddo.",
    "..occcccccccdo..",
    "..ohccccccdddo..",
    "...oocccdddoo...",
    ".....oooooo.....",
    "................",
], {"o": 0x3A1E0C, "h": 0xF4B07A, "c": 0xD27A40, "d": 0x9A4E22, "r": 0x1E1F21})

# A stubby steel cell with a window on its glowing uranium, the contacts on top.
flat_item("uranium_power_cell", [
    "................",
    "......oooo......",
    "......owwo......",
    "....oooooooo....",
    "....ohllllmo....",
    "....ohllllmo....",
    "....oggggggo....",
    "....ogeeeegd....",
    "....ogeeeegd....",
    "....ogeeeegd....",
    "....oggggggo....",
    "....ohllllmo....",
    "....ohllllmo....",
    "....ommmmmmo....",
    ".....oooooo.....",
    "................",
], {"o": 0x1C2026, "w": 0xD9DEE3, "h": 0xE4E8EC, "l": 0xB4BCC6, "m": 0x7E8792, "g": 0x3A8A22, "e": 0x9CFF6A, "d": 0x1C3A14})


def slug(name, body, light, dark):
    """A rail slug lying on the diagonal: a dart of metal with a copper driving band."""
    flat_item(name, [
        "................",
        "................",
        "............oo..",
        "...........ohdo.",
        "..........ohbdo.",
        ".........ohbdo..",
        "........ohbdo...",
        ".......ohbdo....",
        "......occco.....",
        ".....occco......",
        "....ohbdo.......",
        "...ohbdo........",
        "..ohbdo.........",
        "..obdo..........",
        "..ooo...........",
        "................",
    ], {"o": 0x14171B, "h": light, "b": body, "d": dark, "c": 0xD27A40})


slug("steel_rail_slug", 0xB4BCC6, 0xE4E8EC, 0x7E8792)
slug("tungsten_rail_slug", 0x5E646C, 0x8A9098, 0x3A3E44)

# ========================================================================================================= data


def advancement(name, ingredient, category):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def shaped(name, pattern, key, result, count, category, unlock):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, unlock, category)


# Copper wound round a rubber former: two coils.
shaped("copper_coil", [" C ", "CRC", " C "], OrderedDict([("C", "minecraft:copper_ingot"), ("R", "tomyen:rubber")]),
       "tomyen:copper_coil", 2, "misc", "tomyen:rubber")
# Steel plate casing, a chip to regulate it, copper contacts, and the uranium: two cells from a piece of enriched
# uranium, four from a fuel rod.
CELL_KEY = OrderedDict([("S", "tomyen:steel_plate"), ("C", "tomyen:computer_chip"), ("I", "minecraft:copper_ingot")])
shaped("uranium_power_cell", ["SCS", "IUI", "SIS"], OrderedDict(list(CELL_KEY.items()) + [("U", "tomyen:enriched_uranium")]),
       "tomyen:uranium_power_cell", 2, "equipment", "tomyen:enriched_uranium")
shaped("uranium_power_cell_from_fuel_rod", ["SCS", "IUI", "SIS"], OrderedDict(list(CELL_KEY.items()) + [("U", "tomyen:uranium_fuel_rod")]),
       "tomyen:uranium_power_cell", 4, "equipment", "tomyen:uranium_fuel_rod")

for name in ("laser", "railgun_slug"):
    write(os.path.join(DATA, "damage_type", name + ".json"), OrderedDict([
        ("exhaustion", 0.1), ("message_id", "tomyen." + name), ("scaling", "when_caused_by_living_non_player")]))
# A slug is a projectile. (A laser isn't fire damage, though it sets things alight: a fire-proof UFO still feels it.)
for tag, entry in (("is_projectile", "tomyen:railgun_slug"),):
    path = os.path.normpath(os.path.join(DATA, "..", "minecraft", "tags", "damage_type", tag + ".json"))
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})

# ======================================================================================================= sounds
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
    """Stereo only (ffmpeg's Vorbis encoder), like the other gun sounds."""
    peak = max(abs(v) for v in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(math.tanh(1.3 * v / peak) / math.tanh(1.3) * 30000 * gain)) for v in samples)
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


def sweep(seconds, f0, f1, decay, harmonics=(1.0,), tremolo=0.0):
    """A tone sliding exponentially from f0 to f1, dying away."""
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        f = f0 * (f1 / f0) ** (t / seconds)
        phase += math.tau * f / RATE
        tone = sum(a * math.sin((k + 1) * phase) for k, a in enumerate(harmonics))
        env = math.exp(-t / decay) * min(1.0, t / 0.003) * (1.0 + tremolo * math.sin(math.tau * 40 * t))
        out.append(tone * env)
    return out


def ray_zap():
    """The classic pew: a bright tone diving down."""
    return sweep(0.32, 1900, 260, 0.09, (1.0, 0.3, 0.12), tremolo=0.2)


def laser_shot():
    """A sharp laser crack: a fast dive with a buzzy edge and a hiss of noise at the front."""
    sr = random.Random(7)
    tone = sweep(0.3, 2600, 520, 0.06, (1.0, 0.5, 0.33, 0.25))
    hiss = highpass([sr.uniform(-1, 1) for _ in tone], 3000)
    return [t + h * math.exp(-i / RATE / 0.012) * 0.6 for i, (t, h) in enumerate(zip(tone, hiss))]


def railgun_charge(seconds=1.6):
    """The capacitors charging: a whine climbing and swelling, crackling more as it nears full."""
    sr = random.Random(11)
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        p = t / seconds
        f = 180 * (2800 / 180) ** (p ** 1.3)
        phase += math.tau * f / RATE
        crackle = sr.uniform(-1, 1) * 0.6 if sr.random() < 0.002 + 0.02 * p * p else 0.0
        out.append((math.sin(phase) + 0.3 * math.sin(2 * phase)) * (0.25 + 0.75 * p) + crackle)
    return out


def railgun_shot(seconds=1.2):
    """The slug going: a hard supersonic crack, a deep thump under it, a ringing of the rails and a long rumbling tail."""
    sr = random.Random(13)
    n = int(RATE * seconds)
    noise = [sr.uniform(-1, 1) for _ in range(n)]
    crack = highpass(noise, 2500)
    rumble = lowpass(noise, 300)
    out, thump_phase = [], 0.0
    for i in range(n):
        t = i / RATE
        thump_phase += math.tau * (70 - 40 * min(1.0, t / 0.3)) / RATE
        c = crack[i] * math.exp(-t / 0.01) * 2.2
        thump = math.sin(thump_phase) * math.exp(-t / 0.18) * 1.4
        ring = math.sin(math.tau * 1250 * t) * math.exp(-t / 0.25) * 0.25 + math.sin(math.tau * 1870 * t) * math.exp(-t / 0.18) * 0.15
        tail = rumble[i] * math.exp(-t / 0.45) * 3.0
        out.append(c + thump + ring + tail)
    return out


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping synthesised sounds")
else:
    write_ogg("ray_zap", ray_zap(), 0.7)
    write_ogg("laser_shot", laser_shot(), 0.75)
    write_ogg("railgun_charge", railgun_charge(), 0.6)
    write_ogg("railgun_shot", railgun_shot(), 0.95)


def merge_json(path, entries):
    with open(path) as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def own(name):
    return OrderedDict([("subtitle", "subtitles.tomyen." + name), ("sounds", ["tomyen:" + name])])


merge_json(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("laser_shot", own("laser_shot")),
    ("railgun_charge", own("railgun_charge")),
    ("railgun_shot", own("railgun_shot")),
    ("ray_zap", own("ray_zap")),
    ("energy_overheat", OrderedDict([("subtitle", "subtitles.tomyen.energy_overheat"),
                                     ("sounds", [OrderedDict([("name", "minecraft:random/fizz"), ("pitch", 0.8)])])])),
    ("power_cell_load", OrderedDict([("subtitle", "subtitles.tomyen.power_cell_load"),
                                     ("sounds", [OrderedDict([("name", "minecraft:block/beacon/activate"), ("pitch", 1.9), ("volume", 0.6)])])])),
    ("rail_slug_load", OrderedDict([("subtitle", "subtitles.tomyen.rail_slug_load"),
                                    ("sounds", [OrderedDict([("name", "minecraft:item/crossbow/loading_end"), ("pitch", 1.4)])])])),
]))

merge_json(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("item.tomyen.copper_coil", "Copper Coil"),
    ("item.tomyen.uranium_power_cell", "Uranium Power Cell"),
    ("item.tomyen.steel_rail_slug", "Steel Rail Slug"),
    ("item.tomyen.tungsten_rail_slug", "Tungsten Rail Slug"),
    ("item.tomyen.laser_emitter", "Laser Emitter"),
    ("item.tomyen.laser_rifle_housing", "Laser Rifle Housing"),
    ("item.tomyen.laser_rifle", "Laser Rifle"),
    ("item.tomyen.railgun_rails", "Railgun Rails"),
    ("item.tomyen.railgun", "Railgun"),
    ("item.tomyen.ray_gun", "Ray Gun"),
    ("item.tomyen.energy.overheated", "Overheated: venting"),
    ("item.tomyen.laser_rifle.no_cell", "No cell: load a Uranium Power Cell"),
    ("item.tomyen.laser_rifle.charge", "Cell: %s / %s shots"),
    ("item.tomyen.laser_rifle.hint", "Punch to load a Uranium Power Cell; sneak + punch to swap a part-used one"),
    ("item.tomyen.laser_rifle.cell_full", "Cell full"),
    ("item.tomyen.laser_rifle.cell_not_flat", "Cell not flat: sneak + punch to swap it"),
    ("item.tomyen.laser_rifle.no_cells", "No Uranium Power Cells"),
    ("item.tomyen.railgun.empty", "No slugs loaded"),
    ("item.tomyen.railgun.charge_hint", "Hold use to charge; it fires when the coils are full"),
    ("item.tomyen.railgun.hint", "Punch to load slugs; sneak + punch to switch steel and tungsten"),
    ("item.tomyen.railgun.cooling", "Reactor cooling"),
    ("item.tomyen.railgun.full", "Rails loaded"),
    ("item.tomyen.railgun.no_slugs", "No Rail Slugs"),
    ("item.tomyen.ray_gun.hint", "Needs no ammo: lets itself cool between zaps"),
    ("item.tomyen.ray_gun.no_reload", "Nothing to load: it recharges itself"),
    ("hud.tomyen.laser_rifle.cell", "Cell %s / %s"),
    ("hud.tomyen.laser_rifle.no_cell", "No cell"),
    ("hud.tomyen.energy.overheated", "OVERHEATED"),
    ("hud.tomyen.railgun.cooling", "COOLING"),
    ("hud.tomyen.ray_gun.charge", "Charge %s%%"),
    ("hud.tomyen.smart_link.charge", "Chg"),
    ("death.attack.tomyen.laser", "%1$s was burned through"),
    ("death.attack.tomyen.laser.player", "%1$s was burned through by %2$s"),
    ("death.attack.tomyen.laser.item", "%1$s was burned through by %2$s using %3$s"),
    ("death.attack.tomyen.railgun_slug", "%1$s was railed"),
    ("death.attack.tomyen.railgun_slug.player", "%1$s was railed by %2$s"),
    ("death.attack.tomyen.railgun_slug.item", "%1$s was railed by %2$s using %3$s"),
    ("subtitles.tomyen.laser_shot", "Laser fires"),
    ("subtitles.tomyen.railgun_charge", "Railgun charges"),
    ("subtitles.tomyen.railgun_shot", "Railgun fires"),
    ("subtitles.tomyen.ray_zap", "Ray gun zaps"),
    ("subtitles.tomyen.energy_overheat", "Energy weapon vents"),
    ("subtitles.tomyen.power_cell_load", "Power cell clicks in"),
    ("subtitles.tomyen.rail_slug_load", "Slugs load"),
]))
print("energy weapon assets written")
