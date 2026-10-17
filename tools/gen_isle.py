"""Builds the art and data of the Isle of the Damned (pass 1, the corpse run): its ground and ruin blocks, the
gravestone, the Soul Cache, the Lantern of Return and the Return Rift, the Revenant's glowing eyes and soul-light
shell, the Soul Ward icon, and every block state, model, item definition, loot table, recipe (with its recipe-book
advancement), tag, sound, lang line, advancement and world generation file the Isle needs.

Every texture is a vanilla one (read from the client jar, as gen_fae.py does) put through a ramp of the Isle's own
colours by its brightness, so it keeps vanilla's shading but can't be mistaken for it:

    ashen stone        stone in cold ash grey, with a paler stratum running through it
    ash soil           coarse dirt in dead grey, three dim soul-cinders caught in it
    soul moss          the moss block in deep teal, its brightest tufts glowing soul-cyan
    grave stone bricks stone bricks (and the cracked and mossy ones) in blue-black mortar and grave grey
    gravestone         polished andesite, pale and cold, with a hairline crack; its name panel darker and smoother
    old headstone      andesite weathered to lichen grey
    isle lantern       the soul lantern with its iron blackened to a teal patina and its flame turned whiter
    isle iron          the anvil's iron, blackened
    soul coals         soul soil burnt to ash, its brightest grains still glowing
    soul glow          glowstone in soul light
    bone pile          the bone block's grain laid lengthwise, aged to grey ivory, with two small skull faces (one
                       with glowing sockets, for the Soul Cache's carving)
    return rift        the nether portal's swirl, in soul light (animated, as the portal is)
    revenant eyes      soul-fire pinpoints where a player skin's eyes are (on the face and on the hat layer)
    revenant aura      the charged creeper's swirl, in soul light
    soul ward          the Resistance icon, in soul light

The detailed blocks are built here from boxes (the Java shapes in isle/*Block.java follow the same layout):

    gravestone         a stele on a two-step plinth: a recessed name panel (the owner's name is drawn on it by
                       GravestoneRenderer at z 7.5), a stepped arch with a carved cross, soul moss on the plinth
                       and creeping up one side, and a little soul lantern at its front-left foot
    soul cache         a stone ossuary: plinth, corner posts, a soul-lit seam under a ridged lid, a skull carved on
                       its front with glowing sockets, crossed bones on the lid and an iron hasp with a lit keyhole
    lantern of return  two blocks tall: a stepped base, a post with iron collars and four soul-lit runes, a
                       capital, and a big soul lantern with corner posts under a stepped roof and a crystal finial
    return rift        a tall lens of soul-light swirl with a brighter core, a glowing crack at its foot and five
                       chips of ashen stone hanging in the air round it
    soul brazier       a blackened iron bowl on four splayed legs (braced, on feet) with a lip and corner spikes,
                       full of soul coals, with crossed planes of soul fire
    bone pile          a skull, four long bones (one standing half buried), ribs and a few vertebrae
    old headstone      a small arched headstone with a carved cross and lichen, on a mound of ash; three lean
                       variants (upright, tilted back, tilted aside)

Every model is checked for z-fighting in world space (tools/coplanar.py) before it's written. Stairs, slab and wall
block states and models are vanilla stone bricks', renamed.

Writes build/isle_preview.png (or the path given with --preview): the blocks in small scenes from two sides, then
every texture enlarged.

Usage: python3 tools/gen_isle.py src/main/resources/assets/tomyen [--preview path.png]"""
import colorsys, json, math, os, sys, zipfile
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if "--preview" in ARGS:
    PREVIEW = ARGS[ARGS.index("--preview") + 1]
    del ARGS[ARGS.index("--preview"):ARGS.index("--preview") + 2]
if not ARGS:
    sys.exit(__doc__.rsplit("Usage: ", 1)[1])
ASSETS = ARGS[0]
sys.argv = sys.argv[:1] + ARGS[:1]
import gen_fae  # noqa: E402  (reads ASSETS from sys.argv, as this script does)
from gen_fae import (Sprite, ramp, recolour, lum, hexc, read_texture, renamed, item_def, block_model, simple_state,  # noqa: E402
                     recipe, shaped, shapeless, tag, common_jar, CLEAR)
from gen_longswords import client_jar, REPO  # noqa: E402
from gunkit import png, write, rx, ry, rz, mul, rotate, set_rotation, copy  # noqa: E402
import coplanar  # noqa: E402

DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
BLOCK_TEX = os.path.join(ASSETS, "textures", "block")

# ======================================================================================================= colours

ASH = [0x22252a, 0x34373e, 0x484c53, 0x5f636a, 0x7a7e85]
SOIL = [0x2e2d2f, 0x454347, 0x5c5a5e, 0x767478, 0x929094]
MOSS = [0x0a2023, 0x10363b, 0x175257, 0x217371, 0x3a9c93]
SOUL = [0x0f3048, 0x1f6f8a, 0x45b8cf, 0x8eeaf2, 0xeaffff]
GRAVE = [0x12161c, 0x293035, 0x40484d, 0x576065, 0x717b7f]
GRAVE_LIGHT = [0x3c444a, 0x535d65, 0x6c777f, 0x87929a, 0xa5afb4]
PANEL = [0x262c31, 0x2d3439, 0x353c41, 0x3d4449]
HEADSTONE = [0x363b3c, 0x4b5152, 0x626868, 0x7a807d, 0x949a95]
IRON = [0x0e0f12, 0x1b1d21, 0x2a2d33, 0x3d4148, 0x575c64]
PATINA = [0x0e1214, 0x1c2528, 0x2b3a3b, 0x425655, 0x67827d]
BONE = [0x58544a, 0x7f7966, 0xa49d86, 0xc4bda3, 0xddd8c2]
RIFT = [0x061322, 0x103a5c, 0x2479a3, 0x5dcfe3, 0xd8fbff]
COALS = [0x0b0d10, 0x171b20, 0x252b31, 0x343c42]
SOCKET = (20, 17, 15, 255)


def strip_frames(s):
    """Frames of an animated texture strip (16 wide, n*16 tall)."""
    return s.h // s.w


def ashen_stone(src):
    """Stone in cold ash, and a paler stratum (one texel lighter, wandering a row up and down) across it."""
    s = Sprite(16, 16, recolour(src.px, ASH))
    for x in range(16):
        y = 10 + round(1.4 * math.sin(2 * math.pi * x / 16) + 0.6 * math.sin(4 * math.pi * x / 16 + 1.0))
        for yy, lift in ((y, 0.1), (y + 1, 0.04)):
            c = s.get(x, yy)
            s.set(x, yy, ramp(ASH, min(1.0, (lum(c) - 0.13) / 0.36 + lift)))
    return s


def ash_soil(src):
    """Coarse dirt in dead, pale grey, with one dim soul-cinder caught in it."""
    s = Sprite(16, 16, recolour(src.px, SOIL))
    s.set(11, 9, ramp(SOUL, 0.22))
    return s


def soul_moss(src):
    """The moss in deep teal; its brightest few tufts glow soul-cyan."""
    s = Sprite(16, 16, recolour(src.px, MOSS))
    ls = sorted(lum(c) for c in src.px)
    cut = ls[int(len(ls) * 0.96)]
    for i, c in enumerate(src.px):
        if lum(c) >= cut:
            s.px[i] = ramp(SOUL, 0.45 + 0.3 * (lum(c) - cut) / max(1e-6, ls[-1] - cut))
    return s


def grave_bricks(src):
    return Sprite(16, 16, recolour(src.px, GRAVE))


def mossy_grave_bricks(src):
    green = lambda c, i: c[1] > c[0] + 6
    px = recolour(src.px, GRAVE, lambda c, i: not green(c, i), source=src.px)
    px = recolour(px, MOSS[:4], green, source=src.px)
    return Sprite(16, 16, px)


def gravestone(src):
    """Pale, cold polished stone, with a hairline crack running down from the top edge."""
    s = Sprite(16, 16, recolour(src.px, GRAVE_LIGHT))
    for x, y in ((11, 0), (11, 1), (12, 2), (12, 3), (11, 4), (11, 5), (10, 6)):
        s.set(x, y, hexc(0x2a3136))
        s.set(x + 1, y, ramp(GRAVE_LIGHT, 0.95))
    return s


def gravestone_panel(src):
    return Sprite(16, 16, recolour(src.px, PANEL))


def old_headstone(src):
    return Sprite(16, 16, recolour(src.px, HEADSTONE))


def isle_lantern(src):
    """The soul lantern: its iron (the unsaturated texels) to a dark teal patina, its flame and glass whiter."""
    metal = lambda c, i: colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)[1] < 0.3
    px = recolour(src.px, PATINA, metal, source=src.px)
    px = recolour(px, SOUL[1:], lambda c, i: not metal(c, i), source=src.px)
    return Sprite(src.w, src.h, px)


def isle_iron(src):
    return Sprite(16, 16, recolour(src.px, IRON))


def soul_coals(src):
    s = Sprite(16, 16, recolour(src.px, COALS))
    ls = sorted(lum(c) for c in src.px)
    cut = ls[int(len(ls) * 0.88)]
    for i, c in enumerate(src.px):
        if lum(c) >= cut:
            s.px[i] = ramp(SOUL, 0.35 + 0.55 * (lum(c) - cut) / max(1e-6, ls[-1] - cut))
    return s


def soul_glow(src):
    return Sprite(16, 16, recolour(src.px, SOUL[1:]))


SKULL = ["1222221",
         "2DD2DD2",
         "2DD2DD2",
         "2223222",
         "1233321",
         "1424241"]


