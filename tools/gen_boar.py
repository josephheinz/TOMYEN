"""Builds the Wild Boar: its model (the cube layout in PARTS, written into client/boar/BoarModel.java between the
GENERATED markers), its entity texture and the striped piglet texture on the same layout, its spawn egg, and the items
made from it: Raw and Cooked Boar, the Boar Tusk, the Tusk Spear (a vanilla spear, GUI and in-hand sprites), the Tusk
Harpoon (Harpoon Gun ammo, with the 3D model the gun's loading animation draws) and the Tusk Hook (a fishing rod part).
On the data side it writes the boar's loot table, the table of what it digs up rooting, the recipes (with recipe-book
advancements) and the item tags (boar food, the tusk spear's repair material, and #minecraft:spears so spear
enchantments take). Its sounds (vanilla pig, hoglin, goat and ravager sounds, re-pitched) and lang entries are merged
into sounds.json and en_us.json.

The coat is painted in smooth ramps: a grizzled dark brown-grey, darker down the legs, with a near-black bristle mane
along the spine and onto the crown, the pale grey cheek patches wild boar have, a dark disc snout, small dark eyes under
a heavy brow and ivory tusks stained at the root. The side faces carry a deliberate bristle hatch (every third column,
staggered), not noise. Piglets are ginger with cream and dark stripes running nose to tail. No noise, no dithering; a
rerun gives the same files.

Meat is vanilla's porkchop art recoloured to a deeper game red with a dark hide rind; the Tusk Spear is vanilla's stone
spear with its blade swapped for a curved tusk lashed on with leather.

The model is checked for z-fighting at rest (tools/coplanar.py), and its extents are printed (feet on y 24).

With --preview DIR it also renders, into DIR, preview_boar.png (the sow from three sides, the tusker, a piglet, and the
walk, rooting, pawing, charging, stunned and gore poses, and the head up close) and preview_boar_texture.png, using the
same cubes, pivots and UV layout as the Java model and a Python twin of its animation; and preview_boar_items.png (the
item sprites, the Tusk Spear in first person and in the GUI next to vanilla's stone spear, and the tusk harpoon model).

Usage: python3 tools/gen_boar.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, struct, sys, zipfile, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
VANILLA_DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "minecraft"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
_argv = sys.argv
sys.argv = sys.argv[:2]
from gen_longswords import client_jar, read_png as read_png_bytes
JAR = zipfile.ZipFile(client_jar())
sys.argv = _argv


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def vanilla_png(name):
    """(w, h, pixels) of a vanilla texture, e.g. 'item/porkchop'."""
    return read_png_bytes(JAR.read(f"assets/minecraft/textures/{name}.png"))


def mod_png(rel):
    with open(os.path.join(TEX, rel), "rb") as f:
        return read_png_bytes(f.read())


CLEAR = (0, 0, 0, 0)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


class Canvas:
    def __init__(self, w, h, px=None):
        self.w, self.h = w, h
        self.px = list(px) if px else [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c if len(c) == 4 else rgba(c)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(cols, t):
    """A smooth colour from a list (light to dark), t in 0..1."""
    t = max(0.0, min(1.0, t)) * (len(cols) - 1)
    i = min(int(t), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], t - i)


# ---------------------------------------------------------------------------------------------- palette
# The coat, light to dark: a grizzled grey-brown.
COAT = [(142, 122, 100), (116, 98, 80), (92, 77, 64), (70, 59, 50), (52, 44, 39), (36, 31, 28)]
# The bristle mane: near black, its tips catching the light grey-brown.
MANE = [(66, 56, 49), (46, 40, 36), (30, 27, 25)]
MANE_TIP = (124, 110, 96)
# The pale cheek patches and chin.
CHEEK = [(176, 166, 150), (148, 138, 124), (120, 110, 98)]
SNOUT = [(96, 82, 76), (78, 66, 62), (60, 51, 48)]
DISC = [(108, 86, 84), (86, 68, 67)]
NOSTRIL = (36, 26, 26)
MOUTH = (58, 40, 40)
EYE = (20, 16, 14)
EYE_GLINT = (92, 74, 60)
INNER_EAR = (92, 70, 66)
# Tusks: ivory, stained toward the root.
TUSK = [(248, 243, 228), (230, 221, 196), (204, 190, 158), (160, 136, 98)]
HOOF = [(60, 54, 50), (38, 34, 33)]
# Piglets: ginger with cream and dark stripes.
GINGER = [(206, 160, 110), (182, 136, 90), (156, 114, 74), (128, 92, 60), (100, 72, 48)]
STRIPE_LIGHT = (230, 206, 160)
STRIPE_DARK = (104, 74, 50)
PIG_SNOUT = [(176, 132, 118), (148, 108, 98)]

TEX_W, TEX_H = 128, 64


def faces(u, v, w, h, d):
    """A cube's faces in the texture, (u, v, width, height), as the game lays them out. 'top' and 'bottom' are as seen
    in the world (the model is upside down); in both the front edge is the last row. In 'right' (the cube's -x side,
    the creature's right) the front edge is the last column, in 'left' the first."""
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
                canvas.set(fu + x, fv + y, c if c is not None else CLEAR)


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size, mirrored). Mirrored cubes
# share their texture with the unmirrored one of the same key. Model space is the game's: y down, the front toward
# -z, feet at y 24. This is the one place the shape is defined: it's written into client/boar/BoarModel.java (between
# the GENERATED markers), painted here, and drawn by the preview.
#
# A wild boar: a deep, heavy forequarter under a high hump, the back sloping away to narrow hindquarters (the whole
# torso is tipped down toward the tail), bulging shoulders and hams, a brisket hanging low between the front legs. A
# crest of bristles runs from the crown down the spine, tallest over the shoulders. The head is a long wedge: a broad
# skull with a ruff behind it and a heavy brow over small eyes, pale cheek jowls with bristle tufts, a long snout ending
# in a flat disc, a lower jaw that opens, and the lower tusks curving up and back out of it. Short, pointed, bristly
# ears; a thin tail with a tassel. Short legs, thick above and thin below, on small dark hooves.
BODY_TILT = -0.2
# The whole animal sits this far back on its hitbox, so the long head doesn't poke far out the front of it.
SHIFT = 3.0
SIDES = (("right", -1, False), ("left", 1, True))


