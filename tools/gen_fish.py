#!/usr/bin/env python3
"""Fish: item sprites, item models, item definitions, species data, lang, recipes and tags for the fishing catch.

Run from anywhere: python3 tools/gen_fish.py [preview.png]

Every fish is a flat 16x16 item sprite on the plain vanilla minecraft:item/generated parent, like vanilla's cod,
salmon, tropical fish and pufferfish. The sprites are hand-drawn pixel art, written below as 16 rows of characters:
each character names a colour in the fish's own palette ('.' is clear). They follow vanilla's fish: the head to the
lower left and the tail to the upper right, a dark outline (darker on the shadowed lower-right edge), light from the
top left, and a few clean colour ramps per fish with no per-pixel noise.

Every palette colour also has a role, which is how the cooked sprite is made from the raw one, the way vanilla cooks
cod and salmon: body colours ('b') are browned through a roast ramp in the same light-to-dark order, fins and tails
('f') go a darker brown, outlines ('o') a deep brown, and the rest ('k': eyes, bills, bone) is kept as it is.

Also written: data/mymod/fishing_species/<fish>.json (where each fish is caught, read by the fishing system), the
cooking recipes (furnace, smoker, campfire), the crafting uses, lang entries, and the vanilla #minecraft:fishes tag.

The preview (optional argument) shows every sprite at 1x and 8x, raw and cooked side by side, next to vanilla's cod
and salmon."""
import json, os, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gunkit import png, write  # noqa: E402

REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mymod")
DATA = os.path.join(RES, "data", "mymod")
MODELS = os.path.join(ASSETS, "models", "item")
ITEMS = os.path.join(ASSETS, "items")
TEXTURES = os.path.join(ASSETS, "textures", "item", "fish")
SPECIES = os.path.join(DATA, "fishing_species")
LANG = os.path.join(ASSETS, "lang", "en_us.json")


# ================================================================================================== colour helpers


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def steps(stops, t):
    """A posterised ramp: stops is a list of colours, t in 0..1 picks one."""
    t = max(0.0, min(0.999, t))
    return stops[int(t * len(stops))]


def ramp(stops, t):
    """A colour from a list of stops, linearly interpolated at t in 0..1."""
    t = max(0.0, min(1.0, t)) * (len(stops) - 1)
    k = min(len(stops) - 2, int(t))
    return mixc(stops[k], stops[k + 1], t - k)


# ======================================================================================================== sprites


class Sprite:
    """A fish's sprite: 16 rows of 16 palette characters, and a palette of comma-separated "char hex role" entries."""

    def __init__(self, name, title, rows, palette, cooked=None, title_cooked=None):
        self.name, self.title, self.cooked, self.title_cooked = name, title, cooked, title_cooked
        rows = [r for r in rows.strip("\n").split("\n")]
        assert len(rows) == 16, (name, len(rows))
        for r in rows:
            assert len(r.strip()) == 16, (name, r)
        self.rows = [r.strip() for r in rows]
        self.palette = {}
        for line in palette.strip().split("\n"):
            for entry in line.split(","):
                if entry.strip():
                    ch, hexc, role = entry.split()
                    self.palette[ch] = (C(hexc), role)
        for r in self.rows:
            for ch in r:
                assert ch == "." or ch in self.palette, (name, ch)

    def pixels(self, cooked=False):
        colours = self.cooked_palette() if cooked else {k: c for k, (c, _) in self.palette.items()}
        return [colours[ch] if ch != "." else (0, 0, 0, 0) for r in self.rows for ch in r]

    def cooked_palette(self):
        """Cooked the way vanilla cooks cod and salmon: the flesh browned through a roast ramp, keeping its own
        light-to-dark order (a hint of the raw hue keeps cooked fish telling apart), fins and tail a darker brown,
        outlines deep brown; eyes, bills and bone kept."""
        out = {}
        groups = {}
        for k, (c, role) in self.palette.items():
            groups.setdefault(role, []).append(k)
        for role, keys in groups.items():
            ls = [lum(self.palette[k][0]) / 255.0 for k in keys]
            lo, hi = min(ls), max(ls)
            for k, l in zip(keys, ls):
                c = self.palette[k][0]
                rel = (l - lo) / (hi - lo) if hi > lo else 0.5
                if role == "b":
                    out[k] = mixc(ramp(ROAST, 0.12 + 0.88 * (0.45 * l + 0.55 * rel)), c, 0.12)
                elif role == "f":
                    out[k] = mixc(ramp(ROAST_FIN, 0.5 * l + 0.5 * rel), c, 0.06)
                elif role == "o":
                    out[k] = ramp(ROAST_RIM, 0.4 * l + 0.6 * rel)
                else:
                    out[k] = c
        return out


