package com.jheinz.tomyen.accountant;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.jheinz.tomyen.bank.Banking;
import com.jheinz.tomyen.bank.Debt;
import com.jheinz.tomyen.bank.DebtCollection;

/**
 * An Extension Form, from the Accountant's briefcase: filed (used), it pushes the player's repayment deadline back one
 * in-game day, never past a full term, re-arming the warnings it puts back ahead (see {@link DebtCollection#extended}).
 * No good once the account is in collections.
 */
public class ExtensionFormItem extends Item {
	private static final int EXTENSION_TICKS = 24000;

	public ExtensionFormItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer server)) {
			return InteractionResult.SUCCESS;
		}
		Debt debt = Debt.of(server);
		String refusal = debt == null ? "extension_nothing" : debt.hunting() ? "extension_late"
				: debt.ticksLeft() >= DebtCollection.TERM_TICKS ? "extension_full" : null;
		if (refusal != null) {
			server.sendOverlayMessage(Component.translatable("message.tomyen.accountant." + refusal).withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		Debt extended = DebtCollection.extended(debt, EXTENSION_TICKS);
		server.setAttached(Banking.DEBT, extended);
		player.getItemInHand(hand).consume(1, player);
		player.playSound(Accountants.SCRIBBLE, 0.9f, 1.0f);
		server.sendSystemMessage(Component.translatable("message.tomyen.accountant.extended", DebtCollection.formatTime(extended.ticksLeft()))
				.withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.extension_form.hint").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.extension_form.hint2").withStyle(ChatFormatting.DARK_GRAY));
	}
}
