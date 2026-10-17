package com.jheinz.tomyen.moose;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.PartSlot;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.RodPartItem;

/**
 * The Moose, a big neutral animal of the Scavenger tier: small herds in the taigas, the snowy plains where there are
 * trees, and the swamps. Peaceable until it's hit or someone crowds a calf; then a bull lowers its antlers and charges,
 * and a cow rears and stomps (see {@link Moose}). Fed, it calms down enough to take a saddle and a rider: slow, but sure
 * on snow and in water.
 *
 * <p>Its drops plug into the systems already here: leather (more than a cow), venison, and from a bull's rack (or the
 * ground under one, after he sheds), Moose Antlers. An antler saws into {@link #ANTLER_HANDLE}s, which take the place of
 * sticks in the stone, copper, iron and bloom steel tool recipes for a tool half again as durable; or carves into
 * {@link #ANTLER_HOOK}s for a fishing rod.
 */
public final class Mooses {
	private static final int TAIGA_WEIGHT = 7;
	private static final int SWAMP_WEIGHT = 5;
	private static final int SNOWY_PLAINS_WEIGHT = 3;

	/** Calms a wild moose, breeds a calm one, grows a calf: fruit, berries and leaves. */
	public static final TagKey<Item> FOOD = TagKey.create(Registries.ITEM, MyMod.id("moose_food"));

	public static final EntityType<Moose> MOOSE = entity("moose", EntityType.Builder.of(Moose::new, MobCategory.CREATURE)
			.sized(1.5f, 2.2f)
			.eyeHeight(1.95f)
			.passengerAttachments(new Vec3(0.0, 2.0, -0.12))
			.clientTrackingRange(10));

	/** The extra roll a bull with his full rack gets when he dies: one antler, sometimes both. */
	public static final ResourceKey<LootTable> ANTLER_LOOT = ResourceKey.create(Registries.LOOT_TABLE, MyMod.id("entities/moose_antlers"));

	public static final Item MOOSE_ANTLER = item("moose_antler", Item::new, new Item.Properties());
	/** Takes the place of sticks in tool recipes: the tool comes out half again as durable (see the recipes). */
	public static final Item ANTLER_HANDLE = item("antler_handle", Item::new, new Item.Properties());
	/** A heavy carved hook: fish throw it less, but it's slow to set. */
	public static final Item ANTLER_HOOK = item("antler_hook",
			properties -> new RodPartItem(properties, PartSlot.HOOK, RodModifiers.builder().escape(0.25f).biteTicks(-10).build()),
			new Item.Properties());
	public static final Item RAW_VENISON = item("raw_venison", Item::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build()));
	public static final Item COOKED_VENISON = item("cooked_venison", Item::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.9f).build()));
	public static final Item MOOSE_SPAWN_EGG = item("moose_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(MOOSE));

	// --- Sounds (vanilla ones, re-pitched: see sounds.json) ----------------------------------------------------
	public static final SoundEvent AMBIENT = sound("moose_ambient");
	public static final SoundEvent BELLOW = sound("moose_bellow");
	public static final SoundEvent HURT = sound("moose_hurt");
	public static final SoundEvent DEATH = sound("moose_death");
	public static final SoundEvent STEP = sound("moose_step");
	public static final SoundEvent SNORT = sound("moose_snort");
	public static final SoundEvent CHARGE_HIT = sound("moose_charge_hit");
	public static final SoundEvent STOMP = sound("moose_stomp");
	public static final SoundEvent EAT = sound("moose_eat");
	public static final SoundEvent SHED = sound("moose_shed");

	private Mooses() {
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(MOOSE, Moose.createAttributes());
		SpawnPlacements.register(MOOSE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Moose::checkMooseSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
				Biomes.OLD_GROWTH_SPRUCE_TAIGA), MobCategory.CREATURE, MOOSE, TAIGA_WEIGHT, 1, 3);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP), MobCategory.CREATURE, MOOSE, SWAMP_WEIGHT, 1, 3);
		// Out on the snowy plains only near trees (see Moose.checkMooseSpawnRules).
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SNOWY_PLAINS), MobCategory.CREATURE, MOOSE, SNOWY_PLAINS_WEIGHT, 1, 2);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.MAGMA_CUBE_SPAWN_EGG, MOOSE_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.insertAfter(Items.COOKED_MUTTON, RAW_VENISON, COOKED_VENISON));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.LEATHER, MOOSE_ANTLER, ANTLER_HANDLE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.FISHING_ROD, ANTLER_HOOK));
	}
}
