"""Paints the UFO invasion's assets and writes its data: the alien's entity texture (UVs matching
client/alien/AlienModel), the saucer's plain white texture (UfoRenderer colours it vertex by vertex), the UFO and alien
spawn eggs, the Alien Alloy and Alien Reactor items, the Probed effect icon; the UFO's and the alien's loot tables, the
Alien Alloy Mk III plating kit recipe (with its recipe-book advancement), the mymod:abductable entity tag, the alien ray
damage type; and the sounds: the saucer's hum (a wobbling theremin over a drone), the abduction sting and the aliens'
chirps are synthesised (with ffmpeg on the path), the rest are vanilla sounds re-pitched in sounds.json. Language
entries are merged into en_us.json. Everything is flat-shaded from fixed palettes, so a rerun gives the same files.

Usage: python3 tools/gen_ufo.py src/main/resources/assets/mymod"""
import json, math, os, random, shutil, struct, subprocess, sys, tempfile, wave, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(path):
    """8-bit RGBA, non-interlaced only."""
    data = open(path, "rb").read()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    raw = zlib.decompress(idat)
    stride, out, prev = w * 4, [], bytearray(w * 4)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out += [tuple(line[x * 4:x * 4 + 4]) for x in range(w)]
        prev = line
    return w, h, out


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge_json(path, entries):
    """Adds (or updates) keys in a JSON object file, keeping its order and everything else in it."""
    with open(path) as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data.update(entries)
    write(path, data)


def tag_add(path, entry):
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


