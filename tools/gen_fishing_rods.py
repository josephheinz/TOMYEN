"""Builds the fishing system's art and data (see com.jheinz.mymod.fishing):

* the six mod rods (bamboo, iron, steel, duralumin, magmasteel, star steel): a flat 16x16 texture each for the rod
  at rest, cast, and bent under a fighting fish. They keep vanilla's fishing rod geometry (read from the client jar,
  so the line still leaves from the tip) but each has its own shaft ramp, line guides, handle wrap, reel and tip;
* the twelve rod parts (three each of line, hook, reel and lure), as plain item/generated sprites;
* the Tackle Bench: a modeled block (spruce top, dark oak legs, a tackle box on the shelf, a tray of lures, a spool
  and a rod on top), its blockstate, loot table and item, its menu background and the part slots' empty-slot icons;
* item model definitions (the rods switch on mymod:rod_fighting, then vanilla's fishing_rod/cast), recipes with
  their recipe-book advancements, tags, the four vanilla fish's species files, sounds.json entries (vanilla sound
  files, repitched) and the lang entries.

With --preview PATH it also renders a sheet: every rod state beside vanilla's rod, each rod in first person, the
parts, the bench and its menu, and mockups of the three minigame HUD panels.

Every texture is drawn from flat palette ramps placed by hand or by rule (no random noise). The bench model is checked
for z-fighting (tools/zfight.py).

Usage: python3 tools/gen_fishing_rods.py [--preview out.png]"""
import json, math, os, struct, sys, zipfile, zlib
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gunkit import apply, display_matrix, png as write_png
from zfight import check as zfight_check

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None

# ======================================================================================================= utilities


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


CLEAR = (0, 0, 0, 0)


