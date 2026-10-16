package com.jheinz.mymod.client.scarecrow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.scarecrow.Scarecrow;

/**
 * Draws a scarecrow: its own model and look, whatever armor and head item it wears, and its glow (the Jack-o'-Scarecrow's
 * carved face after dark, the Scrap Scarecrow's LED eyes). The whole figure leans and rolls a little on its post in
 * the wind, more in rain and storms, and wobbles when punched. Not drawn at all while it's inside a saucer.
 */
public class ScarecrowRenderer extends LivingEntityRenderer<Scarecrow, ScarecrowRenderState, ScarecrowModel> {
	private static final Identifier[] TEXTURES = {
			MyMod.id("textures/entity/scarecrow/scarecrow.png"),
			MyMod.id("textures/entity/scarecrow/pumpkin_scarecrow.png"),
			MyMod.id("textures/entity/scarecrow/scrap_scarecrow.png")};
	private static final RenderType PUMPKIN_GLOW = RenderTypes.eyes(MyMod.id("textures/entity/scarecrow/pumpkin_scarecrow_glow.png"));
	private static final RenderType SCRAP_GLOW = RenderTypes.eyes(MyMod.id("textures/entity/scarecrow/scrap_scarecrow_glow.png"));

	public ScarecrowRenderer(EntityRendererProvider.Context context) {
		super(context, new ScarecrowModel(context.bakeLayer(ScarecrowModel.LAYER)), 0.4f);
		addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), ScarecrowArmorModel::new),
				context.getEquipmentRenderer()));
		addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
		addLayer(new RenderLayer<>(this) {
			@Override
			public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ScarecrowRenderState state, float yRot, float xRot) {
				if (state.glow != null) {
					collector.order(1).submitModel(getParentModel(), state, poseStack, state.glow, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				}
			}
		});
	}

	@Override
	public Identifier getTextureLocation(ScarecrowRenderState state) {
		return TEXTURES[state.variant.ordinal()];
	}

	@Override
	public ScarecrowRenderState createRenderState() {
		return new ScarecrowRenderState();
	}

	@Override
	public boolean shouldRender(Scarecrow scarecrow, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		return !scarecrow.isInspected() && super.shouldRender(scarecrow, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	public void extractRenderState(Scarecrow scarecrow, ScarecrowRenderState state, float partialTicks) {
		super.extractRenderState(scarecrow, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(scarecrow, state, partialTicks, itemModelResolver);
		Level level = scarecrow.level();
		state.variant = scarecrow.variant();
		state.phase = (scarecrow.getId() * 37) % 200;
		state.wind = 1.0f + 0.6f * level.getRainLevel(partialTicks) + 0.6f * level.getThunderLevel(partialTicks);
		state.wiggle = (level.getGameTime() - scarecrow.lastHit) + partialTicks;
		float health = scarecrow.getHealth() / scarecrow.getMaxHealth();
		state.tatter = Mth.clamp((0.6f - health) / 0.6f, 0.0f, 1.0f);
		state.lifted = scarecrow.isPassenger();
		state.hatOff = !scarecrow.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		state.glow = switch (state.variant) {
			case PUMPKIN -> level.isDarkOutside() || level.getMaxLocalRawBrightness(scarecrow.blockPosition()) < 8 ? PUMPKIN_GLOW : null;
			case SCRAP -> SCRAP_GLOW;
			default -> null;
		};
	}

	/** Facing its way, then leaning and rolling about the foot of its post in the wind (see tools/gen_scarecrow.py). */
	@Override
	protected void setupRotations(ScarecrowRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		poseStack.rotateDegrees(Axis.YP, 180.0f - bodyRot);
		float t = state.ageInTicks + state.phase;
		float lean = (Mth.sin(t * 0.045f) * 1.4f + Mth.sin(t * 0.13f) * 0.5f) * state.wind;
		float roll = (Mth.sin(t * 0.037f + 1.3f) * 1.8f + Mth.sin(t * 0.11f) * 0.4f) * state.wind;
		if (state.wiggle < 5.0f) {
			roll += Mth.sin(state.wiggle / 1.5f * Mth.PI) * 4.0f;
		}
		poseStack.rotateDegrees(Axis.XP, lean);
		poseStack.rotateDegrees(Axis.ZP, roll);
	}

	/** Lifted up on its post. */
	@Override
	protected void scale(ScarecrowRenderState state, PoseStack poseStack) {
		poseStack.translate(0.0f, -ScarecrowModel.LIFT / 16.0f, 0.0f);
	}
}