def parts():
    p = [
        ("body", None, (0.0, 13.0, SHIFT), (BODY_TILT, 0.0, 0.0), [
            ("chest", (-5.0, -7.0, -9.0), (10, 11, 9), False),
            ("hump", (-4.0, -9.5, -8.0), (8, 3, 7), False),
            ("barrel", (-4.5, -6.0, -1.0), (9, 9, 6), False),
            ("rump", (-3.5, -5.0, 4.0), (7, 7, 5), False),
            ("brisket", (-3.5, 3.5, -8.5), (7, 2, 6), False),
            ("belly", (-3.0, 2.5, -3.0), (6, 2, 7), False),
            ("shoulder", (-5.75, -5.5, -8.0), (1, 7, 6), False),
            ("shoulder", (4.75, -5.5, -8.0), (1, 7, 6), True),
            ("ham", (-5.0, -3.5, 4.5), (2, 6, 4), False),
            ("ham", (3.0, -3.5, 4.5), (2, 6, 4), True)]),
        # The bristle crest, its own part so it can rise when the boar's hackles go up: up the neck, tallest over the
        # withers, sloping away down the back.
        ("mane", "body", (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), [
            ("mane_neck", (-1.0, -10.0, -10.0), (2, 3, 3), False),
            ("mane_withers", (-1.5, -12.0, -7.5), (3, 3, 5), False),
            ("mane_mid", (-1.0, -8.5, -3.0), (2, 3, 6), False),
            ("mane_back", (-0.5, -7.0, 2.5), (1, 2, 5), False)]),
        ("tail", "body", (0.0, -4.5, 9.0), (0.3, 0.0, 0.0), [
            ("tail", (-0.5, 0.0, -0.5), (1, 6, 1), False),
            ("tail_tuft", (-1.0, 5.0, -1.0), (2, 3, 2), False)]),
        ("head", "body", (0.0, -1.5, -9.0), (0.3, 0.0, 0.0), [
            ("ruff", (-4.0, -4.0, -1.0), (8, 8, 3), False),
            ("skull", (-3.5, -4.5, -6.0), (7, 7, 6), False),
            ("crown", (-1.0, -6.0, -5.5), (2, 2, 5), False),
            ("brow", (-4.0, -4.0, -7.0), (8, 1, 1), False),
            ("jowls", (-4.0, -1.0, -5.5), (8, 4, 4), False),
            ("cheek_tuft", (-4.75, 0.5, -4.0), (1, 3, 3), False),
            ("cheek_tuft", (3.75, 0.5, -4.0), (1, 3, 3), True),
            ("bridge", (-2.5, -3.5, -8.75), (5, 2, 3), False),
            ("snout", (-2.0, -2.5, -12.0), (4, 4, 6), False),
            ("disc", (-2.5, -2.75, -12.5), (5, 4, 1), False)]),
        ("jaw", "head", (0.0, 1.5, -6.0), (0.0, 0.0, 0.0), [
            ("jaw", (-1.5, 0.0, -5.0), (3, 1, 5), False)]),
    ]
    for side, s, mirror in SIDES:
        p += [
            # The lower tusks: out of the jaw's sides, leaning forward and out, the tips curving up and back.
            (side + "_tusk", "jaw", (s * 1.6, 0.2, -3.5), (0.25, 0.0, s * 0.5), [
                ("tusk_base", (-0.5, -3.0, -0.5), (1, 3, 1), mirror)]),
            (side + "_tusk_tip", side + "_tusk", (0.0, -3.0, 0.0), (-0.95, 0.0, -s * 0.25), [
                ("tusk_tip", (-0.5, -2.0, -0.5), (1, 2, 1), mirror)]),
            (side + "_ear", "head", (s * 2.5, -4.5, -1.5), (-0.35, 0.0, s * 0.55), [
                ("ear", (-1.0, -3.0, -0.5), (2, 3, 1), mirror)]),
            (side + "_front_leg", None, (s * 3.0, 15.0, SHIFT - 6.0), (0.0, 0.0, 0.0), [
                ("forearm", (-1.5, -1.0, -1.5), (3, 6, 3), mirror)]),
            (side + "_front_shin", side + "_front_leg", (0.0, 5.0, 0.0), (0.0, 0.0, 0.0), [
                ("shin", (-1.0, 0.0, -1.0), (2, 3, 2), mirror),
                ("hoof", (-1.0, 3.0, -2.0), (2, 1, 3), mirror)]),
            (side + "_back_leg", None, (s * 2.75, 15.0, SHIFT + 6.0), (0.0, 0.0, 0.0), [
                ("thigh", (-1.5, -2.0, -2.5), (3, 7, 5), mirror)]),
            (side + "_back_shin", side + "_back_leg", (0.0, 5.0, 0.5), (0.0, 0.0, 0.0), [
                ("shin", (-1.0, 0.0, -1.0), (2, 3, 2), mirror),
                ("hoof", (-1.0, 3.0, -2.0), (2, 1, 3), mirror)]),
        ]
    return p


PARTS = parts()
TREE = {}
for _name, _parent, *_ in PARTS:
    TREE.setdefault(_parent or "root", []).append(_name)
PART_CUBES = {name: cubes for name, _, _, _, cubes in PARTS}
TUSK_PARTS = ("right_tusk", "left_tusk")


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
    assert y + shelf <= TEX_H, f"texture too small ({y + shelf} rows)"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


def cube_count():
    return sum(len(c) for _, _, _, _, c in PARTS)


