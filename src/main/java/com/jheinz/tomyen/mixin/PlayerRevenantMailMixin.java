package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.fusion.gear.RevenantMail;

/**
 * The Revenant Mail's death ward: a dying player whose mail keeps their gear drops nothing, as under keepInventory
 * (the respawn copies the inventory back; see {@link RevenantMail}).
 */
@Mixin(Player.class)
public abstract class PlayerRevenantMailMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
	private void tomyen$revenantKeepsGear(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && RevenantMail.tryKeepGear(player)) {
			ci.cancel();
		}
	}
}
