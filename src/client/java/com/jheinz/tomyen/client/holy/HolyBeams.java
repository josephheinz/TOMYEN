package com.jheinz.tomyen.client.holy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModNetworking;
import com.jheinz.tomyen.client.BeamRibbon;

/** Beams of light fired from crucifixes: a white-gold shaft that flashes bright and fades out over a moment. */
public final class HolyBeams {
	private static final long LIFETIME_MILLIS = 450;
	/** Half-widths in blocks of the glow and the core at full charge. */
	private static final float GLOW_WIDTH = 0.35f;
	private static final float CORE_WIDTH = 0.08f;

	private record Beam(Vec3 from, Vec3 to, float power, long startMillis) {
	}

	private static final List<Beam> BEAMS = new ArrayList<>();

	private HolyBeams() {
	}

	public static void receive(ModNetworking.LightBeam payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 from = payload.from();
		// Our own beam: start it where the crucifix sits on screen rather than where others see it held.
		if (minecraft.player != null && payload.shooterId() == minecraft.player.getId() && minecraft.options.getCameraType().isFirstPerson()) {
			Vec3 look = minecraft.player.getViewVector(1.0f);
			float side = minecraft.player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
			Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize().scale(side);
			from = minecraft.player.getEyePosition().add(look.scale(0.7)).add(right.scale(0.22)).add(0.0, -0.22, 0.0);
		}
		BEAMS.add(new Beam(from, payload.to(), payload.power(), Util.getMillis()));
	}

	public static void submit(LevelRenderContext context) {
		long now = Util.getMillis();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
			Beam beam = it.next();
			float age = (now - beam.startMillis) / (float) LIFETIME_MILLIS;
			if (age >= 1.0f) {
				it.remove();
				continue;
			}
			float fade = (1.0f - age) * (1.0f - age);
			float width = 0.5f + 0.5f * beam.power;
			Vector3f a = beam.from.subtract(camera).toVector3f();
			Vector3f b = beam.to.subtract(camera).toVector3f();
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				BeamRibbon.draw(pose, buffer, a, b, GLOW_WIDTH * width * (1.0f + age), 1.0f, 0.85f, 0.45f, 0.45f * fade);
				BeamRibbon.draw(pose, buffer, a, b, CORE_WIDTH * width, 1.0f, 1.0f, 0.9f, 0.95f * fade);
			});
		}
	}
}
