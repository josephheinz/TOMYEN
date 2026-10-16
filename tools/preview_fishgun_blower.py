"""Renders a preview sheet of the Fish Gun and the Leaf Blower / Blower-Vac from their generated model files: each from the
side, three-quarters front and rear, in first person (with the hand holds marked) and as its inventory icon; then a Fish
Gun round in flight (the tuna's own entity model, tumbling along an arc, at the size a trophy is drawn) and mock-ups of
the ammo HUD for both items, drawn with Minecraft's own font.

Usage: python3 tools/preview_fishgun_blower.py src/main/resources/assets/tomyen out.png"""
import json, math, os, sys, zipfile

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import apply, display_matrix, mul, rx, ry, rz
from gunsmith_kit import render, ortho, first_person, gui_icon, compose, SIDE, RIGHT_FRONT, LEFT_REAR, bounds
from gen_longswords import read_png as read_png_bytes


def read_png(path):
    return read_png_bytes(open(path, "rb").read())

ASSETS, OUT = sys.argv[1], sys.argv[2]
MODELS = os.path.join(ASSETS, "models/item")
TEX = os.path.join(ASSETS, "textures")
CACHE = {}


def load(name):
    m = json.load(open(os.path.join(MODELS, name + ".json")))
    atlases = {}
    for key, ref in m["textures"].items():
        path = os.path.join(TEX, ref.split(":")[1] + ".png")
        if path not in CACHE:
            w, h, px = read_png(path)
            CACHE[path] = (px, w)
        atlases["#" + key] = CACHE[path]
    return m["elements"], atlases, m["display"]


def mark(img, w, h, p, colour, r=3):
    x, y = int(p[0]), int(p[1])
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if abs(dx) + abs(dy) <= r and 0 <= x + dx < w and 0 <= y + dy < h:
                img[(y + dy) * w + x + dx] = colour


def views(name, holds=None):
    elements, atlases, display = load(name)
    lo, hi = bounds(elements)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    w, h = 460, 300
    k = min((w - 40) / max(hi[2] - lo[2], 4.0), (h - 30) / max(hi[1] - lo[1], 4.0))
    cells = []
    for view, kk in ((SIDE, k), (RIGHT_FRONT, k * 0.9), (LEFT_REAR, k * 0.9)):
        cells.append((render(elements, atlases, ortho(view, kk, w, h, c), lambda n: n, w, h), w, h, 1))
    project, turn = first_person(display, 480, 300)
    fp = render(elements, atlases, project, turn, 480, 300)
    fp = [(110, 130, 160) if v is None else v for v in fp]
    if holds:
        for p, colour in holds:
            mark(fp, 480, 300, project(p), colour)
    cells.append((fp, 480, 300, 1))
    project, turn = gui_icon(display, 48)
    ic = render(elements, atlases, project, turn, 48, 48)
    cells.append(([(139, 139, 139) if v is None else v for v in ic], 48, 48, 3))
    return cells


GRIP, SUPPORT = (255, 60, 60), (60, 200, 255)
rows = [
    views("fish_gun", [((8.0, 4.6, 10.9), GRIP), ((8.0, 4.4, -8.3), SUPPORT)]),
    views("leaf_blower", [((8.0, 15.8, 14.6), GRIP), ((8.0, 2.8, -2.9), SUPPORT)]),
    views("leaf_vacuum", [((8.0, 15.8, 14.6), GRIP), ((8.0, 2.8, -2.9), SUPPORT)]),
    views("leaf_vacuum_suck")[:2],
]

# ------------------------------------------------------------------------------------------------- the round in flight


def length(average, weight):
    """FishMountRenderer.length: how long a fish of this weight is drawn, in blocks."""
    base = min(max(0.62 * (average / 5.0) ** 0.15, 0.28), 0.95)
    return min(base * (weight / average) ** 0.45, 1.9)


