package com.jheinz.mymod.powerarmor;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

/**
 * The Power Armor Station: a gantry two blocks tall (its frame is drawn by the block model; it needs the space
 * above it clear to be placed) that holds a suit while it's built. It faces the player who placed it, and the
 * suit stands on its base plate looking out the same way.
 * <ul>
 * <li>Use it: the rack's interface, to fit pieces and a fusion core.
 * <li>Sneak-use it with empty hands, wearing an exoskeleton with no modules: climb into the suit on the rack.
 * <li>Use it while wearing power armor, with the rack empty: dock, climbing out and leaving the suit on it.
 * </ul>
 * Only the base plate and the two rear uprights are solid, so the wearer can stand in the frame. Breaking the
 * station drops everything on its rack.
 */
public class PowerArmorStationBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** Height of the base plate the suit stands on, in blocks. */
	public static final double BASE_HEIGHT = 3.0 / 16.0;

	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			// Built facing north (front at z = 0, uprights at the back), then turned.
			SHAPES.put(facing, Shapes.or(Block.box(0, 0, 0, 16, 3, 16),
					turned(facing, 0, 3, 13, 3, 16, 16), turned(facing, 13, 3, 13, 16, 16, 16)));
		}
	}

	public PowerArmorStationBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	private static VoxelShape turned(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
		return switch (facing) {
			case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
			case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.box(x0, y0, z0, x1, y1, z1);
		};
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PowerArmorStationBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/** Where a suit on the rack stands: the middle of the base plate. */
	public static Vec3 anchor(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + BASE_HEIGHT, pos.getZ() + 0.5);
	}

	/** The way a suit on the rack faces, as an entity's yaw. */
	public static float yaw(BlockState state) {
		return state.getValue(FACING).toYRot();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof PowerArmorStationBlockEntity station)) {
			return InteractionResult.SUCCESS;
		}
		if (PowerArmor.isWearing(serverPlayer)) {
			PowerArmor.dock(serverPlayer, pos, station);
		} else if (player.isSecondaryUseActive()) {
			PowerArmor.enterFromStation(serverPlayer, pos, station);
		} else {
			player.openMenu((MenuProvider) station);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// The frame stands two blocks tall.
		if (!context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context)) {
			return null;
		}
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		Containers.updateNeighboursAfterDestroy(state, level, pos);
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
		builder.add(FACING);
	}
}
