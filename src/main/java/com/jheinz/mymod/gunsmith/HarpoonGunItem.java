package com.jheinz.mymod.gunsmith;

import java.util.List;
import java.util.Map;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunSpread;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.silver.EvilBane;

/**
 * A pneumatic spear gun on a line. The spear flies straight through water, and from underwater it reaches further and hits
 * harder. Whatever it strikes is hauled back toward the shooter on the line, and the spear comes back with it; a fish it kills
 * is reeled in whole, its catch dropped straight into the shooter's pack. A spear that hits a wall stays stuck there to be
 * picked up again (now and then it bends and is lost), and one that hits nothing is gone.
 */
public class HarpoonGunItem extends GunsmithGunItem {
	/** Underwater the spear reaches this much further and hits this much harder. */
	private static final float WATER_RANGE = 1.35f;
	private static final float WATER_DAMAGE = 1.5f;
	/** How hard the line hauls a target in (blocks per tick, for a man-sized one), and more so through water. */
	private static final double PULL = 1.1;
	private static final double WATER_PULL = 1.5;
	/** Things bigger than this (in width times height) are too heavy to haul in. */
	private static final double HEAVIEST = 4.5;
	/** The chance a spear that hits stone or wood bends and is lost. */
	private static final float BEND_CHANCE = 0.15f;
	private static final int LINE_COLOR = 0xD8D2BC;

	public HarpoonGunItem(Properties properties, GunStats stats, List<Shell> shells, int capacity, Map<FireMode, Handling> handling,
			Traits traits) {
		super(properties, stats, shells, capacity, handling, traits);
	}

	@Override
	protected void discharge(ServerLevel level, Player player, ItemStack gun, Shell shell, int rounds) {
		boolean underwater = player.isEyeInFluid(FluidTags.WATER);
		float aim = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats(gun, player)) : 0.0f;
		float[] dir = scatter(player, player.getXRot(), player.getYRot(), aim);
		Vec3 look = Vec3.directionFromRotation(dir[0], dir[1]);
		Vec3 eye = player.getEyePosition();
		double range = shell.range() * (underwater ? WATER_RANGE : 1.0f);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable(), eye.distanceToSqr(end));
		Vec3 muzzle = muzzle(player);
		ItemStack spear = new ItemStack(shell.item());

		if (entityHit != null) {
			Entity target = entityHit.getEntity();
			Vec3 hit = entityHit.getLocation();
			line(level, muzzle, hit, underwater);
			float damage = damageAt(shell, eye.distanceTo(hit)) * (underwater ? WATER_DAMAGE : 1.0f);
			DamageSource source = new DamageSource(
					level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET), player);
			AABB body = target.getBoundingBox();
			target.hurtServer(level, source, damage * EvilBane.multiplier(shell.item(), target));
			level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 8, 0.1, 0.1, 0.1, 0.2);
			if (target instanceof AbstractFish && !target.isAlive()) {
				reelInCatch(level, player, body);
			} else if (target.isAlive()) {
				haul(player, target, underwater);
			}
			// The spear comes back on the line.
			player.getInventory().placeItemBackInInventory(spear, Prediction.SERVER_ONLY);
		} else if (blockHit.getType() == HitResult.Type.BLOCK) {
			Vec3 hit = blockHit.getLocation();
			line(level, muzzle, hit, underwater);
			level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 1.2f);
			if (level.getRandom().nextFloat() >= BEND_CHANCE) {
				Vec3 back = hit.subtract(look.scale(0.3));
				ItemEntity stuck = new ItemEntity(level, back.x, back.y, back.z, spear, 0.0, 0.0, 0.0);
				stuck.setDefaultPickUpDelay();
				level.addFreshEntity(stuck);
			}
		} else {
			line(level, muzzle, end, underwater);
		}
	}

	/** Hauls a target toward the shooter on the line: hard for something small, not at all for something huge. */
	private static void haul(Player player, Entity target, boolean underwater) {
		double size = target.getBbWidth() * target.getBbHeight();
		if (size > HEAVIEST) {
			return;
		}
		Vec3 toward = player.position().add(0, 0.5, 0).subtract(target.position());
		double strength = (underwater || target.isInWater() ? WATER_PULL : PULL) * Math.min(1.0, 1.2 / Math.max(size, 0.3));
		Vec3 pull = toward.normalize().scale(strength);
		target.push(pull.x, Math.max(pull.y, 0.0) + 0.15, pull.z);
	}

	/** A fish the spear killed is reeled in: whatever it dropped goes straight into the shooter's pack. */
	private static void reelInCatch(ServerLevel level, Player player, AABB body) {
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, body.inflate(1.5), drop -> drop.getAge() <= 1)) {
			player.getInventory().placeItemBackInInventory(drop.getItem().copy(), Prediction.SERVER_ONLY);
			drop.discard();
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS,
				1.0f, 1.0f);
	}

	/** The line paid out to where the spear went: a pale cord, and bubbles along it underwater. */
	private static void line(ServerLevel level, Vec3 from, Vec3 to, boolean underwater) {
		DustParticleOptions cord = new DustParticleOptions(LINE_COLOR, 0.45f);
		Vec3 path = to.subtract(from);
		int steps = (int) (path.length() * 2.0);
		for (int i = 1; i < steps; i++) {
			Vec3 p = from.add(path.scale(i / (double) steps));
			level.sendParticles(cord, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			if (underwater && i % 3 == 0) {
				level.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}
}
