package com.jheinz.mymod.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.ModSounds;

/**
 * First-person handling animations:
 * <ul>
 * <li>racking a gun when it's drawn or after an empty reload (pistol slide, rifle charging handle),</li>
 * <li>working the AWP's bolt (lift, back, forward, down),</li>
 * <li>magazine reloads (the old magazine slides out, the hand fetches a new one and seats it),</li>
 * <li>loading the revolver, a round at a time or from a speedloader,</li>
 * <li>inspecting the gun on demand: turned to show its left side, rolled over to show the ejection side, a
 * press check of the slide, charging handle or bolt, then back.</li>
 * </ul>
 *
 * <p>Three things move together. Moving parts step through model frames chosen by the mymod:gun_animation
 * item model property (1-6: slide or bolt travel, 7-12: magazine leaving the well, 12 being gone). A hand
 * reaches over and works the part. And the whole gun reacts: it cants and turns toward the working hand,
 * gets tugged by the pull, and jolts when a part slams home. Sounds play at each step.
 */
public final class GunAnimation {
	public static final int FRAMES = 6;
	public static final int MAG_FRAMES = 6;
	/** Where the support hand goes to fetch a new magazine, relative to the old one's base (off the bottom of the screen). */
	private static final Vector3f FETCH_OFFSET = new Vector3f(-0.08f, -0.45f, 0.2f);

	private enum Kind {
		RACK, BOLT, RELOAD, LOAD_ROUND, SPEEDLOAD, INSPECT
	}

	/** How far a gun turns toward the working hand: degrees of roll (top to the left), yaw (muzzle left), pitch, and lift. */
	private record Pose(float roll, float yaw, float pitch, float lift) {
	}

	/**
	 * How a gun is handled.
	 *
	 * @param work          how it's made ready: RACK, BOLT, or null for none
	 * @param mainHandWorks whether the shooting hand works the action (else the support hand)
	 * @param workPoints    where that hand grips the moving part in each frame (item space, as in {@link GunHands})
	 * @param workPose      how the gun turns while being worked
	 * @param drag          how far the pull moves the gun toward the camera at full travel
	 * @param pullPitch     extra muzzle tip at full travel
	 * @param soundPitch    pitch for the handling sounds
	 * @param magPoint      the magazine's base (or the revolver's cylinder) where the support hand takes it
	 * @param magStep       how far the magazine base moves per frame as it leaves the well
	 * @param reloadPose    how the gun turns during a reload, bringing the magazine well to the support hand
	 */
	private record Motion(Kind work, boolean mainHandWorks, Vector3f[] workPoints, Pose workPose, float drag, float pullPitch,
			float soundPitch, Vector3f magPoint, Vector3f magStep, Pose reloadPose) {
	}

	private record Running(Kind kind, Motion motion, Item item, long startMillis, long durationMillis) {
		float progress() {
			return (Util.getMillis() - startMillis) / (float) durationMillis;
		}
	}

	private record Cue(float at, SoundEvent sound, float pitch) {
	}

	private static final long INSPECT_MILLIS = 3200;
	/** How far the action is drawn back for a press check, in frames. */
	private static final float PRESS_CHECK_TRAVEL = 2.5f;

	private static final Pose RIFLE_RELOAD = new Pose(-18, 8, 12, 0.04f);
	private static final Pose PISTOL_RELOAD = new Pose(-10, 10, 18, 0.05f);

	private static Running running;
	private static Running queued;
	private static float lastTickProgress = -1.0f;
	private static int lastSlot = -1;
	private static Item lastItem;
	private static boolean lastBoltOpen;
	private static int lastRounds = -1;

	private GunAnimation() {
	}

