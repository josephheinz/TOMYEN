package com.jheinz.mymod.skullcrane;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Grapple Claw's claw, trailing its cable back to the player's hand (see client/skullcrane/GrappleHookRenderer).
 * It flies straight and fast and gives up {@value #RANGE} blocks out.
 * <ul>
 * <li>Hitting a block, it latches on and reels its player in, quicker the further they are, for up to
 * {@value #MAX_REEL_TICKS} ticks; once they're there they hang on for {@value #HANG_TICKS} ticks. Falls only count
 * from where it lets go. Sneaking, using the claw again, or getting too far away lets go.
 * <li>Hitting a creature, it yanks it over to the player (less, the heavier it is) and comes back.
 * </ul>
 */
public class GrappleHook extends ThrowableItemProjectile {
	public static final double SPEED = 2.2;
	public static final double RANGE = 24.0;
	private static final int MAX_REEL_TICKS = 100;
	private static final int HANG_TICKS = 40;
	/** How close counts as there. */
	private static final double ARRIVED = 1.6;
	private static final double MAX_REEL_SPEED = 1.1;
	private static final double YANK_PER_BLOCK = 0.12;
	private static final double YANK_MAX = 1.6;

	private static final EntityDataAccessor<Boolean> DATA_LATCHED = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.BOOLEAN);

	private int reelTicks;
	private int hangTicks;

	public GrappleHook(EntityType<? extends GrappleHook> type, Level level) {
		super(type, level);
	}

	public GrappleHook(Level level, Player player) {
		super(Skullcranes.GRAPPLE_HOOK, player, level, new ItemStack(Skullcranes.GRAPPLE_CLAW));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LATCHED, false);
	}

	public boolean isLatched() {
		return entityData.get(DATA_LATCHED);
	}

	@Override
	protected Item getDefaultItem() {
		return Skullcranes.GRAPPLE_CLAW;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	@Override
	protected float getAirDrag() {
		return 1.0f;
	}

	/** The player it belongs to, if they're still about. */
	public @Nullable Player player() {
		return getOwner() instanceof Player player && player.isAlive() && player.level() == level() ? player : null;
	}

	@Override
	public void tick() {
		if (isLatched()) {
			// Stays put, hooked on.
			baseTick();
			if (level() instanceof ServerLevel level) {
				reel(level);
			}
			return;
		}
		super.tick();
		Player player = player();
		if (!level().isClientSide() && !isRemoved() && (player == null || distanceTo(player) > RANGE)) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return entity != getOwner() && !(entity instanceof GrappleHook) && super.canHitEntity(entity);
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel level && player() != null) {
			Vec3 at = hit.getLocation();
			setPos(at.x, at.y, at.z);
			setDeltaMovement(Vec3.ZERO);
			setNoGravity(true);
			entityData.set(DATA_LATCHED, true);
			level.playSound(null, at.x, at.y, at.z, Skullcranes.CLAMP, SoundSource.PLAYERS, 1.0f, 1.3f);
		} else {
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Player player = player();
		if (level() instanceof ServerLevel level && player != null && hit.getEntity() instanceof LivingEntity mob) {
			Vec3 toPlayer = player.position().subtract(mob.position());
			double resist = 1.0 - Math.min(1.0, mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			double speed = Math.min(toPlayer.horizontalDistance() * YANK_PER_BLOCK, YANK_MAX) * resist;
			Vec3 flat = toPlayer.horizontal();
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			mob.setDeltaMovement(flat.scale(speed).add(0.0, 0.35 * resist + Math.max(0.0, toPlayer.y) * 0.08, 0.0));
			mob.syncVelocity = true;
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), Skullcranes.CLAMP, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		discard();
	}

	/** Pulls its player toward where it's hooked on, until they're there, let go, or it's held on long enough. */
	private void reel(ServerLevel level) {
		Player player = player();
		if (player == null || player.isShiftKeyDown() || player.isPassenger() || distanceTo(player) > RANGE + 8.0) {
			discard();
			return;
		}
		Vec3 to = position().subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
		double distance = to.length();
		Vec3 motion;
		if (distance > ARRIVED) {
			if (++reelTicks > MAX_REEL_TICKS) {
				discard();
				return;
			}
			double speed = Math.min(MAX_REEL_SPEED, 0.45 + distance * 0.06);
			// Most of the way to the wanted velocity each tick, with gravity cancelled.
			motion = player.getDeltaMovement().lerp(to.scale(speed / distance), 0.5).add(0.0, 0.08, 0.0);
			if (reelTicks % 4 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), Skullcranes.AMBIENT, SoundSource.PLAYERS, 0.4f, 1.6f);
			}
		} else {
			if (++hangTicks > HANG_TICKS) {
				discard();
				return;
			}
			// Hanging there: held still against gravity.
			motion = to.scale(0.2).add(0.0, 0.08, 0.0);
		}
		player.setDeltaMovement(motion);
		player.syncVelocity = true;
		player.resetFallDistance();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel level && isLatched() && reason.shouldDestroy()) {
			level.playSound(null, getX(), getY(), getZ(), Skullcranes.RELEASE, SoundSource.PLAYERS, 0.6f, 1.5f);
		}
		super.remove(reason);
	}

	// Never saved mid-grapple: reloaded, it would hang about unowned.
	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
