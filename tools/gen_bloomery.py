"""The Bloomery and bloom steel: the furnace's block models (cold and lit) and their texture atlases, its GUI background
and button sprites, the bloom and the whole bloom steel kit's art (ingot, nugget, block, tools, long sword, armor and
its worn layers), and the data: blockstate, item definitions, recipes with their recipe-book unlocks, loot tables,
tags, the equipment asset and lang. With --preview, a sheet of the furnace cold and lit at three quarters, its screen
and HUD, and the sprites beside iron's and steel's.

Run from anywhere:  python3 tools/gen_bloomery.py [--preview out.png]

The furnace is built from named boxes (pixels, facing north: the tapping arch looks north, the bellows fills the west
strip). Every face that can be seen is painted on its own patch of an atlas, one texel to a model pixel, with the
patterns (brick courses, clay coils, planks, pleats) laid out in model space so they run on across faces. Faces hidden
against another box are left out, and the coplanar check (tools/coplanar.py) refuses a model with two overlapping faces
in one plane. The glowing parts (the core seen through the shaft's slits, the fire in the arch, the slag, the charge in
the throat) come from a glow atlas: lit, they are full bright and unshaded; cold, the same patches in dead charcoal.

Bloom steel is drawn from vanilla iron's art (read from the client jar), recoloured along a darker, warm iron-black ramp
by brightness so vanilla's shading carries over, then gently mottled (a smooth, low-frequency shift of a step or so,
not per-pixel noise) and peened: hammer marks set on a staggered grid over the metal's broad faces, each a dent one
step darker with its lit rim one step lighter. Steel (the mod's) is a cool blue-grey; iron a pale grey; netherite a
pinkish charcoal: bloom steel sits apart from all three. The bloom is drawn by hand: a spongy lump of iron with pores
and glassy slag."""
import json, math, os, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
_argv = sys.argv
sys.argv = sys.argv[:1]
from gunkit import png  # noqa: E402
from coplanar import check_elements  # noqa: E402
from gen_longswords import client_jar, read_png, long_sword, displays as long_sword_displays, SIZE as LONG_SIZE  # noqa: E402
import gen_star_forge as sf  # noqa: E402
# gen_longswords reads a jar path from the command line: find the jar before our own arguments are back.
JAR = zipfile.ZipFile(client_jar())
sys.argv = _argv

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tomyen")
DATA = os.path.join(RES, "data")
TEX = os.path.join(ASSETS, "textures")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
ATLAS = sf.ATLAS  # 128: one model pixel = one texel
Img, hexc, mixc, shade, write = sf.Img, sf.hexc, sf.mixc, sf.shade, sf.write
CLEAR = (0, 0, 0, 0)


def vanilla(path):
    return read_png(JAR.read(f"assets/minecraft/textures/{path}.png"))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


# ===================================================================================================== palettes
STONE = [hexc(c) for c in ("4a4a4c", "5a5a5c", "6a6a6b", "7b7b7b", "8d8d8c")]
BRICK = [hexc(c) for c in ("6a3024", "7f3b2b", "934634", "a6553f", "b86a50")]
MORTAR = [hexc(c) for c in ("6e655e", "8a8078", "a09790")]
CLAY = [hexc(c) for c in ("5e3c2a", "774c34", "8f5d3f", "a6704c", "bb845d", "cc9a70")]
SOOT = [hexc(c) for c in ("1c1714", "2b2320", "3b302a", "4c3d33")]
IRON = [hexc(c) for c in ("232428", "35373c", "4a4d53", "62666d", "7e838a", "a2a7ad")]
WOOD = [hexc(c) for c in ("352a20", "463729", "584634", "6b5640", "7f674d", "93795a")]
LEATHER = [hexc(c) for c in ("4e2412", "6a321a", "86432a", "a15734", "b96d43", "cf8756")]
FIRE = [hexc(c) for c in ("7a1c08", "b8320c", "e65a14", "ff8a1f", "ffc24a", "ffe9a0", "fff8e0")]
EMBER = [hexc(c) for c in ("120d0b", "1d1512", "2a1e19", "3a2a22", "4e2a1a", "6a2c14")]


# ===================================================================================================== painters
# Each paints one face patch: (w, h) texels for face of box b. ox, oy: the model-space coordinates of the patch's
# top-left texel along the face (u running as the game maps it, v down from the face's top edge), so patterns line
# up across faces. Side faces are painted upright; up faces with row 0 to the north.

