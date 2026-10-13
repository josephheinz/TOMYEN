package com.jheinz.mymod.cathode;

import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.mymod.MyMod;

/**
 * The Cathode Walker, a CRT television strutting about on human legs (see {@link CathodeWalker}): its entity, its
 * Static Burst payload, its sounds and the short-circuit damage water does it; its drop, the Cathode Tube, which goes
 * into computer chips, the Neuralink's Bio-Monitor module and a Mk I power armor helmet's HUD (alternative recipes,
 * data/mymod/recipe/*_from_cathode_tube.json), and the CRT Television block made from one.
 *
 * <p>A common-ish night monster, rarer than a zombie: spawn weight {@value #SPAWN_WEIGHT} (zombies are 95) in ones and
 * twos, anywhere in the Overworld dark enough for monsters (the surface at night, caves any time) but the mushroom
 * fields, and never out in the rain.
 */
public final class CathodeWalkers {
	private static final int SPAWN_WEIGHT = 20;

	public static final EntityType<CathodeWalker> CATHODE_WALKER = entity("cathode_walker",
			EntityType.Builder.of(CathodeWalker::new, MobCategory.MONSTER)
					.sized(0.9f, 1.8f)
					.eyeHeight(1.45f)
					.clientTrackingRange(8)
					.notInPeaceful());

	public static final Item CATHODE_TUBE = item("cathode_tube", Item::new, new Item.Properties());
	public static final Item CATHODE_WALKER_SPAWN_EGG = item("cathode_walker_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(CATHODE_WALKER));

	public static final Block CRT_TELEVISION = block("crt_television", CrtTelevisionBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD)
			.lightLevel(state -> state.getValue(CrtTelevisionBlock.LIT) ? CrtTelevisionBlock.LIGHT : 0));

	/** Water's damage to it (data/mymod/damage_type/short_circuit.json). */
	public static final ResourceKey<DamageType> SHORT_CIRCUIT = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("short_circuit"));

	/** Static's hiss and a mains hum. */
	public static final SoundEvent AMBIENT = sound("cathode_walker_ambient");
	/** Bare feet slapping. */
	public static final SoundEvent STEP = sound("cathode_walker_step");
	public static final SoundEvent KICK = sound("cathode_walker_kick");
	/** The screen brightening for a Static Burst. */
	public static final SoundEvent CHARGE = sound("cathode_walker_charge");
	public static final SoundEvent STATIC_BURST = sound("cathode_walker_static_burst");
	/** Sparking and fizzing in the wet. */
	public static final SoundEvent SHORT = sound("cathode_walker_short");
	public static final SoundEvent HURT = sound("cathode_walker_hurt");
	/** The screen imploding. */
	public static final SoundEvent DEATH = sound("cathode_walker_death");
	public static final SoundEvent POP = sound("cathode_walker_pop");
	/** The set clicking off as it dies. */
	public static final SoundEvent CLICK_OFF = sound("cathode_walker_click_off");
	public static final SoundEvent TELEVISION_TOGGLE = sound("crt_television_toggle");

	private CathodeWalkers() {
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		item(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(CATHODE_WALKER, CathodeWalker.createAttributes());
		SpawnPlacements.register(CATHODE_WALKER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				CathodeWalker::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld().and(BiomeSelectors.excludeByKey(Biomes.MUSHROOM_FIELDS)),
				MobCategory.MONSTER, CATHODE_WALKER, SPAWN_WEIGHT, 1, 2);
		PayloadTypeRegistry.clientboundPlay().register(StaticBurstPayload.TYPE, StaticBurstPayload.CODEC);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SHEEP_SPAWN_EGG, CATHODE_WALKER_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.COPPER_INGOT, CATHODE_TUBE));
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks")))
				.register(output -> output.insertAfter(Items.JUKEBOX, CRT_TELEVISION));
	}
}
