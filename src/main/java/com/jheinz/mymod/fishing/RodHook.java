package com.jheinz.mymod.fishing;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.fishing.mixin.FishingHookAccessor;

/**
 * A mod rod's bobber. It is vanilla's bobber (same cast, same bobbing, same bite timing; FishingHookMixin lets it stay
 * out for a mod rod) with the species system on top: when a fish starts swimming in, it rolls which species from the
 * species files (a Fish Finder names it to the player then); when that fish bites, a fight starts
 * ({@link FishingFights}) and the bobber's timers hold until it ends. If nothing in the files can bite here, the fish
 * is vanilla's: click in time and the fishing loot table decides, with no minigame. A Magmasteel Rod's bobber floats
 * on lava and fishes lava species; any other mod bobber that lands in lava burns up.
 */
public class RodHook extends FishingHook {
	private static final EntityDataAccessor<Boolean> DATA_LAVA = SynchedEntityData.defineId(RodHook.class, EntityDataSerializers.BOOLEAN);
	/** A fight is on: the bobber tugs, the rod bends and the use key reels. */
	private static final EntityDataAccessor<Boolean> DATA_FIGHTING = SynchedEntityData.defineId(RodHook.class, EntityDataSerializers.BOOLEAN);

	private RodStats stats = RodStats.BASELINE;
	/** The fish swimming in (or on the line), rolled when it started its approach. */
	private FishingSpecies pending;
	private int pendingCount = 1;
	private boolean toldEmptyLava;

	public RodHook(EntityType<? extends RodHook> type, Level level) {
		super(type, level);
	}

