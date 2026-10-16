package com.jheinz.tomyen.soul;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import com.jheinz.tomyen.MyMod;

/**
 * The Soul Vacuum and what it collects: the canister component, the Soul Charges its souls are let out as, its
 * sounds, and the entity tags saying what it can't take (bosses) and what fights it (elites). Soul Charges go into
 * the Foundry to make soul steel (see {@link SoulSteel}).
 */
public final class Souls {
	/** On a Soul Vacuum: the souls in its canister. */
	public static final DataComponentType<SoulCanister> SOUL_CANISTER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("soul_canister"), DataComponentType.<SoulCanister>builder().persistent(SoulCanister.CODEC)
					.networkSynchronized(SoulCanister.STREAM_CODEC).build());

	/** Too mighty to be sucked up at all: the bosses. */
	public static final TagKey<EntityType<?>> VACUUM_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("soul_vacuum_immune"));
	/** Elites: pulled far more weakly, and only captured once badly hurt. */
	public static final TagKey<EntityType<?>> VACUUM_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("soul_vacuum_resistant"));

	/** The vacuum's motor, looped while it's sucking (see SoulVacuumSound). */
	public static final SoundEvent VACUUM_LOOP = sound("soul_vacuum_loop");
	/** The ghostly wail of a soul going into the canister. */
	public static final SoundEvent SOUL_WAIL = sound("soul_wail");

	public static final Item SOUL_CHARGE = item("soul_charge", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item SOUL_VACUUM = item("soul_vacuum", SoulVacuumItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(SOUL_CANISTER, SoulCanister.EMPTY));

	private Souls() {
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
		ServerTickEvents.END_SERVER_TICK.register(SoulCapture::tick);
		// A mob being drawn into the canister can't be killed out of it (and so drop its loot after all).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !SoulCapture.isCapturing(entity));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.BRUSH, SOUL_VACUUM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.ECHO_SHARD, SOUL_CHARGE));
		SoulSteel.initialize();
	}
}
