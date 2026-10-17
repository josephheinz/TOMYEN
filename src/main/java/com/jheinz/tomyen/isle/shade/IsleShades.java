package com.jheinz.tomyen.isle.shade;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.levelgen.Heightmap;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.metal.HintItem;
import com.jheinz.tomyen.soul.Souls;

/**
 * The Isle of the Damned, pass 2: its shades and the Soul Tide.
 *
 * <ul>
 * <li>{@link SoulRecorder} notes the world's notable deaths as {@link DeathRecord}s and counts the routine ones into
 * the Soul Tide, all in {@link IsleSouls} (fixed-size saved data, never entities).
 * <li>{@link ShadeManager} raises {@link Shade}s from those records, and waves of them from the tide, round players on
 * the Isle, and dissolves them when the players go.
 * <li>Shades drop {@link #SOUL_WISP}s (and a little Ectoplasm), and boss shades a Soul Charge and their
 * {@link #SHADE_HEART} too (see {@link ShadeLoot}; the heart's use is in {@code PayingRespects}).
 * <li>{@link SoulWisp}s drift about the Isle on their own, its only natural spawn.
 * </ul>
 */
public final class IsleShades {
	// --- Tags ---------------------------------------------------------------------------------------------------------
	/** Every death of these is remembered (bosses and elites); named mobs always are too. */
	public static final TagKey<EntityType<?>> NOTABLE_DEAD = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("notable_dead"));
	/** Their records go in the boss ring and come back as boss shades: rare, strong, with a boss bar and the best loot. */
	public static final TagKey<EntityType<?>> SHADE_BOSSES = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("shade_bosses"));
	/** Their shades keep their distance and lash with soul fire instead of closing in. */
	public static final TagKey<EntityType<?>> SHADE_CASTERS = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("shade_casters"));
	/** No soul to send down: machines, and the Isle's own spirits. */
	public static final TagKey<EntityType<?>> SOULLESS = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("soulless"));

	// --- Entities -----------------------------------------------------------------------------------------------------
	/** Never saved: {@link ShadeManager} raises them round players on the Isle and dissolves them when they go. */
	public static final EntityType<Shade> SHADE = entity("shade", EntityType.Builder.of(Shade::new, MobCategory.MONSTER)
			.sized(0.6f, 1.8f)
			.eyeHeight(1.5f)
			.clientTrackingRange(8)
			.fireImmune()
			.noSave()
			.noLootTable());
	public static final EntityType<SoulWisp> SOUL_WISP_ENTITY = entity("soul_wisp", EntityType.Builder.of(SoulWisp::new, MobCategory.AMBIENT)
			.sized(0.4f, 0.4f)
			.eyeHeight(0.2f)
			.clientTrackingRange(6)
			.fireImmune()
			.noLootTable());

	// --- Items --------------------------------------------------------------------------------------------------------
	/** What a shade leaves when it's beaten: a mote of soul fire. Three and a soul sand or soul soil make a Soul Charge. */
	public static final Item SOUL_WISP = item("soul_wisp", p -> new HintItem(p, List.of("item.tomyen.soul_wisp.hint")),
			new Item.Properties());
	/** A boss shade's, one each: offered at your gravestone, it draws your hoard up a depth. */
	public static final Item SHADE_HEART = item("shade_heart", p -> new HintItem(p, List.of("item.tomyen.shade_heart.hint")),
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	// --- Sounds (vanilla ones, re-pitched: see sounds.json) -----------------------------------------------------------
	public static final SoundEvent SHADE_AMBIENT = sound("shade_ambient");
	public static final SoundEvent SHADE_HURT = sound("shade_hurt");
	public static final SoundEvent SHADE_DEATH = sound("shade_death");
	public static final SoundEvent SHADE_FORM = sound("shade_form");
	public static final SoundEvent SHADE_DISSOLVE = sound("shade_dissolve");
	public static final SoundEvent SHADE_LASH = sound("shade_lash");
	public static final SoundEvent BOSS_SHADE_RISE = sound("boss_shade_rise");
	public static final SoundEvent BOSS_SHADE_NOVA = sound("boss_shade_nova");
	public static final SoundEvent SOUL_TIDE_SURGE = sound("soul_tide_surge");
	public static final SoundEvent PET_ECHO = sound("pet_echo");
	public static final SoundEvent SOUL_WISP_AMBIENT = sound("soul_wisp_ambient");
	public static final SoundEvent SOUL_WISP_DEATH = sound("soul_wisp_death");

	// --- On players ---------------------------------------------------------------------------------------------------
	/** How high the Soul Tide runs (0 to 1), for the HUD, fog and soul motes; only while they're on the Isle. */
	public static final AttachmentType<Float> TIDE = AttachmentRegistry.<Float>builder()
			.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("isle_soul_tide"));

	private IsleShades() {
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
		FabricDefaultAttributeRegistry.register(SHADE, Shade.createAttributes());
		FabricDefaultAttributeRegistry.register(SOUL_WISP_ENTITY, SoulWisp.createAttributes());
		SpawnPlacements.register(SOUL_WISP_ENTITY, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING, SoulWisp::checkSpawnRules);
		SoulRecorder.initialize();
		ShadeManager.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Souls.SOUL_CHARGE, SOUL_WISP, SHADE_HEART));
	}
}
