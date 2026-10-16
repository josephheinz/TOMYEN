package com.jheinz.tomyen.hogfire;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fae.Fae;

/**
 * How a Hogfire flies: an arcade plane, simulated on the server one tick at a time. Speeds are in blocks a tick
 * (×20 for m/s); angles in degrees, with Minecraft's pitch (positive is nose down).
 * <ul>
 * <li>Throttle: forward raises it by {@value #THROTTLE_RATE} a tick (empty to full in 2 s), back lowers it half as
 * fast again. The rocket pushes {@value #THRUST} at full throttle against drag that grows with the square of the
 * speed: full throttle cruises at 1.5 (30 m/s, 108 km/h). The afterburner (jump, at any throttle) adds
 * {@value #AFTERBURNER_THRUST}: 2.2 (44 m/s).
 * <li>Steering: the pig turns and pitches towards where the pilot looks, at up to {@value #TURN_RATE} and
 * {@value #PITCH_RATE} degrees a tick, banking into turns. Climbing trades speed for height, diving the reverse.
 * <li>Lift: at {@value #STALL_SPEED} (9 m/s) and up it holds its height. Slower, it sinks (up to {@value #SINK} a
 * tick at a standstill) and its nose drops, more the slower it goes, until it has the speed to fly again.
 * <li>Fuel: full throttle burns 1 unit a tick and the afterburner 4 more, so a canister ({@value #FUEL_PER_CANISTER}
 * units) gives 3 minutes at full throttle, 5 at 60%, or 36 s of afterburner. With the tank dry it glides,
 * nose a little down, sinking about 2 m/s.
 * <li>Ground: it walks at {@value #WALK_SPEED} with forward held, steered by the pilot's look, and backs up slowly
 * with back held at zero throttle. With throttle up it rolls faster and faster: look up once it's past
 * {@value #TAKEOFF_SPEED} and it lifts off, about five blocks from a standing start. Back brakes on the ground.
 * <li>Crashing: flying into a wall faster than {@value #CRASH_SPEED} (12 m/s), or hitting the ground sinking faster
 * than {@value #CRASH_SINK} (10 m/s), hurts pig and pilot (4 hearts and up, more the faster) and throws the pilot
 * off.
 * <li>Fae Dust: while it's fae-light ({@link Hogfire#faeLight}) it burns 60% of the usual fuel ({@value #FAE_BURN}) and
 * holds its height down to {@value #FAE_STALL_SPEED} (7.6 m/s), trailing a faint sparkle.
 * </ul>
 */
public final class HogfireFlight {
	public static final int FUEL_PER_CANISTER = 3600;
	public static final int MAX_FUEL = FUEL_PER_CANISTER * 2;
	public static final float THROTTLE_RATE = 0.025f;
	public static final float THRUST = 0.04f;
	public static final float AFTERBURNER_THRUST = 0.045f;
	public static final float AFTERBURNER_BURN = 4.0f;
	/** Drag per (block a tick) squared: what makes full throttle top out at 1.5. */
	public static final float DRAG = 0.0178f;
	/** Gravity along the flight path: how much a climb slows it, or a dive speeds it up. */
	public static final float GRAVITY_ALONG = 0.04f;
	public static final float STALL_SPEED = 0.45f;
	public static final float FAE_STALL_SPEED = 0.38f;
	public static final float FAE_BURN = 0.6f;
	public static final float TAKEOFF_SPEED = 0.4f;
	public static final float SINK = 0.12f;
	/** Where the nose falls to in a full stall. */
	public static final float STALL_NOSE_DOWN = 25.0f;
	public static final float TURN_RATE = 3.5f;
	public static final float GROUND_TURN_RATE = 10.0f;
	public static final float PITCH_RATE = 2.5f;
	public static final float MAX_CLIMB = -45.0f;
	public static final float MAX_DIVE = 60.0f;
	/** Degrees of bank per degree a tick of turn. */
	public static final float BANK_PER_TURN = 14.0f;
	public static final float MAX_BANK = 65.0f;
	public static final float WALK_SPEED = 0.2f;
	public static final float ROLLING_DRAG = 0.004f;
	public static final float BRAKE = 0.04f;
	public static final float CRASH_SPEED = 0.6f;
	public static final float CRASH_SINK = 0.5f;
	/** Ticks the server keeps the pilot's last controls before treating them as let go. */
	public static final int CONTROLS_TIMEOUT = 10;
	/** Height of the middle of its body, which it pitches about. */
	public static final double BODY_CENTRE = 0.6;
	/** Contrails stream from the wingtips above this speed. */
	public static final float CONTRAIL_SPEED = 1.3f;

	private HogfireFlight() {
	}

	/** The speed below which it can't hold its height: a little lower while it's fae-light. */
	public static float stallSpeed(Hogfire pig) {
		return pig.faeLight() ? FAE_STALL_SPEED : STALL_SPEED;
	}

