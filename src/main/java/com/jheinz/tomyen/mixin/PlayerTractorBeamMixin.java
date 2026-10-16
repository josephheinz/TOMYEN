package com.jheinz.tomyen.mixin;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.alien.TractorBeam;

/** Caught in a tractor beam, sneaking doesn't get a player out of it. */
@Mixin(Player.class)
public abstract class PlayerTractorBeamMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void tomyen$heldByTractorBeam(CallbackInfoReturnable<Boolean> cir) {
		if (((Player) (Object) this).getVehicle() instanceof TractorBeam) {
			cir.setReturnValue(false);
		}
	}
}
