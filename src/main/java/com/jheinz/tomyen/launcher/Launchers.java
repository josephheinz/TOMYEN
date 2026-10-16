package com.jheinz.tomyen.launcher;

import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.attachment.AttachmentItem;
import com.jheinz.tomyen.attachment.AttachmentSlot;

/**
 * The RPG-7: the launcher, its two Weapon Station parts, PG-7 rockets (plain, homing and napalm), the homing guidance
 * module, the rocket projectile, and the state the launcher keeps on its stack.
 */
public final class Launchers {
	/** On a launcher: the rocket seated in its muzzle. Absent means it's empty. */
	public static final DataComponentType<Holder<Item>> LOADED_ROCKET =
			component("loaded_rocket", Item.CODEC, Item.STREAM_CODEC, true);
	/** On a launcher in hand: what it's doing (see {@link LauncherAction}). Not saved: it restarts each time it's drawn. */
	public static final DataComponentType<LauncherAction> LAUNCHER_ACTION =
			component("launcher_action", LauncherAction.CODEC, LauncherAction.STREAM_CODEC, false);
	/** Only on the copy of a rocket a flying projectile renders, so its model shows the fins out and the motor lit. */
	public static final DataComponentType<Unit> ROCKET_IN_FLIGHT = component("rocket_in_flight", Unit.CODEC, Unit.STREAM_CODEC, false);
	/** On a launcher: the kind of rocket last switched to (sneak and punch), which reloads take first from then on. */
	public static final DataComponentType<Holder<Item>> ROCKET_CHOICE =
			component("rocket_choice", Item.CODEC, Item.STREAM_CODEC, true);

	public static final SoundEvent RPG_LAUNCH = sound("rpg_launch");
	public static final SoundEvent LOCK_BEEP = sound("lock_beep");
	public static final SoundEvent LOCK_TONE = sound("lock_tone");

	public static final ResourceKey<DamageType> BACKBLAST = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("backblast"));

	public static final EntityType<PG7Rocket> ROCKET = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("pg7_rocket")),
			EntityType.Builder.<PG7Rocket>of(PG7Rocket::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.3f, 0.3f)
					.clientTrackingRange(10)
					// Every tick: it's fast, and a homing one turns.
					.updateInterval(1)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("pg7_rocket"))));

	/** Rockets don't stack. */
	public static final Item PG7_ROCKET = item("pg7_rocket", Item::new, new Item.Properties().stacksTo(1));
	/** A PG-7 with a seeker head: steers itself onto a target the launcher's guidance module has locked. */
	public static final Item PG7_HOMING_ROCKET = item("pg7_homing_rocket", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/**
	 * A PG-7 with an incendiary warhead: a smaller blast than the HEAT round, then a wide carpet of burning gel
	 * ({@link PG7Rocket#NAPALM_RADIUS} blocks) that sets everything in it alight with the Napalm effect.
	 */
	public static final Item PG7_NAPALM_ROCKET = item("pg7_napalm_rocket", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Every kind of rocket, in the order sneak-punching switches through them. */
	public static final List<Item> ROCKETS = List.of(PG7_ROCKET, PG7_HOMING_ROCKET, PG7_NAPALM_ROCKET);
	public static final Item RPG7_LAUNCH_TUBE = item("rpg7_launch_tube", Item::new, new Item.Properties().stacksTo(1));
	public static final Item RPG7_TRIGGER_GROUP = item("rpg7_trigger_group", Item::new, new Item.Properties().stacksTo(1));

	/**
	 * Shoulder-fired and loaded a rocket at a time from the inventory: no magazines. Its PGO-7 optic is built in
	 * (sneak to look through it), so the only attachment is the guidance module. Recoil kicks the view; the range
	 * stat is unused (rockets fly as projectiles).
	 */
	public static final Item RPG7 = item("rpg7",
			properties -> new RocketLauncherItem(properties,
					new GunStats(RPG_LAUNCH, 10.0f, 0, RocketLauncherItem.RELOAD_TICKS, List.of(FireMode.SEMI))
							.withScope(0.4f).withSpread(2.5f, 0.0f)),
			new Item.Properties().durability(250).repairable(Items.IRON_INGOT).rarity(Rarity.RARE));

	/** Lets the launcher lock on and guide homing rockets. Changes nothing about how it handles. */
	public static final Item HOMING_GUIDANCE_MODULE = item("homing_guidance_module",
			properties -> new AttachmentItem(properties, AttachmentSlot.GUIDANCE, List.of(RPG7), stats -> stats),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private Launchers() {
	}

	/**
	 * Sent each tick to a player locking on, and once more when the lock is dropped (target -1), so the HUD can show
	 * the brackets and play the tones.
	 *
	 * @param progress ticks spent on the target, up to {@link LockOn#LOCK_TICKS}
	 */
	public record LockState(int target, int progress, boolean locked) implements CustomPacketPayload {
		public static final LockState NONE = new LockState(-1, 0, false);
		public static final Type<LockState> TYPE = new Type<>(MyMod.id("rocket_lock"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LockState> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LockState::target,
				ByteBufCodecs.VAR_INT, LockState::progress,
				ByteBufCodecs.BOOL, LockState::locked,
				LockState::new);

		@Override
		public Type<LockState> type() {
			return TYPE;
		}
	}

	private static <T> DataComponentType<T> component(String name, Codec<T> codec,
			StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec, boolean persistent) {
		DataComponentType.Builder<T> builder = DataComponentType.<T>builder().networkSynchronized(streamCodec);
		if (persistent) {
			builder.persistent(codec);
		}
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MyMod.id(name), builder.build());
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static boolean isRocket(Item item) {
		return ROCKETS.contains(item);
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			output.insertAfter(ModItems.REVOLVER_CYLINDER, RPG7, RPG7_LAUNCH_TUBE, RPG7_TRIGGER_GROUP);
			output.insertAfter(ModItems.GLOCK_SWITCH, HOMING_GUIDANCE_MODULE);
			// After the last of the gun ammo (which goes in right after the arrows).
			output.insertBefore(Items.SPECTRAL_ARROW, PG7_ROCKET, PG7_HOMING_ROCKET, PG7_NAPALM_ROCKET);
		});
		PayloadTypeRegistry.clientboundPlay().register(LockState.TYPE, LockState.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
			LauncherHandling.tick(player);
			LockOn.tick(player);
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LauncherHandling.forget(handler.getPlayer());
			LockOn.forget(handler.getPlayer());
		});
	}
}
