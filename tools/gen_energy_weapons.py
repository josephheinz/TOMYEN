"""Builds the energy weapons' assets: the Laser Rifle, the Railgun and the Ray Gun (each with the glowing parts that
mymod:energy_glow lights up as it heats or charges), the Weapon Station parts (Laser Emitter, Laser Rifle Housing,
Railgun Rails), and the Copper Coil, Uranium Power Cell and Rail Slug sprites; the crafting recipes for coils and cells
(with recipe-book advancements), the laser and railgun damage types, the synthesised sounds (laser shot, railgun charge
and shot, the alien zap; with ffmpeg on the path) and the re-pitched vanilla ones, and language entries.

Model space is in pixels: each gun's bore runs along z at x = 8 with the muzzle toward -z, +x its right. GunDisplay,
GunHands and GunAim mirror the first-person transforms, grip points and sight printed at the end, so move them
together. Clean flat paint, one smooth ramp per material, no speckle. The Railgun and its glow layers are also checked
for z-fighting in world space (tools/coplanar.py), whatever their cubes' rotations.

Usage: python3 tools/gen_energy_weapons.py src/main/resources/assets/mymod"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, rz, mul, apply, look, write, model, ref, range_dispatch, composite, display,
                    display_matrix, placed, centred, bounds_centre, DIAGONAL, png, mix, sprite)
from coplanar import check_elements

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
# Two heavy rails run forward from a stepped receiver with an open gap between them. The inner faces of both rails
# carry long raked combs of polished armature fins (copper above, silver below) over copper windings; at the back of
# the gap, seen through a window cut in the yoke that joins the rails, sits the Alien Reactor's ring core, glowing
# red-orange (brighter as the rails charge). Chunky end caps with ports at the muzzle, a holo sight on top, a pistol
# grip and a forward grip, a laser module under the lower rail, and a thick rounded shoulder stock with a gold strut.
# Gunmetal and graphite with crisp bright bevels, gold trim, copper discs.
R = Kit("railgun", "item/railgun", seed=1908, size=128)


def bands(k, rows):
    """A picture of horizontal bands, top to bottom: rows is a list of (fraction of the height, colour or (top, bottom)
    ramp)."""
    def f(x, y, w, h):
        t, start = (y + 0.5) / h, 0.0
        for i, (frac, c) in enumerate(rows):
            if t <= start + frac or i == len(rows) - 1:
                if isinstance(c, tuple):
                    return k.rgb(mix(c[0], c[1], min(1.0, max(0.0, (t - start) / frac))))
                return k.rgb(c)
            start += frac
    return f


def disc(k, rings, bg=None):
    """A round picture: rings is a list of (outer radius as a fraction of the half-width, colour or (inner, outer)
    ramp); outside the last ring it's bg (transparent if None)."""
    def f(x, y, w, h):
        r = math.hypot(x - (w - 1) / 2, y - (h - 1) / 2) / (w / 2)
        inner = 0.0
        for outer, c in rings:
            if r <= outer:
                if isinstance(c, tuple):
                    return k.rgb(mix(c[0], c[1], (r - inner) / max(1e-6, outer - inner)))
                return k.rgb(c)
            inner = outer
        return (0, 0, 0, 0) if bg is None else k.rgb(bg)
    return f


GM_HI, GM_TOP, GM_BOT, GM_LO = 0xA9B0B8, 0x5E656D, 0x40454C, 0x24272B   # gunmetal
GR_TOP, GR_BOT = 0x3A3E44, 0x272A2F                                    # graphite
GOLD_HI, GOLD_TOP, GOLD_BOT, GOLD_LO = 0xFFF0B0, 0xE8BE4E, 0xB48A28, 0x6E5216
CU_TOP, CU_BOT = 0xEFA06A, 0xA65A2A
AG_HI, AG_TOP, AG_BOT = 0xFFFFFF, 0xE6EAEE, 0x9EA6B0                   # polished silver

