package com.jheinz.tomyen.client.werewolf;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.werewolf.Werewolf;

/** Draws the werewolf, with its amber eyes glowing in the dark. */
public class WerewolfRenderer extends MobRenderer<Werewolf, WerewolfRenderState, WerewolfModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/werewolf/werewolf.png");
	private static final RenderType EYES = RenderTypes.eyes(MyMod.id("textures/entity/werewolf/werewolf_eyes.png"));

	public WerewolfRenderer(EntityRendererProvider.Context context) {
		super(context, new WerewolfModel(context.bakeLayer(WerewolfModel.LAYER)), 0.6f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES;
			}
		});
	}

	@Override
	public Identifier getTextureLocation(WerewolfRenderState state) {
		return TEXTURE;
	}

	@Override
	public WerewolfRenderState createRenderState() {
		return new WerewolfRenderState();
	}

	@Override
	public void extractRenderState(Werewolf werewolf, WerewolfRenderState state, float partialTicks) {
		super.extractRenderState(werewolf, state, partialTicks);
		state.attackAnim = werewolf.getSwingAnimation(partialTicks);
		state.leftSwipe = WerewolfModel.leftSwipe(state.ageInTicks, state.attackAnim);
		state.howl = werewolf.howlAmount(partialTicks);
		state.aggressive = werewolf.isAggressive();
	}
}
