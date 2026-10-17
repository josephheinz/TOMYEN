#!/usr/bin/env python3
"""The Giant Catfish, the River Monster: its entity model and texture, the Giant Fish Mount its trophy goes on, the item
sprites of everything it drops or needs, and the data that ties it into fishing.

Run from anywhere: python3 tools/gen_giant_catfish.py [--preview DIR]

The model. An old flathead the length of five blocks (80 model pixels), built at one texel per model pixel like any
vanilla mob: a broad, flat shovel of a head with a jutting lower jaw, small eyes under heavy brows, gill covers that
breathe, two long whiskers (maxillary barbels, three segments each) from the corners of the lip, two short nasal ones
and four on the chin; then a thick trunk that narrows through five swimming segments (trunk, mid, tail 1 to 3) to a
rounded, fan-shaped tail with a bite out of its top edge. Every body segment is a main box with a narrower cap along the
back and under the belly, so the section reads rounded. Fins: a raked dorsal with its spine, two big pectorals with a
leading spine, the pelvics, a long low anal fin, the little adipose. An old rusted harpoon is stuck in its left flank,
a frayed rope still knotted round the shaft, and a rusted hook hangs through its lower lip. The cube layout is written
into client/fishing/catfish/GiantCatfishModel.java between the GENERATED markers; the model's setupAnim has a Python
twin here (pose) for the previews and the z-fight checks: change one, change both.

Model space is the game's: y down, the head toward -z, the ground at y 24.

The texture (256x128) is painted point by point: every texel is coloured from the place on the fish it lands on (its
rest-pose model point and face normal), so the patterns run unbroken across boxes. Countershading from a dark olive back
through a khaki flank to a cream belly (one ten-step ramp, read from where the texel sits between the section's back and
belly), soft-edged mottling on the back and upper flanks (hand-placed ellipses in two steps, fading out down the flank),
a dashed lateral line, old scars (a propeller's three slashes on the right flank, a gouge behind the harpoon wound, a
pale line across the crown), pale lips, a dark mouth lined with a band of bristle teeth, small gold-ringed eyes, fins in
four tones with their rays and dark rims, rust on the iron and a twist on the rope. Flat posterised ramps, no noise.

The Giant Fish Mount: the small Fish Mount's dark oak plaque grown to five blocks wide and two tall (a stepped oval
backboard, a bevel panel with its routed groove, the raised field and a brass plate), cut into ten block models, one per
part of the block (column 0-4, row 0-1). The item model is all of it at a fifth of the size. The fish on it is drawn by
GiantFishMountRenderer from this model in its mounted pose.

Item sprites (16x16, vanilla's style: an outline, lit from the top left): the Giant Catfish (the trophy), the Catfish
Fillet and Cooked Catfish Fillet, the Catfish Whisker, the Whisker Line, the Ancient Hook, Stink Bait and the spawn egg.

Data: the entity's loot table, the mount's, recipes with their recipe-book unlocks, the fishing species file (rivers at
night, steel rod and up, rarer than anything), the tags (what stink bait draws in, what goes on a giant mount, raw fish
for cut bait, aquatic for Impaling, the axe), and the sounds and lang, merged into the shared files.

Checks: the model at rest and through its swim, slap, drag, roar, flop and mounted poses goes through tools/coplanar.py;
the mount's block models through tools/zfight.py; the script stops on any coplanar overlapping faces.

With --preview DIR it renders DIR/preview_giant_catfish.png: the fish from the side, above, below and three-quarters,
swimming, charging, slapping, dragging and roaring, the head up close, the texture, the Giant Fish Mount with a trophy
on it, and the item sprites."""
import json, math, os, random, struct, sys, zipfile, zlib
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from coplanar import Face, fights  # noqa: E402
from zfight import check as zfight_check  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tomyen")
TEX = os.path.join(ASSETS, "textures")
DATA = os.path.join(RES, "data", "tomyen")
DATA_MC = os.path.join(RES, "data", "minecraft")
JAVA = os.path.join(REPO, "src", "client", "java", "com", "jheinz", "tomyen", "client", "fishing", "catfish", "GiantCatfishModel.java")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None

CLEAR = (0, 0, 0, 0)


# ============================================================================================================ utilities


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def read_png(data):
    """8-bit RGBA or RGB, or palette, non-interlaced: (w, h, [rgba])."""
    pos, idat, w, h, ctype, plte, trns = 8, b"", 0, 0, 6, None, None
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        t = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + n]
        if t == b"IHDR":
            w, h = struct.unpack(">II", body[:8])
            ctype = body[9]
        elif t == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif t == b"tRNS":
            trns = body
        elif t == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ctype]
    raw = zlib.decompress(idat)
    stride, out, prev = w * bpp, [], bytearray(w * bpp)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        for x in range(w):
            px = line[x * bpp:(x + 1) * bpp]
            if ctype == 6:
                out.append(tuple(px))
            elif ctype == 2:
                out.append(tuple(px) + (255,))
            elif ctype == 3:
                a_ = trns[px[0]] if trns and px[0] < len(trns) else 255
                out.append(plte[px[0]] + (a_,))
            elif ctype == 4:
                out.append((px[0], px[0], px[0], px[1]))
            else:
                out.append((px[0], px[0], px[0], 255))
        prev = line
    return w, h, out


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c if len(c) == 4 else (c[0], c[1], c[2], 255)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def fill(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def save(self, path):
        png(path, self.w, self.h, self.px)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])


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


def tag_add(path, entries):
    """Adds entries to a tag file, keeping whatever else is in it."""
    obj = OrderedDict([("replace", False), ("values", [])])
    if os.path.exists(path):
        with open(path) as f:
            obj = json.load(f, object_pairs_hook=OrderedDict)
    for entry in entries:
        if entry not in obj["values"]:
            obj["values"].append(entry)
    write(path, obj)


def client_jar():
    with open(os.path.join(REPO, "gradle.properties")) as f:
        version = next(line.split("=", 1)[1].strip() for line in f if line.startswith("minecraft_version="))
    path = os.path.expanduser(os.path.join("~", ".gradle", "caches", "fabric-loom", version, "minecraft-client.jar"))
    if not os.path.isfile(path):
        sys.exit(f"no Minecraft {version} client jar at {path}; run ./gradlew build first")
    return zipfile.ZipFile(path)


JAR = client_jar()


def vanilla_png(path):
    return read_png(JAR.read("assets/minecraft/textures/" + path + ".png"))


# ============================================================================================================ matrices


def rot_matrix(rx, ry, rz):
    """Rz * Ry * Rx, as ModelPart's Quaternionf.rotationZYX."""
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


# ============================================================================================================ the model
# Parts in parent-before-child order: name -> parent, pivot (in the parent's frame), rest rotation, cubes. A cube is
# (key, from, size, material) in the part's own frame; every cube has its own key (its own patch of texture), since the
# texture is painted by where each texel lands on the fish. Sizes are whole pixels; positions may be halves.
#
# Most parts are placed by absolute rest-pose coordinates (box, part with an absolute pivot), which is only meaningful
# while their ancestors have no rest rotation: the trunk-to-tail chain, the head and the jaw don't. The barbels, fins,
# gills, harpoon and hook have rest rotations; their own children are placed in their frames (local=True).

ZS = 4.0                    # the whole fish is shifted this far back so the hitbox sits over the trunk and head
TEX_W, TEX_H = 256, 128
GROUND = 24.0

PARTS = OrderedDict()
ABS = {}


def part(name, parent, pivot, rot=(0.0, 0.0, 0.0), local=False):
    if local:
        pv = tuple(pivot)
    else:
        pa = (pivot[0], pivot[1], pivot[2] + ZS)
        ABS[name] = pa
        pv = tuple(pa[i] - ABS[parent][i] for i in range(3)) if parent else pa
    PARTS[name] = {"parent": parent, "pivot": pv, "rot": tuple(rot), "cubes": []}


def box(name, key, mat, x0, y0, z0, x1, y1, z1):
    """A cube by its absolute rest-pose corners (the part must have been placed absolutely)."""
    p = ABS[name]
    size = (x1 - x0, y1 - y0, z1 - z0)
    assert all(float(s).is_integer() and s > 0 for s in size), (key, size)
    PARTS[name]["cubes"].append((key, (x0 - p[0], y0 - p[1], z0 + ZS - p[2]), tuple(int(s) for s in size), mat))


def lbox(name, key, mat, frm, size):
    assert all(float(s).is_integer() and s > 0 for s in size), (key, size)
    PARTS[name]["cubes"].append((key, tuple(frm), tuple(int(s) for s in size), mat))


# --- The trunk (the root of the swimming chain) and the head ---------------------------------------------------------
part("body", None, (0.0, 16.0, -12.0))
box("body", "trunk", "skin", -11, 10, -20, 11, 21, -4)
box("body", "trunk_back", "skin", -9, 8, -19, 9, 10, -4)
box("body", "trunk_belly", "skin", -9, 21, -19, 9, 23, -4)

part("head", "body", (0.0, 16.0, -20.0))
box("head", "skull", "skin", -12, 11, -32, 12, 20, -20)
box("head", "crown", "skin", -10, 9, -31, 10, 11, -21)
box("head", "snout", "skin", -10.5, 12, -36, 10.5, 18, -32)
box("head", "snout_top", "skin", -8.5, 11, -35, 8.5, 12, -32)
box("head", "upper_lip", "lip", -11, 16.5, -36.5, 11, 18.5, -32.5)
box("head", "brow_r", "skin", -12.5, 9.5, -30, -8.5, 10.5, -26)
box("head", "brow_l", "skin", 8.5, 9.5, -30, 12.5, 10.5, -26)
box("head", "eye_r", "eye", -12.5, 11.5, -29.5, -11.5, 14.5, -26.5)
box("head", "eye_l", "eye", 11.5, 11.5, -29.5, 12.5, 14.5, -26.5)

# Gill covers: hinged at their front edge, flaring out a little at rest.
part("gill_r", "head", (-12.0, 16.0, -22.5), (0.0, -0.12, 0.0))
box("gill_r", "gill_r", "skin", -13, 11.5, -22.5, -12, 20.5, -16.5)
part("gill_l", "head", (12.0, 16.0, -22.5), (0.0, 0.12, 0.0))
box("gill_l", "gill_l", "skin", 12, 11.5, -22.5, 13, 20.5, -16.5)

# The lower jaw, hinged at the back of the mouth: it juts a pixel past the snout (a flathead's underbite).
part("jaw", "head", (0.0, 18.5, -24.0))
box("jaw", "jaw", "skin", -11, 18.5, -37, 11, 21.5, -24)
box("jaw", "chin", "skin", -9, 21.5, -35, 9, 22.5, -25)
box("jaw", "lower_lip", "lip", -11.5, 18, -37.5, 11.5, 20, -34.5)

