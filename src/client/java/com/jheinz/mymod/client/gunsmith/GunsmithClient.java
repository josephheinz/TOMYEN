package com.jheinz.mymod.client.gunsmith;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import com.jheinz.mymod.attachment.AttachmentItem;
import com.jheinz.mymod.attachment.AttachmentSlot;
import com.jheinz.mymod.attachment.Attachments;
import com.jheinz.mymod.client.GunDisplay;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.gunsmith.Gunsmith;
import com.jheinz.mymod.gunsmith.GunsmithAttachments;
import com.jheinz.mymod.lmg.MachineGunItem;

/**
 * Where things are on the Gunsmith guns and attachments, for the shared gun rendering: first-person display transforms
 * (GunDisplay), hand holds (GunHands), the Lever-Action's optic mount and the hunting scopes' windows (GunAim), the 4x scope's
 * full view (GunScope), and the Nail Gun's magazine for GunAnimation's reload. All the numbers come from GunsmithRigs, which
 * tools/gen_gunsmith.py writes from the models. Also: the bayonet's stab and the bipod's sounds.
 */
public final class GunsmithClient {
	/** The hunting scopes' eyepiece windows relative to their mount: up, and back from its front (tools/gen_attachments.py). */
	private static final float[] SCOPE_2X_WINDOW = {1.25f, 7.3f};
	private static final float[] SCOPE_4X_WINDOW = {1.35f, 9.4f};
	private static final float NO_LIMIT = Float.MAX_VALUE;

	private static boolean wasBraced;

	private GunsmithClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GunsmithAnimation.tick(client.player);
			tickBipod(client.player);
		});
	}

	private static GunDisplay display(float[] d) {
		return new GunDisplay(new Vector3f(d[0], d[1], d[2]), d[3], d[4]);
	}

	public static GunDisplay display(Item gun) {
		if (gun == Gunsmith.LEVER_ACTION) {
			return display(GunsmithRigs.LEVER_ACTION_DISPLAY);
		} else if (gun == Gunsmith.SAWED_OFF) {
			return display(GunsmithRigs.SAWED_OFF_DISPLAY);
		} else if (gun == Gunsmith.SNUB_REVOLVER) {
			return display(GunsmithRigs.SNUB_REVOLVER_DISPLAY);
		} else if (gun == Gunsmith.SLINGSHOT) {
			return display(GunsmithRigs.SLINGSHOT_DISPLAY);
		} else if (gun == Gunsmith.FLARE_GUN) {
			return display(GunsmithRigs.FLARE_GUN_DISPLAY);
		} else if (gun == Gunsmith.HARPOON_GUN) {
			return display(GunsmithRigs.HARPOON_GUN_DISPLAY);
		} else if (gun == Gunsmith.NAIL_GUN) {
			return display(GunsmithRigs.NAIL_GUN_DISPLAY);
		}
		return null;
	}

	public static GunHands.Anchors anchors(Item gun) {
		if (gun == Gunsmith.LEVER_ACTION) {
			return new GunHands.Anchors(GunsmithRigs.LEVER_ACTION_GRIP, GunsmithRigs.LEVER_ACTION_SUPPORT);
		} else if (gun == Gunsmith.SAWED_OFF) {
			return new GunHands.Anchors(GunsmithRigs.SAWED_OFF_GRIP, GunsmithRigs.SAWED_OFF_SUPPORT);
		} else if (gun == Gunsmith.SNUB_REVOLVER) {
			return new GunHands.Anchors(GunsmithRigs.SNUB_REVOLVER_GRIP, GunsmithRigs.SNUB_REVOLVER_SUPPORT);
		} else if (gun == Gunsmith.SLINGSHOT) {
			return new GunHands.Anchors(GunsmithRigs.SLINGSHOT_GRIP, GunsmithRigs.SLINGSHOT_SUPPORT);
		} else if (gun == Gunsmith.FLARE_GUN) {
			return new GunHands.Anchors(GunsmithRigs.FLARE_GUN_GRIP, GunsmithRigs.FLARE_GUN_SUPPORT);
		} else if (gun == Gunsmith.HARPOON_GUN) {
			return new GunHands.Anchors(GunsmithRigs.HARPOON_GUN_GRIP, GunsmithRigs.HARPOON_GUN_SUPPORT);
		} else if (gun == Gunsmith.NAIL_GUN) {
			return new GunHands.Anchors(GunsmithRigs.NAIL_GUN_GRIP, GunsmithRigs.NAIL_GUN_SUPPORT);
		}
		return null;
	}

	/** The Gunsmith guns' optic mounts for GunAim: {front z, top y, furthest eye z}, or null. */
	public static float[] opticMount(Item gun) {
		if (gun == Gunsmith.LEVER_ACTION) {
			return new float[] {GunsmithRigs.LEVER_ACTION_OPTIC.z, GunsmithRigs.LEVER_ACTION_OPTIC.y, NO_LIMIT};
		}
		return null;
	}

	/** The fitted hunting scope's window for GunAim: {up, back} from the mount, or null. */
	public static float[] opticWindow(ItemStack gun) {
		AttachmentItem optic = Attachments.get(gun, AttachmentSlot.OPTIC);
		if (optic == Gunsmith.HUNTING_SCOPE_2X) {
			return SCOPE_2X_WINDOW;
		} else if (optic == Gunsmith.HUNTING_SCOPE_4X) {
			return SCOPE_4X_WINDOW;
		}
		return null;
	}

	/** Whether sneaking looks through this gun's fitted scope full screen, as through the AWP's (the 4x hunting scope). */
	public static boolean fullScope(ItemStack gun) {
		return Attachments.has(gun, Gunsmith.HUNTING_SCOPE_4X);
	}

	/** The Nail Gun's magazine for GunAnimation's reload: where the hand takes it, and its step per frame out of the well. */
	public static Vector3f nailGunMagPoint() {
		return new Vector3f(GunsmithRigs.NAIL_GUN_MAG_BASE_ITEM);
	}

	public static Vector3f nailGunMagStep() {
		return new Vector3f(GunsmithRigs.NAIL_GUN_MAG_STEP_ITEM);
	}

	/** With a bayonet fitted and a mob under the crosshair, punching stabs it instead of reloading. */
	public static boolean bayonetStab(LocalPlayer player) {
		return GunsmithAttachments.hasBayonet(player.getMainHandItem()) && Minecraft.getInstance().hitResult instanceof EntityHitResult;
	}

	/** The bipod's legs swing down as the holder braces (crouches on the ground) and fold as they stand. */
	private static void tickBipod(LocalPlayer player) {
		if (player == null) {
			wasBraced = false;
			return;
		}
		boolean braced = Attachments.has(player.getMainHandItem(), Gunsmith.BIPOD) && MachineGunItem.braced(player);
		if (braced != wasBraced) {
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), braced ? Gunsmith.BIPOD_DEPLOY : Gunsmith.BIPOD_FOLD,
					SoundSource.PLAYERS, 0.6f, 1.0f, false);
		}
		wasBraced = braced;
	}
}
