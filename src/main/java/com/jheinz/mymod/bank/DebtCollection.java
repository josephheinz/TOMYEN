package com.jheinz.mymod.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.tank.Tank;
import com.jheinz.mymod.tank.Tanks;

/**
 * The server side of credit: charging, repaying, the repayment deadline and the reverse raid.
 * <p>
 * The first charge on a clear account opens it with {@link #TERM_TICKS} (five in-game days) to pay; more
 * charges add to what's owed but don't move the deadline. The clock only runs while the player is online
 * and alive. Warnings go out as it gets close; when it runs out with anything still owed the hunt starts:
 * a "Debt Collection" boss bar, and every {@link #WAVE_TICKS} a wave of collectors (tanks crewed by armed
 * villagers, and iron golems) spawns around the player and chases them wherever they go. It ends when:
 * <ul>
 * <li>the debt is paid in full: the collectors leave;</li>
 * <li>the collectors kill the player: the debt is written off entirely (balance, deadline and every card
 * they carried are cleared) and the collectors leave satisfied;</li>
 * <li>the player dies some other way: the collectors lose the trail and leave, but the debt stands, with
 * {@link #GRACE_TICKS} (one in-game day) before they come back.</li>
 * </ul>
 * Collectors check for themselves whether their hunt is still on (see {@link Collectors}), so they also
 * leave if the player logs out, changes dimension or the server restarts mid-hunt.
 */
public final class DebtCollection {
	/** Five in-game days to pay back a balance. */
	public static final int TERM_TICKS = 5 * 24000;
	/** After dying to something other than the collectors, the hunt resumes after one in-game day. */
	public static final int GRACE_TICKS = 24000;
	/** Between collector waves. */
	public static final int WAVE_TICKS = 90 * 20;
	/** No new wave while this many collectors are still after the player. */
	private static final int MAX_COLLECTORS = 10;
	/** Collectors spawn this far from the player, and follow them (teleporting to catch up) past {@link Collectors#CATCH_UP_DISTANCE}. */
	private static final int SPAWN_MIN_DISTANCE = 16;
	private static final int SPAWN_MAX_DISTANCE = 26;
	/** Ticks left at which each warning is given, earliest first. */
	static final int[] WARNINGS = {3 * 24000, 2 * 24000, 24000, 12000, 2000, 600};
	private static final int UPDATE_TICKS = 20;

	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** How many collectors the latest wave sent, per player, for the boss bar. */
	private static final Map<UUID, Integer> WAVE_SIZE = new HashMap<>();

	private DebtCollection() {
	}