# Whiskers. The two long ones run out and back from the corners of the lip in three segments, drooping; the nasal pair
# stand up off the snout, and four short ones hang under the chin.
for side, s in (("r", -1), ("l", 1)):
    part(f"barbel_{side}1", "head", (11.0 * s, 17.5, -34.5), (-0.30, 0.95 * s, 0.0))
    lbox(f"barbel_{side}1", f"barbel_{side}1", "barbel", (-1.0, -1.0, 0.0), (2, 2, 8))
    part(f"barbel_{side}2", f"barbel_{side}1", (0.0, 0.0, 8.0), (-0.12, 0.28 * s, 0.0), local=True)
    lbox(f"barbel_{side}2", f"barbel_{side}2", "barbel", (-0.5, -0.5, 0.0), (1, 1, 8))
    part(f"barbel_{side}3", f"barbel_{side}2", (0.0, 0.0, 8.0), (-0.1, 0.22 * s, 0.0), local=True)
    lbox(f"barbel_{side}3", f"barbel_{side}3", "barbel", (-0.5, -0.5, 0.0), (1, 1, 7))

    part(f"nasal_{side}", "head", (6.0 * s, 11.0, -34.0), (0.55, 0.0, 0.3 * s))
    lbox(f"nasal_{side}", f"nasal_{side}", "barbel", (-0.5, -4.0, -0.5), (1, 4, 1))

    for k, (cx, cz) in enumerate(((3.5, -33.0), (7.5, -30.5))):
        name = f"chin_{side}{k}"
        part(name, "jaw", (cx * s, 22.5 if k == 0 else 21.5, cz), (-0.45, 0.0, 0.18 * s * (k + 1)))
        lbox(name, name, "barbel", (-0.5, 0.0, -0.5), (1, 5 if k == 0 else 4, 1))

# An old hook through the left side of the lower lip: the shank, the bend and the point.
part("hook", "jaw", (8.5, 19.5, -37.0), (0.25, 0.0, 0.2))
lbox("hook", "hook_shank", "rust", (-0.5, -1.0, -1.0), (1, 4, 1))
lbox("hook", "hook_bend", "rust", (-0.5, 3.0, -1.0), (3, 1, 1))
lbox("hook", "hook_point", "rust", (1.5, 1.0, -1.0), (1, 2, 1))

# --- Fins on the trunk ---------------------------------------------------------------------------------------------
part("dorsal", "body", (0.0, 8.0, -14.0), (-0.42, 0.0, 0.0))
lbox("dorsal", "dorsal_spine", "spine", (-0.5, -7.0, -1.0), (1, 7, 1))
lbox("dorsal", "dorsal_fin", "fin", (-0.5, -6.0, 0.0), (1, 6, 3))
lbox("dorsal", "dorsal_rear", "fin", (-0.5, -4.0, 3.0), (1, 4, 3))

for side, s in (("r", -1), ("l", 1)):
    part(f"pec_{side}", "body", (10.5 * s, 20.0, -17.0), (0.0, 0.42 * -s, 0.38 * -s))
    if s < 0:
        lbox(f"pec_{side}", f"pec_{side}", "fin", (-8.0, -0.5, 0.0), (8, 1, 6))
        lbox(f"pec_{side}", f"pec_spine_{side}", "spine", (-9.0, -0.5, -1.0), (9, 1, 1))
    else:
        lbox(f"pec_{side}", f"pec_{side}", "fin", (0.0, -0.5, 0.0), (8, 1, 6))
        lbox(f"pec_{side}", f"pec_spine_{side}", "spine", (0.0, -0.5, -1.0), (9, 1, 1))

# --- The swimming chain behind the trunk -----------------------------------------------------------------------------
part("mid", "body", (0.0, 16.0, -4.0))
box("mid", "mid", "skin", -10, 10.5, -4, 10, 20.5, 8)
box("mid", "mid_back", "skin", -8, 8.5, -4, 8, 10.5, 7)
box("mid", "mid_belly", "skin", -8, 20.5, -4, 8, 22.5, 7)
for side, s in (("r", -1), ("l", 1)):
    part(f"pelvic_{side}", "mid", (6.0 * s, 22.5, 3.0), (0.0, 0.5 * -s, 0.5 * -s))
    lbox(f"pelvic_{side}", f"pelvic_{side}", "fin", ((-4.0 if s < 0 else 0.0), -0.5, 0.0), (4, 1, 4))

# The harpoon, in the left flank: its shaft runs out and back and a little up; the socket collar is half buried, a
# wrap of rope sits near the end and a frayed tail of it hangs down.
part("harpoon", "mid", (10.0, 13.5, 2.0), (-0.38, 1.05, 0.0))
lbox("harpoon", "harpoon_shaft", "rust", (-0.5, -0.5, -2.0), (1, 1, 16))
lbox("harpoon", "harpoon_collar", "rust", (-1.0, -1.0, -1.0), (2, 2, 2))
lbox("harpoon", "harpoon_wrap", "rope", (-1.0, -1.0, 9.0), (2, 2, 2))
part("rope", "harpoon", (0.0, 1.0, 10.5), (0.42, -1.05, 0.0), local=True)
lbox("rope", "rope", "rope", (-0.5, 0.0, -0.5), (1, 6, 1))

part("tail1", "mid", (0.0, 16.0, 8.0))
box("tail1", "tail1", "skin", -7, 11, 8, 7, 20, 18)
box("tail1", "tail1_back", "skin", -5, 10, 8, 5, 11, 17)
box("tail1", "tail1_belly", "skin", -5, 20, 8, 5, 21, 17)
box("tail1", "anal1", "fin", -0.5, 20.5, 9.5, 0.5, 23.5, 16.5)

part("tail2", "tail1", (0.0, 16.0, 18.0))
box("tail2", "tail2", "skin", -4.5, 11.5, 18, 4.5, 19.5, 26)
box("tail2", "tail2_back", "skin", -3, 10.5, 18, 3, 11.5, 25)
box("tail2", "tail2_belly", "skin", -3, 19.5, 18, 3, 20.5, 25)
box("tail2", "anal2", "fin", -0.5, 20, 18.5, 0.5, 23, 24.5)
box("tail2", "adipose", "fin", -0.5, 8.5, 19, 0.5, 10.5, 23)

part("tail3", "tail2", (0.0, 15.5, 26.0))
box("tail3", "tail3", "skin", -2.5, 12.5, 26, 2.5, 18.5, 31)

part("fin", "tail3", (0.0, 15.5, 31.0))
box("fin", "fin_a", "fin", -0.5, 10.5, 30, 0.5, 20.5, 33)
box("fin", "fin_b", "fin", -0.5, 8.5, 33, 0.5, 22.5, 37)
box("fin", "fin_c", "fin", -0.5, 9.5, 37, 0.5, 21.5, 40)
box("fin", "fin_d", "fin", -0.5, 11.5, 40, 0.5, 19.5, 42)

CUBE_COUNT = sum(len(p["cubes"]) for p in PARTS.values())


def children():
    t = {}
    for name, p in PARTS.items():
        t.setdefault(p["parent"] or "root", []).append(name)
    return t


TREE = children()


# --- UV packing -------------------------------------------------------------------------------------------------------
def pack():
    """Texture offsets for every cube key: skyline-packed, tallest footprint first."""
    sizes = {}
    for p in PARTS.values():
        for key, _, size, _ in p["cubes"]:
            assert key not in sizes, key
            sizes[key] = size
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
    return offs


UV = pack()


# ============================================================================================================ the pose
# A Python twin of GiantCatfishModel.setupAnim: keep the two in step.


