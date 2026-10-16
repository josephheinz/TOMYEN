"""Builds the homemade Scavenger-tier guns (Pipe Pistol, Pipe Rifle, Blunderbuss, Junk Repeater) and their ammo: one
shared texture atlas, each gun at rest plus six frames of its moving part (the pistol's striker, the rifle's bolt, the
blunderbuss's ramrod, the repeater's charging lever), chosen by tomyen:gun_animation (1-6, see ShotgunAnimation), a muzzle
flash layer, flat inventory sprites for the Crude Round and Scrap Shell, and the small 3D rounds the client draws in
the hand while loading (and flying out of the port).

Art: scavenged junk built from clearly separate parts in two families of material. Wood (vanilla oak, spruce and dark
oak plank colours) carries a few deliberate grain lines along each part, a lighter top edge, and darker end grain on
cut faces. Metal is shaded as metal: pipes are octagons whose faces step from a bright top through a mid tone to a dark
underside, plates and fittings get a crisp highlight line along their top edge, and each gun mixes galvanised pipe,
blued or dark iron, copper or brass, with rust on the joints of the crudest. No noise anywhere: every texel is placed.

Every swatch's UV cut starts at its top-left corner, so a part's top edge always gets the swatch's first row (the
highlight) and grain or wrap lines land in the same places on every face. check_overlaps() looks for z-fighting in
world space (after element rotations), which catches coplanar faces of parts rotated about different pivots too.

Model space is in pixels: the bore runs along z at x = 8 with the muzzle toward -z, +x is the gun's right. GunDisplay,
GunHands and ShotgunAnimation mirror the display transforms, grip points and part positions printed at the end; the
redesign kept every one of them, so client/shoddy/ShoddyGunClient.java needs no change.

Usage: python3 tools/gen_shoddy.py src/main/resources/assets/tomyen [--preview out.png]"""
import math, os, sys

sys.path.insert(0, os.path.dirname(__file__))
from gunkit import (Kit, check, move, rotate, rx, write, model, ref, range_dispatch, display, display_matrix, placed, centred,
                    bounds, bounds_centre, flash_models, with_flash, sprite, png, element_rotation, mul, apply, ry, IDENTITY)

ARGS = sys.argv[1:]
PREVIEW = None
if "--preview" in ARGS:
    i = ARGS.index("--preview")
    PREVIEW = ARGS[i + 1]
    del ARGS[i:i + 2]
ASSETS = ARGS[0]
TEX = os.path.join(ASSETS, "textures")
MODELS, ITEMS = os.path.join(ASSETS, "models/item"), os.path.join(ASSETS, "items")

FRAMES = 6
# Where long guns put the shooting hand in first person (the M4's grip point, item space pixels), and pistols (the Glock's).
FP_RIFLE_GRIP = (-0.022 * 16, 0.119 * 16, 0.156 * 16)
FP_PISTOL_GRIP = (-0.020 * 16, 0.131 * 16, -0.181 * 16)
TP_GRIP_TARGET = (0.0, 1.01 + 0.65 * (4.507 - 8), -1.23 + 0.65 * (12.2 - 8))

# ============================================================================================================ palette

# Metal ramps, brightest to darkest (highlight, light, mid, low, dark), from the vanilla textures named.
RAMPS = {
    "pipe": (0xE2E2E2, 0xBDBDBD, 0x9C9C9C, 0x7C7F7A, 0x5E5E5E),     # galvanised pipe: iron_bars, iron_ingot
    "blue": (0x8A93AC, 0x5A6278, 0x495065, 0x3E4453, 0x252C3D),     # blued steel: iron_chain, lantern
    "iron": (0x8E8C8C, 0x676161, 0x595858, 0x494848, 0x343438),     # dark wrought iron: anvil, hopper, cauldron
    "rust": (0xD08A4E, 0xB0602E, 0x8A4129, 0x6D3421, 0x542716),     # rusted fittings: copper_ingot, leather
    "copper": (0xFBA894, 0xE3826C, 0xC26B4C, 0x9A5038, 0x6D3421),   # copper_block, raw_copper_block
    "brass": (0xF2DC8E, 0xD4AE52, 0xB08A3E, 0x8C6A2A, 0x5E4418),    # gold_block and gold_ingot, toned down
    "servo": (0xC4CCD4, 0xA0A8B0, 0x70767E, 0x50555E, 0x3E424A),    # the Skullcrane's own metal (gen_skullcrane.py)
    "rubber": (0x5D5D5D, 0x4A4A4F, 0x3F3E42, 0x343438, 0x2D2D32),   # hopper_outside, darkest end
    "tin": (0xF2F2F2, 0xD6D6D6, 0xB9B9B9, 0x9A9A9A, 0x7A7A7A),      # iron_block
}
# Plank colours, darkest to lightest (vanilla's seven per plank), and how much each is lifted so the side faces, which
# the item lighting darkens to about half, still read as wood next to dark metal.
WOODS = {
    "oak": ((0x67502C, 0x7E6237, 0x967441, 0x9F844D, 0xAF8F55, 0xB8945F, 0xC29D62), 1.0),
    "spruce": ((0x553A1F, 0x5A4424, 0x614B2E, 0x70522E, 0x7A5A34, 0x82613A, 0x886539), 1.0),
    "dark_oak": ((0x291A0C, 0x301E0E, 0x3A2411, 0x3E2912, 0x492F17, 0x4F3218, 0x53381A), 1.35),
}
# Wraps: highlight, body, shadow.
WRAPS = {
    "tape_red": (0xB8342C, 0xA42822, 0x8F2020),     # red_wool
    "tape_black": (0x3A3A42, 0x1C1C20, 0x0E0F14),   # black_wool
    "string": (0xF7F7F7, 0xDBDBDB, 0xA9A9A0),       # string
    "wire": (0xE3826C, 0xB26247, 0x6D3421),         # copper_block
    "spring": (0xE2E2E2, 0x9C9C9C, 0x4E4E4E),       # iron_bars
    "cable": (0xE62008, 0xBD2008, 0x941400),        # redstone_block
}
# Wrap lines across each turn: tape shows each overlap, wire and springs each coil.
WRAP_PERIOD = {"tape_red": (0, 1, 1, 2), "tape_black": (0, 1, 1, 2), "string": (0, 1, 2), "wire": (0, 1, 2),
               "spring": (0, 1, 2), "cable": (1, 1, 1)}
PAPER = 0xD8D0B8
LENS, LENS_HOT = 0xD61A1E, 0xFF8C6E
BLACK = 0x101112
# The ammo keeps its old colours (and the Crude Round and Scrap Shell sprites with it).
PIPE, PIPE_DARK = 0x8F959B, 0x676D73
IRON, IRON_DARK = 0x565A60, 0x3C3F44
TAPE = 0x34363A
COPPER, COPPER_DARK = 0xC07345, 0x8E5233


def lift(hx, k):
    return sum(min(255, int(((hx >> s) & 255) * k)) << s for s in (16, 8, 0))


