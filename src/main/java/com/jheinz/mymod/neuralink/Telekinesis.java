package com.jheinz.mymod.neuralink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModDamageTypes;

/**
 * Hold to lift the mob or item under the crosshair (within {@link #RANGE} blocks) and keep it floating
 * {@link #HOLD_DISTANCE} blocks in front of you; let go to fling it where you're looking. Whatever's thrown
 * hurts what it hits, and itself if it's alive, harder the faster it's going. Bosses and players can't be
 * lifted. Cut off (by an overheat or an EMP), it just drops.
 */
final class Telekinesis implements NeuralCore.Ability {
	public static final double RANGE = 12.0;
	private static final double HOLD_DISTANCE = 3.5;
	/** How hard the held thing is pulled to its spot each tick, as a fraction of the gap. */
	private static final double PULL = 0.4;
	/** Lose hold of it if it's dragged this far away. */
	private static final double MAX_DISTANCE = RANGE + 4.0;
	private static final double THROW_SPEED = 2.2;
	private static final float THROW_HEAT = 6.0f;
	/** A thrown thing stops counting as thrown after this long without hitting anything. */
	private static final int FLIGHT_TICKS = 50;
	private static final double MIN_IMPACT_SPEED = 0.35;
	private static final float MAX_IMPACT_DAMAGE = 14.0f;

	private record Thrown(UUID entity, ResourceKey<Level> level, UUID thrower, int thrownAt) {
	}

	private static final List<Thrown> THROWN = new ArrayList<>();
	/** The speed of each thrown entity last tick, before a collision took it away. */
	private static final Map<UUID, Vec3> LAST_VELOCITY = new HashMap<>();

	static boolean canGrab(Entity entity) {
		return entity instanceof Mob mob && !NeuralTargets.isBoss(mob) || entity instanceof ItemEntity || entity instanceof PrimedTnt;
	}

	@Override
	public boolean start(ServerPlayer player, NeuralCore.State state) {
		Entity target = NeuralTargets.lookedAt(player, RANGE, Telekinesis::canGrab);
		if (target == null) {
			return false;
		}
		state.targetId = target.getId();
		target.stopRiding();
		target.ejectPassengers();
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8f, 1.4f);
		return true;
	}

	@Override
	public boolean channel(ServerPlayer player, NeuralCore.State state) {
		ServerLevel level = (ServerLevel) player.level();
		Entity held = level.getEntity(state.targetId);
		if (held == null || !held.isAlive() || held.distanceTo(player) > MAX_DISTANCE) {
			return false;
		}
		Vec3 anchor = player.getEyePosition().add(player.getViewVector(1.0f).scale(HOLD_DISTANCE + held.getBbWidth() / 2.0))
				.subtract(0.0, held.getBbHeight() / 2.0, 0.0);
		held.setDeltaMovement(anchor.subtract(held.position()).scale(PULL));
		held.resetFallDistance();
		held.needsSync = true;
		if (held instanceof Mob mob) {
			mob.getNavigation().stop();
		} else if (held instanceof ItemEntity item) {
			item.setPickUpDelay(10);
		}
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, held.getX(), held.getY(0.5), held.getZ(), 3, held.getBbWidth() * 0.4,
					held.getBbHeight() * 0.4, held.getBbWidth() * 0.4, 0.02);
		}
		return true;
	}

	@Override
	public void stop(ServerPlayer player, NeuralCore.State state, boolean interrupted) {
		Entity held = player.level().getEntity(state.targetId);
		if (held == null || !held.isAlive() || interrupted) {
			return;
		}
		Vec3 velocity = player.getViewVector(1.0f).scale(THROW_SPEED);
		held.setDeltaMovement(velocity);
		held.needsSync = true;
		THROWN.add(new Thrown(held.getUUID(), player.level().dimension(), player.getUUID(), player.level().getServer().getTickCount()));
		LAST_VELOCITY.put(held.getUUID(), velocity);
		NeuralCore.addHeat(player, state, THROW_HEAT);
		player.level().playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
				0.7f, 1.3f);
	}

	/** Checks everything in flight for a hit. Runs after entities have moved this tick. */
	static void tickThrown(MinecraftServer server) {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown thrown = it.next();
			ServerLevel level = server.getLevel(thrown.level());
			Entity entity = level == null ? null : level.getEntity(thrown.entity());
			int age = server.getTickCount() - thrown.thrownAt();
			if (entity == null || !entity.isAlive() || age > FLIGHT_TICKS) {
				it.remove();
				LAST_VELOCITY.remove(thrown.entity());
				continue;
			}
			Vec3 velocity = LAST_VELOCITY.getOrDefault(thrown.entity(), entity.getDeltaMovement());
			Entity thrower = level.getEntity(thrown.thrower());
			List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.2),
					other -> other != entity && other != thrower && other.isAlive() && !other.isSpectator());
			boolean wall = age > 1 && (entity.horizontalCollision || entity.verticalCollision);
			double speed = velocity.length();
			if ((wall || !struck.isEmpty()) && speed >= MIN_IMPACT_SPEED) {
				impact(level, entity, thrower, struck, velocity, wall);
				it.remove();
				LAST_VELOCITY.remove(thrown.entity());
			} else if (wall) {
				// Landed too softly to hurt anything.
				it.remove();
				LAST_VELOCITY.remove(thrown.entity());
			} else {
				LAST_VELOCITY.put(thrown.entity(), entity.getDeltaMovement());
			}
		}
	}

	private static void impact(ServerLevel level, Entity thrown, Entity thrower, List<LivingEntity> struck, Vec3 velocity, boolean wall) {
		float damage = (float) Math.min(MAX_IMPACT_DAMAGE, 2.0 + velocity.length() * 5.0);
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(ModDamageTypes.TELEKINETIC_IMPACT), thrown, thrower);
		for (LivingEntity victim : struck) {
			victim.hurtServer(level, source, damage);
			victim.push(velocity.x * 0.5, 0.25, velocity.z * 0.5);
		}
		if (thrown instanceof LivingEntity living) {
			// Slamming into a wall hurts more than bowling into a crowd.
			living.hurtServer(level, source, wall ? damage : damage * 0.5f);
		}
		level.sendParticles(ParticleTypes.CRIT, thrown.getX(), thrown.getY(0.5), thrown.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
		level.playSound(null, thrown.getX(), thrown.getY(), thrown.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.7f);
	}
}