def ramp01(v, a, b):
    t = max(0.0, min(1.0, (v - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


def rest():
    return {name: [list(p["pivot"]), list(p["rot"])] for name, p in PARTS.items()}


def pose(age=0.0, swim=0.0, tail=0.0, mouth=0.0, slap=0.0, slap_side=1.0, thrash=0.0, roar=0.0, beached=False, look=0.0,
         mounted=False):
    p = rest()
    sin = math.sin

    def add(name, dx=0.0, dy=0.0, dz=0.0):
        r = p[name][1]
        r[0] += dx
        r[1] += dy
        r[2] += dz

    if mounted:
        # Set for the wall as a taxidermist sets a fish: an S through the body, mouth open, fins spread, whiskers out.
        for name, a in (("body", 0.03), ("mid", 0.08), ("tail1", 0.14), ("tail2", 0.18), ("tail3", 0.12), ("fin", 0.1)):
            add(name, dy=a)
        add("head", dy=-0.1, dx=-0.06)
        add("jaw", dx=0.5)
        for side, s in (("r", -1), ("l", 1)):
            add(f"gill_{side}", dy=0.22 * s)
            add(f"pec_{side}", dz=-0.25 * -s, dy=-0.15 * -s)
            add(f"barbel_{side}1", dy=-0.1 * s, dx=0.12)
            add(f"barbel_{side}2", dy=0.12 * s, dx=0.08)
            add(f"barbel_{side}3", dy=0.2 * s, dx=0.06)
        add("dorsal", dx=0.15)
        return p

    swim_c = min(1.0, swim)
    amp = 0.35 + 0.65 * swim_c
    # The travelling wave: small at the trunk, growing to the tail; the head counters the trunk's sway.
    for name, a, lag in (("body", 0.05, 0.0), ("mid", 0.12, 0.9), ("tail1", 0.18, 1.8), ("tail2", 0.24, 2.7),
                         ("tail3", 0.28, 3.5), ("fin", 0.32, 4.3)):
        add(name, dy=amp * a * sin(tail - lag))
    add("head", dy=-amp * 0.04 * sin(tail) + 0.25 * look)
    if beached:
        # Beached: it bends into a C and back.
        bend = 0.22 * sin(age * 0.5)
        for name, k in (("mid", 1.0), ("tail1", 1.0), ("tail2", 1.0), ("tail3", 0.8)):
            add(name, dy=bend * k)
        add("head", dy=-0.6 * bend)
    if slap > 0.0:
        # The tail slap: curls toward the side it'll strike from, whips across, and recovers.
        wind = ramp01(slap, 0.0, 0.35)
        strike = ramp01(slap, 0.35, 0.55)
        back = ramp01(slap, 0.7, 1.0)
        angle = slap_side * (0.5 * wind - 1.4 * strike) * (1.0 - back)
        for name, k in (("mid", 0.35), ("tail1", 0.6), ("tail2", 0.8), ("tail3", 0.6), ("fin", 0.5)):
            add(name, dy=k * angle)
        add("head", dy=-0.3 * angle)
        add("body", dz=slap_side * 0.18 * sin(math.pi * slap))
    if thrash > 0.0:
        # Shaking its catch (or itself, harpooned).
        add("body", dz=thrash * 0.22 * sin(age * 0.9))
        add("head", dy=thrash * 0.18 * sin(age * 1.3), dx=thrash * 0.1 * sin(age * 1.1))
    add("jaw", dx=0.75 * mouth)
    add("head", dx=-0.12 * mouth - 0.25 * roar)
    add("body", dx=-0.1 * roar)
    breathe = 0.5 + 0.5 * sin(age * 0.12)
    for side, s in (("r", -1), ("l", 1)):
        add(f"gill_{side}", dy=s * (0.12 * breathe + 0.35 * mouth))
        add(f"pec_{side}", dz=0.2 * sin(age * 0.2 + (0.0 if s < 0 else math.pi)) * (1.0 - 0.5 * swim_c) * s,
            dy=0.35 * swim_c * s)
        add(f"pelvic_{side}", dz=0.12 * sin(age * 0.2 + 1.0) * s)
        o = 0.0 if s < 0 else 2.1
        add(f"barbel_{side}1", dy=-0.4 * s * swim_c + 0.10 * sin(age * 0.06 + o) - amp * 0.08 * sin(tail - 0.5),
            dx=0.08 * sin(age * 0.05 + o) + 0.15 * swim_c)
        add(f"barbel_{side}2", dy=0.16 * sin(age * 0.06 + o - 0.8) - amp * 0.1 * sin(tail - 1.1),
            dx=0.1 * sin(age * 0.05 + o - 0.8))
        add(f"barbel_{side}3", dy=0.22 * sin(age * 0.06 + o - 1.6) - amp * 0.12 * sin(tail - 1.7),
            dx=0.12 * sin(age * 0.05 + o - 1.6))
        add(f"nasal_{side}", dx=0.06 * sin(age * 0.1 + o))
        for k in range(2):
            add(f"chin_{side}{k}", dx=0.12 * sin(age * 0.09 + k + o) + 0.35 * swim_c)
    add("dorsal", dx=0.25 * roar)
    add("rope", dx=0.1 * sin(age * 0.08), dz=0.1 * sin(age * 0.06))
    return p


# ============================================================================================================ geometry


def placed(p):
    """Every cube with its part's model-space transform: (part, key, from, size, mat, matrix, origin)."""
    out = []

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot = p[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            for key, frm, size, mat in PARTS[child]["cubes"]:
                out.append((child, key, frm, size, mat, m, o))
            walk(child, m, o)
    walk("root", IDENT, (0.0, 0.0, 0.0))
    return out


def part_frames(p):
    """Each part's model-space (matrix, origin)."""
    out = {}

    def walk(name, matrix, origin):
        for child in TREE.get(name, []):
            pivot, rot = p[child]
            o = tuple(origin[i] + apply(matrix, pivot)[i] for i in range(3))
            m = mul(matrix, rot_matrix(*rot))
            out[child] = (m, o)
            walk(child, m, o)
    walk("root", IDENT, (0.0, 0.0, 0.0))
    return out


def cube_faces(m, o, u, v, frm, size):
    """The six faces as the game builds them: (corners, uv rect (u0, v0, u1, v1), name). Corner 1 sits at (u0, v0),
    corner 0 along u from it and corner 2 along v."""
    w, h, d = size
    x0, y0, z0 = frm
    x1, y1, z1 = x0 + w, y0 + h, z0 + d

    def P(x, y, z):
        q = apply(m, (x, y, z))
        return (o[0] + q[0], o[1] + q[1], o[2] + q[2])
    t0, t1, t2, t3 = P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0)
    l0, l1, l2, l3 = P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    return [([l1, l0, t0, t1], (u1, v0, u2, v1), "down"), ([t2, t3, l3, l2], (u2, v1, u22, v0), "up"),
            ([t0, l0, l3, t3], (u0, v1, u1, v2), "west"), ([t1, t0, t3, t2], (u1, v1, u2, v2), "north"),
            ([l1, t1, t2, l2], (u2, v1, u3, v2), "east"), ([l0, l1, l2, l3], (u3, v1, u4, v2), "south")]


def centroid(pts):
    return tuple(sum(q[i] for q in pts) / len(pts) for i in range(3))


def quads(p, labels=False):
    out = []
    for part_, key, frm, size, mat, m, o in placed(p):
        u, v = UV[key]
        c = centroid([apply(m, (frm[0] + size[0] / 2, frm[1] + size[1] / 2, frm[2] + size[2] / 2))])
        centre = (o[0] + c[0], o[1] + c[1], o[2] + c[2])
        for verts, uv, name in cube_faces(m, o, u, v, frm, size):
            if labels:
                fc = centroid(verts)
                out.append((verts, uv, f"{part_}.{key}.{name}", tuple(fc[i] - centre[i] for i in range(3))))
            else:
                out.append((verts, uv))
    return out


def extents(p):
    pts = [q for verts, _ in quads(p) for q in verts]
    return [(min(q[i] for q in pts), max(q[i] for q in pts)) for i in range(3)]


# ============================================================================================================ the texture
SKIN = [(30, 36, 22), (40, 47, 27), (52, 58, 33), (66, 71, 40), (82, 85, 50), (102, 101, 62), (130, 124, 84),
        (176, 166, 128), (208, 199, 162), (226, 219, 189)]
SKIN_T = [0.06, 0.16, 0.27, 0.39, 0.51, 0.62, 0.72, 0.8, 0.9]   # where each step of SKIN begins, back to belly
MOTTLE = (26, 30, 18)
SCAR = [(178, 160, 146), (126, 112, 98)]       # the raised scar, its shadow
WOUND = (84, 40, 34)
LIP = [(196, 182, 154), (168, 152, 126), (138, 124, 102)]
MOUTH = [(54, 22, 24), (80, 34, 34), (108, 50, 46)]
TOOTH = [(212, 200, 174), (178, 164, 140)]
EYE_RING = [(172, 142, 58), (124, 100, 42)]
PUPIL = (16, 14, 10)
GLINT = (234, 228, 198)
BARBEL = [(46, 48, 32), (72, 70, 46), (112, 104, 74), (158, 146, 106)]
FIN = [(46, 48, 30), (64, 64, 39), (84, 82, 51), (106, 102, 65)]
RUST = [(62, 34, 22), (96, 50, 28), (134, 72, 36), (170, 104, 52)]
ROPE = [(184, 164, 120), (152, 132, 94), (118, 100, 70)]

REST = rest()
REST_PLACED = placed(REST)
REST_FRAMES = part_frames(REST)
HINGE_Z = -24.0 + ZS         # the back of the mouth
LIP_Z = -36.5 + ZS           # the upper lip's front


def profile():
    """The body's silhouette at rest, back (min y) and belly (max y), every half pixel along z: from the skin cubes of
    the head, jaw and swimming chain."""
    spans = []
    for part_, key, frm, size, mat, m, o in REST_PLACED:
        if mat != "skin" or part_.startswith("gill") or key.startswith("brow"):
            continue
        lo = tuple(o[i] + frm[i] for i in range(3))
        spans.append((lo[2], lo[2] + size[2], lo[1], lo[1] + size[1]))
    z0 = min(s[0] for s in spans)
    z1 = max(s[1] for s in spans)
    out = {}
    z = z0
    while z <= z1:
        cover = [s for s in spans if s[0] <= z <= s[1]]
        if cover:
            out[round(z * 2)] = (min(s[2] for s in cover), max(s[3] for s in cover))
        z += 0.5
    return out


PROFILE = profile()


def section(z):
    k = max(min(round(z * 2), max(PROFILE)), min(PROFILE))
    while k not in PROFILE:
        k += 1
    return PROFILE[k]


def depth_t(p):
    """How far down the body a point sits: 0 on the back, 1 under the belly."""
    top, bottom = section(p[2])
    return max(0.0, min(1.0, (p[1] - top) / max(1.0, bottom - top)))


def skin_step(t):
    i = 0
    while i < len(SKIN_T) and t >= SKIN_T[i]:
        i += 1
    return i


# Mottling: ellipses (centre z, centre y or x, radius along z, radius across) placed by hand along each flank and the
# back, in model space at rest. Spots fade out down the flank.
_rng = random.Random(4127)


def _blobs(n, z0, z1, a0, a1, rz, ra):
    out = []
    for i in range(n):
        z = z0 + (z1 - z0) * (i + 0.5) / n + _rng.uniform(-1.2, 1.2)
        out.append((z, _rng.uniform(a0, a1), _rng.uniform(*rz), _rng.uniform(*ra)))
    return out


def _side_blobs():
    out = []
    # Flank spots ride a band whose height follows the section: placed as a fraction of the way down, then converted.
    for frac_lo, frac_hi, n in ((0.1, 0.3, 9), (0.33, 0.52, 8)):
        for z, a, rz, ra in _blobs(n, -33 + ZS, 37 + ZS, 0.0, 1.0, (2.4, 4.2), (1.7, 2.8)):
            top, bottom = section(z)
            out.append((z, top + (frac_lo + (frac_hi - frac_lo) * a) * (bottom - top), rz, ra))
    return out


SIDE_BLOBS = {-1: _side_blobs(), 1: _side_blobs()}
TOP_BLOBS = _blobs(16, -33 + ZS, 28 + ZS, -9.0, 9.0, (2.2, 4.0), (2.0, 3.4))
# Paler olive patches between the dark blotches, so the back reads mottled rather than just dark.
TOP_LIGHT = _blobs(11, -31 + ZS, 26 + ZS, -8.0, 8.0, (2.0, 3.2), (1.6, 2.6))
SIDE_LIGHT = {side: [(z, top + 0.2 * (bottom - top), rz, ra) for z, _, rz, ra in _blobs(8, -30 + ZS, 34 + ZS, 0.0, 1.0, (2.0, 3.4), (1.4, 2.2))
                     for top, bottom in [section(z)]] for side in (-1, 1)}

# Scars, as segments in (z, y) on a flank, or (z, x) across the back. Model space at rest.
SIDE_SCARS = {
    -1: [((-15 + ZS, 11.5), (-10 + ZS, 18.0)), ((-11.5 + ZS, 11.0), (-6.5 + ZS, 17.5)), ((-8 + ZS, 11.5), (-3 + ZS, 17.5)),
         ((10 + ZS, 13.0), (15 + ZS, 14.0))],
    1: [((4.5 + ZS, 14.5), (13 + ZS, 16.5)), ((-30 + ZS, 16.0), (-25 + ZS, 18.0))],
}
TOP_SCARS = [((-29 + ZS, -5.0), (-22 + ZS, 3.0))]
WOUND_AT = (2.0 + ZS, 13.5)      # where the harpoon went in (left flank)


def seg_dist(p, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / (dx * dx + dy * dy)))
    qx, qy = ax + dx * t, ay + dy * t
    return math.hypot(p[0] - qx, p[1] - qy), p[1] - qy


def blob_value(q, blobs):
    best = 0.0
    for bz, ba, rz, ra in blobs:
        d = math.sqrt(((q[0] - bz) / rz) ** 2 + ((q[1] - ba) / ra) ** 2)
        best = max(best, 1.0 - d)
    return best


def paint_skin(key, part_, p, n):
    """Countershading, mottling, scars and the lateral line, by where on the fish the texel is."""
    # The mouth: the roof under the snout and skull, the floor on top of the jaw, ahead of the hinge.
    if part_ == "head" and n[1] > 0.5 and p[2] < HINGE_Z:
        return mouth(p)
    if part_ == "jaw" and n[1] < -0.5 and p[2] < HINGE_Z:
        return mouth(p)
    t = depth_t(p)
    light = 0.0
    if n[1] < -0.5:            # facing up (model y is down): the back
        i = (2 if abs(p[0]) < 5.5 else 3) if t < 0.5 else skin_step(t)
        m = blob_value((p[2], p[0]), TOP_BLOBS) * (1.0 if t < 0.5 else 0.0)
        light = blob_value((p[2], p[0]), TOP_LIGHT) * (1.0 if t < 0.5 else 0.0)
        for a, b in TOP_SCARS:
            d, off = seg_dist((p[2], p[0]), a, b)
            if d <= 0.55:
                return SCAR[0]
            if d <= 1.25 and off > 0:
                return SCAR[1]
    elif n[1] > 0.5:           # facing down: the belly
        return SKIN[9] if abs(p[0]) < 5 else SKIN[8]
    else:
        i = skin_step(t)
        side = -1 if p[0] < 0 else 1
        m = 0.0
        if abs(n[0]) > 0.5:
            fade = max(0.0, min(1.0, (0.62 - t) / 0.22))
            m = blob_value((p[2], p[1]), SIDE_BLOBS[side]) * fade
            light = blob_value((p[2], p[1]), SIDE_LIGHT[side]) * fade
            for a, b in SIDE_SCARS[side]:
                d, off = seg_dist((p[2], p[1]), a, b)
                if d <= 0.55:
                    return SCAR[0]
                if d <= 1.3 and off > 0:
                    return SCAR[1]
            if side > 0 and part_ == "mid":
                r = math.hypot(p[2] - WOUND_AT[0], p[1] - WOUND_AT[1])
                if r <= 1.3:
                    return WOUND
                if r <= 2.2:
                    return SCAR[0] if p[1] < WOUND_AT[1] else SCAR[1]
            # The lateral line: a row of pale dashes along the flank, from the gill to the tail.
            top, bottom = section(p[2])
            line = top + 0.4 * (bottom - top)
            if part_ not in ("head", "jaw") and abs(p[1] - line) < 0.5 and int(math.floor(p[2])) % 3 != 0 and m < 0.2:
                return SKIN[min(9, i + 2)]
        elif part_.startswith("gill"):
            m = 0.0
    if m > 0.5:
        return MOTTLE if i <= 3 else SKIN[max(0, i - 3)]
    if m > 0.18:
        return SKIN[max(0, i - 1)]
    if light > 0.3:
        return SKIN[min(9, i + 1)]
    return SKIN[i]


def mouth(p):
    """Dark inside, deeper toward the throat; a band of bristle teeth just behind the lips."""
    from_lip = p[2] - LIP_Z
    if from_lip < 2.5:
        return TOOTH[0] if (int(math.floor(p[0])) % 2 == 0) else TOOTH[1]
    k = (p[2] - LIP_Z) / max(1.0, HINGE_Z - LIP_Z)
    return MOUTH[2] if k < 0.35 else MOUTH[1] if k < 0.7 else MOUTH[0]


def paint_lip(key, part_, p, n, s, t):
    if n[1] < -0.5:
        return LIP[0]
    if n[1] > 0.5:
        return LIP[2]
    if abs(n[0]) > 0.5:
        return LIP[1]
    # The front: lighter in the middle of the lip's thickness.
    return LIP[0] if 0.25 < t < 0.75 else LIP[1]


def paint_eye(key, part_, p, n, i, j, w, h):
    if abs(n[0]) < 0.5 or (n[0] < 0) != (p[0] < 0):
        return SKIN[2]
    if (i, j) == (1, 1):
        return PUPIL
    if (i, j) in ((0, 0), (2, 0)) and False:
        return GLINT
    if j == 0 and i == (0 if p[0] < 0 else 2):
        return GLINT
    return EYE_RING[0] if j < 2 else EYE_RING[1]


def paint_barbel(key, part_, p, n):
    seg = int(part_[-1]) if part_.startswith("barbel") else 3
    lift = 1 if seg == 3 else 0
    if n[1] < -0.5:
        return BARBEL[0 + lift]
    if n[1] > 0.5:
        return BARBEL[2 + lift]
    return BARBEL[1 + lift]


def paint_fin(key, part_, p, n):
    """Fin membrane: lighter at the root, darkening outward, with its rays and a dark rim."""
    if part_ == "fin":
        pivot = (0.0, 15.5, 31.0 + ZS)
        reach = 12.0
    else:
        pivot = REST_FRAMES[part_][1]
        reach = {"dorsal": 7.0, "pec_r": 9.0, "pec_l": 9.0, "pelvic_r": 4.5, "pelvic_l": 4.5, "tail1": 3.0, "tail2": 3.0}.get(part_, 6.0)
    if part_ in ("tail1", "tail2"):
        # The anal and adipose fins: their root is where they meet the body.
        top, bottom = section(p[2])
        r = (p[1] - bottom) / reach if p[1] > (top + bottom) / 2 else (top - p[1]) / 2.0
        ray = int(math.floor(p[2] / 2.0)) % 2
    else:
        d = tuple(p[i] - pivot[i] for i in range(3))
        r = math.sqrt(sum(c * c for c in d)) / reach
        if part_ in ("fin", "dorsal"):
            ang = math.atan2(d[1], d[2])
        else:
            ang = math.atan2(d[0], d[2])
        ray = int(math.floor(ang * 9.0 / math.pi)) % 2
    if part_ == "fin" and 34.5 + ZS <= p[2] <= 36.0 + ZS and p[1] < 10.0:
        return CLEAR           # an old bite out of the top of the tail
    base = 3 if r < 0.35 else 2 if r < 0.7 else 1
    if r >= 0.92:
        return FIN[0]
    return FIN[max(0, base - ray)]


def paint_spine(key, part_, p, n):
    return FIN[1] if n[1] < -0.5 else FIN[0]


def paint_rust(key, part_, p, n, local):
    lz, ly = local[2], local[1]
    band = (int(math.floor(lz / 3.0)) + int(math.floor(local[0] + ly))) % 3
    if n[1] < -0.5:
        return RUST[3] if band else RUST[2]
    if (int(math.floor(lz)) * 7 + int(math.floor(ly)) * 3) % 11 == 0:
        return RUST[0]
    return RUST[1] if band == 0 else RUST[2]


def paint_rope(key, part_, p, n, i, j):
    return ROPE[(i + j) % 3]


def face_info(verts):
    a, b, c = verts[1], verts[0], verts[2]
    e1 = tuple(b[k] - a[k] for k in range(3))
    e2 = tuple(c[k] - a[k] for k in range(3))
    return a, e1, e2


def paint_entity():
    tex = Canvas(TEX_W, TEX_H)
    for part_, key, frm, size, mat, m, o in REST_PLACED:
        u, v = UV[key]
        cc = centroid([(o[0] + q[0], o[1] + q[1], o[2] + q[2])
                       for q in [apply(m, (frm[0] + size[0] / 2, frm[1] + size[1] / 2, frm[2] + size[2] / 2))]])
        inv = [[m[j][i] for j in range(3)] for i in range(3)]
        for verts, (u0, v0, u1, v1), name in cube_faces(m, o, u, v, frm, size):
            fc = centroid(verts)
            n = tuple(fc[k] - cc[k] for k in range(3))
            ln = math.sqrt(sum(c * c for c in n)) or 1.0
            n = tuple(c / ln for c in n)
            a, e1, e2 = face_info(verts)
            nu, nv = int(round(abs(u1 - u0))), int(round(abs(v1 - v0)))
            for j in range(nv):
                for i in range(nu):
                    s = (i + 0.5) / nu
                    t = (j + 0.5) / nv
                    q = tuple(a[k] + e1[k] * s + e2[k] * t for k in range(3))
                    if mat == "skin":
                        col = paint_skin(key, part_, q, n)
                    elif mat == "lip":
                        col = paint_lip(key, part_, q, n, s, t)
                    elif mat == "eye":
                        col = paint_eye(key, part_, q, n, i, j, nu, nv)
                    elif mat == "barbel":
                        col = paint_barbel(key, part_, q, n)
                    elif mat == "fin":
                        col = paint_fin(key, part_, q, n)
                    elif mat == "spine":
                        col = paint_spine(key, part_, q, n)
                    elif mat == "rust":
                        local = apply(inv, tuple(q[k] - o[k] for k in range(3)))
                        col = paint_rust(key, part_, q, n, local)
                    else:
                        col = paint_rope(key, part_, q, n, i, j)
                    tu = int(math.floor(min(u0, u1) + i)) if u1 > u0 else int(math.floor(u0 - 1 - i))
                    tv = int(math.floor(min(v0, v1) + j)) if v1 > v0 else int(math.floor(v0 - 1 - j))
                    tex.set(tu, tv, col if len(col) == 4 else (col[0], col[1], col[2], 255))
    return tex


TEXTURE = paint_entity()
TEXTURE.save(os.path.join(TEX, "entity", "giant_catfish", "giant_catfish.png"))


# ============================================================================================================ checks


def check(label, p):
    faces = []
    for verts, uv, name, n in quads(p, labels=True):
        faces.append(Face(name, verts, n))
    hits = fights(faces)
    for h in hits:
        print(f"z-fight ({label}):", h)
    return len(hits)


POSES = [("rest", {}), ("mounted", {"mounted": True}), ("roar", {"mouth": 1.0, "roar": 1.0}),
         ("drag", {"mouth": 0.3, "thrash": 1.0, "age": 7.0}), ("beached", {"beached": True, "age": 3.0})]
POSES += [(f"swim {t:.1f}", {"swim": 1.0, "tail": t, "age": t * 3}) for t in [i * 0.7 for i in range(10)]]
POSES += [(f"slap {s:.2f}", {"slap": s, "slap_side": 1.0, "age": s * 20}) for s in (0.2, 0.45, 0.6, 0.85)]
POSES += [(f"slap- {s:.2f}", {"slap": s, "slap_side": -1.0}) for s in (0.3, 0.5)]
_bad = sum(check(label, pose(**kw)) for label, kw in POSES)
if _bad:
    raise SystemExit(f"giant catfish: {_bad} coplanar overlapping face pairs")
_ext = extents(REST)
print(f"giant catfish: {CUBE_COUNT} cubes; x {_ext[0][0]:.1f}..{_ext[0][1]:.1f}, y {_ext[1][0]:.1f}..{_ext[1][1]:.1f}, "
      f"z {_ext[2][0]:.1f}..{_ext[2][1]:.1f} ({(_ext[2][1] - _ext[2][0]) / 16:.2f} blocks long)")


# ============================================================================================================ the Java model


def fj(v):
    return f"{v:.3f}f".replace("-0.000f", "0.000f")


def java_layer():
    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]
    for name, p in PARTS.items():
        builder = "CubeListBuilder.create()"
        for key, frm, size, mat in p["cubes"]:
            u, v = UV[key]
            builder += f"\n\t\t\t\t.texOffs({u}, {v}).addBox({', '.join(fj(c) for c in frm)}, {', '.join(fj(c) for c in size)})"
        pv, rot = p["pivot"], p["rot"]
        pose_ = (f"PartPose.offsetAndRotation({', '.join(fj(c) for c in pv)}, {', '.join(fj(c) for c in rot)})"
                 if any(rot) else f"PartPose.offset({', '.join(fj(c) for c in pv)})")
        used = name in TREE
        parent = ("p_" + p["parent"]) if p["parent"] else "root"
        lines.append(f"\t\t{'PartDefinition p_' + name + ' = ' if used else ''}{parent}.addOrReplaceChild(\"{name}\", {builder},"
                     f"\n\t\t\t\t{pose_});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {TEX_W}, {TEX_H});")
    return "\n".join(lines)


_src = open(JAVA).read()
_start, _end = "\t\t// BEGIN GENERATED by tools/gen_giant_catfish.py\n", "\t\t// END GENERATED\n"
_a, _b = _src.index(_start) + len(_start), _src.index(_end)
_src = _src[:_a] + java_layer() + "\n" + _src[_b:]
with open(JAVA, "w") as _f:
    _f.write(_src)


# ============================================================================================================ the Giant Fish Mount
# Pixels in the master block's frame for a north-facing mount (the plaque against the block's south side, its face toward
# -z): x across (the viewer's left is +x), y up, the wall at z = 16. The master is the bottom middle block (column 2, row
# 0); the plaque runs two blocks either side of it (x -32..48) and two blocks up (y 0..32). Column c covers
# x 16*(2-c)..16*(3-c), counted from the viewer's left; row r covers y 16r..16r+16. Each layer is a rectangle whose top
# and bottom rows step in by its insets (outermost row first), which rounds it into a stepped oval.

MOUNT_COLUMNS, MOUNT_ROWS, MASTER_COLUMN = 5, 2, 2
MOUNT_LAYERS = OrderedDict([
    # name: (x0, x1, y0, y1, z0, z1, insets, atlas origin of its front)
    ("board", (-32, 48, 1, 31, 14.0, 16.0, [10, 6, 4, 3, 2, 1, 1], (0, 0))),
    ("bevel", (-30, 46, 3, 29, 13.5, 14.0, [9, 5, 3, 2, 1, 1], (0, 30))),
    ("field", (-26, 42, 10, 27, 13.0, 13.5, [4, 2, 1], (0, 56))),
])
MOUNT_PLATE = (0, 16, 4, 8, 13.0, 13.5)    # the brass name plate, on the bevel under the field, centred on the master
PLATE_ATLAS = (96, 0)                     # its front, 16x4
WOOD_V, WOOD_H = (124, 0), (0, 76)        # end grain (vertical, 2 wide) and long grain (horizontal, 2 tall) side strips
BRASS_V, BRASS_H = (127, 0), (96, 5)
ATLAS = 128


def strips(x0, x1, y0, y1, insets):
    rows = []
    for y in range(y0, y1):
        k = min(y1 - 1 - y, y - y0)
        inset = insets[k] if k < len(insets) else 0
        rows.append((x0 + inset, x1 - inset, y, y + 1))
    merged = []
    for r in rows:
        if merged and merged[-1][0] == r[0] and merged[-1][1] == r[1] and merged[-1][3] == r[2]:
            merged[-1] = (r[0], r[1], merged[-1][2], r[3])
        else:
            merged.append(r)
    return merged


def mask(x0, x1, y0, y1, insets):
    """The layer's front as the viewer sees it: {(column, row)}, column 0 at the viewer's left (high x), row 0 at top."""
    cells = set()
    for sx0, sx1, sy0, sy1 in strips(x0, x1, y0, y1, insets):
        for x in range(sx0, sx1):
            for y in range(sy0, sy1):
                cells.add((x1 - 1 - x, y1 - 1 - y))
    return cells


def uvr(t0, t1, t2, t3):
    return [round(v * 16.0 / ATLAS, 4) for v in (t0, t1, t2, t3)]


def element(name, frm, to, faces):
    out = OrderedDict([("name", name), ("from", [round(c, 4) for c in frm]), ("to", [round(c, 4) for c in to]),
                       ("faces", OrderedDict())])
    for face, (coords, cull) in faces.items():
        f = OrderedDict([("uv", coords), ("texture", "#plaque")])
        if cull:
            f["cullface"] = cull
        out["faces"][face] = f
    return out


def cuts(a, b):
    """[a, b) split at every multiple of 16."""
    out, x = [], a
    while x < b:
        nxt = min(b, (x // 16 + 1) * 16)
        out.append((x, nxt))
        x = nxt
    return out


def mount_pieces():
    """Every element of the plaque, as (column, row, element in master-frame pixels). Strips are cut at block edges;
    a cut edge gets no face (the neighbouring block's piece carries on from it)."""
    out = []
    for name, (x0, x1, y0, y1, z0, z1, insets, (ox, oy)) in MOUNT_LAYERS.items():
        d = z1 - z0
        rows = strips(x0, x1, y0, y1, insets)
        for i, (sx0, sx1, sy0, sy1) in enumerate(rows):
            above = rows[i + 1] if i + 1 < len(rows) else None
            below = rows[i - 1] if i > 0 else None
            for px0, px1 in cuts(sx0, sx1):
                for py0, py1 in cuts(sy0, sy1):
                    faces = OrderedDict()
                    faces["north"] = (uvr(ox + x1 - px1, oy + y1 - py1, ox + x1 - px0, oy + y1 - py0), None)
                    if name == "board":
                        faces["south"] = (uvr(ox + px0 - x0, oy + y1 - py1, ox + px1 - x0, oy + y1 - py0), "south")
                    side = (uvr(WOOD_V[0], WOOD_V[1] + y1 - py1, WOOD_V[0] + d, WOOD_V[1] + y1 - py0), None)
                    if px1 == sx1:
                        faces["east"] = side
                    if px0 == sx0:
                        faces["west"] = side
                    flat = (uvr(WOOD_H[0] + px0 - x0, WOOD_H[1], WOOD_H[0] + px1 - x0, WOOD_H[1] + d), None)
                    if py1 == sy1 and (above is None or above[0] > px0 or above[1] < px1):
                        faces["up"] = flat
                    if py0 == sy0 and (below is None or below[0] > px0 or below[1] < px1):
                        faces["down"] = flat
                    col = MASTER_COLUMN - px0 // 16
                    row = py0 // 16
                    out.append((col, row, element(f"{name}_{i}", (px0, py0, z0), (px1, py1, z1), faces)))
    x0, x1, y0, y1, z0, z1 = MOUNT_PLATE
    d = z1 - z0
    ox, oy = PLATE_ATLAS
    side = (uvr(BRASS_V[0], BRASS_V[1], BRASS_V[0] + d, BRASS_V[1] + (y1 - y0)), None)
    flat = (uvr(BRASS_H[0], BRASS_H[1], BRASS_H[0] + (x1 - x0), BRASS_H[1] + d), None)
    out.append((MASTER_COLUMN - x0 // 16, y0 // 16, element("plate", (x0, y0, z0), (x1, y1, z1), OrderedDict([
        ("north", (uvr(ox, oy, ox + x1 - x0, oy + y1 - y0), None)), ("east", side), ("west", side), ("up", flat),
        ("down", flat)]))))
    return out