	/** Records a card charge against the player paying. Called by the card payment on the server. */
	public static void charge(ServerPlayer player, int emeralds, ItemStack card) {
		Debt debt = Debt.of(player);
		Debt updated = debt == null ? Debt.opened(emeralds) : debt.withOwed(debt.owed() + emeralds);
		player.setAttached(Banking.DEBT, updated);
		player.sendOverlayMessage(Component.translatable("message.mymod.bank.charged", emeralds, card.getHoverName(),
				CreditCardItem.balance(card), ((CreditCardItem) card.getItem()).limit()).withStyle(ChatFormatting.GOLD));
		if (debt == null) {
			player.sendSystemMessage(Component.translatable("message.mymod.bank.account_opened", formatTime(TERM_TICKS))
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Pays off this many emeralds (no more than is owed), clearing as much off the cards they carry. */
	public static void repay(ServerPlayer player, int emeralds) {
		Debt debt = Debt.of(player);
		if (debt == null || emeralds <= 0) {
			return;
		}
		int left = debt.owed() - emeralds;
		if (left <= 0) {
			settle(player);
			player.sendSystemMessage(Component.translatable("message.mymod.bank.paid_off").withStyle(ChatFormatting.GREEN));
		} else {
			CreditCards.restore(player, emeralds);
			player.setAttached(Banking.DEBT, debt.withOwed(left));
			player.sendSystemMessage(Component.translatable("message.mymod.bank.paid", emeralds, left, formatTime(debt.ticksLeft()))
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Clears the account: nothing owed, no deadline, no hunt, and every card they carry back to full credit. */
	public static void settle(ServerPlayer player) {
		player.removeAttached(Banking.DEBT);
		CreditCards.restoreAll(player);
		endBar(player);
	}

	/** The player's account summary, for an ATM or banker: what's owed and when it's due. */
	public static Component statement(ServerPlayer player) {
		Debt debt = Debt.of(player);
		if (debt == null) {
			return Component.translatable("message.mymod.bank.nothing_owed").withStyle(ChatFormatting.GREEN);
		}
		if (debt.hunting()) {
			return Component.translatable("message.mymod.bank.statement_collections", debt.owed()).withStyle(ChatFormatting.RED);
		}
		return Component.translatable("message.mymod.bank.statement", debt.owed(), formatTime(debt.ticksLeft()))
				.withStyle(ChatFormatting.YELLOW);
	}

	/** "2d 5h" or "5h 12m", in in-game time (a day is 24000 ticks, an hour 1000). */
	public static String formatTime(int ticks) {
		int t = Math.max(0, ticks);
		int days = t / 24000;
		int hours = t % 24000 / 1000;
		if (days > 0) {
			return days + "d " + hours + "h";
		}
		return hours + "h " + t % 1000 * 60 / 1000 + "m";
	}

	/**
	 * Before a player dies: a death to the collectors writes the debt off (the cards they carry are cleared
	 * before they drop); any other death during the hunt just buys them a day's grace.
	 */
	public static void onDeath(ServerPlayer player, DamageSource source) {
		Debt debt = Debt.of(player);
		if (debt == null || !debt.hunting()) {
			return;
		}
		if (Collectors.isCollector(source.getEntity()) || Collectors.isCollector(source.getDirectEntity())
				|| Collectors.isCollector(player.getKillCredit())) {
			settle(player);
			player.sendSystemMessage(Component.translatable("message.mymod.bank.collected").withStyle(ChatFormatting.DARK_RED));
		} else {
			int warned = 0;
			for (int i = 0; i < WARNINGS.length; i++) {
				if (WARNINGS[i] >= GRACE_TICKS) {
					warned |= 1 << i;
				}
			}
			player.setAttached(Banking.DEBT, debt.withHunt(false, 0, 0).withTicksLeft(GRACE_TICKS).withWarned(warned));
			endBar(player);
			player.sendSystemMessage(Component.translatable("message.mymod.bank.grace", debt.owed(), formatTime(GRACE_TICKS))
					.withStyle(ChatFormatting.GOLD));
		}
	}

	public static void onLeave(ServerPlayer player) {
		endBar(player);
	}

	public static void onServerStopped() {
		BARS.values().forEach(ServerBossEvent::removeAllPlayers);
		BARS.clear();
		WAVE_SIZE.clear();
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % UPDATE_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			Debt debt = Debt.of(player);
			if (debt == null) {
				endBar(player);
			} else if (player.isAlive()) {
				player.setAttached(Banking.DEBT, debt.hunting() ? huntTick(player, debt) : countdown(player, debt));
			}
		}
	}

	private static Debt countdown(ServerPlayer player, Debt debt) {
		int left = debt.ticksLeft() - UPDATE_TICKS;
		int warned = debt.warned();
		boolean crossed = false;
		for (int i = 0; i < WARNINGS.length; i++) {
			if (left <= WARNINGS[i] && (warned & 1 << i) == 0) {
				warned |= 1 << i;
				crossed = true;
			}
		}
		// One warning even if several thresholds passed at once (a deadline shortened by command, say).
		if (crossed && left > 0) {
			warn(player, debt.owed(), left);
		}
		if (left > 0) {
			return debt.withTicksLeft(left).withWarned(warned);
		}
		player.sendSystemMessage(Component.translatable("message.mymod.bank.hunt_started", debt.owed()).withStyle(ChatFormatting.DARK_RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 64.0f, 1.0f);
		return debt.withTicksLeft(0).withWarned(warned).withHunt(true, 0, 0);
	}

	private static void warn(ServerPlayer player, int owed, int left) {
		boolean urgent = left <= 2000;
		player.sendSystemMessage(Component.translatable(urgent ? "message.mymod.bank.final_warning" : "message.mymod.bank.warning",
				owed, formatTime(left)).withStyle(urgent ? ChatFormatting.RED : ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS,
				urgent ? 1.0f : 0.5f, urgent ? 0.7f : 1.0f);
	}

	private static Debt huntTick(ServerPlayer player, Debt debt) {
		ServerLevel level = player.level();
		List<Mob> collectors = collectorsAfter(player);
		int nextWave = debt.nextWaveTicks() - UPDATE_TICKS;
		int wave = debt.wave();
		// Collectors leave a creative or spectating player alone, so don't send more at one.
		boolean huntable = !player.isCreative() && !player.isSpectator();
		if (nextWave <= 0 && huntable) {
			if (collectors.size() < MAX_COLLECTORS) {
				wave++;
				int sent = spawnWave(level, player, wave);
				WAVE_SIZE.put(player.getUUID(), sent);
				collectors = collectorsAfter(player);
				if (sent > 0) {
					player.sendOverlayMessage(Component.translatable("message.mymod.bank.wave", wave).withStyle(ChatFormatting.RED));
				}
			}
			nextWave = WAVE_TICKS;
		}
		updateBar(player, debt.owed(), wave, collectors.size(), nextWave);
		return debt.withHunt(true, wave, Math.max(nextWave, 0));
	}

	private static List<Mob> collectorsAfter(ServerPlayer player) {
		UUID id = player.getUUID();
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(96.0),
				mob -> mob.isAlive() && mob instanceof Collector collector && id.equals(collector.debtor()));
	}

	/**
	 * Tanks (each crewed by an armed collector) and iron golems; the wave grows up to three of each.
	 * Returns how many collectors were sent (fewer if there was nowhere to put them).
	 */
	private static int spawnWave(ServerLevel level, ServerPlayer player, int wave) {
		int tanks = Math.min(wave, 3);
		int golems = Math.min((wave + 1) / 2, 3);
		int sent = 0;
		for (int i = 0; i < tanks; i++) {
			Vec3 pos = findSpawn(level, player, Tanks.TANK);
			Tank tank = pos == null ? null : Tanks.TANK.create(level, EntitySpawnReason.EVENT);
			DebtCollector crew = tank == null ? null : Banking.DEBT_COLLECTOR.create(level, EntitySpawnReason.EVENT);
			if (tank == null || crew == null) {
				continue;
			}
			tank.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
			tank.setDebtor(player.getUUID());
			crew.snapTo(pos.x, pos.y, pos.z, tank.getYRot(), 0.0f);
			crew.setDebtor(player.getUUID());
			crew.arm();
			level.addFreshEntity(tank);
			level.addFreshEntity(crew);
			crew.startRiding(tank, true, true);
			sent += 2;
		}
		for (int i = 0; i < golems; i++) {
			Vec3 pos = findSpawn(level, player, Banking.COLLECTOR_GOLEM);
			CollectorGolem golem = pos == null ? null : Banking.COLLECTOR_GOLEM.create(level, EntitySpawnReason.EVENT);
			if (golem == null) {
				continue;
			}
			golem.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0f, 0.0f);
			golem.setDebtor(player.getUUID());
			level.addFreshEntity(golem);
			sent++;
		}
		if (sent > 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN, SoundSource.HOSTILE, 64.0f, 0.8f);
		}
		return sent;
	}

	/** A spot on solid ground some way from the player, with room for the entity, or null if none turned up. */
	public static @Nullable Vec3 findSpawn(ServerLevel level, LivingEntity player, EntityType<?> type) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 24; attempt++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double distance = Mth.nextDouble(random, SPAWN_MIN_DISTANCE, SPAWN_MAX_DISTANCE);
			int x = Mth.floor(player.getX() + Mth.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Mth.sin(angle) * distance);
			Vec3 spot = groundAt(level, type, x, Mth.floor(player.getY()), z);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Searches a column around {@code y} for ground with room above it, so it works underground and in the Nether too. */
	private static @Nullable Vec3 groundAt(ServerLevel level, EntityType<?> type, int x, int y, int z) {
		BlockPos column = new BlockPos(x, y, z);
		if (!level.hasChunkAt(column)) {
			return null;
		}
		for (int dy = 0; dy <= 12; dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos pos = column.above(dy * sign);
				BlockPos below = pos.below();
				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
						&& level.getFluidState(pos).isEmpty()) {
					AABB box = type.getSpawnAABB(x + 0.5, pos.getY(), z + 0.5);
					if (level.noCollision(box)) {
						return new Vec3(x + 0.5, pos.getY(), z + 0.5);
					}
				}
				if (dy == 0) {
					break;
				}
			}
		}
		return null;
	}

	private static void updateBar(ServerPlayer player, int owed, int wave, int alive, int nextWave) {
		ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(), Component.empty(),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
		bar.addPlayer(player);
		bar.setName(Component.translatable("event.mymod.debt_collection", owed, wave));
		// Like a raid's bar: the collectors still standing; between waves, it fills up towards the next one.
		if (alive > 0) {
			bar.setProgress(Mth.clamp(alive / (float) Math.max(alive, WAVE_SIZE.getOrDefault(player.getUUID(), alive)), 0.0f, 1.0f));
		} else {
			bar.setProgress(Mth.clamp(1.0f - nextWave / (float) WAVE_TICKS, 0.0f, 1.0f));
		}
	}

	private static void endBar(ServerPlayer player) {
		ServerBossEvent bar = BARS.remove(player.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		WAVE_SIZE.remove(player.getUUID());
	}
}
