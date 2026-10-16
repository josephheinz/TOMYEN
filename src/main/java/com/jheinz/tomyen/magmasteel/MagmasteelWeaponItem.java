package com.jheinz.tomyen.magmasteel;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.jheinz.tomyen.metal.HintItem;

/**
 * Magmasteel's sword and axe: a hit sets the target alight for {@link #BURN_SECONDS} seconds, as Fire Aspect I would.
 * It counts as one more Fire Aspect level on top of any the weapon carries (with Fire Aspect II it burns for 12
 * seconds rather than 8), since the enchantment's own burn and this one would otherwise just overlap.
 */
public class MagmasteelWeaponItem extends HintItem {
	private static final float BURN_SECONDS = 4.0f;

	public MagmasteelWeaponItem(Properties properties, List<String> hints) {
		super(properties, Stream.concat(Stream.of("item.tomyen.magmasteel.ignites"), hints.stream()).toList());
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!attacker.level().isClientSide() && !target.fireImmune()) {
			target.igniteForSeconds(BURN_SECONDS * (1 + fireAspectLevel(stack)));
		}
	}

	private static int fireAspectLevel(ItemInstance stack) {
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (var entry : enchantments.entrySet()) {
			if (entry.getKey().is(Enchantments.FIRE_ASPECT)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}
}
