package com.jheinz.mymod.client.launcher;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.ModSounds;
import com.jheinz.mymod.client.GunAnimation;
import com.jheinz.mymod.client.GunDisplay;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.launcher.LauncherAction;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.launcher.RocketLauncherItem;

/**
 * The RPG-7's handling, played from the {@link LauncherAction} on its stack so other players see it too:
 * <ul>
 * <li>Hoisting it onto the shoulder when it's drawn: it comes up from a low carry across the body, muzzle down
 * and canted, swings level and drops onto the shoulder with a thump.</li>
 * <li>Reloading: it comes down off the shoulder with the muzzle turned in, the off hand drops out of sight for a
 * rocket, brings it up in front of the muzzle and pushes it home, and the launcher goes back up.</li>
 * </ul>
 * In first person the launcher's pose is turned about the pistol grip and the off hand rides the rocket (drawn
 * from its item model, slid along the bore). Other players see the arms swing through matching poses; a rocket in
 * their hand isn't drawn. The seated warhead shows through the mymod:gun_animation model property from the
 * moment it's pushed home, before the server confirms the reload.
 */
public final class LauncherAnimation {
	/** Model points, in pixels, from tools/gen_rpg.py. */
	private static final Vector3f GRIP = new Vector3f(8.0f, 5.0f, 18.6f);
	private static final Vector3f SUPPORT = new Vector3f(8.0f, 7.6f, 10.2f);
	/** Where the off hand holds a rocket: under its motor, just behind the warhead. */
	private static final Vector3f ROCKET_HOLD = new Vector3f(8.0f, 7.9f, -4.5f);
	/** Where a rocket waits, along the bore in front of the muzzle, before it's pushed in. */
	private static final Vector3f ROCKET_READY = new Vector3f(0.0f, 0.0f, -14.0f);
	/** Where the off hand goes to fetch a rocket, relative to its hold on the heat guard (off the bottom of the screen). */
	private static final Vector3f FETCH_OFFSET = new Vector3f(-0.12f, -0.5f, 0.2f);

	// Reload timeline.
	private static final float FETCHED = 0.27f;
	private static final float AT_MUZZLE = 0.45f;
	private static final float SEATED = 0.64f;

	/** How the launcher turns away from the shoulder: degrees of pitch (muzzle up), yaw (muzzle left), roll, and offsets in blocks. */
	private record Pose(float pitch, float yaw, float roll, float dx, float dy, float dz) {
	}

	private static final Pose LOW_CARRY = new Pose(-14.0f, 30.0f, 25.0f, -0.05f, -0.26f, 0.1f);
	private static final Pose LOADING = new Pose(8.0f, 26.0f, -12.0f, -0.04f, -0.14f, 0.22f);

	/** Third-person arm angles in radians: right arm x and y, left arm x and y (for a right-handed player). */
	private record Arms(float rightX, float rightY, float leftX, float leftY) {
		Arms lerp(Arms to, float t) {
			return new Arms(Mth.lerp(t, rightX, to.rightX), Mth.lerp(t, rightY, to.rightY), Mth.lerp(t, leftX, to.leftX),
					Mth.lerp(t, leftY, to.leftY));
		}
	}

	// Fitted with the third-person display transform in tools/gen_rpg.py.
	private static final Arms LOW_ARMS = new Arms(-0.35f, -0.1f, -0.5f, 0.6f);
	private static final Arms LOADING_ARMS = new Arms(-0.82f, 0.35f, -0.95f, 0.9f);
	private static final Arms FETCHING_ARMS = new Arms(-0.82f, 0.35f, -0.2f, 0.3f);
	private static final Arms PUSHING_ARMS = new Arms(-0.82f, 0.35f, -1.2f, 0.75f);

	private record Cue(float at, SoundEvent sound, float volume, float pitch) {
	}

