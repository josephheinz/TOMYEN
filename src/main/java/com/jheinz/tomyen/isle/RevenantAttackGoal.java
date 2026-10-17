package com.jheinz.tomyen.isle;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.energy.EnergyWeaponItem;
import com.jheinz.tomyen.fishgun.FishGunItem;
import com.jheinz.tomyen.gunsmith.FlareGunItem;
import com.jheinz.tomyen.gunsmith.HarpoonGunItem;
import com.jheinz.tomyen.gunsmith.NailGunItem;
import com.jheinz.tomyen.launcher.RocketLauncherItem;
import com.jheinz.tomyen.leafblower.LeafBlowerItem;
import com.jheinz.tomyen.napalm.FlamethrowerItem;
import com.jheinz.tomyen.shotgun.ShotgunItem;

/**
 * How a Revenant fights: with whatever it died holding.
 *
 * <ul>
 * <li><b>Melee</b> (a sword, an axe, anything else, or bare fists): it closes in and swings as often as the weapon's own
 * attack speed allows. When its quarry keeps away, it now and then lunges across the gap in a burst of soul fire.
 * <li><b>Bow or crossbow</b>: it keeps {@value #NEAR}-{@value #FAR} blocks off, side-stepping, draws (or winds the
 * crossbow) and looses real arrows, enchantments and all. Anyone who gets right up close is shoved off with a
 * punch.
 * <li><b>A gun</b>: the hitscan guns (rifles, pistols, SMGs, shotguns, machine guns) fire as the Shrimp's pistol does: it
 * raises the gun and draws a bead, fires a string of rounds (longer for automatics), then stops to reload. Each round
 * does the damage of the round the gun was loaded with (or its first ammo). Guns that throw something other than
 * bullets (launchers, flamethrowers, nail and harpoon guns, energy weapons and the like) are swung as clubs instead.
 * </ul>
 *
 * It never chases past its leash ({@link Revenant#LEASH} blocks from its arena's heart): run far enough and it waits.
 */
class RevenantAttackGoal extends Goal {
	private static final double NEAR = 6.0;
	private static final double FAR = 13.0;
	private static final int LUNGE_COOLDOWN = 90;
	private static final int BOW_DRAW = 22;
	private static final int CROSSBOW_WIND = 28;
	private static final int ARROW_REST = 18;
	private static final int GUN_AIM = 12;
	private static final int GUN_RELOAD = 50;
	private static final double GUN_RANGE = 40.0;
	private static final float GUN_SPREAD = 3.0f;
	/** A Revenant's rounds hit for this share of what the player's would. */
	private static final float GUN_DAMAGE = 0.75f;

	private enum Style {
		MELEE, BOW, CROSSBOW, GUN
	}

	private final Revenant revenant;
	private int attackCooldown;
	private int lungeCooldown;
	private int stateTicks;
	private int shotsLeft;
	private boolean aiming;
	private int repathTicks;
	private int strafeTicks;
	private float strafe;

