package com.jheinz.tomyen.client.tank;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.tank.Tank;
import com.jheinz.tomyen.tank.TankCrew;
import com.jheinz.tomyen.tank.Tanks;

/**
 * Tanks on the client: rendering registration, the crew's controls, the gunner's sight (magnification and
 * thermal), the laser rangefinder, the camera shaking when the gun fires or the tank is hit, scripting the
 * view through the climb in and out, and the engine's rumble.
 *
 * <p>Controls in the gunner's seat (category "TOMYEN: Tank"):
 * <ul>
 * <li>W/S throttle, A/D pivot the hull; the mouse lays the turret and gun, which follow at their own pace.
 * <li>Attack: fire the main gun (once it's loaded). Use (hold): fire the coax.
 * <li>Reload (R): load the selected round from the rack. R is also the Power Armor key, but nobody can be in a
 * tank and a suit at once (a suit won't fit through the hatch), so only one of the two ever acts on it.
 * <li>Select Round (C): the round type for the next reload (APFSDS, tungsten or DU APFSDS, HE).
 * <li>Laser Rangefinder (X): range whatever's under the reticle; the fire control lays the gun for it.
 * <li>Sight Zoom (Y): wide, 3x and 8x. Thermal Sight (N): living things glow white against a dark scene.
 * <li>Smoke Launchers (K). Ammo Rack (I).
 * <li>Hold sneak: climb out. F5 (the perspective key): the view from outside, with a simpler readout, and
 * again back to the sight.
 * </ul>
 * In the sight the camera sits at the front of the turret and points where the gun points (see {@link #sightTank});
 * the mouse picks where the gun should go, and the turret and gun slew there and take the view with them.
 */
