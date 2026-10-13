"""Builds the Werewolf: its model (the cube layout in PARTS, written into client/werewolf/WerewolfModel.java between the
GENERATED markers), its entity texture and the glow layer for its eyes, its spawn egg, the Werewolf Pelt and the Feral
Lining exoskeleton module made from it, and their item models and definitions. On the data side it writes the
werewolf's loot table, the pelt's recipes (with recipe-book advancements) and adds the werewolf to the
mymod:holy_vulnerable entity tag. Its sounds (vanilla wolf and cat sounds, pitched down) and lang entries are merged
into sounds.json and en_us.json.

The texture is painted in smooth ramps from vanilla colours: a dark, desaturated coat (the black wolf's greys), a dull
cream throat and muzzle, three deliberate scars on the breast and one on each shoulder, dark-tipped claws, pale teeth
in red gums with a dark-red stain round the mouth, and narrow amber-red eyes that glow. The rags of a plains
villager's robe (vanilla's colours) hang at its hips, across its chest and round its thighs, so it reads as the
villager it was. No noise, no dithering; a rerun gives the same files.

The model is checked for z-fighting at rest (tools/coplanar.py), and its extents are printed (it should stand on y 24
and stay within about a block's width).

With --preview DIR it also renders, into DIR, preview_werewolf_v2.png (front, side and three-quarters at rest; a
lunging swipe while hunting; the howl; a hunting stalk; and the head up close mid-snap, from three sides) and
preview_werewolf_texture.png, using the same cubes, pivots and UV layout as the Java model, and a Python twin of its
animation.

Usage: python3 tools/gen_werewolf.py src/main/resources/assets/mymod [--preview DIR]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/mymod
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
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
# Fur, light to dark: vanilla's black wolf, a touch of the ashen one, desaturated; smooth ramps run through it.
FUR = [(118, 112, 110), (96, 91, 90), (77, 73, 74), (60, 57, 59), (45, 43, 46), (32, 30, 33)]
# The dull cream of the throat, belly and muzzle, toned right down.
CREAM = [(150, 140, 128), (122, 113, 104)]
NOSE = (24, 22, 24)
SKIN = (78, 62, 62)        # palms, soles and bare scarred patches
SCAR = (138, 104, 100)
SCAR_DARK = (96, 64, 64)
CLAW = [(124, 116, 106), (72, 66, 64), (30, 27, 28)]   # root to tip
TOOTH = (234, 228, 208)
TOOTH_SHADE = (196, 188, 168)
GUM = (138, 54, 60)
MOUTH = (70, 22, 26)
BLOOD = (92, 30, 32)
EYE = (255, 96, 32)        # amber-red
EYE_HOT = (255, 196, 96)
INNER_EAR = (104, 74, 74)
# A plains villager's robe (vanilla's type/plains.png), light to dark.
ROBE = [(113, 84, 77), (90, 66, 61), (68, 50, 46), (53, 37, 31)]
CLAW_SHADE = CLAW[1]

TEX_W, TEX_H = 128, 128


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list, t in 0..1 (no banding beyond the texel grid, no dithering)."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


def fur(y, h, top=0.1, bottom=0.75):
    """Fur down a face: lighter where the light catches the top, darker underneath."""
    return ramp(FUR, top + (bottom - top) * (y / max(1, h - 1)))


def faces(u, v, w, h, d):
    """A model cube's faces in the texture, as (u, v, width, height), laid out as the game lays them out. 'top' and
    'bottom' are as seen in the world (the model is upside down); in both the front edge is the last row. In 'right'
    (the cube's -x side, the creature's right) the front edge is the last column, in 'left' the first."""
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
# -z, feet at y 24. This is the one place the shape is defined: it's written into client/werewolf/WerewolfModel.java
# (between the GENERATED markers), painted here, and drawn by the preview.
#
# A hunched, lanky wolf-man: hips leaning forward under a broad chest that leans further, massive shoulders and a
# ragged mane of ridges down the neck and spine; the neck thrusts the head forward and low. Long arms (upper arm,
# forearm, a big hand, three long dark claws) hang past the knees. Digitigrade legs: a thick thigh angled forward, the
# shin back to a high hock, a long foot. A heavy brow over narrow slanted eyes, a long snout with its lips curled back
# off the teeth and fangs, and a lower jaw that hangs open and snaps. Ears pinned back. What's left of the villager:
# a tattered robe skirt at the hips, a torn strip of it across the chest, and rags of trousers on the thighs.
LEAN_HIPS, LEAN_CHEST = 0.30, 0.55
LEAN = LEAN_HIPS + LEAN_CHEST
SIDES = (("right", -1, False), ("left", 1, True))


def parts():
    p = [
        ("body", None, (0.0, 8.0, 2.0), (LEAN_HIPS, 0.0, 0.0), [
            ("waist", (-3.5, -6.0, -2.5), (7, 6, 5), False),
            ("skirt", (-4.5, -2.0, -3.5), (9, 6, 7), False),
            ("ridge_low", (-1.0, -7.5, 0.5), (2, 2, 3), False)]),
        ("chest", "body", (0.0, -5.0, 0.5), (LEAN_CHEST, 0.0, 0.0), [
            ("chest", (-6.0, -9.0, -5.0), (12, 9, 9), False),
            ("shoulder", (-9.0, -10.0, -4.5), (4, 6, 7), False),
            ("shoulder", (5.0, -10.0, -4.5), (4, 6, 7), True),
            ("ridge_mid", (-1.5, -11.5, -2.0), (3, 3, 4), False),
            ("ridge_back", (-1.0, -11.0, 2.0), (2, 2, 3), False)]),
        ("sash", "chest", (0.0, -4.5, -0.5), (0.0, 0.0, 0.6), [
            ("sash", (-7.5, -1.5, -5.0), (15, 3, 10), False)]),
        ("neck", "chest", (0.0, -7.0, -4.0), (-0.45, 0.0, 0.0), [
            ("neck", (-3.0, -3.0, -5.0), (6, 6, 5), False),
            ("ridge_neck", (-1.0, -5.0, -4.5), (2, 2, 4), False)]),
        ("head", "neck", (0.0, 0.0, -5.0), (-0.25, 0.0, 0.0), [
            ("skull", (-4.0, -4.0, -5.0), (8, 7, 6), False),
            ("brow", (-4.5, -5.0, -6.0), (9, 2, 2), False),
            ("snout", (-2.5, -1.0, -12.0), (5, 3, 7), False),
            ("nose", (-1.5, -1.5, -12.5), (3, 2, 2), False),
            ("teeth", (-2.0, 2.0, -10.5), (4, 1, 5), False),
            ("fang", (-2.3, 2.1, -11.0), (1, 2, 1), False),
            ("fang", (1.3, 2.1, -11.0), (1, 2, 1), True)]),
        ("jaw", "head", (0.0, 2.0, -5.0), (0.3, 0.0, 0.0), [
            ("jaw", (-2.5, 0.0, -6.0), (5, 2, 6), False),
            ("teeth_low", (-1.5, -1.0, -5.5), (3, 1, 5), False)]),
        ("tail", "body", (0.0, -1.0, 2.0), (-1.15, 0.0, 0.0), [
            ("tail", (-1.5, -1.5, 0.0), (3, 3, 9), False)]),
    ]
    for side, s, mirror in SIDES:
        p += [
            (side + "_ear", "head", (s * 2.5, -4.0, -1.0), (0.9, 0.0, s * 0.2), [
                ("ear", (-1.5, -5.0, -0.5), (3, 5, 1), mirror)]),
            # Arms hang from the shoulders, turned back against the lean so they reach down and a little forward.
            (side + "_arm", "chest", (s * 7.5, -7.0, -1.5), (-LEAN - 0.2, 0.0, -s * 0.12), [
                ("upper_arm", (-2.0, -1.0, -2.0), (4, 11, 4), mirror)]),
            (side + "_forearm", side + "_arm", (0.0, 9.5, 0.0), (-0.35, 0.0, 0.0), [
                ("forearm", (-1.5, 0.0, -1.5), (3, 10, 3), mirror)]),
            (side + "_hand", side + "_forearm", (0.0, 9.5, 0.0), (0.15, 0.0, 0.0), [
                ("hand", (-2.0, 0.0, -2.0), (4, 3, 4), mirror),
                ("claw", (-1.9, 3.0, -1.9), (1, 4, 1), mirror),
                ("claw", (-0.5, 3.0, -1.9), (1, 4, 1), mirror),
                ("claw", (0.9, 3.0, -1.9), (1, 4, 1), mirror)]),
            # Digitigrade: the thigh angled forward, the shin back, the hock down, the foot flat.
            (side + "_leg", None, (s * 3.5, 7.4, 3.0), (-0.55, 0.0, 0.0), [
                ("thigh", (-2.5, -1.0, -2.5), (5, 8, 5), mirror),
                ("trousers", (-3.0, -1.5, -3.0), (6, 5, 6), mirror)]),
            (side + "_shin", side + "_leg", (0.0, 6.5, 0.0), (1.3, 0.0, 0.0), [
                ("shin", (-1.5, 0.0, -1.5), (3, 7, 3), mirror)]),
            (side + "_hock", side + "_shin", (0.0, 6.5, 0.0), (-1.05, 0.0, 0.0), [
                ("hock", (-1.0, 0.0, -1.5), (2, 5, 3), mirror)]),
            (side + "_foot", side + "_hock", (0.0, 4.5, 0.0), (0.3, 0.0, 0.0), [
                ("foot", (-2.0, 0.0, -6.0), (4, 2, 7), mirror),
                ("toe_claws", (-2.5, 0.5, -7.5), (5, 1, 2), mirror)]),
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
HEM = [5, 3, 4, 2, 5, 4, 1, 3, 5, 2, 4, 3, 5, 1, 4, 3]


def ragged(base, cut_rows, flip=False, phase=0):
    """Cloth with a torn hem: the last rows of a face (or the first, flipped) go missing column by column."""
    def f(x, y, w, h):
        k = HEM[(x + phase) % len(HEM)] * cut_rows // 5
        row = (h - 1 - y) if not flip else y
        if row < k:
            return None
        return base(x, y, w, h)
    return f


def robe_side(x, y, w, h):
    return ramp(ROBE, 0.15 + 0.7 * y / max(1, h - 1))


def claws(x, y, w, h):
    return ramp(CLAW, y / max(1, h - 1))


SCARS = {(2, 2), (3, 3), (4, 4), (4, 1), (5, 2), (6, 3), (7, 4), (2, 6), (3, 7)}


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    glow = Canvas(TEX_W, TEX_H)
    P = lambda key, painters: paint(tex, *CUBES[key], painters)
    side = lambda x, y, w, h: fur(y, h)
    top = lambda x, y, w, h: FUR[1]
    under = lambda x, y, w, h: FUR[4]
    plain = {"top": top, "bottom": under, "right": side, "left": side, "front": side, "back": side}

    def mane(x, y, w, h):
        # Darker than the coat; the tips of the ridges catch the light, and their top edge is ragged.
        if y == 0 and x % 2 == 1:
            return None
        return ramp(FUR, 0.35 + 0.65 * y / max(1, h - 1)) if y else FUR[2]
    for key in ("ridge_low", "ridge_mid", "ridge_back", "ridge_neck"):
        P(key, {"top": lambda x, y, w, h: FUR[2] if x % 2 == 0 else FUR[3], "bottom": under,
                "right": mane, "left": mane, "front": mane, "back": mane})

    # Waist: lean, a pale belly down the front.
    def waist_front(x, y, w, h):
        return lerp(CREAM[1], fur(y, h), 0.55) if 2 <= x <= w - 3 else fur(y, h)
    P("waist", {**plain, "front": waist_front})

    # The robe skirt: a dark belt, then the brown robe torn short; open at the bottom.
    def skirt(x, y, w, h):
        return ROBE[3] if y == 0 else robe_side(x, y, w, h)
    rag = lambda ph: ragged(skirt, 4, phase=ph)
    P("skirt", {"top": lambda x, y, w, h: ROBE[2], "front": rag(0), "back": rag(5), "right": rag(9), "left": rag(3)})

    # Chest: a pale ruff at the throat, three old claw scars across a bare patch on the right of the breast.
    def chest_front(x, y, w, h):
        if (x, y) in SCARS:
            return SCAR if (x + y) % 2 == 0 else SCAR_DARK
        if (x - 1, y) in SCARS or (x + 1, y) in SCARS:
            return lerp(SKIN, fur(y, h), 0.45)
        middle = abs(x - (w - 1) / 2)
        reach = 3.5 - y * 0.6
        if middle <= reach:
            return lerp(CREAM[0] if y < 2 else CREAM[1], fur(y, h), 0.25 + 0.1 * middle)
        return fur(y, h, 0.15, 0.8)

    def chest_back(x, y, w, h):
        return ramp(FUR, 0.6 + 0.3 * y / (h - 1)) if abs(x - (w - 1) / 2) <= 1.5 else fur(y, h, 0.3, 0.85)

    def chest_top(x, y, w, h):
        return FUR[3] if abs(x - (w - 1) / 2) <= 1.5 else ramp(FUR, 0.15 + 0.2 * (1 - y / (h - 1)))
    P("chest", {**plain, "front": chest_front, "back": chest_back, "top": chest_top,
                "bottom": lambda x, y, w, h: FUR[3]})

    # Shoulders: humped and heavy, a bare raked scar on the outside.
    def shoulder_out(x, y, w, h):
        if y in (2, 3) and x == 1 + (y - 2) * 2:
            return SCAR
        if y in (2, 3) and x == 2 + (y - 2) * 2:
            return SCAR_DARK
        return fur(y, h, 0.05, 0.6)
    P("shoulder", {**plain, "top": lambda x, y, w, h: FUR[0] if y >= 2 else FUR[1], "right": shoulder_out,
                   "left": lambda x, y, w, h: fur(y, h, 0.4, 0.8)})

    # The torn strip of robe across the chest: ragged both edges, open top and bottom.
    sash = lambda ph: ragged(ragged(robe_side, 2, phase=ph), 2, flip=True, phase=ph + 7)
    P("sash", {"front": sash(0), "back": sash(4), "right": sash(2), "left": sash(6)})

    # Neck: pale throat, darker hackles.
    def neck_front(x, y, w, h):
        return lerp(CREAM[1], fur(y, h), 0.4) if 1 <= x <= w - 2 else fur(y, h)
    P("neck", {**plain, "front": neck_front, "bottom": lambda x, y, w, h: lerp(CREAM[1], FUR[3], 0.4),
               "top": lambda x, y, w, h: FUR[3]})

    # Skull: narrow eyes slanting down to the snout under the heavy brow, a dark crown stripe.
    EYES = {(1, 1): EYE, (2, 1): EYE_HOT, (3, 2): EYE, (4, 2): EYE, (5, 1): EYE_HOT, (6, 1): EYE}

    def skull_front(x, y, w, h):
        if (x, y) in EYES:
            return EYES[(x, y)]
        if y == 2 and x in (1, 2, 5, 6) or y == 1 and x in (3, 4):
            return FUR[5]          # the shadowed sockets round the eyes
        return fur(y, h, 0.4, 0.8)

    def skull_side(x, y, w, h):
        return fur(y, h, 0.15, 0.7)
    P("skull", {"front": skull_front, "back": lambda x, y, w, h: FUR[3], "right": skull_side, "left": skull_side,
                "top": lambda x, y, w, h: FUR[3] if abs(x - (w - 1) / 2) <= 1 else FUR[1],
                "bottom": lambda x, y, w, h: lerp(CREAM[1], FUR[3], 0.5)})
    su, sv = faces(*CUBES["skull"][0], *CUBES["skull"][1])["front"][:2]
    for (x, y), c in EYES.items():
        glow.set(su + x, sv + y, rgba(c))

    P("brow", {"front": lambda x, y, w, h: FUR[3] if y == 0 else FUR[4], "top": lambda x, y, w, h: FUR[2],
               "bottom": lambda x, y, w, h: FUR[5], "right": lambda x, y, w, h: FUR[3],
               "left": lambda x, y, w, h: FUR[3], "back": lambda x, y, w, h: FUR[3]})

    # Snout: grey along the bridge, pale muzzle, the lips curled back in a red line along the bottom, stained darker
    # toward the front.
    def snout_side(front_col):
        def f(x, y, w, h):
            stain = 1.0 - abs(x - front_col(w)) / w
            if y == h - 1:
                return lerp(GUM, BLOOD, stain * 0.6)
            if y == h - 2:
                return lerp(CREAM[1], BLOOD, 0.15 + 0.3 * stain)
            return FUR[1] if y == 0 else lerp(CREAM[1], FUR[2], 0.3)
        return f
    P("snout", {"top": lambda x, y, w, h: ramp(FUR, 0.05 + 0.2 * abs(x - (w - 1) / 2)),
                "front": lambda x, y, w, h: CREAM[1] if y < h - 1 else GUM, "back": lambda x, y, w, h: FUR[3],
                "right": snout_side(lambda w: w - 1), "left": snout_side(lambda w: 0),
                "bottom": lambda x, y, w, h: MOUTH})
    P("nose", {"top": lambda x, y, w, h: (44, 41, 44), "front": lambda x, y, w, h: NOSE,
               "right": lambda x, y, w, h: NOSE, "left": lambda x, y, w, h: NOSE, "bottom": lambda x, y, w, h: NOSE,
               "back": lambda x, y, w, h: NOSE})

    def teeth_side(x, y, w, h):
        return TOOTH if x % 2 == 0 else GUM
    teeth = {"front": teeth_side, "right": teeth_side, "left": teeth_side, "back": lambda x, y, w, h: GUM,
             "top": lambda x, y, w, h: GUM, "bottom": lambda x, y, w, h: TOOTH_SHADE}
    P("teeth", teeth)
    P("teeth_low", {**teeth, "top": lambda x, y, w, h: TOOTH_SHADE, "bottom": lambda x, y, w, h: GUM})
    fang = lambda x, y, w, h: GUM if y == 0 else TOOTH if y < h - 1 else TOOTH_SHADE
    P("fang", {"front": fang, "back": fang, "right": fang, "left": fang, "top": lambda x, y, w, h: GUM,
               "bottom": lambda x, y, w, h: TOOTH_SHADE})

    # Lower jaw: gums along the top edge, a pale chin with a dark-red stain, the tongue and mouth inside.
    def jaw_side(x, y, w, h):
        return GUM if y == 0 else lerp(CREAM[1], FUR[3], 0.4)

    def jaw_front(x, y, w, h):
        return GUM if y == 0 else lerp(CREAM[1], BLOOD, 0.25)
    P("jaw", {"front": jaw_front, "right": jaw_side, "left": jaw_side, "back": lambda x, y, w, h: FUR[3],
              "top": lambda x, y, w, h: BLOOD if 1 <= x <= w - 2 else MOUTH,
              "bottom": lambda x, y, w, h: lerp(CREAM[1], FUR[3], 0.55)})

    # Ears: pointed, dark tipped, a dull inside.
    def ear_front(x, y, w, h):
        if y == 0 and x != w // 2:
            return None
        if y <= 1:
            return FUR[5]
        return INNER_EAR if x == w // 2 else FUR[3]

    def ear_back(x, y, w, h):
        if y == 0 and x != w // 2:
            return None
        return FUR[5] if y <= 1 else FUR[3]
    P("ear", {"front": ear_front, "back": ear_back,
              "right": lambda x, y, w, h: None if y == 0 else FUR[4], "left": lambda x, y, w, h: None if y == 0 else FUR[4],
              "top": lambda x, y, w, h: FUR[5] if x == w // 2 else None, "bottom": lambda x, y, w, h: FUR[3]})

    # Tail: ragged, darkening to the tip.
    def tail_side(tip_first):
        def f(x, y, w, h):
            t = (1 - x / (w - 1)) if tip_first else x / (w - 1)
            return ramp(FUR, 0.2 + 0.15 * y + 0.5 * t)
        return f
    P("tail", {"front": lambda x, y, w, h: FUR[3], "back": lambda x, y, w, h: FUR[5],
               "right": tail_side(False), "left": tail_side(True),
               "top": lambda x, y, w, h: ramp(FUR, 0.15 + 0.6 * (1 - y / (h - 1))),
               "bottom": lambda x, y, w, h: FUR[4]})

    # Arms: the coat darkening down the limb into big dark hands with bare palms; long claws, dark at the tips.
    P("upper_arm", {**plain, "top": lambda x, y, w, h: FUR[1], "right": lambda x, y, w, h: fur(y, h, 0.05, 0.5),
                    "front": lambda x, y, w, h: fur(y, h, 0.1, 0.55), "back": lambda x, y, w, h: fur(y, h, 0.3, 0.7),
                    "left": lambda x, y, w, h: fur(y, h, 0.3, 0.7)})
    P("forearm", {k: (lambda x, y, w, h: fur(y, h, 0.45, 0.85)) for k in ("front", "back", "right", "left")}
      | {"top": lambda x, y, w, h: FUR[2], "bottom": lambda x, y, w, h: FUR[5]})
    P("hand", {"front": lambda x, y, w, h: FUR[4], "back": lambda x, y, w, h: FUR[4], "right": lambda x, y, w, h: FUR[4],
               "left": lambda x, y, w, h: FUR[4], "top": lambda x, y, w, h: FUR[3], "bottom": lambda x, y, w, h: SKIN})
    P("claw", {"front": claws, "back": claws, "right": claws, "left": claws, "top": lambda x, y, w, h: CLAW[0],
               "bottom": lambda x, y, w, h: CLAW[2]})

    # Legs: rags of trousers round the thighs (open top and bottom, torn short), the coat darkening down shin and hock
    # to dark long feet with bare soles and a row of claws.
    P("thigh", {**plain, "front": lambda x, y, w, h: fur(y, h, 0.15, 0.6)})
    tr = lambda ph: ragged(robe_side, 3, phase=ph)
    P("trousers", {"front": tr(1), "back": tr(6), "right": tr(11), "left": tr(4)})
    for key in ("shin", "hock"):
        P(key, {**{k: (lambda x, y, w, h: fur(y, h, 0.4, 0.85)) for k in ("front", "back", "right", "left")},
                "top": lambda x, y, w, h: FUR[3], "bottom": lambda x, y, w, h: FUR[5]})
    P("foot", {"top": lambda x, y, w, h: ramp(FUR, 0.55 + 0.3 * (1 - y / (h - 1))), "bottom": lambda x, y, w, h: SKIN,
               "front": lambda x, y, w, h: FUR[5], "back": lambda x, y, w, h: FUR[4],
               "right": lambda x, y, w, h: FUR[4], "left": lambda x, y, w, h: FUR[4]})

    def toe(x, y, w, h):
        return None if x % 2 == 1 and w > 2 else CLAW[2]

    def toe_len(front_last):
        def f(x, y, w, h):
            return ramp(CLAW, (x / (w - 1)) if front_last else (1 - x / (w - 1)))
        return f
    P("toe_claws", {"front": toe, "back": toe, "right": toe_len(True), "left": toe_len(False),
                    "top": lambda x, y, w, h: None if x % 2 == 1 else ramp(CLAW, y / (h - 1)),
                    "bottom": lambda x, y, w, h: None if x % 2 == 1 else CLAW[1]})

    tex.save("entity/werewolf/werewolf.png")
    glow.save("entity/werewolf/werewolf_eyes.png")
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


# Werewolf Pelt: a stretched hide, legs splayed and a bushy tail, dark grey fur with a cream belly stripe.
PELT = ["................",
        "..kk........kk..",
        ".kddk......kddk.",
        ".kdmmkkkkkkmmdk.",
        "..kmmllllllmmk..",
        "...kmllccllmk...",
        "...kmlcccclmk...",
        "...kmlcccclmk...",
        "...kmlcccclmk...",
        "...kmllccllmk...",
        "..kmmllllllmmk..",
        ".kdmmkmmmmkmmdk.",
        ".kddk.kmmk.kddk.",
        "..kk..kmmk..kk..",
        ".......kk.......",
        "................"]
sprite(PELT, {"k": FUR[4], "d": FUR[3], "m": FUR[2], "l": FUR[1], "c": CREAM[1]}).save("item/werewolf_pelt.png")

# Feral Lining Module: the exoskeleton modules' gunmetal cartridge (tools/gen_exo.py), with a paw print in pelt brown.
MODULE_BODY = ["................",
               "................",
               "..oooooooooooo..",
               "..offffffffffo..",
               "..ofkkkkkkkkfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofk......kfo..",
               "..ofkkkkkkkkfo..",
               "..offffffffffo..",
               "..oyoyoyoyoyoo..",
               "...y.y.y.y.y....",
               "................",
               "................"]
PAW = [".a..a.", "a....a", "..aa..", ".aaaa.", ".aaaa."]
rows = [list(r) for r in MODULE_BODY]
for sy, line in enumerate(PAW):
    for sx, ch in enumerate(line):
        rows[5 + sy][5 + sx] = "a" if ch == "a" else "k"


def hexrgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


sprite(["".join(r) for r in rows], {"o": hexrgb(0x1E2228), "f": hexrgb(0x5A616B), "k": hexrgb(0x0E1014), "y": hexrgb(0xC9A23A),
                                    "a": hexrgb(0xB07A4A)}).save("item/feral_lining_module.png")

# Spawn egg: the shared egg shape (as the other mod eggs use), dark fur with a cream belly band and amber eyes.
w, h, cart_egg = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
mask = [p[3] > 0 for p in cart_egg]
xs = [i % w for i in range(w * h) if mask[i]]
ys = [i // w for i in range(w * h) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
egg = Canvas(w, h)
for i in range(w * h):
    if not mask[i]:
        continue
    x, y = i % w, i // w
    outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = rgba(FUR[4])
    elif (x, y) in ((6, 6), (10, 6)):
        egg.px[i] = rgba(EYE)
    elif y == round(cy) + 3:
        egg.px[i] = rgba(ROBE[1])
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = rgba(FUR[1] if light > 5 else FUR[2] if light > 0 else FUR[3])
egg.save("item/werewolf_spawn_egg.png")

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


for name in ("werewolf_pelt", "feral_lining_module", "werewolf_spawn_egg"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "mymod:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


def drop(item, low, high, looting=1.0):
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}]}


write(os.path.join(DATA, "loot_table/entities/werewolf.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [drop("mymod:werewolf_pelt", 1, 2), drop("minecraft:bone", 0, 2)]),
    ("random_sequence", "mymod:entities/werewolf")]))


def advancement(name, ingredient, category="misc"):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shapeless(name, ingredients, result, count, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, ingredients[0], category)


# A pelt tans down to leather (and so into everything leather goes into: the hazmat suit, the pig flight harness, the
# servo arm backpack...); two of them line an exoskeleton, as the other modules are made.
shapeless("leather_from_werewolf_pelt", ["mymod:werewolf_pelt"], "minecraft:leather", 2)
shapeless("feral_lining_module", ["mymod:werewolf_pelt", "mymod:werewolf_pelt", "minecraft:leather", "mymod:computer_chip",
                                  "mymod:tech_scrap"], "mymod:feral_lining_module", 1, "equipment")


def tag_add(path, entry):
    """Adds an entry to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


