"""Builds the Trombone's assets: the instrument, its slide at fifteen positions, and its sounds.

A tenor trombone in lacquered brass with nickel-silver inner slides, every part built for its shape:
- the slide: two inner tubes (nickel, fixed) running forward from the player, with the inner slide brace across them
  near the face; over them the outer slide (brass), two tubes with ferrules at their back ends, the outer brace the
  slide hand holds, and the crook at the front end with its rubber bumper and a little water key;
- the bell section to the player's left: the gooseneck crossing over from the slide's lower tube, the neckpipe running
  back past the cheek to the tuning slide behind the head, and the bell tube coming forward from it to a flared bell
  (stepped octagons, then dodecagons) whose mouth is painted dark down its throat and bright at the rim; a brace across
  the tuning slide and the bell brace by the face, tied to the inner slide brace by a cross bar;
- the mouthpiece: receiver, shank, cup and rim, the rim's face showing the dark cup.
Materials: yellow brass and a darker brass, nickel silver, cork and black rubber, all as smooth ramps from vanilla
colours (gold block, raw gold, iron block, smooth stone, stripped oak, black wool). No noise.

The outer slide (with crook, braces and water key) is its own set of models, one per note of the two-octave Bb major
scale the trombone plays: frame f slides it out f * EXTEND / 14 pixels, frame 0 fully in (the top Bb), frame 14 fully
out (the bottom Bb). mymod:trombone_slide picks the frame (TromboneSlide). Every frame together with the body is checked
for z-fighting in world space (tools/coplanar.py).

Model space is in pixels, the instrument pointing toward -z with +y up and +x the player's right. The mouthpiece is at
the back, at the player's lips. Held items normally sit in the hand; the trombone is drawn from the head in third person
(client.trombone.mixin.PlayerTromboneHeldMixin) and its display transform puts the mouthpiece at the mouth. The hand anchors this prints go into
TromboneHands (first person in item space, third person in head space).

Usage: python3 tools/gen_trombone.py src/main/resources/assets/mymod [--models-only]"""
import math, os, random, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, copy, move, rotate, rx, ry, rz, write, model, ref, range_dispatch, composite, display,
                    display_matrix, mix, element_rotation, apply, look, png)
from coplanar import check_elements

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

# ============================================================================================================ atlas

K = Kit("trombone", "item/trombone", seed=76)


def ramp(top, bottom):
    return lambda x, y, w, h: K.rgb(mix(top, bottom, y / max(1, h - 1)))


def radial(rim, rim_edge, inner, throat, rim_width=0.16):
    """A picture graded along u only, from the middle (the throat) to both ends (the rim): for the faces of the bell's
    and the cup's bands, each of which runs across the whole mouth through its centre."""
    def f(x, y, w, h):
        d = abs((x + 0.5) / w - 0.5) * 2.0
        if d > 1.0 - rim_width:
            return K.rgb(mix(rim_edge, rim, (d - (1.0 - rim_width)) / rim_width))
        t = d / (1.0 - rim_width)
        return K.rgb(mix(throat, inner, t ** 1.6))
    return f


K.swatch("brass", 0, 0, 4, 4, ramp(0xF4D77E, 0xC59532))          # gold block's lights to raw gold's
K.swatch("brass_dark", 4, 0, 8, 4, ramp(0xC2912F, 0x8C621C))
K.swatch("brass_bright", 8, 0, 12, 4, ramp(0xFFEDB0, 0xE2B85A))
K.swatch("nickel", 12, 0, 16, 4, ramp(0xEEEEE8, 0xA9ACA8))        # iron block to smooth stone
K.swatch("rubber", 0, 4, 4, 8, ramp(0x2C2C30, 0x151518))          # black wool
K.swatch("cork", 4, 4, 8, 8, ramp(0xCDA273, 0x9A7048))            # stripped oak
K.swatch("mouth", 8, 4, 12, 8, radial(0xFFF0B8, 0xE8C364, 0xB98A2C, 0x3A250A), picture=True)
K.swatch("cup", 12, 4, 16, 8, radial(0xFFFFFF, 0xC9CCC8, 0x8E918E, 0x1C1C1E, rim_width=0.3), picture=True)
K.swatch("brass_shadow", 0, 8, 4, 12, ramp(0x8C621C, 0x5E3F10))
K.save(TEX)

