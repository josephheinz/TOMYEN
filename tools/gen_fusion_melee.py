#!/usr/bin/env python3
"""Item Fusing, batch 2 (com.jheinz.tomyen.fusion.melee): the seven melee fusion weapons' art and data.

Usage: python3 tools/gen_fusion_melee.py [--preview DIR] [--only NAME[,NAME]]

Writes, for each weapon, a 3D item model (and its texture atlas, and the items/ definition), and then the data: the
fusion files (by running tools/fusion_data.py's write_data, which also writes the first five), the repair and vanilla
item tags (swords, axes, spears and the enchantable ones), and the lang (names and tooltip lines; the riddles are
already there).

Each weapon reads as a fusion of its two inputs, built from many purposeful cubes:
* Slide Hammer (trombone + iron long sword): the long sword's hilt (steel pommel, leather grip, steel guard), the
  trombone's twin slide tubes for the haft with their stockings and braces, and a hammer head that is the trombone's
  bell (a stepped brass flare, dark throat) on one side and a squared steel striking face, cut from the sword's
  blade, on the other; the mouthpiece is the top spike, the tuning-slide loop rides the back of the head.
* Antler Pike (tusk spear + moose antler): a long ash shaft with hide grip and an antler butt cap, the tusk spear's
  bone point and red lashing at the top, and under it a moose's palmate antler mounted crosswise as a broad guard of
  tines.
* Tuskaxe (boar tusk + bloom steel axe): a hickory haft with a boar-hide grip, a bloom steel socket with hammer facets
  and rivets, a great curved tusk for the bit and a smaller tusk as the back spike, lashed on with rawhide, a tuft of
  bristle at the top.
* Moonblade (moonscale koi + fae silver sword): a fae silver blade with a koi-scale fuller, the koi's fan tail as the
  guard (white fins with blue rays, the gold patch at the root), a purple-wrapped grip and a glowing moonstone pommel in
  a silver crescent.
* Pufferblade (ember puffer + magmasteel sword): a black magmasteel blade with glowing magma veins and spines down its
  back, coming out of the mouth of a charred ember puffer that is the guard (round, spined all over, cracks glowing),
  a gold-and-black magmasteel hilt.
* Gnome Lance (garden gnome + tusk spear): a jousting lance, red-and-white striped, with a steel vamplate over the
  grip, the tusk point at the tip, and a little garden gnome riding just behind the point as its figurehead.
* Leviathan Blade (Swordfish Sword + Moonblade): a great blade of deep-sea steel with silver moon edges and a curling
  wave crest along its back, the swordfish's bill for its point, a leviathan's toothed jaws for the guard with the
  moonstone for an eye, swept fins, and a fan-tail pommel.

Swords and the axe and hammer are built blade up +y and laid on the sword diagonal like gen_fusion.py's (vanilla's
handheld transforms hold them); the spears are laid on the other diagonal, the way vanilla's spear_in_hand sprites lie,
with the grip where vanilla's spears are held. Every model is checked for z-fighting in world space. Art rules: clean
ramps, no noise."""
import json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
ONLY = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None

from gunkit import rotate, move, rz, ry, rx, write, display, mix, IDENTITY  # noqa: E402
from gunsmith_kit import Atlas, check_all, bounds  # noqa: E402
import gen_fusion  # noqa: E402  (clears sys.argv; read above)
from gen_fusion import (ASSETS, DATA, TEX, C, o, ramp_flats, gradient, mirror_u, lay_diagonal, sword_displays, item_model,  # noqa: E402
                        items_json, HANDHELD, vanilla_png)

# ============================================================================================== ramps (hi..dk)

BRASS = (0xFFF0B0, 0xF2CC5E, 0xD4A33A, 0xA87A24, 0x6E4E14)
BELL_IN = (0x6E5020, 0x58401A, 0x463214, 0x34240E, 0x22180A)
STEEL = (0xFFFFFF, 0xE8EAEE, 0xC8CCD2, 0x9EA4AC, 0x6C727A)       # the iron long sword's
LEATHER = (0xB07C4E, 0x93603A, 0x7A4C2C, 0x613A22, 0x452818)
ASH = (0xC8A060, 0xAE8648, 0x946E38, 0x76552A, 0x52391C)         # the tusk spear's shaft, lifted
BONE = (0xFFFAEC, 0xF2E6CA, 0xDECDA6, 0xBBA680, 0x8E7B5A)
ANTLER = (0xF0DEBA, 0xDCC49C, 0xC2A87E, 0x9E8660, 0x705C40)      # the moose antler's
LASH = (0xE06A48, 0xC04E30, 0x9E3C24, 0x7C2E1A, 0x561E10)        # the tusk spear's red lashing
BLOOM = (0xC2BEB8, 0xA4A09A, 0x86827C, 0x68645F, 0x4A4744)
HIDE = (0x9A7656, 0x7E5E44, 0x664A36, 0x50392A, 0x38281C)
BRISTLE = (0x5A4A3E, 0x46382E, 0x382C24, 0x2A201A, 0x1C1612)
HICKORY = (0xD2B07A, 0xBC965E, 0xA07C48, 0x806038, 0x5A4226)
FAE = (0xF8F4FF, 0xE2DCF6, 0xC0B8E2, 0x968EC0, 0x68608E)         # fae silver, its sword's lavender
FAE_GRIP = (0xA47CDA, 0x8458BA, 0x66409A, 0x4C2E78, 0x321E52)
SCALE = (0xFFFFFF, 0xEEF4FF, 0xCFDDF4, 0xA4B8DC, 0x7488B0)       # the moonscale koi's
KOI_GOLD = (0xFFE494, 0xF6C454, 0xDCA234, 0xB07E22, 0x7A5414)
MOONSTONE = (0xFFFFFF, 0xE4F2FF, 0xBCDCFF, 0x8EBCEE, 0x6494CC)
MAGSTEEL = (0x6A6460, 0x4A4542, 0x34302E, 0x242120, 0x161414)    # the magmasteel sword's black
MAGMA = (0xFFF09A, 0xFFC040, 0xF68A1C, 0xC85412, 0x84300A)
MAG_GOLD = (0xF4D07A, 0xD4A84E, 0xB08436, 0x8A6226, 0x5E4018)
PUFFER = (0x8E6656, 0x725044, 0x5A3E34, 0x442C26, 0x2C1C18)      # the ember puffer's charred hide
SPINE = (0xFFE6A0, 0xFFC864, 0xF0A040, 0xC07428, 0x844A18)
HAT = (0xF2604C, 0xD8402E, 0xB42E24, 0x8A2018, 0x5C1410)
BEARD = (0xFFFFFF, 0xF2F2F2, 0xDCDCDC, 0xBCBCBC, 0x949494)
SKIN = (0xFCD8BC, 0xF0BC9C, 0xDCA080, 0xBC8464, 0x8C5E46)
COAT = (0x6C9CEC, 0x4A7ED4, 0x3662B0, 0x284C8A, 0x1A3460)
BOOT = (0x8A603A, 0x6E4A2C, 0x583A22, 0x442C1A, 0x2E1E12)
LANCE_W = (0xFFFFFF, 0xF6F2EA, 0xE4DED2, 0xC4BCAE, 0x968E80)
LANCE_R = (0xF0604C, 0xD43C2C, 0xB02C22, 0x86201A, 0x5A1410)
DEEP = (0x9CE0E8, 0x62B2C4, 0x3E86A0, 0x2A6078, 0x183C52)        # deep-sea steel
NAVY = (0x6A72B0, 0x4A4E80, 0x34386A, 0x262848, 0x1A1C33)        # the swordfish's back
FOAM = (0xFFFFFF, 0xF0FAFF, 0xCFEAF6, 0xA2D2EA, 0x70B0D4)
TOOTH = (0xFFFFF4, 0xF2EEDC, 0xDCD4BC, 0xB8AE94, 0x8A8068)


def flats(K, **ramps):
    for name, ramp in ramps.items():
        ramp_flats(K, name, ramp)


# ============================================================================================== building helpers

def post(K, out, y0, y1, r, side, cx=8.0, cz=8.0, cap=None, caps=(True, True), name=None, emit=False):
    """A regular octagonal prism along y (the same construction as gunkit's rod, turned upright): four bands through
    the axis at 0, 90, 45 and -45 degrees about y, their ends staggered a hair so the caps don't z-fight."""
    a = 2 * r * math.tan(math.pi / 8)
    cap = cap or side
    first = None
    for k, (angle, w, d) in enumerate([(0, r, a / 2), (0, a / 2, r), (45, r, a / 2), (-45, r, a / 2)]):
        dd = 0.006 * k
        fm = dict(up=cap if caps[1] else None, down=cap if caps[0] else None)
        e = K.box(out, cx - w, y0 + dd, cz - d, cx + w, y1 - dd, cz + d, side, emit=emit,
                  rot=("y", angle, (cx, y0, cz)) if angle else None, name=(name if k == 0 else None), **fm)
        first = first or e
    return first


