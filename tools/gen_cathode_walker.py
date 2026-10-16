"""Paints the Cathode Walker, a wood-veneer CRT television strutting about on a pair of bare human legs, and the
things that come with it: its entity texture (UVs matching client/cathode/CathodeWalkerModel) and the glowing screen
frames its renderer picks from by tick (static, its pixel face and blink, colour bars, the white flash and the
shrinking line and dot of a set clicking off), the snow of the Static Burst's screen overlay, the Cathode Tube and
spawn egg items, and the CRT Television block (an animated static front, a dark one switched off). Writes their item
and block models, item definitions and block states.

Everything is flat-shaded from fixed palettes, read from vanilla where there is one to match: the legs are Steve's
skin and his rolled-up trousers, the cabinet jungle planks, the bezel and knobs his shoes' greys, the tube vanilla
glass and copper. The only noise anywhere is the static on the screens, from a fixed seed, so a rerun gives the same
files.

On the data side it writes the Cathode Walker's loot table, the CRT Television's, the Cathode Tube's recipes (with
recipe-book advancements) and the short-circuit damage type water does it, and puts the television in
minecraft:mineable/axe. It also adds the Cathode Walker's sound
events (vanilla sounds, repitched) to sounds.json and its lang entries to en_us.json, after the skullcrane's, leaving
the rest of both files as they are.

With --preview DIR it also renders the model in software (front, side, three-quarter, mid-strut and mid-kick, and
the screen in each state) to DIR/preview_cathode_walker*.png, and a sheet of the legs (front, side, three-quarter
and back, the feet close up, the strut cycle and the kick) to DIR/preview_cathode_legs.png. Before writing anything
it checks the model for faces that would z-fight and texture rects that overlap, and stops if it finds any.

Usage: python3 tools/gen_cathode_walker.py src/main/resources/assets/tomyen [--preview DIR]"""
import json, math, os, random, sys, zipfile
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
ARGS = sys.argv[1:]
PREVIEW = None
if "--preview" in ARGS:
    PREVIEW = ARGS[ARGS.index("--preview") + 1]
    del ARGS[ARGS.index("--preview"):ARGS.index("--preview") + 2]
if not ARGS:
    sys.exit(__doc__.rsplit("Usage: ", 1)[1])
ASSETS = ARGS[0]
sys.argv = sys.argv[:1]
from gen_longswords import client_jar, read_png
from gunkit import png, write

