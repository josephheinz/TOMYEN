package com.jheinz.tomyen.gunsmith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.MagazineItem;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.attachment.AttachmentItem;
import com.jheinz.tomyen.attachment.AttachmentSlot;
import com.jheinz.tomyen.boar.Boars;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.shoddy.ShoddyGuns;
import com.jheinz.tomyen.shotgun.ShotgunItem.Handling;
import com.jheinz.tomyen.shotgun.ShotgunItem.Shell;
import com.jheinz.tomyen.silver.EvilBane;
import com.jheinz.tomyen.silver.Silver;
import com.jheinz.tomyen.skullcrane.Skullcranes;
import com.jheinz.tomyen.station.StationRecipe;

/**
 * The Gunsmith set: more guns for the early and middle of the game, each with its own job, and attachments that plug into
 * the existing slots. All load and handle through the shared gun systems (ShotgunItem's one-at-a-time loading for most,
 * MagazineItem for the Nail Gun).
 * <ul>
 * <li><b>Slingshot</b> (Scavenger): a forked stick and a rubber band. Iron nuggets, flint or a pinch of gravel, quiet and
 * weak.</li>
 * <li><b>Flare Gun</b> (Workshop, or Scavenger with its blueprint): a break-open signal pistol. Sets what it hits
 * alight; lights up the sky; can draw a saucer.</li>
 * <li><b>Snub Revolver</b> (Workshop): a cheap five-shot .38 sidearm, before the .357.</li>
 * <li><b>Sawed-off</b> (Workshop): two 12 gauge barrels, one or both at once.</li>
 * <li><b>Lever-Action Rifle</b> (Workshop): eight .38 Special in a tube, the lever worked after every shot; takes the
 * hunting scopes.</li>
 * <li><b>Nail Gun</b> (Workshop): a scrap nailer driven by a Skullcrane servo arm. Fast, quiet, short-ranged.</li>
 * <li><b>Harpoon Gun</b> (Foundry): a spear on a line that hauls its target in, best underwater, and reels in fish.</li>
 * </ul>
 */
public final class Gunsmith {
	// ------------------------------------------------------------------------------------------------------ sounds

	// Recordings from the packs (tools/import_gun_sounds.py); the Slingshot's is vanilla's bow, there being no catapult.
	public static final SoundEvent SLINGSHOT_SHOT = sound("slingshot_shot");
	public static final SoundEvent SLINGSHOT_DRAW = sound("slingshot_draw");
	public static final SoundEvent FLARE_GUN_SHOT = sound("flare_gun_shot");
	public static final SoundEvent SNUB_REVOLVER_SHOT = sound("snub_revolver_shot");
	public static final SoundEvent SAWED_OFF_SHOT = sound("sawed_off_shot");
	public static final SoundEvent SAWED_OFF_DOUBLE_SHOT = sound("sawed_off_double_shot");
	public static final SoundEvent LEVER_ACTION_SHOT = sound("lever_action_shot");
	public static final SoundEvent NAIL_GUN_SHOT = sound("nail_gun_shot");
	public static final SoundEvent HARPOON_GUN_SHOT = sound("harpoon_gun_shot");
	/** A break action opening and snapping shut. */
	public static final SoundEvent BREAK_OPEN = sound("break_open");
	public static final SoundEvent BREAK_CLOSE = sound("break_close");
	/** The lever thrown down and brought home, and a round pushed through the loading gate. */
	public static final SoundEvent LEVER_DOWN = sound("lever_down");
	public static final SoundEvent LEVER_UP = sound("lever_up");
	public static final SoundEvent LEVER_LOAD = sound("lever_load");
	/** A bipod's legs swung down and folded. */
	public static final SoundEvent BIPOD_DEPLOY = sound("bipod_deploy");
	public static final SoundEvent BIPOD_FOLD = sound("bipod_fold");

	// -------------------------------------------------------------------------------------------------------- ammo

