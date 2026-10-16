package com.jheinz.tomyen.herbalism;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import com.jheinz.tomyen.ModEffects;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.fishing.BaitItem;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.survival.Survival;
import com.jheinz.tomyen.vampire.Vampire;

/**
 * Herbalism (Scavenger tier): wild herbs in the vanilla biomes (and one in the Feywild), and what's made of them.
 *
 * <p>The herbs, where they grow, and what they're for:
 * <ul>
 * <li><b>Garlic</b> (plains and forests, as Wild Garlic; then a farmland crop): eaten raw it gives Garlic Breath, which
 * no vampire will bite through; braided, it's a Garlic Braid, a ward vampires keep their distance from
 * ({@link HerbalWards}). Also roasted, and baked into garlic bread and herb stew.</li>
 * <li><b>Wolfsbane</b> (taigas, rare): the Wolfsbane Ward keeps cursed villagers nearby from changing on a full moon;
 * ground into paste it coats blades and tips arrows that bite werewolves through their hide ({@link Wolfsbane}).</li>
 * <li><b>Yarrow</b> (meadows and plains): the staunching herb. A Yarrow Poultice stops Bleeding; it goes into
 * bandages and first aid kits too (alternative recipes).</li>
 * <li><b>Radroot</b> (badlands): brews Anti-Radiation Tea, which draws off a Radiation Sickness dose and halves new
 * ones for a while ({@link HerbalRemedies}).</li>
 * <li><b>Chamomile</b> (flower forests): Calming Tea, a little regeneration and Calm, which keeps nausea and a
 * Cathode Walker's Static Burst off.</li>
 * <li><b>Ginseng</b> (dark forests, rare): Vigor Tonic, haste and speed.</li>
 * <li><b>Moonpetal</b> (the Feywild): Moonpetal Tea for night vision, and ground, a bait the rare night fish rise to.</li>
 * <li><b>Water Mint</b> (swamps): ground into Mint Bait for the bottom feeders.</li>
 * </ul>
 * Every herb spreads with bone meal and can be planted again. The tools: the {@link MortarBlock Mortar &amp; Pestle}
 * grinds herbs into pastes, poultices and baits, and the {@link KettleBlock Kettle}, hung over a campfire, brews teas.
 */
public final class Herbalism {
	// --- Effects -------------------------------------------------------------------------------------------------------
	public static final Holder<MobEffect> GARLIC_BREATH = effect("garlic_breath", 0xE8E0C0);
	public static final Holder<MobEffect> RADIOPROTECTION = effect("radioprotection", 0x8DA34E);
	public static final Holder<MobEffect> CALM = effect("calm", 0xF3E39A);

	public static final ConsumeEffect.Type<HerbalRemedies.EaseRadiation> EASE_RADIATION = Registry.register(
			BuiltInRegistries.CONSUME_EFFECT_TYPE, MyMod.id("ease_radiation"),
			new ConsumeEffect.Type<>(HerbalRemedies.EaseRadiation.CODEC, HerbalRemedies.EaseRadiation.STREAM_CODEC));

