"""Builds the Gunsmith guns (Slingshot, Flare Gun, Snub Revolver, Sawed-off, Lever-Action Rifle, Nail Gun, Harpoon Gun) and
their ammo: one shared texture atlas, each gun at rest plus six frames of its moving part, chosen by mymod:gun_animation (1-6;
client: GunsmithAnimation), loaded and empty variants where the round shows (the Slingshot's stone, the Harpoon Gun's spear),
the Nail Gun's magazine frames (7-12, as GunAnimation's reload), muzzle flash layers, the small 3D rounds drawn in the hand
while loading, flat inventory sprites for the ammo, and client/gunsmith/GunsmithRigs.java: every display transform, hand
hold and moving-part point the client animates from, so the models and the animations can't drift apart.

Art follows gunsmith_kit.py: clearly separate parts in clean material ramps, wood with placed grain, no noise.

Usage: python3 tools/gen_gunsmith.py src/main/resources/assets/mymod [--preview out.png | --preview rows_dir/]
Then, since this rewrites the guns' item models: tools/gen_gunsmith_mags.py, tools/gen_attachments.py (which wraps every gun's
item model with its attachment layers), tools/check_gunsmith_attachments.py and tools/preview_gunsmith.py, in that order."""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (check, move, rotate, rx, ry, rz, write, model, ref, range_dispatch, has_component, condition, composite,
                    display, bounds, flash_models, with_flash, sprite, png, mul, apply, EMPTY)
from gunsmith_kit import (Atlas, Parts, RAMPS, BLACK, CLEAR, LENS, LENS_HOT, FRAMES, paint_materials, bore_picture,
                          screw_picture, check_all, gun_displays, fp_point, fp_vector, rot_point, render, ortho, first_person,
                          gui_icon, compose, SIDE, RIGHT_FRONT, LEFT_REAR)

ARGS = sys.argv[1:]
PREVIEW = None
if "--preview" in ARGS:
    i = ARGS.index("--preview")
    PREVIEW = ARGS[i + 1]
    del ARGS[i:i + 2]
ASSETS = ARGS[0]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
JAVA = os.path.join(os.path.dirname(__file__), "..", "src", "client", "java", "com", "jheinz", "mymod", "client", "gunsmith",
                    "GunsmithRigs.java")

# ============================================================================================================== atlas

K = Atlas("gunsmith", "item/gunsmith_guns", seed=1873, size=128)
paint_materials(K)
for ramp in ("blue", "steel", "pipe", "case", "brass", "black"):
    K.add(f"screw_{ramp}", 4, 4, screw_picture(ramp), picture=True)
K.add("bore_blue", 16, 16, bore_picture("blue"), picture=True)
K.add("bore_cut", 16, 16, bore_picture("pipe", 4.2), picture=True)    # a hacksawn muzzle: bright raw steel
K.add("bore_steel", 16, 16, bore_picture("steel", 4.0), picture=True)
K.add("bore_flare", 16, 16, bore_picture("orange", 5.4), picture=True)
K.add("bore_black", 16, 16, bore_picture("black", 3.6), picture=True)
K.add("led", 4, 4, lambda i, j: LENS_HOT if 1 <= i <= 2 and 1 <= j <= 2 else LENS, picture=True)


def chambers_picture(ramp, n, ring):
    """A cylinder's face: n chambers on a ring, one at the top, each a dark hole with a lit rim."""
    hi, lt, md, lo, dk = RAMPS[ramp]

    def f(i, j):
        x, y = i - 7.5, j - 7.5
        d = math.hypot(x, y)
        if d > 7.9:
            return CLEAR
        for k in range(n):
            a = -math.pi / 2 + 2 * math.pi * k / n
            dc = math.hypot(x - ring * math.cos(a), y - ring * math.sin(a))
            if dc < 1.6:
                return BLACK
            if dc < 2.3:
                return lt
        if d < 1.2:
            return lo
        return md if d < 6.8 else lo
    return f


K.add("cylinder_face", 16, 16, chambers_picture("blue", 5, 4.6), picture=True)


def chamber_picture(i, j):
    """A barrel's open breech: the chamber mouth with the extractor's lip."""
    hi, lt, md, lo, dk = RAMPS["blue"]
    d = math.hypot(i - 7.5, j - 7.5)
    if d > 7.9:
        return CLEAR
    return BLACK if d < 5.0 else lt if d < 6.2 else md


K.add("chamber", 16, 16, chamber_picture, picture=True)


def checker_picture(ramp):
    """Moulded grip checkering: a clean diamond lattice of raised and sunk texels with a plain border."""
    hi, lt, md, lo, dk = RAMPS[ramp]

    def f(i, j):
        if i in (0, 11) or j in (0, 15):
            return md
        return lt if (i + j) % 3 == 0 else lo if (i - j) % 3 == 0 else md
    return f


K.add("checker_orange", 12, 16, checker_picture("orange"), picture=True)
K.add("checker_rubber", 12, 16, checker_picture("rubber"), picture=True)


def label_picture(i, j):
    """The Flare Gun's printed panel: white, a red bar, black type lines."""
    if j in (0, 9) or i in (0, 23):
        return 0x2A2A2A
    if 2 <= j <= 3:
        return 0xC8342A
    if j in (5, 7) and 2 <= i <= (20 if j == 5 else 14):
        return 0x2A2A2A
    return 0xF2EEE4


K.add("flare_label", 24, 10, label_picture, picture=True)


def nail_label(i, j):
    """The nailer's side panel: a black badge with a yellow stripe and a servo-red tag where the air fitting's warning was."""
    if j in (0, 9) or i in (0, 27):
        return 0x16171A
    if 3 <= j <= 4:
        return RAMPS["yellow"][1]
    if 6 <= j <= 7 and 20 <= i <= 25:
        return 0xC8342A
    return 0x222328


K.add("nail_label", 28, 10, nail_label, picture=True)


def nail_window(i, j):
    """A strip magazine's slot: nail heads in a row behind the follower."""
    if j in (0, 3) or i == 0:
        return 0x16171A
    return RAMPS["pipe"][1] if i % 3 == 1 else RAMPS["pipe"][3] if i % 3 == 2 else 0x16171A


K.add("nail_window", 24, 4, nail_window, picture=True)


def coil_picture(i, j):
    """A coil drum's side: the wound nails as rings round a hub, a rim."""
    d = math.hypot(i - 7.5, j - 7.5)
    if d > 7.9:
        return CLEAR
    if d > 7.0:
        return RAMPS["black"][1]
    if d < 1.6:
        return RAMPS["yellow"][2]
    return RAMPS["pipe"][2] if int(d * 1.6) % 2 else RAMPS["pipe"][4]


K.add("coil_face", 16, 16, coil_picture, picture=True)


def gauge_picture(i, j):
    """The harpoon's pressure gauge: a white dial, a red sector, a black needle, a steel rim."""
    d = math.hypot(i - 7.5, j - 7.5)
    if d > 7.9:
        return CLEAR
    if d > 6.6:
        return RAMPS["steel"][1]
    a = math.atan2(j - 7.5, i - 7.5)
    if d > 4.6 and -0.4 < a < 0.9:
        return 0xC8342A
    if abs((i - 7.5) + (j - 7.5)) < 0.8 and d < 5.5 and i < 8:
        return 0x16171A
    return 0xF2EEE4


K.add("gauge", 16, 16, gauge_picture, picture=True)


def teeth_picture(i, j):
    """A nailer's contact tip seen end on: a toothed steel lip round the nail slot."""
    if 6 <= i <= 9 and 5 <= j <= 10:
        return BLACK
    return RAMPS["pipe"][1] if (i + j) % 2 else RAMPS["pipe"][3]


K.add("teeth", 16, 16, teeth_picture, picture=True)
K.save(TEX)
P = Parts(K)
tube, post, metal, flat, wood, wrap, screw, sticker = P.tube, P.post, P.metal, P.flat, P.wood, P.wrap, P.screw, P.sticker


def bore(out, pic, cy, z, r, cx=8.0):
    sticker(out, cx - r, cy - r, z - 0.03, cx + r, cy + r, z - 0.02, pic, "north", name="bore")


def turned(parts, axis, angle, pivot):
    m = {"x": rx, "y": ry, "z": rz}[axis](angle)
    return rotate(parts, m, pivot)


# ===================================================================================================== Lever-Action

LA_BY = 10.0
LA_PIVOT = (8.0, 7.0, 1.2)        # the lever's pivot at the front of the receiver
LA_LEVER = 48.0                   # how far the lever is thrown (degrees)
LA_BOLT = 2.4                     # how far the bolt slides back
LA_GRIP = (8.0, 8.1, 8.6)         # the shooting hand round the wrist, fingers through the lever
LA_SUPPORT = (8.0, 7.95, -3.2)    # the off hand under the fore-end
LA_LOOP = (8.0, 5.2, 8.0)         # where the fingers hold the lever's loop
LA_PORT = (9.2, 8.2, 2.4)         # the loading gate on the right
LA_EJECT = (9.15, 9.8, 4.2)       # the ejection port on the right
LA_OPTIC = (0.6, 11.0)            # the scope mount on the flat top of the receiver (front z, top y)


