package com.jheinz.tomyen.client.accountant;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.gui.screens.MenuScreens;

import com.jheinz.tomyen.accountant.Accountants;

/** The Accountant's renderer and model layers, and his loan office screen. */
public final class AccountantClient {
	private AccountantClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Accountants.ACCOUNTANT, AccountantRenderer::new);
		ModelLayerRegistry.registerModelLayer(AccountantModel.LAYER, AccountantModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(AccountantModel.SHELL_LAYER, AccountantModel::createShellLayer);
		MenuScreens.register(Accountants.LOAN_MENU, LoanScreen::new);
	}
}
