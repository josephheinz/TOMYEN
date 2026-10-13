package com.jheinz.mymod.mech;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.Ammo;
import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.industry.Industry;
import com.jheinz.mymod.launcher.Launchers;
import com.jheinz.mymod.launcher.PG7Rocket;
import com.jheinz.mymod.napalm.FlameStream;
import com.jheinz.mymod.napalm.Napalm;

/**
 * One of a {@link Mech}'s arms on the server: the weapon on its hardpoint, its trigger (attack for the left arm,
 * use for the right), and its load, heat, jams and cooldown, synced to the pilot's HUD as one packed int.
 * Everything fires along the arms' aim at the point the torso's sight line meets, so both arms converge on what's
 * under the reticle. Nothing fires without power (an empty core, or none), and ammunition comes from the stores:
 * the Cargo Pod first, then the pilot's own inventory (a creative pilot needs none).
 * <ul>
 * <li>Autocannon: {@value #AUTOCANNON_INTERVAL} ticks between rounds while held; each hits for
 * {@value #AUTOCANNON_DAMAGE} and bursts (power {@value #AUTOCANNON_BLAST}, no block damage). Thirty rounds to a
 * drum, {@value #AUTOCANNON_RELOAD} ticks to change it. The barrel heats; at full heat it locks until it cools.
 * <li>Rotary Cannon: spins up over {@value #SPIN_UP_TICKS} ticks, then fires a rifle round every tick (each at its
 * own damage), heating fast. 5.56 and 7.62 of any kind.
 * <li>Power Fist: a tap punches ({@value #PUNCH_DAMAGE}, heavy knockback, and it smashes up to three weak
 * blocks, hardness {@value #SMASH_HARDNESS} or less); held for {@value #GRAB_TICKS} ticks it grabs the nearest
 * mob, player or dropped item within reach, and the next tap throws it.
 * <li>Rocket Pod: four PG-7s (plain or homing) a load, one a press, {@value #ROCKET_RELOAD} ticks to reload.
 * Homing ones go for whatever living thing is under the reticle when they're fired.
 * <li>Flamer: a napalm stream while held, a canister ({@value #FLAMER_LOAD} ticks of fire) a load; the empty
 * canister goes back in the stores. Not underwater.
 * </ul>
 * A damaged arm jams: below {@value #JAM_FREE_CONDITION} condition, each shot has a growing chance of jamming
 * it for {@value #JAM_TICKS} ticks.
 */
public final class MechArm {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;

	public static final int READY = 0;
	public static final int RELOADING = 1;
	public static final int JAMMED = 2;
	public static final int OVERHEATED = 3;
	public static final int EMPTY = 4;
	public static final int NO_POWER = 5;
	public static final int HOLDING = 6;
	public static final int SPINNING = 7;

	public static final float JAM_FREE_CONDITION = 0.6f;
	static final int JAM_TICKS = 50;

	static final int AUTOCANNON_INTERVAL = 5;
	static final float AUTOCANNON_DAMAGE = 9.0f;
	static final float AUTOCANNON_BLAST = 1.2f;
	static final int AUTOCANNON_RELOAD = 50;
	/** Net of the cooling between rounds, sustained fire overheats it after about 45 rounds. */
	private static final float AUTOCANNON_HEAT = 0.07f;
	private static final float AUTOCANNON_SPREAD = 0.5f;

	static final int SPIN_UP_TICKS = 16;
	private static final int SPIN_DOWN_TICKS = 30;
	private static final float ROTARY_HEAT = 0.0125f;
	private static final float ROTARY_SPREAD = 1.6f;

	static final float PUNCH_DAMAGE = 20.0f;
	private static final double PUNCH_KNOCKBACK = 2.2;
	private static final int PUNCH_COOLDOWN = 16;
	static final float SMASH_HARDNESS = 3.0f;
	static final int GRAB_TICKS = 8;
	private static final double GRAB_REACH = 4.0;

