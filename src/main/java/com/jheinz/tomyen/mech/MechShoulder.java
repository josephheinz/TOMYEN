package com.jheinz.tomyen.mech;

import net.minecraft.world.item.ItemStack;

/**
 * What a {@link Mech} carries on its back, above the power plant: a Cargo Pod (a 27-slot hold, which also feeds
 * the weapons) or a Smoke Launcher ({@link Mech#MAX_SMOKE} salvos, refilled from gunpowder in the stores), or
 * nothing.
 */
public enum MechShoulder {
	NONE("none"),
	CARGO_POD("cargo_pod"),
	SMOKE_LAUNCHER("smoke_launcher");

	private final String id;

	MechShoulder(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String nameKey() {
		return "hud.tomyen.mech.shoulder." + id;
	}

	public static MechShoulder byOrdinal(int ordinal) {
		MechShoulder[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}

	public static MechShoulder of(ItemStack stack) {
		return stack.is(Mechs.CARGO_POD) ? CARGO_POD : stack.is(Mechs.SMOKE_LAUNCHER) ? SMOKE_LAUNCHER : NONE;
	}
}
