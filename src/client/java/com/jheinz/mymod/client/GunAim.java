package com.jheinz.mymod.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.energy.EnergyWeapons;
import com.jheinz.mymod.attachment.AttachmentItem;
import com.jheinz.mymod.attachment.AttachmentSlot;
import com.jheinz.mymod.attachment.Attachments;

/**
 * Aiming down a fitted optic (red dot, holographic or Kobra): sneaking brings the gun up to the middle of
 * the screen, pointing straight ahead, with the optic's window centred in front of the eye. Each optic's
 * reticle is part of its model, glowing on the glass, so it moves with the gun under recoil and sway.
 * Walking doesn't bob the view while aiming (see GameRendererAimBobMixin), and the gun kicks less. The M249 and
 * the shotguns can also be aimed with no optic, down their iron sights, with a slight zoom.
 *
 * <p>The aimed pose is worked out from each gun's model: where the optic mount is (as placed by
 * tools/gen_attachments.py), how far back the eye can sit before anything gets in the way, and the gun's
 * first-person display transform ({@link GunDisplay}), which vanilla applies after this pose.
 */
public final class GunAim {
	private static final long RAISE_MILLIS = 160;
	/** The M249 is heavy to bring up; the shotguns a little slower than a rifle. */
	private static final long M249_RAISE_MILLIS = 450;
	private static final long SHOTGUN_RAISE_MILLIS = 220;
	/** Field-of-view multiplier aiming down iron sights (only guns that define a sight line below can). */
	public static final float IRON_SIGHT_ZOOM = 0.9f;
	/** How far behind the window the eye sits when nothing's in the way, in model pixels. */
	private static final float EYE_DISTANCE = 6.0f;
	private static final float NO_LIMIT = Float.MAX_VALUE;

	/**
	 * Where a gun's optic mount is.
	 *
	 * @param front  front of the mount along the barrel (z), in model pixels
	 * @param top    the surface the optic sits on (y)
	 * @param maxEye the furthest back (z) the eye can sit with a clear view through the window (the iron sights
	 *               come off while an optic is fitted, so only a hammer gets in the way)
	 */
	private record Mount(float front, float top, float maxEye) {
	}

	/** Where an optic's window centre is relative to its mount (see tools/gen_attachments.py). */
	private record Optic(float windowUp, float windowBack) {
	}

	private static float progress;
	private static long lastMillis;

	private GunAim() {
	}

	private static Mount mount(Item gun) {
		if (gun == ModItems.GLOCK) {
			return new Mount(9.8f, 11.2f, NO_LIMIT);
		} else if (gun == ModItems.M1911) {
			return new Mount(1.8f, 11.0f, 7.2f);
		} else if (gun == ModItems.REVOLVER_357) {
			return new Mount(-0.6f, 11.7f, 5.6f);
		} else if (gun == ModItems.MAC10) {
			return new Mount(-0.5f, 10.4f, NO_LIMIT);
		} else if (gun == ModItems.MP5) {
			return new Mount(3.0f, 10.3f, NO_LIMIT);
		} else if (gun == ModItems.M4) {
			return new Mount(5.0f, 10.4f, NO_LIMIT);
		} else if (gun == ModItems.AKM) {
			return new Mount(10.0f, 10.2f, NO_LIMIT);
		} else if (gun == ModItems.GALIL) {
			return new Mount(3.3f, 10.2f, NO_LIMIT);
		} else if (gun == ModItems.M249) {
			// On the feed cover's rail (tools/gen_m249.py).
			return new Mount(9.6f, 11.4f, NO_LIMIT);
		} else if (gun == ModItems.REMINGTON_870) {
			return new Mount(7.4f, 10.1f, NO_LIMIT);
		} else if (gun == ModItems.SPAS12) {
			return new Mount(7.5f, 10.6f, NO_LIMIT);
		} else if (gun == EnergyWeapons.LASER_RIFLE) {
			// Its built-in sight (tools/gen_energy_weapons.py).
			return new Mount(4.0f, 11.4f, NO_LIMIT);
		} else if (gun == EnergyWeapons.RAILGUN) {
			// Its holo sight's base (tools/gen_energy_weapons.py). Sneaking normally looks through the railgun's scope instead.
			return new Mount(4.6f, 14.4f, NO_LIMIT);
		}
		return null;
	}

