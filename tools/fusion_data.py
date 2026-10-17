"""Item Fusing data, written by tools/gen_fusion.py: the approved fusions and their riddles, the fusion files for the
built ones, the Fusion Reactor's recipe, advancement and loot, the item and block tags, and the lang.

A later batch adding a fusion: build the item, move its name into BUILT (or write its data/tomyen/fusion/<name>.json
by hand, the format is in FusionRecipeLoader.java), and rerun python3 tools/gen_fusion.py. The riddles of every
approved fusion are already here and in en_us.json."""
import json, os
from collections import OrderedDict

from gunkit import write


def o(*items):
    return OrderedDict(items)


# Every approved fusion: result, inputs (either order), station, tier, riddle.
FUSIONS = [
    ("swordfish_sword", ["tomyen:swordfish", "minecraft:diamond_sword"], "pond", 1,
     "The fish already carries a sword. Give it a better one."),
    ("fish_fish_fish", ["tomyen:fish_fish", "tomyen:fish_fish"], "pond", 1,
     "A fish, and another fish. What could they possibly become?"),
    ("rocket_boots", ["tomyen:big_red_boots", "tomyen:soda_jetpack"], "pond", 1,
     "Red rubber feet and a fizzing back, shaken together, leave the ground twice."),
    ("murder_wings", ["tomyen:crow_feather", "minecraft:elytra"], "reactor", 1,
     "One black feather, two borrowed wings. Only great power binds a murder to them."),
    ("stormcrow_rig", ["tomyen:rocket_boots", "tomyen:murder_wings"], "reactor", 2,
     "When the crow's wings wear the rocket's boots, the sky stops being a fall."),
    ("chum_cannon", ["tomyen:fish_gun", "tomyen:remington_870"], "pond", 1,
     "The gun that throws fish envies the gun that throws a spread."),
    ("slide_hammer", ["tomyen:trombone", "tomyen:iron_long_sword"], "pond", 1,
     "A long blade dreams of the trombone's slide."),
    ("soul_gale", ["tomyen:leaf_blower", "tomyen:soul_vacuum"], "reactor", 1,
     "One blows leaves, one swallows souls. With power enough, the wind howls with the dead."),
    ("barbel_lash", ["tomyen:nose_hair_whip", "tomyen:catfish_whisker"], "pond", 1,
     "A whip of nose hair seeks its cousin on a catfish's lip."),
    ("harpoon_hookshot", ["tomyen:grapple_claw", "tomyen:harpoon_gun"], "pond", 1,
     "A claw that clings and a gun that spears: one throw, both catches."),
    ("holy_ray", ["tomyen:crucifix", "tomyen:ray_gun"], "reactor", 1,
     "The cross and the ray gun pray to the same light. Only a reactor can ordain them."),
    ("grave_auger", ["tomyen:gravedigger_shovel", "tomyen:steel_drill"], "pond", 1,
     "The gravedigger's shovel wishes it could spin."),
    ("antler_pike", ["tomyen:tusk_spear", "tomyen:moose_antler"], "pond", 1,
     "A tusk-tipped spear wants a crown from the north woods."),
    ("astral_visor", ["tomyen:tv_helmet", "tomyen:starlight_lens"], "reactor", 1,
     "A television head that would watch the stars needs a lens, and a great deal of power."),
    ("phantasmal_chestplate", ["tomyen:ectoplasm", "tomyen:holy_silver_chestplate"], "reactor", 1,
     "Holy silver can hold a ghost, if the ghost is pressed in hard enough."),
    ("wolfhide_carrier", ["tomyen:werewolf_pelt", "tomyen:plate_carrier"], "pond", 1,
     "Plates sewn into the hide of a moonlit beast."),
    ("hexed_rod", ["tomyen:hag_charm", "tomyen:fae_rod"], "pond", 1,
     "A hag's trinket tangled in a fae line."),
    ("tuskaxe", ["tomyen:boar_tusk", "tomyen:bloom_steel_axe"], "pond", 1,
     "Bloom steel bites. A boar's tusk bites harder."),
    ("moonblade", ["tomyen:moonscale_koi", "tomyen:fae_silver_sword"], "pond", 1,
     "The koi that swallowed the moon swims toward fae silver."),
    ("pufferblade", ["tomyen:ember_puffer", "tomyen:magmasteel_sword"], "pond", 1,
     "A fish that smoulders and swells, wed to a blade born in magma."),
    ("leviathan_blade", ["tomyen:swordfish_sword", "tomyen:moonblade"], "reactor", 2,
     "When the swordfish's sword meets the moon's blade, the deep wakes. No pond can hold it."),
    ("revenant_mail", ["tomyen:phantasmal_chestplate", "tomyen:wolfhide_carrier"], "reactor", 2,
     "Ghost-pressed silver and wolf-hide plates, made one by power, refuse to stay dead."),
    ("writ_of_absolution", ["tomyen:extension_form", "tomyen:crucifix"], "pond", 1,
     "Paperwork and a prayer: the curse and the creditor both must wait."),
    ("gnome_lance", ["tomyen:garden_gnome", "tomyen:tusk_spear"], "pond", 1,
     "A little garden guardian on a spear's point charges hardest at a gallop."),
    ("whiskerline_rod", ["tomyen:giant_catfish", "tomyen:grapple_claw"], "pond", 1,
     "The river king's whisker and a grappler's claw: a line that reels in anything."),
    ("feather_duster", ["tomyen:shrimp_pistol", "tomyen:crow_feather"], "pond", 1,
     "A crow's feather on a shrimp's dart flies quiet and finds its way."),
]
BUILT = {"swordfish_sword", "fish_fish_fish", "rocket_boots", "murder_wings", "stormcrow_rig"}
BUILT |= {"barbel_lash", "grave_auger", "hexed_rod", "whiskerline_rod", "writ_of_absolution"}  # batch 4
BUILT |= {"slide_hammer", "antler_pike", "tuskaxe", "moonblade", "pufferblade", "gnome_lance", "leviathan_blade"}  # batch 2

