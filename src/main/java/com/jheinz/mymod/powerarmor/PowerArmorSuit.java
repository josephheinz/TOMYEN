package com.jheinz.mymod.powerarmor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A suit of power armor as a list of {@link #SIZE} stacks: the six pieces in {@link PowerArmorPiece} order, then
 * the fusion core. The same list is a station's rack, a worn suit ({@link PowerArmors#SUIT}) and a parked one.
 * Treated as immutable: every change makes a new suit, copying the stacks it touches.
 */
public record PowerArmorSuit(List<ItemStack> slots) {
	public static final int CORE = PowerArmorPiece.COUNT;
	public static final int SIZE = CORE + 1;
	public static final PowerArmorSuit EMPTY = new PowerArmorSuit(Collections.nCopies(SIZE, ItemStack.EMPTY));

	public static final Codec<PowerArmorSuit> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(PowerArmorSuit::of, PowerArmorSuit::slots);
	public static final StreamCodec<RegistryFriendlyByteBuf, PowerArmorSuit> STREAM_CODEC =
			ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(PowerArmorSuit::of, PowerArmorSuit::slots);

	/** A suit from any list of stacks, padded or cut to {@link #SIZE}. */
	public static PowerArmorSuit of(List<ItemStack> stacks) {
		List<ItemStack> slots = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			slots.add(i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
		}
		return new PowerArmorSuit(List.copyOf(slots));
	}

	public ItemStack get(int slot) {
		return slots.get(slot);
	}

	public ItemStack piece(PowerArmorPiece piece) {
		return slots.get(piece.slot());
	}

	public ItemStack core() {
		return slots.get(CORE);
	}

	/** A copy with one slot replaced. */
	public PowerArmorSuit with(int slot, ItemStack stack) {
		List<ItemStack> copy = new ArrayList<>(slots);
		copy.set(slot, stack.copy());
		return new PowerArmorSuit(List.copyOf(copy));
	}

	/** The mark of the piece in a slot, or null if the slot is empty. */
	public @Nullable PowerArmorTier tier(PowerArmorPiece piece) {
		return piece(piece).getItem() instanceof PowerArmorPieceItem item ? item.tier() : null;
	}

	/** How intact a piece is, 0 (broken or missing) to 1. */
	public float condition(PowerArmorPiece piece) {
		return PowerArmorPieceItem.condition(piece(piece));
	}

	/** A broken piece still hangs on the frame, but protects nothing and powers nothing. */
	public boolean works(PowerArmorPiece piece) {
		return condition(piece) > 0.0f;
	}

	/** All six pieces are on the rack. */
	public boolean complete() {
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			if (!(piece(piece).getItem() instanceof PowerArmorPieceItem)) {
				return false;
			}
		}
		return true;
	}

	/** Complete, with a fusion core (charged or not): ready to climb into. */
	public boolean ready() {
		return complete() && hasCore();
	}

	public boolean hasCore() {
		return core().getItem() instanceof FusionCoreItem;
	}

	public boolean isEmpty() {
		return slots.stream().allMatch(ItemStack::isEmpty);
	}

	/** The core's charge, 0 without a core. */
	public int charge() {
		return hasCore() ? FusionCoreItem.charge(core()) : 0;
	}

	public PowerArmorSuit withCharge(int charge) {
		if (!hasCore()) {
			return this;
		}
		ItemStack core = core().copy();
		FusionCoreItem.setCharge(core, charge);
		return with(CORE, core);
	}

	public PowerArmorStats stats() {
		return PowerArmorStats.of(this);
	}

	/** How the suit looks, for whoever draws it. */
	public SuitLook look() {
		return SuitLook.of(this);
	}

	/** Every non-empty stack in the suit, copied: what it drops when it's broken down. */
	public List<ItemStack> contents() {
		List<ItemStack> contents = new ArrayList<>();
		for (ItemStack stack : slots) {
			if (!stack.isEmpty()) {
				contents.add(stack.copy());
			}
		}
		return contents;
	}
}
