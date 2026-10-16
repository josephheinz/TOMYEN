"""Builds the napalm weapons' assets: the M2 flamethrower, its fuel tanks, Napalm Canisters, the napalm grenade,
burning gel puddles, the Napalm effect icon, the flame and heat haze particles, and their sounds.

The flamethrower is the M2's wand: nozzle with its pilot light, the ignition cylinder, front and rear grips, the fuel
valve and a length of hose falling away toward the backpack. Held, it shows the wand (with a tongue of flame chosen
by tomyen:muzzle_flash while it sprays); in the inventory, on the ground and in item frames it shows the wand beside
its twin tanks. The tanks are their own Weapon Station part, and the same model is what FlamethrowerTanksLayer hangs
on the back of anyone holding the flamethrower, so keep TANK_TOP and TANK_FRONT in step with that layer.

Model space is in pixels: the wand's bore runs along z at x = 8, y = 9 with the nozzle toward -z, +x is its right.
GunDisplay and GunHands mirror the first-person transform and grip points printed at the end, so move them together.
With ffmpeg on the path it also synthesises the roar loop and the igniter click (napalm going off is vanilla's
explosion and fire charge, pitched down).

Usage: python3 tools/gen_napalm.py src/main/resources/assets/tomyen"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, FLASH, check, move, rotate, rx, rz, write, model, ref, range_dispatch, display, display_matrix,
                    placed, centred, bounds, bounds_centre, DIAGONAL, png, mix, FACES, EMPTY, sprite)

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

# Where long guns put the shooting hand (as in gen_shotguns.py: the M4's grip point, in the held item's space).
FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))

# ============================================================================================================ atlas
# Clean flat paint: one colour per material, no speckle. Minecraft's face shading does the rest.

K = Kit("flamethrower", "item/flamethrower", seed=1942)


def flat(colour):
    return lambda x, y, w, h: K.rgb(colour)


def ramp(top, bottom):
    """A smooth vertical ramp, lighter at the top of the swatch: for the faces whose v runs down them."""
    return lambda x, y, w, h: K.rgb(mix(top, bottom, y / max(1, h - 1)))


K.swatch("steel", 0, 0, 4, 4, flat(0x45494E))
K.swatch("steel_dark", 4, 0, 8, 4, flat(0x2B2E31))
K.swatch("olive", 8, 0, 12, 4, ramp(0x66712F, 0x4C5523))
K.swatch("olive_dark", 12, 0, 16, 4, flat(0x3D4520))
K.swatch("bakelite", 0, 4, 4, 8, ramp(0x5A3826, 0x3E2518))
K.swatch("rubber", 4, 4, 8, 8, flat(0x1E1E1E))
K.swatch("brass", 8, 4, 12, 8, flat(0xB89545))
K.swatch("red", 12, 4, 16, 8, flat(0xA82A20))
K.swatch("bore", 0, 8, 4, 12, lambda x, y, w, h: K.rgb(mix(0x050505, 0x2B2E31, max(0.0, min(1.0, (math.hypot(x - 7.5, y - 7.5) - 4.0) / 3.5)))),
         picture=True)
# The pilot light's glow: a soft blue core fading out to the edge.
K.swatch("pilot_blue", 4, 8, 8, 12, lambda x, y, w, h: K.rgb(mix(0xE0F4FF, 0x4AA0FF, min(1.0, math.hypot(x - 7.5, y - 7.5) / 8.0))))


def pilot_flame(x, y, w, h):
    """A little teardrop of flame, tip up: blue at the root, warming to orange, with a soft edge."""
    u, v = (x + 0.5) / w - 0.5, (y + 0.5) / h
    width = 0.42 * math.sin(math.pi * min(1.0, (1.0 - v) * 1.05)) ** 0.7
    edge = abs(u) / max(width, 0.01)
    if edge > 1.0:
        return (0, 0, 0, 0)
    col = mix(mix(0xFFF0B8, 0xFF8A1C, edge), 0x6AB8FF, max(0.0, (v - 0.65) / 0.35))
    return ((col >> 16) & 255, (col >> 8) & 255, col & 255, int(255 * (1.0 - edge ** 3)))


K.swatch("pilot_flame", 8, 8, 12, 12, pilot_flame, picture=True)
K.save(TEX)

# ========================================================================================================= the wand

BX, BY = 8.0, 9.0
NOZZLE = -8.0
GRIP = (8.0, 6.2, 12.6)      # the shooting hand round the rear pistol grip
SUPPORT = (8.0, 6.6, 0.9)    # the off hand round the front grip


def bar(out, z0, z1, r, mat, cx=BX, cy=BY, **kw):
    """A square-section bar along z, centred on (cx, cy)."""
    return K.box(out, cx - r, cy - r, z0, cx + r, cy + r, z1, mat, **kw)


def wand(with_hose=True):
    """The M2's wand in as few boxes as hold its shape: nozzle, ignition cylinder, tube, two grips, the valve with
    its red knob, and the pilot light glowing under the nozzle."""
    out = []
    bar(out, NOZZLE, -6.6, 0.8, "steel_dark", name="nozzle")
    K.sticker(out, BX - 0.6, BY - 0.6, NOZZLE - 0.02, BX + 0.6, BY + 0.6, NOZZLE - 0.01, "bore", "north")
    bar(out, -6.6, -2.6, 1.45, "olive", cap="olive_dark", name="ignition_cylinder", north="olive_dark", south="olive_dark")
    bar(out, -2.6, 13.0, 0.7, "steel", name="tube", north=None, south=None)
    K.box(out, 7.25, 4.2, 0.0, 8.75, 8.3, 1.7, "bakelite", rot=("x", -8, (8.0, 8.3, 0.85)), name="front_grip")
    K.box(out, 7.0, 7.9, 8.4, 9.0, 10.5, 12.0, "steel_dark", name="valve")
    K.box(out, 7.4, 10.5, 9.6, 8.6, 11.1, 10.8, "red", name="valve_knob")
    K.box(out, 7.2, 3.6, 11.6, 8.8, 7.9, 13.4, "bakelite", rot=("x", -15, (8.0, 7.9, 12.5)), name="rear_grip")
    K.box(out, 7.75, 6.6, 10.3, 8.25, 7.9, 10.8, "steel", name="trigger")
    bar(out, 13.0, 14.4, 0.95, "brass", name="coupling")
    # The pilot light: a glowing nub under the nozzle with its little flame standing up off it.
    K.box(out, BX - 0.3, 7.6, -7.7, BX + 0.3, 8.2, -7.1, "pilot_blue", emit=True, name="pilot_light")
    K.box(out, BX - 0.45, 7.7, -7.4, BX + 0.45, 8.9, -7.4, "pilot_flame", emit=True, east=None, west=None, up=None, down=None)
    if with_hose:
        hose(out)
    return out


def hose(out):
    """The fuel hose off the back of the wand, bending down toward the tanks in two runs."""
    bar(out, 14.4, 17.4, 0.6, "rubber", name="hose", north=None, south=None)
    bend = []
    bar(bend, 17.4, 21.4, 0.6, "rubber", north=None)
    rotate(bend, rx(55), (BX, BY, 17.4))
    out.extend(bend)


# ======================================================================================================== the tanks

TANK_R = 2.3
TANK_X = (5.4, 10.6)
TANK_Z = 8.0
TANK_TOP = 14.0              # the manifold's top: FlamethrowerTanksLayer puts it at the wearer's shoulders...
TANK_FRONT = 5.4             # ...and this face (the straps) against their back


def tanks():
    """Two tall olive tanks with rounded-off ends, the pressure bottle standing between them, the manifold across
    their tops and a pair of straps."""
    out = []
    for i, x in enumerate(TANK_X):
        K.box(out, x - TANK_R, 1.0, TANK_Z - TANK_R, x + TANK_R, 13.0, TANK_Z + TANK_R, "olive", up="olive_dark", down="olive_dark",
              name="fuel_tank" if i == 0 else None)
        K.box(out, x - 1.6, 0.3, TANK_Z - 1.6, x + 1.6, 1.0, TANK_Z + 1.6, "olive_dark", up=None)
        K.box(out, x - 1.6, 13.0, TANK_Z - 1.6, x + 1.6, 13.4, TANK_Z + 1.6, "olive_dark", down=None)
    K.box(out, 7.0, 2.4, 9.8, 9.0, 11.6, 11.8, "steel", name="pressure_bottle")
    K.box(out, 4.0, 13.4, 7.4, 12.0, TANK_TOP, 8.6, "steel_dark", name="manifold")
    for x0 in (4.6, 10.2):
        K.box(out, x0, 1.6, TANK_FRONT, x0 + 1.2, TANK_TOP - 0.2, TANK_FRONT + 0.4, "rubber", name="strap")
    return out


# ========================================================================================================= flames


def flame(variant):
    """A tongue of burning napalm off the nozzle: two crossed flame lobes from the muzzle flash atlas, stretched long
    and glowing. Each variant turns the cross and changes its length, so a held stream flickers."""
    out = []
    length, radius, turn = [(6.4, 1.4, 0), (7.4, 1.2, 30), (5.6, 1.6, 60)][variant]
    z0, z1 = NOZZLE - length, NOZZLE - 0.05
    FLASH.box(out, 7.99, BY - radius, z0, 8.01, BY + radius, z1, "lobe_u", emit=True, north=None, south=None, up=None, down=None)
    FLASH.box(out, 8 - radius, BY - 0.01, z0 - 0.07, 8 + radius, BY + 0.01, z1 - 0.07, "lobe_v", emit=True,
              north=None, south=None, east=None, west=None)
    return rotate(out, rz(turn), (8, BY, NOZZLE))


# ======================================================================================================= displays


def gun_displays(grip, elements, fp_scale):
    fp_rot = [0, -8, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, FP_GRIP_TARGET), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, grip, TP_GRIP_TARGET), 0.65)
    return {"thirdperson_righthand": tp, "thirdperson_lefthand": tp, "firstperson_righthand": fp, "firstperson_lefthand": fp}


def still_displays(elements, scale, rot=(0, -90, 0)):
    """In the inventory, on the ground and in item frames."""
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
    d.update({
        "thirdperson_righthand": display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8),
        "thirdperson_lefthand": display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8),
        "firstperson_righthand": display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8),
        "firstperson_lefthand": display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8),
    })
    return d


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


CREDIT = "tomyen flamethrower - generated by tools/gen_napalm.py"
HIDDEN = display([0, 0, 0], [0, 0, 0], 0)

held = wand()
check("flamethrower", held)
displays = gun_displays(GRIP, held, 0.55)
displays.update(still_displays(held, 0.4))
write(os.path.join(MODELS, "flamethrower.json"), model(K, held, displays, credit=CREDIT))

flames = []
for v in range(3):
    elements = flame(v)
    check(f"flamethrower_flame_{v}", elements)
    # Only in the hands: the flame never shows in the inventory (which shows the icon model anyway).
    flame_displays = dict(displays)
    for ctx in ("gui", "ground", "fixed"):
        flame_displays[ctx] = HIDDEN
    write(os.path.join(MODELS, f"flamethrower_flame_{v}.json"),
          model(FLASH, elements, flame_displays, credit="tomyen flamethrower flame - generated by tools/gen_napalm.py"))
    flames.append(f"flamethrower_flame_{v}")

# The icon: the wand lying in front of its tanks, which stand behind the hose.
icon = wand() + move(tanks(), dz=13.3, dy=-0.37)
check("flamethrower_icon", icon)
write(os.path.join(MODELS, "flamethrower_icon.json"), model(K, icon, still_displays(icon, 0.4), credit=CREDIT))

pack = tanks()
check("flamethrower_tanks", pack)
write(os.path.join(MODELS, "flamethrower_tanks.json"), model(K, pack, part_displays(pack, 0.85, (20, 210, 0)), credit=CREDIT))
write(os.path.join(ITEMS, "flamethrower_tanks.json"), {"model": ref("flamethrower_tanks")})
bare = wand(with_hose=False)
check("flamethrower_wand", bare)
write(os.path.join(MODELS, "flamethrower_wand.json"), model(K, bare, part_displays(bare, 0.5), credit=CREDIT))
write(os.path.join(ITEMS, "flamethrower_wand.json"), {"model": ref("flamethrower_wand")})

body = range_dispatch("tomyen:muzzle_flash",
                      [(k + 0.5, {"type": "minecraft:composite", "models": [ref("flamethrower"), ref(f)]}) for k, f in enumerate(flames)],
                      ref("flamethrower"))
write(os.path.join(ITEMS, "flamethrower.json"), {"model": {
    "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [{"when": ["gui", "ground", "fixed"], "model": ref("flamethrower_icon")}],
    "fallback": body}})

t = displays["firstperson_righthand"]
print(f"flamethrower: {len(held)} elements; firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}")
print(f"   grip {fp_point(displays, GRIP)}  support {fp_point(displays, SUPPORT)}  muzzle {fp_point(displays, (BX, BY, NOZZLE))}")

# ========================================================================================================== sprites
ITEM_TEX = os.path.join(TEX, "item")


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def palette_sprite(path, rows, pal):
    sprite(path, rows, {k: rgba(v) for k, v in pal.items()})


# The jerrycan from tools/gen_industry.py, in napalm's colours: an orange band and a flame-orange label mark.
CANISTER = [
    "................",
    "....oooo.oo.....",
    "...o....oCCo....",
    "...oooooooCo....",
    "..ohhhhhhhhoo...",
    "..ohllllllllo...",
    "..ohlxlllxllo...",
    "..obbbbbbbbbo...",
    "..obwwwwwwwbo...",
    "..obwkkkkkwbo...",
    "..obwwwwwwwbo...",
    "..obbbbbbbbbo...",
    "..ohlxlllxllo...",
    "..ommmmmmmmmo...",
    "...ooooooooo....",
    "................",
]
palette_sprite(os.path.join(ITEM_TEX, "napalm_canister.png"), CANISTER,
               {"o": 0x23272E, "h": 0xA8B0B9, "l": 0x7D8590, "m": 0x555C66, "x": 0x5E6670, "C": 0xC23B2E,
                "b": 0xD9661A, "w": 0x2A2420, "k": 0xFF9A2E})

# A canister grenade: olive body with an orange band, the spoon down its side and the pin's ring at the top.
palette_sprite(os.path.join(ITEM_TEX, "napalm_grenade.png"), [
    "................",
    "........rr......",
    ".......r..r.....",
    "......ssr.r.....",
    ".....osssrr.....",
    "....ohhhhhso....",
    "....ohlllls.o...",
    "....ohlllll.so..",
    "....obbbbbbo.s..",
    "....obkbkbbo.s..",
    "....obbbbbbo....",
    "....ohlllllo....",
    "....ohllllmo....",
    "....olllmmmo....",
    ".....oooooo.....",
    "................",
], {"o": 0x1A1D0E, "h": 0x7A8448, "l": 0x4B5320, "m": 0x343A16, "b": 0xD9661A, "k": 0x2A2420, "s": 0xB4B8BC,
    "r": 0xD8D8D8})

for name in ("napalm_canister", "napalm_grenade"):
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})

# ===================================================================================================== burning gel


def gel_frames(frames=8, size=16):
    """Burning napalm gel seen from above: dark tarry jelly with three soft pools of molten orange that drift round
    and pulse, as an animated strip (frames top to bottom). Smooth gradients only; it tiles."""
    pools = [(4.0, 5.0, 3.6, 0.0), (11.5, 10.0, 4.2, 2.1), (12.0, 2.5, 2.8, 4.2)]
    px = []
    for f in range(frames):
        phase = f / frames * math.tau
        for y in range(size):
            for x in range(size):
                glow = 0.0
                for cx, cy, r, offset in pools:
                    cx += 0.8 * math.cos(phase + offset)
                    cy += 0.8 * math.sin(phase + offset)
                    # Wrapped distance, so the pools carry on over the edges and puddles tile.
                    dx = min(abs(x + 0.5 - cx), size - abs(x + 0.5 - cx))
                    dy = min(abs(y + 0.5 - cy), size - abs(y + 0.5 - cy))
                    glow += math.exp(-(dx * dx + dy * dy) / (r * r)) * (0.8 + 0.2 * math.sin(phase * 2 + offset))
                glow = min(1.0, glow)
                col = mix(0x1E0F08, 0xE0600E, glow)
                col = mix(col, 0xFFD060, max(0.0, glow - 0.65) / 0.35)
                px.append(rgba(col))
    png(os.path.join(TEX, "block", "burning_gel.png"), size, size * frames, px)
    write(os.path.join(TEX, "block", "burning_gel.png.mcmeta"), {"animation": {"frametime": 3, "interpolate": True}})


gel_frames()
BLOCK_MODELS = os.path.join(ASSETS, "models", "block")
# A thin layer of glowing gel with short flames licking up off it (vanilla's animated fire, crossed).
write(os.path.join(BLOCK_MODELS, "burning_gel.json"), {
    "credit": "tomyen burning gel - generated by tools/gen_napalm.py",
    "ambientocclusion": False,
    "textures": {"particle": "tomyen:block/burning_gel", "gel": "tomyen:block/burning_gel", "fire": "minecraft:block/fire_0"},
    "elements": [
        {"from": [0, 0, 0], "to": [16, 1, 16], "shade": False, "light_emission": 12,
         "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#gel"}, "down": {"uv": [0, 0, 16, 16], "texture": "#gel", "cullface": "down"},
                   "north": {"uv": [0, 15, 16, 16], "texture": "#gel"}, "south": {"uv": [0, 15, 16, 16], "texture": "#gel"},
                   "east": {"uv": [0, 15, 16, 16], "texture": "#gel"}, "west": {"uv": [0, 15, 16, 16], "texture": "#gel"}}},
        {"from": [0.8, 0.5, 8], "to": [15.2, 9.5, 8], "shade": False, "light_emission": 15,
         "rotation": {"angle": 45, "axis": "y", "origin": [8, 0, 8]},
         "faces": {"north": {"uv": [0, 7, 16, 16], "texture": "#fire"}, "south": {"uv": [0, 7, 16, 16], "texture": "#fire"}}},
        {"from": [8, 0.5, 0.8], "to": [8, 9.5, 15.2], "shade": False, "light_emission": 15,
         "rotation": {"angle": 45, "axis": "y", "origin": [8, 0, 8]},
         "faces": {"east": {"uv": [0, 7, 16, 16], "texture": "#fire"}, "west": {"uv": [0, 7, 16, 16], "texture": "#fire"}}},
    ]})
write(os.path.join(ASSETS, "blockstates", "burning_gel.json"), {"variants": {"": {"model": "tomyen:block/burning_gel"}}})

# ================================================================================================ effect + particles


def effect_icon():
    """A drop of burning gel: a dark teardrop with an orange flame rising off it, 18x18 like vanilla's icons."""
    px = []
    for y in range(18):
        for x in range(18):
            u, v = x - 8.5, y - 8.5
            drop = math.hypot(u, (v - 3.0) * 1.1) < 5.0 or (v < 3.0 and abs(u) < (v + 4.0) * 0.6 and v > -3.0)
            k = (v + 8.0) / 7.0   # 0 at the flame's tip, 1 where it meets the drop
            fl = 0.0 < k <= 1.0 and abs(u + math.sin(v * 0.9) * 0.7) < 2.6 * k ** 0.7 * (1.15 - k * 0.4)
            if drop:
                edge = math.hypot(u, (v - 3.0) * 1.1) > 4.0
                col = 0x0A0604 if edge else mix(0x2A1408, 0xFF7A10, max(0.0, 0.6 - math.hypot(u + 1.5, v - 1.5) / 5.0))
                px.append(rgba(col))
            elif fl:
                px.append(rgba(mix(0xFFE070, 0xFF5A10, min(1.0, 1.0 - k * 0.8))))
            else:
                px.append((0, 0, 0, 0))
    png(os.path.join(TEX, "mob_effect", "napalm.png"), 18, 18, px)


