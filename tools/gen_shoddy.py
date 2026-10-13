"""Builds the homemade Scavenger-tier guns (Pipe Pistol, Pipe Rifle, Blunderbuss, Junk Repeater) and their ammo: one
shared texture atlas, each gun at rest plus six frames of its moving part (the pistol's striker, the rifle's bolt, the
blunderbuss's ramrod, the repeater's charging lever), chosen by mymod:gun_animation (1-6, see ShotgunAnimation), a muzzle
flash layer, flat inventory sprites for the Crude Round and Scrap Shell, and the small 3D rounds the client draws in
the hand while loading (and flying out of the port).

Art: few cubes, flat colours from a short palette, no noise; pipe, wood, tape, wire and a salvaged servo.

Model space is in pixels: the bore runs along z at x = 8 with the muzzle toward -z, +x is the gun's right. GunDisplay,
GunHands and ShotgunAnimation mirror the display transforms, grip points and part positions printed at the end.

Usage: python3 tools/gen_shoddy.py src/main/resources/assets/mymod [--preview out.png]"""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, write, model, ref, range_dispatch, display, display_matrix, placed, centred,
                    bounds, bounds_centre, flash_models, with_flash, sprite, png, element_rotation, mul, apply, ry, IDENTITY)

ARGS = sys.argv[1:]
PREVIEW = None
if "--preview" in ARGS:
    i = ARGS.index("--preview")
    PREVIEW = ARGS[i + 1]
    del ARGS[i:i + 2]
ASSETS = ARGS[0]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

FRAMES = 6
# Where long guns put the shooting hand in first person (the M4's grip point, item space pixels), and pistols (the Glock's).
FP_RIFLE_GRIP = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
FP_PISTOL_GRIP = (-0.020 * 16, 0.131 * 16, -0.181 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))

# ============================================================================================================ palette

K = Kit("shoddy", "item/shoddy_guns", seed=4040)
PIPE, PIPE_DARK = 0x8F959B, 0x676D73
IRON, IRON_DARK = 0x565A60, 0x3C3F44
WOOD, WOOD_GRAIN, WOOD_END = 0xA47A4A, 0x8C653C, 0x7C5833
TAPE = 0x34363A
COPPER, COPPER_DARK = 0xC07345, 0x8E5233
PAPER = 0xD8D0B8
SERVO, SERVO_LIGHT = 0x2E3137, 0x484C54
LED = 0xE24A3A
BLACK = 0x101112


def flat(hx):
    return lambda x, y, w, h: K.rgb(hx)


K.swatch("pipe", 0, 0, 4, 4, flat(PIPE))
K.swatch("pipe_dark", 4, 0, 8, 4, flat(PIPE_DARK))
K.swatch("iron", 8, 0, 12, 4, flat(IRON))
K.swatch("iron_dark", 12, 0, 16, 4, flat(IRON_DARK))
# Plank grain: a darker line every four texels, along u (wood_u) or along v (wood_v).
K.swatch("wood_u", 0, 4, 8, 6, lambda x, y, w, h: K.rgb(WOOD_GRAIN if y % 4 == 3 else WOOD))
K.swatch("wood_v", 8, 4, 10, 12, lambda x, y, w, h: K.rgb(WOOD_GRAIN if x % 4 == 3 else WOOD))
K.swatch("wood_end", 10, 4, 12, 6, flat(WOOD_END))
K.swatch("tape", 12, 4, 14, 6, flat(TAPE))
K.swatch("copper", 14, 4, 16, 6, flat(COPPER))
K.swatch("copper_dark", 10, 6, 12, 8, flat(COPPER_DARK))
K.swatch("paper", 12, 6, 14, 8, flat(PAPER))
K.swatch("servo", 14, 6, 16, 8, flat(SERVO))
K.swatch("servo_light", 10, 8, 12, 10, flat(SERVO_LIGHT))
K.swatch("led", 12, 8, 13, 9, flat(LED))
K.swatch("black", 13, 8, 14, 9, flat(BLACK))
K.swatch("bore", 0, 6, 4, 10, lambda x, y, w, h: K.rgb(BLACK if math.hypot(x - 7.5, y - 7.5) < 6.2 else PIPE_DARK), picture=True)
K.swatch("bell", 4, 6, 8, 10, lambda x, y, w, h: K.rgb(BLACK if math.hypot(x - 7.5, y - 7.5) < 6.0 else COPPER_DARK), picture=True)
K.save(TEX)


