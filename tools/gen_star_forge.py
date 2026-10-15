"""The Star Forge: its block models (dormant and starlit), their two texture atlases, the GUI background, its
blockstate, item, recipe, recipe unlock, loot table, pickaxe tag and lang, and a preview sheet.

Run from anywhere:  python3 tools/gen_star_forge.py [--preview out.png]

The model is built from named boxes (pixels, facing north: the window looks north). Every face that can be seen is
painted on its own patch of an atlas, so bevels, inlays and trims sit exactly on the face they belong to, one texel to
a model pixel. Structural faces (meteorite stone, star steel) come from block/star_forge; the glowing parts (the
starry window, the core crystal, the spires and studs) from block/star_forge_glow, or block/star_forge_glow_dormant
for the dormant model, where they don't glow. Faces hidden against another box are left out, and the coplanar check
(tools/coplanar.py) refuses to write a model with two overlapping faces in one plane.

The preview (--preview) shows the forge at three quarters by day (dormant) and by night (starlit), a front view, the
screen mid-infusion, and tooltips of infused gear."""
import json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png, element_rotation, apply  # noqa: E402
from coplanar import check_elements  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
ATLAS = 128  # texels; one model pixel = one texel, so a uv unit is ATLAS / 16 texels
CLEAR = (0, 0, 0, 0)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3]) + (c[3],)


class Img:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            if len(c) == 4 and c[3] < 255 and c[3] > 0:
                b = self.px[y * self.w + x]
                t = c[3] / 255.0
                c = tuple(int(round(b[i] + (c[i] - b[i]) * t)) for i in range(3)) + (255,)
            self.px[y * self.w + x] = c

    def fill(self, x0, y0, x1, y1, c):
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.set(x, y, c)

    def paste(self, img, ox, oy, k=1):
        for y in range(img.h):
            for x in range(img.w):
                c = img.get(x, y)
                if c[3]:
                    self.fill(ox + x * k, oy + y * k, ox + (x + 1) * k, oy + (y + 1) * k, c)

    def save(self, path):
        png(path, self.w, self.h, self.px)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# ===================================================================================================== palettes
# Meteorite stone (warm near-black), star steel (deep blue), star crystal (violet to cyan): the mod's own ramps.
S = [hexc(c) for c in ("0f0b0a", "1d1613", "2b211c", "3b2f27", "504035", "67543f")]
T = [hexc(c) for c in ("0a0e2e", "16225e", "24398f", "3a63c4", "8be6ff", "f4fbff")]
C = [hexc(c) for c in ("2a1d6a", "4c34a8", "5e7fe0", "63d0ee", "bff4ff", "f4fbff")]
N = [hexc(c) for c in ("05060f", "0b0f2a", "141a45", "22205e", "33307a")]
D = [hexc(c) for c in ("14161c", "1f2330", "2c3142", "3b4157", "535a73")]


# ===================================================================================================== painters
# Each paints one face patch: (w, h) texels, the face's direction, and the element's name for special cases. Side
# faces are painted upright (row 0 is the top of the face); up faces with row 0 to the north.

