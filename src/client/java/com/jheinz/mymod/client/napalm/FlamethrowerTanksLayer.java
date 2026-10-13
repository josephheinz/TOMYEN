package com.jheinz.mymod.client.napalm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.napalm.FlamethrowerItem;
import com.jheinz.mymod.napalm.Napalm;

/**
 * The flamethrower's twin tanks, worn on the back of anyone holding one: the Flamethrower Tanks item model, hung from
 * the body so it leans when they crouch. The model's manifold (TANK_TOP in tools/gen_napalm.py) sits at the shoulders
 * and its strap face (TANK_FRONT) against the back.
 */
public class FlamethrowerTanksLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Model pixels: the manifold's top and the straps' face, from tools/gen_napalm.py. */
	private static final float TANK_TOP = 14.0f;
	private static final float TANK_FRONT = 5.4f;
	/** The back of the body, in model pixels behind its centre. */
	private static final float BACK = 2.05f;

	private final ItemStackRenderState tanks = new ItemStackRenderState();
	private ItemStack tanksStack;

	public FlamethrowerTanksLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !(state.getMainHandItemStack().getItem() instanceof FlamethrowerItem)) {
			return;
		}
		if (tanksStack == null) {
			tanksStack = new ItemStack(Napalm.FLAMETHROWER_TANKS);
		}
		Minecraft.getInstance().getItemModelResolver().updateForTopItem(tanks, tanksStack, ItemDisplayContext.NONE,
				Minecraft.getInstance().level, null, 0);
		poseStack.pushPose();
		getParentModel().body.translateAndRotate(poseStack);
		// Model space here is y down and +z out of the back; the item model is y up, drawn about its centre. Turning it
		// half round z stands it up the right way; then put the manifold at the shoulders and the straps on the back.
		poseStack.translate(0.0f, (TANK_TOP - 8.0f) / 16.0f, (BACK + 8.0f - TANK_FRONT) / 16.0f);
		poseStack.rotateDegrees(Axis.ZP, 180.0f);
		tanks.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
