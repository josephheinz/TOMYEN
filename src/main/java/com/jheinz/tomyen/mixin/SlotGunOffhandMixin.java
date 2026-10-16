package com.jheinz.tomyen.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.GunOffhand;

/** The off-hand slot won't take a gun. */
@Mixin(Slot.class)
public abstract class SlotGunOffhandMixin {
	@Shadow
	@Final
	public Container container;

	@Shadow
	public abstract int getContainerSlot();

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void tomyen$refuseGunInOffhand(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (container instanceof Inventory && getContainerSlot() == Inventory.SLOT_OFFHAND && GunOffhand.isGun(stack)) {
			cir.setReturnValue(false);
		}
	}
}
