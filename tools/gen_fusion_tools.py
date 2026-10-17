#!/usr/bin/env python3
"""Art and data for Item Fusing batch 4, tools, fishing and odd items (com.jheinz.tomyen.fusion.tools).

Usage: python3 tools/gen_fusion_tools.py [--preview DIR]

Writes:
* Barbel Lash: the handle held in the hand (a 3D model; the lash itself is drawn by client/schnozzle/WhipLashes from the
  handle's tip, which sits exactly where the Nose Hair Whip's does, so WhipHandles needs no change), and for the
  inventory, the ground and item frames the same handle with its lash coiled beside it. The butt is a catfish's head,
  broad and flat with its eyes on top, a pale belly and four barbels trailing; the grip is braided nose hair with a
  catfish whisker wound round it and two bands of booger; the lash is bound on with a booger knot at the tip. The lash's
  ribbon texture: a pale barbel, ringed, with a strand of dark nose hair twisting along it.
* Grave Auger: one of the drill family (tools/gen_drills.py's parts, read from that file without running it), so the
  hands, spin and sound code all work as for any drill. The gravedigger's shovel wound into an auger: a double-flight
  screw of bright shovel-blade steel round an iron shaft, ending in a spade point; a body of dark iron framing coffin
  planks, with a coffin-lid hood and the shovel's own D-grip over it, handle-wood cross grips, a moss-green heavy fuel
  drum, and the gravedigger's lantern hung on the left flank, its wick burning a sickly green.
* Hexed Rod and Whiskerline Rod: 3D rods at rest, cast and bent under a fighting fish, the tip-top exactly where
  vanilla's sprite has it (so the line leaves from it), held as vanilla's rod is. The Hexed Rod: the Fae Rod's lavender
  silver shaft, tarnished toward the butt, on a bog-wood grip bound with twine and tufted with moss, a bone reel and
  butt, a green hex-eye at the tip-top, and the Hag Charm itself (bone ring, moss and glowing eye) hung from the reel
  seat. The Whiskerline Rod: a gunmetal shaft wrapped with whisker at its guides, a catfish-leather grip, a heavy winch
  reel wound with whisker line, the Grapple Claw's three-pronged claw as its tip-top, and two catfish barbels trailing
  from the fore-grip.
* Writ of Absolution: a flat sprite: a parchment scroll rolled top and bottom, ruled lines of the form, a gold cross
  over them and a red wax seal with a ribbon at the foot.
* Data: item tags, the Grave Auger's block tag and repair tag, and the lang.

Every model is checked for z-fighting (gunsmith_kit.check_all, world space, and gen_drills' coplanar for the drill).
Art rules: clean ramps, no noise."""
import ast, json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None

from gunkit import rotate, move, rz, ry, rx, write, display, mix, png, apply, ref, composite, range_dispatch, model as kit_model  # noqa: E402
from gunsmith_kit import Atlas, check_all, bounds  # noqa: E402
import gen_fusion as GF  # noqa: E402  (clears sys.argv; PREVIEW is read above)
from gen_fusion import C, ramp_flats, item_model, items_json, HANDHELD  # noqa: E402

REPO = os.path.dirname(HERE)
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "tomyen")
DATA = os.path.join(REPO, "src", "main", "resources", "data")
TEX = os.path.join(ASSETS, "textures")
MODELS = os.path.join(ASSETS, "models", "item")


def o(*items):
    return OrderedDict(items)


# ===================================================================================================== palettes (hi..dk)

HAIR = (0x6A5440, 0x4F3D2D, 0x3A2C20, 0x271D17, 0x150F0B)          # the Nose Hair Whip's lash
BOOGER = (0xC8E07A, 0x9FC24E, 0x7FA73D, 0x5A7E2A, 0x3A5418)        # its booger grip
CATFISH = (0x8A8C58, 0x6E7044, 0x5C5E38, 0x3E4228, 0x222816)       # the Giant Catfish's back
BELLY = (0xE6DEBE, 0xD0C6A0, 0xB7AE8D, 0x988E66, 0x6E6648)         # its belly
WHISKER = (0xD8CCA0, 0xB0A47A, 0x8E8460, 0x6A6446, 0x48452E)       # the catfish whisker
FAE = (0xFDF6FF, 0xE3CFF7, 0xC8B4E8, 0xA898D0, 0x5A4A7A)           # the Fae Rod's silver
TARNISH = (0xB8A8CC, 0x8E7EA8, 0x6E6088, 0x52466A, 0x342C48)       # fae silver gone dark under the hag's hand
FAE_PINK = (0xFFD8F4, 0xF2A8E0, 0xC070C8, 0x9A4AA4, 0x7A3A80)
BONE = (0xF2ECD6, 0xE6DCBE, 0xC8BC9C, 0xAA966C, 0x786E56)          # the Hag Charm's bone
MOSS = (0xB4D46A, 0x96C44A, 0x667C31, 0x4E6226, 0x3D4F1F)          # its moss
BOGWOOD = (0x7C6444, 0x664C34, 0x4E3A28, 0x3E2D1F, 0x23201A)       # its twine and bog wood
TWINE = (0xD4C49A, 0xB09468, 0x8E7650, 0x6E5A3C, 0x4E3E28)
HEXEYE = (0xF6FFCC, 0xD8FF8A, 0x96C44A, 0x5E9A2A, 0x3A6418)
GUNMETAL = (0xC4C8CC, 0xA0A8B0, 0x6E747C, 0x3E424A, 0x26282C)      # the Grapple Claw
GRIPWOOD = (0xBA9246, 0x9C7638, 0x7E5E28, 0x5E441C, 0x3E2C10)      # its handle


def mats(K, **ramps):
    for name, ramp in ramps.items():
        ramp_flats(K, name, ramp)


def mb(K, out, x0, y0, z0, x1, y1, z1, m, rot=None, name=None, emit=False, **faces):
    """A box of one ramp: lit top, mid sides, a lighter front (south, toward the inventory viewer), dark underside."""
    f = dict(up=f"{m}_lt", down=f"{m}_lo", east=f"{m}_md", west=f"{m}_md", north=f"{m}_md", south=f"{m}_lt")
    f.update(faces)
    return K.box(out, x0, y0, z0, x1, y1, z1, f"{m}_md", rot=rot, name=name, emit=emit, **f)


def octa(K, out, y0, y1, r, side, cap=None, cx=8.0, cz=8.0, name=None, emit=False, caps=(True, True), turn=0.0):
    """A round bar along y: four bands through the axis (0, 90, 45, -45 degrees about y), each an octagon side wide, so
    their ends make the octagon. Hidden faces dropped; band ends staggered a hair so the caps never fight."""
    a = 2 * r * math.tan(math.pi / 8)
    cap = cap or side
    first = None
    for k, (ang, w, h) in enumerate([(0, r, a / 2), (0, a / 2, r), (45, r, a / 2), (-45, r, a / 2)]):
        d = 0.012 * k
        wide_x = w > h
        faces = dict(down=cap if caps[0] else None, up=cap if caps[1] else None)
        if wide_x:
            faces.update(north=None, south=None)
        else:
            faces.update(east=None, west=None)
        e = K.box(out, cx - w, y0 + d, cz - h, cx + w, y1 - d, cz + h, side, emit=emit, name=name if k == 0 else None,
                  rot=("y", ang + turn, (cx, y0, cz)) if ang + turn else None, **faces)
        first = first or e
    return first


