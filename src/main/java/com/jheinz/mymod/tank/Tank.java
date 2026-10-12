package com.jheinz.mymod.tank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.bank.Collector;
import com.jheinz.mymod.bank.Collectors;
import com.jheinz.mymod.powerarmor.PowerArmor;

/**
 * A main battle tank, about 3.5 blocks wide and 8 long: tracked hull, a turret that traverses on its own and a
 * 120mm gun that elevates in it, with a coaxial machine gun and smoke launchers (see {@link TankGunnery}).
 * Its collision box is a 3.5 by 2.8 square (all a vanilla entity can have); the hull's nose and tail reach past
 * it, so it also feels ahead with a "bumper" and stops short of walls it can't climb. It steps up a block, and
 * crushes weak blocks it drives into (leaves, fences, glass panes, flowers, crops) and pushes and runs over
 * small mobs; block breaking needs build rights for a player driver, and mobGriefing otherwise.
 * <p>
 * <b>Crew.</b> Use it to climb in (see {@link TankCrew}): up the hull, through the commander's hatch and into
 * the gunner's seat, locked in place while the hatch opens and shuts. Hold sneak for {@link TankCrew#EXIT_HOLD_TICKS}
 * to climb back out. While in the seat the crew can't be hurt (the tank takes the hits) and drives and fights
 * it: W/S throttle (it speeds up and brakes with some weight to it), A/D pivot the hull, and the turret and gun
 * slew towards where they look at {@link #TRAVERSE_SPEED} and {@link #ELEVATE_SPEED} degrees a tick. Nobody can
 * climb into a tank someone's in, or into one of the bank's.
 * <p>
 * <b>Damage.</b> {@link #MAX_HEALTH} of armour that shrugs off small arms (a third of their damage) and fire
 * but takes the full weight of explosions and tank rounds. Heavy hits also damage a {@link TankPart} depending on
 * where they land: low or on the flank, the tracks; at the back, the engine; on the turret, the ring or the gun.
 * Iron ingots (or blocks) patch up the hull and the parts. Sneak-punch it with an empty hand, repaired, to pack
 * it into an item, ammo rack and all. Destroyed, it burns out: the crew is thrown clear (and singed), and the
 * rack's contents and some scrap drop.
 * <p>
 * <b>The bank's tanks.</b> With a {@link com.jheinz.mymod.bank.DebtCollector} in the hatch it drives itself
 * (see {@link TankAttackGoal}), firing collector shells from an endless supply. They're lighter
 * ({@link #COLLECTOR_HEALTH}), can't be boarded, and drop nothing.
 */
public class Tank extends PathfinderMob implements Collector {
	public static final float MAX_HEALTH = 320.0f;
	public static final float COLLECTOR_HEALTH = 120.0f;
	/** Entity event: the gun fired, so the client kicks the barrel and hull back. */
	static final byte FIRE_EVENT = 4;
	public static final int RECOIL_TICKS = 10;
	/** Top speed (as a ridden speed, about a sprinting player), and the share of it in reverse. */
	public static final float MAX_SPEED = 0.135f;
	private static final float REVERSE = 0.45f;
	/** Share of full throttle gained a tick speeding up, and lost a tick braking or coasting. */
	private static final float ACCELERATION = 1.0f / 50.0f;
	private static final float BRAKING = 1.0f / 18.0f;
	/** Degrees a tick the hull pivots under A/D, and how fast it gets up to that. */
	private static final float TURN_SPEED = 1.9f;
	private static final float TURN_ACCELERATION = 0.3f;
	/** Degrees a tick the turret traverses and the gun elevates, with a sound turret ring. */
	public static final float TRAVERSE_SPEED = 2.0f;
	public static final float ELEVATE_SPEED = 1.2f;
	/** Gun limits: 20 degrees up (negative pitch) and 9 down. */
	public static final float MIN_PITCH = -20.0f;
	public static final float MAX_PITCH = 9.0f;
	/** Speed the bank's tanks drive themselves at. */
	private static final float AI_SPEED = 0.11f;
	private static final float REPAIR_PER_INGOT = 10.0f;
	private static final float PARTS_REPAIR_PER_INGOT = 0.08f;
	/** Running over something at more than this (blocks a tick) hurts it. */
	private static final double CRUSH_SPEED = 0.07;
	/** Hits that leave at least this much (after the armour) can damage a part. */
	private static final float PART_HIT_THRESHOLD = 6.0f;

	public static final int SEQ_NONE = 0;
	public static final int SEQ_ENTERING = 1;
	public static final int SEQ_EXITING = 2;

