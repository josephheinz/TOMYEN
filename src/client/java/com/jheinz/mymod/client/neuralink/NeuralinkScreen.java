package com.jheinz.mymod.client.neuralink;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import com.jheinz.mymod.neuralink.NeuralinkMenu;
import com.jheinz.mymod.neuralink.NeuralinkModule;
import com.jheinz.mymod.neuralink.NeuralinkModuleItem;

import static com.jheinz.mymod.client.neuralink.NeuralStyle.CYAN;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.DARK;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.DIM;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.GLITCH;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.TEXT;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.argb;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.brackets;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.mix;
import static com.jheinz.mymod.client.neuralink.NeuralStyle.wave;

/**
 * The Neuralink's interface, drawn entirely in code: a dark glass panel on a faint grid with a sweeping scan
 * line, a hologram of the player wired to the module sockets (three, or five on a Mk II) that light up in their
 * module's colour, and the player's inventory below.
 */
public class NeuralinkScreen extends AbstractContainerScreen<NeuralinkMenu> {
	private static final int WIDTH = 196;

	// The hologram panel, relative to the screen's top left; it runs down to the layout's holoBottom.
	private static final int HOLO_X0 = 8;
	private static final int HOLO_Y0 = 22;
	private static final int HOLO_X1 = 64;
	/** The hologram's height for three sockets, which the player model is sized for. */
	private static final int BASE_HOLO_HEIGHT = 78;

	private static final Component EMPTY_SOCKET = Component.translatable("container.mymod.neuralink.empty_socket");

