"""Builds the drills' assets: the Hand Drill, Steel Drill, Tungsten Carbide Drill and Star Drill, their bits, the motor
and crank sounds, recipes with recipe-book advancements, tags and language entries.

Every drill is a held 3D model in as few boxes as hold its shape, painted from its own small atlas of flat colours and
smooth ramps (no speckle). The bit is a twisted square bar: each segment is turned 22.5 degrees past the one behind
it, so turning the whole bit another 22.5 degrees per frame makes the flutes run down it. The spinning parts are
their own models, one per frame, which mymod:drill_spin picks (DrillSpin on the client); the Hand Drill's crank, gear
wheel and bit turn together over eight frames, the motor drills' bits over four. In the inventory, on the ground and
in item frames the drill shows its first frame.

The drills are held like the long guns: two-handed, the shooting hand on the pistol grip, the off hand on the side
handle (or, on the Hand Drill, on the crank's knob, which goes round with it). The first-person grip points printed at
the end go in DrillHands; keep them in step with the models.

Model space is in pixels: the bit runs along z at x = 8, y = BORE with its tip toward -z, +x is the drill's right.

Usage: python3 tools/gen_drills.py src/main/resources/assets/mymod"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, rotate, rx, rz, write, model, ref, range_dispatch, composite, display, display_matrix, apply,
                    placed, centred, bounds_centre, look, mix, copy, S2, element_rotation)

ASSETS = sys.argv[1]
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models", "item"), os.path.join(ASSETS, "items")

# Where long guns put the shooting hand (as in gen_napalm.py: the M4's grip point, in the held item's space).
FP_GRIP_TARGET = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
FP_ROT = [0, -8, 0]
FP_SCALE = 0.6
TP_SCALE = 0.65
# Inventory icons lie on the diagonal like the long guns', tip to the upper left, rolled a little to show their tops.
GUI_ROT = look([-S2, S2, 0], [S2 * math.cos(math.radians(25)), S2 * math.cos(math.radians(25)), math.sin(math.radians(25))])
STEP = 22.5          # how far the bit turns each frame


# ============================================================================================================ paint


def flat(kit, colour):
    return lambda x, y, w, h: kit.rgb(colour)


def ramp(kit, top, bottom):
    """A smooth ramp, lighter at the top of the swatch: Minecraft's face shading does the rest."""
    return lambda x, y, w, h: kit.rgb(mix(top, bottom, y / max(1, h - 1)))


def glow(kit, centre, edge):
    """A soft radial glow, bright in the middle: for the lit windows and crystal."""
    return lambda x, y, w, h: kit.rgb(mix(centre, edge, min(1.0, math.hypot(x + 0.5 - w / 2, y + 0.5 - h / 2) / (w / 2))))