def bone_pile(side, top):
    """Rows 0-7: the bone block's side turned so its grain runs along u (bone shafts lie along x). The lower left
    and lower right quarters each hold a 7x6 skull face at (0, 9) and (8, 9): plain sockets, and glowing ones."""
    s = Sprite(16, 16)
    shaft = recolour([side.get(y, x) for y in range(16) for x in range(16)], BONE)
    for y in range(8):
        for x in range(16):
            s.set(x, y, shaft[y * 16 + x])
    fill = ramp(BONE, 0.68)
    for y in range(8, 16):
        for x in range(16):
            s.set(x, y, fill)
    colours = {"1": ramp(BONE, 0.45), "2": ramp(BONE, 0.78), "3": ramp(BONE, 0.22), "4": ramp(BONE, 0.97)}
    for ox, glow in ((0, False), (8, True)):
        for y, row in enumerate(SKULL):
            for x, k in enumerate(row):
                if k == "D":
                    c = ramp(SOUL, 0.55 if y == 1 else 0.92) if glow else SOCKET
                elif k == "3" and glow:
                    c = SOCKET
                else:
                    c = colours[k]
                s.set(ox + x, 9 + y, c)
    return s


def return_rift(src):
    px = recolour(src.px, RIFT)
    return Sprite(src.w, src.h, [c[:3] + (max(c[3], 190),) if c[3] else c for c in px])


def revenant_eyes():
    """Soul fire where a player skin's eyes are (face 8-16 x 8-16, and the hat layer 40-48 x 8-16): two-texel eyes
    on row 12, a dimmer texel above and below each. Everything else is clear (the eyes render type adds light)."""
    s = Sprite(64, 64)
    for ox in (0, 32):
        for ex in (9, 13):
            for dx in (0, 1):
                s.set(ox + ex + dx, 12, ramp(SOUL, 1.0 if dx == 0 else 0.85))
                s.set(ox + ex + dx, 11, ramp(SOUL, 0.35))
                s.set(ox + ex + dx, 13, ramp(SOUL, 0.5))
    return s


def revenant_aura(src):
    """The charged creeper's swirl (64x32), twice over to fill the player model's 64x64 layout, in soul light (the
    energy swirl adds its colour, so black is clear)."""
    px = recolour(src.px, [0x000000, 0x08202c, 0x1a5c74, 0x4fb8cc])
    s = Sprite(64, 64)
    for y in range(64):
        for x in range(64):
            c = px[(y % src.h) * src.w + x]
            s.set(x, y, c[:3] + (255,) if c[3] else CLEAR)
    return s


def soul_ward(src):
    return Sprite(src.w, src.h, recolour(src.px, SOUL))


# ======================================================================================================= models


def fit(a, b):
    """A span of texture coordinates moved by whole tiles (or slid) to lie within 0-16."""
    if b - a >= 16:
        return 0.0, 16.0
    shift = 0
    if a < 0:
        shift = math.ceil(-a / 16) * 16
    elif b > 16:
        shift = -math.ceil((b - 16) / 16) * 16
    a, b = a + shift, b + shift
    if a < 0:
        a, b = 0.0, b - a
    if b > 16:
        a, b = a - (b - 16), 16.0
    return round(a, 4), round(b, 4)


