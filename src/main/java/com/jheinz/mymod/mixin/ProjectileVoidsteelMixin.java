package com.jheinz.mymod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.voidsteel.Voidsteel;

/**
 * Projectiles that roll badly against someone in voidsteel armor pass straight through them, as if they weren't there
 * (every projectile's hit test comes through here, arrows' and fireballs' overrides included).
 */
@Mixin(Projectile.class)
public abstract class ProjectileVoidsteelMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void mymod$voidsteelPhase(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Voidsteel.phases((Projectile) (Object) this, entity)) {
			cir.setReturnValue(false);
		}
	}
}
