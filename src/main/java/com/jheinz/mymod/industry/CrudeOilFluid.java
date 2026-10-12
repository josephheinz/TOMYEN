package com.jheinz.mymod.industry;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Crude oil: black, thick and slow. It creeps out two blocks from a source and takes two seconds a step, never
 * forms new sources (a pocket can be drained), and wades like cobweb-thick mud. It burns: fire or lava touching
 * it lights it, and a lit block passes the flame to the oil around it a few ticks later, so a whole pool goes up.
 */
public abstract class CrudeOilFluid extends FlowingFluid {
	/** Ticks between flow steps: water is 5, lava 30. */
	private static final int TICK_DELAY = 40;
	/** How long a burning block takes to light each oil block next to it. */
	private static final int SPREAD_MIN_TICKS = 3;
	private static final int SPREAD_EXTRA_TICKS = 8;
	/** Wading through it: three fifths of the speed sideways, half vertically. */
	private static final Vec3 WADE = new Vec3(0.6, 0.5, 0.6);

	@Override
	public Fluid getFlowing() {
		return Industry.FLOWING_CRUDE_OIL;
	}

	@Override
	public Fluid getSource() {
		return Industry.CRUDE_OIL;
	}

	@Override
	public Item getBucket() {
		return Industry.CRUDE_OIL_BUCKET;
	}

	@Override
	public boolean isSame(Fluid other) {
		return other == Industry.CRUDE_OIL || other == Industry.FLOWING_CRUDE_OIL;
	}

	@Override
	protected boolean canConvertToSource(ServerLevel level) {
		return false;
	}

	@Override
	protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
		Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
	}

	@Override
	protected int getSlopeFindDistance(LevelReader level) {
		return 2;
	}

	@Override
	protected int getDropOff(LevelReader level) {
		return 3;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return TICK_DELAY;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
		return direction == Direction.DOWN && !isSame(other);
	}

	@Override
	protected float getExplosionResistance() {
		return 100.0f;
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		return Industry.CRUDE_OIL_BLOCK.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
	}

	@Override
	public Optional<SoundEvent> getPickupSound() {
		return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
	}

	@Override
	protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier) {
		entity.makeStuckInBlock(level.getBlockState(pos), WADE);
	}

	/** A slow glug of a bubble now and then on a still surface. */
	@Override
	public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
		if (state.isSource() && random.nextInt(160) == 0 && level.getBlockState(pos.above()).isAir()) {
			double x = pos.getX() + random.nextDouble(), y = pos.getY() + state.getHeight(level, pos), z = pos.getZ() + random.nextDouble();
			level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.01, 0.0);
			level.playLocalSound(x, y, z, SoundEvents.MUD_STEP, SoundSource.BLOCKS, 0.3f, 0.5f + random.nextFloat() * 0.2f, false);
		}
	}

	// --- Burning --------------------------------------------------------------------------------------------

	@Override
	protected boolean isRandomlyTicking() {
		return true;
	}

	/** Catches anything that lit beside it without a block update reaching it (lava flowing up to it, say). */
	@Override
	protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
		if (touchesFlame(level, pos)) {
			ignite(level, pos);
		}
	}

	/** Every flow tick (which any neighbour change schedules) first checks whether it's caught. */
	@Override
	public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
		if (touchesFlame(level, pos)) {
			ignite(level, pos);
			return;
		}
		super.tick(level, pos, blockState, fluidState);
	}

	private static boolean touchesFlame(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			if (!level.isLoaded(next)) {
				continue;
			}
			BlockState state = level.getBlockState(next);
			if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.MAGMA_BLOCK)
					|| state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	/** Burns this block of oil away into flame, and lines the oil around it up to catch next. */
	public static void ignite(ServerLevel level, BlockPos pos) {
		BlockState fire = BaseFireBlock.getState(level, pos);
		level.setBlockAndUpdate(pos, fire.canSurvive(level, pos) ? fire : Blocks.AIR.defaultBlockState());
		RandomSource random = level.getRandom();
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			FluidState fluid = level.getFluidState(next);
			if (fluid.getType() instanceof CrudeOilFluid) {
				level.scheduleTick(next, fluid.getType(), SPREAD_MIN_TICKS + random.nextInt(SPREAD_EXTRA_TICKS));
			} else if (direction != Direction.DOWN && level.getBlockState(next).isAir()) {
				BlockState flame = BaseFireBlock.getState(level, next);
				if (flame.canSurvive(level, next)) {
					level.setBlockAndUpdate(next, flame);
				}
			}
		}
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
		if (random.nextInt(3) == 0) {
			level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7f, 0.6f + random.nextFloat() * 0.3f);
		}
	}

	public static class Flowing extends CrudeOilFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState state) {
			return state.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState state) {
			return false;
		}
	}

	public static class Source extends CrudeOilFluid {
		@Override
		public int getAmount(FluidState state) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}
}
