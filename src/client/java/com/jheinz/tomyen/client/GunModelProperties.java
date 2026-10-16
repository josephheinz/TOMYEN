package com.jheinz.tomyen.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.lmg.MachineGunItem;
import com.jheinz.tomyen.shotgun.ShotgunAction;
import com.jheinz.tomyen.shotgun.Shotguns;

/**
 * Item model properties for the M249 and the shotguns:
 * <ul>
 * <li>tomyen:muzzle_flash, 0 or the flash variant (1-3) for a moment after the gun fires: the local player's shots
 * from the client's own recoil, a shotgun's from the action on its stack, and anyone else's gun from its round
 * count dropping;</li>
 * <li>tomyen:bipod, how far the holder's M249 bipod is swung down (0 to 3), easing as they crouch and stand;</li>
 * <li>tomyen:loaded_rounds, the rounds left in the inserted magazine.</li>
 * </ul>
 */
public final class GunModelProperties {
	private static final long FLASH_MILLIS = 55;
	private static final float BIPOD_MILLIS = 280.0f;

	/** For other holders: their gun's last round count, and when it last went down. */
	private static final Map<LivingEntity, long[]> SEEN_ROUNDS = new WeakHashMap<>();
	/** For each holder: how far down the bipod is (0 to 1), and when that was worked out. */
	private static final Map<LivingEntity, float[]> BIPODS = new WeakHashMap<>();

	private GunModelProperties() {
	}

	public static void register() {
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("muzzle_flash"), MuzzleFlash.MAP_CODEC);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("bipod"), Bipod.MAP_CODEC);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("loaded_rounds"), LoadedRounds.MAP_CODEC);
	}

	private static int rounds(ItemStack stack) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		return loaded == null ? 0 : loaded.rounds();
	}

	private static float flash(ItemStack stack, ItemOwner owner) {
		if (!(owner instanceof LivingEntity holder) || holder.getMainHandItem() != stack) {
			return 0.0f;
		}
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		long shot;
		ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
		if (holder == minecraft.player) {
			shot = GunRecoil.lastShotMillis();
		} else if (action != null && (action.kind() == ShotgunAction.Kind.PUMP || action.kind() == ShotgunAction.Kind.CYCLE)
				&& !action.chambering() && minecraft.level != null) {
			double ticksAgo = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false) - action.start();
			shot = now - (long) (ticksAgo * 50.0);
		} else {
			long[] seen = SEEN_ROUNDS.computeIfAbsent(holder, h -> new long[] {rounds(stack), Long.MIN_VALUE / 2});
			int rounds = rounds(stack);
			if (rounds < seen[0]) {
				seen[1] = now;
			}
			seen[0] = rounds;
			shot = seen[1];
		}
		long age = now - shot;
		return age >= 0 && age < FLASH_MILLIS ? 1 + Math.floorMod(shot / 7, 3) : 0.0f;
	}

	private static float bipod(ItemStack stack, ItemOwner owner) {
		if (!(owner instanceof LivingEntity holder) || holder.getMainHandItem() != stack) {
			return 0.0f;
		}
		long now = Util.getMillis();
		float[] state = BIPODS.computeIfAbsent(holder, h -> new float[] {0.0f, now});
		float step = Math.min(now - (long) state[1], 100L) / BIPOD_MILLIS;
		state[0] = Math.clamp(state[0] + (MachineGunItem.braced(holder) ? step : -step), 0.0f, 1.0f);
		state[1] = now;
		return state[0] * 3.0f;
	}

	public record MuzzleFlash() implements RangeSelectItemModelProperty {
		public static final MapCodec<MuzzleFlash> MAP_CODEC = MapCodec.unit(new MuzzleFlash());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			return flash(stack, owner);
		}

		@Override
		public MapCodec<MuzzleFlash> type() {
			return MAP_CODEC;
		}
	}

	public record Bipod() implements RangeSelectItemModelProperty {
		public static final MapCodec<Bipod> MAP_CODEC = MapCodec.unit(new Bipod());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			return bipod(stack, owner);
		}

		@Override
		public MapCodec<Bipod> type() {
			return MAP_CODEC;
		}
	}

	public record LoadedRounds() implements RangeSelectItemModelProperty {
		public static final MapCodec<LoadedRounds> MAP_CODEC = MapCodec.unit(new LoadedRounds());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			return rounds(stack);
		}

		@Override
		public MapCodec<LoadedRounds> type() {
			return MAP_CODEC;
		}
	}
}
