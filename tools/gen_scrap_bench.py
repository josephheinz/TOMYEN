"""Builds the Scrap Bench and Blueprints (com.jheinz.mymod.scrapbench, com.jheinz.mymod.blueprint):

- the bench's block model (a cluttered workbench: a vise with a pipe clamped in it, a pegboard of tools with a blueprint
  pinned to it, a hanging lamp, a parts tray, a spring, a screwdriver, an oil can, a rolled-up schematic, and a crate of
  junk on the shelf below) and its texture atlas, painted face by face at vanilla's density (one texel per model pixel),
  checked for coplanar faces (tools/coplanar.py) before it's written;
- the Spring, Screws and Blueprint sprites (the blueprint in blank, Workshop-blue and Foundry-amber paper);
- the bench's GUI texture, and a fourth input slot on the Weapon Station's;
- block state, item definitions and models, lang;
- data: the bench's crafting recipe and recipe-book advancement, its block loot, the axe tag, the banker's blueprint
  trade, and the removal of crafting-table recipes that moved to the bench (the homemade guns, the metal detector, the
  tripwire alarm, the bear trap, the pipe bomb and scrap armor) or to the Weapon Station (the flare gun).

Usage: python3 tools/gen_scrap_bench.py src/main/resources/assets/mymod [--preview preview_scrap_bench.png]"""
import json, math, os, sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import gen_fishing_rods as R      # Img, read_png, art, text, the client jar
from gunkit import png
import coplanar

ASSETS = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else os.path.join(REPO, "src/main/resources/assets/mymod")
PREVIEW = os.path.abspath(sys.argv[sys.argv.index("--preview") + 1]) if "--preview" in sys.argv else None
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
TEX = os.path.join(ASSETS, "textures")
CLEAR = (0, 0, 0, 0)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def ramp(*cs):
    return [hexc(c) for c in cs]


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


class Img(R.Img):
    def save(self, path):
        png(path, self.w, self.h, self.px)


# Palettes: short ramps, darkest first.
OAK = ramp("5e4128", "7a5634", "93683f", "a87b4b", "bd9058", "cfa56c")
WALNUT = ramp("3a2716", "4e3420", "62432a", "765335", "8a6442")
PEG = ramp("5a4129", "7f5e3d", "98734c", "a8835a", "b8946a")
STEEL = ramp("2c3036", "474c54", "656b74", "868c95", "a9afb7", "d0d5da")
GUNMETAL = ramp("1f2226", "33373d", "4a4f57", "60666f")
PAINT = ramp("243649", "324a62", "41607e", "557898", "7393b0")
GREEN = ramp("1d3a22", "2a5230", "386b3e", "4b8650", "66a064")
CREAM = ramp("b9a679", "d6c696", "ecdfb6", "faf1d6")
BULB = ramp("ffd36b", "ffe49a", "fff2c8", "fffbea")
COPPER = ramp("6b3218", "8f4625", "b8653a", "d88a58", "f2b98c")
BRASS = ramp("4a3410", "7d5a1c", "b08a32", "dcb95a", "f3dc94")
RED = ramp("4a1410", "7e2219", "a83226", "cc4a35", "e8775c")
AMBER = ramp("6a3a0a", "a4600f", "d58b1e", "f0b347")
BLUE = ramp("17305a", "21457c", "2f5d9e", "3f73b8", "cfe0f5")
RUSTY = ramp("3b2a20", "5c4434", "7a5d47", "9c7556", "b7895c")
CORD = ramp("16171a", "26282c", "393c42")


# =================================================================================================== block atlas

ATLAS = 64           # texels; uv = texel / 4, so one texel per model pixel
UV = 16.0 / ATLAS


def tsize(d):
    return max(1, int(round(d)))


def face_dims(e, d):
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    if d in ("up", "down"):
        return x1 - x0, z1 - z0
    if d in ("north", "south"):
        return x1 - x0, y1 - y0
    return z1 - z0, y1 - y0


def shade_rows(r, w, h, top=-1, mid=-2, bottom=-3, left_hi=False):
    """A plain shaded face: a bright top row, a darker bottom row (light from above), the rest mid."""
    img = Img(w, h, r[mid])
    for x in range(w):
        if h > 2:
            img.set(x, 0, r[top])
            img.set(x, h - 1, r[bottom])
        elif h == 2:
            img.set(x, 0, r[top])
    if left_hi and w > 2:
        for y in range(1, h - 1):
            img.set(0, y, mixc(r[mid], r[top], 0.5))
    return img


def boards(w, h, r, along_x=True, seam_every=4, nails=True, stains=()):
    """Planks running along x (or along y of the image), a darker seam between boards and soft lengthwise grain."""
    img = Img(w, h)
    n = h if along_x else w
    length = w if along_x else h
    for i in range(n):
        board = i // seam_every
        base = r[3] if board % 2 == 0 else mixc(r[3], r[2], 0.45)
        seam = i % seam_every == seam_every - 1
        for j in range(length):
            c = base
            if seam:
                c = r[1]
            elif i % seam_every == 0:
                c = mixc(base, r[4], 0.35)  # the board's lit edge
            else:
                # Two long grain streaks per board, at fixed offsets so it reads as wood, not noise.
                k = (j + board * 5) % 13
                if i % seam_every == 1 and 2 <= k <= 8:
                    c = mixc(base, r[2], 0.35)
                elif i % seam_every == 2 and 6 <= (j + board * 3) % 11 <= 9:
                    c = mixc(base, r[4], 0.25)
            x, y = (j, i) if along_x else (i, j)
            img.set(x, y, c)
        if nails and not seam and i % seam_every == 1 and length >= 10:
            for j in (1, length - 2):
                x, y = (j, i) if along_x else (i, j)
                img.set(x, y, GUNMETAL[1])
    for (sx, sy, rad, col) in stains:
        for y in range(h):
            for x in range(w):
                d = math.hypot(x + 0.5 - sx, y + 0.5 - sy)
                if rad - 0.75 <= d <= rad + 0.25:
                    img.set(x, y, mixc(img.get(x, y), col, 0.55))
    return img


def bench_top():
    """The work surface: four boards, nail heads at their ends, an oil ring under the can and a scorch by the vise."""
    img = boards(16, 16, OAK, stains=((13.0, 11.5, 2.0, OAK[0]),))
    for x, y in ((7, 5), (8, 5), (7, 6), (8, 6), (9, 6), (8, 7)):
        img.set(x, y, mixc(img.get(x, y), OAK[0], 0.45))
    return img


def end_grain(w, h, r, seam_every=4):
    """The ends of the boards: short segments with a seam between and a pale ring in each."""
    img = Img(w, h)
    for x in range(w):
        for y in range(h):
            seam = x % seam_every == seam_every - 1
            c = r[1] if seam else (r[3] if y == 0 else r[2])
            if not seam and x % seam_every == 1 and y == h - 1:
                c = mixc(r[2], r[4], 0.4)
            img.set(x, y, c)
    return img


