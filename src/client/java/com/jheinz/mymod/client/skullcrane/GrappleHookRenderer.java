package com.jheinz.mymod.client.skullcrane;

import java.util.ArrayList;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.skullcrane.GrappleHook;

/**
 * The Grapple Claw's claw, drawn as the item, with its cable run back to the hand of the player who fired it: drawn
 * the way vanilla draws a leash, pulled taut. It's drawn even with the claw itself off screen, so the cable doesn't
 * vanish when the player looks away from the claw.
 */
public class GrappleHookRenderer extends ThrownItemRenderer<GrappleHook> {
	public GrappleHookRenderer(EntityRendererProvider.Context context) {
		super(context, 0.9f, false);
	}

	@Override
	protected boolean affectedByCulling(GrappleHook hook) {
		return false;
	}

	@Override
	public void extractRenderState(GrappleHook hook, ThrownItemRenderState state, float partialTicks) {
		super.extractRenderState(hook, state, partialTicks);
		Player player = hook.player();
		if (player == null) {
			state.leashStates = null;
			return;
		}
		if (state.leashStates == null || state.leashStates.size() != 1) {
			state.leashStates = new ArrayList<>(1);
			state.leashStates.add(new EntityRenderState.LeashState());
		}
		EntityRenderState.LeashState cable = state.leashStates.getFirst();
		Vec3 claw = hook.getPosition(partialTicks);
		BlockPos clawPos = BlockPos.containing(claw);
		BlockPos handPos = BlockPos.containing(player.getEyePosition(partialTicks));
		cable.offset = Vec3.ZERO;
		cable.start = claw;
		cable.end = player.getRopeHoldPosition(partialTicks);
		cable.startBlockLight = hook.level().getBrightness(LightLayer.BLOCK, clawPos);
		cable.endBlockLight = hook.level().getBrightness(LightLayer.BLOCK, handPos);
		cable.startSkyLight = hook.level().getBrightness(LightLayer.SKY, clawPos);
		cable.endSkyLight = hook.level().getBrightness(LightLayer.SKY, handPos);
		cable.slack = false;
	}
}
