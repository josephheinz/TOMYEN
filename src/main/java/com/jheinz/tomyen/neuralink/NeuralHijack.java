package com.jheinz.tomyen.neuralink;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;

/**
 * Hold the trigger on a mob within {@link #RANGE} blocks: after {@link #STARE_TICKS} of unbroken staring it's
 * yours for {@link #ALLY_TICKS}. An ally won't target you (see MobHijackMixin), and goes after whatever you last
 * hit or whatever last hit you, or anything that's after you; otherwise it trails along behind you. Bosses
 * resist. An EMP frees everything hijacked in its range.
 */
public final class NeuralHijack implements NeuralCore.Ability {
	public static final double RANGE = 24.0;
	public static final int STARE_TICKS = 60;
	public static final int ALLY_TICKS = 600;
	/** How long after you hit something your allies still go after it. */
	private static final int GRUDGE_TICKS = 200;
	private static final double DEFEND_RADIUS = 16.0;
	private static final double FOLLOW_DISTANCE = 8.0;

	private record Ally(UUID owner, ResourceKey<Level> level, long until) {
	}

	private static final Map<UUID, Ally> ALLIES = new HashMap<>();

	@Override
	public boolean start(ServerPlayer player, NeuralCore.State state) {
		state.targetId = -1;
		state.progress = 0;
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.8f);
		return true;
	}

	@Override
	public boolean channel(ServerPlayer player, NeuralCore.State state) {
		Entity looked = NeuralTargets.lookedAt(player, RANGE, entity -> entity instanceof Mob);
		if (!(looked instanceof Mob mob)) {
			state.targetId = -1;
			state.progress = 0;
			return true;
		}
		if (mob.getId() != state.targetId) {
			state.targetId = mob.getId();
			state.progress = 0;
			if (NeuralTargets.isBoss(mob)) {
				player.sendOverlayMessage(Component.translatable("neuralink.tomyen.hijack.immune").withStyle(ChatFormatting.RED));
			}
		}
		if (NeuralTargets.isBoss(mob) || ownerOf(mob) == player) {
			return true;
		}
		state.progress++;
		ServerLevel level = (ServerLevel) player.level();
		if (state.progress % 5 == 0) {
			level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 6, 0.3, 0.2, 0.3, 0.5);
		}
		if (state.progress >= STARE_TICKS) {
			hijack(level, player, mob);
			state.cooldowns[NeuralinkModule.NEURAL_HIJACK.ordinal()] = NeuralinkModule.NEURAL_HIJACK.cooldownTicks();
			return false;
		}
		return true;
	}

	private static void hijack(ServerLevel level, ServerPlayer player, Mob mob) {
		ALLIES.put(mob.getUUID(), new Ally(player.getUUID(), level.dimension(), level.getGameTime() + ALLY_TICKS));
		if (mob.getTarget() == player) {
			mob.setTarget(null);
		}
		forget(mob, player);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 20, 0.4, 0.3, 0.4, 0.4);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 0.8f, 1.6f);
		player.sendOverlayMessage(Component.translatable("neuralink.tomyen.hijack.success", mob.getDisplayName(), ALLY_TICKS / 20)
				.withColor(NeuralinkModule.NEURAL_HIJACK.color()));
	}

	/** The player a mob is hijacked by, if they're online; null if it's its own mob. */
	static ServerPlayer ownerOf(Mob mob) {
		Ally ally = ALLIES.get(mob.getUUID());
		return ally == null || mob.level().getServer() == null ? null : mob.level().getServer().getPlayerList().getPlayer(ally.owner());
	}

	/** Frees a hijacked mob (EMP). */
	static void release(Mob mob) {
		ALLIES.remove(mob.getUUID());
	}

	/** Whether a hijacked mob must leave this target alone: its owner, or another of its owner's allies. */
	public static boolean isFriendly(Mob mob, LivingEntity target) {
		Ally ally = ALLIES.get(mob.getUUID());
		if (ally == null || target == null) {
			return false;
		}
		Ally other = ALLIES.get(target.getUUID());
		return target.getUUID().equals(ally.owner()) || other != null && other.owner().equals(ally.owner());
	}

	/** Steers every ally towards its owner's enemies. */
	static void tickAllies(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Ally>> it = ALLIES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Ally> entry = it.next();
			Ally ally = entry.getValue();
			ServerLevel level = server.getLevel(ally.level());
			Entity entity = level == null ? null : level.getEntity(entry.getKey());
			if (!(entity instanceof Mob mob) || !mob.isAlive() || level.getGameTime() > ally.until()) {
				if (entity instanceof Mob mob && mob.isAlive()) {
					level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
				}
				it.remove();
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(ally.owner());
			if (owner == null || owner.level() != level) {
				continue;
			}
			forget(mob, owner);
			LivingEntity enemy = enemyOf(owner, mob);
			if (enemy != null && mob.getTarget() != enemy) {
				mob.setTarget(enemy);
				mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, enemy);
			} else if (enemy == null && mob.getTarget() == null && mob.distanceTo(owner) > FOLLOW_DISTANCE && mob.tickCount % 20 == 0) {
				mob.getNavigation().moveTo(owner, 1.1);
			}
			if (mob.tickCount % 20 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.15, 0.1, 0.15, 0.1);
			}
		}
	}

	/** Drops the owner as a target, wherever the mob keeps it. */
	private static void forget(Mob mob, ServerPlayer owner) {
		if (mob.getTarget() == owner) {
			mob.setTarget(null);
		}
		Optional<LivingEntity> remembered = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
		if (remembered != null && remembered.isPresent() && isFriendly(mob, remembered.get())) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	/** Who the owner is fighting: what they last hit, what last hit them, or the nearest thing after them. */
	private static LivingEntity enemyOf(ServerPlayer owner, Mob ally) {
		LivingEntity hit = owner.getLastHurtMob();
		if (hit != null && owner.tickCount - owner.getLastHurtMobTimestamp() < GRUDGE_TICKS && isFairGame(ally, hit)) {
			return hit;
		}
		LivingEntity attacker = owner.getLastHurtByMob();
		if (attacker != null && isFairGame(ally, attacker)) {
			return attacker;
		}
		Mob nearest = null;
		for (Mob hunter : owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(DEFEND_RADIUS),
				other -> other.getTarget() == owner && isFairGame(ally, other))) {
			if (nearest == null || hunter.distanceToSqr(owner) < nearest.distanceToSqr(owner)) {
				nearest = hunter;
			}
		}
		return nearest;
	}

	private static boolean isFairGame(Mob ally, LivingEntity target) {
		return target != ally && target.isAlive() && !isFriendly(ally, target);
	}
}
