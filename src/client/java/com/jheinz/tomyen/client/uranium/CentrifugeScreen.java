package com.jheinz.tomyen.client.uranium;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.uranium.CentrifugeMenu;

/** Raw uranium over the fuel slot under vanilla's flame, the progress arrow, and the enriched and depleted outputs. */
public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/centrifuge.png");
	private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	// Matches tools/gen_ores_tu.py.
	private static final int FLAME_X = 57;
	private static final int FLAME_Y = 37;
	private static final int ARROW_X = 82;
	private static final int ARROW_Y = 35;

	public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
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
		int flame = Mth.ceil(menu.burn() * 13.0f);
		if (flame > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 14 - flame, leftPos + FLAME_X, topPos + FLAME_Y + 14 - flame,
					14, flame);
		}
		int arrow = Mth.ceil(menu.progress() * 24.0f);
		if (arrow > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, arrow, 16);
		}
	}
}
