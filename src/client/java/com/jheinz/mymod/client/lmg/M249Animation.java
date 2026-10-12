package com.jheinz.mymod.client.lmg;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.ModSounds;
import com.jheinz.mymod.client.GunAnimation;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.client.GunMotion;
import com.jheinz.mymod.client.GunScope;
import com.jheinz.mymod.lmg.MachineGunItem;
import com.jheinz.mymod.lmg.MachineGuns;

/**
 * The M249's first-person handling:
 * <ul>
 * <li>Charging it when it's drawn or after reloading an empty gun: the shooting hand reaches to the handle on the
 * right, draws it back and runs it home.</li>
 * <li>Reloading: the off hand lifts the feed cover, takes the belt off the tray, drops the box and fetches a new one,
 * lays its belt on the tray and slams the cover shut.</li>
 * <li>Inspecting it on demand.</li>
 * </ul>
 * Moving parts step through model frames picked by mymod:gun_animation (1-6 the charging handle, 7-12 the cover
 * opening, 13-16 the box being changed; see tools/gen_m249.py) while the hand rides them and the gun turns toward it.
 */
public final class M249Animation {
	private enum Kind {
		RACK, RELOAD, INSPECT
	}

	private record Running(Kind kind, long startMillis, long durationMillis) {
		float progress() {
			return (Util.getMillis() - startMillis) / (float) durationMillis;
		}
	}

	private record Cue(float at, SoundEvent sound, float volume, float pitch) {
	}

	// Model points (pixels) from tools/gen_m249.py.
	private static final Vector3f SUPPORT = new Vector3f(8.0f, 6.1f, 2.0f);
	/** The feed cover hinges at its front; the hand lifts it by its back edge. */
	private static final Vector3f HINGE = new Vector3f(8.0f, 10.6f, 7.3f);
	private static final Vector3f COVER_EDGE = new Vector3f(8.0f, 11.0f, 16.8f);
	private static final Vector3f FEED = new Vector3f(6.3f, 10.0f, 9.9f);
	private static final Vector3f BELT_END = new Vector3f(4.5f, 9.4f, 9.9f);
	private static final Vector3f BOX = new Vector3f(5.3f, 2.4f, 9.75f);
	private static final Vector3f HANDLE = new Vector3f(10.2f, 8.6f, 7.65f);
	private static final float HANDLE_TRAVEL = 4.0f;
	private static final float COVER_OPEN = 75.0f;
	private static final float BOX_DROP = 3.0f;
	/** Where the off hand goes for a new box, relative to the old one (pixels: down off the screen). */
	private static final Vector3f FETCH = new Vector3f(-3.0f, -14.0f, 4.0f);

	private static final long RACK_MILLIS = 1000;
	private static final long INSPECT_MILLIS = 3200;

	private static final Cue[] RACK_CUES = {new Cue(0.3f, ModSounds.RACK_BACK, 0.9f, 0.75f), new Cue(0.62f, ModSounds.RACK_FORWARD, 0.9f, 0.72f)};
	private static final Cue[] RELOAD_CUES = {new Cue(0.08f, MachineGuns.FEED_COVER_OPEN, 0.9f, 1.0f),
			new Cue(0.2f, MachineGuns.BELT_RATTLE, 0.8f, 1.0f), new Cue(0.31f, ModSounds.MAG_OUT, 0.9f, 0.7f),
			new Cue(0.57f, ModSounds.MAG_IN, 1.0f, 0.68f), new Cue(0.64f, MachineGuns.BELT_RATTLE, 0.8f, 1.1f),
			new Cue(0.8f, MachineGuns.FEED_COVER_CLOSE, 1.0f, 1.0f)};

	private static Running running;
	private static Running queued;
	private static int lastSlot = -1;
	private static Item lastItem;
	private static int lastRounds = -1;
	private static float lastCueProgress = -1.0f;

	private M249Animation() {
	}

	// ----------------------------------------------------------------------------------------------- control

