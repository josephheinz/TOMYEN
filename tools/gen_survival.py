"""Builds the Scavenger's survival kit (com.jheinz.tomyen.survival): every item sprite, the bear trap and tripwire
alarm block models and textures, the Trapped and Stunned effect icons, block states, item definitions, sounds, lang,
and on the data side the recipes (with recipe-book advancements), block loot, the damage type and the tags the kit
owns. The Scrap armor is worn as a 3D model, built by tools/gen_scrap_armor.py.

Sprites are hand-placed pixel grids over short material ramps (outline darkest, light from the top left), drawn in
vanilla's item style; the Scrap armor's show the same junk its worn model is made of. The trap and alarm are detailed
block models built cube by cube here, checked for z-fighting (tools/zfight.py) before they're written.

Usage: python3 tools/gen_survival.py src/main/resources/assets/tomyen [--preview preview_survival.png]"""
import glob, json, math, os, struct, sys, zipfile
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import gen_fishing_rods as R      # Img, read_png, the preview font
from gunkit import png
import zfight

ASSETS = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else os.path.join(REPO, "src/main/resources/assets/tomyen")
PREVIEW = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))
TEX = os.path.join(ASSETS, "textures")
CLEAR = (0, 0, 0, 0)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


class Img(R.Img):
    def save(self, path):
        png(path, self.w, self.h, self.px)

    def copy(self):
        out = Img(self.w, self.h)
        out.px = list(self.px)
        return out


def client_jar():
    with open(os.path.join(REPO, "gradle.properties")) as f:
        version = next(line.split("=", 1)[1].strip() for line in f if line.startswith("minecraft_version="))
    roots = [REPO]
    marker = os.sep + os.path.join(".claude", "worktrees") + os.sep
    if marker in REPO:
        roots.append(REPO.split(marker)[0])
    candidates = [os.path.expanduser(os.path.join("~", ".gradle", "caches", "fabric-loom", version, "minecraft-client.jar"))]
    for root in roots:
        candidates += [p for p in sorted(glob.glob(os.path.join(root, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft",
                                                              "minecraft-clientOnly-*", version, "*.jar"))) if "sources" not in p]
    for path in candidates:
        if os.path.isfile(path):
            return path
    sys.exit(f"no Minecraft {version} client jar found; run ./gradlew build first")


JAR = zipfile.ZipFile(client_jar())


def vanilla(path):
    """A vanilla texture. Also honours tRNS on greyscale and RGB PNGs (vanilla\x27s leather armor sprites are greyscale
    with a transparent grey), which read_png doesn\x27t."""
    data = JAR.read("assets/minecraft/textures/" + path + ".png")
    src = R.read_png(data)
    out = Img(src.w, src.h)
    out.px = list(src.px)
    colour, trns, pos = None, None, 8
    while pos < len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            colour = body[9]
        elif kind == b"tRNS":
            trns = body
        pos += 12 + n
    if trns is not None and colour == 0:
        grey = struct.unpack(">H", trns[:2])[0]
        out.px = [CLEAR if p[:3] == (grey,) * 3 else p for p in out.px]
    elif trns is not None and colour == 2:
        key = struct.unpack(">HHH", trns[:6])
        out.px = [CLEAR if p[:3] == key else p for p in out.px]
    return out


# ======================================================================================================= palettes
# Each ramp runs from its outline (darkest) to its brightest highlight.

STEEL = [hexc(c) for c in ("1c1f23", "363b41", "565c63", "7b828a", "a5acb3", "d2d7dc")]
RED = [hexc(c) for c in ("2a0e0d", "5e1814", "8c2620", "b8372b", "d9604a")]
LEATHER = [hexc(c) for c in ("2b170c", "4f2d16", "6e421f", "8e5a2c", "ad7640", "c99459")]
SCRAP = [hexc(c) for c in ("23272a", "41484c", "5f676b", "838b8e", "a8afb1", "c9ced0")]
RUST = [hexc(c) for c in ("5a2c14", "8a4522", "b3622f", "cf8448")]
COPPER = [hexc(c) for c in ("6b3218", "a85a30", "d88a58", "f2b98c")]
BRASS = [hexc(c) for c in ("4a3410", "7d5a1c", "b08a32", "dcb95a", "f3dc94")]
CLOTH = [hexc(c) for c in ("5d574d", "958d7f", "c3bba9", "e2dccc", "f8f5ec")]
CROSS = [hexc(c) for c in ("6a1010", "a81e1e", "d23a30")]
GLASS = [hexc(c) for c in ("2f4a55", "5f8a96", "9cc6cf", "d6f0f2")]
OIL = [hexc(c) for c in ("5a3608", "8f5a10", "c08a1e", "e6b443", "f7da86")]
OLIVE = [hexc(c) for c in ("24281a", "3d4429", "56603a", "707c4c", "8d9a63")]
STRING = [hexc(c) for c in ("4a4236", "8a8070", "cfc6b4")]
SPARK = [hexc(c) for c in ("c2410c", "f59e0b", "fde68a", "fffbe6")]


def sprite(rows, palette):
    """A 16x16 sprite from 16 strings: each character is a palette key, '.' is clear."""
    img = Img(16, 16)
    assert len(rows) == 16, len(rows)
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.set(x, y, palette[ch])
    return img


def keys(**ramps):
    """Palette keys: for ramp NAME=list, characters given as NAME's letters map 0.. along the ramp."""
    out = {}
    for letters, ramp in ramps.items():
        for i, ch in enumerate(letters):
            out[ch] = ramp[i]
    return out


# ======================================================================================================= sprites

def crowbar():
    """A red-painted pry bar on the diagonal: a chisel end at the bottom left, a gooseneck claw hooking over at the
    top right; bare steel at both working ends."""
    p = keys(KDSMLW=STEEL, ZBRrH=RED)
    return sprite([
        "................",
        "..........KKKK..",
        ".........KLWWMK.",
        "........KLMKKSMK",
        ".......KLMK.KMDK",
        "......KrRZ..KDK.",
        ".....KrRZ....K..",
        "....KrRZ........",
        "...KrRZ.........",
        "..KrRZ..........",
        ".KHrZ...........",
        "KLMZ............",
        "KWMK............",
        "KMDK............",
        ".KK.............",
        "................",
    ], p)


def bandage():
    """A roll of bandage standing on end (its top showing the wound layers round the hollow core), the loose end
    unrolled across the ground in front of it, pinned with a little brass safety pin."""
    p = keys(KDMLW=CLOTH, g=[BRASS[3]], h=[BRASS[2]], A=[CROSS[1]])
    return sprite([
        "................",
        "................",
        "......KKKKK.....",
        "....KKWWWWWKK...",
        "...KWWLMMMLWWK..",
        "...KWLMDDDMLWK..",
        "...KWWLMMMLWLK..",
        "...KMWWWWWWLMK..",
        "...KWWWLALLLMK..",
        "...KWWWAAALLMK..",
        "...KWWWLALLMMK..",
        "...KWWWLLLLMDK..",
        "..KKKWWLLLLMDK..",
        ".KWWLKKKKKKKKK..",
        "KWLLMMghK.......",
        ".KKKKKKKK.......",
    ], p)


