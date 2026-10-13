package com.jheinz.mymod.napalm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.mymod.ModEffects;

/**
 * A puddle of burning napalm gel, a pixel deep on solid ground. Anything that walks through it catches fire and the
 * Napalm effect for {@link #BURN_TICKS}. It glows, crackles and smokes, and burns out after {@link #AGE}'s six steps of
 * {@link #STEP_TICKS} to twice that (about 5 to 10 seconds); fresh napalm landing on it starts it over. Each step it may
 * light flammable blocks round it, and burning out on top of one leaves ordinary fire there, both only where napalm may
 * start fires ({@link NapalmFire#mayStartFires}). Rain doesn't put it out. Water washes it away (it's replaceable),
 * and a punch scrapes it up.
 */
public class BurningGelBlock extends Block {
	public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
	public static final int STEP_TICKS = 16;
	public static final int BURN_TICKS = 100;
	private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 1.0);

	public BurningGelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	/** Napalm landing on a puddle: it burns as long again from now. */
	static void refresh(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.getValue(AGE) > 0) {
			level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			level.scheduleTick(pos, this, STEP_TICKS + level.getRandom().nextInt(STEP_TICKS));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean fires = NapalmFire.mayStartFires(level, pos);
		int age = state.getValue(AGE);
		if (age >= BlockStateProperties.MAX_AGE_5) {
			BlockPos below = pos.below();
			if (fires && level.getBlockState(below).ignitedByLava()) {
				level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
			} else {
				level.removeBlock(pos, false);
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 1.6f + random.nextFloat() * 0.3f);
			}
			return;
		}
		level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, this, STEP_TICKS + random.nextInt(STEP_TICKS));
		if (fires) {
			// Like fire, it reaches out to anything that burns nearby.
			BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(2), random.nextInt(3) - 1);
			if (!target.equals(pos) && BaseFireBlock.canBePlacedAt(level, target, Direction.UP) && touchesFlammable(level, target)) {
				level.setBlockAndUpdate(target, BaseFireBlock.getState(level, target));
			}
		}
	}

	private static boolean touchesFlammable(Level level, BlockPos pos) {
		for (Direction side : Direction.values()) {
			if (level.getBlockState(pos.relative(side)).ignitedByLava()) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
			boolean isPrecise) {
		effectApplier.apply(InsideBlockEffectType.FIRE_IGNITE);
		effectApplier.runAfter(InsideBlockEffectType.FIRE_IGNITE, burning -> {
			if (!burning.level().isClientSide() && !burning.fireImmune() && !burning.isInWater()) {
				burning.igniteForTicks(BURN_TICKS);
				if (burning instanceof LivingEntity living) {
					living.addEffect(new MobEffectInstance(ModEffects.NAPALM, BURN_TICKS));
				}
			}
		});
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + random.nextDouble(), y = pos.getY() + 0.1, z = pos.getZ() + random.nextDouble();
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.FLAME, x, y + random.nextDouble() * 0.3, z, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 0.0, 0.03, 0.0);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
		}
		if (random.nextInt(20) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
					0.7f + random.nextFloat() * 0.4f, 0.6f + random.nextFloat() * 0.3f, false);
		}
	}
}