def star(K, out, y0, y1, half, side, cap=None, cx=8.0, cz=8.0, angle=0.0, name=None, emit=False):
    """A square bar along y and a second an eighth turn round it, set in at its ends: an eight-pointed section."""
    cap = cap or side
    K.box(out, cx - half, y0, cz - half, cx + half, y1, cz + half, side, emit=emit, name=name, up=cap, down=cap,
          rot=("y", angle, (cx, y0, cz)) if angle else None)
    K.box(out, cx - half * 0.92, y0 + 0.03, cz - half * 0.92, cx + half * 0.92, y1 - 0.03, cz + half * 0.92, side, emit=emit,
          up=cap, down=cap, rot=("y", angle + 45, (cx, y0, cz)))


def stripes(cols, period, vertical=False):
    """A swatch of bands: cols[k] for each texel row (or column) in a repeating period."""
    return lambda i, j: C(cols[(j if not vertical else i) % period * len(cols) // period])


def rot_pt(p, m, pivot):
    v = apply(m, [p[a] - pivot[a] for a in range(3)])
    return [v[a] + pivot[a] for a in range(3)]


def segment(K, out, p, q, half, m, depth=None, name=None, **faces):
    """A thin bar from p to q in a plane of constant z (a line, a strand): built along y and turned about z."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    length = math.hypot(dx, dy)
    cx, cy = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
    dz = depth if depth is not None else half
    angle = -math.degrees(math.atan2(dx, dy))
    return K.box(out, cx - half, cy - length / 2, p[2] - dz, cx + half, cy + length / 2, p[2] + dz, m,
                 rot=("z", angle, (cx, cy, p[2])) if abs(angle) > 1e-6 else None, name=name, **faces)


def chain(K, out, start, pieces, half, m, plane_z=None):
    """A drooping strand (a barbel, a whisker) in the z plane of start: pieces of (length, heading degrees from +y,
    clockwise), each starting where the last ended, overlapping it a little."""
    p = list(start)
    for k, (length, heading) in enumerate(pieces):
        h = math.radians(heading)
        q = [p[0] + math.sin(h) * length, p[1] + math.cos(h) * length, p[2]]
        back = [p[0] - math.sin(h) * half, p[1] - math.cos(h) * half, p[2]]
        # Each piece a little thinner than the last (a strand tapers), so where they overlap their faces never share a plane.
        segment(K, out, back, q, half * (1.0 - 0.12 * k), m, depth=half * (1.0 - 0.12 * k))
        p = q
    return p


def lay(elements, points, length_axis_top, target):
    """Turns a part built up +y (axis x = z = 8) onto the sprite diagonal (up and to the right), its local point
    (8, length_axis_top, 8) landing on target. points (a dict of local points) are carried along."""
    pivot = (8.0, 0.0, 8.0)
    m = rz(-45)
    rotate(elements, m, pivot)
    top = rot_pt([8.0, length_axis_top, 8.0], m, pivot)
    d = [target[a] - top[a] for a in range(3)]
    move(elements, *d)
    for k, p in points.items():
        q = rot_pt(p, m, pivot)
        points[k] = [q[a] + d[a] for a in range(3)]
    return elements


def fit_displays(elements, hands, rot=(0, 0, 0)):
    """The given hand transforms; the inventory, ground and frame views scaled to fit the model and centred on it."""
    lo, hi = bounds(elements)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    out = o(*hands.items())
    s = round(min(1.0, 16.0 / span), 3)
    out["gui"] = display(list(rot), [round(-(c[0] - 8) * s, 3), round(-(c[1] - 8) * s, 3), 0], s)
    out["ground"] = display([0, 0, 0], [round(-(c[0] - 8) * s * 0.5, 3), round(2 - (c[1] - 8) * s * 0.5, 3), 0], round(s * 0.5, 3))
    out["fixed"] = display([0, 180, 0], [round((c[0] - 8) * s, 3), round(-(c[1] - 8) * s, 3), 0], s)
    return out


def save_model(K, name, elements, displays):
    m = item_model(K, elements, displays)
    m["credit"] = f"tomyen {name} - generated by tools/gen_fusion_tools.py"
    write(os.path.join(MODELS, name + ".json"), m)


# ================================================================================================= the Barbel Lash

# Where the lash leaves the handle: the Nose Hair Whip's handle tip (client WhipHandles.TIP), in model pixels.
WHIP_TIP = (6.85, 6.35, 8.0)
HANDLE_LEN = 9.0


def barbel_kit():
    K = Atlas("barbel_lash", "item/barbel_lash", seed=41, size=64)
    mats(K, hair=HAIR, booger=BOOGER, cat=CATFISH, belly=BELLY, whisker=WHISKER)
    K.flat("white", 0xF4F2E6)
    K.flat("black", 0x0E0F0A)
    K.flat("mouth", 0x1A1C10)
    # The braid: diagonal plaits of the hair ramp, a lit edge on every plait.
    K.add("braid", 8, 16, lambda i, j: C((HAIR[0], HAIR[1], HAIR[2], HAIR[2], HAIR[3], HAIR[3], HAIR[4], HAIR[2])[(i + j) % 8]), picture=True)
    # Catfish skin: the back's olive, a darker band down the middle where the head's ridge runs.
    K.add("skin", 16, 8, lambda i, j: C(CATFISH[1] if j < 2 else CATFISH[2] if j < 6 else CATFISH[3]), picture=True)
    # A whisker: pale, ringed every fourth texel.
    K.add("barbel", 4, 16, lambda i, j: C(WHISKER[2] if j % 4 == 3 else (WHISKER[0], WHISKER[1], WHISKER[1], WHISKER[2])[i]), picture=True)
    # The booger knot: a glossy highlight up top, the green rolling down.
    K.add("snot", 8, 8, lambda i, j: C(BOOGER[0] if (j == 1 and 2 <= i <= 4) else mix(BOOGER[1], BOOGER[3], j / 7)), picture=True)
    return K


def barbel_handle(K):
    out = []
    pts = {}
    # -- The catfish head (the pommel): broad and flat across x, its back (eyes, ridge) toward +z, its pale belly -z.
    mb(K, out, 6.3, 0.6, 6.8, 9.7, 2.6, 9.2, "cat", name="head", south="skin", north="belly_md", down="cat_lo", up="cat_md")
    mb(K, out, 6.65, -0.2, 7.05, 9.35, 0.6, 8.95, "cat", name="snout", south="cat_lt", north="belly_lo", up=None)
    mb(K, out, 6.95, -0.36, 7.25, 9.05, -0.2, 8.75, "cat", name="mouth", down="mouth", east="mouth", west="mouth", south="mouth",
       north="mouth", up=None)
    # The gill covers: the head flares out just behind the eyes.
    for sx in (-1, 1):
        x0, x1 = (9.7, 9.98) if sx > 0 else (6.02, 6.3)
        mb(K, out, x0, 1.4, 7.2, x1, 2.5, 8.9, "cat", name="gill", **{("west" if sx > 0 else "east"): None}, south="cat_md", up="cat_md")
    # Eyes on top, set wide: white, a black pupil.
    for sx in (-1, 1):
        x = 8.0 + sx * 1.15
        K.box(out, x - 0.38, 1.55, 9.2, x + 0.38, 2.25, 9.33, "white", north=None, name="eye")
        K.box(out, x - 0.2, 1.7, 9.33, x + 0.2, 2.1, 9.4, "black", north=None)
    # Four barbels: the long pair from the corners of the mouth, sweeping out and back; a short pair under the chin.
    for sx in (-1, 1):
        chain(K, out, (8.0 + sx * 1.25, 0.1, 9.05), [(1.3, sx * 120), (1.4, sx * 150), (1.2, sx * 172)], 0.15, "barbel")
        chain(K, out, (8.0 + sx * 0.55, -0.25, 9.0), [(0.9, sx * 160), (0.8, sx * 178)], 0.12, "barbel")

    # -- The grip: braided nose hair from a booger collar at the head to the knot at the tip.
    octa(K, out, 2.6, 3.05, 0.98, "booger_md", cap="booger_lt", name="collar")
    octa(K, out, 2.62, 7.6, 0.78, "braid", cap="hair_lo", name="grip")
    for y in (3.75, 5.35):
        star(K, out, y, y + 0.42, 0.9, "snot", cap="booger_lt", name="band")
    # A catfish whisker wound up the grip, a turn every couple of pixels: one short piece on each face as it goes round.
    for k in range(8):
        y = 3.15 + k * 0.55
        if 3.7 < y < 4.25 or 5.3 < y < 5.85:
            continue
        ang = k * 90.0 + 22.5
        K.box(out, 8.0 - 0.42, y, 8.0 + 0.8, 8.0 + 0.42, y + 0.3, 8.0 + 0.98, "barbel", rot=("y", ang, (8.0, y, 8.0)), down="whisker_lo",
              up="whisker_lt", name="whisker_wrap" if k == 0 else None)

    # -- The knot where the lash is bound on: a lump of booger, a smaller one off to the side, tapering to the tip.
    octa(K, out, 7.6, 8.5, 1.0, "snot", cap="booger_lt", name="knot")
    mb(K, out, 8.35, 7.8, 7.4, 9.25, 8.4, 8.6, "booger", name="knot_lump", south="snot", north="snot", west=None)
    star(K, out, 8.5, HANDLE_LEN, 0.62, "booger_md", cap="booger_dk", name="knot_tip")
    pts["tip"] = [8.0, HANDLE_LEN, 8.0]
    lay(out, pts, HANDLE_LEN, WHIP_TIP)
    return out


def barbel_coil(K):
    """The lash coiled beside the handle, for the inventory and the ground: from the handle's tip a short run out, then
    two loops, a little apart in depth, tapering, and the end hanging free."""
    out = []
    cx, cy, r = 10.6, 10.2, 3.7
    p = list(WHIP_TIP)
    first = [cx - r * math.cos(math.radians(30)), cy - r * math.sin(math.radians(30)), 8.6]
    segment(K, out, [p[0] - 0.2, p[1] - 0.2, 8.6], first, 0.26, "barbel", depth=0.18)
    p = first
    n = 26
    for k in range(1, n + 1):
        t = k / n
        a = math.radians(210 + 360 * 1.7 * t)
        rr = r - 0.9 * t
        z = 8.6 - 1.3 * t
        q = [cx + rr * math.cos(a), cy + rr * math.sin(a), z]
        half = 0.26 - 0.12 * t
        mid = [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2]
        segment(K, out, [p[0], p[1], mid[2]], [q[0] + (q[0] - p[0]) * 0.08, q[1] + (q[1] - p[1]) * 0.08, mid[2]], half, "barbel",
                depth=0.16, name="coil" if k == 1 else None)
        p = q
    # The free end, falling away.
    chain(K, out, (p[0], p[1], p[2] - 0.2), [(1.4, 200), (1.2, 185)], 0.1, "barbel")
    return out


def barbel_lash_texture():
    """The ribbon (8 x 64, drawn along v from the handle to the tip, the barbel across the middle six columns): a pale
    whisker ramp across it, darker every few texels down its length, a strand of dark hair twisting round it, paling
    toward the tip."""
    W, H = 8, 64
    px = [(0, 0, 0, 0)] * (W * H)
    across = (None, WHISKER[3], WHISKER[1], WHISKER[0], WHISKER[1], WHISKER[2], WHISKER[3], None)
    for y in range(H):
        fade = y / (H - 1) * 0.35
        ring = y % 6 == 5
        # The hair strand crosses the barbel every twelve texels, one way then back.
        s = y % 12
        strand = 1 + (s if s < 6 else 11 - s)
        for x in range(W):
            c = across[x]
            if c is None:
                continue
            col = mix(c, WHISKER[3], 0.35) if ring else c
            if x == strand or x == strand + 1 and s in (2, 3, 8, 9):
                col = HAIR[2] if x == strand else HAIR[1]
            px[y * W + x] = C(mix(col, WHISKER[0], fade))
    png(os.path.join(TEX, "entity", "barbel_lash_lash.png"), W, H, px)
    return px, W, H


def barbel_lash():
    K = barbel_kit()
    handle = barbel_handle(K)
    check_all("barbel_lash_in_hand", handle)
    coiled = handle + barbel_coil(K)
    check_all("barbel_lash", coiled)
    K.save(TEX)
    hand_displays = fit_displays(handle, HANDHELD)
    gui_displays = fit_displays(coiled, HANDHELD, rot=(0, 0, 0))
    save_model(K, "barbel_lash_in_hand", handle, hand_displays)
    save_model(K, "barbel_lash", coiled, gui_displays)
    write(os.path.join(ASSETS, "items", "barbel_lash.json"), {"model": o(
        ("type", "minecraft:select"), ("property", "minecraft:display_context"),
        ("cases", [o(("when", ["gui", "ground", "fixed"]), ("model", ref("barbel_lash")))]),
        ("fallback", ref("barbel_lash_in_hand")))})
    lash = barbel_lash_texture()
    print(f"barbel_lash: {len(handle)} cubes in hand, {len(coiled)} coiled")
    return {"barbel_lash_in_hand": (K, handle, hand_displays), "barbel_lash": (K, coiled, gui_displays), "lash": lash}


# ================================================================================================== the fishing rods

# Vanilla's item/handheld_rod hands; the rod's tip-top where vanilla's sprite has it (the line leaves the hand there).
HANDHELD_ROD = o(
    ("thirdperson_righthand", display([0, 90, 55], [0, 4.0, 2.5], 0.85)),
    ("thirdperson_lefthand", display([0, -90, -55], [0, 4.0, 2.5], 0.85)),
    ("firstperson_righthand", display([0, 90, 25], [0, 1.6, 0.8], 0.68)),
    ("firstperson_lefthand", display([0, -90, -25], [0, 1.6, 0.8], 0.68)),
)
ROD_TIP = (14.4, 14.6, 8.0)
ROD_LEN = 16.9
# The shaft's joints (local y) and how far each bends under a fighting fish (toward the guides, +x).
JOINTS = [(7.2, -4.0), (9.4, -7.0), (11.6, -9.0), (13.6, -11.0), (15.3, -13.0)]
SHAFT = [(7.2, 9.4, 0.6), (9.4, 11.6, 0.5), (11.6, 13.6, 0.42), (13.6, 15.3, 0.35), (15.3, 16.6, 0.28)]
GUIDES = [8.5, 11.0, 13.2, 15.0]


def bend(elements, points, joints):
    """Bends a rod built up +y: each joint (y, degrees) turns everything beyond it, the far joints first."""
    centre = [(min(e["from"][1], e["to"][1]) + max(e["from"][1], e["to"][1])) / 2 for e in elements]
    pcentre = {k: p[1] for k, p in points.items()}
    for y, angle in reversed(joints):
        moving = [e for e, c in zip(elements, centre) if c > y]
        m = rz(angle)
        rotate(moving, m, (8.0, y, 8.0))
        for k, p in points.items():
            if pcentre[k] > y:
                points[k] = rot_pt(p, m, (8.0, y, 8.0))


def rod_shaft(K, out, pts, shaft_mats, guide_mat, eye_mat, wrap=None):
    """The shaft, segment by segment (shaft_mats one per segment, tarnished to bright or as given), the guides on the +x
    side with their eyes, and (wrap) a band of thread under each guide."""
    for (y0, y1, r), m in zip(SHAFT, shaft_mats):
        star(K, out, y0, y1, r, m, cap=m, name="shaft")
    for k, y in enumerate(GUIDES):
        r = next(rr for y0, y1, rr in SHAFT if y0 <= y < y1)
        if wrap:
            octa(K, out, y - 0.35, y + 0.35, r + 0.12, wrap, name="guide_wrap")
        K.box(out, 8.0 + r * 0.7, y - 0.12, 7.88, 8.0 + r + 0.55, y + 0.12, 8.12, guide_mat, name="guide")
        K.box(out, 8.0 + r + 0.45, y - 0.3, 7.82, 8.0 + r + 0.8, y + 0.3, 8.18, eye_mat, name="eye")
        pts[f"guide{k}"] = [8.0 + r + 0.62, y, 8.0]
    pts["tip"] = [8.0, ROD_LEN, 8.0]


def rod_line(K, out, pts, line_mat, state):
    """The line, in world space once the rod is laid (and bent): from the reel through each guide to the tip; at rest a
    length hangs from the tip to a hook."""
    chain_pts = [pts["reel_out"]] + [pts[f"guide{k}"] for k in range(len(GUIDES))] + [pts["tip_eye"]]
    for k, (p, q) in enumerate(zip(chain_pts, chain_pts[1:])):
        # Alternate pieces a hair thinner, so the overlaps where they meet at a guide never share a plane.
        d = 0.06 if k % 2 == 0 else 0.05
        segment(K, out, [p[0], p[1], 8.0], [q[0], q[1], 8.0], d, line_mat, depth=d, name="line" if k == 0 else None)
    if state == "rest":
        t = pts["tip_eye"]
        segment(K, out, [t[0], t[1], 8.0], [t[0], 4.2, 8.0], 0.04, line_mat, depth=0.04, name="hanging_line")
        # The hook: a shank, a bend and a barb.
        K.box(out, t[0] - 0.12, 2.6, 7.88, t[0] + 0.12, 4.3, 8.12, "hook_md", name="hook", up="hook_lt", down="hook_dk")
        K.box(out, t[0] - 1.25, 2.35, 7.88, t[0] + 0.12, 2.6, 8.12, "hook_md", up="hook_lt", down="hook_dk")
        K.box(out, t[0] - 1.25, 2.6, 7.88, t[0] - 1.0, 3.5, 8.12, "hook_md", up="hook_lt", down="hook_dk")


def build_rod(name, K, body_fn, extras_fn, line_mat):
    """A rod's three states: built straight, bent for a fight, laid on the diagonal, its line and extras added."""
    states = {}
    for state in ("rest", "cast", "bent"):
        out, pts = [], {}
        body_fn(K, out, pts)
        if state == "bent":
            bend(out, pts, JOINTS)
        lay(out, pts, ROD_LEN, ROD_TIP)
        if state == "bent":
            # Bent, the tip has moved: put it back so the line still leaves from where vanilla expects (the hand moves it, not us).
            pass
        rod_line(K, out, pts, line_mat, state)
        extras_fn(K, out, pts, state)
        check_all(f"{name}_{state}", out)
        states[state] = (out, pts)
    displays = fit_displays(states["rest"][0], HANDHELD_ROD)
    for state, (out, _) in states.items():
        suffix = "" if state == "rest" else "_" + state
        save_model(K, name + suffix, out, displays)
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": o(
        ("type", "minecraft:condition"), ("property", "tomyen:rod_fighting"),
        ("on_true", ref(name + "_bent")),
        ("on_false", o(("type", "minecraft:condition"), ("property", "minecraft:fishing_rod/cast"),
                       ("on_true", ref(name + "_cast")), ("on_false", ref(name)))))})
    K.save(TEX)
    print(f"{name}: " + ", ".join(f"{s} {len(v[0])} cubes" for s, v in states.items()))
    return {f"{name}{'' if s == 'rest' else '_' + s}": (K, v[0], displays) for s, v in states.items()}


