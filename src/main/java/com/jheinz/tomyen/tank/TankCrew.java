package com.jheinz.tomyen.tank;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a {@link Tank}'s crew is, on either side: the climb in through the commander's hatch and back out, the
 * gunner's seat (eyes at the gunner's sight), and where a collector stands in the open hatch.
 * <p>
 * Climbing in ({@link #ENTER_TICKS}): up the side of the hull from the ground onto the fender, onto the turret
 * roof by the hatch, the hatch swings open, down through it, and the hatch shuts over them as they settle at the
 * sight. Climbing out ({@link #EXIT_TICKS}) is the same in reverse, ending on the ground beside the hull on the
 * hatch's side. Points on the hull move with the hull; points on the turret turn with it.
 * <p>
 * Positions are in blocks, from the hull's or turret's own point of view: right, up (from the ground under
 * the tank, or from the turret ring) and forward.
 */
public final class TankCrew {
	public static final int ENTER_TICKS = 45;
	public static final int EXIT_TICKS = 30;
	/** Ticks the crew holds sneak to climb out. */
	public static final int EXIT_HOLD_TICKS = 20;

	/** The turret ring: on the hull roof, a little behind the middle. */
	static final double RING_UP = 26.0 / 16.0;
	static final double RING_FORWARD = -4.0 / 16.0;
	/** Standing on the turret roof by the commander's hatch (turret frame). */
	private static final Vec3 ON_ROOF = new Vec3(11.0 / 16.0, 14.0 / 16.0, -8.0 / 16.0);
	/** The gunner's sight on the turret roof, front right (turret frame). */
	private static final Vec3 SIGHT = new Vec3(11.5 / 16.0, 14.0 / 16.0, 20.0 / 16.0);
	/**
	 * The gunner's view (turret frame): on the face of the mantlet, just above the gun. The crew's own tank isn't
	 * drawn for them in the sight, so nothing of it gets in the way.
	 */
	private static final Vec3 SIGHT_CAMERA = new Vec3(0.0, 11.0 / 16.0, 38.5 / 16.0);
	/** How far below the roof a collector's feet are, standing in the hatch with its head and shoulders out. */
	private static final double HATCH_STANDING_DEPTH = 1.25;

	private TankCrew() {
	}

	// --- Frames -------------------------------------------------------------------------------------------

	/** A point in the hull's frame, in the world. */
	public static Vec3 hullPoint(Tank tank, double right, double up, double forward) {
		return framePoint(tank.position(), tank.getYRot(), right, up, forward);
	}

	/**
	 * A point in the turret's frame (up from the turret ring), in the world, with the hull and turret where they
	 * are {@code partialTicks} into the tick (1 for where they are now).
	 */
	public static Vec3 turretPoint(Tank tank, float partialTicks, double right, double up, double forward) {
		Vec3 ring = framePoint(tank.getPosition(partialTicks), tank.getViewYRot(partialTicks), 0.0, RING_UP, RING_FORWARD);
		return framePoint(ring, tank.turretYaw(partialTicks), right, up, forward);
	}

	/** Where the gunner's sight looks out from: the front of the turret, just above the gun. */
	public static Vec3 sightPoint(Tank tank, float partialTicks) {
		return turretPoint(tank, partialTicks, SIGHT_CAMERA.x, SIGHT_CAMERA.y, SIGHT_CAMERA.z);
	}

	/** {@code origin} plus an offset along a yaw's right, up and forward. */
	public static Vec3 framePoint(Vec3 origin, float yaw, double right, double up, double forward) {
		float radians = yaw * Mth.DEG_TO_RAD;
		double sin = Mth.sin(radians);
		double cos = Mth.cos(radians);
		// Forward is (-sin, cos); right is (-cos, -sin).
		return origin.add(-sin * forward - cos * right, up, cos * forward - sin * right);
	}

	/** Which side of the hull (1 right, -1 left) a point is on. */
	public static int sideOf(Tank tank, Vec3 point) {
		Vec3 offset = point.subtract(tank.position());
		float radians = tank.getYRot() * Mth.DEG_TO_RAD;
		double right = -Mth.cos(radians) * offset.x - Mth.sin(radians) * offset.z;
		return right >= 0.0 ? 1 : -1;
	}

	// --- Where the crew is ----------------------------------------------------------------------------------

	/** Where the gunner's feet go so that their eyes are at the sight. */
	public static Vec3 seat(Tank tank, Entity crew, float partialTicks) {
		return turretPoint(tank, partialTicks, SIGHT.x, SIGHT.y, SIGHT.z).subtract(0.0, crew.getEyeHeight(), 0.0);
	}

	/** Where a collector stands in the open hatch. */
	public static Vec3 inHatch(Tank tank, float partialTicks) {
		return turretPoint(tank, partialTicks, ON_ROOF.x, ON_ROOF.y - HATCH_STANDING_DEPTH, ON_ROOF.z);
	}

	/**
	 * Where a player crewing the tank is: on the climb in or out, or in the seat.
	 *
	 * @param side the side of the hull they climb (1 right, -1 left)
	 */
	public static Vec3 crewPosition(Tank tank, Entity crew, int sequence, float progress, int side, float partialTicks) {
		Vec3 seat = seat(tank, crew, partialTicks);
		if (sequence == Tank.SEQ_NONE) {
			return seat;
		}
		Vec3 ground = hullPoint(tank, side * 2.35, 0.0, -0.6);
		Vec3 fender = hullPoint(tank, side * 1.55, RING_UP, -0.6);
		Vec3 roof = turretPoint(tank, partialTicks, ON_ROOF.x, ON_ROOF.y, ON_ROOF.z);
		// Dropped down through the hatch: eyes at the roof.
		Vec3 inside = roof.subtract(0.0, crew.getEyeHeight() - 0.05, 0.0);
		if (sequence == Tank.SEQ_ENTERING) {
			if (progress < 0.3f) {
				return climb(ground, fender, progress / 0.3f, 0.4);
			}
			if (progress < 0.5f) {
				return climb(fender, roof, (progress - 0.3f) / 0.2f, 0.3);
			}
			if (progress < 0.62f) {
				return roof;
			}
			if (progress < 0.85f) {
				return roof.lerp(inside, smooth((progress - 0.62f) / 0.23f));
			}
			return inside.lerp(seat, smooth((progress - 0.85f) / 0.15f));
		}
		if (progress < 0.15f) {
			return seat.lerp(inside, smooth(progress / 0.15f));
		}
		if (progress < 0.45f) {
			return inside.lerp(roof, smooth((progress - 0.15f) / 0.3f));
		}
		if (progress < 0.7f) {
			return climb(roof, fender, (progress - 0.45f) / 0.25f, 0.3);
		}
		return climb(fender, ground, (progress - 0.7f) / 0.3f, 0.5);
	}

	/** From one point to the next with a little hop over the edge between them. */
	private static Vec3 climb(Vec3 from, Vec3 to, float t, double hop) {
		float eased = smooth(t);
		return from.lerp(to, eased).add(0.0, Math.sin(eased * Math.PI) * hop, 0.0);
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0f, 1.0f);
		return c * c * (3.0f - 2.0f * c);
	}

	/**
	 * How open the commander's hatch is, 0 to 1: during a climb in or out, or (with nobody climbing) open while a
	 * collector stands in it.
	 */
	public static float hatchOpen(int sequence, float progress, boolean collectorInHatch) {
		if (sequence == Tank.SEQ_ENTERING) {
			return progress < 0.45f ? 0.0f : progress < 0.6f ? smooth((progress - 0.45f) / 0.15f)
					: progress < 0.82f ? 1.0f : 1.0f - smooth((progress - 0.82f) / 0.13f);
		}
		if (sequence == Tank.SEQ_EXITING) {
			return progress < 0.2f ? smooth(progress / 0.2f) : progress < 0.8f ? 1.0f : 1.0f - smooth((progress - 0.8f) / 0.2f);
		}
		return collectorInHatch ? 1.0f : 0.0f;
	}
}
