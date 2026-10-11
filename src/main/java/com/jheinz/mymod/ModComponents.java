package com.jheinz.mymod;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;

public final class ModComponents {
	/** On a magazine item: the rounds it holds. Absent means the magazine is empty. */
	public static final DataComponentType<MagazineContents> MAGAZINE_CONTENTS = register("magazine_contents");

	/** On a gun: the inserted magazine. Absent means no magazine; present with 0 rounds means an empty one. */
	public static final DataComponentType<MagazineContents> LOADED_MAGAZINE = register("loaded_magazine");

	/** On a gun: which magazine item is inserted. Absent means the gun's standard magazine. */
	public static final DataComponentType<Holder<Item>> LOADED_MAGAZINE_TYPE =
			register("loaded_magazine_type", Item.CODEC, Item.STREAM_CODEC);

	/** On a gun: the selected fire mode. Absent means the gun's default. */
	public static final DataComponentType<FireMode> FIRE_MODE = register("fire_mode", FireMode.CODEC, FireMode.STREAM_CODEC);

	/** On a bolt-action gun: the last shot left the bolt open, so it must be worked before firing again. */
	public static final DataComponentType<Boolean> BOLT_OPEN = register("bolt_open", Codec.BOOL, ByteBufCodecs.BOOL);

	// On a gun: the attachment fitted in each slot (see AttachmentSlot). Absent means the slot is empty.
	public static final DataComponentType<Holder<Item>> MUZZLE_ATTACHMENT = register("muzzle_attachment", Item.CODEC, Item.STREAM_CODEC);
	public static final DataComponentType<Holder<Item>> UNDERBARREL_ATTACHMENT = register("underbarrel_attachment", Item.CODEC, Item.STREAM_CODEC);
	public static final DataComponentType<Holder<Item>> GRIP_ATTACHMENT = register("grip_attachment", Item.CODEC, Item.STREAM_CODEC);
	public static final DataComponentType<Holder<Item>> OPTIC_ATTACHMENT = register("optic_attachment", Item.CODEC, Item.STREAM_CODEC);
	public static final DataComponentType<Holder<Item>> FIRE_CONTROL_ATTACHMENT = register("fire_control_attachment", Item.CODEC, Item.STREAM_CODEC);

	/** On a plate carrier: the inserted armor plate item. Absent means no plate. */
	public static final DataComponentType<Holder<Item>> ARMOR_PLATE = register("armor_plate", Item.CODEC, Item.STREAM_CODEC);

	/** On a Neuralink: the modules in its sockets. */
	public static final DataComponentType<ItemContainerContents> NEURALINK_MODULES =
			register("neuralink_modules", ItemContainerContents.CODEC, ItemContainerContents.STREAM_CODEC);

	private ModComponents() {
	}

	private static DataComponentType<MagazineContents> register(String name) {
		return register(name, MagazineContents.CODEC, MagazineContents.STREAM_CODEC);
	}

	private static <T> DataComponentType<T> register(String name, Codec<T> codec,
			StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MyMod.id(name),
				DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
	}

	public static void initialize() {
	}
}
