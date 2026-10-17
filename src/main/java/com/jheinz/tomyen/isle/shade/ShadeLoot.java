package com.jheinz.tomyen.isle.shade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.banshee.Banshees;
import com.jheinz.tomyen.soul.Souls;

/**
 * What a beaten shade leaves (only when a player beat it), deeper ones more. Soul Wisps mostly (three and a soul sand
 * or soul soil make a Soul Charge, and they're a gravestone offering), with a little Ectoplasm off the stronger ones:
 *
 * <ul>
 * <li>a tide shade: a Soul Wisp, 20% of the time (+10% a depth);
 * <li>an echo: 1-2 Soul Wisps (more deeper, and one more if it had a name), sometimes one more, and now and then an
 * Ectoplasm;
 * <li>a boss's shade: 4-6 Soul Wisps plus one a depth, 1-2 Ectoplasm, a Soul Charge (two in the Abyss), and its
 * Shade Heart, the only way to get one (offered at your gravestone, it draws your hoard up a depth);
 * <li>a pet's: nothing (it can't be hurt anyway).
 * </ul>
 */
public final class ShadeLoot {
	private ShadeLoot() {
	}

	static void drop(ServerLevel level, Shade shade) {
		RandomSource random = shade.getRandom();
		int d = shade.depth().index;
		switch (shade.kind()) {
			case TIDE -> {
				if (random.nextFloat() < 0.2f + 0.1f * d) {
					shade.spawnAtLocation(level, new ItemStack(IsleShades.SOUL_WISP));
				}
			}
			case ECHO -> {
				int wisps = 1 + random.nextInt(2) + d / 2 + (shade.record() != null && shade.record().name().isPresent() ? 1 : 0);
				if (random.nextFloat() < 0.15f + 0.05f * d) {
					wisps++;
				}
				shade.spawnAtLocation(level, new ItemStack(IsleShades.SOUL_WISP, wisps));
				if (random.nextFloat() < 0.25f) {
					shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM));
				}
			}
			case BOSS -> {
				shade.spawnAtLocation(level, new ItemStack(IsleShades.SOUL_WISP, 4 + random.nextInt(3) + d));
				shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM, 1 + random.nextInt(2)));
				shade.spawnAtLocation(level, new ItemStack(Souls.SOUL_CHARGE, d >= 2 ? 2 : 1));
				shade.spawnAtLocation(level, new ItemStack(IsleShades.SHADE_HEART));
			}
			case PET -> {
			}
		}
	}
}
