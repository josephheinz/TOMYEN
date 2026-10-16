package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.starforge.InfusionEffects;

/** Starlit tools dig faster by night; Soulbound gear is set aside before a dying player's inventory drops. */
@Mixin(Player.class)
public abstract class PlayerInfusionMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void tomyen$starlitDigging(BlockState state, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(InfusionEffects.destroySpeed((Player) (Object) this, state, cir.getReturnValueF()));
	}

	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void tomyen$keepSoulbound(ServerLevel level, CallbackInfo ci) {
		InfusionEffects.stashSoulbound((Player) (Object) this, level);
	}
}
