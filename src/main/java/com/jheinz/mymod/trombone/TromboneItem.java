package com.jheinz.mymod.trombone;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A brass tenor trombone. Hold use to blow; where you look works the slide (up for high notes, down for low, two
 * octaves of Bb major), and the note glides as it moves. Played from the main hand. Everything it does lives
 * elsewhere: {@link TromboneMusic} hears the notes and the songs, the client plays them.
 */
public class TromboneItem extends Item {
	public TromboneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.trombone.play").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.trombone.slide").withStyle(ChatFormatting.GRAY));
		for (TromboneSong song : TromboneSong.values()) {
			tooltip.accept(Component.translatable(song.translationKey()).withColor(song.colour() & 0xFFFFFF)
					.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(song.translationKey() + ".how").withStyle(ChatFormatting.DARK_GRAY)));
		}
	}
}
