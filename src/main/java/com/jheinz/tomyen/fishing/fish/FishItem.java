package com.jheinz.tomyen.fishing.fish;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * A fish whose eating does something a consume effect can't: dose the eater with radiation, hand over experience, set
 * them alight. The food and any status effects come from the item's ordinary food and consumable components. It can
 * carry a line of tooltip (a translation key) saying what's odd about it.
 */
public class FishItem extends Item {
	private final Consumer<ServerPlayer> onEaten;
	private final @Nullable String hint;

	public FishItem(Properties properties, Consumer<ServerPlayer> onEaten) {
		this(properties, onEaten, null);
	}

	public FishItem(Properties properties, Consumer<ServerPlayer> onEaten, @Nullable String hint) {
		super(properties);
		this.onEaten = onEaten;
		this.hint = hint;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			onEaten.accept(player);
		}
		return super.finishUsingItem(stack, level, entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		if (hint != null) {
			tooltip.accept(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
		}
	}
}