def reel(K, out, pts, y0, y1, drum_r, plate_r, drum_mat, plate_mat, crank_mat, knob_mat, foot_mat, foot_out=1.0):
    """A reel hung under the seat (+x): a foot down from the seat, a drum across the rod (along z) between two side
    plates, and a crank on the near (+z) plate with its knob."""
    cy = (y0 + y1) / 2
    cx = 8.0 + 0.8 + foot_out + plate_r
    mb(K, out, 8.6, cy - 0.45, 7.7, cx - plate_r + 0.2, cy + 0.45, 8.3, foot_mat, name="reel_foot")
    # The drum: an octagon along z (two crossed boxes and two turned an eighth).
    a = 2 * drum_r * math.tan(math.pi / 8)
    for k, (ang, w, h) in enumerate([(0, drum_r, a / 2), (0, a / 2, drum_r), (45, drum_r, a / 2), (-45, drum_r, a / 2)]):
        d = 0.012 * k
        K.box(out, cx - w, cy - h, 7.05 + d, cx + w, cy + h, 8.95 - d, drum_mat, rot=("z", ang, (cx, cy, 8.0)) if ang else None,
              name="drum" if k == 0 else None, north=None, south=None)
    # The side plates: octagonal discs, a little proud of the drum all round, lit on their outer faces.
    pa = 2 * plate_r * math.tan(math.pi / 8)
    for z0, z1, outer in ((6.75, 7.05, "north"), (8.95, 9.25, "south")):
        for k, (ang, w, h) in enumerate([(0, plate_r, pa / 2), (0, pa / 2, plate_r), (45, plate_r, pa / 2), (-45, plate_r, pa / 2)]):
            d = 0.012 * k
            faces = dict(south=f"{plate_mat}_lt" if outer == "south" else f"{plate_mat}_lo",
                         north=f"{plate_mat}_lt" if outer == "north" else f"{plate_mat}_lo", up=f"{plate_mat}_lt", down=f"{plate_mat}_dk")
            if w > h:
                faces.update(up=None, down=None)
            else:
                faces.update(east=None, west=None)
            K.box(out, cx - w, cy - h, z0 + d, cx + w, cy + h, z1 - d, f"{plate_mat}_md", rot=("z", ang, (cx, cy, 8.0)) if ang else None,
                  name="side_plate" if k == 0 else None, **faces)
    # The crank: an arm out across the near plate, a knob on its end.
    mb(K, out, cx - 0.22, cy - 0.22, 9.25, cx + plate_r + 0.5, cy + 0.22, 9.5, crank_mat, name="crank")
    mb(K, out, cx + plate_r + 0.2, cy - 0.35, 9.5, cx + plate_r + 0.9, cy + 0.35, 10.3, knob_mat, name="knob")
    pts["reel_out"] = [cx - drum_r * 0.4, cy + drum_r, 8.0]