def auto_uv(face, f, t):
    """Vanilla's default UV for a face (the texture laid on as if the element were part of a full block)."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    u = {"north": (16 - x1, 16 - x0), "south": (x0, x1), "east": (16 - z1, 16 - z0), "west": (z0, z1),
         "up": (x0, x1), "down": (x0, x1)}[face]
    v = {"up": (z0, z1), "down": (16 - z1, 16 - z0)}.get(face, (16 - y1, 16 - y0))
    u, v = fit(*u), fit(*v)
    return [u[0], v[0], u[1], v[1]]


FACES = ["north", "east", "south", "west", "up", "down"]
CULL = {"down": (1, 0), "up": (1, 16), "north": (2, 0), "south": (2, 16), "west": (0, 0), "east": (0, 16)}


def box(out, f, t, tex, faces=None, uv=None, rot=None, emit=0, name=None):
    """An element. tex: texture variable for every face; faces: per-face texture (None drops the face); uv: per-face
    UV (else vanilla's default, kept inside the texture); rot: (matrix, origin)."""
    faces = faces or {}
    uv = uv or {}
    e = OrderedDict()
    if name:
        e["name"] = name
    e["from"] = [round(v, 4) for v in f]
    e["to"] = [round(v, 4) for v in t]
    if emit:
        e["light_emission"] = emit
        e["shade"] = False
    inside = all(0 <= v <= 16 for v in e["from"] + e["to"])
    fs = OrderedDict()
    for face in FACES:
        texture = faces.get(face, tex)
        if texture is None:
            continue
        d = OrderedDict([("uv", uv.get(face) or auto_uv(face, e["from"], e["to"])), ("texture", texture)])
        axis, plane = CULL[face]
        at = e["to"][axis] if plane == 16 else e["from"][axis]
        if inside and rot is None and at == plane:
            d["cullface"] = face
        fs[face] = d
    e["faces"] = fs
    if rot:
        set_rotation(e, rot[0], rot[1])
    out.append(e)
    return e


def turned(elements, m, pivot):
    """The elements turned by m about pivot (rotations compose), with any cullfaces dropped."""
    rotate(elements, m, pivot)
    for e in elements:
        for d in e["faces"].values():
            d.pop("cullface", None)
    return elements


def save_model(name, textures, elements, parent="minecraft:block/block"):
    coplanar.check_elements(name, elements)
    for e in elements:
        for v in e["from"] + e["to"]:
            assert -16 <= v <= 32, (name, e)
    write(os.path.join(ASSETS, "models", "block", name + ".json"),
          OrderedDict([("parent", parent), ("textures", textures), ("elements", elements)]))
    print(f"  {name}: {len(elements)} elements")


# ---- the gravestone (front to the north; the panel's face at z 7.5, its name drawn by GravestoneRenderer) ----


def lantern(out, cx, y, cz, scale=1.0):
    """The little soul lantern at the gravestone's foot: base, body, cap and crossed handle."""
    s = scale
    side, top, cap = [0, 2, 6, 9], [0, 9, 6, 15], [1, 0, 5, 2]
    box(out, (cx - 1.25 * s, y, cz - 1.25 * s), (cx + 1.25 * s, y + 0.5 * s, cz + 1.25 * s), "#lantern",
        uv={f: cap for f in FACES[:4]} | {"up": [1, 10, 5, 14], "down": [1, 10, 5, 14]}, name="lantern_base")
    box(out, (cx - s, y + 0.5 * s, cz - s), (cx + s, y + 3.5 * s, cz + s), "#lantern",
        uv={f: side for f in FACES[:4]} | {"up": top}, faces={"down": None}, emit=12, name="lantern_body")
    box(out, (cx - 0.75 * s, y + 3.5 * s, cz - 0.75 * s), (cx + 0.75 * s, y + 4.25 * s, cz + 0.75 * s), "#lantern",
        uv={f: cap for f in FACES[:4]} | {"up": [1, 10, 5, 14]}, faces={"down": None}, name="lantern_cap")
    for angle in (45, -45):
        box(out, (cx - 0.75 * s, y + 4.25 * s, cz), (cx + 0.75 * s, y + 5.25 * s, cz), "#lantern",
            faces={f: None for f in ("east", "west", "up", "down")}, uv={"north": [14, 1, 11, 3], "south": [11, 1, 14, 3]},
            rot=(ry(angle), (cx, y + 4.25 * s, cz)), name="lantern_handle")


# The little lantern's centre (x; z is 5): GravestoneBlock.animateTick puts its flame here.
LANTERN_X = 3.3


def gravestone_model():
    els = []
    # The plinth: a broad slab and a step.
    box(els, (1, 0, 2), (15, 2, 14), "#base", name="plinth")
    box(els, (2, 2, 3), (14, 3, 13), "#base", name="plinth_step")
    # The stele: a core whose front, set back half a pixel, is the name panel, framed by four strips.
    box(els, (3, 3, 7.5), (13, 13, 11), "#stone", faces={"north": "#panel", "down": None}, name="stele")
    box(els, (3, 3, 7), (13, 5, 7.5), "#stone", faces={"south": None, "down": None}, name="frame_bottom")
    box(els, (3, 11, 7), (13, 13, 7.5), "#stone", faces={"south": None}, name="frame_top")
    box(els, (3, 5, 7), (4.5, 11, 7.5), "#stone", faces={"south": None}, name="frame_left")
    box(els, (11.5, 5, 7), (13, 11, 7.5), "#stone", faces={"south": None}, name="frame_right")
    # The arch, in three steps.
    box(els, (3.6, 13, 7), (12.4, 14.2, 11), "#stone", name="arch_1")
    box(els, (4.8, 14.2, 7), (11.2, 15.2, 11), "#stone", name="arch_2")
    box(els, (6.3, 15.2, 7), (9.7, 16, 11), "#stone", name="arch_3")
    # A cross carved in relief on the arch.
    box(els, (7.4, 11.4, 6.6), (8.6, 15.4, 7), "#stone", faces={"south": None}, name="cross_upright")
    box(els, (6.2, 13.2, 6.7), (9.8, 14.4, 7), "#stone", faces={"south": None}, name="cross_arms")
    # Soul moss: on the plinth, spilling over its front edge, up the stele's west side and on the arch's shoulder.
    box(els, (9, 3, 3), (13.5, 3.3, 6), "#moss", faces={"down": None}, name="moss_plinth_front")
    box(els, (2.5, 3, 11), (6.5, 3.25, 12.8), "#moss", faces={"down": None}, name="moss_plinth_back")
    box(els, (9.5, 2, 2.8), (12.5, 3.3, 3), "#moss", faces={"south": None}, name="moss_drape")
    box(els, (9.8, 2, 1.8), (12.3, 2.2, 2.8), "#moss", faces={"down": None}, name="moss_edge")
    box(els, (10, 0.8, 1.8), (12, 2, 2), "#moss", faces={"south": None, "up": None}, name="moss_drip")
    box(els, (2.8, 6, 8), (3, 12, 10.5), "#moss", faces={"east": None}, name="moss_side")
    box(els, (3.6, 14.2, 7.3), (5.5, 14.45, 10.8), "#moss", faces={"down": None}, name="moss_shoulder")
    lantern(els, LANTERN_X, 3, 5)
    return els


# ---- the soul cache (front to the north) ----


def bone(out, cx, cy, cz, length, m, r=0.45, knob=0.75, glow_end=False):
    """A long bone along x, centred on (cx, cy, cz), then turned by m: a shaft with a knob at each end."""
    els = []
    shaft = [0, 1, min(16, length), 1 + 2 * r]
    end = [12, 2, 12 + 2 * knob, 2 + 2 * knob]
    x0, x1 = cx - length / 2, cx + length / 2
    box(els, (x0 + knob, cy - r, cz - r), (x1 - knob, cy + r, cz + r), "#bone",
        uv={f: shaft for f in FACES} | {"east": end, "west": end}, name="bone")
    for a, b in ((x0, x0 + 2 * knob), (x1 - 2 * knob, x1)):
        box(els, (a, cy - knob, cz - knob), (b, cy + knob, cz + knob), "#bone", uv={f: end for f in FACES}, name="knob")
    out += turned(els, m, (cx, cy, cz))


def soul_cache_model():
    els = []
    box(els, (1, 0, 2), (15, 2, 14), "#base", name="plinth")
    box(els, (2.5, 2, 3.5), (13.5, 9.5, 12.5), "#stone", faces={"up": None, "down": None}, name="chest")
    for x0 in (2, 12.5):
        for z0 in (3, 11.5):
            box(els, (x0, 2, z0), (x0 + 1.5, 10, z0 + 1.5), "#stone", faces={"up": None, "down": None}, name="post")
    box(els, (3, 9.5, 4), (13, 10, 12), "#glow", faces={"up": None, "down": None}, emit=15, name="seam")
    box(els, (1.5, 10, 2.5), (14.5, 12.5, 13.5), "#stone", name="lid")
    box(els, (2.5, 12.5, 4), (13.5, 13.5, 12), "#stone", faces={"down": None}, name="lid_ridge")
    box(els, (4, 13.5, 6), (12, 14, 10), "#stone", faces={"down": None}, name="ridge_cap")
    # The carved skull, its sockets lit.
    skull = [8, 9, 15, 15]
    box(els, (5.9, 4.4, 3.05), (10.1, 8.0, 3.5), "#bone", faces={"south": None},
        uv={"north": skull, "east": [0, 1, 1, 4], "west": [0, 1, 1, 4], "up": [0, 1, 4, 2], "down": [0, 1, 4, 2]}, name="skull")
    box(els, (6.6, 3.3, 3.15), (9.4, 4.4, 3.5), "#bone", faces={"south": None},
        uv={"north": [9, 14, 14, 15], "east": [0, 1, 1, 2], "west": [0, 1, 1, 2], "up": [0, 1, 3, 2], "down": [0, 1, 3, 2]},
        name="jaw")
    # Iron straps over the chest and the lid, front and back.
    for x0 in (3.8, 11.2):
        x1 = x0 + 1
        box(els, (x0, 2, 3.2), (x1, 9.5, 3.5), "#iron", faces={"south": None, "down": None}, name="strap_chest_front")
        box(els, (x0, 10, 2.2), (x1, 12.5, 2.5), "#iron", faces={"south": None}, name="strap_lid_front")
        box(els, (x0, 12.5, 2.2), (x1, 12.8, 4), "#iron", faces={"south": None, "down": None}, name="strap_lid_top_front")
        box(els, (x0, 2, 12.5), (x1, 9.5, 12.8), "#iron", faces={"north": None, "down": None}, name="strap_chest_back")
        box(els, (x0, 10, 13.5), (x1, 12.5, 13.8), "#iron", faces={"north": None}, name="strap_lid_back")
        box(els, (x0, 12.5, 12), (x1, 12.8, 13.8), "#iron", faces={"north": None, "down": None}, name="strap_lid_top_back")
    # Soul candles burning down on the lid's four corners.
    for cx in (2.2, 13.8):
        for cz in (3.4, 12.6):
            tall = 1.3 if cx < 8 else 0.9
            box(els, (cx - 0.4, 12.5, cz - 0.4), (cx + 0.4, 12.5 + tall, cz + 0.4), "#bone", faces={"down": None},
                uv={f: [13, 2, 14, 4] for f in FACES}, name="candle")
            box(els, (cx - 0.15, 12.5 + tall, cz - 0.15), (cx + 0.15, 13.0 + tall, cz + 0.15), "#glow", faces={"down": None},
                emit=15, name="candle_flame")
    # Crossed bones on the lid.
    bone(els, 8, 14.35, 8, 7.0, ry(35), r=0.35, knob=0.5)
    bone(els, 8, 14.42, 8, 7.0, ry(-35), r=0.35, knob=0.5)
    # The hasp and its keyhole.
    box(els, (7, 8.6, 2.1), (9, 11, 2.5), "#iron", faces={"south": None}, name="hasp")
    box(els, (7.6, 9.2, 2.0), (8.4, 10.2, 2.1), "#glow", faces={f: None for f in FACES if f != "north"}, emit=15, name="keyhole")
    return els


# ---- the lantern of return (two blocks tall) ----


def lantern_of_return_model():
    els = []
    box(els, (2.5, 0, 2.5), (13.5, 2, 13.5), "#base", name="base")
    box(els, (3.5, 2, 3.5), (12.5, 3, 12.5), "#stone", faces={"down": None}, name="base_step")
    box(els, (5.5, 3, 5.5), (10.5, 15, 10.5), "#stone", faces={"down": None, "up": None}, name="post")
    box(els, (5, 7, 5), (11, 8, 11), "#iron", name="collar_low")
    box(els, (5, 12, 5), (11, 13, 11), "#iron", name="collar_high")
    for face, f, t in (("north", (7.25, 9.25, 5.4), (8.75, 11.25, 5.5)), ("south", (7.25, 9.25, 10.5), (8.75, 11.25, 10.6)),
                       ("west", (5.4, 9.25, 7.25), (5.5, 11.25, 8.75)), ("east", (10.5, 9.25, 7.25), (10.6, 11.25, 8.75))):
        box(els, f, t, "#glow", faces={g: None for g in FACES if g != face}, emit=13, name="rune")
    box(els, (4.5, 15, 4.5), (11.5, 16.5, 11.5), "#stone", name="capital")
    box(els, (4.75, 16.5, 4.75), (11.25, 17.5, 11.25), "#iron", faces={"down": None}, name="lantern_plate")
    side, cap = [0, 2, 6, 9], [1, 0, 5, 2]
    box(els, (5, 17.5, 5), (11, 24.5, 11), "#lantern", uv={f: side for f in FACES[:4]}, faces={"up": None, "down": None},
        emit=15, name="lantern_cage")
    for x0 in (4.6, 10.6):
        for z0 in (4.6, 10.6):
            box(els, (x0, 17.5, z0), (x0 + 0.8, 24.5, z0 + 0.8), "#iron", faces={"up": None, "down": None}, name="cage_post")
    box(els, (4.25, 24.5, 4.25), (11.75, 25.5, 11.75), "#iron", name="roof_1")
    box(els, (5.25, 25.5, 5.25), (10.75, 26.5, 10.75), "#iron", faces={"down": None}, name="roof_2")
    box(els, (6.5, 26.5, 6.5), (9.5, 27.5, 9.5), "#lantern", faces={"down": None}, uv={f: cap for f in FACES}, name="roof_3")
    box(els, (7.3, 27.5, 7.3), (8.7, 29.5, 8.7), "#glow", faces={"down": None}, emit=15, rot=(ry(45), (8, 27.5, 8)),
        name="finial")
    return els


# ---- the return rift ----


def return_rift_model():
    els = []
    width = lambda y: 0.5 + 3.3 * math.sin(math.pi * y / 28) ** 0.8
    for i in range(14):
        w = width(2 * i + 1)
        box(els, (8 - w, 2 * i, 7.6), (8 + w, 2 * i + 2, 8.4), "#rift", emit=15, name="lens")
    # The core runs a pixel out of step with the lens, so their tops and bottoms never share a plane.
    for i in range(13):
        c = 0.42 * width(2 * i + 2)
        box(els, (8 - c, 2 * i + 1, 7.35), (8 + c, 2 * i + 3, 8.65), "#core", emit=15, name="core")
    box(els, (6.5, 0, 7), (9.5, 0.15, 9), "#core", faces={"down": None}, emit=15, name="crack")
    for (x, y, z, s, a, b, c) in ((3.4, 5.5, 7.6, 1.2, 20, 30, 12), (12.6, 10.5, 8.6, 1.0, -25, 15, 40),
                                  (4.4, 16.5, 8.9, 0.9, 35, -20, 10), (11.9, 20.5, 7.1, 1.1, 10, 50, -30),
                                  (10.6, 25.8, 8.2, 0.8, -15, 25, 20)):
        chip = []
        box(chip, (x - s / 2, y - s / 2, z - s / 2), (x + s / 2, y + s / 2, z + s / 2), "#chip", name="chip")
        els += turned(chip, mul(rz(c), mul(ry(b), rx(a))), (x, y, z))
    return els


# ---- the soul brazier ----


def soul_brazier_model():
    els = []
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = 8 + sx * 4.25, 8 + sz * 4.25
            leg = []
            box(leg, (cx - 0.5, 0.3, cz - 0.5), (cx + 0.5, 7, cz + 0.5), "#iron", faces={"up": None}, name="leg")
            els += turned(leg, mul(rz(sx * 12), rx(-sz * 12)), (cx, 7, cz))
            fx, fz = cx + sx * 1.35, cz + sz * 1.35
            box(els, (fx - 0.9, 0, fz - 0.9), (fx + 0.9, 0.7, fz + 0.9), "#iron", name="foot")
    box(els, (3.1, 2.4, 2.55), (12.9, 3.0, 3.15), "#iron", name="brace_north")
    box(els, (3.1, 2.4, 12.85), (12.9, 3.0, 13.45), "#iron", name="brace_south")
    box(els, (2.55, 2.5, 3.1), (3.15, 3.1, 12.9), "#iron", name="brace_west")
    box(els, (12.85, 2.5, 3.1), (13.45, 3.1, 12.9), "#iron", name="brace_east")
    box(els, (4, 6, 4), (12, 7, 12), "#iron", name="bowl_bottom")
    box(els, (3, 7, 3), (13, 9, 13), "#iron", faces={"up": None}, name="bowl")
    box(els, (2.5, 9, 2.5), (13.5, 10.5, 13.5), "#coals", faces={"down": None}, emit=6, name="coals")
    box(els, (1.5, 9, 1.5), (14.5, 11.5, 2.5), "#iron", name="wall_north")
    box(els, (1.5, 9, 13.5), (14.5, 11.5, 14.5), "#iron", name="wall_south")
    box(els, (1.5, 9, 2.5), (2.5, 11.5, 13.5), "#iron", faces={"north": None, "south": None}, name="wall_west")
    box(els, (13.5, 9, 2.5), (14.5, 11.5, 13.5), "#iron", faces={"north": None, "south": None}, name="wall_east")
    box(els, (1, 11.5, 1), (15, 12, 2.5), "#iron", name="lip_north")
    box(els, (1, 11.5, 13.5), (15, 12, 15), "#iron", name="lip_south")
    box(els, (1, 11.5, 2.5), (2.5, 12, 13.5), "#iron", faces={"north": None, "south": None}, name="lip_west")
    box(els, (13.5, 11.5, 2.5), (15, 12, 13.5), "#iron", faces={"north": None, "south": None}, name="lip_east")
    for x0 in (1.25, 13.75):
        for z0 in (1.25, 13.75):
            box(els, (x0, 12, z0), (x0 + 1, 13.5, z0 + 1), "#iron", faces={"down": None}, name="spike")
    for angle in (45, -45):
        box(els, (2.5, 10.5, 8), (13.5, 21, 8), "#fire", faces={f: None for f in ("east", "west", "up", "down")},
            uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}, emit=15, rot=(ry(angle), (8, 10.5, 8)), name="flame")
    return els


