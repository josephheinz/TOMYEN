package com.jheinz.mymod.client.tvhelmet;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.tvhelmet.TvEmote;
import com.jheinz.mymod.tvhelmet.TvHelmets;
import com.jheinz.mymod.tvhelmet.TvScreen;

/**
 * Draws a worn TV Helmet: a Fabric armor renderer for the head, its {@link TvHelmetModel} copying the wearer's pose
 * (so it turns and nods with the head on players, armor stands and mobs alike, and socketed in an exoskeleton too),
 * then the screen on top, full bright like the Cathode Walker's. Enchanted, the set glints like armor.
 *
 * <p>What's on a player's screen, frame by frame ({@link #frame}): static while they're hurt or the set's shorted out;
 * static breaking through the picture in the rain; colour bars once they've been idle a long while; otherwise their
 * emote, blinking or looping (see {@link TvEmote}). Anything that isn't a player (an armor stand, a zombie) just shows
 * static.
 */
public final class TvHelmetRenderer {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/tv_helmet.png");
	private static final RenderType RENDER_TYPE = RenderTypes.armorCutoutNoCull(TEXTURE);
	private static final RenderType GLINT = RenderTypes.armorCutoutNoCullGlint(TEXTURE);
	private static final Map<TvEmote, RenderType[]> FRAMES = new EnumMap<>(TvEmote.class);
	/** Ticks between blinks, and how long one lasts. */
	private static final int BLINK_EVERY = 70;
	private static final int BLINK_TICKS = 3;

	static {
		for (TvEmote emote : TvEmote.values()) {
			String[] names = emote.frames();
			RenderType[] types = new RenderType[names.length];
			for (int i = 0; i < names.length; i++) {
				types[i] = RenderTypes.eyes(MyMod.id("textures/entity/tv_helmet/screen_" + names[i] + ".png"));
			}
			FRAMES.put(emote, types);
		}
	}

	private TvHelmetRenderer() {
	}

	static void initialize() {
		ModelLayerRegistry.registerModelLayer(TvHelmetModel.LAYER, TvHelmetModel::createLayer);
		ArmorRenderer.register(context -> {
			TvHelmetModel model = new TvHelmetModel(context.bakeLayer(TvHelmetModel.LAYER));
			return (poseStack, collector, stack, state, slot, light, wearerModel) -> {
				// A baby's head is scaled by its own mesh, which the set wouldn't follow.
				if (slot != EquipmentSlot.HEAD || state.isBaby) {
					return;
				}
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector, poseStack,
						stack.hasFoil() ? GLINT : RENDER_TYPE, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
				ArmorRenderer.submitTransformCopyingModel(wearerModel, state, model, state, false, collector.order(1), poseStack,
						frame(state), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			};
		}, TvHelmets.TV_HELMET);
	}

	/** The player wearing the set this render state is for, or null for anything else. */
	private static @Nullable LivingEntity wearer(HumanoidRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state instanceof AvatarRenderState avatar && minecraft.level != null) {
			Entity entity = minecraft.level.getEntity(avatar.id);
			return entity instanceof LivingEntity living ? living : null;
		}
		return null;
	}

	private static RenderType frame(HumanoidRenderState state) {
		LivingEntity wearer = wearer(state);
		int seed = wearer == null ? 0 : wearer.getId() * 13;
		int tick = (int) state.ageInTicks + seed;
		if (wearer == null) {
			return frame(TvEmote.STATIC, tick);
		}
		TvScreen screen = TvHelmets.screen(wearer);
		if (screen.mode() == TvScreen.SHORTED || state.hasRedOverlay) {
			return frames(TvEmote.STATIC)[Math.floorMod(tick, 4)];
		}
		if (screen.mode() == TvScreen.RAIN && rainGlitch(tick)) {
			return frames(TvEmote.STATIC)[Math.floorMod(tick, 4)];
		}
		if (screen.mode() == TvScreen.IDLE) {
			return frame(TvEmote.BARS, tick);
		}
		return frame(screen.emoteType(), tick);
	}

	/** In the rain, two in every five three-tick spells or so break up into static, scattered by a cheap hash. */
	static boolean rainGlitch(int tick) {
		int spell = Math.floorDiv(tick, 3);
		return Math.floorMod((spell * 1103515245 + 12345) >>> 16, 5) < 2;
	}

	private static RenderType[] frames(TvEmote emote) {
		return FRAMES.get(emote);
	}

	/** An emote's frame at a tick. */
	public static RenderType frame(TvEmote emote, int tick) {
		RenderType[] frames = frames(emote);
		return switch (emote.style()) {
			case BLINK -> frames[Math.floorMod(tick, BLINK_EVERY) < BLINK_TICKS ? 1 : 0];
			case LOOP -> frames[Math.floorMod(Math.floorDiv(tick, emote.period()), frames.length)];
			case STILL -> frames[0];
		};
	}
}
