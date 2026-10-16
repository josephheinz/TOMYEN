package com.jheinz.tomyen.client.minigun;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.hogfire.Hogfire;
import com.jheinz.tomyen.minigun.MinigunItem;
import com.jheinz.tomyen.minigun.MinigunState;
import com.jheinz.tomyen.minigun.Miniguns;

/**
 * The Minigun's client side, for the handheld gun and the Hogfire's gun mount alike:
 * <ul>
 * <li>item model properties: tomyen:minigun_barrels, which of the barrel group's six frames to draw (the barrels turn
 * at a rate set by the gun's spin, kept per holder and integrated over real time so they turn smoothly);
 * tomyen:minigun_heat, the heat (1 once overheated), past which the barrels glow; tomyen:minigun_flash, 0 or a muzzle
 * flash variant (1-3) while it fires;</li>
 * <li>the sounds: the motor and fire loops ({@link MinigunSound}) on every gun in earshot that's turning, the
 * spin-up whine as one starts and the spin-down as one's let go;</li>
 * <li>the local player's recoil, a light kick per round (the suit takes most of it).</li>
 * </ul>
 */
public final class MinigunClient {
	/** How fast the barrels are drawn turning at full speed. Slower than the real thing, which would strobe. */
	private static final float DEGREES_PER_SECOND = 540.0f;
	/** The barrel frames are this far apart; the barrels repeat every six. */
	private static final float FRAME_DEGREES = 10.0f;
	private static final int FRAMES = 6;
	private static final double RANGE = MinigunSound.RANGE;

	/** For each holder (or a holderless stack): how far round the barrels are, in degrees, and when that was. */
	private static final Map<Object, double[]> BARRELS = new WeakHashMap<>();
	private static final Map<Integer, Tracked> TRACKED = new HashMap<>();

	/** A gun being listened to: its two loops, its last spin, and whether its spin-down has been played. */
	private static final class Tracked {
		MinigunSound spin;
		MinigunSound fire;
		float lastSpin;
		boolean windingDown;
	}

	private MinigunClient() {
	}

