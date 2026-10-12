"""Builds the M249 SAW, its Weapon Station parts and the M249 ammo box.

Proportions follow a side-on photo of an M249 (about 41 photo pixels to a model pixel): a wide, slab-sided riveted
receiver, a fat polymer stock with a hump and a ribbed buttplate, a chunky ribbed pistol grip, a feed cover hinged at
its front with a full-length rail, the rear aperture sight fixed on the receiver behind it, a carry handle angled up and
forward off to the left of the barrel, a grey perforated heat shield and a railed handguard with the bipod folded under
them, the front sight post on its triangular base atop the gas block, a flash hider, and a tall ammo box under the
receiver, wider than the receiver itself.

The gun's item definition stacks layers, each picked by item model properties (see M249Animation):
  - the body, by mymod:gun_animation: 0 at rest, 1-6 the charging handle drawn back, 7-12 the feed cover swinging
    open (its back end up, 12 fully open), 13-16 cover open while the box is changed;
  - the ammo box: seated, lowered (14, 15) or gone (16), only with one loaded;
  - the belt: from the box up the left side into the feed tray, with as many rounds as are left once fewer than
    nine remain (mymod:loaded_rounds), so it shortens as the box runs dry; lifted out and hanging from the box
    while the box is changed (13-15);
  - the bipod, by mymod:bipod: folded under the handguard (0) through to deployed (3) as the holder crouches;
  - the muzzle flash, by mymod:muzzle_flash.

The iron sights are a ghost ring and a post: the rear sight is an open octagonal ring the eye looks through (nothing
in front of it rises above the rail), and the front post's tip sits on the ring's centre line, SIGHT_Y.

Model space is in pixels: the bore runs along z at x = 8, y = 9 with the muzzle toward -z, +x is the gun's right.
GunDisplay, GunHands, GunAim, LaserBeams and M249Animation mirror the display transforms, grip points, sight line and
part positions printed or defined here, so move them together. Run tools/gen_attachments.py afterwards: it wraps the
item definition with the attachment layers (and swings the optic up with the feed cover it's railed to)."""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, FLASH, check, move, rotate, rx, rz, write, model, ref, EMPTY, range_dispatch, composite,
                    has_component, display, display_matrix, placed, centred, bounds_centre, DIAGONAL, sprite, flash_models,
                    with_flash)

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))

K = Kit("m249", "item/m249", seed=249)
BLACK, BLACK_DARK, BLACK_WORN = 0x232523, 0x141514, 0x464943
POLY, POLY_DARK = 0x1C1D1E, 0x101112
GREY, GREY_DARK = 0x5B5E59, 0x3A3C38
BOX, BOX_DARK, BOX_LIGHT = 0x46544B, 0x313B34, 0x5E6E63