def post(w, h, r):
    """A leg or post: a lit left column, a dark right one and a lengthwise grain line."""
    img = Img(w, h, r[2])
    for y in range(h):
        if w >= 2:
            img.set(0, y, r[3])
            img.set(w - 1, y, r[1])
        if w >= 4:
            img.set(w // 2, y, mixc(r[2], r[1], 0.5) if (y // 3) % 2 == 0 else r[2])
    for x in range(w):
        img.set(x, 0, mixc(img.get(x, 0), r[0], 0.35))
    return img


def pegboard_front(w, h):
    img = Img(w, h, PEG[3])
    for y in range(h):
        for x in range(w):
            if x % 3 == 1 and y % 3 == 1:
                img.set(x, y, PEG[0])
                if x + 1 < w:
                    img.set(x + 1, y, PEG[2])
            if y == 0:
                img.set(x, y, PEG[4])
            elif y == h - 1:
                img.set(x, y, PEG[2])
    return img


def crate_side(w, h):
    img = Img(w, h)
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1):
                c = WALNUT[2] if x == 0 else WALNUT[1]
            elif y % 2 == 1:
                c = WALNUT[0]
            else:
                c = OAK[3] if (y // 2) % 2 == 0 else OAK[2]
            img.set(x, y, c)
    return img


def junk_top(w, h):
    """The crate's open top, heaped with scrap: a pipe offcut, a copper coil, a gear and a bolt."""
    g = ["KMMMKC",
         "KLLMKc",
         "KKKDCc",
         "Gg.KCc",
         "gGg.Kb",
         "Gg.bBb",
         ".K.Kb."]
    p = {"K": GUNMETAL[0], "D": GUNMETAL[2], "M": STEEL[2], "L": STEEL[4], "C": COPPER[3], "c": COPPER[1],
         "G": STEEL[3], "g": STEEL[1], "B": BRASS[3], "b": BRASS[1], ".": GUNMETAL[0]}
    img = Img(w, h)
    for y in range(h):
        for x in range(w):
            img.set(x, y, p[g[y % len(g)][x % len(g[0])]])
    return img


def metal(r, w, h, chips=False):
    img = shade_rows(r, w, h, top=-1, mid=-3, bottom=-4 if len(r) > 4 else 0, left_hi=True)
    if chips and w >= 3 and h >= 3:
        img.set(w - 1, 0, STEEL[3])
        img.set(0, h - 1, STEEL[2])
    return img


def coil(d, w, h):
    """A spring lying along z: copper turns with dark gaps between them; the ends show the coil's ring."""
    img = Img(w, h)
    for y in range(h):
        for x in range(w):
            if d in ("north", "south"):
                edge = x in (0, w - 1) or y in (0, h - 1)
                img.set(x, y, COPPER[3] if edge else COPPER[0])
            else:
                along = x if d in ("east", "west") else y
                img.set(x, y, COPPER[3 if (y == 0 and d != "up") else 2] if along % 2 == 0 else COPPER[0])
    return img


def oil_can(d, w, h):
    img = shade_rows(RED, w, h, top=-1, mid=-3, bottom=1, left_hi=True)
    if d not in ("up", "down") and h >= 3:
        band = h // 2
        for x in range(w):
            img.set(x, band, AMBER[3] if x % 3 else AMBER[2])
    if d == "up":
        img = Img(w, h, RED[2])
        for x in range(w):
            img.set(x, 0, RED[4])
    return img


def shade_side(w, h):
    img = Img(w, h, GREEN[2])
    for x in range(w):
        img.set(x, 0, GREEN[4])
        img.set(x, h - 1, GREEN[3])  # the rolled rim
    return img


def shade_inside(w, h):
    img = Img(w, h, CREAM[3])
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                img.set(x, y, CREAM[1])
    return img


def blueprint_sheet(w, h):
    """A schematic pinned to the board: blue paper, a pale border and the outline of a pistol."""
    img = Img(w, h, BLUE[2])
    for x in range(w):
        img.set(x, 0, BLUE[3])
        img.set(x, h - 1, BLUE[1])
    lines = [(1, 1), (2, 1), (3, 1), (1, 2), (1, 3), (2, 2)]
    for x, y in lines:
        if x < w and y < h - 1:
            img.set(x, y, BLUE[4])
    return img


def rolled(d, w, h):
    """A rolled-up blueprint: blue along its length, the spiral of paper at its ends."""
    if d in ("east", "west"):
        img = Img(w, h, BLUE[4])
        if w > 1 and h > 1:
            img.set(w // 2, h // 2, BLUE[2])
        return img
    img = Img(w, h, BLUE[2])
    for x in range(w):
        img.set(x, 0, BLUE[3])
        if x in (1, w - 2):
            for y in range(h):
                img.set(x, y, BLUE[4])
    return img


def handle(r, w, h):
    img = Img(w, h, r[2])
    for y in range(h):
        img.set(0, y, r[3])
    return img


def bulb(w, h):
    img = Img(w, h, BULB[2])
    for y in range(h):
        for x in range(w):
            if 0 < x < w - 1 and 0 < y < h - 1 or w <= 2:
                img.set(x, y, BULB[3])
    return img


def paint(mat, d, w, h):
    side = d not in ("up", "down")
    if mat == "top":
        if d == "up":
            return bench_top()
        if d == "down":
            return boards(w, h, WALNUT[:1] + OAK[:4] + [OAK[3]], nails=False)
        return end_grain(w, h, OAK) if d in ("east", "west") else shade_rows(OAK, w, h, top=4, mid=3, bottom=2)
    if mat == "shelf":
        if d in ("up", "down"):
            return boards(w, h, WALNUT[:5] + [WALNUT[4]], nails=False)
        return shade_rows(WALNUT, w, h, top=4, mid=3, bottom=2)
    if mat == "leg":
        return post(w, h, WALNUT) if side else Img(w, h, WALNUT[1])
    if mat == "drawer":
        img = shade_rows(OAK, w, h, top=4, mid=3, bottom=1)
        if d == "north" and w >= 4:
            for y in range(h):
                img.set(0, y, OAK[1])
                img.set(w - 1, y, OAK[1])
        return img
    if mat == "peg":
        if d == "north":
            return pegboard_front(w, h)
        return Img(w, h, PEG[2]) if d == "south" else Img(w, h, PEG[1])
    if mat == "crate":
        return junk_top(w, h) if d == "up" else crate_side(w, h) if side else Img(w, h, WALNUT[0])
    if mat == "steel":
        return metal(STEEL, w, h)
    if mat == "gunmetal":
        return metal(GUNMETAL, w, h)
    if mat == "paint":
        return metal(PAINT, w, h, chips=True)
    if mat == "pipe":
        # Galvanised pipe: a lengthwise highlight, a patch of rust, dark bore at the ends.
        if d in ("east", "west"):
            img = Img(w, h, STEEL[3])
            if w >= 2 and h >= 2:
                img.fill(w // 2 - (1 if w > 2 else 0), h // 2 - (1 if h > 2 else 0), w // 2 + 1, h // 2 + 1, GUNMETAL[0])
            return img
        img = Img(w, h, STEEL[3])
        for x in range(w):
            img.set(x, 0, STEEL[4])
            if h > 2:
                img.set(x, h - 1, STEEL[2])
        if w >= 6:
            img.set(w - 3, h // 2, RUSTY[3])
            img.set(w - 2, h // 2, RUSTY[2])
        return img
    if mat == "shade":
        return shade_inside(w, h) if d == "down" else Img(w, h, GREEN[3]) if d == "up" else shade_side(w, h)
    if mat == "bulb":
        return bulb(w, h)
    if mat == "cord":
        return Img(w, h, CORD[1])
    if mat == "coil":
        return coil(d, w, h)
    if mat == "brass":
        img = metal(BRASS, w, h)
        if d == "up" and w >= 2 and h >= 2:
            img.set(w // 2, h // 2, BRASS[0])
        return img
    if mat == "oil":
        return oil_can(d, w, h)
    if mat == "amber":
        return handle(AMBER, w, h)
    if mat == "wood":
        return handle(OAK[1:], w, h)
    if mat == "sheet":
        return blueprint_sheet(w, h) if d == "north" else Img(w, h, BLUE[1])
    if mat == "rolled":
        return rolled(d, w, h)
    raise ValueError(mat)


class Atlas:
    """Packs one painted rectangle per distinct (material, face, size) into rows of a square texture."""

    def __init__(self, size):
        self.size, self.img = size, Img(size, size)
        self.x = self.y = self.row = 0
        self.cache = {}

    def place(self, key, picture):
        if key in self.cache:
            return self.cache[key]
        w, h = picture.w, picture.h
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > self.size:
            raise SystemExit("bench atlas is full")
        self.img.paste(picture, self.x, self.y)
        rect = (self.x, self.y, w, h)
        self.x += w
        self.row = max(self.row, h)
        self.cache[key] = rect
        return rect


def box(name, frm, to, mat, skip=(), rot=None, emit=False, cull=()):
    """An element: faces in `skip` are left out (they're against something), `cull` faces cull against neighbours."""
    e = OrderedDict([("name", name), ("from", list(frm)), ("to", list(to))])
    if rot:
        e["rotation"] = OrderedDict([("angle", rot[0]), ("axis", rot[1]), ("origin", list(rot[2]))])
    if emit:
        e["light_emission"] = 15
    e["faces"] = OrderedDict((d, {"mat": mat}) for d in ("north", "east", "south", "west", "up", "down") if d not in skip)
    for d in cull:
        if d in e["faces"]:
            e["faces"][d]["cullface"] = d
    return e


def bench_elements():
    E = []
    # --- The table: a thick top on four legs, side and back rails, a drawer at the front and a shelf below.
    E.append(box("top", (0, 12, 0), (16, 14, 16), "top", cull=("north", "east", "south", "west")))
    for name, x, z in (("leg_fl", 1, 1), ("leg_fr", 13, 1), ("leg_bl", 1, 13), ("leg_br", 13, 13)):
        E.append(box(name, (x, 0, z), (x + 2, 12, z + 2), "leg", skip=("up",), cull=("down",)))
    E.append(box("rail_w", (1.5, 10, 3), (2.5, 12, 13), "leg", skip=("up", "north", "south")))
    E.append(box("rail_e", (13.5, 10, 3), (14.5, 12, 13), "leg", skip=("up", "north", "south")))
    E.append(box("rail_b", (3, 10, 13.5), (13, 12, 14.5), "leg", skip=("up", "east", "west")))
    E.append(box("drawer", (4, 9, 1), (12, 12, 8), "drawer", skip=("up",)))
    E.append(box("drawer_pull", (7, 10.25, 0.25), (9, 10.75, 1), "steel", skip=("south",)))
    E.append(box("shelf", (1.5, 2.5, 1.5), (14.5, 3.5, 14.5), "shelf"))
    E.append(box("crate", (3, 3.5, 4.5), (9, 7.5, 11.5), "crate", skip=("down",)))
    E.append(box("paint_can", (10.5, 3.5, 6), (13, 6.5, 8.5), "oil", skip=("down",)))

    # --- The vise, bolted to the front left corner with a length of pipe in its jaws.
    E.append(box("vise_base", (1.5, 14, 1.75), (6, 14.5, 6.5), "gunmetal", skip=("down",)))
    E.append(box("vise_body", (2.25, 14.5, 3.5), (5.25, 17, 6), "paint", skip=("down",)))
    E.append(box("vise_jaw_back", (2, 17, 3.5), (5.5, 18.75, 4.5), "steel", skip=("down",)))
    E.append(box("vise_slide", (2.75, 15, 1), (4.75, 16.25, 3.5), "paint", skip=("down", "south")))
    E.append(box("vise_front", (2.25, 14.75, 0), (5.25, 17, 1), "paint"))
    E.append(box("vise_jaw_front", (2, 17, 0.25), (5.5, 18.75, 1.25), "steel", skip=("down",)))
    E.append(box("vise_screw", (3.5, 15.5, -2), (4, 16, 0), "steel", skip=("south",)))
    E.append(box("vise_handle", (2, 15.55, -2.2), (5.5, 15.95, -1.8), "steel"))
    E.append(box("vise_knob_l", (1.6, 15.45, -2.3), (2, 16.05, -1.7), "gunmetal"))
    E.append(box("vise_knob_r", (5.5, 15.45, -2.3), (5.9, 16.05, -1.7), "gunmetal"))
    E.append(box("clamped_pipe", (-0.5, 17.25, 1.25), (7.5, 18.75, 3.5), "pipe"))

    # --- The pegboard on two posts at the back, hung with a wrench, a hammer and a blueprint.
    E.append(box("post_l", (0.5, 14, 13.75), (1.5, 24.5, 15.25), "leg", skip=("down",)))
    E.append(box("post_r", (14.5, 14, 13.75), (15.5, 24.5, 15.25), "leg", skip=("down",)))
    E.append(box("pegboard", (1.5, 14, 14), (14.5, 24, 15), "peg", skip=("down", "east", "west")))
    E.append(box("wrench", (2.75, 16.5, 13.6), (3.75, 21.5, 14), "steel", skip=("south",)))
    E.append(box("wrench_jaw", (2.25, 21.5, 13.5), (4.25, 23, 14), "steel", skip=("south",)))
    E.append(box("sheet", (5.5, 16, 13.8), (9.5, 21, 14), "sheet", skip=("south",)))
    E.append(box("hammer_grip", (11.15, 16, 13.5), (11.85, 21.5, 14), "wood", skip=("south",)))
    E.append(box("hammer_head", (10, 21.5, 13.25), (13, 22.75, 14), "gunmetal", skip=("south",)))

    # --- The lamp: a bracket and boom off the top of the board, a cord, a green enamel shade and its bulb.
    E.append(box("lamp_bracket", (6.75, 22.5, 13), (9.25, 24.25, 14), "gunmetal", skip=("south",)))
    E.append(box("lamp_boom", (7.5, 23.25, 7), (8.5, 24, 13), "steel", skip=("south",)))
    E.append(box("lamp_cord", (7.75, 20.25, 7.25), (8.25, 23.25, 7.75), "cord", skip=("up", "down")))
    E.append(box("lamp_cap", (6.75, 19.5, 6.25), (9.25, 20.25, 8.75), "shade", skip=("down",)))
    E.append(box("lamp_shade", (6, 18, 5.5), (10, 19.5, 9.5), "shade"))
    E.append(box("lamp_bulb", (7, 17, 6.5), (9, 18, 8.5), "bulb", skip=("up",), emit=True))

    # --- Clutter on the top.
    E.append(box("tray", (10, 14, 1.5), (14.5, 14.5, 5), "steel", skip=("down",)))
    E.append(box("tray_n", (10, 14.5, 1.5), (14.5, 15.5, 2), "steel", skip=("down",)))
    E.append(box("tray_s", (10, 14.5, 4.5), (14.5, 15.5, 5), "steel", skip=("down",)))
    E.append(box("tray_w", (10, 14.5, 2), (10.5, 15.5, 4.5), "steel", skip=("down", "north", "south")))
    E.append(box("tray_e", (14, 14.5, 2), (14.5, 15.5, 4.5), "steel", skip=("down", "north", "south")))
    E.append(box("nut_a", (11, 14.5, 2.5), (12, 15, 3.5), "brass", skip=("down",)))
    E.append(box("nut_b", (12.5, 14.5, 2.75), (13.5, 15.25, 3.75), "steel", skip=("down",)))
    E.append(box("spring", (11.5, 14, 6.25), (13, 15.5, 9.75), "coil", skip=("down",)))
    E.append(box("oil_can", (12, 14, 10.5), (14.5, 16.5, 13), "oil", skip=("down",)))
    E.append(box("oil_neck", (12.75, 16.5, 11.25), (13.75, 17, 12.25), "gunmetal", skip=("down",)))
    E.append(box("oil_spout", (13.1, 16.6, 9.75), (13.4, 16.9, 11.25), "gunmetal", skip=("south",)))
    pivot = (7.5, 14, 4.5)
    E.append(box("driver_grip", (7, 14, 2), (8, 15, 4.5), "amber", skip=("down",), rot=(22.5, "y", pivot)))
    E.append(box("driver_shaft", (7.3, 14.2, 4.5), (7.7, 14.6, 7.5), "steel", skip=("down", "north"), rot=(22.5, "y", pivot)))
    E.append(box("rolled_plan", (1.5, 14, 8.25), (7, 15.25, 9.5), "rolled", skip=("down",), rot=(-22.5, "y", (4.25, 14, 8.875))))
    return E


def face_key(mat, d, tw, th):
    """Faces that look the same share texels: per direction for directional materials, else flat (up/down) vs side."""
    if mat in ("top", "peg", "crate", "coil", "oil", "shade", "rolled", "sheet", "drawer"):
        return (mat, d, tw, th)
    return (mat, d if d in ("up", "down") else "side", tw, th)


def finish(elements, atlas):
    """Paints every face into the atlas (tallest first, so the rows pack tightly) and gives it its uv and texture."""
    wanted = OrderedDict()
    for e in elements:
        for d, f in e["faces"].items():
            w, h = face_dims(e, d)
            key = face_key(f["mat"], d, tsize(w), tsize(h))
            wanted.setdefault(key, (f["mat"], d))
    for key in sorted(wanted, key=lambda k: (-k[3], -k[2])):
        mat, d = wanted[key]
        atlas.place(key, paint(mat, d, key[2], key[3]))
    for e in elements:
        for d, f in e["faces"].items():
            w, h = face_dims(e, d)
            x, y, pw, ph = atlas.cache[face_key(f.pop("mat"), d, tsize(w), tsize(h))]
            f["uv"] = [round(x * UV, 4), round(y * UV, 4), round((x + pw) * UV, 4), round((y + ph) * UV, 4)]
            f["texture"] = "#tex"
    return elements


def check(elements):
    faces = coplanar.element_faces(elements)
    hits = coplanar.fights(faces)
    if hits:
        for h in hits:
            print("  z-fight:", h)
        sys.exit(f"scrap bench: {len(hits)} coplanar overlapping face pairs")


BENCH_DISPLAY = OrderedDict([
    ("gui", {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.55, 0.55, 0.55]}),
    ("ground", {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}),
    ("fixed", {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.45, 0.45, 0.45]}),
    ("thirdperson_righthand", {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]}),
    ("firstperson_righthand", {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]}),
    ("firstperson_lefthand", {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]}),
])


def build_bench():
    elements = bench_elements()
    check(elements)
    atlas = Atlas(ATLAS)
    finish(elements, atlas)
    model = OrderedDict([("parent", "minecraft:block/block"), ("ambientocclusion", False),
                         ("textures", OrderedDict([("tex", "mymod:block/scrap_bench"), ("particle", "minecraft:block/oak_planks")])),
                         ("display", BENCH_DISPLAY), ("elements", elements)])
    return model, atlas.img


# ======================================================================================================== sprites

def sprite(rows, palette):
    """A sprite from rows of palette keys ('.' clear)."""
    img = Img(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        assert len(row) == img.w, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.set(x, y, palette[ch])
    return img


def keys(**ramps):
    out = {}
    for letters, r in ramps.items():
        for i, ch in enumerate(letters):
            out[ch] = r[i]
    return out


def spring_sprite():
    """A steel coil spring on the diagonal: the near side of each turn lit, the far side in shadow. Drawn by tracing
    the helix (so the turns are even) and outlining the result in vanilla's style."""
    a, b = (3.6, 12.4), (12.4, 3.6)
    length = math.hypot(b[0] - a[0], b[1] - a[1])
    u = ((b[0] - a[0]) / length, (b[1] - a[1]) / length)
    n = (-u[1], u[0])
    turns, radius, tilt = 5, 2.8, 0.5
    samples = []
    steps = 900
    for i in range(steps + 1):
        t = i / steps
        th = 2 * math.pi * turns * t
        along = t * length + tilt * radius * math.sin(th)
        x = a[0] + u[0] * along + n[0] * radius * math.cos(th)
        y = a[1] + u[1] * along + n[1] * radius * math.cos(th)
        samples.append((x, y, math.sin(th), math.cos(th)))
    r = STEEL
    img = Img(16, 16)
    for py in range(16):
        for px in range(16):
            cx, cy = px + 0.5, py + 0.5
            best = None
            for x, y, depth, side in samples:
                if (x - cx) ** 2 + (y - cy) ** 2 < 0.56 ** 2 and (best is None or depth > best[0]):
                    best = (depth, side)
            if best:
                depth, side = best
                if depth < -0.15:
                    c = r[1]
                elif depth < 0.35:
                    c = r[2] if side > 0 else r[3]
                else:
                    c = r[5] if side < -0.3 else r[4] if side < 0.4 else r[3]
                img.set(px, py, c)
    outline(img, r[0])
    return img


def outline(img, colour):
    solid = [[img.get(x, y)[3] > 0 for x in range(img.w)] for y in range(img.h)]
    for y in range(img.h):
        for x in range(img.w):
            if not solid[y][x] and any(0 <= x + dx < img.w and 0 <= y + dy < img.h and solid[y + dy][x + dx]
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.set(x, y, colour)


def screws_sprite():
    """A wood screw in profile on the diagonal (a domed head at the top left, a threaded shank, a point at the bottom
    right) and a small brass hex nut. The screw is traced from its shape along the axis, lit from the upper left."""
    a, b = (2.2, 2.2), (13.6, 13.6)
    length = math.hypot(b[0] - a[0], b[1] - a[1])
    u = ((b[0] - a[0]) / length, (b[1] - a[1]) / length)
    n = (u[1], -u[0])     # perpendicular, pointing up-right
    img = Img(16, 16)
    for py in range(16):
        for px in range(16):
            dx, dy = px + 0.5 - a[0], py + 0.5 - a[1]
            s_, p_ = dx * u[0] + dy * u[1], dx * n[0] + dy * n[1]
            if s_ < -0.2 or s_ > length:
                continue
            if s_ < 1.6:                                   # the head
                half = 2.6 - max(0.0, 0.9 - s_) * 1.2
                part = "head"
            elif s_ < length - 3.2:                        # the threaded shank
                phase = (s_ - 1.6) / 1.7 % 1.0
                half = 1.05 + 0.55 * (1 - abs(phase * 2 - 1))
                part = "thread" if phase > 0.5 else "shank"
            else:                                          # the point
                half = 1.6 * (length - s_) / 3.2
                part = "shank"
            if abs(p_) > half:
                continue
            side = p_ / max(half, 0.01)
            if part == "head":
                c = STEEL[5] if side > 0.35 else STEEL[4] if side > -0.2 else STEEL[2]
            elif part == "thread":
                c = STEEL[4] if side > 0.2 else STEEL[3] if side > -0.4 else STEEL[1]
            else:
                c = STEEL[3] if side > 0.2 else STEEL[2] if side > -0.4 else STEEL[1]
            img.set(px, py, c)
    outline(img, STEEL[0])
    nut = sprite([".KKK.",
                  "KBCBK",
                  "KAKAK",
                  "KBBAK",
                  ".KKK."], {"K": BRASS[0], "A": BRASS[2], "B": BRASS[3], "C": BRASS[4]})
    for y in range(nut.h):
        for x in range(nut.w):
            c = nut.get(x, y)
            if c[3]:
                img.set(1 + x, 10 + y, c)
    return img


BLUEPRINT_ROWS = [
    "................",
    ".KKK............",
    "KRrdKKKKKKKKKKK.",
    "KRrdBBBBBBBBBBBK",
    "KRrdbgbbgbbgbbBK",
    "KRrdbwwwwwwwwbBK",
    "KRrdbwbbbbbbwwBK",
    "KRrdbwwwwwwwwbBK",
    "KRrdbwwgbwbbgbBK",
    "KRrdbwwbbbbbbbBK",
    "KRrdbgbbgbbgbbBK",
    "KRrdbbbbbbbbbKKK",
    "KRrdBBBBBBBBKfK.",
    "KRrdKKKKKKKKKK..",
    ".KKK............",
    "................",
]


def blueprint_sprite(paper, lines, roll, outline_c):
    """A schematic unrolled from the left: the roll's spiral at its edge, a pistol drawn on the sheet over faint grid
    marks, and the bottom right corner dog-eared."""
    p = {"K": outline_c, "R": roll[2], "r": roll[1], "d": roll[0], "B": paper[2], "b": paper[1], "w": lines,
         "g": mixc(paper[1], lines, 0.3), "f": roll[2]}
    return sprite(BLUEPRINT_ROWS, p)


BLUEPRINTS = {
    "blueprint": blueprint_sprite(ramp("8f9aa6", "c9d2dc", "dde4ea"), hexc("c9d2dc"), ramp("aab4be", "e2e8ee", "f4f7f9"), hexc("4d5661")),
    "blueprint_workshop": blueprint_sprite(ramp("17305a", "2f5d9e", "3f73b8"), hexc("dfeaf8"), ramp("a9bfd8", "d7e3f0", "eef4fa"),
                                           hexc("10213d")),
    "blueprint_foundry": blueprint_sprite(ramp("5a2f0c", "a4600f", "c27a22"), hexc("fbe3b0"), ramp("d9bb8c", "efdcbb", "faf0dd"),
                                          hexc("3b1e07")),
}


# ============================================================================================================ GUI

K, W, BG, SHADOW, SLOT, SLOT_DARK = hexc("000000"), hexc("ffffff"), hexc("c6c6c6"), hexc("555555"), hexc("8b8b8b"), hexc("373737")
GUI_W, GUI_H = 176, 178
INVENTORY_Y = 96


def gui_panel(g, w, h):
    """Vanilla's container panel: black outline with clipped corners, white bevel top-left, grey shadow bottom-right."""
    g.fill(1, 1, w - 1, h - 1, BG)
    for x in range(2, w - 2):
        g.set(x, 0, K)
        g.set(x, h - 1, K)
    for y in range(2, h - 2):
        g.set(0, y, K)
        g.set(w - 1, y, K)
    for x, y in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        g.set(x, y, K)
    for i in (1, 2):
        for x in range(2, w - 3):
            g.set(x, i, W)
        for y in range(2, h - 3):
            g.set(i, y, W)
        for x in range(3, w - 2):
            g.set(x, h - 1 - i, SHADOW)
        for y in range(3, h - 2):
            g.set(w - 1 - i, y, SHADOW)
    g.set(3, 3, W)
    g.set(w - 4, h - 4, SHADOW)


def well(g, x0, y0, x1, y1, inner=SLOT):
    """A recessed area (x1, y1 exclusive): dark top-left edge, white bottom-right."""
    g.fill(x0, y0, x1, y1, inner)
    for x in range(x0, x1 - 1):
        g.set(x, y0, SLOT_DARK)
    for y in range(y0, y1 - 1):
        g.set(x0, y, SLOT_DARK)
    for x in range(x0 + 1, x1):
        g.set(x, y1 - 1, W)
    for y in range(y0 + 1, y1):
        g.set(x1 - 1, y, W)


def slot(g, x, y):
    """A slot whose item sits at (x, y)."""
    well(g, x - 1, y - 1, x + 17, y + 17)


ARROW = ["......#.......",
         "......##......",
         "......###.....",
         "##########....",
         "###########...",
         "##########....",
         "......###.....",
         "......##......",
         "......#......."]


def arrow(g, x, y, c=SLOT):
    for dy, row in enumerate(ARROW):
        for dx, ch in enumerate(row):
            if ch == "#":
                g.set(x + dx, y + dy, c)


HAMMER = ["....KKK.....",
          "...KLWMK....",
          "..KLWMMDK...",
          ".KLMMDDK....",
          "KDMDDKbK....",
          ".KDDKbaK....",
          "..KKbaK.....",
          "....baK.....",
          "...baK......",
          "..baK.......",
          ".bbK........",
          ".KK........."]


def button(g, x, y, state):
    """An 18x18 raised button with a hammer on it: normal, hovered (lit) or disabled (flat, faded)."""
    fill = {"normal": hexc("aeaeae"), "hover": hexc("c8d0e6"), "disabled": hexc("8b8b8b")}[state]
    g.fill(x, y, x + 18, y + 18, fill)
    for i in range(18):
        g.set(x + i, y, K)
        g.set(x + i, y + 17, K)
        g.set(x, y + i, K)
        g.set(x + 17, y + i, K)
    if state != "disabled":
        top = W if state == "hover" else hexc("e0e0e0")
        for i in range(1, 16):
            g.set(x + i, y + 1, top)
            g.set(x + 1, y + i, top)
            g.set(x + 1 + i, y + 16, SHADOW)
            g.set(x + 16, y + 1 + i, SHADOW)
    p = {"K": hexc("1c1f23"), "D": STEEL[1], "M": STEEL[2], "L": STEEL[4], "W": STEEL[5], "a": OAK[2], "b": OAK[4]}
    if state == "disabled":
        p = {k: mixc(v, hexc("8b8b8b"), 0.6) for k, v in p.items()}
    for dy, row in enumerate(HAMMER):
        for dx, ch in enumerate(row):
            if ch != ".":
                g.set(x + 3 + dx, y + 3 + dy, p[ch])


LOCK = ["..KKKK..",
        ".KS..SK.",
        ".K....K.",
        ".K....K.",
        "KKKKKKKK",
        "KBAAAABK",
        "KAAKKAAK",
        "KBAAAABK",
        "KKKKKKKK"]


def gui_texture():
    g = Img(256, 256)
    gui_panel(g, GUI_W, GUI_H)
    # The list (4 x 3 cells of 16 x 18) and its scroll track.
    well(g, 7, 15, 73, 71)
    well(g, 75, 15, 89, 71, hexc("6f6f6f"))
    for row in range(3):
        for col in range(9):
            slot(g, 8 + col * 18, INVENTORY_Y + row * 18)
    for col in range(9):
        slot(g, 8 + col * 18, INVENTORY_Y + 58)

    # Assemble overlay at (176, 0), drawn over the panel at (92, 16).
    ox, oy = 176 - 92, -16
    g.fill(176, 0, 256, 60, BG)
    for i in range(4):
        slot(g, ox + 96 + i % 2 * 18, oy + 22 + i // 2 * 18)
    arrow(g, ox + 135, oy + 34)
    slot(g, ox + 152, oy + 31)
    # Salvage overlay at (176, 64).
    oy = 64 - 16
    g.fill(176, 64, 256, 124, BG)
    slot(g, ox + 97, oy + 22)
    arrow(g, ox + 117, oy + 26)
    for i in range(6):
        slot(g, ox + 134 + i % 2 * 18, oy + 22 + i // 2 * 18)

    for i, state in enumerate(("normal", "hover", "disabled")):
        button(g, 176 + i * 18, 128, state)
    lock = {"K": hexc("2a2116"), "S": hexc("8a8f96"), "A": BRASS[3], "B": BRASS[2]}
    for dy, row in enumerate(LOCK):
        for dx, ch in enumerate(row):
            if ch != ".":
                g.set(230 + dx, 128 + dy, lock[ch])
    return g


def widen_weapon_station():
    """Gives the Weapon Station a fourth input slot (several of its recipes have four ingredients): the three slots
    at x 27-63 become four at 17-71. Idempotent."""
    path = os.path.join(TEX, "gui", "container", "weapon_station.png")
    with open(path, "rb") as f:
        src = R.read_png(f.read())
    g = Img(src.w, src.h)
    g.px = list(src.px)
    g.fill(15, 45, 91, 66, BG)
    for i in range(4):
        slot(g, 17 + i * 18, 47)
    g.save(path)
    return g


# ====================================================================================================== writing

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def merge(path, entries):
    """Adds entries to a shared JSON object file (lang), keeping what's there."""
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tag(path, values):
    obj = OrderedDict([("values", [])])
    if os.path.exists(path):
        with open(path) as f:
            obj = json.load(f, object_pairs_hook=OrderedDict)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(path, obj)


def m(name):
    return "mymod:" + name


# The blueprints, in Blueprints.ALL's order, and their paper.
BLUEPRINT_TIERS = OrderedDict([
    ("flare_gun", "workshop"), ("nail_gun", "workshop"), ("snub_revolver", "workshop"), ("sawed_off", "workshop"),
    ("m1911", "foundry"), ("revolver_357", "foundry"), ("red_dot_sight", "foundry"), ("suppressor", "foundry"),
    ("muzzle_brake", "foundry"), ("vertical_grip", "foundry"),
])

LANG = OrderedDict([
    ("block.mymod.scrap_bench", "Scrap Bench"),
    ("container.mymod.scrap_bench", "Scrap Bench"),
    ("container.mymod.scrap_bench.assemble", "Assemble"),
    ("container.mymod.scrap_bench.salvage", "Salvage"),
    ("container.mymod.scrap_bench.break", "Break one down"),
    ("container.mymod.scrap_bench.break_all", "Shift-click: the whole stack"),
    ("container.mymod.scrap_bench.cost", "%s × %s"),
    ("container.mymod.scrap_bench.result_count", "%s ×%s"),
    ("container.mymod.scrap_bench.fill_hint", "Click to load the bench (shift: as many as fit)"),
    ("container.mymod.scrap_bench.breaks_into", "Breaks down into:"),
    ("container.mymod.scrap_bench.worn", "Worn gear gives back less"),
    ("container.mymod.scrap_bench.salvage_hint", "Click to load it (shift: all of it)"),
    ("container.mymod.blueprint.learned", "Blueprint learned"),
    ("container.mymod.blueprint.locked", "Locked: read the %s"),
    ("container.mymod.blueprint.where", "Found in chests, crashed UFOs and on machines"),
    ("container.mymod.weapon_station.blueprints", "Blueprints"),
    ("container.mymod.weapon_station.blueprint_hint", "Put these in the slots to make it"),
    ("item.mymod.spring", "Spring"),
    ("item.mymod.screws", "Screws"),
    ("item.mymod.blueprint", "Blueprint"),
    ("item.mymod.blueprint.named", "%s Blueprint"),
    ("blueprint.mymod.blank", "Blank: teaches nothing"),
    ("blueprint.mymod.teaches", "Teaches an early recipe at the %s"),
    ("blueprint.mymod.tier", "Skips ahead to the %s tier"),
    ("blueprint.mymod.tier.workshop", "Workshop"),
    ("blueprint.mymod.tier.foundry", "Foundry"),
    ("blueprint.mymod.use", "Use to learn it"),
    ("blueprint.mymod.known", "Already learned"),
    ("blueprint.mymod.learned", "Learned the %s blueprint: make it at the %s."),
    ("blueprint.mymod.already_known", "You already know the %s"),
    ("tag.item.minecraft.planks", "Planks"),
    ("tag.item.minecraft.wooden_slabs", "Wooden Slabs"),
])

# Crafting-table recipes that moved: to the Scrap Bench (BenchRecipes) or, for the flare gun, the Weapon Station.
MOVED = ["pipe_pistol", "pipe_rifle", "blunderbuss", "junk_repeater", "metal_detector", "tripwire_alarm", "bear_trap",
         "pipe_bomb", "scrap_helmet", "scrap_chestplate", "scrap_leggings", "scrap_boots", "flare_gun"]


def write_assets(model, atlas):
    atlas.save(os.path.join(TEX, "block", "scrap_bench.png"))
    write(os.path.join(ASSETS, "models", "block", "scrap_bench.json"), model)
    variants = OrderedDict()
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = OrderedDict([("model", m("block/scrap_bench"))])
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    write(os.path.join(ASSETS, "blockstates", "scrap_bench.json"), {"variants": variants})
    write(os.path.join(ASSETS, "items", "scrap_bench.json"), {"model": {"type": "minecraft:model", "model": m("block/scrap_bench")}})

    sprites = OrderedDict([("spring", spring_sprite()), ("screws", screws_sprite())])
    sprites.update(BLUEPRINTS)
    for name, img in sprites.items():
        img.save(os.path.join(TEX, "item", name + ".png"))
        write(os.path.join(ASSETS, "models", "item", name + ".json"),
              OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": m("item/" + name)})]))
    for name in ("spring", "screws"):
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": m("item/" + name)}})
    # The blueprint's paper follows the tier of what it teaches; a blank one is grey.
    cases = []
    for tier in ("workshop", "foundry"):
        cases.append(OrderedDict([("when", [m(b) for b, t in BLUEPRINT_TIERS.items() if t == tier]),
                                  ("model", {"type": "minecraft:model", "model": m("item/blueprint_" + tier)})]))
    write(os.path.join(ASSETS, "items", "blueprint.json"), {"model": OrderedDict([
        ("type", "minecraft:select"), ("property", "minecraft:component"), ("component", m("blueprint")),
        ("cases", cases), ("fallback", {"type": "minecraft:model", "model": m("item/blueprint")})])})

    gui_texture().save(os.path.join(TEX, "gui", "container", "scrap_bench.png"))
    widen_weapon_station()
    merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


def write_data():
    mine = os.path.join(DATA, "mymod")
    write(os.path.join(mine, "recipe", "scrap_bench.json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", "misc"),
        ("key", OrderedDict([("C", "minecraft:crafting_table"), ("I", "minecraft:iron_ingot"), ("N", "minecraft:iron_nugget"),
                             ("P", "#minecraft:planks")])),
        ("pattern", ["NIN", "PCP", "P P"]),
        ("result", {"count": 1, "id": m("scrap_bench")})]))
    # Unlocked by the first junk or iron the player picks up.
    criteria = OrderedDict()
    for name, item in (("has_iron_nugget", "minecraft:iron_nugget"), ("has_iron_ingot", "minecraft:iron_ingot"),
                       ("has_rusty_scrap", m("rusty_scrap")), ("has_tech_scrap", m("tech_scrap"))):
        criteria[name] = {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"}
    criteria["has_the_recipe"] = {"conditions": {"recipes": m("scrap_bench")}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(mine, "advancement", "recipes", "misc", "scrap_bench.json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria), ("requirements", [list(criteria)]),
        ("rewards", {"recipes": [m("scrap_bench")]})]))
    write(os.path.join(mine, "loot_table", "blocks", "scrap_bench.json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("bonus_rolls", 0.0), ("conditions", [{"condition": "minecraft:survives_explosion"}]),
                                ("entries", [{"type": "minecraft:item", "name": m("scrap_bench")}]), ("rolls", 1.0)])]),
        ("random_sequence", m("blocks/scrap_bench"))]))
    tag(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "axe.json"), [m("scrap_bench")])

    # The banker deals in repossessed schematics.
    write(os.path.join(mine, "villager_trade", "banker", "3", "emerald_blueprint.json"), OrderedDict([
        ("gives", {"id": m("blueprint")}),
        ("given_item_modifier", {"type": m("random_blueprint"), "tier": "foundry"}),
        ("max_uses", 2), ("reputation_discount", 0.05),
        ("wants", {"id": "minecraft:emerald", "count": 16}), ("xp", 10)]))
    trade_set = os.path.join(mine, "trade_set", "banker", "level_3.json")
    with open(trade_set) as f:
        sets = json.load(f, object_pairs_hook=OrderedDict)
    if m("banker/3/emerald_blueprint") not in sets["trades"]:
        sets["trades"].append(m("banker/3/emerald_blueprint"))
    write(trade_set, sets)

    for name in MOVED:
        for path in [os.path.join(mine, "recipe", name + ".json")] + \
                [os.path.join(mine, "advancement", "recipes", d, name + ".json") for d in os.listdir(os.path.join(mine, "advancement", "recipes"))]:
            if os.path.exists(path):
                os.remove(path)


