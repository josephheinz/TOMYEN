"""Builds the two pump shotguns (Remington 870 and SPAS-12), their Weapon Station parts, the 12 gauge shells and the
muzzle flash both share with the M249.

Each gun gets its model at rest plus six frames of the pump being worked (the fore-end and action bars sliding back,
the bolt opening in the ejection port), chosen by the tomyen:gun_animation item model property (1-6, see
ShotgunAnimation), and a muzzle flash layer chosen by tomyen:muzzle_flash. Shells get a flat inventory sprite plus 3D
models (live and fired) that the client draws in the hand while loading and flying out of the ejection port; those
are separate item model definitions, picked with the item_model component.

Model space is in pixels: the bore runs along z at x = 8 with the muzzle toward -z, +x is the gun's right. GunDisplay,
GunHands and GunAim mirror the display transforms, grip points and sight lines printed at the end, so move them
together. Run tools/gen_attachments.py afterwards: it wraps these item definitions with the attachment layers."""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, FLASH, check, move, rotate, rx, rz, write, model, ref, range_dispatch, display, display_matrix,
                    placed, centred, bounds_centre, DIAGONAL, sprite, flash_models, with_flash)

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

FRAMES = 6
MUZZLE = -15.9
# Where long guns put the shooting hand in first person: the M4's grip point, in the held item's space (pixels).
FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
# And in third person, relative to the hand (the M4's grip point under its third-person transform).
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))


# ========================================================================================================== displays


def gun_displays(grip, elements, fp_scale, gui_scale):
    """First person: rotated -8 degrees like the rifles, the grip where the M4's is. Third person: crossbow hold,
    the grip in the hand like the M4's. GUI: side on, like the rifles."""
    fp_rot = [0, -8, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, FP_GRIP_TARGET), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, grip, TP_GRIP_TARGET), 0.65)
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
    """Where model point p ends up in the held item's space (blocks), under the first-person display transform."""
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


def write_gun(kit, name, frames, grip, support, sight, fp_scale, gui_scale, flash_at, parts):
    """frames: element lists for rest and each pump frame. parts: (part name, elements, gui scale)."""
    every = [e for f in frames for e in f]
    displays = gun_displays(grip, every, fp_scale, gui_scale)
    for i, elements in enumerate(frames):
        check(f"{name}_f{i}", elements)
        write(os.path.join(MODELS, name + (f"_f{i}" if i else "") + ".json"), model(kit, elements, displays))
    flashes = flash_models(MODELS, name, flash_at[0], flash_at[1], displays)
    body = range_dispatch("tomyen:gun_animation",
                          [(k - 0.5, ref(f"{name}_f{k}")) for k in range(1, len(frames))] + [(len(frames) - 0.5, ref(name))],
                          ref(name))
    write(os.path.join(ITEMS, name + ".json"), {"model": with_flash(body, flashes)})
    for part, elements, scale in parts:
        check(part, elements)
        write(os.path.join(MODELS, part + ".json"), model(kit, elements, part_displays(elements, scale)))
        write(os.path.join(ITEMS, part + ".json"), {"model": ref(part)})
    t = displays["firstperson_righthand"]
    print(f"{name}: {len(frames[0])} elements; firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}")
    print(f"   grip {fp_point(displays, grip)}  support {fp_point(displays, support)}  sight window {sight}")


# ===================================================================================================== Remington 870

R = Kit("remington_870", "item/remington_870", seed=870)
BLUED, BLUED_DARK, BLUED_WORN = 0x262A31, 0x15171B, 0x4B525C
WALNUT, WALNUT_DARK, WALNUT_HI = 0x6B3A1E, 0x45230F, 0x8C5230


def grain(along_u, base=WALNUT, dark=WALNUT_DARK, hi=WALNUT_HI, kit=R):
    def f(x, y, w, h):
        a, b = (y, x) if along_u else (x, y)
        streak = (a * 5 + int(2.5 * math.sin(b * 0.27 + a * 1.9))) % 7
        return kit.rgb(dark if streak == 0 else hi if streak == 4 else base, jitter=4)
    return f


