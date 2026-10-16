package com.jheinz.tomyen.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.werewolf.Werewolf;

/** Villagers flee werewolves as they flee zombies and illagers (vanilla keeps its list of what to flee in this sensor). */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorWerewolfMixin {
	/** As far off as a werewolf may be before villagers stop running: a little further than from a vindicator. */
	@Unique
	private static final double FLEE_DISTANCE = 12.0;

	@Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
	private void tomyen$fleeWerewolves(ServerLevel level, LivingEntity body, LivingEntity mob, CallbackInfoReturnable<Boolean> cir) {
		if (mob instanceof Werewolf) {
			cir.setReturnValue(mob.distanceToSqr(body) <= FLEE_DISTANCE * FLEE_DISTANCE);
		}
	}
}
