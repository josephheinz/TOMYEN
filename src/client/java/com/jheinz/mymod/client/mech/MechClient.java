package com.jheinz.mymod.client.mech;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.mech.Mech;
import com.jheinz.mymod.mech.MechArm;
import com.jheinz.mymod.mech.MechCrew;
import com.jheinz.mymod.mech.Mechs;

/**
 * Bulwark Walkers on the client: rendering registration, the pilot's controls, the cockpit view, scripting the
 * view through the climb in and out, and the cockpit shaking at every stomp, landing, shot and hit.
 *
 * <p>Controls in the cockpit (category "My Mod: Mech"):
 * <ul>
 * <li>W/A/S/D walk the way they point from where the torso faces (the legs turn to it, and walk backwards rather
 * than turn round); the mouse turns the torso and pitches the arms, which follow at their own pace.
 * <li>Attack (left click) works the left arm's weapon, use (right click) the right's; both are held to keep
 * firing. A Power Fist punches on a tap, grabs when held, and throws what it holds on the next tap.
 * <li>Jump: the jump jets. Cargo Pod (O): open the hold. Smoke Launcher (M): fire a smoke salvo.
 * <li>Hold sneak: climb out. F5: the view from outside (a chase camera), and back to the cockpit.
 * </ul>
 * In the cockpit the camera is the pilot's own eyes, at the viewport across the top of the chest; the walker and
 * its pilot aren't drawn (see {@link #cockpitMech}).
 */
