package com.jheinz.mymod.skullcrane;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A skullcrane pack leader: the same skull {@value #SCALE} times the size, with a bronze crest and horn plates, amber
 * lenses and longer arms. It's far tougher (70 health and 8 armor) with stronger claws, and on its own it
 * gives a grip the lift of {@value #LIFT_WEIGHT} skulls, so it can carry a player off single-handed.
 *
 * <p>One in three natural packs (and every full pack) comes with one (see {@link Skullcrane#finalizeSpawn}). The rest
 * of the pack follows it about and takes up its prey (see {@link SkullcranePackGoal} and {@link Skullcrane#aiStep});
 * when it dies, they let go of whatever they're holding and scatter for {@value #PANIC_TICKS} ticks.
 */
public class SkullcraneLeader extends Skullcrane {
	public static final float SCALE = 2.5f;
	static final int LIFT_WEIGHT = 3;
	private static final double HEALTH = 70.0;
	private static final int PANIC_TICKS = 80;
	/** Clamped on, how much higher over the victim's head it's held, and how much closer in. */
	private static final double HOLD_RAISE = 0.9;
	private static final double HOLD_IN = 0.3;

	public SkullcraneLeader(EntityType<? extends SkullcraneLeader> type, Level level) {
		super(type, level);
		xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, HEALTH)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.14)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected boolean hatchesPack() {
		return false;
	}

	@Override
	public int liftWeight() {
		return LIFT_WEIGHT;
	}

	@Override
	protected float letGoDamage() {
		return 16.0f;
	}

	/** Held up over the victim's head rather than out on its shoulder, its long arms reaching down. */
	@Override
	public Vec3 holdOffset(float bodyYaw, float width, float height, int slot, int count) {
		Vec3 offset = super.holdOffset(bodyYaw, width, height, slot, count);
		return new Vec3(offset.x * HOLD_IN, offset.y + HOLD_RAISE, offset.z * HOLD_IN);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel) {
			for (Skullcrane mate : packmates()) {
				if (!(mate instanceof SkullcraneLeader)) {
					mate.panic(position(), PANIC_TICKS + random.nextInt(30));
				}
			}
		}
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6f;
	}
}
