package com.jheinz.mymod.fishing.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * A fish that lives in lava as other fish live in water (the Magma Eel and Ember Puffer of the Nether's lava seas). Its
 * entity type is fire-immune; here it swims lava the way vanilla's fish swim water (the same drag, drift and
 * buoyancy, a move control and path finder that read lava where theirs read water, and random swims to lava), breathes
 * only in lava (anywhere else, water included, it suffocates as a fish does on land), flops on land rather than in
 * lava, and is caught in a lava bucket.
 */
public class LavaFish extends ModFish {
	public LavaFish(EntityType<? extends LavaFish> type, Level level) {
		super(type, level);
		this.moveControl = new LavaMoveControl(this);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// The fish's own random swim heads for water; this one heads for lava.
		goalSelector.removeAllGoals(goal -> goal instanceof RandomSwimmingGoal);
		goalSelector.addGoal(4, new LavaSwimGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new LavaNavigation(this, level);
	}

	@Override
	protected void travelInFluid(Vec3 input) {
		if (!isInLava()) {
			super.travelInFluid(input);
			return;
		}
		// Vanilla's fish in water: no gravity to speak of, just drag. The idle sink is taken before the move, so it
		// cancels the move control's lift and an idle fish hangs where it is (after the move, as vanilla has it, the
		// lift wins every tick and the fish slowly rises to bob at the lava's surface).
		moveRelative(0.01f, input);
		if (getTarget() == null) {
			setDeltaMovement(getDeltaMovement().add(0.0, -0.005, 0.0));
		}
		move(MoverType.SELF, getDeltaMovement());
		setDeltaMovement(getDeltaMovement().scale(0.9));
	}

	@Override
	public void aiStep() {
		// Fish flop when grounded out of water; on a lava sea's floor this one is at home.
		if (isInLava()) {
			setOnGround(false);
		}
		super.aiStep();
	}

	@Override
	protected void handleAirSupply(ServerLevel level, int preTickAirSupply) {
		if (isAlive() && !isInLava()) {
			setAirSupply(preTickAirSupply - 1);
			if (shouldTakeDrowningDamage()) {
				setAirSupply(0);
				hurtServer(level, damageSources().drown(), 2.0f);
			}
		} else {
			setAirSupply(300);
		}
	}

	@Override
	public boolean canBePickedUpWithBucket(ItemStack stack) {
		return stack.is(Items.LAVA_BUCKET);
	}

	@Override
	public SoundEvent getPickupSound() {
		return SoundEvents.BUCKET_FILL_LAVA;
	}

	private boolean canSwimAbout() {
		return canRandomSwim();
	}

	/** AbstractFish's move control, buoyed by lava instead of water. */
	static class LavaMoveControl extends MoveControl<LavaFish> {
		LavaMoveControl(LavaFish fish) {
			super(fish);
		}

		@Override
		public void tick() {
			if (mob.isEyeInFluid(FluidTags.LAVA)) {
				mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.005, 0.0));
			}
			if (operation == MoveControl.Operation.MOVE_TO && !mob.getNavigation().isDone()) {
				float targetSpeed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
				mob.setSpeed(Mth.lerp(0.125f, mob.getSpeed(), targetSpeed));
				double xd = wantedX - mob.getX();
				double yd = wantedY - mob.getY();
				double zd = wantedZ - mob.getZ();
				if (yd != 0.0) {
					double dd = Math.sqrt(xd * xd + yd * yd + zd * zd);
					mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, mob.getSpeed() * (yd / dd) * 0.1, 0.0));
				}
				if (xd != 0.0 || zd != 0.0) {
					float yRot = (float) (Mth.atan2(zd, xd) * 180.0f / (float) Math.PI) - 90.0f;
					mob.setYRot(rotlerp(mob.getYRot(), yRot, 90.0f));
					mob.yBodyRot = mob.getYRot();
				}
			} else {
				mob.setSpeed(0.0f);
			}
		}
	}

	/** Water-bound navigation whose path finder swims lava. */
	static class LavaNavigation extends WaterBoundPathNavigation {
		LavaNavigation(Mob mob, Level level) {
			super(mob, level);
		}

		@Override
		protected PathFinder createPathFinder(int maxVisitedNodes) {
			nodeEvaluator = new LavaNodeEvaluator();
			nodeEvaluator.setCanPassDoors(false);
			return new PathFinder(nodeEvaluator, maxVisitedNodes);
		}
	}

	/** SwimNodeEvaluator with lava for water: lava is open water to it, anything else a wall. */
	static class LavaNodeEvaluator extends SwimNodeEvaluator {
		LavaNodeEvaluator() {
			super(false);
		}

		@Override
		public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int xx = x; xx < x + entityWidth; xx++) {
				for (int yy = y; yy < y + entityHeight; yy++) {
					for (int zz = z; zz < z + entityDepth; zz++) {
						FluidState fluid = context.getBlockState(pos.set(xx, yy, zz)).getFluidState();
						if (!fluid.is(FluidTags.LAVA)) {
							return PathType.BLOCKED;
						}
					}
				}
			}
			BlockState state = context.getBlockState(pos);
			return state.getFluidState().is(FluidTags.LAVA) || state.isPathfindable(PathComputationType.WATER) ? PathType.WATER
					: PathType.BLOCKED;
		}
	}

	/** The fish's idle swim, to a random spot in the lava nearby. */
	static class LavaSwimGoal extends RandomSwimmingGoal {
		private final LavaFish fish;

		LavaSwimGoal(LavaFish fish) {
			super(fish, 1.0, 40);
			this.fish = fish;
		}

		@Override
		public boolean canUse() {
			return fish.canSwimAbout() && super.canUse();
		}

		@Override
		protected @Nullable Vec3 getPosition() {
			for (int i = 0; i < 10; i++) {
				Vec3 pos = DefaultRandomPos.getPos(fish, 10, 7);
				if (pos != null && fish.level().getFluidState(BlockPos.containing(pos)).is(FluidTags.LAVA)) {
					return pos;
				}
			}
			return null;
		}
	}
}