	/** .38 Special: the Snub Revolver's and the Lever-Action's round, drawn brass with a soft lead nose. */
	public static final Item BULLET_CASING_38 = item("bullet_casing_38");
	public static final Item BULLET_38_FMJ = item("bullet_38_fmj");
	/** Silver bites the evil, as in every other caliber (see EvilBane). */
	public static final Item BULLET_38_SILVER = register("bullet_38_silver", Item::new,
			new Item.Properties().component(ModComponents.EVIL_BANE, EvilBane.SILVER));
	/** Collated iron nails, packed into the Nail Gun's strip or coil. */
	public static final Item IRON_NAIL = item("iron_nail");
	/** A barbed steel spear for the Harpoon Gun; it comes back on the line, or can be pulled out of whatever it hit. */
	public static final Item HARPOON_SPEAR = register("harpoon_spear", Item::new, new Item.Properties().stacksTo(16));
	/** A signal cartridge: a paper hull of red-burning powder. */
	public static final Item FLARE_SHELL = item("flare_shell");

	// Shell(item, pellets, pellet damage, pellet spread, full damage to, falls off to, min factor, range, HUD colour)
	private static final List<Shell> SLINGSHOT_STONES = List.of(
			new Shell(Items.IRON_NUGGET, 1, 5.0f, 0.0f, 8.0, 24.0, 0.5f, 28.0, 0xA9AEB3),
			new Shell(Items.FLINT, 1, 4.0f, 0.0f, 8.0, 22.0, 0.5f, 26.0, 0x4A4A4E),
			new Shell(Items.GRAVEL, 4, 1.3f, 6.0f, 4.0, 12.0, 0.4f, 16.0, 0x8A8580));
	private static final List<Shell> SHELLS_12G = List.of(
			new Shell(ModItems.SHELL_12G_BUCKSHOT, 8, 2.5f, 4.5f, 8.0, 28.0, 0.3f, 40.0, 0xD8483A),
			new Shell(ModItems.SHELL_12G_SLUG, 1, 16.0f, 0.0f, 20.0, 60.0, 0.6f, 80.0, 0x5AA866),
			new Shell(ModItems.SHELL_12G_SILVER_BUCKSHOT, 8, 2.5f, 4.5f, 8.0, 28.0, 0.3f, 40.0, 0xC9D3E0),
			new Shell(ModItems.SHELL_12G_HOLY_SILVER_BUCKSHOT, 8, 2.5f, 4.5f, 8.0, 28.0, 0.3f, 40.0, 0xFFE9A0));
	/** The .38 from the rifle's long barrel: harder hitting and much further reaching than from the snub's two inches. */
	private static final List<Shell> ROUNDS_38_RIFLE = List.of(
			new Shell(BULLET_38_FMJ, 1, 7.0f, 0.0f, 32.0, 80.0, 0.7f, 96.0, 0xD8A84E),
			new Shell(BULLET_38_SILVER, 1, 7.0f, 0.0f, 32.0, 80.0, 0.7f, 96.0, 0xC9D3E0));
	private static final List<Shell> ROUNDS_38_SNUB = List.of(
			new Shell(BULLET_38_FMJ, 1, 5.5f, 0.0f, 10.0, 32.0, 0.55f, 40.0, 0xD8A84E),
			new Shell(BULLET_38_SILVER, 1, 5.5f, 0.0f, 10.0, 32.0, 0.55f, 40.0, 0xC9D3E0));
	// The tusk harpoon (Boars) is the cheap early spear: it hits softer and falls off sooner than steel.
	private static final List<Shell> SPEARS = List.of(new Shell(HARPOON_SPEAR, 1, 11.0f, 0.0f, 16.0, 32.0, 0.6f, 24.0, 0xB8C2C8),
			new Shell(Boars.TUSK_HARPOON, 1, 8.0f, 0.0f, 12.0, 24.0, 0.55f, 20.0, 0xE6DCC0));
	private static final List<Shell> FLARES = List.of(new Shell(FLARE_SHELL, 1, 4.0f, 0.0f, 64.0, 64.0, 1.0f, 64.0, 0xFF5A3A));
	public static final List<Ammo> AMMO_NAILS = List.of(new Ammo(IRON_NAIL, 3.5f, ModDamageTypes.BULLET, 0xA9AEB3));

	public static final MagazineItem NAIL_STRIP = magazine("nail_strip", 25, AMMO_NAILS, 0);
	/** A wound coil of collated nails: more than twice the strip, and a little slower to seat. */
	public static final MagazineItem NAIL_COIL = magazine("nail_coil", 60, AMMO_NAILS, 12);

