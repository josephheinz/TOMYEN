package com.jheinz.tomyen.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.GunOffhand;

/** The swap-hands key does nothing while holding a gun, since it would put the gun in the off hand. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerSwapHandsMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerAction", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
	private void tomyen$keepGunInMainHand(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND && GunOffhand.isGun(player.getMainHandItem())) {
			ci.cancel();
		}
	}
}
