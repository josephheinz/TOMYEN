package com.jheinz.tomyen.client.fusion;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fusion.Fusions;
import com.jheinz.tomyen.fusion.RocketBoots;
import com.jheinz.tomyen.fusion.StormcrowFlight;

/**
 * Item Fusing on the client: the Fusion Reactor's screen, the hint shimmer in tooltips, and moving the local player
 * for the Rocket Boots (double jump, air dash) and the Stormcrow Rig (thrust while gliding). See the server classes in
 * com.jheinz.tomyen.fusion for the rules.
 */
public final class FusionClient {
	/** Items the server last said hum near a pond or reactor (their partner is carried too). */
	private static final Set<Item> SHIMMERING = new HashSet<>();

	private static boolean jumpWasDown;
	private static boolean sprintWasDown;
	private static boolean jumped;
	private static boolean dashed;

	private FusionClient() {
	}

	public static void initialize() {
		MenuScreens.register(Fusions.REACTOR_MENU, FusionReactorScreen::new);
		RocketBootsRenderer.initialize();
		ClientPlayNetworking.registerGlobalReceiver(Fusions.Shimmer.TYPE, (payload, context) -> {
			SHIMMERING.clear();
			for (Identifier id : payload.items()) {
				BuiltInRegistries.ITEM.getOptional(id).ifPresent(SHIMMERING::add);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!SHIMMERING.isEmpty() && SHIMMERING.contains(stack.getItem()) && !lines.isEmpty()) {
				lines.add(1, Component.translatable("fusion.tomyen.shimmer").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
			}
		});
		ClientTickEvents.START_CLIENT_TICK.register(FusionClient::move);
	}

	/** Before vanilla moves the local player: rocket bursts and rig thrust. */
	private static void move(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			SHIMMERING.clear();
			return;
		}
		boolean free = minecraft.gui.screen() == null;
		boolean jump = free && minecraft.options.keyJump.isDown();
		boolean sprint = free && minecraft.options.keySprint.isDown();
		if (player.onGround() || player.isInWater() || player.onClimbable()) {
			jumped = false;
			dashed = false;
		}
		if (RocketBoots.canFire(player)) {
			Vec3 m = player.getDeltaMovement();
			if (jump && !jumpWasDown && !jumped) {
				jumped = true;
				player.setDeltaMovement(m.x * 1.1, RocketBoots.JUMP_SPEED, m.z * 1.1);
				player.resetFallDistance();
				ClientPlayNetworking.send(new Fusions.RocketBurst(RocketBoots.DOUBLE_JUMP));
			} else if (sprint && !sprintWasDown && !dashed) {
				dashed = true;
				float yaw = player.getYRot() * Mth.DEG_TO_RAD;
				player.setDeltaMovement(-Mth.sin(yaw) * RocketBoots.DASH_SPEED, Math.max(m.y, 0.18), Mth.cos(yaw) * RocketBoots.DASH_SPEED);
				player.resetFallDistance();
				ClientPlayNetworking.send(new Fusions.RocketBurst(RocketBoots.DASH));
			}
		}
		if (jump && StormcrowFlight.canThrust(player)) {
			player.setDeltaMovement(StormcrowFlight.thrust(player.getDeltaMovement(), player.getLookAngle()));
		}
		jumpWasDown = jump;
		sprintWasDown = sprint;
	}
}
