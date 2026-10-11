package com.jheinz.mymod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

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

	public static void initialize() {
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
