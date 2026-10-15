package com.jheinz.mymod.client.detector;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.detector.Detectors;
import com.jheinz.mymod.fae.Fae;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.meteorite.Meteorites;
import com.jheinz.mymod.silver.Silver;
import com.jheinz.mymod.tungsten.Tungsten;
import com.jheinz.mymod.uranium.Uranium;

/**
 * A held Metal Detector. Within {@value Detectors#METAL_DETECTOR_RANGE} blocks it picks up metal: metal ores and raw
 * metal blocks, meteorite fragments, chests and barrels (a cache) and the suspicious sand and gravel that buries
 * loot, plus dropped metal and mod scrap lying about. Each find gives a signal of its strength times how close it
 * is (1 right on it, 0 at the edge), and the strongest is the reading: beeps come faster and higher as it rises,
 * from about one a second and a half to ten a second. Blocks come from a {@link SectionScanner}; dropped items are
 * checked every few ticks.
 */
final class MetalDetectorClient {
	enum Find {
		ORE(0.8f), METEORITE(1.0f), CACHE(1.0f), BURIED(1.0f), SCRAP(0.7f);

		final float strength;

		Find(float strength) {
			this.strength = strength;
		}
	}

	private static final SectionScanner SCANNER = new SectionScanner(Detectors.METAL_DETECTOR_RANGE, 12);
	private static final Predicate<BlockState> METAL = state -> classify(state) != null;
	private static final int UPDATE_TICKS = 4;

	private static float signal;
	private static float shown;
	private static @Nullable Find strongest;
	private static int depth;
	private static int beepIn;
	private static int updateIn;
	private static boolean held;

	private MetalDetectorClient() {
	}

	static void reset() {
		SCANNER.reset();
		signal = 0.0f;
		shown = 0.0f;
		strongest = null;
		held = false;
	}

	static @Nullable Find classify(BlockState state) {
		Block block = state.getBlock();
		if (state.is(Meteorites.METEORIC_IRON_ORE) || state.is(Meteorites.METEORITE_STONE)) {
			return Find.METEORITE;
		}
		if (block instanceof ChestBlock || block instanceof BarrelBlock) {
			return Find.CACHE;
		}
		if (state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
			return Find.BURIED;
		}
		if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES) || state.is(Blocks.ANCIENT_DEBRIS)
				|| state.is(Silver.SILVER_ORE) || state.is(Silver.DEEPSLATE_SILVER_ORE) || state.is(Fae.FAE_SILVER_ORE)
				|| state.is(Tungsten.TUNGSTEN_ORE) || state.is(Uranium.URANIUM_ORES) || state.is(Industry.BAUXITE_ORE)
				|| state.is(Blocks.RAW_IRON_BLOCK) || state.is(Blocks.RAW_COPPER_BLOCK) || state.is(Blocks.RAW_GOLD_BLOCK)) {
			return Find.ORE;
		}
		return null;
	}

	/** Dropped things worth a beep: metal (ingots, nuggets, raw ore) and anything of this mod's (scrap, parts, guns). */
	private static boolean metallic(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return MyMod.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()) || path.contains("iron")
				|| path.contains("gold") || path.contains("copper") || path.contains("netherite") || path.contains("chain");
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (DetectorClient.held(player, Detectors.METAL_DETECTOR).isEmpty()) {
			if (held) {
				reset();
			}
			return;
		}
		held = true;
		ClientLevel level = minecraft.level;
		// The search coil sweeps low, at the holder's feet.
		Vec3 coil = player.position().add(0.0, 0.5, 0.0);
		SCANNER.tick(level, BlockPos.containing(coil), "metal", METAL);
		if (--updateIn <= 0) {
			updateIn = UPDATE_TICKS;
			update(level, coil);
		}
		shown += (signal - shown) * 0.35f;
		if (signal > 0.02f && --beepIn <= 0) {
			beepIn = Math.round(Mth.lerp((float) Math.pow(signal, 0.8), 28.0f, 2.0f));
			DetectorClient.play(minecraft, player, Detectors.METAL_DETECTOR_BEEP, 0.25f + 0.25f * signal, 0.7f + 1.1f * signal);
		}
	}

	private static void update(ClientLevel level, Vec3 coil) {
		double range = Detectors.METAL_DETECTOR_RANGE;
		float[] best = {0.0f};
		Find[] kind = {null};
		int[] dy = {0};
		SCANNER.forEach(packed -> {
			double d = coil.distanceTo(new Vec3(BlockPos.getX(packed) + 0.5, BlockPos.getY(packed) + 0.5, BlockPos.getZ(packed) + 0.5));
			if (d >= range) {
				return;
			}
			Find find = classify(level.getBlockState(BlockPos.of(packed)));
			if (find == null) {
				return;
			}
			float strength = find.strength * (float) (1.0 - d / range);
			if (strength > best[0]) {
				best[0] = strength;
				kind[0] = find;
				dy[0] = BlockPos.getY(packed) - Mth.floor(coil.y);
			}
		});
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(coil, coil).inflate(range),
				item -> metallic(item.getItem()))) {
			double d = coil.distanceTo(item.position());
			float strength = Find.SCRAP.strength * (float) Math.max(0.0, 1.0 - d / range);
			if (strength > best[0]) {
				best[0] = strength;
				kind[0] = Find.SCRAP;
				dy[0] = Mth.floor(item.getY()) - Mth.floor(coil.y);
			}
		}
		signal = best[0];
		strongest = kind[0];
		depth = dy[0];
	}

	/** The reading, eased for drawing. */
	static float signal() {
		return shown;
	}

	static @Nullable Find strongest() {
		return strongest;
	}

	/** Blocks the strongest find lies above (+) or below (-) the coil. */
	static int depth() {
		return depth;
	}
}
