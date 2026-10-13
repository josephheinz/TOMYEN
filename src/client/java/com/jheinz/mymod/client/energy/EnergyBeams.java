package com.jheinz.mymod.client.energy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.client.BeamRibbon;
import com.jheinz.mymod.energy.EnergyBeam;

/**
 * Energy weapon beams: a wide coloured glow round a thin white-hot core, flashing on and fading out over the beam's
 * lifetime, the glow swelling a little as it goes. Our own shots in first person start where the gun sits on screen.
 */
public final class EnergyBeams {
	private record Beam(Vec3 from, Vec3 to, int color, float width, long startMillis, int millis) {
	}

	private static final List<Beam> BEAMS = new ArrayList<>();

	private EnergyBeams() {
	}

	public static void receive(EnergyBeam payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 from = payload.from();
		if (minecraft.player != null && payload.shooterId() == minecraft.player.getId() && minecraft.options.getCameraType().isFirstPerson()) {
			Vec3 look = minecraft.player.getViewVector(1.0f);
			float side = minecraft.player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
			Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize().scale(side);
			from = minecraft.player.getEyePosition().add(look.scale(1.0)).add(right.scale(0.2)).add(0.0, -0.2, 0.0);
		}
		BEAMS.add(new Beam(from, payload.to(), payload.color(), payload.width(), Util.getMillis(), Math.max(1, payload.millis())));
	}

	public static void submit(LevelRenderContext context) {
		long now = Util.getMillis();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
			Beam beam = it.next();
			float age = (now - beam.startMillis) / (float) beam.millis;
			if (age >= 1.0f) {
				it.remove();
				continue;
			}
			float fade = (1.0f - age) * (1.0f - age);
			float r = ARGB.red(beam.color) / 255.0f;
			float g = ARGB.green(beam.color) / 255.0f;
			float b = ARGB.blue(beam.color) / 255.0f;
			Vector3f a = beam.from.subtract(camera).toVector3f();
			Vector3f c = beam.to.subtract(camera).toVector3f();
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				BeamRibbon.draw(pose, buffer, a, c, beam.width * 0.5f * (1.0f + age * 0.6f), r, g, b, 0.5f * fade);
				BeamRibbon.draw(pose, buffer, a, c, beam.width * 0.12f, 0.85f + 0.15f * r, 0.85f + 0.15f * g, 0.85f + 0.15f * b, 0.95f * fade);
			});
		}
	}
}
