package com.jheinz.mymod.fae;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * The Feywild: a pocket dimension in eternal violet twilight, one island some 370 blocks across
 * floating in the void round the origin (the terrain, biomes, sky, music and bed rules are all data: see
 * {@code data/mymod/dimension/feywild.json} and what it names). Its world border is set to {@value #BORDER_SIZE}
 * blocks the first time it loads, so it stays a pocket.
 *
 * <p>Everyone arrives at the island's centre, in a Fae Ring that's planted there (clearing anything in its way) the
 * first time it's needed; standing in it again takes them back to where they left the Overworld (stored on the
 * player). Anyone who falls off the island is caught before the void and set down at the ring, drifting.
 */
public final class Feywild {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, MyMod.id("feywild"));
	private static final double BORDER_SIZE = 480.0;
	private static final int ARRIVAL_RING_RADIUS = 4;
	/** How far below the bottom of the world a faller is caught. */
	private static final int CATCH_DEPTH = 16;

	private Feywild() {
	}

	public static boolean is(Level level) {
		return level.dimension() == LEVEL;
	}

	public static void initialize() {
		ServerLevelEvents.LOAD.register((server, level) -> {
			WorldBorder border = level.getWorldBorder();
			if (is(level) && border.getSize() > BORDER_SIZE) {
				border.setCenter(0.0, 0.0);
				border.setSize(BORDER_SIZE);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Feywild::catchFallers);
	}

	private static void catchFallers(MinecraftServer server) {
		ServerLevel level = server.getLevel(LEVEL);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.getY() < level.getMinY() - CATCH_DEPTH && !player.isSpectator()) {
				arrive(player, level, arrival(level));
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
			}
		}
	}

	/** Into the Feywild, remembering where they came from. */
	public static void sendIn(ServerPlayer player) {
		ServerLevel feywild = player.level().getServer().getLevel(LEVEL);
		if (feywild == null) {
			player.sendOverlayMessage(Component.translatable("message.mymod.feywild_missing"));
			return;
		}
		player.setAttached(Fae.RETURN_POINT, GlobalPos.of(player.level().dimension(), player.blockPosition()));
		arrive(player, feywild, arrival(feywild));
	}

	/** Back to where they entered from; with nowhere stored, to the world's spawn. */
	public static void sendHome(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		GlobalPos back = player.getAttached(Fae.RETURN_POINT);
		ServerLevel level = back == null ? null : server.getLevel(back.dimension());
		BlockPos pos;
		if (level != null) {
			pos = back.pos();
		} else {
			GlobalPos spawn = server.getRespawnData().globalPos();
			level = server.getLevel(spawn.dimension());
			if (level == null) {
				level = server.overworld();
			}
			pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.pos());
		}
		player.removeAttached(Fae.RETURN_POINT);
		arrive(player, level, pos);
	}

	private static void arrive(ServerPlayer player, ServerLevel level, BlockPos pos) {
		ServerLevel from = player.level();
		from.playSound(null, player.getX(), player.getY(), player.getZ(), Fae.RING_TRAVEL, SoundSource.PLAYERS, 1.0f, 1.0f);
		from.sendParticles(Fae.SPARKLE, player.getX(), player.getY(1.0), player.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
		player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.getYRot(), player.getXRot(),
				TeleportTransition.DO_NOTHING));
		player.resetFallDistance();
		level.playSound(null, pos, Fae.RING_TRAVEL, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.sendParticles(Fae.SPARKLE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.05);
	}

	/**
	 * Where everyone arrives: the top of the island's centre column, with a ring round it. If the ring's missing (the
	 * first visit, or someone picked the mushrooms) the spot is cleared of trees and plants and a new ring planted.
	 */
	static BlockPos arrival(ServerLevel level) {
		level.getChunk(0, 0);
		BlockPos centre = ground(level);
		if (centre.getY() <= level.getMinY()) {
			// No island here after all (a changed data pack, say): a small platform to stand on.
			centre = new BlockPos(0, 80, 0);
			for (int dx = -ARRIVAL_RING_RADIUS - 1; dx <= ARRIVAL_RING_RADIUS + 1; dx++) {
				for (int dz = -ARRIVAL_RING_RADIUS - 1; dz <= ARRIVAL_RING_RADIUS + 1; dz++) {
					level.setBlockAndUpdate(centre.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
		}
		if (!FaeRings.isInRing(level, centre)) {
			clear(level, centre);
			FaeRingFeature.placeRing(level, centre, level.getRandom(), ARRIVAL_RING_RADIUS, 12, 0.5f);
		}
		return centre;
	}

	/** The first air above the ground at the origin, looking down past any tree or giant mushroom standing there. */
	private static BlockPos ground(ServerLevel level) {
		BlockPos.MutableBlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO).mutable();
		while (pos.getY() > level.getMinY() && clearable(level.getBlockState(pos.below()))) {
			pos.move(0, -1, 0);
		}
		return pos.immutable();
	}

	private static boolean clearable(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
				|| state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)
				|| state.is(Fae.RING_MUSHROOMS) || state.is(BlockTags.FLOWERS);
	}

	/** Clears trees, mushrooms and plants from a column of air over the ring's spot. */
	private static void clear(ServerLevel level, BlockPos centre) {
		int reach = ARRIVAL_RING_RADIUS + 1;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz > reach * reach) {
					continue;
				}
				for (int dy = 0; dy < 24; dy++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && clearable(state)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}
}
