package com.jheinz.mymod.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.meteorite.StarSteel;
import com.jheinz.mymod.meteorite.StarlightLensItem;

/** Star Steel tools mine faster by night, and a Starlight Lens zooms like a spyglass. Runs on both sides alike. */
@Mixin(Player.class)
public abstract class PlayerStarSteelMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void mymod$starSteelAtNight(BlockState state, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(StarSteel.destroySpeed((Player) (Object) this, state, cir.getReturnValueF()));
	}

	@Inject(method = "isScoping", at = @At("RETURN"), cancellable = true)
	private void mymod$starlightLensScopes(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && StarlightLensItem.isScoping((Player) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
