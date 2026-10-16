package com.jheinz.tomyen.trombone;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.tomyen.MyMod;

/**
 * The Trombone (see {@link TromboneItem}): a brass instrument with a working slide that plays two octaves by where you
 * look, and four little songs with something to say to the mobs around ({@link TromboneSong}, {@link TromboneMusic},
 * {@link TromboneMoods}). Made on the crafting table from copper (for the brass), a gold ingot and a spring.
 * Its sounds are synthesised by tools/gen_trombone.py; the lullaby, disco and whimper borrow vanilla's.
 */
public final class Trombones {
	public static final Item TROMBONE = register("trombone");

	/** The sustained note, looped and pitched by the client as the slide moves. */
	public static final SoundEvent NOTE = sound("trombone_note");
	/** The start of each note. */
	public static final SoundEvent ATTACK = sound("trombone_attack");
	public static final SoundEvent SAD_TROMBONE = sound("sad_trombone");
	public static final SoundEvent FANFARE = sound("trombone_fanfare");
	public static final SoundEvent LULLABY_CHIME = sound("trombone_lullaby");
	public static final SoundEvent DISCO_BEAT = sound("trombone_disco");
	public static final SoundEvent WEREWOLF_WHIMPER = sound("werewolf_whimper");

	/** The Zzz a lulled mob breathes out. */
	public static final SimpleParticleType ZZZ =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("trombone_zzz"), FabricParticleTypes.simple());

	private Trombones() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new TromboneItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(key)));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(TrombonePayloads.Staff.TYPE, TrombonePayloads.Staff.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrombonePayloads.Moods.TYPE, TrombonePayloads.Moods.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(TromboneMusic::tick);
		ServerTickEvents.END_LEVEL_TICK.register(TromboneMoods::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TromboneMusic.clear();
			TromboneMoods.clear();
		});
		// After the goat horns among the tools, and with the throwables in combat (it's a weapon of sorts).
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertBefore(Items.MUSIC_DISC_13, TROMBONE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.WIND_CHARGE, TROMBONE));
	}
}
