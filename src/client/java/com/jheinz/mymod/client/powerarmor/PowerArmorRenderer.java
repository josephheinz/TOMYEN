package com.jheinz.mymod.client.powerarmor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.HumanoidArm;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.powerarmor.PowerArmor;
import com.jheinz.mymod.powerarmor.PowerArmorPiece;
import com.jheinz.mymod.powerarmor.PowerArmorSuit;
import com.jheinz.mymod.powerarmor.PowerArmorTier;
import com.jheinz.mymod.powerarmor.SuitLook;

/**
 * Draws power armor wherever it stands: worn (see {@link PowerArmorLayer}), on a station's rack, parked, and as
 * the first-person gauntlet. The suit is drawn {@link #SCALE} times the size of the body inside it, scaled from
 * the feet. Each piece is submitted on its own, tinted by its condition (scuffed and darkening as it wears, dull
 * when broken), and then again in its glowing parts (the helmet lamp, and the jet exhaust while flying), brighter
 * for higher marks and dead on a broken piece.
 */
public final class PowerArmorRenderer {
	public static final float SCALE = 1.15f;
	private static final Identifier TEXTURE = MyMod.id("textures/entity/power_armor/power_armor.png");
	private static final Identifier GLOW_TEXTURE = MyMod.id("textures/entity/power_armor/power_armor_glow.png");
	private static final RenderType BASE = RenderTypes.entityCutout(TEXTURE);
	private static final RenderType GLOW = RenderTypes.eyes(GLOW_TEXTURE);
	private static final int[] GLOW_TINT = {0xFFA8A8A8, 0xFFDCDCDC, 0xFFFFFFFF};
	private static final int DEAD_GLOW = 0xFF303030;
	/** Model space puts the feet this far below the origin (vanilla's offset). */
	private static final float FEET = 1.501f;

	/** On a player's render state: how to draw the suit they're wearing, or absent if they aren't. */
	public static final RenderStateDataKey<Worn> WORN = RenderStateDataKey.create(() -> "mymod:power_armor");

	/**
	 * A worn suit, as extracted for one frame.
	 *
	 * @param flying whether its thrusters are lit
	 * @param flat   how far into the flat, elytra-like flying pose it is, 0 (upright hover) to 1
	 */
	public record Worn(SuitLook look, float open, float flame, boolean flying, float flat) {
	}

	private static @Nullable PowerArmorModel model;

	private PowerArmorRenderer() {
	}

	public static void initialize() {
		ModelLayerRegistry.registerModelLayer(PowerArmorModel.LAYER, PowerArmorModel::createLayer);
	}

	private static PowerArmorModel model() {
		if (model == null) {
			model = new PowerArmorModel(Minecraft.getInstance().getEntityModels().bakeLayer(PowerArmorModel.LAYER));
		}
		return model;
	}

	/**
	 * Submits a suit in entity model space (y down, feet {@link #FEET} below the origin).
	 *
	 * @param parts the wearer's part poses in piece order, or null for the suit's rest pose
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, PartPose @Nullable [] parts, SuitLook look, float open,
			float flame, float time, int light, int overlay, int outline, float scale) {
		poseStack.pushPose();
		poseStack.translate(0.0f, FEET, 0.0f);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0.0f, -FEET, 0.0f);
		PowerArmorModel model = model();
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			PowerArmorTier tier = look.tier(piece);
			if (tier == null) {
				continue;
			}
			float condition = look.condition(piece);
			PowerArmorModel.Pose pose = new PowerArmorModel.Pose(parts, look, piece, open, flame, time);
			collector.submitModel(model, pose, poseStack, BASE, light, overlay, conditionTint(condition), null, outline);
			collector.submitModel(model, pose, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY,
					condition > 0.0f ? GLOW_TINT[tier.ordinal()] : DEAD_GLOW, null, 0);
		}
		poseStack.popPose();
	}

	/** Scuffing as a piece wears: warmer and darker, then dull grey-brown when broken. */
	private static int conditionTint(float condition) {
		if (condition <= 0.0f) {
			return 0xFF5C5248;
		}
		return ARGB.srgbLerp((1.0f - condition) * 0.65f, 0xFFFFFFFF, 0xFF8C7458);
	}

	/**
	 * Submits a suit standing in its rest pose, with the pose stack at its feet: on a station's rack or parked.
	 *
	 * @param yaw the way it faces, as an entity's yaw
	 */
	public static void submitStanding(PoseStack poseStack, SubmitNodeCollector collector, SuitLook look, float yaw, float open, float time,
			int light, int outline) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0f - yaw);
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -FEET, 0.0f);
		submit(poseStack, collector, null, look, open, 0.0f, time, light, OverlayTexture.NO_OVERLAY, outline, SCALE);
		poseStack.popPose();
	}

	/**
	 * Draws the suit's gauntlet in place of the local player's first-person hand, posed as vanilla poses the hand.
	 * Returns false (drawing nothing) if they aren't in power armor.
	 */
	public static boolean submitFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm side) {
		LocalPlayer player = Minecraft.getInstance().player;
		PowerArmorSuit suit = player == null ? null : PowerArmor.worn(player);
		if (suit == null) {
			return false;
		}
		PowerArmorPiece arm = side == HumanoidArm.RIGHT ? PowerArmorPiece.RIGHT_ARM : PowerArmorPiece.LEFT_ARM;
		PartPose[] parts = new PartPose[PowerArmorPiece.COUNT];
		parts[arm.ordinal()] = side == HumanoidArm.RIGHT ? PartPose.offsetAndRotation(-5.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.1f)
				: PartPose.offsetAndRotation(5.0f, 2.0f, 0.0f, 0.0f, 0.0f, -0.1f);
		SuitLook look = suit.look();
		PowerArmorModel.Pose pose = new PowerArmorModel.Pose(parts, look, arm, 0.0f, 0.0f, player.tickCount);
		PowerArmorTier tier = look.tier(arm);
		if (tier == null) {
			return true;
		}
		float condition = look.condition(arm);
		collector.submitModel(model(), pose, poseStack, BASE, light, OverlayTexture.NO_OVERLAY, conditionTint(condition), null, 0);
		collector.submitModel(model(), pose, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY,
				condition > 0.0f ? GLOW_TINT[tier.ordinal()] : DEAD_GLOW, null, 0);
		return true;
	}
}
