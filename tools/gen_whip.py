"""Makes the Nose Hair Whip: its inventory icon (a coiled hairy whip) and in-hand sprite (held by the handle), the
strip of braided hair the lash is drawn with (client/schnozzle/WhipLashes), the crack particle's frames and the
Slimed effect's icon, with the item models and definition and the particle definition. On the data side it writes the
recipe (with its recipe-book advancement) and adds the whip to the melee weapon and durability enchanting tags.
With ffmpeg on the path it also synthesises the crack and snag sounds (stereo, like gen_industry.py's: ffmpeg's own
Vorbis encoder only takes two channels).

Usage: python3 tools/gen_whip.py src/main/resources/assets/tomyen"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
rnd = random.Random(61)


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


CLEAR = (0, 0, 0, 0)


def clamp(v): return max(0, min(255, int(round(v))))


def col(c, shade=1.0, jitter=0, a=255):
    d = rnd.uniform(-jitter, jitter) if jitter else 0
    return (clamp(c[0] * shade + d), clamp(c[1] * shade + d), clamp(c[2] * shade + d), a)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def outline(self, c):
        """A one-pixel outline round everything drawn so far, so the sprite reads against any background."""
        filled = [p[3] > 0 for p in self.px]
        for y in range(self.h):
            for x in range(self.w):
                if not filled[y * self.w + x] and any(0 <= x + dx < self.w and 0 <= y + dy < self.h and filled[(y + dy) * self.w + x + dx]
                                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    self.set(x, y, c)

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


# ---------------------------------------------------------------------------------------------- palette
HAIR = (38, 28, 22)
HAIR_HI = (84, 66, 50)
HAIR_LINE = (18, 12, 9)
WOOD = (122, 90, 52)
WOOD_HI = (158, 122, 72)
WOOD_DARK = (78, 56, 30)
BOOGER = (128, 168, 62)
BOOGER_HI = (186, 214, 104)
BOOGER_DARK = (80, 112, 36)


def braid(t):
    """Braided hair along a strand: every third twist catches the light."""
    return col(HAIR_HI if int(t * 2.2) % 3 == 0 else HAIR, 1.0, 5)


def stroke(canvas, points, width, colour):
    """A line through the points, `width` pixels thick (1 or 2), coloured by colour(t) with t the distance along it."""
    t = 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        steps = max(1, int(math.hypot(x1 - x0, y1 - y0) * 3))
        for k in range(steps):
            f = k / steps
            x, y = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f
            canvas.set(int(round(x)), int(round(y)), colour(t))
            if width > 1:
                canvas.set(int(round(x)) + 1, int(round(y)), colour(t + 0.5))
            t += math.hypot(x1 - x0, y1 - y0) / steps
    return t


def handle(canvas, start, end):
    """The stick handle with a knob at the end, and a booger wrapped round where the hair is bound on."""
    stroke(canvas, [start, end], 2, lambda t: col(WOOD_HI if int(t) % 3 == 0 else WOOD, 1.0, 4))
    canvas.set(start[0], start[1], col(WOOD_DARK))
    canvas.set(start[0] + 1, start[1], col(WOOD_DARK))
    ex, ey = end
    for dx, dy, c in ((0, 0, BOOGER_HI), (1, 0, BOOGER), (0, 1, BOOGER), (1, -1, BOOGER_DARK), (-1, 1, BOOGER_DARK), (2, -1, BOOGER)):
        canvas.set(ex + dx, ey + dy, col(c, 1.0, 4))


def stray_hairs(canvas, count, region):
    """Single wiry hairs poking out of the hair already drawn in the region (x0, y0, x1, y1)."""
    x0, y0, x1, y1 = region
    hair = [(x, y) for y in range(y0, y1) for x in range(x0, x1) if canvas.get(x, y)[3] > 0 and canvas.get(x, y)[1] < 90]
    for _ in range(count):
        if not hair:
            return
        x, y = rnd.choice(hair)
        dx, dy = rnd.choice(((1, -1), (-1, -1), (1, 1), (-1, 1), (0, -1), (1, 0)))
        if canvas.get(x + dx, y + dy)[3] == 0:
            canvas.set(x + dx, y + dy, col(HAIR_HI, 0.8))


# Inventory icon: the lash coiled in two loose loops, seen from above at an angle, with the handle out to the lower left.
def ellipse(cx, cy, rx, ry, a0, a1, steps=90):
    return [(cx + rx * math.cos(a0 + (a1 - a0) * i / steps), cy + ry * math.sin(a0 + (a1 - a0) * i / steps)) for i in range(steps + 1)]


icon = Canvas(16, 16)
handle(icon, (1, 14), (4, 11))
icon.outline(col(HAIR_LINE))
outer = ellipse(9.6, 7.0, 5.6, 4.3, math.pi * 0.8, math.pi * 2.75)
inner = ellipse(9.8, 7.0, 2.9, 1.9, math.pi * 0.75, math.pi * 2.6)
stroke(icon, [(5, 10)] + outer, 1, braid)
# The near (lower) side of the outer loop is thicker, for a bit of depth.
stroke(icon, [p for p in outer if p[1] > 7.5], 2, lambda t: col(HAIR, 0.7, 4))
stroke(icon, outer[-1:] + inner, 1, braid)
stroke(icon, inner[-1:] + [(9.5, 6.5), (8.5, 7.0)], 1, braid)
stray_hairs(icon, 6, (3, 0, 16, 13))
icon.save("item/nose_hair_whip.png")

# In hand: just the handle, held at the lower left as a sword is, and its booger binding. The lash itself is drawn in the
# world from the top of the binding (client/schnozzle/WhipHandles.TIP), hanging at rest and following every crack.
held = Canvas(16, 16)
handle(held, (1, 14), (5, 10))
held.outline(col(HAIR_LINE))
held.save("item/nose_hair_whip_in_hand.png")

# The lash as drawn in the world: 8 wide, the handle end at the top (the binding is on the handle sprite). A braided core
# down the middle half, wiry stray hairs either side (cut out) and a frayed tuft at the tip.
strip = Canvas(8, 64)
for y in range(64):
    for x in range(2, 6):
        strand = (x - 2 + y // 2) % 3
        strip.set(x, y, col(HAIR_HI if strand == 0 else HAIR, 0.85 if x in (2, 5) else 1.0, 5))
    for x, chance in ((1, 0.3), (6, 0.3), (0, 0.08), (7, 0.08)):
        if rnd.random() < chance:
            strip.set(x, y, col(HAIR_HI if rnd.random() < 0.4 else HAIR, 1.0, 6))
for y in range(56, 64):
    for x in range(8):
        if rnd.random() < 0.35 + (y - 56) * 0.04:
            strip.set(x, y, col(HAIR_HI if rnd.random() < 0.5 else HAIR, 1.0, 6))
strip.save("entity/nose_hair_whip_lash.png")

# The crack: a white shock ring bursting outward with spikes, over four frames.
for frame in range(4):
    crack = Canvas(16, 16)
    radius = 2.4 + frame * 1.7
    alpha = 255 - frame * 45
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if abs(d - radius) < 0.75:
                crack.set(x, y, (255, 255, 255, alpha))
            elif abs(d - radius) < 1.4:
                crack.set(x, y, (232, 236, 255, alpha // 2))
            elif frame == 0 and d < 1.6:
                crack.set(x, y, (255, 255, 240, 255))
    if frame < 2:
        for k in range(8):
            a = k * math.pi / 4 + 0.3
            for step in range(3):
                r = radius + 1.5 + step
                crack.set(int(8 + r * math.cos(a)), int(8 + r * math.sin(a)), (255, 255, 255, alpha - step * 50))
    crack.save("particle/whip_crack_%d.png" % frame)

# Slimed: a dripping glob of snot.
slimed = Canvas(18, 18)
for y in range(18):
    for x in range(18):
        d = math.hypot((x + 0.5 - 9) / 6.5, (y + 0.5 - 7.5) / 5.0)
        drip = (x in (5, 6) and 9 <= y <= 15) or (x in (12, 13) and 9 <= y <= 13) or (x == 9 and 10 <= y <= 12)
        if d < 1.0 or drip:
            light = (9 - x) + (7 - y)
            slimed.set(x, y, col(BOOGER_HI if light > 5 else BOOGER if light > -5 else BOOGER_DARK, 1.0, 5))
for x, y in ((5, 4), (6, 4), (5, 5)):
    slimed.set(x, y, col((236, 246, 196)))
slimed.outline(col((52, 76, 22)))
slimed.save("mob_effect/slimed.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


write(os.path.join(MODELS, "nose_hair_whip.json"),
      OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/nose_hair_whip"})]))
write(os.path.join(MODELS, "nose_hair_whip_in_hand.json"),
      OrderedDict([("parent", "minecraft:item/handheld"), ("textures", {"layer0": "tomyen:item/nose_hair_whip_in_hand"})]))
write(os.path.join(ITEMS, "nose_hair_whip.json"), {"model": OrderedDict([
    ("type", "minecraft:select"), ("property", "minecraft:display_context"),
    ("cases", [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": "tomyen:item/nose_hair_whip"}}]),
    ("fallback", {"type": "minecraft:model", "model": "tomyen:item/nose_hair_whip_in_hand"})])})
write(os.path.join(ASSETS, "particles/whip_crack.json"), {"textures": ["tomyen:whip_crack_%d" % i for i in range(4)]})

# Six nose hairs braided onto a stick, bound on with a Giant Booger.
write(os.path.join(DATA, "tomyen/recipe/nose_hair_whip.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "equipment"),
    ("key", OrderedDict([("B", "tomyen:giant_booger"), ("H", "tomyen:nose_hair"), ("S", "minecraft:stick")])),
    ("pattern", ["HHH", "HBH", "SH "]),
    ("result", OrderedDict([("count", 1), ("id", "tomyen:nose_hair_whip")]))]))
write(os.path.join(DATA, "tomyen/advancement/recipes/combat/nose_hair_whip.json"), OrderedDict([
    ("parent", "minecraft:recipes/root"),
    ("criteria", OrderedDict([
        ("has_nose_hair", {"conditions": {"items": [{"items": "tomyen:nose_hair"}]}, "trigger": "minecraft:inventory_changed"}),
        ("has_the_recipe", {"conditions": {"recipes": "tomyen:nose_hair_whip"}, "trigger": "minecraft:recipe_unlocked"})])),
    ("requirements", [["has_nose_hair", "has_the_recipe"]]),
    ("rewards", {"recipes": ["tomyen:nose_hair_whip"]})]))


def add_to_tag(rel, entry):
    path = os.path.join(DATA, "minecraft/tags/item", rel)
    tag = json.load(open(path)) if os.path.exists(path) else {"values": []}
    if entry not in tag["values"]:
        tag["values"].append(entry)
    write(path, tag)


# Melee weapon: Sharpness, Smite, Bane of Arthropods, Knockback, Fire Aspect and Looting. Durability: Unbreaking and Mending.
add_to_tag("enchantable/melee_weapon.json", "tomyen:nose_hair_whip")
add_to_tag("enchantable/durability.json", "tomyen:nose_hair_whip")

# ---------------------------------------------------------------------------------------------- sounds
RATE = 44100


def noise(n, r): return [r.uniform(-1.0, 1.0) for _ in range(n)]


def lowpass(xs, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    y, out = 0.0, []
    for x in xs:
        y += a * (x - y)
        out.append(y)
    return out


def bandpass(xs, low, high):
    return lowpass([x - l for x, l in zip(xs, lowpass(xs, low))], high)


def burst(n, at, decay, attack=0.0002):
    """Exponential decay (seconds to fall to a third) after a short linear attack, starting `at` seconds in."""
    out = []
    for i in range(n):
        t = i / RATE - at
        out.append(0.0 if t < 0 else min(1.0, t / attack) * math.exp(-t / decay))
    return out


def swell(n, until, rise):
    """The swish of the lash: builds up over `rise` seconds to `until`, then cuts off almost at once."""
    out = []
    for i in range(n):
        t = i / RATE
        out.append(((t - (until - rise)) / rise) ** 2 if until - rise <= t < until else
                   math.exp(-(t - until) / 0.006) if t >= until else 0.0)
    return out


def layer(xs, envelope, gain): return [x * e * gain for x, e in zip(xs, envelope)]


def mix(*layers): return [sum(v) for v in zip(*layers)]


def write_ogg(name, samples, gain=1.0):
    peak = max(abs(v) for v in samples) or 1.0
    shape = lambda v: math.tanh(1.8 * v / peak) / math.tanh(1.8)
    frames = b"".join(struct.pack("<h", int(shape(v) * 31500 * gain)) for v in samples)
    path = os.path.join(ASSETS, "sounds", name + ".ogg")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(frames)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "vorbis", "-strict", "-2", "-ac", "2", "-q:a", "5",
                        path], check=True)


def crack(seed, swish_until, brightness):
    """A swish rising to the crack: a sharp broadband snap, a bright ring of a slap, and a short room tail."""
    r = random.Random(seed)
    n = int(RATE * 0.6)
    swish = layer(bandpass(noise(n, r), 400, 2600), swell(n, swish_until, swish_until - 0.01), 0.35)
    snap = layer(bandpass(noise(n, r), 2200 * brightness, 14000), burst(n, swish_until, 0.0016), 1.6)
    slap = layer(bandpass(noise(n, r), 700, 5000 * brightness), burst(n, swish_until, 0.007, 0.0005), 0.9)
    tail = layer(lowpass(noise(n, r), 1800), burst(n, swish_until + 0.004, 0.07, 0.004), 0.25)
    return mix(swish, snap, slap, tail)


def snag(seed, at):
    """A quick swish, the thwip of the lash wrapping round something and a soft slap."""
    r = random.Random(seed)
    n = int(RATE * 0.45)
    swish = layer(bandpass(noise(n, r), 300, 2000), swell(n, at, at - 0.01), 0.4)
    thwip = layer(bandpass(noise(n, r), 1500, 7000), burst(n, at, 0.012, 0.001), 0.8)
    slap = layer(bandpass(noise(n, r), 120, 900), burst(n, at + 0.01, 0.03, 0.002), 1.0)
    return mix(swish, thwip, slap)


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping the sounds")
else:
    for i, (until, bright) in enumerate(((0.14, 1.0), (0.12, 1.15), (0.16, 0.9))):
        write_ogg("nose_hair_whip_crack%d" % (i + 1), crack(70 + i, until, bright))
    for i, at in enumerate((0.13, 0.15)):
        write_ogg("nose_hair_whip_snag%d" % (i + 1), snag(80 + i, at), gain=0.8)
print("done")