class Atlas(Kit):
    """The shared atlas: swatches shelf-packed in texels, and UV cuts that start at the swatch's top-left corner (so
    the first row, a highlight, always lands on a face's top edge). Flat colours are cut from inside a 4 x 4 block, so
    mipmapping never bleeds a neighbour in."""

    def __init__(self):
        super().__init__("shoddy", "item/shoddy_guns", seed=4040, size=128)
        self.skyline = [0] * self.size

    def add(self, name, w, h, f, picture=False):
        """Paints swatch name (w x h texels, f(i, j) -> colour) at the lowest free spot (a skyline packer)."""
        assert name not in self.sw, name
        y, x = min((max(self.skyline[x:x + w]), x) for x in range(0, self.size - w + 1, 4))
        assert y + h <= self.size, ("atlas full", name)
        for i in range(x, x + w):
            self.skyline[i] = y + h
        for j in range(h):
            for i in range(w):
                c = f(i, j)
                self.atlas[(y + j) * self.size + x + i] = c if isinstance(c, tuple) else self.rgb(c)
        t = 16.0 / self.size
        self.sw[name] = ((x * t, y * t, (x + w) * t, (y + h) * t), picture)

    def flat(self, name, hx):
        self.add(name, 4, 4, lambda i, j: hx, picture=True)
        (u0, v0, u1, v1), _ = self.sw[name]
        t = 16.0 / self.size
        self.sw[name] = ((u0 + t, v0 + t, u1 - t, v1 - t), True)

    def uv(self, name, du, dv):
        (u0, v0, u1, v1), picture = self.sw[name]
        if picture:
            return [round(u0, 4), round(v0, 4), round(u1, 4), round(v1, 4)]
        t = 16.0 / self.size
        # Two texels per pixel of face, stretched if the face is bigger than the swatch.
        su, sv = min(u1 - u0, max(du * 2 * t, t)), min(v1 - v0, max(dv * 2 * t, t))
        return [round(u0, 4), round(v0, 4), round(u0 + su, 4), round(v0 + sv, 4)]


K = Atlas()
CLEAR = (0, 0, 0, 0)

for ramp, (hi, lt, md, lo, dk) in RAMPS.items():
    for tone, hx in zip(("hi", "lt", "md", "lo", "dk"), (hi, lt, md, lo, dk)):
        K.flat(f"{ramp}_{tone}", hx)
    # Sides: a crisp highlight line along the top edge, then the light and mid tones. Tops: the line along one edge.
    K.add(f"{ramp}_side", 4, 16, lambda i, j, c=(hi, lt, md): c[min(j, 2)])
    K.add(f"{ramp}_top", 16, 4, lambda i, j, c=(hi, lt): c[min(i, 1)])


def grain(base, pattern, jogs):
    """Lines along one axis: pattern[k] is the plank tone at offset k across the grain; each jog (k, a, b) steps line k
    over by one between a and b along the grain, like vanilla's planks."""
    def tone(along, across):
        t = pattern[across]
        for k, a, b in jogs:
            if a <= along < b:
                if across == k:
                    t = pattern[k - 1]
                elif across == k + 1:
                    t = pattern[k]
        return base[t]
    return tone


GRAIN = (6, 5, 4, 2, 4, 4, 3, 4, 4, 5, 2, 4, 4, 4, 3, 4, 4, 2, 4, 5, 4, 3, 4, 4)
GRAIN_JOGS = ((3, 9, 18), (10, 3, 11), (14, 19, 27), (17, 6, 14), (21, 14, 23))
for sp, (planks, k) in WOODS.items():
    base = [lift(c, k) for c in planks]
    # Grain along u (the long faces of parts running along z), its first row the lighter top edge.
    g = grain(base, GRAIN, GRAIN_JOGS)
    K.add(f"{sp}_rows", 32, 24, lambda i, j, g=g: g(i, j))
    # Grain along v (tops of parts along z, sides of upright ones): no edge highlight.
    gv = grain(base, (4,) + GRAIN[1:], GRAIN_JOGS)
    K.add(f"{sp}_cols", 24, 32, lambda i, j, g=gv: g(j, i))

    def end(i, j, b=base):
        if i in (0, 7) or j in (0, 7):
            return b[1]
        d = math.hypot(i - 3.5, j - 3.5)
        return b[2] if 1.4 < d < 2.4 or d < 0.8 else b[3]
    K.add(f"{sp}_end", 8, 8, end, picture=True)

for kind, cols in WRAPS.items():
    period = WRAP_PERIOD[kind]
    K.add(f"{kind}_cols", 16, 4, lambda i, j, c=cols, p=period: c[p[i % len(p)]])
    K.add(f"{kind}_rows", 4, 16, lambda i, j, c=cols, p=period: c[p[j % len(p)]])
    K.flat(f"{kind}_md", cols[1])


def bore_picture(ramp):
    _, lt, _, lo, dk = RAMPS[ramp]

    def f(i, j):
        d = math.hypot(i - 7.5, j - 7.5)
        return BLACK if d < 4.6 else dk if d < 5.6 else lo if d < 6.6 else lt if d < 7.9 else CLEAR
    return f


K.add("bore_pipe", 16, 16, bore_picture("pipe"), picture=True)
K.add("bore_rust", 16, 16, bore_picture("rust"), picture=True)
_, B_LT, B_MD, B_LO, B_DK = RAMPS["brass"]
K.add("bell", 16, 16, lambda i, j: (lambda d: BLACK if d < 4.8 else B_DK if d < 5.8 else B_LO if d < 6.8 else B_MD
                                    if d < 7.9 else CLEAR)(math.hypot(i - 7.5, j - 7.5)), picture=True)


def screw_picture(i, j):
    hi, lt, md, lo, dk = RAMPS["pipe"]
    return ((lt, lt, lt, md), (lt, dk, md, lo), (lt, md, dk, lo), (md, lo, lo, lo))[j][i]


K.add("screw", 4, 4, screw_picture, picture=True)
K.add("led", 4, 4, lambda i, j: LENS_HOT if 1 <= i <= 2 and 1 <= j <= 2 else LENS, picture=True)


def can_side(i, j):
    hi, lt, md, lo, dk = RAMPS["tin"]
    rows = [hi, md, lo, lt, PAPER, PAPER, 0xA42822, 0xA42822, 0x8F2020, PAPER, PAPER, lt, lo, md, lo, dk]
    return rows[j]


def can_lid(i, j):
    hi, lt, md, lo, dk = RAMPS["tin"]
    d = math.hypot(i - 3.5, j - 3.5)
    return lt if d > 3.0 else lo if d > 2.0 else md


K.add("can_side", 8, 16, can_side, picture=True)
K.add("can_lid", 8, 8, can_lid, picture=True)


def rivet_plate(i, j):
    """A copper patch plate, 12 x 12: bevelled edges and four rivets."""
    hi, lt, md, lo, dk = RAMPS["copper"]
    if j == 0:
        return hi
    if j == 11:
        return dk
    if i == 0:
        return lt
    if i == 11:
        return lo
    for cx in (2, 8):
        for cy in (2, 8):
            if (i, j) == (cx, cy):
                return hi
            if (i, j) in ((cx + 1, cy), (cx, cy + 1)):
                return lt
            if (i, j) == (cx + 1, cy + 1):
                return dk
    return md


K.add("plate_copper", 12, 12, rivet_plate, picture=True)