R.swatch("steel", 0, 0, 8, 2, lambda x, y, w, h: R.rgb(BLUED_WORN if R.rnd.random() < 0.04 else BLUED, jitter=3))
R.swatch("steel_dark", 8, 0, 12, 2, lambda x, y, w, h: R.rgb(BLUED_DARK, jitter=2))
R.swatch("steel_worn", 12, 0, 16, 2, lambda x, y, w, h: R.rgb(BLUED_WORN if R.rnd.random() < 0.6 else BLUED, jitter=5))
R.swatch("wood_u", 0, 2, 8, 4, grain(True))
R.swatch("wood_v", 8, 2, 10, 8, grain(False))
R.swatch("wood_end", 10, 2, 12, 4, lambda x, y, w, h: R.rgb(WALNUT_DARK if int(math.hypot(x - 3.5, y - 3.5)) % 2 else WALNUT, jitter=3))
# The fore-end's grooves ring it every couple of pixels: lines across u on the sides, across v underneath.
R.swatch("forend_u", 0, 4, 8, 6, lambda x, y, w, h: R.rgb(0x2A1608 if x % 5 == 0 else WALNUT_HI if x % 5 == 1 else WALNUT, jitter=3))
R.swatch("forend_v", 12, 2, 14, 8, lambda x, y, w, h: R.rgb(0x2A1608 if y % 5 == 0 else WALNUT_HI if y % 5 == 1 else WALNUT, jitter=3))
R.swatch("rubber", 0, 6, 4, 8, lambda x, y, w, h: R.rgb(0x121212 if y % 3 == 0 else 0x1E1E1E))
R.swatch("brass", 4, 6, 6, 8, lambda x, y, w, h: R.rgb(0xF0D27A if x == 0 or y == 0 else 0xC9A044, jitter=4))
R.swatch("bore", 6, 6, 8, 8, lambda x, y, w, h: R.rgb(0x050505 if math.hypot(x - 3.5, y - 3.5) < 3.0 else 0x2A2E35), picture=True)
R.swatch("black", 14, 2, 16, 4, lambda x, y, w, h: R.rgb(0x18191B, jitter=2))
R.swatch("chamber", 14, 4, 16, 6, lambda x, y, w, h: R.rgb(0x08090A, jitter=1))
R.swatch("bolt", 14, 6, 16, 8, lambda x, y, w, h: R.rgb(0xB4BAC2 if y == 0 else 0x8E949C, jitter=4))


def rollmark(x, y, w, h):
    """Engraved lettering on the receiver's left side: two short lines of light 'text' on blued steel."""
    if 6 <= y <= 7 and 6 <= x <= 33 and x % 4 != 3:
        return R.rgb(0x7E8590)
    if y == 10 and 10 <= x <= 22 and x % 3 != 2:
        return R.rgb(0x6A717B)
    return R.rgb(BLUED, jitter=3)


R.swatch("rollmark", 0, 8, 10, 12, rollmark, picture=True)
R.swatch("port_edge", 10, 8, 12, 10, lambda x, y, w, h: R.rgb(0x3A3F48, jitter=2))
R.swatch("loading_port", 0, 12, 4, 16, lambda x, y, w, h: R.rgb(
    0x5E646C if 3 <= x <= 12 and 3 <= y <= 13 and (x in (3, 12) or y in (3, 13)) else 0x6C727A if 4 <= x <= 11 and 4 <= y <= 12 else 0x08090A), picture=True)
R.swatch("pad_spacer", 4, 12, 6, 14, lambda x, y, w, h: R.rgb(0x0C0C0C, jitter=1))
R.save(TEX)

BX, BY = 8.0, 9.0          # bore axis
R_RECEIVER = (5.6, 15.0)   # receiver front and back
PUMP_TRAVEL = 3.6
R_GRIP = (8.0, 7.3, 16.4)      # the shooting hand round the wrist of the stock
R_SUPPORT = (8.0, 6.35, -2.6)  # the off hand under the fore-end
R_SIGHT = (8.0, 10.15, 14.2)   # the eye's line over the receiver to the bead