def atlas(name, swatches, seed):
    """A drill's atlas: swatches is a list of (name, painter(kit)), laid out four to a row, each 4 x 4 model units."""
    kit = Kit(name, "item/" + name, seed=seed)
    for i, (sw, painter) in enumerate(swatches):
        u, v = (i % 4) * 4, (i // 4) * 4
        kit.swatch(sw, u, v, u + 4, v + 4, painter(kit))
    kit.save(TEX)
    return kit


RUBBER = ("rubber", lambda k: ramp(k, 0x3A3D42, 0x1F2124))

HAND = atlas("hand_drill", [
    ("wood", lambda k: ramp(k, 0xB98250, 0x80552F)),
    ("wood_end", lambda k: flat(k, 0x6E4628)),
    ("iron", lambda k: ramp(k, 0xD6DADF, 0x9299A1)),
    ("iron_dark", lambda k: ramp(k, 0x6F757D, 0x464B52)),
    ("copper", lambda k: ramp(k, 0xE59A68, 0xAE5A34)),
    ("copper_dark", lambda k: flat(k, 0x8C4A2A)),
], 701)

STEEL = atlas("steel_drill", [
    ("steel", lambda k: ramp(k, 0xBFC5CC, 0x868D96)),
    ("steel_dark", lambda k: ramp(k, 0x5C626A, 0x3C4148)),
    RUBBER,
    ("red", lambda k: ramp(k, 0xD84A32, 0x9C2C1C)),
    ("bit", lambda k: ramp(k, 0xE2E7EC, 0x9AA2AC)),
], 702)

CARBIDE = atlas("tungsten_carbide_drill", [
    ("body", lambda k: ramp(k, 0x5E656E, 0x3B4047)),
    ("body_dark", lambda k: ramp(k, 0x34383E, 0x222529)),
    RUBBER,
    ("amber", lambda k: ramp(k, 0xF2B62A, 0xC4800F)),
    ("carbide", lambda k: ramp(k, 0x8995A5, 0x4E5764)),
    ("edge", lambda k: flat(k, 0xD8E4EE)),
    ("glow", lambda k: glow(k, 0xC8FFB0, 0x46D83A)),
], 703)

STAR = atlas("star_drill", [
    ("body", lambda k: ramp(k, 0x2C3F8E, 0x16205A)),
    ("body_dark", lambda k: ramp(k, 0x1A2352, 0x0E1333)),
    RUBBER,
    ("trim", lambda k: ramp(k, 0xD3DBEA, 0x8E9AB6)),
    ("crystal", lambda k: ramp(k, 0xC8FAFF, 0x5CC8EA)),
    ("violet", lambda k: ramp(k, 0x7A5CD8, 0x4C34A8)),
    ("core", lambda k: glow(k, 0xF4FBFF, 0x63D0EE)),
], 704)


# ============================================================================================================ parts


def twist(kit, out, cx, cy, z_tip, z_back, side, segments, mat, emit=False):
    """A twisted square bar along z from z_back forward to z_tip, each segment turned STEP past the one behind it.
    Every other segment is set in a hair at its ends so the caps never share a plane."""
    length = (z_back - z_tip) / segments
    for i in range(segments):
        z1 = z_back - i * length
        z0 = z1 - length
        inset = 0.01 if i % 2 else 0.0
        h = side / 2
        kit.box(out, cx - h, cy - h, z0 + inset, cx + h, cy + h, z1 - inset, mat, emit=emit,
                rot=("z", STEP * i, (cx, cy, z0)) if (STEP * i) % 90 else None)
    return out


def point(kit, out, cx, cy, z_tip, z_base, side, mat, emit=False):
    """The bit's point: two stubby squares, the front one smaller and turned, to read as a tapering tip."""
    mid = (z_tip + z_base) / 2
    kit.box(out, cx - side * 0.35, cy - side * 0.35, mid, cx + side * 0.35, cy + side * 0.35, z_base - 0.01, mat, emit=emit,
            rot=("z", 45, (cx, cy, mid)))
    kit.box(out, cx - side * 0.18, cy - side * 0.18, z_tip, cx + side * 0.18, cy + side * 0.18, mid + 0.01, mat, emit=emit)
    return out


def spun(parts, frame, pivot, step=STEP):
    """The spinning parts turned on for this frame, about the bore."""
    return rotate(copy(parts), rz(step * frame), pivot)


# ======================================================================================================= the drills


class Drill:
    """One drill: its static body, its spinning parts per frame, grip points and how many frames it has."""

    def __init__(self, name, kit, body, frames, moving, grip, support, bit_part):
        self.name, self.kit, self.body, self.frames, self.moving = name, kit, body, frames, moving
        self.grip, self.support, self.bit_part = grip, support, bit_part


# ---------------------------------------------------------------------------------------------------- Hand Drill
# An egg-beater drill held like a gun: a twisted iron bit in a chuck, an iron spine, a copper gear wheel on the left
# side turned by a crank with a wooden knob, and a wooden pistol grip at the back.
H_BORE = 10.0
WHEEL = (5.0, H_BORE + 0.6, 4.6)       # the gear wheel's centre: on the left of the spine, a little above the bore
WHEEL_R = 3.6
CRANK_R = 2.9


def hand_bit():
    out = []
    twist(HAND, out, 8, H_BORE, -8.6, -2.6, 1.3, 4, "iron")
    point(HAND, out, 8, H_BORE, -10.2, -8.6, 1.3, "iron")
    return out


def hand_drill():
    k = HAND
    body = []
    k.box(body, 7.2, H_BORE - 0.8, -0.4, 8.8, H_BORE + 0.8, 10.2, "iron_dark", name="spine")
    k.box(body, 6.4, H_BORE - 0.3, 4.2, 7.2, H_BORE + 1.5, 5.0, "iron_dark", name="axle_bracket")
    k.box(body, 7.0, 3.6, 8.4, 9.0, H_BORE - 0.8, 10.8, "wood", rot=("x", -14, (8.0, H_BORE - 0.8, 9.6)), name="grip",
          down="wood_end")
    k.box(body, 6.8, H_BORE - 1.0, 10.2, 9.2, H_BORE + 1.0, 11.4, "wood", name="butt", south="wood_end")

    def moving(frame):
        """The crank goes round 45 degrees a frame, and through its gears turns the bit twice as fast."""
        crank = []
        wx, wy, wz = WHEEL
        # The gear wheel: two squares a quarter turn apart make an eight-toothed wheel; its hub behind.
        s = WHEEL_R / math.sqrt(2)
        k.box(crank, wx, wy - s, wz - s, wx + 0.6, wy + s, wz + s, "copper", name="gear_wheel")
        k.box(crank, wx + 0.02, wy - s, wz - s, wx + 0.58, wy + s, wz + s, "copper", rot=("x", 45, (wx, wy, wz)))
        k.box(crank, wx + 0.6, wy - 0.6, wz - 0.6, wx + 1.4, wy + 0.6, wz + 0.6, "copper_dark", name="hub")
        # The crank arm out to the knob, on the wheel's outer face.
        k.box(crank, wx - 0.6, wy - 0.45, wz - 0.45, wx, wy + CRANK_R + 0.45, wz + 0.45, "iron_dark", name="crank")
        k.box(crank, wx - 3.0, wy + CRANK_R - 0.65, wz - 0.65, wx - 0.6, wy + CRANK_R + 0.65, wz + 0.65, "wood", name="knob",
              west="wood_end")
        rotate(crank, rx(-45 * frame), WHEEL)
        bit = hand_bit()
        k.box(bit, 8 - 1.25, H_BORE - 1.25, -2.6, 8 + 1.25, H_BORE + 1.25, -0.4, "iron_dark", name="chuck")
        return crank + spun(bit, frame, (8, H_BORE, 0))

    knobs = []
    for f in range(8):
        a = math.radians(-45 * f)
        # The knob's middle, turned with the crank about the wheel's axle (x).
        y, z = CRANK_R * math.cos(a), CRANK_R * math.sin(a)
        knobs.append((WHEEL[0] - 1.8, WHEEL[1] + y, WHEEL[2] + z))
    return Drill("hand_drill", k, body, 8, moving, (8.0, 6.4, 10.4), knobs, hand_bit)


# ---------------------------------------------------------------------------------------------------- Steel Drill
# A motor drill: a steel motor housing with a red band, a gearbox nose and chuck, a rubber pistol grip over a red
# kerosene tank, and a rubber side handle out to the left for the off hand.
S_BORE = 10.6


def steel_bit():
    out = []
    twist(STEEL, out, 8, S_BORE, -8.4, -3.0, 1.4, 4, "bit")
    point(STEEL, out, 8, S_BORE, -10.0, -8.4, 1.4, "bit")
    return out


def motor_body(k, bore, half_w, half_h, z_nose, z_front, z_back, body, dark, band, grip, tank, handle_mat):
    """The housing every motor drill shares: nose, housing with a coloured band, rear cap, pistol grip with its trigger,
    the fuel tank under it and the side handle. Returns the elements and the grip and side handle points."""
    out = []
    y0, y1 = bore - half_h, bore + half_h
    k.box(out, 8 - half_w + 0.5, bore - half_h + 0.6, z_nose, 8 + half_w - 0.5, bore + half_h - 0.6, z_front, dark, name="nose")
    k.box(out, 8 - half_w, y0, z_front, 8 + half_w, y1, z_back, body, name="housing")
    mid = z_front + (z_back - z_front) * 0.25
    k.box(out, 8 - half_w - 0.1, y0 - 0.1, mid, 8 + half_w + 0.1, y1 + 0.1, mid + 1.2, band, name="band")
    k.box(out, 8 - half_w + 0.4, y0 + 0.4, z_back, 8 + half_w - 0.4, y1 - 0.4, z_back + 1.0, dark, name="rear_cap")
    gz = z_back - 2.6
    k.box(out, 7.0, y0 - 5.4, gz - 1.25, 9.0, y0, gz + 1.25, grip, rot=("x", -14, (8.0, y0, gz)), name="grip")
    k.box(out, 7.6, y0 - 1.6, gz - 2.4, 8.4, y0, gz - 1.6, dark, name="trigger")
    k.box(out, 8 - half_w + 0.3, y0 - 7.6, gz - 4.0, 8 + half_w - 0.3, y0 - 5.4, gz + 2.0, tank, name="tank")
    hz = z_nose + (z_front - z_nose) / 2
    k.box(out, 2.2, bore - 0.7, hz - 0.7, 8 - half_w + 0.5, bore + 0.7, hz + 0.7, handle_mat, name="side_handle")
    return out, (8.0, y0 - 2.6, gz + 0.6), (3.6, bore, hz)


def steel_drill():
    k = STEEL
    body, grip, support = motor_body(k, S_BORE, 2.2, 2.6, -1.0, 1.4, 10.4, "steel", "steel_dark", "red", "rubber", "red", "rubber")

    def moving(frame):
        bit = steel_bit()
        k.box(bit, 8 - 1.3, S_BORE - 1.3, -3.0, 8 + 1.3, S_BORE + 1.3, -1.0, "steel_dark", name="chuck")
        return spun(bit, frame, (8, S_BORE, 0))

    return Drill("steel_drill", k, body, 4, moving, grip, [support] * 4, steel_bit)


# ------------------------------------------------------------------------------------------ Tungsten Carbide Drill
# The heavy motor drill: a bigger gunmetal housing with an amber band and tank, and a broad carbide bit with a cross of
# cutters at its tip (it bores 3x3). A glowing green window on the left of the housing shows it can burn a fuel rod.
C_BORE = 10.8


def carbide_bit():
    out = []
    twist(CARBIDE, out, 8, C_BORE, -8.2, -3.4, 1.9, 3, "carbide")
    # The cutting head: two crossed blades, their ground edges bright, and a centre point.
    CARBIDE.box(out, 8 - 2.4, C_BORE - 0.45, -9.6, 8 + 2.4, C_BORE + 0.45, -8.2, "carbide", north="edge", name="cutter")
    CARBIDE.box(out, 8 - 0.44, C_BORE - 2.4, -9.59, 8 + 0.44, C_BORE + 2.4, -8.21, "carbide", north="edge")
    point(CARBIDE, out, 8, C_BORE, -11.0, -9.6, 1.6, "edge")
    return out


def carbide_drill():
    k = CARBIDE
    body, grip, support = motor_body(k, C_BORE, 2.8, 3.2, -1.0, 1.8, 11.6, "body", "body_dark", "amber", "rubber", "amber", "rubber")
    k.box(body, 8 - 2.81, C_BORE - 0.6, 7.0, 8 - 2.8, C_BORE + 1.6, 9.6, "glow", emit=True, name="fuel_window",
          north=None, south=None, east=None, up=None, down=None)

    def moving(frame):
        bit = carbide_bit()
        k.box(bit, 8 - 1.6, C_BORE - 1.6, -3.4, 8 + 1.6, C_BORE + 1.6, -1.0, "body_dark", name="chuck")
        return spun(bit, frame, (8, C_BORE, 0))

    return Drill("tungsten_carbide_drill", k, body, 4, moving, grip, [support] * 4, carbide_bit)


# ---------------------------------------------------------------------------------------------------- Star Drill
# The carbide drill's frame in star steel with silver trim, a glowing star crystal bit in a violet collar, and a star
# crystal core shining from the housing's flank.


def star_bit():
    out = []
    twist(STAR, out, 8, C_BORE, -8.6, -3.4, 1.7, 4, "crystal", emit=True)
    point(STAR, out, 8, C_BORE, -10.8, -8.6, 1.7, "crystal", emit=True)
    return out


def star_drill():
    k = STAR
    body, grip, support = motor_body(k, C_BORE, 2.8, 3.2, -1.0, 1.8, 11.6, "body", "body_dark", "trim", "rubber", "body_dark", "rubber")
    k.box(body, 8 - 2.81, C_BORE - 0.6, 7.0, 8 - 2.8, C_BORE + 1.6, 9.6, "core", emit=True, name="star_core",
          north=None, south=None, east=None, up=None, down=None)

    def moving(frame):
        bit = star_bit()
        k.box(bit, 8 - 1.6, C_BORE - 1.6, -3.4, 8 + 1.6, C_BORE + 1.6, -1.0, "violet", name="collar")
        return spun(bit, frame, (8, C_BORE, 0))

    return Drill("star_drill", k, body, 4, moving, grip, [support] * 4, star_bit)


# ======================================================================================================= displays


def fitted(rotation, elements, size, axes=(0, 1)):
    """Scale and translation that fit the elements, turned by rotation, into size pixels of the slot (along the given
    axes: the slot's x and y, or all three for an item that tumbles on the ground), centred."""
    m = display_matrix(rotation)
    pts = []
    for e in elements:
        corners = [[(e["from"], e["to"])[(i >> a) & 1][a] for a in range(3)] for i in range(8)]
        r = e.get("rotation")
        if r:
            rm, o = element_rotation(e)
            corners = [[v + o[a] for a, v in enumerate(apply(rm, [c[a] - o[a] for a in range(3)]))] for c in corners]
        pts += [apply(m, [v - 8 for v in c]) for c in corners]
    lo = [min(p[a] for p in pts) for a in range(3)]
    hi = [max(p[a] for p in pts) for a in range(3)]
    scale = round(size / max(hi[a] - lo[a] for a in axes), 3)
    return scale, [round(-(lo[a] + hi[a]) / 2 * scale, 3) for a in range(3)]


def still_displays(elements, size=15.0):
    """In the inventory, in item frames and on the ground."""
    rot = [round(v, 2) for v in GUI_ROT]
    scale, tr = fitted(rot, elements, size)
    flat_scale, flat_tr = fitted([0, 0, 0], elements, size * 0.6, (0, 1, 2))
    return [("gui", display(rot, tr, scale)), ("ground", display([0, 0, 0], [flat_tr[0], flat_tr[1] + 2.0, flat_tr[2]], flat_scale)),
            ("fixed", display(rot, tr, scale))]


def held_displays(grip, elements):
    fp = display(FP_ROT, placed(FP_ROT, FP_SCALE, grip, FP_GRIP_TARGET), FP_SCALE)
    tp = display([0, 0, 0], placed([0, 0, 0], TP_SCALE, grip, TP_GRIP_TARGET), TP_SCALE)
    return OrderedDict([("thirdperson_righthand", tp), ("thirdperson_lefthand", tp), ("firstperson_righthand", fp),
                        ("firstperson_lefthand", fp)] + still_displays(elements))


def part_displays(elements):
    """A loose bit: on the diagonal in the inventory, held like any small item."""
    c = bounds_centre(elements)
    held = [("thirdperson_righthand", display([0, 0, 0], centred([0, 0, 0], 0.6, c, 2.0), 0.6)),
            ("thirdperson_lefthand", display([0, 0, 0], centred([0, 0, 0], 0.6, c, 2.0), 0.6)),
            ("firstperson_righthand", display([0, -8, 0], centred([0, -8, 0], 0.6, c, 3.0), 0.6)),
            ("firstperson_lefthand", display([0, -8, 0], centred([0, -8, 0], 0.6, c, 3.0), 0.6))]
    return OrderedDict(held + still_displays(elements, 13.0))


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 3) for i in range(3)]