	// --------------------------------------------------------------------------------------------------------- guns

	// Traits(ticks per round, ticks to close/cock, closes after every load, handgun, gunpowder, volume)
	public static final Item SLINGSHOT = register("slingshot",
			properties -> new GunsmithGunItem(properties,
					new GunStats(SLINGSHOT_SHOT, 0.6f, 28, 9, List.of(FireMode.SEMI)).withSingleRoundLoading().withSpread(1.4f, 1.2f),
					SLINGSHOT_STONES, 1, Map.of(FireMode.SEMI, new Handling(1.0f, 1.0f, 1.0f, 6)),
					new GunsmithGunItem.Traits(9, 0, false, false, false, 0.5f)),
			new Item.Properties().durability(150).repairable(Items.STRING));
	public static final Item FLARE_GUN = register("flare_gun",
			properties -> new FlareGunItem(properties,
					new GunStats(FLARE_GUN_SHOT, 2.5f, 64, 10, List.of(FireMode.SEMI)).withSingleRoundLoading().withSpread(0.8f, 1.0f),
					FLARES, 1, Map.of(FireMode.SEMI, new Handling(1.0f, 1.0f, 1.0f, 6)),
					new GunsmithGunItem.Traits(10, 8, true, true, false, 1.0f)),
			new Item.Properties().durability(250).repairable(Items.COPPER_INGOT));
	public static final Item SNUB_REVOLVER = register("snub_revolver",
			properties -> new GunsmithGunItem(properties,
					new GunStats(SNUB_REVOLVER_SHOT, 3.4f, 40, 9, List.of(FireMode.SEMI)).withSingleRoundLoading().withSpread(1.0f, 2.2f),
					ROUNDS_38_SNUB, 5, Map.of(FireMode.SEMI, new Handling(1.0f, 1.0f, 1.0f, 5)),
					new GunsmithGunItem.Traits(9, 8, true, true, true, 1.0f)),
			new Item.Properties().durability(600).repairable(Items.IRON_INGOT));
	public static final Item SAWED_OFF = register("sawed_off",
			properties -> new BreakActionGunItem(properties,
					new GunStats(SAWED_OFF_SHOT, 7.5f, 48, 9, List.of(FireMode.SEMI, FireMode.DOUBLE))
							.withSingleRoundLoading().withSpread(1.2f, 1.6f),
					SHELLS_12G, 2, Map.of(
							FireMode.SEMI, new Handling(1.0f, 1.45f, 1.0f, 6),
							FireMode.DOUBLE, new Handling(1.0f, 1.55f, 1.8f, 12)),
					new GunsmithGunItem.Traits(9, 9, true, false, true, 1.2f), SAWED_OFF_DOUBLE_SHOT),
			new Item.Properties().durability(500).repairable(Items.IRON_INGOT));
	public static final Item LEVER_ACTION = register("lever_action",
			properties -> new GunsmithGunItem(properties,
					new GunStats(LEVER_ACTION_SHOT, 3.0f, 96, 9, List.of(FireMode.LEVER)).withSingleRoundLoading().withSpread(0.5f, 1.2f),
					ROUNDS_38_RIFLE, 8, Map.of(FireMode.LEVER, new Handling(1.0f, 1.0f, 1.0f, 11)),
					new GunsmithGunItem.Traits(9, 11, false, false, true, 1.0f)),
			new Item.Properties().durability(900).repairable(Items.IRON_INGOT));
	public static final Item NAIL_GUN = register("nail_gun",
			properties -> new NailGunItem(properties,
					new GunStats(NAIL_GUN_SHOT, 0.6f, 24, 26, List.of(FireMode.SEMI)).withCycleTicks(3).withSpread(1.2f, 0.45f),
					List.of(NAIL_STRIP, NAIL_COIL), AMMO_NAILS),
			new Item.Properties().durability(700).repairable(Items.IRON_INGOT));
	public static final Item HARPOON_GUN = register("harpoon_gun",
			properties -> new HarpoonGunItem(properties,
					new GunStats(HARPOON_GUN_SHOT, 2.0f, 24, 16, List.of(FireMode.SEMI)).withSingleRoundLoading().withSpread(0.6f, 1.0f),
					SPEARS, 1, Map.of(FireMode.SEMI, new Handling(1.0f, 1.0f, 1.0f, 6)),
					new GunsmithGunItem.Traits(16, 14, false, false, false, 0.9f)),
			new Item.Properties().durability(400).repairable(Industry.STEEL_INGOT));

