package com.jheinz.tomyen.survival;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Scavenger's Satchel: a leather pouch with nine slots, opened from the hand ({@link SatchelMenu}). Its contents
 * show in its tooltip, it can't hold another satchel or a shulker box, and if it's destroyed (burnt, say) it spills
 * what it held.
 */
public class SatchelItem extends Item {
	public SatchelItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new SatchelMenu(id, inventory, stack, hand),
					stack.getHoverName()));
		}
		level.playSound(player, player.getX(), player.getY(), player.getZ(), Survival.SATCHEL_OPEN, SoundSource.PLAYERS, 0.8f, 1.0f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public void onDestroyed(ItemEntity itemEntity) {
		ItemContainerContents contents = itemEntity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (contents != null) {
			ItemUtils.onContainerDestroyed(itemEntity, contents.nonEmptyItemCopyStream());
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		long used = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
		builder.accept(Component.translatable("item.tomyen.scavengers_satchel.slots", used, SatchelMenu.SIZE).withStyle(ChatFormatting.GRAY));
	}
}
