package com.jheinz.tomyen.fusion.melee;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Tuskaxe (boar tusk + bloom steel axe, at a pond): a bloom steel axe whose bit is a great curved tusk, lashed to a
 * hickory haft with a boar-hide grip. Diamond's mining and 10 damage, and a sprinting chop fells the whole tree
 * ({@link TreeFelling}): run at a wild tree and cut a log within {@value TreeFelling#SPRINT_WINDOW} ticks of
 * sprinting, and every log above it comes down. Only natural trees (logs with wild leaves on them, so not a log cabin),
 * up to {@value TreeFelling#MAX_LOGS} logs, one durability each.
 */
public class TuskaxeItem extends Item {
	public TuskaxeItem(Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND && owner instanceof Player player && player.isSprinting()) {
			TreeFelling.sprinted(player);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.tuskaxe.fell", "item.tomyen.tuskaxe.wild");
	}
}
