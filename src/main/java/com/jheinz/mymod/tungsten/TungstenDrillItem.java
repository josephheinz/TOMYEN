package com.jheinz.mymod.tungsten;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Tungsten Carbide Drill: a pickaxe that, unless its user sneaks, also digs the eight blocks around the one it
 * breaks, in the plane of the face it was dug from (a 3x3 square). It takes the neighbours only where it could
 * harvest them itself and they're no harder than the middle block plus {@link #HARDNESS_MARGIN} (so stone takes
 * the ores in it, but not obsidian), never bedrock and the like. Each extra block wears the drill like a dug block,
 * and the work stops if it breaks. The client outlines the whole square (DrillOutline).
 */
public class TungstenDrillItem extends Item {
	private static final float HARDNESS_MARGIN = 1.5f;
	/** The face each player is digging, from just before the middle block breaks until just after. */
	private static final Map<UUID, Direction> DIGGING_FACE = new HashMap<>();
	private static boolean extraBlocks;

	public TungstenDrillItem(Properties properties) {
		super(properties);
	}

	/** Whether the player is holding a drill and digging 3x3 with it. */
	public static boolean digsArea(Player player) {
		return player.getMainHandItem().getItem() instanceof TungstenDrillItem && !player.isShiftKeyDown();
	}

	/**
	 * The neighbours a 3x3 dig of {@code centre}, dug from {@code face}, would take with this drill: the square
	 * around it across that face's axis, leaving out what it can't harvest or is too hard.
	 */
	public static List<BlockPos> area(BlockGetter level, ItemStack drill, BlockPos centre, Direction face) {
		List<BlockPos> blocks = new ArrayList<>();
		BlockState middle = level.getBlockState(centre);
		float limit = middle.getDestroySpeed(level, centre) + HARDNESS_MARGIN;
		Direction.Axis axis = face.getAxis();
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				BlockPos pos = switch (axis) {
					case X -> centre.offset(0, a, b);
					case Y -> centre.offset(a, 0, b);
					case Z -> centre.offset(a, b, 0);
				};
				BlockState state = level.getBlockState(pos);
				float hardness = state.getDestroySpeed(level, pos);
				if (!state.isAir() && hardness >= 0.0f && hardness <= limit && drill.isCorrectToolForDrops(state)) {
					blocks.add(pos);
				}
			}
		}
		return blocks;
	}

	/** The face of {@code pos} the player is looking at, or (no clear sight of it) the one facing them. */
	public static Direction faceTowards(Player player, BlockPos pos) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(pos)) {
			return blockHit.getDirection();
		}
		return Direction.orderedByNearest(player)[0].getOpposite();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.tungsten_carbide_drill.hint").withStyle(ChatFormatting.GRAY));
	}

	static void initialize() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!extraBlocks && player instanceof ServerPlayer && digsArea(player)) {
				DIGGING_FACE.put(player.getUUID(), faceTowards(player, pos));
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			Direction face = extraBlocks ? null : DIGGING_FACE.remove(player.getUUID());
			if (face != null && player instanceof ServerPlayer serverPlayer) {
				digNeighbours(serverPlayer, pos, state, face);
			}
		});
	}

	/** Breaks the rest of the square as if the player had dug each block, the middle one already gone. */
	private static void digNeighbours(ServerPlayer player, BlockPos centre, BlockState middle, Direction face) {
		ItemStack drill = player.getMainHandItem();
		if (!(drill.getItem() instanceof TungstenDrillItem)) {
			return;
		}
		// The middle block is air by now: judge the square by what was there.
		BlockGetter before = new MiddleRestored(player.level(), centre, middle);
		List<BlockPos> blocks = area(before, drill, centre, face);
		extraBlocks = true;
		try {
			for (BlockPos pos : blocks) {
				if (drill.isEmpty() || !(drill.getItem() instanceof TungstenDrillItem)) {
					break;
				}
				BlockState state = player.level().getBlockState(pos);
				// Block breaks only show other players their particles; the digger gets theirs here.
				player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state), false));
				player.gameMode.destroyBlock(pos);
			}
		} finally {
			extraBlocks = false;
		}
	}

	/** A level as it was before the middle block broke. */
	private record MiddleRestored(BlockGetter level, BlockPos centre, BlockState middle) implements BlockGetter {
		@Override
		public BlockState getBlockState(BlockPos pos) {
			return pos.equals(centre) ? middle : level.getBlockState(pos);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return level.getFluidState(pos);
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return level.getBlockEntity(pos);
		}

		@Override
		public int getHeight() {
			return level.getHeight();
		}

		@Override
		public int getMinY() {
			return level.getMinY();
		}
	}
}
