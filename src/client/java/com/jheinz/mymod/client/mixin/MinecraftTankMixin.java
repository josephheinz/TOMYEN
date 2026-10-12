package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.client.tank.TankClient;

/**
 * In a tank, attack and use are the main gun and the coax (see {@link TankClient}), so they never punch, mine,
 * use the held item or pick blocks; and the gunner's thermal sight lights up anything alive.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTankMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$noAttackInTank(CallbackInfoReturnable<Boolean> cir) {
		if (TankClient.crewTank() != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$noMiningInTank(boolean down, CallbackInfo ci) {
		if (TankClient.crewTank() != null) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void mymod$noUseInTank(CallbackInfo ci) {
		if (TankClient.crewTank() != null) {
			ci.cancel();
		}
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void mymod$noPickInTank(CallbackInfo ci) {
		if (TankClient.crewTank() != null) {
			ci.cancel();
		}
	}

	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void mymod$thermal(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && TankClient.thermalSees(entity)) {
			cir.setReturnValue(true);
		}
	}
}
