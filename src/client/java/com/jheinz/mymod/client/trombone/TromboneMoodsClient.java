package com.jheinz.mymod.client.trombone;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import com.jheinz.mymod.trombone.TromboneMoods;
import com.jheinz.mymod.trombone.TrombonePayloads;

/**
 * Which mobs near this client are under a Trombone song's spell, as the server tells it ({@link TrombonePayloads.Moods}),
 * and how far into it each one is drawn: a spell eases in over half a second and out over the last second.
 */
public final class TromboneMoodsClient {
	private static final float EASE_IN = 10.0f;
	private static final float EASE_OUT = 20.0f;

	private record Spell(TromboneMoods.Mood mood, long from, long until) {
	}

	private static final Map<Integer, Spell> SPELLS = new HashMap<>();

	private TromboneMoodsClient() {
	}

	public static void receive(TrombonePayloads.Moods payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || payload.mood() < 0 || payload.mood() >= TromboneMoods.Mood.values().length) {
			return;
		}
		long now = minecraft.level.getGameTime();
		TromboneMoods.Mood mood = TromboneMoods.Mood.values()[payload.mood()];
		for (int id : payload.ids()) {
			SPELLS.put(id, new Spell(mood, now, now + payload.ticks()));
		}
	}

	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			SPELLS.clear();
			return;
		}
		long now = minecraft.level.getGameTime();
		SPELLS.values().removeIf(spell -> spell.until <= now);
	}

	private static float amount(Entity entity, TromboneMoods.Mood mood, float partialTicks) {
		Spell spell = SPELLS.get(entity.getId());
		if (spell == null || spell.mood != mood) {
			return 0.0f;
		}
		float now = entity.level().getGameTime() + partialTicks;
		float in = (now - spell.from) / EASE_IN;
		float out = (spell.until - now) / EASE_OUT;
		float t = Mth.clamp(Math.min(in, out), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	public static float droop(Entity entity, float partialTicks) {
		return SPELLS.isEmpty() ? 0.0f : amount(entity, TromboneMoods.Mood.SAD, partialTicks);
	}

	public static float sleep(Entity entity, float partialTicks) {
		return SPELLS.isEmpty() ? 0.0f : amount(entity, TromboneMoods.Mood.LULLED, partialTicks);
	}

	public static void clear() {
		SPELLS.clear();
	}
}
