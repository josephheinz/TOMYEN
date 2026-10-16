package com.jheinz.tomyen.froststeel;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import com.jheinz.tomyen.metal.HintItem;

/**
 * Froststeel's sword and axe. A hit slows the target ({@link #SLOWNESS_TICKS}, Slowness I) and puts
 * {@link #FREEZE_PER_HIT} ticks of powder snow's frost on it, so three quick blows leave it fully frozen: frosted
 * over, slowed further, and taking vanilla's freeze damage. The next blow shatters the ice: it hits
 * {@link #SHATTER_BONUS} harder and thaws the target, and the freezing starts over. Mobs that can't freeze (strays,
 * snow golems, anyone in leather or a full set of froststeel) only get the Slowness.
 */
public class FroststeelWeaponItem extends HintItem {
	private static final int SLOWNESS_TICKS = 60;
	private static final int FREEZE_PER_HIT = 80;
	/** How long past fully frozen hits can pile the frost on (it thaws at two ticks a tick). */
	private static final int FREEZE_HOLD = 40;
	public static final float SHATTER_BONUS = 4.0f;

	public FroststeelWeaponItem(Properties properties, List<String> hints) {
		super(properties, Stream.concat(Stream.of("item.tomyen.froststeel.chills", "item.tomyen.froststeel.shatters"), hints.stream())
				.toList());
	}

	private static boolean frozen(Entity entity) {
		return entity instanceof LivingEntity living && living.canFreeze() && living.isFullyFrozen();
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource damageSource) {
		return frozen(victim) ? SHATTER_BONUS : 0.0f;
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!(attacker.level() instanceof ServerLevel level)) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0), attacker);
		double y = target.getY(0.5);
		if (frozen(target)) {
			target.setTicksFrozen(0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), target.getX(), y,
					target.getZ(), 24, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.15);
			level.playSound(null, target.getX(), y, target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
		} else if (target.canFreeze()) {
			int cap = target.getTicksRequiredToFreeze() + FREEZE_HOLD;
			target.setTicksFrozen(Math.min(cap, Math.max(0, target.getTicksFrozen()) + FREEZE_PER_HIT));
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), y, target.getZ(), 6, target.getBbWidth() * 0.3,
					target.getBbHeight() * 0.25, target.getBbWidth() * 0.3, 0.02);
		}
	}
}
