package com.jheinz.tomyen.mixin;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.schnozzle.NoseHairWhipItem;

/**
 * Every swing reaches the server as a punch, hit or miss; a Nose Hair Whip cracks on a charged one. Runs before the
 * punch resets the attack charge.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPunchWhipMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void tomyen$crackWhip(ServerboundPunchPacket packet, CallbackInfo ci) {
		if (player.getMainHandItem().getItem() instanceof NoseHairWhipItem whip && !player.isSpectator()) {
			whip.swung(player.level(), player);
		}
	}
}
