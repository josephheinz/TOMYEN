package com.jheinz.tomyen.alien;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

import com.jheinz.tomyen.MyMod;

/**
 * The UFO invasion: flying saucers that roam the night sky abducting livestock, land to let their crews out, and carry
 * off people in their sleep (see {@link Ufo}, {@link Alien}, {@link UfoSightings} and {@link Abductions}). Their
 * entities and the tractor beam, their sounds, the Probed effect, and what's left when one's shot down: Alien Alloy
 * (Mk III power armor plating, the Railgun's body) and the Alien Reactor (the Railgun's core, or a pair of charged
 * Fusion Cores from the Foundry). Aliens also drop the odd Ray Gun ({@link com.jheinz.tomyen.energy.EnergyWeapons}).
 *
 * <p>Aliens are technological, not evil: they aren't in the {@code tomyen:evil} or {@code tomyen:holy_vulnerable} tags.
 */
public final class Aliens {
	public static final EntityType<Ufo> UFO = entity("ufo", EntityType.Builder.of(Ufo::new, MobCategory.MONSTER)
			.sized(Ufo.WIDTH, Ufo.HEIGHT)
			.eyeHeight(0.3f)
			.fireImmune()
			// Seen from far off.
			.clientTrackingRange(16)
			.updateInterval(1)
			.notInPeaceful());
	public static final EntityType<Alien> ALIEN = entity("alien", EntityType.Builder.of(Alien::new, MobCategory.CREATURE)
			.sized(0.6f, 1.35f)
			.eyeHeight(1.0f)
			.clientTrackingRange(10));
	/** What a tractor beam's victim rides: invisible, never summoned, every tick. */
	public static final EntityType<TractorBeam> TRACTOR_BEAM = entity("tractor_beam",
			EntityType.Builder.<TractorBeam>of(TractorBeam::new, MobCategory.MISC)
					.noLootTable()
					.noSummon()
					.fireImmune()
					.sized(0.2f, 0.2f)
					.clientTrackingRange(16)
					.updateInterval(1));

	public static final Item ALIEN_ALLOY = item("alien_alloy", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item ALIEN_REACTOR = item("alien_reactor", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	public static final Item UFO_SPAWN_EGG = item("ufo_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(UFO));
	public static final Item ALIEN_SPAWN_EGG = item("alien_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ALIEN));

	public static final Holder<MobEffect> PROBED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("probed"), new ProbedEffect());

	/** The saucer's hum, a wobbling theremin note over a low drone: looped by the client while one's near. */
	public static final SoundEvent UFO_HUM = sound("ufo_hum");
	public static final SoundEvent UFO_BEAM = sound("ufo_beam");
	/** Something taken aboard. */
	public static final SoundEvent UFO_TAKE = sound("ufo_take");
	public static final SoundEvent UFO_LAND = sound("ufo_land");
	public static final SoundEvent UFO_DOOR = sound("ufo_door");
	public static final SoundEvent UFO_HURT = sound("ufo_hurt");
	public static final SoundEvent UFO_DEATH = sound("ufo_death");
	public static final SoundEvent ALIEN_AMBIENT = sound("alien_ambient");
	public static final SoundEvent ALIEN_HURT = sound("alien_hurt");
	public static final SoundEvent ALIEN_DEATH = sound("alien_death");
	/** The eerie rising sting as someone's carried off. */
	public static final SoundEvent ABDUCTION = sound("abduction");

	private Aliens() {
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
		FabricDefaultAttributeRegistry.register(UFO, Ufo.createAttributes());
		FabricDefaultAttributeRegistry.register(ALIEN, Alien.createAttributes());
		UfoSightings.initialize();
		Abductions.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SHEEP_SPAWN_EGG, UFO_SPAWN_EGG, ALIEN_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.NETHERITE_INGOT, ALIEN_ALLOY, ALIEN_REACTOR));
	}
}
