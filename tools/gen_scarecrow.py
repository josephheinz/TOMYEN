"""Builds the Scarecrow: its model (the cube layout in PARTS, written into client/scarecrow/ScarecrowModel.java between
the GENERATED markers), its three entity textures (the plain one, the Jack-o'-Scarecrow and the Scrap Scarecrow) and
the glow layers for the last two, the three item sprites and their item models and definitions. On the data side it
writes the recipes (with recipe-book advancements) and the entity and item tags the scarecrow uses, and merges its
lang entries and the saucer's disappointed chirp (vanilla villager grumbles, pitched up) into en_us.json and
sounds.json.

The entity textures are painted at twice the model's pixel density (256x256 for the model's 128x128 UV space), so the
stitched face, the plaid and the straw read cleanly up close. Smooth ramps from vanilla-ish colours, regular weaves
and plaids, no noise or dithering; a rerun gives the same files.

The model is checked for z-fighting (tools/coplanar.py) at rest and through a run of its sway, battered and beamed
poses, and its extents are printed (the post stands on y 27, the ground: the renderer lifts the figure 3 pixels).

With --preview DIR it also renders, into DIR, preview_scarecrow.png: the plain scarecrow from the front, three-quarters
and the side at rest; a gusty sway frame; the Jack-o'-Scarecrow at night and the Scrap Scarecrow; a battered one up a
tractor beam; the head up close; and the three item sprites, enlarged. The render uses the same cubes, pivots and UV
layout as the Java model, a Python twin of ScarecrowModel.pose and of the renderer's sway.

Usage: python3 tools/gen_scarecrow.py src/main/resources/assets/tomyen [--preview DIR]"""
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
    """A smooth colour from a list, t in 0..1."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored). Mirrored cubes
# share their texture with the unmirrored one of the same key. Model space is the game's: y down, the front toward
# -z. The humanoid parts (head, hat, body, arms, legs) sit where vanilla's do, so armor and head items fit; their
# rotations are set by ScarecrowModel.pose. Their feet are at y 24, but the renderer lifts the figure 3 pixels, so
# the post (in the root) runs down to y 27, the ground.
#
# All cube sizes are whole pixels (the texture is laid out in whole pixels); positions can be fractional.
S = 2                    # texels per model pixel
TEX_W, TEX_H = 128, 80  # the model's UV space; the images are S times that
GROUND = 27.0

PARTS = [
    ("post", None, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [
        ("post", (-1.5, -1.0, 2.0), (3, 28, 3), False),
        ("crossbar", (-14.0, 0.0, 2.25), (28, 2, 2), False)]),
    ("head", None, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [
        ("sack", (-4.0, -8.0, -4.0), (8, 8, 8), False),
        ("tie", (-4.5, -2.0, -4.5), (9, 1, 9), False)]),
    ("hat", "head", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), []),
    ("nose", "head", (0.0, -3.5, -4.0), (0.0, 0.0, 0.0), [
        ("nose", (-1.0, -1.0, -1.0), (2, 2, 1), False)]),
    ("straw_hat", "head", (0.0, -7.5, 0.0), (-0.05, 0.0, 0.1), [
        ("brim", (-7.0, -0.5, -7.0), (14, 1, 14), False),
        ("crown", (-4.5, -5.0, -4.5), (9, 5, 9), False),
        ("crown_top", (-4.0, -5.5, -4.0), (8, 1, 8), False),
        ("band", (-5.0, -1.5, -5.0), (10, 1, 10), False)]),
    ("body", None, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [
        ("shirt", (-4.0, 0.0, -2.0), (8, 12, 4), False),
        ("ruff", (-5.0, -0.75, -4.5), (10, 2, 9), False),
        ("belt", (-5.0, 8.0, -3.0), (10, 1, 6), False),
        ("tail", (-4.5, 9.0, -2.5), (9, 4, 5), False)]),
    ("right_arm", None, (-5.0, 2.0, 0.0), (0.0, 0.0, 0.0), [
        ("sleeve_r", (-3.0, -2.0, -2.5), (4, 12, 5), False),
        ("binding", (-3.5, 7.0, -3.0), (5, 1, 8), False),
        ("straw", (-2.5, 10.0, -1.5), (3, 2, 3), False)]),
    ("right_tuft_a", "right_arm", (-1.0, 11.0, 0.0), (0.0, 0.0, 0.55), [
        ("tuft", (-0.5, 0.0, -0.5), (1, 3, 1), False)]),
    ("right_tuft_b", "right_arm", (-1.0, 11.0, 0.5), (0.5, 0.0, -0.35), [
        ("tuft", (-0.5, 0.0, -0.5), (1, 3, 1), False)]),
    ("left_arm", None, (5.0, 2.0, 0.0), (0.0, 0.0, 0.0), [
        ("sleeve_l", (-1.0, -2.0, -2.5), (4, 12, 5), False),
        ("binding", (-1.5, 7.0, -3.0), (5, 1, 8), True),
        ("straw", (-0.5, 10.0, -1.5), (3, 2, 3), True)]),
    ("left_tuft_a", "left_arm", (1.0, 11.0, 0.0), (0.0, 0.0, -0.55), [
        ("tuft", (-0.5, 0.0, -0.5), (1, 3, 1), True)]),
    ("left_tuft_b", "left_arm", (1.0, 11.0, -0.5), (-0.45, 0.0, 0.35), [
        ("tuft", (-0.5, 0.0, -0.5), (1, 3, 1), True)]),
    ("right_leg", None, (-1.9, 12.0, 0.0), (0.0, 0.0, 0.0), [
        ("trouser_r", (-2.0, 0.0, -2.0), (4, 12, 4), False),
        ("cuff", (-2.5, 9.0, -2.5), (5, 2, 5), False),
        ("straw", (-1.5, 11.5, -1.5), (3, 2, 3), False)]),
    ("right_leg_tuft", "right_leg", (0.5, 12.5, -0.5), (0.35, 0.0, -0.4), [
        ("ankle_tuft", (-0.5, 0.0, -0.5), (1, 2, 1), False)]),
    ("left_leg", None, (1.9, 12.0, 0.0), (0.0, 0.0, 0.0), [
        ("trouser_l", (-2.0, 0.0, -2.0), (4, 12, 4), False),
        ("cuff", (-2.5, 9.0, -2.5), (5, 2, 5), True),
        ("straw", (-1.5, 11.5, -1.5), (3, 2, 3), True)]),
    ("left_leg_tuft", "left_leg", (-0.5, 12.5, 0.5), (-0.3, 0.0, 0.45), [
        ("ankle_tuft", (-0.5, 0.0, -0.5), (1, 2, 1), True)]),
]
TREE = {}
for _name, _parent, _, _, _ in PARTS:
    TREE.setdefault(_parent or "root", []).append(_name)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS}


def pack():
    """Texture offsets (in model pixels) for each cube key, shelf-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS:
        for key, _, size, _ in cubes:
            assert sizes.setdefault(key, size) == size, key
            assert all(float(c).is_integer() for c in size), key
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


