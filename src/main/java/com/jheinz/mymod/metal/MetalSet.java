package com.jheinz.mymod.metal;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.MyMod;

/**
 * A metal's whole kit, registered in one go: its ingot, nugget and storage block, the five vanilla tools and the four
 * armor pieces, named {@code <metal>_ingot}, {@code <metal>_sword} and so on. The tools take vanilla's per-tool damage
 * and speed baselines except the axe's and hoe's, which vanilla also varies by tier. Each tool and piece of armor
 * carries the metal's hint lines in its tooltip.
 */
public final class MetalSet {
	/** Vanilla's attack baselines for the tools that don't vary by tier. */
	private static final float SWORD_DAMAGE = 3.0f, SWORD_SPEED = -2.4f;
	private static final float SHOVEL_DAMAGE = 1.5f, SHOVEL_SPEED = -3.0f;
	private static final float PICKAXE_DAMAGE = 1.0f, PICKAXE_SPEED = -2.8f;

	public final Item ingot;
	public final Item nugget;
	public final Block block;
	public final Item sword;
	public final Item pickaxe;
	public final Item axe;
	public final Item shovel;
	public final Item hoe;
	public final Item helmet;
	public final Item chestplate;
	public final Item leggings;
	public final Item boots;

	/**
	 * @param weapon builds the sword and axe (the tools that fight), given their properties and hint keys
	 * @param blockLight how brightly the storage block glows
	 */
	public MetalSet(String metal, ToolMaterial tools, ArmorMaterial armor, float axeDamage, float axeSpeed, float hoeDamage,
			float hoeSpeed, BiFunction<Item.Properties, List<String>, Item> weapon, List<String> toolHints, List<String> armorHints,
			MapColor blockColor, int blockLight, boolean fireResistant) {
		ingot = register(metal + "_ingot", Item::new, properties(fireResistant));
		nugget = register(metal + "_nugget", Item::new, properties(fireResistant));
		block = block(metal + "_block", BlockBehaviour.Properties.of().mapColor(blockColor).requiresCorrectToolForDrops()
				.strength(5.0f, 6.0f).sound(SoundType.METAL).lightLevel(state -> blockLight), fireResistant);
		sword = register(metal + "_sword", properties -> weapon.apply(properties, toolHints),
				properties(fireResistant).sword(tools, SWORD_DAMAGE, SWORD_SPEED));
		pickaxe = register(metal + "_pickaxe", properties -> new HintItem(properties, toolHints),
				properties(fireResistant).pickaxe(tools, PICKAXE_DAMAGE, PICKAXE_SPEED));
		axe = register(metal + "_axe", properties -> weapon.apply(properties, toolHints),
				properties(fireResistant).axe(tools, axeDamage, axeSpeed));
		shovel = register(metal + "_shovel", properties -> new HintItem(properties, toolHints),
				properties(fireResistant).shovel(tools, SHOVEL_DAMAGE, SHOVEL_SPEED));
		hoe = register(metal + "_hoe", properties -> new HintItem(properties, toolHints),
				properties(fireResistant).hoe(tools, hoeDamage, hoeSpeed));
		helmet = armor(metal + "_helmet", armor, ArmorType.HELMET, armorHints, fireResistant);
		chestplate = armor(metal + "_chestplate", armor, ArmorType.CHESTPLATE, armorHints, fireResistant);
		leggings = armor(metal + "_leggings", armor, ArmorType.LEGGINGS, armorHints, fireResistant);
		boots = armor(metal + "_boots", armor, ArmorType.BOOTS, armorHints, fireResistant);
	}

	/** The repair and tool-material tag for a metal: {@code mymod:repairs_<metal>}, holding its ingot. */
	public static TagKey<Item> repairTag(String metal) {
		return TagKey.create(Registries.ITEM, MyMod.id("repairs_" + metal));
	}

	public List<Item> tools() {
		return List.of(sword, pickaxe, axe, shovel, hoe);
	}

	public List<Item> armor() {
		return List.of(helmet, chestplate, leggings, boots);
	}

	/** The sword or the axe: the ones whose hits carry the metal's weapon effect. */
	public boolean isWeapon(ItemStack stack) {
		return stack.is(sword) || stack.is(axe);
	}

	public boolean isTool(ItemStack stack) {
		return tools().stream().anyMatch(stack::is);
	}

	/** How many pieces of this metal's armor the entity wears. */
	public int piecesWorn(LivingEntity entity) {
		int worn = 0;
		worn += entity.getItemBySlot(EquipmentSlot.HEAD).is(helmet) ? 1 : 0;
		worn += entity.getItemBySlot(EquipmentSlot.CHEST).is(chestplate) ? 1 : 0;
		worn += entity.getItemBySlot(EquipmentSlot.LEGS).is(leggings) ? 1 : 0;
		worn += entity.getItemBySlot(EquipmentSlot.FEET).is(boots) ? 1 : 0;
		return worn;
	}

	public boolean wearsFullSet(LivingEntity entity) {
		return piecesWorn(entity) == 4;
	}

	private static Item.Properties properties(boolean fireResistant) {
		Item.Properties properties = new Item.Properties();
		return fireResistant ? properties.fireResistant() : properties;
	}

	private static Item armor(String name, ArmorMaterial material, ArmorType type, List<String> hints, boolean fireResistant) {
		return register(name, properties -> new HintItem(properties, hints), properties(fireResistant).humanoidArmor(material, type));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, BlockBehaviour.Properties properties, boolean fireResistant) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, properties(fireResistant).setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
