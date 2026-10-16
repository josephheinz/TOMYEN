package com.jheinz.tomyen.schnozzle;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
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
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.MyMod;

/**
 * The Schnozzle, a giant detached nose that floats about grassland and woods at night: its entity, the snot
 * rockets it fires from its nostrils, its drops (Giant Boogers, which can be thrown, and Nose Hair) and its sounds,
 * and the Nose Hair Whip braided from those drops, with its crack, its sounds and the Slimed effect.
 *
 * <p>It's a rare elite, not a boss: spawn weight {@link #SPAWN_WEIGHT} (zombies are 95), alone, only at night, only
 * where it can see the sky, and never within {@link Schnozzle#CROWD_RADIUS} blocks of another one.
 */
public final class Schnozzles {
	private static final int SPAWN_WEIGHT = 2;

	public static final EntityType<Schnozzle> SCHNOZZLE = entity("schnozzle", EntityType.Builder.of(Schnozzle::new, MobCategory.MONSTER)
			.sized(2.4f, 3.5f)
			.eyeHeight(1.0f)
			.clientTrackingRange(10)
			.notInPeaceful());
	/** Every tick: it's quick and steers. */
	public static final EntityType<SnotRocket> SNOT_ROCKET = entity("snot_rocket",
			EntityType.Builder.<SnotRocket>of(SnotRocket::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.5f, 0.5f)
					.clientTrackingRange(8)
					.updateInterval(1));
	public static final EntityType<ThrownBooger> THROWN_BOOGER = entity("thrown_booger",
			EntityType.Builder.<ThrownBooger>of(ThrownBooger::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.25f, 0.25f)
					.clientTrackingRange(4)
					.updateInterval(10));

	public static final Item GIANT_BOOGER = item("giant_booger", GiantBoogerItem::new, new Item.Properties().stacksTo(16));
	public static final Item NOSE_HAIR = item("nose_hair", Item::new, new Item.Properties());
	public static final Item NOSE_HAIR_WHIP = item("nose_hair_whip", NoseHairWhipItem::new, NoseHairWhipItem.properties());
	public static final Item SCHNOZZLE_SPAWN_EGG = item("schnozzle_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(SCHNOZZLE));

	public static final SoundEvent AMBIENT = sound("schnozzle_ambient");
	/** Its presence cue: a heavy wheeze that carries much further than its other sounds (see sounds.json). */
	public static final SoundEvent WHEEZE = Registry.register(BuiltInRegistries.SOUND_EVENT, MyMod.id("schnozzle_wheeze"),
			SoundEvent.createFixedRangeEvent(MyMod.id("schnozzle_wheeze"), Schnozzle.WHEEZE_RANGE));
	public static final SoundEvent SNIFF = sound("schnozzle_sniff");
	public static final SoundEvent LAUNCH = sound("schnozzle_launch");
	public static final SoundEvent LASH = sound("schnozzle_lash");
	public static final SoundEvent SNEEZE_WINDUP = sound("schnozzle_sneeze_windup");
	public static final SoundEvent SNEEZE = sound("schnozzle_sneeze");
	public static final SoundEvent HURT = sound("schnozzle_hurt");
	public static final SoundEvent DEATH = sound("schnozzle_death");
	public static final SoundEvent BOOGER_SPLAT = sound("booger_splat");
	public static final SoundEvent WHIP_CRACK = sound("nose_hair_whip_crack");
	public static final SoundEvent WHIP_SNAG = sound("nose_hair_whip_snag");

	/** The shock ring where the whip's tip cracks. */
	public static final SimpleParticleType WHIP_CRACK_PARTICLE =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("whip_crack"), FabricParticleTypes.simple(true));
	/** Slowed and dripping, now and then, after a lash from the booger-gripped whip. */
	public static final Holder<MobEffect> SLIMED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("slimed"),
			new SlimedEffect());

	private Schnozzles() {
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
		FabricDefaultAttributeRegistry.register(SCHNOZZLE, Schnozzle.createAttributes());
		SpawnPlacements.register(SCHNOZZLE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Schnozzle::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA,
				Biomes.SAVANNA_PLATEAU, Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.TAIGA, Biomes.SWAMP),
				MobCategory.MONSTER, SCHNOZZLE, SPAWN_WEIGHT, 1, 1);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SHEEP_SPAWN_EGG, SCHNOZZLE_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.SLIME_BALL, GIANT_BOOGER, NOSE_HAIR));
		DispenserBlock.registerProjectileBehavior(GIANT_BOOGER);
	}
}
