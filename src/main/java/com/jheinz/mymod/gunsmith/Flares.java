package com.jheinz.mymod.gunsmith;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.alien.UfoSightings;

/**
 * Flares from the Flare Gun, flown by the server (no entity: a point with a velocity), each carrying a light with it (see
 * {@link LightSpots}) and trailing red sparks and smoke.
 * <ul>
 * <li>In flight it arcs under gravity. Whatever it strikes is set alight for {@value #IGNITE_SECONDS} seconds, and it burns
 * on there; one that hits the ground burns where it lies.</li>
 * <li>Fired into the open sky, it pops a parachute at the top of its climb and drifts down slowly, lighting the ground below
 * for up to {@value #DRIFT_TICKS} ticks. At night over the Overworld one that high can catch a passing saucer's eye
 * ({@value #UFO_CHANCE} chance, through UfoSightings and its game rules).</li>
 * </ul>
 */
final class Flares {
	private static final double SPEED = 2.0;
	private static final double GRAVITY = 0.04;
	private static final double DRAG = 0.99;
	private static final double DRIFT_SPEED = 0.07;
	private static final int DRIFT_TICKS = 400;
	private static final int BURN_TICKS = 200;
	private static final int STUCK_BURN_TICKS = 80;
	private static final int MAX_FLIGHT_TICKS = 120;
	private static final float IGNITE_SECONDS = 8.0f;
	private static final float IMPACT_DAMAGE = 4.0f;
	/** A flare this far over the ground when its parachute opens is high enough to be seen from afar. */
	private static final int SIGNAL_HEIGHT = 12;
	private static final float UFO_CHANCE = 0.25f;
	private static final int BRIGHTNESS = 15;
	private static final DustParticleOptions SPARK = new DustParticleOptions(0xFF3A2A, 1.2f);
	private static final DustParticleOptions GLOW = new DustParticleOptions(0xFFB0A0, 0.8f);

	private enum State {
		FLYING, DRIFTING, BURNING
	}

	private static final class Flare {
		final ServerLevel level;
		final UUID owner;
		Vec3 pos;
		Vec3 velocity;
		State state = State.FLYING;
		int age;
		int burn;
		/** Something it's stuck in and burning on, if it struck a mob. */
		Entity stuckIn;

		Flare(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	private static final List<Flare> FLARES = new ArrayList<>();

	private Flares() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(Flares::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLARES.clear());
	}

	static void launch(ServerLevel level, Player shooter, Vec3 from, Vec3 direction) {
		FLARES.add(new Flare(level, shooter.getUUID(), from, direction.normalize().scale(SPEED).add(shooter.getDeltaMovement())));
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Flare> it = FLARES.iterator(); it.hasNext(); ) {
			Flare flare = it.next();
			if (!step(flare)) {
				LightSpots.move(flare.level, flare, null, 0);
				it.remove();
			} else {
				LightSpots.move(flare.level, flare, BlockPos.containing(flare.pos), BRIGHTNESS);
			}
		}
	}

