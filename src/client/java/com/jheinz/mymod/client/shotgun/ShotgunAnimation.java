package com.jheinz.mymod.client.shotgun;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

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
import com.jheinz.mymod.client.Keyframes;
import com.jheinz.mymod.client.Keyframes.Ease;
import com.jheinz.mymod.client.Keyframes.Path;
import com.jheinz.mymod.client.Keyframes.Track;
import com.jheinz.mymod.client.gunsmith.GunsmithAnimation;
import com.jheinz.mymod.client.shoddy.ShoddyGunClient;
import com.jheinz.mymod.ModSounds;
import com.jheinz.mymod.shoddy.ShoddyGunItem;
import com.jheinz.mymod.shoddy.ShoddyGuns;
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
 * thumbs it into the port, once per shell, and the gun rolls back when loading stops.</li>
 * <li>Clearing a homemade gun's jam: the gun turns its action to the eye, the off hand winds up and knocks the part
 * twice with the heel of the hand, then takes hold of it and works it all the way, and the stuck case flies out.</li>
 * </ul>
 * The homemade guns (ShoddyGunItem) share all of this, with their own moving part (striker, bolt, ramrod, lever) in
 * place of the pump, their own way of taking a round (see {@link Loading}) and their own poses (see {@link Geometry}):
 * the off hand reaches over to the part, takes hold of it and works it, lifting a bolt first, and the gun turns the part
 * toward the eye while it's worked. The pump's travel shows through the mymod:gun_animation frames (1-6), so other
 * players see it too. Drawing the gun gives the pump a quick flick and inspecting it does a press check, both only for
 * the holder. Every hand movement is a keyframed {@link Path} on the action's timeline.
 */
public final class ShotgunAnimation {
	/** How a gun takes a round. */
	public enum Loading {
		/** Thumbed up into a loading port under the receiver and on into the tube (the pump guns). */
		TUBE,
		/** Laid into an open breech or box from above and pressed down (the pipe guns, the Junk Repeater's box). */
		BREECH,
		/** Poured down the muzzle with the gun tipped up (the Blunderbuss). */
		MUZZLE
	}

