"""Builds the energy weapons' assets: the Laser Rifle, the Railgun and the Ray Gun (each with the glowing parts that
mymod:energy_glow lights up as it heats or charges), the Weapon Station parts (Laser Emitter, Laser Rifle Housing,
Railgun Rails), and the Copper Coil, Uranium Power Cell and Rail Slug sprites; the crafting recipes for coils and cells
(with recipe-book advancements), the laser and railgun damage types, the synthesised sounds (laser shot, railgun charge
and shot, the alien zap; with ffmpeg on the path) and the re-pitched vanilla ones, and language entries.

Model space is in pixels: each gun's bore runs along z at x = 8 with the muzzle toward -z, +x its right. GunDisplay,
GunHands and GunAim mirror the first-person transforms, grip points and sight printed at the end, so move them
together. Clean flat paint, one smooth ramp per material, no speckle.

Usage: python3 tools/gen_energy_weapons.py src/main/resources/assets/mymod"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, write, model, ref, range_dispatch, composite, display, display_matrix, placed,
                    centred, bounds_centre, DIAGONAL, png, mix, sprite)

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))

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
    return range_dispatch("mymod:energy_glow", entries, ref(name))


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


L_CREDIT = "mymod laser rifle - generated by tools/gen_energy_weapons.py"
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
R = Kit("railgun", "item/railgun", seed=1908)
R.swatch("alloy", 0, 0, 4, 4, ramp(R, 0x857AA6, 0x4C4468))
R.swatch("alloy_dark", 4, 0, 8, 4, flat(R, 0x2E2840))
R.swatch("duralumin", 8, 0, 12, 4, ramp(R, 0xD3D9DF, 0xA5ADB6))
R.swatch("tungsten", 12, 0, 16, 4, ramp(R, 0x50555C, 0x33373C))
R.swatch("copper", 0, 4, 4, 8, ramp(R, 0xE39256, 0xA65A2A))
R.swatch("rubber", 4, 4, 8, 8, flat(R, 0x1E1F21))
R.swatch("reactor", 8, 4, 12, 8, glow_disc(R, 0xF0FFE6, 0x58D838), picture=True)
R.swatch("glass", 12, 4, 16, 8, glow_disc(R, 0x9AD8FF, 0x1A2A40), picture=True)
R.swatch("charge_1", 0, 8, 4, 12, ramp(R, 0x8CB8FF, 0x4C78E0))
R.swatch("charge_2", 4, 8, 8, 12, ramp(R, 0xD8ECFF, 0x8CB8FF))
R.swatch("charge_3", 8, 8, 12, 12, ramp(R, 0xFFFFFF, 0xD8ECFF))
R.save(TEX)

R_GRIP = (8.0, 5.6, 9.4)
R_SUPPORT = (8.0, 6.7, -6.0)
R_COILS = (-13.0, -8.8, -4.6)


def rails():
    out = []
    R.box(out, 7.1, 10.2, -15.5, 8.9, 11.0, -0.6, "tungsten", name="top_rail")
    R.box(out, 7.1, 7.0, -15.5, 8.9, 7.8, -0.6, "tungsten", name="bottom_rail")
    for z in R_COILS:
        R.box(out, 6.4, 6.6, z, 9.6, 11.4, z + 1.4, "copper", name="coil")
    R.box(out, 6.7, 6.8, -3.4, 9.3, 11.2, -1.0, "duralumin", name="rail_frame")
    return out


def railgun_body():
    out = []
    R.chamfered(out, 6.0, 6.2, -1.0, 10.0, 11.4, 12.0, 1.0, "alloy", cap="alloy_dark", name="body")
    for x0, x1, face in ((5.98, 5.99, "west"), (10.01, 10.02, "east")):
        R.sticker(out, x0, 7.6, 3.0, x1, 10.2, 8.0, "reactor", face, emit=True, name="reactor_window")
    R.box(out, 7.2, 3.2, 8.0, 8.8, 6.6, 10.6, "rubber", rot=("x", -15, (8.0, 6.6, 9.3)), name="grip")
    R.box(out, 7.75, 5.6, 6.4, 8.25, 6.2, 7.0, "tungsten", name="trigger")
    R.box(out, 7.0, 6.4, 12.0, 9.0, 10.8, 19.0, "duralumin", name="stock")
    R.box(out, 7.0, 6.4, 19.0, 9.0, 10.8, 19.7, "rubber", name="butt_pad")
    for z in (2.6, 7.2):
        R.box(out, 7.5, 11.4, z, 8.5, 12.2, z + 1.0, "tungsten", name="scope_mount")
    R.rod(out, 1.0, 10.0, 1.0, "tungsten", cy=13.1, name="scope")
    R.sticker(out, 7.2, 12.3, 0.98, 8.8, 13.9, 0.99, "glass", "north", name="scope_lens")
    return out


def railgun_glow(level):
    out = []
    mat = "charge_%d" % level
    for z in R_COILS:
        R.box(out, 6.35, 6.55, z - 0.05, 9.65, 11.45, z + 1.45, mat, emit=True)
    # The arc between the rails.
    R.box(out, 7.85, 7.85, -15.1, 8.15, 10.15, -1.2, mat, emit=True, north=None, south=None)
    return out


R_CREDIT = "mymod railgun - generated by tools/gen_energy_weapons.py"
railgun = rails() + railgun_body()
check("railgun", railgun)
railgun_displays = gun_displays(R_GRIP, 0.5)
railgun_displays.update(still_displays(railgun, 0.38))
write(os.path.join(MODELS, "railgun.json"), model(R, railgun, railgun_displays, credit=R_CREDIT))
write(os.path.join(ITEMS, "railgun.json"), {"model": glow_levels(R, "railgun", [railgun_glow(1), railgun_glow(2), railgun_glow(3)],
                                                                 railgun_displays, [0.15, 0.5, 0.9], R_CREDIT)})
rail_part = rails()
check("railgun_rails", rail_part)
write(os.path.join(MODELS, "railgun_rails.json"), model(R, rail_part, part_displays(rail_part, 0.55), credit=R_CREDIT))
write(os.path.join(ITEMS, "railgun_rails.json"), {"model": ref("railgun_rails")})
report("railgun", railgun_displays, R_GRIP, R_SUPPORT)

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


G_CREDIT = "mymod ray gun - generated by tools/gen_energy_weapons.py"
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
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
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
            ("has_the_recipe", {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shaped(name, pattern, key, result, count, category, unlock):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, unlock, category)


# Copper wound round a rubber former: two coils.
shaped("copper_coil", [" C ", "CRC", " C "], OrderedDict([("C", "minecraft:copper_ingot"), ("R", "mymod:rubber")]),
       "mymod:copper_coil", 2, "misc", "mymod:rubber")
# Steel plate casing, a chip to regulate it, copper contacts, and the uranium: two cells from a piece of enriched
# uranium, four from a fuel rod.
CELL_KEY = OrderedDict([("S", "mymod:steel_plate"), ("C", "mymod:computer_chip"), ("I", "minecraft:copper_ingot")])
shaped("uranium_power_cell", ["SCS", "IUI", "SIS"], OrderedDict(list(CELL_KEY.items()) + [("U", "mymod:enriched_uranium")]),
       "mymod:uranium_power_cell", 2, "equipment", "mymod:enriched_uranium")
shaped("uranium_power_cell_from_fuel_rod", ["SCS", "IUI", "SIS"], OrderedDict(list(CELL_KEY.items()) + [("U", "mymod:uranium_fuel_rod")]),
       "mymod:uranium_power_cell", 4, "equipment", "mymod:uranium_fuel_rod")

for name in ("laser", "railgun_slug"):
    write(os.path.join(DATA, "damage_type", name + ".json"), OrderedDict([
        ("exhaustion", 0.1), ("message_id", "mymod." + name), ("scaling", "when_caused_by_living_non_player")]))
# A slug is a projectile. (A laser isn't fire damage, though it sets things alight: a fire-proof UFO still feels it.)
for tag, entry in (("is_projectile", "mymod:railgun_slug"),):
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
    return OrderedDict([("subtitle", "subtitles.mymod." + name), ("sounds", ["mymod:" + name])])


merge_json(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("laser_shot", own("laser_shot")),
    ("railgun_charge", own("railgun_charge")),
    ("railgun_shot", own("railgun_shot")),
    ("ray_zap", own("ray_zap")),
    ("energy_overheat", OrderedDict([("subtitle", "subtitles.mymod.energy_overheat"),
                                     ("sounds", [OrderedDict([("name", "minecraft:random/fizz"), ("pitch", 0.8)])])])),
    ("power_cell_load", OrderedDict([("subtitle", "subtitles.mymod.power_cell_load"),
                                     ("sounds", [OrderedDict([("name", "minecraft:block/beacon/activate"), ("pitch", 1.9), ("volume", 0.6)])])])),
    ("rail_slug_load", OrderedDict([("subtitle", "subtitles.mymod.rail_slug_load"),
                                    ("sounds", [OrderedDict([("name", "minecraft:item/crossbow/loading_end"), ("pitch", 1.4)])])])),
]))

merge_json(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("item.mymod.copper_coil", "Copper Coil"),
    ("item.mymod.uranium_power_cell", "Uranium Power Cell"),
    ("item.mymod.steel_rail_slug", "Steel Rail Slug"),
    ("item.mymod.tungsten_rail_slug", "Tungsten Rail Slug"),
    ("item.mymod.laser_emitter", "Laser Emitter"),
    ("item.mymod.laser_rifle_housing", "Laser Rifle Housing"),
    ("item.mymod.laser_rifle", "Laser Rifle"),
    ("item.mymod.railgun_rails", "Railgun Rails"),
    ("item.mymod.railgun", "Railgun"),
    ("item.mymod.ray_gun", "Ray Gun"),
    ("item.mymod.energy.overheated", "Overheated: venting"),
    ("item.mymod.laser_rifle.no_cell", "No cell: load a Uranium Power Cell"),
    ("item.mymod.laser_rifle.charge", "Cell: %s / %s shots"),
    ("item.mymod.laser_rifle.hint", "Punch to load a Uranium Power Cell; sneak + punch to swap a part-used one"),
    ("item.mymod.laser_rifle.cell_full", "Cell full"),
    ("item.mymod.laser_rifle.cell_not_flat", "Cell not flat: sneak + punch to swap it"),
    ("item.mymod.laser_rifle.no_cells", "No Uranium Power Cells"),
    ("item.mymod.railgun.empty", "No slugs loaded"),
    ("item.mymod.railgun.charge_hint", "Hold use to charge; it fires when the coils are full"),
    ("item.mymod.railgun.hint", "Punch to load slugs; sneak + punch to switch steel and tungsten"),
    ("item.mymod.railgun.cooling", "Reactor cooling"),
    ("item.mymod.railgun.full", "Rails loaded"),
    ("item.mymod.railgun.no_slugs", "No Rail Slugs"),
    ("item.mymod.ray_gun.hint", "Needs no ammo: lets itself cool between zaps"),
    ("item.mymod.ray_gun.no_reload", "Nothing to load: it recharges itself"),
    ("hud.mymod.laser_rifle.cell", "Cell %s / %s"),
    ("hud.mymod.laser_rifle.no_cell", "No cell"),
    ("hud.mymod.energy.overheated", "OVERHEATED"),
    ("hud.mymod.railgun.cooling", "COOLING"),
    ("hud.mymod.ray_gun.charge", "Charge %s%%"),
    ("hud.mymod.smart_link.charge", "Chg"),
    ("death.attack.mymod.laser", "%1$s was burned through"),
    ("death.attack.mymod.laser.player", "%1$s was burned through by %2$s"),
    ("death.attack.mymod.laser.item", "%1$s was burned through by %2$s using %3$s"),
    ("death.attack.mymod.railgun_slug", "%1$s was railed"),
    ("death.attack.mymod.railgun_slug.player", "%1$s was railed by %2$s"),
    ("death.attack.mymod.railgun_slug.item", "%1$s was railed by %2$s using %3$s"),
    ("subtitles.mymod.laser_shot", "Laser fires"),
    ("subtitles.mymod.railgun_charge", "Railgun charges"),
    ("subtitles.mymod.railgun_shot", "Railgun fires"),
    ("subtitles.mymod.ray_zap", "Ray gun zaps"),
    ("subtitles.mymod.energy_overheat", "Energy weapon vents"),
    ("subtitles.mymod.power_cell_load", "Power cell clicks in"),
    ("subtitles.mymod.rail_slug_load", "Slugs load"),
]))
print("energy weapon assets written")