def r870_barrel():
    out = []
    R.rod(out, MUZZLE, R_RECEIVER[0] + 0.4, 0.55, "steel", cx=BX, cy=BY, name="barrel", caps=(True, False))
    R.sticker(out, BX - 0.42, BY - 0.42, MUZZLE - 0.03, BX + 0.42, BY + 0.42, MUZZLE - 0.02, "bore", "north")
    # Brass bead on a little ramp near the muzzle.
    R.box(out, 7.85, 9.5, -15.5, 8.15, 9.7, -14.9, "steel", name="bead_base")
    R.box(out, 7.84, 9.7, -15.4, 8.16, 10.02, -15.08, "brass", name="front_sight")
    R.box(out, 7.88, 9.66, -15.36, 8.12, 10.06, -15.12, "brass")
    # Magazine tube under the barrel with its cap, a clamp tying it to the barrel, and the barrel's ring at the receiver.
    R.rod(out, -13.4, R_RECEIVER[0] + 0.4, 0.55, "steel", cx=BX, cy=7.7, name="magazine_tube", caps=(False, False))
    R.rod(out, -14.2, -13.4, 0.62, "steel_worn", cx=BX, cy=7.7, cap="steel_dark", name="magazine_cap")
    R.box(out, 7.75, 7.45, -14.25, 8.25, 7.95, -14.21, "steel_dark")
    R.chamfered(out, 7.25, 6.95, -13.0, 8.75, 9.75, -12.3, 0.3, "steel_dark", name="barrel_clamp")
    R.box(out, 7.7, 6.55, -12.95, 8.3, 6.95, -12.35, "steel_dark")
    R.chamfered(out, 7.3, 7.0, 4.3, 8.7, 9.65, R_RECEIVER[0], 0.35, "steel", name="barrel_ring")
    return out


def r870_pump(travel):
    """The fore-end and its action bars, slid back by `travel`."""
    out = []
    R.chamfered(out, 6.85, 6.35, -7.6, 9.15, 8.55, 2.4, 0.55, "forend_v", cap="wood_end",
                east="forend_u", west="forend_u", name="forend")
    # The rear lip where the fore-end narrows round the tube.
    R.chamfered(out, 7.05, 6.55, 2.4, 8.95, 8.35, 2.9, 0.45, "wood_v", cap="wood_end", east="wood_u", west="wood_u")
    for x0 in (7.12, 8.68):
        R.box(out, x0, 7.85, 2.9, x0 + 0.2, 8.15, 6.2, "steel_worn", name="action_bar")
    return move(out, dz=travel)


def r870_receiver(travel):
    out = []
    z0, z1 = R_RECEIVER
    R.chamfered(out, 7.0, 6.7, z0, 9.0, 10.1, z1, 0.42, "steel", west="rollmark", name="receiver")
    # Ejection port on the right, with the bolt in it: it rides back with the pump and lays the chamber open.
    R.sticker(out, 9.0, 8.65, 8.1, 9.03, 9.95, 11.5, "port_edge", "east")
    R.sticker(out, 9.0, 8.75, 8.2, 9.04, 9.85, 11.4, "chamber", "east")
    bolt_front = 8.4 + travel
    if bolt_front < 11.3:
        R.sticker(out, 9.0, 8.8, bolt_front, 9.05, 9.8, 11.4, "bolt", "east", name="bolt")
    # Loading port underneath, where shells go into the tube.
    R.sticker(out, 7.3, 6.67, 6.0, 8.7, 6.7, 10.6, "loading_port", "down", name="loading_port")
    # Trigger plate, guard, trigger, cross-bolt safety and the slide release.
    R.box(out, 7.3, 6.25, 9.9, 8.7, 6.7, 15.0, "black", name="trigger_plate")
    R.box(out, 7.65, 4.75, 10.6, 8.35, 5.1, 13.6, "black", name="trigger_guard")
    R.box(out, 7.65, 5.1, 10.25, 8.35, 6.25, 10.6, "black")
    R.box(out, 7.65, 5.1, 13.6, 8.35, 6.25, 14.0, "black")
    R.box(out, 7.84, 5.3, 11.5, 8.16, 6.25, 11.85, "steel_worn", rot=("x", -12, (8.0, 6.25, 11.7)), name="trigger")
    R.box(out, 6.9, 5.85, 13.55, 9.1, 6.2, 13.9, "steel_dark", name="safety")
    R.box(out, 6.86, 5.6, 9.5, 7.3, 6.15, 10.2, "steel_dark", name="slide_release")
    return out


