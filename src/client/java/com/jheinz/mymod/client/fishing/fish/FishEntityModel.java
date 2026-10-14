package com.jheinz.mymod.client.fishing.fish;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

/**
 * Every living fish's model (the cubes are each fish's own, from {@link FishModelLayers}). It animates the parts it
 * finds by name, like vanilla's fish: the tail wags as the cod's does, or, where there's a chain of them (tail, tail2,
 * tail3: the eels, the gulper, the ghoulfish), each segment swings a little later than the one before so a wave runs
 * down the body. Out of water everything swings harder and faster, as the cod's tail does. Signature parts move on their
 * own: the catfish's whiskers sway, the anglerfish's lure bobs, the pixie minnow's wings flutter and the squidling's
 * arms and tentacles writhe. (The renderer adds the whole body's wag and lays the fish on its side on land.)
 */
public class FishEntityModel extends EntityModel<LivingEntityRenderState> {
	private final List<ModelPart> tail = new ArrayList<>();
	private final @Nullable ModelPart whiskerLeft;
	private final @Nullable ModelPart whiskerRight;
	private final @Nullable ModelPart lure;
	private final @Nullable ModelPart wingLeft;
	private final @Nullable ModelPart wingRight;
	private final List<ModelPart> tentacles = new ArrayList<>();

	public FishEntityModel(ModelPart root) {
		super(root);
		Function<String, @Nullable ModelPart> part = root.createPartLookup();
		for (String name : new String[] {"tail", "tail2", "tail3"}) {
			ModelPart p = part.apply(name);
			if (p != null) {
				tail.add(p);
			}
		}
		whiskerLeft = part.apply("whisker_left");
		whiskerRight = part.apply("whisker_right");
		lure = part.apply("lure");
		wingLeft = part.apply("wing_left");
		wingRight = part.apply("wing_right");
		for (int i = 0; ; i++) {
			ModelPart p = part.apply("tentacle_" + i);
			if (p == null) {
				break;
			}
			tentacles.add(p);
		}
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		float amplitude = state.isInWater ? 1.0f : 1.5f;
		float pace = state.isInWater ? 1.0f : 1.7f;
		float swim = 0.6f * pace * t;
		if (tail.size() == 1) {
			tail.get(0).yRot += -amplitude * 0.45f * Mth.sin(swim);
		} else {
			for (int k = 0; k < tail.size(); k++) {
				tail.get(k).yRot += -amplitude * (0.3f + 0.08f * k) * Mth.sin(swim - 0.8f * k);
			}
		}
		if (whiskerLeft != null && whiskerRight != null) {
			float sway = 0.14f * Mth.sin(0.13f * t);
			float droop = 0.08f * Mth.sin(0.21f * t + 1.0f);
			whiskerLeft.yRot += sway;
			whiskerLeft.zRot += droop;
			whiskerRight.yRot += sway;
			whiskerRight.zRot -= droop;
		}
		if (lure != null) {
			lure.xRot += 0.16f * Mth.sin(0.11f * t) + 0.05f * Mth.sin(0.37f * t);
		}
		if (wingLeft != null && wingRight != null) {
			// Wings hinge along the back; each lifts outward, toward its own side.
			float flap = 0.3f + 0.32f * Mth.sin(1.7f * t);
			wingLeft.zRot += wingLeft.x >= 0.0f ? flap : -flap;
			wingRight.zRot += wingRight.x >= 0.0f ? flap : -flap;
		}
		for (int k = 0; k < tentacles.size(); k++) {
			ModelPart p = tentacles.get(k);
			p.yRot += 0.22f * amplitude * Mth.sin(0.3f * pace * t + 1.1f * k);
			p.xRot += 0.12f * amplitude * Mth.sin(0.23f * pace * t + 0.7f * k);
		}
	}
}
