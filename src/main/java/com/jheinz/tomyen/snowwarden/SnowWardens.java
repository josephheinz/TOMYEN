package com.jheinz.tomyen.snowwarden;

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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.metal.HintItem;

/**
 * The Snow Warden (Foundry tier): a hulking warden of packed snow and ice with a snow golem's carved pumpkin for a head,
 * met rarely out on the snowy plains and the ice spikes (see {@link SnowWarden} for how it fights and when it spawns).
 * Its entity, its thrown snowballs ({@link FrostBoulder}), the Frozen Heart it always leaves (the way to Glacial
 * Froststeel armor, and the heart of a Snow Globe), the Snow Globe itself, and its sounds (vanilla warden and snow golem
 * sounds, pitched down: see tools/gen_snow_warden.py).
 *
 * <p>Weight {@link #SNOWY_PLAINS_WEIGHT} on the snowy plains and {@link #ICE_SPIKES_WEIGHT} in the ice spikes, among
 * the zombies and skeletons (zombies are 95), alone; and then only one natural spawn in {@link SnowWarden#SPAWN_CHANCE}
 * that gets that far goes ahead.
 */
public final class SnowWardens {
	private static final int SNOWY_PLAINS_WEIGHT = 2;
	private static final int ICE_SPIKES_WEIGHT = 1;

	public static final ResourceKey<DamageType> FROST_BLAST_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("frost_blast"));

	public static final EntityType<SnowWarden> SNOW_WARDEN = entity("snow_warden", EntityType.Builder.of(SnowWarden::new, MobCategory.MONSTER)
			.sized(1.4f, 3.0f)
			.eyeHeight(2.55f)
			.clientTrackingRange(10)
			.notInPeaceful());
	public static final EntityType<FrostBoulder> FROST_BOULDER = entity("frost_boulder",
			EntityType.Builder.<FrostBoulder>of(FrostBoulder::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.75f, 0.75f)
					.clientTrackingRange(8)
					.updateInterval(5));

	/** A heart of ice that never melts: it hardens froststeel armor into glacial at a smithing table. */
	public static final Item FROZEN_HEART = item("frozen_heart", properties -> new HintItem(properties, java.util.List.of("item.tomyen.frozen_heart.hint")),
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	/** A little snowy scene under glass, with the Frozen Heart at its centre. Something stirs inside. */
	public static final Item SNOW_GLOBE = item("snow_globe", SnowGlobeItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item SNOW_WARDEN_SPAWN_EGG = item("snow_warden_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(SNOW_WARDEN));

	// --- Sounds (vanilla ones, re-pitched: see sounds.json) ----------------------------------------------------
	public static final SoundEvent AMBIENT = sound("snow_warden_ambient");
	public static final SoundEvent HURT = sound("snow_warden_hurt");
	public static final SoundEvent DEATH = sound("snow_warden_death");
	public static final SoundEvent STEP = sound("snow_warden_step");
	public static final SoundEvent SNIFF = sound("snow_warden_sniff");
	public static final SoundEvent ROAR = sound("snow_warden_roar");
	public static final SoundEvent SWING = sound("snow_warden_swing");
	public static final SoundEvent SLAM = sound("snow_warden_slam");
	public static final SoundEvent BLAST_CHARGE = sound("snow_warden_blast_charge");
	public static final SoundEvent BLAST = sound("snow_warden_blast");
	public static final SoundEvent THROW = sound("snow_warden_throw");
	public static final SoundEvent BOULDER_BURST = sound("frost_boulder_burst");
	public static final SoundEvent SNOW_GLOBE_SHAKE = sound("snow_globe_shake");
	public static final SoundEvent FROST_THORNS = sound("frost_thorns");

	private SnowWardens() {
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
		FabricDefaultAttributeRegistry.register(SNOW_WARDEN, SnowWarden.createAttributes());
		SpawnPlacements.register(SNOW_WARDEN, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				SnowWarden::checkSnowWardenSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SNOWY_PLAINS), MobCategory.MONSTER, SNOW_WARDEN,
				SNOWY_PLAINS_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.ICE_SPIKES), MobCategory.MONSTER, SNOW_WARDEN,
				ICE_SPIKES_WEIGHT, 1, 1);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SNOW_GOLEM_SPAWN_EGG, SNOW_WARDEN_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.HEART_OF_THE_SEA, FROZEN_HEART));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.RECOVERY_COMPASS, SNOW_GLOBE));
	}
}