# Roast ramps, from vanilla's cooked cod and cooked salmon.
ROAST = [C("6b3f24"), C("8a5532"), C("a66d40"), C("bb8552"), C("cfa06a"), C("dfbc8a"), C("ecd7b0")]
ROAST_FIN = [C("3f2416"), C("57321d"), C("6e4126"), C("865532")]
ROAST_RIM = [C("2e1a10"), C("4a2b19")]


# Palette roles: 'o' outline, 'b' body (browned when cooked), 'f' fin (darkened), 'k' kept as is.

SPRITES = [
    Sprite("brook_trout", "Brook Trout", """
................
...........oo...
..........ofgO..
..........oFfgO.
......oo.o3FffgO
......ddo32OFfgO
.....o4v3rbO.OO.
....o43322Fw....
...o4v3rb6wO....
..o433226O......
..ov3rb66O......
.o3E2266Fw......
.o32266Ow.......
.O226OO.........
..OOO...........
................
""", """
o 2f3a1c o, O 3a2414 o, 4 8e9a52 b, 3 66743a b, 2 9a9a5a b, v c4c070 b, r d0443a b, b 6a9ad0 b
6 d8853f b, 7 ead6b0 b, E 141414 k, d 4e5a2a f, F a85f2e f, f c8763c f, g e39a53 f, w f4eee0 f
""", "cooked_brook_trout", "Cooked Brook Trout"),

    Sprite("mud_catfish", "Mud Catfish", """
................
............oo..
...........ogfO.
...........ofgfO
...........FffO.
.........oo2OO..
.......oo32O....
.....oo3m22OF...
y...o433m2OF....
x..o4m3226OF....
.xoE33226O......
..O332266O......
.x.O2266O.......
x..xO66O........
y.x.xOO.........
..y.y...........
""", """
o 2a241c o, O 1a1612 o, 4 8a7c62 b, 3 655a48 b, 2 52483a b, m 3a3228 b, 6 c8b890 b, 7 e0d4b0 b
E 0e0e0e k, x 2a2018 k, y 5a4a38 k, F 3a3228 f, f 554a3a f, g 75684f f
""", "cooked_mud_catfish", "Cooked Mud Catfish"),

    Sprite("pike", "Pike", """
............og..
...........ofO..
...........oF.oO
........o..oFffO
........Foo2OOO.
.......oo32OF...
......o3s22OfO..
.....o3322O.....
....o3s2s6O.....
...o33226O......
...o32s6O.......
..oEY26O........
.o3M2OO.........
.oM2O...........
.OOO............
................
""", """
o 2a3a1e o, O 1a2410 o, 3 4a5c30 b, 2 6a7c40 b, s cfd38c b, 6 e0dcb8 b, E 141414 k, Y e8c040 k
M 1a2410 o, F 7a5a30 f, f 9a7444 f, g c09050 f
""", "cooked_pike", "Cooked Pike"),

    Sprite("mackerel", "Mackerel", """
..........oo....
..........ofO...
...........fO...
...........FffO.
.........oo2OOO.
......foob2O....
.....Fo3b22O....
....oo3b22O.....
....o3b226O.....
...o3b226O......
..o3b3226O......
..oE3226OO......
..o226O.........
.O2OOO..........
..O.............
................
""", """
o 1a3440 o, O 102028 o, 3 2f7a80 b, 2 a8c0c4 b, 6 d8e0e0 b, b 14262e b, E 0c0c0c k
F 2a4a54 f, f 4a6e78 f, g 7a9aa0 f
""", "cooked_mackerel", "Cooked Mackerel"),

    Sprite("swordfish", "Swordfish", """
..........o.....
.......o.ofO....
......oF..ofO...
.....oFF..oFO...
.....oFfO..FffO.
.....oFfooo2OOfO
......oo332O..O.
....oo43322O....
....o433226O....
...o4E3226O.....
...o332266O.....
...O32266OFf....
..bBO266OO......
.bB..OOO........
bB..............
t...............
""", """
o 1a1c33 o, O 0e0f1e o, 4 6a72b0 b, 3 34386a b, 2 7a84b0 b, 6 c4cadc b, 7 e8ebf2 b, E 0a0a12 k
B 5a5e78 k, b a8acc0 k, t dfe2ea k, F 262848 f, f 4a4e80 f
""", "swordfish_steak", "Swordfish Steak"),

    Sprite("anglerfish", "Anglerfish", """
................
................
..LLL...........
.L...L......oo..
.L...L.oooooFfO.
yYy.oL44w332OO..
.y..o4w43322O...
...o4E433w26O...
..o44w3322w6O...
..oT4T3w2667O...
..MMMMM26667O...
..mmmmM2w677O...
..TMmMT26677O...
...OTT6677OO....
.....OOOO.......
................
""", """
o 241c14 o, O 140f0a o, 4 6a5a42 b, 3 4e4232 b, 2 3a3126 b, 6 5a4a38 b, 7 6a5a48 b, w 7a6a4a b
M 2a0f12 k, m 6a2028 k, T e8e2cc k, L 5a4a36 k, Y fbffc0 k, y c8f060 k, E 101010 k
F 3a3126 f, f 4e4232 f
"""),

    Sprite("glowfin_tetra", "Glowfin Tetra", """
................
................
................
............oo..
...........oCLO.
.......L..ocCLO.
......cC..cCcO..
......ooooncO...
.....o433nrO....
....o43nnrrO....
...o4nn2rr6O....
...oE22266O.....
...o2666OcC.....
...OOOOO........
................
................
""", """
o 3a4644 o, O 222a28 o, 4 dfe6e4 b, 3 aab8b2 b, 2 cfd6d8 b, 6 e3e8e6 b, n 4ff7ff k, r d63a3a b
E 141414 k, c 49c9e8 k, C 7fe9ff k, L c8fbff k
"""),

    Sprite("parrotfish", "Parrotfish", """
..........o.....
.........ofO....
..........ofO...
......FFFFoFO...
.....FoooooFffO.
....Fo44332OOOfO
...oo443322O..O.
...o4433226O....
...o4332266O....
..o4E322667Of...
..op322667OF....
..Bp2p667O......
..BBp66OOO......
...bbOOfF.......
................
................
""", """
o 0f5a58 o, O 0a3e40 o, 4 7ae8d4 b, 3 37c2ae b, 2 1f9a95 b, 6 f4b3c6 b, 7 ffd6e2 b, p f07aa6 b
B f0ead8 k, b c8bfa8 k, E 1b1420 k, F c9547f f, f f08ab0 f
""", "cooked_parrotfish", "Cooked Parrotfish"),

    Sprite("ice_char", "Ice Char", """
................
...........oo...
........i.otWO..
......i.I.oTtWO.
.....jIjIo3TttWO
......jjj32OTtWO
.....o43w22O.OO.
....o4w322O.....
...o433226O.....
...o3w2266O.....
..o332266O......
..oE2266FW......
..o226OOW.......
.O2OOO..........
..O.............
................
""", """
o 26384e o, O 1a2636 o, 4 a8c4dc b, 3 6f8fae b, 2 c8a8b0 b, w f4fbff b, 6 e48a6e b, 7 f2b8a0 b
E 101820 k, i e8faff f, I a6e4ff f, j 6cb8e0 f, T 4a6a8c f, t 8aaccc f, W f4fbff f, F d97a5e f
""", "cooked_ice_char", "Cooked Ice Char"),

    Sprite("bog_eel", "Bog Eel", """
..............f.
.............oOf
............o3O.
...........o32O.
..........o3v6O.
.....oooooo326O.
....o3v33v3266O.
...o3322222266O.
..o32666666OOO..
..o326OOOOO.....
.o326O..........
.ov26O..........
o326O...........
oE26O...........
M66O............
.OO.............
""", """
o 2a2e16 o, O 1a1c0e o, 3 7a8440 b, 2 5e6830 b, 6 c9b56a b, v 2e3420 b, E e0d070 k, M 141408 o
f 6f7a3a f
""", "cooked_bog_eel", "Cooked Bog Eel"),

    Sprite("cave_blindfish", "Cave Blindfish", """
................
...........oo...
..........oLfO..
..........ofLfO.
.......Ff.oFffO.
.......ooo2OfO..
......o33lO.....
.....o3322O.....
....o33l26O.....
...og3226O.L....
..o33g26O.L.....
..o3226OLL......
..o226O.........
..OOOO..........
................
................
""", """
o 9a7e7c o, O 7a5e60 o, 3 fbeeea b, 2 f2dcd6 b, 6 e6c8c4 b, l c8a8a4 b, g e8707e b
F d8b4b0 f, f ecd4d0 f, L fbf4f2 f
""", "cooked_cave_blindfish", "Cooked Cave Blindfish"),

    Sprite("sculk_gulper", "Sculk Gulper", """
..............C.
.............cO.
............3O..
...........3O...
.....C.C..o3O...
.....c.co43O....
.....oo4c32O....
....o44432O.....
...o4c332O......
..oW3332c2O.....
..MTMTm222O.....
...MMmT2c6O.....
....MMm26O......
.....MT6O.......
......MO........
................
""", """
o 0a1e24 o, O 040c10 o, 4 1f5a66 b, 3 134048 b, 2 0c2c34 b, 6 1a4a54 b
M 2a0818 k, m 7a2048 k, T e8fcf8 k, c 29dfeb k, C a0fcff k, W 7af6ff k
"""),

    Sprite("pixie_minnow", "Pixie Minnow", """
................
................
......WW........
..WW.WVW...oo...
..WVWWVW..opPO..
...WVWVW..pPpO..
....WVVWoopOO...
.....WWoqpO.....
.....ooqppO.....
....o332pO......
...o3322O.......
...ott26O.......
...oET6O........
...OOOO.........
................
................
""", """
o 4a3470 o, O 2e1a4a o, 3 b48ae8 b, 2 8a62c8 b, q ffb8e8 b, p f08ad0 b, P c860a8 b, t 7ae0d8 b
T 3fb0b8 b, 6 e8dcff b, E 1e1030 k, W ffffff k, V d8c8ff k
"""),

    Sprite("moonscale_koi", "Moonscale Koi", """
...........oLO..
..........oLfLO.
..........oFLfLO
...........FfLfL
.......oooo2FfLO
.....oo4s32OFLLO
....o4s3gg2O.LO.
...o4s3ggg6O..O.
..o44s3gg66O....
..o4c32g66O.....
..ocE32667O.....
..o322667OLF....
...O2667OL......
..bOOOOO........
.b..b...........
................
""", """
o 7a8ab8 o, O 5a6890 o, 4 ffffff b, 3 eef1fa b, 2 dfe6f6 b, 6 d0d8ee b, 7 c4cce6 b, s c7d5f2 b
g f2c76a b
c c9dcff b, E 101830 k, b e6d3a8 k, F a8b8e0 f, f c8d4f0 f, L fdfdff f
"""),

    Sprite("magma_eel", "Magma Eel", """
...............Y
..............y.
........Y....O..
........y...o2O.
.........joo2O..
........o3Y2O...
.....Y.o332jO...
.....yj33226O...
.....o3Y226O....
..Y.o332j6O.....
..yj33226O......
..o3Y226O.......
.o332j6O........
.oe226O.........
.j226O..........
.OOOO...........
""", """
o 1e0c08 o, O 0e0504 o, 3 5a2418 b, 2 381410 b, 6 6a2410 b, j ff7a1a k, Y ffd24a k, y ff9a2a k
e ffe680 k
"""),

    Sprite("ember_puffer", "Ember Puffer", """
................
................
.......Y...fF...
..Y....y..fFf...
...y.oooooFf....
....o44j33O.....
...o443j322O....
...o4333j22O....
.YyoIP33j26OyY..
...o33222j6O....
...o32266j7O....
...mm26677O.....
.....OOOOO.y....
.......y....Y...
.......Y........
................
""", """
o 2a2020 o, O 140e0e o, 4 6a5a54 b, 3 4a3e3a b, 2 3a302c b, 6 5a3a2c b, 7 7a4a30 b
j ff8a2a k, I ffd27a k, P 1a0a00 k, m a04a30 k, Y ffe08a k, y ff9a2a k, F c86a1a f, f ff9a2a f
"""),

    Sprite("glowing_mutant_carp", "Glowing Mutant Carp", """
................
...........oo...
..........ofgO..
..........oFfgO.
.ooo....oooFffgO
o444o.oo433OOfgO
oE334o443322OO..
M2633443s322O...
.OO2633ll226O...
...OO3lLl266O...
.....O3ll2667O..
.....o4s22667O..
.....o3322667O..
.....oE2266OO...
......M266O.....
.......OOO......
""", """
o 3a3a1a o, O 22220e o, 4 b0a860 b, 3 8e8644 b, 2 6a6a2a b, s 5a5a22 b, 6 cfc88a b, 7 e0dca8 b
l 5fe02a k, L c8ff7a k, E d6ff6a k, M 1a1a0a o, F 5e5e2c f, f 7a7a3a f, g a6a65a f
"""),

    Sprite("starlit_gar", "Starlit Gar", """
................
............oo..
...........oLlO.
........L.ocLlO.
........cos2cO..
.......co326O...
.......o*26OcL..
......o326Oc....
.....o326O......
....o3s6O.......
...os26O........
...oE6O.........
..JKO...........
.JtO............
JKO.............
KO..............
""", """
o 0e1030 o, O 06081c o, 3 3a4490 b, 2 26306e b, 6 4a5aa8 b, s fff6c8 k, * ffffff k, E fff6c8 k
J 2a3270 b, K 5a6ab8 b, t e8e8ff k, c 6a7ad0 k, L c8d0ff k, l 9aa6ec k
"""),

    Sprite("abyssal_squidling", "Abyssal Squidling", """
...........oo...
..........ofgo..
.........ofggfO.
........o4fgfOO.
.......o4332O...
......o4p3c2O...
.....o4332p2O...
....o4c33266O...
...oEE3p266O....
...oEE3266O.....
..oaaaaaO.......
.a.a.a.aO.......
a..a.a..a.......
..a..a...x......
.x..a...........
x...x...........
""", """
o 1a0e30 o, O 10081e o, 4 8a6ad0 b, 3 6a3a9a b, 2 4a2a78 b, 6 3a2060 b, p ff5ad8 k, c 4af0ff k
E 1a0020 k, a 5a2a8a b, x ff5ad8 k, f 5a3a9a f, g 8a6ad0 f
"""),

    Sprite("ghoulfish", "Ghoulfish", """
................
............g...
............fg..
..........b.Sfg.
.......b...S....
........b.S.b...
.....b...S......
....o.b.S.b.....
...o4r.S...b....
..o433S.b.......
..o4E32r.b......
..o33226rO......
..MTMT66O.......
...O2266O.......
....OOOO........
................
""", """
o 2e3428 o, O 1a1e16 o, 4 a8b098 b, 3 7f8a6e b, 2 5e6a52 b, 6 8a9478 b, r b07a70 b, E 6ff0ff k
M 0e100c k, T e2dcc4 k, S f2eedc k, b d8d0b8 k, f 4a5440 f, g 7f8a6e f
"""),

    Sprite("old_boot", "Old Boot", """
.........k......
.......o.K......
....oooooK......
....o4l3lo......
....o4l3l2O.....
....o3l3l2O.....
....o32322O.....
....o32322O.....
....o3232w2O....
....o322223Ooo..
....o3222222w2O.
....o2222222222O
....OSSSSSSSSSSO
.....SsSSSSSSSs.
................
................
""", """
o 2a1a10 o, O 1a100a o, 4 8a6040 b, 3 6a4630 b, 2 4a3020 b, l d8c8a0 b, w 9a7050 b
S 2a2420 k, s 4a4038 k, k 4a8f3a k, K 2f6a2a k
"""),
]


