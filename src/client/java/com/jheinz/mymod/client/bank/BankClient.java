package com.jheinz.mymod.client.bank;

import java.util.List;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.network.chat.Component;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.bank.Banking;
import com.jheinz.mymod.bank.CreditCardItem;
import com.jheinz.mymod.bank.Debt;
import com.jheinz.mymod.bank.DebtCollection;

/**
 * Client side of banking: collector rendering, the account readout in the top-left corner while you owe
 * anything (red and flashing in the last in-game hours, "IN COLLECTIONS" once the hunt is on), and the
 * account lines on credit card tooltips.
 */
public final class BankClient {
	private static final int OK = 0xFFFFFF55;
	private static final int SOON = 0xFFFFAA00;
	private static final int URGENT = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;
	/** Under an in-game day left, the readout turns orange; under two in-game hours it flashes red. */
	private static final int SOON_TICKS = 24000;
	private static final int URGENT_TICKS = 2000;

	private BankClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Banking.DEBT_COLLECTOR, DebtCollectorRenderer::new);
		EntityRendererRegistry.register(Banking.COLLECTOR_GOLEM, IronGolemRenderer::new);
		HudElementRegistry.addLast(MyMod.id("debt"), BankClient::extractHud);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (stack.getItem() instanceof CreditCardItem && minecraft.player != null) {
				addAccountLines(Debt.of(minecraft.player), lines);
			}
		});
	}

	/** What the holder owes in all, and when it's due: the card shows only its own balance. */
	private static void addAccountLines(Debt debt, List<Component> lines) {
		if (debt == null) {
			return;
		}
		lines.add(Component.translatable("item.mymod.credit_card.owed", debt.owed()).withStyle(ChatFormatting.GOLD));
		if (debt.hunting()) {
			lines.add(Component.translatable("item.mymod.credit_card.frozen").withStyle(ChatFormatting.DARK_RED));
		} else {
			lines.add(Component.translatable("item.mymod.credit_card.due", DebtCollection.formatTime(debt.ticksLeft()))
					.withStyle(debt.ticksLeft() < SOON_TICKS ? ChatFormatting.RED : ChatFormatting.YELLOW));
		}
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Debt debt = Debt.of(minecraft.player);
		if (debt == null) {
			return;
		}
		int x = 4;
		int y = 4;
		graphics.text(minecraft.font, Component.translatable("hud.mymod.debt.owed", debt.owed()), x, y, GRAY, true);
		if (debt.hunting()) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.debt.collections"), x, y + 10, URGENT, true);
			return;
		}
		int left = debt.ticksLeft();
		boolean flashOff = left < URGENT_TICKS && minecraft.player.tickCount / 10 % 2 == 0;
		int color = left < URGENT_TICKS ? URGENT : left < SOON_TICKS ? SOON : OK;
		if (!flashOff) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.debt.due", DebtCollection.formatTime(left)), x, y + 10, color, true);
		}
	}
}
