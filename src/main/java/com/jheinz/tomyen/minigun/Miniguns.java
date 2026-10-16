package com.jheinz.tomyen.minigun;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.UseEffects;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.hogfire.Hogfires;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.napalm.Napalm;
import com.jheinz.tomyen.skullcrane.Skullcranes;
import com.jheinz.tomyen.station.StationRecipe;
import com.jheinz.tomyen.tungsten.Tungsten;

/**
 * The power armor Minigun: the gun, its three Weapon Station parts, its sounds and the state it keeps on its stack.
 * The same works ({@link RotaryGun}) drive the Hogfire's gun mount ({@link Hogfires#GUN_MOUNT}).
 */
public final class Miniguns {
	/** On a Minigun: its spin, heat and firing, for every client to draw and hear. Not saved: it winds down on its own. */
	public static final DataComponentType<MinigunState> STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("minigun_state"), DataComponentType.<MinigunState>builder().networkSynchronized(MinigunState.STREAM_CODEC).build());

	public static final SoundEvent SPIN_UP = sound("minigun_spin_up");
	public static final SoundEvent SPIN = sound("minigun_spin");
	public static final SoundEvent FIRE = sound("minigun_fire");
	public static final SoundEvent SPIN_DOWN = sound("minigun_spin_down");

	/** Six tungsten carbide barrels in their clamps. */
	public static final Item BARREL_CLUSTER = item("minigun_barrel_cluster", Item::new, new Item.Properties().stacksTo(1));
	/** The drum and the feed motor that strips rounds into the rotor: a Skullcrane's servo arm, rewired. */
	public static final Item FEED_MOTOR = item("minigun_feed_motor", Item::new, new Item.Properties().stacksTo(1));
	/** Rotor housing, receiver, carry handle and rubber grip. */
	public static final Item HOUSING = item("minigun_housing", Item::new, new Item.Properties().stacksTo(1));

	/**
	 * Full-auto only, a round a tick once spun up. Light recoil (the suit takes it) but a moderate spread from the hip;
	 * the reload time is unused (it has no magazine). Firing slows its holder to 60% and stops them sprinting.
	 */
	public static final Item MINIGUN = item("minigun",
			properties -> new MinigunItem(properties,
					new GunStats(FIRE, 0.12f, RotaryGun.RANGE, 0, List.of(FireMode.AUTO)).withAutoTicks(1).withSpread(1.6f, 0.05f)),
			new Item.Properties().durability(6000).repairable(Industry.STEEL_INGOT).rarity(Rarity.RARE)
					.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, 0.6f)));

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private Miniguns() {
	}

	/**
	 * Weapon Station recipes: the barrels from tungsten carbide and steel plate, the feed from a Skullcrane's Servo Arm and
	 * computer chips, the housing from steel plate with rubber grips; then the three together. The Hogfire Gun Mount is
	 * a crafting-table recipe (a Minigun strapped to a steel saddle).
	 */
	public static List<StationRecipe> station() {
		return List.of(
				recipe(BARREL_CLUSTER, cost(Tungsten.TUNGSTEN_CARBIDE, 6), cost(Industry.STEEL_PLATE, 2), cost(Industry.STEEL_INGOT, 1)),
				recipe(FEED_MOTOR, cost(Skullcranes.SERVO_ARM, 1), cost(ModItems.COMPUTER_CHIP, 2), cost(Industry.STEEL_INGOT, 2)),
				recipe(HOUSING, cost(Industry.STEEL_PLATE, 4), cost(Industry.RUBBER, 2), cost(ModItems.COMPUTER_CHIP, 1)),
				recipe(MINIGUN, cost(BARREL_CLUSTER, 1), cost(FEED_MOTOR, 1), cost(HOUSING, 1)));
	}

	private static StationRecipe recipe(Item result, StationRecipe.Cost... costs) {
		return new StationRecipe(result, 1, List.of(costs));
	}

	private static StationRecipe.Cost cost(Item item, int count) {
		return new StationRecipe.Cost(item, count);
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
		// After the flamethrower (Napalm adds it first); the mount with the gun it carries.
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> output.insertAfter(Napalm.FLAMETHROWER_WAND,
				MINIGUN, BARREL_CLUSTER, FEED_MOTOR, HOUSING, Hogfires.GUN_MOUNT));
	}
}
