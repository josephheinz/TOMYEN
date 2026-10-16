package com.jheinz.tomyen.scarecrow.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hostile mobs get the scarecrow lure added to their target goals (see Scarecrows#addLureGoal). */
@Mixin(Mob.class)
public interface MobTargetSelectorAccessor {
	@Accessor("targetSelector")
	GoalSelector tomyen$getTargetSelector();
}
