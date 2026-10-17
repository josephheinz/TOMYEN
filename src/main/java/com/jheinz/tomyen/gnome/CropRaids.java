package com.jheinz.tomyen.gnome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gamerules.GameRules;

import com.jheinz.tomyen.scarecrow.CropGuard;

/**
 * What gnomes steal: any crop at its last stage of growth (wheat, carrots, potatoes, beetroots, and any modded crop
 * built the same way), and only where no scarecrow watches over it ({@link CropGuard#scaresGnomes}).
 *
 * <p>Finding a farm looks only at chunk sections whose palette could hold a crop at all, so a captain can cast about a
 * {@value #FARM_RANGE}-block radius cheaply. Harvesting takes the crop's produce (the wheat, not the seeds; the
 * carrots themselves) and leaves the farmland bare: gnomes replant nothing.
 */
public final class CropRaids {
	/** How far from itself a captain looks for ripe crops. */
	public static final int FARM_RANGE = 40;
	private static final int FARM_HEIGHT = 12;
	/** How many of the nearest ripe crops are checked for a scarecrow before giving up on finding one. */
	private static final int GUARD_CHECKS = 24;

	private CropRaids() {
	}

	public static boolean isRipe(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	public static boolean mayRaid(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** The nearest ripe, unguarded crop within {@link #FARM_RANGE} blocks, in loaded chunks only; or null. */
	public static @Nullable BlockPos findFarm(ServerLevel level, BlockPos origin) {
		List<BlockPos> ripe = new ArrayList<>();
		int minY = Math.max(level.getMinY(), origin.getY() - FARM_HEIGHT);
		int maxY = Math.min(level.getMaxY(), origin.getY() + FARM_HEIGHT);
		int minSection = SectionPos.blockToSectionCoord(minY);
		int maxSection = SectionPos.blockToSectionCoord(maxY);
		int minChunkX = SectionPos.blockToSectionCoord(origin.getX() - FARM_RANGE);
		int maxChunkX = SectionPos.blockToSectionCoord(origin.getX() + FARM_RANGE);
		int minChunkZ = SectionPos.blockToSectionCoord(origin.getZ() - FARM_RANGE);
		int maxChunkZ = SectionPos.blockToSectionCoord(origin.getZ() + FARM_RANGE);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (int sy = minSection; sy <= maxSection; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(CropRaids::isRipe)) {
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
									if (pos.distSqr(origin) <= (double) FARM_RANGE * FARM_RANGE) {
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
			if (!CropGuard.scaresGnomes(level, ripe.get(i))) {
				return ripe.get(i);
			}
		}
		return null;
	}

	/** Every ripe crop within radius blocks of the centre (two up or down), nearest first. */
	public static List<BlockPos> ripeAround(ServerLevel level, BlockPos centre, int radius) {
		List<BlockPos> ripe = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
			if (isRipe(level.getBlockState(pos))) {
				ripe.add(pos.immutable());
			}
		}
		ripe.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		return ripe;
	}

	/**
	 * Pulls up a ripe crop and returns its produce (or nothing, if it isn't a ripe, unguarded crop any more). The block
	 * breaks with its usual sound and particles but drops nothing itself: the gnome has it all.
	 */
	public static ItemStack harvest(ServerLevel level, BlockPos pos, Entity thief) {
		BlockState state = level.getBlockState(pos);
		if (!isRipe(state) || CropGuard.scaresGnomes(level, pos)) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, thief, ItemStack.EMPTY);
		Item seed = state.getBlock().asItem();
		Item produce = drops.stream().map(ItemStack::getItem).filter(item -> item != seed).findFirst()
				.orElse(drops.isEmpty() ? seed : drops.getFirst().getItem());
		int count = drops.stream().filter(stack -> stack.is(produce)).mapToInt(ItemStack::getCount).sum();
		level.destroyBlock(pos, false, thief);
		return new ItemStack(produce, Math.max(1, count));
	}
}
