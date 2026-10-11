package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.jheinz.mymod.client.GunAim;

/** Walking barely bobs the view (or the gun with it) while aiming down a sight, so the sight stays on target. */
@Mixin(GameRenderer.class)
public abstract class GameRendererAimBobMixin {
	private static final float AIMED_BOB = 0.03f;

	@ModifyVariable(method = "bobView", at = @At("STORE"), ordinal = 1)
	private float mymod$steadyWhileAiming(float bob) {
		float aim = GunAim.update(Minecraft.getInstance().player);
		return bob * (1.0f - (1.0f - AIMED_BOB) * aim);
	}
}
