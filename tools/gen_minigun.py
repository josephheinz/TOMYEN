"""Builds the power armor Minigun's assets: the gun, its three Weapon Station parts, the Hogfire Gun Mount, and the
spin-up, spin, fire and spin-down sounds.

The gun is drawn in as few boxes as hold its shape: six barrels held by two hexagonal clamps, the octagonal rotor
housing behind them, a slab receiver with the feed motor on its left, a carry handle on top, a rubber pistol grip at
the back and an olive drum magazine slung underneath. It's held at the hip: the shooting hand on the rear grip, the
off hand on the carry handle.

The barrel group (barrels and clamps) is its own set of models, posed at six angles 10 degrees apart (the barrels
repeat every 60), picked by mymod:minigun_barrels, so it turns as the gun spins up; with mymod:minigun_heat past
HOT the front of each barrel glows. mymod:minigun_flash picks a muzzle flash while it fires. The Hogfire Gun Mount is
the same barrel group on a short motor housing, a pylon and a saddle plate; HogfireGearLayer draws it on the pig's
shoulders (keep MOUNT_* there in step with BY and MUZZLE here).

Model space is in pixels: the bore runs along z at x = 8, y = 9 with the muzzle toward -z, +x is the gun's right.
GunDisplay and GunHands mirror the first-person transform and grip points printed at the end, so move them together.
With ffmpeg on the path it also synthesises the four sounds.

Usage: python3 tools/gen_minigun.py src/main/resources/assets/mymod"""
import math, os, random, shutil, struct, subprocess, sys, tempfile, wave

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, ry, rz, write, model, ref, range_dispatch, composite, display, placed,
                    centred, bounds_centre, display_matrix, DIAGONAL, mix, flash_models)

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

# Where long guns put the shooting hand (gen_napalm.py's), dropped and pulled in for a gun held at the hip.
FP_GRIP_TARGET = (-0.022 * 16 + 1.2, 0.119 * 16 - 2.2, 0.156 * 16 - 0.8)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))
# Third person, the arms hang this far below the crossbow hold (MinigunPoseMixin), so the gun is turned back up by it.
TP_DROP = 43.0

# ============================================================================================================ atlas
# Clean paint: one colour or one smooth ramp per material, no speckle. Minecraft's face shading does the rest.

K = Kit("minigun", "item/minigun", seed=134)


def flat(colour):
    return lambda x, y, w, h: K.rgb(colour)


def ramp(top, bottom):
    """A smooth ramp down v, lighter at the top."""
    return lambda x, y, w, h: K.rgb(mix(top, bottom, y / max(1, h - 1)))


def heat(along_u):
    """A barrel's glowing front: white-hot at the muzzle end of the face through orange to a dull red."""
    def f(x, y, w, h):
        t = (x / max(1, w - 1)) if along_u else (y / max(1, h - 1))
        col = mix(0xFFE08A, 0xFF7A26, min(1.0, t * 1.6)) if t < 0.62 else mix(0xFF7A26, 0x9A2E1C, (t - 0.62) / 0.38)
        return K.rgb(col)
    return f


K.swatch("gunmetal", 0, 0, 4, 4, ramp(0x50565E, 0x383D43))
K.swatch("gunmetal_dark", 4, 0, 8, 4, flat(0x2A2D31))
K.swatch("barrel", 8, 0, 12, 4, ramp(0x6A7179, 0x474D54))
K.swatch("olive", 12, 0, 16, 4, ramp(0x6E7642, 0x4E5430))
K.swatch("olive_dark", 0, 4, 4, 8, flat(0x41472A))
K.swatch("rubber", 4, 4, 8, 8, flat(0x1D1D1F))
K.swatch("brass", 8, 4, 12, 8, ramp(0xD2AE58, 0xA88838))
K.swatch("bore", 12, 4, 16, 8, lambda x, y, w, h: K.rgb(mix(0x060606, 0x474D54, max(0.0, min(1.0, (math.hypot(x - 7.5, y - 7.5) - 3.0) / 4.0)))),
         picture=True)
# The hot front of a barrel, as a picture so the ramp runs the face's whole length: u along it for the side faces,
# v along it for the top and bottom.
K.swatch("hot_u", 0, 8, 8, 10, heat(True), picture=True)
K.swatch("hot_v", 8, 8, 10, 16, heat(False), picture=True)
K.save(TEX)

# ========================================================================================================== the gun

