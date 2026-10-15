package com.jheinz.mymod.client.detector;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.detector.Detectors;
import com.jheinz.mymod.detector.OreTarget;
import com.jheinz.mymod.detector.ProspectorsPendulumItem;

/**
 * A held Prospector's Pendulum. It looks for its ore round the holder's eyes ({@link SectionScanner}, a few sections
 * a tick), takes the nearest block within {@value Detectors#PENDULUM_RANGE}, and swings: the bob leans toward it
 * (all the way over when it's behind, telling you to turn), and swings wider and faster the closer it is. With
 * nothing found it only sways a little. Each swing's end ticks softly while it has something; finding something new
 * chimes. The bob's position (-1 left to 1 right) drives the item's sprite (see {@link DetectorProperty}).
 */
final class PendulumClient {
	private static final SectionScanner SCANNER = new SectionScanner(Detectors.PENDULUM_RANGE, 12);

	private static @Nullable OreTarget target;
	private static @Nullable BlockPos nearest;
	private static double distance;
	private static float lean;
	private static float phase;
	private static float bob;
	private static float previousBob;
	private static boolean held;

	private PendulumClient() {
	}

	static void reset() {
		SCANNER.reset();
		target = null;
		nearest = null;
		held = false;
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		ItemStack stack = DetectorClient.held(player, Detectors.PROSPECTORS_PENDULUM);
		previousBob = bob;
		if (stack.isEmpty()) {
			if (held) {
				reset();
			}
			bob = 0.0f;
			return;
		}
		held = true;
		target = ProspectorsPendulumItem.tuning(stack);
		BlockPos found = null;
		if (target != null) {
			OreTarget ore = target;
			BlockPos eyes = BlockPos.containing(player.getEyePosition());
			SCANNER.tick(minecraft.level, eyes, ore.id(), ore.blocks());
			found = closest(player.getEyePosition());
			if (found != null && nearest == null) {
				DetectorClient.play(minecraft, player, Detectors.PENDULUM_FOUND, 0.5f, 1.0f);
			}
		}
		nearest = found;
		float closeness = closeness();
		float wantLean = 0.0f;
		if (nearest != null) {
			float rel = DetectorClient.relativeYaw(player, nearest.getX() + 0.5, nearest.getZ() + 0.5);
			// Straight below or above, it just swings in place.
			double flat = DetectorClient.flatDistance(player, nearest.getX() + 0.5, nearest.getZ() + 0.5);
			wantLean = flat < 1.5 ? 0.0f : Mth.clamp(rel / 90.0f, -1.0f, 1.0f) * (0.45f + 0.35f * closeness);
		}
		lean += (wantLean - lean) * 0.15f;
		float amplitude = nearest == null ? 0.12f : 0.2f + 0.3f * closeness;
		float before = Mth.cos(phase);
		phase += nearest == null ? 0.12f : 0.2f + 0.2f * closeness;
		bob = Mth.clamp(lean + amplitude * Mth.sin(phase), -1.0f, 1.0f);
		// A tick as the bob turns at either end of its swing.
		if (nearest != null && Math.signum(before) != Math.signum(Mth.cos(phase))) {
			DetectorClient.play(minecraft, player, Detectors.PENDULUM_TICK, 0.12f + 0.25f * closeness, 0.9f + 0.7f * closeness);
		}
	}

	private static @Nullable BlockPos closest(Vec3 eyes) {
		double[] best = {Detectors.PENDULUM_RANGE * Detectors.PENDULUM_RANGE};
		long[] pos = {Long.MIN_VALUE};
		SCANNER.forEach(packed -> {
			double d = eyes.distanceToSqr(BlockPos.getX(packed) + 0.5, BlockPos.getY(packed) + 0.5, BlockPos.getZ(packed) + 0.5);
			if (d < best[0]) {
				best[0] = d;
				pos[0] = packed;
			}
		});
		distance = Math.sqrt(best[0]);
		return pos[0] == Long.MIN_VALUE ? null : BlockPos.of(pos[0]);
	}

	/** 0 at the edge of range to 1 right on top of it. */
	static float closeness() {
		return nearest == null ? 0.0f : Mth.clamp(1.0f - (float) distance / Detectors.PENDULUM_RANGE, 0.0f, 1.0f);
	}

	static float bob(float partialTicks) {
		return Mth.lerp(partialTicks, previousBob, bob);
	}

	static @Nullable OreTarget target() {
		return target;
	}

	static @Nullable BlockPos nearest() {
		return nearest;
	}
}
