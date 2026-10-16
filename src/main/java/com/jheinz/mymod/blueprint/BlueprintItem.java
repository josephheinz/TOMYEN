package com.jheinz.mymod.blueprint;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A rolled schematic. Named after what it teaches ("M1911 Blueprint"); right-click to read it, which teaches the recipe
 * for good and uses the paper up. A blueprint you already know is left alone.
 */
public class BlueprintItem extends Item {
	public BlueprintItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		Blueprint blueprint = Blueprints.of(stack);
		return blueprint == null ? super.getName(stack)
				: Component.translatable("item.mymod.blueprint.named", blueprint.resultName());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Blueprint blueprint = Blueprints.of(stack);
		if (blueprint == null) {
			return InteractionResult.PASS;
		}
		if (Blueprints.knows(player, blueprint.id())) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("blueprint.mymod.already_known", getName(stack))
						.withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			Blueprints.learn(serverPlayer, blueprint.id());
			serverPlayer.sendSystemMessage(Component.translatable("blueprint.mymod.learned",
					blueprint.resultName(), blueprint.station().displayName())
					.withStyle(ChatFormatting.AQUA));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.9f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
					SoundSource.PLAYERS, 0.8f, 1.1f);
			serverPlayer.awardStat(Stats.ITEM_USED.get(this));
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		Blueprint blueprint = Blueprints.of(stack);
		if (blueprint == null) {
			builder.accept(Component.translatable("blueprint.mymod.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		builder.accept(Component.translatable("blueprint.mymod.teaches", blueprint.station().displayName())
				.withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("blueprint.mymod.tier", blueprint.tier().displayName())
				.withStyle(blueprint.tier() == Blueprint.Tier.WORKSHOP ? ChatFormatting.BLUE : ChatFormatting.GOLD));
		builder.accept(Component.translatable("blueprint.mymod.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