def cell_offset(col, row):
    return (16 * (MASTER_COLUMN - col), 16 * row)


def mount_models():
    """The ten block models (each part's pieces moved into its own block) and the item model (all of it at a fifth)."""
    pieces = mount_pieces()
    cells = OrderedDict(((c, r), []) for r in range(MOUNT_ROWS) for c in range(MOUNT_COLUMNS))
    for col, row, e in pieces:
        dx, dy = cell_offset(col, row)
        local = json.loads(json.dumps(e))
        local["from"] = [e["from"][0] - dx, e["from"][1] - dy, e["from"][2]]
        local["to"] = [e["to"][0] - dx, e["to"][1] - dy, e["to"][2]]
        assert all(0 <= v <= 16 for v in local["from"] + local["to"]), (col, row, local)
        cells[(col, row)].append(local)
    for key, elements in cells.items():
        clashes = zfight_check(elements)
        if clashes:
            sys.exit(f"giant fish mount {key} z-fighting: {clashes}")
    item = []
    for _, _, e in pieces:
        k = json.loads(json.dumps(e))
        k["from"] = [round(8 + (e["from"][0] - 8) * 0.2, 4), round(8 + (e["from"][1] - 16) * 0.2, 4), round(16 - (16 - e["from"][2]) * 0.2, 4)]
        k["to"] = [round(8 + (e["to"][0] - 8) * 0.2, 4), round(8 + (e["to"][1] - 16) * 0.2, 4), round(16 - (16 - e["to"][2]) * 0.2, 4)]
        for f in k["faces"].values():
            f.pop("cullface", None)
        item.append(k)
    clashes = zfight_check(item)
    if clashes:
        sys.exit(f"giant fish mount item z-fighting: {clashes}")
    return cells, item, pieces


