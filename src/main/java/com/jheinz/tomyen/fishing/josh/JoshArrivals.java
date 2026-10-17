package com.jheinz.tomyen.fishing.josh;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.jheinz.tomyen.MyMod;

/**
 * Josh moving into a village. A village is a bell (a meeting point) with villagers about it, so this works with any
 * village however it came to be: generated, grown, or built by a player.
 *
 * <p>Once a minute, the bells near each player are looked at. Whether a village gets a Josh at all is fixed by the
 * world's seed and the bell's position (about one village in eight), so it can't be rerolled, and each village gets him
 * once: the bells he has moved in by are kept on the level. He turns up out of sight of every player, a short walk
 * from the bell, with his Fish Fish Crate beside him (his job site, so he can restock), a villager of the biome's kind,
 * already Josh: he has traded before, so losing his crate never costs him his job. A village that already has a Josh
 * (one made at a crate, say) never gets another.
 */
public final class JoshArrivals {
	/** The bells Josh has moved in by, so no village gets him twice. */
	public static final AttachmentType<List<Long>> ARRIVED = AttachmentRegistry.<List<Long>>builder()
			.persistent(Codec.LONG.listOf())
			.initializer(List::of)
			.buildAndRegister(MyMod.id("josh_arrivals"));

	/** How often (ticks) the bells near players are looked at. */
	private static final int CHECK_EVERY = 1200;
	/** The share of villages Josh moves into. */
	private static final float CHANCE = 0.125f;
	/** How far from a player bells are looked for, and how far from the bell villagers are counted. */
	private static final int SEARCH = 64;
	private static final double VILLAGE = 48.0;
	/** A village needs this many grown villagers before Josh will bother. */
	private static final int MIN_VILLAGERS = 3;
	/** How far from a bell a Josh counts as that village's. */
	private static final double JOSH_RANGE = 96.0;
	/** No player may be this near where he turns up. */
	private static final double UNSEEN = 24.0;
	/** Mixed into the seed so this roll is Josh's own. */
	private static final long SALT = 0x4A6F7368L;

	private JoshArrivals() {
	}

	/** Registers the level attachment (it has to exist before any level loads). */
	static void initialize() {
	}

	static void tick(ServerLevel level) {
		if (level.getGameTime() % CHECK_EVERY != 37 || level.players().isEmpty()) {
			return;
		}
		List<BlockPos> seen = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			level.getPoiManager().findAll(holder -> holder.is(PoiTypes.MEETING), pos -> true, player.blockPosition(), SEARCH,
					PoiManager.Occupancy.ANY).forEach(bell -> {
						if (!seen.contains(bell)) {
							seen.add(bell.immutable());
						}
					});
		}
		for (BlockPos bell : seen) {
			if (wantsJosh(level, bell) && moveIn(level, bell)) {
				List<Long> arrived = new ArrayList<>(level.getAttachedOrElse(ARRIVED, List.of()));
				arrived.add(bell.asLong());
				level.setAttached(ARRIVED, List.copyOf(arrived));
			}
		}
	}

	/** Whether Josh is due at this bell's village: its roll came up, he hasn't come yet, and it's a village. */
	private static boolean wantsJosh(ServerLevel level, BlockPos bell) {
		if (RandomSource.create(level.getSeed() ^ bell.asLong() * 0x9E3779B97F4A7C15L ^ SALT).nextFloat() >= CHANCE) {
			return false;
		}
		if (level.getAttachedOrElse(ARRIVED, List.of()).contains(bell.asLong()) || !level.isLoaded(bell)) {
			return false;
		}
		if (!level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(JOSH_RANGE), Josh::isJosh).isEmpty()) {
			return false;
		}
		return level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(VILLAGE), villager -> !villager.isBaby()).size()
				>= MIN_VILLAGERS;
	}

	/** Josh and his crate, somewhere near the bell no one is looking. False if there was nowhere to put them. */
	private static boolean moveIn(ServerLevel level, BlockPos bell) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 16; attempt++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			int distance = 6 + random.nextInt(10);
			int x = bell.getX() + Mth.floor(Mth.cos(angle) * distance);
			int z = bell.getZ() + Mth.floor(Mth.sin(angle) * distance);
			BlockPos stand = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos crate = stand.relative(side);
			if (!standable(level, stand) || !standable(level, crate) || crate.getY() != stand.getY() || seen(level, stand)) {
				continue;
			}
			level.setBlockAndUpdate(crate, Josh.FISH_FISH_CRATE.defaultBlockState()
					.setValue(FishFishCrateBlock.FACING, side.getOpposite()));
			Villager josh = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (josh == null) {
				return false;
			}
			josh.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, side.toYRot(), 0.0f);
			josh.finalizeSpawn(level, level.getCurrentDifficultyAt(stand), EntitySpawnReason.EVENT, null);
			josh.setVillagerData(josh.getVillagerData().withProfession(level.registryAccess(), Josh.PROFESSION_KEY).withLevel(1));
			josh.setVillagerXp(1);
			Josh.name(josh);
			level.addFreshEntity(josh);
			MyMod.LOGGER.info("Josh moved in by the bell at {}", bell);
			return true;
		}
		return false;
	}

	/** Loaded, dry, on solid ground, with room for a villager. */
	private static boolean standable(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
			return false;
		}
		BlockState ground = level.getBlockState(pos.below());
		return ground.isFaceSturdy(level, pos.below(), Direction.UP) && level.getFluidState(pos.below()).isEmpty()
				&& level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above());
	}

	private static boolean seen(ServerLevel level, BlockPos pos) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < UNSEEN * UNSEEN) {
				return true;
			}
		}
		return false;
	}
}