class Img:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [fill] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def blend(self, x, y, c):
        """Draws c over the pixel (c's alpha mixes it in)."""
        if not (0 <= x < self.w and 0 <= y < self.h) or c[3] == 0:
            return
        if c[3] == 255:
            self.set(x, y, c)
            return
        b = self.get(x, y)
        a = c[3] / 255.0
        out_a = a + b[3] / 255.0 * (1 - a)
        if out_a <= 0:
            return
        rgb = [int(round((c[k] * a + b[k] * b[3] / 255.0 * (1 - a)) / out_a)) for k in range(3)]
        self.set(x, y, tuple(rgb) + (int(round(out_a * 255)),))

    def fill(self, x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.blend(x, y, c)

    def paste(self, img, ox, oy, k=1):
        """Draws img (scaled up k times, nearest) with its top left at (ox, oy)."""
        for y in range(img.h * k):
            for x in range(img.w * k):
                self.blend(ox + x, oy + y, img.get(x // k, y // k))

    def save(self, path):
        write_png(path, self.w, self.h, self.px)
        return path


def art(rows, palette):
    """A 16x16 (or any size) image from rows of palette letters ('.' clear)."""
    img = Img(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.set(x, y, palette[ch] if isinstance(palette[ch], tuple) else hexc(palette[ch]))
    return img


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def merge_json(path, entries):
    with open(path) as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tag_add(path, entries):
    """Adds entries to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    for entry in entries:
        if entry not in values:
            values.append(entry)
    write(path, {"values": values})


# ======================================================================================================= vanilla


def minecraft_version():
    with open(os.path.join(REPO, "gradle.properties")) as f:
        for line in f:
            if line.startswith("minecraft_version="):
                return line.split("=", 1)[1].strip()
    sys.exit("minecraft_version not found in gradle.properties")


def client_jar():
    version = minecraft_version()
    path = os.path.expanduser(os.path.join("~", ".gradle", "caches", "fabric-loom", version, "minecraft-client.jar"))
    if not os.path.isfile(path):
        sys.exit(f"no Minecraft {version} client jar at {path}; run ./gradlew build first")
    return path


def read_png(data):
    """Pixels of a non-interlaced 8-bit-or-less PNG (any colour type), as an Img."""
    pos, idat, palette, alpha = 8, b"", b"", b""
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if kind == b"IHDR":
            w, h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = body
        elif kind == b"tRNS":
            alpha = body
        elif kind == b"IDAT":
            idat += body
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    step, stride = max(1, channels * depth // 8), (w * channels * depth + 7) // 8
    raw, prev, img = zlib.decompress(idat), bytearray(stride), Img(w, h)
    for y in range(h):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a, b, c = line[i - step] if i >= step else 0, prev[i], prev[i - step] if i >= step else 0
            p = a + b - c
            paeth = a if abs(p - a) <= abs(p - b) and abs(p - a) <= abs(p - c) else b if abs(p - b) <= abs(p - c) else c
            line[i] = (line[i] + (0, a, b, (a + b) // 2, paeth)[kind]) & 255
        prev = line
        samples = [(line[bit // 8] >> (8 - depth - bit % 8)) & ((1 << depth) - 1) for bit in range(0, w * channels * depth, depth)]
        for x in range(w):
            s = samples[x * channels:(x + 1) * channels]
            if colour == 3:
                i = s[0]
                img.set(x, y, tuple(palette[3 * i:3 * i + 3]) + (alpha[i] if i < len(alpha) else 255,))
            elif colour in (0, 4):
                img.set(x, y, (s[0],) * 3 + ((s[1],) if colour == 4 else (255,)))
            else:
                img.set(x, y, tuple(s) + ((255,) if colour == 2 else ()))
    return img


JAR = zipfile.ZipFile(client_jar())


def vanilla(path):
    return read_png(JAR.read("assets/minecraft/textures/" + path + ".png"))


# ======================================================================================================= rods

# Vanilla's fishing rod, texel by texel, by role (checked against the jar below): T/U the tip, L the line running
# along the rod to the guides, d/m/h/o the shaft's dark, mid, highlight and shadow sides, R/r/q the reel, S the line
# hanging from the tip and K/k the hook. The cast texture is the same without S, K and k.
ROD = [
    "................",
    ".............TU.",
    "............Ldo.",
    "...........Ldmo.",
    "..........LdhoS.",
    ".........Ldho.S.",
    "........Ldho..S.",
    ".......Ldho...S.",
    "......Ldmo....S.",
    ".....Ldmo.....S.",
    ".....dmo.....S..",
    "....dmRRq....S..",
    "...ddorRq...S...",
    "..ddo.qq..K.K...",
    "..oo......kK....",
    "................",
]
# Bent under a fighting fish: the top third arcs over to the right and dips, its tip at (15, 6).
BENT_TOP = [
    "................",
    "................",
    ".............LL.",
    "...........LLddL",
    "..........Lddhhd",
    ".........LdhhooT",
    "........Ldhoo...",
]
GRIP_ROWS = range(11, 15)


def rod_roles(state):
    """{(x, y): role} for the rod at rest ("rest"), cast ("cast") or bent ("bent")."""
    roles = {}
    for y, row in enumerate(ROD):
        for x, ch in enumerate(row):
            if ch == "." or (state != "rest" and ch in "SKk"):
                continue
            roles[(x, y)] = ch
    if state == "bent":
        for key in [k for k in roles if k[1] < len(BENT_TOP)]:
            del roles[key]
        for y, row in enumerate(BENT_TOP):
            for x, ch in enumerate(row):
                if ch != ".":
                    roles[(x, y)] = ch
    return roles


def check_rod_template():
    """The ROD roles must land on vanilla's opaque texels, so the mod rods keep vanilla's outline and line exit."""
    rest, cast = vanilla("item/fishing_rod"), vanilla("item/fishing_rod_cast")
    for img, state in ((rest, "rest"), (cast, "cast")):
        roles = rod_roles(state)
        opaque = {(x, y) for y in range(16) for x in range(16) if img.get(x, y)[3] > 0}
        if opaque != set(roles):
            sys.exit(f"vanilla fishing rod ({state}) no longer matches the template: {sorted(opaque ^ set(roles))}")


class Rod:
    """One rod's look: shaft ramp (o, d, m, h), line colours, guide rows, tip, grip and reel painters."""

    def __init__(self, name, shaft, line, guide, guide_rows, tip, hang, hook, grip, reel, extra=None, shaft_paint=None):
        self.name, self.shaft, self.line, self.guide, self.guide_rows = name, [hexc(c) for c in shaft], hexc(line), hexc(guide), guide_rows
        self.tip, self.hang, self.hook = [hexc(c) for c in tip], [hexc(c) for c in hang], [hexc(c) for c in hook]
        self.grip, self.reel, self.extra, self.shaft_paint = grip, reel, extra, shaft_paint


def stripe(x, y):
    """Which wrap band a grip texel is in: bands run across the rod, one diagonal step wide."""
    return (x - y) // 2


def side(x, y):
    """0 on the rod's lit upper-left side, 1 along its middle, 2 on its shaded lower-right side."""
    return max(0, min(2, x + y - 15))


def paint_rod(rod, state):
    roles = rod_roles(state)
    img = Img(16, 16)
    o, d, m, h = rod.shaft
    for (x, y), role in roles.items():
        if role == "L":
            c = rod.guide if y in rod.guide_rows else rod.line
        elif role in "dmho":
            c = {"o": o, "d": d, "m": m, "h": h}[role]
        elif role == "T":
            c = rod.tip[0]
        elif role == "U":
            c = rod.tip[1]
        elif role == "S":
            c = rod.hang[(x + y) % 2]
        elif role in "Kk":
            c = rod.hook[0 if role == "K" else 1]
        else:
            continue  # the reel: painted below
        img.set(x, y, c)
    if rod.shaft_paint:
        rod.shaft_paint(img, roles)
    # The grip: every shaft texel from row 11 down, plus whatever the rod adds to its silhouette.
    grip = [(x, y) for (x, y), role in roles.items() if y in GRIP_ROWS and role in "dmo"]
    rod.grip(img, grip)
    rod.reel(img)
    if rod.extra:
        rod.extra(img, state)
    return img


def grip_bands(colours_a, colours_b, extra=(), cap=None):
    """A grip painter: wrap bands alternating between two three-tone ramps (lit, middle, shaded), with optional extra
    silhouette texels and a butt cap ramp on the last band."""
    a = [hexc(c) for c in colours_a]
    b = [hexc(c) for c in colours_b]
    capc = [hexc(c) for c in cap] if cap else None

    def paint(img, texels):
        for x, y in list(texels) + list(extra):
            ramp = a if stripe(x, y) % 2 == 0 else b
            if capc and x - y <= -11:
                ramp = capc
            img.set(x, y, ramp[side(x, y)])
    return paint


def reel_patch(rows, palette, origin=(5, 10)):
    """A reel painter: clears vanilla's reel texels and draws rows (5 wide) from origin."""
    def paint(img):
        for x, y in ((6, 11), (7, 11), (8, 11), (6, 12), (7, 12), (8, 12), (6, 13), (7, 13)):
            img.set(x, y, CLEAR)
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    img.set(origin[0] + dx, origin[1] + dy, hexc(palette[ch]))
    return paint


def bamboo_nodes(img, roles):
    """Bamboo's nodes: a darker ring with a pale ridge every third step up the shaft."""
    for (x, y), role in roles.items():
        if y <= 10 and (x - y) % 6 == 0 and role in "dmho":
            img.set(x, y, hexc({"d": "#3f5a12", "m": "#c8dc8a", "h": "#d6e8a0", "o": "#2f4410"}[role]))


def magma_seam(img, roles):
    """A glowing seam up the middle of the charcoal magmasteel shaft, hottest mid-rod, cooling toward each end."""
    for (x, y), role in roles.items():
        if role == "h" and y <= 10:
            t = 1.0 - abs((x - y) - 2) / 12.0
            img.set(x, y, hexc("#fbaa59" if t > 0.7 else "#e66410" if t > 0.3 else "#9a3410"))


def star_glint(img, state):
    """A deliberate glint on the star steel shaft and a star at its tip."""
    if state != "bent":
        img.set(11, 5, hexc("#f4fbff"))
        img.set(14, 0, hexc("#8be6ff", 200))
        img.set(15, 1, hexc("#8be6ff", 200))
    else:
        img.set(12, 5, hexc("#f4fbff"))


ROD_ICONS = OrderedDict()
RODS = [
    # Bamboo: green, jointed, a twine-whipped grip, a little wooden spool of string for a reel.
    Rod("bamboo_rod", ["#2f4410", "#568112", "#78a030", "#97c463"], "#ded6bf", "#ded6bf", (), ["#ded6bf", "#a89c7a"],
        ["#cfc7b0", "#a8a08a"], ["#e0e0e0", "#8a8a8a"],
        grip_bands(["#d9cfb0", "#efe7cc", "#9b8f6c"], ["#6b8a1f", "#8db03a", "#3d5a1f"]),
        reel_patch(["......", ".WSSw", ".SssW", ".wWw."], {"W": "#7a4e24", "w": "#a87038", "S": "#eee6cf", "s": "#c9bf9f"}),
        shaft_paint=bamboo_nodes),
    # Iron: plain iron, dark guides, a brown leather grip, vanilla's little round reel in iron.
    Rod("iron_rod", ["#444444", "#727272", "#a8a8a8", "#d8d8d8"], "#9a9a9a", "#2e2e2e", (4, 7), ["#3a3a3a", "#6a6a6a"],
        ["#8c8c8c", "#6c6c6c"], ["#f0f0f0", "#9a9a9a"],
        grip_bands(["#7a4b26", "#9c6535", "#4a2a12"], ["#5e3618", "#7a4b26", "#3a200c"]),
        reel_patch(["......", ".RRq.", ".rHq.", ".qq.."], {"R": "#e0e0e0", "r": "#a8a8a8", "H": "#ffffff", "q": "#555555"})),
    # Steel: blue-grey steel with red-whipped guides, a fat black rubber grip, a chunky reel with a red drag knob.
    Rod("steel_rod", ["#16191e", "#48515b", "#828d99", "#b9c3cd"], "#8c96a0", "#2c323a", (4, 8), ["#c0392b", "#7a1f16"],
        ["#8c96a0", "#636d78"], ["#e8f0f7", "#828d99"],
        grip_bands(["#3a4048", "#4e5560", "#1a1d22"], ["#2a2e34", "#3a4048", "#16191e"],
                   extra=[(3, 11), (2, 12), (1, 13)], cap=["#828d99", "#b9c3cd", "#48515b"]),
        reel_patch(["......", ".RRr.", ".RCq.", ".rqqk", "....K"],
                   {"R": "#b9c3cd", "r": "#828d99", "C": "#d94a3a", "q": "#2c323a", "k": "#48515b", "K": "#16191e"})),
    # Duralumin: warm pale alloy, teal guides and grip, a sleek open-face reel with a teal spool.
    Rod("duralumin_rod", ["#6e564a", "#a88c7b", "#cfbaac", "#fff8f2"], "#d8cfc8", "#2fa7a0", (4, 8), ["#2fa7a0", "#1c6e69"],
        ["#cfc4bc", "#a89c94"], ["#fff8f2", "#a88c7b"],
        grip_bands(["#2a9a93", "#43c4bb", "#155e59"], ["#2a9a93", "#43c4bb", "#155e59"],
                   extra=[(1, 14), (2, 15)], cap=["#cfbaac", "#fff8f2", "#7e6457"]),
        reel_patch(["......", ".EEe.", ".ETe.", ".ee.."], {"E": "#fff8f2", "e": "#a88c7b", "T": "#43c4bb"})),
    # Magmasteel: charcoal with a glowing seam, a blackstone grip with ember cracks, a reel with a molten core.
    Rod("magmasteel_rod", ["#150f0f", "#352a28", "#4a3c38", "#6f5d57"], "#6f5d57", "#e66410", (3, 7), ["#ffd27a", "#e66410"],
        ["#9a6a50", "#6f4a38"], ["#fbaa59", "#9a3410"],
        grip_bands(["#2f2a2e", "#3e383d", "#1a1619"], ["#2f2a2e", "#e66410", "#1a1619"]),
        reel_patch(["......", ".DGd.", ".GYd.", ".dd.."], {"D": "#4a3c38", "d": "#231b1a", "G": "#e66410", "Y": "#ffd27a"}),
        shaft_paint=magma_seam),
    # Star steel: deep blue with a cyan highlight, a starlit tip, gold-ringed indigo grip, a crystal reel.
    Rod("star_steel_rod", ["#0a0e2e", "#29429b", "#4372cb", "#8be6ff"], "#a8c4f0", "#e8c547", (3, 6, 9), ["#f4fbff", "#8be6ff"],
        ["#b8d0f4", "#8aa6d8"], ["#e8c547", "#a8852a"],
        grip_bands(["#1b2a6e", "#29429b", "#0f1743"], ["#e8c547", "#f6e08a", "#a8852a"],
                   extra=[(1, 14), (1, 15), (2, 15)], cap=["#67ace5", "#8be6ff", "#29429b"]),
        reel_patch(["......", ".CWb.", ".cCb.", ".gG.."],
                   {"C": "#8be6ff", "W": "#f4fbff", "c": "#4372cb", "b": "#152059", "g": "#e8c547", "G": "#a8852a"}),
        extra=star_glint),
]


def rod_textures():
    check_rod_template()
    out = OrderedDict()
    for rod in RODS:
        for state, suffix in (("rest", ""), ("cast", "_cast"), ("bent", "_bent")):
            img = paint_rod(rod, state)
            img.save(os.path.join(ASSETS, "textures", "item", rod.name + suffix + ".png"))
            out[rod.name + suffix] = img
    return out


def rod_models():
    for rod in RODS:
        for suffix in ("", "_cast", "_bent"):
            write(os.path.join(ASSETS, "models", "item", rod.name + suffix + ".json"), OrderedDict([
                ("parent", "minecraft:item/handheld_rod"), ("textures", {"layer0": "mymod:item/" + rod.name + suffix})]))
        model = lambda s: OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + rod.name + s)])
        write(os.path.join(ASSETS, "items", rod.name + ".json"), {"model": OrderedDict([
            ("type", "minecraft:condition"), ("property", "mymod:rod_fighting"),
            ("on_true", model("_bent")),
            ("on_false", OrderedDict([("type", "minecraft:condition"), ("property", "minecraft:fishing_rod/cast"),
                                      ("on_true", model("_cast")), ("on_false", model(""))]))])})


# ======================================================================================================= parts

# A spool standing on end, seen a little from above: flanges top and bottom, the line wound between them, and its
# loose end trailing off to the right. F/f/g flange (lit, face, edge), A/B/C the line (lit, middle, shaded) in turns.
SPOOL = [
    "................",
    "................",
    ".....gggggg.....",
    "...gfFFFFFFfg...",
    "...gffFFFFffg...",
    "....gggggggg....",
    ".....ABBBBC.....",
    ".....aBbBbC.....",
    ".....ABBBBC.....",
    ".....aBbBbCEE...",
    ".....ABBBBC..E..",
    "...gggggggggg.E.",
    "...gfffffffffgE.",
    "....gggggggggg..",
    "................",
    "................",
]
# A J hook: eye at the top, shank, bend, point and barb. a lit, b middle, c shaded, n the knotted line.
HOOK = [
    "................",
    "...........nn...",
    "..........cbbc..",
    "..........b..b..",
    "..........cbbc..",
    "...........ab...",
    "...........ab...",
    "...........ab...",
    "...a.......ab...",
    "...ab......ab...",
    "...abx.....ab...",
    "...ab......ab...",
    "....ab....ab....",
    ".....abbbbbc....",
    "......cccc......",
    "................",
]
# A side-on reel: rim, face and hub, its crank arm and knob, and the foot that clamps to the rod.
REEL = [
    "................",
    "................",
    ".....cccccc.....",
    "....cbbbbbbc....",
    "...cbaaaabbbc...",
    "...cbaHHHbbbc...",
    "...cbaHXHbbbckk.",
    "...cbaHHHbbbc.kK",
    "...cbbbbbbbbc..K",
    "...ccbbbbbbcc...",
    "....cccccccc....",
    "......cddc......",
    ".....dddddd.....",
    "................",
    "................",
    "................",
]
# A spoon lure: a teardrop blade on a split ring, glowing spots, a small treble hook trailing.
SPOON = [
    "................",
    "............ii..",
    "...........i..i.",
    "...........Dii..",
    ".........DDGD...",
    "........DGgGD...",
    ".......DGGGGD...",
    "......DGGGgD....",
    ".....DGgGGGD....",
    ".....DGGGGD.....",
    "....DGGGgD......",
    "....DGGGD.......",
    ".....DDD........",
    "....h...........",
    "...h.h..........",
    "....h...........",
]
# A fly: a bead head, a wrapped body and swept-back feathers, sparkling with fae dust.
FLY = [
    "................",
    "................",
    "...........*....",
    "..........***...",
    "...P.......*....",
    "...PP.....hH....",
    "....PPp..hHh....",
    ".....PpPBBBh....",
    "......pPBbB.....",
    "....*..QqBB.....",
    "...***QqQ.......",
    "....*QqQ........",
    "....Qq..........",
    "....k...........",
    "...k.k..........",
    "....k...........",
]
# The Fish Finder: a duralumin case, a round green CRT showing sonar arcs, an antenna.
FINDER = [
    "............a...",
    "............a...",
    "............A...",
    "..OOOOOOOOOOOO..",
    "..OLLLLLLLLLLO..",
    "..OLdDDDDDDdlO..",
    "..OLDsSssSsDlO..",
    "..OLDsssSssDlO..",
    "..OLDSsssssDlO..",
    "..OLDsssPssDlO..",
    "..OLdDDDDDDdlO..",
    "..OLLLLLLLLLlO..",
    "..OlrlYlllllLO..",
    "..OOOOOOOOOOOO..",
    "................",
    "................",
]


def spool(flange, line, loose=None):
    pal = {"F": flange[0], "f": flange[1], "g": flange[2], "A": line[0], "a": line[0], "B": line[1], "b": line[2],
           "C": line[2], "E": loose or line[1]}
    return art(SPOOL, pal)


def hook(ramp, line="#c8c8c8", barbs=()):
    img = art(HOOK, {"a": ramp[0], "b": ramp[1], "c": ramp[2], "x": ramp[1], "n": line})
    for x, y in barbs:
        img.set(x, y, hexc(ramp[1]))
    return img


def reel(ramp, hub, crank, foot, teeth=None, eye=None):
    pal = {"a": ramp[0], "b": ramp[1], "c": ramp[2], "H": hub[0], "X": hub[1], "k": crank[0], "K": crank[1], "d": foot}
    img = art(REEL, pal)
    if teeth:
        # A brass gear behind the hub: four teeth around it.
        for x, y in ((7, 4), (5, 6), (9, 6), (7, 8)):
            img.set(x, y, hexc(teeth))
    if eye:
        img.set(6, 5, hexc(eye[0]))
        img.set(7, 5, hexc(eye[1]))
        img.set(6, 6, hexc(eye[1]))
        img.set(7, 6, hexc(eye[2]))
    return img


PART_ART = OrderedDict([
    ("rubber_line", lambda: spool(["#d98a3a", "#b0682a", "#5e3416"], ["#55575d", "#33353a", "#1b1c1f"])),
    ("nose_hair_line", lambda: spool(["#9fbf5a", "#7e9f42", "#47602a"], ["#7a5d44", "#5a4330", "#3b2a1e"], loose="#7a5d44")),
    ("fae_silk_line", lambda: spool(["#a8d8f0", "#7fb8d9", "#3f6f8f"], ["#fdf6ff", "#e3cff7", "#b79be0"], loose="#e3cff7")),
    ("iron_hook", lambda: hook(["#ffffff", "#c6c6c6", "#6e6e6e"])),
    ("silver_hook", lambda: hook(["#ffffff", "#c8dcee", "#6a8098"])),
    ("tungsten_hook", lambda: hook(["#a7aeb8", "#6c737e", "#2a2d33"], barbs=((10, 9), (10, 7), (2, 9)))),
    ("copper_reel", lambda: reel(["#f7c79e", "#c87a45", "#6e3518"], ["#e09a65", "#4a2410"], ["#8a4a24", "#e8b48a"], "#6e3518")),
    ("geared_reel", lambda: reel(["#e8f0f7", "#828d99", "#2c323a"], ["#e8c547", "#a8852a"], ["#48515b", "#b9c3cd"], "#2c323a",
                                 teeth="#a8852a")),
    ("servo_reel", lambda: reel(["#c9a26a", "#8a6a3a", "#3e2c16"], ["#3a3a3a", "#1a1a1a"], ["#5a4428", "#c9a26a"], "#3e2c16",
                                eye=["#ff6a5a", "#d01e1e", "#7a0e0e"])),
    ("glow_lure", lambda: art(SPOON, {"D": "#0e3a3f", "G": "#1f6f74", "g": "#9ff7ef", "i": "#b8b8b8", "h": "#d8d8d8"})),
    ("fae_dust_lure", lambda: art(FLY, {"P": "#f2a8e0", "p": "#c070c8", "Q": "#b88cf0", "q": "#7a52c0", "B": "#f6e08a",
                                        "b": "#c9a84a", "h": "#ffe9fb", "H": "#f2a8e0", "*": "#fff6ff", "k": "#d8d8d8"})),
    ("fish_finder", lambda: art(FINDER, {"O": "#6e564a", "L": "#eadcd2", "l": "#a88c7b", "D": "#1d2b22", "d": "#2c3b30",
                                         "s": "#173d22", "S": "#46e07a", "P": "#c8ffd8", "a": "#2c323a", "A": "#d94a3a",
                                         "r": "#d94a3a", "Y": "#46e07a"})),
])


def part_textures():
    out = OrderedDict()
    for name, make in PART_ART.items():
        img = make()
        img.save(os.path.join(ASSETS, "textures", "item", name + ".png"))
        out[name] = img
        write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([
            ("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/" + name})]))
        write(os.path.join(ASSETS, "items", name + ".json"),
              {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + name)])})
    return out