	/** Moves one flare on a tick; false once it's burnt out (or its ground has been unloaded). */
	private static boolean step(Flare flare) {
		ServerLevel level = flare.level;
		if (!level.isLoaded(BlockPos.containing(flare.pos)) || level.getServer().getLevel(level.dimension()) != level) {
			return false;
		}
		flare.age++;
		switch (flare.state) {
			case FLYING -> fly(flare);
			case DRIFTING -> drift(flare);
			case BURNING -> {
				if (flare.stuckIn != null) {
					if (!flare.stuckIn.isAlive()) {
						flare.stuckIn = null;
					} else {
						flare.pos = flare.stuckIn.position().add(0, flare.stuckIn.getBbHeight() * 0.6, 0);
					}
				}
				if (--flare.burn <= 0) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, flare.pos.x, flare.pos.y, flare.pos.z, 4, 0.1, 0.1, 0.1, 0.01);
					return false;
				}
			}
		}
		sparks(flare);
		return true;
	}

	private static void fly(Flare flare) {
		ServerLevel level = flare.level;
		Vec3 next = flare.pos.add(flare.velocity);
		BlockHitResult blockHit = level.clip(new ClipContext(flare.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		Vec3 end = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
		Entity shooter = level.getEntity(flare.owner);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, shooter, flare.pos, end,
				new AABB(flare.pos, end).inflate(0.3), entity -> !entity.isSpectator() && entity.isPickable()
						&& (entity != shooter || flare.age > 6), 0.3f);
		if (entityHit != null) {
			strike(flare, entityHit.getEntity(), shooter);
			return;
		}
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			flare.pos = blockHit.getLocation().subtract(flare.velocity.normalize().scale(0.15));
			land(flare, BURN_TICKS);
			return;
		}
		flare.pos = next;
		flare.velocity = flare.velocity.scale(DRAG).add(0, -GRAVITY, 0);
		if (flare.velocity.y < 0.0 && flare.age > 4) {
			openParachute(flare, shooter);
		} else if (flare.age > MAX_FLIGHT_TICKS) {
			land(flare, STUCK_BURN_TICKS);
		}
	}

	private static void strike(Flare flare, Entity target, Entity shooter) {
		ServerLevel level = flare.level;
		DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET), shooter);
		target.hurtServer(level, source, IMPACT_DAMAGE);
		target.igniteForSeconds(IGNITE_SECONDS);
		flare.stuckIn = target;
		flare.pos = target.position().add(0, target.getBbHeight() * 0.6, 0);
		land(flare, STUCK_BURN_TICKS);
	}

	private static void land(Flare flare, int ticks) {
		flare.state = State.BURNING;
		flare.burn = ticks;
		flare.velocity = Vec3.ZERO;
		flare.level.playSound(null, flare.pos.x, flare.pos.y, flare.pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.3f);
	}

	/** Tops out: the parachute opens, and high over open ground at night it may draw a saucer. */
	private static void openParachute(Flare flare, Entity shooter) {
		ServerLevel level = flare.level;
		flare.state = State.DRIFTING;
		flare.burn = DRIFT_TICKS;
		flare.velocity = new Vec3(flare.velocity.x * 0.2, -DRIFT_SPEED, flare.velocity.z * 0.2);
		level.playSound(null, flare.pos.x, flare.pos.y, flare.pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 1.4f);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(flare.pos.x), (int) Math.floor(flare.pos.z));
		if (shooter instanceof ServerPlayer player && level.dimension() == Level.OVERWORLD && level.isDarkOutside()
				&& flare.pos.y - ground >= SIGNAL_HEIGHT && level.getGameRules().get(UfoSightings.UFO_SIGHTINGS)
				&& level.getRandom().nextFloat() < UFO_CHANCE) {
			UfoSightings.visit(level, player);
		}
	}

	private static void drift(Flare flare) {
		ServerLevel level = flare.level;
		// A slow sway under the canopy.
		double sway = Math.sin(flare.age * 0.08) * 0.015;
		Vec3 next = flare.pos.add(flare.velocity.x + sway, flare.velocity.y, flare.velocity.z + sway * 0.6);
		BlockHitResult hit = level.clip(new ClipContext(flare.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.BLOCK) {
			flare.pos = hit.getLocation().add(0, 0.1, 0);
			land(flare, STUCK_BURN_TICKS);
			return;
		}
		flare.pos = next;
		flare.velocity = new Vec3(flare.velocity.x * 0.98, flare.velocity.y, flare.velocity.z * 0.98);
		if (--flare.burn <= 0) {
			flare.state = State.BURNING;
			flare.burn = 1;
		}
	}

	private static void sparks(Flare flare) {
		ServerLevel level = flare.level;
		Vec3 p = flare.pos;
		level.sendParticles(SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
		level.sendParticles(GLOW, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		if (flare.age % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.005);
		}
		if (flare.state != State.FLYING || flare.age % 2 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 1, 0.04, 0.04, 0.04, 0.01);
		}
		if (flare.state == State.BURNING && flare.age % 20 == 0) {
			level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.4f);
		}
	}
}
