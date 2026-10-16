package com.jheinz.tomyen.client.drill;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.client.GunHands;
import com.jheinz.tomyen.drill.DrillItem;
import com.jheinz.tomyen.drill.Drills;

/**
 * Where the hands go on each drill in first person, held two-handed at the chest: the main hand on the bottom handle of
 * the rear cross and the off hand on its left handle, or on the Hand Drill's crank knob, which goes round with the crank. Anchors are in
 * the held item's space as in {@link GunHands}; tools/gen_drills.py prints them from the models.
 * <p>
 * A running drill also shudders in the hands, harder while the bit bites rock.
 */
public final class DrillHands {
	private static final Vector3f GRIP = new Vector3f(0.150f, -0.150f, -0.187f);
	/** The left handle of the rear cross, the same on every motor drill. */
	private static final Vector3f HANDLE = new Vector3f(-0.047f, 0.068f, -0.096f);
	/** The Hand Drill's crank knob, frame by frame. */
	private static final Vector3f[] CRANK_KNOB = {
			new Vector3f(-0.207f, 0.191f, -0.281f), new Vector3f(-0.231f, 0.176f, -0.332f), new Vector3f(-0.249f, 0.135f, -0.369f),
			new Vector3f(-0.255f, 0.078f, -0.383f), new Vector3f(-0.249f, 0.022f, -0.369f), new Vector3f(-0.231f, -0.019f, -0.332f),
			new Vector3f(-0.207f, -0.034f, -0.281f), new Vector3f(-0.184f, -0.019f, -0.230f), new Vector3f(-0.166f, 0.022f, -0.193f),
			new Vector3f(-0.160f, 0.078f, -0.179f), new Vector3f(-0.166f, 0.135f, -0.193f), new Vector3f(-0.184f, 0.176f, -0.230f)};

	private DrillHands() {
	}

	public static GunHands.Anchors anchors(ItemStack stack, int frame) {
		if (stack.is(Drills.HAND_DRILL)) {
			return new GunHands.Anchors(GRIP, CRANK_KNOB[Math.floorMod(frame, CRANK_KNOB.length)]);
		}
		return new GunHands.Anchors(GRIP, HANDLE);
	}

	/** A fast shudder, scaled by how fast the bit turns; the hands ride along. */
	public static void applyShake(PoseStack poseStack, LivingEntity holder, ItemStack stack) {
		float spin = DrillSpin.speed(holder);
		if (spin <= 0.0f) {
			return;
		}
		boolean hand = stack.getItem() instanceof DrillItem drill && drill.engine() == DrillItem.Engine.HAND_CRANK;
		float amount = spin * (hand ? 0.4f : 1.0f) * (DrillSpin.biting(holder) ? 1.0f : 0.35f);
		float t = (Util.getMillis() % 100_000L) / 1000.0f;
		poseStack.translate(Mth.sin(t * 97.0f) * 0.004f * amount, Mth.sin(t * 131.0f) * 0.004f * amount, Mth.sin(t * 71.0f) * 0.003f * amount);
		poseStack.rotateDegrees(Axis.ZP, Mth.sin(t * 113.0f) * 0.6f * amount);
	}
}