def rgba(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    return sum(int(round(ca[i] + (cb[i] - ca[i]) * t)) << s for i, s in enumerate((16, 8, 0)))


CLEAR = (0, 0, 0, 0)

# ================================================================================================== the saucer
# UfoRenderer colours every vertex itself; its texture is just white.
png(os.path.join(TEX, "entity/ufo/ufo.png"), 16, 16, [rgba(0xFFFFFF)] * 256)

# =================================================================================================== the alien
SKIN = [0xB6E67C, 0x8ECD56, 0x6DB040, 0x4F8C2E]     # light to dark
SUIT = [0xDDE2E8, 0xB8C0CA, 0x919AA6, 0x6E7682]
EYE, EYE_SHEEN, GLINT = 0x0E1216, 0x1F3A42, 0xA6DCE4
MOUTH = 0x3C6E26
BOOT = 0x3A4048


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = rgba(c) if isinstance(c, int) else c

    def rect(self, u, v, w, h, f):
        for y in range(h):
            for x in range(w):
                self.set(u + x, v + y, f(x, y, w, h))


def ramp(colours, top, bottom):
    """A vertical ramp between two points of a palette (fractional indices), lighter at the top."""
    def f(x, y, w, h):
        t = top + (bottom - top) * (y / max(1, h - 1))
        i = min(len(colours) - 2, int(t))
        return mix(colours[i], colours[i + 1], t - i)
    return f


def flat(c):
    return lambda x, y, w, h: c


def box_uv(canvas, u, v, w, h, d, top, bottom, front, side, back):
    """Paints a ModelPart box's six faces at texture offset (u, v), vanilla's layout."""
    canvas.rect(u + d, v, w, d, top)
    canvas.rect(u + d + w, v, w, d, bottom)
    canvas.rect(u, v + d, d, h, side)
    canvas.rect(u + d, v + d, w, h, front)
    canvas.rect(u + d + w, v + d, d, h, side)
    canvas.rect(u + d + w + d, v + d, w, h, back)


A = Canvas(64, 32)
skin_side = ramp(SKIN, 1.0, 2.2)
box_uv(A, 0, 0, 9, 8, 8, flat(SKIN[0]), flat(SKIN[3]), ramp(SKIN, 0.6, 1.8), skin_side, ramp(SKIN, 1.2, 2.4))
# The chin.
box_uv(A, 34, 0, 5, 1, 5, flat(SKIN[2]), flat(SKIN[3]), flat(SKIN[2]), flat(SKIN[2]), flat(SKIN[3]))
# The face (the head's front, 9 x 8): big almond eyes slanting up to their outer corners, a glint in each, a small mouth.
FACE = (8, 8)
for x, y in [(1, 2), (2, 2), (1, 3), (2, 3), (3, 3), (1, 4), (2, 4), (3, 4), (2, 5), (3, 5)]:
    A.set(FACE[0] + x, FACE[1] + y, EYE)
    A.set(FACE[0] + 8 - x, FACE[1] + y, EYE)
for x, y in [(3, 5)]:
    A.set(FACE[0] + x, FACE[1] + y, EYE_SHEEN)
    A.set(FACE[0] + 8 - x, FACE[1] + y, EYE_SHEEN)
A.set(FACE[0] + 2, FACE[1] + 3, GLINT)
A.set(FACE[0] + 6, FACE[1] + 3, GLINT)
A.set(FACE[0] + 4, FACE[1] + 6, MOUTH)
# The body: a silver suit, a darker belt with a green buckle.
suit_front = ramp(SUIT, 0.4, 1.6)
box_uv(A, 0, 16, 4, 5, 3, flat(SUIT[0]), flat(SUIT[3]), suit_front, ramp(SUIT, 1.0, 2.2), ramp(SUIT, 1.2, 2.4))
for x in range(14):
    A.set(x, 23, SUIT[3])
A.set(4, 23, SKIN[1])
# The arms: silver sleeves, green hands; the legs: silver, dark boots. Each a 1 x 7 x 1 box: its top and bottom
# texels, then its four sides in a row (side, front, side, back).
for u, cuff, end_colours in ((14, 5, (SKIN[0], SKIN[1])), (18, 6, (BOOT, BOOT))):
    A.set(u + 1, 16, SUIT[0])
    A.set(u + 2, 16, end_colours[1])
    for face, shade in enumerate((0.5, 0.0, 0.5, 1.0)):
        for y in range(7):
            if y >= cuff:
                c = end_colours[0] if face == 1 else end_colours[1]
            else:
                c = mix(mix(SUIT[1], SUIT[2], shade), mix(SUIT[2], SUIT[3], shade), y / 6.0)
            A.set(u + face, 17 + y, c)
png(os.path.join(TEX, "entity/alien/alien.png"), A.w, A.h, A.px)

# ================================================================================================= item sprites


def sprite(name, rows, palette):
    px = []
    for row in rows:
        assert len(row) == 16, (name, row)
        px += [CLEAR if ch == "." else rgba(palette[ch]) for ch in row]
    assert len(px) == 256, name
    png(os.path.join(TEX, "item", name + ".png"), 16, 16, px)
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


# A torn-off scrap of hull plate: dark violet-grey metal with a pearly sheen across it.
sprite("alien_alloy", [
    "................",
    "................",
    "......oo........",
    ".....ollo.......",
    "....olhllooo....",
    "...olhhlllllo...",
    "..olhhllmmmllo..",
    "..ollllmmmmmlo..",
    "...olllmmmmmdo..",
    "...omlllmmmddo..",
    "..ommmllllmddo..",
    "..odmmmlllddo...",
    "...oddmmmddo....",
    "....ooddddo.....",
    "......oooo......",
    "................",
], {"o": 0x1C1826, "h": 0xE6DCFF, "l": 0xA79CC8, "m": 0x7A6E9C, "d": 0x544A70})

# The reactor: a glowing green core in a dark caged housing.
sprite("alien_reactor", [
    "................",
    "................",
    ".....oooooo.....",
    "...oohhggggoo...",
    "..ohwwggggggco..",
    "..ohwggggggcco..",
    ".ommoooooooommo.",
    ".ohggggggggggco.",
    ".ogggggggggccco.",
    ".ommoooooooommo.",
    "..oggggggggcco..",
    "..ogggggggccco..",
    "...oocccccccoo..",
    ".....oooooo.....",
    "................",
    "................",
], {"o": 0x14181E, "h": 0xC8FFB0, "w": 0xF4FFEC, "g": 0x7CFF4A, "c": 0x3EAA2A, "m": 0x5A6270})

# ================================================================================================== spawn eggs
# The same egg outline as the other mod spawn eggs, filled in each mob's colours.
_, _, EGG = read_png(os.path.join(TEX, "item", "skullcrane_spawn_egg.png"))
INSIDE = [[EGG[y * 16 + x][3] > 0 for x in range(16)] for y in range(16)]


def egg(name, base, spots):
    """base: three tones light to dark, shaded from the upper left; spots: {(x, y): colour}."""
    px = []
    for y in range(16):
        for x in range(16):
            if not INSIDE[y][x]:
                px.append(CLEAR)
                continue
            edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16 and INSIDE[y + dy][x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if (x, y) in spots:
                c = spots[(x, y)]
            elif edge:
                c = base[3]
            else:
                t = (x + y - 8) / 16.0
                c = base[0] if t < 0.25 else base[1] if t < 0.7 else base[2]
            px.append(rgba(c))
    png(os.path.join(TEX, "item", name + ".png"), 16, 16, px)
    write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


# A silver egg with a dark saucer across its middle and three lights along its rim.
saucer = {}
for x in range(3, 13):
    saucer[(x, 9)] = 0x3A404A
for x in range(5, 11):
    saucer[(x, 8)] = 0x5C6573
for x in range(6, 10):
    saucer[(x, 7)] = 0x9CE6D8
saucer[(4, 9)], saucer[(8, 9)], saucer[(11, 9)] = 0xFF4A4A, 0x48FF7A, 0x46C8FF
egg("ufo_spawn_egg", [0xE4E8EE, 0xC4CAD3, 0x9AA2AE, 0x4A505A], saucer)
# A green egg with two big dark almond eyes.
eyes = {}
for x, y in [(4, 6), (4, 7), (5, 7), (6, 7), (5, 8), (6, 8)]:
    eyes[(x, y)] = 0x0E1216
    eyes[(15 - x, y)] = 0x0E1216
eyes[(5, 7)] = 0xA6DCE4
eyes[(10, 7)] = 0xA6DCE4
egg("alien_spawn_egg", [0xB6E67C, 0x8ECD56, 0x6DB040, 0x2E5A1A], eyes)

# =================================================================================================== effect icon


def probed_icon():
    """An alien's head, 18 x 18 like vanilla's effect icons: a green dome with two big dark eyes."""
    rows = [
        "..................",
        "......oooooo......",
        "....oollllllo o...",
        "...ollllllllllo...",
        "..olllllllllllmo..",
        "..ollllllllllmmo..",
        ".olllllllllllmmmo.",
        ".oeellllllllleemo.",
        ".oeeeglllllgeeemo.",
        ".oleeeeolleeeemmo.",
        "..olleeellleemmo..",
        "..olllllllllmmmo..",
        "...ollllllmmmmo...",
        "....olllmmmmmo....",
        ".....ollmmmmo.....",
        "......ooooo.......",
        "..................",
        "..................",
    ]
    pal = {"o": 0x24461A, "l": SKIN[1], "m": SKIN[2], "e": 0x0E1216, "g": 0xA6DCE4}
    px = []
    for row in rows:
        row = row.replace(" ", "l")
        assert len(row) == 18, row
        px += [CLEAR if ch == "." else rgba(pal[ch]) for ch in row]
    png(os.path.join(TEX, "mob_effect", "probed.png"), 18, 18, px)


probed_icon()

# ========================================================================================================= data


def drop(item, low, high, looting=0.0, chance=None, by_player=False):
    """A pool of one item, low to high of it (plus up to {@code looting} more per Looting level), only {@code chance}
    (unenchanted chance, extra per Looting level) of the time, and only if a player killed it when {@code by_player}."""
    modifiers = [OrderedDict([("type", "minecraft:set_count"), ("count", OrderedDict([("type", "minecraft:uniform"), ("min", low), ("max", high)]))])]
    if looting:
        modifiers.append(OrderedDict([("type", "minecraft:enchanted_count_increase"),
                                      ("count", OrderedDict([("type", "minecraft:uniform"), ("min", 0.0), ("max", looting)])),
                                      ("enchantment", "minecraft:looting")]))
    terms = []
    if by_player:
        terms.append({"type": "minecraft:killed_by_player"})
    if chance is not None:
        terms.append(OrderedDict([("type", "minecraft:random_chance_with_enchanted_bonus"),
                                  ("enchanted_chance", {"type": "minecraft:linear", "base": round(chance[0] + chance[1], 3),
                                                        "per_level_above_first": chance[1]}),
                                  ("enchantment", "minecraft:looting"), ("unenchanted_chance", chance[0])]))
    pool = OrderedDict()
    if len(terms) == 1:
        pool["condition"] = terms[0]
    elif terms:
        pool["condition"] = {"type": "minecraft:all_of", "terms": terms}
    pool["entries"] = [OrderedDict([("type", "minecraft:item"), ("name", item), ("modifier", modifiers)])]
    pool["rolls"] = 1
    return pool


# A downed saucer: always its reactor, and a pile of hull scrap.
write(os.path.join(DATA, "loot_table/entities/ufo.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:alien_reactor", 1, 1), drop("mymod:alien_alloy", 3, 6, 2.0)]),
    ("random_sequence", "mymod:entities/ufo")]))
