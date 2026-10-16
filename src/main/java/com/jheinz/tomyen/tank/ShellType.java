package com.jheinz.tomyen.tank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

/**
 * What a tank's 120mm gun fires, and how each round flies as a {@link TankShell}: muzzle speed (blocks a tick),
 * gravity and drag (applied the way a thrown projectile applies them), the damage of a direct hit and the power
 * of the burst. {@link #elevationFor} works out how far above the line of sight the gun has to point to hit at
 * a range, which the fire control applies after the gunner lases a target, and which the sight's range ladder
 * marks out.
 * <p>
 * New rounds go on the end: tanks save and sync rounds by ordinal.
 */
public enum ShellType {
	/** Sabot: a hard-tipped steel dart that flies fast and almost flat and hits very hard, with only a small burst. */
	APFSDS("apfsds", 7.0, 0.012, 0.998f, 90.0f, 0.8f, false),
	/** High explosive: slower and loopier, a modest hit but a big blast that breaks blocks (unless mobGriefing is off). */
	HE("he", 3.2, 0.03, 0.99f, 25.0f, 3.5f, true),
	/** What the bank's tanks fire: a light shell whose blast hurts and knocks back but leaves the blocks alone. */
	COLLECTOR("collector", 1.6, 0.03, 0.99f, 0.0f, 1.2f, false),
	/** Tungsten sabot: a denser dart, a little faster and flatter, a third harder-hitting than steel. */
	TUNGSTEN_APFSDS("tungsten_apfsds", 7.6, 0.011, 0.998f, 120.0f, 0.9f, false),
	/**
	 * Depleted uranium sabot: the strongest round. The dart sharpens itself as it goes through and burns on
	 * impact, a small incendiary flash that sets whatever is close to the strike alight (see {@link TankShell}).
	 */
	DU_APFSDS("du_apfsds", 7.8, 0.011, 0.998f, 150.0f, 1.2f, false);

	/** The order the gunner's round selector steps through the loadable rounds in. */
	public static final List<ShellType> SELECTABLE = List.of(APFSDS, TUNGSTEN_APFSDS, DU_APFSDS, HE);

	/** The highest the fire control will raise the gun above the sight line, in degrees. */
	public static final float MAX_ELEVATION = 25.0f;
	private static final int MAX_FLIGHT_TICKS = 400;

	private final String id;
	private final double speed;
	private final double gravity;
	private final float drag;
	private final float damage;
	private final float blast;
	private final boolean breaksBlocks;
	private final Map<Integer, Float> elevations = new HashMap<>();

	ShellType(String id, double speed, double gravity, float drag, float damage, float blast, boolean breaksBlocks) {
		this.id = id;
		this.speed = speed;
		this.gravity = gravity;
		this.drag = drag;
		this.damage = damage;
		this.blast = blast;
		this.breaksBlocks = breaksBlocks;
	}

	public String id() {
		return id;
	}

	public double speed() {
		return speed;
	}

	public double gravity() {
		return gravity;
	}

	public float drag() {
		return drag;
	}

	public float damage() {
		return damage;
	}

	public float blast() {
		return blast;
	}

	public boolean breaksBlocks() {
		return breaksBlocks;
	}

	/** A sabot round: a dart rather than a shell, with a bright tracer and a puff of sparks where it strikes. */
	public boolean sabot() {
		return this == APFSDS || this == TUNGSTEN_APFSDS || this == DU_APFSDS;
	}

	/** Burns where it strikes: depleted uranium is pyrophoric. */
	public boolean incendiary() {
		return this == DU_APFSDS;
	}

	/** The round a player loads; the collectors' shell is only ever fired by the bank's tanks. */
	public boolean loadable() {
		return this != COLLECTOR;
	}

	/** "hud.tomyen.tank.round.apfsds" and so on: the short name the crew readouts use. */
	public String shortNameKey() {
		return "hud.tomyen.tank.round." + id;
	}

	public Item item() {
		return switch (this) {
			case APFSDS -> Tanks.APFSDS_SHELL;
			case HE -> Tanks.HE_SHELL;
			case COLLECTOR -> Tanks.TANK_SHELL;
			case TUNGSTEN_APFSDS -> Tanks.TUNGSTEN_APFSDS_SHELL;
			case DU_APFSDS -> Tanks.DU_APFSDS_SHELL;
		};
	}

	public static ShellType byOrdinal(int ordinal) {
		ShellType[] values = values();
		return values[Mth.clamp(ordinal, 0, values.length - 1)];
	}

	/** The loadable round this item is, or null. */
	public static ShellType of(Item item) {
		for (ShellType type : values()) {
			if (type.loadable() && type.item() == item) {
				return type;
			}
		}
		return null;
	}

	/**
	 * Degrees above the line of sight to fire at for the round to come back down to the muzzle's height
	 * {@code range} blocks away, or {@link #MAX_ELEVATION} if it can't reach that far. Cached by the block.
	 */
	public float elevationFor(double range) {
		if (range <= 1.0) {
			return 0.0f;
		}
		int key = (int) Math.round(range);
		synchronized (elevations) {
			Float cached = elevations.get(key);
			if (cached != null) {
				return cached;
			}
		}
		float low = 0.0f;
		float high = MAX_ELEVATION;
		float result;
		if (heightAt(high, key) < 0.0) {
			result = MAX_ELEVATION;
		} else {
			for (int i = 0; i < 24; i++) {
				float mid = (low + high) / 2.0f;
				if (heightAt(mid, key) < 0.0) {
					low = mid;
				} else {
					high = mid;
				}
			}
			result = high;
		}
		synchronized (elevations) {
			elevations.put(key, result);
		}
		return result;
	}

	/** How high (relative to the muzzle) a round fired {@code degrees} up is when it has gone {@code range} blocks. */
	private double heightAt(float degrees, double range) {
		double radians = Math.toRadians(degrees);
		double vx = speed * Math.cos(radians);
		double vy = speed * Math.sin(radians);
		double x = 0.0;
		double y = 0.0;
		for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
			// The same order a thrown projectile ticks in: gravity, then drag, then the move.
			vy -= gravity;
			vx *= drag;
			vy *= drag;
			double nextX = x + vx;
			double nextY = y + vy;
			if (nextX >= range) {
				return Mth.lerp((range - x) / (nextX - x), y, nextY);
			}
			x = nextX;
			y = nextY;
		}
		return Double.NEGATIVE_INFINITY;
	}
}