# ---- the bone pile (front to the north) ----


def on_ground(elements):
    """Drops the bottoms of elements lying flat on the block's floor (turned, if at all, only about y): they're
    against the ground, never seen, and would share its plane with each other."""
    for e in elements:
        r = e.get("rotation")
        flat = not r or ("axis" in r and r["axis"] == "y") or (r.get("x", 0) == 0 and r.get("z", 0) == 0)
        if flat and abs(e["from"][1]) < 1e-6:
            e["faces"].pop("down", None)
    return elements


def bone_pile_model():
    els = []
    skull = []
    box(skull, (5, 0.6, 4), (9, 3.8, 7.6), "#bone", uv={"north": [0, 9, 7, 15], "south": [0, 0, 4, 3], "east": [0, 0, 4, 3],
                                                       "west": [0, 0, 4, 3], "up": [4, 0, 8, 4], "down": [4, 0, 8, 4]}, name="cranium")
    box(skull, (5.6, 0, 3.6), (8.4, 0.8, 5.4), "#bone", uv={"north": [1, 14, 6, 15]}, name="jaw")
    els += turned(skull, ry(-20), (7, 0, 5.8))
    bone(els, 10.2, 0.75, 9.2, 9.0, ry(30))
    bone(els, 7.2, 1.9, 10.3, 8.0, mul(ry(-50), rz(8)))
    bone(els, 4.0, 0.6, 11.6, 7.0, ry(80), r=0.4, knob=0.6)
    bone(els, 12.3, 2.4, 11.8, 6.0, mul(ry(10), rz(55)), r=0.4, knob=0.65)
    for (x, z, a) in ((11.6, 3.6, -20), (12.6, 5.3, -28), (3.0, 5.2, 62)):
        rib = []
        box(rib, (x - 2.6, 0, z - 0.25), (x + 2.6, 0.5, z + 0.25), "#bone", uv={f: [2, 4, 7, 5] for f in FACES}, name="rib")
        els += turned(rib, ry(a), (x, 0, z))
    spine = []
    for k in range(3):
        box(spine, (9.0 + 1.25 * k, 0, 6.8), (10.0 + 1.25 * k, 0.8, 7.8), "#bone", uv={f: [12, 2, 13, 3] for f in FACES},
            name="vertebra")
    els += turned(spine, ry(15), (10.25, 0, 7.3))
    return on_ground(els)


# ---- the old headstone ----


def old_headstone_model(lean):
    stone = []
    box(stone, (4, -1, 7), (12, 10, 9.5), "#stone", name="slab")
    box(stone, (4.6, 10, 7), (10.6, 11, 9.5), "#stone", faces={"down": None}, name="shoulder")
    box(stone, (5.8, 11, 7), (9.8, 12, 9.5), "#stone", faces={"down": None}, name="top")
    box(stone, (7, 12, 7.3), (8.8, 12.6, 9.2), "#stone", faces={"down": None}, name="crown")
    box(stone, (7.5, 4, 6.7), (8.5, 9, 7), "#stone", faces={"south": None}, name="cross_upright")
    box(stone, (6.2, 7, 6.8), (9.8, 8, 7), "#stone", faces={"south": None}, name="cross_arms")
    box(stone, (4.2, 0.6, 6.8), (8, 2, 7), "#moss", faces={"south": None, "down": None}, name="lichen_foot")
    box(stone, (4.6, 11, 7.2), (5.8, 11.2, 9.3), "#moss", faces={"down": None}, name="lichen_shoulder")
    m = [None, rx(10), mul(rz(9), rx(5))][lean]
    if m:
        turned(stone, m, (8, 0, 8.25))
    els = []
    box(els, (3, 0, 5.5), (13, 0.6, 11), "#soil", name="mound")
    return els + stone


# ======================================================================================================= data


def lang_merge(entries):
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge(path, entries):
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    write(path, obj)


def loot(name, pools):
    write(os.path.join(DATA, "tomyen", "loot_table", "blocks", name + ".json"),
          OrderedDict([("type", "minecraft:block"), ("pools", pools), ("random_sequence", f"tomyen:blocks/{name}")]))


def drops_self(name):
    loot(name, [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}),
                             ("entries", [OrderedDict([("type", "minecraft:item"), ("name", f"tomyen:{name}")])]), ("rolls", 1)])])


def facing_state(name, models, extra=""):
    """Variants by facing (models drawn facing north), optionally times other properties: models maps a property
    suffix (",lean=0", say) to a model."""
    variants = OrderedDict()
    for facing, y in (("east", 90), ("north", 0), ("south", 180), ("west", 270)):
        for suffix, model in models.items():
            v = OrderedDict([("model", model)])
            if y:
                v["y"] = y
            variants[f"facing={facing}{suffix}"] = v
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


SOUNDS = OrderedDict([
    ("isle_respects", ("isle_respects", [("minecraft:block/respawn_anchor/charge1", 0.7), ("minecraft:block/respawn_anchor/charge2", 0.7),
                                                         ("minecraft:block/respawn_anchor/charge3", 0.7)])),
    ("isle_pulled_down", ("isle_pulled_down", [("minecraft:mob/wither/spawn", 1.4)])),
    ("isle_offering", ("isle_offering", [("minecraft:block/beacon/activate", 0.7)])),
    ("isle_arrive", ("isle_arrive", [("minecraft:block/respawn_anchor/set_spawn1", 0.6), ("minecraft:block/respawn_anchor/set_spawn2", 0.6),
                                     ("minecraft:block/respawn_anchor/set_spawn3", 0.6)])),
    ("isle_return", ("isle_return", [("minecraft:block/respawn_anchor/deplete1", 0.8), ("minecraft:block/respawn_anchor/deplete2", 0.8)])),
    ("isle_hoard_sinks", ("isle_hoard_sinks", [(f"minecraft:ambient/nether/soulsand_valley/mood{i}", 0.6) for i in range(1, 5)])),
    ("isle_loop", (None, [("minecraft:ambient/nether/soulsand_valley/ambience", 0.8, True)])),
    ("isle_additions", ("isle_additions", [(f"minecraft:ambient/nether/soulsand_valley/whisper{i}", 0.7) for i in range(1, 9)])),
    ("music.isle", (None, [("minecraft:music/game/nether/soulsand_valley/so_below", 0.85, True)])),
    ("revenant_ambient", ("revenant_ambient", [(f"minecraft:mob/ghast/moan{i}", 0.6) for i in range(1, 8)])),
    ("revenant_hurt", ("revenant_hurt", [(f"minecraft:mob/wither/hurt{i}", 1.5) for i in range(1, 5)])),
    ("revenant_death", ("revenant_death", [("minecraft:mob/ghast/death", 0.6)])),
    ("revenant_form", ("revenant_form", [("minecraft:mob/evocation_illager/prepare_summon", 0.6)])),
    ("soul_cache_open", ("soul_cache_open", [("minecraft:block/vault/open_shutter", 0.7)])),
    ("return_rift_opens", ("return_rift_opens", [("minecraft:block/end_portal/endportal", 1.4)])),
])


def sounds_json():
    out = OrderedDict()
    for event, (subtitle, files) in SOUNDS.items():
        entry = OrderedDict()
        if subtitle:
            entry["subtitle"] = "subtitles.tomyen." + subtitle
        entry["sounds"] = []
        for f in files:
            s = OrderedDict([("name", f[0]), ("pitch", f[1])])
            if len(f) > 2:
                s["stream"] = True
            entry["sounds"].append(s)
        out[event] = entry
    return out


