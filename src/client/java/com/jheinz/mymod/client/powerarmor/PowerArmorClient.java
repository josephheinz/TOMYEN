package com.jheinz.mymod.client.powerarmor;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.powerarmor.PowerArmor;
import com.jheinz.mymod.powerarmor.PowerArmorMotion;
import com.jheinz.mymod.powerarmor.PowerArmorNetworking;
import com.jheinz.mymod.powerarmor.PowerArmorStats;
import com.jheinz.mymod.powerarmor.PowerArmorSuit;
import com.jheinz.mymod.powerarmor.PowerArmors;

/**
 * Power armor on the client: rendering registration, the wearer's controls, flying the local player, and the
 * thruster effects everyone sees.
 *
 * <p>Controls (category "My Mod: Power Armor"):
 * <ul>
 * <li>Power Armor (R): tap to fire a repulsor; hold {@link PowerArmor#EXIT_HOLD_TICKS} ticks to climb out.
 * <li>Unibeam (Z): charge and fire the chest beam.
 * <li>Swap Fusion Core (J): swap in the fullest core from the inventory.
 * <li>Use with an empty main hand at the air or at a monster: fire a repulsor (hold to keep firing).
 * <li>Double-tap jump: light or cut the thrusters. Lit, it flies like a powered elytra: hold forward to thrust
 * where you look (pitch included), add sprint to boost; let go to slow to a hover, where jump and sneak nudge up
 * and down. In creative, double-tapping jump also toggles creative flight, which then takes over: test in survival.
 * </ul>
 * V (Neural Trigger) and H (Hive Link) work as ever, through the helmet's Neuralink sockets. (G, the obvious
 * pick, is vanilla's Quick Actions key.)
 */
