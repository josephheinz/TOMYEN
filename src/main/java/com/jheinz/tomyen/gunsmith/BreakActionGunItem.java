package com.jheinz.tomyen.gunsmith;

import java.util.List;
import java.util.Map;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;

/**
 * The Sawed-off: two barrels, broken open to load. One barrel per pull, or both at once in {@link FireMode#DOUBLE} (the
 * fire-mode key), a heavier blow with twice the kick and its own report.
 */
public class BreakActionGunItem extends GunsmithGunItem {
	private final SoundEvent doubleShot;

	public BreakActionGunItem(Properties properties, GunStats stats, List<Shell> shells, int capacity,
			Map<FireMode, Handling> handling, Traits traits, SoundEvent doubleShot) {
		super(properties, stats, shells, capacity, handling, traits);
		this.doubleShot = doubleShot;
	}

	@Override
	protected int roundsPerShot(ItemStack gun, int loaded) {
		return fireMode(gun) == FireMode.DOUBLE ? 2 : 1;
	}

	@Override
	protected SoundEvent shotSound(ItemStack gun, int rounds) {
		return rounds > 1 ? doubleShot : stats().sound();
	}
}
