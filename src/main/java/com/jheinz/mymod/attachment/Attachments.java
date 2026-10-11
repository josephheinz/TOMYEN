package com.jheinz.mymod.attachment;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.ModItems;

/** Reading and changing the attachments fitted to a gun stack. */
public final class Attachments {
	private Attachments() {
	}

	/** The attachment in this slot, or null. */
	public static AttachmentItem get(ItemStack gun, AttachmentSlot slot) {
		Holder<Item> fitted = gun.get(slot.component());
		return fitted != null && fitted.value() instanceof AttachmentItem attachment ? attachment : null;
	}

	public static boolean has(ItemStack gun, Item attachment) {
		return attachment instanceof AttachmentItem a && get(gun, a.slot()) == a;
	}

	/** Whether shots are muffled: no tracer or muzzle smoke, and much quieter. */
	public static boolean suppressed(ItemStack gun) {
		AttachmentItem muzzle = get(gun, AttachmentSlot.MUZZLE);
		return muzzle == ModItems.SUPPRESSOR || muzzle == ModItems.OIL_CAN_SUPPRESSOR;
	}

	/** Whether the muzzle device hides the smoke puff at the muzzle. */
	public static boolean hidesMuzzleSmoke(ItemStack gun) {
		return suppressed(gun) || get(gun, AttachmentSlot.MUZZLE) == ModItems.FLASH_HIDER;
	}

	/** How loud a shot is, relative to the bare gun. */
	public static float loudness(ItemStack gun) {
		return suppressed(gun) ? 0.3f : get(gun, AttachmentSlot.MUZZLE) == ModItems.MUZZLE_BRAKE ? 1.3f : 1.0f;
	}

	/** Every fitted attachment, in slot order. */
	public static List<AttachmentItem> all(ItemStack gun) {
		List<AttachmentItem> fitted = new ArrayList<>();
		for (AttachmentSlot slot : AttachmentSlot.values()) {
			AttachmentItem attachment = get(gun, slot);
			if (attachment != null) {
				fitted.add(attachment);
			}
		}
		return fitted;
	}

	/** Fits the attachment, returning whatever it replaced (or an empty stack). */
	public static ItemStack attach(ItemStack gun, AttachmentItem attachment) {
		AttachmentItem previous = get(gun, attachment.slot());
		gun.set(attachment.slot().component(), attachment.builtInRegistryHolder());
		// A switch is fitted to fire full-auto, so start there.
		if (attachment.slot() == AttachmentSlot.FIRE_CONTROL) {
			gun.set(ModComponents.FIRE_MODE, FireMode.AUTO);
		}
		return previous == null ? ItemStack.EMPTY : new ItemStack(previous);
	}

	/** Takes off the outermost attachment, returning it (or an empty stack if there are none). */
	public static ItemStack detachOne(ItemStack gun) {
		for (AttachmentSlot slot : AttachmentSlot.values()) {
			AttachmentItem attachment = get(gun, slot);
			if (attachment != null) {
				gun.remove(slot.component());
				return new ItemStack(attachment);
			}
		}
		return ItemStack.EMPTY;
	}
}
