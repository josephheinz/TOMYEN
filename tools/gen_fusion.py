#!/usr/bin/env python3
"""Item Fusing art and data (com.jheinz.tomyen.fusion): the first five fusion items, the Fusion Reactor, and the fusion
data files.

Usage: python3 tools/gen_fusion.py [--preview DIR]

Writes:
* Swordfish Sword: a 3D item model. A swordfish whose bill became a diamond blade: its forked tail is the pommel,
  its narrow tail stalk (cord-wrapped) the grip, its dorsal sail and anal fin sweep out as the crossguard with the
  pectoral fins below, then the head with its eye and gill plate, and the bill, a faceted diamond blade with bevelled
  edges and a bright centre ridge, running out of a diamond collar to the point. Navy back, silver belly.
* Fish Fish Fish: a 3D item model. Three Fish Fish, each biting the tail of the next and bigger one: the little one
  is the grip, the biggest the club's head.
  Both are built along a local axis (blade up +y), then laid on the diagonal like a vanilla sword sprite (pommel lower
  left, point upper right) with the grip where a vanilla sword's is, so vanilla's handheld transforms hold them the
  same way; the GUI view is scaled to fit.
* Rocket Boots: the worn rockets (RocketNozzleModel's cubes, written into the Java between the markers) and their
  texture; the item sprite (the Big Red Boots' own, with the bottles and nozzles drawn on the heels).
* Murder Wings and the Stormcrow Rig: the wings textures (vanilla's elytra layout and shading, recoloured: glossy crow
  black with a blue-violet sheen and feather rows; storm slate with cyan lightning veins) and item sprites (and the
  broken ones).
* The Fusion Reactor: block models (idle and lit), blockstate, item, and the GUI texture.
* Data: the five fusion files, the reactor's recipe, advancement and loot table, item tags, the equipment assets,
  the lang (including the riddles of every approved fusion).

Every model is checked for z-fighting in world space (gunsmith_kit.check_all). Art rules: clean ramps, no noise."""
import json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import rotate, move, rz, ry, rx, write, display, mix, png, IDENTITY  # noqa: E402
from gunsmith_kit import Atlas, check_all, bounds  # noqa: E402
from gen_longswords import read_png, client_jar  # noqa: E402

REPO = os.path.dirname(HERE)
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tomyen")
DATA = os.path.join(REPO, "src", "main", "resources", "data")
TEX = os.path.join(ASSETS, "textures")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
sys.argv[1:] = []  # client_jar() reads argv[2]
import zipfile  # noqa: E402
JAR = zipfile.ZipFile(client_jar())


def vanilla_png(path):
    return read_png(JAR.read("assets/minecraft/textures/" + path))


def C(hx, a=255):
    return ((hx >> 16) & 255, (hx >> 8) & 255, hx & 255, a)


def o(*items):
    return OrderedDict(items)


# ============================================================================================== ramps (hi..dk)

NAVY = (0x6A72B0, 0x4A4E80, 0x34386A, 0x262848, 0x1A1C33)        # the swordfish's back (its sprite's own)
SILVER = (0xE8EBF2, 0xC4CADC, 0xA8ACC0, 0x7A84B0, 0x5A5E78)      # its belly
DIAMOND = (0xD5FFF6, 0xA1FBE8, 0x4AEDD9, 0x20C5B5, 0x11727A)     # vanilla's diamond
LEATHER = (0xB07C4E, 0x93603A, 0x7A4C2C, 0x613A22, 0x452818)
FISH = (0x8CC6EE, 0x4F96D6, 0x3B7BBD, 0x2C66A6, 0x1D3A5C)        # Fish Fish blue (its sprite's own)
BELLY = (0xF4F8FC, 0xD8ECF8, 0xA8CFE8, 0x8CB8D8, 0x6A98BC)
FIN = (0x7FB8E6, 0x4A8CD0, 0x2C66A6, 0x1F4E86, 0x163A66)


def ramp_flats(K, name, ramp):
    for tone, hx in zip(("hi", "lt", "md", "lo", "dk"), ramp):
        K.flat(f"{name}_{tone}", hx)


