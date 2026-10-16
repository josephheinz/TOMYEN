package com.jheinz.tomyen.scarecrow.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.animal.rabbit.Rabbit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.tomyen.scarecrow.CropGuard;

/**
 * Rabbits near a scarecrow lose their appetite for the garden (see {@link CropGuard}): when the raiding goal checks
 * whether its rabbit wants more food (once each time it looks for a carrot), the answer is no.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal")
public abstract class RabbitRaidGardenScarecrowMixin {
	@WrapOperation(method = "canUse", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/animal/rabbit/Rabbit;wantsMoreFood()Z"))
	private boolean tomyen$scaredOffByScarecrow(Rabbit rabbit, Operation<Boolean> original) {
		return original.call(rabbit) && !CropGuard.isGuarded(rabbit.level(), rabbit.blockPosition());
	}
}