# ---------------------------------------------------------------------------------------------- painting
def hatch(x, y):
    """The bristle hatch on a coat face: every third column, staggered every two rows, a step darker."""
    return 0.07 if (x + y // 2) % 3 == 0 else 0.0


def paint_entity(piglet=False):
    tex = Canvas(TEX_W, TEX_H)
    COATS = GINGER if piglet else COAT
    P = lambda key, painters: paint(tex, *CUBES[key], painters)

    def coat(t):
        return ramp(COATS, t)

    def side(top=0.1, bottom=0.75, bristle=True):
        def f(x, y, w, h):
            return coat(top + (bottom - top) * y / max(1, h - 1) + (hatch(x, y) if bristle and not piglet else 0.0))
        return f

    def flat(t):
        return lambda x, y, w, h: coat(t)

    def striped(base, rows=True, offset=0):
        """Piglet stripes along the body: on the sides, light and dark bands run lengthwise (rows); on the tops, the
        same bands seen from above (columns)."""
        def f(x, y, w, h):
            if not piglet:
                return base(x, y, w, h)
            k = ((y if rows else x) + offset) % 4
            if k == 0:
                return STRIPE_LIGHT
            if k == 2:
                return lerp(STRIPE_DARK, base(x, y, w, h), 0.25)
            return base(x, y, w, h)
        return f

    def spine_top(x, y, w, h):
        # Darker down the middle, where the crest sits; lighter on the shoulders of the face.
        mid = abs(x - (w - 1) / 2) / max(1.0, (w - 1) / 2)
        return coat(0.45 - 0.35 * mid)

    def body_cube(key, top=0.1, bottom=0.75, front=None, offset=0):
        P(key, {"top": striped(spine_top, rows=False, offset=offset + 1), "bottom": flat(0.9),
                "right": striped(side(top, bottom), offset=offset), "left": striped(side(top, bottom), offset=offset),
                "front": front or side(top + 0.05, bottom), "back": side(top + 0.1, bottom + 0.05)})

    body_cube("chest", 0.05, 0.8)
    body_cube("hump", 0.0, 0.25)
    body_cube("barrel", 0.1, 0.8, offset=1)
    body_cube("rump", 0.12, 0.8, offset=2)
    P("brisket", {k: side(0.65, 0.95, False) for k in ("front", "back", "right", "left")} | {"bottom": flat(0.95),
                                                                                            "top": flat(0.7)})
    P("belly", {k: side(0.7, 0.95, False) for k in ("front", "back", "right", "left")} | {"bottom": flat(0.95),
                                                                                         "top": flat(0.7)})
    # Shoulders and hams: heavy bulges, lit on top.
    P("shoulder", {"top": flat(0.05), "bottom": flat(0.85), "right": striped(side(0.0, 0.7), offset=1),
                   "left": side(0.3, 0.8), "front": side(0.1, 0.75), "back": side(0.2, 0.8)})
    P("ham", {"top": flat(0.1), "bottom": flat(0.85), "right": striped(side(0.05, 0.7), offset=2),
              "left": side(0.3, 0.8), "front": side(0.15, 0.75), "back": side(0.15, 0.75)})

    # The bristle crest: near black, ragged along its top edge (an irregular run of tufts, not a comb), the tips
    # catching the light.
    TUFTS = (1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 0, 0, 1)

    def mane_side(x, y, w, h):
        if y == 0 and not TUFTS[(x * 5 + w) % len(TUFTS)]:
            return None
        if piglet:
            return lerp(STRIPE_DARK, GINGER[3], 0.3 * y / max(1, h - 1))
        if y == 0:
            return MANE_TIP
        return ramp(MANE, (y - 1) / max(1, h - 2))

    def mane_top(x, y, w, h):
        if piglet:
            return STRIPE_DARK
        return lerp(MANE_TIP, MANE[0], 0.5) if TUFTS[(x * 3 + y * 5) % len(TUFTS)] else MANE[1]
    for key in ("mane_neck", "mane_withers", "mane_mid", "mane_back"):
        P(key, {"top": mane_top, "bottom": lambda x, y, w, h: MANE[2], "right": mane_side, "left": mane_side,
                "front": mane_side, "back": mane_side})
    P("crown", {"top": mane_top, "bottom": lambda x, y, w, h: MANE[2], "right": mane_side, "left": mane_side,
                "front": mane_side, "back": mane_side})

    # Tail: thin and dark, with a tassel of bristles ragged at the end.
    dark = lambda x, y, w, h: GINGER[4] if piglet else MANE[1]
    P("tail", {k: dark for k in ("front", "back", "right", "left", "top", "bottom")})

    def tassel(x, y, w, h):
        if y == h - 1 and x % 2 == 1:
            return None
        return GINGER[4] if piglet else ramp(MANE, y / max(1, h - 1))
    P("tail_tuft", {"front": tassel, "back": tassel, "right": tassel, "left": tassel, "top": dark,
                    "bottom": lambda x, y, w, h: None if (x + y) % 2 else (GINGER[4] if piglet else MANE[2])})

    # Head. The ruff behind the skull: bristly, ragged at its top edge.
    def ruff_side(x, y, w, h):
        if y == 0 and x % 2 == 1:
            return None
        return coat(0.3 + 0.5 * y / max(1, h - 1) + (0 if piglet else hatch(x, y)))
    P("ruff", {"top": lambda x, y, w, h: None if (x + y) % 3 == 0 else coat(0.35), "bottom": flat(0.85),
               "right": ruff_side, "left": ruff_side, "front": ruff_side, "back": ruff_side})

    # Skull: a dark crown, small dark eyes set in the sides just under the brow, near the front.
    def skull_side(front_last):
        def f(x, y, w, h):
            ex = w - 2 if front_last else 1
            if y == 1 and x == ex:
                return EYE
            if y == 1 and abs(x - ex) == 1:
                return EYE_GLINT if not piglet else GINGER[3]
            if y == 0 and x == ex:
                return coat(0.85)    # the brow's shadow over the eye
            return coat(0.25 + 0.5 * y / max(1, h - 1) + (0 if piglet else hatch(x, y) * 0.5))
        return f
    P("skull", {"top": lambda x, y, w, h: coat(0.45 if abs(x - (w - 1) / 2) <= 1 else 0.2), "bottom": flat(0.8),
                "right": skull_side(True), "left": skull_side(False), "front": side(0.3, 0.7, False), "back": flat(0.6)})
    P("brow", {"top": flat(0.25), "bottom": flat(0.95), "front": lambda x, y, w, h: coat(0.55),
               "right": flat(0.5), "left": flat(0.5), "back": flat(0.6)})

    # Jowls: the coat above, pale grey cheeks below (ginger and cream on a piglet).
    PALE = [STRIPE_LIGHT, GINGER[0], GINGER[1]] if piglet else CHEEK

    def jowl_side(x, y, w, h):
        if y == 0:
            return coat(0.45)
        return ramp(PALE, (y - 1) / max(1, h - 2))
    P("jowls", {"top": flat(0.35), "bottom": lambda x, y, w, h: PALE[2], "right": jowl_side, "left": jowl_side,
                "front": jowl_side, "back": jowl_side})

    def tuft(x, y, w, h):
        if (x + y) % 2 == 1 and (y == 0 or y == h - 1 or x == 0 or x == w - 1):
            return None
        return ramp(PALE, 0.2 + 0.6 * y / max(1, h - 1))
    P("cheek_tuft", {"top": tuft, "bottom": tuft, "right": tuft, "left": tuft, "front": tuft, "back": tuft})

    # The snout: darker grey-brown, lighter along the upper lip; the bridge blends coat into it.
    SN = PIG_SNOUT + [PIG_SNOUT[1]] if piglet else SNOUT

    def snout_side(x, y, w, h):
        if y == h - 1:
            return MOUTH
        return ramp(SN, 0.2 + 0.6 * y / max(1, h - 2))
    P("snout", {"top": lambda x, y, w, h: ramp(SN, 0.15 + 0.1 * abs(x - (w - 1) / 2)), "bottom": lambda x, y, w, h: MOUTH,
                "right": snout_side, "left": snout_side, "front": snout_side, "back": snout_side})

    def bridge_side(x, y, w, h):
        return lerp(coat(0.3 + 0.3 * y), SN[0], 0.5)
    P("bridge", {"top": lambda x, y, w, h: lerp(coat(0.3), SN[0], y / max(1, h - 1)), "bottom": lambda x, y, w, h: SN[1],
                 "right": bridge_side, "left": bridge_side, "front": bridge_side, "back": bridge_side})

    # The disc: flat, dark pinkish grey, two nostrils.
    DC = [PIG_SNOUT[0], PIG_SNOUT[1]] if piglet else DISC

    def disc_front(x, y, w, h):
        if x in (1, 3) and y in (1, 2):
            return NOSTRIL
        edge = x in (0, w - 1) or y in (0, h - 1)
        return DC[1] if edge else DC[0]
    P("disc", {"front": disc_front, "back": lambda x, y, w, h: DC[1], "top": lambda x, y, w, h: DC[0],
               "bottom": lambda x, y, w, h: DC[1], "right": lambda x, y, w, h: DC[1], "left": lambda x, y, w, h: DC[1]})

    # Lower jaw: a dark lip line on top, a pale chin.
    def jaw_side(x, y, w, h):
        return ramp(PALE, 0.6)
    P("jaw", {"top": lambda x, y, w, h: MOUTH, "bottom": lambda x, y, w, h: PALE[1], "right": jaw_side, "left": jaw_side,
              "front": jaw_side, "back": lambda x, y, w, h: MOUTH})

    # Tusks: ivory, stained toward the root; the tip piece lighter.
    def tusk_len(root_at_bottom, start, end):
        def f(x, y, w, h):
            t = (y / max(1, h - 1)) if root_at_bottom else (1 - y / max(1, h - 1))
            return ramp(TUSK, start + (end - start) * t)
        return f
    base = tusk_len(True, 0.25, 1.0)
    P("tusk_base", {"front": base, "back": base, "right": base, "left": base, "top": lambda x, y, w, h: TUSK[1],
                    "bottom": lambda x, y, w, h: TUSK[3]})
    tip = tusk_len(True, 0.0, 0.3)
    P("tusk_tip", {"front": tip, "back": tip, "right": tip, "left": tip, "top": lambda x, y, w, h: TUSK[0],
                   "bottom": lambda x, y, w, h: TUSK[1]})

    # Ears: pointed (the top row is one texel wide, toward the outer edge), dark and bristly, a dull inside.
    def ear_face(inner):
        def f(x, y, w, h):
            if y == 0 and x != 0:
                return None
            if inner and y >= 1 and x == 1:
                return INNER_EAR if not piglet else PIG_SNOUT[1]
            return coat(0.55 + 0.1 * y) if not piglet else GINGER[3]
        return f
    P("ear", {"front": ear_face(True), "back": ear_face(False), "right": lambda x, y, w, h: None if y == 0 else coat(0.6),
              "left": lambda x, y, w, h: None if y == 0 else coat(0.6), "top": lambda x, y, w, h: coat(0.7) if x == 0 else None,
              "bottom": flat(0.6)})

    # Legs: the coat darkening down the forearm and thigh, dark shins, small dark hooves with a cleft down the front.
    P("forearm", {k: side(0.35, 0.85) for k in ("front", "back", "right", "left")} | {"top": flat(0.4), "bottom": flat(0.9)})
    P("thigh", {k: side(0.2, 0.85) for k in ("front", "back", "right", "left")} | {"top": flat(0.25), "bottom": flat(0.9)})
    shin = lambda x, y, w, h: GINGER[4] if piglet else coat(0.9 + 0.1 * y / max(1, h - 1))
    P("shin", {k: shin for k in ("front", "back", "right", "left", "top", "bottom")})

    def hoof_front(x, y, w, h):
        return HOOF[1]
    P("hoof", {"front": hoof_front, "back": lambda x, y, w, h: HOOF[0], "right": lambda x, y, w, h: HOOF[0],
               "left": lambda x, y, w, h: HOOF[0], "bottom": lambda x, y, w, h: HOOF[1],
               "top": lambda x, y, w, h: HOOF[1] if x == (w - 1) // 2 and y >= h - 2 else HOOF[0]})
    return tex


TEXTURE = paint_entity()
TEXTURE.save("entity/boar/boar.png")
PIGLET_TEXTURE = paint_entity(piglet=True)
PIGLET_TEXTURE.save("entity/boar/boar_piglet.png")


# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette, w=16):
    assert all(len(r) == w for r in rows), [len(r) for r in rows]
    c = Canvas(w, len(rows))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


TUSK_OUT = (112, 96, 72)
TUSK_PAL = {"o": TUSK_OUT, "w": TUSK[0], "i": TUSK[1], "s": TUSK[2], "r": TUSK[3], "d": (110, 88, 60)}

# Boar Tusk: a big curved lower tusk, tip at the top right, the stained root at the bottom left.
BOAR_TUSK = ["................",
             ".............o..",
             "............owo.",
             "............owo.",
             "...........owio.",
             "..........owiso.",
             ".........owiiso.",
             "........owiiso..",
             ".......owiisso..",
             "......owiisso...",
             ".....oiiisso....",
             "....orsissoo....",
             "...orrsssoo.....",
             "..odrrsoo.......",
             "..oddoo.........",
             "...oo..........."]
sprite(BOAR_TUSK, TUSK_PAL).save("item/boar_tusk.png")


def meat(name, vanilla, dark_ramp, rind):
    """A vanilla porkchop recoloured to game meat (its own ramp by lightness) with a bristly dark hide rind along its
    lower-right edge, so it doesn't read as pork."""
    w, h, px = vanilla_png("item/" + vanilla)
    out = Canvas(w, h)
    lum = lambda c: (c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114) / 255.0
    lums = [lum(c) for c in px if c[3]]
    lo, hi = min(lums), max(lums)
    for i, c in enumerate(px):
        if not c[3]:
            continue
        out.px[i] = rgba(ramp(dark_ramp, 1.0 - (lum(c) - lo) / (hi - lo)))
    # The rind: every opaque pixel on the outline facing down or right becomes hide.
    mask = [c[3] > 0 for c in px]
    for y in range(h):
        for x in range(w):
            if not mask[y * w + x]:
                continue
            down = y + 1 >= h or not mask[(y + 1) * w + x]
            right = x + 1 >= w or not mask[y * w + x + 1]
            if (down or right) and x + y >= 15:
                out.set(x, y, rgba(rind[0] if (x + y) % 3 else rind[1]))
    out.save("item/" + name + ".png")


meat("raw_boar", "porkchop", [(244, 214, 200), (226, 150, 140), (196, 84, 84), (162, 50, 58), (120, 30, 40), (78, 20, 28)],
     [(58, 44, 38), (92, 76, 64)])
meat("cooked_boar", "cooked_porkchop", [(232, 204, 150), (196, 150, 96), (160, 108, 60), (122, 76, 40), (86, 50, 26),
                                        (56, 32, 18)], [(40, 26, 18), (74, 52, 34)])

# Tusk Spear: vanilla's stone spear (GUI and in-hand sprites) with its stone blade cleared and a curved tusk lashed on
# with leather in its place. The tusk below is for the in-hand sprite (tip at the top left); the GUI one is its mirror.
LASH = {"l": (103, 41, 11), "L": (163, 75, 34)}
SPEAR_HEAD = ["oo.......",
              "owo......",
              ".owio....",
              ".oiisoo..",
              "..oisssoo",
              "..osissro",
              "...oossLl",
              "....orLlL",
              ".....oolL"]


def tusk_spear(name, vanilla, mirror):
    w, h, px = vanilla_png("item/" + vanilla)
    out = Canvas(w, h, px)
    # Clear the stone blade: every grey pixel.
    for i, c in enumerate(px):
        if c[3] and abs(c[0] - c[1]) < 8 and abs(c[1] - c[2]) < 8:
            out.px[i] = CLEAR
    pal = TUSK_PAL | LASH
    for y, row in enumerate(SPEAR_HEAD):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            out.set(w - 1 - x if mirror else x, y, rgba(pal[ch]))
    out.save("item/" + name + ".png")


tusk_spear("tusk_spear", "stone_spear", True)
tusk_spear("tusk_spear_in_hand", "stone_spear_in_hand", False)

WOOD = {"k": (137, 103, 39), "j": (104, 78, 30), "h": (73, 54, 21), "n": (40, 30, 11)}
# Tusk Harpoon: a wooden shaft (the steel harpoon's layout), a tusk point lashed on, a line ring at the butt.
TUSK_HARPOON = ["................",
                "..............o.",
                ".............owo",
                "............oiso",
                "...........oisro",
                "..........LlLoo.",
                ".........hklL...",
                "........hkj.....",
                ".......hkj......",
                "......hkj.......",
                ".....hkj........",
                "....hkj.........",
                "...hkj..........",
                ".DDj............",
                ".DD.............",
                "................"]
sprite(TUSK_HARPOON, TUSK_PAL | LASH | WOOD | {"D": (22, 23, 26)}).save("item/tusk_harpoon.png")

# Tusk Hook: a hook carved from a tusk, with its line lashed round the shank.
TUSK_HOOK = ["................",
             "...........LL...",
             "..........LllL..",
             "...........oo...",
             "...........ow...",
             "...........oi...",
             "...........oi...",
             "...o.......oi...",
             "...wo......oi...",
             "...oio.....oi...",
             "...ois.....os...",
             "....ois...ois...",
             ".....oissssr....",
             "......oooor.....",
             "................",
             "................"]
sprite(TUSK_HOOK, TUSK_PAL | LASH).save("item/tusk_hook.png")

# Spawn egg: the shared egg shape (as the other mod eggs use): the grizzled coat with a dark crest down the back and a
# pair of ivory tusks.
_, _, cart_egg = mod_png("item/shopping_cart_spawn_egg.png")
EGG_W = 16
mask = [p[3] > 0 for p in cart_egg]
xs = [i % EGG_W for i in range(len(mask)) if mask[i]]
ys = [i // EGG_W for i in range(len(mask)) if mask[i]]
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
egg = Canvas(EGG_W, len(mask) // EGG_W)
for i in range(len(mask)):
    if not mask[i]:
        continue
    x, y = i % EGG_W, i // EGG_W
    outline = not all(0 <= x + dx < EGG_W and 0 <= y + dy < egg.h and mask[(y + dy) * EGG_W + x + dx]
                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if outline:
        egg.px[i] = rgba(COAT[5])
    elif (x, y) in ((7, 10), (8, 10), (7, 11), (8, 11)):
        egg.px[i] = rgba(DISC[0] if y == 10 else DISC[1])     # the snout's disc
    elif (x, y) in ((5, 10), (10, 10), (5, 9), (10, 9)):
        egg.px[i] = rgba(TUSK[0] if y == 9 else TUSK[2])     # tusks either side of it
    elif (x, y) in ((6, 7), (9, 7)):
        egg.px[i] = rgba(EYE)
    elif x in (7, 8) and y <= 5:
        egg.px[i] = rgba(MANE[1])                             # the crest
    else:
        light = (cx - x) + (cy - y)
        egg.px[i] = rgba(COAT[0] if light > 5 else COAT[1] if light > 1 else COAT[2] if light > -4 else COAT[3])
egg.save("item/boar_spawn_egg.png")

# The tusk harpoon in 3D, for the Harpoon Gun's loading animation (as harpoon_spear_3d): a thin wooden shaft made of
# crossed slabs, a leather lashing, and a tusk point. Its own little texture of swatches.
H3D = Canvas(16, 16)
for y in range(16):
    for x in range(16):
        if y < 4:
            H3D.set(x, y, rgba(WOOD["k"] if x < 8 else WOOD["j"]))       # wood: lit / shaded
        elif y < 8:
            H3D.set(x, y, rgba(TUSK[0] if x < 8 else TUSK[2]))         # ivory: lit / shaded
        elif y < 12:
            H3D.set(x, y, rgba(LASH["L"] if x < 8 else LASH["l"]))     # leather lashing
        else:
            H3D.set(x, y, rgba(TUSK[3]))                                # the stained root
H3D.save("item/tusk_harpoon_3d.png")
SWATCH = {"wood": (0.5, 0.5, 3.5, 3.5), "wood_dark": (8.5, 0.5, 11.5, 3.5), "ivory": (0.5, 4.5, 3.5, 7.5),
          "ivory_dark": (8.5, 4.5, 11.5, 7.5), "lash": (0.5, 8.5, 3.5, 11.5), "lash_dark": (8.5, 8.5, 11.5, 11.5),
          "root": (0.5, 12.5, 3.5, 15.5)}


def slab(name, frm, to, lit, dark, rotation=None, faces_=("north", "east", "south", "west", "up", "down")):
    e = OrderedDict([("name", name), ("from", list(frm)), ("to", list(to))])
    if rotation:
        e["rotation"] = rotation
    e["faces"] = OrderedDict((f, {"uv": list(SWATCH[lit if f in ("up", "east", "north") else dark]), "texture": "#tusk"})
                             for f in faces_)
    return e


def harpoon_elements():
    out = []
    # The shaft: two crossed slabs and two more turned 45 degrees, staggered in length so their end caps never meet.
    out.append(slab("shaft", (7.8, 7.9172, 3.2), (8.2, 8.0828, 13.5), "wood", "wood_dark"))
    out.append(slab("shaft", (7.9172, 7.8, 3.21), (8.0828, 8.2, 13.49), "wood", "wood_dark"))
    for k, a in enumerate((45.0, -45.0)):
        out.append(slab("shaft", (7.8, 7.9172, 3.22 + 0.01 * k), (8.2, 8.0828, 13.48 - 0.01 * k), "wood", "wood_dark",
                        rotation={"angle": a, "axis": "z", "origin": [8.0, 8.0, 3.2]}))
    # The lashing: a short square collar.
    out.append(slab("lashing", (7.72, 7.72, 2.5), (8.28, 8.28, 3.6), "lash", "lash_dark"))
    # The tusk point: crossed tapering slabs (a wide one and a narrow tip), ivory, stained where it meets the lashing.
    out.append(slab("tusk", (7.85, 7.88, 1.2), (8.15, 8.12, 2.6), "ivory", "ivory_dark"))
    out.append(slab("tusk", (7.88, 7.85, 1.21), (8.12, 8.15, 2.59), "ivory", "ivory_dark"))
    out.append(slab("tip", (7.93, 7.93, 0.5), (8.07, 8.07, 1.3), "ivory", "ivory"))
    # A barb kicked back off the point.
    out.append(slab("barb", (7.94, 8.12, 1.5), (8.06, 8.32, 2.3), "ivory", "ivory_dark",
                    rotation={"angle": -22.5, "axis": "x", "origin": [8.0, 8.12, 1.5]}))
    return out


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


for name in ("boar_spawn_egg", "boar_tusk", "raw_boar", "cooked_boar", "tusk_harpoon", "tusk_hook"):
    write(os.path.join(MODELS, name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})

# The spear as vanilla's are: a flat icon in the GUI, on the ground and in frames; the long in-hand sprite on vanilla's
# spear_in_hand parent (its held transforms) everywhere else.
write(os.path.join(MODELS, "tusk_spear.json"),
      OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/tusk_spear"})]))
write(os.path.join(MODELS, "tusk_spear_in_hand.json"),
      OrderedDict([("parent", "minecraft:item/spear_in_hand"), ("textures", {"layer0": "tomyen:item/tusk_spear_in_hand"})]))
write(os.path.join(ITEMS, "tusk_spear.json"), OrderedDict([
    ("model", OrderedDict([
        ("type", "minecraft:select"),
        ("cases", [{"model": {"type": "minecraft:model", "model": "tomyen:item/tusk_spear"},
                    "when": ["gui", "ground", "fixed", "on_shelf"]}]),
        ("fallback", {"type": "minecraft:model", "model": "tomyen:item/tusk_spear_in_hand"}),
        ("property", "minecraft:display_context")])),
    ("swap_animation_scale", 1.95)]))

_steel_3d = json.load(open(os.path.join(MODELS, "harpoon_spear_3d.json")))
HARPOON_3D = harpoon_elements()
write(os.path.join(MODELS, "tusk_harpoon_3d.json"), OrderedDict([
    ("credit", "tomyen boar - tools/gen_boar.py"),
    ("textures", {"tusk": "tomyen:item/tusk_harpoon_3d", "particle": "tomyen:item/tusk_harpoon_3d"}),
    ("display", _steel_3d["display"]),
    ("elements", HARPOON_3D)]))
write(os.path.join(ITEMS, "tusk_harpoon_3d.json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/tusk_harpoon_3d"}})


def looting(n=1.0):
    return {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": n},
            "enchantment": "minecraft:looting"}


def count(low, high):
    return {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}}


SMELT = {"type": "minecraft:furnace_smelt", "condition": {"type": "minecraft:any_of", "terms": [
    {"type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:flags": {"is_on_fire": True}}},
    {"type": "minecraft:entity_properties", "entity": "direct_attacker", "predicate": {"minecraft:equipment": {
        "mainhand": {"predicates": {"minecraft:enchantments": [{"enchantments": "#minecraft:smelts_loot"}]}}}}}]}}

# Raw boar (cooked if it burned), leather and, from the sows, now and then a tusk (a tusker drops a pair on top: Boar).
write(os.path.join(DATA, "loot_table/entities/boar.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:raw_boar",
                                  "modifier": [count(1, 3), SMELT, looting()]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:leather",
                                  "modifier": [count(0, 2), looting()]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "tomyen:boar_tusk",
                                  "modifier": [count(0, 1), looting()]}]}]),
    ("random_sequence", "tomyen:entities/boar")]))

