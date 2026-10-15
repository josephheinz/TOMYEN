package com.jheinz.mymod.detector;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.mymod.dragonscale.Dragonscales;
import com.jheinz.mymod.fae.Fae;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.meteorite.Meteorites;
import com.jheinz.mymod.silver.Silver;
import com.jheinz.mymod.tungsten.Tungsten;
import com.jheinz.mymod.uranium.Uranium;

/**
 * What a Prospector's Pendulum can be tuned to: an ore, the blocks that count as it, and the items that tune it (its
 * ore blocks themselves always do, and so does what the ore drops). Vanilla's ores and every ore this mod adds.
 *
 * @param id     saved on the pendulum ({@link Detectors#PENDULUM_TUNING})
 * @param icon   shown on the HUD
 * @param colour the ore's colour, for the HUD (RGB)
 */
public record OreTarget(String id, Predicate<BlockState> blocks, Item icon, Set<Item> tuners, int colour) {
	public static final List<OreTarget> ALL = List.of(
			new OreTarget("coal", state -> state.is(BlockItemTags.COAL_ORES.block()), Items.COAL, Set.of(Items.COAL), 0x8C8C8C),
			new OreTarget("iron", state -> state.is(BlockTags.IRON_ORES), Items.RAW_IRON, Set.of(Items.RAW_IRON), 0xE8B898),
			new OreTarget("copper", state -> state.is(BlockTags.COPPER_ORES), Items.RAW_COPPER, Set.of(Items.RAW_COPPER), 0xFF8A4C),
			new OreTarget("gold", state -> state.is(BlockTags.GOLD_ORES), Items.RAW_GOLD, Set.of(Items.RAW_GOLD), 0xFFD83D),
			new OreTarget("redstone", state -> state.is(BlockItemTags.REDSTONE_ORES.block()), Items.REDSTONE, Set.of(Items.REDSTONE), 0xFF3A3A),
			new OreTarget("lapis", state -> state.is(BlockItemTags.LAPIS_ORES.block()), Items.LAPIS_LAZULI, Set.of(Items.LAPIS_LAZULI), 0x4A70FF),
			new OreTarget("diamond", state -> state.is(BlockItemTags.DIAMOND_ORES.block()), Items.DIAMOND, Set.of(Items.DIAMOND), 0x4DF5FF),
			new OreTarget("emerald", state -> state.is(BlockItemTags.EMERALD_ORES.block()), Items.EMERALD, Set.of(Items.EMERALD), 0x2DFF6A),
			new OreTarget("quartz", state -> state.is(Blocks.NETHER_QUARTZ_ORE), Items.QUARTZ, Set.of(Items.QUARTZ), 0xF2EDE4),
			new OreTarget("ancient_debris", state -> state.is(Blocks.ANCIENT_DEBRIS), Items.NETHERITE_SCRAP, Set.of(Items.NETHERITE_SCRAP),
					0xB0603A),
			new OreTarget("silver", blocks(Silver.SILVER_ORE, Silver.DEEPSLATE_SILVER_ORE), Silver.RAW_SILVER, Set.of(Silver.RAW_SILVER), 0xD8E4EC),
			new OreTarget("fae_silver", blocks(Fae.FAE_SILVER_ORE), Fae.RAW_FAE_SILVER, Set.of(Fae.RAW_FAE_SILVER), 0xC9A8FF),
			new OreTarget("tungsten", blocks(Tungsten.TUNGSTEN_ORE), Tungsten.RAW_TUNGSTEN, Set.of(Tungsten.RAW_TUNGSTEN), 0x8C96A8),
			new OreTarget("uranium", state -> state.is(Uranium.URANIUM_ORES), Uranium.RAW_URANIUM, Set.of(Uranium.RAW_URANIUM), 0x9CFF3A),
			new OreTarget("meteoric_iron", blocks(Meteorites.METEORIC_IRON_ORE), Meteorites.RAW_METEORIC_IRON,
					Set.of(Meteorites.RAW_METEORIC_IRON), 0x9C8CFF),
			new OreTarget("bauxite", blocks(Industry.BAUXITE_ORE), Industry.BAUXITE, Set.of(Industry.BAUXITE), 0xD9773A),
			new OreTarget("oil_shale", blocks(Industry.OIL_SHALE, Industry.DEEPSLATE_OIL_SHALE), Industry.SHALE_CHUNK, Set.of(Industry.SHALE_CHUNK),
					0x5A4A3A),
			new OreTarget("dragon_scale_fossil", blocks(Dragonscales.DRAGON_SCALE_FOSSIL, Dragonscales.FOSSILIZED_DRAGON_BONE),
					Dragonscales.DRAGON_SCALE_FOSSIL.asItem(), Set.of(), 0xE0D2B0));

	private static Predicate<BlockState> blocks(Block... blocks) {
		Set<Block> set = Set.of(blocks);
		return state -> set.contains(state.getBlock());
	}

	public Component name() {
		return Component.translatable("detector.mymod.ore." + id);
	}

	public static @Nullable OreTarget byId(@Nullable String id) {
		for (OreTarget target : ALL) {
			if (target.id.equals(id)) {
				return target;
			}
		}
		return null;
	}

	/** What this stack would tune a pendulum to: an ore block of it, or what one drops. Null if neither. */
	public static @Nullable OreTarget tunedBy(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (OreTarget target : ALL) {
			if (target.tuners.contains(stack.getItem())
					|| stack.getItem() instanceof BlockItem block && target.blocks.test(block.getBlock().defaultBlockState())) {
				return target;
			}
		}
		return null;
	}
}
