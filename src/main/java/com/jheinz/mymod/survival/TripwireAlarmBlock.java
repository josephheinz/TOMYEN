package com.jheinz.mymod.survival;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.mymod.alien.Alien;
import com.jheinz.mymod.alien.Ufo;
import com.jheinz.mymod.holy.HolyLight;

/**
 * A Tripwire Alarm: a little brass bell on a post over a box of scrap electronics. It rings, loud enough to hear
 * {@value #ALERT_RANGE} blocks off, and every player that close is told what set it off and where, on their action bar:
 * <ul>
 * <li>when a hostile mob, a machine, an alien or anything evil comes within {@value #SENSE_RADIUS} blocks of it (it
 * listens every {@value #SCAN_TICKS} ticks);</li>
 * <li>when a UFO passes within {@value #UFO_RADIUS} blocks overhead;</li>
 * <li>when it's given a redstone signal, so a tripwire hook beside it makes a tripwire across a path ring it for
 * anything that crosses.</li>
 * </ul>
 * After ringing it rests {@value #COOLDOWN_STEPS} scans before it'll ring again for a lurker; its lamp glows while it
 * rings.
 */
public class TripwireAlarmBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty RINGING = BooleanProperty.create("ringing");
	public static final int COOLDOWN_STEPS = 8;
	public static final IntegerProperty COOLDOWN = IntegerProperty.create("cooldown", 0, COOLDOWN_STEPS);
	public static final int SCAN_TICKS = 10;
	public static final int SENSE_RADIUS = 6;
	public static final int UFO_RADIUS = 24;
	public static final int ALERT_RANGE = 64;
	/** Scans it shows as ringing (lamp lit, clapper swung) after going off. */
	private static final int RINGING_STEPS = 3;

	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 13.0, 13.0);

	public TripwireAlarmBlock(Properties properties) {
		super(properties.lightLevel(state -> state.getValue(RINGING) ? 7 : 0));
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(RINGING, false)
				.setValue(COOLDOWN, 0));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			level.scheduleTick(pos, this, SCAN_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int cooldown = Math.max(0, state.getValue(COOLDOWN) - 1);
		BlockState next = state;
		if (cooldown == 0) {
			Mob threat = findThreat(level, pos);
			if (threat != null) {
				ring(level, pos, threat.getDisplayName().copy().withStyle(threat instanceof Ufo ? ChatFormatting.LIGHT_PURPLE
						: HolyLight.isEvil(threat) ? ChatFormatting.GOLD : ChatFormatting.RED));
				cooldown = COOLDOWN_STEPS;
			}
		}
		next = next.setValue(COOLDOWN, cooldown).setValue(RINGING, cooldown > COOLDOWN_STEPS - RINGING_STEPS);
		if (next != state) {
			level.setBlock(pos, next, Block.UPDATE_CLIENTS);
		}
		level.scheduleTick(pos, this, SCAN_TICKS);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
			boolean movedByPiston) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		boolean powered = level.hasNeighborSignal(pos);
		if (powered == state.getValue(POWERED)) {
			return;
		}
		BlockState next = state.setValue(POWERED, powered);
		if (powered) {
			ring(server, pos, Component.translatable("block.mymod.tripwire_alarm.tripwire").withStyle(ChatFormatting.YELLOW));
			next = next.setValue(COOLDOWN, COOLDOWN_STEPS).setValue(RINGING, true);
		}
		level.setBlock(pos, next, Block.UPDATE_CLIENTS);
	}

	/** The most alarming thing in earshot: a UFO overhead, then the evil, then machines, then the nearest hostile. */
	private static @Nullable Mob findThreat(ServerLevel level, BlockPos pos) {
		AABB near = new AABB(pos).inflate(SENSE_RADIUS, 3, SENSE_RADIUS);
		List<Ufo> ufos = level.getEntitiesOfClass(Ufo.class, new AABB(pos).inflate(UFO_RADIUS, 48, UFO_RADIUS), Mob::isAlive);
		if (!ufos.isEmpty()) {
			return ufos.getFirst();
		}
		List<Mob> threats = level.getEntitiesOfClass(Mob.class, near, mob -> mob.isAlive() && isThreat(mob));
		return threats.stream().min(Comparator.<Mob>comparingInt(mob -> HolyLight.isEvil(mob) ? 0 : mob.is(Survival.MACHINES) ? 1 : 2)
				.thenComparingDouble(mob -> mob.distanceToSqr(Vec3.atCenterOf(pos)))).orElse(null);
	}

	private static boolean isThreat(Mob mob) {
		return mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER || mob instanceof Alien || mob.is(Survival.MACHINES)
				|| HolyLight.isEvil(mob);
	}

	/** Rings the bell and tells every player in range what set it off and where. */
	private static void ring(ServerLevel level, BlockPos pos, Component cause) {
		level.playSound(null, pos, Survival.ALARM_RING, SoundSource.BLOCKS, 3.0f, 1.0f);
		level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		Component message = Component.translatable("block.mymod.tripwire_alarm.alert", cause, pos.getX(), pos.getY(), pos.getZ());
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= ALERT_RANGE * ALERT_RANGE) {
				player.sendOverlayMessage(message);
			}
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return canSupportCenter(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
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
		builder.add(FACING, POWERED, RINGING, COOLDOWN);
	}
}
