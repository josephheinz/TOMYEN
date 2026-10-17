package com.jheinz.tomyen.gnome;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.tomyen.MyMod;

/**
 * The Gnome army, a fae nuisance of the Scavenger tier: garden gnomes that come alive at night round the woods and
 * meadows where Fae Rings grow (and in the Feywild), march on farms in formation behind a {@link GnomeCaptain}, and
 * steal the ripe crops (see {@link Gnome}, {@link CropRaids}). A scarecrow keeps them off a farm. At dawn they set into
 * ceramic statues where they stand, and a player can break one for a {@link GardenGnomeBlock}.
 *
 * <p>Only captains spawn naturally (weight {@value #SPAWN_WEIGHT}, alone; each musters its own squad), and only at
 * night.
 */
public final class Gnomes {
	private static final int SPAWN_WEIGHT = 4;

	/** Fae ground a captain makes its home: Fae Mushrooms and Fae Grass. */
	public static final TagKey<Block> HOMES = TagKey.create(Registries.BLOCK, MyMod.id("gnome_homes"));

	public static final EntityType<Gnome> GNOME = entity("gnome", EntityType.Builder.of(Gnome::new, MobCategory.MONSTER)
			.sized(0.45f, 0.9f)
			.eyeHeight(0.55f)
			.clientTrackingRange(8)
			.notInPeaceful());
	public static final EntityType<GnomeCaptain> GNOME_CAPTAIN = entity("gnome_captain",
			EntityType.Builder.of(GnomeCaptain::new, MobCategory.MONSTER)
					.sized(0.5f, 1.05f)
					.eyeHeight(0.6f)
					.clientTrackingRange(10)
					.notInPeaceful());

	public static final Block GARDEN_GNOME;
	public static final Item GARDEN_GNOME_ITEM;

	static {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id("garden_gnome"));
		GARDEN_GNOME = Registry.register(BuiltInRegistries.BLOCK, key, new GardenGnomeBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_RED).strength(0.8f).sound(SoundType.DECORATED_POT).noOcclusion().pushReaction(PushReaction.POPPED)
				.setId(key)));
		GARDEN_GNOME_ITEM = item("garden_gnome", properties -> new BlockItem(GARDEN_GNOME, properties),
				new Item.Properties().useBlockDescriptionPrefix());
	}

	public static final Item GNOME_SPAWN_EGG = item("gnome_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(GNOME));
	public static final Item GNOME_CAPTAIN_SPAWN_EGG = item("gnome_captain_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(GNOME_CAPTAIN));

	// --- Sounds (vanilla ones, re-pitched: see sounds.json) ----------------------------------------------------
	public static final SoundEvent AMBIENT = sound("gnome_ambient");
	public static final SoundEvent HURT = sound("gnome_hurt");
	public static final SoundEvent DEATH = sound("gnome_death");
	public static final SoundEvent GIGGLE = sound("gnome_giggle");
	public static final SoundEvent SCATTER = sound("gnome_scatter");
	public static final SoundEvent POKE = sound("gnome_poke");
	public static final SoundEvent STEP = sound("gnome_step");
	public static final SoundEvent FREEZE = sound("gnome_freeze");
	public static final SoundEvent THAW = sound("gnome_thaw");
	public static final SoundEvent SHATTER = sound("gnome_shatter");
	public static final SoundEvent CAPTAIN_ORDER = sound("gnome_captain_order");
	public static final SoundEvent CAPTAIN_DRUM = sound("gnome_captain_drum");
	public static final SoundEvent ROUT = sound("gnome_rout");

	private Gnomes() {
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(GNOME, Gnome.createAttributes());
		FabricDefaultAttributeRegistry.register(GNOME_CAPTAIN, GnomeCaptain.createAttributes());
		SpawnPlacements.register(GNOME_CAPTAIN, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				GnomeCaptain::checkSpawnRules);
		// The woods and meadows where Fae Rings grow, and the Feywild itself.
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.DARK_FOREST, Biomes.MEADOW,
				ResourceKey.create(Registries.BIOME, MyMod.id("feywild_glade")),
				ResourceKey.create(Registries.BIOME, MyMod.id("feywild_mushroom_wilds"))),
				MobCategory.MONSTER, GNOME_CAPTAIN, SPAWN_WEIGHT, 1, 1);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.ALLAY_SPAWN_EGG, GNOME_SPAWN_EGG, GNOME_CAPTAIN_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.DECORATED_POT, GARDEN_GNOME_ITEM));
	}
}
