package com.jheinz.tomyen.client.sodajetpack;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.sodajetpack.SodaJetpacks;

/**
 * Draws a worn Soda-Bottle Jetpack: a Fabric armor renderer for the chest, its {@link SodaJetpackModel} copying the
 * wearer's pose so it rides on the torso through every walk, crouch and swim (and socketed in an exoskeleton too).
 * Being shaken it jiggles hard from side to side; under thrust it trembles. Enchanted, it glints like armor.
 */
public final class SodaJetpackRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/soda_jetpack.png");
	private static final RenderType RENDER_TYPE = RenderTypes.armorCutoutNoCull(TEXTURE);
	private static final RenderType GLINT = RenderTypes.armorCutoutNoCullGlint(TEXTURE);
	/** How far a shake throws it, and a thrust's tremble, in blocks. */
	private static final float SHAKE = 1.2f / 16.0f;
	private static final float TREMBLE = 0.25f / 16.0f;

	private SodaJetpackRenderer() {
	}

	static void initialize() {
		ModelLayerRegistry.registerModelLayer(SodaJetpackModel.LAYER, SodaJetpackModel::createLayer);
		ArmorRenderer.register(context -> {
			SodaJetpackModel model = new SodaJetpackModel(context.bakeLayer(SodaJetpackModel.LAYER));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				if (slot != EquipmentSlot.CHEST || state.isBaby) {
					return;
				}
				int fx = fx(state);
				float age = state.ageInTicks;
				float dx = 0.0f, dy = 0.0f;
				if ((fx & SodaJetpacks.FX_SHAKE) != 0) {
					dx = Mth.sin(age * 2.6f) * SHAKE;
					dy = Mth.cos(age * 3.3f) * SHAKE * 0.4f;
				} else if ((fx & SodaJetpacks.FX_THRUST) != 0) {
					dx = Mth.sin(age * 5.1f) * TREMBLE;
					dy = Mth.sin(age * 7.3f) * TREMBLE;
				}
				poseStack.pushPose();
				poseStack.translate(dx, dy, 0.0f);
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack,
						stack.hasFoil() ? GLINT : RENDER_TYPE, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			};
		}, SodaJetpacks.SODA_JETPACK);
	}

	/** What the wearer's jetpack is doing, if the wearer is a player this client knows. */
	private static int fx(HumanoidRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state instanceof AvatarRenderState avatar && minecraft.level != null) {
			Entity entity = minecraft.level.getEntity(avatar.id);
			if (entity instanceof LivingEntity living) {
				return SodaJetpacks.fx(living);
			}
		}
		return 0;
	}
}