	static final int ROCKET_RELOAD = 70;
	private static final int ROCKET_INTERVAL = 10;
	private static final double ROCKET_SPEED = 1.6;

	static final int FLAMER_LOAD = 200;
	private static final int FLAMER_RELOAD = 40;
	private static final float FLAMER_SPREAD = 6.0f;

	private static final float COOLING = 0.012f;
	private static final float RESUME_HEAT = 0.35f;
	private static final double RANGE = 160.0;

	// Fuel each use costs, before the torso's efficiency.
	private static final float AUTOCANNON_FUEL = 25.0f;
	private static final float ROTARY_FUEL = 3.0f;
	private static final float PUNCH_FUEL = 60.0f;
	static final float GRAB_FUEL = 40.0f;
	private static final float ROCKET_FUEL = 80.0f;
	private static final float FLAMER_FUEL = 6.0f;

	private final Mech mech;
	private final int side;
	private int loaded;
	private int reloadLeft;
	private int reloadTotal;
	private int cooldown;
	private float heat;
	private boolean overheated;
	private int jam;
	private float spin;
	private boolean wasSpinning;
	private int rounds;
	private boolean trigger;
	private int pressTicks;
	private boolean pressUsed;
	private boolean active;
	/** Bit i set when the rocket in tube i is a homing one. */
	private int homingTubes;
	private int reserve;
	private int lastSynced = -1;

	MechArm(Mech mech, int side) {
		this.mech = mech;
		this.side = side;
	}

	private MechWeapon weapon() {
		return mech.weapon(side);
	}

	private String sideKey() {
		return side == LEFT ? "hud.mymod.mech.left" : "hud.mymod.mech.right";
	}

	/** Ammunition in the stores this weapon can use, as the HUD counts it: rounds, rockets or canisters. */
	public int reserve() {
		return reserve;
	}

	// --- The trigger ---------------------------------------------------------------------------------------

	void triggerDown() {
		trigger = true;
		pressTicks = 0;
		pressUsed = false;
		if (weapon() == MechWeapon.POWER_FIST && mech.holding(side)) {
			pressUsed = true;
			mech.throwHeld();
		}
	}

	void triggerUp() {
		if (trigger && weapon() == MechWeapon.POWER_FIST && !pressUsed && pressTicks < GRAB_TICKS && mech.level() instanceof ServerLevel level) {
			punch(level);
		}
		trigger = false;
	}

	/** Lets go of the trigger without anything happening (the pilot left, or the power failed). */
	void release() {
		trigger = false;
		pressUsed = true;
	}

	// --- Ticking ---------------------------------------------------------------------------------------------

	void tick(ServerLevel level) {
		MechWeapon weapon = weapon();
		if (mech.pilotServer() == null) {
			trigger = false;
		}
		if (trigger) {
			pressTicks++;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		if (jam > 0 && --jam == 0) {
			mech.pilotSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.8f, 0.7f);
		}
		active = false;
		boolean fired = false;
		if (reloadLeft > 0) {
			tickReload(level, weapon);
		}
		switch (weapon) {
			case AUTOCANNON -> fired = tickAutocannon(level);
			case ROTARY_CANNON -> fired = tickRotary(level);
			case ROCKET_POD -> tickRockets(level);
			case FLAMER -> tickFlamer(level);
			case POWER_FIST -> tickFist(level);
			default -> {
			}
		}
		if (!fired) {
			heat = Math.max(0.0f, heat - COOLING);
			if (overheated && heat <= RESUME_HEAT) {
				overheated = false;
				mech.pilotSound(SoundEvents.FIRE_EXTINGUISH, 0.4f, 1.8f);
			}
		}
		if (mech.tickCount % 10 == side) {
			reserve = countReserve(weapon);
		}
		sync(weapon);
	}