# What a rooting boar turns up: mostly worms and roots, a little buried junk, now and then a scrap of something that
# fell out of the sky.
ROOTING = [("tomyen:earthworms", 30, 1, 3), ("minecraft:potato", 12, 1, 1), ("minecraft:carrot", 10, 1, 1),
           ("minecraft:beetroot_seeds", 10, 1, 2), ("minecraft:brown_mushroom", 10, 1, 1), ("minecraft:red_mushroom", 6, 1, 1),
           ("minecraft:bone", 8, 1, 1), ("minecraft:flint", 8, 1, 1), ("tomyen:garlic", 4, 1, 1), ("tomyen:tech_scrap", 2, 1, 1)]
write(os.path.join(DATA, "loot_table/gameplay/boar_rooting.json"), OrderedDict([
    ("type", "minecraft:gift"),
    ("pools", [{"rolls": 1, "entries": [
        OrderedDict([("type", "minecraft:item"), ("name", n), ("weight", wt)] + ([("modifier", [count(lo, hi)])] if hi > 1 else []))
        for n, wt, lo, hi in ROOTING]}]),
    ("random_sequence", "tomyen:gameplay/boar_rooting")]))


def advancement(name, ingredient, category):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def shapeless(name, ingredients, result, n, category="misc", book="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", n), ("id", result)]))]))
    advancement(name, ingredients[0], book)


