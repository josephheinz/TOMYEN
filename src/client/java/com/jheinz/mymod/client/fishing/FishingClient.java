package com.jheinz.mymod.client.fishing;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.fishing.Fishing;
import com.jheinz.mymod.fishing.FishingNetworking;
import com.jheinz.mymod.fishing.FishingNetworking.FightEnd;
import com.jheinz.mymod.fishing.FishingNetworking.FightResult;
import com.jheinz.mymod.fishing.FishingNetworking.FightStart;
import com.jheinz.mymod.fishing.FishingNetworking.Outcome;
import com.jheinz.mymod.fishing.FishingNetworking.SonarPing;
import com.jheinz.mymod.fishing.RodHook;

/**
 * The angler's side of fishing: the Reel key, the minigame (started by the server's {@link FightStart}, played here
 * tick by tick, its result sent back), the HUD panel ({@link FishingHud}), the bobber tugging while the fish fights,
 * the Fish Finder's sonar ping, the Tackle Bench screen and the bent-rod model property.
 *
 * <p>Controls: during a fight the use key (right click) and the Reel key (Left Alt by default) both reel: hold for
 * the tension meter, tap for strikes and the mash meter. The use key does nothing else until the fight is over
 * (MinecraftFishingMixin), and the mouse stays free to look around.
 */
public final class FishingClient {
	public static final KeyMapping REEL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mymod.reel",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, KeyMapping.Category.register(MyMod.id("fishing"))));
	/** How long the result stays up after a fight ends. */
	static final int RESULT_TICKS = 46;

	private static final RandomSource RANDOM = RandomSource.create();

	static FishingMinigame game;
	private static boolean resultSent;
	/** The game's age when it ended, for the result flash. */
	static int endAge;
	/** The server's verdict, once it's in. */
	static FightEnd verdict;

	static ItemStack sonar = ItemStack.EMPTY;
	static int sonarDifficulty;
	static int sonarAge;

	private FishingClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(FightStart.TYPE, (payload, context) -> start(payload));
		ClientPlayNetworking.registerGlobalReceiver(FightEnd.TYPE, (payload, context) -> end(payload));
		ClientPlayNetworking.registerGlobalReceiver(SonarPing.TYPE, (payload, context) -> {
			sonar = payload.species();
			sonarDifficulty = payload.difficulty();
			sonarAge = 0;
			sound(Fishing.SONAR, 0.6f, 1.6f);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ClientTickEvents.START_CLIENT_TICK.register(FishingClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, MyMod.id("fishing"), FishingHud::extract);
		MenuScreens.register(Fishing.TACKLE_BENCH_MENU, TackleBenchScreen::new);
		EntityRendererRegistry.register(Fishing.ROD_HOOK, FishingHookRenderer::new);
		ConditionalItemModelProperties.ID_MAPPER.put(MyMod.id("rod_fighting"), RodFighting.MAP_CODEC);
		BlockEntityRendererRegistry.register(Fishing.FISH_MOUNT_ENTITY, FishMountRenderer::new);
	}

	/** Whether the reel keys belong to a fight right now. */
	public static boolean reeling() {
		return game != null && game.running();
	}

	/** The keys that reel, for the HUD's hint: "Right Button / Left Alt". */
	static Component keys() {
		Minecraft minecraft = Minecraft.getInstance();
		return Component.empty().append(minecraft.options.keyUse.getTranslatedKeyMessage()).append(" / ")
				.append(REEL.getTranslatedKeyMessage());
	}

	static void sound(SoundEvent sound, float volume, float pitch) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.level != null) {
			minecraft.level.playLocalSound(minecraft.player, sound, SoundSource.PLAYERS, volume, pitch);
		}
	}

	private static void start(FightStart payload) {
		game = FishingMinigame.create(payload.params());
		resultSent = false;
		verdict = null;
		sonar = ItemStack.EMPTY;
	}

	private static void end(FightEnd payload) {
		if (game == null || payload.id() != game.params.id()) {
			return;
		}
		verdict = payload;
		if (game.running()) {
			// The server called it first: the bobber went, or the fight ran too long.
			game.end(Outcome.byId(payload.outcome()));
			resultSent = true;
			endAge = game.age;
		}
		if (payload.perfect()) {
			sound(Fishing.STRIKE_HIT, 0.8f, 2.0f);
		}
	}

	private static void reset() {
		game = null;
		verdict = null;
		sonar = ItemStack.EMPTY;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null) {
			reset();
			return;
		}
		if (!sonar.isEmpty() && !minecraft.isPaused() && ++sonarAge > 120) {
			sonar = ItemStack.EMPTY;
		}
		FishMountRenderer.lookAt(minecraft);
		if (game == null) {
			// Don't let presses pile up for the next fight.
			while (REEL.consumeClick()) {
			}
			return;
		}
		int presses = 0;
		boolean held = false;
		if (game.running()) {
			while (minecraft.options.keyUse.consumeClick()) {
				presses++;
			}
			while (REEL.consumeClick()) {
				presses++;
			}
			held = minecraft.gui.screen() == null && (minecraft.options.keyUse.isDown() || REEL.isDown());
		}
		if (minecraft.isPaused()) {
			return;
		}
		game.tick(presses, held);
		if (!game.running() && !resultSent) {
			resultSent = true;
			endAge = game.age;
			ClientPlayNetworking.send(new FightResult(game.params.id(), game.outcome().ordinal(), game.perfect));
		}
		if (game.running()) {
			tugBobber(minecraft);
		} else if (game.age - endAge > RESULT_TICKS) {
			game = null;
			verdict = null;
		}
	}

	/** The bobber jerks and splashes as hard as the fish is pulling. */
	private static void tugBobber(Minecraft minecraft) {
		if (!(minecraft.player.fishing instanceof RodHook hook) || game.age <= com.jheinz.mymod.fishing.FightParams.INTRO_TICKS) {
			return;
		}
		float pull = game.tug();
		if (RANDOM.nextFloat() < 0.2f + 0.4f * pull) {
			hook.setDeltaMovement(hook.getDeltaMovement().add((RANDOM.nextFloat() - 0.5f) * 0.08f * pull,
					-(0.03f + 0.09f * pull) * RANDOM.nextFloat(), (RANDOM.nextFloat() - 0.5f) * 0.08f * pull));
		}
		if (RANDOM.nextFloat() < 0.15f + 0.3f * pull) {
			boolean lava = hook.inLava();
			minecraft.level.addParticle(lava ? ParticleTypes.LAVA : ParticleTypes.SPLASH, hook.getX() + (RANDOM.nextFloat() - 0.5f) * 0.4,
					hook.getY() + 0.15, hook.getZ() + (RANDOM.nextFloat() - 0.5f) * 0.4, 0.0, 0.05, 0.0);
		}
	}

	/** For the HUD: the outcome to show, the server's verdict if it's in, else the local one. */
	static Outcome shownOutcome() {
		return verdict != null ? FishingNetworking.Outcome.byId(verdict.outcome()) : game.outcome();
	}
}
