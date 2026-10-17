package com.jheinz.tomyen.client.isle.shade;

import java.lang.reflect.RecordComponent;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.tomyen.client.isle.mixin.RenderSetupAccessor;
import com.jheinz.tomyen.client.isle.mixin.RenderTypeAccessor;

/**
 * Stands between a mob's own renderer and the real collector while it draws a shade, and turns everything it submits
 * into a ghost of itself:
 *
 * <ul>
 * <li>each model part's solid or cut-out render type becomes the translucent one for the same texture (culled if the
 * original was), its colour multiplied by the shade's tint and alpha, and its light raised to a faint glow;
 * <li>layers that already blend (eyes, glows, swirls) keep their own render type and just take the tint;
 * <li>held items are drawn as they are (the one solid thing about a shade is what it carried);
 * <li>shadows, fire, name tags, leashes, text and blocks (an enderman's, a snow golem's pumpkin) are dropped.
 * </ul>
 *
 * The texture behind a render type is looked up once and remembered (a render type with none is dropped rather than
 * drawn solid).
 */
final class GhostSubmitCollector implements SubmitNodeCollector {
	private static final int MIN_BLOCK_LIGHT = 9;
	private static final Map<RenderType, Optional<RenderType>> GHOSTS = new IdentityHashMap<>();

	private final SubmitNodeCollector delegate;
	private final Ordered ordered;

	GhostSubmitCollector(SubmitNodeCollector delegate, int tint) {
		this.delegate = delegate;
		this.ordered = new Ordered(delegate, tint);
	}

	/** The see-through version of a render type, or empty if it can't be drawn as a ghost. */
	static Optional<RenderType> ghost(RenderType type) {
		if (type.isOutline() || type.hasBlending()) {
			return Optional.of(type);
		}
		Optional<RenderType> cached = GHOSTS.get(type);
		if (cached != null) {
			return cached;
		}
		if (GHOSTS.size() > 1024) {
			GHOSTS.clear();
		}
		Optional<RenderType> ghost = texture(type).map(texture -> type.pipeline().isCull() ? RenderTypes.entityTranslucentCull(texture)
				: RenderTypes.entityTranslucent(texture));
		GHOSTS.put(type, ghost);
		return ghost;
	}

	private static Optional<Identifier> texture(RenderType type) {
		try {
			Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) type).tomyen$state()).tomyen$textures();
			Object binding = textures.get("Sampler0");
			if (binding == null || !binding.getClass().isRecord()) {
				return Optional.empty();
			}
			RecordComponent[] components = binding.getClass().getRecordComponents();
			var accessor = components[0].getAccessor();
			accessor.setAccessible(true);
			return accessor.invoke(binding) instanceof Identifier id ? Optional.of(id) : Optional.empty();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Optional.empty();
		}
	}

	@Override
	public OrderedSubmitNodeCollector order(int order) {
		return new Ordered(delegate.order(order), ordered.tint);
	}

	// --- Everything else goes through the ordered wrapper -------------------------------------------------------------

	@Override
	public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
	}

	@Override
	public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
			CameraRenderState camera) {
	}

	@Override
	public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
			int lightCoords, int color, int backgroundColor, int outlineColor) {
	}

	@Override
	public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode,
			int lightCoords) {
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
	}

	@Override
	public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
			int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
		ordered.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);
	}

	@Override
	public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
			int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
			int overlayCoords, int outlineColor) {
	}

	@Override
	public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
	}

	@Override
	public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
	}

	@Override
	public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
			int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType) {
		ordered.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
		ordered.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
	}

	@Override
	public void submitQuadParticleGroup(QuadParticleRenderState particles) {
		delegate.submitQuadParticleGroup(particles);
	}

	@Override
	public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
	}

	/** The wrapper for one order of the collector (the plain collector is order 0). */
	private static final class Ordered implements OrderedSubmitNodeCollector {
		private final OrderedSubmitNodeCollector delegate;
		private final int tint;

		Ordered(OrderedSubmitNodeCollector delegate, int tint) {
			this.delegate = delegate;
			this.tint = tint;
		}

		private static int glow(int lightCoords) {
			return LightCoordsUtil.pack(Math.max(MIN_BLOCK_LIGHT, LightCoordsUtil.block(lightCoords)), LightCoordsUtil.sky(lightCoords));
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
				int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
			Optional<RenderType> ghost = ghost(renderType);
			if (ghost.isPresent()) {
				int color = renderType.isOutline() ? tintedColor : ARGB.multiply(tintedColor, tint);
				delegate.submitModel(model, state, poseStack, ghost.get(), glow(lightCoords), overlayCoords, color, uvMapping, outlineColor);
			}
		}

		@Override
		public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
				int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType) {
			delegate.submitItem(poseStack, displayContext, glow(lightCoords), overlayCoords, outlineColor, tintLayers, quads, foilType);
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
			if (renderType.hasBlending()) {
				delegate.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
			}
		}

		@Override
		public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		}

		@Override
		public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough,
				int lightCoords, CameraRenderState camera) {
		}

		@Override
		public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
				int lightCoords, int color, int backgroundColor, int outlineColor) {
		}

		@Override
		public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode,
				int lightCoords) {
		}

		@Override
		public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
		}

		@Override
		public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		}

		@Override
		public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
				int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		}

		@Override
		public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
				int overlayCoords, int outlineColor) {
		}

		@Override
		public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
		}

		@Override
		public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
		}

		@Override
		public void submitQuadParticleGroup(QuadParticleRenderState particles) {
			delegate.submitQuadParticleGroup(particles);
		}

		@Override
		public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
		}
	}
}