BRASS = [(92, 61, 18), (138, 95, 31), (184, 134, 46), (217, 171, 69), (240, 207, 106), (255, 240, 168)]


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def wood_ramp(wood):
    """Six tones from the vanilla plank's own colours, darkest first (as tools/gen_fish_mount.py takes them)."""
    w, h, px = vanilla_png(f"block/{wood}_planks")
    px = sorted((c for c in px if c[3]), key=lum)
    out = []
    for q in (0.04, 0.2, 0.4, 0.6, 0.8, 0.97):
        i = int(q * (len(px) - 1))
        window = px[max(0, i - 6):i + 7]
        out.append(tuple(int(round(sum(c[k] for c in window) / len(window))) for k in range(3)))
    return out


def edges(cells, c, r):
    return ((c, r - 1) not in cells, (c - 1, r) not in cells, (c, r + 1) not in cells, (c + 1, r) not in cells)


def paint_mount():
    r = wood_ramp("dark_oak")
    img = Canvas(ATLAS, ATLAS)
    # The backboard's rim round the bevel panel, two pixels wide: lit top and left, in shadow bottom and right.
    x0, x1, y0, y1, _, _, insets, (ox, oy) = MOUNT_LAYERS["board"]
    cells = mask(x0, x1, y0, y1, insets)
    ring = {c for c in cells if any(edges(cells, *c))}
    inner = cells - ring
    for c, row in cells:
        if (c, row) in ring:
            top, left, bottom, right = edges(cells, c, row)
        else:
            top, left, bottom, right = edges(inner, c, row)
        lit, dark = top or left, bottom or right
        col = (r[4] if (c, row) in ring else r[3]) if lit and not dark else (r[1] if dark and not lit else r[2])
        img.set(ox + c, oy + row, col)
    # The bevel panel: a lit chamfer, the routed groove a pixel in, then the flat, with two long grain lines.
    x0, x1, y0, y1, _, _, insets, (ox, oy) = MOUNT_LAYERS["bevel"]
    cells = mask(x0, x1, y0, y1, insets)
    ring = {cell for cell in cells if any(edges(cells, *cell))}
    inner = cells - ring
    groove = {cell for cell in inner if any(edges(inner, *cell))}
    for c, row in cells:
        if (c, row) in ring:
            top, left, bottom, right = edges(cells, c, row)
            col = r[5] if (top or left) and not (bottom or right) else r[1] if (bottom or right) and not (top or left) else r[3]
        elif (c, row) in groove:
            top, left, bottom, right = edges(inner, c, row)
            col = r[2] if (bottom or right) and not (top or left) else r[0]
        else:
            col = r[3]
            if (row == 5 and 8 <= c <= x1 - x0 - 9) or (row == 22 and (6 <= c <= 24 or 51 <= c <= x1 - x0 - 7)):
                col = r[2]
        img.set(ox + c, oy + row, col)
    # The field: a soft top light, darker toward the bottom, its rim lit and shadowed, one long grain line.
    x0, x1, y0, y1, _, _, insets, (ox, oy) = MOUNT_LAYERS["field"]
    cells = mask(x0, x1, y0, y1, insets)
    for c, row in cells:
        top, left, bottom, right = edges(cells, c, row)
        col = r[4] if row < 3 else r[3]
        if top or left:
            col = r[5]
        elif bottom or right:
            col = r[2]
        elif row == 9 and 8 <= c <= x1 - x0 - 9:
            col = r[2]
        img.set(ox + c, oy + row, col)
    # Side strips: end grain with a light band along the front edge; long grain the same.
    img.fill(WOOD_V[0], WOOD_V[1], WOOD_V[0] + 2, WOOD_V[1] + 32, r[2])
    img.fill(WOOD_V[0], WOOD_V[1], WOOD_V[0] + 1, WOOD_V[1] + 32, r[3])
    img.fill(WOOD_H[0], WOOD_H[1], WOOD_H[0] + 80, WOOD_H[1] + 2, r[2])
    img.fill(WOOD_H[0], WOOD_H[1], WOOD_H[0] + 80, WOOD_H[1] + 1, r[3])
    # The brass plate: a bright top bevel, a dark bottom one, a screw head at each end.
    px, py = PLATE_ATLAS
    img.fill(px, py, px + 16, py + 4, BRASS[3])
    img.fill(px, py, px + 16, py + 1, BRASS[4])
    img.fill(px, py + 3, px + 16, py + 4, BRASS[2])
    img.set(px, py, BRASS[5])
    img.set(px + 15, py + 3, BRASS[1])
    for sx in (px + 1, px + 14):
        img.set(sx, py + 1, BRASS[1])
        img.set(sx, py + 2, BRASS[2])
    img.fill(BRASS_V[0], BRASS_V[1], BRASS_V[0] + 1, BRASS_V[1] + 4, BRASS[2])
    img.fill(BRASS_H[0], BRASS_H[1], BRASS_H[0] + 16, BRASS_H[1] + 1, BRASS[3])
    return img


MOUNT_CELLS, MOUNT_ITEM, MOUNT_PIECES = mount_models()
MOUNT_TEXTURE = paint_mount()
MOUNT_TEXTURE.save(os.path.join(TEX, "block", "giant_fish_mount.png"))
MODELS = os.path.join(ASSETS, "models")
for (c, r), elements in MOUNT_CELLS.items():
    write(os.path.join(MODELS, "block", f"giant_fish_mount_c{c}_r{r}.json"), OrderedDict([
        ("parent", "minecraft:block/block"),
        ("textures", OrderedDict([("particle", "minecraft:block/dark_oak_planks"), ("plaque", "tomyen:block/giant_fish_mount")])),
        ("elements", elements)]))
