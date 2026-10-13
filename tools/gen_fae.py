"""Builds the Fae content's art and data: the Glimmerwood set, the Fae Mushroom and Fae Bloom, Fae Silver (ore, raw,
ingot, tools), Fae Dust, the Fae Ring Charm, the Fairy in a Bottle and the fairy spawn egg, every one made from the
vanilla texture it stands in for (read from the Minecraft client jar, as gen_longswords.py does), and the fairy's own
entity texture, which is new art.

The vanilla textures keep their shading: each is recoloured through a ramp, a light-to-dark list of the new colours,
by its own brightness. Then each gets details of its own, so it can't be mistaken for the vanilla original:

    Glimmerwood log     the bark grain swirls instead of running straight, with two glowing knots
    Glimmerwood planks  a softer wave through the boards, and glowing pins at their ends
    Glimmerwood leaves  teal, with pink and white blossom-lights scattered through them
    Fae Mushroom        a two-capped cluster (a small cap beside a taller vanilla-shaped one), freckled with glowing dots
    Fae Bloom           an allium head turned to a teal puffball, a glowing heart, motes drifting round it
    Fae Silver ore      vanilla stone, with iron ore's flecks mirrored and turned to opal
    Fae Silver          lavender silver with an opal sheen and a four-point glint poking out past the outline
    Fae Dust            glowstone dust's heap in violet and cyan, with a sparkle above it

Block states, block and item models (all plain vanilla parents), item definitions, loot tables, recipes with their
recipe-book advancements, and tags are written too. The door, stairs and slab block states and models are vanilla
oak's, renamed. Writes build/fae_preview.png (or the path given with --preview): every texture beside the vanilla
one it came from, eight times size.

Usage: python3 tools/gen_fae.py src/main/resources/assets/mymod [--preview path.png]"""
import colorsys, glob, json, math, os, struct, sys, zipfile
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(__file__))
ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if "--preview" in ARGS:
    PREVIEW = ARGS[ARGS.index("--preview") + 1]
    del ARGS[ARGS.index("--preview"):ARGS.index("--preview") + 2]
ASSETS = ARGS[0] if ARGS else None
sys.argv = sys.argv[:1] + ARGS[:1]
from gen_longswords import client_jar, read_png, REPO, minecraft_version
from gunkit import png, write

DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data")) if ASSETS else None
CLEAR = (0, 0, 0, 0)

# ======================================================================================================= colour


