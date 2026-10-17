"""Builds the Crow Murder's art: the crow model (the cube layout in MODEL, written into client/crow/CrowModel.java between
the GENERATED markers) and its texture, the Crow Nest block (two block models: the bare nest, and the same with a
glint of stolen treasure in the bowl) and its textures, the Crow Feather, Crow Fly and spawn egg sprites, and the item
models, block states and item definitions. On the data side it writes the loot tables (the crow and the nest), the
recipes, the #tomyen:shiny item tag, and merges the lang entries and the crows' sounds into en_us.json and sounds.json.

The crow's texture is painted at twice the model's pixel density (128x128 for the model's 64x64 UV space): glossy
blue-black plumage from one small ramp, with a smooth blue-to-violet sheen band across the back, crown and wing
coverts (no noise, no dithering), regular scalloped covert rows and striped flight feathers on the wings, a slate-grey
beak with a pale ridge, beady eyes with a single glint, and scaly grey legs banded by their scutes. A rerun gives the
same files.

The model is checked for z-fighting (tools/coplanar.py) at rest and through its flight, peck, caw and sleep poses, and
the nest's block models are checked too (coplanar.check_elements).

With --preview DIR it renders, into DIR, preview_crow.png: the crow from the front, three-quarters, side and back at
rest; pecking; cawing; asleep; hopping; flight through the downstroke and upstroke (side and from above); gliding;
the head up close; and preview_crow_items.png: the nest bare and with loot, the item sprites beside the vanilla
feather. The render uses the same cubes, pivots and UV layout as the Java model, and a Python twin of CrowModel.pose.

Usage: python3 tools/gen_crow.py src/main/resources/assets/tomyen [--preview DIR] [--sounds]"""
import json, math, os, struct, sys, zlib
from collections import OrderedDict

ASSETS = sys.argv[1]  # .../assets/tomyen
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(path):
    """8-bit RGBA, grey+alpha or palette PNGs, non-interlaced."""
    data = open(path, "rb").read()
    pos, idat, w, h, ct, plte, trns = 8, b"", 0, 0, 6, None, None
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h, _, ct = struct.unpack(">IIBB", body[:10])
        elif t == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif t == b"tRNS":
            trns = body
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ct]
    raw = zlib.decompress(idat)
    stride, out, prev = w * bpp, [], bytearray(w * bpp)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        for x in range(w):
            px = line[x * bpp:(x + 1) * bpp]
            if ct == 6: out.append(tuple(px))
            elif ct == 2: out.append(tuple(px) + (255,))
            elif ct == 4: out.append((px[0], px[0], px[0], px[1]))
            elif ct == 0: out.append((px[0], px[0], px[0], 255))
            else: out.append(plte[px[0]] + ((trns[px[0]] if trns and px[0] < len(trns) else 255),))
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
            self.px[y * self.w + x] = c if len(c) == 4 else rgba(c)

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
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------------------------------------- the model
# Every part: (name, parent, pivot, rotation, cubes); a cube is (texture key, from, size). Model space is the game's: y
# down, the front toward -z, the ground at y 24. Cube sizes are whole pixels; positions can be fractional. The right
# side of the bird is -x. Rotations here are the rest pose (standing, wings folded); CrowModel.pose sets the rest.
S = 2                   # texels per model pixel
TEX_W, TEX_H = 64, 64   # the model's UV space; the image is S times that
GROUND = 24.0
FOLD_Y = math.pi / 2
FOLD_Z = math.pi / 2 - 0.08


def wing(side):
    """A wing on the right (side -1) or left (+1): an arm of secondaries and a hand of primaries ending in three
    fingered tips, each its own part so they can fan apart in flight and close up into one point when folded. The
    fingers ride at slightly different heights so where they overlap their faces never share a plane."""
    k = "r" if side < 0 else "l"
    x = (lambda a, w: a) if side < 0 else (lambda a, w: -a - w)
    name = "right" if side < 0 else "left"
    # The coverts thicken the leading edge on the wing's upper side in flight; folded, that side lies against the body.
    parts = [(f"{name}_wing", "body", (2.4 * side, -2.0, -2.0), (0.0, -FOLD_Y * side, -FOLD_Z * side), [
                 (f"wing_{k}_arm", (x(-4.0, 4), 0.0, -1.0), (4, 1, 5)),
                 (f"wing_{k}_covert", (x(-3.0, 3), -1.0, -1.0), (3, 1, 3))]),
             (f"{name}_hand", f"{name}_wing", (4.0 * side, 0.0, 0.0), (0.0, 0.0, 0.0), [
                 (f"wing_{k}_prim", (x(-4.0, 4), 0.0, -1.0), (4, 1, 4))])]
    for n, (zc, length, y) in enumerate(((-0.5, 2, 0.05), (1.0, 3, 0.15), (2.5, 2, 0.25)), 1):
        parts.append((f"{name}_finger{n}", f"{name}_hand", (4.0 * side, y, zc), (0.0, 0.0, 0.0),
                      [(f"wing_{k}_f{n}", (x(-float(length), length), 0.0, -0.5), (length, 1, 1))]))
    return parts


def leg(side):
    """A leg: a feathered thigh, a scaly shank, three toes forward (the outer two splayed) and one back."""
    name = "right" if side < 0 else "left"
    return [(f"{name}_leg", None, (1.4 * side, 21.0, 0.5), (0.0, 0.0, 0.0), [
                ("thigh", (-1.0, -1.0, -1.0), (2, 2, 2)),
                ("shank", (-0.5, 0.0, -0.5), (1, 2, 1))]),
            (f"{name}_foot", f"{name}_leg", (0.0, 2.0, 0.0), (0.0, 0.0, 0.0), [
                ("toe_mid", (-0.5, 0.0, -3.0), (1, 1, 3)),
                ("toe_back", (-0.5 - 0.1 * side, 0.0, 0.0), (1, 1, 2))]),
            # The side toes ride a hair higher than the middle one (and the inner a hair higher than the outer), so
            # where they cross at the root their faces never share a plane.
            (f"{name}_toe_in", f"{name}_foot", (0.0, 0.0, -0.3), (0.0, 0.4 * side, 0.0), [
                ("toe_side", (-0.5, -0.1, -2.0), (1, 1, 2))]),
            (f"{name}_toe_out", f"{name}_foot", (0.0, 0.0, -0.3), (0.0, -0.4 * side, 0.0), [
                ("toe_side", (-0.5, -0.2, -2.0), (1, 1, 2))])]


MODEL = [
    ("body", None, (0.0, 18.5, 0.5), (-0.35, 0.0, 0.0), [
        ("torso", (-2.5, -2.5, -3.5), (5, 5, 7)),
        ("chest", (-2.5, -1.0, -4.5), (5, 3, 1)),
        ("belly", (-2.0, 2.5, -2.5), (4, 1, 5)),
        ("mantle", (-2.0, -3.0, -2.5), (4, 1, 5)),
        ("rump", (-2.0, -2.0, 3.5), (4, 4, 1))]),
    ("head", "body", (0.0, -1.5, -4.0), (0.3, 0.0, 0.0), [
        ("skull", (-2.0, -4.0, -3.0), (4, 4, 4)),
        ("crown", (-1.5, -4.5, -2.5), (3, 1, 3)),
        ("throat", (-1.5, -0.5, -3.5), (3, 2, 2)),
        ("nape", (-1.5, -3.0, 1.0), (3, 3, 1)),
        ("bristles", (-1.5, -3.5, -4.2), (3, 1, 1))]),
    # The upper mandible, its ridge sloping down to a hooked tip; the lower one hinges under it to caw.
    ("beak", "head", (0.0, -3.0, -3.0), (0.15, 0.0, 0.0), [
        ("beak", (-1.0, 0.0, -2.0), (2, 2, 2)),
        ("beak_mid", (-1.0, 0.6, -3.0), (2, 1, 1)),
        ("beak_tip", (-0.5, 1.2, -4.0), (1, 1, 1))]),
    ("jaw", "beak", (0.0, 2.05, 0.0), (0.0, 0.0, 0.0), [
        ("jaw", (-1.0, 0.0, -2.6), (2, 1, 3))]),
    ("tail", "body", (0.0, -1.5, 4.0), (-0.1, 0.0, 0.0), [
        ("tail_mid", (-1.5, 0.0, 0.0), (3, 1, 6)),
        ("tail_covert", (-1.5, -1.0, -0.5), (3, 1, 3))]),
    # The outer tail feathers, tucked in under the centre ones at rest, fanned out in flight.
    ("tail_l", "tail", (1.1, 0.25, 0.0), (0.0, -0.2, 0.0), [("tail_side", (-0.5, 0.0, 0.3), (2, 1, 5))]),
    ("tail_r", "tail", (-1.1, 0.35, 0.0), (0.0, 0.2, 0.0), [("tail_side", (-1.5, 0.0, 0.3), (2, 1, 5))]),
] + wing(-1) + wing(1) + leg(-1) + leg(1)


def tree(model):
    t = {}
    for name, parent, _, _, _ in model:
        t.setdefault(parent or "root", []).append(name)
    return t


