package com.jheinz.mymod.silver;

import java.util.Map;
import java.util.function.Function;

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
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.holy.CrucifixItem;
import com.jheinz.mymod.longsword.LongSwordItem;
import com.jheinz.mymod.longsword.LongSwords;

/**
 * Silver and holy silver: the ore and its worldgen, the metal, and what's made from it.
 * <p>
 * Silver Ore (and its deepslate form) is mined with an iron pickaxe for Raw Silver, smelted into ingots. It lies a
 * little rarer than iron, at y -32 to 48 (most common around y 8), and twice as thick under mountains and badlands
 * (data/mymod/worldgen). Silver bites the evil (see {@link EvilBane}): silver rounds, a silver sword and long sword.
 * <p>
 * Silver dropped on the ground and caught in crucifix light turns holy (see {@link HolySilver}). Holy silver makes
 * tools and swords that bite the evil harder, rounds that set them alight, a crucifix with stronger light, and armor
 * that wards evil off (see {@link HolySilverArmor}).
 */
public final class Silver {
	// --- Sounds (vanilla sounds, re-pitched in sounds.json) ------------------------------------------------------------
	/** A choir-like swell as silver is consecrated. */
	public static final SoundEvent CONSECRATE = sound("holy_silver_consecrate");
	/** Holy silver armor turning an evil blow aside. */
	public static final SoundEvent WARD = sound("holy_silver_ward");
	public static final Holder<SoundEvent> EQUIP_HOLY_SILVER = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT,
			MyMod.id("item.armor.equip_holy_silver"), SoundEvent.createVariableRangeEvent(MyMod.id("item.armor.equip_holy_silver")));

	// --- Ores and blocks -----------------------------------------------------------------------------------------------
	public static final Block SILVER_ORE = block("silver_ore", properties -> new DropExperienceBlock(ConstantInt.of(0), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3.0f, 3.0f));
	public static final Block DEEPSLATE_SILVER_ORE = block("deepslate_silver_ore",
			properties -> new DropExperienceBlock(ConstantInt.of(0), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE));
	public static final Block RAW_SILVER_BLOCK = block("raw_silver_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(5.0f, 6.0f));
	public static final Block SILVER_BLOCK = block("silver_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.BELL)
					.requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.METAL));
	/** Glows faintly. */
	public static final Block HOLY_SILVER_BLOCK = block("holy_silver_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.BELL)
					.requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.METAL).lightLevel(state -> 6));

	// --- Materials -----------------------------------------------------------------------------------------------------
	public static final Item RAW_SILVER = item("raw_silver", new Item.Properties());
	public static final Item SILVER_INGOT = item("silver_ingot", new Item.Properties());
	public static final Item SILVER_NUGGET = item("silver_nugget", new Item.Properties());
	public static final Item HOLY_SILVER_INGOT = item("holy_silver_ingot", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item HOLY_SILVER_NUGGET = item("holy_silver_nugget", new Item.Properties().rarity(Rarity.UNCOMMON));

	public static final TagKey<Item> SILVER_TOOL_MATERIALS = TagKey.create(Registries.ITEM, MyMod.id("silver_tool_materials"));
	public static final TagKey<Item> HOLY_SILVER_TOOL_MATERIALS = TagKey.create(Registries.ITEM, MyMod.id("holy_silver_tool_materials"));
	public static final TagKey<Item> REPAIRS_HOLY_SILVER_ARMOR = TagKey.create(Registries.ITEM, MyMod.id("repairs_holy_silver_armor"));

	/** Soft like gold: it barely lasts, and takes enchantments well. Only swords are made of it. */
	public static final ToolMaterial SILVER = new ToolMaterial(BlockTags.INCORRECT_FOR_GOLD_TOOL, 64, 10.0f, 1.0f, 20,
			SILVER_TOOL_MATERIALS);
	/** Mines like iron, lasts twice as long, hits like diamond and takes enchantments almost as well as gold. */
	public static final ToolMaterial HOLY_SILVER = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 8.0f, 2.0f, 22,
			HOLY_SILVER_TOOL_MATERIALS);

	// --- Weapons and tools ---------------------------------------------------------------------------------------------
	public static final Item SILVER_SWORD = item("silver_sword",
			new Item.Properties().sword(SILVER, 3.0f, -2.4f).component(ModComponents.EVIL_BANE, EvilBane.SILVER));
	public static final Item SILVER_LONG_SWORD = register("silver_long_sword", LongSwordItem::new,
			LongSwords.properties(SILVER).component(ModComponents.EVIL_BANE, EvilBane.SILVER));
	public static final Item HOLY_SILVER_SWORD = item("holy_silver_sword", holy().sword(HOLY_SILVER, 3.0f, -2.4f));
	public static final Item HOLY_SILVER_LONG_SWORD = register("holy_silver_long_sword", LongSwordItem::new,
			LongSwords.properties(HOLY_SILVER).rarity(Rarity.UNCOMMON).component(ModComponents.EVIL_BANE, EvilBane.HOLY_SILVER));
	public static final Item HOLY_SILVER_SHOVEL = item("holy_silver_shovel", holy().shovel(HOLY_SILVER, 1.5f, -3.0f));
	public static final Item HOLY_SILVER_PICKAXE = item("holy_silver_pickaxe", holy().pickaxe(HOLY_SILVER, 1.0f, -2.8f));
	public static final Item HOLY_SILVER_AXE = item("holy_silver_axe", holy().axe(HOLY_SILVER, 6.0f, -3.1f));
	public static final Item HOLY_SILVER_HOE = item("holy_silver_hoe", holy().hoe(HOLY_SILVER, -2.0f, -1.0f));
	/** Orbs hit half again as hard, and the beam charges in 1.2 seconds instead of 2. */
	public static final Item HOLY_SILVER_CRUCIFIX = register("holy_silver_crucifix",
			properties -> new CrucifixItem(properties, 6.0f, 24), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	// --- Armor ---------------------------------------------------------------------------------------------------------
	/** Iron's protection with a little toughness, a third longer-lasting; what matters is the ward (HolySilverArmor). */
	public static final ArmorMaterial HOLY_SILVER_ARMOR_MATERIAL = new ArmorMaterial(20,
			Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2, ArmorType.BODY, 5),
			22, EQUIP_HOLY_SILVER, 1.0f, 0.0f, REPAIRS_HOLY_SILVER_ARMOR,
			ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id("holy_silver")));
	public static final Item HOLY_SILVER_HELMET = armor("holy_silver_helmet", ArmorType.HELMET);
	public static final Item HOLY_SILVER_CHESTPLATE = armor("holy_silver_chestplate", ArmorType.CHESTPLATE);
	public static final Item HOLY_SILVER_LEGGINGS = armor("holy_silver_leggings", ArmorType.LEGGINGS);
	public static final Item HOLY_SILVER_BOOTS = armor("holy_silver_boots", ArmorType.BOOTS);

	// --- Worldgen ------------------------------------------------------------------------------------------------------
	private static final ResourceKey<PlacedFeature> ORE_SILVER = placed("ore_silver");
	private static final ResourceKey<PlacedFeature> ORE_SILVER_EXTRA = placed("ore_silver_extra");

	private Silver() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Item.Properties itemProperties = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
		if (name.startsWith("holy_")) {
			itemProperties.rarity(Rarity.UNCOMMON);
		}
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
		return block;
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	/** Holy silver gear: uncommon, and it bites the evil. */
	private static Item.Properties holy() {
		return new Item.Properties().rarity(Rarity.UNCOMMON).component(ModComponents.EVIL_BANE, EvilBane.HOLY_SILVER);
	}

	private static Item armor(String name, ArmorType type) {
		return item(name, new Item.Properties().humanoidArmor(HOLY_SILVER_ARMOR_MATERIAL, type).rarity(Rarity.UNCOMMON));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id(name));
	}

	public static void initialize() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_SILVER);
		// Silver runs in the veins of mountains and the eroded mesas of the badlands: a second, equal helping there.
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN).or(BiomeSelectors.tag(BiomeTags.IS_BADLANDS)),
				GenerationStep.Decoration.UNDERGROUND_ORES, ORE_SILVER_EXTRA);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.DEEPSLATE_IRON_ORE, SILVER_ORE, DEEPSLATE_SILVER_ORE);
			output.insertAfter(Items.RAW_IRON_BLOCK, RAW_SILVER_BLOCK);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Items.IRON_BLOCK, SILVER_BLOCK, HOLY_SILVER_BLOCK));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.insertAfter(Items.RAW_GOLD, RAW_SILVER);
			output.insertAfter(Items.GOLD_NUGGET, SILVER_NUGGET, HOLY_SILVER_NUGGET);
			output.insertAfter(Items.GOLD_INGOT, SILVER_INGOT, HOLY_SILVER_INGOT);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
				output.insertAfter(Items.GOLDEN_HOE, HOLY_SILVER_SHOVEL, HOLY_SILVER_PICKAXE, HOLY_SILVER_AXE, HOLY_SILVER_HOE));
		// After ModItems' own combat entries, which these sit among.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(ModItems.NETHERITE_LONG_SWORD, SILVER_SWORD, SILVER_LONG_SWORD, HOLY_SILVER_SWORD, HOLY_SILVER_LONG_SWORD);
			output.insertAfter(Items.GOLDEN_BOOTS, HOLY_SILVER_HELMET, HOLY_SILVER_CHESTPLATE, HOLY_SILVER_LEGGINGS, HOLY_SILVER_BOOTS);
			output.insertAfter(ModItems.CRUCIFIX, HOLY_SILVER_CRUCIFIX);
		});

		HolySilverArmor.initialize();
	}
}
