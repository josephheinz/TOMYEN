"""Builds the Big Banana: its model (the cube layout in PARTS, written into client/banana/BigBananaModel.java between the
GENERATED markers) and entity texture, its spawn egg, the Banana and the Banana Peel (item sprites, and the flat peel
block's texture, model and blockstate), and their item models and definitions. On the data side it writes the Big
Banana's loot table, the peel block's loot table, the earthworms-from-a-peel recipe (with its recipe-book
advancement), the mymod:makes_big_banana_happy item tag and adds the banana to c:foods/fruit. Its sounds (vanilla
slime, honey block and note block sounds, pitched) and lang entries are merged into sounds.json and en_us.json.

The texture is painted in smooth ramps: a banana-yellow ramp down each face (brightest along the middle of the face,
darker toward its edges, and a darker ridge line along every edge, so the square segments read as a banana's facets),
paler on the inner curve and deeper on the outer; the yellow turns greenish toward the top and into a green stem with a
dark cut end, and brown at the bottom tip. A few brown sugar spots sit at set places on the back and sides. Cartoon
eyes (white, big black pupils with a glint), a painted open smile with a tongue and rosy cheeks. Dark brown stick
limbs, white four-fingered gloves, red sneakers with white soles and laces. No noise, no dithering; a rerun gives the
same files.

The model is checked for z-fighting at rest (tools/coplanar.py), and its extents are printed (it should stand on y 24,
about 48 pixels tall, and stay within its 0.95-block hitbox, +-7.6).

With --preview DIR it also renders, into DIR, preview_banana.png: front, side and three-quarters at rest; two walk
frames; three slap frames; the happy bounce; and the banana, peel, peel block and spawn egg sprites, using the same
cubes, pivots and UV layout as the Java model, and a Python twin of its animation.

Usage: python3 tools/gen_banana.py src/main/resources/assets/mymod [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
MYDATA = os.path.join(DATA, "mymod")
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


# ---------------------------------------------------------------------------------------------- palette
# Banana yellow, light to dark.
YELLOW = [(255, 240, 150), (252, 226, 98), (242, 204, 62), (222, 178, 44), (186, 142, 34)]
PALE = (255, 246, 196)          # the inner curve's paler skin
RIDGE = (200, 152, 40)          # the darker line along each facet edge
GREEN = [(196, 210, 86), (150, 182, 60), (108, 148, 46), (76, 112, 34)]
BROWN = [(128, 92, 48), (96, 66, 36), (66, 44, 26)]
SPOT = (132, 94, 42)            # sugar spots
SPOT_HALO = (210, 164, 58)
WHITE = (250, 250, 244)
WHITE_SHADE = (218, 220, 224)
PUPIL = (28, 24, 30)
MOUTH = (84, 34, 28)
TONGUE = (226, 96, 92)
CHEEK = (250, 176, 112)
LIMB = [(110, 80, 48), (88, 62, 36), (68, 48, 28)]
GLOVE = [(250, 250, 246), (226, 226, 222), (198, 198, 196)]
SHOE = [(214, 60, 52), (182, 44, 40), (140, 32, 32)]
SOLE = (238, 234, 222)

TEX_W, TEX_H = 128, 128


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1 (no banding beyond the texel grid, no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out. In 'front'
    (-z) the first column is the creature's right (-x) and the first row the top; 'top' and 'bottom' are as seen in
    the world. In 'right' (the cube's -x side) the front edge is the last column, in 'left' the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, uv, size, painters):
    """painters: face name -> f(x, y, fw, fh) -> colour (or None for clear)."""
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
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored). Mirrored cubes
# share their texture with the unmirrored one of the same key. Model space is the game's: y down, the front toward
# -z, feet at y 24.
#
# The body is a chain of square segments from the bottom up, each a pixel longer than its spacing so the joints stay
# closed, each tipped forward a little more than the one below: together they bend into the banana's arc, its inner
# (face) side forward, the bottom leaning back and the top forward, so the whole leans forward a touch. Neighbouring
# segments always differ in width, so their sides (which share planes, all the rotations being about x) never overlap
# in the same plane. (name, width, length, tilt relative to the one below)
SEGMENTS = [(9, 5, -0.32), (11, 5, 0.12), (12, 6, 0.12), (13, 6, 0.12), (12, 6, 0.12), (11, 5, 0.14), (9, 3, 0.16),
            (7, 3, 0.18), (4, 3, 0.22)]
FACE, ARMS = 4, 3
HIP_Y = 17.0
SIDES = (("right", -1, False), ("left", 1, True))


def parts():
    p = [("body", None, (0.0, HIP_Y, -1.0), (0.0, 0.0, 0.0), [])]
    for i, (w, L, tilt) in enumerate(SEGMENTS):
        parent = "body" if i == 0 else f"segment_{i - 1}"
        pivot = (0.0, 0.0 if i == 0 else -float(SEGMENTS[i - 1][1]), 0.0)
        cubes = [(f"segment_{i}", (-w / 2, -float(L), -w / 2), (w, L + 1, w), False)]
        if i == len(SEGMENTS) - 1:
            # The stem's cut end, a little flared.
            cubes.append(("stem_end", (-2.5, -float(L) - 1.0, -2.5), (5, 1, 5), False))
        p.append((f"segment_{i}", parent, pivot, (tilt, 0.0, 0.0), cubes))
    # The stubby brown tip under the bottom segment, set upright.
    p.append(("tip", "segment_0", (0.0, 0.0, 0.0), (0.3, 0.0, 0.0), [("tip", (-2.0, 0.0, -2.0), (4, 2, 4), False)]))
    # The eyes stand out from the face segment's front.
    fw = SEGMENTS[FACE][0]
    p.append(("eyes", f"segment_{FACE}", (0.0, -5.0, -fw / 2), (0.0, 0.0, 0.0), [
        ("eye", (-4.0, 0.0, -1.0), (3, 4, 1), False),
        ("eye", (1.0, 0.0, -1.0), (3, 4, 1), True)]))
    arm_tilt = -sum(t for _, _, t in SEGMENTS[:ARMS + 1])
    aw = SEGMENTS[ARMS][0]
    for side, s, mirror in SIDES:
        inner = -s     # toward the body
        p += [
            (side + "_arm", f"segment_{ARMS}", (s * (aw / 2 + 0.5), -4.0, 0.0), (arm_tilt, 0.0, -s * 0.22), [
                ("upper_arm", (-1.0, -1.0, -1.0), (2, 8, 2), mirror)]),
            # A hair of twist at the elbow keeps the forearm's sides out of the upper arm's planes where they meet.
            (side + "_forearm", side + "_arm", (0.0, 7.0, 0.0), (-0.4, s * 0.06, 0.0), [
                ("forearm", (-1.0, 0.0, -1.0), (2, 6, 2), mirror)]),
            (side + "_hand", side + "_forearm", (0.0, 6.5, 0.0), (0.1, 0.0, 0.0), [
                ("cuff", (-2.5, -1.0, -2.5), (5, 2, 5), mirror),
                ("palm", (-2.0, 0.0, -1.5), (4, 3, 3), mirror),
                ("finger", (-1.75, 3.0, -0.5), (1, 2, 1), mirror),
                ("finger", (-0.5, 3.0, -0.5), (1, 2, 1), mirror),
                ("finger", (0.75, 3.0, -0.5), (1, 2, 1), mirror),
                ("thumb", (2.0 if inner > 0 else -3.0, 1.0, -1.5), (1, 2, 1), mirror)]),
            (side + "_leg", None, (s * 2.75, 16.0, 0.5), (0.0, 0.0, 0.0), [
                ("leg", (-1.5, 0.0, -1.5), (3, 5, 3), mirror),
                ("shoe", (-2.0, 5.0, -3.5), (4, 2, 5), mirror),
                ("toe", (-1.5, 5.0, -4.5), (3, 2, 1), mirror),
                ("sole", (-2.5, 7.0, -5.0), (5, 1, 7), mirror)]),
        ]
    return p


PARTS = parts()
TREE = {}
for _name, _parent, *_ in PARTS:
    TREE.setdefault(_parent or "root", []).append(_name)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS}


def pack():
    """Texture offsets for each cube key, shelf-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in PARTS:
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
    return sum(len(c) for _, _, _, _, c in PARTS)


