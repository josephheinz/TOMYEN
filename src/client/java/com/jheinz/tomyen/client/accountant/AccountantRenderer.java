package com.jheinz.tomyen.client.accountant;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.accountant.Accountant;

/**
 * Draws the Accountant (and his juniors, the same model at half the size): the suit, glasses and the rest solid, then his
 * jelly shell see-through over them, the way a slime's outer layer is drawn.
 */
public class AccountantRenderer extends AbstractCubeMobRenderer<Accountant, AccountantRenderState, AccountantModel> {
	public static final Identifier TEXTURE = MyMod.id("textures/entity/accountant/accountant.png");

	public AccountantRenderer(EntityRendererProvider.Context context) {
		super(context, new AccountantModel(context.bakeLayer(AccountantModel.LAYER)));
		addLayer(new ShellLayer(this, new SlimeModel(context.bakeLayer(AccountantModel.SHELL_LAYER))));
	}

	/** The model is drawn at twice a slime's scale, for a finer texture. */
	@Override
	protected void scale(AccountantRenderState state, PoseStack poseStack) {
		poseStack.scale(0.5f, 0.5f, 0.5f);
		super.scale(state, poseStack);
	}

	@Override
	public Identifier getTextureLocation(AccountantRenderState state) {
		return TEXTURE;
	}

	@Override
	public AccountantRenderState createRenderState() {
		return new AccountantRenderState();
	}

	@Override
	public void extractRenderState(Accountant accountant, AccountantRenderState state, float partialTicks) {
		super.extractRenderState(accountant, state, partialTicks);
		state.audit = accountant.auditProgress(partialTicks);
	}

	/** The jelly shell, translucent, as vanilla's SlimeOuterLayer draws a slime's. */
	private static class ShellLayer extends RenderLayer<AccountantRenderState, AccountantModel> {
		private final SlimeModel shell;

		ShellLayer(RenderLayerParent<AccountantRenderState, AccountantModel> renderer, SlimeModel shell) {
			super(renderer);
			this.shell = shell;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AccountantRenderState state, float yRot,
				float xRot) {
			boolean outlineOnly = state.appearsGlowing() && state.isInvisible;
			if (state.isInvisible && !outlineOnly) {
				return;
			}
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
			collector.order(1).submitModel(shell, state, poseStack,
					outlineOnly ? RenderTypes.outline(TEXTURE) : RenderTypes.entityTranslucent(TEXTURE), lightCoords, overlay, state.outlineColor);
		}
	}
}