def shaped(name, pattern, key, result, n, category="equipment", book="combat"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", n), ("id", result)]))]))
    advancement(name, "tomyen:boar_tusk", book)


def cooking(name, kind, ingredient, result, xp, time):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:" + kind), ("category", "food"), ("cookingtime", time), ("experience", xp),
        ("ingredient", ingredient), ("result", {"id": result})]))
    advancement(name, ingredient, "food")


# A tusk lashed to a stick with string, as vanilla's spears are made; tusk harpoons and hooks from string and sticks.
shaped("tusk_spear", ["  T", " /S", "/  "], {"T": "tomyen:boar_tusk", "/": "minecraft:stick", "S": "minecraft:string"},
       "tomyen:tusk_spear", 1)
shapeless("tusk_harpoon", ["tomyen:boar_tusk", "minecraft:stick", "minecraft:string"], "tomyen:tusk_harpoon", 2,
          category="equipment", book="combat")
shapeless("tusk_hook", ["tomyen:boar_tusk", "minecraft:string"], "tomyen:tusk_hook", 2, category="equipment", book="tools")
shapeless("bone_meal_from_boar_tusk", ["tomyen:boar_tusk"], "minecraft:bone_meal", 3)
cooking("cooked_boar", "smelting", "tomyen:raw_boar", "tomyen:cooked_boar", 0.35, 200)
cooking("cooked_boar_from_smoking", "smoking", "tomyen:raw_boar", "tomyen:cooked_boar", 0.35, 200)
cooking("cooked_boar_from_campfire_cooking", "campfire_cooking", "tomyen:raw_boar", "tomyen:cooked_boar", 0.35, 600)


