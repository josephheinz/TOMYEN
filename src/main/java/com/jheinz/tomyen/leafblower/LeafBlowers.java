package com.jheinz.tomyen.leafblower;

import java.util.List;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.scrapbench.ScrapBench;
import com.jheinz.tomyen.skullcrane.Skullcranes;
import com.jheinz.tomyen.station.StationRecipe;

/**
 * The Leaf Blower (Scavenger: built on the Scrap Bench), the Vacuum Kit (Industrial: a Weapon Station part with a rubber
 * hose and a servo-driven impeller) and the Leaf Blower-Vac the two make together at a crafting table (a transmute
 * recipe, so the fuel and the bag carry over). See {@link LeafBlowerItem} and {@link Wind}.
 */
public final class LeafBlowers {
	/**
	 * On a blower: the game time of its last tick of running, set on both sides, so every client can draw the wind and rev
	 * the engine for anyone using one. Not saved.
	 */
	public static final DataComponentType<Long> RUNNING_AT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("blower_running_at"), DataComponentType.<Long>builder().networkSynchronized(ByteBufCodecs.VAR_LONG).build());
	/** On a Blower-Vac: present when it's switched to suck. */
	public static final DataComponentType<Unit> SUCK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("blower_suck"), DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());

	/** A two-stroke at full throttle (synthesised by tools/gen_fishgun_blower.py); clients pitch it down to idle. */
	public static final SoundEvent ENGINE = sound("leaf_blower_engine");
	/** The pull cord ripping out, two coughs and the engine catching. */
	public static final SoundEvent START = sound("leaf_blower_start");

	/** Full-auto at a tick: holding use runs it continuously. The reload stat is the refuel time. */
	private static final GunStats STATS = new GunStats(ENGINE, 0.03f, Wind.BLOW_RANGE, 25, List.of(FireMode.AUTO))
			.withAutoTicks(1).withSpread(0.0f, 0.0f);

	public static final Item LEAF_BLOWER = item("leaf_blower", properties -> new LeafBlowerItem(properties, STATS, false),
			new Item.Properties().stacksTo(1));
	public static final Item LEAF_VACUUM = item("leaf_vacuum", properties -> new LeafBlowerItem(properties, STATS, true),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	/** The impeller, hose and canvas bag that turn a Leaf Blower into a Blower-Vac. */
	public static final Item VACUUM_KIT = item("vacuum_kit", Item::new, new Item.Properties().stacksTo(1));

	private LeafBlowers() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** The Vacuum Kit: a rubber hose, a Skullcrane's servo arm to spin the impeller, a steel plate housing, a leather bag. */
	public static List<StationRecipe> stationRecipes() {
		return List.of(new StationRecipe(VACUUM_KIT, 1, List.of(new StationRecipe.Cost(Industry.RUBBER, 2),
				new StationRecipe.Cost(Skullcranes.SERVO_ARM, 1), new StationRecipe.Cost(Industry.STEEL_PLATE, 2),
				new StationRecipe.Cost(Items.LEATHER, 3))));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.BRUSH, LEAF_BLOWER, LEAF_VACUUM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(ScrapBench.SCREWS, VACUUM_KIT));
		Wind.initialize();
	}
}