# ---------------------------------------------------------------------------------------------- painting
TOTAL = sum(L for _, L, _ in SEGMENTS)


def along(i, row):
    """How far up the banana (0 bottom, 1 top of the stem) a row of segment i's sides is (row 0 its top)."""
    base = sum(L for _, L, _ in SEGMENTS[:i])
    L = SEGMENTS[i][1]
    return max(0.0, min(1.0, (base + L - row + 0.5) / TOTAL))


def skin(t):
    """The banana's colour along its length: a brown tip shading into yellow, yellow, greening toward the stem."""
    if t < 0.05:
        return lerp(BROWN[0], YELLOW[3], t / 0.05)
    if t < 0.12:
        return lerp(YELLOW[3], YELLOW[1], (t - 0.05) / 0.07)
    if t < 0.70:
        return YELLOW[1]
    if t < 0.84:
        return lerp(YELLOW[1], GREEN[0], (t - 0.70) / 0.14)
    return lerp(GREEN[0], GREEN[2], (t - 0.84) / 0.16)


def facet(base, x, w, side_tone):
    """Across one facet: a soft highlight down the middle, darker toward the edges, a ridge line on each edge.
    side_tone shifts the whole face: negative paler (the inner curve), positive deeper (the outer)."""
    if x == 0 or x == w - 1:
        return lerp(base, RIDGE if base[1] > base[0] * 0.75 else lerp(base, GREEN[3], 0.5), 0.55 + 0.1 * max(0.0, side_tone))
    middle = abs(x - (w - 1) / 2) / ((w - 1) / 2)
    c = lerp(base, YELLOW[0], 0.35 * (1 - middle) ** 2) if side_tone <= 0 else base
    c = lerp(c, YELLOW[3], 0.25 * middle ** 2 + 0.25 * max(0.0, side_tone))
    if side_tone < 0:
        c = lerp(c, PALE, -side_tone * (1 - middle))
    return c