# ======================================================================================================= preview helpers

def scaled(img, k):
    out = Img(img.w * k, img.h * k)
    out.paste(img, 0, 0, k)
    return out


def checker(w, h, a="#2a2f36", b="#30363e", cell=8):
    img = Img(w, h)
    for y in range(h):
        for x in range(w):
            img.set(x, y, hexc(a if (x // cell + y // cell) % 2 == 0 else b))
    return img


# ======================================================================================================= menu art


def outline(mask_img, colour="#555555"):
    """Vanilla's empty-slot icon style: a one-texel outline of an item's silhouette."""
    out = Img(mask_img.w, mask_img.h)
    for y in range(mask_img.h):
        for x in range(mask_img.w):
            if mask_img.get(x, y)[3] == 0:
                continue
            if any(mask_img.get(x + dx, y + dy)[3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.set(x, y, hexc(colour))
    return out


def slot_sprites(parts, rods):
    """The empty-slot icons: the rod slot shows a rod, each part slot its kind of part."""
    sprites = OrderedDict([("fishing_rod", outline(rods["bamboo_rod"])), ("rod_line", outline(parts["rubber_line"])),
                           ("rod_hook", outline(parts["iron_hook"])), ("rod_reel", outline(parts["copper_reel"])),
                           ("rod_lure", outline(parts["glow_lure"]))])
    for name, img in sprites.items():
        img.save(os.path.join(ASSETS, "textures", "gui", "sprites", "container", "slot", name + ".png"))
    return sprites


# The menu layout (TackleBenchMenu's slot positions and TackleBenchScreen's readout).
ROD_SLOT = (20, 36)
PART_SLOTS = [(52, 26), (52, 46), (74, 26), (74, 46)]
READOUT = (96, 16, 168, 80)


def frame(img, x, y, w, h, inner="#8b8b8b"):
    """A sunken vanilla slot frame: dark top and left edges, white bottom and right."""
    img.fill(x, y, x + w, y + h, hexc(inner))
    img.fill(x, y, x + w - 1, y + 1, hexc("#373737"))
    img.fill(x, y, x + 1, y + h - 1, hexc("#373737"))
    img.fill(x + 1, y + h - 1, x + w, y + h, hexc("#ffffff"))
    img.fill(x + w - 1, y + 1, x + w, y + h, hexc("#ffffff"))


def menu_background():
    """Vanilla's crafting table menu with its grid cleared, re-laid for the bench: a big rod slot, the four part
    slots in a square beside it (with a thin rail joining them), and a dark readout panel on the right."""
    img = read_png(JAR.read("assets/minecraft/textures/gui/container/crafting_table.png"))
    img.fill(7, 14, 169, 81, hexc("#c6c6c6"))
    rx, ry = ROD_SLOT
    frame(img, rx - 5, ry - 5, 26, 26)
    img.fill(rx + 21, ry + 8, 51, ry + 9, hexc("#8b8b8b"))
    img.fill(48, PART_SLOTS[0][1] + 8, 49, PART_SLOTS[1][1] + 9, hexc("#8b8b8b"))
    for x, y in PART_SLOTS:
        frame(img, x - 1, y - 1, 18, 18)
    x0, y0, x1, y1 = READOUT
    frame(img, x0, y0, x1 - x0, y1 - y0, inner="#1d2731")
    img.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, hexc("#24313c"))
    img.save(os.path.join(ASSETS, "textures", "gui", "container", "tackle_bench.png"))
    return img


# ======================================================================================================= the bench

KIT = Img(32, 32)


def kit_texture():
    """The bench's small things, two texels to a model pixel: the tackle box (front, side, lid), the tray of lures,
    the spool (line and flange), and the rod lying on top (cork handle, shaft, reel) with the box's brass latch."""
    k = KIT
    teal, teal_light, teal_dark, teal_edge = hexc("#2f6b5a"), hexc("#4a9c84"), hexc("#1d4438"), hexc("#24584a")
    brass, brass_dark = hexc("#e8c547"), hexc("#a8852a")
    # Box front (0,0)-(18,8) and side (18,0)-(32,8): painted steel, a lid seam, a bevel top and bottom.
    for x0, x1 in ((0, 18), (18, 32)):
        k.fill(x0, 0, x1, 8, teal)
        k.fill(x0, 0, x1, 1, teal_light)
        k.fill(x0, 2, x1, 3, teal_dark)
        k.fill(x0, 7, x1, 8, teal_dark)
        k.fill(x0, 0, x0 + 1, 8, teal_edge)
        k.fill(x1 - 1, 0, x1, 8, teal_edge)
    # Box lid (0,8)-(18,22): a rim, and a carry handle across the middle.
    k.fill(0, 8, 18, 22, hexc("#3a8270"))
    k.fill(0, 8, 18, 9, teal_light)
    k.fill(0, 21, 18, 22, teal_dark)
    k.fill(0, 8, 1, 22, teal_edge)
    k.fill(17, 8, 18, 22, teal_edge)
    k.fill(4, 14, 14, 15, hexc("#4a4a4a"))
    k.fill(4, 15, 14, 16, hexc("#2a2a2a"))
    k.fill(4, 13, 5, 16, hexc("#2a2a2a"))
    k.fill(13, 13, 14, 16, hexc("#2a2a2a"))
    # Tray top (18,8)-(30,20): a wooden rim and six compartments, each with its own lure.
    wood, wood_dark, well = hexc("#9a7444"), hexc("#6e5430"), hexc("#3a2e22")
    k.fill(18, 8, 30, 20, wood)
    for cx in range(3):
        for cy in range(2):
            x, y = 19 + cx * 4, 9 + cy * 5
            k.fill(x, y, x + 3, y + 4, well)
    k.fill(18, 19, 30, 20, wood_dark)
    lures = [((20, 10), "#d94a3a", (20, 11), "#f4f4f4"), ((24, 11), "#c6c6c6", (25, 11), "#ffffff"),
             ((28, 10), "#e8c547", (28, 11), "#c070c8"), ((20, 15), "#46c4e0", (21, 16), "#1f6f74"),
             ((24, 15), "#f2a8e0", (25, 16), "#7a52c0"), ((28, 16), "#9ff7ef", (28, 15), "#1f6f74")]
    for a, ca, b, cb in lures:
        k.set(a[0], a[1], hexc(ca))
        k.set(b[0], b[1], hexc(cb))
    # Tray side (18,20)-(30,23).
    k.fill(18, 20, 30, 23, wood)
    k.fill(18, 22, 30, 23, wood_dark)
    # Spool line (0,22)-(4,26), wound in turns, and flange (4,22)-(10,28): red with a dark hub.
    for y in range(22, 26):
        k.fill(0, y, 4, y + 1, hexc("#eee6cf" if y % 2 == 0 else "#d4c9aa"))
    k.fill(4, 22, 10, 28, hexc("#b0392b"))
    k.fill(4, 22, 10, 23, hexc("#d4574a"))
    k.fill(6, 24, 8, 26, hexc("#4a1510"))
    # Rod handle (10,22)-(18,26): cork with darker bands; reel (20,24)-(26,30); latch (26,24)-(30,26).
    k.fill(10, 22, 18, 26, hexc("#c9a26a"))
    for x in (12, 16):
        k.fill(x, 22, x + 1, 26, hexc("#9a7444"))
    k.fill(10, 25, 18, 26, hexc("#9a7444"))
    k.fill(20, 24, 26, 30, hexc("#828d99"))
    k.fill(21, 25, 25, 29, hexc("#b9c3cd"))
    k.fill(22, 26, 24, 28, hexc("#2c323a"))
    k.fill(26, 24, 30, 26, brass)
    k.fill(26, 25, 30, 26, brass_dark)
    # Rod shaft (0,28)-(21,30): dark steel with a highlight.
    k.fill(0, 28, 21, 30, hexc("#3a4048"))
    k.fill(0, 28, 21, 29, hexc("#6a7480"))
    k.save(os.path.join(ASSETS, "textures", "block", "tackle_bench_kit.png"))
    return k


def auto_uv(face, frm, to):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[face]


def element(name, frm, to, faces):
    """faces: {face: (texture, uv or None for auto)}."""
    out = OrderedDict([("name", name), ("from", list(frm)), ("to", list(to)), ("faces", OrderedDict())])
    for face, (tex, uv) in faces.items():
        f = OrderedDict([("uv", [round(v, 4) for v in (uv or auto_uv(face, frm, to))]), ("texture", "#" + tex)])
        if face == "down" and frm[1] == 0:
            f["cullface"] = "down"
        out["faces"][face] = f
    return out


def all_faces(tex, skip=()):
    return {f: (tex, None) for f in ("north", "east", "south", "west", "up", "down") if f not in skip}


def bench_elements():
    e = [element("top", (0, 13, 0), (16, 16, 16), all_faces("top"))]
    for i, (x, z) in enumerate(((1, 1), (13, 1), (1, 13), (13, 13))):
        e.append(element(f"leg{i}", (x, 0, z), (x + 2, 13, z + 2), all_faces("leg", skip=("up",))))
    rail = lambda n, f, t, skip: e.append(element(n, f, t, all_faces("leg", skip=("up",) + skip)))
    rail("rail_front", (3, 11, 1.5), (13, 13, 2.5), ("east", "west"))
    rail("rail_back", (3, 11, 13.5), (13, 13, 14.5), ("east", "west"))
    rail("rail_west", (1.5, 11, 3), (2.5, 13, 13), ("north", "south"))
    rail("rail_east", (13.5, 11, 3), (14.5, 13, 13), ("north", "south"))
    e.append(element("shelf", (1.5, 3, 1.5), (14.5, 4.5, 14.5), all_faces("top")))
    # The tackle box on the shelf, its latch on the front.
    e.append(element("tackle_box", (3.5, 4.5, 4), (12.5, 8.5, 11), {
        "north": ("kit", [0, 0, 9, 4]), "south": ("kit", [0, 0, 9, 4]), "east": ("kit", [9, 0, 16, 4]),
        "west": ("kit", [9, 0, 16, 4]), "up": ("kit", [0, 4, 9, 11])}))
    e.append(element("latch", (7.25, 5.5, 3.5), (8.75, 7, 4), {
        "north": ("kit", [13, 12, 15, 13]), "east": ("kit", [13, 12, 13.5, 13]), "west": ("kit", [13, 12, 13.5, 13]),
        "up": ("kit", [13, 12, 15, 12.5]), "down": ("kit", [13, 12.5, 15, 13])}))
    # On top: a tray of lures, a spool of line, a rod with a cork handle and its reel.
    side = ("kit", [9, 10, 15, 11.5])
    e.append(element("tray", (8.5, 16, 2), (14.5, 17.5, 8), {
        "north": side, "south": side, "east": side, "west": side, "up": ("kit", [9, 4, 15, 10])}))
    flange_side = ("kit", [2, 11, 5, 11.5])
    flange = {"north": flange_side, "south": flange_side, "east": flange_side, "west": flange_side, "up": ("kit", [2, 11, 5, 14])}
    e.append(element("spool_bottom", (2, 16, 2), (5, 16.5, 5), flange))
    drum = ("kit", [0, 11, 2, 13])
    e.append(element("spool_line", (2.5, 16.5, 2.5), (4.5, 18.5, 4.5), {"north": drum, "south": drum, "east": drum, "west": drum}))
    e.append(element("spool_top", (2, 18.5, 2), (5, 19, 5), dict(flange, down=("kit", [2, 11, 5, 14]))))
    cork = ("kit", [5, 11, 9, 13])
    e.append(element("rod_handle", (1.5, 16, 10.5), (5, 17, 11.5), {
        "north": ("kit", [5, 11, 8.5, 12]), "south": ("kit", [5, 11, 8.5, 12]), "west": ("kit", [5, 11, 6, 12]),
        "east": ("kit", [5, 11, 6, 12]), "up": ("kit", [5, 11, 8.5, 12])}))
    shaft = ("kit", [0, 14, 10.5, 14.5])
    e.append(element("rod_shaft", (5, 16.25, 10.75), (15.5, 16.75, 11.25), {
        "north": shaft, "south": shaft, "up": shaft, "east": ("kit", [0, 14, 0.5, 14.5])}))
    reel_face = ("kit", [10, 12, 13, 15])
    e.append(element("rod_reel", (5.75, 16, 11.25), (7.75, 18, 12.25), {
        "north": reel_face, "south": reel_face, "east": ("kit", [10, 12, 10.5, 14]), "west": ("kit", [10, 12, 10.5, 14]),
        "up": ("kit", [10, 12, 12, 12.5])}))
    return e


def bench_model():
    elements = bench_elements()
    clashes = zfight_check(elements)
    if clashes:
        sys.exit("tackle bench z-fighting: " + repr(clashes))
    model = OrderedDict([
        ("parent", "minecraft:block/block"),
        ("textures", OrderedDict([("particle", "minecraft:block/spruce_planks"), ("top", "minecraft:block/spruce_planks"),
                                  ("leg", "minecraft:block/dark_oak_planks"), ("kit", "mymod:block/tackle_bench_kit")])),
        ("elements", elements)])
    write(os.path.join(ASSETS, "models", "block", "tackle_bench.json"), model)
    write(os.path.join(ASSETS, "blockstates", "tackle_bench.json"), {"variants": OrderedDict(
        (f"facing={facing}", OrderedDict([("model", "mymod:block/tackle_bench")] + ([("y", y)] if y else [])))
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)))})
    write(os.path.join(ASSETS, "items", "tackle_bench.json"),
          {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:block/tackle_bench")])})
    return elements


# ======================================================================================================= data


def advancement(name, items, category):
    criteria = OrderedDict()
    for i, item in enumerate(items):
        criteria["has_item" if i == 0 else f"has_item_{i}"] = {"conditions": {"items": [{"items": item}]},
                                                               "trigger": "minecraft:inventory_changed"}
    criteria["has_the_recipe"] = {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, "mymod", "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria), ("requirements", [list(criteria)]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shaped(name, pattern, key, unlock, category="tools"):
    write(os.path.join(DATA, "mymod", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", "equipment" if category == "tools" else "misc"), ("key", key),
        ("pattern", pattern), ("result", OrderedDict([("count", 1), ("id", "mymod:" + name)]))]))
    advancement(name, unlock, category)


def shapeless(name, ingredients, unlock, category="tools"):
    write(os.path.join(DATA, "mymod", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", "equipment" if category == "tools" else "misc"),
        ("ingredients", ingredients), ("result", OrderedDict([("count", 1), ("id", "mymod:" + name)]))]))
    advancement(name, unlock, category)


ROD_NAMES = [rod.name for rod in RODS]


def recipes():
    rod = lambda name, material, handle: shaped(name, ["  M", " MS", "H S"], OrderedDict([
        ("M", material), ("S", "minecraft:string"), ("H", handle)]), [material])
    # Each rod is its material in vanilla's rod shape, with a handle that suits it.
    rod("bamboo_rod", "minecraft:bamboo", "minecraft:bamboo")
    rod("iron_rod", "minecraft:iron_ingot", "minecraft:leather")
    rod("steel_rod", "mymod:steel_ingot", "mymod:rubber")
    rod("duralumin_rod", "mymod:duralumin_sheet", "mymod:rubber")
    rod("magmasteel_rod", "mymod:magmasteel_ingot", "minecraft:blaze_rod")
    rod("star_steel_rod", "mymod:star_steel_ingot", "mymod:star_crystal_shard")
    # Lines.
    shapeless("rubber_line", ["mymod:rubber", "mymod:rubber", "minecraft:string"], ["mymod:rubber"])
    shapeless("nose_hair_line", ["mymod:nose_hair", "mymod:nose_hair", "mymod:nose_hair", "minecraft:slime_ball"], ["mymod:nose_hair"])
    shapeless("fae_silk_line", ["minecraft:string", "minecraft:string", "mymod:fae_dust", "mymod:fae_dust"], ["mymod:fae_dust"])
    # Hooks: two nuggets and a flint to grind the point; tungsten's barbs are carbide.
    shapeless("iron_hook", ["minecraft:iron_nugget", "minecraft:iron_nugget", "minecraft:flint"], ["minecraft:iron_nugget"])
    shapeless("silver_hook", ["mymod:silver_nugget", "mymod:silver_nugget", "minecraft:flint"], ["mymod:silver_nugget"])
    shapeless("tungsten_hook", ["mymod:tungsten_nugget", "mymod:tungsten_nugget", "mymod:tungsten_carbide"], ["mymod:tungsten_nugget"])
    # Reels.
    shaped("copper_reel", [" C ", "CIC", " S "], OrderedDict([("C", "minecraft:copper_ingot"), ("I", "minecraft:iron_nugget"),
                                                             ("S", "minecraft:stick")]), ["minecraft:copper_ingot"])
    shaped("geared_reel", [" T ", "SGS", " C "], OrderedDict([("T", "mymod:tech_scrap"), ("S", "mymod:steel_ingot"),
                                                             ("G", "mymod:copper_reel"), ("C", "minecraft:copper_ingot")]),
           ["mymod:copper_reel", "mymod:tech_scrap"])
    shaped("servo_reel", [" A ", "SGS", " P "], OrderedDict([("A", "mymod:servo_arm"), ("S", "mymod:steel_ingot"),
                                                            ("G", "mymod:geared_reel"), ("P", "mymod:computer_chip")]),
           ["mymod:servo_arm"])
    # Lures.
    shapeless("glow_lure", ["minecraft:glow_ink_sac", "minecraft:iron_nugget", "minecraft:feather"], ["minecraft:glow_ink_sac"])
    shapeless("fae_dust_lure", ["mymod:fae_dust", "mymod:fae_dust", "minecraft:feather", "minecraft:gold_nugget"], ["mymod:fae_dust"])
    shaped("fish_finder", ["D", "T", "C"], OrderedDict([("D", "mymod:duralumin_sheet"), ("T", "mymod:cathode_tube"),
                                                       ("C", "mymod:computer_chip")]), ["mymod:cathode_tube", "mymod:computer_chip"])
    # The bench: a crafting table with a fishing rod over it, legs of planks, a string net.
    shaped("tackle_bench", ["SFS", "PTP", "P P"], OrderedDict([("S", "minecraft:string"), ("F", "minecraft:fishing_rod"),
                                                              ("P", "#minecraft:planks"), ("T", "minecraft:crafting_table")]),
           ["minecraft:fishing_rod"], category="misc")


def data():
    write(os.path.join(DATA, "mymod", "loot_table", "blocks", "tackle_bench.json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("rolls", 1), ("entries", [{"type": "minecraft:item", "name": "mymod:tackle_bench"}]),
                                ("conditions", [{"condition": "minecraft:survives_explosion"}])])]),
        ("random_sequence", "mymod:blocks/tackle_bench")]))
    rods = ["mymod:" + name for name in ROD_NAMES]
    for tag in ("fishing", "durability", "vanishing"):
        tag_add(os.path.join(DATA, "minecraft", "tags", "item", "enchantable", tag + ".json"), rods)
    tag_add(os.path.join(DATA, "c", "tags", "item", "tools", "fishing_rod.json"), rods)
    tag_add(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "axe.json"), ["mymod:tackle_bench"])
    # Cursed and evil catches, which a silver hook makes easier: species items go in here.
    tag_add(os.path.join(DATA, "mymod", "tags", "item", "cursed_catches.json"), [])
    recipes()
    species = [
        # Vanilla's own fish, so the system works with nothing else installed. Vanilla's odds are cod 60, salmon 25,
        # pufferfish 13, tropical fish 2; tropical fish are kept to warm water and jungles here, and commoner there.
        ("vanilla_cod", OrderedDict([("item", "minecraft:cod"), ("weight", 60), ("difficulty", 1), ("behavior", "steady")])),
        ("vanilla_salmon", OrderedDict([("item", "minecraft:salmon"), ("weight", 25), ("difficulty", 2), ("behavior", "thrasher")])),
        ("vanilla_pufferfish", OrderedDict([("item", "minecraft:pufferfish"), ("weight", 13), ("difficulty", 2),
                                            ("behavior", "floater")])),
        ("vanilla_tropical_fish", OrderedDict([("item", "minecraft:tropical_fish"), ("weight", 8), ("difficulty", 3),
                                               ("behavior", "darting"),
                                               ("biomes", ["minecraft:warm_ocean", "minecraft:lukewarm_ocean",
                                                           "minecraft:deep_lukewarm_ocean", "#minecraft:is_jungle",
                                                           "minecraft:mangrove_swamp", "minecraft:lush_caves"])])),
    ]
    for name, body in species:
        write(os.path.join(DATA, "mymod", "fishing_species", name + ".json"), body)


