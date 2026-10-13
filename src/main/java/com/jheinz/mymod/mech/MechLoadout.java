package com.jheinz.mymod.mech;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.powerarmor.FusionCoreItem;

/**
 * What a walker is built from: nine slots, the same in a {@link MechGantryBlockEntity} and on a {@link Mech}. The
 * five core parts (torso, legs, two arms and the back's power plant) make it walk; the Fusion Core powers it; the
 * hardpoints and shoulder are optional.
 * <p>
 * For drawing, a loadout is boiled down to a "look": one int saying which core parts are fitted, what's on each
 * hardpoint and the shoulder, and whether there's a charged core, so it can sync as a single value.
 */
public final class MechLoadout {
	public static final int TORSO = 0;
	public static final int LEGS = 1;
	public static final int LEFT_ARM = 2;
	public static final int RIGHT_ARM = 3;
	public static final int POWER_PLANT = 4;
	public static final int CORE = 5;
	public static final int LEFT_WEAPON = 6;
	public static final int RIGHT_WEAPON = 7;
	public static final int SHOULDER = 8;
	public static final int SIZE = 9;

	private MechLoadout() {
	}

	/** Whether a stack belongs in a slot. Either arm frame fits either arm. */
	public static boolean fits(int slot, ItemStack stack) {
		return switch (slot) {
			case TORSO -> stack.is(Mechs.TORSO);
			case LEGS -> stack.is(Mechs.LEGS);
			case LEFT_ARM, RIGHT_ARM -> stack.is(Mechs.ARM);
			case POWER_PLANT -> stack.is(Mechs.POWER_PLANT);
			case CORE -> stack.getItem() instanceof FusionCoreItem;
			case LEFT_WEAPON, RIGHT_WEAPON -> MechWeapon.of(stack) != MechWeapon.NONE;
			case SHOULDER -> MechShoulder.of(stack) != MechShoulder.NONE;
			default -> false;
		};
	}

	/** Whether the five core parts are all fitted: enough to walk out. */
	public static boolean complete(List<ItemStack> items) {
		for (int slot = TORSO; slot <= POWER_PLANT; slot++) {
			if (items.get(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static boolean empty(List<ItemStack> items) {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	// --- Looks -------------------------------------------------------------------------------------------------

	/** Bits 0-4 the core parts fitted, 5-7 and 8-10 the left and right weapons, 11-12 the shoulder, 13 a charged core. */
	public static int look(List<ItemStack> items) {
		int look = 0;
		for (int slot = TORSO; slot <= POWER_PLANT; slot++) {
			if (!items.get(slot).isEmpty()) {
				look |= 1 << slot;
			}
		}
		look |= MechWeapon.of(items.get(LEFT_WEAPON)).ordinal() << 5;
		look |= MechWeapon.of(items.get(RIGHT_WEAPON)).ordinal() << 8;
		look |= MechShoulder.of(items.get(SHOULDER)).ordinal() << 11;
		ItemStack core = items.get(CORE);
		if (core.getItem() instanceof FusionCoreItem && FusionCoreItem.charge(core) > 0) {
			look |= 1 << 13;
		}
		return look;
	}

	/** Whether a look has a core part (slot {@link #TORSO} to {@link #POWER_PLANT}) fitted. */
	public static boolean has(int look, int slot) {
		return (look & 1 << slot) != 0;
	}

	public static MechWeapon weapon(int look, int side) {
		return MechWeapon.byOrdinal(look >> (side == MechArm.LEFT ? 5 : 8) & 7);
	}

	public static MechShoulder shoulder(int look) {
		return MechShoulder.byOrdinal(look >> 11 & 3);
	}

	public static boolean coreLit(int look) {
		return (look & 1 << 13) != 0;
	}

	public static boolean complete(int look) {
		return (look & 0b11111) == 0b11111;
	}
}
