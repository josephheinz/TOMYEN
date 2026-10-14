package com.jheinz.mymod.fishing.fish;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A fish whose eating does something a consume effect can't: dose the eater with radiation, hand over experience, set
 * them alight. The food and any status effects come from the item's ordinary food and consumable components.
 */
public class FishItem extends Item {
	private final Consumer<ServerPlayer> onEaten;

	public FishItem(Properties properties, Consumer<ServerPlayer> onEaten) {
		super(properties);
		this.onEaten = onEaten;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			onEaten.accept(player);
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
