package com.jheinz.mymod;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/**
 * A round a gun or magazine accepts.
 *
 * @param color RGB used for this round in the ammo HUD and magazine fill bar
 */
public record Ammo(Item item, float damage, ResourceKey<DamageType> damageType, int color) {
	public static Ammo find(List<Ammo> ammo, Holder<Item> item) {
		for (Ammo round : ammo) {
			if (item.value() == round.item()) {
				return round;
			}
		}
		return null;
	}
}