LANG = OrderedDict([
    ("block.tomyen.ashen_stone", "Ashen Stone"),
    ("block.tomyen.ash_soil", "Ash Soil"),
    ("block.tomyen.soul_moss", "Soul Moss"),
    ("block.tomyen.soul_moss_carpet", "Soul Moss Carpet"),
    ("block.tomyen.grave_stone_bricks", "Grave Stone Bricks"),
    ("block.tomyen.cracked_grave_stone_bricks", "Cracked Grave Stone Bricks"),
    ("block.tomyen.mossy_grave_stone_bricks", "Mossy Grave Stone Bricks"),
    ("block.tomyen.grave_stone_brick_stairs", "Grave Stone Brick Stairs"),
    ("block.tomyen.grave_stone_brick_slab", "Grave Stone Brick Slab"),
    ("block.tomyen.grave_stone_brick_wall", "Grave Stone Brick Wall"),
    ("block.tomyen.old_headstone", "Old Headstone"),
    ("block.tomyen.bone_pile", "Bone Pile"),
    ("block.tomyen.soul_brazier", "Soul Brazier"),
    ("block.tomyen.gravestone", "Gravestone"),
    ("block.tomyen.gravestone.rip", "R.I.P."),
    ("block.tomyen.soul_cache", "Soul Cache"),
    ("block.tomyen.lantern_of_return", "Lantern of Return"),
    ("block.tomyen.return_rift", "Return Rift"),
    ("block.tomyen.bed.isle", "The dead don't sleep here"),
    ("container.tomyen.soul_cache", "%s's Soul Cache"),
    ("entity.tomyen.revenant", "Revenant"),
    ("entity.tomyen.revenant.named", "%s's Revenant"),
    ("effect.tomyen.soul_ward", "Soul Ward"),
    ("dimension.minecraft.overworld", "the Overworld"),
    ("dimension.minecraft.the_nether", "the Nether"),
    ("dimension.minecraft.the_end", "the End"),
    ("dimension.tomyen.feywild", "the Feywild"),
    ("dimension.tomyen.isle", "the Isle of the Damned"),
    ("isle.tomyen.title", "The Isle of the Damned"),
    ("isle.tomyen.subtitle", "Your Revenant waits in %s"),
    ("isle.tomyen.subtitle.empty", "Nothing of yours lies here"),
    ("isle.tomyen.depth.shore", "the Shore"),
    ("isle.tomyen.depth.hollows", "the Hollows"),
    ("isle.tomyen.depth.abyss", "the Abyss"),
    ("hud.tomyen.isle.revenant", "Your Revenant: %s blocks, in %s"),
    ("message.tomyen.isle.dragged_away", "Your belongings were dragged to the Isle of the Damned. Find your gravestone."),
    ("message.tomyen.isle.gravestone_at", "Your gravestone stands at %s, %s, %s in %s. Sneak and hold use on it to pay your respects."),
    ("message.tomyen.isle.how_to_pay_respects", "Sneak and hold use to pay your respects"),
    ("message.tomyen.isle.paying_respects", "Paying respects"),
    ("message.tomyen.isle.not_your_grave", "Here lies %s. This grave isn't yours to disturb."),
    ("message.tomyen.isle.not_your_cache", "This is %s's Soul Cache. Only they can open it."),
    ("message.tomyen.isle.missing", "The Isle of the Damned can't be reached."),
    ("message.tomyen.isle.hoard_waits", "The Lantern carries you home. What you lost still waits on the Isle."),
    ("message.tomyen.isle.revenant_rises", "Your Revenant rises to meet you."),
    ("message.tomyen.isle.revenant_defeated", "Your Revenant falls. Your belongings wait in its Soul Cache, and a rift home has opened."),
    ("message.tomyen.isle.sinks", "You died on the Isle. Your belongings sink deeper, to %s."),
    ("commands.tomyen.isle.hoard", "%s's hoard: %s stacks and %s XP in %s; gravestone %s; arena %s; loadout score %s"),
    ("commands.tomyen.isle.no_hoard", "%s has no hoard on the Isle"),
    ("subtitles.tomyen.isle_respects", "Respects are paid"),
    ("subtitles.tomyen.isle_pulled_down", "Something pulls you under"),
    ("subtitles.tomyen.isle_offering", "An offering is accepted"),
    ("subtitles.tomyen.isle_arrive", "You wash up on the Isle"),
    ("subtitles.tomyen.isle_return", "The Isle lets you go"),
    ("subtitles.tomyen.isle_hoard_sinks", "Your belongings sink deeper"),
    ("subtitles.tomyen.isle_additions", "The dead whisper"),
    ("subtitles.tomyen.revenant_ambient", "Revenant moans"),
    ("subtitles.tomyen.revenant_hurt", "Revenant hurts"),
    ("subtitles.tomyen.revenant_death", "Revenant falls"),
    ("subtitles.tomyen.revenant_form", "A Revenant forms"),
    ("subtitles.tomyen.soul_cache_open", "Soul Cache opens"),
    ("subtitles.tomyen.return_rift_opens", "A rift home opens"),
    ("advancements.tomyen.isle.root.title", "The Isle of the Damned"),
    ("advancements.tomyen.isle.root.description", "Follow your belongings down through your gravestone"),
    ("advancements.tomyen.isle.revenant.title", "Laid to Rest"),
    ("advancements.tomyen.isle.revenant.description", "Defeat your own Revenant and take back what's yours"),
    ("advancements.tomyen.isle.abyss.title", "All the Way Down"),
    ("advancements.tomyen.isle.abyss.description", "Reach the Abyss, the deepest part of the Isle"),
])