def receiver_side(i, j):
    """The repeater's receiver flank, 36 x 14 (four texels a pixel): a highlight edge, a folded seam two thirds down with
    a rust run below it, three rivets, a dark bottom edge."""
    hi, lt, md, lo, dk = RAMPS["blue"]
    _, r_lt, r_md, r_lo, _ = RAMPS["rust"]
    if j == 0:
        return hi
    if j == 1:
        return lt
    if j == 13:
        return dk
    if j == 9:
        return dk
    if j == 10:
        return lt
    for cx, cy in ((3, 4), (32, 4), (3, 11)):
        if (i, j) == (cx, cy):
            return hi
        if (i, j) in ((cx + 1, cy), (cx, cy + 1)):
            return lt
        if (i, j) == (cx + 1, cy + 1):
            return dk
    # A rust run from the seam, deliberate: widest under the seam, tapering down.
    if 11 <= j <= 12 and 14 + (j - 11) <= i <= 20 - (j - 11):
        return r_md if j == 11 else r_lo
    if j == 11 and i in (13, 21):
        return r_lt
    return md if j < 9 else lo


K.add("receiver_side", 36, 14, receiver_side, picture=True)


def servo_face(i, j):
    """The Skullcrane servo's flank, 18 x 14: grey case, a dark label panel with a brass stripe."""
    hi, lt, md, lo, dk = RAMPS["servo"]
    if j == 0:
        return hi
    if j == 13:
        return dk
    if 3 <= i <= 14 and 4 <= j <= 9:
        if j in (6, 7):
            return RAMPS["brass"][1] if j == 6 else RAMPS["brass"][2]
        return dk if j in (4, 9) or i in (3, 14) else lo
    return lt if j == 1 else md


K.add("servo_face", 18, 14, servo_face, picture=True)
K.flat("black", BLACK)
K.flat("paper", PAPER)
# The ammo's flat colours.
for name, hx in (("pipe", PIPE), ("pipe_dark", PIPE_DARK), ("iron", IRON), ("iron_dark", IRON_DARK), ("tape", TAPE),
                 ("copper", COPPER), ("copper_dark", COPPER_DARK)):
    K.flat(name, hx)
K.save(TEX)

# ============================================================================================================= parts


def tone(ramp, t):
    return f"{ramp}_{t}"


def tube(out, z0, z1, r, ramp, cx=8.0, cy=8.0, caps=(True, True), name=None, cross=False, end=None):
    """A round bar along z, shaded as metal: a regular octagon of four bands (the same construction as Kit.rod) whose
    faces step from the highlight on top through the light upper diagonals and mid sides to the dark underside; or,
    cross=True, a plus of two bands for small round parts. Each band's ends are staggered a hair so caps don't fight."""
    hi, lt, md, lo, dk = (tone(ramp, t) for t in ("hi", "lt", "md", "lo", "dk"))
    end = end or lt
    if cross:
        h = r * 0.6
        bands = [(0, r, h, dict(east=md, west=md, up=lt, down=lo)), (0, h, r, dict(east=md, west=md, up=hi, down=dk))]
    else:
        a = 2 * r * math.tan(math.pi / 8)
        # Faces inside the octagon are left out. Turned +45 about z, a band's east face looks up and to the right.
        bands = [(0, r, a / 2, dict(east=md, west=md, up=None, down=None)),
                 (0, a / 2, r, dict(east=None, west=None, up=hi, down=dk)),
                 (45, r, a / 2, dict(east=lt, west=lo, up=None, down=None)),
                 (-45, r, a / 2, dict(east=lo, west=lt, up=None, down=None))]
    first = None
    for k, (angle, w, h, faces) in enumerate(bands):
        d = 0.01 * k
        e = K.box(out, cx - w, cy - h, z0 + d, cx + w, cy + h, z1 - d, md, name=(name if k == 0 else None),
                  rot=("z", angle, (cx, cy, z0)) if angle else None,
                  north=end if caps[0] else None, south=end if caps[1] else None, **faces)
        first = first or e
    return first


def post(out, y0, y1, r, side, top, bottom, cx=8.0, cz=8.0, name=None):
    """A round upright (a plus of two bands along y): side picture or swatch on all four sides."""
    h = r * 0.55
    K.box(out, cx - r, y0, cz - h, cx + r, y1, cz + h, side, name=name, up=top, down=bottom)
    K.box(out, cx - h, y0 + 0.02, cz - r, cx + h, y1 - 0.02 if top else y1, cz + r, side, up=top, down=bottom)


def metal(out, x0, y0, z0, x1, y1, z1, ramp, rot=None, name=None, **faces):
    """A plate, strap or block of metal: highlight lines along its top edges, the underside dark."""
    f = dict(north=tone(ramp, "side"), south=tone(ramp, "side"), east=tone(ramp, "side"), west=tone(ramp, "side"),
             up=tone(ramp, "top"), down=tone(ramp, "lo"))
    f.update(faces)
    return K.box(out, x0, y0, z0, x1, y1, z1, tone(ramp, "md"), rot=rot, name=name, **f)


def wood(out, x0, y0, z0, x1, y1, z1, sp, rot=None, name=None, upright=False, **faces):
    """A piece of wood with its grain along z (or along y, upright) and end grain on its cut ends."""
    if upright:
        f = dict(north=f"{sp}_cols", south=f"{sp}_cols", east=f"{sp}_cols", west=f"{sp}_cols", up=f"{sp}_end",
                 down=f"{sp}_end")
    else:
        f = dict(north=f"{sp}_end", south=f"{sp}_end", east=f"{sp}_rows", west=f"{sp}_rows", up=f"{sp}_cols",
                 down=f"{sp}_cols")
    f.update(faces)
    return K.box(out, x0, y0, z0, x1, y1, z1, f"{sp}_rows", rot=rot, name=name, **f)


def wrap(out, x0, y0, z0, x1, y1, z1, kind, rot=None, name=None, upright=False, **faces):
    """Tape, string or wire wound round a part along z (or along y, upright): a line across every turn."""
    if upright:
        f = dict(north=f"{kind}_rows", south=f"{kind}_rows", east=f"{kind}_rows", west=f"{kind}_rows",
                 up=f"{kind}_md", down=f"{kind}_md")
    else:
        f = dict(north=f"{kind}_md", south=f"{kind}_md", east=f"{kind}_cols", west=f"{kind}_cols",
                 up=f"{kind}_rows", down=f"{kind}_rows")
    f.update(faces)
    return K.box(out, x0, y0, z0, x1, y1, z1, f"{kind}_md", rot=rot, name=name, **f)


def screw(out, x, y, z, facing, ramp="pipe", s=0.22, h=0.1):
    """A screw or rivet head standing h proud of the surface at (x, y, z), its slotted face toward facing."""
    side = tone(ramp, "md")
    if facing == "east":
        return K.box(out, x, y - s, z - s, x + h, y + s, z + s, side, east="screw", west=None)
    if facing == "west":
        return K.box(out, x - h, y - s, z - s, x, y + s, z + s, side, west="screw", east=None)
    if facing == "south":
        return K.box(out, x - s, y - s, z, x + s, y + s, z + h, side, south="screw", north=None)
    if facing == "up":
        return K.box(out, x - s, y, z - s, x + s, y + h, z + s, side, up="screw", down=None)
    raise ValueError(facing)


def bore(out, ramp, cy, z, r):
    K.sticker(out, 8 - r, cy - r, z - 0.03, 8 + r, cy + r, z - 0.02, f"bore_{ramp}", "north", name="bore")


def nail(out, x, y0, y1, z, name=None, ramp="pipe"):
    return metal(out, x - 0.15, y0, z - 0.15, x + 0.15, y1, z + 0.15, ramp, name=name)


# ========================================================================================================= Pipe Pistol

PP_BY = 10.0
PP_TRAVEL = 2.2
PP_GRIP = (8.0, 6.4, 8.4)
PP_WORK = (8.0, 10.0, 9.8)       # the striker's nail head
PP_PORT = (8.0, 9.0, 8.8)        # the breech cap, pulled back to drop a round in