def bore(out, cy, z, r):
    K.sticker(out, 8 - r, cy - r, z - 0.03, 8 + r, cy + r, z - 0.02, "bore", "north")


def wood_box(out, x0, y0, z0, x1, y1, z1, rot=None, name=None):
    """A plank or block of wood: grain along z on the sides and top, end grain on the ends."""
    return K.box(out, x0, y0, z0, x1, y1, z1, "wood_v", rot=rot, name=name, east="wood_u", west="wood_u",
                 north="wood_end", south="wood_end")


def nail(out, x, y0, y1, z, name=None):
    K.box(out, x - 0.15, y0, z - 0.15, x + 0.15, y1, z + 0.15, "iron", name=name)


# ========================================================================================================= Pipe Pistol

PP_BY = 10.0
PP_TRAVEL = 2.2
PP_GRIP = (8.0, 6.4, 8.4)
PP_WORK = (8.0, 10.0, 9.8)       # the striker's knob
PP_PORT = (8.0, 9.0, 8.8)        # the breech cap, unscrewed to drop a round in


def pipe_pistol(travel):
    out = []
    K.rod(out, -3.0, 5.0, 0.7, "pipe", cy=PP_BY, cap="pipe_dark", caps=(True, False), name="barrel")
    bore(out, PP_BY, -3.0, 0.5)
    K.rod(out, 5.0, 8.0, 1.05, "pipe", cy=PP_BY, cap="pipe_dark", name="coupling")
    K.box(out, 7.85, 10.65, -2.6, 8.15, 11.05, -2.2, "iron_dark", name="front_sight")
    # A plank grip lashed under the coupling with tape, string wound round it.
    grip = []
    wood_box(grip, 7.1, 3.8, 6.2, 8.9, 9.1, 9.3, name="grip")
    K.box(grip, 7.05, 5.2, 6.15, 8.95, 6.4, 9.35, "paper", name="string_wrap")
    out += rotate(grip, rx(-14), (8.0, 9.1, 7.7))
    K.box(out, 6.85, 8.45, 6.3, 9.15, 11.15, 7.3, "tape", name="tape")
    nail(out, 8.0, 7.6, 8.95, 5.35, name="trigger")
    # The striker: an end cap with a nail through it, drawn back to cock.
    striker = []
    K.rod(striker, 8.0, 9.2, 0.9, "iron", cy=PP_BY, cap="iron_dark", name="end_cap")
    K.box(striker, 7.6, 9.6, 9.2, 8.4, 10.4, 10.2, "iron_dark", name="striker_knob")
    K.box(striker, 7.85, 9.85, 6.6, 8.15, 10.15, 7.95, "iron", name="striker_pin")
    out += move(striker, dz=travel)
    return out


# ========================================================================================================= Pipe Rifle

PR_BY = 10.0
PR_TRAVEL = 3.0
PR_GRIP = (8.0, 7.6, 12.8)
PR_SUPPORT = (8.0, 7.9, -3.0)
PR_WORK = (11.0, 10.0, 9.7)      # the bolt knob
PR_PORT = (8.0, 11.0, 7.6)       # the bolt opening on top
PR_EJECT = (9.6, 10.6, 7.6)


def pipe_rifle(travel):
    out = []
    K.rod(out, -15.5, 2.4, 0.6, "pipe", cy=PR_BY, cap="pipe_dark", caps=(True, False), name="barrel")
    bore(out, PR_BY, -15.5, 0.42)
    K.rod(out, 2.2, 3.0, 0.82, "pipe_dark", cy=PR_BY, name="reducer")
    K.rod(out, 3.0, 11.5, 1.0, "pipe", cy=PR_BY, cap="pipe_dark", name="receiver")
    K.sticker(out, 9.0, 9.8, 6.4, 9.03, 10.2, 10.4, "black", "east", name="bolt_slot")
    nail(out, 8.0, 10.5, 11.2, -14.6, name="front_sight")
    K.box(out, 7.6, 10.95, 4.0, 8.4, 11.45, 4.4, "iron_dark", name="rear_sight")
    wood_box(out, 7.2, 8.0, -7.0, 8.8, 9.4, 3.0, name="fore_end")
    for z in (-6.2, -0.8):
        K.box(out, 7.05, 7.85, z, 8.95, 10.75, z + 1.0, "tape", name="tape")
    stock = []
    wood_box(stock, 7.15, 5.0, 10.8, 8.85, 9.4, 27.5, name="stock")
    out += rotate(stock, rx(6), (8.0, 9.4, 10.8))
    K.box(out, 6.85, 8.3, 10.6, 9.15, 11.15, 11.3, "copper", name="wire")
    nail(out, 8.0, 7.3, 9.05, 8.6, name="trigger")
    bolt = []
    K.box(bolt, 9.0, 9.75, 9.4, 10.7, 10.25, 10.0, "iron", name="bolt_handle")
    K.box(bolt, 10.6, 9.6, 9.3, 11.4, 10.4, 10.1, "iron_dark", name="bolt_knob")
    out += move(bolt, dz=travel)
    return out


