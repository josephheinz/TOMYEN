#!/usr/bin/env python3
"""Josh, the villager who only sells Fish Fish (see com.jheinz.tomyen.fishing.josh): his outfit, his job site (the
Fish Fish Crate), his trades, and the lang. The Fish Fish itself is tools/gen_fish.py's and gen_fish_entities.py's.

Run from anywhere: python3 tools/gen_josh.py [preview.png]

Outfit. A profession layer over vanilla's villager model (textures/entity/villager/profession/josh.png, and the same
for the zombie villager, as the banker does): a fisherman's yellow oilskins, a sou'wester with a broad brim that
sweeps longer at the back, a Fish Fish badge on the front of the hat and another lying on its crown, a navy jumper
showing at the collar and cuffs, toggles down the coat, a chest pocket with a Fish Fish's tail sticking out of it,
and green rubber boots under the hem. Flat, deliberate ramps lit from the top left; nothing else on the texture. The
hat is "full" (.mcmeta), so a biome's own hat never shows through it. Villager UVs follow vanilla's VillagerModel:
a box at (u, v) of size (w, h, d) has its top at (u+d, v) (rows back to front), its front at (u+d, v+d), its sides at
(u, v+d) and (u+d+w, v+d) and its back at (u+2d+w, v+d).

Fish Fish Crate. Josh's job site: a slatted spruce crate on four corner posts, heaped with Fish Fish, three more lying
on top of the heap and a Fish Fish stencilled on its front. Built from boxes (no z-fighting: tools/zfight.py), its
textures painted in the same flat ramps, its wood taken from vanilla's spruce planks.

Trades (data/tomyen/villager_trade/josh, trade_set/josh): every one of them is Fish Fish for emeralds. One, a handful,
a dozen, a stack, a bucket of them, and Josh's own trophies (weighed and signed "caught by Josh") at 8, 14.5 and a
record 20 lb.

The preview draws the crate from both front corners and Josh (a plains villager) from the front, three-quarters and
behind, beside vanilla's fisherman."""
import json, math, os, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png, write, apply, rx, ry, mul  # noqa: E402
from zfight import check as zfight_check  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tomyen")
DATA = os.path.join(RES, "data")
TEX = os.path.join(ASSETS, "textures")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
CLEAR = (0, 0, 0, 0)


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# Oilskin yellow, light to dark; the jumper's navy; rubber boots; the Fish Fish's own blues (gen_fish.py).
Y = [C("7a4e0a"), C("a8700f"), C("d39a1c"), C("eebd2e"), C("f6d65a")]
NAVY = [C("18223c"), C("26365a"), C("3a4f7a")]
BOOT = [C("27381b"), C("3a5229"), C("4f6b3a")]
TOGGLE = C("5a3a1e")
FISH = dict(o=C("1d3a5c"), d=C("2c66a6"), b=C("4f96d6"), L=C("8cc6ee"), w=C("d8ecf8"), E=C("0f1720"), W=C("f4f8fc"))


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def stamp(self, x0, y0, rows, palette):
        """Characters of rows at (x0, y0): each a palette key, '.' left alone."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    self.set(x0 + i, y0 + j, palette[ch])


def box_faces(u, v, w, h, d):
    """A model box's texture rectangles (x0, y0, x1, y1) by face."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "west": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# ============================================================================================================ outfit

# A Fish Fish, small: 7 x 3 for the hat's badge (head to the left), and 7 x 4 lying on the crown (head forward).
FISH_BADGE = ["obbbo.o",
              "bEbbbdo",
              "owwwo.o"]


def coat_face(c, rect, lit, shade, hem=True):
    """Oilskin: base yellow, a lit column on the left edge (lit) or a shaded one on the right (shade), the hem darker."""
    x0, y0, x1, y1 = rect
    c.rect(x0, y0, x1, y1, Y[3])
    if lit:
        c.rect(x0, y0, x0 + 1, y1, Y[4])
    if shade:
        c.rect(x1 - 1, y0, x1, y1, Y[2])
    if hem:
        c.rect(x0, y1 - 1, x1, y1, Y[1])


