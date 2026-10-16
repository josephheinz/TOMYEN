package com.jheinz.tomyen.client.bloomery;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bloomery.BloomeryBlockEntity;
import com.jheinz.tomyen.bloomery.BloomeryMenu;

/**
 * The Bloomery's screen: ore over fuel (a flame between them burns down with the charge), an arrow filling as the
 * bloom gathers, the bloom, the forge step's hammer and the ingot it makes. Down the left a thermometer, marked at a
 * bloom heat and a white heat, and with a tick at the most the burning fuel allows. Along the bottom the bellows and
 * its gauge ({@link BloomeryGauges}): click the bellows, or press Space, when the gauge is in the green.
 */
public class BloomeryScreen extends AbstractContainerScreen<BloomeryMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/bloomery.png");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	// Matches tools/gen_bloomery.py.
	static final int THERMO_X0 = 15, THERMO_X1 = 21, THERMO_Y0 = 17, THERMO_Y1 = 64;
	static final int FLAME_X = 39, FLAME_Y = 36;
	static final int ARROW_X = 62, ARROW_Y = 35;
	static final int HAMMER_X = 118, HAMMER_Y = 35, HAMMER_SIZE = 16;
	static final int PIPS_X = 119, PIPS_Y = 30;
	static final int BELLOWS_X = 60, BELLOWS_Y = 55, BELLOWS_W = 26, BELLOWS_H = 16;
	static final int GAUGE_X0 = 92, GAUGE_X1 = 166, GAUGE_Y0 = 60, GAUGE_Y1 = 66;
	// The button sprites, under the panel in the background texture: three states side by side.
	static final int SPRITE_V = 166, BELLOWS_U = 0, HAMMER_U = 78;
	private static final int TEXT = 0xFF404040;

	/** Ticks the bellows sprite shows pressed after a click. */
	private int pressedTicks;

	public BloomeryScreen(BloomeryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		titleLabelX = (imageWidth - font.width(title)) / 2;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		pressedTicks = Math.max(0, pressedTicks - 1);
	}

	private void pump() {
		pressedTicks = 4;
		minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BloomeryMenu.PUMP);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (isHovering(BELLOWS_X, BELLOWS_Y, BELLOWS_W, BELLOWS_H, event.x(), event.y())) {
				pump();
				return true;
			}
			if (isHovering(HAMMER_X, HAMMER_Y, HAMMER_SIZE, HAMMER_SIZE, event.x(), event.y()) && menu.hasBloom()) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BloomeryMenu.FORGE);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_SPACE) {
			pump();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int x = leftPos, y = topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

		if (menu.lit()) {
			int h = Mth.ceil(menu.burn() * 13.0f) + 1;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 14 - h, x + FLAME_X, y + FLAME_Y + 14 - h, 14, h);
		}
		int arrow = Mth.ceil(menu.progress() * 24.0f);
		if (arrow > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, x + ARROW_X, y + ARROW_Y, arrow, 16);
		}

		BloomeryGauges.thermometer(graphics, x + THERMO_X0, y + THERMO_Y0, x + THERMO_X1, y + THERMO_Y1, menu.temperature(), menu.fuel());

		// The bellows: pressed for a moment after a stroke, lit up under the mouse.
		boolean hoverBellows = isHovering(BELLOWS_X, BELLOWS_Y, BELLOWS_W, BELLOWS_H, mouseX, mouseY);
		int bellowsState = pressedTicks > 0 ? 2 : hoverBellows ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x + BELLOWS_X, y + BELLOWS_Y, BELLOWS_U + bellowsState * BELLOWS_W, SPRITE_V,
				BELLOWS_W, BELLOWS_H, 256, 256);
		float since = menu.sinceStroke() >= BloomeryBlockEntity.SINCE_CAP ? BloomeryBlockEntity.SINCE_CAP : menu.sinceStroke() + partialTick;
		BloomeryGauges.breath(graphics, x + GAUGE_X0, y + GAUGE_Y0, x + GAUGE_X1, y + GAUGE_Y1, since);

		// The hammer, greyed out until there's a bloom to work, and a pip per blow struck.
		boolean canForge = menu.hasBloom();
		boolean hoverHammer = canForge && isHovering(HAMMER_X, HAMMER_Y, HAMMER_SIZE, HAMMER_SIZE, mouseX, mouseY);
		int hammerState = !canForge ? 2 : hoverHammer ? 1 : 0;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x + HAMMER_X, y + HAMMER_Y, HAMMER_U + hammerState * HAMMER_SIZE, SPRITE_V,
				HAMMER_SIZE, HAMMER_SIZE, 256, 256);
		for (int i = 0; i < BloomeryBlockEntity.FORGE_BLOWS; i++) {
			int px = x + PIPS_X + i * 6;
			graphics.fill(px, y + PIPS_Y, px + 4, y + PIPS_Y + 2, i < menu.forgeBlows() ? 0xFFFFB43A : 0xFF5A5148);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		Component result = BloomeryGauges.strokeText(menu.stroke(), menu.combo(), menu.sinceStroke());
		if (result != null) {
			graphics.text(font, result, GAUGE_X1 - font.width(result), GAUGE_Y1 + 3, BloomeryGauges.strokeColour(menu.stroke()), false);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (isHovering(THERMO_X0 - 3, THERMO_Y0, THERMO_X1 - THERMO_X0 + 6, THERMO_Y1 - THERMO_Y0, mouseX, mouseY)) {
			int t = menu.temperature();
			String key = t >= BloomeryBlockEntity.WHITE_HEAT ? "white" : t >= BloomeryBlockEntity.BLOOM_TEMPERATURE ? "bloom"
					: menu.lit() ? "rising" : "cold";
			graphics.setTooltipForNextFrame(font, Component.translatable("container.tomyen.bloomery.temperature." + key, t), mouseX, mouseY);
		} else if (isHovering(BELLOWS_X, BELLOWS_Y, BELLOWS_W, BELLOWS_H, mouseX, mouseY)
				|| isHovering(GAUGE_X0, GAUGE_Y0, GAUGE_X1 - GAUGE_X0, GAUGE_Y1 - GAUGE_Y0, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, Component.translatable("container.tomyen.bloomery.bellows"), mouseX, mouseY);
		} else if (isHovering(HAMMER_X, HAMMER_Y, HAMMER_SIZE, HAMMER_SIZE, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, Component.translatable(menu.temperature() >= BloomeryBlockEntity.FORGE_TEMPERATURE
					? "container.tomyen.bloomery.forge" : "container.tomyen.bloomery.forge.cold", BloomeryBlockEntity.FORGE_TEMPERATURE), mouseX, mouseY);
		}
	}
}