# Sugar spots: segment -> face -> [(x, y)]: a few, placed, never on the face.
SPOTS = {1: {"back": [(3, 2), (8, 4)], "left": [(6, 3)]},
         2: {"right": [(4, 1)], "back": [(6, 2)]},
         3: {"back": [(9, 3)], "left": [(3, 4)]},
         5: {"back": [(4, 2)], "right": [(7, 2)]}}


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    P = lambda key, painters: paint(tex, *CUBES[key], painters)

    for i, (w, L, _) in enumerate(SEGMENTS):
        spots = SPOTS.get(i, {})

        def side(tone, name, i=i, spots=spots):
            def f(x, y, fw, fh):
                c = facet(skin(along(i, y)), x, fw, tone)
                pts = spots.get(name, [])
                if (x, y) in pts:
                    return SPOT
                if any(abs(x - px) + abs(y - py) == 1 for px, py in pts):
                    return lerp(c, SPOT_HALO, 0.5)
                return c
            return f

        def cap(x, y, fw, fh, i=i):
            # Ends of a segment: mostly hidden in the next; the stem's top shows as the stem.
            return facet(skin(along(i, 0)), x, fw, 0.3)
        front = side(-0.6, "front")
        if i == ARMS:
            front = mouth_face(front)
        P(f"segment_{i}", {"front": front, "back": side(0.6, "back"), "right": side(0.15, "right"),
                           "left": side(0.15, "left"), "top": cap, "bottom": cap})

    # The stem's cut end: dark, fibrous ring and a darker heart.
    def stem_end(x, y, w, h):
        r = max(abs(x - (w - 1) / 2), abs(y - (h - 1) / 2))
        return BROWN[2] if r < 1 else BROWN[1] if r < 2 else GREEN[3]
    P("stem_end", {"top": stem_end, "bottom": lambda x, y, w, h: GREEN[3],
                   **{k: (lambda x, y, w, h: lerp(GREEN[3], BROWN[1], 0.4)) for k in ("front", "back", "right", "left")}})

    # The tip: brown, darker toward the very end.
    tip_side = lambda x, y, w, h: ramp(BROWN, 0.2 + 0.7 * y / max(1, h - 1)) if 0 < x < w - 1 else BROWN[2]
    P("tip", {"front": tip_side, "back": tip_side, "right": tip_side, "left": tip_side,
              "top": lambda x, y, w, h: BROWN[0], "bottom": lambda x, y, w, h: BROWN[2]})

    # Eyes: white, a big pupil low and toward the middle with a glint, a shaded bottom row.
    def eye(inner_last):
        def f(x, y, w, h):
            px = (1, 2) if inner_last else (0, 1)
            if y in (1, 2) and x in px:
                return WHITE if (y == 1 and x == px[0]) else PUPIL
            if y == 3 and x in px:
                return PUPIL
            return WHITE_SHADE if y == h - 1 or y == 0 and x != 1 else WHITE
        return f
    # The right eye (creature's right, -x) has its pupil toward +x (the middle); the mirrored left one flips.
    P("eye", {"front": eye(True), "back": lambda x, y, w, h: WHITE_SHADE, "top": lambda x, y, w, h: WHITE,
              "bottom": lambda x, y, w, h: WHITE_SHADE, "right": lambda x, y, w, h: WHITE_SHADE,
              "left": lambda x, y, w, h: WHITE_SHADE})

    # Limbs: dark brown sticks, lighter down the front.
    limb = lambda x, y, w, h: LIMB[0] if x == 0 else LIMB[1]
    for key in ("upper_arm", "forearm", "leg"):
        P(key, {"front": limb, "back": lambda x, y, w, h: LIMB[2], "right": limb, "left": limb,
                "top": lambda x, y, w, h: LIMB[1], "bottom": lambda x, y, w, h: LIMB[2]})

    # Gloves: white, a cuff with a rolled rim, three fingers and a thumb.
    glove = lambda x, y, w, h: GLOVE[0] if y < h - 1 else GLOVE[1]
    P("cuff", {k: (lambda x, y, w, h: GLOVE[0] if y == 0 else GLOVE[1]) for k in ("front", "back", "right", "left")}
      | {"top": lambda x, y, w, h: GLOVE[1], "bottom": lambda x, y, w, h: GLOVE[2]})
    P("palm", {k: glove for k in ("front", "back", "right", "left")}
      | {"top": lambda x, y, w, h: GLOVE[1], "bottom": lambda x, y, w, h: GLOVE[2]})
    for key in ("finger", "thumb"):
        P(key, {k: glove for k in ("front", "back", "right", "left")}
          | {"top": lambda x, y, w, h: GLOVE[1], "bottom": lambda x, y, w, h: GLOVE[1]})

    # Sneakers: red, white laces across the top, white soles, a white toe cap.
    def shoe_top(x, y, w, h):
        return SOLE if y in (1, 3) and 0 < x < w - 1 else SHOE[0]
    shoe_side = lambda x, y, w, h: SHOE[0] if y == 0 else SHOE[1]
    P("shoe", {"top": shoe_top, "front": shoe_side, "back": shoe_side, "right": shoe_side, "left": shoe_side,
               "bottom": lambda x, y, w, h: SHOE[2]})
    P("toe", {"top": lambda x, y, w, h: SOLE, "front": lambda x, y, w, h: SOLE if y == 1 else SHOE[0],
              "right": shoe_side, "left": shoe_side, "back": shoe_side, "bottom": lambda x, y, w, h: SHOE[2]})
    P("sole", {k: (lambda x, y, w, h: SOLE) for k in ("front", "back", "right", "left", "top")}
      | {"bottom": lambda x, y, w, h: lerp(SOLE, (150, 140, 128), 0.35)})

    tex.save("entity/big_banana/big_banana.png")
    return tex


