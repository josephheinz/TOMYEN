package com.jheinz.mymod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.jheinz.mymod.armor.ArmorPlateItem;
import com.jheinz.mymod.armor.PlateCarrierItem;

public final class ModItems {
	// Armor's effectiveness against each round is set per damage type in ModDamageTypes.

	// 5.56x45mm NATO: M4
	public static final Item BULLET_CASING_556 = item("bullet_casing_556");
	public static final Item BULLET_556_FMJ = item("bullet_556_fmj");
	public static final Item BULLET_556_GREEN_TIP = item("bullet_556_green_tip");
	public static final Item BULLET_556_AP = item("bullet_556_ap");
	public static final Item BULLET_556_HOLLOW_POINT = item("bullet_556_hollow_point");
	private static final List<Ammo> AMMO_556 = List.of(
			new Ammo(BULLET_556_FMJ, 6.0f, ModDamageTypes.BULLET, 0xD08A4E),
			new Ammo(BULLET_556_GREEN_TIP, 5.5f, ModDamageTypes.PENETRATOR_BULLET, 0x6CC24A),
			new Ammo(BULLET_556_AP, 5.0f, ModDamageTypes.ARMOR_PIERCING_BULLET, 0xB4B4B4),
			new Ammo(BULLET_556_HOLLOW_POINT, 9.0f, ModDamageTypes.HOLLOW_POINT_BULLET, 0xE05A4E));

	// 9x19mm Parabellum: Glock
	public static final Item BULLET_CASING_9MM = item("bullet_casing_9mm");
	public static final Item BULLET_9MM_FMJ = item("bullet_9mm_fmj");
	public static final Item BULLET_9MM_HOLLOW_POINT = item("bullet_9mm_hollow_point");
	private static final List<Ammo> AMMO_9MM = List.of(
			new Ammo(BULLET_9MM_FMJ, 4.5f, ModDamageTypes.BULLET, 0xD08A4E),
			new Ammo(BULLET_9MM_HOLLOW_POINT, 7.0f, ModDamageTypes.HOLLOW_POINT_BULLET, 0xE05A4E));

	// 7.62x39mm: AKM
	public static final Item BULLET_CASING_762 = item("bullet_casing_762");
	public static final Item BULLET_762_FMJ = item("bullet_762_fmj");
	public static final Item BULLET_762_AP = item("bullet_762_ap");
	private static final List<Ammo> AMMO_762 = List.of(
			new Ammo(BULLET_762_FMJ, 7.5f, ModDamageTypes.BULLET, 0xD08A4E),
			new Ammo(BULLET_762_AP, 6.5f, ModDamageTypes.ARMOR_PIERCING_BULLET, 0xB4B4B4));

	// .45 ACP: MAC-10, 1911
	public static final Item BULLET_CASING_45 = item("bullet_casing_45");
	public static final Item BULLET_45_FMJ = item("bullet_45_fmj");
	public static final Item BULLET_45_HOLLOW_POINT = item("bullet_45_hollow_point");
	private static final List<Ammo> AMMO_45 = List.of(
			new Ammo(BULLET_45_FMJ, 5.5f, ModDamageTypes.BULLET, 0xD08A4E),
			new Ammo(BULLET_45_HOLLOW_POINT, 8.0f, ModDamageTypes.HOLLOW_POINT_BULLET, 0xE05A4E));

	// .357 Magnum: revolver
	public static final Item BULLET_CASING_357 = item("bullet_casing_357");
	public static final Item BULLET_357_FMJ = item("bullet_357_fmj");
	public static final Item BULLET_357_HOLLOW_POINT = item("bullet_357_hollow_point");
	private static final List<Ammo> AMMO_357 = List.of(
			new Ammo(BULLET_357_FMJ, 8.0f, ModDamageTypes.BULLET, 0xD08A4E),
			new Ammo(BULLET_357_HOLLOW_POINT, 11.0f, ModDamageTypes.HOLLOW_POINT_BULLET, 0xE05A4E));

	// .338 Lapua Magnum: AWP
	public static final Item BULLET_CASING_338 = item("bullet_casing_338");
	public static final Item BULLET_338_FMJ = item("bullet_338_fmj");
	public static final Item BULLET_338_AP = item("bullet_338_ap");
	private static final List<Ammo> AMMO_338 = List.of(
			new Ammo(BULLET_338_FMJ, 20.0f, ModDamageTypes.BULLET, 0xD08A4E),
			new Ammo(BULLET_338_AP, 17.0f, ModDamageTypes.ARMOR_PIERCING_BULLET, 0xB4B4B4));

