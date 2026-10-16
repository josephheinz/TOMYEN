package com.jheinz.tomyen;

import java.util.List;

import net.minecraft.sounds.SoundEvent;

/**
 * How a gun handles. Damage comes from the round, not the gun.
 *
 * @param recoilDegrees  how far each shot kicks the view up
 * @param range          hitscan distance in blocks
 * @param reloadTicks    how long a reload blocks firing
 * @param fireModes      modes the fire-mode key cycles through; the first is the default
 * @param autoTicks      ticks between rounds in full-auto: 4 is about 300 rpm, 2 is 600, 1 (every tick) is 1200
 * @param cycleTicks     minimum ticks between trigger pulls, e.g. working a bolt; 0 for none
 * @param scopeZoom      field-of-view multiplier while sneaking, or 1 for no scope
 * @param singleRounds   fed from an internal cylinder that takes loose rounds one at a time (a loaded
 *                       "magazine", i.e. speedloader, empties into it and stays in the inventory)
 * @param spreadDegrees  how far shots stray from the crosshair at rest (cone radius)
 * @param bloomPerShot   extra spread each shot adds while firing rapidly; it recovers between shots
 */
public record GunStats(SoundEvent sound, float recoilDegrees, double range, int reloadTicks, List<FireMode> fireModes,
		int autoTicks, int cycleTicks, float scopeZoom, boolean singleRounds, float spreadDegrees,
		float bloomPerShot) {
	public GunStats(SoundEvent sound, float recoilDegrees, double range, int reloadTicks, List<FireMode> fireModes) {
		this(sound, recoilDegrees, range, reloadTicks, fireModes, 4, 0, 1.0f, false, 0.5f, 0.6f);
	}

	public GunStats withRecoil(float degrees) {
		return new GunStats(sound, degrees, range, reloadTicks, fireModes, autoTicks, cycleTicks, scopeZoom, singleRounds, spreadDegrees, bloomPerShot);
	}

	public GunStats withFireModes(List<FireMode> modes) {
		return new GunStats(sound, recoilDegrees, range, reloadTicks, modes, autoTicks, cycleTicks, scopeZoom, singleRounds, spreadDegrees, bloomPerShot);
	}

	public GunStats withAutoTicks(int ticks) {
		return new GunStats(sound, recoilDegrees, range, reloadTicks, fireModes, ticks, cycleTicks, scopeZoom, singleRounds, spreadDegrees, bloomPerShot);
	}

	public GunStats withCycleTicks(int ticks) {
		return new GunStats(sound, recoilDegrees, range, reloadTicks, fireModes, autoTicks, ticks, scopeZoom, singleRounds, spreadDegrees, bloomPerShot);
	}

	public GunStats withScope(float zoom) {
		return new GunStats(sound, recoilDegrees, range, reloadTicks, fireModes, autoTicks, cycleTicks, zoom, singleRounds, spreadDegrees, bloomPerShot);
	}

	public GunStats withSingleRoundLoading() {
		return new GunStats(sound, recoilDegrees, range, reloadTicks, fireModes, autoTicks, cycleTicks, scopeZoom, true, spreadDegrees, bloomPerShot);
	}

	public GunStats withSpread(float degrees, float bloom) {
		return new GunStats(sound, recoilDegrees, range, reloadTicks, fireModes, autoTicks, cycleTicks, scopeZoom,
				singleRounds, degrees, bloom);
	}

	public boolean scoped() {
		return scopeZoom < 1.0f;
	}
}
