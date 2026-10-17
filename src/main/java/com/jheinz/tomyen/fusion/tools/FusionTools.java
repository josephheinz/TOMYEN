package com.jheinz.tomyen.fusion.tools;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.block.Block;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.drill.DrillItem;
import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.fishing.RodCondition;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.catfish.GiantCatfishes;
import com.jheinz.tomyen.fusion.Fusions;

/**
 * Fusion batch 4, tools, fishing and odd items, all made at a fae pond:
 * <ul>
 * <li>Barbel Lash (Nose Hair Whip + Catfish Whisker): a longer whip whose hits drag what they hit in, and whose lash
 * sweeps dropped items to you ({@link BarbelLashItem}).
 * <li>Grave Auger (Gravedigger's Shovel + Steel Drill): a heavy-engine drill that bores 3x3 through stone and earth,
 * and by night digs earth instantly ({@link GraveAugerItem}).
 * <li>Hexed Rod (Hag Charm + Fae Rod): every fish it lands is hexed, a gamble when eaten; its fights are a step harder
 * ({@link HexedRodItem}, {@link HexedCatch}).
 * <li>Whiskerline Rod (Giant Catfish + Grapple Claw): casts far, and reels in hooked mobs and boats
 * ({@link WhiskerlineRodItem}).
 * <li>Writ of Absolution (Extension Form + Crucifix): lifts a Hag's Curse, or pushes a debt's deadline days back and
 * calls the collectors off ({@link WritOfAbsolutionItem}).
 * </ul>
 */
public final class FusionTools {
	/** What the Grave Auger digs as its own: anything a pickaxe or a shovel does (data/tomyen/tags/block). */
	public static final TagKey<Block> MINEABLE_WITH_GRAVE_AUGER = TagKey.create(Registries.BLOCK, MyMod.id("mineable/grave_auger"));
	/** A heavy drill: between the Steel Drill and the Tungsten Carbide Drill, a little behind carbide's speed. */
	public static final ToolMaterial GRAVE_AUGER_MATERIAL = new ToolMaterial(DrillItem.INCORRECT_FOR_POWERED_DRILL, 2600, 11.0f, 3.5f, 14,
			TagKey.create(Registries.ITEM, MyMod.id("repairs_grave_auger")));

	public static final ConsumeEffect.Type<HexedCatch> HEXED_CATCH = Registry.register(BuiltInRegistries.CONSUME_EFFECT_TYPE,
			MyMod.id("hexed_catch"), new ConsumeEffect.Type<>(HexedCatch.CODEC, HexedCatch.STREAM_CODEC));

	public static final Item BARBEL_LASH = item("barbel_lash", BarbelLashItem::new, BarbelLashItem.properties());
	public static final Item GRAVE_AUGER = item("grave_auger", GraveAugerItem::new, GraveAugerItem.properties());
	/** Tier 3: the Fae Rod (2) with the hag's luck tied in for good. */
	public static final Item HEXED_ROD = item("hexed_rod", properties -> new HexedRodItem(properties,
			RodModifiers.builder().zone(0.1f).rarity(0.15f).nightRarity(0.15f).cursedBonus().build(), RodCondition.FEYWILD,
			RodModifiers.builder().biteTicks(80).rarity(0.25f).build()),
			new Item.Properties().durability(420).repairable(Fae.FAE_SILVER_INGOT).enchantable(1).rarity(Rarity.RARE));
	/** Tier 3: a whisker line and a claw for a hook; it holds what it takes. */
	public static final Item WHISKERLINE_ROD = item("whiskerline_rod", properties -> new WhiskerlineRodItem(properties,
			RodModifiers.builder().line(0.35f).escape(0.25f).reel(0.15f).zone(0.05f).build()),
			new Item.Properties().durability(600).repairable(GiantCatfishes.CATFISH_WHISKER).enchantable(1).rarity(Rarity.RARE));
	public static final Item WRIT_OF_ABSOLUTION = item("writ_of_absolution", WritOfAbsolutionItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	private FusionTools() {
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** From {@link Fusions#initialize}. */
	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.TRIDENT, BARBEL_LASH));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.NETHERITE_HOE, GRAVE_AUGER);
			output.insertAfter(Items.FISHING_ROD, HEXED_ROD, WHISKERLINE_ROD);
			output.insertAfter(Items.WRITABLE_BOOK, WRIT_OF_ABSOLUTION);
		});
	}
}