	/**
	 * The sight line of a gun that can be aimed down its iron sights, as a window relative to its optic mount: the
	 * rear aperture (or, on the 870, the back of the receiver over which the bead is lined up), from
	 * tools/gen_m249.py and gen_shotguns.py. Null for guns that need an optic to aim.
	 */
	private static Optic ironSights(Item gun) {
		if (gun == ModItems.M249) {
			// The middle of the rear ghost ring; the front post's tip is on the same line.
			return new Optic(0.55f, 8.5f);
		} else if (gun == ModItems.REMINGTON_870) {
			return new Optic(0.05f, 6.8f);
		} else if (gun == ModItems.SPAS12) {
			return new Optic(0.8f, 6.6f);
		} else if (gun == EnergyWeapons.LASER_RIFLE) {
			// The middle of its sight's window.
			return new Optic(1.4f, 0.2f);
		} else if (gun == EnergyWeapons.RAILGUN) {
			// The middle of its holo sight's glass.
			return new Optic(1.2f, 0.8f);
		}
		return null;
	}

	/** Whether this gun can be aimed down its iron sights (without an optic). */
	public static boolean hasIronSights(ItemStack gun) {
		return ironSights(gun.getItem()) != null;
	}

	private static Optic optic(ItemStack gun) {
		AttachmentItem optic = Attachments.get(gun, AttachmentSlot.OPTIC);
		if (optic == ModItems.RED_DOT_SIGHT) {
			return new Optic(0.95f, 0.35f);
		} else if (optic == ModItems.HOLOGRAPHIC_SIGHT) {
			return new Optic(1.275f, 1.4f);
		} else if (optic == ModItems.KOBRA_SIGHT) {
			return new Optic(1.05f, 1.2f);
		}
		return optic == null ? ironSights(gun.getItem()) : null;
	}

	/** Whether this gun has an optic to aim down. */
	public static boolean hasOptic(ItemStack gun) {
		return optic(gun) != null;
	}

	/** Whether the player is trying to aim down an optic right now. */
	private static boolean wantsToAim(LocalPlayer player) {
		ItemStack held = player.getMainHandItem();
		// A reload, rack or inspection takes the gun out of the shoulder.
		return GunScope.zoom(player) > 0.0f && !GunScope.throughScope(player) && optic(held) != null
				&& mount(held.getItem()) != null && !GunAnimation.busy(held);
	}

	/**
	 * Eases the gun up or down; call each frame (calling again in the same frame does nothing more). Returns
	 * how far up it is, 0 to 1.
	 */
	public static float update(LocalPlayer player) {
		long now = Util.getMillis();
		Item held = player == null ? null : player.getMainHandItem().getItem();
		long raise = held == ModItems.M249 ? M249_RAISE_MILLIS
				: held == ModItems.REMINGTON_870 || held == ModItems.SPAS12 ? SHOTGUN_RAISE_MILLIS : RAISE_MILLIS;
		float step = Math.min(now - lastMillis, 100L) / (float) raise;
		lastMillis = now;
		progress = Mth.clamp(progress + (player != null && wantsToAim(player) ? step : -step), 0.0f, 1.0f);
		GunRecoil.setAiming(progress);
		return progress;
	}

	/** How far up the gun is, as of the last frame. */
	public static float progress() {
		return progress;
	}

	/**
	 * Blends the held item's pose (before its display transform) toward the aimed pose by {@code amount}.
	 *
	 * @param view the pose first-person rendering starts from: view space, turning with the camera
	 */
	public static void apply(PoseStack poseStack, PoseStack.Pose view, ItemStack stack, boolean mirrored, float amount) {
		Mount mount = mount(stack.getItem());
		Optic optic = optic(stack);
		if (mount == null || optic == null || amount <= 0.0f) {
			return;
		}
		GunDisplay gunDisplay = GunDisplay.of(stack.getItem());
		float s = gunDisplay.scale();
		// What vanilla does next: the display transform, then the model's pixels.
		Matrix4f display = gunDisplay.matrix(mirrored);
		// Where the model should end up: square to the view, the window dead ahead of the eye. Every optic sits on
		// the gun's centreline (x = 8).
		Vector3f window = new Vector3f(8.0f, mount.top() + optic.windowUp(), mount.front() + optic.windowBack());
		float eyeDistance = Math.min(EYE_DISTANCE, mount.maxEye() - window.z);
		Matrix4f aimed = new Matrix4f()
				.translation(0.0f, 0.0f, -eyeDistance * s / 16.0f)
				.scale(s)
				.translate(-window.x / 16.0f, -window.y / 16.0f, -window.z / 16.0f);
		Matrix4f target = new Matrix4f(view.pose()).mul(aimed).mul(display.invert());

		Matrix4f current = poseStack.last().pose();
		float t = amount * amount * (3.0f - 2.0f * amount);
		Vector3f position = current.getTranslation(new Vector3f()).lerp(target.getTranslation(new Vector3f()), t);
		Quaternionf rotation = current.getNormalizedRotation(new Quaternionf())
				.slerp(target.getNormalizedRotation(new Quaternionf()), t);
		Matrix4f blended = new Matrix4f().translation(position).rotate(rotation);
		poseStack.last().pose().set(blended);
		poseStack.last().normal().set(new Matrix3f(blended));
	}
}
