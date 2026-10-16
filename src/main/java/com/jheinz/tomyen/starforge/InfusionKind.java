package com.jheinz.tomyen.starforge;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.drill.DrillItem;

/**
 * What sort of gear an item is, as far as the Star Forge cares: each {@link Infusion} takes on some sorts and does
 * something different on each. Guns, drills, rods and armor are one sort each; an axe is both a melee weapon and a
 * tool, and gets both halves of an infusion.
 */
public enum InfusionKind {
	MELEE("melee"),
	GUN("gun"),
	TOOL("tool"),
	/** Drills share the tools' effect lines (they dig like one). */
	DRILL("tool"),
	ARMOR("armor"),
	ROD("rod");

	/** The lang key part for this sort's effect line: {@code infusion.tomyen.<infusion>.<key>}. */
	public final String key;

	InfusionKind(String key) {
		this.key = key;
	}

	/** The sorts of gear this stack is; empty if the Star Forge can't infuse it. */
	public static Set<InfusionKind> of(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1) {
			return Set.of();
		}
		Item item = stack.getItem();
		if (item instanceof GunItem) {
			return EnumSet.of(GUN);
		}
		if (item instanceof DrillItem) {
			return EnumSet.of(DRILL);
		}
		if (item instanceof FishingRodItem) {
			return EnumSet.of(ROD);
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			// Damageable, so a carved pumpkin or a mob head isn't armor.
			return stack.isDamageableItem() ? EnumSet.of(ARMOR) : Set.of();
		}
		EnumSet<InfusionKind> kinds = EnumSet.noneOf(InfusionKind.class);
		boolean axe = stack.is(ItemTags.AXES);
		if (axe || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
			kinds.add(TOOL);
		}
		if (axe || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)
				|| stack.has(DataComponents.WEAPON) && !kinds.contains(TOOL) && stack.isDamageableItem()) {
			kinds.add(MELEE);
		}
		return kinds;
	}

	/** Whether an item of these sorts is a weapon, so its infusion's on-hit half works through it. */
	public static boolean strikes(Set<InfusionKind> kinds) {
		return kinds.contains(MELEE) || kinds.contains(GUN);
	}

	/** Whether an item of these sorts digs, so its infusion's tool half applies. */
	public static boolean digs(Set<InfusionKind> kinds) {
		return kinds.contains(TOOL) || kinds.contains(DRILL);
	}

	/** Whether it's carried rather than worn: gear in the hand. */
	public static boolean held(Set<InfusionKind> kinds) {
		return !kinds.isEmpty() && !kinds.contains(ARMOR);
	}
}