# ========================================================================================================= output
DRILLS = [hand_drill(), steel_drill(), carbide_drill(), star_drill()]
BITS = {"iron_drill_bit": DRILLS[0], "steel_drill_bit": DRILLS[1], "carbide_drill_bit": DRILLS[2], "star_drill_bit": DRILLS[3]}
STILL = ["gui", "ground", "fixed"]

for d in DRILLS:
    credit = f"mymod {d.name} - generated by tools/gen_drills.py"
    whole = d.body + d.moving(0)
    check(d.name, whole)
    displays = held_displays(d.grip, whole)
    write(os.path.join(MODELS, d.name + ".json"), model(d.kit, d.body, displays, credit=credit))
    frames = []
    for f in range(d.frames):
        parts = d.moving(f)
        check(f"{d.name}_spin_{f}", d.body + parts)
        write(os.path.join(MODELS, f"{d.name}_spin_{f}.json"), model(d.kit, parts, displays, credit=credit))
        frames.append(ref(f"{d.name}_spin_{f}"))
    still = composite(ref(d.name), frames[0])
    spinning = composite(ref(d.name), range_dispatch("mymod:drill_spin", [(f, frames[f]) for f in range(1, d.frames)], frames[0]))
    write(os.path.join(ITEMS, d.name + ".json"), {"model": OrderedDict([
        ("type", "minecraft:select"), ("property", "minecraft:display_context"),
        ("cases", [OrderedDict([("when", STILL), ("model", still)])]), ("fallback", spinning)])})
    print(f"{d.name}: {len(whole)} elements, {d.frames} frames")
    print(f"   grip {fp_point(displays, d.grip)}  support {[fp_point(displays, s) for s in d.support[:d.frames]] if d.name == 'hand_drill' else fp_point(displays, d.support[0])}")

