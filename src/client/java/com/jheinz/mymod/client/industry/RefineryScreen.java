package com.jheinz.mymod.client.industry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.RefineryMenu;
import com.jheinz.mymod.industry.RefineryRecipe;

/** The three inputs, the fuel slot under vanilla's flame, the progress arrow labelled with the process, and the outputs. */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/refinery.png");
	private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	// Matches tools/gen_industry.py.
	private static final int FLAME_X = 45;
	private static final int FLAME_Y = 37;
	private static final int ARROW_X = 86;
	private static final int ARROW_Y = 35;
	private static final int LABEL_COLOUR = 0xFF404040;

	public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
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
		RefineryRecipe recipe = menu.recipe();
		if (recipe != null) {
			Component label = Component.translatable("container.mymod.refinery." + recipe.process());
			graphics.text(font, label, leftPos + ARROW_X + 12 - font.width(label) / 2, topPos + ARROW_Y - 11, LABEL_COLOUR, false);
		}
	}
}
