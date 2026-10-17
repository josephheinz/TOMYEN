package com.jheinz.tomyen.fishing.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.jheinz.tomyen.fishing.josh.Josh;

/**
 * Josh ({@link Josh}): a villager who takes the job is named Josh, and one who loses it before he ever traded is
 * Josh no more; and now and then, when Josh mumbles to himself, it's a fish flopping instead.
 */
@Mixin(Villager.class)
public abstract class VillagerJoshMixin {
	/** The share of Josh's idle sounds that are a fish flopping. */
	private static final float TOMYEN$FLOP_CHANCE = 0.3f;

	@Inject(method = "setVillagerData", at = @At("HEAD"))
	private void tomyen$nameJosh(VillagerData data, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		boolean was = self.getVillagerData().profession().is(Josh.PROFESSION_KEY);
		boolean is = data.profession().is(Josh.PROFESSION_KEY);
		if (is) {
			Josh.name(self);
		} else if (was) {
			Josh.unname(self);
		}
	}

	@Inject(method = "getAmbientSound", at = @At("RETURN"), cancellable = true)
	private void tomyen$fishyMumble(CallbackInfoReturnable<SoundEvent> cir) {
		Villager self = (Villager) (Object) this;
		if (cir.getReturnValue() == SoundEvents.VILLAGER_AMBIENT && Josh.isJosh(self) && self.getRandom().nextFloat() < TOMYEN$FLOP_CHANCE) {
			cir.setReturnValue(SoundEvents.COD_FLOP);
		}
	}
}