TEX = os.path.join(ASSETS, "textures")
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "tomyen"))
MC_DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data", "minecraft"))
CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h, self.px = w, h, [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x]

    def save(self, rel):
        png(os.path.join(TEX, rel), self.w, self.h, self.px)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def scale(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


# ---------------------------------------------------------------------------------------------- vanilla palettes
JAR = zipfile.ZipFile(client_jar())


def vanilla(name):
    return read_png(JAR.read("assets/minecraft/textures/%s.png" % name))


def steve(x, y):
    w, h, px = STEVE
    return px[y * w + x][:3]


STEVE = vanilla("entity/player/wide/steve")
# Steve's skin, light to dark: his cheek highlight, the face's two mid tones, its shade, the underside of his chin in
# two tones, and the shadow under his nose.
SKIN = [steve(11, 10), steve(10, 10), steve(13, 10), steve(9, 10), steve(16, 2), steve(16, 0), steve(11, 13)]
MOUTH, EYE_WHITE = steve(11, 14), steve(9, 12)


def mix(a, b, t):
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


# Toe tips a little pinker (flushed toward his mouth's red), the undersides of them a step darker; toenails pale, his
# skin halfway to the white of his eye, their free edges paler still.
PINK = mix(SKIN[1], scale(MOUTH, 1.5), 0.55)
PINK_DARK = mix(PINK, SKIN[4], 0.5)
NAIL = mix(SKIN[0], EYE_WHITE, 0.45)
NAIL_TIP = mix(SKIN[0], EYE_WHITE, 0.65)
# His trousers, light to dark, and the faded inside of the denim for where they're rolled up.
TROUSERS = [steve(4, 20), steve(4, 24), steve(5, 24)]
CUFF = mix(TROUSERS[0], EYE_WHITE, 0.22)
# His shoes' greys, for the bezel, the knobs and the screen's surround.
GREYS = [steve(5, 30), steve(4, 30), steve(4, 31), steve(0, 31)]
# Jungle planks, light to dark.
_, _, planks = vanilla("block/jungle_planks")
WOOD = sorted({p[:3] for p in planks}, key=lambda c: -sum(c))
# Vanilla glass and copper for the tube.
_, _, glass = vanilla("block/glass")
GLASS = sorted({p[:3] for p in glass if p[3]}, key=lambda c: -sum(c))
_, _, copper = vanilla("item/copper_ingot")
COPPER = sorted({p[:3] for p in copper if p[3]}, key=lambda c: -sum(c))
CHROME = [(214, 214, 214), (168, 168, 168), (118, 118, 118)]
DARK_GLASS = [(52, 62, 68), (34, 40, 45), (24, 28, 32), (14, 16, 18)]


# ---------------------------------------------------------------------------------------------- the model
# Kept in step with client/cathode/CathodeWalkerModel: (name, parent, pivot, rest rotation, cubes), each cube
# (u, v, w, h, d, x0, y0, z0, x1, y1, z1): where it sits on the texture and its size there, then its corners in its
# part's space. Where the two differ the game grows or shrinks the cube about its centre (a CubeDeformation per axis),
# which is how the skinny legs and the toes get sizes in fractions of a pixel while their textures stay whole texels.
#
# Model space is in pixels, y down, the front toward -z; the ground is y = 24. The legs are thin and long: a hip of
# baggy rolled-up jeans, a thigh 2.5 across, a knobbly knee, a shin 2 across with a calf behind it, and a bare foot of
# heel, instep, outer sole, ball and five toes, each toe its own part so it can curl and splay.
def cube(u, v, size, lo, hi):
    return (u, v) + tuple(size) + tuple(lo) + tuple(hi)


GROUND = 24.0
HIP_Y = 7.0                        # hips at rest: legs of 8 + 7 + 2 reach the ground
THIGH_LEN, SHIN_LEN, SOLE = 8.0, 7.0, 2.0
LEG_X = 3.5
CABINET = cube(0, 0, (16, 12, 11), (-8, -12, -5.5), (8, 0, 5.5))
# The screen bulges a pixel out of the front, its back sunk a fifth of a pixel into the cabinet.
SCREEN = cube(0, 23, (11, 9, 1), (-7, -11, -6.5), (4, -2, -5.3))
ANTENNA = cube(24, 23, (1, 9, 1), (-0.5, -9, -0.5), (0.5, 0, 0.5))
# Thigh part, about the hip: the jeans (their top sunk into the cabinet), the rolled cuff round their hem, the thigh.
JEANS = cube(0, 33, (3, 3, 3), (-1.7, -0.5, -1.7), (1.7, 2.5, 1.7))
JEAN_CUFF = cube(12, 33, (3, 2, 3), (-1.95, 2.0, -1.95), (1.95, 3.4, 1.95))
THIGH = cube(24, 33, (2, 5, 2), (-1.25, 2.9, -1.25), (1.25, 8.1, 1.25))
# Shin part, about the knee: the kneecap (the back of it a hollow behind the thigh), the shin, the calf.
KNEE = cube(32, 33, (3, 3, 3), (-1.35, -1.3, -1.5), (1.35, 1.3, 1.15))
SHIN = cube(44, 33, (2, 6, 2), (-1.0, 1.0, -1.0), (1.0, 7.3, 1.0))
CALF = cube(52, 33, (2, 3, 2), (-1.15, 1.6, -0.6), (1.15, 4.4, 1.45))
# Foot part, about the ankle, sole on the ground at y = 2. x here runs toward the other foot (the big toe's side), so
# the right foot is this one mirrored. The instep stops short of the ground on the inside, the arch; the outer sole
# under it doesn't quite reach it either, so it never shares a plane with the heel or the ball.
HEEL = cube(0, 42, (2, 2, 3), (-1.15, -0.4, -1.2), (1.2, 2.0, 1.5))
INSTEP = cube(10, 42, (3, 1, 3), (-1.3, 0.3, -3.9), (1.35, 1.55, -1.0))
OUTER = cube(22, 42, (1, 1, 3), (-1.0, 1.45, -4.1), (-0.05, 1.9, -1.1))
BALL = cube(30, 42, (3, 1, 2), (-1.65, 0.75, -5.3), (1.7, 2.0, -3.6))
FOOT = [HEEL, INSTEP, OUTER, BALL]
# The toes, big toe first: (u, v, x0, x1, height, length), each hinged at the bottom of its root, sunk 0.2 into the
# ball, a tenth of a pixel off the ground. Each smaller and shorter than the last, with a gap between each.
TOE_Y, TOE_Z = 1.9, -5.1
TOE_SPECS = [(40, 42, 0.85, 1.9, 0.95, 1.7), (46, 42, 0.05, 0.71, 0.75, 1.5), (52, 42, -0.62, -0.04, 0.7, 1.35),
             (58, 42, -1.23, -0.71, 0.62, 1.15), (0, 47, -1.78, -1.32, 0.55, 0.9)]
TOE_PIVOTS = [((x0 + x1) / 2, TOE_Y, TOE_Z) for _, _, x0, x1, _, _ in TOE_SPECS]
TOES = [cube(u, v, (1, 1, 2), (x0 - px, -hgt, -ln), (x1 - px, 0.0, 0.0))
        for (u, v, x0, x1, hgt, ln), (px, _, _) in zip(TOE_SPECS, TOE_PIVOTS)]
ANTENNA_TILT, ANTENNA_SPLAY = -0.2, 0.45


def mirrored(c, flip):
    """A cube with its x flipped when flip is -1."""
    if flip > 0:
        return c
    u, v, w, h, d, x0, y0, z0, x1, y1, z1 = c
    return (u, v, w, h, d, -x1, y0, z0, -x0, y1, z1)


def build_parts():
    parts = [("hips", None, (0, HIP_Y, 0), (0, 0, 0), []),
             ("cabinet", "hips", (0, 0, 0), (0, 0, 0), [CABINET, SCREEN]),
             ("antenna_0", "cabinet", (-2, -12, 2.5), (ANTENNA_TILT, 0, -ANTENNA_SPLAY), [ANTENNA]),
             ("antenna_1", "cabinet", (2, -12, 2.5), (ANTENNA_TILT, 0, ANTENNA_SPLAY), [ANTENNA])]
    for i in (0, 1):
        side = -1 if i == 0 else 1
        flip = -side  # the foot's +x, toward the other leg
        parts += [("leg_%d" % i, "hips", (side * LEG_X, 0, 0), (0, 0, 0), [JEANS, JEAN_CUFF, THIGH]),
                  ("shin_%d" % i, "leg_%d" % i, (0, THIGH_LEN, 0), (0, 0, 0), [KNEE, SHIN, CALF]),
                  ("foot_%d" % i, "shin_%d" % i, (0, SHIN_LEN, 0), (0, 0, 0), [mirrored(c, flip) for c in FOOT])]
        for j, (toe, (px, py, pz)) in enumerate(zip(TOES, TOE_PIVOTS)):
            parts.append(("toe_%d_%d" % (i, j), "foot_%d" % i, (flip * px, py, pz), (0, 0, 0), [mirrored(toe, flip)]))
    return parts


PARTS = build_parts()


def faces(u, v, w, h, d):
    """A cube's faces in the texture as the game lays them out: (u, v, width, height). 'top' and 'bottom' are as seen
    in the world (the model is upside down), their front edge the last row; 'right' (-x, the mob's right) has its
    front edge in the last column, 'left' in the first; 'back' is mirrored."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(canvas, cube, painters):
    """painters: face name -> f(x, y, w, h) -> colour (or None to leave it)."""
    for name, (fu, fv, fw, fh) in faces(*cube[:5]).items():
        f = painters.get(name)
        if f is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = f(x, y, fw, fh)
                if c is not None:
                    canvas.set(fu + x, fv + y, rgba(c) if len(c) == 3 else c)


def flat(c):
    return lambda x, y, w, h: c


# ---------------------------------------------------------------------------------------------- entity texture
def entity_texture():
    tex = Canvas(64, 64)

    # Cabinet, 16 x 12 x 11: jungle wood veneer, lit from above. Two long grain lines run along each face; the edges
    # are bevelled, light along the top and left, dark along the bottom and right. No noise.
    def veneer(grain_rows, base=2, top_light=True):
        def f(x, y, w, h):
            tone = base
            if y in grain_rows:
                tone += 1 + (1 if (x + y) % 7 == 0 else 0)
            if y == 0 and top_light:
                tone = base - 1
            if y == h - 1 or x == w - 1:
                tone = max(tone, base + 2)
            return WOOD[max(0, min(len(WOOD) - 1, tone))]
        return f

    def top(x, y, w, h):
        tone = 1
        if y in (3, 7):
            tone = 2 + (1 if x % 6 == 2 else 0)
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            tone = 2
        return WOOD[tone]

    def back(x, y, w, h):
        # Hardboard, with a lighter frame round it.
        if x in (0, w - 1) or y in (0, h - 1):
            return WOOD[3]
        return WOOD[5] if y % 4 != 1 else WOOD[6]

    def front(x, y, w, h):
        # The screen bulge covers columns 1-11, rows 1-9. The control strip on the right is a darker panel with two
        # knobs; a thin bevel frames the whole face.
        if x == 0 or y == 0:
            return WOOD[1]
        if x == w - 1 or y == h - 1:
            return WOOD[4]
        if 12 <= x <= 14 and 1 <= y <= 10:
            for ky in (2, 5):
                if 13 <= x <= 14 and ky <= y <= ky + 1:
                    return GREYS[0] if (x, y) == (13, ky) else GREYS[2] if (x, y) == (14, ky + 1) else GREYS[1]
            if y == 8 and 12 <= x <= 14:
                return GREYS[3]  # a slot of a speaker under the knobs
            return WOOD[4]
        if y == 10:
            return WOOD[3]  # the shadow under the screen
        return WOOD[2]

    paint(tex, CABINET, {"top": top, "front": front, "right": veneer((4, 8)), "left": veneer((4, 8)),
                         "back": back, "bottom": flat(WOOD[5])})

    # The screen bulge, 11 x 9 x 1: dark glass, a soft highlight curving across its top left, darkened corners so it
    # reads rounded. Its rim is the bezel.
    def glass_face(x, y, w, h):
        if (x in (0, w - 1)) and (y in (0, h - 1)):
            return DARK_GLASS[3]
        r = math.hypot(x - 2.5, y - 1.5)
        if 1.2 <= r < 2.4 and x <= 4 and y <= 3:
            return DARK_GLASS[0]
        edge = min(x, y, w - 1 - x, h - 1 - y)
        return DARK_GLASS[1] if edge >= 1 else DARK_GLASS[2]

    bezel = flat(GREYS[2])
    paint(tex, SCREEN, {"front": glass_face, "top": bezel, "bottom": bezel, "right": bezel, "left": bezel,
                        "back": flat(GREYS[3])})

    # Antennas, 1 x 9 x 1: chrome, a bright ball at the tip, darker where they meet the cabinet.
    def rod(x, y, w, h):
        return CHROME[0] if y == 0 else CHROME[2] if y == h - 1 else CHROME[1]

    paint(tex, ANTENNA, {name: rod for name in ("front", "right", "left", "back")} | {"top": flat(CHROME[0]),
                                                                                      "bottom": flat(CHROME[2])})

    # Legs and feet: Steve's skin, lit from the front and above, each face a short deliberate ramp (no noise); shadow
    # gathers under the cuff, under the knee, in the hollow behind it, under the arch and in the creases at the toes.
    def sk(t):
        return SKIN[max(0, min(len(SKIN) - 1, int(round(t))))]

    def grid(rows):
        """A face painted from a grid of skin tones, row by row."""
        return lambda x, y, w, h: sk(rows[min(y, len(rows) - 1)][min(x, len(rows[0]) - 1)])

    def sides(f):
        """Both side faces from one painter f(c, y, w, h), c counting columns back from the front edge."""
        return {"right": lambda x, y, w, h: f(w - 1 - x, y, w, h), "left": lambda x, y, w, h: f(x, y, w, h)}

    def tone(t):
        return flat(sk(t))

    # Jeans, 3 x 3 x 3: baggy round the skinny thigh, lighter where the light catches the top of them.
    paint(tex, JEANS, {"front": lambda x, y, w, h: TROUSERS[0 if y < 2 else 1],
                       "back": lambda x, y, w, h: TROUSERS[1 if y < 1 else 2], "top": flat(TROUSERS[1]),
                       "bottom": flat(TROUSERS[2]),
                       **sides(lambda c, y, w, h: TROUSERS[(0 if c == 0 else 1) + (1 if y == 2 else 0)])})
    # The rolled cuff, 3 x 2 x 3: the faded inside of the denim, its rolled lip lit, the fold under it in shade.
    paint(tex, JEAN_CUFF, {"front": lambda x, y, w, h: CUFF if y == 0 else scale(CUFF, 0.88),
                      "back": lambda x, y, w, h: scale(CUFF, 0.9 if y == 0 else 0.8),
                      "top": flat(mix(CUFF, EYE_WHITE, 0.12)), "bottom": flat(scale(CUFF, 0.7)),
                      **sides(lambda c, y, w, h: scale(CUFF, (1.0 if y == 0 else 0.88) * (1.0 if c == 0 else 0.94)))})
    # Thigh, 2 x 5 x 2: a highlight down the front; shadowed just under the cuff.
    under_cuff = [2, 1, 0, 0, 0]
    paint(tex, THIGH, {"front": lambda x, y, w, h: sk(2 - (x == 0) + under_cuff[y]),
                       "back": lambda x, y, w, h: sk(4 + under_cuff[y] // 2), "top": tone(3), "bottom": tone(3),
                       **sides(lambda c, y, w, h: sk(3 - (c == 0) + under_cuff[y]))})
    # Knee, 3 x 3 x 3: a bony cap lit in the middle, its underside shaded, the hollow behind it creased.
    paint(tex, KNEE, {"front": grid([[2, 1, 2], [1, 0, 1], [3, 2, 3]]), "back": grid([[4], [5], [4]]),
                      "top": tone(2), "bottom": tone(4),
                      **sides(lambda c, y, w, h: sk(2 + min(c, 1) + (y == 2)))})
    # Shin, 2 x 6 x 2: the shin bone's highlight down the front, shaded under the knee.
    paint(tex, SHIN, {"front": lambda x, y, w, h: sk(2 - (x == 0) + (y == 0)),
                      "back": tone(4), "top": tone(3), "bottom": tone(4),
                      **sides(lambda c, y, w, h: sk(3 + (y == 0)))})
    # Calf, 2 x 3 x 2: the swell lit along its top, falling into shade toward the ankle.
    paint(tex, CALF, {"back": grid([[2], [3], [4]]), "front": tone(4), "top": tone(2), "bottom": tone(5),
                      **sides(lambda c, y, w, h: sk(3 if c == 0 else 2 + y))})
    # Heel, 2 x 2 x 3: the ankle lit, shading down to the ground and toward the arch.
    paint(tex, HEEL, {"back": grid([[3], [4]]), "front": tone(5), "top": tone(3), "bottom": tone(5),
                      **sides(lambda c, y, w, h: sk(2 + y + (c == 0)))})
    # Instep, 3 x 1 x 3: the top of the foot lit along its ridge; the arch under it dark.
    paint(tex, INSTEP, {"top": grid([[2, 1, 2], [1, 0, 1], [2, 1, 2]]), "bottom": tone(5), "front": tone(3),
                        "back": tone(3), **sides(lambda c, y, w, h: sk(3))})
    paint(tex, OUTER, {"top": tone(4), "bottom": tone(5), "front": tone(5), "back": tone(5),
                       **sides(lambda c, y, w, h: sk(4))})
    # Ball, 3 x 1 x 2: its top darkening into the knuckles where the toes join; its front, between the toes, a crease.
    paint(tex, BALL, {"top": grid([[2, 1, 2], [3, 2, 3]]), "front": tone(5), "back": tone(4), "bottom": tone(5),
                      **sides(lambda c, y, w, h: sk(3))})
    # Toes, 1 x 1 x 2 each: a pale nail on the front of the top, pink tips, a darker crease at the root and underneath.
    for n, toe in enumerate(TOES):
        nail = NAIL_TIP if n == 0 else NAIL
        paint(tex, toe, {"top": lambda x, y, w, h, nail=nail: nail if y == h - 1 else SKIN[1], "front": flat(PINK),
                         "back": tone(5), "bottom": lambda x, y, w, h: PINK_DARK if y == h - 1 else SKIN[4],
                         **sides(lambda c, y, w, h: PINK if c == 0 else SKIN[3])})
    return tex


# ---------------------------------------------------------------------------------------------- screen frames
SW, SH = 11, 9
SCREEN_FACE = faces(*SCREEN[:5])["front"]


def corner(x, y):
    return x in (0, SW - 1) and y in (0, SH - 1)


def screen_frame(f):
    """A glow texture: only the screen's front face painted, f(x, y) -> colour or None, its corners left dark."""
    tex = Canvas(64, 64)
    fu, fv, _, _ = SCREEN_FACE
    for y in range(SH):
        for x in range(SW):
            c = None if corner(x, y) else f(x, y)
            if c is not None:
                tex.set(fu + x, fv + y, rgba(c))
    return tex


STATIC_GREYS = [(36, 38, 40), (86, 88, 90), (138, 140, 142), (186, 188, 190), (232, 234, 236)]


def static_frame(seed, w=SW, h=SH, band=True):
    rnd = random.Random(seed)
    roll = rnd.randrange(h)

    def f(x, y):
        level = rnd.choice((0, 0, 1, 1, 2, 2, 3, 4))
        if band and y == roll:
            level = min(4, level + 1)
        return STATIC_GREYS[level]
    return f


PHOSPHOR = (222, 244, 255)
SCANLINE = [(10, 22, 34), (4, 12, 20)]

# The hunting face: square eyes and a wide grin, bright phosphor on a dim blue screen with scanlines. 'X' lit,
# 'x' half lit.
FACE = ["...........",
        "...........",
        "..XX...XX..",
        "..XX...XX..",
        "...........",
        ".X.......X.",
        ".xXXXXXXXx.",
        "...xXXXx...",
        "..........."]
BLINK = ["...........",
         "...........",
         "...........",
         "..XX...XX..",
         "...........",
         ".X.......X.",
         ".xXXXXXXXx.",
         "...xXXXx...",
         "..........."]


def face_frame(rows):
    def f(x, y):
        c = rows[y][x]
        if c == "X":
            return PHOSPHOR
        if c == "x":
            return scale(PHOSPHOR, 0.55)
        return SCANLINE[y % 2]
    return f


# SMPTE-ish colour bars: seven bars across the top, a strip of reversed blues along the bottom.
BAR_WIDTHS = [2, 1, 2, 1, 2, 1, 2]
BARS = [(200, 200, 200), (200, 200, 0), (0, 200, 200), (0, 200, 0), (200, 0, 200), (200, 0, 0), (0, 0, 200)]
LOW = [(0, 0, 200), (16, 16, 16), (200, 0, 200), (16, 16, 16), (0, 200, 200), (16, 16, 16), (200, 200, 200)]


def bars(x, y):
    i, edge = 0, BAR_WIDTHS[0]
    while x >= edge:
        i += 1
        edge += BAR_WIDTHS[i]
    return (LOW if y >= 7 else BARS)[i]


def white(x, y):
    return (255, 255, 255) if 1 <= x <= SW - 2 and 1 <= y <= SH - 2 else (200, 206, 212)


def off_line(x0, x1, dim=1.0):
    """A set switching off: one bright line across the middle row, faintly bleeding into the rows either side."""
    def f(x, y):
        if x0 <= x <= x1 and y == SH // 2:
            return scale((255, 255, 255), dim)
        if x0 <= x <= x1 and abs(y - SH // 2) == 1:
            return scale((70, 76, 82), dim)
        return None
    return f


def off_dot(dim):
    cx, cy = SW // 2, SH // 2

    def f(x, y):
        if (x, y) == (cx, cy):
            return scale((255, 255, 255), dim)
        if abs(x - cx) + abs(y - cy) == 1:
            return scale((80, 86, 92), dim)
        return None
    return f


SCREENS = OrderedDict(
    [("static_%d" % i, static_frame(1000 + i)) for i in range(4)]
    + [("face", face_frame(FACE)), ("face_blink", face_frame(BLINK)), ("bars", bars), ("white", white),
       ("off_line", off_line(0, SW - 1)), ("off_short", off_line(3, SW - 4)), ("off_dot", off_dot(1.0)),
       ("off_fade", off_dot(0.45))])


# ---------------------------------------------------------------------------------------------- overlay snow
def overlay_snow(seed):
    """64 x 64 greyscale snow for the Static Burst's screen overlay, drawn stretched; mostly translucent."""
    rnd = random.Random(seed)
    tex = Canvas(64, 64)
    roll = rnd.randrange(64)
    for y in range(64):
        for x in range(64):
            level = rnd.choice((0, 1, 1, 2, 2, 3, 4))
            if abs(y - roll) <= 2:
                level = min(4, level + 1)
            g = STATIC_GREYS[level][0]
            tex.set(x, y, (g, g, g, 150 + 25 * level))
    return tex


# ---------------------------------------------------------------------------------------------- items
def sprite(rows, palette):
    c = Canvas(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c.set(x, y, rgba(palette[ch]))
    return c


# The Cathode Tube: a glass envelope with its electrodes and a glowing filament inside, on a dark bakelite base with
# copper pins. Glass is vanilla glass's, copper vanilla copper's.
TUBE = ["................",
        "......oooo......",
        ".....oWggho.....",
        "....oWgggggo....",
        "....oWpPPpgo....",
        "....oWPffPgo....",
        "....ogPfFPgo....",
        "....ogPffPgo....",
        "....ogpPPpgo....",
        "....oggggggo....",
        "....bBBBBBBb....",
        "....kcccccck....",
        ".....kkkkkk.....",
        "......n..n......",
        "......N..N......",
        "................"]
TUBE_PALETTE = {"o": GLASS[3], "W": (240, 250, 250), "g": GLASS[1], "h": GLASS[0], "p": CHROME[2], "P": CHROME[1],
                "f": (255, 150, 40), "F": (255, 230, 150), "b": (70, 52, 40), "B": (96, 72, 54), "k": (40, 30, 24),
                "c": COPPER[3], "n": COPPER[2], "N": COPPER[4]}


def spawn_egg():
    """The mod's egg outline (the shopping cart egg's, as every mob here uses), wood-brown with two glowing screen
    spots and a band of trousers."""
    w, h, px = read_png(open(os.path.join(TEX, "item/shopping_cart_spawn_egg.png"), "rb").read())
    mask = [p[3] > 0 for p in px]
    egg = Canvas(w, h)
    xs = [i % w for i in range(w * h) if mask[i]]
    ys = [i // w for i in range(w * h) if mask[i]]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    spots = [(6, 6), (9, 9)]
    for i in range(w * h):
        if not mask[i]:
            continue
        x, y = i % w, i // w
        outline = not all(0 <= x + dx < w and 0 <= y + dy < h and mask[(y + dy) * w + x + dx]
                          for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if outline:
            egg.px[i] = rgba(WOOD[6])
        elif (x, y) in spots:
            egg.px[i] = rgba(PHOSPHOR)
        elif any(abs(x - sx) + abs(y - sy) == 1 for sx, sy in spots):
            egg.px[i] = rgba(STATIC_GREYS[2])
        elif y == round(cy) + 3:
            egg.px[i] = rgba(TROUSERS[0])
        else:
            light = (cx - x) + (cy - y)
            egg.px[i] = rgba(WOOD[0] if light > 5 else WOOD[1] if light > 1 else WOOD[3] if light > -4 else WOOD[4])
    return egg


# ---------------------------------------------------------------------------------------------- block
def tv_front(screen):
    """16 x 16: a wood frame, the screen (10 x 8, from screen(x, y) or dark glass) on the left, two knobs on the
    right."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                col = WOOD[1]
            elif x == 15 or y == 15:
                col = WOOD[4]
            elif 1 <= x <= 12 and 2 <= y <= 11:
                sx, sy = x - 2, y - 3
                if x in (1, 12) or y in (2, 11):
                    col = GREYS[2]  # the bezel
                elif sx in (0, 9) and sy in (0, 7):
                    col = DARK_GLASS[3]
                else:
                    col = screen(sx, sy)
            elif 13 <= x <= 14 and y in (3, 4, 7, 8):
                col = GREYS[0] if (x, y) in ((13, 3), (13, 7)) else GREYS[1]
            elif 13 <= x <= 14 and y == 11:
                col = GREYS[3]
            elif y >= 12:
                col = WOOD[3] if y == 12 else WOOD[2]
            else:
                col = WOOD[2]
            c.set(x, y, rgba(col))
    return c


def tv_side():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            tone = 2
            if y in (5, 10):
                tone = 3 + (1 if x % 7 == 3 else 0)
            if x in (0, 15) or y in (0, 15):
                tone = 4 if (x == 15 or y == 15) else 1
            c.set(x, y, rgba(WOOD[tone]))
    return c


def tv_top():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            tone = 1
            if y in (4, 11):
                tone = 2 + (1 if x % 6 == 2 else 0)
            if x in (0, 15) or y in (0, 15):
                tone = 3
            c.set(x, y, rgba(WOOD[tone]))
    return c


def dark_glass_screen(x, y):
    r = math.hypot(x - 2.0, y - 1.5)
    if 1.0 <= r < 2.2 and x <= 3 and y <= 3:
        return DARK_GLASS[0]
    return DARK_GLASS[1] if min(x, y, 9 - x, 7 - y) >= 1 else DARK_GLASS[2]


# ---------------------------------------------------------------------------------------------- json helpers
MODELS, ITEMS = os.path.join(ASSETS, "models"), os.path.join(ASSETS, "items")


def advancement(name, ingredient, category="misc"):
    write(os.path.join(DATA, "advancement/recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", {"conditions": {"items": [{"items": ingredient}]}, "trigger": "minecraft:inventory_changed"}),
            ("has_the_recipe", {"conditions": {"recipes": "tomyen:" + name}, "trigger": "minecraft:recipe_unlocked"})])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", {"recipes": ["tomyen:" + name]})]))


def shapeless(name, ingredients, result, count=1, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, "tomyen:cathode_tube", category)


def shaped(name, pattern, key, result, count=1, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), OrderedDict([
        ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
        ("result", OrderedDict([("count", count), ("id", result)]))]))
    advancement(name, "tomyen:cathode_tube", category)


def drop(item, low, high, looting=1.0, chance=None):
    pool = OrderedDict([("rolls", 1), ("entries", [{"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
        {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": looting},
         "enchantment": "minecraft:looting"}]}])])
    if chance is not None:
        pool["condition"] = OrderedDict([("type", "minecraft:random_chance_with_enchanted_bonus"),
                                         ("enchanted_chance", {"type": "minecraft:linear", "base": chance + 0.1,
                                                               "per_level_above_first": 0.1}),
                                         ("enchantment", "minecraft:looting"), ("unenchanted_chance", chance)])
    return pool


def tag_add(path, entry):
    values = json.load(open(path))["values"] if os.path.exists(path) else []
    if entry not in values:
        values.append(entry)
    write(path, {"values": values})


def insert_after(path, anchor, entries):
    """Adds entries (key -> value) to a JSON object file just after the anchor key, replacing any already there,
    and leaves the rest of the file as it is."""
    with open(path, encoding="utf-8") as f:
        d = json.load(f, object_pairs_hook=OrderedDict)
    for k in entries:
        d.pop(k, None)
    out = OrderedDict()
    for k, v in d.items():
        out[k] = v
        if k == anchor:
            out.update(entries)
    if anchor not in d:
        out.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(out, indent=2, ensure_ascii=False) + "\n")


# ---------------------------------------------------------------------------------------------- preview renderer
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_vec(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def rot_zyx(x, y, z):
    """As ModelPart applies it: Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(rz, mat_mul(ry, rx))


SHADE = {"top": 1.0, "bottom": 0.5, "front": 0.82, "back": 0.82, "right": 0.62, "left": 0.62}


def face_quads(cube):
    """(face name, origin, u edge, v edge, texture rect) in part space."""
    x0, y0, z0, x1, y1, z1 = cube[5:]
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    rects = faces(*cube[:5])
    return [
        ("front", (x0, y0, z0), (w, 0, 0), (0, h, 0), rects["front"]),
        ("back", (x1, y0, z1), (-w, 0, 0), (0, h, 0), rects["back"]),
        ("right", (x0, y0, z1), (0, 0, -d), (0, h, 0), rects["right"]),
        ("left", (x1, y0, z0), (0, 0, d), (0, h, 0), rects["left"]),
        ("top", (x0, y0, z1), (w, 0, 0), (0, 0, -d), rects["top"]),
        ("bottom", (x0, y1, z1), (w, 0, 0), (0, 0, -d), rects["bottom"]),
    ]


def render(tex, pose, yaw, glow=None, size=(300, 440), px_scale=10, pitch=0.0, target=(0, 5.0, 0), ground=True):
    """Orthographic, looking at the mob's front with yaw turning it and pitch tipping the view down onto it, centred
    on target; pose: part -> (xRot, yRot, zRot, dy). A faint line marks the ground in level views."""
    W, H = size
    img = [(58, 60, 66, 255) if ((x // 20 + y // 20) % 2) else (66, 68, 74, 255) for y in range(H) for x in range(W)]
    depth = [1e9] * (W * H)
    cy_, sy_, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

    def view(p):
        x, z = p[0] * cy_ - p[2] * sy_, p[0] * sy_ + p[2] * cy_
        return (x, p[1] * cp - z * sp, z * cp + p[1] * sp)
    centre = view(target)
    world = {}
    for name, parent, pivot, rest, cubes in PARTS:
        rx, ry, rz, dy = pose.get(name, (0, 0, 0, 0))
        local = rot_zyx(rest[0] + rx, rest[1] + ry, rest[2] + rz)
        p = (pivot[0], pivot[1] + dy, pivot[2])
        if parent:
            pm, po = world[parent]
            m = mat_mul(pm, local)
            o = tuple(po[i] + mat_vec(pm, p)[i] for i in range(3))
        else:
            m, o = local, p
        world[name] = (m, o)
        for layer, source in ((0, tex), (1, glow)):
            if source is None:
                continue
            for cube in cubes:
                for fname, origin, ue, ve, (fu, fv, fw, fh) in face_quads(cube):
                    O = tuple(o[i] + mat_vec(m, origin)[i] for i in range(3))
                    U, V = mat_vec(m, ue), mat_vec(m, ve)
                    O2 = view(O)
                    U2 = tuple(view(tuple(O[i] + U[i] for i in range(3)))[k] - O2[k] for k in range(3))
                    V2 = tuple(view(tuple(O[i] + V[i] for i in range(3)))[k] - O2[k] for k in range(3))
                    det = U2[0] * V2[1] - U2[1] * V2[0]
                    if abs(det) < 1e-6:
                        continue
                    corners = [(O2[0] + a * U2[0] + b * V2[0], O2[1] + a * U2[1] + b * V2[1]) for a in (0, 1) for b in (0, 1)]
                    sx0 = int(math.floor((min(c[0] for c in corners) - centre[0]) * px_scale + W / 2)) - 1
                    sx1 = int(math.ceil((max(c[0] for c in corners) - centre[0]) * px_scale + W / 2)) + 1
                    sy0 = int(math.floor((min(c[1] for c in corners) - centre[1]) * px_scale + H / 2)) - 1
                    sy1 = int(math.ceil((max(c[1] for c in corners) - centre[1]) * px_scale + H / 2)) + 1
                    for sy in range(max(0, sy0), min(H, sy1)):
                        for sx in range(max(0, sx0), min(W, sx1)):
                            mx = (sx + 0.5 - W / 2) / px_scale + centre[0] - O2[0]
                            my = (sy + 0.5 - H / 2) / px_scale + centre[1] - O2[1]
                            a = (mx * V2[1] - my * V2[0]) / det
                            b = (U2[0] * my - U2[1] * mx) / det
                            if not (0 <= a < 1 and 0 <= b < 1):
                                continue
                            z = O2[2] + a * U2[2] + b * V2[2]
                            i = sy * W + sx
                            tx, ty = int(fu + a * fw), int(fv + b * fh)
                            c = source.get(min(63, tx), min(63, ty))
                            if layer == 0:
                                if c[3] == 0 or z >= depth[i]:
                                    continue
                                depth[i] = z
                                s = SHADE[fname] * 0.92
                                img[i] = (int(c[0] * s), int(c[1] * s), int(c[2] * s), 255)
                            elif c[3] and z <= depth[i] + 1e-3:
                                img[i] = tuple(min(255, img[i][k] + c[k]) for k in range(3)) + (255,)
    if ground and pitch == 0:
        gy = int(round((GROUND - centre[1]) * px_scale + H / 2))
        for sx in range(W):
            if 0 <= gy < H and depth[gy * W + sx] == 1e9:
                img[gy * W + sx] = (120, 150, 110, 255)
    return W, H, img


def kick_angles(a):
    """The kicking thigh's and shin's angles, as CathodeWalkerModel.kick."""
    if a < 0.3:
        t = a / 0.3
        return -1.25 * t, 1.7 * t
    if a < 0.55:
        t = (a - 0.3) / 0.25
        return -1.25 - 0.4 * t, 1.7 * (1 - t)
    t = (a - 0.55) / 0.45
    return -1.65 * (1 - t), 0.0


def strike_of(a):
    return math.sin(min(1.0, a / 0.55) * math.pi / 2) * (1.0 if a < 0.55 else 1.0 - (a - 0.55) / 0.45) if a > 0 else 0.0


def reach(thigh, shin, foot, curl):
    """How far below the hip a posed leg's lowest point is: the back of the heel, the front of the ball or the tip of
    the big toe. As CathodeWalkerModel.reach."""
    a2, a3 = thigh + shin, thigh + shin + foot

    def down(a, y, z):
        return y * math.cos(a) - z * math.sin(a)
    toe = down(a3, TOE_Y, TOE_Z) + down(a3 + curl, 0.0, -TOE_SPECS[0][5])
    return (THIGH_LEN * math.cos(thigh) + SHIN_LEN * math.cos(a2)
            + max(down(a3, SOLE, HEEL[10]), down(a3, SOLE, BALL[7]), toe))


def strut_pose(phase, amount=1.0, kick=0.0, lean=0.0):
    """The walk and kick, as CathodeWalkerModel does them (no head turn, no idle): high knees, the heel peeling up
    and the toes bending back on the push-off, the toes clawing in the air and splaying as the foot lands, and the
    hips set so the lower foot stands exactly on the ground."""
    pose, reaches = {}, []
    for i in (0, 1):
        p = phase + i * math.pi
        c, k = math.sin(p), math.cos(p)
        thigh = amount * (-1.3 * c if c > 0 else -0.45 * c)
        shin = amount * (1.55 * max(0.0, c) ** 0.8 + 0.15)
        push = amount * 2 * max(0.0, -c) * max(0.0, k)
        land = amount * 2 * max(0.0, -c) * max(0.0, -k)
        curl = -0.6 * push + 0.3 * amount * max(0.0, c)
        if i == 0 and kick > 0:
            thigh, shin = kick_angles(kick)
            push = land = 0.0
            curl = -0.7 * strike_of(kick)
        foot = -(thigh + shin) * 0.85 + 0.45 * push
        pose["leg_%d" % i] = (thigh, 0, 0, 0)
        pose["shin_%d" % i] = (shin, 0, 0, 0)
        pose["foot_%d" % i] = (foot, 0, 0, 0)
        flip = 1 if i == 0 else -1
        for j, (px, _, _) in enumerate(TOE_PIVOTS):
            pose["toe_%d_%d" % (i, j)] = (curl, -flip * (px - 0.1) * 0.22 * land, 0, 0)
        reaches.append(reach(thigh, shin, foot, curl))
    c = math.sin(phase)
    pose["hips"] = (0, 0, 0, GROUND - max(reaches) - HIP_Y)
    strike = strike_of(kick)
    pose["cabinet"] = (0.12 * lean * amount - 0.3 * strike, 0.15 * amount * c, 0.1 * amount * c, 0)
    return pose


# ---------------------------------------------------------------------------------------------- checks
def world_cubes():
    """Every cube at rest as (part, cube, rotation, its corners in that rotation's frame)."""
    world, out = {}, []
    for name, parent, pivot, rest, cubes in PARTS:
        local = rot_zyx(*rest)
        if parent:
            pm, po = world[parent]
            m = mat_mul(pm, local)
            o = tuple(po[i] + mat_vec(pm, pivot)[i] for i in range(3))
        else:
            m, o = local, pivot
        world[name] = (m, o)
        mt = [[m[j][i] for j in range(3)] for i in range(3)]
        off = mat_vec(mt, o)
        for c in cubes:
            lo = tuple(c[5 + k] + off[k] for k in range(3))
            hi = tuple(c[8 + k] + off[k] for k in range(3))
            out.append((name, c, tuple(round(v, 6) for row in m for v in row), lo, hi))
    return out


def zfight_check(tol=0.06):
    """Faces that would fight at rest: two cubes in the same frame with parallel faces facing the same way closer
    than tol and overlapping (they'd flicker), or facing each other in the same plane and overlapping (touching,
    which the game draws both of). Returns the clashes."""
    cubes, hits = world_cubes(), []
    for a in range(len(cubes)):
        for b in range(a + 1, len(cubes)):
            na, ca, ma, la, ha = cubes[a]
            nb, cb, mb, lb, hb = cubes[b]
            if ma != mb:
                continue
            for axis in range(3):
                others = [k for k in range(3) if k != axis]
                area = 1.0
                for k in others:
                    area *= max(0.0, min(ha[k], hb[k]) - max(la[k], lb[k]))
                if area < 1e-4:
                    continue
                for sa, pa in ((-1, la[axis]), (1, ha[axis])):
                    for sb, pb in ((-1, lb[axis]), (1, hb[axis])):
                        if (sa == sb and abs(pa - pb) < tol) or (sa != sb and abs(pa - pb) < 1e-4):
                            hits.append("%s %s / %s %s: %s%s at %.2f" % (na, ca[:2], nb, cb[:2], "-+"[sa > 0], "xyz"[axis], pa))
    return hits


def uv_check():
    """Cubes whose texture rects overlap each other or run off the 64 x 64 texture."""
    owner, seen, hits = {}, set(), []
    for _, _, _, _, cubes in PARTS:
        for c in cubes:
            if c[:5] in seen:
                continue
            seen.add(c[:5])
            for fu, fv, fw, fh in faces(*c[:5]).values():
                for y in range(fv, fv + fh):
                    for x in range(fu, fu + fw):
                        if not (0 <= x < 64 and 0 <= y < 64):
                            hits.append("%s off the texture" % (c[:2],))
                        elif owner.get((x, y), c[:5]) != c[:5]:
                            hits.append("%s overlaps %s at %d,%d" % (c[:2], owner[(x, y)][:2], x, y))
                        owner[(x, y)] = c[:5]
    return sorted(set(hits))


def save_preview(path, panels, cols):
    pw, ph = panels[0][0], panels[0][1]
    rows = (len(panels) + cols - 1) // cols
    W, H = cols * pw, rows * ph
    out = [(40, 40, 44, 255)] * (W * H)
    for n, (w, h, img) in enumerate(panels):
        ox, oy = (n % cols) * pw, (n // cols) * ph
        for y in range(h):
            out[(oy + y) * W + ox:(oy + y) * W + ox + w] = img[y * w:(y + 1) * w]
    png(path, W, H, out)


def save_rows(path, rows):
    """Rows of panels, each row's panels the same size, stacked."""
    W = max(sum(p[0] for p in row) for row in rows)
    H = sum(row[0][1] for row in rows)
    out, oy = [(40, 40, 44, 255)] * (W * H), 0
    for row in rows:
        ox = 0
        for w, h, img in row:
            for y in range(h):
                out[(oy + y) * W + ox:(oy + y) * W + ox + w] = img[y * w:(y + 1) * w]
            ox += w
        oy += row[0][1]
    png(path, W, H, out)


def blow_up(canvas, k, bg=(58, 60, 66)):
    W, H = canvas.w * k, canvas.h * k
    img = []
    for y in range(H):
        for x in range(W):
            c = canvas.get(x // k, y // k)
            a = c[3] / 255
            img.append(tuple(int(c[i] * a + bg[i] * (1 - a)) for i in range(3)) + (255,))
    return W, H, img


# ---------------------------------------------------------------------------------------------- main
def main():
    clashes = zfight_check() + uv_check()
    if clashes:
        sys.exit("model clashes:\n  " + "\n  ".join(clashes))
    tex = entity_texture()
    tex.save("entity/cathode_walker/cathode_walker.png")
    frames = OrderedDict()
    for name, f in SCREENS.items():
        frames[name] = screen_frame(f)
        frames[name].save("entity/cathode_walker/screen_%s.png" % name)
    for i in range(4):
        overlay_snow(2000 + i).save("misc/tv_static_%d.png" % i)

    tube = sprite(TUBE, TUBE_PALETTE)
    tube.save("item/cathode_tube.png")
    egg = spawn_egg()
    egg.save("item/cathode_walker_spawn_egg.png")

    # The television: an animated static front (four frames, two ticks each), a dark one off.
    anim = Canvas(16, 64)
    for i in range(4):
        snow = static_frame(3000 + i, 10, 8)
        cache = {}

        def screen(x, y, snow=snow, cache=cache):
            if (x, y) not in cache:
                cache[(x, y)] = snow(x, y)
            return cache[(x, y)]
        frame = tv_front(screen)
        for y in range(16):
            for x in range(16):
                anim.set(x, i * 16 + y, frame.get(x, y))
    anim.save("block/crt_television_front_on.png")
    write(os.path.join(TEX, "block/crt_television_front_on.png.mcmeta"), {"animation": {"frametime": 2}})
    tv_off = tv_front(dark_glass_screen)
    tv_off.save("block/crt_television_front.png")
    side, top = tv_side(), tv_top()
    side.save("block/crt_television_side.png")
    top.save("block/crt_television_top.png")

    for state, front_tex in (("", "crt_television_front"), ("_on", "crt_television_front_on")):
        write(os.path.join(MODELS, "block/crt_television%s.json" % state), OrderedDict([
            ("parent", "minecraft:block/orientable"),
            ("textures", OrderedDict([("front", "tomyen:block/" + front_tex), ("side", "tomyen:block/crt_television_side"),
                                      ("top", "tomyen:block/crt_television_top")]))]))
    variants = OrderedDict()
    for lit in ("false", "true"):
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = OrderedDict([("model", "tomyen:block/crt_television" + ("_on" if lit == "true" else ""))])
            if y:
                v["y"] = y
            variants["facing=%s,lit=%s" % (facing, lit)] = v
    write(os.path.join(ASSETS, "blockstates/crt_television.json"), {"variants": variants})
    write(os.path.join(ITEMS, "crt_television.json"), {"model": {"type": "minecraft:model", "model": "tomyen:block/crt_television_on"}})
    for name in ("cathode_tube", "cathode_walker_spawn_egg"):
        write(os.path.join(MODELS, "item", name + ".json"),
              OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": "tomyen:item/" + name})]))
        write(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "tomyen:item/" + name}})

    # ------------------------------------------------------------------------------------------ data
    write(os.path.join(DATA, "loot_table/entities/cathode_walker.json"), OrderedDict([
        ("type", "minecraft:entity"),
        ("pools", [drop("tomyen:cathode_tube", 1, 1, 0.0, chance=0.5), drop("minecraft:glass_pane", 0, 2),
                   drop("minecraft:copper_nugget", 1, 3), drop("minecraft:redstone", 0, 2)]),
        ("random_sequence", "tomyen:entities/cathode_walker")]))
    write(os.path.join(DATA, "loot_table/blocks/crt_television.json"), OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}), ("rolls", 1),
                                ("entries", [{"type": "minecraft:item", "name": "tomyen:crt_television"}])])]),
        ("random_sequence", "tomyen:blocks/crt_television")]))
    tag_add(os.path.join(MC_DATA, "tags/block/mineable/axe.json"), "tomyen:crt_television")
    # Water's damage to it: no knockback, no hunger, the same whatever the difficulty.
    write(os.path.join(DATA, "damage_type/short_circuit.json"), OrderedDict([
        ("exhaustion", 0.0), ("message_id", "tomyen.short_circuit"), ("scaling", "never")]))

    # The Cathode Tube's uses. A vacuum tube is a computer's oldest logic, a CRT a heart monitor's screen and a HUD's
    # display: each an alternative to the usual recipe, never harder.
    shapeless("computer_chip_from_cathode_tube", ["tomyen:cathode_tube", "tomyen:tech_scrap"], "tomyen:computer_chip")
    shapeless("bio_monitor_module_from_cathode_tube", ["tomyen:cathode_tube", "tomyen:tech_scrap", "minecraft:glistering_melon_slice"],
              "tomyen:bio_monitor_module", category="equipment")
    shaped("power_armor_helmet_mk1_from_cathode_tube", ["BTB", "IGI"], OrderedDict([
        ("B", "minecraft:iron_block"), ("T", "tomyen:cathode_tube"), ("I", "minecraft:iron_ingot"), ("G", "minecraft:glass_pane")]),
        "tomyen:power_armor_helmet_mk1", category="equipment")
    shaped("crt_television", ["PPP", "PTR", "PPP"], OrderedDict([
        ("P", "#minecraft:planks"), ("T", "tomyen:cathode_tube"), ("R", "minecraft:redstone")]), "tomyen:crt_television")

    # ------------------------------------------------------------------------------------------ sounds and lang
    def ev(subtitle, *sounds):
        return OrderedDict([("subtitle", "subtitles.tomyen." + subtitle),
                            ("sounds", [OrderedDict([("name", "minecraft:" + n)] + [(k, v) for k, v in extra.items()])
                                        for n, extra in sounds])])

    def many(base, count, **extra):
        return [("%s%d" % (base, i), extra) for i in range(1, count + 1)]

    insert_after(os.path.join(ASSETS, "sounds.json"), "skullcrane_death", OrderedDict([
        # Static hiss and a mains hum: fire's crackle and the fizz pitched up, the beacon's drone pitched way up.
        ("cathode_walker_ambient", ev("cathode_walker_ambient", ("fire/fire", {"pitch": 1.8, "volume": 0.6}),
                                      ("random/fizz", {"pitch": 1.7, "volume": 0.3}),
                                      ("block/beacon/ambient", {"pitch": 1.9, "volume": 0.6}))),
        ("cathode_walker_step", ev("cathode_walker_step", *many("block/mud/step", 6, pitch=1.5, volume=0.45))),
        ("cathode_walker_kick", ev("cathode_walker_kick", *many("entity/player/attack/knockback", 4, pitch=0.8))),
        ("cathode_walker_charge", ev("cathode_walker_charge", *many("block/respawn_anchor/charge", 3, pitch=1.6))),
        # The Static Burst: a roar of static, fire's crackle pitched down and turned right up.
        ("cathode_walker_static_burst", ev("cathode_walker_static_burst", ("fire/fire", {"pitch": 0.9, "volume": 3.0}),
                                           ("random/fizz", {"pitch": 0.6, "volume": 2.0}))),
        ("cathode_walker_short", ev("cathode_walker_short", ("random/fizz", {"pitch": 1.4, "volume": 0.6}),
                                    *many("block/conduit/short", 9, pitch=1.6, volume=0.7))),
        ("cathode_walker_hurt", ev("cathode_walker_hurt", *many("block/copper_bulb/break", 4, pitch=1.5),
                                   ("random/fizz", {"pitch": 2.0, "volume": 0.5}))),
        ("cathode_walker_death", ev("cathode_walker_death", *many("random/glass", 3, pitch=0.8))),
        ("cathode_walker_pop", ev("cathode_walker_pop", ("random/pop", {"pitch": 0.5}))),
        ("cathode_walker_click_off", ev("cathode_walker_click_off", ("random/click", {"pitch": 0.7, "volume": 0.8}))),
        ("crt_television_toggle", ev("crt_television_toggle", ("random/click", {"pitch": 0.9, "volume": 0.6}))),
    ]))
    insert_after(os.path.join(ASSETS, "lang/en_us.json"), "item.tomyen.leader_core.use", OrderedDict([
        ("entity.tomyen.cathode_walker", "Cathode Walker"),
        ("item.tomyen.cathode_walker_spawn_egg", "Cathode Walker Spawn Egg"),
        ("item.tomyen.cathode_tube", "Cathode Tube"),
        ("block.tomyen.crt_television", "CRT Television"),
        ("death.attack.tomyen.short_circuit", "%1$s shorted out"),
        ("death.attack.tomyen.short_circuit.player", "%1$s shorted out whilst fighting %2$s"),
        ("subtitles.tomyen.cathode_walker_ambient", "Static hisses"),
        ("subtitles.tomyen.cathode_walker_step", "Bare feet slap"),
        ("subtitles.tomyen.cathode_walker_kick", "Cathode Walker kicks"),
        ("subtitles.tomyen.cathode_walker_charge", "Cathode Walker's screen brightens"),
        ("subtitles.tomyen.cathode_walker_static_burst", "Static roars"),
        ("subtitles.tomyen.cathode_walker_short", "Cathode Walker sparks"),
        ("subtitles.tomyen.cathode_walker_hurt", "Cathode Walker crackles"),
        ("subtitles.tomyen.cathode_walker_death", "Screen implodes"),
        ("subtitles.tomyen.cathode_walker_pop", "Tube pops"),
        ("subtitles.tomyen.cathode_walker_click_off", "Television clicks off"),
        ("subtitles.tomyen.crt_television_toggle", "Television clicks"),
    ]))

    # ------------------------------------------------------------------------------------------ previews
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        rest = {}
        views = [render(tex, rest, 0.0, frames["static_0"]), render(tex, rest, math.pi / 2, frames["static_0"]),
                 render(tex, rest, 0.6, frames["face"]), render(tex, rest, math.pi, None)]
        save_preview(os.path.join(PREVIEW, "preview_cathode_walker.png"), views, 4)
        # The legs sheet: front, side, three-quarter and back; the right foot close up (three-quarter from above,
        # side, from above, pushing off, landing); the strut cycle side on; the kick.
        foot = (-LEG_X, GROUND - 1.5, -2.5)
        feet = [render(tex, rest, 0.6, size=(240, 240), px_scale=24, pitch=0.5, target=foot),
                render(tex, rest, math.pi / 2, size=(240, 240), px_scale=24, target=foot),
                render(tex, rest, -1.1, size=(240, 240), px_scale=24, pitch=0.25, target=foot),
                render(tex, strut_pose(-math.pi / 4 + math.pi), math.pi / 2 + 0.5, size=(240, 240), px_scale=20,
                       pitch=0.2, target=(LEG_X, GROUND - 2.5, 2.5)),
                render(tex, strut_pose(math.pi / 4), 0.5, size=(240, 240), px_scale=20, pitch=0.9,
                       target=(LEG_X, GROUND - 1.5, -2.5))]
        cycle = [render(tex, strut_pose(n * math.pi / 4, 1.0, lean=1.0), math.pi / 2, frames["face"], size=(150, 260),
                        px_scale=6) for n in range(8)]
        kicks = [render(tex, strut_pose(math.pi / 2, 0.0, kick=k), math.pi / 2 + 0.3, frames["face"], size=(150, 260),
                        px_scale=6) for k in (0.15, 0.3, 0.45, 0.6, 0.85)]
        rows = [[render(tex, rest, a, frames["face"]) for a in (0.0, math.pi / 2, 0.6, math.pi)], feet, cycle, kicks]
        save_rows(os.path.join(PREVIEW, "preview_cathode_legs.png"), rows)
        anims = [render(tex, strut_pose(p, 1.0, lean=1.0), math.pi / 2, frames["face"]) for p in (0.0, math.pi / 4, math.pi / 2, 3 * math.pi / 4)]
        anims += [render(tex, strut_pose(math.pi / 2, 0.0, kick=k), math.pi / 2 + 0.3, frames["face"]) for k in (0.2, 0.45, 0.6, 0.85)]
        save_preview(os.path.join(PREVIEW, "preview_cathode_walker_anim.png"), anims, 4)
        screens = [render(tex, rest, 0.0, f, size=(200, 150), px_scale=11, target=(0, 3.0 + HIP_Y - 9, 0), ground=False)
                   for f in frames.values()]
        save_preview(os.path.join(PREVIEW, "preview_cathode_walker_screens.png"), screens, 6)
        sheet = [blow_up(tex, 6)]
        save_preview(os.path.join(PREVIEW, "preview_cathode_walker_texture.png"), sheet, 1)
        items = [blow_up(c, 12) for c in (tube, egg, tv_off, side, top)]
        first = Canvas(16, 16)
        first.px = anim.px[:256]
        items.append(blow_up(first, 12))
        save_preview(os.path.join(PREVIEW, "preview_cathode_walker_items.png"), items, 6)
    print("done")


main()
