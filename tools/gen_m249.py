"""Builds the M249 SAW, its Weapon Station parts and the M249 ammo box.

The gun's item definition stacks layers, each picked by item model properties (see M249Animation):
  - the body, by mymod:gun_animation: 0 at rest, 1-6 the charging handle drawn back, 7-12 the feed cover swinging
    open (12 fully open), 13-16 cover open while the box is changed;
  - the ammo box: seated, lowered (14, 15) or gone (16), only with one loaded;
  - the belt: from the box up the left side into the feed tray, with as many rounds as are left once fewer than
    nine remain (mymod:loaded_rounds), so it shortens as the box runs dry; lifted out and hanging from the box
    while the box is changed (13-15);
  - the bipod, by mymod:bipod: folded along the handguard (0) through to deployed (3) as the holder crouches;
  - the muzzle flash, by mymod:muzzle_flash.

Model space is in pixels: the bore runs along z at x = 8, y = 9 with the muzzle toward -z, +x is the gun's right.
GunDisplay, GunHands and GunAim mirror the display transforms, grip points and sight line printed at the end, so move
them together. Run tools/gen_attachments.py afterwards: it wraps the item definition with the attachment layers (and
swings the optic up with the feed cover it's railed to)."""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, FLASH, check, copy, move, rotate, rx, rz, write, model, ref, EMPTY, range_dispatch, composite,
                    has_component, display, display_matrix, placed, centred, bounds_centre, DIAGONAL, sprite, flash_models,
                    with_flash)

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))

K = Kit("m249", "item/m249", seed=249)
PARK, PARK_DARK, PARK_WORN = 0x34372F, 0x202219, 0x575B50
POLY, POLY_DARK = 0x1E1F20, 0x121213
OD, OD_DARK, OD_LIGHT = 0x535A35, 0x3B4125, 0x6C7446

K.swatch("steel", 0, 0, 8, 2, lambda x, y, w, h: K.rgb(PARK_WORN if K.rnd.random() < 0.05 else PARK, jitter=3))
K.swatch("steel_dark", 8, 0, 12, 2, lambda x, y, w, h: K.rgb(PARK_DARK, jitter=2))
K.swatch("steel_worn", 12, 0, 16, 2, lambda x, y, w, h: K.rgb(PARK_WORN if K.rnd.random() < 0.6 else PARK, jitter=5))
K.swatch("poly", 0, 2, 4, 4, lambda x, y, w, h: K.rgb(POLY, jitter=2))
K.swatch("ribs_u", 4, 2, 12, 4, lambda x, y, w, h: K.rgb(POLY_DARK if x % 4 == 0 else 0x2A2B2D if x % 4 == 1 else POLY))
K.swatch("ribs_v", 12, 2, 14, 10, lambda x, y, w, h: K.rgb(POLY_DARK if y % 4 == 0 else 0x2A2B2D if y % 4 == 1 else POLY))
K.swatch("checker", 14, 2, 16, 4, lambda x, y, w, h: K.rgb(POLY_DARK if (x + y) % 2 == 0 else 0x2C2D2F))
# The soft ammo pouch: olive cloth with a weave and a stitched seam.
K.swatch("pouch", 0, 4, 8, 6, lambda x, y, w, h: K.rgb(OD_DARK if (x * 3 + y * 5) % 7 == 0 else OD, jitter=4))
K.swatch("pouch_seam", 8, 4, 12, 6, lambda x, y, w, h: K.rgb(0xC9C3A0 if y == 1 and x % 2 == 0 else OD_DARK if y == 0 else OD, jitter=3))
K.swatch("brass", 14, 4, 16, 6, lambda x, y, w, h: K.rgb(0xF2D27C if y == 0 else 0xCFA448, jitter=5))
K.swatch("copper", 0, 6, 2, 8, lambda x, y, w, h: K.rgb(0xC0703A if y > 0 else 0xE08E52, jitter=4))
K.swatch("link", 2, 6, 4, 8, lambda x, y, w, h: K.rgb(0x151515 if y % 2 else 0x262626))
K.swatch("rubber", 4, 6, 6, 8, lambda x, y, w, h: K.rgb(0x101010 if y % 3 == 0 else 0x1B1B1B))
K.swatch("bore", 6, 6, 8, 8, lambda x, y, w, h: K.rgb(0x050505 if math.hypot(x - 3.5, y - 3.5) < 3.0 else 0x2A2C26), picture=True)
K.swatch("slots", 8, 6, 12, 8, lambda x, y, w, h: K.rgb(0x050505 if 3 <= y <= 12 and x % 4 == 1 else PARK, jitter=2))
K.swatch("rail", 0, 8, 8, 10, lambda x, y, w, h: K.rgb(PARK_DARK if x % 3 == 0 else PARK, jitter=2))
K.swatch("chamber", 8, 8, 10, 10, lambda x, y, w, h: K.rgb(0x08090A, jitter=1))
K.swatch("aperture", 10, 8, 12, 10, lambda x, y, w, h: K.rgb(0x030303 if math.hypot(x - 3.5, y - 3.5) < 1.6 else PARK_DARK), picture=True)
K.swatch("perforated", 0, 10, 8, 12, lambda x, y, w, h: K.rgb(0x050505 if x % 4 in (1, 2) and y % 4 in (1, 2) else PARK, jitter=2))
K.swatch("pouch_mouth", 8, 10, 12, 12, lambda x, y, w, h: K.rgb(0x16180F if 1 <= x <= w - 2 and 1 <= y <= h - 2 else OD_DARK), picture=True)


