package com.jheinz.mymod.fishing;

import net.minecraft.network.chat.Component;

/** The four places on a rod a part goes, in the Tackle Bench's slot order. */
public enum PartSlot {
	LINE("line"),
	HOOK("hook"),
	REEL("reel"),
	LURE("lure");

	private final String name;

	PartSlot(String name) {
		this.name = name;
	}

	public String id() {
		return name;
	}

	public Component label() {
		return Component.translatable("tooltip.mymod.rod_slot." + name);
	}

	/** What the slot holds when nothing's fitted: plain string, a bent iron hook and so on. */
	public Component stock() {
		return Component.translatable("tooltip.mymod.rod_slot." + name + ".stock");
	}
}
