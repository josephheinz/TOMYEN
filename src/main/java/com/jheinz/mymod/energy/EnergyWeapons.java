package com.jheinz.mymod.energy;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.alien.Aliens;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.meteorite.Meteorites;
import com.jheinz.mymod.napalm.Napalm;
import com.jheinz.mymod.skullcrane.Skullcranes;
import com.jheinz.mymod.station.StationRecipe;
import com.jheinz.mymod.tungsten.Tungsten;

/**
 * Energy weapons, the Exotic tier above the guns: the uranium-fed {@link LaserRifleItem Laser Rifle}, the
 * {@link RailgunItem Railgun} built round an Alien Reactor, and the aliens' own {@link RayGunItem Ray Gun}. Also what
 * they're made from and fire: Copper Coils, Uranium Power Cells, steel and tungsten Rail Slugs, and the Weapon Station
 * parts. They're all made from what's already about: steel plate, duralumin, rubber, computer chips, tungsten carbide,
 * enriched uranium and fuel rods, a Skullcrane's Optic Lens and meteorite Star Crystal (see {@link #stationRecipes}).
 */
public final class EnergyWeapons {
	/** On an energy weapon: how hot it was when it last fired (see {@link EnergyWeaponItem#heat}). */
	public static final DataComponentType<Heat> HEAT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MyMod.id("energy_heat"),
			DataComponentType.<Heat>builder().persistent(Heat.CODEC).networkSynchronized(Heat.STREAM_CODEC).build());

	public static final ResourceKey<DamageType> LASER = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("laser"));
	public static final ResourceKey<DamageType> RAILGUN_SLUG = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("railgun_slug"));
	/** A UFO's zap, an alien's, or a Ray Gun's. */
	public static final ResourceKey<DamageType> ALIEN_RAY = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("alien_ray"));

	public static final SoundEvent LASER_SHOT = sound("laser_shot");
	/** Vents hissing as an energy weapon overheats. */
	public static final SoundEvent OVERHEAT = sound("energy_overheat");
	public static final SoundEvent CELL_LOAD = sound("power_cell_load");
	public static final SoundEvent RAILGUN_CHARGE = sound("railgun_charge");
	public static final SoundEvent RAILGUN_SHOT = sound("railgun_shot");
	public static final SoundEvent SLUG_LOAD = sound("rail_slug_load");
	/** The alien zap: a Ray Gun's, an alien's, or a UFO's. */
	public static final SoundEvent RAY_ZAP = sound("ray_zap");

	/** Copper wire wound on a rubber former: for the laser's pump coils and the railgun's rails. */
	public static final Item COPPER_COIL = item("copper_coil", Item::new, new Item.Properties());
	public static final Item URANIUM_POWER_CELL = item("uranium_power_cell", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item STEEL_RAIL_SLUG = item("steel_rail_slug", Item::new, new Item.Properties());
	public static final Item TUNGSTEN_RAIL_SLUG = item("tungsten_rail_slug", Item::new, new Item.Properties());
	/** The lens stack, star crystal lasing rods and driver chips. */
	public static final Item LASER_EMITTER = item("laser_emitter", Item::new, new Item.Properties().stacksTo(1));
	/** The steel body with its pump coils, rubber grips and the cell well. */
	public static final Item LASER_RIFLE_HOUSING = item("laser_rifle_housing", Item::new, new Item.Properties().stacksTo(1));
	public static final Item LASER_RIFLE = item("laser_rifle", properties -> new LaserRifleItem(properties, LaserRifleItem.defaultStats()),
			new Item.Properties().durability(1500).repairable(Industry.STEEL_PLATE).rarity(Rarity.RARE));
	/** Tungsten carbide rails wound with copper coils in a duralumin frame. */
	public static final Item RAILGUN_RAILS = item("railgun_rails", Item::new, new Item.Properties().stacksTo(1));
	public static final Item RAILGUN = item("railgun", properties -> new RailgunItem(properties, RailgunItem.defaultStats()),
			new Item.Properties().durability(600).repairable(Tungsten.TUNGSTEN_CARBIDE).rarity(Rarity.EPIC));
	public static final Item RAY_GUN = item("ray_gun", RayGunItem::new, new Item.Properties().durability(400).rarity(Rarity.RARE));

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private EnergyWeapons() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/**
	 * The Weapon Station's energy weapon recipes. The cells and coils are crafting-table recipes (data/mymod/recipe).
	 * <ul>
	 * <li>Laser Emitter: a Skullcrane's Optic Lens to focus it, star crystal shards for the lasing rods, chips to drive it.
	 * <li>Laser Rifle Housing: steel plate, copper coils for the pump, rubber for the grips and insulation.
	 * <li>Railgun Rails: tungsten carbide rails, copper coils, a duralumin frame.
	 * <li>Railgun: the rails round an Alien Reactor, in an Alien Alloy body.
	 * <li>Rail slugs, four a time: steel or tungsten carbide with a copper driving band.
	 * </ul>
	 */
	public static List<StationRecipe> stationRecipes() {
		return List.of(
				recipe(LASER_EMITTER, 1, cost(Skullcranes.OPTIC_LENS, 1), cost(Meteorites.STAR_CRYSTAL_SHARD, 2), cost(ModItems.COMPUTER_CHIP, 2)),
				recipe(LASER_RIFLE_HOUSING, 1, cost(Industry.STEEL_PLATE, 4), cost(COPPER_COIL, 2), cost(Industry.RUBBER, 2)),
				recipe(LASER_RIFLE, 1, cost(LASER_EMITTER, 1), cost(LASER_RIFLE_HOUSING, 1)),
				recipe(RAILGUN_RAILS, 1, cost(Tungsten.TUNGSTEN_CARBIDE, 4), cost(COPPER_COIL, 4), cost(Industry.DURALUMIN_SHEET, 2)),
				recipe(RAILGUN, 1, cost(RAILGUN_RAILS, 1), cost(Aliens.ALIEN_REACTOR, 1), cost(Aliens.ALIEN_ALLOY, 4)),
				// A copper driving band and a redstone-doped core; steel and copper alone are the revolver cylinder.
				recipe(STEEL_RAIL_SLUG, 4, cost(Industry.STEEL_INGOT, 1), cost(Items.COPPER_NUGGET, 1), cost(Items.REDSTONE, 1)),
				recipe(TUNGSTEN_RAIL_SLUG, 4, cost(Tungsten.TUNGSTEN_CARBIDE, 1), cost(Items.COPPER_NUGGET, 1)));
	}

	private static StationRecipe recipe(Item result, int count, StationRecipe.Cost... costs) {
		return new StationRecipe(result, count, List.of(costs));
	}

	private static StationRecipe.Cost cost(Item item, int count) {
		return new StationRecipe.Cost(item, count);
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(EnergyBeam.TYPE, EnergyBeam.CODEC);
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			output.insertAfter(Napalm.FLAMETHROWER_WAND, LASER_RIFLE, LASER_EMITTER, LASER_RIFLE_HOUSING, RAILGUN, RAILGUN_RAILS, RAY_GUN);
			output.insertBefore(Items.SPECTRAL_ARROW, URANIUM_POWER_CELL, TUNGSTEN_RAIL_SLUG, STEEL_RAIL_SLUG);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.COPPER_INGOT, COPPER_COIL));
	}
}