# ======================================================================================================= sounds, lang


def sound(name, files, volume=1.0, pitch=1.0):
    entries = []
    for f in files:
        e = OrderedDict([("name", "minecraft:" + f)])
        if volume != 1.0:
            e["volume"] = volume
        if pitch != 1.0:
            e["pitch"] = pitch
        entries.append(e)
    return name, OrderedDict([("subtitle", "subtitles.mymod." + name), ("sounds", entries)])


SOUNDS = OrderedDict([
    sound("fishing_reel_click", ["random/click"], 0.5, 1.5),
    sound("fishing_line_creak", ["item/crossbow/loading_middle1", "item/crossbow/loading_middle2",
                                                 "item/crossbow/loading_middle3", "item/crossbow/loading_middle4"], 0.6, 1.4),
    sound("fishing_hooked", ["entity/fish/flop1", "entity/fish/flop2", "entity/fish/flop3", "entity/fish/flop4"]),
    sound("fishing_catch", ["random/splash"], 0.8, 1.15),
    sound("fishing_line_snap", ["random/break"], 0.9, 1.6),
    sound("fishing_escape", ["entity/fish/swim1", "entity/fish/swim2", "entity/fish/swim3", "entity/fish/swim4"]),
    sound("fishing_strike_hit", ["note/pling"], 0.6),
    sound("fishing_strike_miss", ["note/bass"], 0.7),
    sound("fishing_sonar", ["note/bell"], 0.5),
])
SUBTITLES = {"fishing_reel_click": "Reel clicks", "fishing_line_creak": "Line creaks", "fishing_hooked": "Fish hooked",
             "fishing_catch": "Fish landed", "fishing_line_snap": "Line snaps", "fishing_escape": "Fish escapes",
             "fishing_strike_hit": "Strike lands", "fishing_strike_miss": "Strike misses", "fishing_sonar": "Sonar pings"}

