package com.jheinz.mymod.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * Keeps an exoskeleton's covered armor slots clear. {@code isEquippableInSlot} is what armor slots in menus
 * (clicks, shift-clicks, hotbar swaps), equipping onto another entity and mob pickups all ask; dispensers ask
 * {@code canEquipWithDispenser}. Right-click equipping is handled in EquippableExoskeletonMixin.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityExoskeletonMixin {
	@Inject(method = "isEquippableInSlot", at = @At("RETURN"), cancellable = true)
	private void mymod$exoskeletonSlots(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && ExoskeletonItem.refusal((LivingEntity) (Object) this, stack, slot) != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canEquipWithDispenser", at = @At("RETURN"), cancellable = true)
	private void mymod$exoskeletonDispenser(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (cir.getReturnValueZ() && equippable != null
				&& ExoskeletonItem.refusal((LivingEntity) (Object) this, stack, equippable.slot()) != null) {
			cir.setReturnValue(false);
		}
	}
}
