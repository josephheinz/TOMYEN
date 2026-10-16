package com.jheinz.tomyen.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;

/**
 * Held in place: no walking, jumping or flying while it lasts. A mob's pathfinding is dropped every tick, so it can't
 * shuffle toward its target, and anything flying (a UFO) drifts to a stop. Trapped (a bear trap) and Stunned (a pipe
 * bomb) are both this.
 */
public class SnaredEffect extends MobEffect {
	public SnaredEffect(MobEffectCategory category, int color, String name) {
		super(category, color);
		addAttributeModifier(Attributes.MOVEMENT_SPEED, MyMod.id(name + "_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(Attributes.FLYING_SPEED, MyMod.id(name + "_flying_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(Attributes.JUMP_STRENGTH, MyMod.id(name + "_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return true;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplification) {
		if (entity instanceof Mob mob) {
			mob.getNavigation().stop();
			Vec3 motion = mob.getDeltaMovement();
			// Keep falling (a UFO sinks a little), but no sideways drift.
			mob.setDeltaMovement(motion.x * 0.2, mob.isNoGravity() ? motion.y * 0.5 : motion.y, motion.z * 0.2);
		}
		return true;
	}
}