def first_aid_kit():
    """A battered white tin with a red cross, a dark carry handle on top and a brass latch."""
    p = keys(KDMLW=CLOTH, ABC=CROSS)
    p.update(keys(klmn=STEEL[:4], bgh=BRASS[1:4]))
    return sprite([
        "................",
        "................",
        "......kkkkk.....",
        ".....kmlllmk....",
        ".....kl...lk....",
        ".KKKKkKKKKKkKKK.",
        ".KWWWWWWWWWWWWK.",
        ".KWLLLLBBLLLLMK.",
        ".KWLLLLBCLLLLMK.",
        ".KWLLBBBCBBLLMK.",
        ".KWLLAAABAALLMK.",
        ".KWLLLLABLLLLMK.",
        ".KWLLLLAALLLLMK.",
        ".KMMMMMgbMMMMDK.",
        ".KKKKKKKKKKKKKK.",
        "................",
    ], p)


def molotov():
    """A bottle of amber fish oil with a rag stuffed in its neck, the rag's end hanging over the side."""
    p = keys(KMLW=GLASS, abcde=OIL, rRsS=CLOTH[:4], tu=RUST[1:3])
    return sprite([
        "................",
        "......ss........",
        ".....sSSs.......",
        "......SRst......",
        "......KRK.u.....",
        "......KRKt......",
        "......KWK.......",
        ".....KWLLK......",
        "....KWLMMLK.....",
        "...KWLMMMMLK....",
        "...KLdeedccK....",
        "...KLdddcccK....",
        "...KMcccbbbK....",
        "...KMbbbbaaK....",
        "....KKaaaaK.....",
        ".....KKKKK......",
    ], p)


def pipe_bomb():
    """A stub of iron pipe with screw caps on both ends, lying on the diagonal, its fuse wick sparking at the top."""
    p = keys(KDMLW=STEEL[:5], abc=STRING, xyzw=SPARK, rR=RUST[1:3])
    return sprite([
        "................",
        "............x...",
        "...........xzx..",
        "............wy..",
        "...........ab...",
        "..........ab....",
        ".......KKKb.....",
        "......KLWMDK....",
        ".....KLWMMMDK...",
        "....KLWMMMDDK...",
        "...KLLMMMRDK....",
        "..KLWMMMDDK.....",
        "..KLMMMDDK......",
        "..KDMMDDK.......",
        "...KKDDK........",
        ".....KK.........",
    ], p)


