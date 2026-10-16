package com.jheinz.tomyen.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.bigredboots.BigRedBoots;

/** Big Red Boots squeak now and then underfoot: hooked where every walking step sound starts, whoever overrides it. */
@Mixin(Entity.class)
public abstract class EntityBigRedBootsMixin {
	@Inject(method = "walkingStepSound", at = @At("HEAD"))
	private void tomyen$bigRedBootsSqueak(BlockPos pos, BlockState state, CallbackInfo ci) {
		BigRedBoots.step((Entity) (Object) this);
	}
}
