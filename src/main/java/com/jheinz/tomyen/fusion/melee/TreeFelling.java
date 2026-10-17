package com.jheinz.tomyen.fusion.melee;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.tomyen.boar.Boars;

/**
 * The Tuskaxe's felling. A log cut by a Tuskaxe that was sprinting a moment ago brings the tree down: every log joined
 * to it (corners too) at or above the cut, as long as the logs carry wild leaves (leaves that would decay, so a cabin
 * or a log pile is left alone). The logs come down a few a tick, lowest first, each broken as if the player had cut it
 * (drops, durability, stats), so the tree topples rather than vanishing.
 */
public final class TreeFelling {
	/** How recently the player must have been sprinting, ticks. */
	public static final int SPRINT_WINDOW = 40;
	public static final int MAX_LOGS = 160;
	/** Wild leaves a tree must have touching its logs. */
	private static final int MIN_WILD_LEAVES = 4;
	private static final int LOGS_PER_TICK = 4;
	private static final Map<UUID, Long> LAST_SPRINT = new HashMap<>();
	private static final List<Fell> FELLING = new ArrayList<>();
	/** Set while felling breaks a log, so those breaks don't start fellings of their own. */
	private static boolean breaking;

	private TreeFelling() {
	}

	private record Fell(ServerLevel level, UUID player, ArrayDeque<BlockPos> logs) {
	}

	static void sprinted(Player player) {
		LAST_SPRINT.put(player.getUUID(), player.level().getGameTime());
	}

	static void initialize() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (breaking || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server) || !state.is(BlockTags.LOGS)
					|| !(player.getMainHandItem().getItem() instanceof TuskaxeItem)) {
				return;
			}
			Long sprint = LAST_SPRINT.get(player.getUUID());
			if (sprint == null || level.getGameTime() - sprint > SPRINT_WINDOW) {
				return;
			}
			List<BlockPos> logs = tree(server, pos);
			if (!logs.isEmpty()) {
				FELLING.add(new Fell(server, serverPlayer.getUUID(), new ArrayDeque<>(logs)));
				LAST_SPRINT.remove(player.getUUID());
				server.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.9f, 0.7f);
				server.playSound(null, pos, Boars.ANGRY, SoundSource.PLAYERS, 0.9f, 0.9f);
			}
		});
	}

	/**
	 * The logs of the tree above a cut log at {@code cut} (already gone), lowest first and nearest the cut first within a
	 * layer; empty if they don't carry enough wild leaves to be a tree.
	 */
	static List<BlockPos> tree(ServerLevel level, BlockPos cut) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		List<BlockPos> logs = new ArrayList<>();
		seen.add(cut);
		open.add(cut);
		while (!open.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos at = open.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = at.offset(dx, dy, dz);
						if (next.getY() < cut.getY() || !seen.add(next)) {
							continue;
						}
						if (level.getBlockState(next).is(BlockTags.LOGS)) {
							logs.add(next);
							open.add(next);
							if (logs.size() >= MAX_LOGS) {
								break;
							}
						}
					}
				}
			}
		}
		int wild = 0;
		Set<BlockPos> counted = new HashSet<>();
		for (BlockPos log : logs) {
			for (Direction side : Direction.values()) {
				BlockPos leaf = log.relative(side);
				BlockState state = level.getBlockState(leaf);
				if (state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)
						&& counted.add(leaf)) {
					wild++;
				}
			}
			if (wild >= MIN_WILD_LEAVES) {
				break;
			}
		}
		if (wild < MIN_WILD_LEAVES) {
			return List.of();
		}
		logs.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distSqr(cut)));
		return logs;
	}

	static void tick(ServerLevel level) {
		Iterator<Fell> it = FELLING.iterator();
		while (it.hasNext()) {
			Fell fell = it.next();
			if (fell.level != level) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(fell.player);
			if (player == null || player.level() != level || !(player.getMainHandItem().getItem() instanceof TuskaxeItem)) {
				it.remove();
				continue;
			}
			for (int i = 0; i < LOGS_PER_TICK && !fell.logs.isEmpty(); i++) {
				BlockPos pos = fell.logs.poll();
				BlockState state = level.getBlockState(pos);
				if (!state.is(BlockTags.LOGS)) {
					continue;
				}
				// Block breaks show other players their particles; the feller gets theirs here.
				player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state), false));
				breaking = true;
				try {
					player.gameMode.destroyBlock(pos);
				} finally {
					breaking = false;
				}
				if (!(player.getMainHandItem().getItem() instanceof TuskaxeItem)) {
					// The axe broke on this one.
					fell.logs.clear();
				}
			}
			if (fell.logs.isEmpty()) {
				it.remove();
			}
		}
	}

	static void clear() {
		LAST_SPRINT.clear();
		FELLING.clear();
		breaking = false;
	}
}
