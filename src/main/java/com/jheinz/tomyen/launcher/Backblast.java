package com.jheinz.tomyen.launcher;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The booster's blast out of the venturi when a rocket is fired: a cone of smoke and flame behind the shooter.
 * Anything living in the cone (within {@link #LENGTH} blocks and {@link #HALF_ANGLE} degrees of straight back, with
 * nothing solid in between) is burned and thrown back, hardest right behind the tube.
 */
final class Backblast {
	private static final double LENGTH = 5.0;
	private static final double HALF_ANGLE = 35.0;
	private static final float MAX_DAMAGE = 9.0f;
	private static final double MAX_PUSH = 1.4;
	/** This close behind the tube, it sets you alight. */
	private static final double IGNITE_DISTANCE = 1.8;

	private Backblast() {
	}

	/** @param back unit vector pointing out of the venturi */
	static void blast(ServerLevel level, Player shooter, Vec3 venturi, Vec3 back) {
		RandomSource random = shooter.getRandom();
		level.sendParticles(ParticleTypes.EXPLOSION, venturi.x + back.x * 0.6, venturi.y + back.y * 0.6, venturi.z + back.z * 0.6,
				1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 28; i++) {
			double along = 0.3 + random.nextDouble() * LENGTH * 0.8;
			double spread = along * 0.3;
			Vec3 p = venturi.add(back.scale(along)).add(random.nextGaussian() * spread, random.nextGaussian() * spread,
					random.nextGaussian() * spread);
			ParticleOptions puff = i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE;
			// A count of 0 makes the offset the particle's velocity.
			double speed = 0.15 + random.nextDouble() * 0.25;
			level.sendParticles(puff, p.x, p.y, p.z, 0, back.x, back.y + 0.05, back.z, speed);
			if (along < 2.0) {
				level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 0, back.x, back.y, back.z, speed * 0.6);
			}
		}

		double cos = Math.cos(Math.toRadians(HALF_ANGLE));
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(Launchers.BACKBLAST),
				shooter);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, shooter.getBoundingBox().inflate(LENGTH + 1.0),
				entity -> entity != shooter && entity.isAlive() && !entity.isSpectator())) {
			Vec3 to = victim.getBoundingBox().getCenter().subtract(venturi);
			double along = to.dot(back);
			if (along < 0.0 || along > LENGTH || along < to.length() * cos) {
				continue;
			}
			if (level.clip(new ClipContext(venturi, victim.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, shooter)).getType() != HitResult.Type.MISS) {
				continue;
			}
			double strength = 1.0 - along / LENGTH;
			victim.hurtServer(level, source, 1.0f + MAX_DAMAGE * (float) strength);
			if (along < IGNITE_DISTANCE) {
				victim.igniteForSeconds(2.0f);
			}
			victim.push(back.x * MAX_PUSH * strength, 0.25 + 0.2 * strength, back.z * MAX_PUSH * strength);
			// Players move themselves, so they need telling.
			victim.syncVelocity = true;
		}
	}
}
