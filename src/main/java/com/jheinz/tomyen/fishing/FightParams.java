package com.jheinz.tomyen.fishing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the client needs to play one fight, sent when it starts: which minigame, how hard, the seed its fish
 * moves by, the rod's numbers, and the duration limits the server will hold the result to. The minigame's timings
 * live here too, so the server's {@link #minTicks} (the fastest a fair player could win) and the client's game agree.
 *
 * <p>The fish's weight is rolled before the fight, so the fight fits the fish ({@link FightStrength}): {@code length}
 * stretches the catch bar's fill, the strikes needed and the mash timer, and {@code power} speeds the fish up, narrows
 * the zones and strengthens its pulls. A trophy is also a difficulty step harder ({@link FishingFights#start}), and the
 * HUD calls it "Big one!". The rod still counts: reel speed fills the bar faster, line and hook give more grace.
 *
 * @param shown the species, when the rod's Fish Finder has already named it; otherwise empty (the HUD shows "?")
 * @param trophy the fish on the line is trophy class
 * @param power how hard the fish pulls, 0 to 1 ({@link FightStrength#power})
 * @param length the fight's length multiplier ({@link FightStrength#length})
 * @param stamina the fish tires partway through and surges near the end
 */
public record FightParams(int id, FishingGame game, FishBehavior behavior, int difficulty, long seed, float zone, float speed,
		float escape, float line, float reel, float assist, int minTicks, int maxTicks, ItemStack shown, boolean trophy, float power,
		float length, boolean stamina) {
	/** How much faster the strongest fish moves than the weakest, and how much its zones shrink. */
	private static final float POWER_SPEED = 0.35f;
	private static final float POWER_ZONE = 0.15f;

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
				buf.writeBoolean(params.trophy);
				buf.writeFloat(params.power);
				buf.writeFloat(params.length);
				buf.writeBoolean(params.stamina);
			},
			buf -> new FightParams(buf.readVarInt(), FishingGame.values()[buf.readVarInt()], FishBehavior.values()[buf.readVarInt()],
					buf.readVarInt(), buf.readLong(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
					buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

	public static FightParams create(int id, FishBehavior behavior, int difficulty, long seed, RodStats stats, ItemStack shown,
			FightStrength strength, boolean trophy) {
		int d = Mth.clamp(difficulty, 1, 5);
		FishingGame game = behavior.game();
		float power = strength.power();
		float length = strength.length();
		float reel = stats.reel();
		float speed = Mth.clamp(stats.speed() * (1.0f + POWER_SPEED * power), 0.5f, 2.0f);
		float zone = Math.max(0.5f, stats.zone() * (1.0f - POWER_ZONE * power));
		int min;
		int max;
		switch (game) {
			case TENSION -> {
				int fill = Mth.ceil(tensionFillTicks(d, length) / reel);
				min = INTRO_TICKS + fill - 2;
				max = INTRO_TICKS + fill * 7 / 2 + 80;
			}
			case STRIKES -> {
				int hits = strikeHits(d, length);
				min = INTRO_TICKS + (hits - 1) * STRIKE_LOCKOUT - 2;
				max = INTRO_TICKS + 20 * (8 + 2 * hits);
			}
			default -> {
				int duration = mashTicks(d, length);
				min = INTRO_TICKS + duration - 2;
				max = INTRO_TICKS + duration + 20;
			}
		}
		return new FightParams(id, game, behavior, d, seed, zone, speed, stats.escape(), stats.line(), reel, stats.assist(),
				Math.max(0, min), max, shown, trophy, power, length, strength.staminaPhases());
	}

	/** Tension: ticks of needle-in-the-green to fill the catch bar at reel speed 1. */
	public static int tensionFillTicks(int difficulty, float length) {
		return Math.round((50 + 25 * difficulty) * length);
	}

	/** Strikes: hits needed. */
	public static int strikeHits(int difficulty, float length) {
		return Math.max(2, Math.round((3 + (difficulty + 1) / 2) * length));
	}

	/** Strikes: misses allowed before it gets away (a barbed hook holds on for more, and a long fight gives a few more). */
	public static int strikeMisses(int difficulty, float escape, float length) {
		return Math.max(1, Math.round((4 - difficulty / 2) * (float) Math.sqrt(Math.max(1.0f, length)))) + Math.round(escape * 2.0f);
	}

	/** Mash: how long the meter has to stay up. */
	public static int mashTicks(int difficulty, float length) {
		return Math.round((60 + 20 * difficulty) * length);
	}
}