def faces(u, v, w, h, d):
    """A model cube's faces in the texture (model pixels), as (u, v, width, height), laid out as the game lays them
    out. 'top' and 'bottom' are as seen in the world; in both the front edge is the last row. In 'right' (the cube's
    -x side) the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, key, painters):
    """painters: face name (or 'default') -> f(x, y, fw, fh) -> colour, an RGBA tuple, or None for clear. x, y and the
    face size are in texels."""
    (u, v), (w, h, d) = CUBES[key]
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name, painters.get("default"))
        if f is None:
            continue
        W, H = fw * S, fh * S
        for y in range(H):
            for x in range(W):
                c = f(x, y, W, H)
                canvas.set(fu * S + x, fv * S + y, rgba(c) if c is not None and len(c) == 3 else (c or CLEAR))


# ---------------------------------------------------------------------------------------------- palettes
BURLAP = [(200, 174, 126), (182, 156, 110), (164, 138, 94), (140, 116, 78)]
THREAD = (66, 46, 32)
TWINE = [(204, 176, 112), (170, 142, 86), (138, 112, 64)]
STRAW = [(244, 218, 126), (226, 192, 96), (202, 166, 72), (166, 130, 54)]
HAT = [(230, 200, 122), (210, 178, 100), (188, 154, 80), (154, 124, 62)]
RIBBON = [(168, 54, 44), (128, 38, 34)]
WOOD = [(140, 110, 72), (120, 92, 58), (100, 76, 48), (80, 60, 38)]
BUTTON = (226, 216, 186)
DENIM = [(92, 116, 158), (76, 98, 138), (62, 80, 116), (48, 62, 92)]
PATCH = [(140, 102, 66), (118, 84, 54)]

RED_PLAID = {"base": (172, 48, 44), "band": (124, 32, 32), "cross": (88, 22, 24), "line": (218, 150, 112)}
GREEN_PLAID = {"base": (70, 116, 62), "band": (46, 78, 44), "cross": (30, 50, 30), "line": (214, 170, 78)}
TARP = {"base": (66, 108, 156), "band": (56, 94, 140), "cross": (48, 82, 124), "line": (86, 130, 178)}

PUMPKIN = [(236, 156, 40), (220, 134, 28), (196, 110, 22), (156, 82, 16)]
CARVED = (84, 42, 12)
GLOW = [(255, 226, 120), (255, 176, 48)]
CORD = [(132, 94, 60), (114, 80, 50), (96, 66, 40)]
NIGHT_BAND = [(54, 46, 58), (36, 30, 40)]

METAL = [(184, 190, 194), (158, 164, 170), (132, 138, 144), (104, 108, 114)]
RUST = [(178, 98, 52), (146, 76, 40), (112, 56, 30)]
LED = (150, 34, 28)
LED_HOT = (255, 82, 56)
GREEN_LED = (60, 120, 50)
GREEN_LED_HOT = (120, 255, 110)
TAPE = [(196, 198, 200), (170, 172, 176), (146, 148, 152)]
WIRES = [(198, 120, 64), (182, 48, 42), (60, 98, 176), (52, 52, 56)]
RUBBER = [(78, 78, 84), (52, 52, 58)]
CANVAS = [(122, 120, 80), (104, 102, 68), (86, 84, 56)]


# ---------------------------------------------------------------------------------------------- patterns
def burlap(x, y, w, h, cols=BURLAP, top=0.1, bottom=0.45):
    """Burlap: a gentle vertical ramp with a fine, regular weave (alternate rows and every third column a touch
    darker)."""
    c = ramp(cols, top + (bottom - top) * y / max(1, h - 1))
    k = 1.0
    if y % 2 == 1:
        k -= 0.035
    if x % 3 == 0:
        k -= 0.03
    return shade(c, k)


def plaid(p, x, y, period=12, ox=0, oy=0):
    """A tartan: two-texel bands across and down (darkest where they cross) and a fine light line between."""
    xx, yy = (x + ox) % period, (y + oy) % period
    across, down = yy in (0, 1, 2), xx in (0, 1, 2)
    if across and down:
        return p["cross"]
    if across or down:
        return p["band"]
    if xx == 7 or yy == 7:
        return p["line"]
    return p["base"]


def rope(x, y, cols=TWINE):
    """Twisted cord: diagonal strands two texels wide."""
    return cols[0] if ((x + y) // 2) % 2 == 0 else cols[1]


def strands(x, y, cols=STRAW):
    """Straw (or wire): strands running along the face, three tones repeating, lighter toward the tips."""
    return cols[[0, 1, 0, 2, 1, 3][x % 6] if len(cols) > 3 else x % len(cols)]


def ends(x, y, cols=STRAW):
    """The cut ends of a bundle of straw."""
    return cols[(x + 2 * y) % 3]


def rings(x, y, w, h, cols, width=2):
    """Concentric square rings, as a coiled straw hat or a cut log end."""
    r = max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2))
    return cols[int(r // width) % 2]


def grain(x, y, cols=WOOD, along_y=True):
    """Wood grain running along the post (or the bar): a regular run of light and dark lines."""
    i = x if along_y else y
    return cols[[1, 0, 1, 2, 1, 1, 3, 1][i % 8]]


def stitched_rect(f, x0, y0, x1, y1, fill, border, dash=True):
    """Paints a patch (inclusive rect) over f, its border stitched in dashes."""
    def g(x, y, w, h):
        if x0 <= x <= x1 and y0 <= y <= y1:
            inner = fill(x, y, w, h) if callable(fill) else fill
            if x in (x0, x1) or y in (y0, y1):
                return border if not dash or (x + y) % 2 == 0 else shade(inner, 0.92)
            return inner
        return f(x, y, w, h)
    return g


def over(f, pixels):
    """f with some texels replaced: pixels maps (x, y) -> colour."""
    return lambda x, y, w, h: pixels.get((x, y), f(x, y, w, h))


def x_stitch(cx, cy, size=4):
    """An X of thread, size texels square, top-left at (cx, cy)."""
    return {(cx + i, cy + i): THREAD for i in range(size)} | {(cx + size - 1 - i, cy + i): THREAD for i in range(size)}


# ---------------------------------------------------------------------------------------------- the plain scarecrow
def sack_face():
    """The stitched face on the front of the sack (16x16 texels): cross-stitched eyes, a wide stitched grin turned up
    at the ends, and faintly rosy cheeks."""
    px = x_stitch(3, 4) | x_stitch(9, 4)
    px |= {(x, 11): THREAD for x in range(4, 12)}
    px |= {(3, 10): THREAD, (12, 10): THREAD, (5, 10): THREAD, (10, 10): THREAD, (5, 12): THREAD, (10, 12): THREAD}
    for x, y in ((2, 9), (3, 9), (12, 9), (13, 9)):
        px[(x, y)] = lerp(BURLAP[1], (196, 92, 76), 0.35)
    return px


def straw_painters(cols=STRAW, along=True):
    side = (lambda x, y, w, h: strands(x, y, cols)) if along else (lambda x, y, w, h: strands(y, x, cols))
    return {"default": side, "top": lambda x, y, w, h: ends(x, y, cols), "bottom": lambda x, y, w, h: ends(x, y, cols)}


def paint_straw_scarecrow(tex, shirt=RED_PLAID, trousers=DENIM, band=RIBBON):
    P = lambda key, painters: paint(tex, key, painters)
    seam = lambda x, y, w, h: THREAD if x == 4 and y % 3 != 2 else shade(burlap(x, y, w, h), 0.9) if x in (3, 5) else burlap(x, y, w, h)
    P("sack", {"default": burlap, "front": over(burlap, sack_face()), "right": seam,
               "back": stitched_rect(burlap, 4, 3, 11, 10, lambda x, y, w, h: burlap(x, y, w, h, top=0.5, bottom=0.8), THREAD)})
    P("nose", {"default": lambda x, y, w, h: BURLAP[2], "bottom": lambda x, y, w, h: BURLAP[3],
               "front": lambda x, y, w, h: ramp(BURLAP, 0.1 + 0.6 * y / (h - 1))})
    P("tie", {"default": lambda x, y, w, h: rope(x, y), "top": lambda x, y, w, h: TWINE[1], "bottom": lambda x, y, w, h: TWINE[2]})

    def pleats(x, y, w, h):
        if y == h - 1:
            return strands(x, y)
        return shade(BURLAP[1] if (x // 3) % 2 == 0 else BURLAP[2], 1.0 - 0.06 * y / max(1, h - 1))
    P("ruff", {"default": pleats, "top": lambda x, y, w, h: BURLAP[1] if ((x + y) // 3) % 2 == 0 else BURLAP[2],
               "bottom": lambda x, y, w, h: ends(x, y)})

    P("brim", {"top": lambda x, y, w, h: HAT[2] if min(x, y, w - 1 - x, h - 1 - y) < 1 else rings(x, y, w, h, HAT[:2]),
               "bottom": lambda x, y, w, h: rings(x, y, w, h, HAT[2:]),
               "default": lambda x, y, w, h: HAT[2] if y == 0 else (HAT[3] if x % 3 == 0 else HAT[2])})
    P("crown", {"default": lambda x, y, w, h: shade(HAT[(y // 2) % 2], 1.0 - 0.05 * y / h), "top": lambda x, y, w, h: rings(x, y, w, h, HAT[:2]),
                "bottom": lambda x, y, w, h: HAT[3]})
    P("crown_top", {"default": lambda x, y, w, h: HAT[1], "top": lambda x, y, w, h: rings(x, y, w, h, HAT[:2])})
    P("band", {"default": lambda x, y, w, h: band[0] if y == 0 else band[1], "top": lambda x, y, w, h: band[1],
               "bottom": lambda x, y, w, h: band[1]})

    shirt_f = lambda x, y, w, h: plaid(shirt, x, y)

    def shirt_front(x, y, w, h):
        if x in (7, 8):
            if y in (3, 4, 10, 11, 17, 18):
                return BUTTON
            return shade(plaid(shirt, x, y), 0.85)
        return plaid(shirt, x, y)
    pocket = stitched_rect(shirt_front, 10, 4, 14, 9, lambda x, y, w, h: ramp(trousers, 0.2 + 0.3 * (y - 4) / 5), (226, 214, 184))
    P("shirt", {"default": shirt_f, "front": pocket,
                "back": stitched_rect(shirt_f, 4, 9, 11, 16, lambda x, y, w, h: PATCH[(x + y) // 3 % 2], THREAD),
                "bottom": lambda x, y, w, h: shade(shirt["cross"], 0.8)})
    hem = lambda x, y, w, h: shade(plaid(shirt, x, y, oy=4), 0.82) if y == h - 1 and x % 3 != 1 else plaid(shirt, x, y, oy=4)
    P("tail", {"default": hem, "top": shirt_f, "bottom": lambda x, y, w, h: shade(shirt["cross"], 0.75)})
    P("belt", {"default": lambda x, y, w, h: rope(x, y),
               "front": lambda x, y, w, h: (TWINE[0] if y == 0 else TWINE[2]) if x in (8, 9, 10, 11) else rope(x, y)})

    sleeve = lambda x, y, w, h: plaid(shirt, x, y, ox=3)
    cuff_end = lambda x, y, w, h: shade(shirt["cross"], 0.8)
    P("sleeve_r", {"default": sleeve, "bottom": cuff_end,
                   "front": stitched_rect(sleeve, 1, 9, 6, 14, lambda x, y, w, h: ramp(trousers, 0.25), (226, 214, 184))})
    P("sleeve_l", {"default": sleeve, "bottom": cuff_end,
                   "front": stitched_rect(sleeve, 2, 12, 6, 17, lambda x, y, w, h: burlap(x, y, w, h, top=0.3, bottom=0.6), THREAD)})
    P("binding", {"default": lambda x, y, w, h: rope(x, y), "top": lambda x, y, w, h: TWINE[1], "bottom": lambda x, y, w, h: TWINE[1]})
    P("straw", straw_painters())
    tuft = {"default": lambda x, y, w, h: STRAW[0] if y >= h - 2 else strands(x, y),
            "top": lambda x, y, w, h: STRAW[1], "bottom": lambda x, y, w, h: STRAW[0]}
    P("tuft", tuft)
    P("ankle_tuft", tuft)

    def leg(x, y, w, h):
        return ramp(trousers, 0.1 + 0.45 * y / max(1, h - 1))

    def leg_side(x, y, w, h):
        if x in (3, 4) and y % 3 != 2:
            return shade(ramp(trousers, 0.1 + 0.45 * y / max(1, h - 1)), 1.18)
        return leg(x, y, w, h)
    knee = stitched_rect(leg, 1, 9, 6, 14, lambda x, y, w, h: PATCH[(x // 2 + y // 2) % 2], THREAD)

    def hole(x, y, w, h):
        if 3 <= x <= 6 and 15 <= y <= 18:
            return ramp(trousers, 0.95) if x in (3, 6) or y in (15, 18) else strands(x, y)
        return leg(x, y, w, h)
    pocket_back = stitched_rect(leg, 1, 2, 6, 7, lambda x, y, w, h: ramp(trousers, 0.35), shade(trousers[0], 1.15))
    P("trouser_r", {"default": leg_side, "front": hole, "back": pocket_back, "top": lambda x, y, w, h: trousers[3],
                    "bottom": lambda x, y, w, h: ends(x, y)})
    P("trouser_l", {"default": leg_side, "front": knee, "back": leg, "top": lambda x, y, w, h: trousers[3],
                    "bottom": lambda x, y, w, h: ends(x, y)})
    P("cuff", {"default": lambda x, y, w, h: trousers[0] if y == 0 else trousers[1] if y < h - 1 else trousers[2],
               "top": lambda x, y, w, h: trousers[2], "bottom": lambda x, y, w, h: trousers[3]})

    def knot(x, y, w, h):
        k = grain(x, y)
        return shade(k, 0.8) if (x - 3) ** 2 / 2.0 + (y - 20) ** 2 / 6.0 < 1.5 else k
    P("post", {"default": lambda x, y, w, h: grain(x, y), "front": knot, "top": lambda x, y, w, h: rings(x, y, w, h, WOOD[:3:2], 1),
               "bottom": lambda x, y, w, h: WOOD[3]})
    P("crossbar", {"default": lambda x, y, w, h: grain(x, y, along_y=False), "right": lambda x, y, w, h: rings(x, y, w, h, WOOD[:3:2], 1),
                   "left": lambda x, y, w, h: rings(x, y, w, h, WOOD[:3:2], 1)})


# ---------------------------------------------------------------------------------------------- the Jack-o'-Scarecrow
def pumpkin_face():
    """The carved face (16x16 texels): two triangle eyes, a triangle nose and a jagged grin. Returns the carved
    texels, each with how deep into the cut it is (0 at the rim, 1 in the middle), for the glow."""
    cut = set()
    eye = [(1, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2), (3, 2), (0, 3), (1, 3), (2, 3), (3, 3)]
    for ex, ey in eye:
        cut.add((3 + ex, 3 + ey))
        cut.add((12 - ex, 3 + ey))
    cut |= {(7, 8), (8, 8), (7, 9), (8, 9)}
    grin_top = [11, 10, 11, 10, 11, 11, 10, 11, 10, 11]
    for i, x in enumerate(range(3, 13)):
        bottom = 13 if 4 <= x <= 11 else 12
        for y in range(grin_top[i], bottom + 1):
            cut.add((x, y))
    for x in (5, 10):           # teeth
        cut.discard((x, 11))
        cut.discard((x, 10))
    return cut


def paint_pumpkin_scarecrow(tex, glow):
    paint_straw_scarecrow(tex, shirt=GREEN_PLAID, trousers=CORD + [CORD[2]], band=NIGHT_BAND)

    def ribs(x, y, w, h):
        g = x % 8
        t = min(g, 8 - g) / 4.0
        return ramp(PUMPKIN, 0.75 - 0.6 * t + 0.15 * y / max(1, h - 1))
    cut = pumpkin_face()
    front = lambda x, y, w, h: CARVED if (x, y) in cut else ribs(x, y, w, h)
    paint(tex, "sack", {"default": ribs, "front": front, "top": lambda x, y, w, h: rings(x, y, w, h, PUMPKIN[1:3], 2),
                        "bottom": lambda x, y, w, h: PUMPKIN[3]})
    paint(glow, "sack", {"front": lambda x, y, w, h: (GLOW[0] if all((x + dx, y + dy) in cut for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                                                      else GLOW[1]) if (x, y) in cut else None})

    def cord(x, y, w, h):
        return shade(ramp(CORD, 0.1 + 0.5 * y / max(1, h - 1)), 0.9 if x % 2 == 0 else 1.0)
    for key, patch in (("trouser_r", False), ("trouser_l", True)):
        f = stitched_rect(cord, 1, 9, 6, 14, lambda x, y, w, h: PUMPKIN[2], THREAD) if patch else cord
        paint(tex, key, {"default": cord, "front": f, "top": lambda x, y, w, h: CORD[2], "bottom": lambda x, y, w, h: ends(x, y)})
    paint(tex, "cuff", {"default": lambda x, y, w, h: CORD[0] if y == 0 else CORD[1] if y < h - 1 else CORD[2],
                        "top": lambda x, y, w, h: CORD[2], "bottom": lambda x, y, w, h: CORD[2]})


# ---------------------------------------------------------------------------------------------- the Scrap Scarecrow
def metal(x, y, w, h, cols=METAL):
    return ramp(cols, 0.15 + 0.4 * y / max(1, h - 1))


def bucket(x, y, w, h):
    """Galvanised sheet with two pressed ridges and a row of rivets on each, rust running down from a few of them."""
    c = metal(x, y, w, h)
    for ridge in (2, h - 4):
        if y == ridge:
            return shade(c, 1.12)
        if y == ridge + 1:
            return shade(c, 0.82)
    if y in (1, h - 3) and x % 4 == 1:
        return METAL[0]
    if x in (2, w - 3) and 4 <= y <= 9:
        return lerp(c, RUST[1], 0.5 - 0.06 * (y - 4))
    return c


def paint_scrap_scarecrow(tex, glow):
    paint_straw_scarecrow(tex, shirt=TARP, trousers=CANVAS + [CANVAS[2]], band=TAPE)

    def face(x, y, w, h):
        for cx in (5, 10):
            d = max(abs(x - cx), abs(y - 7))
            if d == 0:
                return LED
            if d == 1:
                return LED if (x - cx) * (y - 7) == 0 else (60, 22, 20)
            if d == 2 and abs(x - cx) + abs(y - 7) <= 3:
                return METAL[3]
        if 4 <= x <= 11 and 10 <= y <= 12:
            return (40, 42, 46) if y % 2 == 0 and 5 <= x <= 10 else METAL[2]
        return bucket(x, y, w, h)

    def dent(x, y, w, h):
        c = bucket(x, y, w, h)
        r = ((x - 9) ** 2 + (y - 8) ** 2) / 9.0
        return shade(c, 0.88 + 0.1 * (x - 9) / 3) if r < 1 else c
    paint(tex, "sack", {"default": bucket, "front": face, "right": dent, "top": lambda x, y, w, h: rings(x, y, w, h, METAL[1:3], 2),
                        "bottom": lambda x, y, w, h: METAL[3]})
    paint(glow, "sack", {"front": lambda x, y, w, h: (LED_HOT if (x, y) in ((5, 7), (10, 7)) else shade(LED_HOT, 0.7)
                                                      if any(abs(x - cx) + abs(y - 7) == 1 for cx in (5, 10)) else None)})
    paint(tex, "nose", {"default": lambda x, y, w, h: METAL[2],
                        "front": lambda x, y, w, h: (40, 42, 46) if 1 <= x <= 2 and 1 <= y <= 2 else METAL[1] if (x + y) % 3 else METAL[0]})
    copper = [WIRES[0], shade(WIRES[0], 0.75)]
    paint(tex, "tie", {"default": lambda x, y, w, h: rope(x, y, copper), "top": lambda x, y, w, h: copper[1], "bottom": lambda x, y, w, h: copper[1]})
    paint(tex, "ruff", {"default": lambda x, y, w, h: RUBBER[(y // 1) % 2] if y < h - 1 else strands(x, y, WIRES),
                        "top": lambda x, y, w, h: RUBBER[((x + y) // 2) % 2], "bottom": lambda x, y, w, h: ends(x, y, WIRES)})

    def sawblade_edge(x, y, w, h):
        if y == 0:
            return METAL[0] if x % 4 < 2 else METAL[2]
        return METAL[3]

    def blade(x, y, w, h):
        r = max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2)) / ((w - 1) / 2)
        if r > 0.93:
            return METAL[3]
        return METAL[2] if 0.55 < r < 0.62 else ramp(METAL[:3], 0.15 + 0.6 * r)
    paint(tex, "brim", {"top": blade, "bottom": lambda x, y, w, h: shade(blade(x, y, w, h), 0.85), "default": sawblade_edge})
    pot = lambda x, y, w, h: METAL[3] if y == h - 3 else lerp(RUST[0], RUST[2], y / max(1, h - 1))
    paint(tex, "crown", {"default": lambda x, y, w, h: METAL[1] if y < 2 else pot(x, y, w, h), "top": lambda x, y, w, h: rings(x, y, w, h, RUST[:2], 2),
                         "bottom": lambda x, y, w, h: RUST[2]})
    paint(tex, "crown_top", {"default": lambda x, y, w, h: METAL[2],
                             "top": lambda x, y, w, h: (40, 42, 46) if 6 <= x <= 9 and 6 <= y <= 9 else rings(x, y, w, h, METAL[1:3], 2)})
    paint(tex, "band", {"default": lambda x, y, w, h: METAL[0] if x % 6 == 2 and y == 0 else METAL[2] if y == 0 else METAL[3]})

    tarp = lambda x, y, w, h: plaid(TARP, x, y)

    def grommet(f, spots):
        def g(x, y, w, h):
            for gx, gy in spots:
                d = max(abs(x - gx), abs(y - gy))
                if d == 0:
                    return (36, 52, 74)
                if d == 1:
                    return METAL[1]
            return f(x, y, w, h)
        return g

    def plate(f, x0, y0, x1, y1):
        def g(x, y, w, h):
            if x0 <= x <= x1 and y0 <= y <= y1:
                if (x, y) in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
                    return METAL[0]
                return METAL[3] if x in (x0, x1) or y in (y0, y1) else metal(x - x0, y - y0, x1 - x0 + 1, y1 - y0 + 1)
            return f(x, y, w, h)
        return g
    front = grommet(plate(tarp, 9, 3, 14, 9), [(2, 2), (13, 21)])
    front = over(front, {(4, 6): GREEN_LED, (5, 6): GREEN_LED})
    paint(tex, "shirt", {"default": tarp, "front": front, "back": plate(tarp, 3, 8, 12, 17), "bottom": lambda x, y, w, h: TARP["cross"]})
    paint(glow, "shirt", {"front": lambda x, y, w, h: GREEN_LED_HOT if (x, y) in ((4, 6), (5, 6)) else None})
    paint(tex, "tail", {"default": grommet(tarp, [(2, 4), (15, 4)]), "top": tarp, "bottom": lambda x, y, w, h: TARP["cross"]})
    tape = lambda x, y, w, h: TAPE[0] if y == 0 else TAPE[1] if (x // 3) % 2 else TAPE[2]
    paint(tex, "belt", {"default": tape})
    paint(tex, "binding", {"default": tape})
    paint(tex, "sleeve_r", {"default": tarp, "front": plate(tarp, 1, 8, 6, 15), "bottom": lambda x, y, w, h: TARP["cross"]})
    paint(tex, "sleeve_l", {"default": grommet(tarp, [(4, 6), (4, 18)]), "bottom": lambda x, y, w, h: TARP["cross"]})
    paint(tex, "straw", straw_painters(WIRES))
    paint(tex, "tuft", {"default": lambda x, y, w, h: METAL[0] if y >= h - 1 else WIRES[(x // 2) % 4]})
    paint(tex, "ankle_tuft", {"default": lambda x, y, w, h: METAL[0] if y >= h - 1 else WIRES[(x // 2 + 1) % 4]})

    canvas = lambda x, y, w, h: ramp(CANVAS, 0.1 + 0.45 * y / max(1, h - 1))
    paint(tex, "trouser_r", {"default": canvas, "front": plate(canvas, 1, 9, 6, 15), "top": lambda x, y, w, h: CANVAS[2],
                             "bottom": lambda x, y, w, h: ends(x, y, WIRES)})
    paint(tex, "trouser_l", {"default": canvas, "front": grommet(canvas, [(4, 12)]), "top": lambda x, y, w, h: CANVAS[2],
                             "bottom": lambda x, y, w, h: ends(x, y, WIRES)})
    paint(tex, "cuff", {"default": tape})

    def pipe(x, y, w, h):
        c = metal(x, y, w, h, METAL[1:])
        return lerp(c, RUST[1], 0.55) if x % 5 == 3 or (x % 5 == 4 and y % 7 < 3) else c
    paint(tex, "post", {"default": pipe, "top": lambda x, y, w, h: (40, 42, 46) if 1 <= x <= 4 and 1 <= y <= 4 else METAL[2],
                        "bottom": lambda x, y, w, h: RUST[2]})
    rebar = lambda x, y, w, h: RUST[0] if (x + y) % 4 == 0 else RUST[1] if y < h - 1 else RUST[2]
    paint(tex, "crossbar", {"default": rebar})


TEXTURES = {}
GLOWS = {}
for variant in ("scarecrow", "pumpkin_scarecrow", "scrap_scarecrow"):
    tex, glow = Canvas(TEX_W * S, TEX_H * S), Canvas(TEX_W * S, TEX_H * S)
    if variant == "scarecrow":
        paint_straw_scarecrow(tex)
    elif variant == "pumpkin_scarecrow":
        paint_pumpkin_scarecrow(tex, glow)
    else:
        paint_scrap_scarecrow(tex, glow)
    tex.save(f"entity/scarecrow/{variant}.png")
    TEXTURES[variant] = tex
    if variant != "scarecrow":
        glow.save(f"entity/scarecrow/{variant}_glow.png")
        GLOWS[variant] = glow


# ---------------------------------------------------------------------------------------------- item sprites
def sprite(rows, palette):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    c = Canvas(16, 16)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# The scarecrow on its post, arms out: a straw hat, the sack head's stitched face, a plaid shirt with straw bursting
# from the cuffs, patched trousers dangling either side of the post.
SCARECROW_SPRITE = ["................",
                    "......hhhh......",
                    ".....hHHHHh.....",
                    ".....rrrrrr.....",
                    "...bBBBBBBBBb...",
                    ".....sSSSSs.....",
                    ".....sxSSxs.....",
                    ".....sSSSSs.....",
                    ".....smmmms.....",
                    ".y..ttttttt..y..",
                    "yYpPpPpPPpPpPpYy",
                    ".yqpqpqPPqpqpqy.",
                    "......pPPp......",
                    "......dwwd......",
                    "......dwwd......",
                    "......ywwy......"]
SPRITE_BASE = {"h": HAT[2], "H": HAT[0], "r": RIBBON[0], "b": HAT[3], "B": HAT[1], "s": BURLAP[2], "S": BURLAP[0],
               "x": THREAD, "m": THREAD, "t": TWINE[1], "y": STRAW[2], "Y": STRAW[0], "w": WOOD[1]}
ITEM_SPRITES = {}
ITEM_SPRITES["scarecrow"] = sprite(SCARECROW_SPRITE, SPRITE_BASE | {"p": RED_PLAID["band"], "P": RED_PLAID["base"],
                                                                     "q": RED_PLAID["cross"], "d": DENIM[1]})
PUMPKIN_SPRITE = list(SCARECROW_SPRITE)
PUMPKIN_SPRITE[5:9] = [".....oOOOOo.....",
                       ".....oxOOxo.....",
                       ".....oOOOOo.....",
                       ".....oxxxxo....."]
ITEM_SPRITES["pumpkin_scarecrow"] = sprite(PUMPKIN_SPRITE, SPRITE_BASE | {"o": PUMPKIN[2], "O": PUMPKIN[0], "x": GLOW[1], "r": NIGHT_BAND[0],
                                      "p": GREEN_PLAID["band"], "P": GREEN_PLAID["base"], "q": GREEN_PLAID["cross"],
                                      "d": CORD[1]})
SCRAP_SPRITE = list(SCARECROW_SPRITE)
SCRAP_SPRITE[1:9] = ["......kkkk......",
                     ".....kKKKKk.....",
                     ".....gggggg.....",
                     "...gGGGGGGGGg...",
                     ".....gGGGGg.....",
                     ".....gxGGxg.....",
                     ".....gGGGGg.....",
                     ".....gzzzzg....."]
ITEM_SPRITES["scrap_scarecrow"] = sprite(SCRAP_SPRITE, SPRITE_BASE | {"k": RUST[1], "K": RUST[0], "g": METAL[3], "G": METAL[1], "x": LED_HOT, "z": (40, 42, 46),
                                    "t": RUBBER[0], "y": WIRES[0], "Y": WIRES[1], "p": TARP["band"], "P": TARP["base"],
                                    "q": TARP["cross"], "d": CANVAS[1], "w": METAL[2]})
for _name, _sprite in ITEM_SPRITES.items():
    _sprite.save(f"item/{_name}.png")


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


for name in ("scarecrow", "pumpkin_scarecrow", "scrap_scarecrow"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})


def advancement(name, ingredient, category):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


CATEGORY = "decorations"
# The scarecrow: a pumpkin (carved or not) for the head, a hay bale stuffing the body, leather or wool for the clothes,
# and sticks for the post and crossbar. Unlocked by picking up hay.
write(os.path.join(DATA, "recipe/scarecrow.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "misc"),
    ("key", OrderedDict([("P", "#tomyen:scarecrow_heads"), ("S", "minecraft:stick"), ("H", "minecraft:hay_block"),
                         ("C", "#tomyen:scarecrow_cloth")])),
    ("pattern", ["SPS", "CHC", " S "]),
    ("result", OrderedDict([("count", 1), ("id", "tomyen:scarecrow")]))]))
advancement("scarecrow", "minecraft:hay_block", CATEGORY)
# The variants are made from a scarecrow: a jack-o'-lantern for a head, or rebuilt with tech scrap and redstone.
for name, ingredients in (("pumpkin_scarecrow", ["tomyen:scarecrow", "minecraft:jack_o_lantern"]),
                          ("scrap_scarecrow", ["tomyen:scarecrow", "tomyen:tech_scrap", "tomyen:tech_scrap", "minecraft:redstone"])):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", "misc"), ("ingredients", ingredients),
        ("result", OrderedDict([("count", 1), ("id", "tomyen:" + name)]))]))
    advancement(name, "tomyen:scarecrow", CATEGORY)

write(os.path.join(DATA, "tags/item/scarecrow_heads.json"), {"values": ["minecraft:pumpkin", "minecraft:carved_pumpkin"]})
write(os.path.join(DATA, "tags/item/scarecrow_cloth.json"), {"values": ["minecraft:leather", "#minecraft:wool"]})
write(os.path.join(DATA, "tags/entity_type/scarecrow_haters.json"), {"values": ["#minecraft:undead", "tomyen:werewolf"]})
write(os.path.join(DATA, "tags/entity_type/ignores_scarecrows.json"), {"values": [
    "minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian", "minecraft:warden", "tomyen:ufo"]})


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


merge(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    ("ufo_disappointed", OrderedDict([("subtitle", "subtitles.tomyen.ufo_disappointed"),
                                      ("sounds", vanilla(["mob/villager/no1", "mob/villager/no2", "mob/villager/no3"], 1.7,
                                                         attenuation_distance=48))]))]))

merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.tomyen.scarecrow", "Scarecrow"),
    ("item.tomyen.scarecrow", "Scarecrow"),
    ("item.tomyen.pumpkin_scarecrow", "Jack-o'-Scarecrow"),
    ("item.tomyen.scrap_scarecrow", "Scrap Scarecrow"),
    ("item.tomyen.scarecrow.tooltip.ufo", "UFOs go for it before your livestock"),
    ("item.tomyen.scarecrow.tooltip.mobs", "Hostile mobs nearby attack it for a few seconds"),
    ("item.tomyen.scrap_scarecrow.tooltip.mobs", "Hostile mobs and rogue machines nearby attack it for a few seconds"),
    ("item.tomyen.scarecrow.tooltip.crops", "Mobs don't trample farmland and rabbits leave crops alone nearby"),
    ("item.tomyen.scarecrow.tooltip.repair", "Mend with hay bales or wheat; dress it in armor"),
    ("entity.tomyen.scarecrow.rejected", "The UFO throws the scarecrow back in disgust"),
    ("entity.tomyen.scarecrow.probed.0", "Rejected Specimen #%s"),
    ("entity.tomyen.scarecrow.probed.1", "Sample %s: Just Hay"),
    ("entity.tomyen.scarecrow.probed.2", "Probed (Inconclusive) #%s"),
    ("entity.tomyen.scarecrow.probed.3", "Return to Sender #%s"),
    ("subtitles.tomyen.ufo_disappointed", "UFO grumbles in disappointment"),
]))


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "scarecrow", "ScarecrowModel.java"))


def f(v):
    return f"{v:.2f}f".replace("-0.00f", "0.00f")


def java_layer():
    """createBodyLayer's body, from PARTS."""
    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in PARTS:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size, mirror in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}){'.mirror()' if mirror else ''}.addBox({', '.join(f(c) for c in frm)}, " \
                       f"{', '.join(f(c) for c in size)}){'.mirror(false)' if mirror else ''}"
        pose = (f"PartPose.offsetAndRotation({', '.join(f(c) for c in pivot)}, {', '.join(f(c) for c in rot)})"
                if any(rot) else f"PartPose.offset({', '.join(f(c) for c in pivot)})")
        used = TREE.get(name) is not None
        lines.append(f"\t\t{'PartDefinition ' + var + ' = ' if used else ''}{('p_' + parent) if parent else 'root'}"
                     f".addOrReplaceChild(\"{name}\", {builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_scarecrow.py\n", "\t\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose():
    return {name: [pivot, rot] for name, _, pivot, rot, _ in PARTS}


def pose(age=0.0, wind=1.0, tatter=0.0, lifted=False):
    """Python twin of ScarecrowModel.pose."""
    p = rest_pose()
    t = age
    flail = 1.0 if lifted else 0.0
    sin = math.sin
    p["head"][1] = (0.08 + 0.05 * sin(t * 0.051) * wind + 0.25 * tatter, 0.06 * sin(t * 0.023),
                    0.14 + 0.06 * sin(t * 0.043 + 0.7) * wind + 0.2 * tatter)
    reach = math.pi / 2 - 0.06 - 0.22 * tatter
    p["right_arm"][1] = (0.04 * sin(t * 0.09) * wind, 0.0, reach + 0.03 * sin(t * 0.13) * wind + 0.35 * flail * sin(t * 0.55))
    p["left_arm"][1] = (0.04 * sin(t * 0.09 + 1.9) * wind, 0.0,
                        -reach - 0.03 * sin(t * 0.13 + 2.4) * wind - 0.35 * flail * sin(t * 0.55 + 1.0))
    p["right_leg"][1] = (-0.10 + 0.07 * sin(t * 0.071) * wind + 0.5 * flail * sin(t * 0.6), 0.0, 0.07)
    p["left_leg"][1] = (0.06 + 0.07 * sin(t * 0.071 + 2.1) * wind - 0.5 * flail * sin(t * 0.6), 0.0, -0.07)
    return p


def sway(age=0.0, wind=1.0, wiggle=100.0):
    """Python twin of ScarecrowRenderer.setupRotations' lean and roll, in radians."""
    lean = (math.sin(age * 0.045) * 1.4 + math.sin(age * 0.13) * 0.5) * wind
    roll = (math.sin(age * 0.037 + 1.3) * 1.8 + math.sin(age * 0.11) * 0.4) * wind
    if wiggle < 5.0:
        roll += math.sin(wiggle / 1.5 * math.pi) * 4.0
    return math.radians(lean), math.radians(roll)


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


def quads(p, labels=False, hidden=()):
    """Every textured face of the posed model: (corners in model space, uv rect in model pixels[, label, outward
    normal])."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            if child in hidden:
                continue
            pivot, rot = p[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for k, (cube, frm, size, mirror) in enumerate(PART_CUBES.get(child, [])):
                (u, v), _ = CUBES[cube]
                for verts, uv in cube_faces(m, o, u, v, frm, size, mirror):
                    if labels:
                        centre = tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3))
                        fc = tuple(sum(q[i] for q in verts) / 4 for i in range(3))
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


# Z-fighting: no two faces may share a plane, face the same way and overlap, at rest or anywhere through its sway,
# battered or up a beam.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from coplanar import Face, fights
CHECKS = [("rest", pose())] + [(f"sway t={t}", pose(age=t, wind=2.2)) for t in range(0, 400, 23)] \
         + [("battered", pose(age=40, tatter=1.0)), ("beamed", pose(age=13, lifted=True)), ("beamed2", pose(age=31, lifted=True))]
_bad = 0
for _label, _p in CHECKS:
    _hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(_p, labels=True)])
    for h_ in _hits:
        print(f"z-fight ({_label}):", h_)
    _bad += len(_hits)
if _bad:
    raise SystemExit(f"scarecrow: {_bad} coplanar overlapping face pairs")


def extents(p):
    pts = [q for verts, _ in quads(p) for q in verts]
    return [(min(q[i] for q in pts), max(q[i] for q in pts)) for i in range(3)]


ext = extents(pose())
print(f"scarecrow: {sum(len(c) for _, _, _, _, c in PARTS)} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, "
      f"top y {ext[1][0]:.1f} ({(GROUND - ext[1][0]) / 16:.2f} blocks tall), bottom y {ext[1][1]:.2f} (ground {GROUND:.0f}), "
      f"z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


# ---------------------------------------------------------------------------------------------- preview
def render(p, view, tex, glow=None, scale=7, width=320, height=400, light=(0.35, -0.8, -0.5), focus=None, lean=0.0, roll=0.0,
           night=False, hidden=(), background=(46, 52, 64)):
    """Orthographic render. view: 'front', 'side' (from its right), 'three' (three-quarters) or 'back'. lean and roll
    (radians) tip the whole figure about the foot of its post, as the renderer does. focus: a model point to centre
    (default: the ground at the bottom)."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    tip = rot_matrix(lean, 0.0, roll)
    base = (0.0, GROUND, 0.0)
    qs = []
    for verts, uv in quads(p, hidden=hidden):
        qs.append(([tuple(base[i] + apply(tip, tuple(v[j] - base[j] for j in range(3)))[i] for i in range(3)) for v in verts], uv))
    zbuf = [1e9] * (width * height)
    img = [rgba(background)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        ox, oy, ly = width * 0.5, height - 16, GROUND
    gy = int(oy + (GROUND - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    dim = 0.45 if night else 1.0
    for verts, (ua, va, ub, vb) in qs:
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = (0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)) * dim
        du, dv = (ub - ua) * S, (vb - va) * S
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua * S + (i + 0.5) * du / nu), TEX_W * S - 1)
                tv = min(int(va * S + (j + 0.5) * dv / nv), TEX_H * S - 1)
                col = tex.get(tu, tv)
                if col[3] == 0:
                    continue
                g = glow.get(tu, tv) if glow else CLEAR
                if g[3]:
                    c = rgba(g[:3]) if night else rgba(lerp(shade(col, lit), g[:3], 0.35))
                else:
                    c = rgba(shade(col, lit))
                corners = []
                for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a / nu, b / nv
                    q = tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))
                    x = q[0] * ca - q[2] * sa
                    z = q[0] * sa + q[2] * ca
                    corners.append((ox + x * scale, oy + (q[1] - ly) * scale, z))
                fill(img, zbuf, width, height, corners, sum(c_[2] for c_ in corners) / 4, c)
    return img


