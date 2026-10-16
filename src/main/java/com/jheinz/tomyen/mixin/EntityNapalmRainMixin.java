package com.jheinz.tomyen.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.tomyen.ModEffects;

/** Rain puts out burning things, but not burning napalm: it's water you have to get into. */
@Mixin(Entity.class)
public abstract class EntityNapalmRainMixin {
	@WrapOperation(method = "applyEffectsFromBlocks(Ljava/util/List;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isInRain()Z"))
	private boolean tomyen$napalmIgnoresRain(Entity entity, Operation<Boolean> original) {
		return original.call(entity) && !(entity instanceof LivingEntity living && living.hasEffect(ModEffects.NAPALM));
	}
}