def pipe_pistol(travel):
    """A stub of galvanised pipe screwed into a rusty beaded coupling, a spruce block for a grip hung under it on red
    tape and string, a bent nail trigger in a copper wire guard, and a blued breech cap carrying the striker: a nail
    through a coil spring, its head the knob you draw back."""
    out = []
    tube(out, -3.0, 4.6, 0.7, "pipe", cy=PP_BY, caps=(True, False), name="barrel")
    bore(out, "pipe", PP_BY, -3.0, 0.66)
    # Front sight: a filed blade on a strap clamped round the muzzle.
    metal(out, 7.2, 9.2, -2.5, 8.8, 10.8, -1.9, "iron", name="sight_clamp")
    metal(out, 7.86, 10.8, -2.45, 8.14, 11.35, -1.95, "blue", name="front_sight")
    # The coupling: a body between two beaded ends, all rust.
    tube(out, 4.75, 7.65, 1.0, "rust", cy=PP_BY, caps=(False, False), name="coupling")
    tube(out, 4.4, 4.75, 1.18, "rust", cy=PP_BY, name="coupling_bead")
    tube(out, 7.65, 8.0, 1.18, "rust", cy=PP_BY, name="coupling_bead")
    # Rear sight: a notch of two blued posts on the back bead.
    for x0 in (7.5, 8.24):
        metal(out, x0, 11.08, 7.7, x0 + 0.26, 11.55, 7.95, "blue", name="rear_sight")
    # The grip, raked back, its parts turned together.
    grip = []
    wood(grip, 7.1, 3.8, 6.2, 8.9, 9.1, 9.3, "spruce", upright=True, name="grip")
    metal(grip, 7.0, 3.5, 6.1, 9.0, 3.8, 9.4, "iron", name="grip_cap")
    wrap(grip, 7.05, 5.6, 6.15, 8.95, 6.15, 9.35, "string", upright=True, name="string_wrap")
    screw(grip, 8.9, 8.2, 7.75, "east")
    screw(grip, 7.1, 8.2, 7.75, "west")
    out += rotate(grip, rx(-14), (8.0, 9.1, 7.7))
    # Red tape binding the grip to the coupling.
    for z in (5.5, 6.75):
        wrap(out, 6.85, 8.3, z, 9.15, 11.12, z + 0.55, "tape_red", name="tape")
    # A bent nail trigger in a copper wire guard.
    nail(out, 8.0, 7.55, 9.05, 5.45, name="trigger")
    metal(out, 7.85, 7.55, 4.9, 8.15, 7.85, 5.3, "pipe", name="trigger_tip")
    metal(out, 7.88, 6.85, 4.3, 8.12, 7.1, 6.55, "copper", name="trigger_guard")
    metal(out, 7.88, 7.1, 4.3, 8.12, 8.95, 4.55, "copper", name="trigger_guard")
    # The striker: the breech cap, a coil spring on the nail through it, and the nail's head to pull back.
    striker = []
    tube(striker, 8.0, 9.0, 0.95, "blue", cy=PP_BY, name="end_cap")
    wrap(striker, 7.62, 9.62, 9.0, 8.38, 10.38, 9.75, "spring", name="spring")
    metal(striker, 7.42, 9.42, 9.75, 8.58, 10.58, 10.1, "blue", name="striker_knob")
    out += move(striker, dz=travel)
    return out


# ========================================================================================================= Pipe Rifle

PR_BY = 10.0
PR_TRAVEL = 3.0
PR_GRIP = (8.0, 7.6, 12.8)
PR_SUPPORT = (8.0, 7.9, -3.0)
PR_WORK = (11.0, 10.0, 9.7)      # the bolt knob
PR_PORT = (8.0, 11.0, 7.6)       # the bolt opening on top
PR_EJECT = (9.6, 10.6, 7.6)


def pipe_rifle(travel):
    """Two lengths of galvanised pipe joined by a rusty coupling, wired to an oak fore-end, a reducer into a blued
    receiver pipe with a slotted bolt and an oak knob, and a carved oak stock: a slim wrist stepping up into the butt,
    an iron tang strap screwed along it, copper wire binding it to the receiver."""
    out = []
    tube(out, -15.5, 2.3, 0.6, "pipe", cy=PR_BY, caps=(True, False), name="barrel")
    bore(out, "pipe", PR_BY, -15.5, 0.57)
    metal(out, 7.3, 9.3, -15.05, 8.7, 10.7, -14.55, "iron", name="sight_clamp")
    metal(out, 7.88, 10.7, -15.0, 8.12, 11.3, -14.6, "blue", name="front_sight")
    tube(out, -10.2, -8.6, 0.84, "rust", cy=PR_BY, name="coupling")
    tube(out, 2.0, 2.9, 0.82, "iron", cy=PR_BY, name="reducer")
    tube(out, 2.9, 11.5, 1.0, "blue", cy=PR_BY, name="receiver")
    tube(out, 11.5, 11.95, 0.62, "iron", cy=PR_BY, cross=True, name="receiver_plug")
    K.sticker(out, 9.0, 9.8, 6.4, 9.03, 10.2, 10.4, "black", "east", name="bolt_slot")
    K.sticker(out, 7.7, 11.0, 6.7, 8.3, 11.03, 8.9, "black", "up", name="loading_port")
    # Rear sight: a bent strip with a notch.
    metal(out, 7.45, 10.95, 3.9, 8.55, 11.15, 4.45, "iron", name="rear_sight")
    for x0 in (7.45, 8.3):
        metal(out, x0, 11.15, 4.05, x0 + 0.25, 11.6, 4.3, "iron", name="rear_sight")
    wood(out, 7.2, 8.0, -7.0, 8.8, 9.5, 2.3, "oak", name="fore_end")
    wood(out, 7.35, 8.15, -7.6, 8.65, 9.45, -7.0, "oak", name="fore_end_tip")
    # Wire ties round the fore-end and barrel, their twisted tails hanging under.
    for z in (-6.2, -0.7):
        wrap(out, 7.05, 7.85, z, 8.95, 10.75, z + 0.65, "wire", name="wire_tie")
        metal(out, 7.88, 7.4, z + 0.2, 8.12, 7.85, z + 0.45, "copper", name="wire_tail")
    # The stock: wrist, butt (taller, so its front shows end grain above and below the wrist), the tang strap.
    stock = []
    wood(stock, 7.25, 6.8, 10.8, 8.75, 9.4, 16.6, "oak", name="wrist")
    wood(stock, 7.12, 4.7, 16.2, 8.88, 9.7, 27.0, "oak", name="butt")
    metal(stock, 7.62, 9.4, 10.2, 8.38, 9.55, 15.2, "iron", name="tang")
    wrap(stock, 7.15, 6.65, 11.5, 8.85, 9.72, 12.1, "wire", name="wire")
    for z in (12.6, 14.4):
        screw(stock, 8.0, 9.55, z, "up")
    out += rotate(stock, rx(6), (8.0, 9.4, 10.8))
    nail(out, 8.0, 7.35, 9.05, 8.7, name="trigger")
    metal(out, 7.78, 6.9, 7.2, 8.22, 7.12, 10.6, "iron", name="trigger_guard")
    metal(out, 7.78, 7.12, 7.2, 8.22, 9.05, 7.42, "iron", name="trigger_guard")
    bolt = []
    metal(bolt, 9.0, 9.75, 9.4, 10.65, 10.25, 10.0, "blue", name="bolt_handle")
    wood(bolt, 10.6, 9.6, 9.3, 11.4, 10.4, 10.1, "oak", name="bolt_knob", east="oak_end", west="oak_end")
    out += move(bolt, dz=travel)
    return out


