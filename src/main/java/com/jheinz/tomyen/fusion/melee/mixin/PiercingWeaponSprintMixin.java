package com.jheinz.tomyen.fusion.melee.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.fusion.melee.AntlerPikeItem;

/** A jab with the Antler Pike while sprinting starts a boar's charge; caught before the jab's knockback ends the sprint. */
@Mixin(PiercingWeapon.class)
public abstract class PiercingWeaponSprintMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void tomyen$sprintJab(LivingEntity attacker, EquipmentSlot hand, CallbackInfo ci) {
		if (attacker instanceof ServerPlayer player && player.isSprinting()) {
			ItemStack stack = player.getItemBySlot(hand);
			if (stack.getItem() instanceof AntlerPikeItem) {
				AntlerPikeItem.sprintJab(player, stack);
			}
		}
	}
}
