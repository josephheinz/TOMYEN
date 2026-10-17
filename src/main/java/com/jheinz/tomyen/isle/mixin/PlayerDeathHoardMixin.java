package com.jheinz.tomyen.isle.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.isle.DeathCapture;

/**
 * A dying player's inventory goes into their Isle of the Damned hoard instead of onto the ground (see DeathCapture).
 * Runs before vanilla drops it, so there's nothing left for vanilla to drop.
 */
@Mixin(Player.class)
public abstract class PlayerDeathHoardMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void tomyen$hoardOnDeath(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			DeathCapture.capture(player, level);
		}
	}
}