	/** Hits of wolfsbane left on a coated blade. */
	public static final DataComponentType<Integer> WOLFSBANE_COATING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("wolfsbane_coating"), DataComponentType.<Integer>builder().persistent(Codec.intRange(1, Wolfsbane.MAX_COATING))
					.networkSynchronized(ByteBufCodecs.VAR_INT).build());

	// --- Wild herbs ----------------------------------------------------------------------------------------------------
	private static final Predicate<BlockState> VEGETATION_SOIL = state -> state.is(BlockTags.SUPPORTS_VEGETATION);
	private static final Predicate<BlockState> DRY_SOIL = state -> state.is(BlockTags.SUPPORTS_DRY_VEGETATION);

	/** Ramsons: drops garlic bulbs to plant out on farmland. */
	public static final Block WILD_GARLIC = herb("wild_garlic", MobEffects.SATURATION, 0.35f, VEGETATION_SOIL, herbProperties());
	public static final Block WOLFSBANE = herb("wolfsbane", MobEffects.POISON, 8.0f, VEGETATION_SOIL, herbProperties());
	public static final Block YARROW = herb("yarrow", MobEffects.REGENERATION, 6.0f, VEGETATION_SOIL, herbProperties());
	public static final Block RADROOT = herb("radroot", MobEffects.RESISTANCE, 4.0f, DRY_SOIL, herbProperties());
	public static final Block CHAMOMILE = herb("chamomile", MobEffects.SATURATION, 0.35f, VEGETATION_SOIL, herbProperties());
	public static final Block GINSENG = herb("ginseng", MobEffects.HASTE, 8.0f, VEGETATION_SOIL, herbProperties());
	public static final Block MOONPETAL = registerBlock("moonpetal",
			properties -> new HerbBlock(MobEffects.NIGHT_VISION, 5.0f, VEGETATION_SOIL, Fae.SPARKLE, properties),
			herbProperties().lightLevel(state -> 5));
	public static final Block WATER_MINT = herb("water_mint", MobEffects.WATER_BREATHING, 6.0f, VEGETATION_SOIL, herbProperties());
	public static final List<Block> HERBS = List.of(WILD_GARLIC, WOLFSBANE, YARROW, RADROOT, CHAMOMILE, GINSENG, MOONPETAL, WATER_MINT);

	/** Garlic on farmland (its item is the garlic bulb). */
	public static final Block GARLIC_CROP = blockOnly("garlic_crop", GarlicCropBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));

	// --- Tools and wards -----------------------------------------------------------------------------------------------
	public static final Block KETTLE = registerBlock("kettle", KettleBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).strength(1.5f, 4.0f).sound(SoundType.COPPER).noOcclusion().pushReaction(PushReaction.POPPED));
	public static final BlockEntityType<KettleBlockEntity> KETTLE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("kettle"), new BlockEntityType<>(KettleBlockEntity::new, Set.of(KETTLE)));
	public static final Block MORTAR_AND_PESTLE = registerBlock("mortar_and_pestle", MortarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(1.0f, 3.0f).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.POPPED));
	public static final Block GARLIC_BRAID = registerBlock("garlic_braid",
			properties -> new WardBlock(properties, 8.0, 1.0, 15.0, 4.0, null), wardProperties(MapColor.TERRACOTTA_WHITE));
	public static final Block WOLFSBANE_WARD = registerBlock("wolfsbane_ward",
			properties -> new WardBlock(properties, 14.0, 1.0, 15.0, 3.0, new DustParticleOptions(0x8A5FC8, 0.7f)),
			wardProperties(MapColor.COLOR_PURPLE));

	public static final ResourceKey<PoiType> GARLIC_BRAID_POI = poi("garlic_braid", GARLIC_BRAID);
	public static final ResourceKey<PoiType> WOLFSBANE_WARD_POI = poi("wolfsbane_ward", WOLFSBANE_WARD);

	// --- Items ---------------------------------------------------------------------------------------------------------
	/** Raw garlic: plant it on farmland, or eat it for a brief Garlic Breath. */
	public static final Item GARLIC = item("garlic", properties -> new HerbalItem.Placeable(GARLIC_CROP, properties),
			new Item.Properties().useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM)
					.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.3f).build(),
							Consumables.defaultFood().onConsume(applies(new MobEffectInstance(GARLIC_BREATH, 45 * 20))).build()));
	public static final Item ROASTED_GARLIC = item("roasted_garlic", HerbalItem::new, new Item.Properties()
			.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH)
			.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.6f).build()));
	public static final Item GARLIC_BREAD = item("garlic_bread", HerbalItem::new, new Item.Properties()
			.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH)
			.food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.7f).build(),
					Consumables.defaultFood().onConsume(applies(new MobEffectInstance(GARLIC_BREATH, 30 * 20))).build()));
	public static final Item HERB_STEW = item("herb_stew", HerbalItem::new, new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL)
			.food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.8f).build(),
					Consumables.defaultFood().onConsume(applies(new MobEffectInstance(MobEffects.REGENERATION, 6 * 20))).build()));

	/** Pressed onto a wound: stops Bleeding. */
	public static final Item YARROW_POULTICE = item("yarrow_poultice", HerbalItem::new, new Item.Properties().stacksTo(16)
			.component(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(1.5f)
					.animation(ItemUseAnimation.BRUSH).sound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(Survival.BANDAGE_WRAP))
					.hasConsumeParticles(false)
					.onConsume(new RemoveStatusEffectsConsumeEffect(ModEffects.BLEEDING))
					.onConsume(applies(new MobEffectInstance(MobEffects.REGENERATION, 3 * 20))).build()));
	public static final Item WOLFSBANE_PASTE = item("wolfsbane_paste", WolfsbanePasteItem::new, new Item.Properties().stacksTo(16));
	public static final Item WOLFSBANE_ARROW = item("wolfsbane_arrow", ArrowItem::new, new Item.Properties());

	/** Crushed water mint: the bottom feeders follow the smell, and it carries in murky, rainy water. */
	public static final Item MINT_BAIT = item("mint_bait", properties -> new BaitItem(properties, new BaitItem.Effect(
			RodModifiers.builder().biteTicks(40).rainBiteTicks(60).build(), 0.08f, Fishing.BOTTOM_FISH, 2.5f, 0.1f, false, 0)),
			new Item.Properties());
	/** Ground moonpetal, faintly glowing: the rare fish of the night come up for it. */
	public static final Item MOONPETAL_BAIT = item("moonpetal_bait", properties -> new BaitItem(properties, new BaitItem.Effect(
			RodModifiers.builder().nightBiteTicks(60).rarity(0.05f).nightRarity(0.15f).build(), 0.15f, Fishing.NIGHT_FISH, 2.0f, 0.15f,
			false, 0)), new Item.Properties());

	public static final Item ANTI_RADIATION_TEA = tea("anti_radiation_tea",
			new HerbalRemedies.EaseRadiation(4 * 60 * 20),
			applies(new MobEffectInstance(RADIOPROTECTION, 2 * 60 * 20)));
	public static final Item CALMING_TEA = tea("calming_tea",
			new RemoveStatusEffectsConsumeEffect(MobEffects.NAUSEA),
			applies(new MobEffectInstance(CALM, 3 * 60 * 20), new MobEffectInstance(MobEffects.REGENERATION, 5 * 20)));
	public static final Item VIGOR_TONIC = tea("vigor_tonic",
			new RemoveStatusEffectsConsumeEffect(MobEffects.WEAKNESS),
			applies(new MobEffectInstance(MobEffects.HASTE, 3 * 60 * 20), new MobEffectInstance(MobEffects.SPEED, 3 * 60 * 20)));
	public static final Item MOONPETAL_TEA = tea("moonpetal_tea",
			applies(new MobEffectInstance(MobEffects.NIGHT_VISION, 5 * 60 * 20)));

	// --- Worldgen ------------------------------------------------------------------------------------------------------
	private static final ResourceKey<Biome> FEYWILD_GLADE = ResourceKey.create(Registries.BIOME, MyMod.id("feywild_glade"));
	private static final ResourceKey<Biome> FEYWILD_MUSHROOM_WILDS = ResourceKey.create(Registries.BIOME, MyMod.id("feywild_mushroom_wilds"));

	private Herbalism() {
	}

	// --- Registration helpers ------------------------------------------------------------------------------------------

	private static Holder<MobEffect> effect(String name, int color) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id(name), new HerbalEffect(color));
	}

	private static BlockBehaviour.Properties herbProperties() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS)
				.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED);
	}

	private static BlockBehaviour.Properties wardProperties(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.3f).sound(SoundType.GRASS).noCollision().noOcclusion()
				.pushReaction(PushReaction.POPPED);
	}

	private static Block herb(String name, Holder<MobEffect> stewEffect, float stewSeconds, Predicate<BlockState> soil,
			BlockBehaviour.Properties properties) {
		return registerBlock(name, p -> new HerbBlock(stewEffect, stewSeconds, soil, null, p), properties);
	}

	private static Block blockOnly(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = blockOnly(name, factory, properties);
		Item.Properties itemProperties = new Item.Properties().useBlockDescriptionPrefix();
		if (block instanceof HerbBlock) {
			itemProperties.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM);
		}
		item(name, p -> new HerbalItem.Placeable(block, p), itemProperties);
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static ApplyStatusEffectsConsumeEffect applies(MobEffectInstance... effects) {
		return new ApplyStatusEffectsConsumeEffect(List.of(effects));
	}

	/** A tea: drunk from a glass bottle (which comes back), with these effects. */
	private static Item tea(String name, ConsumeEffect... effects) {
		Consumable.Builder drink = Consumables.defaultDrink();
		for (ConsumeEffect effect : effects) {
			drink.onConsume(effect);
		}
		return item(name, HerbalItem::new, new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
				.component(DataComponents.CONSUMABLE, drink.build()));
	}

	private static ResourceKey<PoiType> poi(String name, Block block) {
		PoiHelper.register(MyMod.id(name), 0, 1, block);
		return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MyMod.id(name));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, MyMod.id(name));
	}

	@SafeVarargs
	private static void growIn(String patch, ResourceKey<Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Decoration.VEGETAL_DECORATION, placed(patch));
	}

	// --- Setup ---------------------------------------------------------------------------------------------------------

	public static void initialize() {
		growIn("wild_garlic_patch", Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST);
		growIn("wolfsbane_patch", Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
				Biomes.GROVE);
		growIn("yarrow_patch", Biomes.MEADOW, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS);
		growIn("radroot_patch", Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS);
		growIn("chamomile_patch", Biomes.FLOWER_FOREST, Biomes.MEADOW);
		growIn("ginseng_patch", Biomes.DARK_FOREST);
		growIn("moonpetal_patch", FEYWILD_GLADE, FEYWILD_MUSHROOM_WILDS);
		growIn("water_mint_patch", Biomes.SWAMP, Biomes.MANGROVE_SWAMP);

		// Calm: nausea can't take hold.
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, context) -> !(effect.is(MobEffects.NAUSEA) && entity.hasEffect(CALM)));
		// Garlic Breath: no vampire bites through it.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.getDirectEntity() instanceof Vampire && entity.hasEffect(GARLIC_BREATH)));
		ItemComponentTooltipProviderRegistry.addLast(WOLFSBANE_COATING, Wolfsbane::tooltip);
		DispenserBlock.registerProjectileBehavior(WOLFSBANE_ARROW);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.insertAfter(Items.LILY_OF_THE_VALLEY, WILD_GARLIC, WOLFSBANE, YARROW, CHAMOMILE, RADROOT, GINSENG, WATER_MINT, MOONPETAL);
			output.insertAfter(Items.BEETROOT_SEEDS, GARLIC);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.insertAfter(Items.BAKED_POTATO, GARLIC, ROASTED_GARLIC, GARLIC_BREAD);
			output.insertAfter(Items.RABBIT_STEW, HERB_STEW);
			output.insertAfter(Items.HONEY_BOTTLE, ANTI_RADIATION_TEA, CALMING_TEA, VIGOR_TONIC, MOONPETAL_TEA, YARROW_POULTICE);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
				output.insertAfter(Items.CAULDRON, KETTLE, MORTAR_AND_PESTLE, GARLIC_BRAID, WOLFSBANE_WARD));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output ->
				output.insertAfter(Items.FERMENTED_SPIDER_EYE, WOLFSBANE_PASTE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output ->
				output.insertAfter(Items.SPECTRAL_ARROW, WOLFSBANE_ARROW));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
				output.insertAfter(Fishing.STAR_BAIT, MINT_BAIT, MOONPETAL_BAIT));
	}

	/** Whether this player is Calm (a Cathode Walker's Static Burst doesn't get through). */
	public static boolean isCalm(LivingEntity entity) {
		return entity.hasEffect(CALM);
	}
}