K.swatch("steel", 0, 0, 8, 2, lambda x, y, w, h: K.rgb(BLACK_WORN if K.rnd.random() < 0.05 else BLACK, jitter=3))
K.swatch("steel_dark", 8, 0, 12, 2, lambda x, y, w, h: K.rgb(BLACK_DARK, jitter=2))
K.swatch("steel_worn", 12, 0, 16, 2, lambda x, y, w, h: K.rgb(BLACK_WORN if K.rnd.random() < 0.6 else BLACK, jitter=5))
K.swatch("poly", 0, 2, 4, 4, lambda x, y, w, h: K.rgb(POLY, jitter=2))
# Ribs across the pistol grip and buttplate (stripes down v), and the handguard's grooves (stripes across u).
K.swatch("ribs_v", 4, 2, 8, 4, lambda x, y, w, h: K.rgb(POLY_DARK if y % 3 == 0 else 0x2A2B2D if y % 3 == 1 else POLY))
K.swatch("ribs_u", 8, 2, 12, 4, lambda x, y, w, h: K.rgb(POLY_DARK if x % 4 == 0 else 0x2A2B2D if x % 4 == 1 else POLY))
K.swatch("grey", 12, 2, 16, 4, lambda x, y, w, h: K.rgb(GREY, jitter=3))
# The heat shield's dotted panel.
K.swatch("dots", 0, 4, 8, 6, lambda x, y, w, h: K.rgb(GREY_DARK if x % 3 == 1 and y % 3 == 1 else GREY, jitter=2))
K.swatch("box", 8, 4, 12, 6, lambda x, y, w, h: K.rgb(BOX_DARK if K.rnd.random() < 0.03 else BOX, jitter=3))
K.swatch("box_edge", 12, 4, 14, 6, lambda x, y, w, h: K.rgb(BOX_LIGHT if y == 0 else BOX, jitter=2))
K.swatch("brass", 14, 4, 16, 6, lambda x, y, w, h: K.rgb(0xF2D27C if y == 0 else 0xCFA448, jitter=5))
K.swatch("copper", 0, 6, 2, 8, lambda x, y, w, h: K.rgb(0xC0703A if y > 0 else 0xE08E52, jitter=4))
K.swatch("link", 2, 6, 4, 8, lambda x, y, w, h: K.rgb(0x151515 if y % 2 else 0x262626))
K.swatch("rubber", 4, 6, 6, 8, lambda x, y, w, h: K.rgb(0x0E0E0E if y % 2 == 0 else 0x1B1B1B))
K.swatch("bore", 6, 6, 8, 8, lambda x, y, w, h: K.rgb(0x050505 if math.hypot(x - 3.5, y - 3.5) < 3.0 else 0x2A2C26), picture=True)
K.swatch("slots", 8, 6, 12, 8, lambda x, y, w, h: K.rgb(0x050505 if 3 <= y <= 12 and x % 4 == 1 else BLACK, jitter=2))
K.swatch("rail", 12, 6, 16, 8, lambda x, y, w, h: K.rgb(BLACK_DARK if x % 3 == 0 else BLACK, jitter=2))
K.swatch("chamber", 0, 8, 2, 10, lambda x, y, w, h: K.rgb(0x08090A, jitter=1))
# Rivets and pins: bright heads with a dark rim.
K.swatch("rivet", 2, 8, 4, 10, lambda x, y, w, h: K.rgb(0x8A8E88 if 2 <= x <= 5 and 2 <= y <= 5 else 0x2C2E2B), picture=True)
# The sights: matt black, darker than the gun, so they read against the sky.
K.swatch("sight", 4, 8, 6, 10, lambda x, y, w, h: K.rgb(0x070707, jitter=1))
K.swatch("box_mouth", 6, 8, 10, 10, lambda x, y, w, h: K.rgb(0x101410 if 1 <= x <= w - 2 and 1 <= y <= h - 2 else BOX_DARK), picture=True)


def box_stencil(x, y, w, h):
    """Stencilled lot markings and a latch outline on the ammo box's side."""
    if 3 <= y <= 4 and 3 <= x <= w - 4 and x % 3 != 2:
        return K.rgb(0xE0DCC8)
    if y == 7 and 3 <= x <= w // 2 and x % 2 == 0:
        return K.rgb(0xD0CCB8)
    if y in (1, h - 2) and 1 <= x <= w - 2:
        return K.rgb(BOX_DARK)
    return K.rgb(BOX, jitter=3)


K.swatch("box_stencil", 0, 10, 8, 14, box_stencil, picture=True)
K.save(TEX)

BX, BY = 8.0, 9.0
MUZZLE = -15.9
RECEIVER = (6.7, 20.4)
HANDLE_TRAVEL = 4.0
HINGE = (8.0, 10.6, 7.3)           # the feed cover's hinge, at its front
COVER_OPEN = 75.0                   # its back end swings up this far
SIGHT_Y = 11.95                     # the sight line: the rear ring's centre and the front post's tip
REAR_SIGHT_Z = 18.1                 # the rear ring
FRONT_SIGHT_Z = -5.7                # the front post
GRIP = (8.0, 4.0, 16.4)             # the shooting hand on the pistol grip
SUPPORT = (8.0, 6.1, 2.0)           # the off hand under the heat shield
BIPOD_PIVOT_Z = -5.7
BELT_PITCH = 0.46
BELT_MAX = 9
RAIL = (9.6, 16.4, 11.4)            # front, back and top of the cover's rail


