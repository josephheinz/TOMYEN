package com.jheinz.mymod.minigun;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.Ammo;
import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.ModItems;

/**
 * The works of a six-barrelled rotary gun, shared by the handheld {@link MinigunItem} and the Hogfire's gun mount.
 * Everything runs on the server, one tick at a time, and lands in a {@link MinigunState} the clients draw from.
 * <ul>
 * <li><b>Spin.</b> Holding the trigger spins the barrels up over {@value #SPIN_UP_TICKS} ticks (0.75 s, the motor's
 * whine climbing with them); letting go spins them down over {@value #SPIN_DOWN_TICKS}. Only at full speed does it
 * fire: a round every tick, 1200 rpm, evenly spaced.
 * <li><b>Heat.</b> Every round adds 1/{@value #ROUNDS_TO_OVERHEAT} (six seconds of fire); it sheds {@link #COOL_RATE}
 * a tick when it isn't firing (2.5 s from hot to cold). Reaching full heat overheats it: the barrels spin down and it
 * won't fire again until the heat is all gone, about four seconds.
 * <li><b>Ammo.</b> No magazine: 5.56 straight from the shooter's inventory, any kind, the first stack found (the hotbar
 * comes first). Each round hits like the round it is (silver and holy silver against the evil, tungsten through into a
 * second target, DU's fire) through {@link GunItem#hit}.
 * <li><b>Damage and the hurt cooldown.</b> A deliberate choice: the gun ignores it. Vanilla shrugs off hits for half
 * a second after one lands, so at 1200 rpm only one round in ten would count against a single target and the gun would
 * do less to it than a rifle. Instead each round clears the target's hurt cooldown and does {@link #DAMAGE_FACTOR} of
 * its round's damage: an FMJ round does 1.8, about 36 a second while the target stays in the cone (a Mk III repulsor
 * does about 27, a netherite sword about 13). The ammo bill (twenty rounds a second), the heat and the spin-up keep it
 * honest. Each round's knockback is cut to {@link #KNOCKBACK_KEPT}, so the stream doesn't bat targets out of it.
 * </ul>
 */
public final class RotaryGun {
	public static final int SPIN_UP_TICKS = 15;
	public static final int SPIN_DOWN_TICKS = 30;
	public static final int ROUNDS_TO_OVERHEAT = 120;
	public static final float COOL_RATE = 1.0f / 50.0f;
	/** How fast it sheds heat once it's overheated and locked out. */
	public static final float OVERHEAT_COOL_RATE = 1.0f / 80.0f;
	public static final float DAMAGE_FACTOR = 0.3f;
	public static final float KNOCKBACK_KEPT = 0.15f;
	public static final double RANGE = 80.0;
	/** Every this many rounds is a tracer. */
	private static final int TRACER_EVERY = 3;

	private RotaryGun() {
	}

	/**
	 * One tick of the gun.
	 *
	 * @param trigger    the trigger is held
	 * @param spinUpRate how much faster than usual the barrels spin up (a Neuralink Overclock doubles it)
	 * @param fireRound  fires one round, returning false if there was none to fire
	 */
	public static MinigunState tick(MinigunState state, boolean trigger, float spinUpRate, BooleanSupplier fireRound) {
		boolean spinning = trigger && !state.overheated();
		float spin = spinning ? Math.min(1.0f, state.spin() + spinUpRate / SPIN_UP_TICKS)
				: Math.max(0.0f, state.spin() - 1.0f / SPIN_DOWN_TICKS);
		boolean fired = spinning && spin >= 1.0f && fireRound.getAsBoolean();
		float heat = fired ? state.heat() + 1.0f / ROUNDS_TO_OVERHEAT
				: state.heat() - (state.overheated() ? OVERHEAT_COOL_RATE : COOL_RATE);
		heat = Mth.clamp(heat, 0.0f, 1.0f);
		boolean overheated = state.overheated() ? heat > 0.0f : heat >= 1.0f;
		return new MinigunState(spin, heat, overheated, fired);
	}

	// --- Ammo -------------------------------------------------------------------------------------------------

