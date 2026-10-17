"""Builds the art and data of the Isle of the Damned, pass 2 (shades and the Soul Tide): the Soul Wisp and Shade Heart
items, the soul wisp's sprite, their models, sounds, lang lines, tags, the Soul Charge recipe, and the Shade Heart
advancement. (The Isle's terrain, biomes and their spawns are still tools/gen_isle.py's.)

Textures follow the mod's rule: vanilla art put through a ramp of the Isle's own colours by brightness, except the
wisp's sprite, which is drawn here.

    soul wisp (item)   the soul fire flame particle in a faint ring of soul light, sparks rising off it
    shade heart        the heart of the sea, in a shade's violet, its glint soul-cyan
    soul wisp (entity) a round mote of soul fire, 16x16: a white core in rings out to a clear edge (one quad, so
                       nothing overlaps; see SoulWispRenderer)

The tags (see IsleShades):

    notable_dead   every death remembered: the bosses, and the elites (the mod's Skullcrane Pack Leader, werewolf,
                   Gnome Captain, Banshee, Bog Hag and vampire's kin, and vanilla's ravager, evoker, piglin brute)
    shade_bosses   their shades are boss fights: Wither, Ender Dragon, Elder Guardian, Warden, Schnozzle, Giant Catfish
    shade_casters  their shades keep back and lash: the skeletons, blazes, ghasts, witches, illagers that cast or shoot
    soulless       leave nothing on the Isle: the mod's machines, and the Isle's own spirits

Usage: python3 tools/gen_isle_shades.py src/main/resources/assets/tomyen [--preview path.png]"""
import json, os, sys, zipfile
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
import gen_fae  # noqa: E402,F401  (reads ASSETS from sys.argv)
from gen_fae import Sprite, ramp, recolour, lum, read_texture, item_def, recipe, shapeless, tag, CLEAR  # noqa: E402
from gen_longswords import client_jar, REPO  # noqa: E402
from gunkit import png, write  # noqa: E402

DATA = os.path.normpath(os.path.join(ASSETS, "..", "..", "data"))

SOUL = [0x0f3048, 0x1f6f8a, 0x45b8cf, 0x8eeaf2, 0xeaffff]
SHADE = [0x0e0820, 0x241446, 0x41287a, 0x6c4cb4, 0xa58ae6]


# ======================================================================================================= textures


def soul_wisp_item(flame):
    """The soul fire flame particle (8x8) standing in a faint round halo of soul light, three sparks drifting up off
    it: a mote of soul fire, held."""
    s = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 9.0) ** 2) ** 0.5
            if r < 6.2:
                s.set(x, y, ramp(SOUL, 0.55 if r > 4.6 else 0.45, 45 if r > 4.6 else 105))
    for y in range(flame.h):
        for x in range(flame.w):
            c = flame.get(x, y)
            if c[3]:
                s.set(x + 4, y + 5, recolour([c], SOUL, stretch=False)[0][:3] + (255,))
    for x, y, t in ((3, 3, 0.85), (11, 2, 1.0), (12, 6, 0.6)):
        s.set(x, y, ramp(SOUL, t))
    return s


def shade_heart(src):
    """The heart of the sea in shade violet, its few brightest texels (the glint) in soul-cyan."""
    s = Sprite(src.w, src.h, recolour(src.px, SHADE))
    ls = sorted(lum(c) for c in src.px if c[3])
    cut = ls[int(len(ls) * 0.985)]
    for i, c in enumerate(src.px):
        if c[3] and lum(c) >= cut:
            s.px[i] = ramp(SOUL, 0.75 + 0.25 * (lum(c) - cut) / max(1e-6, ls[-1] - cut))
    return s


def soul_wisp_sprite():
    """Rings of soul fire round a white core, fading to clear: by distance from the centre of the 16x16, in steps."""
    rings = [(1.6, 4, 255), (2.9, 3, 245), (4.2, 2, 200), (5.5, 1, 120), (6.8, 0, 60), (7.6, 0, 22)]
    s = Sprite(16, 16)
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            for edge, shade, alpha in rings:
                if r < edge:
                    s.set(x, y, ramp(SOUL, shade / 4.0, alpha))
                    break
    return s


# ======================================================================================================= data