def outfit():
    c = Canvas(64, 64)

    # --- The sou'wester -------------------------------------------------------------------------------------------
    hat = box_faces(32, 0, 8, 10, 8)
    # Crown: the top of the hat box, lighter in the middle where the light falls, with a Fish Fish lying on it.
    x0, y0, x1, y1 = hat["top"]
    c.rect(x0, y0, x1, y1, Y[3])
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, Y[4])
    c.stamp(x0 + 1, y0 + 2, ["obbbbo.", "bEbLbdo", "obwwbo."], FISH)
    # Sides of the crown: rows 0-4 of each side face, down to the brim (rows 5-9 are the face, left bare).
    for face in ("west", "front", "east", "back"):
        x0, y0, x1, y1 = hat[face]
        c.rect(x0, y0, x1, y0 + 5, Y[3])
        c.rect(x0, y0, x1, y0 + 1, Y[4])                 # the crown's lit shoulder
        c.rect(x0, y0 + 4, x1, y0 + 5, Y[2])             # in the brim's shadow
    c.rect(hat["east"][0], hat["east"][1] + 1, hat["east"][2], hat["east"][1] + 4, Y[2])   # the side away from the light
    # The badge: a Fish Fish on the front of the hat, head to the left.
    fx, fy = hat["front"][0], hat["front"][1]
    c.stamp(fx, fy + 1, FISH_BADGE, FISH)
    # The sou'wester's long back flap, down over the neck, and round the back of each side.
    x0, y0, x1, y1 = hat["back"]
    c.rect(x0, y0 + 5, x1, y1, Y[3])
    c.rect(x0, y1 - 1, x1, y1, Y[1])
    for face, cols in (("west", (0, 4)), ("east", (4, 8))):
        x0, y0, x1, y1 = hat[face]
        c.rect(x0 + cols[0], y0 + 5, x0 + cols[1], y1 - 2, Y[2])
        c.rect(x0 + cols[0], y1 - 3, x0 + cols[1], y1 - 2, Y[1])
    # A seam down the back panel, where a sou'wester is sewn.
    c.rect(hat["back"][0] + 3, hat["back"][1] + 1, hat["back"][0] + 4, hat["back"][1] + 4, Y[2])

    # Brim: the rim plane's top face at (31, 48) (col: x -8..8, row: back to front) and its underside at (48, 48)
    # (mirrored left to right). Broad all round, longer over the neck than over the eyes, with a dark rolled edge.
    def brim(cx, cy, mirror):
        def inside(k, r):
            if not (0 <= k < 16 and 0 <= r < 16):
                return False
            x = k - 7.5
            z = 7.5 - r                                  # + at the back
            reach_z = 7.6 if z > 0 else 6.6
            return (abs(x) / 7.6) ** 4 + (abs(z) / reach_z) ** 4 <= 1.0
        for r in range(16):
            for k in range(16):
                if not inside(k, r):
                    continue
                ring = min(d for d in range(1, 4) if any(not inside(k + dx * d, r + dy * d)
                                                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) or d == 3)
                if mirror:
                    colour = Y[1] if ring == 1 else Y[2]
                else:
                    colour = Y[1] if ring == 1 else Y[3] if ring == 2 else Y[4]
                c.set(cx + (15 - k if mirror else k), cy + r, colour)
    brim(31, 48, False)
    brim(48, 48, True)

    # --- The oilskin coat (the robe) ------------------------------------------------------------------------------
    coat = box_faces(0, 38, 8, 20, 6)
    coat_face(c, coat["front"], True, True)
    coat_face(c, coat["west"], False, False)
    coat_face(c, coat["east"], False, True)
    coat_face(c, coat["back"], False, False)
    x0, y0, x1, y1 = coat["top"]
    c.rect(x0, y0, x1, y1, Y[4])
    c.rect(x0 + 2, y0 + 1, x1 - 2, y1 - 1, NAVY[1])      # the jumper's roll neck round the head
    x0, y0, x1, y1 = coat["bottom"]
    c.rect(x0, y0, x1, y1, Y[1])
    # Front: the jumper in a narrow V at the collar, the coat's overlap seam, toggles, a pocket with a fish tail.
    fx, fy, _, _ = coat["front"]
    c.rect(fx + 3, fy, fx + 5, fy + 2, NAVY[2])
    c.set(fx + 3, fy + 2, NAVY[1])
    c.set(fx + 2, fy, Y[4])                              # collar points
    c.set(fx + 5, fy, Y[4])
    c.rect(fx + 4, fy + 2, fx + 5, fy + 19, Y[2])        # where the coat's front edge overlaps
    for ty in (4, 8, 12):
        c.set(fx + 3, fy + ty, TOGGLE)
        c.set(fx + 3, fy + ty + 1, Y[1])
    # Pockets low on the coat, below the crossed arms: a flap over each, and a Fish Fish's tail fanned up out of the
    # right-hand one.
    for px in (1, 5):
        c.rect(fx + px, fy + 13, fx + px + 2, fy + 14, Y[2])
        c.rect(fx + px, fy + 14, fx + px + 2, fy + 16, Y[3])
        c.rect(fx + px, fy + 16, fx + px + 2, fy + 17, Y[2])
    c.stamp(fx + 5, fy + 11, ["b.L", ".d."], FISH)
    # Back: a yoke across the shoulders and the centre seam.
    bx, by, bx1, _ = coat["back"]
    c.rect(bx, by + 3, bx1, by + 4, Y[2])
    c.rect(bx + 4, by + 4, bx + 5, by + 19, Y[2])
    # And on the back, a Fish Fish stencilled across the shoulders, like a fish company's coat.
    c.stamp(bx, by + 5, ["o.obbbo", "odbbbEb", "o.owwwo"], FISH)
    # The body under the coat, in case any of it shows.
    for face, (x0, y0, x1, y1) in box_faces(16, 20, 8, 12, 6).items():
        c.rect(x0, y0, x1, y1, Y[2] if face in ("bottom", "east") else Y[3])

    # --- Sleeves: oilskin, with the jumper's navy cuff at the wrist; the crossed hands stay bare -------------------
    arm = box_faces(44, 22, 4, 8, 4)
    for face, (x0, y0, x1, y1) in arm.items():
        if face == "bottom":
            c.rect(x0, y0, x1, y1, Y[2])
            c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, NAVY[1])  # the cuff, round the wrist
        elif face == "top":
            c.rect(x0, y0, x1, y1, Y[4])
        else:
            c.rect(x0, y0, x1, y1, Y[3] if face != "east" else Y[2])
            if face == "front":
                c.rect(x0, y0, x0 + 1, y1, Y[4])
            c.rect(x0, y1 - 1, x1, y1, NAVY[2] if face != "east" else NAVY[1])

    # --- Rubber boots: the bottom six rows of each leg (the robe hides the rest) -----------------------------------
    leg = box_faces(0, 22, 4, 12, 4)
    for face, (x0, y0, x1, y1) in leg.items():
        if face == "bottom":
            c.rect(x0, y0, x1, y1, BOOT[0])
        elif face != "top":
            c.rect(x0, y1 - 6, x1, y1, BOOT[1])
            c.rect(x0, y1 - 6, x1, y1 - 5, BOOT[2])      # the turned-down top
            c.rect(x0, y1 - 1, x1, y1, BOOT[0])          # the sole
            if face == "front":
                c.rect(x0, y1 - 5, x0 + 1, y1 - 1, BOOT[2])
    return c


