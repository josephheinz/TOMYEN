package com.jheinz.mymod;

import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Damage types are data-driven; these keys point at data/mymod/damage_type/*.json. */
public final class ModDamageTypes {
	public static final ResourceKey<DamageType> BULLET = key("bullet");
	public static final ResourceKey<DamageType> PENETRATOR_BULLET = key("penetrator_bullet");
	public static final ResourceKey<DamageType> ARMOR_PIERCING_BULLET = key("armor_piercing_bullet");
	public static final ResourceKey<DamageType> HOLLOW_POINT_BULLET = key("hollow_point_bullet");
	public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
	public static final ResourceKey<DamageType> HOLY_LIGHT = key("holy_light");

	/** Armor stops bullets better than melee: armor and toughness count this much more against them. */
	private static final float BULLET_ARMOR_BONUS = 1.5f;

	/** How well armor works against each round, before {@link #BULLET_ARMOR_BONUS}. */
	private static final Map<ResourceKey<DamageType>, Float> ARMOR_EFFECTIVENESS = Map.of(
			BULLET, 1.0f,
			PENETRATOR_BULLET, 0.5f,
			ARMOR_PIERCING_BULLET, 0.15f,
			HOLLOW_POINT_BULLET, 2.0f);

	private ModDamageTypes() {
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id(name));
	}

	public static boolean isBullet(DamageSource source) {
		return source.typeHolder().unwrapKey().map(ARMOR_EFFECTIVENESS::containsKey).orElse(false);
	}

	/**
	 * Hollow points flatten against armor: after the normal (capped) armor reduction, armor reduces
	 * them a second time, so heavy armor stops them almost completely.
	 */
	public static boolean deformsOnArmor(DamageSource source) {
		return source.is(HOLLOW_POINT_BULLET);
	}

	/**
	 * How much of an armor plate's protection applies against this source: rounds that get through
	 * armor get through plates too. 0 for non-bullets.
	 */
	public static float plateEffectiveness(DamageSource source) {
		return source.typeHolder().unwrapKey()
				.map(ARMOR_EFFECTIVENESS::get)
				.map(effectiveness -> Math.min(effectiveness, 1.0f))
				.orElse(0.0f);
	}

	/** Multiplier for the target's armor and toughness against this source; 1 for non-bullets. */
	public static float armorMultiplier(DamageSource source) {
		return source.typeHolder().unwrapKey()
				.map(ARMOR_EFFECTIVENESS::get)
				.map(effectiveness -> effectiveness * BULLET_ARMOR_BONUS)
				.orElse(1.0f);
	}
}
