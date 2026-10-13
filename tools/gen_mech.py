"""Paints the Bulwark Walker and its Mech Gantry, and writes everything around them.

The walker and the gantry frame are code-defined models (MechModel, MechGantryModel) sharing one 256x256 texture
and a glow texture. This script is where their geometry lives: SPEC lists every part and box (pivots, offsets and
sizes in model pixels, y down, front at -z, feet at y 24), UVs are packed from it, and `--java` prints the two
createLayer bodies to paste into the Java models, so the texture and the models can't drift apart.

Style: as few boxes as hold the silhouette (a hulking sarcophagus torso with a lifting chest hatch, huge
pauldrons, stubby legs, weapon forearms), four flat materials (slate armour, charcoal frame, brass trim, gunmetal)
shaded with smooth ramps and a one-pixel edge light; no noise. The glow texture holds the amber viewport and visor,
the teal power plant window and the jet flames.

Also: the part, ammo and gantry icons (16x16), their item models and definitions, the gantry's blockstate, block
model and deck texture, and the crafting recipes with their recipe-book advancements.

`--preview PATH` renders the walker from the front and the back, and assembled in its gantry, to a PNG.

Usage: python3 tools/gen_mech.py src/main/resources/assets/mymod [--java] [--preview PATH]"""
import json, math, os, struct, sys, zlib

ASSETS = sys.argv[1]  # .../assets/mymod
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "mymod"))
TEX = os.path.join(ASSETS, "textures")
CLEAR = (0, 0, 0, 0)
SIZE = 256


def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def rgb(c, a=255):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, a)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)

    def save(self, path):
        png(path, self.w, self.h, self.px)


# --- Materials ----------------------------------------------------------------------------------------------------
# Four flat materials; each face gets a vertical ramp (light at the top, dark at the bottom) and a one-pixel edge.
ARMOR = rgb(0x5D6E84)
FRAME = rgb(0x393C43)
TRIM = rgb(0xB08D57)
GUN = rgb(0x4A4E55)
GLASS = rgb(0x1B2026)
BORE = rgb(0x121417)
AMBER = rgb(0xFFB347)
TEAL = rgb(0x4FE3CF)
FLAME_HOT = rgb(0xFFF1A8)
FLAME_COOL = rgb(0xFF6A1A)
MATERIALS = {"armor": ARMOR, "frame": FRAME, "trim": TRIM, "gun": GUN, "flame": FLAME_COOL}

# --- The spec -------------------------------------------------------------------------------------------------------
# A part: (name, parent, (px, py, pz) pivot, (rx, ry, rz) rest rotation, [boxes]). A box: (x, y, z, w, h, d, material,
# decals) where decals name things painted on particular faces.


def part(name, parent, pivot, boxes, rot=(0.0, 0.0, 0.0)):
    return {"name": name, "parent": parent, "pivot": pivot, "rot": rot, "boxes": boxes}


def box(x, y, z, w, h, d, material, *decals):
    return {"at": (x, y, z), "size": (w, h, d), "mat": material, "decals": decals}


def leg(side):
    """side: 1 for the left leg (model +x), -1 for the right."""
    s = "left" if side > 0 else "right"
    rungs = [box(-3, 3, -8, 6, 1, 2, "trim"), box(-3, 8, -8, 6, 1, 2, "trim")] if side > 0 else []
    parts = [part(s + "_leg", "legs", (8 * side, -31, 0), [box(-5.5, 0, -6, 11, 13, 12, "armor")] + rungs),
             part(s + "_shin", s + "_leg", (0, 12, 0),
                  [box(-6, 0, -6.5, 12, 16, 13, "armor"), box(-5, -3, -9.5, 10, 9, 3, "armor")]
                  + ([box(-3, 11, -8.5, 6, 1, 2, "trim")] if side > 0 else [])),
             part(s + "_foot", s + "_shin", (0, 16, 0), [box(-7, 0, -11, 14, 3, 18, "frame")])]
    return parts


def weapons(side):
    """Every weapon a forearm can carry, each its own child group; the model shows the fitted one."""
    s = "left" if side > 0 else "right"
    fore = s + "_forearm"
    return [
        part(s + "_none", fore, (0, 0, 0), [box(-4, -4, -10, 8, 8, 12, "frame", "plate")]),
        part(s + "_autocannon", fore, (0, 0, 0), [box(-5, -5, -14, 10, 10, 18, "gun"), box(-3, -9, -10, 6, 4, 8, "frame")]),
        part(s + "_autocannon_barrel", s + "_autocannon", (0, 0, -14),
             [box(-2, -2, -16, 4, 4, 16, "gun"), box(-3, -3, -19, 6, 6, 3, "frame", "bore")]),
        part(s + "_rotary_cannon", fore, (0, 0, 0), [box(-5, -5, -10, 10, 10, 12, "gun"), box(-4, -4, -12, 8, 8, 2, "frame")]),
        part(s + "_rotary_barrels", s + "_rotary_cannon", (0, 0, -12), [box(-3, -3, -14, 6, 6, 14, "gun", "barrels")]),
        part(s + "_power_fist", fore, (0, 0, 0), [box(-5, -5, -12, 10, 10, 14, "armor")]),
        part(s + "_palm", s + "_power_fist", (0, 0, -12), [box(-6, -6, -7, 12, 12, 7, "armor")]),
        part(s + "_fingers", s + "_palm", (0, -6, -7), [box(-6, 0, -4, 12, 11, 4, "gun", "knuckles")]),
        part(s + "_rocket_pod", fore, (0, 0, 0), [box(-6, -6, -13, 12, 12, 15, "armor", "tubes")]),
        part(s + "_flamer", fore, (0, 0, 0), [box(-4, -4, -10, 8, 8, 12, "gun"), box(-2.5, -9, -8, 5, 5, 9, "trim"),
                                              box(-1.5, -1.5, -22, 3, 3, 12, "gun"), box(-2, -2, -24, 4, 4, 2, "frame", "bore")]),
    ]