	/**
	 * Where things are on a tube-fed gun, in model pixels.
	 *
	 * @param support   where the off hand holds it
	 * @param port      where rounds go in
	 * @param ejection  where fired hulls fly out from
	 * @param travel    how far the moving part (pump, bolt, striker, ramrod) goes along z when worked; negative is forward
	 * @param work      where the off hand takes hold of the moving part at rest
	 * @param ejects    whether working it throws out a hull (a muzzle-loader or a pipe pistol doesn't)
	 * @param loadPose  how the gun turns to bring the port to the off hand while loading: degrees of pitch, yaw and roll
	 * @param loading   how a round goes in
	 * @param workPose  how the gun turns to bring the moving part toward the eye while the hand works it (pitch, yaw,
	 *                  roll); none for a pump, which the hand works where it already holds it
	 * @param workLift  how far (pixels) the hand lifts the part before drawing it back, as a bolt handle turns up
	 * @param workTwist how the hand turns to take hold of the part (degrees, see GunHands.poseArm)
	 */
	public record Geometry(Vector3f support, Vector3f port, Vector3f ejection, float travel, Vector3f work, boolean ejects,
			Vector3f loadPose, Loading loading, Vector3f workPose, float workLift, float workTwist) {
		/** A pump gun: the off hand rides the fore-end, and the gun rolls over to load from underneath. */
		Geometry(Vector3f support, Vector3f port, Vector3f ejection, float travel) {
			this(support, port, ejection, travel, support, true, new Vector3f(10.0f, 12.0f, -35.0f), Loading.TUBE, new Vector3f(),
					0.0f, 0.0f);
		}

		/** Whether the off hand has to leave its hold to work the action (it doesn't on a pump gun's fore-end). */
		boolean reaches() {
			return !work.equals(support);
		}
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

	/** How far back the pump (or a homemade gun's part) is through a stroke, 0 to 1. */
	private static final Track PUMP_TRAVEL = Keyframes.track(0.0f).hold(PULL).key(BACK, 1.0f).hold(RETURN).key(HOME, 0.0f).build();
	/** How far a bolt is lifted through a stroke: up as the hand takes it, down once it's home. */
	private static final Track PUMP_LIFT = Keyframes.track(0.0f).hold(PULL - 0.02f).key(PULL + 0.04f, 1.0f).hold(HOME - 0.04f)
			.key(HOME + 0.02f, 0.0f).build();
	/** How far the gun turns its part toward the eye while the hand works it. */
	private static final Track PUMP_ENGAGED = Keyframes.track(0.0f).key(PULL, 1.0f).hold(HOME).key(1.0f, 0.0f).build();

	/**
	 * Clearing a jam, as fractions of its time: the gun rolls over to show the action (to CLEAR_ROLLED), two knocks of the
	 * hand that each jerk the part a little way back ({hit, jerked back, home}), then a full stroke that throws the stuck
	 * case out, and the gun rolls back from CLEAR_UNROLL.
	 */
	private static final float CLEAR_ROLLED = 0.15f;
	private static final float[][] CLEAR_KNOCKS = {{0.24f, 0.27f, 0.31f}, {0.40f, 0.43f, 0.47f}};
	private static final float CLEAR_KNOCK_TRAVEL = 0.3f;
	private static final float CLEAR_PULL = 0.50f;
	private static final float CLEAR_BACK = 0.62f;
	private static final float CLEAR_RETURN = 0.70f;
	private static final float CLEAR_HOME = 0.80f;
	private static final float CLEAR_UNROLL = 0.85f;
	/** How the gun turns while a jam is cleared: pitch, yaw, and roll to show the ejection side. */
	private static final Vector3f CLEAR_POSE = new Vector3f(6.0f, 14.0f, -30.0f);
	/** How far back the action is while a jam is cleared, 0 to 1: jerked by two knocks, then a full stroke. */
	private static final Track CLEAR_TRAVEL = Keyframes.track(0.0f)
			.hold(CLEAR_KNOCKS[0][0]).key(CLEAR_KNOCKS[0][1], CLEAR_KNOCK_TRAVEL, Ease.OUT).key(CLEAR_KNOCKS[0][2], 0.0f)
			.hold(CLEAR_KNOCKS[1][0]).key(CLEAR_KNOCKS[1][1], CLEAR_KNOCK_TRAVEL, Ease.OUT).key(CLEAR_KNOCKS[1][2], 0.0f)
			.hold(CLEAR_PULL).key(CLEAR_BACK, 1.0f).hold(CLEAR_RETURN).key(CLEAR_HOME, 0.0f).build();
	private static final Track CLEAR_ENGAGED = Keyframes.track(0.0f).key(CLEAR_ROLLED, 1.0f).hold(CLEAR_UNROLL).key(1.0f, 0.0f).build();

	// Per shell, as fractions of the shell's loading time.
	private static final float FETCHED = 0.3f;
	private static final float AT_PORT = 0.55f;
	private static final float PUSHED = 0.72f;
	/** Where the off hand goes for a shell, relative to the loading port (pixels: below and back, off the screen). */
	private static final Vector3f FETCH = new Vector3f(-2.0f, -12.0f, 5.0f);

	private static final long FLOURISH_MILLIS = 650;
	private static final long INSPECT_MILLIS = 3200;
	private static final long ROLL_MILLIS = 220;

	private enum Stroke {
		PUMP, CLEAR, LOAD
	}

	/** Each gun's off-hand paths, in its model pixels and in the held item's space, built the first time they're needed. */
	private static final Map<Item, Map<Stroke, Path>> PIXEL_PATHS = new HashMap<>();
	private static final Map<Item, Map<Stroke, Path>> PATHS = new HashMap<>();

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
		Geometry shoddy = ShoddyGunClient.geometry(item);
		if (shoddy != null) {
			return shoddy;
		}
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
		if (GunsmithAnimation.owns(held.getItem())) {
			GunsmithAnimation.inspect(player);
			return;
		}
		if (GunScope.zoom(player) <= 0.0f && !busy(held)) {
			inspectStart = Util.getMillis();
		}
	}

