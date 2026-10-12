package com.jheinz.mymod.neuralink;

import java.util.Optional;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Picking what a player is looking at, for the modules that act on a target. */
public final class NeuralTargets {
	/** Small things like dropped items get this much extra around them, so they can be picked out at range. */
	private static final double PICK_MARGIN = 0.3;

	private NeuralTargets() {
	}

	/** Bosses shrug off what the Neuralink does to other mobs. */
	public static boolean isBoss(Entity entity) {
		return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof ElderGuardian || entity instanceof Warden
				|| entity.is(ConventionalEntityTypeTags.BOSSES);
	}

	/** The nearest matching entity under the crosshair within {@code range}, stopping at the first solid block. */
	public static Entity lookedAt(Player player, double range, Predicate<Entity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0f).scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB sweep = new AABB(eye, end).inflate(1.0);
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, sweep, entity -> !entity.isSpectator() && entity.isAlive() && filter.test(entity))) {
			AABB box = entity.getBoundingBox().inflate(Math.max(PICK_MARGIN, entity.getPickRadius()));
			double distance;
			if (box.contains(eye)) {
				distance = 0.0;
			} else {
				Optional<Vec3> hit = box.clip(eye, end);
				if (hit.isEmpty()) {
					continue;
				}
				distance = eye.distanceToSqr(hit.get());
			}
			if (distance < bestDistance) {
				best = entity;
				bestDistance = distance;
			}
		}
		return best;
	}
}
