package com.jheinz.tomyen.client.mixin;

import org.joml.Matrix4fc;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.jheinz.tomyen.client.schnozzle.WhipHandles;

/**
 * The view bob (and hurt tilt) the level is drawn with this frame, which the first-person hand is drawn with too: the
 * whip's lash needs it to carry the handle's tip from the hand pass into the level (client/schnozzle/WhipHandles).
 */
@Mixin(GameRenderer.class)
public abstract class WhipViewBobMixin {
	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"))
	private Matrix4fc tomyen$rememberBob(Matrix4fc bob) {
		WhipHandles.setBob(bob);
		return bob;
	}
}
