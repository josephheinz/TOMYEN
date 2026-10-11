package com.jheinz.mymod.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.station.WeaponStationMenu;

/** Three input slots, an arrow and a result slot over the player's inventory. */
public class WeaponStationScreen extends AbstractContainerScreen<WeaponStationMenu> {
	private static final Identifier BACKGROUND = MyMod.id("textures/gui/container/weapon_station.png");

	public WeaponStationScreen(WeaponStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
	}
}