# An alien: now and then a scrap of alloy, and rarely its ray gun.
write(os.path.join(DATA, "loot_table/entities/alien.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:alien_alloy", 1, 1, chance=(0.35, 0.1)), drop("mymod:ray_gun", 1, 1, chance=(0.04, 0.02), by_player=True)]),
    ("random_sequence", "mymod:entities/alien")]))

# Alien Alloy stands in for the netherite in Mk III power armor plating.
write(os.path.join(DATA, "recipe", "mk3_plating_kit_from_alien_alloy.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "equipment"),
    ("key", OrderedDict([("C", "mymod:computer_chip"), ("A", "mymod:alien_alloy"), ("G", "minecraft:gold_ingot")])),
    ("pattern", ["CAC", "GAG", "CAC"]),
    ("result", OrderedDict([("count", 1), ("id", "mymod:mk3_plating_kit")]))]))
write(os.path.join(DATA, "advancement/recipes/equipment/mk3_plating_kit_from_alien_alloy.json"), OrderedDict([
    ("parent", "minecraft:recipes/root"),
    ("criteria", OrderedDict([
        ("has_alien_alloy", {"conditions": {"items": [{"items": "mymod:alien_alloy"}]}, "trigger": "minecraft:inventory_changed"}),
        ("has_the_recipe", {"conditions": {"recipes": "mymod:mk3_plating_kit_from_alien_alloy"}, "trigger": "minecraft:recipe_unlocked"})])),
    ("requirements", [["has_alien_alloy", "has_the_recipe"]]),
    ("rewards", {"recipes": ["mymod:mk3_plating_kit_from_alien_alloy"]})]))