	public NeuralinkScreen(NeuralinkMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, menu.layout().height());
	}

	private int sockets() {
		return menu.layout().sockets();
	}

	private NeuralinkModule moduleIn(int socket) {
		return menu.slots.get(socket).getItem().getItem() instanceof NeuralinkModuleItem item ? item.module() : null;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		long now = Util.getMillis();
		int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;

		// Glass and grid.
		graphics.fillGradient(x0, y0, x1, y1, 0xF20A1420, 0xF205090F);
		for (int x = x0 + 4; x < x1; x += 8) {
			graphics.verticalLine(x, y0, y1 - 1, argb(0x0C, CYAN));
		}
		for (int y = y0 + 4; y < y1; y += 8) {
			graphics.horizontalLine(x0, x1 - 1, y, argb(0x0C, CYAN));
		}

		// The scan line sweeping down the panel.
		int sweep = y0 + (int) (now / 14 % (imageHeight + 40)) - 20;
		int top = Math.max(y0 + 1, sweep - 18), bottom = Math.min(y1 - 1, sweep);
		if (bottom > top) {
			graphics.fillGradient(x0 + 1, top, x1 - 1, bottom, argb(0x00, CYAN), argb(0x1C, CYAN));
		}
		if (sweep > y0 && sweep < y1) {
			graphics.horizontalLine(x0 + 1, x1 - 2, sweep, argb(0x40, CYAN));
		}

		// Frame: a dim border, a soft outer glow and bright corner brackets.
		graphics.outline(x0 - 1, y0 - 1, imageWidth + 2, imageHeight + 2, argb(0x30, CYAN));
		graphics.outline(x0, y0, imageWidth, imageHeight, argb(0x70, CYAN));
		brackets(graphics, x0, y0, x1, y1, 12, 2, argb(0xFF, CYAN));

		// Header rule, with a bright segment running along it.
		graphics.horizontalLine(x0 + 6, x1 - 7, y0 + 17, argb(0x50, CYAN));
		int run = x0 + 6 + (int) (now / 8 % (imageWidth - 12 + 30)) - 30;
		graphics.fill(Math.max(x0 + 6, run), y0 + 17, Math.min(x1 - 6, run + 30), y0 + 18, argb(0xD0, CYAN));

		// Hologram of the player.
		NeuralinkMenu.Layout layout = menu.layout();
		int hx0 = x0 + HOLO_X0, hy0 = y0 + HOLO_Y0, hx1 = x0 + HOLO_X1, hy1 = y0 + layout.holoBottom();
		int modelSize = Math.min(40, 30 * (hy1 - hy0) / BASE_HOLO_HEIGHT);
		graphics.fillGradient(hx0, hy0, hx1, hy1, argb(0x10, CYAN), argb(0x30, CYAN));
		brackets(graphics, hx0, hy0, hx1, hy1, 6, 1, argb(0xE0, CYAN));
		int base = hy1 - 8;
		graphics.horizontalLine(hx0 + 10, hx1 - 11, base, argb(0x90, CYAN));
		graphics.horizontalLine(hx0 + 16, hx1 - 17, base + 2, argb(0x60, CYAN));
		graphics.horizontalLine(hx0 + 22, hx1 - 23, base + 4, argb(0x30, CYAN));
		InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, hx0, hy0 + 2, hx1, base + 2, modelSize, 0.0625f, mouseX, mouseY,
				minecraft.player);
		int holoScan = hy0 + (int) (now / 20 % (hy1 - hy0));
		graphics.horizontalLine(hx0 + 1, hx1 - 2, holoScan, argb(0x60, CYAN));

		// Module sockets, each wired to the hologram.
		for (int socket = 0; socket < sockets(); socket++) {
			Slot slot = menu.slots.get(socket);
			NeuralinkModule module = moduleIn(socket);
			int accent = module != null ? module.color() : CYAN;
			int sx = x0 + slot.x - 1, sy = y0 + slot.y - 1;
			int wireY = sy + 9;
			graphics.horizontalLine(hx1, sx - 2, wireY, argb(module != null ? 0x90 : 0x30, accent));
			if (module != null) {
				// A pulse of data running down the wire into the hologram.
				int length = sx - 2 - hx1;
				int dot = sx - 2 - (int) ((now / 6 + socket * 40) % Math.max(1, length));
				graphics.fill(dot - 2, wireY - 1, dot + 1, wireY + 2, argb(0xFF, accent));
			}
			float pulse = wave(now, 1600.0f, socket * 1.3f);
			graphics.fill(sx, sy, sx + 18, sy + 18, module != null ? argb(0xFF, mix(0x060C12, accent, 0.15f)) : 0xFF060C12);
			graphics.outline(sx - 2, sy - 2, 22, 22, argb(module != null ? (int) (0x30 + 0x40 * pulse) : 0x18, accent));
			graphics.outline(sx - 1, sy - 1, 20, 20, argb(module != null ? 0x60 : (int) (0x20 + 0x30 * pulse), accent));
			graphics.outline(sx, sy, 18, 18, argb(module != null ? 0xFF : 0x90, accent));
		}

		// Inventory.
		graphics.horizontalLine(x0 + 6, x1 - 7, y0 + layout.dividerY(), argb(0x50, CYAN));
		for (int i = sockets(); i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			int sx = x0 + slot.x - 1, sy = y0 + slot.y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, 0xC00A1620);
			if (menu.isDeviceSlot(slot)) {
				// The Neuralink being edited: locked while the interface is open.
				graphics.fill(sx, sy, sx + 18, sy + 18, argb((int) (0x20 + 0x30 * wave(now, 1200.0f, 0.0f)), CYAN));
				graphics.outline(sx, sy, 18, 18, argb(0xFF, CYAN));
			} else {
				graphics.outline(sx, sy, 18, 18, argb(0x38, CYAN));
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		long now = Util.getMillis();
		String title = this.title.getString().toUpperCase(Locale.ROOT);
		// Every few seconds the title glitches for a moment.
		if (now % 4300 < 110) {
			graphics.text(font, title, 9, 6, argb(0xB0, GLITCH), false);
			graphics.text(font, title, 11, 7, argb(0xB0, CYAN), false);
		}
		graphics.text(font, title, 10, 6, TEXT, false);
		String link = (menu.isMk2() ? "N2" : "N1") + " // LINK " + (now % 1000 < 600 ? "●" : "○");
		graphics.text(font, link, imageWidth - 10 - font.width(link), 6, DIM, false);

		NeuralinkMenu.Layout layout = menu.layout();
		for (int socket = 0; socket < sockets(); socket++) {
			NeuralinkModule module = moduleIn(socket);
			int x = NeuralinkMenu.MODULE_X + 22, y = layout.socketY(socket);
			if (module != null) {
				graphics.text(font, module.displayName().getString().toUpperCase(Locale.ROOT), x, y, argb(0xFF, module.color()), false);
				// Active modules are fired with the trigger key; passive ones just run.
				String state = module.isActive() ? "container.mymod.neuralink.active" : "container.mymod.neuralink.ready";
				graphics.text(font, "▪ " + Component.translatable(state).getString(), x, y + 9, DIM, false);
			} else {
				graphics.text(font, Component.translatable("container.mymod.neuralink.socket", socket + 1).getString(), x, y, DIM, false);
				graphics.text(font, "— " + Component.translatable("container.mymod.neuralink.empty").getString() + " —", x, y + 9, DARK, false);
			}
		}

		graphics.text(font, Component.translatable("container.mymod.neuralink.storage").getString(), NeuralinkMenu.INVENTORY_X - 1,
				layout.inventoryY() - 11, DIM, false);
		String hint = Component.translatable(menu.isHelmet() ? "container.mymod.neuralink.hint_helmet" : "container.mymod.neuralink.hint").getString();
		graphics.text(font, hint, imageWidth - 9 - font.width(hint), layout.inventoryY() - 11, DARK, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && hoveredSlot.index < sockets() && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
			graphics.setTooltipForNextFrame(font, EMPTY_SOCKET, mouseX, mouseY);
		}
	}
}