# ------------------------------------------------------------------------------------------------- barrel group


def barrel():
    out = []
    K.rod(out, MUZZLE, -13.9, 0.5, "steel_dark", cx=BX, cy=BY, mark="slots", name="flash_hider")
    K.sticker(out, BX - 0.36, BY - 0.36, MUZZLE - 0.03, BX + 0.36, BY + 0.36, MUZZLE - 0.02, "bore", "north")
    K.rod(out, -13.9, RECEIVER[0] + 0.3, 0.38, "steel", cx=BX, cy=BY, name="barrel", caps=(False, False))
    # Gas block and regulator; on it the front sight's triangular base, its protective ears, and the post.
    K.chamfered(out, 7.35, 7.4, -6.6, 8.65, 9.6, -4.8, 0.2, "steel", name="gas_block")
    K.box(out, 7.6, 7.0, -7.1, 8.4, 7.8, -6.6, "steel_worn", name="gas_regulator")
    K.box(out, 7.55, 9.6, -6.5, 8.45, 10.2, -4.9, "steel_dark", name="front_sight_base")
    for z_foot, lean in ((-6.45, 24.0), (-4.95, -24.0)):
        leg = []
        K.box(leg, 7.6, 9.9, z_foot - 0.25, 8.4, 11.3, z_foot + 0.25, "steel_dark", name="front_sight_base")
        out += rotate(leg, rx(lean), (8.0, 9.9, z_foot))
    for x0 in (7.25, 8.5):
        K.box(out, x0, 10.6, FRONT_SIGHT_Z - 0.35, x0 + 0.25, SIGHT_Y + 0.1, FRONT_SIGHT_Z + 0.35, "sight", name="front_sight_ear")
    K.box(out, 7.8, 10.7, FRONT_SIGHT_Z - 0.18, 8.2, SIGHT_Y, FRONT_SIGHT_Z + 0.18, "sight", name="front_sight_post")
    # Bipod mount and yoke under the gas block.
    K.box(out, 7.2, 6.6, -6.4, 8.8, 7.4, -5.0, "steel_dark", name="bipod_mount")
    K.box(out, 6.6, 6.0, -6.0, 9.4, 6.6, -5.4, "steel_dark", name="bipod_yoke")
    # Handguard: a railed front section, then the grey perforated heat shield back to the receiver.
    K.chamfered(out, 6.6, 6.1, -3.0, 9.4, 9.0, 1.2, 0.45, "ribs_u", cap="poly", up="poly", down="poly", name="handguard")
    K.chamfered(out, 6.6, 6.1, 1.2, 9.4, 9.0, RECEIVER[0], 0.45, "dots", cap="grey", up="grey", down="grey", name="heat_shield")
    for x0 in (6.35, 9.4):
        K.box(out, x0, 7.15, -2.4, x0 + 0.25, 7.85, 0.9, "rail", name="handguard_rail")
    K.box(out, 9.4, 8.05, 1.6, 9.45, 8.55, 5.8, "steel_dark", name="heat_shield_slot")
    # Carry handle: a clamp on the barrel, and the handle swung up and forward off to the left, clear of the sights.
    K.rod(out, 4.6, 6.2, 0.55, "steel_dark", cx=BX, cy=BY, name="carry_handle_clamp")
    handle = []
    K.box(handle, 6.55, 9.0, 5.0, 6.95, 12.2, 5.4, "steel_dark", name="carry_handle_arm")
    K.chamfered(handle, 6.35, 11.95, 1.9, 7.15, 12.75, 5.6, 0.25, "ribs_u", cap="poly", up="poly", down="poly", name="carry_handle")
    rotate(handle, rx(-22), (6.75, 9.3, 5.2))
    out += rotate(handle, rz(32), (6.75, 9.3, 5.2))
    return out