public final class TankClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("tank"));
	public static final KeyMapping RELOAD_KEY = key("key.tomyen.tank_reload", InputConstants.KEY_R);
	public static final KeyMapping ROUND_KEY = key("key.tomyen.tank_round", InputConstants.KEY_C);
	public static final KeyMapping LASE_KEY = key("key.tomyen.tank_lase", InputConstants.KEY_X);
	public static final KeyMapping ZOOM_KEY = key("key.tomyen.tank_zoom", InputConstants.KEY_Y);
	public static final KeyMapping THERMAL_KEY = key("key.tomyen.tank_thermal", InputConstants.KEY_N);
	public static final KeyMapping SMOKE_KEY = key("key.tomyen.tank_smoke", InputConstants.KEY_K);
	public static final KeyMapping RACK_KEY = key("key.tomyen.tank_rack", InputConstants.KEY_I);

	/** Sight magnifications: wide (unity), 3x and 8x. */
	public static final float[] ZOOMS = {1.0f, 3.0f, 8.0f};
	/** Furthest the rangefinder reaches (it can only see as far as the client has the world loaded). */
	private static final double LASE_RANGE = 1500.0;
	private static final double THERMAL_RANGE = 192.0;
	private static final int SHAKE_TICKS = 12;

	private static int zoom = 1;
	private static boolean thermal;
	private static double lasedRange;
	private static int sinceLase = 1000;
	private static int exitHold;
	private static boolean coaxDown;
	private static int shakeTicks;
	private static float shakeStrength;
	private static float lastRecoil;
	private static final Map<Integer, EngineSound> SOUNDS = new HashMap<>();

	private TankClient() {
	}

	private static KeyMapping key(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, key, CATEGORY));
	}

	public static void initialize() {
		EntityRendererRegistry.register(Tanks.TANK, TankRenderer::new);
		ModelLayerRegistry.registerModelLayer(TankModel.LAYER, TankModel::createBodyLayer);
		EntityRendererRegistry.register(Tanks.TANK_SHELL_ENTITY, ThrownItemRenderer::new);
		MenuScreens.register(Tanks.RACK_MENU, ContainerScreen::new);
		ClientTickEvents.START_CLIENT_TICK.register(TankClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(TankClient::endTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SOUNDS.clear();
			reset();
		});
		TankHud.initialize();
	}

	// --- Whose tank -----------------------------------------------------------------------------------------

	/** The tank the local player is in (climbing or seated), or null. */
	public static @Nullable Tank crewTank() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getVehicle() instanceof Tank tank ? tank : null;
	}

	/** The tank the local player is in the gunner's seat of, or null. */
	public static @Nullable Tank seatedTank() {
		Tank tank = crewTank();
		return tank != null && tank.isSeated(Minecraft.getInstance().player) ? tank : null;
	}

	/**
	 * The tank whose gunner's sight the local player is looking through, or null. This is the one check everything
	 * about the sight view goes by: the camera at the sight following the gun, the tank and its crew not drawn,
	 * no hands, the sight's zoom and thermal, the mouse's damping, and the sight HUD in place of vanilla's. It
	 * holds when the player is in the gunner's seat (climbed all the way in), the view is first person and the
	 * camera is on the player (not spectating something else).
	 */
	public static @Nullable Tank sightTank() {
		Minecraft minecraft = Minecraft.getInstance();
		Tank tank = seatedTank();
		return tank != null && minecraft.options.getCameraType().isFirstPerson() && minecraft.getCameraEntity() == minecraft.player
				? tank : null;
	}

	public static boolean isCrewSightView() {
		return sightTank() != null;
	}

	// --- Sight --------------------------------------------------------------------------------------------

	/** The sight's magnification for this player, or 0 if they aren't looking through it. */
	public static float sightZoom(Player player) {
		return player == Minecraft.getInstance().player && isCrewSightView() ? ZOOMS[zoom] : 0.0f;
	}

	public static float zoom() {
		return ZOOMS[zoom];
	}

	public static boolean thermal() {
		return thermal && isCrewSightView();
	}

	/** Whether the thermal sight shows this entity glowing. */
	public static boolean thermalSees(Entity entity) {
		if (!thermal() || !(entity instanceof LivingEntity) || entity instanceof Tank || entity == Minecraft.getInstance().player) {
			return false;
		}
		Tank tank = sightTank();
		return tank != null && entity.distanceToSqr(tank) < THERMAL_RANGE * THERMAL_RANGE;
	}

	/** The last lased range in blocks (metres), or 0 for no return. */
	public static double lasedRange() {
		return lasedRange;
	}

	/** Ticks since the last lase. */
	public static int sinceLase() {
		return sinceLase;
	}

	/** How far through holding sneak to climb out, 0 to 1. */
	public static float exitProgress() {
		return Math.min(1.0f, exitHold / (float) TankCrew.EXIT_HOLD_TICKS);
	}

	/** Mouse sensitivity: slowed by the sight's magnification, and none while the climb has the view. */
	public static double mouseScale() {
		Tank tank = crewTank();
		if (tank == null) {
			return 1.0;
		}
		if (tank.sequence() != Tank.SEQ_NONE) {
			return 0.0;
		}
		return isCrewSightView() ? Math.pow(ZOOMS[zoom], -0.8) : 1.0;
	}

	/** The view's roll and pitch kick in degrees for the shake, or null when it's still. */
	public static float @Nullable [] cameraShake() {
		if (shakeTicks <= 0) {
			return null;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = Mth.clamp((shakeTicks - partial) / SHAKE_TICKS, 0.0f, 1.0f);
		float amplitude = shakeStrength * t * t;
		float time = (Util.getMillis() % 100000L) / 1000.0f;
		return new float[] {Mth.sin(time * 47.0f) * amplitude * 2.5f, Mth.cos(time * 31.0f) * amplitude * 1.5f - amplitude * 2.0f};
	}

	private static void reset() {
		exitHold = 0;
		coaxDown = false;
		shakeTicks = 0;
		lastRecoil = 0.0f;
	}

	// --- Controls ----------------------------------------------------------------------------------------

	private static void send(int action) {
		ClientPlayNetworking.send(new Tanks.Control(action));
	}

	private static void drain(KeyMapping... keys) {
		for (KeyMapping key : keys) {
			while (key.consumeClick()) {
				// Pressed outside a tank: ignore.
			}
		}
	}

	/** Before vanilla handles input: the crew's controls, or the scripted view while climbing in or out. */
	private static void startTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		Tank tank = crewTank();
		if (player == null || tank == null) {
			drain(RELOAD_KEY, ROUND_KEY, LASE_KEY, ZOOM_KEY, THERMAL_KEY, SMOKE_KEY, RACK_KEY);
			reset();
			return;
		}
		if (!tank.isSeated(player)) {
			drain(RELOAD_KEY, ROUND_KEY, LASE_KEY, ZOOM_KEY, THERMAL_KEY, SMOKE_KEY, RACK_KEY);
			drain(minecraft.options.keyAttack, minecraft.options.keyUse);
			exitHold = 0;
			scriptView(player, tank);
			return;
		}
		boolean free = minecraft.gui.screen() == null;
		while (minecraft.options.keyAttack.consumeClick()) {
			if (free) {
				send(Tanks.Control.FIRE);
			}
		}
		drain(minecraft.options.keyUse);
		boolean coax = free && minecraft.options.keyUse.isDown();
		if (coax != coaxDown) {
			coaxDown = coax;
			send(coax ? Tanks.Control.COAX_DOWN : Tanks.Control.COAX_UP);
		}
		while (RELOAD_KEY.consumeClick()) {
			send(Tanks.Control.RELOAD);
		}
		while (ROUND_KEY.consumeClick()) {
			send(Tanks.Control.NEXT_ROUND);
			click(minecraft, 1.2f);
		}
		while (LASE_KEY.consumeClick()) {
			lase(minecraft, player, tank);
		}
		while (ZOOM_KEY.consumeClick()) {
			zoom = (zoom + 1) % ZOOMS.length;
			click(minecraft, 0.8f + zoom * 0.2f);
		}
		while (THERMAL_KEY.consumeClick()) {
			thermal = !thermal;
			click(minecraft, thermal ? 1.5f : 1.0f);
		}
		while (SMOKE_KEY.consumeClick()) {
			send(Tanks.Control.SMOKE);
		}
		while (RACK_KEY.consumeClick()) {
			send(Tanks.Control.OPEN_RACK);
		}
		exitHold = free && minecraft.options.keyShift.isDown() ? exitHold + 1 : 0;
	}

	private static void click(Minecraft minecraft, float pitch) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, pitch, 0.4f));
	}

	/**
	 * Ranges whatever's under the reticle, and tells the fire control. In the sight that's along the sight line
	 * (the camera, which follows the gun); from outside, along where the player is looking.
	 */
	private static void lase(Minecraft minecraft, LocalPlayer player, Tank tank) {
		Vec3 from = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		if (isCrewSightView()) {
			Camera camera = minecraft.gameRenderer.mainCamera();
			from = camera.position();
			look = new Vec3(camera.forwardVector().x(), camera.forwardVector().y(), camera.forwardVector().z());
		}
		Vec3 to = from.add(look.scale(LASE_RANGE));
		HitResult block = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double range = block.getType() == HitResult.Type.MISS ? 0.0 : block.getLocation().distanceTo(from);
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		AABB sweep = new AABB(from, end).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, end, sweep,
				e -> !e.isSpectator() && e.isPickable() && e != tank, from.distanceToSqr(end));
		if (entity != null) {
			range = entity.getLocation().distanceTo(from);
		}
		lasedRange = range;
		sinceLase = 0;
		tank.clientLasedRange = range;
		ClientPlayNetworking.send(new Tanks.Control(Tanks.Control.LASE, (float) range));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), range > 0.0 ? 2.0f : 0.7f, 0.5f));
	}

	/**
	 * Through the climb the view is scripted: looking at the turret going up the hull, down into the hatch going
	 * in, then along the gun; up at the sky coming out, then down at the ground beside the tank.
	 */
	private static void scriptView(LocalPlayer player, Tank tank) {
		float progress = tank.sequenceProgress(0.0f);
		boolean entering = tank.sequence() == Tank.SEQ_ENTERING;
		Vec3 eye = player.getEyePosition();
		float wantYaw;
		float wantPitch;
		if (entering && progress < 0.5f) {
			Vec3 to = TankCrew.turretPoint(tank, 1.0f, 0.0, 0.5, 0.0).subtract(eye);
			wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
			wantPitch = 15.0f;
		} else if (entering && progress < 0.85f) {
			wantYaw = tank.turretYaw(1.0f);
			wantPitch = 70.0f;
		} else if (entering) {
			wantYaw = tank.turretYaw(1.0f);
			wantPitch = 0.0f;
		} else if (progress < 0.45f) {
			wantYaw = tank.turretYaw(1.0f);
			wantPitch = -35.0f;
		} else {
			Vec3 to = eye.subtract(tank.position());
			wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
			wantPitch = 30.0f;
		}
		player.setYRot(Mth.rotLerp(0.3f, player.getYRot(), wantYaw));
		player.setXRot(Mth.lerp(0.3f, player.getXRot(), wantPitch));
	}

	// --- Effects -------------------------------------------------------------------------------------------

	private static void endTick(Minecraft minecraft) {
		if (sinceLase < 1000) {
			sinceLase++;
		}
		if (shakeTicks > 0) {
			shakeTicks--;
		}
		Tank tank = crewTank();
		// F5 in a tank: the sight, then the view from outside, then back to the sight (no view from the front).
		if (tank != null && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		}
		if (tank != null) {
			float recoil = tank.recoil(0.0f);
			if (recoil > lastRecoil + 0.5f) {
				shake(1.0f);
			}
			lastRecoil = recoil;
			if (tank.hurtTime == tank.hurtDuration && tank.hurtTime > 0) {
				shake(0.6f);
			}
			tank.clientLasedRange = lasedRange;
		} else {
			lasedRange = 0.0;
		}
		tickSounds(minecraft);
	}

	private static void shake(float strength) {
		shakeStrength = shakeTicks > 0 ? Math.max(strength, shakeStrength) : strength;
		shakeTicks = SHAKE_TICKS;
	}

	/** Starts the engine rumble for crewed tanks nearby. */
	private static void tickSounds(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		SOUNDS.values().removeIf(sound -> sound.isStopped());
		for (Tank tank : minecraft.level.getEntitiesOfClass(Tank.class, player.getBoundingBox().inflate(64.0),
				t -> t.isAlive() && t.isVehicle())) {
			if (!SOUNDS.containsKey(tank.getId())) {
				EngineSound sound = new EngineSound(tank);
				SOUNDS.put(tank.getId(), sound);
				minecraft.getSoundManager().play(sound);
			}
		}
	}

	/** A crewed tank's diesel: louder and higher as it speeds up. */
	static final class EngineSound extends AbstractTickableSoundInstance {
		private final Tank tank;

		EngineSound(Tank tank) {
			super(Tanks.ENGINE_SOUND, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
			this.tank = tank;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.3f;
			this.pitch = 0.8f;
			this.x = tank.getX();
			this.y = tank.getY();
			this.z = tank.getZ();
		}

		@Override
		public void tick() {
			if (tank.isRemoved() || !tank.isAlive() || !tank.isVehicle()) {
				stop();
				return;
			}
			x = tank.getX();
			y = tank.getY() + 1.0;
			z = tank.getZ();
			double speed = tank.position().subtract(tank.xo, tank.yo, tank.zo).horizontalDistance() / Tank.MAX_SPEED;
			float load = (float) Math.min(1.0, speed * 0.4);
			volume = Mth.lerp(0.15f, volume, 0.45f + load * 0.55f);
			pitch = Mth.lerp(0.15f, pitch, 0.75f + load * 0.45f);
		}
	}
}