# ========================================================================================================= Blunderbuss

BB_BY = 9.6
BB_TRAVEL = -5.0                 # the ramrod slides out forward
BB_GRIP = (8.0, 7.6, 9.2)
BB_SUPPORT = (8.0, 6.9, -2.5)
BB_WORK = (8.0, 7.2, -9.6)       # the ramrod's tip
BB_PORT = (8.0, 9.6, -14.6)      # the muzzle: it's loaded from the front


def blunderbuss(travel):
    out = []
    K.rod(out, -11.0, 6.0, 0.75, "iron", cy=BB_BY, cap="iron_dark", caps=(False, True), name="barrel")
    K.rod(out, -12.6, -11.0, 1.1, "copper", cy=BB_BY, caps=(False, False), name="bell_neck")
    K.rod(out, -14.5, -12.6, 1.55, "copper", cy=BB_BY, cap="copper_dark", name="bell")
    K.sticker(out, 6.7, BB_BY - 1.3, -14.53, 9.3, BB_BY + 1.3, -14.52, "bell", "north", name="muzzle")
    wood_box(out, 7.15, 7.6, -8.0, 8.85, 9.2, 6.0, name="fore_stock")
    wrist = []
    wood_box(wrist, 7.1, 6.0, 5.8, 8.9, 9.6, 12.0, name="wrist")
    out += rotate(wrist, rx(10), (8.0, 9.6, 5.8))
    butt = []
    wood_box(butt, 6.95, 3.4, 11.2, 9.05, 8.7, 23.0, name="butt")
    out += rotate(butt, rx(13), (8.0, 8.7, 11.2))
    # The lock: plate, pan and a cocked hammer gripping its flint.
    K.box(out, 8.85, 8.6, 3.5, 9.15, 10.2, 8.0, "iron_dark", name="lock_plate")
    K.box(out, 9.0, 10.0, 4.3, 9.5, 10.4, 5.3, "iron", name="pan")
    hammer = []
    K.box(hammer, 9.15, 9.8, 6.2, 9.55, 11.6, 6.8, "iron", name="hammer")
    K.box(hammer, 9.1, 11.3, 5.6, 9.6, 11.8, 6.6, "iron_dark", name="flint")
    out += rotate(hammer, rx(-25), (9.35, 9.9, 6.5))
    for z in (-6.8, 0.6):
        K.box(out, 7.0, 6.8, z, 9.0, 10.45, z + 0.6, "copper", name="band")
    nail(out, 8.0, 7.0, 8.0, 7.6, name="trigger")
    K.box(out, 7.7, 6.2, 6.6, 8.3, 6.5, 9.0, "copper", name="trigger_guard")
    rod = []
    K.box(rod, 7.8, 7.0, -9.6, 8.2, 7.4, 5.0, "wood_u", name="ramrod")
    K.box(rod, 7.75, 6.95, -9.9, 8.25, 7.45, -9.6, "copper", name="ramrod_tip")
    out += move(rod, dz=travel)
    return out


# ======================================================================================================= Junk Repeater

JR_BY = 10.2
JR_TRAVEL = 2.6
JR_GRIP = (8.0, 6.3, 10.0)
JR_SUPPORT = (8.0, 9.1, -3.0)
JR_WORK = (8.0, 12.45, 8.7)      # the charging lever's knob
JR_PORT = (8.0, 11.6, 6.2)
JR_EJECT = (9.5, 10.3, 6.5)


