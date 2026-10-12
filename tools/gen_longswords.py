"""Builds the seven long swords (wooden, stone, copper, iron, golden, diamond, netherite): a 3D model for the hands and
a flat 32x32 inventory sprite, each in its material's colours (taken from the vanilla sword of that tier).

The held model is a two-hand sword: a blade three times the length of a vanilla sword's, a wide crossguard with flared
quillons and a centre boss, a long leather-wrapped grip split by a ring into two hand-widths, and an octagonal pommel.
It's far longer than the 16 pixel item box, so it's built at half scale (K model pixels per design pixel) and the
display transforms scale it back up. Design pixels match a vanilla sword's texture pixels, measured along the sword
from the crossguard (a, + toward the tip); the sword stands up the y axis at x = z = 8, its flat facing +-z.

In third person it takes the vanilla sword's pose (the model's upright blade turned onto the sprite's diagonal), held
low on the grip: the hand sits where it holds a vanilla sword, and the blade reaches three times as far past it. In
first person vanilla's pose would bring the point back into the camera (a sword's tip leans toward the viewer), so
the sword stands up and leans away instead: the grip where a vanilla sword's is, the point about half a block further
off than the grip and leaving the top of the screen right of the crosshair, the flat turned toward the viewer. The inventory, item frames, shelves and dropped items use the sprite (the item
definition picks by display context)."""
import math, os, sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import Kit, check, display, display_matrix, mix, model, placed, png, ref, write

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

K = 0.5    # model pixels per design pixel
Y0 = 4.0   # model y of the crossguard's centre (a = 0)
HAND = -7.4  # where the main hand holds the grip (a): low down, below the ring, leaving room for the other hand

# Each tier's ramp, darkest to brightest, from its vanilla sword: deep, outline, dark, mid, light, bright, highlight.
TIERS = OrderedDict([
    ("wooden", [0x20180A, 0x372910, 0x473614, 0x594319, 0x755821, 0x866526, 0xA58A4A]),
    ("stone", [0x212121, 0x494949, 0x5A5A5A, 0x787777, 0x95918D, 0xB3B1AF, 0xCFCDCB]),
    ("copper", [0x421C11, 0x5F2C1C, 0x803921, 0x9C4E31, 0xE3826C, 0xFC9982, 0xFDD4CB]),
    ("iron", [0x181818, 0x444444, 0x6B6B6B, 0x969696, 0xBEBEBE, 0xD8D8D8, 0xFFFFFF]),
    ("golden", [0x3F2E0E, 0x825D16, 0xDC9613, 0xE9B115, 0xEAEE57, 0xFDFF76, 0xFFFFFF]),
    ("diamond", [0x082520, 0x0E3F36, 0x156355, 0x1E8A77, 0x2BC7AC, 0x33EBCB, 0xA4FDF0]),
    ("netherite", [0x160814, 0x231012, 0x322727, 0x4F3C3E, 0x51444E, 0x706770, 0x867B86]),
])
# The vanilla stick's browns, wrapped as leather on the grip: deep, dark, mid, light.
LEATHER = [0x281E0B, 0x493615, 0x684E1E, 0x896727]
DEEP, OUTLINE, DARK, MID, LIGHT, BRIGHT, HI = range(7)

# ============================================================================================================ atlas