LANG = o(
    ("item.tomyen.fused", "Fused"),
    ("item.tomyen.swordfish_sword", "Swordfish Sword"),
    ("item.tomyen.swordfish_sword.lunge", "Use in water: lunge where you look"),
    ("item.tomyen.swordfish_sword.submerged", "+4 damage while under water"),
    ("item.tomyen.swordfish_sword.swim", "Swim fast while it's in hand"),
    ("item.tomyen.fish_fish_fish", "Fish Fish Fish"),
    ("item.tomyen.fish_fish_fish.club", "A club made of fish. It is not a good club."),
    ("item.tomyen.fish_fish_fish.fish", "Now and then, what it hits becomes a fish"),
    ("item.tomyen.fish_fish_fish.converted", "%s is a fish now."),
    ("item.tomyen.rocket_boots", "Rocket Boots"),
    ("item.tomyen.rocket_boots.jump", "Jump again in mid-air to rocket up"),
    ("item.tomyen.rocket_boots.dash", "Tap sprint in mid-air to dash"),
    ("item.tomyen.rocket_boots.falls", "Halves fall damage"),
    ("item.tomyen.murder_wings", "Murder Wings"),
    ("item.tomyen.murder_wings.glide", "Glides like an elytra"),
    ("item.tomyen.murder_wings.crows", "Crows come to peck whatever you hurt"),
    ("item.tomyen.stormcrow_rig", "Stormcrow Rig"),
    ("item.tomyen.stormcrow_rig.fly", "Hold jump while gliding to fly under power"),
    ("item.tomyen.stormcrow_rig.cost", "Thrust wears it down; repair with Star Steel"),
    ("item.tomyen.stormcrow_rig.crows", "Crows come to peck whatever you hurt"),
    ("block.tomyen.fusion_reactor", "Fusion Reactor"),
    ("container.tomyen.fusion_reactor", "Fusion Reactor"),
    ("fusion.tomyen.pond.fizzle", "The pond fizzles. These two will never be one."),
    ("fusion.tomyen.pond.needs_power", "The pond strains... it needs more power."),
    ("fusion.tomyen.pond.failed", "The pond swallows the dust... and spits them back."),
    ("fusion.tomyen.hint.villager", "%s leans in: \"%s\""),
    ("fusion.tomyen.hint.fairy", "A fairy whispers: \"%s\""),
    ("fusion.tomyen.shimmer", "It hums near something you carry..."),
)
for _name, _inputs, _station, _tier, _riddle in FUSIONS:
    LANG[f"fusion.tomyen.{_name}.riddle"] = _riddle