def bipod(frac):
    """The bipod's legs, folded back under the handguard (0) or swung down and splayed (1)."""
    out = []
    for side in (-1, 1):
        x0 = 6.8 if side < 0 else 8.8
        leg = []
        K.box(leg, x0, 5.65, BIPOD_PIVOT_Z - 0.2, x0 + 0.4, 6.05, 3.9, "steel", name="bipod_leg")
        K.box(leg, x0 - 0.05, 5.6, 2.2, x0 + 0.45, 6.1, 3.95, "steel_worn")
        K.box(leg, x0 - 0.08, 5.55, 3.95, x0 + 0.48, 6.15, 4.5, "rubber", name="bipod_foot")
        pivot = (x0 + 0.2, 5.85, BIPOD_PIVOT_Z)
        rotate(leg, rx(90 * frac), pivot)
        rotate(leg, rz(12 * frac * side), pivot)
        out += leg
    return out


# ----------------------------------------------------------------------------------------------- receiver group


def cover(angle):
    """The feed cover and its rail, its back end swung up by `angle` degrees about the hinge at its front."""
    out = []
    K.chamfered(out, 6.6, 10.3, HINGE[2] - 0.1, 9.4, 10.9, 17.0, 0.25, "steel", name="feed_cover")
    K.box(out, 7.4, 10.9, 16.45, 8.6, 11.2, 17.0, "steel_worn", name="cover_latch")
    K.box(out, 7.25, 10.9, RAIL[0], 8.75, RAIL[2], RAIL[1], "rail", name="cover_rail")
    K.rod(out, HINGE[2] - 0.35, HINGE[2] + 0.35, 0.3, "steel_worn", cx=9.45, cy=HINGE[1])
    return rotate(out, rx(-angle), HINGE) if angle else out


def rivets(out, x, face, points):
    for y, z in points:
        K.box(out, x - 0.08 if face == "west" else x, y - 0.17, z - 0.17, x + 0.08 if face == "east" else x, y + 0.17, z + 0.17,
              "steel_worn", **{face: "rivet"})


def receiver(handle):
    out = []
    z0, z1 = RECEIVER
    K.chamfered(out, 6.55, 6.0, z0, 9.45, 10.3, z1, 0.35, "steel", name="receiver")
    K.chamfered(out, 6.85, 7.5, z0 - 0.5, 9.15, 10.0, z0 + 0.1, 0.27, "steel_worn", name="trunnion")
    # The side plates: a raised strip along each, rivets and pins.
    for x, face in ((9.45, "east"), (6.55, "west")):
        K.box(out, min(x, x + (0.07 if face == "east" else -0.07)), 9.0, 7.2, max(x, x + (0.07 if face == "east" else -0.07)), 9.25, 19.8,
              "steel_worn", name="side_strip")
        rivets(out, x, face, [(9.7, 8.0), (9.7, 13.0), (9.7, 19.4), (7.9, 12.4), (6.7, 8.3), (6.7, 13.6), (6.7, 17.2),
                              (7.6, 19.6), (8.4, 15.6)])
    # The belt goes in on the left; the charging handle runs in a slot on the right.
    K.sticker(out, 6.52, 9.5, 8.4, 6.55, 10.25, 11.2, "chamber", "west", name="feed_opening")
    K.sticker(out, 9.45, 8.25, 7.0, 9.48, 8.95, 12.4, "chamber", "east", name="charging_slot")
    K.box(out, 9.48, 8.2, 7.2 + handle, 10.05, 9.0, 8.1 + handle, "steel_worn", name="charging_handle")
    K.box(out, 10.05, 8.35, 7.3 + handle, 10.35, 8.85, 8.0 + handle, "steel_dark")
    # The rear sight, fixed on the receiver behind the cover: a block, two ears, and the open ghost ring between them.
    K.box(out, 7.2, 10.3, 17.15, 8.8, 11.4, 19.4, "steel_dark", name="rear_sight_base")
    for x0 in (6.95, 8.75):
        K.box(out, x0, 11.4, REAR_SIGHT_Z - 0.5, x0 + 0.3, SIGHT_Y + 0.75, REAR_SIGHT_Z + 0.5, "sight", name="rear_sight_ear")
    inner, thick = 0.3, 0.3
    width = 2 * (inner + thick) * math.tan(math.pi / 8) + 0.02
    for k in range(8):
        K.box(out, BX - width / 2, SIGHT_Y + inner, REAR_SIGHT_Z - 0.15, BX + width / 2, SIGHT_Y + inner + thick, REAR_SIGHT_Z + 0.15,
              "sight", rot=("z", k * 45.0, (BX, SIGHT_Y, REAR_SIGHT_Z)) if k else None, name="rear_sight_ring")
    # Trigger guard and trigger.
    K.box(out, 7.55, 4.0, 11.6, 8.45, 4.35, 14.7, "poly", name="trigger_guard")
    K.box(out, 7.55, 4.35, 11.6, 8.45, 6.0, 11.95, "poly")
    K.box(out, 7.82, 4.6, 12.7, 8.18, 6.0, 13.05, "steel_worn", rot=("x", -10, (8.0, 6.0, 12.9)), name="trigger")
    K.box(out, 6.45, 6.6, 15.0, 9.55, 6.95, 15.35, "steel_dark", name="safety")
    # The chunky ribbed pistol grip, raked back and flared at the bottom.
    grip = []
    K.chamfered(grip, 7.0, 1.4, 14.6, 9.0, 6.0, 17.3, 0.5, "poly", cap="poly", north="ribs_v", south="ribs_v", name="pistol_grip")
    K.chamfered(grip, 6.9, 1.0, 14.4, 9.1, 1.5, 17.6, 0.3, "poly", name="pistol_grip_base")
    out += rotate(grip, rx(-18), (8.0, 6.0, 15.9))
    return out