BX, BY = 8.0, 9.0
MUZZLE = -14.0
BARREL_R = 1.55          # barrel centres from the axis
BARREL_HALF = 0.42       # half a barrel's width
BARREL_END = 3.6         # where the barrels go into the rotor
HOT_LENGTH = 5.0         # how much of each barrel glows when hot
CLAMPS = ((-12.4, -11.4), (-4.2, -3.2))
CLAMP_APOTHEM = 2.35
ROTOR = (3.5, 8.5)
RECEIVER = (8.5, 17.5)
FRAMES = 6
FRAME_STEP = 10.0        # degrees between barrel frames; they repeat after 60
GRIP = (8.0, 3.9, 16.4)          # the shooting hand round the rear pistol grip
SUPPORT = (8.0, 13.6, 12.6)      # the off hand round the carry handle


def hex_prism(out, z0, z1, a, mat, cap=None, name=None):
    """A hexagonal prism along the bore: three bands through the axis 60 degrees apart, each as wide as a side, so their
    ends make up the hexagon. Each band's ends are staggered a hair so the overlapping caps don't z-fight."""
    half = a / math.sqrt(3)
    cap = cap or mat
    for k, angle in enumerate((0, 60, -60)):
        d = 0.01 * k
        K.box(out, BX - a, BY - half, z0 + d, BX + a, BY + half, z1 - d, mat, north=cap, south=cap,
              rot=("z", angle, (BX, BY, z0)) if angle else None, name=name if k == 0 else None)


def barrels(angle, hot):
    """The turning group: six barrels round the axis and the two clamps that hold them, turned `angle` degrees about
    the bore. Hot, the front of each barrel glows."""
    out = []
    for i in range(6):
        one = []
        x0, x1 = BX + BARREL_R - BARREL_HALF, BX + BARREL_R + BARREL_HALF
        y0, y1 = BY - BARREL_HALF, BY + BARREL_HALF
        if hot:
            # A hair wider than the barrel, so its faces sit just proud of the cool part behind.
            K.box(one, x0 - 0.01, y0 - 0.01, MUZZLE, x1 + 0.01, y1 + 0.01, MUZZLE + HOT_LENGTH, "hot_u", emit=True,
                  north="bore", up="hot_v", down="hot_v", south=None, name="barrel_hot" if i == 0 else None)
            K.box(one, x0, y0, MUZZLE + HOT_LENGTH, x1, y1, BARREL_END, "barrel", north=None, south=None)
        else:
            K.box(one, x0, y0, MUZZLE, x1, y1, BARREL_END, "barrel", north="bore", south=None, name="barrel" if i == 0 else None)
        out += rotate(one, rz(60.0 * i), (BX, BY, MUZZLE))
    for z0, z1 in CLAMPS:
        hex_prism(out, z0, z1, CLAMP_APOTHEM, "gunmetal_dark", name="barrel_clamp")
    return rotate(out, rz(angle), (BX, BY, MUZZLE)) if angle else out


def rotor():
    out = []
    K.rod(out, ROTOR[0], ROTOR[1], 3.0, "gunmetal", cap="gunmetal_dark", cx=BX, cy=BY, name="rotor_housing")
    return out


def receiver(z1=RECEIVER[1]):
    """The slab receiver behind the rotor, with the feed motor on its left."""
    out = []
    K.box(out, 4.9, 5.2, RECEIVER[0], 11.1, 12.0, z1, "gunmetal", north="gunmetal_dark", south="gunmetal_dark", name="receiver")
    K.box(out, 3.7, 7.0, RECEIVER[0] + 1.2, 4.9, 10.6, RECEIVER[0] + 5.6, "gunmetal_dark", name="feed_motor")
    K.box(out, 3.5, 8.2, RECEIVER[0] + 2.0, 3.7, 9.4, RECEIVER[0] + 4.8, "brass", name="feed_motor_plate")
    return out


def handle():
    out = []
    for z in (9.6, 15.0):
        K.box(out, 7.4, 12.0, z, 8.6, 14.2, z + 1.0, "gunmetal_dark", name="carry_handle_post")
    K.box(out, 7.2, 14.2, 9.2, 8.8, 15.2, 16.4, "rubber", name="carry_handle")
    return out


def grip():
    out = []
    g = []
    K.box(g, 7.1, 1.4, 15.2, 8.9, 5.2, 17.4, "rubber", name="pistol_grip")
    out += rotate(g, rx(-14), (8.0, 5.2, 16.3))
    K.box(out, 7.75, 3.9, 14.0, 8.25, 5.2, 14.5, "brass", name="trigger")
    return out


DRUM = (8.0, 2.5, 11.4)          # the drum's centre: it lies across the gun under the receiver
DRUM_R = 3.0
DRUM_HALF = 3.4