def write_blocks(client, common):
    def vanilla(path):
        return json.loads(client.read(path), object_pairs_hook=OrderedDict)

    def vanilla_data(path):
        return json.loads(common.read(path), object_pairs_hook=OrderedDict)

    # --- Full blocks ------------------------------------------------------------------------------------------
    for name in ["ashen_stone", "ash_soil", "soul_moss", "grave_stone_bricks", "cracked_grave_stone_bricks", "mossy_grave_stone_bricks"]:
        block_model(name, "minecraft:block/cube_all", {"all": f"tomyen:block/{name}"})
        simple_state(name)
        item_def(name, f"tomyen:block/{name}")
    block_model("soul_moss_carpet", "minecraft:block/carpet", {"wool": "tomyen:block/soul_moss"})
    simple_state("soul_moss_carpet")
    item_def("soul_moss_carpet", "tomyen:block/soul_moss_carpet")
    bricks = {"minecraft:block/stone_brick": "tomyen:block/grave_stone_brick", "minecraft:stone_brick": "tomyen:grave_stone_brick"}
    for kind in ["stairs", "slab", "wall"]:
        write(os.path.join(ASSETS, "blockstates", f"grave_stone_brick_{kind}.json"),
              renamed(vanilla(f"assets/minecraft/blockstates/stone_brick_{kind}.json"), bricks))
    for path in client.namelist():
        base = os.path.basename(path)
        if path.startswith("assets/minecraft/models/block/stone_brick_") and base.endswith(".json"):
            write(os.path.join(ASSETS, "models", "block", "grave_" + base), renamed(vanilla(path), bricks))
    item_def("grave_stone_brick_stairs", "tomyen:block/grave_stone_brick_stairs")
    item_def("grave_stone_brick_slab", "tomyen:block/grave_stone_brick_slab")
    item_def("grave_stone_brick_wall", "tomyen:block/grave_stone_brick_wall_inventory")

    # --- Detailed blocks --------------------------------------------------------------------------------------
    print("models:")
    save_model("gravestone", OrderedDict([("particle", "tomyen:block/gravestone"), ("stone", "tomyen:block/gravestone"),
                                          ("panel", "tomyen:block/gravestone_panel"), ("base", "tomyen:block/grave_stone_bricks"),
                                          ("moss", "tomyen:block/soul_moss"), ("lantern", "tomyen:block/isle_lantern")]),
               gravestone_model())
    facing_state("gravestone", {"": "tomyen:block/gravestone"})
    save_model("soul_cache", OrderedDict([("particle", "tomyen:block/gravestone"), ("stone", "tomyen:block/gravestone"),
                                          ("base", "tomyen:block/grave_stone_bricks"), ("bone", "tomyen:block/bone_pile"),
                                          ("glow", "tomyen:block/soul_glow"), ("iron", "tomyen:block/isle_iron")]),
               soul_cache_model())
    facing_state("soul_cache", {"": "tomyen:block/soul_cache"})
    save_model("lantern_of_return", OrderedDict([("particle", "tomyen:block/isle_lantern"), ("stone", "tomyen:block/gravestone"),
                                                 ("base", "tomyen:block/grave_stone_bricks"), ("iron", "tomyen:block/isle_iron"),
                                                 ("glow", "tomyen:block/soul_glow"), ("lantern", "tomyen:block/isle_lantern")]),
               lantern_of_return_model())
    simple_state("lantern_of_return")
    save_model("return_rift", OrderedDict([("particle", "tomyen:block/return_rift"), ("rift", "tomyen:block/return_rift"),
                                           ("core", "tomyen:block/soul_glow"), ("chip", "tomyen:block/ashen_stone")]),
               return_rift_model())
    simple_state("return_rift")
    save_model("soul_brazier", OrderedDict([("particle", "tomyen:block/isle_iron"), ("iron", "tomyen:block/isle_iron"),
                                            ("coals", "tomyen:block/soul_coals"), ("fire", "minecraft:block/soul_fire_0")]),
               soul_brazier_model())
    simple_state("soul_brazier")
    item_def("soul_brazier", "tomyen:block/soul_brazier")
    save_model("bone_pile", OrderedDict([("particle", "tomyen:block/bone_pile"), ("bone", "tomyen:block/bone_pile")]), bone_pile_model())
    facing_state("bone_pile", {"": "tomyen:block/bone_pile"})
    item_def("bone_pile", "tomyen:block/bone_pile")
    for lean in range(3):
        save_model(f"old_headstone_lean{lean}", OrderedDict([("particle", "tomyen:block/old_headstone"), ("stone", "tomyen:block/old_headstone"),
                                                              ("moss", "tomyen:block/soul_moss"), ("soil", "tomyen:block/ash_soil")]),
                   old_headstone_model(lean))
    facing_state("old_headstone", {f",lean={k}": f"tomyen:block/old_headstone_lean{k}" for k in range(3)})
    item_def("old_headstone", "tomyen:block/old_headstone_lean0")

    # --- Loot tables ------------------------------------------------------------------------------------------
    for name in ["ashen_stone", "ash_soil", "soul_moss", "soul_moss_carpet", "grave_stone_bricks", "cracked_grave_stone_bricks",
                 "mossy_grave_stone_bricks", "grave_stone_brick_stairs", "grave_stone_brick_wall", "old_headstone", "soul_brazier"]:
        drops_self(name)
    slab = renamed(vanilla_data("data/minecraft/loot_table/blocks/stone_brick_slab.json"), bricks)
    slab["random_sequence"] = "tomyen:blocks/grave_stone_brick_slab"
    write(os.path.join(DATA, "tomyen", "loot_table", "blocks", "grave_stone_brick_slab.json"), slab)
    loot("bone_pile", [OrderedDict([
        ("condition", {"type": "minecraft:survives_explosion"}),
        ("entries", [OrderedDict([("type", "minecraft:item"), ("modifier", [OrderedDict([
            ("type", "minecraft:set_count"), ("count", OrderedDict([("type", "minecraft:uniform"), ("max", 4), ("min", 2)]))])]),
            ("name", "minecraft:bone")])]),
        ("rolls", 1)])])

    # --- Recipes ----------------------------------------------------------------------------------------------
    names = OrderedDict([("minecraft:mossy_stone_bricks", "tomyen:mossy_grave_stone_bricks"),
                         ("minecraft:cracked_stone_bricks", "tomyen:cracked_grave_stone_bricks"),
                         ("minecraft:moss_block", "tomyen:soul_moss"), ("minecraft:moss_carpet", "tomyen:soul_moss_carpet"),
                         ("minecraft:stone_brick", "tomyen:grave_stone_brick"), ('"minecraft:stone"', '"tomyen:ashen_stone"')])
    for source, unlock in [("stone_bricks", "tomyen:ashen_stone"), ("stone_brick_stairs", "tomyen:grave_stone_bricks"),
                           ("stone_brick_slab", "tomyen:grave_stone_bricks"), ("stone_brick_wall", "tomyen:grave_stone_bricks"),
                           ("stone_bricks_from_stone_stonecutting", "tomyen:ashen_stone"),
                           ("stone_brick_stairs_from_stone_bricks_stonecutting", "tomyen:grave_stone_bricks"),
                           ("stone_brick_slab_from_stone_bricks_stonecutting", "tomyen:grave_stone_bricks"),
                           ("stone_brick_wall_from_stone_bricks_stonecutting", "tomyen:grave_stone_bricks"),
                           ("stone_brick_stairs_from_stone_stonecutting", "tomyen:ashen_stone"),
                           ("stone_brick_slab_from_stone_stonecutting", "tomyen:ashen_stone"),
                           ("stone_brick_wall_from_stone_stonecutting", "tomyen:ashen_stone"),
                           ("cracked_stone_bricks", "tomyen:grave_stone_bricks"),
                           ("mossy_stone_bricks_from_moss_block", "tomyen:soul_moss"), ("moss_carpet", "tomyen:soul_moss")]:
        name = source.replace("stone_brick", "grave_stone_brick").replace("_from_stone_", "_from_ashen_stone_")
        name = name.replace("moss_block", "soul_moss").replace("moss_carpet", "soul_moss_carpet")
        body = renamed(vanilla_data(f"data/minecraft/recipe/{source}.json"), names)
        folder = "decorations" if "wall" in source or "carpet" in source else "building_blocks"
        recipe(name, body, folder, [unlock])
    recipe("soul_brazier", shaped("soul_brazier", ["ISI", " I ", "I I"], OrderedDict([
        ("I", "minecraft:iron_ingot"), ("S", "#minecraft:soul_fire_base_blocks")]), "misc"), "decorations", ["minecraft:soul_soil", "minecraft:soul_sand"])
    recipe("old_headstone", shaped("old_headstone", ["#", "#"], OrderedDict([("#", "tomyen:grave_stone_bricks")]), "building"),
           "decorations", ["tomyen:grave_stone_bricks"])
    recipe("bone_pile", shapeless("bone_pile", ["minecraft:bone"] * 4, "building"), "decorations", ["minecraft:bone"])

    # --- Tags -------------------------------------------------------------------------------------------------
    tag("block", "minecraft", "mineable/pickaxe", ["tomyen:ashen_stone", "tomyen:grave_stone_bricks", "tomyen:cracked_grave_stone_bricks",
                                                   "tomyen:mossy_grave_stone_bricks", "tomyen:grave_stone_brick_stairs",
                                                   "tomyen:grave_stone_brick_slab", "tomyen:grave_stone_brick_wall",
                                                   "tomyen:old_headstone", "tomyen:soul_brazier"])
    tag("block", "minecraft", "mineable/shovel", ["tomyen:ash_soil"])
    tag("block", "minecraft", "mineable/hoe", ["tomyen:soul_moss", "tomyen:soul_moss_carpet"])
    tag("block", "minecraft", "supports_dry_vegetation", ["tomyen:ash_soil"])
    for kind, plural in (("stairs", "stairs"), ("slab", "slabs"), ("wall", "walls")):
        tag("block", "minecraft", plural, [f"tomyen:grave_stone_brick_{kind}"])
        tag("item", "minecraft", plural, [f"tomyen:grave_stone_brick_{kind}"])
    tag("block", "tomyen", "isle_ground", ["tomyen:ashen_stone", "tomyen:ash_soil", "tomyen:soul_moss", "minecraft:soul_soil"])
    tag("item", "tomyen", "isle_holy_offerings", ["tomyen:holy_silver_ingot", "tomyen:holy_silver_nugget", "tomyen:crucifix",
                                                  "tomyen:holy_silver_crucifix"])
    tag("item", "tomyen", "isle_soul_offerings", ["tomyen:soul_charge", "tomyen:soul_steel_ingot", "tomyen:soul_steel_nugget",
                                                  "tomyen:ectoplasm"])
    tag("entity_type", "tomyen", "evil", ["tomyen:revenant"])


# ======================================================================================================= world


def d_noise(name, xz, y=0.0):
    return OrderedDict([("type", "minecraft:noise"), ("noise", name), ("xz_scale", xz), ("y_scale", y)])


def d_add(a, b):
    return OrderedDict([("type", "minecraft:add"), ("left", a), ("right", b)])


def d_mul(a, b):
    return OrderedDict([("type", "minecraft:mul"), ("left", a), ("right", b)])


def d_clamp(x, lo, hi):
    return OrderedDict([("type", "minecraft:clamp"), ("input", x), ("min", lo), ("max", hi)])


# Distance from the origin, squared and over the Abyss's inner radius squared: 0 at the origin, 0.212 where the
# Hollows begin (175 blocks), 1 where the Abyss does (380). IsleDepth's radii match.
ABYSS_R = 380.0
HOLLOWS_T = round((175.0 / ABYSS_R) ** 2, 4)
# How far (in t) each outer ring's terrain takes to come in: about 45 blocks at each edge.
HOLLOWS_RAMP = round(((220.0 / ABYSS_R) ** 2) - HOLLOWS_T, 4)
ABYSS_RAMP = round(((425.0 / ABYSS_R) ** 2) - 1.0, 4)


def depth_t():
    return d_mul(OrderedDict([("type", "minecraft:slice"), ("axis", "y"), ("coordinate", 0),
                              ("input", OrderedDict([("type", "minecraft:distance_to_point"), ("metric", "euclidean_squared"),
                                                     ("point", [0, 0, 0])]))]), round(1.0 / ABYSS_R ** 2, 12))


def d_unary(kind, x):
    return OrderedDict([("type", f"minecraft:{kind}"), ("input", x)])


def ridge(noise, xz):
    """1 along the noise's zero line, falling away either side: sharp crests where the noise crosses zero."""
    return d_add(1.0, d_mul(-2.0, d_unary("abs", d_noise(noise, xz))))


def surface_height():
    """The ground's height (as density: it's the height, less y, plus a little 3D noise out past the Shore).

    The Shore is as it was: islands from broad noise round a sea at 63, an island always at the origin (the arrival
    shrine's). Past it the land rises rather than drowns. The Hollows ramp in over their first 45 blocks: the ground
    stands four and a half blocks higher, with crests along a ridged noise up to six more, and craggy 3D noise up to
    seven blocks either way gives it overhangs and broken edges. The Abyss ramps in over its first 45: on top of the
    Hollows' rise, sharp ridgelines up to fourteen blocks, the old narrow spires, and deep trenches cut where the base
    noise is low, ten blocks down, black water between black crags; its 3D noise is rougher still.
    """
    t = depth_t()
    h = d_add(60.5, d_mul(16.0, d_noise("minecraft:surface", 0.45)))
    h = d_add(h, d_mul(3.0, d_noise("minecraft:surface_secondary", 1.2)))
    origin = d_clamp(d_add(1.0, d_mul(-(ABYSS_R / 46.0) ** 2, t)), 0.0, 1.0)
    h = d_add(h, d_mul(14.0, origin))
    hollows = d_clamp(d_mul(1.0 / HOLLOWS_RAMP, d_add(t, -HOLLOWS_T)), 0.0, 1.0)
    abyss = d_clamp(d_mul(1.0 / ABYSS_RAMP, d_add(t, -1.0)), 0.0, 1.0)
    h = d_add(h, d_mul(4.5, hollows))
    h = d_add(h, d_mul(d_mul(10.0, hollows), d_clamp(d_add(ridge("minecraft:ridge", 1.4), -0.4), 0.0, 0.6)))
    h = d_add(h, d_mul(abyss, d_mul(28.0, d_clamp(d_add(ridge("minecraft:surface_secondary", 0.9), -0.5), 0.0, 0.5))))
    h = d_add(h, d_mul(d_mul(-20.0, abyss), d_clamp(d_add(-0.05, d_mul(-1.0, d_noise("minecraft:surface", 0.45))), 0.0, 0.5)))
    spires_at = d_mul(3.33, d_clamp(d_add(t, -0.9), 0.0, 0.3))
    spires = d_mul(60.0, d_clamp(d_add(d_noise("minecraft:surface_secondary", 3.0), -0.5), 0.0, 0.35))
    h = d_add(h, d_mul(spires_at, spires))
    return OrderedDict([("type", "minecraft:cache"), ("input", h)]), hollows, abyss