	/** The inventory slot of the first 5.56 rounds (the hotbar comes first), or -1. */
	private static int roundSlot(Player player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && Ammo.find(ModItems.AMMO_556, stack.typeHolder()) != null) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Takes one 5.56 round from the inventory, returning what kind it was, or null with none left. Creative fires
	 * whatever it carries without using it up, and FMJ when it carries none.
	 */
	public static @Nullable Ammo takeRound(Player player) {
		int slot = roundSlot(player);
		if (slot < 0) {
			return player.hasInfiniteMaterials() ? ModItems.AMMO_556.getFirst() : null;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		Ammo round = Ammo.find(ModItems.AMMO_556, stack.typeHolder());
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		return round;
	}

	/** Every 5.56 round the player carries. */
	public static int carried(Player player) {
		int total = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && Ammo.find(ModItems.AMMO_556, stack.typeHolder()) != null) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// --- Firing -----------------------------------------------------------------------------------------------

	/** {@code direction} turned a random amount up to {@code degrees} off it, spread evenly over the cone's disc. */
	public static Vec3 stray(Vec3 direction, float degrees, RandomSource random) {
		if (degrees <= 0.0f) {
			return direction;
		}
		Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(direction).normalize();
		double off = Math.tan(Math.toRadians(degrees) * Math.sqrt(random.nextFloat()));
		double angle = random.nextFloat() * Math.PI * 2.0;
		return direction.add(side.scale(off * Math.cos(angle))).add(up.scale(off * Math.sin(angle))).normalize();
	}

	/** {@code wanted}, or if it's more than {@code maxDegrees} off {@code heading}, as near to it as that allows. */
	public static Vec3 within(Vec3 heading, Vec3 wanted, float maxDegrees) {
		double cos = Mth.clamp(heading.dot(wanted), -1.0, 1.0);
		double max = Math.toRadians(maxDegrees);
		if (Math.acos(cos) <= max) {
			return wanted;
		}
		Vec3 across = wanted.subtract(heading.scale(cos));
		if (across.lengthSqr() < 1.0e-6) {
			return heading;
		}
		return heading.scale(Math.cos(max)).add(across.normalize().scale(Math.sin(max)));
	}

	/**
	 * Fires one round from {@code from} along {@code direction}: a hitscan out to {@link #RANGE}, through whatever it
	 * pierces, stopped by walls. The tracer and smoke come from {@code muzzle}. Never hits {@code mount} (the vehicle the
	 * gun is on) or anyone riding with the shooter.
	 */
	public static void shoot(ServerLevel level, Player shooter, Vec3 from, Vec3 direction, Vec3 muzzle, Ammo round,
			@Nullable Entity mount) {
		Ammo scaled = new Ammo(round.item(), round.damage() * DAMAGE_FACTOR, round.damageType(), round.color(), round.pierce(),
				round.burnSeconds());
		Vec3 end = from.add(direction.scale(RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = new AABB(from, end).inflate(1.0);
		List<Entity> struck = new ArrayList<>();
		Predicate<Entity> target = entity -> !entity.isSpectator() && entity.isPickable() && entity != mount
				&& !shooter.isPassengerOfSameVehicle(entity) && !struck.contains(entity);
		Vec3 stop = end;
		for (int i = 0; i <= scaled.pierce(); i++) {
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, from, end, sweep, target, from.distanceToSqr(end));
			if (hit == null) {
				break;
			}
			struck.add(hit.getEntity());
			strike(level, shooter, scaled, hit.getEntity());
			stop = hit.getLocation();
			level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.1, 0.1, 0.1, 0.2);
		}
		if (struck.isEmpty() && blockHit.getType() == HitResult.Type.BLOCK) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockHit.getBlockPos()));
			level.sendParticles(debris, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.1);
		}
		long now = level.getGameTime();
		if (now % TRACER_EVERY == 0) {
			GunItem.drawTracer(level, muzzle, stop, false);
		}
		if (now % 4 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 1, 0.03, 0.03, 0.03, 0.01);
		}
	}

	/** One round's hit, past the hurt cooldown and with most of its knockback taken out (see the class notes). */
	private static void strike(ServerLevel level, Player shooter, Ammo round, Entity target) {
		Vec3 before = target.getDeltaMovement();
		target.setInvulnerableTime(0);
		GunItem.hit(level, shooter, round, target);
		Vec3 kick = target.getDeltaMovement().subtract(before);
		target.setDeltaMovement(before.add(kick.scale(KNOCKBACK_KEPT)));
	}
}