	/** Called every client tick: starts the charging and reload animations, puts an inspection away, plays cues. */
	public static void tick(LocalPlayer player) {
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		int slot = player.getInventory().getSelectedSlot();
		MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
		int rounds = loaded == null ? -1 : loaded.rounds();
		if (held.getItem() instanceof MachineGunItem gun) {
			if (slot != lastSlot || held.getItem() != lastItem) {
				queued = null;
				start(Kind.RACK, RACK_MILLIS);
			} else if (rounds > lastRounds) {
				start(Kind.RELOAD, (long) ((gun.stats().reloadTicks() + gun.loadedMagazine(held).extraReloadTicks()) * 50L
						* GunItem.reloadFactor(player)));
				// An empty gun has to be charged once the new belt is in.
				queued = lastRounds <= 0 ? new Running(Kind.RACK, 0, RACK_MILLIS) : null;
			}
		} else {
			running = null;
			queued = null;
		}
		lastSlot = slot;
		lastItem = held.getItem();
		lastRounds = rounds;

		Running now = current();
		if (now != null && now.kind() == Kind.INSPECT && (GunRecoil.kick() > 0.01f || GunScope.zoom(player) > 0.0f)) {
			running = null;
			now = null;
		}
		float p = now == null ? -1.0f : now.progress();
		if (now != null) {
			for (Cue cue : now.kind() == Kind.RACK ? RACK_CUES : now.kind() == Kind.RELOAD ? RELOAD_CUES : new Cue[0]) {
				if (cue.at() > lastCueProgress && cue.at() <= p) {
					player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
							cue.volume(), cue.pitch() * (0.95f + player.getRandom().nextFloat() * 0.1f), false);
				}
			}
		}
		lastCueProgress = p;
	}

	private static void start(Kind kind, long millis) {
		running = new Running(kind, Util.getMillis(), millis);
		lastCueProgress = 0.0f;
	}

	/** The running animation, moving on to the queued one when it finishes. */
	private static Running current() {
		if (running != null && running.progress() >= 1.0f) {
			running = queued == null ? null : new Running(queued.kind(), Util.getMillis(), queued.durationMillis());
			queued = null;
			lastCueProgress = 0.0f;
		}
		return running;
	}

	private static Running current(ItemStack stack) {
		return stack.getItem() instanceof MachineGunItem ? current() : null;
	}

	public static boolean busy(ItemStack stack) {
		return current(stack) != null;
	}

	public static void inspect(LocalPlayer player) {
		if (GunScope.zoom(player) <= 0.0f && current() == null && queued == null) {
			start(Kind.INSPECT, INSPECT_MILLIS);
		}
	}

	// ---------------------------------------------------------------------------------------------- timeline

	private static float span(float p, float a, float b) {
		return GunMotion.span(p, a, b);
	}

	private static float smooth(float t) {
		return GunMotion.smooth(t);
	}

	/** How far open the feed cover is during a reload, 0 to 1. */
	private static float cover(float p) {
		return smooth(span(p, 0.06f, 0.16f)) * (1.0f - smooth(span(p, 0.7f, 0.8f)));
	}

	/** How far the box has dropped (1 is as far as the model shows it before it's gone). */
	private static float drop(float p) {
		return p < 0.38f ? span(p, 0.3f, 0.38f) : p < 0.5f ? 1.0f : 1.0f - span(p, 0.5f, 0.58f);
	}

	/** The belt is off the tray, hanging from the box, through this stretch. */
	private static boolean beltOut(float p) {
		return p >= 0.22f && p < 0.66f;
	}

	/** How far back the charging handle is, 0 to 1. */
	private static float handle(float p) {
		return p < 0.22f ? 0.0f : p < 0.45f ? smooth(span(p, 0.22f, 0.45f)) : p < 0.55f ? 1.0f : 1.0f - smooth(span(p, 0.55f, 0.65f));
	}

	/** The model frame for the mymod:gun_animation property (0 at rest; see tools/gen_m249.py). */
	public static float frame(ItemStack stack) {
		Running run = current(stack);
		if (run == null) {
			return 0.0f;
		}
		float p = run.progress();
		switch (run.kind()) {
			case RACK -> {
				return Math.round(handle(p) * 6.0f);
			}
			case RELOAD -> {
				if (beltOut(p)) {
					return p >= 0.38f && p < 0.5f ? 16.0f : 13.0f + Math.round(drop(p) * 2.0f);
				}
				float c = cover(p);
				return c <= 0.01f ? 0.0f : 6.0f + Math.max(1, Math.round(c * 6.0f));
			}
			default -> {
				return 0.0f;
			}
		}
	}

	/** The back edge of the feed cover (model pixels), swung up by {@code open} (0 to 1). */
	private static Vector3f coverEdge(float open) {
		float a = (float) Math.toRadians(-COVER_OPEN * open);
		float dy = COVER_EDGE.y - HINGE.y;
		float dz = COVER_EDGE.z - HINGE.z;
		return new Vector3f(COVER_EDGE.x, HINGE.y + dy * (float) Math.cos(a) - dz * (float) Math.sin(a),
				HINGE.z + dy * (float) Math.sin(a) + dz * (float) Math.cos(a));
	}

	private static Vector3f box(float dropped) {
		return new Vector3f(BOX).add(-0.4f * dropped, -BOX_DROP * dropped, 0.0f);
	}

	/** Where the off hand is during a reload (model pixels). */
	private static Vector3f reloadHand(float p, Vector3f support) {
		if (p < 0.06f) {
			return new Vector3f(support).lerp(coverEdge(0.0f), smooth(span(p, 0.0f, 0.06f)));
		} else if (p < 0.16f) {
			return coverEdge(cover(p));
		} else if (p < 0.22f) {
			return coverEdge(1.0f).lerp(FEED, smooth(span(p, 0.16f, 0.22f)));
		} else if (p < 0.3f) {
			return new Vector3f(FEED).lerp(box(0.0f), smooth(span(p, 0.22f, 0.3f)));
		} else if (p < 0.38f || p >= 0.5f && p < 0.58f) {
			return box(drop(p));
		} else if (p < 0.5f) {
			Vector3f fetch = box(1.0f).add(FETCH);
			return box(1.0f).lerp(fetch, smooth(span(p, 0.38f, 0.44f)) * (1.0f - smooth(span(p, 0.44f, 0.5f))));
		} else if (p < 0.62f) {
			return box(0.0f).lerp(BELT_END, smooth(span(p, 0.58f, 0.62f)));
		} else if (p < 0.68f) {
			return new Vector3f(BELT_END).lerp(FEED, smooth(span(p, 0.62f, 0.68f)));
		} else if (p < 0.7f) {
			return new Vector3f(FEED).lerp(coverEdge(1.0f), smooth(span(p, 0.68f, 0.7f)));
		} else if (p < 0.8f) {
			return coverEdge(cover(p));
		}
		return coverEdge(0.0f).lerp(support, smooth(span(p, 0.8f, 0.92f)));
	}

	// ------------------------------------------------------------------------------------------------ output

	/** Turns the gun toward the working hand and shakes it as parts hit their stops. */
	public static void applyGunPose(PoseStack poseStack, ItemStack stack, GunHands.Anchors anchors, boolean mirrored) {
		Running run = current(stack);
		if (run == null || anchors == null) {
			return;
		}
		float p = run.progress();
		switch (run.kind()) {
			case INSPECT -> GunMotion.inspect(poseStack, p, (Util.getMillis() - run.startMillis()) / 1000.0f, anchors.grip(), mirrored);
			case RACK -> {
				float engaged = smooth(span(p, 0.0f, 0.2f)) * (1.0f - smooth(span(p, 0.66f, 0.9f)));
				float shake = GunMotion.jolt(p, 0.45f, run.durationMillis(), 0.4f) + GunMotion.jolt(p, 0.65f, run.durationMillis(), 1.0f);
				poseStack.translate(0.0f, 0.0f, 0.02f * handle(p));
				GunMotion.turn(poseStack, anchors.support(), 4.0f, -6.0f, -14.0f, 0.03f, engaged, shake, mirrored);
			}
			case RELOAD -> {
				float engaged = smooth(span(p, 0.0f, 0.08f)) * (1.0f - smooth(span(p, 0.84f, 0.98f)));
				long millis = run.durationMillis();
				float shake = GunMotion.jolt(p, 0.16f, millis, 0.4f) + GunMotion.jolt(p, 0.3f, millis, 0.3f)
						+ GunMotion.jolt(p, 0.58f, millis, 0.6f) + GunMotion.jolt(p, 0.8f, millis, 1.0f);
				GunMotion.turn(poseStack, anchors.grip(), 6.0f, 14.0f, 22.0f, 0.05f, engaged, shake, mirrored);
			}
		}
	}

	/** Where a hand is pulled to: the shooting hand to the charging handle, or the off hand through a reload. */
	public static GunAnimation.HandOverride hand(ItemStack stack) {
		Running run = current(stack);
		if (run == null) {
			return null;
		}
		Item item = stack.getItem();
		float p = run.progress();
		return switch (run.kind()) {
			case RACK -> new GunAnimation.HandOverride(true,
					GunMotion.onGun(item, new Vector3f(HANDLE).add(0.0f, 0.0f, HANDLE_TRAVEL * handle(p))),
					smooth(span(p, 0.0f, 0.2f)) * (1.0f - smooth(span(p, 0.66f, 0.86f))));
			case RELOAD -> new GunAnimation.HandOverride(false, GunMotion.onGun(item, reloadHand(p, SUPPORT)),
					1.0f - smooth(span(p, 0.9f, 0.97f)));
			case INSPECT -> null;
		};
	}
}