def lever_action(t):
    """A Marlin-style lever gun in .38: a blued round barrel over a full-length magazine tube with a brass band, a walnut
    fore-end with a blued cap, a solid-top blued receiver (so a scope fits) with the loading gate and ejection port on the
    right and a brass saddle ring on the left, an exposed hammer, a straight walnut stock with a blued crescent butt plate,
    and the lever, whose loop the fingers work. t (0-1) is the lever stroke: the lever thrown, the bolt back."""
    out = []
    tube(out, -15.0, 0.6, 0.55, "blue", cy=LA_BY, caps=(True, False), name="barrel")
    bore(out, "bore_blue", LA_BY, -15.0, 0.5)
    metal(out, 7.8, 10.45, -14.7, 8.2, 10.75, -14.0, "blue", name="front_sight_base")
    flat(out, 7.88, 10.75, -14.55, 8.12, 11.2, -14.2, "brass", "lt", up="brass_hi", name="front_sight")
    tube(out, -12.6, 0.6, 0.45, "blue", cy=8.95, caps=(False, False), name="magazine_tube")
    tube(out, -13.3, -12.6, 0.5, "blue", cy=8.95, end="blue_md", name="magazine_cap")
    metal(out, 7.36, 8.35, -12.2, 8.64, 10.64, -11.65, "brass", name="barrel_band")
    # Walnut fore-end: a body round the tube, a narrower top along the barrel, a rounded nose; a blued cap.
    wood(out, 7.15, 7.95, -6.4, 8.85, 9.5, 0.6, "walnut", name="fore_end")
    wood(out, 7.3, 9.5, -6.4, 8.7, 10.05, 0.6, "walnut", name="fore_end_top")
    wood(out, 7.3, 8.15, -6.9, 8.7, 9.32, -6.4, "walnut", name="fore_end_nose")
    metal(out, 7.1, 7.88, -6.6, 8.9, 10.15, -6.15, "blue", name="fore_end_cap")
    # Rear sight on the barrel, a step elevator and a notched leaf (they come off for a scope).
    metal(out, 7.5, 10.45, -5.0, 8.5, 10.7, -4.2, "blue", name="rear_sight")
    flat(out, 7.62, 10.7, -4.95, 8.38, 10.85, -4.75, "blue", "lt", name="rear_sight")
    for x0 in (7.55, 8.17):
        metal(out, x0, 10.7, -4.62, x0 + 0.28, 11.3, -4.42, "blue", name="rear_sight")
    # Receiver: a chamfered blued block, the gate and port on the right, screws, the saddle ring on the left.
    K.chamfered(out, 6.95, 7.0, 0.6, 9.05, 11.0, 6.6, 0.35, "blue_md", cap="blue_md", east="blue_side", west="blue_side",
                up="blue_top", down="blue_lo", name="receiver")
    metal(out, 9.05, 7.6, 1.0, 9.17, 8.8, 3.5, "case", west=None, name="loading_gate")
    screw(out, 9.17, 8.2, 3.1, "east", ramp="case", s=0.14)
    sticker(out, 9.05, 9.2, 2.6, 9.08, 10.4, 5.4, "black", "east", name="ejection_port")
    bolt_front = 2.7 + LA_BOLT * t
    if bolt_front < 5.25:
        sticker(out, 9.05, 9.3, bolt_front, 9.1, 10.3, 5.3, "blue_lt", "east", name="bolt")
    if t > 0.01:
        metal(out, 7.55, 9.55, 6.6, 8.45, 10.45, 6.6 + LA_BOLT * t, "blue", north=None, name="bolt_tail")
    for x, f in ((9.05, "east"), (6.95, "west")):
        screw(out, x, 8.1, 5.6, f, ramp="blue")
        screw(out, x, 10.1, 1.3, f, ramp="blue")
    ring = []
    metal(ring, 6.7, 9.0, 5.45, 6.95, 9.3, 5.75, "brass", east=None, name="saddle_ring")
    for y0, y1, z0, z1 in ((7.85, 8.0, 5.0, 6.2), (8.85, 9.0, 5.0, 6.2), (8.0, 8.85, 5.0, 5.15), (8.0, 8.85, 6.05, 6.2)):
        flat(ring, 6.6, y0, z0, 6.74, y1, z1, "brass", "lt", name="saddle_ring")
    out += ring
    # The tang and the hammer, cocked (and pushed a little further back as the bolt goes).
    metal(out, 7.55, 10.6, 6.6, 8.45, 10.85, 9.4, "blue", name="tang")
    screw(out, 8.0, 10.85, 8.9, "up", ramp="blue", s=0.15)
    hammer = []
    metal(hammer, 7.72, 9.9, 6.65, 8.28, 11.35, 7.15, "case", name="hammer")
    metal(hammer, 7.64, 11.2, 6.85, 8.36, 11.48, 7.9, "case", name="hammer_spur")
    out += turned(hammer, "x", 24 + 8 * t, (8.0, 9.9, 6.9))
    flat(out, 7.88, 6.0, 4.6, 8.12, 7.0, 4.85, "case", "md", rot=("x", -12, (8.0, 7.0, 4.7)), name="trigger")
    # The lever: a bar flush under the receiver and the loop round the fingers, thrown about the pivot.
    lever = []
    metal(lever, 7.6, 6.6, 1.0, 8.4, 7.0, 5.35, "blue", up=None, name="lever")
    metal(lever, 7.62, 4.75, 5.3, 8.38, 6.6, 5.75, "blue", name="lever_loop")
    metal(lever, 7.62, 4.3, 5.3, 8.38, 4.75, 9.5, "blue", name="lever_loop")
    metal(lever, 7.62, 4.75, 9.05, 8.38, 6.75, 9.5, "blue", name="lever_loop")
    metal(lever, 7.5, 6.55, 0.75, 8.5, 7.0, 1.55, "blue", up=None, name="lever_pivot")
    out += turned(lever, "x", LA_LEVER * t, LA_PIVOT)
    # The stock: a slim straight wrist, a butt whose belly deepens in a wedge to the toe, a blued crescent plate.
    wrist = []
    wood(wrist, 7.33, 6.95, 6.6, 8.67, 10.5, 11.2, "walnut", name="wrist")
    out += turned(wrist, "x", 5, (8.0, 10.5, 6.6))
    butt = []
    wood(butt, 7.08, 5.5, 10.6, 8.92, 10.2, 23.0, "walnut", name="butt")
    toe = []
    wood(toe, 7.14, 3.9, 12.5, 8.86, 5.75, 23.4, "walnut", name="butt_toe")
    butt += turned(toe, "x", 11, (8.0, 5.75, 23.4))
    metal(butt, 7.02, 3.9, 23.0, 8.98, 10.28, 23.5, "blue", name="butt_plate")
    screw(butt, 8.0, 5.2, 23.5, "south", ramp="blue", s=0.16)
    screw(butt, 8.0, 9.3, 23.5, "south", ramp="blue", s=0.16)
    out += turned(butt, "x", 4, (8.0, 10.2, 10.6))
    return out


def la_loop(t):
    """Where the fingers hold the lever's loop at stroke t."""
    return rot_point(LA_LOOP, "x", LA_LEVER * t, LA_PIVOT)


# ======================================================================================================== Sawed-off

SO_BY = 9.7
SO_HINGE = (8.0, 8.85, 4.55)
SO_OPEN = -38.0                   # the barrels drop this far (degrees) when broken open
SO_GRIP = (8.0, 7.0, 9.4)
SO_SUPPORT = (8.0, 8.35, 1.2)
SO_BARRELS = (7.32, 8.68)
SO_BREECH = 4.4