	public static final MagazineItem STANAG_MAGAZINE = magazine("stanag_magazine", 30, AMMO_556, 0);
	public static final MagazineItem GLOCK_MAGAZINE = magazine("glock_magazine", 17, AMMO_9MM, 0);
	public static final MagazineItem AK_MAGAZINE = magazine("ak_magazine", 30, AMMO_762, 0);
	// Drums hold far more but are clumsy to seat, so they slow the reload.
	public static final MagazineItem GLOCK_DRUM_MAGAZINE = magazine("glock_drum_magazine", 50, AMMO_9MM, 15);
	public static final MagazineItem AK_DRUM_MAGAZINE = magazine("ak_drum_magazine", 75, AMMO_762, 20);
	public static final MagazineItem AWP_MAGAZINE = magazine("awp_magazine", 10, AMMO_338, 0);
	public static final MagazineItem GALIL_MAGAZINE = magazine("galil_magazine", 35, AMMO_556, 0);
	public static final MagazineItem SPEEDLOADER_357 = magazine("speedloader_357", 6, AMMO_357, 0);
	public static final MagazineItem MP5_MAGAZINE = magazine("mp5_magazine", 30, AMMO_9MM, 0);
	public static final MagazineItem MAC10_MAGAZINE = magazine("mac10_magazine", 30, AMMO_45, 0);
	public static final MagazineItem M1911_MAGAZINE = magazine("m1911_magazine", 7, AMMO_45, 0);

	public static final Item M4_UPPER_RECEIVER = part("m4_upper_receiver");
	public static final Item M4_LOWER_RECEIVER = part("m4_lower_receiver");
	public static final Item GLOCK_SLIDE = part("glock_slide");
	public static final Item GLOCK_FRAME = part("glock_frame");
	public static final Item AK_RECEIVER = part("ak_receiver");
	public static final Item AK_BARREL = part("ak_barrel");
	public static final Item AWP_ACTION = part("awp_action");
	public static final Item AWP_STOCK = part("awp_stock");
	public static final Item GALIL_RECEIVER = part("galil_receiver");
	public static final Item GALIL_BARREL = part("galil_barrel");
	public static final Item REVOLVER_FRAME = part("revolver_frame");
	public static final Item REVOLVER_CYLINDER = part("revolver_cylinder");
	public static final Item MP5_RECEIVER = part("mp5_receiver");
	public static final Item MP5_LOWER = part("mp5_lower");
	public static final Item MAC10_UPPER = part("mac10_upper");
	public static final Item MAC10_LOWER = part("mac10_lower");
	public static final Item M1911_SLIDE = part("m1911_slide");
	public static final Item M1911_FRAME = part("m1911_frame");

