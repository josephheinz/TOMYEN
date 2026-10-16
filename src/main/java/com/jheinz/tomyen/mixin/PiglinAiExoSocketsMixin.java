package com.jheinz.tomyen.mixin;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.exoskeleton.ExoSockets;

/** Piglins leave alone someone whose exoskeleton has gold armor socketed, as they do anyone wearing it. */
@Mixin(PiglinAi.class)
public abstract class PiglinAiExoSocketsMixin {
	@Inject(method = "isWearingSafeArmor", at = @At("RETURN"), cancellable = true)
	private static void tomyen$socketedGold(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && ExoSockets.wears(entity, stack -> stack.is(ItemTags.PIGLIN_SAFE_ARMOR))) {
			cir.setReturnValue(true);
		}
	}
}
