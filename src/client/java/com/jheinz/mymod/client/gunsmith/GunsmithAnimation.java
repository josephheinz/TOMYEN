package com.jheinz.mymod.client.gunsmith;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.ModSounds;
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
import com.jheinz.mymod.gunsmith.Gunsmith;
import com.jheinz.mymod.gunsmith.GunsmithGunItem;
import com.jheinz.mymod.shotgun.ShotgunAction;
import com.jheinz.mymod.shotgun.ShotgunItem;
import com.jheinz.mymod.shotgun.Shotguns;

/**
 * The handling of the Gunsmith guns loaded a round at a time, played from the {@link ShotgunAction} on the stack (as
 * ShotgunAnimation plays the shotguns'), with the hands doing the work:
 * <ul>
 * <li><b>Break actions</b> (Sawed-off, Flare Gun): loading breaks the gun open, the barrels dropping on their hinge and the
 * spent hulls kicked out; the off hand fetches each round and drops it into its chamber (the Sawed-off's two in turn); when
 * loading stops the off hand comes under the barrels and swings them up until the action snaps shut.</li>
 * <li><b>Lever</b> (Lever-Action): after every shot the shooting hand, its fingers through the loop, throws the lever down
 * and brings it home, the bolt sliding back and the case flying out of the port; loading rolls the gun over to bring the
 * gate up, and the off hand thumbs each round in through it.</li>
 * <li><b>Revolver</b> (Snub Revolver): loading swings the cylinder out to the left with the gun tipped up; the off hand puts
 * a round into each chamber in turn and, once loading stops, flicks the cylinder shut.</li>
 * <li><b>Spear gun</b> (Harpoon Gun): with the gun tipped up, the off hand slides a spear down the muzzle, then reaches back
 * to the cocking knob and draws it to charge the gun.</li>
 * <li><b>Slingshot</b>: the off hand always holds the pouch: it fetches a stone, lays it in the pouch and draws the bands
 * back to the eye, and the pouch flies forward when it's loosed.</li>
 * </ul>
 * Moving parts show through the mymod:gun_animation frames (1-6), worked out from the stack alone, so others see them too.
 * Every hand movement is a keyframed {@link Path} on the action's timeline, in the gun's model pixels.
 */
public final class GunsmithAnimation {
	private enum Kind {
		BREAK, LEVER, REVOLVER, SPEAR, SLINGSHOT
	}

	// Per round of loading, as fractions of its time: down for a round, back to the port, pushed in.
	private static final float FETCHED = 0.3f;
	private static final float AT_PORT = 0.55f;
	private static final float PUSHED = 0.72f;
	/** Where the off hand goes for a round, relative to where it goes in (pixels: below and back, off the screen). */
	private static final Vector3f FETCH = new Vector3f(-2.0f, -12.0f, 5.0f);

	/** The lever through a stroke, 0 home to 1 thrown: down quickly, a beat, home. */
	private static final Track LEVER = Keyframes.track(0.0f).key(0.32f, 1.0f, Ease.OUT).hold(0.42f).key(0.72f, 0.0f, Ease.IN).build();
	/** A break action closing, 1 open to 0 shut: the hand lifts the barrels (to 0.42) and they snap home. */
	private static final Track CLOSE = Keyframes.track(1.0f).hold(0.28f).key(0.42f, 0.25f).key(0.47f, 0.0f, Ease.IN).build();
	/** A cylinder flicked shut. */
	private static final Track SWING_IN = Keyframes.track(1.0f).hold(0.3f).key(0.46f, 0.0f, Ease.IN).build();
	/** The spear gun's cocking knob: drawn back, held, let run home. */
	private static final Track COCK = Keyframes.track(0.0f).hold(0.25f).key(0.48f, 1.0f).hold(0.56f).key(0.64f, 0.0f, Ease.IN).build();
	/** The slingshot's pouch after a shot: it flies forward off the fingers. */
	private static final Track LOOSE = Keyframes.track(1.0f).key(0.25f, 0.0f, Ease.OUT).build();
	private static final float LEVER_EJECT = 0.3f;

	private static final long OPEN_MILLIS = 160;
	private static final long INSPECT_MILLIS = 3000;
	private static final long FLOURISH_MILLIS = 700;
	private static final float EJECT_TICKS = 8.0f;

