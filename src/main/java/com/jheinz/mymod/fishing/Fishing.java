package com.jheinz.mymod.fishing;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.fae.Fae;
import com.jheinz.mymod.froststeel.Froststeel;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.magmasteel.Magmasteel;
import com.jheinz.mymod.meteorite.Meteorites;

/**
 * Fishing: eleven mod rods with a quick-time minigame when a fish bites (see {@link RodItem}, {@link RodHook},
 * {@link FishingFights} and client/fishing), the parts that customize them at the Tackle Bench, and the species data
 * files every catch is rolled from ({@link FishingSpeciesLoader}). Vanilla's fishing rod is left exactly as it is.
 *
 * <p>Rods, by tier: Bamboo (0, Scavenger), Iron (1), Steel (2, Foundry), Duralumin (3, light and quick to bite),
 * Magmasteel (3, fishes lava) and Star Steel (4, Exotic). Beside them, rods with other trade-offs at the same tiers: the
 * Stick &amp; String Pole (0: forgiving but slow, and no reel to fit), Copper (1: quick bites in rain or swamp water),
 * Bone (1: a night rod), Fae (2: fast, lucky bites in the Feywild) and Froststeel (3: sluggish fish in the cold). A
 * rod's own bonus for a place ({@link RodCondition}) shows on its tooltip and as a tag at the Tackle Bench.
 *
 * <p>Every fish landed on a mod rod is weighed ({@link FishSize}): ordinary catches are announced and stack as usual,
 * trophies ({@link TrophyFish}) keep their weight and catcher and go up on a {@link FishMountBlock}, sized by weight.
 * Each player's heaviest of every species is kept ({@link FishingRecords}). Bait ({@link BaitItem}), loaded at the
 * Tackle Bench, leans the weight roll heavier, draws certain species in or quickens the bite.
 */
