package com.jheinz.tomyen.dragonscale;

import java.util.Map;
import java.util.function.Function;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.longsword.LongSwords;

/**
 * Dragonscale: the scales, the armor made from them, the fossils they're dug from and the Dragonbone Long Sword.
 * <p>
 * Scales come from two places. Ancient Dragon Scales are mined from Dragon Scale Fossil blocks, set into the
 * skeletons of long-dead dragons buried under badlands and deserts and deep in the deepslate everywhere (see
 * {@link DragonFossilFeature}). Ender Dragon Scales drop from every Ender Dragon killed, respawned ones too (see
 * {@link EnderDragonScales}). Either kind crafts and repairs the armor.
 * <p>
 * The armor is netherite's equal with more toughness, and fireproof as an item. Each piece takes some of the sting
 * out of fire; the whole set makes the wearer immune to fire and lava, and lets them flap while gliding
 * (see {@link DragonscaleArmor}). The chestplate is a glider in its own right: it glides like an
 * elytra, with or without the rest of the set.
 */
public final class Dragonscales {
	// --- Fossils -----------------------------------------------------------------------------------------------
	/** A bone block turned to stone over the ages: the bulk of a dragon fossil. */
	public static final Block FOSSILIZED_DRAGON_BONE = block("fossilized_dragon_bone", RotatedPillarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).instrument(NoteBlockInstrument.XYLOPHONE)
					.requiresCorrectToolForDrops().strength(3.0f, 6.0f).sound(SoundType.BONE_BLOCK));
	/** Fossil bone with the dragon's scales still pressed into it. Drops Ancient Dragon Scales. */
	public static final Block DRAGON_SCALE_FOSSIL = block("dragon_scale_fossil",
			properties -> new DropExperienceBlock(UniformInt.of(3, 7), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).instrument(NoteBlockInstrument.XYLOPHONE)
					.requiresCorrectToolForDrops().strength(4.0f, 9.0f).sound(SoundType.BONE_BLOCK));

	// --- Scales ------------------------------------------------------------------------------------------------
	/** Either kind of scale: what the armor is crafted and repaired with (data/tomyen/tags/item/dragon_scales.json). */
	public static final TagKey<Item> DRAGON_SCALES = TagKey.create(Registries.ITEM, MyMod.id("dragon_scales"));
	public static final Item ANCIENT_DRAGON_SCALE = item("ancient_dragon_scale", Item::new,
			new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item ENDER_DRAGON_SCALE = item("ender_dragon_scale", Item::new,
			new Item.Properties().rarity(Rarity.RARE).fireResistant());

	// --- Sounds ------------------------------------------------------------------------------------------------
	public static final Holder<SoundEvent> EQUIP_SOUND = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT,
			MyMod.id("dragonscale_equip"), SoundEvent.createVariableRangeEvent(MyMod.id("dragonscale_equip")));
	public static final SoundEvent WING_FLAP = sound("dragonscale_wing_flap");

	// --- Armor -------------------------------------------------------------------------------------------------
	/**
	 * Netherite's armor points with more toughness (4 a piece against 3) and a little more durability and
	 * enchantability. Equipment asset assets/tomyen/equipment/dragonscale.json: the armor, and the wings the
	 * chestplate spreads when gliding.
	 */
	public static final ArmorMaterial MATERIAL = new ArmorMaterial(40,
			Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 19),
			18, EQUIP_SOUND, 4.0f, 0.1f, DRAGON_SCALES, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id("dragonscale")));

	public static final Item HELMET = armor("dragonscale_helmet", ArmorType.HELMET, new Item.Properties());
	/** Also a glider: vanilla's elytra flight works off the glider component, worn or socketed in an exoskeleton. */
	public static final Item CHESTPLATE = armor("dragonscale_chestplate", ArmorType.CHESTPLATE,
			new Item.Properties().component(DataComponents.GLIDER, Unit.INSTANCE));
	public static final Item LEGGINGS = armor("dragonscale_leggings", ArmorType.LEGGINGS, new Item.Properties());
	public static final Item BOOTS = armor("dragonscale_boots", ArmorType.BOOTS, new Item.Properties());

	// --- Dragonbone Long Sword ---------------------------------------------------------------------------------
	/** Between diamond and netherite for wear, netherite's for damage and speed; repaired with scales. */
	private static final ToolMaterial DRAGONBONE = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1800, 9.0f, 4.0f, 18,
			DRAGON_SCALES);
	public static final Item DRAGONBONE_LONG_SWORD = item("dragonbone_long_sword", DragonboneLongSwordItem::new,
			LongSwords.properties(DRAGONBONE).fireResistant().rarity(Rarity.EPIC));

	// --- Worldgen ----------------------------------------------------------------------------------------------
	public static final MapCodec<DragonFossilFeature> DRAGON_FOSSIL = Registry.register(BuiltInRegistries.FEATURE_TYPE,
			MyMod.id("dragon_fossil"), DragonFossilFeature.CODEC);
	private static final ResourceKey<PlacedFeature> DRAGON_FOSSIL_BURIED = ResourceKey.create(Registries.PLACED_FEATURE,
			MyMod.id("dragon_fossil_buried"));
	private static final ResourceKey<PlacedFeature> DRAGON_FOSSIL_DEEP = ResourceKey.create(Registries.PLACED_FEATURE,
			MyMod.id("dragon_fossil_deep"));

	private Dragonscales() {
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item armor(String name, ArmorType type, Item.Properties properties) {
		return item(name, Item::new, properties.humanoidArmor(MATERIAL, type).fireResistant().rarity(Rarity.EPIC));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		// Buried a little under the sand and terracotta of the dry biomes; deep in the deepslate anywhere.
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BADLANDS).or(BiomeSelectors.includeByKey(Biomes.DESERT)),
				GenerationStep.Decoration.UNDERGROUND_STRUCTURES, DRAGON_FOSSIL_BURIED);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_STRUCTURES,
				DRAGON_FOSSIL_DEEP);
		DragonscaleArmor.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_BOOTS, HELMET, CHESTPLATE, LEGGINGS, BOOTS);
			output.insertAfter(Items.NETHERITE_SWORD, DRAGONBONE_LONG_SWORD);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.NETHERITE_SCRAP, ANCIENT_DRAGON_SCALE, ENDER_DRAGON_SCALE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
				.register(output -> output.insertAfter(Items.BONE_BLOCK, FOSSILIZED_DRAGON_BONE, DRAGON_SCALE_FOSSIL));
	}
}
