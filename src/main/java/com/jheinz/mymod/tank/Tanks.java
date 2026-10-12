package com.jheinz.mymod.tank;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.mymod.MyMod;

/** The drivable tank: its entity, item, shell, and the packet its driver fires the cannon with. */
public final class Tanks {
	public static final EntityType<Tank> TANK = entity("tank",
			EntityType.Builder.of(Tank::new, MobCategory.MISC)
					.sized(2.5f, 1.35f)
					.eyeHeight(1.15f)
					.clientTrackingRange(10));
	public static final EntityType<TankShell> TANK_SHELL_ENTITY = entity("tank_shell",
			EntityType.Builder.<TankShell>of(TankShell::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.3f, 0.3f)
					.clientTrackingRange(6)
					.updateInterval(10));

	public static final Item TANK_ITEM = item("tank", TankItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** What the cannon fires; not obtainable, it's only there for the projectile's looks. */
	public static final Item TANK_SHELL = item("tank_shell", Item::new, new Item.Properties().stacksTo(16));

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

	private Tanks() {
	}

	/** Sent when the driver of a tank presses attack. */
	public record FireCannon() implements CustomPacketPayload {
		public static final FireCannon INSTANCE = new FireCannon();
		public static final Type<FireCannon> TYPE = new Type<>(MyMod.id("fire_tank_cannon"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FireCannon> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<FireCannon> type() {
			return TYPE;
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

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(TANK, Tank.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(output -> output.insertAfter(Items.TNT_MINECART, TANK_ITEM));
		PayloadTypeRegistry.serverboundPlay().register(FireCannon.TYPE, FireCannon.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FireCannon.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof Tank tank) {
				tank.fireForDriver(context.player());
			}
		});
	}
}