# ======================================================================================================== species

OCEAN_WARM = ["minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"]
COLD = ["minecraft:frozen_river", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:cold_ocean",
        "minecraft:deep_cold_ocean", "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes",
        "minecraft:grove", "minecraft:snowy_beach", "minecraft:snowy_slopes"]
FEYWILD = ["mymod:feywild_glade", "mymod:feywild_mushroom_wilds"]
OVERWORLD = "minecraft:overworld"
NETHER = "minecraft:the_nether"

# name: (weight, difficulty, behaviour, rod tier, extra keys)
SPECIES_DATA = OrderedDict([
    ("brook_trout", (30, 1, "steady", 0, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "#minecraft:is_forest",
                                                                        "#minecraft:is_taiga", "minecraft:meadow",
                                                                        "minecraft:cherry_grove"]))),
    ("mud_catfish", (24, 2, "sinker", 0, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "minecraft:swamp",
                                                                        "minecraft:mangrove_swamp", "minecraft:plains",
                                                                        "minecraft:sunflower_plains",
                                                                        "minecraft:savanna"]))),
    ("pike", (12, 3, "darting", 1, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "#minecraft:is_forest",
                                                                  "#minecraft:is_taiga"]))),
    ("mackerel", (30, 1, "steady", 0, dict(dimension=OVERWORLD, biomes="#minecraft:is_ocean", count=[1, 2]))),
    ("swordfish", (3, 5, "darting", 3, dict(dimension=OVERWORLD, biomes=OCEAN_WARM + ["minecraft:deep_ocean"],
                                            time="day"))),
    ("anglerfish", (8, 3, "sinker", 1, dict(dimension=OVERWORLD, biomes="#minecraft:is_deep_ocean", time="night"))),
    ("glowfin_tetra", (14, 3, "darting", 0, dict(dimension=OVERWORLD, biomes=OCEAN_WARM + [
        "minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle", "minecraft:lush_caves"],
        count=[1, 2]))),
    ("parrotfish", (18, 2, "steady", 0, dict(dimension=OVERWORLD, biomes=OCEAN_WARM, time="day"))),
    ("ice_char", (22, 2, "steady", 0, dict(dimension=OVERWORLD, biomes=COLD))),
    ("bog_eel", (16, 3, "thrasher", 0, dict(dimension=OVERWORLD, biomes=["minecraft:swamp", "minecraft:mangrove_swamp"]))),
    ("cave_blindfish", (18, 2, "steady", 0, dict(dimension=OVERWORLD, biomes=["minecraft:lush_caves",
                                                                           "minecraft:dripstone_caves"], max_y=40))),
    ("sculk_gulper", (8, 4, "sinker", 2, dict(dimension=OVERWORLD, biomes="minecraft:deep_dark", max_y=-20))),
    ("pixie_minnow", (24, 3, "darting", 0, dict(dimension="mymod:feywild", biomes=FEYWILD, count=[1, 3]))),
    ("moonscale_koi", (5, 4, "floater", 3, dict(dimension="mymod:feywild", biomes=FEYWILD, time="night"))),
    ("magma_eel", (14, 4, "thrasher", 3, dict(fluid="lava", dimension=NETHER, biomes="#minecraft:is_nether"))),
    ("ember_puffer", (10, 3, "floater", 3, dict(fluid="lava", dimension=NETHER, biomes=[
        "minecraft:nether_wastes", "minecraft:basalt_deltas", "minecraft:crimson_forest"]))),
    # Uranium lies between y -48 and 0, in any Overworld biome: the carp lives in the water down there with it.
    ("glowing_mutant_carp", (10, 3, "thrasher", 1, dict(dimension=OVERWORLD, max_y=0))),
    ("starlit_gar", (3, 5, "darting", 4, dict(dimension=OVERWORLD, biomes=["#minecraft:is_river", "#minecraft:is_ocean"],
                                              time="night", weather="clear"))),
    ("abyssal_squidling", (2, 5, "thrasher", 4, dict(dimension=OVERWORLD, biomes="#minecraft:is_deep_ocean",
                                                     time="night"))),
    ("ghoulfish", (20, 4, "thrasher", 1, dict(dimension=OVERWORLD, time="night", weather="thunder"))),
    ("old_boot", (4, 1, "sinker", 0, dict(dimension=OVERWORLD))),
])

