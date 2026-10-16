package com.jheinz.tomyen.dragonscale;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The buried skeleton of a dragon, built in code: a spine of Fossilized Dragon Bone lying straight along X or Z, its
 * tail sloping down into the ground behind, a rib cage of arching bone block ribs (some fossilized) over the back half
 * of the spine with a dorsal spike on every other vertebra, and in front a skull with an open jaw, teeth and two
 * swept-back horns. A few of its bones are Dragon Scale Fossils, where the scales are still pressed into the bone.
 * <p>
 * The spine is {@code spine_length} blocks (the skull adds six more), the ribs rise 3 to 6 blocks over it, and the
 * whole thing stays within 12 blocks of the origin so it never writes outside the chunks a feature may touch. The
 * origin is the bottom of the rib cage, under the middle of the skeleton. Like vanilla's fossils it weathers: each
 * bone is left out with a chance of {@code 1 - integrity} (scale fossils never are), it won't replace bedrock and
 * the like ({@code minecraft:features_cannot_replace}), and it isn't placed at all if more than a third of it would
 * hang in air or water, as in a big cave.
 */
public record DragonFossilFeature(IntProvider spineLength, IntProvider scaleFossils, float integrity) implements Feature {
	public static final MapCodec<DragonFossilFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			IntProviders.codec(6, 18).fieldOf("spine_length").forGetter(DragonFossilFeature::spineLength),
			IntProviders.codec(0, 8).fieldOf("scale_fossils").forGetter(DragonFossilFeature::scaleFossils),
			Codec.floatRange(0.0f, 1.0f).fieldOf("integrity").forGetter(DragonFossilFeature::integrity))
			.apply(i, DragonFossilFeature::new));
	/** Skull plus the block of neck before it. */
	private static final int SKULL_LENGTH = 6;
	private static final float FOSSILIZED_RIB_CHANCE = 0.3f;
	private static final float MAX_EXPOSED = 1.0f / 3.0f;

	@Override
	public MapCodec<DragonFossilFeature> codec() {
		return CODEC;
	}

	/** Bones laid out in the skeleton's own frame, then turned into the world. */
	private static final class Skeleton {
		final Map<BlockPos, BlockState> bones = new LinkedHashMap<>();
		/** Where a scale fossil may go: spine and ribs, not the skull. */
		final List<BlockPos> body = new ArrayList<>();
		final Direction forward;
		final Direction side;
		final BlockPos origin;

		Skeleton(BlockPos origin, Direction forward, Direction side) {
			this.origin = origin;
			this.forward = forward;
			this.side = side;
		}

		/** u along the spine (toward the head), v to the side, y up. */
		BlockPos at(int u, int v, int y) {
			return origin.relative(forward, u).relative(side, v).above(y);
		}

		void put(int u, int v, int y, BlockState state, boolean body) {
			BlockPos pos = at(u, v, y);
			bones.put(pos, state);
			if (body) {
				this.body.add(pos);
			}
		}

		/** A pillar block lying along the spine, across it, or upright. */
		BlockState along(BlockState state) {
			return state.setValue(RotatedPillarBlock.AXIS, forward.getAxis());
		}

		BlockState across(BlockState state) {
			return state.setValue(RotatedPillarBlock.AXIS, side.getAxis());
		}

		static BlockState upright(BlockState state) {
			return state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		}
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
		Direction forward = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction side = random.nextBoolean() ? forward.getClockWise() : forward.getCounterClockWise();
		Skeleton skeleton = new Skeleton(origin, forward, side);
		BlockState fossil = Dragonscales.FOSSILIZED_DRAGON_BONE.defaultBlockState();
		BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();

		int length = spineLength.sample(random);
		int height = 3 + length / 6;
		int tail = u0(length);
		int tailLength = Math.max(2, length * 2 / 5);
		int cageStart = tail + tailLength;
		int head = tail + length;

		// Spine: the tail rises from the ground to the back, then runs level to the neck.
		for (int u = tail; u < head; u++) {
			int y = u < cageStart ? (u - tail + 1) * height / (tailLength + 1) : height;
			skeleton.put(u, 0, y, skeleton.along(fossil), true);
			if (u >= cageStart && (u - cageStart) % 2 == 1) {
				skeleton.put(u, 0, y + 1, Skeleton.upright(bone), false);
			}
		}

		// Ribs: an arch from the spine down to the ground on each side, widest mid-cage.
		for (int u = cageStart; u < head - 1; u += 2) {
			float along = (u - cageStart) / (float) Math.max(1, head - 2 - cageStart);
			int width = height - (along < 0.2f || along > 0.8f ? 1 : 0);
			for (int sign = -1; sign <= 1; sign += 2) {
				int lastV = 0, lastY = height;
				for (int step = 1; step <= 24; step++) {
					double angle = step / 24.0 * Math.PI / 2.0;
					int v = (int) Math.round(width * Math.sin(angle));
					int y = (int) Math.round(height * Math.cos(angle));
					if (v == lastV && y == lastY) {
						continue;
					}
					BlockState rib = random.nextFloat() < FOSSILIZED_RIB_CHANCE ? fossil : bone;
					// Bones lie the way the rib runs there: across near the top, upright down the sides.
					skeleton.put(u, sign * v, y, lastY - y > Math.abs(v - lastV) ? Skeleton.upright(rib) : skeleton.across(rib), true);
					lastV = v;
					lastY = y;
				}
			}
		}

		// Skull: a cranium, a snout, an open lower jaw with teeth between, and horns sweeping back over the neck.
		int s0 = head + 1;
		int top = height;
		skeleton.put(head, 0, top, skeleton.along(fossil), false);
		for (int u = s0; u < s0 + 3; u++) {
			for (int v = -1; v <= 1; v++) {
				skeleton.put(u, v, top - 1, skeleton.along(fossil), false);
				if (!(u == s0 + 2 && v != 0)) {
					// The eye sockets are left as they were.
					skeleton.put(u, v, top, skeleton.along(fossil), false);
				}
			}
		}
		for (int u = s0 + 3; u < s0 + 5; u++) {
			for (int v = -1; v <= 1; v++) {
				skeleton.put(u, v, top - 1, skeleton.along(fossil), false);
			}
			skeleton.put(u, 0, top, skeleton.along(fossil), false);
			skeleton.put(u, -1, top - 2, Skeleton.upright(bone), false);
			skeleton.put(u, 1, top - 2, Skeleton.upright(bone), false);
		}
		for (int u = s0 + 1; u < s0 + 5; u++) {
			skeleton.put(u, -1, top - 3, skeleton.along(fossil), false);
			skeleton.put(u, 1, top - 3, skeleton.along(fossil), false);
		}
		for (int v = -1; v <= 1; v += 2) {
			skeleton.put(s0, v, top + 1, Skeleton.upright(bone), false);
			skeleton.put(s0 - 1, v, top + 2, skeleton.along(bone), false);
			skeleton.put(s0 - 2, v, top + 2, skeleton.along(bone), false);
		}

		// Too much of it in the open (a cave, a ravine, the sea floor): leave this one buried elsewhere.
		int exposed = 0;
		for (BlockPos pos : skeleton.bones.keySet()) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				exposed++;
			}
		}
		if (exposed > skeleton.bones.size() * MAX_EXPOSED) {
			return false;
		}

		for (int i = scaleFossils.sample(random); i > 0 && !skeleton.body.isEmpty(); i--) {
			BlockPos pos = skeleton.body.remove(random.nextInt(skeleton.body.size()));
			skeleton.bones.put(pos, Dragonscales.DRAGON_SCALE_FOSSIL.defaultBlockState());
		}
		boolean placed = false;
		for (Map.Entry<BlockPos, BlockState> entry : skeleton.bones.entrySet()) {
			BlockState state = entry.getValue();
			if (!state.is(Dragonscales.DRAGON_SCALE_FOSSIL) && random.nextFloat() >= integrity) {
				continue;
			}
			if (level.getBlockState(entry.getKey()).is(BlockTags.FEATURES_CANNOT_REPLACE)) {
				continue;
			}
			level.setBlock(entry.getKey(), state, 2);
			placed = true;
		}
		return placed;
	}

	/** Where the tail starts, so the whole skeleton (spine and skull) is centred on the origin. */
	private static int u0(int length) {
		return -(length + SKULL_LENGTH) / 2;
	}
}