def junk_repeater(travel):
    out = []
    K.rod(out, -14.5, 2.2, 0.6, "pipe", cy=JR_BY, cap="pipe_dark", caps=(True, False), name="barrel")
    bore(out, JR_BY, -14.5, 0.42)
    nail(out, 8.0, 10.7, 11.4, -13.6, name="front_sight")
    K.box(out, 7.25, 9.45, -9.4, 8.75, 10.95, -8.8, "iron", name="hose_clamp")
    # A scrap box for a receiver, its port on the right, and a tin magazine under it.
    K.box(out, 6.8, 8.0, 2.0, 9.2, 11.6, 11.0, "iron", name="receiver")
    K.sticker(out, 9.2, 9.6, 5.0, 9.23, 10.9, 8.0, "black", "east", name="ejection_port")
    K.box(out, 7.2, 4.8, 3.4, 8.8, 8.0, 6.0, "iron_dark", name="magazine")
    # The salvaged Skullcrane servo on the left, its status light, and the link to the lever.
    K.box(out, 5.9, 8.6, 4.0, 6.8, 12.1, 8.5, "servo", name="servo")
    K.box(out, 5.7, 9.3, 8.5, 6.8, 10.1, 9.8, "servo_light", name="servo_joint")
    K.sticker(out, 5.87, 10.0, 4.6, 5.9, 10.4, 5.0, "led", "west", emit=True, name="servo_light")
    grip = []
    wood_box(grip, 7.15, 4.0, 8.4, 8.85, 8.0, 10.8, name="grip")
    out += rotate(grip, rx(-16), (8.0, 8.0, 9.6))
    stock = []
    wood_box(stock, 7.2, 5.4, 11.0, 8.8, 10.4, 25.0, name="stock")
    out += rotate(stock, rx(5), (8.0, 10.4, 11.0))
    K.box(out, 6.65, 7.85, 10.3, 9.35, 11.75, 11.5, "tape", name="tape")
    nail(out, 8.0, 6.6, 8.0, 7.7, name="trigger")
    lever = []
    K.box(lever, 7.6, 11.6, 7.8, 8.4, 12.2, 9.0, "iron_dark", name="lever")
    K.box(lever, 7.4, 12.2, 8.4, 8.6, 12.7, 9.0, "iron_dark", name="lever_knob")
    K.box(lever, 6.75, 11.65, 7.9, 7.65, 11.95, 8.3, "copper", name="servo_link")
    out += move(lever, dz=travel)
    return out


# ============================================================================================================ displays


def gun_displays(grip, elements, fp_scale, gui_scale, pistol=False):
    fp_rot = [0, -6 if pistol else -8, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, FP_PISTOL_GRIP if pistol else FP_RIFLE_GRIP), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, grip, TP_GRIP_TARGET), 0.65)
    c = bounds_centre(elements)
    gui_rot = [0, -90, 0]
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], 0.32 if not pistol else 0.5, c, 2.0), 0.32 if not pistol else 0.5),
        "fixed": display(gui_rot, centred(gui_rot, gui_scale * 1.3, c), gui_scale * 1.3),
    }


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


SUMMARY = []


def write_gun(name, build, travel, grip, support, fp_scale, gui_scale, flash_at, pistol=False):
    frames = [build(travel * k / FRAMES) for k in range(FRAMES + 1)]
    every = [e for f in frames for e in f]
    displays = gun_displays(grip, every, fp_scale, gui_scale, pistol)
    for i, elements in enumerate(frames):
        check(f"{name}_f{i}", elements)
        write(os.path.join(MODELS, name + (f"_f{i}" if i else "") + ".json"), model(K, elements, displays))
    flashes = flash_models(MODELS, name, flash_at[0], flash_at[1], displays)
    body = range_dispatch("mymod:gun_animation",
                          [(k - 0.5, ref(f"{name}_f{k}")) for k in range(1, FRAMES + 1)] + [(FRAMES + 0.5, ref(name))], ref(name))
    write(os.path.join(ITEMS, name + ".json"), {"model": with_flash(body, flashes)})
    t = displays["firstperson_righthand"]
    line = (f"{name}: {len(frames[0])} elements; firstperson {t['translation']} rot {t['rotation'][1]} scale {fp_scale}; "
            f"grip {fp_point(displays, grip)}" + (f" support {fp_point(displays, support)}" if support else ""))
    print(line)
    SUMMARY.append((name, frames[0], frames[FRAMES], gui_scale))


