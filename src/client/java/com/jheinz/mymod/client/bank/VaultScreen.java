package com.jheinz.mymod.client.bank;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.bank.Debt;
import com.jheinz.mymod.bank.DebtCollection;
import com.jheinz.mymod.bank.VaultMenu;
import com.jheinz.mymod.bank.Vaults;

/**
 * The bank vault: vanilla's three-row chest, under a dark, gold-trimmed statement panel showing the
 * emeralds in the vault and what's owed and when, with a "Pay debt" button that pays it from the vault.
 */
public class VaultScreen extends AbstractContainerScreen<VaultMenu> {
	private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int ROWS = Vaults.SIZE / 9;
	/** The statement panel above the chest. */
	private static final int HEADER = 40;
	private static final int BUTTON_WIDTH = 58;

	private static final int PANEL = 0xFF14211A;
	private static final int TRIM = 0xFFB8902E;
	private static final int TRIM_LIGHT = 0xFFE8C25A;
	private static final int EMERALD = 0xFF6CF09A;
	private static final int TEXT = 0xFFD8D8D0;
	private static final int OK = 0xFFFFFF55;
	private static final int SOON = 0xFFFFAA00;
	private static final int URGENT = 0xFFFF5555;
	/** Under an in-game day left the due date turns orange, like the HUD readout. */
	private static final int SOON_TICKS = 24000;

	private @Nullable Button payButton;

	public VaultScreen(VaultMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 114 + ROWS * 18);
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		// Centre the chest and the panel above it together.
		topPos += HEADER / 2;
		payButton = addRenderableWidget(Button.builder(Component.translatable("container.mymod.bank_vault.pay_debt"),
						button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, VaultMenu.PAY_DEBT))
				.bounds(leftPos + imageWidth - BUTTON_WIDTH - 6, topPos - HEADER + 9, BUTTON_WIDTH, 20)
				.tooltip(Tooltip.create(Component.translatable("container.mymod.bank_vault.pay_debt.tooltip")))
				.build());
		updateButton();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButton();
	}

	private void updateButton() {
		if (payButton != null) {
			payButton.active = Debt.owed(minecraft.player) > 0 && menu.emeralds() > 0;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos, 0.0f, 0.0f, imageWidth, ROWS * 18 + 17, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CHEST, leftPos, topPos + ROWS * 18 + 17, 0.0f, 126.0f, imageWidth, 96, 256, 256);

		// The statement panel, sitting on the chest like a teller's window.
		int x0 = leftPos;
		int y0 = topPos - HEADER;
		graphics.fill(x0, y0, x0 + imageWidth, topPos - 1, PANEL);
		graphics.outline(x0, y0, imageWidth, HEADER - 1, TRIM);
		graphics.fill(x0 + 1, y0 + 1, x0 + imageWidth - 1, y0 + 2, TRIM_LIGHT);
		graphics.item(new ItemStack(Items.EMERALD), x0 + 6, y0 + 12);

		int textX = x0 + 26;
		graphics.text(font, Component.translatable("container.mymod.bank_vault.balance", menu.emeralds()), textX, y0 + 6, EMERALD, true);
		Debt debt = Debt.of(minecraft.player);
		if (debt == null) {
			graphics.text(font, Component.translatable("container.mymod.bank_vault.no_debt"), textX, y0 + 17, TEXT, true);
			return;
		}
		graphics.text(font, Component.translatable("container.mymod.bank_vault.owed", debt.owed()), textX, y0 + 17, TEXT, true);
		if (debt.hunting()) {
			graphics.text(font, Component.translatable("container.mymod.bank_vault.collections"), textX, y0 + 28, URGENT, true);
		} else {
			int left = debt.ticksLeft();
			graphics.text(font, Component.translatable("container.mymod.bank_vault.due", DebtCollection.formatTime(left)), textX, y0 + 28,
					left < 2000 ? URGENT : left < SOON_TICKS ? SOON : OK, true);
		}
	}

	// The statement panel counts as part of the screen, so clicking it doesn't throw the carried stack away.
	@Override
	protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
		return mx < xo || my < yo - HEADER || mx >= xo + imageWidth || my >= yo + imageHeight;
	}
}