public final class PowerArmorClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("power_armor"));
	public static final KeyMapping POWER_ARMOR_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.power_armor", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
	public static final KeyMapping UNIBEAM_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.unibeam", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY));
	public static final KeyMapping SWAP_CORE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.swap_core", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));

	/** A tap of the Power Armor key shorter than this fires a repulsor. */
	private static final int TAP_TICKS = 6;
	/** Two presses of jump this close together light the thrusters (vanilla's creative double-tap window). */
	private static final int DOUBLE_TAP_TICKS = 7;
	/** Holding use, a repulsor every this many ticks (the server holds it to the arm's cooldown). */
	private static final int AUTO_FIRE_TICKS = 4;
	/** Share of the velocity swung round towards the look each tick while thrusting: how fast turns bite. */
	private static final double STEERING = 0.08;
	/** Speed kept each tick hovering, and with back held. */
	private static final double BRAKE = 0.85;
	private static final double BRAKE_HARD = 0.7;
	/** Vertical speed jump or sneak nudge a hover towards. */
	private static final double NUDGE = 0.15;
	/** Extra vertical drag in flight, so vanilla's 0.98 comes to about the 0.91 it gives horizontal speed. */
	private static final double VERTICAL_DRAG = 0.93;
	/** Speeds (blocks a tick) over which a suit eases from upright into the flat flying pose. */
	private static final double FLAT_FROM = 0.3;
	private static final double FLAT_AT = 0.55;
	/** Water pulls a suit down this much more a tick than it would a swimmer. */
	private static final double SINK = 0.045;

	/** The last readings from the server, or null before the first (or outside a suit). */
	public static PowerArmorNetworking.@Nullable Status status;
	private static int exitHold;
	private static boolean exitSent;
	private static boolean jumpWasDown;
	private static int sinceJump = 100;
	private static int sinceFire;
	private static final Map<Integer, ThrusterSound> SOUNDS = new HashMap<>();

	private PowerArmorClient() {
	}

	public static void initialize() {
		PowerArmorRenderer.initialize();
		BlockEntityRendererRegistry.register(PowerArmors.STATION_ENTITY, PowerArmorStationRenderer::new);
		EntityRendererRegistry.register(PowerArmors.PARKED, ParkedPowerArmorRenderer::new);
		MenuScreens.register(PowerArmors.STATION_MENU, PowerArmorStationScreen::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new PowerArmorLayer(avatar));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(PowerArmorNetworking.Status.TYPE, (payload, context) -> status = payload);
		ClientPlayNetworking.registerGlobalReceiver(PowerArmorNetworking.Beam.TYPE, (payload, context) -> PowerArmorBeams.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			status = null;
			SOUNDS.clear();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(PowerArmorBeams::submit);
		ClientTickEvents.START_CLIENT_TICK.register(PowerArmorClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(PowerArmorClient::endTick);
		PowerArmorHud.initialize();
	}

	/** How far through holding the Power Armor key to exit, 0 to 1. */
	public static float exitProgress() {
		return exitHold <= TAP_TICKS ? 0.0f : Math.min(1.0f, exitHold / (float) PowerArmor.EXIT_HOLD_TICKS);
	}

	private static void send(int action) {
		ClientPlayNetworking.send(new PowerArmorNetworking.Request(action));
	}

	// ------------------------------------------------------------------------------------------------ the wearer

	/** Before vanilla handles input and moves the player: controls, then thrust. */
	private static void startTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		PowerArmorSuit suit = player == null ? null : PowerArmor.worn(player);
		if (suit == null) {
			exitHold = 0;
			exitSent = false;
			jumpWasDown = false;
			if (player == null || !PowerArmor.isWearing(player)) {
				status = null;
			}
			return;
		}
		PowerArmorMotion motion = PowerArmor.motion(player);
		boolean free = minecraft.gui.screen() == null && !motion.inSequence();

		// The Power Armor key: a tap fires, a hold climbs out.
		if (POWER_ARMOR_KEY.isDown() && free) {
			exitHold++;
			if (exitHold >= PowerArmor.EXIT_HOLD_TICKS && !exitSent) {
				exitSent = true;
				send(PowerArmorNetworking.Request.EXIT);
			}
		} else {
			if (exitHold > 0 && exitHold < TAP_TICKS && free) {
				send(PowerArmorNetworking.Request.REPULSOR);
			}
			exitHold = 0;
			exitSent = false;
		}
		while (POWER_ARMOR_KEY.consumeClick()) {
			// Only whether it's held matters.
		}
		while (UNIBEAM_KEY.consumeClick()) {
			if (free) {
				send(PowerArmorNetworking.Request.UNIBEAM);
			}
		}
		while (SWAP_CORE_KEY.consumeClick()) {
			if (free) {
				send(PowerArmorNetworking.Request.SWAP_CORE);
			}
		}

		// Use with an empty main hand at nothing in particular fires a repulsor, instead of doing nothing.
		sinceFire++;
		if (free && player.getMainHandItem().isEmpty() && aimingToFire(minecraft)) {
			boolean fire = false;
			while (minecraft.options.keyUse.consumeClick()) {
				fire = true;
			}
			if (fire || minecraft.options.keyUse.isDown() && sinceFire >= AUTO_FIRE_TICKS) {
				send(PowerArmorNetworking.Request.REPULSOR);
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
				sinceFire = 0;
			}
		}

		// Double-tap jump lights (or cuts) the thrusters.
		boolean jump = minecraft.options.keyJump.isDown() && free;
		sinceJump++;
		if (jump && !jumpWasDown) {
			if (sinceJump <= DOUBLE_TAP_TICKS && !player.isInWater()) {
				send(motion.flying() ? PowerArmorNetworking.Request.THRUSTERS_OFF : PowerArmorNetworking.Request.THRUSTERS_ON);
				sinceJump = 100;
			} else {
				sinceJump = 0;
			}
		}
		jumpWasDown = jump;

		if (player.getAbilities().flying) {
			// Creative flight already flies them.
			return;
		}
		if (motion.flying() && !motion.inSequence()) {
			fly(minecraft, player, suit.stats());
		} else if (player.isInWater()) {
			// Too heavy to swim: it sinks and walks the bottom.
			Vec3 motionNow = player.getDeltaMovement();
			player.setDeltaMovement(motionNow.x, Math.min(motionNow.y, 0.1) - SINK, motionNow.z);
		}
	}

	/** Whether the crosshair is on empty air or a monster, where use has nothing better to do than fire. */
	private static boolean aimingToFire(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		return hit == null || hit.getType() == HitResult.Type.MISS
				|| hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Enemy;
	}

	/**
	 * Flies the local player for this tick, like a powered elytra, before vanilla moves them. Gravity is off while
	 * the thrusters are lit (the server's attribute modifier).
	 * <ul>
	 * <li>Forward thrusts along the whole look, pitch and all, so looking up climbs and down dives; sprint boosts.
	 * Some of the current velocity swings round towards the look each tick, so turns follow the mouse with inertia.
	 * <li>No forward: the speed bleeds off to a hover (back brakes harder); jump and sneak nudge up and down, and
	 * left and right drift sideways.
	 * </ul>
	 * Drag is the air's: 91% of the speed kept each tick, vertical evened out to match, so speed builds and fades.
	 */
	private static void fly(Minecraft minecraft, LocalPlayer player, PowerArmorStats stats) {
		Vec3 motion = player.getDeltaMovement();
		// Until the gravity modifier arrives, cancel gravity here.
		double gravity = player.getGravity();
		boolean forward = minecraft.options.keyUp.isDown();
		boolean back = minecraft.options.keyDown.isDown();
		boolean sprint = minecraft.options.keySprint.isDown();
		float strafe = (minecraft.options.keyLeft.isDown() ? 1 : 0) - (minecraft.options.keyRight.isDown() ? 1 : 0);
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		// Vanilla still pushes an airborne player along their input; take that back out, the thrusters do the flying.
		float walk = (forward ? 1 : 0) - (back ? 1 : 0);
		double inputLength = Math.sqrt(walk * walk + strafe * strafe);
		if (inputLength > 0.0) {
			double airControl = (player.isSprinting() ? 0.026 : 0.02) / Math.max(1.0, inputLength);
			double x = strafe * airControl, z = walk * airControl;
			motion = motion.subtract(x * Mth.cos(yaw) - z * Mth.sin(yaw), 0.0, z * Mth.cos(yaw) + x * Mth.sin(yaw));
		}
		if (forward) {
			Vec3 look = player.getLookAngle();
			motion = motion.lerp(look.scale(motion.length()), STEERING).add(look.scale(sprint ? stats.boostThrust() : stats.cruiseThrust()));
		} else {
			double brake = back ? BRAKE_HARD : BRAKE;
			boolean up = minecraft.options.keyJump.isDown(), down = minecraft.options.keyShift.isDown();
			double target = up ? NUDGE : down ? -NUDGE : 0.0;
			motion = new Vec3(motion.x * brake, motion.y + (target - motion.y) * 0.25, motion.z * brake);
			if (strafe != 0.0f) {
				double drift = stats.cruiseThrust() * 0.4 * strafe;
				motion = motion.add(drift * Mth.cos(yaw), 0.0, drift * Mth.sin(yaw));
			}
		}
		// Vanilla drags vertical speed far less (0.98) than horizontal (0.91); even them out.
		motion = new Vec3(motion.x, motion.y * VERTICAL_DRAG + gravity, motion.z);
		player.setDeltaMovement(motion);
	}

	// ------------------------------------------------------------------------------------------------ everyone

	/** How to draw the suit an entity wears this frame, or null if it isn't wearing one. */
	public static PowerArmorRenderer.@Nullable Worn worn(Entity entity, float partialTicks) {
		PowerArmorSuit suit = PowerArmor.worn(entity);
		if (suit == null) {
			return null;
		}
		PowerArmorMotion motion = PowerArmor.motion(entity);
		float open = motion.openness(entity.level().getGameTime(), partialTicks);
		return new PowerArmorRenderer.Worn(suit.look(), open, flame(motion.thrusters()), motion.flying(), flatness(entity, motion));
	}

	/** How far into the flat flying pose a suit is, by how fast it's going: none at a hover, all of it at speed. */
	private static float flatness(Entity entity, PowerArmorMotion motion) {
		if (!motion.flying()) {
			return 0.0f;
		}
		double speed = entity.position().subtract(entity.xo, entity.yo, entity.zo).length();
		float t = (float) Mth.clamp((speed - FLAT_FROM) / (FLAT_AT - FLAT_FROM), 0.0, 1.0);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float flame(int thrusters) {
		return switch (thrusters) {
			case PowerArmorMotion.HOVER -> 0.6f;
			case PowerArmorMotion.CRUISE -> 1.1f;
			case PowerArmorMotion.BOOST -> 1.6f;
			default -> 0.0f;
		};
	}

	/** After everything moved: thruster flames, smoke and sound for every flying suit in sight. */
	private static void endTick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		for (Player player : minecraft.level.players()) {
			PowerArmorMotion motion = PowerArmor.motion(player);
			PowerArmorSuit suit = PowerArmor.worn(player);
			if (suit == null || !motion.flying() || player.distanceToSqr(minecraft.player) > 96 * 96) {
				continue;
			}
			thrusterParticles(minecraft, player, motion.thrusters());
			ThrusterSound sound = SOUNDS.get(player.getId());
			if (sound == null || sound.isStopped()) {
				sound = new ThrusterSound(player);
				SOUNDS.put(player.getId(), sound);
				minecraft.getSoundManager().play(sound);
			}
		}
		SOUNDS.values().removeIf(ThrusterSound::isStopped);
	}

	/**
	 * Jet exhaust from the soles and smoke from the backpack's stacks. The feet stay where the player stands, and the
	 * body points from them along its "up": straight up hovering, along the look lying flat.
	 */
	private static void thrusterParticles(Minecraft minecraft, Player player, int thrusters) {
		ClientLevel level = minecraft.level;
		RandomSource random = player.getRandom();
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		PowerArmorRenderer.Worn worn = worn(player, 1.0f);
		float flat = worn == null ? 0.0f : worn.flat();
		Vec3 headward = new Vec3(0.0, 1.0, 0.0).lerp(player.getLookAngle(), flat).normalize();
		Vec3 backward = right.cross(headward).normalize();
		Vec3 exhaust = headward.reverse();
		Vec3 feet = player.position();
		int flames = thrusters == PowerArmorMotion.HOVER ? 1 : 2;
		for (int side = -1; side <= 1; side += 2) {
			Vec3 boot = feet.add(right.scale(side * 0.2 * PowerArmorRenderer.SCALE));
			for (int i = 0; i < flames; i++) {
				double speed = 0.2 + random.nextDouble() * 0.15;
				level.addParticle(ParticleTypes.FLAME, boot.x, boot.y, boot.z, exhaust.x * speed + random.nextGaussian() * 0.02,
						exhaust.y * speed + random.nextGaussian() * 0.02, exhaust.z * speed + random.nextGaussian() * 0.02);
			}
			Vec3 smoke = boot.add(exhaust.scale(0.4));
			level.addParticle(thrusters == PowerArmorMotion.BOOST ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, smoke.x, smoke.y, smoke.z,
					exhaust.x * 0.08, exhaust.y * 0.08, exhaust.z * 0.08);
			if (thrusters == PowerArmorMotion.BOOST && random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.LAVA, boot.x, boot.y, boot.z, 0.0, 0.0, 0.0);
			}
			// The backpack's exhaust stacks puff while the reactor works hard.
			if (random.nextInt(3) == 0) {
				Vec3 stack = feet.add(headward.scale(2.05 * PowerArmorRenderer.SCALE)).add(backward.scale(0.35)).add(right.scale(side * 0.25));
				level.addParticle(ParticleTypes.SMOKE, stack.x, stack.y, stack.z, headward.x * 0.04, headward.y * 0.04, headward.z * 0.04);
			}
		}
	}

	/** A jet whine following a flying suit, louder the harder it's thrusting. */
	static final class ThrusterSound extends AbstractTickableSoundInstance {
		private final Player player;

		ThrusterSound(Player player) {
			super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.player = player;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01f;
			this.x = player.getX();
			this.y = player.getY();
			this.z = player.getZ();
		}

		@Override
		public void tick() {
			PowerArmorMotion motion = PowerArmor.motion(player);
			if (player.isRemoved() || !motion.flying()) {
				stop();
				return;
			}
			x = player.getX();
			y = player.getY();
			z = player.getZ();
			float thrust = flame(motion.thrusters());
			volume = Mth.lerp(0.2f, volume, Math.min(1.0f, 0.35f + thrust * 0.4f));
			pitch = Mth.lerp(0.2f, pitch, 1.3f + thrust * 0.35f);
		}
	}
}