def stock():
    """The fat polymer stock: a neck off the receiver, a dip, a humped comb, a deep belly down to the toe, and the
    ribbed buttplate with its sling loop."""
    out = []
    neck = []
    K.chamfered(neck, 6.8, 5.6, 20.1, 9.2, 10.0, 23.6, 0.5, "poly", name="stock_neck")
    out += rotate(neck, rx(14), (8.0, 10.0, 20.1))
    K.chamfered(out, 6.8, 5.2, 22.4, 9.2, 9.05, 26.8, 0.5, "poly", name="stock")
    K.chamfered(out, 6.75, 3.4, 26.5, 9.25, 9.6, 30.7, 0.6, "poly", name="stock_hump")
    belly = []
    K.chamfered(belly, 6.85, 3.4, 20.8, 9.15, 6.2, 27.2, 0.5, "poly", name="stock_belly")
    out += rotate(belly, rx(21), (8.0, 3.4, 27.2))
    K.chamfered(out, 6.7, 3.15, 30.6, 9.3, 9.85, 31.35, 0.3, "ribs_v", cap="rubber", south="rubber", name="buttplate")
    K.box(out, 7.75, 9.85, 30.75, 8.25, 10.3, 31.2, "steel_worn", name="sling_loop")
    return out


# --------------------------------------------------------------------------------------------- box and belt


BOX_Z = (8.1, 11.4)


def ammo_box(drop=0.0):
    """The tall plastic box under the receiver, wider than the receiver."""
    out = []
    z0, z1 = BOX_Z
    K.chamfered(out, 5.55, -1.4, z0, 9.75, 5.6, z1, 0.6, "box", east="box_stencil", west="box_stencil", up="box", name="ammo_box")
    K.chamfered(out, 5.45, 5.6, z0 - 0.05, 9.85, 5.95, z1 + 0.05, 0.3, "box_edge", name="ammo_box_lid")
    K.sticker(out, 5.75, 5.95, z0 + 0.4, 6.65, 5.98, z1 - 0.3, "box_mouth", "up", name="ammo_box_mouth")
    K.box(out, 7.3, 5.95, z0 + 0.6, 9.1, 6.0, z1 - 0.6, "steel_dark", name="ammo_box_clip")
    return move(out, dx=-0.4 * drop / 3.0, dy=-drop)


def bezier(p0, p1, p2, t):
    return tuple((1 - t) ** 2 * p0[i] + 2 * (1 - t) * t * p1[i] + t * t * p2[i] for i in range(2))


