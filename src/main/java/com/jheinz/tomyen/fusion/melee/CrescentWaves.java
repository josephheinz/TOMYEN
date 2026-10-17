package com.jheinz.tomyen.fusion.melee;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Waves that sweep out from a swing: a crescent of moonlight (the Moonblade by night), a crescent of sea water (the
 * Leviathan Blade), or a full ring of it (the Leviathan's tidal surge). Each is an arc of particles that runs out from
 * where its wielder stood, a little over a block a tick, hitting each living thing its front passes through once.
 * Only the server knows them; the particles are what players see.
 */
public final class CrescentWaves {
	public enum Kind {
		/** Pale silver-blue moonlight. */
		MOON(0xD8E4FF, 0x9FB6F0),
		/** Sea green-blue water with spray. */
		TIDE(0x6FD0F0, 0x2E7FC8),
		/** Water lit by the moon: the Leviathan by night. */
		MOON_TIDE(0xCFEFFF, 0x5FA8E8),
		/** The surge: a wall of water. */
		SURGE(0x8FDCF5, 0x1F6FB8);

		final int light;
		final int dark;

		Kind(int light, int dark) {
			this.light = light;
			this.dark = dark;
		}

		boolean water() {
			return this != MOON;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	/** Half the depth of a wave's front, blocks: what it hits as it passes. */
	private static final double FRONT = 0.9;

	private CrescentWaves() {
	}

	private static final class Wave {
		final ServerLevel level;
		final UUID owner;
		final Vec3 origin;
		final Vec3 dir;
		final double reach;
		final double speed;
		final double arc;
		final float damage;
		final float knockback;
		final Kind kind;
		final Set<Integer> hit = new HashSet<>();
		double dist;

		Wave(ServerLevel level, Player owner, Vec3 origin, Vec3 dir, double start, double reach, double speed, double arc, float damage,
				float knockback, Kind kind) {
			this.level = level;
			this.owner = owner.getUUID();
			this.origin = origin;
			this.dir = dir;
			this.dist = start;
			this.reach = reach;
			this.speed = speed;
			this.arc = arc;
			this.damage = damage;
			this.knockback = knockback;
			this.kind = kind;
		}
	}

	/** The player's facing, flattened: a wave runs along the ground. */
	static Vec3 flatLook(Player player) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** A crescent sweeping out ahead of the player, about 100 degrees wide, out to {@code reach} blocks. */
	public static void crescent(ServerLevel level, Player owner, Kind kind, double reach, float damage) {
		Vec3 origin = new Vec3(owner.getX(), owner.getY() + owner.getBbHeight() * 0.55, owner.getZ());
		WAVES.add(new Wave(level, owner, origin, flatLook(owner), 0.9, reach, 1.15, Math.toRadians(50.0), damage, 0.55f, kind));
	}

	/** A ring of water running out all round the player. */
	public static void ring(ServerLevel level, Player owner, double reach, float damage) {
		Vec3 origin = new Vec3(owner.getX(), owner.getY() + 0.6, owner.getZ());
		WAVES.add(new Wave(level, owner, origin, flatLook(owner), 1.0, reach, 1.0, Math.PI, damage, 1.3f, Kind.SURGE));
	}

	static void tick(ServerLevel level) {
		Iterator<Wave> it = WAVES.iterator();
		while (it.hasNext()) {
			Wave wave = it.next();
			if (wave.level != level) {
				continue;
			}
			Player owner = level.getPlayerByUUID(wave.owner);
			if (owner == null || wave.dist > wave.reach) {
				it.remove();
				continue;
			}
			draw(wave);
			strike(wave, owner);
			wave.dist += wave.speed;
		}
	}

	/** The arc of the front: thickest in the middle, tapering to points (a crescent), fading as it nears its reach. */
	private static void draw(Wave wave) {
		ServerLevel level = wave.level;
		double fade = 1.0 - 0.5 * (wave.dist / wave.reach);
		int points = Math.max(6, (int) (wave.arc * wave.dist * (wave.kind == Kind.SURGE ? 2.2 : 3.2)));
		DustParticleOptions light = new DustParticleOptions(wave.kind.light, (float) (1.4 * fade));
		DustParticleOptions dark = new DustParticleOptions(wave.kind.dark, (float) (1.1 * fade));
		double base = Math.atan2(wave.dir.z, wave.dir.x);
		for (int i = 0; i <= points; i++) {
			double t = -1.0 + 2.0 * i / points;
			double a = base + t * wave.arc;
			double thick = wave.kind == Kind.SURGE ? 1.0 : Math.cos(t * Math.PI / 2.0);
			double cx = Math.cos(a);
			double cz = Math.sin(a);
			double x = wave.origin.x + cx * wave.dist;
			double z = wave.origin.z + cz * wave.dist;
			double y = wave.origin.y + (wave.kind == Kind.SURGE ? 0.0 : 0.15 * Math.sin(t * Math.PI));
			level.sendParticles(light, x, y, z, 1, 0.02, 0.05 + 0.1 * thick, 0.02, 0.0);
			if (thick > 0.35) {
				double back = 0.45 * thick;
				level.sendParticles(dark, x - cx * back, y, z - cz * back, 1, 0.02, 0.05, 0.02, 0.0);
			}
			if (wave.kind.water()) {
				if (i % 2 == 0) {
					level.sendParticles(ParticleTypes.SPLASH, x, y + 0.2, z, 2, 0.1, 0.15, 0.1, 0.0);
				}
				if (wave.kind == Kind.SURGE && i % 3 == 0) {
					// The wall of the surge: water thrown up and falling back.
					level.sendParticles(ParticleTypes.FALLING_WATER, x, y + 0.6 + 0.6 * fade, z, 2, 0.15, 0.4, 0.15, 0.0);
					level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.3, z, 1, 0.1, 0.2, 0.1, 0.02);
				}
			}
			if ((wave.kind == Kind.MOON || wave.kind == Kind.MOON_TIDE) && Math.abs(t) < 0.5 && i % 2 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.005);
			}
		}
	}

	private static void strike(Wave wave, Player owner) {
		ServerLevel level = wave.level;
		double r = wave.dist + FRONT + 1.0;
		AABB box = new AABB(wave.origin.x - r, wave.origin.y - 2.0, wave.origin.z - r, wave.origin.x + r, wave.origin.y + 2.0, wave.origin.z + r);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> canHit(owner, e))) {
			if (wave.hit.contains(target.getId())) {
				continue;
			}
			Vec3 d = new Vec3(target.getX() - wave.origin.x, 0.0, target.getZ() - wave.origin.z);
			double dist = d.length();
			// Measured to the near side of the target, so big things get caught as the front reaches them.
			double near = Math.max(0.0, dist - target.getBbWidth() * 0.5);
			if (near > wave.dist + FRONT || dist + target.getBbWidth() * 0.5 < wave.dist - FRONT) {
				continue;
			}
			if (dist > 1.0e-4 && wave.arc < Math.PI) {
				double angle = Math.acos(Mth.clamp(d.dot(wave.dir) / dist, -1.0, 1.0));
				if (angle > wave.arc + Math.atan2(target.getBbWidth() * 0.5, Math.max(dist, 0.5))) {
					continue;
				}
			}
			double top = target.getY() + target.getBbHeight();
			if (top < wave.origin.y - 1.6 || target.getY() > wave.origin.y + 1.6) {
				continue;
			}
			wave.hit.add(target.getId());
			DamageSource source = level.damageSources().playerAttack(owner);
			if (target.hurtServer(level, source, wave.damage)) {
				Vec3 push = dist > 1.0e-4 && wave.arc >= Math.PI ? d.scale(1.0 / dist) : wave.dir;
				target.knockback(wave.knockback, -push.x, -push.z, source, wave.damage);
				if (wave.kind == Kind.SURGE) {
					target.push(0.0, 0.35, 0.0);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), owner);
				}
				target.syncVelocity = true;
				if (wave.kind.water()) {
					target.clearFire();
				}
				level.sendParticles(wave.kind.water() ? ParticleTypes.SPLASH : ParticleTypes.END_ROD, target.getX(), target.getY(0.6),
						target.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
			}
		}
	}

	static boolean canHit(Player owner, LivingEntity target) {
		return target != owner && target.isAlive() && !owner.isAlliedTo(target) && !(target instanceof ArmorStand stand && stand.isMarker())
				&& !(target instanceof OwnableEntity pet && owner.equals(pet.getOwner()))
				&& !(target instanceof Player player && (player.isSpectator() || !owner.canHarmPlayer(player)));
	}

	static void clear() {
		WAVES.clear();
	}
}
