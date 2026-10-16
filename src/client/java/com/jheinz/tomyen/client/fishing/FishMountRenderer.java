package com.jheinz.tomyen.client.fishing;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.fishing.FishMountBlock;
import com.jheinz.tomyen.fishing.FishMountBlockEntity;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.TrophyFish;

/**
 * The fish on a Fish Mount, and the engraving on its brass plate.
 *
 * <p>The fish is its 3D model ({@link MountedFishModels}) set across the plaque's raised field, angled out from the
 * board with its head toward the room, as fish are mounted. Its length comes from its weight: a species' typical fish
 * is a fixed size for its kind (a tetra small, a swordfish nearly a block), and a heavier one is longer by
 * {@code (weight / typical)^0.45}, a bit more than the cube root weight really scales by, so a fish twice as heavy
 * looks clearly bigger. Big ones overhang the plaque; past a block long they're tilted head-up across it, and the
 * longest stop at {@link #MAX_LENGTH}. A fish with no 3D model (cooked fish, an old boot) is drawn as its item.
 *
 * <p>The plate reads "Pike, 31.7 lb" over "Caught by Steve" for a trophy, or just the name for an ordinary fish.
 * Looking at a mount also puts its full description on the action bar ({@link #lookAt}).
 *
 * <p>Everything is laid out for a north-facing mount (the plaque against the block's south side, its face toward -z,
 * matching tools/gen_fish_mount.py) and turned to the block's facing.
 */
