package com.jheinz.tomyen.shrimp;

import java.util.List;
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
import net.minecraft.tags.BiomeTags;
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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.BaitItem;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.shoddy.ShoddyGunItem;
import com.jheinz.tomyen.shoddy.ShoddyGuns;
import com.jheinz.tomyen.shotgun.ShotgunItem.Shell;

/**
 * The Shrimp with a Gun: a human-sized mantis shrimp that stands up on its tail, holds a pistol in its claws and comes
 * out of the sea to shoot people. Its entity, its sounds, what it drops (Raw Shrimp, which is food and fishing bait and
 * cooks into Cooked Shrimp, and now and then its Shrimp Pistol) and where it spawns.
 *
 * <p>A Workshop-tier threat: dangerous to a player in leather and a pipe pistol, not a boss. It lives in every ocean and
 * on the beaches beside them, weight {@value #OCEAN_WEIGHT} in the sea and {@value #BEACH_WEIGHT} on the sand (zombies
 * are 95), in ones and twos, and far more often at night (see {@link Shrimp#checkSpawnRules}).
 *
 * <p>The Shrimp Pistol is the gun it carries, salvaged: a stubby four-shot pistol that takes the same Crude Rounds as the
 * pipe guns, loaded a round at a time through a gate on top, the slide racked once it's full. Better made than anything
 * off the Scrap Bench, so it misfires and jams less, but the salt has got into it.
 */
public final class Shrimps {
	private static final int OCEAN_WEIGHT = 6;
	private static final int BEACH_WEIGHT = 8;

	public static final EntityType<Shrimp> SHRIMP = entity("shrimp", EntityType.Builder.of(Shrimp::new, MobCategory.MONSTER)
			// Drawn about 2.2 blocks to the antenna tips; the shell stands just under 2.
			.sized(0.9f, 1.95f)
			.eyeHeight(1.7f)
			.clientTrackingRange(10)
			.notInPeaceful());

	/** Ocean fish, which come to shrimp bait: everything the fishing species files put in the sea. */
	public static final TagKey<Item> SALTWATER_FISH = TagKey.create(Registries.ITEM, MyMod.id("saltwater_fish"));

	/**
	 * Raw Shrimp: a little food, better cooked, and good bait. On a rod the sea fish go for it (two and a half times as
	 * likely), and bigger ones; anything else bites a little sooner.
	 */
	public static final Item RAW_SHRIMP = item("raw_shrimp", properties -> new BaitItem(properties, new BaitItem.Effect(
			RodModifiers.builder().biteTicks(30).build(), 0.12f, SALTWATER_FISH, 2.5f, 0.2f, false, 0)),
			new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).build()));
	public static final Item COOKED_SHRIMP = item("cooked_shrimp", Item::new,
			new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7f).build()));

	public static final SoundEvent PISTOL_SHOT = sound("shrimp_pistol_shot");

	// Shell(item, pellets, pellet damage, pellet spread, full damage to, falls off to, min factor, range, HUD colour)
	// Quirks(misfire new, worn; jam new, worn; backfire new, worn; backfire damage)
	// Traits(ticks per round, ticks to rack the slide, racked after loading, handgun, shove, loose shot)
	public static final Item SHRIMP_PISTOL = item("shrimp_pistol",
			properties -> new ShoddyGunItem(properties,
					new GunStats(PISTOL_SHOT, 3.2f, 40, 14, List.of(FireMode.SEMI)).withSingleRoundLoading().withSpread(1.4f, 1.6f),
					List.of(new Shell(ShoddyGuns.CRUDE_ROUND, 1, 5.5f, 0.0f, 8.0, 28.0, 0.45f, 40.0, 0xA9AEB3)), 4, 6,
					new ShoddyGunItem.Quirks(0.03f, 0.14f, 0.04f, 0.2f, 0.0f, 0.03f, 1.5f),
					new ShoddyGunItem.Traits(10, 12, true, true, 0.0f, List.of())),
			new Item.Properties().durability(180).repairable(Items.IRON_INGOT).rarity(Rarity.UNCOMMON));
	public static final Item SHRIMP_SPAWN_EGG = item("shrimp_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(SHRIMP));

	public static final SoundEvent AMBIENT = sound("shrimp_ambient");
	public static final SoundEvent HURT = sound("shrimp_hurt");
	public static final SoundEvent DEATH = sound("shrimp_death");
	public static final SoundEvent STEP = sound("shrimp_step");
	public static final SoundEvent SWIM = sound("shrimp_swim");
	/** Its club claw going off: the fastest punch in the sea, a crack like a snapping branch. */
	public static final SoundEvent SNAP = sound("shrimp_snap");
	/** Drawing a bead: the hammer cocked. */
	public static final SoundEvent COCK = sound("shrimp_cock");
	/** A round thumbed into the gate, and the slide racked at the end of a reload. */
	public static final SoundEvent RELOAD_ROUND = sound("shrimp_reload_round");
	public static final SoundEvent RELOAD_RACK = sound("shrimp_reload_rack");

	private Shrimps() {
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
		FabricDefaultAttributeRegistry.register(SHRIMP, Shrimp.createAttributes());
		// Its own rule says where (in the sea near the surface, or on sand by it): see Shrimp.checkSpawnRules.
		SpawnPlacements.register(SHRIMP, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Shrimp::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_OCEAN), MobCategory.MONSTER, SHRIMP, OCEAN_WEIGHT, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_BEACH), MobCategory.MONSTER, SHRIMP, BEACH_WEIGHT, 1, 2);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.DROWNED_SPAWN_EGG, SHRIMP_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.insertAfter(Items.COOKED_SALMON, RAW_SHRIMP, COOKED_SHRIMP));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
				.register(output -> output.insertAfter(ShoddyGuns.JUNK_REPEATER, SHRIMP_PISTOL));
	}
}