	/** Every Gunsmith gun loaded a round at a time (the Nail Gun takes magazines). */
	public static final List<Item> LOADED_BY_HAND = List.of(SLINGSHOT, FLARE_GUN, SNUB_REVOLVER, SAWED_OFF, LEVER_ACTION, HARPOON_GUN);

	// -------------------------------------------------------------------------------------------------- attachments

	private static final List<Item> RIFLES = List.of(ModItems.M4, ModItems.AKM, ModItems.GALIL, LEVER_ACTION);
	/** A 2x hunting scope: sneak to aim down it, with a duplex reticle. Iron and glass, so it comes early. */
	public static final Item HUNTING_SCOPE_2X = attachment("hunting_scope_2x", AttachmentSlot.OPTIC,
			List.of(ModItems.M4, ModItems.AKM, ModItems.GALIL, ModItems.M249, ModItems.REMINGTON_870, ModItems.SPAS12, LEVER_ACTION),
			stats -> stats.withScope(0.5f));
	/** A 4x hunting scope: sneak to look through it, like the AWP's. */
	public static final Item HUNTING_SCOPE_4X = attachment("hunting_scope_4x", AttachmentSlot.OPTIC, RIFLES,
			stats -> stats.withScope(0.25f));
	/** Lights up whatever the gun is pointed at (see Flashlights). */
	public static final Item FLASHLIGHT = attachment("flashlight", AttachmentSlot.UNDERBARREL,
			List.of(ModItems.M4, ModItems.GLOCK, ModItems.AKM, ModItems.AWP, ModItems.GALIL, ModItems.REVOLVER_357, ModItems.MP5,
					ModItems.MAC10, ModItems.M1911, ModItems.M249, ModItems.REMINGTON_870, ModItems.SPAS12, LEVER_ACTION, NAIL_GUN,
					HARPOON_GUN),
			UnaryOperator.identity());
	/** Folded, it's dead weight (slightly wider hip fire); braced on the ground while crouching it steadies the gun a lot. */
	public static final Item BIPOD = attachment("bipod", AttachmentSlot.UNDERBARREL,
			List.of(ModItems.M4, ModItems.AKM, ModItems.GALIL, ModItems.AWP, LEVER_ACTION),
			stats -> stats.withSpread(stats.spreadDegrees() * 1.1f, stats.bloomPerShot()));
	/** A strap: half the penalty for holding the gun in one hand. */
	public static final Item RIFLE_SLING = attachment("rifle_sling", AttachmentSlot.GRIP,
			List.of(ModItems.M4, ModItems.AKM, ModItems.AWP, ModItems.GALIL, ModItems.MP5, ModItems.M249, ModItems.REMINGTON_870,
					ModItems.SPAS12, LEVER_ACTION, HARPOON_GUN),
			UnaryOperator.identity());
	/** A blade on the muzzle: punching a mob stabs it instead of reloading. */
	public static final Item BAYONET = attachment("bayonet", AttachmentSlot.MUZZLE,
			List.of(ModItems.M4, ModItems.AKM, ModItems.GALIL, LEVER_ACTION, ModItems.REMINGTON_870, ModItems.SPAS12),
			UnaryOperator.identity());
	/** A full choke: buckshot in a much tighter pattern. */
	public static final Item SHOTGUN_CHOKE = attachment("shotgun_choke", AttachmentSlot.MUZZLE,
			List.of(ModItems.REMINGTON_870, ModItems.SPAS12), UnaryOperator.identity());
	/** Ports the gas up: less kick and much less climb in rapid fire, without the brake's extra noise. */
	public static final Item COMPENSATOR = attachment("compensator", AttachmentSlot.MUZZLE,
			List.of(ModItems.M4, ModItems.GLOCK, ModItems.AKM, ModItems.AWP, ModItems.GALIL, ModItems.MP5, ModItems.MAC10, ModItems.M1911,
					ModItems.M249),
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.8f).withSpread(stats.spreadDegrees(), stats.bloomPerShot() * 0.75f));

	public static final List<Item> ATTACHMENTS = List.of(HUNTING_SCOPE_2X, HUNTING_SCOPE_4X, FLASHLIGHT, BIPOD, RIFLE_SLING, BAYONET,
			SHOTGUN_CHOKE, COMPENSATOR);

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private Gunsmith() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name) {
		return register(name, Item::new, new Item.Properties());
	}

	private static MagazineItem magazine(String name, int capacity, List<Ammo> ammo, int extraReloadTicks) {
		return (MagazineItem) register(name, properties -> new MagazineItem(properties, capacity, ammo, extraReloadTicks),
				new Item.Properties().stacksTo(1));
	}

	private static Item attachment(String name, AttachmentSlot slot, List<Item> guns, UnaryOperator<GunStats> effect) {
		return register(name, properties -> new AttachmentItem(properties, slot, guns, effect), new Item.Properties().stacksTo(16));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Weapon Station recipes (crafting-table ones are data). Every ingredient set is new: the station matches by set. */
	public static List<StationRecipe> stationRecipes() {
		return List.of(
				// .38 Special: copper-washed iron cases, a lead (iron) or silver nose.
				recipe(BULLET_CASING_38, 10, cost(Items.COPPER_INGOT, 1), cost(Items.IRON_NUGGET, 1)),
				recipe(BULLET_38_FMJ, 8, cost(BULLET_CASING_38, 8), cost(Items.GUNPOWDER, 1), cost(Items.IRON_NUGGET, 1)),
				recipe(BULLET_38_SILVER, 8, cost(BULLET_CASING_38, 8), cost(Items.GUNPOWDER, 1), cost(Silver.SILVER_NUGGET, 1)),
				// Nails on a paper collation strip; the strip and coil are bent sheet with a spring.
				recipe(IRON_NAIL, 16, cost(Items.IRON_NUGGET, 4), cost(Items.PAPER, 1)),
				recipe(NAIL_STRIP, 1, cost(Items.IRON_INGOT, 1), cost(ModItems.TECH_SCRAP, 1)),
				recipe(NAIL_COIL, 1, cost(Items.IRON_INGOT, 2), cost(Items.COPPER_INGOT, 1), cost(ModItems.TECH_SCRAP, 1)),
				recipe(HARPOON_SPEAR, 2, cost(Industry.STEEL_INGOT, 1), cost(Items.FLINT, 1), cost(Items.STRING, 1)),
				// The Flare Gun moved here from the crafting table (its blueprint makes it on the Scrap Bench).
				recipe(FLARE_GUN, 1, cost(Items.COPPER_INGOT, 2), cost(Items.IRON_INGOT, 1), cost(Items.TRIPWIRE_HOOK, 1),
						cost(Items.DYE.orange(), 1)),
				// Workshop guns: iron and wood with a bit of tech scrap for springs and pins.
				recipe(SNUB_REVOLVER, 1, cost(Items.IRON_INGOT, 3), cost(Items.COPPER_INGOT, 1), cost(ModItems.TECH_SCRAP, 1),
						cost(Items.LEATHER, 1)),
				recipe(SAWED_OFF, 1, cost(Items.IRON_INGOT, 5), cost(Items.SPRUCE_PLANKS, 2), cost(ModItems.TECH_SCRAP, 1),
						cost(Items.TRIPWIRE_HOOK, 1)),
				recipe(LEVER_ACTION, 1, cost(Items.IRON_INGOT, 6), cost(Items.OAK_PLANKS, 2), cost(ModItems.TECH_SCRAP, 2),
						cost(Items.TRIPWIRE_HOOK, 1)),
				// A Skullcrane's servo arm drives the nailer's blade.
				recipe(NAIL_GUN, 1, cost(Skullcranes.SERVO_ARM, 1), cost(Items.IRON_INGOT, 3), cost(ModItems.TECH_SCRAP, 2),
						cost(Items.DYE.yellow(), 1)),
				// Foundry: a steel pneumatic gun with a rubber seal and a fishing reel for the line.
				recipe(HARPOON_GUN, 1, cost(Industry.STEEL_INGOT, 4), cost(Industry.RUBBER, 2), cost(Items.PISTON, 1),
						cost(Items.FISHING_ROD, 1)),
				// Attachments: the early ones iron, glass and leather; the choke and compensator need steel.
				recipe(HUNTING_SCOPE_2X, 1, cost(Items.IRON_INGOT, 2), cost(Items.GLASS_PANE, 2)),
				recipe(HUNTING_SCOPE_4X, 1, cost(Items.IRON_INGOT, 3), cost(Items.GLASS_PANE, 3), cost(Items.COPPER_INGOT, 1)),
				recipe(FLASHLIGHT, 1, cost(Items.COPPER_INGOT, 2), cost(Items.GLASS_PANE, 1), cost(Items.REDSTONE, 2),
						cost(ModItems.TECH_SCRAP, 1)),
				recipe(BIPOD, 1, cost(Items.IRON_INGOT, 1), cost(Items.STICK, 2), cost(Items.TRIPWIRE_HOOK, 1)),
				recipe(RIFLE_SLING, 1, cost(Items.LEATHER, 3), cost(Items.STRING, 1), cost(Items.COPPER_NUGGET, 2)),
				recipe(BAYONET, 1, cost(Items.IRON_SWORD, 1), cost(Items.IRON_NUGGET, 2)),
				recipe(SHOTGUN_CHOKE, 1, cost(Industry.STEEL_INGOT, 1), cost(Items.IRON_NUGGET, 3)),
				recipe(COMPENSATOR, 1, cost(Industry.STEEL_PLATE, 1), cost(Industry.STEEL_INGOT, 1)),
				// Bigger magazines for the M4 and the MP5 (registered with their guns in ModItems).
				recipe(ModItems.STANAG_DRUM_MAGAZINE, 1, cost(Industry.DURALUMIN_SHEET, 2), cost(Items.IRON_NUGGET, 4),
						cost(Items.COPPER_INGOT, 1)),
				recipe(ModItems.MP5_EXTENDED_MAGAZINE, 1, cost(Items.IRON_NUGGET, 9), cost(Items.TRIPWIRE_HOOK, 1),
						cost(Items.IRON_INGOT, 1)));
	}

	private static StationRecipe recipe(Item result, int count, StationRecipe.Cost... costs) {
		return new StationRecipe(result, count, List.of(costs));
	}

	private static StationRecipe.Cost cost(Item item, int count) {
		return new StationRecipe.Cost(item, count);
	}

	/** Creative tab entries for a magazine: empty, then full of each round. */
	private static List<ItemStack> magazineEntries(MagazineItem magazine, List<Ammo> ammo) {
		List<ItemStack> entries = new ArrayList<>();
		entries.add(new ItemStack(magazine));
		for (Ammo round : ammo) {
			ItemStack full = new ItemStack(magazine);
			full.set(ModComponents.MAGAZINE_CONTENTS, new MagazineContents(BuiltInRegistries.ITEM.wrapAsHolder(round.item()), magazine.capacity()));
			entries.add(full);
		}
		return entries;
	}

	public static void initialize() {
		Flashlights.initialize();
		Flares.initialize();
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			// The Scavenger pair with the homemade guns, the rest after them; attachments with the others.
			output.insertAfter(ShoddyGuns.JUNK_REPEATER, SLINGSHOT, FLARE_GUN, SNUB_REVOLVER, SAWED_OFF, LEVER_ACTION, NAIL_GUN,
					HARPOON_GUN);
			output.insertAfter(ModItems.GLOCK_SWITCH, HUNTING_SCOPE_2X, HUNTING_SCOPE_4X, FLASHLIGHT, BIPOD, RIFLE_SLING, BAYONET,
					SHOTGUN_CHOKE, COMPENSATOR);
			List<ItemStack> ammo = new ArrayList<>(List.of(new ItemStack(BULLET_CASING_38), new ItemStack(BULLET_38_FMJ),
					new ItemStack(BULLET_38_SILVER), new ItemStack(IRON_NAIL)));
			ammo.addAll(magazineEntries(NAIL_STRIP, AMMO_NAILS));
			ammo.addAll(magazineEntries(NAIL_COIL, AMMO_NAILS));
			ammo.add(new ItemStack(HARPOON_SPEAR));
			ammo.add(new ItemStack(Boars.TUSK_HARPOON));
			ammo.add(new ItemStack(FLARE_SHELL));
			output.insertAfter(ShoddyGuns.SCRAP_SHELL, ammo);
		});
	}
}