def sawed_off(t):
    """A side-by-side 12 gauge cut down to a coach gun: two blued barrels hacksawn short (the cut bright raw steel), a top
    rib with a brass bead, a walnut splinter fore-end, a case-hardened action with two exposed hammers and two triggers, a top
    lever, and the stock sawn off behind a pistol grip, its cut end bare and the grip wrapped in tape. t (0-1): broken open."""
    out = []
    barrels = []
    for cx in SO_BARRELS:
        tube(barrels, -5.0, SO_BREECH, 0.6, "blue", cx=cx, cy=SO_BY, caps=(True, False), name="barrel")
        bore(barrels, "bore_cut", SO_BY, -5.0, 0.56, cx=cx)
        sticker(barrels, cx - 0.56, SO_BY - 0.56, SO_BREECH - 0.04, cx + 0.56, SO_BY + 0.56, SO_BREECH - 0.03, "chamber", "south",
                name="chamber")
    metal(barrels, 7.84, 9.95, -5.0, 8.16, 10.4, SO_BREECH, "blue", name="top_rib")
    flat(barrels, 7.9, 10.4, -4.85, 8.1, 10.62, -4.55, "brass", "lt", up="brass_hi", name="bead")
    metal(barrels, 7.84, 8.95, -5.0, 8.16, 9.45, SO_BREECH, "blue", name="bottom_rib")
    wood(barrels, 6.95, 8.35, -1.2, 9.05, 9.25, 3.6, "walnut", name="fore_end")
    wood(barrels, 7.15, 8.5, -1.7, 8.85, 9.15, -1.2, "walnut", name="fore_end_nose")
    metal(barrels, 7.6, 8.25, 1.6, 8.4, 8.35, 3.2, "case", up=None, name="fore_end_iron")
    metal(barrels, 7.5, 8.55, 3.6, 8.5, 9.1, SO_BREECH - 0.04, "case", name="barrel_lump")
    out += turned(barrels, "x", SO_OPEN * t, SO_HINGE)
    # The action: case-hardened, firing pin bushings on its breech face, the hinge pin.
    K.chamfered(out, 6.9, 7.3, SO_BREECH, 9.1, 10.45, 8.2, 0.4, "case_md", cap="case_md", east="case_side", west="case_side",
                up="case_top", down="case_lo", name="action")
    for cx in SO_BARRELS:
        screw(out, cx, SO_BY, SO_BREECH, "north", ramp="pipe", s=0.16, h=0.04)
    tube(out, 6.8, 9.2, 0.22, "case", cx=SO_HINGE[0], cy=SO_HINGE[1], cross=True, name="hinge_pin")
    # Top lever, turned to the right as the gun breaks.
    top = []
    metal(top, 7.6, 10.45, 7.2, 8.4, 10.7, 9.4, "blue", name="top_lever")
    metal(top, 8.3, 10.48, 9.0, 9.0, 10.78, 9.55, "blue", name="top_lever_thumb")
    out += turned(top, "y", -28 * t, (8.0, 10.6, 7.6))
    for x in SO_BARRELS:
        hammer = []
        metal(hammer, x - 0.17, 9.6, 7.35, x + 0.17, 11.0, 7.82, "case", name="hammer")
        metal(hammer, x - 0.22, 10.82, 7.55, x + 0.22, 11.08, 8.5, "case", name="hammer_spur")
        out += turned(hammer, "x", 28, (x, 9.6, 7.6))
    screw(out, 9.1, 8.4, 6.9, "east", ramp="case")
    screw(out, 6.9, 8.4, 6.9, "west", ramp="case")
    metal(out, 7.4, 7.0, 4.6, 8.6, 7.3, 8.2, "case", up=None, name="trigger_plate")
    flat(out, 7.62, 6.4, 5.9, 7.88, 7.0, 6.15, "pipe", "lt", name="trigger")
    flat(out, 8.12, 6.4, 6.6, 8.38, 7.0, 6.85, "pipe", "lt", name="trigger")
    metal(out, 7.72, 5.6, 5.3, 8.28, 5.85, 7.95, "blue", name="trigger_guard")
    metal(out, 7.72, 5.85, 5.3, 8.28, 7.0, 5.55, "blue", name="trigger_guard")
    metal(out, 7.72, 5.85, 7.7, 8.28, 7.0, 7.95, "blue", name="trigger_guard")
    # The pistol grip left by the saw: a slim walnut grip with a blued cap, wrapped in tape, and the stub of the stock above
    # it ending in a bare cut.
    grip = []
    wood(grip, 7.33, 3.7, 8.0, 8.67, 9.3, 10.15, "walnut", upright=True, name="grip")
    wrap(grip, 7.25, 5.0, 7.95, 8.75, 6.1, 10.2, "tape_black", upright=True, name="grip_tape")
    metal(grip, 7.26, 3.45, 7.96, 8.74, 3.7, 10.19, "blue", name="grip_cap")
    out += turned(grip, "x", -18, (8.0, 9.0, 8.2))
    stub = []
    wood(stub, 7.37, 8.0, 8.2, 8.63, 10.3, 11.6, "walnut", name="stock_stub")
    out += turned(stub, "x", 8, (8.0, 10.3, 8.2))
    return out


def so_point(p, t):
    """A point on the barrels at break-open t."""
    return rot_point(p, "x", SO_OPEN * t, SO_HINGE)


# ===================================================================================================== Snub Revolver

SR_BY = 10.05
SR_CYL = (8.0, 9.2)               # the cylinder's axis
SR_CRANE = (7.35, 8.15)           # the crane's pivot (an axis along z)
SR_SWING = 78.0                   # how far the cylinder swings out to the left
SR_GRIP = (8.0, 6.0, 7.0)
SR_CYL_Z = (0.95, 4.6)


def snub_revolver(t):
    """A two-inch .38 snub: a blued barrel with a full ejector shroud, a ramp front sight, an open frame showing the
    five-shot cylinder (which swings out to the left on its crane), a case-coloured hammer with a spur, a round trigger guard
    and a walnut boot grip. t (0-1): the cylinder swung out."""
    out = []
    tube(out, -3.6, 0.7, 0.42, "blue", cy=SR_BY, caps=(True, False), name="barrel")
    bore(out, "bore_blue", SR_BY, -3.6, 0.38)
    metal(out, 7.6, 8.75, -3.4, 8.4, 9.66, 0.4, "blue", name="ejector_shroud")
    flat(out, 7.86, 10.45, -3.3, 8.14, 10.95, -2.5, "blue", "md", up="blue_hi", name="front_sight")
    # The frame: top strap, bottom bar, the front post the barrel threads into, the recoil shield, the rear housing.
    metal(out, 7.25, 10.55, 0.4, 8.75, 11.0, 5.6, "blue", name="top_strap")
    sticker(out, 7.92, 11.0, 2.0, 8.08, 11.02, 5.6, "black", "up", name="rear_sight")
    metal(out, 7.25, 7.4, 0.9, 8.75, 7.85, 4.65, "blue", name="frame_bottom")
    metal(out, 7.15, 7.4, 0.4, 8.85, 10.55, 0.9, "blue", name="frame_front")
    metal(out, 7.15, 7.4, 4.65, 8.85, 10.55, 5.6, "blue", name="recoil_shield")
    metal(out, 7.25, 7.0, 5.6, 8.75, 11.1, 7.1, "blue", name="frame_rear")
    metal(out, 6.95, 9.3, 5.0, 7.15, 9.8, 6.0, "case", east=None, name="cylinder_latch")
    for x, f in ((8.75, "east"), (7.25, "west")):
        screw(out, x, 10.2, 6.5, f, ramp="blue", s=0.15)
        screw(out, x, 7.8, 6.6, f, ramp="blue", s=0.15)
    # Cylinder, crane and ejector rod, swung out together about the crane's pivot.
    swing = []
    tube(swing, SR_CYL_Z[0], SR_CYL_Z[1], 1.3, "blue", cy=SR_CYL[1], end="blue_md", name="cylinder")
    sticker(swing, 8 - 1.25, SR_CYL[1] - 1.25, SR_CYL_Z[0] - 0.03, 8 + 1.25, SR_CYL[1] + 1.25, SR_CYL_Z[0] - 0.02, "cylinder_face",
            "north", name="cylinder_front")
    sticker(swing, 8 - 1.25, SR_CYL[1] - 1.25, SR_CYL_Z[1] + 0.02, 8 + 1.25, SR_CYL[1] + 1.25, SR_CYL_Z[1] + 0.03, "cylinder_face",
            "south", name="cylinder_rear")
    # Flutes between the chambers, on the cylinder's four diagonal faces.
    for a in (45, 135, 225, 315):
        flute = []
        sticker(flute, 9.3, SR_CYL[1] - 0.24, SR_CYL_Z[0] + 0.5, 9.33, SR_CYL[1] + 0.24, SR_CYL_Z[1] - 0.6, "blue_dk", "east",
                name="cylinder_flute")
        swing += turned(flute, "z", a, (8.0, SR_CYL[1], 0.0))
    tube(swing, -2.7, SR_CYL_Z[0], 0.2, "pipe", cy=SR_CYL[1], cross=True, name="ejector_rod")
    metal(swing, 7.3, 7.9, 0.55, 8.3, 8.6, 0.92, "blue", name="crane")
    out += turned(swing, "z", SR_SWING * t, (SR_CRANE[0], SR_CRANE[1], 0.0))
    hammer = []
    metal(hammer, 7.72, 10.4, 5.95, 8.28, 11.85, 6.45, "case", name="hammer")
    metal(hammer, 7.66, 11.6, 6.2, 8.34, 11.9, 7.15, "case", name="hammer_spur")
    out += turned(hammer, "x", -4, (8.0, 10.4, 6.2))
    flat(out, 7.86, 6.4, 5.35, 8.14, 7.4, 5.65, "case", "md", rot=("x", -10, (8.0, 7.4, 5.5)), name="trigger")
    metal(out, 7.74, 5.7, 4.4, 8.26, 5.95, 6.7, "blue", name="trigger_guard")
    metal(out, 7.74, 5.95, 4.4, 8.26, 7.4, 4.65, "blue", name="trigger_guard")
    metal(out, 7.74, 5.95, 6.45, 8.26, 7.0, 6.7, "blue", name="trigger_guard")
    # A small walnut boot grip, reaching up into the frame, its heel rounded off, a brass medallion each side.
    grip = []
    wood(grip, 7.12, 4.0, 5.75, 8.88, 7.6, 8.2, "walnut", upright=True, name="grip")
    heel = []
    wood(heel, 7.16, 3.55, 6.3, 8.84, 4.4, 7.65, "walnut", upright=True, name="grip_heel")
    grip += heel
    metal(grip, 7.55, 4.05, 8.2, 8.45, 7.4, 8.45, "blue", name="backstrap")
    screw(grip, 8.88, 5.6, 6.95, "east", ramp="brass", s=0.22)
    screw(grip, 7.12, 5.6, 6.95, "west", ramp="brass", s=0.22)
    out += turned(grip, "x", -14, (8.0, 7.4, 6.4))
    return out


