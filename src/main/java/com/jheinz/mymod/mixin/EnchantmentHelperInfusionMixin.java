package com.jheinz.mymod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.starforge.InfusionEffects;

/** Voidtouched gear keeps some of its durability, after Unbreaking has had its say. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperInfusionMixin {
	@Inject(method = "processDurabilityChange", at = @At("RETURN"), cancellable = true)
	private static void mymod$voidtouchedDurability(ServerLevel level, ItemStack stack, int amount, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(InfusionEffects.durabilityChange(stack, cir.getReturnValueI(), level.getRandom()));
	}
}
