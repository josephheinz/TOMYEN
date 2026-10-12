package com.jheinz.mymod.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jheinz.mymod.client.tank.TankClient;
import com.jheinz.mymod.tank.Tank;
import com.jheinz.mymod.tank.TankCrew;

/**
 * Looking through a tank's gunner's sight, the camera is on the sight, at the front of the turret, and looks along
 * the sight line: where the gun points, less the fire control's superelevation (so it's on the target the gun is
 * laid for). The player's own view is where they want the gun to go; the turret slews after it, taking the
 * camera with it.
 */
@Mixin(Camera.class)
public abstract class TankSightCameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void mymod$gunnersSight(float partialTicks, CallbackInfo ci) {
		Tank tank = TankClient.sightTank();
		if (tank != null) {
			setRotation(tank.turretYaw(partialTicks), tank.gunPitch(partialTicks) + tank.superelevationNow());
			setPosition(TankCrew.sightPoint(tank, partialTicks));
		}
	}
}