# Row 0: the metals.
R.swatch("gunmetal", 0, 0, 2, 2, ramp(R, GM_TOP, GM_BOT))
R.swatch("gunmetal_panel", 2, 0, 4, 2, bands(R, [(0.07, GM_HI), (0.83, (GM_TOP, GM_BOT)), (0.10, GM_LO)]), picture=True)
R.swatch("graphite", 4, 0, 6, 2, ramp(R, GR_TOP, GR_BOT))
R.swatch("graphite_panel", 6, 0, 8, 2, bands(R, [(0.08, 0x7C838C), (0.82, (GR_TOP, GR_BOT)), (0.10, 0x18191C)]), picture=True)
R.swatch("edge", 8, 0, 10, 2, ramp(R, 0xC8CFD6, 0x969EA8))
R.swatch("gold", 10, 0, 12, 2, ramp(R, GOLD_TOP, GOLD_BOT))
R.swatch("gold_panel", 12, 0, 14, 2, bands(R, [(0.12, GOLD_HI), (0.76, (GOLD_TOP, GOLD_BOT)), (0.12, GOLD_LO)]), picture=True)
R.swatch("copper", 14, 0, 16, 2, ramp(R, CU_TOP, CU_BOT))
# Row 1: fins, rubber, discs, ports, the sight's glass, the laser's lens, vents.
R.swatch("silver", 0, 2, 2, 4, ramp(R, AG_TOP, AG_BOT))
R.swatch("fin_face", 2, 2, 4, 4, bands(R, [(0.10, AG_HI), (0.80, (AG_TOP, AG_BOT)), (0.10, 0x6A727C)]), picture=True)
# Polished copper for the upper fins: brighter than the windings, so they still shine facing down.
CU_HI_TOP, CU_HI_BOT = 0xFFC898, 0xD27A40
R.swatch("copper_fin", 14, 8, 16, 10, bands(R, [(0.10, 0xFFF0E0), (0.80, (CU_HI_TOP, CU_HI_BOT)), (0.10, 0x7A3C18)]), picture=True)
R.swatch("copper_hi", 8, 10, 10, 12, ramp(R, CU_HI_TOP, CU_HI_BOT))
R.swatch("rubber", 4, 2, 6, 4, ramp(R, 0x3A3D42, 0x26282C))
R.swatch("copper_disc", 6, 2, 8, 4, disc(R, [(0.28, (0xFFD2A8, 0xE39256)), (0.42, 0x5A2E14), (0.82, (0xEFA06A, 0xA65A2A)),
                                               (1.0, 0x2A2C30)], bg=GR_BOT), picture=True)
R.swatch("port", 8, 2, 10, 4, disc(R, [(0.22, (0x8A949E, 0x50575F)), (0.36, 0x16171A), (0.62, (0x2E3238, 0x1E2024)),
                                        (0.74, 0x9AA2AC), (1.0, (GR_TOP, GR_BOT))], bg=GR_BOT), picture=True)


def reticle(x, y, w, h):
    """The holo sight's glass: a thin dark frame, clear, an amber ring and dot in the middle."""
    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
        return R.rgb(0x1C1D20)
    r = math.hypot(x - (w - 1) / 2, y - (h - 1) / 2)
    if r <= 0.8 or 3.2 <= r <= 4.0:
        return R.rgb(0xFFB040)
    return (0, 0, 0, 0)


R.swatch("reticle", 10, 2, 12, 4, reticle, picture=True)
R.swatch("laser_lens", 12, 2, 14, 4, disc(R, [(0.35, (0xFFF0E0, 0xFF6040)), (0.75, (0xFF4020, 0xA01808)), (1.0, 0x16171A)], bg=0x16171A),
         picture=True)
R.swatch("vent", 14, 2, 16, 4, lambda x, y, w, h: R.rgb(0xB9C0C8 if (x + y) % 4 < 2 else 0x1C1D20), picture=True)
# Row 2: the core's glow, idle and charging, its dark eye, the gold chevrons, a knurled ring, the receiver's slits.
CORE = [(0xF04818, 0x9C1E08), (0xFF7A2A, 0xE0400E), (0xFFB050, 0xFF6A1E), (0xFFF4D8, 0xFFB050)]
for i, (top, bottom) in enumerate(CORE):
    R.swatch("core_%d" % i, 2 * i, 4, 2 * i + 2, 6, ramp(R, top, bottom))
