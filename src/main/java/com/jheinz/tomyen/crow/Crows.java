package com.jheinz.tomyen.crow;

import java.util.Set;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.PartSlot;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.RodPartItem;

/**
 * The Crow Murder, a Scavenger-tier nuisance from nature's side of the invasion: flocks of glossy black crows that
 * turn up by day over plains, woods and (above all) farms and villages, raid ripe crops, eat seed off the ground and
 * steal anything shiny, dropped or (now and then) held, to hoard in a {@link CrowNestBlock} up a nearby tree. A
 * scarecrow keeps them off a farm (see {@link CropGuard}). See {@link Crow} for the bird and {@link CrowFlock} for how a
 * murder decides what to do.
 *
 * <p>They spawn in the ambient category (like bats, so they despawn when nobody's about), weight {@value #SPAWN_WEIGHT}, in
 * murders of 5 to 12, by day only, and three times as readily near a village or a farm (see
 * {@link Crow#checkCrowSpawnRules}).
 *
 * <p>Drops: Crow Feathers, which fletch six arrows instead of four and tie the {@link #CROW_FLY} lure for the Tackle
 * Bench, plus whatever the bird was carrying.
 */
public final class Crows {
	private static final int SPAWN_WEIGHT = 8;

	/** What crows steal: ingots, nuggets, gems, the mod's silver and coins. */
	public static final TagKey<net.minecraft.world.item.Item> SHINY = TagKey.create(Registries.ITEM, MyMod.id("shiny"));

	public static final EntityType<Crow> CROW = entity("crow", EntityType.Builder.of(Crow::new, MobCategory.AMBIENT)
			.sized(Crow.WIDTH, Crow.HEIGHT)
			.eyeHeight(0.38f)
			.clientTrackingRange(10));

	public static final Block CROW_NEST;
	public static final Item CROW_NEST_ITEM;

	static {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id("crow_nest"));
		CROW_NEST = Registry.register(BuiltInRegistries.BLOCK, key, new CrowNestBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BROWN).strength(0.6f).sound(SoundType.AZALEA_LEAVES).noOcclusion().ignitedByLava()
				.setId(key)));
		CROW_NEST_ITEM = item("crow_nest", properties -> new BlockItem(CROW_NEST, properties), new Item.Properties().useBlockDescriptionPrefix());
	}

	public static final BlockEntityType<CrowNestBlockEntity> CROW_NEST_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("crow_nest"), new BlockEntityType<>(CrowNestBlockEntity::new, Set.of(CROW_NEST)));

	/** Glossy black, with a blue-purple sheen. */
	public static final Item CROW_FEATHER = item("crow_feather", Item::new, new Item.Properties());
	/**
	 * A fishing fly tied from crow feathers on an iron hook: dark against the night sky, so it's best after dusk (a
	 * lure for a mod rod, fitted at the Tackle Bench).
	 */
	public static final Item CROW_FLY = item("crow_fly", properties -> new RodPartItem(properties, PartSlot.LURE,
			RodModifiers.builder().biteTicks(20).nightBiteTicks(80).nightRarity(0.08f).build()), new Item.Properties());
	public static final Item CROW_SPAWN_EGG = item("crow_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(CROW));

	// --- Sounds (see sounds.json: the caws are built from vanilla sounds by tools/gen_crow.py) ---------------
	public static final SoundEvent CAW = sound("crow_caw");
	/** Harsher and quicker: the whole murder bursting into the air. */
	public static final SoundEvent ALARM = sound("crow_alarm");
	public static final SoundEvent HURT = sound("crow_hurt");
	public static final SoundEvent DEATH = sound("crow_death");
	public static final SoundEvent FLAP = sound("crow_flap");
	public static final SoundEvent PECK = sound("crow_peck");
	public static final SoundEvent SNATCH = sound("crow_snatch");
	public static final SoundEvent NEST_RUSTLE = sound("crow_nest_rustle");

	private Crows() {
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
		FabricDefaultAttributeRegistry.register(CROW, Crow.createAttributes());
		SpawnPlacements.register(CROW, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Crow::checkCrowSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST,
				Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.SAVANNA, Biomes.TAIGA),
				MobCategory.AMBIENT, CROW, SPAWN_WEIGHT, 5, 12);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.COW_SPAWN_EGG, CROW_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.FEATHER, CROW_FEATHER));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.BARREL, CROW_NEST_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Fishing.FEATHER_LURE, CROW_FLY));
	}
}