def along(p0, p1, p2, spacing, count):
    """count points spaced along a quadratic curve from p0, with the curve's direction (degrees) at each."""
    samples = [bezier(p0, p1, p2, i / 400) for i in range(401)]
    out, travelled, want = [], 0.0, 0.0
    for a, b in zip(samples, samples[1:]):
        seg = math.dist(a, b)
        while len(out) < count and travelled + seg >= want:
            t = (want - travelled) / seg if seg else 0
            x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            out.append((x, y, math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))))
            want += spacing
        travelled += seg
    return out


ROUND_Z = (8.4, 9.05, 10.95)        # bullet tip, case mouth, case head


def cartridge(out, x, y, angle):
    """One linked 5.56 round lying along z (bullet forward), turned to the belt's run."""
    tip, mouth, head = ROUND_Z
    r = []
    K.box(r, x - 0.21, y - 0.21, mouth, x + 0.21, y + 0.21, head, "brass", name="round")
    K.box(r, x - 0.14, y - 0.14, tip, x + 0.14, y + 0.14, mouth, "copper")
    K.box(r, x - 0.25, y - 0.25, 9.6, x + 0.25, y + 0.25, 9.9, "link", north=None, south=None)
    K.box(r, x - 0.25, y - 0.25, 10.35, x + 0.25, y + 0.25, 10.65, "link", north=None, south=None)
    out += rotate(r, rz(angle - 90), (x, y, 9.6))


FEED = (6.3, 10.0)
MOUTH = (6.15, 6.05)


def belt(n):
    """The belt with n rounds still out of the box, hugging the receiver's left side up into the feed (rounds nearest
    the feed first), and the round on the feed tray."""
    out = []
    for x, y, a in along(FEED, (5.75, 8.1), MOUTH, BELT_PITCH, n):
        cartridge(out, x, y, a)
    tip, mouth, head = ROUND_Z
    K.box(out, 7.0, 10.3, mouth, 7.42, 10.72, head, "brass", name="tray_round")
    K.box(out, 7.07, 10.37, tip, 7.35, 10.65, mouth, "copper")
    return out


def hanging_belt(drop):
    """Lifted off the tray: the belt's end hangs out of the box mouth where the hand holds it."""
    out = []
    for x, y, a in along((4.5, 9.4), (4.8, 6.8), MOUTH, BELT_PITCH, 8):
        cartridge(out, x, y, a)
    return move(out, dx=-0.4 * drop / 3.0, dy=-drop)


# --------------------------------------------------------------------------------------------------- output


def gun_displays(elements, fp_scale, gui_scale):
    fp_rot = [0, -8, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, GRIP, FP_GRIP_TARGET), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.62, GRIP, TP_GRIP_TARGET), 0.62)
    c = bounds_centre(elements)
    gui_rot = [0, -90, 0]
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], 0.32, c, 2.0), 0.32),
        "fixed": display(gui_rot, centred(gui_rot, gui_scale * 1.3, c), gui_scale * 1.3),
    }


def part_displays(elements, scale):
    c = bounds_centre(elements)
    return {
        "gui": display(DIAGONAL, centred(DIAGONAL, scale, c), scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], scale * 0.7, c, 2.0), scale * 0.7),
        "fixed": display(DIAGONAL, centred(DIAGONAL, scale * 1.2, c), scale * 1.2),
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


def body(handle=0.0, angle=0.0):
    return barrel() + receiver(handle) + cover(angle) + stock()


everything = body() + ammo_box() + belt(BELT_MAX) + bipod(1.0) + bipod(0.0) + cover(COVER_OPEN) + hanging_belt(0)
DISPLAYS = gun_displays(everything, 0.5, 0.3)


def out_model(name, elements):
    check(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, DISPLAYS))
    return ref(name)


rest = out_model("m249", body())
handle_frames = [out_model(f"m249_c{k}", body(handle=HANDLE_TRAVEL * k / 6)) for k in range(1, 7)]
cover_frames = [out_model(f"m249_o{k}", body(angle=COVER_OPEN * k / 6)) for k in range(1, 7)]
body_layer = range_dispatch("mymod:gun_animation",
                            [(k - 0.5, handle_frames[k - 1]) for k in range(1, 7)]
                            + [(k - 0.5, cover_frames[k - 7]) for k in range(7, 13)]
                            + [(12.5, cover_frames[-1]), (16.5, rest)], rest)

