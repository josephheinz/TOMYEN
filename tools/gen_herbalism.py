"""Builds the Herbalism set's art and data (see com.jheinz.mymod.herbalism.Herbalism):

  * textures: the eight wild herbs and four garlic crop stages, the item sprites, the effect icons, and the smooth
    materials of the placed blocks' models (drawn in tools/herbalism_art.py);
  * models: herbs on vanilla's block/cross and the crop on block/crop (their items are block/item sprites like
    vanilla flowers), and detailed cube models for the Kettle (sitting, and hung from a spit over a campfire), the
    Mortar & Pestle, the Garlic Braid and the Wolfsbane Ward. Every placed-block model is checked for z-fighting
    (coplanar, same-facing, overlapping faces, in world space) and the build stops if any is found;
  * block states, item definitions, loot tables, recipes (with their recipe-book advancements), tags, worldgen
    patches, and the lang entries (merged into en_us.json).

Usage: python3 tools/gen_herbalism.py [--preview out.png]

With --preview it also renders a sheet: each herb growing on its own ground, the garlic stages on farmland, the kettle
over a campfire and on magma, the mortar, the braid and the ward on a wall, then every sprite at 4x."""
import json, math, os, sys
from collections import OrderedDict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ARGS = sys.argv[1:]
PREVIEW = None
if "--preview" in ARGS:
    PREVIEW = ARGS[ARGS.index("--preview") + 1]
sys.argv = sys.argv[:1]
import herbalism_art as art
from gunkit import png, write, rz, rx, ry, mul, apply, rotate, copy
from gunsmith_kit import find_overlaps

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "mymod")
DATA = os.path.join(REPO, "src", "main", "resources", "data")
MOD = os.path.join(DATA, "mymod")


def tex(path, grid):
    png(os.path.join(ASSETS, "textures", path + ".png"), grid.w, grid.h, grid.px)


def item_def(name, model=None):
    write(os.path.join(ASSETS, "items", name + ".json"),
          {"model": {"type": "minecraft:model", "model": model or "mymod:item/" + name}})


def flat_item(name, texture):
    write(os.path.join(ASSETS, "models", "item", name + ".json"),
          OrderedDict([("parent", "minecraft:item/generated"), ("textures", {"layer0": texture})]))
    item_def(name)


def merge_tag(kind, tag, values):
    """Adds values to a tag file shared with other features (keeping what's there)."""
    ns, path = tag.split(":")
    file = os.path.join(DATA, ns, "tags", kind, path + ".json")
    obj = OrderedDict([("values", [])])
    if os.path.exists(file):
        with open(file) as f:
            obj = json.load(f, object_pairs_hook=OrderedDict)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(file, obj)


def merge_lang(entries):
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        obj = json.load(f, object_pairs_hook=OrderedDict)
    obj.update(entries)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ================================================================================================== textures

HERBS = ["wild_garlic", "wolfsbane", "yarrow", "radroot", "chamomile", "ginseng", "moonpetal", "water_mint"]
ITEMS = ["garlic", "roasted_garlic", "garlic_bread", "herb_stew", "yarrow_poultice", "wolfsbane_paste", "wolfsbane_arrow",
         "mint_bait", "moonpetal_bait", "anti_radiation_tea", "calming_tea", "vigor_tonic", "moonpetal_tea"]
MATERIALS = ["kettle_copper", "kettle_iron", "herbal_wood", "mortar_stone", "herb_paste", "garlic_skin", "braid_straw",
             "ward_twig", "ward_bloom", "ward_leaf", "string_white", "nail_iron"]

for name in HERBS:
    tex("block/" + name, getattr(art, name)())
for stage in range(4):
    tex(f"block/garlic_stage{stage}", art.garlic_stage(stage))
for name in ITEMS:
    tex("item/" + name, getattr(art, name)())
tex("item/garlic_braid", art.garlic_braid_item())
tex("item/wolfsbane_ward", art.wolfsbane_ward_item())
for name in MATERIALS:
    tex("block/herbalism/" + name, getattr(art, name)())
tex("mob_effect/garlic_breath", art.garlic_breath_icon())
tex("mob_effect/radioprotection", art.radioprotection_icon())
tex("mob_effect/calm", art.calm_icon())

# ================================================================================================== models

FACES = ("north", "east", "south", "west", "up", "down")