def sr_cylinder(p, t):
    return rot_point(p, "z", SR_SWING * t, (SR_CRANE[0], SR_CRANE[1], 0.0))


# ========================================================================================================== Slingshot

SS_TIPS = 4.2                     # the fork arms' length
SS_SPLAY = 28.0
SS_POUCH = (8.0, 12.1, 2.0)       # the pouch at rest, just behind the fork
SS_DRAW = 10.5                    # how far it's drawn back
SS_GRIP = (8.0, 5.0, 0.0)


def ss_tip(side):
    a = math.radians(SS_SPLAY)
    return (8.0 + side * SS_TIPS * math.sin(a), 9.0 + (SS_TIPS - 0.5) * math.cos(a), 0.0)


def band(out, a, b, w, ramp):
    """A latex tube from a to b: a square strip along z of length |b - a|, turned to run along the line."""
    d = [b[k] - a[k] for k in range(3)]
    length = math.sqrt(sum(v * v for v in d))
    yaw = math.degrees(math.atan2(d[0], d[2]))
    pitch = math.degrees(math.atan2(-d[1], math.hypot(d[0], d[2])))
    e = []
    metal(e, a[0] - w / 2, a[1] - w / 2, a[2], a[0] + w / 2, a[1] + w / 2, a[2] + length, ramp, north=None, south=None,
          name="band")
    m = mul(ry(yaw), rx(pitch))
    rotate(e, m, a)
    out += e


def slingshot(t, loaded=False):
    """A forked oak branch with a black-taped handle and a knobbed pommel with a string lanyard, a brass ferrule at the
    crotch, string whipping at each tip holding two amber latex tubes a side, and a stitched leather pouch (with a stone in it
    when loaded). t (0-1): drawn."""
    out = []
    wood(out, 7.25, 2.4, -0.75, 8.75, 8.3, 0.75, "oak", upright=True, name="handle")
    wrap(out, 7.2, 3.1, -0.8, 8.8, 6.7, 0.8, "tape_black", upright=True, name="handle_tape")
    wood(out, 7.12, 1.85, -0.88, 8.88, 2.4, 0.88, "oak", upright=True, name="pommel")
    flat(out, 7.85, 0.9, -0.12, 8.15, 1.85, 0.12, "string", "md", name="lanyard")
    flat(out, 7.55, 0.6, -0.12, 8.45, 0.9, 0.12, "string", "md", name="lanyard")
    wood(out, 7.15, 8.3, -0.8, 8.85, 9.4, 0.8, "oak", upright=True, name="crotch")
    metal(out, 7.08, 8.0, -0.86, 8.92, 8.4, 0.86, "brass", name="ferrule")
    for side in (-1, 1):
        arm = []
        # The two arms cross inside the crotch; one a hair thinner so their faces never meet.
        d = 0.62 if side < 0 else 0.58
        wood(arm, 7.35, 9.0, -d, 8.65, 9.0 + SS_TIPS, d, "oak", upright=True, name="fork_arm")
        wrap(arm, 7.3, 9.0 + SS_TIPS - 1.0, -d - 0.05, 8.7, 9.0 + SS_TIPS - 0.35, d + 0.05, "string", upright=True, name="whipping")
        out += turned(arm, "z", -side * SS_SPLAY, (8.0, 9.0, 0.0))
    pz = SS_POUCH[2] + SS_DRAW * t
    py = SS_POUCH[1] - 0.25 * t
    for side in (-1, 1):
        tip = ss_tip(side)
        for dy in (-0.22, 0.22):
            band(out, (tip[0], tip[1] + dy, tip[2] + 0.5), (8.0 + side * 0.85, py + dy, pz), 0.24, "amber")
    pouch = []
    metal(pouch, 7.25, py - 0.55, pz - 0.15, 8.75, py + 0.55, pz + 0.3, "leather", name="pouch")
    for side in (-1, 1):
        x0 = 8.0 + side * 0.75
        metal(pouch, min(x0, x0 + side * 0.3), py - 0.42, pz - 0.1, max(x0, x0 + side * 0.3), py + 0.42, pz + 0.25, "leather",
              name="pouch_tab")
    flat(pouch, 7.4, py - 0.05, pz + 0.3, 8.6, py + 0.05, pz + 0.33, "leather", "hi", name="stitching")
    for side in (-1, 1):
        x = 8.0 + side * 0.9
        flat(pouch, x - 0.1, py - 0.48, pz - 0.14, x + 0.1, py + 0.48, pz + 0.27, "string", "md", name="pouch_tie")
    if loaded:
        flat(pouch, 7.62, py - 0.38, pz - 0.5, 8.38, py + 0.38, pz - 0.15, "iron", "lt", up="iron_hi", name="stone")
    out += pouch
    return out


def ss_pouch(t):
    return (8.0, SS_POUCH[1] - 0.25 * t, SS_POUCH[2] + SS_DRAW * t)


# ========================================================================================================== Flare Gun

FG_BY = 10.4
FG_HINGE = (8.0, 9.05, 3.0)
FG_OPEN = -45.0
FG_BREECH = 3.2
FG_GRIP = (8.0, 6.4, 6.0)


def flare_gun(t):
    """A 26.5 mm signal pistol in moulded orange: a fat barrel with a thicker muzzle ring and a sight rib, broken open on a
    hinge lug, a chamfered frame with a printed panel, a black hammer and latch, an integral trigger guard, a checkered grip
    with a black base and a brass lanyard ring. t (0-1): broken open."""
    out = []
    barrel = []
    tube(barrel, -3.6, FG_BREECH, 1.05, "orange", cy=FG_BY, caps=(False, False), name="barrel")
    tube(barrel, -4.3, -3.6, 1.2, "orange", cy=FG_BY, end="orange_md", name="muzzle_ring")
    bore(barrel, "bore_flare", FG_BY, -4.3, 1.12)
    sticker(barrel, 8 - 1.0, FG_BY - 1.0, FG_BREECH - 0.04, 8 + 1.0, FG_BY + 1.0, FG_BREECH - 0.03, "bore_flare", "south",
            name="chamber")
    metal(barrel, 7.84, 11.4, -3.5, 8.16, 11.66, 2.9, "orange", name="sight_rib")
    flat(barrel, 7.88, 11.66, -3.4, 8.12, 11.95, -3.0, "orange", "hi", name="front_sight")
    metal(barrel, 7.5, 8.95, 1.8, 8.5, 9.4, FG_BREECH, "orange", name="hinge_lug")
    # A moulded reinforcing ring round the barrel, and the extractor at the breech.
    tube(barrel, -1.3, -0.95, 1.13, "orange", cy=FG_BY, end="orange_lo", name="barrel_ring")
    flat(barrel, 8.6, 9.6, FG_BREECH - 0.6, 9.02, 10.0, FG_BREECH - 0.02, "black", "lt", name="extractor")
    out += turned(barrel, "x", FG_OPEN * t, FG_HINGE)
    K.chamfered(out, 6.9, 8.4, FG_BREECH, 9.1, 11.65, 6.4, 0.4, "orange_md", cap="orange_md", east="orange_side",
                west="orange_side", up="orange_top", down="orange_lo", name="frame")
    screw(out, 8.0, FG_BY, FG_BREECH, "north", ramp="pipe", s=0.2, h=0.04)
    tube(out, 6.75, 9.25, 0.24, "black", cx=FG_HINGE[0], cy=FG_HINGE[1], cross=True, name="hinge_pin")
    sticker(out, 9.1, 9.0, 3.8, 9.13, 10.8, 5.9, "flare_label", "east", name="label")
    metal(out, 7.55, 11.65, 2.7, 8.45, 11.9, 4.1, "black", name="barrel_latch")
    metal(out, 8.45, 11.62, 3.0, 9.0, 11.85, 3.5, "black", name="latch_thumb")
    sticker(out, 6.87, 9.0, 3.8, 6.9, 10.8, 5.9, "flare_label", "west", name="label")
    metal(out, 6.75, 9.6, 5.4, 6.9, 10.2, 6.2, "black", east=None, name="safety")
    for x, f in ((9.1, "east"), (6.9, "west")):
        screw(out, x, 8.9, 6.0, f, ramp="black", s=0.16)
    hammer = []
    metal(hammer, 7.66, 10.6, 5.75, 8.34, 12.0, 6.3, "black", name="hammer")
    metal(hammer, 7.62, 11.75, 6.0, 8.38, 12.05, 6.9, "black", name="hammer_spur")
    out += turned(hammer, "x", 10, (8.0, 10.6, 6.0))
    metal(out, 7.68, 6.55, 1.5, 8.32, 6.9, 5.1, "orange", name="trigger_guard")
    metal(out, 7.68, 6.9, 1.5, 8.32, 8.4, 1.85, "orange", name="trigger_guard")
    flat(out, 7.85, 7.1, 3.3, 8.15, 8.4, 3.65, "black", "md", rot=("x", -10, (8.0, 8.4, 3.45)), name="trigger")
    grip = []
    metal(grip, 7.05, 4.0, 4.3, 8.95, 9.3, 7.0, "orange", east="checker_orange", west="checker_orange", name="grip")
    metal(grip, 7.0, 3.7, 4.25, 9.0, 4.0, 7.05, "black", name="grip_base")
    for y0, y1, z0, z1 in ((3.0, 3.15, 5.8, 6.8), (3.15, 3.55, 5.8, 5.95), (3.15, 3.55, 6.65, 6.8)):
        flat(grip, 7.92, y0, z0, 8.08, y1, z1, "brass", "lt", name="lanyard_ring")
    out += turned(grip, "x", -14, (8.0, 8.45, 5.8))
    return out