def mouth_face(base):
    """The arm segment's front: an open cartoon smile with a tongue, and rosy cheeks either side."""
    def f(x, y, w, h):
        m = (w - 1) / 2
        dx = x - m
        if y == 1 and abs(dx) in (3.0,):
            return MOUTH
        if y == 2 and abs(dx) <= 2.0:
            return MOUTH
        if y == 3 and abs(dx) <= 1.0:
            return TONGUE if dx == 0 else MOUTH
        if y == 1 and 4.0 <= abs(dx) <= 5.0:
            return lerp(base(x, y, w, h), CHEEK, 0.75)
        if y == 2 and abs(dx) == 3.0:
            return MOUTH
        return base(x, y, w, h)
    return f


TEXTURE = paint_entity()


# ---------------------------------------------------------------------------------------------- items
def outlined(inside, colour, outline):
    """A 16x16 sprite from a coverage test and a colour function; its rim gets the outline colour (fn of x, y)."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            rim = not all(0 <= x + dx < 16 and 0 <= y + dy < 16 and inside(x + dx, y + dy)
                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            col = outline(x, y) if rim else colour(x, y)
            if col is not None:
                c.set(x, y, rgba(col))
    return c


# The Banana: an arc round a centre off the top-left, thick in the middle, thin at the ends; a green stem at the top
# right, the brown tip at the bottom left. Lighter along its inner (concave) edge.
BC, BR = (1.5, 1.5), 10.5
A0, A1 = math.radians(4), math.radians(86)


def banana_at(x, y):
    """(u along the banana 0 stem .. 1 tip, v across it -1 inner .. 1 outer) or None."""
    px, py = x + 0.5 - BC[0], y + 0.5 - BC[1]
    a = math.atan2(py, px)
    r = math.hypot(px, py)
    u = (a - A0) / (A1 - A0)
    if not -0.12 <= u <= 1.04:
        return None
    if u < 0:
        half = 0.9          # the stem: narrow
    else:
        half = 0.8 + 2.1 * math.sin(math.pi * min(1.0, u * 1.03)) ** 0.8
    v = (r - BR) / half
    return (u, v) if abs(v) <= 1.0 else None


def banana_colour(x, y):
    u, v = banana_at(x, y)
    if u < 0:
        return GREEN[1]
    if u > 0.93:
        return BROWN[1]
    base = YELLOW[1] if u < 0.85 else lerp(YELLOW[1], YELLOW[3], (u - 0.85) / 0.1)
    if u < 0.12:
        base = lerp(GREEN[0], base, u / 0.12)
    return lerp(base, YELLOW[0], 0.6) if v < -0.3 else base if v < 0.35 else lerp(base, YELLOW[3], 0.5)


def banana_rim(x, y):
    u, v = banana_at(x, y)
    if u < 0:
        return GREEN[3]
    if u > 0.93:
        return BROWN[2]
    return lerp(YELLOW[4], BROWN[1], 0.25)


BANANA_SPRITE = outlined(lambda x, y: banana_at(x, y) is not None, banana_colour, banana_rim)
BANANA_SPRITE.save("item/banana.png")


def peel(hub, flaps, stem=None):
    """A banana peel: a hub and tapered flaps (each (angle in degrees, length)), pale pith down the middle of each flap,
    yellow edges, brown flap ends. stem: pixels drawn green above the hub."""
    segs = [(hub, (hub[0] + math.cos(math.radians(a)) * n, hub[1] + math.sin(math.radians(a)) * n)) for a, n in flaps]

    def at(x, y):
        """(t along the nearest flap, distance / its radius there) or None."""
        p = (x + 0.5, y + 0.5)
        best = None
        for a, b in segs:
            ab = (b[0] - a[0], b[1] - a[1])
            t = max(0.0, min(1.0, ((p[0] - a[0]) * ab[0] + (p[1] - a[1]) * ab[1]) / (ab[0] ** 2 + ab[1] ** 2)))
            q = (a[0] + ab[0] * t, a[1] + ab[1] * t)
            r = 2.2 - 1.0 * t
            d = math.hypot(p[0] - q[0], p[1] - q[1]) / r
            if d <= 1.0 and (best is None or d < best[1]):
                best = (t, d)
        if best is None and math.hypot(p[0] - hub[0], p[1] - hub[1]) <= 2.3:
            best = (0.0, 0.0)
        return best

    def colour(x, y):
        if stem and (x, y) in stem:
            return GREEN[1]
        t, d = at(x, y)
        if t > 0.86:
            return BROWN[0]
        return PALE if d < 0.45 and t > 0.2 else YELLOW[1] if d < 0.8 else YELLOW[2]

    def rim(x, y):
        if stem and (x, y) in stem:
            return GREEN[3]
        t, d = at(x, y)
        return BROWN[1] if t > 0.8 else lerp(YELLOW[4], BROWN[1], 0.25)
    return outlined(lambda x, y: (stem is not None and (x, y) in stem) or at(x, y) is not None, colour, rim)


# Held: the classic peel, stem up and the flaps drooping out and down. Lying on the ground: flaps splayed all round.
PEEL_SPRITE = peel((8.0, 5.5), [(160, 6.5), (115, 8.5), (70, 8.5), (25, 6.5)], stem={(7, 1), (8, 1), (7, 2), (8, 2)})
PEEL_SPRITE.save("item/banana_peel.png")
PEEL_BLOCK = peel((8.0, 8.0), [(15, 6.8), (105, 6.8), (190, 6.8), (285, 6.8)])
PEEL_BLOCK.save("block/banana_peel.png")

# Spawn egg: the shared egg shape (as the other mod eggs use), banana yellow with a green top and brown sugar spots.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
top = min(ys)
egg = Canvas(w, h)
EGG_SPOTS = {(9, 8), (6, 10), (10, 11), (7, 13)}
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    green = y <= top + 2
    if outline:
        egg.px[i] = rgba(GREEN[3] if green else YELLOW[4])
    elif (x, y) in EGG_SPOTS:
        egg.px[i] = rgba(SPOT)
    else:
        light = (cx - x) + (cy - y)
        tone = 0 if light > 5 else 1 if light > 0 else 2
        egg.px[i] = rgba(GREEN[tone] if green else YELLOW[tone])
egg.save("item/big_banana_spawn_egg.png")

# ---------------------------------------------------------------------------------------------- json
MODELS, ITEMS = os.path.join(ASSETS, "models"), os.path.join(ASSETS, "items")


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


for name in ("banana", "banana_peel", "big_banana_spawn_egg"):
    write(os.path.join(MODELS, "item", name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})

# The peel lying on the ground: one flat, double-sided sheet just off the floor, turned to face the way it was dropped.
write(os.path.join(MODELS, "block", "banana_peel.json"), OrderedDict([
    ("textures", OrderedDict([("particle", "mymod:block/banana_peel"), ("peel", "mymod:block/banana_peel")])),
    ("elements", [OrderedDict([("from", [0, 0.25, 0]), ("to", [16, 0.25, 16]), ("faces", OrderedDict([
        ("up", {"uv": [0, 0, 16, 16], "texture": "#peel"}),
        ("down", {"uv": [0, 16, 16, 0], "texture": "#peel"})]))])])]))
write(os.path.join(ASSETS, "blockstates", "banana_peel.json"), {"variants": OrderedDict(
    (f"facing={d}", OrderedDict([("model", "mymod:block/banana_peel")] + ([("y", r)] if r else [])))
    for d, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)))})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(MYDATA, "loot_table/entities/big_banana.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:banana", 2, 4),
               OrderedDict([("condition", OrderedDict([
                   ("type", "minecraft:random_chance_with_enchanted_bonus"),
                   ("enchanted_chance", {"type": "minecraft:linear", "base": 0.4, "per_level_above_first": 0.1}),
                   ("enchantment", "minecraft:looting"),
                   ("unenchanted_chance", 0.3)])),
                   ("entries", [{"type": "minecraft:item", "name": "mymod:banana_peel"}]),
                   ("rolls", 1)])]),
    ("random_sequence", "mymod:entities/big_banana")]))
write(os.path.join(MYDATA, "loot_table/blocks/banana_peel.json"), OrderedDict([
    ("type", "minecraft:block"),
    ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                            ("entries", [{"type": "minecraft:item", "name": "mymod:banana_peel"}]), ("rolls", 1)])]),
    ("random_sequence", "mymod:blocks/banana_peel")]))


def advancement(name, ingredient, category="misc"):
    write(os.path.join(MYDATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shapeless(name, ingredients, result, count, category="misc"):
    write(os.path.join(MYDATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, ingredients[0], category)


# A peel left to rot in dirt breeds earthworms, the fishing system's basic bait, as rotten flesh does.
shapeless("earthworms_from_banana_peel", ["mymod:banana_peel", "minecraft:dirt"], "mymod:earthworms", 4)


def tag_add(path, entry):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


# What a Big Banana is glad to see in a player's hand.
for item in ("mymod:banana", "mymod:banana_peel"):
    tag_add(os.path.join(MYDATA, "tags/item/makes_big_banana_happy.json"), item)
tag_add(os.path.join(DATA, "c", "tags/item/foods/fruit.json"), "mymod:banana")


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


def event(name, sounds):
    return name, OrderedDict([("subtitle", "subtitles.mymod." + name), ("sounds", sounds)])


# Vanilla sounds, pitched: squishy slime and honey for the banana, a goofy note-block boop for its happy bounce.
SOUNDS = OrderedDict([
    event("big_banana_ambient", vanilla(["mob/slime/small%d" % i for i in (1, 2, 3, 4, 5)], 0.75)
          + vanilla(["note/bit"], 0.6, volume=0.5)),
    event("big_banana_hurt", vanilla(["mob/slime/big%d" % i for i in (1, 2, 3, 4)], 1.25)),
    event("big_banana_death", vanilla(["block/honeyblock/break%d" % i for i in (1, 2, 3)], 0.6)),
    event("big_banana_step", vanilla(["block/honeyblock/step%d" % i for i in (1, 2, 3, 4, 5)], 1.2)),
    event("big_banana_slap", vanilla(["mob/slime/attack1", "mob/slime/attack2"], 0.65)),
    event("big_banana_boop", vanilla(["note/bit"], 1.0) + vanilla(["note/banjo"], 1.2)),
    event("banana_peel_slip", vanilla(["block/honeyblock/slide%d" % i for i in (1, 2, 3, 4)], 1.7)),
    event("banana_peel_place", vanilla(["mob/slime/small1", "mob/slime/small2"], 1.4)),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.mymod.big_banana", "Big Banana"),
    ("entity.mymod.thrown_banana_peel", "Banana Peel"),
    ("item.mymod.big_banana_spawn_egg", "Big Banana Spawn Egg"),
    ("item.mymod.banana", "Banana"),
    ("item.mymod.banana_peel", "Banana Peel"),
    ("item.mymod.banana_peel.use", "Place it on the ground, or throw it: whatever steps on it slips"),
    ("block.mymod.banana_peel", "Banana Peel"),
    ("subtitles.mymod.big_banana_ambient", "Big Banana squishes"),
    ("subtitles.mymod.big_banana_hurt", "Big Banana hurts"),
    ("subtitles.mymod.big_banana_death", "Big Banana dies"),
    ("subtitles.mymod.big_banana_step", "Footsteps"),
    ("subtitles.mymod.big_banana_slap", "Big Banana slaps"),
    ("subtitles.mymod.big_banana_boop", "Big Banana boops happily"),
    ("subtitles.mymod.banana_peel_slip", "Something slips on a banana peel"),
    ("subtitles.mymod.banana_peel_place", "Banana peel placed"),
    ("tag.item.mymod.makes_big_banana_happy", "Makes Big Bananas Happy"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "mymod", "client",
                                     "banana", "BigBananaModel.java"))


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
        used = name in TREE
        decl = f"PartDefinition {var} = " if used else ""
        lines.append(f"\t\t{decl}{('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", "
                     f"{builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_banana.py\n", "\t\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose():
    return {name: [pivot, rot] for name, _, pivot, rot, _ in PARTS}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def wind_up(attack):
    return smooth(attack / 0.4 if attack < 0.4 else 1.0 - (attack - 0.4) / 0.2)


def across(attack):
    return smooth(0.0 if attack < 0.35 else (attack - 0.35) / 0.25 if attack < 0.6 else 1.0 - (attack - 0.6) / 0.4)


WALK_RATE = 0.6662


def animate(pose, walk=0.0, speed=0.0, age=0.0, attack=0.0, happy=0.0, yaw=0.0):
    """Python twin of BigBananaModel.setupAnim, for the preview."""
    step = walk * WALK_RATE
    stride = math.cos(step)
    hop = abs(math.sin(step))
    n = len(SEGMENTS)
    add(pose, "right_leg", dx=stride * 0.9 * speed)
    add(pose, "left_leg", dx=-stride * 0.9 * speed)
    add(pose, "body", dz=0.1 * stride * speed, pos=(0, (1 - hop) * 1.2 * speed, 0))
    for i in range(n):
        add(pose, f"segment_{i}", dz=0.035 * speed * math.sin(step - i * 0.45) + 0.012 * math.sin(age * 0.07 - i * 0.4),
            dx=0.01 * math.sin(age * 0.05 - i * 0.3))
    add(pose, "right_arm", dx=-stride * 0.6 * speed)
    add(pose, "left_arm", dx=stride * 0.6 * speed)
    flap = 0.2 * hop * speed + 0.04 * math.sin(age * 0.07)
    add(pose, "right_arm", dz=flap)
    add(pose, "left_arm", dz=-flap)
    y = max(-50.0, min(50.0, yaw)) * math.pi / 180
    add(pose, f"segment_{FACE - 1}", dy=y * 0.3)
    add(pose, f"segment_{FACE}", dy=y * 0.5)
    if attack > 0:
        up, ac = wind_up(attack), across(attack)
        add(pose, "right_arm", dz=2.0 * up, dx=-1.4 * ac, dy=-0.7 * ac)
        add(pose, "right_forearm", dx=0.35 * max(up, ac))
        twist = 0.08 * up - 0.14 * ac
        for i in range(1, FACE + 1):
            add(pose, f"segment_{i}", dy=twist)
        add(pose, "segment_2", dx=0.1 * ac)
    if happy > 0:
        hh = math.sin(happy * math.pi)
        add(pose, "right_arm", dz=2.3 * hh)
        add(pose, "left_arm", dz=-2.3 * hh)
        add(pose, "right_forearm", dx=0.35 * hh)
        add(pose, "left_forearm", dx=0.35 * hh)
        for i in range(n):
            add(pose, f"segment_{i}", dz=0.07 * hh * math.sin(age * 0.9 - i * 0.7))
        if happy < 0.25:
            add(pose, "body", pos=(0, 1.5 * math.sin(happy / 0.25 * math.pi), 0))
    return pose


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


def quads(pose, labels=False):
    """Every textured face of the posed model: (corners in model space, uv rect[, label, outward normal])."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot = pose[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for k, (cube, frm, size, mirror) in enumerate(PART_CUBES.get(child, [])):
                (u, v), _ = CUBES[cube]
                for verts, uv in cube_faces(m, o, u, v, frm, size, mirror):
                    if labels:
                        centre = tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3))
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


