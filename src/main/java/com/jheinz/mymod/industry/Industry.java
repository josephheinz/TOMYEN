package com.jheinz.mymod.industry;

import java.util.Set;
import java.util.function.Function;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.hogfire.Hogfires;

/**
 * The oil-to-aerospace industry: its ores, crude oil, alloys, machines and parts, the worldgen that hides the oil,
 * and the Industry creative tab (which also holds the Hogfire gear).
 * <p>
 * The chain: bauxite and oil shale are mined; the Foundry makes steel, aluminium (it needs a hot Foundry) and
 * duralumin, and retorts shale into crude oil; a Pump Jack on an Oil Seep pumps crude from the pocket below; the
 * Refinery distils it into kerosene (rubber and tar on the side) and blends kerosene into rocket fuel; the Metal
 * Press stamps steel plate and duralumin sheet; and those make the aerospace parts the Hogfire gear is built from.
 */
public final class Industry {
	// --- Crude oil ---------------------------------------------------------------------------------------------
	public static final FlowingFluid FLOWING_CRUDE_OIL = Registry.register(BuiltInRegistries.FLUID, MyMod.id("flowing_crude_oil"),
			new CrudeOilFluid.Flowing());
	public static final FlowingFluid CRUDE_OIL = Registry.register(BuiltInRegistries.FLUID, MyMod.id("crude_oil"), new CrudeOilFluid.Source());
	public static final Block CRUDE_OIL_BLOCK = blockOnly("crude_oil", properties -> new LiquidBlock(CRUDE_OIL, properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).replaceable().noCollision().randomTicks().strength(100.0f)
					.pushReaction(PushReaction.POPPED).noLootTable().liquid().sound(SoundType.EMPTY));

