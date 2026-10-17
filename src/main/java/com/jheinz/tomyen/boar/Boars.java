package com.jheinz.tomyen.boar;

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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.PartSlot;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.RodPartItem;

/**
 * The Wild Boar ({@link Boar}), a Scavenger-tier animal of jungles, taigas and dark forests, and what it gives: Raw Boar
 * (more filling than pork, and it cooks), leather, and the Boar Tusk. Tusks make the Tusk Spear (a vanilla spear, a
 * little better than stone), the Tusk Harpoon (cheap ammo for the Harpoon Gun, see Gunsmith) and the Tusk Hook (a rod
 * part). Its art, data and sounds are written by tools/gen_boar.py.
 */
public final class Boars {
	public static final EntityType<Boar> BOAR = entity("boar", EntityType.Builder.of(Boar::new, MobCategory.CREATURE)
			.sized(0.95f, 0.95f)
			.eyeHeight(0.7f)
			.clientTrackingRange(10));

	/** What boars eat (and are bred with): carrots, beetroots and mushrooms. */
	public static final TagKey<Item> BOAR_FOOD = TagKey.create(Registries.ITEM, MyMod.id("boar_food"));
	private static final TagKey<Item> TUSK_TOOL_MATERIALS = TagKey.create(Registries.ITEM, MyMod.id("tusk_tool_materials"));
	/** A tusk point: between stone and copper, a little sharper than either, mended with tusks. */
	public static final ToolMaterial TUSK = new ToolMaterial(BlockTags.INCORRECT_FOR_STONE_TOOL, 160, 4.0f, 1.5f, 12, TUSK_TOOL_MATERIALS);

	public static final Item BOAR_TUSK = item("boar_tusk", Item::new, new Item.Properties());
	public static final Item RAW_BOAR = item("raw_boar", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3f).build()));
	public static final Item COOKED_BOAR = item("cooked_boar", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.9f).build()));
	/** A vanilla spear (jab, charge, Lunge) with a tusk point; stone's reach and timing, a touch more damage. */
	public static final Item TUSK_SPEAR = item("tusk_spear", Item::new, new Item.Properties()
			.spear(TUSK, 0.75f, 0.88f, 0.7f, 4.5f, 13.0f, 9.0f, 5.1f, 13.5f, 4.6f));
	/** Harpoon Gun ammo: a tusk lashed to a stick. Cheaper than the steel spear, and it doesn't hit as hard. */
	public static final Item TUSK_HARPOON = item("tusk_harpoon", Item::new, new Item.Properties().stacksTo(16));
	/** A hook carved from a tusk: holds a fish well, and the predators go for the smell of it. */
	public static final Item TUSK_HOOK = item("tusk_hook",
			properties -> new RodPartItem(properties, PartSlot.HOOK, RodModifiers.builder().escape(0.2f).predator(0.25f).build()),
			new Item.Properties());
	public static final Item BOAR_SPAWN_EGG = item("boar_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(BOAR));

	public static final SoundEvent AMBIENT = sound("boar_ambient");
	public static final SoundEvent HURT = sound("boar_hurt");
	public static final SoundEvent DEATH = sound("boar_death");
	/** A snort and a squeal: angry, and the start of the warning. */
	public static final SoundEvent ANGRY = sound("boar_angry");
	/** A hoof scraping the ground before a charge. */
	public static final SoundEvent PAW = sound("boar_paw");
	public static final SoundEvent CHARGE = sound("boar_charge");
	/** A charge landing, on a victim or a wall. */
	public static final SoundEvent IMPACT = sound("boar_impact");
	public static final SoundEvent STUNNED = sound("boar_stunned");
	/** Snuffling and chewing in the dirt. */
	public static final SoundEvent ROOT = sound("boar_root");
	public static final SoundEvent STEP = sound("boar_step");

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private Boars() {
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
		FabricDefaultAttributeRegistry.register(BOAR, Boar.createAttributes());
		SpawnPlacements.register(BOAR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Boar::checkBoarSpawnRules);
		// Small groups in the jungles, the taigas and the dark forest.
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.TAIGA,
				Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.DARK_FOREST), MobCategory.CREATURE, BOAR, 8, 2, 4);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.PIG_SPAWN_EGG, BOAR_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.insertAfter(Items.COOKED_PORKCHOP, RAW_BOAR, COOKED_BOAR));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.RABBIT_HIDE, BOAR_TUSK));
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB)
				.register(output -> output.insertAfter(Items.STONE_SPEAR, TUSK_SPEAR));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Fishing.TREBLE_HOOK, TUSK_HOOK));
	}
}
