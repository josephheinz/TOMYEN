package com.jheinz.mymod.survival;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.exoskeleton.ExoSockets;

/**
 * Scrap armor: plates of tech scrap riveted over leather. On its own it sits between leather and chainmail; what it's
 * for is machines. Against a blow from a rogue machine ({@link Survival#MACHINES}: Skullcranes, Cathode Walkers, UFOs)
 * or any electric damage ({@link Survival#ELECTRIC}: alien zaps, shorts, lasers) each piece takes
 * {@link #REDUCTION_PER_PIECE} off, and the full set a further {@link #FULL_SET_BONUS}, with a spray of sparks as the
 * plates take it. Pieces in an exoskeleton's sockets count. LivingEntityScrapArmorMixin applies it to every hurt.
 */
public final class ScrapArmor {
	public static final float REDUCTION_PER_PIECE = 0.1f;
	public static final float FULL_SET_BONUS = 0.1f;
	public static final int FULL_SET = 4;

	private ScrapArmor() {
	}

	static void initialize() {
	}

	public static boolean isPiece(ItemStack stack) {
		Item item = stack.getItem();
		return item == Survival.SCRAP_HELMET || item == Survival.SCRAP_CHESTPLATE || item == Survival.SCRAP_LEGGINGS
				|| item == Survival.SCRAP_BOOTS;
	}

	public static int pieces(LivingEntity entity) {
		int pieces = 0;
		for (var slot : ExoSockets.ARMOR_SLOTS) {
			if (isPiece(entity.getItemBySlot(slot)) || isPiece(ExoSockets.worn(entity, slot))) {
				pieces++;
			}
		}
		return pieces;
	}

	/** Whether this hit came from a machine or is electric. */
	public static boolean isMachineDamage(DamageSource source) {
		return source.is(Survival.ELECTRIC) || isMachine(source.getEntity()) || isMachine(source.getDirectEntity());
	}

	private static boolean isMachine(Entity entity) {
		return entity != null && entity.is(Survival.MACHINES);
	}

	/** What's left of a hit after the wearer's scrap plating: 1 for anything that isn't a machine's. */
	public static float remaining(LivingEntity entity, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !isMachineDamage(source)) {
			return 1.0f;
		}
		int pieces = pieces(entity);
		if (pieces == 0) {
			return 1.0f;
		}
		return 1.0f - REDUCTION_PER_PIECE * pieces - (pieces >= FULL_SET ? FULL_SET_BONUS : 0.0f);
	}

	/** The plates took some of a machine's hit: sparks off the wearer and a tinny clang. */
	public static void deflect(ServerLevel level, LivingEntity wearer) {
		double y = wearer.getY(0.6);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, wearer.getX(), y, wearer.getZ(), 8, 0.3, 0.4, 0.3, 0.15);
		level.sendParticles(ParticleTypes.CRIT, wearer.getX(), y, wearer.getZ(), 4, 0.25, 0.3, 0.25, 0.1);
		level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(), Survival.SCRAP_DEFLECT, SoundSource.PLAYERS, 0.7f,
				0.9f + level.getRandom().nextFloat() * 0.2f);
	}
}
