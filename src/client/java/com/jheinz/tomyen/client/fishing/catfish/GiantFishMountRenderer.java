package com.jheinz.tomyen.client.fishing.catfish;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.TrophyFish;
import com.jheinz.tomyen.fishing.catfish.GiantFishMountBlock;
import com.jheinz.tomyen.fishing.catfish.GiantFishMountBlockEntity;

/**
 * The trophy on a Giant Fish Mount: the Giant Catfish's own model in its taxidermy pose, laid along the board with its
 * head to the viewer's left and its harpooned flank toward the room, sized by its weight; and the engraving on the brass
 * plate. Drawn from the master block (the bottom middle one), for a north-facing mount and turned to the block's facing.
 *
 * <p>The placement matches tools/gen_giant_catfish.py's mount preview (mounted_fish_quads), which checks it against the
 * board: change one, change both.
 */
public class GiantFishMountRenderer implements BlockEntityRenderer<GiantFishMountBlockEntity, GiantFishMountRenderer.State> {
	private static final Identifier TEXTURE = MyMod.id("textures/entity/giant_catfish/giant_catfish.png");
	/** The raised field's front face, and the fish's middle height, in block space about the master's centre. */
	private static final float FIELD_FRONT = 13.0f / 16.0f - 0.5f;
	private static final float FISH_Y = 18.5f / 16.0f - 0.5f;
	/** How deep the fish's back flank reaches behind its centre line in this pose, model pixels (its gill covers). */
	private static final float BACK = 13.0f;
	/** The brass plate: front face, middle, usable width. */
	private static final float PLATE_Z = 13.0f / 16.0f - 0.5f;
	private static final float PLATE_Y = 6.0f / 16.0f - 0.5f;
	private static final float PLATE_TEXT_WIDTH = 14.0f / 16.0f;
	private static final int ENGRAVING = 0xFF2E2008;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		boolean hasFish;
		float length;
		@Nullable FormattedCharSequence title;
		@Nullable FormattedCharSequence byline;
	}

	private final ModelPart root;
	private final RenderType renderType = RenderTypes.entityCutout(TEXTURE);
	private final Font font;
	/** The posed model's extents, in blocks. */
	private final Vector3f min = new Vector3f(Float.MAX_VALUE);
	private final Vector3f max = new Vector3f(-Float.MAX_VALUE);

	public GiantFishMountRenderer(BlockEntityRendererProvider.Context context) {
		GiantCatfishModel model = new GiantCatfishModel(context.bakeLayer(GiantCatfishModel.LAYER));
		GiantCatfishRenderState pose = new GiantCatfishRenderState();
		pose.mounted = true;
		model.setupAnim(pose);
		root = model.root();
		font = context.font();
		measure();
	}

	private void measure() {
		PoseStack poseStack = new PoseStack();
		Vector3f corner = new Vector3f();
		root.visit(poseStack, (pose, path, index, cube) -> {
			for (int i = 0; i < 8; i++) {
				corner.set(((i & 1) == 0 ? cube.minX : cube.maxX) / 16.0f, ((i & 2) == 0 ? cube.minY : cube.maxY) / 16.0f,
						((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16.0f);
				pose.pose().transformPosition(corner);
				min.min(corner);
				max.max(corner);
			}
		});
		if (min.x > max.x) {
			min.set(-0.5f);
			max.set(0.5f);
		}
	}

	/** How long the trophy is on the board, in blocks: a typical fish 3.6, heavier ones longer, within the board. */
	public static float length(float average, float weight) {
		if (average <= 0.0f || weight <= 0.0f) {
			return 3.6f;
		}
		return Mth.clamp(3.6f * (float) Math.pow(weight / average, 0.3), 3.1f, 4.4f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GiantFishMountBlockEntity mount, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(mount, state, partialTicks, cameraPosition, breakProgress);
		state.facing = mount.getBlockState().hasProperty(GiantFishMountBlock.FACING) ? mount.getBlockState().getValue(GiantFishMountBlock.FACING)
				: Direction.NORTH;
		ItemStack fish = mount.fish();
		state.hasFish = !fish.isEmpty();
		state.title = null;
		state.byline = null;
		if (!state.hasFish) {
			return;
		}
		state.length = length(mount.averageWeight(), mount.weight());
		TrophyFish trophy = fish.get(Fishing.TROPHY);
		if (trophy != null) {
			state.title = TrophyFish.describe(fish, trophy.weight()).getVisualOrderText();
			state.byline = Component.translatable("block.tomyen.fish_mount.caught_by", trophy.catcher()).getVisualOrderText();
		} else {
			state.title = TrophyFish.speciesName(fish).getVisualOrderText();
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.hasFish) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.rotate(Axis.YP.rotationDegrees(-yaw(state.facing)));
		submitFish(state, poseStack, collector);
		submitPlate(state, poseStack, collector);
		poseStack.popPose();
	}

	private static float yaw(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 180.0f;
			case WEST -> 270.0f;
			default -> 0.0f;
		};
	}

	private void submitFish(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		float scale = state.length / Math.max(0.01f, max.z - min.z);
		// Its back flank (the model's -x side, BACK pixels deep) rests just in front of the field.
		float z = FIELD_FRONT - 0.01f - scale * BACK / 16.0f;
		poseStack.pushPose();
		poseStack.translate(0.0f, FISH_Y, z);
		poseStack.scale(scale, scale, scale);
		// Model space is upside down with the head at -z: upright, the head to +x (the viewer's left, facing the mount from
		// the north) and the model's +x flank out toward the room. (x, y, z) becomes (-(z - cz), -(y - cy), -x).
		poseStack.rotate(Axis.YP.rotationDegrees(-90.0f));
		poseStack.rotate(Axis.ZP.rotationDegrees(180.0f));
		poseStack.translate(0.0f, -(min.y + max.y) / 2.0f, -(min.z + max.z) / 2.0f);
		collector.submitModelPart(root, poseStack, renderType, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1);
		poseStack.popPose();
	}

	/** The engraving: the title on top, the catcher under it, each shrunk to fit the plate. */
	private void submitPlate(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.title == null) {
			return;
		}
		boolean two = state.byline != null;
		line(state, poseStack, collector, state.title, two ? PLATE_Y + 0.04f : PLATE_Y, 0.0085f);
		if (two) {
			line(state, poseStack, collector, state.byline, PLATE_Y - 0.05f, 0.0065f);
		}
	}

	private void line(State state, PoseStack poseStack, SubmitNodeCollector collector, FormattedCharSequence text, float y, float maxScale) {
		int width = font.width(text);
		float scale = Math.min(maxScale, PLATE_TEXT_WIDTH / Math.max(1, width));
		poseStack.pushPose();
		poseStack.translate(0.0f, y, PLATE_Z - 0.002f);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0f));
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -width / 2.0f, -4.0f, text, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, ENGRAVING, 0, 0);
		poseStack.popPose();
	}

	/** The fish overhangs the master block by two blocks either way, so keep drawing it while the master's off screen. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
