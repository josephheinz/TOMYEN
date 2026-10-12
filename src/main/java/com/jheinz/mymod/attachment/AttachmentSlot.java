package com.jheinz.mymod.attachment;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;

import com.jheinz.mymod.ModComponents;

/** Where on a gun an attachment goes. A gun holds at most one attachment per slot. */
public enum AttachmentSlot {
	// Listed in the order an empty-handed right-click takes them off: outermost first.
	OPTIC(ModComponents.OPTIC_ATTACHMENT),
	UNDERBARREL(ModComponents.UNDERBARREL_ATTACHMENT),
	GRIP(ModComponents.GRIP_ATTACHMENT),
	MUZZLE(ModComponents.MUZZLE_ATTACHMENT),
	FIRE_CONTROL(ModComponents.FIRE_CONTROL_ATTACHMENT),
	/** The rocket launcher's homing seeker pod. */
	GUIDANCE(ModComponents.GUIDANCE_ATTACHMENT);

	private final DataComponentType<Holder<Item>> component;

	AttachmentSlot(DataComponentType<Holder<Item>> component) {
		this.component = component;
	}

	public DataComponentType<Holder<Item>> component() {
		return component;
	}
}
