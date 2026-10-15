package com.jheinz.mymod.fishing.fish;

import java.util.List;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Every living fish of the mod's catch: vanilla's fish (swimming, fleeing players, flopping and suffocating on land,
 * caught in a bucket, dropping its catch and now and then bone meal), parameterized by its {@link FishKind}. Kinds
 * that school do it like cod, but only with their own kind; loners never join a school.
 */
public class ModFish extends AbstractSchoolingFish {
	/** How hard a bottom dweller (a flounder, a sturgeon) sinks each tick it isn't chasing anything. */
	private static final double BOTTOM_SINK = 0.012;

	public ModFish(EntityType<? extends ModFish> type, Level level) {
		super(type, level);
	}

	public FishKind kind() {
		return FishKind.of(getType());
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Vanilla's school goal gathers any fish of the same class: every kind here shares one.
		goalSelector.removeAllGoals(goal -> goal instanceof FollowFlockLeaderGoal);
		if (kind().schools()) {
			goalSelector.addGoal(5, new SchoolGoal(this));
		}
	}

	@Override
	public int getMaxSchoolSize() {
		return kind().schoolSize();
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return Math.max(kind().schoolSize(), kind().maxGroup());
	}

	@Override
	public AbstractSchoolingFish startFollowing(AbstractSchoolingFish leader) {
		// Loners spawned in a group don't fall in behind the first of it (they'd never swim about on their own).
		return kind().schools() ? super.startFollowing(leader) : leader;
	}

	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		super.travelInWater(input, baseGravity, isFalling, oldY);
		if (kind().isBottomDweller() && getTarget() == null) {
			// Negatively buoyant: it drifts back down to the bed between swims (the move control lifts it 0.005 a tick).
			setDeltaMovement(getDeltaMovement().add(0.0, -BOTTOM_SINK, 0.0));
		}
	}

	@Override
	public ItemStack getBucketItemStack() {
		return new ItemStack(kind().bucket());
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return kind().voice().ambient;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return kind().voice().hurt;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return kind().voice().death;
	}

	@Override
	protected SoundEvent getFlopSound() {
		return kind().voice().flop;
	}

	/** Vanilla's FollowFlockLeaderGoal, gathering only fish of the same kind. */
	static class SchoolGoal extends Goal {
		private final ModFish fish;
		private int timeToRecalcPath;
		private int nextStartTick;

		SchoolGoal(ModFish fish) {
			this.fish = fish;
			this.nextStartTick = nextStartTick();
		}

		private int nextStartTick() {
			return reducedTickDelay(200 + fish.getRandom().nextInt(200) % 20);
		}

		@Override
		public boolean canUse() {
			if (fish.hasFollowers()) {
				return false;
			}
			if (fish.isFollower()) {
				return true;
			}
			if (nextStartTick > 0) {
				nextStartTick--;
				return false;
			}
			nextStartTick = nextStartTick();
			List<ModFish> near = fish.level().getEntitiesOfClass(ModFish.class, fish.getBoundingBox().inflate(8.0),
					other -> other.getType() == fish.getType() && (other.canBeFollowed() || !other.isFollower()));
			AbstractSchoolingFish leader = near.stream().filter(AbstractSchoolingFish::canBeFollowed).findAny().orElse(fish);
			leader.addFollowers(near.stream().filter(other -> !other.isFollower()));
			return fish.isFollower();
		}

		@Override
		public boolean canContinueToUse() {
			return fish.isFollower() && fish.inRangeOfLeader();
		}

		@Override
		public void start() {
			timeToRecalcPath = 0;
		}

		@Override
		public void stop() {
			fish.stopFollowing();
		}

		@Override
		public void tick() {
			if (--timeToRecalcPath <= 0) {
				timeToRecalcPath = adjustedTickDelay(10);
				fish.pathToLeader();
			}
		}
	}
}
