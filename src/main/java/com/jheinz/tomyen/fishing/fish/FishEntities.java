package com.jheinz.tomyen.fishing.fish;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import com.jheinz.tomyen.MyMod;

/**
 * The living fish: an entity type for each {@link FishKind} (all {@link ModFish}, the lava ones {@link LavaFish}), a
 * "Bucket of" item for each (a lava bucket for the lava fish), a spawn egg, their attributes, spawn rules and natural
 * spawns (water_ambient, like vanilla's fish: see FishKind for where), and their places in the creative tabs. Summon
 * one with {@code /summon tomyen:<fish>}. Models and textures are tools/gen_fish_entities.py's; drops are
 * data/tomyen/loot_table/entities/<fish>.json (vanilla cod's: the fish, cooked if it died burning, sometimes bone meal).
 */
public final class FishEntities {
	private FishEntities() {
	}

	private static <T extends ModFish> EntityType<T> entity(FishKind kind, EntityType.EntityFactory<T> factory) {
		EntityType.Builder<T> builder = EntityType.Builder.of(factory, MobCategory.WATER_AMBIENT)
				.sized(kind.width(), kind.height())
				.eyeHeight(kind.height() * 0.65f)
				.clientTrackingRange(4);
		if (kind.isLava()) {
			builder = builder.fireImmune();
		}
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(kind.id()));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static void register(FishKind kind) {
		EntityType<? extends ModFish> type = kind.isLava() ? entity(kind, LavaFish::new) : entity(kind, ModFish::new);
		Item bucket = item(kind.id() + "_bucket",
				properties -> new MobBucketItem(type, kind.isLava() ? Fluids.LAVA : Fluids.WATER,
						kind.isLava() ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY_FISH, properties),
				new Item.Properties().stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY));
		Item egg = item(kind.id() + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
		kind.bind(type, bucket, egg);

		FabricDefaultAttributeRegistry.register(type, AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, kind.health()));
		registerSpawn(kind, type);
		BiomeModifications.addSpawn(kind.biomes(), MobCategory.WATER_AMBIENT, type, kind.weight(), kind.minGroup(), kind.maxGroup());
	}

	private static <T extends ModFish> void registerSpawn(FishKind kind, EntityType<T> type) {
		SpawnPlacements.register(type, kind.isLava() ? SpawnPlacementTypes.IN_LAVA : SpawnPlacementTypes.IN_WATER,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (t, level, reason, pos, random) -> kind.canSpawnAt(level, pos));
	}

	public static void initialize() {
		FishKind.ALL.forEach(FishEntities::register);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.TADPOLE_BUCKET, FishKind.ALL.stream().map(FishKind::bucket).toArray(Item[]::new)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.TROPICAL_FISH_SPAWN_EGG,
						FishKind.ALL.stream().map(FishKind::spawnEgg).toArray(Item[]::new)));
	}
}