# ========================================================================================================= Blunderbuss

BB_BY = 9.6
BB_TRAVEL = -5.0                 # the ramrod slides out forward
BB_GRIP = (8.0, 7.6, 9.2)
BB_SUPPORT = (8.0, 6.9, -2.5)
BB_WORK = (8.0, 7.2, -9.6)       # the ramrod's tip
BB_PORT = (8.0, 9.6, -14.6)      # the muzzle: it's loaded from the front


def blunderbuss(travel):
    """A steel barrel thickening at the breech and flaring into a stepped brass bell, set in a dark oak stock with a
    curved wrist and a brass butt plate, nose cap, bands, side plate and trigger guard; a flintlock on the right (plate,
    pan, frizzen and a cocked hammer holding its flint) and an oak ramrod in a brass pipe underneath."""
    out = []
    tube(out, -11.0, 3.6, 0.75, "pipe", cy=BB_BY, caps=(False, False), name="barrel")
    tube(out, 3.4, 6.0, 0.92, "pipe", cy=BB_BY, caps=(True, True), name="breech")
    tube(out, -12.3, -10.9, 1.02, "brass", cy=BB_BY, caps=(False, True), name="bell_neck")
    tube(out, -13.5, -12.3, 1.32, "brass", cy=BB_BY, caps=(False, True), name="bell")
    tube(out, -14.5, -13.5, 1.62, "brass", cy=BB_BY, name="bell_lip")
    K.sticker(out, 8 - 1.58, BB_BY - 1.58, -14.53, 8 + 1.58, BB_BY + 1.58, -14.52, "bell", "north", name="muzzle")
    metal(out, 7.85, 10.3, -10.5, 8.15, 10.62, -10.1, "brass", name="front_sight")
    wood(out, 7.15, 7.6, -8.0, 8.85, 9.2, 6.0, "dark_oak", name="fore_stock")
    metal(out, 7.1, 7.55, -8.4, 8.9, 9.25, -8.0, "brass", name="nose_cap")
    for z in (-6.8, -0.6):
        metal(out, 7.0, 6.8, z, 9.0, 10.45, z + 0.55, "brass", name="band")
    metal(out, 7.7, 6.85, -3.6, 8.3, 7.58, -3.0, "brass", name="ramrod_pipe")
    wrist = []
    wood(wrist, 7.2, 6.0, 5.8, 8.8, 9.6, 12.2, "dark_oak", name="wrist")
    out += rotate(wrist, rx(10), (8.0, 9.6, 5.8))
    butt = []
    wood(butt, 6.95, 3.4, 11.2, 9.05, 8.7, 23.0, "dark_oak", name="butt")
    metal(butt, 6.9, 3.35, 23.0, 9.1, 8.75, 23.35, "brass", name="butt_plate")
    out += rotate(butt, rx(13), (8.0, 8.7, 11.2))
    # The lock: plate and screws, pan, frizzen, and the hammer cocked back with its flint in the jaws.
    metal(out, 8.85, 8.6, 3.55, 9.12, 10.1, 8.2, "blue", name="lock_plate")
    screw(out, 9.12, 9.0, 3.95, "east")
    screw(out, 9.12, 9.0, 7.7, "east")
    metal(out, 9.0, 9.95, 4.3, 9.6, 10.3, 5.3, "brass", name="pan")
    metal(out, 9.1, 10.3, 4.35, 9.5, 11.35, 4.7, "pipe", name="frizzen")
    hammer = []
    metal(hammer, 9.15, 9.8, 6.2, 9.55, 11.6, 6.8, "pipe", name="hammer")
    metal(hammer, 9.12, 11.25, 5.55, 9.58, 11.5, 6.65, "pipe", name="hammer_jaw")
    metal(hammer, 9.17, 11.5, 5.65, 9.53, 11.85, 6.3, "iron", name="flint")
    out += rotate(hammer, rx(-25), (9.35, 9.9, 6.5))
    screw(out, 9.12, 9.95, 6.5, "east", s=0.17)
    metal(out, 6.98, 7.9, 4.0, 7.15, 9.0, 7.6, "brass", name="side_plate")
    nail(out, 8.0, 5.5, 6.6, 7.6, name="trigger", ramp="iron")
    metal(out, 7.8, 4.95, 6.2, 8.2, 5.2, 9.6, "brass", name="trigger_guard")
    metal(out, 7.8, 5.2, 6.2, 8.2, 6.6, 6.45, "brass", name="trigger_guard")
    metal(out, 7.8, 5.2, 9.35, 8.2, 6.2, 9.6, "brass", name="trigger_guard")
    rod = []
    wood(rod, 7.8, 7.0, -9.6, 8.2, 7.4, 5.0, "oak", name="ramrod")
    metal(rod, 7.75, 6.95, -9.9, 8.25, 7.45, -9.6, "brass", name="ramrod_tip")
    out += move(rod, dz=travel)
    return out


# ======================================================================================================= Junk Repeater

JR_BY = 10.2
JR_TRAVEL = 2.6
JR_GRIP = (8.0, 6.3, 10.0)
JR_SUPPORT = (8.0, 9.1, -3.0)
JR_WORK = (8.0, 12.45, 8.7)      # the charging lever's knob
JR_PORT = (8.0, 11.6, 6.2)
JR_EJECT = (9.5, 10.3, 6.5)