# ===================================================================================================== dimensions

XS, YU, YL = 8.0, 8.0, 5.6        # the slide's plane and its upper (mouthpiece) and lower tubes
XB, YBT = 2.5, 8.8                # the bell section's plane and its bell tube; its neckpipe runs at YL
R_INNER, R_OUTER = 0.42, 0.62
INNER_FRONT = 0.5                 # the inner tubes' front ends, inside the outer slide even fully out
INNER_BRACE_Z = 14.4              # the inner slide brace, by the face, and the bell brace and cross bar beside it
GOOSENECK_Z = 16.3
TUNING_Z = 28.1                   # the tuning slide's bend, behind the player's head
OUTER = (-1.5, 12.6)              # the outer slide's tubes, fully in
OUTER_BRACE_Z = 10.4
CROOK_Z = -2.1
EXTEND = 11.0                     # how far the slide goes out
FRAMES = 15
MOUTHPIECE_RIM = 19.8
# The hands, as a trombonist holds one: the off hand grips the bell brace and the inner slide brace together by the face
# (it holds the instrument up); the main hand works the outer slide brace by its grip bar.
GRIP = (5.6, 6.8, INNER_BRACE_Z + 0.2)
SLIDE_HAND = (XS + 0.55, (YU + YL) / 2, OUTER_BRACE_Z)
MOUTH = (XS, YU, MOUTHPIECE_RIM)
BELL_RIM_Z = -3.6
T15 = math.tan(math.radians(15))


def dodeca(out, z0, z1, r, mat, cx, cy, cap=None, south_cap=None, name=None):
    """A twelve-sided prism along z: six bands through the axis 30 degrees apart, each as wide as a side, so their ends
    make up the dodecagon; ends staggered a hair so the overlapping caps don't fight."""
    s = r * T15
    cap = cap or mat
    for k in range(6):
        d = 0.01 * k
        K.box(out, cx - r, cy - s, z0 + d, cx + r, cy + s, z1 - d, mat, north=cap, south=south_cap or mat,
              rot=("z", 30.0 * k, (cx, cy, z0)) if k else None, name=name if k == 0 else None)


def centre_origins(elements):
    """Moves each band's rotation origin (a turn about z) along z to the band's middle: the same turn, but once the
    rod is stood up its unturned box then sits round its middle, inside the -16..32 bounds Minecraft allows."""
    for e in elements:
        if "rotation" in e:
            e["rotation"]["origin"][2] = round((e["from"][2] + e["to"][2]) / 2, 4)
    return elements


def rod_y(out, x, z, y0, y1, r, mat, cap=None, name=None):
    """An octagonal rod standing along y at (x, z)."""
    one = []
    K.rod(one, y0 + z, y1 + z, r, mat, cx=x, cy=0.0, cap=cap, name=name)
    out += rotate(centre_origins(one), rx(-90), (x, 0.0, z))


def rod_x(out, y, z, x0, x1, r, mat, cap=None, name=None):
    """An octagonal rod lying along x at (y, z)."""
    one = []
    K.rod(one, x0 + z, x1 + z, r, mat, cx=0.0, cy=y, cap=cap, name=name)
    out += rotate(centre_origins(one), ry(90), (0.0, y, z))


# =========================================================================================================== slide