	public RodHook(Player player, Level level, int luck, int lureSpeed, RodStats stats) {
		super(Fishing.ROD_HOOK, level, luck, lureSpeed);
		this.stats = stats;
		getEntityData().set(DATA_LAVA, stats.lava());
		setOwner(player);
		// Thrown exactly as vanilla's bobber is.
		float xRot = player.getXRot();
		float yRot = player.getYRot();
		float yCos = Mth.cos(-yRot * Mth.DEG_TO_RAD - Mth.PI);
		float ySin = Mth.sin(-yRot * Mth.DEG_TO_RAD - Mth.PI);
		float xCos = -Mth.cos(-xRot * Mth.DEG_TO_RAD);
		float xSin = Mth.sin(-xRot * Mth.DEG_TO_RAD);
		snapTo(player.getX() - ySin * 0.3, player.getEyeY(), player.getZ() - yCos * 0.3, yRot, xRot);
		Vec3 movement = new Vec3(-ySin, Mth.clamp(-(xSin / xCos), -5.0f, 5.0f), -yCos);
		double length = movement.length();
		movement = movement.multiply(0.6 / length + random.triangle(0.5, 0.0103365), 0.6 / length + random.triangle(0.5, 0.0103365),
				0.6 / length + random.triangle(0.5, 0.0103365));
		setDeltaMovement(movement);
		setYRot((float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(movement.y, movement.horizontalDistance()) * Mth.RAD_TO_DEG));
		yRotO = getYRot();
		xRotO = getXRot();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LAVA, false);
		builder.define(DATA_FIGHTING, false);
	}

	public RodStats stats() {
		return stats;
	}

	public boolean fishesLava() {
		return getEntityData().get(DATA_LAVA);
	}

	public boolean isFighting() {
		return getEntityData().get(DATA_FIGHTING);
	}

	void setFighting(boolean fighting) {
		getEntityData().set(DATA_FIGHTING, fighting);
	}

	public int luck() {
		return ((FishingHookAccessor) this).mymod$getLuck();
	}

	public RandomSource rng() {
		return random;
	}

	/** Whether the bobber is floating in lava rather than water. */
	public boolean inLava() {
		return level().getFluidState(blockPosition()).is(FluidTags.LAVA);
	}

	/** Whether the bobber is still floating in something it can fish: the fight's catch needs the fish still on. */
	public boolean inFishableFluid() {
		var fluid = level().getFluidState(blockPosition());
		var below = level().getFluidState(blockPosition().below());
		return fluid.is(FluidTags.WATER) || below.is(FluidTags.WATER)
				|| fishesLava() && (fluid.is(FluidTags.LAVA) || below.is(FluidTags.LAVA));
	}

	FishingSpecies pending() {
		return pending;
	}

	int pendingCount() {
		return pendingCount;
	}

	/** After a fish gets away: back to waiting for the next one, as if just cast. */
	void resetWait() {
		FishingHookAccessor timers = (FishingHookAccessor) this;
		timers.mymod$setNibble(0);
		timers.mymod$setTimeUntilHooked(0);
		timers.mymod$setTimeUntilLured(0);
		getEntityData().set(FishingHookAccessor.mymod$dataBiting(), false);
		setFighting(false);
		pending = null;
	}

	@Override
	public void tick() {
		FishingHookAccessor timers = (FishingHookAccessor) this;
		int nibbleBefore = timers.mymod$getNibble();
		int hookedBefore = timers.mymod$getTimeUntilHooked();
		if (level() instanceof ServerLevel serverLevel && !fishesLava() && isInLava()) {
			serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.4f, 1.6f);
			serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.2, getZ(), 6, 0.1, 0.1, 0.1, 0.01);
			discard();
			return;
		}
		super.tick();
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel serverLevel) {
			if (!isFighting()) {
				int nibble = timers.mymod$getNibble();
				int hooked = timers.mymod$getTimeUntilHooked();
				if (hookedBefore <= 0 && hooked > 0 && nibble <= 0) {
					approach(serverLevel);
				}
				if (nibbleBefore <= 0 && nibble > 0) {
					bite(serverLevel);
				}
				// Vanilla's swimming-in trail only shows on water; in lava the fish stirs the surface instead.
				if (hooked > 0 && inLava() && random.nextFloat() < 0.25f) {
					serverLevel.sendParticles(ParticleTypes.LAVA, getX() + (random.nextFloat() - 0.5f) * hooked * 0.05,
							getY() + 0.2, getZ() + (random.nextFloat() - 0.5f) * hooked * 0.05, 1, 0, 0, 0, 0);
				}
			}
		} else if (isFighting() && random.nextFloat() < 0.18f) {
			// Everyone sees the bobber jerk about while the fish fights (the angler's own client tugs harder: FishingClient).
			setDeltaMovement(getDeltaMovement().add((random.nextFloat() - 0.5f) * 0.05, -0.05 * random.nextFloat(),
					(random.nextFloat() - 0.5f) * 0.05));
		}
	}

	/** A fish has started swimming in: decide what it is, and ping the sonar. */
	private void approach(ServerLevel level) {
		if (!(getPlayerOwner() instanceof ServerPlayer player)) {
			return;
		}
		pending = FishingFights.roll(this, player);
		if (pending != null) {
			pendingCount = pending.rollCount(random);
			if (stats.mods().sonar()) {
				ServerPlayNetworking.send(player, new FishingNetworking.SonarPing(pending.stack(),
						FishingFights.effectiveDifficulty(pending, stats)));
			}
		}
	}

	/** It bit: start the fight, or leave vanilla's nibble for a loot-table catch when no species lives here. */
	private void bite(ServerLevel level) {
		if (!(getPlayerOwner() instanceof ServerPlayer player)) {
			return;
		}
		if (pending == null) {
			pending = FishingFights.roll(this, player);
			pendingCount = pending == null ? 1 : pending.rollCount(random);
		}
		if (pending != null) {
			FishingFights.start(player, this, pending, pendingCount);
		} else if (inLava()) {
			// Vanilla's loot table has nothing for lava: nothing bites until a lava species can.
			resetWait();
			if (!toldEmptyLava) {
				toldEmptyLava = true;
				player.sendOverlayMessage(Component.translatable("hud.mymod.fishing.empty_lava"));
			}
		}
	}
}