for animal in ("minecraft:cow", "minecraft:mooshroom", "minecraft:sheep", "minecraft:pig"):
    tag_add(os.path.join(DATA, "tags/entity_type/abductable.json"), animal)
# Far too big for a Soul Vacuum.
tag_add(os.path.join(DATA, "tags/entity_type/soul_vacuum_immune.json"), "mymod:ufo")
write(os.path.join(DATA, "damage_type/alien_ray.json"), OrderedDict([
    ("exhaustion", 0.1), ("message_id", "mymod.alien_ray"), ("scaling", "when_caused_by_living_non_player")]))

# ======================================================================================================= sounds
RATE = 44100


def lowpass(xs, cutoff):
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    y, out = 0.0, []
    for x in xs:
        y += a * (x - y)
        out.append(y)
    return out


def write_ogg(name, samples, gain=1.0):
    """ffmpeg's Vorbis encoder only writes stereo, which Minecraft doesn't fade with distance: the hum fades itself
    (UfoHumSound); the sting is only for whoever's being carried off; the chirps are short and quiet."""
    peak = max(abs(v) for v in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(math.tanh(1.2 * v / peak) / math.tanh(1.2) * 30000 * gain)) for v in samples)
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


def theremin(freq_at, n, vibrato=6.0, depth=0.012):
    """A theremin-ish voice: a sine with a little second harmonic, wobbling by vibrato; freq_at(t) its pitch."""
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        f = freq_at(t) * (1.0 + depth * math.sin(math.tau * vibrato * t))
        phase += math.tau * f / RATE
        out.append(math.sin(phase) + 0.18 * math.sin(2 * phase) + 0.06 * math.sin(3 * phase))
    return out


