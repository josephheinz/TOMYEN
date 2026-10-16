package com.jheinz.tomyen.survival;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.jheinz.tomyen.MyMod;

/**
 * The Scavenger's survival kit: early, cheap gear that plugs into the invasion's mechanics rather than out-muscling it.
 * <ul>
 * <li>{@link CrowbarItem}: a knock-back club that pries iron doors open, rips scrap off pinned machines and breaks
 * crates and barrels fast.</li>
 * <li>Scrap armor ({@link ScrapArmor}): junk metal bolted and strapped together, between leather and chainmail, that
 * turns aside machine blows and electric zaps.</li>
 * <li>{@link MedicalItem}s: the Bandage and the First Aid Kit, which stop Bleeding (and the kit eases Radiation
 * Sickness).</li>
 * <li>Throwables: the {@link Molotov} (a fish-oil fire bomb, the early cousin of napalm) and the {@link PipeBomb}
 * (a short-fuse blast that's safe for blocks and stuns machines).</li>
 * <li>Placeables: the {@link BearTrapBlock} (and its silver-toothed variant for werewolves and the evil) and the
 * {@link TripwireAlarmBlock}, which rings and calls out what crossed it.</li>
 * <li>The {@link CanteenItem} (three sips of water or one potion) and the {@link SatchelItem} (nine slots on the go).</li>
 * </ul>
 */
