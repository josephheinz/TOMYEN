package com.jheinz.mymod.client.shotgun;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.GunAnimation;
import com.jheinz.mymod.client.GunDisplay;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.client.GunMotion;
import com.jheinz.mymod.client.GunScope;
import com.jheinz.mymod.shotgun.ShotgunAction;
import com.jheinz.mymod.shotgun.ShotgunItem;
import com.jheinz.mymod.shotgun.Shotguns;

/**
 * The shotguns' handling, played from the {@link ShotgunAction} on the stack:
 * <ul>
 * <li>Working the pump after a shot: the kick settles, the off hand rides the fore-end back (the bolt opens in the
 * port and the fired hull flies out to the right) and slams it forward again.</li>
 * <li>A semi-auto cycle: the hull flies out with no hand movement.</li>
 * <li>Loading: the gun rolls over to bring its loading port to the off hand, which fetches a shell from below and
 * pushes it up into the tube, once per shell, and the gun rolls back when loading stops.</li>
 * </ul>
 * The pump's travel shows through the mymod:gun_animation frames (1-6), so other players see it too. Drawing the gun
 * gives the pump a quick flick and inspecting it does a press check, both only for the holder.
 */
public final class ShotgunAnimation {
	private record Geometry(Vector3f support, Vector3f port, Vector3f ejection, float travel) {
	}

	// Model points (pixels) from tools/gen_shotguns.py.
	private static final Geometry REMINGTON = new Geometry(new Vector3f(8.0f, 6.35f, -2.6f), new Vector3f(8.0f, 6.6f, 8.4f),
			new Vector3f(9.2f, 9.3f, 9.8f), 3.6f);
	private static final Geometry SPAS = new Geometry(new Vector3f(8.0f, 5.95f, -3.0f), new Vector3f(8.0f, 6.45f, 7.3f),
			new Vector3f(9.25f, 9.25f, 8.5f), 3.0f);

	/** Pump timeline, as fractions of the pump time: drawn back, held, home. */
	private static final float PULL = 0.25f;
	private static final float BACK = 0.45f;
	private static final float RETURN = 0.52f;
	private static final float HOME = 0.68f;
	/** When the hull clears the port: in a pump stroke, and in a semi-auto cycle. */
	private static final float PUMP_EJECT = 0.42f;
	private static final float CYCLE_EJECT = 0.12f;
	private static final float EJECT_TICKS = 7.0f;

	// Per shell, as fractions of the shell's loading time.
	private static final float FETCHED = 0.3f;
	private static final float AT_PORT = 0.55f;
	private static final float PUSHED = 0.72f;
	/** Where the off hand goes for a shell, relative to the loading port (pixels: below and back, off the screen). */
	private static final Vector3f FETCH = new Vector3f(-2.0f, -12.0f, 5.0f);

	private static final long FLOURISH_MILLIS = 650;
	private static final long INSPECT_MILLIS = 3200;
	private static final long ROLL_MILLIS = 220;

	private static final ItemStackRenderState SHELL_STATE = new ItemStackRenderState();

	private static int lastSlot = -1;
	private static Item lastItem;
	private static long flourishStart = Long.MIN_VALUE;
	private static long inspectStart = Long.MIN_VALUE;
	private static float rolled;
	private static long rolledMillis;
	private static long cueStart = Long.MIN_VALUE;
	private static float cueProgress;

	private ShotgunAnimation() {
	}

	private static Geometry geometry(Item item) {
		return item == ModItems.SPAS12 ? SPAS : REMINGTON;
	}

	/** Game time now, between ticks. */
	private static double now() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0
				: minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static float since(long startMillis, long durationMillis) {
		return (Util.getMillis() - startMillis) / (float) durationMillis;
	}

	// ----------------------------------------------------------------------------------------------- control

	/** Called every client tick: the flick on drawing, putting an inspection away, and the handling sounds. */
	public static void tick(LocalPlayer player) {
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		int slot = player.getInventory().getSelectedSlot();
		if (held.getItem() instanceof ShotgunItem && (slot != lastSlot || held.getItem() != lastItem)) {
			flourishStart = Util.getMillis();
			inspectStart = Long.MIN_VALUE;
		}
		lastSlot = slot;
		lastItem = held.getItem();
		if (since(inspectStart, INSPECT_MILLIS) < 1.0f && (GunRecoil.kick() > 0.01f || GunScope.zoom(player) > 0.0f)) {
			inspectStart = Long.MIN_VALUE;
		}
		tickCues(player, held);
	}

	public static void inspect(LocalPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (GunScope.zoom(player) <= 0.0f && !busy(held)) {
			inspectStart = Util.getMillis();
		}
	}

	/** Whether this shotgun is busy: working its action, loading, flicking the pump or being inspected. */
	public static boolean busy(ItemStack stack) {
		if (!(stack.getItem() instanceof ShotgunItem)) {
			return false;
		}
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		return action != null && (action.kind() == ShotgunAction.Kind.LOAD || now() < action.end())
				|| since(flourishStart, FLOURISH_MILLIS) < 1.0f || since(inspectStart, INSPECT_MILLIS) < 1.0f;
	}

