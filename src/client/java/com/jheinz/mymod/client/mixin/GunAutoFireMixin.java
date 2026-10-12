package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;

/**
 * Full-auto: holding use repeats it after vanilla's 4-tick right-click delay, so a gun in auto shortens that
 * delay to its own fire interval and every round goes out evenly spaced. An empty gun keeps the normal delay,
 * so holding the trigger on it doesn't click twenty times a second.
 */
@Mixin(Minecraft.class)
public abstract class GunAutoFireMixin {
	@Shadow
	public LocalPlayer player;

	@Shadow
	private int rightClickDelay;

	@Inject(method = "startUseItem", at = @At("RETURN"))
	private void mymod$autoFireRate(CallbackInfo ci) {
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof GunItem gun) || gun.fireMode(held) != FireMode.AUTO) {
			return;
		}
		MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
		if (player.hasInfiniteMaterials() || loaded != null && !loaded.isEmpty()) {
			rightClickDelay = Math.min(rightClickDelay, gun.autoTicks(held, player));
		}
	}
}
