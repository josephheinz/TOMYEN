package com.jheinz.tomyen.alien;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;

/**
 * UFOs over the Overworld. Every {@value #CHECK_INTERVAL} ticks each player has a small chance of a visit, so that over
 * a whole night the chance is the {@code tomyen:ufo_chance} game rule (in percent, 15 by default); by day it's a fifth
 * of that, the odd saucer seen roaming in daylight. A visit is a saucer turning up {@value #MIN_DISTANCE}-{@value
 * #MAX_DISTANCE} blocks off, high up, to roam round them (see {@link Ufo}). Separately, at night only, there's the
 * {@code tomyen:ufo_landing_chance} (8% a night by default) of one landing in the open {@value #MIN_LANDING}-{@value
 * #MAX_LANDING} blocks away with its crew. {@code tomyen:ufo_sightings} turns them all off.
 * <p>
 * Never more than {@value #MAX_NEARBY} saucers within {@value #NEARBY_RANGE} blocks of a player, and only one landed.
 * Operators can call one up on the spot with {@code /ufo visit} or {@code /ufo land}.
 */
public final class UfoSightings {
	public static final GameRule<Boolean> UFO_SIGHTINGS = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.SPAWNING)
			.buildAndRegister(MyMod.id("ufo_sightings"));
	public static final GameRule<Integer> UFO_CHANCE = GameRuleBuilder.forInteger(15)
			.category(GameRuleCategory.SPAWNING)
			.range(0, 100)
			.buildAndRegister(MyMod.id("ufo_chance"));
	public static final GameRule<Integer> UFO_LANDING_CHANCE = GameRuleBuilder.forInteger(8)
			.category(GameRuleCategory.SPAWNING)
			.range(0, 100)
			.buildAndRegister(MyMod.id("ufo_landing_chance"));

	private static final int CHECK_INTERVAL = 100;
	/** About how many checks a night (or a day) lasts. */
	private static final int CHECKS_PER_HALF_DAY = 115;
	private static final float DAY_FACTOR = 0.2f;
	static final int MIN_DISTANCE = 48;
	static final int MAX_DISTANCE = 96;
	static final int MIN_LANDING = 40;
	static final int MAX_LANDING = 80;
	static final int MAX_NEARBY = 2;
	static final int NEARBY_RANGE = 192;
	/** A landing site's ground has to be this flat for this far round its middle. */
	private static final int SITE_RADIUS = 3;

	private UfoSightings() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD || level.getGameTime() % CHECK_INTERVAL != 0 || !level.getGameRules().get(UFO_SIGHTINGS)) {
				return;
			}
			boolean night = level.isDarkOutside();
			float visit = level.getGameRules().get(UFO_CHANCE) / 100.0f / CHECKS_PER_HALF_DAY * (night ? 1.0f : DAY_FACTOR);
			float landing = night ? level.getGameRules().get(UFO_LANDING_CHANCE) / 100.0f / CHECKS_PER_HALF_DAY : 0.0f;
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) {
					continue;
				}
				float roll = level.getRandom().nextFloat();
				if (roll < landing) {
					land(level, player);
				} else if (roll < landing + visit) {
					visit(level, player);
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("ufo")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("visit").then(Commands.argument("players", EntityArgument.players()).executes(command ->
						call(EntityArgument.getPlayers(command, "players"), false))))
				.then(Commands.literal("land").then(Commands.argument("players", EntityArgument.players()).executes(command ->
						call(EntityArgument.getPlayers(command, "players"), true))))));
	}

	private static int call(Collection<ServerPlayer> players, boolean landing) {
		int sent = 0;
		for (ServerPlayer player : players) {
			if ((landing ? land(player.level(), player) : visit(player.level(), player)) != null) {
				sent++;
			}
		}
		return sent;
	}

	private static int nearby(ServerLevel level, ServerPlayer player, boolean landedOnly) {
		return level.getEntitiesOfClass(Ufo.class, new AABB(player.blockPosition()).inflate(NEARBY_RANGE),
				ufo -> !landedOnly || ufo.isLanded() || ufo.state() == Ufo.LANDING).size();
	}

	/** Sends a saucer to roam round this player; null if there's no room for one. */
	public static @Nullable Ufo visit(ServerLevel level, ServerPlayer player) {
		if (nearby(level, player, false) >= MAX_NEARBY) {
			return null;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Mth.TWO_PI;
		for (int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1); distance >= 16; distance /= 2) {
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			Ufo ufo = spawn(level, player, x, z);
			if (ufo != null) {
				player.sendOverlayMessage(Component.translatable("message.tomyen.ufo_sighted").withStyle(ChatFormatting.GREEN));
				return ufo;
			}
		}
		return null;
	}

	/** Brings a saucer down near this player with its crew; null if there's nowhere to land or one's down already. */
	public static @Nullable Ufo land(ServerLevel level, ServerPlayer player) {
		if (nearby(level, player, false) >= MAX_NEARBY || nearby(level, player, true) > 0) {
			return null;
		}
		BlockPos site = findLandingSite(level, player);
		if (site == null) {
			return null;
		}
		Ufo ufo = spawn(level, player, site.getX(), site.getZ());
		if (ufo != null) {
			ufo.land(site);
		}
		return ufo;
	}

	private static @Nullable Ufo spawn(ServerLevel level, ServerPlayer player, int x, int z) {
		BlockPos column = new BlockPos(x, 0, z);
		if (!level.isPositionEntityTicking(column)) {
			return null;
		}
		Ufo ufo = Aliens.UFO.create(level, EntitySpawnReason.EVENT);
		if (ufo == null) {
			return null;
		}
		int y = Math.min(level.getMaxY() - 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + Ufo.MAX_ALTITUDE);
		ufo.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
		ufo.visit(player);
		level.addFreshEntity(ufo);
		return ufo;
	}

	/**
	 * Somewhere out in the open {@value #MIN_LANDING}-{@value #MAX_LANDING} blocks from the player: dry, flat ground
	 * {@value #SITE_RADIUS} blocks round with nothing overhead. Null if a couple of dozen tries find nowhere.
	 */
	static @Nullable BlockPos findLandingSite(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 24; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = MIN_LANDING + random.nextDouble() * (MAX_LANDING - MIN_LANDING);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.isPositionEntityTicking(new BlockPos(x, 0, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (isSite(level, x, y, z)) {
				return new BlockPos(x, y, z);
			}
		}
		return null;
	}

	private static boolean isSite(ServerLevel level, int x, int y, int z) {
		for (int dx = -SITE_RADIUS; dx <= SITE_RADIUS; dx++) {
			for (int dz = -SITE_RADIUS; dz <= SITE_RADIUS; dz++) {
				// The motion-blocking top counts trees and fluids, so this also rules out woods and water.
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz);
				BlockPos ground = new BlockPos(x + dx, top - 1, z + dz);
				if (Math.abs(top - y) > 1 || !level.getFluidState(ground).isEmpty() || !level.getBlockState(ground).isSolidRender()) {
					return false;
				}
			}
		}
		return level.canSeeSky(new BlockPos(x, y, z));
	}
}