def r870_stock():
    """Walnut stock, dropped at the comb and the heel: the wrist, the comb, the belly, and the butt pad."""
    out = []
    wrist = []
    R.chamfered(wrist, 7.1, 5.5, 14.7, 8.9, 10.0, 18.7, 0.6, "wood_v", cap="wood_end", east="wood_u", west="wood_u", name="stock_wrist")
    out += rotate(wrist, rx(14), (8.0, 10.0, 14.7))
    comb = []
    R.chamfered(comb, 7.0, 5.0, 17.3, 9.0, 9.3, 30.25, 0.6, "wood_v", cap="wood_end", east="wood_u", west="wood_u", name="stock_comb")
    out += rotate(comb, rx(5.0), (8.0, 9.3, 17.3))
    belly = []
    R.chamfered(belly, 7.05, 1.65, 17.4, 8.95, 5.65, 29.6, 0.6, "wood_v", cap="wood_end", east="wood_u", west="wood_u", name="stock_belly")
    out += rotate(belly, rx(14.7), (8.0, 1.65, 29.6))
    R.chamfered(out, 6.95, 1.35, 29.55, 9.05, 8.35, 30.95, 0.35, "rubber", cap="black", name="butt_pad")
    R.box(out, 7.75, 3.35, 26.0, 8.25, 3.75, 26.8, "steel_worn", rot=("x", 14.7, (8.0, 3.35, 26.4)), name="sling_swivel")
    return out


def r870(travel):
    return r870_barrel() + r870_pump(travel) + r870_receiver(travel) + r870_stock()


R_FRAMES = [r870(PUMP_TRAVEL * k / FRAMES) for k in range(FRAMES + 1)]
write_gun(R, "remington_870", R_FRAMES, R_GRIP, R_SUPPORT, R_SIGHT, 0.55, 0.33, (MUZZLE, BY),
          [("remington_870_receiver", r870_receiver(0) + r870_stock(), 0.52),
           ("remington_870_barrel", r870_barrel() + r870_pump(0), 0.5)])

# ============================================================================================================ SPAS-12

P = Kit("spas12", "item/spas12", seed=12)
PARK, PARK_DARK, PARK_WORN = 0x2C2E2B, 0x1B1C1A, 0x4A4D48
POLY, POLY_DARK = 0x1F2021, 0x131314

