package com.jheinz.mymod.client.industry;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.Industry;

/** The industry's client side: crude oil's look, the machines' screens and their moving parts. */
public final class IndustryClient {
	private IndustryClient() {
	}

	public static void initialize() {
		FluidRenderingRegistry.register(Industry.CRUDE_OIL, Industry.FLOWING_CRUDE_OIL, new FluidModel.Unbaked(
				new Material(MyMod.id("block/crude_oil_still")), new Material(MyMod.id("block/crude_oil_flow")), null, null));
		MenuScreens.register(Industry.PUMP_JACK_MENU, PumpJackScreen::new);
		MenuScreens.register(Industry.REFINERY_MENU, RefineryScreen::new);
		ModelLayerRegistry.registerModelLayer(PumpJackRenderer.LAYER, PumpJackRenderer::createLayer);
		ModelLayerRegistry.registerModelLayer(MetalPressRenderer.LAYER, MetalPressRenderer::createLayer);
		BlockEntityRendererRegistry.register(Industry.PUMP_JACK_ENTITY, PumpJackRenderer::new);
		BlockEntityRendererRegistry.register(Industry.METAL_PRESS_ENTITY, MetalPressRenderer::new);
	}
}
