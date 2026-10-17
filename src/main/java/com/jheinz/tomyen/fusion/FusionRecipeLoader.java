package com.jheinz.tomyen.fusion;

/*
 * ITEM FUSING: data files
 * =======================
 * One file per fusion: data/<namespace>/fusion/<result name>.json (this mod's are in data/tomyen/fusion/).
 *
 * {
 *   "inputs": ["tomyen:swordfish", "minecraft:diamond_sword"],   required: exactly two; each an item id or "#ns:tag".
 *                                                                Order doesn't matter. The same id twice means two of
 *                                                                that item (two dropped stacks, or one stack of 2+).
 *   "result": "tomyen:swordfish_sword",                         required: an item id, or {"id": "...", "count": 2}
 *   "station": "pond",                                          pond | reactor | either (default pond).
 *                                                                pond: a fae pond with Fae Dust (70% chance) or the
 *                                                                reactor; reactor: the Fusion Reactor only (a pond
 *                                                                hints that it "needs more power"); either: both.
 *   "tier": 1,                                                  1 (default), or 2 for fusing two fused items. Tier 2
 *                                                                takes the reactor twice as long.
 *   "riddle": "fusion.tomyen.swordfish_sword.riddle"            optional translation key of its hint riddle;
 *                                                                default "fusion.<namespace>.<file name>.riddle"
 * }
 *
 * A file whose result or inputs don't resolve, or that fails to parse, is logged and skipped. Unknown keys are
 * ignored. Pond fusions should land about one tier above their inputs; reactor fusions go past Exotic.
 *
 * HOW TO ADD A FUSION (later batches: follow these steps exactly)
 * 1. Register the result item in your own feature package (or in fusion/FusionItems.java next to the first five),
 *    with its behaviour, a tooltip, its model/texture, and its lang name. Fusion items have no crafting recipe.
 * 2. Write data/tomyen/fusion/<result>.json as above. The riddle is already in en_us.json for every approved fusion
 *    (keys "fusion.tomyen.<result>.riddle", listed below); for a new one, add a short cryptic riddle there.
 * 3. If the result is a weapon/armor piece, add it to the matching vanilla item tags (minecraft:swords,
 *    minecraft:foot_armor, ... and the enchantable ones) so it takes enchantments.
 * 4. Run ./gradlew build, then in a dev client: /reload, check the log for "Loaded N fusions" and no "Skipping
 *    fusion", then throw the pair plus 1 Fae Dust into a pond (or use the reactor).
 * Nothing else: the pond, the reactor, the tooltip shimmer and the villager/fairy hints all read these files.
 *
 * THE APPROVED LIST (result id: inputs, station). Built: the first five. The rest have riddles only.
 *   swordfish_sword        swordfish + diamond_sword                         pond     (built)
 *   fish_fish_fish         fish_fish + fish_fish                             pond     (built)
 *   rocket_boots           big_red_boots + soda_jetpack                      pond     (built)
 *   murder_wings           crow_feather + minecraft:elytra                   reactor  (built)
 *   stormcrow_rig          rocket_boots + murder_wings                       reactor  tier 2 (built)
 *   chum_cannon            fish_gun + remington_870                          pond
 *   slide_hammer           trombone + iron_long_sword                        pond
 *   soul_gale              leaf_blower + soul_vacuum                         reactor
 *   barbel_lash            nose_hair_whip + catfish_whisker                  pond
 *   harpoon_hookshot       grapple_claw + harpoon_gun                        pond
 *   holy_ray               crucifix + ray_gun                                reactor
 *   grave_auger            gravedigger_shovel + steel_drill                  pond
 *   antler_pike            tusk_spear + moose_antler                         pond
 *   astral_visor           tv_helmet + starlight_lens                        reactor
 *   phantasmal_chestplate  ectoplasm + holy_silver_chestplate                reactor
 *   wolfhide_carrier       werewolf_pelt + plate_carrier                     pond
 *   hexed_rod              hag_charm + fae_rod                               pond
 *   tuskaxe                boar_tusk + bloom_steel_axe                       pond
 *   moonblade              moonscale_koi + fae_silver_sword                  pond
 *   pufferblade            ember_puffer + magmasteel_sword                   pond (tentative)
 *   leviathan_blade        swordfish_sword + moonblade                       reactor  tier 2
 *   revenant_mail          phantasmal_chestplate + wolfhide_carrier          reactor  tier 2
 *   writ_of_absolution     extension_form + crucifix                         pond     (cancels a Hag's Curse, or
 *                                                                                      pushes the bank deadline back)
 *   gnome_lance            garden_gnome + tusk_spear                         pond     (hits harder the faster you move,
 *                                                                                      riding a moose or bathtub boat too)
 *   whiskerline_rod        giant_catfish + grapple_claw                      pond     (rod that hooks mobs and boats)
 *   feather_duster         shrimp_pistol + crow_feather                      pond     (silent, slightly homing darts)
 * All ids are tomyen: unless shown otherwise.
 */

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import com.jheinz.tomyen.MyMod;