def stencil(x, y, w, h):
    """Black stencilled 'CTG 5.56MM' lines on the pouch's side."""
    if 3 <= y <= 5 and 4 <= x <= 27 and x % 4 != 3 and (y != 4 or x % 4 == 0):
        return K.rgb(0x1E2012)
    if y == 9 and 8 <= x <= 22 and x % 3 != 2:
        return K.rgb(0x1E2012)
    return K.rgb(OD, jitter=4)


K.swatch("stencil", 0, 12, 8, 16, stencil, picture=True)
K.save(TEX)

BX, BY = 8.0, 9.0
MUZZLE = -15.9
RECEIVER = (4.0, 17.2)
HANDLE_TRAVEL = 4.2
HINGE = (8.0, 10.9, 13.3)          # the feed cover's hinge
COVER_OPEN = 75.0
GRIP = (8.0, 4.4, 16.3)            # the shooting hand on the pistol grip
SUPPORT = (8.0, 6.4, -0.8)         # the off hand under the handguard
SIGHT = (8.0, 12.25, 11.95)        # the rear aperture, lined up with the front post
BIPOD_PIVOT_Z = -5.6
BELT_PITCH = 0.46
BELT_MAX = 9


# ------------------------------------------------------------------------------------------------- barrel group


def barrel():
    out = []
    K.rod(out, MUZZLE, -14.2, 0.55, "steel_dark", cx=BX, cy=BY, mark="slots", name="flash_hider")
    K.sticker(out, BX - 0.4, BY - 0.4, MUZZLE - 0.03, BX + 0.4, BY + 0.4, MUZZLE - 0.02, "bore", "north")
    K.rod(out, -14.2, RECEIVER[0] + 0.4, 0.45, "steel", cx=BX, cy=BY, name="barrel", caps=(False, False))
    K.box(out, 7.55, 9.3, -13.4, 8.45, 10.0, -12.6, "steel_dark", name="front_sight_base")
    K.box(out, 7.92, 10.0, -13.15, 8.08, SIGHT[1], -12.85, "steel_dark", name="front_sight_post")
    # Gas block and regulator, with the bipod's mount under it.
    K.chamfered(out, 7.4, 7.2, -6.6, 8.6, 9.5, -4.6, 0.25, "steel", name="gas_block")
    K.box(out, 7.6, 7.4, -7.0, 8.4, 8.2, -6.6, "steel_worn", name="gas_regulator")
    K.box(out, 7.25, 6.55, -6.2, 8.75, 7.2, -5.0, "steel_dark", name="bipod_mount")
    K.box(out, 6.5, 6.5, -5.85, 9.5, 6.85, -5.35, "steel_dark", name="bipod_yoke")
    # Handguard and the heat shield over the barrel.
    K.chamfered(out, 6.95, 6.4, -4.6, 9.05, 8.6, 4.0, 0.5, "ribs_v", cap="poly", east="ribs_u", west="ribs_u", name="handguard")
    K.box(out, 7.45, 9.55, -4.2, 8.55, 9.75, 0.4, "perforated", north="steel_dark", south="steel_dark", name="heat_shield")
    # Carry handle, swung up and over to the left so it clears the sights.
    K.rod(out, 0.6, 2.6, 0.62, "steel_dark", cx=BX, cy=BY, name="carry_handle_clamp")
    arms = []
    for z0 in (0.9, 1.9):
        K.box(arms, 7.3, 9.3, z0, 7.7, 11.9, z0 + 0.4, "steel_dark", name="carry_handle_arm")
    out += rotate(arms, rz(45), (7.5, 9.3, 1.6))
    K.chamfered(out, 5.15, 10.55, -0.8, 6.3, 11.6, 4.4, 0.3, "poly", name="carry_handle")
    return out


