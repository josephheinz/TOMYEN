package com.jheinz.mymod.meteorite;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * Meteors falling in the Overworld on clear nights. Every {@link #CHECK_INTERVAL} ticks of a dark, dry night each
 * player has a small chance of one coming down {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} blocks away, so that
 * over a whole night the chance per player is the {@code mymod:meteor_chance} game rule (in percent; 10 by default).
 * {@code mymod:meteor_showers} turns them off altogether.
 * <p>
 * A meteor starts about {@link #FALL_HEIGHT} blocks above its landing spot and a little way back towards the player,
 * so it streaks over (or past) them on its way down. Everyone within {@link #WARNING_RANGE} blocks of it is told one
 * is falling. Spots in chunks that aren't ticking entities are passed over, so a meteor never stalls mid-air.
 */
public final class MeteorShowers {
	public static final GameRule<Boolean> METEOR_SHOWERS = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.SPAWNING)
			.buildAndRegister(MyMod.id("meteor_showers"));
	public static final GameRule<Integer> METEOR_CHANCE = GameRuleBuilder.forInteger(10)
			.category(GameRuleCategory.SPAWNING)
			.range(0, 100)
			.buildAndRegister(MyMod.id("meteor_chance"));

	private static final int CHECK_INTERVAL = 100;
	/** About how many checks a night lasts (it's dark for some 11000 ticks). */
	private static final int CHECKS_PER_NIGHT = 110;
	public static final int MIN_DISTANCE = 64;
	public static final int MAX_DISTANCE = 128;
	public static final int FALL_HEIGHT = 110;
	private static final int WARNING_RANGE = 192;

	private MeteorShowers() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD || level.getGameTime() % CHECK_INTERVAL != 0 || !level.getGameRules().get(METEOR_SHOWERS)
					|| !level.isDarkOutside() || level.isRaining()) {
				return;
			}
			float chance = level.getGameRules().get(METEOR_CHANCE) / 100.0f / CHECKS_PER_NIGHT;
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && level.getRandom().nextFloat() < chance) {
					launch(level, player);
				}
			}
		});
	}

	/** Sends a meteor down somewhere near this player; false if there was nowhere for it to go. */
	public static boolean launch(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Mth.TWO_PI;
		for (int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1); distance >= MIN_DISTANCE / 2; distance /= 2) {
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance), z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos target = new BlockPos(x, 0, z);
			if (!level.isPositionEntityTicking(target)) {
				continue;
			}
			Vec3 to = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
			// Start back towards the player and off to one side, so the streak crosses the sky.
			double back = distance * (0.3 + random.nextDouble() * 0.3);
			double side = (random.nextDouble() - 0.5) * 60.0;
			Vec3 from = new Vec3(to.x - Math.cos(angle) * back - Math.sin(angle) * side,
					Math.min(level.getMaxY() - 2, to.y + FALL_HEIGHT), to.z - Math.sin(angle) * back + Math.cos(angle) * side);
			if (!level.isPositionEntityTicking(BlockPos.containing(from))) {
				continue;
			}
			level.addFreshEntity(new Meteor(level, from, to));
			Component warning = Component.translatable("message.mymod.meteor_falling").withStyle(ChatFormatting.GOLD);
			for (ServerPlayer nearby : level.players()) {
				if (nearby.distanceToSqr(from) < WARNING_RANGE * WARNING_RANGE || nearby.distanceToSqr(to) < WARNING_RANGE * WARNING_RANGE) {
					nearby.sendOverlayMessage(warning);
				}
			}
			return true;
		}
		return false;
	}
}