public final class Survival {
	// --- Tags ------------------------------------------------------------------------------------------------------------
	/** Rogue machines: what scrap armor guards against, pipe bombs stun and crowbars strip. */
	public static final TagKey<EntityType<?>> MACHINES = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("machines"));
	/** Electric and energy damage (alien zaps, shorts, lasers): what scrap armor grounds. */
	public static final TagKey<DamageType> ELECTRIC = TagKey.create(Registries.DAMAGE_TYPE, MyMod.id("electric"));
	/** Crates, barrels and the like: what a crowbar breaks quickly. */
	public static final TagKey<Block> PRYABLE = TagKey.create(Registries.BLOCK, MyMod.id("pryable"));
	public static final TagKey<Item> REPAIRS_SCRAP_ARMOR = TagKey.create(Registries.ITEM, MyMod.id("repairs_scrap_armor"));

	public static final ResourceKey<DamageType> BEAR_TRAP_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, MyMod.id("bear_trap"));

	// --- Sounds (vanilla sounds re-pitched in sounds.json) ----------------------------------------------------------
	public static final SoundEvent CROWBAR_PRY = sound("crowbar_pry");
	public static final SoundEvent BEAR_TRAP_SNAP = sound("bear_trap_snap");
	public static final SoundEvent BEAR_TRAP_SET = sound("bear_trap_set");
	public static final SoundEvent ALARM_RING = sound("tripwire_alarm_ring");
	public static final SoundEvent PIPE_BOMB_FUSE = sound("pipe_bomb_fuse");
	public static final SoundEvent PIPE_BOMB_BLAST = sound("pipe_bomb_blast");
	public static final SoundEvent MOLOTOV_SHATTER = sound("molotov_shatter");
	public static final SoundEvent BANDAGE_WRAP = sound("bandage_wrap");
	public static final SoundEvent CANTEEN_SIP = sound("canteen_sip");
	public static final SoundEvent CANTEEN_FILL = sound("canteen_fill");
	public static final SoundEvent SATCHEL_OPEN = sound("satchel_open");
	public static final SoundEvent SCRAP_DEFLECT = sound("scrap_armor_deflect");
	public static final Holder<SoundEvent> EQUIP_SCRAP = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT,
			MyMod.id("item.armor.equip_scrap"), SoundEvent.createVariableRangeEvent(MyMod.id("item.armor.equip_scrap")));

	// --- Effects ---------------------------------------------------------------------------------------------------------
	/** Caught in a bear trap: can't walk or jump. */
	public static final Holder<MobEffect> TRAPPED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("trapped"),
			new SnaredEffect(MobEffectCategory.HARMFUL, 0x6B4A2E, "trapped"));
	/** Rattled by a pipe bomb: frozen in place, its machinery jammed. */
	public static final Holder<MobEffect> STUNNED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MyMod.id("stunned"),
			new SnaredEffect(MobEffectCategory.HARMFUL, 0xE8D25A, "stunned"));

	// --- Components ------------------------------------------------------------------------------------------------------
	/** Sips (or doses) left in a canteen. */
	public static final DataComponentType<Integer> SIPS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, MyMod.id("sips"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, CanteenItem.CAPACITY)).networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	// --- Blocks ----------------------------------------------------------------------------------------------------------
	public static final Block BEAR_TRAP = block("bear_trap", properties -> new BearTrapBlock(properties, false), trapProperties());
	public static final Block SILVER_BEAR_TRAP = block("silver_bear_trap", properties -> new BearTrapBlock(properties, true), trapProperties());
	public static final Block TRIPWIRE_ALARM = block("tripwire_alarm", TripwireAlarmBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.POPPED));

	// --- Entities --------------------------------------------------------------------------------------------------------
	public static final EntityType<Molotov> MOLOTOV = projectile("molotov_cocktail", Molotov::new);
	public static final EntityType<PipeBomb> PIPE_BOMB = projectile("pipe_bomb", PipeBomb::new);

	// --- Items -----------------------------------------------------------------------------------------------------------
	public static final Item CROWBAR = item("crowbar", CrowbarItem::new, crowbarProperties());

	// The "scrap" equipment asset has no file: worn pieces are drawn as a 3D model on the client (ScrapArmorRenderer).
	// The id stays so the game still treats the pieces as armor that's drawn by the armor layer, not a hat item.
	public static final ArmorMaterial SCRAP_ARMOR_MATERIAL = new ArmorMaterial(11,
			Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 3, ArmorType.CHESTPLATE, 5, ArmorType.HELMET, 2, ArmorType.BODY, 4),
			12, EQUIP_SCRAP, 0.0f, 0.0f, REPAIRS_SCRAP_ARMOR, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id("scrap")));
	public static final Item SCRAP_HELMET = armor("scrap_helmet", ArmorType.HELMET);
	public static final Item SCRAP_CHESTPLATE = armor("scrap_chestplate", ArmorType.CHESTPLATE);
	public static final Item SCRAP_LEGGINGS = armor("scrap_leggings", ArmorType.LEGGINGS);
	public static final Item SCRAP_BOOTS = armor("scrap_boots", ArmorType.BOOTS);

	public static final Item BANDAGE = item("bandage", properties -> new MedicalItem(properties, MedicalItem.BANDAGE),
			new Item.Properties().stacksTo(16));
	public static final Item FIRST_AID_KIT = item("first_aid_kit", properties -> new MedicalItem(properties, MedicalItem.FIRST_AID_KIT),
			new Item.Properties().durability(MedicalItem.FIRST_AID_KIT.uses()));

	public static final Item MOLOTOV_COCKTAIL = item("molotov_cocktail",
			properties -> new ThrowableItem(properties, Molotov::new, level -> new Molotov(MOLOTOV, level), 1.0f, 15),
			new Item.Properties().stacksTo(16));
	public static final Item PIPE_BOMB_ITEM = item("pipe_bomb",
			properties -> new ThrowableItem(properties, PipeBomb::new, level -> new PipeBomb(PIPE_BOMB, level), 0.9f, 20),
			new Item.Properties().stacksTo(16));

	public static final Item CANTEEN = item("canteen", CanteenItem::new, new Item.Properties().stacksTo(1).component(SIPS, 0));
	public static final Item SCAVENGERS_SATCHEL = item("scavengers_satchel", SatchelItem::new,
			new Item.Properties().stacksTo(1).component(DataComponents.CONTAINER,
					ItemContainerContents.EMPTY));

	private Survival() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item armor(String name, ArmorType type) {
		return item(name, Item::new, new Item.Properties().humanoidArmor(SCRAP_ARMOR_MATERIAL, type));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MyMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		item(name, itemProperties -> new BlockItem(block, itemProperties), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static BlockBehaviour.Properties trapProperties() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 4.0f).sound(SoundType.CHAIN)
				.noCollision().noOcclusion().pushReaction(PushReaction.POPPED);
	}

	private static <T extends Entity> EntityType<T> projectile(String name, EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(factory, MobCategory.MISC)
				.noLootTable().sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build(key));
	}

	/**
	 * A crowbar: about an iron sword's hit at a heavy club's pace with a strong shove, lasting a little longer than iron
	 * tools; it breaks {@link #PRYABLE} blocks quickly and knocks a raised shield aside for a moment.
	 */
	private static Item.Properties crowbarProperties() {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return new Item.Properties()
				.durability(320)
				.repairable(ItemTags.IRON_TOOL_MATERIALS)
				.enchantable(14)
				.component(DataComponents.TOOL, new Tool(List.of(Tool.Rule.overrideSpeed(blocks.getOrThrow(PRYABLE), 7.0f)), 1.0f, 1, false))
				.component(DataComponents.WEAPON, new Weapon(1, 2.0f))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 5.0, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.9, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(MyMod.id("crowbar_knockback"), 1.0,
								AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
						.build());
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.IRON_AXE, CROWBAR);
			output.insertAfter(Items.LEATHER_BOOTS, SCRAP_HELMET, SCRAP_CHESTPLATE, SCRAP_LEGGINGS, SCRAP_BOOTS);
			output.insertAfter(Items.WIND_CHARGE, MOLOTOV_COCKTAIL, PIPE_BOMB_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.BRUSH, CROWBAR);
			output.insertAfter(Items.BUNDLE, SCAVENGERS_SATCHEL);
			output.insertAfter(Items.MILK_BUCKET, CANTEEN);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.insertAfter(Items.HONEY_BOTTLE, BANDAGE, FIRST_AID_KIT);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.insertAfter(Items.TRIPWIRE_HOOK, TRIPWIRE_ALARM, BEAR_TRAP, SILVER_BEAR_TRAP);
		});
		DispenserBlock.registerProjectileBehavior(MOLOTOV_COCKTAIL);
		DispenserBlock.registerProjectileBehavior(PIPE_BOMB_ITEM);
		CrowbarItem.initialize();
		ScrapArmor.initialize();
	}
}
