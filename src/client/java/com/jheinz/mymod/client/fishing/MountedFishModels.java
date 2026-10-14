package com.jheinz.mymod.client.fishing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Vector3f;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.client.fishing.fish.FishModelLayers;
import com.jheinz.mymod.fishing.fish.FishKind;

/**
 * The 3D fish a Fish Mount draws, by item: every mod fish's own living-fish model (client/fishing/fish, with its glow
 * layer where it has one) and vanilla's cod, salmon, pufferfish and tropical fish models. Anything else on a mount
 * (cooked fish, an old boot) is drawn as its item, by {@link FishMountRenderer}.
 *
 * <p>Each model is posed once for the wall, the way a taxidermist sets a fish: the body bent a little through its tail
 * segments so the tail sweeps out from the board, fins left spread. Its bounds in that pose are measured, so the
 * renderer can scale any model to a length in blocks and centre it.
 */
public final class MountedFishModels {
	/** A posed fish model, ready to draw at a given length. */
	public interface MountedFish {
		/**
		 * Draws the fish centred on the pose's origin: its length (head to tail) along x with the head toward -x, its
		 * back up (+y), its flank facing -z. {@code length} is in blocks.
		 */
		void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, float length);

		/** Its thickness (along z) at that length, in blocks: how far it stands off the board. */
		float thickness(float length);

		/** Its height (along y) at that length, in blocks. */
		float height(float length);
	}

	private static final Map<Item, Function<BlockEntityRendererProvider.Context, MountedFish>> FACTORIES = new HashMap<>();

	static {
		vanilla(Items.COD, ModelLayers.COD, "cod", -1);
		vanilla(Items.SALMON, ModelLayers.SALMON, "salmon", -1);
		vanilla(Items.PUFFERFISH, ModelLayers.PUFFERFISH_BIG, "pufferfish", -1);
		// Tropical fish are white, tinted by their variant: a mounted one is the classic orange clownfish colour.
		vanilla(Items.TROPICAL_FISH, ModelLayers.TROPICAL_FISH_LARGE, "tropical_b", 0xFFF9801D);
		for (FishModelLayers.Shape shape : FishModelLayers.SHAPES) {
			FishKind kind = FishKind.ALL.stream().filter(k -> k.id().equals(shape.id())).findFirst().orElse(null);
			if (kind == null) {
				continue;
			}
			ModelLayerLocation layer = new ModelLayerLocation(MyMod.id(shape.id()), "main");
			Identifier texture = MyMod.id("textures/entity/fish/" + shape.id() + ".png");
			Identifier glow = shape.glows() ? MyMod.id("textures/entity/fish/" + shape.id() + "_glow.png") : null;
			FACTORIES.put(kind.catchItem(), context -> new ModelFish(context.bakeLayer(layer), texture, glow, -1));
		}
	}

	private MountedFishModels() {
	}

	private static void vanilla(Item item, ModelLayerLocation layer, String texture, int tint) {
		Identifier id = Identifier.withDefaultNamespace("textures/entity/fish/" + texture + ".png");
		FACTORIES.put(item, context -> new ModelFish(context.bakeLayer(layer), id, null, tint));
	}

	/** Every mounted fish model, baked for one renderer. */
	static Map<Item, MountedFish> bake(BlockEntityRendererProvider.Context context) {
		Map<Item, MountedFish> out = new HashMap<>();
		FACTORIES.forEach((item, factory) -> out.put(item, factory.apply(context)));
		return out;
	}

	/** An entity model, posed for the wall and measured. */
	private static final class ModelFish implements MountedFish {
		/** How far each tail segment turns, in radians: a gentle S through the body. */
		private static final float BEND = 0.22f;

		private final ModelPart root;
		private final RenderType renderType;
		private final @Nullable RenderType glow;
		private final int tint;
		private final Vector3f min = new Vector3f(Float.MAX_VALUE);
		private final Vector3f max = new Vector3f(-Float.MAX_VALUE);

		ModelFish(ModelPart root, Identifier texture, @Nullable Identifier glow, int tint) {
			this.root = root;
			this.renderType = RenderTypes.entityCutout(texture);
			this.glow = glow == null ? null : RenderTypes.entityTranslucentEmissive(glow);
			this.tint = tint;
			pose(root);
			measure();
		}

		/** The mounted pose: each tail segment (the mod fish's tail/tail2/tail3, vanilla's tail fin or back half) bent out. */
		private static void pose(ModelPart root) {
			root.getAllParts().forEach(ModelPart::resetPose);
			Function<String, @Nullable ModelPart> part = root.createPartLookup();
			int k = 0;
			for (String name : new String[] {"tail", "tail2", "tail3", "body_back", "tail_fin", "back_fin"}) {
				ModelPart segment = part.apply(name);
				if (segment != null) {
					// Negative yaw swings a segment toward the model's -x flank, the side that faces out from the wall.
					segment.yRot -= BEND * (k == 0 ? 1.0f : 0.7f);
					k++;
				}
			}
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

		private float scale(float length) {
			return length / Math.max(0.01f, max.z - min.z);
		}

		@Override
		public float thickness(float length) {
			return (max.x - min.x) * scale(length);
		}

		@Override
		public float height(float length) {
			return (max.y - min.y) * scale(length);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, float length) {
			float s = scale(length);
			poseStack.pushPose();
			poseStack.scale(s, s, s);
			// Model space is entity space before the renderer's flip: z runs head (-z) to tail, y points down. Flipped
			// upright and turned so the tail runs to +x, the model's -x flank faces -z, out from the board:
			// (x, y, z) becomes (z, -y, x).
			poseStack.rotate(Axis.YP.rotationDegrees(90.0f));
			poseStack.rotate(Axis.ZP.rotationDegrees(180.0f));
			poseStack.translate(-(min.x + max.x) / 2.0f, -(min.y + max.y) / 2.0f, -(min.z + max.z) / 2.0f);
			collector.submitModelPart(root, poseStack, renderType, light, OverlayTexture.NO_OVERLAY, null, tint);
			if (glow != null) {
				collector.submitModelPart(root, poseStack, glow, 0xF000F0, OverlayTexture.NO_OVERLAY, null, -1);
			}
			poseStack.popPose();
		}
	}
}