KEY_ORDER = ["item", "weight", "difficulty", "behavior", "min_rod_tier", "fluid", "dimension", "biomes", "time",
             "weather", "min_y", "max_y", "count"]


def species_json(name):
    weight, difficulty, behavior, tier, extra = SPECIES_DATA[name]
    obj = dict(item="mymod:" + name, weight=weight, difficulty=difficulty, behavior=behavior, min_rod_tier=tier)
    obj.update(extra)
    return OrderedDict((k, obj[k]) for k in KEY_ORDER if k in obj)


# ========================================================================================================= recipes


def recipe_with_unlock(name, body, category, unlock):
    write(os.path.join(DATA, "recipe", name + ".json"), body)
    adv = OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", OrderedDict([
            ("has_item", OrderedDict([("conditions", OrderedDict([("items", [OrderedDict([("items", unlock)])])])),
                                      ("trigger", "minecraft:inventory_changed")])),
            ("has_the_recipe", OrderedDict([("conditions", OrderedDict([("recipes", "mymod:" + name)])),
                                            ("trigger", "minecraft:recipe_unlocked")]))])),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", OrderedDict([("recipes", ["mymod:" + name])]))])
    write(os.path.join(DATA, "advancement", "recipes", category, name + ".json"), adv)


def cooking(raw, cooked):
    for kind, suffix, ticks in (("smelting", "", 200), ("smoking", "_from_smoking", 100),
                                ("campfire_cooking", "_from_campfire_cooking", 600)):
        recipe_with_unlock(cooked + suffix, OrderedDict([
            ("type", "minecraft:" + kind), ("category", "food"), ("cookingtime", ticks), ("experience", 0.35),
            ("ingredient", "mymod:" + raw), ("result", OrderedDict([("id", "mymod:" + cooked)]))]), "food", "mymod:" + raw)


