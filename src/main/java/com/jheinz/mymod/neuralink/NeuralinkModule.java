package com.jheinz.mymod.neuralink;

import net.minecraft.network.chat.Component;

/** What a module installed in a worn Neuralink does. Each module works once per Neuralink. */
public enum NeuralinkModule {
	/** Boxes around every mob within {@link #ESP_RANGE} blocks, seen through walls. */
	ESP("esp", 0xFF4D6A),
	/** Permanent night vision. */
	NIGHT_VISION("night_vision", 0x5CFF7A),
	/** Highlights ores within {@link #XRAY_RANGE} blocks, seen through walls. */
	XRAY("xray", 0xC77DFF);

	public static final double ESP_RANGE = 50.0;
	public static final double XRAY_RANGE = 50.0;

	private final String id;
	private final int color;

	NeuralinkModule(String id, int color) {
		this.id = id;
		this.color = color;
	}

	/** The module's accent colour (RGB) in the interface and its effects. */
	public int color() {
		return color;
	}

	public Component displayName() {
		return Component.translatable("neuralink_module.mymod." + id);
	}

	public Component description() {
		return Component.translatable("neuralink_module.mymod." + id + ".description");
	}
}
