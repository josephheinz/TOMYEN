package com.jheinz.mymod.fishing;

/*
 * Fishing species data files: data/<namespace>/fishing_species/<name>.json, one per catchable thing. Only fish bitten
 * on a mod rod (RodItem) roll from these; the vanilla rod keeps vanilla's loot table.
 *
 * {
 *   "item": "mymod:glowfin_tetra",        required: the item caught
 *   "weight": 10,                         required: relative chance among the species eligible at the hook (> 0)
 *   "difficulty": 3,                      required: 1 (easy) .. 5 (brutal); minigame speed and zone size
 *   "behavior": "darting",                steady | darting | sinker | floater | thrasher (default steady);
 *                                         steady/thrasher = tension meter, darting = timing strikes, sinker/floater = mash
 *   "min_rod_tier": 0,                    0..4 (default 0): bamboo 0, iron 1, steel 2, duralumin/magmasteel 3, star steel 4
 *   "fluid": "water",                     water | lava (default water); only the Magmasteel Rod fishes lava
 *   "dimension": "minecraft:overworld",   optional: a dimension id
 *   "biomes": "#minecraft:is_ocean",      optional: a biome tag "#ns:tag", a biome id, or a list of either (any matches)
 *   "time": "any",                        any | day | night (default any)
 *   "weather": "any",                     any | clear | rain | thunder (default any)
 *   "min_y": -64, "max_y": 320,           optional: the hook's block y range
 *   "count": [1, 1],                      optional: how many are caught, [min, max] or a single number (default 1)
 *   "size": {                             optional: what it weighs, in lb (see FishSize); without it, never weighed
 *     "min_lb": 1, "avg_lb": 8,           the smallest catchable, and the typical catch (the roll's median)
 *     "max_lb": 55,                       the record: the end of the long, thin upper tail
 *     "trophy_lb": 19                     optional: trophies from here up (default: the roll's 85th percentile)
 *   }
 * }
 *
 * Unknown keys are ignored. A file whose item doesn't resolve, or that fails to parse, is logged and skipped. Lower
 * weight means rarer: rarity bias from rod parts and luck (see FishingSpecies#biasedWeight) favours low weights.
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

import com.jheinz.mymod.MyMod;

/** Loads the fishing species files (format above) on every data reload. */
public final class FishingSpeciesLoader extends SimpleReloadListener<List<FishingSpecies>> {
	public static final Identifier ID = MyMod.id("fishing_species");
	private static final FileToIdConverter FILES = FileToIdConverter.json("fishing_species");

