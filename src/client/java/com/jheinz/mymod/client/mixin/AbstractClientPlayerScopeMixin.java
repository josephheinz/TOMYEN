package com.jheinz.mymod.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.client.GunScope;

/** Zooms the view while looking through a gun's scope, the way the spyglass does. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerScopeMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void mymod$scopeZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		float zoom = GunScope.zoom((Player) (Object) this);
		if (firstPerson && zoom > 0.0f) {
			cir.setReturnValue(zoom);
		}
	}
}
