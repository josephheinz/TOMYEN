package com.jheinz.tomyen.client.hag;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.hag.BogHag;

/** Draws the Bog Hag, her eyes and her lantern's marsh-light glowing in the dark. */
public class BogHagRenderer extends MobRenderer<BogHag, BogHagRenderState, BogHagModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/bog_hag/bog_hag.png");
	private static final RenderType GLOW = RenderTypes.eyes(MyMod.id("textures/entity/bog_hag/bog_hag_glow.png"));

	public BogHagRenderer(EntityRendererProvider.Context context) {
		super(context, new BogHagModel(context.bakeLayer(BogHagModel.LAYER)), 0.45f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public Identifier getTextureLocation(BogHagRenderState state) {
		return TEXTURE;
	}

	@Override
	public BogHagRenderState createRenderState() {
		return new BogHagRenderState();
	}

	@Override
	public void extractRenderState(BogHag hag, BogHagRenderState state, float partialTicks) {
		super.extractRenderState(hag, state, partialTicks);
		state.attackAnim = hag.getSwingAnimation(partialTicks);
		state.spell = hag.spell();
		state.spellProgress = hag.spellProgress(partialTicks);
		state.aggressive = hag.isAggressive();
	}
}
