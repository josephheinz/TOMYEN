package com.jheinz.tomyen.scarecrow.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.jheinz.tomyen.scarecrow.CropGuard;

/** Mobs don't trample farmland a scarecrow watches over (see {@link CropGuard}); players still can. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockScarecrowMixin {
	@WrapOperation(method = "fallOn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToBaseBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
	private void tomyen$scarecrowGuardsFarmland(FarmlandBlock block, Entity entity, BlockState state, Level level, BlockPos pos,
			Operation<Void> original) {
		if (entity instanceof Player || !CropGuard.isGuarded(level, pos)) {
			original.call(block, entity, state, level, pos);
		}
	}
}