def fg_point(p, t):
    return rot_point(p, "x", FG_OPEN * t, FG_HINGE)


# ======================================================================================================== Harpoon Gun

HG_BY = 10.2
HG_RES = 8.25                     # the air reservoir's axis
HG_GRIP = (8.0, 6.3, 6.0)
HG_SUPPORT = (8.0, 7.1, -4.6)
HG_WORK = (6.75, 8.6, 2.4)        # the cocking knob
HG_TRAVEL = 3.0
HG_MUZZLE = -13.0


def harpoon_gun(t, loaded=False):
    """A pneumatic spear gun: a brushed steel barrel over a fat air reservoir, both held by black clamps, a black muzzle head
    with the line release on top, a pressure gauge on the reservoir, a line reel on the left (drum of pale line, side plates,
    crank), a rubber pistol grip and chest pad, and the cocking knob on the left that's drawn back to charge it. Loaded, the
    spear's barbed head stands out of the muzzle. t (0-1): the cocking knob drawn back."""
    out = []
    tube(out, -11.6, 6.0, 0.62, "steel", cy=HG_BY, caps=(False, True), name="barrel")
    tube(out, HG_MUZZLE, -11.6, 0.86, "black", cy=HG_BY, end="black_md", name="muzzle_head")
    bore(out, "bore_steel", HG_BY, HG_MUZZLE, 0.55)
    metal(out, 7.72, 11.0, -12.6, 8.28, 11.42, -11.0, "black", name="line_release")
    flat(out, 7.9, 11.42, -12.4, 8.1, 11.7, -12.1, "steel", "lt", name="front_sight")
    tube(out, -8.6, 8.0, 1.1, "steel", cy=HG_RES, caps=(False, False), name="reservoir")
    # A rubber sleeve round the reservoir where the off hand holds it, and the charging valve under its front cap.
    tube(out, -5.8, -1.6, 1.18, "rubber", cy=HG_RES, end="rubber_lo", name="foregrip")
    flat(out, 7.75, 6.75, -9.15, 8.25, 7.3, -8.75, "steel", "lt", name="charge_valve")
    tube(out, -9.3, -8.6, 0.95, "black", cy=HG_RES, end="black_md", name="reservoir_cap")
    tube(out, 8.0, 8.6, 0.95, "black", cy=HG_RES, end="black_md", name="reservoir_cap")
    for z in (-7.6, -0.8):
        metal(out, 7.18, 7.0, z, 8.82, 10.95, z + 0.6, "black", name="clamp")
    metal(out, 7.6, 11.0, 4.6, 8.4, 11.15, 5.2, "black", name="rear_sight")
    for x0 in (7.6, 8.18):
        flat(out, x0, 11.15, 4.75, x0 + 0.22, 11.5, 5.05, "black", "lt", name="rear_sight")
    # Pressure gauge on the right of the reservoir.
    gauge = []
    tube(gauge, 9.0, 9.55, 0.62, "steel", cx=0.0, cy=0.0, name="gauge")
    sticker(gauge, -0.58, -0.58, 9.56, 0.58, 0.58, 9.57, "gauge", "south", name="gauge_face")
    out += move(rotate(gauge, ry(90), (0, 0, 0)), dx=0.0, dy=HG_RES + 0.3, dz=4.6)
    # Line reel on the left, under the barrel ahead of the grip.
    reel = []
    K.rod(reel, -0.45, 0.45, 0.95, "line_rows", cx=0.0, cy=0.0, along="line_cols", caps=(False, False), name="reel_line")
    for z0 in (-0.55, 0.45):
        tube(reel, z0, z0 + 0.1, 1.25, "black", cx=0.0, cy=0.0, end="black_md", name="reel_plate")
    reel_parts = rotate(reel, ry(90), (0, 0, 0))
    out += move(reel_parts, dx=5.85, dy=7.0, dz=2.6)
    metal(out, 6.4, 6.7, 2.3, 7.2, 7.3, 2.9, "black", name="reel_bracket")
    flat(out, 4.95, 6.85, 2.45, 5.25, 8.2, 2.75, "steel", "md", name="reel_crank")
    flat(out, 4.7, 7.95, 2.35, 4.95, 8.35, 2.85, "black", "lt", name="reel_knob")
    # Cocking knob on a slot along the reservoir's left.
    sticker(out, 6.97, 8.35, 0.4, 7.0, 8.85, 4.0, "black", "west", name="cocking_slot")
    knob = []
    metal(knob, 7.0, 8.45, 1.9, 7.2, 8.75, 2.5, "steel", name="cocking_stem")
    metal(knob, 6.4, 8.2, 1.85, 7.0, 9.0, 2.75, "black", name="cocking_knob")
    out += move(knob, dz=HG_TRAVEL * t)
    grip = []
    metal(grip, 7.15, 3.3, 4.8, 8.85, 7.2, 7.2, "rubber", east="checker_rubber", west="checker_rubber", name="grip")
    metal(grip, 7.1, 3.0, 4.75, 8.9, 3.3, 7.3, "black", name="grip_cap")
    out += turned(grip, "x", -14, (8.0, 7.2, 5.6))
    metal(out, 7.25, 7.1, 4.4, 8.75, 7.4, 8.0, "black", up=None, name="grip_frame")
    metal(out, 7.72, 5.6, 2.9, 8.28, 5.85, 5.0, "black", name="trigger_guard")
    metal(out, 7.72, 5.85, 2.9, 8.28, 7.1, 3.15, "black", name="trigger_guard")
    flat(out, 7.86, 5.95, 3.9, 8.14, 7.1, 4.2, "steel", "md", rot=("x", -10, (8.0, 7.1, 4.05)), name="trigger")
    metal(out, 7.0, 6.9, 8.6, 9.0, 10.7, 9.8, "rubber", name="chest_pad")
    if loaded:
        spear = []
        tube(spear, -15.2, HG_MUZZLE, 0.26, "steel", cy=HG_BY, name="spear")
        tube(spear, -15.9, -15.2, 0.17, "steel", cy=HG_BY, cross=True, name="spear_tip")
        for a, w in ((-24, 0.08), (24, 0.07)):
            barb = []
            flat(barb, 8.0 - w, HG_BY - 0.06, -14.9, 8.0 + w, HG_BY + 0.06, -13.9, "steel", "lt", name="barb")
            spear += turned(barb, "x", a, (8.0, HG_BY, -13.9))
        out += spear
    return out


# ========================================================================================================== Nail Gun

NG_BY = 10.0
NG_SHOT = 8.9                     # the nail's line, out of the nose
NG_GRIP = (8.0, 6.8, 6.2)
NG_SUPPORT = (8.0, 8.3, -4.4)
NG_MAG_PIVOT = (8.0, 8.4, -2.4)
NG_MAG_RAKE = -15.0
NG_MAG_STEP = 1.5


def ng_mag_offset(k):
    """How far the magazine has slid out of the well in magazine frame k: along its own axis, down and back."""
    d = apply(rx(NG_MAG_RAKE), [0.0, -NG_MAG_STEP * k, 0.0])
    return d