def rod_x(K, out, x0, x1, r, side, cy=8.0, cz=8.0, cap=None, caps=(True, True), name=None, emit=False):
    """An octagonal prism along x: gunkit's rod (along z) turned a quarter about y."""
    part = []
    K.rod(part, x0, x1, r, side, cx=cz, cy=cy, cap=cap, caps=caps, name=name, emit=emit)
    # The rod runs along z from x0 to x1 at (x=cz, y=cy); ry(90) takes (x, y, z) to (z, y, -x), then back over to z=cz.
    rotate(part, ry(90), (0.0, 0.0, 0.0))
    move(part, 0.0, 0.0, 2 * cz)
    out.extend(part)
    return part[0]


def ring_y(K, out, y0, y1, r_in, r_out, mat, cx=8.0, cz=8.0, segments=8, name=None, emit=False, **faces):
    """A flat octagonal ring round the y axis: eight boards, each turned to its side."""
    seg = 2 * r_out * math.tan(math.pi / segments) + 0.02
    first = None
    for k in range(segments):
        d = 0.004 * (k % 2)
        e = K.box(out, cx - seg / 2, y0 + d, cz + r_in, cx + seg / 2, y1 - d, cz + r_out, mat, name=(name if k == 0 else None), emit=emit,
                  **faces)
        if k:
            rotate([e], ry(360.0 / segments * k), (cx, y0, cz))
        first = first or e
    return first


def bevel_box(K, out, x0, y0, z0, x1, y1, z1, r, mat, name=None, **faces):
    """A box along z with its four long edges bevelled by r (gunkit's chamfered, with the side slabs and corner
    prisms set in from the ends a hair so no end face lies in the core's plane)."""
    ends = {k: v for k, v in faces.items() if k in ("north", "south")}
    first = K.box(out, x0 + r, y0, z0, x1 - r, y1, z1, mat, name=name, **faces)
    sides = {k: v for k, v in faces.items() if k not in ("up", "down")}
    K.box(out, x0, y0 + r, z0 + 0.01, x0 + r, y1 - r, z1 - 0.01, mat, **{**sides, "east": None})
    K.box(out, x1 - r, y0 + r, z0 + 0.01, x1, y1 - r, z1 - 0.01, mat, **{**sides, "west": None})
    h = r / math.sqrt(2)
    for cx, cy in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
        K.box(out, cx - h, cy - h, z0 + 0.02, cx + h, cy + h, z1 - 0.02, mat, rot=("z", 45, (cx, cy, z0)), north=None, south=None)
    return first


# Where vanilla's spears are held: the point of the spear_in_hand model the first person hand closes on (worked out
# from vanilla's handheld and spear_in_hand transforms, the fist being where it holds a sword's grip).
SPEAR_GRIP = (9.9, 7.1, 8.0)
SPEAR_HELD = {"firstperson_righthand": ([-20, 90, -35], [3.13, 2.0, 0.13], [1.36, 1.36, 0.68]),
              "firstperson_lefthand": ([-20, -90, 35], [3.13, 2.0, 0.13], [1.36, 1.36, 0.68]),
              "thirdperson_righthand": ([5, 270, -40], [0, 2, 2], [1.7, 1.7, 0.85]),
              "thirdperson_lefthand": ([5, -270, 40], [0, 2, 2], [1.7, 1.7, 0.85])}


def spear_displays(elements, scale=1.1):
    """Vanilla's spear_in_hand held rotations at an even scale (these are solid models, not flat sprites, and smaller
    than vanilla's 1.36 so the long ones don't fill the screen), each translated so the grip lands where vanilla's
    spear grip does; the GUI, ground and frame views fitted to the model."""
    from gunkit import display_matrix, apply
    out = sword_displays(elements)
    g = [v / 16 - 0.5 for v in SPEAR_GRIP]
    for ctx, (rot, tr, vs) in SPEAR_HELD.items():
        m = display_matrix(rot)
        k = scale * vs[0] / 1.36
        want = [a + t / 16 for a, t in zip(apply(m, [vs[i] * g[i] for i in range(3)]), tr)]
        have = apply(m, [k * g[i] for i in range(3)])
        out[ctx] = display(rot, [round((want[i] - have[i]) * 16, 3) for i in range(3)], round(k, 3))
    return out


def lay_spear(elements, grip_y):
    """Turns a spear built up +y (grip centre at (8, grip_y, 8)) onto the spear diagonal, point upper left like
    vanilla's spear_in_hand sprites, grip where vanilla's spears are held."""
    rotate(elements, rz(45), (8.0, grip_y, 8.0))
    move(elements, SPEAR_GRIP[0] - 8.0, SPEAR_GRIP[1] - grip_y, 0.0)
    return elements


def finish(K, name, out, displays, extra_items=None):
    check_all(name, out)
    K.save(TEX)
    write(os.path.join(ASSETS, "models", "item", name + ".json"), item_model(K, out, displays))
    if extra_items:
        write(os.path.join(ASSETS, "items", name + ".json"), extra_items)
    else:
        items_json(name)
    lo, hi = bounds(out)
    assert all(-16 <= v <= 32 for v in lo + hi), (name, lo, hi)
    print(f"{name}: {len(out)} cubes")
    return K, out, displays


def spear_items(name):
    """A spear's item definition: one model in every context, and vanilla's spear swap animation."""
    return o(("model", o(("type", "minecraft:model"), ("model", "tomyen:item/" + name))), ("swap_animation_scale", 1.95))


# ============================================================================================ the Slide Hammer

