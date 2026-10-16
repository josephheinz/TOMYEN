package com.jheinz.tomyen.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.soul.SoulVacuumItem;

/** Players hold the Soul Vacuum up in both hands, nozzle where they look, like a gun (its display transforms match). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarSoulVacuumPoseMixin {
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("HEAD"), cancellable = true)
	private static void tomyen$aimSoulVacuum(Avatar avatar, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (stack.getItem() instanceof SoulVacuumItem) {
			cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