effect_icon()
PARTICLES = os.path.join(TEX, "particle")


def flame_sprite(frame, frames=8, size=16):
    """A soft round ball of burning napalm: white-hot and small early on, swelling into an orange then dull red-black
    smoky blob as it cools. A smooth radial ramp, edges feathered out."""
    t = frame / (frames - 1)
    hot, mid, cold = 0xFFF2B0, 0xFF7A18, 0x4A1A0C
    core = mix(hot, mid, min(1.0, t * 1.8))
    rim = mix(mid, cold, t)
    radius = size * (0.3 + 0.18 * t)
    px = []
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / radius
            if d > 1.0:
                px.append((0, 0, 0, 0))
                continue
            col = mix(core, rim, d ** 1.4)
            alpha = int(255 * (1.0 - d ** 3) * (1.0 - 0.45 * t))
            px.append(rgba(col, alpha))
    png(os.path.join(PARTICLES, f"napalm_flame_{frame}.png"), size, size, px)


for f in range(8):
    flame_sprite(f)
write(os.path.join(ASSETS, "particles", "napalm_flame.json"), {"textures": [f"tomyen:napalm_flame_{f}" for f in range(8)]})


def haze_sprite(frame, frames=4, size=16):
    """Heat shimmer: a faint, wavering ring of pale light, nearly clear (Minecraft can't distort the view behind it)."""
    px = []
    for y in range(size):
        for x in range(size):
            u, v = x + 0.5 - size / 2, y + 0.5 - size / 2
            r = math.hypot(u, v) / (size / 2) + 0.08 * math.sin(math.atan2(v, u) * 5 + frame * 1.6)
            a = max(0.0, 1.0 - abs(r - 0.6) / 0.3)
            px.append(rgba(0xFFF4E0, int(42 * a)) if a > 0 else (0, 0, 0, 0))
    png(os.path.join(PARTICLES, f"heat_haze_{frame}.png"), size, size, px)


