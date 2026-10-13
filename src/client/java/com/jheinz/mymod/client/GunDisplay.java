package com.jheinz.mymod.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.world.item.Item;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.client.shoddy.ShoddyGunClient;
import com.jheinz.mymod.energy.EnergyWeapons;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.minigun.Miniguns;
import com.jheinz.mymod.napalm.Napalm;

/**
 * A gun's first-person display transform, copied from its item model (both hands use the same one;
 * vanilla mirrors it for the left). Lets code place things on the gun model, in model pixels, in the
 * space the held item's pose is in.
 *
 * @param translation in pixels
 * @param yaw         in degrees (every gun's display rotation is a yaw only)
 */
public record GunDisplay(Vector3f translation, float yaw, float scale) {
	public static GunDisplay of(Item gun) {
		GunDisplay shoddy = ShoddyGunClient.display(gun);
		if (shoddy != null) {
			return shoddy;
		} else if (gun == ModItems.GLOCK) {
			return display(0.0f, 3.0f, -6.0f, -6, 0.65f);
		} else if (gun == ModItems.M1911) {
			return display(-0.56f, 3.08f, -0.71f, -6, 0.65f);
		} else if (gun == ModItems.REVOLVER_357) {
			return display(-0.46f, 3.5f, -1.66f, -6, 0.65f);
		} else if (gun == ModItems.MAC10) {
			return display(-0.73f, 3.71f, 0.98f, -6, 0.65f);
		} else if (gun == ModItems.MP5) {
			return display(-0.45f, 4.16f, 0.21f, -8, 0.62f);
		} else if (gun == ModItems.M4) {
			return display(0.0f, 4.0f, 0.0f, -8, 0.6f);
		} else if (gun == ModItems.AKM) {
			return display(0.0f, 4.0f, -3.0f, -8, 0.6f);
		} else if (gun == ModItems.GALIL) {
			return display(-0.18f, 4.0f, -1.69f, -8, 0.6f);
		} else if (gun == Launchers.RPG7) {
			return display(-1.4f, 3.3f, -1.0f, -7, 0.65f);
		} else if (gun == ModItems.AWP) {
			return display(-0.35f, 3.85f, -0.53f, -8, 0.5f);
		} else if (gun == ModItems.M249) {
			// tools/gen_m249.py and gen_shotguns.py print these.
			return display(0.233f, 3.904f, -1.663f, -8, 0.5f);
		} else if (gun == ModItems.REMINGTON_870) {
			return display(0.291f, 2.289f, -2.079f, -8, 0.55f);
		} else if (gun == ModItems.SPAS12) {
			return display(0.179f, 3.547f, -1.283f, -8, 0.53f);
		} else if (gun == Napalm.FLAMETHROWER) {
			// tools/gen_napalm.py prints this.
			return display(0.0f, 2.894f, -0.009f, -8, 0.55f);
		} else if (gun == Miniguns.MINIGUN) {
			// tools/gen_minigun.py prints this.
			return display(-0.455f, 1.654f, -5.969f, -6, 0.5f);
		} else if (gun == EnergyWeapons.LASER_RIFLE) {
			// tools/gen_energy_weapons.py prints these.
			return display(-0.245f, 3.004f, 1.733f, -8, 0.55f);
		} else if (gun == EnergyWeapons.RAILGUN) {
			return display(-0.255f, 3.104f, 1.803f, -8, 0.5f);
		} else if (gun == EnergyWeapons.RAY_GUN) {
			return display(-0.503f, 2.746f, -1.151f, -6, 0.65f);
		}
		return null;
	}

	private static GunDisplay display(float tx, float ty, float tz, float yaw, float scale) {
		return new GunDisplay(new Vector3f(tx, ty, tz), yaw, scale);
	}

	/** What vanilla applies after the held item's pose: model blocks (pixels / 16) to item pose space. */
	public Matrix4f matrix(boolean mirrored) {
		float side = mirrored ? -1.0f : 1.0f;
		return new Matrix4f()
				.translate(translation.x / 16.0f * side, translation.y / 16.0f, translation.z / 16.0f)
				.rotateY((float) Math.toRadians(yaw * side))
				.scale(scale)
				.translate(-0.5f, -0.5f, -0.5f);
	}
}
