package com.jheinz.mymod.starforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What the Star Forge would make of an item and a catalyst: the infusion, the sorts of gear the item is, and the
 * infusion it would lose. The forge works only on a {@link Status#ready ready} job; the screen shows the rest as a
 * hint. Client and server read the same slots, so they always agree.
 */
public record Infusing(Status status, @Nullable Infusion infusion, @Nullable Infusion previous, Set<InfusionKind> kinds) {
	/** A red and an amber that read on the screen's light grey and in a dark tooltip alike. */
	private static final int PROBLEM = 0xD03030;
	private static final int WARNING = 0xC06A00;

	public enum Status {
		EMPTY(false),
		NOT_GEAR(false),
		NO_CATALYST(false),
		NOT_A_CATALYST(false),
		/** The infusion doesn't take on this sort of gear (Drakehide on a sword). */
		WONT_TAKE(false),
		/** The item already carries this very infusion. */
		ALREADY(false),
		READY(true),
		/** Ready, but the item's infusion will be replaced. */
		REPLACES(true);

		public final boolean ready;

		Status(boolean ready) {
			this.ready = ready;
		}
	}

	public static Infusing of(ItemStack input, ItemStack catalyst) {
		if (input.isEmpty()) {
			return new Infusing(Status.EMPTY, Infusion.byCatalyst(catalyst), null, Set.of());
		}
		Set<InfusionKind> kinds = InfusionKind.of(input);
		Infusion previous = Infusion.of(input);
		if (kinds.isEmpty()) {
			return new Infusing(Status.NOT_GEAR, null, previous, kinds);
		}
		if (catalyst.isEmpty()) {
			return new Infusing(Status.NO_CATALYST, null, previous, kinds);
		}
		Infusion infusion = Infusion.byCatalyst(catalyst);
		if (infusion == null) {
			return new Infusing(Status.NOT_A_CATALYST, null, previous, kinds);
		}
		if (!infusion.fits(kinds)) {
			return new Infusing(Status.WONT_TAKE, infusion, previous, kinds);
		}
		if (previous == infusion) {
			return new Infusing(Status.ALREADY, infusion, previous, kinds);
		}
		return new Infusing(previous == null ? Status.READY : Status.REPLACES, infusion, previous, kinds);
	}

	public boolean ready() {
		return status.ready;
	}

	/** One short line for the screen: what will happen, or what's missing. */
	public Component summary() {
		return switch (status) {
			case EMPTY -> Component.translatable("container.mymod.star_forge.place_gear").withStyle(ChatFormatting.DARK_GRAY);
			case NOT_GEAR -> Component.translatable("container.mymod.star_forge.not_gear").withColor(PROBLEM);
			case NO_CATALYST -> Component.translatable("container.mymod.star_forge.add_catalyst").withStyle(ChatFormatting.DARK_GRAY);
			case NOT_A_CATALYST -> Component.translatable("container.mymod.star_forge.not_a_catalyst").withColor(PROBLEM);
			case WONT_TAKE -> Component.translatable("container.mymod.star_forge.wont_take", infusion.displayName()).withColor(PROBLEM);
			case ALREADY -> Component.translatable("container.mymod.star_forge.already", infusion.displayName()).withColor(WARNING);
			case READY, REPLACES -> infusion.title();
		};
	}

	/** The lines under the summary: the replacement warning, if any, and what the infusion will do on this gear. */
	public List<Component> details() {
		if (!ready()) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>();
		if (status == Status.REPLACES) {
			lines.add(Component.translatable("container.mymod.star_forge.replaces", previous.displayName()).withColor(WARNING));
		}
		for (Component effect : infusion.effects(kinds)) {
			lines.add(effect.copy().withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}
}