def disc(img, cx, cy, r, ramp, light=(-0.55, -0.83)):
    """A shaded disc: outline round the rim, then the ramp stepped by how squarely each pixel faces the light (from the
    top left), as a ball of cloth would catch it. Smooth bands, no dither."""
    inside = lambda x, y: (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r
    for y in range(img.h):
        for x in range(img.w):
            if not inside(x, y):
                continue
            if not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.set(x, y, ramp[0])
                continue
            nx, ny = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            facing = 0.55 - 0.75 * (nx * light[0] + ny * light[1]) - 0.25 * math.hypot(nx, ny)
            step = 1 + int(max(0.0, min(0.999, 1.0 - facing)) * (len(ramp) - 1))
            img.set(x, y, ramp[len(ramp) - step] if step < len(ramp) else ramp[1])


def canteen():
    """A round army canteen in an olive cloth cover: a seam down the middle with a brass snap, the steel neck and screw
    cap on top, and a leather carry strap looped through the cap's chain."""
    img = Img(16, 16)
    disc(img, 8.0, 9.5, 6.3, OLIVE)
    p = keys(KDMLW=STEEL[:5], abcdef=LEATHER, g=[BRASS[2]], h=[BRASS[4]], o=[OLIVE[0]], s=[OLIVE[1]])
    over = [
        "......KKKK......",
        ".....KWWLMK.....",
        ".....KLLMDK.....",
        "....bKKDDKKb....",
        "...b.oKKKKo.b...",
        "..b.....s....b..",
        "........s.......",
        "........s.......",
        "........h.......",
        "........g.......",
        "........s.......",
        "........s.......",
        "........s.......",
        "........s.......",
        "................",
        "................",
    ]
    for y, row in enumerate(over):
        for x, ch in enumerate(row):
            if ch != "." and (ch not in "sgh" or img.get(x, y)[3]):
                img.set(x, y, p[ch])
    return img


def satchel():
    """A squat leather satchel with a buckled flap, a strap arching over the top and a tech-scrap patch stitched on."""
    p = keys(abcdef=LEATHER, ghij=BRASS[1:5], KDMLW=SCRAP[:5], r=[RUST[2]], s=[STRING[2]])
    return sprite([
        "................",
        ".....aaaaaa.....",
        "....ad....da....",
        "...ad......da...",
        "...ac......ca...",
        "..aaaaaaaaaaaa..",
        ".aeffffffffffea.",
        ".aeeeeeggeeeeda.",
        ".adddddhjddddca.",
        ".aaaaaaggaaaaaa.",
        ".acdddddKKKKdca.",
        ".acddddKLMMDKca.",
        ".acddddKMrMDKca.",
        ".acccccKDDDDKba.",
        ".abbbbbbKKKKbba.",
        "..aaaaaaaaaaaa..",
    ], p)


def scrap_armor_sprites():
    """The Scrap armor's sprites, drawn as the junk the worn pieces are made of (see gen_scrap_armor.py): a
    galvanized pot helmet with a rusty brim, welding goggles and an antenna; a chestplate of galvanized steel and
    copper over drum iron with a hubcap and a tire-and-saw-blade shoulder, a road-sign belly and a bandolier; tin-can
    and pipe leggings with a soda-can knee and bottle caps; odd boots on tire soles."""
    p = keys(KDMLWH=SCRAP, xrRo=RUST, qcC=COPPER[1:4], bB=BRASS[2:4], kl=[hexc("1a1a1c"), hexc("333336")],
             s=[STEEL[4]], S=[STEEL[3]], e=[RED[3]], g=[hexc("22422f")], G=[hexc("3c6650")], h=[hexc("9cc0ac")],
             d=[hexc("2c201a")], t=[hexc("4a3a2e")], Y=[hexc("c6a44c")], y=[hexc("2e2c2a")], E=[hexc("ac2a26")],
             F=[hexc("6e1a1c")], u=[hexc("3e5a7e")], U=[hexc("587ca2")], P=[hexc("b02a26")], Q=[hexc("2c56a0")])
    rows = {
        "helmet": [
            "................",
            "..........e.....",
            "..........D.....",
            "...KKKKKKKKKK...",
            "..KHWWWWWWWWLK..",
            "..KWccLWLLLLMKkk",
            "..KLqcMLLLLLMKl.",
            "..KLMMLLLLLMDK..",
            ".KoRoRRoRRoRRoK.",
            ".KxrrRrrrRrrrxK.",
            "..KMKKbBKKbBbKS.",
            "..KLKbGgbKbhgbK.",
            "..KDKbgGbKbggbK.",
            "..KKKKbbKKKbbbK.",
            "................",
            "................",
        ],
        "chestplate": [
            "..........KsK...",
            ".KKKKK...KsSsK..",
            "KMWHWMK.KkSSSkkK",
            "KWHsHWKKKKkkkkkK",
            "KMWHWMDLLcCCckkK",
            ".KKKKKWLLcCccKK.",
            "..KMLLLLMcCccqK.",
            "..KDLLLLMccccqK.",
            "..KDMMLMDqcccqK.",
            "..KYYyyYYyyYYyK.",
            "..KYyyYYyyYYyoK.",
            "..KlLMlLMDlLMlK.",
            "..KlMDlMDDlMDlK.",
            "..KKKKKKKKKKKKK.",
            "................",
            "................",
        ],
        "leggings": [
            "................",
            "..KKKKKKKKKKKK..",
            "..KddtdSsSdtdK..",
            "..KxRRoKKdtddK..",
            "..KRoRrK.KxrRK..",
            "..KWHWWK.KDMDK..",
            "..KLMLMK.KMDxK..",
            "..KddddK.KddtK..",
            "..KWHWWK.KMDDK..",
            "..KMLLMK.KDrMK..",
            "..KEPEEK.KtPtK..",
            "..KPEEFK.KQtYK..",
            "..KEEFFK.KtttK..",
            "..KKKKKK.KKKKK..",
            "................",
            "................",
        ],
        "boots": [
            "................",
            "................",
            "................",
            "................",
            "...KKKK..KKKK...",
            "...KWLK..KUuK...",
            "...KLMK..KuUK...",
            "...KddKK.KCqKK..",
            "...KMLDK.KUuKK..",
            "..KKLMDK.KuuKK..",
            ".KdtddDK.KdtxK..",
            "KLWtdddKKoRdddK.",
            "KMLMddtKKRxddtK.",
            "KklklklKKklklklK",
            ".KKKKKKK.KKKKKK.",
            "................",
        ],
    }
    out = {}
    for piece, grid in rows.items():
        out[piece] = sprite(grid, p)
    # The chestplate's bandolier: a dark strap from the right shoulder to the left hip, drawn over the plates.
    chest = out["chestplate"]
    for x, y in ((5, 4), (6, 5), (7, 6), (8, 7), (9, 8), (10, 9), (11, 10), (12, 11)):
        chest.set(x, y, LEATHER[0])
        if chest.get(x + 1, y)[3] and (x + 1, y) != (13, 11):
            chest.set(x + 1, y, LEATHER[1])
    return out


SPRITES = OrderedDict()


def build_sprites():
    SPRITES["crowbar"] = crowbar()
    for piece, img in scrap_armor_sprites().items():
        SPRITES["scrap_" + piece] = img
    SPRITES["bandage"] = bandage()
    SPRITES["first_aid_kit"] = first_aid_kit()
    SPRITES["molotov_cocktail"] = molotov()
    SPRITES["pipe_bomb"] = pipe_bomb()
    SPRITES["canteen"] = canteen()
    SPRITES["scavengers_satchel"] = satchel()


# ======================================================================================================= block art
# Each block texture is a 16x16 sheet of flat material swatches (no noise: Minecraft's face shading does the rest);
# model faces map onto the swatch for their material and side.

SWATCH = {
    # name: (u0, v0, u1, v1)
    "iron_top": (0, 0, 4, 4), "iron_side": (4, 0, 8, 4), "iron_dark": (8, 0, 12, 4), "tooth": (12, 0, 16, 4),
    "coil": (0, 4, 4, 8), "rust": (4, 4, 8, 8), "chain": (8, 4, 12, 8), "tooth_tip": (12, 4, 16, 8),
    "pan": (0, 8, 8, 16), "stamp": (8, 8, 16, 16),
}
ALARM_SWATCH = {
    "wood_top": (0, 0, 4, 4), "wood_side": (4, 0, 8, 4), "wood_dark": (8, 0, 12, 4), "steel": (12, 0, 16, 4),
    "brass_top": (0, 4, 4, 8), "brass_side": (4, 4, 8, 8), "brass_dark": (8, 4, 12, 8), "steel_dark": (12, 4, 16, 8),
    "lamp_off": (0, 8, 4, 12), "lamp_on": (4, 8, 8, 12), "grille": (8, 8, 12, 12), "hook": (12, 8, 16, 12),
    "bell_mouth": (0, 12, 4, 16), "panel": (4, 12, 12, 16), "brass_lip": (12, 12, 16, 16),
}


def fill(img, rect, colour_at):
    u0, v0, u1, v1 = rect
    for y in range(v0, v1):
        for x in range(u0, u1):
            img.set(x, y, colour_at(x - u0, y - v0, u1 - u0, v1 - v0))


def flat(c):
    return lambda x, y, w, h: c


def vramp(top, bottom):
    return lambda x, y, w, h: mixc(top, bottom, y / max(1, h - 1))


def trap_texture(silver):
    """The bear trap's sheet: blued iron jaws, a dished pan with a stamped centre, coiled hinge springs and a rusty
    base bar. The silver trap's teeth (and the pan's stamp, a small cross) are bright silver."""
    img = Img(16, 16)
    teeth = [hexc(c) for c in ("8f9cb5", "c4cee0", "eef3fb")] if silver else [STEEL[3], STEEL[4], STEEL[5]]
    fill(img, SWATCH["iron_top"], vramp(STEEL[3], STEEL[2]))
    fill(img, SWATCH["iron_side"], flat(STEEL[2]))
    fill(img, SWATCH["iron_dark"], flat(STEEL[1]))
    fill(img, SWATCH["tooth"], vramp(teeth[2], teeth[1]))
    fill(img, SWATCH["tooth_tip"], flat(teeth[2]))
    fill(img, SWATCH["coil"], lambda x, y, w, h: STEEL[3] if y % 2 == 0 else STEEL[1])
    fill(img, SWATCH["rust"], vramp(RUST[2], RUST[1]))
    fill(img, SWATCH["chain"], lambda x, y, w, h: STEEL[2] if (x + y) % 4 < 2 else STEEL[1])

    def pan(x, y, w, h):
        d = math.hypot(x + 0.5 - w / 2, y + 0.5 - h / 2)
        if d > 3.6:
            return STEEL[2]
        if d > 2.6:
            return STEEL[4]
        if d > 1.2:
            return STEEL[3]
        return teeth[1] if silver else STEEL[2]
    fill(img, SWATCH["pan"], pan)

    def stamp(x, y, w, h):
        if silver and (x in (3, 4) and 1 <= y <= 6 or y in (2, 3) and 1 <= x <= 6):
            return teeth[2]
        return STEEL[3]
    fill(img, SWATCH["stamp"], stamp)
    return img


def alarm_texture():
    """The alarm's sheet: oak base and post (plain grain stripes), brass bell, steel electronics box with a grille,
    and its lamp, dark and lit."""
    img = Img(16, 16)
    oak = [hexc(c) for c in ("4c3218", "6b4a26", "8c6538", "a8804a", "c09a5e")]
    fill(img, ALARM_SWATCH["wood_top"], lambda x, y, w, h: oak[3] if y % 3 else oak[2])
    fill(img, ALARM_SWATCH["wood_side"], lambda x, y, w, h: oak[2] if x % 3 else oak[1])
    fill(img, ALARM_SWATCH["wood_dark"], flat(oak[1]))
    fill(img, ALARM_SWATCH["steel"], vramp(STEEL[3], STEEL[2]))
    fill(img, ALARM_SWATCH["steel_dark"], flat(STEEL[1]))
    fill(img, ALARM_SWATCH["brass_top"], flat(hexc("c99a3a")))
    fill(img, ALARM_SWATCH["brass_side"], vramp(hexc("b8862c"), hexc("8a6018")))
    fill(img, ALARM_SWATCH["brass_dark"], flat(BRASS[1]))
    fill(img, ALARM_SWATCH["brass_lip"], vramp(hexc("d6aa48"), hexc("a37424")))
    fill(img, ALARM_SWATCH["bell_mouth"], lambda x, y, w, h: BRASS[0] if 0 < x < w - 1 and 0 < y < h - 1 else BRASS[1])
    fill(img, ALARM_SWATCH["lamp_off"], lambda x, y, w, h: hexc("5a1512") if 0 < x < w - 1 and 0 < y < h - 1 else hexc("3a0c0a"))
    fill(img, ALARM_SWATCH["lamp_on"], lambda x, y, w, h: hexc("ffd2b0") if 1 <= x <= 2 and 1 <= y <= 2 else hexc("ff5a3a"))
    fill(img, ALARM_SWATCH["grille"], lambda x, y, w, h: STEEL[1] if y % 2 else STEEL[3])
    fill(img, ALARM_SWATCH["hook"], flat(BRASS[3]))

    def panel(x, y, w, h):
        if y == 0:
            return STEEL[4]
        if (x, y) in ((1, 2), (6, 2)):
            return COPPER[2]
        if 2 <= x <= 5 and y == 2:
            return hexc("2e3a2a")
        return STEEL[3]
    fill(img, ALARM_SWATCH["panel"], panel)
    return img


def effect_icon(kind):
    """18x18 effect icons: Trapped is a pair of sprung jaws, Stunned a ring of yellow sparks."""
    img = Img(18, 18)
    if kind == "trapped":
        rows = [
            "..................",
            "..................",
            "..................",
            "...K..........K...",
            "..KLK........KLK..",
            "..KLK.K.KK.K.KLK..",
            "..KLKKLKLLKLKKLK..",
            "..KLMLWLWWLWLMLK..",
            "...KLMMMMMMMMLK...",
            "....KKLMMMMLKK....",
            "......KKKKKK......",
            "..ccKDDDDDDDDKcc..",
            ".cCCKMMMMMMMMKCCc.",
            "..ccKKKKKKKKKKcc..",
            "..................",
            "..................",
            "..................",
            "..................",
        ]
        p = keys(KDMLW=STEEL[:5], cC=SCRAP[2:4])
    else:
        rows = [
            "..................",
            "........y.........",
            "...y...yWy....y...",
            "..yWy...y....yWy..",
            "...y..........y...",
            "....OOOOOOOOOO....",
            "...OoooooooooooO..",
            "...Oo........oO...",
            "....OOOOOOOOOO....",
            "..................",
            "..y............y..",
            ".yWy..........yWy.",
            "..y.....y......y..",
            "........yWy.......",
            ".........y........",
            "..................",
            "..................",
            "..................",
        ]
        p = {"y": SPARK[1], "W": SPARK[3], "O": SPARK[0], "o": SPARK[2]}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.set(x, y, p[ch])
    return img


# ======================================================================================================= models

def el(name, frm, to, faces, swatch, cull_floor=True):
    """A model element: faces is {direction: swatch name}; each face's uv is its swatch's rectangle."""
    out = OrderedDict([("name", name), ("from", [round(v, 4) for v in frm]), ("to", [round(v, 4) for v in to]), ("faces", OrderedDict())])
    for direction, mat in faces.items():
        face = OrderedDict([("uv", list(swatch[mat])), ("texture", "#tex")])
        if cull_floor and direction == "down" and frm[1] == 0:
            face["cullface"] = "down"
        out["faces"][direction] = face
    return out


def mat(top, side, bottom=None):
    bottom = bottom or side
    return OrderedDict([("north", side), ("east", side), ("south", side), ("west", side), ("up", top), ("down", bottom)])


IRON = mat("iron_top", "iron_side", "iron_dark")
TOOTH = mat("tooth_tip", "tooth", "tooth")
COIL = mat("iron_top", "coil", "iron_dark")
BASE = mat("rust", "iron_dark", "iron_dark")


def jaw_cells():
    """The open jaws as a pixel ring round the pan, split into the north and south jaw; the hinge rows are left out."""
    cells = []
    for z in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, z + 0.5 - 8)
            if 5.6 <= d <= 7.0 and z not in (7, 8):
                cells.append((x, z))
    return cells