write_gun("pipe_pistol", pipe_pistol, PP_TRAVEL, PP_GRIP, None, 0.65, 0.95, (-3.0, PP_BY), pistol=True)
write_gun("pipe_rifle", pipe_rifle, PR_TRAVEL, PR_GRIP, PR_SUPPORT, 0.55, 0.36, (-15.5, PR_BY))
write_gun("blunderbuss", blunderbuss, BB_TRAVEL, BB_GRIP, BB_SUPPORT, 0.6, 0.42, (-14.5, BB_BY))
write_gun("junk_repeater", junk_repeater, JR_TRAVEL, JR_GRIP, JR_SUPPORT, 0.55, 0.38, (-14.5, JR_BY))

# =============================================================================================================== ammo

ROUND_DISPLAYS = {
    "gui": display([0, -90, 45], centred([0, -90, 45], 2.6, (8, 8, 8)), 2.6),
    "ground": display([0, 0, 0], centred([0, 0, 0], 1.0, (8, 8, 8), 2.0), 1.0),
    "fixed": display([0, -90, 45], centred([0, -90, 45], 2.6, (8, 8, 8)), 2.6),
    "thirdperson_righthand": display([0, 90, -30], centred([0, 90, -30], 1.0, (8, 8, 8), 2.0), 1.0),
    "firstperson_righthand": display([0, 90, -30], centred([0, 90, -30], 1.0, (8, 8, 8), 3.0), 1.0),
}


def crude_round(spent=False):
    """A paper case with an iron nugget slug in its nose, lying along z, centred on (8, 8, 8)."""
    out = []
    K.rod(out, 7.0, 9.2, 0.3, "paper", cap="paper" if not spent else "black", caps=(spent, True), name="case")
    if not spent:
        K.rod(out, 6.6, 7.0, 0.26, "iron", cap="iron_dark", caps=(True, False), name="slug")
    return out


def scrap_shell(spent=False):
    out = []
    K.rod(out, 6.7, 9.3, 0.42, "paper", cap="tape", name="hull")
    K.rod(out, 9.3, 9.6, 0.44, "copper", cap="copper_dark", caps=(False, True), name="head")
    return out


def handful(colour, dark):
    """A pinch of loose shot: three small lumps."""
    out = []
    for (x, y, z, s, m) in ((7.4, 7.6, 7.6, 0.55, colour), (8.2, 7.7, 8.3, 0.5, dark), (8.0, 8.3, 7.5, 0.45, colour)):
        K.box(out, x, y, z, x + s, y + s, z + s, m)
    return out


for name, elements in (("crude_round_3d", crude_round()), ("crude_round_spent", crude_round(True)),
                       ("scrap_shell_3d", scrap_shell()), ("gravel_3d", handful("pipe", "pipe_dark")),
                       ("flint_3d", handful("iron_dark", "iron"))):
    check(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, ROUND_DISPLAYS))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


def rgba(hx):
    return ((hx >> 16) & 255, (hx >> 8) & 255, hx & 255, 255)


def sprites():
    pal = {"p": rgba(PAPER), "P": rgba(0xEFE8D6), "d": rgba(0x9A9078), "s": rgba(0x7A7F86), "S": rgba(0xA3A8AE),
           "o": rgba(IRON_DARK), "c": rgba(COPPER), "C": rgba(0xDE9461), "q": rgba(COPPER_DARK), "t": rgba(TAPE),
           "n": rgba(PIPE)}
    # A stubby paper cartridge with a grey nugget slug in its nose, on the diagonal like the 12 gauge shells.
    crude = [
        "................",
        "................",
        "................",
        "..........oso...",
        ".........oSsso..",
        "........oSsso...",
        ".......dPppd....",
        "......dPppd.....",
        ".....dPppd......",
        "....dPppd.......",
        "...dPppd........",
        "...dppd.........",
        "....dd..........",
        "................",
        "................",
        "................",
    ]
    # The 12 gauge shell's shape in paper and copper, its taped crimp showing the shot.
    shell = [
        "................",
        "................",
        "...........ttn..",
        "..........tPpnt.",
        ".........dPppt..",
        "........dPppd...",
        ".......dPppd....",
        "......dPppd.....",
        ".....dPppd......",
        "....qcCcd.......",
        "...qcCcq........",
        "..qcCcq.........",
        "..qccq..........",
        "...qq...........",
        "................",
        "................",
    ]
    for name, rows in (("crude_round", crude), ("scrap_shell", shell)):
        sprite(os.path.join(TEX, f"item/{name}.png"), rows, pal)
        write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
        write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