for name, d in BITS.items():
    bit = d.bit_part()
    check(name, bit)
    write(os.path.join(MODELS, name + ".json"), model(d.kit, bit, part_displays(bit), credit=f"mymod {name} - generated by tools/gen_drills.py"))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


# ========================================================================================================== data


def write_json(path, obj):
    write(path, obj)


def merge_tag(path, values):
    """Adds values to a tag file, keeping whatever's there (other features share the vanilla tag files)."""
    full = os.path.join(DATA, path + ".json")
    existing = json.load(open(full))["values"] if os.path.exists(full) else []
    write(full, {"values": existing + [v for v in values if v not in existing]})


def unmerge_tag(path, values):
    full = os.path.join(DATA, path + ".json")
    if os.path.exists(full):
        existing = json.load(open(full))["values"]
        write(full, {"values": [v for v in existing if v not in values]})


def merge_json(path, entries):
    with open(path) as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def advancement(name, category, unlock_items):
    criteria = OrderedDict()
    for i, item in enumerate(unlock_items):
        criteria["has_item" if i == 0 else f"has_item_{i}"] = {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"}
    criteria["has_the_recipe"] = {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, "mymod", "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria), ("requirements", [list(criteria)]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shaped(name, pattern, key, unlock, category="equipment"):
    write(os.path.join(DATA, "mymod", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", {"count": 1, "id": "mymod:" + name})]))
    advancement(name, category, unlock)


M = "mymod:"
# Bits: twisted bars of their metal, the zig-zag of the pattern the twist. The iron bit unlocks with tech scrap, so it
# shows up around the Weapon Station and the first guns.
shaped("iron_drill_bit", [" I", "I ", " I"], {"I": "minecraft:iron_ingot"}, [M + "tech_scrap"], category="misc")
shaped("steel_drill_bit", [" S", "S ", " S"], {"S": M + "steel_ingot"}, [M + "steel_ingot"], category="misc")
shaped("carbide_drill_bit", [" C", "S ", " C"], {"C": M + "tungsten_carbide", "S": M + "steel_ingot"}, [M + "tungsten_carbide"],
       category="misc")
shaped("star_drill_bit", [" X", "S ", " X"], {"X": M + "star_crystal_shard", "S": M + "star_steel_ingot"},
       [M + "star_crystal_shard", M + "star_steel_ingot"], category="misc")
# The drills: a bit up top right, the works in the middle, the grip bottom left.
shaped("hand_drill", [" CB", "STI", "S  "],
       {"B": M + "iron_drill_bit", "C": "minecraft:copper_ingot", "S": "minecraft:stick", "T": M + "tech_scrap", "I": "minecraft:iron_ingot"},
       [M + "iron_drill_bit", M + "tech_scrap"])
shaped("steel_drill", ["PAB", "ECP", "R  "],
       {"B": M + "steel_drill_bit", "P": M + "steel_plate", "A": M + "servo_arm", "E": M + "empty_canister", "C": M + "computer_chip",
        "R": M + "rubber"}, [M + "steel_drill_bit", M + "servo_arm"])
shaped("tungsten_carbide_drill", [" TB", "CDT", "P  "],
       {"B": M + "carbide_drill_bit", "T": M + "tungsten_carbide", "C": M + "computer_chip", "D": M + "steel_drill", "P": M + "steel_plate"},
       [M + "carbide_drill_bit", M + "tungsten_carbide"])
shaped("star_drill", [" XB", "SDX", "S  "],
       {"B": M + "star_drill_bit", "X": M + "star_crystal_shard", "S": M + "star_steel_ingot", "D": M + "tungsten_carbide_drill"},
       [M + "star_drill_bit"])

# Tags. Blocks in mymod:needs_drill take any drill (the Hand Drill is enough), mymod:needs_powered_drill a running
# Steel Drill or better. Every vanilla tool tier counts them incorrect, so pickaxes chip at them slowly for nothing.
DRILL_ITEMS = [M + d.name for d in DRILLS]
merge_tag("mymod/tags/item/drills", DRILL_ITEMS)
merge_tag("minecraft/tags/item/pickaxes", ["#mymod:drills"])
unmerge_tag("minecraft/tags/item/pickaxes", [M + "tungsten_carbide_drill"])
merge_tag("mymod/tags/item/star_steel_tools", [M + "star_drill"])
merge_tag("mymod/tags/item/repairs_hand_drill", ["minecraft:iron_ingot"])
merge_tag("mymod/tags/item/repairs_steel_drill", [M + "steel_ingot"])
merge_tag("mymod/tags/block/needs_drill", [M + "meteorite_stone", M + "tungsten_ore"])
merge_tag("mymod/tags/block/needs_powered_drill", [M + "meteoric_iron_ore"])
unmerge_tag("minecraft/tags/block/needs_diamond_tool", [M + "tungsten_ore"])
unmerge_tag("minecraft/tags/block/needs_iron_tool", [M + "meteoric_iron_ore"])
merge_tag("mymod/tags/block/incorrect_for_hand_drill", ["#minecraft:needs_diamond_tool", "#mymod:needs_powered_drill"])
merge_tag("mymod/tags/block/incorrect_for_powered_drill", [])
for tier in ("wooden", "stone", "copper", "iron", "gold", "diamond", "netherite"):
    merge_tag(f"minecraft/tags/block/incorrect_for_{tier}_tool", ["#mymod:needs_drill", "#mymod:needs_powered_drill"])

merge_json(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("item.mymod.hand_drill", "Hand Drill"),
    ("item.mymod.steel_drill", "Steel Drill"),
    ("item.mymod.tungsten_carbide_drill", "Tungsten Carbide Drill"),
    ("item.mymod.star_drill", "Star Drill"),
    ("item.mymod.iron_drill_bit", "Iron Drill Bit"),
    ("item.mymod.steel_drill_bit", "Steel Drill Bit"),
    ("item.mymod.carbide_drill_bit", "Carbide Drill Bit"),
    ("item.mymod.star_drill_bit", "Star Drill Bit"),
    ("item.mymod.drill.hand_hint", "Hand-cranked: needs no fuel. Bores meteorite stone and tungsten ore"),
    ("item.mymod.drill.powered_hint", "Bores any drill-only rock while it has fuel"),
    ("item.mymod.drill.fuel", "Fuel: %s%% (%s / %s)"),
    ("item.mymod.drill.empty", "Out of fuel: digs like a stone pickaxe"),
    ("item.mymod.drill.refuel", "Use to refuel with %s"),
    ("item.mymod.drill.mode_wide", "Mode: 3×3 (sneak to dig one block)"),
    ("item.mymod.drill.mode_single", "Mode: single block"),
    ("item.mymod.drill.toggle_hint", "Sneak and use to switch 1×1 / 3×3"),
    ("item.mymod.drill.switched_wide", "Drilling 3×3"),
    ("item.mymod.drill.switched_single", "Drilling one block"),
    ("item.mymod.drill.refuelled", "Refuelled: %s%%"),
    ("item.mymod.drill.tank_full", "The tank's too full for another %s"),
    ("item.mymod.drill.no_fuel", "No fuel to refill it with"),
    ("item.mymod.drill.or", "%s or %s"),
    ("subtitles.mymod.drill_motor", "Drill whirrs"),
    ("subtitles.mymod.hand_drill_crank", "Drill gears click"),
]))
merge_json(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("drill_motor", {"subtitle": "subtitles.mymod.drill_motor", "sounds": ["mymod:drill_motor"]}),
    ("hand_drill_crank", {"subtitle": "subtitles.mymod.hand_drill_crank", "sounds": ["mymod:hand_drill_crank"]}),
]))
write(os.path.join(ASSETS, "particles", "drill_spark.json"), {"textures": [f"minecraft:spark_{i}" for i in range(7, -1, -1)]})