LANG = OrderedDict([
    ("item.mymod.bamboo_rod", "Bamboo Rod"),
    ("item.mymod.iron_rod", "Iron Rod"),
    ("item.mymod.steel_rod", "Steel Rod"),
    ("item.mymod.duralumin_rod", "Duralumin Rod"),
    ("item.mymod.magmasteel_rod", "Magmasteel Rod"),
    ("item.mymod.star_steel_rod", "Star Steel Rod"),
    ("item.mymod.rubber_line", "Rubber Fishing Line"),
    ("item.mymod.nose_hair_line", "Nose Hair Fishing Line"),
    ("item.mymod.fae_silk_line", "Fae Silk Fishing Line"),
    ("item.mymod.iron_hook", "Iron Fish Hook"),
    ("item.mymod.silver_hook", "Silver Fish Hook"),
    ("item.mymod.tungsten_hook", "Barbed Tungsten Hook"),
    ("item.mymod.copper_reel", "Copper Reel"),
    ("item.mymod.geared_reel", "Geared Reel"),
    ("item.mymod.servo_reel", "Servo Reel"),
    ("item.mymod.glow_lure", "Glow Lure"),
    ("item.mymod.fae_dust_lure", "Fae Dust Lure"),
    ("item.mymod.fish_finder", "Fish Finder"),
    ("block.mymod.tackle_bench", "Tackle Bench"),
    ("entity.mymod.rod_bobber", "Fishing Bobber"),
    ("container.mymod.tackle_bench", "Tackle Bench"),
    ("container.mymod.tackle_bench.empty", "Place a mod rod"),
    ("container.mymod.tackle_bench.stat.tier", "Tier"),
    ("container.mymod.tackle_bench.stat.zone", "Green zone"),
    ("container.mymod.tackle_bench.stat.speed", "Fish speed"),
    ("container.mymod.tackle_bench.stat.hold", "Hook hold"),
    ("container.mymod.tackle_bench.stat.line", "Line strength"),
    ("container.mymod.tackle_bench.stat.reel", "Reel speed"),
    ("container.mymod.tackle_bench.stat.bite", "Bite wait"),
    ("container.mymod.tackle_bench.stat.rare", "Rare bias"),
    ("container.mymod.tackle_bench.tag.lava", "LAVA"),
    ("container.mymod.tackle_bench.tag.sonar", "SONAR"),
    ("container.mymod.tackle_bench.tag.servo", "SERVO"),
    ("container.mymod.tackle_bench.tag.night", "NIGHT"),
    ("container.mymod.tackle_bench.tag.cursed", "CURSED"),
    ("key.category.mymod.fishing", "My Mod: Fishing"),
    ("key.mymod.reel", "Reel In (fishing minigame)"),
    ("tooltip.mymod.rod.tier", "Tier %s rod"),
    ("tooltip.mymod.rod.lava", "Fishes in lava"),
    ("tooltip.mymod.rod.slot", "%s: %s"),
    ("tooltip.mymod.rod.customize", "Fit lines, hooks, reels and lures at a Tackle Bench"),
    ("tooltip.mymod.rod_part", "Rod part: %s"),
    ("tooltip.mymod.rod_part.fit", "Fit it to a mod rod at a Tackle Bench"),
    ("tooltip.mymod.rod_slot.line", "Line"),
    ("tooltip.mymod.rod_slot.line.stock", "String"),
    ("tooltip.mymod.rod_slot.hook", "Hook"),
    ("tooltip.mymod.rod_slot.hook.stock", "Bent pin"),
    ("tooltip.mymod.rod_slot.reel", "Reel"),
    ("tooltip.mymod.rod_slot.reel.stock", "Plain spool"),
    ("tooltip.mymod.rod_slot.lure", "Lure"),
    ("tooltip.mymod.rod_slot.lure.stock", "Plain bobber"),
    ("tooltip.mymod.rod_effect.bite_sooner", "Bites %ss sooner"),
    ("tooltip.mymod.rod_effect.bite_later", "Bites %ss later"),
    ("tooltip.mymod.rod_effect.night_bite", "Bites %ss sooner at night"),
    ("tooltip.mymod.rod_effect.zone", "%s green zone"),
    ("tooltip.mymod.rod_effect.fish_slower", "Fish move %s slower"),
    ("tooltip.mymod.rod_effect.fish_faster", "Fish move %s faster"),
    ("tooltip.mymod.rod_effect.escape", "%s hook hold (fewer escapes)"),
    ("tooltip.mymod.rod_effect.line", "%s line strength (fewer snaps)"),
    ("tooltip.mymod.rod_effect.reel", "%s reel speed"),
    ("tooltip.mymod.rod_effect.assist", "%s servo assist"),
    ("tooltip.mymod.rod_effect.rarity", "%s rare fish bias"),
    ("tooltip.mymod.rod_effect.night_rarity", "%s rare fish bias at night"),
    ("tooltip.mymod.rod_effect.sonar", "Sonar: names a fish before it bites"),
    ("tooltip.mymod.rod_effect.cursed", "Cursed catches are easier"),
    ("hud.mymod.fishing.unknown", "Something's on the line!"),
    ("hud.mymod.fishing.hint.tension", "Hold %s to reel"),
    ("hud.mymod.fishing.hint.strikes", "Press %s in the zone"),
    ("hud.mymod.fishing.hint.mash", "Tap %s fast"),
    ("hud.mymod.fishing.mash.under", "Tap! Tap!"),
    ("hud.mymod.fishing.mash.hold", "Keep it up!"),
    ("hud.mymod.fishing.outcome.caught", "Caught!"),
    ("hud.mymod.fishing.outcome.escaped", "It got away..."),
    ("hud.mymod.fishing.outcome.snapped", "Line snapped!"),
    ("hud.mymod.fishing.outcome.cancelled", "Lost it"),
    ("hud.mymod.fishing.perfect", "Perfect!"),
    ("hud.mymod.fishing.xp", "+%s xp"),
    ("hud.mymod.fishing.sonar", "Sonar: %s"),
    ("hud.mymod.fishing.empty_lava", "Nothing in this lava is biting... yet"),
    ("hud.mymod.fishing.game.tension", "tension meter"),
    ("hud.mymod.fishing.game.strikes", "timing strikes"),
    ("hud.mymod.fishing.game.mash", "mash meter"),
    ("commands.mymod.fishing.test.started", "Started a %s fight (%s, difficulty %s)"),
    ("commands.mymod.fishing.test.result", "Test fight: %s in %ss, perfect: %s"),
    ("commands.mymod.fishing.bad_behavior", "Unknown behavior %s: try steady, darting, sinker, floater or thrasher"),
    ("commands.mymod.fishing.species.header", "%s of %s species can bite here (rod tier %s, in %s):"),
    ("commands.mymod.fishing.species.none", "Nothing in the species files can bite here."),
    ("commands.mymod.fishing.species.line", " %s: %s%% (weight %s, difficulty %s, %s) [%s]"),
] + [("subtitles.mymod." + k, v) for k, v in SUBTITLES.items()])