SOUNDS = OrderedDict([
    ("shade_ambient", ("shade_ambient", [(f"minecraft:mob/vex/idle{i}", 0.55) for i in range(1, 5)])),
    ("shade_hurt", ("shade_hurt", [(f"minecraft:mob/vex/hurt{i}", 0.6) for i in range(1, 3)])),
    ("shade_death", ("shade_death", [(f"minecraft:mob/vex/death{i}", 0.5) for i in range(1, 3)])),
    ("shade_form", ("shade_form", [(f"minecraft:enchant/soulspeed/soulspeed{i}", 0.6) for i in range(1, 7)])),
    ("shade_dissolve", ("shade_dissolve", [(f"minecraft:enchant/soulspeed/soulspeed{i}", 1.0) for i in range(7, 14)])),
    ("shade_lash", ("shade_lash", [(f"minecraft:mob/evocation_illager/cast{i}", 0.7) for i in range(1, 3)])),
    ("boss_shade_rise", ("boss_shade_rise", [("minecraft:mob/warden/emerge", 0.8)])),
    ("boss_shade_nova", ("boss_shade_nova", [(f"minecraft:mob/warden/sonic_boom{i}", 1.4) for i in range(1, 5)])),
    ("soul_tide_surge", ("soul_tide_surge", [(f"minecraft:block/sculk_shrieker/shriek{i}", 0.5) for i in range(1, 6)])),
    ("pet_echo", ("pet_echo", [(f"minecraft:mob/allay/idle_without_item{i}", 0.6) for i in range(1, 5)])),
    ("soul_wisp_ambient", ("soul_wisp_ambient", [("minecraft:block/amethyst/shimmer", 1.5)])),
    ("soul_wisp_death", ("soul_wisp_death", [(f"minecraft:block/candle/extinguish{i}", 1.3) for i in range(1, 4)])),
])

LANG = OrderedDict([
    ("entity.tomyen.shade", "Shade"),
    ("entity.tomyen.shade.of", "%s Shade"),
    ("entity.tomyen.shade.boss", "Shade of the %s"),
    ("entity.tomyen.shade.boss_named", "Shade of %s"),
    ("entity.tomyen.soul_wisp", "Soul Wisp"),
    ("message.tomyen.isle.tide_surges", "The Soul Tide surges..."),
    ("message.tomyen.isle.boss_shade", "%s gathers itself out of the fog"),
    ("message.tomyen.isle.boss_shade_rests", "%s is laid to rest"),
    ("message.tomyen.isle.pet_echo", "%s found you, even here."),
    ("message.tomyen.isle.pet_goes", "%s stays a moment longer, then is gone, at peace."),
    ("message.tomyen.isle.your_victim", "Something you killed has come for you: %s"),
    ("message.tomyen.isle.named_shade", "The shade of %s drifts out of the fog. It fell %s."),
    ("message.tomyen.isle.origin", "near %s, %s in %s"),
    ("message.tomyen.isle.named_rests", "%s is laid to rest."),
    ("message.tomyen.isle.heart_shallowest", "Your belongings already lie as shallow as they can"),
    ("message.tomyen.isle.heart_raises", "The Shade Heart draws your belongings up to %s."),
    ("hud.tomyen.isle.tide_high", "The Soul Tide runs high"),
    ("hud.tomyen.isle.tide_surges", "The Soul Tide surges"),
    ("commands.tomyen.isle.souls", "Isle souls: %s records in %s regions, %s boss records. Soul Tide %s over %s types (strength %s): %s"),
    ("commands.tomyen.isle.shades", "Shades: %s now (peak %s); %s raised, %s beaten, %s dissolved. Deaths seen: %s, %s ns each to record"),
    ("subtitles.tomyen.shade_ambient", "Shade whispers"),
    ("subtitles.tomyen.shade_hurt", "Shade hurts"),
    ("subtitles.tomyen.shade_death", "Shade fades"),
    ("subtitles.tomyen.shade_form", "A shade forms"),
    ("subtitles.tomyen.shade_dissolve", "A shade dissolves"),
    ("subtitles.tomyen.shade_lash", "Shade lashes"),
    ("subtitles.tomyen.boss_shade_rise", "A great shade rises"),
    ("subtitles.tomyen.boss_shade_nova", "Shade blasts"),
    ("subtitles.tomyen.soul_tide_surge", "The Soul Tide surges"),
    ("subtitles.tomyen.pet_echo", "A faithful echo"),
    ("subtitles.tomyen.soul_wisp_ambient", "Soul Wisp chimes"),
    ("subtitles.tomyen.soul_wisp_death", "Soul Wisp gutters out"),
])

