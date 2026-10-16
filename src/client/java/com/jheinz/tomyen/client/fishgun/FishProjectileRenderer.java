package com.jheinz.tomyen.client.fishgun;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.client.fishing.FishMountRenderer;
import com.jheinz.tomyen.client.fishing.MountedFishModels;
import com.jheinz.tomyen.fishgun.FishAmmo;
import com.jheinz.tomyen.fishgun.FishProjectile;
import com.jheinz.tomyen.fishing.fish.Fishes;

/**
 * A fish in flight: its own 3D model (the living fish's, as a Fish Mount draws it), at the length a Fish Mount would show
 * a fish of that weight, head first along its flight and tumbling end over end, the lighter the faster. A swordfish
 * flies true, bill first, only rolling. A fish without a model is drawn as its item, spinning.
 */
public class FishProjectileRenderer extends EntityRenderer<FishProjectile, FishProjectileRenderer.State> {
	public static class State extends EntityRenderState {
		MountedFishModels.@Nullable MountedFish model;
		final ItemStackRenderState item = new ItemStackRenderState();
		float length;
		float yaw;
		float pitch;
		float spin;
		boolean rolls;
	}

	private final Map<Item, MountedFishModels.MountedFish> models;
	private final ItemModelResolver itemModelResolver;

	public FishProjectileRenderer(EntityRendererProvider.Context context) {
		super(context);
		models = MountedFishModels.bake(context::bakeLayer);
		itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FishProjectile fish, State state, float partialTicks) {
		super.extractRenderState(fish, state, partialTicks);
		ItemStack stack = fish.getItem();
		state.model = models.get(stack.getItem());
		state.item.clear();
		if (state.model == null) {
			itemModelResolver.updateForNonLiving(state.item, stack, ItemDisplayContext.GROUND, fish);
		}
		state.length = FishMountRenderer.length(fish.average(), fish.weight());
		Vec3 velocity = fish.getDeltaMovement();
		if (velocity.lengthSqr() > 1.0e-6) {
			state.yaw = (float) Math.toDegrees(Mth.atan2(velocity.x, velocity.z));
			state.pitch = (float) Math.toDegrees(Mth.atan2(velocity.y, velocity.horizontalDistance()));
		} else {
			state.yaw = fish.getViewYRot(partialTicks);
			state.pitch = fish.getViewXRot(partialTicks);
		}
		state.rolls = stack.is(Fishes.SWORDFISH);
		float rate = 30.0f - 24.0f * FishAmmo.heft(fish.weight());
		state.spin = (fish.tickCount + partialTicks) * rate;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.22f, 0.0f);
		// Head along the flight (-z after these, as the rocket renderer does it)...
		poseStack.rotateDegrees(Axis.YP, state.yaw + 180.0f);
		poseStack.rotateDegrees(Axis.XP, state.pitch);
		if (state.model != null) {
			// ...tumbling end over end about its flanks (a swordfish rolls about its bill instead)...
			poseStack.rotateDegrees(state.rolls ? Axis.ZP : Axis.XP, state.spin);
			// ...the mounted model's head (-x) turned to -z.
			poseStack.rotateDegrees(Axis.YP, -90.0f);
			state.model.submit(poseStack, collector, state.lightCoords, state.length);
		} else {
			poseStack.rotateDegrees(Axis.XP, state.spin);
			float s = state.length * 1.4f;
			poseStack.scale(s, s, s);
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