def arm(side):
    s = "left" if side > 0 else "right"
    return [part(s + "_arm", "torso", (16 * side, -19, 0), [box(-4, 0, -4, 8, 11, 8, "frame")]),
            part(s + "_forearm", s + "_arm", (0, 10, 0), [])] + weapons(side)


MECH = (
    [part("legs", None, (0, 24, 0), [box(-11, -37, -7, 22, 8, 14, "frame")])]
    + leg(1) + leg(-1)
    + [part("torso", None, (0, -13, 0), [
        box(-8, -3, -7, 16, 4, 14, "frame"),
        box(-12, -27, -10, 24, 24, 20, "armor", "viewport"),
        box(-5, -34, -11, 10, 7, 10, "armor", "visor")]),
       part("hatch", "torso", (0, -23, -10), [box(-9, 0, -3, 18, 18, 3, "armor", "hatch")]),
       part("left_pauldron", "torso", (0, 0, 0), [box(9.5, -32, -10.5, 12, 14, 21, "armor"), box(10.5, -35, -8.5, 10, 3, 17, "armor"),
                                                  box(8.5, -20, -11.5, 14, 2, 23, "trim")]),
       part("right_pauldron", "torso", (0, 0, 0), [box(-21.5, -32, -10.5, 12, 14, 21, "armor"), box(-20.5, -35, -8.5, 10, 3, 17, "armor"),
                                                   box(-22.5, -20, -11.5, 14, 2, 23, "trim")]),
       part("power_plant", "torso", (0, 0, 0), [box(-9, -25, 10, 18, 18, 7, "armor", "core"),
                                                box(-7.5, -7, 11, 5, 5, 5, "gun", "nozzle"), box(2.5, -7, 11, 5, 5, 5, "gun", "nozzle")]),
       part("jet_flames", "power_plant", (0, 0, 0), [box(-7, -2, 12, 4, 7, 4, "flame"), box(3, -2, 12, 4, 7, 4, "flame")]),
       part("cargo_pod", "torso", (0, 0, 0), [box(-9, -35, 4, 18, 10, 12, "armor", "crate")]),
       part("smoke_launcher", "torso", (0, -30, 11), [box(-7, -4, -4, 14, 5, 8, "gun", "smoke")], rot=(-0.35, 0.0, 0.0))]
    + arm(1) + arm(-1))

GANTRY = [
    part("frame", None, (0, 24, 0), [
        box(-24, -3, -20, 48, 3, 40, "frame", "deck"),
        box(-24, -90, 16, 5, 87, 5, "frame"),
        box(19, -90, 16, 5, 87, 5, "frame"),
        box(-24, -94, 15, 48, 4, 7, "trim"),
        box(-24, -80, -12, 4, 4, 28, "gun"),
        box(20, -80, -12, 4, 4, 28, "gun")]),
]


# --- UV packing ------------------------------------------------------------------------------------------------------
def uv_size(b):
    w, h, d = (int(math.ceil(v)) for v in b["size"])
    return 2 * (w + d), d + h


def pack():
    """Skyline packing: each box's region goes wherever along the top edge leaves it highest up. Boxes of the same
    size, material and decals (the two sides' limbs and weapons) share a region."""
    boxes = [b for p in MECH + GANTRY for b in p["boxes"]]
    shared = {}
    for b in boxes:
        shared.setdefault((b["size"], b["mat"], b["decals"]), []).append(b)
    order = sorted(shared.values(), key=lambda group: (-uv_size(group[0])[1], -uv_size(group[0])[0]))
    sky = [0] * SIZE
    for group in order:
        bw, bh = uv_size(group[0])
        best = None
        for x in range(0, SIZE - bw + 1):
            y = max(sky[x:x + bw])
            if best is None or y < best[1]:
                best = (x, y)
        x, y = best
        assert y + bh <= SIZE, "texture overflow placing %s" % (group[0],)
        for b in group:
            b["uv"] = (x, y)
        for i in range(x, x + bw):
            sky[i] = y + bh


