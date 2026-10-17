package com.jheinz.tomyen.client.gravedigger;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.gravedigger.GraveDigger;

/** Draws the grave digger, his pinprick eyes and the lantern on his hip glowing in the dark. */
public class GraveDiggerRenderer extends MobRenderer<GraveDigger, GraveDiggerRenderState, GraveDiggerModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/grave_digger/grave_digger.png");
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/grave_digger/grave_digger_glow.png"));

	public GraveDiggerRenderer(EntityRendererProvider.Context context) {
		super(context, new GraveDiggerModel(context.bakeLayer(GraveDiggerModel.LAYER)), 0.5f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public Identifier getTextureLocation(GraveDiggerRenderState state) {
		return TEXTURE;
	}

	@Override
	public GraveDiggerRenderState createRenderState() {
		return new GraveDiggerRenderState();
	}

	@Override
	public void extractRenderState(GraveDigger digger, GraveDiggerRenderState state, float partialTicks) {
		super.extractRenderState(digger, state, partialTicks);
		state.attackAnim = digger.getSwingAnimation(partialTicks);
		state.aggressive = digger.isAggressive();
		state.dig = digger.digMode();
	}
}