def nail_magazine(kind, k):
    """The stick strip (25) or the coil drum (60), slid k frames out of the well."""
    out = []
    if kind == "strip":
        metal(out, 7.42, 2.9, -3.3, 8.58, 8.35, -1.5, "black", east="black_side", name="magazine")
        sticker(out, 8.58, 3.5, -3.0, 8.61, 7.6, -1.8, "nail_window", "east", name="magazine_window")
        flat(out, 7.38, 2.6, -3.35, 8.62, 2.9, -1.45, "yellow", "lo", name="magazine_base")
        out = turned(out, "x", NG_MAG_RAKE, NG_MAG_PIVOT)
    else:
        metal(out, 7.45, 6.4, -3.1, 8.55, 8.35, -1.7, "black", name="magazine")
        drum = []
        tube(drum, -0.8, 0.8, 2.3, "black", cx=0.0, cy=0.0, end="black_md", name="coil_drum")
        sticker(drum, -2.2, -2.2, 0.81, 2.2, 2.2, 0.82, "coil_face", "south", name="coil_face")
        sticker(drum, -2.2, -2.2, -0.82, 2.2, 2.2, -0.81, "coil_face", "north", name="coil_face")
        out += move(rotate(drum, ry(90), (0, 0, 0)), dx=8.0, dy=4.5, dz=-2.6)
        out = turned(out, "x", NG_MAG_RAKE * 0.5, NG_MAG_PIVOT)
    d = ng_mag_offset(k)
    return move(out, dx=d[0], dy=d[1], dz=d[2])


def nail_gun(mag="strip", k=0):
    """A framing nailer rebuilt as a weapon: the tool-yellow drive cylinder with black end caps and a turned exhaust cap on top,
    a steel nose with its toothed contact tip and guide rails and a depth wheel, a magazine well underneath, a yellow pistol
    grip with black rubber overmould and the air fitting taped over, a trigger hoop, a rafter hook, and the salvaged Skullcrane
    servo strapped to the left with black tape, its red light on top and a red cable to the trigger. mag: the magazine fitted
    (strip, coil or none); k: magazine frames out of the well."""
    out = []
    tube(out, -5.0, 4.0, 1.7, "yellow", cy=NG_BY, caps=(False, False), name="body")
    tube(out, -5.6, -5.0, 1.5, "black", cy=NG_BY, end="black_md", name="front_cap")
    tube(out, 4.0, 4.7, 1.5, "black", cy=NG_BY, end="black_md", name="rear_cap")
    # Moulding seams round the body, and the driver housing's black cap on top at the front.
    for z in (-4.1, 2.9):
        tube(out, z, z + 0.25, 1.76, "black", cy=NG_BY, end="black_lo", name="body_seam")
    metal(out, 7.35, 11.55, -4.85, 8.65, 11.95, -3.3, "black", name="driver_cap")
    sticker(out, 9.7, 9.35, -3.6, 9.73, 10.65, 0.4, "nail_label", "east", name="label")
    metal(out, 7.2, 11.55, 2.5, 8.8, 12.15, 4.4, "black", name="exhaust_cap")
    sticker(out, 7.4, 11.75, 2.47, 8.6, 12.0, 2.5, "black", "north", name="exhaust_vent")
    flat(out, 7.86, 11.7, -4.4, 8.14, 12.1, -4.0, "black", "lt", name="front_sight")
    for x0 in (7.62, 8.16):
        flat(out, x0, 12.15, 3.4, x0 + 0.22, 12.45, 3.7, "black", "lt", name="rear_sight")
    # The nose: a steel guide, its toothed contact tip, guide rails, a depth wheel.
    metal(out, 7.35, 8.2, -9.4, 8.65, 9.6, -5.6, "steel", name="nose")
    metal(out, 7.5, 8.3, -10.25, 8.5, 9.5, -9.4, "pipe", north="teeth", name="contact_tip")
    for x0 in (7.18, 8.67):
        flat(out, x0, 8.95, -9.8, x0 + 0.15, 9.15, -5.8, "pipe", "lt", name="contact_rail")
    metal(out, 8.65, 9.2, -8.2, 8.95, 9.9, -7.4, "black", name="depth_wheel")
    metal(out, 7.3, 7.65, -3.75, 8.7, 8.45, -1.05, "yellow", name="magwell")
    # Grip: a yellow core in black rubber overmould, a neck to the body, the taped-over air fitting.
    metal(out, 7.3, 8.0, 3.0, 8.7, 8.45, 6.6, "yellow", name="grip_neck")
    grip = []
    metal(grip, 7.2, 3.8, 5.4, 8.8, 8.0, 7.6, "yellow", name="grip")
    metal(grip, 7.15, 4.2, 5.25, 8.85, 7.8, 5.6, "rubber", north="rubber_side", name="grip_overmould")
    metal(grip, 7.15, 4.2, 7.4, 8.85, 7.8, 7.75, "rubber", south="rubber_side", name="grip_overmould")
    metal(grip, 7.55, 3.15, 6.05, 8.45, 3.8, 6.95, "brass", name="air_fitting")
    wrap(grip, 7.5, 2.85, 6.0, 8.5, 3.15, 7.0, "tape_black", upright=True, name="fitting_tape")
    out += turned(grip, "x", -16, (8.0, 8.0, 6.4))
    flat(out, 7.86, 6.7, 4.4, 8.14, 8.0, 4.75, "black", "md", rot=("x", -12, (8.0, 8.0, 4.6)), name="trigger")
    metal(out, 7.74, 5.55, 3.1, 8.26, 5.8, 5.4, "black", name="trigger_hoop")
    metal(out, 7.74, 5.8, 3.1, 8.26, 8.0, 3.35, "black", name="trigger_hoop")
    # Rafter hook on the right.
    metal(out, 9.62, 9.3, 1.4, 9.95, 10.4, 3.6, "black", west=None, name="rafter_hook")
    metal(out, 9.95, 8.5, 3.25, 10.25, 10.4, 3.6, "black", name="rafter_hook")
    # The Skullcrane servo on the left, taped on, its light and the cable to the trigger.
    metal(out, 5.6, 8.9, -3.0, 6.35, 11.6, 1.6, "servo", west="servo_side", name="servo")
    metal(out, 5.75, 11.6, -0.6, 6.25, 11.9, 1.2, "brass", name="servo_horn")
    K.box(out, 5.78, 11.6, -2.4, 6.18, 11.95, -2.0, "led", emit=True, name="servo_light", down=None)
    for z in (-2.95, 0.6):
        wrap(out, 5.5, 8.15, z, 9.85, 11.85, z + 0.5, "tape_black", name="servo_tape")
    wrap(out, 6.0, 7.6, 1.7, 6.3, 8.9, 2.0, "cable", upright=True, name="cable")
    wrap(out, 6.0, 7.35, 1.7, 6.3, 7.6, 4.3, "cable", name="cable")
    if mag != "none":
        out += nail_magazine(mag, k)
    return out


def ng_mag_base(kind):
    """Where the off hand takes the magazine: its base."""
    if kind == "strip":
        return rot_point((8.0, 2.7, -2.4), "x", NG_MAG_RAKE, NG_MAG_PIVOT)
    return rot_point((8.0, 2.4, -2.6), "x", NG_MAG_RAKE * 0.5, NG_MAG_PIVOT)


# ============================================================================================================ output

SUMMARY = []
RIGS = {}


def gun_tree(name, frames_of, variants=None):
    """The gun_animation range over frames 1-6 of one variant."""
    return range_dispatch("mymod:gun_animation",
                          [(k - 0.5, ref(frames_of(k))) for k in range(1, FRAMES + 1)] + [(FRAMES + 0.5, ref(frames_of(0)))],
                          ref(frames_of(0)))


def write_frames(name, frames, displays, suffix=""):
    for i, elements in enumerate(frames):
        check_all(f"{name}{suffix}_f{i}", elements)
        write(os.path.join(MODELS, name + suffix + (f"_f{i}" if i else "") + ".json"), model(K, elements, displays))


def write_gun(name, build, grip, support, fp_scale, flash_at=None, pistol=False, loaded_variant=False, fp_yaw=None,
              gui_fill=15.0, gui_rot=None):
    """Frames 0-6 of the moving part (and a loaded set, for guns whose round shows), the item model, and the summary."""
    frames = [build(k / FRAMES) for k in range(FRAMES + 1)]
    loaded = [build(k / FRAMES, True) for k in range(FRAMES + 1)] if loaded_variant else None
    rest = frames[0] + (loaded[0] if loaded else [])
    displays = gun_displays(grip, rest, fp_scale, pistol, fp_yaw, gui_fill, gui_rot)
    write_frames(name, frames, displays)
    body = gun_tree(name, lambda k: name + (f"_f{k}" if k else ""))
    if loaded:
        write_frames(name, loaded, displays, "_loaded")
        body = range_dispatch("mymod:loaded_rounds", [(0.5, gun_tree(name, lambda k: name + "_loaded" + (f"_f{k}" if k else "")))],
                              body)
    if flash_at:
        flashes = flash_models(MODELS, name, flash_at[0], flash_at[1], displays)
        body = with_flash(body, flashes)
    write(os.path.join(ITEMS, name + ".json"), {"model": body})
    report(name, frames[0], displays, grip, support)
    SUMMARY.append((name, (loaded or frames)[0], (loaded or frames)[FRAMES], displays))
    return displays