def faces(b):
    """Face rectangles of a box's texture (x0, y0, x1, y1), named as seen on the walker: top, bottom, the -x side
    (the walker's right), front (-z), the +x side and back."""
    u, v = b["uv"]
    w, h, d = (int(math.ceil(s)) for s in b["size"])
    return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "xneg": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
            "xpos": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


# --- Painting --------------------------------------------------------------------------------------------------------
def paint_face(c, rect, base, face, ramp=True):
    x0, y0, x1, y1 = rect
    h = max(1, y1 - y0)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if face == "top":
                f = 1.16
            elif face == "bottom":
                f = 0.62
            else:
                t = (y - y0) / max(1, h - 1)
                f = (1.07 - 0.25 * t) if ramp else 0.95
            col = shade(base, f)
            # One-pixel edge: lit along the top, shadowed along the bottom, a touch darker at the sides.
            if y == y0 and face not in ("bottom",):
                col = shade(col, 1.22)
            elif y == y1 - 1 and face not in ("top", "bottom"):
                col = shade(col, 0.72)
            elif x in (x0, x1 - 1) and x1 - x0 > 2:
                col = shade(col, 0.9)
            c.set(x, y, col)


def hole(c, glow, x0, y0, x1, y1):
    """A dark round-cornered opening."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            corner = (x in (x0, x1 - 1)) and (y in (y0, y1 - 1)) and x1 - x0 > 2
            if not corner:
                c.set(x, y, BORE)


def decal(c, glow, b, name):
    f = faces(b)
    if name == "viewport":
        x0, y0, x1, y1 = f["front"]
        for x in range(x0 + 4, x1 - 4):
            for y in (y0 + 1, y0 + 2):
                c.set(x, y, GLASS)
                glow.set(x, y, AMBER if y == y0 + 2 else shade(AMBER, 0.8))
            c.set(x, y0 + 3, shade(ARMOR, 0.7))
    elif name == "visor":
        x0, y0, x1, y1 = f["front"]
        for x in range(x0 + 2, x1 - 2):
            c.set(x, y0 + 3, GLASS)
            glow.set(x, y0 + 3, AMBER)
    elif name == "hatch":
        x0, y0, x1, y1 = f["front"]
        for x in range(x0, x1):
            for y in range(y0, y1):
                if x in (x0, x1 - 1) or y in (y0, y1 - 1):
                    c.set(x, y, shade(TRIM, 1.05 - 0.2 * (y - y0) / (y1 - y0)))
        # Two heavy ribs down the sarcophagus lid.
        for x in (x0 + 5, x1 - 6):
            for y in range(y0 + 2, y1 - 2):
                c.set(x, y, shade(c.get(x, y), 1.18))
                c.set(x + 1, y, shade(c.get(x + 1, y), 0.82))
    elif name == "core":
        x0, y0, x1, y1 = f["back"]
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2 - 1
        for y in range(cy - 4, cy + 4):
            for x in range(cx - 4, cx + 4):
                ring = x in (cx - 4, cx + 3) or y in (cy - 4, cy + 3)
                corner = x in (cx - 4, cx + 3) and y in (cy - 4, cy + 3)
                if corner:
                    continue
                if ring:
                    c.set(x, y, TRIM)
                else:
                    t = math.hypot(x - cx + 0.5, y - cy + 0.5) / 4.0
                    col = mix(rgb(0xD8FFF8), TEAL, min(1.0, t * 1.2))
                    c.set(x, y, col)
                    glow.set(x, y, col)
    elif name == "nozzle":
        x0, y0, x1, y1 = f["bottom"]
        hole(c, glow, x0 + 1, y0 + 1, x1 - 1, y1 - 1)
    elif name == "bore":
        x0, y0, x1, y1 = f["front"]
        hole(c, glow, x0 + 1, y0 + 1, x1 - 1, y1 - 1)
    elif name == "barrels":
        x0, y0, x1, y1 = f["front"]
        for (x, y) in ((x0 + 1, y0 + 1), (x0 + 3, y0 + 1), (x0 + 1, y0 + 3), (x0 + 3, y0 + 3)):
            hole(c, glow, x, y, x + 2, y + 2)
        # Barrel grooves along the sides.
        for side in ("top", "bottom", "xneg", "xpos"):
            sx0, sy0, sx1, sy1 = f[side]
            if side in ("top", "bottom"):
                for y in range(sy0, sy1):
                    c.set(sx0 + 2, y, shade(GUN, 0.6))
                    c.set(sx1 - 3, y, shade(GUN, 0.6))
            else:
                for x in range(sx0, sx1):
                    c.set(x, sy0 + 2, shade(GUN, 0.6))
                    c.set(x, sy1 - 3, shade(GUN, 0.6))
    elif name == "knuckles":
        x0, y0, x1, y1 = f["front"]
        for x in (x0 + 3, x0 + 6, x0 + 9):
            for y in range(y0 + 1, y1 - 1):
                c.set(x, y, shade(GUN, 0.6))
        for x in range(x0, x1):
            c.set(x, y0, TRIM)
    elif name == "tubes":
        x0, y0, x1, y1 = f["front"]
        for (x, y) in ((x0 + 1, y0 + 1), (x0 + 7, y0 + 1), (x0 + 1, y0 + 7), (x0 + 7, y0 + 7)):
            hole(c, glow, x, y, x + 4, y + 4)
            c.set(x + 1, y + 1, rgb(0x6E3A2A))
    elif name == "smoke":
        x0, y0, x1, y1 = f["top"]
        for x in (x0 + 1, x0 + 4, x0 + 8, x0 + 11):
            hole(c, glow, x, y0 + 2, x + 2, y0 + 5)
    elif name == "plate":
        x0, y0, x1, y1 = f["front"]
        for x in range(x0 + 1, x1 - 1):
            for y in range(y0 + 1, y1 - 1):
                c.set(x, y, shade(TRIM, 0.85))
    elif name == "crate":
        for side in ("xneg", "xpos", "back", "front"):
            x0, y0, x1, y1 = f[side]
            for x in range(x0, x1):
                c.set(x, y0 + 2, TRIM)
                c.set(x, y1 - 3, shade(TRIM, 0.8))
    elif name == "deck":
        x0, y0, x1, y1 = f["top"]
        # Brass edging round the deck and a walkway of plates.
        for x in range(x0, x1):
            for y in range(y0, y1):
                if x in (x0, x0 + 1, x1 - 1, x1 - 2) or y in (y0, y0 + 1, y1 - 1, y1 - 2):
                    c.set(x, y, TRIM if (x + y) // 3 % 2 == 0 else shade(TRIM, 0.7))
                elif (x - x0) % 12 == 0 or (y - y0) % 12 == 0:
                    c.set(x, y, shade(FRAME, 0.8))


def paint():
    c = Canvas(SIZE, SIZE)
    glow = Canvas(SIZE, SIZE)
    for p in MECH + GANTRY:
        for b in p["boxes"]:
            base = MATERIALS[b["mat"]]
            for name, rect in faces(b).items():
                if b["mat"] == "flame":
                    x0, y0, x1, y1 = rect
                    for y in range(y0, y1):
                        for x in range(x0, x1):
                            col = mix(FLAME_HOT, FLAME_COOL, (y - y0) / max(1, y1 - y0 - 1))
                            c.set(x, y, col)
                            glow.set(x, y, col)
                else:
                    paint_face(c, rect, base, name)
            for d in b["decals"]:
                decal(c, glow, b, d)
    return c, glow


# --- Java ----------------------------------------------------------------------------------------------------------------
def jf(v):
    return ("%.1f" % v) + "f"


def java(spec, root_var="root"):
    parents = {p["parent"] for p in spec}
    lines = []
    for p in spec:
        parent = root_var if p["parent"] is None else p["parent"]
        cubes = "CubeListBuilder.create()"
        for b in p["boxes"]:
            (x, y, z), (w, h, d) = b["at"], b["size"]
            cubes += "\n\t\t\t\t.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)" % (b["uv"] + tuple(jf(v) for v in (x, y, z, w, h, d)))
        px, py, pz = p["pivot"]
        rx, ry, rz = p["rot"]
        pose = ("PartPose.offset(%s, %s, %s)" % (jf(px), jf(py), jf(pz)) if (rx, ry, rz) == (0, 0, 0)
                else "PartPose.offsetAndRotation(%s, %s, %s, %sf, %sf, %sf)" % (jf(px), jf(py), jf(pz), rx, ry, rz))
        declare = "PartDefinition %s = " % camel(p["name"]) if p["name"] in parents else ""
        lines.append("\t\t%s%s.addOrReplaceChild(\"%s\", %s,\n\t\t\t\t%s);" % (declare, camel(parent), p["name"], cubes, pose))
    return "\n".join(lines)


def camel(name):
    head, *rest = name.split("_")
    return head + "".join(s.capitalize() for s in rest)


# --- Preview ---------------------------------------------------------------------------------------------------------------
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rotation(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rxm = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    rym = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    rzm = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(mat_mul(rzm, rym), rxm)


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def world_boxes(spec, show, poses, origin=(0.0, 0.0, 0.0)):
    """Every visible box as (corners, box), in model space (y down), with the parts posed."""
    mats = {}
    out = []
    for p in spec:
        if not show(p["name"]):
            continue
        parent = mats.get(p["parent"], translate(*origin))
        if p["parent"] is not None and p["parent"] not in mats:
            continue
        px, py, pz = p["pivot"]
        rx, ry, rz = poses.get(p["name"], p["rot"])
        m = mat_mul(mat_mul(parent, translate(px, py, pz)), rotation(rx, ry, rz))
        mats[p["name"]] = m
        for b in p["boxes"]:
            (x, y, z), (w, h, d) = b["at"], b["size"]
            corners = [apply(m, (x + w * i, y + h * j, z + d * k)) for i in (0, 1) for j in (0, 1) for k in (0, 1)]
            out.append((corners, b))
    return out


# Corner index = i*4 + j*2 + k for (x, y, z) at (min/max). Each face: four corners in the order vanilla maps to
# (u2, v1), (u1, v1), (u1, v2), (u2, v2) of its rectangle.
FACE_CORNERS = {
    "top": (5, 1, 0, 4),      # DOWN in model space (min y): (x1,y0,z1) (x0,y0,z1) (x0,y0,z0) (x1,y0,z0)
    "bottom": (6, 2, 3, 7),   # UP: (x1,y1,z0) (x0,y1,z0) (x0,y1,z1) (x1,y1,z1)
    "xneg": (0, 1, 3, 2),     # WEST: (x0,y0,z0) (x0,y0,z1) (x0,y1,z1) (x0,y1,z0)
    "front": (4, 0, 2, 6),    # NORTH: (x1,y0,z0) (x0,y0,z0) (x0,y1,z0) (x1,y1,z0)
    "xpos": (5, 4, 6, 7),     # EAST: (x1,y0,z1) (x1,y0,z0) (x1,y1,z0) (x1,y1,z1)
    "back": (1, 5, 7, 3),     # SOUTH: (x0,y0,z1) (x1,y0,z1) (x1,y1,z1) (x0,y1,z1)
}


def render(scene, tex, glow, yaw, pitch, w, h, scale, centre):
    img = [rgb(0x2B3138)] * (w * h)
    depth = [1e9] * (w * h)
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    light = (-0.45, 0.75, -0.5)
    ln = math.sqrt(sum(v * v for v in light))
    light = tuple(v / ln for v in light)

    def view(p):
        # Model space (y down) to display (y up, x flipped as the entity renderer does), turned and tilted.
        x, y, z = -p[0], 24.0 - p[1], p[2]
        x, z = x * cy_ - z * sy_, x * sy_ + z * cy_
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    for corners, b in scene:
        v = [view(c) for c in corners]
        for name, idx in FACE_CORNERS.items():
            x0, y0, x1, y1 = faces(b)[name]
            uvs = [(x1, y0), (x0, y0), (x0, y1), (x1, y1)]
            q = [v[i] for i in idx]
            e1 = [q[1][k] - q[0][k] for k in range(3)]
            e2 = [q[3][k] - q[0][k] for k in range(3)]
            n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
            nl = math.sqrt(sum(c * c for c in n)) or 1.0
            n = tuple(c / nl for c in n)
            lit = 0.55 + 0.5 * max(0.0, sum(n[k] * light[k] for k in range(3)))
            for tri in ((0, 1, 2), (0, 2, 3)):
                raster(img, depth, w, h, [q[i] for i in tri], [uvs[i] for i in tri], (x0, y0, x1, y1), tex, glow, lit, scale, centre)
    return img


def raster(img, depth, w, h, pts, uvs, rect, tex, glow, lit, scale, centre):
    sp = [(centre[0] + p[0] * scale, centre[1] - p[1] * scale, p[2]) for p in pts]
    minx, maxx = max(0, int(min(p[0] for p in sp))), min(w - 1, int(max(p[0] for p in sp)) + 1)
    miny, maxy = max(0, int(min(p[1] for p in sp))), min(h - 1, int(max(p[1] for p in sp)) + 1)
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = sp
    area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(area) < 1e-6:
        return
    for py in range(miny, maxy + 1):
        for px in range(minx, maxx + 1):
            x, y = px + 0.5, py + 0.5
            w0 = ((bx - x) * (cy - y) - (cx - x) * (by - y)) / area
            w1 = ((cx - x) * (ay - y) - (ax - x) * (cy - y)) / area
            w2 = 1.0 - w0 - w1
            if w0 < -1e-4 or w1 < -1e-4 or w2 < -1e-4:
                continue
            z = w0 * az + w1 * bz + w2 * cz
            i = py * w + px
            if z >= depth[i]:
                continue
            u = w0 * uvs[0][0] + w1 * uvs[1][0] + w2 * uvs[2][0]
            v = w0 * uvs[0][1] + w1 * uvs[1][1] + w2 * uvs[2][1]
            tu = min(rect[2] - 1, max(rect[0], int(u)))
            tv = min(rect[3] - 1, max(rect[1], int(v)))
            col = tex.get(tu, tv)
            if col[3] == 0:
                continue
            g = glow.get(tu, tv)
            depth[i] = z
            img[i] = g if g[3] else shade(col, lit)


def preview(path):
    tex, glow = paint()
    w, h, scale = 380, 400, 4.6
    shown = {"none", "rotary_cannon", "rotary_barrels", "power_fist", "palm", "fingers", "autocannon", "autocannon_barrel",
             "rocket_pod", "flamer"}

    def mech_show(loadout):
        def show(name):
            for prefix in ("left_", "right_"):
                if name.startswith(prefix) and name[len(prefix):] in shown:
                    rest = name[len(prefix):]
                    return rest in loadout[prefix]
            if name == "jet_flames":
                return loadout.get("jets", False)
            if name in ("cargo_pod", "smoke_launcher"):
                return name == loadout["shoulder"]
            return True
        return show

    front = {"left_": {"autocannon", "autocannon_barrel"}, "right_": {"power_fist", "palm", "fingers"}, "shoulder": "cargo_pod"}
    back = {"left_": {"rocket_pod"}, "right_": {"rotary_cannon", "rotary_barrels"}, "shoulder": "smoke_launcher", "jets": True}
    walk = {"left_leg": (0.35, 0, 0), "right_leg": (-0.35, 0, 0), "right_shin": (0.5, 0, 0), "right_foot": (-0.15, 0, 0),
            "left_foot": (-0.35, 0, 0), "hatch": (-1.9, 0, 0)}
    views = [
        render(world_boxes(MECH, mech_show(front), {}), tex, glow, math.radians(-28), math.radians(-12), w, h, scale, (w / 2, h - 30)),
        render(world_boxes(MECH, mech_show(back), walk), tex, glow, math.radians(150), math.radians(-12), w, h, scale, (w / 2, h - 30)),
        render(world_boxes(GANTRY, lambda n: True, {}) + world_boxes(MECH, mech_show(front), {}, (0, -3, 0)), tex, glow,
               math.radians(-35), math.radians(-15), w, h, 3.4, (w / 2, h - 50)),
    ]
    out = Canvas(w * 3, h)
    for k, img in enumerate(views):
        for y in range(h):
            for x in range(w):
                out.set(k * w + x, y, img[y * w + x])
    out.save(path)


# --- Icons ---------------------------------------------------------------------------------------------------------------
PALETTE = {
    ".": CLEAR, "k": rgb(0x1A1D22), "a": ARMOR, "A": shade(ARMOR, 1.3), "d": shade(ARMOR, 0.72), "f": FRAME, "F": shade(FRAME, 1.45),
    "g": GUN, "G": shade(GUN, 1.45), "b": TRIM, "B": shade(TRIM, 1.25), "n": shade(TRIM, 0.7), "y": AMBER, "t": TEAL,
    "T": rgb(0xC8FFF6), "o": FLAME_COOL, "O": FLAME_HOT, "w": rgb(0xE6ECF2), "r": rgb(0x8E3B2B), "s": rgb(0x9AA3AD),
}

ICONS = {
    "mech_torso": [
        "................",
        "...kkkkkkkkkk...",
        "..kAAAAAAAAAAk..",
        ".kAaaaaaaaaaaak.",
        ".kakkkkkkkkkkak.",
        ".kakyyyyyyyykak.",
        ".kaabbbbbbbbaak.",
        ".kaabAaaaaAbaak.",
        ".kaabAaaaaAbaak.",
        ".kaabAaaaaAbaak.",
        ".kaabAaaaaAbaak.",
        ".kdabaddddabadk.",
        ".kddbbbbbbbbddk.",
        "..kddddddddddk..",
        "...kffffffffk...",
        "....kkkkkkkk....",
    ],
    "mech_legs": [
        "................",
        "..kkkkkkkkkkkk..",
        "..kFFFFFFFFFFk..",
        "..kffffffffffk..",
        "..kkkkk..kkkkk..",
        "..kAak....kAak..",
        "..kaak....kaak..",
        ".kbbbbk..kbbbbk.",
        ".kbBBbk..kbBBbk.",
        "..kaak....kaak..",
        "..kaak....kaak..",
        "..kdak....kdak..",
        ".kkdakk..kkdakk.",
        "kFFFFFk..kFFFFFk",
        "kffffffkkffffffk",
        "kkkkkkkkkkkkkkkk",
    ],
    "mech_arm": [
        "................",
        "...kkkkkk.......",
        "..kAAAAAAk......",
        "..kaaaaaak......",
        "..kbbbbbbk......",
        "...kffffk.......",
        "...kFfffk.......",
        "...kffffk.......",
        "...kfffkkkkkkkk.",
        "...kffkGGGGGGGGk",
        "...kffkgggggggfk",
        "...kffkgggggggfk",
        "...kkkkkkkkkkkkk",
        "................",
        "................",
        "................",
    ],
    "mech_power_plant": [
        "................",
        "..kkkkkkkkkkkk..",
        ".kAAAAAAAAAAAAk.",
        ".kaaaaaaaaaaaak.",
        ".kaaakbbbbkaaak.",
        ".kaaabTTttbaaak.",
        ".kaaabTtttbaaak.",
        ".kaaabtttTbaaak.",
        ".kaaabttTtbaaak.",
        ".kaaakbbbbkaaak.",
        ".kddddddddddddk.",
        ".kkgggkkkkgggkk.",
        "..kgGgk..kgGgk..",
        "..kkkkk..kkkkk..",
        "................",
        "................",
    ],
    "mech_autocannon": [
        "................",
        "................",
        "................",
        ".....kkkk.......",
        "....kbBBbk......",
        "kkkkkkkkkkkkkk..",
        "kGGGGGGGGGkkkkkk",
        "kgggggggggkGGGGk",
        "kgggggggggkggggk",
        "kgggggggggkkkkkk",
        "kddddddddgk.....",
        "kkkkkkkkkkk.....",
        "................",
        "................",
        "................",
        "................",
    ],
    "mech_rotary_cannon": [
        "................",
        "................",
        "................",
        "kkkkkkkk........",
        "kGGGGGGkkkkkkkkk",
        "kgggggkfkGGGGGGk",
        "kgggggkfkkkkkkkk",
        "kgggggkfkGGGGGGk",
        "kgggggkfkkkkkkkk",
        "kgggggkfkGGGGGGk",
        "kgggggkfkkkkkkkk",
        "kkkkkkkk........",
        "................",
        "................",
        "................",
        "................",
    ],
    "mech_power_fist": [
        "................",
        "....kkkkkkkk....",
        "...kAAAAAAAAk...",
        "..kAaaaaaaaaak..",
        "..kakbbbbbbbkak.",
        "..kakBnBnBnBkak.",
        "..kakbnbnbnbkak.",
        "..kakbnbnbnbkak.",
        "..kakbbbbbbbkak.",
        "..kaakkkkkkkaak.",
        "..kaaaaaaaaaaak.",
        "..kddddddddddk..",
        "...kkkaaaaakkk..",
        ".....kaaaaak....",
        ".....kddddk.....",
        ".....kkkkkk.....",
    ],
    "mech_rocket_pod": [
        "................",
        "..kkkkkkkkkkkk..",
        ".kAAAAAAAAAAAAk.",
        ".kaaaaaaaaaaaak.",
        ".kakkkkaakkkkak.",
        ".kakrkkaakrkkak.",
        ".kakkkkaakkkkak.",
        ".kaaaaaaaaaaaak.",
        ".kaaaaaaaaaaaak.",
        ".kakkkkaakkkkak.",
        ".kakrkkaakrkkak.",
        ".kakkkkaakkkkak.",
        ".kddddddddddddk.",
        "..kkkkkkkkkkkk..",
        "................",
        "................",
    ],
    "mech_flamer": [
        "................",
        "................",
        "...kkkkkk.......",
        "..kbBBBBbk......",
        "..kbbbbbbk......",
        "..kkkkkkkk......",
        ".kGGGGGGGkkkkkk.",
        ".kgggggggGGGGkok",
        ".kgggggggggggkoO",
        ".kgggggggkkkkkko",
        ".kkkkkkkkk......",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "mech_cargo_pod": [
        "................",
        "................",
        ".kkkkkkkkkkkkkk.",
        ".kAAAAAAAAAAAAk.",
        ".kaaaaaaaaaaaak.",
        ".kbbbbbbbbbbbbk.",
        ".kaaaaaaaaaaaak.",
        ".kaaaaakkaaaaak.",
        ".kaaaaakkaaaaak.",
        ".kaaaaaaaaaaaak.",
        ".knnnnnnnnnnnnk.",
        ".kaaaaaaaaaaaak.",
        ".kddddddddddddk.",
        ".kkkkkkkkkkkkkk.",
        "................",
        "................",
    ],
    "mech_smoke_launcher": [
        "................",
        "................",
        ".........kk.....",
        "......kk.kgk.kk.",
        "...kk.kgkkgkkgk.",
        "...kgkkgkkgkkgk.",
        "...kgkkgkkgkkgk.",
        "..kkgkkgkkgkkgkk",
        "..kGGGGGGGGGGGGk",
        "..kggggggggggggk",
        "..kggggggggggggk",
        "..kkkkkkkkkkkkkk",
        "................",
        "................",
        "................",
        "................",
    ],
    "mech_actuator": [
        "................",
        "................",
        "......kkkk......",
        ".....kssssk.....",
        ".....kwssk......",
        "......kssk......",
        "......kssk......",
        "....kkkkkkkk....",
        "...kBBBBBBBBk...",
        "...kbbbbbbbbk...",
        "...kbbbbbbbbk...",
        "...knnnnnnnnk...",
        "...kffffffffk...",
        "...kFFFFFFFFk...",
        "....kkkkkkkk....",
        "................",
    ],
    "autocannon_drum": [
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkGGGGGGkk...",
        "..kGGggggggGGk..",
        "..kgggkkkkgggk..",
        ".kgggkbBBbkgggk.",
        ".kgggkbrrbkgggk.",
        ".kgggkbrrbkgggk.",
        ".kgggkbbbbkgggk.",
        "..kgggkkkkgggk..",
        "..kddggggggddk..",
        "...kkddddddkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "mech_gantry": [
        "kkkkkkkkkkkkkkkk",
        "kBBBBBBBBBBBBBBk",
        "kfkkkkkkkkkkkkfk",
        "kfk..........kfk",
        "kfkkkk....kkkkfk",
        "kfGGGk....kGGGfk",
        "kfkkkk....kkkkfk",
        "kfk..........kfk",
        "kfk..........kfk",
        "kfk..........kfk",
        "kfk..........kfk",
        "kfk..........kfk",
        "kkkkkkkkkkkkkkkk",
        "kbbnbbnbbnbbnbbk",
        "kffffffffffffffk",
        "kkkkkkkkkkkkkkkk",
    ],
}


def icons():
    for name, rows in ICONS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
        c = Canvas(16, 16)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c.set(x, y, PALETTE[ch])
        c.save(os.path.join(TEX, "item", name + ".png"))
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "mymod:item/" + name}})
        write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "mymod:item/" + name}})


def gantry_block():
    """The deck texture, for the block's breaking particles. The block model itself has no elements: the deck and the
    frame are both drawn by the block entity's renderer (a block model's deck would z-fight with the frame's)."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            seam = x == 8 or y == 8
            c.set(x, y, TRIM if edge else shade(FRAME, 0.8) if seam else shade(FRAME, 1.05 - 0.1 * y / 15))
    c.save(os.path.join(TEX, "block", "mech_gantry.png"))
    write_json(os.path.join(ASSETS, "models", "block", "mech_gantry.json"), {"textures": {"particle": "mymod:block/mech_gantry"}})
    write_json(os.path.join(ASSETS, "blockstates", "mech_gantry.json"), {"variants": {
        "facing=north": {"model": "mymod:block/mech_gantry"}, "facing=east": {"model": "mymod:block/mech_gantry", "y": 90},
        "facing=south": {"model": "mymod:block/mech_gantry", "y": 180}, "facing=west": {"model": "mymod:block/mech_gantry", "y": 270}}})