def hexc(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def read_texture(data):
    """read_png, but also honouring a colour-key tRNS chunk on RGB and greyscale images (read_png only applies tRNS
    to palette images), which some vanilla item textures use for their transparent background."""
    w, h, px = read_png(data)
    pos, colour, key = 8, None, None
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if kind == b"IHDR":
            colour = body[9]
        elif kind == b"tRNS" and colour in (0, 2):
            key = tuple(struct.unpack(">HHH", body)) if colour == 2 else (struct.unpack(">H", body)[0],) * 3
    if key:
        px = [(0, 0, 0, 0) if c[:3] == key else c for c in px]
    return w, h, px


def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def from_hsv(h, s, v, a=255):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return (int(r * 255 + 0.5), int(g * 255 + 0.5), int(b * 255 + 0.5), a)


def ramp(colours, t, a=255):
    """The colour t (0 dark to 1 light) of the way along a list of hex colours."""
    t = max(0.0, min(1.0, t)) * (len(colours) - 1)
    i = min(int(t), len(colours) - 2)
    f = t - i
    lo, hi = hexc(colours[i]), hexc(colours[i + 1])
    return tuple(int(lo[k] + (hi[k] - lo[k]) * f + 0.5) for k in range(3)) + (a,)


def recolour(px, colours, select=lambda c, i: True, stretch=True, source=None):
    """Each selected opaque pixel through the ramp by its brightness, stretched over the selection's own range so the
    whole ramp is used. The selection is made on source (the untouched vanilla pixels) when given, so a pixel already
    recoloured by an earlier pass is judged by what it was."""
    test = source or px
    sel = [i for i, c in enumerate(px) if c[3] > 0 and select(test[i], i)]
    if not sel:
        return list(px)
    ls = [lum(px[i]) for i in sel]
    lo, hi = (min(ls), max(ls)) if stretch else (0.0, 1.0)
    out = list(px)
    for i in sel:
        out[i] = ramp(colours, (lum(px[i]) - lo) / max(hi - lo, 1e-6), px[i][3])
    return out


def opal(x, y, v=1.0):
    """An opal colour that drifts slowly from pink through lavender to cyan across the diagonal."""
    t = 0.5 + 0.5 * math.sin((x + y) * 0.2)
    return from_hsv(0.88 - 0.36 * t, 0.3, 0.72 + 0.26 * v)


class Sprite:
    def __init__(self, w, h, px=None):
        self.w, self.h = w, h
        self.px = list(px) if px else [CLEAR] * (w * h)

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def copy(self):
        return Sprite(self.w, self.h, self.px)


# Glimmerwood: indigo bark, lavender heartwood, cyan glow.
BARK = [0x1a0f33, 0x33205e, 0x553c8a, 0x7d66b4]
HEART = [0x3b2f5c, 0x5d4d86, 0x8170ad, 0xa999cf]
PLANK = [0x2e2448, 0x4e4073, 0x6f60a0, 0x9284c0, 0xb3a8d8]
GLOW = [0x1d8f9a, 0x45d6d4, 0x9ff8f0, 0xf0fffd]
LEAF = [0x0b2f3d, 0x15606a, 0x239a92, 0x52d6be, 0xb6f7e4]
STEM = [0x4a6b78, 0x7fb4b4, 0xbfe9df, 0xeafffa]
CAP = [0x2b1050, 0x4f2386, 0x7c3cc0, 0xa96ae0, 0xd2a8f5]
SILVER = [0x3a3550, 0x5f5a7c, 0x8f8aac, 0xc0bcd8, 0xeeeafb]
DUST = [0x3a1868, 0x6c3fb0, 0x8f78e0, 0x6fd8e6, 0xd8fbff]
PINK = hexc(0xff9ee6)
WHITE = hexc(0xf6fffe)


def knot(s, cx, cy):
    """A glowing knot in the bark: a dark lip round a cyan eye with a bright core (3 wide, 4 tall)."""
    for dx, dy in [(-1, -2), (0, -2), (1, -2), (-2, -1), (2, -1), (-2, 0), (2, 0), (-2, 1), (2, 1), (-1, 2), (0, 2), (1, 2)]:
        s.set(cx + dx, cy + dy, hexc(BARK[0]))
    for dx, dy in [(-1, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]:
        s.set(cx + dx, cy + dy, ramp(GLOW, 0.35))
    s.set(cx, cy - 1, ramp(GLOW, 0.75))
    s.set(cx, cy, ramp(GLOW, 1.0))


def glint(s, x, y, colour=WHITE, arm=1):
    """A four-point star of light, centred on (x, y)."""
    s.set(x, y, colour)
    dim = colour[:3] + (200,)
    for k in range(1, arm + 1):
        for dx, dy in [(k, 0), (-k, 0), (0, k), (0, -k)]:
            s.set(x + dx, y + dy, dim if k == arm else colour)


# ======================================================================================================= textures


def glimmerwood_log(src):
    """The oak bark's grain pushed into swirls (each row shifted by a wave that also runs across, so the stripes
    curl), then two glowing knots."""
    s = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            shift = round(2.2 * math.sin(2 * math.pi * (y / 16 + x / 16)) + 1.0 * math.sin(2 * math.pi * y / 8))
            s.set(x, y, src.get((x + shift) % 16, y))
    s.px = recolour(s.px, BARK)
    # Light ridges along the swirling grain, so the curl reads at a glance.
    for y in range(16):
        for x in range(16):
            shift = round(2.2 * math.sin(2 * math.pi * (y / 16 + x / 16)) + 1.0 * math.sin(2 * math.pi * y / 8))
            if (x + shift) % 5 == 0:
                s.set(x, y, ramp(BARK + [0x9a86cc], 0.85 + 0.15 * ((x + y) % 2)))
    knot(s, 4, 5)
    knot(s, 11, 12)
    return s


def glimmerwood_log_top(src):
    """Oak's end grain: the bark rim indigo, the rings lavender, and a glowing pith at the heart."""
    s = Sprite(16, 16, src.px)
    rim = lambda c, i: i % 16 in (0, 15) or i // 16 in (0, 15)
    s.px = recolour(s.px, BARK, rim, source=src.px)
    s.px = recolour(s.px, HEART, lambda c, i: not rim(c, i), source=src.px)
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        s.set(x, y, ramp(GLOW, 0.9))
    for x, y in [(7, 6), (8, 9), (6, 8), (9, 7)]:
        s.set(x, y, ramp(GLOW, 0.4))
    return s


def glimmerwood_planks(src):
    """Oak planks in lavender, the grain within each board rippled, and a glowing pin at the ends of each board."""
    s = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            c = src.get(x, y)
            board_edge = y % 4 == 3
            if not board_edge:
                c = src.get((x + round(math.sin(2 * math.pi * (x / 8 + y / 4)))) % 16, y)
            s.set(x, y, c)
    s.px = recolour(s.px, PLANK)
    for x, y in [(1, 1), (14, 5), (6, 9), (9, 13)]:
        s.set(x, y, ramp(GLOW, 0.85))
    return s


def glimmerwood_leaves(src):
    """Oak leaves (vanilla tints them; these carry their own colour) in teal, lit with pink and white blossom-lights."""
    s = Sprite(16, 16, recolour(src.px, [0x15606a, 0x2fa89c, 0x8ff0d8]))
    for i, (x, y) in enumerate([(2, 3), (11, 6), (5, 12)]):
        if s.get(x, y)[3]:
            s.set(x, y, PINK if i % 2 else WHITE)
    return s


def glimmerwood_sapling(src):
    s = Sprite(16, 16, src.px)
    leafy = lambda c, i: hsv(c)[0] > 0.17
    s.px = recolour(s.px, LEAF, leafy, source=src.px)
    s.px = recolour(s.px, BARK[1:], lambda c, i: not leafy(c, i), source=src.px)
    top = min(i // 16 for i, c in enumerate(s.px) if c[3])
    for i, c in enumerate(s.px):
        if c[3] and i // 16 == top:
            s.set(i % 16, top, WHITE)
            break
    return s


def glimmerwood_door(src, knots):
    """Oak's door in the plank colours, its metal fittings silvered, and a glowing knot set in the panel."""
    s = Sprite(src.w, src.h, src.px)
    metal = lambda c, i: hsv(c)[1] < 0.2
    s.px = recolour(s.px, SILVER, metal, source=src.px)
    s.px = recolour(s.px, PLANK, lambda c, i: not metal(c, i), source=src.px)
    for x, y in knots:
        if s.get(x, y)[3]:
            s.set(x, y, ramp(GLOW, 1.0))
            for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                if s.get(x + dx, y + dy)[3]:
                    s.set(x + dx, y + dy, ramp(GLOW, 0.35))
    return s


def fae_mushroom(src):
    """Vanilla's red mushroom stood up taller (its stem drawn out by three rows), with a small second mushroom
    sprouting beside it: violet caps, pale teal stems, freckled with glowing dots in place of the white spots."""
    cap = lambda c: hsv(c)[0] > 0.9 or hsv(c)[0] < 0.05 or hsv(c)[1] < 0.2 and lum(c) > 0.6
    rows = [y for y in range(16) if any(src.get(x, y)[3] for x in range(16))]
    cap_rows = [y for y in rows if any(src.get(x, y)[3] and cap(src.get(x, y)) and hsv(src.get(x, y))[1] > 0.2 for x in range(16))]
    stem_rows = [y for y in rows if y > max(cap_rows)]
    s = Sprite(16, 16)
    lift = 3
    for y in cap_rows:
        for x in range(16):
            if src.get(x, y)[3]:
                s.set(x + 1, y - lift, src.get(x, y))
    # The stem, drawn out: its top row repeated down to where the vanilla stem starts.
    for y in range(max(cap_rows) - lift + 1, 16):
        sy = max(stem_rows[0], y + len(stem_rows) - 16 + stem_rows[0])
        for x in range(16):
            if src.get(x, sy)[3]:
                s.set(x + 1, y, src.get(x, sy))
    capsel = lambda c, i: i // 16 <= max(cap_rows) - lift
    s.px = recolour(s.px, CAP, capsel)
    s.px = recolour(s.px, STEM, lambda c, i: not capsel(c, i))
    # The little one: a three-row cap and a two-pixel stem, at the lower left.
    small = {11: range(2, 5), 12: range(1, 6), 13: range(1, 6)}
    for y, xs in small.items():
        for x in xs:
            s.set(x, y, ramp(CAP, 0.85 - 0.25 * (y - 11) - (0.2 if x in (xs[0], xs[-1]) else 0.0)))
    for y in (14, 15):
        s.set(3, y, ramp(STEM, 0.75 - 0.2 * (y - 14)))
    # Glowing freckles.
    top = max(cap_rows) - lift
    for x, y in [(8, top - 2), (11, top - 1), (6, top), (10, top), (3, 12), (2, 11)]:
        if s.get(x, y)[3]:
            s.set(x, y, ramp(GLOW, 1.0))
    return s


def fae_bloom(src):
    """The allium's round head turned into a teal puffball with a glowing heart, on a teal-green stem, with three
    motes of light drifting round it."""
    s = Sprite(16, 16, src.px)
    head = lambda c, i: hsv(c)[0] > 0.6
    s.px = recolour(s.px, LEAF[1:] + [0xe6fffb], head, source=src.px)
    s.px = recolour(s.px, [0x1d4f3c, 0x2f7a5e, 0x4caa86], lambda c, i: not head(c, i), source=src.px)
    heads = [i for i, c in enumerate(s.px) if c[3] and head(src.px[i], i)]
    cx = round(sum(i % 16 for i in heads) / len(heads))
    cy = round(sum(i // 16 for i in heads) / len(heads))
    s.set(cx, cy, PINK)
    s.set(cx - 1, cy, ramp(GLOW, 0.9))
    for x, y in [(2, 2), (12, 4), (11, 9)]:
        s.set(x, y, WHITE[:3] + (220,))
    return s


def fae_silver_ore(ore, stone):
    """Vanilla stone, with iron ore's flecks mirrored left to right (so they sit elsewhere) and turned to opal, with
    a white glint in a few of them."""
    s = Sprite(16, 16, stone.px)
    fleck = [[ore.get(x, y) != stone.get(x, y) for x in range(16)] for y in range(16)]
    ls = [lum(ore.get(x, y)) for y in range(16) for x in range(16) if fleck[y][x]]
    lo, hi = min(ls), max(ls)
    for y in range(16):
        for x in range(16):
            if fleck[y][15 - x]:
                v = (lum(ore.get(15 - x, y)) - lo) / max(hi - lo, 1e-6)
                s.set(x, y, opal(x, y, v))
    for x, y in [(12, 2), (6, 5), (5, 12)]:
        if fleck[y][15 - x]:
            s.set(x, y, WHITE)
    return s


def fae_silver_item(src, star):
    """Lavender silver over the item's own shading, an opal sheen down the diagonal, and a glint at one corner that
    breaks the outline."""
    s = Sprite(src.w, src.h, recolour(src.px, SILVER))
    for i, c in enumerate(s.px):
        x, y = i % s.w, i // s.w
        if c[3] and abs((x - y) - 1) <= 1:
            o = opal(x, y)
            s.px[i] = tuple(int(c[k] * 0.55 + o[k] * 0.45) for k in range(3)) + (c[3],)
    glint(s, *star, arm=1)
    return s


def fae_silver_tool(src):
    """An iron tool: the metal silvered, the handle Glimmerwood, and a glowing gem where the
    head meets the handle."""
    s = Sprite(16, 16, src.px)
    handle = lambda c, i: hsv(c)[1] >= 0.2 and 0.02 < hsv(c)[0] < 0.15
    s.px = recolour(s.px, SILVER, lambda c, i: not handle(c, i), source=src.px)
    s.px = recolour(s.px, BARK[1:] + [0x8a77bd], handle, source=src.px)
    # The gem: the handle pixel nearest the head.
    metal = [i for i, c in enumerate(src.px) if c[3] and not handle(c, i)]
    best = None
    for i, c in enumerate(src.px):
        if c[3] and handle(c, i):
            d = min(abs(i % 16 - j % 16) + abs(i // 16 - j // 16) for j in metal)
            if best is None or d < best[0] or d == best[0] and i // 16 < best[1] // 16:
                best = (d, i)
    if best:
        s.px[best[1]] = ramp(GLOW, 1.0)
    return s


def fae_dust(src):
    """Glowstone dust's heap, violet at the base rising to cyan, with a sparkle hanging above it."""
    s = Sprite(16, 16, recolour(src.px, DUST))
    glint(s, 12, 2, arm=1)
    s.set(4, 3, WHITE[:3] + (210,))
    return s


def raw_fae_silver(src):
    s = Sprite(16, 16, recolour(src.px, SILVER))
    glint(s, 12, 3, arm=1)
    return s


def fae_ring_charm(ingot, shard):
    """A silver ring (shaded with iron ingot's own colours by the angle the light catches it) set with a gem in the
    amethyst shard's colours, with a spark of fae light."""
    metal = sorted({c for c in ingot.px if c[3]}, key=lum)
    gem = sorted({c for c in shard.px if c[3]}, key=lum)
    s = Sprite(16, 16)
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, (y - cy) * 1.1)
            if 3.6 <= d <= 5.6:
                light = 0.65 + 0.35 * math.cos(math.atan2(y - cy, x - cx) + 2.4)
                edge = 0.25 if d > 5.0 or d < 4.1 else 0.0
                c = metal[int(max(0.0, light - edge) * (len(metal) - 1))]
                h, sat, v = hsv(c)
                s.set(x, y, from_hsv(0.72, 0.18, v))
    for (x, y), t in {(6, 3): 0.6, (7, 3): 0.9, (8, 3): 0.6, (5, 4): 0.3, (6, 4): 0.7, (7, 4): 1.0, (8, 4): 0.7, (9, 4): 0.3,
                      (6, 5): 0.4, (7, 5): 0.6, (8, 5): 0.4, (7, 6): 0.3}.items():
        s.set(x, y, gem[int(t * (len(gem) - 1))])
    glint(s, 12, 2, colour=ramp(GLOW, 1.0), arm=1)
    return s


def fairy_bottle_item(src):
    """The bottle o' enchanting's glass with the glow inside turned violet and teal, and a fairy in it: a bright body
    with pink wings."""
    s = Sprite(16, 16, src.px)
    liquid = lambda c, i: hsv(c)[1] > 0.5 and 0.15 < hsv(c)[0] < 0.45
    s.px = recolour(s.px, [0x3a1868, 0x6c3fb0, 0x45d6d4, 0x9ff8f0], liquid, source=src.px)
    s.px = recolour(s.px, [0x2c3a52, 0x5f7896, 0xa6c4dc], lambda c, i: not liquid(c, i), source=src.px)
    s.set(8, 9, WHITE)
    s.set(8, 10, ramp(GLOW, 0.9))
    for x, y in [(6, 8), (7, 9), (10, 8), (9, 9)]:
        s.set(x, y, PINK)
    return s


def fairy_bottle_block(src):
    """The lantern (all three frames) with silvered metal and a teal flame, and a fairy's pink wings either side of
    the light."""
    s = Sprite(src.w, src.h, src.px)
    flame = lambda c, i: 0.05 < hsv(c)[0] < 0.2 and hsv(c)[1] > 0.3 and i % 16 < 6
    s.px = recolour(s.px, SILVER[:4], lambda c, i: not flame(c, i), source=src.px)
    s.px = recolour(s.px, [0x2a8f9a, 0x6fe6e0, 0xeafffd], flame, source=src.px)
    for f in range(src.h // 16):
        for x, y in [(1, 5), (4, 5)]:
            s.set(x, y + f * 16, PINK)
    return s


def fairy_spawn_egg(src):
    """The allay's egg, its blues shifted round to violet and its spots to fairy pink."""
    s = Sprite(16, 16, src.px)
    for i, c in enumerate(s.px):
        if c[3]:
            h, sat, v = hsv(c)
            s.px[i] = from_hsv(0.78 if sat > 0.15 and 0.45 < h < 0.7 else (0.9 if sat > 0.15 else h), sat, v, c[3])
    return s


def fairy_entity():
    """The fairy's own texture (32x32, UVs match FairyModel): a head with a pale face under teal hair, a violet
    body, and one gauzy wing (both wing planes share it), all in smooth top-to-bottom shading."""
    s = Sprite(32, 32)
    skin = [0xcdb8e2, 0xf6ecff]
    hair = [0x2a9a96, 0x7fe8dc]
    tunic = [0x4c2c8a, 0x8a68d4]

    def box(u, v, w, h, d, colours):
        # Vanilla's box UV layout: top and bottom faces above, the four sides below them; lit from above.
        for y in range(d + h):
            for x in range(2 * (w + d)):
                if y < d and not d <= x < d + 2 * w:
                    continue
                t = 1.0 if y < d else 0.85 - 0.5 * (y - d) / max(h - 1, 1)
                s.set(u + x, v + y, ramp(colours, t))

    box(0, 0, 4, 4, 4, hair)
    # The face (the head's front, its second side slot) is skin under a fringe of hair.
    for y in range(5, 8):
        for x in range(4, 8):
            s.set(x, y, ramp(skin, 0.95 - 0.3 * (y - 5) / 2))
    s.set(5, 6, hexc(0x2a1858))
    s.set(6, 6, hexc(0x2a1858))
    box(0, 8, 2, 3, 2, tunic)

    # The wing: a flat pane 7 long and 6 tall, its face at (u, v + d); pale cyan fading to pink at the tip.
    u, v, d, h = 0, 16, 7, 6
    for face in range(2):
        for y in range(h):
            for x in range(d):
                c = ramp([0x9ff8f0, 0xd8f0ff, 0xffb4ec], x / (d - 1))
                edge = x == d - 1 or y in (0, h - 1)
                s.set(u + face * d + x, v + d + y, c[:3] + (210 if edge else 130,))
    return s


# ======================================================================================================= data


def common_jar():
    """The Minecraft common jar (data packs live there), from the project's Loom cache or the main checkout's."""
    version = minecraft_version()
    roots = [REPO]
    marker = os.sep + os.path.join(".claude", "worktrees") + os.sep
    if marker in REPO:
        roots.append(REPO.split(marker)[0])
    for root in roots:
        for path in sorted(glob.glob(os.path.join(root, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft",
                                                  "minecraft-common-*", version, f"minecraft-common-*-{version}.jar"))):
            return path
    sys.exit(f"no Minecraft {version} common jar found; run ./gradlew build first")


def renamed(obj, names):
    """A vanilla JSON object with every mention of the vanilla names swapped for ours."""
    text = json.dumps(obj)
    for old, new in names.items():
        text = text.replace(old, new)
    return json.loads(text, object_pairs_hook=OrderedDict)


def item_model(name, texture=None, parent="minecraft:item/generated"):
    write(os.path.join(ASSETS, "models", "item", name + ".json"),
          OrderedDict([("parent", parent), ("textures", {"layer0": texture or f"mymod:item/{name}"})]))
    item_def(name, f"mymod:item/{name}")


def item_def(name, model, tints=None):
    m = OrderedDict([("type", "minecraft:model"), ("model", model)])
    if tints:
        m["tints"] = tints
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": m})


def block_model(name, parent, textures):
    write(os.path.join(ASSETS, "models", "block", name + ".json"), OrderedDict([("parent", parent), ("textures", textures)]))


def simple_state(name, model=None):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": model or f"mymod:block/{name}"}}})


def loot(name, table):
    table = OrderedDict(table)
    table["random_sequence"] = f"mymod:blocks/{name}"
    write(os.path.join(DATA, "mymod", "loot_table", "blocks", name + ".json"), table)


def drops_self(name):
    loot(name, {"type": "minecraft:block", "pools": [{"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                                                      "entries": [{"type": "minecraft:item", "name": f"mymod:{name}"}], "rolls": 1.0}]})


def recipe(name, body, category, unlock):
    """A recipe and its recipe-book advancement, unlocked by having any of the items (or tags) in unlock."""
    write(os.path.join(DATA, "mymod", "recipe", name + ".json"), body)
    criteria = OrderedDict()
    for i, item in enumerate(unlock):
        criteria[f"has_{i}"] = {"conditions": {"items": [{"items": item}]}, "trigger": "minecraft:inventory_changed"}
    criteria["has_the_recipe"] = {"conditions": {"recipes": f"mymod:{name}"}, "trigger": "minecraft:recipe_unlocked"}
    write(os.path.join(DATA, "mymod", "advancement", "recipes", category, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"), ("criteria", criteria), ("requirements", [list(criteria)]),
        ("rewards", {"recipes": [f"mymod:{name}"]})]))


def shaped(result, pattern, key, category, count=1, group=None):
    body = OrderedDict([("type", "minecraft:crafting_shaped"), ("category", category)])
    if group:
        body["group"] = group
    body["key"] = key
    body["pattern"] = pattern
    body["result"] = OrderedDict([("count", count), ("id", f"mymod:{result}")])
    return body


def shapeless(result, ingredients, category, count=1, group=None):
    body = OrderedDict([("type", "minecraft:crafting_shapeless"), ("category", category)])
    if group:
        body["group"] = group
    body["ingredients"] = ingredients
    body["result"] = OrderedDict([("count", count), ("id", result if ":" in result else f"mymod:{result}")])
    return body


def tag(kind, namespace, name, values):
    """Writes a tag, or adds to it if this script (or another) already wrote one."""
    path = os.path.join(DATA, namespace, "tags", kind, name + ".json")
    body = OrderedDict([("replace", False), ("values", [])])
    if os.path.exists(path):
        with open(path) as f:
            body = json.load(f, object_pairs_hook=OrderedDict)
    body["values"] += [v for v in values if v not in body["values"]]
    write(path, body)


WOOD = {"minecraft:oak_planks": "mymod:glimmerwood_planks", "minecraft:block/oak_planks": "mymod:block/glimmerwood_planks",
        "minecraft:block/oak_door": "mymod:block/glimmerwood_door", "minecraft:oak_door": "mymod:glimmerwood_door",
        "minecraft:block/oak_stairs": "mymod:block/glimmerwood_stairs", "minecraft:block/oak_slab": "mymod:block/glimmerwood_slab",
        "minecraft:oak_slab": "mymod:glimmerwood_slab", "minecraft:oak_stairs": "mymod:glimmerwood_stairs",
        "minecraft:item/oak_door": "mymod:item/glimmerwood_door", "minecraft:blocks/oak": "mymod:blocks/glimmerwood",
        "minecraft:oak_leaves": "mymod:glimmerwood_leaves", "minecraft:oak_sapling": "mymod:glimmerwood_sapling",
        "minecraft:blocks/grass_block": "mymod:blocks/fae_grass_block", "minecraft:grass_block": "mymod:fae_grass_block"}


def write_data(client, common):
    def vanilla(path):
        return json.loads(client.read(path), object_pairs_hook=OrderedDict)

    def vanilla_data(path):
        return json.loads(common.read(path), object_pairs_hook=OrderedDict)

    # --- Block states and models ----------------------------------------------------------------------------
    for name in ["fae_mushroom", "fae_bloom", "glimmerwood_sapling"]:
        block_model(name, "minecraft:block/cross", {"cross": f"mymod:block/{name}"})
        simple_state(name)
        item_model(name, f"mymod:block/{name}")
    for name in ["glimmerwood_planks", "glimmerwood_leaves", "fae_silver_ore"]:
        block_model(name, "minecraft:block/cube_all", {"all": f"mymod:block/{name}"})
        simple_state(name)
        item_def(name, f"mymod:block/{name}")
    block_model("glimmerwood_log", "minecraft:block/cube_column",
                {"end": "mymod:block/glimmerwood_log_top", "side": "mymod:block/glimmerwood_log"})
    block_model("glimmerwood_log_horizontal", "minecraft:block/cube_column_horizontal",
                {"end": "mymod:block/glimmerwood_log_top", "side": "mymod:block/glimmerwood_log"})
    write(os.path.join(ASSETS, "blockstates", "glimmerwood_log.json"), renamed(vanilla("assets/minecraft/blockstates/oak_log.json"),
                                                                             {"minecraft:block/oak_log": "mymod:block/glimmerwood_log"}))
    item_def("glimmerwood_log", "mymod:block/glimmerwood_log")
    # The Fae Grass Block is drawn with vanilla's own grass block models; only its tint differs (see FaeClient).
    write(os.path.join(ASSETS, "blockstates", "fae_grass_block.json"), vanilla("assets/minecraft/blockstates/grass_block.json"))
    item_def("fae_grass_block", "minecraft:block/grass_block",
             [{"type": "minecraft:constant", "value": -10031489}])
    # Door, stairs, slab: vanilla oak's block states and models, renamed.
    for kind in ["door", "stairs", "slab"]:
        write(os.path.join(ASSETS, "blockstates", f"glimmerwood_{kind}.json"),
              renamed(vanilla(f"assets/minecraft/blockstates/oak_{kind}.json"), WOOD))
    for path in client.namelist():
        base = os.path.basename(path)
        if path.startswith("assets/minecraft/models/block/oak_") and any(base.startswith(f"oak_{k}") for k in ["door", "stairs", "slab"]):
            write(os.path.join(ASSETS, "models", "block", "glimmerwood_" + base[4:]), renamed(vanilla(path), WOOD))
    item_def("glimmerwood_stairs", "mymod:block/glimmerwood_stairs")
    item_def("glimmerwood_slab", "mymod:block/glimmerwood_slab")
    item_model("glimmerwood_door")
    # The Fairy in a Bottle: hung or stood like a lantern.
    block_model("fairy_in_a_bottle", "minecraft:block/template_lantern", {"lantern": "mymod:block/fairy_in_a_bottle"})
    block_model("fairy_in_a_bottle_hanging", "minecraft:block/template_hanging_lantern", {"lantern": "mymod:block/fairy_in_a_bottle"})
    write(os.path.join(ASSETS, "blockstates", "fairy_in_a_bottle.json"), {"variants": OrderedDict([
        ("hanging=false", {"model": "mymod:block/fairy_in_a_bottle"}), ("hanging=true", {"model": "mymod:block/fairy_in_a_bottle_hanging"})])})
    item_model("fairy_in_a_bottle")
    # Items.
    for name in ["fae_dust", "raw_fae_silver", "fae_silver_ingot", "fae_ring_charm", "fairy_spawn_egg"]:
        item_model(name)
    for tool in TOOLS:
        item_model(f"fae_silver_{tool}", parent="minecraft:item/handheld")

    # --- Loot tables ------------------------------------------------------------------------------------------
    for name in ["fae_mushroom", "fae_bloom", "glimmerwood_log", "glimmerwood_planks", "glimmerwood_sapling", "glimmerwood_stairs",
                 "fairy_in_a_bottle"]:
        drops_self(name)
    for kind in ["slab", "door"]:
        loot(f"glimmerwood_{kind}", renamed(vanilla_data(f"data/minecraft/loot_table/blocks/oak_{kind}.json"), WOOD))
    leaves = renamed(vanilla_data("data/minecraft/loot_table/blocks/oak_leaves.json"), WOOD)
    leaves["pools"] = [p for p in leaves["pools"] if "minecraft:apple" not in json.dumps(p)]
    loot("glimmerwood_leaves", leaves)
    loot("fae_grass_block", renamed(vanilla_data("data/minecraft/loot_table/blocks/grass_block.json"), WOOD))
    ore = renamed(vanilla_data("data/minecraft/loot_table/blocks/iron_ore.json"),
                  {"minecraft:raw_iron": "mymod:raw_fae_silver", "minecraft:iron_ore": "mymod:fae_silver_ore"})
    loot("fae_silver_ore", ore)

    # --- Recipes ----------------------------------------------------------------------------------------------
    recipe("glimmerwood_planks", shapeless("glimmerwood_planks", ["mymod:glimmerwood_log"], "building", 4, "planks"), "building",
           ["mymod:glimmerwood_log"])
    for kind, cat, folder in [("stairs", "building", "building"), ("slab", "building", "building"), ("door", "redstone", "redstone")]:
        body = renamed(vanilla_data(f"data/minecraft/recipe/oak_{kind}.json"), WOOD)
        recipe(f"glimmerwood_{kind}", body, folder, ["mymod:glimmerwood_planks"])
    for method, kind in [("smelting", "minecraft:smelting"), ("blasting", "minecraft:blasting")]:
        for source in ["raw_fae_silver", "fae_silver_ore"]:
            recipe(f"fae_silver_ingot_from_{method}_{source}", OrderedDict([
                ("type", kind), ("category", "misc"), ("cookingtime", 200 if method == "smelting" else 100), ("experience", 0.8),
                ("group", "fae_silver_ingot"), ("ingredient", f"mymod:{source}"), ("result", {"id": "mymod:fae_silver_ingot"})]),
                "misc", [f"mymod:{source}"])
    for tool, pattern in TOOLS.items():
        body = shaped(f"fae_silver_{tool}", pattern, OrderedDict([("#", "minecraft:stick"), ("X", "mymod:fae_silver_ingot")]),
                      "equipment")
        recipe(f"fae_silver_{tool}", body, "combat" if tool == "sword" else "equipment", ["mymod:fae_silver_ingot"])
    recipe("fae_ring_charm", shaped("fae_ring_charm", [" D ", "DMD", " S "], OrderedDict([
        ("D", "mymod:fae_dust"), ("M", "mymod:fae_mushroom"), ("S", "mymod:fae_silver_ingot")]), "misc"), "misc", ["mymod:fae_dust"])
    recipe("fae_dust_from_fae_bloom", shapeless("fae_dust", ["mymod:fae_bloom"], "misc", 2), "misc", ["mymod:fae_bloom"])
    recipe("glow_ink_sac_from_fae_dust", shapeless("minecraft:glow_ink_sac", ["minecraft:ink_sac", "mymod:fae_dust"], "misc"), "misc",
           ["mymod:fae_dust"])
    for potion, reagent in [("slow_falling", "mymod:fae_dust"), ("night_vision", "mymod:fae_mushroom")]:
        write(os.path.join(DATA, "mymod", "recipe", f"potion_{potion}_from_{reagent.split(':')[1]}.json"), OrderedDict([
            ("type", "minecraft:brewing"),
            ("input", {"item": "minecraft:potion", "potion_contents": {"potions": "minecraft:awkward"}}),
            ("output", {"components": {"minecraft:potion_contents": {"potion": f"minecraft:{potion}"}}, "id": "minecraft:potion"}),
            ("reagent", {"item": reagent})]))

    # --- Tags -------------------------------------------------------------------------------------------------
    tag("block", "minecraft", "logs_that_burn", ["mymod:glimmerwood_log"])
    tag("item", "minecraft", "logs_that_burn", ["mymod:glimmerwood_log"])
    for kind, name in [("planks", "planks"), ("leaves", "leaves"), ("sapling", "saplings"), ("stairs", "wooden_stairs"),
                       ("slab", "wooden_slabs"), ("door", "wooden_doors")]:
        tag("block", "minecraft", name, [f"mymod:glimmerwood_{kind}"])
        tag("item", "minecraft", name, [f"mymod:glimmerwood_{kind}"])
    tag("block", "minecraft", "small_flowers", ["mymod:fae_bloom"])
    tag("item", "minecraft", "small_flowers", ["mymod:fae_bloom"])
    tag("block", "minecraft", "grass_blocks", ["mymod:fae_grass_block"])
    tag("block", "minecraft", "animals_spawnable_on", ["mymod:fae_grass_block"])
    tag("block", "minecraft", "mineable/shovel", ["mymod:fae_grass_block"])
    tag("block", "minecraft", "mineable/pickaxe", ["mymod:fae_silver_ore", "mymod:fairy_in_a_bottle"])
    tag("block", "minecraft", "needs_iron_tool", ["mymod:fae_silver_ore"])
    tag("block", "minecraft", "ores", ["mymod:fae_silver_ore"])
    tag("item", "minecraft", "ores", ["mymod:fae_silver_ore"])
    tag("item", "mymod", "fae_silver_tool_materials", ["mymod:fae_silver_ingot"])
    tag("block", "mymod", "fae_ring_mushrooms", ["minecraft:red_mushroom", "minecraft:brown_mushroom", "mymod:fae_mushroom"])
    tag("block", "mymod", "feywild_ore_replaceables", ["minecraft:stone", "minecraft:andesite", "minecraft:calcite"])
    for tool in TOOLS:
        tag("item", "minecraft", tool + ("s" if tool != "axe" else "es"), [f"mymod:fae_silver_{tool}"])


TOOLS = OrderedDict([("sword", ["X", "X", "#"]), ("pickaxe", ["XXX", " # ", " # "]), ("axe", ["XX", "X#", " #"]),
                     ("shovel", ["X", "#", "#"]), ("hoe", ["XX", " #", " #"])])


# ======================================================================================================= main


def preview(path, pairs):
    """Every new texture beside the vanilla one it came from, at eight times size on a mid grey."""
    scale, pad = 8, 8
    cell = 16 * scale
    w = pad + len(pairs[0]) * 0 + 2 * (cell + pad) * 4
    rows = (len(pairs) + 3) // 4
    h = pad + rows * (cell + pad)
    out = [(70, 70, 78, 255)] * (w * h)
    for n, (a, b) in enumerate(pairs):
        ox = pad + (n % 4) * 2 * (cell + pad)
        oy = pad + (n // 4) * (cell + pad)
        for k, sprite in enumerate((a, b)):
            fs = 16 / max(sprite.w, sprite.h)
            for y in range(cell):
                for x in range(cell):
                    sx, sy = int(x / scale / fs), int(y / scale / fs)
                    c = sprite.get(sx, sy) if sx < sprite.w and sy < sprite.h else CLEAR
                    bg = (92, 92, 100) if (x // 16 + y // 16) % 2 else (70, 70, 78)
                    a_ = c[3] / 255
                    px = tuple(int(c[i] * a_ + bg[i] * (1 - a_)) for i in range(3)) + (255,)
                    out[(oy + y) * w + ox + k * (cell + 2) + x] = px
    png(path, w, h, out)


def main():
    if not ASSETS:
        sys.exit(__doc__.rsplit("Usage: ", 1)[1])
    jar = client_jar()
    print("vanilla textures from", jar)
    block, item = os.path.join(ASSETS, "textures", "block"), os.path.join(ASSETS, "textures", "item")
    pairs = []
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(common_jar()) as c:
        def tex(name):
            w, h, px = read_texture(z.read(f"assets/minecraft/textures/{name}.png"))
            return Sprite(w, h, px)

        def out(folder, name, sprite, source):
            png(os.path.join(folder, name + ".png"), sprite.w, sprite.h, sprite.px)
            pairs.append((source, sprite))

        out(block, "glimmerwood_log", glimmerwood_log(tex("block/oak_log")), tex("block/oak_log"))
        out(block, "glimmerwood_log_top", glimmerwood_log_top(tex("block/oak_log_top")), tex("block/oak_log_top"))
        out(block, "glimmerwood_planks", glimmerwood_planks(tex("block/oak_planks")), tex("block/oak_planks"))
        leaves = tex("block/oak_leaves")
        tinted = Sprite(16, 16, [tuple(int(c[k] * (0x48, 0xb5, 0x18)[k] / 255) for k in range(3)) + (c[3],) for c in leaves.px])
        out(block, "glimmerwood_leaves", glimmerwood_leaves(leaves), tinted)
        out(block, "glimmerwood_sapling", glimmerwood_sapling(tex("block/oak_sapling")), tex("block/oak_sapling"))
        out(block, "glimmerwood_door_top", glimmerwood_door(tex("block/oak_door_top"), [(8, 11)]), tex("block/oak_door_top"))
        out(block, "glimmerwood_door_bottom", glimmerwood_door(tex("block/oak_door_bottom"), [(8, 5)]), tex("block/oak_door_bottom"))
        out(item, "glimmerwood_door", glimmerwood_door(tex("item/oak_door"), [(8, 10)]), tex("item/oak_door"))
        out(block, "fae_mushroom", fae_mushroom(tex("block/red_mushroom")), tex("block/red_mushroom"))
        out(block, "fae_bloom", fae_bloom(tex("block/allium")), tex("block/allium"))
        out(block, "fae_silver_ore", fae_silver_ore(tex("block/iron_ore"), tex("block/stone")), tex("block/iron_ore"))
        out(item, "raw_fae_silver", raw_fae_silver(tex("item/raw_iron")), tex("item/raw_iron"))
        out(item, "fae_silver_ingot", fae_silver_item(tex("item/iron_ingot"), (13, 3)), tex("item/iron_ingot"))
        for tool in TOOLS:
            out(item, f"fae_silver_{tool}", fae_silver_tool(tex(f"item/iron_{tool}")), tex(f"item/iron_{tool}"))
        out(item, "fae_dust", fae_dust(tex("item/glowstone_dust")), tex("item/glowstone_dust"))
        out(item, "fae_ring_charm", fae_ring_charm(tex("item/iron_ingot"), tex("item/amethyst_shard")), tex("item/amethyst_shard"))
        out(item, "fairy_in_a_bottle", fairy_bottle_item(tex("item/experience_bottle")), tex("item/experience_bottle"))
        out(block, "fairy_in_a_bottle", fairy_bottle_block(tex("block/lantern")), tex("block/lantern"))
        with open(os.path.join(block, "fairy_in_a_bottle.png.mcmeta"), "wb") as f:
            f.write(z.read("assets/minecraft/textures/block/lantern.png.mcmeta"))
        out(item, "fairy_spawn_egg", fairy_spawn_egg(tex("item/allay_spawn_egg")), tex("item/allay_spawn_egg"))
        fairy = fairy_entity()
        png(os.path.join(ASSETS, "textures", "entity", "fairy.png"), fairy.w, fairy.h, fairy.px)
        pairs.append((tex("entity/allay/allay"), fairy))
        write_data(z, c)
    path = PREVIEW or os.path.join(REPO, "build", "fae_preview.png")
    preview(path, pairs)
    print("fae done:", len(pairs), "textures; preview at", path)


if __name__ == "__main__":
    main()