	public static void initialize() {
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("minigun_barrels"), Barrels.MAP_CODEC);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("minigun_heat"), Heat.MAP_CODEC);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("minigun_flash"), Flash.MAP_CODEC);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				TRACKED.clear();
			} else if (!client.isPaused()) {
				tick(client, client.level);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRACKED.clear());
	}

	/** The state of the gun a source carries: a player's held Minigun, or a Hogfire's mounted one. */
	public static MinigunState state(Entity source) {
		if (source instanceof Hogfire hogfire) {
			return hogfire.hasGunMount() ? hogfire.gunState() : MinigunState.IDLE;
		}
		if (source instanceof Player player && player.getMainHandItem().getItem() instanceof MinigunItem) {
			return MinigunItem.state(player.getMainHandItem());
		}
		return MinigunState.IDLE;
	}

	/** 1 at the listener, falling to 0 at {@code range} blocks away. */
	static float nearness(Entity source, double range) {
		Player listener = Minecraft.getInstance().player;
		double distance = listener == null ? 0.0 : listener.distanceTo(source);
		return (float) Mth.clamp(1.0 - distance / range, 0.0, 1.0);
	}

	private static MinigunState state(ItemStack stack, ItemOwner owner) {
		if (owner instanceof Hogfire hogfire) {
			return hogfire.hasGunMount() ? hogfire.gunState() : MinigunState.IDLE;
		}
		return stack.getItem() instanceof MinigunItem ? MinigunItem.state(stack) : MinigunState.IDLE;
	}

	private static float barrelFrame(ItemStack stack, ItemOwner owner) {
		long now = Util.getMillis();
		double[] barrels = BARRELS.computeIfAbsent(owner != null ? owner : stack, key -> new double[] {0.0, now});
		double seconds = Math.min(now - (long) barrels[1], 100L) / 1000.0;
		barrels[0] = (barrels[0] + state(stack, owner).spin() * DEGREES_PER_SECOND * seconds) % (FRAME_DEGREES * FRAMES);
		barrels[1] = now;
		return (int) (barrels[0] / FRAME_DEGREES);
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		Player local = minecraft.player;
		if (local != null && local.getMainHandItem().getItem() instanceof MinigunItem gun && MinigunItem.state(local.getMainHandItem()).firing()) {
			GunRecoil.queue(local, 1, gun.stats(local.getMainHandItem(), local));
		}
		Set<Integer> heard = new HashSet<>();
		for (AbstractClientPlayer player : level.players()) {
			if (player.getMainHandItem().getItem() instanceof MinigunItem && nearness(player, RANGE) > 0.0f) {
				listen(minecraft, player, heard);
			}
		}
		if (local != null) {
			for (Hogfire hogfire : level.getEntitiesOfClass(Hogfire.class, new AABB(local.blockPosition()).inflate(RANGE), Hogfire::hasGunMount)) {
				listen(minecraft, hogfire, heard);
			}
		}
		TRACKED.keySet().retainAll(heard);
	}

	/** Starts and stops a gun's sounds as its spin and firing change. */
	private static void listen(Minecraft minecraft, Entity source, Set<Integer> heard) {
		heard.add(source.getId());
		MinigunState state = state(source);
		Tracked tracked = TRACKED.computeIfAbsent(source.getId(), id -> new Tracked());
		if (state.spin() > 0.0f && (tracked.spin == null || tracked.spin.isStopped())) {
			tracked.spin = new MinigunSound(source, false);
			minecraft.getSoundManager().play(tracked.spin);
		}
		if (state.firing() && (tracked.fire == null || tracked.fire.isStopped())) {
			tracked.fire = new MinigunSound(source, true);
			minecraft.getSoundManager().play(tracked.fire);
		}
		if (state.spin() > tracked.lastSpin) {
			if (tracked.lastSpin == 0.0f) {
				oneShot(minecraft, source, Miniguns.SPIN_UP);
			}
			tracked.windingDown = false;
		} else if (state.spin() < tracked.lastSpin && !tracked.windingDown && tracked.lastSpin > 0.3f) {
			oneShot(minecraft, source, Miniguns.SPIN_DOWN);
			tracked.windingDown = true;
		}
		tracked.lastSpin = state.spin();
	}

	private static void oneShot(Minecraft minecraft, Entity source, SoundEvent sound) {
		float near = nearness(source, RANGE);
		if (near > 0.0f) {
			minecraft.getSoundManager().play(new SimpleSoundInstance(sound, source instanceof Player ? SoundSource.PLAYERS : SoundSource.NEUTRAL,
					near, 1.0f, SoundInstance.createUnseededRandom(), source.getX(), source.getEyeY(), source.getZ()));
		}
	}

	public record Barrels() implements RangeSelectItemModelProperty {
		public static final MapCodec<Barrels> MAP_CODEC = MapCodec.unit(new Barrels());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			return barrelFrame(stack, owner);
		}

		@Override
		public MapCodec<Barrels> type() {
			return MAP_CODEC;
		}
	}

	public record Heat() implements RangeSelectItemModelProperty {
		public static final MapCodec<Heat> MAP_CODEC = MapCodec.unit(new Heat());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			MinigunState state = state(stack, owner);
			return state.overheated() ? 1.0f : state.heat();
		}

		@Override
		public MapCodec<Heat> type() {
			return MAP_CODEC;
		}
	}

	public record Flash() implements RangeSelectItemModelProperty {
		public static final MapCodec<Flash> MAP_CODEC = MapCodec.unit(new Flash());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			return state(stack, owner).firing() ? 1 + (Util.getMillis() / 40) % 3 : 0.0f;
		}

		@Override
		public MapCodec<Flash> type() {
			return MAP_CODEC;
		}
	}
}
