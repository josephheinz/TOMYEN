package com.jheinz.tomyen.banana;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import com.jheinz.tomyen.MyMod;

/**
 * Bananas: the Big Banana (see {@link BigBanana}), a three-block walking banana that wanders jungles by day; the Banana
 * it drops (and jungle leaves now and then let fall), an apple's worth of food that leaves its peel behind; and the
 * Banana Peel, which lies flat where it's put or thrown and makes whatever steps on it slip (see
 * {@link BananaPeelBlock#slip}). Peels rot down in dirt into earthworms, the fishing bait (a recipe). Their sounds are
 * vanilla's, pitched (see sounds.json).
 */
public final class Bananas {
	/** Rare: jungles' parrots are 40 and ocelots 2. */
	private static final int SPAWN_WEIGHT = 3;
	/** The chance a jungle leaves block drops a banana, by Fortune level (an oak's apple is 0.005 without it). */
	private static final float[] LEAF_BANANA_CHANCES = {0.01f, 0.0111f, 0.0125f, 0.0167f, 0.05f};

	public static final EntityType<BigBanana> BIG_BANANA = entity("big_banana", EntityType.Builder.of(BigBanana::new, MobCategory.CREATURE)
			.sized(0.95f, 3.0f)
			.eyeHeight(2.3f)
			.clientTrackingRange(10));
	public static final EntityType<ThrownBananaPeel> THROWN_BANANA_PEEL = entity("thrown_banana_peel",
			EntityType.Builder.<ThrownBananaPeel>of(ThrownBananaPeel::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.25f, 0.25f)
					.clientTrackingRange(4)
					.updateInterval(10));

	/** What a Big Banana is glad to see in a player's hand. */
	public static final TagKey<Item> MAKES_BIG_BANANA_HAPPY = TagKey.create(Registries.ITEM, MyMod.id("makes_big_banana_happy"));

	public static final Block BANANA_PEEL_BLOCK = block("banana_peel", BananaPeelBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW)
			.instabreak()
			.noCollision()
			.noOcclusion()
			.sound(SoundType.HONEY_BLOCK)
			.pushReaction(PushReaction.POPPED));
	public static final Item BANANA_PEEL = item("banana_peel", properties -> new BananaPeelItem(BANANA_PEEL_BLOCK, properties),
			new Item.Properties().stacksTo(16).compostable(ContextIntProviders.COMPOSTABLE_LOW_MEDIUM));
	/** An apple's worth of food; eating it leaves the peel. */
	public static final Item BANANA = item("banana", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build())
			.usingConvertsTo(BANANA_PEEL)
			.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	public static final Item BIG_BANANA_SPAWN_EGG = item("big_banana_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(BIG_BANANA));

	public static final SoundEvent AMBIENT = sound("big_banana_ambient");
	public static final SoundEvent HURT = sound("big_banana_hurt");
	public static final SoundEvent DEATH = sound("big_banana_death");
	public static final SoundEvent STEP = sound("big_banana_step");
	/** The wet slap of its arm landing. */
	public static final SoundEvent SLAP = sound("big_banana_slap");
	/** A goofy note-block boop as it bounces for joy. */
	public static final SoundEvent BOOP = sound("big_banana_boop");
	/** The skid of something slipping on a peel. */
	public static final SoundEvent PEEL_SLIP = sound("banana_peel_slip");
	public static final SoundEvent PEEL_PLACE = sound("banana_peel_place");

	private Bananas() {
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(BIG_BANANA, BigBanana.createAttributes());
		SpawnPlacements.register(BIG_BANANA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BigBanana::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE), MobCategory.CREATURE, BIG_BANANA,
				SPAWN_WEIGHT, 1, 2);
		DispenserBlock.registerProjectileBehavior(BANANA_PEEL);
		// Jungle leaves now and then let a banana fall, as oak leaves do apples (Fortune helps).
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (source.isBuiltin() && Blocks.JUNGLE_LEAVES.getLootTable().filter(key::equals).isPresent()) {
				table.withPool(LootPool.lootPool()
						.add(LootItem.lootTableItem(BANANA))
						.when(ExplosionCondition.survivesExplosion())
						.when(BonusLevelTableCondition.bonusLevelFlatChance(
								registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), LEAF_BANANA_CHANCES)));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.BEE_SPAWN_EGG, BIG_BANANA_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.insertAfter(Items.APPLE, BANANA));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
				.register(output -> output.insertAfter(Items.EGG, BANANA_PEEL));
	}
}
