package com.jheinz.tomyen.client.trombone;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.trombone.TromboneItem;
import com.jheinz.tomyen.trombone.TromboneMusic;
import com.jheinz.tomyen.trombone.TromboneNotes;

/**
 * Where each trombonist's slide is, on the client, and the item model property tomyen:trombone_slide: the slide frame to
 * draw (tools/gen_trombone.py), 0 fully in to {@link TromboneNotes#TOP} fully out. While a player blows, the slide
 * runs to the note their look picks, quickly but not instantly (so a big leap visibly slides); at rest it's drawn in.
 * Also how far the instrument is raised to the lips in first person, 0 to 1.
 */
public final class TromboneSlide {
	/** Seconds for the slide to cover most of the way to a new note. */
	private static final float SLIDE_SECONDS = 0.045f;
	private static final float RAISE_SECONDS = 0.08f;
	private static final Map<Entity, State> STATES = new WeakHashMap<>();

	private static final class State {
		/** The note position the slide is at, as a fractional scale degree. */
		float position = TromboneNotes.TOP;
		float raise;
		long millis = Util.getMillis();
	}

	private TromboneSlide() {
	}

	public static void register() {
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("trombone_slide"), Property.MAP_CODEC);
	}

	/** Brings the holder's slide up to now (any number of calls a frame are fine). */
	private static State update(Player holder) {
		State state = STATES.computeIfAbsent(holder, h -> new State());
		long now = Util.getMillis();
		float seconds = Math.min(now - state.millis, 200L) / 1000.0f;
		if (seconds > 0.0f) {
			state.millis = now;
			boolean playing = TromboneMusic.playing(holder);
			float target = playing ? TromboneNotes.degree(holder.getXRot()) : TromboneNotes.TOP;
			state.position += (target - state.position) * (1.0f - (float) Math.exp(-seconds / SLIDE_SECONDS));
			state.raise = Mth.approach(state.raise, playing ? 1.0f : 0.0f, seconds / RAISE_SECONDS * 0.6f);
		}
		return state;
	}

	/** The slide frame to draw for this player's trombone. */
	public static int frame(Player holder) {
		return Mth.clamp(Math.round(TromboneNotes.TOP - update(holder).position), 0, TromboneNotes.TOP);
	}

	/** How far the trombone is raised to the lips, 0 (held lower, resting) to 1 (playing). */
	public static float raise(Player holder) {
		return update(holder).raise;
	}

	public static @Nullable Player holder(@Nullable ItemOwner owner) {
		return owner != null && owner.asLivingEntity() instanceof Player player ? player : null;
	}

	public static void clear() {
		STATES.clear();
	}

	public record Property() implements RangeSelectItemModelProperty {
		public static final MapCodec<Property> MAP_CODEC = MapCodec.unit(new Property());

		@Override
		public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
			Player holder = holder(owner);
			if (holder == null || !(holder.getMainHandItem().getItem() instanceof TromboneItem)) {
				return 0.0f;
			}
			return frame(holder);
		}

		@Override
		public MapCodec<Property> type() {
			return MAP_CODEC;
		}
	}
}
