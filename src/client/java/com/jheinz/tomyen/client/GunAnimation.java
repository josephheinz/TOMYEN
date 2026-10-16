package com.jheinz.tomyen.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.ModSounds;
import com.jheinz.tomyen.client.Keyframes.Ease;
import com.jheinz.tomyen.client.Keyframes.Path;
import com.jheinz.tomyen.client.Keyframes.Track;
import com.jheinz.tomyen.client.gunsmith.GunsmithClient;
import com.jheinz.tomyen.client.launcher.LauncherAnimation;
import com.jheinz.tomyen.gunsmith.Gunsmith;
import com.jheinz.tomyen.client.lmg.M249Animation;
import com.jheinz.tomyen.client.shotgun.ShotgunAnimation;
import com.jheinz.tomyen.launcher.RocketLauncherItem;
import com.jheinz.tomyen.lmg.MachineGunItem;
import com.jheinz.tomyen.napalm.FlamethrowerItem;
import com.jheinz.tomyen.napalm.Napalm;
import com.jheinz.tomyen.shotgun.ShotgunItem;

/**
 * First-person handling animations:
 * <ul>
 * <li>racking a gun when it's drawn or after an empty reload (pistol slide, rifle charging handle): the off hand reaches
 * over, pinches the part, draws it back and lets it fly home, flicking off it;</li>
 * <li>working the AWP's bolt (lift, back, forward, down) with the shooting hand;</li>
 * <li>magazine reloads: the off hand drops to the magazine, yanks it out and flings it away off the bottom of the
 * screen, comes back with a fresh one, starts it into the well and slaps it home;</li>
 * <li>loading the revolver, a round at a time (thumbed into the cylinder, the hand staying by it while rounds keep
 * coming) or from a speedloader (pushed in, twisted, pulled away, and the cylinder flicked shut);</li>
 * <li>refuelling the flamethrower: the off hand runs down the hose to the tanks on the back, swaps the canister and
 * comes back up to open the fuel valve;</li>
 * <li>inspecting the gun on demand: turned to show its left side, rolled over to show the ejection side, a
 * press check of the slide, charging handle or bolt, then back.</li>
 * </ul>
 * The shotguns (and the homemade guns), the M249 and the RPG-7 hand all of this to ShotgunAnimation, M249Animation and
 * LauncherAnimation.
 *
 * <p>Three things move together, all keyframed ({@link Keyframes}) on the same timeline as the sound cues. Moving parts
 * step through model frames chosen by the tomyen:gun_animation item model property (1-6: slide or bolt travel, 7-12:
 * magazine leaving the well, 12 being gone). A hand follows its path, riding the part while it works it. And the whole
 * gun reacts: it cants and turns toward the working hand, gets tugged by the pull, and jolts when a part slams home.
 */
public final class GunAnimation {
	public static final int FRAMES = 6;
	public static final int MAG_FRAMES = 6;
	/** Where the support hand goes to fetch a new magazine, relative to the old one's base (off the bottom of the screen). */
	private static final Vector3f FETCH_OFFSET = new Vector3f(-0.08f, -0.45f, 0.2f);

