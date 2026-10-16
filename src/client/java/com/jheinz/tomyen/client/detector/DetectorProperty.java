package com.jheinz.tomyen.client.detector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.detector.Detectors;

/**
 * Item model property {@code tomyen:detector}: what a detector in the local player's hand reads, for its sprite.
 * Anywhere else (dropped, in a frame, someone else's hand) it reads its resting value.
 * <ul>
 * <li>{@code "reading": "pendulum"}: the Prospector's Pendulum's bob, -1 (left) to 1 (right); rests at 0.</li>
 * <li>{@code "reading": "bearing"}: which of {@code sectors} slices round the holder the target lies in, as
 * {@code sector / sectors} (0 is ahead, then clockwise); -1 with no target. Meteorite Scanner and Dowsing Rods.</li>
 * <li>{@code "reading": "signal"}: the Metal Detector's signal, 0 to 1; the UFO Radar's state, 0 nothing, 0.5
 * contacts, 1 an abduction or landing.</li>
 * </ul>
 */
public record DetectorProperty(Reading reading, int sectors) implements RangeSelectItemModelProperty {
	public enum Reading implements StringRepresentable {
		PENDULUM("pendulum"), BEARING("bearing"), SIGNAL("signal");

		public static final Codec<Reading> CODEC = StringRepresentable.fromEnum(Reading::values);
		private final String name;

		Reading(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final MapCodec<DetectorProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Reading.CODEC.fieldOf("reading").forGetter(DetectorProperty::reading),
			Codec.intRange(1, 32).optionalFieldOf("sectors", 8).forGetter(DetectorProperty::sectors))
			.apply(i, DetectorProperty::new));

	@Override
	public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		boolean inHand = player != null && owner != null && owner.asLivingEntity() == player
				&& (player.getMainHandItem() == stack || player.getOffhandItem() == stack);
		if (!inHand) {
			return reading == Reading.BEARING ? -1.0f : 0.0f;
		}
		float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return switch (reading) {
			case PENDULUM -> PendulumClient.bob(partialTicks);
			case SIGNAL -> stack.is(Detectors.UFO_RADAR)
					? (UfoRadarClient.alert() != UfoRadarClient.NONE ? 1.0f : UfoRadarClient.contacts().isEmpty() ? 0.0f : 0.5f)
					: MetalDetectorClient.signal();
			case BEARING -> bearing(player, stack);
		};
	}

	private float bearing(LocalPlayer player, ItemStack stack) {
		BlockPos target = TrackerClient.target(player, stack.getItem());
		if (target == null) {
			return -1.0f;
		}
		float rel = DetectorClient.relativeYaw(player, target.getX() + 0.5, target.getZ() + 0.5);
		float slice = 360.0f / sectors;
		int sector = Math.floorMod((int) Math.floor((rel + slice / 2.0f) / slice), sectors);
		return (float) sector / sectors;
	}

	@Override
	public MapCodec<DetectorProperty> type() {
		return MAP_CODEC;
	}
}
