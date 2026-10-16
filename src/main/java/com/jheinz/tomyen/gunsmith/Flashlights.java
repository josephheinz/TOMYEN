package com.jheinz.tomyen.gunsmith;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.attachment.Attachments;

/**
 * A gun with a flashlight lights up whatever it's pointed at while it's held: a light block rides the spot where the beam lands
 * (see {@link LightSpots}), out to {@value #RANGE} blocks, through water as well as air.
 */
final class Flashlights {
	private static final double RANGE = 28.0;
	private static final int BRIGHTNESS = 14;
	/** How often the spot follows the aim (ticks). */
	private static final int INTERVAL = 2;

	private Flashlights() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL == 0) {
				server.getPlayerList().getPlayers().forEach(Flashlights::tick);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				LightSpots.move(handler.getPlayer().level(), handler.getPlayer().getUUID(), null, 0));
		ServerLifecycleEvents.SERVER_STOPPING.register(LightSpots::clearAll);
	}

	private static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		ItemStack held = player.getMainHandItem();
		boolean on = !player.isSpectator() && held.getItem() instanceof GunItem && Attachments.has(held, Gunsmith.FLASHLIGHT);
		LightSpots.move(level, player.getUUID(), on ? spot(player) : null, BRIGHTNESS);
	}

	/** Where the beam lands: just short of the first block in its way, or at its full reach. */
	private static BlockPos spot(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(RANGE));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(0.4));
		return BlockPos.containing(at);
	}
}
