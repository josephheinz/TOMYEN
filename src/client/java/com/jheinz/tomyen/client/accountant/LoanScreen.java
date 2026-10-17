package com.jheinz.tomyen.client.accountant;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.accountant.LoanMenu;
import com.jheinz.tomyen.accountant.Loans;
import com.jheinz.tomyen.bank.Debt;
import com.jheinz.tomyen.bank.DebtCollection;

/**
 * The loan office: a dark, gold-trimmed panel like the bank vault's statement, showing what's owed and when, the credit
 * left under the cap and the terms, over a button for each loan. A button is greyed out when that loan would go over the
 * cap or the account is in collections; its tooltip says why.
 */
public class LoanScreen extends AbstractContainerScreen<LoanMenu> {
	private static final int WIDTH = 210;
	private static final int HEIGHT = 102;
	private static final int BUTTON_WIDTH = 62;
	private static final int BUTTON_GAP = 5;

	private static final int PANEL = 0xFF14211A;
	private static final int TRIM = 0xFFB8902E;
	private static final int TRIM_LIGHT = 0xFFE8C25A;
	private static final int EMERALD = 0xFF6CF09A;
	private static final int TEXT = 0xFFD8D8D0;
	private static final int DIM = 0xFF9A9A92;
	private static final int OK = 0xFFFFFF55;
	private static final int SOON = 0xFFFFAA00;
	private static final int URGENT = 0xFFFF5555;
	private static final int SOON_TICKS = 24000;

	private final Button[] buttons = new Button[Loans.PRINCIPALS.length];

	public LoanScreen(LoanMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		int total = buttons.length * BUTTON_WIDTH + (buttons.length - 1) * BUTTON_GAP;
		int x = leftPos + (imageWidth - total) / 2;
		for (int i = 0; i < buttons.length; i++) {
			int option = i;
			buttons[i] = addRenderableWidget(Button.builder(Component.translatable("container.tomyen.loan_office.borrow", Loans.PRINCIPALS[i]),
							button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, option))
					.bounds(x + i * (BUTTON_WIDTH + BUTTON_GAP), topPos + HEIGHT - 28, BUTTON_WIDTH, 20)
					.build());
		}
		updateButtons();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i] == null) {
				continue;
			}
			int principal = Loans.PRINCIPALS[i];
			int owe = Loans.repayment(principal);
			boolean allowed = Loans.canBorrow(minecraft.player, i);
			buttons[i].active = allowed;
			Component tooltip;
			if (allowed) {
				tooltip = Component.translatable("container.tomyen.loan_office.borrow.tooltip", principal, owe);
			} else if (Debt.inCollections(minecraft.player)) {
				tooltip = Component.translatable("container.tomyen.loan_office.collections");
			} else {
				tooltip = Component.translatable("container.tomyen.loan_office.borrow.declined", Debt.owed(minecraft.player) + owe, Loans.CAP);
			}
			buttons[i].setTooltip(Tooltip.create(tooltip));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x0 = leftPos;
		int y0 = topPos;
		graphics.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, PANEL);
		graphics.outline(x0, y0, imageWidth, imageHeight, TRIM);
		graphics.fill(x0 + 1, y0 + 1, x0 + imageWidth - 1, y0 + 2, TRIM_LIGHT);
		graphics.item(new ItemStack(Items.EMERALD), x0 + imageWidth - 22, y0 + 6);

		int textX = x0 + 8;
		graphics.text(font, title, textX, y0 + 7, TRIM_LIGHT, true);
		Debt debt = Debt.of(minecraft.player);
		if (debt == null) {
			graphics.text(font, Component.translatable("container.tomyen.loan_office.nothing_owed"), textX, y0 + 21, TEXT, true);
		} else {
			graphics.text(font, Component.translatable("container.tomyen.loan_office.owed", debt.owed()), textX, y0 + 21, TEXT, true);
			if (debt.hunting()) {
				graphics.text(font, Component.translatable("container.tomyen.loan_office.collections"), textX, y0 + 32, URGENT, true);
			} else {
				int left = debt.ticksLeft();
				graphics.text(font, Component.translatable("container.tomyen.loan_office.due", DebtCollection.formatTime(left)), textX, y0 + 32,
						left < 2000 ? URGENT : left < SOON_TICKS ? SOON : OK, true);
			}
		}
		graphics.text(font, Component.translatable("container.tomyen.loan_office.headroom", Loans.headroom(minecraft.player), Loans.CAP),
				textX, y0 + 46, EMERALD, true);
		graphics.text(font, Component.translatable("container.tomyen.loan_office.terms", Loans.INTEREST_PERCENT), textX, y0 + 57, DIM, true);
	}

	/** The title is drawn on the panel; there's no inventory to label. */
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
	}
}