	private static final ItemStackRenderState ROUND_STATE = new ItemStackRenderState();

	/** A round the holder's hands threw out (hulls kicked out of a broken-open breech), drawn flying for a moment. */
	private record Flying(Item item, Holder<Item> round, Vector3f from, Vector3f velocity, long start) {
	}

	private static final List<Flying> FLYING = new ArrayList<>();

	private static int lastSlot = -1;
	private static Item lastItem;
	private static long inspectStart = Long.MIN_VALUE;
	private static long flourishStart = Long.MIN_VALUE;
	private static float opened;
	private static long openedMillis;
	private static long cueStart = Long.MIN_VALUE;
	private static float cueProgress;
	private static ShotgunAction.Kind lastKind;
	private static boolean chainStarted;

	private GunsmithAnimation() {
	}

	private static Kind kind(Item item) {
		if (item == Gunsmith.SAWED_OFF || item == Gunsmith.FLARE_GUN) {
			return Kind.BREAK;
		} else if (item == Gunsmith.LEVER_ACTION) {
			return Kind.LEVER;
		} else if (item == Gunsmith.SNUB_REVOLVER) {
			return Kind.REVOLVER;
		} else if (item == Gunsmith.HARPOON_GUN) {
			return Kind.SPEAR;
		} else if (item == Gunsmith.SLINGSHOT) {
			return Kind.SLINGSHOT;
		}
		return null;
	}

	/** Whether this is a Gunsmith gun whose handling this class plays (instead of ShotgunAnimation). */
	public static boolean owns(Item item) {
		return item instanceof GunsmithGunItem && kind(item) != null;
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

	private static float progress(ShotgunAction action) {
		return action == null ? 1.0f : action.progress(now());
	}

	private static int rounds(ItemStack stack) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		return loaded == null ? 0 : loaded.rounds();
	}

	private static Vector3f onGun(Item item, Vector3f pixels) {
		return GunMotion.onGun(item, pixels);
	}

	private static Vector3f rotX(Vector3f p, Vector3f pivot, float degrees) {
		return new Vector3f(p).sub(pivot).rotateX((float) Math.toRadians(degrees)).add(pivot);
	}

	private static Vector3f rotZ(Vector3f p, Vector3f pivot, float degrees) {
		return new Vector3f(p).sub(pivot).rotateZ((float) Math.toRadians(degrees)).add(pivot);
	}

	// ------------------------------------------------------------------------------------------------ geometry

	private static Vector3f hinge(Item item) {
		return item == Gunsmith.SAWED_OFF ? GunsmithRigs.SAWED_OFF_HINGE : GunsmithRigs.FLARE_GUN_HINGE;
	}

	private static float openAngle(Item item) {
		return item == Gunsmith.SAWED_OFF ? GunsmithRigs.SAWED_OFF_OPEN_ANGLE.x : GunsmithRigs.FLARE_GUN_OPEN_ANGLE.x;
	}

	/** A break action's breech (the chamber a round goes into: the Sawed-off's right barrel first), broken open by {@code open}. */
	private static Vector3f chamber(Item item, int loaded, float open) {
		Vector3f breech = item == Gunsmith.SAWED_OFF
				? new Vector3f(loaded % 2 == 0 ? GunsmithRigs.SAWED_OFF_BARRELS.y : GunsmithRigs.SAWED_OFF_BARRELS.x,
						GunsmithRigs.SAWED_OFF_BREECH_Z.y, GunsmithRigs.SAWED_OFF_BREECH_Z.z)
				: new Vector3f(GunsmithRigs.FLARE_GUN_BREECH);
		return rotX(breech, hinge(item), openAngle(item) * open);
	}

	/** The way into a broken-open chamber (down the bore, which has dropped with the barrels). */
	private static Vector3f boreAxis(Item item) {
		return new Vector3f(0.0f, 0.0f, -1.0f).rotateX((float) Math.toRadians(openAngle(item)));
	}

	/** The revolver's chamber that round {@code k} goes into, on the rear face of the swung-out cylinder. */
	private static Vector3f revolverChamber(int k) {
		Vector3f centre = new Vector3f(GunsmithRigs.SNUB_REVOLVER_CYLINDER_OPEN.x, GunsmithRigs.SNUB_REVOLVER_CYLINDER_OPEN.y,
				GunsmithRigs.SNUB_REVOLVER_CYLINDER_REAR_Z.z + 0.1f);
		double angle = Math.toRadians(90.0 + GunsmithRigs.SNUB_REVOLVER_SWING.x + 72.0 * k);
		return centre.add((float) Math.cos(angle) * 0.85f, (float) Math.sin(angle) * 0.85f, 0.0f);
	}

