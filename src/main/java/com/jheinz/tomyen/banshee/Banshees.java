package com.jheinz.tomyen.banshee;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.MyMod;

/**
 * The Banshee (Foundry tier): a wailing ghost that drifts through dark forests and swamps at night. Her entity, the
 * Ectoplasm she leaves behind (two make a Soul Charge, or a phantom membrane; see tools/gen_banshee.py for the
 * recipes) and her sounds.
 *
 * <p>Rare everywhere she spawns: weight {@link #SPAWN_WEIGHT_HAUNTS} in dark forests and swamps, {@link #SPAWN_WEIGHT}
 * in the other old woods (zombies are 95), alone, at night, never within {@link Banshee#CROWD_RADIUS} blocks of another.
 */
public final class Banshees {
	private static final int SPAWN_WEIGHT_HAUNTS = 4;
	private static final int SPAWN_WEIGHT = 1;

	public static final EntityType<Banshee> BANSHEE = entity("banshee", EntityType.Builder.of(Banshee::new, MobCategory.MONSTER)
			.sized(0.7f, 2.1f)
			.eyeHeight(1.8f)
			.clientTrackingRange(10)
			.notInPeaceful());

	/** What's left of a banshee: cold, faintly glowing spirit-stuff. */
	public static final Item ECTOPLASM = item("ectoplasm", Item::new, new Item.Properties());
	public static final Item BANSHEE_SPAWN_EGG = item("banshee_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(BANSHEE));

	/** Sobbing, now and then, as she drifts. */
	public static final SoundEvent AMBIENT = sound("banshee_ambient");
	/** The long indrawn breath before the wail: the warning. */
	public static final SoundEvent WAIL_WINDUP = sound("banshee_wail_windup");
	/** The wail itself: heard {@value Banshee#WAIL_SOUND_RANGE} blocks off, whatever the volume. */
	public static final SoundEvent WAIL = Registry.register(BuiltInRegistries.SOUND_EVENT, MyMod.id("banshee_wail"),
			SoundEvent.createFixedRangeEvent(MyMod.id("banshee_wail"), Banshee.WAIL_SOUND_RANGE));
	public static final SoundEvent TOUCH = sound("banshee_touch");
	public static final SoundEvent HURT = sound("banshee_hurt");
	public static final SoundEvent DEATH = sound("banshee_death");
	/** Struck by something holy: she comes apart. */
	public static final SoundEvent BANISHED = sound("banshee_banished");

	private Banshees() {
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
		FabricDefaultAttributeRegistry.register(BANSHEE, Banshee.createAttributes());
		SpawnPlacements.register(BANSHEE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Banshee::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.PALE_GARDEN, Biomes.SWAMP, Biomes.MANGROVE_SWAMP),
				MobCategory.MONSTER, BANSHEE, SPAWN_WEIGHT_HAUNTS, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
				Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA), MobCategory.MONSTER, BANSHEE, SPAWN_WEIGHT, 1, 1);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.VEX_SPAWN_EGG, BANSHEE_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.PHANTOM_MEMBRANE, ECTOPLASM));
	}
}
