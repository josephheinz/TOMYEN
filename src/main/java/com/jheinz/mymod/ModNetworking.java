package com.jheinz.mymod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.holy.CrucifixItem;
import com.jheinz.mymod.neuralink.NeuralNetworking;

/**
 * Attacking with nothing targeted never reaches the server in vanilla, so the client reports
 * these attack-key actions itself.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	/** Sent repeatedly while the player sneaks and holds attack with a magazine. */
	public record PackMagazine() implements CustomPacketPayload {
		public static final PackMagazine INSTANCE = new PackMagazine();
		public static final Type<PackMagazine> TYPE = new Type<>(MyMod.id("pack_magazine"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PackMagazine> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<PackMagazine> type() {
			return TYPE;
		}
	}

	/** Sent when the player punches while holding a gun. */
	public record Reload() implements CustomPacketPayload {
		public static final Reload INSTANCE = new Reload();
		public static final Type<Reload> TYPE = new Type<>(MyMod.id("reload"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reload> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Reload> type() {
			return TYPE;
		}
	}

	/** Sent when the player presses the fire-mode key while holding a gun. */
	public record CycleFireMode() implements CustomPacketPayload {
		public static final CycleFireMode INSTANCE = new CycleFireMode();
		public static final Type<CycleFireMode> TYPE = new Type<>(MyMod.id("cycle_fire_mode"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CycleFireMode> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<CycleFireMode> type() {
			return TYPE;
		}
	}

	/** Sent when the player punches while holding a crucifix. */
	public record CrucifixPunch() implements CustomPacketPayload {
		public static final CrucifixPunch INSTANCE = new CrucifixPunch();
		public static final Type<CrucifixPunch> TYPE = new Type<>(MyMod.id("crucifix_punch"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CrucifixPunch> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<CrucifixPunch> type() {
			return TYPE;
		}
	}

	/** Sent to everyone nearby when a crucifix fires a beam of light, so they can draw it. */
	public record LightBeam(int shooterId, Vec3 from, Vec3 to, float power) implements CustomPacketPayload {
		public static final Type<LightBeam> TYPE = new Type<>(MyMod.id("light_beam"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LightBeam> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LightBeam::shooterId,
				Vec3.STREAM_CODEC, LightBeam::from,
				Vec3.STREAM_CODEC, LightBeam::to,
				ByteBufCodecs.FLOAT, LightBeam::power,
				LightBeam::new);

		@Override
		public Type<LightBeam> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		NeuralNetworking.initialize();
		PayloadTypeRegistry.serverboundPlay().register(CrucifixPunch.TYPE, CrucifixPunch.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LightBeam.TYPE, LightBeam.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CrucifixPunch.TYPE, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			if (held.getItem() instanceof CrucifixItem crucifix && !context.player().isUsingItem()) {
				crucifix.punch((ServerLevel) context.player().level(), context.player(), held);
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(PackMagazine.TYPE, PackMagazine.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Reload.TYPE, Reload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CycleFireMode.TYPE, CycleFireMode.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(PackMagazine.TYPE, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			if (context.player().isShiftKeyDown() && held.getItem() instanceof MagazineItem magazine) {
				magazine.loadRound(context.player(), held);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(Reload.TYPE, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			if (held.getItem() instanceof GunItem gun && !context.player().getCooldowns().isOnCooldown(held)) {
				gun.reload(context.player(), held);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(CycleFireMode.TYPE, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			if (held.getItem() instanceof GunItem gun) {
				gun.cycleFireMode(context.player(), held);
			}
		});
	}
}
