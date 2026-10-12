"""Synthesises the M249's and the shotguns' sounds into assets/mymod/sounds/*.ogg (needs ffmpeg on the path, as
gen_industry.py's engine loop does).

A shot is layered: a sharp noise crack, a low boom sweeping down in pitch, a short burst of body noise and a long
filtered tail standing in for the echo, each channel with its own noise so it sounds wide. The shotguns boom lower
and ring longer than the M249, whose short report overlaps cleanly at 800 rpm. The handling sounds (the pump
stroke, a shell going in, the feed cover and the belt) are clicks and damped metal rings with a little friction
noise."""
import math, os, random, shutil, struct, subprocess, sys, tempfile, wave

ASSETS = sys.argv[1]
OUT = os.path.join(ASSETS, "sounds")
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


def bandpass(xs, low, high):
    return lowpass(highpass(xs, low), high)


def noise(n, rnd):
    return [rnd.uniform(-1.0, 1.0) for _ in range(n)]


def env(n, decay, attack=0.0005, delay=0.0):
    """Exponential decay (seconds to fall to a third) after a short linear attack, starting `delay` seconds in."""
    out = []
    for i in range(n):
        t = i / RATE - delay
        out.append(0.0 if t < 0 else min(1.0, t / attack) * math.exp(-t / decay))
    return out


def ring(n, freq, decay, delay=0.0, sweep_to=None):
    """A damped sine (optionally sweeping down in pitch over its decay)."""
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE - delay
        if t < 0:
            out.append(0.0)
            continue
        f = freq if sweep_to is None else sweep_to + (freq - sweep_to) * math.exp(-t / (decay * 0.7))
        phase += 2.0 * math.pi * f / RATE
        out.append(math.sin(phase) * math.exp(-t / decay))
    return out


def mixdown(n, *layers):
    return [sum(layer[i] * gain for layer, gain in layers) for i in range(n)]


def shot(seconds, seed, boom_from, boom_to, boom_decay, crack, body_decay, tail_decay, tail_cutoff, brightness):
    """One channel of a gunshot."""
    rnd = random.Random(seed)
    n = int(RATE * seconds)
    crack_layer = [x * e for x, e in zip(highpass(noise(n, rnd), 1800 * brightness), env(n, 0.006))]
    body = [x * e for x, e in zip(bandpass(noise(n, rnd), 200, 2400 * brightness), env(n, body_decay, 0.001))]
    tail = [x * e for x, e in zip(lowpass(noise(n, rnd), tail_cutoff), env(n, tail_decay, 0.01, 0.01))]
    boom = ring(n, boom_from, boom_decay, sweep_to=boom_to)
    return mixdown(n, (crack_layer, crack), (body, 0.9), (tail, 1.6), (boom, 0.9))


def clicks(seconds, seed, parts):
    """Handling sound: parts are ("click", at, gain), ("ring", at, freq, decay, gain), ("rub", at, decay, low, high, gain)."""
    rnd = random.Random(seed)
    n = int(RATE * seconds)
    layers = []
    for part in parts:
        kind, at = part[0], part[1]
        if kind == "click":
            layers.append(([x * e for x, e in zip(highpass(noise(n, rnd), 2500), env(n, 0.0025, 0.0002, at))], part[2]))
        elif kind == "ring":
            layers.append((ring(n, part[2], part[3], at), part[4]))
        else:
            layers.append(([x * e for x, e in zip(bandpass(noise(n, rnd), part[3], part[4]), env(n, part[2], 0.004, at))], part[5]))
    return mixdown(n, *layers)


def write_ogg(name, left, right=None, gain=1.0):
    """gain: the handling sounds sit about 6 dB under full scale, like the existing rack and bolt sounds."""
    right = right or left
    peak = max(max(abs(v) for v in left), max(abs(v) for v in right)) or 1.0
    # Soft-clip a little for punch, then normalise to just under full scale like the other gun sounds.
    shape = lambda v: math.tanh(1.6 * v / peak) / math.tanh(1.6)
    frames = b"".join(struct.pack("<hh", int(shape(l) * 31500 * gain), int(shape(r) * 31500 * gain)) for l, r in zip(left, right))
    path = os.path.join(OUT, name + ".ogg")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(2)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(frames)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-q:a", "5", path], check=True)
    print("wrote", path)


if shutil.which("ffmpeg") is None:
    raise SystemExit("ffmpeg not found")

# Shots: (seconds, boom from/to Hz, boom decay, crack gain, body decay, tail decay, tail cutoff, brightness).
SHOTS = {
    "remington_870_shot": (1.3, 95, 38, 0.2, 1.0, 0.09, 0.5, 900, 0.9),
    "spas12_shot": (1.2, 110, 42, 0.17, 1.3, 0.075, 0.42, 1100, 1.1),
    "m249_shot": (0.65, 150, 62, 0.07, 1.4, 0.04, 0.22, 1500, 1.2),
}
for name, (secs, bf, bt, bd, crack, body, tail, cut, bright) in SHOTS.items():
    seed = sum(map(ord, name))
    write_ogg(name, shot(secs, seed, bf, bt, bd, crack, body, tail, cut, bright),
              shot(secs, seed + 1, bf, bt, bd, crack, body, tail, cut, bright))

# Handling.
write_ogg("pump_back", clicks(0.22, 11, [("click", 0.0, 0.7), ("rub", 0.0, 0.05, 900, 3800, 0.7),
                                         ("ring", 0.075, 2300, 0.025, 0.5), ("click", 0.075, 0.9)]), gain=0.5)
write_ogg("pump_forward", clicks(0.24, 12, [("rub", 0.0, 0.04, 800, 3200, 0.6), ("click", 0.06, 1.0),
                                            ("ring", 0.06, 1700, 0.035, 0.6), ("ring", 0.06, 420, 0.04, 0.5),
                                            ("click", 0.075, 0.6)]), gain=0.5)
write_ogg("shell_insert", clicks(0.2, 13, [("rub", 0.0, 0.03, 500, 2000, 0.5), ("ring", 0.05, 820, 0.025, 0.5),
                                           ("click", 0.06, 0.8), ("ring", 0.06, 2600, 0.012, 0.3)]), gain=0.5)
write_ogg("feed_cover_open", clicks(0.32, 14, [("click", 0.0, 0.8), ("ring", 0.0, 3100, 0.015, 0.4),
                                               ("rub", 0.03, 0.08, 1200, 5000, 0.35), ("ring", 0.09, 1250, 0.05, 0.45),
                                               ("click", 0.09, 0.5)]), gain=0.5)
write_ogg("feed_cover_close", clicks(0.4, 15, [("rub", 0.0, 0.03, 900, 3000, 0.4), ("ring", 0.05, 170, 0.06, 0.9),
                                               ("click", 0.05, 1.0), ("ring", 0.05, 1480, 0.07, 0.55),
                                               ("ring", 0.05, 2650, 0.05, 0.35), ("click", 0.075, 0.5)]), gain=0.5)
rr = random.Random(16)
rattle = []
for k in range(9):
    at = 0.02 + k * 0.026 + rr.uniform(-0.008, 0.008)
    rattle += [("click", at, rr.uniform(0.3, 0.7)), ("ring", at, rr.uniform(3000, 5200), 0.008, rr.uniform(0.2, 0.4))]
write_ogg("belt_rattle", clicks(0.32, 17, rattle + [("rub", 0.0, 0.12, 2000, 7000, 0.25)]), gain=0.5)
