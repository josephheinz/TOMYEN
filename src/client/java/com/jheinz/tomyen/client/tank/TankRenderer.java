package com.jheinz.tomyen.client.tank;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.tank.Tank;

/**
 * Draws a tank. It doesn't tip over when it dies (it just burns), its long hull and gun keep it drawn while any
 * of it is on screen, and its own gunner doesn't see it from the sight (they'd be looking out from inside it).
 */
public class TankRenderer extends MobRenderer<Tank, TankRenderState, TankModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/tank/tank.png");

	public TankRenderer(EntityRendererProvider.Context context) {
		super(context, new TankModel(context.bakeLayer(TankModel.LAYER)), 2.4f);
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
	public boolean shouldRender(Tank tank, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		if (tank == TankClient.sightTank()) {
			return false;
		}
		return super.shouldRender(tank, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	protected AABB getBoundingBoxForCulling(Tank tank, float partialTicks) {
		return super.getBoundingBoxForCulling(tank, partialTicks).inflate(4.5, 1.0, 4.5);
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0f;
	}

	@Override
	public void extractRenderState(Tank tank, TankRenderState state, float partialTicks) {
		super.extractRenderState(tank, state, partialTicks);
		state.turretYaw = Mth.wrapDegrees(tank.turretYaw(partialTicks) - state.bodyRot);
		state.gunPitch = tank.gunPitch(partialTicks);
		state.recoil = tank.recoil(partialTicks);
		state.hatch = tank.hatchOpen(partialTicks);
		state.trackLeft = tank.trackTravel(true, partialTicks);
		state.trackRight = tank.trackTravel(false, partialTicks);
	}
}