R.swatch("core_eye", 8, 4, 10, 6, disc(R, [(0.42, (0x0E0F11, 0x1E0A06)), (0.56, (0x5A1606, 0x8A2408))]), picture=True)


def chevron(x, y, w, h):
    """Two gold chevrons pointing forward (toward u = 0)."""
    t = abs((y + 0.5) / h - 0.5) * 2              # 0 at the middle row, 1 at the top and bottom
    for start in (0.12, 0.52):
        u = (x + 0.5) / w - start - 0.18 * t
        if 0 <= u <= 0.2:
            return R.rgb(mix(GOLD_HI, GOLD_BOT, u / 0.2 * 0.7 + 0.3 * t))
    return (0, 0, 0, 0)


R.swatch("chevron", 10, 4, 12, 6, chevron, picture=True)
R.swatch("knurl", 12, 4, 14, 6, lambda x, y, w, h: R.rgb(0x7E868F if (x // 2 + y // 2) % 2 else 0x2A2C30), picture=True)
R.swatch("dark", 14, 4, 16, 6, ramp(R, 0x1C1D20, 0x121315))


def arc(level):
    """A crackling arc between the rails, seen side on: a jagged white-hot line in an orange halo; clear elsewhere."""
    jag = random.Random(5)
    pts, u = [(0.0, 0.5)], 0.0
    while u < 0.95:
        u = min(1.0, u + jag.uniform(0.025, 0.07))
        pts.append((u, min(0.8, max(0.2, pts[-1][1] + jag.uniform(-0.22, 0.22)))))
    pts.append((1.0, 0.5))

    def f(x, y, w, h):
        u = (x + 0.5) / w
        for (u0, v0), (u1, v1) in zip(pts, pts[1:]):
            if u0 <= u <= u1:
                v = v0 + (v1 - v0) * (u - u0) / (u1 - u0)
                break
        d = abs((y + 0.5) - v * h)
        core, halo = (0.9, 2.2) if level else (0.6, 1.6)
        if d <= core:
            return R.rgb(0xFFFFFF if level else 0xFFE0B0)
        if d <= halo:
            return R.rgb(mix(0xFFC070, 0xFF5A1A, (d - core) / (halo - core)) if level else mix(0xFF9040, 0xE0400E, (d - core) / (halo - core)))
        return (0, 0, 0, 0)
    return f


R.swatch("arc_dim", 0, 6, 8, 8, arc(0), picture=True)
R.swatch("arc_hot", 8, 6, 16, 8, arc(1), picture=True)

def ring_band(level, along_u):
    """A core ring's rim: brightest round its middle, darker toward its front and back edges (across u on the faces
    whose u runs along the bore, across v on the others)."""
    top, bottom = CORE[level]
    edge = mix(bottom, 0x3A0C04, 0.45)

    def f(x, y, w, h):
        t = (x + 0.5) / w if along_u else (y + 0.5) / h
        d = abs(t - 0.5) * 2
        return R.rgb(mix(top, bottom, d / 0.7) if d < 0.7 else mix(bottom, edge, (d - 0.7) / 0.3))
    return f


for i in range(4):
    R.swatch("ring_u_%d" % i, 2 * i, 12, 2 * i + 2, 14, ring_band(i, True), picture=True)
    R.swatch("ring_v_%d" % i, 8 + 2 * i, 12, 10 + 2 * i, 14, ring_band(i, False), picture=True)


def cap_face(w_px, h_px, r_px, port_at, holes_at, port_r, gold=False):
    """A muzzle cap's front, as one picture over the whole face: graphite with the bevelled corners cut away (clear), a
    round port (a bright rim, a dark gap, a lens) and two small dark holes. Positions are fractions of the face."""
    def f(x, y, w, h):
        fx, fy = (x + 0.5) / w, (y + 0.5) / h
        ex, ey = min(fx, 1 - fx) * w_px, min(fy, 1 - fy) * h_px
        if ex + ey < r_px - 0.05:
            return (0, 0, 0, 0)
        px, py = fx * w_px, fy * h_px
        d = math.hypot(px - port_at[0] * w_px, py - port_at[1] * h_px) / port_r
        if d <= 1.0:
            for outer, c in ((0.3, (0xC8D0D8, 0x7A848E)), (0.45, 0x2A2E34), (0.62, 0x101113), (0.8, (0x3A3E44, 0x24272B)), (1.0, 0xB8C0C8)):
                if d <= outer:
                    return R.rgb(c if not isinstance(c, tuple) else mix(c[0], c[1], d / outer))
        for hx, hy in holes_at:
            dh = math.hypot(px - hx * w_px, py - hy * h_px)
            if dh <= 0.32:
                return R.rgb(0x101113)
            if dh <= 0.42:
                return R.rgb(0x8A929C)
        if gold and (fy > 0.86 or (fy > 0.55 and min(fx, 1 - fx) * w_px < 0.32)):
            return R.rgb(mix(GOLD_HI, GOLD_BOT, min(1.0, abs(fy - 0.9) * 4 + abs(fx - 0.5))))   # the gold U-band
        if ex + ey < r_px + 0.18:
            return R.rgb(0x8E969F)                    # a bright line along the bevels' edge
        return R.rgb(mix(0x464B52, 0x2A2D32, fy))
    return f


R.swatch("upper_face", 0, 8, 4, 12, cap_face(3.4, 3.5, 0.7, (0.5, 0.42), ((0.25, 0.83), (0.75, 0.83)), 1.15), picture=True)
R.swatch("lower_face", 4, 8, 8, 12, cap_face(3.8, 4.2, 0.8, (0.5, 0.62), ((0.27, 0.2), (0.73, 0.2)), 1.2, gold=True), picture=True)
R.swatch("rubber_panel", 10, 8, 12, 10, bands(R, [(0.06, 0x6A6E75), (0.88, (0x3A3D42, 0x26282C)), (0.06, 0x141517)]), picture=True)


def inset(x, y, w, h):
    """A recessed panel: a lit lower-right lip, a shadowed upper-left one, a flat darker field."""
    if x == 0 or y == 0:
        return R.rgb(0x16171A)
    if x == w - 1 or y == h - 1:
        return R.rgb(0x8A929C)
    return R.rgb(mix(0x34383E, 0x2A2D32, y / (h - 1)))


R.swatch("inset", 12, 8, 14, 10, inset, picture=True)
R.swatch("armour", 8, 8, 10, 10, bands(R, [(0.05, 0x9AA2AC), (0.9, (0x464B52, 0x2C2F34)), (0.05, 0x16171A)]), picture=True)
R.save(TEX)

RX0, RX1 = 6.7, 9.3            # the upper rail, side to side
LX0, LX1 = 6.5, 9.5            # the lower rail
UY0, UY1 = 11.4, 14.0          # the upper rail, bottom and top
LY0, LY1 = 4.0, 7.0            # the lower rail
MUZZLE = -15.8
CAP_BACK = -13.2               # the muzzle caps' back
RAIL_BACK = 0.4                # where the rails run into the yoke
YOKE_BACK = 4.4                # the yoke's back, the receiver's front
FIN_Z = (-12.8, -0.2)          # the fin combs
FIN_PITCH, FIN_T, FIN_D = 0.62, 0.26, 1.25
FIN_RAKE = 20                  # the fins lean back, their tips toward the receiver
CORE_Y, CORE_R = 9.2, 1.72
CORE_RINGS = ((1.1, 1.6), (2.05, 2.55), (3.0, 3.5))
ARMOUR = ((4.4, 4.95), (5.5, 6.05), (6.6, 7.15))
SLITS = ((4.95, 5.5), (6.05, 6.6))
STOCK = 10.4                   # the receiver's back, the stock's front
BUTT = 15.8
R_GRIP = (8.0, 2.9, 9.6)
R_SUPPORT = (8.0, 2.6, -5.8)
R_SIGHT = (8.0, 15.6, 5.4)     # the middle of the holo sight's glass
R_SIGHT_MOUNT = (4.6, 14.4)    # the sight base's front (z) and the top it stands on (y)


def bevel(out, x0, y0, z0, x1, y1, z1, r, side, top=None, bottom=None, cap=None, edge="edge", name=None, caps=(True, True),
          lower=True):
    """A box along z with its long edges bevelled at 45 degrees by r (the lower two only if lower), the bevels in a
    bright edge material so every long edge catches a crisp highlight. Faces hidden inside are left off."""
    cap = cap or side
    ends = dict(north=cap if caps[0] else None, south=cap if caps[1] else None)
    R.box(out, x0 + r, y0, z0, x1 - r, y1, z1, side, name=name, up=top or side, down=bottom or side, east=None, west=None, **ends)
    sy0 = y0 + r if lower else y0
    R.box(out, x0, sy0, z0, x0 + r, y1 - r, z1, side, up=None, down=None if lower else bottom or side, east=None, **ends)
    R.box(out, x1 - r, sy0, z0, x1, y1 - r, z1, side, up=None, down=None if lower else bottom or side, west=None, **ends)
    h = r / math.sqrt(2)
    for cx, cy in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
        if cy < (y0 + y1) / 2 and not lower:
            continue
        R.box(out, cx - h, cy - h, z0 + 0.02, cx + h, cy + h, z1 - 0.02, edge, rot=("z", 45, (cx, cy, z0)), north=None, south=None)


def both_sides(out, x0, x1, y0, z0, y1, z1, mat, emit=False, gap=0.03):
    """The same decal on the west face at x0 and the east face at x1, standing gap proud."""
    R.sticker(out, x0 - gap - 0.01, y0, z0, x0 - gap, y1, z1, mat, "west", emit=emit)
    R.sticker(out, x1 + gap, y0, z0, x1 + gap + 0.01, y1, z1, mat, "east", emit=emit)


def rail_assembly():
    """Both rails with their fin combs and muzzle caps: the Railgun Rails part, and the front of the gun."""
    out = []
    # The rails: heavy bevelled bars, the panel picture giving each side a lit top edge and a dark foot.
    bevel(out, RX0, UY0, CAP_BACK, RX1, UY1, RAIL_BACK, 0.5, "gunmetal_panel", top="gunmetal", bottom="graphite",
          name="upper_rail", caps=(False, True))
    bevel(out, LX0, LY0, CAP_BACK, LX1, LY1, RAIL_BACK, 0.5, "gunmetal_panel", top="graphite", bottom="graphite",
          name="lower_rail", caps=(False, True), lower=False)
    # The copper windings the fins stand on.
    R.box(out, 7.3, UY0 - 0.4, FIN_Z[0] - 0.3, 8.7, UY0, FIN_Z[1] + 0.3, "copper", name="upper_winding", up=None)
    R.box(out, 7.2, LY1, FIN_Z[0] - 0.3, 8.8, LY1 + 0.4, FIN_Z[1] + 0.3, "copper", name="lower_winding", down=None)
    # The fin combs: polished plates closely spaced like a radiator, reaching halfway into the gap and raked back;
    # copper above, silver below.
    n = int((FIN_Z[1] - FIN_Z[0] - FIN_T) / FIN_PITCH) + 1
    for i in range(n):
        z = FIN_Z[0] + i * FIN_PITCH
        mid = z + FIN_T / 2
        R.box(out, 7.0, UY0 - FIN_D, z, 9.0, UY0 + 0.1, z + FIN_T, "copper_hi", north="copper_fin", south="copper_fin", up=None,
              rot=("x", -FIN_RAKE, (8.0, UY0, mid)), name="upper_fin" if i == 0 else None)
        R.box(out, 6.9, LY1 - 0.1, z, 9.1, LY1 + FIN_D, z + FIN_T, "silver", north="fin_face", south="fin_face", down=None,
              rot=("x", FIN_RAKE, (8.0, LY1, mid)), name="lower_fin" if i == 0 else None)
    # The muzzle caps: chunky and bevelled, their fronts one picture each (ports, holes); gold trim under the lower one.
    bevel(out, 6.3, 11.0, MUZZLE, 9.7, 14.5, CAP_BACK, 0.7, "graphite_panel", top="gunmetal", bottom="graphite",
          cap="graphite", name="upper_cap", caps=(False, True))
    R.sticker(out, 6.3, 11.0, MUZZLE, 9.7, 14.5, MUZZLE + 0.01, "upper_face", "north", name="upper_face")
    bevel(out, 6.1, 3.3, MUZZLE, 9.9, 7.5, CAP_BACK, 0.8, "graphite_panel", top="graphite", bottom="graphite",
          cap="graphite", name="lower_cap", caps=(False, True))
    R.sticker(out, 6.1, 3.3, MUZZLE, 9.9, 7.5, MUZZLE + 0.01, "lower_face", "north", name="lower_face")
    R.box(out, 6.5, 2.9, MUZZLE + 0.25, 9.5, 3.3, CAP_BACK - 0.3, "gold", north="gold_panel", east="gold_panel", west="gold_panel",
          up=None, name="cap_trim")
    both_sides(out, 6.3, 9.7, 12.4, MUZZLE + 0.4, 12.75, CAP_BACK - 0.4, "gold")
    return out


def core(level=0, grow=0.0):
    """The reactor's ring core at the back of the gap: three glowing rings."""
    out = []
    for z0, z1 in CORE_RINGS:
        R.rod(out, z0 - grow, z1 + grow, CORE_R + grow, "ring_v_%d" % level, along="ring_u_%d" % level, cap="core_%d" % level,
              cy=CORE_Y, emit=True, name="core_ring")
    return out


def railgun_body():
    out = []
    # The yoke: where the rails meet, a frame round the core with a window cut through each side.
    R.box(out, 5.9, UY0, RAIL_BACK, 10.1, 14.2, YOKE_BACK, "graphite_panel", up="gunmetal", down="graphite", north="graphite",
          south=None, name="yoke_top")
    R.box(out, 5.9, 3.8, RAIL_BACK, 10.1, LY1, YOKE_BACK, "graphite_panel", up="graphite", down="graphite", north="graphite",
          south=None, name="yoke_bottom")
    for x0, x1 in ((5.9, 6.25), (9.75, 10.1)):
        R.box(out, x0, LY1, RAIL_BACK, x1, UY0, RAIL_BACK + 0.5, "gunmetal", up=None, down=None, south=None, name="yoke_post")
    R.box(out, 5.9, LY1, YOKE_BACK - 0.7, 10.1, UY0, YOKE_BACK, "dark", up=None, down=None, south=None, east="graphite",
          west="graphite", name="yoke_back")
    R.box(out, 7.3, CORE_Y - 0.7, CORE_RINGS[0][0] + 0.1, 8.7, CORE_Y + 0.7, YOKE_BACK - 0.7, "dark", name="core_spindle")
    R.sticker(out, 8 - CORE_R, CORE_Y - CORE_R, CORE_RINGS[0][0] - 0.11, 8 + CORE_R, CORE_Y + CORE_R, CORE_RINGS[0][0] - 0.1,
              "core_eye", "north", emit=True, name="core_eye")
    out += core()
    # Gold chevrons on the lower rail's flanks by the yoke, pointing down the rails.
    both_sides(out, LX0, LX1, 4.5, RAIL_BACK - 2.6, 6.5, RAIL_BACK - 0.4, "chevron")
    # The receiver: segmented armour rings in front with glowing slits between them, then the stepped rear block.
    bevel(out, 6.1, 4.0, YOKE_BACK, 9.9, 13.8, ARMOUR[-1][1] + 0.3, 0.6, "gunmetal_panel", top="gunmetal", bottom="graphite",
          name="receiver_front", caps=(False, False))
    for i, (z0, z1) in enumerate(ARMOUR):
        R.box(out, 5.8, 3.6, z0, 10.2, 14.2, z1, "armour", up="graphite", down="graphite", north="graphite", south="graphite",
              name="armour_ring" if i == 0 else None)
    for z0, z1 in SLITS:
        both_sides(out, 6.1, 9.9, 5.2, z0 + 0.12, 12.6, z1 - 0.12, "core_0", emit=True)
    bevel(out, 6.3, 4.4, ARMOUR[-1][1], 9.7, 13.4, STOCK, 0.7, "gunmetal_panel", top="gunmetal", bottom="graphite",
          cap="graphite", name="receiver_rear", caps=(False, False), lower=False)
    both_sides(out, 6.3, 9.7, 4.7, 7.6, 5.5, STOCK - 0.4, "gold")
    both_sides(out, 6.3, 9.7, 6.2, 7.8, 11.4, STOCK - 0.6, "inset")
    # The stock: a thick rounded shoulder hump, a gold-sided strut running down to the grip's heel, a rubber pad.
    bevel(out, 6.4, 10.0, STOCK, 9.6, 14.6, BUTT, 1.4, "graphite_panel", top="graphite", bottom="graphite", cap="graphite",
          name="stock", caps=(False, False))
    R.box(out, 7.0, 14.59, STOCK + 1.0, 9.0, 15.0, BUTT - 0.8, "graphite", down=None, name="stock_hump")
    both_sides(out, 6.4, 9.6, 12.1, STOCK + 0.6, 12.4, BUTT - 0.6, "gold")
    # Swept up from the receiver's top into the hump.
    R.box(out, 7.0, 12.9, STOCK - 1.8, 9.0, 13.7, STOCK + 1.0, "graphite", east="graphite_panel", west="graphite_panel",
          rot=("x", -24, (8.0, 13.3, STOCK - 1.8)), name="stock_sweep")
    R.box(out, 7.1, 0.4, STOCK + 0.2, 8.9, 1.6, STOCK + 8.8, "gunmetal", east="gold_panel", west="gold_panel",
          rot=("x", -45, (8.0, 1.0, STOCK + 0.2)), name="stock_strut")
    R.box(out, 6.6, 6.0, BUTT, 9.4, 14.4, BUTT + 1.0, "rubber", name="butt_pad")
    # Pistol grip, trigger and guard.
    R.box(out, 7.15, -0.6, 8.2, 8.85, 4.4, 10.4, "rubber", east="rubber_panel", west="rubber_panel",
          rot=("x", -14, (8.0, 4.4, 9.3)), name="grip")
    R.box(out, 7.75, 3.0, 7.4, 8.25, 4.4, 7.8, "edge", name="trigger")
    R.box(out, 7.6, 2.0, 6.0, 8.4, 2.4, 8.9, "gunmetal", name="trigger_guard")
    R.box(out, 7.6, 2.4, 6.0, 8.4, 4.4, 6.4, "gunmetal", name="trigger_guard_front")
    # Forward grip under the lower rail, raked forward a touch.
    R.box(out, 7.2, 0.8, -6.6, 8.8, LY0, -5.0, "rubber", east="rubber_panel", west="rubber_panel",
          rot=("x", 8, (8.0, LY0, -5.8)), name="forward_grip")
    R.box(out, 7.0, 0.3, -6.8, 9.0, 0.8, -4.8, "gunmetal", rot=("x", 8, (8.0, LY0, -5.8)), name="forward_grip_foot")
    # The laser module under the lower rail's front: a block, a knurled collar and a red lens.
    R.box(out, 7.1, 1.6, -12.8, 8.9, LY0, -9.8, "graphite_panel", up=None, north="graphite", name="laser_module")
    R.box(out, 7.35, 2.0, -13.6, 8.65, 3.3, -12.8, "knurl", north="graphite", name="laser_collar")
    R.sticker(out, 7.6, 2.25, -13.64, 8.4, 3.05, -13.63, "laser_lens", "north", emit=True, name="laser_lens")
    # The lower rail's flanks: copper discs and a vent grille; a gold stripe along the upper rail.
    for z in (-11.4, -9.2):
        both_sides(out, LX0, LX1, 4.7, z, 6.3, z + 1.6, "copper_disc")
    both_sides(out, LX0, LX1, 4.2, -7.6, 4.5, -3.4, "vent")
    both_sides(out, RX0, RX1, 12.3, -12.6, 12.55, -0.4, "gold")
    # The holo sight on the receiver: a base, a chunky hood round the glass, the emitter behind.
    sz, top = R_SIGHT_MOUNT
    gz = R_SIGHT[2]
    R.box(out, 7.0, 13.8, sz, 9.0, top, sz + 3.6, "graphite", name="sight_base", down=None)
    R.box(out, 6.6, top, gz - 0.4, 7.2, 16.6, gz + 0.4, "gunmetal", name="sight_hood", down=None)
    R.box(out, 8.8, top, gz - 0.4, 9.4, 16.6, gz + 0.4, "gunmetal", down=None)
    R.box(out, 6.6, 16.6, gz - 0.4, 9.4, 17.1, gz + 0.4, "edge")
    R.box(out, 7.2, top, gz + 1.2, 8.8, 15.2, sz + 3.6, "graphite_panel", up="graphite", name="sight_emitter")
    R.box(out, 7.2, top + 0.2, gz - 0.05, 8.8, 16.6, gz + 0.05, "reticle", name="sight_glass", up=None, down=None, east=None, west=None)
    return out


def railgun_glow(level):
    """Charging: the core's rings burn brighter (shells just over them), the slits with them, and an arc jumps the gap."""
    out = core(level, grow=0.05)
    for z0, z1 in SLITS:
        both_sides(out, 6.1, 9.9, 5.2, z0 + 0.12, 12.6, z1 - 0.12, "core_%d" % level, emit=True, gap=0.06)
    if level >= 2:
        R.box(out, 8.0, 8.3, FIN_Z[0], 8.0, 10.1, CORE_RINGS[0][0] - 0.1, "arc_dim" if level == 2 else "arc_hot", emit=True,
              north=None, south=None, up=None, down=None)
    return out


R_CREDIT = "mymod railgun - generated by tools/gen_energy_weapons.py"
railgun = rail_assembly() + railgun_body()
check("railgun", railgun)
R_GLOWS = [railgun_glow(1), railgun_glow(2), railgun_glow(3)]
for i, layer in enumerate(R_GLOWS):
    check_elements(f"railgun+glow_{i}", railgun, layer)
# Turned a touch toward the middle of the screen and set a little further in, so the rails and the gap between them
# show in first person.
railgun_displays = gun_displays(R_GRIP, 0.46, (FP_GRIP_TARGET[0] - 1.0, FP_GRIP_TARGET[1] + 0.3, FP_GRIP_TARGET[2]), yaw=6)
# The icon, frame and dropped item at three-quarters, like a product shot: the left side and the muzzle toward the
# viewer, seen from a little above, the muzzle raised toward the slot's upper left to fill it.
R_ICON = mul(rz(-35), display_matrix([10, 115, 0]))
railgun_displays.update(still_displays(railgun, 0.6, rot=look(apply(R_ICON, [0, 0, -1]), apply(R_ICON, [0, 1, 0]))))
write(os.path.join(MODELS, "railgun.json"), model(R, railgun, railgun_displays, credit=R_CREDIT))
write(os.path.join(ITEMS, "railgun.json"), {"model": glow_levels(R, "railgun", R_GLOWS, railgun_displays, [0.15, 0.5, 0.9], R_CREDIT)})
rail_part = rail_assembly()
check("railgun_rails", rail_part)
check_elements("railgun_rails", rail_part)
write(os.path.join(MODELS, "railgun_rails.json"), model(R, rail_part, part_displays(rail_part, 0.5), credit=R_CREDIT))
write(os.path.join(ITEMS, "railgun_rails.json"), {"model": ref("railgun_rails")})
report("railgun", railgun_displays, R_GRIP, R_SUPPORT, f"; sight glass {R_SIGHT}, mount front {R_SIGHT_MOUNT[0]} top {R_SIGHT_MOUNT[1]}")
print("railgun cubes:", len(railgun), "+ glow", [len(g) for g in R_GLOWS])


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
