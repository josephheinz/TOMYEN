package com.jheinz.tomyen.fishing.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.fishing.RodHook;
import com.jheinz.tomyen.fishing.RodItem;
import com.jheinz.tomyen.hag.HagsCurse;

/**
 * Teaches the vanilla bobber about mod rods ({@link RodHook}); vanilla bobbers are untouched but for the last:
 * <ul>
 * <li>it stays out while a mod rod (not only the vanilla one) is held;</li>
 * <li>its bite timers stop while a fight is on, so the fish stays hooked until the minigame ends;</li>
 * <li>a Magmasteel Rod's bobber treats lava as water: it floats on it and fish bite in it;</li>
 * <li>(any bobber) an angler under a Hag's Curse waits longer for each fish.</li>
 * </ul>
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow
	private int timeUntilLured;

	@Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
	private void tomyen$modRods(Player owner, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof RodHook hook) {
			boolean holding = owner.getMainHandItem().getItem() instanceof RodItem || owner.getOffhandItem().getItem() instanceof RodItem;
			boolean keep = owner.canInteractWithLevel() && holding && hook.distanceToSqr(owner) <= 1024.0;
			if (!keep) {
				hook.discard();
			}
			cir.setReturnValue(!keep);
		}
	}

	@Inject(method = "catchingFish", at = @At("HEAD"), cancellable = true)
	private void tomyen$holdDuringFight(BlockPos pos, CallbackInfo ci) {
		if ((Object) this instanceof RodHook hook && hook.isFighting()) {
			ci.cancel();
		}
	}

	/** A Hag's Curse on the angler: the next fish takes longer to come (any rod). */
	@Inject(method = "catchingFish", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/FishingHook;timeUntilLured:I",
			opcode = Opcodes.PUTFIELD, ordinal = 3, shift = At.Shift.AFTER))
	private void tomyen$hagsCurse(BlockPos pos, CallbackInfo ci) {
		timeUntilLured += HagsCurse.biteDelay(((FishingHook) (Object) this).getPlayerOwner());
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean tomyen$floatOnLava(FluidState fluid, TagKey<Fluid> tag, Operation<Boolean> original) {
		if (original.call(fluid, tag)) {
			return true;
		}
		return tag == FluidTags.WATER && (Object) this instanceof RodHook hook && hook.fishesLava() && fluid.is(FluidTags.LAVA);
	}
}
