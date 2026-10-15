package com.jheinz.mymod.banana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * A thrown Banana Peel. Hitting a creature, it slips it up (sending it skidding the way the peel was flying); landing on
 * top of a block, it lies there as a {@link BananaPeelBlock} trap if there's room. Otherwise it drops as an item.
 */
public class ThrownBananaPeel extends ThrowableItemProjectile {
	public ThrownBananaPeel(EntityType<? extends ThrownBananaPeel> type, Level level) {
		super(type, level);
	}

	public ThrownBananaPeel(Level level, LivingEntity thrower, ItemStack stack) {
		super(Bananas.THROWN_BANANA_PEEL, thrower, level, stack);
	}

	public ThrownBananaPeel(Level level, double x, double y, double z, ItemStack stack) {
		super(Bananas.THROWN_BANANA_PEEL, x, y, z, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Bananas.BANANA_PEEL;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (level() instanceof ServerLevel level) {
			if (result.getEntity() instanceof LivingEntity living) {
				BananaPeelBlock.slip(level, living, getDeltaMovement());
			} else {
				spawnAtLocation(level, new ItemStack(Bananas.BANANA_PEEL));
			}
			discard();
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}
		BlockPos pos = result.getBlockPos().relative(result.getDirection());
		BlockState peel = Bananas.BANANA_PEEL_BLOCK.defaultBlockState()
				.setValue(HorizontalDirectionalBlock.FACING, Direction.fromYRot(Math.toDegrees(Math.atan2(-getDeltaMovement().x, getDeltaMovement().z))));
		if (result.getDirection() == Direction.UP && level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
				&& peel.canSurvive(level, pos)) {
			level.setBlockAndUpdate(pos, peel);
			level.playSound(null, pos, Bananas.PEEL_PLACE, SoundSource.BLOCKS, 0.8f, 0.9f + random.nextFloat() * 0.2f);
		} else {
			spawnAtLocation(level, new ItemStack(Bananas.BANANA_PEEL));
		}
		discard();
	}
}
