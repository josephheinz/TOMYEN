package com.jheinz.tomyen.bloomery;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.ModBlocks;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.metal.HintItem;
import com.jheinz.tomyen.metal.MetalSet;

/**
 * The Bloomery: a clay-and-brick shaft furnace with a bellows, the first-hour step toward the Foundry. Raw iron (or
 * iron ore) and charcoal go in; worked hot enough with the bellows ({@link BloomeryBlockEntity}), it gives a spongy
 * {@link #BLOOM} of iron. Hammered out, on an anvil ({@link BloomHammering}) or at the Bloomery's own forge step, a
 * bloom becomes a Bloom Steel Ingot.
 *
 * <p>Bloom steel is a crude steel. Its own kit ({@link #SET}: tools a little past iron, armor iron's with more wear in
 * it) is cheap; and it stands in for real steel in a short list of early recipes ({@link BloomSteelRecipes}), always at
 * a premium: about half as much again, rounded up, and never less than one more ingot than the steel recipe takes. The
 * gear is the same; you pay for the crude metal in quantity. The Foundry refines it into real steel.
 */
public final class Bloomery {
	public static final String STEEL = "bloom_steel";
	public static final TagKey<Item> REPAIRS = MetalSet.repairTag(STEEL);
	/** Iron's mining level and speed, half again its wear. */
	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 400, 6.5f, 2.0f, 12, REPAIRS);
	/** Iron's protection, a little toughness and a third more wear. */
	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(20,
			Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2, ArmorType.BODY, 5),
			10, SoundEvents.ARMOR_EQUIP_IRON, 0.5f, 0.0f, REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id(STEEL)));
	/** The line every bloom steel item carries: it's the stepping stone. */
	public static final String CRUDE_HINT = "item.tomyen.bloom_steel.crude";
	public static final MetalSet SET = new MetalSet(STEEL, TOOL_MATERIAL, ARMOR_MATERIAL, 6.0f, -3.1f, -2.0f, -1.0f, HintItem::new,
			List.of(CRUDE_HINT), List.of(CRUDE_HINT), MapColor.COLOR_GRAY, 0, false);

	/** Hammer blows a bloom on an anvil has taken. Absent means none. */
	public static final DataComponentType<Integer> BLOOM_HITS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("bloom_hits"), DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final Item BLOOM = item("bloom", properties -> new HintItem(properties, List.of("item.tomyen.bloom.hint")));

	public static final Block BLOOMERY = block("bloomery", BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
			.strength(2.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()
			.lightLevel(state -> state.getValue(BloomeryBlock.LIT) ? 12 : 0));
	public static final BlockEntityType<BloomeryBlockEntity> BLOOMERY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("bloomery"), new BlockEntityType<>(BloomeryBlockEntity::new, Set.of(BLOOMERY)));
	public static final MenuType<BloomeryMenu> BLOOMERY_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("bloomery"),
			new MenuType<>(BloomeryMenu::new, FeatureFlags.VANILLA_SET));

	private static final ResourceKey<CreativeModeTab> INDUSTRY_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, MyMod.id("industry"));

	private Bloomery() {
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new BloomeryBlock(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void initialize() {
		UseBlockCallback.EVENT.register(BloomHammering::useOnBlock);
		// The Bloomery comes before the Foundry it leads to, and its metal before steel.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertBefore(ModBlocks.FOUNDRY, BLOOMERY));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.IRON_INGOT, BLOOM, SET.ingot, SET.nugget));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Items.IRON_BLOCK, SET.block.asItem()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.IRON_HOE, SET.shovel, SET.pickaxe, SET.axe, SET.hoe));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.IRON_SWORD, SET.sword);
			output.insertAfter(Items.IRON_AXE, SET.axe);
			output.insertAfter(Items.IRON_BOOTS, SET.helmet, SET.chestplate, SET.leggings, SET.boots);
		});
		CreativeModeTabEvents.modifyOutputEvent(INDUSTRY_TAB)
				.register(output -> output.insertBefore(Industry.STEEL_INGOT, BLOOMERY.asItem(), BLOOM, SET.ingot));
	}
}
