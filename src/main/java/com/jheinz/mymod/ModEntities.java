package com.jheinz.mymod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

import com.jheinz.mymod.cart.ShoppingCart;

public final class ModEntities {
	public static final EntityType<ShoppingCart> SHOPPING_CART = register("shopping_cart",
			EntityType.Builder.of(ShoppingCart::new, MobCategory.MONSTER)
					.sized(0.9f, 0.95f)
					.eyeHeight(0.6f)
					.clientTrackingRange(8)
					.notInPeaceful());

	public static final Item SHOPPING_CART_SPAWN_EGG = spawnEgg("shopping_cart_spawn_egg", SHOPPING_CART);

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item spawnEgg(String name, EntityType<?> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(SHOPPING_CART, ShoppingCart.createAttributes());
		SpawnPlacements.register(SHOPPING_CART, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				ShoppingCart::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.DESERT), MobCategory.MONSTER,
				SHOPPING_CART, 40, 1, 2);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SHEEP_SPAWN_EGG, SHOPPING_CART_SPAWN_EGG));
	}
}
