package com.jheinz.mymod.mixin;

import java.util.function.Predicate;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.mymod.exoskeleton.ExoSockets;

/** A carved pumpkin socketed in an exoskeleton hides the wearer's stare from endermen, as a worn one does. */
@Mixin(Enderman.class)
public abstract class EndermanExoSocketsMixin {
	@WrapOperation(method = "isBeingStaredBy", at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean mymod$socketedDisguise(Predicate<LivingEntity> notDisguised, Object player, Operation<Boolean> original) {
		return original.call(notDisguised, player)
				&& !(player instanceof LivingEntity entity && ExoSockets.worn(entity, EquipmentSlot.HEAD).is(ItemTags.GAZE_DISGUISE_EQUIPMENT));
	}
}
