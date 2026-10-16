package com.jheinz.tomyen.client.powerarmor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.client.BeamRibbon;
import com.jheinz.tomyen.powerarmor.PowerArmorNetworking;

/**
 * Repulsor blasts and the unibeam: a pale blue-white bolt from a gauntlet port that's gone in a blink, and a thick white
 * shaft from the chest emitter that flares and fades over most of a second.
 */
public final class PowerArmorBeams {
	private static final long REPULSOR_MILLIS = 220;
	private static final long UNIBEAM_MILLIS = 700;

	private record Beam(Vec3 from, Vec3 to, boolean unibeam, long startMillis) {
	}

	private static final List<Beam> BEAMS = new ArrayList<>();

	private PowerArmorBeams() {
	}

	public static void receive(PowerArmorNetworking.Beam payload) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Vec3 from = payload.from();
		boolean unibeam = payload.kind() == PowerArmorNetworking.Beam.UNIBEAM;
		// Our own shot in first person: start it where the gauntlet or chest is on screen.
		if (player != null && payload.shooterId() == player.getId() && minecraft.options.getCameraType().isFirstPerson()) {
			Vec3 look = player.getViewVector(1.0f);
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
			if (unibeam) {
				from = player.getEyePosition().add(look.scale(0.6)).add(0.0, -0.5, 0.0);
			} else {
				double side = payload.kind() == PowerArmorNetworking.Beam.LEFT_REPULSOR ? -0.3 : 0.3;
				from = player.getEyePosition().add(look.scale(0.7)).add(right.scale(side)).add(0.0, -0.25, 0.0);
			}
		}
		BEAMS.add(new Beam(from, payload.to(), unibeam, Util.getMillis()));
	}

	public static void submit(LevelRenderContext context) {
		long now = Util.getMillis();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
			Beam beam = it.next();
			float age = (now - beam.startMillis) / (float) (beam.unibeam ? UNIBEAM_MILLIS : REPULSOR_MILLIS);
			if (age >= 1.0f) {
				it.remove();
				continue;
			}
			float fade = (1.0f - age) * (1.0f - age);
			Vector3f a = beam.from.subtract(camera).toVector3f();
			Vector3f b = beam.to.subtract(camera).toVector3f();
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.lightning(), (pose, buffer) -> {
				if (beam.unibeam) {
					float flare = age < 0.15f ? 1.0f + (0.15f - age) * 4.0f : 1.0f;
					BeamRibbon.draw(pose, buffer, a, b, 0.55f * flare, 0.55f, 0.85f, 1.0f, 0.45f * fade);
					BeamRibbon.draw(pose, buffer, a, b, 0.22f * flare, 0.85f, 0.97f, 1.0f, 0.8f * fade);
					BeamRibbon.draw(pose, buffer, a, b, 0.08f, 1.0f, 1.0f, 1.0f, fade);
				} else {
					BeamRibbon.draw(pose, buffer, a, b, 0.18f, 0.5f, 0.85f, 1.0f, 0.5f * fade);
					BeamRibbon.draw(pose, buffer, a, b, 0.05f, 0.95f, 1.0f, 1.0f, 0.95f * fade);
				}
			});
		}
	}
}
