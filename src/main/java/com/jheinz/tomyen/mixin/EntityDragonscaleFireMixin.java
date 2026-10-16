package com.jheinz.tomyen.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.dragonscale.DragonscaleArmor;

/**
 * A full set of dragonscale (worn or socketed) makes its wearer fire-immune the way a blaze is: vanilla then keeps
 * them from catching fire, drawing flames on them, and taking any fire damage (fire, lava, magma blocks). Both sides
 * ask, so the client doesn't show them burning either.
 */
@Mixin(Entity.class)
public abstract class EntityDragonscaleFireMixin {
	@Inject(method = "fireImmune", at = @At("RETURN"), cancellable = true)
	private void tomyen$dragonscaleFireImmune(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && (Object) this instanceof LivingEntity living && DragonscaleArmor.hasFullSet(living)) {
			cir.setReturnValue(true);
		}
	}
}
