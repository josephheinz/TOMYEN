package com.jheinz.mymod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.longsword.LongSwords;

/**
 * Long swords. Their sweep catches mobs further around the target (1.5 blocks instead of 1) and further from the
 * player (4 blocks instead of 3, to go with their extra reach). And they're two-handed: an off-hand use already under
 * way when one is drawn (a raised shield, a drawn bow) is dropped, on both sides so the client doesn't keep showing it.
 */
@Mixin(Player.class)
public abstract class PlayerLongSwordMixin {
	@WrapOperation(method = "doSweepAttack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
	private AABB mymod$widenLongSwordSweep(AABB box, double x, double y, double z, Operation<AABB> original) {
		if (mymod$holdingLongSword()) {
			return original.call(box, LongSwords.SWEEP_INFLATE, y, LongSwords.SWEEP_INFLATE);
		}
		return original.call(box, x, y, z);
	}

	@ModifyConstant(method = "doSweepAttack", constant = @Constant(doubleValue = 9.0))
	private double mymod$lengthenLongSwordSweep(double rangeSqr) {
		return mymod$holdingLongSword() ? LongSwords.SWEEP_RANGE * LongSwords.SWEEP_RANGE : rangeSqr;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void mymod$releaseOffHandForLongSword(CallbackInfo ci) {
		Player player = (Player) (Object) this;
		if (player.isUsingItem() && LongSwords.offHandBusy(player, player.getUsedItemHand())) {
			player.stopUsingItem();
		}
	}

	private boolean mymod$holdingLongSword() {
		return LongSwords.isLongSword(((Player) (Object) this).getMainHandItem());
	}
}
