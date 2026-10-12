package com.jheinz.mymod.skullcrane;

import java.util.Arrays;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.exoskeleton.ExoSockets;

/**
 * Gear made from skullcrane parts, worn in an armor slot or socketed in an exoskeleton (which counts as worn):
 * <ul>
 * <li>The Optic Monocle (head): hold the zoom key to zoom in like a spyglass, and every creature in sight shows a
 * health bar over its head (both client side, see client/skullcrane/MonocleHud).
 * <li>The Servo Arm Backpack (chest): two servo arms over the shoulders that reach {@value #BACKPACK_REACH} blocks
 * further for blocks, and grab dropped items from {@value #PICKUP_RANGE} blocks away.
 * </ul>
 */
public final class ServoGear {
	public static final double BACKPACK_REACH = 2.0;
	public static final double PICKUP_RANGE = 4.5;

	private ServoGear() {
	}

	static Item.Properties monocleProperties() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(DataComponents.EQUIPPABLE,
				Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_CHAIN).build())
				.component(DataComponents.LORE, lore("item.mymod.optic_monocle.zoom", "item.mymod.optic_monocle.health",
						"item.mymod.servo_gear.socket"));
	}

	static Item.Properties backpackProperties() {
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.BLOCK_INTERACTION_RANGE, new AttributeModifier(MyMod.id("servo_arm_backpack"), BACKPACK_REACH,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.build();
		return new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).attributes(attributes).component(DataComponents.EQUIPPABLE,
				Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).build())
				.component(DataComponents.LORE, lore("item.mymod.servo_arm_backpack.pickup", "item.mymod.servo_gear.socket"));
	}

	private static ItemLore lore(String... keys) {
		return new ItemLore(Arrays.stream(keys)
				.<Component>map(key -> Component.translatable(key).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)))
				.toList());
	}

	/** Whether something wears this in a slot, or has it socketed in the exoskeleton it wears. */
	private static boolean wears(LivingEntity entity, EquipmentSlot slot, Item item) {
		return entity.getItemBySlot(slot).is(item) || ExoSockets.worn(entity, slot).is(item);
	}

	public static boolean wearsMonocle(LivingEntity entity) {
		return wears(entity, EquipmentSlot.HEAD, Skullcranes.OPTIC_MONOCLE);
	}

	public static boolean wearsBackpack(LivingEntity entity) {
		return wears(entity, EquipmentSlot.CHEST, Skullcranes.SERVO_ARM_BACKPACK);
	}

	static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 2 != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isAlive() && !player.isSpectator() && wearsBackpack(player)) {
					// The arms reach out and grab: as if the player had walked over each item.
					for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PICKUP_RANGE, 2.0, PICKUP_RANGE),
							ItemEntity::isAlive)) {
						item.playerTouch(player);
					}
				}
			}
		});
	}
}
