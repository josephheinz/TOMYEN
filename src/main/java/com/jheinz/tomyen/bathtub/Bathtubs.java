package com.jheinz.tomyen.bathtub;

import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.industry.Industry;

/**
 * The Bathtub Boat: a white enamel clawfoot tub with an outboard motor clamped over its back rim (Workshop into Foundry).
 * <ul>
 * <li>The {@link BathtubBlock Bathtub}: a two-block decorative tub, steel plate enamelled with quartz and white dye on
 * gold claw feet.</li>
 * <li>The Propeller (steel plate, or tungsten), which also lifts a Skullcrane servo drone in place of its bone struts,
 * and the Outboard Motor built round it: a servo arm for the powerhead, a canister for its tank, rubber seals.</li>
 * <li>Bathtub and Outboard Motor make the {@link BathtubBoat}. It burns kerosene (or fish oil), carried on the item
 * ({@link #BOAT_FUEL}) when it's broken.</li>
 * </ul>
 * Riding it, the {@link Honk} packet squeezes the rubber duck.
 */
public final class Bathtubs {
	/** Square like a vanilla boat's box and as tall, a little wider for the long tub. */
	public static final EntityType<BathtubBoat> BATHTUB_BOAT_ENTITY = entity("bathtub_boat",
			EntityType.Builder.of(BathtubBoat::new, MobCategory.MISC)
					.noLootTable()
					.sized(1.5f, 0.5625f)
					.eyeHeight(0.5625f)
					.clientTrackingRange(10));

	/** Fuel units in a packed-up boat's tank. */
	public static final DataComponentType<Integer> BOAT_FUEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("bathtub_boat_fuel"), DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final Block BATHTUB = block("bathtub", BathtubBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.SNOW).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.POPPED));

	public static final Item PROPELLER = item("propeller", Item::new, new Item.Properties());
	public static final Item OUTBOARD_MOTOR = item("outboard_motor", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item BATHTUB_BOAT = item("bathtub_boat", BathtubBoatItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	public static final SoundEvent MOTOR_SOUND = sound("bathtub_boat_motor");
	public static final SoundEvent MOTOR_START_SOUND = sound("bathtub_boat_start");
	public static final SoundEvent DUCK_SQUEAK = sound("rubber_duck_squeak");

	private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));
	private static final ResourceKey<CreativeModeTab> INDUSTRY_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, MyMod.id("industry"));

	private Bathtubs() {
	}

	/** Sent by a boat's rider to squeeze the rubber duck. */
	public record Honk() implements CustomPacketPayload {
		public static final Honk INSTANCE = new Honk();
		public static final Type<Honk> TYPE = new Type<>(MyMod.id("bathtub_honk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Honk> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Honk> type() {
			return TYPE;
		}
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		item(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
		return block;
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
		CreativeModeTabEvents.modifyOutputEvent(TOOLS_TAB).register(output -> output.insertAfter(Items.BAMBOO_CHEST_RAFT, BATHTUB_BOAT));
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_TAB).register(output -> output.insertAfter(Items.CAULDRON, BATHTUB));
		CreativeModeTabEvents.modifyOutputEvent(INDUSTRY_TAB).register(output -> output.insertAfter(Industry.WING_SKIN, PROPELLER, OUTBOARD_MOTOR));
		PayloadTypeRegistry.serverboundPlay().register(Honk.TYPE, Honk.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Honk.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof BathtubBoat boat) {
				boat.honk();
			}
		});
		// Riding, use a canister of kerosene or a bottle of fish oil to top up the tank from your seat.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player.getVehicle() instanceof BathtubBoat boat && BathtubBoatItem.fuelUnits(player.getItemInHand(hand)) > 0) {
				return boat.refuel(player, hand) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}
}
