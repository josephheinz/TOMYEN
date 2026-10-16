package com.jheinz.mymod.herbalism;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Wolfsbane against werewolves. A blade rubbed with Wolfsbane Paste carries {@value #COATING_HITS} coated hits (the
 * {@code mymod:wolfsbane_coating} component); Wolfsbane Arrows are always coated. A coated hit on a werewolf goes
 * straight through its hide (as silver does) and does {@value #MULTIPLIER} times the damage on top, and the poison
 * slows it for a moment. Only hits on werewolves use up a blade's coating.
 */
public final class Wolfsbane {
	public static final int COATING_HITS = 16;
	/** Coatings stack up to this many hits. */
	public static final int MAX_COATING = 48;
	public static final float MULTIPLIER = 1.5f;
	private static final int SLOW_TICKS = 60;
	private static final int COLOR = 0x6A3FA0;

	private Wolfsbane() {
	}

	/** Hits of coating left on a weapon (0 if none). */
	public static int coating(ItemStack stack) {
		Integer hits = stack.get(Herbalism.WOLFSBANE_COATING);
		return hits == null ? 0 : hits;
	}

	/** Whether this item takes a coating: anything that's a weapon (swords, axes, spears, crowbars and the like). */
	public static boolean canCoat(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.WEAPON);
	}

	/**
	 * A hit landing on a werewolf: the damage multiplier wolfsbane gives it (1 if the hit isn't coated). A coated
	 * blade's coating loses a hit; the werewolf is slowed and gives off violet motes.
	 */
	public static float strike(ServerLevel level, LivingEntity werewolf, DamageSource source) {
		if (!isCoated(source)) {
			return 1.0f;
		}
		werewolf.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1));
		level.sendParticles(new DustParticleOptions(COLOR, 1.0f), werewolf.getX(), werewolf.getY(0.6), werewolf.getZ(), 10,
				werewolf.getBbWidth() * 0.4, werewolf.getBbHeight() * 0.3, werewolf.getBbWidth() * 0.4, 0.0);
		return MULTIPLIER;
	}

	/** Whether this hit carries wolfsbane, using up a hit of a blade's coating if it does. */
	private static boolean isCoated(DamageSource source) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			return arrow.getPickupItemStackOrigin().is(Herbalism.WOLFSBANE_ARROW);
		}
		// A blow struck by hand, not a projectile thrown or fired by someone holding a coated blade.
		if (source.getDirectEntity() == null || source.getDirectEntity() != source.getEntity()) {
			return false;
		}
		@Nullable ItemStack weapon = source.getWeaponItem();
		int hits = weapon == null ? 0 : coating(weapon);
		if (hits <= 0) {
			return false;
		}
		if (hits > 1) {
			weapon.set(Herbalism.WOLFSBANE_COATING, hits - 1);
		} else {
			weapon.remove(Herbalism.WOLFSBANE_COATING);
		}
		return true;
	}

	/** The coating's tooltip line on a weapon. */
	static TooltipProvider tooltip(Integer hits) {
		return (Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) ->
				tooltip.accept(Component.translatable("tooltip.mymod.wolfsbane_coating", hits).withStyle(ChatFormatting.DARK_PURPLE));
	}
}