def hum(seconds=3.0):
    """The saucer's loop: a low throbbing drone under a theremin note gliding up and down a little. Every modulation
    runs whole cycles in the loop, so it joins up seamlessly."""
    n = int(RATE * seconds)
    voice = theremin(lambda t: 520.0 + 30.0 * math.sin(math.tau * t / seconds), n, vibrato=6.0, depth=0.018)
    out = []
    for i in range(n):
        t = i / RATE
        drone = math.sin(math.tau * 55 * t) * 0.9 + math.sin(math.tau * 110 * t) * 0.35 + math.sin(math.tau * 165 * t) * 0.12
        throb = 0.75 + 0.25 * math.sin(math.tau * 2 * t / seconds * 3)
        out.append(drone * throb * 0.8 + voice[i] * 0.32)
    return out


def abduction(seconds=3.6):
    """The sting as someone's carried off: a theremin sliding up and up with a wide, slow wobble, an echo trailing it,
    and the drone swelling underneath; fading out at the top."""
    n = int(RATE * seconds)
    voice = theremin(lambda t: 260.0 * 2 ** (1.6 * t / seconds), n, vibrato=5.0, depth=0.03)
    out = []
    delay = int(RATE * 0.23)
    for i in range(n):
        t = i / RATE
        swell = min(1.0, t / 0.6) * (1.0 - max(0.0, (t - seconds + 0.8) / 0.8))
        drone = math.sin(math.tau * 55 * t) * 0.6 * min(1.0, t / 1.5)
        echo = voice[i - delay] * 0.45 if i >= delay else 0.0
        out.append((voice[i] * 0.6 + echo + drone) * swell)
    return out


def chirps(seed):
    """An alien chattering to itself: three to five quick blips, each sliding in pitch, with little gaps."""
    sr = random.Random(seed)
    out = []
    for _ in range(sr.randint(3, 5)):
        length = sr.uniform(0.05, 0.11)
        f0, f1 = sr.uniform(700, 1400), sr.uniform(600, 1800)
        m = int(RATE * length)
        phase = 0.0
        for i in range(m):
            t = i / m
            phase += math.tau * (f0 + (f1 - f0) * t) / RATE
            env = math.sin(math.pi * t) ** 0.6
            out.append((math.sin(phase) + 0.25 * math.sin(2 * phase)) * env)
        out += [0.0] * int(RATE * sr.uniform(0.02, 0.06))
    return lowpass(out, 5000)


if shutil.which("ffmpeg") is None:
    print("ffmpeg not found: skipping synthesised sounds")
else:
    write_ogg("ufo_hum", hum(), 0.6)
    write_ogg("abduction", abduction(), 0.8)
    for k in range(3):
        write_ogg("alien_chirp%d" % (k + 1), chirps(10 + k), 0.45)


def vanilla(*sounds, subtitle=None):
    entry = OrderedDict()
    if subtitle:
        entry["subtitle"] = "subtitles.mymod." + subtitle
    entry["sounds"] = [OrderedDict([("name", "minecraft:" + name), ("pitch", pitch)] + ([("volume", volume)] if volume != 1.0 else []))
                       for name, pitch, volume in sounds]
    return entry