# ======================================================================================================= preview

def face_shade(n):
    """Minecraft's block face shading: up 1.0, down 0.5, north/south 0.8, east/west 0.6."""
    return 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] > 0 else 0.5) * n[1] ** 2


def camera(d):
    """An orthographic camera looking along -d (d points from the scene toward the camera)."""
    dl = math.sqrt(sum(c * c for c in d))
    d = [c / dl for c in d]
    f = [-c for c in d]
    up = (0.0, 1.0, 0.0)
    r = [f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]]
    rl = math.sqrt(sum(c * c for c in r))
    r = [c / rl for c in r]
    u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
    return r, u, d


def render_model(img, elements, atlases, d, cx, cy, scale, centre=(8, 12, 8), bg_depth=None):
    """Rasterises model elements (rotations included) with Minecraft's face shading; atlases maps '#key' to Img."""
    from gunsmith_kit import face_polygon
    r, u, dd = camera(d)
    depth = bg_depth or [-1e9] * (img.w * img.h)
    for e in elements:
        for face, f in e["faces"].items():
            pts, n = face_polygon(e, face)
            if sum(n[k] * dd[k] for k in range(3)) <= 1e-6:
                continue
            shade = 1.0 if e.get("light_emission") else face_shade(n)
            tex = atlases[f["texture"]]
            sp = []
            for p in pts:
                v = [p[k] - centre[k] for k in range(3)]
                sp.append((cx + sum(v[k] * r[k] for k in range(3)) * scale, cy - sum(v[k] * u[k] for k in range(3)) * scale,
                           sum(v[k] * dd[k] for k in range(3))))
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (sp[i] for i in tri)
                ta, tb, tc = (uvs[i] for i in tri)
                area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
                if abs(area) <= 1e-9:
                    continue
                for py in range(max(0, int(min(a[1], b[1], c[1]))), min(img.h, int(max(a[1], b[1], c[1])) + 2)):
                    for px in range(max(0, int(min(a[0], b[0], c[0]))), min(img.w, int(max(a[0], b[0], c[0])) + 2)):
                        qx, qy = px + 0.5, py + 0.5
                        l1 = ((b[0] - qx) * (c[1] - qy) - (b[1] - qy) * (c[0] - qx)) / area
                        l2 = ((c[0] - qx) * (a[1] - qy) - (c[1] - qy) * (a[0] - qx)) / area
                        l3 = 1 - l1 - l2
                        if min(l1, l2, l3) < -1e-6:
                            continue
                        z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                        i = py * img.w + px
                        if z <= depth[i]:
                            continue
                        tu = l1 * ta[0] + l2 * tb[0] + l3 * tc[0]
                        tv = l1 * ta[1] + l2 * tb[1] + l3 * tc[1]
                        col = tex.get(min(tex.w - 1, max(0, int(tu / 16 * tex.w))), min(tex.h - 1, max(0, int(tv / 16 * tex.h))))
                        if col[3] < 128:
                            continue
                        depth[i] = z
                        img.set(px, py, tuple(min(255, int(col[k] * shade)) for k in range(3)) + (255,))
    return depth