def report(name, rest, displays, grip, support):
    t = displays["firstperson_righthand"]
    print(f"{name}: {len(rest)} elements; firstperson {t['translation']} rot {t['rotation'][1]} scale {t['scale'][0]}; "
          f"grip {fp_point(displays, grip)} support {fp_point(displays, support)}; gui scale {displays['gui']['scale'][0]}")


def rig(name, displays, grip, support, **points):
    t = displays["firstperson_righthand"]
    RIGS[name] = dict(display=(t["translation"], t["rotation"][1], t["scale"][0]), grip=fp_point(displays, grip),
                      support=fp_point(displays, support), points=points)


D = write_gun("lever_action", lever_action, LA_GRIP, LA_SUPPORT, 0.55, flash_at=(-15.0, LA_BY))
rig("lever_action", D, LA_GRIP, LA_SUPPORT, port=LA_PORT, eject=LA_EJECT, loop=LA_LOOP, pivot=LA_PIVOT,
    loop_open=la_loop(1.0), muzzle=(8.0, LA_BY, -15.0), optic=(8.0, LA_OPTIC[1], LA_OPTIC[0]), lever_angle=(LA_LEVER, 0.0, 0.0))
D = write_gun("sawed_off", sawed_off, SO_GRIP, SO_SUPPORT, 0.6, flash_at=(-5.0, SO_BY))
rig("sawed_off", D, SO_GRIP, SO_SUPPORT, hinge=SO_HINGE, open_angle=(SO_OPEN, 0.0, 0.0), breech_z=(0.0, SO_BY, SO_BREECH),
    barrels=(SO_BARRELS[0], SO_BARRELS[1], 0.0), chamber_left=so_point((SO_BARRELS[0], SO_BY, SO_BREECH), 1.0),
    chamber_right=so_point((SO_BARRELS[1], SO_BY, SO_BREECH), 1.0), support_open=so_point(SO_SUPPORT, 1.0),
    lever=(8.7, 10.8, 9.2))
D = write_gun("snub_revolver", snub_revolver, SR_GRIP, (7.6, 6.6, 6.0), 0.65, flash_at=(-3.6, SR_BY), pistol=True)
rig("snub_revolver", D, SR_GRIP, (7.6, 6.6, 6.0), swing=(SR_SWING, 0.0, 0.0), cylinder_rear_z=(0.0, 0.0, SR_CYL_Z[1]),
    port_open=sr_cylinder((8.0, SR_CYL[1] + 0.85, SR_CYL_Z[1] + 0.1), 1.0),
    cylinder_open=sr_cylinder((8.0, SR_CYL[1], 2.8), 1.0), cylinder=(8.0, SR_CYL[1], 2.8), crane=(SR_CRANE[0], SR_CRANE[1], 2.8))
D = write_gun("slingshot", slingshot, SS_GRIP, ss_pouch(1.0), 0.65, pistol=True, loaded_variant=True, fp_yaw=-2,
              gui_fill=14.0, gui_rot=[20, -35, 0])
rig("slingshot", D, SS_GRIP, ss_pouch(1.0), pouch=ss_pouch(0.0), pouch_drawn=ss_pouch(1.0), fork=(8.0, 12.4, 0.0))
D = write_gun("flare_gun", flare_gun, FG_GRIP, (7.6, 7.0, 5.2), 0.65, flash_at=(-4.3, FG_BY), pistol=True)
rig("flare_gun", D, FG_GRIP, (7.6, 7.0, 5.2), hinge=FG_HINGE, open_angle=(FG_OPEN, 0.0, 0.0), breech=(8.0, FG_BY, FG_BREECH), chamber=fg_point((8.0, FG_BY, FG_BREECH), 1.0),
    barrel_top=fg_point((8.0, 11.5, -1.0), 1.0))
D = write_gun("harpoon_gun", harpoon_gun, HG_GRIP, HG_SUPPORT, 0.55, loaded_variant=True)
rig("harpoon_gun", D, HG_GRIP, HG_SUPPORT, muzzle=(8.0, HG_BY, HG_MUZZLE), work=HG_WORK, travel=(0.0, 0.0, HG_TRAVEL))

# The Nail Gun: magazine variants and frames like the M4's (GunAnimation: 7-12 the magazine leaving the well).
NG_DISPLAYS = None


def write_nail_gun():
    variants = {"strip": [nail_gun("strip", 0)] + [nail_gun("strip", k) for k in range(1, 7)],
                "coil": [nail_gun("coil", 0)] + [nail_gun("coil", k) for k in range(1, 7)]}
    empty = nail_gun("none")
    displays = gun_displays(NG_GRIP, variants["strip"][0], 0.6)
    names = {}
    for kind, frames in variants.items():
        base = "nail_gun" if kind == "strip" else "nail_gun_coil"
        for k, elements in enumerate(frames):
            n = base + (f"_m{k}" if k else "")
            check_all(n, elements)
            write(os.path.join(MODELS, n + ".json"), model(K, elements, displays))
        names[kind] = base
    check_all("nail_gun_no_mag", empty)
    write(os.path.join(MODELS, "nail_gun_no_mag.json"), model(K, empty, displays))

    def loaded(k):
        m = lambda kind: ref(names[kind] + (f"_m{k}" if k else ""))
        return has_component("mymod:loaded_magazine",
                             __import__("collections").OrderedDict([("type", "minecraft:select"), ("property", "minecraft:component"),
                                                                    ("component", "mymod:loaded_magazine_type"),
                                                                    ("cases", [{"when": "mymod:nail_coil", "model": m("coil")}]),
                                                                    ("fallback", m("strip"))]),
                             ref("nail_gun_no_mag"))
    body = range_dispatch("mymod:gun_animation", [(6 + k - 0.5, loaded(k)) for k in range(1, 7)] + [(12.5, loaded(0))], loaded(0))
    write(os.path.join(ITEMS, "nail_gun.json"), {"model": body})
    report("nail_gun", variants["strip"][0], displays, NG_GRIP, NG_SUPPORT)
    SUMMARY.append(("nail_gun", variants["strip"][0], variants["coil"][0], displays))
    base = ng_mag_base("strip")
    step = ng_mag_offset(1)
    rig("nail_gun", displays, NG_GRIP, NG_SUPPORT, mag_base=base, mag_step_item=tuple(fp_vector(displays, step)))
    RIGS["nail_gun"]["mag_base_item"] = fp_point(displays, base)
    return displays


NG_DISPLAYS = write_nail_gun()

# ============================================================================================================== ammo

ROUND_DISPLAYS = {
    "gui": display([0, -90, 45], [0, 0, 0], 2.6),
    "ground": display([0, 0, 0], [0, 2.0, 0], 1.0),
    "fixed": display([0, -90, 45], [0, 0, 0], 2.6),
    "thirdperson_righthand": display([0, 90, -30], [0, 2.0, 0], 1.0),
    "firstperson_righthand": display([0, 90, -30], [0, 3.0, 0], 1.0),
}


def round_38(spent=False, silver=False):
    """A .38 Special lying along z, centred on (8, 8, 8): a brass case with its rim, and a lead or silver round nose."""
    out = []
    K.rod(out, 7.6, 9.3, 0.24, "brass_md", cap="brass_lo" if spent else "brass_lt", caps=(spent, True), name="case")
    K.rod(out, 9.3, 9.42, 0.28, "brass_lt", cap="brass_lt", name="rim")
    if not spent:
        tone = "steel_lt" if silver else "iron_lt"
        K.rod(out, 7.0, 7.6, 0.23, tone, cap=tone, caps=(True, False), name="bullet")
        K.rod(out, 6.85, 7.0, 0.14, tone, cap=tone, caps=(True, False), name="bullet_nose")
    return out


def flare_shell(spent=False):
    out = []
    K.rod(out, 6.8, 9.3, 0.55, "red_md", cap="black" if spent else "red_lt", caps=(True, True), name="hull")
    K.rod(out, 9.3, 9.6, 0.6, "brass_md", cap="brass_lt", caps=(False, True), name="head")
    return out


def spear():
    out = []
    K.rod(out, 2.5, 13.5, 0.2, "steel_lt", cap="steel_md", name="shaft")
    K.rod(out, 1.6, 2.5, 0.13, "steel_hi", cap="steel_hi", caps=(True, False), name="tip")
    K.box(out, 7.92, 7.75, 2.4, 8.08, 8.0, 3.6, "steel_md", name="barb")
    K.box(out, 7.92, 8.0, 2.4, 8.08, 8.25, 3.6, "steel_md", name="barb")
    return out


def nugget():
    out = []
    K.box(out, 7.55, 7.6, 7.5, 8.45, 8.35, 8.5, "iron_lt", up="iron_hi", down="iron_lo")
    K.box(out, 7.7, 7.45, 7.65, 8.3, 7.6, 8.35, "iron_md")
    return out