def drum():
    """The olive drum magazine across the gun under the receiver, an octagon end on, with a brass feed lip."""
    out = []
    cx, cy, cz = DRUM
    K.rod(out, cz - DRUM_HALF, cz + DRUM_HALF, DRUM_R, "olive", cap="olive_dark", cx=cx, cy=cy, name="drum")
    rotate(out, ry(90), DRUM)
    K.box(out, 6.6, 4.85, cz - 3.15, 9.4, 5.3, cz - 2.35, "brass", name="feed_lip")
    return out


def body():
    return rotor() + receiver() + handle() + grip() + drum()


# ============================================================================================================ mount


def mount_body():
    """The Hogfire mount: the rotor on a short motor housing, a pylon under it and the saddle plate that straps to the
    harness."""
    out = rotor() + receiver(z1=RECEIVER[0] + 4.6)
    K.box(out, 7.2, 1.5, 9.0, 8.8, 5.2, 12.6, "gunmetal_dark", name="pylon")
    K.box(out, 4.6, 0.9, 6.0, 11.4, 1.5, 15.0, "olive", up="olive", name="saddle_plate")
    for z in (6.6, 13.6):
        K.box(out, 4.4, 0.6, z, 11.6, 1.6, z + 0.8, "rubber", name="strap")
    return out


# ======================================================================================================= displays


def gun_displays(elements, fp_scale, gui_scale):
    fp_rot = [0, -6, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, GRIP, FP_GRIP_TARGET), fp_scale)
    tp_rot = [TP_DROP, 0, 0]
    tp = display(tp_rot, placed(tp_rot, 0.6, GRIP, TP_GRIP_TARGET), 0.6)
    c = bounds_centre(elements)
    gui_rot = [20, -125, 0]
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], 0.32, c, 2.0), 0.32),
        "fixed": display([0, -90, 0], centred([0, -90, 0], gui_scale * 1.25, c), gui_scale * 1.25),
    }


def part_displays(elements, scale, rot=DIAGONAL):
    c = bounds_centre(elements)
    rot = list(rot)
    return {
        "gui": display(rot, centred(rot, scale, c), scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], scale * 0.7, c, 2.0), scale * 0.7),
        "fixed": display(rot, centred(rot, scale * 1.2, c), scale * 1.2),
        "thirdperson_righthand": display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8),
        "thirdperson_lefthand": display([0, 0, 0], centred([0, 0, 0], scale * 0.8, c, 2.0), scale * 0.8),
        "firstperson_righthand": display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8),
        "firstperson_lefthand": display([0, -8, 0], centred([0, -8, 0], scale * 0.8, c, 3.0), scale * 0.8),
    }


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


CREDIT = "mymod minigun - generated by tools/gen_minigun.py"
HIDDEN = display([0, 0, 0], [0, 0, 0], 0)
everything = body() + barrels(0, True)
DISPLAYS = gun_displays(everything, 0.5, 0.45)


def out_model(name, elements, displays=None):
    check(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, displays or DISPLAYS, credit=CREDIT))
    return ref(name)


gun_body = out_model("minigun", body())
cool = [out_model(f"minigun_barrels_{k}", barrels(-FRAME_STEP * k, False)) for k in range(FRAMES)]
hot = [out_model(f"minigun_barrels_hot_{k}", barrels(-FRAME_STEP * k, True)) for k in range(FRAMES)]


def turning(frames):
    return range_dispatch("mymod:minigun_barrels", [(k - 0.5, frames[k]) for k in range(1, FRAMES)], frames[0])


HOT = 0.6
barrel_layer = range_dispatch("mymod:minigun_heat", [(HOT, turning(hot))], turning(cool))
flashes = flash_models(MODELS, "minigun", MUZZLE, BY, DISPLAYS)
flash_layer = range_dispatch("mymod:minigun_flash", [(k + 0.5, ref(f)) for k, f in enumerate(flashes)],
                             {"type": "minecraft:empty"})
write(os.path.join(ITEMS, "minigun.json"), {"model": composite(gun_body, barrel_layer, flash_layer)})

THREE_QUARTER = (20, -125, 0)

# The mount: drawn on the pig in no display context at all; in the inventory it shows one still model, centred.
mount = out_model("hogfire_gun_mount", mount_body())
icon = mount_body() + barrels(0, False)
out_model("hogfire_gun_mount_icon", icon, part_displays(icon, 0.55, THREE_QUARTER))
write(os.path.join(ITEMS, "hogfire_gun_mount.json"), {"model": {
    "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [{"when": ["none"], "model": composite(mount, barrel_layer, flash_layer)}],
    "fallback": ref("hogfire_gun_mount_icon")}})

