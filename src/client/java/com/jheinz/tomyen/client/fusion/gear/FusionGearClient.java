package com.jheinz.tomyen.client.fusion.gear;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fusion.gear.FusionGear;
import com.jheinz.tomyen.fusion.gear.RevenantMail;

/**
 * The batch-3 fusion gear on the client: the worn models ({@link FusionGearRenderer}), the Astral Visor's outlines and
 * warnings ({@link AstralVisorClient}), and the Revenant Mail's tooltip line saying whether its death ward is ready
 * today (the client knows the day; the item's own tooltip doesn't).
 */
public final class FusionGearClient {
	private FusionGearClient() {
	}

	public static void initialize() {
		FusionGearRenderer.initialize();
		AstralVisorClient.initialize();
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, MyMod.id("astral_warning"), AstralVisorClient::extract);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (!stack.is(FusionGear.REVENANT_MAIL) || minecraft.level == null || lines.size() < 2) {
				return;
			}
			boolean ready = RevenantMail.isReady(stack, minecraft.level);
			// Just under the "Fused" line and the ward's own line.
			lines.add(Math.min(3, lines.size()), Component.translatable(ready ? "item.tomyen.revenant_mail.ready" : "item.tomyen.revenant_mail.spent")
					.withStyle(ready ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
		});
	}
}