def p_stone_polished(w, h, face, name):
    img = Img(w, h, S[3])
    if face in ("up", "down"):
        # A polished slab: a lit north-west edge and a shadowed south-east one.
        for x in range(w):
            img.set(x, 0, S[4])
            img.set(x, h - 1, S[2])
        for y in range(h):
            img.set(0, y, S[4])
            img.set(w - 1, y, S[2])
        if name == "hearth":
            # A star steel inlay line a pixel in from the edge, framing the ring.
            for x in range(1, w - 1):
                img.set(x, 1, T[1])
                img.set(x, h - 2, T[1])
            for y in range(1, h - 1):
                img.set(1, y, T[1])
                img.set(w - 2, y, T[1])
        return img
    for x in range(w):
        img.set(x, 0, S[4])
        if h > 2:
            img.set(x, h - 1, S[2])
    if name == "hearth" and face == "north":
        # A star set in the hearth's front edge, over the window, between two star steel studs.
        img.set(w // 2, 0, C[3])
    if name.startswith("pillar"):
        # A rounded column: lit down one side, shadowed down the other, with a star steel band a third of the way up.
        img = Img(w, h, S[3])
        for y in range(h):
            img.set(0, y, S[4])
            if w > 1:
                img.set(w - 1, y, S[2])
        for band in (h // 3, h - 2):
            for x in range(w):
                img.set(x, band, T[3] if x == 0 else T[2])
        for x in range(w):
            img.set(x, 0, S[5])
    return img


def p_stone_body(w, h, face, name):
    """The body's sides and back: a carved, recessed panel with a constellation inlaid in star steel and crystal."""
    img = Img(w, h, S[3])
    for x in range(w):
        img.set(x, 0, S[4])
        img.set(x, h - 1, S[2])
    if w < 6 or h < 6:
        return img
    x0, y0, x1, y1 = 1, 1, w - 1, h - 1
    img.fill(x0, y0, x1, y1, S[2])
    for x in range(x0, x1):
        img.set(x, y0, S[1])          # the recess's top lip throws a shadow
        img.set(x, y1 - 1, S[3])      # its floor catches the light at the bottom
    for y in range(y0, y1):
        img.set(x0, y, S[1])
        img.set(x1 - 1, y, S[3])
    img.set(x1 - 1, y0, S[2])
    img.set(x0, y1 - 1, S[2])
    # The inlay: three or four stars joined by star steel, laid out for this face's width.
    iw, ih = x1 - x0 - 2, y1 - y0 - 2
    pts = [(0.1, 0.75), (0.35, 0.25), (0.65, 0.55), (0.9, 0.2)] if iw >= 9 else [(0.15, 0.7), (0.5, 0.2), (0.85, 0.65)]
    pts = [(x0 + 1 + int(round(px * (iw - 1))), y0 + 1 + int(round(py * (ih - 1)))) for px, py in pts]
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        steps = max(abs(bx - ax), abs(by - ay))
        for i in range(1, steps):
            img.set(ax + int(round((bx - ax) * i / steps)), ay + int(round((by - ay) * i / steps)), T[2])
    for i, (x, y) in enumerate(pts):
        img.set(x, y, C[4] if i % 2 else C[3])
    return img


def p_frame(w, h, face, name):
    """The window frame's stone, lined with star steel along the edge that meets the window."""
    img = Img(w, h, S[3])
    if face == "north":
        for x in range(w):
            img.set(x, 0, S[4])
        if name == "frame_l":
            for y in range(h):
                img.set(0, y, T[1])       # the window side (seen from the front, frame_l is on the right)
                img.set(w - 1, y, S[4] if y == 0 else S[3])
        elif name == "frame_r":
            for y in range(h):
                img.set(w - 1, y, T[1])
        elif name == "frame_t":
            for x in range(w):
                img.set(x, h - 1, T[1])
        elif name == "frame_b":
            for x in range(w):
                img.set(x, 0, T[2])
        return img
    if face in ("east", "west"):
        if name in ("frame_l", "frame_r"):
            # The window's reveal (the front pixel of it shows): star steel.
            img = Img(w, h, T[1])
            for y in range(h):
                img.set(0, y, T[2])
            return img
    if face in ("up", "down") and name in ("frame_t", "frame_b"):
        return Img(w, h, T[1] if face == "down" else T[2])
    for x in range(w):
        img.set(x, 0, S[4])
    return img


def p_steel(w, h, face, name):
    """Star steel: a mid-blue field, a lit top edge, a dark foot; one polished glint where a corner catches light."""
    img = Img(w, h, T[2])
    if face in ("up", "down"):
        if face == "down":
            return Img(w, h, T[1])
        img = Img(w, h, T[2])
        if w > 2 and h > 2:
            for x in range(w):
                img.set(x, 0, T[3])
            for y in range(h):
                img.set(0, y, T[3])
            img.set(0, 0, T[4])
            if name.startswith("cap"):
                # The spire's socket.
                img.fill(1, 1, w - 1, h - 1, T[1])
        elif w > h:
            img = Img(w, h, T[3])
            img.set(0, 0, T[4])
        else:
            img = Img(w, h, T[3])
        return img
    if h == 1:
        return Img(w, h, T[3] if name == "plinth" else T[2])
    for x in range(w):
        img.set(x, 0, T[3])
        img.set(x, h - 1, T[1])
    img.set(0, 0, T[4])
    if name.startswith("claw"):
        img = Img(w, h, T[2])
        img.set(0, 0, T[4])
        img.set(0, 1, T[3])
    return img


def g_window(w, h, face, name, lit):
    """The night sky behind the window: a smooth gradient with a few deliberate stars."""
    img = Img(w, h)
    for y in range(h):
        t = y / max(1, h - 1)
        if lit:
            c = mixc(N[1], N[3], t)
        else:
            c = mixc(N[0], D[1], t)
        for x in range(w):
            img.set(x, y, c)
    stars = [(1, 1, 5), (4, 1, 4), (0, 4, 3), (4, 4, 5), (2, 5, 3)]
    for x, y, k in stars:
        if x < w and y < h:
            img.set(x, y, C[k] if lit else D[3 if k > 3 else 2])
    return img


CORE_SIDE = ["1221", "1232", "0121", "0010"]
CORE_TOP = ["1221", "2332", "2332", "1221"]


def g_core(w, h, face, name, lit):
    """The core crystal: lit from within, brightest a little above its middle, a white glint on each face. Dormant,
    the same facets in deep violet."""
    rows = CORE_TOP if face == "up" else CORE_SIDE
    ramp = [C[2], C[3], C[4], C[5]] if lit else [C[0], C[0], C[1], C[2]]
    img = Img(w, h)
    for y in range(h):
        for x in range(w):
            img.set(x, y, ramp[int(rows[y * len(rows) // h][x * len(rows[0]) // w])])
    return img


def g_crystal(w, h, face, name, lit):
    """Spires, their tips and the studs: a lit upper facet over a deeper one."""
    img = Img(w, h)
    top, mid, low = (C[4], C[3], C[2]) if lit else (C[2], C[1], C[0])
    if name.startswith("tip"):
        return Img(w, h, C[5] if lit else C[2])
    for y in range(h):
        for x in range(w):
            t = (y + 0.5) / h
            img.set(x, y, top if t < 0.34 else mid if t < 0.7 else low)
    if face == "up":
        img = Img(w, h, top)
    if name.startswith("stud") and w >= 2 and h >= 2 and face != "up":
        img.fill(0, 0, w, h, mid)
        img.set(0, 0, top)
        img.set(w - 1, h - 1, low)
    return img


# ===================================================================================================== geometry

def box(name, frm, to, mat, faces=("north", "east", "south", "west", "up", "down"), rot=None):
    return {"name": name, "from": list(frm), "to": list(to), "mat": mat, "faces": list(faces), "rot": rot}


ALL = ("north", "east", "south", "west", "up", "down")


def without(*drop):
    return tuple(f for f in ALL if f not in drop)


def boxes():
    out = []
    # Base: a polished meteorite slab and a star steel plinth band on it.
    out.append(box("base", (0, 0, 0), (16, 1, 16), "stone_polished"))
    out.append(box("plinth", (0.5, 1, 0.5), (15.5, 2, 15.5), "steel", without("down")))
    corners = [(1.5, 1.5), (14.5, 1.5), (1.5, 14.5), (14.5, 14.5)]
    for i, (cx, cz) in enumerate(corners):
        out.append(box(f"foot{i}", (cx - 1.5, 2, cz - 1.5), (cx + 1.5, 3.5, cz + 1.5), "steel"))
        out.append(box(f"pillar{i}", (cx - 1, 3.5, cz - 1), (cx + 1, 12, cz + 1), "stone_polished", without("up", "down")))
        out.append(box(f"cap{i}", (cx - 1.5, 12, cz - 1.5), (cx + 1.5, 13, cz + 1.5), "steel"))
        rot = {"origin": [cx, 13, cz], "axis": "y", "angle": 45}
        out.append(box(f"spire{i}", (cx - 0.75, 13, cz - 0.75), (cx + 0.75, 15.25, cz + 0.75), "crystal", without("down", "up"), rot))
        out.append(box(f"tip{i}", (cx - 0.4, 15.25, cz - 0.4), (cx + 0.4, 16, cz + 0.4), "crystal", without("down"), rot))
    # The body, its front a stone frame round the starry window, a star steel grille across the window and a sill.
    out.append(box("body", (2, 2, 4), (14, 10, 14), "stone_body", ("east", "south", "west")))
    out.append(box("frame_l", (2, 2, 2), (5, 10, 4), "frame", ("north", "east", "west")))
    out.append(box("frame_r", (11, 2, 2), (14, 10, 4), "frame", ("north", "east", "west")))
    out.append(box("frame_t", (5, 9, 2), (11, 10, 4), "frame", ("north", "down")))
    out.append(box("frame_b", (5, 2, 2), (11, 3, 4), "frame", ("north", "up")))
    out.append(box("window", (5, 3, 3), (11, 9, 4), "window", ("north",)))
    out.append(box("mullion", (7.75, 3, 2.5), (8.25, 9, 3), "steel", ("north", "east", "west")))
    out.append(box("transom_l", (5, 5.75, 2.5), (7.75, 6.25, 3), "steel", ("north", "up", "down")))
    out.append(box("transom_r", (8.25, 5.75, 2.5), (11, 6.25, 3), "steel", ("north", "up", "down")))
    out.append(box("sill", (4, 2, 1), (12, 3, 2), "steel", ("north", "east", "west", "up")))
    # Crystal studs in star steel bezels on the sides and back.
    out.append(box("bezel_e", (14, 4.5, 7.5), (14.5, 7.5, 10.5), "steel", without("west")))
    out.append(box("stud_e", (14.5, 5, 8), (15.25, 7, 10), "crystal", without("west", "down")))
    out.append(box("bezel_w", (1.5, 4.5, 7.5), (2, 7.5, 10.5), "steel", without("east")))
    out.append(box("stud_w", (0.75, 5, 8), (1.5, 7, 10), "crystal", without("east", "down")))
    out.append(box("bezel_s", (6.5, 4.5, 14), (9.5, 7.5, 14.5), "steel", without("north")))
    out.append(box("stud_s", (7, 5, 14.5), (9, 7, 15.25), "crystal", without("north", "down")))
    # The hearth on top, the star steel ring set in it, four claws, and the core crystal they hold.
    out.append(box("hearth", (1.5, 10, 1.5), (14.5, 11, 14.5), "stone_polished", without("down")))
    out.append(box("ring_n", (4, 11, 4), (12, 12.5, 5), "steel", without("down")))
    out.append(box("ring_s", (4, 11, 11), (12, 12.5, 12), "steel", without("down")))
    out.append(box("ring_w", (4, 11, 5), (5, 12.5, 11), "steel", without("down", "north", "south")))
    out.append(box("ring_e", (11, 11, 5), (12, 12.5, 11), "steel", without("down", "north", "south")))
    for i, (x, z) in enumerate([(4, 4), (11, 4), (4, 11), (11, 11)]):
        out.append(box(f"claw{i}", (x, 12.5, z), (x + 1, 14, z + 1), "steel", without("down")))
    out.append(box("core", (6, 11.5, 6), (10, 15.5, 10), "core", without("down"),
                   {"origin": [8, 13.5, 8], "axis": "y", "angle": 45}))
    return out


GLOW = {"window", "core", "crystal"}
PAINTERS = {"stone_polished": p_stone_polished, "stone_body": p_stone_body, "frame": p_frame, "steel": p_steel}
GLOW_PAINTERS = {"window": g_window, "core": g_core, "crystal": g_crystal}


def face_size(b, face):
    f, t = b["from"], b["to"]
    dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


class Packer:
    """Shelf packing of face patches into an ATLAS-square texture, a texel of gap between patches."""

    def __init__(self):
        self.x = self.y = self.row = 0

    def place(self, w, h):
        if self.x + w > ATLAS:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        if self.y + h > ATLAS:
            raise SystemExit("star forge atlas full")
        at = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        return at


def build():
    """The model elements (for both variants) and the three atlases: structure, glow lit, glow dormant."""
    base = Img(ATLAS, ATLAS)
    glow_lit = Img(ATLAS, ATLAS)
    glow_dark = Img(ATLAS, ATLAS)
    base_pack, glow_pack = Packer(), Packer()
    patches = {}  # (painter, w, h, face, name-key) -> (atlas, x, y), so identical patches are shared
    elements = []
    k = ATLAS / 16.0
    for b in boxes():
        glow = b["mat"] in GLOW
        faces = OrderedDict()
        for face in b["faces"]:
            fw, fh = face_size(b, face)
            w, h = max(1, int(math.ceil(fw - 1e-6))), max(1, int(math.ceil(fh - 1e-6)))
            # Same-looking faces share a patch: repeated corners, spires and so on.
            key_name = b["name"].rstrip("0123456789")
            if key_name in ("frame_l", "frame_r") or b["mat"] in ("stone_body",):
                key_name = b["name"]
            key = (b["mat"], w, h, face if face in ("up", "down") or b["mat"] == "frame" else "side", key_name)
            if key not in patches:
                if glow:
                    x, y = glow_pack.place(w, h)
                    glow_lit.paste(GLOW_PAINTERS[b["mat"]](w, h, face, b["name"], True), x, y)
                    glow_dark.paste(GLOW_PAINTERS[b["mat"]](w, h, face, b["name"], False), x, y)
                else:
                    x, y = base_pack.place(w, h)
                    base.paste(PAINTERS[b["mat"]](w, h, face, b["name"]), x, y)
                patches[key] = (x, y)
            x, y = patches[key]
            faces[face] = OrderedDict([("uv", [round(x / k, 4), round(y / k, 4), round((x + fw) / k, 4), round((y + fh) / k, 4)]),
                                       ("texture", "#glow" if glow else "#base")])
        e = OrderedDict([("name", b["name"]), ("from", b["from"]), ("to", b["to"])])
        if b["rot"]:
            e["rotation"] = OrderedDict([("angle", b["rot"]["angle"]), ("axis", b["rot"]["axis"]), ("origin", b["rot"]["origin"])])
        e["faces"] = faces
        e["_glow"] = glow
        elements.append(e)
    return elements, base, glow_lit, glow_dark


def model(elements, lit):
    out = []
    for e in elements:
        e2 = OrderedDict((k, v) for k, v in e.items() if not k.startswith("_"))
        if e["_glow"] and lit:
            # Full bright and unshaded: the starlight is its own light.
            e2["light_emission"] = 15
            e2["shade"] = False
        out.append(e2)
    return OrderedDict([
        ("parent", "minecraft:block/block"),
        ("textures", OrderedDict([("particle", "mymod:block/polished_meteorite_stone"), ("base", "mymod:block/star_forge"),
                                  ("glow", "mymod:block/star_forge_glow" if lit else "mymod:block/star_forge_glow_dormant")])),
        ("elements", out),
    ])


# ===================================================================================================== GUI
K, W, BG, SHADOW, SLOT, SLOT_DARK = hexc("000000"), hexc("ffffff"), hexc("c6c6c6"), hexc("555555"), hexc("8b8b8b"), hexc("373737")
GUI_W, GUI_H = 176, 198
# Matches StarForgeMenu / StarForgeScreen.
INPUT, CATALYST, FUEL, OUTPUT = (16, 18), (16, 40), (16, 62), (144, 31)
PANEL = (48, 17, 128, 61)
GAUGE = (37, 18, 41, 78)
SKY_XY, SKY_SIZE, ICON_V = (50, 19), 9, 200
GHOST_V = 212
HELP = (162, 5, 7, 9)
INV_Y = 116

PANEL_STARS = [(6, 6, 1), (14, 30, 0), (21, 12, 1), (30, 37, 0), (37, 5, 0), (44, 15, 1), (52, 33, 0), (58, 8, 1), (63, 24, 0),
               (71, 11, 0), (74, 30, 1), (25, 22, 0), (67, 38, 0), (9, 18, 0)]


def gui():
    g = Img(256, 256)
    g.fill(1, 1, GUI_W - 1, GUI_H - 1, BG)
    for x in range(2, GUI_W - 2):
        g.set(x, 0, K)
        g.set(x, GUI_H - 1, K)
    for y in range(2, GUI_H - 2):
        g.set(0, y, K)
        g.set(GUI_W - 1, y, K)
    for x, y in ((1, 1), (GUI_W - 2, 1), (1, GUI_H - 2), (GUI_W - 2, GUI_H - 2)):
        g.set(x, y, K)
    for i in (1, 2):
        for x in range(2, GUI_W - 3):
            g.set(x, i, W)
        for y in range(2, GUI_H - 3):
            g.set(i, y, W)
        for x in range(3, GUI_W - 2):
            g.set(x, GUI_H - 1 - i, SHADOW)
        for y in range(3, GUI_H - 2):
            g.set(GUI_W - 1 - i, y, SHADOW)
    g.set(3, 3, W)
    g.set(GUI_W - 4, GUI_H - 4, SHADOW)

    def recess(x0, y0, x1, y1, fill=None):
        if fill:
            g.fill(x0, y0, x1, y1, fill)
        for x in range(x0, x1):
            g.set(x, y0, SLOT_DARK)
            g.set(x, y1 - 1, W)
        for y in range(y0, y1):
            g.set(x0, y, SLOT_DARK)
            g.set(x1 - 1, y, W)
        g.set(x1 - 1, y0, SLOT)
        g.set(x0, y1 - 1, SLOT)

    def slot(x, y, size=18):
        pad = (size - 16) // 2
        recess(x - pad, y - pad, x - pad + size, y - pad + size, SLOT)

    for x, y in (INPUT, CATALYST, FUEL):
        slot(x, y)
    slot(*OUTPUT, size=26)
    for row in range(3):
        for col in range(9):
            slot(8 + col * 18, INV_Y + row * 18)
    for col in range(9):
        slot(8 + col * 18, INV_Y + 58)

    # The starlight gauge: a glass tube, ticks at each quarter.
    gx0, gy0, gx1, gy1 = GAUGE
    recess(gx0 - 1, gy0 - 1, gx1 + 1, gy1 + 1, hexc("1a1d33"))
    for y in range(gy0 + 15, gy1, 15):
        g.set(gx1 + 1, y, SHADOW)

    # The night-sky panel: a smooth navy-to-violet gradient, a scatter of faint stars, a low horizon.
    px0, py0, px1, py1 = PANEL
    recess(px0, py0, px1, py1)
    for y in range(py0 + 1, py1 - 1):
        t = (y - py0 - 1) / (py1 - py0 - 3)
        c = mixc(N[1], N[3], t ** 1.3)
        for x in range(px0 + 1, px1 - 1):
            g.set(x, y, c)
    for x, y, bright in PANEL_STARS:
        g.set(px0 + x, py0 + y, hexc("8e9ad6") if bright else hexc("4d5794"))
    for x in range(px0 + 1, px1 - 1):
        rise = 1 + int(round(1.2 + 1.2 * math.sin((x - px0) / 9.0) + 0.8 * math.sin((x - px0) / 4.3 + 1.0)))
        for y in range(py1 - 1 - rise, py1 - 1):
            g.set(x, y, S[1] if y > py1 - 1 - rise else S[2])

    # An arrow from the sky to the output.
    ax, ay = px1 + 2, OUTPUT[1] + 4
    for dy, (a, b) in enumerate([(3, 3), (3, 4), (0, 5), (0, 6), (0, 5), (3, 4), (3, 3)]):
        for x in range(a, b + 1):
            g.set(ax + x, ay + dy, SHADOW)

    # The "?" button.
    hx, hy, hw, hh = HELP
    recess(hx - 1, hy - 1, hx + hw + 2, hy + hh + 2, SLOT)

    # Sky icons, in StarForgeBlockEntity.Sky order: day, roofed, no sky, clear, clouded.
    def icon(i, rows, pal):
        for dy, line in enumerate(rows):
            for dx, ch in enumerate(line):
                if ch != ".":
                    g.set(i * SKY_SIZE + dx, ICON_V + dy, pal[ch])
    sun = {"o": hexc("f2c14e"), "r": hexc("b98a2a")}
    icon(0, ["....r....", ".r.....r.", "...ooo...", "..ooooo..", "r.ooooo.r", "..ooooo..", "...ooo...", ".r.....r.", "....r...."], sun)
    roof = {"r": hexc("8a6a52"), "w": hexc("5c4636"), "m": hexc("c8d0e8")}
    icon(1, [".m...m...", "....r....", "...rrr...", "..rrrrr..", ".rrrrrrr.", "..w...w..", "..w...w..", "..w...w..", "..wwwww.."], roof)
    none = {"x": hexc("9a3a3a"), "o": hexc("6a6f8a")}
    icon(2, ["..ooooo..", ".o.....o.", "o.x...x.o", "o..x.x..o", "o...x...o", "o..x.x..o", "o.x...x.o", ".o.....o.", "..ooooo.."], none)
    moon = {"m": hexc("eef4ff"), "s": hexc("aebce8"), "*": hexc("8be6ff")}
    icon(3, ["...mmm..*", "..mms....", ".mms.....", ".mms.....", ".mms....*", ".mmms....", "..mmmsss.", "...mmmm..", "........."], moon)
    cloud = {"m": hexc("eef4ff"), "s": hexc("aebce8"), "c": hexc("8d93a8"), "d": hexc("6c7186")}
    icon(4, ["...mmm...", "..mms....", ".mms.....", ".mms.cc..", ".mmcccccc", "..cccccdd", ".cccccccd", "..ddddddd", "........."], cloud)

    # Ghost icons for the empty slots: gear (a sword), a catalyst (a crystal), a shard.
    def ghost(i, rows):
        for dy, line in enumerate(rows):
            for dx, ch in enumerate(line):
                if ch == "#":
                    g.set(i * 16 + dx, GHOST_V + dy, hexc("7a7a7a"))
    ghost(0, ["................", "............###.", "...........#..#.", "..........#..#..", ".........#..#...", "........#..#....",
              ".......#..#.....", "..#...#..#......", "...#.#..#.......", "....#..#........", "....##.#........", "...#..#.........",
              "..#..#.#........", ".#..#...........", ".###............", "................"])
    ghost(1, ["................", ".......##.......", "......#..#......", ".....#....#.....", "....#......#....", "...#........#...",
              "..#..........#..", "..#..........#..", "...#........#...", "....#......#....", ".....#....#.....", "......#..#......",
              ".......##.......", "................", "................", "................"])
    ghost(2, ["................", "................", ".......#........", ".......#........", "......#.#.......", ".....#...#......",
              "..###.....###...", ".....#...#......", "......#.#.......", ".......#........", ".......#........", "................",
              "................", "................", "................", "................"])
    return g


# ===================================================================================================== data
LANG = OrderedDict([
    ("block.mymod.star_forge", "Star Forge"),
    ("block.mymod.star_forge.desc", "Infuses gear with starlight and a catalyst"),
    ("block.mymod.star_forge.rule", "Works only under an open night sky"),
    ("block.mymod.star_forge.shift", "Hold Shift for the catalysts"),
    ("block.mymod.star_forge.guide", "Catalysts:"),
    ("block.mymod.star_forge.guide.line", "%s → %s (%s)"),
    ("block.mymod.star_forge.guide.or", " or "),
    ("container.mymod.star_forge", "Star Forge"),
    ("container.mymod.star_forge.place_gear", "Place gear to infuse"),
    ("container.mymod.star_forge.not_gear", "Can't infuse that"),
    ("container.mymod.star_forge.add_catalyst", "Add a catalyst"),
    ("container.mymod.star_forge.not_a_catalyst", "Not a catalyst"),
    ("container.mymod.star_forge.wont_take", "%s won't take on this"),
    ("container.mymod.star_forge.already", "Already %s"),
    ("container.mymod.star_forge.replaces", "Replaces %s!"),
    ("container.mymod.star_forge.replaces_warning", "This replaces its %s infusion"),
    ("container.mymod.star_forge.dormant", "Dormant"),
    ("container.mymod.star_forge.needs_starlight", "Add shards"),
    ("container.mymod.star_forge.output_full", "Output full"),
    ("container.mymod.star_forge.starlight", "Starlight: %s%%"),
    ("container.mymod.star_forge.starlight.hint", "Star crystal shards burn as starlight"),
    ("container.mymod.star_forge.cost", "Uses one catalyst and %s shards"),
    ("container.mymod.star_forge.takes", "Takes: %s"),
    ("container.mymod.star_forge.sky.day", "Dormant: it's day"),
    ("container.mymod.star_forge.sky.roofed", "Dormant: no open sky above"),
    ("container.mymod.star_forge.sky.no_sky", "Dormant: no stars here"),
    ("container.mymod.star_forge.sky.clear", "Starlit: drawing starlight"),
    ("container.mymod.star_forge.sky.clouded", "Starlit, but clouded: half speed"),
    ("container.mymod.star_forge.sky.rule", "Needs night and open sky above it (glass is fine)"),
    ("infusion.mymod.title", "✦ %s"),
    ("infusion.mymod.takes.all", "any gear"),
    ("infusion.mymod.takes.melee", "weapons"),
    ("infusion.mymod.takes.gun", "guns"),
    ("infusion.mymod.takes.tool", "tools"),
    ("infusion.mymod.takes.drill", "drills"),
    ("infusion.mymod.takes.armor", "armor"),
    ("infusion.mymod.takes.rod", "rods"),
    ("infusion.mymod.starlit", "Starlit"),
    ("infusion.mymod.starlit.melee", "+15% damage at night"),
    ("infusion.mymod.starlit.gun", "+15% damage at night"),
    ("infusion.mymod.starlit.tool", "+25% digging speed at night"),
    ("infusion.mymod.starlit.armor", "-4% damage taken at night"),
    ("infusion.mymod.starlit.rod", "Sooner bites and rarer fish at night"),
    ("infusion.mymod.featherweight", "Featherweight"),
    ("infusion.mymod.featherweight.melee", "+5% speed while held"),
    ("infusion.mymod.featherweight.gun", "+5% speed held, 15% faster reloads"),
    ("infusion.mymod.featherweight.tool", "+5% speed while held"),
    ("infusion.mymod.featherweight.armor", "+2.5% speed"),
    ("infusion.mymod.featherweight.rod", "+5% speed held, reels 10% faster"),
    ("infusion.mymod.voidtouched", "Voidtouched"),
    ("infusion.mymod.voidtouched.melee", "30% of each hit ignores armor"),
    ("infusion.mymod.voidtouched.gun", "30% of each round ignores armor"),
    ("infusion.mymod.voidtouched.tool", "30% chance to keep durability"),
    ("infusion.mymod.voidtouched.armor", "30% chance to keep durability"),
    ("infusion.mymod.voidtouched.rod", "30% chance to keep durability"),
    ("infusion.mymod.soulbound", "Soulbound"),
    ("infusion.mymod.soulbound.melee", "Heals you 10% of the damage dealt"),
    ("infusion.mymod.soulbound.gun", "Heals you 5% of a hit, twice a second"),
    ("infusion.mymod.soulbound.tool", "Kept on death"),
    ("infusion.mymod.soulbound.armor", "Kept on death"),
    ("infusion.mymod.soulbound.rod", "Kept on death"),
    ("infusion.mymod.fae_kissed", "Fae-Kissed"),
    ("infusion.mymod.fae_kissed.melee", "25% chance of an extra drop from kills"),
    ("infusion.mymod.fae_kissed.gun", "25% chance of an extra drop from kills"),
    ("infusion.mymod.fae_kissed.tool", "20% chance of extra ore drops"),
    ("infusion.mymod.fae_kissed.armor", "+0.5 luck"),
    ("infusion.mymod.fae_kissed.rod", "Bites lean toward rare fish"),
    ("infusion.mymod.rimed", "Rimed"),
    ("infusion.mymod.rimed.melee", "Slows targets for 2s"),
    ("infusion.mymod.rimed.gun", "Slows targets for 1.5s"),
    ("infusion.mymod.rimed.armor", "-25% freezing damage"),
    ("infusion.mymod.molten", "Molten"),
    ("infusion.mymod.molten.melee", "Sets targets alight for 3s"),
    ("infusion.mymod.molten.gun", "Sets targets alight for 2s"),
    ("infusion.mymod.molten.tool", "Smelts what it mines"),
    ("infusion.mymod.molten.armor", "15% chance to set attackers alight"),
    ("infusion.mymod.consecrated", "Consecrated"),
    ("infusion.mymod.consecrated.melee", "×1.3 damage to the evil, holy fire"),
    ("infusion.mymod.consecrated.gun", "×1.3 damage to the evil, holy fire"),
    ("infusion.mymod.consecrated.armor", "-5% damage from the evil"),
    ("infusion.mymod.drakehide", "Drakehide"),
    ("infusion.mymod.drakehide.armor", "-10% fire and explosion damage"),
    ("infusion.mymod.irradiated", "Irradiated"),
    ("infusion.mymod.irradiated.melee", "Weakens and irradiates; doses you unless shielded"),
    ("infusion.mymod.irradiated.gun", "Weakens and irradiates; doses you unless shielded"),
])


def merge_lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path) as f:
        lang = json.load(f, object_pairs_hook=OrderedDict)
    lang.update(LANG)
    with open(path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


def data():
    # Blockstate: facing x starlit x forging (forging uses the starlit model; it only glows brighter).
    variants = OrderedDict()
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for starlit in ("false", "true"):
            for forging in ("false", "true"):
                v = OrderedDict([("model", "mymod:block/star_forge_lit" if starlit == "true" else "mymod:block/star_forge")])
                if y:
                    v["y"] = y
                variants[f"facing={facing},forging={forging},starlit={starlit}"] = v
    write(os.path.join(ASSETS, "blockstates", "star_forge.json"), {"variants": variants})
    write(os.path.join(ASSETS, "items", "star_forge.json"), {"model": {"type": "minecraft:model", "model": "mymod:block/star_forge_lit"}})
    write(os.path.join(DATA, "mymod", "recipe", "star_forge.json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", "misc"),
        ("key", OrderedDict([("S", "mymod:star_steel_ingot"), ("C", "mymod:star_crystal_block"), ("T", "mymod:tungsten_carbide"),
                             ("F", "mymod:foundry"), ("M", "mymod:meteorite_bricks")])),
        ("pattern", ["SCS", "TFT", "MMM"]),
        ("result", OrderedDict([("count", 1), ("id", "mymod:star_forge")])),
    ]))
    write(os.path.join(DATA, "mymod", "advancement", "recipes", "misc", "star_forge.json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_star_steel", {"conditions": {"items": [{"items": "mymod:star_steel_ingot"}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_star_crystal", {"conditions": {"items": [{"items": "mymod:star_crystal_shard"}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "mymod:star_forge"}, "trigger": "minecraft:recipe_unlocked"}),
        ])),
        ("requirements", [["has_star_steel", "has_star_crystal", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:star_forge"]}),
    ]))
    write(os.path.join(DATA, "mymod", "loot_table", "blocks", "star_forge.json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                                ("entries", [{"type": "minecraft:item", "name": "mymod:star_forge"}]), ("rolls", 1)])]),
        ("random_sequence", "mymod:blocks/star_forge"),
    ]))
    tag = os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json")
    with open(tag) as f:
        t = json.load(f, object_pairs_hook=OrderedDict)
    if "mymod:star_forge" not in t["values"]:
        t["values"].append("mymod:star_forge")
        write(tag, t)


# ===================================================================================================== preview
# Texel quads drawn with a depth buffer: Minecraft's flat face shading (up 1.0, down 0.5, north/south 0.8, east/west
# 0.6) for shaded faces, full bright for the glowing ones in the lit model.

FACE_CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def model_quads(m, atlases):
    out = []
    for e in m["elements"]:
        rot = element_rotation(e)
        glow = e.get("light_emission", 0) > 0
        for face, f in e["faces"].items():
            tex = atlases[f["texture"]]
            A, B, Cc, Dd = FACE_CORNERS[face](e["from"], e["to"])
            u0, v0, u1, v1 = [x * ATLAS / 16.0 for x in f["uv"]]
            nu, nv = max(1, int(round(abs(u1 - u0)))), max(1, int(round(abs(v1 - v0))))
            for j in range(nv):
                for i in range(nu):
                    tx = int(math.floor(u0 + (u1 - u0) * (i + 0.5) / nu))
                    ty = int(math.floor(v0 + (v1 - v0) * (j + 0.5) / nv))
                    col = tex.get(min(ATLAS - 1, tx), min(ATLAS - 1, ty))
                    if not col[3]:
                        continue
                    cs = []
                    for s, t in ((i / nu, j / nv), ((i + 1) / nu, j / nv), ((i + 1) / nu, (j + 1) / nv), (i / nu, (j + 1) / nv)):
                        top = [A[q] + (B[q] - A[q]) * s for q in range(3)]
                        bot = [Dd[q] + (Cc[q] - Dd[q]) * s for q in range(3)]
                        cs.append([top[q] + (bot[q] - top[q]) * t for q in range(3)])
                    n = NORMAL[face]
                    if rot:
                        mtx, o = rot
                        cs = [[v + o[q] for q, v in enumerate(apply(mtx, [p[q] - o[q] for q in range(3)]))] for p in cs]
                        n = apply(mtx, n)
                    cs = [tuple((p[q] / 16.0 - 0.5) for q in range(3)) for p in cs]
                    out.append((cs, col, glow, n))
    return out


def view_34(p, yaw=35.0, pitch=28.0):
    """A camera yaw degrees round from looking straight at the front (positive: from the front's left, so the window is
    on the left and the west side on the right) and pitch degrees above: (screen x, screen y, depth)."""
    a, b = math.radians(yaw), math.radians(pitch)
    f = (math.sin(a) * math.cos(b), -math.sin(b), math.cos(a) * math.cos(b))
    right = (-math.cos(a), 0.0, math.sin(a))
    up = (right[1] * f[2] - right[2] * f[1], right[2] * f[0] - right[0] * f[2], right[0] * f[1] - right[1] * f[0])
    dot = lambda u, v: u[0] * v[0] + u[1] * v[1] + u[2] * v[2]
    return (dot(p, right), dot(p, up), dot(p, f))


def view_front(p):
    return view_34(p, 0.0, 10.0)


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


def render(img, quads, view, cx, cy, scale, light, ss=3):
    """light(colour, glow, face shade) -> colour: the day or night lighting. Drawn ss times over and averaged down,
    so edges don't stair-step."""
    x0, y0 = int(cx - scale * 0.95), int(cy - scale * 1.0)
    w, h = int(scale * 1.9), int(scale * 1.9)
    big = Img(w * ss, h * ss)
    depth = {}
    for corners, col, glow, n in quads:
        sp = [view(p) for p in corners]
        k = 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] > 0 else 0.5) * n[1] ** 2
        c = light(col, glow, 1.0 if glow else k)
        pts = [((cx - x0 + q[0] * scale) * ss, (cy - y0 - q[1] * scale) * ss) for q in sp]
        # Depth across the quad's plane, so faces meeting at an edge sort per pixel.
        (ax, ay), (bx, by), (qx, qy) = pts[0], pts[1], pts[3]
        det = (bx - ax) * (qy - ay) - (qx - ax) * (by - ay)
        if abs(det) < 1e-9:
            continue
        za, zb, zq = sp[0][2], sp[1][2], sp[3][2]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        for py in range(max(0, int(min(ys))), min(big.h, int(math.ceil(max(ys))) + 1)):
            for px in range(max(0, int(min(xs))), min(big.w, int(math.ceil(max(xs))) + 1)):
                u = ((px + 0.5 - ax) * (qy - ay) - (qx - ax) * (py + 0.5 - ay)) / det
                v = ((bx - ax) * (py + 0.5 - ay) - (px + 0.5 - ax) * (by - ay)) / det
                z = za + (zb - za) * u + (zq - za) * v
                if inside(pts, px + 0.5, py + 0.5) and z < depth.get((px, py), 1e9):
                    depth[(px, py)] = z
                    big.px[py * big.w + px] = c
    for y in range(h):
        for x in range(w):
            acc, n = [0, 0, 0], 0
            for dy in range(ss):
                for dx in range(ss):
                    c = big.px[(y * ss + dy) * big.w + x * ss + dx]
                    if c[3]:
                        acc = [acc[i] + c[i] for i in range(3)]
                        n += 1
            if n:
                a = int(round(255 * n / (ss * ss)))
                img.set(x0 + x, y0 + y, (acc[0] // n, acc[1] // n, acc[2] // n, a))


def day_light(col, glow, k):
    return shade(col, k)


def night_light(col, glow, k):
    if glow:
        return col
    # Block light 9 from the forge itself, a cool moonlit cast.
    c = shade(col, k * 0.62)
    return (int(c[0] * 0.92), int(c[1] * 0.95), min(255, int(c[2] * 1.08 + 4)), 255)


# --- Minecraft's font, read from the client jar ---------------------------------------------------------------

class Font:
    def __init__(self):
        sys_argv = sys.argv
        sys.argv = sys.argv[:1]
        try:
            from gen_longswords import client_jar, read_png
            import zipfile
            with zipfile.ZipFile(client_jar()) as z:
                w, h, px = read_png(z.read("assets/minecraft/textures/font/ascii.png"))
        finally:
            sys.argv = sys_argv
        self.w, self.px = w, px
        self.cell = w // 16
        self.widths = {}
        for ch in range(256):
            gx, gy = (ch % 16) * self.cell, (ch // 16) * self.cell
            right = -1
            for x in range(self.cell):
                if any(px[(gy + y) * w + gx + x][3] for y in range(self.cell)):
                    right = x
            self.widths[ch] = right + 2 if right >= 0 else 4
        self.widths[32] = 4

    def width(self, s):
        return sum(self.glyph_width(ch) for ch in s)

    def glyph_width(self, ch):
        if ch == "✦":
            return 6
        o = ord(ch)
        return self.widths.get(o, 6) if o < 256 else 6

    def draw(self, img, x, y, s, col, shadow=True, k=1):
        for dx, dy, c in (((k, k, shade(col, 0.25)),) if shadow else ()) + ((0, 0, col),):
            cx = x
            for ch in s:
                self._glyph(img, cx + dx, y + dy, ch, c, k)
                cx += self.glyph_width(ch) * k
        return x + self.width(s) * k

    def _glyph(self, img, x, y, ch, c, k):
        if ch == "✦":
            for gx, gy in ((2, 0), (2, 1), (1, 2), (2, 2), (3, 2), (0, 3), (1, 3), (2, 3), (3, 3), (4, 3), (1, 4), (2, 4), (3, 4),
                           (2, 5), (2, 6)):
                img.fill(x + gx * k, y + gy * k, x + (gx + 1) * k, y + (gy + 1) * k, c)
            return
        o = ord(ch) if ord(ch) < 256 else ord("?")
        if ch == "×":
            o = ord("x")
        gx0, gy0 = (o % 16) * self.cell, (o // 16) * self.cell
        for gy in range(self.cell):
            for gx in range(self.cell):
                if self.px[(gy0 + gy) * self.w + gx0 + gx][3]:
                    img.fill(x + gx * k, y + gy * k, x + (gx + 1) * k, y + (gy + 1) * k, c)


def read_tex(path):
    sys_argv = sys.argv
    sys.argv = sys.argv[:1]
    try:
        from gen_longswords import read_png
    finally:
        sys.argv = sys_argv
    with open(path, "rb") as f:
        w, h, px = read_png(f.read())
    img = Img(w, h)
    img.px = list(px)
    return img


# Mirrors StarForgeScreen's constellations (panel pixels: stars in lighting order, then lines).
CONSTELLATIONS = {
    "molten": ([(20, 39), (31, 27), (37, 31), (41, 11), (46, 29), (52, 24), (62, 39)], [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6)]),
    "fae_kissed": ([(40, 22), (26, 9), (17, 21), (28, 34), (54, 9), (63, 21), (52, 34)],
                   [(0, 1), (1, 2), (2, 3), (3, 0), (0, 4), (4, 5), (5, 6), (6, 0)]),
    "starlit": ([(14, 31), (25, 26), (36, 27), (46, 21), (56, 30), (67, 33), (70, 19)],
                [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 3)]),
}
COLOURS = {"starlit": hexc("b4c6ff"), "featherweight": hexc("e6f59a"), "voidtouched": hexc("b26bff"), "soulbound": hexc("3ed8c3"),
           "fae_kissed": hexc("ff8fd8"), "rimed": hexc("bdf0ff"), "molten": hexc("ff8c3a"), "consecrated": hexc("ffd45e"),
           "drakehide": hexc("e0484e"), "irradiated": hexc("8cff4a")}
TEXT_GREY, DARK = hexc("404040"), hexc("404040")


def wrap(font, text, width):
    lines, line = [], ""
    for word in text.split(" "):
        trial = word if not line else line + " " + word
        if font.width(trial) <= width:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + [line]


def screen_mockup(font, gui_img, progress, infusion, items, summary, details, sky, hold=None):
    """The Star Forge screen as StarForgeScreen draws it, at 1x. items: {(x, y): (texture, count)}; summary and
    details: (text, colour); sky: the Sky ordinal (3 clear night, 0 day)."""
    img = Img(GUI_W, GUI_H)
    for y in range(GUI_H):
        for x in range(GUI_W):
            img.px[y * GUI_W + x] = gui_img.get(x, y)
    px0, py0, px1, py1 = PANEL
    works = sky >= 3
    if works:
        for i, (sx, sy) in enumerate([(9, 9), (40, 31), (66, 5)]):
            img.set(px0 + 3 + sx, py0 + 3 + sy, hexc("dce8ff"))
    if infusion in CONSTELLATIONS:
        stars, segs = CONSTELLATIONS[infusion]
        col = COLOURS[infusion]
        lit = progress * len(stars)
        for a_i, b_i in segs:
            a, b = stars[a_i], stars[b_i]
            drawn = max(0.0, min(1.0, lit - max(a_i, b_i)))
            steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
            for i in range(1, int(round(steps * drawn))):
                x = px0 + a[0] + int(round((b[0] - a[0]) * i / steps))
                y = py0 + a[1] + int(round((b[1] - a[1]) * i / steps))
                img.set(x, y, col[:3] + (170,))
        for i, (sx, sy) in enumerate(stars):
            x, y = px0 + sx, py0 + sy
            if lit > i:
                img.fill(x - 1, y, x + 2, y + 1, col)
                img.fill(x, y - 1, x + 1, y + 2, col)
                img.set(x, y, hexc("ffffff"))
            else:
                img.set(x, y, col[:3] + (110,))
    if not works:
        img.fill(px0 + 1, py0 + 1, px1 - 1, py1 - 1, (0x10, 0x10, 0x18, 0x99))
    for y in range(SKY_SIZE):
        for x in range(SKY_SIZE):
            c = gui_img.get(sky * SKY_SIZE + x, ICON_V + y)
            if c[3]:
                img.set(SKY_XY[0] + x, SKY_XY[1] + y, c)
    if works:
        gx0, gy0, gx1, gy1 = GAUGE
        level = int(math.ceil(0.6 * (gy1 - gy0)))
        for y in range(gy1 - level, gy1):
            t = (y - (gy1 - level)) / max(1, level - 1)
            img.fill(gx0, y, gx1, y + 1, mixc(hexc("f4fbff"), hexc("5e7fe0"), t))
    for (sx, sy), (tex, count) in items.items():
        img.paste(tex, sx, sy)
        if count > 1:
            font.draw(img, sx + 17 - font.width(str(count)), sy + 9, str(count), hexc("ffffff"), shadow=True)
    font.draw(img, (GUI_W - font.width("Star Forge")) // 2, 6, "Star Forge", TEXT_GREY, shadow=False)
    font.draw(img, 8, GUI_H - 94, "Inventory", TEXT_GREY, shadow=False)
    font.draw(img, HELP[0] + 1, HELP[1] + 1, "?", TEXT_GREY, shadow=False)
    if hold:
        font.draw(img, (px0 + px1 - font.width(hold)) // 2, (py0 + py1) // 2 - 4, hold, hexc("b8c6f0"), shadow=True)
    font.draw(img, 48, 64, summary[0], shade(summary[1], 0.55) if summary[0].startswith("✦") else summary[1], shadow=False)
    wrapped = []
    for text, colour in details:
        wrapped += [(line, colour) for line in wrap(font, text, 124)]
    for i, (text, colour) in enumerate(wrapped[:3]):
        font.draw(img, 48, 74 + i * 10, text, colour, shadow=False)
    return img


def tooltip(font, lines, k=2):
    """A vanilla tooltip box: (text, colour) lines, the first one the title."""
    w = max(sum(font.width(t) for t, _ in (line if isinstance(line, list) else [line])) for line in lines) + 8
    h = 8 + 10 * len(lines) + 2
    img = Img(w, h, CLEAR)
    bg = hexc("100010", 240)
    img.fill(1, 0, w - 1, h, bg)
    img.fill(0, 1, w, h - 1, bg)
    for y in range(1, h - 1):
        t = y / (h - 1)
        img.set(1, y, mixc(hexc("5000ff"), hexc("28007f"), t))
        img.set(w - 2, y, mixc(hexc("5000ff"), hexc("28007f"), t))
    for x in range(1, w - 1):
        img.set(x, 1, hexc("5000ff"))
        img.set(x, h - 2, hexc("28007f"))
    y = 4
    for i, line in enumerate(lines):
        x = 4
        for text, colour in (line if isinstance(line, list) else [line]):
            x = font.draw(img, x, y, text, colour)
        y += 12 if i == 0 else 10
    return img


def label(img, font, x, y, s, k=2, colour=hexc("e9eff5")):
    font.draw(img, x, y, s, colour, shadow=True, k=k)


def night_sky(img, x0, y0, x1, y1):
    for y in range(y0, y1):
        t = (y - y0) / (y1 - y0)
        img.fill(x0, y, x1, y + 1, mixc(hexc("070a1c"), hexc("1b1d4a"), t))
    for i in range(60):
        x = x0 + (i * 977) % (x1 - x0)
        y = y0 + (i * 613) % int((y1 - y0) * 0.7)
        img.set(x, y, hexc("c9d4ff") if i % 3 else hexc("6c76b8"))
    img.fill(x0, y1 - 40, x1, y1, hexc("1a2617"))


def day_sky(img, x0, y0, x1, y1):
    for y in range(y0, y1):
        t = (y - y0) / (y1 - y0)
        img.fill(x0, y, x1, y + 1, mixc(hexc("78a7ff"), hexc("c0d8ff"), t))
    img.fill(x0, y1 - 40, x1, y1, hexc("5d8f3a"))


def preview(path, elements, atlases_lit, atlases_dark, gui_img):
    font = Font()
    Wd, Ht = 1500, 1560
    sheet = Img(Wd, Ht, hexc("24272e"))
    lit_q = model_quads(model(elements, True), atlases_lit)
    dark_q = model_quads(model(elements, False), atlases_dark)

    # Row 1: day (dormant) and night (starlit) at three quarters, and the night front.
    S = 300
    panels = [("BY DAY OR UNDER A ROOF: DORMANT", day_sky, dark_q, view_34, day_light),
              ("UNDER THE NIGHT SKY: STARLIT", night_sky, lit_q, view_34, night_light),
              ("NIGHT, FRONT", night_sky, lit_q, view_front, night_light)]
    for i, (title, sky, quads, view, light) in enumerate(panels):
        x0 = 20 + i * 490
        sky(sheet, x0, 50, x0 + 470, 520)
        render(sheet, quads, view, x0 + 235, 300, S, light)
        label(sheet, font, x0, 20, title)

    # Row 2: the screen mid-infusion (3x), a dormant one replacing an infusion (2x), and infused-gear tooltips.
    item = lambda n: read_tex(os.path.join(ASSETS, "textures", "item", n + ".png"))
    grey, gold = hexc("555555"), hexc("c06a00")
    shot = screen_mockup(font, gui_img, 0.62, "fae_kissed",
                         {INPUT: (item("star_steel_sword"), 1), CATALYST: (item("fae_dust"), 3), FUEL: (item("star_crystal_shard"), 12)},
                         ("✦ Fae-Kissed", COLOURS["fae_kissed"]), [("25% chance of an extra drop from kills", grey)], 3)
    label(sheet, font, 20, 545, "SCREEN: FAE-KISSED, 62% DONE (3X)")
    sheet.paste(shot, 20, 575, 3)
    shot2 = screen_mockup(font, gui_img, 0.0, "molten",
                          {INPUT: (item("star_steel_helmet"), 1), CATALYST: (item("magmasteel_ingot"), 1), FUEL: (item("star_crystal_shard"), 4)},
                          ("✦ Molten", COLOURS["molten"]), [("Replaces Rimed!", gold), ("15% chance to set attackers alight", grey)], 0,
                          hold="Dormant")
    label(sheet, font, 580, 545, "BY DAY, REPLACING (2X)")
    sheet.paste(shot2, 580, 575, 2)
    label(sheet, font, 580, 990, "SKY ICONS: DAY ROOF NONE CLEAR CLOUDED", k=1)
    for i in range(5):
        icon = Img(SKY_SIZE, SKY_SIZE)
        for y in range(SKY_SIZE):
            for x in range(SKY_SIZE):
                icon.px[y * SKY_SIZE + x] = gui_img.get(i * SKY_SIZE + x, ICON_V + y)
        sheet.fill(580 + i * 44, 1005, 580 + i * 44 + 40, 1045, hexc("c6c6c6"))
        sheet.paste(icon, 584 + i * 44, 1009, 4)

    tips = [
        [("Star Steel Sword", COLOURS["fae_kissed"]), ("✦ Fae-Kissed", COLOURS["fae_kissed"]),
         (" 25% chance of an extra drop from kills", hexc("aaaaaa")), ("", hexc("aaaaaa")), ("When in Main Hand:", hexc("aaaaaa")),
         (" 11 Attack Damage", hexc("00aa00"))],
        [("Star Steel Helmet", COLOURS["drakehide"]), ("✦ Drakehide", COLOURS["drakehide"]),
         (" -10% fire and explosion damage", hexc("aaaaaa"))],
        [("Star Drill", COLOURS["molten"]), ("✦ Molten", COLOURS["molten"]), (" Smelts what it mines", hexc("aaaaaa"))],
        [("M4", COLOURS["voidtouched"]), ("✦ Voidtouched", COLOURS["voidtouched"]), (" 30% of each round ignores armor", hexc("aaaaaa"))],
        [("Star Steel Rod", COLOURS["starlit"]), ("✦ Starlit", COLOURS["starlit"]), (" Sooner bites and rarer fish at night", hexc("aaaaaa"))],
        [("Diamond Axe", COLOURS["soulbound"]), ("✦ Soulbound", COLOURS["soulbound"]), (" Heals you 10% of the damage dealt", hexc("aaaaaa")),
         (" Kept on death", hexc("aaaaaa"))],
    ]
    label(sheet, font, 960, 545, "INFUSED GEAR TOOLTIPS (2X)")
    ty = 575
    for t in tips:
        timg = tooltip(font, t)
        sheet.paste(timg, 960, ty, 2)
        ty += timg.h * 2 + 12

    # Row 3: the block item's guide tooltip.
    guide = [("Star Forge", hexc("55ffff")), ("Infuses gear with starlight and a catalyst", hexc("aaaaaa")),
             ("Works only under an open night sky", hexc("00aaaa")), ("Catalysts:", hexc("ffffff"))]
    for name, inf, takes in (("Block of Star Crystal", "Starlit", "any gear"), ("Alien Alloy", "Featherweight", "any gear"),
                             ("Void Flux", "Voidtouched", "any gear"), ("Soul Charge", "Soulbound", "any gear"),
                             ("Fae Dust", "Fae-Kissed", "any gear"), ("Froststeel Ingot", "Rimed", "weapons, guns, armor"),
                             ("Magmasteel Ingot", "Molten", "weapons, guns, tools, drills, armor"),
                             ("Holy Silver Ingot", "Consecrated", "weapons, guns, armor"),
                             ("Ancient Dragon Scale or Ender Dragon Scale", "Drakehide", "armor"),
                             ("Enriched Uranium", "Irradiated", "weapons, guns")):
        guide.append([(name, hexc("aaaaaa")), (" -> ", hexc("ffffff")), (inf, COLOURS[inf.lower().replace("-", "_")]),
                      (f" ({takes})", hexc("555555"))])
    gimg = tooltip(font, guide)
    label(sheet, font, 20, 1180, "STAR FORGE ITEM WITH SHIFT HELD (2X)")
    sheet.paste(gimg, 20, 1210, 2)
    sheet.save(path)
    print("preview:", path)


# ===================================================================================================== main

def main():
    elements, base, glow_lit, glow_dark = build()
    lit = model(elements, True)
    dark = model(elements, False)
    check_elements("star_forge", lit["elements"])
    write(os.path.join(ASSETS, "models", "block", "star_forge.json"), dark)
    write(os.path.join(ASSETS, "models", "block", "star_forge_lit.json"), lit)
    tex = os.path.join(ASSETS, "textures", "block")
    base.save(os.path.join(tex, "star_forge.png"))
    glow_lit.save(os.path.join(tex, "star_forge_glow.png"))
    glow_dark.save(os.path.join(tex, "star_forge_glow_dormant.png"))
    g = gui()
    g.save(os.path.join(ASSETS, "textures", "gui", "container", "star_forge.png"))
    data()
    merge_lang()
    print("star forge: %d elements, no coplanar faces" % len(elements))
    if PREVIEW:
        preview(os.path.abspath(PREVIEW), elements, {"#base": base, "#glow": glow_lit}, {"#base": base, "#glow": glow_dark}, g)


if __name__ == "__main__":
    main()
