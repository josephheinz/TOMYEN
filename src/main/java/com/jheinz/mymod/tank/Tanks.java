package com.jheinz.mymod.tank;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.mymod.MyMod;

/**
 * The tank: its entity, the item it packs into, its 120mm rounds and their projectile, the ammo rack's menu, its
 * sounds, and the packet its crew works the controls with (see {@link Control}).
 */
public final class Tanks {
	public static final EntityType<Tank> TANK = entity("tank",
			EntityType.Builder.of(Tank::new, MobCategory.MISC)
					.sized(3.5f, 2.8f)
					.eyeHeight(2.4f)
					.clientTrackingRange(10));
	public static final EntityType<TankShell> TANK_SHELL_ENTITY = entity("tank_shell",
			EntityType.Builder.<TankShell>of(TankShell::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.3f, 0.3f)
					.clientTrackingRange(10)
					// Every tick: a sabot covers seven blocks of it.
					.updateInterval(1));

	public static final Item TANK_ITEM = item("tank", TankItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item APFSDS_SHELL = item("apfsds_shell", properties -> new ShellItem(properties, ShellType.APFSDS),
			new Item.Properties().stacksTo(8).rarity(Rarity.UNCOMMON));
	public static final Item HE_SHELL = item("he_shell", properties -> new ShellItem(properties, ShellType.HE),
			new Item.Properties().stacksTo(8));
	/** The bank's shells; not obtainable, it's only there for the projectile's looks. */
	public static final Item TANK_SHELL = item("tank_shell", Item::new, new Item.Properties().stacksTo(16));

	public static final MenuType<TankRackMenu> RACK_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("tank_rack"),
			new MenuType<>(TankRackMenu::new, FeatureFlags.VANILLA_SET));

	public static final SoundEvent CANNON_SOUND = sound("tank_cannon");
	/** What the crew hears of the gun from inside. This and the loader's sounds are only played to the crew. */
	public static final SoundEvent CANNON_CABIN_SOUND = sound("tank_cannon_cabin");
	public static final SoundEvent BREECH_SOUND = sound("tank_breech");
	public static final SoundEvent RAM_SOUND = sound("tank_ram");
	public static final SoundEvent CASING_SOUND = sound("tank_casing");
	public static final SoundEvent LOADER_SOUND = sound("tank_loader");
	public static final SoundEvent COAX_SOUND = sound("tank_coax");
	public static final SoundEvent SMOKE_SOUND = sound("tank_smoke");
	public static final SoundEvent HATCH_OPEN_SOUND = sound("tank_hatch_open");
	public static final SoundEvent HATCH_CLOSE_SOUND = sound("tank_hatch_close");
	public static final SoundEvent ENGINE_SOUND = sound("tank_engine");

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private Tanks() {
	}

	/**
	 * Sent by a tank's crew to work the controls the vanilla keys don't cover. The server checks they're in the
	 * gunner's seat.
	 *
	 * @param value the lased range in blocks, for {@link #LASE}
	 */
	public record Control(int action, float value) implements CustomPacketPayload {
		/** Attack: fire the main gun. */
		public static final int FIRE = 0;
		/** Use pressed and released: the coax trigger. */
		public static final int COAX_DOWN = 1;
		public static final int COAX_UP = 2;
		public static final int RELOAD = 3;
		public static final int NEXT_ROUND = 4;
		public static final int SMOKE = 5;
		public static final int LASE = 6;
		public static final int OPEN_RACK = 7;

		public static final Type<Control> TYPE = new Type<>(MyMod.id("tank_control"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Control> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Control::action,
				ByteBufCodecs.FLOAT, Control::value,
				Control::new);

		public Control(int action) {
			this(action, 0.0f);
		}

		@Override
		public Type<Control> type() {
			return TYPE;
		}
	}

	private static void handle(ServerPlayer player, Control control) {
		if (!(player.getVehicle() instanceof Tank tank) || !tank.isSeated(player)) {
			return;
		}
		TankGunnery gunnery = tank.gunnery();
		switch (control.action()) {
			case Control.FIRE -> gunnery.fire(player);
			case Control.COAX_DOWN -> gunnery.setCoaxTrigger(true);
			case Control.COAX_UP -> gunnery.setCoaxTrigger(false);
			case Control.RELOAD -> gunnery.reload(player);
			case Control.NEXT_ROUND -> gunnery.cycleRound();
			case Control.SMOKE -> gunnery.fireSmoke(player);
			case Control.LASE -> gunnery.lase(control.value());
			case Control.OPEN_RACK -> tank.openRack(player);
			default -> {
			}
		}
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(TANK, Tank.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(output -> output.insertAfter(Items.TNT_MINECART, TANK_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> output.insertBefore(Items.SPECTRAL_ARROW, APFSDS_SHELL, HE_SHELL));
		PayloadTypeRegistry.serverboundPlay().register(Control.TYPE, Control.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Control.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerTickEvents.END_LEVEL_TICK.register(SmokeScreens::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SmokeScreens.clear());
	}
}