def tag_add(path, entries):
    """Adds entries to a tag file, leaving whatever else is in it."""
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    for e in entries:
        if e not in values:
            values.append(e)
    write(path, {"values": values})


tag_add(os.path.join(DATA, "tags/item/boar_food.json"),
        ["minecraft:carrot", "minecraft:beetroot", "minecraft:brown_mushroom", "minecraft:red_mushroom"])
tag_add(os.path.join(DATA, "tags/item/tusk_tool_materials.json"), ["tomyen:boar_tusk"])
tag_add(os.path.join(VANILLA_DATA, "tags/item/spears.json"), ["tomyen:tusk_spear"])
tag_add(os.path.join(VANILLA_DATA, "tags/item/meat.json"), ["tomyen:raw_boar", "tomyen:cooked_boar"])


def vanilla(names, pitch, volume=None):
    out = []
    for n in names:
        s = OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)])
        if volume is not None:
            s["volume"] = volume
        out.append(s)
    return out


def event(name, sounds):
    return name, OrderedDict([("subtitle", "subtitles.tomyen." + name), ("sounds", sounds)])


# Vanilla sounds re-pitched: a big pig's grunts under a hoglin's, squeals and snorts from the hoglin, the paw of the
# ground from rooted dirt, a goat's ram for the impact and a ravager's daze for the stun.
SOUNDS = OrderedDict([
    event("boar_ambient", vanilla(["mob/pig/big/ambient%d" % i for i in range(1, 7)], 0.8)
          + vanilla(["mob/hoglin/idle%d" % i for i in range(1, 7)], 1.35, 0.6)),
    event("boar_hurt", vanilla(["mob/pig/big/hit%d" % i for i in (1, 2, 3)], 0.9) + vanilla(["mob/hoglin/hurt%d" % i for i in (1, 2, 3, 4)], 1.3)),
    event("boar_death", vanilla(["mob/pig/big/death"], 0.8) + vanilla(["mob/hoglin/death%d" % i for i in (1, 2, 3)], 1.3)),
    event("boar_angry", vanilla(["mob/hoglin/angry%d" % i for i in range(1, 7)], 1.3)),
    event("boar_paw", vanilla(["block/rooted_dirt/step%d" % i for i in range(1, 7)], 0.75)),
    event("boar_charge", vanilla(["mob/hoglin/attack1", "mob/hoglin/attack2"], 1.25) + vanilla(["mob/hoglin/angry1", "mob/hoglin/angry4"], 1.5)),
    event("boar_impact", vanilla(["mob/goat/impact1", "mob/goat/impact2"], 0.8)),
    event("boar_stunned", vanilla(["mob/ravager/stun1", "mob/ravager/stun2", "mob/ravager/stun3"], 1.5, 0.7)),
    event("boar_root", vanilla(["block/rooted_dirt/break%d" % i for i in (1, 2, 3, 4)], 1.1, 0.6) + vanilla(["mob/pig/big/eat1", "mob/pig/big/eat2"], 0.9)),
    event("boar_step", vanilla(["mob/hoglin/step%d" % i for i in range(1, 7)], 1.3, 0.5)),
])
merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)

