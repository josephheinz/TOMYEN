package com.jheinz.mymod.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.foundry.FoundryBlockEntity;
import com.jheinz.mymod.foundry.FoundryMenu;

/** Two inputs, the lava slot, a tank showing how much heat is left, and vanilla's progress arrow. */
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

		int arrow = Mth.ceil(menu.progress() * 24.0f);
		if (arrow > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, arrow, 16);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (isHovering(TANK_X0, TANK_Y0, TANK_X1 - TANK_X0, TANK_Y1 - TANK_Y0, mouseX, mouseY)) {
			float heat = menu.heat();
			Component text = heat <= 0.0f ? Component.translatable("container.mymod.foundry.cold")
					: Component.translatable("container.mymod.foundry.heat", Mth.ceil(heat * 100.0f),
							Mth.ceil(heat * FoundryBlockEntity.HEAT_PER_BUCKET / 200.0f));
			graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
		}
	}
}
