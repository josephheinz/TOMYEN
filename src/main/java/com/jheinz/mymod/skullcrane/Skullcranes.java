package com.jheinz.mymod.skullcrane;

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

import com.jheinz.mymod.MyMod;

/**
 * Skullcranes: packs of flying mechanical skulls with servo arms that hunt the mountains at night, grab their prey
 * between them, carry it up and drop it. Their entity, the invisible grip that carries the prey, their drops (Servo
 * Arms and Optic Lenses, which go into tech scrap, computer chips and spyglasses) and their sounds; and the gear
 * built from those parts: the Grapple Claw, the Optic Monocle, the Servo Arm Backpack (see {@link ServoGear}) and the
 * Servo Drone, a tame little servo skull.
 *
 * <p>Uncommon rather than rare: spawn weight {@link #SPAWN_WEIGHT} (zombies are 95, the Schnozzle 2), in packs of
 * {@value #MIN_PACK}-{@value #MAX_PACK}, only at night, only under open sky, only in mountain and hill biomes (where
 * the drop is deadliest), and not where there are already a couple of packs about (see
 * {@link Skullcrane#checkSpawnRules}).
 */
public final class Skullcranes {
	private static final int SPAWN_WEIGHT = 6;
	static final int MIN_PACK = 3;
	static final int MAX_PACK = 6;

	public static final EntityType<Skullcrane> SKULLCRANE = entity("skullcrane", EntityType.Builder.of(Skullcrane::new, MobCategory.MONSTER)
			.sized(0.7f, 0.7f)
			.eyeHeight(0.4f)
			.clientTrackingRange(10)
			// Every tick, so the ones carrying something keep up with it.
			.updateInterval(1)
			.notInPeaceful());
	/** What a skullcrane pack's prey rides while they carry it: invisible, never summoned, every tick. */
	public static final EntityType<SkullcraneGrip> GRIP = entity("skullcrane_grip",
			EntityType.Builder.<SkullcraneGrip>of(SkullcraneGrip::new, MobCategory.MISC)
					.noLootTable()
					.noSummon()
					.fireImmune()
					.sized(0.2f, 0.2f)
					.clientTrackingRange(10)
					.updateInterval(1));

	/** The Grapple Claw's claw: quick, every tick, so its cable stays on it. */
	public static final EntityType<GrappleHook> GRAPPLE_HOOK = entity("grapple_hook",
			EntityType.Builder.<GrappleHook>of(GrappleHook::new, MobCategory.MISC)
					.noLootTable()
					.noSummon()
					.sized(0.3f, 0.3f)
					.clientTrackingRange(8)
					.updateInterval(1));
	public static final EntityType<ServoDrone> SERVO_DRONE_ENTITY = entity("servo_drone", EntityType.Builder.of(ServoDrone::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.45f, 0.45f)
			.eyeHeight(0.25f)
			.clientTrackingRange(10)
			.updateInterval(2));

	public static final Item SERVO_ARM = item("servo_arm", Item::new, new Item.Properties());
	public static final Item OPTIC_LENS = item("optic_lens", Item::new, new Item.Properties());
	public static final Item GRAPPLE_CLAW = item("grapple_claw", GrappleClawItem::new, GrappleClawItem.properties());
	public static final Item OPTIC_MONOCLE = item("optic_monocle", Item::new, ServoGear.monocleProperties());
	public static final Item SERVO_ARM_BACKPACK = item("servo_arm_backpack", Item::new, ServoGear.backpackProperties());
	public static final Item SERVO_DRONE = item("servo_drone", ServoDroneItem::new, ServoDroneItem.properties());
	public static final Item SKULLCRANE_SPAWN_EGG = item("skullcrane_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(SKULLCRANE));

	/** A whirring servo whine: its ambient sound. */
	public static final SoundEvent AMBIENT = sound("skullcrane_ambient");
	/** Its jaw clacking as it attacks. */
	public static final SoundEvent CHATTER = sound("skullcrane_chatter");
	public static final SoundEvent SCRATCH = sound("skullcrane_scratch");
	public static final SoundEvent CLAMP = sound("skullcrane_clamp");
	/** The antigrav hum as a pack lifts off with its prey, and every so often on the way up. */
	public static final SoundEvent LIFT = sound("skullcrane_lift");
	public static final SoundEvent RELEASE = sound("skullcrane_release");
	public static final SoundEvent HURT = sound("skullcrane_hurt");
	public static final SoundEvent DEATH = sound("skullcrane_death");

	private Skullcranes() {
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
		FabricDefaultAttributeRegistry.register(SKULLCRANE, Skullcrane.createAttributes());
		FabricDefaultAttributeRegistry.register(SERVO_DRONE_ENTITY, ServoDrone.createAttributes());
		ServoGear.initialize();
		SpawnPlacements.register(SKULLCRANE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Skullcrane::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS,
				Biomes.WINDSWEPT_FOREST, Biomes.WINDSWEPT_SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.GROVE,
				Biomes.SNOWY_SLOPES, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS, Biomes.WOODED_BADLANDS,
				Biomes.STONY_SHORE), MobCategory.MONSTER, SKULLCRANE, SPAWN_WEIGHT, MIN_PACK, MAX_PACK);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SHEEP_SPAWN_EGG, SKULLCRANE_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.BONE, SERVO_ARM, OPTIC_LENS));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.SPYGLASS, GRAPPLE_CLAW, OPTIC_MONOCLE, SERVO_ARM_BACKPACK, SERVO_DRONE));
	}
}
