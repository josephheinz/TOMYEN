"""Builds the RPG-7 rocket launcher, its PG-7 rockets (plain, homing and napalm) and the homing guidance module.

Writes the launcher's item models (the bare launcher, plus overlays for a seated warhead and for a fitted seeker pod,
which its item definition stacks), the two Weapon Station parts, the rocket models (as an item, and in flight with
its fins out and the motor lit), the guidance module, one shared texture atlas, and the PGO-7 sight's reticle.

Model space is in pixels. The bore runs along z at x = 8, y = 9 with the muzzle toward -z; +x is the launcher's right.
A rocket is modelled where it sits when seated in the launcher, so the client can slide one along the bore during a
reload (LauncherAnimation) and the warhead overlay is the front of the same rocket. GunDisplay and GunHands mirror
the first-person display transform and the grip points below, so move them together."""
import json, math, os, random, struct, sys, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from zfight import check as zfight_check

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
rnd = random.Random(7)

BX, BY = 8.0, 9.0        # bore axis
MUZZLE_Z = -2.0           # front of the tube; a seated rocket's motor goes in here
GRIP = (8.0, 5.0, 18.6)   # where the shooting hand holds the pistol grip
SUPPORT = (8.0, 7.6, 10.2)  # where the off hand holds the front heat guard (from below)


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rgb(hx, a=255, jitter=0):
    r, g, b = (hx >> 16) & 255, (hx >> 8) & 255, hx & 255
    d = rnd.randint(-jitter, jitter) if jitter else 0
    c = lambda v: max(0, min(255, v + d))
    return (c(r), c(g), c(b), a)


def mix(a, b, t):
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    return sum(int(ca[i] + (cb[i] - ca[i]) * t) << s for i, s in enumerate((16, 8, 0)))


# ----------------------------------------------------------------------------------------------- texture atlas
# 64x64 texels; swatch rectangles are in model UV units (0-16), four texels each.
W = 64
atlas = [(0, 0, 0, 0)] * (W * W)
SW = {}


def swatch(name, u0, v0, u1, v1, f, picture=False):
    """f(x, y, w, h) -> colour for texel (x, y) of a w x h texel swatch. Picture swatches map whole onto a face."""
    SW[name] = ((u0, v0, u1, v1), picture)
    w, h = (u1 - u0) * 4, (v1 - v0) * 4
    for y in range(h):
        for x in range(w):
            atlas[(v0 * 4 + y) * W + u0 * 4 + x] = f(x, y, w, h)


# Blued steel with an olive cast, worn lighter where hands rub it.
STEEL, STEEL_DARK, STEEL_WORN = 0x3B3F38, 0x262924, 0x5D6258
WOOD, WOOD_DARK, WOOD_HI = 0x9A5A2C, 0x6E3D1C, 0xB8743D
BAKELITE, BAKELITE_DARK = 0x4A2618, 0x2E160E
OLIVE, OLIVE_DARK, OLIVE_HI = 0x5E6B3A, 0x434D29, 0x7A8850
MOTOR, BOOSTER = 0x7C8079, 0xA89A6C
BRASS = 0xB89545
YELLOW = 0xE8C21E

swatch("steel", 0, 0, 8, 2, lambda x, y, w, h: rgb(STEEL_WORN if rnd.random() < 0.05 else STEEL, jitter=4))
swatch("steel_dark", 0, 10, 4, 12, lambda x, y, w, h: rgb(STEEL_DARK, jitter=3))
swatch("steel_worn", 4, 10, 6, 12, lambda x, y, w, h: rgb(STEEL_WORN if rnd.random() < 0.6 else STEEL, jitter=5))


def wood(along_u):
    def f(x, y, w, h):
        a, b = (y, x) if along_u else (x, y)
        streak = (a * 5 + int(2.5 * math.sin(b * 0.3 + a * 1.7))) % 6
        return rgb(WOOD_DARK if streak == 0 else WOOD_HI if streak == 3 else WOOD, jitter=4)
    return f


swatch("wood_u", 8, 0, 16, 2, wood(True))
swatch("wood_v", 0, 2, 2, 10, wood(False))
swatch("wood_end", 2, 2, 4, 4, lambda x, y, w, h: rgb(WOOD_DARK if int(math.hypot(x - 3.5, y - 3.5)) % 2 else WOOD, jitter=3))
# Bakelite pistol grip with a checkered panel.
swatch("bakelite", 4, 2, 6, 4, lambda x, y, w, h: rgb(BAKELITE_DARK if (x + y) % 2 == 0 else BAKELITE, jitter=3))
swatch("bakelite_plain", 6, 2, 8, 4, lambda x, y, w, h: rgb(BAKELITE, jitter=3))
swatch("olive", 8, 2, 12, 4, lambda x, y, w, h: rgb(OLIVE_HI if rnd.random() < 0.04 else OLIVE, jitter=3))
swatch("olive_dark", 12, 2, 14, 4, lambda x, y, w, h: rgb(OLIVE_DARK, jitter=3))
swatch("olive_light", 14, 2, 16, 4, lambda x, y, w, h: rgb(OLIVE_HI, jitter=3))