def slide_hammer():
    K = Atlas("slide_hammer", "item/slide_hammer", seed=21, size=64)
    flats(K, brass=BRASS, bell=BELL_IN, steel=STEEL, leather=LEATHER)
    K.flat("black", 0x14100A)
    # Brass with a polished streak: a bright line a quarter of the way across, for the tubes and the bell's bands.
    K.add("brass_tube", 8, 8, lambda i, j: C((BRASS[1], BRASS[0], BRASS[1], BRASS[2], BRASS[2], BRASS[2], BRASS[3], BRASS[3])[i]),
          picture=True)
    K.add("brass_band", 8, 8, lambda i, j: C((BRASS[0], BRASS[1], BRASS[2], BRASS[2], BRASS[2], BRASS[3], BRASS[3], BRASS[4])[j]),
          picture=True)
    # The bell's throat, dark at the middle: concentric squares.
    K.add("throat", 8, 8, lambda i, j: C(BELL_IN[4 - int(max(abs(i - 3.5), abs(j - 3.5)))]), picture=True)
    # The steel face: a bevelled square, bright rim, a darker centre ground flat by the sword-smith.
    def face(i, j):
        e = min(i, j, 7 - i, 7 - j)
        return C(STEEL[1] if e == 0 else STEEL[2] if e == 1 else STEEL[3] if (i + j) % 7 == 0 else STEEL[2])
    K.add("strike", 8, 8, face, picture=True)
    K.add("wrap", 8, 4, lambda i, j: C((LEATHER[1], LEATHER[2], LEATHER[3], LEATHER[4])[(i + j) % 4]), picture=True)
    K.add("steel_side", 16, 4, gradient([C(c) for c in (STEEL[1], STEEL[1], STEEL[2], STEEL[2], STEEL[2], STEEL[3], STEEL[3], STEEL[4])]),
          picture=True)

    out = []
    b = K.box

    # -- The long sword's hilt: a steel wheel pommel with a button, the leather grip in turns, a steel guard.
    post(K, out, -0.4, 0.4, 1.0, "steel_md", cap="steel_lt", name="pommel_button")
    post(K, out, 0.4, 1.9, 1.45, "steel_side", cap="steel_lt", name="pommel")
    post(K, out, 1.9, 2.3, 0.9, "steel_lo", cap="steel_md")
    post(K, out, 2.3, 9.0, 0.82, "wrap", cap="leather_md", name="grip")
    for y in (3.4, 5.6, 7.8):
        post(K, out, y, y + 0.35, 0.92, "leather_dk", cap="leather_lo")
    # The guard: a bar across with flared, down-turned quillon ends, and a langet up the haft.
    b(out, 4.2, 9.0, 7.35, 11.8, 9.9, 8.65, "steel_md", up="steel_lt", down="steel_lo", east="steel_lo", west="steel_lo",
      south="steel_side", north="steel_side", name="guard")
    for side in (1, -1):
        x0, x1 = (11.8, 12.9) if side > 0 else (3.1, 4.2)
        b(out, x0, 8.4, 7.2, x1, 10.2, 8.8, "steel_lt", up="steel_hi", down="steel_lo", name="quillon")
    b(out, 7.0, 9.9, 7.2, 9.0, 10.8, 8.8, "steel_lt", up="steel_hi", down="steel_lo", name="langet")

    # -- The haft: the trombone's slide, its two tubes side by side, each in a heavier stocking at the bottom where it
    # sits in the guard, joined by the slide's braces, and a water key on the lower tube's bow.
    for tx in (7.05, 8.95):
        post(K, out, 11.2, 21.6, 0.55, "brass_tube", cx=tx, cap="brass_lt", caps=(False, True), name="slide_tube")
        post(K, out, 10.8, 12.4, 0.72, "brass_band", cx=tx, cap="brass_lt", name="stocking")
        post(K, out, 19.4, 20.2, 0.72, "brass_band", cx=tx, cap="brass_lt")
    for y in (13.9, 17.3):
        b(out, 7.4, y, 7.7, 8.6, y + 0.55, 8.3, "brass_md", up="brass_lt", down="brass_lo", name="brace")
        b(out, 7.75, y - 0.1, 7.55, 8.25, y + 0.65, 8.45, "brass_lt", up="brass_hi", down="brass_lo")
    b(out, 9.5, 15.2, 7.8, 9.95, 16.6, 8.2, "brass_lo", up="brass_md", down="brass_dk", name="water_key")
    b(out, 9.95, 15.7, 7.85, 10.35, 16.1, 8.15, "steel_lt", up="steel_hi", down="steel_lo")

    head = len(out)
    # -- The head: a brass hub over the slide's top, the bell flaring out to +x, the steel striking face to -x.
    b(out, 6.1, 24.2, 6.6, 9.9, 28.4, 9.4, "brass_md", up="brass_lt", down="brass_lo", east="brass_lo", west="brass_lo",
      south="brass_band", north="brass_band", name="hub")
    for y in (24.5, 27.6):
        b(out, 5.95, y, 6.45, 10.05, y + 0.5, 9.55, "brass_lt", up="brass_hi", down="brass_lo", name="hub_band")
    # The bell: leadpipe out of the hub, then the flare in steps, each a little wider, the rim rolled.
    bell_steps = [(9.9, 11.0, 1.05), (11.0, 12.1, 1.3), (12.1, 13.0, 1.6), (13.0, 13.8, 2.0), (13.8, 14.5, 2.45),
                  (14.5, 15.0, 2.85)]
    for k, (x0, x1, r) in enumerate(bell_steps):
        rod_x(K, out, x0, x1, r, "brass_tube" if k % 2 == 0 else "brass_md", cy=26.3, cz=8.0, cap="brass_lt",
              caps=(False, k < len(bell_steps) - 1), name="bell" if k == 0 else None)
    rod_x(K, out, 15.0, 15.4, 3.15, "brass_lt", cy=26.3, cz=8.0, cap="brass_hi", caps=(True, False), name="bell_rim")
    # The throat, seen down the bell: a dark disc set just inside the rim's mouth.
    b(out, 15.38, 23.8, 5.5, 15.42, 28.8, 10.5, "throat", **{f: None for f in ("north", "south", "west", "up", "down")}, name="throat")
    # The striking face: the long sword's steel, squared off and bevelled, with a collar where it meets the hub.
    b(out, 5.2, 24.6, 7.0, 6.1, 28.0, 9.0, "steel_md", up="steel_lt", down="steel_lo", name="collar")
    bevel_box(K, out, 1.2, 24.0, 6.4, 5.2, 28.6, 9.6, 0.5, "steel_md", up="steel_lt", down="steel_lo", south="steel_side",
              north="steel_side", name="poll")
    b(out, 0.6, 24.5, 6.9, 1.2, 28.1, 9.1, "strike", up="steel_lt", down="steel_lo", south="steel_md", north="steel_md",
      name="strike_face")
    # -- The mouthpiece on top: a silver cup and shank, the hammer's spike.
    post(K, out, 28.4, 29.5, 0.45, "steel_md", cap="steel_lt", name="mouthpiece_shank")
    post(K, out, 29.5, 30.4, 0.85, "steel_lt", cap="black", name="mouthpiece_cup")
    # -- The tuning slide: a brass loop off the back of the hub.
    b(out, 7.2, 25.1, 9.4, 7.8, 27.5, 10.8, "brass_md", up="brass_lt", down="brass_lo", name="tuning_slide")
    b(out, 8.2, 25.1, 9.4, 8.8, 27.5, 10.8, "brass_md", up="brass_lt", down="brass_lo")
    b(out, 7.2, 27.5, 9.6, 8.8, 27.95, 10.8, "brass_lt", up="brass_hi", down="brass_lo")
    b(out, 7.1, 25.4, 10.8, 8.9, 27.2, 11.2, "brass_lo", up="brass_md", down="brass_dk")

    move(out[head:], 0.0, -4.0, 0.0)
    lay_diagonal(out, grip_y=5.6)
    return finish(K, "slide_hammer", out, sword_displays(out))


def arc(K, out, cx, cy, R, a0, a1, n, thick, depth, mat, z=8.0, name=None, **faces):
    """A curved horn or crescent in the xy plane: n boxes along an arc of radius R about (cx, cy) from angle a0 to a1
    (degrees), each turned to the tangent. thick(s) and depth(s) give its radial thickness and its z thickness at s
    (0 to 1 along the arc); neighbours differ in depth by a hair so their flat faces never share a plane."""
    first = None
    step = (a1 - a0) / n
    for i in range(n):
        s_ = (i + 0.5) / n
        deg = a0 + step * (i + 0.5)
        th = math.radians(deg)
        px, py = cx + R * math.cos(th), cy + R * math.sin(th)
        t = thick(s_)
        d = depth(s_) / 2 + (0.03 if i % 2 else 0.0)
        L = abs(math.radians(step)) * R * 1.22 + 0.05
        e = K.box(out, px - t / 2, py - L / 2, z - d, px + t / 2, py + L / 2, z + d, mat, rot=("z", round(deg, 3), (px, py, z)),
                  name=(name if i == 0 else None), **faces)
        first = first or e
    return first


def spike(K, out, at, length, w, mat, yaw, tilt, tip=None, name=None, off=0.0):
    """A spine standing out from `at`: built pointing up, leaned over by tilt degrees (toward +x), then turned yaw
    degrees about y; a brighter tip."""
    part = []
    x, y, z = at
    K.box(part, x - w / 2, y + off, z - w / 2, x + w / 2, y + off + length * 0.65, z + w / 2, mat, name=name)
    K.box(part, x - w * 0.3, y + off + length * 0.6, z - w * 0.3, x + w * 0.3, y + off + length, z + w * 0.3, tip or mat)
    rotate(part, rz(-tilt), (x, y, z))
    if yaw:
        rotate(part, ry(yaw), (x, y, z))
    out.extend(part)


def leaf_blade(K, out, y0, steps, core, bevel, ridge, edge_mat=None, zc=8.0, half=0.42, name="blade", ridge_end=None):
    """A blade up +y from y0 in steps (height, half-width): each a core flat, a thinner bevel strip each side, and a
    ridge down the middle. Returns the top of the last step."""
    y = y0
    edge = edge_mat or core
    for k, (h, w) in enumerate(steps):
        inner = w * 0.58
        hz = half + (0.006 if k % 2 else 0.0)
        K.box(out, 8 - inner, y, zc - hz, 8 + inner, y + h, zc + hz, core + "_md", up=core + "_lt", down=core + "_lo",
              south=core + "_flat", north=core + "_flat", name=name if k == 0 else None)
        for side in (1, -1):
            xa, xb = (8 + inner, 8 + w) if side > 0 else (8 - w, 8 - inner)
            e = K.box(out, xa, y + 0.01, zc - half * 0.62, xb, y + h - 0.01, zc + half * 0.62, bevel, up=edge + "_hi", down=core + "_lo",
                      east=edge + "_lt" if side > 0 else None, west=edge + "_lt" if side < 0 else None)
            mirror_u(e, "south" if side < 0 else "north")
        y += h
    K.box(out, 7.8, y0 + 0.05, zc - half - 0.1, 8.2, (ridge_end or y - steps[-1][0] - 0.07), zc + half + 0.1, ridge, name="ridge")
    return y