	private static final Cue[] EQUIP_CUES = {new Cue(0.08f, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 0.8f),
			new Cue(0.8f, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.9f, 0.6f), new Cue(0.9f, SoundEvents.LEVER_CLICK, 0.35f, 1.6f)};
	private static final Cue[] RELOAD_CUES = {new Cue(0.03f, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 0.8f),
			new Cue(FETCHED, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.1f),
			new Cue(0.52f, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 0.6f, 0.7f),
			new Cue(SEATED, ModSounds.MAG_IN, 1.0f, 0.55f), new Cue(0.93f, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 0.65f)};

	private static final ItemStackRenderState ROCKET_STATE = new ItemStackRenderState();
	/** One of each kind of rocket, in {@link Launchers#ROCKETS} order, for drawing the one being loaded. */
	private static ItemStack[] rocketStacks;

	private static long cueStart = Long.MIN_VALUE;
	private static float cueProgress;

	private LauncherAnimation() {
	}

	// ---------------------------------------------------------------------------------------------------- timing

	/** Game time now, between ticks. */
	static double now() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0
				: minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float span(float p, float a, float b) {
		return Mth.clamp((p - a) / (b - a), 0.0f, 1.0f);
	}

	/** Springy shake starting at {@code at} (progress), for an action lasting {@code ticks}. */
	private static float jolt(float p, float at, int ticks, float size) {
		float t = (p - at) * ticks / 20.0f;
		return t < 0.0f ? 0.0f : size * Mth.sin(t * Mth.TWO_PI * 6.0f) * (float) Math.exp(-t / 0.08f);
	}

	/** How far up onto the shoulder it is while being hoisted, 0 (low carry) to 1. */
	private static float lift(float p) {
		return smooth(span(p, 0.08f, 0.72f));
	}

	/** How far off the shoulder it is during a reload, 0 to 1. */
	private static float lowered(float p) {
		return smooth(span(p, 0.0f, 0.16f)) * (1.0f - smooth(span(p, 0.74f, 0.92f)));
	}

	// ---------------------------------------------------------------------------------------------- first person

	public static GunHands.Anchors anchors() {
		Matrix4f display = GunDisplay.of(Launchers.RPG7).matrix(false);
		return new GunHands.Anchors(display.transformPosition(new Vector3f(GRIP).div(16.0f)),
				display.transformPosition(new Vector3f(SUPPORT).div(16.0f)));
	}

	/** A launcher's shot kicks far harder than a rifle's, so its kick animation is toned down to keep it on the shoulder. */
	public static float recoilScale(ItemStack stack) {
		return stack.getItem() instanceof RocketLauncherItem ? 0.45f : 1.0f;
	}

