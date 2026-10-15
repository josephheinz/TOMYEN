#!/usr/bin/env python3
"""The Fish Mount and the baits (see com.jheinz.mymod.fishing: FishMountBlock, FishMountRenderer, BaitItem).

Run from anywhere: python3 tools/gen_fish_mount.py [--preview out.png]

Fish Mount. A trophy plaque in three woods (oak, spruce, dark oak), modelled for a north-facing mount: the plaque
against the block's south side, its face toward -z (FishMountRenderer lays the fish and the plate's engraving out in
the same frame). Built front to back from rounded layers of horizontal strips, so its outline steps in at the corners:
  * the backboard (1.5 px thick, the block's full width, an oval of stepped corners), its rim catching the light top left and in shadow bottom right;
  * a raised bevel panel (0.5 px) with a routed groove running round inside its edge;
  * the field the fish hangs over (0.5 px), and below it a brass name plate (0.5 px) for the engraving.
Each wood's texture is painted from that vanilla plank's own colours (sorted into a six-step ramp), in flat, deliberate
bands: a highlight and shadow rim, the groove, a couple of long grain lines; no noise. One texel is one model pixel.
The model is checked for z-fighting (tools/zfight.py): faces in one plane only ever meet edge to edge.

Baits. Twelve 16x16 item sprites in vanilla's style (dark outline, lit from the top left, a few clean ramps): the
worms, leeches and the grub are drawn along a centre line with banded segments; dough balls and seed mash are round
lumps lit in three flat steps (blobs); the chum bucket is vanilla's water bucket with chum in place of the water; the
rest are hand-drawn rows. Also the Tackle Bench's empty bait-slot icon (an earthworm's
outline, as vanilla draws its empty-slot icons).

Also written: blockstates, block and item models, item definitions, loot tables, the axe-mineable tag, the bait and
fish tags (raw fish for cut bait; predator, night and mutant fish that baits draw in), recipes with their recipe-book
unlocks, and the lang for everything in the fish-weight, trophy, bait and mount features.

The preview (--preview) shows the oak and dark oak plaques from the front and at three quarters, three dark oak mounts
with a pike at different weights side by side (an ordinary catch at the species' typical 8 lb, a 25 lb trophy and a
50 lb near-record: the renderer's sizing, with the pike's own entity model), and every bait sprite at 1x and 8x."""
import json, math, os, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png  # noqa: E402
from zfight import check as zfight_check  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None

WOODS = OrderedDict([("oak", "Oak"), ("spruce", "Spruce"), ("dark_oak", "Dark Oak")])
CLEAR = (0, 0, 0, 0)