	RevenantAttackGoal(Revenant revenant) {
		this.revenant = revenant;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private @Nullable LivingEntity target() {
		LivingEntity target = revenant.getTarget();
		return target != null && target.isAlive() && !target.isSpectator() && !(target instanceof net.minecraft.world.entity.player.Player player
				&& player.isCreative()) ? target : null;
	}

	@Override
	public boolean canUse() {
		return target() != null && !revenant.isForming();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		revenant.setAggressive(true);
		repathTicks = 0;
		stateTicks = 0;
		aiming = false;
		lungeCooldown = 40;
	}

	@Override
	public void stop() {
		revenant.setAggressive(false);
		revenant.stopUsingItem();
		revenant.getNavigation().stop();
		revenant.getMoveControl().strafe(0.0f, 0.0f);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private Style style() {
		ItemStack weapon = revenant.getMainHandItem();
		if (weapon.getItem() instanceof CrossbowItem) {
			return Style.CROSSBOW;
		}
		if (weapon.getItem() instanceof BowItem) {
			return Style.BOW;
		}
		return round(weapon) != null ? Style.GUN : Style.MELEE;
	}

	/** The round a hitscan gun fires (what it's loaded with, else its first), or null if it isn't one. */
	static @Nullable Ammo round(ItemStack weapon) {
		if (!(weapon.getItem() instanceof GunItem gun) || gun.ammo().isEmpty() || gun instanceof RocketLauncherItem
				|| gun instanceof FlamethrowerItem || gun instanceof LeafBlowerItem || gun instanceof FishGunItem
				|| gun instanceof EnergyWeaponItem || gun instanceof NailGunItem || gun instanceof HarpoonGunItem || gun instanceof FlareGunItem) {
			return null;
		}
		MagazineContents loaded = weapon.get(ModComponents.LOADED_MAGAZINE);
		Ammo round = loaded == null ? null : Ammo.find(gun.ammo(), loaded.ammo());
		return round != null ? round : gun.ammo().getFirst();
	}

	@Override
	public void tick() {
		LivingEntity target = target();
		if (target == null) {
			return;
		}
		ServerLevel level = (ServerLevel) revenant.level();
		revenant.getLookControl().setLookAt(target, 30.0f, 30.0f);
		attackCooldown = Math.max(0, attackCooldown - 1);
		lungeCooldown = Math.max(0, lungeCooldown - 1);
		repathTicks = Math.max(0, repathTicks - 1);
		boolean sees = revenant.getSensing().hasLineOfSight(target);
		double distance = revenant.distanceTo(target);
		boolean leashed = !revenant.isWithinHome(target.blockPosition());

		switch (style()) {
			case BOW, CROSSBOW -> archer(level, target, sees, distance, leashed);
			case GUN -> gunner(level, target, sees, distance, leashed);
			default -> fighter(level, target, sees, distance, leashed);
		}
	}

	// --- Melee ----------------------------------------------------------------------------------------------------------

	private void fighter(ServerLevel level, LivingEntity target, boolean sees, double distance, boolean leashed) {
		if (revenant.isWithinMeleeAttackRange(target)) {
			if (attackCooldown == 0) {
				swingAt(level, target);
				attackCooldown = swingInterval();
			}
		} else if (!leashed && sees && lungeCooldown == 0 && distance > 4.0 && distance < 11.0 && revenant.onGround()) {
			lunge(level, target);
		}
		if (leashed) {
			holdGround();
		} else if (repathTicks == 0) {
			revenant.getNavigation().moveTo(target, 1.2);
			repathTicks = 6;
		}
	}

	private void swingAt(ServerLevel level, LivingEntity target) {
		revenant.swingForAttack(InteractionHand.MAIN_HAND);
		revenant.doHurtTarget(level, target);
	}

	/** Ticks between blows: the weapon's own attack speed, as a player would swing it. */
	private int swingInterval() {
		ItemStack weapon = revenant.getMainHandItem();
		double speed = weapon.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
		return Mth.clamp((int) Math.round(20.0 / Math.max(0.5, speed)), 10, 30);
	}

	/** A leap at the target across the gap, trailing soul fire. */
	private void lunge(ServerLevel level, LivingEntity target) {
		Vec3 to = target.position().subtract(revenant.position());
		Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize();
		double strength = 0.75 + 0.12 * revenant.depth().index;
		revenant.setDeltaMovement(flat.x * strength, 0.42, flat.z * strength);
		revenant.needsSync = true;
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, revenant.getX(), revenant.getY(0.5), revenant.getZ(), 14, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, revenant.getX(), revenant.getY(), revenant.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2f,
				0.6f);
		lungeCooldown = LUNGE_COOLDOWN - 15 * revenant.depth().index + revenant.getRandom().nextInt(30);
	}

	// --- Bow and crossbow -----------------------------------------------------------------------------------------------

	private void archer(ServerLevel level, LivingEntity target, boolean sees, double distance, boolean leashed) {
		boolean crossbow = style() == Style.CROSSBOW;
		if (distance < 2.5 && attackCooldown == 0 && revenant.isWithinMeleeAttackRange(target)) {
			swingAt(level, target);
			attackCooldown = 20;
		}
		if (sees && distance < FAR + 8) {
			if (!revenant.isUsingItem() && attackCooldown == 0) {
				revenant.startUsingItem(InteractionHand.MAIN_HAND);
				stateTicks = crossbow ? CROSSBOW_WIND : BOW_DRAW;
			} else if (revenant.isUsingItem() && --stateTicks <= 0) {
				revenant.stopUsingItem();
				shootArrow(level, target, crossbow);
				attackCooldown = ARROW_REST + revenant.getRandom().nextInt(12);
			}
		} else if (revenant.isUsingItem() && !sees) {
			revenant.stopUsingItem();
		}
		keepDistance(target, sees, distance, leashed, revenant.isUsingItem());
	}

	private void shootArrow(ServerLevel level, LivingEntity target, boolean crossbow) {
		ItemStack weapon = revenant.getMainHandItem();
		ItemStack projectile = revenant.getProjectile(weapon);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(revenant, projectile, crossbow ? 1.3f : 1.0f, weapon);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double dx = target.getX() - revenant.getX();
		double dy = target.getY(0.3333333333333333) - arrow.getY();
		double dz = target.getZ() - revenant.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		float speed = crossbow ? 3.0f : 1.8f;
		float inaccuracy = 10.0f - 3.0f * revenant.depth().index;
		Projectile.spawnProjectileUsingShoot(arrow, level, projectile, dx, dy + flat * (crossbow ? 0.08 : 0.2), dz, speed, inaccuracy);
		level.playSound(null, revenant.getX(), revenant.getY(), revenant.getZ(), crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
				SoundSource.HOSTILE, 1.0f, 0.8f + revenant.getRandom().nextFloat() * 0.2f);
	}

	// --- Guns -----------------------------------------------------------------------------------------------------------

	private void gunner(ServerLevel level, LivingEntity target, boolean sees, double distance, boolean leashed) {
		if (distance < 2.5 && attackCooldown == 0 && revenant.isWithinMeleeAttackRange(target)) {
			// Too close to shoot: a blow with the stock.
			swingAt(level, target);
			attackCooldown = 20;
		}
		ItemStack gun = revenant.getMainHandItem();
		GunItem item = (GunItem) gun.getItem();
		boolean auto = item.stats().fireModes().contains(FireMode.AUTO);
		if (aiming) {
			if (!sees) {
				aiming = false;
				stateTicks = 10;
			} else if (--stateTicks <= 0) {
				fire(level, target, gun);
				stateTicks = auto ? Math.max(2, item.stats().autoTicks()) : item instanceof ShotgunItem ? 16 : 7;
				if (--shotsLeft <= 0) {
					aiming = false;
					stateTicks = GUN_RELOAD + revenant.getRandom().nextInt(20);
					level.playSound(null, revenant.getX(), revenant.getY(), revenant.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(),
							SoundSource.HOSTILE, 0.8f, 0.6f);
				}
			}
		} else if (--stateTicks <= 0 && sees && distance < GUN_RANGE * 0.6) {
			aiming = true;
			stateTicks = GUN_AIM;
			shotsLeft = auto ? 6 + revenant.depth().index * 2 : item instanceof ShotgunItem ? 2 : 3 + revenant.depth().index;
		}
		keepDistance(target, sees, distance, leashed, aiming);
	}

	/** One round, hitscan from the gun's muzzle at the target's chest, straying up to {@link #GUN_SPREAD} degrees. */
	private void fire(ServerLevel level, LivingEntity target, ItemStack gun) {
		Ammo round = round(gun);
		if (round == null) {
			return;
		}
		Vec3 muzzle = revenant.getEyePosition().add(revenant.getViewVector(1.0f).scale(0.9)).add(0.0, -0.2, 0.0);
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0).subtract(muzzle);
		float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalDistance()));
		float spread = GUN_SPREAD * (1.0f - 0.25f * revenant.depth().index);
		float radius = spread * Mth.sqrt(revenant.getRandom().nextFloat());
		float angle = revenant.getRandom().nextFloat() * Mth.TWO_PI;
		Vec3 look = Vec3.directionFromRotation(pitch + radius * Mth.sin(angle), yaw + radius * Mth.cos(angle));
		Vec3 end = muzzle.add(look.scale(GUN_RANGE));

		HitResult blockHit = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, revenant));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = revenant.getBoundingBox().expandTowards(end.subtract(muzzle)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(revenant, muzzle, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable() && !(entity instanceof Revenant), muzzle.distanceToSqr(end));
		if (entityHit != null) {
			end = entityHit.getLocation();
			DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(round.damageType()),
					revenant);
			float damage = round.damage() * GUN_DAMAGE * revenant.depth().damageMultiplier;
			entityHit.getEntity().hurtServer(level, source, damage);
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 6, 0.1, 0.1, 0.1, 0.2);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
		GunItem.drawTracer(level, muzzle, end, false);
		GunItem item = (GunItem) gun.getItem();
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, item.stats().sound(), SoundSource.HOSTILE, 1.4f,
				0.85f + revenant.getRandom().nextFloat() * 0.1f);
		revenant.swingForAttack(InteractionHand.MAIN_HAND);
	}

	// --- Moving ---------------------------------------------------------------------------------------------------------

	/**
	 * For a shooter: closes in when far or out of sight, backs off when near, otherwise stands its ground and side-steps;
	 * holds still at its leash's end.
	 */
	private void keepDistance(LivingEntity target, boolean sees, double distance, boolean leashed, boolean busy) {
		if (leashed) {
			holdGround();
			return;
		}
		if (!sees || distance > FAR) {
			revenant.getMoveControl().strafe(0.0f, 0.0f);
			if (repathTicks == 0) {
				revenant.getNavigation().moveTo(target, 1.0);
				repathTicks = 10;
			}
			return;
		}
		if (distance < NEAR && repathTicks == 0) {
			Vec3 away = DefaultRandomPos.getPosAway(revenant, 8, 4, target.position());
			if (away != null && revenant.isWithinHome(net.minecraft.core.BlockPos.containing(away))) {
				revenant.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
				repathTicks = 15;
				return;
			}
		}
		if (revenant.getNavigation().isDone() || busy) {
			revenant.getNavigation().stop();
			if (revenant.isInWater()) {
				return;
			}
			if (--strafeTicks <= 0) {
				strafe = revenant.getRandom().nextInt(3) - 1;
				strafeTicks = 20 + revenant.getRandom().nextInt(30);
			}
			revenant.getMoveControl().strafe(0.0f, strafe * (busy ? 0.3f : 0.5f));
		}
	}

	/** At its leash's end: it won't follow, but turns to face and waits. */
	private void holdGround() {
		revenant.getMoveControl().strafe(0.0f, 0.0f);
		if (!revenant.isWithinHome()) {
			if (repathTicks == 0) {
				net.minecraft.core.BlockPos home = revenant.getHomePosition();
				revenant.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
				repathTicks = 20;
			}
		} else {
			revenant.getNavigation().stop();
		}
	}
}