P.swatch("steel", 0, 0, 8, 2, lambda x, y, w, h: P.rgb(PARK_WORN if P.rnd.random() < 0.05 else PARK, jitter=3))
P.swatch("steel_dark", 8, 0, 12, 2, lambda x, y, w, h: P.rgb(PARK_DARK, jitter=2))
P.swatch("steel_worn", 12, 0, 16, 2, lambda x, y, w, h: P.rgb(PARK_WORN if P.rnd.random() < 0.6 else PARK, jitter=5))
P.swatch("alloy", 0, 2, 8, 4, lambda x, y, w, h: P.rgb(0x252628 if P.rnd.random() > 0.04 else 0x34363A, jitter=2))
P.swatch("poly", 8, 2, 12, 4, lambda x, y, w, h: P.rgb(POLY, jitter=2))
# Long ribs down the fore-end's sides (stripes across v), and its underside ribbed the same way along u.
P.swatch("ribs_v", 12, 2, 16, 4, lambda x, y, w, h: P.rgb(POLY_DARK if y % 3 == 0 else 0x2A2B2D if y % 3 == 1 else POLY))
P.swatch("ribs_u", 0, 4, 2, 12, lambda x, y, w, h: P.rgb(POLY_DARK if x % 3 == 0 else 0x2A2B2D if x % 3 == 1 else POLY))
P.swatch("checker", 2, 4, 6, 6, lambda x, y, w, h: P.rgb(POLY_DARK if (x + y) % 2 == 0 else 0x2C2D2F))
# The heat shield's perforations: a staggered grid of round holes, about one per pixel of shield.
P.swatch("perforated", 6, 4, 16, 6, lambda x, y, w, h: P.rgb(
    0x050505 if x % 4 in (1, 2) and y % 4 in (1, 2) and (x // 4 + y // 4) % 2 == 0 else PARK, jitter=2))
P.swatch("perforated_v", 2, 6, 4, 16, lambda x, y, w, h: P.rgb(
    0x050505 if (x % 4 in (1, 2) and y % 4 in (1, 2)) and ((x // 4 + y // 4) % 2 == 0) else PARK, jitter=2))
P.swatch("rubber", 4, 6, 8, 8, lambda x, y, w, h: P.rgb(0x101010 if y % 3 == 0 else 0x1B1B1B))
P.swatch("bore", 8, 6, 10, 8, lambda x, y, w, h: P.rgb(0x050505 if math.hypot(x - 3.5, y - 3.5) < 3.0 else 0x2A2C2A), picture=True)
P.swatch("chamber", 10, 6, 12, 8, lambda x, y, w, h: P.rgb(0x08090A, jitter=1))
P.swatch("bolt", 12, 6, 14, 8, lambda x, y, w, h: P.rgb(0xA6ABB0 if y == 0 else 0x83888E, jitter=4))
P.swatch("port_edge", 14, 6, 16, 8, lambda x, y, w, h: P.rgb(0x3A3C3E, jitter=2))
# Stamped steel stock sides with long lightening slots.
P.swatch("stamped", 4, 8, 12, 10, lambda x, y, w, h: P.rgb(0x0A0A0A if 2 <= y <= 5 and x % 12 not in (0, 1, 10, 11) else PARK, jitter=3))
P.swatch("red", 12, 8, 14, 10, lambda x, y, w, h: P.rgb(0xB0241C, jitter=6))
P.swatch("aperture", 14, 8, 16, 10, lambda x, y, w, h: P.rgb(0x030303 if math.hypot(x - 3.5, y - 3.5) < 1.6 else PARK_DARK), picture=True)


def spas_mark(x, y, w, h):
    """'SPAS-12' and a maker's line stamped on the receiver's left side."""
    if 5 <= y <= 9 and 8 <= x <= 30 and (x % 5 in (0, 1, 3) and y in (5, 7, 9) or x % 5 == 0):
        return P.rgb(0x8A8E92)
    if y == 12 and 10 <= x <= 26 and x % 3 != 2:
        return P.rgb(0x5E6266)
    return P.rgb(0x252628, jitter=2)


P.swatch("rollmark", 4, 10, 14, 14, spas_mark, picture=True)
P.save(TEX)

SBY = 9.2
S_RECEIVER = (4.5, 15.6)
SPAS_TRAVEL = 3.0
S_GRIP = (8.0, 4.9, 15.2)
S_SUPPORT = (8.0, 5.95, -3.0)
S_SIGHT = (8.0, 11.4, 14.1)


def spas_barrel():
    out = []
    P.rod(out, MUZZLE, S_RECEIVER[0] + 0.4, 0.62, "steel", cx=BX, cy=SBY, name="barrel", caps=(True, False))
    P.sticker(out, BX - 0.48, SBY - 0.48, MUZZLE - 0.03, BX + 0.48, SBY + 0.48, MUZZLE - 0.02, "bore", "north")
    # Hooded front sight post.
    P.box(out, 7.5, 9.75, -14.7, 8.5, 10.3, -13.75, "steel_dark", name="front_sight_base")
    P.box(out, 7.9, 10.3, -14.3, 8.1, 11.4, -13.9, "steel_dark", name="front_sight")
    for x0 in (7.3, 8.45):
        P.box(out, x0, 9.9, -14.5, x0 + 0.25, 11.6, -13.7, "steel_dark", name="front_sight_ear")
    # Magazine tube, its cap with the sling loop, and the clamp tying it to the barrel.
    P.rod(out, -14.0, S_RECEIVER[0] + 0.4, 0.62, "steel", cx=BX, cy=7.6, name="magazine_tube", caps=(False, False))
    P.rod(out, -15.2, -14.0, 0.7, "steel_worn", cx=BX, cy=7.6, cap="steel_dark", name="magazine_cap")
    P.box(out, 7.75, 6.3, -15.0, 8.25, 6.75, -14.2, "steel_dark", name="sling_loop")
    P.chamfered(out, 7.2, 6.75, -13.6, 8.8, 10.0, -12.9, 0.3, "steel_dark", name="barrel_clamp")
    # The perforated heat shield over the barrel: the top five-eighths of an octagonal sleeve.
    z0, z1, r, t = -7.0, 4.3, 1.08, 0.12
    half = r * math.tan(math.pi / 8)
    shield = []
    P.box(shield, BX - half, SBY + r, z0, BX + half, SBY + r + t, z1, "perforated_v", north="steel_dark", south="steel_dark",
          name="heat_shield")
    for side in (-1, 1):
        diag = []
        P.box(diag, BX - half, SBY + r, z0 + 0.01, BX + half, SBY + r + t, z1 - 0.01, "perforated_v", north="steel_dark",
              south="steel_dark")
        shield += rotate(diag, rz(45 * side), (BX, SBY, z0))
        x0 = BX + side * r
        P.box(shield, x0, SBY - half - 0.35, z0 + 0.02, x0 + side * t, SBY + half, z1 - 0.02, "perforated", north="steel_dark",
              south="steel_dark")
    out += shield
    return out


def spas_pump(travel):
    out = []
    P.chamfered(out, 6.55, 5.95, -7.8, 9.45, 8.95, 1.6, 0.7, "ribs_u", cap="poly", east="ribs_v", west="ribs_v", name="forend")
    # Tapered nose and the pump/semi selector button under it.
    P.chamfered(out, 6.85, 6.25, -8.4, 9.15, 8.65, -7.8, 0.6, "poly", name="forend_nose")
    P.box(out, 7.55, 5.7, -7.3, 8.45, 5.95, -5.9, "red", name="mode_button")
    P.box(out, 7.4, 5.75, -7.45, 8.6, 5.95, -7.3, "poly")
    for x0 in (7.0, 8.8):
        P.box(out, x0, 7.75, 1.6, x0 + 0.2, 8.1, 5.2, "steel_worn", name="action_bar")
    return move(out, dz=travel)


def spas_receiver(travel):
    out = []
    z0, z1 = S_RECEIVER
    P.chamfered(out, 6.95, 6.5, z0, 9.05, 10.6, z1, 0.5, "alloy", west="rollmark", name="receiver")
    P.sticker(out, 9.05, 8.5, 6.6, 9.08, 9.95, 10.4, "port_edge", "east")
    P.sticker(out, 9.05, 8.6, 6.7, 9.09, 9.85, 10.3, "chamber", "east")
    bolt_front = 6.9 + travel
    if bolt_front < 10.2:
        P.sticker(out, 9.05, 8.65, bolt_front, 9.1, 9.8, 10.3, "bolt", "east", name="bolt")
    P.sticker(out, 7.3, 6.47, 5.0, 8.7, 6.5, 9.6, "chamber", "down", name="loading_port")
    # Rear sight: an aperture between two protective ears.
    P.box(out, 7.35, 10.6, 13.0, 8.65, 11.0, 15.1, "steel_dark", name="rear_sight_base")
    P.box(out, 7.7, 11.0, 13.9, 8.3, 11.75, 14.2, "steel_dark", name="rear_sight")
    P.sticker(out, 7.8, 11.15, 14.2, 8.2, 11.6, 14.22, "aperture", "south", name="rear_sight_aperture")
    for x0 in (7.25, 8.45):
        P.box(out, x0, 11.0, 13.2, x0 + 0.3, 11.95, 14.9, "steel_dark", name="rear_sight_ear")
    # Trigger guard, trigger and the cross-bolt safety.
    P.box(out, 7.65, 4.4, 10.7, 8.35, 4.75, 13.7, "poly", name="trigger_guard")
    P.box(out, 7.65, 4.75, 10.7, 8.35, 6.5, 11.05, "poly")
    P.box(out, 7.84, 4.95, 11.9, 8.16, 6.5, 12.25, "steel_worn", rot=("x", -10, (8.0, 6.5, 12.0)), name="trigger")
    P.box(out, 6.85, 5.9, 10.15, 9.15, 6.25, 10.5, "steel_dark", name="safety")
    # Pistol grip, raked back.
    grip = []
    P.chamfered(grip, 7.15, 2.3, 13.6, 8.85, 6.6, 15.9, 0.45, "checker", cap="poly", name="pistol_grip")
    P.box(grip, 7.1, 2.1, 13.5, 8.9, 2.4, 16.0, "poly")
    out += rotate([e for e in grip], rx(-16), (8.0, 6.6, 14.7))
    return out


def spas_stock():
    """The top-folding stock, unfolded: a stamped steel frame off a hinge on the receiver, the butt plate, and the
    hook that hangs under it for the forearm."""
    out = []
    P.box(out, 7.15, 8.9, S_RECEIVER[1] - 0.1, 8.85, 10.15, 16.6, "steel_dark", name="stock_hinge")
    P.rod(out, 15.7, 16.5, 0.38, "steel_worn", cx=9.0, cy=9.5)
    for x0 in (7.1, 8.7):
        P.box(out, x0, 8.2, 16.4, x0 + 0.2, 9.75, 28.8, "stamped", north="steel", south="steel", up="steel", down="steel",
              name="stock_frame")
    P.box(out, 7.1, 9.75, 16.4, 8.9, 9.95, 28.8, "steel", name="stock_top")
    P.chamfered(out, 6.9, 4.0, 28.6, 9.1, 10.0, 29.4, 0.3, "rubber", cap="poly", name="butt_plate")
    hook = []
    P.box(hook, 7.55, 3.55, 25.4, 8.45, 4.1, 28.7, "steel", name="stock_hook")
    out += rotate(hook, rx(-12), (8.0, 4.0, 28.7))
    tip = []
    P.box(tip, 7.55, 3.9, 25.0, 8.45, 5.0, 25.5, "steel")
    out += rotate(tip, rx(-12), (8.0, 4.0, 28.7))
    return out


def spas(travel):
    return spas_barrel() + spas_pump(travel) + spas_receiver(travel) + spas_stock()


S_FRAMES = [spas(SPAS_TRAVEL * k / FRAMES) for k in range(FRAMES + 1)]
write_gun(P, "spas12", S_FRAMES, S_GRIP, S_SUPPORT, S_SIGHT, 0.53, 0.32, (MUZZLE, SBY),
          [("spas12_receiver", spas_receiver(0) + spas_stock(), 0.5),
           ("spas12_barrel", spas_barrel() + spas_pump(0), 0.5)])
FLASH.save(TEX)

# ============================================================================================================ shells

H = Kit("shell_12g", "item/shell_12g", seed=1212, size=32)
H.swatch("brass", 0, 0, 4, 4, lambda x, y, w, h: H.rgb(0xF2D27C if y == 0 else 0xCFA448, jitter=5))
H.swatch("brass_end", 4, 0, 8, 4, lambda x, y, w, h: H.rgb(0x9A7A30 if math.hypot(x - 3.5, y - 3.5) < 1.4 else 0xD8B055), picture=True)
H.swatch("red", 8, 0, 12, 4, lambda x, y, w, h: H.rgb(0xB82A20 if y > 0 else 0xD8483A, jitter=4))
H.swatch("green", 12, 0, 16, 4, lambda x, y, w, h: H.rgb(0x2F6E3A if y > 0 else 0x45904F, jitter=4))
H.swatch("crimp_red", 0, 4, 4, 8, lambda x, y, w, h: H.rgb(
    0x6A140F if (int(math.degrees(math.atan2(y - 3.5, x - 3.5)) // 60) % 2) else 0xA82A20), picture=True)
H.swatch("slug_end", 4, 4, 8, 8, lambda x, y, w, h: H.rgb(0x8C9094 if math.hypot(x - 3.5, y - 3.5) < 2.6 else 0x2F6E3A), picture=True)
H.swatch("hull_open", 8, 4, 12, 8, lambda x, y, w, h: H.rgb(0x1A0806 if math.hypot(x - 3.5, y - 3.5) < 2.6 else 0x7A1C15), picture=True)
H.swatch("hull_open_green", 12, 4, 16, 8, lambda x, y, w, h: H.rgb(0x07140A if math.hypot(x - 3.5, y - 3.5) < 2.6 else 0x23502B), picture=True)
H.save(TEX)


def shell(hull, front, spent=False):
    """A 12 gauge shell lying along z, centred on (8, 8, 8): crimped (or fired open) hull toward -z, brass head behind."""
    out = []
    z0 = 6.35 - (0.25 if spent else 0.0)
    H.rod(out, z0, 9.0, 0.42, hull, name="hull", caps=(False, False))
    H.sticker(out, 7.58, 7.58, z0 - 0.01, 8.42, 8.42, z0, front, "north")
    H.rod(out, 9.0, 9.5, 0.44, "brass", name="head", caps=(False, False))
    H.rod(out, 9.5, 9.62, 0.48, "brass", cap="brass_end", name="rim")
    if spent:
        # The fired crimp opens out into a short flare.
        H.rod(out, z0, z0 + 0.25, 0.46, hull, caps=(False, False))
    return out


SHELL_DISPLAYS = {
    "gui": display([0, -90, 45], centred([0, -90, 45], 2.6, (8, 8, 8)), 2.6),
    "ground": display([0, 0, 0], centred([0, 0, 0], 1.0, (8, 8, 8), 2.0), 1.0),
    "fixed": display([0, -90, 45], centred([0, -90, 45], 2.6, (8, 8, 8)), 2.6),
    "thirdperson_righthand": display([0, 90, -30], centred([0, 90, -30], 1.0, (8, 8, 8), 2.0), 1.0),
    "firstperson_righthand": display([0, 90, -30], centred([0, 90, -30], 1.0, (8, 8, 8), 3.0), 1.0),
}
for name, hull, front, opened in (("shell_12g_buckshot", "red", "crimp_red", "hull_open"),
                                  ("shell_12g_slug", "green", "slug_end", "hull_open_green")):
    for variant, elements in (("3d", shell(hull, front)), ("spent", shell(hull, opened, spent=True))):
        check(f"{name}_{variant}", elements)
        write(os.path.join(MODELS, f"{name}_{variant}.json"), model(H, elements, SHELL_DISPLAYS))
        # An item model definition of its own, for stacks the client renders with the item_model component.
        write(os.path.join(ITEMS, f"{name}_{variant}.json"), {"model": ref(f"{name}_{variant}")})


def shell_sprite(name, hull, hull_dark, hull_hi, front):
    """The inventory sprite: a shell on the diagonal, brass head at the lower left."""
    pal = {"b": H.rgb(0xCFA448), "B": H.rgb(0xF2D27C), "d": H.rgb(0x8C6A28), "h": H.rgb(hull), "H": H.rgb(hull_hi),
           "k": H.rgb(hull_dark), "f": H.rgb(front), "o": H.rgb(0x1A1A1A, 120)}
    rows = [
        "................",
        "................",
        "...........kff..",
        "..........khHfk.",
        ".........khHhk..",
        "........khHhk...",
        ".......khHhk....",
        "......khHhk.....",
        ".....khHhk......",
        "....dbBbk.......",
        "...dbBbd........",
        "..dbBbd.........",
        "..dbbd..........",
        "...dd...........",
        "................",
        "................",
    ]
    sprite(os.path.join(TEX, f"item/{name}.png"), rows, pal)
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


shell_sprite("shell_12g_buckshot", 0xB82A20, 0x7A1C15, 0xE05A4C, 0x8E1E16)
shell_sprite("shell_12g_slug", 0x2F6E3A, 0x1E4826, 0x5AA866, 0x9A9EA2)
print("shotguns done")