public class FishMountRenderer implements BlockEntityRenderer<FishMountBlockEntity, FishMountRenderer.State> {
	/** The raised field the fish hangs over: its front face, and its middle, in block space about the centre. */
	private static final float FIELD_Z = 13.5f / 16.0f - 0.5f;
	private static final float FISH_Y = 9.5f / 16.0f - 0.5f;
	/** The brass plate: its front face, middle and usable width. */
	private static final float PLATE_Z = 13.5f / 16.0f - 0.5f;
	private static final float PLATE_Y = 4.5f / 16.0f - 0.5f;
	private static final float PLATE_TEXT_WIDTH = 7.2f / 16.0f;
	private static final int ENGRAVING = 0xFF2E2008;
	/** Fish lengths, in blocks: a typical one between these, scaled by weight up to the max. */
	private static final float MIN_BASE = 0.28f;
	private static final float MAX_BASE = 0.95f;
	public static final float MAX_LENGTH = 1.9f;
	/** How far the head turns out from the board. */
	private static final float YAW = 12.0f;
	/** An item drawn in place of a model is about this long, corner to corner, at scale 1. */
	private static final float SPRITE_LENGTH = 1.2f;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		MountedFishModels.@Nullable MountedFish model;
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean hasFish;
		float length;
		@Nullable FormattedCharSequence title;
		@Nullable FormattedCharSequence byline;
	}

	private final Map<Item, MountedFishModels.MountedFish> models;
	private final ItemModelResolver itemModelResolver;
	private final Font font;

	public FishMountRenderer(BlockEntityRendererProvider.Context context) {
		models = MountedFishModels.bake(context);
		itemModelResolver = context.itemModelResolver();
		font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/**
	 * How long a fish of {@code weight} lb is on the wall, in blocks, for a species whose typical fish weighs
	 * {@code average} (0 when unknown: a middling length).
	 */
	public static float length(float average, float weight) {
		if (average <= 0.0f) {
			return 0.6f;
		}
		float base = Mth.clamp(0.62f * (float) Math.pow(average / 5.0f, 0.15f), MIN_BASE, MAX_BASE);
		float ratio = weight > 0.0f ? (float) Math.pow(weight / average, 0.45f) : 1.0f;
		return Math.min(base * ratio, MAX_LENGTH);
	}

	@Override
	public void extractRenderState(FishMountBlockEntity mount, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(mount, state, partialTicks, cameraPosition, breakProgress);
		state.facing = mount.getBlockState().hasProperty(FishMountBlock.FACING) ? mount.getBlockState().getValue(FishMountBlock.FACING)
				: Direction.NORTH;
		ItemStack fish = mount.fish();
		state.hasFish = !fish.isEmpty();
		state.item.clear();
		state.model = null;
		state.title = null;
		state.byline = null;
		if (!state.hasFish) {
			return;
		}
		state.model = models.get(fish.getItem());
		if (state.model == null) {
			itemModelResolver.updateForTopItem(state.item, fish, ItemDisplayContext.FIXED, mount.getLevel(), null,
					(int) mount.getBlockPos().asLong());
		}
		state.length = length(mount.averageWeight(), mount.weight());
		TrophyFish trophy = fish.get(Fishing.TROPHY);
		Component name = TrophyFish.speciesName(fish);
		if (trophy != null) {
			state.title = TrophyFish.describe(fish, trophy.weight()).getVisualOrderText();
			state.byline = Component.translatable("block.tomyen.fish_mount.caught_by", trophy.catcher()).getVisualOrderText();
		} else {
			state.title = name.getVisualOrderText();
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

	/** The block-model y rotation for a facing (north 0, east 90, south 180, west 270), as the blockstate turns it. */
	private static float yaw(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0f;
			case SOUTH -> 180.0f;
			case WEST -> 270.0f;
			default -> 0.0f;
		};
	}

	private void submitFish(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		float length = state.length;
		// Past a block long, the fish tilts head-up across the plaque so it fits the wall better.
		float tiltFraction = Mth.clamp((length - 1.05f) / 0.85f, 0.0f, 1.0f);
		float tilt = tiltFraction * 30.0f;
		// A tilted fish rides a little higher, so its tail clears the plate.
		float y = FISH_Y + tiltFraction * 0.08f;
		float outFromBoard = length / 2.0f * Mth.sin(YAW * Mth.DEG_TO_RAD) + 0.01f;
		poseStack.pushPose();
		if (state.model != null) {
			// The model's head is at -x, on the right as you face the mount: tilt it up (anticlockwise as seen), turn it out.
			float thickness = state.model.thickness(length);
			poseStack.translate(0.0f, y, FIELD_Z - thickness / 2.0f - outFromBoard);
			poseStack.rotate(Axis.ZP.rotationDegrees(-tilt));
			poseStack.rotate(Axis.YP.rotationDegrees(-YAW));
			state.model.submit(poseStack, collector, state.lightCoords, length);
		} else if (!state.item.isEmpty()) {
			// An item sprite's head is at the lower left as you face it: level it, then tilt and turn it the same way.
			float scale = length / SPRITE_LENGTH;
			poseStack.translate(0.0f, y, FIELD_Z - scale / 32.0f - outFromBoard);
			poseStack.rotate(Axis.ZP.rotationDegrees(tilt));
			poseStack.rotate(Axis.YP.rotationDegrees(YAW));
			poseStack.rotate(Axis.ZP.rotationDegrees(45.0f));
			poseStack.scale(scale, scale, scale);
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		}
		poseStack.popPose();
	}

	/** The engraving: the title on top, the catcher under it, each shrunk to fit the plate. */
	private void submitPlate(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.title == null) {
			return;
		}
		boolean two = state.byline != null;
		line(state, poseStack, collector, state.title, two ? PLATE_Y + 0.032f : PLATE_Y, 0.0075f);
		if (two) {
			line(state, poseStack, collector, state.byline, PLATE_Y - 0.04f, 0.0055f);
		}
	}

	private void line(State state, PoseStack poseStack, SubmitNodeCollector collector, FormattedCharSequence text, float y, float maxScale) {
		int width = font.width(text);
		float scale = Math.min(maxScale, PLATE_TEXT_WIDTH / Math.max(1, width));
		poseStack.pushPose();
		poseStack.translate(0.0f, y, PLATE_Z - 0.002f);
		// Text reads along +x with +y down, toward a viewer at +z: turned to face out of the plate, which faces -z.
		poseStack.rotate(Axis.YP.rotationDegrees(180.0f));
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -width / 2.0f, -4.0f, text, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, ENGRAVING, 0, 0);
		poseStack.popPose();
	}

	/** Big fish overhang the block, so keep drawing them while the block itself is just off screen. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	// --- Looking at a mount: its description on the action bar ---------------------------------------------------

	private static @Nullable BlockPos lookedAt;
	private static int lookTicks;

	/** Each client tick: looking at a mounted fish shows what it is, refreshed while the look lasts. */
	static void lookAt(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		if (minecraft.level == null || minecraft.gui == null || !(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
				|| !(minecraft.level.getBlockEntity(blockHit.getBlockPos()) instanceof FishMountBlockEntity mount) || mount.fish().isEmpty()) {
			lookedAt = null;
			return;
		}
		if (!blockHit.getBlockPos().equals(lookedAt) || ++lookTicks >= 40) {
			lookedAt = blockHit.getBlockPos();
			lookTicks = 0;
			minecraft.gui.hud.setOverlayMessage(mount.describe(), false);
		}
	}
}