def fill(img, zbuf, width, height, corners, depth, colour):
    xs_ = [c[0] for c in corners]
    ys_ = [c[1] for c in corners]
    for py in range(max(0, int(min(ys_))), min(height, int(math.ceil(max(ys_))) + 1)):
        for px in range(max(0, int(min(xs_))), min(width, int(math.ceil(max(xs_))) + 1)):
            if inside(corners, px + 0.5, py + 0.5) and depth < zbuf[py * width + px]:
                zbuf[py * width + px] = depth
                img[py * width + px] = colour


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


def sprites_panel(width, height):
    img = [(46, 52, 64, 255)] * (width * height)
    k = 6
    for n, name in enumerate(("scarecrow", "pumpkin_scarecrow", "scrap_scarecrow")):
        canvas = ITEM_SPRITES[name]
        bx, by = (width - 16 * k) // 2, 16 + n * (16 * k + 22)
        for y in range(16 * k):
            for x in range(16 * k):
                if 0 <= by + y < height:
                    pxl = canvas.get(x // k, y // k)
                    checker = (56, 62, 74, 255) if ((x // 12) + (y // 12)) % 2 else (50, 56, 68, 255)
                    img[(by + y) * width + bx + x] = pxl if pxl[3] else checker
    return img


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
    plain = TEXTURES["scarecrow"]
    gust_age = 130.0
    lean, roll = sway(gust_age, wind=2.2)
    head_centre = (0.0, -4.0, 0.0)
    views = [render(pose(), "front", plain), render(pose(), "three", plain), render(pose(), "side", plain),
             render(pose(age=gust_age, wind=2.2), "three", plain, lean=lean, roll=roll),
             render(pose(age=60), "three", TEXTURES["pumpkin_scarecrow"], GLOWS["pumpkin_scarecrow"], night=True,
                    background=(20, 22, 34)),
             render(pose(age=90), "three", TEXTURES["scrap_scarecrow"], GLOWS["scrap_scarecrow"]),
             render(pose(age=20, tatter=1.0, lifted=True), "front", plain, background=(36, 60, 50)),
             render(pose(), "front", plain, scale=20, focus=head_centre),
             sprites_panel(320, 400)]
    out, bw, bh = board(views, 320, 400, 3)
    png(os.path.join(PREVIEW, "preview_scarecrow.png"), bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