	/** Whether this shotgun is busy: working its action, loading, flicking the pump or being inspected. */
	public static boolean busy(ItemStack stack) {
		if (!(stack.getItem() instanceof ShotgunItem)) {
			return false;
		} else if (GunsmithAnimation.owns(stack.getItem())) {
			return GunsmithAnimation.busy(stack);
		}
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		return action != null && (action.kind() == ShotgunAction.Kind.LOAD || now() < action.end())
				|| since(flourishStart, FLOURISH_MILLIS) < 1.0f || since(inspectStart, INSPECT_MILLIS) < 1.0f;
	}

	private record Cue(float at, SoundEvent sound, float volume, float pitch) {
	}

	private static void tickCues(LocalPlayer player, ItemStack held) {
		ShotgunAction action = held.get(Shotguns.SHOTGUN_ACTION);
		if (!(held.getItem() instanceof ShotgunItem) || action == null || action.kind() == ShotgunAction.Kind.CYCLE
				|| GunsmithAnimation.owns(held.getItem())) {
			return;
		}
		if (action.start() != cueStart) {
			cueStart = action.start();
			cueProgress = -1.0f;
		}
		float p = action.progress(player.level().getGameTime());
		Cue[] cues = switch (action.kind()) {
			// A homemade gun's bolt, striker, ramrod or lever sounds like a rack, not a pump.
			case PUMP -> held.getItem() instanceof ShoddyGunItem
					? new Cue[] {new Cue(PULL + 0.02f, ModSounds.BOLT_BACK, 0.8f, 0.8f), new Cue(RETURN + 0.03f, ModSounds.BOLT_FORWARD, 0.8f, 0.75f)}
					: new Cue[] {new Cue(PULL + 0.02f, Shotguns.PUMP_BACK, 0.9f, 1.0f), new Cue(RETURN + 0.03f, Shotguns.PUMP_FORWARD, 0.9f, 1.0f)};
			// Two knocks of the hand that don't shift it, then the stroke that does.
			case CLEAR -> new Cue[] {new Cue(CLEAR_KNOCKS[0][0], ShoddyGuns.JAM_CLEAR, 0.8f, 1.0f),
					new Cue(CLEAR_KNOCKS[1][0], ShoddyGuns.JAM_CLEAR, 0.8f, 1.15f), new Cue(CLEAR_PULL + 0.03f, Shotguns.PUMP_BACK, 0.9f, 0.8f),
					new Cue(CLEAR_RETURN + 0.03f, Shotguns.PUMP_FORWARD, 0.9f, 0.85f)};
			default -> new Cue[] {new Cue(PUSHED - 0.02f, Shotguns.SHELL_INSERT, 0.7f, 1.0f)};
		};
		for (Cue cue : cues) {
			if (cue.at() > cueProgress && cue.at() <= p) {
				player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
						cue.volume(), cue.pitch() * (0.95f + player.getRandom().nextFloat() * 0.1f), false);
			}
		}
		cueProgress = p;
	}

	// ---------------------------------------------------------------------------------------------- timeline

	private static float span(float p, float a, float b) {
		return GunMotion.span(p, a, b);
	}

	/** How far back the pump is now, 0 to 1, for whoever holds it (the flick and press check only for the holder). */
	private static float pumpTravel(ItemStack stack, boolean holder) {
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		if (action != null && (action.kind() == ShotgunAction.Kind.PUMP || action.kind() == ShotgunAction.Kind.CLEAR)) {
			float p = action.progress(now());
			if (p < 1.0f) {
				return (action.kind() == ShotgunAction.Kind.PUMP ? PUMP_TRAVEL : CLEAR_TRAVEL).sample(p);
			}
		}
		if (!holder) {
			return 0.0f;
		}
		float f = since(flourishStart, FLOURISH_MILLIS);
		if (f < 1.0f) {
			// A quick flick: straight back, a beat, home.
			return PUMP_TRAVEL.sample(PULL + f * (HOME - PULL + 0.05f));
		}
		float i = since(inspectStart, INSPECT_MILLIS);
		// Press check: drawn a little way back while the port faces the camera.
		return i < 1.0f ? 0.4f * GunMotion.smooth(span(i, 0.6f, 0.68f)) * (1.0f - GunMotion.smooth(span(i, 0.74f, 0.78f))) : 0.0f;
	}

	/** The mymod:gun_animation frame: the pump's position, 0 (home) to 6 (all the way back). */
	public static float frame(ItemStack stack, boolean holder) {
		if (GunsmithAnimation.owns(stack.getItem())) {
			return GunsmithAnimation.frame(stack, holder);
		}
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

	// ------------------------------------------------------------------------------------------- hand paths

	/** The off hand's path for a stroke, in the gun's model pixels. */
	private static Path pixelPath(Item item, Stroke stroke) {
		return PIXEL_PATHS.computeIfAbsent(item, i -> new EnumMap<>(Stroke.class)).computeIfAbsent(stroke, s -> {
			Geometry g = geometry(item);
			return switch (s) {
				case PUMP -> pumpPath(g);
				case CLEAR -> clearPath(g);
				case LOAD -> loadPath(g);
			};
		});
	}

	/** The off hand's path for a stroke, in the held item's space. */
	private static Path path(Item item, Stroke stroke) {
		return PATHS.computeIfAbsent(item, i -> new EnumMap<>(Stroke.class))
				.computeIfAbsent(stroke, s -> pixelPath(item, s).map(point -> GunMotion.onGun(item, point)));
	}

	/** Where the hand holds the moving part at {@code t} through a stroke (pixels): lifted, then along its travel. */
	private static Function<Float, Vector3f> onPart(Geometry g, Track travel, Track lift) {
		return t -> new Vector3f(g.work()).add(0.0f, g.workLift() * lift.sample(t), g.travel() * travel.sample(t));
	}

	/**
	 * A stroke of the action. On a pump gun the off hand simply rides the fore-end back and slams it home. On a homemade
	 * gun it reaches over and takes the part (by PULL), lifts it if it's a bolt, draws it back and runs it home, then
	 * flicks off it and goes back to its hold.
	 */
	private static Path pumpPath(Geometry g) {
		Keyframes.PathBuilder path = Keyframes.path(g.support());
		if (g.reaches()) {
			path.key(PULL - 0.08f, new Vector3f(g.work()).add(0.0f, 1.8f, 1.2f), g.workTwist() * 0.7f)
					.key(PULL - 0.02f, g.work(), g.workTwist());
		}
		path.hold(PULL).follow(PULL, HOME + 0.02f, onPart(g, PUMP_TRAVEL, PUMP_LIFT));
		if (g.reaches()) {
			path.key(HOME + 0.1f, new Vector3f(g.work()).add(0.3f, 1.6f, 1.2f), g.workTwist() * 0.5f, Ease.OUT);
		}
		return path.key(1.0f, g.support(), 0.0f).build();
	}

	/**
	 * Clearing a jam: the hand comes up beside the action, palm toward it, and knocks it with the heel of the hand
	 * (twice, on the cues), then takes hold of the part and works it all the way back and home, and lets go.
	 */
	private static Path clearPath(Geometry g) {
		Vector3f windUp = new Vector3f(g.work()).add(2.5f, 4.0f, 1.0f);
		Vector3f knock = new Vector3f(g.work()).add(0.4f, 0.6f, 0.0f);
		Track noLift = Keyframes.track(1.0f).build();
		return Keyframes.path(g.support())
				.key(CLEAR_ROLLED - 0.02f, windUp, -70.0f)
				.key(CLEAR_KNOCKS[0][0], knock, -90.0f, Ease.IN)
				.key(CLEAR_KNOCKS[0][2], windUp, -70.0f, Ease.OUT)
				.key(CLEAR_KNOCKS[1][0], knock, -90.0f, Ease.IN)
				.key(CLEAR_KNOCKS[1][2], new Vector3f(g.work()).add(0.0f, 1.5f + g.workLift(), 0.8f), g.workTwist(), Ease.OUT)
				.key(CLEAR_PULL, new Vector3f(g.work()).add(0.0f, g.workLift(), 0.0f), g.workTwist())
				.follow(CLEAR_PULL, CLEAR_HOME, onPart(g, CLEAR_TRAVEL, noLift))
				.key(CLEAR_UNROLL, new Vector3f(g.work()).add(0.3f, 1.8f, 1.2f), g.workTwist() * 0.5f, Ease.OUT)
				.key(1.0f, g.support(), 0.0f)
				.build();
	}

	/**
	 * One round, from the port and back to it (the gun's roll brings the hand there as loading starts): down off the
	 * screen for a round (FETCHED), back up to the port (AT_PORT), and in (PUSHED, the cue): thumbed up into a tube,
	 * pressed down into a breech, or tipped into a muzzle with the hand turned over and a shake.
	 */
	private static Path loadPath(Geometry g) {
		Vector3f port = g.port();
		Vector3f fetch = new Vector3f(port).add(FETCH);
		Keyframes.PathBuilder path = Keyframes.path(port).key(FETCHED, fetch, -30.0f);
		switch (g.loading()) {
			case TUBE -> path.key(AT_PORT, new Vector3f(port).add(0.0f, -1.6f, 2.4f), 10.0f)
					.key(0.62f, new Vector3f(port).add(0.0f, -0.7f, 0.7f), 0.0f)
					.key(PUSHED, new Vector3f(port).add(0.0f, -0.3f, -1.4f), 0.0f, Ease.IN);
			case BREECH -> path.key(AT_PORT, new Vector3f(port).add(0.0f, 2.6f, 1.0f), 130.0f)
					.key(0.62f, new Vector3f(port).add(0.0f, 1.6f, 0.4f), 140.0f)
					.key(PUSHED, new Vector3f(port).add(0.0f, 0.4f, 0.0f), 140.0f, Ease.IN);
			case MUZZLE -> path.key(0.5f, new Vector3f(port).add(0.0f, 1.0f, -3.5f), 40.0f)
					.key(0.6f, new Vector3f(port).add(0.0f, 0.5f, -2.0f), 115.0f)
					.key(0.66f, new Vector3f(port).add(0.0f, 0.8f, -2.3f), 100.0f, Ease.OUT)
					.key(PUSHED, new Vector3f(port).add(0.0f, 0.4f, -1.8f), 125.0f, Ease.IN);
		}
		return path.key(1.0f, port, 0.0f).build();
	}

	// ------------------------------------------------------------------------------------------------ output

	/** Turns the held shotgun for what it's doing: tugged by the pump, rolled over for loading, or inspected. */
	public static void applyGunPose(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		if (!(stack.getItem() instanceof ShotgunItem) || anchors == null) {
			return;
		} else if (GunsmithAnimation.owns(stack.getItem())) {
			GunsmithAnimation.applyGunPose(poseStack, stack, anchors, mirrored);
			return;
		}
		float i = since(inspectStart, INSPECT_MILLIS);
		if (i < 1.0f) {
			GunMotion.inspect(poseStack, i, (Util.getMillis() - inspectStart) / 1000.0f, anchors.grip(), mirrored);
			return;
		}
		Geometry g = geometry(stack.getItem());
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		float roll = rolled(stack);
		float shake = 0.0f;
		float pull = pumpTravel(stack, true);
		float clearing = 0.0f;
		float working = 0.0f;
		if (action != null) {
			float p = action.progress(now());
			long millis = action.ticks() * 50L;
			if (action.kind() == ShotgunAction.Kind.PUMP) {
				shake = GunMotion.jolt(p, HOME, millis, 0.9f) + GunMotion.jolt(p, BACK, millis, 0.4f);
				working = p < 1.0f ? PUMP_ENGAGED.sample(p) : 0.0f;
			} else if (action.kind() == ShotgunAction.Kind.LOAD) {
				shake = GunMotion.jolt(p, PUSHED, millis, 0.35f);
			} else if (action.kind() == ShotgunAction.Kind.CLEAR && p < 1.0f) {
				clearing = CLEAR_ENGAGED.sample(p);
				shake = GunMotion.jolt(p, CLEAR_KNOCKS[0][0], millis, 0.9f) + GunMotion.jolt(p, CLEAR_KNOCKS[1][0], millis, 1.0f)
						+ GunMotion.jolt(p, CLEAR_BACK, millis, 0.6f) + GunMotion.jolt(p, CLEAR_HOME, millis, 0.7f);
			}
		}
		// The pump tugs the muzzle down and the gun back a touch; loading turns the port to the off hand, working a homemade
		// gun's part turns it to the eye, and clearing a jam turns the action toward the eye.
		Vector3f load = g.loadPose();
		Vector3f work = g.workPose();
		poseStack.translate(0.0f, 0.0f, 0.015f * pull);
		GunMotion.turn(poseStack, anchors.grip(), load.x * roll + work.x * working + CLEAR_POSE.x * clearing + 3.0f * pull,
				load.y * roll + work.y * working + CLEAR_POSE.y * clearing,
				load.z * roll + work.z * working + CLEAR_POSE.z * clearing - 4.0f * pull,
				0.03f * (roll + clearing + working), 1.0f, shake, mirrored);
	}

	/** Where the off hand goes: working the action, knocking a jam loose, or fetching and pushing in rounds. */
	public static GunAnimation.HandOverride hand(ItemStack stack) {
		if (!(stack.getItem() instanceof ShotgunItem)) {
			return null;
		} else if (GunsmithAnimation.owns(stack.getItem())) {
			return GunsmithAnimation.hand(stack);
		}
		Item item = stack.getItem();
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		if (action != null && action.kind() == ShotgunAction.Kind.LOAD) {
			Keyframes.Sample at = path(item, Stroke.LOAD).sample(action.progress(now()));
			return new GunAnimation.HandOverride(false, at.point(), rolled, at.twist() * rolled);
		}
		float p = action == null ? 1.0f : action.progress(now());
		if (action != null && p < 1.0f && (action.kind() == ShotgunAction.Kind.PUMP || action.kind() == ShotgunAction.Kind.CLEAR)) {
			Keyframes.Sample at = path(item, action.kind() == ShotgunAction.Kind.PUMP ? Stroke.PUMP : Stroke.CLEAR).sample(p);
			return new GunAnimation.HandOverride(false, at.point(), 1.0f, at.twist());
		}
		// The flick on drawing and the inspection's press check: the off hand reaches to the moving part (on a pump gun
		// it's already there, on the fore-end), works it a little, and goes back.
		Geometry g = geometry(item);
		float pull = pumpTravel(stack, true);
		float flick = since(flourishStart, FLOURISH_MILLIS);
		float check = since(inspectStart, INSPECT_MILLIS);
		float blend;
		if (flick < 1.0f) {
			blend = GunMotion.smooth(span(flick, 0.0f, 0.2f)) * (1.0f - GunMotion.smooth(span(flick, 0.8f, 1.0f)));
		} else if (check < 1.0f) {
			blend = GunMotion.smooth(span(check, 0.55f, 0.6f)) * (1.0f - GunMotion.smooth(span(check, 0.78f, 0.83f)));
		} else {
			return null;
		}
		if (blend <= 0.0f) {
			return null;
		}
		return new GunAnimation.HandOverride(false, GunMotion.onGun(item, new Vector3f(g.work()).add(0.0f, 0.0f, g.travel() * pull)), blend,
				g.workTwist() * blend);
	}

	/**
	 * Draws, for the holder in first person, the round the off hand is loading and any hull flying out of the
	 * ejection port. The pose stack holds the gun's item pose (before its display transform).
	 */
	public static void submitShells(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack stack, boolean mirrored,
			LocalPlayer player) {
		if (!(stack.getItem() instanceof ShotgunItem)) {
			return;
		} else if (GunsmithAnimation.owns(stack.getItem())) {
			GunsmithAnimation.submitShells(poseStack, collector, light, stack, mirrored, player);
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
				// Held in the fingers just past the hand, nose first toward where it goes; as it's pushed it slides on in
				// (forward up a tube, down into a breech, down the bore of a tipped-up muzzle) and out of sight.
				float push = GunMotion.smooth(span(p, AT_PORT, PUSHED));
				Vector3f hand = pixelPath(item, Stroke.LOAD).sample(p).point();
				Vector3f at = switch (g.loading()) {
					case TUBE -> hand.add(0.0f, 0.7f, -0.6f - 1.0f * push);
					case BREECH -> hand.add(0.0f, -0.4f - 1.2f * push, -0.3f);
					case MUZZLE -> hand.add(0.0f, -0.3f, 0.6f + 1.6f * push);
				};
				submitShell(poseStack, collector, light, item, mirrored, player, action.shell(), "3d", at, 0.0f, 0.0f);
			}
			return;
		}
		if (action.chambering() || !g.ejects()) {
			// Nothing fired, so nothing to throw out (or a gun with no port to throw it from).
			return;
		}
		float eject = switch (action.kind()) {
			case PUMP -> PUMP_EJECT;
			case CLEAR -> CLEAR_BACK;
			default -> CYCLE_EJECT;
		};
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
