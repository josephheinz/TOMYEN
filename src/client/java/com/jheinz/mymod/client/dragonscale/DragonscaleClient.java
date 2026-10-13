package com.jheinz.mymod.client.dragonscale;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.dragonscale.DragonRoar;
import com.jheinz.mymod.dragonscale.DragonscaleArmor;

/**
 * Dragonscale on the client: the Dragon Roar key (G), the flap (the jump key pressed while gliding in the full set,
 * no more often than the server allows), and the helmet's horns on anything humanoid. The wings, spread or folded
 * to nubs, are vanilla's elytra layer (WingsLayerDragonscaleMixin).
 */
public final class DragonscaleClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MyMod.id("dragonscale"));
	public static final KeyMapping ROAR_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mymod.dragon_roar", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));

	private static boolean jumpWasDown;
	private static int lastFlap = Integer.MIN_VALUE / 2;

	private DragonscaleClient() {
	}

	public static void initialize() {
		ModelLayerRegistry.registerModelLayer(DragonHornsLayer.LAYER, DragonHornsLayer::createLayer);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(horns(renderer, context.getModelSet()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(DragonscaleClient::tick);
	}

	/** Only renderers with a humanoid model get here, and a humanoid model's state is a humanoid one. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static DragonHornsLayer<?, ?> horns(LivingEntityRenderer<?, ?, ?> renderer, EntityModelSet models) {
		return new DragonHornsLayer((LivingEntityRenderer) renderer, models);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		boolean jump = client.options.keyJump.isDown();
		if (player != null) {
			while (ROAR_KEY.consumeClick()) {
				if (DragonscaleArmor.hasFullSet(player)) {
					ClientPlayNetworking.send(DragonRoar.Roar.INSTANCE);
				}
			}
			// The press that starts a glide isn't a flap: gliding only shows up once the server has agreed to it.
			if (jump && !jumpWasDown && player.isFallFlying() && player.tickCount - lastFlap >= DragonscaleArmor.FLAP_COOLDOWN
					&& DragonscaleArmor.hasFullSet(player)) {
				lastFlap = player.tickCount;
				ClientPlayNetworking.send(DragonRoar.Flap.INSTANCE);
			}
		}
		jumpWasDown = jump;
	}
}