def outer_slide():
    """The moving part: outer tubes with their back ferrules, the crook with bumper and water key, the outer brace."""
    out = []
    for y, nm in ((YU, "outer_slide_upper"), (YL, "outer_slide_lower")):
        K.rod(out, OUTER[0], OUTER[1], R_OUTER, "brass", cx=XS, cy=y, cap="brass_dark", name=nm)
        K.rod(out, OUTER[1] - 0.7, OUTER[1] + 0.12, 0.78, "brass_bright", cx=XS, cy=y, cap="brass_dark",
              name="ferrule" if y == YU else None)
        K.rod(out, OUTER[0] + 1.2, OUTER[0] + 1.75, 0.74, "brass_bright", cx=XS, cy=y, cap="brass", name="crook_ferrule" if y == YU else None)
    # The crook: a standing tube joining the two at the front, capped round each end.
    rod_y(out, XS, CROOK_Z, YL - 0.62, YU + 0.62, R_OUTER + 0.02, "brass", cap="brass_bright", name="crook")
    # Rubber bumper under it, and the water key: a nickel lever lying along the crook's front with a cork pad.
    K.box(out, XS - 0.45, YL - 1.35, CROOK_Z - 0.45, XS + 0.45, YL - 0.6, CROOK_Z + 0.45, "rubber", name="bumper")
    K.box(out, XS - 0.18, YL + 0.2, CROOK_Z - 0.95, XS + 0.18, YU - 0.1, CROOK_Z - 0.7, "nickel", name="water_key")
    K.box(out, XS - 0.3, YL - 0.35, CROOK_Z - 0.9, XS + 0.3, YL + 0.2, CROOK_Z - 0.55, "cork", name="water_key_pad")
    # The outer brace the slide hand holds, standing between the tubes, with a grip bar on the outside.
    rod_y(out, XS, OUTER_BRACE_Z, YL, YU, 0.36, "brass", name="outer_brace")
    K.box(out, XS + 0.3, YL + 0.5, OUTER_BRACE_Z - 0.3, XS + 0.85, YU - 0.5, OUTER_BRACE_Z + 0.3, "nickel",
          name="slide_grip")
    return out


SLIDE = outer_slide()


def slide_at(e):
    """The outer slide moved out by e pixels (the same UVs every frame, so the brass doesn't shimmer as it moves)."""
    return move(copy(SLIDE), dz=-e) if e else copy(SLIDE)


# ============================================================================================================ body


def bell():
    """The bell tube from the tuning slide forward, flaring to the rim."""
    out = []
    K.rod(out, 6.4, TUNING_Z, 0.7, "brass", cx=XB, cy=YBT, cap="brass_dark", name="bell_tube")
    # The flare: a horn curve, r growing ever faster toward the rim, in short overlapping steps (octagons while it's
    # narrow, dodecagons once it opens up) so it reads as a smooth cone from behind as well as from the side.
    z_back, z_front, r_back, r_front, n = 7.0, -3.15, 0.85, 3.98, 13
    for k in range(n):
        t0, t1 = k / n, (k + 1) / n
        z1 = z_back + (z_front - z_back) * t0
        z0 = z_back + (z_front - z_back) * t1
        r = round(r_back + (r_front - r_back) * t1 ** 2.2, 3)
        if k:
            z1 += 0.18      # overlap the step behind
        if r < 1.9:
            K.rod(out, round(z0, 3), round(z1, 3), r, "brass", cx=XB, cy=YBT, cap="brass", name="bell_flare" if k == 0 else None)
        else:
            dodeca(out, round(z0, 3), round(z1, 3), r, "brass", XB, YBT, cap="brass", south_cap="brass",
                   name="bell_bloom" if r >= 1.9 and k == 7 else None)
    # The rim: a bead a little proud of the last step, its face the bell's mouth.
    dodeca(out, BELL_RIM_Z, -3.0, 4.3, "brass_bright", XB, YBT, cap="mouth", south_cap="brass_dark", name="bell_rim")
    return out