def sounds_and_lang():
    merge_json(os.path.join(ASSETS, "sounds.json"), SOUNDS)
    merge_json(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ======================================================================================================= preview

FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
    "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Y": "101101010010010",
    "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111", "2": "110001010100111", "3": "110001010001110",
    "4": "101101111001001", "5": "111100110001110", "6": "011100111101111", "7": "111001010010010", "8": "111101111101111",
    "9": "111101111001110", "!": "010010010000010", ".": "000000000000010", "%": "101001010100101", "+": "000010111010000",
    "-": "000000111000000", ":": "000010000010000", "?": "110001010000010", "/": "001001010100100", "'": "010010000000000",
    ",": "000000000010100", "(": "010100100100010", ")": "010001001001010", " ": "000000000000000",
}


def text(img, x, y, s, colour, k=1, shadow=None):
    """The preview's tiny 3x5 font (upper case), k times size."""
    c = hexc(colour) if isinstance(colour, str) else colour
    for dx, dy, col in (((k, k, hexc(shadow)),) if shadow else ()) + ((0, 0, c),):
        cx = x
        for ch in s.upper():
            g = FONT.get(ch, FONT["?"])
            for i, bit in enumerate(g):
                if bit == "1":
                    img.fill(cx + (i % 3) * k + dx, y + (i // 3) * k + dy, cx + (i % 3 + 1) * k + dx, y + (i // 3 + 1) * k + dy, col)
            cx += 4 * k
    return len(s) * 4 * k


def text_width(s, k=1):
    return len(s) * 4 * k


LIGHTS = [[v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (0.2, 1.0, -0.7)],
          [v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (-0.2, 1.0, 0.7)]]


def lightness(n):
    return min(1.0, 0.4 + 0.6 * sum(max(0.0, sum(n[k] * l[k] for k in range(3))) for l in LIGHTS))


CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def render(faces, project, turn, w, h, background=None):
    """Rasterises quads (corners, uvs, sampler(u, v) -> colour, normal) with a depth buffer and vanilla-ish item
    lighting; back faces culled, transparent texels skipped."""
    img = background or Img(w, h)
    depth = [-1e9] * (w * h)
    for pts, uvs, sample, n in faces:
        shade = lightness(turn(n))
        sp = [project(p) for p in pts]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (sp[i] for i in tri)
            ta, tb, tc = (uvs[i] for i in tri)
            area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if area <= 1e-9:
                continue
            for py in range(max(0, int(min(a[1], b[1], c[1]))), min(h - 1, int(max(a[1], b[1], c[1])) + 1) + 1):
                for px in range(max(0, int(min(a[0], b[0], c[0]))), min(w - 1, int(max(a[0], b[0], c[0])) + 1) + 1):
                    qx, qy = px + 0.5, py + 0.5
                    l1 = ((b[0] - qx) * (c[1] - qy) - (b[1] - qy) * (c[0] - qx)) / area
                    l2 = ((c[0] - qx) * (a[1] - qy) - (c[1] - qy) * (a[0] - qx)) / area
                    l3 = 1 - l1 - l2
                    if min(l1, l2, l3) < -1e-6:
                        continue
                    z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                    if z <= depth[py * w + px]:
                        continue
                    col = sample(l1 * ta[0] + l2 * tb[0] + l3 * tc[0], l1 * ta[1] + l2 * tb[1] + l3 * tc[1])
                    if col[3] < 128:
                        continue
                    depth[py * w + px] = z
                    img.set(px, py, tuple(int(col[k] * shade) for k in range(3)) + (255,))
    return img


def flat(colour):
    return lambda u, v: colour


def sprite_faces(img):
    """A flat item, extruded the way Minecraft does: front and back, and a side wherever a texel's edge is open."""
    faces = []
    for ty in range(img.h):
        for tx in range(img.w):
            c = img.get(tx, ty)
            if c[3] < 128:
                continue
            a = (tx, 15 - ty, 7.5)
            b = (tx + 1, 16 - ty, 8.5)
            for face, step in (("south", None), ("north", None), ("east", (1, 0)), ("west", (-1, 0)), ("up", (0, -1)),
                               ("down", (0, 1))):
                if step and img.get(tx + step[0], ty + step[1])[3] >= 128:
                    continue
                faces.append((CORNERS[face](a, b), [(0, 0), (1, 0), (1, 1), (0, 1)], flat(c), NORMALS[face]))
    return faces


HANDHELD_ROD_FP = {"rotation": [0, 90, 25], "translation": [0, 1.6, 0.8], "scale": 0.68}


def first_person(w, h, fov=70.0):
    """Vanilla's resting right hand (offset 0.56, -0.52, -0.72 from the eye) and handheld_rod's first-person transform,
    seen through a perspective camera looking down -z."""
    t = HANDHELD_ROD_FP
    m = display_matrix(t["rotation"])
    s = t["scale"]
    f = (h / 2) / math.tan(math.radians(fov / 2))

    def project(p):
        q = apply(m, [s * (p[k] / 16 - 0.5) for k in range(3)])
        x, y, z = (q[k] + t["translation"][k] / 16 + (0.56, -0.52, -0.72)[k] for k in range(3))
        return (w / 2 + x / -z * f, h / 2 - y / -z * f, z)
    return project, lambda n: apply(m, n)


def sky(w, h, top="#6fa8dc", bottom="#cfe3f2", water="#2d5f8a", horizon=0.55):
    img = Img(w, h)
    for y in range(h):
        t = y / max(1, h - 1)
        if t < horizon:
            a, b, u = hexc(top), hexc(bottom), t / horizon
        else:
            a, b, u = hexc("#3c79a8"), hexc(water), (t - horizon) / (1 - horizon)
        c = tuple(int(a[k] + (b[k] - a[k]) * u) for k in range(3)) + (255,)
        img.fill(0, y, w, y + 1, c)
    return img


def iso(w, h, scale, yaw=-35.0, pitch=28.0):
    rx = lambda a: [[1, 0, 0], [0, math.cos(math.radians(a)), -math.sin(math.radians(a))], [0, math.sin(math.radians(a)), math.cos(math.radians(a))]]
    ry = lambda a: [[math.cos(math.radians(a)), 0, math.sin(math.radians(a))], [0, 1, 0], [-math.sin(math.radians(a)), 0, math.cos(math.radians(a))]]
    from gunkit import mul
    view = mul(rx(pitch), ry(yaw))

    def project(p):
        v = apply(view, [p[0] - 8, p[1] - 9, p[2] - 8])
        return (w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2])
    return project, lambda n: apply(view, n)


def bench_faces(elements, textures):
    faces = []
    for e in elements:
        for face, f in e["faces"].items():
            tex = textures[f["texture"].lstrip("#")]
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]

            def sample(u, v, tex=tex):
                return tex.get(min(tex.w - 1, max(0, int(u / 16 * tex.w))), min(tex.h - 1, max(0, int(v / 16 * tex.h))))
            faces.append((CORNERS[face](e["from"], e["to"]), uvs, sample, NORMALS[face]))
    return faces


