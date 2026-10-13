package com.jheinz.mymod.exoskeleton;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.gameevent.GameEvent;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.neuralink.NeuralinkItem;
import com.jheinz.mymod.powerarmor.PowerArmorPieceItem;

/**
 * The exoskeleton's sockets. Each takes a module (one of each) or something the frame wears for you, since the
 * suit itself fills every armor slot: any item that equips to the head, chest, legs or feet (modded armor, a
 * turtle shell, a carved pumpkin, a mob head) or an elytra, but not another exoskeleton, a Neuralink or power
 * armor. One piece per armor slot, so a chestplate and an elytra rule each other out.
 *
 * <p>Gear ({@link #GEAR}, such as the Optic Monocle and the Servo Arm Backpack) is worn in an armor slot too, but in
 * a socket it isn't armor: it doesn't take up its slot, so it goes in alongside a helmet, a chestplate or an elytra
 * (one of each gear item). It still counts as worn in its slot for attributes and enchantments, and is drawn by its
 * own renderer rather than as the slot's armor.
 *
 * <p>While the suit is worn its socketed pieces count as worn, through hooks in vanilla's own equipment code:
 * their attribute modifiers (and enchantment attributes) come with the suit's ({@link #forEachModifier}), their
 * enchantments run in their slot ({@link #runEnchantments}), they wear down when you're hit
 * ({@link #hurtArmor}), and a socketed elytra glides ({@link #glide}). Pieces are kept as copies on the suit,
 * so anything that changes one goes through {@link #edit}, which writes it back. A few vanilla checks look at
 * the armor slots directly and are pointed here too: a turtle shell's water breathing, gold armor for piglins, a
 * carved pumpkin for endermen, and a plate carrier's plate. Not carried over: Mending (XP only repairs items
 * in slots), the carved pumpkin's view overlay, the creaking's stare check, and break particles.
 */