# ------------------------------------------------------------------------------------------------------ the Hexed Rod

def hexed_kit():
    K = Atlas("hexed_rod", "item/hexed_rod", seed=42, size=128)
    mats(K, fae=FAE, tarnish=TARNISH, pink=FAE_PINK, bone=BONE, moss=MOSS, bog=BOGWOOD, twine=TWINE, eye=HEXEYE, hook=TARNISH)
    # Fae silver along the shaft, from tarnished at the butt to bright at the tip, one swatch per segment.
    for k in range(5):
        t = k / 4
        ramp = [mix(a, b, t) for a, b in zip(TARNISH, FAE)]
        K.add(f"shaft{k}", 4, 16, lambda i, j, r=ramp: C((r[0], r[1], r[2], r[3])[i]), picture=True)
    # Bog wood: grain lines down its length, darker toward the ends.
    K.add("bogwood", 8, 16, lambda i, j: C(BOGWOOD[3] if i in (2, 6) else BOGWOOD[1] if i in (0, 4) else BOGWOOD[2]), picture=True)
    K.add("twine", 8, 4, lambda i, j: C((TWINE[0], TWINE[1], TWINE[2], TWINE[3])[(i + j) % 4]), picture=True)
    K.add("line", 4, 4, lambda i, j: C(mix(0xD8E8C0, HEXEYE[2], 0.25)), picture=True)
    K.add("glow", 4, 4, lambda i, j: C(HEXEYE[0] if 1 <= i <= 2 and 1 <= j <= 2 else HEXEYE[1]), picture=True)
    return K