# Weapon Station parts.
for name, elements, scale, rot in (("minigun_barrel_cluster", barrels(0, False), 0.62, DIAGONAL),
                                   ("minigun_feed_motor", drum() + move(receiver(z1=RECEIVER[0] + 4.6)[1:], dx=4.0, dy=-1.45), 0.95,
                                    (25, -150, 0)),
                                   ("minigun_housing", rotor() + receiver() + handle() + grip(), 0.75, THREE_QUARTER)):
    out_model(name, elements, part_displays(elements, scale, rot))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})

t = DISPLAYS["firstperson_righthand"]
print(f"minigun: {len(body()) + len(barrels(0, False))} elements; firstperson {t['translation']} rot {t['rotation']} scale {t['scale'][0]}")
print(f"   grip {fp_point(DISPLAYS, GRIP)}  support {fp_point(DISPLAYS, SUPPORT)}  muzzle {fp_point(DISPLAYS, (BX, BY, MUZZLE))}")

# =========================================================================================================== sounds
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
    """Mono written as stereo (ffmpeg's built-in Vorbis encoder only writes stereo), so MinigunSound fades these with
    distance itself, as the flamethrower's roar does."""
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


def motor(freqs, seed, whirr=0.5):
    """The motor and the barrels going round: a whine (with a fifth and an octave over it) following freqs, one value a
    sample, and a rush of air chopped at the rate the barrels pass (a sixth of the whine's)."""
    sr = random.Random(seed)
    air = lowpass(highpass([sr.uniform(-1, 1) for _ in freqs], 600), 3200)
    out, phase = [], 0.0
    for i, f in enumerate(freqs):
        phase += math.tau * f / RATE
        whine = math.sin(phase) + 0.35 * math.sin(phase * 1.5) + 0.25 * math.sin(phase * 2.0)
        chop = 0.5 + 0.5 * math.sin(phase / 6.0)
        out.append(whine * 0.5 + air[i] * whirr * (0.4 + 0.6 * chop) * min(1.0, f / 600.0))
    return out


def clunk(n, seed, at=0.0, gain=1.0):
    """The drive engaging or letting go: a dull knock."""
    sr = random.Random(seed)
    body = lowpass([sr.uniform(-1, 1) for _ in range(n)], 500)
    out = []
    for i in range(n):
        t = i / RATE - at
        out.append(0.0 if t < 0 else body[i] * math.exp(-t / 0.05) * 3.0 * gain + math.sin(math.tau * 95 * t) * math.exp(-t / 0.08) * gain)
    return out


TOP = 960.0                       # the whine at full speed, Hz


def spin_up(seconds=0.8):
    n = int(RATE * seconds)
    rise = [180.0 + (TOP - 180.0) * (1 - (1 - i / n) ** 2.2) for i in range(n)]
    out = [a + b for a, b in zip(motor(rise, 21), clunk(n, 22))]
    fade = int(RATE * 0.08)
    for i in range(fade):
        out[n - fade + i] *= 1.0 - 0.3 * i / fade
    return out


def spin(seconds=1.0):
    """Seamless when looped: the whine and the chop both go round a whole number of times a second."""
    return motor([TOP] * int(RATE * seconds), 23)


def spin_down(seconds=1.6):
    n = int(RATE * seconds)
    fall = [TOP * math.exp(-2.1 * i / n) for i in range(n)]
    out = motor(fall, 24)
    for i in range(n):
        out[i] *= (1.0 - i / n) ** 1.2
    return [a + b for a, b in zip(out, clunk(n, 25, at=seconds - 0.25, gain=0.35))]


def fire(seconds=1.0, rate=20):
    """Seamless when looped: `rate` shots a second, each a short crack and a thump, over the motor, so held fire is one
    tearing roar."""
    sr = random.Random(26)
    n = int(RATE * seconds)
    noise = [sr.uniform(-1, 1) for _ in range(n)]
    crack = highpass(noise, 2200)
    thump = lowpass(noise, 420)
    out = [v * 0.25 for v in spin(seconds)]
    gap = n // rate
    for k in range(rate):
        start = k * gap + sr.randint(0, gap // 8)
        g = 0.85 + sr.random() * 0.3
        # The last shots' tails wrap round to the start.
        for i in range(start, start + gap * 2):
            t, j = (i - start) / RATE, i % n
            out[j] += (crack[j] * math.exp(-t / 0.005) * 1.3 + thump[j] * math.exp(-t / 0.03) * 4.0
                       + math.sin(math.tau * 70 * t) * math.exp(-t / 0.025) * 0.8) * g
    return out


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping sounds")
else:
    write_ogg("minigun_spin_up", spin_up(), 0.75)
    write_ogg("minigun_spin", spin(), 0.6)
    write_ogg("minigun_fire", fire(), 0.95)
    write_ogg("minigun_spin_down", spin_down(), 0.7)
print("minigun assets written")
