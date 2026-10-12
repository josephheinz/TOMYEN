package com.jheinz.mymod.client.tank;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.tank.Tank;
import com.jheinz.mymod.tank.Tanks;

/**
 * Tank rendering and driving. The attack key fires the cannon while driving: it's taken before vanilla
 * sees it, so the driver doesn't punch at whatever's in front of them, and sent to the server, which
 * checks the reload. Under the crosshair, a readout shows when the cannon is ready again.
 */
public final class TankClient {
	private static final int READY = 0xFF55FF55;
	private static final int LOADING = 0xFFFFAA00;
	private static final int BAR_BACK = 0x80000000;

	private TankClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Tanks.TANK, TankRenderer::new);
		ModelLayerRegistry.registerModelLayer(TankModel.LAYER, TankModel::createBodyLayer);
		EntityRendererRegistry.register(Tanks.TANK_SHELL_ENTITY, ThrownItemRenderer::new);
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (drivenTank(client) != null) {
				while (client.options.keyAttack.consumeClick()) {
					ClientPlayNetworking.send(Tanks.FireCannon.INSTANCE);
				}
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, MyMod.id("tank_cannon"), TankClient::extractHud);
	}

	private static Tank drivenTank(Minecraft client) {
		if (client.player != null && client.player.getVehicle() instanceof Tank tank && tank.getControllingPassenger() == client.player) {
			return tank;
		}
		return null;
	}

	private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Tank tank = drivenTank(minecraft);
		if (tank == null) {
			return;
		}
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 12;
		float progress = tank.reloadProgress();
		boolean ready = progress >= 1.0f;
		Component label = Component.translatable(ready ? "hud.mymod.tank.ready" : "hud.mymod.tank.reloading");
		graphics.centeredText(minecraft.font, label, x, y, ready ? READY : LOADING);
		if (!ready) {
			graphics.fill(x - 20, y + 10, x + 20, y + 12, BAR_BACK);
			graphics.fill(x - 20, y + 10, x - 20 + Math.round(40 * progress), y + 12, LOADING);
		}
	}
}
