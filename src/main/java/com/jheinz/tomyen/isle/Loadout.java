package com.jheinz.tomyen.isle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.jheinz.tomyen.GunItem;

/**
 * What a player wore and held when they died: the Revenant is dressed in copies of these. The real items are in the
 * hoard; these copies are only for show and for working out how strong the Revenant is (see {@link #score}).
 */
public record Loadout(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, ItemStack mainHand, ItemStack offHand) {
	public static final Loadout EMPTY = new Loadout(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
			ItemStack.EMPTY);

	public static final Codec<Loadout> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("head", ItemStack.EMPTY).forGetter(Loadout::head),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("chest", ItemStack.EMPTY).forGetter(Loadout::chest),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("legs", ItemStack.EMPTY).forGetter(Loadout::legs),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("feet", ItemStack.EMPTY).forGetter(Loadout::feet),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("main_hand", ItemStack.EMPTY).forGetter(Loadout::mainHand),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("off_hand", ItemStack.EMPTY).forGetter(Loadout::offHand))
			.apply(i, Loadout::new));

	/** Full armor of 20 points counts as fully armored; a weapon of 10 damage as fully armed. */
	private static final float FULL_ARMOR = 20.0f;
	private static final float FULL_WEAPON = 10.0f;

	/** A snapshot of the player's gear right now (copies, so the hoard's own stacks are never shared). */
	public static Loadout of(Player player) {
		return new Loadout(player.getItemBySlot(EquipmentSlot.HEAD).copy(), player.getItemBySlot(EquipmentSlot.CHEST).copy(),
				player.getItemBySlot(EquipmentSlot.LEGS).copy(), player.getItemBySlot(EquipmentSlot.FEET).copy(),
				player.getMainHandItem().copy(), player.getOffhandItem().copy());
	}

	public ItemStack get(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> head;
			case CHEST -> chest;
			case LEGS -> legs;
			case FEET -> feet;
			case MAINHAND -> mainHand;
			case OFFHAND -> offHand;
			default -> ItemStack.EMPTY;
		};
	}

	public boolean isEmpty() {
		return head.isEmpty() && chest.isEmpty() && legs.isEmpty() && feet.isEmpty() && mainHand.isEmpty() && offHand.isEmpty();
	}

	/**
	 * This loadout with each slot kept from whichever of the two is stronger there. A second death adds to the same
	 * hoard; merging this way means dying again naked can't make the Revenant guarding the first death's gear weaker.
	 */
	public Loadout strongest(Loadout other) {
		return new Loadout(armorPick(head, other.head, EquipmentSlot.HEAD), armorPick(chest, other.chest, EquipmentSlot.CHEST),
				armorPick(legs, other.legs, EquipmentSlot.LEGS), armorPick(feet, other.feet, EquipmentSlot.FEET),
				weaponPower(other.mainHand) > weaponPower(mainHand) ? other.mainHand : mainHand,
				offHand.isEmpty() ? other.offHand : offHand);
	}

	private static ItemStack armorPick(ItemStack mine, ItemStack theirs, EquipmentSlot slot) {
		return armor(theirs, slot) > armor(mine, slot) ? theirs : mine;
	}

	/** The armor points a piece gives in its slot. */
	public static double armor(ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty()) {
			return 0.0;
		}
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0.0, slot);
	}

	public double totalArmor() {
		return armor(head, EquipmentSlot.HEAD) + armor(chest, EquipmentSlot.CHEST) + armor(legs, EquipmentSlot.LEGS)
				+ armor(feet, EquipmentSlot.FEET);
	}

	/** How hard a weapon hits: its melee damage, a gun's round, or a bow's arrow. Bare hands are 1. */
	public static double weaponPower(ItemStack stack) {
		if (stack.isEmpty()) {
			return 1.0;
		}
		double melee = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
		if (stack.getItem() instanceof GunItem gun && !gun.ammo().isEmpty()) {
			melee = Math.max(melee, gun.ammo().getFirst().damage());
		} else if (stack.getItem() instanceof CrossbowItem) {
			melee = Math.max(melee, 7.0);
		} else if (stack.getItem() instanceof BowItem) {
			melee = Math.max(melee, 6.0);
		}
		return melee;
	}

	/**
	 * How well equipped this was, 0 (naked and bare-handed) to 1 (full armor and a heavy weapon): armor counts for 60%,
	 * the weapon for 40%.
	 */
	public float score() {
		float armor = (float) Math.min(1.0, totalArmor() / FULL_ARMOR);
		float weapon = (float) Math.min(1.0, (weaponPower(mainHand) - 1.0) / (FULL_WEAPON - 1.0));
		return 0.6f * armor + 0.4f * Math.max(0.0f, weapon);
	}
}
