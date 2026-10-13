package com.jheinz.mymod;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.jheinz.mymod.armor.ArmorPlateItem;
import com.jheinz.mymod.attachment.AttachmentItem;
import com.jheinz.mymod.holy.CrucifixItem;
import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.neuralink.NeuralinkModule;
import com.jheinz.mymod.neuralink.NeuralinkModuleItem;
import com.jheinz.mymod.exoskeleton.ExoModule;
import com.jheinz.mymod.exoskeleton.ExoModuleItem;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;
import com.jheinz.mymod.attachment.AttachmentSlot;
import com.jheinz.mymod.armor.PlateCarrierItem;
import com.jheinz.mymod.lmg.MachineGunItem;
import com.jheinz.mymod.longsword.LongSwordItem;
import com.jheinz.mymod.longsword.LongSwords;
import com.jheinz.mymod.magmasteel.Magmasteel;
import com.jheinz.mymod.froststeel.Froststeel;
import com.jheinz.mymod.soul.SoulSteel;
import com.jheinz.mymod.voidsteel.Voidsteel;
import com.jheinz.mymod.lmg.MachineGuns;
import com.jheinz.mymod.schnozzle.Schnozzles;
import com.jheinz.mymod.shotgun.ShotgunItem;
import com.jheinz.mymod.shotgun.Shotguns;

public final class ModItems {
	/** Field-of-view multiplier when aiming through a red dot sight. */
	public static final float RED_DOT_ZOOM = 0.8f;
	public static final float HOLOGRAPHIC_ZOOM = 0.75f;
	public static final float KOBRA_ZOOM = 0.85f;

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