def merge_lang(assets):
    path = os.path.join(assets, "lang", "en_us.json")
    lang = json.load(open(path, encoding="utf-8"), object_pairs_hook=OrderedDict)
    lang.update(LANG)
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")


def merge_tag(data, path, values):
    full = os.path.join(data, path)
    tag = json.load(open(full), object_pairs_hook=OrderedDict) if os.path.exists(full) else o(("values", []))
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write(full, tag)


def write_data(data, assets):
    for name, inputs, station, tier, riddle in FUSIONS:
        if name in BUILT:
            write(os.path.join(data, "tomyen", "fusion", name + ".json"),
                  o(("inputs", inputs), ("result", "tomyen:" + name), ("station", station), ("tier", tier)))
    # The reactor: the Alien Reactor, Star Steel, a Fusion Core and Void Flux in an alien alloy and steel casing.
    write(os.path.join(data, "tomyen", "recipe", "fusion_reactor.json"), o(
        ("type", "minecraft:crafting_shaped"), ("category", "misc"),
        ("key", o(("L", "tomyen:alien_alloy"), ("S", "tomyen:star_steel_ingot"), ("V", "tomyen:void_flux"), ("A", "tomyen:alien_reactor"),
                  ("F", "tomyen:fusion_core"), ("P", "tomyen:steel_plate"))),
        ("pattern", ["LSL", "VAV", "PFP"]),
        ("result", o(("count", 1), ("id", "tomyen:fusion_reactor")))))
    write(os.path.join(data, "tomyen", "advancement", "recipes", "misc", "fusion_reactor.json"), o(
        ("parent", "minecraft:recipes/root"),
        ("criteria", o(("has_item", o(("conditions", o(("items", [o(("items", "tomyen:alien_reactor"))]))),
                                     ("trigger", "minecraft:inventory_changed"))),
                       ("has_the_recipe", o(("conditions", o(("recipes", "tomyen:fusion_reactor"))),
                                            ("trigger", "minecraft:recipe_unlocked"))))),
        ("requirements", [["has_item", "has_the_recipe"]]),
        ("rewards", o(("recipes", ["tomyen:fusion_reactor"])))))
    write(os.path.join(data, "tomyen", "loot_table", "blocks", "fusion_reactor.json"), o(
        ("type", "minecraft:block"),
        ("pools", [o(("condition", o(("type", "minecraft:survives_explosion"))),
                     ("entries", [o(("type", "minecraft:item"), ("name", "tomyen:fusion_reactor"))]), ("rolls", 1))]),
        ("random_sequence", "tomyen:blocks/fusion_reactor")))
    merge_tag(data, "minecraft/tags/block/mineable/pickaxe.json", ["tomyen:fusion_reactor"])
    merge_tag(data, "minecraft/tags/block/needs_diamond_tool.json", ["tomyen:fusion_reactor"])
    merge_tag(data, "minecraft/tags/item/swords.json", ["tomyen:swordfish_sword", "tomyen:fish_fish_fish"])
    merge_tag(data, "minecraft/tags/item/foot_armor.json", ["tomyen:rocket_boots"])
    merge_tag(data, "minecraft/tags/item/enchantable/durability.json", ["tomyen:murder_wings", "tomyen:stormcrow_rig"])
    merge_tag(data, "minecraft/tags/item/enchantable/equippable.json", ["tomyen:murder_wings", "tomyen:stormcrow_rig"])
    merge_tag(data, "tomyen/tags/item/repairs_swordfish_sword.json", ["minecraft:diamond"])
    merge_tag(data, "tomyen/tags/item/repairs_fish_fish_fish.json", ["tomyen:fish_fish"])
    merge_tag(data, "tomyen/tags/block/fae_pond_markers.json", ["tomyen:fae_bloom", "tomyen:fae_mushroom"])
    merge_tag(data, "tomyen/tags/entity_type/fish_fish_fish_immune.json",
              ["#tomyen:soul_vacuum_immune", "#tomyen:soul_vacuum_resistant", "tomyen:giant_catfish"])
    merge_lang(assets)
