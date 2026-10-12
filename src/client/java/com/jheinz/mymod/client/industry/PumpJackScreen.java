package com.jheinz.mymod.client.industry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.PumpJackBlockEntity;
import com.jheinz.mymod.industry.PumpJackMenu;

/** Empty canisters in, an oil gauge filling as it pumps, full canisters out, and a line on how the well is doing. */
public class PumpJackScreen extends AbstractContainerScreen<PumpJackMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/pump_jack.png");
	// Matches tools/gen_industry.py: the gauge between the slots.
	private static final int GAUGE_X0 = 68;
	private static final int GAUGE_Y0 = 37;
	private static final int GAUGE_X1 = 108;
	private static final int GAUGE_Y1 = 49;
	private static final int STATUS_Y = 62;

	public PumpJackScreen(PumpJackMenu menu, Inventory inventory, Component title) {
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
		int fill = Mth.ceil(menu.progress() * (GAUGE_X1 - GAUGE_X0));
		if (fill > 0) {
			graphics.fillGradient(leftPos + GAUGE_X0, topPos + GAUGE_Y0, leftPos + GAUGE_X0 + fill, topPos + GAUGE_Y1, 0xFF3A3330, 0xFF0E0B0A);
			graphics.fill(leftPos + GAUGE_X0, topPos + GAUGE_Y0, leftPos + GAUGE_X0 + fill, topPos + GAUGE_Y0 + 1, 0xFF6E6258);
		}
		int status = menu.status();
		String key = switch (status) {
			case PumpJackBlockEntity.STATUS_NO_SEEP -> "no_seep";
			case PumpJackBlockEntity.STATUS_DRY -> "dry";
			case PumpJackBlockEntity.STATUS_PUMPING -> "pumping";
			default -> "idle";
		};
		int colour = status == PumpJackBlockEntity.STATUS_PUMPING ? 0xFF2E7D32 : status == PumpJackBlockEntity.STATUS_IDLE ? 0xFF404040 : 0xFFB02020;
		Component text = Component.translatable("container.mymod.pump_jack." + key);
		graphics.text(font, text, leftPos + (imageWidth - font.width(text)) / 2, topPos + STATUS_Y, colour, false);
	}
}
