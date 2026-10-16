package com.jheinz.tomyen.soul;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;

/**
 * The Soul Vacuum at work, on the server.
 *
 * <p>Sucking: each tick the trigger's held, the vacuum keeps hold of its mob while it stays within
 * {@link #HOLD_ANGLE} degrees of where the player looks (and in range and sight), or else picks the mob nearest the
 * middle of the {@link #AIM_ANGLE} degree cone. The pull starts at {@link #PULL_START} blocks a tick and grows by
 * {@link #PULL_RAMP} a tick to {@link #PULL_MAX}, less any knockback resistance, and lifts the mob off its feet. A mob
 * is captured when it reaches the nozzle, or after {@link #WEAKENED_TICKS} ticks in the beam once it's down to a
 * fifth of its health. Bosses (tomyen:soul_vacuum_immune) are never taken, nor players. Elites
 * (tomyen:soul_vacuum_resistant, the Schnozzle among them) feel only {@link #ELITE_PULL} of the pull and resist
 * capture entirely until they're below a quarter of their health.
 *
 * <p>Capturing: the soul is stored at once (whole for a hostile mob, weak for anything else), then over
 * {@link #CAPTURE_TICKS} ticks the mob shrinks and spirals into the nozzle trailing soul fire, and vanishes with a
 * wail: no death, so no loot or experience. It can't be hurt meanwhile.
 */
public final class SoulCapture {
	private static final double AIM_ANGLE = 15.0;
	private static final double HOLD_ANGLE = 30.0;
	private static final double AIM_COS = Math.cos(Math.toRadians(AIM_ANGLE));
	private static final double HOLD_COS = Math.cos(Math.toRadians(HOLD_ANGLE));
	private static final double PULL_START = 0.06;
	private static final double PULL_RAMP = 0.012;
	private static final double PULL_MAX = 0.6;
	/** Cancels most of gravity while in the beam, so mobs come off the ground. */
	private static final double PULL_LIFT = 0.07;
	private static final double ELITE_PULL = 0.25;
	private static final float WEAKENED_HEALTH = 0.2f;
	private static final float ELITE_CAPTURE_HEALTH = 0.25f;
	private static final int WEAKENED_TICKS = 10;
	/** How close (blocks, beyond the mob's half-width) to the nozzle counts as reaching it. */
	private static final double CAPTURE_DISTANCE = 1.2;
	private static final int CAPTURE_TICKS = 14;
	private static final Identifier CAPTURE_SCALE_ID = MyMod.id("soul_capture_shrink");

	private static final Map<UUID, Beam> BEAMS = new HashMap<>();
	private static final List<Capture> CAPTURES = new ArrayList<>();

	/**
	 * A player's beam: what it has hold of, for how long, and the last tick it ran (switching items lets go of the
	 * trigger without a release).
	 */
	private static final class Beam {
		Mob target;
		int ticks;
		int lastTick;
	}

	private static final class Capture {
		final Mob mob;
		final Player player;
		final Vec3 start;
		Vec3 nozzle;
		int age;

		Capture(Mob mob, Player player) {
			this.mob = mob;
			this.player = player;
			this.start = mob.getBoundingBox().getCenter();
			this.nozzle = SoulVacuumItem.nozzle(player);
		}
	}

	private SoulCapture() {
	}

	public static boolean isCapturing(Entity entity) {
		for (Capture capture : CAPTURES) {
			if (capture.mob == entity) {
				return true;
			}
		}
		return false;
	}

	/** Whether the vacuum can take this mob at all. */
	public static boolean canSuck(Entity entity) {
		return entity instanceof Mob mob && mob.isAlive() && !mob.isInvulnerable() && !mob.is(Souls.VACUUM_IMMUNE)
				&& !isCapturing(mob);
	}

	public static boolean isElite(Mob mob) {
		return mob.is(Souls.VACUUM_RESISTANT);
	}

	static void stop(Player player) {
		BEAMS.remove(player.getUUID());
	}

