"""Builds the Accountant: his model (the cube layout in PARTS and SHELL, written into client/accountant/AccountantModel.java
between the GENERATED markers, with the reach of his audit pose), his entity texture, his spawn egg and the Extension Form
he carries, and their item models and definitions. On the data side it writes his loot table (by size: the Accountant
himself drops the Ledger and paperwork, his Junior Accountants slime and scrap paper). His sounds (vanilla slime, book and
cartography-table sounds, pitched) and lang entries are merged into sounds.json and en_us.json.

He's a slime in a charcoal pinstripe suit: a translucent jelly shell (its own layer, drawn see-through over the rest) round
an opaque core with two heavy-lidded eyes and a flat, unimpressed mouth. The double-breasted jacket wraps the lower half of
the cube, with lapels, a white shirt collar, a striped red tie that flies up when he hops, two horn buttons and a pocket
square. Stubby jelly hands come out of the sleeves; the right one holds a slim brown attache case with brass clasps, and
the left one stretches up through the jelly to push his round, dark-rimmed glasses up his face when he audits you. A
pencil rests on the glasses' right temple. The model is drawn at twice a slime's scale (the shell is 16 units) and the
renderer halves it, so his texture is as fine as a block's at his normal size.

Textures are painted in smooth ramps from a small palette (vanilla's slime greens, charcoal, shirt white, tie red, brass,
leather brown); no noise, no dithering, and a rerun gives the same files. The model is checked for z-fighting at rest
(tools/coplanar.py), both layers together, and its extents are printed (it should stand on y 24).

With --preview DIR it also renders preview_accountant.png into DIR (front, side, three-quarters and back at rest; mid-hop;
three stages of the audit; and the face up close) and preview_accountant_texture.png, using the same cubes, pivots and UV
layout as the Java model and a Python twin of its animation.

Usage: python3 tools/gen_accountant.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None


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


CLEAR = (0, 0, 0, 0)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h, self.px = w, h, [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1 (no banding beyond the texel grid, no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


# ---------------------------------------------------------------------------------------------- palette
# Vanilla's slime greens (textures/entity/slime/slime.png), light to dark.
JELLY = [(150, 222, 132), (123, 206, 106), (115, 194, 98), (98, 182, 74)]
CORE = [(140, 204, 122), (126, 191, 110), (117, 185, 100), (98, 170, 80), (81, 160, 62), (66, 138, 50)]
EYE = (22, 40, 16)
EYE_GLINT = (176, 222, 160)
LID = (81, 160, 62)
# A charcoal suit, light to dark, and its pinstripe.
SUIT = [(92, 95, 104), (74, 77, 85), (60, 62, 70), (47, 49, 56), (36, 37, 43)]
PINSTRIPE_LIFT = 10
SHIRT = [(238, 238, 230), (214, 214, 206), (184, 184, 178)]
TIE = [(214, 64, 58), (182, 38, 40), (140, 26, 32), (100, 18, 24)]
BRASS = [(250, 222, 132), (220, 178, 76), (168, 124, 44), (112, 80, 28)]
LEATHER = [(150, 100, 60), (124, 80, 46), (100, 62, 36), (74, 44, 26)]
HORN = [(118, 90, 66), (78, 59, 44)]
FRAME = [(64, 56, 52), (38, 33, 31), (24, 21, 20)]
LENS_GLINT = (232, 244, 250)
PENCIL = [(246, 206, 74), (226, 178, 46), (186, 140, 30)]
WOOD = (226, 196, 150)
LEAD = (58, 58, 64)
ERASER = (232, 148, 156)
FERRULE = [(204, 204, 196), (150, 150, 144)]
EMERALD = [(118, 236, 152), (40, 180, 90)]

TEX_W, TEX_H = 128, 128
SHELL_ALPHA, SHELL_EDGE_ALPHA = 128, 196


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out. 'top' and
    'bottom' are as seen in the world (the model is upside down); in both the front edge is the last row. In 'right'
    (the cube's -x side, the creature's right) the front edge is the last column, in 'left' the first. In 'front', column
    0 is the creature's right."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, uv, size, painters):
    """painters: face name -> f(x, y, fw, fh) -> colour (rgb, rgba, or None for clear)."""
    u, v = uv
    w, h, d = size
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name)
        if f is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = f(x, y, fw, fh)
                canvas.set(fu + x, fv + y, rgba(c) if c is not None and len(c) == 3 else (c or CLEAR))


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored), with `from` relative
# to the part's pivot. Pivots are relative to the parent's. Mirrored cubes share their texture with the unmirrored one of
# the same key. Model space is the game's: y down, the front toward -z, the ground at y 24; the shell is 16 units (the
# renderer halves the whole model, then scales it by the slime's size).
#
# Absolute positions are easier to think in, so parts are written with absolute pivots and cubes (A below) and turned
# relative; a part's cubes are given as they'd be with the part unrotated.
SIDES = (("right", -1, False), ("left", 1, True))


def parts():
    raw = [
        ("body", None, (0.0, 24.0, 0.0), (0.0, 0.0, 0.0), [
            ("core", (-5.0, 11.0, -5.0), (10, 10, 10), False),
            ("eye", (-4.0, 9.0, -8.25), (2, 2, 2), False),
            ("eye", (2.0, 9.0, -8.25), (2, 2, 2), True),
            ("mouth", (-1.0, 12.5, -8.25), (2, 1, 1), False),
            ("jacket", (-8.5, 14.0, -8.5), (17, 9, 17), False),
            ("collar", (-2.5, 13.75, -9.15), (2, 2, 1), False),
            ("collar", (0.5, 13.75, -9.15), (2, 2, 1), True),
            ("knot", (-1.0, 13.6, -9.5), (2, 2, 1), False),
            ("button", (-4.0, 19.5, -9.0), (1, 1, 1), False),
            ("button", (3.0, 19.5, -9.0), (1, 1, 1), True),
            ("pocket_square", (4.5, 15.0, -9.0), (2, 1, 1), False)]),
        ("tie", "body", (0.0, 15.6, -9.0), (0.0, 0.0, 0.0), [
            ("tie", (-1.5, 15.6, -9.25), (3, 7, 1), False)]),
        ("pencil", "body", (-9.0, 8.0, -3.0), (0.22, 0.0, 0.0), [
            ("pencil", (-9.5, 7.5, -6.0), (1, 1, 6), False)]),
        ("glasses", "body", (0.0, 10.0, -9.5), (0.0, 0.0, 0.0), [
            ("lens", (-5.0, 8.0, -10.0), (4, 4, 1), False),
            ("lens", (1.0, 8.0, -10.0), (4, 4, 1), True),
            ("bridge", (-1.0, 9.0, -9.75), (2, 1, 1), False),
            ("hinge", (-7.75, 9.0, -9.75), (3, 1, 1), False),
            ("hinge", (4.75, 9.0, -9.75), (3, 1, 1), True),
            ("temple", (-8.75, 9.0, -9.75), (1, 1, 8), False),
            ("temple", (7.75, 9.0, -9.75), (1, 1, 8), True)]),
    ]
    for side, s, mirror in SIDES:
        raw += [
            # Lapels slant in from the shoulders to the buttons.
            (side + "_lapel", "body", (s * 2.5, 14.0, -8.5), (0.0, 0.0, s * 0.4), [
                ("lapel", (s * 2.5 - 1.0, 14.0, -9.0 - 0.1 * (s > 0)), (2, 6, 1), mirror)]),
            (side + "_arm", "body", (s * 9.5, 14.5, 0.0), (0.0, 0.0, 0.0), [
                ("sleeve", (s * 9.5 - 1.5, 14.5, -1.5), (3, 4, 3), mirror)]),
            (side + "_hand", side + "_arm", (s * 9.5, 18.5, 0.0), (0.0, 0.0, 0.0), [
                ("hand", (s * 9.5 - 1.0, 18.5, -1.0), (2, 2, 2), mirror)]),
        ]
    raw += [
        ("case", "right_hand", (-9.5, 20.0, 0.0), (0.0, 0.0, 0.0), [
            ("handle", (-10.0, 19.25, -2.0), (1, 1, 4), False),
            ("case", (-11.0, 20.25, -3.5), (2, 3, 7), False),
            ("clasp", (-11.5, 20.75, -2.5), (1, 1, 1), False),
            ("clasp", (-11.5, 20.75, 1.5), (1, 1, 1), False)]),
    ]
    absolute = {name: pivot for name, _, pivot, _, _ in raw}
    out = []
    for name, parent, pivot, rot, cubes in raw:
        base = absolute[parent] if parent else (0.0, 0.0, 0.0)
        rel = tuple(pivot[i] - base[i] for i in range(3))
        out.append((name, parent, rel, rot,
                    [(k, tuple(frm[i] - pivot[i] for i in range(3)), size, m) for k, frm, size, m in cubes]))
    return out


PARTS = parts()
# The jelly shell, drawn translucent in its own layer.
SHELL = [("shell", None, (0.0, 24.0, 0.0), (0.0, 0.0, 0.0), [("shell", (-8.0, -16.0, -8.0), (16, 16, 16), False)])]


def tree(parts_):
    t = {}
    for name, parent, *_ in parts_:
        t.setdefault(parent or "root", []).append(name)
    return t


TREE, SHELL_TREE = tree(PARTS), tree(SHELL)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS + SHELL}


def pack():
    """Texture offsets for each cube key, shelf-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS + SHELL:
        for key, _, size, _ in cubes:
            assert sizes.setdefault(key, size) == size, key
    order = sorted(sizes, key=lambda k: (-(sizes[k][2] + sizes[k][1]), k))
    offs, x, y, shelf = {}, 0, 0, 0
    for k in order:
        w, h, d = sizes[k]
        fw, fh = 2 * (w + d), d + h
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        offs[k] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= TEX_H, "texture too small"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


