package com.jheinz.mymod.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.dragonscale.EnderDragonScales;

/** At the end of the Ender Dragon's death animation, as it's removed, it drops its scales (see EnderDragonScales). */
@Mixin(EnderDragon.class)
public abstract class EnderDragonScalesMixin {
	@Inject(method = "tickDeath", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
	private void mymod$dropScales(CallbackInfo ci) {
		EnderDragon dragon = (EnderDragon) (Object) this;
		if (dragon.level() instanceof ServerLevel level) {
			EnderDragonScales.drop(dragon, level);
		}
	}
}
