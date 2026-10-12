package com.jheinz.mymod.client.tank;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.tank.Tank;

public class TankRenderer extends MobRenderer<Tank, TankRenderState, TankModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/tank/tank.png");

	public TankRenderer(EntityRendererProvider.Context context) {
		super(context, new TankModel(context.bakeLayer(TankModel.LAYER)), 1.3f);
	}

	@Override
	public Identifier getTextureLocation(TankRenderState state) {
		return TEXTURE;
	}

	@Override
	public TankRenderState createRenderState() {
		return new TankRenderState();
	}

	@Override
	public void extractRenderState(Tank tank, TankRenderState state, float partialTicks) {
		super.extractRenderState(tank, state, partialTicks);
		state.recoil = tank.recoil(partialTicks);
	}
}
