package com.jheinz.mymod.drill;

import java.util.function.Function;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.meteorite.Meteorites;
import com.jheinz.mymod.meteorite.StarSteel;
import com.jheinz.mymod.tungsten.Tungsten;

/**
 * The drills, the tier of tools that bores what pickaxes can't ({@link DrillItem}), and their bits.
 * <ul>
 * <li>Hand Drill (Workshop, with the first guns): an egg-beater drill, cranked, so it needs no fuel. A little faster
 * than iron, iron's harvest level, and enough for meteorite stone and tungsten ore.</li>
 * <li>Steel Drill (Foundry into Industrial): a kerosene motor drill. Fast, diamond's harvest level and everything
 * drill-only (meteoric iron ore needs it), 1x1 or switched to 3x3.</li>
 * <li>Tungsten Carbide Drill ({@link Tungsten#TUNGSTEN_CARBIDE_DRILL}, top Industrial): heavier and faster, digs
 * 3x3 unless switched off, and burns kerosene or uranium fuel rods.</li>
 * <li>Star Drill (Exotic): star steel with a star crystal bit, the fastest; a Star Steel tool, so faster by night.</li>
 * </ul>
 * The bits are crafting parts: iron, steel, carbide and star crystal.
 */
public final class Drills {
	/** Every drill (data/mymod/tags/item/drills.json); minecraft:pickaxes takes them all. */
	public static final TagKey<Item> DRILLS = TagKey.create(Registries.ITEM, MyMod.id("drills"));
	private static final TagKey<Item> REPAIRS_HAND_DRILL = TagKey.create(Registries.ITEM, MyMod.id("repairs_hand_drill"));
	private static final TagKey<Item> REPAIRS_STEEL_DRILL = TagKey.create(Registries.ITEM, MyMod.id("repairs_steel_drill"));

	/** A little faster than iron at iron's harvest level, plus drill-only rock that doesn't need a motor. */
	public static final ToolMaterial HAND_DRILL_MATERIAL = new ToolMaterial(DrillItem.INCORRECT_FOR_HAND_DRILL, 450, 7.0f, 2.0f, 14,
			REPAIRS_HAND_DRILL);
	/** Faster than diamond, as durable as a diamond pickaxe and a half. */
	public static final ToolMaterial STEEL_DRILL_MATERIAL = new ToolMaterial(DrillItem.INCORRECT_FOR_POWERED_DRILL, 1500, 10.0f, 3.0f, 12,
			REPAIRS_STEEL_DRILL);
	/** The fastest digging there is, before star steel's night bonus. */
	public static final ToolMaterial STAR_DRILL_MATERIAL = new ToolMaterial(DrillItem.INCORRECT_FOR_POWERED_DRILL, 3000, 15.0f, 4.0f, 18,
			StarSteel.REPAIR_MATERIALS);

	/** On a motor drill: the fuel in its tank. Absent means empty. */
	public static final DataComponentType<Integer> DRILL_FUEL = component("drill_fuel", Codec.INT, ByteBufCodecs.VAR_INT);
	/** On a drill that bores: whether it digs 3x3. Absent means its default (on for the heavy drills). */
	public static final DataComponentType<Boolean> DRILL_WIDE = component("drill_wide", Codec.BOOL, ByteBufCodecs.BOOL);

	public static final SoundEvent DRILL_MOTOR = sound("drill_motor");
	public static final SoundEvent HAND_DRILL_CRANK = sound("hand_drill_crank");
	/** Sparks off the bit where it bites stone. */
	public static final SimpleParticleType DRILL_SPARK =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("drill_spark"), FabricParticleTypes.simple(false));

	public static final Item IRON_DRILL_BIT = item("iron_drill_bit", Item::new, new Item.Properties());
	public static final Item STEEL_DRILL_BIT = item("steel_drill_bit", Item::new, new Item.Properties());
	public static final Item CARBIDE_DRILL_BIT = item("carbide_drill_bit", Item::new, new Item.Properties());
	public static final Item STAR_DRILL_BIT = item("star_drill_bit", Item::new, new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON));

	public static final Item HAND_DRILL = item("hand_drill",
			properties -> new DrillItem(properties, DrillItem.Engine.HAND_CRANK, false, false),
			DrillItem.properties(HAND_DRILL_MATERIAL, -3.0f));
	public static final Item STEEL_DRILL = item("steel_drill",
			properties -> new DrillItem(properties, DrillItem.Engine.KEROSENE, true, false),
			DrillItem.properties(STEEL_DRILL_MATERIAL, -3.1f).rarity(Rarity.UNCOMMON));
	public static final Item STAR_DRILL = item("star_drill",
			properties -> new DrillItem(properties, DrillItem.Engine.HEAVY, true, true),
			DrillItem.properties(STAR_DRILL_MATERIAL, -3.2f).fireResistant().rarity(Rarity.EPIC));

	private Drills() {
	}

	private static <T> DataComponentType<T> component(String name, Codec<T> codec, StreamCodec<? super ByteBuf, T> stream) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MyMod.id(name),
				DataComponentType.<T>builder().persistent(codec).networkSynchronized(stream).build());
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
		DrillArea.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Meteorites.STAR_STEEL_HOE, HAND_DRILL, STEEL_DRILL, Tungsten.TUNGSTEN_CARBIDE_DRILL,
						STAR_DRILL));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Meteorites.STAR_CRYSTAL_SHARD, IRON_DRILL_BIT, STEEL_DRILL_BIT, CARBIDE_DRILL_BIT,
						STAR_DRILL_BIT));
	}
}
