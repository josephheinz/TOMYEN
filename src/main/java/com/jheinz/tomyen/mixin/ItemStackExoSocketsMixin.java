package com.jheinz.tomyen.mixin;

import java.util.function.BiConsumer;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.exoskeleton.ExoskeletonItem;

/**
 * A worn exoskeleton's attribute modifiers include those of its socketed armor, each for its own slot. This is
 * what vanilla applies when equipment changes and takes off again when it's removed or breaks, so the
 * socketed pieces' armor, toughness, knockback resistance and enchantment attributes come and go with the suit.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackExoSocketsMixin {
	@Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("TAIL"))
	private void tomyen$socketedModifiers(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (slot == EquipmentSlot.CHEST && self.getItem() instanceof ExoskeletonItem) {
			ExoSockets.forEachModifier(self, consumer);
		}
	}
}