def runs(cells):
    """Merges cells (a, b) into runs along a for each b: [(a0, a1, b)]."""
    out = []
    for b in sorted({c[1] for c in cells}):
        row = sorted(c[0] for c in cells if c[1] == b)
        start = prev = row[0]
        for a in row[1:] + [None]:
            if a is not None and a == prev + 1:
                prev = a
                continue
            out.append((start, prev + 1, b))
            if a is not None:
                start = prev = a
    return out


def trap_elements(open_):
    """The bear trap, hinge axis along x. Open, its jaws lie flat in a ring round the pan with their teeth standing
    up; sprung, the jaws stand upright face to face over the hinge line with their teeth interlocked in the gap."""
    els = []
    cells = jaw_cells()
    north = [c for c in cells if c[1] < 8]
    south = [c for c in cells if c[1] > 8]
    # Teeth: on the innermost cell of each column of the jaws' middle, alternate columns on each jaw so they mesh.
    def inner(jaw, x, north_side):
        zs = [c[1] for c in jaw if c[0] == x]
        return (max(zs) if north_side else min(zs)) if zs else None
    if open_:
        for x0, x1, z in runs(north):
            els.append(el(f"north_jaw_{z}", (x0, 0, z), (x1, 1, z + 1), IRON, SWATCH))
        for x0, x1, z in runs(south):
            els.append(el(f"south_jaw_{z}", (x0, 0, z), (x1, 1, z + 1), IRON, SWATCH))
        for x in range(4, 12):
            if x % 2 == 0:
                z = inner(north, x, True)
                els.append(el(f"north_tooth_{x}", (x + 0.2, 1, z + 0.4), (x + 0.8, 2.6, z + 1), TOOTH, SWATCH))
            else:
                z = inner(south, x, False)
                els.append(el(f"south_tooth_{x}", (x + 0.2, 1, z), (x + 0.8, 2.6, z + 0.6), TOOTH, SWATCH))
        els.append(el("pan", (5, 0.5, 5), (11, 1, 11), mat("pan", "iron_side", "iron_dark"), SWATCH))
        els.append(el("dog", (7.4, 0.25, 2.2), (8.6, 0.75, 5), IRON, SWATCH))
    else:
        # North jaw stands at z 6.5..7.5, south at 8.5..9.5; a cell z cells from the hinge rises that high.
        up_n = [(x, 7 - z) for x, z in north]
        up_s = [(x, z - 8) for x, z in south]
        for x0, x1, y in runs(up_n):
            els.append(el(f"north_jaw_{y}", (x0, y - 0.5, 6.5), (x1, y + 0.5, 7.5), IRON, SWATCH))
        for x0, x1, y in runs(up_s):
            els.append(el(f"south_jaw_{y}", (x0, y - 0.5, 8.5), (x1, y + 0.5, 9.5), IRON, SWATCH))
        for x in range(4, 12):
            if x % 2 == 0:
                y = 7 - inner(north, x, True)
                els.append(el(f"north_tooth_{x}", (x + 0.2, y - 0.3, 7.5), (x + 0.8, y + 0.3, 8.5), TOOTH, SWATCH))
            else:
                y = inner(south, x, False) - 8
                els.append(el(f"south_tooth_{x}", (x + 0.2, y - 0.3, 7.5), (x + 0.8, y + 0.3, 8.5), TOOTH, SWATCH))
        els.append(el("pan", (5, 0.5, 5), (11, 0.75, 11), mat("pan", "iron_side", "iron_dark"), SWATCH))
    els.append(el("base_bar", (2.5, 0, 7.5), (13.5, 0.5, 8.5), BASE, SWATCH))
    els.append(el("west_hinge", (0.5, 0, 7), (2.5, 1.75, 9), COIL, SWATCH))
    els.append(el("east_hinge", (13.5, 0, 7), (15.5, 1.75, 9), COIL, SWATCH))
    els.append(el("west_pin", (0.0, 0.5, 7.5), (0.5, 1.25, 8.5), mat("stamp", "stamp"), SWATCH))
    els.append(el("east_pin", (15.5, 0.5, 7.5), (16.0, 1.25, 8.5), mat("stamp", "stamp"), SWATCH))
    # The anchor chain: three links running back from the east hinge to a stake driven into the ground.
    chain = mat("chain", "chain", "iron_dark")
    els.append(el("chain_link_1", (15.1, 0, 9.2), (15.9, 0.35, 10.6), chain, SWATCH))
    els.append(el("chain_link_2", (15.35, 0, 10.6), (15.65, 0.75, 12.2), chain, SWATCH))
    els.append(el("chain_link_3", (15.1, 0, 12.2), (15.9, 0.35, 13.6), chain, SWATCH))
    els.append(el("stake", (15.1, 0, 13.6), (15.9, 1.6, 14.4), IRON, SWATCH))
    els.append(el("stake_head", (14.9, 1.6, 13.4), (16.0, 2.0, 14.6), IRON, SWATCH))
    return els