public final class MechClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("mech"));
	public static final KeyMapping CARGO_KEY = key("key.mymod.mech_cargo", InputConstants.KEY_O);
	public static final KeyMapping SMOKE_KEY = key("key.mymod.mech_smoke", InputConstants.KEY_M);

	private static final int SHAKE_TICKS = 10;
	/** How far the arms' sight line reaches, for the reticles. */
	private static final double AIM_RANGE = 160.0;

	private static int exitHold;
	private static final boolean[] triggers = new boolean[2];
	private static boolean jumpWasDown;
	private static int shakeTicks;
	private static float shakeStrength;
	/** A footfall's or landing's jolt: how long ago, and how far it drops the view. */
	private static final int JOLT_TICKS = 7;
	private static int joltAge = JOLT_TICKS;
	private static float joltStrength;
	/** Out to here a walker's footfalls are felt by someone on foot, and its barrels heard whirring. */
	private static final float FEEL_RANGE = 12.0f;
	private static final double WHIR_RANGE = 40.0;
	private static float lean;
	private static float leanO;
	private static float lastTorsoYaw = Float.NaN;
	private static float lastSentYaw = Float.NaN;
	private static float lastSentPitch;
	private static int aimKeepAlive;
	/** Per walker near the player: the footfalls and landings already felt. */
	private static final Map<Integer, int[]> feltEvents = new HashMap<>();
	private static final Map<Integer, MechWhirSound> whirs = new HashMap<>();
	private static final float[] lastRecoil = new float[2];

	private MechClient() {
	}

	private static KeyMapping key(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, key, CATEGORY));
	}

	public static void initialize() {
		EntityRendererRegistry.register(Mechs.MECH, MechRenderer::new);
		ModelLayerRegistry.registerModelLayer(MechModel.LAYER, MechModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(MechGantryModel.LAYER, MechGantryModel::createLayer);
		BlockEntityRendererRegistry.register(Mechs.GANTRY_ENTITY, MechGantryRenderer::new);
		MenuScreens.register(Mechs.GANTRY_MENU, MechGantryScreen::new);
		MenuScreens.register(Mechs.CARGO_MENU, ContainerScreen::new);
		ClientTickEvents.START_CLIENT_TICK.register(MechClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(MechClient::endTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		MechHud.initialize();
	}

	// --- Whose walker -----------------------------------------------------------------------------------------

	/** The walker the local player is in (climbing or seated), or null. Not one holding them in its fist. */
	public static @Nullable Mech pilotedMech() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getVehicle() instanceof Mech mech && mech.pilot() == player ? mech : null;
	}

	/** The walker the local player is in the seat of, or null. */
	public static @Nullable Mech seatedMech() {
		Mech mech = pilotedMech();
		return mech != null && mech.isSeated(Minecraft.getInstance().player) ? mech : null;
	}

	/**
	 * The walker whose cockpit the local player is looking out of, or null: seated, in first person, with the camera
	 * on the player. Everything about the cockpit view goes by this: the walker and the pilot not drawn, no hands,
	 * and the cockpit HUD in place of vanilla's.
	 */
	public static @Nullable Mech cockpitMech() {
		Minecraft minecraft = Minecraft.getInstance();
		Mech mech = seatedMech();
		return mech != null && minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() == minecraft.player ? mech : null;
	}

	/** How far through holding sneak to climb out, 0 to 1. */
	public static float exitProgress() {
		return Math.min(1.0f, exitHold / (float) MechCrew.EXIT_HOLD_TICKS);
	}

	/** Mouse sensitivity: none while the climb has the view. */
	public static double mouseScale() {
		Mech mech = pilotedMech();
		return mech != null && mech.sequence() != Mech.SEQ_NONE ? 0.0 : 1.0;
	}

	/**
	 * The view's weight, applied where vanilla bobs the view when hurt (see MechCameraShakeMixin), for the pilot and
	 * anyone standing near a walker:
	 * <ul>
	 * <li>A shake (roll and pitch) at shots, hits and stomps.
	 * <li>A jolt at each footfall and landing: the view drops sharply and eases back up, nodding down a little.
	 * <li>In the cockpit, a slow lean into the torso's turns that settles after it, and a sway onto the planted foot
	 * as it walks. Only the view leans: the aim is the pilot's own and never lags for it.
	 * </ul>
	 */
	public static void applyCamera(PoseStack poseStack) {
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (shakeTicks > 0) {
			float t = Mth.clamp((shakeTicks - partial) / SHAKE_TICKS, 0.0f, 1.0f);
			float amplitude = shakeStrength * t * t;
			float time = (Util.getMillis() % 100000L) / 1000.0f;
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(time * 41.0f) * amplitude * 1.5f);
			poseStack.rotateDegrees(Axis.XP, Mth.cos(time * 29.0f) * amplitude * 1.2f + amplitude * 1.5f);
		}
		float age = joltAge + partial;
		if (age < JOLT_TICKS) {
			// Down in a tick and a half, back up over the rest.
			float envelope = age < 1.5f ? age / 1.5f : 1.0f - (age - 1.5f) / (JOLT_TICKS - 1.5f);
			envelope = envelope * envelope * (3.0f - 2.0f * envelope);
			poseStack.translate(0.0f, joltStrength * envelope, 0.0f);
			poseStack.rotateDegrees(Axis.XP, joltStrength * envelope * 14.0f);
		}
		Mech mech = cockpitMech();
		if (mech != null) {
			float leaning = Mth.lerp(partial, leanO, lean);
			float phase = mech.walkAnimation.position(partial) * Mech.WALK_CYCLE;
			float stride = Math.min(1.0f, mech.walkAnimation.speed(partial) * 1.6f);
			poseStack.rotateDegrees(Axis.ZP, leaning + Mth.cos(phase) * 0.9f * stride);
		}
	}

	/** A jolt of the view: {@code strength} is how far it drops, in blocks. A stronger one already going isn't cut short. */
	private static void jolt(float strength) {
		if (joltAge >= JOLT_TICKS || strength >= joltStrength * 0.6f) {
			joltStrength = joltAge < 2 ? Math.max(strength, joltStrength) : strength;
			joltAge = 0;
		}
	}

	/** Where each arm's shot goes: from its muzzle along the way it fires (see MechCrew#fireDirection), out to the aim point. */
	public static Vec3 armAim(Mech mech, int side, Vec3 aimPoint, float partialTicks) {
		Vec3 muzzle = MechCrew.muzzle(mech, side, partialTicks);
		double distance = Math.max(6.0, muzzle.distanceTo(aimPoint));
		return muzzle.add(MechCrew.fireDirection(mech, side, aimPoint, partialTicks).scale(distance));
	}

	/** Where the arms converge: what the torso's sight line from the viewport runs into. */
	public static Vec3 aimPoint(Mech mech, float partialTicks) {
		Vec3 from = MechCrew.viewport(mech, partialTicks);
		Vec3 to = from.add(MechCrew.aim(mech, partialTicks).scale(AIM_RANGE));
		HitResult block = mech.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mech));
		if (block.getType() != HitResult.Type.MISS) {
			to = block.getLocation();
		}
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(mech, from, to, new AABB(from, to).inflate(1.0),
				e -> e != mech && !mech.hasPassenger(e) && e.isPickable() && !e.isSpectator(), from.distanceToSqr(to));
		return entity != null ? entity.getLocation() : to;
	}

	private static void reset() {
		exitHold = 0;
		triggers[0] = false;
		triggers[1] = false;
		jumpWasDown = false;
		shakeTicks = 0;
		joltAge = JOLT_TICKS;
		lean = 0.0f;
		leanO = 0.0f;
		lastTorsoYaw = Float.NaN;
		lastSentYaw = Float.NaN;
		feltEvents.clear();
		whirs.clear();
	}

	// --- Controls --------------------------------------------------------------------------------------------

	private static void send(int action) {
		ClientPlayNetworking.send(new Mechs.Control(action));
	}

	private static void drain(KeyMapping... keys) {
		for (KeyMapping key : keys) {
			while (key.consumeClick()) {
				// Pressed outside a walker's cockpit: ignore.
			}
		}
	}

	/** Lets go of anything held down, telling the server. */
	private static void releaseTriggers() {
		for (int side = 0; side < 2; side++) {
			if (triggers[side]) {
				triggers[side] = false;
				send(side == MechArm.LEFT ? Mechs.Control.LEFT_UP : Mechs.Control.RIGHT_UP);
			}
		}
	}

	/** Before vanilla handles input: the pilot's controls, or the scripted view while climbing in or out. */
	private static void startTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		Mech mech = pilotedMech();
		if (player == null || mech == null) {
			drain(CARGO_KEY, SMOKE_KEY);
			if (triggers[0] || triggers[1]) {
				triggers[0] = false;
				triggers[1] = false;
			}
			exitHold = 0;
			return;
		}
		if (!mech.isSeated(player)) {
			drain(CARGO_KEY, SMOKE_KEY, minecraft.options.keyAttack, minecraft.options.keyUse);
			releaseTriggers();
			exitHold = 0;
			scriptView(player, mech);
			return;
		}
		boolean free = minecraft.gui.screen() == null;
		drain(minecraft.options.keyAttack, minecraft.options.keyUse);
		boolean[] down = {free && minecraft.options.keyAttack.isDown(), free && minecraft.options.keyUse.isDown()};
		for (int side = 0; side < 2; side++) {
			if (down[side] != triggers[side]) {
				triggers[side] = down[side];
				send(side == MechArm.LEFT ? down[side] ? Mechs.Control.LEFT_DOWN : Mechs.Control.LEFT_UP
						: down[side] ? Mechs.Control.RIGHT_DOWN : Mechs.Control.RIGHT_UP);
			}
		}
		boolean jump = free && minecraft.options.keyJump.isDown();
		if (jump && !jumpWasDown) {
			if (mech.jetsReady()) {
				mech.jetBurst();
				send(Mechs.Control.JETS);
			} else {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.4f, 0.4f));
			}
		}
		jumpWasDown = jump;
		while (CARGO_KEY.consumeClick()) {
			send(Mechs.Control.CARGO);
		}
		while (SMOKE_KEY.consumeClick()) {
			send(Mechs.Control.SMOKE);
		}
		exitHold = free && minecraft.options.keyShift.isDown() ? exitHold + 1 : 0;
	}

	/**
	 * Through the climb the view is scripted: at the walker going up the ladder, up at the hatch as it lifts, then
	 * out through the viewport; coming out, down the ladder, then back at the walker from the ground.
	 */
	private static void scriptView(LocalPlayer player, Mech mech) {
		float progress = mech.sequenceProgress(0.0f);
		boolean entering = mech.sequence() == Mech.SEQ_ENTERING;
		float wantYaw = mech.getYRot() + 180.0f;
		float wantPitch;
		if (entering) {
			wantPitch = progress < 0.45f ? -20.0f : progress < 0.8f ? -35.0f : 0.0f;
			if (progress >= 0.8f) {
				wantYaw = mech.getYRot();
			}
		} else {
			wantYaw = progress < 0.15f ? mech.getYRot() : mech.getYRot() + 180.0f;
			wantPitch = progress < 0.15f ? 0.0f : progress < 0.85f ? 10.0f : -25.0f;
		}
		player.setYRot(Mth.rotLerp(0.25f, player.getYRot(), wantYaw));
		player.setXRot(Mth.lerp(0.25f, player.getXRot(), wantPitch));
	}

	// --- Effects -------------------------------------------------------------------------------------------

	private static void endTick(Minecraft minecraft) {
		if (shakeTicks > 0) {
			shakeTicks--;
		}
		if (joltAge < JOLT_TICKS) {
			joltAge++;
		}
		leanO = lean;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Mech mech = pilotedMech();
		nearbyWalkers(minecraft, player, mech);
		if (mech == null) {
			lastTorsoYaw = Float.NaN;
			lean = 0.0f;
			return;
		}
		// F5 in a walker: the cockpit, then the chase camera, then back (no view from the front).
		if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		}
		if (mech.isSeated(player)) {
			sendAim(mech);
		}
		// The lean: into the torso's turn, by how fast it's turning, settling slowly.
		float torsoYaw = mech.torsoYaw(1.0f);
		float rate = Float.isNaN(lastTorsoYaw) ? 0.0f : Mth.wrapDegrees(torsoYaw - lastTorsoYaw);
		lastTorsoYaw = torsoYaw;
		lean += (Mth.clamp(-rate * 0.2f, -3.5f, 3.5f) - lean) * 0.16f;
		for (int side = 0; side < 2; side++) {
			float recoil = mech.recoil(side, 0.0f);
			if (recoil > lastRecoil[side] + 0.5f) {
				shake(0.3f);
			}
			lastRecoil[side] = recoil;
		}
		if (mech.hurtTime == mech.hurtDuration && mech.hurtTime > 0) {
			shake(0.7f);
		}
	}

	/** Tells the server the torso aim this client shows: on every change, and every few ticks regardless. */
	private static void sendAim(Mech mech) {
		float yaw = mech.torsoYawNow();
		float pitch = mech.armPitchNow();
		if (yaw != lastSentYaw || pitch != lastSentPitch || ++aimKeepAlive >= 2) {
			aimKeepAlive = 0;
			lastSentYaw = yaw;
			lastSentPitch = pitch;
			ClientPlayNetworking.send(new Mechs.Aim(yaw, pitch));
		}
	}

	/**
	 * Every walker near the player: its footfalls and landings jolt and shake the view (the pilot's own fully, anyone
	 * else's fading out to {@value #FEEL_RANGE} blocks), and its rotary cannons whir while their barrels turn.
	 */
	private static void nearbyWalkers(Minecraft minecraft, LocalPlayer player, @Nullable Mech own) {
		Set<Integer> present = new HashSet<>();
		for (Mech mech : minecraft.level.getEntitiesOfClass(Mech.class, player.getBoundingBox().inflate(WHIR_RANGE))) {
			present.add(mech.getId());
			int[] seen = feltEvents.computeIfAbsent(mech.getId(), id -> new int[] {mech.clientStomps, mech.clientLandings});
			boolean stomped = mech.clientStomps != seen[0];
			boolean landed = mech.clientLandings != seen[1];
			seen[0] = mech.clientStomps;
			seen[1] = mech.clientLandings;
			float feel = mech == own ? 1.0f : 0.55f * (1.0f - (float) mech.distanceTo(player) / FEEL_RANGE);
			if (feel > 0.0f) {
				if (stomped) {
					jolt(0.075f * mech.clientStompStrength * feel);
					shake(0.3f * mech.clientStompStrength * feel);
				}
				if (landed) {
					jolt(0.22f * feel);
					shake(1.2f * feel);
				}
			}
			for (int side = 0; side < 2; side++) {
				int key = mech.getId() * 2 + side;
				MechWhirSound whir = whirs.get(key);
				if (MechWhirSound.spin(mech, side) > 0.01f && (whir == null || !whir.playingFor(mech, side))) {
					whir = new MechWhirSound(mech, side);
					whirs.put(key, whir);
					minecraft.getSoundManager().play(whir);
				}
			}
		}
		feltEvents.keySet().retainAll(present);
		whirs.values().removeIf(MechWhirSound::isStopped);
	}

	private static void shake(float strength) {
		shakeStrength = shakeTicks > 0 ? Math.max(strength, shakeStrength) : strength;
		shakeTicks = SHAKE_TICKS;
	}

	/** Whether this entity is the local pilot's own walker or the pilot themselves, and so hidden in the cockpit view. */
	public static boolean hiddenInCockpit(Entity entity) {
		Mech mech = cockpitMech();
		return mech != null && (entity == mech || entity == mech.pilot());
	}
}
