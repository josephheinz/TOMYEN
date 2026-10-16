package com.jheinz.tomyen.neuralink;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import com.jheinz.tomyen.ModEffects;

/**
 * A shockwave out to {@link #RADIUS} blocks. Mobs in it forget who they were after and are stunned (rooted and
 * too weak to hurt anyone) for {@link #STUN_TICKS}; hijacked mobs are freed, except the user's own. Every other
 * player wearing a Neuralink in range has it knocked out for {@link #JAM_TICKS}: all modules off.
 */
final class EmpPulse implements NeuralCore.Ability {
	public static final double RADIUS = 12.0;
	public static final int STUN_TICKS = 60;
	public static final int JAM_TICKS = 300;

	@Override
	public boolean start(ServerPlayer player, NeuralCore.State state) {
		ServerLevel level = (ServerLevel) player.level();
		double radiusSqr = RADIUS * RADIUS;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS),
				mob -> mob.isAlive() && mob.distanceToSqr(player) <= radiusSqr && !NeuralTargets.isBoss(mob))) {
			if (NeuralHijack.ownerOf(mob) == player) {
				continue;
			}
			NeuralHijack.release(mob);
			mob.setTarget(null);
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			mob.getNavigation().stop();
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, 9, false, false));
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 9, false, false));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY(0.6), mob.getZ(), 8, 0.3, 0.4, 0.3, 0.3);
		}
		for (ServerPlayer other : level.players()) {
			if (other != player && other.distanceToSqr(player) <= radiusSqr && !NeuralinkItem.installedModules(other).isEmpty()) {
				NeuralCore.interrupt(other);
				other.addEffect(new MobEffectInstance(ModEffects.NEURAL_STATIC, JAM_TICKS, 0, false, true, true));
				ServerPlayNetworking.send(other, new NeuralNetworking.Flash(NeuralNetworking.Flash.EMP));
			}
		}

		// Rings of sparks running outwards.
		for (double radius = 1.5; radius <= RADIUS; radius += 2.0) {
			int count = (int) (radius * 5);
			for (int i = 0; i < count; i++) {
				float angle = Mth.TWO_PI * i / count;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + Mth.cos(angle) * radius, player.getY() + 0.3,
						player.getZ() + Mth.sin(angle) * radius, 1, 0.0, 0.1, 0.0, 0.05);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
				1.2f, 0.6f);
		return true;
	}
}