def bipod(frac):
    """The bipod's legs, folded back along the handguard's lower edges (0) or swung down and splayed (1)."""
    out = []
    for side in (-1, 1):
        x0 = 6.55 if side < 0 else 9.1
        leg = []
        K.box(leg, x0, 6.45, BIPOD_PIVOT_Z - 0.2, x0 + 0.35, 6.8, 3.2, "steel", name="bipod_leg")
        K.box(leg, x0 - 0.05, 6.4, 1.6, x0 + 0.4, 6.85, 3.1, "steel_worn")
        K.box(leg, x0 - 0.08, 6.35, 3.0, x0 + 0.43, 6.9, 3.5, "rubber", name="bipod_foot")
        pivot = (x0 + 0.175, 6.625, BIPOD_PIVOT_Z)
        rotate(leg, rx(90 * frac), pivot)
        rotate(leg, rz(10 * frac * side), pivot)
        out += leg
    return out


# ----------------------------------------------------------------------------------------------- receiver group


def cover(angle):
    """The feed cover with its rail and rear sight, swung open by `angle` degrees about its hinge."""
    out = []
    K.chamfered(out, 6.95, 10.55, 4.2, 9.05, 11.4, HINGE[2], 0.3, "steel", name="feed_cover")
    K.box(out, 7.4, 11.4, 12.4, 8.6, 11.7, 13.2, "steel_worn", name="cover_latch")
    K.box(out, 7.3, 11.4, 5.0, 8.7, 11.85, 11.0, "rail", name="cover_rail")
    K.box(out, 7.45, 11.4, 11.2, 8.55, 11.85, 12.4, "steel_dark", name="rear_sight_base")
    K.box(out, 7.55, 11.85, 11.8, 8.45, 12.65, 12.1, "steel_dark", name="rear_sight")
    K.sticker(out, 7.95 - 0.2, SIGHT[1] - 0.2, 12.1, 8.05 + 0.2, SIGHT[1] + 0.2, 12.12, "aperture", "south", name="rear_sight_aperture")
    return rotate(out, rx(angle), HINGE) if angle else out


def receiver(handle):
    out = []
    z0, z1 = RECEIVER
    K.chamfered(out, 6.9, 6.0, z0, 9.1, 10.4, z1, 0.45, "steel", name="receiver")
    K.box(out, 6.95, 10.4, 4.3, 9.05, 10.55, 9.2, "steel_worn", name="feed_tray")
    # Where the belt goes in on the left, and the charging handle in its slot on the right.
    K.sticker(out, 6.87, 9.55, 4.6, 6.9, 10.35, 8.6, "chamber", "west", name="feed_opening")
    K.sticker(out, 9.1, 8.45, 7.2, 9.13, 8.95, 12.6, "chamber", "east", name="charging_slot")
    K.box(out, 9.13, 8.3, 7.4 + handle, 9.75, 9.1, 8.2 + handle, "steel_worn", name="charging_handle")
    K.box(out, 9.75, 8.45, 7.5 + handle, 10.05, 8.95, 8.1 + handle, "steel_dark")
    # Trigger guard, trigger, pistol grip.
    K.box(out, 7.65, 3.9, 11.2, 8.35, 4.25, 14.4, "poly", name="trigger_guard")
    K.box(out, 7.65, 4.25, 11.2, 8.35, 6.0, 11.55, "poly")
    K.box(out, 7.84, 4.5, 12.3, 8.16, 6.0, 12.65, "steel_worn", rot=("x", -10, (8.0, 6.0, 12.5)), name="trigger")
    K.box(out, 6.8, 5.4, 13.6, 9.2, 5.75, 13.95, "steel_dark", name="safety")
    grip = []
    K.chamfered(grip, 7.15, 1.6, 14.4, 8.85, 6.0, 16.8, 0.45, "checker", cap="poly", name="pistol_grip")
    K.box(grip, 7.1, 1.4, 14.3, 8.9, 1.7, 16.9, "poly")
    out += rotate(grip, rx(-18), (8.0, 6.0, 15.6))
    return out


def stock():
    out = []
    K.chamfered(out, 7.05, 6.9, 16.9, 8.95, 10.05, 30.3, 0.55, "poly", name="stock")
    belly = []
    K.chamfered(belly, 7.1, 2.8, 17.8, 8.9, 7.0, 30.0, 0.5, "poly", name="stock_belly")
    out += rotate(belly, rx(14.5), (8.0, 2.8, 30.0))
    K.chamfered(out, 6.95, 2.4, 29.9, 9.05, 10.3, 31.2, 0.4, "rubber", cap="poly", name="butt_plate")
    K.box(out, 7.7, 7.3, 22.0, 8.3, 7.9, 23.2, "steel_dark", rot=("x", 0.0, (8, 7.3, 22)), name="sling_mount")
    return out


# --------------------------------------------------------------------------------------------- box and belt


