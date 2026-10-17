package com.jheinz.tomyen.fishing.catfish;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How the Giant Catfish fights, one move at a time ({@link GiantCatfish#state}):
 * <ul>
 * <li><b>Charge</b>, from a distance: it stops and roars, turning on its target ({@value #WINDUP_TICKS} ticks), then
 * drives straight at it through the water. Whatever it rams takes a heavy hit and is thrown; a boat in the way is
 * overturned. A charge that strikes someone in the water often turns into a drag.</li>
 * <li><b>Drag</b>: it seizes someone in or beside the water and hauls them away under (see
 * {@link GiantCatfish#startDrag}).</li>
 * <li><b>Tail slap</b>, at anything beside or behind it: the tail curls away and whips across, hitting and throwing
 * everything near it, and overturning boats.</li>
 * <li><b>Bite</b>, up close, when it can't drag.</li>
 * </ul>
 * Between moves it swims at its target. It only fights from the water.
 */
public class CatfishAttackGoal extends Goal {
	static final int WINDUP_TICKS = 16;
	private static final int DASH_TICKS = 22;
	private static final double DASH_SPEED = 0.8;
	/** The slap lands this far into it, as the model's strike does (0.35 to 0.55 of the way through). */
	private static final int SLAP_STRIKE = 8;
	private static final float CHARGE_DAMAGE = 11.0f;
	private static final float SLAP_DAMAGE = 8.0f;

	private final GiantCatfish fish;
	private int chargeCooldown;
	private int slapCooldown;
	private int biteCooldown;
	private Vec3 dash = Vec3.ZERO;
	private boolean dashHit;
	private int pathDelay;

	public CatfishAttackGoal(GiantCatfish fish) {
		this.fish = fish;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return fish.isInWater() && fish.liveTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return fish.isDragging() || canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		chargeCooldown = 30;
		pathDelay = 0;
	}

	@Override
	public void stop() {
		if (fish.isDragging()) {
			fish.releaseDrag();
		}
		int state = fish.state();
		if (state == GiantCatfish.WINDUP || state == GiantCatfish.CHARGING || state == GiantCatfish.SLAPPING || state == GiantCatfish.BITING) {
			fish.setState(GiantCatfish.IDLE);
		}
		fish.getNavigation().stop();
	}

	@Override
	public void tick() {
		chargeCooldown--;
		slapCooldown--;
		biteCooldown--;
		if (!(fish.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity target = fish.liveTarget();
		switch (fish.state()) {
			case GiantCatfish.SURFACING, GiantCatfish.STUNNED, GiantCatfish.DRAGGING -> {
				// The fish handles these itself.
			}
			case GiantCatfish.WINDUP -> windup(target);
			case GiantCatfish.CHARGING -> charge(level, target);
			case GiantCatfish.SLAPPING -> slap(level);
			case GiantCatfish.BITING -> bite(level, target);
			default -> choose(target);
		}
	}

	/** Picks its next move, or swims closer. */
	private void choose(@Nullable LivingEntity target) {
		if (target == null) {
			return;
		}
		Vec3 forward = fish.forward();
		Vec3 to = target.position().subtract(fish.position());
		Vec3 flat = to.multiply(1.0, 0.0, 1.0);
		double distance = to.length();
		double ahead = flat.dot(forward);
		double mouthDistance = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).distanceTo(fish.mouth());
		float scale = fish.sizeScale();

		// Beside its back half or behind it, within the tail's reach: the slap.
		if (slapCooldown <= 0 && distance < 4.2 * scale && ahead < -0.3 * scale) {
			Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
			fish.setSlapRight(flat.dot(right) > 0.0);
			fish.setState(GiantCatfish.SLAPPING);
			fish.getNavigation().stop();
			fish.playSound(GiantCatfishes.SWIM, 1.5f, 0.6f);
			slapCooldown = 50 + fish.getRandom().nextInt(30);
			return;
		}
		// At its mouth: seize them if it can, else bite.
		if (mouthDistance < 2.4 * scale) {
			if (fish.dragCooldown <= 0 && fish.grabbable(target) && fish.getRandom().nextFloat() < 0.5f) {
				fish.startDrag(target);
				return;
			}
			if (biteCooldown <= 0) {
				fish.setState(GiantCatfish.BITING);
				biteCooldown = 24;
				return;
			}
		}
		// Far off, in the water or on it: line up a charge.
		if (chargeCooldown <= 0 && distance > 5.0 && distance < 22.0 && fish.hasLineOfSight(target)
				&& (target.isInWater() || target.getVehicle() instanceof AbstractBoat || fish.grabbable(target))) {
			fish.setState(GiantCatfish.WINDUP);
			fish.getNavigation().stop();
			fish.playSound(GiantCatfishes.ROAR, 2.5f, 0.9f + fish.getRandom().nextFloat() * 0.15f);
			return;
		}
		if (--pathDelay <= 0) {
			pathDelay = 8;
			fish.getNavigation().moveTo(target, 1.5);
		}
		if (fish.getNavigation().isDone()) {
			// No path through the water (someone in a boat, up on the surface): straight at them, staying under.
			turnTo(target.position(), 10.0f);
			Vec3 push = fish.forward().scale(0.04);
			double up = target.getY() < fish.getY() ? -0.02 : 0.0;
			fish.setDeltaMovement(fish.getDeltaMovement().add(push.x, up, push.z));
		}
	}

	/** Turning on the target, slowing, roaring. */
	private void windup(@Nullable LivingEntity target) {
		fish.setDeltaMovement(fish.getDeltaMovement().scale(0.8));
		if (target != null) {
			turnTo(target.position(), 15.0f);
		}
		if (fish.stateTicks() >= WINDUP_TICKS) {
			Vec3 aim = target != null ? target.position().add(0.0, target.getBbHeight() * 0.3, 0.0).subtract(fish.position()) : fish.forward();
			dash = aim.lengthSqr() < 1.0E-4 ? fish.forward() : aim.normalize();
			dash = new Vec3(dash.x, Mth.clamp(dash.y, -0.4, 0.4), dash.z).normalize();
			dashHit = false;
			fish.setState(GiantCatfish.CHARGING);
			fish.playSound(GiantCatfishes.SPLASH, 2.0f, 0.7f);
		}
	}

	/** Driving straight at the target, homing a little; whatever's in the way is rammed. */
	private void charge(ServerLevel level, @Nullable LivingEntity target) {
		if (target != null && !dashHit) {
			Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.3, 0.0).subtract(fish.position());
			if (to.lengthSqr() > 1.0E-4) {
				dash = dash.scale(0.9).add(to.normalize().scale(0.1)).normalize();
			}
		}
		double speed = dashHit ? DASH_SPEED * 0.4 : DASH_SPEED;
		fish.setDeltaMovement(dash.scale(fish.isInWater() ? speed : speed * 0.3));
		turnTo(fish.position().add(dash), 30.0f);
		AABB ram = fish.getBoundingBox().inflate(0.6).move(fish.forward().scale(1.2 * fish.sizeScale()));
		for (AbstractBoat boat : level.getEntitiesOfClass(AbstractBoat.class, ram)) {
			GiantCatfish.capsize(boat, fish.position(), 0.9);
		}
		if (!dashHit) {
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, ram, entity -> entity != fish && entity.isAlive()
					&& !(entity instanceof GiantCatfish) && fish.canAttack(entity))) {
				dashHit = true;
				if (hit.hurtServer(level, fish.damageSources().mobAttack(fish), CHARGE_DAMAGE)) {
					hit.knockback(1.6, -dash.x, -dash.z, fish.damageSources().mobAttack(fish), CHARGE_DAMAGE);
					hit.needsSync = true;
				}
				Vec3 at = hit.getBoundingBox().getCenter();
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, 0.6, 0.4, 0.6, 0.3);
				fish.playSound(GiantCatfishes.BITE, 2.0f, 0.6f);
				if (hit == target && fish.dragCooldown <= 0 && fish.grabbable(hit) && fish.getRandom().nextFloat() < 0.6f) {
					fish.startDrag(hit);
					return;
				}
			}
		}
		if (fish.stateTicks() >= DASH_TICKS || fish.horizontalCollision && fish.stateTicks() > 4) {
			fish.setState(GiantCatfish.IDLE);
			chargeCooldown = 70 + fish.getRandom().nextInt(50);
		}
	}

	/** The tail slap: it lands partway through, on everything round the tail. */
	private void slap(ServerLevel level) {
		fish.setDeltaMovement(fish.getDeltaMovement().scale(0.85));
		if (fish.stateTicks() == SLAP_STRIKE) {
			Vec3 tail = fish.tailTip();
			AABB reach = new AABB(tail, tail).inflate(2.0 * fish.sizeScale(), 1.4, 2.0 * fish.sizeScale());
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != fish && entity.isAlive()
					&& !(entity instanceof GiantCatfish))) {
				if (hit.hurtServer(level, fish.damageSources().mobAttack(fish), SLAP_DAMAGE)) {
					hit.knockback(2.0, tail.x - hit.getX(), tail.z - hit.getZ(), fish.damageSources().mobAttack(fish), SLAP_DAMAGE);
					hit.setDeltaMovement(hit.getDeltaMovement().add(0.0, 0.45, 0.0));
					hit.needsSync = true;
				}
			}
			for (AbstractBoat boat : level.getEntitiesOfClass(AbstractBoat.class, reach.inflate(0.5))) {
				GiantCatfish.capsize(boat, tail, 1.2);
			}
			fish.playSound(GiantCatfishes.SLAP, 2.5f, 0.8f + fish.getRandom().nextFloat() * 0.2f);
			level.sendParticles(ParticleTypes.SPLASH, tail.x, tail.y + 0.6, tail.z, 60, 1.4, 0.3, 1.4, 0.4);
		}
		if (fish.stateTicks() >= GiantCatfish.SLAP_TICKS) {
			fish.setState(GiantCatfish.IDLE);
		}
	}

	private void bite(ServerLevel level, @Nullable LivingEntity target) {
		if (target != null) {
			turnTo(target.position(), 20.0f);
			if (fish.stateTicks() == GiantCatfish.BITE_TICKS / 2
					&& target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).distanceTo(fish.mouth()) < 2.8 * fish.sizeScale()) {
				fish.doHurtTarget(level, target);
			}
		}
		if (fish.stateTicks() >= GiantCatfish.BITE_TICKS) {
			fish.setState(GiantCatfish.IDLE);
		}
	}

	private void turnTo(Vec3 at, float step) {
		float yaw = (float) (Mth.atan2(at.z - fish.getZ(), at.x - fish.getX()) * Mth.RAD_TO_DEG) - 90.0f;
		fish.setYRot(Mth.approachDegrees(fish.getYRot(), yaw, step));
		fish.yBodyRot = fish.getYRot();
		fish.yHeadRot = fish.getYRot();
	}
}