/** Loads the fusion files (format above) on every data reload. */
public final class FusionRecipeLoader extends SimpleReloadListener<List<FusionRecipe>> {
	public static final Identifier ID = MyMod.id("fusion");
	private static final FileToIdConverter FILES = FileToIdConverter.json("fusion");

	@Override
	protected List<FusionRecipe> prepare(PreparableReloadListener.SharedState state) {
		List<FusionRecipe> recipes = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());
			try (Reader reader = entry.getValue().openAsReader()) {
				FusionRecipe parsed = parse(id, JsonParser.parseReader(reader).getAsJsonObject());
				if (parsed != null) {
					recipes.add(parsed);
				}
			} catch (Exception e) {
				MyMod.LOGGER.warn("Skipping fusion {}: {}", id, e.toString());
			}
		}
		return recipes;
	}

	@Override
	protected void apply(List<FusionRecipe> recipes, PreparableReloadListener.SharedState state) {
		FusionRecipe.set(recipes);
		MyMod.LOGGER.info("Loaded {} fusions", recipes.size());
	}

	static FusionRecipe parse(Identifier id, JsonObject json) {
		if (!json.has("inputs") || !json.get("inputs").isJsonArray() || json.getAsJsonArray("inputs").size() != 2 || !json.has("result")) {
			MyMod.LOGGER.warn("Skipping fusion {}: needs \"inputs\" (two entries) and \"result\"", id);
			return null;
		}
		JsonArray inputs = json.getAsJsonArray("inputs");
		FusionRecipe.Input first = input(id, inputs.get(0).getAsString());
		FusionRecipe.Input second = input(id, inputs.get(1).getAsString());
		if (first == null || second == null) {
			return null;
		}
		JsonElement resultJson = json.get("result");
		String resultId = resultJson.isJsonObject() ? resultJson.getAsJsonObject().get("id").getAsString() : resultJson.getAsString();
		int count = resultJson.isJsonObject() && resultJson.getAsJsonObject().has("count")
				? Mth.clamp(resultJson.getAsJsonObject().get("count").getAsInt(), 1, 64) : 1;
		Identifier resultKey = Identifier.tryParse(resultId);
		Optional<Item> result = resultKey == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(resultKey);
		if (result.isEmpty()) {
			MyMod.LOGGER.warn("Skipping fusion {}: unknown result {}", id, resultId);
			return null;
		}
		FusionRecipe.Station station = FusionRecipe.Station.POND;
		if (json.has("station")) {
			String name = json.get("station").getAsString().toUpperCase(Locale.ROOT);
			try {
				station = FusionRecipe.Station.valueOf(name);
			} catch (IllegalArgumentException e) {
				MyMod.LOGGER.warn("Fusion {}: unknown station {}, using pond", id, name.toLowerCase(Locale.ROOT));
			}
		}
		int tier = json.has("tier") ? Mth.clamp(json.get("tier").getAsInt(), 1, 2) : 1;
		String riddle = json.has("riddle") ? json.get("riddle").getAsString()
				: "fusion." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".riddle";
		return new FusionRecipe(id, first, second, result.get(), count, station, tier, riddle);
	}

	private static FusionRecipe.Input input(Identifier id, String text) {
		boolean tag = text.startsWith("#");
		Identifier key = Identifier.tryParse(tag ? text.substring(1) : text);
		if (key == null || !tag && BuiltInRegistries.ITEM.getOptional(key).isEmpty()) {
			MyMod.LOGGER.warn("Skipping fusion {}: unknown input {}", id, text);
			return null;
		}
		return new FusionRecipe.Input(tag, key);
	}
}
