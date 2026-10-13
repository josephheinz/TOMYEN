package com.jheinz.mymod.client.shoddy;

import org.joml.Vector3f;

import net.minecraft.world.item.Item;

import com.jheinz.mymod.client.GunDisplay;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.client.shotgun.ShotgunAnimation;
import com.jheinz.mymod.shoddy.ShoddyGuns;

/**
 * Where things are on the homemade guns, for the shared gun rendering: first-person display transforms (GunDisplay),
 * hand holds (GunHands) and the moving parts and ports their handling animates (ShotgunAnimation). All printed by
 * tools/gen_shoddy.py; move them together with the models.
 */
public final class ShoddyGunClient {
	private static final ShotgunAnimation.Geometry PIPE_PISTOL = new ShotgunAnimation.Geometry(
			new Vector3f(8.0f, 6.4f, 8.4f), new Vector3f(8.0f, 9.0f, 8.8f), new Vector3f(8.0f, 10.0f, 9.0f), 2.2f,
			new Vector3f(8.0f, 10.0f, 9.8f), false, new Vector3f(8.0f, 10.0f, -30.0f));
	private static final ShotgunAnimation.Geometry PIPE_RIFLE = new ShotgunAnimation.Geometry(
			new Vector3f(8.0f, 7.9f, -3.0f), new Vector3f(8.0f, 11.0f, 7.6f), new Vector3f(9.6f, 10.6f, 7.6f), 3.0f,
			new Vector3f(11.0f, 10.0f, 9.7f), true, new Vector3f(6.0f, 14.0f, -25.0f));
	// Loaded down the muzzle with the gun tipped up; the ramrod runs out forward, so its travel is negative.
	private static final ShotgunAnimation.Geometry BLUNDERBUSS = new ShotgunAnimation.Geometry(
			new Vector3f(8.0f, 6.9f, -2.5f), new Vector3f(8.0f, 9.6f, -14.6f), new Vector3f(8.0f, 9.6f, 5.0f), -5.0f,
			new Vector3f(8.0f, 7.2f, -9.6f), false, new Vector3f(40.0f, 18.0f, -8.0f));
	private static final ShotgunAnimation.Geometry JUNK_REPEATER = new ShotgunAnimation.Geometry(
			new Vector3f(8.0f, 9.1f, -3.0f), new Vector3f(8.0f, 11.6f, 6.2f), new Vector3f(9.5f, 10.3f, 6.5f), 2.6f,
			new Vector3f(8.0f, 12.45f, 8.7f), true, new Vector3f(8.0f, 12.0f, -30.0f));

	private ShoddyGunClient() {
	}

	public static GunDisplay display(Item gun) {
		if (gun == ShoddyGuns.PIPE_PISTOL) {
			return new GunDisplay(new Vector3f(-0.293f, 3.136f, -3.155f), -6, 0.65f);
		} else if (gun == ShoddyGuns.PIPE_RIFLE) {
			return new GunDisplay(new Vector3f(0.015f, 2.124f, -0.118f), -8, 0.55f);
		} else if (gun == ShoddyGuns.BLUNDERBUSS) {
			return new GunDisplay(new Vector3f(-0.252f, 2.144f, 1.783f), -8, 0.6f);
		} else if (gun == ShoddyGuns.JUNK_REPEATER) {
			return new GunDisplay(new Vector3f(-0.199f, 2.839f, 1.407f), -8, 0.55f);
		}
		return null;
	}

	public static GunHands.Anchors anchors(Item gun) {
		if (gun == ShoddyGuns.PIPE_PISTOL) {
			// Like the other handguns: the off hand cups the shooting hand.
			return anchors(-0.020f, 0.131f, -0.181f, -0.090f, 0.101f, -0.151f);
		} else if (gun == ShoddyGuns.PIPE_RIFLE) {
			return anchors(-0.022f, 0.119f, 0.156f, 0.054f, 0.129f, -0.382f);
		} else if (gun == ShoddyGuns.BLUNDERBUSS) {
			return anchors(-0.022f, 0.119f, 0.156f, 0.039f, 0.093f, -0.279f);
		} else if (gun == ShoddyGuns.JUNK_REPEATER) {
			return anchors(-0.022f, 0.119f, 0.156f, 0.040f, 0.215f, -0.287f);
		}
		return null;
	}

	private static GunHands.Anchors anchors(float gx, float gy, float gz, float sx, float sy, float sz) {
		return new GunHands.Anchors(new Vector3f(gx, gy, gz), new Vector3f(sx, sy, sz));
	}

	public static ShotgunAnimation.Geometry geometry(Item gun) {
		if (gun == ShoddyGuns.PIPE_PISTOL) {
			return PIPE_PISTOL;
		} else if (gun == ShoddyGuns.PIPE_RIFLE) {
			return PIPE_RIFLE;
		} else if (gun == ShoddyGuns.BLUNDERBUSS) {
			return BLUNDERBUSS;
		} else if (gun == ShoddyGuns.JUNK_REPEATER) {
			return JUNK_REPEATER;
		}
		return null;
	}
}