def shapeless(name, ingredients, result, count=1, unlock=None, category="misc"):
    recipe_with_unlock(name, OrderedDict([
        ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
        ("result", OrderedDict([("count", count), ("id", result)]))]), category, unlock or ingredients[0])


def write_recipes():
    for f in SPRITES:
        if f.cooked:
            cooking(f.name, f.cooked)
    # Fish oil: rendered from oily fish into a bottle; burns like coal, and four of them make a canister of bio-crude
    # for the Refinery (kerosene, then rocket fuel or napalm).
    shapeless("fish_oil_from_mackerel", ["mymod:mackerel", "minecraft:glass_bottle"], "mymod:fish_oil")
    shapeless("fish_oil_from_bog_eel", ["mymod:bog_eel", "minecraft:glass_bottle"], "mymod:fish_oil")
    shapeless("fish_oil_from_magma_eel", ["mymod:magma_eel", "minecraft:glass_bottle", "minecraft:glass_bottle",
                                          "minecraft:glass_bottle"], "mymod:fish_oil", 3)
    shapeless("crude_oil_canister_from_fish_oil", ["mymod:empty_canister", "mymod:fish_oil", "mymod:fish_oil",
                                                   "mymod:fish_oil", "mymod:fish_oil"], "mymod:crude_oil_canister",
              unlock="mymod:fish_oil")
    # The anglerfish's lure lights ink.
    shapeless("glow_ink_sac_from_anglerfish", ["mymod:anglerfish", "minecraft:ink_sac", "minecraft:ink_sac"],
              "minecraft:glow_ink_sac", 2)
    shapeless("fae_dust_from_pixie_minnow", ["mymod:pixie_minnow"], "mymod:fae_dust")
    # Ice char freeze packed ice hard enough for the Froststeel quench.
    shapeless("blue_ice_from_ice_char", ["mymod:ice_char", "minecraft:packed_ice", "minecraft:packed_ice",
                                         "minecraft:packed_ice", "minecraft:packed_ice"], "minecraft:blue_ice")
    # A drowned soul wrung out of three ghoulfish, into the Foundry for soul steel.
    shapeless("soul_charge_from_ghoulfish", ["mymod:ghoulfish", "mymod:ghoulfish", "mymod:ghoulfish",
                                             "#minecraft:soul_fire_base_blocks"], "mymod:soul_charge")
    shapeless("blaze_powder_from_ember_puffer", ["mymod:ember_puffer"], "minecraft:blaze_powder", 2)
    shapeless("sculk_from_sculk_gulper", ["mymod:sculk_gulper"], "minecraft:sculk", 2)
    shapeless("leather_from_old_boot", ["mymod:old_boot"], "minecraft:leather")
    # The banker pays for the prize fish (listed in its trade sets, levels 3 and 4).
    for level, fish, emeralds in ((3, "moonscale_koi", 4), (4, "starlit_gar", 6)):
        write(os.path.join(DATA, "villager_trade", "banker", str(level), fish + "_emerald.json"), OrderedDict([
            ("gives", OrderedDict([("id", "minecraft:emerald"), ("count", emeralds)])), ("max_uses", 8),
            ("reputation_discount", 0.05), ("wants", OrderedDict([("id", "mymod:" + fish)])), ("xp", 15)]))


