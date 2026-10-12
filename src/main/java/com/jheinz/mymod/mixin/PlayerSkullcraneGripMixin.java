package com.jheinz.mymod.mixin;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.skullcrane.SkullcraneGrip;

/** Carried off by skullcranes, sneaking doesn't get a player loose: only the skulls letting go does. */
@Mixin(Player.class)
public abstract class PlayerSkullcraneGripMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void mymod$heldBySkullcranes(CallbackInfoReturnable<Boolean> cir) {
		if (((Player) (Object) this).getVehicle() instanceof SkullcraneGrip) {
			cir.setReturnValue(false);
		}
	}
}