def p_stone(w, h, face, b, ox, oy):
    img = Img(w, h, STONE[2])
    if face == "up":
        # Flagstones: three rows of slabs, each with a lit north-west edge and a shadowed south-east one.
        for y in range(h):
            for x in range(w):
                ax, az = ox + x, oy + y
                row = int(az // 6)
                shift = (0, 3, 1)[row % 3]
                col_edge = (ax + shift) % 7
                row_edge = az % 6
                tone = STONE[2 if (row + int((ax + shift) // 7)) % 2 else 3]
                if col_edge == 0 or row_edge == 0:
                    tone = STONE[4]
                elif col_edge == 6 or row_edge == 5:
                    tone = STONE[1]
                img.set(x, y, tone)
        return img
    if face == "down":
        return Img(w, h, STONE[0])
    for x in range(w):
        ax = ox + x
        for y in range(h):
            img.set(x, y, STONE[3] if y == 0 else STONE[1] if y == h - 1 else STONE[2])
        if int(ax) % 6 == 5:
            for y in range(h):
                img.set(x, y, STONE[0] if y else STONE[1])
    return img


def p_brick(w, h, face, b, ox, oy):
    """Fired brick in courses three texels high (two of brick, one of mortar), bricks six long and staggered. The
    bricks take one of three set tones by their place in the wall, so the wall reads as laid, not speckled."""
    img = Img(w, h, BRICK[2])
    if face in ("up", "down"):
        # The hearth's top ledge and the arch's soffit: a brick header course.
        for y in range(h):
            for x in range(w):
                img.set(x, y, BRICK[3] if face == "up" else BRICK[1])
        if face == "up":
            for x in range(w):
                if int(ox + x) % 3 == 2:
                    for y in range(h):
                        img.set(x, y, MORTAR[1])
        return img
    for y in range(h):
        ay = 16 - (oy + y) - 1  # model y of this row, counted from the bottom
        course = int((ay - 2) // 3)
        in_course = (ay - 2) % 3
        for x in range(w):
            ax = ox + x
            shift = 3 if course % 2 else 0
            pos = (ax + shift) % 6
            brick = int((ax + shift) // 6)
            if in_course == 2 or pos == 5:
                c = MORTAR[1]
            else:
                c = BRICK[(1, 2, 3)[(brick * 2 + course) % 3]]
                if in_course == 1 and pos < 5:
                    c = shade(c, 1.08)  # each brick's upper edge catches the light
            img.set(x, y, c)
    return img


def p_clay(w, h, face, b, ox, oy):
    """Clay daub, built up in coils: each coil two texels high, its rounded top a tone lighter. The shaft darkens a
    little toward its foot where the fire is, and soot creeps over the throat's top."""
    if face == "up":
        img = Img(w, h, SOOT[2] if b["name"].startswith("ring") else CLAY[3])
        if b["name"].startswith("ring"):
            for x in range(w):
                img.set(x, 0, SOOT[3])
        else:
            for x in range(w):
                img.set(x, 0, CLAY[4])
        return img
    if face == "down":
        return Img(w, h, CLAY[1])
    img = Img(w, h, CLAY[3])
    for y in range(h):
        ay = 16 - (oy + y) - 1
        coil_top = int(ay) % 2 == 1
        base = 3 if ay >= 10 else 2
        if ay >= 15:
            # The lip: sooted.
            c = SOOT[3]
        else:
            c = CLAY[base + 1] if coil_top else CLAY[base]
        for x in range(w):
            img.set(x, y, c)
    return img


def p_scorch(w, h, face, b, ox, oy):
    """Inside a slit and the throat: clay fired dark, warmer where the heat comes through."""
    img = Img(w, h, SOOT[2])
    for y in range(h):
        for x in range(w):
            img.set(x, y, mixc(SOOT[3], hexc("6a2c14"), 0.35) if y >= h - 1 else SOOT[2] if y % 2 else SOOT[3])
    return img


def p_iron(w, h, face, b, ox, oy):
    """Wrought iron straps and the tuyere: a lit upper edge, a dark lower one, and rivets along the bands."""
    img = Img(w, h, IRON[2])
    if face in ("up", "down"):
        return Img(w, h, IRON[3] if face == "up" else IRON[1])
    for x in range(w):
        img.set(x, 0, IRON[4])
        if h > 1:
            img.set(x, h - 1, IRON[1])
    if b["name"].startswith("band") and h >= 1:
        for x in range(w):
            if int(ox + x) % 4 == 1:
                img.set(x, 0, IRON[5])
    return img


def p_wood(w, h, face, b, ox, oy):
    """The bellows' boards: vertical planks with a grain line each, and an iron corner bracket on the faces."""
    img = Img(w, h, WOOD[3])
    if face in ("up", "down"):
        img = Img(w, h, WOOD[4] if face == "up" else WOOD[1])
        return img
    for y in range(h):
        for x in range(w):
            ax = int(ox + x)
            plank = ax // 2
            c = WOOD[3] if plank % 2 else WOOD[2]
            if ax % 2 == 1 and (y + plank) % 3 == 1:
                c = WOOD[1]  # grain
            img.set(x, y, c)
    for x in range(w):
        img.set(x, 0, WOOD[4])
        img.set(x, h - 1, WOOD[1])
    if b["name"] in ("plate_front", "plate_back") and face in ("north", "south") and w >= 4 and h >= 4:
        # Iron corner brackets.
        for (cx, cy) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
            img.set(cx, cy, IRON[2])
        img.set(1, 0, IRON[3])
        img.set(w - 2, 0, IRON[3])
        img.set(1, h - 1, IRON[1])
        img.set(w - 2, h - 1, IRON[1])
    if b["name"].startswith("handle") or b["name"].startswith("rod"):
        img = Img(w, h, WOOD[3])
        for x in range(w):
            img.set(x, 0, WOOD[5])
            if h > 1:
                img.set(x, h - 1, WOOD[1])
    return img


def p_leather(w, h, face, b, ox, oy):
    """The bellows' bag: ridges lit on top, the folds between them in shadow."""
    if b["name"].startswith("ridge"):
        img = Img(w, h, LEATHER[3])
        if face == "up":
            return Img(w, h, LEATHER[5])
        for x in range(w):
            img.set(x, 0, LEATHER[5])
            if h > 1:
                img.set(x, 1, LEATHER[4])
            img.set(x, h - 1, LEATHER[1])
        return img
    img = Img(w, h, LEATHER[2])
    if face == "up":
        img = Img(w, h, LEATHER[3])
        return img
    for x in range(w):
        img.set(x, 0, LEATHER[3])
        img.set(x, h - 1, LEATHER[0])
    return img


# The glowing parts, lit and cold.
def g_core(w, h, face, b, ox, oy, lit):
    """The burning charge seen through a slit: brightest at the middle of the shaft's height."""
    img = Img(w, h)
    for y in range(h):
        t = abs((y + 0.5) / h - 0.55) * 2
        if lit:
            c = FIRE[5] if t < 0.3 else FIRE[4] if t < 0.65 else FIRE[3]
        else:
            c = EMBER[4] if t < 0.3 else EMBER[2]
        for x in range(w):
            img.set(x, y, c)
    return img


def g_fire(w, h, face, b, ox, oy, lit):
    """The fire at the back of the arch: a heap of charcoal lumps, white-hot between them."""
    img = Img(w, h, FIRE[5] if lit else EMBER[1])
    lumps = [(0, 3, 2, 1), (2, 2, 2, 2), (0, 1, 1, 1), (3, 0, 1, 1), (1, 0, 1, 1)]
    for lx, ly, lw, lh in lumps:
        for y in range(ly, ly + lh):
            for x in range(lx, lx + lw):
                if x < w and y < h:
                    img.set(x, y, FIRE[3] if lit else EMBER[0])
    if lit:
        for x, y in ((1, 2), (3, 1)):
            if x < w and y < h:
                img.set(x, y, FIRE[6])
    else:
        for x, y in ((1, 2), (3, 2)):
            if x < w and y < h:
                img.set(x, y, EMBER[5])
    return img


def g_slag(w, h, face, b, ox, oy, lit):
    """Slag run out under the arch: molten in the middle, crusting at its edge."""
    img = Img(w, h, FIRE[3] if lit else EMBER[2])
    for y in range(h):
        for x in range(w):
            edge = x == 0 or x == w - 1 or (face == "up" and y == 0 and b["name"] == "slag_lip")
            if edge:
                img.set(x, y, FIRE[1] if lit else EMBER[1])
            elif face == "up" and (x + int(oy + y)) % 3 == 0:
                img.set(x, y, FIRE[4] if lit else EMBER[3])
    return img


def g_charge(w, h, face, b, ox, oy, lit):
    """The charge in the throat seen from above: charcoal lumps on a staggered grid, glowing in the gaps."""
    img = Img(w, h, FIRE[4] if lit else EMBER[1])
    for y in range(h):
        for x in range(w):
            ax, az = int(ox + x), int(oy + y)
            shift = 1 if (az // 2) % 2 else 0
            if (ax + shift) % 2 == 0 and az % 2 == 0:
                img.set(x, y, FIRE[2] if lit else EMBER[0])
            elif (ax + shift) % 2 == 1 and az % 2 == 1:
                img.set(x, y, FIRE[5] if lit else EMBER[3])
    return img


PAINTERS = {"stone": p_stone, "brick": p_brick, "clay": p_clay, "scorch": p_scorch, "iron": p_iron, "wood": p_wood,
            "leather": p_leather}
GLOW_PAINTERS = {"core": g_core, "fire": g_fire, "slag": g_slag, "charge": g_charge}
GLOW = set(GLOW_PAINTERS)


# ===================================================================================================== geometry
ALL = ("north", "east", "south", "west", "up", "down")


def box(name, frm, to, mat, faces=ALL, side_mat=None):
    """side_mat: a material for the faces that look into a slit or the throat (the box's inner faces)."""
    return {"name": name, "from": list(frm), "to": list(to), "mat": mat, "faces": list(faces), "side_mat": side_mat or {}}


def without(*drop):
    return tuple(f for f in ALL if f not in drop)


def boxes():
    out = []
    # The stone footing the whole thing stands on.
    out.append(box("footing", (0, 0, 0), (16, 2, 16), "stone"))

    # The hearth: brick, x 5..15, z 2..12, y 2..8, with the tapping arch (x 8..12, y 2..6) open at the front onto the
    # fire. The hearth's back wall behind the fire only shows from the south.
    out.append(box("hearth_w", (5, 2, 2), (8, 8, 12), "brick", ("north", "east", "south", "west", "up")))
    out.append(box("hearth_e", (12, 2, 2), (15, 8, 12), "brick", ("north", "east", "south", "west", "up")))
    out.append(box("hearth_top", (8, 6, 2), (12, 8, 12), "brick", ("north", "south", "up", "down")))
    out.append(box("hearth_back", (8, 2, 5), (12, 6, 12), "brick", ("south",)))
    out.append(box("fire", (8, 2, 4), (12, 6, 5), "fire", ("north",)))
    # The arch's brick surround, standing proud of the face, and slag run out under it.
    out.append(box("arch_l", (7, 2, 1.5), (8, 6, 2), "brick", ("north", "west", "east")))
    out.append(box("arch_r", (12, 2, 1.5), (13, 6, 2), "brick", ("north", "west", "east")))
    out.append(box("arch_top", (7, 6, 1.5), (13, 7, 2), "brick", ("north", "west", "east", "up", "down")))
    out.append(box("slag", (8, 2, 2.5), (12, 2.5, 4), "slag", ("north", "up")))
    out.append(box("slag_lip", (9, 2, 1), (11, 2.25, 2.5), "slag", ("north", "east", "west", "up")))
    # An iron band round the hearth's top course.
    out.append(box("band_hn", (4.75, 7, 1.75), (15.25, 8, 2), "iron", ("north", "east", "west", "up", "down")))
    out.append(box("band_hs", (4.75, 7, 12), (15.25, 8, 12.25), "iron", ("south", "east", "west", "up", "down")))
    out.append(box("band_hw", (4.75, 7, 2), (5, 8, 12), "iron", ("west", "up", "down")))
    out.append(box("band_he", (15, 7, 2), (15.25, 8, 12), "iron", ("east", "up", "down")))

    # The shaft: clay walls a texel thick round the burning core, x 6..14, z 3..11, y 8..12. A course of clay runs
    # unbroken along the foot and the head of each wall; between them two short slits a side, through which the core
    # glows. The slits' sides, floors and heads are fired dark.
    lo, hi = (8, 9), (11, 12)
    for side, z0, z1, outer in (("n", 3, 4, "north"), ("s", 10, 11, "south")):
        out.append(box(f"course_{side}_lo", (6, lo[0], z0), (14, lo[1], z1), "clay", (outer, "east", "west", "up"), {"up": "scorch"}))
        out.append(box(f"course_{side}_hi", (6, hi[0], z0), (14, hi[1], z1), "clay", (outer, "east", "west", "up", "down"),
                       {"down": "scorch"}))
        for i, (a, c) in enumerate(((6, 8), (9, 11), (12, 14))):
            slit = ("east",) if i == 0 else ("west",) if i == 2 else ("east", "west")
            corner = ("west",) if i == 0 else ("east",) if i == 2 else ()
            out.append(box(f"panel_{side}{i}", (a, lo[1], z0), (c, hi[0], z1), "clay", (outer,) + slit + corner,
                           {e: "scorch" for e in slit}))
    for side, x0, x1, outer in (("w", 6, 7, "west"), ("e", 13, 14, "east")):
        out.append(box(f"course_{side}_lo", (x0, lo[0], 4), (x1, lo[1], 10), "clay", (outer, "up"), {"up": "scorch"}))
        out.append(box(f"course_{side}_hi", (x0, hi[0], 4), (x1, hi[1], 10), "clay", (outer, "up", "down"), {"down": "scorch"}))
        for i, (a, c) in enumerate(((4, 5), (6, 8), (9, 10))):
            slit = ("south",) if i == 0 else ("north",) if i == 2 else ("north", "south")
            out.append(box(f"panel_{side}{i}", (x0, lo[1], a), (x1, hi[0], c), "clay", (outer,) + slit, {e: "scorch" for e in slit}))
    out.append(box("core", (7, lo[0], 4), (13, hi[1], 10), "core", ("north", "east", "south", "west")))

    # The throat: solid clay, an iron band round it, and the sooted mouth with the charge glowing in it.
    out.append(box("throat", (7, 12, 4), (13, 14, 10), "clay", ("north", "east", "south", "west")))
    out.append(box("band_tn", (6.75, 12.5, 3.75), (13.25, 13.5, 4), "iron", ("north", "east", "west", "up", "down")))
    out.append(box("band_ts", (6.75, 12.5, 10), (13.25, 13.5, 10.25), "iron", ("south", "east", "west", "up", "down")))
    out.append(box("band_tw", (6.75, 12.5, 4), (7, 13.5, 10), "iron", ("west", "up", "down")))
    out.append(box("band_te", (13, 12.5, 4), (13.25, 13.5, 10), "iron", ("east", "up", "down")))
    out.append(box("ring_n", (7, 14, 4), (13, 16, 5), "clay", ("north", "south", "up", "east", "west"), {"south": "scorch"}))
    out.append(box("ring_s", (7, 14, 9), (13, 16, 10), "clay", ("south", "north", "up", "east", "west"), {"north": "scorch"}))
    out.append(box("ring_w", (7, 14, 5), (8, 16, 9), "clay", ("west", "east", "up"), {"east": "scorch"}))
    out.append(box("ring_e", (12, 14, 5), (13, 16, 9), "clay", ("east", "west", "up"), {"west": "scorch"}))
    out.append(box("charge", (8, 14, 5), (12, 14.5, 9), "charge", ("up",)))

    # The tuyere: an iron pipe from the bellows into the hearth's west side, through a clay collar.
    out.append(box("tuyere", (2, 3.75, 3.25), (5, 4.75, 4.25), "iron", ("north", "south", "west", "up", "down")))
    out.append(box("collar", (4, 3, 2.5), (5, 5.5, 5), "clay", ("north", "south", "west", "up", "down")))
    out.append(box("nozzle", (2, 3.75, 4.25), (3, 4.75, 5), "iron", ("east", "west", "up", "down")))

    # The bellows: a concertina of leather between two boards, the front board's nozzle feeding the tuyere, a
    # handle out of the back board.
    out.append(box("plate_front", (0.5, 2, 5), (4.5, 7, 5.75), "wood", ("north", "east", "south", "west", "up")))
    out.append(box("plate_back", (0.5, 2, 12.25), (4.5, 7, 13), "wood", ("north", "east", "south", "west", "up")))
    out.append(box("bag", (1, 2, 5.75), (4, 6.5, 12.25), "leather", ("east", "west", "up")))
    for i, z in enumerate((7, 8.75, 10.5)):
        out.append(box(f"ridge{i}", (0.75, 2, z), (4.25, 6.75, z + 0.5), "leather", ("north", "east", "south", "west", "up")))
    out.append(box("rod_l", (1, 4, 13), (1.5, 4.5, 15), "wood", ("east", "west", "up", "down")))
    out.append(box("rod_r", (3.5, 4, 13), (4, 4.5, 15), "wood", ("east", "west", "up", "down")))
    out.append(box("handle", (0.5, 3.75, 15), (4.5, 4.75, 15.75), "wood", ALL))
    return out


def face_size(b, face):
    f, t = b["from"], b["to"]
    dx, dy, dz = t[0] - f[0], t[1] - f[1], t[2] - f[2]
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def face_origin(b, face):
    """Model coordinates of a face's top-left corner along (u, v), as the game lays the texture on it."""
    f, t = b["from"], b["to"]
    top = 16 - t[1]
    return {"north": (16 - t[0], top), "south": (f[0], top), "east": (16 - t[2], top), "west": (f[2], top),
            "up": (f[0], f[2]), "down": (f[0], 16 - t[2])}[face]


class Packer:
    """Shelf packing of face patches into an ATLAS-square texture, a texel of gap between patches."""

    def __init__(self):
        self.x = self.y = self.row = 0

    def place(self, w, h):
        if self.x + w > ATLAS:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        if self.y + h > ATLAS:
            raise SystemExit("bloomery atlas full")
        at = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        return at


def build():
    base, glow_lit, glow_dark = Img(ATLAS, ATLAS), Img(ATLAS, ATLAS), Img(ATLAS, ATLAS)
    base_pack, glow_pack = Packer(), Packer()
    elements = []
    k = ATLAS / 16.0
    for b in boxes():
        faces = OrderedDict()
        glow = b["mat"] in GLOW
        for face in b["faces"]:
            fw, fh = face_size(b, face)
            w, h = max(1, int(math.ceil(fw - 1e-6))), max(1, int(math.ceil(fh - 1e-6)))
            ox, oy = face_origin(b, face)
            mat = b["side_mat"].get(face, b["mat"])
            if glow:
                x, y = glow_pack.place(w, h)
                glow_lit.paste(GLOW_PAINTERS[mat](w, h, face, b, ox, oy, True), x, y)
                glow_dark.paste(GLOW_PAINTERS[mat](w, h, face, b, ox, oy, False), x, y)
            else:
                x, y = base_pack.place(w, h)
                base.paste(PAINTERS[mat](w, h, face, b, ox, oy), x, y)
            faces[face] = OrderedDict([("uv", [round(x / k, 4), round(y / k, 4), round((x + fw) / k, 4), round((y + fh) / k, 4)]),
                                       ("texture", "#glow" if glow else "#base")])
        e = OrderedDict([("name", b["name"]), ("from", b["from"]), ("to", b["to"]), ("faces", faces)])
        e["_glow"] = glow
        elements.append(e)
    return elements, base, glow_lit, glow_dark


def model(elements, lit):
    out = []
    for e in elements:
        e2 = OrderedDict((k, v) for k, v in e.items() if not k.startswith("_"))
        if e["_glow"] and lit:
            e2["light_emission"] = 15
            e2["shade"] = False
        out.append(e2)
    return OrderedDict([
        ("parent", "minecraft:block/block"),
        ("textures", OrderedDict([("particle", "minecraft:block/bricks"), ("base", "tomyen:block/bloomery"),
                                  ("glow", "tomyen:block/bloomery_glow" if lit else "tomyen:block/bloomery_glow_cold")])),
        ("elements", out),
    ])


# ===================================================================================================== GUI
K, W, BG, SHADOW, SLOT, SLOT_DARK = hexc("000000"), hexc("ffffff"), hexc("c6c6c6"), hexc("555555"), hexc("8b8b8b"), hexc("373737")
GUI_W, GUI_H = 176, 166
# Matches BloomeryMenu / BloomeryScreen.
ORE, FUEL, BLOOM_XY, INGOT = (38, 17), (38, 53), (92, 35), (144, 35)
THERMO = (15, 17, 21, 64)
FLAME_XY = (39, 36)
ARROW_XY = (62, 35)
HAMMER_XY, HAMMER_SIZE = (118, 35), 16
PIPS_XY = (119, 30)
BELLOWS_XY, BELLOWS_W, BELLOWS_H = (60, 55), 26, 16
GAUGE = (92, 60, 166, 66)
SPRITE_V, BELLOWS_U, HAMMER_U = 166, 0, 78
ARROW = [(15, 15), (15, 16), (15, 17), (15, 18), (15, 19), (15, 20), (1, 21), (1, 22), (1, 21), (15, 20), (15, 19),
         (15, 18), (15, 17), (15, 16), (15, 15)]
FLAME = ["......#.......", ".....##.......", ".....###......", "....####......", "...#####.#....", "...#######....",
         "..#########...", "..##########..", ".###########..", ".############.", ".############.", "..##########..",
         "...########...", "....######...."]


def gui():
    g = Img(256, 256)
    g.fill(1, 1, GUI_W - 1, GUI_H - 1, BG)
    for x in range(2, GUI_W - 2):
        g.set(x, 0, K)
        g.set(x, GUI_H - 1, K)
    for y in range(2, GUI_H - 2):
        g.set(0, y, K)
        g.set(GUI_W - 1, y, K)
    for x, y in ((1, 1), (GUI_W - 2, 1), (1, GUI_H - 2), (GUI_W - 2, GUI_H - 2)):
        g.set(x, y, K)
    for i in (1, 2):
        for x in range(2, GUI_W - 3):
            g.set(x, i, W)
        for y in range(2, GUI_H - 3):
            g.set(i, y, W)
        for x in range(3, GUI_W - 2):
            g.set(x, GUI_H - 1 - i, SHADOW)
        for y in range(3, GUI_H - 2):
            g.set(GUI_W - 1 - i, y, SHADOW)
    g.set(3, 3, W)
    g.set(GUI_W - 4, GUI_H - 4, SHADOW)

    def recess(x0, y0, x1, y1, fill=None):
        if fill:
            g.fill(x0, y0, x1, y1, fill)
        for x in range(x0 - 1, x1):
            g.set(x, y0 - 1, SLOT_DARK)
        for y in range(y0 - 1, y1):
            g.set(x0 - 1, y, SLOT_DARK)
        for x in range(x0, x1 + 1):
            g.set(x, y1, W)
        for y in range(y0, y1 + 1):
            g.set(x1, y, W)

    def slot(x, y, size=18):
        pad = (size - 16) // 2
        recess(x - pad + 1, y - pad + 1, x - pad + size - 1, y - pad + size - 1, SLOT)

    for xy in (ORE, FUEL, BLOOM_XY):
        slot(*xy)
    slot(*INGOT, size=26)
    for row in range(3):
        for col in range(9):
            slot(8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(8 + col * 18, 142)
    # The flame's and the arrow's grooves, as vanilla's furnace has them.
    for dy, line in enumerate(FLAME):
        for dx, ch in enumerate(line):
            if ch == "#":
                g.set(FLAME_XY[0] + dx, FLAME_XY[1] + dy, SLOT)
    for dy, (a, b) in enumerate(ARROW):
        for x in range(a, b + 1):
            g.set(ARROW_XY[0] + x, ARROW_XY[1] + dy, SLOT)
    # The thermometer's glass, recessed, with a bulb at its foot and a scale of ticks every 250°C.
    x0, y0, x1, y1 = THERMO
    recess(x0, y0, x1, y1, hexc("1c1714"))
    recess(x0 - 1, y1 + 1, x1 + 1, y1 + 4, hexc("6e1a0e"))
    for t in range(250, 1501, 250):
        y = y1 - round(t / 1500 * (y1 - y0))
        g.set(x1 + 2, y, SHADOW)
        if t % 500 == 0:
            g.set(x1 + 3, y, SHADOW)
    # A small arrow from the hammer to the ingot.
    for dx, dy in ((0, 0), (1, 0), (2, 0), (3, 0), (2, -1), (2, 1), (1, -2), (1, 2)):
        g.set(HAMMER_XY[0] + HAMMER_SIZE + 2 + dx, HAMMER_XY[1] + 8 + dy, SLOT)
    # The bellows gauge's slot along the bottom.
    gx0, gy0, gx1, gy1 = GAUGE
    recess(gx0, gy0, gx1, gy1)

    # Button sprites under the panel: the bellows (normal, hover, pressed) and the hammer (normal, hover, disabled).
    for state in range(3):
        bellows_button(g, BELLOWS_U + state * BELLOWS_W, SPRITE_V, state)
        hammer_button(g, HAMMER_U + state * HAMMER_SIZE, SPRITE_V, state)
    return g


def raised(g, x0, y0, w, h, state):
    """A vanilla-style button: raised (state 0), lit (1, hovered), sunk (2, pressed) or flat (3, disabled)."""
    face = {0: hexc("a8a196"), 1: hexc("c2b9a8"), 2: hexc("8a8378"), 3: hexc("9a9a9a")}[state]
    g.fill(x0, y0, x0 + w, y0 + h, face)
    light, dark = (W, SHADOW) if state in (0, 1) else (SHADOW, W) if state == 2 else (BG, BG)
    for x in range(x0, x0 + w):
        g.set(x, y0, light)
        g.set(x, y0 + h - 1, dark)
    for y in range(y0, y0 + h):
        g.set(x0, y, light)
        g.set(x0 + w - 1, y, dark)
    g.set(x0 + w - 1, y0, face)
    g.set(x0, y0 + h - 1, face)


BELLOWS_ICON = [
    "......................",
    "..WW..............WW..",
    "..WWLLLLLLLLLLLLLLWW..",
    "..WWLlLlLlLlLlLlLLWW..",
    "IIWWLLLLLLLLLLLLLLWWHH",
    "IIWWLlLlLlLlLlLlLLWWHH",
    "..WWLLLLLLLLLLLLLLWW..",
    "..WW..............WW..",
    "......................",
]


def bellows_button(g, x0, y0, state):
    raised(g, x0, y0, BELLOWS_W, BELLOWS_H, state)
    pal = {"W": WOOD[2], "L": LEATHER[3], "l": LEATHER[1], "I": IRON[3], "H": WOOD[4]}
    # Pressed, the bag is squeezed: drawn one texel shorter on each side and pushed down a texel.
    squeeze = 1 if state == 2 else 0
    ox, oy = x0 + 2, y0 + 3 + squeeze
    for dy, line in enumerate(BELLOWS_ICON):
        for dx, ch in enumerate(line):
            if ch == "." or (squeeze and ch in "Ll" and (dy in (2, 6))):
                continue
            g.set(ox + dx, oy + dy, pal[ch])


HAMMER_ICON = [
    "..............",
    "...HHHHHH.....",
    "..HHhhhhHH....",
    "..HHHHHHHH....",
    "....HHHH......",
    ".....WW.......",
    ".....WW.......",
    ".....WW.......",
    ".....WW.......",
    ".....wW.......",
    ".....ww.......",
    "..............",
]


def hammer_button(g, x0, y0, state):
    raised(g, x0, y0, HAMMER_SIZE, HAMMER_SIZE, 3 if state == 2 else state)
    if state == 2:
        pal = {"H": hexc("7a7a7a"), "h": hexc("8e8e8e"), "W": hexc("858585"), "w": hexc("7a7a7a")}
    else:
        pal = {"H": IRON[2], "h": IRON[4], "W": WOOD[3], "w": WOOD[2]}
    for dy, line in enumerate(HAMMER_ICON):
        for dx, ch in enumerate(line):
            if ch != ".":
                g.set(x0 + 1 + dx, y0 + 2 + dy, pal[ch])


# ===================================================================================================== bloom steel art
# Iron's greys by brightness onto bloom steel's ramp: a warm iron-black, lifting to a dull pewter at the highlights.
RAMP = [(0.0, hexc("14110f")), (0.18, hexc("2a2521")), (0.38, hexc("3e3731")), (0.58, hexc("564d45")),
        (0.76, hexc("71675c")), (0.9, hexc("8e8374")), (1.0, hexc("b0a594"))]


def ramp(t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        if t <= t1:
            return mixc(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0.0)
    return RAMP[-1][1]


def is_handle(c):
    r, g, b = c[:3]
    return max(r, g, b) > 0 and (max(r, g, b) - min(r, g, b)) / max(r, g, b) > 0.25 and r - b > 20 and g >= b


def mottle(x, y):
    """A smooth, low shift in brightness: broad patches a few texels across, never a per-texel speckle."""
    return 0.045 * (math.sin(x * 0.9 + y * 0.35) + math.cos(y * 0.8 - x * 0.3))


def dimple(w, px, metal, x, y):
    """One hammer mark: its upper-left texel a step darker (turned from the light), its lower-right a step lighter
    (catching it)."""
    px[y * w + x] = shade(px[y * w + x], 0.72)
    px[(y + 1) * w + x + 1] = shade(px[(y + 1) * w + x + 1], 1.22)


def peen(w, h, px, metal, step=5, phase=0, marks=()):
    """Hammer marks on a staggered grid over the metal's broad faces, plus any placed by hand (marks). A grid mark goes
    only where its two texels and the four round them all sit inside the metal, off the outline."""
    def inside(x, y):
        return 0 <= x < w and 0 <= y < h and metal[y * w + x]
    for y in range(h - 1):
        if (y + phase) % 3:
            continue
        row = (y + phase) // 3
        for x in range(w - 1):
            if (x + row * 2 + phase) % step:
                continue
            around = ((0, 0), (1, 1), (1, 0), (0, 1), (-1, 0), (0, -1), (2, 1), (1, 2))
            if all(inside(x + dx, y + dy) for dx, dy in around):
                dimple(w, px, metal, x, y)
    for x, y in marks:
        if inside(x, y) and inside(x + 1, y + 1):
            dimple(w, px, metal, x, y)


def recolour(src, dst, handles=True, step=6, phase=0, peened=True, darken=1.0, marks=()):
    """A vanilla texture onto bloom steel's ramp (keeping tool handles), mottled and peened. Returns the pixels."""
    w, h, px = vanilla(src)
    metal_l = [lum(c) for c in px if c[3] and not (handles and is_handle(c))]
    lo, hi = min(metal_l), max(metal_l)
    out, metal = [], []
    for i, c in enumerate(px):
        x, y = i % w, i // w
        if c[3] == 0:
            out.append(CLEAR)
            metal.append(False)
        elif handles and is_handle(c):
            out.append(c)
            metal.append(False)
        else:
            t = (lum(c) - lo) / (hi - lo) if hi > lo else 0.5
            outline = t < 0.12
            if not outline:
                t += mottle(x, y)
            out.append(ramp(t * darken)[:3] + (c[3],))
            metal.append(not outline)
    if peened:
        peen(w, h, out, metal, step, phase, marks)
    png(os.path.join(TEX, dst + ".png"), w, h, out)
    return w, h, out


BLOOM_ROWS = [
    "................",
    "................",
    "......oooo......",
    "....oohhllooo...",
    "...ohwhlllmmdo..",
    "..ohwhlpmlmmmdo.",
    "..ohhlmmlpmlmdo.",
    ".ohlplmlmmsmpdo.",
    ".olmmlmsmlmmmddo",
    ".omlpmmmlmpmddo.",
    ".odmmlpmmmmddpdo",
    "..odmmmdmsdpddo.",
    "..oodddpdddddo..",
    "....ooodddooo...",
    ".......ooo......",
    "................",
]
BLOOM_PAL = {"o": hexc("1a1613"), "d": hexc("332b26"), "m": hexc("4a4038"), "l": hexc("62574c"), "h": hexc("807264"),
             "w": hexc("a29482"), "p": hexc("0f0c0a"), "s": hexc("8a4424")}


def bloom_sprite():
    """A spongy lump of iron, its pores dark pits each with a lit lower rim, glassy slag caught in it."""
    w = h = 16
    px = [CLEAR] * (w * h)
    for y, row in enumerate(BLOOM_ROWS):
        for x, ch in enumerate(row):
            if ch != ".":
                px[y * w + x] = BLOOM_PAL[ch]
    for y, row in enumerate(BLOOM_ROWS):
        for x, ch in enumerate(row):
            if ch == "p" and y + 1 < h and BLOOM_ROWS[y + 1][x] in "mdl":
                px[(y + 1) * w + x] = BLOOM_PAL["h"]
            if ch == "s" and x + 1 < w and BLOOM_ROWS[y][x + 1] in "mdl":
                px[y * w + x + 1] = hexc("c06a34")
    png(os.path.join(TEX, "item", "bloom.png"), w, h, px)
    return px


def forged_block():
    """A forged slab: a bevelled edge like iron's block, and its face hammered all over in rows of dimples, each row
    set half a dimple along from the last, so the hammering reads as worked, not speckled."""
    w = h = 16
    px = [ramp(0.6 + mottle(x * 0.7, y * 0.7) * 0.8) for y in range(h) for x in range(w)]
    for y in range(1, h - 1):
        cell_row = (y - 1) // 4
        for x in range(1, w - 1):
            cx = (x - 1 + (2 if cell_row % 2 else 0)) % 4
            cy = (y - 1) % 4
            # The dimple's three by three: shadowed up and left, lit down and right; the fourth row and column flat.
            if cx < 3 and cy < 3:
                t = (0.44, 0.52, 0.6, 0.7, 0.78)[cx + cy]
                px[y * w + x] = ramp(t + mottle(x * 0.7, y * 0.7) * 0.5)
    for i in range(w):
        px[i] = ramp(0.84)
        px[(h - 1) * w + i] = ramp(0.22)
    for j in range(h):
        px[j * w] = ramp(0.8)
        px[j * w + w - 1] = ramp(0.26)
    png(os.path.join(TEX, "block", "bloom_steel_block.png"), w, h, px)
    return (w, h, px)


TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
ARMOR = ["helmet", "chestplate", "leggings", "boots"]
# Per item: where its peening grid starts (so each lands its marks on the broad faces) and how dense it is.
PEEN = {"ingot": (5, 0), "nugget": (3, 1), "sword": (5, 0), "pickaxe": (5, 1), "axe": (4, 0), "shovel": (4, 1), "hoe": (5, 0),
        "helmet": (4, 1), "chestplate": (5, 0), "leggings": (4, 1), "boots": (4, 0)}


# Hammer marks placed by hand where the grid misses a broad face: (x, y) of each dimple's dark texel.
MARKS = {"ingot": [(6, 5), (10, 5), (4, 7), (8, 7), (9, 9)], "nugget": [(6, 7)], "sword": [(9, 5)], "axe": [(8, 3)],
         "helmet": [(5, 5), (9, 5)], "leggings": [(5, 3), (9, 3)], "boots": [(4, 6), (10, 7)]}


def bloom_steel_art():
    sprites = {}
    for part in ["ingot", "nugget"] + TOOLS + ARMOR:
        step, phase = PEEN[part]
        sprites[part] = recolour(f"item/iron_{part}", f"item/bloom_steel_{part}", handles=part in TOOLS, step=step, phase=phase,
                                 marks=MARKS.get(part, ()))
    sprites["block"] = forged_block()
    for layer in ("humanoid", "humanoid_leggings"):
        recolour(f"entity/equipment/{layer}/iron", f"entity/equipment/{layer}/bloom_steel", handles=False, step=5, darken=0.95)
    # The long sword, from the sword, as tools/gen_longswords.py makes the others.
    w, h, px = sprites["sword"]
    png(os.path.join(TEX, "item", "bloom_steel_long_sword.png"), LONG_SIZE, LONG_SIZE, long_sword(px, accents=True))
    sprites["bloom"] = (16, 16, bloom_sprite())
    return sprites


# ===================================================================================================== data
RECIPES = os.path.join(DATA, "tomyen", "recipe")
ADVANCEMENTS = os.path.join(DATA, "tomyen", "advancement", "recipes")
TAGS = os.path.join(DATA, "minecraft", "tags")


def advancement(name, category, unlocks):
    """unlocks: {criterion: item}; any one of them unlocks the recipe."""
    criteria = OrderedDict()
    for criterion, item in unlocks.items():
        criteria[criterion] = {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"}
    criteria["has_the_recipe"] = {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(ADVANCEMENTS, category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria),
        ("requirements", [list(criteria)]), ("rewards", {"recipes": ["tomyen:" + name]})]))


def shaped(name, result, pattern, key, unlocks, count=1, category="misc", book=None):
    write(os.path.join(RECIPES, name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, book or category, unlocks)


def shapeless(name, result, ingredients, unlocks, count=1, category="misc"):
    write(os.path.join(RECIPES, name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, category, unlocks)


def merge_tag(path, values):
    try:
        with open(path) as f:
            tag = json.load(f, object_pairs_hook=OrderedDict)
    except FileNotFoundError:
        tag = OrderedDict([("values", [])])
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write(path, tag)


def loot(name):
    write(os.path.join(DATA, "tomyen", "loot_table", "blocks", name + ".json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                                ("entries", [{"type": "minecraft:item", "name": "tomyen:" + name}]), ("rolls", 1)])]),
        ("random_sequence", "tomyen:blocks/" + name)]))


def item_model(name, parent):
    write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([
        ("parent", parent), ("textures", OrderedDict(layer0="tomyen:item/" + name))]))
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", "tomyen:item/" + name)])})


TOOL_PATTERNS = {"sword": ["X", "X", "#"], "pickaxe": ["XXX", " # ", " # "], "axe": ["XX", "X#", " #"],
                 "shovel": ["X", "#", "#"], "hoe": ["XX", " #", " #"]}
ARMOR_PATTERNS = {"helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"],
                  "boots": ["X X", "X X"]}


def data():
    # The furnace: facing x lit.
    variants = OrderedDict()
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in ("false", "true"):
            v = OrderedDict([("model", "tomyen:block/bloomery_lit" if lit == "true" else "tomyen:block/bloomery")])
            if y:
                v["y"] = y
            variants[f"facing={facing},lit={lit}"] = v
    write(os.path.join(ASSETS, "blockstates", "bloomery.json"), {"variants": variants})
    write(os.path.join(ASSETS, "items", "bloomery.json"), {"model": {"type": "minecraft:model", "model": "tomyen:block/bloomery_lit"}})
    loot("bloomery")
    # Bricks for the hearth, clay for the shaft, cobblestone for the footing, leather for the bellows, iron for the tuyere.
    shaped("bloomery", "tomyen:bloomery", ["BCB", "L I", "SSS"],
           OrderedDict([("B", "minecraft:bricks"), ("C", "minecraft:clay"), ("L", "minecraft:leather"), ("I", "minecraft:iron_ingot"),
                        ("S", "#minecraft:stone_crafting_materials")]),
           OrderedDict([("has_raw_iron", "minecraft:raw_iron"), ("has_bricks", "minecraft:bricks"), ("has_clay", "minecraft:clay_ball")]))

    # Bloom steel's kit.
    metal = "bloom_steel"
    ingot, nugget, block = f"tomyen:{metal}_ingot", f"tomyen:{metal}_nugget", f"tomyen:{metal}_block"
    has_ingot = {"has_bloom_steel": ingot}
    shaped(f"{metal}_block", block, ["###", "###", "###"], {"#": ingot}, has_ingot, category="building")
    shapeless(f"{metal}_ingot_from_{metal}_block", ingot, [block], {"has_block": block}, 9)
    shaped(f"{metal}_ingot_from_nuggets", ingot, ["###", "###", "###"], {"#": nugget}, {"has_nugget": nugget})
    shapeless(f"{metal}_nugget", nugget, [ingot], has_ingot, 9)
    for tool, pattern in TOOL_PATTERNS.items():
        shaped(f"{metal}_{tool}", f"tomyen:{metal}_{tool}", pattern, {"X": ingot, "#": "minecraft:stick"}, has_ingot,
               category="equipment", book="combat" if tool == "sword" else "equipment")
    for piece, pattern in ARMOR_PATTERNS.items():
        shaped(f"{metal}_{piece}", f"tomyen:{metal}_{piece}", pattern, {"X": ingot}, has_ingot, category="equipment", book="combat")
    sword = f"tomyen:{metal}_sword"
    shaped(f"{metal}_long_sword", f"tomyen:{metal}_long_sword", ["S  ", " S ", "  S"], {"S": sword}, {"has_sword": sword},
           category="equipment", book="combat")
    # Steel parts made with bloom steel instead, at its premium (BloomSteelRecipes): half as much again, rounded up.
    # The drill bit: steel's takes three, this five.
    shaped("steel_drill_bit_from_bloom_steel", "tomyen:steel_drill_bit", ["BB", "B ", "BB"], {"B": ingot}, has_ingot)
    # The steel rod: steel's two become three, and with no rubber yet its grip is leather, as the iron rod's is.
    shaped("steel_rod_from_bloom_steel", "tomyen:steel_rod", ["  B", " BS", "LBS"],
           OrderedDict([("B", ingot), ("S", "minecraft:string"), ("L", "minecraft:leather")]), has_ingot, category="equipment")

    for name in [f"{metal}_ingot", f"{metal}_nugget", "bloom"] + [f"{metal}_{p}" for p in ARMOR]:
        item_model(name, "minecraft:item/generated")
    for tool in TOOLS:
        item_model(f"{metal}_{tool}", "minecraft:item/handheld")
    name = f"{metal}_long_sword"
    write(os.path.join(ASSETS, "models", "item", name + ".json"), OrderedDict([
        ("parent", "minecraft:item/handheld"), ("textures", OrderedDict(layer0=f"tomyen:item/{name}")), ("display", long_sword_displays())]))
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", f"tomyen:item/{name}")])})
    write(os.path.join(ASSETS, "blockstates", block.split(":")[1] + ".json"), {"variants": {"": {"model": "tomyen:block/" + metal + "_block"}}})
    write(os.path.join(ASSETS, "models", "block", f"{metal}_block.json"), OrderedDict([
        ("parent", "minecraft:block/cube_all"), ("textures", OrderedDict(all=f"tomyen:block/{metal}_block"))]))
    write(os.path.join(ASSETS, "items", f"{metal}_block.json"), {"model": OrderedDict([("type", "minecraft:model"), ("model", f"tomyen:block/{metal}_block")])})
    loot(f"{metal}_block")
    write(os.path.join(ASSETS, "equipment", metal + ".json"), {"layers": OrderedDict([
        ("humanoid", [{"texture": "tomyen:" + metal}]), ("humanoid_leggings", [{"texture": "tomyen:" + metal}])])})

    merge_tag(os.path.join(DATA, "tomyen", "tags", "item", f"repairs_{metal}.json"), [ingot])
    for kind in TOOLS:
        merge_tag(os.path.join(TAGS, "item", kind + "s.json"), [f"tomyen:{metal}_{kind}"])
    for piece, tag in zip(ARMOR, ["head_armor", "chest_armor", "leg_armor", "foot_armor"]):
        merge_tag(os.path.join(TAGS, "item", tag + ".json"), [f"tomyen:{metal}_{piece}"])
    merge_tag(os.path.join(TAGS, "item", "beacon_payment_items.json"), [ingot])
    merge_tag(os.path.join(TAGS, "block", "mineable", "pickaxe.json"), ["tomyen:bloomery", block])
    merge_tag(os.path.join(TAGS, "block", "needs_stone_tool.json"), [block])
    merge_tag(os.path.join(TAGS, "block", "beacon_base_blocks.json"), [block])
    merge_tag(os.path.join(DATA, "tomyen", "tags", "item", "long_swords.json"), [f"tomyen:{metal}_long_sword"])


LANG = OrderedDict([
    ("block.tomyen.bloomery", "Bloomery"),
    ("block.tomyen.bloomery.desc", "A crude early Foundry: smelts raw iron into blooms"),
    ("block.tomyen.bloomery.bellows", "Work the bellows in rhythm to raise the heat"),
    ("container.tomyen.bloomery", "Bloomery"),
    ("container.tomyen.bloomery.temperature.cold", "Cold: add raw iron and charcoal (%s°C)"),
    ("container.tomyen.bloomery.temperature.rising", "%s°C: work the bellows to reach a bloom heat"),
    ("container.tomyen.bloomery.temperature.bloom", "%s°C: the bloom is gathering"),
    ("container.tomyen.bloomery.temperature.white", "%s°C: white heat, gathering twice as fast"),
    ("container.tomyen.bloomery.bellows", "Bellows: click (or press Space) when the gauge is green"),
    ("container.tomyen.bloomery.forge", "Forge: strike the bloom into Bloom Steel (needs %s°C)"),
    ("container.tomyen.bloomery.forge.cold", "Too cold to forge: needs %s°C"),
    ("container.tomyen.bloomery.stroke.early", "Too soon!"),
    ("container.tomyen.bloomery.stroke.good", "Good!"),
    ("container.tomyen.bloomery.stroke.combo", "Good! x%s"),
    ("container.tomyen.bloomery.stroke.late", "Late"),
    ("hud.tomyen.bloomery.temperature", "%s°C"),
    ("hud.tomyen.bloomery.blooming", "Bloom %s%%"),
    ("item.tomyen.bloom", "Bloom"),
    ("item.tomyen.bloom.hint", "Hammer it on an anvil, or at the Bloomery, into Bloom Steel"),
    ("item.tomyen.bloom.blows", "Hammered: %s of %s blows"),
    ("item.tomyen.bloom_steel.crude", "A crude steel; the Foundry makes better"),
    ("item.tomyen.bloom_steel_ingot.stands_in", "Stands in for steel in early recipes, at half as much again"),
    ("item.tomyen.bloom_steel_ingot", "Bloom Steel Ingot"),
    ("item.tomyen.bloom_steel_nugget", "Bloom Steel Nugget"),
    ("block.tomyen.bloom_steel_block", "Block of Bloom Steel"),
    ("item.tomyen.bloom_steel_long_sword", "Bloom Steel Long Sword"),
])
for part in TOOLS + ARMOR:
    LANG[f"item.tomyen.bloom_steel_{part}"] = f"Bloom Steel {part.capitalize()}"


def merge_lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path) as f:
        lang = json.load(f, object_pairs_hook=OrderedDict)
    lang.update(LANG)
    with open(path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ===================================================================================================== preview

def sprite_img(w, h, px):
    img = Img(w, h)
    img.px = list(px)
    return img


def vanilla_img(path):
    w, h, px = vanilla(path)
    return sprite_img(w, h, px)


def mod_img(path):
    return sf.read_tex(os.path.join(TEX, path + ".png"))


def screen_mockup(font, gui_img, items, temperature, since, combo, stroke, burn, progress, blows, pressed=False):
    """The Bloomery screen as BloomeryScreen draws it, at 1x."""
    img = Img(GUI_W, GUI_H)
    for y in range(GUI_H):
        for x in range(GUI_W):
            img.px[y * GUI_W + x] = gui_img.get(x, y)
    # Flame and arrow, from vanilla's furnace sprites.
    flame = vanilla_img("gui/sprites/container/furnace/lit_progress")
    arrow = vanilla_img("gui/sprites/container/furnace/burn_progress")
    fh = math.ceil(burn * 13) + 1
    for y in range(14 - fh, 14):
        for x in range(14):
            c = flame.get(x, y)
            if c[3]:
                img.set(FLAME_XY[0] + x, FLAME_XY[1] + y, c)
    aw = math.ceil(progress * 24)
    for y in range(16):
        for x in range(aw):
            c = arrow.get(x, y)
            if c[3]:
                img.set(ARROW_XY[0] + x, ARROW_XY[1] + y, c)
    thermometer(img, *THERMO, temperature, 1450)
    state = 2 if pressed else 0
    for y in range(BELLOWS_H):
        for x in range(BELLOWS_W):
            img.set(BELLOWS_XY[0] + x, BELLOWS_XY[1] + y, gui_img.get(BELLOWS_U + state * BELLOWS_W + x, SPRITE_V + y))
    breath(img, *GAUGE, since)
    for y in range(HAMMER_SIZE):
        for x in range(HAMMER_SIZE):
            img.set(HAMMER_XY[0] + x, HAMMER_XY[1] + y, gui_img.get(HAMMER_U + x, SPRITE_V + y))
    for i in range(3):
        img.fill(PIPS_XY[0] + i * 6, PIPS_XY[1], PIPS_XY[0] + i * 6 + 4, PIPS_XY[1] + 2, hexc("ffb43a") if i < blows else hexc("5a5148"))
    for (sx, sy), (tex, count) in items.items():
        img.paste(tex, sx, sy)
        if count > 1:
            font.draw(img, sx + 17 - font.width(str(count)), sy + 9, str(count), hexc("ffffff"), shadow=True)
    font.draw(img, (GUI_W - font.width("Bloomery")) // 2, 6, "Bloomery", hexc("404040"), shadow=False)
    font.draw(img, 8, GUI_H - 94, "Inventory", hexc("404040"), shadow=False)
    verdict = {"good": "Good! x%d" % combo if combo > 1 else "Good!", "early": "Too soon!", "late": "Late"}.get(stroke)
    if verdict:
        colour = {"good": hexc("3fa83a"), "early": hexc("ff5e57"), "late": hexc("c07a10")}[stroke]
        font.draw(img, GAUGE[2] - font.width(verdict), GAUGE[3] + 3, verdict, colour, shadow=False)
    return img


SCALE_T = 1500


def heat_colour(t):
    t = max(0.0, min(1.0, (t - 400) / (SCALE_T - 400)))
    stops = [hexc("6e1a0e"), hexc("c2360c"), hexc("f07a1e"), hexc("ffc94a"), hexc("fff6d8")]
    s = t * (len(stops) - 1)
    i = min(len(stops) - 2, int(s))
    return mixc(stops[i], stops[i + 1], s - i)


def thermometer(img, x0, y0, x1, y1, temperature, ceiling):
    img.fill(x0, y0, x1, y1, hexc("1c1714"))
    lvl = lambda t: y1 - round(max(0.0, min(1.0, t / SCALE_T)) * (y1 - y0))
    top = lvl(temperature)
    for y in range(top, y1):
        img.fill(x0, y, x1, y + 1, mixc(heat_colour(temperature), hexc("6e1a0e"), (y - top) / max(1, y1 - top)))
    for t, c in ((1150, hexc("ffb43a")), (1300, hexc("ffffff"))):
        y = lvl(t)
        img.fill(x0 - 3, y, x0 - 1, y + 1, c)
        img.fill(x0, y, x1, y + 1, c[:3] + (128,))
    y = lvl(ceiling)
    img.fill(x1 + 1, y, x1 + 3, y + 1, hexc("b0a8a0"))


REFILL, WINDOW = 12, 10
SPAN = REFILL + WINDOW + 8


def breath(img, x0, y0, x1, y1, since):
    w = x1 - x0
    ws = x0 + round(REFILL / SPAN * w)
    we = x0 + round((REFILL + WINDOW) / SPAN * w)
    img.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, hexc("2a211c"))
    img.fill(x0, y0, x1, y1, hexc("1c1714"))
    img.fill(ws, y0, we, y1, hexc("294a2a"))
    inw = REFILL <= since <= REFILL + WINDOW
    colour = hexc("8b95a1") if since < REFILL else hexc("6ee06a") if inw else hexc("e8a33c")
    filled = round(max(0.0, min(1.0, since / SPAN)) * w)
    img.fill(x0, y0 + 1, x0 + filled, y1 - 1, colour)
    if inw:
        img.fill(x0 - 1, y0 - 1, x1 + 1, y0, hexc("6ee06a"))
        img.fill(x0 - 1, y1, x1 + 1, y1 + 1, hexc("6ee06a"))
    img.fill(ws, y0 - 1, ws + 1, y1 + 1, hexc("6ee06a"))
    img.fill(we - 1, y0 - 1, we, y1 + 1, hexc("6ee06a"))


def heat_bar(img, x0, y0, x1, y1, temperature, ceiling):
    img.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, hexc("2a211c"))
    img.fill(x0, y0, x1, y1, hexc("1c1714"))
    w = x1 - x0
    img.fill(x0, y0, x0 + round(min(1.0, temperature / SCALE_T) * w), y1, heat_colour(temperature))
    for t, c in ((1150, hexc("ffb43a")), (1300, hexc("ffffff"))):
        x = x0 + round(t / SCALE_T * w)
        img.fill(x, y0 - 2, x + 1, y1 + 2, c)
    x = x0 + round(ceiling / SCALE_T * w)
    img.fill(x, y1, x + 1, y1 + 2, hexc("b0a8a0"))


def hud_mockup(font, temperature, since, verdict, colour):
    """The HUD under the crosshair, as BloomeryClient draws it, at 1x over a patch of scene."""
    img = Img(140, 60, hexc("6d8a52"))
    img.fill(0, 0, 140, 30, hexc("8fb3e0"))
    cx, cy = 70, 14
    img.fill(cx - 4, cy, cx + 5, cy + 1, hexc("ffffff"))
    img.fill(cx, cy - 4, cx + 1, cy + 5, hexc("ffffff"))
    x0, y0 = 20, cy + 12
    img.fill(x0 - 4, y0 - 4, x0 + 104, y0 + 22, (0x10, 0x10, 0x14, 0xB0))
    font.draw(img, x0, y0 - 2, "%d°C" % temperature, hexc("e6e0d8"))
    if verdict:
        font.draw(img, x0 + 100 - font.width(verdict), y0 - 2, verdict, colour)
    heat_bar(img, x0, y0 + 9, x0 + 100, y0 + 12, temperature, 1450)
    breath(img, x0, y0 + 15, x0 + 100, y0 + 18, since)
    return img


class Font(sf.Font):
    """The preview's font, with a degree sign (the game draws it from its unicode font)."""

    def glyph_width(self, ch):
        return 4 if ch == "°" else super().glyph_width(ch)

    def _glyph(self, img, x, y, ch, c, k):
        if ch == "°":
            for gx, gy in ((1, 0), (0, 1), (2, 1), (1, 2)):
                img.fill(x + gx * k, y + gy * k, x + (gx + 1) * k, y + (gy + 1) * k, c)
            return
        super()._glyph(img, x, y, ch, c, k)


def preview(path, elements, base, glow_lit, glow_dark, gui_img, sprites):
    font = Font()
    Wd, Ht = 1640, 1560
    sheet = Img(Wd, Ht, hexc("24272e"))
    label = lambda x, y, s, k=2: sf.label(sheet, font, x, y, s, k)
    lit_q = sf.model_quads(model(elements, True), {"#base": base, "#glow": glow_lit})
    dark_q = sf.model_quads(model(elements, False), {"#base": base, "#glow": glow_dark})

    # Row 1: cold by day, lit at dusk, from both front corners, and the back.
    S = 290
    panels = [("COLD, BY DAY", sf.day_sky, dark_q, lambda p: sf.view_34(p, 35, 28), sf.day_light),
              ("LIT, AT NIGHT", sf.night_sky, lit_q, lambda p: sf.view_34(p, 35, 28), sf.night_light),
              ("LIT, FROM THE BELLOWS SIDE", sf.night_sky, lit_q, lambda p: sf.view_34(p, -40, 24), sf.night_light),
              ("COLD, FROM BEHIND", sf.day_sky, dark_q, lambda p: sf.view_34(p, 150, 26), sf.day_light)]
    for i, (title, sky, quads, view, light) in enumerate(panels):
        x0 = 15 + i * 385
        sky(sheet, x0, 50, x0 + 370, 470)
        sf.render(sheet, quads, view, x0 + 185, 270, S, light)
        label(x0, 20, title)

    # Row 2: the screen mid-run (3x), and the HUD (3x) in two moments.
    item = lambda n: mod_img("item/" + n)
    raw = vanilla_img("item/raw_iron")
    charcoal = vanilla_img("item/charcoal")
    shot = screen_mockup(font, gui_img, {ORE: (raw, 23), FUEL: (charcoal, 9), BLOOM_XY: (item("bloom"), 2),
                                         (INGOT[0], INGOT[1]): (item("bloom_steel_ingot"), 5)},
                         1338, 15.4, 4, "good", 0.62, 0.55, 1)
    label(15, 495, "SCREEN: WHITE HEAT, GOOD STROKE x4, FORGE 1/3 (3X)")
    sheet.paste(shot, 15, 525, 3)
    hud1 = hud_mockup(font, 1214, 17.0, "Good! x2", hexc("7cf070"))
    hud2 = hud_mockup(font, 980, 5.0, "Too soon!", hexc("ff6a5e"))
    label(570, 495, "HUD WHILE LOOKING AT IT (3X)")
    sheet.paste(hud1, 570, 525, 3)
    sheet.paste(hud2, 570, 525 + 190, 3)
    label(570, 905, "BUTTON SPRITES (4X): BELLOWS, HOVER, PRESSED / HAMMER, HOVER, OFF", 1)
    for i in range(3):
        sub = Img(BELLOWS_W, BELLOWS_H)
        for y in range(BELLOWS_H):
            for x in range(BELLOWS_W):
                sub.px[y * BELLOWS_W + x] = gui_img.get(BELLOWS_U + i * BELLOWS_W + x, SPRITE_V + y)
        sheet.paste(sub, 570 + i * 112, 925, 4)
        sub = Img(HAMMER_SIZE, HAMMER_SIZE)
        for y in range(HAMMER_SIZE):
            for x in range(HAMMER_SIZE):
                sub.px[y * HAMMER_SIZE + x] = gui_img.get(HAMMER_U + i * HAMMER_SIZE + x, SPRITE_V + y)
        sheet.paste(sub, 570 + i * 80, 1000, 4)

    # Row 2, right: the sprites at 6x beside their vanilla and steel neighbours.
    x0, y0 = 1000, 495
    label(x0, y0, "SPRITES (6X)")
    row = [("RAW IRON", raw), ("BLOOM", sprite_img(*sprites["bloom"])), ("IRON", vanilla_img("item/iron_ingot")),
           ("BLOOM STEEL", sprite_img(*sprites["ingot"])), ("STEEL", mod_img("item/steel_ingot")),
           ("NETHERITE", vanilla_img("item/netherite_ingot"))]
    for i, (name, img) in enumerate(row):
        x = x0 + (i % 3) * 196
        y = y0 + 30 + (i // 3) * 130
        sheet.fill(x, y, x + 96, y + 96, hexc("8b8b8b"))
        sheet.paste(img, x, y, 6)
        label(x, y + 100, name, 1)
    y = y0 + 300
    parts = ["nugget", "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots"]
    for i, p in enumerate(parts):
        x = x0 + (i % 5) * 116
        yy = y + (i // 5) * 116
        sheet.fill(x, yy, x + 96, yy + 96, hexc("8b8b8b"))
        sheet.paste(sprite_img(*sprites[p]), x, yy, 6)
    yy = y + 240
    sheet.fill(x0, yy, x0 + 96, yy + 96, hexc("8b8b8b"))
    sheet.paste(sprite_img(*sprites["block"]), x0, yy, 6)
    label(x0, yy + 100, "BLOCK", 1)
    sheet.fill(x0 + 116, yy, x0 + 116 + 96, yy + 96, hexc("8b8b8b"))
    sheet.paste(vanilla_img("block/iron_block"), x0 + 116, yy, 6)
    label(x0 + 116, yy + 100, "IRON BLOCK", 1)
    ls = mod_img("item/bloom_steel_long_sword")
    sheet.fill(x0 + 232, yy, x0 + 232 + 96, yy + 96, hexc("8b8b8b"))
    sheet.paste(ls, x0 + 232, yy, 3)
    label(x0 + 232, yy + 100, "LONG SWORD", 1)
    layer = mod_img("entity/equipment/humanoid/bloom_steel")
    sheet.fill(x0 + 348, yy, x0 + 348 + 192, yy + 96, hexc("8b8b8b"))
    sheet.paste(layer, x0 + 348, yy, 3)
    label(x0 + 348, yy + 100, "WORN LAYER", 1)

    # Row 3: tooltips.
    grey, dark = hexc("aaaaaa"), hexc("555555")
    tips = [
        [("Bloomery", hexc("ffffff")), ("A crude early Foundry: smelts raw iron into blooms", grey),
         ("Work the bellows in rhythm to raise the heat", hexc("ffaa00"))],
        [("Bloom", hexc("ffffff")), ("Hammer it on an anvil, or at the Bloomery, into Bloom Steel", grey),
         ("Hammered: 2 of 4 blows", hexc("ffaa00"))],
        [("Bloom Steel Ingot", hexc("ffffff")), ("A crude steel; the Foundry makes better", grey),
         ("Stands in for steel in early recipes, at half as much again", dark)],
        [("Bloom Steel Pickaxe", hexc("ffffff")), ("A crude steel; the Foundry makes better", grey), ("", grey),
         ("When in Main Hand:", grey), (" 4 Attack Damage", hexc("00aa00"))],
    ]
    label(15, 1170, "TOOLTIPS (2X)")
    ty, tx = 1200, 15
    for t in tips:
        timg = sf.tooltip(font, t)
        if tx + timg.w * 2 > Wd:
            tx, ty = 15, ty + 130
        sheet.paste(timg, tx, ty, 2)
        tx += timg.w * 2 + 20
    sheet.save(path)
    print("preview:", path)


# ===================================================================================================== main

def main():
    elements, base, glow_lit, glow_dark = build()
    lit = model(elements, True)
    cold = model(elements, False)
    check_elements("bloomery", lit["elements"])
    write(os.path.join(ASSETS, "models", "block", "bloomery.json"), cold)
    write(os.path.join(ASSETS, "models", "block", "bloomery_lit.json"), lit)
    tex = os.path.join(TEX, "block")
    base.save(os.path.join(tex, "bloomery.png"))
    glow_lit.save(os.path.join(tex, "bloomery_glow.png"))
    glow_dark.save(os.path.join(tex, "bloomery_glow_cold.png"))
    g = gui()
    g.save(os.path.join(TEX, "gui", "container", "bloomery.png"))
    sprites = bloom_steel_art()
    data()
    merge_lang()
    print("bloomery: %d elements, no coplanar faces" % len(elements))
    if PREVIEW:
        preview(os.path.abspath(PREVIEW), elements, base, glow_lit, glow_dark, g, sprites)


if __name__ == "__main__":
    main()