def hexed_body(K, out, pts):
    # The butt: a bone knob, a tarnished ferrule.
    octa(K, out, 0.0, 0.75, 1.15, "bone_md", cap="bone_lt", name="butt")
    octa(K, out, 0.75, 0.95, 1.05, "tarnish_md", cap="tarnish_lt")
    # The grip: bog wood bound with twine, moss in the gaps.
    octa(K, out, 0.97, 4.6, 0.95, "bogwood", cap="bog_md", name="grip")
    for y in (1.4, 2.65, 3.9):
        octa(K, out, y, y + 0.38, 1.05, "twine", cap="twine_lt", name="twine", turn=22.5)
    for y, ang in ((2.05, 30), (3.3, 160), (4.25, 250)):
        K.box(out, 7.45, y - 0.25, 8.85, 8.55, y + 0.25, 9.25, "moss_md", rot=("y", ang, (8.0, y, 8.0)), up="moss_lt", down="moss_dk",
              south="moss_lt", name="moss")
    # The fore-grip and reel seat, fae silver going dark.
    octa(K, out, 4.6, 5.0, 1.08, "tarnish_lt", cap="fae_md", name="ferrule")
    octa(K, out, 5.0, 7.2, 0.72, "tarnish_md", cap="tarnish_lt", name="seat")
    reel(K, out, pts, 5.25, 6.95, 0.8, 1.0, "bone_md", "tarnish", "tarnish", "eye", "tarnish", foot_out=0.5)
    pts["charm"] = [8.6, 4.8, 8.0]
    rod_shaft(K, out, pts, [f"shaft{k}" for k in range(5)], "fae_md", "pink_lt", wrap="twine")
    # The tip-top: a little ring, and the hag's green eye set in it.
    K.box(out, 7.65, 16.55, 7.65, 8.35, 16.95, 8.35, "fae_lt", name="tip_top", up="fae_hi")
    K.box(out, 7.75, 16.95, 7.75, 8.25, 17.35, 8.25, "glow", emit=True, name="tip_eye")
    pts["tip_eye"] = [8.0, 17.15, 8.0]


def hexed_extras(K, out, pts, state):
    """The Hag Charm hung from the fore-grip on a twine cord: a bone ring, a green eye in it, a tuft of moss each side."""
    a = pts["charm"]
    top = [a[0] + 0.3, a[1] - 0.2, 8.0]
    bottom = [top[0], top[1] - 1.7, 8.0]
    segment(K, out, top, bottom, 0.09, "twine_md", depth=0.09, name="cord")
    cx, cy = bottom[0], bottom[1] - 0.9
    for k, ang in enumerate((0, 45)):
        K.box(out, cx - 0.9, cy - 0.9, 7.75 + 0.01 * k, cx + 0.9, cy + 0.9, 8.25 - 0.01 * k, "bone_md", rot=("z", ang, (cx, cy, 8.0)) if ang else None,
              south="bone_lt", north="bone_lo", up="bone_lt", down="bone_lo", name="charm_bone" if k == 0 else None)
    K.box(out, cx - 0.42, cy - 0.42, 8.25, cx + 0.42, cy + 0.42, 8.4, "glow", emit=True, north=None, name="charm_eye")
    K.box(out, cx - 0.42, cy - 0.42, 7.6, cx + 0.42, cy + 0.42, 7.75, "glow", emit=True, south=None)
    for sx in (-1, 1):
        K.box(out, cx + sx * 0.75, cy - 1.3, 7.85, cx + sx * 1.55, cy - 0.3, 8.15, "moss_md", rot=("z", sx * -25, (cx + sx * 0.9, cy - 0.3, 8.0)),
              up="moss_lt", down="moss_dk", south="moss_lt", name="charm_moss")


# ------------------------------------------------------------------------------------------------- the Whiskerline Rod

def whisker_kit():
    K = Atlas("whiskerline_rod", "item/whiskerline_rod", seed=43, size=128)
    mats(K, gun=GUNMETAL, cat=CATFISH, belly=BELLY, whisker=WHISKER, wood=GRIPWOOD, hook=GUNMETAL)
    K.add("steel", 4, 16, lambda i, j: C((GUNMETAL[1], GUNMETAL[2], GUNMETAL[2], GUNMETAL[3])[i]), picture=True)
    K.add("steel_dark", 4, 16, lambda i, j: C((GUNMETAL[2], GUNMETAL[3], GUNMETAL[3], GUNMETAL[4])[i]), picture=True)
    # Catfish leather: olive bands wound round, the belly's pale showing between.
    K.add("leather", 16, 8, lambda i, j: C((CATFISH[1], CATFISH[2], CATFISH[2], CATFISH[3], BELLY[3], CATFISH[2], CATFISH[2], CATFISH[3])[j]),
          picture=True)
    K.add("wound", 16, 8, lambda i, j: C((WHISKER[0], WHISKER[1], WHISKER[2], WHISKER[1])[j % 4]), picture=True)
    K.add("barbel", 4, 16, lambda i, j: C(WHISKER[2] if j % 4 == 3 else (WHISKER[0], WHISKER[1], WHISKER[1], WHISKER[2])[i]), picture=True)
    K.add("line", 4, 4, lambda i, j: C(WHISKER[1]), picture=True)
    return K