def paint(tier, ramp):
    k = Kit("sword", f"item/{tier}_long_sword", seed=40 + list(TIERS).index(tier))
    c = lambda i, jitter=0: k.rgb(ramp[i], jitter=jitter)

    def blade_face(fuller):
        # Across the flat: dark rim, a bright bevel lit from the upper left, the flat, a fuller down the middle (or
        # a plain ridge toward the point) and a darker bevel on the far side.
        cols = ([OUTLINE, HI, BRIGHT, LIGHT, LIGHT, MID, DARK, DARK, MID, LIGHT, LIGHT, MID, MID, DARK, DEEP, DEEP]
                if fuller else [OUTLINE, HI, BRIGHT, BRIGHT, LIGHT, LIGHT, MID, DARK, DARK, DEEP, DEEP, DEEP])

        def f(x, y, w, h):
            i = cols[x * len(cols) // w]
            # A faint sheen running down the blade.
            if i in (LIGHT, BRIGHT) and (y // 6) % 5 == 0:
                return k.rgb(mix(ramp[i], ramp[min(i + 1, HI)], 0.4))
            return c(i, jitter=3)
        return f

    def edge(x, y, w, h):
        return c(BRIGHT if x < w // 2 else LIGHT, jitter=2)

    def bevelled(lo, body, hi, centre=None):
        """A picture with a 1 texel rim: dark bottom/right, light top/left; centre: a 2x2 stud of that colour."""
        def f(x, y, w, h):
            if x == 0 or y == 0:
                return c(hi)
            if x == w - 1 or y == h - 1:
                return c(lo)
            if centre is not None and abs(x - (w - 1) / 2) < 1 and abs(y - (h - 1) / 2) < 1:
                return c(centre)
            return c(body, jitter=4)
        return f

    def grip(x, y, w, h):
        # Leather strip wound round the grip: diagonal bands with a dark seam between them.
        band = (x + y) % 6
        if band == 0:
            return k.rgb(LEATHER[0])
        return k.rgb(LEATHER[1 if band in (1, 5) else 2 if band in (2, 4) else 3], jitter=4)

    k.swatch("blade_face", 0, 0, 4, 16, blade_face(True), picture=True)
    k.swatch("blade_tip", 4, 0, 7, 16, blade_face(False), picture=True)
    k.swatch("blade_edge", 7, 0, 8, 16, edge, picture=True)
    k.swatch("blade_end", 8, 0, 10, 2, lambda x, y, w, h: c(MID, jitter=4))
    k.swatch("guard_side", 10, 0, 16, 4, lambda x, y, w, h: c(DARK if (x + y) % 7 == 0 else MID, jitter=5))
    k.swatch("guard_face", 8, 4, 16, 6, bevelled(OUTLINE, MID, LIGHT), picture=True)
    k.swatch("boss", 8, 6, 11, 9, bevelled(OUTLINE, LIGHT, BRIGHT, centre=HI), picture=True)
    k.swatch("grip", 8, 9, 10, 16, grip, picture=True)
    k.swatch("ring", 10, 9, 12, 11, lambda x, y, w, h: c(LIGHT if y % 4 == 0 else MID, jitter=3))
    k.swatch("pommel", 12, 9, 15, 12, bevelled(OUTLINE, MID, BRIGHT), picture=True)
    k.swatch("pommel_end", 12, 12, 15, 15, bevelled(DARK, LIGHT, BRIGHT, centre=HI), picture=True)
    return k


# ============================================================================================================ model


def y(a):
    return round(Y0 + K * a, 4)


def part(k, out, a0, a1, width, depth, mat, **faces):
    """A box centred on the sword's axis from a0 to a1 (design pixels), width across the flat, depth through it."""
    w, d = width * K / 2, depth * K / 2
    return k.box(out, 8 - w, y(a0), 8 - d, 8 + w, y(a1), 8 + d, mat, **faces)


def sword(k):
    out = []
    # Blade: 0.6 thick, stepping in toward the point, then a square turned 45 degrees for the point itself (a hair
    # thinner than the blade so their flats don't fight). The first step carries the fuller.
    for a0, a1, width, face in ((0.8, 31, 2.8, "blade_face"), (31, 35, 2.4, "blade_tip"), (35, 38, 1.9, "blade_tip"),
                                (38, 40, 1.3, "blade_tip")):
        part(k, out, a0, a1, width, 0.6, "blade_end", north=face, south=face, east="blade_edge", west="blade_edge")
    s = 1.3 * K / math.sqrt(2) / 2
    k.box(out, 8 - s, y(40) - s, 8 - 0.25 * K, 8 + s, y(40) + s, 8 + 0.25 * K, "blade_tip",
          rot=("z", 45, (8, y(40), 8)), down=None)
    # Crossguard: a wide bar, flared quillons past its ends and a boss in the middle.
    part(k, out, -0.8, 0.8, 10, 1.8, "guard_side", north="guard_face", south="guard_face")
    for side in (-1, 1):
        x0, x1 = 8 + side * 4.4 * K, 8 + side * 5.6 * K
        k.box(out, x0, y(-1.2), 8 - 1.0 * K, x1, y(1.2), 8 + 1.0 * K, "guard_side", north="guard_face", south="guard_face")
    part(k, out, -1.3, 1.6, 3.2, 2.2, "guard_side", north="boss", south="boss")
    # Grip: long enough for two hands, a ring at each end and one between the hands.
    part(k, out, -9.8, -1.3, 1.6, 1.6, "grip", up="ring", down="ring")
    for a0, a1, width in ((-2.3, -1.4, 2.0), (-5.9, -5.4, 1.9), (-9.6, -8.8, 2.0)):
        part(k, out, a0, a1, width, width, "ring")
    # Pommel: an octagon (two squares, one turned 45 degrees about the axis) with a button under it.
    part(k, out, -12.2, -9.6, 2.8, 2.8, "pommel", up="pommel_end", down="pommel_end")
    turned = part(k, out, -12.15, -9.65, 2.8, 2.8, "pommel", up="pommel_end", down="pommel_end")
    turned["rotation"] = OrderedDict([("angle", 45), ("axis", "y"), ("origin", [8, y(-11), 8])])
    turned["faces"] = turned.pop("faces")
    part(k, out, -12.8, -12.2, 1.4, 1.4, "pommel_end")
    return out


# ========================================================================================================= displays

# Vanilla's item/handheld transforms, and the middle of a sword sprite's grip (texel 2, 13) in its model space.
VANILLA = {
    "thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], 0.85),
    "firstperson_righthand": ([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
}
VANILLA_GRIP = (2.5, 2.5, 8)
# First-person rotation, found by projecting the model into the view (70 degree hand FOV, 16:9): the nearest point is
# 0.66 blocks from the camera, the point leans 0.7 blocks further away than the grip and leaves the top of the screen
# at 72% of its width, and the blade passes no nearer the crosshair than a quarter of the screen.
FP_ROTATION = [-30, -50, -10]


def held(context):
    rot, tr, sc = VANILLA[context]
    m = display_matrix(rot)
    target = [tr[i] + sum(m[i][j] * (VANILLA_GRIP[j] - 8) for j in range(3)) * sc for i in range(3)]
    # The sprite's blade runs up its diagonal, 45 degrees round from this model's upright one.
    mine = [rot[0], rot[1], rot[2] - 45]
    if context.startswith("firstperson"):
        mine = FP_ROTATION
    scale = round(sc / K, 4)
    return display(mine, [v + 0.0 for v in placed(mine, scale, (8, y(HAND), 8), target)], scale)


def displays():
    out = OrderedDict()
    for side in ("thirdperson", "firstperson"):
        # The game mirrors a left-hand transform itself (negating its x shift and its y and z turns), so both hands
        # take the same values, as the guns' do.
        out[side + "_righthand"] = out[side + "_lefthand"] = held(side + "_righthand")
    return out


# =========================================================================================================== sprite


def sprite(tier, ramp):
    """32x32, on the diagonal like a vanilla sword: pommel at the lower left, point at the upper right. s runs along
    the diagonal (0-62), d across it (each step one diagonal row of texels)."""
    W = 32
    px = [(0, 0, 0, 0)] * (W * W)
    parts = [None] * (W * W)
    rgb = lambda hx: ((hx >> 16) & 255, (hx >> 8) & 255, hx & 255, 255)

    def put(x, y_, colour, part_name):
        px[y_ * W + x] = rgb(colour)
        parts[y_ * W + x] = part_name

    for y_ in range(W):
        for x in range(W):
            s, d = x + (W - 1 - y_), x - (W - 1 - y_)
            if 22 <= s <= 60:
                width = 3 if s <= 52 else 2 if s <= 56 else 1 if s <= 59 else 0
                if abs(d) <= width:
                    shade = {-3: OUTLINE, -2: HI, -1: BRIGHT, 0: MID if s < 50 else LIGHT, 1: LIGHT, 2: MID, 3: DEEP}[d]
                    if abs(d) == width:
                        shade = OUTLINE if d <= 0 else DEEP
                    put(x, y_, ramp[shade], "blade")
            if 17 <= s <= 23 and abs(d) <= 2:
                put(x, y_, ramp[HI if (s, d) == (20, 0) else LIGHT], "guard")
            elif 18 <= s <= 21 and abs(d) <= 9 or 17 <= s <= 22 and 8 <= abs(d) <= 9:
                put(x, y_, ramp[LIGHT if s == 20 and abs(d) < 8 else MID], "guard")
            if 6 <= s <= 16 and abs(d) <= 2:
                if s in (11, 12):
                    put(x, y_, ramp[MID], "guard")
                else:
                    put(x, y_, LEATHER[1 + (s // 2 + (d + 4) // 2) % 3], "grip")
            if s <= 6 and abs(d) + abs(s - 3) <= 5:
                put(x, y_, ramp[BRIGHT if (s, d) == (3, -1) else LIGHT if d < 0 else MID], "pommel")
    # Outline every part where it meets empty space: the material's outline up and left, its deep shade down and right
    # (the leather's darkest brown on the grip).
    out = list(px)
    for y_ in range(W):
        for x in range(W):
            p = parts[y_ * W + x]
            if p is None or p == "blade":
                continue
            empty = lambda xx, yy: not (0 <= xx < W and 0 <= yy < W) or parts[yy * W + xx] is None
            if empty(x - 1, y_) or empty(x, y_ - 1):
                out[y_ * W + x] = rgb(LEATHER[0] if p == "grip" else ramp[OUTLINE])
            elif empty(x + 1, y_) or empty(x, y_ + 1):
                out[y_ * W + x] = rgb(LEATHER[0] if p == "grip" else ramp[DEEP])
    png(os.path.join(TEX, f"item/{tier}_long_sword_icon.png"), W, W, out)


# ============================================================================================================ write

DISPLAYS = displays()
FLAT = ["gui", "fixed", "ground", "head", "on_shelf"]
for tier, ramp in TIERS.items():
    name = f"{tier}_long_sword"
    kit = paint(tier, ramp)
    elements = sword(kit)
    check(name, elements)
    kit.save(TEX)
    write(os.path.join(MODELS, name + ".json"), model(kit, elements, DISPLAYS))
    sprite(tier, ramp)
    write(os.path.join(MODELS, name + "_icon.json"), OrderedDict([
        ("parent", "minecraft:item/generated"), ("textures", OrderedDict(layer0=f"mymod:item/{name}_icon"))]))
    write(os.path.join(ITEMS, name + ".json"), {"model": OrderedDict([
        ("type", "minecraft:select"), ("property", "minecraft:display_context"),
        ("cases", [OrderedDict([("when", FLAT), ("model", ref(name + "_icon"))])]),
        ("fallback", ref(name))])})

for context, t in DISPLAYS.items():
    print(f"{context}: rotation {t['rotation']} translation {t['translation']} scale {t['scale'][0]}")
print("long swords done")
