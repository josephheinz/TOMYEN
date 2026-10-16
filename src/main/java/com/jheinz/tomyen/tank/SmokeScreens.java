package com.jheinz.tomyen.tank;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke screens from tanks' grenade launchers, on the server. Each grenade arcs out to its spot over
 * {@link #FLIGHT_TICKS}, bursts into a cloud {@link #RADIUS} blocks across that hangs for {@link #LIFETIME} ticks,
 * thinning towards the end. The clouds are particles, so players see through them only as well as their
 * particle settings allow; the bank's tanks and crews, though, can't aim through one (see {@link #obscured}).
 * Not saved: a screen is gone after a restart.
 */
public final class SmokeScreens {
	public static final double RADIUS = 3.5;
	public static final int FLIGHT_TICKS = 10;
	public static final int LIFETIME = 300;
	private static final Map<Level, List<Cloud>> CLOUDS = new WeakHashMap<>();

	private SmokeScreens() {
	}

	private static final class Cloud {
		final Vec3 from;
		final Vec3 center;
		int age;

		Cloud(Vec3 from, Vec3 center) {
			this.from = from;
			this.center = center;
		}

		boolean burst() {
			return age >= FLIGHT_TICKS;
		}
	}

	/** Sends a grenade from the launcher at {@code from} to burst at {@code to}. */
	public static void launch(ServerLevel level, Vec3 from, Vec3 to) {
		CLOUDS.computeIfAbsent(level, key -> new ArrayList<>()).add(new Cloud(from, to));
	}

	public static void tick(ServerLevel level) {
		List<Cloud> clouds = CLOUDS.get(level);
		if (clouds == null || clouds.isEmpty()) {
			return;
		}
		for (Iterator<Cloud> it = clouds.iterator(); it.hasNext();) {
			Cloud cloud = it.next();
			cloud.age++;
			if (!cloud.burst()) {
				// The grenade's arc: a puff of smoke where it is now, a little above the straight line.
				float t = cloud.age / (float) FLIGHT_TICKS;
				Vec3 p = cloud.from.lerp(cloud.center, t).add(0.0, Math.sin(t * Math.PI) * 2.0, 0.0);
				level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
				continue;
			}
			Vec3 c = cloud.center;
			if (cloud.age == FLIGHT_TICKS) {
				level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 20, 0.6, 0.4, 0.6, 0.08);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 30, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.01);
			}
			int life = cloud.age - FLIGHT_TICKS;
			if (life >= LIFETIME) {
				it.remove();
				continue;
			}
			// Thick for most of its life, then thinning out.
			int every = life < LIFETIME * 0.75 ? 3 : 6;
			if (life % every == 0) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.5, c.z, 3, RADIUS * 0.45, 0.8, RADIUS * 0.45, 0.005);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0, c.z, 4, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.0);
				level.sendParticles(ParticleTypes.WHITE_SMOKE, c.x, c.y + 0.8, c.z, 3, RADIUS * 0.5, 0.8, RADIUS * 0.5, 0.0);
			}
		}
	}

	/** Whether a line of sight between two points passes through a cloud that has burst. */
	public static boolean obscured(Level level, Vec3 from, Vec3 to) {
		List<Cloud> clouds = CLOUDS.get(level);
		if (clouds == null) {
			return false;
		}
		Vec3 path = to.subtract(from);
		double lengthSqr = path.lengthSqr();
		for (Cloud cloud : clouds) {
			if (!cloud.burst()) {
				continue;
			}
			// Closest point on the segment to the cloud's middle.
			double t = lengthSqr < 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, cloud.center.subtract(from).dot(path) / lengthSqr));
			if (from.add(path.scale(t)).distanceToSqr(cloud.center.add(0.0, 1.0, 0.0)) < RADIUS * RADIUS) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		CLOUDS.clear();
	}
}