def ammo_box(drop=0.0):
    out = []
    K.chamfered(out, 5.7, 1.2, 4.7, 9.1, 5.95, 9.9, 0.5, "pouch", east="stencil", up="pouch", name="ammo_box")
    K.box(out, 5.6, 5.4, 4.65, 9.2, 5.75, 9.95, "pouch_seam", up=None, down=None, name="ammo_box_flap")
    K.sticker(out, 5.8, 5.95, 5.9, 6.75, 5.98, 8.1, "pouch_mouth", "up", name="ammo_box_mouth")
    K.box(out, 7.3, 5.95, 5.6, 8.7, 6.0, 9.0, "steel_dark", name="ammo_box_clip")
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


def cartridge(out, x, y, angle):
    """One linked 5.56 round lying along z (bullet forward), turned to the belt's run."""
    r = []
    K.box(r, x - 0.21, y - 0.21, 6.0, x + 0.21, y + 0.21, 7.95, "brass", name="round")
    K.box(r, x - 0.14, y - 0.14, 5.35, x + 0.14, y + 0.14, 6.0, "copper")
    K.box(r, x - 0.25, y - 0.25, 6.55, x + 0.25, y + 0.25, 6.85, "link", north=None, south=None)
    K.box(r, x - 0.25, y - 0.25, 7.3, x + 0.25, y + 0.25, 7.6, "link", north=None, south=None)
    out += rotate(r, rz(angle - 90), (x, y, 6.6))


FEED = (6.72, 10.12)
MOUTH = (6.15, 6.05)


def belt(n):
    """The belt with n rounds still out of the box: the run up into the feed (rounds nearest the feed first) and the
    round on the feed tray."""
    out = []
    for x, y, a in along(FEED, (5.25, 8.6), MOUTH, BELT_PITCH, n):
        cartridge(out, x, y, a)
    tray = []
    K.box(tray, 7.35, 10.55, 6.0, 7.77, 10.97, 7.95, "brass", name="tray_round")
    K.box(tray, 7.42, 10.62, 5.35, 7.7, 10.9, 6.0, "copper")
    return out + tray


def hanging_belt(drop):
    """Lifted off the tray: the belt's end hangs out of the box mouth where the hand holds it."""
    out = []
    for x, y, a in along((4.7, 9.5), (4.9, 6.9), MOUTH, BELT_PITCH, 8):
        cartridge(out, x, y, a)
    return move(out, dx=-0.4 * drop / 3.0, dy=-drop)


# --------------------------------------------------------------------------------------------------- output


def gun_displays(elements, fp_scale, gui_scale):
    fp_rot = [0, -8, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, GRIP, FP_GRIP_TARGET), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, GRIP, TP_GRIP_TARGET), 0.65)
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
DISPLAYS = gun_displays(everything, 0.52, 0.31)


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
for name, elements, scale in (("m249_receiver", receiver(0) + cover(0) + stock(), 0.45),
                              ("m249_barrel", barrel() + bipod(0.0), 0.42)):
    check(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, part_displays(elements, scale)))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})

# The ammo box item: a flat sprite like the other magazines, with brass showing at the mouth once it's packed.
PAL = {"o": K.rgb(OD), "d": K.rgb(OD_DARK), "l": K.rgb(OD_LIGHT), "s": K.rgb(0xC9C3A0), "k": K.rgb(0x1E2012),
       "b": K.rgb(0xCFA448), "B": K.rgb(0xF2D27C), "c": K.rgb(0xC0703A), "x": K.rgb(0x262626), "m": K.rgb(0x16180F)}
TOP_EMPTY = ["................", "................", "................", "...xx.......xx..", "..xddddddddddddx",
             "..dmmmlllllllldd"]
TOP_FULL = ["...cBc..........", "..cBbBc.........", "...BbB..........", "...BbBx......xx.", "..xBbddddddddddx",
            "..dBbmlllllllldd"]
BODY = ["..dlooooooooooodd", "..dossssssssssodd", "..doooooooooooodd", "..dokkkokkkoooodd", "..doooooooooooodd",
        "..dookkkkkooooodd", "..doooooooooooodd", "..dddoooooooodddd", "...dddddddddddd.", "................"]
BODY = [row[:16] for row in BODY]
for name, top in (("m249_ammo_box", TOP_EMPTY), ("m249_ammo_box_loaded", TOP_FULL)):
    sprite(os.path.join(TEX, f"item/{name}.png"), top + BODY, PAL)
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
write(os.path.join(ITEMS, "m249_ammo_box.json"), {"model": has_component("mymod:magazine_contents", ref("m249_ammo_box_loaded"),
                                                                         ref("m249_ammo_box"))})

t = DISPLAYS["firstperson_righthand"]
print(f"m249: {len(body())} elements; firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}")
print(f"   grip {fp_point(DISPLAYS, GRIP)}  support {fp_point(DISPLAYS, SUPPORT)}  sight window {SIGHT}")
