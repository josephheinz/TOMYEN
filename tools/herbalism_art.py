"""The herbalism set's pixel art (used by gen_herbalism.py): the wild herbs and garlic crop stages (vanilla flower and
crop style: one-pixel stems, darker outlines low down, light from the upper left), the item sprites (drawn in the
vanilla bottle, bowl and arrow shapes), the materials of the placed blocks' models, and the effect icons.

Every sprite is composed from small hand-placed stamps over a 16x16 (or 18x18) grid, with a short palette per
material and no noise: each pixel is put there on purpose."""

CLEAR = (0, 0, 0, 0)


def hexc(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


class Grid:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.px = [CLEAR] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        return self.px[y * self.w + x] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def stamp(self, x, y, rows, pal):
        """Draws rows of palette keys with the top-left at (x, y); '.' leaves what's there."""
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    self.set(x + dx, y + dy, pal[ch])
        return self

    def rows(self, rows, pal):
        assert len(rows) == self.h, len(rows)
        for r in rows:
            assert len(r) == self.w, (r, len(r))
        return self.stamp(0, 0, rows, pal)

    def flip(self):
        g = Grid(self.w, self.h)
        for y in range(self.h):
            for x in range(self.w):
                g.set(self.w - 1 - x, y, self.get(x, y))
        return g


def pal(**kw):
    return {k: hexc(v) for k, v in kw.items()}


# --------------------------------------------------------------------------------------------------- palettes
# Vanilla's flower greens (from the allium, carrots and lily of the valley), darkest to lightest.
GREEN = dict(D=0x1c4a1a, G=0x266325, g=0x3f8424, h=0x55ab2d, H=0x7cc84a)
# Taiga-darker greens for wolfsbane's leaves, a bluish cast.
DEEP = dict(D=0x173a22, G=0x22573a, g=0x2f7448, h=0x46955a, H=0x6db67a)
# The lead-grey leaves of radroot.
LEAD = dict(D=0x2b3536, G=0x47585a, g=0x65797a, h=0x8a9d9c, H=0xb2c3c0)


def herb(rows, colours):
    g = Grid()
    return g.rows(rows, pal(**colours))


# ======================================================================================================= herbs


def wolfsbane():
    """Aconite: a tall spike of hooded violet flowers up a single stem, smaller toward the tip, over a rosette of
    deeply cut dark leaves."""
    return herb([
        "................",
        ".......r........",
        "......osr.......",
        "......oqs.......",
        ".......o.rs.....",
        "....rs.goqr.....",
        "...oqrsg.oo.....",
        "...ooqrg..rs....",
        ".....oogoqqrs...",
        "....rs.goooqr...",
        "...oqrsg..oo....",
        "..h.ooqgh....h..",
        ".hgh..oggh.hgH..",
        ".Ggh..hgG.hghG..",
        "..GGghgGghgGG...",
        "....DGgGGgD.....",
    ], dict(DEEP, o=0x2e1f63, q=0x553a9c, r=0x7b5fcf, s=0xae98f0))


def yarrow():
    """Yarrow: a flat-topped umbel of tiny white florets (a soft cream with a pinkish-grey underside) on a straight
    stem, with feathery, finely cut leaves."""
    return herb([
        "................",
        "................",
        "...wcw.wwc.cw...",
        "..wwcwwcwwwcww..",
        "..cbwbcwbcwbbc..",
        "...bb.b.b.bb....",
        "....b.g.g.b.....",
        ".....bgggb......",
        ".......g........",
        ".h.h...g...h.h..",
        "..hgh..g..hgh...",
        ".h.gG..gh.Gg.h..",
        "...hGg.gGgGh....",
        "..h.GgGgGG.h....",
        "......GgG.......",
        "......DGD.......",
    ], dict(GREEN, w=0xf6f3ea, c=0xddd8cb, b=0xa89e9a))


def chamomile():
    """Chamomile: three little daisies at different heights, white rays round raised golden domes, on thin stems with
    threadlike leaves."""
    return herb([
        "................",
        "..........w.....",
        ".........wyw....",
        "...w.....wyYw...",
        "..wyw.....wc....",
        ".wyYyw.....g....",
        "..wcw......g....",
        "...g....w..g....",
        "...g...wyw.g....",
        "...g..wyYyw.g...",
        "....g..wcw..g...",
        "..h.g...g..g.h..",
        "...hg...g.g.h...",
        "..h.Gg..ggG.h...",
        "....GGggGGg.....",
        ".....DGgGD......",
    ], dict(GREEN, w=0xf8f8f2, c=0xd2d9d4, y=0xf5c22a, Y=0xffe25a))


def ginseng():
    """Ginseng: a whorl of five-fingered leaves on a short stalk, and at its heart a tight cluster of glossy red
    berries."""
    return herb([
        "................",
        "................",
        "................",
        "...H.......H....",
        "..hgh.....hgh...",
        ".hgGh.H.H.hGgh..",
        "..hgGhgHghGgh...",
        "...hGgrRrgGh....",
        ".H..hrRsRrh..H..",
        "hgh..rRrRrg.hgh.",
        ".hgGh.rqr.hGgh..",
        "..hgGg.g.gGgh...",
        "...hgGggggGh....",
        ".....GgGgG......",
        "......GgG.......",
        "......DGD.......",
    ], dict(GREEN, q=0x6e0f12, r=0xa8161b, R=0xd8282a, s=0xff8a7a))


def radroot():
    """Radroot: stiff lead-grey leaves fanning out of a pale, swollen root crown that glows a sickly green where it
    breaks the soil."""
    return herb([
        "................",
        "................",
        "................",
        "........H.......",
        "...H....h...H...",
        "...hh..Hg..hg...",
        "....hg.hg.hg....",
        "..H..hghggg..H..",
        "..hh.hgggGh.hg..",
        "...hgggGgGghg...",
        "....hgGgGGgh....",
        "......GgGG......",
        ".....lLWLl......",
        "....lLLeLLl.....",
        "....lLeYeLl.....",
        ".....llell......",
    ], dict(LEAD, l=0x8a8458, L=0xc4bc86, W=0xe2dcae, e=0xa9c86a, Y=0xd4ee8c))


def moonpetal():
    """Moonpetal: a single pale bloom of moonlit petals, silver-blue fading to white, cupping a glowing cyan heart,
    on a curving stem with two leaves; it glows faintly in the dark."""
    return herb([
        "................",
        "......w.........",
        "....wwmw.w......",
        "...wmmbmwmw.....",
        "..wmbbcCbbmw....",
        "...mbcCcbm......",
        "..wmbbcbbmww....",
        "...wmmbmmw......",
        ".....wmw........",
        ".......g........",
        "..H.....g.......",
        "..hgh...g..H....",
        "...hgg..g.hg....",
        "....GggGg.gh....",
        "......GgGG......",
        "......DGD.......",
    ], dict(GREEN, w=0xf2f6ff, m=0xc7d4f0, b=0x93a8de, c=0x69e6e0, C=0xd6fff9))


def water_mint():
    """Water mint: square stems with paired, toothed leaves, topped by round heads of lilac flowers."""
    return herb([
        "................",
        "..lLl.....lLl...",
        ".lLWLl...lLWLl..",
        ".lLLLl...lLLLl..",
        "..PlP.....PlP...",
        "...g.......g....",
        ".Hhg.......ghH..",
        "..hgg.....ggh...",
        "...g.......g....",
        "...g...H...g....",
        ".Hhg..hgh..ghH..",
        "..hgg..g..ggh...",
        "...gG..g..Gg....",
        "..hgGgGgGgGgh...",
        "....GGgGgGG.....",
        "......DGD.......",
    ], dict(GREEN, l=0xa57ccc, L=0xc9a4e8, W=0xe8d4fa, P=0x6e4c94))


def wild_garlic():
    """Wild garlic (ramsons): a starry white umbel above broad, lance-shaped leaves."""
    return herb([
        "................",
        "................",
        ".....w...w......",
        "...w.wcwcw.w....",
        "....wcyccywc....",
        "...wcw.y.wcw....",
        ".....w.g.w......",
        ".......g........",
        "...H...g...H....",
        "...hh..g..hh....",
        "....hg.g.gh..H..",
        ".H..hgGgGgh.hg..",
        ".hh.hgGgGgh.g...",
        "..hgggGgGgGgg...",
        "....GgGgGgG.....",
        "......DGD.......",
    ], dict(GREEN, w=0xf7f7f1, c=0xd8dcd2, y=0xe8e2a0))


def garlic_stage(stage):
    """Garlic on farmland: flat blue-green blades, taller each stage; ripe, a white bulb shows at the base."""
    # Garlic's leaves are a cooler, bluer green than carrot tops.
    colours = dict(D=0x1f4434, G=0x2d6448, g=0x438a5c, h=0x62ad74, H=0x8fcf96, b=0xf1ece0, W=0xffffff, B=0xb8aca8,
                   p=0xb898b8, n=0xb9a07a)
    stages = [
        [
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "..h.....h.....h.", "...g.h..g..h.g..", "...G.G..G..G.G..",
        ],
        [
            "................", "................", "................", "................",
            "................", "................", "................", "................",
            "................", "................", "...h.....h......", "....h..h..g...h.",
            "..h.g..g..g..hg.", "...gG.gG..Gg.g..", "...GG.GG..GG.G..", "...DG.DG..GD.D..",
        ],
        [
            "................", "................", "................", "................",
            "................", "................", ".h........h.....", "..h...h....g..h.",
            "..g...g....g.hg.", "...g..g..h.g.g..", "...g..gh.h.Gg...", "...Gh.Gg.g.G..h.",
            "..hGg.GG.gGG.hg.", "...GG.GG.GG..G..", "...GG.GG.GG..G..", "...DG.DG.GD..D..",
        ],
        [
            "................", "................", "................", "..H........H....",
            "..h...H....h..H.", "..g...h.....g.h.", "...g..g..H..gg..", "...g..g..h.Gg...",
            "...G..Gg.g.G..h.", "..hG..GG.gGG.hg.", "...Gg.GG.GG..G..", "...GG.GG.GG..G..",
            "...nn..G..nn....", "..bWbb...bWbb...", ".bWbbpb.bWbbpb..", ".BbbpbB.BbbpbB..",
        ],
    ]
    g = Grid()
    return g.rows(stages[stage], pal(**colours))


# ======================================================================================================= items

GLASS = dict(A=0x5d8fc2, B=0xb3cfec, C=0xd4e5f7, F=0x8badd0, K=0xd46d49, I=0xccafa5, L=0x973716, M=0xa94725)


def bottle(liquid, dark, light, garnish=None):
    """A vanilla glass bottle (the potion's outline and cork) filled with a tea: liquid shaded dark at the bottom
    and edges, a light glint, and a sprig of the herb tucked under the cork."""
    p = dict(GLASS, d=dark, m=liquid, l=light)
    rows = [
        "................",
        "................",
        ".......KKM......",
        "......CIIIB.....",
        "......BMMLB.....",
        ".......B.A......",
        ".......BmA......",
        "......CmmmA.....",
        ".....Cllmmmd....",
        "....Clmmmmmmd...",
        "....Blmmmmmmd...",
        "....Bmmmmmmmd...",
        "....Admmmmmdd...",
        ".....Addmddd....",
        "......AAAAA.....",
        "................",
    ]
    g = Grid().rows(rows, pal(**p))
    if garnish:
        g.stamp(*garnish)
    return g


def anti_radiation_tea():
    g = bottle(0x8fb34a, 0x5c7a2a, 0xc8e07a)
    # A radroot leaf stuck through the cork.
    g.stamp(9, 1, ["..h", ".g.", "g.."], pal(g=0x65797a, h=0x8a9d9c))
    return g


def calming_tea():
    g = bottle(0xe2b64c, 0xb07e22, 0xf8e09a)
    g.stamp(9, 0, [".w.", "wyw", ".w."], pal(w=0xf8f8f2, y=0xf5c22a))
    return g


def vigor_tonic():
    g = bottle(0xb8452a, 0x7c2414, 0xf08a5a)
    g.stamp(9, 1, ["hR", "g."], pal(g=0x3f8424, h=0x55ab2d, R=0xd8282a))
    return g


def moonpetal_tea():
    g = bottle(0x7fa8e8, 0x4a68b8, 0xd6fff9)
    g.stamp(9, 0, [".w.", "wcw", ".w."], pal(w=0xf2f6ff, c=0x69e6e0))
    return g


def garlic():
    """A garlic bulb: a plump, papery white bulb with faint violet streaks, a dry neck and a tuft of roots."""
    return Grid().rows([
        "................",
        "................",
        "........n.......",
        ".......nN.......",
        ".......Nn.......",
        "......BwwB......",
        "....BwwWwwbB....",
        "...BwWWwwpwbB...",
        "..BwWWwwpwwwbB..",
        "..BwWwwpwwwwbB..",
        "..BwwwpwwwwbbB..",
        "..BbwwpwwwbbbB..",
        "...BbbwwwbbbB...",
        "....BBbbbbBB....",
        ".....rrRrr......",
        "......r.r.......",
    ], pal(n=0xb9a07a, N=0x8e7650, w=0xf1ece0, W=0xffffff, b=0xd4c9c8, B=0x8f8486, p=0xb898b8, r=0xc7b28a, R=0x9e8a66))


def roasted_garlic():
    """Roasted: the bulb split open, its cloves gone golden brown and soft."""
    return Grid().rows([
        "................",
        "................",
        "........n.......",
        ".......nN.......",
        "......BwwB......",
        "....BwyYyywB....",
        "...ByYoyYoywB...",
        "..BwyoYyoYyowB..",
        "..BYyoyYyoyYwB..",
        "..BwYyoYyoYywB..",
        "..BbyYyoyYyobB..",
        "..BbbwyyyywbbB..",
        "...BbbwwwbbbB...",
        "....BBbbbbBB....",
        "................",
        "................",
    ], pal(n=0x7a5a34, N=0x5a3f22, w=0xe6dccb, b=0xc4b29a, B=0x7a6450, y=0xd9a24a, Y=0xf0c870, o=0xa86a2a))


def garlic_bread():
    """Bread split and toasted, buttered and flecked with garlic and green herbs."""
    return Grid().rows([
        "................",
        "................",
        "................",
        "................",
        "..........cCc...",
        "........cCYyCc..",
        "......cCYwyYgCc.",
        "....cCYyYgyYwyCc",
        "...cCgyYwyYyyYCc",
        "..cCYwyYygyYwCcd",
        "..cCyYgyYwyYCcd.",
        "..dcCYyyYyCccd..",
        "...dcCCyYCccd...",
        "....ddcCCccd....",
        "......dddd......",
        "................",
    ], pal(c=0xa8642a, C=0xc98a3c, d=0x7a4518, y=0xe8c46a, Y=0xf7dc8c, w=0xfff4d8, g=0x55ab2d))


def herb_stew():
    """A vanilla wooden bowl of green-gold stew, with garlic and a sprig floating in it."""
    return Grid().rows([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...aAAAAAAAAa...",
        "..aAsSssSssSAa..",
        ".aAsSgSswSsSsAa.",
        ".aBsSsGsSwsgSBa.",
        ".aBBsSsSsSsSBBa.",
        "..aBBBBBBBBBBa..",
        "...aaBBBBBBaa...",
        ".....aaaaaa.....",
        "................",
        "................",
    ], pal(a=0x4c2d10, A=0x8a5a2b, B=0x6b4219, s=0x9aa848, S=0xbcc760, g=0x3f8424, G=0x55ab2d, w=0xf1ece0))


def yarrow_poultice():
    """A folded cloth pad, soaked green with crushed yarrow, a white floret pinned to it."""
    return Grid().rows([
        "................",
        "................",
        "................",
        "....wcw.........",
        "...wcbcw........",
        "....wcw.........",
        "....OOOOOOOOO...",
        "...OccccccccO...",
        "...OcgGgGggcO...",
        "..OccGhGgGhccO..",
        "..OcgGgGhGgGcO..",
        "..OccgGgGgGccO..",
        "...OccccccccO...",
        "...OOOOOOOOOO...",
        "................",
        "................",
    ], pal(O=0x8f8a7a, c=0xe8e2cf, w=0xf6f3ea, b=0xa89e9a, g=0x4a8a34, G=0x5fa043, h=0x86c25a))


def wolfsbane_paste():
    """A twist of waxed cloth holding a dark violet paste, a bead of it at the top."""
    return Grid().rows([
        "................",
        "................",
        "................",
        "................",
        ".......sr.......",
        "......orqo......",
        ".....oqrqqo.....",
        "....oqrsrqqo....",
        "...oqrqqqqqpo...",
        "...oqqqqqqqpo...",
        "..ccoqqqqqpocc..",
        ".cCccoppppocccd.",
        ".cCCcccccccccdd.",
        "..ddcccccccddd..",
        "....dddddddd....",
        "................",
    ], pal(o=0x2e1f63, p=0x3f2a80, q=0x553a9c, r=0x7b5fcf, s=0xae98f0, c=0xc9b98e, C=0xe6d8ae, d=0x8f7f58))


def wolfsbane_arrow():
    """Vanilla's arrow, its head stained violet and a wolfsbane flower bound under it."""
    return Grid().rows([
        "................",
        "...........ssr..",
        "...........ssr..",
        "..........rqqo..",
        ".........qo.....",
        "........vqo.....",
        ".......wv.......",
        "......wv........",
        ".....wv.........",
        "....wv..........",
        "..FwF...........",
        "..FF............",
        ".FlF............",
        "Fl..............",
        "................",
        "................",
    ], pal(s=0xd8cdf5, r=0xae98f0, q=0x7b5fcf, o=0x2e1f63, v=0x553a9c, w=0x6b4a2b, F=0xe6e6e6, l=0xb8b8b8))


def mint_bait():
    """A ball of seed mash rolled in crushed water mint."""
    return Grid().rows([
        "................",
        "................",
        "................",
        "................",
        "................",
        "......gGGg......",
        "....gHhGhgGg....",
        "...gHhsGhGgGg...",
        "...GhGhGsGhGG...",
        "...gGsGhGgGsg...",
        "...GgGhGgsGGd...",
        "....dGgGGgGd....",
        ".....ddGGdd.....",
        "................",
        "................",
        "................",
    ], pal(g=0x3f8a52, G=0x2e6c40, h=0x5fae6e, H=0x8fd29a, s=0xd8c27a, d=0x1f4a2a))


def moonpetal_bait():
    """A pinch of ground moonpetal: pale glowing petal-dust heaped up, with a couple of motes above it."""
    return Grid().rows([
        "................",
        "................",
        "........C.......",
        "....c...........",
        "...........c....",
        "................",
        "................",
        "........w.......",
        ".......wmw......",
        "......wmbmw.....",
        ".....wmbcbmw....",
        "....wmbbCbbmw...",
        "...bmbcbbbcbmb..",
        "....bbbbbbbbb...",
        "................",
        "................",
    ], pal(w=0xf2f6ff, m=0xc7d4f0, b=0x93a8de, c=0x69e6e0, C=0xd6fff9))


def garlic_braid_item():
    """The Garlic Braid's inventory sprite: a plait of straw with bulbs either side, on a loop of string."""
    return Grid().rows([
        "......sss.......",
        ".....s...s......",
        "......sts.......",
        ".......t........",
        "....BwbtTbwB....",
        "...BwWwtbwWwB...",
        "...BbwbTtbwbB...",
        "....BBbtTbBB....",
        "...BwbBtbBbwB...",
        "..BwWwbTtbwWwB..",
        "..BbwwbtTbwwbB..",
        "...BBbBtbBbBB...",
        ".....BwTtwB.....",
        ".....BwbwbB.....",
        "......BBBB......",
        ".......rr.......",
    ], pal(s=0xe8e2cf, t=0xc9a85e, T=0xa8853f, w=0xf1ece0, W=0xffffff, b=0xd4c9c8, B=0x8f8486, r=0xc7b28a))


def wolfsbane_ward_item():
    """The Wolfsbane Ward's inventory sprite: a twig wreath set with violet blooms and tied with a white bow."""
    return Grid().rows([
        "................",
        ".....tTTTTt.....",
        "...tTTqrTTTTt...",
        "..tTqoqgt..TTt..",
        "..TTo..t....Tt..",
        ".tTg.........Tt.",
        ".tT.........qrt.",
        ".tT.........oqt.",
        ".tTr.........Tt.",
        ".tqo.........gt.",
        "..Tt........TT..",
        "..tTTg....gTTt..",
        "...tTTTwwTTTt...",
        ".....tTwWwt.....",
        "......w..w......",
        ".....w....w.....",
    ], pal(t=0x5a3f22, T=0x8a6438, o=0x2e1f63, q=0x553a9c, r=0x8e72dc, g=0x3f8424, w=0xe8e2cf, W=0xffffff))


# ======================================================================================================= effect icons (18x18)


def garlic_breath_icon():
    """A garlic bulb breathing out a wisp."""
    g = Grid(18, 18)
    g.rows([
        "..................",
        "..........ss......",
        ".........s..s.....",
        "..........s..s....",
        "...........ss.....",
        "........n.........",
        ".......nN.........",
        "......BwwB........",
        "....BwwWwwbB......",
        "...BwWWwwpwbB.....",
        "..BwWWwwpwwwbB....",
        "..BwWwwpwwwwbB....",
        "..BwwwpwwwwbbB....",
        "..BbwwpwwwbbbB....",
        "...BbbwwwbbbB.....",
        "....BBbbbbBB......",
        "......rRr.........",
        "..................",
    ], pal(n=0xb9a07a, N=0x8e7650, w=0xf1ece0, W=0xffffff, b=0xd4c9c8, B=0x6f6466, p=0xb898b8, r=0xc7b28a, R=0x9e8a66,
           s=0xd8e6c8))
    return g


def radioprotection_icon():
    """A lead-grey shield with a green leaf over it."""
    g = Grid(18, 18)
    g.rows([
        "..................",
        "..................",
        "...DDDDDDDDDDDD...",
        "...DHHhhhhhhhGD...",
        "...DHhhhhlhhhGD...",
        "...DHhhhlLlhhGD...",
        "...DhhhlLeLlhGD...",
        "...DhhlLeLeLlGD...",
        "...DhhlLeLeLlGD...",
        "....DhhlLeLlhD....",
        "....DhhhlLlhGD....",
        ".....DhhhlhGD.....",
        ".....DGhhhhGD.....",
        "......DGhhGD......",
        ".......DGGD.......",
        "........DD........",
        "..................",
        "..................",
    ], pal(D=0x2b3536, G=0x47585a, h=0x7c8f8f, H=0xb2c3c0, l=0x3f8424, L=0x7cc84a, e=0xb8e86a))
    return g


def calm_icon():
    """A chamomile flower."""
    g = Grid(18, 18)
    g.rows([
        "..................",
        "..................",
        "........ww........",
        "......w.ww.w......",
        ".....wwcwwcww.....",
        "......wcwwcw......",
        "...wwwcyyyycwww...",
        "..wwwcyYYYyycwww..",
        "....wcyYYyyycw....",
        "....wcyYyyyocw....",
        "..wwwcyyyyoocwww..",
        "...wwwcyoooccww...",
        "......wcccccw.....",
        ".....wwcwwcww.....",
        "......w.ww.w......",
        "........ww........",
        "..................",
        "..................",
    ], pal(w=0xf8f8f2, c=0xc8d0cc, y=0xf5c22a, Y=0xffe25a, o=0xc88a14))
    return g


# ======================================================================================================= model materials


def shade(base, t):
    """base (hex) brightened (t > 0) or darkened (t < 0) by t, 0 to 1."""
    c = hexc(base)
    if t >= 0:
        return tuple(int(c[k] + (255 - c[k]) * t) for k in range(3)) + (255,)
    return tuple(int(c[k] * (1 + t)) for k in range(3)) + (255,)


def material(base, column=None, row=None, extra=None):
    """A smooth 16x16 material: the base colour, shaded by column (a list of 16 offsets) and row (16 offsets)."""
    g = Grid()
    for y in range(16):
        for x in range(16):
            t = (column[x] if column else 0.0) + (row[y] if row else 0.0)
            g.set(x, y, shade(base, max(-0.9, min(0.9, t))))
    if extra:
        extra(g)
    return g


# A soft cylinder: brightest a third of the way across, falling off to both edges.
ROUND = [-0.30, -0.18, -0.06, 0.06, 0.14, 0.18, 0.14, 0.08, 0.02, -0.04, -0.10, -0.16, -0.22, -0.28, -0.34, -0.40]
TOP_LIT = [0.10 - 0.02 * y for y in range(16)]


def kettle_copper():
    """Polished copper: vanilla copper's warm orange, a soft round highlight, a darker band (the seam) every eight
    rows."""
    def seam(g):
        for x in range(16):
            for y in (7, 15):
                c = g.get(x, y)
                g.set(x, y, tuple(int(v * 0.82) for v in c[:3]) + (255,))
    return material(0xc87456, ROUND, TOP_LIT, seam)


def kettle_iron():
    return material(0x5a5f66, ROUND, None)


def herbal_wood():
    """Smooth spruce-brown wood with two straight grain lines."""
    def grain(g):
        for y in range(16):
            for x in (4, 11):
                c = g.get(x, y)
                g.set(x, y, tuple(int(v * 0.84) for v in c[:3]) + (255,))
    return material(0x6e4a28, [0.04 if x % 8 < 3 else -0.03 for x in range(16)], None, grain)


def mortar_stone():
    """Smooth pale stone, lit from above."""
    return material(0xa9a49a, [0.03 if x % 6 == 1 else 0.0 for x in range(16)], [0.12 - 0.025 * y for y in range(16)])


def herb_paste():
    """Crushed herbs, wet and green."""
    def flecks(g):
        for x, y in [(2, 3), (9, 2), (13, 7), (5, 10), (11, 12), (3, 14), (7, 6)]:
            g.set(x, y, hexc(0x9ad06a))
    return material(0x4f8a34, None, None, flecks)


def garlic_skin():
    """Papery white garlic: off-white, faint violet streaks running down, darker toward the bottom."""
    def streaks(g):
        for y in range(16):
            for x in range(1, 16, 3):
                c = g.get(x, y)
                g.set(x, y, (int(c[0] * 0.9), int(c[1] * 0.82), int(c[2] * 0.92), 255))
    return material(0xefe8dc, None, [0.15 - 0.03 * y for y in range(16)], streaks)


def braid_straw():
    """Dry garlic stems plaited: tan, with a diagonal twist mark every four pixels."""
    def twist(g):
        for y in range(16):
            for x in range(16):
                if (x + y) % 4 == 0:
                    c = g.get(x, y)
                    g.set(x, y, tuple(int(v * 0.8) for v in c[:3]) + (255,))
    return material(0xc9a85e, None, None, twist)


def ward_twig():
    def bark(g):
        for y in range(16):
            for x in range(16):
                if (x * 3 + y) % 7 == 0:
                    c = g.get(x, y)
                    g.set(x, y, tuple(int(v * 0.8) for v in c[:3]) + (255,))
    return material(0x6f4c2a, [0.05 if x % 4 == 1 else 0.0 for x in range(16)], None, bark)


def ward_bloom():
    """Wolfsbane's violet, lighter at the top of each hood."""
    return material(0x5f44a8, None, [0.25 - 0.06 * (y % 4) * 2 for y in range(16)])


def ward_leaf():
    return material(0x2f7448, [0.05 if x % 3 == 0 else -0.02 for x in range(16)], None)


def string_white():
    return material(0xe8e2cf, None, [0.05 if y % 2 == 0 else -0.04 for y in range(16)])


def nail_iron():
    return material(0x7d8288, ROUND, None)


def garlic_stage0():
    return garlic_stage(0)


def garlic_stage1():
    return garlic_stage(1)


def garlic_stage2():
    return garlic_stage(2)


def garlic_stage3():
    return garlic_stage(3)