	private static final ArmorMaterial PLATE_CARRIER_MATERIAL = new ArmorMaterial(15,
			Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
			12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, ItemTags.REPAIRS_LEATHER_ARMOR,
			ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id("plate_carrier")));

	public static final Item PLATE_CARRIER = register("plate_carrier", PlateCarrierItem::new,
			new Item.Properties().humanoidArmor(PLATE_CARRIER_MATERIAL, ArmorType.CHESTPLATE));
	public static final Item ARMOR_PLATE_LEVEL_1 = register("armor_plate_level_1",
			properties -> new ArmorPlateItem(properties, 1, 0.30f), new Item.Properties().stacksTo(1));
	public static final Item ARMOR_PLATE_LEVEL_2 = register("armor_plate_level_2",
			properties -> new ArmorPlateItem(properties, 2, 0.50f), new Item.Properties().stacksTo(1));
	public static final Item ARMOR_PLATE_LEVEL_3 = register("armor_plate_level_3",
			properties -> new ArmorPlateItem(properties, 3, 0.70f), new Item.Properties().stacksTo(1));

	public static final Item M4 = gun("m4", 1000,
			new GunStats(ModSounds.M4_SHOT, 1.5f, 96, 30, List.of(FireMode.AUTO, FireMode.SEMI)).withSpread(0.4f, 0.5f),
			List.of(STANAG_MAGAZINE), AMMO_556);
	public static final Item GLOCK = gun("glock", 800,
			new GunStats(ModSounds.GLOCK_SHOT, 2.5f, 48, 25, List.of(FireMode.SEMI, FireMode.BURST)).withSpread(0.8f, 0.9f),
			List.of(GLOCK_MAGAZINE, GLOCK_DRUM_MAGAZINE), AMMO_9MM);
	public static final Item AKM = gun("akm", 1200,
			new GunStats(ModSounds.AKM_SHOT, 2.2f, 96, 35, List.of(FireMode.AUTO, FireMode.SEMI)).withSpread(0.6f, 0.75f),
			List.of(AK_MAGAZINE, AK_DRUM_MAGAZINE), AMMO_762);
	// Bolt-action: punch to work the bolt after each shot. Sneak to look through the scope.
	public static final Item AWP = gun("awp", 600,
			new GunStats(ModSounds.AWP_SHOT, 7.0f, 192, 50, List.of(FireMode.BOLT)).withScope(0.2f).withSpread(3.0f, 0.0f),
			List.of(AWP_MAGAZINE), AMMO_338);
	public static final Item GALIL = gun("galil", 1100,
			new GunStats(ModSounds.GALIL_SHOT, 1.6f, 96, 35, List.of(FireMode.AUTO, FireMode.SEMI)).withSpread(0.5f, 0.55f),
			List.of(GALIL_MAGAZINE), AMMO_556);
	// Double-action: a short cycle for the heavy trigger pull. Loads loose rounds one at a time, or all six
	// at once from a loaded speedloader.
	public static final Item REVOLVER_357 = gun("revolver_357", 900,
			new GunStats(ModSounds.MAGNUM_SHOT, 4.0f, 64, 40, List.of(FireMode.SEMI)).withCycleTicks(5)
					.withSingleRoundLoading().withSpread(0.6f, 2.0f),
			List.of(SPEEDLOADER_357), AMMO_357);
	// Three rounds per auto cycle: about 900 rpm.
	public static final Item MP5 = gun("mp5", 1000,
			new GunStats(ModSounds.MP5_SHOT, 0.9f, 64, 30, List.of(FireMode.AUTO, FireMode.BURST, FireMode.SEMI))
					.withShotsPerCycle(3).withSpread(0.8f, 0.35f),
			List.of(MP5_MAGAZINE), AMMO_9MM);
	// One round every tick in full-auto (about 1200 rpm): it empties a magazine in a second and a half.
	public static final Item MAC10 = gun("mac10", 700,
			new GunStats(ModSounds.MAC10_SHOT, 1.0f, 40, 25, List.of(FireMode.AUTO, FireMode.SEMI)).withShotsPerCycle(4).withSpread(1.5f, 0.45f),
			List.of(MAC10_MAGAZINE), AMMO_45);
	public static final Item M1911 = gun("m1911", 800,
			new GunStats(ModSounds.M1911_SHOT, 2.8f, 48, 25, List.of(FireMode.SEMI)).withSpread(0.8f, 1.2f),
			List.of(M1911_MAGAZINE), AMMO_45);

	// Vanilla keeps its tab keys private, so rebuild the key from its id.
	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name) {
		return register(name, Item::new, new Item.Properties());
	}

	private static Item part(String name) {
		return register(name, Item::new, new Item.Properties().stacksTo(1));
	}

	private static MagazineItem magazine(String name, int capacity, List<Ammo> ammo, int extraReloadTicks) {
		return (MagazineItem) register(name, properties -> new MagazineItem(properties, capacity, ammo, extraReloadTicks),
				new Item.Properties().stacksTo(1));
	}

	private static Item gun(String name, int durability, GunStats stats, List<MagazineItem> magazines, List<Ammo> ammo) {
		return register(name, properties -> new GunItem(properties, stats, magazines, ammo),
				new Item.Properties().durability(durability).repairable(Items.IRON_INGOT));
	}

	/**
	 * Creative tab entries for a caliber: casing, each round, then for each magazine an empty one and a
	 * full one per round.
	 */
	private static List<ItemStack> ammoEntries(Item casing, List<Ammo> ammo, MagazineItem... magazines) {
		List<ItemStack> entries = new ArrayList<>();
		entries.add(new ItemStack(casing));
		ammo.forEach(round -> entries.add(new ItemStack(round.item())));
		for (MagazineItem magazine : magazines) {
			entries.add(new ItemStack(magazine));
			for (Ammo round : ammo) {
				ItemStack full = new ItemStack(magazine);
				full.set(ModComponents.MAGAZINE_CONTENTS,
						new MagazineContents(BuiltInRegistries.ITEM.wrapAsHolder(round.item()), magazine.capacity()));
				entries.add(full);
			}
		}
		return entries;
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB)
				.register(output -> {
					output.insertAfter(Items.NETHERITE_BOOTS, PLATE_CARRIER, ARMOR_PLATE_LEVEL_1, ARMOR_PLATE_LEVEL_2,
							ARMOR_PLATE_LEVEL_3);
					output.insertAfter(Items.CROSSBOW, M4, M4_UPPER_RECEIVER, M4_LOWER_RECEIVER, AKM, AK_RECEIVER,
							AK_BARREL, GALIL, GALIL_RECEIVER, GALIL_BARREL, AWP, AWP_ACTION, AWP_STOCK, MP5, MP5_RECEIVER,
							MP5_LOWER, MAC10, MAC10_UPPER, MAC10_LOWER, GLOCK, GLOCK_SLIDE, GLOCK_FRAME, M1911,
							M1911_SLIDE, M1911_FRAME, REVOLVER_357, REVOLVER_FRAME, REVOLVER_CYLINDER);
					List<ItemStack> ammo = new ArrayList<>();
					ammo.addAll(ammoEntries(BULLET_CASING_556, AMMO_556, STANAG_MAGAZINE, GALIL_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_762, AMMO_762, AK_MAGAZINE, AK_DRUM_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_338, AMMO_338, AWP_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_9MM, AMMO_9MM, GLOCK_MAGAZINE, GLOCK_DRUM_MAGAZINE, MP5_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_45, AMMO_45, M1911_MAGAZINE, MAC10_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_357, AMMO_357, SPEEDLOADER_357));
					output.insertAfter(Items.ARROW, ammo);
				});
	}
}
