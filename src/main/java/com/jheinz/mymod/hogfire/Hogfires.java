package com.jheinz.mymod.hogfire;

import java.util.function.Function;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import com.jheinz.mymod.MyMod;

/**
 * The Hogfire, a rocket-powered flying pig: its entity, the three pieces of gear that make one out of a pig (and the
 * gun mount that can go on it), its engine sound, and the packet its pilot flies it with.
 */
public final class Hogfires {
	/** A pig in flight gear. Tracked every tick, since it's fast. */
	public static final EntityType<Hogfire> HOGFIRE = entity("hogfire", EntityType.Builder.of(Hogfire::new, MobCategory.CREATURE)
			.sized(0.9f, 0.9f)
			.passengerAttachments(0.86875f)
			.clientTrackingRange(10)
			.updateInterval(1));

	public static final Item PIG_FLIGHT_HARNESS = gear("pig_flight_harness", Hogfire.HARNESS);
	public static final Item PIG_WINGS = gear("pig_wings", Hogfire.WINGS);
	public static final Item ROCKET_ENGINE = gear("rocket_engine", Hogfire.ENGINE);
	/** A Minigun on a saddle plate, fitted to the pig's shoulders any time after the harness (see Hogfire#fitGun). */
	public static final Item GUN_MOUNT = gunMount("hogfire_gun_mount");

	public static final SoundEvent ENGINE_SOUND = sound("hogfire_engine");

	private Hogfires() {
	}

	/**
	 * Sent every tick by a Hogfire's pilot: where they're looking (the pig steers towards it), and whether
	 * they're holding throttle up (forward), throttle down (back), the afterburner (jump) and the mounted gun's
	 * trigger (attack).
	 */
	public record Controls(float yaw, float pitch, boolean throttleUp, boolean throttleDown, boolean afterburner, boolean fire)
			implements CustomPacketPayload {
		public static final Controls NEUTRAL = new Controls(0.0f, 0.0f, false, false, false, false);
		public static final Type<Controls> TYPE = new Type<>(MyMod.id("hogfire_controls"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Controls> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Controls::yaw,
				ByteBufCodecs.FLOAT, Controls::pitch,
				ByteBufCodecs.BOOL, Controls::throttleUp,
				ByteBufCodecs.BOOL, Controls::throttleDown,
				ByteBufCodecs.BOOL, Controls::afterburner,
				ByteBufCodecs.BOOL, Controls::fire,
				Controls::new);

		@Override
		public Type<Controls> type() {
			return TYPE;
		}
	}

	private static Item gear(String name, int stage) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new HogfireGearItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON), stage));
	}

	private static Item gunMount(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new HogfireGunMountItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(HOGFIRE, Hogfire.createAttributes());
		PayloadTypeRegistry.serverboundPlay().register(Controls.TYPE, Controls.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Controls.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof Hogfire hogfire && hogfire.getFirstPassenger() == context.player()) {
				hogfire.setControls(payload);
			}
		});
	}
}
