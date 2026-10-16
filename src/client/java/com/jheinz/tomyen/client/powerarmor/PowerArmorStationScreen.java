package com.jheinz.tomyen.client.powerarmor;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.powerarmor.FusionCoreItem;
import com.jheinz.tomyen.powerarmor.PowerArmorPiece;
import com.jheinz.tomyen.powerarmor.PowerArmorStationMenu;
import com.jheinz.tomyen.powerarmor.PowerArmorSuit;
import com.jheinz.tomyen.powerarmor.PowerArmorTier;

/**
 * The Power Armor Station's rack, as a green-on-black maintenance terminal: the piece slots laid out as a body
 * wired together, a readout of each piece's mark and condition, the fusion core's charge, and whether the suit
 * is ready to climb into (and how).
 */
public class PowerArmorStationScreen extends AbstractContainerScreen<PowerArmorStationMenu> {
	private static final int WIDTH = 176;
	private static final int PANEL_TOP = 0xF2101610;
	private static final int PANEL_BOTTOM = 0xF2080B08;
	private static final int GREEN = 0xFF5CFF7A;
	private static final int DIM = 0xFF2F9A45;
	private static final int DARK = 0xFF1B4D27;
	private static final int AMBER = 0xFFFFB000;
	private static final int RED = 0xFFFF4A3A;
	private static final int READOUT_X = 89;
	private static final int READOUT_RIGHT = WIDTH - 6;
	private static final int READOUT_Y = 18;
	private static final int LINE = 12;

