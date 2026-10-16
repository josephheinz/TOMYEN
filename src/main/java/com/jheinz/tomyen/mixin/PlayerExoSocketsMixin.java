package com.jheinz.tomyen.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.exoskeleton.ExoSockets;

/** A socketed piece counts as equipped when vanilla asks for a worn item: a turtle shell's water breathing. */
@Mixin(Player.class)
public abstract class PlayerExoSocketsMixin {
	@Inject(method = "isEquipped", at = @At("RETURN"), cancellable = true)
	private void tomyen$socketedEquipped(Item item, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && ExoSockets.wears((Player) (Object) this, stack -> stack.is(item))) {
			cir.setReturnValue(true);
		}
	}
}
