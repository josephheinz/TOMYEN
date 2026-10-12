package com.jheinz.mymod.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.neuralink.NeuralHijack;

/** A mob under Neural Hijack won't take its owner (or the owner's other allies) as a target, whatever its AI wants. */
@Mixin(Mob.class)
public abstract class MobHijackMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mymod$spareOwner(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		// Hijacks only exist on the server (whose thread owns the list).
		if (target != null && !self.level().isClientSide() && NeuralHijack.isFriendly(self, target)) {
			ci.cancel();
		}
	}
}