	private static Vector3f leverLoop(float lever) {
		return rotX(GunsmithRigs.LEVER_ACTION_LOOP, GunsmithRigs.LEVER_ACTION_PIVOT, GunsmithRigs.LEVER_ACTION_LEVER_ANGLE.x * lever);
	}

	private static Vector3f pouch(float draw) {
		return new Vector3f(GunsmithRigs.SLINGSHOT_POUCH).lerp(GunsmithRigs.SLINGSHOT_POUCH_DRAWN, draw);
	}

	// ---------------------------------------------------------------------------------------------- the part

	/**
	 * How far the moving part is worked now, 0 to 1, for whoever holds it: a break action's opening, the lever's throw, the
	 * cylinder's swing, the cocking knob's draw, the slingshot's draw.
	 */
	private static float part(ItemStack stack, boolean holder) {
		Item item = stack.getItem();
		Kind kind = kind(item);
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		float p = progress(action);
		boolean live = action != null && p < 1.0f;
		switch (kind) {
			case BREAK, REVOLVER -> {
				if (action != null && action.kind() == ShotgunAction.Kind.LOAD) {
					return holder ? opened : 1.0f;
				}
				if (live && action.kind() == ShotgunAction.Kind.PUMP && action.chambering()) {
					return (kind == Kind.BREAK ? CLOSE : SWING_IN).sample(p);
				}
				return 0.0f;
			}
			case LEVER -> {
				if (live && action.kind() == ShotgunAction.Kind.PUMP) {
					return LEVER.sample(p);
				}
				float f = holder ? since(flourishStart, FLOURISH_MILLIS) : 1.0f;
				return f < 1.0f ? 0.55f * LEVER.sample(f) : 0.0f;
			}
			case SPEAR -> {
				return live && action.kind() == ShotgunAction.Kind.PUMP ? COCK.sample(p) : 0.0f;
			}
			case SLINGSHOT -> {
				if (action != null && action.kind() == ShotgunAction.Kind.LOAD) {
					// Drawn as the stone goes in.
					return GunMotion.smooth(GunMotion.span(p, PUSHED, 1.0f));
				}
				if (live && action.kind() == ShotgunAction.Kind.CYCLE) {
					return LOOSE.sample(p);
				}
				return rounds(stack) > 0 ? 1.0f : 0.0f;
			}
		}
		return 0.0f;
	}

	/** The mymod:gun_animation frame: the part's position, 0 (home) to 6. */
	public static float frame(ItemStack stack, boolean holder) {
		return Math.round(part(stack, holder) * 6.0f);
	}

	// ------------------------------------------------------------------------------------------------ control

	/** Called every client tick: drawing, opening for a load (and the hulls kicked out), putting an inspection away, cues. */
	public static void tick(LocalPlayer player) {
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		Item item = held.getItem();
		int slot = player.getInventory().getSelectedSlot();
		if (owns(item) && (slot != lastSlot || item != lastItem)) {
			inspectStart = Long.MIN_VALUE;
			flourishStart = kind(item) == Kind.LEVER ? Util.getMillis() : Long.MIN_VALUE;
			opened = 0.0f;
		}
		lastSlot = slot;
		lastItem = item;
		if (!owns(item)) {
			lastKind = null;
			return;
		}
		if (since(inspectStart, INSPECT_MILLIS) < 1.0f && (GunRecoil.kick() > 0.01f || GunScope.zoom(player) > 0.0f
				|| held.has(Shotguns.SHOTGUN_ACTION))) {
			inspectStart = Long.MIN_VALUE;
		}
		ShotgunAction action = held.get(Shotguns.SHOTGUN_ACTION);
		ShotgunAction.Kind now = action == null ? null : action.kind();
		chainStarted = now == ShotgunAction.Kind.LOAD && lastKind != ShotgunAction.Kind.LOAD;
		if (chainStarted && kind(item) == Kind.BREAK) {
			kickOutHulls(held, action);
		}
		lastKind = now;
		tickCues(player, held, action);
	}