def pack():
    """Texture offsets (in model pixels) for each cube key, skyline-packed into the texture."""
    sizes = {}
    for _, _, _, _, cubes in MODEL:
        for key, _, size in cubes:
            assert sizes.setdefault(key, size) == size, key
            assert all(float(c).is_integer() for c in size), key
    order = sorted(sizes, key=lambda k: (-(sizes[k][2] + sizes[k][1]), -(sizes[k][0] + sizes[k][2]), k))
    sky = [0] * TEX_W
    offs = {}
    for k in order:
        w, h, d = sizes[k]
        fw, fh = 2 * (w + d), d + h
        best = min(range(TEX_W - fw + 1), key=lambda x: (max(sky[x:x + fw]), x))
        y = max(sky[best:best + fw])
        offs[k] = (best, y)
        for x in range(best, best + fw):
            sky[x] = y + fh
    assert max(sky) <= TEX_H, f"texture too small: {max(sky)}"
    return {k: (offs[k], sizes[k]) for k in sizes}


CUBES = pack()


def faces(u, v, w, h, d):
    """A cube's faces in the texture (model pixels) as the game lays them out: (u, v, width, height). 'top' is the
    cube's -y side and 'bottom' its +y side; in both the front (-z) edge is the last row. In 'right' (the -x side) the
    front edge is the last column, in 'left' (+x) the first."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, key, painters):
    """painters: face name (or 'default') -> f(x, y, fw, fh) -> colour or None. x, y and the size are in texels."""
    (u, v), (w, h, d) = CUBES[key]
    for name, (fu, fv, fw, fh) in faces(u, v, w, h, d).items():
        f = painters.get(name, painters.get("default"))
        if f is None:
            continue
        W, H = fw * S, fh * S
        for y in range(H):
            for x in range(W):
                c = f(x, y, W, H)
                if c is not None:
                    canvas.set(fu * S + x, fv * S + y, c)


# ---------------------------------------------------------------------------------------------- palettes
# Blue-black plumage, light to dark. The sheen runs from steel blue to violet; the underparts are a duller, greyer black.
PLUME = [(54, 56, 78), (39, 40, 58), (28, 29, 43), (20, 20, 31), (12, 12, 20)]
SHEEN_BLUE = (84, 106, 172)
SHEEN_VIOLET = (106, 80, 158)
DULL = [(46, 46, 56), (35, 35, 44), (26, 26, 33), (18, 18, 24)]
BEAK = [(104, 104, 114), (76, 76, 86), (52, 52, 61), (34, 34, 41), (22, 22, 28)]
LEG = [(98, 96, 104), (74, 72, 80), (54, 52, 60), (38, 36, 44)]
CLAW = (20, 18, 24)
EYE_RIM = (70, 72, 92)
IRIS = (44, 30, 26)
PUPIL = (10, 9, 12)
GLINT = (238, 242, 255)


def sheen(base, x, y, w, h, strength, phase=0.0):
    """Iridescence: a soft diagonal band across a face, blue at one end of it shading to violet at the other."""
    t = (x + 0.5) / w * 0.7 + (y + 0.5) / h * 0.3 + phase
    band = max(0.0, 1.0 - abs(t - 0.5) / 0.45)
    band = smooth(band) * strength
    tint = lerp(SHEEN_BLUE, SHEEN_VIOLET, smooth((x + 0.5) / w))
    return lerp(base, tint, band)


def plume(top=0.15, bottom=0.6, gloss=0.0, phase=0.0):
    """Plain plumage: a vertical ramp down the face, with an optional sheen band."""
    def f(x, y, w, h):
        c = ramp(PLUME, top + (bottom - top) * (y + 0.5) / h)
        return sheen(c, x, y, w, h, gloss, phase) if gloss else c
    return f


def dull(top=0.2, bottom=0.8):
    return lambda x, y, w, h: ramp(DULL, top + (bottom - top) * (y + 0.5) / h)


def scallops(base_f, period=4, row=3, edge=1.18, offset=0):
    """Covert feathers: rows of rounded tips, each tip's lower edge a step lighter (a regular scale pattern)."""
    def f(x, y, w, h):
        c = base_f(x, y, w, h)
        r = (y + offset) // row
        sx = (x + (period // 2 if r % 2 else 0)) % period
        # The tip's curve: the lighter rim sits lower in the middle of each feather than at its sides.
        rim = row - 1 - (0 if sx in (1, 2) else 1)
        if (y + offset) % row == rim:
            return shade(c, edge)
        if (y + offset) % row == rim - 1 and sx in (1, 2):
            return shade(c, 1.07)
        return c
    return f


def stripes(base_f, period=3, along_x=False, light=1.2, dark=0.82, tip=None):
    """Flight feathers: long feathers side by side, each with a pale shaft and a dark edge to its neighbour."""
    def f(x, y, w, h):
        c = base_f(x, y, w, h)
        a = y if along_x else x
        k = a % period
        if k == period - 1:
            c = shade(c, dark)
        elif k == period // 2 - (0 if period > 2 else 0) and period > 2:
            c = shade(c, light)
        if tip is not None and tip(x, y, w, h):
            c = shade(c, 0.78)
        return c
    return f


def scutes(base_cols, top=0.1, bottom=0.6, band=2):
    """Scaly legs and toes: a ramp crossed by regular lighter scute lines."""
    def f(x, y, w, h):
        c = ramp(base_cols, top + (bottom - top) * (y + 0.5) / h)
        return shade(c, 1.22) if y % band == 0 else c
    return f


def over(f, pixels):
    return lambda x, y, w, h: pixels.get((x, y), f(x, y, w, h))


def eye(front_col, w, h):
    """The eye on a skull side (8x8 texels): a dark iris ringed by a faint rim, the pupil, one glint toward the front."""
    fc = front_col
    step = -1 if fc > w / 2 else 1
    ex, ey = fc + 2 * step, 2
    px = {}
    for dx in (-1, 0, 1, 2):
        for dy in (-1, 0, 1, 2):
            px[(ex + dx * step, ey + dy)] = EYE_RIM
    for dx in (0, 1):
        for dy in (0, 1):
            px[(ex + dx * step, ey + dy)] = IRIS
    px[(ex + step, ey + 1)] = PUPIL
    px[(ex, ey + 1)] = PUPIL
    px[(ex + step, ey)] = PUPIL
    px[(ex, ey)] = GLINT
    # the rim's corners rounded off
    for dx, dy in ((-1, -1), (2, -1), (-1, 2), (2, 2)):
        px.pop((ex + dx * step, ey + dy), None)
    return px


def paint_crow():
    tex = Canvas(TEX_W * S, TEX_H * S)
    P = lambda key, painters: paint(tex, key, painters)
    under = dull(0.45, 0.9)

    # Body: the back glossiest, the flanks a ramp darkening down, the underparts dull.
    P("torso", {"top": plume(0.05, 0.25, 0.4), "right": plume(0.2, 0.75, 0.2), "left": plume(0.2, 0.75, 0.2, 0.3),
                "front": plume(0.25, 0.7, 0.15), "back": plume(0.25, 0.7, 0.1), "bottom": under})
    P("mantle", {"top": scallops(plume(0.0, 0.2, 0.5), period=4, row=3, edge=1.12), "default": plume(0.1, 0.4, 0.3),
                 "bottom": under})
    P("chest", {"front": scallops(plume(0.3, 0.6, 0.15), period=4, row=3, edge=1.12), "default": plume(0.3, 0.7, 0.1),
                "bottom": under, "top": plume(0.2, 0.3)})
    P("belly", {"default": dull(0.3, 0.8)})
    P("rump", {"default": plume(0.25, 0.7, 0.15), "top": plume(0.1, 0.2, 0.4), "bottom": under})

    # Head: a glossy crown, the eye a little back from the beak, the throat's shaggy hackles in short regular strokes.
    def skull_side(front_last):
        def f(x, y, w, h):
            c = plume(0.1, 0.55, 0.35)(x, y, w, h)
            fc = w - 1 if front_last else 0
            px = eye(fc - (2 if front_last else -2) * 0, w, h)
            return px.get((x, y), c)
        return f
    skull_right = skull_side(True)
    skull_left = skull_side(False)
    P("skull", {"top": plume(0.0, 0.2, 0.75), "right": skull_right, "left": skull_left, "front": plume(0.15, 0.5, 0.2),
                "back": plume(0.2, 0.6, 0.25), "bottom": under})
    P("crown", {"default": plume(0.0, 0.2, 0.8, 0.1), "bottom": under})
    P("nape", {"default": scallops(plume(0.1, 0.5, 0.4), period=3, row=3, edge=1.12), "bottom": under})

    def hackles(x, y, w, h):
        c = ramp(PLUME, 0.25 + 0.4 * (y + 0.5) / h)
        sway = (0, 0, 1, 1)[(y // 2) % 4]
        return shade(c, 1.16) if (x + sway) % 3 == 0 else c
    P("throat", {"default": hackles, "top": plume(0.3, 0.4), "bottom": dull(0.4, 0.7)})

    # Beak: slate grey, a pale ridge down the top, darker down the sides to a dark cutting edge; the hook darker still.
    def beak_top(x, y, w, h):
        mid = abs((x + 0.5) - w / 2) / (w / 2)
        return ramp(BEAK, 0.05 + 0.5 * mid + 0.15 * (h - 1 - y) / max(1, h - 1))

    def beak_side(x, y, w, h):
        c = ramp(BEAK, 0.25 + 0.5 * (y + 0.5) / h)
        return BEAK[4] if y == h - 1 else c
    P("beak", {"top": beak_top, "right": beak_side, "left": beak_side, "front": beak_side, "back": beak_side,
               "bottom": lambda x, y, w, h: BEAK[3]})
    P("beak_mid", {"top": beak_top, "default": beak_side, "bottom": lambda x, y, w, h: BEAK[3]})
    P("beak_tip", {"default": lambda x, y, w, h: ramp(BEAK, 0.45 + 0.4 * (y + 0.5) / h), "top": beak_top,
                   "bottom": lambda x, y, w, h: BEAK[4]})
    P("jaw", {"default": lambda x, y, w, h: ramp(BEAK, 0.35 + 0.35 * (y + 0.5) / h),
              "top": lambda x, y, w, h: BEAK[4], "bottom": lambda x, y, w, h: ramp(BEAK, 0.4 + 0.2 * (y + 0.5) / h)})

    def bristle(x, y, w, h):
        return shade(ramp(PLUME, 0.3), 1.2) if x % 2 == 0 else ramp(PLUME, 0.5)
    P("bristles", {"default": bristle, "bottom": under})

    # Wings. Arms: coverts in scalloped rows along the leading edge, the secondaries striped behind them. Hands: long
    # primaries striped along the span; the fingers are single primaries, each a ramp to a darker tip with a pale shaft.
    # Both broad faces are painted: the outer one shows folded, the upper one in flight.
    for k, side in (("r", -1), ("l", 1)):
        def arm_face(gloss, side=side):
            covert = scallops(plume(0.05, 0.25, gloss, 0.2), period=4, row=3)
            secondaries = stripes(plume(0.15, 0.45, gloss * 0.6), period=3, light=1.15)

            def f(x, y, w, h):
                # rows: 0 = trailing edge, h-1 = leading edge
                return covert(x, y, w, h) if y >= h * 0.55 else secondaries(x, y, w, h)
            return f

        def prim_face(gloss, side=side):
            def f(x, y, w, h):
                # x runs along the span: toward the tip it's -x for the right wing (u 0), +x for the left (u w-1).
                out = (w - 1 - x) / (w - 1) if side < 0 else x / (w - 1)
                c = ramp(PLUME, 0.15 + 0.35 * out)
                c = sheen(c, x, y, w, h, gloss * (1.0 - out))
                k_ = y % 3
                return shade(c, 0.8) if k_ == 2 else shade(c, 1.14) if k_ == 1 and out < 0.85 else c
            return f

        def finger(gloss, side=side):
            def f(x, y, w, h):
                out = (w - 1 - x) / max(1, w - 1) if side < 0 else x / max(1, w - 1)
                c = ramp(PLUME, 0.2 + 0.55 * out)
                return shade(c, 1.15) if y == h // 2 - 1 and out < 0.9 else c
            return f
        edge = plume(0.1, 0.3, 0.2)
        P(f"wing_{k}_arm", {"top": arm_face(0.45), "bottom": arm_face(0.35), "default": edge})
        P(f"wing_{k}_covert", {"top": scallops(plume(0.05, 0.25, 0.45), period=4, row=3, edge=1.12), "bottom": dull(),
                              "default": plume(0.15, 0.35, 0.15)})
        P(f"wing_{k}_prim", {"top": prim_face(0.35), "bottom": prim_face(0.25), "default": edge})
        for n in (1, 2, 3):
            P(f"wing_{k}_f{n}", {"top": finger(0.3), "bottom": finger(0.2), "default": lambda x, y, w, h: ramp(PLUME, 0.6)})

    # Tail: a long centre feather and two side ones, each with a pale shaft and a darker rounded tip; dull underneath.
    def tail_feather(x, y, w, h):
        # rows: front edge last, so the tip (back) is row 0
        c = ramp(PLUME, 0.15 + 0.4 * (h - 1 - y) / max(1, h - 1))
        c = sheen(c, x, y, w, h, 0.3, 0.2)
        if w >= 4 and x in (w // 2 - 1, w // 2):
            c = shade(c, 1.13)
        if y == 0 and (x == 0 or x == w - 1):
            return None
        return shade(c, 0.85) if y <= 1 else c
    P("tail_mid", {"top": tail_feather, "bottom": dull(0.4, 0.8), "default": plume(0.4, 0.6)})
    P("tail_side", {"top": tail_feather, "bottom": dull(0.4, 0.8), "default": plume(0.45, 0.65)})
    P("tail_covert", {"top": scallops(plume(0.0, 0.3, 0.5), period=3, row=3), "default": plume(0.2, 0.5, 0.2),
                      "bottom": under})

    # Legs: feathered thighs, scaly shanks and toes, dark claws at the toe tips.
    P("thigh", {"default": plume(0.35, 0.7, 0.0), "top": plume(0.3, 0.4), "bottom": dull()})
    shank = scutes(LEG, 0.1, 0.5)
    P("shank", {"default": shank, "top": lambda x, y, w, h: LEG[1], "bottom": lambda x, y, w, h: LEG[3]})

    def toe(claw_rows):
        def top(x, y, w, h):
            # the front (claw) end is the last row
            return CLAW if y >= h - claw_rows else (shade(ramp(LEG, 0.2), 1.18) if y % 2 == 0 else ramp(LEG, 0.3))
        return {"top": top, "front": lambda x, y, w, h: CLAW, "bottom": lambda x, y, w, h: LEG[3],
                "right": lambda x, y, w, h: CLAW if x >= w - claw_rows else ramp(LEG, 0.35),
                "left": lambda x, y, w, h: CLAW if x < claw_rows else ramp(LEG, 0.35),
                "back": lambda x, y, w, h: ramp(LEG, 0.3)}
    P("toe_mid", toe(1))
    P("toe_side", toe(1))
    back = toe(1)
    # the hind toe's claw is at its back end
    back["top"] = lambda x, y, w, h: CLAW if y == 0 else (shade(ramp(LEG, 0.2), 1.18) if y % 2 == 0 else ramp(LEG, 0.3))
    back["front"] = lambda x, y, w, h: ramp(LEG, 0.3)
    back["back"] = lambda x, y, w, h: CLAW
    back["right"] = lambda x, y, w, h: CLAW if x == 0 else ramp(LEG, 0.35)
    back["left"] = lambda x, y, w, h: CLAW if x == w - 1 else ramp(LEG, 0.35)
    P("toe_back", back)
    return tex


TEXTURE = paint_crow()
TEXTURE.save("entity/crow/crow.png")


# ---------------------------------------------------------------------------------------------- posing
def rest_pose(model):
    return {name: [list(pivot), list(rot)] for name, _, pivot, rot, _ in model}


def pose(model, fly=0.0, flap=0.0, strength=0.0, peck=0.0, caw=0.0, sleeping=False, yaw=0.0, pitch=0.0, flight_pitch=0.0,
         age=0.0):
    """Python twin of CrowModel.pose."""
    p = rest_pose(model)
    sin = math.sin
    f = fly
    tuck = max(0.0, min(1.0, (f - 0.4) / 0.6))
    amp = 0.25 + 0.75 * strength
    beat = sin(flap)
    lag = sin(flap - 0.8)
    pk = sin(peck * math.pi)
    cw = sin(caw * math.pi)
    lerp_ = lambda t, a, b: a + (b - a) * t
    p["body"][1][0] = lerp_(f, -0.35, 0.05) + flight_pitch * f + 0.55 * pk + 0.18 * cw
    p["body"][0][1] += 0.25 * sin(age * 0.08) * (1.0 - f)
    p["head"][1][0] = lerp_(f, 0.3, -0.05) + pitch + 0.6 * pk - 0.42 * cw
    p["head"][1][1] = yaw
    p["jaw"][1][0] = 0.55 * cw + 0.12 * pk
    if sleeping:
        p["head"][1][0] += 0.45
        p["head"][0][1] += 1.0
        p["head"][0][2] += 1.0
        p["body"][0][1] += 0.5
    p["tail"][1][0] += -0.25 * f - 0.35 * cw
    p["tail"][1][1] = 0.06 * sin(age * 0.11) * (1.0 - f)
    p["tail_l"][1][1] = lerp_(f, -0.2, 0.35)
    p["tail_r"][1][1] = lerp_(f, 0.2, -0.35)
    spread = 1.0 - f
    stroke = f * (0.1 + 0.65 * amp * beat)
    p["right_wing"][1] = [0.0, FOLD_Y * spread, FOLD_Z * spread + stroke - 0.12 * cw * spread]
    p["left_wing"][1] = [0.0, -FOLD_Y * spread, -FOLD_Z * spread - stroke + 0.12 * cw * spread]
    p["right_hand"][1] = [0.0, 0.18 * f, f * 0.4 * amp * lag]
    p["left_hand"][1] = [0.0, -0.18 * f, -f * 0.4 * amp * lag]
    # The fingers close into one point folded, and fan apart in flight.
    for side, s_ in (("right", -1), ("left", 1)):
        p[f"{side}_finger1"][1][1] = -s_ * lerp_(f, 0.25, -0.12)
        p[f"{side}_finger3"][1][1] = -s_ * lerp_(f, -0.25, 0.12)
    for s in ("right", "left"):
        p[f"{s}_leg"][1][0] = 1.25 * tuck
        p[f"{s}_foot"][1][0] = 0.8 * tuck
    return p


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


IDENT = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def placed_cubes(model, p):
    t = tree(model)
    cubes = {name: c for name, _, _, _, c in model}
    out = []

    def walk(name, matrix, origin):
        for child in t.get(name, []):
            pivot, rot = p[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for k, (key, frm, size) in enumerate(cubes[child]):
                out.append((child, k, key, frm, size, m, o))
            walk(child, m, o)
    walk("root", IDENT, (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size):
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d
    P = lambda x, y, z: tuple(o[i] + apply(m, (x, y, z))[i] for i in range(3))
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    polys = [([l1, l0, t0, t1], u1, v0, u2, v1, "down"), ([t2, t3, l3, l2], u2, v1, u22, v0, "up"),
             ([t0, l0, l3, t3], u0, v1, u1, v2, "west"), ([t1, t0, t3, t2], u1, v1, u2, v2, "north"),
             ([l1, t1, t2, l2], u2, v1, u3, v2, "east"), ([l0, l1, l2, l3], u3, v1, u4, v2, "south")]
    return [(verts, (a0, b0, a1, b1), name) for verts, a0, b0, a1, b1, name in polys]


def quads(model, p, labels=False):
    out = []
    for part, k, key, frm, size, m, o in placed_cubes(model, p):
        (u, v), _ = CUBES[key]
        centre = tuple(o[i] + apply(m, tuple(frm[j] + size[j] / 2 for j in range(3)))[i] for i in range(3))
        for verts, uv, name in cube_faces(m, o, u, v, frm, size):
            if labels:
                fc = tuple(sum(q[i] for q in verts) / 4 for i in range(3))
                out.append((verts, uv, f"{part}.{key}{k}.{name}", tuple(fc[i] - centre[i] for i in range(3))))
            else:
                out.append((verts, uv))
    return out


from coplanar import Face, fights


def check(label, p):
    faces_ = []
    for verts, uv, name, n in quads(MODEL, p, labels=True):
        faces_.append(Face(name, verts, n))
    hits = fights(faces_)
    for h in hits:
        print(f"z-fight ({label}):", h)
    return len(hits)


POSES = [("rest", {}), ("peck", {"peck": 0.5}), ("caw", {"caw": 0.5}), ("sleep", {"sleeping": True}),
         ("hop", {"fly": 0.35}), ("look", {"yaw": 0.7, "pitch": -0.3})]
POSES += [(f"flap {a:.1f}", {"fly": 1.0, "flap": a, "strength": 0.8}) for a in [i * 0.5 for i in range(13)]]
POSES += [(f"unfold {f_:.2f}", {"fly": f_, "flap": 1.0, "strength": 0.5}) for f_ in (0.15, 0.3, 0.5, 0.7, 0.85)]
POSES += [("glide", {"fly": 1.0, "flap": 0.4, "strength": 0.0, "flight_pitch": 0.2})]
_bad = sum(check(label, pose(MODEL, **kw)) for label, kw in POSES)
if _bad:
    raise SystemExit(f"crow: {_bad} coplanar overlapping face pairs")


def extents(p):
    pts = [q for verts, _ in quads(MODEL, p) for q in verts]
    return [(min(q[i] for q in pts), max(q[i] for q in pts)) for i in range(3)]


EXT = extents(pose(MODEL))
print(f"crow: {sum(len(c) for _, _, _, _, c in MODEL)} cubes; x {EXT[0][0]:.1f}..{EXT[0][1]:.1f}, top y {EXT[1][0]:.1f} "
      f"({GROUND - EXT[1][0]:.1f} px tall), bottom y {EXT[1][1]:.2f}, z {EXT[2][0]:.1f}..{EXT[2][1]:.1f}")
_fe = extents(pose(MODEL, fly=1.0, flap=math.pi / 2, strength=0.0))
print(f"crow in flight: wingspan {_fe[0][1] - _fe[0][0]:.1f} px")


# ---------------------------------------------------------------------------------------------- the Java model
JAVA = os.path.normpath(os.path.join(ASSETS, "..", "..", "..", "..", "client", "java", "com", "jheinz", "tomyen", "client",
                                     "crow", "CrowModel.java"))


def f2(v):
    return f"{v:.4f}f".replace("-0.0000f", "0.0000f")


def java_layer():
    t = tree(MODEL)
    lines = ["\tpublic static LayerDefinition createBodyLayer() {", "\t\tMeshDefinition mesh = new MeshDefinition();",
             "\t\tPartDefinition root = mesh.getRoot();"]
    for name, parent, pivot, rot, cubes in MODEL:
        var = "p_" + name
        builder = "CubeListBuilder.create()"
        for key, frm, size in cubes:
            (u, v), _ = CUBES[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({', '.join(f2(c) for c in frm)}, {', '.join(f2(c) for c in size)})"
        pose_ = (f"PartPose.offsetAndRotation({', '.join(f2(c) for c in pivot)}, {', '.join(f2(c) for c in rot)})"
                 if any(rot) else f"PartPose.offset({', '.join(f2(c) for c in pivot)})")
        used = t.get(name) is not None
        lines.append(f"\t\t{'PartDefinition ' + var + ' = ' if used else ''}{('p_' + parent) if parent else 'root'}"
                     f".addOrReplaceChild(\"{name}\", {builder},\n\t\t\t\t{pose_});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    lines.append("\t}")
    return "\n".join(lines)


src = open(JAVA).read()
start, end = "\t// BEGIN GENERATED by tools/gen_crow.py\n", "\t// END GENERATED\n"
a, b = src.index(start) + len(start), src.index(end)
src = src[:a] + java_layer() + "\n" + src[b:]
with open(JAVA, "w") as out:
    out.write(src)


# ---------------------------------------------------------------------------------------------- the nest
# Block textures, 16x16, in the vanilla style: twigs from the stick's bark browns, a lining of black feathers and dry
# grass, and the treasure (gold, silver and an emerald's greens).
STICK = [(140, 112, 76), (114, 90, 60), (90, 70, 46), (66, 50, 33), (44, 33, 22)]
GRASS = [(150, 132, 84), (126, 108, 66), (102, 86, 52), (78, 64, 40)]
GOLD = [(255, 250, 170), (252, 222, 88), (226, 176, 36), (168, 118, 20)]
SILVER = [(250, 252, 255), (210, 216, 228), (164, 172, 188), (116, 122, 140)]
EMERALD = [(190, 255, 214), (64, 222, 128), (22, 160, 80), (10, 98, 50)]


def twig_texture():
    """Woven twigs: bark-brown strands running across, each a two-texel twig lit along its top and shaded below, the
    rows staggered where they pass over and under one another, with a few knots."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            band = y // 3
            k = y % 3
            # every other row of twigs dips a texel as it passes under its neighbour
            weave = 1 if (x // 4 + band) % 2 == 0 and k == 0 else 0
            if k == 2 or weave:
                col = STICK[4] if k == 2 else STICK[3]
            else:
                col = STICK[1] if k == 0 else STICK[2]
            # a pale end-grain nick every so often along each twig
            if k == 0 and not weave and (x + band * 5) % 7 == 0:
                col = STICK[0]
            c.set(x, y, col)
    return c


def lining_texture():
    """The bowl's lining (the middle 8x8, all the bowl shows): dry grass laid round in arcs, with two black feathers
    tucked in, each a sheened vane either side of a pale shaft. The strip down the right edge (x 12-15) is a single
    feather drawn upright, for the one caught in the rim."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(12):
            r = math.hypot(x - 7.5, y - 7.5)
            k = int(r * 1.4) % 3
            c.set(x, y, GRASS[1] if k == 0 else GRASS[2] if k == 1 else GRASS[3])
    for (x0, y0, n) in ((4, 5, 5), (7, 10, 4)):
        for i in range(n):
            x, y = x0 + i, y0 - i // 2
            t = i / (n - 1)
            c.set(x, y, shade(lerp(PLUME[1], SHEEN_BLUE, 0.35 * (1 - t)), 1.0))
            c.set(x, y + 1, (120, 126, 168) if i < n - 1 else PLUME[1])
            c.set(x, y + 2, PLUME[3])
    # The upright feather: shaft at x 13, a broad vane to its left, a narrow one to its right, quill at the bottom.
    for y in range(16):
        t = y / 15.0  # 0 at the tip, 1 at the quill
        for x in range(12, 16):
            if x == 13:
                col = (122, 128, 170) if t < 0.8 else (88, 90, 108)
            elif t >= 0.8:
                col = GRASS[3]
            elif x == 12:
                col = lerp(SHEEN_VIOLET, SHEEN_BLUE, t) if 0.1 < t < 0.75 else PLUME[1]
            elif x == 14:
                col = PLUME[3] if 0.06 < t < 0.75 else PLUME[2]
            else:
                col = PLUME[2]
            c.set(x, y, col)
    return c


def loot_texture():
    """Treasure swatches in four quadrants: gold, silver, emerald and a gold coin face; each a smooth ramp, lit top left."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            qx, qy = x // 8, y // 8
            lx, ly = x % 8, y % 8
            t = (lx + ly) / 14.0
            if (qx, qy) == (0, 0):
                col = ramp(GOLD, 0.1 + 0.8 * t)
            elif (qx, qy) == (1, 0):
                col = ramp(SILVER, 0.1 + 0.8 * t)
            elif (qx, qy) == (0, 1):
                col = ramp(EMERALD, 0.1 + 0.8 * t)
                if lx in (2, 5) or ly in (2, 5):
                    col = shade(col, 1.15)
            else:
                d = math.hypot(lx - 3.5, ly - 3.5)
                col = ramp(GOLD, 0.2 + 0.6 * t) if d < 3.0 else ramp(GOLD, 0.75)
                if 2.0 < d < 3.0:
                    col = ramp(GOLD, 0.05)
            c.set(x, y, col)
    return c


twig_texture().save("block/crow_nest_twigs.png")
lining_texture().save("block/crow_nest_lining.png")
loot_texture().save("block/crow_nest_loot.png")

from gunkit import euler, set_rotation  # noqa: E402


def box(name, frm, to, tex, rot=None, uv=None, faces_=("north", "south", "east", "west", "up", "down")):
    """A block model element; uv maps a face to its rect (defaults to the element's own extent on the texture)."""
    e = OrderedDict([("name", name), ("from", [round(c, 4) for c in frm]), ("to", [round(c, 4) for c in to])])
    fs = OrderedDict()
    for fname in faces_:
        if uv and fname in uv:
            r = uv[fname]
        else:
            ax = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}[fname]
            a0, a1 = frm[ax[0]] % 16, to[ax[0]] - frm[ax[0]]
            b0, b1 = frm[ax[1]] % 16, to[ax[1]] - frm[ax[1]]
            r = [a0, b0, min(16, a0 + a1), min(16, b0 + b1)]
        fs[fname] = {"uv": [round(c, 4) for c in r], "texture": tex}
    e["faces"] = fs
    if rot:
        x, y, z, origin = rot
        set_rotation(e, euler(x, y, z), origin)
    return e


def nest_elements(loot):
    E = []
    # The floor of the bowl and its lining.
    E.append(box("floor", (3, 0, 3), (13, 1, 13), "#twigs"))
    E.append(box("lining", (4, 1, 4), (12, 1.5, 12), "#lining", faces_=("up",)))
    # First course: four twig walls round the floor, butting at the corners.
    E.append(box("wall_n", (2, 0.5, 2), (14, 3, 4), "#twigs"))
    E.append(box("wall_s", (2, 0.5, 12), (14, 3, 14), "#twigs"))
    E.append(box("wall_w", (2, 0.5, 4), (4, 3, 12), "#twigs"))
    E.append(box("wall_e", (12, 0.5, 4), (14, 3, 12), "#twigs"))
    # Second course, turned a quarter of a right angle so the rim reads as round, not square.
    for i, (frm, to) in enumerate((((3, 3, 2.5), (13, 5, 4.5)), ((3, 3, 11.5), (13, 5, 13.5)), ((2.5, 3, 4.5), (4.5, 5, 11.5)),
                                   ((11.5, 3, 4.5), (13.5, 5, 11.5)))):
        E.append(box(f"rim_{i}", frm, to, "#twigs", rot=(0, 45, 0, [8, 4, 8])))
    # Loose sticks laid round the rim, each a chord of the ring with its ends poking out past the walls, tilted a little
    # as they pass over and under one another; the bowl itself stays open.
    for i, (angle, r, y, length, tilt) in enumerate(((10, 5.6, 4.6, 12, 6), (70, 5.4, 3.8, 11, -8), (130, 5.7, 4.8, 13, 5),
                                                     (195, 5.5, 4.0, 12, -6), (255, 5.6, 4.7, 11, 7), (315, 5.4, 3.9, 13, -5))):
        rad = math.radians(angle)
        cx, cz = 8 + r * math.cos(rad), 8 + r * math.sin(rad)
        # the stick runs along x before turning: a y turn of (90 - angle) lines it up with the ring's tangent there
        E.append(box(f"stick_{i}", (cx - length / 2, y - 0.5, cz - 0.5), (cx + length / 2, y + 0.5, cz + 0.5), "#twigs",
                     rot=(0, 90 - angle, tilt, [cx, y, cz])))
    # A black feather caught in the rim, lying back across it.
    E.append(box("feather", (10.5, 4.2, 4), (12.5, 4.7, 11), "#lining", rot=(18, -28, 0, [11.5, 4.45, 7.5]),
                 uv={"up": [12, 1, 15, 15], "down": [15, 1, 12, 15], "north": [12, 1, 15, 2], "south": [12, 14, 15, 15],
                     "east": [13, 1, 14, 15], "west": [13, 1, 14, 15]}))
    if loot:
        E.append(box("gold_ingot", (5, 1.5, 6.5), (9, 2.5, 8.5), "#loot", rot=(0, 30, 0, [7, 2, 7.5]),
                     uv={"up": [0, 0, 8, 4], "north": [0, 4, 8, 6], "south": [0, 4, 8, 6], "east": [0, 4, 4, 6],
                         "west": [0, 4, 4, 6], "down": [0, 4, 8, 8]}))
        E.append(box("silver_ingot", (7.5, 1.5, 8.5), (11, 2.5, 10.5), "#loot", rot=(0, -12, 4, [9, 2, 9.5]),
                     uv={"up": [8, 0, 15, 4], "north": [8, 4, 15, 6], "south": [8, 4, 15, 6], "east": [8, 4, 12, 6],
                         "west": [8, 4, 12, 6], "down": [8, 4, 15, 8]}))
        E.append(box("emerald", (5.5, 1.5, 9), (7.5, 4, 11), "#loot", rot=(10, 45, 0, [6.5, 2.5, 10]),
                     uv={k: [0, 8, 4, 13] for k in ("north", "south", "east", "west")} | {"up": [0, 8, 4, 12], "down": [0, 8, 4, 12]}))
        E.append(box("coin", (9, 1.5, 5), (11.5, 2.0, 7.5), "#loot", rot=(0, 0, -12, [10, 1.75, 6]),
                     uv={"up": [8, 8, 16, 16], "down": [8, 8, 16, 16], "north": [8, 12, 16, 13], "south": [8, 12, 16, 13],
                         "east": [8, 12, 16, 13], "west": [8, 12, 16, 13]}))
        E.append(box("nugget_a", (8, 2.5, 7), (9.5, 3.5, 8.5), "#loot", rot=(0, 20, 0, [8.75, 3, 7.75]),
                     uv={k: [1, 1, 4, 3] for k in ("north", "south", "east", "west", "up", "down")}))
        E.append(box("nugget_b", (6.0, 1.5, 4.2), (7.5, 2.75, 5.7), "#loot", uv={k: [9, 1, 12, 3] for k in ("north", "south", "east", "west", "up", "down")}))
    return E


from coplanar import check_elements  # noqa: E402
NEST = nest_elements(False)
NEST_LOOT = nest_elements(True)
check_elements("crow nest", NEST)
check_elements("crow nest (loot)", NEST_LOOT)
print(f"crow nest: {len(NEST)} elements ({len(NEST_LOOT)} with loot)")


# ---------------------------------------------------------------------------------------------- item sprites
def vanilla_sprite(name):
    """A vanilla item sprite, read from the client jar (via the Gradle cache), or None if it isn't there."""
    import zipfile
    jar = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
    if not os.path.exists(jar):
        return None
    with zipfile.ZipFile(jar) as z:
        data = z.read(f"assets/minecraft/textures/item/{name}.png")
    tmp = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".__sprite.png")
    with open(tmp, "wb") as f_:
        f_.write(data)
    try:
        return read_png(tmp)
    finally:
        os.remove(tmp)


def crow_feather():
    """A crow's flight feather, laid on the same diagonal as vanilla's but drawn solid, not speckled: a pale steel
    shaft from a bare grey quill to the tip, a broad vane on its upper side (dark at the shaft, its outer edge lit
    in a sheen running from blue at the base to violet at the tip, split once where the barbs part) and a narrow
    shaded vane below. Longer and slimmer than the chicken's, and black."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            s_ = x + y - 15  # 0 on the shaft, negative above-left of it
            t = (x - y + 11) / 22.0  # 0 at the quill (2, 13), 1 at the tip (13, 2)
            if not (-0.01 <= t <= 1.01):
                continue
            if s_ == 0:
                col = (90, 92, 110) if t < 0.18 else lerp((136, 142, 184), (110, 100, 164), (t - 0.18) / 0.82)
            elif t < 0.18 or t > 0.93:
                continue
            elif s_ < 0:
                u = (t - 0.18) / 0.75
                width = 1 if u < 0.06 or u > 0.9 else 2 if u < 0.16 or u > 0.78 else 3 if u < 0.3 or u > 0.62 else 4
                if -s_ > width:
                    continue
                if -s_ == width and abs(u - 0.5) < 0.04:
                    continue  # where the barbs part
                col = lerp(SHEEN_BLUE, SHEEN_VIOLET, u) if -s_ == width else PLUME[1] if -s_ == 1 else PLUME[2]
            else:
                u = (t - 0.18) / 0.75
                lower = 0 if u > 0.88 else 1 if u < 0.15 or u > 0.7 else 2
                if s_ > lower:
                    continue
                col = PLUME[3] if s_ == lower else PLUME[2]
            c.set(x, y, col)
    return c


def crow_fly():
    """The Feather Lure's layout (the mod's own sprite), tied in crow: a black hackle collar splayed behind the hook's
    eye, a black feather wing, a ribbed silver body with a red tag, on a bright steel hook."""
    w, h, base = read_png(os.path.join(TEX, "item", "feather_lure.png"))
    c = Canvas(16, 16)
    remap = {(138, 106, 72): PLUME[1], (232, 220, 200): (126, 134, 182), (62, 42, 24): PLUME[4], (90, 58, 30): PLUME[2],
             (106, 74, 42): lerp(PLUME[1], SHEEN_VIOLET, 0.35), (200, 58, 42): SILVER[2], (255, 106, 74): SILVER[0],
             (200, 200, 200): (214, 218, 226)}
    for y in range(16):
        for x in range(16):
            p = base[y * w + x]
            if p[3] == 0:
                continue
            col = remap.get(p[:3], p[:3])
            c.set(x, y, col)
    # The body's ribbing: alternate silver rows; the red tag at the bend of the hook.
    for (x, y) in ((5, 11), (6, 12)):
        c.set(x, y, SILVER[3])
    c.set(4, 12, (196, 40, 40))
    c.set(5, 12, (150, 28, 30))
    # The hackle: fibres splayed out either side of the collar.
    for (x, y, col) in ((4, 9, PLUME[2]), (3, 9, PLUME[3]), (7, 9, PLUME[2]), (8, 10, PLUME[3]), (4, 8, SHEEN_BLUE)):
        if c.get(x, y)[3] == 0:
            c.set(x, y, col)
    return c


def egg_sprite(base, spots, outline):
    """The shopping cart egg's outline, repainted: a smooth light-to-dark ramp across the shell, and spots."""
    w, h, cart = read_png(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"))
    mask = [p_[3] > 0 for p_ in cart]
    xs = [i % w for i in range(w * h) if mask[i]]
    ys = [i // w for i in range(w * h) if mask[i]]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    egg = Canvas(w, h)
    for i in range(w * h):
        if not mask[i]:
            continue
        x, y = i % w, i // w
        edge = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            egg.px[i] = rgba(outline)
            continue
        c = ramp(base, 0.5 - ((cx - x) + (cy - y)) / 14.0)
        egg.px[i] = rgba(spots.get((x, y), c))
    return egg


SPRITES = {
    "crow_feather": crow_feather(),
    "crow_fly": crow_fly(),
    "crow_spawn_egg": egg_sprite([(84, 88, 122), (58, 60, 88), (40, 42, 62), (26, 27, 42)],
                                 {(7, 4): SHEEN_BLUE, (8, 4): SHEEN_VIOLET, (6, 7): SHEEN_VIOLET, (9, 8): SHEEN_BLUE, (7, 10): BEAK[0],
                                  (8, 10): BEAK[1], (5, 11): SHEEN_BLUE, (10, 11): SHEEN_VIOLET}, (14, 14, 22)),
}
for name, sprite in SPRITES.items():
    sprite.save(f"item/{name}.png")


# ---------------------------------------------------------------------------------------------- json
MODELS = os.path.join(ASSETS, "models")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f_:
        json.dump(obj, f_, indent=2, ensure_ascii=False)
        f_.write("\n")


def merge(path, entries):
    """Adds entries to a JSON object file others also write (lang, sounds.json), keeping what's there and its order."""
    with open(path) as f_:
        obj = json.load(f_, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


for name in SPRITES:
    write(os.path.join(MODELS, "item", name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})

NEST_DISPLAY = OrderedDict([
    ("gui", {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]}),
    ("ground", {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]}),
    ("fixed", {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}),
    ("thirdperson_righthand", {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}),
    ("thirdperson_lefthand", {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}),
    ("firstperson_righthand", {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}),
    ("firstperson_lefthand", {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]})])
for name, elements in (("crow_nest", NEST), ("crow_nest_loot", NEST_LOOT)):
    textures = OrderedDict([("twigs", "tomyen:block/crow_nest_twigs"), ("lining", "tomyen:block/crow_nest_lining"),
                            ("particle", "tomyen:block/crow_nest_twigs")])
    if name.endswith("loot"):
        textures["loot"] = "tomyen:block/crow_nest_loot"
    write(os.path.join(MODELS, "block", name + ".json"), OrderedDict([
        ("parent", "minecraft:block/block"), ("ambientocclusion", False), ("textures", textures), ("elements", elements),
        ("display", NEST_DISPLAY)]))
write(os.path.join(ASSETS, "blockstates", "crow_nest.json"), {"variants": OrderedDict([
    ("loot=false", {"model": "tomyen:block/crow_nest"}), ("loot=true", {"model": "tomyen:block/crow_nest_loot"})])})
write(os.path.join(ASSETS, "items", "crow_nest.json"), {"model": {"type": "minecraft:model", "model": "tomyen:block/crow_nest"}})


def pool(item, low, high, looting=1.0, chance=None, player=False):
    """A loot pool of one item (26.3's format: an entry's functions are its "modifier", a pool's conditions one
    "condition", all_of for several)."""
    entry = OrderedDict([("type", "minecraft:item"), ("name", item)])
    mods = []
    if high > 1 or low != 1:
        mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}})
    if looting:
        mods.append({"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
                     "enchantment": "minecraft:looting"})
    if mods:
        entry["modifier"] = mods
    p_ = OrderedDict([("rolls", 1), ("entries", [entry])])
    conditions = []
    if player:
        conditions.append({"type": "minecraft:killed_by_player"})
    if chance is not None:
        conditions.append({"type": "minecraft:random_chance", "chance": chance})
    if len(conditions) == 1:
        p_["condition"] = conditions[0]
    elif conditions:
        p_["condition"] = {"type": "minecraft:all_of", "terms": conditions}
    return p_


write(os.path.join(DATA, "loot_table/entities/crow.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [pool("tomyen:crow_feather", 1, 2), pool("minecraft:feather", 0, 1, looting=0)]),
    ("random_sequence", "tomyen:entities/crow")]))
# The nest itself: its sticks and a feather or two (the hoard spills out on its own, as a container's does).
write(os.path.join(DATA, "loot_table/blocks/crow_nest.json"), OrderedDict([
    ("type", "minecraft:block"),
    ("pools", [
        OrderedDict([("rolls", 1), ("entries", [OrderedDict([("type", "minecraft:alternatives"), ("children", [
            OrderedDict([("type", "minecraft:item"), ("name", "tomyen:crow_nest"),
                         ("condition", {"type": "minecraft:match_tool", "predicate": {"predicates": {
                             "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}})]),
            OrderedDict([("type", "minecraft:item"), ("name", "minecraft:stick"),
                         ("modifier", [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}},
                                       {"type": "minecraft:explosion_decay"}])])])])])]),
        OrderedDict([("rolls", 1), ("entries", [OrderedDict([("type", "minecraft:item"), ("name", "tomyen:crow_feather"),
                                                             ("modifier", [{"type": "minecraft:set_count",
                                                                            "count": {"type": "minecraft:uniform", "min": 0, "max": 2}}])])]),
                     ("condition", {"type": "minecraft:inverted", "term": {"type": "minecraft:match_tool", "predicate": {"predicates": {
                         "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}})])]),
    ("random_sequence", "tomyen:blocks/crow_nest")]))

write(os.path.join(DATA, "tags/item/shiny.json"), OrderedDict([("values", [
    "minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:copper_ingot",
    "minecraft:copper_nugget", "minecraft:netherite_ingot", "minecraft:emerald", "minecraft:diamond", "minecraft:amethyst_shard",
    "tomyen:silver_ingot", "tomyen:silver_nugget", "tomyen:holy_silver_ingot", "tomyen:holy_silver_nugget", "tomyen:fae_silver_ingot",
    "tomyen:sunken_coin_pouch", "tomyen:steel_ingot", "tomyen:tungsten_ingot", "tomyen:tungsten_nugget", "tomyen:aluminium_ingot",
    "tomyen:duralumin_ingot", "tomyen:meteoric_iron_ingot", "tomyen:star_steel_ingot", "tomyen:star_crystal_shard",
    {"id": "#c:ingots", "required": False}, {"id": "#c:nuggets", "required": False}])]))

write(os.path.join(DATA, "recipe/crow_fly.json"), OrderedDict([
    ("type", "minecraft:crafting_shapeless"), ("category", "equipment"),
    ("ingredients", ["tomyen:crow_feather", "tomyen:crow_feather", "minecraft:iron_nugget", "minecraft:string"]),
    ("result", {"count": 1, "id": "tomyen:crow_fly"})]))
write(os.path.join(DATA, "recipe/arrow_from_crow_feather.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "equipment"), ("group", "arrow"),
    ("key", OrderedDict([("#", "minecraft:stick"), ("X", "minecraft:flint"), ("Y", "tomyen:crow_feather")])),
    ("pattern", ["X", "#", "Y"]), ("result", {"count": 6, "id": "minecraft:arrow"})]))
write(os.path.join(DATA, "recipe/crow_nest.json"), OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "misc"),
    ("key", OrderedDict([("#", "minecraft:stick"), ("F", "tomyen:crow_feather"), ("H", "minecraft:wheat")])),
    ("pattern", ["# #", "FHF", "###"]), ("result", {"count": 1, "id": "tomyen:crow_nest"})]))


def unlock(recipe, category, items):
    """The recipe advancement: unlocked on having any of the items."""
    criteria = OrderedDict((f"has_{i.split(':')[1]}", {"conditions": {"items": [{"items": i}]}, "trigger": "minecraft:inventory_changed"})
                           for i in items)
    criteria["has_the_recipe"] = {"conditions": {"recipe": f"tomyen:{recipe}"}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, f"advancement/recipes/{category}/{recipe}.json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria),
        ("requirements", [list(criteria.keys())]), ("rewards", {"recipes": [f"tomyen:{recipe}"]})]))


unlock("crow_fly", "tools", ["tomyen:crow_feather"])
unlock("arrow_from_crow_feather", "combat", ["tomyen:crow_feather"])
unlock("crow_nest", "misc", ["tomyen:crow_feather"])


def vanilla(names, pitch, volume=None, **extra):
    out = []
    for n in names:
        e = OrderedDict([("name", n if ":" in n else "minecraft:" + n), ("pitch", pitch)])
        if volume is not None:
            e["volume"] = volume
        e.update(extra)
        out.append(e)
    return out


def event(name, sounds, subtitle=True):
    e = OrderedDict()
    if subtitle:
        e["subtitle"] = f"subtitles.tomyen.{name}"
    e["sounds"] = sounds
    return name, e


CAWS = [f"tomyen:crow/caw{i}" for i in range(1, 5)]
merge(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    event("crow_caw", vanilla(CAWS, 1.0, 0.9, attenuation_distance=40)),
    event("crow_alarm", vanilla(CAWS, 1.12, 1.0, attenuation_distance=48)),
    event("crow_hurt", vanilla(["tomyen:crow/caw2", "tomyen:crow/caw4"], 1.3, 0.9)),
    event("crow_death", vanilla(["tomyen:crow/caw1", "tomyen:crow/caw3"], 0.8, 1.0)),
    event("crow_flap", vanilla(["mob/phantom/flap1", "mob/phantom/flap2", "mob/phantom/flap3", "mob/phantom/flap4"], 1.7, 0.45)),
    event("crow_peck", vanilla(["block/azalea_leaves/hit1", "block/azalea_leaves/hit2", "block/azalea_leaves/hit3"], 1.6, 0.5),
          subtitle=False),
    event("crow_snatch", vanilla(["random/pop"], 1.4, 0.8) + vanilla(["tomyen:crow/caw3"], 1.25, 0.9)),
    event("crow_nest_rustle", vanilla(["block/azalea_leaves/step1", "block/azalea_leaves/step2", "block/azalea_leaves/step3",
                                       "block/azalea_leaves/step4"], 0.8, 0.9)),
]))

merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.tomyen.crow", "Crow"),
    ("entity.tomyen.crow.snatched", "A crow snatched your %s!"),
    ("entity.tomyen.crow.defend", "The crows are defending their nest!"),
    ("item.tomyen.crow_spawn_egg", "Crow Spawn Egg"),
    ("item.tomyen.crow_feather", "Crow Feather"),
    ("item.tomyen.crow_fly", "Crow Fly"),
    ("block.tomyen.crow_nest", "Crow Nest"),
    ("container.tomyen.crow_nest", "Crow Nest"),
    ("subtitles.tomyen.crow_caw", "Crow caws"),
    ("subtitles.tomyen.crow_alarm", "Crows scatter, cawing"),
    ("subtitles.tomyen.crow_hurt", "Crow hurts"),
    ("subtitles.tomyen.crow_death", "Crow dies"),
    ("subtitles.tomyen.crow_flap", "Crow takes off"),
    ("subtitles.tomyen.crow_snatch", "Crow snatches something"),
    ("subtitles.tomyen.crow_nest_rustle", "Crow nest rustles"),
]))


# ---------------------------------------------------------------------------------------------- the caws
# With --sounds: four caws built from vanilla sounds (read from the Gradle asset cache) into sounds/crow/caw1-4.ogg. A
# crow's caw is a harsh, nasal "kraa": a dense harmonic stack a few hundred hertz up, rough with noise, a third to half
# a second long, falling a little at the end. The parrot's squawks (its death and hurt calls) have the stack and the
# roughness at three times the pitch and a third the length; slowed and lowered by resampling they land in the
# crow's range. Each caw is one or two of them, a fox's screech under some for grit, band-limited, with a lift round
# 1.3 kHz for the nasal tone, and a dip in pitch over its last third (the tail resampled lower still). Needs ffmpeg;
# written mono by vorbis_mono.py (Minecraft only positions mono sounds).
CAW_RATE = 44100
CAW_SPECS = [
    # (layers: (vanilla sound, start s, length s, pitch, gain dB)), length after shaping (s), fall at the end
    ([("mob/parrot/death1", 0.0, 0.26, 0.68, 0.0)], 0.38, 0.9),
    ([("mob/parrot/death2", 0.0, 0.30, 0.72, 0.0), ("mob/fox/screech1", 0.02, 0.22, 1.2, -9.0)], 0.42, 0.88),
    ([("mob/parrot/hurt1", 0.0, 0.14, 0.6, 0.0), ("mob/parrot/hurt2", 0.0, 0.18, 0.62, -4.0)], 0.30, 0.92),
    ([("mob/parrot/death1", 0.0, 0.26, 0.62, 0.0), ("mob/fox/screech3", 0.02, 0.24, 1.1, -10.0)], 0.44, 0.86),
]


def build_caws():
    import array
    import subprocess
    import vorbis_mono
    index = os.path.expanduser("~/.gradle/caches/fabric-loom/assets/indexes/26.3-34.json")
    objects = json.load(open(index))["objects"]

    def ff(args, data=None):
        return subprocess.run(["ffmpeg", "-v", "error", "-y"] + args, input=data, capture_output=True, check=True).stdout

    def source(name):
        h = objects[f"minecraft/sounds/{name}.ogg"]["hash"]
        return os.path.expanduser(f"~/.gradle/caches/fabric-loom/assets/objects/{h[:2]}/{h}")

    def layer(name, start, dur, pitch):
        raw = ff(["-ss", f"{start:.3f}", "-t", f"{dur:.3f}", "-i", source(name), "-ac", "1", "-af",
                  f"aresample={CAW_RATE},asetrate={CAW_RATE * pitch:.0f},aresample={CAW_RATE}", "-f", "f32le", "-"])
        return array.array("f", raw)

    def filt(xs, chain):
        return array.array("f", ff(["-f", "f32le", "-ar", str(CAW_RATE), "-ac", "1", "-i", "-", "-af", chain, "-f", "f32le", "-"],
                                   xs.tobytes()))

    out_dir = os.path.join(ASSETS, "sounds", "crow")
    os.makedirs(out_dir, exist_ok=True)
    for n, (layers, length, fall) in enumerate(CAW_SPECS, 1):
        mixed = array.array("f")
        for name, start, dur, pitch, gain in layers:
            xs = layer(name, start, dur, pitch)
            g = 10 ** (gain / 20)
            if len(xs) > len(mixed):
                mixed.extend([0.0] * (len(xs) - len(mixed)))
            for i, x in enumerate(xs):
                mixed[i] += x * g
        # The fall: the last third resampled a touch lower (and so a touch longer), crossfaded in.
        cut = int(len(mixed) * 0.62)
        tail = filt(mixed[cut:], f"asetrate={CAW_RATE * fall:.0f},aresample={CAW_RATE}")
        fade = int(0.012 * CAW_RATE)
        body = mixed[:cut]
        for i in range(min(fade, len(tail), len(body))):
            t = i / fade
            body[len(body) - fade + i] = body[len(body) - fade + i] * (1 - t) + tail[i] * t
        xs = body + tail[fade:]
        xs = filt(xs, "highpass=f=320,lowpass=f=5200,equalizer=f=1300:t=o:w=1.2:g=5,equalizer=f=3200:t=o:w=1:g=-2")
        # Shape: cut to length, a 6 ms attack and a smooth release over the last 35%.
        xs = xs[:int(length * CAW_RATE)]
        m = len(xs)
        for i in range(m):
            a = min(1.0, i / (0.006 * CAW_RATE))
            r = min(1.0, (m - i) / (0.35 * m))
            xs[i] *= a * (0.5 - 0.5 * math.cos(math.pi * r))
        peak = max(abs(x) for x in xs) or 1.0
        k = 0.89 / peak
        vorbis_mono.encode([x * k for x in xs], os.path.join(out_dir, f"caw{n}.ogg"), rate=CAW_RATE)
        print(f"caw{n}: {m / CAW_RATE:.2f} s")


if "--sounds" in sys.argv:
    build_caws()


# ---------------------------------------------------------------------------------------------- preview
def render(p, view, tex, scale=14, width=300, height=300, light=(0.35, -0.8, -0.5), focus=None, background=(46, 52, 64),
           extra=None, tex_w=TEX_W, tex_h=TEX_H, s=S):
    """Orthographic render. view: 'front', 'side' (from its right), 'three' (three-quarters), 'back', 'top' (from above,
    front up) or 'below'. focus: a model point to centre."""
    if view == "top":
        rotv = lambda q: (q[0], q[2], q[1])
    elif view == "below":
        rotv = lambda q: (-q[0], q[2], -q[1])
    elif view == "high":
        ca, sa, cp, sp = math.cos(math.pi / 5), math.sin(math.pi / 5), math.cos(0.65), math.sin(0.65)

        def rotv(q):
            x, y, z = q[0] * ca - q[2] * sa, q[1], q[0] * sa + q[2] * ca
            return (x, y * cp - z * sp, y * sp + z * cp)
    else:
        angle = {"front": 0.0, "side": math.pi / 2, "three": math.pi / 5, "back": math.pi, "threeback": math.pi * 0.8}[view]
        ca, sa = math.cos(angle), math.sin(angle)
        rotv = lambda q: (q[0] * ca - q[2] * sa, q[1], q[0] * sa + q[2] * ca)
    zbuf = [1e9] * (width * height)
    img = [rgba(background)] * (width * height)
    fx, fy = (rotv(focus)[0], rotv(focus)[1]) if focus else (0.0, rotv((0.0, GROUND - 6.0, 0.0))[1])
    ox, oy = width / 2 - fx * scale, height / 2 - fy * scale
    if view not in ("top", "below") and not focus:
        gy = int(oy + GROUND * scale)
        for x in range(width):
            if 0 <= gy < height:
                img[gy * width + x] = (70, 78, 92, 255)
    lx, lyy, lz = light
    ln = math.sqrt(lx * lx + lyy * lyy + lz * lz)
    qs = quads(MODEL, p) if p else []
    for verts, (ua, va, ub, vb) in qs + (extra or []):
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.55 + 0.45 * abs(n[0] * lx + n[1] * lyy + n[2] * lz) / (nl * ln)
        du, dv = (ub - ua) * s, (vb - va) * s
        nu, nv = max(1, round(abs(du))), max(1, round(abs(dv)))
        for j in range(nv):
            for i in range(nu):
                tu = min(int(ua * s + (i + 0.5) * du / nu), tex_w * s - 1)
                tv = min(int(va * s + (j + 0.5) * dv / nv), tex_h * s - 1)
                col = tex.get(tu, tv)
                if col[3] == 0:
                    continue
                c = rgba(shade(col, lit))
                corners = []
                for (a_, b_) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s_, t_ = a_ / nu, b_ / nv
                    q = rotv(tuple(A[k] + e1[k] * s_ + e2[k] * t_ for k in range(3)))
                    corners.append((ox + q[0] * scale, oy + q[1] * scale, q[2]))
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
        s_ = 1 if cross > 0 else -1
        if sign == 0:
            sign = s_
        elif s_ != sign:
            return False
    return sign != 0


class Atlas:
    """The nest's three block textures side by side, so the renderer can sample them through one texture."""
    def __init__(self, textures):
        self.textures = textures

    def get(self, u, v):
        i = min(len(self.textures) - 1, u // 16)
        return self.textures[i].get(u % 16, v % 16)


def block_quads(elements, names):
    """A block model's faces as the game reads its JSON, in entity-model space (x mirrored, y down, centred), each
    face's uv shifted onto its texture's tile in the Atlas."""
    from gunkit import element_rotation
    out = []
    inv = lambda b: ((8.0 - b[0]), GROUND - b[1], (b[2] - 8.0))
    for e in elements:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        corners = {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
                   "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
                   "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
                   "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
                   "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
                   "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}
        rot = element_rotation(e)
        for name, face in e["faces"].items():
            pts = corners[name]
            if rot:
                m, o = rot
                pts = [tuple(o[k] + apply(m, tuple(q[i] - o[i] for i in range(3)))[k] for k in range(3)) for q in pts]
            tile = names.index(face["texture"]) * 16
            u0, v0, u1, v1 = face["uv"]
            out.append(([inv(pts[1]), inv(pts[0]), inv(pts[3]), inv(pts[2])], (u0 + tile, v0, u1 + tile, v1)))
    return out


def sprite_panel(sprites, width, height, k=9):
    img = [(46, 52, 64, 255)] * (width * height)
    for n, canvas in enumerate(sprites):
        bx, by = 12 + (n % 2) * (16 * k + 12), 12 + (n // 2) * (16 * k + 12)
        for y in range(16 * k):
            for x in range(16 * k):
                pxl = canvas.get(x // k, y // k)
                checker = (150, 150, 150, 255) if ((x // 18) + (y // 18)) % 2 else (139, 139, 139, 255)
                if 0 <= by + y < height and 0 <= bx + x < width:
                    img[(by + y) * width + bx + x] = pxl if pxl[3] else checker
    return img


def board(images, w, h, cols):
    rows_ = (len(images) + cols - 1) // cols
    out = [(30, 33, 40, 255)] * (w * cols * h * rows_)
    for k_, im in enumerate(images):
        bx, by = (k_ % cols) * w, (k_ // cols) * h
        for y in range(h):
            for x in range(w):
                out[(by + y) * w * cols + bx + x] = im[y * w + x]
    return out, w * cols, h * rows_


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    T = TEXTURE
    up = lambda **kw: pose(MODEL, fly=1.0, strength=0.9, **kw)
    views = [
        render(pose(MODEL), "front", T), render(pose(MODEL, age=10), "three", T), render(pose(MODEL), "side", T),
        render(pose(MODEL), "threeback", T),
        render(pose(MODEL, peck=0.5), "side", T), render(pose(MODEL, caw=0.5), "three", T),
        render(pose(MODEL, sleeping=True), "three", T), render(pose(MODEL, fly=0.35, flap=1.0, strength=0.5), "three", T),
        render(up(flap=math.pi / 2), "front", T, scale=10), render(up(flap=-math.pi / 2), "front", T, scale=10),
        render(up(flap=0.0), "three", T, scale=10), render(up(flap=math.pi / 2), "top", T, scale=10),
        render(pose(MODEL, fly=1.0, flap=0.0, strength=0.0, flight_pitch=0.15), "side", T, scale=10),
        render(up(flap=-1.0), "below", T, scale=10),
        render(pose(MODEL, fly=0.5, flap=0.5, strength=0.5), "three", T),
        render(pose(MODEL), "three", T, scale=34, focus=(0.0, 12.5, -6.5)),
    ]
    out, bw, bh = board(views, 300, 300, 4)
    png(os.path.join(PREVIEW, "preview_crow.png"), bw, bh, out)
    names = ["#twigs", "#lining", "#loot"]
    atlas = Atlas([twig_texture(), lining_texture(), loot_texture()])
    nest_views = [render(None, "high", atlas, scale=14, extra=block_quads(NEST, names), tex_w=48, tex_h=16, s=1,
                         focus=(0.0, GROUND - 3.0, 0.0)),
                  render(None, "high", atlas, scale=14, extra=block_quads(NEST_LOOT, names), tex_w=48, tex_h=16, s=1,
                         focus=(0.0, GROUND - 3.0, 0.0)),
                  render(None, "top", atlas, scale=14, extra=block_quads(NEST_LOOT, names), tex_w=48, tex_h=16, s=1,
                         focus=(0.0, GROUND, 0.0))]
    vf = vanilla_sprite("feather")
    feather = Canvas(16, 16)
    if vf:
        feather.px = vf[2]
    lure = Canvas(16, 16)
    lure.px = read_png(os.path.join(TEX, "item", "feather_lure.png"))[2]
    nest_views.append(sprite_panel([SPRITES["crow_feather"], feather, SPRITES["crow_fly"], lure], 300, 300, k=8))
    nest_views.append(sprite_panel([SPRITES["crow_spawn_egg"]], 300, 300, k=8))
    out, bw, bh = board(nest_views, 300, 300, len(nest_views))
    png(os.path.join(PREVIEW, "preview_crow_items.png"), bw, bh, out)
    print("preview written to", PREVIEW)
print("done")