	private static Motion motion(Item gun) {
		if (gun == ModItems.GLOCK) {
			return slide(-0.030f, 0.293f, -0.092f, -0.011f, 0.000f, 0.101f, new Pose(28, 14, 12, 0.05f), -0.025f, 4, 1.0f,
					mag(-0.030f, -0.086f, -0.092f), mag(-0.002f, -0.056f, 0.023f), PISTOL_RELOAD);
		} else if (gun == ModItems.M1911) {
			return slide(-0.030f, 0.290f, -0.093f, -0.011f, 0.000f, 0.101f, new Pose(28, 14, 12, 0.05f), -0.025f, 4, 1.0f,
					mag(-0.031f, -0.098f, -0.087f), mag(-0.002f, -0.056f, 0.023f), PISTOL_RELOAD);
		} else if (gun == ModItems.M4) {
			return slide(-0.028f, 0.302f, 0.197f, -0.016f, 0.000f, 0.111f, new Pose(12, 10, 16, 0.05f), 0.03f, 5, 0.9f,
					mag(0.018f, -0.115f, -0.125f), mag(0.0f, -0.056f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.AKM) {
			return slide(0.022f, 0.276f, 0.048f, -0.018f, 0.000f, 0.130f, new Pose(35, 12, 10, 0.04f), 0.02f, 3, 0.85f,
					mag(-0.002f, -0.068f, -0.176f), mag(0.0f, -0.056f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.GALIL) {
			return slide(0.036f, 0.332f, -0.078f, -0.018f, 0.000f, 0.130f, new Pose(35, 12, 10, 0.04f), 0.02f, 3, 0.9f,
					mag(0.004f, -0.081f, -0.212f), mag(0.0f, -0.056f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.MP5) {
			// The MP5's handle is on the left, so it cants the other way.
			return slide(-0.013f, 0.334f, -0.497f, -0.019f, 0.000f, 0.134f, new Pose(-28, 6, 10, 0.03f), 0.02f, 3, 0.95f,
					mag(0.008f, -0.159f, -0.241f), mag(0.0f, -0.058f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.MAC10) {
			return slide(0.001f, 0.348f, -0.379f, -0.015f, 0.000f, 0.141f, new Pose(15, 10, 14, 0.05f), 0.02f, 4, 1.0f,
					mag(-0.020f, -0.191f, -0.181f), mag(0.0f, -0.061f, 0.0f), PISTOL_RELOAD);
		} else if (gun == ModItems.AWP) {
			return new Motion(Kind.BOLT, true, new Vector3f[] {
					mag(0.058f, 0.269f, 0.008f), mag(0.052f, 0.301f, 0.007f), mag(0.034f, 0.329f, 0.005f),
					mag(0.030f, 0.329f, 0.032f), mag(0.027f, 0.329f, 0.059f), mag(0.023f, 0.329f, 0.086f),
					mag(0.019f, 0.329f, 0.113f)},
					new Pose(18, -4, 4, 0.02f), 0.015f, 2, 1.0f,
					mag(-0.013f, 0.128f, -0.098f), mag(0.0f, -0.047f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.REVOLVER_357) {
			// Rounds go into the cylinder from the left; the gun rolls over to bring it to the support hand.
			return new Motion(null, false, null, null, 0, 0, 1.0f, mag(-0.041f, 0.276f, -0.411f), mag(0, 0, 0),
					new Pose(30, 10, 14, 0.05f));
		}
		return null;
	}

	private static Vector3f mag(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	/** A part the support hand pulls straight back: grip point at rest and its full travel. */
	private static Motion slide(float x, float y, float z, float dx, float dy, float dz, Pose pose, float drag, float pullPitch,
			float soundPitch, Vector3f magPoint, Vector3f magStep, Pose reloadPose) {
		Vector3f[] points = new Vector3f[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float f = k / (float) FRAMES;
			points[k] = new Vector3f(x + dx * f, y + dy * f, z + dz * f);
		}
		return new Motion(Kind.RACK, false, points, pose, drag, pullPitch, soundPitch, magPoint, magStep, reloadPose);
	}

	// ------------------------------------------------------------------------------------------------ control

	/** Called every client tick: starts animations when the held gun is drawn, reloaded or worked, and plays cues. */
	public static void tick(LocalPlayer player) {
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		Item item = held.getItem();
		int slot = player.getInventory().getSelectedSlot();
		boolean boltOpen = held.has(ModComponents.BOLT_OPEN);
		MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
		int rounds = loaded == null ? -1 : loaded.rounds();
		Motion motion = motion(item);

		if (slot != lastSlot || item != lastItem) {
			queued = null;
			start(motion == null ? null : motion.work(), motion, item, 0);
		} else if (motion != null && item instanceof GunItem gun && rounds > lastRounds) {
			if (gun.stats().singleRounds()) {
				boolean single = rounds - Math.max(lastRounds, 0) == 1;
				start(single ? Kind.LOAD_ROUND : Kind.SPEEDLOAD, motion, item,
						single ? 250 : (long) (gun.stats().reloadTicks() * 50L * GunItem.reloadFactor(player)));
			} else {
				start(Kind.RELOAD, motion, item,
						(long) ((gun.stats().reloadTicks() + gun.loadedMagazine(held).extraReloadTicks()) * 50L * GunItem.reloadFactor(player)));
				// An empty gun (or an open bolt) has to be made ready once the new magazine is in.
				if (lastRounds <= 0 || (lastBoltOpen && !boltOpen)) {
					queued = new Running(motion.work(), motion, item, 0, duration(motion.work()));
				}
			}
		} else if (lastBoltOpen && !boltOpen && motion != null && motion.work() == Kind.BOLT) {
			start(Kind.BOLT, motion, item, 0);
		}
		lastSlot = slot;
		lastItem = item;
		lastBoltOpen = boltOpen;
		lastRounds = rounds;

		// Firing or raising the scope puts an inspection away.
		if (running != null && running.kind() == Kind.INSPECT && (GunRecoil.kick() > 0.01f || GunScope.zoom(player) > 0.0f)) {
			running = null;
		}

		Running now = current(item);
		float p = now == null ? -1.0f : now.progress();
		if (now != null) {
			for (Cue cue : cues(now)) {
				if (cue.at() > lastTickProgress && cue.at() <= p) {
					player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
							0.9f, now.motion().soundPitch() * cue.pitch() * (0.95f + player.getRandom().nextFloat() * 0.1f), false);
				}
			}
		}
		lastTickProgress = p;
	}

	/** Whether this stack is in the middle of an animation (drawing, reloading, working the action, inspecting). */
	public static boolean busy(ItemStack stack) {
		return current(stack.getItem()) != null;
	}

	/** Starts inspecting the held gun, unless it's busy with another animation. */
	public static void inspect(LocalPlayer player) {
		if (player == null || GunScope.zoom(player) > 0.0f) {
			return;
		}
		Item item = player.getMainHandItem().getItem();
		Motion motion = motion(item);
		if (motion != null && current(item) == null && queued == null) {
			start(Kind.INSPECT, motion, item, INSPECT_MILLIS);
		}
	}

	private static long duration(Kind kind) {
		return kind == Kind.BOLT ? 900 : 1000;
	}

	private static void start(Kind kind, Motion motion, Item item, long durationMillis) {
		if (kind == null || motion == null) {
			running = null;
			return;
		}
		running = new Running(kind, motion, item, Util.getMillis(), durationMillis > 0 ? durationMillis : duration(kind));
		lastTickProgress = 0.0f;
	}

	/** The animation running on this item, moving on to the queued one when it finishes. */
	private static Running current(Item item) {
		if (running != null && running.progress() >= 1.0f) {
			running = queued == null ? null
					: new Running(queued.kind(), queued.motion(), queued.item(), Util.getMillis(), queued.durationMillis());
			queued = null;
			lastTickProgress = 0.0f;
		}
		return running != null && running.item() == item ? running : null;
	}

	private static Cue[] cues(Running run) {
		return switch (run.kind()) {
			case RACK -> new Cue[] {new Cue(0.22f, ModSounds.RACK_BACK, 1.0f), new Cue(0.58f, ModSounds.RACK_FORWARD, 1.0f)};
			case BOLT -> new Cue[] {new Cue(0.14f, ModSounds.BOLT_UP, 1.0f), new Cue(0.22f, ModSounds.BOLT_BACK, 1.0f),
					new Cue(0.44f, ModSounds.BOLT_FORWARD, 1.0f), new Cue(0.6f, ModSounds.BOLT_DOWN, 1.0f)};
			case RELOAD -> new Cue[] {new Cue(0.15f, ModSounds.MAG_OUT, 1.0f), new Cue(0.66f, ModSounds.MAG_IN, 1.0f)};
			case LOAD_ROUND -> new Cue[] {new Cue(0.45f, ModSounds.MAG_IN, 1.5f)};
			case SPEEDLOAD -> new Cue[] {new Cue(0.45f, ModSounds.MAG_IN, 1.15f), new Cue(0.75f, ModSounds.RACK_FORWARD, 1.3f)};
			case INSPECT -> run.motion().work() == Kind.BOLT
					? new Cue[] {new Cue(0.6f, ModSounds.BOLT_UP, 1.1f), new Cue(0.66f, ModSounds.BOLT_BACK, 1.2f),
							new Cue(0.74f, ModSounds.BOLT_FORWARD, 1.2f), new Cue(0.78f, ModSounds.BOLT_DOWN, 1.1f)}
					: run.motion().work() == Kind.RACK
							? new Cue[] {new Cue(0.6f, ModSounds.RACK_BACK, 1.2f), new Cue(0.75f, ModSounds.RACK_FORWARD, 1.25f)}
							// The revolver's cylinder gets a spin instead.
							: new Cue[] {new Cue(0.6f, ModSounds.MAG_IN, 1.7f), new Cue(0.66f, ModSounds.MAG_IN, 1.9f)};
		};
	}

	// ----------------------------------------------------------------------------------------------- timeline

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	/** How far through the stretch from {@code a} to {@code b} progress {@code p} is, 0 to 1. */
	private static float span(float p, float a, float b) {
		return Mth.clamp((p - a) / (b - a), 0.0f, 1.0f);
	}

	/** Slide or bolt position, continuously from 0 (at rest) to FRAMES (fully back). */
	private static float partPosition(Kind kind, float p) {
		return switch (kind) {
			// Reach (to 0.22), pull back (to 0.5), hold, slam forward (0.58-0.6), return.
			case RACK -> p < 0.22f ? 0.0f : p < 0.5f ? smooth(span(p, 0.22f, 0.5f)) * FRAMES : p < 0.58f ? FRAMES
					: (1.0f - span(p, 0.58f, 0.6f)) * FRAMES;
			// Reach, lift (to frame 2), draw back (to 6), hold, push forward (to 2), turn down (to 0), return.
			case BOLT -> p < 0.14f ? 0.0f : p < 0.22f ? smooth(span(p, 0.14f, 0.22f)) * 2.0f
					: p < 0.4f ? 2.0f + smooth(span(p, 0.22f, 0.4f)) * (FRAMES - 2) : p < 0.44f ? FRAMES
					: p < 0.58f ? FRAMES - smooth(span(p, 0.44f, 0.58f)) * (FRAMES - 2)
					: 2.0f - smooth(span(p, 0.58f, 0.64f)) * 2.0f;
			default -> 0.0f;
		};
	}

	/**
	 * Slide or bolt position during an inspection's press check: drawn back just far enough to see the
	 * chamber (0.6-0.68), held a beat, then let go (0.74). A bolt is lifted first and turned down after.
	 */
	private static float pressCheckPosition(Motion motion, float p) {
		if (motion.work() == Kind.RACK) {
			return p < 0.6f ? 0.0f : p < 0.68f ? smooth(span(p, 0.6f, 0.68f)) * PRESS_CHECK_TRAVEL : p < 0.74f ? PRESS_CHECK_TRAVEL
					: (1.0f - span(p, 0.74f, 0.76f)) * PRESS_CHECK_TRAVEL;
		} else if (motion.work() == Kind.BOLT) {
			return p < 0.6f ? 0.0f : p < 0.65f ? smooth(span(p, 0.6f, 0.65f)) * 2.0f
					: p < 0.7f ? 2.0f + smooth(span(p, 0.65f, 0.7f)) * (PRESS_CHECK_TRAVEL + 0.5f)
					: p < 0.74f ? 4.5f : p < 0.77f ? 4.5f - smooth(span(p, 0.74f, 0.77f)) * 2.5f
					: 2.0f - smooth(span(p, 0.77f, 0.81f)) * 2.0f;
		}
		return 0.0f;
	}

	/**
	 * Magazine position during a reload: 0 seated, 5 at the end of the well, 6 gone. It's pulled out (to 0.3),
	 * gone while the hand fetches a new one, then pushed home (0.58-0.68).
	 */
	private static float magPosition(float p) {
		return p < 0.15f ? 0.0f : p < 0.3f ? smooth(span(p, 0.15f, 0.3f)) * 5.0f : p < 0.33f ? 5.0f + span(p, 0.3f, 0.33f)
				: p < 0.56f ? 6.0f : p < 0.58f ? 6.0f - span(p, 0.56f, 0.58f) : 5.0f - smooth(span(p, 0.58f, 0.68f)) * 5.0f;
	}

	/** How far the gun is turned toward the working hand. */
	private static float engagement(Kind kind, float p) {
		return switch (kind) {
			case RACK -> smooth(span(p, 0.0f, 0.22f)) * (1.0f - smooth(span(p, 0.64f, 0.9f)));
			case BOLT -> smooth(span(p, 0.0f, 0.14f)) * (1.0f - smooth(span(p, 0.66f, 0.9f)));
			case RELOAD -> smooth(span(p, 0.0f, 0.15f)) * (1.0f - smooth(span(p, 0.76f, 0.96f)));
			case LOAD_ROUND -> smooth(span(p, 0.0f, 0.3f)) * (1.0f - smooth(span(p, 0.6f, 1.0f)));
			case SPEEDLOAD -> smooth(span(p, 0.0f, 0.2f)) * (1.0f - smooth(span(p, 0.75f, 0.97f)));
			case INSPECT -> smooth(span(p, 0.0f, 0.12f)) * (1.0f - smooth(span(p, 0.86f, 1.0f)));
		};
	}

	/** Springy shake after each part hits its stop. */
	private static float jolt(Running run, float p) {
		float[][] hits = switch (run.kind()) {
			case RACK -> new float[][] {{0.5f, 0.4f}, {0.6f, 1.0f}};
			case BOLT -> new float[][] {{0.4f, 0.4f}, {0.58f, 0.8f}, {0.64f, 0.5f}};
			case RELOAD -> new float[][] {{0.16f, 0.3f}, {0.68f, 1.0f}};
			case LOAD_ROUND -> new float[][] {{0.45f, 0.3f}};
			case SPEEDLOAD -> new float[][] {{0.45f, 0.5f}, {0.75f, 0.8f}};
			case INSPECT -> new float[][] {{0.76f, 0.45f}};
		};
		float total = 0.0f;
		for (float[] hit : hits) {
			float t = (p - hit[0]) * run.durationMillis() / 1000.0f;
			if (t >= 0.0f) {
				total += hit[1] * Mth.sin(t * Mth.TWO_PI * 7.0f) * (float) Math.exp(-t / 0.07f);
			}
		}
		return total;
	}

	// ------------------------------------------------------------------------------------------------- output

	/** The model frame for this stack, for the item model property (0 at rest). */
	public static float frame(ItemStack stack, ItemOwner owner, LocalPlayer player) {
		if (player == null || owner != player || stack != player.getMainHandItem()) {
			return 0.0f;
		}
		Running run = current(stack.getItem());
		if (run == null) {
			return 0.0f;
		}
		float p = run.progress();
		if (run.kind() == Kind.RELOAD) {
			float m = magPosition(p);
			return m < 0.5f ? 0.0f : FRAMES + Mth.clamp(Math.round(m), 1, MAG_FRAMES);
		} else if (run.kind() == Kind.INSPECT) {
			return Math.round(pressCheckPosition(run.motion(), p));
		}
		return Math.round(partPosition(run.kind(), p));
	}

	/**
	 * Turns, lifts, tugs and shakes the gun for the running animation, pivoting on the hand that keeps
	 * hold of it. Applied to the item's pose, so the gun and both hands move together.
	 */
	public static void applyGunPose(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		Running run = current(stack.getItem());
		if (run == null || anchors == null) {
			return;
		}
		float p = run.progress();
		Motion motion = run.motion();
		if (run.kind() == Kind.INSPECT) {
			applyInspectPose(poseStack, run, anchors, mirrored);
			return;
		}
		boolean working = run.kind() == Kind.RACK || run.kind() == Kind.BOLT;
		Pose pose = working ? motion.workPose() : motion.reloadPose();
		float engaged = engagement(run.kind(), p);
		float pull = working ? partPosition(run.kind(), p) / FRAMES : 0.0f;
		float shake = jolt(run, p);
		float side = mirrored ? -1.0f : 1.0f;
		Vector3f pivot = working && motion.mainHandWorks() ? anchors.support() : anchors.grip();

		poseStack.translate(0.0f, pose.lift() * engaged, (working ? motion.drag() * pull : 0.0f) - shake * 0.012f);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(pose.pitch() * engaged + (working ? motion.pullPitch() * pull : 0.0f) + shake * 2.5f),
				(float) Math.toRadians(pose.yaw() * engaged * side),
				(float) Math.toRadians(pose.roll() * engaged * side + shake * 1.5f)),
				pivot.x * side, pivot.y, pivot.z);
	}

	/**
	 * Inspecting: the gun comes in toward the middle of the screen turned muzzle-left, showing its left
	 * side, then rolls over to bring the ejection side up to the camera for the press check, and goes back.
	 */
	private static void applyInspectPose(PoseStack poseStack, Running run, GunHands.Anchors anchors, boolean mirrored) {
		float p = run.progress();
		float raised = engagement(Kind.INSPECT, p);
		float flipped = smooth(span(p, 0.34f, 0.5f)) * (1.0f - smooth(span(p, 0.84f, 0.97f)));
		float checking = smooth(span(p, 0.58f, 0.64f)) * (1.0f - smooth(span(p, 0.78f, 0.86f)));
		float shake = jolt(run, p);
		float side = mirrored ? -1.0f : 1.0f;
		// A slow drift while it's held up, so it doesn't look frozen.
		float t = (Util.getMillis() - run.startMillis()) / 1000.0f;
		float drift = Mth.sin(t * 2.3f) * raised;

		float roll = -14.0f * raised + 88.0f * flipped + drift * 2.0f + shake * 2.0f;
		float yaw = 38.0f * raised - 14.0f * flipped + drift;
		float pitch = 6.0f * raised + 8.0f * flipped + 5.0f * checking + shake * 2.0f;
		Vector3f pivot = anchors.grip();

		poseStack.translate(-0.1f * raised * side, 0.05f * raised + 0.03f * flipped, 0.07f * raised - shake * 0.01f);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(pitch), (float) Math.toRadians(yaw * side), (float) Math.toRadians(roll * side)),
				pivot.x * side, pivot.y, pivot.z);
	}

	/** A hand being pulled off its hold toward {@code target} by {@code blend} (0 to 1). */
	public record HandOverride(boolean mainHand, Vector3f target, float blend) {
	}

	/** Where a hand should be pulled to for the running animation on this stack, if any. */
	public static HandOverride hand(ItemStack stack) {
		Running run = current(stack.getItem());
		if (run == null) {
			return null;
		}
		float p = run.progress();
		Motion motion = run.motion();
		return switch (run.kind()) {
			case RACK, BOLT -> {
				float blend = run.kind() == Kind.RACK ? smooth(span(p, 0.0f, 0.2f)) * (1.0f - smooth(span(p, 0.64f, 0.86f)))
						: smooth(span(p, 0.0f, 0.13f)) * (1.0f - smooth(span(p, 0.66f, 0.86f)));
				// The hand follows the part continuously, between its frame positions.
				float position = partPosition(run.kind(), p);
				int lower = Mth.clamp((int) Math.floor(position), 0, FRAMES);
				int upper = Math.min(lower + 1, FRAMES);
				Vector3f target = new Vector3f(motion.workPoints()[lower]).lerp(motion.workPoints()[upper], position - lower);
				yield new HandOverride(motion.mainHandWorks(), target, blend);
			}
			case RELOAD -> {
				float blend = smooth(span(p, 0.0f, 0.13f)) * (1.0f - smooth(span(p, 0.74f, 0.92f)));
				Vector3f wellEnd = new Vector3f(motion.magStep()).mul(5.0f).add(motion.magPoint());
				Vector3f fetch = new Vector3f(wellEnd).add(FETCH_OFFSET);
				Vector3f target;
				if (p < 0.3f || p >= 0.58f) {
					// Riding the magazine out of (or into) the well.
					target = new Vector3f(motion.magStep()).mul(Math.min(magPosition(p), 5.0f)).add(motion.magPoint());
				} else {
					// Down out of sight for a fresh magazine and back.
					float away = smooth(span(p, 0.3f, 0.4f)) * (1.0f - smooth(span(p, 0.46f, 0.58f)));
					target = new Vector3f(wellEnd).lerp(fetch, away);
				}
				yield new HandOverride(false, target, blend);
			}
			case LOAD_ROUND, SPEEDLOAD -> {
				// Thumb the round(s) into the front of the cylinder: in, a push, and back.
				float push = run.kind() == Kind.LOAD_ROUND ? smooth(span(p, 0.25f, 0.45f)) * (1.0f - smooth(span(p, 0.5f, 0.7f)))
						: smooth(span(p, 0.3f, 0.45f)) * (1.0f - smooth(span(p, 0.55f, 0.7f)));
				Vector3f target = new Vector3f(motion.magPoint()).add(0.0f, -0.02f * (1.0f - push), 0.05f - 0.05f * push);
				yield new HandOverride(false, target, engagement(run.kind(), p));
			}
			case INSPECT -> {
				if (motion.work() == null) {
					yield null;
				}
				// The press check: the working hand goes to the slide or bolt and draws it back a little.
				float blend = smooth(span(p, 0.54f, 0.6f)) * (1.0f - smooth(span(p, 0.8f, 0.87f)));
				float position = pressCheckPosition(motion, p);
				int lower = Mth.clamp((int) Math.floor(position), 0, FRAMES);
				int upper = Math.min(lower + 1, FRAMES);
				Vector3f target = new Vector3f(motion.workPoints()[lower]).lerp(motion.workPoints()[upper], position - lower);
				yield new HandOverride(motion.mainHandWorks(), target, blend);
			}
		};
	}
}