write(os.path.join(MODELS, "item", "giant_fish_mount.json"), OrderedDict([
    ("parent", "minecraft:block/block"),
    ("textures", OrderedDict([("particle", "minecraft:block/dark_oak_planks"), ("plaque", "tomyen:block/giant_fish_mount")])),
    ("display", OrderedDict([
        ("gui", OrderedDict([("rotation", [10, 160, 0]), ("translation", [0, 0, 0]), ("scale", [1.0, 1.0, 1.0])])),
        ("fixed", OrderedDict([("rotation", [0, 180, 0]), ("translation", [0, 0, -7]), ("scale", [0.9, 0.9, 0.9])])),
        ("ground", OrderedDict([("rotation", [0, 0, 0]), ("translation", [0, 3, 0]), ("scale", [0.5, 0.5, 0.5])])),
        ("thirdperson_righthand", OrderedDict([("rotation", [75, 45, 0]), ("translation", [0, 2.5, 0]), ("scale", [0.5, 0.5, 0.5])])),
        ("firstperson_righthand", OrderedDict([("rotation", [0, 135, 0]), ("translation", [0, 0, 0]), ("scale", [0.6, 0.6, 0.6])]))])),
    ("elements", MOUNT_ITEM)]))
write(os.path.join(ASSETS, "blockstates", "giant_fish_mount.json"), {"variants": OrderedDict(
    (f"column={c},facing={facing},row={r}", OrderedDict([("model", f"tomyen:block/giant_fish_mount_c{c}_r{r}")] + ([("y", y)] if y else [])))
    for c in range(MOUNT_COLUMNS) for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)) for r in range(MOUNT_ROWS))})
write(os.path.join(ASSETS, "items", "giant_fish_mount.json"),
      {"model": OrderedDict([("type", "minecraft:model"), ("model", "tomyen:item/giant_fish_mount")])})
print(f"giant fish mount: {len(MOUNT_PIECES)} elements over {len(MOUNT_CELLS)} blocks")


# ============================================================================================================ item sprites
# 16x16, in the style of the mod's other fishing sprites: a dark outline, lit from the top left, a few flat ramps.
OUTLINE = (24, 28, 16)


def axis_sprite(tail, head, widths, colour, outline, extra=None):
    """A shape laid along a line from tail to head: widths is [(u, half width)] along it (u 0 at the tail, 1 at the
    head). colour(u, v) gives each inside pixel's colour (v: pixels across it, negative on the upper left side); the
    outline goes round it. extra(img, inside) adds details after."""
    img = Canvas(16, 16)
    tx, ty = tail
    hx, hy = head
    L = math.hypot(hx - tx, hy - ty)
    ax, ay = (hx - tx) / L, (hy - ty) / L
    nx, ny = -ay, ax        # across, toward the lower right when the head is up and to the right

    def half(u):
        for (u0, w0), (u1, w1) in zip(widths, widths[1:]):
            if u0 <= u <= u1:
                return w0 + (w1 - w0) * (u - u0) / (u1 - u0)
        return -1.0
    inside = {}
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - tx, y + 0.5 - ty
            u = (px * ax + py * ay) / L
            v = px * nx + py * ny
            if abs(v) <= half(u):
                inside[(x, y)] = (u, v)
    for (x, y), (u, v) in inside.items():
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        img.set(x, y, outline if edge else colour(u, v))
    if extra:
        extra(img, inside)
    return img


def rows_sprite(rows, palette):
    img = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.set(x, y, palette[ch])
    return img


def recolour(path, mapping):
    w, h, px = read_png(open(os.path.join(TEX, path), "rb").read())
    img = Canvas(w, h)
    for i, c in enumerate(px):
        if c[3]:
            img.px[i] = mapping.get(c[:3], c[:3]) + (255,)
    return img


def trophy_sprite():
    """The Giant Catfish, as the mod draws its fish: on the diagonal, tail low left, head high right. Broad-headed and
    fat, a mottled olive back over a pale belly, a fan of a tail, whiskers swept back, and the harpoon in its back."""
    back, flank, belly, mott, fin = (54, 60, 34), (92, 94, 56), (208, 198, 160), (34, 40, 22), (74, 72, 44)
    tail, head = (0.8, 15.2), (14.8, 2.9)
    L = math.hypot(head[0] - tail[0], head[1] - tail[1])
    a = ((head[0] - tail[0]) / L, (head[1] - tail[1]) / L)
    n = (-a[1], a[0])

    def at(u, v):
        return (int(tail[0] + a[0] * u * L + n[0] * v), int(tail[1] + a[1] * u * L + n[1] * v))

    def colour(u, v):
        if u < 0.12:
            return fin if int(v + 10.0) % 2 == 0 else shade(fin, 0.78)
        if v > 2.1:
            return shade(belly, 0.88)
        if v > 0.9:
            return belly
        if v < -1.0:
            return mott if (round(u * 12) % 3 == 0 and v < -1.5) else back
        return flank

    def extra(img, inside):
        img.set(*at(0.86, -1.5), (196, 164, 66))                  # the eye
        for v in (-1.2, 0.0, 1.2):                                # the pale lip across its broad snout
            img.set(*at(0.985, v), (198, 184, 156))
        for u, v in ((0.93, -3.2), (0.88, -3.6), (0.83, -4.0), (0.77, -4.3), (0.71, -4.6)):    # whisker swept over its back
            img.set(*at(u, v), (62, 62, 42))
        for u, v in ((0.97, 3.0), (0.93, 3.6), (0.88, 4.2), (0.83, 4.6)):                     # and one under its chin
            img.set(*at(u, v), (62, 62, 42))
        img.set(*at(0.66, -4.8), (152, 142, 102))
        img.set(*at(0.79, 4.9), (152, 142, 102))
        for k, (u, v) in enumerate(((0.5, -2.9), (0.47, -3.9), (0.44, -4.9))):              # the old harpoon in its back
            img.set(*at(u, v), (182, 110, 54) if k == 2 else (138, 74, 36))
    return axis_sprite(tail, head, [(0.0, 3.2), (0.09, 1.4), (0.2, 1.7), (0.5, 3.2), (0.82, 3.7), (0.94, 3.3), (1.0, 2.2)],
                       colour, OUTLINE, extra)


def fillet_sprite(cooked):
    """A long fillet, thick at the head end: raw, pink with a pale seam and a strip of dark skin; cooked, a golden crust
    with flaky white showing along the seam and a darker, crisper edge underneath."""
    if cooked:
        light, mid, dark, seam, outline = (208, 152, 86), (178, 120, 62), (136, 84, 40), (240, 222, 184), (78, 44, 20)
    else:
        light, mid, dark, seam, outline = (248, 220, 212), (234, 186, 178), (208, 150, 144), (252, 240, 234), (150, 92, 92)

    def colour(u, v):
        if abs(v) < 0.5 and (not cooked or round(u * 12) % 3 != 0):
            return seam
        if not cooked and v < -1.9:
            return (128, 128, 112)                          # a strip of the dark skin along its back
        return light if v < -0.4 else mid if v < 1.4 else dark
    return axis_sprite((2.0, 13.8), (13.8, 2.6), [(0.0, 0.3), (0.15, 1.6), (0.5, 3.1), (0.88, 3.1), (1.0, 2.4)], colour, outline)


def whisker_sprite():
    img = Canvas(16, 16)
    tones = [(58, 60, 40), (86, 84, 56), (126, 118, 84), (176, 164, 122)]
    for k in range(400):
        t = k / 399.0
        # A curve from the root (bottom left) up and over to the tip (top right).
        x = (1 - t) ** 2 * 2.0 + 2 * (1 - t) * t * 3.5 + t * t * 14.0
        y = (1 - t) ** 2 * 14.0 + 2 * (1 - t) * t * 3.0 + t * t * 2.5
        width = 1.0 if t < 0.35 else 0.5
        for py in range(int(y - 2), int(y + 3)):
            for px in range(int(x - 2), int(x + 3)):
                if (px + 0.5 - x) ** 2 + (py + 0.5 - y) ** 2 <= width ** 2:
                    tone = 0 if t < 0.25 else 1 if t < 0.55 else 2 if t < 0.85 else 3
                    old = img.get(px, py)
                    if not old[3]:
                        img.set(px, py, tones[tone])
    # Light the thick root's upper left edge, as vanilla lights a stick.
    for y in range(8, 16):
        for x in range(16):
            if img.get(x, y)[3] and not img.get(x - 1, y)[3] and img.get(x + 1, y)[3]:
                img.set(x, y, tones[2])
    return img


def stink_bait_sprite():
    rows = [
        "................",
        "....g.....g.....",
        "...g.....g...g..",
        "....g.....g.g...",
        "...g.....g...g..",
        "......oooo......",
        "....ooAABBoo....",
        "...oAAABBBCCo...",
        "..oABBpBBCCCCo..",
        "..oABBBBBCCpCo..",
        "..oBBCCBBCCCDo..",
        "..oBpCCCCCCDDo..",
        "...oCCCCCDDDo...",
        "....ooDDDDoo....",
        "......oooo......",
        "................",
    ]
    return rows_sprite(rows, {"o": (40, 34, 20), "A": (150, 140, 82), "B": (118, 108, 60), "C": (92, 82, 46),
                              "D": (68, 60, 34), "p": (214, 134, 120), "g": (164, 196, 98)})


def egg_sprite():
    """The shopping cart egg's outline, repainted: dark olive shading toward the lower right, pale and dark spots."""
    w, h, cart = read_png(open(os.path.join(TEX, "item", "shopping_cart_spawn_egg.png"), "rb").read())
    mask_ = [p[3] > 0 for p in cart]
    xs = [i % w for i in range(w * h) if mask_[i]]
    ys = [i // w for i in range(w * h) if mask_[i]]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    base = [(118, 120, 76), (88, 92, 54), (62, 67, 38), (42, 47, 27)]
    pale, dark = (206, 196, 158), (30, 34, 20)
    spots = {(6, 4): pale, (7, 4): pale, (9, 7): pale, (5, 9): pale, (10, 11): pale, (11, 10): pale, (7, 12): dark,
             (8, 8): dark, (11, 5): dark, (4, 7): (150, 82, 38)}
    img = Canvas(w, h)
    for i in range(w * h):
        if not mask_[i]:
            continue
        x, y = i % w, i // w
        edge = not all(0 <= x + dx < w and 0 <= y + dy < h and mask_[(y + dy) * w + x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            img.px[i] = (26, 30, 16, 255)
            continue
        t = max(0.0, min(1.0, 0.5 - ((cx - x) + (cy - y)) / 14.0)) * (len(base) - 1)
        c = lerp(base[int(t)], base[min(len(base) - 1, int(t) + 1)], t - int(t))
        img.px[i] = spots.get((x, y), c) + (255,)
    return img


SPRITES = OrderedDict([
    ("giant_catfish", trophy_sprite()),
    ("catfish_fillet", fillet_sprite(False)),
    ("cooked_catfish_fillet", fillet_sprite(True)),
    ("catfish_whisker", whisker_sprite()),
    # The rubber line's spool, wound with whisker instead of rubber (the wooden spool kept).
    ("whisker_line", recolour("item/rubber_line.png", {(27, 28, 31): (34, 36, 22), (51, 53, 58): (66, 66, 42),
                                                         (85, 87, 93): (112, 106, 72)})),
    ("ancient_hook", recolour("item/tungsten_hook.png", {(108, 115, 126): (128, 66, 34), (167, 174, 184): (176, 106, 52),
                                                          (42, 45, 51): (52, 28, 18), (200, 200, 200): (206, 142, 82)})),
    ("stink_bait", stink_bait_sprite()),
    ("giant_catfish_spawn_egg", egg_sprite()),
])
for _name, _sprite in SPRITES.items():
    _sprite.save(os.path.join(TEX, "item", _name + ".png"))
    write(os.path.join(MODELS, "item", _name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + _name})]))
    write(os.path.join(ASSETS, "items", _name + ".json"),
          {"model": OrderedDict([("type", "minecraft:model"), ("model", "tomyen:item/" + _name)])})


