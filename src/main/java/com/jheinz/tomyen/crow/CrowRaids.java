package com.jheinz.tomyen.crow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.jheinz.tomyen.scarecrow.CropGuard;

/**
 * Where crows go and what they take. Everything here is cheap on purpose, since a dozen birds call it:
 * <ul>
 * <li>Farms are found by looking only in chunk sections whose palette could hold a ripe crop at all, once every
 * twenty seconds per murder, and never where a scarecrow stands guard ({@link CropGuard#scaresCrows}).
 * <li>Landing spots (a fence, a roof, a treetop, a patch of grass) come from the heightmap, a few columns at a time.
 * <li>Nothing here loads a chunk: any column whose chunk isn't loaded is skipped.
 * </ul>
 */
public final class CrowRaids {
	/** How far a murder looks for a ripe farm. */
	public static final int FARM_RANGE = 40;
	private static final int FARM_HEIGHT = 16;
	private static final int GUARD_CHECKS = 16;

	private CrowRaids() {
	}

	public static boolean mayRaid(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	public static boolean isRipe(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	/** The nearest ripe crop no scarecrow guards and the murder isn't avoiding, within {@link #FARM_RANGE}; or null. */
	public static @Nullable BlockPos findFarm(ServerLevel level, BlockPos origin, Predicate<BlockPos> avoided) {
		List<BlockPos> ripe = new ArrayList<>();
		int minY = Math.max(level.getMinY(), origin.getY() - FARM_HEIGHT);
		int maxY = Math.min(level.getMaxY(), origin.getY() + FARM_HEIGHT);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = SectionPos.blockToSectionCoord(origin.getX() - FARM_RANGE); cx <= SectionPos.blockToSectionCoord(origin.getX() + FARM_RANGE); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(origin.getZ() - FARM_RANGE); cz <= SectionPos.blockToSectionCoord(origin.getZ() + FARM_RANGE); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(CrowRaids::isRipe)) {
						continue;
					}
					for (int y = 0; y < 16; y++) {
						int wy = SectionPos.sectionToBlockCoord(sy) + y;
						if (wy < minY || wy > maxY) {
							continue;
						}
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (isRipe(section.getBlockState(x, y, z))) {
									pos.set(SectionPos.sectionToBlockCoord(cx) + x, wy, SectionPos.sectionToBlockCoord(cz) + z);
									if (pos.distSqr(origin) <= (double) FARM_RANGE * FARM_RANGE && !avoided.test(pos)) {
										ripe.add(pos.immutable());
									}
								}
							}
						}
					}
				}
			}
		}
		ripe.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
		for (int i = 0; i < Math.min(GUARD_CHECKS, ripe.size()); i++) {
			if (!CropGuard.scaresCrows(level, ripe.get(i))) {
				return ripe.get(i);
			}
		}
		return null;
	}

	/** Every ripe crop within radius blocks of the centre (three up or down), nearest the centre first. */
	public static List<BlockPos> ripeAround(ServerLevel level, BlockPos centre, int radius) {
		List<BlockPos> ripe = new ArrayList<>();
		if (!level.hasChunksAt(centre.getX() - radius, centre.getZ() - radius, centre.getX() + radius, centre.getZ() + radius)) {
			return ripe;
		}
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -3, -radius), centre.offset(radius, 3, radius))) {
			if (isRipe(level.getBlockState(pos))) {
				ripe.add(pos.immutable());
			}
		}
		ripe.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		return ripe;
	}

	/**
	 * Pecks a ripe crop to bits: the produce (the wheat, the carrots) goes in the crow's beak, and the seeds it shakes
	 * loose fall to the ground, where the murder will be after them too. Returns the produce, or nothing if the crop
	 * isn't ripe and unguarded any more.
	 */
	public static ItemStack harvest(ServerLevel level, BlockPos pos, Entity thief) {
		BlockState state = level.getBlockState(pos);
		if (!isRipe(state) || !mayRaid(level) || CropGuard.scaresCrows(level, pos)) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, thief, ItemStack.EMPTY);
		Item seed = state.getBlock().asItem();
		Item produce = drops.stream().map(ItemStack::getItem).filter(item -> item != seed).findFirst()
				.orElse(drops.isEmpty() ? seed : drops.getFirst().getItem());
		level.destroyBlock(pos, false, thief);
		ItemStack taken = ItemStack.EMPTY;
		for (ItemStack drop : drops) {
			if (drop.is(produce) && taken.isEmpty()) {
				taken = drop;
			} else if (drop.is(produce) && ItemStack.isSameItemSameComponents(drop, taken)) {
				taken.grow(drop.getCount());
			} else {
				Block.popResource(level, pos, drop);
			}
		}
		return taken;
	}

	/** The height a bird stands at on the block column at pos (on whatever's at pos, or else on what's under it). */
	public static double surfaceY(Level level, BlockPos pos) {
		VoxelShape here = level.getBlockState(pos).getCollisionShape(level, pos);
		if (!here.isEmpty()) {
			return pos.getY() + here.max(Direction.Axis.Y);
		}
		BlockPos below = pos.below();
		VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
		return below.getY() + (under.isEmpty() ? 0.0 : under.max(Direction.Axis.Y));
	}

	public static Vec3 standPoint(Level level, BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, surfaceY(level, pos), pos.getZ() + 0.5);
	}

	/** Somewhere up off the ground a crow likes to sit: fences, walls, roofs, branches and treetops. */
	public static boolean isPerch(BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
				|| state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.FENCE_GATES);
	}

	/** Open ground to forage on: grass, dirt and tilled soil. */
	public static boolean isGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH);
	}

	/** What a landing search wants. */
	public enum Want {
		PERCH, GROUND, TREETOP, ANY
	}

	/** The top of the block column at x, z (the highest thing a bird could land on), or null if not loaded or water. */
	public static @Nullable BlockPos top(Level level, int x, int z) {
		if (!level.hasChunkAt(x, z)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		if (y < level.getMinY()) {
			return null;
		}
		BlockPos pos = new BlockPos(x, y, z);
		return level.getFluidState(pos).isEmpty() ? pos : null;
	}

	/** Whether a treetop at the top of this column is a real tree's crown (with clear air under the canopy somewhere). */
	public static boolean isTreetop(Level level, BlockPos top) {
		return level.getBlockState(top).is(BlockTags.LEAVES)
				&& level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.getX(), top.getZ()) <= top.getY() - 2;
	}

	/**
	 * A spot to land within radius of the centre: a few random columns, preferring what's wanted and taking anything
	 * dry to stand on if none of them have it. Null if the columns there aren't loaded.
	 */
	public static @Nullable Vec3 findLanding(Level level, Vec3 centre, double radius, Want want, RandomSource random) {
		BlockPos fallback = null;
		for (int i = 0; i < 8; i++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double r = Math.sqrt(random.nextDouble()) * radius;
			BlockPos top = top(level, Mth.floor(centre.x + Mth.cos(angle) * r), Mth.floor(centre.z + Mth.sin(angle) * r));
			if (top == null || Math.abs(top.getY() - centre.y) > 12) {
				continue;
			}
			BlockState state = level.getBlockState(top);
			boolean good = switch (want) {
				case PERCH -> isPerch(state);
				case GROUND -> isGround(state);
				case TREETOP -> isTreetop(level, top);
				case ANY -> true;
			};
			if (good) {
				return standPoint(level, top.above());
			}
			if (fallback == null) {
				fallback = top;
			}
		}
		return fallback == null ? null : standPoint(level, fallback.above());
	}

	/** A column within range of the origin with what's wanted on top of it, for a whole murder to head for; or null. */
	public static @Nullable BlockPos findArea(Level level, BlockPos origin, int range, Want want, RandomSource random, int tries) {
		for (int i = 0; i < tries; i++) {
			BlockPos top = top(level, origin.getX() + random.nextInt(range * 2 + 1) - range, origin.getZ() + random.nextInt(range * 2 + 1) - range);
			if (top == null) {
				continue;
			}
			BlockState state = level.getBlockState(top);
			boolean good = switch (want) {
				case PERCH -> isPerch(state);
				case GROUND -> isGround(state);
				case TREETOP -> isTreetop(level, top);
				case ANY -> true;
			};
			if (good) {
				return top;
			}
		}
		return null;
	}

	/**
	 * The nearest item on the ground in this tag within range that no other crow in the murder is after and no
	 * scarecrow guards (a scarecrow keeps them off its farm whatever's lying there); or null.
	 */
	public static @Nullable ItemEntity findItem(ServerLevel level, Crow crow, double range, TagKey<Item> tag, CrowFlock flock) {
		ItemEntity best = null;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, crow.getBoundingBox().inflate(range, 6.0, range),
				item -> item.isAlive() && item.getItem().is(tag) && !flock.isClaimed(item))) {
			if (best == null || crow.distanceToSqr(item) < crow.distanceToSqr(best)) {
				best = item;
			}
		}
		return best != null && !CropGuard.scaresCrows(level, best.blockPosition()) ? best : null;
	}
}