	/** One tick of the trigger held: aim, pull, and capture. */
	static void suck(ServerLevel level, Player player, ItemStack stack) {
		Beam beam = BEAMS.computeIfAbsent(player.getUUID(), id -> new Beam());
		int now = level.getServer().getTickCount();
		if (beam.lastTick < now - 1) {
			beam.target = null;
		}
		beam.lastTick = now;
		SoulCanister canister = SoulVacuumItem.canister(stack);
		if (canister.isFull()) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.soul_vacuum.full").withStyle(ChatFormatting.AQUA));
			player.stopUsingItem();
			stop(player);
			return;
		}
		Vec3 nozzle = SoulVacuumItem.nozzle(player);
		Mob target = stillHeld(player, beam.target) ? beam.target : aim(level, player);
		if (target != beam.target) {
			beam.target = target;
			beam.ticks = 0;
		}
		if (target == null) {
			return;
		}
		beam.ticks++;
		boolean elite = isElite(target);
		Vec3 centre = target.getBoundingBox().getCenter();
		Vec3 toNozzle = nozzle.subtract(centre);
		double distance = toNozzle.length();
		double resistance = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		double pull = Math.min(PULL_MAX, PULL_START + PULL_RAMP * beam.ticks) * (elite ? ELITE_PULL : 1.0) * (1.0 - resistance * 0.5);
		// Never overshoot the nozzle.
		pull = Math.min(pull, distance * 0.5);
		Vec3 velocity = target.getDeltaMovement().scale(0.5).add(toNozzle.normalize().scale(pull));
		target.setDeltaMovement(velocity.add(0.0, elite ? 0.0 : PULL_LIFT, 0.0));
		target.needsSync = true;
		target.resetFallDistance();
		target.getNavigation().stop();
		// Soul fire streaming off the mob toward the nozzle.
		if (beam.ticks % 2 == 0) {
			Vec3 drift = toNozzle.normalize();
			for (int i = 0; i < 2; i++) {
				double t = level.getRandom().nextDouble();
				Vec3 at = centre.add(toNozzle.scale(t * 0.8));
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 0, drift.x, drift.y, drift.z, 0.15);
			}
			level.sendParticles(ParticleTypes.SOUL, centre.x, centre.y, centre.z, 1, target.getBbWidth() * 0.3, target.getBbHeight() * 0.3,
					target.getBbWidth() * 0.3, 0.02);
		}
		boolean weakened = target.getHealth() <= target.getMaxHealth() * (elite ? ELITE_CAPTURE_HEALTH : WEAKENED_HEALTH);
		if (elite && !weakened) {
			if (beam.ticks % 20 == 1) {
				player.sendOverlayMessage(Component.translatable("item.tomyen.soul_vacuum.resists", target.getDisplayName())
						.withStyle(ChatFormatting.RED));
				level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, centre.y, centre.z, 6, target.getBbWidth() * 0.4,
						target.getBbHeight() * 0.4, target.getBbWidth() * 0.4, 0.02);
			}
			return;
		}
		boolean reached = distance < CAPTURE_DISTANCE + target.getBbWidth() / 2.0;
		if (reached || weakened && beam.ticks >= WEAKENED_TICKS) {
			capture(level, player, stack, canister, target);
			beam.target = null;
			beam.ticks = 0;
		}
	}

	private static boolean stillHeld(Player player, Mob target) {
		if (target == null || !canSuck(target) || target.level() != player.level()) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
		double distance = toTarget.length();
		return distance <= SoulVacuumItem.RANGE + 2.0 && toTarget.dot(player.getViewVector(1.0f)) >= distance * HOLD_COS
				&& player.hasLineOfSight(target);
	}

	/** The mob nearest the middle of the cone (or one the line of sight runs straight through), within range. */
	private static Mob aim(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(SoulVacuumItem.RANGE));
		AABB reach = player.getBoundingBox().inflate(SoulVacuumItem.RANGE + 2.0);
		Mob best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, reach, SoulCapture::canSuck)) {
			Vec3 toMob = mob.getBoundingBox().getCenter().subtract(eye);
			double distance = toMob.length();
			if (distance > SoulVacuumItem.RANGE + mob.getBbWidth() / 2.0 || distance < 1.0E-3) {
				continue;
			}
			double cos = toMob.dot(look) / distance;
			boolean onLine = mob.getBoundingBox().inflate(0.3).clip(eye, end).isPresent();
			if ((cos < AIM_COS && !onLine) || !player.hasLineOfSight(mob)) {
				continue;
			}
			double score = (onLine ? 1.0 : cos) - distance * 0.01;
			if (score > bestScore) {
				bestScore = score;
				best = mob;
			}
		}
		return best;
	}

	private static void capture(ServerLevel level, Player player, ItemStack stack, SoulCanister canister, Mob mob) {
		stack.set(Souls.SOUL_CANISTER, canister.with(!(mob instanceof Enemy)));
		mob.ejectPassengers();
		mob.stopRiding();
		mob.setSilent(true);
		CAPTURES.add(new Capture(mob, player));
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	/** Runs the capture animations after everything else has moved this tick. */
	static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		BEAMS.values().removeIf(beam -> beam.lastTick < now - 20);
		for (Iterator<Capture> it = CAPTURES.iterator(); it.hasNext(); ) {
			Capture capture = it.next();
			Mob mob = capture.mob;
			if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (!capture.player.isRemoved() && capture.player.level() == level) {
				capture.nozzle = SoulVacuumItem.nozzle(capture.player);
			}
			capture.age++;
			float t = Math.min(1.0f, (float) capture.age / CAPTURE_TICKS);
			shrink(mob, t);
			// Along the line to the nozzle, easing in, round a spiral that tightens as it goes.
			Vec3 line = capture.nozzle.subtract(capture.start);
			Vec3 side = line.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
			Vec3 up = side.cross(line).normalize();
			double angle = capture.age * 0.9;
			double radius = (1.0 - t) * 0.6;
			Vec3 centre = capture.start.add(line.scale(t * t)).add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
			mob.setPos(centre.x, centre.y - mob.getBbHeight() / 2.0, centre.z);
			mob.setDeltaMovement(Vec3.ZERO);
			mob.needsSync = true;
			mob.getNavigation().stop();
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x, centre.y, centre.z, 3, 0.15, 0.15, 0.15, 0.02);
			if (capture.age % 3 == 0) {
				level.sendParticles(ParticleTypes.SOUL, centre.x, centre.y, centre.z, 1, 0.1, 0.1, 0.1, 0.02);
			}
			if (capture.age >= CAPTURE_TICKS) {
				Vec3 nozzle = capture.nozzle;
				level.sendParticles(ParticleTypes.SOUL, nozzle.x, nozzle.y, nozzle.z, 8, 0.15, 0.15, 0.15, 0.04);
				level.sendParticles(ParticleTypes.SCULK_SOUL, nozzle.x, nozzle.y, nozzle.z, 4, 0.1, 0.1, 0.1, 0.02);
				level.playSound(null, nozzle.x, nozzle.y, nozzle.z, Souls.SOUL_WAIL, SoundSource.PLAYERS, 1.0f,
						0.85f + level.getRandom().nextFloat() * 0.3f);
				mob.discard();
				it.remove();
			}
		}
	}

	private static void shrink(LivingEntity mob, float t) {
		AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.removeModifier(CAPTURE_SCALE_ID);
			scale.addTransientModifier(new AttributeModifier(CAPTURE_SCALE_ID, -0.9 * t, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