def body():
    out = []
    # Inner slide: two nickel tubes, the upper running on back into the mouthpiece receiver.
    K.rod(out, INNER_FRONT, 16.6, R_INNER, "nickel", cx=XS, cy=YU, name="inner_slide_upper")
    K.rod(out, INNER_FRONT + 0.05, GOOSENECK_Z + 0.2, R_INNER, "nickel", cx=XS, cy=YL, name="inner_slide_lower")
    rod_y(out, XS, INNER_BRACE_Z, YL, YU, 0.38, "nickel", name="inner_brace")
    # Slide lock collar on the lower tube, at the brace.
    K.rod(out, INNER_BRACE_Z - 1.6, INNER_BRACE_Z - 0.6, 0.6, "nickel", cx=XS, cy=YL, name="slide_lock")
    # Gooseneck: crosses from the slide's lower tube to the bell section, which runs back past the cheek.
    rod_x(out, YL, GOOSENECK_Z, XB - 0.05, XS + 0.05, 0.58, "brass", name="gooseneck")
    K.rod(out, GOOSENECK_Z - 0.5, TUNING_Z + 0.05, 0.62, "brass", cx=XB, cy=YL, name="neckpipe")
    # Tuning slide: the bend behind the head, a nickel brace across it, and a weight knob.
    rod_y(out, XB, TUNING_Z + 0.4, YL - 0.7, YBT + 0.75, 0.74, "brass", cap="brass_bright", name="tuning_slide")
    rod_y(out, XB, TUNING_Z - 3.6, YL + 0.4, YBT - 0.4, 0.3, "nickel", name="tuning_brace")
    rod_x(out, (YL + YBT) / 2, TUNING_Z - 3.6, XB - 1.1, XB - 0.2, 0.42, "nickel", name="tuning_knob")
    # Bell brace by the face, and the cross bar tying it to the inner slide brace: where the off hand holds it.
    rod_y(out, XB, INNER_BRACE_Z, YL, YBT, 0.36, "nickel", name="bell_brace")
    rod_x(out, (YU + YL) / 2, INNER_BRACE_Z, XB, XS, 0.33, "nickel", name="cross_bar")
    # Mouthpiece: receiver, shank, cup, rim (its face the cup).
    K.rod(out, 15.4, 16.5, 0.62, "nickel", cx=XS, cy=YU, cap="nickel", name="receiver")
    K.rod(out, 16.4, 18.1, 0.46, "nickel", cx=XS, cy=YU, name="shank")
    K.rod(out, 17.9, 18.7, 0.66, "nickel", cx=XS, cy=YU, name="throat")
    K.rod(out, 18.6, 19.4, 0.9, "nickel", cx=XS, cy=YU, name="cup")
    K.rod(out, 19.3, MOUTHPIECE_RIM, 1.08, "nickel", cx=XS, cy=YU, cap="nickel", name="rim")
    K.box(out, XS - 0.9, YU - 0.9, MOUTHPIECE_RIM, XS + 0.9, YU + 0.9, MOUTHPIECE_RIM + 0.02, "cup",
          **{f: None for f in ("north", "east", "west", "up", "down")}, name="cup_face")
    return out + bell()


# ======================================================================================================= displays


def corners(elements):
    for e in elements:
        rot = element_rotation(e)
        f, t = e["from"], e["to"]
        for x in (f[0], t[0]):
            for y in (f[1], t[1]):
                for z in (f[2], t[2]):
                    p = [x, y, z]
                    if rot:
                        m, o = rot
                        p = [v + o[i] for i, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))]
                    yield p


def fit(elements, rot, size=15.0):
    m = display_matrix(rot)
    pts = [[sum(m[i][k] * p[k] for k in range(3)) for i in range(2)] for p in corners(elements)]
    span = max(max(p[i] for p in pts) - min(p[i] for p in pts) for i in range(2))
    return round(size / span, 3)


def centred_on(rot, scale, elements):
    """Translation centring the turned elements' screen bounds in the slot."""
    m = display_matrix(rot)
    pts = [[sum(m[i][k] * (p[k] - 8) for k in range(3)) * scale for i in range(3)] for p in corners(elements)]
    return [round(-(max(p[i] for p in pts) + min(p[i] for p in pts)) / 2, 3) for i in range(3)]


def placed(rot, scale, point, target_blocks):
    """Translation (pixels) putting model point `point` at target (blocks) in the display's space."""
    m = display_matrix(rot)
    c = [(v - 8) for v in point]
    return [round(target_blocks[i] * 16 - sum(m[i][k] * c[k] for k in range(3)) * scale, 3) for i in range(3)]


# True size: a pixel of the model is 2.5 cm.
SCALE = 0.4
# Third person a little bigger, to suit the blocky player's big head.
TP_SCALE = 0.56
# First person: the mouthpiece just under the eye and a touch right, the instrument pointing straight ahead. Item space
# here is after vanilla's right-hand arm offset (0.56, -0.52, -0.72), so the target subtracts it.
FP_ARM = (0.56, -0.52, -0.72)
FP_MOUTH_VIEW = (0.16, -0.4, -0.42)
FP_ROT = [3.0, -18.0, 0.0]
FP_SCALE = 0.7
# Third person (client.trombone.mixin.PlayerTromboneHeldMixin): drawn in head space turned to item axes (x right, y up, -z forward, origin at the
# head's centre); the mouth is two pixels below the centre, just in front of the face.
TP_MOUTH = (0.0, -2.0 / 16, -4.4 / 16)
TP_ROT = [0.0, 0.0, 0.0]