# ======================================================================================================= utilities


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, k):
    return tuple(max(0, min(255, int(round(c[i] * k)))) for i in range(3)) + (255,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tag_add(path, entries):
    """Adds entries to a tag file, keeping whatever else is in it."""
    obj = OrderedDict([("replace", False), ("values", [])])
    if os.path.exists(path):
        with open(path) as fh:
            obj = json.load(fh, object_pairs_hook=OrderedDict)
    for entry in entries:
        if entry not in obj["values"]:
            obj["values"].append(entry)
    write(path, obj)


def merge_lang(entries):
    with open(LANG) as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(LANG, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


class Img:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [fill] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def fill(self, x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.px[y * self.w + x] = c

    def paste(self, img, ox, oy, k=1):
        for y in range(img.h * k):
            for x in range(img.w * k):
                c = img.get(x // k, y // k)
                if c[3]:
                    self.set(ox + x, oy + y, c[:3] + (255,))

    def save(self, path):
        png(path, self.w, self.h, self.px)


def client_jar():
    with open(os.path.join(REPO, "gradle.properties")) as f:
        version = next(line.split("=", 1)[1].strip() for line in f if line.startswith("minecraft_version="))
    path = os.path.expanduser(os.path.join("~", ".gradle", "caches", "fabric-loom", version, "minecraft-client.jar"))
    if not os.path.isfile(path):
        sys.exit(f"no Minecraft {version} client jar at {path}; run ./gradlew build first")
    return path


def read_png(data):
    from gen_fishing_rods import read_png as rp      # the non-interlaced PNG reader the fishing tools share
    img = rp(data)
    out = Img(img.w, img.h)
    out.px = list(img.px)
    return out


JAR = zipfile.ZipFile(client_jar())


def vanilla(path):
    return read_png(JAR.read("assets/minecraft/textures/" + path + ".png"))


# ======================================================================================================= the plaque
#
# Model pixels, north-facing: x across (the viewer's left is +x), y up, the wall at z = 16. Each layer is a rectangle
# whose top and bottom rows step in by `insets` (outermost row first) to round its corners.

LAYERS = OrderedDict([
    # name: (x0, x1, y0, y1, z0, z1, insets, atlas origin)
    ("board", (0, 16, 1, 15, 14.5, 16.0, [4, 2, 1], (0, 0))),
    ("bevel", (1, 15, 2, 14, 14.0, 14.5, [3, 1], (16, 0))),
    ("field", (3, 13, 7, 12, 13.5, 14.0, [1], (0, 14))),
])
PLATE = (4, 12, 3, 6, 13.5, 14.0)       # the brass name plate, on the bevel panel under the field
PLATE_ATLAS = (12, 14)                  # its front, 8x3 texels
# Side strips: wood (vertical 2x14 at (30, 0), horizontal 16x2 at (0, 20)) and brass (vertical 1x3, horizontal 8x1).
WOOD_V, WOOD_H = (30, 0), (0, 20)
BRASS_V, BRASS_H = (24, 14), (12, 17)
ATLAS = 32                              # texels; uv units are half a texel's... 16 uv units span the 32 texels


def strips(x0, x1, y0, y1, insets):
    """The layer's outline as horizontal strips: [(sx0, sx1, sy0, sy1)], top and bottom rows stepped in."""
    rows = []
    n = len(insets)
    for y in range(y0, y1):
        k = min(y1 - 1 - y, y - y0)                   # rows from the nearer edge
        inset = insets[k] if k < n else 0
        rows.append((x0 + inset, x1 - inset, y, y + 1))
    merged = []
    for r in rows:
        if merged and merged[-1][0] == r[0] and merged[-1][1] == r[1] and merged[-1][3] == r[2]:
            merged[-1] = (r[0], r[1], merged[-1][2], r[3])
        else:
            merged.append(r)
    return merged


def mask(x0, x1, y0, y1, insets):
    """The layer's front as the viewer sees it: {(column, row)} with column 0 at the viewer's left (high x)."""
    cells = set()
    for sx0, sx1, sy0, sy1 in strips(x0, x1, y0, y1, insets):
        for x in range(sx0, sx1):
            for y in range(sy0, sy1):
                cells.add((x1 - 1 - x, y1 - 1 - y))
    return cells


def uv(t0, t1, t2, t3):
    """Texel rectangle to block-model uv (16 units across the 32-texel atlas)."""
    return [round(v * 16.0 / ATLAS, 4) for v in (t0, t1, t2, t3)]


def element(name, frm, to, faces):
    out = OrderedDict([("name", name), ("from", list(frm)), ("to", list(to)), ("faces", OrderedDict())])
    for face, (coords, cull) in faces.items():
        f = OrderedDict([("uv", coords), ("texture", "#plaque")])
        if cull:
            f["cullface"] = cull
        out["faces"][face] = f
    return out


def plaque_elements():
    """Every cube of the plaque. Strips of one layer share front and back planes but only ever meet edge to edge."""
    out = []
    for name, (x0, x1, y0, y1, z0, z1, insets, (ox, oy)) in LAYERS.items():
        d = z1 - z0
        rows = strips(x0, x1, y0, y1, insets)
        for i, (sx0, sx1, sy0, sy1) in enumerate(rows):
            faces = OrderedDict()
            faces["north"] = (uv(ox + x1 - sx1, oy + y1 - sy1, ox + x1 - sx0, oy + y1 - sy0), None)
            if name == "board":
                # The back, against the wall (culled whenever the wall is there).
                faces["south"] = (uv(ox + sx0 - x0, oy + y1 - sy1, ox + sx1 - x0, oy + y1 - sy0), "south")
            side = (uv(WOOD_V[0], WOOD_V[1] + y1 - sy1, WOOD_V[0] + d, WOOD_V[1] + y1 - sy0), None)
            faces["east"] = side
            faces["west"] = side
            # Tops and bottoms only where they're open (wider than the strip above or below).
            above = rows[i + 1] if i + 1 < len(rows) else None
            below = rows[i - 1] if i > 0 else None
            flat = (uv(WOOD_H[0] + sx0 - x0, WOOD_H[1], WOOD_H[0] + sx1 - x0, WOOD_H[1] + d), None)
            if above is None or above[0] > sx0 or above[1] < sx1:
                faces["up"] = flat
            if below is None or below[0] > sx0 or below[1] < sx1:
                faces["down"] = flat
            out.append(element(f"{name}_{i}", (sx0, sy0, z0), (sx1, sy1, z1), faces))
    x0, x1, y0, y1, z0, z1 = PLATE
    d = z1 - z0
    ox, oy = PLATE_ATLAS
    side = (uv(BRASS_V[0], BRASS_V[1], BRASS_V[0] + d, BRASS_V[1] + (y1 - y0)), None)
    flat = (uv(BRASS_H[0], BRASS_H[1], BRASS_H[0] + (x1 - x0), BRASS_H[1] + d), None)
    out.append(element("plate", (x0, y0, z0), (x1, y1, z1), OrderedDict([
        ("north", (uv(ox, oy, ox + x1 - x0, oy + y1 - y0), None)), ("east", side), ("west", side), ("up", flat), ("down", flat)])))
    return out


# --- Textures

BRASS = [hexc("5c3d12"), hexc("8a5f1f"), hexc("b8862e"), hexc("d9ab45"), hexc("f0cf6a"), hexc("fff0a8")]


def wood_ramp(wood):
    """Six tones from the vanilla plank's own colours, darkest first: their luminance quantiles, averaged locally."""
    px = sorted((c for c in vanilla(f"block/{wood}_planks").px if c[3]), key=lum)
    out = []
    for q in (0.04, 0.2, 0.4, 0.6, 0.8, 0.97):
        i = int(q * (len(px) - 1))
        window = px[max(0, i - 6):i + 7]
        out.append(tuple(int(round(sum(c[k] for c in window) / len(window))) for k in range(3)) + (255,))
    return out


def edges(cells, c, r):
    """Which sides of a cell are open: (top, left, bottom, right)."""
    return ((c, r - 1) not in cells, (c - 1, r) not in cells, (c, r + 1) not in cells, (c + 1, r) not in cells)


def paint_plaque(wood):
    r = wood_ramp(wood)
    img = Img(ATLAS, ATLAS)
    # Backboard: only its rim shows round the bevel panel: lit top and left, shadowed bottom and right.
    x0, x1, y0, y1, _, _, insets, (ox, oy) = LAYERS["board"]
    cells = mask(x0, x1, y0, y1, insets)
    for c, row in cells:
        top, left, bottom, right = edges(cells, c, row)
        col = r[2]
        if top or left:
            col = r[4]
        if bottom or right:
            col = r[1] if not (top or left) else r[3]
        img.set(ox + c, oy + row, col)
    # Bevel panel: a lit chamfer rim, then the routed groove one pixel in, then the flat. One long grain line each
    # side of the plate keeps the flat from reading as plastic.
    x0, x1, y0, y1, _, _, insets, (ox, oy) = LAYERS["bevel"]
    cells = mask(x0, x1, y0, y1, insets)
    ring = {cell for cell in cells if any(edges(cells, *cell))}
    inner = cells - ring
    groove = {cell for cell in inner if any(edges(inner, *cell))}
    for c, row in cells:
        if (c, row) in ring:
            top, left, bottom, right = edges(cells, c, row)
            col = r[5] if (top or left) and not (bottom or right) else r[1] if (bottom or right) and not (top or left) else r[3]
        elif (c, row) in groove:
            # The groove's far wall catches light, its near wall is in shadow: lighter along its bottom and right.
            top, left, bottom, right = edges(inner, c, row)
            col = r[2] if (bottom or right) and not (top or left) else r[0]
        else:
            col = r[3]
        img.set(ox + c, oy + row, col)
    # The field: a soft top light, darker toward the bottom, its rim lit and shadowed like the board's.
    x0, x1, y0, y1, _, _, insets, (ox, oy) = LAYERS["field"]
    cells = mask(x0, x1, y0, y1, insets)
    for c, row in cells:
        top, left, bottom, right = edges(cells, c, row)
        col = r[4] if row < 2 else r[3]
        if top or left:
            col = r[5]
        elif bottom or right:
            col = r[2]
        img.set(ox + c, oy + row, col)
    for c in (2, 3, 4, 5, 6):
        img.set(ox + c, oy + 3, r[2])
    # Wood side strips: end grain, a light band along the front edge.
    img.fill(WOOD_V[0], WOOD_V[1], WOOD_V[0] + 2, WOOD_V[1] + 14, r[2])
    img.fill(WOOD_V[0], WOOD_V[1], WOOD_V[0] + 1, WOOD_V[1] + 14, r[3])
    img.fill(WOOD_H[0], WOOD_H[1], WOOD_H[0] + 16, WOOD_H[1] + 2, r[2])
    img.fill(WOOD_H[0], WOOD_H[1], WOOD_H[0] + 16, WOOD_H[1] + 1, r[3])
    # The brass plate: a bright top bevel, a dark bottom one, two screw heads at its ends.
    px, py = PLATE_ATLAS
    img.fill(px, py, px + 8, py + 3, BRASS[3])
    img.fill(px, py, px + 8, py + 1, BRASS[4])
    img.fill(px, py + 2, px + 8, py + 3, BRASS[2])
    img.set(px, py, BRASS[5])
    img.set(px + 7, py + 2, BRASS[1])
    img.set(px, py + 1, BRASS[4])
    img.set(px + 7, py + 1, BRASS[2])
    for sx in (px + 1, px + 6):
        img.set(sx, py + 1, BRASS[1])
    img.fill(BRASS_V[0], BRASS_V[1], BRASS_V[0] + 1, BRASS_V[1] + 3, BRASS[2])
    img.fill(BRASS_H[0], BRASS_H[1], BRASS_H[0] + 8, BRASS_H[1] + 1, BRASS[3])
    return img


def plaque_model(wood):
    elements = plaque_elements()
    clashes = zfight_check(elements)
    if clashes:
        sys.exit("fish mount z-fighting: " + repr(clashes))
    tex = paint_plaque(wood)
    tex.save(os.path.join(ASSETS, "textures", "block", f"{wood}_fish_mount.png"))
    name = f"{wood}_fish_mount"
    write(os.path.join(ASSETS, "models", "block", name + ".json"), OrderedDict([
        ("parent", "minecraft:block/block"),
        ("textures", OrderedDict([("particle", f"minecraft:block/{wood}_planks"), ("plaque", f"mymod:block/{name}")])),
        # In the inventory it's seen face on, turned a little to show its depth; in an item frame, flat.
        ("display", OrderedDict([
            ("gui", OrderedDict([("rotation", [10, 160, 0]), ("translation", [0, 0, 0]), ("scale", [0.8, 0.8, 0.8])])),
            ("fixed", OrderedDict([("rotation", [0, 180, 0]), ("translation", [0, 0, -6]), ("scale", [0.7, 0.7, 0.7])])),
            ("ground", OrderedDict([("rotation", [0, 0, 0]), ("translation", [0, 3, 0]), ("scale", [0.4, 0.4, 0.4])]))])),
        ("elements", elements)]))
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": OrderedDict(
        (f"facing={facing}", OrderedDict([("model", f"mymod:block/{name}")] + ([("y", y)] if y else [])))
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)))})
    write(os.path.join(ASSETS, "items", name + ".json"),
          {"model": OrderedDict([("type", "minecraft:model"), ("model", f"mymod:block/{name}")])})
    write(os.path.join(DATA, "mymod", "loot_table", "blocks", name + ".json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("rolls", 1), ("entries", [{"type": "minecraft:item", "name": "mymod:" + name}]),
                                ("conditions", [{"condition": "minecraft:survives_explosion"}])])]),
        ("random_sequence", "mymod:blocks/" + name)]))
    return elements, tex


