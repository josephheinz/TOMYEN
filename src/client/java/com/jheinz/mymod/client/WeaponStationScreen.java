package com.jheinz.mymod.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.scrapbench.BlueprintPanel;
import com.jheinz.mymod.station.WeaponStationMenu;

/**
 * Four input slots, an arrow and a result slot over the player's inventory, with the blueprint panel alongside (see
 * {@link BlueprintPanel}).
 */
public class WeaponStationScreen extends AbstractContainerScreen<WeaponStationMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/weapon_station.png");

	public WeaponStationScreen(WeaponStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
		BlueprintPanel.extract(graphics, font, minecraft.player, leftPos, topPos, imageWidth);
		BlueprintPanel.extractLockedResult(graphics, menu, minecraft.player, WeaponStationMenu.INPUT_COUNT,
				leftPos + WeaponStationMenu.RESULT_X, topPos + WeaponStationMenu.SLOT_Y);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		BlueprintPanel.extractTooltip(graphics, font, menu, minecraft.player, leftPos, topPos, imageWidth, WeaponStationMenu.INPUT_COUNT,
				WeaponStationMenu.RESULT_X, WeaponStationMenu.SLOT_Y, mouseX, mouseY);
	}

	@Override
	protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
		return super.hasClickedOutside(mx, my, xo, yo) && !BlueprintPanel.contains(mx, my, leftPos, topPos, imageWidth);
	}
}