# --- HUD mockups: the same layout FishingHud draws, at GUI scale, scaled up.

PANEL_W = 172


def hud_panel(body_h, title, difficulty, draw_body, hint, known=None, result=None):
    h = 22 + body_h + 15
    img = Img(PANEL_W + 8, h + 8)
    x0, y0 = 4, 4
    edge, fill = hexc("#9db4c8", 90), hexc("#0e151c", 210)
    img.fill(x0 + 2, y0, x0 + PANEL_W - 2, y0 + h, edge)
    img.fill(x0, y0 + 2, x0 + PANEL_W, y0 + h - 2, edge)
    img.fill(x0 + 1, y0 + 1, x0 + PANEL_W - 1, y0 + h - 1, edge)
    img.fill(x0 + 2, y0 + 1, x0 + PANEL_W - 2, y0 + h - 1, fill)
    img.fill(x0 + 1, y0 + 2, x0 + PANEL_W - 1, y0 + h - 2, fill)
    img.fill(x0 + 2, y0 + 1, x0 + PANEL_W - 2, y0 + 2, hexc("#ffffff", 34))
    # Badge, name, pips.
    img.fill(x0 + 7, y0 + 5, x0 + 19, y0 + 19, hexc("#22313f"))
    img.fill(x0 + 6, y0 + 6, x0 + 20, y0 + 18, hexc("#22313f"))
    if known is not None:
        img.paste(known, x0 + 7, y0 + 6)
    else:
        text(img, x0 + 12, y0 + 9, "?", "#e9eff5")
    text(img, x0 + 22, y0 + 9, title, "#e9eff5" if known is not None else "#8c9baa")
    pip = ["#5bd46a", "#a6d45b", "#e8c547", "#ee8e43", "#e5534b"][difficulty - 1]
    for i in range(5):
        px = x0 + PANEL_W - 6 - (5 - i) * 7 + 2
        img.fill(px, y0 + 9, px + 5, y0 + 13, hexc(pip) if i < difficulty else hexc("#ffffff", 64))
    draw_body(img, x0 + 8, y0 + 22, PANEL_W - 16)
    if hint:
        text(img, x0 + PANEL_W // 2 - text_width(hint) // 2, y0 + 22 + body_h + 5, hint, "#8c9baa")
    if result:
        word, colour = result
        img.fill(x0 + 1, y0 + 19, x0 + PANEL_W - 1, y0 + h - 1, hexc("#0e151c", 210))
        img.fill(x0 + 1, y0 + 1, x0 + PANEL_W - 1, y0 + h - 1, hexc(colour, 60))
        text(img, x0 + PANEL_W // 2 - text_width(word, 2) // 2, y0 + 22 + (h - 22) // 2 - 8, word, colour, 2)
        text(img, x0 + PANEL_W // 2 - text_width("PERFECT!  +17 XP") // 2, y0 + 22 + (h - 22) // 2 + 5, "PERFECT!  +17 XP", "#ffe27a")
    return img


def track(img, x0, y0, x1, y1):
    img.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, hexc("#2c3b49"))
    img.fill(x0, y0, x1, y1, hexc("#16202a"))


def tension_body(needle=0.42, half=0.124, progress=0.62, strain=0.0):
    def draw(img, x, y, w):
        track(img, x, y + 3, x + w, y + 12)
        red = round(w * 0.08)
        img.fill(x, y + 3, x + red, y + 12, hexc("#c2453f"))
        img.fill(x + w - red, y + 3, x + w, y + 12, hexc("#c2453f"))
        g0, g1 = round(x + w * (0.5 - half)), round(x + w * (0.5 + half))
        img.fill(g0, y + 3, g1, y + 12, hexc("#3fb56f"))
        img.fill(g0 + 1, y + 4, g1 - 1, y + 6, hexc("#7fe0a3", 140))
        nx = round(x + w * needle)
        img.fill(nx - 1, y, nx + 1, y + 15, hexc("#f4f7fa"))
        img.fill(nx - 2, y - 1, nx + 2, y + 1, hexc("#f4f7fa"))
        track(img, x, y + 17, x + w, y + 20)
        img.fill(x, y + 17, x + round(w * progress), y + 20, hexc("#6ccbff"))
    return draw


def strike_body(marker=0.31, zone=0.68, width=0.08, hits=2, need=5, misses=1, allowed=3):
    def draw(img, x, y, w):
        track(img, x, y + 3, x + w, y + 12)
        img.fill(round(x + w * (zone - width)), y + 3, round(x + w * (zone + width)), y + 12, hexc("#3fb56f"))
        img.fill(round(x + w * (zone - width)) + 1, y + 4, round(x + w * (zone + width)) - 1, y + 6, hexc("#7fe0a3", 128))
        mx = round(x + w * marker)
        img.fill(mx - 1, y, mx + 1, y + 15, hexc("#f4f7fa"))
        img.fill(mx - 2, y + 14, mx + 2, y + 16, hexc("#f4f7fa"))
        for i in range(need):
            img.fill(x + i * 9, y + 18, x + i * 9 + 7, y + 22, hexc("#6ccbff" if i < hits else "#2c3b49"))
        for i in range(allowed):
            px = x + w - 7 - i * 9
            img.fill(px, y + 18, px + 7, y + 22, hexc("#c2453f" if i < misses else "#2c3b49"))
    return draw


def mash_body(level=0.58, line=0.43, left=0.4, under=False):
    def draw(img, x, y, w):
        top, bottom = y + 1, y + 35
        height = bottom - top
        track(img, x, top, x + 12, bottom)
        cap = top + round(height * 0.06)
        img.fill(x, top, x + 12, cap, hexc("#c2453f"))
        fill_top = round(top + height * (1 - level))
        img.fill(x + 1, fill_top, x + 11, bottom, hexc("#e8b74a" if under else "#3fb56f"))
        ly = round(top + height * (1 - line))
        img.fill(x - 2, ly, x + 14, ly + 1, hexc("#f4f7fa"))
        img.fill(x + 14, ly, x + 20, ly + 1, hexc("#f4f7fa", 90))
        text(img, x + 24, y + 7, "TAP! TAP!" if under else "KEEP IT UP!", "#ff7a70" if under else "#d8e1ea")
        track(img, x + 24, y + 22, x + w, y + 25)
        img.fill(x + 24, y + 22, x + 24 + round((w - 24) * (1 - left)), y + 25, hexc("#6ccbff"))
    return draw


def preview(path, rods, parts, sprites, gui, elements):
    W, H = 1528, 1330
    sheet = Img(W, H, hexc("#1b2026"))
    text(sheet, 16, 14, "FISHING SYSTEM PREVIEW", "#e9eff5", 3)

    # Rods: rest, cast and bent, beside vanilla's rod.
    text(sheet, 16, 52, "RODS: REST / CAST / FIGHTING (BENT), VANILLA ROD FIRST", "#8c9baa", 2)
    names = ["vanilla"] + ROD_NAMES
    for i, name in enumerate(names):
        x = 16 + i * 100
        label = name.replace("_rod", "").replace("_", " ")
        text(sheet, x + 48 - text_width(label) // 2, 76, label, "#c8d2dc")
        for j, suffix in enumerate(("", "_cast", "_bent")):
            cell = checker(96, 96)
            if name == "vanilla":
                if suffix == "_bent":
                    continue
                img = vanilla("item/fishing_rod" + suffix)
            else:
                img = rods[name + suffix]
            cell.paste(img, 0, 0, 6)
            sheet.paste(cell, x, 88 + j * 100)

    # Parts.
    text(sheet, 730, 52, "PARTS: LINE / HOOK / REEL / LURE", "#8c9baa", 2)
    for i, (name, img) in enumerate(parts.items()):
        cx, cy = 730 + (i % 6) * 110, 80 + (i // 6) * 112
        cell = checker(80, 80)
        cell.paste(img, 0, 0, 5)
        sheet.paste(cell, cx, cy)
        label = name.replace("_", " ")
        text(sheet, cx + 40 - text_width(label) // 2, cy + 86, label, "#c8d2dc")
    # The empty-slot icons, on a slot's grey.
    text(sheet, 730, 316, "EMPTY SLOT ICONS", "#8c9baa", 2)
    for i, (name, img) in enumerate(sprites.items()):
        cell = Img(18, 18, hexc("#8b8b8b"))
        cell.paste(img, 1, 1)
        sheet.paste(cell, 730 + i * 70, 340, 3)

    # First person.
    text(sheet, 16, 400, "IN HAND (FIRST PERSON, LOWER RIGHT OF SCREEN, 2X): RESTING / FIGHTING", "#8c9baa", 2)
    fw, fh = 224, 126
    for i, name in enumerate(ROD_NAMES):
        for j, suffix in enumerate(("", "_bent")):
            # Rendered at twice the size and cropped to the lower right, where the rod is held.
            project, turn = first_person(fw * 2, fh * 2)
            full = render(sprite_faces(rods[name + suffix]), project, turn, fw * 2, fh * 2, background=sky(fw * 2, fh * 2))
            img = Img(fw, fh)
            for y in range(fh):
                for x in range(fw):
                    img.set(x, y, full.get(x + fw - 20, y + fh))
            sheet.paste(img, 16 + i * 230, 424 + j * 132)

    # The bench, and its menu.
    text(sheet, 16, 700, "TACKLE BENCH", "#8c9baa", 2)
    textures = {"top": vanilla("block/spruce_planks"), "leg": vanilla("block/dark_oak_planks"), "kit": KIT}
    for i, yaw in enumerate((-35.0, 145.0)):
        project, turn = iso(300, 300, 13.0, yaw=yaw)
        img = render(bench_faces(elements, textures), project, turn, 300, 300, background=checker(300, 300))
        sheet.paste(img, 16 + i * 310, 724)
    menu = Img(176, 166)
    for y in range(166):
        for x in range(176):
            menu.set(x, y, gui.get(x, y))
    rx, ry = ROD_SLOT
    menu.paste(rods["steel_rod"], rx, ry)
    for (x, y), part in zip(PART_SLOTS, ("rubber_line", "tungsten_hook", "geared_reel", "fish_finder")):
        menu.paste(parts[part], x, y)
    text(menu, 8, 6, "TACKLE BENCH", "#404040")
    text(menu, 8, 73, "INVENTORY", "#404040")
    stats = [("TIER", "2", "#b9c6d2"), ("ZONE", "+10%", "#7fd0ff"), ("FISH", "-10%", "#7fd0ff"), ("HOLD", "+40%", "#7fd0ff"),
             ("LINE", "+45%", "#7fd0ff"), ("REEL", "+30%", "#7fd0ff"), ("BITE", "0.0S", "#6f7e8c"), ("RARE", "0%", "#6f7e8c")]
    for i, (k, v, c) in enumerate(stats):
        text(menu, 100, 19 + i * 7, k, "#b9c6d2")
        text(menu, 164 - text_width(v), 19 + i * 7, v, c)
    text(menu, 40 - text_width("SONAR") // 2, 65, "SONAR", "#ffc66b")
    sheet.paste(menu, 650, 724, 2)
    text(sheet, 650, 1062, "MENU: ROD SLOT, LINE/HOOK/REEL/LURE, LIVE READOUT", "#8c9baa")

    # The three minigames, and a result.
    text(sheet, 16, 1090, "MINIGAME HUD (GUI SCALE X2): TENSION / STRIKES / MASH / RESULT", "#8c9baa", 2)
    cod = vanilla("item/cod")
    panels = [
        hud_panel(22, "SOMETHING'S ON THE LINE!", 2, tension_body(), "HOLD RIGHT BUTTON / LEFT ALT TO REEL"),
        hud_panel(24, "TROPICAL FISH", 3, strike_body(), "PRESS RIGHT BUTTON / LEFT ALT IN THE ZONE", known=vanilla("item/tropical_fish")),
        hud_panel(36, "SOMETHING'S ON THE LINE!", 4, mash_body(under=True), "TAP RIGHT BUTTON / LEFT ALT FAST"),
        hud_panel(22, "COD", 1, tension_body(needle=0.5, progress=1.0), None, known=cod, result=("CAUGHT!", "#5bd46a")),
    ]
    for i, panel in enumerate(panels):
        backdrop = sky(372, 200)
        backdrop.fill(184, 20, 188, 21, hexc("#ffffff"))
        backdrop.fill(185, 19, 187, 23, hexc("#ffffff"))
        big = scaled(panel, 2)
        backdrop.paste(big, (372 - big.w) // 2, 34)
        sheet.paste(backdrop, 16 + i * 376, 1114)
    sheet.save(path)
    print("preview", path)


# ======================================================================================================= main


def main():
    rods = rod_textures()
    rod_models()
    parts = part_textures()
    sprites = slot_sprites(parts, rods)
    gui = menu_background()
    kit_texture()
    elements = bench_model()
    data()
    sounds_and_lang()
    print("fishing: %d rod textures, %d parts, bench with %d elements" % (len(rods), len(parts), len(elements)))
    if PREVIEW:
        preview(PREVIEW, rods, parts, sprites, gui, elements)


if __name__ == "__main__":
    main()