def look_rot(muzzle, top):
    """A display rotation turning the bell end (model -z) toward `muzzle` and the top toward `top` (made square to it)."""
    f = [v / math.sqrt(sum(c * c for c in muzzle)) for v in muzzle]
    d = sum(a * b for a, b in zip(top, f))
    t = [top[i] - d * f[i] for i in range(3)]
    t = [v / math.sqrt(sum(c * c for c in t)) for v in t]
    return look(f, t)


def all_displays(elements):
    fp = display(FP_ROT, placed(FP_ROT, FP_SCALE, MOUTH, [FP_MOUTH_VIEW[i] - FP_ARM[i] for i in range(3)]), FP_SCALE)
    tp = display(TP_ROT, placed(TP_ROT, TP_SCALE, MOUTH, TP_MOUTH), TP_SCALE)
    gui_rot = look_rot((-0.62, 0.55, 0.42), (0.55, 0.62, 0.2))
    gui_scale = fit(elements, gui_rot, 16.5)
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred_on(gui_rot, gui_scale, elements), gui_scale),
        "ground": display([0, 0, 0], centred_on([0, 0, 0], 0.28, elements), 0.28),
        "fixed": display([0, 90, 0], centred_on([0, 90, 0], round(gui_scale * 1.2, 3), elements), round(gui_scale * 1.2, 3)),
    }


def space_point(t, p):
    """A model point in the space a display transform puts the item in (blocks)."""
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


def space_vector(t, v):
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    return [round(sum(m[i][k] * v[k] for k in range(3)) * s / 16, 5) for i in range(3)]


ICON_FRAME = 4
BODY = body()
everything = BODY + slide_at(EXTEND * ICON_FRAME / (FRAMES - 1))
DISPLAYS = all_displays(everything)
CREDIT = "mymod trombone - generated by tools/gen_trombone.py"


def out_model(name, elements):
    check(name, elements)
    check_elements(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, DISPLAYS, credit=CREDIT))
    return ref(name)


body_ref = out_model("trombone", BODY)
frames = [out_model(f"trombone_slide_{f}", slide_at(EXTEND * f / (FRAMES - 1))) for f in range(FRAMES)]
for f in range(FRAMES):
    check_elements(f"trombone frame {f}", BODY, slide_at(EXTEND * f / (FRAMES - 1)))
slide = range_dispatch("mymod:trombone_slide", [(f - 0.5, frames[f]) for f in range(1, FRAMES)], frames[0])
write(os.path.join(ITEMS, "trombone.json"), {"model": {
    "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [{"when": ["gui", "ground", "fixed"], "model": composite(body_ref, frames[ICON_FRAME])}],
    "fallback": composite(body_ref, slide)}})

fp, tp = DISPLAYS["firstperson_righthand"], DISPLAYS["thirdperson_righthand"]
print(f"trombone: {len(BODY)} body + {len(SLIDE)} slide elements, {FRAMES} slide frames")
print(f"   firstperson {fp['translation']} rot {fp['rotation']}; thirdperson {tp['translation']}; gui scale {DISPLAYS['gui']['scale'][0]}")
print(f"   FP  grip {space_point(fp, GRIP)}  slide hand {space_point(fp, SLIDE_HAND)} + e * {space_vector(fp, (0, 0, -1))}"
      f"  bell {space_point(fp, (XB, YBT, BELL_RIM_Z))}")
print(f"   TP  grip {space_point(tp, GRIP)}  slide hand {space_point(tp, SLIDE_HAND)} + e * {space_vector(tp, (0, 0, -1))}"
      f"  bell {space_point(tp, (XB, YBT, BELL_RIM_Z))}")

# The Lullaby's Zzz particle: an 8x8 Z, pale lavender with a dark outline, like vanilla's particle sprites.
ZZZ = ["........",
       ".oooooo.",
       ".oZZZZo.",
       "..ooZo..",
       "..oZo...",
       ".oZoooo.",
       ".oZZZZo.",
       ".oooooo."]
