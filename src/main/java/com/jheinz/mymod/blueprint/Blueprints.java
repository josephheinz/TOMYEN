package com.jheinz.mymod.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.mymod.ModCreativeTabs;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.blueprint.Blueprint.Station;
import com.jheinz.mymod.blueprint.Blueprint.Tier;
import com.jheinz.mymod.gunsmith.Gunsmith;

/**
 * Blueprints: schematics found in loot. Reading one (right-click) teaches its recipe for good, which lets you make
 * something a tier early out of cheaper parts. They're a shortcut and a reward for exploring, never a requirement: every
 * item they unlock still has its normal route.
 * <ul>
 * <li>Workshop blueprints (blue paper) teach Weapon Station guns at the Scrap Bench: the Flare Gun, the Nail Gun, the
 * Snub Revolver and the Sawed-off.</li>
 * <li>Foundry blueprints (amber paper) teach Foundry guns and attachments at the Weapon Station from iron, springs and
 * screws instead of steel and chips: the M1911, the .357, the red dot, the suppressor, the muzzle brake and the vertical
 * grip.</li>
 * </ul>
 * Locked recipes still show, greyed out, in both stations' lists, so players know what to look for.
 * See {@link BlueprintLoot} for where they turn up.
 */
public final class Blueprints {
	// --- Data -------------------------------------------------------------------------------------------------------
	/** On a Blueprint: the id of the blueprint it carries (one of {@link #ALL}). */
	public static final DataComponentType<Identifier> BLUEPRINT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("blueprint"), DataComponentType.<Identifier>builder().persistent(Identifier.CODEC)
					.networkSynchronized(Identifier.STREAM_CODEC).build());

	/** On a player: every blueprint they've read. Kept through death; synced to them alone so their stations can grey out the rest. */
	public static final AttachmentType<List<Identifier>> KNOWN = AttachmentRegistry.<List<Identifier>>builder()
			.persistent(Identifier.CODEC.listOf())
			.copyOnDeath()
			.syncWith(Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("known_blueprints"));

	// --- The blueprints ---------------------------------------------------------------------------------------------
	private static final Map<Identifier, Blueprint> BY_ID = new LinkedHashMap<>();

	public static final Blueprint FLARE_GUN = define("flare_gun", Gunsmith.FLARE_GUN, Station.SCRAP_BENCH, Tier.WORKSHOP);
	public static final Blueprint NAIL_GUN = define("nail_gun", Gunsmith.NAIL_GUN, Station.SCRAP_BENCH, Tier.WORKSHOP);
	public static final Blueprint SNUB_REVOLVER = define("snub_revolver", Gunsmith.SNUB_REVOLVER, Station.SCRAP_BENCH, Tier.WORKSHOP);
	public static final Blueprint SAWED_OFF = define("sawed_off", Gunsmith.SAWED_OFF, Station.SCRAP_BENCH, Tier.WORKSHOP);
	public static final Blueprint M1911 = define("m1911", ModItems.M1911, Station.WEAPON_STATION, Tier.FOUNDRY);
	public static final Blueprint REVOLVER_357 = define("revolver_357", ModItems.REVOLVER_357, Station.WEAPON_STATION, Tier.FOUNDRY);
	public static final Blueprint RED_DOT_SIGHT = define("red_dot_sight", ModItems.RED_DOT_SIGHT, Station.WEAPON_STATION, Tier.FOUNDRY);
	public static final Blueprint SUPPRESSOR = define("suppressor", ModItems.SUPPRESSOR, Station.WEAPON_STATION, Tier.FOUNDRY);
	public static final Blueprint MUZZLE_BRAKE = define("muzzle_brake", ModItems.MUZZLE_BRAKE, Station.WEAPON_STATION, Tier.FOUNDRY);
	public static final Blueprint VERTICAL_GRIP = define("vertical_grip", ModItems.VERTICAL_GRIP, Station.WEAPON_STATION, Tier.FOUNDRY);

	public static final List<Blueprint> ALL = List.copyOf(BY_ID.values());

	// --- Item -------------------------------------------------------------------------------------------------------
	public static final Item BLUEPRINT_ITEM = register();

	private Blueprints() {
	}

	private static Blueprint define(String name, Item result, Station station, Tier tier) {
		Blueprint blueprint = new Blueprint(MyMod.id(name), result, station, tier);
		BY_ID.put(blueprint.id(), blueprint);
		return blueprint;
	}

	private static Item register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id("blueprint"));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlueprintItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON).setId(key)));
	}

	public static @Nullable Blueprint get(@Nullable Identifier id) {
		return id == null ? null : BY_ID.get(id);
	}

	/** The blueprint a stack carries, if any. */
	public static @Nullable Blueprint of(ItemStack stack) {
		return stack.is(BLUEPRINT_ITEM) ? get(stack.get(BLUEPRINT)) : null;
	}

	public static ItemStack stack(Blueprint blueprint) {
		ItemStack stack = new ItemStack(BLUEPRINT_ITEM);
		stack.set(BLUEPRINT, blueprint.id());
		return stack;
	}

	public static List<Blueprint> ofTier(@Nullable Tier tier) {
		return tier == null ? ALL : ALL.stream().filter(blueprint -> blueprint.tier() == tier).toList();
	}

	/** Whether this player has read the blueprint (works on the client too: the list is synced to its owner). */
	public static boolean knows(Player player, Identifier id) {
		return player.getAttachedOrElse(KNOWN, List.of()).contains(id);
	}

	/** Teaches the player a blueprint; false if they already knew it. Server side. */
	public static boolean learn(Player player, Identifier id) {
		List<Identifier> known = player.getAttachedOrElse(KNOWN, List.of());
		if (known.contains(id)) {
			return false;
		}
		List<Identifier> updated = new ArrayList<>(known);
		updated.add(id);
		player.setAttached(KNOWN, List.copyOf(updated));
		return true;
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, MyMod.id("random_blueprint"), RandomBlueprintFunction.MAP_CODEC);
		BlueprintLoot.initialize();
		// One of each in the creative tabs; the blank one only in My Mod (where every item is listed). The stacks are built
		// when the tab fills, since item components aren't bound yet during mod initialization.
		CreativeModeTabEvents.modifyOutputEvent(ModCreativeTabs.MY_MOD_TAB_KEY)
				.register(output -> output.insertAfter(BLUEPRINT_ITEM, ALL.stream().map(Blueprints::stack).toList()));
		// Next to the smithing templates, the other "schematics" in vanilla.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output ->
				output.insertBefore(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, ALL.stream().map(Blueprints::stack).toList()));
	}
}