merge_json(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("ufo_hum", OrderedDict([("sounds", [OrderedDict([("name", "mymod:ufo_hum"), ("stream", True)])])])),
    ("ufo_beam", vanilla(("block/beacon/ambient", 1.6, 1.0), subtitle="ufo_beam")),
    ("ufo_take", vanilla(("block/beacon/power1", 1.8, 1.0), ("block/beacon/power2", 1.8, 1.0), subtitle="ufo_take")),
    ("ufo_land", vanilla(("tile/piston/out", 0.55, 1.0), subtitle="ufo_land")),
    ("ufo_door", vanilla(("block/iron_door/open1", 0.6, 1.0), ("block/iron_door/open2", 0.6, 1.0), subtitle="ufo_door")),
    ("ufo_hurt", vanilla(("random/anvil_land", 1.6, 0.5), subtitle="ufo_hurt")),
    ("ufo_death", vanilla(("block/beacon/deactivate", 0.5, 1.0), subtitle="ufo_death")),
    ("alien_ambient", OrderedDict([("subtitle", "subtitles.mymod.alien_ambient"),
                                   ("sounds", ["mymod:alien_chirp1", "mymod:alien_chirp2", "mymod:alien_chirp3"])])),
    ("alien_hurt", vanilla(("mob/villager/hit1", 2.0, 1.0), ("mob/villager/hit2", 2.0, 1.0), ("mob/villager/hit3", 2.0, 1.0),
                           subtitle="alien_hurt")),
    ("alien_death", vanilla(("mob/villager/death", 2.0, 1.0), subtitle="alien_death")),
    ("abduction", OrderedDict([("subtitle", "subtitles.mymod.abduction"), ("sounds", ["mymod:abduction"])])),
]))

# ========================================================================================================= lang
merge_json(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.mymod.ufo", "UFO"),
    ("entity.mymod.alien", "Alien"),
    ("entity.mymod.tractor_beam", "Tractor Beam"),
    ("entity.mymod.ufo.abducted", "A blinding light... you can't move!"),
    ("entity.mymod.ufo.returned", "You wake up somewhere else, feeling... probed."),
    ("entity.mymod.ufo.souvenir", "The aliens kept your %s as a souvenir."),
    ("message.mymod.ufo_sighted", "Strange lights in the sky..."),
    ("item.mymod.ufo_spawn_egg", "UFO Spawn Egg"),
    ("item.mymod.alien_spawn_egg", "Alien Spawn Egg"),
    ("item.mymod.alien_alloy", "Alien Alloy"),
    ("item.mymod.alien_reactor", "Alien Reactor"),
    ("effect.mymod.probed", "Probed"),
    ("death.attack.mymod.alien_ray", "%1$s was zapped"),
    ("death.attack.mymod.alien_ray.player", "%1$s was zapped by %2$s"),
    ("death.attack.mymod.alien_ray.item", "%1$s was zapped by %2$s using %3$s"),
    ("subtitles.mymod.ufo_beam", "Tractor beam hums"),
    ("subtitles.mymod.ufo_take", "Something is beamed aboard"),
    ("subtitles.mymod.ufo_land", "UFO legs extend"),
    ("subtitles.mymod.ufo_door", "UFO ramp moves"),
    ("subtitles.mymod.ufo_hurt", "UFO clangs"),
    ("subtitles.mymod.ufo_death", "UFO powers down"),
    ("subtitles.mymod.alien_ambient", "Alien chitters"),
    ("subtitles.mymod.alien_hurt", "Alien squeaks"),
    ("subtitles.mymod.alien_death", "Alien dies"),
    ("subtitles.mymod.abduction", "Eerie wailing rises"),
    ("gamerule.mymod.ufo_sightings", "UFO sightings"),
    ("gamerule.mymod.ufo_sightings.description", "Whether UFOs visit players, roam the sky and land."),
    ("gamerule.mymod.ufo_chance", "UFO chance"),
    ("gamerule.mymod.ufo_chance.description", "The chance, in percent, of a UFO visiting each player in a night (a fifth of that by day)."),
    ("gamerule.mymod.ufo_landing_chance", "UFO landing chance"),
    ("gamerule.mymod.ufo_landing_chance.description", "The chance, in percent, of a UFO landing with its crew near each player in a night."),
    ("gamerule.mymod.alien_abductions", "Alien abductions"),
    ("gamerule.mymod.alien_abductions.description", "Whether landed aliens carry off players who sleep nearby."),
]))
print("ufo assets written")