	private enum Kind {
		RACK, BOLT, RELOAD, LOAD_ROUND, SPEEDLOAD, REFUEL, INSPECT
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
	 * @param workTwist     how the working hand is turned to take hold of the part (degrees, see {@link GunHands#poseArm})
	 * @param workPose      how the gun turns while being worked
	 * @param drag          how far the pull moves the gun toward the camera at full travel
	 * @param pullPitch     extra muzzle tip at full travel
	 * @param soundPitch    pitch for the handling sounds
	 * @param magPoint      the magazine's base (or the revolver's cylinder) where the support hand takes it
	 * @param magStep       how far the magazine base moves per frame as it leaves the well
	 * @param reloadPose    how the gun turns during a reload, bringing the magazine well to the support hand
	 */
	private record Motion(Kind work, boolean mainHandWorks, Vector3f[] workPoints, float workTwist, Pose workPose, float drag,
			float pullPitch, float soundPitch, Vector3f magPoint, Vector3f magStep, Pose reloadPose) {
		/** Where the working hand holds the part at {@code frame} (0 to FRAMES, between frames too). */
		Vector3f workPoint(float frame) {
			int lower = Mth.clamp((int) Math.floor(frame), 0, FRAMES);
			int upper = Math.min(lower + 1, FRAMES);
			return new Vector3f(workPoints[lower]).lerp(workPoints[upper], Mth.clamp(frame - lower, 0.0f, 1.0f));
		}

		/** Where the magazine's base is with the magazine {@code m} frames out of the well. */
		Vector3f mag(float m) {
			return new Vector3f(magStep).mul(m).add(magPoint);
		}
	}

	/** @param chained a round loaded straight after another, so the hand is already at the cylinder */
	private record Running(Kind kind, Motion motion, Item item, long startMillis, long durationMillis, boolean chained) {
		float progress() {
			return (Util.getMillis() - startMillis) / (float) durationMillis;
		}
	}

	private record Cue(float at, SoundEvent sound, float pitch) {
	}

	private static final long INSPECT_MILLIS = 3200;
	/** One revolver round: thumbed in by the middle, the hand back at rest by the end unless another round follows. */
	private static final long ROUND_MILLIS = 500;
	/** How far the action is drawn back for a press check, in frames. */
	private static final float PRESS_CHECK_TRAVEL = 2.5f;

	private static final Pose RIFLE_RELOAD = new Pose(-26, 12, 14, 0.05f);
	private static final Pose PISTOL_RELOAD = new Pose(-16, 14, 20, 0.06f);
	/** The flamethrower dips and turns to the off side while the hand reaches back for the tanks, then shows its valve. */
	private static final Pose REFUEL_POSE = new Pose(14, 16, -8, 0.02f);

	// ------------------------------------------------------------------------------------------------- timelines

	/** Slide or handle position (frames) through a rack: reached (0.22), pulled back (0.5), held, let go to slam home. */
	private static final Track RACK_PART = Keyframes.track(0.0f).hold(0.22f).key(0.5f, FRAMES).hold(0.58f).key(0.6f, 0.0f, Ease.IN)
			.build();
	/** Bolt position (frames): lifted (to 2), drawn back (to 6), held, pushed forward (to 2), turned down. */
	private static final Track BOLT_PART = Keyframes.track(0.0f).hold(0.14f).key(0.22f, 2.0f).key(0.4f, FRAMES).hold(0.44f)
			.key(0.58f, 2.0f).key(0.64f, 0.0f).build();
	/**
	 * Magazine position during a reload: 0 seated, 5 at the end of the well, 6 gone. Yanked out (0.15-0.3), gone while
	 * the hand fetches a new one, started into the well (0.58-0.63), then slapped home (0.655-0.67, with the MAG_IN cue).
	 */
	private static final Track MAG = Keyframes.track(0.0f).hold(0.15f).key(0.3f, 5.0f, Ease.OUT).key(0.33f, 6.0f, Ease.LINEAR)
			.hold(0.56f).key(0.58f, 5.0f, Ease.LINEAR).key(0.63f, 2.0f).hold(0.655f).key(0.67f, 0.0f, Ease.IN).build();
	/** The gun's tug as the magazine is yanked (down) and slapped home (up), -1 to 1. */
	private static final Track RELOAD_TUG = Keyframes.track(0.0f).hold(0.15f).key(0.19f, -1.0f, Ease.OUT).key(0.3f, 0.0f)
			.hold(0.655f).key(0.67f, 1.0f, Ease.IN).key(0.78f, 0.0f).build();

	/** How far the gun is turned toward the working hand, for each kind of handling. */
	private static final Map<Kind, Track> ENGAGEMENT = new EnumMap<>(Map.of(
			Kind.RACK, Keyframes.track(0.0f).key(0.22f, 1.0f).hold(0.64f).key(0.9f, 0.0f).build(),
			Kind.BOLT, Keyframes.track(0.0f).key(0.14f, 1.0f).hold(0.66f).key(0.9f, 0.0f).build(),
			Kind.RELOAD, Keyframes.track(0.0f).key(0.15f, 1.0f).hold(0.76f).key(0.96f, 0.0f).build(),
			Kind.LOAD_ROUND, Keyframes.track(0.0f).key(0.2f, 1.0f).hold(0.5f).key(1.0f, 0.0f).build(),
			Kind.SPEEDLOAD, Keyframes.track(0.0f).key(0.2f, 1.0f).hold(0.75f).key(0.97f, 0.0f).build(),
			Kind.REFUEL, Keyframes.track(0.0f).key(0.12f, 1.0f).hold(0.84f).key(0.98f, 0.0f).build(),
			Kind.INSPECT, Keyframes.track(0.0f).key(0.12f, 1.0f).hold(0.86f).key(1.0f, 0.0f).build()));
	/** A round loaded straight after another: the gun stays turned until the hand has pushed it in. */
	private static final Track CHAINED_ROUND = Keyframes.track(1.0f).hold(0.5f).key(1.0f, 0.0f).build();

	/** Each gun's hand paths (item space), built from its Motion the first time they're needed. */
	private static final Map<Item, Map<Kind, Path>> PATHS = new HashMap<>();

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
			return slide(-0.030f, 0.293f, -0.092f, -0.011f, 0.000f, 0.101f, 150, new Pose(28, 14, 12, 0.05f), -0.025f, 4, 1.0f,
					mag(-0.030f, -0.086f, -0.092f), mag(-0.002f, -0.056f, 0.023f), PISTOL_RELOAD);
		} else if (gun == ModItems.M1911) {
			return slide(-0.030f, 0.290f, -0.093f, -0.011f, 0.000f, 0.101f, 150, new Pose(28, 14, 12, 0.05f), -0.025f, 4, 1.0f,
					mag(-0.031f, -0.098f, -0.087f), mag(-0.002f, -0.056f, 0.023f), PISTOL_RELOAD);
		} else if (gun == ModItems.M4) {
			// The charging handle is pinched from behind, at the back of the upper.
			return slide(-0.028f, 0.302f, 0.197f, -0.016f, 0.000f, 0.111f, 120, new Pose(12, 10, 16, 0.05f), 0.03f, 5, 0.9f,
					mag(0.018f, -0.115f, -0.125f), mag(0.0f, -0.056f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.AKM) {
			return slide(0.022f, 0.276f, 0.048f, -0.018f, 0.000f, 0.130f, 150, new Pose(35, 12, 10, 0.04f), 0.02f, 3, 0.85f,
					mag(-0.002f, -0.068f, -0.176f), mag(0.0f, -0.056f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.GALIL) {
			return slide(0.036f, 0.332f, -0.078f, -0.018f, 0.000f, 0.130f, 150, new Pose(35, 12, 10, 0.04f), 0.02f, 3, 0.9f,
					mag(0.004f, -0.081f, -0.212f), mag(0.0f, -0.056f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.MP5) {
			// The MP5's handle is on the left, so it cants the other way and the hand takes it from the side.
			return slide(-0.013f, 0.334f, -0.497f, -0.019f, 0.000f, 0.134f, 60, new Pose(-28, 6, 10, 0.03f), 0.02f, 3, 0.95f,
					mag(0.008f, -0.159f, -0.241f), mag(0.0f, -0.058f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.MAC10) {
			return slide(0.001f, 0.348f, -0.379f, -0.015f, 0.000f, 0.141f, 140, new Pose(15, 10, 14, 0.05f), 0.02f, 4, 1.0f,
					mag(-0.020f, -0.191f, -0.181f), mag(0.0f, -0.061f, 0.0f), PISTOL_RELOAD);
		} else if (gun == ModItems.AWP) {
			return new Motion(Kind.BOLT, true, new Vector3f[] {
					mag(0.058f, 0.269f, 0.008f), mag(0.052f, 0.301f, 0.007f), mag(0.034f, 0.329f, 0.005f),
					mag(0.030f, 0.329f, 0.032f), mag(0.027f, 0.329f, 0.059f), mag(0.023f, 0.329f, 0.086f),
					mag(0.019f, 0.329f, 0.113f)}, -50,
					new Pose(18, -4, 4, 0.02f), 0.015f, 2, 1.0f,
					mag(-0.013f, 0.128f, -0.098f), mag(0.0f, -0.047f, 0.0f), RIFLE_RELOAD);
		} else if (gun == ModItems.REVOLVER_357) {
			// Rounds go into the cylinder from the left; the gun rolls over to bring it to the support hand.
			return new Motion(null, false, null, 0, null, 0, 0, 1.0f, mag(-0.041f, 0.276f, -0.411f), mag(0, 0, 0),
					new Pose(34, 12, 16, 0.05f));
		} else if (gun == Gunsmith.NAIL_GUN) {
			// No action to work: the servo drives it. The strip goes in under the body like an SMG's (tools/gen_gunsmith.py).
			return new Motion(null, false, null, 0, null, 0, 0, 1.1f, GunsmithClient.nailGunMagPoint(), GunsmithClient.nailGunMagStep(),
					RIFLE_RELOAD);
		} else if (gun == Napalm.FLAMETHROWER) {
			// No action to work; the valve knob on top (tools/gen_napalm.py) is where the hand finishes a refuel.
			return new Motion(null, false, null, 0, null, 0, 0, 0.9f, GunMotion.onGun(gun, new Vector3f(8.0f, 10.8f, 10.2f)),
					mag(0, 0, 0), REFUEL_POSE);
		}
		return null;
	}

	private static Vector3f mag(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	/** A part the support hand pulls straight back: grip point at rest and its full travel. */
	private static Motion slide(float x, float y, float z, float dx, float dy, float dz, float twist, Pose pose, float drag,
			float pullPitch, float soundPitch, Vector3f magPoint, Vector3f magStep, Pose reloadPose) {
		Vector3f[] points = new Vector3f[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float f = k / (float) FRAMES;
			points[k] = new Vector3f(x + dx * f, y + dy * f, z + dz * f);
		}
		return new Motion(Kind.RACK, false, points, twist, pose, drag, pullPitch, soundPitch, magPoint, magStep, reloadPose);
	}

	// ----------------------------------------------------------------------------------------------- hand paths

	/** The working hand's path through an animation, in item space (null for none, e.g. a revolver's draw). */
	private static Path path(Item item, Motion motion, Kind kind, boolean chained) {
		if (chained) {
			// Not worth caching: one variant, and only while rounds keep coming.
			return roundPath(GunHands.anchors(item), motion, true);
		}
		return PATHS.computeIfAbsent(item, i -> new EnumMap<>(Kind.class)).computeIfAbsent(kind, k -> buildPath(item, motion, k));
	}

	private static Path buildPath(Item item, Motion motion, Kind kind) {
		GunHands.Anchors anchors = GunHands.anchors(item);
		if (anchors == null) {
			return null;
		}
		return switch (kind) {
			case RACK -> rackPath(anchors, motion);
			case BOLT -> boltPath(anchors, motion);
			case RELOAD -> reloadPath(anchors, motion);
			case LOAD_ROUND -> roundPath(anchors, motion, false);
			case SPEEDLOAD -> speedloadPath(anchors, motion);
			case REFUEL -> refuelPath(item, anchors, motion);
			case INSPECT -> null;
		};
	}

	/**
	 * Racking: the hand comes over the top and pinches the slide or handle (0.22), draws it back with it (to 0.5), holds,
	 * and lets go: the part slams home and the hand flicks up and back off it, then returns to its hold.
	 */
	private static Path rackPath(GunHands.Anchors anchors, Motion motion) {
		Vector3f rest = motion.mainHandWorks() ? anchors.grip() : anchors.support();
		Vector3f grab = motion.workPoint(0.0f);
		Vector3f back = motion.workPoint(FRAMES);
		return Keyframes.path(rest)
				.key(0.15f, new Vector3f(grab).add(0.0f, 0.03f, 0.03f), motion.workTwist() * 0.8f)
				.key(0.22f, grab, motion.workTwist())
				.follow(0.22f, 0.5f, t -> motion.workPoint(RACK_PART.sample(t)))
				.hold(0.58f)
				.key(0.63f, new Vector3f(back).add(0.0f, 0.025f, 0.04f), motion.workTwist() * 0.6f, Ease.OUT)
				.hold(0.68f)
				.key(0.88f, rest, 0.0f)
				.build();
	}

	/** Working a bolt: the shooting hand takes the handle, rides it up, back, forward and down, and goes back to the grip. */
	private static Path boltPath(GunHands.Anchors anchors, Motion motion) {
		Vector3f rest = motion.mainHandWorks() ? anchors.grip() : anchors.support();
		Vector3f grab = motion.workPoint(0.0f);
		return Keyframes.path(rest)
				.key(0.11f, new Vector3f(grab).add(0.025f, 0.0f, 0.025f), motion.workTwist() * 0.7f)
				.key(0.14f, grab, motion.workTwist())
				.follow(0.14f, 0.64f, t -> motion.workPoint(BOLT_PART.sample(t)))
				.key(0.7f, new Vector3f(grab).add(0.025f, 0.015f, 0.02f), motion.workTwist() * 0.5f, Ease.OUT)
				.key(0.86f, rest, 0.0f)
				.build();
	}

	/**
	 * A magazine swap: the off hand drops to the magazine and takes it (0.15), yanks it out with it (to 0.3), flings it
	 * away down off the screen and fetches a fresh one (0.36-0.48), brings it up to the well and starts it in (0.56-0.63),
	 * drops back a hand's breadth and slaps it home (0.67, with the MAG_IN cue), and goes back to its hold.
	 */
	private static Path reloadPath(GunHands.Anchors anchors, Motion motion) {
		Vector3f rest = anchors.support();
		Vector3f wellEnd = motion.mag(5.0f);
		Vector3f fetch = new Vector3f(wellEnd).add(FETCH_OFFSET);
		return Keyframes.path(rest)
				.key(0.1f, motion.mag(0.0f).add(0.0f, -0.03f, 0.015f), 35.0f)
				.key(0.15f, motion.mag(0.0f), 50.0f)
				.follow(0.15f, 0.3f, t -> motion.mag(MAG.sample(t)))
				.key(0.36f, new Vector3f(wellEnd).lerp(fetch, 0.55f).add(-0.04f, 0.0f, 0.0f), 95.0f, Ease.OUT)
				.key(0.42f, fetch, 70.0f)
				.hold(0.48f)
				.key(0.56f, motion.mag(5.8f), 45.0f)
				.key(0.58f, motion.mag(5.0f), 45.0f, Ease.LINEAR)
				.follow(0.58f, 0.63f, t -> motion.mag(MAG.sample(t)))
				.key(0.655f, motion.mag(3.4f), 10.0f)
				.key(0.67f, motion.mag(-0.4f), 0.0f, Ease.IN)
				.key(0.7f, motion.mag(0.6f), 0.0f, Ease.OUT)
				.hold(0.74f)
				.key(0.92f, rest, 0.0f)
				.build();
	}

	/**
	 * One revolver round: the hand comes down beside the cylinder with a round (0.18), lines it up behind a chamber
	 * (0.3), thumbs it in (0.4, the cue), and drops back ready for the next (0.5); with no next round it goes back to its
	 * hold. A chained round starts from that ready point, so held loading is a steady rhythm.
	 */
	private static Path roundPath(GunHands.Anchors anchors, Motion motion, boolean chained) {
		Vector3f cylinder = motion.magPoint();
		Vector3f ready = new Vector3f(cylinder).add(-0.025f, -0.075f, 0.09f);
		Vector3f behind = new Vector3f(cylinder).add(0.0f, -0.015f, 0.065f);
		Vector3f pushed = new Vector3f(cylinder).add(0.0f, -0.005f, 0.005f);
		Keyframes.PathBuilder path = chained
				? Keyframes.path(ready, 30.0f).key(0.18f, new Vector3f(ready).add(0.0f, -0.025f, 0.02f), 30.0f)
				: Keyframes.path(anchors.support()).key(0.18f, ready, 30.0f);
		return path.key(0.3f, behind, 40.0f)
				.key(0.4f, pushed, 40.0f, Ease.IN)
				.key(0.5f, ready, 30.0f, Ease.OUT)
				.key(1.0f, anchors.support(), 0.0f)
				.build();
	}

	/**
	 * A speedloader: the hand drops off the screen for it (to 0.26), lines it up behind the cylinder and pushes the
	 * rounds in (0.46, the cue), twists the knob to let them go, pulls the empty loader away, and flicks the cylinder shut
	 * from the left (0.75, the second cue).
	 */
	private static Path speedloadPath(GunHands.Anchors anchors, Motion motion) {
		Vector3f cylinder = motion.magPoint();
		return Keyframes.path(anchors.support())
				.key(0.14f, new Vector3f(cylinder).add(-0.06f, -0.42f, 0.15f), 40.0f)
				.hold(0.26f)
				.key(0.38f, new Vector3f(cylinder).add(0.0f, -0.01f, 0.07f), 20.0f)
				.key(0.46f, new Vector3f(cylinder).add(0.0f, -0.005f, 0.01f), 20.0f, Ease.IN)
				.key(0.54f, new Vector3f(cylinder).add(0.0f, -0.005f, 0.01f), 110.0f)
				.key(0.62f, new Vector3f(cylinder).add(-0.02f, -0.06f, 0.1f), 90.0f, Ease.OUT)
				.key(0.7f, new Vector3f(cylinder).add(-0.06f, -0.01f, 0.0f), 60.0f)
				.key(0.75f, new Vector3f(cylinder).add(0.0f, -0.005f, 0.0f), 30.0f, Ease.IN)
				.key(0.8f, new Vector3f(cylinder).add(0.01f, -0.035f, 0.03f), 10.0f, Ease.OUT)
				.key(0.95f, anchors.support(), 0.0f)
				.build();
	}

	/**
	 * Refuelling the flamethrower: the off hand lets go of the front grip, runs back along the hose (0.14) and down off
	 * the screen to the tanks on the back (0.24), works the canister loose and the new one on (to 0.52), comes back up
	 * over the wand to the valve knob (0.68) and turns it open (0.76, the click), and takes the front grip again.
	 */
	private static Path refuelPath(Item item, GunHands.Anchors anchors, Motion motion) {
		Function<Vector3f, Vector3f> model = point -> GunMotion.onGun(item, point);
		Vector3f hose = model.apply(new Vector3f(8.0f, 8.6f, 16.0f));
		Vector3f tanks = new Vector3f(hose).add(-0.3f, -0.6f, 0.35f);
		Vector3f knob = motion.magPoint();
		return Keyframes.path(anchors.support())
				.key(0.14f, hose, 60.0f)
				.key(0.24f, tanks, 90.0f, Ease.OUT)
				.key(0.34f, new Vector3f(tanks).add(0.03f, 0.05f, 0.0f), 130.0f)
				.key(0.44f, new Vector3f(tanks).add(-0.02f, -0.03f, 0.02f), 60.0f)
				.key(0.52f, new Vector3f(tanks).add(0.0f, 0.04f, 0.0f), 90.0f)
				.key(0.62f, new Vector3f(knob).add(0.0f, 0.05f, 0.02f), 150.0f)
				.key(0.68f, new Vector3f(knob).add(0.0f, 0.012f, 0.0f), 150.0f)
				.key(0.76f, new Vector3f(knob).add(0.0f, 0.012f, 0.0f), 240.0f, Ease.IN)
				.key(0.8f, new Vector3f(knob).add(0.0f, 0.045f, 0.015f), 200.0f, Ease.OUT)
				.key(0.94f, anchors.support(), 0.0f)
				.build();
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
			if (item instanceof FlamethrowerItem) {
				start(Kind.REFUEL, motion, item, (long) (gun.stats().reloadTicks() * 50L * GunItem.reloadFactor(player)));
			} else if (gun.stats().singleRounds()) {
				boolean single = rounds - Math.max(lastRounds, 0) == 1;
				if (single) {
					// Straight on from the last round's push: the hand is still at the cylinder.
					boolean chained = running != null && running.kind() == Kind.LOAD_ROUND && running.item() == item
							&& running.progress() < 1.0f;
					start(Kind.LOAD_ROUND, motion, item, ROUND_MILLIS, chained);
				} else {
					start(Kind.SPEEDLOAD, motion, item, (long) (gun.stats().reloadTicks() * 50L * GunItem.reloadFactor(player)));
				}
			} else {
				start(Kind.RELOAD, motion, item,
						(long) ((gun.stats().reloadTicks() + gun.loadedMagazine(held).extraReloadTicks()) * 50L * GunItem.reloadFactor(player)));
				// An empty gun (or an open bolt) has to be made ready once the new magazine is in.
				if (motion.work() != null && (lastRounds <= 0 || (lastBoltOpen && !boltOpen))) {
					queued = new Running(motion.work(), motion, item, 0, duration(motion.work()), false);
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
		return current(stack.getItem()) != null || ShotgunAnimation.busy(stack) || M249Animation.busy(stack);
	}

	/** Starts inspecting the held gun, unless it's busy with another animation. */
	public static void inspect(LocalPlayer player) {
		if (player == null || GunScope.zoom(player) > 0.0f) {
			return;
		}
		Item item = player.getMainHandItem().getItem();
		// The shotguns and the M249 have handling of their own.
		if (item instanceof ShotgunItem) {
			ShotgunAnimation.inspect(player);
			return;
		} else if (item instanceof MachineGunItem) {
			M249Animation.inspect(player);
			return;
		}
		Motion motion = motion(item);
		if (motion != null && current(item) == null && queued == null) {
			start(Kind.INSPECT, motion, item, INSPECT_MILLIS);
		}
	}

	private static long duration(Kind kind) {
		return kind == Kind.BOLT ? 900 : 1000;
	}

	private static void start(Kind kind, Motion motion, Item item, long durationMillis) {
		start(kind, motion, item, durationMillis, false);
	}

	private static void start(Kind kind, Motion motion, Item item, long durationMillis, boolean chained) {
		if (kind == null || motion == null) {
			running = null;
			return;
		}
		running = new Running(kind, motion, item, Util.getMillis(), durationMillis > 0 ? durationMillis : duration(kind), chained);
		lastTickProgress = 0.0f;
	}

	/** The animation running on this item, moving on to the queued one when it finishes. */
	private static Running current(Item item) {
		if (running != null && running.progress() >= 1.0f) {
			running = queued == null ? null
					: new Running(queued.kind(), queued.motion(), queued.item(), Util.getMillis(), queued.durationMillis(), false);
			queued = null;
			lastTickProgress = 0.0f;
		}
		return running != null && running.item() == item ? running : null;
	}

	private static Cue[] cues(Running run) {
		return switch (run.kind()) {
			case RACK -> new Cue[] {new Cue(0.22f, rackBack(run), 1.0f), new Cue(0.58f, rackForward(run), 1.0f)};
			case BOLT -> new Cue[] {new Cue(0.14f, ModSounds.BOLT_UP, 1.0f), new Cue(0.22f, ModSounds.BOLT_BACK, 1.0f),
					new Cue(0.44f, ModSounds.BOLT_FORWARD, 1.0f), new Cue(0.6f, ModSounds.BOLT_DOWN, 1.0f)};
			case RELOAD -> pistolMagazine(run.item())
					? new Cue[] {new Cue(0.15f, ModSounds.PISTOL_MAG_OUT, 1.0f), new Cue(0.66f, ModSounds.PISTOL_MAG_IN, 1.0f)}
					: new Cue[] {new Cue(0.15f, ModSounds.MAG_OUT, 1.0f), new Cue(0.66f, ModSounds.MAG_IN, 1.0f)};
			// Only the revolver loads loose rounds.
			case LOAD_ROUND -> new Cue[] {new Cue(0.38f, ModSounds.REVOLVER_LOAD, 1.0f)};
			case SPEEDLOAD -> new Cue[] {new Cue(0.1f, ModSounds.REVOLVER_CYLINDER_OPEN, 1.0f), new Cue(0.45f, ModSounds.REVOLVER_LOAD, 0.95f),
					new Cue(0.75f, ModSounds.REVOLVER_CYLINDER_CLOSE, 1.0f)};
			// The server plays the fill itself; these are the hose coming off, the canister going on and the valve.
			case REFUEL -> new Cue[] {new Cue(0.24f, ModSounds.MAG_OUT, 0.6f), new Cue(0.48f, ModSounds.MAG_IN, 0.55f),
					new Cue(0.76f, ModSounds.RACK_BACK, 1.7f)};
			case INSPECT -> run.motion().work() == Kind.BOLT
					? new Cue[] {new Cue(0.6f, ModSounds.BOLT_UP, 1.1f), new Cue(0.66f, ModSounds.BOLT_BACK, 1.2f),
							new Cue(0.74f, ModSounds.BOLT_FORWARD, 1.2f), new Cue(0.78f, ModSounds.BOLT_DOWN, 1.1f)}
					: run.motion().work() == Kind.RACK
							? new Cue[] {new Cue(0.6f, rackBack(run), 1.2f), new Cue(0.75f, rackForward(run), 1.25f)}
							// The revolver's cylinder gets a spin and the hammer is thumbed back; the flamethrower's valve clicks.
							: run.item() == ModItems.REVOLVER_357
									? new Cue[] {new Cue(0.6f, ModSounds.REVOLVER_SPIN, 1.0f), new Cue(0.72f, ModSounds.REVOLVER_HAMMER, 1.0f)}
									: new Cue[] {new Cue(0.6f, ModSounds.MAG_IN, 1.7f), new Cue(0.66f, ModSounds.MAG_IN, 1.9f)};
		};
	}

	/** Pistols rack a slide; the rifles and SMGs pull a charging handle. */
	private static boolean pistolSlide(Item item) {
		return item == ModItems.GLOCK || item == ModItems.M1911;
	}

	/** Magazines that go up the grip. */
	private static boolean pistolMagazine(Item item) {
		return pistolSlide(item) || item == ModItems.MAC10;
	}

	private static SoundEvent rackBack(Running run) {
		return pistolSlide(run.item()) ? ModSounds.PISTOL_SLIDE_BACK : ModSounds.RACK_BACK;
	}

	private static SoundEvent rackForward(Running run) {
		return pistolSlide(run.item()) ? ModSounds.PISTOL_SLIDE_FORWARD : ModSounds.RACK_FORWARD;
	}

	// ----------------------------------------------------------------------------------------------- timeline

	private static float smooth(float t) {
		return GunMotion.smooth(t);
	}

	/** How far through the stretch from {@code a} to {@code b} progress {@code p} is, 0 to 1. */
	private static float span(float p, float a, float b) {
		return GunMotion.span(p, a, b);
	}

	/** Slide or bolt position, continuously from 0 (at rest) to FRAMES (fully back). */
	private static float partPosition(Kind kind, float p) {
		return switch (kind) {
			case RACK -> RACK_PART.sample(p);
			case BOLT -> BOLT_PART.sample(p);
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

	/** How far the gun is turned toward the working hand. */
	private static float engagement(Running run, float p) {
		return (run.chained() ? CHAINED_ROUND : ENGAGEMENT.get(run.kind())).sample(p);
	}

	/** Springy shake after each part hits its stop. */
	private static float jolt(Running run, float p) {
		float[][] hits = switch (run.kind()) {
			case RACK -> new float[][] {{0.5f, 0.4f}, {0.6f, 1.0f}};
			case BOLT -> new float[][] {{0.4f, 0.4f}, {0.58f, 0.8f}, {0.64f, 0.5f}};
			case RELOAD -> new float[][] {{0.16f, 0.4f}, {0.67f, 1.1f}};
			case LOAD_ROUND -> new float[][] {{0.4f, 0.3f}};
			case SPEEDLOAD -> new float[][] {{0.46f, 0.5f}, {0.75f, 0.8f}};
			case REFUEL -> new float[][] {{0.48f, 0.3f}, {0.76f, 0.35f}};
			case INSPECT -> new float[][] {{0.76f, 0.45f}};
		};
		float total = 0.0f;
		for (float[] hit : hits) {
			total += GunMotion.jolt(p, hit[0], run.durationMillis(), hit[1]);
		}
		return total;
	}

	// ------------------------------------------------------------------------------------------------- output

	/** The model frame for this stack, for the item model property (0 at rest). */
	public static float frame(ItemStack stack, ItemOwner owner, LocalPlayer player) {
		if (stack.getItem() instanceof RocketLauncherItem) {
			// Whoever holds it: a launcher's handling is on its stack.
			return LauncherAnimation.frame(stack);
		}
		if (stack.getItem() instanceof ShotgunItem) {
			// The pump's on the stack too; only the holder sees its flick on drawing and press check.
			return ShotgunAnimation.frame(stack, player != null && owner == player && stack == player.getMainHandItem());
		}
		if (player == null || owner != player || stack != player.getMainHandItem()) {
			// Someone else's gun: its magazine comes out and goes back in when they're seen reloading it.
			return owner instanceof LivingEntity holder && holder.getMainHandItem() == stack ? othersFrame(stack, holder) : 0.0f;
		}
		if (stack.getItem() instanceof MachineGunItem) {
			return M249Animation.frame(stack);
		}
		Running run = current(stack.getItem());
		if (run == null) {
			return 0.0f;
		}
		float p = run.progress();
		if (run.kind() == Kind.RELOAD) {
			return magFrame(MAG.sample(p));
		} else if (run.kind() == Kind.INSPECT) {
			return Math.round(pressCheckPosition(run.motion(), p));
		}
		return Math.round(partPosition(run.kind(), p));
	}

	private static float magFrame(float m) {
		return m < 0.5f ? 0.0f : FRAMES + Mth.clamp(Math.round(m), 1, MAG_FRAMES);
	}

	/** The model frame for a gun another player (or mob) is holding, from the reload GunReloads saw them start. */
	private static float othersFrame(ItemStack stack, LivingEntity holder) {
		GunReloads.Reload reload = GunReloads.of(holder);
		if (reload == null || reload.item() != stack.getItem() || reload.kind() != GunReloads.Kind.MAGAZINE) {
			return 0.0f;
		}
		if (stack.getItem() instanceof MachineGunItem) {
			return M249Animation.reloadFrame(reload.progress());
		}
		return motion(stack.getItem()) == null ? 0.0f : magFrame(MAG.sample(reload.progress()));
	}

	/**
	 * Turns, lifts, tugs and shakes the gun for the running animation, pivoting on the hand that keeps
	 * hold of it. Applied to the item's pose, so the gun and both hands move together.
	 */
	public static void applyGunPose(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		ShotgunAnimation.applyGunPose(poseStack, stack, anchors, mirrored);
		M249Animation.applyGunPose(poseStack, stack, anchors, mirrored);
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
		float engaged = engagement(run, p);
		float pull = working ? partPosition(run.kind(), p) / FRAMES : 0.0f;
		float tug = run.kind() == Kind.RELOAD ? RELOAD_TUG.sample(p) : 0.0f;
		float shake = jolt(run, p);
		float side = mirrored ? -1.0f : 1.0f;
		Vector3f pivot = working && motion.mainHandWorks() ? anchors.support() : anchors.grip();

		poseStack.translate(0.0f, pose.lift() * engaged + 0.012f * tug, (working ? motion.drag() * pull : 0.0f) - shake * 0.012f);
		poseStack.rotateAround(new Quaternionf().rotationXYZ(
				(float) Math.toRadians(pose.pitch() * engaged + (working ? motion.pullPitch() * pull : 0.0f) + 2.5f * tug + shake * 2.5f),
				(float) Math.toRadians(pose.yaw() * engaged * side),
				(float) Math.toRadians((pose.roll() * engaged - 3.0f * tug) * side + shake * 1.5f)),
				pivot.x * side, pivot.y, pivot.z);
	}

	/**
	 * Inspecting: the gun comes in toward the middle of the screen turned muzzle-left, showing its left
	 * side, then rolls over to bring the ejection side up to the camera for the press check, and goes back.
	 */
	private static void applyInspectPose(PoseStack poseStack, Running run, GunHands.Anchors anchors, boolean mirrored) {
		float p = run.progress();
		float raised = engagement(run, p);
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

	/**
	 * A hand being pulled off its hold toward {@code target} (item space) by {@code blend} (0 to 1), twisted about its
	 * arm by {@code twist} degrees (see {@link GunHands#poseArm}).
	 */
	public record HandOverride(boolean mainHand, Vector3f target, float blend, float twist) {
		public HandOverride(boolean mainHand, Vector3f target, float blend) {
			this(mainHand, target, blend, 0.0f);
		}
	}

	/** Where a hand should be pulled to for the running animation on this stack, if any. */
	public static HandOverride hand(ItemStack stack) {
		if (stack.getItem() instanceof ShotgunItem) {
			return ShotgunAnimation.hand(stack);
		} else if (stack.getItem() instanceof MachineGunItem) {
			return M249Animation.hand(stack);
		}
		Running run = current(stack.getItem());
		if (run == null) {
			return null;
		}
		float p = run.progress();
		Motion motion = run.motion();
		if (run.kind() == Kind.INSPECT) {
			if (motion.work() == null) {
				return null;
			}
			// The press check: the working hand goes to the slide or bolt and draws it back a little.
			float blend = smooth(span(p, 0.54f, 0.6f)) * (1.0f - smooth(span(p, 0.8f, 0.87f)));
			return new HandOverride(motion.mainHandWorks(), motion.workPoint(pressCheckPosition(motion, p)), blend,
					motion.workTwist() * blend);
		}
		Path path = path(run.item(), motion, run.kind(), run.chained());
		if (path == null) {
			return null;
		}
		Keyframes.Sample at = path.sample(p);
		boolean main = (run.kind() == Kind.RACK || run.kind() == Kind.BOLT) && motion.mainHandWorks();
		return new HandOverride(main, at.point(), 1.0f, at.twist());
	}
}