def gradient(cols):
    """A picture swatch function: columns stepping through cols left to right."""
    return lambda i, j, n=len(cols): cols[min(n - 1, i * n // 16)]


def mirror_u(e, face):
    uv = e["faces"][face]["uv"]
    e["faces"][face]["uv"] = [uv[2], uv[1], uv[0], uv[3]]


# ====================================================================================== laying a sword on the diagonal

# Vanilla's item/handheld transforms (models/item/handheld.json) and item/generated's ground/head/fixed.
HANDHELD = o(
    ("thirdperson_righthand", display([0, -90, 55], [0, 4.0, 0.5], 0.85)),
    ("thirdperson_lefthand", display([0, 90, -55], [0, 4.0, 0.5], 0.85)),
    ("firstperson_righthand", display([0, -90, 25], [1.13, 3.2, 1.13], 0.68)),
    ("firstperson_lefthand", display([0, 90, -25], [1.13, 3.2, 1.13], 0.68)),
)
VANILLA_GRIP = (3.5, 3.5, 8.0)   # the middle of a vanilla sword sprite's grip, model pixels


def lay_diagonal(elements, grip_y):
    """Turns a sword built up +y (grip centre at (8, grip_y, 8)) onto the sprite diagonal, grip where vanilla's is."""
    rotate(elements, rz(-45), (8.0, grip_y, 8.0))
    move(elements, VANILLA_GRIP[0] - 8.0, VANILLA_GRIP[1] - grip_y, 0.0)
    return elements


def sword_displays(elements):
    """Vanilla's hands; the GUI, ground and frame views scaled to fit the long model and centred on it."""
    lo, hi = bounds(elements)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    out = o(*HANDHELD.items())
    s = round(16.0 / span, 3)
    out["gui"] = display([0, 0, 0], [round(-(c[0] - 8) * s, 3), round(-(c[1] - 8) * s, 3), 0], s)
    out["ground"] = display([0, 0, 0], [round(-(c[0] - 8) * s * 0.5, 3), round(2 - (c[1] - 8) * s * 0.5, 3), 0], round(s * 0.5, 3))
    out["fixed"] = display([0, 180, 0], [round((c[0] - 8) * s, 3), round(-(c[1] - 8) * s, 3), 0], s)
    return out


def item_model(K, elements, displays):
    m = o(("credit", f"tomyen {K.key} - generated by tools/gen_fusion.py"), ("texture_size", [K.size, K.size]),
          ("gui_light", "front"),
          ("textures", o((K.key, "tomyen:" + K.texture), ("particle", "tomyen:" + K.texture))),
          ("display", displays), ("elements", elements))
    return m


def items_json(name, model=None):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": model or o(("type", "minecraft:model"), ("model", "tomyen:item/" + name))})


# ============================================================================================ the Swordfish Sword

def swordfish_sword():
    K = Atlas("swordfish_sword", "item/swordfish_sword", seed=11, size=64)
    for name, ramp in (("navy", NAVY), ("silver", SILVER), ("dia", DIAMOND), ("leather", LEATHER)):
        ramp_flats(K, name, ramp)
    K.flat("black", 0x0A0A12)
    K.flat("white", 0xF4F6FA)
    # Countershading across the body, belly (left, low x) to back: silver into navy.
    K.add("countershade", 16, 4, gradient([C(c) for c in (SILVER[1], SILVER[2], SILVER[3], NAVY[0], NAVY[1], NAVY[2], NAVY[3], NAVY[4])]),
          picture=True)
    # Fins: rays running out along u, alternating tones, darkening toward the tips.
    def fin(i, j):
        base = NAVY[2] if j % 4 < 2 else NAVY[3]
        return C(mix(base, NAVY[4], max(0.0, (i - 6) / 12)))
    K.add("fin", 16, 8, fin, picture=True)
    def tail(i, j):
        base = NAVY[1] if j % 4 < 2 else NAVY[2]
        return C(mix(base, NAVY[4], max(0.0, (j - 2) / 16)))
    K.add("tail", 8, 16, tail, picture=True)
    # The cord wrap: tight turns across the grip.
    K.add("cord", 16, 4, lambda i, j: C((LEATHER[1], LEATHER[2], LEATHER[3])[i % 3]), picture=False)
    # The blade's flat: the bevel line bright along the edge side, mid diamond across.
    K.add("bevel", 4, 16, lambda i, j: C((DIAMOND[0], DIAMOND[1], DIAMOND[1], DIAMOND[2])[i]), picture=True)

    out = []
    b = K.box

    def body(x0, y0, z0, x1, y1, z1, name=None):
        e = b(out, x0, y0, z0, x1, y1, z1, "navy_md", name=name, south="countershade", north="countershade",
              east="navy_lo", west="silver_lt", up="navy_lt", down="silver_lo")
        mirror_u(e, "north")
        return e

    # -- The tail fin (the pommel): the stalk's root and a crescent fork spreading across the sword.
    body(7.25, 0.6, 7.45, 8.75, 2.2, 8.55, name="peduncle")
    for side in (1, -1):
        lobe = b(out, 7.15, -3.6, 7.72 + 0.02 * side, 8.85, 1.4, 8.28 - 0.02 * side, "navy_lo", south="tail", north="tail", up="navy_lt", down="navy_dk",
                 east="navy_lo", west="navy_lo")
        rotate([lobe], rz(side * 40), (8.0, 1.2, 8.0))
        # The crescent's tip, broader and swept further out.
        tip = b(out, 7.0, -4.6, 7.76 + 0.02 * side, 9.0, -2.6, 8.24 - 0.02 * side, "navy_dk", south="tail", north="tail",
                up="navy_md", down="navy_dk")
        rotate([tip], rz(side * 52), (8.0, 1.2, 8.0))
    # Keels each side of the stalk.
    b(out, 6.9, 0.9, 7.8, 9.1, 1.7, 8.2, "silver_md", up="silver_lt", down="silver_lo", name="keel")

    # -- The grip: the narrow tail stalk, cord-wrapped in three places, a finlet on its back.
    body(7.1, 2.2, 7.15, 8.9, 10.0, 8.85, name="grip")
    for y in (3.2, 5.6, 8.0):
        e = b(out, 6.95, y, 7.0, 9.05, y + 1.1, 9.0, "cord", up="leather_lt", down="leather_dk", name="wrap")
    b(out, 8.9, 4.4, 7.9, 9.5, 5.2, 8.1, "navy_dk", up="navy_md", name="finlet")
    b(out, 8.9, 6.8, 7.9, 9.4, 7.4, 8.1, "navy_dk", up="navy_md")

    # -- The body's thick shoulders, and the crossguard: the dorsal sail out over the back, the anal fin over the belly,
    # both swept back toward the hand; the pectoral fins out to the sides beneath.
    body(6.3, 10.0, 6.85, 9.7, 13.2, 9.15, name="shoulders")
    body(6.6, 9.4, 7.1, 9.4, 10.1, 8.9)
    # Each fin a crescent: a broad root, then narrower pieces, each swept back a little more.
    for sign in (1, -1):
        root_x = 9.4 if sign > 0 else 6.6
        pieces = [(0.0, 3.0, 10.3, 13.1, -16), (2.7, 5.0, 10.5, 12.3, -26), (4.8, 6.6, 10.7, 11.6, -36)]
        if sign < 0:
            pieces = [(a * 0.85, c * 0.85, y0 + 0.2, y1 - 0.2, ang) for a, c, y0, y1, ang in pieces]
        for k, (a0, a1, y0, y1, ang) in enumerate(pieces):
            x0, x1 = sorted((root_x + sign * a0, root_x + sign * a1))
            th = 0.32 - 0.04 * k
            e = b(out, x0, y0, 8 - th, x1, y1, 8 + th, "navy_md", south="fin", north="fin", up="navy_lt", down="navy_dk",
                  east="navy_dk" if sign > 0 else "silver_md", west="navy_md" if sign > 0 else "navy_dk",
                  name=("dorsal" if sign > 0 else "anal") if k == 0 else None)
            if sign < 0:
                mirror_u(e, "south")
            else:
                mirror_u(e, "north")
            rotate([e], rz(sign * ang), (root_x, 11.7, 8.0))
    for side in (1, -1):
        z0, z1 = (9.1, 12.2) if side > 0 else (3.8, 6.9)
        pec = b(out, 6.9, 10.5, z0, 7.7, 12.5, z1, "navy_lo", up="navy_md", down="navy_dk", east="fin", west="fin",
                south="navy_dk", north="navy_dk", name="pectoral")
        rotate([pec], rx(side * 28), (7.3, 12.5, 9.1 if side > 0 else 6.9))

    # -- The head, tapering: eye, gill plate.
    body(6.55, 13.2, 7.0, 9.45, 15.2, 9.0, name="head")
    body(6.85, 15.2, 7.2, 9.15, 16.6, 8.8)
    body(7.15, 16.6, 7.4, 8.85, 17.6, 8.6, name="snout")
    for side in (1, -1):
        z0, z1 = (9.0, 9.12) if side > 0 else (6.88, 7.0)
        g = b(out, 6.75, 13.5, z0, 9.25, 14.0, z1, "navy_dk", **{("north" if side > 0 else "south"): None}, name="gill")
        z0, z1 = (8.8, 8.96) if side > 0 else (7.04, 7.2)
        b(out, 8.05, 15.35, z0, 8.95, 16.25, z1, "white", **{("north" if side > 0 else "south"): None}, name="eye")
        z0, z1 = (8.96, 9.04) if side > 0 else (6.96, 7.04)
        b(out, 8.3, 15.55, z0, 8.8, 16.05, z1, "black", **{("north" if side > 0 else "south"): None}, name="pupil")

    # -- The bill: a diamond collar, then the blade in steps, each a core flat with a bevel strip each side (thinner,
    # brighter) and a ridge down the middle, to a point.
    b(out, 6.7, 17.3, 7.25, 9.3, 18.2, 8.75, "dia_lt", up="dia_hi", down="dia_lo", east="dia_md", west="dia_md", name="collar")
    b(out, 6.9, 18.2, 7.5, 9.1, 18.6, 8.5, "dia_md", up="dia_lt", down="dia_lo")
    steps = [(18.6, 22.0, 1.9), (22.0, 25.0, 1.66), (25.0, 27.6, 1.4), (27.6, 29.8, 1.12), (29.8, 31.4, 0.84), (31.4, 32.6, 0.56),
             (32.6, 33.4, 0.3)]
    for k, (y0, y1, w) in enumerate(steps):
        inner = w * 0.55
        b(out, 8 - inner, y0, 7.55, 8 + inner, y1, 8.45, "dia_md", up="dia_lt", down="dia_lo", east="dia_md", west="dia_md",
          name="blade")
        for side in (1, -1):
            xa, xb = (8 + inner, 8 + w) if side > 0 else (8 - w, 8 - inner)
            e = b(out, xa, y0 + 0.01, 7.68, xb, y1 - 0.01, 8.32, "bevel", up="dia_hi", down="dia_lo",
                  east="dia_lo" if side > 0 else None, west="dia_lo" if side < 0 else None)
            if side < 0:
                mirror_u(e, "south")
            else:
                mirror_u(e, "north")
    b(out, 7.78, 18.65, 7.42, 8.22, 32.4, 8.58, "dia_hi", up="dia_hi", down="dia_lt", east="dia_lt", west="dia_lt", name="ridge")
    point = b(out, 7.79, 33.19, 7.7, 8.21, 33.61, 8.3, "dia_hi", name="point")
    rotate([point], rz(45), (8.0, 33.4, 8.0))

    lay_diagonal(out, grip_y=6.1)
    check_all("swordfish_sword", out)
    K.save(TEX)
    displays = sword_displays(out)
    write(os.path.join(ASSETS, "models", "item", "swordfish_sword.json"), item_model(K, out, displays))
    items_json("swordfish_sword")
    print(f"swordfish_sword: {len(out)} cubes")
    return K, out, displays


# ============================================================================================= Fish Fish Fish

def fish_fish_fish():
    K = Atlas("fish_fish_fish", "item/fish_fish_fish", seed=12, size=64)
    for name, ramp in (("fish", FISH), ("belly", BELLY), ("fin", FIN)):
        ramp_flats(K, name, ramp)
    K.flat("black", 0x0F1720)
    K.flat("white", 0xF4F8FC)
    K.flat("mouth", 0x132840)
    K.add("side", 16, 4, gradient([C(c) for c in (BELLY[1], BELLY[2], FISH[0], FISH[1], FISH[1], FISH[2], FISH[2], FISH[3])]),
          picture=True)
    def finpic(i, j):
        return C(FIN[1] if j % 4 < 2 else FIN[2]) if i < 12 else C(FIN[2] if j % 4 < 2 else FIN[3])
    K.add("fin", 16, 8, finpic, picture=True)
    def tailpic(i, j):
        return C(mix(FIN[0] if i % 4 < 2 else FIN[1], FIN[3], max(0.0, (j - 4) / 14)))
    K.add("tail", 8, 16, tailpic, picture=True)
    out = []
    b = K.box

    def side_box(x0, y0, z0, x1, y1, z1, name=None):
        e = b(out, x0, y0, z0, x1, y1, z1, "fish_md", south="side", north="side", east="fish_lo", west="belly_lo", up="fish_lt",
              down="belly_dk", name=name)
        mirror_u(e, "north")
        return e

    def fish(y_tail, y_head, hh, ht, name):
        """A Fish Fish along +y, head up: back toward +x. hh: half its height (across x), ht: half its thickness."""
        L = y_head - y_tail
        # The tail fan, then the stalk, the round body (a long core and a shorter, deeper middle), the head's taper.
        for side in (1, -1):
            lobe = b(out, 8 - hh * 0.32, y_tail - L * 0.02, 8 - ht * 0.22 + 0.02 * side, 8 + hh * 0.32, y_tail + L * 0.26,
                     8 + ht * 0.22 - 0.02 * side, "fin_md",
                     south="tail", north="tail", up="fin_lt", down="fin_dk", east="fin_lo", west="fin_lo")
            rotate([lobe], rz(side * 30), (8.0, y_tail + L * 0.22, 8.0))
        side_box(8 - hh * 0.3, y_tail + L * 0.18, 8 - ht * 0.5, 8 + hh * 0.3, y_tail + L * 0.34, 8 + ht * 0.5)
        side_box(8 - hh * 0.72, y_tail + L * 0.3, 8 - ht, 8 + hh * 0.72, y_head - L * 0.14, 8 + ht, name=name)
        side_box(8 - hh, y_tail + L * 0.42, 8 - ht * 0.82, 8 + hh, y_head - L * 0.28, 8 + ht * 0.82)
        side_box(8 - hh * 0.52, y_head - L * 0.14, 8 - ht * 0.72, 8 + hh * 0.52, y_head, 8 + ht * 0.72)
        # Fins: one on the back, one under the belly.
        b(out, 8 + hh, y_tail + L * 0.48, 8 - 0.12, 8 + hh * 1.45, y_head - L * 0.36, 8 + 0.12, "fin_md", south="fin", north="fin",
          up="fin_lt", down="fin_dk", east="fin_lo")
        b(out, 8 - hh * 1.35, y_tail + L * 0.5, 8 - 0.12, 8 - hh, y_head - L * 0.42, 8 + 0.12, "fin_md", south="fin", north="fin",
          up="fin_lt", down="fin_dk", west="fin_lo")
        # Eyes and the mouth.
        ey = y_head - L * 0.2
        for side in (1, -1):
            face = "north" if side > 0 else "south"
            zt = 8 + side * ht
            z0, z1 = sorted((zt - side * 0.05, zt + side * 0.12))
            r = max(0.45, hh * 0.24)
            b(out, 8 + hh * 0.08, ey - r, z0, 8 + hh * 0.08 + 2 * r, ey + r, z1, "white", **{face: None})
            z0, z1 = sorted((zt + side * 0.12, zt + side * 0.18))
            b(out, 8 + hh * 0.08 + r * 0.6, ey - r * 0.45, z0, 8 + hh * 0.08 + r * 1.5, ey + r * 0.45, z1, "black", **{face: None})
        b(out, 8 - hh * 0.32, y_head - 0.02, 8 - ht * 0.3, 8 - hh * 0.02, y_head + 0.12, 8 + ht * 0.3, "mouth")

    # The grip fish (head up, biting the middle one's tail), the middle one, and the big one, the club's head.
    fish(0.4, 9.0, 1.05, 0.95, "grip_fish")
    fish(8.6, 17.4, 1.9, 1.35, "middle_fish")
    fish(16.9, 30.0, 3.4, 2.1, "club_fish")
    lay_diagonal(out, grip_y=5.2)
    check_all("fish_fish_fish", out)
    K.save(TEX)
    displays = sword_displays(out)
    write(os.path.join(ASSETS, "models", "item", "fish_fish_fish.json"), item_model(K, out, displays))
    items_json("fish_fish_fish")
    print(f"fish_fish_fish: {len(out)} cubes")
    return K, out, displays


# ========================================================================================== the Fusion Reactor

GUNMETAL = (0x7A8094, 0x5A6072, 0x464B5B, 0x363A47, 0x24272F)
STARSTEEL = (0xE6F0FF, 0xBFD0EA, 0x96A9C8, 0x74849F, 0x535F75)
VOID = (0xB98AF0, 0x8A5CC8, 0x6A3FA8, 0x4E2C82, 0x341C5A)
CORE_ON = (0xFFFFFF, 0xD8FFFF, 0x8AF6FF, 0x40D8F0, 0x1EA6C8)
CORE_OFF = (0x7FD8E8, 0x4FB2CC, 0x2E88A6, 0x1F6684, 0x154A62)


def reactor_kit(lit):
    K = Atlas("reactor", "block/fusion_reactor" + ("_on" if lit else ""), seed=13, size=64)
    core = CORE_ON if lit else CORE_OFF
    for name, ramp in (("gun", GUNMETAL), ("star", STARSTEEL), ("void", VOID), ("core", core)):
        ramp_flats(K, name, ramp)
    # Plates: a bright top edge line, then the face, a darker foot.
    K.add("plate", 16, 16, lambda i, j: C(GUNMETAL[1] if j == 0 else GUNMETAL[4] if j == 15 else GUNMETAL[2] if (i // 8 + j // 8) % 2 else
                                          mix(GUNMETAL[2], GUNMETAL[3], 0.35)))
    K.add("pillar", 8, 16, lambda i, j: C(GUNMETAL[1] if i == 0 else GUNMETAL[3] if i == 7 else GUNMETAL[2]))
    K.add("band", 16, 4, lambda i, j: C((STARSTEEL[0], STARSTEEL[1], STARSTEEL[2], STARSTEEL[3])[j]))
    # The glow strips: a hot centre line in a softer glow.
    K.add("glow", 4, 16, lambda i, j: C((core[2], core[0], core[1], core[3])[i]), picture=True)
    K.add("glow_h", 16, 4, lambda i, j: C((core[2], core[0], core[1], core[3])[j]), picture=True)
    # The front readout: a dark screen with three cyan bars and a void-purple tick.
    def screen(i, j):
        if i in (0, 15) or j in (0, 7):
            return C(GUNMETAL[3])
        if j in (2, 3) and 2 <= i < 2 + (9 if lit else 4):
            return C(core[1])
        if j == 5 and 2 <= i < 13:
            return C(core[3] if (i // 2) % 2 else GUNMETAL[4])
        if 13 <= i <= 13 and 2 <= j <= 3:
            return C(VOID[1])
        return C(0x10141C)
    K.add("screen", 16, 8, screen, picture=True)
    def vent(i, j):
        return C(GUNMETAL[4] if j % 3 == 1 else GUNMETAL[2] if j % 3 == 0 else GUNMETAL[3])
    K.add("vent", 16, 12, vent, picture=True)
    return K


def reactor_model(lit):
    K = reactor_kit(lit)
    out = []
    b = K.box

    # -- The plinth: a heavy base, a star steel step, vents on its sides and a readout on the front.
    b(out, 0, 0, 0, 16, 2, 16, "plate", up="gun_md", down="gun_dk", name="plinth")
    b(out, 1, 2, 1, 15, 3, 15, "band", up="star_lo", down="star_dk", name="step")
    b(out, 4.5, 0.4, -0.05, 11.5, 1.65, 0.0, "screen", emit=lit, **{f: None for f in ("south", "east", "west", "up", "down")},
      name="readout")
    for x0, x1, z0, z1, face in ((16.0, 16.05, 4, 12, "east"), (-0.05, 0.0, 4, 12, "west"), (4, 12, 16.0, 16.05, "south")):
        b(out, x0, 0.35, z0, x1, 1.65, z1, "vent", **{f: None for f in ("north", "south", "east", "west", "up", "down") if f != face})
    # The dais under the core, with a glowing seam round it.
    b(out, 4, 3, 4, 12, 4, 12, "gun_lo", up="gun_md", name="dais")
    b(out, 4.5, 4, 4.5, 11.5, 4.3, 11.5, "glow_h", up="core_md", emit=True, name="dais_glow")

    # -- Four pillars, each with two star steel bands and glowing conduit strips on its two inner faces.
    for cx, cz in ((1.75, 1.75), (14.25, 1.75), (1.75, 14.25), (14.25, 14.25)):
        b(out, cx - 1.25, 3, cz - 1.25, cx + 1.25, 13, cz + 1.25, "pillar", up="gun_md", down="gun_dk", name="pillar")
        for y in (4.6, 10.8):
            b(out, cx - 1.45, y, cz - 1.45, cx + 1.45, y + 0.7, cz + 1.45, "band", up="star_hi", down="star_dk")
        sx = 1 if cx < 8 else -1
        sz = 1 if cz < 8 else -1
        x = cx + sx * 1.25
        b(out, min(x, x + sx * 0.12), 5.5, cz - 0.35, max(x, x + sx * 0.12), 10.6, cz + 0.35, "glow", emit=True,
          **{("west" if sx > 0 else "east"): None})
        z = cz + sz * 1.25
        b(out, cx - 0.35, 5.5, min(z, z + sz * 0.12), cx + 0.35, 10.6, max(z, z + sz * 0.12), "glow", emit=True,
          **{("north" if sz > 0 else "south"): None})

    # -- The cap: a frame of four bars on the pillars, a plate inside it, and the emitter crown hanging over the core.
    b(out, 0, 13, 0, 16, 15, 3, "plate", up="gun_md", down="gun_lo", name="cap")
    b(out, 0, 13, 13, 16, 15, 16, "plate", up="gun_md", down="gun_lo")
    b(out, 0, 13, 3, 3, 15, 13, "plate", up="gun_md", down="gun_lo")
    b(out, 13, 13, 3, 16, 15, 13, "plate", up="gun_md", down="gun_lo")
    b(out, 3, 13.4, 3, 13, 14.6, 13, "gun_lo", up="gun_md", down="gun_dk")
    b(out, 4, 14.6, 4, 12, 15.4, 12, "band", up="star_lt", down="star_dk", name="top_ring")
    b(out, 6, 15.4, 6, 10, 16, 10, "void_md", up="void_lt", down="void_dk", east="void_lo", name="top_cell")
    b(out, 6, 12.2, 6, 10, 13.4, 10, "star_md", up="star_lt", down="star_lo", name="crown")
    b(out, 7, 11.2, 7, 9, 12.2, 9, "core_lt", emit=True, name="emitter")
    b(out, 7, 3.8, 7, 9, 4.6, 9, "core_lt", emit=True, name="receiver")

    # -- The containment rings: one level round the core, one upright across it, each an octagon of star steel with
    # a glowing inner face.
    def ring(r, thick, axis, lowered):
        seg = 2 * r * math.tan(math.pi / 8) + 0.02
        h = thick / 2
        for k in range(8):
            d = 0.05 if k % 2 else 0.0
            if axis == "y":
                e = b(out, 8 - seg / 2, 8 - h + d, 8 - r - h, 8 + seg / 2, 8 + h - d, 8 - r + h, "star_md", up="star_lt", down="star_lo",
                      north="star_md", south="glow_h", east="star_lo", west="star_lo", name="ring_level" if k == 0 else None)
                e["faces"]["south"]["texture"] = "#" + K.key
                rotate([e], ry(45 * k), (8.0, 8.0, 8.0))
            else:
                e = b(out, 8 - seg / 2, 8 + r - h, 8 - h + d, 8 + seg / 2, 8 + r + h, 8 + h - d, "star_md", up="star_lt", down="glow_h",
                      north="star_lo", south="star_md", east="star_lo", west="star_lo", name="ring_upright" if k == 0 else None)
                rotate([e], rz(45 * k), (8.0, 8.0, 8.0))
        return out

    ring(4.5, 1.0, "y", False)
    ring(3.6, 0.9, "z", False)

    # -- The core: a glowing ball of crossed blocks.
    b(out, 6.5, 6.5, 6.5, 9.5, 9.5, 9.5, "core_md", up="core_hi", emit=True, name="core")
    b(out, 7.0, 6.0, 7.0, 9.0, 10.0, 9.0, "core_lt", emit=True)
    b(out, 6.0, 7.0, 7.02, 10.0, 9.0, 8.98, "core_lt", emit=True)
    b(out, 7.03, 7.03, 6.0, 8.97, 8.97, 10.0, "core_lt", emit=True)

    # -- Conduits from each pillar to the dais: a casing with a glowing line on top and a coupler at each end.
    for k in range(4):
        e1 = b(out, 7.6, 4.3, 0.9, 8.4, 5.1, 4.6, "gun_md", up="gun_lt", down="gun_dk", name="conduit" if k == 0 else None)
        e2 = b(out, 7.85, 5.1, 1.0, 8.15, 5.25, 4.5, "core_md", up="core_lt", emit=True)
        e3 = b(out, 7.45, 4.15, 3.6, 8.55, 5.3, 4.3, "star_md", up="star_lt", down="star_lo")
        rotate([e1, e2, e3], ry(45 + 90 * k), (8.0, 8.0, 8.0))

    check_all("fusion_reactor" + ("_on" if lit else ""), out)
    K.save(TEX)
    model = o(("credit", "tomyen fusion reactor - generated by tools/gen_fusion.py"), ("texture_size", [K.size, K.size]),
              ("ambientocclusion", False),
              ("textures", o((K.key, "tomyen:" + K.texture), ("particle", "tomyen:" + K.texture))),
              ("display", o(("gui", display([30, 225, 0], [0, 0, 0], 0.625)), ("ground", display([0, 0, 0], [0, 3, 0], 0.25)),
                            ("fixed", display([0, 0, 0], [0, 0, 0], 0.5)),
                            ("thirdperson_righthand", display([75, 45, 0], [0, 2.5, 0], 0.375)),
                            ("firstperson_righthand", display([0, 45, 0], [0, 0, 0], 0.4)),
                            ("firstperson_lefthand", display([0, 225, 0], [0, 0, 0], 0.4)))),
              ("elements", out))
    write(os.path.join(ASSETS, "models", "block", "fusion_reactor" + ("_on" if lit else "") + ".json"), model)
    print(f"fusion_reactor{'_on' if lit else ''}: {len(out)} cubes")
    return K, out


def reactor():
    built = [reactor_model(False), reactor_model(True)]
    variants = o()
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            v = o(("model", "tomyen:block/fusion_reactor" + ("_on" if lit == "true" else "")))
            if y:
                v["y"] = y
            variants[f"facing={facing},lit={lit}"] = v
    write(os.path.join(ASSETS, "blockstates", "fusion_reactor.json"), {"variants": variants})
    items_json("fusion_reactor", o(("type", "minecraft:model"), ("model", "tomyen:block/fusion_reactor")))
    return built


# ================================================================================================== reactor GUI

def reactor_gui():
    """The Fusion Reactor's screen (FusionReactorScreen, FusionReactorMenu): vanilla's furnace panel and inventory with
    the top redrawn: input slots left, beam grooves into an octagonal containment ring round a dark core well, the
    fuel slot and its gauge under the ring, the arrow, the big output slot. Overlays to the right (u >= 176)."""
    w, h, base = vanilla_png("gui/container/furnace.png")
    px = list(base)
    S = 256

    def put(x, y, c):
        if 0 <= x < S and 0 <= y < S:
            px[y * S + x] = c if len(c) == 4 else c + (255,)

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                put(x, y, c)

    PANEL, SLOT, DARK, LIGHT = (198, 198, 198), (139, 139, 139), (55, 55, 55), (255, 255, 255)
    rect(7, 4, 170, 78, PANEL)
    for x in range(176, 256):
        for y in range(0, 256):
            px[y * S + x] = (0, 0, 0, 0)

    def slot(x, y, size=18):
        rect(x, y, x + size, y + size, SLOT)
        rect(x, y, x + size - 1, y + 1, DARK)
        rect(x, y, x + 1, y + size - 1, DARK)
        rect(x + 1, y + size - 1, x + size, y + size, LIGHT)
        rect(x + size - 1, y + 1, x + size, y + size, LIGHT)

    slot(25, 16)
    slot(25, 52)
    slot(79, 59)
    slot(129, 30, 26)
    # The arrow, unlit, from the furnace's own.
    for y in range(17):
        for x in range(24):
            px[(35 + y) * S + 106 + x] = base[(34 + y) * S + 79 + x]

    # The containment ring: an octagon band round (88, 36), a highlight on its lower right, shadow upper left, and the
    # dark well inside where the core glows.
    cx, cy = 88, 36

    def octo(x, y):
        dx, dy = abs(x + 0.5 - cx), abs(y + 0.5 - cy)
        return max(dx, dy, (dx + dy) / 1.38)

    for y in range(cy - 18, cy + 18):
        for x in range(cx - 18, cx + 18):
            r = octo(x, y)
            if 12.0 <= r < 16.0:
                lit = (x - cx) + (y - cy) > 0
                put(x, y, (165, 172, 190) if 13.0 <= r < 15.0 else LIGHT if (r >= 15.0) == lit else DARK)
            elif r < 12.0:
                put(x, y, (24, 28, 36))
    # Bolts on the ring's diagonals.
    for bx, by in ((cx - 10, cy - 10), (cx + 9, cy - 10), (cx - 10, cy + 9), (cx + 9, cy + 9)):
        rect(bx, by, bx + 2, by + 2, (110, 116, 130))
        put(bx + 1, by + 1, DARK)

    # Beam grooves (A from the upper slot, B from the lower), and their lit overlays.
    def path(points, n=200):
        out = []
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            for k in range(n):
                t = k / n
                out.append((round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)))
        out.append(points[-1])
        return sorted(set(out))

    beams = [((44, 22), (176, 0), (30, 12), [(0, 3), (9, 3), (16, 8), (30, 8)]),
             ((44, 38), (176, 12), (30, 26), [(0, 23), (8, 23), (20, 6), (30, 6)])]
    for (gx, gy), (u, v), (bw, bh), pts in beams:
        line = path(pts)
        for x, y in line:
            for dy in (-1, 0, 1):
                if 0 <= y + dy < bh and 0 <= x < bw:
                    if octo(gx + x, gy + y + dy) >= 12.0 and octo(gx + x, gy + y + dy) < 16.0:
                        continue
                    put(gx + x, gy + y + dy, (85, 85, 85) if dy else (55, 55, 55))
                    put(u + x, v + y + dy, (74, 216, 240) if dy else (224, 255, 255))

    # The core frames: a ball of light growing brighter, four steps.
    for k in range(4):
        v0 = 38 + 24 * k
        for y in range(24):
            for x in range(24):
                d = math.hypot(x - 11.5, y - 11.5)
                rad = 5.5 + 2.2 * k
                if d < rad:
                    t = 1 - d / rad
                    c = mix(0x1EA6C8, 0xFFFFFF, min(1.0, t * (0.7 + 0.25 * k)))
                    put(176 + x, v0 + y, C(c)[:3])
                elif octo(76 + x, 24 + y) < 12.0:
                    put(176 + x, v0 + y, (24, 28, 36))
                else:
                    put(176 + x, v0 + y, (0, 0, 0, 0))
    # The fuel gauge: an empty groove, and its fill (green to cyan, uranium to soul).
    rect(72, 60, 76, 76, DARK)
    for y in range(16):
        for x in range(4):
            c = mix(0x8CF07A, 0x40D8F0, y / 15)
            put(208 + x, y, C(c)[:3] if 0 < x < 3 else C(mix(c, 0x1A4A3A, 0.4))[:3])
    png(os.path.join(TEX, "gui", "container", "fusion_reactor.png"), S, S, px)
    return px


# ============================================================================================ sprites and wings

def mod_png(path):
    return read_png(open(os.path.join(TEX, path), "rb").read())


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def recolour(px, palette, lo=None, hi=None):
    """Maps each opaque texel's lightness onto the palette (darkest first), keeping alpha: vanilla's shading, new hues."""
    ls = [lum(c) for c in px if c[3]]
    lo = min(ls) if lo is None else lo
    hi = max(ls) if hi is None else hi
    out = []
    for c in px:
        if not c[3]:
            out.append((0, 0, 0, 0))
            continue
        t = (lum(c) - lo) / max(1.0, hi - lo)
        k = max(0, min(len(palette) - 1, int(round(t * (len(palette) - 1)))))
        out.append(C(palette[k], c[3]))
    return out


CROW = (0x08080E, 0x121220, 0x1C1A2E, 0x28243F, 0x37315A, 0x4A4480, 0x6A62AC)
STORM = (0x10141A, 0x1A2028, 0x262E38, 0x34404E, 0x485668, 0x627288, 0x8696B0)
SPARK = (0x2EC8E6, 0x8AF6FF, 0xE8FFFF)


def wings_texture(name, palette, veins):
    w, h, px = vanilla_png("entity/equipment/wings/elytra.png")
    out = recolour(px, palette)
    # Feather rows across both broad faces (north u 24-33 and south u 36-45, v 2-21): each row ends in a dark tip
    # line stepping down toward the trailing edge, with a sheen texel at its root.
    for u0 in (24, 36):
        for row in range(4, 22, 4):
            for i in range(10):
                x = u0 + i
                y = row + (i * 2) // 10 - (1 if u0 == 36 else 0) + (0 if u0 == 24 else 0)
                y = min(21, y)
                if out[y * w + x][3]:
                    out[y * w + x] = C(palette[0])
                    if y - 1 >= 2 and out[(y - 1) * w + x][3]:
                        out[(y - 1) * w + x] = C(palette[2])
            x = u0 + (1 if u0 == 24 else 8)
            if out[(row - 2) * w + x][3]:
                out[(row - 2) * w + x] = C(palette[-1])
    if veins:
        # A lightning vein down each broad face: a zigzag of hot cyan with a softer edge.
        for u0, phase in ((24, 0), (36, 1)):
            x = u0 + 5
            for y in range(3, 21):
                step = ((y + phase) // 3) % 4
                dx = (0, 1, 2, 1)[step] - 1
                for d, col in ((0, SPARK[2]), (-1, SPARK[0]), (1, SPARK[0])):
                    xx = x + dx + d
                    if u0 <= xx < u0 + 10 and out[y * w + xx][3]:
                        out[y * w + xx] = C(col)
    png(os.path.join(TEX, "entity", "equipment", "wings", name + ".png"), w, h, out)
    write(os.path.join(ASSETS, "equipment", name + ".json"), {"layers": {"wings": [{"texture": "tomyen:" + name}]}})
    return out, w, h


def wings_sprites(name, palette, veins):
    sprites = []
    for broken in (False, True):
        w, h, px = vanilla_png("item/elytra" + ("_broken" if broken else "") + ".png")
        out = recolour(px, palette)
        if veins:
            for x, y in ((4, 4), (4, 5), (3, 6), (4, 7), (4, 8), (11, 4), (11, 5), (12, 6), (11, 7), (11, 8)):
                if out[y * w + x][3]:
                    out[y * w + x] = C(SPARK[1])
            # The thruster pod between the wings: steel, with a glowing nozzle.
            for x, y, c in ((7, 3, 0x96A9C8), (8, 3, 0x74849F), (7, 4, 0xBFD0EA), (8, 4, 0x96A9C8), (7, 5, 0x96A9C8), (8, 5, 0x535F75),
                            (7, 6, SPARK[1]), (8, 6, SPARK[0])):
                out[y * w + x] = C(c)
        else:
            # A crow's red eye glinting from the shoulder of each wing.
            for x, y in ((4, 3), (11, 3)):
                if out[y * w + x][3]:
                    out[y * w + x] = C(0xC0202A)
        suffix = "_broken" if broken else ""
        png(os.path.join(TEX, "item", name + suffix + ".png"), w, h, out)
        write(os.path.join(ASSETS, "models", "item", name + suffix + ".json"),
              o(("parent", "minecraft:item/generated"), ("textures", o(("layer0", "tomyen:item/" + name + suffix)))))
        sprites.append((out, w, h, 12))
    items_json(name, o(("type", "minecraft:condition"), ("property", "minecraft:broken"),
                       ("on_false", o(("type", "minecraft:model"), ("model", "tomyen:item/" + name))),
                       ("on_true", o(("type", "minecraft:model"), ("model", "tomyen:item/" + name + "_broken")))))
    return sprites


def rocket_boots_sprite():
    w, h, px = mod_png("item/big_red_boots.png")
    out = list(px)
    GLASS, GLASS_LT, GLASS_DK, LABEL, CAP = 0x4FB46A, 0x9CE6A8, 0x1E5A30, 0xF2F2F2, 0xD8342C
    CHROME, CHROME_DK = 0xD8DEE6, 0x7A828E
    # A bottle up the back of each boot's shaft, cap up, a chrome nozzle down at the heel; outlined on its right.
    rows = [(CAP, 0x9A1E1A, 0x5A0E0C), (GLASS_LT, GLASS, GLASS_DK), (GLASS_LT, GLASS, GLASS_DK), (LABEL, CAP, GLASS_DK),
            (GLASS_LT, GLASS, GLASS_DK), (GLASS, GLASS_DK, GLASS_DK), (CHROME, CHROME_DK, None)]
    for x0, y0 in ((6, 2), (13, 3)):
        for k, cols in enumerate(rows):
            for d, col in enumerate(cols):
                if col is not None and x0 + d < w:
                    out[(y0 + k) * w + x0 + d] = C(col)
    png(os.path.join(TEX, "item", "rocket_boots.png"), w, h, out)
    write(os.path.join(ASSETS, "models", "item", "rocket_boots.json"),
          o(("parent", "minecraft:item/generated"), ("textures", o(("layer0", "tomyen:item/rocket_boots")))))
    items_json("rocket_boots")
    return [(out, w, h, 12)]


# ============================================================================================ the worn rockets

def rocket_cubes():
    """The right leg's rocket (leg space: y down from the hip, front -z, outside -x): name, ramp, from, to."""
    cx, cz = -0.625, 5.0
    glass = (0x9CE6A8, 0x6CCB82, 0x4FB46A, 0x2E8A48, 0x1E5A30)
    chrome = (0xF2F6FA, 0xD8DEE6, 0xAEB6C2, 0x7A828E, 0x4E5560)
    red = (0xF07060, 0xD8342C, 0xB0281F, 0x86201A, 0x5A0E0C)
    rubber = (0x5D5D5D, 0x4A4A4F, 0x3F3E42, 0x343438, 0x2D2D32)
    label = (0xFFFFFF, 0xF2F2F2, 0xD8342C, 0xB0281F, 0x86201A)
    def box(name, ramp, x0, y0, z0, x1, y1, z1):
        return (name, ramp, (cx + x0, y0, cz + z0), (cx + x1, y1, cz + z1))
    return [
        box("cap", red, -0.8, 3.3, -0.8, 0.8, 4.0, 0.8),
        box("body", glass, -1.5, 4.0, -1.0, 1.5, 9.0, 1.0),
        box("body_round", glass, -1.0, 4.1, -1.5, 1.0, 8.9, 1.5),
        box("label", label, -1.6, 5.6, -1.6, 1.6, 7.2, 1.6),
        box("shoulder", glass, -1.0, 9.0, -1.0, 1.0, 9.6, 1.0),
        box("neck", glass, -0.55, 9.6, -0.55, 0.55, 10.4, 0.55),
        box("nozzle", chrome, -1.05, 10.4, -1.05, 1.05, 11.3, 1.05),
        box("bell", chrome, -1.25, 11.0, -1.25, 1.25, 11.55, 1.25),
        (("strap_top", rubber, (cx - 1.7, 4.9, 2.7), (cx + 1.7, 5.4, cz + 1.7))),
        (("strap_low", rubber, (cx - 1.7, 8.0, 2.7), (cx + 1.7, 8.5, cz + 1.7))),
    ]


def rocket_boots_model():
    import re
    cubes = rocket_cubes()
    TW, TH = 64, 32
    tex = [(0, 0, 0, 0)] * (TW * TH)
    # Shelf-pack each cube's unwrap (2(d+w) by d+h texels), painting it: lit top, dark underside, sides shaded from a
    # bright top row down, the label's middle a white band.
    placed, x, y, shelf = [], 0, 0, 0
    for name, ramp, a, b in cubes:
        w, h, d = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
        uw, uh = math.ceil(2 * (d + w)), math.ceil(d + h)
        if x + uw > TW:
            x, y, shelf = 0, y + shelf, 0
        placed.append((x, y))
        cd, cw, ch = math.ceil(d), math.ceil(w), math.ceil(h)
        for j in range(uh):
            for i in range(uw):
                if j < cd:
                    col = ramp[1] if i < cd + cw else ramp[4]
                else:
                    row = j - cd
                    col = ramp[1] if row == 0 else ramp[2] if row < ch - 1 or ch <= 2 else ramp[3]
                    if name == "label":
                        col = ramp[1] if 0 < row < ch - 1 else ramp[2]
                    if name == "body" and i % (cd + cw) == 1:
                        col = ramp[0]
                tex[(y + j) * TW + x + i] = C(col)
        x += uw + 1
        shelf = max(shelf, uh + 1)
    assert y + shelf <= TH, "rocket texture full"
    png(os.path.join(TEX, "entity", "rocket_boots_rockets.png"), TW, TH, tex)

    # Z-fight check against the boots' own cubes (BigRedBootsModel's right leg) and the leg.
    src = open(os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client", "bigredboots", "BigRedBootsModel.java")).read()
    right = src[src.index('"right_leg"'):src.index('"left_leg"')]
    elements = []
    def el(a, b, nm):
        faces = {f: {"uv": [0, 0, 1, 1], "texture": "#t"} for f in ("north", "south", "east", "west", "up", "down")}
        elements.append(o(("name", nm), ("from", list(a)), ("to", list(b)), ("faces", faces)))
    for m in re.finditer(r"addBox\(([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f\)", right):
        v = [float(t) for t in m.groups()]
        el(v[:3], [v[0] + v[3], v[1] + v[4], v[2] + v[5]], "boot")
    el([-2.0, 0.0, -2.0], [2.0, 12.0, 2.0], "leg")
    el([-2.25, -0.25, -2.25], [2.25, 12.25, 2.25], "trousers")
    for name, ramp, a, b in cubes:
        el(a, b, name)
    from gunsmith_kit import find_overlaps
    hits = [h for h in find_overlaps(elements) if not (h[0] in ("boot", "leg", "trousers") and h[2] in ("boot", "leg", "trousers"))]
    if hits:
        raise SystemExit("rocket boots z-fight: " + str(hits))

    def f(v):
        return f"{v:.4g}f" if "." in f"{v:.4g}" else f"{v:.4g}.0f"
    lines = []
    for leg, sign, pivot in (("right_leg", 1, "-1.9f"), ("left_leg", -1, "1.9f")):
        parts = []
        for (name, ramp, a, b), (u, v) in zip(cubes, placed):
            w, h, d = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
            x0 = a[0] if sign > 0 else -(a[0] + w)
            parts.append(f"\t\t\t\t.texOffs({u}, {v}).addBox({f(x0)}, {f(a[1])}, {f(a[2])}, {f(w)}, {f(h)}, {f(d)})")
        mirror = "" if sign > 0 else ".mirror()"
        lines.append(f'\t\troot.addOrReplaceChild("{leg}", CubeListBuilder.create(){mirror}\n' + "\n".join(parts) + f",\n\t\t\t\tPartPose.offset({pivot}, 12.0f, 0.0f));")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TW}, {TH});")
    java = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client", "fusion", "RocketNozzleModel.java")
    text = open(java).read()
    start, end = "\t\t// BEGIN GENERATED by tools/gen_fusion.py\n", "\t\t// END GENERATED\n"
    a, b = text.index(start) + len(start), text.index(end)
    open(java, "w").write(text[:a] + "\n".join(lines) + "\n" + text[b:])
    print(f"rocket boots: {len(cubes)} cubes a rocket, no z-fighting with the boots")


# ======================================================================================================== main

if __name__ == "__main__":
    built = {}
    import fusion_data
    fusion_data.write_data(DATA, ASSETS)
    built["swordfish_sword"] = swordfish_sword()
    built["fish_fish_fish"] = fish_fish_fish()
    rocket_boots_model()
    sprites = rocket_boots_sprite()
    sprites += wings_sprites("murder_wings", CROW, False)
    sprites += wings_sprites("stormcrow_rig", STORM, True)
    for name, pal, veins in (("murder_wings", CROW, False), ("stormcrow_rig", STORM, True)):
        out, w, h = wings_texture(name, pal, veins)
        sprites.append((out, w, h, 6))
    built["sprites"] = ("sprites", sprites)
    gui_px = reactor_gui()
    built["gui"] = ("sprites", [(gui_px, 256, 256, 3)])
    for k, (K, els) in enumerate(reactor()):
        built[f"reactor{k}"] = ("block", K, els)
    if PREVIEW:
        import preview_fusion
        preview_fusion.items(built, PREVIEW)
        preview_fusion.compose([preview_fusion.vanilla_sword_cells(vanilla_png)], os.path.join(PREVIEW, "vanilla_fp.png"))