seated = out_model("m249_box", ammo_box())
box_layer = range_dispatch("mymod:gun_animation", [
    (13.5, out_model("m249_box_lowered_1", ammo_box(1.5))), (14.5, out_model("m249_box_lowered_2", ammo_box(3.0))),
    (15.5, EMPTY), (16.5, seated)], seated)

belts = range_dispatch("mymod:loaded_rounds", [(n - 0.5, out_model(f"m249_belt_{n}", belt(n))) for n in range(1, BELT_MAX + 1)], EMPTY)
belt_layer = range_dispatch("mymod:gun_animation", [
    (12.5, out_model("m249_belt_hanging_0", hanging_belt(0))), (13.5, out_model("m249_belt_hanging_1", hanging_belt(1.5))),
    (14.5, out_model("m249_belt_hanging_2", hanging_belt(3.0))), (15.5, EMPTY), (16.5, belts)], belts)

bipod_layer = range_dispatch("mymod:bipod", [(k - 0.5, out_model(f"m249_bipod_{k}", bipod(k / 3))) for k in range(1, 4)],
                             out_model("m249_bipod_0", bipod(0.0)))

gun = has_component("mymod:loaded_magazine", composite(body_layer, box_layer, belt_layer, bipod_layer),
                    composite(body_layer, bipod_layer))
flashes = flash_models(MODELS, "m249", MUZZLE, BY, DISPLAYS)
write(os.path.join(ITEMS, "m249.json"), {"model": with_flash(gun, flashes, ("mymod:suppressor", "mymod:oil_can_suppressor"))})
FLASH.save(TEX)

# Weapon Station parts.
for name, elements, scale in (("m249_receiver", receiver(0) + cover(0) + stock(), 0.42),
                              ("m249_barrel", barrel() + bipod(0.0), 0.4)):
    check(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, part_displays(elements, scale)))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})

# The ammo box item: a flat sprite like the other magazines, with brass showing at the mouth once it's packed.
PAL = {"o": K.rgb(BOX), "d": K.rgb(BOX_DARK), "l": K.rgb(BOX_LIGHT), "s": K.rgb(0xE0DCC8), "k": K.rgb(0x101410),
       "b": K.rgb(0xCFA448), "B": K.rgb(0xF2D27C), "c": K.rgb(0xC0703A), "x": K.rgb(0x262626), "m": K.rgb(0x101410)}
TOP_EMPTY = ["................", "................", "................", "....xx....xx....", "...dllllllllld..",
             "...dmmllllllld.."]
TOP_FULL = ["....cBc.........", "...cBbBc........", "....BbB.........", "....BbBx...xx...", "...dBbllllllld..",
            "...dBbmlllllld.."]
BODY = ["...doooooooood..", "...dossssssood..", "...doooooooood..", "...dossssoooood.", "...doooooooood..",
        "...doooooooood..", "...doooooooood..", "...dddooooodd...", "....ddddddddd...", "................"]
BODY = [(row + "................")[:16] for row in BODY]
for name, top in (("m249_ammo_box", TOP_EMPTY), ("m249_ammo_box_loaded", TOP_FULL)):
    sprite(os.path.join(TEX, f"item/{name}.png"), top + BODY, PAL)
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
write(os.path.join(ITEMS, "m249_ammo_box.json"), {"model": has_component("mymod:magazine_contents", ref("m249_ammo_box_loaded"),
                                                                         ref("m249_ammo_box"))})

t = DISPLAYS["firstperson_righthand"]
print(f"m249: {len(body())} elements; firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}")
print(f"   grip {fp_point(DISPLAYS, GRIP)}  support {fp_point(DISPLAYS, SUPPORT)}")
print(f"   sight window (8, {SIGHT_Y}, {REAR_SIGHT_Z}); optic mount front {RAIL[0]} top {RAIL[2]}")
