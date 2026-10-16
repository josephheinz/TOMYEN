package com.jheinz.tomyen.detector;

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

import com.jheinz.tomyen.bank.CreditCardItem;
import com.jheinz.tomyen.bank.DebtCollection;
import com.jheinz.tomyen.bank.Vaults;

/**
 * A pocket ledger: held, its HUD panel keeps the books (vault balance, what's charged to the cards carried, what's
 * owed and how long before the collectors come; see the bank package). Opened (used), it writes the statement out
 * into chat, as an ATM would.
 */
public class LedgerItem extends Item {
	private static final int COOLDOWN = 20;

	public LedgerItem(Properties properties) {
		super(properties);
	}

	/** Emeralds charged to, and the total limit of, the credit cards in this player's inventory. */
	public static int[] cards(Player player) {
		int charged = 0, limit = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof CreditCardItem card) {
				charged += CreditCardItem.balance(stack);
				limit += card.limit();
			}
		}
		return new int[] {charged, limit};
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.playSound(Detectors.LEDGER_OPEN, 0.8f, 1.0f);
		if (player instanceof ServerPlayer server) {
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN);
			int vault = Vaults.emeralds(Vaults.of(server).getItems());
			int[] cards = cards(player);
			server.sendSystemMessage(Component.translatable("item.tomyen.ledger.vault", vault).withStyle(ChatFormatting.GREEN));
			if (cards[1] > 0) {
				server.sendSystemMessage(Component.translatable("item.tomyen.ledger.cards", cards[0], cards[1]).withStyle(ChatFormatting.GRAY));
			}
			server.sendSystemMessage(DebtCollection.statement(server));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.ledger.hint").withStyle(ChatFormatting.GRAY));
	}
}
