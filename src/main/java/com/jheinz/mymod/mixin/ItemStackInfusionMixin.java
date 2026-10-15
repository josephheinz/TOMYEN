package com.jheinz.mymod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.starforge.Infusion;

/** An infused item's name takes its infusion's colour, wherever it's shown. */
@Mixin(ItemStack.class)
public abstract class ItemStackInfusionMixin {
	@Inject(method = "getStyledHoverName", at = @At("RETURN"), cancellable = true)
	private void mymod$infusionColour(CallbackInfoReturnable<Component> cir) {
		Infusion infusion = Infusion.of((ItemStack) (Object) this);
		if (infusion != null) {
			cir.setReturnValue(cir.getReturnValue().copy().withColor(infusion.color()));
		}
	}
}