def junk_repeater(travel):
    """A galvanised barrel with a rusty coupling for a muzzle, hose-clamped to a spruce fore-end; a receiver cobbled from
    a blued box, a riveted copper patch, a galvanised top plate and a rusty skirt; a red-labelled tin can for a
    magazine; the salvaged Skullcrane servo on the left, its red light on top and a red cable to the grip, driving the
    charging lever; a tape-wrapped grip and a skeleton spruce stock strapped on, with a rubber pad."""
    out = []
    tube(out, -13.4, 2.2, 0.6, "pipe", cy=JR_BY, caps=(False, False), name="barrel")
    tube(out, -14.5, -13.2, 0.84, "rust", cy=JR_BY, name="muzzle_coupling")
    bore(out, "rust", JR_BY, -14.5, 0.8)
    metal(out, 7.88, 10.98, -14.2, 8.12, 11.5, -13.6, "blue", name="front_sight")
    wood(out, 7.2, 8.4, -7.5, 8.8, 9.65, 2.0, "spruce", name="fore_end")
    wood(out, 7.35, 8.55, -8.0, 8.65, 9.55, -7.5, "spruce", name="fore_end_tip")
    for z in (-6.6, -1.4):
        metal(out, 7.1, 8.3, z, 8.9, 10.9, z + 0.5, "pipe", name="hose_clamp")
        metal(out, 8.9, 9.2, z - 0.1, 9.25, 9.9, z + 0.6, "pipe", name="clamp_screw", east="screw")
    # The receiver: a blued box with plates of other metals bolted on.
    metal(out, 6.8, 8.0, 2.0, 9.2, 11.6, 11.0, "blue", name="receiver", east="receiver_side", west="receiver_side")
    metal(out, 9.2, 8.35, 2.3, 9.42, 11.25, 4.9, "copper", name="patch_plate", east="plate_copper")
    metal(out, 7.0, 11.6, 2.3, 9.0, 11.82, 5.4, "pipe", name="top_plate")
    screw(out, 7.45, 11.82, 2.75, "up")
    screw(out, 8.55, 11.82, 4.95, "up")
    metal(out, 6.58, 8.2, 2.4, 6.8, 9.6, 10.6, "rust", name="skirt_plate")
    K.sticker(out, 7.5, 11.6, 5.6, 8.5, 11.62, 7.4, "black", "up", name="loading_port")
    for x0 in (7.1, 8.62):
        metal(out, x0, 11.6, 10.3, x0 + 0.28, 12.1, 10.6, "iron", name="rear_sight")
    for x in (7.25, 8.75):
        screw(out, x, 11.1, 11.0, "south", s=0.18)
    K.sticker(out, 9.2, 9.6, 5.3, 9.23, 10.9, 8.0, "black", "east", name="ejection_port")
    # A tin can for a magazine, and the bent strip that catches it.
    post(out, 4.4, 8.0, 1.0, "can_side", None, "can_lid", cz=4.7, name="magazine")
    metal(out, 7.7, 7.0, 5.7, 8.3, 8.0, 5.95, "iron", name="mag_catch")
    # The Skullcrane servo on the left: its case, mounting ears, output horn, status light and the cable to the grip.
    metal(out, 5.9, 8.6, 4.0, 6.8, 12.1, 8.5, "servo", name="servo", west="servo_face", east=None)
    for z0 in (3.6, 8.5):
        metal(out, 6.15, 9.9, z0, 6.8, 10.7, z0 + 0.4, "servo", name="servo_ear")
        screw(out, 6.15, 10.3, z0 + 0.2, "west", s=0.16)
    metal(out, 5.95, 12.1, 7.3, 6.75, 12.35, 8.7, "brass", name="servo_horn")
    K.box(out, 6.15, 12.1, 4.45, 6.55, 12.45, 4.85, "led", emit=True, name="servo_light", down=None)
    wrap(out, 6.29, 7.75, 6.99, 6.53, 8.6, 7.23, "cable", upright=True, name="cable")
    wrap(out, 6.3, 7.55, 7.0, 6.52, 7.77, 9.3, "cable", name="cable")
    grip = []
    wood(grip, 7.15, 3.9, 8.4, 8.85, 8.0, 10.8, "spruce", upright=True, name="grip")
    wrap(grip, 7.1, 4.5, 8.35, 8.9, 7.5, 10.85, "tape_black", upright=True, name="grip_tape")
    out += rotate(grip, rx(-16), (8.0, 8.0, 9.6))
    nail(out, 8.0, 6.6, 8.05, 7.55, name="trigger")
    metal(out, 7.78, 6.15, 6.1, 8.22, 6.38, 8.7, "iron", name="trigger_guard")
    metal(out, 7.78, 6.38, 6.1, 8.22, 8.05, 6.32, "iron", name="trigger_guard")
    # The skeleton stock: a top rail, an upright butt board, a brace between, a rubber pad.
    stock = []
    wood(stock, 7.25, 9.0, 11.0, 8.75, 10.4, 25.0, "spruce", name="stock_rail")
    wood(stock, 7.2, 5.4, 22.0, 8.8, 9.0, 25.0, "spruce", name="stock_butt", upright=True)
    brace = []
    wood(brace, 7.3, 7.4, 11.5, 8.7, 8.4, 22.6, "spruce", name="stock_brace")
    stock += rotate(brace, rx(12), (8.0, 7.9, 11.5))
    metal(stock, 7.12, 5.3, 25.0, 8.88, 10.5, 25.8, "rubber", name="butt_pad")
    metal(stock, 7.1, 8.85, 11.35, 8.9, 10.55, 11.8, "pipe", name="stock_strap")
    screw(stock, 8.9, 9.7, 11.575, "east", s=0.16)
    out += rotate(stock, rx(5), (8.0, 10.4, 11.0))
    lever = []
    metal(lever, 7.6, 11.6, 7.8, 8.4, 12.2, 9.0, "blue", name="lever")
    metal(lever, 7.4, 12.2, 8.4, 8.6, 12.7, 9.0, "rubber", name="lever_knob")
    metal(lever, 6.75, 11.65, 7.9, 7.65, 11.95, 8.3, "copper", name="servo_link")
    out += move(lever, dz=travel)
    return out


# =========================================================================================================== checks


def face_polygon(e, face):
    """A face's corners in model space, after the element's rotation, and its outward normal."""
    pts = [list(p) for p in corners(e, face)]
    n = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
         "down": (0, -1, 0)}[face]
    r = element_rotation(e)
    if r:
        m, o = r
        pts = [[o[a] + v for a, v in enumerate(apply(m, [p[k] - o[k] for k in range(3)]))] for p in pts]
        n = apply(m, list(n))
    return pts, n


def clip_area(a, b):
    """Area of the overlap of two convex polygons in 2D (Sutherland-Hodgman)."""
    def area(p):
        return 0.5 * sum(p[i][0] * p[(i + 1) % len(p)][1] - p[(i + 1) % len(p)][0] * p[i][1] for i in range(len(p)))
    if area(a) < 0:
        a = a[::-1]
    if area(b) < 0:
        b = b[::-1]
    out = a
    for i in range(len(b)):
        c, d = b[i], b[(i + 1) % len(b)]
        inside = lambda p: (d[0] - c[0]) * (p[1] - c[1]) - (d[1] - c[1]) * (p[0] - c[0]) >= -1e-9
        src, out = out, []
        for j in range(len(src)):
            p, q = src[j], src[(j + 1) % len(src)]
            if inside(q):
                if not inside(p):
                    out.append(cut(p, q, c, d))
                out.append(q)
            elif inside(p):
                out.append(cut(p, q, c, d))
        if not out:
            return 0.0
    return abs(area(out))


def cut(p, q, c, d):
    a1, b1 = q[1] - p[1], p[0] - q[0]
    a2, b2 = d[1] - c[1], c[0] - d[0]
    det = a1 * b2 - a2 * b1
    if abs(det) < 1e-12:
        return q
    c1, c2 = a1 * p[0] + b1 * p[1], a2 * c[0] + b2 * c[1]
    return [(b2 * c1 - b1 * c2) / det, (a1 * c2 - a2 * c1) / det]


def check_overlaps(name, elements):
    """Z-fighting, found in world space: two faces facing the same way in the same plane whose outlines overlap. Unlike
    gunkit's check (which compares only elements sharing an identical rotation), this also catches parts turned the same
    way about different pivots, and turned faces that land on an unturned one's plane."""
    faces = []
    for i, e in enumerate(elements):
        for face in e["faces"]:
            pts, n = face_polygon(e, face)
            faces.append((i, face, pts, n, sum(n[k] * pts[0][k] for k in range(3))))
    hits = []
    for x in range(len(faces)):
        i, fa, pa, na, da = faces[x]
        for y in range(x + 1, len(faces)):
            j, fb, pb, nb, db = faces[y]
            if i == j or sum(na[k] * nb[k] for k in range(3)) < 0.9999 or abs(da - db) > 2e-3:
                continue
            # Project both onto the plane: drop the normal's largest axis.
            ax = max(range(3), key=lambda k: abs(na[k]))
            keep = [k for k in range(3) if k != ax]
            area = clip_area([[p[k] for k in keep] for p in pa], [[p[k] for k in keep] for p in pb])
            if area > 1e-4:
                hits.append((elements[i].get("name", i), fa, elements[j].get("name", j), fb, round(area, 4)))
    if hits:
        for h in hits:
            print("coplanar overlap:", name, h)
        raise SystemExit(name + " has coplanar overlapping faces")


