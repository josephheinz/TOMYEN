package com.jheinz.tomyen.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.GunOffhand;

/**
 * Pressing the swap-hands key over a slot in an inventory screen swaps that slot with the off hand
 * directly, without asking the off-hand slot; skip it when the slot holds a gun.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerSwapOffhandMixin {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

	@Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
	private void tomyen$refuseGunSwapToOffhand(int slotIndex, int buttonNum, ContainerInput input, Player player, CallbackInfo ci) {
		if (input == ContainerInput.SWAP && buttonNum == Inventory.SLOT_OFFHAND && slotIndex >= 0 && slotIndex < slots.size()
				&& GunOffhand.isGun(slots.get(slotIndex).getItem())) {
			ci.cancel();
		}
	}
}
