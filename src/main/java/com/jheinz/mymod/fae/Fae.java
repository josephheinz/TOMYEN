package com.jheinz.mymod.fae;

import java.util.function.Function;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.mymod.MyMod;

/**
 * The fae: Fae Rings in the Overworld's woods and meadows, the Feywild beyond them (see {@link Feywild}), the fairies
 * that live there, and what's made from it all.
 *
 * <p>A Fae Ring is a circle of mushrooms (red, brown and glowing Fae Mushrooms) round a patch of brighter grass. At
 * night, standing in the middle of one for five seconds carries you to the Feywild, and the ring you arrive in carries
 * you back (see {@link FaeRings}); a Fae Ring Charm does the same anywhere. Fairies flutter about the Feywild, and
 * round Overworld rings at night, stealing what they can (see {@link Fairy}); a glass bottle catches one.
 *
 * <p>The materials: Glimmerwood (luminous trees of the Feywild), the Fae Bloom (crafts into Fae Dust, as fairies drop
 * it), and Fae Silver, mined in the Feywild, for light, quick tools and the charm. Fae Dust also brews Slow Falling
 * and makes glow ink; the Fae Mushroom brews Night Vision.
 */
public final class Fae {
	/** The glitter fairies and fae light leave in the air; the client tints it violet, teal or pink. */
	public static final SimpleParticleType SPARKLE =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("fae_sparkle"), FabricParticleTypes.simple(false));

	// --- Sounds (vanilla ones, re-pitched: see sounds.json) ----------------------------------------------------
	public static final SoundEvent FAIRY_AMBIENT = sound("fairy_ambient");
	public static final SoundEvent FAIRY_GIGGLE = sound("fairy_giggle");
	public static final SoundEvent FAIRY_HURT = sound("fairy_hurt");
	public static final SoundEvent FAIRY_DEATH = sound("fairy_death");
	public static final SoundEvent FAIRY_BOTTLED = sound("fairy_bottled");
	public static final SoundEvent RING_CHIME = sound("fae_ring_chime");
	public static final SoundEvent RING_TRAVEL = sound("fae_ring_travel");
	public static final SoundEvent FEYWILD_LOOP = sound("feywild_loop");
	public static final SoundEvent FEYWILD_ADDITIONS = sound("feywild_additions");
	public static final SoundEvent FEYWILD_MUSIC = sound("music.feywild");

	// --- Worldgen keys -----------------------------------------------------------------------------------------
	public static final MapCodec<FaeRingFeature> FAE_RING_FEATURE = Registry.register(BuiltInRegistries.FEATURE_TYPE, MyMod.id("fae_ring"),
			FaeRingFeature.CODEC);
	private static final ResourceKey<PlacedFeature> FAE_RING_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id("fae_ring"));
	public static final ResourceKey<Feature> GLIMMERWOOD_TREE = ResourceKey.create(Registries.FEATURE, MyMod.id("glimmerwood_tree"));
	public static final ResourceKey<Feature> FANCY_GLIMMERWOOD_TREE = ResourceKey.create(Registries.FEATURE, MyMod.id("fancy_glimmerwood_tree"));
	private static final TreeGrower GLIMMERWOOD_GROWER = new TreeGrower("glimmerwood", WeightedList.of(GLIMMERWOOD_TREE),
			WeightedList.of(), WeightedList.of(FANCY_GLIMMERWOOD_TREE), GLIMMERWOOD_TREE);

	/** Mushrooms that count toward a ring. */
	public static final TagKey<Block> RING_MUSHROOMS = TagKey.create(Registries.BLOCK, MyMod.id("fae_ring_mushrooms"));

	// --- Blocks ------------------------------------------------------------------------------------------------
	/** Glows; at night it now and then calls up a fairy (see FaeMushroomBlock). */
	public static final Block FAE_MUSHROOM = block("fae_mushroom", FaeMushroomBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).lightLevel(state -> 7)
			.pushReaction(PushReaction.POPPED));
	public static final Block FAE_BLOOM = block("fae_bloom", properties -> new FlowerBlock(MobEffects.NIGHT_VISION, 5.0f, properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollision().instabreak().sound(SoundType.GRASS)
					.offsetType(BlockBehaviour.OffsetType.XZ).lightLevel(state -> 5).pushReaction(PushReaction.POPPED));
	/** The brighter grass inside a ring: a grass block that doesn't spread (no random ticks), tinted lighter. */
	public static final Block FAE_GRASS_BLOCK = block("fae_grass_block", GrassBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6f).sound(SoundType.GRASS));
	public static final Block GLIMMERWOOD_LOG = block("glimmerwood_log", RotatedPillarBlock::new,
			Blocks.logProperties(MapColor.COLOR_LIGHT_BLUE, MapColor.COLOR_PURPLE, SoundType.WOOD));
	public static final Block GLIMMERWOOD_PLANKS = block("glimmerwood_planks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASS).strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava());
	/** Luminous: they light the Feywild's woods and shed sparkles rather than leaves. */
	public static final Block GLIMMERWOOD_LEAVES = block("glimmerwood_leaves",
			properties -> new UntintedParticleLeavesBlock(0.015f, SPARKLE, AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties),
			Blocks.leavesProperties(SoundType.GRASS).mapColor(MapColor.COLOR_CYAN).lightLevel(state -> 7));
	public static final Block GLIMMERWOOD_SAPLING = block("glimmerwood_sapling", properties -> new SaplingBlock(GLIMMERWOOD_GROWER, properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS)
					.lightLevel(state -> 3).pushReaction(PushReaction.POPPED));
	public static final Block GLIMMERWOOD_STAIRS = block("glimmerwood_stairs",
			properties -> new StairBlock(GLIMMERWOOD_PLANKS.defaultBlockState(), properties), BlockBehaviour.Properties.ofFullCopy(GLIMMERWOOD_PLANKS));
	public static final Block GLIMMERWOOD_SLAB = block("glimmerwood_slab", SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(GLIMMERWOOD_PLANKS));
	public static final Block GLIMMERWOOD_DOOR = block("glimmerwood_door", properties -> new DoorBlock(BlockSetType.OAK, properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASS).strength(3.0f).noOcclusion()
					.ignitedByLava().pushReaction(PushReaction.POPPED));
	public static final Block FAE_SILVER_ORE = block("fae_silver_ore", properties -> new DropExperienceBlock(UniformInt.of(1, 3), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
					.strength(3.0f, 3.0f));
	/** A caught fairy, set down (or hung) like a lantern. Its item is the Fairy in a Bottle. */
	public static final Block FAIRY_IN_A_BOTTLE = blockOnly("fairy_in_a_bottle", FairyInABottleBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().strength(0.5f).sound(SoundType.GLASS).lightLevel(state -> 15).noOcclusion()
			.pushReaction(PushReaction.POPPED));

	// --- Fairies -----------------------------------------------------------------------------------------------
	public static final EntityType<Fairy> FAIRY = entity("fairy", EntityType.Builder.of(Fairy::new, MobCategory.AMBIENT)
			.sized(0.35f, 0.6f)
			.eyeHeight(0.45f)
			.clientTrackingRange(8));

	// --- Items -------------------------------------------------------------------------------------------------
	/** Light and quick: Fae Silver tools swing and dig faster than iron but wear out a little sooner. */
	public static final ToolMaterial FAE_SILVER = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 200, 8.0f, 2.0f, 20,
			TagKey.create(Registries.ITEM, MyMod.id("fae_silver_tool_materials")));
	public static final Item FAE_DUST = item("fae_dust", Item::new, new Item.Properties());
	public static final Item RAW_FAE_SILVER = item("raw_fae_silver", Item::new, new Item.Properties());
	public static final Item FAE_SILVER_INGOT = item("fae_silver_ingot", Item::new, new Item.Properties());
	public static final Item FAE_SILVER_SWORD = item("fae_silver_sword", Item::new, new Item.Properties().sword(FAE_SILVER, 3.0f, -2.0f));
	public static final Item FAE_SILVER_PICKAXE = item("fae_silver_pickaxe", Item::new, new Item.Properties().pickaxe(FAE_SILVER, 1.0f, -2.4f));
	public static final Item FAE_SILVER_AXE = item("fae_silver_axe", Item::new, new Item.Properties().axe(FAE_SILVER, 5.5f, -2.8f));
	public static final Item FAE_SILVER_SHOVEL = item("fae_silver_shovel", Item::new, new Item.Properties().shovel(FAE_SILVER, 1.5f, -2.6f));
	public static final Item FAE_SILVER_HOE = item("fae_silver_hoe", Item::new, new Item.Properties().hoe(FAE_SILVER, -2.0f, -0.5f));
	public static final Item FAE_RING_CHARM = item("fae_ring_charm", FaeRingCharmItem::new,
			new Item.Properties().durability(16).rarity(Rarity.UNCOMMON));
	public static final Item FAIRY_IN_A_BOTTLE_ITEM = item("fairy_in_a_bottle", properties -> new FairyInABottleItem(FAIRY_IN_A_BOTTLE, properties),
			new Item.Properties().stacksTo(16).useBlockDescriptionPrefix());
	public static final Item FAIRY_SPAWN_EGG = item("fairy_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(FAIRY));

	/** On a player: where they stepped into the Feywild from, so the return ring can send them back there. */
	public static final AttachmentType<GlobalPos> RETURN_POINT = AttachmentRegistry.<GlobalPos>builder()
			.persistent(GlobalPos.CODEC)
			.copyOnDeath()
			.buildAndRegister(MyMod.id("fae_return_point"));

	private Fae() {
	}

	private static Block blockOnly(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = blockOnly(name, factory, properties);
		item(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		// Rare: about one ring every few hundred chunks of these biomes.
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST, Biomes.MEADOW),
				GenerationStep.Decoration.VEGETAL_DECORATION, FAE_RING_PLACED);
		FabricDefaultAttributeRegistry.register(FAIRY, Fairy.createAttributes());
		// Natural spawns are the Feywild's own (its biomes list fairies); in the Overworld, Fae Mushrooms call them up.
		SpawnPlacements.register(FAIRY, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Fairy::checkFairySpawnRules);
		FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
		flammable.add(GLIMMERWOOD_LOG, 5, 5);
		flammable.add(GLIMMERWOOD_PLANKS, 5, 20);
		flammable.add(GLIMMERWOOD_STAIRS, 5, 20);
		flammable.add(GLIMMERWOOD_SLAB, 5, 20);
		flammable.add(GLIMMERWOOD_LEAVES, 30, 60);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.CHERRY_LOG, GLIMMERWOOD_LOG);
			output.insertAfter(Items.CHERRY_LEAVES, GLIMMERWOOD_LEAVES);
			output.insertAfter(Items.CHERRY_SAPLING, GLIMMERWOOD_SAPLING);
			output.insertAfter(Items.RED_MUSHROOM, FAE_MUSHROOM);
			output.insertAfter(Items.ALLIUM, FAE_BLOOM);
			output.insertAfter(Items.GRASS_BLOCK, FAE_GRASS_BLOCK);
			output.insertAfter(Items.DEEPSLATE_IRON_ORE, FAE_SILVER_ORE);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Items.CHERRY_BUTTON, GLIMMERWOOD_LOG, GLIMMERWOOD_PLANKS, GLIMMERWOOD_STAIRS,
						GLIMMERWOOD_SLAB, GLIMMERWOOD_DOOR));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.IRON_INGOT, RAW_FAE_SILVER, FAE_SILVER_INGOT, FAE_DUST));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.IRON_HOE, FAE_SILVER_SHOVEL, FAE_SILVER_PICKAXE, FAE_SILVER_AXE, FAE_SILVER_HOE,
						FAE_RING_CHARM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.IRON_SWORD, FAE_SILVER_SWORD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.SOUL_LANTERN, FAIRY_IN_A_BOTTLE_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.ALLAY_SPAWN_EGG, FAIRY_SPAWN_EGG));

		FaeRings.initialize();
		Feywild.initialize();
	}
}