public final class Fishing {
	/** On a mod rod: the parts fitted at the Tackle Bench. */
	public static final DataComponentType<RodParts> ROD_PARTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("rod_parts"), DataComponentType.<RodParts>builder().persistent(RodParts.CODEC).networkSynchronized(RodParts.STREAM_CODEC).build());

	/** On a mod rod: the bait loaded at the Tackle Bench and how much is left. */
	public static final DataComponentType<RodBait> ROD_BAIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("rod_bait"), DataComponentType.<RodBait>builder().persistent(RodBait.CODEC).networkSynchronized(RodBait.STREAM_CODEC).build());
	/** On a trophy fish: its weight, catcher and day (ordinary catches carry nothing, so they stack). */
	public static final DataComponentType<TrophyFish> TROPHY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("trophy"), DataComponentType.<TrophyFish>builder().persistent(TrophyFish.CODEC).networkSynchronized(TrophyFish.STREAM_CODEC)
					.build());

	/** Catches a silver hook makes easier: cursed and evil things. Empty unless a species' item is tagged. */
	public static final TagKey<Item> CURSED_CATCHES = TagKey.create(Registries.ITEM, MyMod.id("cursed_catches"));
	/** Fish that hunt fish: live minnows and cut bait draw them in. */
	public static final TagKey<Item> PREDATOR_FISH = TagKey.create(Registries.ITEM, MyMod.id("predator_fish"));
	/** Fish of the dark: glowworms draw them in. */
	public static final TagKey<Item> NIGHT_FISH = TagKey.create(Registries.ITEM, MyMod.id("night_fish"));
	/** What irradiated bait draws in. */
	public static final TagKey<Item> MUTANT_FISH = TagKey.create(Registries.ITEM, MyMod.id("mutant_fish"));
	/** Raw fish that cut into bait. */
	public static final TagKey<Item> RAW_FISH = TagKey.create(Registries.ITEM, MyMod.id("raw_fish"));
	/** Small fish of ponds and creeks: crickets draw them in. */
	public static final TagKey<Item> PANFISH = TagKey.create(Registries.ITEM, MyMod.id("panfish"));
	/** Fish that feed on the bottom: dough balls draw them in. */
	public static final TagKey<Item> BOTTOM_FISH = TagKey.create(Registries.ITEM, MyMod.id("bottom_fish"));

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
	// Sideways rods: the same tiers, different trade-offs.
	/** Two sticks and some string, the first rod anyone makes: forgiving, but slow to bite, slow to reel, and no reel to fit. */
	public static final Item STICK_POLE = rod("stick_pole", 0, false, 48, Items.STICK,
			RodModifiers.builder().biteTicks(-40).zone(0.15f).reel(-0.25f).build(), false, RodCondition.NONE, RodModifiers.NONE,
			properties -> properties);
	/** Copper: fish bite far sooner on it in the rain (or in murky swamp water). */
	public static final Item COPPER_ROD = rod("copper_rod", 1, false, 160, Items.COPPER_INGOT,
			RodModifiers.builder().rainBiteTicks(120).line(0.05f).build(), properties -> properties);
	/** Bone: a night rod. Bites sooner and rarer after dark, but brittle (a narrower zone). */
	public static final Item BONE_ROD = rod("bone_rod", 1, false, 112, Items.BONE,
			RodModifiers.builder().nightBiteTicks(100).nightRarity(0.12f).zone(-0.05f).build(), properties -> properties);
	/** Fae silver: light and lucky; in the Feywild the fae fish come to it fast and rare. */
	public static final Item FAE_ROD = rod("fae_rod", 2, false, 240, Fae.FAE_SILVER_INGOT, RodModifiers.builder().zone(0.1f).build(),
			true, RodCondition.FEYWILD, RodModifiers.builder().biteTicks(80).rarity(0.25f).build(),
			properties -> properties.rarity(Rarity.UNCOMMON));
	/** Froststeel: in the cold, every fish on its line moves sluggishly. */
	public static final Item FROSTSTEEL_ROD = rod("froststeel_rod", 3, false, 384, Froststeel.SET.ingot,
			RodModifiers.builder().speed(-0.05f).line(0.1f).build(), true, RodCondition.COLD,
			RodModifiers.builder().speed(-0.25f).zone(0.05f).build(), properties -> properties);

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
	// Early parts, from string, nuggets and feathers.
	/** Three strings braided together: stronger, and you feel the bite. */
	public static final Item BRAIDED_LINE = part("braided_line", PartSlot.LINE, RodModifiers.builder().line(0.2f).zone(0.05f).build());
	/** Three points: it holds, but the fish fights it harder. */
	public static final Item TREBLE_HOOK = part("treble_hook", PartSlot.HOOK, RodModifiers.builder().escape(0.3f).speed(0.05f).build());
	/** A leather drag lets a running fish take line instead of breaking it, at the cost of reeling speed. */
	public static final Item DRAG_REEL = part("drag_reel", PartSlot.REEL, RodModifiers.builder().line(0.25f).reel(-0.1f).build());
	/** A bark bobber: easy to see go under. */
	public static final Item CORK_BOBBER = part("cork_bobber", PartSlot.LURE, RodModifiers.builder().zone(0.08f).biteTicks(20).build());
	/** A tied feather: bites a little sooner, and now and then something unusual. */
	public static final Item FEATHER_LURE = part("feather_lure", PartSlot.LURE, RodModifiers.builder().biteTicks(40).rarity(0.05f).build());
	/** A flashing blade the predators can't leave alone. */
	public static final Item SPINNER_LURE = part("spinner_lure", PartSlot.LURE, RodModifiers.builder().predator(1.0f).biteTicks(20).build());
	/** Seeds rattling in a copper shell: fish find it by sound when they can't see it, in rain or murky water. */
	public static final Item RATTLE_LURE = part("rattle_lure", PartSlot.LURE, RodModifiers.builder().rainBiteTicks(160).predator(0.25f).build());

	// --- Bait (the rod's bait slot; one eaten per fish landed) -------------------------------------------------------
	/** Dug from grass and dirt now and then, or bred in a heap of dirt and rotten flesh. The basic bait. */
	public static final Item EARTHWORMS = bait("earthworms", new BaitItem.Effect(RodModifiers.builder().biteTicks(40).build(),
			0.10f, null, 1.0f, 0.0f, false, 0));
	/** Any raw fish, cut up: heavier fish, and the predators smell it. */
	public static final Item CUT_BAIT = bait("cut_bait", new BaitItem.Effect(RodModifiers.builder().biteTicks(20).build(),
			0.22f, PREDATOR_FISH, 1.5f, 0.0f, false, 0));
	/** A bucket of minced cut bait and bone meal ladled out a scoop at a time: everything bites, fast. */
	public static final Item CHUM_BUCKET = bait("chum_bucket", new BaitItem.Effect(RodModifiers.builder().biteTicks(160).build(),
			0.05f, null, 1.0f, 0.0f, false, 0));
	/** An earthworm fed on glow berries: glows on the hook, and the fish of the dark come to it. */
	public static final Item GLOWWORM = bait("glowworm", new BaitItem.Effect(RodModifiers.builder().nightBiteTicks(100).nightRarity(0.1f)
			.build(), 0.12f, NIGHT_FISH, 2.5f, 0.0f, false, 0));
	/** A little fish on the hook: predators go for it, and the big ones do. */
	public static final Item LIVE_MINNOW = bait("live_minnow", new BaitItem.Effect(RodModifiers.builder().biteTicks(20).build(),
			0.12f, PREDATOR_FISH, 2.5f, 0.45f, false, 0));
	/** A grub that lives in magma cream: only good in lava, where it's very good. */
	public static final Item MAGMA_GRUB = bait("magma_grub", new BaitItem.Effect(RodModifiers.builder().biteTicks(100).build(),
			0.35f, null, 1.0f, 0.0f, true, 0));
	/** Bait laced with uranium: the biggest fish (and mutants) can't resist it, and it doses whoever handles it. */
	public static final Item IRRADIATED_BAIT = bait("irradiated_bait", new BaitItem.Effect(RodModifiers.NONE,
			0.6f, MUTANT_FISH, 5.0f, 0.0f, false, 200));
	/** Cut bait dusted with ground star crystal: the best there is for trophies. */
	public static final Item STAR_BAIT = bait("star_bait", new BaitItem.Effect(RodModifiers.builder().rarity(0.1f).build(),
			0.85f, null, 1.0f, 0.0f, false, 0), Rarity.UNCOMMON);

	/** Caught in the grass now and then: panfish (bluegill, crappie, perch, trout) can't resist them. */
	public static final Item CRICKETS = bait("crickets", new BaitItem.Effect(RodModifiers.builder().biteTicks(30).build(),
			0.08f, PANFISH, 2.5f, 0.0f, false, 0));
	/** A loaf rolled into balls: the bottom feeders (catfish, carp, sturgeon, flounder) come for it. */
	public static final Item DOUGH_BALLS = bait("dough_balls", new BaitItem.Effect(RodModifiers.builder().biteTicks(20).build(),
			0.10f, BOTTOM_FISH, 2.5f, 0.15f, false, 0));
	/** Wheat seeds and bone meal: everything nibbles, fast, but it's the small fish that get there first. */
	public static final Item SEED_MASH = bait("seed_mash", new BaitItem.Effect(RodModifiers.builder().biteTicks(80).build(),
			-0.10f, null, 1.0f, 0.0f, false, 0));
	/** Dug from swamp mud: predators love them, and more so at night. */
	public static final Item LEECHES = bait("leeches", new BaitItem.Effect(RodModifiers.builder().nightBiteTicks(60).build(),
			0.15f, PREDATOR_FISH, 2.0f, 0.1f, false, 0));

	public static final List<Item> BAITS = List.of(EARTHWORMS, CRICKETS, SEED_MASH, DOUGH_BALLS, LEECHES, CUT_BAIT, CHUM_BUCKET, GLOWWORM,
			LIVE_MINNOW, MAGMA_GRUB, IRRADIATED_BAIT, STAR_BAIT);

	public static final List<Item> RODS = List.of(STICK_POLE, BAMBOO_ROD, COPPER_ROD, BONE_ROD, IRON_ROD, FAE_ROD, STEEL_ROD, DURALUMIN_ROD,
			FROSTSTEEL_ROD, MAGMASTEEL_ROD, STAR_STEEL_ROD);
	public static final List<Item> PARTS = List.of(BRAIDED_LINE, RUBBER_LINE, NOSE_HAIR_LINE, FAE_SILK_LINE, IRON_HOOK, TREBLE_HOOK,
			SILVER_HOOK, TUNGSTEN_HOOK, COPPER_REEL, DRAG_REEL, GEARED_REEL, SERVO_REEL, CORK_BOBBER, FEATHER_LURE, SPINNER_LURE, RATTLE_LURE,
			GLOW_LURE, FAE_DUST_LURE, FISH_FINDER);

	// --- Tackle Bench -------------------------------------------------------------------------------------------
	public static final Block TACKLE_BENCH = block("tackle_bench", TackleBenchBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
	// --- Fish Mount (a trophy plaque for the wall) -----------------------------------------------------------------
	public static final Block OAK_FISH_MOUNT = fishMount("oak_fish_mount", MapColor.WOOD);
	public static final Block SPRUCE_FISH_MOUNT = fishMount("spruce_fish_mount", MapColor.PODZOL);
	public static final Block DARK_OAK_FISH_MOUNT = fishMount("dark_oak_fish_mount", MapColor.COLOR_BROWN);
	public static final List<Block> FISH_MOUNTS = List.of(OAK_FISH_MOUNT, SPRUCE_FISH_MOUNT, DARK_OAK_FISH_MOUNT);
	public static final BlockEntityType<FishMountBlockEntity> FISH_MOUNT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("fish_mount"), new BlockEntityType<>(FishMountBlockEntity::new, Set.copyOf(FISH_MOUNTS)));

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

	private static final float WORM_CHANCE = 0.06f;
	private static final float CRICKET_CHANCE = 0.05f;

	private Fishing() {
	}

	private static Item rod(String name, int tier, boolean lava, int durability, Item repair, RodModifiers modifiers,
			UnaryOperator<Item.Properties> extra) {
		return rod(name, tier, lava, durability, repair, modifiers, true, RodCondition.NONE, RodModifiers.NONE, extra);
	}

	private static Item rod(String name, int tier, boolean lava, int durability, Item repair, RodModifiers modifiers, boolean reel,
			RodCondition condition, RodModifiers bonus, UnaryOperator<Item.Properties> extra) {
		return item(name, properties -> new RodItem(properties, tier, lava, modifiers, reel, condition, bonus),
				extra.apply(new Item.Properties().durability(durability).repairable(repair).enchantable(1)));
	}

	private static Item part(String name, PartSlot slot, RodModifiers modifiers) {
		return item(name, properties -> new RodPartItem(properties, slot, modifiers), new Item.Properties());
	}

	private static Item bait(String name, BaitItem.Effect effect) {
		return bait(name, effect, Rarity.COMMON);
	}

	private static Item bait(String name, BaitItem.Effect effect, Rarity rarity) {
		return item(name, properties -> new BaitItem(properties, effect), new Item.Properties().rarity(rarity));
	}

	private static Block fishMount(String name, MapColor color) {
		return block(name, FishMountBlock::new, BlockBehaviour.Properties.of().mapColor(color).strength(1.0f).sound(SoundType.WOOD)
				.noOcclusion().noCollision().ignitedByLava());
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
		FishingRecords.initialize();
		ItemComponentTooltipProviderRegistry.addLast(TROPHY);
		PlayerBlockBreakEvents.AFTER.register(Fishing::digWorms);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.FISHING_ROD, RODS.toArray(Item[]::new));
			output.insertAfter(RODS.getLast(), PARTS.toArray(Item[]::new));
			output.insertAfter(PARTS.getLast(), BAITS.toArray(Item[]::new));
		});
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS_TAB).register(output -> {
			output.insertAfter(Items.FLETCHING_TABLE, TACKLE_BENCH);
			output.insertAfter(Items.GLOW_ITEM_FRAME, FISH_MOUNTS.stream().map(Block::asItem).toArray(Item[]::new));
		});
	}

	/**
	 * Digging turns up live bait now and then: earthworms from grass blocks and dirt (leeches instead, from a swamp's
	 * dirt and mud), and crickets from long grass and ferns.
	 */
	private static void digWorms(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (level.isClientSide() || player.isCreative()) {
			return;
		}
		if (state.is(BlockTags.DIRT) && level.getRandom().nextFloat() < WORM_CHANCE) {
			boolean swamp = RodCondition.murky(level, pos);
			Block.popResource(level, pos, new ItemStack(swamp ? LEECHES : EARTHWORMS, 1 + level.getRandom().nextInt(2)));
		} else if ((state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN))
				&& level.getRandom().nextFloat() < CRICKET_CHANCE) {
			Block.popResource(level, pos, new ItemStack(CRICKETS, 1 + level.getRandom().nextInt(2)));
		}
	}
}
