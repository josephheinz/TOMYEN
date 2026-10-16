package com.jheinz.tomyen.gunsmith;

import java.util.List;
import java.util.Map;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunSpread;
import com.jheinz.tomyen.GunStats;

/**
 * A break-open signal pistol. Its flare is a real, slow projectile (see {@link Flares}): it sets alight whatever it strikes,
 * burns where it lands, and one fired into the sky pops a parachute and drifts down lighting up the ground under it, which at
 * night can draw a passing saucer.
 */
public class FlareGunItem extends GunsmithGunItem {
	public FlareGunItem(Properties properties, GunStats stats, List<Shell> shells, int capacity, Map<FireMode, Handling> handling,
			Traits traits) {
		super(properties, stats, shells, capacity, handling, traits);
	}

	@Override
	protected void discharge(ServerLevel level, Player player, ItemStack gun, Shell shell, int rounds) {
		float aim = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats(gun, player)) : 0.0f;
		float[] dir = scatter(player, player.getXRot(), player.getYRot(), aim);
		Vec3 muzzle = muzzle(player);
		Flares.launch(level, player, muzzle, Vec3.directionFromRotation(dir[0], dir[1]));
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.05, 0.05, 0.05, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.04, 0.04, 0.04, 0.02);
	}
}
