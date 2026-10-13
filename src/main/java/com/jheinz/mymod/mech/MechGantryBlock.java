package com.jheinz.mymod.mech;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.powerarmor.PowerArmor;

/**
 * The Mech Gantry: where a Bulwark Walker is built, rearmed and docked. It's a single block, the deck the walker
 * stands on; the three-wide assembly frame over it (five and a half blocks tall) is drawn by its renderer, and
 * needs that space clear, three by three and six high, to be placed. It faces away from the player who placed
 * it, and the walker stands in it facing the same way, drawn part by part as they're fitted.
 * <ul>
 * <li>Use it: the gantry's interface, to fit the parts (see {@link MechLoadout}).
 * <li>Sneak-use it with the five core parts fitted: the walker steps out of the frame and you climb into it.
 * <li>Use it, empty, with a walker nobody's in standing on it: dock it, its parts (and their wear, its hold and
 * its core's charge) back in the gantry to repair or rearm.
 * </ul>
 * Breaking it drops whatever's fitted.
 */
public class MechGantryBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** Height of the deck the walker stands on, in blocks. */
	public static final double DECK_HEIGHT = 3.0 / 16.0;
	/** How near the deck a walker has to stand to be docked. */
	private static final double DOCK_REACH = 3.0;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

	public MechGantryBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MechGantryBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Where a walker in the gantry stands: the middle of the deck. */
	public static Vec3 anchor(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + DECK_HEIGHT, pos.getZ() + 0.5);
	}

	/** The way a walker in the gantry faces, as an entity's yaw. */
	public static float yaw(BlockState state) {
		return state.getValue(FACING).toYRot();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof MechGantryBlockEntity gantry)) {
			return InteractionResult.SUCCESS;
		}
		ServerLevel serverLevel = serverPlayer.level();
		if (player.isSecondaryUseActive() && MechLoadout.complete(gantry.items())) {
			launch(serverLevel, pos, state, gantry, serverPlayer);
			return InteractionResult.SUCCESS;
		}
		if (MechLoadout.empty(gantry.items())) {
			Mech docking = dockable(serverLevel, pos);
			if (docking != null) {
				if (docking.pilot() != null || docking.isVehicle()) {
					player.sendOverlayMessage(Component.translatable("block.mymod.mech_gantry.occupied").withStyle(ChatFormatting.RED));
				} else {
					gantry.setParts(docking.uninstall());
					docking.discard();
					level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.6f);
					player.sendOverlayMessage(Component.translatable("block.mymod.mech_gantry.docked").withStyle(ChatFormatting.GREEN));
				}
				return InteractionResult.SUCCESS;
			}
		}
		player.openMenu((MenuProvider) gantry);
		return InteractionResult.SUCCESS;
	}

	private static @Nullable Mech dockable(ServerLevel level, BlockPos pos) {
		Vec3 anchor = anchor(pos);
		List<Mech> mechs = level.getEntitiesOfClass(Mech.class, new AABB(anchor, anchor).inflate(DOCK_REACH, 2.0, DOCK_REACH), Mech::isAlive);
		return mechs.isEmpty() ? null : mechs.getFirst();
	}

	/** The walker steps out of the frame with the gantry's parts, and the player climbs aboard. */
	private static void launch(ServerLevel level, BlockPos pos, BlockState state, MechGantryBlockEntity gantry, ServerPlayer player) {
		if (PowerArmor.isWearing(player) || player.isPassenger()) {
			player.sendOverlayMessage(Component.translatable(PowerArmor.isWearing(player) ? "entity.mymod.mech.no_power_armor"
					: "entity.mymod.mech.cant_board").withStyle(ChatFormatting.RED));
			return;
		}
		List<ItemStack> parts = gantry.items().stream().map(ItemStack::copy).toList();
		Mech mech = Mech.assemble(level, anchor(pos), yaw(state), parts);
		if (mech == null) {
			player.sendOverlayMessage(Component.translatable("block.mymod.mech_gantry.no_room").withStyle(ChatFormatting.RED));
			return;
		}
		gantry.clearContent();
		level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
		mech.board(player);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		// The frame stands three wide and six high round the deck.
		BlockPos centre = context.getClickedPos();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 5, 1))) {
			if (!pos.equals(centre) && !context.getLevel().getBlockState(pos).canBeReplaced(context)) {
				return null;
			}
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
