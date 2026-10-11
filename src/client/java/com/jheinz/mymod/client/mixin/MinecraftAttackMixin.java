package com.jheinz.mymod.client.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.MagazineItem;
import com.jheinz.mymod.ModNetworking;
import com.jheinz.mymod.holy.CrucifixItem;

/**
 * Turns the attack key into reload (gun), pack (sneaking with a magazine) and light orbs (crucifix) instead of punching. Holding
 * it with a cylinder-fed gun keeps loading rounds.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
	private static final int PACK_INTERVAL_TICKS = 4;
	private static final int LOAD_INTERVAL_TICKS = 5;

	@Shadow
	public LocalPlayer player;

	private int mymod$packCooldown;

	private int mymod$loadCooldown;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (player == null) {
			return;
		}
		Item held = player.getMainHandItem().getItem();
		if (held instanceof GunItem) {
			ClientPlayNetworking.send(ModNetworking.Reload.INSTANCE);
			mymod$loadCooldown = LOAD_INTERVAL_TICKS;
			cir.setReturnValue(false);
		} else if (held instanceof MagazineItem && player.isShiftKeyDown()) {
			cir.setReturnValue(false);
		} else if (held instanceof CrucifixItem) {
			// A punch sends out an orb of light instead of hitting anything.
			ItemStack stack = player.getMainHandItem();
			if (!player.getCooldowns().isOnCooldown(stack)) {
				ClientPlayNetworking.send(ModNetworking.CrucifixPunch.INSTANCE);
				player.swing(InteractionHand.MAIN_HAND, stack.getAttackAnimation(), false);
			}
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void mymod$continueAttack(boolean attacking, CallbackInfo ci) {
		if (player == null) {
			return;
		}
		Item held = player.getMainHandItem().getItem();
		if (held instanceof MagazineItem && player.isShiftKeyDown()) {
			if (attacking && --mymod$packCooldown <= 0) {
				ClientPlayNetworking.send(ModNetworking.PackMagazine.INSTANCE);
				mymod$packCooldown = PACK_INTERVAL_TICKS;
			}
			ci.cancel();
		} else if (held instanceof CrucifixItem) {
			// Never mines blocks either.
			ci.cancel();
		} else if (held instanceof GunItem gun) {
			// Holding attack keeps loading a cylinder round by round. Guns never mine blocks.
			if (attacking && gun.stats().singleRounds() && --mymod$loadCooldown <= 0) {
				ClientPlayNetworking.send(ModNetworking.Reload.INSTANCE);
				mymod$loadCooldown = LOAD_INTERVAL_TICKS;
			}
			ci.cancel();
		}
	}
}