for name, elements in (("bullet_38_fmj_3d", round_38()), ("bullet_38_silver_3d", round_38(silver=True)),
                       ("bullet_38_fmj_spent", round_38(True)), ("bullet_38_silver_spent", round_38(True)),
                       ("flare_shell_3d", flare_shell()), ("flare_shell_spent", flare_shell(True)),
                       ("harpoon_spear_3d", spear()), ("iron_nugget_3d", nugget())):
    check_all(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, ROUND_DISPLAYS))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


def rgba(hx):
    return ((hx >> 16) & 255, (hx >> 8) & 255, hx & 255, 255)


def sprites():
    b = RAMPS["brass"]
    pal = {"B": rgba(b[0]), "b": rgba(b[1]), "m": rgba(b[2]), "d": rgba(b[3]), "D": rgba(b[4]),
           "l": rgba(0xA8ACB2), "L": rgba(0xD4D8DC), "o": rgba(0x6E737A), "s": rgba(0xE8EEF4), "S": rgba(0xFFFFFF),
           "k": rgba(0x16171A), "K": rgba(0x34363B), "y": rgba(RAMPS["yellow"][1]), "Y": rgba(RAMPS["yellow"][0]),
           "Z": rgba(RAMPS["yellow"][3]), "r": rgba(RAMPS["red"][1]), "R": rgba(RAMPS["red"][0]), "q": rgba(RAMPS["red"][3]),
           "p": rgba(0xD8D0B8), "t": rgba(RAMPS["steel"][1]), "T": rgba(RAMPS["steel"][0]), "u": rgba(RAMPS["steel"][3]),
           "e": rgba(RAMPS["steel"][4]), "n": rgba(0x9C9C9C)}
    rows = {
        # A short brass case on the diagonal, mouth up-right, rim bottom-left.
        "bullet_casing_38": [
            "................", "................", "................", "................", "..........kk....",
            ".........bBbk...", "........bBbd....", ".......bBbd.....", "......bBbd......", ".....bBbd.......",
            "....dbbd........", "...dDdd.........", "...dDd..........", "................", "................",
            "................"],
        "bullet_38_fmj": [
            "................", "................", "............oo..", "...........olLo.", "..........olLo..",
            ".........bBbo...", "........bBbd....", ".......bBbd.....", "......bBbd......", ".....bBbd.......",
            "....dbbd........", "...dDdd.........", "...dDd..........", "................", "................",
            "................"],
        "bullet_38_silver": [
            "................", "................", "............tt..", "...........tSst.", "..........tSst..",
            ".........bBbt...", "........bBbd....", ".......bBbd.....", "......bBbd......", ".....bBbd.......",
            "....dbbd........", "...dDdd.........", "...dDd..........", "................", "................",
            "................"],
        # Three collated nails on a paper strip.
        "iron_nail": [
            "................", "................", "...LL.LL.LL.....", "...ln.ln.ln.....", "....n..n..n.....",
            "...pnpppnppnp...", "...pnpppnppnp...", "....n..n..n.....", "....n..n..n.....", "....n..n..n.....",
            "....o..o..o.....", "....o..o..o.....", "....o..o..o.....", "................", "................",
            "................"],
        "nail_strip": [
            "................", "......kkkk......", "......KyyK......", "......KyyK......", ".....kKLLKk.....",
            ".....kKllKk.....", ".....kKLLKk.....", ".....kKllKk.....", ".....kKLLKk.....", ".....kKllKk.....",
            ".....kKLLKk.....", ".....kKllKk.....", ".....kKLLKk.....", ".....kZyyZk.....", "......kkkk......",
            "................"],
        "nail_coil": [
            "................", "......kkkk......", "....kkKKKKkk....", "...kKlnnnnlKk...", "..kKlnLLLLnlKk..",
            "..kKnLkkkkLnKk..", ".kKnLkyYYykLnKk.", ".kKnLkYZZYkLnKk.", ".kKnLkYZZYkLnKk.", ".kKnLkyYYykLnKk.",
            "..kKnLkkkkLnKk..", "..kKlnLLLLnlKk..", "...kKlnnnnlKk...", "....kkKKKKkk....", "......kkkk......",
            "................"],
        "harpoon_spear": [
            "..............T.", ".............TtT", "............tTu.", "...........tTu..", "..........ttu.u.",
            ".........tTu....", "........tTu.....", ".......tTu......", "......tTu.......", ".....tTu........",
            "....tTu.........", "...tTu..........", "..tTu...........", ".kke............", ".kk.............",
            "................"],
        "flare_shell": [
            "................", "................", "...........qqq..", "..........qRrq..", ".........qRrrq..",
            "........qRrrq...", ".......qRrrq....", "......qRrrq.....", ".....qRrrq......", "....bBbd........",
            "...bBbd.........", "..dBbd..........", "..ddd...........", "................", "................",
            "................"],
    }
    for name, r in rows.items():
        sprite(os.path.join(TEX, f"item/{name}.png"), r, pal)
        write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
        if name not in ("nail_strip", "nail_coil"):
            write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})
    # The magazines' sprites show their fill bar in the inventory; plain models.
    for name in ("nail_strip", "nail_coil"):
        write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


sprites()

# ========================================================================================================= Java rigs


def jv(v):
    return "new Vector3f(" + ", ".join(f"{x:.4f}f" for x in v) + ")"


def write_java():
    lines = ["package com.jheinz.mymod.client.gunsmith;", "", "import org.joml.Vector3f;", "",
             "/**", " * Where things are on the Gunsmith guns: generated by tools/gen_gunsmith.py from the models, so the animations",
             " * (GunsmithAnimation, GunsmithClient) stay on the parts. Display transforms are first-person (translation in pixels,",
             " * yaw in degrees, scale); GRIP and SUPPORT are hand holds in the held item's space (blocks); every other point is in",
             " * the gun's model pixels.", " */", "final class GunsmithRigs {"]
    for name, r in RIGS.items():
        up = name.upper()
        tr, yaw, sc = r["display"]
        lines.append(f"\t// {name}")
        lines.append(f"\tstatic final float[] {up}_DISPLAY = {{{tr[0]:.4f}f, {tr[1]:.4f}f, {tr[2]:.4f}f, {yaw:.1f}f, {sc:.3f}f}};")
        lines.append(f"\tstatic final Vector3f {up}_GRIP = {jv(r['grip'])};")
        lines.append(f"\tstatic final Vector3f {up}_SUPPORT = {jv(r['support'])};")
        for k, v in r["points"].items():
            lines.append(f"\tstatic final Vector3f {up}_{k.upper()} = {jv(v)};")
        if "mag_base_item" in r:
            lines.append(f"\tstatic final Vector3f {up}_MAG_BASE_ITEM = {jv(r['mag_base_item'])};")
    lines += ["", "\tprivate GunsmithRigs() {", "\t}", "}", ""]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write("\n".join(lines))


write_java()

# ============================================================================================================ preview

ATLASES = {"#gunsmith": (K.atlas, K.size)}


def gun_views(name, rest, worked, displays, cell_w=420, cell_h=210, fp_w=480, fp_h=270, icon=48):
    lo, hi = bounds(rest)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    span = max(hi[2] - lo[2], hi[1] - lo[1])
    k = min(16.0, 360.0 / max(span, 1.0))
    cells = []
    for view, elements in ((SIDE, rest), (RIGHT_FRONT, worked), (LEFT_REAR, rest)):
        cells.append((render(elements, ATLASES, ortho(view, k, cell_w, cell_h, c), lambda n: n, cell_w, cell_h), cell_w, cell_h, 1))
    project, turn = first_person(displays, fp_w, fp_h)
    cells.append((render(rest, ATLASES, project, turn, fp_w, fp_h), fp_w, fp_h, 1))
    project, turn = gui_icon(displays, icon)
    ic = render(rest, ATLASES, project, turn, icon, icon)
    cells.append(([(139, 139, 139) if v is None else v for v in ic], icon, icon, 2))
    return cells


if PREVIEW:
    rows = [gun_views(name, rest, worked, displays) for name, rest, worked, displays in SUMMARY]
    if PREVIEW.endswith("/"):
        os.makedirs(PREVIEW, exist_ok=True)
        for (name, rest, worked, displays), row in zip(SUMMARY, rows):
            compose([row], os.path.join(PREVIEW, name + ".png"))
            # Close-ups: the right side and the worked part from the front, big enough to judge the parts.
            lo, hi = bounds(rest)
            c = [(lo[a] + hi[a]) / 2 for a in range(3)]
            k = 880.0 / max(hi[2] - lo[2], hi[1] - lo[1], 8.0)
            big = []
            for view, elements in ((SIDE, rest), (RIGHT_FRONT, worked), (LEFT_REAR, rest)):
                big.append([(render(elements, ATLASES, ortho(view, k * 0.9, 960, 520, c), lambda n: n, 960, 520), 960, 520, 1)])
            compose(big, os.path.join(PREVIEW, name + "_big.png"))
    else:
        compose(rows, PREVIEW)
    print("preview:", PREVIEW)
print("gunsmith guns done")