def alarm_elements(ringing):
    """The Tripwire Alarm, front to the north: an oak base, a post at the back with an arm reaching over, a brass bell
    hung from it (cap, shoulder, waist and flared lip) with its clapper below, and in front a steel box of scrap
    electronics with a grille, a lamp on top and a little brass trip lever."""
    W = ALARM_SWATCH
    wood = mat("wood_top", "wood_side", "wood_dark")
    post = mat("wood_top", "wood_side", "wood_dark")
    brass = mat("brass_top", "brass_side", "brass_dark")
    lip = mat("brass_top", "brass_lip", "bell_mouth")
    steel = mat("steel", "steel", "steel_dark")
    lamp = mat("lamp_on" if ringing else "lamp_off", "lamp_on" if ringing else "lamp_off")
    box = mat("steel", "steel", "steel_dark")
    box["north"] = "panel"
    box["east"] = "grille"
    swing = 1.5 if ringing else 0.0
    els = [
        el("base", (3, 0, 3), (13, 1.5, 13), wood, W),
        el("base_trim", (3.5, 1.5, 11), (12.5, 2, 13), mat("wood_dark", "wood_dark"), W),
        el("post", (7, 2, 11), (9, 12.5, 13), post, W),
        el("arm", (7, 11, 7), (9, 12.5, 11), post, W),
        el("brace", (7.5, 9.5, 10), (8.5, 11, 11), mat("wood_dark", "wood_dark"), W),
        el("hanger", (7.5, 10.75, 7.5), (8.5, 11, 8.5), steel, W),
        el("bell_cap", (7, 10, 7), (9, 10.75, 9), brass, W),
        el("bell_shoulder", (6, 9, 6), (10, 10, 10), brass, W),
        el("bell_waist", (5.5, 6.5, 5.5), (10.5, 9, 10.5), brass, W),
        el("bell_lip", (5, 5.5, 5), (11, 6.5, 11), lip, W),
        el("clapper_rod", (7.75 + swing, 4.5, 7.75), (8.25 + swing, 5.5, 8.25), steel, W),
        el("clapper", (7.25 + swing, 3.5, 7.25), (8.75 + swing, 4.5, 8.75), mat("steel_dark", "steel_dark"), W),
        el("box", (3.5, 1.5, 2.5), (9, 4.5, 5), box, W),
        el("lamp", (6.5, 4.5, 3), (8, 5.25, 4.5), lamp, W),
        el("lever", (4.25, 2.5, 2), (5.25, 3.5, 2.5), mat("hook", "hook"), W),
        el("wire", (8.75, 1.5, 5), (9.25, 2, 11), mat("steel_dark", "steel_dark"), W),
    ]
    return els


def block_model(elements, texture, item_display=None):
    model = OrderedDict([("parent", "minecraft:block/block"), ("ambientocclusion", False),
                         ("textures", OrderedDict([("tex", texture), ("particle", texture)]))])
    if item_display:
        model["display"] = item_display
    model["elements"] = elements
    return model


SMALL_DISPLAY = OrderedDict([
    ("gui", {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]}),
    ("ground", {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]}),
    ("fixed", {"rotation": [90, 0, 0], "translation": [0, 0, -4], "scale": [0.75, 0.75, 0.75]}),
    ("thirdperson_righthand", {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]}),
    ("firstperson_righthand", {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]}),
    ("firstperson_lefthand", {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]}),
])


def check_zfight(name, elements):
    hits = zfight.check(elements)
    if hits:
        for h in hits:
            print("  z-fight in", name, h)
        sys.exit(f"{name}: {len(hits)} coplanar overlapping faces")


# ======================================================================================================= writing

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def merge(path, entries):
    """Adds entries to a shared JSON object file (lang, sounds), keeping what's there."""
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def tag(path, values):
    """Adds values to a tag file (creating it if needed), keeping its other entries."""
    obj = OrderedDict([("values", [])])
    if os.path.exists(path):
        with open(path) as f:
            obj = json.load(f, object_pairs_hook=OrderedDict)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(path, obj)


def m(name):
    return "tomyen:" + name


