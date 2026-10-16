package com.jheinz.tomyen;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/**
 * A round a gun or magazine accepts.
 *
 * @param color       RGB used for this round in the ammo HUD and magazine fill bar
 * @param pierce      how many more targets a hit carries on through into (each takes the full damage); walls still stop it
 * @param burnSeconds sets whatever it hits alight for this long; 0 for an ordinary round
 */
public record Ammo(Item item, float damage, ResourceKey<DamageType> damageType, int color, int pierce, float burnSeconds) {
	public Ammo(Item item, float damage, ResourceKey<DamageType> damageType, int color) {
		this(item, damage, damageType, color, 0, 0.0f);
	}

	public static Ammo find(List<Ammo> ammo, Holder<Item> item) {
		for (Ammo round : ammo) {
			if (item.value() == round.item()) {
				return round;
			}
		}
		return null;
	}
}