def write_outfit():
    c = outfit()
    meta = OrderedDict([("villager", OrderedDict([("hat", "full")]))])
    for kind in ("villager", "zombie_villager"):
        path = os.path.join(TEX, "entity", kind, "profession", "josh.png")
        png(path, 64, 64, c.px)
        with open(path + ".mcmeta", "w") as fh:
            json.dump(meta, fh, indent=4)
            fh.write("\n")
    return c


# ============================================================================================================= crate

def spruce_ramp():
    """Vanilla's spruce planks, sorted into a five-step ramp (dark gap to highlight)."""
    from gen_longswords import client_jar, read_png
    with zipfile.ZipFile(client_jar()) as z:
        _, _, px = read_png(z.read("assets/minecraft/textures/block/spruce_planks.png"))
    cols = sorted({p[:3] for p in px}, key=lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2])
    pick = [cols[0], cols[len(cols) // 4], cols[len(cols) // 2], cols[3 * len(cols) // 4], cols[-1]]
    return [p + (255,) for p in pick]


def crate_textures(W):
    """W: wood ramp, dark to light. Returns name -> Canvas."""
    nail = C("4a4a52")
    out = {}

    # Side: three horizontal boards over the face's ten rows (rows 6-15), dark gaps between, a nail at each end of
    # each board by the posts, a grain dash or two.
    side = Canvas(16, 16, W[2])
    boards = [(6, 9), (10, 13), (14, 16)]
    for b0, b1 in [(0, 2), (2, 5)] + boards:
        side.rect(0, b0, 16, b1, W[2])
        side.rect(0, b0, 16, b0 + 1, W[3])
        side.rect(0, b1 - 1, 16, b1, W[1])
    for gap in (5, 9, 13):
        side.rect(0, gap, 16, gap + 1, W[0])
    for b0, b1 in boards:
        side.set(2, b0 + 1, nail)
        side.set(13, b0 + 1, nail)
    side.rect(5, 7, 9, 8, W[1])
    side.rect(9, 11, 13, 12, W[1])
    out["side"] = side

    # Front: the side, with a Fish Fish stencilled across the boards in blue paint (darker where it runs into a gap).
    front = Canvas(16, 16)
    front.px = list(side.px)
    # The stencil: an oval body with a hole for the eye, a gap, then a forked tail; painted on in one coat, a shade
    # darker where it runs over the gaps between the boards.
    def stencilled(i, j):
        if i <= 6:
            return ((i - 3.0) / 3.6) ** 2 + ((j - 2.0) / 2.7) ** 2 <= 1.0 and (i, j) != (1, 1)
        spread = i - 6.5                                 # the tail fans out from the body's end
        return abs(j - 2.0) <= spread + 0.01 and not (i == 9 and j == 2)
    for j in range(5):
        for i in range(10):
            if stencilled(i, j):
                x, y = 3 + i, 8 + j
                front.set(x, y, FISH["d"] if side.get(x, y) == W[0] else FISH["b"])
    out["front"] = front

    # Top: the heap of Fish Fish inside the rim (rows and columns 2-13), in the crate's shadow: a regular lay of
    # lit backs, like fish packed in rows.
    top = Canvas(16, 16, FISH["d"])
    for y in range(16):
        for x in range(16):
            q = (x + 2 * ((y // 2) % 2)) % 4
            if y % 2 == 0 and q in (1, 2):
                top.set(x, y, FISH["b"])
    out["top"] = top

    # Posts: lit on the left, shaded on the right, a cut end on top.
    post = Canvas(16, 16, W[2])
    post.rect(0, 0, 1, 16, W[3])
    post.rect(1, 0, 2, 16, W[2])
    post.rect(0, 0, 2, 2, W[4])
    out["post"] = post

    # The little fish lying on the heap: their flanks (up), backs, bellies, heads and tails.
    fish = Canvas(16, 16)
    fish.stamp(0, 0, ["LLLLL", "Ebbbb", "wwwwb"], FISH)               # flank, head on the left (u 0-5, v 0-3)
    fish.rect(0, 4, 5, 6, FISH["d"])                                    # back (v 4-6)
    fish.rect(0, 6, 5, 8, FISH["w"])                                    # belly (v 6-8)
    fish.rect(6, 4, 9, 6, FISH["b"])                                    # head end (u 6-9)
    fish.set(6, 4, FISH["o"])
    fish.stamp(10, 0, ["bL", "dL", "dL", "bL"], FISH)                   # tail fan (u 10-12, v 0-4)
    fish.rect(10, 5, 14, 6, FISH["d"])                                  # tail's edges
    out["fish"] = fish
    return out


def crate_model():
    def el(name, f, t, faces, rotation=None):
        e = OrderedDict([("name", name), ("from", f), ("to", t)])
        if rotation:
            e["rotation"] = rotation
        e["faces"] = OrderedDict(faces)
        return e

    # The box itself stands a pixel up on its posts.
    els = [el("shell", [1, 1, 1], [15, 10, 15],
              [("north", {"texture": "#front"}), ("east", {"texture": "#side"}), ("south", {"texture": "#side"}),
               ("west", {"texture": "#side"}), ("up", {"texture": "#top"}), ("down", {"uv": [1, 1, 15, 15], "texture": "#side"})])]
    # Corner posts, standing proud of the shell at every corner.
    for name, x, z in (("post_nw", 0, 0), ("post_ne", 14, 0), ("post_sw", 0, 14), ("post_se", 14, 14)):
        els.append(el(name, [x, 0, z], [x + 2, 12, z + 2],
                      [(n, {"uv": [0, 0, 2, 2] if n in ("up", "down") else [0, 4, 2, 16], "texture": "#post"})
                       for n in ("north", "east", "south", "west", "up", "down")]))
    # The rim: a board along the top of each side between the posts (ends and undersides hidden, so left out).
    board = lambda: {"uv": [2, 6, 14, 8], "texture": "#side"}
    top_uv = {"uv": [2, 0, 14, 1], "texture": "#side"}
    els.append(el("rim_north", [2, 10, 1], [14, 12, 2], [("north", board()), ("south", board()), ("up", top_uv)]))
    els.append(el("rim_south", [2, 10, 14], [14, 12, 15], [("north", board()), ("south", board()), ("up", top_uv)]))
    side_board = lambda: {"uv": [2, 6, 14, 8], "texture": "#side"}
    side_top = {"uv": [2, 0, 14, 1], "rotation": 90, "texture": "#side"}
    els.append(el("rim_west", [1, 10, 2], [2, 12, 14], [("east", side_board()), ("west", side_board()), ("up", side_top)]))
    els.append(el("rim_east", [14, 10, 2], [15, 12, 14], [("east", side_board()), ("west", side_board()), ("up", side_top)]))

    # Three Fish Fish lying on their sides on the heap, turned a little this way and that.
    def lying(tag, x, z, flip, angle):
        rot = OrderedDict([("angle", angle), ("axis", "y"), ("origin", [x + 3.5, 10, z + 1.5])])
        hx0, hx1, tx0, tx1 = (x, x + 5, x + 5, x + 7) if not flip else (x + 2, x + 7, x, x + 2)
        flank = [5, 0, 0, 3] if flip else [0, 0, 5, 3]
        return [
            el(tag + "_body", [hx0, 10, z], [hx1, 11.5, z + 3],
               [("up", {"uv": flank, "texture": "#fish"}), ("north", {"uv": [0, 4, 5, 6], "texture": "#fish"}),
                ("south", {"uv": [0, 6, 5, 8], "texture": "#fish"}),
                ("west" if not flip else "east", {"uv": [6, 4, 9, 6], "texture": "#fish"})], rot),
            el(tag + "_tail", [tx0, 10, z - 0.5], [tx1, 11, z + 3.5],
               [("up", {"uv": [10, 0, 12, 4] if not flip else [12, 0, 10, 4], "texture": "#fish"}),
                ("north", {"uv": [10, 5, 12, 6], "texture": "#fish"}), ("south", {"uv": [10, 5, 12, 6], "texture": "#fish"}),
                ("east" if not flip else "west", {"uv": [10, 5, 14, 6], "texture": "#fish"})], rot),
        ]
    els += lying("fish_a", 2.5, 2.5, False, 22.5)
    els += lying("fish_c", 7, 6.5, True, -22.5)
    els += lying("fish_b", 3, 10.5, False, 22.5)
    fights = zfight_check(els)
    if fights:
        for f in fights:
            print("z-fight: fish_fish_crate", f)
        raise SystemExit("fish_fish_crate has z-fighting")
    return OrderedDict([
        ("parent", "minecraft:block/block"),
        ("textures", OrderedDict([("particle", "tomyen:block/fish_fish_crate_side"), ("side", "tomyen:block/fish_fish_crate_side"),
                                  ("front", "tomyen:block/fish_fish_crate_front"), ("top", "tomyen:block/fish_fish_crate_top"),
                                  ("post", "tomyen:block/fish_fish_crate_post"), ("fish", "tomyen:block/fish_fish_crate_fish")])),
        ("elements", els),
    ])


def write_crate():
    W = spruce_ramp()
    for name, canvas in crate_textures(W).items():
        png(os.path.join(TEX, "block", "fish_fish_crate_" + name + ".png"), 16, 16, canvas.px)
    write(os.path.join(ASSETS, "models", "block", "fish_fish_crate.json"), crate_model())
    variants = OrderedDict()
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = OrderedDict([("model", "tomyen:block/fish_fish_crate")])
        if y:
            v["y"] = y
        variants["facing=" + facing] = v
    write(os.path.join(ASSETS, "blockstates", "fish_fish_crate.json"), OrderedDict([("variants", variants)]))
    write(os.path.join(ASSETS, "items", "fish_fish_crate.json"),
          {"model": OrderedDict([("type", "minecraft:model"), ("model", "tomyen:block/fish_fish_crate")])})
    write(os.path.join(DATA, "tomyen", "loot_table", "blocks", "fish_fish_crate.json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("rolls", 1), ("entries", [OrderedDict([("type", "minecraft:item"), ("name", "tomyen:fish_fish_crate")])]),
                                ("conditions", [OrderedDict([("condition", "minecraft:survives_explosion")])])])]),
        ("random_sequence", "tomyen:blocks/fish_fish_crate")]))
    # A barrel and two Fish Fish; unlocked by catching one.
    name = "fish_fish_crate"
    write(os.path.join(DATA, "tomyen", "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", "misc"),
        ("ingredients", ["minecraft:barrel", "tomyen:fish_fish", "tomyen:fish_fish"]),
        ("result", OrderedDict([("count", 1), ("id", "tomyen:" + name)]))]))
    write(os.path.join(DATA, "tomyen", "advancement", "recipes", "misc", name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", OrderedDict([("conditions", OrderedDict([("items", [OrderedDict([("items", "tomyen:fish_fish")])])])),
                                      ("trigger", "minecraft:inventory_changed")])),
            ("has_the_recipe", OrderedDict([("conditions", OrderedDict([("recipes", "tomyen:" + name)])),
                                            ("trigger", "minecraft:recipe_unlocked")]))])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", OrderedDict([("recipes", ["tomyen:" + name])]))]))
    tag_add(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "axe.json"), ["tomyen:fish_fish_crate"])
    tag_add(os.path.join(DATA, "minecraft", "tags", "point_of_interest_type", "acquirable_job_site.json"), ["tomyen:fish_fish_crate"])
    return W


