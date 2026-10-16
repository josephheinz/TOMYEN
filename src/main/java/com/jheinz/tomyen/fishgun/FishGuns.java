package com.jheinz.tomyen.fishgun;

import java.util.List;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.napalm.Napalm;
import com.jheinz.tomyen.scrapbench.ScrapBench;
import com.jheinz.tomyen.station.StationRecipe;

/**
 * The Fish Gun (a Workshop-tier Weapon Station gun, {@link FishGunItem}), the fish it fires ({@link FishProjectile}), the
 * state it keeps on its stack, and its sounds and damage type.
 */
public final class FishGuns {
	/** On a held Fish Gun: what it'll fire next. Server-computed every tick, synced, not saved. */
	public static final DataComponentType<LoadedFish> LOADED_FISH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("loaded_fish"), DataComponentType.<LoadedFish>builder().networkSynchronized(LoadedFish.STREAM_CODEC).build());
	/** On a Fish Gun: the kind of fish the fire-mode key last picked. */
	public static final DataComponentType<LoadedFish.Choice> FISH_CHOICE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("fish_choice"), DataComponentType.<LoadedFish.Choice>builder().persistent(LoadedFish.Choice.CODEC)
					.networkSynchronized(LoadedFish.Choice.STREAM_CODEC).build());

	/** The air blast: a real 20 gauge, slowed and muffled to a thump (tools/import_gun_sounds.py). */
	public static final SoundEvent FISH_GUN_FIRE = sound("fish_gun_fire");
	/** The rush of the fish leaving the bell: vanilla's wind charge burst, pitched down. */
	public static final SoundEvent FISH_GUN_WHOOSH = sound("fish_gun_whoosh");
	/** A fish meeting something: wet slaps of slime, honey and mud. */
	public static final SoundEvent FISH_SPLAT = sound("fish_splat");

	public static final ResourceKey<DamageType> FISH_GUN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("fish_gun"));

	public static final EntityType<FishProjectile> FISH_ROUND = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("fish_round")),
			EntityType.Builder.<FishProjectile>of(FishProjectile::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.45f, 0.45f)
					.clientTrackingRange(8)
					.updateInterval(2)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("fish_round"))));

	/**
	 * Semi-auto: one fish a click. Its recoil is set per shot by the fish's weight, the reload stat is unused (loading
	 * time is the fish's own, {@link FishAmmo#loadTicks}) and so is the range (fish fly as projectiles).
	 */
	public static final Item FISH_GUN = item("fish_gun",
			new Item.Properties().durability(350).repairable(Items.COPPER_INGOT).rarity(Rarity.UNCOMMON));

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private FishGuns() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new FishGunItem(properties.setId(key),
				new GunStats(FISH_GUN_FIRE, 3.0f, 0, 0, List.of(FireMode.SEMI)).withSpread(1.0f, 0.0f)));
	}

	/**
	 * Its Weapon Station recipe: a copper bell barrel and air tank, springs for the valve, a piston for the blast valve and
	 * tech scrap for the regulator and gauge.
	 */
	public static List<StationRecipe> stationRecipes() {
		return List.of(new StationRecipe(FISH_GUN, 1, List.of(new StationRecipe.Cost(Items.COPPER_INGOT, 4),
				new StationRecipe.Cost(ScrapBench.SPRING, 2), new StationRecipe.Cost(Items.PISTON, 1),
				new StationRecipe.Cost(ModItems.TECH_SCRAP, 2))));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> output.insertAfter(Napalm.FLAMETHROWER_WAND, FISH_GUN));
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
			if (player.getMainHandItem().getItem() instanceof FishGunItem) {
				FishGunItem.refresh(player, player.getMainHandItem());
			}
		}));
	}
}
