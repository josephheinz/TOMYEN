package com.jheinz.tomyen.fusion.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.banshee.Banshees;
import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.fusion.FusionTooltipItem;
import com.jheinz.tomyen.meteorite.Meteorites;
import com.jheinz.tomyen.werewolf.Werewolves;

/**
 * Fusion gear (batch 3): armor fused from armor.
 * <ul>
 * <li><b>Phantasmal Chestplate</b> (Ectoplasm + Holy Silver Chestplate, reactor): past Exotic (9 armor, toughness
 * 4.5). One hit in five passes straight through ({@link Phasing}); it still counts as holy silver against the evil.
 * <li><b>Wolfhide Carrier</b> (Werewolf Pelt + Plate Carrier, pond): a plate carrier sewn into the pelt (7 armor,
 * toughness 1.5), still taking an armor plate; by night it lends speed and strength, more under a full moon
 * ({@link MoonHide}).
 * <li><b>Astral Visor</b> (Cathode TV Helmet + Starlight Lens, reactor): night vision, mobs outlined through walls
 * nearby, and a warning when a meteor or a UFO is coming ({@link AstralVisor}).
 * <li><b>Revenant Mail</b> (Phantasmal Chestplate + Wolfhide Carrier, reactor, tier 2): the best chestplate (10
 * armor, toughness 5), with both parents' abilities, and once a day it keeps your gear on you through death
 * ({@link RevenantMail}).
 * </ul>
 */
public final class FusionGear {
	// --- Items -------------------------------------------------------------------------------------------------
	public static final Item PHANTASMAL_CHESTPLATE = item("phantasmal_chestplate",
			properties -> new FusionTooltipItem(properties, "item.tomyen.phantasmal_chestplate.phase",
					"item.tomyen.phantasmal_chestplate.holy"),
			armor("phantasmal_chestplate", EquipmentSlot.CHEST, ArmorType.CHESTPLATE.getDurability(45), 9.0, 4.5, 0.1,
					Banshees.ECTOPLASM, SoundEvents.ARMOR_EQUIP_NETHERITE, Rarity.EPIC));
	public static final Item WOLFHIDE_CARRIER = item("wolfhide_carrier", properties -> new WolfhideCarrierItem(properties,
			"item.tomyen.wolfhide_carrier.night", "item.tomyen.wolfhide_carrier.moon"),
			armor("wolfhide_carrier", EquipmentSlot.CHEST, ArmorType.CHESTPLATE.getDurability(26), 7.0, 1.5, 0.0,
					Werewolves.WEREWOLF_PELT, SoundEvents.ARMOR_EQUIP_LEATHER, Rarity.RARE));
	public static final Item ASTRAL_VISOR = item("astral_visor", properties -> new FusionTooltipItem(properties,
			"item.tomyen.astral_visor.vision", "item.tomyen.astral_visor.sight", "item.tomyen.astral_visor.warning"),
			armor("astral_visor", EquipmentSlot.HEAD, ArmorType.HELMET.getDurability(45), 4.0, 4.0, 0.1,
					Meteorites.STAR_CRYSTAL_SHARD, SoundEvents.ARMOR_EQUIP_GENERIC, Rarity.EPIC));
	public static final Item REVENANT_MAIL = item("revenant_mail", RevenantMailItem::new,
			armor("revenant_mail", EquipmentSlot.CHEST, ArmorType.CHESTPLATE.getDurability(52), 10.0, 5.0, 0.15,
					Banshees.ECTOPLASM, SoundEvents.ARMOR_EQUIP_NETHERITE, Rarity.EPIC).fireResistant());

	// --- State -------------------------------------------------------------------------------------------------
	/** On a Revenant Mail: the in-game day its death ward was last spent (absent: never). */
	public static final DataComponentType<Long> REVENANT_SPENT_DAY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("revenant_spent_day"), DataComponentType.<Long>builder().persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	/**
	 * On a dead player: the Revenant Mail kept their gear through this death, so it goes back on them at respawn. Saved
	 * (a server restart on the death screen mustn't lose the gear), and not copied to the respawned player.
	 */
	public static final AttachmentType<Boolean> REVENANT_KEPT = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL)
			.buildAndRegister(MyMod.id("revenant_kept"));

	private FusionGear() {
	}

	private static Item.Properties armor(String name, EquipmentSlot slot, int durability, double armor, double toughness,
			double knockback, Item repair, Holder<SoundEvent> sound, Rarity rarity) {
		Identifier id = MyMod.id(name);
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE), group)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE), group);
		if (knockback > 0.0) {
			attributes.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, knockback, AttributeModifier.Operation.ADD_VALUE), group);
		}
		// No equipment asset: each piece is drawn as its own 3D model (FusionGearRenderer, client side).
		return new Item.Properties().stacksTo(1).durability(durability).enchantable(15).repairable(repair).rarity(rarity)
				.attributes(attributes.build())
				.component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setEquipSound(sound).build());
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** Whether the entity wears this item in its chest slot, or socketed in its exoskeleton's chest socket. */
	static boolean wearsChest(LivingEntity entity, Item item) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).is(item) || ExoSockets.worn(entity, EquipmentSlot.CHEST).is(item);
	}

	/** Phantasmal Chestplate or Revenant Mail: one hit in five passes through. */
	public static boolean phases(LivingEntity entity) {
		return wearsChest(entity, PHANTASMAL_CHESTPLATE) || wearsChest(entity, REVENANT_MAIL);
	}

	/** Wolfhide Carrier or Revenant Mail: stronger by night. */
	public static boolean moonlit(LivingEntity entity) {
		return wearsChest(entity, WOLFHIDE_CARRIER) || wearsChest(entity, REVENANT_MAIL);
	}

	// --- Packets -----------------------------------------------------------------------------------------------

	/** One thing the Astral Visor has spotted coming: a meteor (with where it'll land) or a UFO. */
	public record Threat(int entityId, int kind, double x, double y, double z, int ticks) {
		public static final int METEOR = 0;
		public static final int UFO = 1;
		public static final StreamCodec<RegistryFriendlyByteBuf, Threat> CODEC = StreamCodec.of((buf, t) -> {
			buf.writeVarInt(t.entityId);
			buf.writeVarInt(t.kind);
			buf.writeDouble(t.x);
			buf.writeDouble(t.y);
			buf.writeDouble(t.z);
			buf.writeVarInt(t.ticks);
		}, buf -> new Threat(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));
	}

	/** To a visor wearer: what's coming (meteor impact points and UFOs), nearest first; empty when the sky clears. */
	public record AstralWarning(List<Threat> threats) implements CustomPacketPayload {
		public static final Type<AstralWarning> TYPE = new Type<>(MyMod.id("astral_warning"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AstralWarning> CODEC = StreamCodec.composite(
				Threat.CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), AstralWarning::threats, AstralWarning::new);

		@Override
		public Type<AstralWarning> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(AstralWarning.TYPE, AstralWarning.CODEC);
		Phasing.initialize();
		MoonHide.initialize();
		AstralVisor.initialize();
		RevenantMail.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_HELMET, ASTRAL_VISOR);
			output.insertAfter(Items.NETHERITE_CHESTPLATE, WOLFHIDE_CARRIER, PHANTASMAL_CHESTPLATE, REVENANT_MAIL);
		});
	}
}
