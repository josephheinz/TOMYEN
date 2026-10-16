package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.tank.Tank;

/**
 * A tank's crew: sneaking doesn't drop them out of it (they hold sneak to climb out, which the tank handles), and
 * nothing hurts them while they're inside it, the tank takes the hits. Only what bypasses invulnerability
 * (the void, /kill) still reaches them.
 */
@Mixin(Player.class)
public abstract class PlayerTankMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void tomyen$stayInTank(CallbackInfoReturnable<Boolean> cir) {
		if (((Player) (Object) this).getVehicle() instanceof Tank) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void tomyen$armouredCrew(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;
		if (player.getVehicle() instanceof Tank tank && tank.isSeated(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			cir.setReturnValue(false);
		}
	}
}