def whisker_body(K, out, pts):
    # The butt: a gunmetal pommel with a lanyard ring through it.
    octa(K, out, 0.0, 0.8, 1.2, "steel_dark", cap="gun_md", name="pommel")
    K.box(out, 7.9, -0.95, 7.3, 8.1, 0.2, 8.7, "gun_md", name="lanyard_ring", south="gun_lt")
    # The grip: catfish leather, a gunmetal fore-ferrule.
    octa(K, out, 0.8, 4.6, 1.0, "leather", cap="cat_md", name="grip")
    octa(K, out, 4.6, 5.05, 1.12, "steel", cap="gun_lt", name="ferrule")
    octa(K, out, 5.05, 7.2, 0.75, "steel_dark", cap="gun_md", name="seat")
    reel(K, out, pts, 5.1, 7.1, 1.05, 1.3, "wound", "gun", "gun", "wood", "gun", foot_out=0.45)
    pts["barbels"] = [8.0, 4.8, 8.0]
    rod_shaft(K, out, pts, ["steel_dark", "steel_dark", "steel", "steel", "steel"], "gun_lt", "gun_hi", wrap="wound")
    # The tip-top: the Grapple Claw's hub, its three prongs splayed out and hooked back down the rod.
    star(K, out, 16.4, 17.0, 0.42, "steel", cap="gun_lt", name="claw_hub")
    for k in range(3):
        ang = 90 + k * 120
        # Out from the hub, down the rod, and a hooked point turned back in.
        e1 = K.box(out, 7.84, 16.5, 8.3, 8.16, 16.88, 10.1, "gun_md", up="gun_lt", down="gun_dk", south="gun_lt", name="prong")
        e2 = K.box(out, 7.87, 14.9, 9.74, 8.13, 16.6, 10.04, "gun_md", up="gun_lt", down="gun_dk", south="gun_lt", north="gun_lo")
        e3 = K.box(out, 7.9, 14.7, 9.15, 8.1, 15.0, 9.98, "gun_lt", up="gun_hi", down="gun_dk")
        for e in (e1, e2, e3):
            rotate([e], ry(ang), (8.0, 16.7, 8.0))
    pts["tip_eye"] = [8.0, 17.0, 8.0]


def whisker_extras(K, out, pts, state):
    """Two catfish barbels trailing from the fore-grip, drooping."""
    a = pts["barbels"]
    for k, (dz, pieces) in enumerate(((8.6, [(1.2, 170), (1.3, 190), (1.1, 205)]), (7.4, [(1.0, 160), (1.2, 182), (1.0, 196)]))):
        chain(K, out, (a[0] + 0.4, a[1] - 0.2, dz), pieces, 0.13, "barbel")


# ================================================================================================= the Grave Auger


def drill_parts():
    """gen_drills.py's part builders and constants, without running its output."""
    src = open(os.path.join(HERE, "gen_drills.py")).read()
    tree = ast.parse(src)
    keep = {"AX", "AY", "FRAMES", "MOTOR_STEP", "FP_ROT", "FP_SCALE", "FP_GRIP_TARGET", "TP_GRIP_TARGET", "TP_SCALE", "GUI_ROT",
            "BODY_Z", "CROSS_Z", "REACH", "_CORNERS", "_NORMALS"}
    body = []
    for node in tree.body:
        if isinstance(node, (ast.Import, ast.ImportFrom, ast.FunctionDef, ast.ClassDef)):
            body.append(node)
        elif isinstance(node, ast.Assign) and all(isinstance(t, (ast.Name, ast.Tuple)) for t in node.targets):
            names = set()
            for t in node.targets:
                names |= {n.id for n in ast.walk(t) if isinstance(n, ast.Name)}
            if names <= keep:
                body.append(node)
    ns = {"__name__": "gen_drills_parts", "__file__": os.path.join(HERE, "gen_drills.py")}
    exec(compile(ast.Module(body=body, type_ignores=[]), "gen_drills.py", "exec"), ns)
    return ns


def planks(dark, mid, light, seam):
    """Coffin planks (a picture): boards across the face, a lit top edge on each, a dark seam, two nail heads per board."""
    def f(x, y, w, h):
        row = y % 5
        if row == 4:
            return GF.C(seam)
        c = light if row == 0 else mix(mid, dark, row / 4)
        if row == 2 and x in (2, w - 3):
            c = mix(c, 0x9A9A9A, 0.7)
        return GF.C(c)
    return f