	/** Breaking a gun open throws out a hull for every chamber that was fired. */
	private static void kickOutHulls(ItemStack held, ShotgunAction action) {
		Item item = held.getItem();
		int fired = ((ShotgunItem) item).capacity(held) - rounds(held);
		for (int k = 0; k < fired; k++) {
			Vector3f from = chamber(item, k == 0 ? 0 : 1, 1.0f);
			Vector3f velocity = new Vector3f(0.25f * (k == 0 ? 1 : -1), 1.1f, 1.0f);
			FLYING.add(new Flying(item, action.shell(), from, velocity, Util.getMillis() + 120L * k));
		}
	}

	public static void inspect(LocalPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (GunScope.zoom(player) <= 0.0f && !busy(held)) {
			inspectStart = Util.getMillis();
		}
	}

	/** Whether this gun is busy: loading, working its action, being drawn or inspected. */
	public static boolean busy(ItemStack stack) {
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		return action != null && (action.kind() == ShotgunAction.Kind.LOAD || now() < action.end())
				|| since(flourishStart, FLOURISH_MILLIS) < 1.0f || since(inspectStart, INSPECT_MILLIS) < 1.0f;
	}

	private record Cue(float at, SoundEvent sound, float volume, float pitch) {
	}

	private static void tickCues(LocalPlayer player, ItemStack held, ShotgunAction action) {
		if (action == null) {
			return;
		}
		if (action.start() != cueStart) {
			cueStart = action.start();
			cueProgress = -1.0f;
		}
		float p = action.progress(player.level().getGameTime());
		Kind kind = kind(held.getItem());
		List<Cue> cues = new ArrayList<>();
		switch (action.kind()) {
			case LOAD -> {
				if (chainStarted) {
					if (kind == Kind.BREAK) {
						cues.add(new Cue(0.0f, Gunsmith.BREAK_OPEN, 0.9f, held.getItem() == Gunsmith.FLARE_GUN ? 1.25f : 1.0f));
					} else if (kind == Kind.REVOLVER) {
						cues.add(new Cue(0.0f, ModSounds.REVOLVER_CYLINDER_OPEN, 0.8f, 1.1f));
					}
				}
				cues.add(switch (kind) {
					case LEVER -> new Cue(PUSHED - 0.02f, Gunsmith.LEVER_LOAD, 0.8f, 1.0f);
					case REVOLVER -> new Cue(PUSHED - 0.02f, ModSounds.REVOLVER_LOAD, 0.8f, 1.1f);
					case SPEAR -> new Cue(PUSHED - 0.02f, Shotguns.SHELL_INSERT, 0.7f, 0.6f);
					case SLINGSHOT -> new Cue(0.82f, Gunsmith.SLINGSHOT_DRAW, 0.6f, 1.0f);
					default -> new Cue(PUSHED - 0.02f, Shotguns.SHELL_INSERT, 0.7f, held.getItem() == Gunsmith.FLARE_GUN ? 1.2f : 1.0f);
				});
			}
			case PUMP -> {
				switch (kind) {
					case LEVER -> {
						cues.add(new Cue(0.04f, Gunsmith.LEVER_DOWN, 0.9f, 1.0f));
						cues.add(new Cue(0.5f, Gunsmith.LEVER_UP, 0.9f, 1.0f));
					}
					case BREAK -> cues.add(new Cue(0.44f, Gunsmith.BREAK_CLOSE, 0.9f, held.getItem() == Gunsmith.FLARE_GUN ? 1.25f : 1.0f));
					case REVOLVER -> cues.add(new Cue(0.44f, ModSounds.REVOLVER_CYLINDER_CLOSE, 0.9f, 1.1f));
					case SPEAR -> {
						cues.add(new Cue(0.27f, ModSounds.BOLT_BACK, 0.8f, 0.7f));
						cues.add(new Cue(0.6f, ModSounds.BOLT_FORWARD, 0.8f, 0.65f));
					}
					default -> {
					}
				}
			}
			default -> {
			}
		}
		for (Cue cue : cues) {
			if (cue.at() > cueProgress && cue.at() <= p || cue.at() == 0.0f && cueProgress < 0.0f) {
				player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
						cue.volume(), cue.pitch() * (0.95f + player.getRandom().nextFloat() * 0.1f), false);
			}
		}
		cueProgress = p;
	}

	// ----------------------------------------------------------------------------------------------- the gun

	/** Eases the holder's break action or cylinder open as loading starts (and shut, for the hands, when it stops). */
	private static float opened(ItemStack stack) {
		long now = Util.getMillis();
		float step = Math.min(now - openedMillis, 100L) / (float) OPEN_MILLIS;
		openedMillis = now;
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		boolean loading = action != null && action.kind() == ShotgunAction.Kind.LOAD;
		opened = Mth.clamp(opened + (loading ? step : -step), 0.0f, 1.0f);
		return GunMotion.smooth(opened);
	}

	/** How the gun turns while it's loaded, for each kind: pitch, yaw, roll (top to the left) and a lift. */
	private static float[] loadPose(Kind kind) {
		return switch (kind) {
			// Breech toward the eye, the muzzle dipped.
			case BREAK -> new float[] {-16.0f, 16.0f, -10.0f, 0.03f};
			// Over on its left side to bring the gate up to the off hand.
			case LEVER -> new float[] {6.0f, 14.0f, 42.0f, 0.03f};
			// Muzzle up, the swung-out cylinder toward the eye.
			case REVOLVER -> new float[] {38.0f, 12.0f, -18.0f, 0.05f};
			// Muzzle up for the spear to go down it.
			case SPEAR -> new float[] {38.0f, 18.0f, -8.0f, 0.04f};
			case SLINGSHOT -> new float[] {0.0f, 0.0f, 0.0f, 0.0f};
		};
	}

	/** Turns the held gun for what it's doing: over for loading, tugged by its action, jolted as parts hit home, inspected. */
	public static void applyGunPose(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		if (anchors == null) {
			return;
		}
		float i = since(inspectStart, INSPECT_MILLIS);
		if (i < 1.0f) {
			GunMotion.inspect(poseStack, i, (Util.getMillis() - inspectStart) / 1000.0f, anchors.grip(), mirrored);
			return;
		}
		Kind kind = kind(stack.getItem());
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		float open = opened(stack);
		float p = progress(action);
		float shake = 0.0f;
		float loading = kind == Kind.SLINGSHOT ? 0.0f : open;
		float pitch = 0.0f;
		float roll = 0.0f;
		if (action != null && p < 1.0f) {
			long millis = action.ticks() * 50L;
			switch (action.kind()) {
				case LOAD -> shake = GunMotion.jolt(p, PUSHED, millis, 0.3f);
				case PUMP -> {
					switch (kind) {
						case LEVER -> {
							float lever = LEVER.sample(p);
							pitch = -3.0f * lever;
							roll = -4.0f * lever;
							shake = GunMotion.jolt(p, 0.72f, millis, 0.7f) + GunMotion.jolt(p, 0.32f, millis, 0.3f);
						}
						case BREAK, REVOLVER -> {
							// Still turned for loading until it's shut.
							loading = Math.max(loading, 1.0f - GunMotion.smooth(GunMotion.span(p, 0.5f, 0.9f)));
							shake = GunMotion.jolt(p, kind == Kind.BREAK ? 0.47f : 0.46f, millis, 1.0f);
						}
						case SPEAR -> {
							loading = Math.max(loading, 0.6f * (1.0f - GunMotion.smooth(GunMotion.span(p, 0.65f, 0.95f))));
							roll = 10.0f * COCK.sample(p);
							shake = GunMotion.jolt(p, 0.64f, millis, 0.8f);
						}
						default -> {
						}
					}
				}
				case CYCLE -> {
					if (kind == Kind.SLINGSHOT) {
						shake = GunMotion.jolt(p, 0.2f, millis, 0.5f);
					}
				}
				default -> {
				}
			}
		}
		float[] pose = loadPose(kind);
		GunMotion.turn(poseStack, anchors.grip(), pose[0] * loading + pitch, pose[1] * loading, pose[2] * loading + roll,
				pose[3] * loading, 1.0f, shake, mirrored);
	}

	// ----------------------------------------------------------------------------------------------- the hands

	/** One round going in, in model pixels: from {@code port}, down for a round, back up to {@code ready} and in to {@code home}. */
	private static Path roundPath(Vector3f port, Vector3f ready, Vector3f home, float readyTwist, float pushTwist) {
		return Keyframes.path(port)
				.key(FETCHED, new Vector3f(port).add(FETCH), -30.0f)
				.key(AT_PORT, ready, readyTwist)
				.key(0.62f, new Vector3f(ready).lerp(home, 0.4f), pushTwist)
				.key(PUSHED, home, pushTwist, Ease.IN)
				.key(1.0f, port, 0.0f)
				.build();
	}

	/** Where the off hand goes while a round goes in. */
	private static Keyframes.Sample loadingHand(Item item, Kind kind, int loaded, float p) {
		return switch (kind) {
			case BREAK -> {
				Vector3f port = chamber(item, loaded, 1.0f);
				Vector3f axis = boreAxis(item);
				Vector3f ready = new Vector3f(port).sub(new Vector3f(axis).mul(2.4f)).add(0.0f, 0.8f, 0.0f);
				Vector3f home = new Vector3f(port).add(new Vector3f(axis).mul(0.3f)).add(0.0f, 0.5f, 0.0f);
				yield roundPath(new Vector3f(port).add(0.0f, 1.0f, 1.2f), ready, home, 120.0f, 135.0f).sample(p);
			}
			case LEVER -> {
				Vector3f port = new Vector3f(GunsmithRigs.LEVER_ACTION_PORT);
				yield roundPath(new Vector3f(port).add(0.6f, -0.8f, 1.6f), new Vector3f(port).add(0.9f, -0.5f, 2.2f),
						new Vector3f(port).add(0.25f, 0.0f, -0.4f), 40.0f, 30.0f).sample(p);
			}
			case REVOLVER -> {
				Vector3f port = revolverChamber(loaded);
				yield roundPath(new Vector3f(port).add(-0.8f, 0.6f, 1.8f), new Vector3f(port).add(0.0f, 0.2f, 2.0f),
						new Vector3f(port).add(0.0f, 0.0f, 0.3f), 20.0f, 20.0f).sample(p);
			}
			case SPEAR -> {
				Vector3f port = new Vector3f(GunsmithRigs.HARPOON_GUN_MUZZLE);
				yield roundPath(new Vector3f(port).add(0.0f, 1.2f, -2.0f), new Vector3f(port).add(0.0f, 0.9f, -6.5f),
						new Vector3f(port).add(0.0f, 0.4f, -2.2f), 60.0f, 90.0f).sample(p);
			}
			case SLINGSHOT -> {
				// Fetch a stone, lay it in the pouch at rest, then draw the pouch back to the eye.
				Vector3f rest = pouch(0.0f);
				Keyframes.Sample s = Keyframes.path(rest)
						.key(FETCHED, new Vector3f(rest).add(FETCH), -30.0f)
						.key(AT_PORT, new Vector3f(rest).add(0.0f, 1.4f, 1.2f), 40.0f)
						.key(PUSHED, rest, 20.0f, Ease.IN)
						.key(1.0f, pouch(1.0f), 0.0f)
						.build().sample(p);
				yield s;
			}
		};
	}

	/** Where a hand is pulled to for what the gun is doing: loading, working the action, holding the pouch. */
	public static GunAnimation.HandOverride hand(ItemStack stack) {
		Item item = stack.getItem();
		Kind kind = kind(item);
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		float p = progress(action);
		if (action != null && action.kind() == ShotgunAction.Kind.LOAD) {
			Keyframes.Sample at = loadingHand(item, kind, rounds(stack), p);
			float blend = kind == Kind.SLINGSHOT ? 1.0f : opened;
			return new GunAnimation.HandOverride(false, onGun(item, at.point()), blend, at.twist() * blend);
		}
		boolean live = action != null && p < 1.0f;
		switch (kind) {
			case LEVER -> {
				float lever = live && action.kind() == ShotgunAction.Kind.PUMP ? LEVER.sample(p) : part(stack, true);
				float blend = live && action.kind() == ShotgunAction.Kind.PUMP
						? GunMotion.smooth(GunMotion.span(p, 0.0f, 0.06f)) * (1.0f - GunMotion.smooth(GunMotion.span(p, 0.82f, 1.0f)))
						: lever > 0.0f ? 1.0f : 0.0f;
				if (blend <= 0.0f) {
					return null;
				}
				return new GunAnimation.HandOverride(true, onGun(item, leverLoop(lever)), blend, -10.0f * lever);
			}
			case BREAK -> {
				if (live && action.kind() == ShotgunAction.Kind.PUMP && action.chambering()) {
					// Under the dropped barrels, lifting them until they snap shut, then back to the hold.
					float open = CLOSE.sample(p);
					Vector3f support = item == Gunsmith.SAWED_OFF ? GunsmithRigs.SAWED_OFF_SUPPORT_OPEN : GunsmithRigs.FLARE_GUN_BARREL_TOP;
					Vector3f on = rotX(rotX(support, hinge(item), -openAngle(item)), hinge(item), openAngle(item) * open);
					Path path = Keyframes.path(new Vector3f(on).add(0.0f, -1.6f, 1.0f), 30.0f)
							.key(0.22f, new Vector3f(on).add(0.0f, -0.5f, 0.0f), 20.0f)
							.key(0.28f, on, 0.0f)
							.build();
					Vector3f point = p < 0.28f ? path.sample(p).point() : on;
					float leave = item == Gunsmith.FLARE_GUN ? 1.0f - GunMotion.smooth(GunMotion.span(p, 0.55f, 0.85f)) : 1.0f;
					if (item == Gunsmith.SAWED_OFF && p > 0.5f) {
						return null;
					}
					return new GunAnimation.HandOverride(false, onGun(item, point), leave, 0.0f);
				}
				return null;
			}
			case REVOLVER -> {
				if (live && action.kind() == ShotgunAction.Kind.PUMP && action.chambering()) {
					// Beside the cylinder, then a flick to the right that swings it home, and back to cup the grip.
					Vector3f out = new Vector3f(GunsmithRigs.SNUB_REVOLVER_CYLINDER_OPEN).add(-1.6f, 0.0f, 0.0f);
					Vector3f home = new Vector3f(GunsmithRigs.SNUB_REVOLVER_CYLINDER).add(-1.5f, 0.0f, 0.0f);
					Keyframes.Sample at = Keyframes.path(new Vector3f(out).add(0.0f, 0.6f, 1.6f), 20.0f)
							.key(0.28f, out, -60.0f)
							.key(0.46f, home, -70.0f, Ease.IN)
							.key(0.6f, new Vector3f(home).add(-0.6f, -0.4f, 0.4f), -40.0f, Ease.OUT)
							.build().sample(p);
					float leave = 1.0f - GunMotion.smooth(GunMotion.span(p, 0.6f, 1.0f));
					return new GunAnimation.HandOverride(false, onGun(item, at.point()), leave, at.twist() * leave);
				}
				return null;
			}
			case SPEAR -> {
				if (live && action.kind() == ShotgunAction.Kind.PUMP) {
					// Over to the cocking knob, draws it back and lets it run home, then back under the reservoir.
					Vector3f work = GunsmithRigs.HARPOON_GUN_WORK;
					float cock = COCK.sample(p);
					Path path = Keyframes.path(new Vector3f(work).add(-1.4f, 1.0f, -3.0f), 30.0f)
							.key(0.22f, new Vector3f(work).add(-0.5f, 0.6f, 0.2f), 70.0f)
							.key(0.25f, work, 80.0f)
							.build();
					Vector3f point = p < 0.25f ? path.sample(p).point()
							: new Vector3f(work).add(0.0f, 0.0f, GunsmithRigs.HARPOON_GUN_TRAVEL.z * cock);
					float blend = GunMotion.smooth(GunMotion.span(p, 0.0f, 0.12f)) * (1.0f - GunMotion.smooth(GunMotion.span(p, 0.66f, 0.95f)));
					return new GunAnimation.HandOverride(false, onGun(item, point), blend, 80.0f * blend);
				}
				return null;
			}
			case SLINGSHOT -> {
				// The off hand always holds the pouch.
				return new GunAnimation.HandOverride(false, onGun(item, pouch(part(stack, true))), 1.0f, 15.0f);
			}
		}
		return null;
	}

	// ----------------------------------------------------------------------------------------------- the rounds

	/** The model drawn for a round in the hand ("3d") or flying out ("spent"); the silver buckshot looks like plain buckshot. */
	private static String roundModel(Holder<Item> round, String variant) {
		String path = BuiltInRegistries.ITEM.getKey(round.value()).getPath();
		if (path.startsWith("shell_12g_") && !path.equals("shell_12g_slug")) {
			path = "shell_12g_buckshot";
		}
		return path + "_" + variant;
	}

	/**
	 * Draws, for the holder in first person, the round the off hand is loading, hulls flying out of a broken breech and the
	 * case thrown from the lever gun's port. The pose stack holds the gun's item pose (before its display transform).
	 */
	public static void submitShells(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack stack, boolean mirrored,
			LocalPlayer player) {
		Item item = stack.getItem();
		Kind kind = kind(item);
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		float p = progress(action);
		// The round in the fingers (and, on the slingshot, the stone sitting in the pouch as it's drawn).
		if (action != null && action.kind() == ShotgunAction.Kind.LOAD && p >= FETCHED - 0.05f && (p < PUSHED || kind == Kind.SLINGSHOT)) {
			Vector3f hand = loadingHand(item, kind, rounds(stack), p).point();
			float push = GunMotion.smooth(GunMotion.span(p, AT_PORT, PUSHED));
			float spinX = 0.0f;
			Vector3f at;
			switch (kind) {
				case BREAK -> {
					Vector3f axis = boreAxis(item);
					at = hand.add(0.0f, -0.6f, 0.0f).add(new Vector3f(axis).mul(0.6f + 1.4f * push));
					spinX = -openAngle(item);
				}
				case LEVER -> at = hand.add(-0.3f, 0.2f, -0.9f - 1.4f * push);
				case REVOLVER -> at = hand.add(0.0f, 0.2f, -0.8f - 1.0f * push);
				case SPEAR -> at = hand.add(0.0f, -0.3f, -3.0f + 2.5f * push);
				default -> at = p >= PUSHED ? pouch(GunMotion.smooth(GunMotion.span(p, PUSHED, 1.0f))).add(0.0f, 0.0f, -0.35f)
						: hand.add(0.0f, 0.2f, -0.5f);
			}
			submitRound(poseStack, collector, light, item, mirrored, player, action.shell(), "3d", at, spinX, 0.0f);
		}
		// The lever gun's case, out of the port to the right at the bottom of the stroke.
		if (kind == Kind.LEVER && action != null && action.kind() == ShotgunAction.Kind.PUMP && !action.chambering()) {
			float t = (float) (now() - (action.start() + LEVER_EJECT * action.ticks()));
			if (t >= 0.0f && t < EJECT_TICKS) {
				Vector3f at = new Vector3f(GunsmithRigs.LEVER_ACTION_EJECT).add(1.6f * t, 1.2f * t - 0.32f * t * t, 0.4f * t);
				submitRound(poseStack, collector, light, item, mirrored, player, action.shell(), "spent", at, 70.0f * t, 40.0f * t);
			}
		}
		for (Iterator<Flying> it = FLYING.iterator(); it.hasNext(); ) {
			Flying hull = it.next();
			float t = (Util.getMillis() - hull.start()) / 50.0f;
			if (t >= EJECT_TICKS || hull.item() != item) {
				it.remove();
				continue;
			}
			if (t < 0.0f) {
				continue;
			}
			Vector3f at = new Vector3f(hull.from()).add(hull.velocity().x * t, hull.velocity().y * t - 0.3f * t * t, hull.velocity().z * t);
			submitRound(poseStack, collector, light, item, mirrored, player, hull.round(), "spent", at, -40.0f + 60.0f * t, 20.0f * t);
		}
	}

	private static void submitRound(PoseStack poseStack, SubmitNodeCollector collector, int light, Item gun, boolean mirrored,
			LocalPlayer player, Holder<Item> round, String variant, Vector3f at, float spinX, float spinY) {
		ItemStack render = new ItemStack(round);
		render.set(DataComponents.ITEM_MODEL, MyMod.id(roundModel(round, variant)));
		poseStack.pushPose();
		poseStack.mulPose(GunDisplay.of(gun).matrix(mirrored));
		poseStack.translate(at.x / 16.0f, at.y / 16.0f, at.z / 16.0f);
		poseStack.rotateDegrees(Axis.XP, spinX);
		poseStack.rotateDegrees(Axis.YP, spinY);
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(ROUND_STATE, render, ItemDisplayContext.NONE, player.level(), player, 0);
		ROUND_STATE.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