ZZZ_COLOURS = {".": (0, 0, 0, 0), "o": (46, 36, 82, 255), "Z": (226, 214, 255, 255)}
png(os.path.join(TEX, "particle", "trombone_zzz.png"), 8, 8, [ZZZ_COLOURS[c] for row in ZZZ for c in row])
write(os.path.join(ASSETS, "particles", "trombone_zzz.json"), {"textures": ["mymod:trombone_zzz"]})

# =========================================================================================================== sounds
# Synthesised: no trombone recording is to hand. A brass tone is a stack of harmonics whose top end opens up as the
# player blows harder: here each harmonic's level follows a steep low-pass round a "brightness" cutoff, with a bump
# round 1.1 kHz (the bell's formant), and a breath of noise on top. Written mono (vorbis_mono.py) so Minecraft places
# and fades them.
if "--models-only" in sys.argv:
    sys.exit(0)

import vorbis_mono  # noqa: E402

RATE = 44100
NOTE_HZ = 233.0           # the sustain loop's pitch: Bb3 (TromboneNotes plays degree 7 at pitch 1)


def brass(f0s, cutoffs, amps, seed=1, harmonics=42, breath=0.0):
    """f0s, cutoffs, amps: one value per sample. Returns the samples (unnormalised)."""
    rnd = random.Random(seed)
    n = len(f0s)
    phases = [rnd.uniform(0, 2 * math.pi) for _ in range(harmonics)]
    out = [0.0] * n
    block = 32
    for b0 in range(0, n, block):
        f0, fc = f0s[b0], cutoffs[b0]
        weights = []
        for h in range(1, harmonics + 1):
            fh = h * f0
            if fh > RATE * 0.45:
                weights.append(0.0)
                continue
            w = h ** -0.45 / math.sqrt(1.0 + (fh / fc) ** 6)
            w *= 1.0 + 0.7 * math.exp(-((fh - 1100.0) / 500.0) ** 2)
            if h == 1:
                w *= 0.75
            weights.append(w)
        live = [(h, w) for h, w in enumerate(weights) if w > 1e-4]
        for i in range(b0, min(n, b0 + block)):
            step = 2 * math.pi * f0s[i] / RATE
            s = 0.0
            for h, w in live:
                s += w * math.sin(phases[h])
            for h in range(harmonics):
                phases[h] += step * (h + 1)
            out[i] = s * amps[i]
    if breath:
        # Noise made of sines at whole-hertz frequencies, so a one-second loop of it repeats seamlessly.
        tones = [(rnd.randint(1800, 6500), rnd.uniform(0, 2 * math.pi)) for _ in range(90)]
        for i in range(n):
            t = i / RATE
            out[i] += breath * amps[i] * sum(math.sin(2 * math.pi * f * t + p) for f, p in tones) / 9.0
    return out


def normalise(xs, peak=0.85):
    top = max(abs(x) for x in xs) or 1.0
    return [x / top * peak for x in xs]


def fade(xs, seconds_in=0.0, seconds_out=0.0):
    n = len(xs)
    a, b = int(seconds_in * RATE), int(seconds_out * RATE)
    for i in range(a):
        xs[i] *= i / a
    for i in range(b):
        xs[n - 1 - i] *= i / b
    return xs


def save(name, xs):
    path = os.path.join(ASSETS, "sounds", name + ".ogg")
    vorbis_mono.encode(xs, path)
    print("wrote", path, f"{len(xs) / RATE:.2f}s")


def note_loop():
    """One second of steady Bb3, looped by TromboneSound: whole cycles of everything (233 Hz, a 3 Hz shimmer in the
    brightness, whole-hertz breath), so it joins up without a click."""
    n = RATE
    f0s = [NOTE_HZ] * n
    cut = [1500.0 + 160.0 * math.sin(2 * math.pi * 3 * i / RATE) for i in range(n)]
    amps = [1.0 + 0.04 * math.sin(2 * math.pi * 5 * i / RATE) for i in range(n)]
    return normalise(brass(f0s, cut, amps, seed=7, breath=0.012), 0.8)


