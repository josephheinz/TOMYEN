package com.jheinz.tomyen.hag;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * How a Bog Hag fights. She keeps {@value #KEEP_AWAY} blocks or more from her target, backing off when it closes and
 * shuffling sideways when it's where she wants it; out of sight or past {@value #CAST_RANGE} blocks she hobbles
 * nearer. Every {@value #HEX_COOLDOWN}-odd ticks she raises her staff and thrusts it forward, lobbing a hex; anyone in
 * reach gets the staff across them (knocked back); and when pressed, someone close or her below half health, she
 * slams the staff down and calls slimes up, every {@value #SUMMON_COOLDOWN} ticks at most. Casting, she stands still.
 */
class BogHagSpellGoal extends Goal {
	static final double KEEP_AWAY = 7.0;
	static final double CAST_RANGE = 18.0;
	private static final int HEX_COOLDOWN = 50;
	private static final int HEX_COOLDOWN_SPREAD = 30;
	private static final int SUMMON_COOLDOWN = 400;
	private static final int RAP_COOLDOWN = 30;
	private static final int REPATH_TICKS = 10;

	private final BogHag hag;
	private BogHag.Spell casting = BogHag.Spell.NONE;
	private int spellTicks;
	private int hexCooldown;
	private int summonCooldown;
	private int rapCooldown;
	private int repath;
	private int strafeTicks;
	private float strafe = 0.35f;

	BogHagSpellGoal(BogHag hag) {
		this.hag = hag;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = hag.getTarget();
		return target != null && target.isAlive() && hag.canAttack(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = hag.getTarget();
		if (target == null || !target.isAlive() || !hag.canAttack(target)) {
			return false;
		}
		return !(target instanceof Player player) || !player.isCreative() && !player.isSpectator();
	}

	@Override
	public void start() {
		hexCooldown = 15;
		hag.setAggressive(true);
	}

	@Override
	public void stop() {
		casting = BogHag.Spell.NONE;
		hag.setSpell(BogHag.Spell.NONE);
		hag.setAggressive(false);
		hag.getNavigation().stop();
		hag.getMoveControl().strafe(0.0f, 0.0f);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = hag.getTarget();
		if (target == null || !(hag.level() instanceof ServerLevel level)) {
			return;
		}
		hag.getLookControl().setLookAt(target, 30.0f, 30.0f);
		hexCooldown--;
		summonCooldown--;
		rapCooldown--;
		repath--;

		if (casting != BogHag.Spell.NONE) {
			hag.getNavigation().stop();
			hag.getMoveControl().strafe(0.0f, 0.0f);
			spellTicks++;
			if (casting == BogHag.Spell.HEX && spellTicks == BogHag.HEX_RELEASE) {
				hag.throwHex(level, target);
			} else if (casting == BogHag.Spell.SUMMON && spellTicks == BogHag.SUMMON_RELEASE) {
				hag.summon(level, target);
			}
			if (spellTicks >= casting.ticks()) {
				casting = BogHag.Spell.NONE;
				hag.setSpell(BogHag.Spell.NONE);
			}
			return;
		}

		double distance = hag.distanceTo(target);
		boolean sees = hag.getSensing().hasLineOfSight(target);
		if (rapCooldown <= 0 && hag.isWithinMeleeAttackRange(target) && sees) {
			hag.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			hag.doHurtTarget(level, target);
			rapCooldown = RAP_COOLDOWN;
		}
		boolean pressed = distance < KEEP_AWAY || hag.getHealth() < hag.getMaxHealth() * 0.5f;
		if (summonCooldown <= 0 && pressed && sees && hag.slimesAbout() < BogHag.MAX_SLIMES) {
			begin(BogHag.Spell.SUMMON);
			summonCooldown = SUMMON_COOLDOWN;
			return;
		}
		if (hexCooldown <= 0 && sees && distance < CAST_RANGE) {
			begin(BogHag.Spell.HEX);
			hexCooldown = HEX_COOLDOWN + hag.getRandom().nextInt(HEX_COOLDOWN_SPREAD);
			return;
		}

		if (distance < KEEP_AWAY) {
			// Back off, away from it.
			hag.getMoveControl().strafe(0.0f, 0.0f);
			if (repath <= 0 || hag.getNavigation().isDone()) {
				repath = REPATH_TICKS;
				Vec3 away = DefaultRandomPos.getPosAway(hag, 10, 4, target.position());
				if (away != null) {
					hag.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
				}
			}
		} else if (distance > CAST_RANGE * 0.8 || !sees) {
			hag.getMoveControl().strafe(0.0f, 0.0f);
			if (repath <= 0) {
				repath = REPATH_TICKS;
				hag.getNavigation().moveTo(target, 1.0);
			}
		} else {
			// Where she wants it: shuffle sideways, now one way, now the other.
			hag.getNavigation().stop();
			if (--strafeTicks <= 0) {
				strafeTicks = 30 + hag.getRandom().nextInt(40);
				if (hag.getRandom().nextBoolean()) {
					strafe = -strafe;
				}
			}
			hag.getMoveControl().strafe(0.0f, strafe);
		}
	}

	private void begin(BogHag.Spell spell) {
		casting = spell;
		spellTicks = 0;
		hag.setSpell(spell);
		hag.getNavigation().stop();
	}
}