# ====================================================================================================== fish oil


def fish_oil_texture():
    """Vanilla's honey bottle, its honey recoloured to a clear greenish amber oil."""
    from gen_longswords import client_jar, read_png
    with zipfile.ZipFile(client_jar()) as z:
        w, h, px = read_png(z.read("assets/minecraft/textures/item/honey_bottle.png"))
    ramp = [C("5a5a1e"), C("8a8a2a"), C("b8b23a"), C("d8d266"), C("f0eca0")]
    out = []
    for k, c in enumerate(px):
        r, g, b, a = c
        if a and k // w >= 5 and r > g + 25 and r > b + 60:      # honey (below the cork): saturated orange
            out.append(steps(ramp, lum(c) / 255.0 * 1.1))
        else:
            out.append(c)
    png(os.path.join(TEXTURES, "fish_oil.png"), w, h, out)
    write(os.path.join(MODELS, "fish_oil.json"), OrderedDict([("parent", "minecraft:item/generated"),
                                                               ("textures", OrderedDict(layer0="mymod:item/fish/fish_oil"))]))
    write(os.path.join(ITEMS, "fish_oil.json"), {"model": OrderedDict([("type", "minecraft:model"),
                                                                        ("model", "mymod:item/fish_oil")])})



# ========================================================================================================= preview