LANG = OrderedDict([
    ("item.tomyen.crowbar", "Crowbar"),
    ("item.tomyen.scrap_helmet", "Scrap Helmet"),
    ("item.tomyen.scrap_chestplate", "Scrap Chestplate"),
    ("item.tomyen.scrap_leggings", "Scrap Leggings"),
    ("item.tomyen.scrap_boots", "Scrap Boots"),
    ("item.tomyen.bandage", "Bandage"),
    ("item.tomyen.bandage.desc", "Stops bleeding and heals over a few seconds"),
    ("item.tomyen.first_aid_kit", "First Aid Kit"),
    ("item.tomyen.first_aid_kit.desc", "Heals, stops bleeding and poison, eases radiation sickness"),
    ("item.tomyen.molotov_cocktail", "Molotov Cocktail"),
    ("item.tomyen.molotov_cocktail.desc", "Shatters into burning oil"),
    ("item.tomyen.pipe_bomb", "Pipe Bomb"),
    ("item.tomyen.pipe_bomb.desc", "Short fuse; spares blocks, stuns machines"),
    ("item.tomyen.canteen", "Canteen"),
    ("item.tomyen.canteen.empty", "Empty: fill it at water, or click a potion onto it"),
    ("item.tomyen.canteen.sips", "%s: %s/%s"),
    ("item.tomyen.scavengers_satchel", "Scavenger's Satchel"),
    ("item.tomyen.scavengers_satchel.slots", "%s/%s slots used"),
    ("block.tomyen.bear_trap", "Bear Trap"),
    ("block.tomyen.silver_bear_trap", "Silver-Toothed Bear Trap"),
    ("block.tomyen.tripwire_alarm", "Tripwire Alarm"),
    ("block.tomyen.tripwire_alarm.alert", "Alarm at %2$s, %3$s, %4$s: %1$s!"),
    ("block.tomyen.tripwire_alarm.tripwire", "tripwire crossed"),
    ("entity.tomyen.molotov_cocktail", "Molotov Cocktail"),
    ("entity.tomyen.pipe_bomb", "Pipe Bomb"),
    ("effect.tomyen.trapped", "Trapped"),
    ("effect.tomyen.stunned", "Stunned"),
    ("death.attack.tomyen.bear_trap", "%1$s was caught in a bear trap"),
    ("death.attack.tomyen.bear_trap.player", "%1$s was caught in a bear trap whilst fleeing %2$s"),
    ("subtitles.tomyen.crowbar_pry", "Crowbar pries"),
    ("subtitles.tomyen.bear_trap_snap", "Bear trap snaps"),
    ("subtitles.tomyen.bear_trap_set", "Bear trap set"),
    ("subtitles.tomyen.tripwire_alarm_ring", "Alarm rings"),
    ("subtitles.tomyen.pipe_bomb_fuse", "Fuse fizzes"),
    ("subtitles.tomyen.pipe_bomb_blast", "Pipe bomb explodes"),
    ("subtitles.tomyen.molotov_shatter", "Molotov shatters"),
    ("subtitles.tomyen.bandage_wrap", "Bandage wraps"),
    ("subtitles.tomyen.canteen_sip", "Canteen sipped"),
    ("subtitles.tomyen.canteen_fill", "Canteen fills"),
    ("subtitles.tomyen.satchel_open", "Satchel opens"),
    ("subtitles.tomyen.scrap_armor_deflect", "Scrap armor clangs"),
    ("subtitles.tomyen.item.armor.equip_scrap", "Scrap armor rattles"),
])


def sound(name, files, pitch, volume=1.0):
    entries = []
    for f in files:
        e = OrderedDict([("name", "minecraft:" + f), ("pitch", pitch)])
        if volume != 1.0:
            e["volume"] = volume
        entries.append(e)
    return name, OrderedDict([("subtitle", "subtitles.tomyen." + name), ("sounds", entries)])


SOUNDS = OrderedDict([
    sound("crowbar_pry", ["mob/zombie/metal1", "mob/zombie/metal2", "mob/zombie/metal3"], 1.45, 0.6),
    sound("bear_trap_snap", ["random/anvil_land"], 1.9, 0.7),
    sound("bear_trap_set", ["block/iron_trapdoor/open1", "block/iron_trapdoor/open2", "block/iron_trapdoor/open3",
                            "block/iron_trapdoor/open4"], 1.35),
    sound("tripwire_alarm_ring", ["block/bell/bell_use01", "block/bell/bell_use02"], 1.6),
    sound("pipe_bomb_fuse", ["random/fuse"], 1.6, 0.6),
    sound("pipe_bomb_blast", ["random/explode1", "random/explode2", "random/explode3", "random/explode4"], 1.45),
    sound("molotov_shatter", ["random/glass1", "random/glass2", "random/glass3"], 0.8),
    sound("bandage_wrap", ["dig/cloth1", "dig/cloth2", "dig/cloth3", "dig/cloth4"], 1.3, 0.7),
    sound("canteen_sip", ["random/drink"], 1.25),
    sound("canteen_fill", ["item/bottle/fill1", "item/bottle/fill2", "item/bottle/fill3", "item/bottle/fill4"], 0.8),
    sound("satchel_open", ["item/armor/equip_leather1", "item/armor/equip_leather2", "item/armor/equip_leather3"], 1.2),
    sound("scrap_armor_deflect", ["mob/zombie/metal1", "mob/zombie/metal2", "mob/zombie/metal3"], 1.8, 0.45),
    sound("item.armor.equip_scrap", ["item/armor/equip_chain1", "item/armor/equip_chain2", "item/armor/equip_chain3",
                                     "item/armor/equip_chain4"], 0.8),
])


def write_assets():
    for name, img in SPRITES.items():
        img.save(os.path.join(TEX, "item", name + ".png"))
        write(os.path.join(ASSETS, "models", "item", name + ".json"),
              OrderedDict([("parent", "minecraft:item/handheld" if name == "crowbar" else "minecraft:item/generated"),
                           ("textures", {"layer0": m("item/" + name)})]))
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": m("item/" + name)}})
    for kind in ("trapped", "stunned"):
        effect_icon(kind).save(os.path.join(TEX, "mob_effect", kind + ".png"))

    # Bear traps: open and sprung models, the silver trap's re-textured children; item shows the open trap.
    trap_texture(False).save(os.path.join(TEX, "block", "bear_trap.png"))
    trap_texture(True).save(os.path.join(TEX, "block", "silver_bear_trap.png"))
    for state in ("open", "closed"):
        elements = trap_elements(state == "open")
        check_zfight("bear_trap_" + state, elements)
        write(os.path.join(ASSETS, "models", "block", f"bear_trap_{state}.json"),
              block_model(elements, m("block/bear_trap"), SMALL_DISPLAY if state == "open" else None))
        write(os.path.join(ASSETS, "models", "block", f"silver_bear_trap_{state}.json"),
              OrderedDict([("parent", m(f"block/bear_trap_{state}")),
                           ("textures", {"tex": m("block/silver_bear_trap"), "particle": m("block/silver_bear_trap")})]))
    for trap in ("bear_trap", "silver_bear_trap"):
        variants = OrderedDict()
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for is_open in ("true", "false"):
                v = OrderedDict([("model", m(f"block/{trap}_{'open' if is_open == 'true' else 'closed'}"))])
                if y:
                    v["y"] = y
                variants[f"facing={facing},open={is_open}"] = v
        write(os.path.join(ASSETS, "blockstates", trap + ".json"), {"variants": variants})
        write(os.path.join(ASSETS, "items", trap + ".json"), {"model": {"type": "minecraft:model", "model": m(f"block/{trap}_open")}})

    alarm_texture().save(os.path.join(TEX, "block", "tripwire_alarm.png"))
    for ringing in (False, True):
        elements = alarm_elements(ringing)
        name = "tripwire_alarm" + ("_ringing" if ringing else "")
        check_zfight(name, elements)
        write(os.path.join(ASSETS, "models", "block", name + ".json"),
              block_model(elements, m("block/tripwire_alarm"), None))
    variants = OrderedDict()
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for ringing in ("false", "true"):
            v = OrderedDict([("model", m("block/tripwire_alarm" + ("_ringing" if ringing == "true" else "")))])
            if y:
                v["y"] = y
            variants[f"facing={facing},ringing={ringing}"] = v
    write(os.path.join(ASSETS, "blockstates", "tripwire_alarm.json"), {"variants": variants})
    write(os.path.join(ASSETS, "items", "tripwire_alarm.json"), {"model": {"type": "minecraft:model", "model": m("block/tripwire_alarm")}})

    merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG)
    merge(os.path.join(ASSETS, "sounds.json"), SOUNDS)