for f in range(4):
    haze_sprite(f)
write(os.path.join(ASSETS, "particles", "heat_haze.json"), {"textures": [f"tomyen:heat_haze_{f}" for f in range(4)]})

# ============================================================================================================ sounds
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
    """ffmpeg's built-in Vorbis encoder only writes stereo, which Minecraft doesn't fade with distance, so the roar's
    sound instance (FlamethrowerSound) fades itself, as the Hogfire's engine does."""
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


def roar(seconds=2.0):
    """The stream's roar, seamless when looped: a deep rushing (brown noise) under a hissing jet, both breathing
    with a slow flutter, and the odd crackle of burning gel."""
    sr = random.Random(3)
    n = int(RATE * seconds)
    brown, white = [], []
    b = 0.0
    for _ in range(n):
        b = b * 0.992 + sr.uniform(-1, 1) * 0.12
        brown.append(b)
        white.append(sr.uniform(-1, 1))
    hiss = lowpass(highpass(white, 1400), 5200)
    out = []
    for i in range(n):
        t = i / RATE
        # Whole cycles in two seconds so the flutter joins up at the loop point.
        flutter = 0.8 + 0.12 * math.sin(math.tau * 7 * t) + 0.08 * math.sin(math.tau * 13 * t + 1.0)
        crackle = sr.uniform(-1, 1) * 0.5 if sr.random() < 0.003 else 0.0
        out.append((brown[i] * 2.4 + hiss[i] * 0.55) * flutter + crackle)
    fade = int(RATE * 0.15)
    for i in range(fade):
        w = i / fade
        out[i] = out[i] * w + out[n - fade + i] * (1 - w)
    return out[:n - fade]


def ignite():
    """The igniter: a sharp spark click, then the whump of the stream catching."""
    sr = random.Random(5)
    n = int(RATE * 0.6)
    noise = [sr.uniform(-1, 1) for _ in range(n)]
    click = highpass(noise, 3000)
    body = lowpass(noise, 700)
    out = []
    for i in range(n):
        t = i / RATE
        c = click[i] * math.exp(-t / 0.004) * 1.4
        w = t - 0.06
        whump = body[i] * (min(1.0, w / 0.03) * math.exp(-w / 0.18) if w > 0 else 0.0) * 3.5
        out.append(c + whump)
    return out


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping sounds")
else:
    write_ogg("flamethrower_roar", roar(), 0.9)
    write_ogg("flamethrower_ignite", ignite(), 0.7)
print("napalm assets written")