	/** How much of the usual fuel it burns: less while it's fae-light. */
	public static float burnFactor(Hogfire pig) {
		return pig.faeLight() ? FAE_BURN : 1.0f;
	}

	/** One tick of flight, on the server. */
	static void fly(Hogfire pig, Player pilot) {
		Hogfires.Controls controls = pig.controls();
		RandomSource random = pig.getRandom();
		boolean grounded = pig.onGround() || pig.isInWater();

		// Throttle and fuel.
		float throttle = pig.throttle();
		if (controls.throttleUp()) {
			throttle += THROTTLE_RATE;
		}
		if (controls.throttleDown()) {
			throttle -= THROTTLE_RATE * 1.5f;
		}
		pig.setThrottle(throttle);
		throttle = pig.throttle();
		boolean fuelled = pig.fuel() > 0;
		boolean afterburner = controls.afterburner() && fuelled;
		if (afterburner && !pig.afterburner()) {
			pig.playSound(SoundEvents.FIRECHARGE_USE, 1.5f, 0.6f);
			pig.playSound(pig.squealSound(), 1.5f, 2.0f);
		}
		pig.setAfterburner(afterburner);
		float thrust = 0.0f;
		if (fuelled) {
			thrust = throttle * THRUST + (afterburner ? AFTERBURNER_THRUST : 0.0f);
			pig.burn((throttle + (afterburner ? AFTERBURNER_BURN : 0.0f)) * burnFactor(pig));
		}

		// Steering.
		float speed = pig.airspeed();
		float stall = stallSpeed(pig);
		float lift = Mth.clamp(speed / stall, 0.0f, 1.0f);
		float yaw = pig.getYRot();
		float pitch = pig.getXRot();
		float wantedPitch = Mth.clamp(controls.pitch(), MAX_CLIMB, MAX_DIVE);
		float turn;
		if (grounded) {
			turn = Mth.clamp(Mth.wrapDegrees(controls.yaw() - yaw), -GROUND_TURN_RATE, GROUND_TURN_RATE);
			// It rotates off the ground once it's fast enough and the pilot looks up.
			pitch = speed >= TAKEOFF_SPEED && wantedPitch < -5.0f ? Math.max(wantedPitch, -20.0f) : 0.0f;
			pig.setBank(Mth.approach(pig.bank(), 0.0f, 6.0f));
		} else {
			turn = Mth.clamp(Mth.wrapDegrees(controls.yaw() - yaw), -TURN_RATE, TURN_RATE);
			// Stalling, the pilot loses the elevator and the nose falls through.
			float target = Mth.lerp(1.0f - lift, wantedPitch, STALL_NOSE_DOWN);
			pitch += Mth.clamp(target - pitch, -PITCH_RATE, PITCH_RATE);
			pig.setBank(Mth.approach(pig.bank(), Mth.clamp(turn * BANK_PER_TURN, -MAX_BANK, MAX_BANK), 5.0f));
		}
		yaw += turn;

		// Speed along the flight path.
		float climb = -pitch * Mth.DEG_TO_RAD;
		speed += thrust - DRAG * speed * speed - GRAVITY_ALONG * Mth.sin(climb);
		if (grounded) {
			speed -= ROLLING_DRAG;
			if (controls.throttleDown()) {
				speed -= BRAKE;
			}
			if (pig.isInWater()) {
				speed *= 0.9f;
			}
		}
		speed = Math.max(0.0f, speed);
		lift = Mth.clamp(speed / stall, 0.0f, 1.0f);

		Vec3 heading = Vec3.directionFromRotation(pitch, yaw);
		Vec3 velocity = heading.scale(speed);
		if (grounded) {
			Vec3 flat = Vec3.directionFromRotation(0.0f, yaw);
			if (speed < WALK_SPEED && controls.throttleUp()) {
				velocity = flat.scale(WALK_SPEED);
			} else if (speed < 0.01f && controls.throttleDown() && throttle <= 0.0f) {
				velocity = flat.scale(-WALK_SPEED * 0.5);
			}
			// Hold it to the ground unless it's rotating off.
			velocity = new Vec3(velocity.x, velocity.y > 0.0 ? velocity.y : -0.08, velocity.z);
		} else {
			velocity = velocity.add(0.0, -SINK * (1.0f - lift * lift), 0.0);
		}

		pig.move(MoverType.SELF, velocity);
		pig.setDeltaMovement(velocity);
		pig.resetFallDistance();
		pig.setYRot(yaw);
		pig.setXRot(pitch);
		pig.yBodyRot = yaw;
		pig.yHeadRot = yaw;
		pig.setAirspeed(speed);

		if (pig.level() instanceof ServerLevel level) {
			if (pig.horizontalCollision && speed > CRASH_SPEED) {
				crash(level, pig, pilot, speed - CRASH_SPEED);
				return;
			}
			if (pig.verticalCollisionBelow && -velocity.y > CRASH_SINK) {
				crash(level, pig, pilot, (float) -velocity.y - CRASH_SINK);
				return;
			}
			if (pig.horizontalCollision) {
				// A gentle bump just stops it.
				pig.setAirspeed(Math.min(speed, (float) pig.getDeltaMovement().horizontalDistance()));
			}
			squeal(pig, random, afterburner, !grounded && lift < 0.8f);
		}
	}

