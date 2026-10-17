package com.jheinz.tomyen.isle;

import java.util.Objects;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;

/**
 * The Isle of the Damned: a dusk-dark archipelago of ash on a still black sea, laid out in rings (the Shore, the
 * Hollows, the Abyss; see {@link IsleDepth}) round an arrival shrine at the origin. Its terrain, biomes, sky, fog and
 * sound are data ({@code data/tomyen/dimension/isle.json} and what it names), following the Feywild's pattern; its
 * world border is set to {@value #BORDER_SIZE} blocks the first time it loads.
 *
 * <p>Everyone arrives at the shrine, facing the way to their Revenant, with the title card and the depth their hoard
 * lies at. Leaving (by the Lantern of Return or a Return Rift) takes them back to where they were pulled down from,
 * stored on the player; with nowhere stored, to their respawn point or the world spawn. Anyone who falls out of the
 * world is caught and set down at the shrine.
 */
public final class IsleDimension {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, MyMod.id("isle"));
	private static final double BORDER_SIZE = 1400.0;
	private static final int CATCH_DEPTH = 16;
	private static final int HINT_INTERVAL = 20;
	private static final int WARD_TICKS = 1200;

	private IsleDimension() {
	}

	public static boolean is(Level level) {
		return level.dimension() == LEVEL;
	}

	static void initialize() {
		ServerLevelEvents.LOAD.register((server, level) -> {
			WorldBorder border = level.getWorldBorder();
			if (is(level) && border.getSize() > BORDER_SIZE) {
				border.setCenter(0.0, 0.0);
				border.setSize(BORDER_SIZE);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(IsleDimension::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel isle = server.getLevel(LEVEL);
		if (isle != null) {
			for (ServerPlayer player : isle.players()) {
				if (player.getY() < isle.getMinY() - CATCH_DEPTH && !player.isSpectator()) {
					BlockPos arrival = IsleStructures.arrival(isle);
					player.teleport(new TeleportTransition(isle, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, player.getYRot(), player.getXRot(),
							TeleportTransition.DO_NOTHING));
					player.resetFallDistance();
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
				}
			}
		}
		if (server.getTickCount() % HINT_INTERVAL == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				updateHint(player);
			}
		}
	}

	/** Keeps the player's HUD hint pointing at their Revenant while they're on the Isle, and gone when they aren't. */
	static void updateHint(ServerPlayer player) {
		Hoard hoard = is(player.level()) ? Hoards.get(player.level().getServer(), player.getUUID()) : null;
		IsleHint hint = hoard == null ? null : new IsleHint(IsleArenas.target(player.level(), hoard), hoard.depth().index);
		if (!Objects.equals(hint, player.getAttached(Isle.HINT))) {
			if (hint == null) {
				player.removeAttached(Isle.HINT);
			} else {
				player.setAttached(Isle.HINT, hint);
			}
		}
	}

	/**
	 * Down to the Isle, remembering where they came from ({@code from}: by their gravestone). An offering brings them
	 * with Soul Ward on.
	 */
	public static void sendIn(ServerPlayer player, GlobalPos from, boolean offering) {
		MinecraftServer server = player.level().getServer();
		ServerLevel isle = server.getLevel(LEVEL);
		if (isle == null) {
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.missing"));
			return;
		}
		if (!is(player.level())) {
			player.setAttached(Isle.RETURN_POINT, from);
		}
		ServerLevel fromLevel = player.level();
		fromLevel.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 40, 0.4, 0.9, 0.4, 0.06);
		fromLevel.playSound(null, player.getX(), player.getY(), player.getZ(), Isle.PULLED_DOWN, SoundSource.PLAYERS, 1.0f, 1.0f);

		BlockPos arrival = IsleStructures.arrival(isle);
		Hoard hoard = Hoards.get(server, player.getUUID());
		float yaw = player.getYRot();
		if (hoard != null) {
			BlockPos target = IsleArenas.target(isle, hoard);
			yaw = (float) (Mth.atan2(target.getZ() - arrival.getZ(), target.getX() - arrival.getX()) * Mth.RAD_TO_DEG) - 90.0f;
		}
		player.teleport(new TeleportTransition(isle, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, yaw, 0.0f, TeleportTransition.DO_NOTHING));
		player.resetFallDistance();
		isle.playSound(null, arrival, Isle.ARRIVE, SoundSource.PLAYERS, 1.0f, 1.0f);
		isle.sendParticles(ParticleTypes.SOUL, arrival.getX() + 0.5, arrival.getY() + 1.0, arrival.getZ() + 0.5, 40, 0.4, 0.9, 0.4, 0.04);

		IsleDepth depth = hoard == null ? IsleDepth.SHORE : hoard.depth();
		player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 30));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("isle.tomyen.title").withStyle(ChatFormatting.DARK_AQUA)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(hoard == null
				? Component.translatable("isle.tomyen.subtitle.empty").withStyle(ChatFormatting.GRAY)
				: Component.translatable("isle.tomyen.subtitle", depth.displayName()).withStyle(ChatFormatting.GRAY)));
		if (offering) {
			player.addEffect(new MobEffectInstance(Isle.SOUL_WARD, WARD_TICKS, 0));
		}
		updateHint(player);
		IsleEvents.ARRIVED.invoker().onArrived(player, depth, offering);
	}

	/**
	 * Home: to where they were pulled down from (in front of their gravestone, if it's still there), else their
	 * respawn point or the world spawn. {@code victorious} for a Return Rift.
	 */
	public static void sendHome(ServerPlayer player, boolean victorious) {
		MinecraftServer server = player.level().getServer();
		GlobalPos back = player.getAttached(Isle.RETURN_POINT);
		ServerLevel level = back == null ? null : server.getLevel(back.dimension());
		BlockPos pos = null;
		if (level != null) {
			level.getChunk(back.pos());
			pos = GravestonePlacement.find(level, back.pos().above());
		}
		if (pos == null) {
			ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
			GlobalPos spawn = respawn != null ? respawn.respawnData().globalPos() : server.getRespawnData().globalPos();
			level = server.getLevel(spawn.dimension());
			if (level == null) {
				level = server.overworld();
			}
			level.getChunk(spawn.pos());
			pos = GravestonePlacement.find(level, spawn.pos().above());
			if (pos == null) {
				pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.pos());
			}
		}
		ServerLevel from = player.level();
		from.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 30, 0.4, 0.9, 0.4, 0.05);
		from.playSound(null, player.getX(), player.getY(), player.getZ(), Isle.RETURN, SoundSource.PLAYERS, 1.0f, 1.0f);
		player.removeAttached(Isle.RETURN_POINT);
		player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.getYRot(), player.getXRot(),
				TeleportTransition.DO_NOTHING));
		player.resetFallDistance();
		level.playSound(null, pos, Isle.RETURN, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.4, 0.9, 0.4, 0.04);
		updateHint(player);
		IsleEvents.LEFT.invoker().onLeft(player, victorious);
	}
}
