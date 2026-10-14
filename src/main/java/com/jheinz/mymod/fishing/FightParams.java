package com.jheinz.mymod.fishing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the client needs to play one fight, sent when it starts: which minigame, how hard, the seed its fish
 * moves by, the rod's numbers, and the duration limits the server will hold the result to. The minigame's timings
 * live here too, so the server's {@link #minTicks} (the fastest a fair player could win) and the client's game agree.
 *
 * @param shown the species, when the rod's Fish Finder has already named it; otherwise empty (the HUD shows "?")
 */
public record FightParams(int id, FishingGame game, FishBehavior behavior, int difficulty, long seed, float zone, float speed,
		float escape, float line, float reel, float assist, int minTicks, int maxTicks, ItemStack shown) {
	/** The "Hooked!" moment before the fish starts fighting, while the panel slides in. */
	public static final int INTRO_TICKS = 12;
	/** Strikes: after a hit the zone darts off, and the next press can't land for this long. */
	public static final int STRIKE_LOCKOUT = 6;
	public static final int MISS_LOCKOUT = 4;

	public static final StreamCodec<RegistryFriendlyByteBuf, FightParams> STREAM_CODEC = StreamCodec.of(
			(buf, params) -> {
				buf.writeVarInt(params.id);
				buf.writeVarInt(params.game.ordinal());
				buf.writeVarInt(params.behavior.ordinal());
				buf.writeVarInt(params.difficulty);
				buf.writeLong(params.seed);
				buf.writeFloat(params.zone);
				buf.writeFloat(params.speed);
				buf.writeFloat(params.escape);
				buf.writeFloat(params.line);
				buf.writeFloat(params.reel);
				buf.writeFloat(params.assist);
				buf.writeVarInt(params.minTicks);
				buf.writeVarInt(params.maxTicks);
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, params.shown);
			},
			buf -> new FightParams(buf.readVarInt(), FishingGame.values()[buf.readVarInt()], FishBehavior.values()[buf.readVarInt()],
					buf.readVarInt(), buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

	public static FightParams create(int id, FishBehavior behavior, int difficulty, long seed, RodStats stats, ItemStack shown) {
		int d = Mth.clamp(difficulty, 1, 5);
		FishingGame game = behavior.game();
		float reel = stats.reel();
		int min;
		int max;
		switch (game) {
			case TENSION -> {
				int fill = Mth.ceil(tensionFillTicks(d) / reel);
				min = INTRO_TICKS + fill - 2;
				max = INTRO_TICKS + fill * 7 / 2 + 60;
			}
			case STRIKES -> {
				int hits = strikeHits(d);
				min = INTRO_TICKS + (hits - 1) * STRIKE_LOCKOUT - 2;
				max = INTRO_TICKS + 20 * (8 + 2 * hits);
			}
			default -> {
				int duration = mashTicks(d);
				min = INTRO_TICKS + duration - 2;
				max = INTRO_TICKS + duration + 20;
			}
		}
		return new FightParams(id, game, behavior, d, seed, stats.zone(), stats.speed(), stats.escape(), stats.line(), reel,
				stats.assist(), Math.max(0, min), max, shown);
	}

	/** Tension: ticks of needle-in-the-green to fill the catch bar at reel speed 1. */
	public static int tensionFillTicks(int difficulty) {
		return 70 + 25 * difficulty;
	}

	/** Strikes: hits needed. */
	public static int strikeHits(int difficulty) {
		return 3 + (difficulty + 1) / 2;
	}

	/** Strikes: misses allowed before it gets away (a barbed hook holds on for more). */
	public static int strikeMisses(int difficulty, float escape) {
		return Math.max(1, 4 - difficulty / 2) + Math.round(escape * 2.0f);
	}

	/** Mash: how long the meter has to stay up. */
	public static int mashTicks(int difficulty) {
		return 100 + 20 * difficulty;
	}
}