	/** Turns the held launcher for whatever it's doing, pivoting on the pistol grip. */
	public static void applyFirstPerson(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		if (!(stack.getItem() instanceof RocketLauncherItem) || anchors == null) {
			return;
		}
		LauncherAction action = stack.get(Launchers.LAUNCHER_ACTION);
		Pose from;
		float amount;
		float bump = 0.0f;
		float shake = 0.0f;
		if (action == null) {
			from = LOW_CARRY;
			amount = 1.0f;
		} else {
			float p = action.progress(now());
			switch (action.kind()) {
				case EQUIP -> {
					from = LOW_CARRY;
					amount = 1.0f - lift(p);
					// Lifted a touch high, then dropped onto the shoulder.
					bump = Mth.sin((float) Math.PI * span(p, 0.6f, 0.8f));
					shake = jolt(p, 0.8f, action.ticks(), 1.0f);
				}
				case RELOAD -> {
					from = LOADING;
					amount = lowered(p);
					// A tug back as the rocket is pushed home, and a thump going back up.
					bump = -0.6f * Mth.sin((float) Math.PI * span(p, AT_MUZZLE, SEATED + 0.04f));
					shake = jolt(p, SEATED, action.ticks(), 0.6f) + jolt(p, 0.93f, action.ticks(), 0.8f);
				}
				default -> {
					return;
				}
			}
		}
		float side = mirrored ? -1.0f : 1.0f;
		Vector3f pivot = anchors.grip();
		poseStack.translate(from.dx() * amount * side, from.dy() * amount + 0.035f * bump, from.dz() * amount - shake * 0.012f);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(from.pitch() * amount + 4.0f * bump + shake * 2.0f),
				(float) Math.toRadians(from.yaw() * amount * side),
				(float) Math.toRadians(from.roll() * amount * side + shake * 1.5f)),
				pivot.x * side, pivot.y, pivot.z);
	}

	/** Where the off hand goes during a reload: down for a rocket, up with it to the muzzle, and back. */
	public static GunAnimation.HandOverride hand(ItemStack stack, GunHands.Anchors anchors) {
		LauncherAction action = stack.get(Launchers.LAUNCHER_ACTION);
		if (anchors == null || action == null || action.kind() != LauncherAction.Kind.RELOAD) {
			return null;
		}
		float p = action.progress(now());
		Vector3f support = anchors.support();
		Vector3f fetch = new Vector3f(support).add(FETCH_OFFSET);
		Vector3f target;
		if (p < FETCHED) {
			target = new Vector3f(support).lerp(fetch, smooth(span(p, 0.1f, FETCHED)));
		} else if (p < SEATED) {
			target = holdPoint(rocketOffset(p, anchors));
		} else {
			target = holdPoint(new Vector3f()).lerp(support, smooth(span(p, SEATED, 0.8f)));
		}
		return new GunAnimation.HandOverride(false, target, 1.0f - smooth(span(p, 0.78f, 0.82f)));
	}

	/** Where the off hand holds a rocket sitting {@code offset} pixels from its seat, in the held item's space. */
	private static Vector3f holdPoint(Vector3f offset) {
		return GunDisplay.of(Launchers.RPG7).matrix(false).transformPosition(new Vector3f(ROCKET_HOLD).add(offset).div(16.0f));
	}

	/**
	 * How far (model pixels) the rocket in hand is from its seat: brought up from where the hand fetched it to
	 * just in front of the muzzle, then slid in along the bore.
	 */
	private static Vector3f rocketOffset(float p, GunHands.Anchors anchors) {
		if (p >= AT_MUZZLE) {
			return new Vector3f(ROCKET_READY).lerp(new Vector3f(), smooth(span(p, AT_MUZZLE, SEATED)));
		}
		// The fetch point, back in model pixels, less the hold point: where the rocket is when the hand has it.
		Vector3f fetch = new Vector3f(anchors.support()).add(FETCH_OFFSET);
		Matrix4f toModel = GunDisplay.of(Launchers.RPG7).matrix(false).invert();
		Vector3f low = toModel.transformPosition(fetch).mul(16.0f).sub(ROCKET_HOLD);
		return low.lerp(ROCKET_READY, smooth(span(p, FETCHED, AT_MUZZLE)));
	}

	/**
	 * Draws the rocket the off hand is loading, if it's holding one now. The pose stack holds the launcher's item
	 * pose (before its display transform).
	 */
	public static void submitRocket(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack stack,
			GunHands.Anchors anchors, boolean mirrored, LocalPlayer player) {
		LauncherAction action = stack.get(Launchers.LAUNCHER_ACTION);
		if (anchors == null || action == null || action.kind() != LauncherAction.Kind.RELOAD) {
			return;
		}
		float p = action.progress(now());
		if (p < FETCHED || p >= SEATED) {
			return;
		}
		if (rocketStacks == null) {
			rocketStacks = Launchers.ROCKETS.stream().map(ItemStack::new).toArray(ItemStack[]::new);
		}
		Vector3f offset = rocketOffset(p, anchors);
		poseStack.pushPose();
		// The launcher's display transform, without its last half-block shift (the rocket's own rendering adds that).
		poseStack.mulPose(GunDisplay.of(Launchers.RPG7).matrix(mirrored).translate(0.5f, 0.5f, 0.5f));
		poseStack.translate(offset.x / 16.0f, offset.y / 16.0f, offset.z / 16.0f);
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(ROCKET_STATE, rocketStacks[Launchers.ROCKETS.indexOf(action.rocketItem())],
				ItemDisplayContext.NONE, player.level(), player, 0);
		ROCKET_STATE.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/** The mymod:gun_animation value once a rocket being loaded is pushed home: 1 a PG-7, 2 a homing one, 3 napalm. */
	public static float frame(ItemStack stack) {
		LauncherAction action = stack.get(Launchers.LAUNCHER_ACTION);
		if (action == null || action.kind() != LauncherAction.Kind.RELOAD || action.progress(now()) < SEATED) {
			return 0.0f;
		}
		return Launchers.ROCKETS.indexOf(action.rocketItem()) + 1.0f;
	}

	/** Plays the handling sounds for the local player's launcher as its animation passes each cue. */
	static void tickCues(LocalPlayer player) {
		LauncherAction action = player.getMainHandItem().get(Launchers.LAUNCHER_ACTION);
		if (!(player.getMainHandItem().getItem() instanceof RocketLauncherItem) || action == null
				|| action.kind() == LauncherAction.Kind.READY) {
			cueStart = Long.MIN_VALUE;
			return;
		}
		float p = action.progress(player.level().getGameTime());
		if (action.start() != cueStart) {
			cueStart = action.start();
			cueProgress = -1.0f;
		}
		for (Cue cue : action.kind() == LauncherAction.Kind.EQUIP ? EQUIP_CUES : RELOAD_CUES) {
			if (cue.at() > cueProgress && cue.at() <= p) {
				player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
						cue.volume(), cue.pitch() * (0.95f + player.getRandom().nextFloat() * 0.1f), false);
			}
		}
		cueProgress = p;
	}

	// ---------------------------------------------------------------------------------------------- third person

	/** Poses a player's arms for the launcher they're holding: shouldered and aimed where they look, or mid-action. */
	public static void poseArms(HumanoidModel<?> model, HumanoidRenderState state) {
		ItemStack stack = state.getMainHandItemStack();
		LauncherAction action = stack.get(Launchers.LAUNCHER_ACTION);
		float hx = model.head.xRot;
		float hy = model.head.yRot;
		Arms aimed = new Arms((float) (-Math.PI / 2) + 0.05f + hx, -0.08f, (float) (-Math.PI / 2) + 0.1f + hx, 0.75f);
		Arms arms;
		float headTurn = 1.0f;
		if (action == null) {
			arms = LOW_ARMS;
			headTurn = 0.0f;
		} else {
			float p = action.progress(now());
			switch (action.kind()) {
				case EQUIP -> {
					float t = lift(p);
					arms = LOW_ARMS.lerp(aimed, t);
					headTurn = t;
				}
				case RELOAD -> {
					float t = lowered(p);
					// The left arm drops for a rocket, then reaches up to the muzzle with it and pushes.
					float fetching = smooth(span(p, 0.1f, FETCHED)) * (1.0f - smooth(span(p, FETCHED, AT_MUZZLE)));
					float pushing = smooth(span(p, FETCHED, AT_MUZZLE)) * (1.0f - smooth(span(p, SEATED, 0.8f)));
					Arms loading = LOADING_ARMS.lerp(FETCHING_ARMS, fetching).lerp(PUSHING_ARMS, pushing);
					arms = aimed.lerp(loading, t);
					headTurn = 1.0f - t;
				}
				default -> arms = aimed;
			}
		}
		boolean right = state.mainArm == HumanoidArm.RIGHT;
		float side = right ? 1.0f : -1.0f;
		ModelPart main = right ? model.rightArm : model.leftArm;
		ModelPart off = right ? model.leftArm : model.rightArm;
		main.xRot = arms.rightX();
		main.yRot = arms.rightY() * side + hy * headTurn;
		main.zRot = 0.0f;
		off.xRot = arms.leftX();
		off.yRot = arms.leftY() * side + hy * headTurn;
		off.zRot = 0.0f;
	}
}
