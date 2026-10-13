package com.jheinz.mymod.client.drill;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.drill.DrillItem;
import com.jheinz.mymod.drill.Drills;

/**
 * Where the hands go on each drill in first person, held like the long guns: the shooting hand on the pistol grip and
 * the off hand on the side handle, or on the Hand Drill's crank knob, which goes round with the crank. Anchors are in
 * the held item's space as in {@link GunHands}; tools/gen_drills.py prints them from the models.
 * <p>
 * A running drill also shudders in the hands, harder while the bit bites rock.
 */
public final class DrillHands {
	private static final Vector3f GRIP = new Vector3f(-0.022f, 0.119f, 0.156f);
	private static final Vector3f STEEL_HANDLE = new Vector3f(-0.143f, 0.314f, -0.171f);
	private static final Vector3f HEAVY_HANDLE = new Vector3f(-0.137f, 0.337f, -0.209f);
	/** The Hand Drill's crank knob, frame by frame. */
	private static final Vector3f[] CRANK_KNOB = {
			new Vector3f(-0.170f, 0.385f, -0.084f), new Vector3f(-0.159f, 0.353f, -0.161f), new Vector3f(-0.155f, 0.276f, -0.192f),
			new Vector3f(-0.159f, 0.200f, -0.161f), new Vector3f(-0.170f, 0.168f, -0.084f), new Vector3f(-0.181f, 0.200f, -0.008f),
			new Vector3f(-0.185f, 0.276f, 0.023f), new Vector3f(-0.181f, 0.353f, -0.008f)};

	private DrillHands() {
	}

	public static GunHands.Anchors anchors(ItemStack stack, int frame) {
		if (stack.is(Drills.HAND_DRILL)) {
			return new GunHands.Anchors(GRIP, CRANK_KNOB[Math.floorMod(frame, CRANK_KNOB.length)]);
		}
		return new GunHands.Anchors(GRIP, stack.is(Drills.STEEL_DRILL) ? STEEL_HANDLE : HEAVY_HANDLE);
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
