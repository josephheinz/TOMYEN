package com.jheinz.mymod.client.drill;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.drill.DrillItem;

/**
 * How far each holder's drill has turned, and the item model property mymod:drill_spin, the frame of the spinning
 * parts to show (tools/gen_drills.py). While the holder drills (the local player holding attack; anyone else swinging,
 * which digging does every tick) and the drill runs, it spins up to speed in a quarter of a second, and coasts down over
 * half a second when they stop. Every drill has {@link #FRAMES} frames: the Hand Drill's crank and bit go round once over
 * them, twice a second; a motor drill's bit turns half a turn over them (its symmetry), five times a second.
 */
public final class DrillSpin {
	public static final int FRAMES = 12;
	private static final float HAND_FRAMES_PER_SECOND = 24.0f;
	private static final float MOTOR_FRAMES_PER_SECOND = 60.0f;
	private static final float SPIN_UP_PER_SECOND = 4.0f;
	private static final float SPIN_DOWN_PER_SECOND = 2.0f;
	private static final Map<LivingEntity, Spin> SPINS = new WeakHashMap<>();

	private static final class Spin {
		/** In frames. */
		private float angle;
		/** 0 (still) to 1 (full speed). */
		private float speed;
		private long millis = Util.getMillis();
		/** Whether the bit is against rock, which the motor labours under. */
		private boolean biting;
	}

	private DrillSpin() {
	}

	public static void register() {
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("drill_spin"), Property.MAP_CODEC);
	}

	private static boolean handCranked(ItemStack stack) {
		return stack.getItem() instanceof DrillItem drill && drill.engine() == DrillItem.Engine.HAND_CRANK;
	}

	/** Whether the holder has the trigger down. */
	public static boolean drilling(LivingEntity holder) {
		Minecraft minecraft = Minecraft.getInstance();
		if (holder == minecraft.player) {
			return minecraft.gui.screen() == null && minecraft.options.keyAttack.isDown();
		}
		return holder.isSwinging();
	}

	/** Brings the holder's spin up to now (any number of calls a frame) and returns its speed, 0 to 1. */
	public static float update(LivingEntity holder) {
		Spin spin = SPINS.computeIfAbsent(holder, h -> new Spin());
		long now = Util.getMillis();
		float seconds = Math.min(now - spin.millis, 200L) / 1000.0f;
		spin.millis = now;
		ItemStack stack = holder.getMainHandItem();
		boolean running = stack.getItem() instanceof DrillItem drill && drilling(holder)
				&& (drill.powered(stack) || holder instanceof Player player && player.hasInfiniteMaterials());
		spin.speed = Mth.clamp(spin.speed + (running ? SPIN_UP_PER_SECOND : -SPIN_DOWN_PER_SECOND) * seconds, 0.0f, 1.0f);
		float rate = handCranked(stack) ? HAND_FRAMES_PER_SECOND : MOTOR_FRAMES_PER_SECOND;
		spin.angle = (spin.angle + spin.speed * rate * seconds) % FRAMES;
		return spin.speed;
	}

	public static float speed(LivingEntity holder) {
		Spin spin = SPINS.get(holder);
		return spin == null ? 0.0f : spin.speed;
	}

	public static boolean biting(LivingEntity holder) {
		Spin spin = SPINS.get(holder);
		return spin != null && spin.biting;
	}

	static void setBiting(LivingEntity holder, boolean biting) {
		SPINS.computeIfAbsent(holder, h -> new Spin()).biting = biting;
	}

	/** The frame of the drill's spinning parts to show in the holder's hands. */
	public static int frame(LivingEntity holder, ItemStack stack) {
		update(holder);
		return Mth.floor(SPINS.get(holder).angle) % FRAMES;
	}

	public static void clear() {
		SPINS.clear();
	}

	public record Property() implements RangeSelectItemModelProperty {
		public static final MapCodec<Property> MAP_CODEC = MapCodec.unit(new Property());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			if (!(owner instanceof LivingEntity holder) || holder.getMainHandItem().getItem() != stack.getItem()) {
				return 0.0f;
			}
			return frame(holder, stack);
		}

		@Override
		public MapCodec<Property> type() {
			return MAP_CODEC;
		}
	}
}
