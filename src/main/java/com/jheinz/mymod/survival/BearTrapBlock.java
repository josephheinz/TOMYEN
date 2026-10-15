package com.jheinz.mymod.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.mymod.holy.HolyLight;
import com.jheinz.mymod.silver.EvilBane;

/**
 * A bear trap: set on the ground with its jaws open, it snaps shut on the first creature to step on its pan. The
 * victim takes {@value #DAMAGE} damage and is Trapped (can't walk or jump) for {@value #HOLD_TICKS} ticks, a player
 * {@value #PLAYER_HOLD_TICKS}; machines' legs ({@link Survival#MACHINES}) and the evil are pinned half as long again.
 * A player sneaking past feels for it and steps clear. Right-click a sprung trap to prise it open again.
 * <p>
 * The silver-toothed trap bites the evil like a silver blade ({@link EvilBane#SILVER}: twice the damage, and a
 * werewolf can't shrug it off).
 */
public class BearTrapBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final float DAMAGE = 4.0f;
	public static final int HOLD_TICKS = 100;
	public static final int PLAYER_HOLD_TICKS = 60;

	private static final VoxelShape OPEN_SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, 2.5, 15.5);
	private static final VoxelShape CLOSED_SHAPE_X = Block.box(0.5, 0.0, 5.5, 15.5, 7.5, 10.5);
	private static final VoxelShape CLOSED_SHAPE_Z = Block.box(5.5, 0.0, 0.5, 10.5, 7.5, 15.5);
	/** Only the pan and the space over it spring the trap, not the jaws' outer rim. */
	private static final VoxelShape TRIGGER = Block.box(3.0, 0.0, 3.0, 13.0, 4.0, 13.0);

	private final boolean silver;

	public BearTrapBlock(Properties properties, boolean silver) {
		super(properties);
		this.silver = silver;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, true));
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
			boolean isPrecise) {
		if (!state.getValue(OPEN) || !(level instanceof ServerLevel server) || !(entity instanceof LivingEntity victim) || !victim.isAlive()
				|| victim.isSpectator() || victim.getBbHeight() < 0.4f) {
			return;
		}
		if (victim instanceof Player player && (player.isShiftKeyDown() || player.isCreative())) {
			return;
		}
		snap(server, pos, state, victim);
	}

	private void snap(ServerLevel level, BlockPos pos, BlockState state, LivingEntity victim) {
		level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
		boolean tough = victim.is(Survival.MACHINES) || HolyLight.isEvil(victim);
		int hold = victim instanceof Player ? PLAYER_HOLD_TICKS : HOLD_TICKS;
		if (tough) {
			hold = hold * 3 / 2;
		}
		float damage = DAMAGE;
		if (silver) {
			if (victim instanceof EvilBane.Wary wary) {
				wary.struckBySilver();
			}
			if (HolyLight.isEvil(victim)) {
				damage *= EvilBane.SILVER.multiplier();
			}
		}
		victim.hurtServer(level, level.damageSources().source(Survival.BEAR_TRAP_DAMAGE), damage);
		victim.addEffect(new MobEffectInstance(Survival.TRAPPED, hold));
		victim.setDeltaMovement(0.0, Math.min(0.0, victim.getDeltaMovement().y), 0.0);
		victim.needsSync = true;
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.CRIT, x, pos.getY() + 0.4, z, 10, 0.25, 0.15, 0.25, 0.2);
		if (silver && HolyLight.isEvil(victim)) {
			level.sendParticles(ParticleTypes.END_ROD, x, pos.getY() + 0.5, z, 8, 0.2, 0.2, 0.2, 0.05);
		}
		level.playSound(null, pos, Survival.BEAR_TRAP_SNAP, SoundSource.BLOCKS, 1.2f, 0.9f + level.getRandom().nextFloat() * 0.2f);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPEN)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
			level.playSound(null, pos, Survival.BEAR_TRAP_SET, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		return InteractionResult.SUCCESS;
	}

	/** A set silver trap's teeth glint now and then. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (silver && state.getValue(OPEN) && random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.15,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(OPEN)) {
			return OPEN_SHAPE;
		}
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? CLOSED_SHAPE_Z : CLOSED_SHAPE_X;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return TRIGGER;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return canSupportRigidBlock(level, below) || canSupportCenter(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}
}
