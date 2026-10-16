package com.jheinz.tomyen.banana;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A banana peel lying on the ground, where it was put or thrown. Whatever walks onto it (a mob, an animal, a player)
 * slips (see {@link #slip}) and the peel is gone. Players sneaking step carefully over it. It needs something under it,
 * like a carpet, and breaks back into its item.
 */
public class BananaPeelBlock extends HorizontalDirectionalBlock {
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0);
	/** How fast a slip sends you skidding along (a sprint is about 0.28 a tick). */
	private static final double SLIDE_SPEED = 1.0;
	/** And a little hop, so feet leave the ground. */
	private static final double SLIDE_LIFT = 0.22;
	/** Then a moment to find your feet: Slowness II for this long. */
	private static final int DAZE_TICKS = 40;

	public BananaPeelBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return !level.isEmptyBlock(pos.below());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
			boolean isPrecise) {
		if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
				&& living.onGround() && !living.isSteppingCarefully() && !living.isPassenger()) {
			serverLevel.removeBlock(pos, false);
			slip(serverLevel, living, null);
		}
	}

	/**
	 * Slips: a skid along the way it was going (or, standing still, the way it faces) with a little hop, Slowness II
	 * for a couple of seconds, a squelchy skid and a spray of peel. Harmless. {@code direction}: which way to send it,
	 * or null for its own way.
	 */
	public static void slip(ServerLevel level, LivingEntity entity, @Nullable Vec3 direction) {
		Vec3 move = direction != null ? direction : entity.getKnownMovement();
		Vec3 flat = new Vec3(move.x, 0.0, move.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = Vec3.directionFromRotation(0.0f, entity.getYRot());
		}
		flat = flat.normalize();
		entity.setDeltaMovement(flat.x * SLIDE_SPEED, SLIDE_LIFT, flat.z * SLIDE_SPEED);
		entity.needsSync = true;
		entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DAZE_TICKS, 1));
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Bananas.PEEL_SLIP, SoundSource.NEUTRAL, 1.0f,
				0.9f + level.getRandom().nextFloat() * 0.25f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Bananas.BANANA_PEEL), entity.getX(), entity.getY() + 0.1, entity.getZ(),
				8, 0.2, 0.05, 0.2, 0.08);
	}
}