def stencil(x, y, w, h):
    """Black stencilled lot markings on the warhead's olive paint: two short lines of 'text'."""
    if y == 3 and 3 <= x <= 12 and x % 3 != 2:
        return rgb(0x23271A)
    if y == 5 and 4 <= x <= 9 and x % 2 == 0:
        return rgb(0x23271A)
    return rgb(OLIVE, jitter=3)


swatch("stencil", 2, 4, 6, 6, stencil, picture=True)
swatch("brass", 6, 4, 8, 6, lambda x, y, w, h: rgb(0xE0C070 if x == 0 or y == 0 else BRASS, jitter=4))
swatch("motor", 8, 4, 12, 6, lambda x, y, w, h: rgb(MOTOR, jitter=4))
# The booster is a waxed paper cartridge.
swatch("booster", 12, 4, 16, 6, lambda x, y, w, h: rgb(0x8E8258 if x % 5 == 0 else BOOSTER, jitter=4))
swatch("black", 2, 6, 4, 8, lambda x, y, w, h: rgb(0x151515, jitter=2))
swatch("rubber", 4, 6, 6, 8, lambda x, y, w, h: rgb(0x1E1E1E if (x // 2 + y) % 2 else 0x262626))
swatch("glass", 6, 6, 8, 8, lambda x, y, w, h: rgb(0x9FD2FF if (x, y) in ((1, 1), (2, 1), (1, 2)) else 0x1F3550, jitter=3))
swatch("glow_red", 8, 6, 10, 8, lambda x, y, w, h: rgb(0xFFE0D0 if 3 <= x <= 4 and 3 <= y <= 4 else 0xFF2A1A))
swatch("glow_green", 10, 6, 12, 8, lambda x, y, w, h: rgb(0xC8FFC8 if 3 <= x <= 4 and 3 <= y <= 4 else 0x30FF50))
swatch("bore", 12, 6, 14, 8, lambda x, y, w, h: rgb(0x070707 if math.hypot(x - 3.5, y - 3.5) < 3.0 else 0x2A2C28), picture=True)
swatch("flame", 14, 6, 16, 8, lambda x, y, w, h: rgb(mix(0xFFF6C0, 0xFF7A10, math.hypot(x - 3.5, y - 3.5) / 5.0)))
# Seeker pod: dark composite housing with vent slots.
swatch("pod", 2, 8, 6, 10, lambda x, y, w, h: rgb(0x2F3530 if rnd.random() > 0.05 else 0x3A413B, jitter=3))
swatch("pod_panel", 6, 8, 10, 10, lambda x, y, w, h: rgb(0x1A1E1B if (y % 4 == 1 and 1 <= x <= w - 2) else 0x313833, jitter=2))
swatch("hazard", 10, 8, 14, 10, lambda x, y, w, h: rgb(0x111111 if ((x + y) // 2) % 2 else YELLOW), picture=True)
swatch("yellow", 14, 8, 16, 10, lambda x, y, w, h: rgb(YELLOW, jitter=6))


def screen(x, y, w, h):
    """The pod's little status display, facing the shooter: a crosshair and a level bar on dark green."""
    if x in (0, w - 1) or y in (0, h - 1):
        return rgb(0x0B140D)
    if (x == 3 and 1 <= y <= 4) or (y == 2 and 1 <= x <= 5) or (y == 6 and 1 <= x <= 5):
        return rgb(0x7DFF8C)
    return rgb(0x10301A)


swatch("screen", 6, 10, 8, 12, screen, picture=True)
swatch("seeker_glass", 8, 10, 10, 12, lambda x, y, w, h: rgb(0x6A5A8A if (x, y) in ((2, 2), (3, 2)) else 0x18121F, jitter=2))


# The napalm rocket's paint: flat colours in a corner of the atlas nothing else uses (no random numbers drawn, so
# the rest of the atlas and every UV cut come out as before).
swatch("napalm", 10, 10, 14, 12, lambda x, y, w, h: (0x7A, 0x24, 0x16, 255))
swatch("napalm_band", 14, 10, 16, 12, lambda x, y, w, h: (0xE8, 0x70, 0x1E, 255))
swatch("napalm_dark", 0, 12, 4, 14, lambda x, y, w, h: (0x4A, 0x14, 0x0C, 255))
swatch("plain_motor", 4, 12, 8, 14, lambda x, y, w, h: (0x7C, 0x80, 0x79, 255))
swatch("plain_booster", 8, 12, 12, 14, lambda x, y, w, h: (0xA8, 0x9A, 0x6C, 255))
swatch("plain_steel", 12, 12, 16, 14, lambda x, y, w, h: (0x3B, 0x3F, 0x38, 255))
png(os.path.join(TEX, "item/rpg7.png"), W, W, atlas)

# ----------------------------------------------------------------------------------------------- elements
FACES = ["north", "east", "south", "west", "up", "down"]
DIRS = {"north": ("x", "y"), "south": ("x", "y"), "east": ("z", "y"), "west": ("z", "y"), "up": ("x", "z"), "down": ("x", "z")}


def uv_for(name, du, dv):
    (u0, v0, u1, v1), picture = SW[name]
    if picture:
        return [u0, v0, u1, v1]
    # About one texel per half pixel, cut from a varying spot in the swatch, stretched if the face is bigger.
    su, sv = min(u1 - u0, max(du * 0.5, 0.25)), min(v1 - v0, max(dv * 0.5, 0.25))
    ou = rnd.uniform(0, (u1 - u0) - su)
    ov = rnd.uniform(0, (v1 - v0) - sv)
    return [round(u0 + ou, 3), round(v0 + ov, 3), round(u0 + ou + su, 3), round(v0 + ov + sv, 3)]


def B(out, x0, y0, z0, x1, y1, z1, mat, rot=None, emit=False, name=None, **face_mats):
    """A box. mat: swatch for every face; face_mats override per face (None drops a face)."""
    e = OrderedDict()
    if name:
        e["name"] = name
    e["from"] = [round(x0, 4), round(y0, 4), round(z0, 4)]
    e["to"] = [round(x1, 4), round(y1, 4), round(z1, 4)]
    if rot:
        axis, angle, origin = rot
        e["rotation"] = OrderedDict([("angle", angle), ("axis", axis), ("origin", [round(v, 4) for v in origin])])
    if emit:
        e["shade"] = False
        e["light_emission"] = 15
    size = {"x": abs(x1 - x0), "y": abs(y1 - y0), "z": abs(z1 - z0)}
    e["faces"] = OrderedDict()
    for f in FACES:
        m = face_mats.get(f, mat)
        if m is None:
            continue
        a, b = DIRS[f]
        e["faces"][f] = OrderedDict([("uv", uv_for(m, size[a], size[b])), ("texture", "#rpg")])
    out.append(e)
    return e


def rod(out, z0, z1, r, side, cap=None, along=None, cx=BX, cy=BY, name=None, emit=False, caps=(True, True), mark=None):
    """A solid regular octagonal prism along z: four bands through the axis at 0, 90, 45 and -45 degrees, each
    as wide as an octagon side, so their ends make up the octagon's faces exactly. Each band's end caps are
    staggered a hair so the overlapping caps don't z-fight. along: swatch for the faces whose u runs along z
    (east/west), when the material has grain; side covers up/down, whose v runs along z. mark: a picture for
    the two faces of the level band (the octagon's left and right sides)."""
    a = 2 * r * math.tan(math.pi / 8)
    cap = cap or side
    ew = along or side
    for k, (angle, w, h) in enumerate([(0, r, a / 2), (0, a / 2, r), (45, r, a / 2), (-45, r, a / 2)]):
        d = 0.01 * k
        faces = dict(east=ew, west=ew, up=side, down=side,
                     north=cap if caps[0] else None, south=cap if caps[1] else None)
        if mark and k == 0:
            faces.update(east=mark, west=mark)
        B(out, cx - w, cy - h, z0 + d, cx + w, cy + h, z1 - d, side, emit=emit,
          rot=("z", angle, (cx, cy, z0)) if angle else None, name=(name if k == 0 else None), **faces)


def steps(out, profile, side, cap=None, along=None, **kw):
    """Stacked rods: profile is a list of (z0, z1, radius)."""
    for z0, z1, r in profile:
        rod(out, z0, z1, r, side, cap=cap, along=along, **kw)


# --- the launcher ---------------------------------------------------------------------------------------------
tube, trigger = [], []   # what the two Weapon Station parts are made of; the launcher is both

rod(tube, -2.0, -1.0, 1.2, "steel_worn", cap="steel_dark", name="muzzle_ring")
rod(tube, -1.0, 6.0, 1.0, "steel", name="front_tube")
for z0, z1 in ((6.0, 6.5), (13.0, 13.5), (19.5, 20.0), (26.0, 26.5)):
    rod(tube, z0, z1, 1.68, "steel_dark", name="clamp")
# Wooden heat guards fore and aft of the trigger group; the grain runs along the tube.
rod(tube, 6.5, 13.0, 1.55, "wood_v", cap="wood_end", along="wood_u", name="front_heat_guard")
rod(tube, 20.0, 26.0, 1.55, "wood_v", cap="wood_end", along="wood_u", name="rear_heat_guard")
rod(tube, 13.5, 19.5, 1.05, "steel", name="mid_tube")
rod(tube, 26.5, 28.0, 1.0, "steel", name="rear_tube")
# The flared venturi that vents the booster's blast out the back.
steps(tube, [(28.0, 28.8, 1.15), (28.8, 29.6, 1.35), (29.6, 30.4, 1.6), (30.4, 31.2, 1.85), (31.2, 31.9, 2.05)],
      "steel", cap="steel_dark", name="venturi")
# Dark bores at either end, as stickers just off the end faces.
for z, face in ((MUZZLE_Z - 0.03, "north"), (31.93, "south")):
    s = 0.75 if face == "north" else 1.45
    only = {f: None for f in FACES if f != face}
    B(tube, BX - s, BY - s, z, BX + s, BY + s, z + 0.01, "bore", **only)
    B(tube, BX - s, BY - s, z - 0.005, BX + s, BY + s, z + 0.005, "bore", rot=("z", 45, (BX, BY, z)), **only)
# Folding front sight post on the left of the tube's top.
B(tube, 7.0, 9.9, 4.6, 8.0, 10.5, 5.6, "steel_dark", name="front_sight")
B(tube, 7.35, 10.5, 4.95, 7.65, 11.8, 5.25, "steel_dark")
# Sling swivels.
for z in (1.5, 27.0):
    B(tube, 7.8, 7.4, z, 8.2, 8.0, z + 0.9, "steel_dark")
    B(tube, 7.85, 7.1, z - 0.3, 8.15, 7.4, z + 1.2, "steel_dark")

# Trigger group under the middle of the tube: housing, pistol grip raked back, guard and trigger.
B(trigger, 7.25, 6.4, 13.6, 8.75, 8.2, 19.4, "steel_dark", name="trigger_housing")
B(trigger, 7.35, 6.6, 13.4, 8.65, 8.0, 13.6, "steel_worn")
B(trigger, 7.2, 2.6, 16.9, 8.8, 6.6, 19.3, "bakelite_plain", east="bakelite", west="bakelite",
  rot=("x", -16, (8.0, 6.6, 18.1)), name="pistol_grip")
B(trigger, 7.1, 2.4, 16.8, 8.9, 2.7, 19.4, "bakelite_plain", rot=("x", -16, (8.0, 6.6, 18.1)))
B(trigger, 7.7, 5.0, 13.8, 8.3, 5.35, 16.9, "steel_dark", name="trigger_guard")
B(trigger, 7.7, 5.35, 13.8, 8.3, 6.4, 14.15, "steel_dark")
B(trigger, 7.82, 5.5, 15.0, 8.18, 6.4, 15.4, "steel_worn", rot=("x", -12, (8.0, 6.4, 15.2)), name="trigger")
# Hammer spur and safety at the back of the housing.
B(trigger, 7.75, 7.6, 19.4, 8.25, 8.4, 19.9, "steel_worn", name="hammer")
B(trigger, 8.75, 7.0, 17.6, 8.95, 7.5, 18.3, "steel_worn", name="safety")
# Rear iron sight (a leaf) on the left of the top.
B(trigger, 6.8, 9.9, 13.6, 8.0, 10.6, 14.8, "steel_dark", name="rear_sight")
B(trigger, 6.9, 10.6, 14.1, 7.9, 11.4, 14.3, "steel_dark")
# PGO-7 optical sight on a bracket off the left side, its eyepiece toward the shooter.
B(trigger, 5.9, 9.3, 15.2, 7.0, 10.3, 17.6, "steel_dark", name="sight_bracket")
B(trigger, 4.3, 9.8, 13.0, 6.0, 11.7, 19.0, "olive_dark", up="olive_dark", name="pgo7")
B(trigger, 4.5, 9.95, 11.6, 5.8, 11.55, 13.0, "black", north=None, name="pgo7_sunshade")
B(trigger, 4.6, 10.05, 11.75, 5.7, 11.45, 11.77, "glass", **{f: None for f in FACES if f != "north"})
B(trigger, 4.6, 10.0, 19.0, 5.7, 11.5, 20.9, "rubber", name="pgo7_eyecup")
B(trigger, 4.85, 11.7, 15.4, 5.45, 12.3, 16.6, "steel_worn", name="pgo7_turret")
B(trigger, 3.9, 10.4, 15.5, 4.3, 11.1, 16.5, "steel_worn")
B(trigger, 5.95, 11.2, 17.4, 6.25, 11.6, 18.2, "black", name="pgo7_lamp")

LAUNCHER = tube + trigger

# --- PG-7 rockets ---------------------------------------------------------------------------------------------


def rocket(homing, flight=False, front_only=False):
    """A PG-7: piezo-fuzed HEAT warhead, sustainer motor, paper booster cartridge and folded tail fins. The homing
    one swaps the fuze for a seeker dome with canards and wears a yellow band. front_only: just what sticks out
    of the muzzle when seated."""
    out = []
    if homing:
        steps(out, [(-15.9, -15.4, 0.7), (-15.4, -14.8, 1.1)], "seeker_glass", name="seeker")
        B(out, BX - 0.25, BY - 0.25, -15.94, BX + 0.25, BY + 0.25, -15.92, "glow_red", emit=True,
          **{f: None for f in FACES if f != "north"})
        steps(out, [(-14.8, -14.4, 1.3)], "steel_worn")
        steps(out, [(-14.4, -13.4, 1.5), (-13.4, -12.4, 1.72), (-12.4, -11.3, 1.9), (-11.3, -10.6, 2.02)], "olive")
        for k in range(4):
            B(out, BX - 0.08, BY + 1.5, -13.6, BX + 0.08, BY + 2.9, -12.4, "steel_dark",
              rot=("z", 45 + 90 * k, (BX, BY, -13.0)), name="canard" if k == 0 else None)
    else:
        rod(out, -16.0, -15.3, 0.32, "brass", name="fuze")
        steps(out, [(-15.3, -14.5, 0.55), (-14.5, -13.7, 0.85), (-13.7, -12.9, 1.15), (-12.9, -12.1, 1.45),
                    (-12.1, -11.3, 1.75), (-11.3, -10.6, 1.98)], "olive", name="nose")
    rod(out, -10.6, -8.0, 2.1, "olive", mark="stencil", name="warhead")
    if homing:
        rod(out, -9.7, -9.1, 2.16, "yellow", name="homing_band")
    steps(out, [(-8.0, -7.4, 1.75), (-7.4, -6.8, 1.35), (-6.8, -6.3, 1.0)], "olive_dark")
    rod(out, -6.3, MUZZLE_Z - 0.1 if front_only else -2.0, 0.85, "motor", name="sustainer_motor")
    rod(out, -5.4, -4.9, 0.92, "steel_dark")
    if front_only:
        return out
    rod(out, -2.0, 7.8, 0.82, "booster", name="booster")
    rod(out, 7.8, 10.6, 0.7, "steel_dark", name="tail")
    rod(out, 10.6, 11.2, 0.5, "black")
    for k in range(4):
        if flight:
            # Sprung out once it's clear of the tube.
            B(out, BX - 0.1, BY + 0.6, 8.4, BX + 0.1, BY + 2.4, 10.8, "steel_worn",
              rot=("z", 45 + 90 * k, (BX, BY, 8.4)), name="fin" if k == 0 else None)
        else:
            # Folded flat round the tail, inside the tube's bore.
            B(out, BX - 0.35, BY + 0.7, 7.6, BX + 0.35, BY + 0.82, 10.4, "steel_worn",
              rot=("z", 45 + 90 * k, (BX, BY, 7.6)), name="fin" if k == 0 else None)
    if flight:
        rod(out, 11.2, 12.4, 0.42, "flame", emit=True, name="exhaust")
    return out


# --- homing guidance module: a seeker pod clamped on top of the front heat guard ---------------------------------
POD_Y, POD_Z = 12.1, 10.0   # pod centre


def pod(with_clamps):
    out = []
    if with_clamps:
        for z in (7.9, 11.5):
            rod(out, z, z + 0.6, 1.62, "steel_dark", name="pod_strap")
    B(out, 6.9, 10.55, 7.6, 9.1, 11.0, 12.6, "steel_dark", name="pod_mount")
    B(out, 6.7, 11.0, 7.0, 9.3, 13.2, 13.0, "pod", east="pod_panel", west="pod_panel", south="pod", name="pod_body")
    B(out, 6.95, 13.2, 7.3, 9.05, 13.5, 12.7, "pod")
    rod(out, 6.0, 7.0, 0.95, "pod", cy=POD_Y, name="pod_seeker")
    rod(out, 5.8, 6.0, 0.75, "seeker_glass", cy=POD_Y)
    B(out, 7.75, POD_Y - 0.25, 5.76, 8.25, POD_Y + 0.25, 5.79, "glow_red", emit=True, **{f: None for f in FACES if f != "north"})
    # Facing the shooter: a status display and a ready light.
    B(out, 7.2, 11.4, 13.0, 8.8, 12.9, 13.03, "screen", emit=True, **{f: None for f in FACES if f != "south"})
    B(out, 8.9, 12.95, 13.0, 9.15, 13.15, 13.03, "glow_green", emit=True, **{f: None for f in FACES if f != "south"})
    B(out, 9.0, 13.5, 12.0, 9.2, 15.0, 12.2, "black", name="antenna")
    B(out, 8.95, 15.0, 11.95, 9.25, 15.3, 12.25, "steel_worn")
    B(out, 9.3, 11.3, 8.0, 9.32, 11.9, 10.4, "hazard", **{f: None for f in FACES if f != "east"})
    if with_clamps:
        # Cable down the right side into the trigger housing.
        B(out, 8.75, 7.8, 13.1, 9.0, 11.0, 13.4, "black", name="pod_cable")
    return out


POD = pod(True)

# ----------------------------------------------------------------------------------------------- checks + output


def check(name, elements):
    fights = zfight_check(elements)
    if fights:
        for f in fights:
            print("z-fight:", name, f)
        raise SystemExit(name + " has z-fighting")
    for e in elements:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (name, e)


def write(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def euler_xyz(m):
    """Degrees (x, y, z) with Rx * Ry * Rz == m, the order item display rotations are applied in."""
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    a = math.atan2(-m[1][2], m[2][2])
    c = math.atan2(-m[0][1], m[0][0])
    return [round(math.degrees(v), 2) for v in (a, b, c)]


def look(muzzle, top):
    """A display rotation turning the muzzle (model -z) and the top (model +y) to these screen directions."""
    f = [-v for v in muzzle]
    up = top
    side = [up[1] * f[2] - up[2] * f[1], up[2] * f[0] - up[0] * f[2], up[0] * f[1] - up[1] * f[0]]
    return euler_xyz([[side[i], up[i], f[i]] for i in range(3)])


def centred(rotation, scale, centre, lift=0.0):
    """Translation (pixels) that puts the model's centre at the middle of the slot after rotation and scale."""
    rx, ry, rz = (math.radians(v) for v in rotation)
    def rmx(a): return [[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]]
    def rmy(a): return [[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]]
    def rmz(a): return [[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]]
    def mul(p, q): return [[sum(p[i][k] * q[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    m = mul(mul(rmx(rx), rmy(ry)), rmz(rz))
    c = [v - 8 for v in centre]
    return [round(-sum(m[i][k] * c[k] for k in range(3)) * scale + (lift if i == 1 else 0), 3) for i in range(3)]


def display(rot, tr, sc):
    return OrderedDict([("rotation", rot), ("translation", tr), ("scale", [sc, sc, sc])])


def bounds_centre(elements):
    lo = [min(e["from"][a] for e in elements) for a in range(3)]
    hi = [max(e["to"][a] for e in elements) for a in range(3)]
    return [(lo[a] + hi[a]) / 2 for a in range(3)]


S2 = math.sqrt(0.5)
# Inventory icons lie on the diagonal, left side up (the PGO-7's side), muzzle to the upper left.
DIAGONAL = look([-S2, S2, 0], [S2, S2, 0])

# First person: on the right shoulder, the tube running straight ahead (mirrored in GunDisplay).
FP = display([0, -7, 0], [-1.4, 3.3, -1.0], 0.65)
# Third person: solved for the shouldered arm pose in LauncherAnimation (right arm forward, turned in 0.08 rad and
# raised 0.05 rad above level), so the rear heat guard rests on top of the right shoulder.
TP = display([2.86, -4.58, 0], [1.0, 0.95, 1.49], 0.58)


def item_displays(elements, gui_scale, extra=None):
    """elements: everything the item can show, so it stays centred with a warhead seated and the pod fitted."""
    c = bounds_centre(elements)
    d = OrderedDict([
        ("thirdperson_righthand", TP), ("thirdperson_lefthand", TP),
        ("firstperson_righthand", FP), ("firstperson_lefthand", FP),
        ("gui", display(DIAGONAL, centred(DIAGONAL, gui_scale, c), gui_scale)),
        ("ground", display([0, 0, 0], centred([0, 0, 0], gui_scale * 0.7, c, 2.0), gui_scale * 0.7)),
        ("fixed", display(DIAGONAL, centred(DIAGONAL, gui_scale, c), gui_scale)),
    ])
    if extra:
        d.update(extra)
    return d


def model(elements, displays=None, parent=None):
    m = OrderedDict([("credit", "tomyen rpg7 — generated by tools/gen_rpg.py"), ("texture_size", [64, 64])])
    if parent:
        m["parent"] = parent
    m["textures"] = OrderedDict([("rpg", "tomyen:item/rpg7"), ("particle", "tomyen:item/rpg7")])
    if displays:
        m["display"] = displays
    m["elements"] = elements
    return m


def ref(path):
    return {"type": "minecraft:model", "model": "tomyen:item/" + path}


EMPTY = {"type": "minecraft:empty"}

check("rpg7", LAUNCHER)
write(os.path.join(MODELS, "rpg7.json"), model(LAUNCHER, item_displays(LAUNCHER + rocket(True, front_only=True) + POD, 0.4)))
for name, homing in (("pg7", False), ("pg7_homing", True)):
    front = rocket(homing, front_only=True)
    check("rpg7_" + name, front)
    write(os.path.join(MODELS, "rpg7_warhead_" + name + ".json"), model(front, parent="tomyen:item/rpg7"))
check("rpg7_pod", POD)
write(os.path.join(MODELS, "rpg7_seeker.json"), model(POD, parent="tomyen:item/rpg7"))

# Weapon Station parts: the launch tube, and the trigger group with its sight.
for name, elements, scale in (("rpg7_launch_tube", tube, 0.42), ("rpg7_trigger_group", trigger, 0.95)):
    check(name, elements)
    c = bounds_centre(elements)
    write(os.path.join(MODELS, name + ".json"), model(elements, OrderedDict([
        ("gui", display(DIAGONAL, centred(DIAGONAL, scale, c), scale)),
        ("ground", display([0, 0, 0], centred([0, 0, 0], scale * 0.7, c, 2.0), scale * 0.7)),
        ("fixed", display(DIAGONAL, centred(DIAGONAL, scale * 1.2, c), scale * 1.2)),
        ("thirdperson_righthand", display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8)),
        ("firstperson_righthand", display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8)),
    ])))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})

# Rockets: held like a stick of TNT, on the diagonal in the inventory.
for name, homing in (("pg7_rocket", False), ("pg7_homing_rocket", True)):
    held = rocket(homing)
    check(name, held)
    c = bounds_centre(held)
    s = 0.55
    hand = OrderedDict([("rotation", [0, 90, -30]), ("translation", centred([0, 90, -30], 0.5, c, 3.0)), ("scale", [0.5] * 3)])
    write(os.path.join(MODELS, name + ".json"), model(held, OrderedDict([
        ("gui", display(DIAGONAL, centred(DIAGONAL, s, c), s)),
        ("ground", display([0, 0, 0], centred([0, 0, 0], 0.4, c, 2.0), 0.4)),
        ("fixed", display(DIAGONAL, centred(DIAGONAL, 0.7, c), 0.7)),
        ("thirdperson_righthand", display([0, 90, -30], centred([0, 90, -30], 0.45, c, 2.0), 0.45)),
        ("thirdperson_lefthand", display([0, 90, -30], centred([0, 90, -30], 0.45, c, 2.0), 0.45)),
        ("firstperson_righthand", hand), ("firstperson_lefthand", hand),
    ])))
    flying = rocket(homing, flight=True)
    check(name + "_flight", flying)
    write(os.path.join(MODELS, name + "_flight.json"), model(flying, parent="tomyen:item/" + name))
    # The projectile renders a copy carrying tomyen:rocket_in_flight, which picks the fins-out model.
    write(os.path.join(ITEMS, name + ".json"), {"model": OrderedDict([
        ("type", "minecraft:condition"), ("property", "minecraft:has_component"), ("component", "tomyen:rocket_in_flight"),
        ("on_true", ref(name + "_flight")), ("on_false", ref(name))])})

# The module on its own: the pod without the straps that hold it to the tube, centred.
module = pod(False)
check("homing_guidance_module", module)
c = bounds_centre(module)
ISO = [30, 225, 0]
write(os.path.join(MODELS, "homing_guidance_module.json"), model(module, OrderedDict([
    ("gui", display(ISO, centred(ISO, 1.55, c), 1.55)),
    ("ground", display([0, 0, 0], centred([0, 0, 0], 0.9, c, 2.0), 0.9)),
    ("fixed", display([0, 180, 0], centred([0, 180, 0], 1.6, c), 1.6)),
    ("thirdperson_righthand", display([0, 0, 0], centred([0, 0, 0], 0.8, c, 2.0), 0.8)),
    ("firstperson_righthand", display([0, -20, 0], centred([0, -20, 0], 0.9, c, 3.0), 0.9)),
    ("firstperson_lefthand", display([0, -20, 0], centred([0, -20, 0], 0.9, c, 3.0), 0.9)),
])))
write(os.path.join(ITEMS, "homing_guidance_module.json"), {"model": ref("homing_guidance_module")})


# The launcher: its body, then whichever warhead is seated (or being seated, which the reload animation shows
# through tomyen:gun_animation: 1 a PG-7, 2 a homing one, 3 a napalm one), then the seeker pod if the module is fitted.
def warhead(rocket_id, path):
    return {"when": rocket_id, "model": ref(path)}


write(os.path.join(ITEMS, "rpg7.json"), {"model": OrderedDict([("type", "minecraft:composite"), ("models", [
    ref("rpg7"),
    OrderedDict([
        ("type", "minecraft:select"), ("property", "minecraft:component"), ("component", "tomyen:loaded_rocket"),
        ("cases", [warhead("tomyen:pg7_rocket", "rpg7_warhead_pg7"), warhead("tomyen:pg7_homing_rocket", "rpg7_warhead_pg7_homing"),
                   warhead("tomyen:pg7_napalm_rocket", "rpg7_warhead_pg7_napalm")]),
        ("fallback", OrderedDict([
            ("type", "minecraft:range_dispatch"), ("property", "tomyen:gun_animation"),
            ("entries", [{"threshold": 0.5, "model": ref("rpg7_warhead_pg7")},
                         {"threshold": 1.5, "model": ref("rpg7_warhead_pg7_homing")},
                         {"threshold": 2.5, "model": ref("rpg7_warhead_pg7_napalm")}]),
            ("fallback", EMPTY)]))]),
    OrderedDict([
        ("type", "minecraft:condition"), ("property", "minecraft:has_component"), ("component", "tomyen:guidance_attachment"),
        ("on_true", ref("rpg7_seeker")), ("on_false", EMPTY)]),
])])})




def napalm_rocket(flight=False, front_only=False):
    """The napalm PG-7, kept plain: square-section stages laid out along the bore where a PG-7's are (so it seats and
    loads the same), a maroon incendiary warhead with an orange band, and crossed tail fins."""
    out = []
    B(out, BX - 0.9, BY - 0.9, -16.0, BX + 0.9, BY + 0.9, -14.4, "napalm_dark", name="nose")
    B(out, BX - 1.5, BY - 1.5, -14.4, BX + 1.5, BY + 1.5, -12.6, "napalm")
    B(out, BX - 2.1, BY - 2.1, -12.6, BX + 2.1, BY + 2.1, -8.0, "napalm", name="warhead")
    B(out, BX - 2.15, BY - 2.15, -10.5, BX + 2.15, BY + 2.15, -9.7, "napalm_band", north=None, south=None, name="band")
    B(out, BX - 1.2, BY - 1.2, -8.0, BX + 1.2, BY + 1.2, -6.3, "napalm_dark")
    B(out, BX - 0.85, BY - 0.85, -6.3, BX + 0.85, BY + 0.85, MUZZLE_Z - 0.1 if front_only else -2.0, "plain_motor",
      name="sustainer_motor")
    if front_only:
        return out
    B(out, BX - 0.8, BY - 0.8, -2.0, BX + 0.8, BY + 0.8, 7.8, "plain_booster", name="booster")
    B(out, BX - 0.6, BY - 0.6, 7.8, BX + 0.6, BY + 0.6, 10.6, "plain_steel", name="tail")
    # Sprung out in flight; folded inside the bore before.
    span = 2.4 if flight else 0.75
    B(out, BX - span, BY - 0.1, 8.4, BX + span, BY + 0.1, 10.6, "plain_steel", rot=("z", 45, (BX, BY, 8.4)), name="fins")
    B(out, BX - 0.1, BY - span, 8.42, BX + 0.1, BY + span, 10.58, "plain_steel", rot=("z", 45, (BX, BY, 8.4)))
    if flight:
        B(out, BX - 0.42, BY - 0.42, 10.6, BX + 0.42, BY + 0.42, 12.4, "flame", emit=True, name="exhaust")
    return out


# The napalm rocket, last of all the models: it draws UV cuts from the same random numbers as everything above, so
# going last leaves every earlier model as it was.
front = napalm_rocket(front_only=True)
check("rpg7_pg7_napalm", front)
write(os.path.join(MODELS, "rpg7_warhead_pg7_napalm.json"), model(front, parent="tomyen:item/rpg7"))
held = napalm_rocket()
check("pg7_napalm_rocket", held)
c = bounds_centre(held)
hand = OrderedDict([("rotation", [0, 90, -30]), ("translation", centred([0, 90, -30], 0.5, c, 3.0)), ("scale", [0.5] * 3)])
write(os.path.join(MODELS, "pg7_napalm_rocket.json"), model(held, OrderedDict([
    ("gui", display(DIAGONAL, centred(DIAGONAL, 0.55, c), 0.55)),
    ("ground", display([0, 0, 0], centred([0, 0, 0], 0.4, c, 2.0), 0.4)),
    ("fixed", display(DIAGONAL, centred(DIAGONAL, 0.7, c), 0.7)),
    ("thirdperson_righthand", display([0, 90, -30], centred([0, 90, -30], 0.45, c, 2.0), 0.45)),
    ("thirdperson_lefthand", display([0, 90, -30], centred([0, 90, -30], 0.45, c, 2.0), 0.45)),
    ("firstperson_righthand", hand), ("firstperson_lefthand", hand),
])))
flying = napalm_rocket(flight=True)
check("pg7_napalm_rocket_flight", flying)
write(os.path.join(MODELS, "pg7_napalm_rocket_flight.json"), model(flying, parent="tomyen:item/pg7_napalm_rocket"))
write(os.path.join(ITEMS, "pg7_napalm_rocket.json"), {"model": OrderedDict([
    ("type", "minecraft:condition"), ("property", "minecraft:has_component"), ("component", "tomyen:rocket_in_flight"),
    ("on_true", ref("pg7_napalm_rocket_flight")), ("on_false", ref("pg7_napalm_rocket"))])})

# ----------------------------------------------------------------------------------------------- PGO-7 reticle
def reticle(size=512):
    """The PGO-7's view: a range scale of chevrons (2 to 5 hundred metres) down the middle, a lead scale across,
    and the stadiametric rangefinder curve in the lower left, etched black on amber-tinted glass. Like scope.png,
    clear inside the eyepiece and black round it."""
    px = [(0, 0, 0, 0)] * (size * size)
    c = size / 2
    rad = size * 0.46
    lines = []   # (x0, y0, x1, y1, half-width) in units of the radius, y down

    def seg(x0, y0, x1, y1, w=0.006):
        lines.append((x0, y0, x1, y1, w))
    for k, y in enumerate((-0.02, 0.1, 0.22, 0.34)):
        seg(-0.06, y + 0.05, 0.0, y, 0.007)
        seg(0.0, y, 0.06, y + 0.05, 0.007)
    seg(0.0, -0.75, 0.0, -0.08, 0.004)
    for x in (-0.6, -0.45, -0.3, -0.15, 0.15, 0.3, 0.45, 0.6):
        seg(x, 0.0, x, -0.05 if abs(x) in (0.3, 0.6) else -0.03, 0.005)
    seg(-0.66, 0.0, -0.12, 0.0, 0.004)
    seg(0.12, 0.0, 0.66, 0.0, 0.004)
    # Rangefinder: a dashed curve from upper right to lower left over a baseline.
    pts = [(-0.58 + t * 0.42, 0.52 - 0.2 * (1 - (1 - t) ** 2)) for t in [i / 10 for i in range(11)]]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        seg(x0, y0, x1, y1, 0.005)
    seg(-0.58, 0.52, -0.16, 0.52, 0.005)
    for i in range(7):
        x = -0.58 + i * 0.07
        seg(x, 0.52, x, 0.56, 0.004)

    def dist(px_, py_, l):
        x0, y0, x1, y1, w = l
        dx, dy = x1 - x0, y1 - y0
        t = max(0.0, min(1.0, ((px_ - x0) * dx + (py_ - y0) * dy) / (dx * dx + dy * dy or 1)))
        return math.hypot(px_ - x0 - t * dx, py_ - y0 - t * dy) - w
    for y in range(size):
        for x in range(size):
            u, v = (x + 0.5 - c) / rad, (y + 0.5 - c) / rad
            r = math.hypot(u, v)
            if r > 1.0:
                px[y * size + x] = (0, 0, 0, 255)
                continue
            # Dark vignette toward the edge of the eyepiece, a faint amber tint inside.
            edge = max(0.0, (r - 0.8) / 0.2)
            a = int(40 + 215 * edge ** 1.5)
            col = mix(0x2A1E08, 0x000000, edge)
            d = min(dist(u, v, l) for l in lines) if r < 0.8 else 1.0
            if d < 0.0:
                col, a = 0x050505, 255
            elif d < 0.004:
                col, a = 0x050505, max(a, int(255 * (1 - d / 0.004)))
            px[y * size + x] = ((col >> 16) & 255, (col >> 8) & 255, col & 255, a)
    png(os.path.join(TEX, "misc/pgo7_scope.png"), size, size, px)


reticle()
print(f"rpg7: {len(LAUNCHER)} elements, pod {len(POD)}, rocket {len(rocket(False))}/{len(rocket(True))}")