def tag_add(path, values):
    obj = OrderedDict([("values", [])])
    if os.path.exists(path):
        with open(path) as fh:
            obj = json.load(fh, object_pairs_hook=OrderedDict)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(path, obj)


# ============================================================================================================ trades

FISH_FISH = "tomyen:fish_fish"


def trophy(weight):
    """One of Josh's own Fish Fish, weighed and signed, as a trophy catch is (TrophyFish)."""
    return OrderedDict([("id", FISH_FISH), ("components", OrderedDict([
        ("tomyen:trophy", OrderedDict([("weight_lb", weight), ("average_lb", 2.0), ("caught_by", "Josh"), ("day", 1)])),
        ("minecraft:max_stack_size", 1),
        ("minecraft:item_name", OrderedDict([("translate", "item.tomyen.trophy_fish"), ("with", [{"translate": "item.tomyen.fish_fish"}]),
                                             ("color", "gold")]))]))])


def stack(count, item=FISH_FISH):
    g = OrderedDict([("id", item)])
    if count > 1:
        g["count"] = count
    return g


# level: [(name, emeralds, gives, max uses, xp)], and how many of them a Josh offers at that level.
TRADES = OrderedDict([
    (1, (2, [("fish_fish", 1, stack(1), 16, 2), ("handful_of_fish_fish", 2, stack(6), 16, 2)])),
    (2, (2, [("fish_fish_bucket", 3, stack(1, "tomyen:fish_fish_bucket"), 8, 10), ("dozen_fish_fish", 3, stack(12), 12, 10)])),
    (3, (2, [("stack_of_fish_fish", 12, stack(64), 4, 20), ("big_fish_fish", 6, trophy(8.0), 3, 20)])),
    (4, (1, [("trophy_fish_fish", 14, trophy(14.5), 2, 30)])),
    (5, (1, [("record_fish_fish", 32, trophy(20.0), 1, 30)])),
])


