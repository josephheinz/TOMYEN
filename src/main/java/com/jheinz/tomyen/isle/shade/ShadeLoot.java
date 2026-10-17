package com.jheinz.tomyen.isle.shade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.banshee.Banshees;
import com.jheinz.tomyen.soul.Souls;

/**
 * What a beaten shade leaves (only when a player beat it), deeper ones more. Until the Soul Wisp and Shade Heart items
 * are built (they're on the backlog), shades drop the soul line's existing materials:
 *
 * <ul>
 * <li>a tide shade: Ectoplasm, 20% of the time (+10% a depth);
 * <li>an echo: 1-2 Ectoplasm (more deeper, and one more if it had a name), and sometimes a little more;
 * <li>a boss's shade: 4-6 Ectoplasm plus one a depth, 2-4 more, and a Soul Charge (two in the Abyss);
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
					shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM));
				}
			}
			case ECHO -> {
				int wisps = 1 + random.nextInt(2) + d / 2 + (shade.record() != null && shade.record().name().isPresent() ? 1 : 0);
				shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM, wisps));
				if (random.nextFloat() < 0.15f + 0.05f * d) {
					shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM));
				}
			}
			case BOSS -> {
				shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM, 4 + random.nextInt(3) + d));
				shade.spawnAtLocation(level, new ItemStack(Banshees.ECTOPLASM, 2 + random.nextInt(3)));
				shade.spawnAtLocation(level, new ItemStack(Souls.SOUL_CHARGE, d >= 2 ? 2 : 1));
			}
			case PET -> {
			}
		}
	}
}
