package com.jheinz.tomyen.client.shrimp;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.jheinz.tomyen.client.GunDisplay;
import com.jheinz.tomyen.client.GunHands;
import com.jheinz.tomyen.client.shotgun.ShotgunAnimation;
import com.jheinz.tomyen.shrimp.Shrimps;

/**
 * The Shrimp with a Gun's renderer and model, and where things are on its Shrimp Pistol for the shared gun rendering
 * (handed to it through ShoddyGunClient, the pistol being a homemade gun like the pipe guns): its first-person display
 * transform, the hand holds and the parts its loading animates. All printed by tools/gen_shrimp.py; move them together
 * with the model.
 */
public final class ShrimpClient {
	/** Rounds go in through the gate on top of the slide; the hand reaches over and draws the slide back by its knob. */
	public static final ShotgunAnimation.Geometry PISTOL_GEOMETRY = new ShotgunAnimation.Geometry(
			new Vector3f(8.0f, 6.2f, 5.4f), new Vector3f(8.0f, 11.6f, 2.8f), new Vector3f(9.2f, 11.0f, 3.6f), 1.8f,
			new Vector3f(8.0f, 10.4f, 7.0f), true, new Vector3f(8.0f, 10.0f, -30.0f),
			ShotgunAnimation.Loading.BREECH, new Vector3f(12.0f, 10.0f, -20.0f), 0.0f, 140.0f);
	public static final GunDisplay PISTOL_DISPLAY = new GunDisplay(new Vector3f(-0.497f, 3.266f, -1.215f), -6, 0.65f);
	/** Like the other handguns: the off hand cups the shooting hand. */
	public static final GunHands.Anchors PISTOL_ANCHORS = new GunHands.Anchors(new Vector3f(-0.020f, 0.131f, -0.181f),
			new Vector3f(-0.090f, 0.101f, -0.151f));

	private ShrimpClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Shrimps.SHRIMP, ShrimpRenderer::new);
		ModelLayerRegistry.registerModelLayer(ShrimpModel.LAYER, ShrimpModel::createBodyLayer);
	}
}
