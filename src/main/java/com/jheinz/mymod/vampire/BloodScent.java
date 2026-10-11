package com.jheinz.mymod.vampire;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.ModEntities;
import com.jheinz.mymod.MyMod;

/** How bleeding draws predators: they smell it from {@link #RANGE} blocks, and at night it can bring out new vampires. */
public final class BloodScent {
	/** How far a bleeding target can be smelled from, through walls. */
	public static final double RANGE = 48.0;
	private static final AttributeModifier SCENT_FOLLOW_RANGE = new AttributeModifier(MyMod.id("blood_scent"), RANGE,
			AttributeModifier.Operation.ADD_VALUE);
	/** Chance per bleed tick (every two seconds) that a new vampire comes for a bleeding player in the dark. */
	private static final float CALL_CHANCE = 0.12f;
	private static final int MAX_NEARBY_VAMPIRES = 4;

	private BloodScent() {
	}

	public static boolean isBleeding(LivingEntity entity) {
		return entity.hasEffect(ModEffects.BLEEDING);
	}

	/** Sometimes spawns a vampire in a dark spot 16-32 blocks from the player, already hunting them. */
	public static void maybeCallVampire(ServerLevel level, Player player) {
		if (player.isCreative() || player.isSpectator() || level.getRandom().nextFloat() >= CALL_CHANCE) {
			return;
		}
		if (level.getEntitiesOfClass(Vampire.class, player.getBoundingBox().inflate(RANGE)).size() >= MAX_NEARBY_VAMPIRES) {
			return;
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
			double distance = 16.0 + level.getRandom().nextDouble() * 16.0;
			BlockPos pos = findStandingSpot(level, BlockPos.containing(player.getX() + Mth.cos(angle) * distance, player.getY(),
					player.getZ() + Mth.sin(angle) * distance));
			if (pos != null && level.getMaxLocalRawBrightness(pos) <= Vampire.MAX_SPAWN_LIGHT) {
				Vampire vampire = ModEntities.VAMPIRE.spawn(level, pos, EntitySpawnReason.EVENT);
				if (vampire != null) {
					vampire.setTarget(player);
				}
				return;
			}
		}
	}

	/** Searches 8 blocks up and down for open space with ground under it. */
	private static @Nullable BlockPos findStandingSpot(ServerLevel level, BlockPos around) {
		for (int dy = 8; dy >= -8; dy--) {
			BlockPos pos = around.above(dy);
			if (SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, ModEntities.VAMPIRE)
					&& level.noCollision(ModEntities.VAMPIRE.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
				return pos;
			}
		}
		return null;
	}

	/**
	 * Targets the nearest bleeding entity of a type within {@link #RANGE}, seen or not, and stretches the
	 * mob's follow range while it hunts so it can path that far.
	 */
	public static class Goal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
		public Goal(Mob mob, Class<T> type) {
			super(mob, type, 10, false, false, (target, level) -> isBleeding(target));
			setFlags(EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET));
		}

		@Override
		protected double getFollowDistance() {
			return RANGE;
		}

		@Override
		protected AABB getTargetSearchArea(double followDistance) {
			return mob.getBoundingBox().inflate(followDistance, 16.0, followDistance);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = mob.getTarget();
			return super.canContinueToUse() && target != null && (isBleeding(target) || mob.distanceToSqr(target) < 24.0 * 24.0);
		}

		@Override
		public void start() {
			super.start();
			AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
			if (followRange != null) {
				followRange.addOrUpdateTransientModifier(SCENT_FOLLOW_RANGE);
			}
		}

		@Override
		public void stop() {
			super.stop();
			AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
			if (followRange != null) {
				followRange.removeModifier(SCENT_FOLLOW_RANGE.id());
			}
		}
	}
}