def attack():
    """The start of a note: the lips catch, the tone blats in bright and a little flat, then gives way to the loop."""
    n = int(0.34 * RATE)
    f0s, cut, amps = [], [], []
    for i in range(n):
        t = i / RATE
        f0s.append(NOTE_HZ * 2 ** (-0.45 / 12 * math.exp(-t / 0.035)))
        cut.append(1500.0 + 2600.0 * math.exp(-t / 0.05) * (1 - math.exp(-t / 0.004)))
        amps.append((1 - math.exp(-t / 0.005)) * math.exp(-t / 0.1))
    xs = brass(f0s, cut, amps, seed=11, breath=0.05)
    return fade(normalise(xs, 0.8), 0.0, 0.04)


def phrase(notes, seed, plunger=None, bright=1.0, voices=((1.0, 1.0),)):
    """notes: (start s, length s, Hz, vibrato depth in semitones). plunger(t) -> 0 shut .. 1 open scales the cutoff.
    voices: (pitch ratio, level) layers."""
    end = max(s + l for s, l, _, _ in notes) + 0.25
    n = int(end * RATE)
    total = [0.0] * n
    for ratio, level in voices:
        f0s, cut, amps = [], [], []
        for i in range(n):
            t = i / RATE
            # The note sounding (or the last one started), gliding into each new one over 40 ms.
            cur = [nt for nt in notes if nt[0] <= t] or [notes[0]]
            s, l, hz, vib = cur[-1]
            prev = cur[-2][2] if len(cur) > 1 else hz
            g = min(1.0, (t - s) / 0.04) if t >= s else 1.0
            f = prev + (hz - prev) * g
            held = max(0.0, t - s - 0.25)
            f *= 2 ** (vib * min(1.0, held / 0.4) * math.sin(2 * math.pi * 5.2 * held) / 12)
            f0s.append(f * ratio)
            on = s <= t < s + l
            a = 0.0
            for s2, l2, _, _ in notes:
                if s2 <= t < s2 + l2 + 0.08:
                    a = max(a, (1 - math.exp(-(t - s2) / 0.012)) * (1.0 if t < s2 + l2 else 1 - (t - s2 - l2) / 0.08))
            amps.append(a * level)
            p = plunger(t) if plunger else 1.0
            cut.append((350.0 + 2300.0 * p) * bright * (1.0 + 0.6 * math.exp(-(t - s) / 0.05) if on else 1.0))
        layer = brass(f0s, cut, amps, seed=seed + int(ratio * 10), breath=0.02)
        total = [a + b for a, b in zip(total, layer)]
    return fade(normalise(total, 0.9), 0.0, 0.1)


def sad_trombone():
    """Wah, wah, wah, waaaah: four notes falling a semitone at a time through a plunger mute, the last one long,
    wobbling and wah-wahing as it sags."""
    g3 = 196.0
    notes = [(0.0, 0.42, g3, 0.0), (0.55, 0.42, g3 * 2 ** (-1 / 12), 0.0), (1.1, 0.42, g3 * 2 ** (-2 / 12), 0.0),
             (1.65, 1.9, g3 * 2 ** (-3 / 12), 0.55)]

    def plunger(t):
        for s, l, _, _ in notes[:3]:
            if s <= t < s + l + 0.1:
                u = (t - s) / l
                return max(0.0, math.sin(math.pi * min(1.0, u * 1.15))) ** 1.3
        u = t - notes[3][0]
        # Opens and shuts as the note dies, each time a little less.
        return 0.15 + 0.85 * (0.5 - 0.5 * math.cos(2 * math.pi * 2.6 * u)) * max(0.25, 1.0 - u / 2.2)
    return phrase(notes, 31, plunger)


def fanfare():
    """Ta-ta-ta-taaa up the Bb major chord, bright and doubled an octave down: the War March answering."""
    b3 = 233.08
    notes = [(0.0, 0.13, b3, 0.0), (0.17, 0.13, b3 * 2 ** (4 / 12), 0.0), (0.34, 0.13, b3 * 2 ** (7 / 12), 0.0),
             (0.52, 0.95, b3 * 2, 0.25)]
    return phrase(notes, 43, None, bright=1.6, voices=((1.0, 1.0), (0.5, 0.55)))


save("trombone_note", note_loop())
save("trombone_attack", attack())
save("sad_trombone", sad_trombone())
save("trombone_fanfare", fanfare())