# --- Recipes and their recipe-book advancements ---------------------------------------------------------------------------
RECIPES = os.path.join(DATA, "recipe")
ADVANCEMENTS = os.path.join(DATA, "advancement", "recipes")


def advancement(name, category, unlock_item):
    write_json(os.path.join(ADVANCEMENTS, category, name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": unlock_item}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipes": "mymod:" + name}, "trigger": "minecraft:recipe_unlocked"}},
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": ["mymod:" + name]}})


def shaped(name, result, pattern, key, unlock, count=1, category="misc", folder="misc"):
    write_json(os.path.join(RECIPES, name + ".json"), {"type": "minecraft:crafting_shaped", "category": category,
                                                        "key": key, "pattern": pattern,
                                                        "result": {"count": count, "id": result}})
    advancement(name, folder, unlock)


def recipes():
    """Built from what the rest of the mod makes: steel and duralumin, tungsten carbide joints, rubber seals, computer
    chips, and Skullcrane Servo Arms as the muscle in every actuator."""
    # The gantry: steel blocks for the frame, Servo Arms as its hoists, a chip for the assembly controller.
    shaped("mech_gantry", "mymod:mech_gantry", ["SCS", "A A", "PPP"],
           {"S": "mymod:steel_block", "C": "mymod:computer_chip", "A": "mymod:servo_arm", "P": "mymod:steel_plate"},
           "mymod:steel_block")
    # Two actuators: a Servo Arm with a tungsten carbide joint, rubber seals and a steel housing.
    shaped("mech_actuator", "mymod:mech_actuator", [" C ", "RAR", " S "],
           {"C": "mymod:tungsten_carbide", "R": "mymod:rubber", "A": "mymod:servo_arm", "S": "mymod:steel_ingot"},
           "mymod:servo_arm", 2)
    shaped("mech_legs", "mymod:mech_legs", ["PAP", "T T", "B B"],
           {"P": "mymod:steel_plate", "A": "mymod:mech_actuator", "T": "mymod:tungsten_carbide", "B": "mymod:steel_block"},
           "mymod:mech_actuator", category="equipment", folder="combat")
    shaped("mech_arm", "mymod:mech_arm", ["BP", "AT", "BP"],
           {"P": "mymod:steel_plate", "A": "mymod:mech_actuator", "T": "mymod:tungsten_carbide", "B": "mymod:steel_block"},
           "mymod:mech_actuator", category="equipment", folder="combat")
    # A light duralumin hold on a chest, sealed with rubber.
    shaped("mech_cargo_pod", "mymod:mech_cargo_pod", ["DDD", "DCD", "RRR"],
           {"D": "mymod:duralumin_sheet", "C": "minecraft:chest", "R": "mymod:rubber"}, "mymod:mech_actuator")


if __name__ == "__main__":
    pack()
    if "--java" in sys.argv:
        print("// --- MechModel ---")
        print(java(MECH))
        print("// --- MechGantryModel ---")
        print(java(GANTRY))
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    tex, glow = paint()
    tex.save(os.path.join(TEX, "entity", "mech", "mech.png"))
    glow.save(os.path.join(TEX, "entity", "mech", "mech_glow.png"))
    icons()
    gantry_block()
    recipes()
