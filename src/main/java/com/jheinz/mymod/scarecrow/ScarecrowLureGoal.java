package com.jheinz.mymod.scarecrow;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A hostile mob's distraction (added to every goal-targeting hostile's target selector, ahead of everything else, by
 * {@link Scarecrows}): every second or two it looks for a scarecrow it can see within {@value #RANGE} blocks
 * ({@value #HATER_RANGE} for the undead and werewolves, which loathe them) and, more often than not, goes for it for a
 * few seconds, even mid-chase. Then it loses interest, goes back to whatever it was doing, and won't fall for one again
 * for {@value #COOLDOWN} ticks. Getting hurt by something snaps it out of it at once.
 * <p>
 * Rogue machines (the {@code mymod:machines} tag) only fall for the Scrap Scarecrow; anything in the
 * {@code mymod:ignores_scarecrows} tag never gets this goal at all.
 */
public class ScarecrowLureGoal extends Goal {
	static final double RANGE = 12.0;
	static final double HATER_RANGE = 16.0;
	/** Lured ticks: so long for most, longer for the haters. */
	private static final int LURE_TICKS = 80;
	private static final int HATER_LURE_TICKS = 140;
	private static final int COOLDOWN = 300;
	/** Most mobs take the bait one look in two; haters always. */
	private static final float LURE_CHANCE = 0.5f;

	private final Mob mob;
	private final boolean hater;
	private final boolean machine;
	private @Nullable Scarecrow lure;
	private int scanCooldown;
	private long until;
	private long nextLure;
	private int hurtStamp;

	public ScarecrowLureGoal(Mob mob, boolean hater, boolean machine) {
		this.mob = mob;
		this.hater = hater;
		this.machine = machine;
		setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (--scanCooldown > 0) {
			return false;
		}
		scanCooldown = reducedTickDelay(20 + mob.getRandom().nextInt(20));
		if (mob.level().getGameTime() < nextLure || !mob.isAlive() || mob.isPassenger()) {
			return false;
		}
		if (!hater && mob.getRandom().nextFloat() >= LURE_CHANCE) {
			return false;
		}
		lure = findLure();
		return lure != null;
	}

	private @Nullable Scarecrow findLure() {
		double range = hater ? HATER_RANGE : RANGE;
		Scarecrow nearest = null;
		for (Scarecrow scarecrow : mob.level().getEntitiesOfClass(Scarecrow.class, mob.getBoundingBox().inflate(range, 6.0, range),
				scarecrow -> scarecrow.isDecoyActive() && (!machine || scarecrow.variant() == Scarecrow.Variant.SCRAP))) {
			if (mob.distanceToSqr(scarecrow) <= range * range && mob.canAttack(scarecrow) && mob.getSensing().hasLineOfSight(scarecrow)
					&& (nearest == null || mob.distanceToSqr(scarecrow) < mob.distanceToSqr(nearest))) {
				nearest = scarecrow;
			}
		}
		return nearest;
	}

	@Override
	public void start() {
		mob.setTarget(lure);
		until = mob.level().getGameTime() + (hater ? HATER_LURE_TICKS : LURE_TICKS);
		hurtStamp = mob.getLastHurtByMobTimestamp();
	}

	@Override
	public boolean canContinueToUse() {
		double range = (hater ? HATER_RANGE : RANGE) + 8.0;
		return lure != null && lure.isDecoyActive() && mob.getTarget() == lure && mob.level().getGameTime() < until
				&& mob.getLastHurtByMobTimestamp() == hurtStamp && mob.distanceToSqr(lure) < range * range;
	}

	@Override
	public void stop() {
		if (lure != null && mob.getTarget() == lure) {
			mob.setTarget(null);
		}
		lure = null;
		nextLure = mob.level().getGameTime() + COOLDOWN;
	}
}
