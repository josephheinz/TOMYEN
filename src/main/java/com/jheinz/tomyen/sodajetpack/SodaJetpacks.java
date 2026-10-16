package com.jheinz.tomyen.sodajetpack;

import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.exoskeleton.ExoSockets;

/**
 * The Soda-Bottle Jetpack: two soda bottles taped to a bit of cardboard with a funnel for a nozzle, worn in the chest
 * slot (drawn as a 3D pack by SodaJetpackRenderer, client side) and assembled at the Scrap Bench. An early, genuinely
 * bad jetpack: hold jump in the air for a few seconds of weak, wobbly, sputtering lift (see {@link SodaJetpackFlight}).
 * Power armor's thrusters are the real thing, much later.
 *
 * <p>It runs on sugar and slime (see {@link JetpackMode}), loaded from the inventory as the tank runs dry: a Sugar
 * gives {@value #SUGAR_TICKS} ticks of burn, a Slime Ball {@value #SLIME_TICKS} and a Slime Block
 * {@value #SLIME_BLOCK_TICKS}. The Fizz Charge, two sugar and a slime ball shaken up in a bottle, gives
 * {@value #CHARGE_TICKS} of each and its bottle back. A Fizz Charge can also be drunk for a burst of Speed and Jump
 * Boost (and a burp).
 *
 * <p>Thrust uses up the pressure in the bottles; on the ground it slowly builds back, but run it right out and it goes
 * flat. A flat jetpack gives nothing until you shake it: sneak and hold use with both hands empty
 * ({@link Shake}).
 */
public final class SodaJetpacks {
	/** Leather tunic's armor, nearly: a cardboard backing. */
	public static final double ARMOR = 1.0;
	public static final int DURABILITY = ArmorType.CHESTPLATE.getDurability(7);
	public static final int SUGAR_TICKS = 20;
	public static final int SLIME_TICKS = 30;
	public static final int SLIME_BLOCK_TICKS = 270;
	public static final int CHARGE_TICKS = 60;

	// What the jetpack is doing, for everyone's particles, sounds and wobble (the FX attachment's bits).
	public static final int FX_THRUST = 1;
	public static final int FX_SPUTTER = 2;
	public static final int FX_SPIN = 4;
	public static final int FX_SHAKE = 8;
	/** The FX attachment's mode (a {@link JetpackMode} ordinal) sits above the flags. */
	public static final int FX_MODE_SHIFT = 4;

	public static final SoundEvent THRUST = sound("soda_jetpack_thrust");
	public static final SoundEvent SPUTTER = sound("soda_jetpack_sputter");
	public static final SoundEvent SPIN = sound("soda_jetpack_spin");
	public static final SoundEvent FLAT = sound("soda_jetpack_flat");
	public static final SoundEvent SHAKE = sound("soda_jetpack_shake");
	public static final SoundEvent CHARGED = sound("soda_jetpack_charged");
	public static final SoundEvent REFUEL = sound("soda_jetpack_refuel");
	public static final SoundEvent CRASH = sound("soda_jetpack_crash");
	public static final Holder<SoundEvent> BURP = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, MyMod.id("fizz_charge_burp"),
			SoundEvent.createVariableRangeEvent(MyMod.id("fizz_charge_burp")));

	public static final Item SODA_JETPACK = jetpack("soda_jetpack");
	public static final Item FIZZ_CHARGE = item("fizz_charge", new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink()
					.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.SPEED, 160, 0),
							new MobEffectInstance(MobEffects.JUMP_BOOST, 160, 0))))
					.soundAfterConsume(BURP)
					.build()),
			"item.tomyen.fizz_charge.fuel");

	/** On a player: their jetpack's fuel and pressure. Saved; synced to them alone. */
	public static final AttachmentType<JetpackTank> TANK = AttachmentRegistry.<JetpackTank>builder()
			.persistent(JetpackTank.CODEC)
			.initializer(() -> JetpackTank.FRESH)
			.syncWith(JetpackTank.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("soda_jetpack_tank"));

	/** On a player: what their jetpack is doing (the FX_ bits and its mode), synced to everyone. Not saved. */
	public static final AttachmentType<Integer> FX = AttachmentRegistry.<Integer>builder()
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
			.buildAndRegister(MyMod.id("soda_jetpack_fx"));

	/** On a player: the server's own bookkeeping for their jetpack. Not saved or synced. */
	public static final AttachmentType<SodaJetpackFlight.Run> RUN = AttachmentRegistry.<SodaJetpackFlight.Run>builder()
			.initializer(SodaJetpackFlight.Run::new)
			.buildAndRegister(MyMod.id("soda_jetpack_run"));

	private SodaJetpacks() {
	}

	private static Item jetpack(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Identifier id = MyMod.id(name);
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.build();
		// No equipment asset: the pack is drawn in code (SodaJetpackRenderer), not as an armor texture layer.
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(1).durability(DURABILITY).repairable(Items.SLIME_BALL)
				.attributes(attributes)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build());
		return Registry.register(BuiltInRegistries.ITEM, key, new SodaJetpackItem(properties, "item.tomyen.soda_jetpack.fly",
				"item.tomyen.soda_jetpack.fuel", "item.tomyen.soda_jetpack.shake"));
	}

	private static Item item(String name, Item.Properties properties, String... tooltip) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new SodaJetpackItem(properties.setId(key), tooltip));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Sent every tick the wearer is shaking the jetpack: sneaking, holding use, both hands empty. */
	public record Shake() implements CustomPacketPayload {
		public static final Shake INSTANCE = new Shake();
		public static final Type<Shake> TYPE = new Type<>(MyMod.id("soda_jetpack_shake"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shake> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Shake> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(Shake.TYPE, Shake.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Shake.TYPE, (payload, context) -> SodaJetpackFlight.shake(context.player()));
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			for (ServerPlayer player : level.players()) {
				SodaJetpackFlight.tick(player);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
				.register(output -> output.insertAfter(Items.TURTLE_HELMET, SODA_JETPACK, FIZZ_CHARGE));
	}

	/** Whether an entity has the jetpack on: worn, or socketed in the exoskeleton it wears. */
	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).is(SODA_JETPACK) || ExoSockets.hasSocketed(entity, SODA_JETPACK);
	}

	public static JetpackTank tank(LivingEntity entity) {
		return entity.getAttachedOrElse(TANK, JetpackTank.FRESH);
	}

	public static int fx(LivingEntity entity) {
		return entity.getAttachedOrElse(FX, 0);
	}
}
