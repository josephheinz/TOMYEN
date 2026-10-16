package com.jheinz.tomyen.mech;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are on a {@link Mech}: its two frames, the arms' elbows and muzzles, and the pilot's climb in and
 * out of the cockpit.
 * <p>
 * The legs frame turns with the legs (the entity's yaw), from the ground under it. The torso frame turns with
 * the torso's twist, from the waist ({@link #WAIST_UP} up). Positions are in blocks: right, up and forward.
 * <p>
 * Climbing in ({@link #ENTER_TICKS}): from the ground in front, up the rungs on the front of the left leg, onto
 * the hip ledge under the chest, the chest hatch swings down, in through it, and the hatch shuts as they settle
 * with their eyes at the viewport. Climbing out ({@link #EXIT_TICKS}) is the same in reverse. The torso is turned
 * square to the legs for both.
 */
public final class MechCrew {
	public static final int ENTER_TICKS = 50;
	public static final int EXIT_TICKS = 34;
	/** Ticks the pilot holds sneak to climb out. */
	public static final int EXIT_HOLD_TICKS = 20;

	/** The torso turns on the waist ring, this high. */
	public static final double WAIST_UP = 37.0 / 16.0;
	/** The pilot's eyes, at the viewport slit across the top of the chest (torso frame). */
	private static final Vec3 VIEWPORT = new Vec3(0.0, 61.5 / 16.0 - WAIST_UP, 9.0 / 16.0);
	/** The shoulder joint, under the pauldron (torso frame; left is negative right). */
	private static final double SHOULDER_OUT = 16.0 / 16.0;
	private static final double ELBOW_UP = 46.0 / 16.0 - WAIST_UP;
	/** The ladder up the front of the left leg, and the ledge on the hips in front of the hatch (legs frame). */
	private static final Vec3 GROUND = new Vec3(-0.5, 0.0, 2.1);
	private static final Vec3 LADDER_FOOT = new Vec3(-0.5, 0.0, 1.3);
	private static final Vec3 LADDER_TOP = new Vec3(-0.5, 1.4, 1.05);
	private static final Vec3 LEDGE = new Vec3(-0.15, 2.32, 0.78);
	/** Just inside the open hatch (torso frame). */
	private static final Vec3 IN_HATCH = new Vec3(0.0, 42.5 / 16.0 - WAIST_UP, 0.3);

	private MechCrew() {
	}

	// --- Frames -------------------------------------------------------------------------------------------

	/** {@code origin} plus an offset along a yaw's right, up and forward. */
	public static Vec3 framePoint(Vec3 origin, float yaw, double right, double up, double forward) {
		float radians = yaw * Mth.DEG_TO_RAD;
		double sin = Mth.sin(radians);
		double cos = Mth.cos(radians);
		// Forward is (-sin, cos); right is (-cos, -sin).
		return origin.add(-sin * forward - cos * right, up, cos * forward - sin * right);
	}

	/** A point in the legs' frame, in the world. */
	public static Vec3 legsPoint(Mech mech, float partialTicks, double right, double up, double forward) {
		return framePoint(mech.getPosition(partialTicks), mech.getViewYRot(partialTicks), right, up, forward);
	}

	/** A point in the torso's frame (up from the waist), in the world. */
	public static Vec3 torsoPoint(Mech mech, float partialTicks, double right, double up, double forward) {
		return framePoint(mech.getPosition(partialTicks).add(0.0, WAIST_UP, 0.0), mech.torsoYaw(partialTicks), right, up, forward);
	}

	/** The pilot's eyes: the cockpit viewport. */
	public static Vec3 viewport(Mech mech, float partialTicks) {
		return torsoPoint(mech, partialTicks, VIEWPORT.x, VIEWPORT.y, VIEWPORT.z);
	}

	/** Which way the arms point: the torso's twist and the arms' pitch. */
	public static Vec3 aim(Mech mech, float partialTicks) {
		return Vec3.directionFromRotation(mech.armPitch(partialTicks), mech.torsoYaw(partialTicks));
	}

	/** An arm's elbow, where its weapon pivots. */
	public static Vec3 elbow(Mech mech, int side, float partialTicks) {
		return torsoPoint(mech, partialTicks, (side == MechArm.LEFT ? -1 : 1) * SHOULDER_OUT, ELBOW_UP, 0.0);
	}

	/** The end of an arm's weapon: its muzzle, nozzle or the fist's knuckles. */
	public static Vec3 muzzle(Mech mech, int side, float partialTicks) {
		MechWeapon weapon = mech.weapon(side);
		return elbow(mech, side, partialTicks).add(aim(mech, partialTicks).scale(Math.max(10.0f, weapon.reach()) / 16.0));
	}

	/** The most an arm's shot bends in from straight ahead to meet the sight line, in degrees. */
	private static final float MAX_CONVERGE = 15.0f;
	private static final double MIN_CONVERGE_COS = Math.cos(Math.toRadians(MAX_CONVERGE));

	/**
	 * Which way an arm's shot leaves its muzzle: at the aim point if that's nearly straight down the barrel, else straight
	 * down the barrel. Something closer than the muzzle reaches (or off to the side of it) never sends a round sideways
	 * or backwards out of the gun.
	 */
	public static Vec3 fireDirection(Mech mech, int side, Vec3 aimPoint, float partialTicks) {
		Vec3 barrel = aim(mech, partialTicks);
		Vec3 toAim = aimPoint.subtract(muzzle(mech, side, partialTicks));
		double length = toAim.length();
		if (length < 2.5) {
			return barrel;
		}
		Vec3 direction = toAim.scale(1.0 / length);
		return direction.dot(barrel) >= MIN_CONVERGE_COS ? direction : barrel;
	}

	// --- The pilot ------------------------------------------------------------------------------------------

	/** Where the pilot's feet go so their eyes are at the viewport. */
	public static Vec3 seat(Mech mech, Entity pilot, float partialTicks) {
		return viewport(mech, partialTicks).subtract(0.0, pilot.getEyeHeight(), 0.0);
	}

	/** Where a pilot climbing in or out (or seated) is. */
	public static Vec3 pilotPosition(Mech mech, Entity pilot, int sequence, float progress, float partialTicks) {
		Vec3 seat = seat(mech, pilot, partialTicks);
		if (sequence == Mech.SEQ_NONE) {
			return seat;
		}
		Vec3 ground = legs(mech, partialTicks, GROUND);
		Vec3 foot = legs(mech, partialTicks, LADDER_FOOT);
		Vec3 top = legs(mech, partialTicks, LADDER_TOP);
		Vec3 ledge = legs(mech, partialTicks, LEDGE);
		Vec3 hatch = torsoPoint(mech, partialTicks, IN_HATCH.x, IN_HATCH.y, IN_HATCH.z);
		if (sequence == Mech.SEQ_ENTERING) {
			if (progress < 0.1f) {
				return ground.lerp(foot, smooth(progress / 0.1f));
			}
			if (progress < 0.42f) {
				return rungs(foot, top, (progress - 0.1f) / 0.32f);
			}
			if (progress < 0.56f) {
				return climb(top, ledge, (progress - 0.42f) / 0.14f, 0.35);
			}
			if (progress < 0.68f) {
				return ledge;
			}
			if (progress < 0.84f) {
				return climb(ledge, hatch, (progress - 0.68f) / 0.16f, 0.25);
			}
			return hatch.lerp(seat, smooth((progress - 0.84f) / 0.16f));
		}
		if (progress < 0.15f) {
			return seat.lerp(hatch, smooth(progress / 0.15f));
		}
		if (progress < 0.32f) {
			return climb(hatch, ledge, (progress - 0.15f) / 0.17f, 0.2);
		}
		if (progress < 0.45f) {
			return climb(ledge, top, (progress - 0.32f) / 0.13f, 0.2);
		}
		if (progress < 0.85f) {
			return rungs(top, foot, (progress - 0.45f) / 0.4f);
		}
		return foot.lerp(ground, smooth((progress - 0.85f) / 0.15f));
	}

	private static Vec3 legs(Mech mech, float partialTicks, Vec3 at) {
		return legsPoint(mech, partialTicks, at.x, at.y, at.z);
	}

	/** Up or down a ladder a rung at a time: a pull, then a pause as the feet find the next rung. */
	private static Vec3 rungs(Vec3 from, Vec3 to, float t) {
		float steps = 4.0f;
		float stepped = Mth.clamp(t, 0.0f, 1.0f) * steps;
		int rung = Math.min((int) stepped, (int) steps - 1);
		float within = Math.min(1.0f, (stepped - rung) * 1.5f);
		return from.lerp(to, (rung + smooth(within)) / steps);
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

	/** How far the chest hatch has swung down, 0 (shut) to 1 (open), through a climb in or out. */
	public static float hatchOpen(int sequence, float progress) {
		if (sequence == Mech.SEQ_ENTERING) {
			return progress < 0.5f ? 0.0f : progress < 0.64f ? smooth((progress - 0.5f) / 0.14f)
					: progress < 0.86f ? 1.0f : 1.0f - smooth((progress - 0.86f) / 0.12f);
		}
		if (sequence == Mech.SEQ_EXITING) {
			return progress < 0.14f ? smooth(progress / 0.14f) : progress < 0.42f ? 1.0f : 1.0f - smooth((progress - 0.42f) / 0.16f);
		}
		return 0.0f;
	}

	/** Where something held in an arm's fist hangs: centred on the open hand. */
	public static Vec3 inFist(Mech mech, int side, Entity held, float partialTicks) {
		return muzzle(mech, side, partialTicks).add(aim(mech, partialTicks).scale(0.4)).subtract(0.0, held.getBbHeight() * 0.5, 0.0);
	}
}
