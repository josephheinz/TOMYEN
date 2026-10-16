package com.jheinz.tomyen.silver;

import java.util.Map;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.ModItems;

/**
 * Consecration: silver lying on the ground that crucifix light touches (a light orb passing through or bursting on
 * it, or a beam crossing or striking it; see LightOrb and HolyLight) turns holy, the whole stack at once, with a
 * choir-like swell, a burst of golden light and sparkles rising off it. Ingots, nuggets, blocks and silver rounds
 * all turn; silver gear doesn't (holy silver gear is made from holy silver).
 */
public final class HolySilver {
	private static final Map<Item, Item> CONSECRATED = Map.of(
			Silver.SILVER_INGOT, Silver.HOLY_SILVER_INGOT,
			Silver.SILVER_NUGGET, Silver.HOLY_SILVER_NUGGET,
			Silver.SILVER_BLOCK.asItem(), Silver.HOLY_SILVER_BLOCK.asItem(),
			ModItems.BULLET_556_SILVER, ModItems.BULLET_556_HOLY_SILVER,
			ModItems.SHELL_12G_SILVER_BUCKSHOT, ModItems.SHELL_12G_HOLY_SILVER_BUCKSHOT);

	private static final int GOLD = 0xFFD54A;
	private static final int PALE_GOLD = 0xFFF3C4;

	private HolySilver() {
	}

	/** The holy form of a silver item, or null if it has none. */
	public static Item consecrated(Item silver) {
		return CONSECRATED.get(silver);
	}

	/** Turns a dropped stack of silver holy, if it is silver. Returns whether it did. */
	public static boolean consecrate(ServerLevel level, ItemEntity entity) {
		ItemStack stack = entity.getItem();
		Item holy = consecrated(stack.getItem());
		if (holy == null || !entity.isAlive()) {
			return false;
		}
		entity.setItem(stack.transmuteCopy(holy));
		// It lifts a little, as if blessed.
		entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, 0.25, 0.0));

		double x = entity.getX(), y = entity.getY() + 0.2, z = entity.getZ();
		level.sendParticles(new DustParticleOptions(GOLD, 1.4f), x, y, z, 24, 0.3, 0.25, 0.3, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 12; i++) {
			// Count 0 sends one particle moving along the offset: straight up, slowly.
			level.sendParticles(ParticleTypes.END_ROD, x + (level.getRandom().nextDouble() - 0.5) * 0.6, y,
					z + (level.getRandom().nextDouble() - 0.5) * 0.6, 0, 0.0, 1.0, 0.0, 0.08 + level.getRandom().nextDouble() * 0.08);
		}
		level.sendParticles(new DustParticleOptions(PALE_GOLD, 0.8f), x, y + 0.6, z, 16, 0.2, 0.6, 0.2, 0.0);
		level.playSound(null, x, y, z, Silver.CONSECRATE, SoundSource.NEUTRAL, 1.2f, 1.0f);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5f, 1.3f);
		return true;
	}
}