def corners(e, face):
    (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


# ============================================================================================================ displays


def gun_displays(grip, elements, fp_scale, pistol=False):
    fp_rot = [0, -6 if pistol else -8, 0]
    fp = display(fp_rot, placed(fp_rot, fp_scale, grip, FP_PISTOL_GRIP if pistol else FP_RIFLE_GRIP), fp_scale)
    tp = display([0, 0, 0], placed([0, 0, 0], 0.65, grip, TP_GRIP_TARGET), 0.65)
    lo, hi = bounds(elements)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    # Side on in the slot, the longer of length and height filling 15 of its 16 pixels.
    gui_scale = round(15.0 / max(hi[2] - lo[2], hi[1] - lo[1]), 3)
    gui_rot = [0, -90, 0]
    ground = 0.5 if pistol else 0.32
    return {
        "thirdperson_righthand": tp, "thirdperson_lefthand": tp,
        "firstperson_righthand": fp, "firstperson_lefthand": fp,
        "gui": display(gui_rot, centred(gui_rot, gui_scale, c), gui_scale),
        "ground": display([0, 0, 0], centred([0, 0, 0], ground, c, 2.0), ground),
        "fixed": display(gui_rot, centred(gui_rot, gui_scale * 1.3, c), round(gui_scale * 1.3, 3)),
    }


def fp_point(displays, p):
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    c = [v - 8 for v in p]
    return [round((t["translation"][i] + sum(m[i][k] * c[k] for k in range(3)) * s) / 16, 4) for i in range(3)]


SUMMARY = []


def write_gun(name, build, travel, grip, support, fp_scale, flash_at, pistol=False):
    frames = [build(travel * k / FRAMES) for k in range(FRAMES + 1)]
    every = [e for f in frames for e in f]
    displays = gun_displays(grip, every, fp_scale, pistol)
    for i, elements in enumerate(frames):
        check(f"{name}_f{i}", elements)
        check_overlaps(f"{name}_f{i}", elements)
        write(os.path.join(MODELS, name + (f"_f{i}" if i else "") + ".json"), model(K, elements, displays))
    flashes = flash_models(MODELS, name, flash_at[0], flash_at[1], displays)
    body = range_dispatch("tomyen:gun_animation",
                          [(k - 0.5, ref(f"{name}_f{k}")) for k in range(1, FRAMES + 1)] + [(FRAMES + 0.5, ref(name))], ref(name))
    write(os.path.join(ITEMS, name + ".json"), {"model": with_flash(body, flashes)})
    t = displays["firstperson_righthand"]
    line = (f"{name}: {len(frames[0])} elements; firstperson {t['translation']} rot {t['rotation'][1]} scale {fp_scale}; "
            f"grip {fp_point(displays, grip)}" + (f" support {fp_point(displays, support)}" if support else "")
            + f"; gui scale {displays['gui']['scale'][0]}")
    print(line)
    SUMMARY.append((name, frames[0], frames[FRAMES], displays))


write_gun("pipe_pistol", pipe_pistol, PP_TRAVEL, PP_GRIP, None, 0.65, (-3.0, PP_BY), pistol=True)
write_gun("pipe_rifle", pipe_rifle, PR_TRAVEL, PR_GRIP, PR_SUPPORT, 0.55, (-15.5, PR_BY))
write_gun("blunderbuss", blunderbuss, BB_TRAVEL, BB_GRIP, BB_SUPPORT, 0.6, (-14.5, BB_BY))
write_gun("junk_repeater", junk_repeater, JR_TRAVEL, JR_GRIP, JR_SUPPORT, 0.55, (-14.5, JR_BY))

# =============================================================================================================== ammo

ROUND_DISPLAYS = {
    "gui": display([0, -90, 45], centred([0, -90, 45], 2.6, (8, 8, 8)), 2.6),
    "ground": display([0, 0, 0], centred([0, 0, 0], 1.0, (8, 8, 8), 2.0), 1.0),
    "fixed": display([0, -90, 45], centred([0, -90, 45], 2.6, (8, 8, 8)), 2.6),
    "thirdperson_righthand": display([0, 90, -30], centred([0, 90, -30], 1.0, (8, 8, 8), 2.0), 1.0),
    "firstperson_righthand": display([0, 90, -30], centred([0, 90, -30], 1.0, (8, 8, 8), 3.0), 1.0),
}


def crude_round(spent=False):
    """A paper case with an iron nugget slug in its nose, lying along z, centred on (8, 8, 8)."""
    out = []
    K.rod(out, 7.0, 9.2, 0.3, "paper", cap="paper" if not spent else "black", caps=(spent, True), name="case")
    if not spent:
        K.rod(out, 6.6, 7.0, 0.26, "iron", cap="iron_dark", caps=(True, False), name="slug")
    return out


def scrap_shell(spent=False):
    out = []
    K.rod(out, 6.7, 9.3, 0.42, "paper", cap="tape", name="hull")
    K.rod(out, 9.3, 9.6, 0.44, "copper", cap="copper_dark", caps=(False, True), name="head")
    return out


def handful(colour, dark):
    """A pinch of loose shot: three small lumps."""
    out = []
    for (x, y, z, s, m) in ((7.4, 7.6, 7.6, 0.55, colour), (8.2, 7.7, 8.3, 0.5, dark), (8.0, 8.3, 7.5, 0.45, colour)):
        K.box(out, x, y, z, x + s, y + s, z + s, m)
    return out


for name, elements in (("crude_round_3d", crude_round()), ("crude_round_spent", crude_round(True)),
                       ("scrap_shell_3d", scrap_shell()), ("gravel_3d", handful("pipe", "pipe_dark")),
                       ("flint_3d", handful("iron_dark", "iron"))):
    check(name, elements)
    write(os.path.join(MODELS, name + ".json"), model(K, elements, ROUND_DISPLAYS))
    write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


def rgba(hx):
    return ((hx >> 16) & 255, (hx >> 8) & 255, hx & 255, 255)


def sprites():
    pal = {"p": rgba(PAPER), "P": rgba(0xEFE8D6), "d": rgba(0x9A9078), "s": rgba(0x7A7F86), "S": rgba(0xA3A8AE),
           "o": rgba(IRON_DARK), "c": rgba(COPPER), "C": rgba(0xDE9461), "q": rgba(COPPER_DARK), "t": rgba(TAPE),
           "n": rgba(PIPE)}
    # A stubby paper cartridge with a grey nugget slug in its nose, on the diagonal like the 12 gauge shells.
    crude = [
        "................",
        "................",
        "................",
        "..........oso...",
        ".........oSsso..",
        "........oSsso...",
        ".......dPppd....",
        "......dPppd.....",
        ".....dPppd......",
        "....dPppd.......",
        "...dPppd........",
        "...dppd.........",
        "....dd..........",
        "................",
        "................",
        "................",
    ]
    # The 12 gauge shell's shape in paper and copper, its taped crimp showing the shot.
    shell = [
        "................",
        "................",
        "...........ttn..",
        "..........tPpnt.",
        ".........dPppt..",
        "........dPppd...",
        ".......dPppd....",
        "......dPppd.....",
        ".....dPppd......",
        "....qcCcd.......",
        "...qcCcq........",
        "..qcCcq.........",
        "..qccq..........",
        "...qq...........",
        "................",
        "................",
    ]
    for name, rows in (("crude_round", crude), ("scrap_shell", shell)):
        sprite(os.path.join(TEX, f"item/{name}.png"), rows, pal)
        write(os.path.join(MODELS, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "tomyen:item/" + name}})
        write(os.path.join(ITEMS, name + ".json"), {"model": ref(name)})


