package com.jheinz.tomyen.client.mech;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.mech.MechGantryMenu;
import com.jheinz.tomyen.mech.MechLoadout;
import com.jheinz.tomyen.powerarmor.FusionCoreItem;

/**
 * The Mech Gantry's assembly terminal, amber on black: the slots wired together as the walker seen from the
 * front, the power plant and its core off to one side, each part's condition, and what's still needed (or how to
 * walk it out).
 */
public class MechGantryScreen extends AbstractContainerScreen<MechGantryMenu> {
	private static final int WIDTH = 176;
	private static final int PANEL_TOP = 0xF2161310;
	private static final int PANEL_BOTTOM = 0xF20B0907;
	private static final int AMBER = 0xFFFFB347;
	private static final int DIM = 0xFFA06A28;
	private static final int DARK = 0xFF4D3418;
	private static final int GREEN = 0xFF6CFF8A;
	private static final int RED = 0xFFFF4A3A;
	private static final String[] SLOT_KEYS = {"torso", "legs", "left_arm", "right_arm", "power_plant", "core", "left_weapon",
			"right_weapon", "shoulder"};

	public MechGantryScreen(MechGantryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, MechGantryMenu.HEIGHT);
	}

	private static int cx(int slot) {
		return MechGantryMenu.SLOT_POSITIONS[slot][0] + 8;
	}

	private static int cy(int slot) {
		return MechGantryMenu.SLOT_POSITIONS[slot][1] + 8;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
		graphics.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOTTOM);
		graphics.outline(x0, y0, imageWidth, imageHeight, DIM);
		graphics.outline(x0 + 2, y0 + 2, imageWidth - 4, imageHeight - 4, 0x40A06A28);

		// The walker's skeleton: spine from shoulder to legs, a yoke across the arms out to the hardpoints, and the
		// power plant's feed to the core.
		graphics.verticalLine(x0 + cx(MechLoadout.TORSO), y0 + cy(MechLoadout.SHOULDER), y0 + cy(MechLoadout.LEGS), DARK);
		graphics.horizontalLine(x0 + cx(MechLoadout.RIGHT_WEAPON), x0 + cx(MechLoadout.LEFT_WEAPON), y0 + cy(MechLoadout.TORSO), DARK);
		graphics.verticalLine(x0 + cx(MechLoadout.RIGHT_WEAPON), y0 + cy(MechLoadout.TORSO), y0 + cy(MechLoadout.RIGHT_WEAPON), DARK);
		graphics.verticalLine(x0 + cx(MechLoadout.LEFT_WEAPON), y0 + cy(MechLoadout.TORSO), y0 + cy(MechLoadout.LEFT_WEAPON), DARK);
		graphics.verticalLine(x0 + cx(MechLoadout.POWER_PLANT), y0 + cy(MechLoadout.POWER_PLANT), y0 + cy(MechLoadout.CORE), DARK);

		List<ItemStack> parts = menu.parts();
		for (Slot slot : menu.slots) {
			int bx = x0 + slot.x - 1, by = y0 + slot.y - 1;
			graphics.fill(bx, by, bx + 18, by + 18, 0xFF0A0806);
			int border = DARK;
			if (slot.index < MechLoadout.SIZE) {
				ItemStack part = parts.get(slot.index);
				border = part.isEmpty() ? (slot.index <= MechLoadout.POWER_PLANT ? DIM : DARK) : condition(part);
			}
			graphics.outline(bx, by, 18, 18, border);
		}
	}

	/** A part's colour: its wear, or a core's charge. */
	private static int condition(ItemStack part) {
		float fraction = part.getItem() instanceof FusionCoreItem ? FusionCoreItem.fraction(part)
				: part.isDamageableItem() ? 1.0f - part.getDamageValue() / (float) part.getMaxDamage() : 1.0f;
		return fraction <= 0.0f ? RED : fraction < 0.35f ? 0xFFFF7A3A : fraction < 0.7f ? AMBER : GREEN;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title.getString().toUpperCase(Locale.ROOT), 8, 5, AMBER, false);
		List<ItemStack> parts = menu.parts();

		// Under the walker: the core's charge, then what's missing or how to get in.
		ItemStack core = parts.get(MechLoadout.CORE);
		int y = 94;
		String coreText = core.isEmpty() ? "---" : Mth.ceil(FusionCoreItem.fraction(core) * 100.0f) + "%";
		graphics.text(font, Component.translatable("container.tomyen.mech_gantry.core", coreText).getString(), 8, y,
				core.isEmpty() ? DIM : condition(core), false);
		int missing = 0;
		for (int slot = MechLoadout.TORSO; slot <= MechLoadout.POWER_PLANT; slot++) {
			if (parts.get(slot).isEmpty()) {
				missing++;
			}
		}
		Component state;
		int colour;
		if (MechLoadout.empty(parts)) {
			state = Component.translatable("container.tomyen.mech_gantry.empty");
			colour = DIM;
		} else if (missing > 0) {
			state = Component.translatable("container.tomyen.mech_gantry.missing", missing);
			colour = AMBER;
		} else {
			state = Component.translatable("container.tomyen.mech_gantry.ready");
			colour = GREEN;
		}
		graphics.text(font, state.getString().toUpperCase(Locale.ROOT), 8, y + 11, colour, false);
		graphics.text(font, Component.translatable(missing > 0 || MechLoadout.empty(parts) ? "container.tomyen.mech_gantry.hint_dock"
				: "container.tomyen.mech_gantry.hint_launch").getString(), 8, y + 22, DIM, false);
		graphics.text(font, playerInventoryTitle.getString().toUpperCase(Locale.ROOT), MechGantryMenu.INVENTORY_X, MechGantryMenu.INVENTORY_Y - 10,
				DIM, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (hoveredSlot != null && hoveredSlot.index < MechLoadout.SIZE && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
			graphics.setTooltipForNextFrame(font, Component.translatable("container.tomyen.mech_gantry.slot." + SLOT_KEYS[hoveredSlot.index]),
					mouseX, mouseY);
		}
	}
}