def crags(hollows, abyss):
    """3D roughness, out past the Shore only: up to 7 blocks of it in the Hollows, 11 in the Abyss."""
    strength = d_add(d_mul(7.0, hollows), d_mul(4.0, abyss))
    noise = d_add(d_noise("minecraft:cave_cheese", 1.6, 2.2), d_mul(0.5, d_noise("minecraft:surface_secondary", 4.0, 4.0)))
    return OrderedDict([("type", "minecraft:interpolated"), ("cell_size_xz", 4), ("cell_size_y", 4), ("input", d_mul(strength, noise))])


def material_rule():
    def block(b):
        return OrderedDict([("type", "minecraft:block"), ("result_state", b)])

    def when(cond, then):
        return OrderedDict([("type", "minecraft:condition"), ("if_true", cond), ("then_run", then)])

    def biome(*names):
        return OrderedDict([("type", "minecraft:biome"), ("biome_is", [f"tomyen:{n}" for n in names])])

    def patches(noise, lo, hi=1.0):
        return OrderedDict([("type", "minecraft:noise_threshold"), ("max_threshold", hi), ("min_threshold", lo), ("noise", noise)])

    # The Hollows go darker underfoot, more moss than ash; the Abyss is soul soil and moss, and its cliffs (anything
    # steep) bare black basalt.
    top = OrderedDict([("type", "minecraft:sequence"), ("sequence", [
        when(biome("isle_abyss"), when({"type": "minecraft:steep"}, block("minecraft:smooth_basalt"))),
        when(biome("isle_hollows"), when(patches("minecraft:surface", -0.05), block("tomyen:soul_moss"))),
        when(biome("isle_abyss"), when(patches("minecraft:surface", 0.05), block("minecraft:soul_soil"))),
        when(biome("isle_abyss"), when(patches("minecraft:patch", 0.2), block("tomyen:soul_moss"))),
        block("tomyen:ash_soil")])])
    under = OrderedDict([("type", "minecraft:sequence"), ("sequence", [
        when(biome("isle_abyss"), when({"type": "minecraft:steep"}, block("minecraft:smooth_basalt"))),
        block("tomyen:ash_soil")])])
    return OrderedDict([("type", "minecraft:sequence"), ("sequence", [
        "minecraft:bedrock_floor",
        when("minecraft:on_floor", top),
        when("minecraft:under_floor", under)])])


def noise_settings():
    y = OrderedDict([("type", "minecraft:gradient"), ("axis", "y"), ("from_coordinate", 0), ("to_coordinate", 256),
                     ("from_value", 0.0), ("to_value", 256.0)])
    t = d_clamp(depth_t(), 0.0, 2.0)
    height, hollows, abyss = surface_height()
    final_density = d_add(OrderedDict([("type", "minecraft:sub"), ("left", height), ("right", y)]), crags(hollows, abyss))
    return OrderedDict([
        ("default_block", "tomyen:ashen_stone"), ("default_fluid", "minecraft:water"), ("disable_mob_generation", True),
        ("legacy_random_source", False), ("material_rule", material_rule()),
        ("noise", OrderedDict([("height", 256), ("min_y", 0)])),
        ("noise_router", OrderedDict([
            ("chunk_surface_level", 0.0), ("continents", 0.0), ("depth", 0.0), ("erosion", 0.0),
            ("final_density", final_density),
            ("ridges", 0.0), ("temperature", t), ("vegetation", d_noise("minecraft:vegetation", 1.5))])),
        ("sea_level", 63), ("spawn_target", [])])


def dimension():
    def point(name, lo, hi):
        p = OrderedDict([("temperature", [lo, hi]), ("humidity", 0.0), ("continentalness", 0.0), ("erosion", 0.0), ("depth", 0.0),
                         ("weirdness", 0.0), ("offset", 0.0)])
        return OrderedDict([("biome", f"tomyen:{name}"), ("parameters", p)])
    return OrderedDict([("type", "tomyen:isle"), ("generator", OrderedDict([
        ("type", "minecraft:noise"),
        ("biome_source", OrderedDict([("type", "minecraft:multi_noise"), ("biomes", [
            point("isle_shore", -1.0, HOLLOWS_T), point("isle_hollows", HOLLOWS_T, 1.0), point("isle_abyss", 1.0, 2.0)])])),
        ("settings", "tomyen:isle")]))])


def dimension_type():
    return OrderedDict([
        ("ambient_light", 0.06),
        ("attributes", OrderedDict([
            ("minecraft:audio/ambient_sounds", OrderedDict([
                ("additions", OrderedDict([("sound", "tomyen:isle_additions"), ("tick_chance", 0.004)])), ("loop", "tomyen:isle_loop")])),
            ("minecraft:audio/background_music", OrderedDict([("default", OrderedDict([
                ("max_delay", 12000), ("min_delay", 4000), ("replace_current_music", True), ("sound", "tomyen:music.isle")]))])),
            ("minecraft:gameplay/bed_rule", OrderedDict([("can_set_spawn", "never"), ("can_sleep", "never"),
                                                         ("error_message", {"translate": "block.tomyen.bed.isle"})])),
            ("minecraft:gameplay/respawn_anchor_works", False),
            ("minecraft:gameplay/sky_light_level", 0.4),
            ("minecraft:visual/ambient_light_color", "#1c2a30"),
            ("minecraft:visual/cloud_color", "#40283438"),
            ("minecraft:visual/fog_color", "#2b3439"),
            ("minecraft:visual/moon_angle", 322.0),
            ("minecraft:visual/sky_color", "#1d272c"),
            ("minecraft:visual/sky_light_color", "#8fb8b0"),
            ("minecraft:visual/sky_light_factor", 0.4),
            ("minecraft:visual/star_angle", 40.0),
            ("minecraft:visual/star_brightness", 0.25),
            ("minecraft:visual/sun_angle", 114.0),
            ("minecraft:visual/sunrise_sunset_color", "#9a5e8a72"),
            ("minecraft:visual/water_fog_color", "#070b0d")])),
        ("coordinate_scale", 1.0), ("has_ceiling", False), ("has_ender_dragon_fight", False), ("has_fixed_time", True),
        ("has_skylight", True), ("height", 256), ("infiniburn", "#minecraft:infiniburn_overworld"), ("logical_height", 256),
        ("min_y", 0), ("monster_spawn_block_light_limit", 0), ("monster_spawn_light_level", 0)])


DEPTHS = [
    # name, fog colour, fog start, fog end, sky, water, particles, features (dead trees, headstones, bones, arches, braziers,
    # soul fire vents), soul wisps (weight, fewest, most in a group)
    ("isle_shore", "#2b3439", 10.0, 112.0, "#1d272c", "#141a1d",
     [("minecraft:ash", 0.004), ("minecraft:white_ash", 0.002)], (2, 2, 3, 14, 12, 0), (6, 1, 2)),
    ("isle_hollows", "#1b2529", 4.0, 72.0, "#131b1f", "#0e1315",
     [("minecraft:ash", 0.006), ("minecraft:white_ash", 0.002), ("minecraft:soul", 0.0006)], (3, 3, 2, 9, 7, 0), (10, 1, 3)),
    ("isle_abyss", "#0c1214", 0.0, 44.0, "#080c0e", "#06090a",
     [("minecraft:ash", 0.008), ("minecraft:soul", 0.0015)], (2, 4, 1, 6, 5, 2), (8, 1, 2)),
]


def placed(name, feature, rarity=None, count=None):
    placement = []
    if rarity:
        placement.append(OrderedDict([("type", "minecraft:rarity_filter"), ("chance", rarity)]))
    if count:
        placement.append(OrderedDict([("type", "minecraft:count"), ("count", count)]))
    placement += [{"type": "minecraft:in_square"}, OrderedDict([("type", "minecraft:heightmap"), ("heightmap", "WORLD_SURFACE_WG")]),
                  {"type": "minecraft:biome"}]
    write(os.path.join(DATA, "tomyen", "worldgen", "placed_feature", name + ".json"),
          OrderedDict([("feature", feature), ("placement", placement)]))