# ======================================================================================================= baits


def outline_sprite(fill, light_dir=(-1, -1)):
    """Vanilla's item outline round {(x, y): colour}: a one-pixel rim, darker on the shadowed bottom right."""
    img = Img(16, 16)
    for (x, y), c in fill.items():
        img.set(x, y, c)
    return img


def worm(points, r, ramp, band, rim, tip=None, tip_ramp=None, band_every=3):
    """A worm along a centre line (points in sprite pixels): each pixel within r of the line is body, shaded by which
    side of the line it's on (lit from the top left), banded every few pixels of length, then outlined."""
    segs = []
    total = 0.0
    for a, b in zip(points, points[1:]):
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        segs.append((a, b, total, L))
        total += L
    body = {}
    for y in range(16):
        for x in range(16):
            q = (x + 0.5, y + 0.5)
            best = None
            for a, b, t0, L in segs:
                dx, dy = b[0] - a[0], b[1] - a[1]
                s = max(0.0, min(1.0, ((q[0] - a[0]) * dx + (q[1] - a[1]) * dy) / (L * L)))
                px, py = a[0] + dx * s, a[1] + dy * s
                d = math.hypot(q[0] - px, q[1] - py)
                if best is None or d < best[0]:
                    # Which side: the line's normal toward the light (top left) is the lit side.
                    nx, ny = -dy / L, dx / L
                    if nx + ny > 0:
                        nx, ny = -nx, -ny
                    side = (q[0] - px) * nx + (q[1] - py) * ny
                    best = (d, t0 + s * L, side)
            if best and best[0] <= r:
                d, t, side = best
                k = 2 if side > 0.35 else 0 if side < -0.35 else 1
                ramp_ = tip_ramp if tip and t > total - tip else ramp
                col = ramp_[k]
                if band and int(t) % band_every == 0 and 1.5 < t < total - 1.5:
                    col = band
                body[(x, y)] = col
    img = Img(16, 16)
    for (x, y), c in body.items():
        img.set(x, y, c)
    for y in range(16):
        for x in range(16):
            if (x, y) in body:
                continue
            n = [(x + dx, y + dy) in body for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if any(n):
                # Rim pixels below or right of the body are in shadow.
                img.set(x, y, rim[1] if ((x - 1, y) in body or (x, y - 1) in body) else rim[0])
    return img


def rows_sprite(rows, palette):
    img = Img(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.set(x, y, hexc(palette[ch]))
    return img


def earthworms():
    """Two pink earthworms, one looped over the other, ringed with darker segments."""
    pink = [hexc("b85d66"), hexc("d77e83"), hexc("f0a9a8")]
    rim = (hexc("5a2430"), hexc("3e1820"))
    a = worm([(2, 12.5), (4.5, 10.5), (7.5, 11.5), (10.5, 10), (12, 7), (13.5, 4), (12, 2)], 1.05, pink, hexc("a24e58"), rim)
    b = worm([(2, 7.5), (3.5, 4.5), (6.5, 3), (9, 4.5)], 1.0, pink, hexc("a24e58"), rim)
    out = Img(16, 16)
    out.paste(b, 0, 0)
    out.paste(a, 0, 0)
    return out


def glowworm():
    """An earthworm fed on glow berries: pale and greenish, its tail end glowing berry-gold."""
    pale = [hexc("8fa860"), hexc("b5cc7a"), hexc("dfeba6")]
    gold = [hexc("e09a2c"), hexc("f5c64a"), hexc("fff0a0")]
    return worm([(2.5, 12.5), (5, 10), (8.5, 11), (11, 8.5), (12, 5), (10.5, 2.5)], 1.3, pale, hexc("7a9450"),
                (hexc("3e4a22"), hexc("2a3416")), tip=3.5, tip_ramp=gold)


def magma_grub():
    """A fat grub out of magma cream, curled in a C: dark crusted plates over a glowing orange body, a black head."""
    body = [hexc("c24a1c"), hexc("ee7a2a"), hexc("ffb84a")]
    arc = [(8 + 4.2 * math.cos(math.radians(a)), 8.3 - 4.2 * math.sin(math.radians(a))) for a in range(60, 330, 30)]
    img = worm(arc, 2.0, body, hexc("6a2410"), (hexc("3a1408"), hexc("260c04")), band_every=2)
    hx, hy = arc[0]
    for dx, dy, c in ((0, 0, "2a1208"), (-1, 0, "3a1a10"), (0, 1, "3a1a10"), (-1, 1, "1a0a04"), (1, 0, "3a1a10"), (0, -1, "4a2414")):
        img.set(int(hx) + dx, int(hy) + dy, hexc(c))
    return img


def cut_bait():
    """A wedge of raw fish: pink flesh with pale fat lines, silver skin along its back."""
    return rows_sprite([
        "................",
        "................",
        "..........oo....",
        "........oossO...",
        "......oossssO...",
        "....oosSllfllO..",
        "...osSllfllfllO.",
        "..oslflllfllmmO.",
        "..olllfllflmmmO.",
        "..oflllflmmmmO..",
        "...olflmmmmmO...",
        "...omlmmmmdO....",
        "....ommmdOO.....",
        ".....oddO.......",
        "......OO........",
        "................",
    ], dict(o="5a2a26", O="3e1a18", s="c8d0d6", S="8e9aa4", l="f2a08a", f="ffd7c4", m="d97862", d="b0553f"))


def irradiated_bait():
    """Cut bait gone sickly green, with glowing uranium flecks set into it in a deliberate scatter."""
    return rows_sprite([
        "................",
        "................",
        "..........oo....",
        "........oossO...",
        "......oossssO...",
        "....oosSllgllO..",
        "...osSlGlllgllO.",
        "..oslllllglmmmO.",
        "..olGllllllmGmO.",
        "..olllglmmmmmO..",
        "...ollmmGmmmO...",
        "...omlmmmmdO....",
        "....omGmdOO.....",
        ".....oddO.......",
        "......OO........",
        "................",
    ], dict(o="25381a", O="16240f", s="a8c49a", S="6f8a64", l="9cc46a", m="6e9a48", d="4a6e30", g="c8f07a",
            G="7aff3a"))


def star_bait():
    """Cut bait dusted with ground star crystal: violet flesh and a cyan-white glint."""
    return rows_sprite([
        "................",
        "...........w....",
        "..........cwc...",
        "........oowO....",
        "......oossssO...",
        "....oosSllfllO..",
        "...osSllfllfllO.",
        "..oslflllfllmmO.",
        "..olllfllflmmmO.",
        "..oflllflmmmmO..",
        "...olflmmmmmO.c.",
        "...omlmmmmdO.cwc",
        "....ommmdOO...c.",
        ".....oddO.......",
        "......OO........",
        "................",
    ], dict(o="2a1758", O="1a0e3a", s="b8c8f0", S="7d8cc8", l="8f6ad8", f="c8b4ff", m="6a4cc0", d="4c34a8", c="63d0ee",
            w="f4fbff"))


def live_minnow():
    """A little minnow, nose down to the lower left like vanilla's fish: olive back, silver flank with a dark stripe."""
    return rows_sprite([
        "................",
        "................",
        "..........ooo...",
        ".........ottto..",
        "..........ottto.",
        ".........ooottO.",
        "........obbbsO..",
        ".......obbbsdO..",
        "......obbbsdlO..",
        ".....obbbsdllO..",
        "....obbbsdllO...",
        "...oebbsdllmO...",
        "..oeebsdllmO....",
        "..osbsdlmmO.....",
        "...oOOOOOO......",
        "................",
    ], dict(o="27322a", O="18201a", b="6b7a4a", s="c4ccc6", d="4a5560", l="e6eef0", m="aab4b8", e="111111",
            t="9aa69a"))


def chum_bucket():
    """Vanilla's water bucket, its water swapped for chum: minced red-brown bait with pale flecks."""
    img = vanilla("item/water_bucket")
    ramp = [hexc("5c2418"), hexc("7a3424"), hexc("9a4a32"), hexc("b8664a")]
    flecks = {(6, 4), (9, 5), (7, 6), (10, 4)}
    for y in range(img.h):
        for x in range(img.w):
            c = img.get(x, y)
            if c[3] and c[2] > c[0] + 30 and c[2] > c[1]:     # water: blue
                k = min(3, int(lum(c) / 255.0 * 4.4))
                img.set(x, y, hexc("e8c8b0") if (x, y) in flecks else ramp[k])
    return img


def blobs(discs, ramp, rim, flecks=()):
    """Round lumps (discs (cx, cy, r) in sprite pixels, later ones in front), each lit from the top left in three flat
    steps of ramp (dark, mid, light), the one behind shadowed where a front one overlaps it, all outlined like worm()."""
    owner = {}
    for y in range(16):
        for x in range(16):
            for i, (cx, cy, r) in enumerate(discs):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    owner[(x, y)] = i
    img = Img(16, 16)
    for (x, y), i in owner.items():
        cx, cy, r = discs[i]
        lit = -((x + 0.5 - cx) + (y + 0.5 - cy)) / (r * 1.4)
        c = ramp[2] if lit > 0.35 else ramp[1] if lit > -0.35 else ramp[0]
        if any(owner.get((x + dx, y + dy), -1) > i for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            c = rim[0]                                 # a front lump's edge over this one
        img.set(x, y, c)
    for (x, y), c in flecks:
        if (x, y) in owner:
            img.set(x, y, hexc(c))
    for y in range(16):
        for x in range(16):
            if (x, y) in owner:
                continue
            if any((x + dx, y + dy) in owner for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.set(x, y, rim[1] if ((x - 1, y) in owner or (x, y - 1) in owner) else rim[0])
    return img


def crickets():
    """A field cricket side on, facing left: a dark brown body, its big jumping leg folded over its back, long
    antennae swept back over it."""
    return rows_sprite([
        "................",
        "................",
        ".a..............",
        "..aa............",
        "....aa....oo....",
        "......a..oLLo...",
        "...oooo.oLLLLo..",
        "..oHHhhoLLlLLLo.",
        ".oEHhhBBLLlBBLo.",
        ".ohHhBBBbbbbbbo.",
        "..ohBBbbbbbbbbo.",
        "...ooobbbbbbooL.",
        "...l.l.ooooo..L.",
        "..l..l..l.l...L.",
        "................",
        "................",
    ], dict(o="1e1408", a="6a5034", H="8a6a40", h="5e4426", E="0a0a0a", B="6a4a28", b="44301a", L="9a7444", l="3a2a14"))


def dough_balls():
    """Three balls of bread dough, pale and soft, one in front of the other two."""
    return blobs([(5.5, 6.5, 3.4), (10.5, 6.0, 3.2), (8.0, 10.5, 3.6)],
                 [hexc("c8a86a"), hexc("e8d09a"), hexc("faf0cc")], (hexc("6a5028"), hexc("4a3818")))


def seed_mash():
    """A lump of mash: wheat seeds pressed together with bone meal, the seeds showing in a deliberate scatter."""
    seeds = [((5, 7), "5a8a2a"), ((8, 5), "5a8a2a"), ((10, 8), "5a8a2a"), ((7, 10), "5a8a2a"), ((4, 10), "3e6a1e"),
             ((11, 11), "3e6a1e"), ((9, 12), "5a8a2a"), ((6, 4), "3e6a1e")]
    return blobs([(6.0, 8.0, 4.0), (10.0, 9.5, 3.6), (8.5, 6.0, 3.2)],
                 [hexc("a89870"), hexc("d0c49c"), hexc("ece4c8")], (hexc("5a4e30"), hexc("3a3220")), seeds)


def leeches():
    """Two fat leeches: near-black backs, an orange-spotted stripe, blunt sucker ends."""
    dark = [hexc("1a1814"), hexc("33302a"), hexc("4e4a3e")]
    rim = (hexc("0e0c08"), hexc("080604"))
    a = worm([(2.5, 12.0), (5.0, 10.0), (8.0, 11.0), (11.0, 9.0), (12.5, 6.0)], 1.45, dark, hexc("c8782a"), rim)
    b = worm([(3.0, 6.0), (5.5, 3.5), (9.0, 3.5)], 1.3, dark, hexc("c8782a"), rim)
    out = Img(16, 16)
    out.paste(b, 0, 0)
    out.paste(a, 0, 0)
    return out


BAITS = OrderedDict([
    ("earthworms", ("Earthworms", earthworms)),
    ("crickets", ("Crickets", crickets)),
    ("dough_balls", ("Dough Balls", dough_balls)),
    ("seed_mash", ("Seed Mash", seed_mash)),
    ("leeches", ("Leeches", leeches)),
    ("cut_bait", ("Cut Bait", cut_bait)),
    ("chum_bucket", ("Chum Bucket", chum_bucket)),
    ("glowworm", ("Glowworm", glowworm)),
    ("live_minnow", ("Live Minnow", live_minnow)),
    ("magma_grub", ("Magma Grub", magma_grub)),
    ("irradiated_bait", ("Irradiated Bait", irradiated_bait)),
    ("star_bait", ("Star Bait", star_bait)),
])


def bait_files():
    sprites = OrderedDict()
    for name, (_, make) in BAITS.items():
        img = make()
        img.save(os.path.join(ASSETS, "textures", "item", "bait", name + ".png"))
        write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([
            ("parent", "minecraft:item/generated"), ("textures", OrderedDict(layer0="mymod:item/bait/" + name))]))
        write(os.path.join(ASSETS, "items", name + ".json"),
              {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + name)])})
        sprites[name] = img
    # The Tackle Bench's empty bait slot: the earthworms' outline in vanilla's slot-icon grey.
    worms = sprites["earthworms"]
    icon = Img(16, 16)
    for y in range(16):
        for x in range(16):
            if worms.get(x, y)[3] and any(worms.get(x + dx, y + dy)[3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                icon.set(x, y, hexc("555555"))
    icon.save(os.path.join(ASSETS, "textures", "gui", "sprites", "container", "slot", "rod_bait.png"))
    return sprites


# ======================================================================================================= data


def advancement(name, unlock, category):
    items = [unlock] if isinstance(unlock, str) else unlock
    criteria = OrderedDict()
    for i, item in enumerate(items):
        criteria[f"has_item_{i}"] = OrderedDict([
            ("conditions", OrderedDict([("items", [OrderedDict([("items", item)])])])), ("trigger", "minecraft:inventory_changed")])
    criteria["has_the_recipe"] = OrderedDict([("conditions", OrderedDict([("recipes", "mymod:" + name)])),
                                              ("trigger", "minecraft:recipe_unlocked")])
    write(os.path.join(DATA, "mymod", "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria),
        ("requirements", [list(criteria.keys())]),
        ("rewards", OrderedDict([("recipes", ["mymod:" + name])]))]))


def shaped(name, pattern, key, result, count, unlock, category):
    write(os.path.join(DATA, "mymod", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("pattern", pattern), ("key", key),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, unlock, "decorations" if category == "building" else category)


def shapeless(name, ingredients, result, count, unlock, category="misc"):
    write(os.path.join(DATA, "mymod", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, unlock, category)


RAW_FISH = ["minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish"] + ["mymod:" + n for n in (
    "brook_trout", "mud_catfish", "pike", "mackerel", "swordfish", "anglerfish", "glowfin_tetra", "parrotfish", "ice_char",
    "bog_eel", "cave_blindfish", "largemouth_bass", "bluegill", "yellow_perch", "walleye", "crappie", "sturgeon", "red_snapper",
    "flounder", "tuna", "mahi_mahi", "barracuda")]
PREDATOR_FISH = ["minecraft:cod", "minecraft:salmon"] + ["mymod:" + n for n in (
    "pike", "swordfish", "anglerfish", "starlit_gar", "magma_eel", "mud_catfish", "bog_eel", "largemouth_bass", "walleye",
    "tuna", "mahi_mahi", "barracuda")]
NIGHT_FISH = ["mymod:" + n for n in ("anglerfish", "starlit_gar", "abyssal_squidling", "ghoulfish", "glowfin_tetra", "sculk_gulper",
                                     "walleye")]
MUTANT_FISH = ["mymod:glowing_mutant_carp"]
# Small fish of ponds and creeks (crickets), and the bottom feeders (dough balls).
PANFISH = ["mymod:" + n for n in ("bluegill", "crappie", "yellow_perch", "brook_trout")]
BOTTOM_FISH = ["minecraft:cod"] + ["mymod:" + n for n in ("mud_catfish", "glowing_mutant_carp", "sturgeon", "flounder")]


def data():
    item_tags = os.path.join(DATA, "mymod", "tags", "item")
    tag_add(os.path.join(item_tags, "raw_fish.json"), RAW_FISH)
    tag_add(os.path.join(item_tags, "predator_fish.json"), PREDATOR_FISH)
    tag_add(os.path.join(item_tags, "night_fish.json"), NIGHT_FISH)
    tag_add(os.path.join(item_tags, "mutant_fish.json"), MUTANT_FISH)
    tag_add(os.path.join(item_tags, "panfish.json"), PANFISH)
    tag_add(os.path.join(item_tags, "bottom_fish.json"), BOTTOM_FISH)
    tag_add(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "axe.json"), ["mymod:%s_fish_mount" % w for w in WOODS])
    for wood in WOODS:
        # A board on two sticks with a gold nugget for the plate; unlocked by catching any fish.
        shaped(f"{wood}_fish_mount", ["SPS", "PNP"], OrderedDict([("S", "minecraft:stick"), ("P", f"minecraft:{wood}_planks"),
                                                                 ("N", "minecraft:gold_nugget")]),
               f"mymod:{wood}_fish_mount", 1, "#minecraft:fishes", "building")
    shapeless("earthworms", ["minecraft:dirt", "minecraft:rotten_flesh"], "mymod:earthworms", 4,
              ["minecraft:rotten_flesh", "mymod:earthworms"])
    # Worm castings: earthworms back to the soil as bone meal.
    shapeless("bone_meal_from_earthworms", ["mymod:earthworms"] * 4, "minecraft:bone_meal", 1, "mymod:earthworms")
    shapeless("cut_bait", ["#mymod:raw_fish"], "mymod:cut_bait", 4, "#mymod:raw_fish")
    shapeless("chum_bucket", ["minecraft:bucket", "mymod:cut_bait", "mymod:cut_bait", "mymod:cut_bait", "minecraft:bone_meal"],
              "mymod:chum_bucket", 8, "mymod:cut_bait")
    shapeless("glowworm", ["minecraft:glow_berries", "mymod:earthworms"], "mymod:glowworm", 2, "minecraft:glow_berries")
    shapeless("live_minnow_from_pixie_minnow", ["mymod:pixie_minnow", "minecraft:kelp"], "mymod:live_minnow", 2, "mymod:pixie_minnow")
    shapeless("live_minnow_from_mackerel", ["mymod:mackerel", "minecraft:kelp"], "mymod:live_minnow", 2, "mymod:mackerel")
    shapeless("live_minnow_from_bluegill", ["mymod:bluegill", "minecraft:kelp"], "mymod:live_minnow", 2, "mymod:bluegill")
    shapeless("live_minnow_from_mooshroom_minnow", ["mymod:mooshroom_minnow", "minecraft:kelp"], "mymod:live_minnow", 2,
              "mymod:mooshroom_minnow")
    # The new early baits: crickets and leeches are found (long grass; swamp dirt and mud), the rest made.
    shapeless("dough_balls", ["minecraft:bread"], "mymod:dough_balls", 4, "minecraft:bread")
    shapeless("seed_mash", ["minecraft:wheat_seeds", "minecraft:wheat_seeds", "minecraft:wheat_seeds", "minecraft:bone_meal"],
              "mymod:seed_mash", 6, "minecraft:wheat_seeds")
    shapeless("magma_grub", ["minecraft:magma_cream", "mymod:earthworms"], "mymod:magma_grub", 4, "minecraft:magma_cream")
    shapeless("irradiated_bait", ["mymod:raw_uranium"] + ["mymod:cut_bait"] * 4, "mymod:irradiated_bait", 4, "mymod:raw_uranium")
    shapeless("irradiated_bait_from_mutant_carp", ["mymod:glowing_mutant_carp"], "mymod:irradiated_bait", 4,
              "mymod:glowing_mutant_carp")
    shapeless("star_bait", ["mymod:star_crystal_shard", "mymod:cut_bait", "mymod:cut_bait"], "mymod:star_bait", 2,
              "mymod:star_crystal_shard")


LANG_ENTRIES = OrderedDict([
    # Weights and trophies
    ("item.mymod.trophy_fish", "Trophy %s"),
    ("tooltip.mymod.trophy.weight", "Weight: %s"),
    ("tooltip.mymod.trophy.caught_by", "Caught by %s"),
    ("tooltip.mymod.trophy.day", "Day %s"),
    ("hud.mymod.fishing.weighed", "%s, %s"),
    ("hud.mymod.fishing.trophy", "Trophy! %s"),
    ("hud.mymod.fishing.personal_best", "New personal best! (was %s)"),
    ("chat.mymod.fishing.personal_best", "New personal best: %s, %s (was %s)"),
    ("hud.mymod.fishing.big_one", "Big one!"),
    ("hud.mymod.fishing.tiring", "The fish is tiring! Reel it in!"),
    ("hud.mymod.fishing.surge", "It's making a last run!"),
    # The mount
    ("block.mymod.oak_fish_mount", "Oak Fish Mount"),
    ("block.mymod.spruce_fish_mount", "Spruce Fish Mount"),
    ("block.mymod.dark_oak_fish_mount", "Dark Oak Fish Mount"),
    ("block.mymod.fish_mount.trophy", "%s, %s, caught by %s on day %s"),
    ("block.mymod.fish_mount.plain", "%s (a typical one, about %s)"),
    ("block.mymod.fish_mount.caught_by", "Caught by %s"),
    # Bait
    ("tooltip.mymod.bait", "Bait"),
    ("tooltip.mymod.bait.size", "Bigger fish +%s"),
    ("tooltip.mymod.bait.attracts.predator_fish", "Draws in predators"),
    ("tooltip.mymod.bait.attracts.night_fish", "Draws in the fish of the night"),
    ("tooltip.mymod.bait.attracts.mutant_fish", "Draws in mutants"),
    ("tooltip.mymod.bait.attracts.panfish", "Draws in panfish (bluegill, crappie, perch, trout)"),
    ("tooltip.mymod.bait.attracts.bottom_fish", "Draws in bottom feeders (catfish, carp, sturgeon, flounder, cod)"),
    ("tooltip.mymod.bait.attracted_size.panfish", "Bigger panfish +%s"),
    ("tooltip.mymod.bait.attracted_size.bottom_fish", "Bigger bottom feeders +%s"),
    ("tooltip.mymod.bait.smaller", "Smaller fish -%s"),
    ("tooltip.mymod.bait.attracted_size.predator_fish", "Bigger predators +%s"),
    ("tooltip.mymod.bait.attracted_size.night_fish", "Bigger night fish +%s"),
    ("tooltip.mymod.bait.attracted_size.mutant_fish", "Bigger mutants +%s"),
    ("tooltip.mymod.bait.lava_only", "Only works in lava"),
    ("tooltip.mymod.bait.radiation", "Irradiates the angler with every catch"),
    ("tooltip.mymod.bait.fit", "Load it on a rod at a Tackle Bench; one is eaten per fish"),
    ("tooltip.mymod.rod_slot.bait", "Bait"),
    ("tooltip.mymod.rod_slot.bait.stock", "None"),
    ("tooltip.mymod.rod.bait_count", "%s x%s"),
    ("container.mymod.tackle_bench.stat.size", "Size bias"),
    ("container.mymod.tackle_bench.tag.radiation", "HOT"),
    # Commands
    ("commands.mymod.fishing.bad_species", "No weighed fishing species called %s"),
    ("commands.mymod.fishing.give_trophy", "Gave %s (%s)"),
    ("commands.mymod.fishing.records.none", "You haven't weighed a fish yet: catch one on a mod rod."),
    ("commands.mymod.fishing.records.header", "Your personal bests (%s species):"),
    ("commands.mymod.fishing.records.line", " %s: %s, day %s (world record %s)"),
    ("commands.mymod.fishing.test.species", "Testing %s: %s (%s), difficulty %s%s"),
    ("commands.mymod.fishing.test.trophy", ", trophy class"),
    ("commands.mymod.fishing.test.strength",
     "Fight strength %s (weight percentile %s%%, rarity %s%%, size %s%%): x%s as long, %s to %s s%s"),
    ("commands.mymod.fishing.test.stamina", ", tires and surges"),
])


# ======================================================================================================= preview
#
# Everything becomes texel quads in the north-facing mount's frame (blocks, about the block centre: x to the viewer's
# left, y up, z into the wall), drawn with a depth buffer and Minecraft's flat block shading by face direction.

FACE_CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}


def element_quads(elements, tex):
    """The plaque's faces as texel quads: (corners, colour)."""
    out = []
    for e in elements:
        a = [v / 16.0 - 0.5 for v in e["from"]]
        b = [v / 16.0 - 0.5 for v in e["to"]]
        for face, f in e["faces"].items():
            if face == "south":
                continue
            A, B, Cc, D = FACE_CORNERS[face](a, b)
            u0, v0, u1, v1 = [x * ATLAS / 16.0 for x in f["uv"]]
            nu, nv = max(1, int(round(abs(u1 - u0)))), max(1, int(round(abs(v1 - v0))))
            for j in range(nv):
                for i in range(nu):
                    tx = int(math.floor(u0 + (u1 - u0) * (i + 0.5) / nu))
                    ty = int(math.floor(v0 + (v1 - v0) * (j + 0.5) / nv))
                    col = tex.get(tx, ty)
                    if not col[3]:
                        continue
                    corners = []
                    for s, t in ((i / nu, j / nv), ((i + 1) / nu, j / nv), ((i + 1) / nu, (j + 1) / nv), (i / nu, (j + 1) / nv)):
                        top = [A[k] + (B[k] - A[k]) * s for k in range(3)]
                        bot = [D[k] + (Cc[k] - D[k]) * s for k in range(3)]
                        corners.append(tuple(top[k] + (bot[k] - top[k]) * t for k in range(3)))
                    out.append((corners, col, False))
    return out


def fish_quads(length, tilt_sign=-1):
    """The pike's entity model, posed and placed as FishMountRenderer does it, as texel quads in the mount frame."""
    import gen_fish_entities as E
    rig = E.Rig(E.pike())
    bend = 0.22
    pose = {}
    k = 0
    for name in ("tail", "tail2", "tail3"):
        if name in rig.nodes:
            pose[name] = (0.0, -bend * (1.0 if k == 0 else 0.7), 0.0)
            k += 1
    quads, sample = E.rig_quads(rig, pose, rig.scale)
    texels = []
    for pts, (u, v, fw, fh), _ in quads:
        A, B, Cc, D = [tuple(c / 16.0 for c in p) for p in pts]
        e1 = [B[i] - A[i] for i in range(3)]
        e2 = [D[i] - A[i] for i in range(3)]
        for j in range(fh):
            for i in range(fw):
                col, glow = sample(u + i, v + j)
                if col is None:
                    continue
                cs = []
                for a_, b_ in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a_ / fw, b_ / fh
                    cs.append(tuple(A[q] + e1[q] * s + e2[q] * t for q in range(3)))
                texels.append((cs, col, glow))
    pts = [p for cs, _, _ in texels for p in cs]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
    s = length / (hi[2] - lo[2])
    thickness = (hi[0] - lo[0]) * s
    yaw = math.radians(-12.0)
    tilt = math.radians(tilt_sign * max(0.0, min(1.0, (length - 1.05) / 0.85)) * 30.0)
    out_from_board = length / 2.0 * math.sin(math.radians(12.0)) + 0.01
    frac = max(0.0, min(1.0, (length - 1.05) / 0.85))
    origin = (0.0, 9.5 / 16.0 - 0.5 + frac * 0.08, 13.5 / 16.0 - 0.5 - thickness / 2.0 - out_from_board)

    def place(p):
        x, y, z = [(p[i] - centre[i]) * s for i in range(3)]
        x, y, z = z, -y, x                                    # flip upright, tail to +x, the -x flank out
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        x, y = x * math.cos(tilt) - y * math.sin(tilt), x * math.sin(tilt) + y * math.cos(tilt)
        return (x + origin[0], y + origin[1], z + origin[2])
    return [([place(p) for p in cs], col, glow) for cs, col, glow in texels]


def mount_length(avg, weight):
    """FishMountRenderer.length."""
    base = max(0.28, min(0.95, 0.62 * (avg / 5.0) ** 0.15))
    return min(base * (weight / avg) ** 0.45, 1.9)


def render(img, quads, view, cx, cy, scale, depth):
    """Draws texel quads: view(p) -> (screen x, screen y, depth) in blocks, then scaled about (cx, cy)."""
    for corners, col, glow in quads:
        sp = [view(p) for p in corners]
        e1 = [corners[1][i] - corners[0][i] for i in range(3)]
        e2 = [corners[3][i] - corners[0][i] for i in range(3)]
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        n = [c / nl for c in n]
        # Minecraft's face shading: up 1.0, down 0.5, north/south 0.8, east/west 0.6.
        lit = 1.0 if glow else (0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] > 0 else 0.5) * n[1] ** 2)
        pts = [(cx + q[0] * scale, cy - q[1] * scale, q[2]) for q in sp]
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        z = sum(p[2] for p in pts) / 4
        for py in range(max(0, int(min(ys))), min(img.h, int(math.ceil(max(ys))) + 1)):
            for px in range(max(0, int(min(xs))), min(img.w, int(math.ceil(max(xs))) + 1)):
                if inside(pts, px + 0.5, py + 0.5) and z < depth[py * img.w + px]:
                    depth[py * img.w + px] = z
                    img.set(px, py, shade(col, lit))


