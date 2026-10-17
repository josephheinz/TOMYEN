package com.jheinz.tomyen.gravedigger;

import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.bloomery.Bloomery;

/**
 * The Grave Digger: a zombie gravedigger who comes out at night and, when he can't walk to somebody, digs under their
 * walls to get at them (see {@link GraveDigger} and {@link TunnelGoal}). His entity, its spawning, the block tags that
 * say what he can dig, his sounds, and the Gravedigger's Shovel he sometimes drops.
 */
public final class GraveDiggers {
	/** Rarer than zombies (95), about as common as witches (5) are to them. */
	private static final int SPAWN_WEIGHT = 8;

	public static final EntityType<GraveDigger> GRAVE_DIGGER = entity("grave_digger", EntityType.Builder.of(GraveDigger::new, MobCategory.MONSTER)
			.sized(0.6f, 1.95f)
			.eyeHeight(1.6f)
			.clientTrackingRange(8)
			.notInPeaceful());

	/** Soft ground he digs through quickly. */
	public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK, MyMod.id("grave_digger_diggable"));
	/** Natural stone he can dig, slowly. */
	public static final TagKey<Block> SOFT_STONE = TagKey.create(Registries.BLOCK, MyMod.id("grave_digger_soft_stone"));
	/** Never dug, whatever the other two tags say: obsidian, reinforced deepslate, metal blocks... */
	public static final TagKey<Block> PROOF = TagKey.create(Registries.BLOCK, MyMod.id("grave_digger_proof"));

	/** On a Gravedigger's Shovel: it's night where its holder is, and it digs faster. Kept up to date by the shovel. */
	public static final DataComponentType<Boolean> NIGHT_SHIFT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("night_shift"), DataComponentType.<Boolean>builder().persistent(Codec.BOOL)
					.networkSynchronized(ByteBufCodecs.BOOL).build());

	/** Sturdy: iron's mining level and speed, near twice its wear, mended with bloom steel. */
	public static final ToolMaterial SHOVEL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 480, 6.0f, 2.0f, 12,
			Bloomery.REPAIRS);

	public static final Item GRAVEDIGGER_SHOVEL = item("gravedigger_shovel", GravediggerShovelItem::new,
			new Item.Properties().shovel(SHOVEL_MATERIAL, 1.5f, -3.0f).rarity(Rarity.UNCOMMON));
	public static final Item GRAVE_DIGGER_SPAWN_EGG = item("grave_digger_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(GRAVE_DIGGER));

	public static final SoundEvent AMBIENT = sound("grave_digger_ambient");
	public static final SoundEvent HURT = sound("grave_digger_hurt");
	public static final SoundEvent DEATH = sound("grave_digger_death");
	public static final SoundEvent STEP = sound("grave_digger_step");
	/** His shovel biting into the ground. */
	public static final SoundEvent DIG = sound("grave_digger_dig");
	/** The same from under the floor: the warning that he's coming up. */
	public static final SoundEvent DIG_MUFFLED = sound("grave_digger_dig_muffled");
	/** The flat of the shovel on somebody's head. */
	public static final SoundEvent CLANG = sound("grave_digger_clang");

	private GraveDiggers() {
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
		FabricDefaultAttributeRegistry.register(GRAVE_DIGGER, GraveDigger.createAttributes());
		SpawnPlacements.register(GRAVE_DIGGER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Monster::checkMonsterSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, GRAVE_DIGGER, SPAWN_WEIGHT, 1, 1);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.ZOMBIE_SPAWN_EGG, GRAVE_DIGGER_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.IRON_HOE, GRAVEDIGGER_SHOVEL));
	}
}