def load_png(path):
    with open(path, "rb") as f:
        return R.read_png(f.read())


def resolve_textures(model):
    textures = dict(model.get("textures", {}))
    out = {}
    for key, ref in textures.items():
        while ref.startswith("#"):
            ref = textures[ref[1:]]
        ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
        if ns == "mymod":
            out["#" + key] = load_png(os.path.join(TEX, path + ".png"))
        else:
            out["#" + key] = R.read_png(R.JAR.read("assets/minecraft/textures/" + path + ".png"))
    return out


BLOCK_GUI = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}


def model_icon(model):
    """A 16x16 inventory icon of an element model, drawn the way its gui display places it."""
    from gunsmith_kit import gui_icon, render
    gui = model.get("display", {}).get("gui", BLOCK_GUI)
    atl = {k: (v.px, v.w) for k, v in resolve_textures(model).items()}
    project, turn = gui_icon({"gui": gui}, 64)
    px = render(model["elements"], atl, project, turn, 64, 64)
    big = Img(64, 64)
    for i, c in enumerate(px):
        if c is not None:
            big.px[i] = tuple(c) + (255,)
    # Down to 16x16, keeping hard edges: a texel is solid if most of its 4x4 block is.
    out = Img(16, 16)
    for y in range(16):
        for x in range(16):
            cols = [big.get(x * 4 + i, y * 4 + j) for j in range(4) for i in range(4)]
            solid = [c for c in cols if c[3]]
            if len(solid) >= 6:
                out.set(x, y, tuple(sum(c[k] for c in solid) // len(solid) for k in range(3)) + (255,))
    return out


ICONS = {}


def first_model(node):
    """The plain model an item definition shows by default (following fallbacks, first entries and composites)."""
    kind = node.get("type")
    if kind == "minecraft:model":
        return node["model"]
    for key in ("fallback", "on_false"):
        if key in node:
            return first_model(node[key])
    if kind == "minecraft:composite":
        for sub in node["models"]:
            found = first_model(sub)
            if found:
                return found
    if "entries" in node:
        return first_model(node["entries"][0]["model"])
    if "cases" in node:
        return first_model(node["cases"][0]["model"])
    return None


def load_model(ref):
    ns, path = ref.split(":")
    if ns == "minecraft":
        return json.loads(R.JAR.read("assets/minecraft/models/" + path + ".json"))
    with open(os.path.join(ASSETS, "models", path + ".json")) as f:
        return json.load(f)


def icon(item):
    """An item's inventory picture: its layer0 sprite, or its element model drawn small."""
    if item in ICONS:
        return ICONS[item]
    ns, name = item.split(":")
    img = None
    if ns == "minecraft":
        for folder in ("item", "block"):
            try:
                img = R.read_png(R.JAR.read("assets/minecraft/textures/" + folder + "/" + name + ".png"))
                break
            except KeyError:
                img = None
    else:
        with open(os.path.join(ASSETS, "items", name + ".json")) as f:
            model = load_model(first_model(json.load(f)["model"]))
        layer = model.get("textures", {}).get("layer0")
        if layer and "elements" not in model:
            img = load_png(os.path.join(TEX, layer.split(":")[1] + ".png"))
        elif "elements" in model:
            img = model_icon(model)
    ICONS[item] = img or Img(16, 16, hexc("ff00ff"))
    return ICONS[item]


def vsprite(path):
    return R.read_png(R.JAR.read("assets/minecraft/textures/gui/sprites/" + path + ".png"))


def put(sheet, img, x, y, k):
    sheet.paste(img, x, y, k)


def wash(sheet, x, y, k, colour):
    sheet.fill(x, y, x + 16 * k, y + 16 * k, colour)


LOCK_IMG = None


def gui_mockup(mode, k):
    """The bench window as the game draws it (k times size): tabs, the list, the mode's slots, some items."""
    tex = gui_texture()
    out = Img(GUI_W * k + 8 * k, (GUI_H + 32) * k, hexc("2b2f36"))
    ox, oy = 4 * k, 30 * k

    def blit(src, sx, sy, w, h, dx, dy):
        part = Img(w, h)
        for y in range(h):
            for x in range(w):
                part.set(x, y, src.get(sx + x, sy + y))
        out.paste(part, ox + dx * k, oy + dy * k, k)

    def item(name, x, y, count=None, locked=False, short=False, ghost=False):
        put(out, icon(name), ox + x * k, oy + y * k, k)
        if locked:
            wash(out, ox + x * k, oy + y * k, k, (139, 139, 139, 176))
            lock = Img(8, 9)
            for dy, row in enumerate(LOCK):
                for dx, ch in enumerate(row):
                    if ch != ".":
                        lock.set(dx, dy, {"K": hexc("2a2116"), "S": hexc("8a8f96"), "A": BRASS[3], "B": BRASS[2]}[ch])
            out.paste(lock, ox + (x + 9) * k, oy + (y + 8) * k, k)
        elif short or ghost:
            wash(out, ox + x * k, oy + y * k, k, (139, 139, 139, 112 if short else 128))
        if count and count > 1:
            R.text(out, ox + (x + 17 - 4 * len(str(count))) * k, oy + (y + 11) * k, str(count), "#ffffff", k, shadow="#3f3f3f")

    tab_unsel = [vsprite("container/creative_inventory/tab_top_unselected_%d" % i) for i in (1, 2)]
    tab_sel = [vsprite("container/creative_inventory/tab_top_selected_%d" % i) for i in (1, 2)]
    other = 1 - mode
    out.paste(tab_unsel[other], ox + other * 27 * k, oy - 28 * k, k)
    blit(tex, 0, 0, GUI_W, GUI_H, 0, 0)
    blit(tex, 176, 0 if mode == 0 else 64, 80, 60, 92, 16)
    out.paste(tab_sel[mode], ox + mode * 27 * k, oy - 28 * k, k)
    for t, name in ((0, "mymod:pipe_pistol"), (1, "mymod:rusty_scrap")):
        put(out, icon(name), ox + (t * 27 + 5) * k, oy + (-28 + 9) * k, k)
    R.text(out, ox + 8 * k, oy + 6 * k, "SCRAP BENCH", "#404040", k)
    R.text(out, ox + 8 * k, oy + (INVENTORY_Y - 11) * k, "INVENTORY", "#404040", k)

    cell, scroller = vsprite("container/stonecutter/recipe"), vsprite("container/stonecutter/scroller")
    if mode == 0:
        entries = [("mymod:bear_trap", 0), ("mymod:pipe_bomb", 0), ("mymod:scrap_helmet", 0), ("mymod:scrap_chestplate", 1),
                   ("mymod:scrap_leggings", 0), ("mymod:scrap_boots", 0), ("mymod:flare_gun", 0), ("mymod:nail_gun", 2),
                   ("mymod:snub_revolver", 2), ("mymod:sawed_off", 2)]
        out.paste(scroller, ox + 76 * k, oy + (16 + 39) * k, k)
    else:
        entries = [(n, 0 if i in (0, 2, 3, 6) else 1) for i, n in enumerate(
            ["mymod:rusty_scrap", "mymod:old_boot", "mymod:tech_scrap", "mymod:servo_arm", "mymod:optic_lens", "mymod:cathode_tube",
             "minecraft:rail", "minecraft:iron_chain", "minecraft:bucket", "minecraft:minecart", "minecraft:tripwire_hook",
             "minecraft:shears"])]
        out.paste(scroller, ox + 76 * k, oy + 16 * k, k)
    for i, (name, state) in enumerate(entries):
        x, y = 8 + i % 4 * 16, 16 + i // 4 * 18
        out.paste(cell, ox + x * k, oy + y * k, k)
        item(name, x, y + 1, locked=state == 2, short=state == 1)
    if mode == 0:
        for i, (name, n) in enumerate((("minecraft:iron_ingot", 2), ("mymod:spring", 1), ("minecraft:flint", 1), ("minecraft:oak_planks", 1))):
            if name == "minecraft:oak_planks":
                planks = R.read_png(R.JAR.read("assets/minecraft/textures/block/oak_planks.png"))
                put(out, planks, ox + (96 + i % 2 * 18) * k, oy + (22 + i // 2 * 18) * k, k)
            else:
                item(name, 96 + i % 2 * 18, 22 + i // 2 * 18, n)
        item("mymod:pipe_pistol", 152, 31)
    else:
        item("mymod:rusty_scrap", 97, 22, 12)
        blit(tex, 176, 128, 18, 18, 96, 42)
        item("minecraft:iron_nugget", 134, 22, 6)
        item("mymod:screws", 152, 22, 3)
    for i, (name, n) in enumerate((("minecraft:iron_ingot", 14), ("mymod:rusty_scrap", 30), ("minecraft:iron_nugget", 9),
                                   ("mymod:spring", 4), ("mymod:screws", 16), ("minecraft:flint", 5), ("minecraft:copper_ingot", 7),
                                   ("mymod:tech_scrap", 6), ("minecraft:leather", 3))):
        item(name, 8 + i * 18, INVENTORY_Y + 58, n)
    return out


def station_mockup(k):
    """The Weapon Station with its fourth slot and the blueprint panel beside it."""
    tex = load_png(os.path.join(TEX, "gui", "container", "weapon_station.png"))
    out = Img((176 + 70) * k, 170 * k, hexc("2b2f36"))
    part = Img(176, 166)
    for y in range(166):
        for x in range(176):
            part.set(x, y, tex.get(x, y))
    out.paste(part, 0, 0, k)
    x0, y0, w, h = 178, 0, 66, 16 + 2 * 18 + 6
    panel = Img(w, h)
    panel.fill(1, 0, w - 1, h, K)
    panel.fill(0, 1, w, h - 1, K)
    panel.fill(1, 1, w - 1, h - 1, BG)
    panel.fill(1, 1, w - 2, 3, W)
    panel.fill(1, 1, 3, h - 2, W)
    panel.fill(2, h - 3, w - 1, h - 1, SHADOW)
    panel.fill(w - 3, 2, w - 1, h - 1, SHADOW)
    out.paste(panel, x0 * k, y0 * k, k)
    R.text(out, (x0 + 6) * k, (y0 + 6) * k, "BLUEPRINTS", "#404040", k)
    names = ["mymod:m1911", "mymod:revolver_357", "mymod:red_dot_sight", "mymod:suppressor", "mymod:muzzle_brake", "mymod:vertical_grip"]
    for i, name in enumerate(names):
        cx, cy = x0 + 7 + i % 3 * 18, y0 + 17 + i // 3 * 18
        frame = Img(18, 18)
        well(frame, 0, 0, 18, 18)
        out.paste(frame, (cx - 1) * k, (cy - 1) * k, k)
        out.paste(icon(name), cx * k, cy * k, k)
        if i != 0:
            out.fill(cx * k, cy * k, (cx + 16) * k, (cy + 16) * k, (139, 139, 139, 176))
            lock = Img(8, 9)
            for dy, row in enumerate(LOCK):
                for dx, ch in enumerate(row):
                    if ch != ".":
                        lock.set(dx, dy, {"K": hexc("2a2116"), "S": hexc("8a8f96"), "A": BRASS[3], "B": BRASS[2]}[ch])
            out.paste(lock, (cx + 9) * k, (cy + 8) * k, k)
    for i, (name, n) in enumerate((("minecraft:iron_ingot", 6), ("mymod:spring", 2), ("mymod:screws", 4))):
        out.paste(icon(name), (17 + i * 18) * k, 47 * k, k)
        R.text(out, (17 + i * 18 + 17 - 4) * k, (47 + 11) * k, str(n), "#ffffff", k, shadow="#3f3f3f")
    out.paste(icon("mymod:m1911"), 129 * k, 47 * k, k)
    return out


def preview(path, model, atlas):
    sheet = Img(1700, 1120, hexc("2b2f36"))

    def label(x, y, s, k=2):
        R.text(sheet, x, y, s, "#e9eff5", k)

    elements = model["elements"]
    atlases = {"#tex": atlas}
    label(20, 14, "SCRAP BENCH: 3/4 VIEW (FRONT RIGHT)")
    sheet.fill(20, 34, 620, 534, hexc("5b7a3a"))
    clip = Img(600, 500, hexc("5b7a3a"))
    render_model(clip, elements, atlases, (0.45, 0.6, -0.95), 300, 290, 17)
    sheet.paste(clip, 20, 34)
    label(640, 14, "BACK LEFT")
    clip = Img(330, 245, hexc("5b7a3a"))
    render_model(clip, elements, atlases, (-0.7, 0.55, 0.7), 165, 135, 10)
    sheet.paste(clip, 640, 34)
    label(640, 290, "SIDE, LAMP LIT")
    clip = Img(330, 245, hexc("26303a"))
    render_model(clip, elements, atlases, (-1.0, 0.25, -0.15), 165, 130, 10)
    sheet.paste(clip, 640, 310)
    label(990, 14, "ATLAS (64PX, X4)")
    sheet.fill(990, 34, 990 + 260, 34 + 260, hexc("1d2026"))
    sheet.paste(atlas, 992, 36, 4)
    label(990, 310, "SPRITES (6X, 1X)")
    sprites = [("SPRING", spring_sprite()), ("SCREWS", screws_sprite())] + [(n.replace("blueprint_", "").upper() or "BLANK", img)
                                                                         for n, img in BLUEPRINTS.items()]
    for i, (name, img) in enumerate(sprites):
        x = 990 + i * 136
        sheet.fill(x, 330, x + 108, 438, hexc("8b8b8b"))
        sheet.paste(img, x + 6, 336, 6)
        sheet.fill(x, 444, x + 20, 464, hexc("8b8b8b"))
        sheet.paste(img, x + 2, 446)
        R.text(sheet, x + 26, 450, "BLANK" if name == "BLUEPRINT" else name, "#e9eff5", 1)
    label(990, 478, "BENCH ITEM ICON (GUI DISPLAY, 4X AND 1X)")
    bench_icon = model_icon(dict(model, textures={"tex": "mymod:block/scrap_bench"}))
    sheet.fill(990, 494, 1058, 562, hexc("8b8b8b"))
    sheet.paste(bench_icon, 992, 496, 4)
    sheet.fill(1066, 494, 1086, 514, hexc("8b8b8b"))
    sheet.paste(bench_icon, 1068, 496)
    label(20, 572, "ASSEMBLE (SCROLLED DOWN)")
    sheet.paste(gui_mockup(0, 2), 20, 592)
    label(420, 572, "SALVAGE")
    sheet.paste(gui_mockup(1, 2), 420, 592)
    label(830, 572, "WEAPON STATION: 4TH SLOT, BLUEPRINT PANEL")
    sheet.paste(station_mockup(2), 830, 652)
    sheet.save(path)
    print("preview:", path)


def main():
    model, atlas = build_bench()
    write_assets(model, atlas)
    write_data()
    if PREVIEW:
        preview(PREVIEW, model, atlas)


if __name__ == "__main__":
    main()
