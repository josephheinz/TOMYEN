package com.jheinz.mymod.fishing;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.magmasteel.Magmasteel;
import com.jheinz.mymod.meteorite.Meteorites;

/**
 * Fishing: six mod rods with a quick-time minigame when a fish bites (see {@link RodItem}, {@link RodHook},
 * {@link FishingFights} and client/fishing), the parts that customize them at the Tackle Bench, and the species data
 * files every catch is rolled from ({@link FishingSpeciesLoader}). Vanilla's fishing rod is left exactly as it is.
 *
 * <p>Rods, by tier: Bamboo (0, Scavenger), Iron (1), Steel (2, Foundry), Duralumin (3, light and quick to bite),
 * Magmasteel (3, fishes lava) and Star Steel (4, Exotic).
 */
public final class Fishing {
	/** On a mod rod: the parts fitted at the Tackle Bench. */
	public static final DataComponentType<RodParts> ROD_PARTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("rod_parts"), DataComponentType.<RodParts>builder().persistent(RodParts.CODEC).networkSynchronized(RodParts.STREAM_CODEC).build());

	/** Catches a silver hook makes easier: cursed and evil things. Empty unless a species' item is tagged. */
	public static final TagKey<Item> CURSED_CATCHES = TagKey.create(Registries.ITEM, MyMod.id("cursed_catches"));

	/** A mod rod's bobber. Like vanilla's, but fireproof (a Magmasteel Rod's floats on lava). */
	public static final EntityType<RodHook> ROD_HOOK = entity("rod_bobber", EntityType.Builder.<RodHook>of(RodHook::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.noSummon()
			.fireImmune()
			.sized(0.25f, 0.25f)
			.clientTrackingRange(4)
			.updateInterval(5));

	// --- Rods ---------------------------------------------------------------------------------------------------
	public static final Item BAMBOO_ROD = rod("bamboo_rod", 0, false, 64, Items.BAMBOO,
			RodModifiers.builder().zone(0.1f).build(), properties -> properties);
	public static final Item IRON_ROD = rod("iron_rod", 1, false, 128, Items.IRON_INGOT,
			RodModifiers.builder().line(0.1f).reel(0.1f).build(), properties -> properties);
	public static final Item STEEL_ROD = rod("steel_rod", 2, false, 256, Industry.STEEL_INGOT,
			RodModifiers.builder().zone(0.1f).speed(-0.1f).line(0.1f).build(), properties -> properties);
	public static final Item DURALUMIN_ROD = rod("duralumin_rod", 3, false, 320, Industry.DURALUMIN_SHEET,
			RodModifiers.builder().biteTicks(60).zone(0.05f).speed(-0.1f).reel(0.1f).build(), properties -> properties);
	public static final Item MAGMASTEEL_ROD = rod("magmasteel_rod", 3, true, 384, Magmasteel.SET.ingot,
			RodModifiers.builder().zone(0.05f).line(0.2f).escape(0.1f).build(), Item.Properties::fireResistant);
	public static final Item STAR_STEEL_ROD = rod("star_steel_rod", 4, false, 768, Meteorites.STAR_STEEL_INGOT,
			RodModifiers.builder().biteTicks(40).zone(0.2f).speed(-0.2f).reel(0.2f).rarity(0.15f).build(),
			properties -> properties.fireResistant().rarity(Rarity.UNCOMMON));

	// --- Parts --------------------------------------------------------------------------------------------------
	public static final Item RUBBER_LINE = part("rubber_line", PartSlot.LINE, RodModifiers.builder().line(0.35f).build());
	/** Braided from a Schnozzle's nose hair: nearly unbreakable, and the fish can smell it. */
	public static final Item NOSE_HAIR_LINE = part("nose_hair_line", PartSlot.LINE,
			RodModifiers.builder().line(0.6f).escape(0.1f).biteTicks(-40).build());
	public static final Item FAE_SILK_LINE = part("fae_silk_line", PartSlot.LINE,
			RodModifiers.builder().line(0.25f).zone(0.1f).rarity(0.05f).build());
	public static final Item IRON_HOOK = part("iron_hook", PartSlot.HOOK, RodModifiers.builder().escape(0.15f).build());
	public static final Item SILVER_HOOK = part("silver_hook", PartSlot.HOOK, RodModifiers.builder().escape(0.1f).cursedBonus().build());
	public static final Item TUNGSTEN_HOOK = part("tungsten_hook", PartSlot.HOOK, RodModifiers.builder().escape(0.4f).build());
	public static final Item COPPER_REEL = part("copper_reel", PartSlot.REEL, RodModifiers.builder().reel(0.1f).build());
	public static final Item GEARED_REEL = part("geared_reel", PartSlot.REEL, RodModifiers.builder().reel(0.3f).build());
	public static final Item SERVO_REEL = part("servo_reel", PartSlot.REEL, RodModifiers.builder().assist(0.35f).reel(0.15f).build());
	public static final Item GLOW_LURE = part("glow_lure", PartSlot.LURE,
			RodModifiers.builder().nightBiteTicks(100).nightRarity(0.1f).build());
	public static final Item FAE_DUST_LURE = part("fae_dust_lure", PartSlot.LURE, RodModifiers.builder().rarity(0.35f).build());
	public static final Item FISH_FINDER = part("fish_finder", PartSlot.LURE, RodModifiers.builder().sonar().build());

	public static final List<Item> RODS = List.of(BAMBOO_ROD, IRON_ROD, STEEL_ROD, DURALUMIN_ROD, MAGMASTEEL_ROD, STAR_STEEL_ROD);
	public static final List<Item> PARTS = List.of(RUBBER_LINE, NOSE_HAIR_LINE, FAE_SILK_LINE, IRON_HOOK, SILVER_HOOK, TUNGSTEN_HOOK,
			COPPER_REEL, GEARED_REEL, SERVO_REEL, GLOW_LURE, FAE_DUST_LURE, FISH_FINDER);

	// --- Tackle Bench -------------------------------------------------------------------------------------------
	public static final Block TACKLE_BENCH = block("tackle_bench", TackleBenchBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
	public static final MenuType<TackleBenchMenu> TACKLE_BENCH_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("tackle_bench"),
			new MenuType<>(TackleBenchMenu::new, FeatureFlags.VANILLA_SET));

	// --- Sounds (vanilla sound files, see sounds.json) ----------------------------------------------------------
	public static final SoundEvent REEL_CLICK = sound("fishing_reel_click");
	public static final SoundEvent LINE_CREAK = sound("fishing_line_creak");
	public static final SoundEvent HOOKED = sound("fishing_hooked");
	public static final SoundEvent CATCH_SPLASH = sound("fishing_catch");
	public static final SoundEvent LINE_SNAP = sound("fishing_line_snap");
	public static final SoundEvent ESCAPE = sound("fishing_escape");
	public static final SoundEvent STRIKE_HIT = sound("fishing_strike_hit");
	public static final SoundEvent STRIKE_MISS = sound("fishing_strike_miss");
	public static final SoundEvent SONAR = sound("fishing_sonar");

	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	private Fishing() {
	}

	private static Item rod(String name, int tier, boolean lava, int durability, Item repair, RodModifiers modifiers,
			UnaryOperator<Item.Properties> extra) {
		return item(name, properties -> new RodItem(properties, tier, lava, modifiers),
				extra.apply(new Item.Properties().durability(durability).repairable(repair).enchantable(1)));
	}

	private static Item part(String name, PartSlot slot, RodModifiers modifiers) {
		return item(name, properties -> new RodPartItem(properties, slot, modifiers), new Item.Properties());
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		item(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FishingNetworking.initialize();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(FishingSpeciesLoader.ID, new FishingSpeciesLoader());
		ServerTickEvents.END_SERVER_TICK.register(FishingFights::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FishingFights.disconnect(handler.player));
		FishingCommands.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.FISHING_ROD, RODS.toArray(Item[]::new));
			output.insertAfter(STAR_STEEL_ROD, PARTS.toArray(Item[]::new));
		});
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS_TAB)
				.register(output -> output.insertAfter(Items.FLETCHING_TABLE, TACKLE_BENCH));
	}
}
