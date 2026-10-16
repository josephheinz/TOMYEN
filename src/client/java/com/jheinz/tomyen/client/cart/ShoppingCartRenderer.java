package com.jheinz.tomyen.client.cart;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.cart.ShoppingCart;

public class ShoppingCartRenderer extends MobRenderer<ShoppingCart, ShoppingCartRenderState, ShoppingCartModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/shopping_cart/shopping_cart.png");
	private static final Identifier ANGRY_TEXTURE = MyMod.id("textures/entity/shopping_cart/shopping_cart_angry.png");
	private static final RenderType ANGRY_EYES = RenderTypes.eyes(MyMod.id("textures/entity/shopping_cart/shopping_cart_eyes.png"));
	/** The model is built at real-cart proportions; this brings it down to about a block tall. */
	private static final float SCALE = 0.75f;

	public ShoppingCartRenderer(EntityRendererProvider.Context context) {
		super(context, new ShoppingCartModel(context.bakeLayer(ShoppingCartModel.LAYER)), 0.5f);
		addLayer(new AngryEyesLayer(this));
	}

	@Override
	public Identifier getTextureLocation(ShoppingCartRenderState state) {
		return state.aggressive ? ANGRY_TEXTURE : TEXTURE;
	}

	@Override
	public ShoppingCartRenderState createRenderState() {
		return new ShoppingCartRenderState();
	}

	@Override
	public void extractRenderState(ShoppingCart cart, ShoppingCartRenderState state, float partialTicks) {
		super.extractRenderState(cart, state, partialTicks);
		state.aggressive = cart.isAggressive();
		state.swing = cart.getSwingAnimation(partialTicks);
		state.pounce = cart.pounce(partialTicks);
	}

	@Override
	protected void scale(ShoppingCartRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	/** Glowing red eyes while it's hunting. */
	private static class AngryEyesLayer extends EyesLayer<ShoppingCartRenderState, ShoppingCartModel> {
		AngryEyesLayer(ShoppingCartRenderer renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ShoppingCartRenderState state, float yRot,
				float xRot) {
			if (state.aggressive) {
				super.submit(poseStack, collector, light, state, yRot, xRot);
			}
		}

		@Override
		public RenderType renderType() {
			return ANGRY_EYES;
		}
	}
}
