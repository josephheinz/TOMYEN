package com.jheinz.mymod.exoskeleton;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.MyMod;

/**
 * A powered exoskeleton: a frame over the whole body. It goes in the chest slot but takes up every armor slot,
 * so the head, legs and feet must be empty to put it on and nothing can go in them while it's worn (see
 * {@link #refusal}), not even a Neuralink. Armor goes in its sockets instead: {@link ExoSockets#COUNT} of
 * them, each holding a module, or a piece of armor or an elytra that the frame wears for you (see
 * {@link ExoSockets}). An exoskeleton with nothing socketed is also the frame power armor is worn over (see
 * {@link com.jheinz.mymod.powerarmor.PowerArmor}), whose helmet has Neuralink sockets of its own.
 *
 * <p>Using it in hand opens its sockets; sneak-use puts it on. What's socketed works only while it's worn, and
 * falls out if the suit breaks. On its own it protects about as well as a full set of armor between iron and
 * diamond ({@link #ARMOR} armor, {@link #TOUGHNESS} toughness), shrugs off some knockback, and its weight
 * costs {@link #SPEED_PENALTY} of the wearer's speed.
 */
public class ExoskeletonItem extends Item {
	// A full iron set is 15 armor and no toughness, diamond 20 and 8.
	public static final double ARMOR = 18.0;
	public static final double TOUGHNESS = 3.0;
	public static final double KNOCKBACK_RESISTANCE = 0.2;
	/** Fraction of movement speed lost. */
	public static final double SPEED_PENALTY = 0.05;
	/** Around a diamond chestplate's: the suit is one piece, so it takes the wear of one piece in a set. */
	public static final int DURABILITY = 560;

	private static final Component TITLE = Component.translatable("container.mymod.exoskeleton");
	/** The armor slots the suit fills besides its own. */
	private static final List<EquipmentSlot> COVERED = List.of(EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	public ExoskeletonItem(Properties properties) {
		super(properties);
	}

	/** The suit's base stats: equipped to the chest, worn by iron, repaired with iron ingots. */
	public static Item.Properties properties() {
		Identifier id = MyMod.id("exoskeleton");
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, TOUGHNESS, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.CHEST)
				.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.CHEST)
				.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, -SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
						EquipmentSlotGroup.CHEST)
				.build();
		// No equipment asset: the frame is drawn in code (ExoskeletonRenderer), not as an armor texture.
		return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(DURABILITY).repairable(Items.IRON_INGOT)
				.attributes(attributes)
				.component(DataComponents.EQUIPPABLE,
						Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE).build());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive()) {
			// Equips it; the slot rules are checked where every equip goes through (see EquippableExoskeletonMixin).
			return super.use(level, player, hand);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, user) -> new ExoskeletonMenu(containerId, inventory, hand), TITLE));
		}
		return InteractionResult.SUCCESS;
	}

	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ExoskeletonItem;
	}

	/**
	 * Why a stack can't go in one of an entity's armor slots because of the exoskeleton, or null if the suit
	 * doesn't stand in the way: the suit needs the head, legs and feet clear, and while it's worn they stay that way.
	 */
	public static @Nullable Component refusal(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
		if (stack.isEmpty()) {
			return null;
		}
		if (slot == EquipmentSlot.CHEST && stack.getItem() instanceof ExoskeletonItem) {
			for (EquipmentSlot covered : COVERED) {
				if (!mayStayWithSuit(entity.getItemBySlot(covered), covered)) {
					return Component.translatable("item.mymod.exoskeleton.slots_occupied");
				}
			}
			return null;
		}
		if (COVERED.contains(slot) && isWearing(entity) && !mayStayWithSuit(stack, slot)) {
			return Component.translatable("item.mymod.exoskeleton.slot_blocked");
		}
		return null;
	}

	/** Whether a worn stack is allowed in a covered slot while the suit is on: only nothing. */
	static boolean mayStayWithSuit(ItemStack stack, EquipmentSlot slot) {
		return stack.isEmpty();
	}

	static List<EquipmentSlot> coveredSlots() {
		return COVERED;
	}

	/** The modules installed in an exoskeleton stack. */
	public static Set<ExoModule> modules(ItemStack suit) {
		Set<ExoModule> modules = EnumSet.noneOf(ExoModule.class);
		suit.getOrDefault(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
				.forEach(stack -> {
					if (stack.getItem() instanceof ExoModuleItem module) {
						modules.add(module.module());
					}
				});
		return modules;
	}

	/** The modules working for an entity: those in the exoskeleton it wears, if it wears one. */
	public static Set<ExoModule> activeModules(LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		return chest.getItem() instanceof ExoskeletonItem ? modules(chest) : EnumSet.noneOf(ExoModule.class);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		List<ItemStack> socketed = ExoSockets.contents(stack).stream().filter(item -> !item.isEmpty()).toList();
		tooltip.accept(Component.translatable("item.mymod.exoskeleton.sockets", socketed.size(), ExoSockets.COUNT).withStyle(ChatFormatting.GOLD));
		if (socketed.isEmpty()) {
			tooltip.accept(Component.translatable("item.mymod.exoskeleton.nothing_socketed").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (ItemStack item : socketed) {
			if (item.getItem() instanceof ExoModuleItem module) {
				tooltip.accept(Component.literal("▪ ").append(module.module().displayName()).withColor(module.module().color()));
			} else {
				tooltip.accept(Component.literal("▪ ").append(Component.translatable("item.mymod.exoskeleton.socketed", item.getHoverName(),
						socketKind(item))).withStyle(ChatFormatting.GRAY));
			}
		}
		tooltip.accept(Component.translatable("item.mymod.exoskeleton.full_body").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.exoskeleton.open").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.exoskeleton.wear").withStyle(ChatFormatting.GRAY));
	}

	/** What a socketed stack is there as: a module, an elytra, or the armor for one slot. */
	public static Component socketKind(ItemStack socketed) {
		EquipmentSlot slot = ExoSockets.armorSlot(socketed);
		if (slot == null) {
			return Component.translatable("container.mymod.exoskeleton.kind.module");
		}
		String kind = ExoSockets.isGlider(socketed) ? "elytra" : slot.getSerializedName();
		return Component.translatable("container.mymod.exoskeleton.kind." + kind);
	}
}