	public PowerArmorStationScreen(PowerArmorStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, PowerArmorStationMenu.HEIGHT);
	}

	private static int slotCenterX(int slot) {
		return PowerArmorStationMenu.SLOT_POSITIONS[slot][0] + 8;
	}

	private static int slotCenterY(int slot) {
		return PowerArmorStationMenu.SLOT_POSITIONS[slot][1] + 8;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
		long now = Util.getMillis();
		graphics.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOTTOM);
		// Scanlines, and a frame.
		for (int y = y0 + 1 + (int) (now / 60 % 3); y < y1; y += 3) {
			graphics.horizontalLine(x0 + 1, x1 - 2, y, 0x0C5CFF7A);
		}
		graphics.outline(x0, y0, imageWidth, imageHeight, DIM);
		graphics.outline(x0 + 2, y0 + 2, imageWidth - 4, imageHeight - 4, 0x402F9A45);
		graphics.horizontalLine(x0 + 6, x1 - 7, y0 + 15, DARK);

		// The frame the pieces hang on: a spine from helmet to core, shoulders and hips.
		int sx = x0 + slotCenterX(0);
		graphics.verticalLine(sx, y0 + slotCenterY(0), y0 + slotCenterY(PowerArmorSuit.CORE), DARK);
		graphics.horizontalLine(x0 + slotCenterX(PowerArmorPiece.RIGHT_ARM.slot()), x0 + slotCenterX(PowerArmorPiece.LEFT_ARM.slot()),
				y0 + slotCenterY(PowerArmorPiece.TORSO.slot()), DARK);
		graphics.horizontalLine(x0 + slotCenterX(PowerArmorPiece.RIGHT_LEG.slot()), x0 + slotCenterX(PowerArmorPiece.LEFT_LEG.slot()),
				y0 + slotCenterY(PowerArmorPiece.LEFT_LEG.slot()) - 12, DARK);
		for (PowerArmorPiece leg : new PowerArmorPiece[] {PowerArmorPiece.RIGHT_LEG, PowerArmorPiece.LEFT_LEG}) {
			graphics.verticalLine(x0 + slotCenterX(leg.slot()), y0 + slotCenterY(leg.slot()) - 12, y0 + slotCenterY(leg.slot()), DARK);
		}

		PowerArmorSuit suit = menu.suit();
		for (Slot slot : menu.slots) {
			int bx = x0 + slot.x - 1, by = y0 + slot.y - 1;
			graphics.fill(bx, by, bx + 18, by + 18, 0xFF060906);
			int border = DARK;
			if (slot.index < PowerArmorSuit.SIZE) {
				border = slot.index == PowerArmorSuit.CORE ? (suit.hasCore() ? coreColour(suit.core()) : DIM) : pieceColour(suit, slot.index);
			}
			graphics.outline(bx, by, 18, 18, border);
		}
	}

	/** A fitted piece is outlined in its mark's colour; an empty slot dimly. */
	private static int pieceColour(PowerArmorSuit suit, int slot) {
		PowerArmorTier tier = suit.tier(PowerArmorPiece.values()[slot]);
		return tier == null ? DIM : 0xFF000000 | tier.color();
	}

	private static int coreColour(ItemStack core) {
		float charge = FusionCoreItem.fraction(core);
		return charge <= 0.0f ? RED : charge < 0.15f ? AMBER : GREEN;
	}

	private static int conditionColour(float condition) {
		return condition <= 0.0f ? RED : condition < 0.25f ? 0xFFFF7A3A : condition < 0.6f ? AMBER : GREEN;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		long now = Util.getMillis();
		graphics.text(font, title.getString().toUpperCase(Locale.ROOT), 8, 5, GREEN, false);
		String cursor = now % 1000 < 500 ? "▮" : "";
		graphics.text(font, cursor, imageWidth - 12, 5, GREEN, false);

		PowerArmorSuit suit = menu.suit();
		int y = READOUT_Y;
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			graphics.text(font, piece.shortName().getString(), READOUT_X, y, DIM, false);
			PowerArmorTier tier = suit.tier(piece);
			if (tier == null) {
				graphics.text(font, "---", READOUT_X + 25, y, DARK, false);
			} else {
				float condition = suit.condition(piece);
				graphics.text(font, "Mk" + tier.numeral(), READOUT_X + 25, y, 0xFF000000 | tier.color(), false);
				String percent = condition <= 0.0f ? "BRKN" : Math.round(condition * 100.0f) + "%";
				graphics.text(font, percent, READOUT_RIGHT - font.width(percent), y, conditionColour(condition), false);
				bar(graphics, READOUT_X, y + 9, READOUT_RIGHT, condition, conditionColour(condition));
			}
			y += LINE;
		}

		// The core.
		y += 4;
		graphics.text(font, Component.translatable("container.tomyen.power_armor_station.core").getString(), READOUT_X, y, DIM, false);
		if (suit.hasCore()) {
			float charge = FusionCoreItem.fraction(suit.core());
			String percent = Mth.ceil(charge * 100.0f) + "%";
			graphics.text(font, percent, READOUT_RIGHT - font.width(percent), y, coreColour(suit.core()), false);
			bar(graphics, READOUT_X, y + 9, READOUT_RIGHT, charge, coreColour(suit.core()));
		} else {
			graphics.text(font, "---", READOUT_RIGHT - font.width("---"), y, DARK, false);
		}

		// What's still needed, or how to get in.
		int missing = 0;
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			if (suit.tier(piece) == null) {
				missing++;
			}
		}
		Component state;
		int colour;
		if (missing > 0) {
			state = Component.translatable("container.tomyen.power_armor_station.missing", missing);
			colour = AMBER;
		} else if (!suit.hasCore()) {
			state = Component.translatable("container.tomyen.power_armor_station.no_core");
			colour = AMBER;
		} else {
			state = Component.translatable("container.tomyen.power_armor_station.ready");
			colour = now % 1200 < 900 ? GREEN : DIM;
		}
		graphics.centeredText(font, state.getString().toUpperCase(Locale.ROOT), (READOUT_X + READOUT_RIGHT) / 2, 109, colour);
		graphics.centeredText(font, Component.translatable("container.tomyen.power_armor_station.hint").getString(), imageWidth / 2, 120, DARK);
		graphics.text(font, playerInventoryTitle.getString().toUpperCase(Locale.ROOT), PowerArmorStationMenu.INVENTORY_X,
				PowerArmorStationMenu.INVENTORY_Y - 10, DIM, false);
	}

	private static void bar(GuiGraphicsExtractor graphics, int x0, int y, int x1, float fraction, int colour) {
		graphics.fill(x0, y, x1, y + 1, 0xFF0F2A16);
		graphics.fill(x0, y, x0 + Math.round((x1 - x0) * Mth.clamp(fraction, 0.0f, 1.0f)), y + 1, colour);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && hoveredSlot.index < PowerArmorSuit.SIZE && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
			Component slot = hoveredSlot.index == PowerArmorSuit.CORE ? Component.translatable("container.tomyen.power_armor_station.core_slot")
					: Component.translatable("container.tomyen.power_armor_station.piece_slot", PowerArmorPiece.values()[hoveredSlot.index].displayName());
			graphics.setTooltipForNextFrame(font, slot, mouseX, mouseY);
		}
	}
}
