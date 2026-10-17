package com.jheinz.tomyen.fusion.melee;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;

/**
 * The Pufferblade (ember puffer + magmasteel sword, at a pond): a magmasteel blade grown out of a smouldering puffer,
 * its spines bristling along the spine of the blade and round the guard, magma glowing in the cracks. 7.5 damage, and:
 * <ul>
 * <li>Whatever it hits catches fire ({@value #BURN_SECONDS} seconds).
 * <li>A charged hit has a {@value #PUFF_CHANCE} chance to puff the target up like a pufferfish: it swells for
 * {@value #PUFF_TICKS} ticks and bursts in a fire blast, {@value #BURST_DAMAGE} damage to it and everything within
 * {@value #BURST_RADIUS} blocks (not you) and sets them alight. The blast breaks no blocks.
 * </ul>
 */
public class PufferbladeItem extends Item {
	public static final float BURN_SECONDS = 3.0f;
	public static final float PUFF_CHANCE = 0.25f;
	public static final int PUFF_TICKS = 14;
	public static final float BURST_DAMAGE = 6.0f;
	public static final double BURST_RADIUS = 2.75;
	/** How much bigger a puffed target gets at the top of its swell. */
	private static final double SWELL = 0.8;
	private static final Identifier PUFF_ID = MyMod.id("pufferblade_puff");
	private static final Map<UUID, Puff> PUFFS = new HashMap<>();

	public PufferbladeItem(Properties properties) {
		super(properties);
	}

	private static final class Puff {
		final ServerLevel level;
		final UUID attacker;
		int ticks;

		Puff(ServerLevel level, UUID attacker) {
			this.level = level;
			this.attacker = attacker;
		}
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		target.igniteForSeconds(BURN_SECONDS);
		if (attacker instanceof Player player && MeleeSwings.chargedHit(player) && !PUFFS.containsKey(target.getUUID()) && target.isAlive()
				&& level.getRandom().nextFloat() < PUFF_CHANCE) {
			PUFFS.put(target.getUUID(), new Puff(level, attacker.getUUID()));
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 1.2f, 0.7f);
		}
	}

	static void tick(ServerLevel level) {
		Iterator<Map.Entry<UUID, Puff>> it = PUFFS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Puff> entry = it.next();
			Puff puff = entry.getValue();
			if (puff.level != level) {
				continue;
			}
			Entity entity = level.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
				if (entity instanceof LivingEntity dead) {
					swell(dead, 0.0);
				}
				it.remove();
				continue;
			}
			puff.ticks++;
			if (puff.ticks < PUFF_TICKS) {
				// Swelling fast, then straining: eased out, with a tremble near the end.
				double t = (double) puff.ticks / PUFF_TICKS;
				double tremble = t > 0.6 ? 0.04 * Math.sin(puff.ticks * 2.6) : 0.0;
				swell(target, SWELL * (1.0 - (1.0 - t) * (1.0 - t)) + tremble);
				level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.6), target.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
				if (puff.ticks % 3 == 0) {
					level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.5), target.getZ(), 3, 0.4, 0.4, 0.4, 0.01);
				}
				continue;
			}
			swell(target, 0.0);
			it.remove();
			burst(level, target, level.getPlayerByUUID(puff.attacker));
		}
	}

	private static void swell(LivingEntity target, double amount) {
		AttributeInstance scale = target.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		if (amount <= 0.0) {
			scale.removeModifier(PUFF_ID);
		} else {
			scale.addOrUpdateTransientModifier(new AttributeModifier(PUFF_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** The fire blast: hurts and lights the target and those around it, never the one who puffed it. No blocks broken. */
	private static void burst(ServerLevel level, LivingEntity centre, Player attacker) {
		Vec3 at = centre.position().add(0.0, centre.getBbHeight() * 0.5, 0.0);
		AABB box = new AABB(at, at).inflate(BURST_RADIUS + 1.0);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (attacker != null && !CrescentWaves.canHit(attacker, target)) {
				continue;
			}
			Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(at);
			double dist = to.length();
			if (dist > BURST_RADIUS + target.getBbWidth() * 0.5) {
				continue;
			}
			float damage = target == centre ? BURST_DAMAGE : BURST_DAMAGE * (float) (1.0 - 0.5 * dist / BURST_RADIUS);
			if (target.hurtServer(level, attacker != null ? level.damageSources().explosion(null, attacker) : level.damageSources().onFire(), damage)) {
				target.igniteForSeconds(4.0f);
				if (target != centre && dist > 1.0e-3) {
					Vec3 push = to.scale(0.7 / dist);
					target.push(push.x, 0.3, push.z);
					target.syncVelocity = true;
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.2, 0.2, 0.2, 0.25);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 10, 0.5, 0.4, 0.5, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	static void clear() {
		PUFFS.clear();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.pufferblade.burn", "item.tomyen.pufferblade.puff");
	}
}
