package com.jheinz.tomyen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModItems;
import com.jheinz.tomyen.ModParticles;
import com.jheinz.tomyen.attachment.AttachmentItem;
import com.jheinz.tomyen.attachment.AttachmentSlot;
import com.jheinz.tomyen.attachment.Attachments;

/** Puts a dot wherever a laser sight is pointing, for every player holding a gun with one (not just us). */
public final class LaserSights {
	private static final double RANGE = 48.0;
	/** A green laser is easier to see, so its dot shows from further away. */
	private static final double GREEN_RANGE = 96.0;
	/** Pulls the dot back off the surface so it isn't buried in it. */
	private static final double LIFT = 0.02;

	private LaserSights() {
	}

	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		for (Player player : minecraft.level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			AttachmentItem laser = Attachments.get(player.getMainHandItem(), AttachmentSlot.UNDERBARREL);
			boolean green = laser == ModItems.GREEN_LASER_SIGHT;
			if (laser == ModItems.LASER_SIGHT || green) {
				Vec3 dot = target(player, green ? GREEN_RANGE : RANGE, 1.0f);
				if (dot != null) {
					minecraft.level.addParticle(green ? ModParticles.GREEN_LASER_DOT : ModParticles.LASER_DOT, dot.x, dot.y, dot.z,
							0.0, 0.0, 0.0);
				}
			}
		}
	}

	/** How far the dot shows for the laser on this gun. */
	public static double range(ItemStack gun) {
		return Attachments.has(gun, ModItems.GREEN_LASER_SIGHT) ? GREEN_RANGE : RANGE;
	}

	/** Where the player's line of sight first hits a block or entity, or null if nothing's in range. */
	public static Vec3 target(Player player, double range, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable(), eye.distanceToSqr(end));
		if (entityHit != null) {
			return entityHit.getLocation().subtract(look.scale(LIFT));
		}
		return blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation().subtract(look.scale(LIFT));
	}
}