def write_data():
    mine = os.path.join(DATA, "tomyen")
    vanilla_tags = os.path.join(DATA, "minecraft", "tags")
    recipes, advancements = os.path.join(mine, "recipe"), os.path.join(mine, "advancement", "recipes")

    def advancement(name, category, unlock):
        write(os.path.join(advancements, category, name + ".json"), OrderedDict([
            ("parent", "minecraft:recipes/root"),
            ("criteria", OrderedDict([
                ("has_item", {"conditions": {"items": [{"items": unlock}]}, "trigger": "minecraft:inventory_changed"}),
                ("has_the_recipe", {"conditions": {"recipes": m(name)}, "trigger": "minecraft:recipe_unlocked"})])),
            ("requirements", [["has_item", "has_the_recipe"]]),
            ("rewards", {"recipes": [m(name)]})]))

    def shaped(name, pattern, key, unlock, count=1, category="equipment", book="combat"):
        write(os.path.join(recipes, name + ".json"), OrderedDict([
            ("type", "minecraft:crafting_shaped"), ("category", category), ("key", key), ("pattern", pattern),
            ("result", {"count": count, "id": m(name)})]))
        advancement(name, book, unlock)

    def shapeless(name, ingredients, unlock, count=1, category="equipment", book="combat"):
        write(os.path.join(recipes, name + ".json"), OrderedDict([
            ("type", "minecraft:crafting_shapeless"), ("category", category), ("ingredients", ingredients),
            ("result", {"count": count, "id": m(name)})]))
        advancement(name, book, unlock)

    scrap = m("tech_scrap")
    shaped("crowbar", ["  N", " I ", "I  "], {"I": "minecraft:iron_ingot", "N": "minecraft:iron_nugget"}, "minecraft:iron_ingot",
           book="tools")
    # Scrap armor, the pipe bomb, the bear trap and the tripwire alarm are assembled on the Scrap Bench
    # (scrapbench/BenchRecipes.java).
    shaped("bandage", ["PSP"], {"P": "minecraft:paper", "S": "minecraft:string"}, "minecraft:paper", count=2, category="misc",
           book="misc")
    shaped("first_aid_kit", ["LFL", "BHB"], {"L": "minecraft:leather", "F": "#minecraft:small_flowers", "B": m("bandage"),
                                             "H": "minecraft:honey_bottle"}, m("bandage"), category="misc", book="misc")
    shapeless("molotov_cocktail", [m("fish_oil"), "minecraft:string"], m("fish_oil"))
    shapeless("silver_bear_trap", [m("bear_trap"), m("silver_ingot")], m("silver_ingot"), category="redstone", book="redstone")
    shaped("canteen", [" N ", "LBL", " L "], {"N": "minecraft:iron_nugget", "L": "minecraft:leather", "B": "minecraft:glass_bottle"},
           "minecraft:leather", category="misc", book="tools")
    shaped("scavengers_satchel", [" S ", "LTL", "LLL"], {"S": "minecraft:string", "L": "minecraft:leather", "T": scrap},
           scrap, category="misc", book="tools")

    for block in ("bear_trap", "silver_bear_trap", "tripwire_alarm"):
        write(os.path.join(mine, "loot_table", "blocks", block + ".json"), OrderedDict([
            ("type", "minecraft:block"),
            ("pools", [OrderedDict([("condition", {"type": "minecraft:survives_explosion"}), ("rolls", 1),
                                    ("entries", [{"type": "minecraft:item", "name": m(block)}])])]),
            ("random_sequence", m("blocks/" + block))]))

    write(os.path.join(mine, "damage_type", "bear_trap.json"),
          OrderedDict([("exhaustion", 0.1), ("message_id", "tomyen.bear_trap"), ("scaling", "never")]))

    tag(os.path.join(mine, "tags", "entity_type", "machines.json"),
        [m("skullcrane"), m("skullcrane_leader"), m("cathode_walker"), m("ufo")])
    tag(os.path.join(mine, "tags", "damage_type", "electric.json"), [m("alien_ray"), m("short_circuit"), m("laser")])
    tag(os.path.join(mine, "tags", "block", "pryable.json"),
        ["minecraft:barrel", "minecraft:chest", "minecraft:trapped_chest", "minecraft:composter", "minecraft:beehive",
         "minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:crafting_table", "minecraft:fletching_table",
         "minecraft:cartography_table", "minecraft:smithing_table", "minecraft:loom", "minecraft:lectern", "minecraft:jukebox",
         "minecraft:note_block", "#minecraft:wooden_doors", "#minecraft:wooden_trapdoors", "#minecraft:fence_gates",
         "#minecraft:signs", m("crt_television")])
    tag(os.path.join(mine, "tags", "item", "repairs_scrap_armor.json"), [scrap])
    for piece, tag_name in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        tag(os.path.join(vanilla_tags, "item", tag_name + ".json"), [m(f"scrap_{piece}")])
    tag(os.path.join(vanilla_tags, "item", "enchantable", "melee_weapon.json"), [m("crowbar")])
    tag(os.path.join(vanilla_tags, "item", "enchantable", "durability.json"), [m("crowbar")])
    tag(os.path.join(vanilla_tags, "block", "mineable", "pickaxe.json"), [m("bear_trap"), m("silver_bear_trap")])
    tag(os.path.join(vanilla_tags, "block", "mineable", "axe.json"), [m("tripwire_alarm")])


# ======================================================================================================= preview