# Z-fighting: no two faces of the model at rest may share a plane, face the same way and overlap.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from coplanar import Face, fights
_hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(rest_pose(), labels=True)])
if _hits:
    for h_ in _hits:
        print("z-fight: big banana", h_)
    raise SystemExit(f"big banana: {len(_hits)} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, _ in quads(pose) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
print(f"big banana: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f} "
      f"({24 - ext[1][0]:.1f} px tall), feet y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


def render(pose, view, scale=6, width=240, height=340, light=(0.35, -0.8, -0.5)):
    """Orthographic render. view: 'front' (looking at its face), 'side' (from its right), 'three' (three-quarters) or
    'back'. Feet at the bottom."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    xs = [p[0] * ca - p[2] * sa for verts, _ in quads(pose) for p in verts]
    ox, oy, ly = width * 0.5 - (min(xs) + max(xs)) / 2 * scale, height - 14, 24.0
    gy = int(oy)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    for verts, (ua, va, ub, vb) in quads(pose):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        shade = 0.6 + 0.4 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
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
                for (a_, b_) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s, t = a_ / nu, b_ / nv
                    p = tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))
                    x = p[0] * ca - p[2] * sa
                    z = p[0] * sa + p[2] * ca
                    corners.append((ox + x * scale, oy + (p[1] - ly) * scale, z))
                depth = sum(c[2] for c in corners) / 4
                c = (int(col[0] * shade), int(col[1] * shade), int(col[2] * shade), 255)
                fill(img, zbuf, width, height, corners, depth, c)
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


def sprite_tile(canvas, width=240, height=340, scale=12):
    img = [(46, 52, 64, 255)] * (width * height)
    ox, oy = (width - 16 * scale) // 2, (height - 16 * scale) // 2
    for y in range(16 * scale):
        for x in range(16 * scale):
            c = canvas.get(x // scale, y // scale)
            if c[3]:
                img[(oy + y) * width + ox + x] = c
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
    views = [render(rest_pose(), "front"), render(rest_pose(), "side"), render(rest_pose(), "three"),
             render(rest_pose(), "back"),
             render(animate(rest_pose(), walk=1.2, speed=1.0, age=10), "three"),
             render(animate(rest_pose(), walk=3.6, speed=1.0, age=20), "front"),
             render(animate(rest_pose(), attack=0.4, age=5), "three"),
             render(animate(rest_pose(), attack=0.55, age=5), "front"),
             render(animate(rest_pose(), attack=0.7, age=5), "three"),
             render(animate(rest_pose(), happy=0.5, age=7), "front"),
             sprite_tile(BANANA_SPRITE), sprite_tile(PEEL_SPRITE), sprite_tile(PEEL_BLOCK), sprite_tile(egg)]
    out, bw, bh = board(views, 240, 340, 5)
    png(os.path.join(PREVIEW, "preview_banana.png"), bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