# ============================================================================================================ data


def entry(item, low=1, high=1, looting=0.0, smelt=False):
    """A loot entry in 26.3's format: its functions under "modifier", a function's conditions one "condition"."""
    e = OrderedDict([("type", "minecraft:item"), ("name", item)])
    mods = []
    if low != 1 or high != 1:
        mods.append(OrderedDict([("type", "minecraft:set_count"),
                                 ("count", OrderedDict([("type", "minecraft:uniform"), ("min", low), ("max", high)]))]))
    if smelt:
        mods.append(OrderedDict([("type", "minecraft:furnace_smelt"), ("condition", OrderedDict([
            ("type", "minecraft:any_of"), ("terms", [
                OrderedDict([("type", "minecraft:entity_properties"), ("entity", "this"),
                             ("predicate", {"minecraft:flags": {"is_on_fire": True}})]),
                OrderedDict([("type", "minecraft:entity_properties"), ("entity", "direct_attacker"),
                             ("predicate", {"minecraft:equipment": {"mainhand": {"predicates": {
                                 "minecraft:enchantments": [{"enchantments": "#minecraft:smelts_loot"}]}}}})])])]))]))
    if looting:
        mods.append(OrderedDict([("type", "minecraft:enchanted_count_increase"),
                                 ("count", OrderedDict([("type", "minecraft:uniform"), ("min", 0.0), ("max", looting)])),
                                 ("enchantment", "minecraft:looting")]))
    if mods:
        e["modifier"] = mods
    return e


def loot_pool(e, condition=None):
    p = OrderedDict([("rolls", 1), ("entries", [e])])
    if condition:
        p["condition"] = condition
    return p


# The trophy itself isn't in the table: it's stamped with its weight and its killer's name, so GiantCatfish drops it.
write(os.path.join(DATA, "loot_table", "entities", "giant_catfish.json"), OrderedDict([
    ("type", "minecraft:entity"),
    ("pools", [
        loot_pool(entry("tomyen:catfish_fillet", 6, 10, looting=2.0, smelt=True)),
        loot_pool(entry("tomyen:catfish_whisker", 2, 4, looting=1.0)),
        loot_pool(entry("tomyen:ancient_hook"), {"type": "minecraft:killed_by_player"}),
        loot_pool(entry("minecraft:bone", 2, 5, looting=1.0)),
        loot_pool(entry("tomyen:rusty_scrap", 1, 3)),
    ]),
    ("random_sequence", "tomyen:entities/giant_catfish")]))
write(os.path.join(DATA, "loot_table", "blocks", "giant_fish_mount.json"), OrderedDict([
    ("type", "minecraft:block"),
    ("pools", [OrderedDict([("rolls", 1), ("entries", [{"type": "minecraft:item", "name": "tomyen:giant_fish_mount"}]),
                            ("condition", OrderedDict([("type", "minecraft:all_of"), ("terms", [
                                {"type": "minecraft:survives_explosion"},
                                OrderedDict([("type", "minecraft:block_state_property"), ("block", "tomyen:giant_fish_mount"),
                                             ("properties", OrderedDict([("column", str(MASTER_COLUMN)), ("row", "0")]))])])]))])]),
    ("random_sequence", "tomyen:blocks/giant_fish_mount")]))


def advancement(name, unlock, category):
    write(os.path.join(DATA, "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", OrderedDict([("conditions", {"items": [{"items": unlock}]}), ("trigger", "minecraft:inventory_changed")])),
            ("has_the_recipe", OrderedDict([("conditions", {"recipes": "tomyen:" + name}), ("trigger", "minecraft:recipe_unlocked")]))])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def recipe(name, obj, unlock, category):
    write(os.path.join(DATA, "recipe", name + ".json"), obj)
    advancement(name, unlock, category)


recipe("stink_bait", OrderedDict([
    ("type", "minecraft:crafting_shapeless"), ("category", "misc"),
    ("ingredients", ["minecraft:rotten_flesh", "minecraft:rotten_flesh", "tomyen:raw_shrimp", "tomyen:garlic"]),
    ("result", OrderedDict([("count", 4), ("id", "tomyen:stink_bait")]))]), "tomyen:raw_shrimp", "misc")
recipe("whisker_line", OrderedDict([
    ("type", "minecraft:crafting_shapeless"), ("category", "equipment"),
    ("ingredients", ["tomyen:catfish_whisker", "tomyen:catfish_whisker", "tomyen:fish_oil"]),
    ("result", OrderedDict([("count", 1), ("id", "tomyen:whisker_line")]))]), "tomyen:catfish_whisker", "tools")
recipe("giant_fish_mount", OrderedDict([
    ("type", "minecraft:crafting_shaped"), ("category", "building"),
    ("pattern", ["MMM", "PBP"]),
    ("key", OrderedDict([("M", ["tomyen:oak_fish_mount", "tomyen:spruce_fish_mount", "tomyen:dark_oak_fish_mount"]),
                         ("P", "tomyen:steel_plate"), ("B", "minecraft:gold_ingot")])),
    ("result", OrderedDict([("count", 1), ("id", "tomyen:giant_fish_mount")]))]), "tomyen:giant_catfish", "building")
for _kind, _time in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
    recipe("cooked_catfish_fillet" + ("" if _kind == "smelting" else "_from_" + _kind), OrderedDict([
        ("type", "minecraft:" + _kind), ("category", "food"), ("cookingtime", _time), ("experience", 0.35),
        ("ingredient", "tomyen:catfish_fillet"), ("result", {"id": "tomyen:cooked_catfish_fillet"})]), "tomyen:catfish_fillet", "food")

# In rivers, at night, on a steel rod or better: the rarest thing in the water (weight 1). Stink bait draws it in thirty
# times over. Every one is a trophy. Landing it doesn't land it: it comes up out of the water (FishingFights.surfacing).
write(os.path.join(DATA, "fishing_species", "giant_catfish.json"), OrderedDict([
    ("item", "tomyen:giant_catfish"), ("weight", 1), ("difficulty", 5), ("behavior", "thrasher"), ("min_rod_tier", 2),
    ("dimension", "minecraft:overworld"), ("biomes", ["#minecraft:is_river"]), ("time", "night"),
    ("size", OrderedDict([("min_lb", 180.0), ("avg_lb", 320.0), ("max_lb", 720.0), ("trophy_lb", 180.0)]))]))

write(os.path.join(DATA, "tags", "item", "river_monsters.json"), OrderedDict([("replace", False), ("values", ["tomyen:giant_catfish"])]))
write(os.path.join(DATA, "tags", "item", "giant_trophies.json"), OrderedDict([("replace", False), ("values", ["tomyen:giant_catfish"])]))
tag_add(os.path.join(DATA, "tags", "item", "raw_fish.json"), ["tomyen:catfish_fillet"])
tag_add(os.path.join(DATA_MC, "tags", "entity_type", "aquatic.json"), ["tomyen:giant_catfish"])
tag_add(os.path.join(DATA_MC, "tags", "block", "mineable", "axe.json"), ["tomyen:giant_fish_mount"])


def vanilla_sound(names, pitch, volume=None, **extra):
    out = []
    for n in names:
        e = OrderedDict([("name", "minecraft:" + n), ("pitch", pitch)])
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


ELDER_IDLE = [f"mob/guardian/elder_idle{i}" for i in range(1, 5)]
ELDER_HIT = [f"mob/guardian/elder_hit{i}" for i in range(1, 5)]
FLOP = [f"mob/guardian/flop{i}" for i in range(1, 5)]
merge(os.path.join(ASSETS, "sounds.json"), OrderedDict([
    event("giant_catfish_ambient", vanilla_sound(ELDER_IDLE, 0.5, 1.2, attenuation_distance=32)),
    event("giant_catfish_hurt", vanilla_sound(ELDER_HIT, 0.55, 1.2)),
    event("giant_catfish_death", vanilla_sound(["mob/guardian/elder_death"], 0.5, 1.6, attenuation_distance=48)),
    event("giant_catfish_roar", vanilla_sound(["mob/guardian/curse"], 0.42, 1.4, attenuation_distance=64)),
    event("giant_catfish_bite", vanilla_sound(["mob/ravager/bite1", "mob/ravager/bite2", "mob/ravager/bite3"], 0.65, 1.3)),
    event("giant_catfish_slap", vanilla_sound(FLOP, 0.4, 1.8) + vanilla_sound(["liquid/heavy_splash"], 0.7, 1.5)),
    event("giant_catfish_splash", vanilla_sound(["liquid/heavy_splash"], 0.55, 1.6, attenuation_distance=32)),
    event("giant_catfish_flop", vanilla_sound([f"entity/fish/flop{i}" for i in range(1, 5)], 0.4, 1.4)),
    event("giant_catfish_swim", vanilla_sound([f"liquid/swim{i}" for i in range(1, 6)], 0.6, 0.8), subtitle=False),
    event("giant_catfish_harpooned", vanilla_sound(ELDER_HIT, 0.85, 1.4)),
]))

merge(os.path.join(ASSETS, "lang", "en_us.json"), OrderedDict([
    ("entity.tomyen.giant_catfish", "Giant Catfish"),
    ("boss.tomyen.giant_catfish", "The River Monster"),
    ("item.tomyen.giant_catfish_spawn_egg", "Giant Catfish Spawn Egg"),
    ("item.tomyen.giant_catfish", "Giant Catfish"),
    ("item.tomyen.catfish_fillet", "Catfish Fillet"),
    ("item.tomyen.cooked_catfish_fillet", "Cooked Catfish Fillet"),
    ("item.tomyen.catfish_whisker", "Catfish Whisker"),
    ("item.tomyen.whisker_line", "Whisker Line"),
    ("item.tomyen.ancient_hook", "Ancient Hook"),
    ("item.tomyen.stink_bait", "Stink Bait"),
    ("block.tomyen.giant_fish_mount", "Giant Fish Mount"),
    ("block.tomyen.fish_mount.too_big", "Too big for this mount: it needs a Giant Fish Mount"),
    ("block.tomyen.giant_fish_mount.too_small", "Only a river monster goes up on a Giant Fish Mount"),
    ("tooltip.tomyen.giant_catfish", "Too big for a Fish Mount: hang it on a Giant Fish Mount"),
    ("tooltip.tomyen.catfish_whisker", "Tough, and it feels every ripple: braid it into a fishing line"),
    ("tooltip.tomyen.giant_fish_mount", "Needs a clear wall 5 blocks wide and 2 tall"),
    ("tooltip.tomyen.bait.attracts.river_monsters", "Draws in river monsters (at night, in rivers)"),
    ("tooltip.tomyen.bait.attracted_size.river_monsters", "Bigger river monsters +%s"),
    ("chat.tomyen.giant_catfish.surfaces", "Something enormous is coming up on your line..."),
    ("chat.tomyen.giant_catfish.landed", "You landed the River Monster: %s"),
    ("subtitles.tomyen.giant_catfish_ambient", "Giant Catfish rumbles"),
    ("subtitles.tomyen.giant_catfish_hurt", "Giant Catfish hurts"),
    ("subtitles.tomyen.giant_catfish_death", "Giant Catfish dies"),
    ("subtitles.tomyen.giant_catfish_roar", "Giant Catfish bellows"),
    ("subtitles.tomyen.giant_catfish_bite", "Giant Catfish bites"),
    ("subtitles.tomyen.giant_catfish_slap", "Giant Catfish slaps its tail"),
    ("subtitles.tomyen.giant_catfish_splash", "Something huge splashes"),
    ("subtitles.tomyen.giant_catfish_flop", "Giant Catfish flops"),
    ("subtitles.tomyen.giant_catfish_harpooned", "Giant Catfish is harpooned"),
]))


