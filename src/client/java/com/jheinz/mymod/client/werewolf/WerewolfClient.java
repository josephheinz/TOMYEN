package com.jheinz.mymod.client.werewolf;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.jheinz.mymod.silver.Silver;
import com.jheinz.mymod.werewolf.Werewolves;

/**
 * The werewolf's renderer and model, and the hint on a Holy Silver Ingot that it lifts the curse. (A transforming
 * villager's shudder is LivingEntityRendererWerewolfShakeMixin.)
 */
public final class WerewolfClient {
	private WerewolfClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Werewolves.WEREWOLF, WerewolfRenderer::new);
		ModelLayerRegistry.registerModelLayer(WerewolfModel.LAYER, WerewolfModel::createBodyLayer);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(Silver.HOLY_SILVER_INGOT)) {
				lines.add(Math.min(1, lines.size()), Component.translatable("item.mymod.holy_silver_ingot.cure").withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