LANG = OrderedDict([
    ("entity.tomyen.boar", "Wild Boar"),
    ("item.tomyen.boar_spawn_egg", "Wild Boar Spawn Egg"),
    ("item.tomyen.boar_tusk", "Boar Tusk"),
    ("item.tomyen.raw_boar", "Raw Boar"),
    ("item.tomyen.cooked_boar", "Cooked Boar"),
    ("item.tomyen.tusk_spear", "Tusk Spear"),
    ("item.tomyen.tusk_harpoon", "Tusk Harpoon"),
    ("item.tomyen.tusk_hook", "Tusk Hook"),
    ("subtitles.tomyen.boar_ambient", "Boar grunts"),
    ("subtitles.tomyen.boar_hurt", "Boar squeals"),
    ("subtitles.tomyen.boar_death", "Boar dies"),
    ("subtitles.tomyen.boar_angry", "Boar snorts"),
    ("subtitles.tomyen.boar_paw", "Boar paws the ground"),
    ("subtitles.tomyen.boar_charge", "Boar charges"),
    ("subtitles.tomyen.boar_impact", "Boar rams"),
    ("subtitles.tomyen.boar_stunned", "Boar is dazed"),
    ("subtitles.tomyen.boar_root", "Boar roots"),
    ("subtitles.tomyen.boar_step", "Footsteps"),
])
merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "boar", "BoarModel.java"))


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
        has_children = name in TREE
        lhs = f"PartDefinition {var} = " if has_children else ""
        lines.append(f"\t\t{lhs}{('p_' + parent) if parent else 'root'}.addOrReplaceChild(\"{name}\", "
                     f"{builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


if os.path.exists(JAVA):
    src = open(JAVA).read()
    start, end = "\t\t// BEGIN GENERATED by tools/gen_boar.py\n", "\t\t// END GENERATED\n"
    a, b = src.index(start) + len(start), src.index(end)
    src = src[:a] + java_layer() + "\n" + src[b:]
    with open(JAVA, "w") as out:
        out.write(src)


# ---------------------------------------------------------------------------------------------- posing
def rest_pose():
    return {name: [pivot, rot, 1.0, True] for name, _, pivot, rot, _ in PARTS}


def add(pose, part, dx=0.0, dy=0.0, dz=0.0, pos=None):
    if pos:
        x, y, z = pose[part][0]
        pose[part][0] = (x + pos[0], y + pos[1], z + pos[2])
    x, y, z = pose[part][1]
    pose[part][1] = (x + dx, y + dy, z + dz)


GORE_DIP = 0.3


def gore(attack):
    """The gore: a short dip of the head, then a hard upward toss of the tusks. Returns (dip, toss), both 0..1."""
    dip = math.sin(min(1.0, attack / GORE_DIP) * math.pi) if attack < GORE_DIP else 0.0
    toss = math.sin(max(0.0, (attack - GORE_DIP) / (1.0 - GORE_DIP)) * math.pi) if attack > GORE_DIP else 0.0
    return dip, toss


def animate(pose, walk=0.0, speed=0.0, age=0.0, yaw=0.0, pitch=0.0, root=0.0, paw=0.0, charge=0.0, stun=0.0, attack=0.0,
            agitated=0.0, baby=False, tusker=False):
    """Python twin of BoarModel.setupAnim, for the preview."""
    phase = walk * 0.6662
    # Walking it trots (diagonal pairs); charging it bounds (the front pair together, then the back pair).
    trot_a = math.cos(phase) * 1.2 * speed
    trot_b = math.cos(phase + math.pi) * 1.2 * speed
    bound_f = math.cos(phase) * 1.4 * speed
    bound_b = math.cos(phase + math.pi) * 1.4 * speed
    legs = {"right_front": (trot_a, bound_f, phase), "left_front": (trot_b, bound_f, phase),
            "right_back": (trot_b, bound_b, phase + math.pi), "left_back": (trot_a, bound_b, phase + math.pi)}
    for leg, (trot, bound, lp) in legs.items():
        swing = trot + (bound - trot) * charge
        add(pose, leg + "_leg", dx=swing)
        # The lower leg folds while the leg swings forward through the air.
        lift_phase = lp if charge > 0.5 else (phase if leg in ("right_front", "left_back") else phase + math.pi)
        add(pose, leg + "_shin", dx=max(0.0, math.sin(lift_phase)) * 0.8 * speed)
    add(pose, "body", pos=(0, abs(math.cos(phase)) * 0.5 * speed, 0), dx=0.1 * math.sin(phase) * charge * speed)

    add(pose, "head", dy=max(-40.0, min(40.0, yaw)) * math.pi / 180 * 0.8, dx=pitch * math.pi / 180 * 0.6
        + 0.06 * math.cos(phase * 2) * speed)
    # The ears flick now and then.
    flick = age % 83.0
    if flick < 6.0:
        add(pose, "right_ear", dz=-0.3 * math.sin(flick / 6.0 * math.pi))
    # The tail swishes; with its hackles up the boar carries it high and stiff, and its crest rises.
    add(pose, "tail", dz=0.3 * math.sin(age * 0.25) * (1 - agitated), dx=1.9 * agitated)
    add(pose, "mane", pos=(0, -0.8 * agitated, 0))

    if root > 0.0:
        # Snout down in the dirt, nudging and shoving, chewing at what it turns up.
        add(pose, "head", dx=0.75 * root + 0.12 * root * math.sin(age * 0.9), dy=0.15 * root * math.sin(age * 0.37))
        add(pose, "jaw", dx=0.18 * root * max(0.0, math.sin(age * 1.3)))
        add(pose, "body", dx=0.08 * root)
        add(pose, "right_front_leg", dx=-0.1 * root)
        add(pose, "left_front_leg", dx=-0.1 * root)
    if paw > 0.0:
        # The warning: head low, weight back, a front hoof scraping the ground again and again.
        cycle = age * 0.55
        lift = max(0.0, math.sin(cycle))
        add(pose, "head", dx=0.35 * paw)
        add(pose, "body", dx=0.06 * paw, pos=(0, 0, 0.6 * paw))
        add(pose, "right_front_leg", dx=paw * (-0.9 * lift + 0.35 * math.cos(cycle)))
        add(pose, "right_front_shin", dx=paw * 0.9 * lift)
        add(pose, "jaw", dx=0.12 * paw)
        for ear in ("right_ear", "left_ear"):
            add(pose, ear, dx=-0.5 * paw)
    if charge > 0.0:
        # Head down and level, tusks forward, ears flat.
        add(pose, "head", dx=0.4 * charge)
        add(pose, "body", dx=0.05 * charge)
        for ear in ("right_ear", "left_ear"):
            add(pose, ear, dx=-0.7 * charge)
    if stun > 0.0:
        # Dazed: head hanging and lolling, legs splayed, body sagging.
        add(pose, "head", dx=0.5 * stun, dz=0.35 * stun * math.sin(age * 0.35), dy=0.25 * stun * math.cos(age * 0.35))
        add(pose, "body", dz=0.08 * stun * math.sin(age * 0.35), pos=(0, 1.0 * stun, 0))
        add(pose, "jaw", dx=0.25 * stun)
        for leg, s in (("right_front_leg", 1), ("right_back_leg", 1), ("left_front_leg", -1), ("left_back_leg", -1)):
            add(pose, leg, dz=0.2 * s * stun)
        for ear, s in (("right_ear", 1), ("left_ear", -1)):
            add(pose, ear, dx=0.6 * stun, dz=0.3 * s * stun)
    if attack > 0.0:
        dip, toss = gore(attack)
        add(pose, "head", dx=0.4 * dip - 1.1 * toss)
        add(pose, "jaw", dx=0.3 * toss)
        add(pose, "right_front_leg", dx=-0.3 * toss)
        add(pose, "left_front_leg", dx=-0.3 * toss)
        add(pose, "body", dx=-0.06 * toss)
    # Piglets: a bigger head, no tusks yet. Tuskers carry big ones; sows small ones.
    if baby:
        pose["head"][2] = 1.25
        for t in TUSK_PARTS:
            pose[t][3] = False
    else:
        for t in TUSK_PARTS:
            pose[t][2] = 1.45 if tusker else 0.85
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


def quads(pose, labels=False, baby=False):
    """Every textured face of the posed model: (corners in model space, uv rect[, label, outward normal])."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot, scale, visible = pose[child]
            if not visible:
                continue
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(mul(matrix, rot_matrix(*rot)), [[scale, 0, 0], [0, scale, 0], [0, 0, scale]])
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

    if baby:
        # MeshTransformer.scaling(0.5): the whole model halved and dropped back onto the ground.
        walk("root", [[0.5, 0, 0], [0, 0.5, 0], [0, 0, 0.5]], (0.0, 24.016 * 0.5, 0.0))
    else:
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


# Z-fighting: no two faces of the model may share a plane, face the same way and overlap, at rest and as a tusker.
from coplanar import Face, fights, check_elements
for _label, _pose in (("rest", rest_pose()), ("tusker", animate(rest_pose(), tusker=True))):
    _hits = fights([Face(label, verts, normal) for verts, uv, label, normal in quads(_pose, labels=True)])
    if _hits:
        for h_ in _hits:
            print("z-fight: boar", _label, h_)
        raise SystemExit(f"boar ({_label}): {len(_hits)} coplanar overlapping face pairs")
check_elements("tusk_harpoon_3d", HARPOON_3D)


def extents(pose, baby=False):
    pts = [p for verts, _ in quads(pose, baby=baby) for p in verts]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


ext = extents(rest_pose())
print(f"boar: {cube_count()} cubes; at rest x {ext[0][0]:.1f}..{ext[0][1]:.1f}, top y {ext[1][0]:.1f}, "
      f"feet y {ext[1][1]:.2f} (ground 24), z {ext[2][0]:.1f}..{ext[2][1]:.1f}")


# ---------------------------------------------------------------------------------------------- preview
def render(pose, view, scale=8, width=300, height=260, light=(0.35, -0.8, -0.5), focus=None, texture=None, baby=False):
    """Orthographic render. view: 'front', 'side' (from its right), 'three' (three-quarters front) or 'back'."""
    texture = texture or TEXTURE
    angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "rear3": math.pi * 0.8,
             "lside": -math.pi / 2}[view]
    ca, sa = math.cos(angle), math.sin(angle)
    zbuf = [1e9] * (width * height)
    img = [(46, 52, 64, 255)] * (width * height)
    qs = quads(pose, baby=baby)
    if focus:
        fx = focus[0] * ca - focus[2] * sa
        ox, oy, ly = width / 2 - fx * scale, height / 2, focus[1]
    else:
        xs = [p[0] * ca - p[2] * sa for verts, _ in qs for p in verts]
        ox, oy, ly = width * 0.5 - (min(xs) + max(xs)) / 2 * scale, height - 20, 24.0
    gy = int(oy + (24.0 - ly) * scale)
    for x in range(width):
        if 0 <= gy < height:
            img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    for verts, (ua, va, ub, vb) in qs:
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
                col = texture.get(int(tu), int(tv))
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


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k, im in enumerate(images):
        bx, by = (k % cols) * w, (k // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


def texture_sheet(textures, s=4):
    W = TEX_W * s
    big = []
    for tex in textures:
        part = [(40, 44, 52, 255) if ((x // (s * 4)) + (y // (s * 4))) % 2 == 0 else (52, 56, 64, 255)
                for y in range(TEX_H * s) for x in range(W)]
        for y in range(TEX_H * s):
            for x in range(W):
                c = tex.get(x // s, y // s)
                if c[3]:
                    part[y * W + x] = c
        big += part
    return big, W, TEX_H * s * len(textures)


def item_previews():
    """Sprites up close, the Tusk Spear held in first person next to vanilla's stone spear, both GUI icons, and the
    tusk harpoon model from the side and in the gun's loading view."""
    from gunsmith_kit import render as render_elements, first_person, gui_icon, compose, ortho, SIDE, RIGHT_FRONT

    def extruded(canvas):
        """item/generated as the game builds it: one texel-deep slab, a box per opaque texel (enough for a preview)."""
        n = canvas.w
        k = 16.0 / n
        els = []
        for y in range(n):
            for x in range(n):
                if canvas.get(x, y)[3] == 0:
                    continue
                uv = [x * k + 0.01, y * k + 0.01, (x + 1) * k - 0.01, (y + 1) * k - 0.01]
                els.append({"from": [x * k, 16 - (y + 1) * k, 7.5], "to": [(x + 1) * k, 16 - y * k, 8.5],
                            "faces": {fc: {"uv": uv, "texture": "#t"} for fc in ("north", "south", "east", "west", "up", "down")}})
        return els, {"#t": (canvas.px, n)}

    spear_hand = json.loads(JAR.read("assets/minecraft/models/item/spear_in_hand.json"))["display"]
    rows = []
    cells = []
    for name in ("tusk_spear_in_hand", "stone_spear_in_hand"):
        if name.startswith("stone"):
            w, h, px = vanilla_png("item/" + name)
            cv = Canvas(w, h, px)
        else:
            w, h, px = mod_png("item/" + name + ".png")
            cv = Canvas(w, h, px)
        els, atl = extruded(cv)
        # As the game frames it (16:9, a 70 degree field of view), and pulled back (110 degrees) to see it whole.
        for fov in (70.0, 110.0):
            project, turn = first_person(spear_hand, 480, 270, fov)
            fp = render_elements(els, atl, project, turn, 480, 270)
            cells.append(([(110, 130, 160) if v is None else v for v in fp], 480, 270, 1))
    rows.append(cells)
    gui = {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}
    cells = []
    for name, vanilla_name in (("tusk_spear", None), (None, "stone_spear"), ("boar_tusk", None), ("raw_boar", None),
                               (None, "porkchop"), ("cooked_boar", None), (None, "cooked_porkchop"), ("tusk_harpoon", None),
                               (None, "harpoon_spear"), ("tusk_hook", None), (None, "iron_hook"), ("boar_spawn_egg", None)):
        if vanilla_name == "harpoon_spear" or vanilla_name == "iron_hook":
            w, h, px = mod_png("item/" + vanilla_name + ".png")
        elif vanilla_name:
            w, h, px = vanilla_png("item/" + vanilla_name)
        else:
            w, h, px = mod_png("item/" + name + ".png")
        img = [(139, 139, 139) if c[3] == 0 else c[:3] for c in px]
        cells.append((img, w, h, 6))
    rows.append(cells[:6])
    rows.append(cells[6:])
    # The 3D tusk harpoon beside the steel one.
    cells = []
    steel = json.load(open(os.path.join(MODELS, "harpoon_spear_3d.json")))
    w, h, gpx = mod_png("item/gunsmith_guns.png")
    for els, atl in ((HARPOON_3D, {"#tusk": (H3D.px, 16)}), (steel["elements"], {"#gunsmith": (gpx, w)})):
        cells.append(([(90, 100, 120) if v is None else v for v in render_elements(els, atl, ortho(SIDE, 30, 420, 120, (8, 8, 7)),
                                                                                  lambda n: n, 420, 120)], 420, 120, 1))
        cells.append(([(90, 100, 120) if v is None else v for v in render_elements(els, atl, ortho(RIGHT_FRONT, 30, 420, 120, (8, 8, 7)),
                                                                                  lambda n: n, 420, 120)], 420, 120, 1))
    rows.append(cells[:2])
    rows.append(cells[2:])
    compose(rows, os.path.join(PREVIEW, "preview_boar_items.png"))


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    R = rest_pose
    views = [render(R(), "front", scale=7), render(R(), "side", scale=7), render(R(), "three", scale=7),
             render(R(), "rear3", scale=7),
             render(animate(R(), tusker=True), "three", scale=7 * 1.2),
             render(animate(R(), baby=True), "three", scale=7, texture=PIGLET_TEXTURE, baby=True),
             render(animate(R(), baby=True), "side", scale=7, texture=PIGLET_TEXTURE, baby=True),
             render(animate(R(), walk=1.2, speed=0.8, age=10), "side", scale=7),
             render(animate(R(), walk=3.6, speed=0.8, age=10), "side", scale=7),
             render(animate(R(), root=1.0, age=14), "three", scale=7),
             render(animate(R(), paw=1.0, agitated=1.0, age=3.0), "side", scale=7),
             render(animate(R(), paw=1.0, agitated=1.0, age=8.0), "three", scale=7),
             render(animate(R(), charge=1.0, walk=2.0, speed=1.0, agitated=1.0, tusker=True), "side", scale=7),
             render(animate(R(), charge=1.0, walk=4.4, speed=1.0, agitated=1.0, tusker=True), "three", scale=7),
             render(animate(R(), stun=1.0, age=4.0), "front", scale=7),
             render(animate(R(), stun=1.0, age=13.0), "three", scale=7),
             render(animate(R(), attack=0.2, agitated=1.0), "side", scale=7),
             render(animate(R(), attack=0.65, agitated=1.0, tusker=True), "side", scale=7)]
    head_pts = [p for verts, uv, label, n in quads(R(), labels=True) if label.startswith(("head.", "jaw.")) for p in verts]
    centre = tuple(sum(p[i] for p in head_pts) / len(head_pts) for i in range(3))
    views += [render(animate(R(), tusker=True), "three", scale=20, focus=centre),
              render(animate(R(), tusker=True), "side", scale=20, focus=centre),
              render(animate(R(), tusker=True), "front", scale=20, focus=centre)]
    out, bw, bh = board(views, 300, 260, 4)
    png(os.path.join(PREVIEW, "preview_boar.png"), bw, bh, out)
    big, W, H = texture_sheet([TEXTURE, PIGLET_TEXTURE])
    png(os.path.join(PREVIEW, "preview_boar_texture.png"), W, H, big)
    item_previews()
    print("preview written to", PREVIEW)
print("done")