def grave_auger():
    D = drill_parts()
    AX, AY, FRAMES, STEP = D["AX"], D["AY"], D["FRAMES"], D["MOTOR_STEP"]
    part, star8, square = D["part"], D["star8"], D["square"]
    k = D["DrillKit"]("grave_auger", 705)
    k.metal("iron", 0xB8B8BC, 0x6E6E74, 0x46464C, 0x26262A, top=(0xC8C8CC, 0x88888E, 0x6E6E74, 0x56565C))
    k.metal("iron_dark", 0x6E6E74, 0x3C3C42, 0x26262A, 0x141416, top=(0x7E7E84, 0x4C4C52, 0x3C3C42, 0x26262A))
    k.metal("blade", 0xFFFFFF, 0xE5E5E5, 0xA8A8AE, 0x6E6E74, top=(0xFFFFFF, 0xF4F4F4, 0xE5E5E5, 0xC8C8CC))
    k.metal("edge", 0xFFFFFF, 0xF4F4F4, 0xD8D8D8, 0xA0A0A0)
    k.metal("bronze", 0xF0D8A0, 0xB89058, 0x806030, 0x4A3418, top=(0xFFE8B8, 0xD0A868, 0xB89058, 0x987440))
    k.metal("tank", 0x9AB07C, 0x5A7040, 0x384A2A, 0x1E2818, top=(0xA8BE88, 0x6A8250, 0x5A7040, 0x465A34))
    k.metal("hose", 0x5A5E65, 0x313338, 0x1E1F23, 0x0E0E10)
    k.wood("handle", 0xB89048, 0x896727, 0x5A4318, 0x3A2A0E, (0x7A5C24, 0x4A3614, 0x2A1E0A))
    k.wood("coffin", 0x8A7A68, 0x645444, 0x443828, 0x2A2018, (0x5A4C3C, 0x3A3024, 0x241C14))
    k.paint("panel", planks(0x3A3026, 0x5A4C3C, 0x7A6A56, 0x1C1610), picture=True)
    k.paint("lantern", D["glow"](0xF8FFD0, 0x9AD848), picture=True)
    k.save(TEX)

    # -- The body: dark iron framing coffin planks, two bronze bands; the rear cross with handle-wood grips.
    body = D["framed_body"](k, [], D["BODY_Z"][0], D["BODY_Z"][1], 4.75, 4.2, 4.5, "iron", "panel", "iron_dark", "bronze",
                            [(4.3, 0.6), (9.8, 0.6)])
    grip, support = D["rear_cross"](k, body, D["CROSS_Z"], D["REACH"], "iron_dark", "iron", "bronze",
                                    grips=("handle", [("y", -1), ("x", -1)]))
    # The hood: a coffin lid, wide at the shoulders and narrowing both ways, iron straps over it.
    part(k, body, AX - 3.2, AY + 4.75, 4.6, AX + 3.2, AY + 6.3, 9.4, "coffin", name="hood", down=None)
    part(k, body, AX - 2.5, AY + 4.75, 2.9, AX + 2.5, AY + 6.0, 4.6, "coffin", south=None, down=None)
    part(k, body, AX - 2.5, AY + 4.75, 9.4, AX + 2.5, AY + 6.0, 11.4, "coffin", north=None, down=None)
    part(k, body, AX - 2.6, AY + 6.3, 5.4, AX + 2.6, AY + 6.8, 8.6, "coffin", name="lid", down=None)
    for z in (5.0, 8.4):
        part(k, body, AX - 3.4, AY + 4.75, z, AX + 3.4, AY + 6.5, z + 0.5, "iron_dark", name="strap", down=None)
    # The shovel's D-grip, standing over the lid: two posts and the grip across them, iron ferrules at the feet.
    for sx in (-1, 1):
        x0, x1 = sorted((AX + sx * 1.0, AX + sx * 1.7))
        part(k, body, x0, AY + 6.8, 6.2, x1, AY + 9.4, 6.9, "handle", name="d_post", down=None)
        part(k, body, x0 - 0.12, AY + 6.8, 6.08, x1 + 0.12, AY + 7.4, 7.02, "iron", name="ferrule", down=None)
    part(k, body, AX - 2.2, AY + 9.4, 6.05, AX + 2.2, AY + 10.4, 7.05, "handle", name="d_grip")
    # The exhaust, up from the hood's left rear.
    part(k, body, AX - 2.2, AY + 6.0, 10.0, AX - 1.2, AY + 9.0, 11.0, "iron_dark", name="exhaust", down=None, north=None)
    part(k, body, AX - 2.35, AY + 8.6, 10.0, AX - 1.05, AY + 9.3, 12.0, "iron_dark", name="exhaust_outlet", south="hose")
    # The heavy fuel drum on the right, its line up into the hood.
    D["side_tank"](k, body, AX + 6.7, AY - 1.0, 3.2, 11.2, 2.0, "tank", "iron_dark", "bronze")
    part(k, body, AX + 6.3, AY + 0.9, 9.75, AX + 7.1, AY + 6.0, 10.55, "hose", name="fuel_line")
    part(k, body, AX + 3.2, AY + 5.3, 9.85, AX + 6.7, AY + 5.9, 10.45, "hose")
    # The gravedigger's lantern on the left flank: a bracket out from the body, the lantern hung under it.
    part(k, body, AX - 6.4, AY + 3.3, 6.6, AX - 4.75, AY + 3.75, 7.4, "iron_dark", name="bracket")
    lx, lz = AX - 6.05, 7.0
    part(k, body, lx - 0.25, AY + 2.65, lz - 0.25, lx + 0.25, AY + 3.3, lz + 0.25, "iron_dark", name="hanger")
    part(k, body, lx - 0.85, AY + 2.2, lz - 0.85, lx + 0.85, AY + 2.65, lz + 0.85, "iron", name="lantern_cap")
    part(k, body, lx - 0.6, AY - 0.1, lz - 0.6, lx + 0.6, AY + 2.2, lz + 0.6, "lantern", emit=True, name="lantern_light")
    for sx in (-1, 1):
        for sz in (-1, 1):
            part(k, body, lx + sx * 0.6 - 0.14 + 0.14 * sx, AY - 0.07, lz + sz * 0.6 - 0.14 + 0.14 * sz,
                 lx + sx * 0.6 + 0.14 + 0.14 * sx, AY + 2.17, lz + sz * 0.6 + 0.14 + 0.14 * sz, "iron_dark", name="lantern_post")
    part(k, body, lx - 0.85, AY - 0.55, lz - 0.85, lx + 0.85, AY - 0.1, lz + 0.85, "iron", name="lantern_base")
    part(k, body, AX - 0.4, AY - 6.2, D["CROSS_Z"] - 0.5, AX + 0.4, AY - 4.75, D["CROSS_Z"] + 0.4, "bronze", name="trigger")

    # -- The auger: the chuck, an iron shaft, two flights of shovel blade winding down it, and a spade point.
    def auger():
        out = []
        for i, (z0, z1, h) in enumerate([(-5.0, -1.8, 1.05), (-8.2, -5.0, 0.9), (-11.4, -8.2, 0.75), (-13.6, -11.4, 0.6)]):
            star8(k, out, z0, z1, h, "iron", 22.5 * i, name="shaft" if i == 0 else None)
        n, pitch, turn = 14, 0.78, 360.0 / 14
        for s in (0, 180):
            for i in range(n):
                z1 = -2.12 - i * pitch
                z0 = z1 - pitch
                r_out = 4.3 - 2.8 * i / (n - 1)
                r_in = 0.55
                part(k, out, AX + r_in, AY - 0.17, z0, AX + r_out, AY + 0.17, z1, "blade", rot=D["zrot"](s + i * turn),
                     east="edge", name="flight" if (s == 0 and i == 0) else None)
        # The spade point: the gravedigger's blade, stepping in to its tip.
        for x, t, z0, z1, name in ((1.7, 0.3, -14.2, -13.0, "spade"), (1.15, 0.26, -15.2, -14.2, None), (0.6, 0.22, -15.9, -15.2, None)):
            part(k, out, AX - x, AY - t, z0, AX + x, AY + t, z1, "blade", east="edge", west="edge", north="edge", name=name)
        D["chuck"](k, out, -2.6, 1.0, 2.9, 5.0, 2.3, "iron_dark", "iron", ring=(3.6, 4.5, "iron_dark", 1.0))
        return out

    still = auger()

    def moving(frame):
        return D["spun"](still, STEP * frame)

    whole = body + moving(0)
    displays = D["held_displays"](grip, whole)
    credit = "tomyen grave_auger - generated by tools/gen_fusion_tools.py"
    write(os.path.join(MODELS, "grave_auger.json"), kit_model(k, body, displays, credit=credit))
    frames = []
    for f in range(FRAMES):
        parts = moving(f)
        D["check"](f"grave_auger_spin_{f}", body + parts)
        check_all(f"grave_auger_spin_{f}", body + parts)
        write(os.path.join(MODELS, f"grave_auger_spin_{f}.json"), kit_model(k, parts, displays, credit=credit))
        frames.append(ref(f"grave_auger_spin_{f}"))
    still_model = composite(ref("grave_auger"), frames[0])
    spinning = composite(ref("grave_auger"), range_dispatch("tomyen:drill_spin", [(f, frames[f]) for f in range(1, FRAMES)], frames[0]))
    write(os.path.join(ASSETS, "items", "grave_auger.json"), {"model": o(
        ("type", "minecraft:select"), ("property", "minecraft:display_context"),
        ("cases", [o(("when", ["gui", "ground", "fixed"]), ("model", still_model))]), ("fallback", spinning))})
    print(f"grave_auger: {len(whole)} cubes ({len(body)} body, {len(whole) - len(body)} spinning), {FRAMES} frames")
    print(f"   grip {D['fp_point'](displays, grip)}  support {D['fp_point'](displays, support)}")
    return {"grave_auger": (k, whole, displays)}


# ========================================================================================== the Writ of Absolution

