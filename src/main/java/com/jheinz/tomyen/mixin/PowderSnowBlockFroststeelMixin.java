package com.jheinz.tomyen.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.PowderSnowBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.froststeel.Froststeel;

/** A full set of froststeel walks on powder snow as leather boots do, instead of sinking into it. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockFroststeelMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
	private static void tomyen$froststeelSnowshoes(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof LivingEntity living && Froststeel.coldProof(living)) {
			cir.setReturnValue(true);
		}
	}
}
