package com.jheinz.mymod.gunsmith;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.attachment.Attachments;
import com.jheinz.mymod.lmg.MachineGunItem;

/**
 * What the Gunsmith attachments do beyond changing a gun's stats, called from the shared gun code: the sling (one-handed
 * handling), the bipod (braced handling), the shotgun choke (pellet spread) and the bayonet (melee).
 */
public final class GunsmithAttachments {
	/** With a sling, holding the gun in one hand costs this much of the usual penalty. */
	private static final float SLING_STRAIN = 0.5f;
	/** Braced on the bipod (crouched on the ground): recoil and spread are cut to these fractions. */
	private static final float BIPOD_RECOIL = 0.45f;
	private static final float BIPOD_SPREAD = 0.5f;
	/** A full choke squeezes the pattern to this fraction of its width. */
	private static final float CHOKE_SPREAD = 0.6f;
	/** The bayonet's thrust: damage over a bare fist, and a slow, heavy swing. */
	private static final double BAYONET_DAMAGE = 6.0;
	private static final double BAYONET_SPEED = -2.6;

	private GunsmithAttachments() {
	}

	/** How much of the one-handed penalty this gun takes: half with a sling. */
	public static float oneHandedStrain(ItemStack gun) {
		return Attachments.has(gun, Gunsmith.RIFLE_SLING) ? SLING_STRAIN : 1.0f;
	}

	/** A bipod braced on the ground (the holder crouching on it) steadies the gun a great deal. */
	public static GunStats braced(ItemStack gun, LivingEntity holder, GunStats stats) {
		if (!Attachments.has(gun, Gunsmith.BIPOD) || !MachineGunItem.braced(holder)) {
			return stats;
		}
		return stats.withRecoil(stats.recoilDegrees() * BIPOD_RECOIL)
				.withSpread(stats.spreadDegrees() * BIPOD_SPREAD, stats.bloomPerShot() * BIPOD_SPREAD);
	}

	/** How wide a shotgun's pattern is relative to normal: tighter with a choke. */
	public static float pelletSpread(ItemStack gun) {
		return Attachments.has(gun, Gunsmith.SHOTGUN_CHOKE) ? CHOKE_SPREAD : 1.0f;
	}

	public static boolean hasBayonet(ItemStack gun) {
		return Attachments.has(gun, Gunsmith.BAYONET);
	}

	/**
	 * Brings the gun's attack attributes in line with its attachments after one is fitted or taken off: with a bayonet it
	 * hits like a short spear (on top of whatever the gun itself has, such as the M249's weight); without, it's back to the
	 * gun's own.
	 */
	public static void refit(ItemStack gun) {
		Item item = gun.getItem();
		ItemAttributeModifiers own = item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (hasBayonet(gun)) {
			gun.set(DataComponents.ATTRIBUTE_MODIFIERS, own
					.withModifierAdded(Attributes.ATTACK_DAMAGE,
							new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, BAYONET_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.withModifierAdded(Attributes.ATTACK_SPEED,
							new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, BAYONET_SPEED, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND));
		} else {
			gun.set(DataComponents.ATTRIBUTE_MODIFIERS, own);
		}
	}
}
