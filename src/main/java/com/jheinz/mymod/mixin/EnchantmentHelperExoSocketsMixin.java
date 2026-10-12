package com.jheinz.mymod.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * Wherever vanilla runs the enchantments of a worn item, a worn exoskeleton's run those of its socketed armor
 * too, each in its own slot: damage protection and immunity, Thorns, Frost Walker, Soul Speed and anything else
 * that goes through worn equipment. Starting and stopping location effects goes through here as well.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperExoSocketsMixin {
	@Inject(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
			at = @At("RETURN"))
	private static void mymod$socketedEnchantments(ItemStack piece, EquipmentSlot slot, LivingEntity owner,
			EnchantmentHelper.EnchantmentInSlotVisitor method, CallbackInfo ci) {
		if (slot == EquipmentSlot.CHEST && piece.getItem() instanceof ExoskeletonItem) {
			ExoSockets.runEnchantments(piece, owner, method);
		}
	}
}