# ============================================================================================================ preview
# An orthographic software render: quads are textured one texel at a time, lit by a fixed light (a flat face shade by
# how squarely it faces the light, as the game shades entity faces), and z-buffered.

BG = (44, 52, 60)


def view_matrix(yaw, pitch):
    """World (model space, y down) to view: turn about the vertical by yaw, then tip by pitch (positive looks down)."""
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    Ry = [[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]]
    Rx = [[1, 0, 0], [0, cp, -sp], [0, sp, cp]]
    return mul(Rx, Ry)


class Image:
    def __init__(self, w, h, bg=BG):
        self.w, self.h = w, h
        self.px = [bg + (255,)] * (w * h)
        self.z = [1e18] * (w * h)


def render(img, qlist, view, cx, cy, scale, light=(0.35, -0.8, -0.5)):
    """qlist: (verts in model space, uv rect in texels (u0, v0, u1, v1), texture Canvas). The point (0, 0, 0) of view
    space lands at (cx, cy)."""
    lx, ly, lz = light
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    for verts, (ua, va, ub, vb), tex in qlist:
        A, B, C = verts[1], verts[0], verts[2]
        e1 = tuple(B[i] - A[i] for i in range(3))
        e2 = tuple(C[i] - A[i] for i in range(3))
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.6 + 0.4 * abs(n[0] * lx + n[1] * ly + n[2] * lz) / (nl * ln)
        nu, nv = max(1, int(round(abs(ub - ua)))), max(1, int(round(abs(vb - va))))
        su = 1 if ub >= ua else -1
        sv = 1 if vb >= va else -1
        for j in range(nv):
            for i in range(nu):
                tu = int(math.floor(ua + (i + 0.5) * su))
                tv = int(math.floor(va + (j + 0.5) * sv))
                col = tex.get(tu, tv)
                if col[3] == 0:
                    continue
                c = shade(col, lit)
                corners = []
                for a_, b_ in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                    s_, t_ = a_ / nu, b_ / nv
                    q = tuple(A[k] + e1[k] * s_ + e2[k] * t_ for k in range(3))
                    w_ = apply(view, q)
                    corners.append((cx + w_[0] * scale, cy + w_[1] * scale, w_[2]))
                fill(img, corners, sum(c_[2] for c_ in corners) / 4, c)


def fill(img, corners, depth, colour):
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    for py in range(max(0, int(min(ys))), min(img.h, int(math.ceil(max(ys))) + 1)):
        for px in range(max(0, int(min(xs))), min(img.w, int(math.ceil(max(xs))) + 1)):
            if inside(corners, px + 0.5, py + 0.5) and depth < img.z[py * img.w + px]:
                img.z[py * img.w + px] = depth
                img.px[py * img.w + px] = colour + (255,)


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


def fish_quads(p, tex=None):
    return [(verts, uv, tex or TEXTURE) for verts, uv in quads(p)]


def ground(img, view, cx, cy, scale):
    """A faint line where the ground is under the fish (y = 24), for the side views."""
    w_ = apply(view, (0.0, GROUND, 0.0))
    gy = int(cy + w_[1] * scale)
    if 0 <= gy < img.h and abs(view[1][1]) > 0.9:
        for x in range(img.w):
            if img.z[gy * img.w + x] > 1e17:
                img.px[gy * img.w + x] = (64, 74, 84, 255)


def panel(p, yaw, pitch, w=520, h=260, scale=5.6, focus=(0.0, 16.0, 6.0), tex=None, extra=()):
    img = Image(w, h)
    view = view_matrix(yaw, pitch)
    f = apply(view, focus)
    cx, cy = w / 2 - f[0] * scale, h / 2 - f[1] * scale
    render(img, fish_quads(p, tex) + list(extra), view, cx, cy, scale)
    ground(img, view, cx, cy, scale)
    return img


def board(images, cols):
    w = max(i.w for i in images)
    h = max(i.h for i in images)
    rows = (len(images) + cols - 1) // cols
    out = Image(w * cols, h * rows, (26, 30, 36))
    for k, im in enumerate(images):
        bx, by = (k % cols) * w, (k // cols) * h
        for y in range(im.h):
            for x in range(im.w):
                out.px[(by + y) * out.w + bx + x] = im.px[y * im.w + x]
    return out


def texture_panel(tex, k, w, h):
    img = Image(w, h, (36, 40, 46))
    for y in range(tex.h * k):
        for x in range(tex.w * k):
            c = tex.get(x // k, y // k)
            if c[3]:
                img.px[(4 + y) * w + 4 + x] = c
            elif ((x // 8) + (y // 8)) % 2:
                img.px[(4 + y) * w + 4 + x] = (48, 52, 60, 255)
    return img


def entity_preview(path):
    side = math.pi / 2           # looking at its right flank (from -x)
    views = [
        panel(pose(), -side, 0.0),                                             # right flank
        panel(pose(), side, 0.0),                                              # left flank (harpoon)
        panel(pose(), -side + 0.0, math.pi / 2 - 0.001, h=300),               # from above
        panel(pose(), -side, -math.pi / 2 + 0.001, h=300),                    # from below
        panel(pose(), -0.75, 0.42, h=300),                                     # three-quarters, front
        panel(pose(), 2.4, 0.35, h=300),                                       # three-quarters, from behind on the left
        panel(pose(swim=1.0, tail=1.2, age=10), -side + 0.001, math.pi / 2 - 0.001, h=300),   # swimming, from above
        panel(pose(swim=1.0, tail=3.0, age=14, mouth=0.7), -0.9, 0.3, h=300),                 # charging
        panel(pose(slap=0.3, slap_side=1.0, age=8), -side, math.pi / 2 - 0.001, h=300),       # slap: wind-up
        panel(pose(slap=0.5, slap_side=1.0, age=10), -side, math.pi / 2 - 0.001, h=300),      # slap: strike
        panel(pose(mouth=0.35, thrash=1.0, age=5, swim=0.6, tail=2.0), -0.7, 0.3, h=300),      # dragging
        panel(pose(mouth=1.0, roar=1.0, age=3), -1.0, 0.15, h=300),                            # roaring
        panel(pose(), -0.6, 0.25, w=520, h=300, scale=13.0, focus=(0.0, 16.0, -26.0 + ZS)),  # the head up close
        panel(pose(mouth=0.9), -0.35, -0.25, w=520, h=300, scale=13.0, focus=(0.0, 18.0, -28.0 + ZS)),  # into the mouth
        texture_panel(TEXTURE, 2, 520, 300),
    ]
    out = board(views, 3)
    png(path, out.w, out.h, out.px)


# --- The Giant Fish Mount with a trophy on it. Everything goes into the render frame from world pixels (y up) by a half
# turn about z, (x, y, z) -> (-x, -y, z), the same turn that takes the game's world to entity model space, so a viewer
# looking along +z sees the mount's front with its own left (+x) on their left.

FACE_CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}

# The fish on the mount, as GiantFishMountRenderer lays it out (in blocks from the master block's centre, north-facing):
# its middle at the field's middle, its right flank just off the field, the length from its weight.
FIELD_FRONT = 13.0 / 16.0 - 0.5
MOUNT_FISH_Y = 18.5 / 16.0 - 0.5


def mount_length(weight, average=320.0):
    """GiantFishMountRenderer.length: blocks, from the trophy's weight."""
    return max(3.1, min(4.4, 3.6 * (weight / average) ** 0.3))


def mounted_fish_quads(weight):
    p = pose(mounted=True)
    qs = quads(p)
    pts = [q for verts, _ in qs for q in verts]
    zmin, zmax = min(q[2] for q in pts), max(q[2] for q in pts)
    ymin, ymax = min(q[1] for q in pts), max(q[1] for q in pts)
    cz, cy = (zmin + zmax) / 2, (ymin + ymax) / 2
    s = mount_length(weight) * 16.0 / (zmax - zmin)          # world pixels per model pixel
    back = 13.0                                               # the right flank's deepest point (the gill covers)
    fz = (FIELD_FRONT - 0.01) * 16.0 - s * back

    def world(q):
        # Java: translate(0, FISH_Y, fz), scale(s), rotate Y -90, rotate Z 180, translate(0, -cy, -cz):
        # (x, y, z) -> (-(z - cz), -(y - cy), -x).
        return (8.0 - s * (q[2] - cz), 8.0 + MOUNT_FISH_Y * 16.0 - s * (q[1] - cy), 8.0 + fz - s * q[0])
    return [([to_render(world(q)) for q in verts], uv, TEXTURE) for verts, uv in qs]


def to_render(w):
    return (-w[0], -w[1], w[2])


def mount_quads():
    out = []
    for col, row, e in MOUNT_PIECES:
        a, b = e["from"], e["to"]
        for name, face in e["faces"].items():
            c = FACE_CORNERS[name](a, b)
            u0, v0, u1, v1 = (v * ATLAS / 16.0 for v in face["uv"])
            # render() reads corner 1 as (u0, v0), corner 0 along u, corner 2 along v.
            out.append(([to_render(c[1]), to_render(c[0]), to_render(c[3]), to_render(c[2])], (u0, v0, u1, v1), MOUNT_TEXTURE))
    return out


def wall_quads():
    """A stone wall behind the mount for context: one flat face at z = 16, five blocks by three."""
    grey = Canvas(1, 1, (96, 98, 104, 255))
    return [([to_render((48.0, 40.0, 16.0)), to_render((-32.0, 40.0, 16.0)), to_render((48.0, -8.0, 16.0)),
              to_render((-32.0, -8.0, 16.0))], (0, 0, 1, 1), grey)]


def mount_preview(weight, yaw, pitch, w=780, h=420, scale=8.5, fish=True):
    img = Image(w, h)
    view = view_matrix(yaw, pitch)
    f = apply(view, to_render((8.0, 16.0, 14.0)))
    cx, cy = w / 2 - f[0] * scale, h / 2 - f[1] * scale
    qs = mount_quads() + wall_quads() + (mounted_fish_quads(weight) if fish else [])
    render(img, qs, view, cx, cy, scale)
    return img


def sprites_panel(w, h, k=8):
    img = Image(w, h, (36, 40, 46))
    for n, (name, sprite) in enumerate(SPRITES.items()):
        bx, by = 12 + (n % 4) * (16 * k + 14), 12 + (n // 4) * (16 * k + 14)
        for y in range(16 * k):
            for x in range(16 * k):
                c = sprite.get(x // k, y // k)
                checker = (56, 60, 68, 255) if ((x // 16) + (y // 16)) % 2 else (48, 52, 60, 255)
                img.px[(by + y) * w + bx + x] = c if c[3] else checker
    return img


if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)
    entity_preview(os.path.join(PREVIEW, "preview_giant_catfish.png"))
    mounts = [mount_preview(412.0, 0.0, 0.0), mount_preview(650.0, 0.6, 0.25), mount_preview(0.0, -0.5, 0.2, fish=False),
              sprites_panel(780, 420)]
    out_ = board(mounts, 2)
    png(os.path.join(PREVIEW, "preview_giant_fish_mount.png"), out_.w, out_.h, out_.px)
    print("preview written to", PREVIEW)
print("done")
