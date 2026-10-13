package com.jheinz.mymod.tungsten;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.Industry;

/**
 * Tungsten: its ore, the metal and tungsten carbide, and the Tungsten Carbide Drill.
 * <p>
 * The ore is wolframite, found only in deepslate, deep down and rarely: three tries a chunk at a small vein (size 5,
 * so one to five blocks) spread evenly between y -64 and -16, any block that would touch air left out half the time
 * (worldgen/feature and worldgen/placed_feature ore_tungsten). It needs a diamond pickaxe. Raw tungsten only melts in a Foundry run up to
 * {@link TungstenRecipes#INGOT_TEMPERATURE}, slowly; an ingot carburised with coal at
 * {@link TungstenRecipes#CARBIDE_TEMPERATURE} becomes tungsten carbide, the hard material the drill, tungsten-core
 * 5.56 rounds and the tungsten sabot are made of.
 */
public final class Tungsten {
	/** What repairs the drill. */
	public static final TagKey<Item> TOOL_MATERIALS = TagKey.create(Registries.ITEM, MyMod.id("tungsten_tool_materials"));
	/** Very durable, a little slower to dig than diamond, and harvests whatever netherite does. */
	public static final ToolMaterial CARBIDE = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4000, 7.0f, 4.0f, 12, TOOL_MATERIALS);

	public static final Block TUNGSTEN_ORE = block("tungsten_ore", properties -> new DropExperienceBlock(UniformInt.of(3, 7), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.DEEPSLATE));
	public static final Block RAW_TUNGSTEN_BLOCK = block("raw_tungsten_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(5.0f, 6.0f));
	public static final Block TUNGSTEN_BLOCK = block("tungsten_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(7.0f, 12.0f).sound(SoundType.METAL));

	public static final Item RAW_TUNGSTEN = item("raw_tungsten");
	public static final Item TUNGSTEN_INGOT = item("tungsten_ingot");
	public static final Item TUNGSTEN_NUGGET = item("tungsten_nugget");
	public static final Item TUNGSTEN_CARBIDE = item("tungsten_carbide");
	/** Digs 3x3 unless the player sneaks; very durable, and as slow to swing as an axe. */
	public static final Item TUNGSTEN_CARBIDE_DRILL = register("tungsten_carbide_drill", TungstenDrillItem::new,
			new Item.Properties().pickaxe(CARBIDE, 1.0f, -3.4f).rarity(Rarity.RARE));

	private static final ResourceKey<PlacedFeature> ORE_TUNGSTEN = ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id("ore_tungsten"));
	private static final ResourceKey<CreativeModeTab> INDUSTRY_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, MyMod.id("industry"));

	private Tungsten() {
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name) {
		return register(name, Item::new, new Item.Properties());
	}

	public static void initialize() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_TUNGSTEN);
		CreativeModeTabEvents.modifyOutputEvent(INDUSTRY_TAB).register(output -> {
			output.insertAfter(Industry.DEEPSLATE_OIL_SHALE, TUNGSTEN_ORE);
			output.insertAfter(Industry.ALUMINIUM_BLOCK, RAW_TUNGSTEN, RAW_TUNGSTEN_BLOCK, TUNGSTEN_INGOT, TUNGSTEN_NUGGET, TUNGSTEN_BLOCK,
					TUNGSTEN_CARBIDE, TUNGSTEN_CARBIDE_DRILL);
		});
		TungstenDrillItem.initialize();
	}
}