	/** Pigs are not built for this, and they let you know. */
	private static void squeal(Hogfire pig, RandomSource random, boolean afterburner, boolean stalling) {
		int odds = stalling ? 8 : afterburner ? 12 : pig.onGround() ? 120 : 45;
		if (random.nextInt(odds) == 0) {
			pig.playSound(pig.squealSound(), 1.4f, 1.5f + random.nextFloat() * 0.6f);
		}
	}

	/** Slammed into something: both get hurt, the pilot goes flying, and the engine cuts out. */
	private static void crash(ServerLevel level, Hogfire pig, Player pilot, float excess) {
		float damage = 8.0f + excess * 20.0f;
		pig.ejectPassengers();
		pig.setThrottle(0.0f);
		pig.setAfterburner(false);
		pig.setAirspeed(0.0f);
		pig.setDeltaMovement(Vec3.ZERO);
		pig.hurtServer(level, pig.damageSources().flyIntoWall(), damage);
		pilot.hurtServer(level, pilot.damageSources().flyIntoWall(), damage * 0.75f);
		pilot.setDeltaMovement(pilot.getDeltaMovement().add(0.0, 0.5, 0.0));
		pilot.needsSync = true;
		level.sendParticles(ParticleTypes.EXPLOSION, pig.getX(), pig.getY() + 0.5, pig.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pig.getX(), pig.getY() + 0.6, pig.getZ(), 20, 0.4, 0.3, 0.4, 0.05);
		pig.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.3f);
		pig.playSound(pig.squealSound(), 2.0f, 0.8f);
	}

	// --- Effects, on the client --------------------------------------------------------------------------------

	/**
	 * A point on the pig, given right of, above and forward of its feet, turned with its yaw and pitched about the
	 * middle of its body (as HogfireRenderer draws it).
	 */
	public static Vec3 point(Hogfire pig, double right, double up, double forward) {
		return new Vec3(-right, up - BODY_CENTRE, forward)
				.xRot(-pig.getXRot() * Mth.DEG_TO_RAD)
				.yRot(-pig.getYRot() * Mth.DEG_TO_RAD)
				.add(pig.getX(), pig.getY() + BODY_CENTRE, pig.getZ());
	}

	/** Flame and smoke from the nozzle, contrails off the wingtips at speed, and a fae-light pig's sparkle. */
	static void effects(Hogfire pig) {
		Level level = pig.level();
		RandomSource random = pig.getRandom();
		if (pig.faeLight() && random.nextInt(pig.onGround() ? 4 : 1) == 0) {
			// Shed off the back and the wings, hanging where they fall, so in flight they stream out behind.
			double span = pig.hasWings() ? 2.4 : 0.8;
			Vec3 at = point(pig, (random.nextDouble() - 0.5) * span, 0.6 + random.nextDouble() * 0.4, -0.3 - random.nextDouble() * 0.5);
			level.addParticle(Fae.SPARKLE, at.x, at.y, at.z, 0.0, -0.01, 0.0);
		}
		if (pig.engineRunning()) {
			Vec3 nozzle = point(pig, 0.0, 1.09, -0.95);
			// Shot out backwards and left hanging in the air, so they trail behind it.
			Vec3 back = Vec3.directionFromRotation(pig.getXRot(), pig.getYRot()).scale(-0.25);
			int flames = pig.afterburner() ? 4 : 1 + (int) (pig.throttle() * 2.0f);
			for (int i = 0; i < flames; i++) {
				double spread = 0.04;
				level.addParticle(pig.afterburner() && i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
						nozzle.x, nozzle.y, nozzle.z,
						back.x + random.nextGaussian() * spread, back.y + random.nextGaussian() * spread, back.z + random.nextGaussian() * spread);
			}
			if (random.nextFloat() < 0.3f + pig.throttle() * 0.5f) {
				level.addParticle(pig.throttle() > 0.6f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
						nozzle.x - back.x, nozzle.y - back.y, nozzle.z - back.z, back.x * 0.3, 0.02, back.z * 0.3);
			}
		}
		if (pig.airspeed() > CONTRAIL_SPEED && pig.hasWings()) {
			for (int side = -1; side <= 1; side += 2) {
				Vec3 tip = point(pig, side * 1.3, 0.78, -0.2);
				level.addParticle(ParticleTypes.WHITE_SMOKE, tip.x, tip.y, tip.z, 0.0, 0.0, 0.0);
			}
		}
	}
}