sprites()


# ============================================================================================================ preview

# Vanilla's item lighting: two directional lights and an ambient floor.
LIGHTS = [[v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (0.2, 1.0, -0.7)],
          [v / math.sqrt(0.2 ** 2 + 1 + 0.7 ** 2) for v in (-0.2, 1.0, 0.7)]]


def lightness(n):
    return min(1.0, 0.4 + 0.6 * sum(max(0.0, sum(n[k] * l[k] for k in range(3))) for l in LIGHTS))


def render(elements, atlas, size, project, turn, w, h):
    """Rasterises elements: project(p) -> (screen x, screen y, depth, bigger nearer); turn(n) -> the normal in the
    space the lights are in. Texels sampled nearest, alpha-tested like vanilla's cutout items."""
    img = [None] * (w * h)
    depth = [-1e9] * (w * h)
    for e in elements:
        for face, f in e["faces"].items():
            pts, n = face_polygon(e, face)
            shade = 1.0 if e.get("light_emission") else lightness(turn(n))
            sp = [project(p) for p in pts]
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (sp[i] for i in tri)
                ta, tb, tc = (uvs[i] for i in tri)
                area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
                if area <= 1e-9:      # back faces (and edge-on ones) are culled, like in game
                    continue
                xs0, xs1 = max(0, int(min(a[0], b[0], c[0]))), min(w - 1, int(max(a[0], b[0], c[0])) + 1)
                ys0, ys1 = max(0, int(min(a[1], b[1], c[1]))), min(h - 1, int(max(a[1], b[1], c[1])) + 1)
                for py in range(ys0, ys1 + 1):
                    for px in range(xs0, xs1 + 1):
                        qx, qy = px + 0.5, py + 0.5
                        l1 = ((b[0] - qx) * (c[1] - qy) - (b[1] - qy) * (c[0] - qx)) / area
                        l2 = ((c[0] - qx) * (a[1] - qy) - (c[1] - qy) * (a[0] - qx)) / area
                        l3 = 1 - l1 - l2
                        if l1 < -1e-6 or l2 < -1e-6 or l3 < -1e-6:
                            continue
                        z = l1 * a[2] + l2 * b[2] + l3 * c[2]
                        i = py * w + px
                        if z <= depth[i]:
                            continue
                        tu = l1 * ta[0] + l2 * tb[0] + l3 * tc[0]
                        tv = l1 * ta[1] + l2 * tb[1] + l3 * tc[1]
                        tx = min(size - 1, max(0, int(tu / 16 * size)))
                        ty = min(size - 1, max(0, int(tv / 16 * size)))
                        col = atlas[ty * size + tx]
                        if col[3] < 128:
                            continue
                        depth[i] = z
                        img[i] = tuple(int(col[k] * shade) for k in range(3))
    return img


def ortho(view, scale, w, h, centre):
    def project(p):
        v = apply(view, [p[k] - centre[k] for k in range(3)])
        return (w / 2 + v[0] * scale, h / 2 - v[1] * scale, v[2])
    return project


def first_person(displays, w, h, fov=70.0):
    """Vanilla's resting right-hand item: the arm offset (0.56, -0.52, -0.72) blocks from the eye, then the model's
    first-person display transform; seen through a perspective camera looking down -z."""
    t = displays["firstperson_righthand"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    f = (h / 2) / math.tan(math.radians(fov / 2))

    def project(p):
        q = apply(m, [s * (p[k] / 16 - 0.5) for k in range(3)])
        x, y, z = (q[k] + t["translation"][k] / 16 + (0.56, -0.52, -0.72)[k] for k in range(3))
        return (w / 2 + x / -z * f, h / 2 - y / -z * f, z)
    return project, lambda n: apply(m, n)


def gui_icon(displays, px):
    """The slot (16 GUI pixels) at px screen pixels: the gui display transform, seen straight on."""
    t = displays["gui"]
    m = display_matrix(t["rotation"])
    s = t["scale"][0]
    k = px / 16.0

    def project(p):
        v = apply(m, [s * (p[a] - 8) for a in range(3)])
        return (px / 2 + (v[0] + t["translation"][0]) * k, px / 2 - (v[1] + t["translation"][1]) * k, v[2])
    return project, lambda n: apply(m, n)


def blit(out, ow, img, w, h, ox, oy, k=1):
    for y in range(h * k):
        for x in range(w * k):
            c = img[(y // k) * w + x // k]
            if c is not None:
                out[(oy + y) * ow + ox + x] = c + (255,)


SIDE = [[0, 0, -1], [0, 1, 0], [1, 0, 0]]                 # from the right, muzzle to the right
TILT = [[1, 0, 0], [0, math.cos(-0.35), -math.sin(-0.35)], [0, math.sin(-0.35), math.cos(-0.35)]]
RIGHT_FRONT = mul(mul(TILT, SIDE), ry(-35))
LEFT_REAR = mul(mul(TILT, SIDE), ry(-145))


def gun_views(name, rest, worked, displays, atlas, size, cell_w=400, cell_h=200, fp_w=480, fp_h=270, icon=48):
    """One row: right side at rest, right front with the part worked, left rear (the side first person shows), the
    first-person view, and the inventory icon at GUI scale 3 (doubled)."""
    lo, hi = bounds(rest)
    c = [(lo[a] + hi[a]) / 2 for a in range(3)]
    k = 8 * (2.4 if name == "pipe_pistol" else 1.0)
    cells = []
    for view, elements in ((SIDE, rest), (RIGHT_FRONT, worked), (LEFT_REAR, rest)):
        cells.append((render(elements, atlas, size, ortho(view, k, cell_w, cell_h, c), lambda n: n, cell_w, cell_h), cell_w, cell_h, 1))
    project, turn = first_person(displays, fp_w, fp_h)
    cells.append((render(rest, atlas, size, project, turn, fp_w, fp_h), fp_w, fp_h, 1))
    project, turn = gui_icon(displays, icon)
    ic = render(rest, atlas, size, project, turn, icon, icon)
    cells.append(([(139, 139, 139) if v is None else v for v in ic], icon, icon, 2))
    return cells


def compose(rows, path, gap=10, bg=(58, 60, 66, 255)):
    """rows: lists of (image, w, h, upscale) cells, laid out left to right, rows top to bottom."""
    W = max(sum(w * k + gap for _, w, _, k in row) for row in rows) + gap
    heights = [max(h * k for _, _, h, k in row) + gap for row in rows]
    H = sum(heights) + gap
    out = [bg] * (W * H)
    y = gap
    for row, rh in zip(rows, heights):
        x = gap
        for img, w, h, k in row:
            blit(out, W, img, w, h, x, y + (rh - gap - h * k) // 2, k)
            x += w * k + gap
        y += rh
    png(path, W, H, out)


def preview(path):
    compose([gun_views(name, rest, worked, displays, K.atlas, K.size) for name, rest, worked, displays in SUMMARY], path)
    print("preview:", path)


if PREVIEW:
    preview(PREVIEW)
print("shoddy guns done")