	// --- Ores --------------------------------------------------------------------------------------------------
	public static final Block BAUXITE_ORE = block("bauxite_ore", properties -> new DropExperienceBlock(UniformInt.of(0, 2), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3.0f, 3.0f));
	public static final Block OIL_SHALE = block("oil_shale", properties -> new DropExperienceBlock(UniformInt.of(0, 1), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(3.0f, 3.0f));
	public static final Block DEEPSLATE_OIL_SHALE = block("deepslate_oil_shale", properties -> new DropExperienceBlock(UniformInt.of(0, 1), properties),
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops().strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE));
	public static final Block OIL_SEEP = block("oil_seep", OilSeepBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.MUD));

	// --- Storage blocks ----------------------------------------------------------------------------------------
	public static final Block STEEL_BLOCK = block("steel_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.METAL));
	public static final Block ALUMINIUM_BLOCK = block("aluminium_block", Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).requiresCorrectToolForDrops().strength(4.0f, 6.0f).sound(SoundType.METAL));

	// --- Machines ----------------------------------------------------------------------------------------------
	public static final Block PUMP_JACK = block("pump_jack", PumpJackBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
	public static final Block REFINERY = block("refinery", RefineryBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops()
					.lightLevel(state -> state.getValue(RefineryBlock.LIT) ? 11 : 0));
	public static final Block METAL_PRESS = block("metal_press", MetalPressBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

	public static final BlockEntityType<PumpJackBlockEntity> PUMP_JACK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("pump_jack"), new BlockEntityType<>(PumpJackBlockEntity::new, Set.of(PUMP_JACK)));
	public static final BlockEntityType<RefineryBlockEntity> REFINERY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("refinery"), new BlockEntityType<>(RefineryBlockEntity::new, Set.of(REFINERY)));
	public static final BlockEntityType<MetalPressBlockEntity> METAL_PRESS_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("metal_press"), new BlockEntityType<>(MetalPressBlockEntity::new, Set.of(METAL_PRESS)));

	public static final MenuType<PumpJackMenu> PUMP_JACK_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("pump_jack"),
			new MenuType<>(PumpJackMenu::new, FeatureFlags.VANILLA_SET));
	public static final MenuType<RefineryMenu> REFINERY_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("refinery"),
			new MenuType<>(RefineryMenu::new, FeatureFlags.VANILLA_SET));

	// --- Materials ---------------------------------------------------------------------------------------------
	public static final Item BAUXITE = item("bauxite");
	public static final Item SHALE_CHUNK = item("shale_chunk");
	public static final Item STEEL_INGOT = item("steel_ingot");
	public static final Item ALUMINIUM_INGOT = item("aluminium_ingot");
	public static final Item DURALUMIN_INGOT = item("duralumin_ingot");
	public static final Item STEEL_PLATE = item("steel_plate");
	public static final Item DURALUMIN_SHEET = item("duralumin_sheet");
	public static final Item RUBBER = item("rubber");
	public static final Item TAR = item("tar");

	// --- Oil ---------------------------------------------------------------------------------------------------
	public static final Item CRUDE_OIL_BUCKET = register("crude_oil_bucket", properties -> new BucketItem(CRUDE_OIL, properties),
			new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
	public static final Item EMPTY_CANISTER = register("empty_canister", Item::new, new Item.Properties().stacksTo(16));
	public static final Item CRUDE_OIL_CANISTER = canister("crude_oil_canister");
	public static final Item KEROSENE_CANISTER = canister("kerosene_canister");
	/** Fuels a Hogfire. */
	public static final Item ROCKET_FUEL_CANISTER = canister("rocket_fuel_canister");

	// --- Aerospace components ----------------------------------------------------------------------------------
	public static final Item COMBUSTION_CHAMBER = component("combustion_chamber");
	public static final Item ROCKET_NOZZLE = component("rocket_nozzle");
	public static final Item TURBOPUMP = component("turbopump");
	public static final Item FUEL_TANK = component("fuel_tank");
	public static final Item WING_SPAR = component("wing_spar");
	public static final Item WING_SKIN = component("wing_skin");

	// --- Worldgen ----------------------------------------------------------------------------------------------
	public static final MapCodec<CrudeOilPocketFeature> CRUDE_OIL_POCKET = Registry.register(BuiltInRegistries.FEATURE_TYPE,
			MyMod.id("crude_oil_pocket"), CrudeOilPocketFeature.CODEC);
	private static final ResourceKey<PlacedFeature> ORE_BAUXITE = placed("ore_bauxite");
	private static final ResourceKey<PlacedFeature> ORE_OIL_SHALE = placed("ore_oil_shale");
	private static final ResourceKey<PlacedFeature> CRUDE_OIL_POCKET_PLACED = placed("crude_oil_pocket");

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MyMod.id("industry"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.mymod.industry"))
					.icon(() -> new ItemStack(PUMP_JACK))
					.displayItems((parameters, output) -> {
						output.accept(BAUXITE_ORE);
						output.accept(OIL_SHALE);
						output.accept(DEEPSLATE_OIL_SHALE);
						output.accept(OIL_SEEP);
						output.accept(BAUXITE);
						output.accept(SHALE_CHUNK);
						output.accept(CRUDE_OIL_BUCKET);
						output.accept(STEEL_INGOT);
						output.accept(ALUMINIUM_INGOT);
						output.accept(DURALUMIN_INGOT);
						output.accept(STEEL_BLOCK);
						output.accept(ALUMINIUM_BLOCK);
						output.accept(STEEL_PLATE);
						output.accept(DURALUMIN_SHEET);
						output.accept(RUBBER);
						output.accept(TAR);
						output.accept(PUMP_JACK);
						output.accept(REFINERY);
						output.accept(METAL_PRESS);
						output.accept(EMPTY_CANISTER);
						output.accept(CRUDE_OIL_CANISTER);
						output.accept(KEROSENE_CANISTER);
						output.accept(ROCKET_FUEL_CANISTER);
						output.accept(COMBUSTION_CHAMBER);
						output.accept(ROCKET_NOZZLE);
						output.accept(TURBOPUMP);
						output.accept(FUEL_TANK);
						output.accept(WING_SPAR);
						output.accept(WING_SKIN);
						output.accept(Hogfires.PIG_FLIGHT_HARNESS);
						output.accept(Hogfires.PIG_WINGS);
						output.accept(Hogfires.ROCKET_ENGINE);
					})
					.build());

	private Industry() {
	}

	private static Block blockOnly(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = blockOnly(name, factory, properties);
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

	private static Item canister(String name) {
		return register(name, Item::new, new Item.Properties().stacksTo(16));
	}

	private static Item component(String name) {
		return register(name, Item::new, new Item.Properties().stacksTo(16));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id(name));
	}

	public static void initialize() {
		// Bauxite weathers out of the tropical soils of badlands and savannas, close under the surface.
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BADLANDS).or(BiomeSelectors.tag(BiomeTags.IS_SAVANNA)),
				GenerationStep.Decoration.UNDERGROUND_ORES, ORE_BAUXITE);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_OIL_SHALE);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES, CRUDE_OIL_POCKET_PLACED);
		// Fire spreading over a pool lights it as well as fire beside it.
		FlammableBlockRegistry.getDefaultInstance().add(CRUDE_OIL_BLOCK, 60, 100);
	}
}
