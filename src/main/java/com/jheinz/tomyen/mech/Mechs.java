package com.jheinz.tomyen.mech;

import java.util.Set;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.powerarmor.PowerArmors;
import com.jheinz.tomyen.tank.Tanks;

/**
 * The Bulwark Walker: its entity, the Mech Gantry it's built in, its parts and weapons, the Autocannon Drum, the
 * servo actuators its limbs are made from, its menus, sounds and damage types, and the packet its pilot works the
 * controls with (see {@link Control}). The behaviour lives in {@link Mech}.
 */
public final class Mechs {
	public static final EntityType<Mech> MECH = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("mech")),
			EntityType.Builder.of(Mech::new, MobCategory.MISC)
					// As tall as vanilla's entity lookups allow (see Mech): the head and pauldrons stand above it.
					.sized(2.5f, 4.0f)
					.eyeHeight(3.8f)
					.fireImmune()
					.clientTrackingRange(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("mech"))));

	/** The core parts wear: their durability is their condition. */
	private static final int PART_DURABILITY = 200;

	public static final Item TORSO = part("mech_torso", new Item.Properties().durability(PART_DURABILITY).rarity(Rarity.EPIC).fireResistant());
	public static final Item LEGS = part("mech_legs", new Item.Properties().durability(PART_DURABILITY).rarity(Rarity.RARE).fireResistant());
	public static final Item ARM = part("mech_arm", new Item.Properties().durability(PART_DURABILITY).rarity(Rarity.RARE).fireResistant());
	public static final Item POWER_PLANT = part("mech_power_plant", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	public static final Item AUTOCANNON = part("mech_autocannon", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item ROTARY_CANNON = part("mech_rotary_cannon", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item POWER_FIST = part("mech_power_fist", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item ROCKET_POD = part("mech_rocket_pod", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item FLAMER = part("mech_flamer", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item CARGO_POD = part("mech_cargo_pod", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item SMOKE_LAUNCHER = part("mech_smoke_launcher", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** Pistons, steel and redstone: what the walker's limbs are made of. */
	public static final Item ACTUATOR = item("mech_actuator", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	/** Thirty explosive-tipped autocannon rounds. */
	public static final Item AUTOCANNON_DRUM = item("autocannon_drum", Item::new, new Item.Properties().stacksTo(16));

	public static final Block GANTRY = block("mech_gantry", MechGantryBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0f, 12.0f).sound(SoundType.NETHERITE_BLOCK)
					.requiresCorrectToolForDrops().noOcclusion());
	public static final BlockEntityType<MechGantryBlockEntity> GANTRY_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("mech_gantry"), new BlockEntityType<>(MechGantryBlockEntity::new, Set.of(GANTRY)));
	public static final MenuType<MechGantryMenu> GANTRY_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("mech_gantry"),
			new MenuType<>(MechGantryMenu::new, FeatureFlags.VANILLA_SET));
	public static final MenuType<MechCargoMenu> CARGO_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("mech_cargo"),
			new MenuType<>(MechCargoMenu::new, FeatureFlags.VANILLA_SET));

	/** A footfall is three layered sounds: the stomp, a deep metal clank, and a low thud. */
	public static final SoundEvent STEP_SOUND = sound("mech_step");
	public static final SoundEvent STEP_CLANK_SOUND = sound("mech_step_clank");
	public static final SoundEvent STEP_THUD_SOUND = sound("mech_step_thud");
	public static final SoundEvent LAND_SOUND = sound("mech_land");
	public static final SoundEvent JETS_SOUND = sound("mech_jets");
	public static final SoundEvent HATCH_OPEN_SOUND = sound("mech_hatch_open");
	public static final SoundEvent HATCH_CLOSE_SOUND = sound("mech_hatch_close");
	public static final SoundEvent POWER_UP_SOUND = sound("mech_power_up");
	public static final SoundEvent POWER_DOWN_SOUND = sound("mech_power_down");
	/** The autocannon's report (a big rifle crack, pitched right down) and the boom-thump layered under it. */
	public static final SoundEvent AUTOCANNON_SOUND = sound("mech_autocannon");
	public static final SoundEvent AUTOCANNON_THUMP_SOUND = sound("mech_autocannon_thump");
	/** The rotary cannon's report (the mod's machine gun shots, pitched heavier), its motor's spin up and down, and the barrels' whir. */
	public static final SoundEvent ROTARY_SOUND = sound("mech_rotary");
	public static final SoundEvent ROTARY_SPIN_SOUND = sound("mech_rotary_spin");
	public static final SoundEvent ROTARY_SPIN_DOWN_SOUND = sound("mech_rotary_spin_down");
	public static final SoundEvent ROTARY_WHIR_SOUND = sound("mech_rotary_whir");
	public static final SoundEvent PUNCH_SOUND = sound("mech_punch");
	public static final SoundEvent GRAB_SOUND = sound("mech_grab");
	public static final SoundEvent THROW_SOUND = sound("mech_throw");
	public static final SoundEvent RELOAD_SOUND = sound("mech_reload");
	public static final SoundEvent SMOKE_SOUND = sound("mech_smoke");

	public static final ResourceKey<DamageType> FIST_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("mech_fist"));
	public static final ResourceKey<DamageType> SHOCKWAVE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("mech_shockwave"));

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	private Mechs() {
	}

	/**
	 * Sent by a walker's pilot to work the controls: the triggers going down and up, and the keys. The server checks
	 * they're in the seat.
	 */
	public record Control(int action) implements CustomPacketPayload {
		public static final int LEFT_DOWN = 0;
		public static final int LEFT_UP = 1;
		public static final int RIGHT_DOWN = 2;
		public static final int RIGHT_UP = 3;
		/** Jump: the pilot's client has already kicked the walker up; the server burns the fuel and makes the noise. */
		public static final int JETS = 4;
		public static final int SMOKE = 5;
		public static final int CARGO = 6;

		public static final Type<Control> TYPE = new Type<>(MyMod.id("mech_control"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Control> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Control::action,
				Control::new);

		@Override
		public Type<Control> type() {
			return TYPE;
		}
	}

	/**
	 * Sent by a seated pilot's client every tick: the torso yaw and arm pitch it shows. The server goes by it (within
	 * the twist limit) so the walker it simulates, and the shots it fires, match what the pilot sees.
	 */
	public record Aim(float torsoYaw, float armPitch) implements CustomPacketPayload {
		public static final Type<Aim> TYPE = new Type<>(MyMod.id("mech_aim"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aim> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Aim::torsoYaw,
				ByteBufCodecs.FLOAT, Aim::armPitch,
				Aim::new);

		@Override
		public Type<Aim> type() {
			return TYPE;
		}
	}

	private static void handleAim(ServerPlayer player, Aim aim) {
		if (player.getVehicle() instanceof Mech mech && mech.isSeated(player)) {
			mech.pilotAim(aim.torsoYaw(), aim.armPitch());
		}
	}

	private static void handle(ServerPlayer player, Control control) {
		if (player.getVehicle() instanceof Mech mech && mech.isSeated(player)) {
			mech.control(player, control.action());
		}
	}

	private static Item part(String name, Item.Properties properties) {
		return item(name, MechPartItem::new, properties);
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(MECH, Mech.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(output -> output.insertAfter(Tanks.TANK_ITEM, TORSO, LEGS, ARM, POWER_PLANT,
				ACTUATOR, CARGO_POD, SMOKE_LAUNCHER));
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			output.insertAfter(PowerArmors.MK3_PLATING_KIT, AUTOCANNON, ROTARY_CANNON, POWER_FIST, ROCKET_POD, FLAMER);
			output.insertBefore(Items.SPECTRAL_ARROW, AUTOCANNON_DRUM);
		});
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS_TAB).register(output -> output.insertAfter(PowerArmors.STATION, GANTRY));
		PayloadTypeRegistry.serverboundPlay().register(Control.TYPE, Control.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Control.TYPE, (payload, context) -> handle(context.player(), payload));
		PayloadTypeRegistry.serverboundPlay().register(Aim.TYPE, Aim.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Aim.TYPE, (payload, context) -> handleAim(context.player(), payload));
	}
}