def write_trades():
    for level, (amount, trades) in TRADES.items():
        ids = []
        for name, emeralds, gives, uses, xp in trades:
            write(os.path.join(DATA, "tomyen", "villager_trade", "josh", str(level), "emerald_" + name + ".json"), OrderedDict([
                ("gives", gives), ("max_uses", uses), ("reputation_discount", 0.05),
                ("wants", OrderedDict([("id", "minecraft:emerald"), ("count", emeralds)])), ("xp", xp)]))
            ids.append("tomyen:josh/%d/emerald_%s" % (level, name))
        write(os.path.join(DATA, "tomyen", "trade_set", "josh", "level_%d.json" % level), OrderedDict([
            ("amount", amount), ("random_sequence", "tomyen:trade_set/josh/level_%d" % level), ("trades", ids)]))


# ============================================================================================================== lang

LINES = [
    "Fish Fish.",
    "Fish Fish?",
    "It's a fish. It's a Fish Fish.",
    "I only sell Fish Fish.",
    "Have you tried Fish Fish?",
    "Don't ask me about the other fish.",
    "Fish Fish: it's fish.",
    "Caught them all myself.",
]


def write_lang():
    entries = OrderedDict([
        ("block.tomyen.fish_fish_crate", "Fish Fish Crate"),
        ("entity.tomyen.villager.josh", "Josh"),
    ])
    for k, line in enumerate(LINES):
        entries["chat.tomyen.josh.%d" % k] = line
    with open(LANG) as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(LANG, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


# =========================================================================================================== preview

def villager_quads(textures):
    """Vanilla's VillagerModel at rest (y down, the face toward -z), as quads (corners for texel (0, 0), (1, 0) and
    (0, 1) of each face) over a 64x64 texture."""
    quads = []

    def cube(u, v, x0, y0, z0, w, h, d, grow=0.0, pose=None, pivot=(0, 0, 0)):
        lo = (x0 - grow, y0 - grow, z0 - grow)
        hi = (x0 + w + grow, y0 + h + grow, z0 + d + grow)
        X0, Y0, Z0 = lo
        X1, Y1, Z1 = hi
        # (rect, top-left corner, top-right corner, bottom-left corner), per ModelPart's polygons.
        faces = [
            ((u + d, v, w, d), (X0, Y0, Z1), (X1, Y0, Z1), (X0, Y0, Z0)),                  # top
            ((u + d + w, v, w, d), (X0, Y1, Z1), (X1, Y1, Z1), (X0, Y1, Z0)),              # bottom
            ((u, v + d, d, h), (X0, Y0, Z1), (X0, Y0, Z0), (X0, Y1, Z1)),                  # west
            ((u + d, v + d, w, h), (X0, Y0, Z0), (X1, Y0, Z0), (X0, Y1, Z0)),              # front
            ((u + d + w, v + d, d, h), (X1, Y0, Z0), (X1, Y0, Z1), (X1, Y1, Z0)),          # east
            ((u + 2 * d + w, v + d, w, h), (X1, Y0, Z1), (X0, Y0, Z1), (X1, Y1, Z1)),      # back
        ]
        for rect, a, b, dd in faces:
            pts = [a, b, tuple(b[k] + dd[k] - a[k] for k in range(3)), dd]
            if pose:
                pts = [tuple(apply(pose, [p[k] - pivot[k] for k in range(3)])[k] + pivot[k] for k in range(3)) for p in pts]
            quads.append((pts, rect))

    cube(0, 0, -4, -10, -4, 8, 10, 8)                                   # head
    cube(32, 0, -4, -10, -4, 8, 10, 8, grow=0.51)                       # hat
    cube(24, 0, -1, -3, -6, 2, 4, 2)                                    # nose
    cube(16, 20, -4, 0, -3, 8, 12, 6)                                   # body
    cube(0, 38, -4, 0, -3, 8, 20, 6, grow=0.5)                          # robe
    arms = rx(math.degrees(-0.75))
    for x in (-8, 4):
        cube(44, 22, x, 3 - 2, -1 - 2, 4, 8, 4, pose=arms, pivot=(0, 3, -1))
    cube(40, 38, -4, 3 + 2, -1 - 2, 8, 4, 4, pose=arms, pivot=(0, 3, -1))
    cube(0, 22, -4, 12, -2, 4, 12, 4)                                   # legs
    cube(0, 22, 0, 12, -2, 4, 12, 4)
    # The hat's brim: a 16 x 16 plane turned flat at the brow, rows back to front: its top face at (31, 48), its
    # underside at (48, 48) (mirrored left to right).
    quads.append(([(-8, -6, 8), (8, -6, 8), (8, -6, -8), (-8, -6, -8)], (31, 48, 16, 16)))
    quads.append(([(8, -5, 8), (-8, -5, 8), (-8, -5, -8), (8, -5, -8)], (48, 48, 16, 16)))
    return quads


def composite(*layers):
    out = list(layers[0])
    for layer in layers[1:]:
        for k, c in enumerate(layer):
            if c[3] > 0:
                out[k] = c
    return out


def render_villager(tex, view, W, H, scale):
    """Orthographic, each texel its own little quad, lit from above, depth-sorted per pixel."""
    img = [None] * (W * H)
    depth = [1e9] * (W * H)
    light = (0.3, -1.0, -0.6)
    ln = math.sqrt(sum(c * c for c in light))
    for pts, (u, v, fw, fh) in villager_quads(None):
        A, B, Cc, D = pts
        e1 = [B[i] - A[i] for i in range(3)]
        e2 = [D[i] - A[i] for i in range(3)]
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.6 + 0.4 * abs(sum(n[i] * light[i] for i in range(3))) / (nl * ln)
        for j in range(int(fh)):
            for i in range(int(fw)):
                col = tex[(v + j) * 64 + u + i]
                if col[3] < 128:
                    continue
                corners = []
                for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a / fw, b / fh
                    p = [A[k] + e1[k] * s + e2[k] * t for k in range(3)]
                    q = apply(view, [p[0], p[1] - 7.0, p[2]])
                    corners.append((W / 2 + q[0] * scale, H / 2 + q[1] * scale, q[2]))
                z = sum(c[2] for c in corners) / 4
                fill(img, depth, W, H, corners, z, tuple(int(col[c] * lit) for c in range(3)))
    return img


def fill(img, depth, w, h, corners, z, colour):
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    for py in range(max(0, int(min(ys))), min(h, int(math.ceil(max(ys))) + 1)):
        for px in range(max(0, int(min(xs))), min(w, int(math.ceil(max(xs))) + 1)):
            if inside4(corners, px + 0.5, py + 0.5) and z < depth[py * w + px]:
                depth[py * w + px] = z
                img[py * w + px] = colour


def inside4(poly, x, y):
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


def preview(path, josh):
    from gen_longswords import client_jar, read_png
    import preview_herbalism as ph
    jar = zipfile.ZipFile(client_jar())

    def vt(n):
        return read_png(jar.read("assets/minecraft/textures/entity/villager/%s.png" % n))[2]
    base = composite(vt("villager"), vt("type/plains"))
    josh_tex = composite(base, josh.px)
    fisher = composite(base, vt("profession/fisherman"))
    # Views in model space (y down, face toward -z): front, three-quarters from the front left, from behind.
    front = ry(0)
    three = mul(rx(-15), ry(35))
    back = ry(180)
    W, H, scale = 200, 300, 7.0
    cells = [render_villager(josh_tex, v, W, H, scale) for v in (front, three, back)]
    cells += [render_villager(fisher, v, W, H, scale) for v in (front, three)]
    # The crate, from both front corners, at the same scale (a block is 16 pixels, like the villager's).
    atlases = {}
    els = ph.prepare(ASSETS, "tomyen:block/fish_fish_crate", atlases)
    crate = [ph.scene(els, atlases, ph.FRONT_LEFT, W, H, (8, 6, 8), scale), ph.scene(els, atlases, ph.FRONT_RIGHT, W, H, (8, 6, 8), scale)]
    gap = 6
    cols = len(cells) + len(crate)
    sheet_w = cols * (W + gap) + gap
    sheet_h = H + 2 * gap
    out = [(46, 50, 58, 255)] * (sheet_w * sheet_h)
    for n, img in enumerate(cells + crate):
        ox = gap + n * (W + gap)
        for y in range(H):
            for x in range(W):
                c = img[y * W + x]
                out[(gap + y) * sheet_w + ox + x] = (c[:3] + (255,)) if c else (58, 62, 72, 255)
    png(path, sheet_w, sheet_h, out)
    print("preview:", path)


def main():
    preview_path = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else None
    del sys.argv[1:]                                 # gen_longswords.client_jar() reads argv[2] as a jar path
    josh = write_outfit()
    write_crate()
    write_trades()
    write_lang()
    if preview_path:
        preview(preview_path, josh)


if __name__ == "__main__":
    main()
