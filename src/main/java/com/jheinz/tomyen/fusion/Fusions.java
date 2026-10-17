package com.jheinz.tomyen.fusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.crow.Crows;
import com.jheinz.tomyen.industry.Industry;
import com.jheinz.tomyen.meteorite.Meteorites;

/**
 * Item Fusing: two items thrown into a fae pond with Fae Dust ({@link PondFusions}), or loaded into a Fusion Reactor
 * ({@link FusionReactorBlockEntity}), fuse into a fusion-only item that's a real upgrade on both. What fuses with
 * what is data ({@link FusionRecipeLoader}: the format, the approved list, and how to add a fusion). Players find out by
 * experimenting, nudged by riddles from villagers and fairies and a shimmer in the tooltip ({@link FusionHints}).
 *
 * <p>The first five fusions live here: the Swordfish Sword, Fish Fish Fish and Rocket Boots (pond), and Murder Wings
 * and the tier-2 Stormcrow Rig (reactor).
 */
public final class Fusions {
	// --- Materials ---------------------------------------------------------------------------------------------
	/** A cut above diamond: netherite's damage, nearly its durability. */
	public static final ToolMaterial SWORDFISH = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.0f, 4.0f, 15,
			TagKey.create(Registries.ITEM, MyMod.id("repairs_swordfish_sword")));
	/** Wood's bite, deliberately; it lasts a little longer, being fish. */
	public static final ToolMaterial FISH = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 250, 2.0f, 0.0f, 15,
			TagKey.create(Registries.ITEM, MyMod.id("repairs_fish_fish_fish")));

	// --- Items -------------------------------------------------------------------------------------------------
	public static final Item SWORDFISH_SWORD = item("swordfish_sword", SwordfishSwordItem::new,
			new Item.Properties().sword(SWORDFISH, 3.0f, -2.4f).rarity(Rarity.RARE));
	public static final Item FISH_FISH_FISH = item("fish_fish_fish", FishFishFishItem::new,
			new Item.Properties().sword(FISH, 3.0f, -2.4f).rarity(Rarity.UNCOMMON));
	public static final Item ROCKET_BOOTS = item("rocket_boots", RocketBootsItem::new, rocketBoots());
	public static final Item MURDER_WINGS = item("murder_wings", properties -> new FusionTooltipItem(properties, "item.tomyen.murder_wings.glide",
			"item.tomyen.murder_wings.crows"), wings("murder_wings", 1296, 3.0, 0.0, Crows.CROW_FEATHER));
	public static final Item STORMCROW_RIG = item("stormcrow_rig", properties -> new FusionTooltipItem(properties, "item.tomyen.stormcrow_rig.fly",
			"item.tomyen.stormcrow_rig.cost", "item.tomyen.stormcrow_rig.crows"),
			wings("stormcrow_rig", 2400, 6.0, 2.0, Meteorites.STAR_STEEL_INGOT));

	// --- The Fusion Reactor ------------------------------------------------------------------------------------
	public static final Block FUSION_REACTOR;
	public static final Item FUSION_REACTOR_ITEM;

	static {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id("fusion_reactor"));
		FUSION_REACTOR = Registry.register(BuiltInRegistries.BLOCK, key, new FusionReactorBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_CYAN).strength(5.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
				.noOcclusion().lightLevel(state -> state.getValue(FusionReactorBlock.LIT) ? 13 : 5).setId(key)));
		FUSION_REACTOR_ITEM = item("fusion_reactor", properties -> new BlockItem(FUSION_REACTOR, properties),
				new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.EPIC).fireResistant());
	}

	public static final BlockEntityType<FusionReactorBlockEntity> REACTOR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MyMod.id("fusion_reactor"), new BlockEntityType<>(FusionReactorBlockEntity::new, Set.of(FUSION_REACTOR)));
	public static final MenuType<FusionReactorMenu> REACTOR_MENU = Registry.register(BuiltInRegistries.MENU, MyMod.id("fusion_reactor"),
			new MenuType<>(FusionReactorMenu::new, FeatureFlags.VANILLA_SET));

	private Fusions() {
	}

	private static Item.Properties rocketBoots() {
		Identifier id = MyMod.id("rocket_boots");
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
				.add(Attributes.FALL_DAMAGE_MULTIPLIER, new AttributeModifier(id, -RocketBoots.FALL_REDUCTION,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.FEET)
				.build();
		// No equipment asset: drawn in code on the feet, like the Big Red Boots they were (RocketBootsRenderer).
		return new Item.Properties().stacksTo(1).durability(ArmorType.BOOTS.getDurability(33)).enchantable(15).repairable(Industry.RUBBER)
				.attributes(attributes).rarity(Rarity.RARE)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build());
	}

	/** An elytra-like glider worn on the chest, drawn as wings from textures/entity/equipment/wings/{name}.png. */
	private static Item.Properties wings(String name, int durability, double armor, double toughness, Item repair) {
		Identifier id = MyMod.id(name);
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.build();
		return new Item.Properties().durability(durability).rarity(Rarity.EPIC).enchantable(15).repairable(repair).attributes(attributes)
				.component(DataComponents.GLIDER, Unit.INSTANCE)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
						.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, id)).setDamageOnHurt(false).build());
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	// --- Packets -----------------------------------------------------------------------------------------------

	/** From the wearer's client: the Rocket Boots fired ({@link RocketBoots#DOUBLE_JUMP} or {@link RocketBoots#DASH}). */
	public record RocketBurst(int kind) implements CustomPacketPayload {
		public static final Type<RocketBurst> TYPE = new Type<>(MyMod.id("rocket_burst"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RocketBurst> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,
				RocketBurst::kind, RocketBurst::new);

		@Override
		public Type<RocketBurst> type() {
			return TYPE;
		}
	}

	/** To a player: the items they carry that hum near a pond or reactor (their partner is in the inventory too). */
	public record Shimmer(List<Identifier> items) implements CustomPacketPayload {
		public static final Type<Shimmer> TYPE = new Type<>(MyMod.id("fusion_shimmer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shimmer> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), Shimmer::items, Shimmer::new);

		@Override
		public Type<Shimmer> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(FusionRecipeLoader.ID, new FusionRecipeLoader());
		PayloadTypeRegistry.serverboundPlay().register(RocketBurst.TYPE, RocketBurst.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Shimmer.TYPE, Shimmer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RocketBurst.TYPE, (payload, context) -> RocketBoots.burst(context.player(), payload.kind()));
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			PondFusions.tick(level);
			StormcrowFlight.tick(level);
			FusionHints.tick(level);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PondFusions.releaseAll());
		MurderCrows.initialize();
		FusionHints.initialize();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_SWORD, SWORDFISH_SWORD, FISH_FISH_FISH);
			output.insertAfter(Items.NETHERITE_BOOTS, ROCKET_BOOTS);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.ELYTRA, MURDER_WINGS, STORMCROW_RIG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.insertAfter(Items.BEACON, FUSION_REACTOR_ITEM));
	}
}
