package com.jheinz.tomyen.powerarmor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.ModBlocks;
import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.MyMod;

/**
 * Everything power armor registers: the Power Armor Station, the eighteen pieces (six pieces in three marks,
 * each its own item: see {@link PowerArmorTier}), fusion cores and the plating kits that upgrade pieces, the
 * parked suit, and the data a worn suit keeps on its player. The behaviour lives in {@link PowerArmor}.
 */
public final class PowerArmors {
	/** On a fusion core: its charge, 0 to {@link FusionCoreItem#MAX_CHARGE}. Absent means full. */
	public static final DataComponentType<Integer> FUSION_CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("fusion_charge"), DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/**
	 * On a player: the power armor they're wearing, absent when they aren't. Saved with them and synced to
	 * everyone, since every client draws the suit (and the wearer's own needs its helmet's Neuralink modules).
	 * Not kept through death: the suit is left parked where they fell.
	 */
	public static final AttachmentType<PowerArmorSuit> SUIT = AttachmentRegistry.<PowerArmorSuit>builder()
			.persistent(PowerArmorSuit.CODEC)
			.syncWith(PowerArmorSuit.STREAM_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(MyMod.id("power_armor"));

	/** On a player in power armor: thrusters and the entry/exit sequence, for everyone's animations. Not saved. */
	public static final AttachmentType<PowerArmorMotion> MOTION = AttachmentRegistry.<PowerArmorMotion>builder()
			.syncWith(PowerArmorMotion.STREAM_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(MyMod.id("power_armor_motion"));

	public static final ResourceKey<DamageType> REPULSOR = damageType("repulsor");
	public static final ResourceKey<DamageType> UNIBEAM = damageType("unibeam");
	public static final ResourceKey<DamageType> SHOCKWAVE = damageType("power_armor_shockwave");

	private static final Map<PowerArmorPiece, Map<PowerArmorTier, Item>> PIECES = new EnumMap<>(PowerArmorPiece.class);

	static {
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			Map<PowerArmorTier, Item> tiers = new EnumMap<>(PowerArmorTier.class);
			for (PowerArmorTier tier : PowerArmorTier.values()) {
				tiers.put(tier, item("power_armor_" + piece.id() + "_" + tier.id(), properties -> new PowerArmorPieceItem(properties, piece, tier),
						PowerArmorPieceItem.properties(piece, tier)));
			}
			PIECES.put(piece, tiers);
		}
	}

	public static final Item FUSION_CORE = item("fusion_core", FusionCoreItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	/** Diamonds and computer chips: turns a Mk I piece into a Mk II in the Foundry. */
	public static final Item MK2_PLATING_KIT = item("mk2_plating_kit", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	/** Netherite and more chips: turns a Mk II piece into a Mk III in the Foundry. */
	public static final Item MK3_PLATING_KIT = item("mk3_plating_kit", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());

	public static final Block STATION = block("power_armor_station", PowerArmorStationBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(5.0f, 9.0f).sound(SoundType.METAL)
					.requiresCorrectToolForDrops().noOcclusion());
	public static final BlockEntityType<PowerArmorStationBlockEntity> STATION_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("power_armor_station"), new BlockEntityType<>(PowerArmorStationBlockEntity::new, Set.of(STATION)));
	public static final MenuType<PowerArmorStationMenu> STATION_MENU = Registry.register(BuiltInRegistries.MENU,
			MyMod.id("power_armor_station"), new MenuType<>(PowerArmorStationMenu::new, FeatureFlags.VANILLA_SET));

	public static final EntityType<ParkedPowerArmor> PARKED = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("parked_power_armor")),
			EntityType.Builder.<ParkedPowerArmor>of(ParkedPowerArmor::new, MobCategory.MISC)
					.sized(0.9f, 2.3f)
					.fireImmune()
					.noLootTable()
					.clientTrackingRange(10)
					.build(ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id("parked_power_armor"))));

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

	private PowerArmors() {
	}

	/** The item for a piece of a mark. */
	public static Item piece(PowerArmorPiece piece, PowerArmorTier tier) {
		return PIECES.get(piece).get(tier);
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static ResourceKey<DamageType> damageType(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id(name));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			List<Item> entries = new ArrayList<>(List.of(FUSION_CORE, MK2_PLATING_KIT, MK3_PLATING_KIT));
			for (PowerArmorTier tier : PowerArmorTier.values()) {
				for (PowerArmorPiece piece : PowerArmorPiece.values()) {
					entries.add(piece(piece, tier));
				}
			}
			output.insertAfter(ModItems.SERVO_LEGS_MODULE, entries.toArray(Item[]::new));
		});
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS_TAB).register(output -> output.insertAfter(ModBlocks.FOUNDRY, STATION));
		PowerArmorNetworking.initialize();
		PowerArmor.initialize();
	}
}
