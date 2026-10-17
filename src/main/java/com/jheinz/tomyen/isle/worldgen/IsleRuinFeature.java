package com.jheinz.tomyen.isle.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import com.jheinz.tomyen.isle.BonePileBlock;
import com.jheinz.tomyen.isle.Isle;
import com.jheinz.tomyen.isle.OldHeadstoneBlock;

/**
 * The Isle of the Damned's set dressing, one kind per configured feature ({@code data/tomyen/worldgen/feature/isle_*.json}):
 *
 * <ul>
 * <li>{@code dead_tree}: a bare, bleached tree (pale oak logs), leaning a little, with a few crooked branches and now
 * and then a beard of pale hanging moss.
 * <li>{@code headstones}: a huddle of two to five old headstones at all angles, sometimes with a heap of bones.
 * <li>{@code arch}: a broken stone arch, two brick pillars with a lintel of stairs, one side often snapped short.
 * <li>{@code bones}: bone piles round a half-buried bone block and a skull.
 * <li>{@code brazier}: a soul brazier on a small brick plinth.
 * </ul>
 *
 * Everything stands only on the Isle's own dry ground ({@code #tomyen:isle_ground}), never in water.
 */
public record IsleRuinFeature(Kind kind) implements Feature {
	public static final MapCodec<IsleRuinFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Kind.CODEC.fieldOf("kind").forGetter(IsleRuinFeature::kind)).apply(i, IsleRuinFeature::new));

	public enum Kind implements StringRepresentable {
		DEAD_TREE("dead_tree"), HEADSTONES("headstones"), ARCH("arch"), BONES("bones"), BRAZIER("brazier");

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	@Override
	public MapCodec<IsleRuinFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos pos = ground(level, origin);
		if (pos == null) {
			return false;
		}
		return switch (kind) {
			case DEAD_TREE -> deadTree(level, random, pos);
			case HEADSTONES -> headstones(level, random, pos);
			case ARCH -> arch(level, random, pos);
			case BONES -> bones(level, random, pos);
			case BRAZIER -> brazier(level, random, pos);
		};
	}

	/** The open air just above Isle ground at or a little below {@code at}; null if there's none (water, say). */
	private static BlockPos ground(WorldGenLevel level, BlockPos at) {
		BlockPos.MutableBlockPos pos = at.mutable();
		for (int i = 0; i < 4; i++) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Isle.ISLE_GROUND)) {
				return pos.immutable();
			}
			pos.move(Direction.DOWN);
		}
		return null;
	}

	private static boolean open(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	private static boolean deadTree(WorldGenLevel level, RandomSource random, BlockPos base) {
		int height = 4 + random.nextInt(5);
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockState log = Blocks.PALE_OAK_LOG.defaultBlockState();
		BlockPos.MutableBlockPos pos = base.mutable();
		BlockPos top = base;
		for (int y = 0; y < height; y++) {
			if (!open(level, pos)) {
				return y > 2;
			}
			level.setBlock(pos, log, 2);
			top = pos.immutable();
			pos.move(Direction.UP);
			// It leans: every few blocks up the trunk steps over.
			if (y >= 2 && random.nextInt(4) == 0) {
				pos.move(lean);
			}
		}
		int branches = 2 + random.nextInt(3);
		for (int b = 0; b < branches; b++) {
			Direction out = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos.MutableBlockPos branch = top.below(random.nextInt(Math.max(1, height - 2))).mutable();
			int length = 1 + random.nextInt(3);
			for (int i = 0; i < length; i++) {
				branch.move(out);
				if (i > 0 && random.nextBoolean()) {
					branch.move(Direction.UP);
				}
				if (!open(level, branch)) {
					break;
				}
				level.setBlock(branch, log.setValue(RotatedPillarBlock.AXIS, out.getAxis()), 2);
				if (i == length - 1 && random.nextInt(3) == 0 && open(level, branch.below())) {
					level.setBlock(branch.below(), Blocks.PALE_HANGING_MOSS.defaultBlockState(), 2);
				}
			}
		}
		return true;
	}

	private static boolean headstones(WorldGenLevel level, RandomSource random, BlockPos centre) {
		int count = 2 + random.nextInt(4);
		int placed = 0;
		for (int i = 0; i < count * 2 && placed < count; i++) {
			BlockPos spot = ground(level, centre.offset(random.nextInt(7) - 3, 1, random.nextInt(7) - 3));
			if (spot == null) {
				continue;
			}
			level.setBlock(spot, Isle.OLD_HEADSTONE.defaultBlockState()
					.setValue(OldHeadstoneBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
					.setValue(OldHeadstoneBlock.LEAN, random.nextInt(3)), 2);
			placed++;
		}
		if (placed > 0 && random.nextInt(3) == 0) {
			BlockPos spot = ground(level, centre.offset(random.nextInt(5) - 2, 1, random.nextInt(5) - 2));
			if (spot != null) {
				level.setBlock(spot, Isle.BONE_PILE.defaultBlockState().setValue(BonePileBlock.FACING,
						Direction.Plane.HORIZONTAL.getRandomDirection(random)), 2);
			}
		}
		return placed > 0;
	}

	private static BlockState brick(RandomSource random) {
		int roll = random.nextInt(10);
		return roll < 3 ? Isle.CRACKED_GRAVE_STONE_BRICKS.defaultBlockState()
				: roll < 5 ? Isle.MOSSY_GRAVE_STONE_BRICKS.defaultBlockState() : Isle.GRAVE_STONE_BRICKS.defaultBlockState();
	}

	private static boolean arch(WorldGenLevel level, RandomSource random, BlockPos base) {
		Direction across = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		BlockPos other = ground(level, base.relative(across, 4).above(2));
		if (other == null || Math.abs(other.getY() - base.getY()) > 1) {
			return false;
		}
		int height = 4 + random.nextInt(2);
		boolean broken = random.nextInt(3) != 0;
		int otherHeight = broken ? 1 + random.nextInt(2) : height;
		for (int y = -1; y < height; y++) {
			level.setBlock(base.above(y), brick(random), 2);
		}
		for (int y = -1; y < otherHeight; y++) {
			level.setBlock(base.relative(across, 4).atY(base.getY() + y), brick(random), 2);
		}
		// The lintel: stairs turned down over each pillar, bricks between; a broken arch keeps only its first half.
		int top = base.getY() + height;
		int span = broken ? 2 : 4;
		for (int i = 0; i <= span; i++) {
			BlockPos lintel = base.relative(across, i).atY(top);
			if (!open(level, lintel)) {
				continue;
			}
			BlockState state;
			if (i == 1 || (!broken && i == 3)) {
				Direction facing = i == 1 ? across : across.getOpposite();
				state = Isle.GRAVE_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
			} else if (i == 0 || i == 4) {
				state = brick(random);
			} else {
				state = Isle.GRAVE_STONE_BRICK_SLAB.defaultBlockState();
			}
			level.setBlock(lintel, state, 2);
		}
		if (broken) {
			// Rubble where the other half fell.
			for (int i = 0; i < 3; i++) {
				BlockPos rubble = ground(level, base.relative(across, 2 + random.nextInt(3)).relative(across.getClockWise(), random.nextInt(3) - 1)
						.above(2));
				if (rubble != null) {
					level.setBlock(rubble, random.nextBoolean() ? Isle.GRAVE_STONE_BRICK_SLAB.defaultBlockState() : brick(random), 2);
				}
			}
		}
		return true;
	}

	private static boolean bones(WorldGenLevel level, RandomSource random, BlockPos centre) {
		level.setBlock(centre.below(), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
				random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z), 2);
		if (open(level, centre)) {
			level.setBlock(centre, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), 2);
		}
		int piles = 1 + random.nextInt(3);
		for (int i = 0; i < piles; i++) {
			BlockPos spot = ground(level, centre.offset(random.nextInt(5) - 2, 1, random.nextInt(5) - 2));
			if (spot != null) {
				level.setBlock(spot, Isle.BONE_PILE.defaultBlockState().setValue(BonePileBlock.FACING,
						Direction.Plane.HORIZONTAL.getRandomDirection(random)), 2);
			}
		}
		return true;
	}

	private static boolean brazier(WorldGenLevel level, RandomSource random, BlockPos base) {
		if (!open(level, base.above())) {
			return false;
		}
		level.setBlock(base, Isle.GRAVE_STONE_BRICKS.defaultBlockState(), 2);
		level.setBlock(base.above(), Isle.SOUL_BRAZIER.defaultBlockState(), 2);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextInt(3) == 0) {
				BlockPos step = base.relative(side);
				if (open(level, step) && level.getBlockState(step.below()).is(Isle.ISLE_GROUND)) {
					level.setBlock(step, Isle.GRAVE_STONE_BRICK_SLAB.defaultBlockState(), 2);
				}
			}
		}
		return true;
	}
}
