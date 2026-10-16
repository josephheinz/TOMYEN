package com.jheinz.tomyen.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.foundry.FoundryBlockEntity;
import com.jheinz.tomyen.foundry.FoundryMenu;

/**
 * Two inputs, the lava slot, a tank showing how much heat is left, vanilla's progress arrow, and a thermometer with
 * a mark at the temperature the current recipe needs (white until it's reached, then green), or, for a quench, a
 * pale blue one at the most it allows.
 */
public class FoundryScreen extends AbstractContainerScreen<FoundryMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/foundry.png");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	// Matches tools/gen_foundry.py.
	private static final int ARROW_X = 80;
	private static final int ARROW_Y = 35;
	private static final int TANK_X0 = 21;
	private static final int TANK_Y0 = 17;
	private static final int TANK_X1 = 33;
	private static final int TANK_Y1 = 70;
	private static final int THERMOMETER_X0 = 152;
	private static final int THERMOMETER_X1 = 158;

	public FoundryScreen(FoundryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		titleLabelX = (imageWidth - font.width(title)) / 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

		// Lava in the tank, its surface shimmering.
		int level = Mth.ceil(menu.heat() * (TANK_Y1 - TANK_Y0));
		if (level > 0) {
			int x0 = leftPos + TANK_X0, x1 = leftPos + TANK_X1, bottom = topPos + TANK_Y1, top = bottom - level;
			graphics.fillGradient(x0, top, x1, bottom, 0xFFFFA126, 0xFFC2360C);
			long now = Util.getMillis();
			for (int i = 0; i < 3; i++) {
				int x = x0 + (int) ((now / (90 + i * 37) + i * 5) % (x1 - x0));
				graphics.fill(x, top, Math.min(x1, x + 2), top + 1, 0xFFFFF27A);
			}
			for (int y = top + 3; y < bottom; y += 7) {
				int x = x0 + (int) ((now / 160 + y * 3) % (x1 - x0 - 1));
				graphics.fill(x, y, x + 1, y + 1, 0xFFFFD34A);
			}
		}

		extractThermometer(graphics);

		int arrow = Mth.ceil(menu.progress() * 24.0f);
		if (arrow > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, arrow, 16);
		}
	}

	/** A glass tube, its column rising from dull red to white-hot, with a notch at the recipe's temperature. */
	private void extractThermometer(GuiGraphicsExtractor graphics) {
		int x0 = leftPos + THERMOMETER_X0, x1 = leftPos + THERMOMETER_X1, top = topPos + TANK_Y0, bottom = topPos + TANK_Y1;
		graphics.fill(x0 - 1, top - 1, x1 + 1, bottom + 1, 0xFF373737);
		graphics.fill(x0, top, x1, bottom, 0xFF1E1A18);
		float hot = Mth.clamp((float) menu.temperature() / FoundryBlockEntity.MAX_TEMPERATURE, 0.0f, 1.0f);
		int column = Mth.ceil(hot * (bottom - top));
		if (column > 0) {
			graphics.fillGradient(x0, bottom - column, x1, bottom, hot > 0.8f ? 0xFFFFF4C2 : hot > 0.5f ? 0xFFFFB13B : 0xFFE0521A, 0xFF8E1A0C);
		}
		int required = menu.requiredTemperature();
		if (required > 0) {
			int y = bottom - Math.round((float) required / FoundryBlockEntity.MAX_TEMPERATURE * (bottom - top));
			int colour = menu.temperature() >= required ? 0xFF55FF55 : 0xFFFFFFFF;
			graphics.fill(x0 - 3, y, x0, y + 1, colour);
			graphics.fill(x1, y, x1 + 3, y + 1, colour);
		}
		int max = menu.maxTemperature();
		if (max > 0) {
			int y = bottom - Math.round((float) max / FoundryBlockEntity.MAX_TEMPERATURE * (bottom - top));
			int colour = menu.temperature() <= max ? 0xFF9FD8FF : 0xFFFFFFFF;
			graphics.fill(x0 - 3, y, x0, y + 1, colour);
			graphics.fill(x1, y, x1 + 3, y + 1, colour);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (isHovering(THERMOMETER_X0 - 3, TANK_Y0, THERMOMETER_X1 - THERMOMETER_X0 + 6, TANK_Y1 - TANK_Y0, mouseX, mouseY)) {
			int required = menu.requiredTemperature();
			int max = menu.maxTemperature();
			Component text = required > 0
					? Component.translatable("container.tomyen.foundry.temperature_needed", menu.temperature(), required)
					: max > 0 ? Component.translatable("container.tomyen.foundry.temperature_max", menu.temperature(), max)
					: Component.translatable("container.tomyen.foundry.temperature", menu.temperature());
			graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
		}
		if (isHovering(TANK_X0, TANK_Y0, TANK_X1 - TANK_X0, TANK_Y1 - TANK_Y0, mouseX, mouseY)) {
			float heat = menu.heat();
			Component text = heat <= 0.0f ? Component.translatable("container.tomyen.foundry.cold")
					: Component.translatable("container.tomyen.foundry.heat", Mth.ceil(heat * 100.0f),
							Mth.ceil(heat * FoundryBlockEntity.HEAT_PER_BUCKET / 200.0f));
			graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
		}
	}
}
