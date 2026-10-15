package com.jheinz.mymod.detector;

import java.util.function.Function;

import com.mojang.serialization.Codec;

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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.mymod.MyMod;

/**
 * Detectors and trackers: gadgets that read the world and point at the invasion's traces. One for each tier from
 * Scavenger up, each with its own held-item HUD panel (all drawn in one shared style, see the client's DetectorHud).
 * <ul>
 * <li>{@link ProspectorsPendulumItem Prospector's Pendulum} (Scavenger): tuned to one ore, it swings toward the
 * nearest block of it within {@value #PENDULUM_RANGE} blocks.</li>
 * <li>Metal Detector (Workshop): beeps faster near metal ore, meteorite fragments, buried caches and dropped scrap,
 * {@value #METAL_DETECTOR_RANGE} blocks out.</li>
 * <li>Meteorite Scanner (Foundry): points to the nearest logged crash site (see {@link Landmarks}).</li>
 * <li>Dowsing Rod and Fae Dowsing Rod (Scavenger, then fae): point to the nearest Fae Ring.</li>
 * <li>UFO Radar (Foundry): a radar sweep of the saucers about, and an alarm when one's abducting or landing.</li>
 * <li>{@link LunarAlmanacItem Lunar Almanac} (Scavenger): the moon, nights to the full moon, and a bad feeling
 * near a cursed villager.</li>
 * <li>{@link LedgerItem Ledger} (Scavenger): vault balance, card debt and how long before the collectors come.</li>
 * </ul>
 * Everything that only needs what the client can see (blocks, saucers, the moon, the player's own bank data) is
 * worked out on the client; crash sites, rings and curses come from the server through {@link #FIX}.
 */
public final class Detectors {
	public static final int PENDULUM_RANGE = 16;
	public static final int METAL_DETECTOR_RANGE = 10;
	public static final int DOWSING_ROD_RANGE = 160;
	public static final int FAE_DOWSING_ROD_RANGE = 400;
	public static final int METEORITE_SCANNER_RANGE = 600;
	public static final int UFO_RADAR_RANGE = 128;
	/** How far round the holder of an almanac a cursed villager makes itself felt. */
	public static final int OMEN_RANGE = 32;

	// --- Items -------------------------------------------------------------------------------------------------
	public static final Item PROSPECTORS_PENDULUM = item("prospectors_pendulum", ProspectorsPendulumItem::new, new Item.Properties().stacksTo(1));
	public static final Item METAL_DETECTOR = gadget("metal_detector", new Item.Properties().stacksTo(1));
	public static final Item METEORITE_SCANNER = gadget("meteorite_scanner", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item DOWSING_ROD = gadget("dowsing_rod", new Item.Properties().stacksTo(1));
	public static final Item FAE_DOWSING_ROD = gadget("fae_dowsing_rod", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item UFO_RADAR = gadget("ufo_radar", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item LUNAR_ALMANAC = item("lunar_almanac", LunarAlmanacItem::new, new Item.Properties().stacksTo(1));
	public static final Item LEDGER = item("ledger", LedgerItem::new, new Item.Properties().stacksTo(1));

	// --- Data --------------------------------------------------------------------------------------------------
	/** On a Prospector's Pendulum: the ore it's tuned to (an {@link OreTarget} id). */
	public static final DataComponentType<String> PENDULUM_TUNING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("pendulum_tuning"), DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());
	/** On the Overworld: every Fae Ring and meteorite crash site found so far (see {@link Landmarks}). */
	public static final AttachmentType<LandmarkData> LANDMARKS = AttachmentRegistry.<LandmarkData>builder()
			.persistent(LandmarkData.CODEC)
			.buildAndRegister(MyMod.id("landmarks"));
	/** On a player: what the server has worked out for the trackers they're holding. Synced to them alone. */
	public static final AttachmentType<DetectorFix> FIX = AttachmentRegistry.<DetectorFix>builder()
			.syncWith(DetectorFix.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(MyMod.id("detector_fix"));

	// --- Sounds (vanilla ones, re-pitched: see sounds.json) ----------------------------------------------------
	public static final SoundEvent PENDULUM_TUNE = sound("pendulum_tune");
	public static final SoundEvent PENDULUM_TICK = sound("pendulum_tick");
	public static final SoundEvent PENDULUM_FOUND = sound("pendulum_found");
	public static final SoundEvent METAL_DETECTOR_BEEP = sound("metal_detector_beep");
	public static final SoundEvent SCANNER_PING = sound("meteorite_scanner_ping");
	public static final SoundEvent SCANNER_ARRIVED = sound("meteorite_scanner_arrived");
	public static final SoundEvent DOWSING_TWITCH = sound("dowsing_rod_twitch");
	public static final SoundEvent RADAR_SWEEP = sound("ufo_radar_sweep");
	public static final SoundEvent RADAR_CONTACT = sound("ufo_radar_contact");
	public static final SoundEvent RADAR_ALARM = sound("ufo_radar_alarm");
	public static final SoundEvent ALMANAC_OPEN = sound("lunar_almanac_open");
	public static final SoundEvent ALMANAC_OMEN = sound("lunar_almanac_omen");
	public static final SoundEvent LEDGER_OPEN = sound("ledger_open");

	private Detectors() {
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item gadget(String name, Item.Properties properties) {
		return item(name, DetectorItem::new, properties);
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Whether this player has this item in either hand. */
	public static boolean holds(Player player, Item item) {
		return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
	}

	public static void initialize() {
		Landmarks.initialize();
		DetectorFixes.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.RECOVERY_COMPASS,
				PROSPECTORS_PENDULUM, DOWSING_ROD, FAE_DOWSING_ROD, METAL_DETECTOR, METEORITE_SCANNER, UFO_RADAR, LUNAR_ALMANAC, LEDGER));
	}
}
