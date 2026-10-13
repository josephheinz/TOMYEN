package com.jheinz.mymod.meteorite;

import java.util.function.Function;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.mymod.MyMod;

/**
 * Meteorites: the crash sites scattered over the Overworld, the meteors that still fall on clear nights, and what
 * comes out of them.
 * <p>
 * A meteorite's core is built like a geode: a crust of Meteorite Stone, a layer of Meteoric Iron Ore, and a hollow
 * lined with Budding Star Crystal, which grows Star Crystal buds into clusters the way budding amethyst does. The
 * crust and ore are too hard for pickaxes, which chip through for nothing: Meteorite Stone takes a drill (a Hand Drill
 * will do), Meteoric Iron Ore a running motor drill (see {@link com.jheinz.mymod.drill.DrillItem}). Raw
 * meteoric iron only smelts in a Foundry at {@link MeteoriteRecipes#METEORIC_IRON_TEMPERATURE}; alloyed with star
 * crystal it makes Star Steel ({@link StarSteel}), the best tools and armor there are by night. Star crystal shards
 * also make the Star Lantern and the Starlight Lens.
 */
public final class Meteorites {
	// --- The meteorite ------------------------------------------------------------------------------------------
	public static final Block METEORITE_STONE = block("meteorite_stone", Block::new, stone().strength(3.0f, 9.0f));
	public static final Block METEORIC_IRON_ORE = block("meteoric_iron_ore", properties -> new DropExperienceBlock(UniformInt.of(2, 5), properties),
			stone().strength(4.5f, 9.0f));
	public static final Block STAR_CRYSTAL_BLOCK = block("star_crystal_block", AmethystBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5f).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
					.lightLevel(state -> 3));
	public static final Block BUDDING_STAR_CRYSTAL = block("budding_star_crystal", BuddingStarCrystalBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).randomTicks().strength(1.5f).sound(SoundType.AMETHYST)
					.requiresCorrectToolForDrops().pushReaction(PushReaction.POPPED).lightLevel(state -> 4));
	// Brighter than amethyst: star crystal glows on its own.
	public static final Block STAR_CRYSTAL_CLUSTER = block("star_crystal_cluster", properties -> new AmethystClusterBlock(7.0f, 10.0f, properties),
			crystal(SoundType.AMETHYST_CLUSTER, 9));
	public static final Block LARGE_STAR_CRYSTAL_BUD = block("large_star_crystal_bud", properties -> new AmethystClusterBlock(5.0f, 10.0f, properties),
			crystal(SoundType.LARGE_AMETHYST_BUD, 6));
	public static final Block MEDIUM_STAR_CRYSTAL_BUD = block("medium_star_crystal_bud", properties -> new AmethystClusterBlock(4.0f, 10.0f, properties),
			crystal(SoundType.MEDIUM_AMETHYST_BUD, 4));
	public static final Block SMALL_STAR_CRYSTAL_BUD = block("small_star_crystal_bud", properties -> new AmethystClusterBlock(3.0f, 8.0f, properties),
			crystal(SoundType.SMALL_AMETHYST_BUD, 2));

	// --- Meteorite stone building blocks ------------------------------------------------------------------------
	public static final Block METEORITE_STONE_STAIRS = block("meteorite_stone_stairs",
			properties -> new StairBlock(METEORITE_STONE.defaultBlockState(), properties), BlockBehaviour.Properties.ofLegacyCopy(METEORITE_STONE));
	public static final Block METEORITE_STONE_SLAB = block("meteorite_stone_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(METEORITE_STONE));
	public static final Block METEORITE_STONE_WALL = block("meteorite_stone_wall", WallBlock::new,
			BlockBehaviour.Properties.ofLegacyCopy(METEORITE_STONE).forceSolidOn());
	public static final Block POLISHED_METEORITE_STONE = block("polished_meteorite_stone", Block::new, BlockBehaviour.Properties.ofLegacyCopy(METEORITE_STONE));
	public static final Block POLISHED_METEORITE_STONE_STAIRS = block("polished_meteorite_stone_stairs",
			properties -> new StairBlock(POLISHED_METEORITE_STONE.defaultBlockState(), properties),
			BlockBehaviour.Properties.ofLegacyCopy(POLISHED_METEORITE_STONE));
	public static final Block POLISHED_METEORITE_STONE_SLAB = block("polished_meteorite_stone_slab", SlabBlock::new,
			BlockBehaviour.Properties.ofLegacyCopy(POLISHED_METEORITE_STONE));
	public static final Block METEORITE_BRICKS = block("meteorite_bricks", Block::new, BlockBehaviour.Properties.ofLegacyCopy(METEORITE_STONE));
	public static final Block METEORITE_BRICK_STAIRS = block("meteorite_brick_stairs",
			properties -> new StairBlock(METEORITE_BRICKS.defaultBlockState(), properties), BlockBehaviour.Properties.ofLegacyCopy(METEORITE_BRICKS));
	public static final Block METEORITE_BRICK_SLAB = block("meteorite_brick_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(METEORITE_BRICKS));
	public static final Block METEORITE_BRICK_WALL = block("meteorite_brick_wall", WallBlock::new,
			BlockBehaviour.Properties.ofLegacyCopy(METEORITE_BRICKS).forceSolidOn());

	// --- Made from it -------------------------------------------------------------------------------------------
	public static final Block STAR_STEEL_BLOCK = block("star_steel_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops().strength(30.0f, 1200.0f)
					.sound(SoundType.NETHERITE_BLOCK));
	/** As bright as a block of light gets. */
	public static final Block STAR_LANTERN = block("star_lantern", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.3f).sound(SoundType.AMETHYST).lightLevel(state -> 15));

	public static final Item RAW_METEORIC_IRON = item("raw_meteoric_iron", new Item.Properties());
	public static final Item METEORIC_IRON_INGOT = item("meteoric_iron_ingot", new Item.Properties());
	public static final Item STAR_CRYSTAL_SHARD = item("star_crystal_shard", new Item.Properties());
	public static final Item STAR_STEEL_INGOT = item("star_steel_ingot", new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON));
	public static final Item STARLIGHT_LENS = register("starlight_lens", StarlightLensItem::new, new Item.Properties().stacksTo(1));

	public static final Item STAR_STEEL_SWORD = item("star_steel_sword", starSteel().sword(StarSteel.TOOL_MATERIAL, 3.0f, -2.4f));
	public static final Item STAR_STEEL_SHOVEL = item("star_steel_shovel", starSteel().shovel(StarSteel.TOOL_MATERIAL, 1.5f, -3.0f));
	public static final Item STAR_STEEL_PICKAXE = item("star_steel_pickaxe", starSteel().pickaxe(StarSteel.TOOL_MATERIAL, 1.0f, -2.8f));
	public static final Item STAR_STEEL_AXE = item("star_steel_axe", starSteel().axe(StarSteel.TOOL_MATERIAL, 5.0f, -3.0f));
	public static final Item STAR_STEEL_HOE = item("star_steel_hoe", starSteel().hoe(StarSteel.TOOL_MATERIAL, -4.0f, 0.0f));
	public static final Item STAR_STEEL_HELMET = item("star_steel_helmet", starSteel().humanoidArmor(StarSteel.ARMOR_MATERIAL, ArmorType.HELMET));
	public static final Item STAR_STEEL_CHESTPLATE = item("star_steel_chestplate",
			starSteel().humanoidArmor(StarSteel.ARMOR_MATERIAL, ArmorType.CHESTPLATE));
	public static final Item STAR_STEEL_LEGGINGS = item("star_steel_leggings", starSteel().humanoidArmor(StarSteel.ARMOR_MATERIAL, ArmorType.LEGGINGS));
	public static final Item STAR_STEEL_BOOTS = item("star_steel_boots", starSteel().humanoidArmor(StarSteel.ARMOR_MATERIAL, ArmorType.BOOTS));

	// --- Falling meteors ----------------------------------------------------------------------------------------
	public static final EntityType<Meteor> METEOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, key(Registries.ENTITY_TYPE, "meteor"),
			EntityType.Builder.<Meteor>of(Meteor::new, MobCategory.MISC)
					.noLootTable()
					.sized(1.0f, 1.0f)
					.fireImmune()
					// Seen streaking across the sky from far off.
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(key(Registries.ENTITY_TYPE, "meteor")));
	public static final SoundEvent METEOR_WHISTLE = sound("meteor_whistle");
	public static final SoundEvent METEOR_IMPACT = sound("meteor_impact");

	// --- Worldgen -----------------------------------------------------------------------------------------------
	public static final MapCodec<MeteoriteCrashSiteFeature> CRASH_SITE = Registry.register(BuiltInRegistries.FEATURE_TYPE,
			MyMod.id("meteorite_crash_site"), MeteoriteCrashSiteFeature.CODEC);
	private static final ResourceKey<PlacedFeature> CRASH_SITE_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id("meteorite_crash_site"));

	private Meteorites() {
	}

	private static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
				.sound(SoundType.BASALT);
	}

	private static BlockBehaviour.Properties crystal(SoundType sound, int light) {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).forceSolidOn().noOcclusion().sound(sound).strength(1.5f)
				.lightLevel(state -> light).pushReaction(PushReaction.POPPED);
	}

	private static Item.Properties starSteel() {
		return new Item.Properties().fireResistant();
	}

	private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String name) {
		return ResourceKey.create(registry, MyMod.id(name));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = key(Registries.BLOCK, name);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = key(Registries.ITEM, name);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = key(Registries.ITEM, name);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		// Crash sites: one try in every 32 chunks (the placed feature's rarity filter), on land only. The feature
		// itself turns down water, ice and steep ground (about half the tries), so it's one per 50 to 70 land chunks.
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
						.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
						.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
				GenerationStep.Decoration.TOP_LAYER_MODIFICATION, CRASH_SITE_PLACED);
		MeteorShowers.initialize();
		StarSteel.initialize();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.DEEPSLATE_IRON_ORE, METEORIC_IRON_ORE);
			output.insertAfter(Items.AMETHYST_CLUSTER, METEORITE_STONE, STAR_CRYSTAL_BLOCK, BUDDING_STAR_CRYSTAL, SMALL_STAR_CRYSTAL_BUD,
					MEDIUM_STAR_CRYSTAL_BUD, LARGE_STAR_CRYSTAL_BUD, STAR_CRYSTAL_CLUSTER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			output.insertAfter(Items.NETHERITE_BLOCK, STAR_STEEL_BLOCK);
			output.insertAfter(Items.POLISHED_BLACKSTONE_BRICK_WALL, METEORITE_STONE, METEORITE_STONE_STAIRS, METEORITE_STONE_SLAB,
					METEORITE_STONE_WALL, POLISHED_METEORITE_STONE, POLISHED_METEORITE_STONE_STAIRS, POLISHED_METEORITE_STONE_SLAB, METEORITE_BRICKS,
					METEORITE_BRICK_STAIRS, METEORITE_BRICK_SLAB, METEORITE_BRICK_WALL);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.SEA_LANTERN, STAR_LANTERN));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.NETHERITE_INGOT, RAW_METEORIC_IRON, METEORIC_IRON_INGOT, STAR_STEEL_INGOT,
						STAR_CRYSTAL_SHARD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.NETHERITE_HOE, STAR_STEEL_SHOVEL, STAR_STEEL_PICKAXE, STAR_STEEL_AXE, STAR_STEEL_HOE);
			output.insertAfter(Items.SPYGLASS, STARLIGHT_LENS);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_SWORD, STAR_STEEL_SWORD);
			output.insertAfter(Items.NETHERITE_AXE, STAR_STEEL_AXE);
			output.insertAfter(Items.NETHERITE_BOOTS, STAR_STEEL_HELMET, STAR_STEEL_CHESTPLATE, STAR_STEEL_LEGGINGS, STAR_STEEL_BOOTS);
		});
	}
}
