package com.jheinz.mymod.station;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.attachment.AttachmentItem;
import com.jheinz.mymod.attachment.AttachmentSlot;
import com.jheinz.mymod.attachment.Attachments;

/**
 * The Weapon Station's other job: a gun plus attachments in the inputs makes the same gun (ammo, wear and
 * all) with them fitted. Each attachment has to fit the gun and go in a slot that's still free.
 */
public final class AttachmentFitting {
	private AttachmentFitting() {
	}

	/** The fitted gun, or null if the inputs aren't one gun plus attachments that all fit it. */
	public static ItemStack result(Container inputs) {
		ItemStack gun = null;
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.getItem() instanceof GunItem) {
				if (gun != null) {
					return null;
				}
				gun = stack;
			}
		}
		if (gun == null) {
			return null;
		}

		ItemStack fitted = gun.copyWithCount(1);
		Set<AttachmentSlot> used = EnumSet.noneOf(AttachmentSlot.class);
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.isEmpty() || stack == gun) {
				continue;
			}
			if (!(stack.getItem() instanceof AttachmentItem attachment) || !attachment.fits(gun.getItem())
					|| Attachments.get(gun, attachment.slot()) != null || !used.add(attachment.slot())) {
				return null;
			}
			Attachments.attach(fitted, attachment);
		}
		return used.isEmpty() ? null : fitted;
	}

	/** Uses up the gun and one of each attachment; callers check {@link #result} first. */
	public static void consume(Container inputs) {
		for (int i = 0; i < inputs.getContainerSize(); i++) {
			if (!inputs.getItem(i).isEmpty()) {
				inputs.removeItem(i, 1);
			}
		}
	}
}