def write_world():
    root = os.path.join(DATA, "tomyen")
    write(os.path.join(root, "dimension", "isle.json"), dimension())
    write(os.path.join(root, "dimension_type", "isle.json"), dimension_type())
    write(os.path.join(root, "worldgen", "noise_settings", "isle.json"), noise_settings())
    for kind in ("dead_tree", "headstones", "bones", "arch", "brazier", "soul_fire"):
        write(os.path.join(root, "worldgen", "feature", f"isle_{kind}.json"),
              OrderedDict([("type", "tomyen:isle_ruin"), ("kind", kind)]))
    for name, fog, start, end, sky, water, particles, (trees, stones, bones, arch, brazier, vents), wisps in DEPTHS:
        depth = name[5:]
        placed(f"isle_dead_trees_{depth}", "tomyen:isle_dead_tree", count=trees)
        placed(f"isle_headstones_{depth}", "tomyen:isle_headstones", count=stones)
        placed(f"isle_bones_{depth}", "tomyen:isle_bones", rarity=bones)
        placed(f"isle_arch_{depth}", "tomyen:isle_arch", rarity=arch)
        placed(f"isle_brazier_{depth}", "tomyen:isle_brazier", rarity=brazier)
        features = [[] for _ in range(11)]
        features[4] = [f"tomyen:isle_arch_{depth}", f"tomyen:isle_brazier_{depth}"]
        features[9] = [f"tomyen:isle_dead_trees_{depth}", f"tomyen:isle_headstones_{depth}", f"tomyen:isle_bones_{depth}",
                       "minecraft:patch_dead_bush"]
        if vents:
            placed(f"isle_soul_fire_{depth}", "tomyen:isle_soul_fire", count=vents)
            features[9].append(f"tomyen:isle_soul_fire_{depth}")
        weight, fewest, most = wisps
        spawns = OrderedDict([("argument", OrderedDict([("spawn_costs", {}), ("spawns_by_category", {"ambient": [OrderedDict([
            ("type", "tomyen:soul_wisp"),
            ("count", OrderedDict([("type", "minecraft:uniform"), ("max_inclusive", most), ("min_inclusive", fewest)])),
            ("weight", weight)])]})])), ("modifier", "overlay")])
        biome = OrderedDict([
            ("attributes", OrderedDict([
                ("minecraft:gameplay/natural_mob_spawns", spawns),
                ("minecraft:visual/ambient_particles", OrderedDict([
                    ("argument", [OrderedDict([("particle", {"type": p}), ("probability", pr)]) for p, pr in particles]),
                    ("modifier", "append")])),
                ("minecraft:visual/fog_color", fog),
                ("minecraft:visual/fog_end_distance", end),
                ("minecraft:visual/fog_start_distance", start),
                ("minecraft:visual/sky_color", sky),
                ("minecraft:visual/water_fog_color", water)])),
            ("carvers", []), ("downfall", 0.0),
            ("effects", OrderedDict([("foliage_color", "#5e6660"), ("grass_color", "#646b63"), ("water_color", water)])),
            ("features", features), ("has_precipitation", False), ("temperature", 0.3)])
        write(os.path.join(root, "worldgen", "biome", name + ".json"), biome)

    # Advancements: a tab of their own.
    adv = os.path.join(root, "advancement", "isle")

    def display(key, icon, frame=None, toast=True, background=None):
        d = OrderedDict()
        if background:
            d["background"] = background
        d["description"] = {"translate": f"advancements.tomyen.isle.{key}.description"}
        if frame:
            d["frame"] = frame
        d["icon"] = {"id": icon}
        if not toast:
            d["show_toast"] = False
            d["announce_to_chat"] = False
        d["title"] = {"translate": f"advancements.tomyen.isle.{key}.title"}
        return d

    write(os.path.join(adv, "root.json"), OrderedDict([
        ("criteria", {"entered_isle": OrderedDict([("conditions", {"to": "tomyen:isle"}), ("trigger", "minecraft:changed_dimension")])}),
        ("display", display("root", "tomyen:grave_stone_bricks", background="tomyen:gui/advancements/backgrounds/isle")),
        ("requirements", [["entered_isle"]])]))
    write(os.path.join(adv, "revenant.json"), OrderedDict([
        ("parent", "tomyen:isle/root"),
        ("criteria", {"killed_revenant": OrderedDict([("conditions", {"entity": OrderedDict([
            ("type", "minecraft:entity_properties"), ("entity", "this"), ("predicate", {"minecraft:entity_type": "tomyen:revenant"})])}),
            ("trigger", "minecraft:player_killed_entity")])}),
        ("display", display("revenant", "minecraft:skeleton_skull", frame="goal")),
        ("requirements", [["killed_revenant"]])]))
    write(os.path.join(adv, "abyss.json"), OrderedDict([
        ("parent", "tomyen:isle/root"),
        ("criteria", {"reached_abyss": OrderedDict([("conditions", {"player": OrderedDict([
            ("type", "minecraft:entity_properties"), ("entity", "this"),
            ("predicate", {"minecraft:location": {"biomes": "tomyen:isle_abyss"}})])}), ("trigger", "minecraft:location")])}),
        ("display", display("abyss", "minecraft:soul_lantern", frame="challenge")),
        ("requirements", [["reached_abyss"]])]))


# ======================================================================================================= preview


def preview_sheet(path, sprites):
    from preview_herbalism import prepare, scene, camera, FRONT_LEFT, FRONT_RIGHT
    back = camera((0.5, 0.45, 0.8))
    rows = []

    def shot(parts, view, w, h, centre, scale):
        atlases = {}
        els = []
        for ref, offset, turn in parts:
            els += prepare(ASSETS, ref, atlases, offset, turn)
        return (scene(els, atlases, view, w, h, centre, scale), w, h, 1)

    ground = "tomyen:block/ash_soil"
    floor = [(ground, (dx, -16, dz), 0) for dx in (-16, 0, 16) for dz in (-16, 0, 16)]
    rows.append([shot(floor + [("tomyen:block/gravestone", (0, 0, 0), 0)], v, 170, 170, (8, 7, 8), 7.0)
                 for v in (FRONT_LEFT, FRONT_RIGHT, camera((0.0, 0.15, -1.0)), back)]
                + [shot(floor + [("tomyen:block/soul_cache", (0, 0, 0), 0)], v, 170, 170, (8, 6, 8), 7.0) for v in (FRONT_LEFT, back)])
    rows.append([shot(floor + [("tomyen:block/lantern_of_return", (0, 0, 0), 0)], v, 130, 230, (8, 14, 8), 6.0) for v in (FRONT_LEFT, back)]
                + [shot(floor + [("tomyen:block/return_rift", (0, 0, 0), 0)], v, 130, 230, (8, 14, 8), 6.0)
                   for v in (camera((0.0, 0.1, -1.0)), FRONT_LEFT, camera((-1.0, 0.1, 0.0)))]
                + [shot(floor + [("tomyen:block/soul_brazier", (0, 0, 0), 0)], v, 150, 150, (8, 8, 8), 7.0) for v in (FRONT_LEFT, back)])
    rows.append([shot(floor + [(f"tomyen:block/old_headstone_lean{k}", (0, 0, 0), 0)], v, 130, 150, (8, 6, 8), 7.5)
                 for k in range(3) for v in (FRONT_LEFT, camera((-1.0, 0.15, -0.1)))]
                + [shot(floor + [("tomyen:block/bone_pile", (0, 0, 0), 0)], v, 150, 130, (8, 2, 8), 8.0) for v in (FRONT_LEFT, back)])
    cubes = ["ashen_stone", "ash_soil", "soul_moss", "grave_stone_bricks", "cracked_grave_stone_bricks", "mossy_grave_stone_bricks"]
    row = []
    for c in cubes:
        parts = [(f"tomyen:block/{c}", (dx, 0, dz), 0) for dx in (0, 16) for dz in (0, 16)]
        row.append(shot(parts, FRONT_LEFT, 120, 120, (16, 8, 16), 2.6))
    row.append(shot([("tomyen:block/ash_soil", (0, -16, 0), 0), ("tomyen:block/soul_moss_carpet", (0, 0, 0), 0),
                     ("tomyen:block/grave_stone_brick_wall_inventory", (16, -16, 0), 0)], FRONT_LEFT, 120, 120, (16, 0, 8), 3.0))
    rows.append(row)
    flat = []
    for s in sprites:
        k = 4 if s.w <= 18 else 2
        h = min(s.h, s.w)
        flat.append(([c[:3] if c[3] > 0 else None for c in s.px[:s.w * h]], s.w, h, k))
    for i in range(0, len(flat), 11):
        rows.append(flat[i:i + 11])

    gap = 8
    W = max(sum(w * k + gap for _, w, _, k in r) for r in rows) + gap
    heights = [max(h * k for _, _, h, k in r) + gap for r in rows]
    H = sum(heights) + gap
    canvas = [(34, 38, 44, 255)] * (W * H)
    y = gap
    for r, rh in zip(rows, heights):
        x = gap
        for img, w, h, k in r:
            oy = y + (rh - gap - h * k) // 2
            for yy in range(h * k):
                for xx in range(w * k):
                    c = img[(yy // k) * w + xx // k]
                    if c is not None:
                        canvas[(oy + yy) * W + x + xx] = tuple(c[:3]) + (255,)
            x += w * k + gap
        y += rh
    png(path, W, H, canvas)
    print("preview:", path)


# ======================================================================================================= main


def main():
    jar = client_jar()
    print("vanilla textures from", jar)
    sprites = []
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(common_jar()) as c:
        def tex(name):
            w, h, px = read_texture(z.read(f"assets/minecraft/textures/{name}.png"))
            return Sprite(w, h, px)

        def out(folder, name, sprite, mcmeta=None):
            png(os.path.join(ASSETS, "textures", folder, name + ".png"), sprite.w, sprite.h, sprite.px)
            if mcmeta:
                with open(os.path.join(ASSETS, "textures", folder, name + ".png.mcmeta"), "wb") as f:
                    f.write(z.read(f"assets/minecraft/textures/{mcmeta}.png.mcmeta"))
            sprites.append(sprite)

        out("block", "ashen_stone", ashen_stone(tex("block/stone")))
        out("block", "ash_soil", ash_soil(tex("block/coarse_dirt")))
        out("block", "soul_moss", soul_moss(tex("block/moss_block")))
        out("block", "grave_stone_bricks", grave_bricks(tex("block/stone_bricks")))
        out("block", "cracked_grave_stone_bricks", grave_bricks(tex("block/cracked_stone_bricks")))
        out("block", "mossy_grave_stone_bricks", mossy_grave_bricks(tex("block/mossy_stone_bricks")))
        out("block", "gravestone", gravestone(tex("block/polished_andesite")))
        out("block", "gravestone_panel", gravestone_panel(tex("block/polished_andesite")))
        out("block", "old_headstone", old_headstone(tex("block/polished_andesite")))
        out("block", "isle_lantern", isle_lantern(tex("block/soul_lantern")), "block/soul_lantern")
        out("block", "isle_iron", isle_iron(tex("block/anvil")))
        out("block", "soul_coals", soul_coals(tex("block/soul_soil")))
        out("block", "soul_glow", soul_glow(tex("block/glowstone")))
        out("block", "bone_pile", bone_pile(tex("block/bone_block_side"), tex("block/bone_block_top")))
        out("block", "return_rift", return_rift(tex("block/nether_portal")), "block/nether_portal")
        out("entity/revenant", "revenant_eyes", revenant_eyes())
        out("entity/revenant", "revenant_aura", revenant_aura(tex("entity/creeper/creeper_armor")))
        out("mob_effect", "soul_ward", soul_ward(tex("mob_effect/resistance")))
        bricks = Sprite(16, 16, list(sprites[3].px))
        png(os.path.join(ASSETS, "textures", "gui", "advancements", "backgrounds", "isle.png"), 16, 16, bricks.px)
        write_blocks(z, c)
    write_world()
    merge(os.path.join(ASSETS, "sounds.json"), sounds_json())
    lang_merge(LANG)
    preview_sheet(PREVIEW or os.path.join(REPO, "build", "isle_preview.png"), sprites)
    print("isle done:", len(sprites), "textures")


if __name__ == "__main__":
    main()