def bands(K, out, y0, y1, r, mats, step):
    """A tapering shaft of posts in alternating colours (paint stripes): runs of `step` from y0 to y1, radius r(y)."""
    y = y0
    k = 0
    while y < y1 - 1e-6:
        y2 = min(y1, y + step)
        last = y2 >= y1 - 1e-6
        post(K, out, y, y2, r((y + y2) / 2), mats[k % len(mats)], caps=(True, last), name="shaft" if k == 0 else None)
        y = y2
        k += 1


# ============================================================================================== the Antler Pike

def antler_pike():
    K = Atlas("antler_pike", "item/antler_pike", seed=22, size=64)
    flats(K, ash=ASH, bone=BONE, antler=ANTLER, lash=LASH, hide=HIDE)
    K.add("grain", 4, 16, lambda i, j: C((ASH[1], ASH[2], ASH[2], ASH[3])[i] if (j + i * 5) % 11 else ASH[3]), picture=True)
    K.add("wrap", 8, 4, lambda i, j: C((HIDE[1], HIDE[2], HIDE[3], HIDE[4])[(i + j) % 4]), picture=True)
    K.add("lashing", 8, 4, lambda i, j: C((LASH[0], LASH[1], LASH[2], LASH[3])[(i + 2 * j) % 4]), picture=True)
    # Antler: pale, with darker grooves running along it.
    K.add("antler_grain", 4, 16, lambda i, j: C((ANTLER[1], ANTLER[2], ANTLER[1], ANTLER[3])[i] if (j + i * 3) % 7 else ANTLER[3]),
          picture=True)
    K.add("bone_flat", 16, 4, gradient([C(c) for c in (BONE[0], BONE[1], BONE[1], BONE[2], BONE[2], BONE[3])]), picture=True)
    K.add("bone_bevel", 4, 16, lambda i, j: C((BONE[0], BONE[1], BONE[1], BONE[2])[i]), picture=True)
    out = []
    b = K.box

    # -- The butt: a cap cut from the antler's burr, and three stubby tines.
    post(K, out, -1.6, -0.2, 0.95, "antler_grain", cap="antler_lt", name="butt")
    post(K, out, -0.2, 0.3, 1.1, "antler_lo", cap="antler_md", name="burr")
    for k in range(3):
        spike(K, out, (8.0, -1.0, 8.0), 1.5, 0.45, "antler_md", 120 * k + 30, 140, tip="antler_hi")
    # -- The shaft: ash, a hide grip bound with thongs.
    post(K, out, 0.3, 28.0, 0.56, "grain", cap="ash_lt", name="shaft")
    post(K, out, 5.2, 11.6, 0.68, "wrap", cap="hide_md", name="grip")
    for y in (5.0, 8.3, 11.5):
        post(K, out, y, y + 0.4, 0.78, "hide_dk", cap="hide_lo")

    # -- The crown: the antler mounted crosswise under the point, lashed on with red cord above and below.
    for y in (20.2, 25.1):
        post(K, out, y, y + 0.7, 0.74, "lashing", cap="lash_lt", name="lash")
    post(K, out, 20.9, 25.1, 0.66, "antler_grain", cap="antler_lt", name="pedicle")
    for side in (1, -1):
        # The beam out from the shaft, then the palm, broad and flat, sweeping up and out.
        x0, x1 = (8.5, 11.2) if side > 0 else (4.8, 7.5)
        b(out, x0, 21.6, 7.62, x1, 22.9, 8.38, "antler_md", up="antler_lt", down="antler_lo", south="antler_grain", north="antler_grain",
          name="beam")
        # The palm: three broad plates fanned out from the beam's end, from nearly upright to nearly level, overlapping
        # into one flat, scalloped blade of antler, a tine standing off the end of each and one between.
        root = (8.0 + side * 3.0, 22.2)
        for k, (ang, ln, wd) in enumerate(((22, 4.6, 2.3), (48, 5.0, 2.4), (74, 4.2, 2.1))):
            d = 0.3 - 0.03 * k
            e = b(out, root[0] - wd / 2, root[1], 8 - d, root[0] + wd / 2, root[1] + ln, 8 + d, "antler_md", up="antler_lt", down="antler_lo",
                  east="antler_lo", west="antler_lo", south="antler_grain", north="antler_grain", name="palm" if k == 0 else None)
            rotate([e], rz(-side * ang), (root[0], root[1], 8.0))
            th = math.radians(90 - side * ang)
            for j, dx in enumerate((-0.6, 0.6)):
                # Two tines off each plate's end, either side of its middle.
                ex = root[0] + (ln - 0.3) * math.cos(th) + dx * math.sin(th)
                ey = root[1] + (ln - 0.3) * math.sin(th) - dx * math.cos(th)
                spike(K, out, (ex, ey, 8.0), 1.2 + 0.5 * ((k + j) % 2), 0.5 - 0.06 * j, "antler_md", 0, side * (ang + 6 * (j - 0.5)), tip="antler_hi",
                      name="tine" if k == j == 0 else None)
        # A brow tine down and out under the beam.
        spike(K, out, (8.0 + side * 2.6, 21.8, 8.0), 1.8, 0.5, "antler_md", 0, side * 125, tip="antler_hi")

    # -- The tusk point, as the Tusk Spear's: a bone leaf, bevelled, a ridge, out of a red-lashed socket.
    post(K, out, 25.8, 28.6, 0.62, "lashing", cap="lash_lt", caps=(False, True), name="socket")
    top = leaf_blade(K, out, 28.6, [(1.4, 1.05), (1.4, 0.95), (1.2, 0.78), (1.0, 0.6), (0.8, 0.4), (0.5, 0.2)], "bone", "bone_bevel",
                     "bone_hi", half=0.36)
    tip = b(out, 7.86, top - 0.14, 7.86, 8.14, top + 0.14, 8.14, "bone_hi", name="point")
    rotate([tip], rz(45), (8.0, top, 8.0))

    lay_spear(out, grip_y=8.4)
    return finish(K, "antler_pike", out, spear_displays(out), spear_items("antler_pike"))


# ================================================================================================= the Tuskaxe