def fish_cells():
    """The tuna's entity model tumbling along a lobbed arc (FishProjectileRenderer's spin), at a 400 lb trophy's length,
    against a sky with a one-block ruler; then a minnow and a pike for scale."""
    import gen_fish_entities as G
    out = []
    W, H = 900, 300
    sky = [(150, 185, 225)] * (W * H)
    img = list(sky)
    depth = [1e9] * (W * H)

    def draw(f, length, cx, cy, spin, yaw=0.0):
        rig = G.Rig(f)
        quads, sample = G.rig_quads(rig, {}, rig.scale)
        # Model space: y down, head toward -z. Turn the head to the right of the picture (+x on screen), then tumble.
        vm = mul(rz(spin), mul(ry(-90 + yaw), [[1, 0, 0], [0, 1, 0], [0, 0, 1]]))
        pts = [p for q in quads for p in q[0]]
        lo = min(p[2] for p in pts)
        hi = max(p[2] for p in pts)
        px = length * 16.0 / max(0.01, hi - lo) * PX
        part = G.render_quads(quads, sample, vm, W, H, cx, cy, px)
        for i, c in enumerate(part):
            if c is not None:
                img[i] = c

    PX = 3.2
    # The arc: a trophy tuna lobbed left to right, drawn at five points of its flight, turning end over end.
    for n in range(5):
        t = n / 4
        x = 90 + t * 720
        y = 230 - 520 * t * (1 - t) * 0.8
        draw(G.tuna(), length(150.0, 400.0), x, y, -30 + n * 55)
    for x in range(30, 30 + int(16 * PX)):
        for y in range(16, 19):
            img[y * W + x] = (40, 40, 40)
    out.append((img, W, H, 1))
    img2 = [(150, 185, 225)] * (420 * H)
    img = img2
    W2 = 420

    def draw2(f, length, cx, cy, spin):
        rig = G.Rig(f)
        quads, sample = G.rig_quads(rig, {}, rig.scale)
        vm = mul(rz(spin), ry(-90))
        pts = [p for q in quads for p in q[0]]
        lo = min(p[2] for p in pts)
        hi = max(p[2] for p in pts)
        px = length * 16.0 / max(0.01, hi - lo) * PX
        part = G.render_quads(quads, sample, vm, W2, H, cx, cy, px)
        for i, c in enumerate(part):
            if c is not None:
                img2[i] = c
    draw2(G.glowfin_tetra(), length(0.006, 0.006), 60, 150, 20)
    draw2(G.pike(), length(8.0, 20.0), 180, 150, -15)
    draw2(G.swordfish(), length(250.0, 250.0), 330, 150, 0)
    out.append((img2, W2, H, 1))
    return out


rows.append(fish_cells())

# --------------------------------------------------------------------------------------------------------------- HUD


def client_jar():
    from gen_longswords import client_jar as cj
    saved = sys.argv[:]
    sys.argv[1:] = []                                 # it reads argv[2] as a jar path
    try:
        return cj()
    finally:
        sys.argv[:] = saved


FONT = None


def font():
    global FONT
    if FONT is None:
        jar = zipfile.ZipFile(client_jar())
        w, h, px = read_png_bytes(jar.read("assets/minecraft/textures/font/ascii.png"))
        widths = {}
        for code in range(256):
            gx, gy = code % 16 * 8, code // 16 * 8
            last = -1
            for x in range(8):
                if any(px[(gy + y) * w + gx + x][3] > 0 for y in range(8)):
                    last = x
            widths[code] = last + 2 if last >= 0 else 4
        FONT = (w, px, widths)
    return FONT


def text(img, W, s, x, y, colour, k=2, shadow=True):
    w, px, widths = font()
    for pass_, (dx, col) in enumerate((((1, (0x3F, 0x3F, 0x3F) if shadow else None)), (0, colour))):
        if col is None:
            continue
        cx = x
        for ch in s:
            code = ord(ch) if ord(ch) < 256 else ord("?")
            gx, gy = code % 16 * 8, code // 16 * 8
            for yy in range(8):
                for xx in range(8):
                    if px[(gy + yy) * w + gx + xx][3] > 0:
                        for a in range(k):
                            for b in range(k):
                                X, Y = cx + (xx + dx) * k + a, y + (yy + dx) * k + b
                                if 0 <= X < W and 0 <= Y < len(img) // W:
                                    img[Y * W + X] = col
            cx += widths[code] * k
    return cx


def rect(img, W, x0, y0, x1, y1, colour):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y * W + x] = colour


def hud_cell(lines, bar=None):
    """A strip of hotbar-grey with the HUD's lines as AmmoHud draws them (2x GUI scale)."""
    W, H = 560, 120
    img = [(76, 96, 60)] * (W * H)
    rect(img, W, 0, 70, 180, 120, (40, 40, 40))
    for i in range(9):
        rect(img, W, 2 + i * 20, 74, 18 + i * 20, 90, (90, 90, 90))
    y = 16
    for parts in lines:
        x = 196
        for s, colour in parts:
            x = text(img, W, s, x, y, colour) + 12
        y += 20
    if bar:
        frac, colour = bar
        rect(img, W, 196, y + 2, 196 + 80, y + 8, (32, 32, 32))
        rect(img, W, 196, y + 2, 196 + int(80 * frac), y + 8, colour)
    return (img, W, H, 1)


WHITE, GRAY, GOLD, RED, ORANGE = (255, 255, 255), (170, 170, 170), (255, 170, 0), (255, 85, 85), (255, 122, 26)
rows.append([
    hud_cell([[("Trophy Tuna 412 lb", GOLD), ("x1", GRAY)], [("CANNONBALL", RED)]]),
    hud_cell([[("Pike 8.0 lb", WHITE), ("x12", GRAY)], [("Thumper", (255, 255, 85))]]),
    hud_cell([[("Fuel 64%", WHITE), ("SUCK", (85, 255, 255)), ("Bag 4/9", GRAY)]], (0.64, (240, 168, 48))),
])
compose(rows, OUT)
print("preview:", OUT)
