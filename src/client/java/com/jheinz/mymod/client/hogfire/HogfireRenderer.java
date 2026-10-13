package com.jheinz.mymod.client.hogfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.hogfire.Hogfire;
import com.jheinz.mymod.hogfire.HogfireFlight;
import com.jheinz.mymod.hogfire.Hogfires;

/**
 * A Hogfire is drawn as the pig it is (variant and all), wearing its {@link HogfireGearLayer gear} (and gun mount),
 * and tilted to its flight attitude: pitched with its nose and rolled into its turns about the middle of its body.
 */
public class HogfireRenderer extends PigRenderer {
	/** Height of the middle of the body, which it pitches and rolls about. */
	private static final float BODY_CENTRE = (float) HogfireFlight.BODY_CENTRE;

	private ItemStack mountStack;

	public HogfireRenderer(EntityRendererProvider.Context context) {
		super(context);
		addLayer(new HogfireGearLayer(this, context));
	}

	@Override
	public PigRenderState createRenderState() {
		return new HogfireRenderState();
	}

	@Override
	public void extractRenderState(Pig pig, PigRenderState state, float partialTicks) {
		super.extractRenderState(pig, state, partialTicks);
		if (pig instanceof Hogfire hogfire && state instanceof HogfireRenderState hogState) {
			hogState.gear = hogfire.gear();
			hogState.flying = hogfire.pilot() != null;
			hogState.pitch = hogState.flying ? Mth.lerp(partialTicks, hogfire.xRotO, hogfire.getXRot()) : 0.0f;
			hogState.bank = hogfire.bank(partialTicks);
			hogState.engineRunning = hogfire.engineRunning();
			hogState.afterburner = hogfire.afterburner();
			hogState.throttle = hogfire.throttle();
			hogState.gunMount = hogfire.hasGunMount();
			if (hogState.gunMount) {
				if (mountStack == null) {
					mountStack = new ItemStack(Hogfires.GUN_MOUNT);
				}
				// Owned by the pig, so the mount's barrels turn with its gun.
				itemModelResolver.updateForLiving(hogState.mountedGun, mountStack, ItemDisplayContext.NONE, hogfire);
			} else {
				hogState.mountedGun.clear();
			}
			// Flying, it holds its head level with its body rather than looking about.
			if (hogState.flying) {
				state.xRot = 0.0f;
				state.yRot = 0.0f;
			}
		}
	}

	@Override
	protected void setupRotations(PigRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state instanceof HogfireRenderState hogState && (hogState.pitch != 0.0f || hogState.bank != 0.0f)) {
			poseStack.translate(0.0f, BODY_CENTRE, 0.0f);
			poseStack.rotate(Axis.XP.rotationDegrees(-hogState.pitch));
			poseStack.rotate(Axis.ZP.rotationDegrees(-hogState.bank));
			poseStack.translate(0.0f, -BODY_CENTRE, 0.0f);
		}
	}
}