def tuskaxe():
    K = Atlas("tuskaxe", "item/tuskaxe", seed=23, size=64)
    flats(K, wood=HICKORY, hide=HIDE, bloom=BLOOM, tusk=BONE, bristle=BRISTLE, lash=ANTLER)
    K.add("grain", 4, 16, lambda i, j: C((HICKORY[1], HICKORY[2], HICKORY[2], HICKORY[3])[i] if (j + i * 5) % 9 else HICKORY[3]),
          picture=True)
    K.add("wrap", 8, 4, lambda i, j: C((HIDE[1], HIDE[2], HIDE[3], HIDE[4])[(i + j) % 4]), picture=True)
    # Bloom steel: hammered facets, a pale line where the light catches the top of each.
    K.add("hammered", 8, 8, lambda i, j: C(BLOOM[1] if j % 4 == 0 else BLOOM[2] if (i // 3 + j // 4) % 2 else BLOOM[3]), picture=True)
    # Ivory: cream along the outer curve, yellowing toward the root side.
    K.add("ivory", 16, 4, gradient([C(c) for c in (BONE[0], BONE[0], BONE[1], BONE[1], BONE[2], BONE[2], BONE[3], BONE[4])]), picture=True)
    K.add("rawhide", 8, 4, lambda i, j: C((ANTLER[1], ANTLER[2], ANTLER[3], ANTLER[2])[(i + j) % 4]), picture=True)
    out = []
    b = K.box

    # -- The haft: a bloom steel pommel knob, a boar-hide grip in turns, hickory up to the head.
    post(K, out, -0.9, 0.5, 0.9, "bloom_md", cap="bloom_lt", name="pommel")
    post(K, out, 0.5, 22.2, 0.6, "grain", cap="wood_lt", caps=(False, True), name="haft")
    post(K, out, 0.9, 8.4, 0.72, "wrap", cap="hide_md", name="grip")
    for y in (0.7, 4.5, 8.3):
        post(K, out, y, y + 0.4, 0.82, "hide_dk", cap="hide_lo")

    # -- The socket: crude steel, hammered, wrapped round the haft's top, banded and riveted.
    b(out, 6.6, 15.8, 6.9, 9.4, 22.6, 9.1, "hammered", up="bloom_lt", down="bloom_lo", east="bloom_md", west="bloom_md", name="socket")
    for y in (15.3, 22.6):
        b(out, 6.4, y, 6.7, 9.6, y + 0.5, 9.3, "bloom_lo", up="bloom_md", down="bloom_dk", name="band")
    for x, y in ((7.3, 17.0), (8.7, 17.0), (7.3, 21.2), (8.7, 21.2)):
        for z0, z1, face in ((9.1, 9.3, "north"), (6.7, 6.9, "south")):
            b(out, x - 0.3, y - 0.3, z0, x + 0.3, y + 0.3, z1, "bloom_hi", up="bloom_lt", down="bloom_lo", **{face: None})
    # The bit: a great boar tusk, a crescent of ivory round the front, thickest at its middle where it's lashed on.
    arc(K, out, 8.6, 19.2, 4.4, -72, 74, 10, lambda s_: 0.55 + 1.75 * math.sin(math.pi * s_) ** 0.8,
        lambda s_: 1.0 + 0.4 * math.sin(math.pi * s_), "tusk_md", east="ivory", west="tusk_lo", up="tusk_lt", down="tusk_lo", south="tusk_lt",
        north="tusk_lt", name="tusk")
    for y in (17.6, 20.8):
        b(out, 9.4, y, 7.25, 11.4, y + 0.8, 8.75, "rawhide", up="lash_lt", down="lash_lo", name="lashing")
    # The back spike: a smaller tusk hooking down behind.
    arc(K, out, 6.2, 17.6, 2.6, 95, 205, 6, lambda s_: 1.1 - 0.85 * s_, lambda s_: 0.9 - 0.5 * s_, "tusk_md", east="tusk_lt", west="ivory",
        up="tusk_lt", down="tusk_lo", name="back_tusk")
    b(out, 5.6, 19.6, 7.4, 6.6, 21.0, 8.6, "rawhide", up="lash_lt", down="lash_lo", name="back_lashing")
    # A tuft of boar bristle bound on at the top.
    post(K, out, 23.1, 23.7, 0.7, "bristle_md", cap="bristle_lt", name="tuft_base")
    for k in range(5):
        spike(K, out, (8.0, 23.5, 8.0), 1.3 + 0.3 * (k % 2), 0.32, "bristle_lo", 72 * k, 28 + 6 * (k % 3), tip="bristle_dk")

    lay_diagonal(out, grip_y=4.6)
    return finish(K, "tuskaxe", out, sword_displays(out))


# ============================================================================================== the Moonblade

def moonblade():
    K = Atlas("moonblade", "item/moonblade", seed=24, size=64)
    flats(K, fae=FAE, grip=FAE_GRIP, scale=SCALE, gold=KOI_GOLD, moon=MOONSTONE)
    K.add("fae_flat", 16, 4, gradient([C(c) for c in (FAE[1], FAE[1], FAE[2], FAE[2], FAE[2], FAE[3])]), picture=True)
    K.add("fae_bevel", 4, 16, lambda i, j: C((FAE[0], FAE[1], FAE[1], FAE[2])[i]), picture=True)
    K.add("wrap", 8, 4, lambda i, j: C((FAE_GRIP[1], FAE_GRIP[2], FAE_GRIP[3], FAE_GRIP[4])[(i + j) % 4]), picture=True)
    # The koi's scales: rows of little arcs, white with a pale blue rim, offset each row.
    def scales(i, j):
        u, v = (i + (2 if (j // 3) % 2 else 0)) % 4, j % 3
        return C(SCALE[4] if v == 2 and u in (1, 2) else SCALE[3] if v == 2 else SCALE[0] if (u, v) == (1, 0) else SCALE[1])
    K.add("scales", 8, 24, scales, picture=True)
    # A fin: white, blue rays fanning out, fading toward the edge.
    def fin(i, j):
        c = SCALE[3] if (i + j // 3) % 3 == 0 else SCALE[1]
        return C(mix(c, SCALE[0], j / 20.0))
    K.add("fin", 8, 12, fin, picture=True)
    out = []
    b = K.box

    # -- The pommel: a moonstone, glowing, cradled in a fae silver crescent.
    post(K, out, -1.4, 0.3, 0.95, "moon_md", cap="moon_hi", emit=True, name="moonstone")
    arc(K, out, 8.0, -0.55, 1.45, 200, 340, 5, lambda s_: 0.35 + 0.35 * math.sin(math.pi * s_), lambda s_: 1.4, "fae_lt", up="fae_hi",
        down="fae_lo", name="crescent")
    post(K, out, 0.3, 0.9, 0.7, "fae_md", cap="fae_lt")
    # -- The grip: purple cord, silver rings.
    post(K, out, 0.9, 7.6, 0.62, "wrap", cap="grip_md", name="grip")
    for y in (1.0, 4.1, 7.1):
        post(K, out, y, y + 0.4, 0.72, "fae_md", cap="fae_lt")
    # -- The guard: the koi's fan tail, three lobes each side sweeping down and out from a gold-patched root.
    b(out, 6.5, 7.6, 7.15, 9.5, 9.2, 8.85, "scale_md", up="scale_lt", down="scale_lo", south="scales", north="scales", name="tail_root")
    b(out, 7.2, 8.0, 7.1, 8.8, 8.9, 7.15, "gold_md", **{f: None for f in ("north", "east", "west", "up", "down")}, name="gold_patch")
    b(out, 7.2, 8.0, 8.85, 8.8, 8.9, 8.9, "gold_md", **{f: None for f in ("south", "east", "west", "up", "down")})
    for side in (1, -1):
        for k, (ang, ln, wd) in enumerate(((22, 3.6, 1.7), (52, 3.0, 1.4), (82, 2.2, 1.1))):
            x = 8 + side * 1.3
            d = 0.16 + 0.02 * k
            e = b(out, x - wd / 2, 8.4, 8 - d, x + wd / 2, 8.4 + ln, 8 + d, "fin", up="scale_hi", down="scale_lo", east="scale_md", west="scale_md",
                  name="fin" if k == 0 else None)
            rotate([e], rz(-side * (90 + ang)), (x, 8.4, 8.0))
    # A ricasso of scales between the tail and the blade, a little crescent moon set in each face.
    b(out, 6.9, 9.2, 7.3, 9.1, 10.6, 8.7, "scale_md", up="scale_lt", down="scale_lo", south="scales", north="scales", name="ricasso")
    for zf, face in ((7.3, "south"), (8.7, "north")):
        keep = {f: None for f in ("north", "south", "east", "west", "up", "down") if f != face}
        for k, (x, y) in enumerate(((7.55, 9.75), (7.8, 10.2), (8.25, 10.3), (7.8, 9.35), (8.25, 9.25))):
            dz = 0.04 + 0.004 * k
            z0, z1 = (zf - dz, zf) if face == "south" else (zf, zf + dz)
            b(out, x - 0.18, y - 0.18, z0, x + 0.18, y + 0.18, z1, "moon_hi", emit=True, name="moon" if k == 0 else None, **keep)
    # -- The blade: fae silver, a koi-scale fuller inlaid down both faces.
    top = leaf_blade(K, out, 10.6, [(3.6, 1.25), (3.4, 1.18), (3.0, 1.08), (2.6, 0.95), (2.2, 0.78), (1.6, 0.55), (1.0, 0.3)], "fae",
                     "fae_bevel", "fae_hi", half=0.38, ridge_end=24.4)
    for z0, z1, face in ((7.58, 7.62, "south"), (8.38, 8.42, "north")):
        b(out, 7.45, 11.0, z0, 8.55, 23.6, z1, "scales", **{f: None for f in ("north", "south", "east", "west", "up", "down") if f != face},
          name="fuller")
    tip = b(out, 7.86, top - 0.14, 7.86, 8.14, top + 0.14, 8.14, "fae_hi", name="point")
    rotate([tip], rz(45), (8.0, top, 8.0))

    lay_diagonal(out, grip_y=4.2)
    return finish(K, "moonblade", out, sword_displays(out))


# ============================================================================================ the Pufferblade

def pufferblade():
    K = Atlas("pufferblade", "item/pufferblade", seed=25, size=64)
    flats(K, steel=MAGSTEEL, magma=MAGMA, gold=MAG_GOLD, puff=PUFFER, spine=SPINE)
    K.flat("white", 0xFFF4E0)
    K.flat("black", 0x100808)
    K.add("steel_flat", 16, 4, gradient([C(c) for c in (MAGSTEEL[1], MAGSTEEL[2], MAGSTEEL[2], MAGSTEEL[3], MAGSTEEL[3], MAGSTEEL[4])]),
          picture=True)
    K.add("steel_bevel", 4, 16, lambda i, j: C((MAGSTEEL[0], MAGSTEEL[1], MAGSTEEL[2], MAGSTEEL[2])[i]), picture=True)
    K.add("wrap", 8, 4, lambda i, j: C((MAG_GOLD[1], MAG_GOLD[2], MAGSTEEL[2], MAGSTEEL[3])[(i + j) % 4]), picture=True)
    # The puffer's hide: charred, with a glowing crack wandering across it.
    def hide(i, j):
        crack = (i == (j // 2 + 3) % 8 and j % 6 < 5) or (j == 5 and 1 <= i <= 4)
        return C(MAGMA[2] if crack else PUFFER[2] if (i + j) % 5 else PUFFER[3])
    K.add("hide", 8, 8, hide, picture=True)
    # The blade's vein: a hot line in a cooler glow.
    K.add("vein", 4, 16, lambda i, j: C((MAGMA[3], MAGMA[1], MAGMA[0], MAGMA[2])[i] if (j % 7) else MAGMA[3]), picture=True)
    out = []
    b = K.box

    # -- The hilt: magmasteel's gold-and-black, an ember set in the pommel.
    post(K, out, -1.0, 0.5, 0.95, "gold_md", cap="gold_lt", name="pommel")
    post(K, out, -1.5, -1.0, 0.5, "magma_md", cap="magma_hi", emit=True, name="pommel_ember")
    post(K, out, 0.5, 6.8, 0.6, "wrap", cap="gold_md", name="grip")
    for y in (0.6, 3.5, 6.3):
        post(K, out, y, y + 0.4, 0.7, "steel_lo", cap="steel_md")

    # -- The guard: the ember puffer, blown up round, its mouth opening up to let the blade out.
    cy = 9.3
    b(out, 5.5, cy - 2.2, 5.6, 10.5, cy + 2.2, 10.4, "hide", up="puff_lt", down="puff_lo", name="puffer")
    b(out, 5.1, cy - 1.6, 5.85, 10.9, cy + 1.6, 10.15, "hide", up="puff_lt", down="puff_lo")
    b(out, 5.95, cy - 2.6, 5.95, 10.05, cy + 2.6, 10.05, "hide", up="puff_lt", down="puff_lo")
    b(out, 6.05, cy - 1.7, 5.2, 9.95, cy + 1.7, 10.8, "hide", up="puff_lt", down="puff_lo")
    # Its eyes, one each side, and a pursed, glowing mouth on top.
    for side in (1, -1):
        z0, z1 = (10.8, 10.95) if side > 0 else (5.05, 5.2)
        open_face = "north" if side > 0 else "south"
        b(out, 8.9, cy + 0.2, z0, 9.9, cy + 1.2, z1, "white", **{open_face: None}, name="eye")
        z0, z1 = (10.95, 11.02) if side > 0 else (4.98, 5.05)
        b(out, 9.3, cy + 0.4, z0, 9.8, cy + 0.9, z1, "black", **{open_face: None})
    post(K, out, cy + 2.6, cy + 3.1, 1.25, "magma_lo", cap="magma_md", emit=True, name="mouth")
    # Fins.
    for side in (1, -1):
        e = b(out, 7.6 + side * 3.0, cy - 0.4, 7.9, 8.4 + side * 3.0, cy + 0.9, 8.1, "spine_lo", up="spine_md", down="spine_dk", name="fin")
        rotate([e], rz(-side * 40), (8 + side * 3.0, cy - 0.4, 8.0))
    # Spines all over: a ring round its middle and a ring above and below, staggered.
    for k in range(8):
        spike(K, out, (8.0, cy, 8.0), 2.5, 0.42, "spine_md", 45 * k + 22.5, 90, tip="spine_hi", name="spine" if k == 0 else None, off=2.2)
    for k in range(6):
        spike(K, out, (8.0, cy + 0.6, 8.0), 2.2, 0.38, "spine_md", 60 * k, 45, tip="spine_hi", off=2.0)
        spike(K, out, (8.0, cy - 0.6, 8.0), 2.2, 0.38, "spine_md", 60 * k + 30, 135, tip="spine_hi", off=2.0)
    # Cracks glowing through the hide on its broad faces.
    for z0, z1, face in ((5.16, 5.2, "south"), (10.8, 10.84, "north")):
        keep = {f: None for f in ("north", "south", "east", "west", "up", "down") if f != face}
        lo = face == "south"
        b(out, 6.4, cy - 1.0, z0, 6.7, cy + 1.2, z1, "magma_lt", emit=True, name="crack", **keep)
        b(out, 6.7, cy - 1.0, z0 - (0.004 if lo else 0), 7.9, cy - 0.7, z1 + (0 if lo else 0.004), "magma_md", emit=True, **keep)
        b(out, 7.9, cy - 1.6, z0 - (0.008 if lo else 0), 8.2, cy - 0.7, z1 + (0 if lo else 0.008), "magma_lt", emit=True, **keep)

    # -- The blade: black magmasteel with magma edges, a vein of magma down each face, spines along its back edge.
    top = leaf_blade(K, out, cy + 3.1, [(3.6, 1.2), (3.6, 1.12), (3.2, 1.02), (2.8, 0.9), (2.2, 0.72), (1.6, 0.5), (1.0, 0.26)], "steel",
                     "steel_bevel", "steel_lo", edge_mat="magma", half=0.4, ridge_end=cy + 18.0)
    for z0, z1, face in ((7.56, 7.6, "south"), (8.4, 8.44, "north")):
        b(out, 7.65, cy + 3.4, z0, 8.35, cy + 17.6, z1, "vein", emit=True,
          **{f: None for f in ("north", "south", "east", "west", "up", "down") if f != face}, name="vein")
    for k in range(5):
        y = cy + 4.6 + 2.6 * k
        w = (1.2, 1.12, 1.02, 0.9, 0.72)[k]
        e = b(out, 8 - w - 0.35, y, 7.82, 8 - w + 0.35, y + 0.7, 8.18, "spine_md", up="spine_hi", down="spine_lo",
              name="back_spine" if k == 0 else None)
        rotate([e], rz(35), (8 - w, y, 8.0))
    tip = b(out, 7.86, top - 0.14, 7.86, 8.14, top + 0.14, 8.14, "steel_lt", name="point")
    rotate([tip], rz(45), (8.0, top, 8.0))

    lay_diagonal(out, grip_y=3.6)
    return finish(K, "pufferblade", out, sword_displays(out))


# ============================================================================================== the Gnome Lance

GNOME_FACES = {"up": "east", "down": "west", "south": "up", "north": "down", "east": "south", "west": "north"}


def gnome_box(K, out, origin, g0, g1, mat, name=None, **faces):
    """A box of the figurehead gnome, given in the gnome's own frame (X right, Y up, Z forward) and set on the lance
    (local frame: the shaft up +y): the gnome's up is the lance's +x, its forward the lance's +y (toward the point),
    its right the lance's +z."""
    ox, oy, oz = origin
    mapped = {GNOME_FACES[k]: v for k, v in faces.items()}
    return K.box(out, ox + g0[1], oy + g0[2], oz + g0[0], ox + g1[1], oy + g1[2], oz + g1[0], mat, name=name, **mapped)


def gnome_lance():
    K = Atlas("gnome_lance", "item/gnome_lance", seed=26, size=64)
    flats(K, white=LANCE_W, red=LANCE_R, steel=STEEL, leather=LEATHER, bone=BONE, hat=HAT, beard=BEARD, skin=SKIN, coat=COAT, boot=BOOT)
    K.flat("black", 0x141018)
    K.flat("buckle", 0xF2C84A)
    K.add("wrap", 8, 4, lambda i, j: C((LEATHER[1], LEATHER[2], LEATHER[3], LEATHER[4])[(i + j) % 4]), picture=True)
    K.add("bone_flat", 16, 4, gradient([C(c) for c in (BONE[0], BONE[1], BONE[1], BONE[2], BONE[2], BONE[3])]), picture=True)
    K.add("bone_bevel", 4, 16, lambda i, j: C((BONE[0], BONE[1], BONE[1], BONE[2])[i]), picture=True)
    K.add("steel_band", 8, 8, lambda i, j: C((STEEL[0], STEEL[1], STEEL[2], STEEL[2], STEEL[2], STEEL[3], STEEL[3], STEEL[4])[j]), picture=True)
    out = []
    b = K.box

    # -- The butt and grip: a steel cap, a leather grip with a rondel behind the hand.
    post(K, out, -0.6, 0.4, 0.85, "steel_md", cap="steel_lt", name="butt")
    post(K, out, 0.4, 5.6, 0.62, "wrap", cap="leather_md", name="grip")
    post(K, out, 0.8, 1.2, 1.25, "steel_lo", cap="steel_md", name="rondel")
    # -- The vamplate: a steel cone over the hand, its rim rolled, opening back toward the grip.
    for k, (y0, y1, r) in enumerate(((5.6, 6.2, 2.6), (6.2, 6.8, 2.15), (6.8, 7.4, 1.7), (7.4, 8.0, 1.3))):
        post(K, out, y0, y1, r, "steel_band", cap="steel_lt" if k else "steel_lo", name="vamplate" if k == 0 else None)
    ring_y(K, out, 5.3, 5.7, 2.35, 2.85, "steel_lt", up="steel_hi", down="steel_lo", name="vamplate_rim")
    # -- The lance: tapering, painted in red and white bands, to a steel collar.
    bands(K, out, 8.0, 25.6, lambda y: 1.05 - (y - 8.0) / (25.6 - 8.0) * 0.6, ["red_md", "white_md"], 2.2)
    post(K, out, 25.6, 26.4, 0.62, "steel_md", cap="steel_lt", name="collar")
    # -- The point: the tusk spear's bone leaf.
    top = leaf_blade(K, out, 26.4, [(1.2, 0.85), (1.2, 0.75), (1.0, 0.6), (0.8, 0.42), (0.5, 0.22)], "bone", "bone_bevel", "bone_hi", half=0.32)
    tip = b(out, 7.88, top - 0.12, 7.88, 8.12, top + 0.12, 8.12, "bone_hi", name="point")
    rotate([tip], rz(45), (8.0, top, 8.0))

    # -- The figurehead: a garden gnome astride the lance just behind the point, facing along it, hat up.
    r_here = 1.05 - (21.0 - 8.0) / (25.6 - 8.0) * 0.6
    g = (8.0 + r_here - 0.05, 21.0, 8.0)

    def gb(g0, g1, mat, name=None, **f):
        return gnome_box(K, out, g, g0, g1, mat, name=name, **f)

    for side in (1, -1):
        # Legs down either side of the shaft, boots forward.
        gb((side * 0.62 - 0.3, -1.3, -0.35), (side * 0.62 + 0.3, 0.2, 0.35), "coat_lo", up="coat_md", name="leg" if side > 0 else None)
        gb((side * 0.62 - 0.34, -1.8, -0.4), (side * 0.62 + 0.34, -1.3, 0.75), "boot_md", up="boot_lt", down="boot_dk")
    gb((-0.9, 0.0, -0.65), (0.9, 1.7, 0.65), "coat_md", up="coat_lt", down="coat_lo", name="coat")
    gb((-0.95, 0.55, -0.7), (0.95, 0.85, 0.7), "black", name="belt")
    gb((-0.25, 0.5, 0.7), (0.25, 0.9, 0.78), "buckle", north=None)
    for side in (1, -1):
        # Arms reaching forward, hands on the shaft ahead of it.
        xa, xb = sorted((side * 0.9, side * 1.3))
        gb((xa, 0.75, -0.25), (xb, 1.45, 0.75), "coat_md", up="coat_lt", down="coat_lo")
        xa, xb = sorted((side * 0.75, side * 1.25))
        gb((xa, 0.15, 0.75), (xb, 0.85, 1.15), "skin_md", up="skin_lt", down="skin_lo")
    gb((-0.75, 1.7, -0.6), (0.75, 2.9, 0.6), "skin_md", up="skin_lt", down="skin_lo", name="head")
    gb((-0.2, 2.05, 0.6), (0.2, 2.45, 1.02), "skin_lo", up="skin_md", north=None, name="nose")
    for side in (1, -1):
        gb((side * 0.42 - 0.1, 2.45, 0.6), (side * 0.42 + 0.1, 2.65, 0.63), "black", north=None)
    gb((-0.8, 1.15, 0.45), (0.8, 2.1, 0.95), "beard_md", up="beard_lt", down="beard_lo", south="beard_lt", name="beard")
    gb((-0.45, 0.75, 0.55), (0.45, 1.15, 0.9), "beard_lo", up="beard_md")
    gb((-0.85, 2.1, 0.95), (0.85, 2.3, 1.05), "beard_hi", down="beard_md", name="moustache")
    for k, (y0, y1, hw, lean) in enumerate(((2.85, 3.45, 0.92, 0.0), (3.45, 4.0, 0.7, -0.12), (4.0, 4.5, 0.48, -0.26), (4.5, 4.95, 0.27, -0.42))):
        gb((-hw, y0, -hw + lean), (hw, y1, hw + lean), "hat_md", up="hat_lt", down="hat_lo", north="hat_lo", name="hat" if k == 0 else None)
    gb((-0.12, 4.95, -0.62), (0.12, 5.25, -0.38), "hat_lt", up="hat_hi")

    lay_spear(out, grip_y=3.0)
    return finish(K, "gnome_lance", out, spear_displays(out), spear_items("gnome_lance"))


# ========================================================================================= the Leviathan Blade

def leviathan_blade():
    K = Atlas("leviathan_blade", "item/leviathan_blade", seed=27, size=64)
    flats(K, deep=DEEP, navy=NAVY, foam=FOAM, tooth=TOOTH, moon=MOONSTONE, silver=FAE)
    K.add("deep_flat", 16, 4, gradient([C(c) for c in (DEEP[1], DEEP[1], DEEP[2], DEEP[2], DEEP[3], DEEP[3], DEEP[4])]), picture=True)
    K.add("silver_bevel", 4, 16, lambda i, j: C((FAE[0], FAE[0], FAE[1], FAE[2])[i]), picture=True)
    K.add("cord", 8, 4, lambda i, j: C((NAVY[1], NAVY[2], NAVY[3], NAVY[4])[(i + j) % 4]), picture=True)
    # The tide inlay: deep water with white-crested waves rolling along it.
    def tide(i, j):
        crest = (j % 8) in (0, 1) and (i + j) % 4 < 2
        return C(FOAM[1] if crest else DEEP[0] if (j % 8) == 2 else DEEP[1] if i in (1, 2) else DEEP[2])
    K.add("tide", 4, 24, tide, picture=True)
    def finpic(i, j):
        base = NAVY[2] if j % 4 < 2 else NAVY[3]
        return C(mix(base, NAVY[4], max(0.0, (i - 6) / 12)))
    K.add("fin", 16, 8, finpic, picture=True)
    out = []
    b = K.box

    # -- The pommel: a fan tail (the swordfish's and the koi's in one), navy lobes with silver tips.
    post(K, out, -0.2, 1.2, 0.85, "navy_md", cap="navy_lt", name="tail_stalk")
    for side in (1, -1):
        lobe = b(out, 7.2, -3.2, 7.74 + 0.02 * side, 8.8, 0.9, 8.26 - 0.02 * side, "fin", up="navy_lt", down="navy_dk", east="navy_lo",
                 west="navy_lo", name="tail")
        rotate([lobe], rz(side * 38), (8.0, 0.8, 8.0))
        tip = b(out, 7.4, -4.0, 7.78 + 0.02 * side, 8.6, -2.9, 8.22 - 0.02 * side, "silver_lt", up="silver_hi", down="silver_lo")
        rotate([tip], rz(side * 44), (8.0, 0.8, 8.0))
    # -- The grip: navy cord, silver rings.
    post(K, out, 1.2, 8.4, 0.66, "cord", cap="navy_md", name="grip")
    for y in (1.3, 4.6, 7.9):
        post(K, out, y, y + 0.4, 0.78, "silver_md", cap="silver_lt")

    # -- The guard: a leviathan's head, jaws gaping up round the blade's root, a moonstone eye each side.
    b(out, 5.4, 8.4, 6.6, 10.6, 10.8, 9.4, "navy_md", up="navy_lt", down="navy_lo", east="navy_lo", west="navy_lo", name="skull")
    b(out, 5.8, 10.8, 6.9, 10.2, 11.5, 9.1, "navy_lo", up="navy_md", down="navy_dk")
    for side in (1, -1):
        # A jaw each side curving up and in, a row of teeth along its inner edge.
        a0, a1 = (10, 62) if side > 0 else (170, 118)
        arc(K, out, 8.0, 10.6, 3.5, a0, a1, 5, lambda s_: 1.5 - 0.7 * s_, lambda s_: 2.2 - 0.5 * s_, "navy_md", up="navy_lt", down="navy_lo",
            name="jaw")
        for k in range(3):
            deg = (16 + 16 * k) if side > 0 else (164 - 16 * k)
            th = math.radians(deg)
            px, py = 8.0 + 2.55 * math.cos(th), 10.6 + 2.55 * math.sin(th)
            e = b(out, px - 0.5, py - 0.18, 7.45 + 0.01 * k, px + 0.5, py + 0.18, 8.55 - 0.01 * k, "tooth_lt", up="tooth_hi", down="tooth_lo",
                  name="tooth" if k == 0 else None)
            rotate([e], rz(deg), (px, py, 8.0))
        z0, z1 = (9.4, 9.5) if side > 0 else (6.5, 6.6)
        x0 = 8.6 if side > 0 else 6.4
        b(out, x0, 9.2, z0, x0 + 1.0, 10.2, z1, "moon_hi", emit=True, **{("north" if side > 0 else "south"): None}, name="eye")
        # Swept fins off the back of the head, the swordfish's sail.
        for k, (ang, ln, wd) in enumerate(((62, 5.0, 1.7), (88, 3.8, 1.3))):
            x = 8 + side * 2.4
            d = 0.2 - 0.03 * k
            e = b(out, x - wd / 2, 9.0, 8 - d, x + wd / 2, 9.0 + ln, 8 + d, "fin", up="navy_lt", down="navy_dk", east="navy_lo", west="navy_lo",
                  name="sail" if k == 0 else None)
            rotate([e], rz(-side * ang), (x, 9.0, 8.0))

    # -- The blade: deep-sea steel, silver moon edges, a tide inlay down each face.
    y0 = 11.5
    top = leaf_blade(K, out, y0, [(4.6, 1.9), (4.4, 1.84), (4.0, 1.7), (3.4, 1.5), (2.6, 1.2), (1.8, 0.85)], "deep", "silver_bevel",
                     "deep_hi", edge_mat="silver", half=0.46, ridge_end=y0 + 20.4)
    for z0, z1, face in ((7.5, 7.54, "south"), (8.46, 8.5, "north")):
        b(out, 7.3, y0 + 0.5, z0, 8.7, y0 + 19.6, z1, "tide", **{f: None for f in ("north", "south", "east", "west", "up", "down") if f != face},
          name="tide")
    # The wave crest: curls of foam breaking off the back edge, each a little smaller toward the point.
    for k in range(5):
        y = y0 + 2.2 + 3.8 * k
        w = (1.9, 1.84, 1.7, 1.5, 1.2)[k]
        sz = 1.0 - 0.1 * k
        arc(K, out, 8 + w + 0.95 * sz, y + 1.1 * sz, 1.25 * sz, 250, 10, 5, lambda s_: 0.95 - 0.5 * s_, lambda s_: 0.7 - 0.25 * s_, "foam_md",
            up="foam_hi", down="foam_lo", east="foam_lt", west="foam_md", name="crest" if k == 0 else None)
    # The point: the swordfish's bill, long and narrow, out of a silver collar.
    b(out, 7.35, top, 7.72, 8.65, top + 0.5, 8.28, "silver_md", up="silver_lt", down="silver_lo", name="bill_collar")
    post(K, out, top + 0.5, top + 3.0, 0.36, "navy_md", cap="navy_lt", caps=(False, True), name="bill")
    post(K, out, top + 3.0, top + 4.4, 0.22, "silver_lt", cap="silver_hi", caps=(False, True))

    lay_diagonal(out, grip_y=4.8)
    return finish(K, "leviathan_blade", out, sword_displays(out))


def vanilla_spear_cells():
    """Vanilla's iron spear as its in-hand sprite's texels, held the way the spears here are, for comparing."""
    from gunsmith_kit import render, first_person
    w, h, px = vanilla_png("item/iron_spear_in_hand.png")
    els = []
    t = 16.0 / w
    for y in range(h):
        for x in range(w):
            c = px[y * w + x]
            if c[3] < 128:
                continue
            els.append({"from": [x * t, 16 - (y + 1) * t, 7.75], "to": [(x + 1) * t, 16 - y * t, 8.25],
                        "faces": {f: {"uv": [x * t, y * t, (x + 1) * t, (y + 1) * t], "texture": "#v"}
                                  for f in ("north", "south", "east", "west", "up", "down")}})
    disp = spear_displays(els, scale=1.36)
    cells = []
    for fov in (70, 110):
        project, turn = first_person(disp, 480, 300, fov=fov)
        fp = render(els, {"#v": (px, w)}, project, turn, 480, 300)
        cells.append(([(110, 130, 160) if v is None else v for v in fp], 480, 300, 1))
    return cells


BUILDERS = OrderedDict([("slide_hammer", slide_hammer), ("antler_pike", antler_pike), ("tuskaxe", tuskaxe), ("moonblade", moonblade),
                        ("pufferblade", pufferblade), ("gnome_lance", gnome_lance), ("leviathan_blade", leviathan_blade)])


# ======================================================================================================== data

LANG = o(
    ("item.tomyen.slide_hammer", "Slide Hammer"),
    ("item.tomyen.slide_hammer.charge", "Hold use to wind up a slam; the slide climbs the scale"),
    ("item.tomyen.slide_hammer.slam", "Release: a shockwave that grows the longer you held"),
    ("item.tomyen.slide_hammer.shields", "Knocks shields aside"),
    ("item.tomyen.antler_pike", "Antler Pike"),
    ("item.tomyen.antler_pike.reach", "Long reach"),
    ("item.tomyen.antler_pike.charge", "Jab while sprinting: charge like a boar, goring and flinging what you meet"),
    ("item.tomyen.tuskaxe", "Tuskaxe"),
    ("item.tomyen.tuskaxe.fell", "Sprint at a tree and chop: the whole tree comes down"),
    ("item.tomyen.tuskaxe.wild", "Only wild trees, never your log cabin"),
    ("item.tomyen.moonblade", "Moonblade"),
    ("item.tomyen.moonblade.night", "Stronger at night, and stronger again under a full moon"),
    ("item.tomyen.moonblade.wave", "At night, charged swings loose a crescent of moonlight"),
    ("item.tomyen.pufferblade", "Pufferblade"),
    ("item.tomyen.pufferblade.burn", "Sets what it hits alight"),
    ("item.tomyen.pufferblade.puff", "Charged hits may puff the target up until it bursts into flame"),
    ("item.tomyen.gnome_lance", "Gnome Lance"),
    ("item.tomyen.gnome_lance.speed", "Hits harder the faster you're moving"),
    ("item.tomyen.gnome_lance.mounts", "On foot, on horseback, on a moose, in a Bathtub Boat"),
    ("item.tomyen.gnome_lance.unhorse", "Knocks riders off their mounts"),
    ("item.tomyen.gnome_lance.joust", "Joust! +%s damage"),
    ("item.tomyen.leviathan_blade", "Leviathan Blade"),
    ("item.tomyen.leviathan_blade.wet", "+4 damage in water or rain; swim fast with it in hand"),
    ("item.tomyen.leviathan_blade.lunge", "Use in water: lunge where you look"),
    ("item.tomyen.leviathan_blade.wave", "Charged swings loose a crescent wave, moonlit by night"),
    ("item.tomyen.leviathan_blade.surge", "On land, hold use and release: a tidal surge all around you"),
)

REPAIRS = {"slide_hammer": ["minecraft:gold_ingot"], "antler_pike": ["tomyen:moose_antler"],
           "tuskaxe": ["tomyen:boar_tusk", "tomyen:bloom_steel_ingot"], "moonblade": ["tomyen:fae_silver_ingot"],
           "pufferblade": ["tomyen:magmasteel_ingot"], "gnome_lance": ["minecraft:iron_ingot"],
           "leviathan_blade": ["tomyen:star_steel_ingot"]}


def write_data():
    import fusion_data
    fusion_data.write_data(DATA, ASSETS)
    tag = fusion_data.merge_tag
    for name, items in REPAIRS.items():
        tag(DATA, f"tomyen/tags/item/repairs_{name}.json", items)
    tag(DATA, "minecraft/tags/item/swords.json", ["tomyen:moonblade", "tomyen:pufferblade", "tomyen:leviathan_blade"])
    tag(DATA, "minecraft/tags/item/axes.json", ["tomyen:tuskaxe"])
    tag(DATA, "minecraft/tags/item/spears.json", ["tomyen:antler_pike", "tomyen:gnome_lance"])
    # The hammer is none of those: it takes the weapon enchantments (Sharpness, Smite and the rest, Fire Aspect) directly.
    tag(DATA, "minecraft/tags/item/enchantable/sharp_weapon.json", ["tomyen:slide_hammer"])
    tag(DATA, "minecraft/tags/item/enchantable/fire_aspect.json", ["tomyen:slide_hammer"])
    tag(DATA, "minecraft/tags/item/enchantable/durability.json", ["tomyen:slide_hammer"])
    path = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(path, encoding="utf-8"), object_pairs_hook=OrderedDict)
    lang.update(LANG)
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")


# ======================================================================================================== main

if __name__ == "__main__":
    if ONLY is None:
        write_data()
    built = OrderedDict()
    for name, fn in BUILDERS.items():
        if ONLY is None or name in ONLY:
            built[name] = fn()
    if PREVIEW:
        import preview_fusion
        preview_fusion.items(built, PREVIEW, name="melee")
        from gunsmith_kit import compose
        compose([preview_fusion.vanilla_sword_cells(vanilla_png) + vanilla_spear_cells()], os.path.join(PREVIEW, "vanilla_fp.png"))