def cube_count():
    return sum(len(c) for _, _, _, _, c in PARTS + SHELL)


# ---------------------------------------------------------------------------------------------- painting
def vgrad(cols, top=0.0, bottom=1.0):
    """Down a face, from `top` to `bottom` along the ramp."""
    return lambda x, y, w, h: ramp(cols, top + (bottom - top) * (y / max(1, h - 1)))


def flat(c):
    return lambda x, y, w, h: c


def suit(top=0.05, bottom=0.75, stripes=True, phase=0):
    """The charcoal suit down a face, lighter where the shoulders catch the light; a faint pinstripe every third column."""
    def f(x, y, w, h):
        c = ramp(SUIT, top + (bottom - top) * (y / max(1, h - 1)))
        if stripes and (x + phase) % 3 == 1:
            c = tuple(min(255, v + PINSTRIPE_LIFT) for v in c)
        return c
    return f


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    P = lambda key, painters: paint(tex, *CUBES[key], painters)

    # The shell: see-through jelly, a touch lighter round its edges (as vanilla's slime has it) and a soft glint up in
    # the top corner of the face. Lighter at the top, a little denser toward the bottom.
    def shell(glint):
        def f(x, y, w, h):
            edge = x in (0, w - 1) or y in (0, h - 1)
            if edge:
                return rgba(JELLY[0], SHELL_EDGE_ALPHA)
            if glint and (x, y) in ((2, 2), (3, 2), (2, 3)):
                return rgba(lerp(JELLY[0], (255, 255, 255), 0.55), 200)
            if glint and (x, y) in ((4, 2), (2, 4)):
                return rgba(JELLY[0], 170)
            return rgba(ramp(JELLY, 0.25 + 0.6 * y / (h - 1)), SHELL_ALPHA)
        return f
    P("shell", {"front": shell(True), "back": shell(False), "right": shell(False), "left": shell(True),
                "top": shell(True), "bottom": shell(False)})

    # The core: solid jelly, lit from above.
    core_side = vgrad(CORE, 0.1, 0.9)
    P("core", {"front": core_side, "back": core_side, "right": core_side, "left": core_side,
               "top": flat(CORE[0]), "bottom": flat(CORE[5])})

    # Eyes: heavy-lidded (a band of jelly across the top), dark, with a glint.
    def eye_front(x, y, w, h):
        if y == 0:
            return LID
        if (x, y) == (0, 1):
            return EYE_GLINT
        return EYE
    P("eye", {"front": eye_front, "back": flat(EYE), "right": lambda x, y, w, h: LID if y == 0 else EYE,
              "left": lambda x, y, w, h: LID if y == 0 else EYE, "top": flat(LID), "bottom": flat(EYE)})
    P("mouth", {k: flat(EYE) for k in ("front", "back", "right", "left", "top", "bottom")})

    # The jacket. Front: a V of white shirt down the middle (the 3D tie, collar and lapels sit on it), pocket flaps, a
    # darker hem. Its top and bottom are open (the jelly's inside it): only a one-texel rim of cloth.
    def jacket_front(x, y, w, h):
        mid = (w - 1) / 2
        half = 2.6 - 0.55 * y
        if abs(x - mid) <= half:
            # Shirt, shaded where the lapels overhang it.
            return SHIRT[1] if abs(x - mid) > half - 1 else SHIRT[0]
        if y == h - 1:
            return SUIT[4]
        if y == 6 and (1 <= x <= 4 or w - 5 <= x <= w - 2):
            return SUIT[4]          # pocket flaps
        return suit(0.05, 0.7)(x, y, w, h)

    def jacket_back(x, y, w, h):
        mid = (w - 1) / 2
        if y >= h - 4 and x == round(mid):
            return SUIT[4]          # the vent
        if y == h - 1:
            return SUIT[4]
        return suit(0.15, 0.8)(x, y, w, h)

    def jacket_side(x, y, w, h):
        return SUIT[4] if y == h - 1 else suit(0.1, 0.75, phase=1)(x, y, w, h)

    def rim(fill_front=False):
        def f(x, y, w, h):
            if not (x in (0, w - 1) or y in (0, h - 1)):
                return None
            mid = (w - 1) / 2
            if fill_front and y == h - 1 and abs(x - mid) <= 2.6:
                return SHIRT[1]     # the shirt's neckline at the front
            return SUIT[1] if fill_front else SUIT[4]
        return f
    P("jacket", {"front": jacket_front, "back": jacket_back, "right": jacket_side, "left": jacket_side,
                 "top": rim(True), "bottom": rim(False)})

    # Lapels: a shade lighter (the cloth turns to the light), with a dark edge on the inside; a tiny emerald pin in
    # the left one (painted on both, mirrored; the right one gets a buttonhole instead).
    def lapel_front(x, y, w, h):
        if x == w - 1:
            return SUIT[3]
        return ramp(SUIT, 0.0 + 0.4 * y / (h - 1))
    P("lapel", {"front": lapel_front, "back": flat(SUIT[3]), "right": vgrad(SUIT, 0.2, 0.6), "left": vgrad(SUIT, 0.2, 0.6),
                "top": flat(SUIT[0]), "bottom": flat(SUIT[3])})

    # Shirt collar points: triangles pointing down to the knot.
    def collar_front(x, y, w, h):
        if y == h - 1 and x == 0:
            return None
        return SHIRT[0] if y == 0 else SHIRT[1]
    P("collar", {"front": collar_front, "back": flat(SHIRT[2]), "right": flat(SHIRT[1]), "left": flat(SHIRT[1]),
                 "top": flat(SHIRT[0]), "bottom": flat(SHIRT[2])})

    # The tie: knot, and a blade that widens from the knot and comes to a point, with thin dark diagonal stripes.
    P("knot", {"front": lambda x, y, w, h: TIE[1] if y == 0 else TIE[2], "back": flat(TIE[3]),
               "right": flat(TIE[2]), "left": flat(TIE[2]), "top": flat(TIE[0]), "bottom": flat(TIE[3])})

    def tie_shape(x, y, w, h):
        return not ((y == 0 or y == h - 1) and x != w // 2)

    def tie_front(x, y, w, h):
        if not tie_shape(x, y, w, h):
            return None
        if (x + y) % 4 == 0:
            return TIE[3]
        return ramp(TIE, 0.1 + 0.35 * y / (h - 1))

    def tie_edge(x, y, w, h):
        return None if y in (0, h - 1) else TIE[2]
    P("tie", {"front": tie_front, "back": lambda x, y, w, h: TIE[3] if tie_shape(x, y, w, h) else None,
              "right": tie_edge, "left": tie_edge, "top": lambda x, y, w, h: TIE[1] if x == w // 2 else None,
              "bottom": lambda x, y, w, h: TIE[3] if x == w // 2 else None})

    P("button", {"front": flat(HORN[0]), "back": flat(HORN[1]), "right": flat(HORN[1]), "left": flat(HORN[1]),
                 "top": flat(HORN[0]), "bottom": flat(HORN[1])})
    P("pocket_square", {"front": lambda x, y, w, h: SHIRT[0], "back": flat(SHIRT[2]), "right": flat(SHIRT[1]),
                        "left": flat(SHIRT[1]), "top": lambda x, y, w, h: SHIRT[0], "bottom": flat(SHIRT[2])})

    # Sleeves: the suit, with the white shirt cuff showing at the bottom; the hand's jelly fills the opening.
    def sleeve_side(x, y, w, h):
        return SHIRT[0] if y == h - 1 else suit(0.1, 0.6, phase=x)(x, y, w, h)
    P("sleeve", {"front": sleeve_side, "back": sleeve_side, "right": sleeve_side, "left": sleeve_side,
                 "top": flat(SUIT[1]), "bottom": lambda x, y, w, h: CORE[3] if (x, y) == (1, 1) else SHIRT[1]})
    hand_side = vgrad(CORE, 0.2, 0.75)
    P("hand", {"front": hand_side, "back": hand_side, "right": hand_side, "left": hand_side, "top": flat(CORE[2]),
               "bottom": flat(CORE[4])})

    # Round glasses: a ring of dark frame, clear in the middle so his eyes show through, one glint.
    RING = {(1, 0), (2, 0), (0, 1), (3, 1), (0, 2), (3, 2), (1, 3), (2, 3)}

    def lens_face(glint):
        def f(x, y, w, h):
            if (x, y) in RING:
                return FRAME[0] if y == 0 else FRAME[1]
            if glint and (x, y) == (1, 1):
                return LENS_GLINT
            return None
        return f
    P("lens", {"front": lens_face(True), "back": lens_face(False),
               "right": lambda x, y, w, h: FRAME[1] if 1 <= y <= 2 else None,
               "left": lambda x, y, w, h: FRAME[1] if 1 <= y <= 2 else None,
               "top": lambda x, y, w, h: FRAME[0] if 1 <= x <= 2 else None,
               "bottom": lambda x, y, w, h: FRAME[2] if 1 <= x <= 2 else None})
    for key in ("bridge", "hinge", "temple"):
        P(key, {"front": flat(FRAME[1]), "back": flat(FRAME[1]), "right": flat(FRAME[1]), "left": flat(FRAME[1]),
                "top": flat(FRAME[0]), "bottom": flat(FRAME[2])})

    # The pencil, front to back: lead, sharpened wood, yellow paint, the ferrule and a pink eraser.
    def pencil_len(front_last):
        def f(x, y, w, h):
            i = (w - 1 - x) if front_last else x   # 0 at the front
            return [WOOD, PENCIL[0], PENCIL[1], PENCIL[1], FERRULE[0], ERASER][i]
        return f

    def pencil_topdown(x, y, w, h):
        i = h - 1 - y                              # front edge is the last row
        return [WOOD, PENCIL[0], PENCIL[0], PENCIL[1], FERRULE[0], ERASER][i]
    P("pencil", {"front": flat(LEAD), "back": flat(ERASER), "right": pencil_len(True), "left": pencil_len(False),
                 "top": pencil_topdown, "bottom": lambda x, y, w, h: lerp(pencil_topdown(x, y, w, h), (0, 0, 0), 0.25)})

    # The attache case: brown leather, the lid's seam a dark line, brass caps at the corners; a darker handle.
    def case_side(x, y, w, h):
        if (x in (0, w - 1)) and y in (0, h - 1):
            return BRASS[1]
        if y == 1:
            return LEATHER[3]
        return LEATHER[0] if y == 0 else LEATHER[1]

    def case_end(x, y, w, h):
        return LEATHER[3] if y == 1 else LEATHER[2]
    P("case", {"right": case_end, "left": case_end, "front": case_side, "back": case_side,
               "top": lambda x, y, w, h: LEATHER[0], "bottom": flat(LEATHER[3])})
    P("handle", {k: flat(LEATHER[3]) for k in ("front", "back", "right", "left", "bottom")} | {"top": flat(LEATHER[2])})
    P("clasp", {"right": flat(BRASS[0]), "left": flat(BRASS[2]), "front": flat(BRASS[1]), "back": flat(BRASS[2]),
                "top": flat(BRASS[0]), "bottom": flat(BRASS[3])})

    tex.save("entity/accountant/accountant.png")
    return tex


TEXTURE = paint_entity()


# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# Extension Form: a sheet of paper, ruled, with a red "APPROVED"-style stamp in the corner and a brass paper clip.
FORM = ["....ccc.........",
        "....c.C.........",
        "..ooc.Cooooooo..",
        "..owc.Cwwwwwwo..",
        "..owwCCllllwwo..",
        "..owwwwwwwwwwo..",
        "..owlllllllwwo..",
        "..owwwwwwwwwwo..",
        "..owllllllwwwo..",
        "..owwwwwwwwwwo..",
        "..owlllwwrrrwo..",
        "..owwwwwrRRrwo..",
        "..owsssswrrrwo..",
        "..owwwwwwwwwso..",
        "..oooooooooooo..",
        "................"]
sprite(FORM, {"o": (150, 146, 130), "w": SHIRT[0], "l": (150, 164, 196), "c": BRASS[1], "C": BRASS[2],
              "r": TIE[1], "R": TIE[3], "s": (60, 60, 70)}).save("item/extension_form.png")

# Spawn egg: the shared egg shape; jelly green above with his eyes behind dark round glasses, the charcoal jacket below
# with the white V and red tie.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
top_y = min(ys)
jacket_y = round(cy) + 1
egg = Canvas(w, h)
GLASSES = {(5, 6), (6, 6), (9, 6), (10, 6), (4, 7), (7, 7), (8, 7), (11, 7), (5, 8), (6, 8), (9, 8), (10, 8)}
PUPILS = {(5, 7), (6, 7), (9, 7), (10, 7)}
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    light = (cx - x) + (cy - y)
    if outline:
        egg.px[i] = rgba(SUIT[4] if y >= jacket_y else CORE[5])
    elif y < jacket_y:
        if (x, y) in GLASSES:
            egg.px[i] = rgba(FRAME[1])
        elif (x, y) in PUPILS:
            egg.px[i] = rgba(EYE)
        else:
            egg.px[i] = rgba(JELLY[0] if light > 5 else JELLY[1] if light > 1 else JELLY[3])
    else:
        half = 2.0 - 0.6 * (y - jacket_y)
        if abs(x - cx) <= 0.6 and y < jacket_y + 4:
            egg.px[i] = rgba(TIE[1])
        elif abs(x - cx) <= half:
            egg.px[i] = rgba(SHIRT[0])
        else:
            egg.px[i] = rgba(SUIT[1] if light > 0 else SUIT[2] if light > -4 else SUIT[3])
egg.save("item/accountant_spawn_egg.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge(path, entries):
    """Adds entries to a JSON object file others also write (lang, sounds.json), keeping what's there and its order."""
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


for name in ("extension_form", "accountant_spawn_egg"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def sized(low, high):
    return {"type": "minecraft:entity_properties", "entity": "this",
            "predicate": {"minecraft:type_specific/cube_mob": {"size": {"min": low, "max": high} if high else low}}}


def drop(item, low, high, looting=1.0, condition=None):
    pool = OrderedDict([("rolls", 1)])
    if condition:
        pool["condition"] = condition
    count = low if low == high else {"type": "minecraft:uniform", "min": low, "max": high}
    modifiers = [{"type": "minecraft:set_count", "count": count}]
    if looting:
        modifiers.append({"type": "minecraft:enchanted_count_increase",
                          "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting}, "enchantment": "minecraft:looting"})
    pool["entries"] = [{"type": "minecraft:item", "name": item, "modifier": modifiers}]
    return pool


SENIOR, JUNIOR = sized(2, 127), sized(1, None)
write(os.path.join(DATA, "loot_table/entities/accountant.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("tomyen:ledger", 1, 1, 0.0, SENIOR), drop("tomyen:extension_form", 1, 1, 1.0, SENIOR),
               drop("minecraft:paper", 1, 3, 1.0, SENIOR),
               drop("minecraft:slime_ball", 0, 2, 1.0, JUNIOR), drop("minecraft:paper", 0, 1, 0.0, JUNIOR)]),
    ("random_sequence", "tomyen:entities/accountant")]))


def vanilla(names, pitch, volume=None):
    out = []
    for n in names:
        e = OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)])
        if volume is not None:
            e["volume"] = volume
        out.append(e)
    return out


SMALL = ["mob/slime/small%d" % i for i in range(1, 6)]
BIG = ["mob/slime/big%d" % i for i in range(1, 5)]
SOUNDS = OrderedDict([
    ("accountant_hop", OrderedDict([("subtitle", "subtitles.tomyen.accountant_hop"), ("sounds", vanilla(SMALL, 1.35, 0.7))])),
    ("accountant_squish", OrderedDict([("subtitle", "subtitles.tomyen.accountant_squish"), ("sounds", vanilla(SMALL, 1.55, 0.5))])),
    ("accountant_hurt", OrderedDict([("subtitle", "subtitles.tomyen.accountant_hurt"), ("sounds", vanilla(BIG, 1.3))])),
    ("accountant_death", OrderedDict([("subtitle", "subtitles.tomyen.accountant_death"), ("sounds", vanilla(BIG, 1.1))])),
    ("accountant_papers", OrderedDict([("subtitle", "subtitles.tomyen.accountant_papers"),
                                       ("sounds", vanilla(["item/book/open_flip%d" % i for i in (1, 2, 3)], 1.1))])),
    ("accountant_scribble", OrderedDict([("subtitle", "subtitles.tomyen.accountant_scribble"),
                                         ("sounds", vanilla(["ui/cartography_table/drawmap%d" % i for i in (1, 2, 3)], 1.2))])),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.tomyen.accountant", "Accountant"),
    ("entity.tomyen.junior_accountant", "Junior Accountant"),
    ("item.tomyen.accountant_spawn_egg", "Accountant Spawn Egg"),
    ("item.tomyen.extension_form", "Extension Form"),
    ("item.tomyen.extension_form.hint", "Use: push your repayment deadline back one in-game day (up to the full term)"),
    ("item.tomyen.extension_form.hint2", "Not accepted once your account is in collections"),
    ("container.tomyen.loan_office", "Loan Office"),
    ("container.tomyen.loan_office.owed", "You owe: %s"),
    ("container.tomyen.loan_office.nothing_owed", "You owe: nothing"),
    ("container.tomyen.loan_office.due", "Due in %s"),
    ("container.tomyen.loan_office.collections", "IN COLLECTIONS: no further credit"),
    ("container.tomyen.loan_office.headroom", "Credit left: %s (cap %s)"),
    ("container.tomyen.loan_office.terms", "%s%% interest. Due with the rest of your debt."),
    ("container.tomyen.loan_office.borrow", "Borrow %s"),
    ("container.tomyen.loan_office.borrow.tooltip", "Get %s emeralds now; %s added to what you owe"),
    ("container.tomyen.loan_office.borrow.declined", "Over your limit: you'd owe %s, the cap is %s"),
    ("message.tomyen.accountant.loan", "Loan approved: %s emeralds. %s added to your account (%s owed in all). Sign here. And here."),
    ("message.tomyen.accountant.loan_declined", "Declined. That would put you at %s emeralds owed; the bank's cap is %s."),
    ("message.tomyen.accountant.loan_frozen", "Your account is in collections. The bank isn't lending you a pebble."),
    ("message.tomyen.accountant.intern", "Junior Accountant: \"I'm only an intern. Please see my supervisor.\" *glances at the puddle* \"...Ah.\""),
    ("message.tomyen.accountant.audit.0", "*adjusts glasses* Per our records: %s emeralds outstanding, due in %s. I'll make a note."),
    ("message.tomyen.accountant.audit.1", "*adjusts glasses* Audit complete. Balance: %s emeralds. Time remaining: %s. Tick tock."),
    ("message.tomyen.accountant.audit.2", "*adjusts glasses* You owe %s emeralds, due in %s. I am not impressed, but I am rarely impressed."),
    ("message.tomyen.accountant.audit.3", "*adjusts glasses* %s emeralds. %s. Interest never sleeps, and neither do I."),
    ("message.tomyen.accountant.overdue", "*adjusts glasses* Account %s emeralds overdue. Seizing liquid assets under clause 14(b)."),
    ("message.tomyen.accountant.seized", "Seized %s emeralds from your pockets toward the debt."),
    ("message.tomyen.accountant.broke", "No liquid assets found. This will go in your file."),
    ("message.tomyen.accountant.assaulted", "\"That will be noted.\""),
    ("message.tomyen.accountant.fined", "Destruction of bank property: %s emeralds added to your account."),
    ("message.tomyen.accountant.fined_junior", "Intern replacement fee: %s emeralds added to your account."),
    ("message.tomyen.accountant.escalated", "Assaulting an auditor. Your file has been escalated: the collectors are on their way now."),
    ("message.tomyen.accountant.extended", "Extension granted. Your deadline is now %s away."),
    ("message.tomyen.accountant.extension_nothing", "There's no debt to extend."),
    ("message.tomyen.accountant.extension_full", "Your deadline is already as far off as the bank allows."),
    ("message.tomyen.accountant.extension_late", "Too late for paperwork: your account is in collections."),
    ("subtitles.tomyen.accountant_hop", "Accountant hops"),
    ("subtitles.tomyen.accountant_squish", "Accountant squishes"),
    ("subtitles.tomyen.accountant_hurt", "Accountant hurts"),
    ("subtitles.tomyen.accountant_death", "Accountant dies"),
    ("subtitles.tomyen.accountant_papers", "Papers rustle"),
    ("subtitles.tomyen.accountant_scribble", "Pen scratches"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose(parts_=None):
    return {name: [pivot, rot, (1.0, 1.0, 1.0)] for name, _, pivot, rot, _ in (parts_ or PARTS)}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


def rot_matrix(rx, ry, rz):
    # Rz * Ry * Rx, as Quaternionf.rotationZYX.
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Rz, mul(Ry, Rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


# The audit reach: the left arm swings up and in from the shoulder toward the bridge of his glasses, and the hand's jelly
# stretches out of the cuff the rest of the way. Worked out here from the model, written into the Java model.
SHOULDER = (9.5, 14.5, 0.0)
SLEEVE_LEN = 4.0
HAND_LEN = 2.0
BRIDGE_TOUCH = (0.6, 10.4, -10.6)


def reach():
    d = tuple(BRIDGE_TOUCH[i] - SHOULDER[i] for i in range(3))
    dist = math.sqrt(sum(c * c for c in d))
    u = tuple(c / dist for c in d)
    # Rest direction (0, 1, 0) through Ry * Rx: (sin x sin y, cos x, sin x cos y).
    xr = -math.acos(max(-1.0, min(1.0, u[1])))
    sx = math.sin(xr)
    yr = math.atan2(u[0] / sx, u[2] / sx)
    stretch = (dist - SLEEVE_LEN) / HAND_LEN
    return xr, yr, stretch


REACH_X, REACH_Y, REACH_STRETCH = reach()


def smooth(a, b, t):
    t = max(0.0, min(1.0, (t - a) / (b - a)))
    return t * t * (3 - 2 * t)


def audit_curves(a):
    """Raise (arm up), push (glasses up), read (nodding over the figures), 0..1 each, through an audit 0..1."""
    raise_ = smooth(0.0, 0.18, a) * (1.0 - smooth(0.42, 0.56, a))
    push = smooth(0.2, 0.3, a) * (1.0 - smooth(0.52, 0.7, a))
    read = smooth(0.55, 0.65, a) * (1.0 - smooth(0.92, 1.0, a))
    return raise_, push, read


def animate(pose, age=0.0, squish=0.0, audit=0.0):
    """Python twin of AccountantModel.setupAnim, for the preview."""
    air = max(0.0, squish)
    land = max(0.0, -squish) * 2.0
    # The tie flies up and the arms flare out in the air; the glasses jolt down on landing. A little idle sway.
    add(pose, "tie", dx=-1.1 * air + 0.06 * math.sin(age * 0.11), dz=0.05 * math.sin(age * 0.07))
    add(pose, "right_arm", dx=0.06 * math.sin(age * 0.09), dz=0.35 * air)
    add(pose, "left_arm", dx=-0.06 * math.sin(age * 0.09), dz=-0.35 * air)
    add(pose, "case", dx=-0.06 * math.sin(age * 0.09) + 0.08 * math.sin(age * 0.13), dz=-0.3 * air)
    add(pose, "glasses", pos=(0.0, 0.5 * land, 0.0))
    if audit > 0.0:
        raise_, push, read = audit_curves(audit)
        add(pose, "left_arm", dx=REACH_X * raise_, dy=REACH_Y * raise_)
        hand = pose["left_hand"]
        stretch = 1.0 + (REACH_STRETCH - 1.0) * raise_ + 0.25 * push
        thin = max(0.55, 1.0 / math.sqrt(stretch))
        hand[2] = (thin, stretch, thin)
        add(pose, "glasses", dx=0.1 * read * math.sin(audit * 40.0), pos=(0.0, -0.9 * push, 0.0))
        add(pose, "case", dx=0.25 * read)
        add(pose, "right_arm", dx=-0.3 * read)
    return pose


def quads(pose, tree_, labels=False, squish=0.0):
    """Every textured face of the posed model: (corners in model space, uv rect[, label, outward normal])."""
    out = []
    ss = squish / (1 * 0.5 + 1.0)
    wsc = 1.0 / (ss + 1.0)

    def squash(p):
        # The renderer's squish, about the ground under him.
        return (p[0] * wsc, 24.0 + (p[1] - 24.0) / wsc, p[2] * wsc)

    def walk(name, matrix, origin):
        for child in tree_.get(name, []):
            pivot, rot, scale = pose[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            m = mul(m, [[scale[0], 0, 0], [0, scale[1], 0], [0, 0, scale[2]]])
            for k, (cube, frm, size, mirror) in enumerate(PART_CUBES.get(child, [])):
                (u, v), _ = CUBES[cube]
                for verts, uv in cube_faces(m, o, u, v, frm, size, mirror):
                    verts = [squash(p) for p in verts]
                    if labels:
                        centre = squash(tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3)))
                        fc = tuple(sum(p[i] for p in verts) / 4 for i in range(3))
                        out.append((verts, uv, f"{child}.{cube}{k}", tuple(fc[i] - centre[i] for i in range(3))))
                    else:
                        out.append((verts, uv))
            walk(child, m, o)

    walk("root", [[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size, mirror):
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    if mirror:
        x0, x1 = x1, x0
    P = lambda x, y, z: tuple(o[i] + apply(m, (x, y, z))[i] for i in range(3))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1), ([t2, t3, l3, l2], u2, v1, u22, v0), ([t0, l0, l3, t3], u0, v1, u1, v2),
             ([t1, t0, t3, t2], u1, v1, u2, v2), ([l1, t1, t2, l2], u2, v1, u3, v2), ([l0, l1, l2, l3], u3, v1, u4, v2)]
    return [(verts, (a0, b0, a1, b1)) for verts, a0, b0, a1, b1 in polys]


# Z-fighting: no two faces of the model at rest (both layers, drawn together) may share a plane, face the same way and
# overlap.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from coplanar import Face, fights
_faces = [Face(label, verts, normal) for verts, uv, label, normal in quads(rest_pose(), TREE, labels=True)]
_faces += [Face(label, verts, normal) for verts, uv, label, normal in quads(rest_pose(SHELL), SHELL_TREE, labels=True)]
_hits = fights(_faces)
if _hits:
    for h_ in _hits:
        print("z-fight: accountant", h_)
    raise SystemExit(f"accountant: {len(_hits)} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, _ in quads(pose, TREE) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
print(f"accountant: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f}, "
      f"bottom y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}; "
      f"audit reach x {REACH_X:.3f} y {REACH_Y:.3f} stretch {REACH_STRETCH:.2f}")


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "accountant", "AccountantModel.java"))


def f(v):
    return f"{v:.3f}f".replace("-0.000f", "0.000f")


def java_layer(parts_):
    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in parts_:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size, mirror in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}){'.mirror()' if mirror else ''}.addBox({', '.join(f(c) for c in frm)}, " \
                       f"{', '.join(f(c) for c in size)}){'.mirror(false)' if mirror else ''}"
        pose = (f"PartPose.offsetAndRotation({', '.join(f(c) for c in pivot)}, {', '.join(f(c) for c in rot)})"
                if any(rot) else f"PartPose.offset({', '.join(f(c) for c in pivot)})")
        children = TREE.get(name, []) + SHELL_TREE.get(name, [])
        decl = f"PartDefinition {var} = " if children else ""
        lines.append(f"\t\t{decl}{('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", "
                     f"{builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


def splice(src, tag, body):
    start, end = f"\t\t// BEGIN GENERATED {tag} by tools/gen_accountant.py\n", f"\t\t// END GENERATED {tag}\n"
    a, b = src.index(start) + len(start), src.index(end)
    return src[:a] + body + "\n" + src[b:]


if os.path.exists(JAVA):
    src = open(JAVA).read()
    src = splice(src, "BODY", java_layer(PARTS))
    src = splice(src, "SHELL", java_layer(SHELL))
    consts = (f"\tprivate static final float REACH_X = {f(REACH_X)};\n\tprivate static final float REACH_Y = {f(REACH_Y)};\n"
              f"\tprivate static final float REACH_STRETCH = {f(REACH_STRETCH)};")
    start, end = "\t// BEGIN GENERATED REACH by tools/gen_accountant.py\n", "\t// END GENERATED REACH\n"
    a, b = src.index(start) + len(start), src.index(end)
    src = src[:a] + consts + "\n" + src[b:]
    with open(JAVA, "w") as out:
        out.write(src)
else:
    print("note: no", JAVA, "yet: Java model not written")


# ---------------------------------------------------------------------------------------------- preview
def render(pose, view, scale=8, width=300, height=300, light=(0.35, -0.8, -0.5), focus=None, squish=0.0):
    """Orthographic render, solid layer then the translucent shell blended over it. view: 'front', 'side' (from his
    right), 'three' (three-quarters) or 'back'."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "threeback": math.pi * 0.8}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    solid = quads(pose, TREE, squish=squish)
    shell = quads(rest_pose(SHELL), SHELL_TREE, squish=squish)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs_ = [p[0] * ca - p[2] * sa for verts, _ in solid for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs_) + max(xs_)) / 2 * scale, height - 30, 24.0
    gy = int(oy + (24.0 - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)

    def project(p):
        x = p[0] * ca - p[2] * sa
        z = p[0] * sa + p[2] * ca
        return (ox + x * scale, oy + (p[1] - ly) * scale, z)

    def draw(qs, blend):
        for verts, (ua, va, ub, vb) in qs:
            A, B, C = verts[1], verts[0], verts[2]
            e1 = tuple(B[i] - A[i] for i in range(3))
            e2 = tuple(C[i] - A[i] for i in range(3))
            n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
            nl = math.sqrt(sum(c * c for c in n)) or 1.0
            if blend:
                # Back faces of the shell are culled: only the side toward the viewer tints what's behind.
                fc = tuple(sum(p[i] for p in verts) / 4 for i in range(3))
                cc = (0.0, 16.0, 0.0)
                out_ = tuple(fc[i] - cc[i] for i in range(3))
                view_dir = (sa, 0.0, ca)
                if sum(out_[i] * view_dir[i] for i in range(3)) > 0:
                    continue
            shade = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
            du, dv = ub - ua, vb - va
            nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
            for j in range(nv):
                for i in range(nu):
                    tu = min(int(ua + (i + 0.5) * du / nu), TEX_W - 1)
                    tv = min(int(va + (j + 0.5) * dv / nv), TEX_H - 1)
                    col = TEXTURE.get(int(tu), int(tv))
                    if col[3] == 0:
                        continue
                    corners = []
                    for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                        s, t = a / nu, b / nv
                        corners.append(project(tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))))
                    depth = sum(c[2] for c in corners) / 4
                    c = (int(col[0] * shade), int(col[1] * shade), int(col[2] * shade), col[3])
                    fill(img, zbuf, width, height, corners, depth, c, blend)
    draw(solid, False)
    draw(shell, True)
    return img


def fill(img, zbuf, width, height, corners, depth, colour, blend):
    xs_ = [c[0] for c in corners]
    ys_ = [c[1] for c in corners]
    for py in range(max(0, int(min(ys_))), min(height, int(math.ceil(max(ys_))) + 1)):
        for px in range(max(0, int(min(xs_))), min(width, int(math.ceil(max(xs_))) + 1)):
            if inside(corners, px + 0.5, py + 0.5) and depth < zbuf[py * width + px]:
                k = py * width + px
                if blend:
                    a = colour[3] / 255.0
                    under = img[k]
                    img[k] = tuple(int(under[i] * (1 - a) + colour[i] * a) for i in range(3)) + (255,)
                else:
                    zbuf[k] = depth
                    img[k] = colour[:3] + (255,)


def inside(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cross) < 1e-9:
            continue
        s = 1 if cross > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return sign != 0


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k, im in enumerate(images):
        bx, by = (k % cols) * w, (k // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    S = 11
    views = [render(rest_pose(), "front", S), render(rest_pose(), "side", S), render(rest_pose(), "three", S),
             render(rest_pose(), "threeback", S),
             render(animate(rest_pose(), age=10, squish=0.9), "three", S, squish=0.9),
             render(animate(rest_pose(), age=20, squish=-0.45), "three", S, squish=-0.45),
             render(animate(rest_pose(), audit=0.16), "three", S), render(animate(rest_pose(), audit=0.3), "three", S),
             render(animate(rest_pose(), audit=0.3), "front", S),
             render(animate(rest_pose(), audit=0.75), "three", S),
             render(rest_pose(), "front", 26, focus=(0.0, 11.5, -8.0)),
             render(animate(rest_pose(), audit=0.3), "three", 22, focus=(0.0, 11.0, -8.0))]
    out, bw, bh = board(views, 300, 300, 4)
    png(os.path.join(PREVIEW, "preview_accountant.png"), bw, bh, out)
    s = 5
    big = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
           for y in range(TEX_H * s) for x in range(TEX_W * s)]
    for y in range(TEX_H * s):
        for x in range(TEX_W * s):
            c = TEXTURE.get(x // s, y // s)
            if c[3]:
                b = big[y * TEX_W * s + x]
                a = c[3] / 255.0
                big[y * TEX_W * s + x] = tuple(int(b[i] * (1 - a) + c[i] * a) for i in range(3)) + (255,)
    png(os.path.join(PREVIEW, "preview_accountant_texture.png"), TEX_W * s, TEX_H * s, big)
    print("preview written to", PREVIEW)
print("done")
