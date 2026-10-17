package com.jheinz.tomyen.silver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.holy.HolyLight;

/**
 * Silver's bite against the evil (see {@link HolyLight#EVIL}): a weapon or round carrying this hits evil targets
 * {@code multiplier} times as hard. Holy silver also sets them alight with holy fire. Melee weapons get it through
 * EnchantmentHelperEvilBaneMixin (so it shows as the enchanted part of the hit), rounds in GunItem and ShotgunItem.
 */
public record EvilBane(float multiplier, boolean holy) {
	public static final Codec<EvilBane> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.fieldOf("multiplier").forGetter(EvilBane::multiplier),
			Codec.BOOL.optionalFieldOf("holy", false).forGetter(EvilBane::holy)
	).apply(instance, EvilBane::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, EvilBane> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, EvilBane::multiplier,
			ByteBufCodecs.BOOL, EvilBane::holy,
			EvilBane::new);

	/** Silver: twice the damage to the evil. */
	public static final EvilBane SILVER = new EvilBane(2.0f, false);
	/** Holy silver: three times, like holy light on the holy-vulnerable, and holy fire. */
	public static final EvilBane HOLY_SILVER = new EvilBane(3.0f, true);

	private static final int HOLY_FIRE_TICKS = 60;
	private static final int HOLY_FIRE_COLOR = 0xFFD86B;

	public static @Nullable EvilBane of(ItemStack stack) {
		return stack.get(ModComponents.EVIL_BANE);
	}

	public static @Nullable EvilBane of(Item item) {
		return item.components().get(ModComponents.EVIL_BANE);
	}

	/** Damage multiplier for a round of this item hitting the target: 1 unless the round is silver and the target evil. */
	public static float multiplier(Item round, Entity target) {
		EvilBane bane = of(round);
		if (bane != null && target instanceof Wary wary) {
			wary.struckBy(bane);
		}
		return bane != null && HolyLight.isEvil(target) ? bane.multiplier() : 1.0f;
	}

	/**
	 * Whether a hit is silver or holy: holy light, or a blow from a weapon carrying this component. A round's damage
	 * source only knows the gun it came from, so a {@link Wary} target is told about a silver round just before it lands.
	 */
	public static boolean isSilverOrHoly(DamageSource source) {
		if (source.is(ModDamageTypes.HOLY_LIGHT)) {
			return true;
		}
		ItemStack weapon = source.getWeaponItem();
		return weapon != null && of(weapon) != null;
	}

	/** Something that shrugs off everything but silver and holy things (a werewolf), and so needs to know which is which. */
	public interface Wary {
		/** A silver round is about to hit it; called just before its damage is dealt. */
		void struckBySilver();

		/** A round of this bane is about to hit it (holy or not, see {@link #holy}); by default, as any silver. */
		default void struckBy(EvilBane bane) {
			struckBySilver();
		}
	}

	/** After a round of this item hits: holy silver sets an evil target alight. */
	public static void afterHit(ServerLevel level, Item round, Entity target) {
		EvilBane bane = of(round);
		if (bane != null) {
			bane.strike(level, target);
		}
	}

	/** Holy silver sets an evil target alight with holy fire; plain silver just glints. Nothing for anything else. */
	public void strike(ServerLevel level, Entity target) {
		if (!HolyLight.isEvil(target)) {
			return;
		}
		double y = target.getY(0.5);
		if (holy) {
			target.igniteForTicks(Math.max(target.getRemainingFireTicks(), HOLY_FIRE_TICKS));
			level.sendParticles(new DustParticleOptions(HOLY_FIRE_COLOR, 1.0f), target.getX(), y, target.getZ(), 14,
					target.getBbWidth() * 0.4, target.getBbHeight() * 0.35, target.getBbWidth() * 0.4, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, target.getX(), y, target.getZ(), 8, 0.2, 0.4, 0.2, 0.06);
		} else {
			level.sendParticles(ParticleTypes.WAX_OFF, target.getX(), y, target.getZ(), 5, 0.25, 0.4, 0.25, 0.4);
		}
	}
}