	private record Cue(float at, SoundEvent sound, float volume, float pitch) {
	}

	private static void tickCues(LocalPlayer player, ItemStack held) {
		ShotgunAction action = held.get(Shotguns.SHOTGUN_ACTION);
		if (!(held.getItem() instanceof ShotgunItem) || action == null || action.kind() == ShotgunAction.Kind.CYCLE) {
			return;
		}
		if (action.start() != cueStart) {
			cueStart = action.start();
			cueProgress = -1.0f;
		}
		float p = action.progress(player.level().getGameTime());
		Cue[] cues = action.kind() == ShotgunAction.Kind.PUMP
				? new Cue[] {new Cue(PULL + 0.02f, Shotguns.PUMP_BACK, 0.9f, 1.0f), new Cue(RETURN + 0.03f, Shotguns.PUMP_FORWARD, 0.9f, 1.0f)}
				: new Cue[] {new Cue(PUSHED - 0.02f, Shotguns.SHELL_INSERT, 0.7f, 1.0f)};
		for (Cue cue : cues) {
			if (cue.at() > cueProgress && cue.at() <= p) {
				player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
						cue.volume(), cue.pitch() * (0.95f + player.getRandom().nextFloat() * 0.1f), false);
			}
		}
		cueProgress = p;
	}

	// ---------------------------------------------------------------------------------------------- timeline

	/** How far back the pump is during a stroke, 0 to 1. */
	private static float pump(float p) {
		return p < PULL ? 0.0f : p < BACK ? GunMotion.smooth(span(p, PULL, BACK)) : p < RETURN ? 1.0f
				: 1.0f - GunMotion.smooth(span(p, RETURN, HOME));
	}

	private static float span(float p, float a, float b) {
		return GunMotion.span(p, a, b);
	}

	/** How far back the pump is now, 0 to 1, for whoever holds it (the flick and press check only for the holder). */
	private static float pumpTravel(ItemStack stack, boolean holder) {
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		if (action != null && action.kind() == ShotgunAction.Kind.PUMP) {
			float p = action.progress(now());
			if (p < 1.0f) {
				return pump(p);
			}
		}
		if (!holder) {
			return 0.0f;
		}
		float f = since(flourishStart, FLOURISH_MILLIS);
		if (f < 1.0f) {
			// A quick flick: straight back, a beat, home.
			return pump(PULL + f * (HOME - PULL + 0.05f));
		}
		float i = since(inspectStart, INSPECT_MILLIS);
		// Press check: drawn a little way back while the port faces the camera.
		return i < 1.0f ? 0.4f * GunMotion.smooth(span(i, 0.6f, 0.68f)) * (1.0f - GunMotion.smooth(span(i, 0.74f, 0.78f))) : 0.0f;
	}

	/** The mymod:gun_animation frame: the pump's position, 0 (home) to 6 (all the way back). */
	public static float frame(ItemStack stack, boolean holder) {
		return Math.round(pumpTravel(stack, holder) * 6.0f);
	}

	/** How far over the gun is rolled for loading, eased in and out as loading starts and stops. */
	private static float rolled(ItemStack stack) {
		long now = Util.getMillis();
		float step = Math.min(now - rolledMillis, 100L) / (float) ROLL_MILLIS;
		rolledMillis = now;
		rolled = Mth.clamp(rolled + (ShotgunItem.loading(stack) ? step : -step), 0.0f, 1.0f);
		return GunMotion.smooth(rolled);
	}

	// ------------------------------------------------------------------------------------------------ output

	/** Turns the held shotgun for what it's doing: tugged by the pump, rolled over for loading, or inspected. */
	public static void applyGunPose(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		if (!(stack.getItem() instanceof ShotgunItem) || anchors == null) {
			return;
		}
		float i = since(inspectStart, INSPECT_MILLIS);
		if (i < 1.0f) {
			GunMotion.inspect(poseStack, i, (Util.getMillis() - inspectStart) / 1000.0f, anchors.grip(), mirrored);
			return;
		}
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		float roll = rolled(stack);
		float shake = 0.0f;
		float pull = pumpTravel(stack, true);
		if (action != null) {
			float p = action.progress(now());
			long millis = action.ticks() * 50L;
			if (action.kind() == ShotgunAction.Kind.PUMP) {
				shake = GunMotion.jolt(p, HOME, millis, 0.9f) + GunMotion.jolt(p, BACK, millis, 0.4f);
			} else if (action.kind() == ShotgunAction.Kind.LOAD) {
				shake = GunMotion.jolt(p, PUSHED, millis, 0.35f);
			}
		}
		// The pump tugs the muzzle down and the gun back a touch; loading rolls the port over to the off hand.
		poseStack.translate(0.0f, 0.0f, 0.015f * pull);
		GunMotion.turn(poseStack, anchors.grip(), 10.0f * roll + 3.0f * pull, 12.0f * roll, -35.0f * roll - 4.0f * pull,
				0.03f * roll, 1.0f, shake, mirrored);
	}

	/** Where the off hand goes: riding the fore-end through the pump stroke, or fetching and pushing in shells. */
	public static GunAnimation.HandOverride hand(ItemStack stack) {
		if (!(stack.getItem() instanceof ShotgunItem)) {
			return null;
		}
		Item item = stack.getItem();
		Geometry g = geometry(item);
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		if (action != null && action.kind() == ShotgunAction.Kind.LOAD) {
			return new GunAnimation.HandOverride(false, GunMotion.onGun(item, loadingHand(g, action.progress(now()))), rolled);
		}
		float pull = pumpTravel(stack, true);
		if (pull <= 0.0f) {
			return null;
		}
		return new GunAnimation.HandOverride(false, GunMotion.onGun(item, new Vector3f(g.support()).add(0.0f, 0.0f, g.travel() * pull)), 1.0f);
	}

	/** The off hand's path for one shell (model pixels): down for it, up behind the port, and pushing it in. */
	private static Vector3f loadingHand(Geometry g, float q) {
		Vector3f port = g.port();
		Vector3f fetch = new Vector3f(port).add(FETCH);
		Vector3f below = new Vector3f(port).add(0.0f, -1.6f, 2.4f);
		Vector3f pushed = new Vector3f(port).add(0.0f, -0.3f, -0.4f);
		if (q < FETCHED) {
			return new Vector3f(port).lerp(fetch, GunMotion.smooth(span(q, 0.0f, FETCHED)));
		} else if (q < AT_PORT) {
			return fetch.lerp(below, GunMotion.smooth(span(q, FETCHED, AT_PORT)));
		} else if (q < PUSHED) {
			return below.lerp(pushed, GunMotion.smooth(span(q, AT_PORT, PUSHED)));
		}
		return pushed.lerp(port, GunMotion.smooth(span(q, PUSHED, 1.0f)));
	}

	/**
	 * Draws, for the holder in first person, the shell the off hand is loading and any hull flying out of the
	 * ejection port. The pose stack holds the gun's item pose (before its display transform).
	 */
	public static void submitShells(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack stack, boolean mirrored,
			LocalPlayer player) {
		if (!(stack.getItem() instanceof ShotgunItem)) {
			return;
		}
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		if (action == null) {
			return;
		}
		Item item = stack.getItem();
		Geometry g = geometry(item);
		float p = action.progress(now());
		if (action.kind() == ShotgunAction.Kind.LOAD) {
			if (p >= FETCHED - 0.05f && p < PUSHED) {
				// Held in the fingers just above the hand, nose first toward the port; pushed in it slides forward out of sight.
				Vector3f at = loadingHand(g, p).add(0.0f, 0.7f, -0.6f - 1.6f * GunMotion.smooth(span(p, AT_PORT, PUSHED)));
				submitShell(poseStack, collector, light, item, mirrored, player, action.shell(), "3d", at, 0.0f, 0.0f);
			}
			return;
		}
		if (action.chambering()) {
			// Nothing fired, so nothing to throw out.
			return;
		}
		float eject = action.kind() == ShotgunAction.Kind.PUMP ? PUMP_EJECT : CYCLE_EJECT;
		float t = (float) (now() - (action.start() + eject * action.ticks()));
		if (t < 0.0f || t >= EJECT_TICKS) {
			return;
		}
		// Out to the right and up, falling away, tumbling end over end.
		Vector3f at = new Vector3f(g.ejection()).add(1.5f * t, 1.3f * t - 0.32f * t * t, 0.7f * t);
		submitShell(poseStack, collector, light, item, mirrored, player, action.shell(), "spent", at, 70.0f * t, 35.0f * t);
	}

	private static void submitShell(PoseStack poseStack, SubmitNodeCollector collector, int light, Item gun, boolean mirrored,
			LocalPlayer player, Holder<Item> shell, String variant, Vector3f at, float spinX, float spinY) {
		ItemStack render = new ItemStack(shell);
		render.set(DataComponents.ITEM_MODEL, MyMod.id(BuiltInRegistries.ITEM.getKey(shell.value()).getPath() + "_" + variant));
		poseStack.pushPose();
		// The gun's display transform takes model pixels / 16 to the item's space; the shell model is centred on its origin.
		poseStack.mulPose(GunDisplay.of(gun).matrix(mirrored));
		poseStack.translate(at.x / 16.0f, at.y / 16.0f, at.z / 16.0f);
		poseStack.rotateDegrees(Axis.XP, spinX);
		poseStack.rotateDegrees(Axis.YP, spinY);
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(SHELL_STATE, render, ItemDisplayContext.NONE, player.level(), player, 0);
		SHELL_STATE.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