	// 12 gauge: Remington 870, SPAS-12. Buckshot throws eight pellets that lose damage with range; a slug is one heavy
	// ball that carries much further. Shells go straight into a shotgun's tube from the inventory.
	public static final Item SHELL_12G_BUCKSHOT = item("shell_12g_buckshot");
	public static final Item SHELL_12G_SLUG = item("shell_12g_slug");
	private static final List<ShotgunItem.Shell> SHELLS_12G = List.of(
			new ShotgunItem.Shell(SHELL_12G_BUCKSHOT, 8, 2.5f, 4.5f, 8.0, 28.0, 0.3f, 40.0, 0xD8483A),
			new ShotgunItem.Shell(SHELL_12G_SLUG, 1, 16.0f, 0.0f, 20.0, 60.0, 0.6f, 80.0, 0x5AA866));

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
	/** A soft pouch of linked 5.56 for the M249. Packed like any magazine, a round at a time. */
	public static final MagazineItem M249_AMMO_BOX = magazine("m249_ammo_box", 100, AMMO_556, 0);

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
	public static final Item M249_RECEIVER = part("m249_receiver");
	public static final Item M249_BARREL = part("m249_barrel");
	public static final Item REMINGTON_870_RECEIVER = part("remington_870_receiver");
	public static final Item REMINGTON_870_BARREL = part("remington_870_barrel");
	public static final Item SPAS12_RECEIVER = part("spas12_receiver");
	public static final Item SPAS12_BARREL = part("spas12_barrel");

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
	// A round every other tick in full-auto: 600 rpm.
	public static final Item MP5 = gun("mp5", 1000,
			new GunStats(ModSounds.MP5_SHOT, 0.9f, 64, 30, List.of(FireMode.AUTO, FireMode.BURST, FireMode.SEMI))
					.withAutoTicks(2).withSpread(0.8f, 0.35f),
			List.of(MP5_MAGAZINE), AMMO_9MM);
	// One round every tick in full-auto (about 1200 rpm): it empties a magazine in a second and a half.
	public static final Item MAC10 = gun("mac10", 700,
			new GunStats(ModSounds.MAC10_SHOT, 1.0f, 40, 25, List.of(FireMode.AUTO, FireMode.SEMI)).withAutoTicks(1).withSpread(1.5f, 0.45f),
			List.of(MAC10_MAGAZINE), AMMO_45);
	public static final Item M1911 = gun("m1911", 800,
			new GunStats(ModSounds.M1911_SHOT, 2.8f, 48, 25, List.of(FireMode.SEMI)).withSpread(0.8f, 1.2f),
			List.of(M1911_MAGAZINE), AMMO_45);
	// Belt-fed from a 100-round box, 800 rpm (see MachineGunItem). Wild from the hip, steady on the bipod (crouch); slow to
	// carry, aim and reload.
	public static final Item M249 = register("m249",
			properties -> new MachineGunItem(properties,
					new GunStats(MachineGuns.M249_SHOT, 1.3f, 112, 100, List.of(FireMode.AUTO)).withAutoTicks(2).withSpread(1.2f, 0.6f),
					List.of(M249_AMMO_BOX), AMMO_556),
			new Item.Properties().durability(1500).repairable(Items.IRON_INGOT).attributes(ItemAttributeModifiers.builder()
					.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(MyMod.id("m249_weight"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
							EquipmentSlotGroup.MAINHAND)
					.build()));
	// Pump-action: the pump is worked after every shot (0.6 s). Six shells in the tube, loaded one at a time.
	public static final Item REMINGTON_870 = register("remington_870",
			properties -> new ShotgunItem(properties,
					new GunStats(Shotguns.REMINGTON_870_SHOT, 6.0f, 80, ShotgunItem.SHELL_TICKS, List.of(FireMode.PUMP))
							.withSingleRoundLoading().withSpread(0.8f, 1.5f),
					SHELLS_12G, 6, Map.of(FireMode.PUMP, new ShotgunItem.Handling(1.0f, 1.0f, 1.0f, ShotgunItem.PUMP_TICKS))),
			new Item.Properties().durability(900).repairable(Items.IRON_INGOT));
	// Pump or semi-auto (the fire-mode key): pumped, it throws a tighter, harder-hitting pattern; semi-auto cycles itself
	// in a quarter of a second but kicks harder. Eight shells in the tube.
	public static final Item SPAS12 = register("spas12",
			properties -> new ShotgunItem(properties,
					new GunStats(Shotguns.SPAS12_SHOT, 6.5f, 80, ShotgunItem.SHELL_TICKS, List.of(FireMode.PUMP, FireMode.SEMI))
							.withSingleRoundLoading().withSpread(0.7f, 1.4f),
					SHELLS_12G, 8, Map.of(
							FireMode.PUMP, new ShotgunItem.Handling(1.1f, 0.85f, 1.0f, ShotgunItem.PUMP_TICKS),
							FireMode.SEMI, new ShotgunItem.Handling(0.9f, 1.0f, 1.35f, 5))),
			new Item.Properties().durability(1000).repairable(Items.IRON_INGOT));

	// Attachments. Each is right-clicked onto a gun (or combined with it in the Weapon Station).
	private static final List<Item> ALL_GUNS = List.of(M4, GLOCK, AKM, AWP, GALIL, REVOLVER_357, MP5, MAC10, M1911, M249, REMINGTON_870,
			SPAS12);
	/** Quieter shots with no smoke trail, and a little less kick. A revolver's cylinder gap can't be suppressed. */
	public static final Item SUPPRESSOR = attachment("suppressor", AttachmentSlot.MUZZLE,
			List.of(M4, GLOCK, AKM, AWP, GALIL, MP5, MAC10, M1911, M249),
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.85f));
	/** A cheap oil-filter can: as quiet as a suppressor and a little less kick, but the fat can throws shots wider. */
	public static final Item OIL_CAN_SUPPRESSOR = attachment("oil_can_suppressor", AttachmentSlot.MUZZLE,
			List.of(M4, GLOCK, AKM, AWP, GALIL, MP5, MAC10, M1911, M249),
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.9f).withSpread(stats.spreadDegrees() * 1.15f, stats.bloomPerShot()));
	/** Ports the gas sideways: much less recoil, but louder. */
	public static final Item MUZZLE_BRAKE = attachment("muzzle_brake", AttachmentSlot.MUZZLE,
			List.of(M4, GLOCK, AKM, AWP, GALIL, MP5, MAC10, M1911, M249),
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.65f));
	/** Hides the muzzle smoke and steadies rapid fire a little. */
	public static final Item FLASH_HIDER = attachment("flash_hider", AttachmentSlot.MUZZLE,
			List.of(M4, GLOCK, AKM, AWP, GALIL, MP5, MAC10, M1911, M249),
			stats -> stats.withSpread(stats.spreadDegrees(), stats.bloomPerShot() * 0.85f));
	/** Tighter hip fire, and a red dot on whatever it's pointed at. */
	public static final Item LASER_SIGHT = attachment("laser_sight", AttachmentSlot.UNDERBARREL, ALL_GUNS,
			stats -> stats.withSpread(stats.spreadDegrees() * 0.6f, stats.bloomPerShot()));
	/**
	 * Sneaking aims through it: a slight zoom, a red dot reticle, and no resting spread (the same as a scope,
	 * so the AWP's scope leaves no room for one).
	 */
	public static final Item RED_DOT_SIGHT = attachment("red_dot_sight", AttachmentSlot.OPTIC,
			List.of(M4, GLOCK, AKM, GALIL, REVOLVER_357, MP5, MAC10, M1911, M249, REMINGTON_870, SPAS12),
			stats -> stats.withScope(RED_DOT_ZOOM));
	/** A green laser: brighter, so the dot shows twice as far, and a touch steadier than the red one. */
	public static final Item GREEN_LASER_SIGHT = attachment("green_laser_sight", AttachmentSlot.UNDERBARREL, ALL_GUNS,
			stats -> stats.withSpread(stats.spreadDegrees() * 0.55f, stats.bloomPerShot()));
	/** EOTech holographic sight: aims like a red dot, a little more zoom, with a ring-and-dot reticle. */
	public static final Item HOLOGRAPHIC_SIGHT = attachment("holographic_sight", AttachmentSlot.OPTIC,
			List.of(M4, GLOCK, AKM, GALIL, REVOLVER_357, MP5, MAC10, M1911, M249, REMINGTON_870, SPAS12),
			stats -> stats.withScope(HOLOGRAPHIC_ZOOM));
	/** Kobra reflex sight: aims like a red dot, a little less zoom, with a chevron reticle. */
	public static final Item KOBRA_SIGHT = attachment("kobra_sight", AttachmentSlot.OPTIC,
			List.of(M4, GLOCK, AKM, GALIL, REVOLVER_357, MP5, MAC10, M1911, M249, REMINGTON_870, SPAS12),
			stats -> stats.withScope(KOBRA_ZOOM));
	/** Converts the Glock to full-auto: a round every tick (1200 rpm), and harder to keep on target. */
	public static final Item GLOCK_SWITCH = attachment("glock_switch", AttachmentSlot.FIRE_CONTROL, List.of(GLOCK),
			stats -> stats.withFireModes(withAuto(stats.fireModes())).withAutoTicks(1)
					.withSpread(stats.spreadDegrees(), stats.bloomPerShot() * 1.3f));

	/** Foregrips: a second hand on the gun steadies it. They go on the rail behind any laser, so both fit. */
	private static final List<Item> GRIP_GUNS = List.of(M4, AKM, AWP, GALIL, MP5, MAC10, M249);
	/** A straight grip to pull the gun into the shoulder: the most recoil control. */
	public static final Item VERTICAL_GRIP = attachment("vertical_grip", AttachmentSlot.GRIP, GRIP_GUNS,
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.7f));
	/** Angled grip: a bit less recoil control than a vertical one, but steadier in sustained fire. */
	public static final Item ANGLED_GRIP = attachment("angled_grip", AttachmentSlot.GRIP, GRIP_GUNS,
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.8f).withSpread(stats.spreadDegrees(), stats.bloomPerShot() * 0.8f));
	/** Stubby grip: a short grip that also tightens hip fire. */
	public static final Item STUBBY_GRIP = attachment("stubby_grip", AttachmentSlot.GRIP, GRIP_GUNS,
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.85f).withSpread(stats.spreadDegrees() * 0.85f, stats.bloomPerShot()));
	/** Handstop: a nub the hand pushes against, keeping long bursts on target. */
	public static final Item HANDSTOP = attachment("handstop", AttachmentSlot.GRIP, GRIP_GUNS,
			stats -> stats.withRecoil(stats.recoilDegrees() * 0.9f).withSpread(stats.spreadDegrees(), stats.bloomPerShot() * 0.7f));

	/** Punch for orbs of light, hold use to charge a beam. Holy light only hurts the undead. */
	/** Salvaged circuitry: what sights and lasers are built from. */
	public static final Item TECH_SCRAP = item("tech_scrap");
	/** Refined in the Foundry from quartz and sand. */
	public static final Item SILICON = item("silicon");
	/** Silicon and tech scrap: the next tier of electronics after scrap. */
	public static final Item COMPUTER_CHIP = item("computer_chip");
	public static final Item NEURALINK = register("neuralink", properties -> new NeuralinkItem(properties, 3),
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).equippable(EquipmentSlot.HEAD));
	/** Fused in the Foundry from a Neuralink and more chips: five sockets instead of three. */
	public static final Item NEURALINK_MK2 = register("neuralink_mk2", properties -> new NeuralinkItem(properties, 5),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).equippable(EquipmentSlot.HEAD));
	public static final Item ESP_MODULE = module("esp_module", NeuralinkModule.ESP);
	public static final Item NIGHT_VISION_MODULE = module("night_vision_module", NeuralinkModule.NIGHT_VISION);
	public static final Item XRAY_MODULE = module("xray_module", NeuralinkModule.XRAY);
	public static final Item OPTIC_LASERS_MODULE = module("optic_lasers_module", NeuralinkModule.OPTIC_LASERS);
	public static final Item TELEKINESIS_MODULE = module("telekinesis_module", NeuralinkModule.TELEKINESIS);
	public static final Item BLINK_MODULE = module("blink_module", NeuralinkModule.BLINK);
	public static final Item NEURAL_HIJACK_MODULE = module("neural_hijack_module", NeuralinkModule.NEURAL_HIJACK);
	public static final Item EMP_PULSE_MODULE = module("emp_pulse_module", NeuralinkModule.EMP_PULSE);
	public static final Item OVERCLOCK_MODULE = module("overclock_module", NeuralinkModule.OVERCLOCK);
	public static final Item SMART_LINK_MODULE = module("smart_link_module", NeuralinkModule.SMART_LINK);
	public static final Item MIND_READER_MODULE = module("mind_reader_module", NeuralinkModule.MIND_READER);
	public static final Item BIO_MONITOR_MODULE = module("bio_monitor_module", NeuralinkModule.BIO_MONITOR);
	public static final Item HIVE_MIND_MODULE = module("hive_mind_module", NeuralinkModule.HIVE_MIND);
	public static final Item RECALL_MODULE = module("recall_module", NeuralinkModule.RECALL);
	/** A full-body frame worn in the chest slot that fills every armor slot; armor and an elytra go in its sockets. */
	public static final Item EXOSKELETON = register("exoskeleton", ExoskeletonItem::new, ExoskeletonItem.properties());
	public static final Item REINFORCED_PLATING_MODULE = register("reinforced_plating_module",
			properties -> new ExoModuleItem(properties, ExoModule.REINFORCED_PLATING), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item SERVO_LEGS_MODULE = register("servo_legs_module",
			properties -> new ExoModuleItem(properties, ExoModule.SERVO_LEGS), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item CRUCIFIX = register("crucifix", CrucifixItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// Long swords: three swords of one material on the diagonal of a crafting table. Two-handed, slower and harder
	// hitting, with more reach and a wider sweep (see LongSwords).
	public static final Item WOODEN_LONG_SWORD = longSword("wooden_long_sword", LongSwords.properties(ToolMaterial.WOOD));
	public static final Item STONE_LONG_SWORD = longSword("stone_long_sword", LongSwords.properties(ToolMaterial.STONE));
	public static final Item COPPER_LONG_SWORD = longSword("copper_long_sword", LongSwords.properties(ToolMaterial.COPPER));
	public static final Item IRON_LONG_SWORD = longSword("iron_long_sword", LongSwords.properties(ToolMaterial.IRON));
	public static final Item GOLDEN_LONG_SWORD = longSword("golden_long_sword", LongSwords.properties(ToolMaterial.GOLD));
	public static final Item DIAMOND_LONG_SWORD = longSword("diamond_long_sword", LongSwords.properties(ToolMaterial.DIAMOND));
	public static final Item NETHERITE_LONG_SWORD = longSword("netherite_long_sword",
			LongSwords.properties(ToolMaterial.NETHERITE).fireResistant());

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

	private static Item longSword(String name, Item.Properties properties) {
		return register(name, LongSwordItem::new, properties);
	}

	private static Item part(String name) {
		return register(name, Item::new, new Item.Properties().stacksTo(1));
	}

	private static MagazineItem magazine(String name, int capacity, List<Ammo> ammo, int extraReloadTicks) {
		return (MagazineItem) register(name, properties -> new MagazineItem(properties, capacity, ammo, extraReloadTicks),
				new Item.Properties().stacksTo(1));
	}

	private static Item attachment(String name, AttachmentSlot slot, List<Item> guns, UnaryOperator<GunStats> effect) {
		return register(name, properties -> new AttachmentItem(properties, slot, guns, effect), new Item.Properties().stacksTo(16));
	}

	private static Item module(String name, NeuralinkModule module) {
		return register(name, properties -> new NeuralinkModuleItem(properties, module),
				new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	private static List<FireMode> withAuto(List<FireMode> modes) {
		List<FireMode> all = new ArrayList<>(modes);
		all.add(FireMode.AUTO);
		return List.copyOf(all);
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
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(TECH_SCRAP);
			output.accept(SILICON);
			output.accept(COMPUTER_CHIP);
		});
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB)
				.register(output -> {
					output.insertAfter(Items.NETHERITE_SWORD, WOODEN_LONG_SWORD, STONE_LONG_SWORD, COPPER_LONG_SWORD, IRON_LONG_SWORD,
							GOLDEN_LONG_SWORD, DIAMOND_LONG_SWORD, NETHERITE_LONG_SWORD, Magmasteel.SET.longSword,
							SoulSteel.SET.longSword, Froststeel.SET.longSword, Voidsteel.SET.longSword);
					output.insertAfter(Items.TRIDENT, CRUCIFIX, Schnozzles.NOSE_HAIR_WHIP);
					output.insertAfter(Items.TURTLE_HELMET, NEURALINK, NEURALINK_MK2, ESP_MODULE, NIGHT_VISION_MODULE,
							XRAY_MODULE, OPTIC_LASERS_MODULE, TELEKINESIS_MODULE, BLINK_MODULE, NEURAL_HIJACK_MODULE,
							EMP_PULSE_MODULE, OVERCLOCK_MODULE, SMART_LINK_MODULE, MIND_READER_MODULE, BIO_MONITOR_MODULE,
							HIVE_MIND_MODULE, RECALL_MODULE);
					output.insertAfter(RECALL_MODULE, EXOSKELETON, REINFORCED_PLATING_MODULE, SERVO_LEGS_MODULE);
					output.insertAfter(Items.NETHERITE_BOOTS, PLATE_CARRIER, ARMOR_PLATE_LEVEL_1, ARMOR_PLATE_LEVEL_2,
							ARMOR_PLATE_LEVEL_3);
					output.insertAfter(Items.CROSSBOW, M4, M4_UPPER_RECEIVER, M4_LOWER_RECEIVER, AKM, AK_RECEIVER,
							AK_BARREL, GALIL, GALIL_RECEIVER, GALIL_BARREL, AWP, AWP_ACTION, AWP_STOCK, MP5, MP5_RECEIVER,
							MP5_LOWER, MAC10, MAC10_UPPER, MAC10_LOWER, GLOCK, GLOCK_SLIDE, GLOCK_FRAME, M1911,
							M1911_SLIDE, M1911_FRAME, REVOLVER_357, REVOLVER_FRAME, REVOLVER_CYLINDER, M249, M249_RECEIVER,
							M249_BARREL, REMINGTON_870, REMINGTON_870_RECEIVER, REMINGTON_870_BARREL, SPAS12, SPAS12_RECEIVER,
							SPAS12_BARREL, SUPPRESSOR,
							OIL_CAN_SUPPRESSOR, MUZZLE_BRAKE, FLASH_HIDER, LASER_SIGHT, GREEN_LASER_SIGHT, RED_DOT_SIGHT,
							HOLOGRAPHIC_SIGHT, KOBRA_SIGHT, VERTICAL_GRIP, ANGLED_GRIP, STUBBY_GRIP, HANDSTOP, GLOCK_SWITCH);
					List<ItemStack> ammo = new ArrayList<>();
					ammo.addAll(ammoEntries(BULLET_CASING_556, AMMO_556, STANAG_MAGAZINE, GALIL_MAGAZINE, M249_AMMO_BOX));
					ammo.addAll(ammoEntries(BULLET_CASING_762, AMMO_762, AK_MAGAZINE, AK_DRUM_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_338, AMMO_338, AWP_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_9MM, AMMO_9MM, GLOCK_MAGAZINE, GLOCK_DRUM_MAGAZINE, MP5_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_45, AMMO_45, M1911_MAGAZINE, MAC10_MAGAZINE));
					ammo.addAll(ammoEntries(BULLET_CASING_357, AMMO_357, SPEEDLOADER_357));
					ammo.add(new ItemStack(SHELL_12G_BUCKSHOT));
					ammo.add(new ItemStack(SHELL_12G_SLUG));
					output.insertAfter(Items.ARROW, ammo);
				});
	}
}
