package com.jheinz.mymod.client.exoskeleton;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.exoskeleton.ExoModuleItem;
import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;
import com.jheinz.mymod.exoskeleton.ExoskeletonMenu;

/**
 * A placeholder interface for the exoskeleton: a plain dark panel with the title, the sockets in a column each
 * labelled with its number, what it holds and what that's there as (a module, or the armor slot it fills), and
 * the player's inventory below.
 */
public class ExoskeletonScreen extends AbstractContainerScreen<ExoskeletonMenu> {
	private static final int WIDTH = 176;
	private static final int PANEL = 0xF0202429;
	private static final int BORDER = 0xFF6B737E;
	private static final int SLOT = 0xFF111317;
	private static final int SLOT_BORDER = 0xFF4A515B;
	private static final int TEXT = 0xFFE6E9ED;
	private static final int DIM = 0xFF8A929C;
	private static final int DARK = 0xFF565D67;
	private static final int ACCENT = 0xFFFFB300;
	/** Where socket labels start, right of the sockets, and how wide they may run. */
	private static final int LABEL_X = ExoskeletonMenu.SOCKET_X + 22;
	private static final int LABEL_WIDTH = WIDTH - LABEL_X - 6;

	private static final Component EMPTY_SOCKET = Component.translatable("container.mymod.exoskeleton.empty_socket");

	public ExoskeletonScreen(ExoskeletonMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, ExoskeletonMenu.HEIGHT);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x0 = leftPos, y0 = topPos;
		graphics.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, PANEL);
		graphics.outline(x0, y0, imageWidth, imageHeight, BORDER);
		for (Slot slot : menu.slots) {
			int sx = x0 + slot.x - 1, sy = y0 + slot.y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, SLOT);
			// Sockets stand out, and so does the suit being edited, which is locked while the interface is open.
			graphics.outline(sx, sy, 18, 18, menu.isDeviceSlot(slot) || slot.index < ExoSockets.COUNT ? ACCENT : SLOT_BORDER);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, 8, 6, TEXT, false);
		for (int socket = 0; socket < ExoSockets.COUNT; socket++) {
			int y = ExoskeletonMenu.socketY(socket);
			ItemStack stack = menu.slots.get(socket).getItem();
			Component number = Component.translatable("container.mymod.exoskeleton.socket", socket + 1);
			if (stack.isEmpty()) {
				graphics.text(font, number, LABEL_X, y, DIM, false);
				graphics.text(font, Component.translatable("container.mymod.exoskeleton.empty"), LABEL_X, y + 9, DARK, false);
				continue;
			}
			// "Socket 1 · Head", then the item's name, cut to fit.
			graphics.text(font, Component.translatable("container.mymod.exoskeleton.socket_kind", number, ExoskeletonItem.socketKind(stack)),
					LABEL_X, y, DIM, false);
			int color = stack.getItem() instanceof ExoModuleItem module ? 0xFF000000 | module.module().color() : TEXT;
			graphics.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(stack.getHoverName(), LABEL_WIDTH)), LABEL_X, y + 9,
					color, false);
		}
		graphics.text(font, playerInventoryTitle, ExoskeletonMenu.INVENTORY_X, ExoskeletonMenu.INVENTORY_Y - 11, DIM, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && hoveredSlot.index < ExoSockets.COUNT && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
			graphics.setTooltipForNextFrame(font, font.split(EMPTY_SOCKET, 180), mouseX, mouseY);
		}
	}
}
