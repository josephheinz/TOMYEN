package com.jheinz.mymod.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.soul.SoulSteel;

/** Weaker monsters won't take someone in full soul steel as their target unless that someone hurt them first. */
@Mixin(Mob.class)
public abstract class MobSoulSteelMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mymod$soulSteelDread(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && SoulSteel.dreads(self, target)) {
			SoulSteel.balk(self, target);
			ci.cancel();
		}
	}
}
