package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.mech.Mech;

/**
 * Aboard a Bulwark Walker, sneaking doesn't drop a player off it: its pilot holds sneak to climb out (which the
 * walker handles), and a player held in its fist can't wriggle free. The seated pilot can't be hurt either: the
 * walker takes the hits. Only what bypasses invulnerability (the void, /kill) still reaches them.
 */
@Mixin(Player.class)
public abstract class PlayerMechMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void tomyen$stayInMech(CallbackInfoReturnable<Boolean> cir) {
		if (((Player) (Object) this).getVehicle() instanceof Mech) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void tomyen$armouredPilot(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		Player player = (Player) (Object) this;
		if (player.getVehicle() instanceof Mech mech && mech.isSeated(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			cir.setReturnValue(false);
		}
	}
}