sprites()

# ============================================================================================================ preview

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def corners(e, face):
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def render(elements, view, scale, w, h, centre):
    """Orthographic render: view is a matrix taking model space to (right, up, toward the camera)."""
    img = [None] * (w * h)
    depth = [-1e9] * (w * h)
    atlas, size = K.atlas, K.size
    for e in elements:
        r = element_rotation(e)
        for face, f in e["faces"].items():
            pts = corners(e, face)
            if r:
                m, o = r
                pts = [[o[a] + v for a, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))] for p in pts]
            sp = []
            for p in pts:
                v = apply(view, [p[k] - centre[k] for k in range(3)])
                sp.append((w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2]))
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            shade = 1.0 if e.get("light_emission") else SHADE[face]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (sp[i] for i in tri)
                ta, tb, tc = (uvs[i] for i in tri)
                area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
                if abs(area) < 1e-9:
                    continue
                xs0, xs1 = max(0, int(min(a[0], b[0], c[0]))), min(w - 1, int(max(a[0], b[0], c[0])) + 1)
                ys0, ys1 = max(0, int(min(a[1], b[1], c[1]))), min(h - 1, int(max(a[1], b[1], c[1])) + 1)
                for py in range(ys0, ys1 + 1):
                    for px in range(xs0, xs1 + 1):
                        qx, qy = px + 0.5, py + 0.5
                        l1 = ((b[0] - qx) * (c[1] - qy) - (b[1] - qy) * (c[0] - qx)) / area
                        l2 = ((c[0] - qx) * (a[1] - qy) - (c[1] - qy) * (a[0] - qx)) / area
                        l3 = 1 - l1 - l2
                        if l1 < -1e-6 or l2 < -1e-6 or l3 < -1e-6:
                            continue
                        z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                        i = py * w + px
                        if z <= depth[i]:
                            continue
                        tu = l1 * ta[0] + l2 * tb[0] + l3 * tc[0]
                        tv = l1 * ta[1] + l2 * tb[1] + l3 * tc[1]
                        tx = min(size - 1, max(0, int(tu / 16 * size)))
                        ty = min(size - 1, max(0, int(tv / 16 * size)))
                        col = atlas[ty * size + tx]
                        if col[3] < 128:
                            continue
                        depth[i] = z
                        img[i] = tuple(int(col[k] * shade) for k in range(3))
    return img


def blit(out, ow, img, w, h, ox, oy, k=1):
    for y in range(h * k):
        for x in range(w * k):
            c = img[(y // k) * w + x // k]
            if c is not None:
                out[(oy + y) * ow + ox + x] = c + (255,)


def preview(path):
    """Each gun: from the right at rest, from the right front with its part worked, from the left front, and its inventory
    icon at GUI scale 3 (48 screen pixels a slot), doubled."""
    side = [[0, 0, -1], [0, 1, 0], [1, 0, 0]]                 # from the right, muzzle to the right
    tilt = [[1, 0, 0], [0, math.cos(-0.35), -math.sin(-0.35)], [0, math.sin(-0.35), math.cos(-0.35)]]
    right = mul(mul(tilt, side), ry(-35))
    left = mul(mul(tilt, side), ry(-145))
    cell_w, cell_h, s = 400, 190, 8
    icon, blow = 48, 2
    W = 3 * cell_w + icon * blow + 30
    H = len(SUMMARY) * cell_h
    out = [(58, 60, 66, 255)] * (W * H)
    for row, (name, rest, worked, gui_scale) in enumerate(SUMMARY):
        lo, hi = bounds(rest)
        c = [(lo[a] + hi[a]) / 2 for a in range(3)]
        oy = row * cell_h
        k = s * (2.4 if name == "pipe_pistol" else 1.0)
        for col, (view, elements) in enumerate(((side, rest), (right, worked), (left, rest))):
            blit(out, W, render(elements, view, k, cell_w, cell_h, c), cell_w, cell_h, col * cell_w, oy)
        ic = render(rest, side, gui_scale * 3, icon, icon, c)
        for i in range(icon * icon):
            if ic[i] is None:
                ic[i] = (139, 139, 139)
        blit(out, W, ic, icon, icon, 3 * cell_w + 15, oy + (cell_h - icon * blow) // 2, blow)
    png(path, W, H, out)
    print("preview:", path)


if PREVIEW:
    preview(PREVIEW)
print("shoddy guns done")