def blit(out, ow, px, ox, oy, k, checker=None):
    for y in range(16 * k):
        for x in range(16 * k):
            c = px[(y // k) * 16 + x // k]
            if c[3]:
                out[(oy + y) * ow + ox + x] = tuple(c[:3]) + (255,)
            elif checker and ((x // k + y // k) % 2):
                out[(oy + y) * ow + ox + x] = checker


def preview(path):
    """Every sprite at 1x and 8x, raw then cooked, on the inventory slot grey; vanilla's cod and salmon first."""
    from gen_longswords import client_jar, read_png
    rows = []
    with zipfile.ZipFile(client_jar()) as z:
        def vanilla(n):
            return read_png(z.read("assets/minecraft/textures/item/%s.png" % n))[2]
        rows.append([vanilla("cod"), vanilla("cooked_cod")])
        rows.append([vanilla("salmon"), vanilla("cooked_salmon")])
    for s in SPRITES:
        rows.append([s.pixels(False)] + ([s.pixels(True)] if s.cooked else []))
    k, gap = 8, 8
    cell = 16 + gap + 16 * k + gap                  # 1x, then 8x
    cols = 3
    cw = 2 * cell + 2 * gap                          # raw and cooked
    W = cols * cw + gap
    H = ((len(rows) + cols - 1) // cols) * (16 * k + gap) + gap
    slot = (139, 139, 139, 255)
    out = [(60, 60, 66, 255)] * (W * H)
    for n, imgs in enumerate(rows):
        ox = gap + (n % cols) * cw
        oy = gap + (n // cols) * (16 * k + gap)
        for i, px in enumerate(imgs):
            x0 = ox + i * cell
            for y in range(16 * k):
                for x in range(16 * k):
                    out[(oy + y) * W + x0 + 16 + gap + x] = slot
            for y in range(16):
                for x in range(16):
                    out[(oy + y) * W + x0 + x] = slot
            blit(out, W, px, x0, oy, 1)
            blit(out, W, px, x0 + 16 + gap, oy, k, (147, 147, 147, 255))
    png(path, W, H, out)
    print("preview:", path)


# =========================================================================================================== main


def merge_lang(entries):
    with open(LANG) as fh:
        obj = json.load(fh, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(LANG, "w") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def generated(name):
    """A flat item: vanilla's item/generated parent with the sprite as its one layer, and its item definition."""
    write(os.path.join(MODELS, name + ".json"), OrderedDict([
        ("parent", "minecraft:item/generated"), ("textures", OrderedDict(layer0="mymod:item/fish/" + name))]))
    write(os.path.join(ITEMS, name + ".json"),
          {"model": OrderedDict([("type", "minecraft:model"), ("model", "mymod:item/" + name)])})


def main():
    preview_path = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else None
    del sys.argv[1:]                                 # gen_longswords.client_jar() reads argv[2] as a jar path
    lang = OrderedDict()
    tag_values = []
    for s in SPRITES:
        png(os.path.join(TEXTURES, s.name + ".png"), 16, 16, s.pixels(False))
        generated(s.name)
        lang["item.mymod." + s.name] = s.title
        if s.name != "old_boot":
            tag_values.append("mymod:" + s.name)
        if s.cooked:
            png(os.path.join(TEXTURES, s.cooked + ".png"), 16, 16, s.pixels(True))
            generated(s.cooked)
            lang["item.mymod." + s.cooked] = s.title_cooked
            tag_values.append("mymod:" + s.cooked)
        write(os.path.join(SPECIES, s.name + ".json"), species_json(s.name))
    fish_oil_texture()
    lang["item.mymod.fish_oil"] = "Fish Oil"
    lang["item.mymod.glowing_mutant_carp.desc"] = "Faintly radioactive"
    lang["item.mymod.sculk_gulper.desc"] = "Full of swallowed experience"
    merge_lang(lang)
    write(os.path.join(RES, "data", "minecraft", "tags", "item", "fishes.json"),
          OrderedDict([("replace", False), ("values", tag_values)]))
    write_recipes()
    if preview_path:
        preview(preview_path)


if __name__ == "__main__":
    main()
