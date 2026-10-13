package com.jheinz.mymod.napalm;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.industry.Industry;

/**
 * Napalm: the M2 flamethrower and its two Weapon Station parts, the napalm grenade, the burning gel puddles napalm
 * leaves, the flame and heat haze particles, sounds and damage types, and the state the flamethrower keeps on its
 * stack. Napalm Canisters come from the Refinery ({@link Industry#NAPALM_CANISTER}); the napalm PG-7 lives with
 * the other rockets in {@link com.jheinz.mymod.launcher.Launchers}; the burning effect is
 * {@link com.jheinz.mymod.ModEffects#NAPALM}.
 */
public final class Napalm {
	/**
	 * On a flamethrower: the game time it last sprayed, so every client can draw the stream and play the roar while it
	 * keeps changing (see {@link FlamethrowerItem#firing}). Not saved.
	 */
	public static final DataComponentType<Long> FLAME_FIRED_AT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("flame_fired_at"), DataComponentType.<Long>builder().networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	public static final SoundEvent FLAMETHROWER_ROAR = sound("flamethrower_roar");
	public static final SoundEvent FLAMETHROWER_IGNITE = sound("flamethrower_ignite");

	/** The stream itself and napalm splashes: fire, so fire resistance and fire-immune mobs shrug it off. */
	public static final ResourceKey<DamageType> NAPALM = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("napalm"));
	/** The Napalm effect's burn: past armor and the hurt cooldown, so it always adds to ordinary burning. */
	public static final ResourceKey<DamageType> NAPALM_BURN = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("napalm_burn"));

	/** A ball of burning napalm in the stream, drawn on the client along each glob's path. */
	public static final SimpleParticleType NAPALM_FLAME =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("napalm_flame"), FabricParticleTypes.simple(true));
	/** The shimmer of hot air rising off the stream. */
	public static final SimpleParticleType HEAT_HAZE =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("heat_haze"), FabricParticleTypes.simple(false));

	public static final Block BURNING_GEL = block("burning_gel", BurningGelBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).replaceable().noCollision().instabreak().noLootTable()
			.lightLevel(state -> 13).sound(SoundType.HONEY_BLOCK).pushReaction(PushReaction.POPPED));

	public static final EntityType<NapalmGrenade> GRENADE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("napalm_grenade")),
			EntityType.Builder.<NapalmGrenade>of(NapalmGrenade::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.25f, 0.25f)
					.clientTrackingRange(4)
					.updateInterval(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("napalm_grenade"))));

	/** The backpack: twin fuel tanks and the pressure bottle between them, with the hose. */
	public static final Item FLAMETHROWER_TANKS = item("flamethrower_tanks", Item::new, new Item.Properties().stacksTo(1));
	/** The wand: nozzle, ignition cylinder, valve and grips. */
	public static final Item FLAMETHROWER_WAND = item("flamethrower_wand", Item::new, new Item.Properties().stacksTo(1));

	/**
	 * Full-auto only, one spray a tick (the client repeats use every tick while it's held). The stats' spread is the
	 * stream's cone ({@link FlameStream}), recoil a gentle drift; range is for reference (the stream is simulated, not
	 * hitscan, and reaches 10 to 14 blocks); the reload is swapping a canister.
	 */
	public static final Item FLAMETHROWER = item("flamethrower",
			properties -> new FlamethrowerItem(properties,
					new GunStats(FLAMETHROWER_IGNITE, 0.04f, 14, 50, List.of(FireMode.AUTO)).withAutoTicks(1).withSpread(3.0f, 0.0f)),
			new Item.Properties().durability(1200).repairable(Items.IRON_INGOT).rarity(Rarity.UNCOMMON));

	/** Bursts into a splash of burning gel where it lands. */
	public static final Item NAPALM_GRENADE = item("napalm_grenade", NapalmGrenadeItem::new, new Item.Properties().stacksTo(8));

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private Napalm() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	/** A block with no item: gel only comes from napalm. */
	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			// After the shotguns; the grenade with the other throwables, the canister with the other ammo.
			output.insertAfter(ModItems.SPAS12_BARREL, FLAMETHROWER, FLAMETHROWER_TANKS, FLAMETHROWER_WAND);
			output.insertAfter(Items.WIND_CHARGE, NAPALM_GRENADE);
			output.insertBefore(Items.SPECTRAL_ARROW, Industry.NAPALM_CANISTER);
		});
		DispenserBlock.registerProjectileBehavior(NAPALM_GRENADE);
		FlameStream.initialize();
	}
}