	@Override
	protected List<FishingSpecies> prepare(PreparableReloadListener.SharedState state) {
		List<FishingSpecies> species = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(state.resourceManager()).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				FishingSpecies parsed = parse(id, json.getAsJsonObject());
				if (parsed != null) {
					species.add(parsed);
				}
			} catch (Exception e) {
				MyMod.LOGGER.warn("Skipping fishing species {}: {}", id, e.toString());
			}
		}
		return species;
	}

	@Override
	protected void apply(List<FishingSpecies> species, PreparableReloadListener.SharedState state) {
		FishingSpecies.set(species);
		MyMod.LOGGER.info("Loaded {} fishing species", species.size());
	}

	/** The species in {@code json}, or null (logged) if it can't be used. */
	static FishingSpecies parse(Identifier id, JsonObject json) {
		if (!json.has("item") || !json.has("weight") || !json.has("difficulty")) {
			MyMod.LOGGER.warn("Skipping fishing species {}: \"item\", \"weight\" and \"difficulty\" are required", id);
			return null;
		}
		Identifier itemId = Identifier.tryParse(json.get("item").getAsString());
		Optional<Item> item = itemId == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(itemId);
		if (item.isEmpty()) {
			MyMod.LOGGER.warn("Skipping fishing species {}: unknown item {}", id, json.get("item").getAsString());
			return null;
		}
		int weight = json.get("weight").getAsInt();
		if (weight <= 0) {
			MyMod.LOGGER.warn("Skipping fishing species {}: weight must be above 0", id);
			return null;
		}
		int difficulty = Mth.clamp(json.get("difficulty").getAsInt(), 1, 5);

		FishBehavior behavior = FishBehavior.STEADY;
		if (json.has("behavior")) {
			behavior = FishBehavior.byId(lower(json, "behavior"));
			if (behavior == null) {
				MyMod.LOGGER.warn("Fishing species {}: unknown behavior {}, using steady", id, json.get("behavior").getAsString());
				behavior = FishBehavior.STEADY;
			}
		}
		int minRodTier = json.has("min_rod_tier") ? Mth.clamp(json.get("min_rod_tier").getAsInt(), 0, 4) : 0;
		boolean lava = json.has("fluid") && lower(json, "fluid").equals("lava");
		Identifier dimension = json.has("dimension") ? Identifier.tryParse(json.get("dimension").getAsString()) : null;

		List<FishingSpecies.BiomeMatch> biomes = new ArrayList<>();
		if (json.has("biomes")) {
			JsonElement element = json.get("biomes");
			JsonArray list = element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
			if (!element.isJsonArray()) {
				list.add(element);
			}
			for (JsonElement entry : list) {
				String text = entry.getAsString();
				boolean tag = text.startsWith("#");
				Identifier biome = Identifier.tryParse(tag ? text.substring(1) : text);
				if (biome == null) {
					MyMod.LOGGER.warn("Fishing species {}: bad biome {}, ignored", id, text);
				} else {
					biomes.add(new FishingSpecies.BiomeMatch(tag, biome));
				}
			}
		}

		FishingSpecies.Time time = json.has("time") ? enumValue(FishingSpecies.Time.class, lower(json, "time"), FishingSpecies.Time.ANY, id)
				: FishingSpecies.Time.ANY;
		FishingSpecies.Weather weather = json.has("weather")
				? enumValue(FishingSpecies.Weather.class, lower(json, "weather"), FishingSpecies.Weather.ANY, id) : FishingSpecies.Weather.ANY;
		int minY = json.has("min_y") ? json.get("min_y").getAsInt() : Integer.MIN_VALUE;
		int maxY = json.has("max_y") ? json.get("max_y").getAsInt() : Integer.MAX_VALUE;

		int minCount = 1;
		int maxCount = 1;
		if (json.has("count")) {
			JsonElement count = json.get("count");
			if (count.isJsonArray() && count.getAsJsonArray().size() >= 2) {
				minCount = count.getAsJsonArray().get(0).getAsInt();
				maxCount = count.getAsJsonArray().get(1).getAsInt();
			} else if (count.isJsonPrimitive()) {
				minCount = maxCount = count.getAsInt();
			}
			minCount = Math.max(1, minCount);
			maxCount = Math.max(minCount, maxCount);
		}
		FishSize size = null;
		if (json.has("size")) {
			JsonObject sizeJson = json.getAsJsonObject("size");
			float min = sizeJson.has("min_lb") ? sizeJson.get("min_lb").getAsFloat() : -1.0f;
			float avg = sizeJson.has("avg_lb") ? sizeJson.get("avg_lb").getAsFloat() : -1.0f;
			float max = sizeJson.has("max_lb") ? sizeJson.get("max_lb").getAsFloat() : -1.0f;
			float trophy = sizeJson.has("trophy_lb") ? sizeJson.get("trophy_lb").getAsFloat() : 0.0f;
			if (min > 0.0f && min <= avg && avg < max) {
				size = FishSize.of(min, avg, max, trophy);
			} else {
				MyMod.LOGGER.warn("Fishing species {}: size needs 0 < min_lb <= avg_lb < max_lb, ignored", id);
			}
		}
		return new FishingSpecies(id, item.get(), weight, difficulty, behavior, minRodTier, lava, dimension, List.copyOf(biomes), time,
				weather, minY, maxY, minCount, maxCount, size);
	}

	private static String lower(JsonObject json, String key) {
		return json.get(key).getAsString().toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback, Identifier id) {
		for (E value : type.getEnumConstants()) {
			if (value.name().equalsIgnoreCase(name)) {
				return value;
			}
		}
		MyMod.LOGGER.warn("Fishing species {}: unknown {} {}, using {}", id, type.getSimpleName().toLowerCase(Locale.ROOT), name,
				fallback.name().toLowerCase(Locale.ROOT));
		return fallback;
	}
}