# ========================================================================================================= sounds
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
    """ffmpeg's built-in Vorbis encoder only writes stereo, which Minecraft doesn't fade with distance, so DrillSound
    fades itself, as the flamethrower's roar does."""
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


def motor(seconds=1.0):
    """The motor's whirr, seamless when looped: a whining fundamental with its harmonics (whole cycles in the loop), the
    buzz of the gear teeth meshing, a little brush noise, and a slow wobble as it labours."""
    sr = random.Random(11)
    n = int(RATE * seconds)
    noise = highpass([sr.uniform(-1, 1) for _ in range(n)], 2500)
    noise = lowpass(noise, 7000)
    out = []
    for i in range(n):
        t = i / RATE
        wobble = 1.0 + 0.08 * math.sin(math.tau * 6 * t)
        whine = (0.55 * math.sin(math.tau * 220 * t) + 0.3 * math.sin(math.tau * 440 * t) + 0.18 * math.sin(math.tau * 660 * t)
                 + 0.1 * math.sin(math.tau * 1320 * t))
        # The gears: a rough pulse train at 110 Hz, its edges softened.
        gear = math.tanh(6 * math.sin(math.tau * 110 * t)) * 0.18
        out.append((whine + gear) * wobble + noise[i] * 0.35)
    return out


def crank(seconds=1.0):
    """The Hand Drill's gears turning: soft ticks of the teeth (16 a second) over a faint grinding hum, louder twice a
    turn where the crank pushes hardest."""
    sr = random.Random(13)
    n = int(RATE * seconds)
    rough = lowpass(highpass([sr.uniform(-1, 1) for _ in range(n)], 600), 3000)
    out = []
    period = RATE // 16
    for i in range(n):
        t = i / RATE
        since = (i % period) / RATE
        tick = sr.uniform(-1, 1) * math.exp(-since / 0.003) * 0.9 + math.sin(math.tau * 1900 * since) * math.exp(-since / 0.006) * 0.5
        push = 0.75 + 0.25 * math.cos(math.tau * 2 * t)
        out.append((tick + rough[i] * 0.25 + 0.12 * math.sin(math.tau * 90 * t)) * push)
    return out


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping sounds")
else:
    write_ogg("drill_motor", motor(), 0.8)
    write_ogg("hand_drill_crank", crank(), 0.6)
print("drill assets written")
