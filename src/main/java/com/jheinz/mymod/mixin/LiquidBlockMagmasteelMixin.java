package com.jheinz.mymod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.magmasteel.Magmasteel;

/**
 * Still lava is a floor for anyone in a full set of magmasteel: its surface holds them up the way a strider's does,
 * just above the lava so they don't stand in it. Flowing lava, and lava under more lava, still let them in.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMagmasteelMixin {
	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void mymod$magmasteelLavaFloor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		if (Magmasteel.isLavaFloor(state, level, pos, context)) {
			cir.setReturnValue(Magmasteel.LAVA_FLOOR);
		}
	}
}
