package com.jheinz.tomyen.hag;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.BaitItem;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.PartSlot;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.RodPartItem;
import com.jheinz.tomyen.herbalism.HerbalEffect;
import com.jheinz.tomyen.herbalism.HerbalItem;

/**
 * The Bog Hag (Workshop tier, one of the evil things abroad): a rotten swamp witch who walks the swamps and mangroves
 * on dark nights, alone and rarely. She keeps her distance, flings gobs of hexed muck ({@link HexGlob}) that lay
 * {@link HagsCurse Hag's Curse} on whoever they hit, raps anyone who gets too close with her staff, and calls slimes
 * up out of the bog when she's pressed (see {@link BogHag}).
 *
 * <p>What comes of her:
 * <ul>
 * <li><b>Hag's Curse</b>: a few minutes of bad luck at the water and in the mine (see {@link HagsCurse}). Milk won't
 * lift it.</li>
 * <li><b>Hexbreaker Tea</b>: water mint steeped in the herbalism Kettle. It lifts the curse, and its Hex Ward keeps a
 * new one from taking hold for five minutes.</li>
 * <li><b>Hag Charm</b> (her rare drop): a knot of bone, moss and twine that ties on at the Tackle Bench as a lure.
 * The hag's luck turns your way: rarer fish, more so at night, and cursed catches a step easier.</li>
 * <li><b>Bog Muck</b>: what her hexes are made of, what the curse turns your mining into, and a bait the bottom
 * feeders love (best in rain and murky water). It also composts, and four of it make mud.</li>
 * </ul>
 * Silver and holy things hurt her extra: she's in {@code #tomyen:holy_vulnerable} (and so {@code #tomyen:evil}).
 */
public final class BogHags {
	/** Witches are 5 in swamps; she's rarer still, and only at night (see {@link BogHag#checkSpawnRules}). */
	private static final int SPAWN_WEIGHT = 3;

	// --- Effects --------------------------------------------------------------------------------------------------
	public static final Holder<MobEffect> HAGS_CURSE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("hags_curse"),
			new HagsCurseEffect());
	/** From Hexbreaker Tea: a Hag's Curse can't take hold. */
	public static final Holder<MobEffect> HEX_WARD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("hex_ward"),
			new HerbalEffect(0x7FC0A0));

	public static final ConsumeEffect.Type<HagsCurse.Lift> LIFT_HAGS_CURSE = Registry.register(BuiltInRegistries.CONSUME_EFFECT_TYPE,
			MyMod.id("lift_hags_curse"), new ConsumeEffect.Type<>(HagsCurse.Lift.CODEC, HagsCurse.Lift.STREAM_CODEC));

	// --- Entities -------------------------------------------------------------------------------------------------
	public static final EntityType<BogHag> BOG_HAG = entity("bog_hag", EntityType.Builder.of(BogHag::new, MobCategory.MONSTER)
			// Hunched, she stands about a block and three quarters; her staff reaches higher.
			.sized(0.6f, 1.75f)
			.eyeHeight(1.45f)
			.clientTrackingRange(8)
			.notInPeaceful());
	public static final EntityType<HexGlob> HEX_GLOB = entity("hex_glob", EntityType.Builder.<HexGlob>of(HexGlob::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.3f, 0.3f)
			.clientTrackingRange(6)
			.updateInterval(5));

	// --- Items ----------------------------------------------------------------------------------------------------
	/** Swamp-bottom ooze with a sickly sheen. A bait for the bottom feeders, compost, and mud. */
	public static final Item BOG_MUCK = item("bog_muck", properties -> new BaitItem(properties, new BaitItem.Effect(
			RodModifiers.builder().rainBiteTicks(80).biteTicks(10).build(), 0.0f, Fishing.BOTTOM_FISH, 2.0f, 0.1f, false, 0)),
			new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
	/** A rod lure: the hag's luck, turned your way. */
	public static final Item HAG_CHARM = item("hag_charm", properties -> new RodPartItem(properties, PartSlot.LURE,
			RodModifiers.builder().rarity(0.15f).nightRarity(0.15f).cursedBonus().build()), new Item.Properties().rarity(Rarity.UNCOMMON));
	/** Water mint steeped in the Kettle: lifts a Hag's Curse and wards off the next for a while. */
	public static final Item HEXBREAKER_TEA = item("hexbreaker_tea", HerbalItem::new, new Item.Properties().stacksTo(16)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, tea(HagsCurse.Lift.INSTANCE,
					new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(HEX_WARD, HagsCurse.WARD_TICKS))))));
	public static final Item BOG_HAG_SPAWN_EGG = item("bog_hag_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(BOG_HAG));

	// --- Sounds (vanilla witch sounds, pitched down and rasped up) ------------------------------------------------
	public static final SoundEvent AMBIENT = sound("bog_hag_ambient");
	/** Her cackle as a hex lands, or as she calls the slimes up. */
	public static final SoundEvent CACKLE = sound("bog_hag_cackle");
	public static final SoundEvent CAST = sound("bog_hag_cast");
	public static final SoundEvent SUMMON = sound("bog_hag_summon");
	public static final SoundEvent HURT = sound("bog_hag_hurt");
	public static final SoundEvent DEATH = sound("bog_hag_death");
	/** A hexed glob bursting on something. */
	public static final SoundEvent HEX_SPLAT = sound("hex_splat");
	/** A curse falling on someone, or a ward turning one away. */
	public static final SoundEvent CURSED = sound("hags_curse");

	private BogHags() {
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

	private static Consumable tea(ConsumeEffect... effects) {
		Consumable.Builder drink = Consumables.defaultDrink();
		for (ConsumeEffect effect : effects) {
			drink.onConsume(effect);
		}
		return drink.build();
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(BOG_HAG, BogHag.createAttributes());
		SpawnPlacements.register(BOG_HAG, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BogHag::checkSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP), MobCategory.MONSTER, BOG_HAG,
				SPAWN_WEIGHT, 1, 1);

		// A Hex Ward turns a new curse away; milk and totems don't lift one (only Hexbreaker Tea, or a command).
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, context) ->
				context.isFromCommand() || !effect.is(HAGS_CURSE) || !entity.hasEffect(HEX_WARD));
		ServerMobEffectEvents.ALLOW_EARLY_REMOVE.register((effect, entity, context) ->
				!effect.is(HAGS_CURSE) || context.isFromCommand() || HagsCurse.lifting() || !entity.isAlive());
		LootTableEvents.MODIFY_DROPS.register(HagsCurse::modifyDrops);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.WITCH_SPAWN_EGG, BOG_HAG_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.SLIME_BALL, BOG_MUCK, HAG_CHARM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(output -> output.insertAfter(Items.HONEY_BOTTLE, HEXBREAKER_TEA));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Fishing.STAR_BAIT, BOG_MUCK, HAG_CHARM));
	}
}