def writ():
    """16 x 16: a scroll, rolled at the top and the foot, the form's ruled lines, a gold cross above them, and a red
    wax seal with two ribbon tails at the bottom right."""
    P = {
        "a": (0x5E4A2C,), "b": (0xF8F0D4,), "c": (0xEEDFB4,), "d": (0xDCC894,), "e": (0xC4AA72,), "f": (0x9A7E4E,),
        "l": (0x8A96B8,), "m": (0x6A7698,),
        "G": (0xFFF0A0,), "H": (0xF2D060,), "I": (0xC89A30,), "J": (0x8A6418,),
        "R": (0xF07068,), "S": (0xC83232,), "T": (0x8E1C1E,), "U": (0x5A1014,),
        "r": (0xB02428,), "s": (0x7A1418,),
    }
    rows = [
        "................",
        "..aaaaaaaaaaaa..",
        ".afbbbbbbbbbbbea",
        ".aedddddddddddfa",
        "..afeeeeeeeeefa.",
        "..acccccHcccca..",
        "..acccGHHHIccda.",
        "..accccdIdcccda.",
        "..acllllIllmcda.",
        "..acccccccccdda.",
        "..acllllllmcdda.",
        "..acccccccRSdda.",
        ".aedddddcRSSTTfa",
        ".afbbbbbbSTTUUea",
        "..aaaaaaarTUaa..",
        "........rs..s...",
    ]
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    px = []
    for row in rows:
        for ch in row:
            px.append((0, 0, 0, 0) if ch == "." else C(P[ch][0]))
    png(os.path.join(TEX, "item", "writ_of_absolution.png"), 16, 16, px)
    write(os.path.join(MODELS, "writ_of_absolution.json"), o(("parent", "minecraft:item/generated"),
                                                               ("textures", o(("layer0", "tomyen:item/writ_of_absolution")))))
    items_json("writ_of_absolution")
    return px


# ============================================================================================================ data

LANG = o(
    ("item.tomyen.barbel_lash", "Barbel Lash"),
    ("item.tomyen.barbel_lash.reach", "Lashes out 9 blocks; snags from 14"),
    ("item.tomyen.barbel_lash.drag", "Whatever it hits is dragged toward you"),
    ("item.tomyen.barbel_lash.sweep", "Every lash sweeps dropped items to you"),
    ("item.tomyen.barbel_lash.snag", "Use to snag a creature, an item or a ledge"),
    ("item.tomyen.grave_auger", "Grave Auger"),
    ("item.tomyen.grave_auger.both", "A drill and a shovel: bores stone and earth 3×3"),
    ("item.tomyen.grave_auger.night", "Works the night shift: digs earth instantly after dark"),
    ("item.tomyen.grave_auger.night_now", "Night shift: earth gives way instantly"),
    ("item.tomyen.grave_auger.bones", "Now and then turns up an old bone"),
    ("item.tomyen.hexed_rod", "Hexed Rod"),
    ("item.tomyen.hexed_rod.hexed", "Every fish it lands is hexed: eat one and see"),
    ("item.tomyen.hexed_rod.harder", "Its fish fight a step harder"),
    ("item.tomyen.hexed_catch", "Hexed %s"),
    ("item.tomyen.hexed_catch.lore", "A blessing or a curse, once eaten"),
    ("hud.tomyen.hexed_catch.might", "The hex favours you: strength floods in"),
    ("hud.tomyen.hexed_catch.iron_scales", "The hex favours you: your skin hardens like scales"),
    ("hud.tomyen.hexed_catch.quicksilver", "The hex favours you: quick as a minnow"),
    ("hud.tomyen.hexed_catch.deep", "The hex favours you: the deep takes you in"),
    ("hud.tomyen.hexed_catch.ember", "The hex favours you: fire can't touch you"),
    ("hud.tomyen.hexed_catch.fortune", "The hex favours you: luck turns your way"),
    ("hud.tomyen.hexed_catch.hag", "The hag laughs: you're cursed"),
    ("hud.tomyen.hexed_catch.rot", "It was rotten through"),
    ("hud.tomyen.hexed_catch.float", "Your feet leave the ground..."),
    ("hud.tomyen.hexed_catch.murk", "The bog's murk fills your eyes"),
    ("hud.tomyen.hexed_catch.hollow", "A hollow hunger takes you"),
    ("hud.tomyen.hexed_catch.wither", "Something withers inside you"),
    ("item.tomyen.whiskerline_rod", "Whiskerline Rod"),
    ("item.tomyen.whiskerline_rod.cast", "Casts much further"),
    ("item.tomyen.whiskerline_rod.hook", "Cast at a creature or a boat to hook it"),
    ("item.tomyen.whiskerline_rod.reel", "Use while it's hooked to reel it in"),
    ("item.tomyen.writ_of_absolution", "Writ of Absolution"),
    ("item.tomyen.writ_of_absolution.curse", "Use to lift a Hag's Curse and ward off the next"),
    ("item.tomyen.writ_of_absolution.debt", "Otherwise: pushes your debt 3 days back, and calls off the collectors"),
    ("message.tomyen.writ_of_absolution.curse", "Absolved: the curse is lifted, and you're warded for %s seconds."),
    ("message.tomyen.writ_of_absolution.extended", "Absolved: your repayment is due in %s."),
    ("message.tomyen.writ_of_absolution.called_off", "Absolved: the collectors are called off. Your repayment is due in %s."),
    ("message.tomyen.writ_of_absolution.full", "Your deadline is as far off as any writ can put it"),
    ("message.tomyen.writ_of_absolution.nothing", "You have nothing to be absolved of"),
)


def write_data():
    import fusion_data
    tag = fusion_data.merge_tag
    tag(DATA, "minecraft/tags/item/enchantable/durability.json", ["tomyen:barbel_lash", "tomyen:hexed_rod", "tomyen:whiskerline_rod"])
    tag(DATA, "minecraft/tags/item/enchantable/melee_weapon.json", ["tomyen:barbel_lash"])
    tag(DATA, "minecraft/tags/item/enchantable/fishing.json", ["tomyen:hexed_rod", "tomyen:whiskerline_rod"])
    tag(DATA, "minecraft/tags/item/enchantable/vanishing.json", ["tomyen:hexed_rod", "tomyen:whiskerline_rod"])
    tag(DATA, "c/tags/item/tools/fishing_rod.json", ["tomyen:hexed_rod", "tomyen:whiskerline_rod"])
    tag(DATA, "tomyen/tags/item/drills.json", ["tomyen:grave_auger"])
    tag(DATA, "tomyen/tags/item/repairs_grave_auger.json", ["tomyen:steel_ingot"])
    tag(DATA, "tomyen/tags/block/mineable/grave_auger.json", ["#minecraft:mineable/pickaxe", "#minecraft:mineable/shovel"])
    path = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(path, encoding="utf-8"), object_pairs_hook=OrderedDict)
    lang.update(LANG)
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")


# ============================================================================================================ main

if __name__ == "__main__":
    built = {}
    built.update(barbel_lash())
    lash_px, lw, lh = built.pop("lash")
    built.update(grave_auger())
    built.update(build_rod("hexed_rod", hexed_kit(), hexed_body, hexed_extras, "line"))
    built.update(build_rod("whiskerline_rod", whisker_kit(), whisker_body, whisker_extras, "line"))
    writ_px = writ()
    write_data()
    if PREVIEW:
        import preview_fusion
        sheet = {}
        for key, (K, els, displays) in built.items():
            sheet[key] = (K, els, displays)
        sheet["sprites"] = ("sprites", [(writ_px, 16, 16, 12), (lash_px, lw, lh, 6)])
        preview_fusion.items(sheet, PREVIEW, "fusion_tools")