public final class ExoSockets {
	/** Four armor slots and both modules fit at once. */
	public static final int COUNT = 6;
	public static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET);
	/** Wearables that socket as gear, not armor: they don't take up their armor slot. */
	public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, MyMod.id("exo_gear"));

	private ExoSockets() {
	}

	/** Both sides: the client asks too, to start gliding. */
	public static void initialize() {
		EntityElytraEvents.CUSTOM.register(ExoSockets::glide);
	}

	/** The armor slot a stack fills in a socket, or null if it isn't armor the frame can wear (gear isn't). */
	public static @Nullable EquipmentSlot armorSlot(ItemStack stack) {
		return isGear(stack) ? null : wornSlot(stack);
	}

	/** Whether a stack is gear: something wearable that sockets without taking up its armor slot. */
	public static boolean isGear(ItemStack stack) {
		return !stack.isEmpty() && stack.is(GEAR) && wornSlot(stack) != null;
	}

	/**
	 * The armor slot a socketed stack counts as worn in (its armor slot, or the slot gear is worn in), or null if it
	 * isn't something the frame can wear.
	 */
	public static @Nullable EquipmentSlot wornSlot(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem() instanceof ExoskeletonItem || stack.getItem() instanceof NeuralinkItem
				|| stack.getItem() instanceof PowerArmorPieceItem) {
			return null;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || !ARMOR_SLOTS.contains(equippable.slot())
				|| !equippable.canBeEquippedBy(EntityTypes.PLAYER.builtInRegistryHolder())) {
			return null;
		}
		return equippable.slot();
	}

	public static boolean isGlider(ItemStack stack) {
		return stack.has(DataComponents.GLIDER) && armorSlot(stack) == EquipmentSlot.CHEST;
	}

	/** Whether a stack goes in a socket at all: a module or something wearable (armor or gear). */
	public static boolean fits(ItemStack stack) {
		return stack.getItem() instanceof ExoModuleItem || wornSlot(stack) != null;
	}

	/**
	 * Whether a stack may go in one of the sockets, given what's in the others: one piece of armor per armor slot, one
	 * of each module and one of each gear item.
	 */
	public static boolean mayPlace(List<ItemStack> sockets, int socket, ItemStack stack) {
		if (!fits(stack)) {
			return false;
		}
		EquipmentSlot slot = armorSlot(stack);
		ExoModule module = stack.getItem() instanceof ExoModuleItem item ? item.module() : null;
		boolean gear = isGear(stack);
		for (int i = 0; i < sockets.size(); i++) {
			ItemStack other = sockets.get(i);
			if (i == socket || other.isEmpty()) {
				continue;
			}
			boolean clash = gear ? other.is(stack.getItem())
					: slot != null ? armorSlot(other) == slot
					: other.getItem() instanceof ExoModuleItem item && item.module() == module;
			if (clash) {
				return false;
			}
		}
		return true;
	}

	/** Copies of what's in a suit's sockets, in order. */
	public static NonNullList<ItemStack> contents(ItemStack suit) {
		NonNullList<ItemStack> sockets = NonNullList.withSize(COUNT, ItemStack.EMPTY);
		suit.getOrDefault(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.EMPTY).copyInto(sockets);
		return sockets;
	}

	/** Whether nothing at all is socketed: what power armor needs of its frame. */
	public static boolean isEmpty(ItemStack suit) {
		return !suit.getOrDefault(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.EMPTY).nonEmptyItems().iterator().hasNext();
	}

	/** The piece in a suit's sockets that fills an armor slot, or empty. */
	public static ItemStack armor(ItemStack suit, EquipmentSlot slot) {
		if (!(suit.getItem() instanceof ExoskeletonItem)) {
			return ItemStack.EMPTY;
		}
		for (ItemStack stack : contents(suit)) {
			if (armorSlot(stack) == slot) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The piece the exoskeleton an entity wears holds for an armor slot, or empty. */
	public static ItemStack worn(LivingEntity entity, EquipmentSlot slot) {
		return armor(entity.getItemBySlot(EquipmentSlot.CHEST), slot);
	}

	/** The gear socketed in a suit (see {@link #GEAR}), or none. */
	public static List<ItemStack> gear(ItemStack suit) {
		if (!(suit.getItem() instanceof ExoskeletonItem) || isEmpty(suit)) {
			return List.of();
		}
		return contents(suit).stream().filter(ExoSockets::isGear).toList();
	}

	/** The stack of an item socketed in the exoskeleton an entity wears, armor or gear (not a module), or empty. */
	public static ItemStack socketed(LivingEntity entity, Item item) {
		ItemStack suit = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (suit.getItem() instanceof ExoskeletonItem && !isEmpty(suit)) {
			for (ItemStack stack : contents(suit)) {
				if (stack.is(item) && wornSlot(stack) != null) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	/** Whether the exoskeleton an entity wears has an item socketed, as armor or gear. */
	public static boolean hasSocketed(LivingEntity entity, Item item) {
		return !socketed(entity, item).isEmpty();
	}

	/** The armor pieces socketed in the exoskeleton an entity wears (not its modules or gear), or none. */
	public static List<ItemStack> socketedArmor(LivingEntity entity) {
		ItemStack suit = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (!(suit.getItem() instanceof ExoskeletonItem) || isEmpty(suit)) {
			return List.of();
		}
		return contents(suit).stream().filter(stack -> armorSlot(stack) != null).toList();
	}

	/** Whether the exoskeleton an entity wears holds a piece (armor or gear) that matches. */
	public static boolean wears(LivingEntity entity, Predicate<ItemStack> test) {
		ItemStack suit = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (suit.getItem() instanceof ExoskeletonItem) {
			for (ItemStack stack : contents(suit)) {
				if (wornSlot(stack) != null && test.test(stack)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Edits copies of the socketed pieces, armor and gear (each with the slot it counts as worn in), and writes any
	 * change back to the suit: durability, or a piece used up.
	 */
	static void edit(ItemStack suit, BiConsumer<EquipmentSlot, ItemStack> edit) {
		NonNullList<ItemStack> sockets = contents(suit);
		List<ItemStack> before = sockets.stream().map(ItemStack::copy).toList();
		for (ItemStack stack : sockets) {
			EquipmentSlot slot = wornSlot(stack);
			if (slot != null) {
				edit.accept(slot, stack);
			}
		}
		for (int i = 0; i < COUNT; i++) {
			if (!ItemStack.matches(before.get(i), sockets.get(i))) {
				suit.set(ModComponents.EXOSKELETON_MODULES, ItemContainerContents.fromItems(sockets));
				return;
			}
		}
	}

	// ------------------------------------------------------------------------------------------ vanilla hooks

	/**
	 * The attribute modifiers the socketed pieces (armor and gear) give, each as if worn in its own slot (enchantment
	 * attributes included): a socketed Servo Arm Backpack's reach, say. Vanilla adds and removes these along with the suit's own whenever the suit changes, so they come
	 * and go as it's put on, taken off or a piece breaks.
	 */
	public static void forEachModifier(ItemStack suit, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
		for (ItemStack stack : contents(suit)) {
			EquipmentSlot slot = wornSlot(stack);
			if (slot != null && !stack.isBroken()) {
				stack.forEachModifier(slot, consumer);
			}
		}
	}

	/**
	 * Runs a visitor over the enchantments of the socketed pieces, each in its own slot, as vanilla does over
	 * worn armor: protection, Thorns, Frost Walker, Soul Speed and the rest. Damage an enchantment does to its
	 * piece (Thorns, Soul Speed) is written back.
	 */
	public static void runEnchantments(ItemStack suit, LivingEntity owner, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
		if (isEmpty(suit)) {
			return;
		}
		edit(suit, (slot, stack) -> {
			ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
			if (enchantments == null || enchantments.isEmpty()) {
				return;
			}
			EnchantedItemInUse inUse = new EnchantedItemInUse(stack, slot, owner, broken -> broke(owner, broken));
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
				if (entry.getKey().value().matchingSlot(slot)) {
					visitor.accept(entry.getKey(), entry.getIntValue(), inUse);
				}
			}
		});
	}

	/** Wears down the socketed pieces for the armor slots a hit landed on, as vanilla wears worn armor. */
	public static void hurtArmor(LivingEntity entity, DamageSource source, float damage, EquipmentSlot[] slots) {
		ItemStack suit = entity.getItemBySlot(EquipmentSlot.CHEST);
		if (damage <= 0.0f || !(suit.getItem() instanceof ExoskeletonItem) || !(entity.level() instanceof ServerLevel level)
				|| isEmpty(suit)) {
			return;
		}
		int durabilityDamage = (int) Math.max(1.0f, damage / 4.0f);
		List<EquipmentSlot> hit = List.of(slots);
		edit(suit, (slot, stack) -> {
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (hit.contains(slot) && equippable != null && equippable.damageOnHurt() && stack.isDamageableItem()
					&& stack.canBeHurtBy(source)) {
				stack.hurtAndBreak(durabilityDamage, level, entity instanceof ServerPlayer player ? player : null, broken -> broke(entity, broken));
			}
		});
	}

	/**
	 * Fabric's custom elytra hook, asked wherever vanilla checks for a worn glider: whether the exoskeleton an
	 * entity wears holds an elytra it can glide on (vanilla's own check, so not one about to break). Vanilla has
	 * already checked the wearer is airborne, not riding and not levitating. When the flight ticks (every second
	 * of gliding), the elytra wears down a point and the glide is heard, as a worn one's would be.
	 */
	private static boolean glide(LivingEntity entity, boolean tickElytra) {
		ItemStack glider = worn(entity, EquipmentSlot.CHEST);
		if (!isGlider(glider) || !LivingEntity.canGlideUsing(glider, EquipmentSlot.CHEST)) {
			return false;
		}
		if (tickElytra && entity.level() instanceof ServerLevel level) {
			edit(entity.getItemBySlot(EquipmentSlot.CHEST), (slot, stack) -> {
				if (isGlider(stack)) {
					stack.hurtAndBreak(1, level, entity instanceof ServerPlayer player ? player : null, broken -> broke(entity, broken));
				}
			});
			entity.gameEvent(GameEvent.ELYTRA_GLIDE);
		}
		return true;
	}

	/** When a worn exoskeleton breaks, what was in its sockets falls out instead of breaking with it. */
	public static void spill(LivingEntity entity, ItemStack brokenSuit) {
		if (!(brokenSuit.getItem() instanceof ExoskeletonItem) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		for (ItemStack stack : contents(brokenSuit)) {
			if (stack.isEmpty()) {
				continue;
			}
			if (entity instanceof Player player) {
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			} else {
				entity.spawnAtLocation(level, stack);
			}
		}
	}

	/** A socketed piece broke: vanilla's break sound (the slot it'd show particles for is empty). */
	private static void broke(LivingEntity entity, ItemStack broken) {
		Holder<SoundEvent> sound = broken.get(DataComponents.BREAK_SOUND);
		if (sound != null && !entity.isSilent()) {
			entity.level().playSound(null, entity, sound, entity.getSoundSource(), 0.8f, 0.8f + entity.getRandom().nextFloat() * 0.4f);
		}
	}
}
