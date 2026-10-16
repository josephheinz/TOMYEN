package com.jheinz.tomyen.scarecrow;

import java.util.EnumMap;
import java.util.Map;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.scarecrow.mixin.MobTargetSelectorAccessor;
import com.jheinz.tomyen.survival.Survival;

/**
 * Scarecrows ({@link Scarecrow}): the entity, its three items (one per variant), the saucer's disappointed chirp, and
 * the hook that gives hostile mobs their {@link ScarecrowLureGoal}. The Scavenger tier's answer to cattle rustling
 * UFOs, and a cheap distraction in a fight.
 */
public final class Scarecrows {
	/** Mobs that fall hardest for a scarecrow: the undead and werewolves. */
	public static final TagKey<EntityType<?>> HATERS = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("scarecrow_haters"));
	/** Hostile mobs that never fall for one (bosses and the like). */
	public static final TagKey<EntityType<?>> IGNORES_SCARECROWS = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("ignores_scarecrows"));

	public static final EntityType<Scarecrow> SCARECROW;

	static {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("scarecrow"));
		SCARECROW = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(Scarecrow::new, MobCategory.MISC)
				.noLootTable()
				.sized(Scarecrow.WIDTH, Scarecrow.HEIGHT)
				.eyeHeight(2.0f)
				.clientTrackingRange(10)
				.build(key));
	}

	private static final Map<Scarecrow.Variant, Item> ITEMS = new EnumMap<>(Scarecrow.Variant.class);
	public static final Item SCARECROW_ITEM = register(Scarecrow.Variant.STRAW);
	public static final Item PUMPKIN_SCARECROW = register(Scarecrow.Variant.PUMPKIN);
	public static final Item SCRAP_SCARECROW = register(Scarecrow.Variant.SCRAP);

	/** A saucer's crew finding out what they've caught: a deflated, warbling "hmph". */
	public static final SoundEvent UFO_DISAPPOINTED = sound("ufo_disappointed");

	private Scarecrows() {
	}

	private static Item register(Scarecrow.Variant variant) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(variant.id()));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new ScarecrowItem(new Item.Properties().stacksTo(16).setId(key), variant));
		ITEMS.put(variant, item);
		return item;
	}

	static Item item(Scarecrow.Variant variant) {
		return ITEMS.get(variant);
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(SCARECROW, Scarecrow.createAttributes());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob instanceof Enemy) {
				addLureGoal(mob);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.ARMOR_STAND, SCARECROW_ITEM, PUMPKIN_SCARECROW, SCRAP_SCARECROW));
	}

	/**
	 * Gives a hostile mob that picks its targets with goals the scarecrow lure, ahead of its other target goals. Mobs
	 * run by a brain (piglins, wardens) or with no target goals at all (UFOs) are left alone.
	 */
	private static void addLureGoal(Mob mob) {
		if (mob.is(IGNORES_SCARECROWS)) {
			return;
		}
		GoalSelector targets = ((MobTargetSelectorAccessor) mob).tomyen$getTargetSelector();
		if (targets.getAvailableGoals().isEmpty()
				|| targets.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof ScarecrowLureGoal)) {
			return;
		}
		targets.addGoal(0, new ScarecrowLureGoal(mob, mob.is(HATERS), mob.is(Survival.MACHINES)));
	}
}
