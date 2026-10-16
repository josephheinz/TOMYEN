package com.jheinz.mymod.scrapbench;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;

/**
 * The Scrap Bench: the Scavenger tier's home base and the first mod station, a cluttered workbench with a vise and a
 * hanging lamp. Cheaper than the Weapon Station (an iron ingot, two nuggets, planks and a crafting table). Two modes:
 * <ul>
 * <li><b>Salvage</b> breaks junk down ({@link SalvageRecipes}): fished-up scrap, machine parts, worn iron and copper
 * gear and old hardware come apart into nuggets, tech scrap, {@link #SPRING springs} and {@link #SCREWS screws}.</li>
 * <li><b>Assemble</b> builds the homemade guns, a few gadgets and the survival kit's metalwork ({@link BenchRecipes}),
 * plus Workshop guns once their blueprints are read.</li>
 * </ul>
 */
public final class ScrapBench {
	public static final Block SCRAP_BENCH = block("scrap_bench", ScrapBenchBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion().lightLevel(state -> 9));
	public static final Item SCRAP_BENCH_ITEM = BuiltInRegistries.ITEM.getValue(MyMod.id("scrap_bench"));

	/** A coil spring: in triggers, traps, magazines and bells. Salvaged from tripwire hooks, servo arms and tech scrap. */
	public static final Item SPRING = item("spring");
	/** A handful of screws: what holds homemade gear together. Salvaged from nearly any junk. */
	public static final Item SCREWS = item("screws");

	public static final MenuType<ScrapBenchMenu> MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("scrap_bench"),
			new MenuType<>(ScrapBenchMenu::new, FeatureFlags.VANILLA_SET));

	private ScrapBench() {
	}

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void initialize() {
		BenchRecipes.initialize();
		SalvageRecipes.initialize();
		// The first station, so before the Weapon Station (which sits right after the crafting table).
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.CRAFTING_TABLE, SCRAP_BENCH_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(ModItems.TECH_SCRAP, SPRING, SCREWS));
	}
}
