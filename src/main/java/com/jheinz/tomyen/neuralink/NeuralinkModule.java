package com.jheinz.tomyen.neuralink;

import net.minecraft.network.chat.Component;

/**
 * What a module installed in a worn Neuralink does. Each module works once per Neuralink.
 *
 * <p>Passive modules work on their own. Active ones are fired with the Neural Trigger key: a one-shot fires on
 * a tap, a channelled one runs for as long as the key is held. Every active adds to the wearer's Neural Heat
 * (see {@link NeuralCore}): {@link #heat} per use for a one-shot, per tick for a channel.
 */
public enum NeuralinkModule {
	/** Boxes around every mob within {@link #ESP_RANGE} blocks, seen through walls. */
	ESP("esp", 0xFF4D6A),
	/** Permanent night vision. */
	NIGHT_VISION("night_vision", 0x5CFF7A),
	/** Highlights ores within {@link #XRAY_RANGE} blocks, seen through walls. */
	XRAY("xray", 0xC77DFF),
	/** Twin heat beams from the eyes: burns mobs, melts what it's held on. See {@link OpticLasers}. */
	OPTIC_LASERS("optic_lasers", 0xFF3B30, Kind.CHANNEL, 1.4f, 0),
	/** Lifts the looked-at mob or item, and throws it on release. See {@link Telekinesis}. */
	TELEKINESIS("telekinesis", 0xB388FF, Kind.CHANNEL, 0.3f, 0),
	/** A short teleport to where you're looking. See {@link Blink}. */
	BLINK("blink", 0x40C4FF, Kind.ONE_SHOT, 18.0f, 30),
	/** Stare a mob down to make it fight for you for a while. See {@link NeuralHijack}. */
	NEURAL_HIJACK("neural_hijack", 0xFF6EC7, Kind.CHANNEL, 0.6f, 200),
	/** A shockwave that stuns mobs and knocks out nearby Neuralinks. See {@link EmpPulse}. */
	EMP_PULSE("emp_pulse", 0xFFEA00, Kind.ONE_SHOT, 35.0f, 400),
	/** A few seconds of bullet time. See {@link Overclock}. */
	OVERCLOCK("overclock", 0xFF9100, Kind.ONE_SHOT, 55.0f, 600),
	/** Gun integration: ammo readout, hit markers and tighter hip fire. */
	SMART_LINK("smart_link", 0x18FFFF),
	/** What nearby mobs are thinking, over their heads. See {@link MindReader}. */
	MIND_READER("mind_reader", 0xF48FB1),
	/** Vitals readout, slower bleeding, and a warning when something out of sight is after you. */
	BIO_MONITOR("bio_monitor", 0x76FF03),
	/** Shares ESP contacts and position between wearers, and lets them see through each other's eyes. See {@link HiveMind}. */
	HIVE_MIND("hive_mind", 0xFFC23D),
	/** Waypoints to your last death and your respawn point. */
	RECALL("recall", 0x84FFFF);

	public static final double ESP_RANGE = 50.0;
	public static final double XRAY_RANGE = 50.0;

	public enum Kind {
		PASSIVE,
		/** Fires once per tap. */
		ONE_SHOT,
		/** Runs while the trigger is held. */
		CHANNEL
	}

	private final String id;
	private final int color;
	private final Kind kind;
	private final float heat;
	private final int cooldownTicks;

	NeuralinkModule(String id, int color) {
		this(id, color, Kind.PASSIVE, 0.0f, 0);
	}

	NeuralinkModule(String id, int color, Kind kind, float heat, int cooldownTicks) {
		this.id = id;
		this.color = color;
		this.kind = kind;
		this.heat = heat;
		this.cooldownTicks = cooldownTicks;
	}

	public String id() {
		return id;
	}

	/** The module's accent colour (RGB) in the interface and its effects. */
	public int color() {
		return color;
	}

	public Kind kind() {
		return kind;
	}

	public boolean isActive() {
		return kind != Kind.PASSIVE;
	}

	/** Heat added per use (one-shot) or per tick held (channel). */
	public float heat() {
		return heat;
	}

	/** Ticks before it can be fired again once used; 0 for none. */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	public Component displayName() {
		return Component.translatable("neuralink_module.tomyen." + id);
	}

	public Component description() {
		return Component.translatable("neuralink_module.tomyen." + id + ".description");
	}
}
