package com.jheinz.tomyen.client.cathode;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.cathode.CathodeWalker;

/**
 * Draws a Cathode Walker, with its screen glowing full bright on top. What's on the screen, frame by frame (the frames
 * are textures painted by tools/gen_cathode_walker.py, only the screen's pixels in each):
 * <ul>
 * <li>wandering: grey static, a new frame every other tick;
 * <li>hunting: a grinning pixel face that blinks now and then and glitches to static for a tick;
 * <li>hurt: colour bars;
 * <li>charging a Static Burst: brightening to white, and a flash of white when it goes off;
 * <li>dying: a white flash as the screen implodes, a crackle of static, then the picture clicks off, shrinking to a
 * line across the middle, a short line, a white dot, a fading dot and nothing.
 * </ul>
 */
public class CathodeWalkerRenderer extends MobRenderer<CathodeWalker, CathodeWalkerRenderState, CathodeWalkerModel> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/cathode_walker/cathode_walker.png");
	private static final RenderType[] STATIC = {screen("static_0"), screen("static_1"), screen("static_2"), screen("static_3")};
	private static final RenderType FACE = screen("face");
	private static final RenderType BLINK = screen("face_blink");
	private static final RenderType BARS = screen("bars");
	private static final RenderType WHITE = screen("white");
	private static final RenderType OFF_LINE = screen("off_line");
	private static final RenderType OFF_SHORT = screen("off_short");
	private static final RenderType OFF_DOT = screen("off_dot");
	private static final RenderType OFF_FADE = screen("off_fade");
	/** Ticks between blinks, and how long one lasts. */
	private static final int BLINK_EVERY = 70;
	private static final int BLINK_TICKS = 3;

	public CathodeWalkerRenderer(EntityRendererProvider.Context context) {
		super(context, new CathodeWalkerModel(context.bakeLayer(CathodeWalkerModel.LAYER)), 0.5f);
		addLayer(new ScreenLayer(this));
	}

	private static RenderType screen(String name) {
		return RenderTypes.eyes(MyMod.id("textures/entity/cathode_walker/screen_" + name + ".png"));
	}

	@Override
	public Identifier getTextureLocation(CathodeWalkerRenderState state) {
		return TEXTURE;
	}

	@Override
	public CathodeWalkerRenderState createRenderState() {
		return new CathodeWalkerRenderState();
	}

	@Override
	public void extractRenderState(CathodeWalker walker, CathodeWalkerRenderState state, float partialTicks) {
		super.extractRenderState(walker, state, partialTicks);
		int id = walker.getId();
		state.seed = (id % 97) * 0.37f;
		state.hunting = walker.mode() != CathodeWalker.IDLE;
		state.attackAnim = walker.getSwingAnimation(partialTicks);
		state.charge = walker.isAlive() ? Math.max(walker.charge(partialTicks), walker.flashTicks() > 0 ? 1.0f : 0.0f) : 0.0f;
		state.screenBrightness = 1.0f;
		state.screen = pick(walker, state, (int) state.ageInTicks + id * 13);
	}

	private static @Nullable RenderType pick(CathodeWalker walker, CathodeWalkerRenderState state, int tick) {
		if (!walker.isAlive()) {
			int dead = (int) state.deathTime;
			if (dead < 2) {
				return WHITE;
			}
			if (dead < CathodeWalker.CLICK_OFF_TICK) {
				return STATIC[dead % STATIC.length];
			}
			int off = dead - CathodeWalker.CLICK_OFF_TICK;
			return off < 2 ? OFF_LINE : off < 4 ? OFF_SHORT : off < 9 ? OFF_DOT : off < 13 ? OFF_FADE : null;
		}
		if (walker.hurtTime > 0) {
			return BARS;
		}
		if (walker.mode() == CathodeWalker.IDLE) {
			return STATIC[(tick / 2) % STATIC.length];
		}
		if (tick % 47 == 0) {
			return STATIC[tick % STATIC.length];
		}
		return tick % BLINK_EVERY < BLINK_TICKS ? BLINK : FACE;
	}

	/** The screen, full bright whatever the light; whiting out as it charges a burst. */
	static class ScreenLayer extends RenderLayer<CathodeWalkerRenderState, CathodeWalkerModel> {
		ScreenLayer(RenderLayerParent<CathodeWalkerRenderState, CathodeWalkerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CathodeWalkerRenderState state, float yRot,
				float xRot) {
			if (state.screen != null && state.charge < 1.0f) {
				collector.order(1).submitModel(getParentModel(), state, poseStack, state.screen, light, OverlayTexture.NO_OVERLAY,
						ARGB.gray(state.screenBrightness * (1.0f - state.charge)), null, state.outlineColor);
			}
			if (state.charge > 0.0f) {
				collector.order(1).submitModel(getParentModel(), state, poseStack, WHITE, light, OverlayTexture.NO_OVERLAY,
						ARGB.gray(state.charge), null, state.outlineColor);
			}
		}
	}
}