	/** Whether the trigger can do anything at all right now; if it's pulled with no power, says so (once a press). */
	private boolean ready() {
		if (jam > 0 || overheated || reloadLeft > 0) {
			return false;
		}
		if (!mech.hasPower()) {
			if (trigger && !pressUsed) {
				pressUsed = true;
				mech.pilotSound(SoundEvents.DISPENSER_FAIL, 0.6f, 0.6f);
				mech.pilotMessage(Component.translatable("hud.mymod.mech.no_power").withStyle(ChatFormatting.RED));
			}
			return false;
		}
		return true;
	}

	/** An empty click, once a press. */
	private void dryFire() {
		if (!pressUsed) {
			pressUsed = true;
			mech.pilotSound(SoundEvents.DISPENSER_FAIL, 0.6f, 1.3f);
			mech.pilotMessage(Component.translatable("hud.mymod.mech.out_of_ammo", Component.translatable(sideKey()),
					Component.translatable(weapon().nameKey())).withStyle(ChatFormatting.GOLD));
		}
	}

	/** After a shot: a damaged arm may jam. {@code rate} scales the odds for weapons that fire many times a second. */
	private void maybeJam(RandomSource random, float rate) {
		float condition = mech.partCondition(MechPart.arm(side));
		if (condition < JAM_FREE_CONDITION && random.nextFloat() < (JAM_FREE_CONDITION - condition) * 0.12f * rate) {
			jam = JAM_TICKS;
			spin = 0.0f;
			mech.pilotSound(SoundEvents.CROSSBOW_QUICK_CHARGE_1.value(), 1.0f, 0.5f);
			mech.pilotMessage(Component.translatable("hud.mymod.mech.jammed", Component.translatable(sideKey()))
					.withStyle(ChatFormatting.RED));
		}
	}

	private void addHeat(float amount) {
		heat = Math.min(1.0f, heat + amount);
		if (heat >= 1.0f && !overheated) {
			overheated = true;
			spin = 0.0f;
			mech.pilotSound(SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.8f);
			mech.pilotMessage(Component.translatable("hud.mymod.mech.overheated", Component.translatable(sideKey()))
					.withStyle(ChatFormatting.RED));
		}
	}

	// --- Reloading ---------------------------------------------------------------------------------------------

	/** Starts a reload if there's ammunition for one; returns whether it did. Damage to the arm slows it. */
	private boolean startReload(MechWeapon weapon) {
		int base;
		switch (weapon) {
			case AUTOCANNON -> {
				if (mech.takeFromStores(stack -> stack.is(Mechs.AUTOCANNON_DRUM)).isEmpty()) {
					return false;
				}
				base = AUTOCANNON_RELOAD;
			}
			case ROCKET_POD -> {
				homingTubes = 0;
				int taken = 0;
				for (int tube = 0; tube < weapon.magazine(); tube++) {
					ItemStack rocket = mech.takeFromStores(stack -> Launchers.isRocket(stack.getItem()));
					if (rocket.isEmpty()) {
						break;
					}
					if (rocket.is(Launchers.PG7_HOMING_ROCKET)) {
						homingTubes |= 1 << tube;
					}
					taken++;
				}
				if (taken == 0) {
					return false;
				}
				// Only what was taken goes in the tubes.
				loaded = -taken;
				base = ROCKET_RELOAD;
			}
			case FLAMER -> {
				if (mech.takeFromStores(stack -> stack.is(Industry.NAPALM_CANISTER)).isEmpty()) {
					return false;
				}
				mech.giveToStores(new ItemStack(Industry.EMPTY_CANISTER));
				base = FLAMER_RELOAD;
			}
			default -> {
				return false;
			}
		}
		reloadTotal = Math.round(base * (1.0f + 0.6f * (1.0f - mech.partCondition(MechPart.arm(side)))));
		reloadLeft = reloadTotal;
		mech.pilotSound(SoundEvents.CROSSBOW_LOADING_START.value(), 1.0f, 0.6f);
		return true;
	}

