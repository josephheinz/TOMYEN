package com.jheinz.tomyen.client.fusion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fusion.FusionReactorMenu;

/**
 * The Fusion Reactor's screen: the two inputs feed beams into the containment ring, whose core brightens as the fusion
 * goes on, and then the arrow carries it to the output. The fuel gauge drains beside the fuel slot. Everything is drawn
 * from one texture (tools/gen_fusion.py): the background, then the overlays to its right.
 */
public class FusionReactorScreen extends AbstractContainerScreen<FusionReactorMenu> {
	private static final Identifier TEXTURE = MyMod.id("textures/gui/container/fusion_reactor.png");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	// Matches tools/gen_fusion.py: where each overlay is drawn, and where it sits in the texture.
	static final int BEAM_A_X = 44, BEAM_A_Y = 22, BEAM_A_W = 30, BEAM_A_H = 12, BEAM_A_U = 176, BEAM_A_V = 0;
	static final int BEAM_B_X = 44, BEAM_B_Y = 38, BEAM_B_W = 30, BEAM_B_H = 26, BEAM_B_U = 176, BEAM_B_V = 12;
	static final int CORE_X = 76, CORE_Y = 24, CORE_SIZE = 24, CORE_U = 176, CORE_V = 38, CORE_FRAMES = 4;
	static final int FUEL_X = 72, FUEL_Y = 60, FUEL_W = 4, FUEL_H = 16, FUEL_U = 208, FUEL_V = 0;
	static final int ARROW_X = 106, ARROW_Y = 35;

	public FusionReactorScreen(FusionReactorMenu menu, Inventory inventory, Component title) {
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
		int fuel = Mth.ceil(menu.burn() * FUEL_H);
		if (fuel > 0) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + FUEL_X, topPos + FUEL_Y + FUEL_H - fuel, FUEL_U,
					FUEL_V + FUEL_H - fuel, FUEL_W, fuel, 256, 256);
		}
		float progress = menu.progress();
		if (progress <= 0.0f) {
			return;
		}
		// The beams run in over the first half, the arrow out over the second.
		float in = Math.min(1.0f, progress * 2.0f);
		int a = Mth.ceil(in * BEAM_A_W);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + BEAM_A_X, topPos + BEAM_A_Y, BEAM_A_U, BEAM_A_V, a, BEAM_A_H, 256, 256);
		int b = Mth.ceil(in * BEAM_B_W);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + BEAM_B_X, topPos + BEAM_B_Y, BEAM_B_U, BEAM_B_V, b, BEAM_B_H, 256, 256);
		int frame = Math.min(CORE_FRAMES - 1, (int) (progress * CORE_FRAMES));
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + CORE_X, topPos + CORE_Y, CORE_U, CORE_V + frame * CORE_SIZE, CORE_SIZE,
				CORE_SIZE, 256, 256);
		float out = Math.max(0.0f, progress * 2.0f - 1.0f);
		int arrow = Mth.ceil(out * 24.0f);
		if (arrow > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, leftPos + ARROW_X, topPos + ARROW_Y, arrow, 16);
		}
	}
}
