package com.jheinz.mymod.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.GunItem;

/**
 * Players hold guns up in both hands, aimed where they look, using the loaded-crossbow pose. The
 * guns' third-person display transforms are fitted to this pose.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarGunPoseMixin {
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("HEAD"), cancellable = true)
	private static void mymod$aimGuns(Avatar avatar, ItemStack stack, InteractionHand hand,
			CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (stack.getItem() instanceof GunItem) {
			cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