	static final EntityDataAccessor<Float> TURRET_YAW = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.FLOAT);
	static final EntityDataAccessor<Float> GUN_PITCH = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.FLOAT);
	/** Breech state, the round in it and the next round picked, packed (see {@link TankGunnery}). */
	static final EntityDataAccessor<Integer> BREECH = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.INT);
	/** Ticks of loading left and the total, packed. */
	static final EntityDataAccessor<Integer> LOAD = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.INT);
	/** APFSDS, HE and gunpowder in the rack, ten bits each. */
	static final EntityDataAccessor<Integer> RACK = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Byte> SMOKE = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.BYTE);
	/** Coax heat in thousandths, and bit 16 when it's jammed. */
	static final EntityDataAccessor<Integer> COAX = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> PARTS = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.INT);
	/** The crew's climb ({@link #SEQ_NONE}, in or out), with bit 2 set when it's up the left side. */
	static final EntityDataAccessor<Byte> SEQUENCE = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.BYTE);
	static final EntityDataAccessor<Long> SEQUENCE_START = SynchedEntityData.defineId(Tank.class, EntityDataSerializers.LONG);

	private final TankGunnery gunnery;
	private @Nullable UUID debtor;
	private float turretYaw;
	private float turretYawO;
	private float gunPitch;
	private float gunPitchO;
	// Server: where the bank's crew wants the gun, and how long the gunner has held sneak.
	private @Nullable Vec3 aimPoint;
	private float aimYaw;
	private float aimPitch;
	private int exitHold;
	// The authoritative side (the driver's client): throttle and turn, -1 to 1 and degrees a tick.
	private float throttle;
	private float turnRate;
	// Client side: barrel and hull kick, track travel (blocks) for the tread animation, the lased range.
	private int recoil;
	private float trackLeft;
	private float trackLeftO;
	private float trackRight;
	private float trackRightO;
	private float lastHullYaw;
	/** Client side: the range the gunner last lased, in blocks (0 if none), for the fire control's prediction. */
	public double clientLasedRange;

	public Tank(EntityType<? extends Tank> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		gunnery = new TankGunnery(this);
		moveControl = new TankAttackGoal.TankMoveControl(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, AI_SPEED)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.CAMERA_DISTANCE, 9.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TURRET_YAW, 0.0f);
		builder.define(GUN_PITCH, 0.0f);
		builder.define(BREECH, 0);
		builder.define(LOAD, 0);
		builder.define(RACK, 0);
		builder.define(SMOKE, (byte) TankGunnery.MAX_SMOKE);
		builder.define(COAX, 0);
		builder.define(PARTS, TankPart.ALL_SOUND);
		builder.define(SEQUENCE, (byte) SEQ_NONE);
		builder.define(SEQUENCE_START, 0L);
	}

	<T> void sync(EntityDataAccessor<T> accessor, T value) {
		entityData.set(accessor, value);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(2, new TankAttackGoal(this));
	}

	/** The hull only faces the way the tracks point; it never swings round to where the turret aims. */
	@Override
	protected BodyRotationControl createBodyControl() {
		return new BodyRotationControl(this) {
			@Override
			public void clientTick() {
				yBodyRot = getYRot();
			}
		};
	}

	@Override
	public int getMaxHeadYRot() {
		return 180;
	}

	public TankGunnery gunnery() {
		return gunnery;
	}

	// --- Collectors ---------------------------------------------------------------------------------------------

	@Override
	public @Nullable UUID debtor() {
		return debtor;
	}

	/** Sending it after a debtor makes it one of the bank's lighter tanks. */
	@Override
	public void setDebtor(@Nullable UUID debtor) {
		this.debtor = debtor;
		if (debtor != null && getAttribute(Attributes.MAX_HEALTH) != null) {
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(COLLECTOR_HEALTH);
			setHealth(COLLECTOR_HEALTH);
		}
	}

	public boolean isCollectorTank() {
		return debtor != null;
	}

	// --- Ticking -------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		turretYawO = turretYaw;
		gunPitchO = gunPitch;
		trackLeftO = trackLeft;
		trackRightO = trackRight;
		super.tick();
		tickTurret();
		if (level() instanceof ServerLevel level) {
			if (debtor != null) {
				Collectors.tick(this);
			}
			tickCrew(level);
			gunnery.tick(level);
			tickTerrain(level);
		} else {
			if (recoil > 0) {
				recoil--;
			}
			tickTracks();
			tickExhaust();
		}
	}

	/** Slews the turret and gun towards whoever's aiming them: the gunner's view, or the bank crew's target. */
	private void tickTurret() {
		Player crew = seatedPlayer();
		if (level().isClientSide()) {
			if (crew != null && crew.isLocalPlayer()) {
				slew(crew.getYRot(), crew.getXRot() - superelevationNow());
			} else {
				turretYaw = entityData.get(TURRET_YAW);
				gunPitch = entityData.get(GUN_PITCH);
			}
			return;
		}
		if (crew != null) {
			slew(crew.getYRot(), crew.getXRot() - gunnery.superelevation());
		} else if (aimPoint != null) {
			Vec3 from = TankCrew.turretPoint(this, 1.0f, 0.0, 5.0 / 16.0, 36.0 / 16.0);
			Vec3 to = aimPoint.subtract(from);
			double flat = to.horizontalDistance();
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
			aimYaw = yaw;
			aimPitch = Mth.clamp((float) -(Mth.atan2(to.y, flat) * Mth.RAD_TO_DEG) - ShellType.COLLECTOR.elevationFor(flat), MIN_PITCH, MAX_PITCH);
			slew(aimYaw, aimPitch);
		}
		entityData.set(TURRET_YAW, turretYaw);
		entityData.set(GUN_PITCH, gunPitch);
		yHeadRot = turretYaw;
	}

	private void slew(float wantYaw, float wantPitch) {
		float ring = 0.3f + 0.7f * partCondition(TankPart.TURRET_RING);
		float traverse = TRAVERSE_SPEED * ring;
		float elevate = ELEVATE_SPEED * ring;
		turretYaw = Mth.wrapDegrees(turretYaw + Mth.clamp(Mth.wrapDegrees(wantYaw - turretYaw), -traverse, traverse));
		gunPitch = Mth.clamp(gunPitch + Mth.clamp(Mth.clamp(wantPitch, MIN_PITCH, MAX_PITCH) - gunPitch, -elevate, elevate),
				MIN_PITCH, MAX_PITCH);
	}

	/** The bank crew's target for the gun, or null to hold it where it is. */
	void aimAt(@Nullable Vec3 point) {
		aimPoint = point;
	}

	/** Whether the gun has come round onto the bank crew's target. */
	boolean aimSettled() {
		return aimPoint != null && Math.abs(Mth.wrapDegrees(aimYaw - turretYaw)) < 3.0f && Math.abs(aimPitch - gunPitch) < 3.0f;
	}

	/** Degrees the fire control holds the gun above the sight line (predicted on the gunner's client). */
	public float superelevationNow() {
		if (!level().isClientSide()) {
			return gunnery.superelevation();
		}
		return clientLasedRange > 0.0 ? laidFor().elevationFor(clientLasedRange) : 0.0f;
	}

	/** Client side: the tread travel since the last tick, from how far the hull moved and turned. */
	private void tickTracks() {
		float yaw = getYRot();
		Vec3 moved = position().subtract(xo, yo, zo);
		float radians = yaw * Mth.DEG_TO_RAD;
		double forward = -Mth.sin(radians) * moved.x + Mth.cos(radians) * moved.z;
		// Turning right (yaw up) drives the left track forward and the right one back, 1.4 blocks out each side.
		float turn = Mth.wrapDegrees(yaw - lastHullYaw) * Mth.DEG_TO_RAD * 1.4f;
		lastHullYaw = yaw;
		if (Math.abs(turn) > 0.5f) {
			turn = 0.0f;
		}
		trackLeft += (float) forward + turn;
		trackRight += (float) forward - turn;
	}

	/** Client side: diesel smoke from the exhausts while crewed, heavier on the move. */
	private void tickExhaust() {
		if (!isVehicle() || !isAlive()) {
			return;
		}
		boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-3;
		if (tickCount % (moving ? 2 : 6) != 0) {
			return;
		}
		Vec3 back = TankCrew.hullPoint(this, 0.0, 0.0, -1.0).subtract(position());
		for (int side = -1; side <= 1; side += 2) {
			Vec3 port = TankCrew.hullPoint(this, side * 0.45, 1.35, -3.68);
			level().addParticle(moving ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, port.x, port.y, port.z,
					back.x * 0.05, 0.03, back.z * 0.05);
		}
	}

	// --- The crew ------------------------------------------------------------------------------------------------

	/** The player in the tank (climbing or seated), or null. */
	public @Nullable Player crew() {
		return getFirstPassenger() instanceof Player player ? player : null;
	}

	/** The player in the gunner's seat, not climbing in or out. */
	public @Nullable Player seatedPlayer() {
		Player crew = crew();
		return crew != null && sequence() == SEQ_NONE ? crew : null;
	}

	@Nullable ServerPlayer seatedCrew() {
		return seatedPlayer() instanceof ServerPlayer player ? player : null;
	}

	/** Whether this player is in the seat, so their weapons and controls work. */
	public boolean isSeated(Player player) {
		return seatedPlayer() == player;
	}

	/** A sound only the player inside hears. */
	void crewSound(SoundEvent sound, float volume, float pitch) {
		if (crew() instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.NEUTRAL,
					player.getX(), player.getEyeY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
		}
	}

	void crewMessage(Component message) {
		if (crew() instanceof ServerPlayer player) {
			player.sendOverlayMessage(message);
		}
	}

	public int sequence() {
		return entityData.get(SEQUENCE) & 3;
	}

	private int sequenceSide() {
		return (entityData.get(SEQUENCE) & 4) != 0 ? -1 : 1;
	}

	/** How far through the crew's climb in or out, 0 to 1 (1 with nobody climbing). */
	public float sequenceProgress(float partialTicks) {
		int sequence = sequence();
		if (sequence == SEQ_NONE) {
			return 1.0f;
		}
		float ticks = level().getGameTime() - entityData.get(SEQUENCE_START) + partialTicks;
		return Mth.clamp(ticks / (sequence == SEQ_ENTERING ? TankCrew.ENTER_TICKS : TankCrew.EXIT_TICKS), 0.0f, 1.0f);
	}

	private void startSequence(int sequence, int side) {
		entityData.set(SEQUENCE, (byte) (sequence | (side < 0 ? 4 : 0)));
		entityData.set(SEQUENCE_START, level().getGameTime());
	}

	/** How open the commander's hatch is, 0 to 1. */
	public float hatchOpen(float partialTicks) {
		boolean collector = getFirstPassenger() != null && !(getFirstPassenger() instanceof Player);
		return TankCrew.hatchOpen(sequence(), sequenceProgress(partialTicks), collector);
	}

	/** Use: climb in, if nobody's in it and it isn't the bank's. Locks the player in place for the climb. */
	private void board(ServerPlayer player) {
		int side = TankCrew.sideOf(this, player.position());
		player.stopUsingItem();
		if (!player.startRiding(this, true, true)) {
			return;
		}
		throttle = 0.0f;
		exitHold = 0;
		// A new gunner starts without a range: the last one's lase doesn't carry over.
		gunnery.lase(0.0);
		startSequence(SEQ_ENTERING, side);
		player.setYRot(turretYaw);
		playSound(SoundEvents.IRON_GOLEM_STEP, 0.8f, 0.6f);
	}

	/** Steps the climb in or out along, with its clanks, and climbs the gunner out when they've held sneak. */
	private void tickCrew(ServerLevel level) {
		Player crew = crew();
		int sequence = sequence();
		if (crew == null) {
			if (sequence != SEQ_NONE) {
				entityData.set(SEQUENCE, (byte) SEQ_NONE);
			}
			exitHold = 0;
			return;
		}
		int ticks = (int) (level.getGameTime() - entityData.get(SEQUENCE_START));
		Vec3 hatch = TankCrew.turretPoint(this, 1.0f, 11.0 / 16.0, 0.9, -0.5);
		if (sequence == SEQ_ENTERING) {
			crew.resetFallDistance();
			if (ticks == 4 || ticks == 9 || ticks == 18) {
				playSound(SoundEvents.IRON_GOLEM_STEP, 0.7f, 0.7f + ticks * 0.01f);
			}
			if (ticks == 21) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Tanks.HATCH_OPEN_SOUND, SoundSource.NEUTRAL, 1.0f, 0.9f);
			}
			if (ticks == 33) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0f, 0.7f);
			}
			if (ticks == 38) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Tanks.HATCH_CLOSE_SOUND, SoundSource.NEUTRAL, 1.0f, 0.9f);
			}
			if (ticks >= TankCrew.ENTER_TICKS) {
				entityData.set(SEQUENCE, (byte) SEQ_NONE);
				gunnery.syncAll();
				if (crew instanceof ServerPlayer player) {
					player.sendOverlayMessage(Component.translatable("hud.mymod.tank.crewed").withStyle(ChatFormatting.GREEN));
				}
			}
			return;
		}
		if (sequence == SEQ_EXITING) {
			crew.resetFallDistance();
			if (ticks == 2) {
				level.playSound(null, hatch.x, hatch.y, hatch.z, Tanks.HATCH_OPEN_SOUND, SoundSource.NEUTRAL, 1.0f, 0.9f);
			}
			if (ticks == 16 || ticks == 25) {
				playSound(SoundEvents.IRON_GOLEM_STEP, 0.7f, 0.8f);
			}
			if (ticks >= TankCrew.EXIT_TICKS) {
				crew.stopRiding();
				entityData.set(SEQUENCE, (byte) SEQ_NONE);
				level.playSound(null, hatch.x, hatch.y, hatch.z, Tanks.HATCH_CLOSE_SOUND, SoundSource.NEUTRAL, 0.8f, 1.0f);
			}
			return;
		}
		// Seated: holding sneak climbs out.
		boolean sneaking = crew instanceof ServerPlayer player && player.getLastClientInput().shift();
		exitHold = sneaking ? exitHold + 1 : 0;
		if (exitHold >= TankCrew.EXIT_HOLD_TICKS) {
			exitHold = 0;
			gunnery.setCoaxTrigger(false);
			startSequence(SEQ_EXITING, TankCrew.sideOf(this, hatch));
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		Vec3 at = passenger instanceof Player
				? TankCrew.crewPosition(this, passenger, sequence(), sequenceProgress(0.0f), sequenceSide(), 1.0f)
				: TankCrew.inHatch(this, 1.0f);
		return at.subtract(position());
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	/** Climbing out ends on the ground beside the hull, on the hatch's side if there's room. */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		int side = sequenceSide();
		List<Vec3> spots = List.of(TankCrew.hullPoint(this, side * 2.35, 0.0, -0.6), TankCrew.hullPoint(this, -side * 2.35, 0.0, -0.6),
				TankCrew.hullPoint(this, 0.0, 0.0, -4.6), TankCrew.hullPoint(this, 0.0, 0.0, 4.8));
		for (Vec3 spot : spots) {
			for (double dy : new double[] {0.0, 1.0, -1.0}) {
				Vec3 at = spot.add(0.0, dy, 0.0);
				if (DismountHelper.canDismountTo(level(), passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(at))) {
					return at;
				}
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	// --- Driving ---------------------------------------------------------------------------------------------

	/** A player in it drives; a collector crew only shoots, and the tank drives itself. */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return crew();
	}

	@Override
	protected void tickRidden(Player driver, Vec3 input) {
		super.tickRidden(driver, input);
		if (!isLocalInstanceAuthoritative()) {
			return;
		}
		boolean seated = isSeated(driver);
		float forward = seated ? driver.zza : 0.0f;
		float turn = seated ? driver.xxa : 0.0f;
		float target = forward > 0.0f ? 1.0f : forward < 0.0f ? -REVERSE : 0.0f;
		boolean braking = target == 0.0f || throttle != 0.0f && Math.signum(target) != Math.signum(throttle);
		float rate = braking ? BRAKING : ACCELERATION * (0.5f + 0.5f * partCondition(TankPart.ENGINE));
		throttle = throttle < target ? Math.min(target, throttle + rate) : Math.max(target, throttle - rate);
		float wantTurn = -turn * TURN_SPEED * (0.4f + 0.6f * partCondition(TankPart.TRACKS));
		turnRate += Mth.clamp(wantTurn - turnRate, -TURN_ACCELERATION, TURN_ACCELERATION);
		if (Math.abs(turnRate) > 1.0e-3f) {
			float yaw = getYRot() + turnRate;
			setYRot(yaw);
			yBodyRot = yaw;
		}
	}

	/** Forward or back as the throttle says; A/D turn the hull instead of strafing. */
	@Override
	protected Vec3 getRiddenInput(Player driver, Vec3 selfInput) {
		return Math.abs(throttle) < 0.005f ? Vec3.ZERO : new Vec3(0.0, 0.0, Math.signum(throttle));
	}

	@Override
	protected float getRiddenSpeed(Player driver) {
		return Math.abs(throttle) * MAX_SPEED * speedFactor();
	}

	/** How much of its top speed damage leaves it. */
	float speedFactor() {
		return (0.35f + 0.65f * partCondition(TankPart.TRACKS)) * (0.5f + 0.5f * partCondition(TankPart.ENGINE));
	}

	/** On the driver's client: the throttle, -{@value #REVERSE} (full reverse) to 1. */
	public float throttle() {
		return throttle;
	}

	/** Stops short of a wall the nose would otherwise drive into (the box only covers the middle of the hull). */
	@Override
	public void travel(Vec3 input) {
		if (input.z != 0.0 && onGround() && bumperBlocked(input.z > 0.0)) {
			input = Vec3.ZERO;
			Vec3 motion = getDeltaMovement();
			setDeltaMovement(0.0, motion.y, 0.0);
			throttle = 0.0f;
		}
		super.travel(input);
	}

	/**
	 * Whether there's something solid it can't climb under the hull's nose (or tail) beyond its box: two blocks
	 * high within a block of the box, or three blocks high further out. Weak blocks don't count: they get crushed.
	 */
	private boolean bumperBlocked(boolean forwards) {
		double half = getBbWidth() / 2.0;
		double sign = forwards ? 1.0 : -1.0;
		for (double reach : new double[] {0.4, 0.9, 1.6, 2.2}) {
			int height = reach < 1.0 ? 1 : 2;
			for (double right : new double[] {-1.4, -0.5, 0.5, 1.4}) {
				Vec3 p = TankCrew.hullPoint(this, right, height + 0.5, sign * (half + reach));
				BlockPos pos = BlockPos.containing(p);
				BlockState state = level().getBlockState(pos);
				if (!state.getCollisionShape(level(), pos).isEmpty() && !crushable(state)) {
					return true;
				}
			}
		}
		return false;
	}

	// --- Crushing -------------------------------------------------------------------------------------------

	/** Leaves, fences and gates, glass panes, plants and crops, carpets, snow layers, cobwebs and flower pots. */
	public static boolean crushable(BlockState state) {
		if (state.isAir() || state.hasBlockEntity()) {
			return false;
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.FLOWER_POTS)) {
			return true;
		}
		if (state.is(BlockTags.REPLACEABLE) && state.getFluidState().isEmpty()) {
			return true;
		}
		if (state.getBlock() instanceof IronBarsBlock && state.getSoundType() == SoundType.GLASS) {
			return true;
		}
		return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB) || state.is(Blocks.SNOW) || state.is(Blocks.BROWN_MUSHROOM)
				|| state.is(Blocks.RED_MUSHROOM);
	}

	/** Breaks weak blocks under and ahead of the hull, and pushes (and at speed, runs over) small mobs. */
	private void tickTerrain(ServerLevel level) {
		Vec3 moved = position().subtract(xo, yo, zo);
		double speed = moved.horizontalDistance();
		int intent = 0;
		if (crew() instanceof ServerPlayer driver && isSeated(driver)) {
			intent = driver.getLastClientInput().forward() ? 1 : driver.getLastClientInput().backward() ? -1 : 0;
		} else if (zza != 0.0f) {
			intent = zza > 0.0f ? 1 : -1;
		}
		if (speed < 0.02 && intent == 0) {
			return;
		}
		if (tickCount % 2 == 0) {
			crushBlocks(level, intent);
		}
		crushMobs(level, moved, speed);
	}

	private void crushBlocks(ServerLevel level, int intent) {
		Player driver = crew();
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		if (driver == null && !griefing || driver != null && !driver.mayBuild()) {
			return;
		}
		double front = 4.0 + (intent > 0 ? 0.8 : 0.0);
		double back = -3.9 - (intent < 0 ? 0.8 : 0.0);
		Set<BlockPos> checked = new HashSet<>();
		for (double forward = back; forward <= front; forward += 0.5) {
			for (double right = -1.75; right <= 1.75; right += 0.5) {
				for (double up = 0.2; up < 2.8; up += 1.0) {
					BlockPos pos = BlockPos.containing(TankCrew.hullPoint(this, right, up, forward));
					if (!checked.add(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (crushable(state) && (driver == null || level.mayInteract(driver, pos))) {
						level.destroyBlock(pos, true, this);
					}
				}
			}
		}
	}

	private void crushMobs(ServerLevel level, Vec3 moved, double speed) {
		AABB area = getBoundingBox().inflate(2.6, 0.0, 2.6);
		Player driver = crew();
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != this && !hasPassenger(e)
				&& !(e instanceof Tank) && e.getBbWidth() < 2.0f && !e.isSpectator())) {
			if (debtor != null && Collectors.isCollector(mob)) {
				continue;
			}
			Vec3 offset = mob.position().subtract(position());
			double forward = offset.x * fx + offset.z * fz;
			double right = -offset.x * fz + offset.z * fx;
			double half = mob.getBbWidth() / 2.0;
			if (Math.abs(forward) > 4.0 + half || Math.abs(right) > 1.8 + half || mob.getY() > getY() + 2.5) {
				continue;
			}
			// Shoved out sideways and along with the hull.
			Vec3 sideways = new Vec3(-fz, 0.0, fx).scale(Math.signum(right) * 0.35);
			Vec3 push = moved.normalize().scale(0.5).add(sideways);
			mob.push(push.x, 0.15, push.z);
			if (speed > CRUSH_SPEED) {
				Entity cause = driver != null ? driver : this;
				DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
						.getOrThrow(DamageTypes.MOB_ATTACK), this, cause);
				mob.hurtServer(level, source, (float) (4.0 + speed * 60.0));
			}
		}
	}

	// --- The gun ---------------------------------------------------------------------------------------------

	public float turretYaw(float partialTicks) {
		return Mth.rotLerp(partialTicks, turretYawO, turretYaw);
	}

	public float gunPitch(float partialTicks) {
		return Mth.lerp(partialTicks, gunPitchO, gunPitch);
	}

	/** Which way the gun points. */
	public Vec3 gunDirection(float partialTicks) {
		return Vec3.directionFromRotation(gunPitch(partialTicks), turretYaw(partialTicks));
	}

	/** The trunnions, where the gun pivots in the turret. */
	public Vec3 trunnion(float partialTicks) {
		return TankCrew.turretPoint(this, partialTicks, 0.0, 5.0 / 16.0, 36.0 / 16.0);
	}

	/** The end of the barrel. */
	public Vec3 muzzle(float partialTicks) {
		return trunnion(partialTicks).add(gunDirection(partialTicks).scale(75.0 / 16.0));
	}

	/** The coaxial machine gun's muzzle, beside the main gun in the mantlet. */
	public Vec3 coaxMuzzle(float partialTicks) {
		return TankCrew.turretPoint(this, partialTicks, 5.0 / 16.0, 5.0 / 16.0, 38.5 / 16.0);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == FIRE_EVENT) {
			recoil = RECOIL_TICKS;
			// The whole tank rocks back on its suspension.
			if (isLocalInstanceAuthoritative()) {
				Vec3 kick = gunDirection(1.0f).multiply(1.0, 0.0, 1.0).normalize().scale(-0.12);
				setDeltaMovement(getDeltaMovement().add(kick));
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** How far back the barrel (and hull) has kicked from the last shot, 0 to 1. */
	public float recoil(float partialTicks) {
		return recoil <= 0 ? 0.0f : Mth.clamp((recoil - partialTicks) / RECOIL_TICKS, 0.0f, 1.0f);
	}

	/** Blocks of tread travel, for the animation. */
	public float trackTravel(boolean left, float partialTicks) {
		return left ? Mth.lerp(partialTicks, trackLeftO, trackLeft) : Mth.lerp(partialTicks, trackRightO, trackRight);
	}

	// --- Readouts ------------------------------------------------------------------------------------------------

	public int breechState() {
		return entityData.get(BREECH) & 3;
	}

	public ShellType breechRound() {
		return ShellType.byOrdinal(entityData.get(BREECH) >> 2 & 3);
	}

	public ShellType nextRound() {
		return ShellType.byOrdinal(entityData.get(BREECH) >> 4 & 3);
	}

	/** The round the fire control lays the gun for: the one in the breech, or the next one if it's empty. */
	public ShellType laidFor() {
		return breechState() == TankGunnery.BREECH_EMPTY ? nextRound() : breechRound();
	}

	/** How far through loading, 0 to 1. */
	public float loadProgress() {
		int packed = entityData.get(LOAD);
		int left = packed & 0xFFF;
		int total = Math.max(1, packed >> 12 & 0xFFF);
		return 1.0f - left / (float) total;
	}

	public int rackCount(ShellType type) {
		int packed = entityData.get(RACK);
		return type == ShellType.APFSDS ? packed & 1023 : type == ShellType.HE ? packed >> 10 & 1023 : 0;
	}

	public int rackGunpowder() {
		return entityData.get(RACK) >> 20 & 1023;
	}

	public int smokeSalvos() {
		return entityData.get(SMOKE);
	}

	public float coaxHeat() {
		return (entityData.get(COAX) & 0xFFFF) / 1000.0f;
	}

	public boolean coaxJammed() {
		return (entityData.get(COAX) & 1 << 16) != 0;
	}

	public float partCondition(TankPart part) {
		return part.condition(entityData.get(PARTS));
	}

	// --- Using it -------------------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (debtor != null) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("entity.mymod.tank.bank_property").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (hasPassenger(player)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		int ingots = held.is(Items.IRON_INGOT) ? 1 : held.is(Items.IRON_BLOCK) ? 9 : 0;
		if (ingots > 0 && needsRepair()) {
			if (!level().isClientSide()) {
				repair(ingots);
				held.consume(1, player);
				playSound(SoundEvents.ANVIL_USE, 0.6f, 1.2f);
			}
			return InteractionResult.SUCCESS;
		}
		if (TankRack.accepts(held)) {
			if (!level().isClientSide()) {
				ItemStack left = gunnery.rack().addItem(held.copy());
				if (left.getCount() == held.getCount()) {
					player.sendOverlayMessage(Component.translatable("entity.mymod.tank.rack_full").withStyle(ChatFormatting.RED));
				} else {
					player.sendOverlayMessage(Component.translatable("entity.mymod.tank.stowed", held.getCount() - left.getCount(),
							held.getHoverName()).withStyle(ChatFormatting.GRAY));
					held.setCount(left.getCount());
					playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 0.8f);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (player instanceof ServerPlayer serverPlayer) {
				openRack(serverPlayer);
			}
			return InteractionResult.SUCCESS;
		}
		if (isVehicle()) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("entity.mymod.tank.crewed_already").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (player.isPassenger() || PowerArmor.isWearing(player)) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("entity.mymod.tank.cant_board").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			board(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	void openRack(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TankRackMenu(id, inventory, gunnery.rack(), this),
				Component.translatable("container.mymod.tank_rack")));
	}

	private boolean needsRepair() {
		return getHealth() < getMaxHealth() || !TankPart.allSound(entityData.get(PARTS));
	}

	private void repair(int ingots) {
		heal(REPAIR_PER_INGOT * ingots);
		int parts = entityData.get(PARTS);
		for (TankPart part : TankPart.values()) {
			parts = part.with(parts, part.condition(parts) + PARTS_REPAIR_PER_INGOT * ingots);
		}
		entityData.set(PARTS, parts);
	}

	/** Sneak-punched with an empty hand: into an item, rack, smoke and all, if it's fully repaired. */
	private void packUp(ServerPlayer player) {
		if (needsRepair()) {
			player.sendOverlayMessage(Component.translatable("entity.mymod.tank.repair_first").withStyle(ChatFormatting.RED));
			return;
		}
		ItemStack item = new ItemStack(Tanks.TANK_ITEM);
		if (!gunnery.rack().isEmpty()) {
			item.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(gunnery.rack().getItems()));
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("Smoke", gunnery.smoke());
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		gunnery.rack().clearContent();
		player.getInventory().placeItemBackInInventory(item, Prediction.SERVER_ONLY);
		playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 0.8f);
		discard();
	}

	/** A freshly deployed tank takes what its item kept: the rack and its smoke. */
	void unpack(ItemStack item) {
		ItemContainerContents contents = item.get(DataComponents.CONTAINER);
		if (contents != null) {
			contents.copyInto(gunnery.rack().getItems());
			gunnery.rack().setChanged();
		}
		CustomData data = item.get(DataComponents.CUSTOM_DATA);
		gunnery.setSmoke(data == null ? TankGunnery.MAX_SMOKE : data.copyTag().getIntOr("Smoke", TankGunnery.MAX_SMOKE));
	}

	// --- Damage ---------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		// Collectors don't shell each other, and the crew can't hurt their own tank.
		if (debtor != null && Collectors.isFriendlyFire(source) || attacker != null && hasPassenger(attacker)) {
			return false;
		}
		if (debtor == null && source.getDirectEntity() instanceof ServerPlayer player && attacker == player && player.isSecondaryUseActive()
				&& player.getMainHandItem().isEmpty() && !isVehicle()) {
			packUp(player);
			return false;
		}
		float armoured = damage * armourFactor(source);
		if (armoured <= 0.0f) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, armoured);
		if (hurt && armoured >= PART_HIT_THRESHOLD) {
			damagePart(level, source, armoured);
		}
		return hurt;
	}

	/** How much of a hit gets through: all of a tank round or an explosion, a third of small arms, little of fire. */
	private static float armourFactor(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1.0f;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.CACTUS)) {
			return 0.0f;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return 1.25f;
		}
		if (source.getDirectEntity() instanceof TankShell) {
			return 1.0f;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return 0.15f;
		}
		if (source.is(ModDamageTypes.ARMOR_PIERCING_BULLET) || source.is(ModDamageTypes.PENETRATOR_BULLET)) {
			return 0.7f;
		}
		return 0.35f;
	}

	/** Wears down whichever part a heavy hit landed on, and tells the crew. */
	private void damagePart(ServerLevel level, DamageSource source, float damage) {
		Vec3 at = source.getSourcePosition();
		TankPart part;
		if (at == null) {
			part = TankPart.values()[random.nextInt(TankPart.values().length)];
		} else {
			Vec3 offset = at.subtract(position());
			float yaw = getYRot() * Mth.DEG_TO_RAD;
			double forward = -Mth.sin(yaw) * offset.x + Mth.cos(yaw) * offset.z;
			double right = -Mth.cos(yaw) * offset.x - Mth.sin(yaw) * offset.z;
			if (offset.y > 1.7) {
				Vec3 gun = gunDirection(1.0f);
				part = offset.subtract(trunnion(1.0f).subtract(position())).dot(gun) > -0.5 ? TankPart.GUN : TankPart.TURRET_RING;
			} else if (forward < -1.5) {
				part = TankPart.ENGINE;
			} else if (offset.y < 1.0 || Math.abs(right) > 1.4) {
				part = TankPart.TRACKS;
			} else {
				part = random.nextBoolean() ? TankPart.TRACKS : TankPart.ENGINE;
			}
		}
		int parts = entityData.get(PARTS);
		float before = part.condition(parts);
		float after = Math.max(0.0f, before - damage / 160.0f);
		entityData.set(PARTS, part.with(parts, after));
		if (before - after > 0.01f) {
			crewMessage(Component.translatable("hud.mymod.tank.part_hit", Component.translatable(part.nameKey()))
					.withStyle(after < 0.35f ? ChatFormatting.RED : ChatFormatting.GOLD));
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level) {
			// Brewed up: the crew bails out singed, and everything in the rack is thrown clear.
			for (Entity crew : List.copyOf(getPassengers())) {
				crew.stopRiding();
				crew.igniteForSeconds(4.0f);
				crew.hurtServer(level, damageSources().explosion(this, source.getEntity()), 6.0f);
			}
		}
		super.die(source);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack stack : gunnery.rack().removeAllItems()) {
			spawnAtLocation(level, stack, 1.0f);
		}
	}

	/** Scrap from a player's tank; the bank's are written off. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return debtor == null && super.shouldDropLoot(level);
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (level() instanceof ServerLevel level) {
			if (deathTime == 1) {
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 2, 1.0, 0.3, 1.0, 0.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 60, 1.5, 0.8, 1.5, 0.05);
				level.playSound(null, getX(), getY(), getZ(), Tanks.CANNON_SOUND, SoundSource.NEUTRAL, 6.0f, 0.6f);
			}
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.8, getZ(), 4, 1.0, 0.4, 1.0, 0.02);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 2.2, getZ(), 2, 0.8, 0.2, 0.8, 0.02);
		}
	}

	// --- Bits and pieces -------------------------------------------------------------------------------------

	/** Other things bump into it (and can stand on it); its crew doesn't. */
	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return isAlive() && (other == null || !hasPassenger(other) && other.getVehicle() != this);
	}

	/** Sealed hull: it can drive along the bottom of a river. */
	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		Collectors.save(output, debtor);
		output.putFloat("TurretYaw", turretYaw);
		output.putFloat("GunPitch", gunPitch);
		output.putInt("Parts", entityData.get(PARTS));
		gunnery.save(output);
		ContainerHelper.saveAllItems(output.child("Rack"), gunnery.rack().getItems());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		debtor = Collectors.load(input);
		turretYaw = input.getFloatOr("TurretYaw", getYRot());
		gunPitch = input.getFloatOr("GunPitch", 0.0f);
		turretYawO = turretYaw;
		gunPitchO = gunPitch;
		entityData.set(PARTS, input.getIntOr("Parts", TankPart.ALL_SOUND));
		ContainerHelper.loadAllItems(input.childOrEmpty("Rack"), gunnery.rack().getItems());
		gunnery.load(input);
	}

	/** Points the turret along the hull, for a tank just put down. */
	public void alignTurret() {
		turretYaw = getYRot();
		turretYawO = turretYaw;
		entityData.set(TURRET_YAW, turretYaw);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 0.5f, 0.5f + random.nextFloat() * 0.1f);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected float getSoundVolume() {
		return 0.8f;
	}

	@Override
	public float getVoicePitch() {
		return 0.5f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GENERIC_EXPLODE.value();
	}
}
