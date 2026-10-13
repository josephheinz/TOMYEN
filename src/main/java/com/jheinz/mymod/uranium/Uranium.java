package com.jheinz.mymod.uranium;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.tungsten.Tungsten;

/**
 * Uranium: its ores, the Centrifuge that enriches it, what's made of it, and keeping safe from it.
 * <p>
 * The ore (stone and deepslate forms) lies deep, between y -48 and 0, and is several times rarer than diamond: two
 * tries a chunk at a small vein (size 5, so one to five blocks), any block that would touch air left out half the time
 * (worldgen ore_uranium). It glows faintly (light 3) and needs an iron pickaxe. The Centrifuge spins three raw uranium into one
 * enriched and two depleted. Enriched uranium refills a fusion core in the Foundry and is clad into fuel rods (an
 * alternative fusion core recipe); depleted uranium makes the DU 5.56 round and the DU sabot.
 * <p>
 * Raw and loose enriched uranium are radioactive (see {@link Radiation}); the Geiger Counter clicks near the ore, and
 * the Hazmat Suit or a full suit of power armor keeps the radiation out.
 */
public final class Uranium {
	/** The ores the Geiger Counter picks up and that irradiate whoever digs them. */
	public static final TagKey<Block> URANIUM_ORES = TagKey.create(Registries.BLOCK, MyMod.id("uranium_ores"));
	private static final TagKey<Item> REPAIRS_HAZMAT_SUIT = TagKey.create(Registries.ITEM, MyMod.id("repairs_hazmat_suit"));
	/** Little more protection than leather; what it's for is the radiation. */
	private static final ArmorMaterial HAZMAT = new ArmorMaterial(7,
			Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 2, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 2),
			10, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, REPAIRS_HAZMAT_SUIT,
			ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id("hazmat")));

	// --- Blocks ------------------------------------------------------------------------------------------------
	public static final Block URANIUM_ORE = block("uranium_ore", properties -> new DropExperienceBlock(UniformInt.of(2, 5), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3.0f, 3.0f).lightLevel(state -> 3));
	public static final Block DEEPSLATE_URANIUM_ORE = block("deepslate_uranium_ore",
			properties -> new DropExperienceBlock(UniformInt.of(2, 5), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE).lightLevel(state -> 3));
	public static final Block RAW_URANIUM_BLOCK = block("raw_uranium_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(5.0f, 6.0f).lightLevel(state -> 3));
	public static final Block CENTRIFUGE = block("centrifuge", CentrifugeBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops()
					.noOcclusion());

	public static final BlockEntityType<CentrifugeBlockEntity> CENTRIFUGE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("centrifuge"), new BlockEntityType<>(CentrifugeBlockEntity::new, Set.of(CENTRIFUGE)));
	public static final MenuType<CentrifugeMenu> CENTRIFUGE_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("centrifuge"),
			new MenuType<>(CentrifugeMenu::new, FeatureFlags.VANILLA_SET));

	// --- Items -------------------------------------------------------------------------------------------------
	public static final Item RAW_URANIUM = item("raw_uranium", new Item.Properties());
	public static final Item ENRICHED_URANIUM = item("enriched_uranium", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item DEPLETED_URANIUM = item("depleted_uranium", new Item.Properties());
	/** Enriched uranium clad in steel: shielded, and for now an alternative to redstone in a fusion core. */
	public static final Item URANIUM_FUEL_ROD = item("uranium_fuel_rod", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item GEIGER_COUNTER = register("geiger_counter", GeigerCounterItem::new, new Item.Properties().stacksTo(1));
	public static final Item HAZMAT_HELMET = hazmat("hazmat_helmet", ArmorType.HELMET);
	public static final Item HAZMAT_SUIT = hazmat("hazmat_suit", ArmorType.CHESTPLATE);
	public static final Item HAZMAT_LEGGINGS = hazmat("hazmat_leggings", ArmorType.LEGGINGS);
	public static final Item HAZMAT_BOOTS = hazmat("hazmat_boots", ArmorType.BOOTS);

	// --- Sounds ------------------------------------------------------------------------------------------------
	public static final SoundEvent GEIGER_CLICK = sound("geiger_click");
	public static final SoundEvent CENTRIFUGE_HUM = sound("centrifuge_hum");

	private static final ResourceKey<PlacedFeature> ORE_URANIUM = ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id("ore_uranium"));
	private static final ResourceKey<CreativeModeTab> INDUSTRY_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, MyMod.id("industry"));

	private Uranium() {
	}

	/** Whether all four pieces of the Hazmat Suit are on. */
	public static boolean wearsHazmatSuit(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(HAZMAT_HELMET) && entity.getItemBySlot(EquipmentSlot.CHEST).is(HAZMAT_SUIT)
				&& entity.getItemBySlot(EquipmentSlot.LEGS).is(HAZMAT_LEGGINGS) && entity.getItemBySlot(EquipmentSlot.FEET).is(HAZMAT_BOOTS);
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

	private static Item item(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	private static Item hazmat(String name, ArmorType type) {
		return register(name, Item::new, new Item.Properties().humanoidArmor(HAZMAT, type));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_URANIUM);
		CreativeModeTabEvents.modifyOutputEvent(INDUSTRY_TAB).register(output -> {
			output.insertAfter(Tungsten.TUNGSTEN_ORE, URANIUM_ORE, DEEPSLATE_URANIUM_ORE);
			output.insertAfter(Tungsten.TUNGSTEN_CARBIDE_DRILL, RAW_URANIUM, RAW_URANIUM_BLOCK, ENRICHED_URANIUM, DEPLETED_URANIUM,
					URANIUM_FUEL_ROD, GEIGER_COUNTER, HAZMAT_HELMET, HAZMAT_SUIT, HAZMAT_LEGGINGS, HAZMAT_BOOTS);
			output.insertAfter(Industry.METAL_PRESS, CENTRIFUGE);
		});
		Radiation.initialize();
	}
}
