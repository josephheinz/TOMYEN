package com.jheinz.tomyen.fusion.melee.mixin;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.fusion.melee.MeleeSwings;

/** Every swing reaches the server as a punch, hit or miss: a swing ability fires on a charged one (MeleeSwings). */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPunchSwingMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void tomyen$swingAbility(ServerboundPunchPacket packet, CallbackInfo ci) {
		MeleeSwings.punched(player);
	}
}
