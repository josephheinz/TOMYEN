package com.jheinz.tomyen.napalm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Burning napalm in flight from flamethrowers, simulated on the server as points rather than entities. Each tick a
 * spray throws {@link #GLOBS_PER_TICK} globs off the nozzle at {@link #SPEED} blocks a tick, scattered within the
 * gun's spread and converging on the line of sight {@link #CONVERGE} blocks out. A glob falls ({@link #GRAVITY}) and
 * slows ({@link #DRAG}) each tick, so the stream arcs: fired level it comes down about 10 blocks out, aimed a little
 * up it reaches about 14, and it burns out after {@link #LIFETIME} ticks wherever it is.
 *
 * <p>Each tick a glob sweeps the step it moves, swelling as it goes. The first thing in its way is scorched and the
 * glob is spent: damage falls from {@link #MAX_DAMAGE} at the nozzle to {@link #MIN_DAMAGE} at the end of its flight,
 * with a burn ({@link NapalmFire#scorch}) that's longer the closer it was. Ordinary hurt cooldown applies, so a target
 * soaking the stream point blank takes about 12 a second plus the burn; at the far end, 2. A block catches a glob and
 * may take a gel puddle or fire ({@link NapalmFire#land}); water puts it out. Clients draw the stream with particles
 * following the same physics (NapalmClient), so the two agree without a packet per glob. Not saved.
 */
public final class FlameStream {
	public static final double SPEED = 1.2;
	public static final double DRAG = 0.95;
	public static final double GRAVITY = 0.03;
	public static final int LIFETIME = 18;
	public static final int GLOBS_PER_TICK = 2;
	public static final float MAX_DAMAGE = 6.0f;
	public static final float MIN_DAMAGE = 1.0f;
	/** Where the stream crosses the line of sight, so the nozzle's offset doesn't throw it wide. */
	public static final double CONVERGE = 8.0;
	/** How far a glob reaches round itself: from this at the nozzle... */
	private static final double MIN_RADIUS = 0.2;
	/** ...to this as it burns out. */
	private static final double MAX_RADIUS = 0.7;

	private static final Map<Level, List<Glob>> GLOBS = new WeakHashMap<>();

	private static final class Glob {
		final Entity owner;
		Vec3 pos;
		Vec3 velocity;
		int age;

		Glob(Entity owner, Vec3 pos, Vec3 velocity) {
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
		}

		/** 1 leaving the nozzle, falling to 0 as it burns out. */
		float heat() {
			return 1.0f - age / (float) LIFETIME;
		}
	}

	private FlameStream() {
	}

	static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(FlameStream::tick);
	}

	/** Which way the stream leaves a nozzle at {@code nozzle} for a shooter looking along {@code look} from {@code eye}. */
	public static Vec3 aim(Vec3 eye, Vec3 look, Vec3 nozzle) {
		return eye.add(look.scale(CONVERGE)).subtract(nozzle).normalize();
	}

	/** {@code direction} turned a random amount up to {@code degrees} off, spread evenly over the cone's disc. */
	public static Vec3 scatter(Vec3 direction, float degrees, RandomSource random) {
		if (degrees <= 0.0f) {
			return direction;
		}
		Vec3 side = Math.abs(direction.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0).cross(direction).normalize();
		Vec3 up = direction.cross(side);
		double radius = Math.tan(Math.toRadians(degrees)) * Math.sqrt(random.nextDouble());
		double angle = random.nextDouble() * Math.PI * 2.0;
		return direction.add(side.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle))).normalize();
	}

	/** One tick's spray from {@code shooter}'s nozzle. */
	public static void spray(ServerLevel level, LivingEntity shooter, Vec3 nozzle, float spreadDegrees) {
		spray(level, shooter, nozzle, aim(shooter.getEyePosition(), shooter.getViewVector(1.0f), nozzle), spreadDegrees);
	}

	/** One tick's spray from a nozzle pointing its own way (a mounted one, say), rather than where the shooter looks. */
	public static void spray(ServerLevel level, LivingEntity shooter, Vec3 nozzle, Vec3 direction, float spreadDegrees) {
		Vec3 eye = shooter.getEyePosition();
		// A nozzle pushed into a wall sprays from the wall.
		BlockHitResult blocked = level.clip(new ClipContext(eye, nozzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 start = blocked.getType() == HitResult.Type.MISS ? nozzle : blocked.getLocation().lerp(eye, 0.05);
		List<Glob> globs = GLOBS.computeIfAbsent(level, key -> new ArrayList<>());
		RandomSource random = shooter.getRandom();
		for (int i = 0; i < GLOBS_PER_TICK; i++) {
			double speed = SPEED * (0.92 + random.nextDouble() * 0.16);
			Glob glob = new Glob(shooter, start, scatter(direction, spreadDegrees, random).scale(speed));
			// The second of each pair starts half a step on, so the stream comes out even rather than in clumps.
			if (i % 2 == 0 || step(level, glob, 0.5)) {
				globs.add(glob);
			}
		}
	}

	private static void tick(ServerLevel level) {
		List<Glob> globs = GLOBS.get(level);
		if (globs == null || globs.isEmpty()) {
			return;
		}
		for (Iterator<Glob> it = globs.iterator(); it.hasNext();) {
			if (!step(level, it.next(), 1.0)) {
				it.remove();
			}
		}
	}

	/** Moves a glob {@code fraction} of a tick on. Returns false once it's spent. */
	private static boolean step(ServerLevel level, Glob glob, double fraction) {
		glob.velocity = glob.velocity.add(0.0, -GRAVITY * fraction, 0.0);
		Vec3 from = glob.pos;
		Vec3 to = from.add(glob.velocity.scale(fraction));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, glob.owner));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		float heat = glob.heat();
		double radius = Mth.lerp(1.0f - heat, MIN_RADIUS, MAX_RADIUS);

		Entity struck = firstInPath(level, glob, from, end, radius);
		if (struck != null) {
			NapalmFire.scorch(level, struck, glob.owner, Mth.lerp(heat * heat, MIN_DAMAGE, MAX_DAMAGE), 80 + Math.round(120 * heat));
			Vec3 at = struck.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.2, 0.3, 0.2, 0.02);
			return false;
		}
		if (block.getType() == HitResult.Type.BLOCK) {
			if (level.getFluidState(block.getBlockPos()).is(FluidTags.WATER)) {
				fizzle(level, end);
			} else {
				NapalmFire.land(level, block, glob.owner);
			}
			return false;
		}
		glob.pos = to;
		glob.velocity = glob.velocity.scale(Math.pow(DRAG, fraction));
		glob.age++;
		if (level.getFluidState(BlockPos.containing(to)).is(FluidTags.WATER)) {
			fizzle(level, to);
			return false;
		}
		return glob.age < LIFETIME;
	}

	/** The nearest thing a glob sweeping from {@code from} to {@code to} touches: anything alive and hittable but its shooter (or what they ride). */
	private static Entity firstInPath(ServerLevel level, Glob glob, Vec3 from, Vec3 to, double radius) {
		AABB sweep = new AABB(from, to).inflate(radius + 1.0);
		Entity nearest = null;
		double best = Double.MAX_VALUE;
		for (Entity entity : level.getEntities(glob.owner, sweep,
				entity -> entity.isAlive() && !entity.isSpectator() && entity.isPickable()
						&& (glob.owner == null || !glob.owner.isPassengerOfSameVehicle(entity)))) {
			AABB box = entity.getBoundingBox().inflate(radius);
			double distance = box.contains(from) ? 0.0 : box.clip(from, to).map(from::distanceToSqr).orElse(Double.MAX_VALUE);
			if (distance < best) {
				best = distance;
				nearest = entity;
			}
		}
		return nearest;
	}

	private static void fizzle(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 2, 0.15, 0.05, 0.15, 0.01);
		if (level.getRandom().nextInt(6) == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.4f + level.getRandom().nextFloat() * 0.4f);
		}
	}
}
