package com.jheinz.mymod.werewolf;

import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.exoskeleton.ExoModule;
import com.jheinz.mymod.exoskeleton.ExoModuleItem;

/**
 * Werewolves: villagers carrying a curse (see {@link WerewolfCurse}) that turn into a {@link Werewolf} on full-moon
 * nights and back at dawn. Their entity, the curse's data, the {@code mymod:werewolves} game rule that turns the
 * whole thing off, the Werewolf Pelt they drop and the Feral Lining exoskeleton module made from it, their sounds and
 * the testing command (see {@link WerewolfCommands}).
 */
public final class Werewolves {
	/** Whether villagers are ever cursed, and whether cursed ones change. Off: no new curses and no transformations. */
	public static final GameRule<Boolean> WEREWOLVES = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.SPAWNING)
			.buildAndRegister(MyMod.id("werewolves"));

	public static final EntityType<Werewolf> WEREWOLF = entity("werewolf", EntityType.Builder.of(Werewolf::new, MobCategory.MONSTER)
			// Drawn about 2.4 blocks tall to the ear tips, but hunched under 2 so it fits through the doors it smashes.
			.sized(0.8f, 1.95f)
			.eyeHeight(1.7f)
			.clientTrackingRange(10)
			.notInPeaceful());

	/**
	 * On a villager: whether it carries the curse. Missing until the villager is first seen (when it's rolled, see
	 * {@link WerewolfCurse#CURSE_CHANCE}); villagers cured of zombification get it set false so they're never rolled.
	 * Saved, never synced: nothing on the client can tell a cursed villager from any other.
	 */
	public static final AttachmentType<Boolean> CURSED = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.buildAndRegister(MyMod.id("werewolf_curse"));

	/** On a cursed villager mid-transformation: ticks left before it changes. Synced so clients shake it; not saved. */
	public static final AttachmentType<Integer> TRANSFORMING = AttachmentRegistry.<Integer>builder()
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
			.buildAndRegister(MyMod.id("werewolf_transforming"));

	public static final Item WEREWOLF_PELT = item("werewolf_pelt", Item::new, new Item.Properties());
	public static final Item FERAL_LINING_MODULE = item("feral_lining_module",
			properties -> new ExoModuleItem(properties, ExoModule.FERAL_LINING), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item WEREWOLF_SPAWN_EGG = item("werewolf_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(WEREWOLF));

	/** A low, rumbling growl: the werewolf's ambient sound, and now and then a cursed villager's tell. */
	public static final SoundEvent GROWL = sound("werewolf_growl");
	/** A long, low howl, heard right across a village: transforming, and every so often while it hunts. */
	public static final SoundEvent HOWL = sound("werewolf_howl");
	public static final SoundEvent HURT = sound("werewolf_hurt");
	public static final SoundEvent DEATH = sound("werewolf_death");
	/** The curse recoiling from holy things. */
	public static final SoundEvent HISS = sound("werewolf_hiss");

	private Werewolves() {
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
		FabricDefaultAttributeRegistry.register(WEREWOLF, Werewolf.createAttributes());
		WerewolfCurse.initialize();
		WerewolfCommands.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS)
				.register(output -> output.insertAfter(Items.SHEEP_SPAWN_EGG, WEREWOLF_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(output -> output.insertAfter(Items.RABBIT_HIDE, WEREWOLF_PELT));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
				.register(output -> output.insertAfter(ModItems.SERVO_LEGS_MODULE, FERAL_LINING_MODULE));
	}
}