def element_quads(elements, tex, offset=(0, 0, 0)):
    """Model elements as texel quads in block units: one small quad per texel of each face, so the texture shows."""
    quads = []
    for e in elements:
        f, t = e["from"], e["to"]
        for direction, face in e["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            if direction == "up":
                corner = lambda s, q: (f[0] + (t[0] - f[0]) * s, t[1], f[2] + (t[2] - f[2]) * q)
            elif direction == "down":
                corner = lambda s, q: (f[0] + (t[0] - f[0]) * s, f[1], t[2] - (t[2] - f[2]) * q)
            elif direction == "north":
                corner = lambda s, q: (t[0] - (t[0] - f[0]) * s, t[1] - (t[1] - f[1]) * q, f[2])
            elif direction == "south":
                corner = lambda s, q: (f[0] + (t[0] - f[0]) * s, t[1] - (t[1] - f[1]) * q, t[2])
            elif direction == "west":
                corner = lambda s, q: (f[0], t[1] - (t[1] - f[1]) * q, f[2] + (t[2] - f[2]) * s)
            else:
                corner = lambda s, q: (t[0], t[1] - (t[1] - f[1]) * q, t[2] - (t[2] - f[2]) * s)
            nu, nv = max(1, int(round(abs(u1 - u0)))), max(1, int(round(abs(v1 - v0))))
            for j in range(nv):
                for i in range(nu):
                    c = tex.get(int(min(u0, u1)) + i, int(min(v0, v1)) + j)
                    if not c[3]:
                        continue
                    pts = [corner(a / nu, b / nv) for a, b in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1))]
                    pts = [tuple((p[k] + offset[k] * 16) / 16.0 - 0.5 for k in range(3)) for p in pts]
                    quads.append((pts, c, False))
    return quads


def view(yaw, pitch):
    a, b = math.radians(yaw), math.radians(pitch)

    def project(p):
        x, y, z = -p[0], p[1], p[2]
        x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
        y, z = y * math.cos(b) + z * math.sin(b), -y * math.sin(b) + z * math.cos(b)
        return (x, y, z)
    return project


def render(img, quads, project, cx, cy, scale, depth=None, clip=None):
    """Flat-shaded texel quads with Minecraft's face shading (up 1.0, down 0.5, north/south 0.8, east/west 0.6)."""
    depth = depth or [1e9] * (img.w * img.h)
    cx0, cy0, cx1, cy1 = clip or (0, 0, img.w, img.h)
    for corners, col, _ in quads:
        sp = [project(p) for p in corners]
        e1 = [corners[1][i] - corners[0][i] for i in range(3)]
        e2 = [corners[3][i] - corners[0][i] for i in range(3)]
        n = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        n = [c / nl for c in n]
        lit = 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] > 0 else 0.5) * n[1] ** 2
        pts = [(cx + p[0] * scale, cy - p[1] * scale, p[2]) for p in sp]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        z = sum(p[2] for p in pts) / 4
        shaded = tuple(min(255, int(col[k] * lit)) for k in range(3)) + (255,)
        for py in range(max(cy0, int(min(ys))), min(cy1, int(math.ceil(max(ys))) + 1)):
            for px in range(max(cx0, int(min(xs))), min(cx1, int(math.ceil(max(xs))) + 1)):
                if R_inside(pts, px + 0.5, py + 0.5) and z < depth[py * img.w + px]:
                    depth[py * img.w + px] = z
                    img.set(px, py, shaded)
    return depth


def R_inside(poly, x, y):
    sign = 0
    for i in range(4):
        x1, y1 = poly[i][0], poly[i][1]
        x2, y2 = poly[(i + 1) % 4][0], poly[(i + 1) % 4][1]
        cr = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if abs(cr) < 1e-9:
            continue
        s = 1 if cr > 0 else -1
        if sign == 0:
            sign = s
        elif s != sign:
            return False
    return sign != 0


def label(img, x, y, s, k=2, colour="#e9eff5"):
    R.text(img, x, y, s, colour, k)


def preview(path):
    W, H = 1500, 790
    sheet = Img(W, H, hexc("2b2f36"))
    # Row 1: every sprite, 1x and 6x on a vanilla-grey slot.
    label(sheet, 20, 14, "ITEM SPRITES (1X AND 6X)")
    for i, (name, img) in enumerate(SPRITES.items()):
        x = 20 + i * 132
        sheet.fill(x, 40, x + 18, 58, hexc("8b8b8b"))
        sheet.paste(img, x + 1, 41)
        sheet.fill(x, 64, x + 100, 164, hexc("8b8b8b"))
        sheet.paste(img, x + 2, 66, 6)
        label(sheet, x, 170, name.replace("scavengers_", "").replace("_cocktail", "").replace("_", " "), 1)
    # Row 2: the traps, open and sprung, iron and silver; the alarm idle and ringing.
    label(sheet, 20, 196, "BEAR TRAP, OPEN AND SPRUNG (IRON, SILVER)  /  TRIPWIRE ALARM, IDLE AND RINGING")
    textures = {"bear_trap": trap_texture(False), "silver_bear_trap": trap_texture(True), "alarm": alarm_texture()}
    shots = [("bear_trap", True), ("bear_trap", False), ("silver_bear_trap", True), ("silver_bear_trap", False)]
    for i, (trap, is_open) in enumerate(shots):
        x0 = 20 + i * 245
        sheet.fill(x0, 220, x0 + 235, 470, hexc("5b7a3a"))
        render(sheet, element_quads(trap_elements(is_open), textures[trap]), view(30, 32), x0 + 117, 270, 220,
               clip=(x0, 220, x0 + 235, 470))
        label(sheet, x0 + 6, 452, ("OPEN" if is_open else "SPRUNG") + (" SILVER" if trap.startswith("silver") else ""), 1)
    for i, ringing in enumerate((False, True)):
        x0 = 1000 + i * 245
        sheet.fill(x0, 220, x0 + 235, 470, hexc("5b7a3a"))
        render(sheet, element_quads(alarm_elements(ringing), textures["alarm"]), view(35, 22), x0 + 117, 330, 210,
               clip=(x0, 220, x0 + 235, 470))
        label(sheet, x0 + 6, 452, "RINGING" if ringing else "IDLE", 1)
    # Row 3: close-ups of the sprung trap from the side and the alarm from the front, and the textures.
    sheet.fill(20, 490, 500, 760, hexc("5b7a3a"))
    render(sheet, element_quads(trap_elements(False), textures["bear_trap"]), view(15, 12), 260, 470, 420,
           clip=(20, 490, 500, 760))
    label(sheet, 26, 742, "SPRUNG, CLOSE: JAWS UP, TEETH MESHED", 1)
    sheet.fill(520, 490, 900, 760, hexc("5b7a3a"))
    render(sheet, element_quads(alarm_elements(False), textures["alarm"]), view(0, 6), 710, 600, 300,
           clip=(520, 490, 900, 760))
    label(sheet, 526, 742, "ALARM, FRONT", 1)
    for i, (name, tex) in enumerate(textures.items()):
        x = 920 + i * 190
        sheet.fill(x, 500, x + 168, 668, hexc("8b8b8b"))
        sheet.paste(tex, x + 4, 504, 10)
        label(sheet, x, 676, name.replace("_", " ") + " tex", 1)
    for i, kind in enumerate(("trapped", "stunned")):
        x = 920 + i * 190
        sheet.fill(x, 700, x + 80, 760, hexc("8b8b8b"))
        sheet.paste(effect_icon(kind), x + 4, 704, 3)
        label(sheet, x + 86, 724, kind, 1)
    sheet.save(path)
    print("preview:", path)


def main():
    build_sprites()
    write_assets()
    write_data()
    if PREVIEW:
        preview(PREVIEW)


if __name__ == "__main__":
    main()