# Silver and holy light bite it (it isn't undead: the sun doesn't burn it).
tag_add(os.path.join(DATA, "tags/entity_type/holy_vulnerable.json"), "mymod:werewolf")


def vanilla(names, pitch, **extra):
    return [OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)] + list(extra.items())) for n in names]


# Vanilla sounds, pitched down: there's no wolf howl in the game any more, so a big wolf's whine, slowed right down,
# stands in, carrying four times as far as most sounds.
SOUNDS = OrderedDict([
    ("werewolf_growl", OrderedDict([("subtitle", "subtitles.mymod.werewolf_growl"),
                                    ("sounds", vanilla(["mob/wolf/angry/growl%d" % i for i in (1, 2, 3)], 0.6))])),
    ("werewolf_howl", OrderedDict([("subtitle", "subtitles.mymod.werewolf_howl"),
                                   ("sounds", vanilla(["mob/wolf/big/whine", "mob/wolf/classic/whine"], 0.5, attenuation_distance=64))])),
    ("werewolf_hurt", OrderedDict([("subtitle", "subtitles.mymod.werewolf_hurt"),
                                   ("sounds", vanilla(["mob/wolf/big/hurt%d" % i for i in (1, 2, 3)], 0.65))])),
    ("werewolf_death", OrderedDict([("subtitle", "subtitles.mymod.werewolf_death"),
                                    ("sounds", vanilla(["mob/wolf/big/death"], 0.55))])),
    ("werewolf_hiss", OrderedDict([("subtitle", "subtitles.mymod.werewolf_hiss"),
                                   ("sounds", vanilla(["mob/cat/hiss%d" % i for i in (1, 2, 3)], 0.6))])),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.mymod.werewolf", "Werewolf"),
    ("item.mymod.werewolf_spawn_egg", "Werewolf Spawn Egg"),
    ("item.mymod.werewolf_pelt", "Werewolf Pelt"),
    ("item.mymod.feral_lining_module", "Feral Lining Module"),
    ("exo_module.mymod.feral_lining", "Feral Lining"),
    ("exo_module.mymod.feral_lining.description", "+10% speed and +1 attack damage"),
    ("item.mymod.holy_silver_ingot.cure", "Use on a cursed villager: lifts the werewolf curse"),
    ("item.mymod.crucifix.undead", "Only harms the undead and the unholy; triple damage to vampires, werewolves, the Schnozzle "
                                   "and skullcranes"),
    ("gamerule.mymod.werewolves", "Werewolves"),
    ("gamerule.mymod.werewolves.description", "Whether villagers can carry the werewolf curse, and whether cursed villagers "
                                              "turn into werewolves on full-moon nights."),
    ("subtitles.mymod.werewolf_growl", "Something growls"),
    ("subtitles.mymod.werewolf_howl", "Werewolf howls"),
    ("subtitles.mymod.werewolf_hurt", "Werewolf hurts"),
    ("subtitles.mymod.werewolf_death", "Werewolf dies"),
    ("subtitles.mymod.werewolf_hiss", "Something hisses"),
    ("message.mymod.werewolf.cured", "The curse lifts"),
    ("commands.mymod.werewolf.cursed", "%s now carries the werewolf curse"),
    ("commands.mymod.werewolf.cured", "Lifted the werewolf curse from %s"),
    ("commands.mymod.werewolf.is_werewolf", "%s is a werewolf"),
    ("commands.mymod.werewolf.is_cursed", "%s carries the werewolf curse"),
    ("commands.mymod.werewolf.not_cursed", "%s isn't cursed"),
    ("commands.mymod.werewolf.unrolled", "%s hasn't been checked for the curse yet"),
    ("commands.mymod.werewolf.transforming", "%s is changing"),
    ("commands.mymod.werewolf.reverted", "%s is a villager again"),
    ("commands.mymod.werewolf.no_target", "No villager or werewolf there (look at one, or name targets)"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "mymod", "client",
                                     "werewolf", "WerewolfModel.java"))


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
        lines.append(f"\t\tPartDefinition {var} = {('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", "
                     f"{builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t\t// BEGIN GENERATED by tools/gen_werewolf.py\n", "\t\t// END GENERATED\n"
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


def animate(pose, walk=0.0, walk_speed=0.0, attack=0.0, howl=0.0, aggressive=False, age=0.0, left_swipe=False):
    """Python twin of WerewolfModel.setupAnim, for the preview."""
    stride = math.cos(walk * 0.5)
    swing = stride * 0.8 * walk_speed
    bob = abs(math.sin(walk * 0.5)) * walk_speed
    hunt = 1.0 if aggressive else 0.0
    add(pose, "body", dz=0.08 * stride * walk_speed, pos=(0, 1.2 * bob + 2.0 * hunt, 0))
    add(pose, "chest", dz=-0.12 * stride * walk_speed, dx=0.2 * hunt)
    add(pose, "neck", dx=-0.3 * hunt)
    for side, s in (("right", 1), ("left", -1)):
        add(pose, side + "_leg", dx=s * swing - 0.3 * hunt, pos=(0, 2.0 * hunt, 0))
        add(pose, side + "_shin", dx=max(0.0, -s * stride) * 0.6 * walk_speed + 0.45 * hunt)
        add(pose, side + "_hock", dx=-0.15 * hunt)
        add(pose, side + "_arm", dx=-s * swing - 0.55 * hunt, dz=-s * 0.5 * hunt)
        add(pose, side + "_forearm", dx=-max(0.0, s * stride) * 0.4 * walk_speed - 0.35 * hunt)
        add(pose, side + "_ear", dx=0.3 * hunt)
    add(pose, "jaw", dx=0.15 * hunt)
    add(pose, "tail", dx=0.08 * math.sin(age * 0.08) + 0.3 * walk_speed + 0.4 * hunt, dy=0.2 * math.sin(age * 0.06))
    phase = age % 61.0
    if phase < 5.0:
        p = math.sin(phase / 5.0 * math.pi)
        add(pose, "head", dz=0.3 * p * math.sin(age * 2.3), dy=0.2 * p * math.cos(age * 1.7))
        add(pose, "right_ear", dz=0.3 * p)
    if attack > 0.0:
        rake = math.sin(attack * math.pi)
        raise_ = math.sin(math.sqrt(attack) * math.pi)
        arm, s = ("left", -1) if left_swipe else ("right", 1)
        add(pose, arm + "_arm", dx=-2.4 * raise_ + 0.9 * rake, dy=0.5 * rake * s, dz=0.5 * raise_ * s)
        add(pose, arm + "_forearm", dx=-0.6 * raise_)
        add(pose, "chest", dx=0.25 * rake)
        add(pose, "neck", dx=0.2 * rake)
        add(pose, "jaw", dx=0.9 * snap(attack))
    if howl > 0.0:
        add(pose, "body", dx=-0.5 * howl, pos=(0, -1.0 * howl, 0))
        add(pose, "chest", dx=-0.45 * howl)
        add(pose, "neck", dx=-0.55 * howl)
        add(pose, "head", dx=-0.65 * howl)
        pose["head"][1] = (pose["head"][1][0], pose["head"][1][1] * (1 - howl), pose["head"][1][2])
        add(pose, "jaw", dx=0.85 * howl)
        for side, s in (("right", 1), ("left", -1)):
            add(pose, side + "_arm", dx=-0.5 * howl, dz=s * 0.9 * howl)
            add(pose, side + "_forearm", dx=-0.5 * howl)
            add(pose, side + "_ear", dx=0.4 * howl)
            add(pose, side + "_leg", dx=0.25 * howl)
            add(pose, side + "_shin", dx=-0.2 * howl)
        add(pose, "tail", dx=-0.3 * howl)
    return pose


def snap(attack):
    """The jaw through a swipe: gaping as the arm winds up, then snapped shut as it rakes down."""
    return attack / 0.45 if attack < 0.45 else max(0.0, 1.0 - (attack - 0.45) / 0.15)


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
        print("z-fight: werewolf", h_)
    raise SystemExit(f"werewolf: {len(_hits)} coplanar overlapping face pairs")


def extents(pose):
    pts = [p for verts, _ in quads(pose) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
print(f"werewolf: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f}, "
      f"feet y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


def render(pose, view, scale=8, width=300, height=380, light=(0.35, -0.8, -0.5), focus=None):
    """Orthographic render. view: 'front' (looking at its face), 'side' (from its right), 'three' (three-quarters) or
    'back'. focus: a model point to centre (default: feet at the bottom)."""
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs = [p[0] * ca - p[2] * sa for verts, _ in quads(pose) for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs) + max(xs)) / 2 * scale, height - 20, 24.0
    # The ground line.
    gy = int(oy + (24.0 - ly) * scale)
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
                    p = tuple(A[k] + e1[k] * s + e2[k] * t for k in range(3))
                    x = p[0] * ca - p[2] * sa
                    z = p[0] * sa + p[2] * ca
                    corners.append((ox + x * scale, oy + (p[1] - ly) * scale, z))
                depth = sum(c[2] for c in corners) / 4
                glowing = col[:3] in (EYE, EYE_HOT)
                k_ = 1.0 if glowing else shade
                c = (int(col[0] * k_), int(col[1] * k_), int(col[2] * k_), 255)
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
    head = (0.0, -5.0, -12.0)
    snarl = animate(rest_pose(), attack=0.4)
    views = [render(rest_pose(), "front", scale=7), render(rest_pose(), "side", scale=7), render(rest_pose(), "three", scale=7),
             render(animate(rest_pose(), walk=1.2, walk_speed=0.9, aggressive=True, attack=0.4), "three", scale=7),
             render(animate(rest_pose(), howl=1.0), "side", scale=7),
             render(animate(rest_pose(), walk=2.0, walk_speed=0.6, aggressive=True), "three", scale=7)]
    # The head up close: where it sits at rest, found from the snout.
    snout_pts = [p for verts, uv, label, n in quads(snarl, labels=True) if label.startswith("head.") for p in verts]
    centre = tuple(sum(p[i] for p in snout_pts) / len(snout_pts) for i in range(3))
    views += [render(snarl, "three", scale=22, focus=centre),
              render(snarl, "side", scale=22, focus=centre),
              render(rest_pose(), "front", scale=22, focus=(centre[0], centre[1] - 2.5, centre[2]))]
    out, bw, bh = board(views, 300, 380, 3)
    png(os.path.join(PREVIEW, "preview_werewolf_v2.png"), bw, bh, out)
    s = 4
    big = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
           for y in range(TEX_H * s) for x in range(TEX_W * s)]
    for y in range(TEX_H * s):
        for x in range(TEX_W * s):
            c = TEXTURE.get(x // s, y // s)
            if c[3]:
                big[y * TEX_W * s + x] = c
    png(os.path.join(PREVIEW, "preview_werewolf_texture.png"), TEX_W * s, TEX_H * s, big)
    print("preview written to", PREVIEW)
print("done")
