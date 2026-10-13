package com.jheinz.mymod.shoddy;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.shotgun.ShotgunItem.Shell;

/**
 * The Scavenger-tier guns: janky homemade firearms made on a crafting table from pipe (iron), wood, flint, tape and tech
 * scrap, before the Foundry's steel makes real ones possible. All four load a round at a time and misfire, jam and
 * backfire more as they wear (see {@link ShoddyGunItem}).
 * <ul>
 * <li>Pipe Pistol: a slam-fire pipe on a plank grip. One crude round, short range, wild; cocked by drawing the striker
 * back after loading.</li>
 * <li>Pipe Rifle: a longer pipe with a bolt. One crude round, hits much harder and further; the bolt is worked after
 * each shot.</li>
 * <li>Blunderbuss: a muzzle-loader with a flared copper bell. Scrap shells, or gravel or flint poured down the bore
 * with a pinch of gunpowder; a huge spread at close range and a kick that shoves the shooter back.</li>
 * <li>Junk Repeater: five crude rounds in a scrap box magazine, cycled by a salvaged Skullcrane servo arm. Jams the
 * most.</li>
 * </ul>
 */
public final class ShoddyGuns {
	public static final DataComponentType<ShoddyState> SHODDY_STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("shoddy_state"),
			DataComponentType.<ShoddyState>builder().persistent(ShoddyState.CODEC).networkSynchronized(ShoddyState.STREAM_CODEC).build());

	// Pitched-down existing reports and vanilla bits (see sounds.json): cruder, louder, cheaper.
	public static final SoundEvent PIPE_PISTOL_SHOT = sound("pipe_pistol_shot");
	public static final SoundEvent PIPE_RIFLE_SHOT = sound("pipe_rifle_shot");
	public static final SoundEvent BLUNDERBUSS_SHOT = sound("blunderbuss_shot");
	public static final SoundEvent JUNK_REPEATER_SHOT = sound("junk_repeater_shot");
	/** The hammer falling on a dud. */
	public static final SoundEvent MISFIRE_CLICK = sound("misfire_click");
	/** A case sticking in the action. */
	public static final SoundEvent JAM = sound("gun_jam");
	/** Knocking a stuck case out. */
	public static final SoundEvent JAM_CLEAR = sound("gun_jam_clear");
	public static final SoundEvent BACKFIRE = sound("gun_backfire");

	public static final ResourceKey<DamageType> BACKFIRE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("backfire"));

	/** An iron nugget slug on a pinch of powder in a paper (or copper) case: the pipe guns' round. */
	public static final Item CRUDE_ROUND = item("crude_round", Item::new, new Item.Properties());
	/** Iron nugget shot and powder in a paper (or tar-sealed) wad: the blunderbuss's load. */
	public static final Item SCRAP_SHELL = item("scrap_shell", Item::new, new Item.Properties());

	// Shell(item, pellets, pellet damage, pellet spread, full damage to, falls off to, min factor, range, HUD colour)
	public static final Item PIPE_PISTOL = item("pipe_pistol",
			properties -> new ShoddyGunItem(properties,
					new GunStats(PIPE_PISTOL_SHOT, 4.5f, 32, 24, List.of(FireMode.SEMI))
							.withSingleRoundLoading().withSpread(2.5f, 2.0f),
					List.of(new Shell(CRUDE_ROUND, 1, 6.0f, 0.0f, 6.0, 20.0, 0.4f, 32.0, 0xA9AEB3)), 1, 6,
					new ShoddyGunItem.Quirks(0.06f, 0.24f, 0.03f, 0.12f, 0.01f, 0.05f, 2.0f),
					new ShoddyGunItem.Traits(24, 14, true, true, 0.0f, List.of())),
			new Item.Properties().durability(60).repairable(Items.IRON_INGOT));
	public static final Item PIPE_RIFLE = item("pipe_rifle",
			properties -> new ShoddyGunItem(properties,
					new GunStats(PIPE_RIFLE_SHOT, 5.0f, 64, 20, List.of(FireMode.BOLT))
							.withSingleRoundLoading().withSpread(1.2f, 1.5f),
					List.of(new Shell(CRUDE_ROUND, 1, 9.0f, 0.0f, 16.0, 48.0, 0.5f, 64.0, 0xA9AEB3)), 1, 18,
					new ShoddyGunItem.Quirks(0.05f, 0.20f, 0.04f, 0.15f, 0.01f, 0.04f, 2.0f),
					new ShoddyGunItem.Traits(20, 16, false, false, 0.0f, List.of())),
			new Item.Properties().durability(80).repairable(Items.IRON_INGOT));
	public static final Item BLUNDERBUSS = item("blunderbuss",
			properties -> new ShoddyGunItem(properties,
					new GunStats(BLUNDERBUSS_SHOT, 9.0f, 20, 36, List.of(FireMode.SEMI))
							.withSingleRoundLoading().withSpread(1.5f, 2.0f),
					List.of(new Shell(SCRAP_SHELL, 9, 2.0f, 11.0f, 4.0, 14.0, 0.25f, 20.0, 0x9C8B6E),
							new Shell(Items.GRAVEL, 7, 1.5f, 14.0f, 3.0, 10.0, 0.2f, 16.0, 0x8A8580),
							new Shell(Items.FLINT, 5, 2.4f, 9.0f, 4.0, 12.0, 0.25f, 18.0, 0x4A4A4E)), 1, 10,
					new ShoddyGunItem.Quirks(0.08f, 0.26f, 0.02f, 0.08f, 0.02f, 0.08f, 3.0f),
					new ShoddyGunItem.Traits(36, 16, true, false, 0.7f, List.of(Items.GRAVEL, Items.FLINT))),
			new Item.Properties().durability(70).repairable(Items.IRON_INGOT));
	public static final Item JUNK_REPEATER = item("junk_repeater",
			properties -> new ShoddyGunItem(properties,
					new GunStats(JUNK_REPEATER_SHOT, 3.0f, 40, 12, List.of(FireMode.SEMI))
							.withSingleRoundLoading().withSpread(1.8f, 1.6f),
					List.of(new Shell(CRUDE_ROUND, 1, 5.0f, 0.0f, 8.0, 28.0, 0.45f, 40.0, 0xA9AEB3)), 5, 7,
					new ShoddyGunItem.Quirks(0.04f, 0.14f, 0.09f, 0.32f, 0.01f, 0.04f, 2.0f),
					new ShoddyGunItem.Traits(12, 12, true, false, 0.0f, List.of())),
			new Item.Properties().durability(140).repairable(Items.IRON_INGOT));

	public static final List<Item> GUNS = List.of(PIPE_PISTOL, PIPE_RIFLE, BLUNDERBUSS, JUNK_REPEATER);

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));

	private ShoddyGuns() {
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
		// Before the modern guns and their ammo: the first rung of the ladder.
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			output.insertAfter(Items.CROSSBOW, PIPE_PISTOL, PIPE_RIFLE, BLUNDERBUSS, JUNK_REPEATER);
			output.insertAfter(Items.ARROW, CRUDE_ROUND, SCRAP_SHELL);
		});
	}
}
