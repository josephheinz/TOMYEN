package com.jheinz.mymod.fishing;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.world.entity.player.Player;

import com.jheinz.mymod.MyMod;

/**
 * On a player: their heaviest catch of each species (by species id), kept through death. Only weighed catches on a mod
 * rod count; {@code /mymod fishing records} lists them.
 */
public record FishingRecords(Map<String, Best> bests) {
	public static final FishingRecords EMPTY = new FishingRecords(Map.of());

	/** One personal best: the weight, and the day it was caught. */
	public record Best(float weight, long day) {
		public static final Codec<Best> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("weight_lb").forGetter(Best::weight),
				Codec.LONG.fieldOf("day").forGetter(Best::day)).apply(instance, Best::new));
	}

	public static final Codec<FishingRecords> CODEC = Codec.unboundedMap(Codec.STRING, Best.CODEC)
			.xmap(FishingRecords::new, FishingRecords::bests);

	public static final AttachmentType<FishingRecords> ATTACHMENT = AttachmentRegistry.<FishingRecords>builder()
			.persistent(CODEC)
			.copyOnDeath()
			.initializer(() -> EMPTY)
			.buildAndRegister(MyMod.id("fishing_records"));

	public static FishingRecords of(Player player) {
		return player.getAttachedOrElse(ATTACHMENT, EMPTY);
	}

	/**
	 * Records {@code weight} for {@code species} if it beats the player's best. Returns the previous best's weight
	 * when it was beaten, 0 when this was the first of its species, or -1 when it wasn't a record.
	 */
	public static float submit(Player player, String species, float weight, long day) {
		FishingRecords records = of(player);
		Best old = records.bests.get(species);
		if (old != null && old.weight >= weight) {
			return -1.0f;
		}
		Map<String, Best> bests = new HashMap<>(records.bests);
		bests.put(species, new Best(weight, day));
		player.setAttached(ATTACHMENT, new FishingRecords(Map.copyOf(bests)));
		return old == null ? 0.0f : old.weight;
	}

	static void initialize() {
		// Registration happens in the static initializer.
	}
}