NOTABLE = ["#tomyen:shade_bosses", "tomyen:skullcrane_leader", "tomyen:werewolf", "tomyen:gnome_captain", "tomyen:banshee", "tomyen:bog_hag",
           "minecraft:ravager", "minecraft:evoker", "minecraft:piglin_brute"]
BOSSES = ["minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian", "minecraft:warden", "tomyen:schnozzle",
          "tomyen:giant_catfish"]
CASTERS = ["minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:wither_skeleton", "minecraft:blaze", "minecraft:ghast",
           "minecraft:witch", "minecraft:evoker", "minecraft:illusioner", "minecraft:pillager", "minecraft:breeze", "minecraft:shulker",
           "minecraft:guardian", "tomyen:bog_hag"]
SOULLESS = ["#tomyen:machines", "tomyen:shade", "tomyen:soul_wisp", "tomyen:revenant"]


def sounds_json():
    out = OrderedDict()
    for event, (subtitle, files) in SOUNDS.items():
        out[event] = OrderedDict([("subtitle", "subtitles.tomyen." + subtitle),
                                  ("sounds", [OrderedDict([("name", f), ("pitch", p)]) for f, p in files])])
    return out


def merge(path, entries, indent=None):
    with open(path) as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    if indent:
        with open(path, "w") as f:
            json.dump(obj, f, indent=indent, ensure_ascii=False)
            f.write("\n")
    else:
        write(path, obj)


def check_sounds():
    """Every sound file named must be in the 26.3 asset index."""
    index = os.path.expanduser("~/.gradle/caches/fabric-loom/assets/indexes/26.3-34.json")
    with open(index) as f:
        objects = json.load(f)["objects"]
    for _, files in SOUNDS.values():
        for name, _ in files:
            ns, path = name.split(":")
            key = f"{ns}/sounds/{path}.ogg"
            if key not in objects:
                sys.exit(f"missing vanilla sound {key}")


def write_data():
    tag("entity_type", "tomyen", "shade_bosses", BOSSES)
    tag("entity_type", "tomyen", "notable_dead", NOTABLE)
    tag("entity_type", "tomyen", "shade_casters", CASTERS)
    tag("entity_type", "tomyen", "soulless", SOULLESS)
    tag("entity_type", "tomyen", "evil", ["tomyen:shade"])
    # The Soul Wisp and Shade Heart items (offering tag, Soul Charge recipe, shade_heart advancement, item models) are on
    # the backlog; shades drop Ectoplasm and Soul Charges for now.


def preview_sheet(path, sprites):
    K = 12
    gap = 10
    W = sum(s.w * K + gap for s in sprites) + gap
    H = max(s.h * K for s in sprites) * 2 + gap * 3
    canvas = [(34, 38, 44, 255)] * (W * H)
    x0 = gap
    for s in sprites:
        for band, bg in enumerate([(34, 38, 44), (150, 150, 150)]):
            oy = gap + band * (s.h * K + gap)
            for y in range(s.h * K):
                for x in range(s.w * K):
                    c = s.px[(y // K) * s.w + x // K]
                    a = c[3] / 255
                    canvas[(oy + y) * W + x0 + x] = tuple(int(c[i] * a + bg[i] * (1 - a)) for i in range(3)) + (255,)
        x0 += s.w * K + gap
    png(path, W, H, canvas)
    print("preview:", path)


def main():
    check_sounds()
    sprites = []
    with zipfile.ZipFile(client_jar()) as z:
        def tex(name):
            w, h, px = read_texture(z.read(f"assets/minecraft/textures/{name}.png"))
            return Sprite(w, h, px)

        def out(folder, name, sprite):
            png(os.path.join(ASSETS, "textures", folder, name + ".png"), sprite.w, sprite.h, sprite.px)
            sprites.append(sprite)

        out("entity", "soul_wisp", soul_wisp_sprite())
    write_data()
    merge(os.path.join(ASSETS, "sounds.json"), sounds_json())
    merge(os.path.join(ASSETS, "lang", "en_us.json"), LANG, indent=2)
    preview_sheet(PREVIEW or os.path.join(REPO, "build", "isle_shades_preview.png"), sprites)
    print("isle shades done:", len(sprites), "textures")


if __name__ == "__main__":
    main()