def inside(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cr = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cr) < 1e-9:
            continue
        s = 1 if cr > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return sign != 0


def front_view(p):
    """Seen from the room (the -z side), face on: the viewer's right is -x."""
    return (-p[0], p[1], p[2])


def three_quarter_view(p, yaw=35.0, pitch=12.0):
    a, b = math.radians(yaw), math.radians(pitch)
    x, y, z = -p[0], p[1], p[2]
    x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
    y, z = y * math.cos(b) + z * math.sin(b), -y * math.sin(b) + z * math.cos(b)
    return (x, y, z)


def wall(img, x0, y0, x1, y1):
    """A plain stone-brick-grey wall behind the mounts, in two flat tones of courses (no noise)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            course = (y - y0) // 24
            joint = (y - y0) % 24 == 0 or (x - x0 + (12 if course % 2 else 0)) % 48 == 0
            img.set(x, y, hexc("6f6f74") if joint else hexc("8a8a90"))


def preview(path, models):
    sys.path.insert(0, HERE)
    from gen_fishing_rods import text
    W, H = 1180, 1000 + 180 * ((len(BAITS) - 1) // 8)
    sheet = Img(W, H, hexc("2b2f36"))

    def label(x, y, s, k=2, colour="#e9eff5"):
        text(LabelProxy(sheet), x, y, s, colour, k)

    # Row 1: oak and dark oak, front and three quarters, empty but for the plate (big scale).
    S = 190
    for i, wood in enumerate(("oak", "dark_oak")):
        elements, tex = models[wood]
        for j, (view, name) in enumerate(((front_view, "FRONT"), (three_quarter_view, "3/4"))):
            ox = 20 + (i * 2 + j) * 290
            wall(sheet, ox, 40, ox + 270, 300)
            depth = [1e9] * (W * H)
            render(sheet, element_quads(elements, tex), view, ox + 135, 170, S, depth)
            label(ox, 16, f"{wood.replace('_', ' ')} {name}")
    # Row 2: three dark oak mounts with a pike: ordinary (typical 8 lb), a 25 lb trophy, a 50 lb near-record.
    label(20, 330, "DARK OAK MOUNTS, PIKE BY WEIGHT (ENTITY MODEL, RENDERER'S SIZING)")
    S2 = 150
    wall(sheet, 20, 355, W - 20, 760)
    elements, tex = models["dark_oak"]
    for i, (weight, title, by) in enumerate(((8.0, "PIKE", "ORDINARY: 8 LB"), (25.0, "PIKE, 25.0 LB", "BY STEVE"),
                                             (50.0, "PIKE, 50.0 LB", "BY STEVE"))):
        cx = 200 + i * 380
        cy = 560
        length = mount_length(8.0, weight)
        depth = [1e9] * (W * H)
        quads = element_quads(elements, tex) + fish_quads(length)
        render(sheet, quads, front_view, cx, cy, S2, depth)
        # The plate's engraving, roughly as the renderer lays it out.
        py = cy - (4.5 / 16 - 0.5) * S2
        tw = len(title) * 4
        text(LabelProxy(sheet), int(cx - tw / 2), int(py - 6), title, "#2e2008", 1)
        if i:
            text(LabelProxy(sheet), int(cx - len(by) * 2), int(py + 1), by, "#2e2008", 1)
    for i, weight in enumerate((8.0, 25.0, 50.0)):
        label(60 + i * 380, 770, f"{weight:g} LB -> {mount_length(8.0, weight):.2f} BLOCKS LONG")
    # Row 3: the baits, 1x and 8x.
    label(20, 800, "BAITS (1X AND 8X)")
    for i, (name, (title, _)) in enumerate(BAITS.items()):
        img = Img(16, 16)
        img.px = list(BAIT_SPRITES[name].px)
        x = 20 + (i % 8) * 145
        y = (i // 8) * 180
        sheet.fill(x, 825 + y, x + 16, 841 + y, hexc("8b8b8b"))
        sheet.paste(img, x, 825 + y)
        sheet.fill(x, 846 + y, x + 128, 974 + y, hexc("8b8b8b"))
        sheet.paste(img, x, 846 + y, 8)
        label(x, 980 + y, title, 1)
    sheet.save(path)
    print("preview:", path)


class LabelProxy:
    """Lets gen_fishing_rods.text draw onto our Img (it calls fill(x0, y0, x1, y1, colour))."""

    def __init__(self, img):
        self.img = img

    def fill(self, x0, y0, x1, y1, c):
        self.img.fill(x0, y0, x1, y1, c)


# ======================================================================================================= main

BAIT_SPRITES = {}


def main():
    models = OrderedDict()
    for wood in WOODS:
        models[wood] = plaque_model(wood)
    BAIT_SPRITES.update(bait_files())
    data()
    lang = OrderedDict(LANG_ENTRIES)
    for name, (title, _) in BAITS.items():
        lang["item.mymod." + name] = title
    merge_lang(lang)
    elements = models["oak"][0]
    print("fish mount: %d elements per wood, %d woods; %d baits" % (len(elements), len(WOODS), len(BAITS)))
    if PREVIEW:
        preview(os.path.abspath(PREVIEW), models)


if __name__ == "__main__":
    main()
