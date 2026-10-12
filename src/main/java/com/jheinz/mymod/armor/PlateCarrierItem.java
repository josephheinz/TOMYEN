package com.jheinz.mymod.armor;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * A vest worn in the chest slot that holds one {@link ArmorPlateItem}. Like a bundle: right-click a
 * plate onto the vest to insert (or swap) it, right-click the vest with an empty cursor to take it out.
 */
public class PlateCarrierItem extends Item {
	public PlateCarrierItem(Properties properties) {
		super(properties);
	}

	/** The plate in the vest the entity is wearing in its chest slot (or in its exoskeleton's chest socket), or null. */
	public static ArmorPlateItem wornPlate(LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (chest.getItem() instanceof ExoskeletonItem) {
			chest = ExoSockets.armor(chest, EquipmentSlot.CHEST);
		}
		if (!(chest.getItem() instanceof PlateCarrierItem)) {
			return null;
		}
		Holder<Item> plate = chest.get(ModComponents.ARMOR_PLATE);
		return plate != null && plate.value() instanceof ArmorPlateItem armorPlate ? armorPlate : null;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack vest, ItemStack carried, Slot slot, ClickAction action,
			Player player, SlotAccess carriedAccess) {
		if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
			return false;
		}
		Holder<Item> current = vest.get(ModComponents.ARMOR_PLATE);

		if (carried.isEmpty()) {
			if (current == null) {
				return false;
			}
			vest.remove(ModComponents.ARMOR_PLATE);
			carriedAccess.set(new ItemStack(current));
			player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.2f);
			return true;
		}

		if (!(carried.getItem() instanceof ArmorPlateItem)) {
			return false;
		}
		vest.set(ModComponents.ARMOR_PLATE, carried.typeHolder());
		carriedAccess.set(current == null ? ItemStack.EMPTY : new ItemStack(current));
		player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.0f);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		Holder<Item> plate = stack.get(ModComponents.ARMOR_PLATE);
		if (plate == null) {
			tooltip.accept(Component.translatable("item.mymod.plate_carrier.no_plate").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.mymod.plate_carrier.plate", new ItemStack(plate).getHoverName())
					.withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.plate_carrier.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
