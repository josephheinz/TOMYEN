package com.jheinz.mymod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * Right-click equipping (an armor piece used in hand, or sneak-using the exoskeleton) obeys the exoskeleton's
 * slot rules too, and says why it didn't work.
 */
@Mixin(Equippable.class)
public abstract class EquippableExoskeletonMixin {
	@Inject(method = "swapWithEquipmentSlot", at = @At("HEAD"), cancellable = true)
	private void mymod$exoskeletonSlots(ItemStack inHand, Player player, CallbackInfoReturnable<InteractionResult> cir) {
		Component refusal = ExoskeletonItem.refusal(player, inHand, ((Equippable) (Object) this).slot());
		if (refusal != null) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(refusal);
			}
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