	private void tickReload(ServerLevel level, MechWeapon weapon) {
		if (--reloadLeft > 0) {
			if (reloadLeft == reloadTotal / 2) {
				mech.pilotSound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.0f, 0.6f);
			}
			return;
		}
		reloadLeft = 0;
		loaded = switch (weapon) {
			case AUTOCANNON -> weapon.magazine();
			case ROCKET_POD -> Math.abs(loaded);
			case FLAMER -> weapon.magazine();
			default -> 0;
		};
		level.playSound(null, mech.getX(), mech.getY() + 3.0, mech.getZ(), Mechs.RELOAD_SOUND, SoundSource.NEUTRAL, 1.0f, 0.8f);
	}

	private int countReserve(MechWeapon weapon) {
		return switch (weapon) {
			case AUTOCANNON -> mech.countInStores(stack -> stack.is(Mechs.AUTOCANNON_DRUM)) * weapon.magazine();
			case ROTARY_CANNON -> mech.countInStores(stack -> rifleRound(stack.getItem()) != null);
			case ROCKET_POD -> mech.countInStores(stack -> Launchers.isRocket(stack.getItem()));
			case FLAMER -> mech.countInStores(stack -> stack.is(Industry.NAPALM_CANISTER));
			default -> 0;
		};
	}

	// --- Autocannon ------------------------------------------------------------------------------------------

	private boolean tickAutocannon(ServerLevel level) {
		if (loaded <= 0 && reloadLeft == 0 && jam == 0 && mech.hasPower()) {
			startReload(MechWeapon.AUTOCANNON);
		}
		if (!trigger || cooldown > 0 || !ready()) {
			return false;
		}
		if (loaded <= 0) {
			dryFire();
			return false;
		}
		loaded--;
		cooldown = AUTOCANNON_INTERVAL;
		active = true;
		ServerPlayer pilot = mech.pilotServer();
		Vec3 from = MechCrew.muzzle(mech, side, 1.0f);
		Vec3 direction = spread(level.getRandom(), MechCrew.fireDirection(mech, side, mech.aimPoint(level).point(), 1.0f), AUTOCANNON_SPREAD);
		Shot shot = shoot(level, from, direction);
		if (shot.entity != null) {
			shot.entity.hurtServer(level, mech.damage(ModDamageTypes.BULLET, pilot), AUTOCANNON_DAMAGE);
		}
		if (shot.entity != null || shot.block) {
			DamageSource blast = mech.damageSources().explosion(mech, pilot);
			level.explode(mech, blast, null, shot.end.x, shot.end.y, shot.end.z, AUTOCANNON_BLAST, false, Level.ExplosionInteraction.NONE);
		}
		GunItem.drawTracer(level, from, shot.end, false);
		muzzleFlash(level, from, direction, false);
		// A deep boom-thump: the report over a low blast, both carrying (3.0 is heard out to 48 blocks).
		float pitch = 0.95f + level.getRandom().nextFloat() * 0.1f;
		level.playSound(null, from.x, from.y, from.z, Mechs.AUTOCANNON_SOUND, SoundSource.NEUTRAL, 3.0f, pitch);
		level.playSound(null, from.x, from.y, from.z, Mechs.AUTOCANNON_THUMP_SOUND, SoundSource.NEUTRAL, 2.0f, pitch);
		mech.drain(AUTOCANNON_FUEL);
		mech.armFired(side);
		addHeat(AUTOCANNON_HEAT);
		maybeJam(level.getRandom(), 1.0f);
		return true;
	}

	// --- Rotary cannon ----------------------------------------------------------------------------------------

	/** The 5.56 or 7.62 round an item is, or null. */
	static @Nullable Ammo rifleRound(Item item) {
		for (List<Ammo> calibre : List.of(ModItems.AMMO_556, ModItems.AMMO_762)) {
			for (Ammo round : calibre) {
				if (round.item() == item) {
					return round;
				}
			}
		}
		return null;
	}

	private boolean tickRotary(ServerLevel level) {
		boolean spinning = trigger && ready();
		// The motor whines up from rest and winds down when let go; the barrels' whir in between is each client's (it
		// follows the synced spin).
		Vec3 motor = MechCrew.elbow(mech, side, 1.0f);
		if (spinning && !wasSpinning && spin < 0.5f) {
			level.playSound(null, motor.x, motor.y, motor.z, Mechs.ROTARY_SPIN_SOUND, SoundSource.NEUTRAL, 1.4f, 0.9f + spin * 0.4f);
		} else if (!spinning && wasSpinning && spin > 0.3f) {
			level.playSound(null, motor.x, motor.y, motor.z, Mechs.ROTARY_SPIN_DOWN_SOUND, SoundSource.NEUTRAL, 1.4f, 0.8f + spin * 0.3f);
		}
		wasSpinning = spinning;
		if (spinning) {
			spin = Math.min(1.0f, spin + 1.0f / SPIN_UP_TICKS);
		} else {
			spin = Math.max(0.0f, spin - 1.0f / SPIN_DOWN_TICKS);
		}
		active = spin > 0.0f;
		if (!spinning || spin < 1.0f) {
			return false;
		}
		ServerPlayer pilot = mech.pilotServer();
		Ammo round;
		if (pilot != null && pilot.hasInfiniteMaterials()) {
			round = ModItems.AMMO_556.getFirst();
		} else {
			ItemStack taken = mech.takeFromStores(stack -> rifleRound(stack.getItem()) != null);
			round = taken.isEmpty() ? null : rifleRound(taken.getItem());
		}
		if (round == null) {
			dryFire();
			spin = 0.0f;
			return false;
		}
		Vec3 from = MechCrew.muzzle(mech, side, 1.0f);
		Vec3 direction = spread(level.getRandom(), MechCrew.fireDirection(mech, side, mech.aimPoint(level).point(), 1.0f), ROTARY_SPREAD);
		Shot shot = shoot(level, from, direction);
		if (shot.entity != null) {
			shot.entity.hurtServer(level, mech.damage(round.damageType(), pilot), round.damage());
			if (round.burnSeconds() > 0.0f) {
				shot.entity.igniteForSeconds(round.burnSeconds());
			}
			level.sendParticles(ParticleTypes.CRIT, shot.end.x, shot.end.y, shot.end.z, 3, 0.1, 0.1, 0.1, 0.2);
		} else if (shot.block) {
			level.sendParticles(ParticleTypes.SMOKE, shot.end.x, shot.end.y, shot.end.z, 2, 0.05, 0.05, 0.05, 0.01);
		}
		if (mech.tickCount % 2 == 0) {
			GunItem.drawTracer(level, from, shot.end, false);
		}
		muzzleFlash(level, from, direction, true);
		// A real report every other round (ten a second, the M249's own rate) keeps twenty rounds a second a roar rather
		// than mush; the barrels' whir carries the rest. Pitch wanders a little so the reports don't phase.
		if (++rounds % 2 == 0) {
			level.playSound(null, from.x, from.y, from.z, Mechs.ROTARY_SOUND, SoundSource.NEUTRAL, 2.5f, 0.92f + level.getRandom().nextFloat() * 0.14f);
		}
		mech.drain(ROTARY_FUEL);
		addHeat(ROTARY_HEAT);
		maybeJam(level.getRandom(), 0.15f);
		return true;
	}

	// --- Rocket pod ---------------------------------------------------------------------------------------------

	private void tickRockets(ServerLevel level) {
		if (loaded <= 0 && reloadLeft == 0 && jam == 0 && mech.hasPower()) {
			startReload(MechWeapon.ROCKET_POD);
		}
		if (!trigger || cooldown > 0 || !ready()) {
			return;
		}
		if (loaded <= 0) {
			dryFire();
			return;
		}
		loaded--;
		boolean homing = (homingTubes >> loaded & 1) != 0;
		cooldown = ROCKET_INTERVAL;
		ServerPlayer pilot = mech.pilotServer();
		Mech.AimPoint aim = mech.aimPoint(level);
		Vec3 from = MechCrew.muzzle(mech, side, 1.0f);
		Vec3 direction = MechCrew.fireDirection(mech, side, aim.point(), 1.0f);
		Entity target = homing && aim.entity() != null && aim.entity().isAlive() ? aim.entity() : null;
		PG7Rocket rocket = new PG7Rocket(level, pilot != null ? pilot : mech, from,
				new ItemStack(homing ? Launchers.PG7_HOMING_ROCKET : Launchers.PG7_ROCKET), target);
		rocket.launch(direction.scale(ROCKET_SPEED));
		level.addFreshEntity(rocket);
		muzzleFlash(level, from, direction, false);
		level.playSound(null, from.x, from.y, from.z, Launchers.RPG_LAUNCH, SoundSource.NEUTRAL, 3.0f, 0.85f + level.getRandom().nextFloat() * 0.1f);
		mech.drain(ROCKET_FUEL);
		mech.armFired(side);
		maybeJam(level.getRandom(), 1.0f);
	}

	// --- Flamer ------------------------------------------------------------------------------------------------

	private void tickFlamer(ServerLevel level) {
		if (loaded <= 0 && reloadLeft == 0 && jam == 0 && mech.hasPower()) {
			startReload(MechWeapon.FLAMER);
		}
		if (!trigger || !ready()) {
			return;
		}
		ServerPlayer pilot = mech.pilotServer();
		boolean free = pilot != null && pilot.hasInfiniteMaterials();
		if (loaded <= 0 && !free) {
			dryFire();
			return;
		}
		Vec3 nozzle = MechCrew.muzzle(mech, side, 1.0f);
		if (pilot == null || level.getFluidState(BlockPos.containing(nozzle)).is(FluidTags.WATER)) {
			return;
		}
		if (!free) {
			loaded--;
		}
		active = true;
		FlameStream.spray(level, pilot, nozzle, MechCrew.fireDirection(mech, side, mech.aimPoint(level).point(), 1.0f), FLAMER_SPREAD);
		if (mech.tickCount % 4 == 0) {
			level.playSound(null, nozzle.x, nozzle.y, nozzle.z, Napalm.FLAMETHROWER_ROAR, SoundSource.NEUTRAL, 1.2f, 0.7f);
		}
		mech.drain(FLAMER_FUEL);
		if (mech.tickCount % 10 == 0) {
			maybeJam(level.getRandom(), 1.0f);
		}
	}

	// --- Power fist ---------------------------------------------------------------------------------------------

	private void tickFist(ServerLevel level) {
		active = trigger && !pressUsed;
		if (trigger && !pressUsed && pressTicks == GRAB_TICKS) {
			pressUsed = true;
			if (ready() && !mech.holding(side)) {
				grab(level);
			}
		}
	}

	private void punch(ServerLevel level) {
		if (cooldown > 0 || !ready()) {
			return;
		}
		cooldown = PUNCH_COOLDOWN;
		ServerPlayer pilot = mech.pilotServer();
		Vec3 aim = MechCrew.aim(mech, 1.0f);
		Vec3 knuckles = MechCrew.muzzle(mech, side, 1.0f);
		Vec3 centre = knuckles.add(aim.scale(1.0));
		boolean landed = false;
		for (Entity target : level.getEntities(mech, new AABB(centre, centre).inflate(1.4), mech::canStrike)) {
			target.hurtServer(level, mech.damage(Mechs.FIST_DAMAGE, pilot), PUNCH_DAMAGE);
			Vec3 push = aim.multiply(1.0, 0.0, 1.0).normalize().scale(PUNCH_KNOCKBACK);
			target.push(push.x, 0.45, push.z);
			target.needsSync = true;
			landed = true;
		}
		BlockHitResult block = level.clip(new ClipContext(MechCrew.elbow(mech, side, 1.0f), centre.add(aim.scale(0.6)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, mech));
		if (block.getType() == HitResult.Type.BLOCK) {
			landed |= smash(level, pilot, block.getBlockPos());
			centre = block.getLocation();
		}
		level.sendParticles(landed ? ParticleTypes.EXPLOSION : ParticleTypes.CLOUD, centre.x, centre.y, centre.z, landed ? 1 : 4, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, centre.x, centre.y, centre.z, Mechs.PUNCH_SOUND, SoundSource.NEUTRAL, landed ? 1.6f : 0.8f,
				landed ? 0.7f : 1.1f);
		mech.drain(PUNCH_FUEL);
		mech.armFired(side);
	}

	/** Breaks the block the fist drove into and the ones above and below it, if they're weak enough. */
	private boolean smash(ServerLevel level, @Nullable ServerPlayer pilot, BlockPos pos) {
		if (pilot == null || !pilot.mayBuild()) {
			return false;
		}
		boolean broke = false;
		for (BlockPos at : new BlockPos[] {pos, pos.above(), pos.below()}) {
			BlockState state = level.getBlockState(at);
			float hardness = state.getDestroySpeed(level, at);
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || hardness < 0.0f || hardness > SMASH_HARDNESS
					|| !level.mayInteract(pilot, at)) {
				continue;
			}
			level.destroyBlock(at, true, pilot);
			broke = true;
		}
		return broke;
	}

	private void grab(ServerLevel level) {
		Vec3 aim = MechCrew.aim(mech, 1.0f);
		Vec3 knuckles = MechCrew.muzzle(mech, side, 1.0f);
		Vec3 reach = knuckles.add(aim.scale(GRAB_REACH));
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity candidate : level.getEntities(mech, new AABB(knuckles, reach).inflate(1.2), mech::canGrab)) {
			double distance = candidate.getBoundingBox().getCenter().distanceToSqr(knuckles);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = candidate;
			}
		}
		if (best == null) {
			mech.pilotSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6f, 0.6f);
			mech.pilotMessage(Component.translatable("hud.mymod.mech.nothing_to_grab").withStyle(ChatFormatting.GRAY));
			return;
		}
		mech.grab(best, side);
	}

	// --- Shooting -----------------------------------------------------------------------------------------------

	/** Where a hitscan round ends: on the first thing (or block) along its path, or at full range. */
	private record Shot(Vec3 end, @Nullable Entity entity, boolean block) {
	}

	private Shot shoot(ServerLevel level, Vec3 from, Vec3 direction) {
		Vec3 end = from.add(direction.scale(RANGE));
		HitResult blockHit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mech));
		boolean block = blockHit.getType() != HitResult.Type.MISS;
		if (block) {
			end = blockHit.getLocation();
		}
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mech, from, end, new AABB(from, end).inflate(1.0), mech::canStrike,
				from.distanceToSqr(end));
		if (entityHit != null) {
			return new Shot(entityHit.getLocation(), entityHit.getEntity(), false);
		}
		return new Shot(end, null, block);
	}

	private static Vec3 spread(RandomSource random, Vec3 direction, float degrees) {
		return FlameStream.scatter(direction, degrees, random);
	}

	private void muzzleFlash(ServerLevel level, Vec3 muzzle, Vec3 direction, boolean light) {
		Vec3 front = muzzle.add(direction.scale(0.4));
		level.sendParticles(ParticleTypes.FLAME, front.x, front.y, front.z, light ? 1 : 4, 0.08, 0.08, 0.08, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, front.x, front.y, front.z, light ? 1 : 3, 0.1, 0.1, 0.1, 0.02);
	}

	// --- Syncing and saving -----------------------------------------------------------------------------------

	/**
	 * Packs the arm for the HUD: rounds loaded (bits 0-9), heat in percent (10-16), progress in percent (17-23: the
	 * reload, the barrels' spin, the fist's grip or the jam clearing), the status (24-26) and whether it's firing
	 * or gripping right now (27).
	 */
	private void sync(MechWeapon weapon) {
		int status;
		float progress = 0.0f;
		if (jam > 0) {
			status = JAMMED;
			progress = 1.0f - jam / (float) JAM_TICKS;
		} else if (overheated) {
			status = OVERHEATED;
		} else if (reloadLeft > 0) {
			status = RELOADING;
			progress = 1.0f - reloadLeft / (float) Math.max(1, reloadTotal);
		} else if (weapon == MechWeapon.POWER_FIST && mech.holding(side)) {
			status = HOLDING;
		} else if (!mech.hasPower() && weapon != MechWeapon.NONE) {
			status = NO_POWER;
		} else if (weapon == MechWeapon.ROTARY_CANNON && spin > 0.0f && spin < 1.0f) {
			status = SPINNING;
			progress = spin;
		} else if (weapon.magazine() > 0 && loaded <= 0 && reserve <= 0 || weapon == MechWeapon.ROTARY_CANNON && reserve <= 0) {
			status = EMPTY;
		} else {
			status = READY;
			if (weapon == MechWeapon.ROTARY_CANNON) {
				progress = spin;
			} else if (weapon == MechWeapon.POWER_FIST && trigger && !pressUsed) {
				progress = Math.min(1.0f, pressTicks / (float) GRAB_TICKS);
			}
		}
		int packed = Mth.clamp(loaded, 0, 1023) | Math.round(heat * 100.0f) << 10 | Math.round(Mth.clamp(progress, 0.0f, 1.0f) * 100.0f) << 17
				| status << 24 | (active ? 1 << 27 : 0);
		if (packed != lastSynced) {
			lastSynced = packed;
			mech.syncArm(side, packed);
		}
	}

	/** The weapon on this hardpoint changed: start it empty. */
	void reset() {
		loaded = 0;
		reloadLeft = 0;
		heat = 0.0f;
		overheated = false;
		jam = 0;
		spin = 0.0f;
		homingTubes = 0;
		trigger = false;
	}

	void save(ValueOutput output) {
		output.putInt("Loaded", Math.abs(loaded));
		output.putInt("ReloadLeft", reloadLeft);
		output.putInt("ReloadTotal", reloadTotal);
		output.putFloat("Heat", heat);
		output.putBoolean("Overheated", overheated);
		output.putInt("Jam", jam);
		output.putInt("Homing", homingTubes);
	}

	void load(ValueInput input) {
		loaded = input.getIntOr("Loaded", 0);
		reloadLeft = input.getIntOr("ReloadLeft", 0);
		reloadTotal = input.getIntOr("ReloadTotal", 1);
		if (reloadLeft > 0 && weapon() == MechWeapon.ROCKET_POD) {
			loaded = -loaded;
		}
		heat = input.getFloatOr("Heat", 0.0f);
		overheated = input.getBooleanOr("Overheated", false);
		jam = input.getIntOr("Jam", 0);
		homingTubes = input.getIntOr("Homing", 0);
	}

	// --- Readouts from a packed value (client side) --------------------------------------------------------------

	public static int loaded(int packed) {
		return packed & 1023;
	}

	public static float heat(int packed) {
		return (packed >> 10 & 127) / 100.0f;
	}

	public static float progress(int packed) {
		return (packed >> 17 & 127) / 100.0f;
	}

	public static int status(int packed) {
		return packed >> 24 & 7;
	}

	public static boolean active(int packed) {
		return (packed >> 27 & 1) != 0;
	}
}