def auto_uv(face, a, b):
    """Vanilla's default UVs for a face (from the element's position), shifted into the 0-16 square when the element
    reaches outside the block (the spit's stakes go down into the campfire's block)."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    u0, v0, u1, v1 = {
        "down": (x0, 16 - z1, x1, 16 - z0),
        "up": (x0, z0, x1, z1),
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
        "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0),
        "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
    }[face]

    def fit(lo, hi):
        span = min(hi - lo, 16.0)
        lo = lo % 16.0 if (lo < 0 or hi > 16) else lo
        if lo + span > 16:
            lo = 16 - span
        return round(lo, 4), round(lo + span, 4)
    u0, u1 = fit(u0, u1)
    v0, v1 = fit(v0, v1)
    return [u0, v0, u1, v1]


def box(name, a, b, texture, skip=(), rot=None):
    """A cube element, every face textured from its own position. rot: (axis, angle, origin)."""
    e = OrderedDict([("name", name), ("from", [round(v, 4) for v in a]), ("to", [round(v, 4) for v in b])])
    if rot:
        axis, angle, origin = rot
        e["rotation"] = OrderedDict([("angle", angle), ("axis", axis), ("origin", list(origin))])
    e["faces"] = OrderedDict((f, OrderedDict([("uv", auto_uv(f, a, b)), ("texture", "#" + texture)]))
                             for f in FACES if f not in skip)
    return e


def turned(elements, angle, pivot):
    """Elements turned about the z axis (in the wall's plane) by angle degrees about pivot."""
    return rotate(copy(elements), rz(angle), pivot)


def check(name, elements):
    hits = find_overlaps(elements)
    if hits:
        for h in hits:
            print("z-fight:", name, h)
        raise SystemExit(f"{name}: {len(hits)} coplanar overlapping face pairs")


def block_model(name, elements, textures, parent="minecraft:block/block", ambient=True):
    check(name, elements)
    m = OrderedDict()
    m["parent"] = parent
    if not ambient:
        m["ambientocclusion"] = False
    m["textures"] = OrderedDict((k, "mymod:block/herbalism/" + v) for k, v in textures.items())
    m["elements"] = elements
    write(os.path.join(ASSETS, "models", "block", name + ".json"), m)
    return m


# --- the kettle (spout to the north) ---------------------------------------------------------------------------------

def kettle_elements():
    e = [
        box("foot", (4.5, 0, 4.5), (11.5, 0.75, 11.5), "copper"),
        box("belly", (3.5, 0.75, 3.5), (12.5, 5.5, 12.5), "copper"),
        box("shoulder", (4.25, 5.5, 4.25), (11.75, 7, 11.75), "copper"),
        box("neck", (5.25, 7, 5.25), (10.75, 7.75, 10.75), "copper"),
        box("lid", (5.75, 7.75, 5.75), (10.25, 8.5, 10.25), "copper"),
        box("knob_stem", (7.5, 8.5, 7.5), (8.5, 9, 8.5), "iron"),
        box("knob", (7.25, 9, 7.25), (8.75, 10, 8.75), "wood"),
        # The spout, angled up out of the front of the belly, and the lip round its mouth.
        box("spout", (7.1, 2.5, 0), (8.9, 4.1, 4.25), "copper", rot=("x", 45, (8, 3.3, 4.25))),
        box("spout_lip", (6.85, 2.25, -0.5), (9.15, 4.35, 0.25), "copper", rot=("x", 45, (8, 3.3, 4.25))),
        # A band round the belly where its two halves were joined.
        box("seam", (3.25, 3, 3.25), (12.75, 3.75, 12.75), "iron"),
        # The bail handle: an iron loop over the lid with a wooden grip.
        box("bail_west", (4.5, 7, 7.5), (5.25, 11, 8.5), "iron"),
        box("bail_east", (10.75, 7, 7.5), (11.5, 11, 8.5), "iron"),
        box("bail_top", (4.5, 11, 7.5), (11.5, 11.75, 8.5), "iron"),
        box("grip", (6.25, 10.75, 7.25), (9.75, 12, 8.75), "wood"),
    ]
    return e


def spit_elements():
    """The spit it hangs from over a campfire: forked stakes driven in either side of the fire (reaching down into
    the campfire's block), an iron bar across, and an S-hook down to the kettle's grip."""
    e = []
    for side, x in (("west", 0.25), ("east", 14.25)):
        e.append(box(f"stake_{side}", (x, -15.95, 7.25), (x + 1.5, 13, 8.75), "wood"))
        e.append(box(f"fork_{side}_out", (x - 0.25 if side == "west" else x + 0.75, 13, 7.25),
                     (x + 0.5 if side == "west" else x + 1.75, 15, 8.75), "wood"))
        e.append(box(f"fork_{side}_in", (x + 1.0 if side == "west" else x - 0.25, 13, 7.25),
                     (x + 1.75 if side == "west" else x + 0.5, 15, 8.75), "wood"))
    e.append(box("bar", (0.4, 13.25, 7.6), (15.6, 14.25, 8.4), "iron"))
    e.append(box("hook", (7.6, 12, 7.6), (8.4, 13.25, 8.4), "iron"))
    return e


KETTLE_TEX = {"copper": "kettle_copper", "iron": "kettle_iron", "wood": "herbal_wood", "particle": "kettle_copper"}
block_model("kettle", kettle_elements(), KETTLE_TEX)
block_model("kettle_hung", kettle_elements() + spit_elements(), KETTLE_TEX)


def check_over_campfire():
    """The hung kettle's spit reaches down into the campfire's block: check it against vanilla's campfire model too
    (read from the client jar, when there is one)."""
    import zipfile
    from gen_longswords import client_jar
    try:
        with zipfile.ZipFile(client_jar()) as jar:
            campfire = json.loads(jar.read("assets/minecraft/models/block/template_campfire.json"))["elements"]
    except (OSError, KeyError, SystemExit):
        print("herbalism: no client jar, skipped the campfire z-fight check")
        return
    below = [OrderedDict(e, name=f"campfire_{i}") for i, e in enumerate(campfire)]
    for e in below:
        e["from"] = [e["from"][0], e["from"][1] - 16, e["from"][2]]
        e["to"] = [e["to"][0], e["to"][1] - 16, e["to"][2]]
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"] = dict(e["rotation"], origin=[o[0], o[1] - 16, o[2]])
    hung = kettle_elements() + spit_elements()
    hits = find_overlaps(below + hung, only=set(range(len(below), len(below) + len(hung))))
    hits = [h for h in hits if not (str(h[0]).startswith("campfire_") and str(h[2]).startswith("campfire_"))]
    if hits:
        for h in hits:
            print("z-fight over campfire:", h)
        raise SystemExit("kettle_hung fights the campfire below it")


check_over_campfire()


# --- the mortar & pestle -------------------------------------------------------------------------------------------

def mortar_elements():
    pestle = (8.25, 3, 8.25)
    return [
        box("base", (4.5, 0, 4.5), (11.5, 1, 11.5), "stone"),
        box("wall_north", (3.5, 1, 3.5), (12.5, 5, 5.25), "stone"),
        box("wall_south", (3.5, 1, 10.75), (12.5, 5, 12.5), "stone"),
        box("wall_west", (3.5, 1, 5.25), (5.25, 5, 10.75), "stone"),
        box("wall_east", (10.75, 1, 5.25), (12.5, 5, 10.75), "stone"),
        box("lip_north", (3.25, 5, 3.25), (12.75, 5.75, 5), "stone"),
        box("lip_south", (3.25, 5, 11), (12.75, 5.75, 12.75), "stone"),
        box("lip_west", (3.25, 5, 5), (5, 5.75, 11), "stone"),
        box("lip_east", (11, 5, 5), (12.75, 5.75, 11), "stone"),
        box("paste", (5.25, 1, 5.25), (10.75, 3, 10.75), "paste"),
        box("pestle_head", (7, 2.5, 7), (9.5, 5, 9.5), "stone", rot=("z", -30, pestle)),
        box("pestle_handle", (7.5, 5, 7.5), (9, 12, 9), "wood", rot=("z", -30, pestle)),
        box("pestle_knob", (7.25, 12, 7.25), (9.25, 13, 9.25), "wood", rot=("z", -30, pestle)),
    ]


block_model("mortar_and_pestle", mortar_elements(),
            {"stone": "mortar_stone", "paste": "herb_paste", "wood": "herbal_wood", "particle": "mortar_stone"})


# --- the garlic braid (hung on the wall to the south, facing north) ---------------------------------------------------

def bulb(name, cx, y, cz, tilt):
    """A garlic bulb, rounded from three stacked layers: a plump base, a narrower shoulder and a crown, then the dry
    neck it's plaited in by and a tuft of roots underneath; the whole bulb leans tilt degrees toward the plait."""
    origin = (cx, y + 1.5, cz)
    r = ("z", tilt, origin)
    return [
        box(name + "_roots", (cx - 0.7, y - 0.5, cz - 0.7), (cx + 0.7, y, cz + 0.7), "straw", rot=r),
        box(name, (cx - 1.5, y, cz - 1.5), (cx + 1.5, y + 1.5, cz + 1.5), "skin", rot=r),
        box(name + "_shoulder", (cx - 1.2, y + 1.5, cz - 1.2), (cx + 1.2, y + 2.4, cz + 1.2), "skin", rot=r),
        box(name + "_crown", (cx - 0.7, y + 2.4, cz - 0.7), (cx + 0.7, y + 3.0, cz + 0.7), "skin", rot=r),
        box(name + "_neck", (cx - 0.35, y + 3.0, cz - 0.35), (cx + 0.35, y + 4.3, cz + 0.35), "straw", rot=r),
    ]


def braid_elements():
    e = [
        box("nail", (7.5, 14, 14.5), (8.5, 15, 16), "iron"),
        box("nail_head", (7.25, 13.75, 14.25), (8.75, 15.25, 14.5), "iron"),
        box("loop", (7.6, 12.25, 14.8), (8.4, 13.75, 15.2), "string"),
    ]
    # The plait: short lengths of stem twisted alternately left and right down the middle.
    for i in range(6):
        y = 10.75 - i * 1.75
        e.append(box(f"plait_{i}", (7, y, 13.75 + 0.1 * (i % 2)), (9, y + 1.75, 15.25 - 0.1 * (i % 2)), "straw",
                     rot=("z", 22.5 if i % 2 == 0 else -22.5, (8, y + 0.875, 14.5))))
    # Bulbs down both sides, each leaning in to the plait; one hangs at the bottom.
    for name, cx, y, cz, tilt in [
        ("bulb_top_left", 4.9, 8.6, 13.4, -24), ("bulb_top_right", 11.1, 8.2, 13.62, 22),
        ("bulb_mid_left", 4.6, 5.2, 13.71, -14), ("bulb_mid_right", 11.35, 4.8, 13.24, 16),
        ("bulb_low_left", 5.3, 1.9, 13.53, -30), ("bulb_low_right", 10.7, 1.6, 13.36, 28),
        ("bulb_tail", 8.05, -0.1, 12.95, 4),
    ]:
        e += bulb(name, cx, y, cz, tilt)
    return e


block_model("garlic_braid", braid_elements(),
            {"skin": "garlic_skin", "straw": "braid_straw", "string": "string_white", "iron": "nail_iron",
             "particle": "garlic_skin"})


# --- the wolfsbane ward (a wreath on the wall to the south, facing north) ----------------------------------------------

def ward_elements():
    centre = (8, 8, 15)
    e = []
    # The ring: twelve lengths of twig round the circle, every other one set a little further out from the wall so no
    # two faces share a plane.
    for k in range(12):
        z0, z1 = (14.25, 15.75) if k % 2 == 0 else (14.4, 15.9)
        seg = box(f"twig_{k}", (6.25, 12.5, z0), (9.75, 14, z1), "twig")
        e += turned([seg], k * 30 + 15, centre)
    # Violet blooms with a leaf each, at four places round the ring.
    for n, angle in enumerate((40, 130, 215, 310)):
        cluster = [
            box(f"bloom_{n}_a", (6.6, 12.2, 13.3), (8.1, 13.9, 14.3), "bloom"),
            box(f"bloom_{n}_b", (8.3, 12.7, 13.5), (9.6, 14.3, 14.4), "bloom"),
            box(f"bloom_{n}_c", (7.4, 13.6, 13.7), (8.8, 15.0, 14.5), "bloom"),
            box(f"leaf_{n}", (5.6, 12.6, 13.9), (7.2, 13.4, 14.35), "leaf"),
        ]
        e += turned(cluster, angle, centre)
    # A white bow tied at the bottom, and the nail it hangs from at the top.
    e += [
        box("bow_knot", (7.4, 1.6, 13.6), (8.6, 3.0, 14.2), "string"),
        box("bow_loop_left", (5.6, 2.0, 13.75), (7.4, 3.2, 14.05), "string", rot=("z", 15, (7.4, 2.4, 13.9))),
        box("bow_loop_right", (8.6, 2.0, 13.75), (10.4, 3.2, 14.05), "string", rot=("z", -15, (8.6, 2.4, 13.9))),
        box("bow_tail_left", (6.9, 0.3, 13.8), (7.5, 1.7, 14.0), "string", rot=("z", 20, (7.2, 1.6, 13.9))),
        box("bow_tail_right", (8.5, 0.3, 13.8), (9.1, 1.7, 14.0), "string", rot=("z", -20, (8.8, 1.6, 13.9))),
        box("nail", (7.6, 14.6, 14.8), (8.4, 15.4, 16), "iron"),
        box("nail_head", (7.35, 14.35, 14.55), (8.65, 15.65, 14.8), "iron"),
    ]
    return e


block_model("wolfsbane_ward", ward_elements(),
            {"twig": "ward_twig", "bloom": "ward_bloom", "leaf": "ward_leaf", "string": "string_white", "iron": "nail_iron",
             "particle": "ward_bloom"})

# --- plants ----------------------------------------------------------------------------------------------------------

for name in HERBS:
    write(os.path.join(ASSETS, "models", "block", name + ".json"),
          OrderedDict([("parent", "minecraft:block/cross"), ("textures", {"cross": "mymod:block/" + name})]))
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": "mymod:block/" + name}}})
    flat_item(name, "mymod:block/" + name)
for stage in range(4):
    write(os.path.join(ASSETS, "models", "block", f"garlic_stage{stage}.json"),
          OrderedDict([("parent", "minecraft:block/crop"), ("textures", {"crop": f"mymod:block/garlic_stage{stage}"})]))
write(os.path.join(ASSETS, "blockstates", "garlic_crop.json"),
      {"variants": {f"age={a}": {"model": f"mymod:block/garlic_stage{a}"} for a in range(4)}})

# --- block states, items ---------------------------------------------------------------------------------------------
Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_variants(model, extra=""):
    out = OrderedDict()
    for f, y in Y.items():
        v = OrderedDict([("model", model)])
        if y:
            v["y"] = y
        out[f"facing={f}{extra}"] = v
    return out


kettle_states = OrderedDict()
for hung in ("false", "true"):
    for f, y in Y.items():
        for tea in ("none", "steeping", "ready"):
            for water in range(4):
                v = OrderedDict([("model", "mymod:block/kettle" + ("_hung" if hung == "true" else ""))])
                if y:
                    v["y"] = y
                kettle_states[f"facing={f},hung={hung},tea={tea},water={water}"] = v
write(os.path.join(ASSETS, "blockstates", "kettle.json"), {"variants": kettle_states})
for name in ("mortar_and_pestle", "garlic_braid", "wolfsbane_ward"):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": facing_variants("mymod:block/" + name)})

for name in ("kettle", "mortar_and_pestle"):
    item_def(name, "mymod:block/" + name)
flat_item("garlic_braid", "mymod:item/garlic_braid")
flat_item("wolfsbane_ward", "mymod:item/wolfsbane_ward")
for name in ITEMS:
    flat_item(name, "mymod:item/" + name)

# ================================================================================================== data


def drop_self(name):
    return OrderedDict([
        ("type", "minecraft:block"),
        ("pools", [OrderedDict([
            ("condition", {"type": "minecraft:survives_explosion"}),
            ("entries", [OrderedDict([("type", "minecraft:item"), ("name", "mymod:" + name)])]),
            ("rolls", 1)])]),
        ("random_sequence", "mymod:blocks/" + name)])


for name in HERBS[1:] + ["kettle", "mortar_and_pestle", "garlic_braid", "wolfsbane_ward"]:
    write(os.path.join(MOD, "loot_table", "blocks", name + ".json"), drop_self(name))

# Wild garlic: one or two bulbs (more with Fortune), or the plant itself with shears or Silk Touch.
write(os.path.join(MOD, "loot_table", "blocks", "wild_garlic.json"), OrderedDict([
    ("type", "minecraft:block"),
    ("modifier", {"type": "minecraft:explosion_decay"}),
    ("pools", [OrderedDict([
        ("entries", [OrderedDict([
            ("type", "minecraft:alternatives"),
            ("children", [
                OrderedDict([
                    ("type", "minecraft:item"),
                    ("condition", {"type": "minecraft:any_of", "terms": ["minecraft:tool/can_shear", "minecraft:tool/can_silk_touch"]}),
                    ("name", "mymod:wild_garlic")]),
                OrderedDict([
                    ("type", "minecraft:item"),
                    ("modifier", [
                        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                        {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                         "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}}]),
                    ("name", "mymod:garlic")])])])]),
        ("rolls", 1)])]),
    ("random_sequence", "mymod:blocks/wild_garlic")]))

# The crop: a bulb always, and two to four more when ripe (as carrots).
write(os.path.join(MOD, "loot_table", "blocks", "garlic_crop.json"), OrderedDict([
    ("type", "minecraft:block"),
    ("modifier", {"type": "minecraft:explosion_decay"}),
    ("pools", [
        OrderedDict([("entries", [{"type": "minecraft:item", "name": "mymod:garlic"}]), ("rolls", 1)]),
        OrderedDict([
            ("condition", {"type": "minecraft:match_block", "blocks": "mymod:garlic_crop", "state": {"age": "3"}}),
            ("entries", [OrderedDict([
                ("type", "minecraft:item"),
                ("modifier", {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                              "formula": "minecraft:binomial_with_bonus_count",
                              "parameters": {"extra": 3, "probability": 0.5714286}}),
                ("name", "mymod:garlic")])]),
            ("rolls", 1)])]),
    ("random_sequence", "mymod:blocks/garlic_crop")]))

# --- recipes and their recipe-book advancements --------------------------------------------------------------------
BOOK = {"misc": "misc", "equipment": "combat", "building": "building_blocks", "redstone": "redstone", "food": "food",
        "blocks": "decorations"}


def recipe(name, body, unlocked_by, book=None):
    write(os.path.join(MOD, "recipe", name + ".json"), body)
    folder = book or BOOK.get(body.get("category", "misc"), "misc")
    criteria = OrderedDict()
    for i, item in enumerate(unlocked_by):
        criteria[f"has_{item.split(':')[1]}"] = OrderedDict([
            ("conditions", {"items": [{"items": item}]}), ("trigger", "minecraft:inventory_changed")])
    criteria["has_the_recipe"] = OrderedDict([
        ("conditions", {"recipes": "mymod:" + name}), ("trigger", "minecraft:recipe_unlocked")])
    write(os.path.join(MOD, "advancement", "recipes", folder, name + ".json"), OrderedDict([
        ("parent", "minecraft:recipes/root"),
        ("criteria", criteria),
        ("requirements", [["has_the_recipe"] + [k for k in criteria if k != "has_the_recipe"]]),
        ("rewards", {"recipes": ["mymod:" + name]})]))


def shaped(category, pattern, key, result, count=1):
    r = OrderedDict([("type", "minecraft:crafting_shaped"), ("category", category), ("key", OrderedDict(key)),
                     ("pattern", pattern), ("result", OrderedDict([("count", count), ("id", result)]) if count > 1
                                            else {"id": result})])
    return r


def shapeless(category, ingredients, result, count=1, group=None):
    r = OrderedDict([("type", "minecraft:crafting_shapeless"), ("category", category)])
    if group:
        r["group"] = group
    r["ingredients"] = ingredients
    r["result"] = OrderedDict([("count", count), ("id", result)]) if count > 1 else {"id": result}
    return r


def cooking(kind, ingredient, result, time, xp=0.35):
    return OrderedDict([("type", "minecraft:" + kind), ("category", "food"), ("cookingtime", time), ("experience", xp),
                        ("ingredient", ingredient), ("result", {"id": result})])


recipe("kettle", shaped("misc", [" N ", "C C", "CCC"], [("N", "minecraft:iron_nugget"), ("C", "minecraft:copper_ingot")],
                        "mymod:kettle"), ["minecraft:copper_ingot"], book="decorations")
recipe("mortar_and_pestle", shaped("misc", ["  S", "B B", " B "], [("S", "minecraft:stick"), ("B", "#minecraft:stone_tool_materials")],
                                   "mymod:mortar_and_pestle"), ["minecraft:cobblestone"], book="decorations")
recipe("garlic_braid", shaped("misc", [" S ", "GGG", "GGG"], [("S", "minecraft:string"), ("G", "mymod:garlic")], "mymod:garlic_braid"),
       ["mymod:garlic"], book="decorations")
recipe("wolfsbane_ward", shaped("misc", ["TWT", "W W", "TWT"], [("T", "minecraft:stick"), ("W", "mymod:wolfsbane")],
                                "mymod:wolfsbane_ward"), ["mymod:wolfsbane"], book="decorations")
recipe("wolfsbane_arrow", shaped("equipment", ["AAA", "APA", "AAA"], [("A", "minecraft:arrow"), ("P", "mymod:wolfsbane_paste")],
                                 "mymod:wolfsbane_arrow", 8), ["mymod:wolfsbane_paste"])
recipe("garlic_bread", shapeless("misc", ["minecraft:bread", "mymod:garlic"], "mymod:garlic_bread"), ["mymod:garlic"], book="food")
recipe("herb_stew", shapeless("misc", ["minecraft:bowl", "mymod:garlic", "mymod:yarrow", "minecraft:carrot", "minecraft:potato"],
                              "mymod:herb_stew"), ["mymod:garlic"], book="food")
recipe("roasted_garlic", cooking("smelting", "mymod:garlic", "mymod:roasted_garlic", 200), ["mymod:garlic"])
recipe("roasted_garlic_from_smoking", cooking("smoking", "mymod:garlic", "mymod:roasted_garlic", 100), ["mymod:garlic"])
recipe("roasted_garlic_from_campfire_cooking", cooking("campfire_cooking", "mymod:garlic", "mymod:roasted_garlic", 600),
       ["mymod:garlic"])
# Yarrow in the survival kit: alternative bandage and first aid kit recipes.
recipe("bandage_with_yarrow", shapeless("misc", ["minecraft:paper", "minecraft:paper", "minecraft:string", "mymod:yarrow"],
                                        "mymod:bandage", 3), ["mymod:yarrow"])
recipe("first_aid_kit_with_yarrow", shaped("misc", ["LFL", "BPB"], [
    ("L", "minecraft:leather"), ("F", "#minecraft:small_flowers"), ("B", "mymod:bandage"), ("P", "mymod:yarrow_poultice")],
    "mymod:first_aid_kit"), ["mymod:yarrow_poultice"])
for herb, dye in (("wolfsbane", "purple_dye"), ("yarrow", "white_dye"), ("chamomile", "yellow_dye"),
                  ("moonpetal", "light_blue_dye"), ("water_mint", "magenta_dye")):
    recipe(f"{dye}_from_{herb}", shapeless("misc", ["mymod:" + herb], "minecraft:" + dye, group=dye), ["mymod:" + herb])

# --- tags ------------------------------------------------------------------------------------------------------------
FLOWERS = ["mymod:wolfsbane", "mymod:yarrow", "mymod:chamomile", "mymod:moonpetal", "mymod:water_mint"]
merge_tag("block", "minecraft:small_flowers", FLOWERS)
merge_tag("item", "minecraft:small_flowers", FLOWERS)
merge_tag("block", "minecraft:crops", ["mymod:garlic_crop"])
merge_tag("block", "minecraft:maintains_farmland", ["mymod:garlic_crop"])
merge_tag("block", "minecraft:mineable/pickaxe", ["mymod:kettle", "mymod:mortar_and_pestle"])
merge_tag("block", "minecraft:enderman_holdable", ["mymod:" + h for h in HERBS])
merge_tag("item", "minecraft:villager_plantable_seeds", ["mymod:garlic"])
merge_tag("item", "minecraft:villager_picks_up", ["mymod:garlic"])
merge_tag("item", "minecraft:arrows", ["mymod:wolfsbane_arrow"])
merge_tag("item", "c:crops/garlic", ["mymod:garlic"])
merge_tag("item", "c:crops", ["#c:crops/garlic"])
merge_tag("item", "c:foods/vegetable", ["mymod:garlic"])
merge_tag("item", "c:foods/bread", ["mymod:garlic_bread"])
merge_tag("item", "c:foods/soup", ["mymod:herb_stew"])
merge_tag("item", "c:foods/cooked_vegetable", ["mymod:roasted_garlic"])
merge_tag("item", "c:drinks", ["mymod:anti_radiation_tea", "mymod:calming_tea", "mymod:vigor_tonic", "mymod:moonpetal_tea"])

# --- worldgen: each herb as a patch scattered round a point, like vanilla flowers ------------------------------------
PATCHES = {
    # herb: (one patch in this many chunks, tries per patch, spread)
    "wild_garlic": (10, 12, 4),
    "wolfsbane": (24, 8, 4),
    "yarrow": (8, 12, 5),
    "radroot": (8, 10, 5),
    "chamomile": (3, 16, 5),
    "ginseng": (28, 5, 3),
    "moonpetal": (3, 12, 6),
    "water_mint": (6, 12, 4),
}


def trapezoid(n):
    return OrderedDict([("type", "minecraft:trapezoid"), ("max", n), ("min", -n), ("plateau", 0)])


for herb, (rarity, count, spread) in PATCHES.items():
    write(os.path.join(MOD, "worldgen", "feature", herb + ".json"),
          OrderedDict([("type", "minecraft:simple_block"), ("to_place", {"id": "mymod:" + herb})]))
    write(os.path.join(MOD, "worldgen", "placed_feature", herb + "_patch.json"), OrderedDict([
        ("feature", "mymod:" + herb),
        ("placement", [
            {"type": "minecraft:rarity_filter", "chance": rarity},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
            {"type": "minecraft:biome"},
            {"type": "minecraft:count", "count": count},
            OrderedDict([("type", "minecraft:offset"), ("x", trapezoid(spread)), ("y", trapezoid(2)), ("z", trapezoid(spread))]),
            {"type": "minecraft:block_predicate_filter",
             "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
        ])]))

# --- lang ------------------------------------------------------------------------------------------------------------
LANG = OrderedDict([
    ("block.mymod.wild_garlic", "Wild Garlic"),
    ("block.mymod.wild_garlic.desc", "Plains and forests. Drops garlic bulbs to plant on farmland"),
    ("block.mymod.wolfsbane", "Wolfsbane"),
    ("block.mymod.wolfsbane.desc", "Rare, in taigas. Grind into paste against werewolves, or weave a ward"),
    ("block.mymod.yarrow", "Yarrow"),
    ("block.mymod.yarrow.desc", "Meadows and plains. Grind into a poultice that stops bleeding"),
    ("block.mymod.radroot", "Radroot"),
    ("block.mymod.radroot.desc", "Badlands. Brew in a kettle for Anti-Radiation Tea"),
    ("block.mymod.chamomile", "Chamomile"),
    ("block.mymod.chamomile.desc", "Flower forests and meadows. Brew in a kettle for Calming Tea"),
    ("block.mymod.ginseng", "Ginseng"),
    ("block.mymod.ginseng.desc", "Rare, in dark forests. Brew in a kettle for a Vigor Tonic"),
    ("block.mymod.moonpetal", "Moonpetal"),
    ("block.mymod.moonpetal.desc", "The Feywild. Brew for Moonpetal Tea, or grind into bait"),
    ("block.mymod.water_mint", "Water Mint"),
    ("block.mymod.water_mint.desc", "Swamps. Grind into Mint Bait for bottom feeders"),
    ("block.mymod.garlic_crop", "Garlic"),
    ("block.mymod.kettle", "Kettle"),
    ("block.mymod.kettle.desc", "Hang over a campfire, fill with water and drop in a tea herb. Pour with glass bottles"),
    ("block.mymod.mortar_and_pestle", "Mortar & Pestle"),
    ("block.mymod.mortar_and_pestle.desc", "Use yarrow, wolfsbane, water mint or moonpetal on it to grind them"),
    ("block.mymod.garlic_braid", "Garlic Braid"),
    ("block.mymod.garlic_braid.desc", "Hang on a wall: vampires won't come within 8 blocks"),
    ("block.mymod.wolfsbane_ward", "Wolfsbane Ward"),
    ("block.mymod.wolfsbane_ward.desc", "Hang on a wall: cursed villagers within 16 blocks won't change on a full moon"),
    ("item.mymod.garlic", "Garlic"),
    ("item.mymod.garlic.desc", "Plant on farmland, or eat raw for a brief Garlic Breath that vampires won't bite through"),
    ("item.mymod.roasted_garlic", "Roasted Garlic"),
    ("item.mymod.roasted_garlic.desc", "Soft and sweet: all the flavour, none of the breath"),
    ("item.mymod.garlic_bread", "Garlic Bread"),
    ("item.mymod.garlic_bread.desc", "Filling, with a little Garlic Breath"),
    ("item.mymod.herb_stew", "Herb Stew"),
    ("item.mymod.herb_stew.desc", "Hearty, and heals a little"),
    ("item.mymod.yarrow_poultice", "Yarrow Poultice"),
    ("item.mymod.yarrow_poultice.desc", "Press onto a wound to stop bleeding"),
    ("item.mymod.wolfsbane_paste", "Wolfsbane Paste"),
    ("item.mymod.wolfsbane_paste.desc", "Use with a blade in your other hand to coat it: coated hits bite werewolves through their hide"),
    ("item.mymod.wolfsbane_arrow", "Wolfsbane Arrow"),
    ("item.mymod.mint_bait", "Mint Bait"),
    ("item.mymod.moonpetal_bait", "Moonpetal Bait"),
    ("item.mymod.anti_radiation_tea", "Anti-Radiation Tea"),
    ("item.mymod.anti_radiation_tea.desc", "Draws off radiation sickness, and halves new doses for two minutes"),
    ("item.mymod.calming_tea", "Calming Tea"),
    ("item.mymod.calming_tea.desc", "Settles nausea and steadies you against a Cathode Walker's static"),
    ("item.mymod.vigor_tonic", "Vigor Tonic"),
    ("item.mymod.vigor_tonic.desc", "Haste and Speed for three minutes"),
    ("item.mymod.moonpetal_tea", "Moonpetal Tea"),
    ("item.mymod.moonpetal_tea.desc", "Night vision for five minutes"),
    ("effect.mymod.garlic_breath", "Garlic Breath"),
    ("effect.mymod.radioprotection", "Radioprotection"),
    ("effect.mymod.calm", "Calm"),
    ("tooltip.mymod.wolfsbane_coating", "Wolfsbane-coated (%s hits)"),
    ("message.mymod.wolfsbane_paste.no_blade", "Hold a blade in your other hand to coat it"),
    ("message.mymod.kettle.empty", "The kettle is empty: fill it with water"),
    ("message.mymod.kettle.water", "%s cups of water: drop in a tea herb"),
    ("message.mymod.kettle.cold", "Steeping, but it needs heat: hang it over a lit campfire"),
    ("message.mymod.kettle.steeping", "Steeping: %s%%"),
    ("message.mymod.kettle.ready", "Tea's ready: %s cups left"),
])
merge_lang(LANG)

print("herbalism: textures, models, data and lang written")

if PREVIEW:
    import preview_herbalism
    preview_herbalism.render_sheet(ASSETS, PREVIEW)
