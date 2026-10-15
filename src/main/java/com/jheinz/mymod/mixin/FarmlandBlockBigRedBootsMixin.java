package com.jheinz.mymod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.mymod.bigredboots.BigRedBoots;

/** Big Red Boots spread the wearer's weight: landing on farmland in them never tramples it back to dirt. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockBigRedBootsMixin {
	@WrapOperation(method = "fallOn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToBaseBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
	private void mymod$bigRedBootsDontTrample(FarmlandBlock block, Entity entity, BlockState state, Level level, BlockPos pos,
			Operation<Void> original) {
		if (!BigRedBoots.isWearing(entity)) {
			original.call(block, entity, state, level, pos);
		}
	}
}
