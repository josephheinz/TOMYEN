package com.jheinz.tomyen.bathtub;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A clawfoot bathtub with an outboard motor clamped over its back rim. It's a vanilla boat underneath (the rider's client
 * steers it, the paddle packets tell the server what they're pressing), with three things on top:
 * <ul>
 * <li><b>The motor.</b> With fuel in the tank, holding forward afloat opens the throttle over a second and a half; at
 * full throttle the propeller adds {@link #THRUST} a tick to the paddle stroke, for a top speed of about 0.85 blocks a
 * tick (17 m/s) against a rowed boat's 0.4. It burns {@link #BURN} units a tick at full throttle (a sip idling): a
 * Kerosene Canister is {@value #KEROSENE_UNITS} units, two and a half minutes flat out, and the tank holds four. Fish
 * oil works too, a quarter canister a bottle. Out of fuel it rows like any boat, oars and all.</li>
 * <li><b>Handling.</b> Afloat it only loses {@code 1 - }{@link #SIDE_KEEP} of its sideways slide a tick where vanilla
 * loses a tenth of everything, so it drifts through its turns. At speed the bow lifts and it throws a wake: spray off
 * the bow, a churn behind the stern, bubbles off the propeller.</li>
 * <li><b>The claw feet.</b> Driven into a bank up to {@value #HOP_HEIGHT} blocks high, it hops out of the water; on
 * land it waddles at about a block a second ({@link #LAND_KEEP}) and steps up whole blocks, the motor tilted up out of
 * the way.</li>
 * </ul>
 * Over {@value #RAM_SPEED} blocks a tick it rams what it hits, and the rider can squeeze the rubber duck ({@link #honk}).
 * Two ride: the driver at the back by the tiller, a passenger at the faucet end. Broken, it drops as the item with its
 * fuel kept.
 */
public class BathtubBoat extends AbstractBoat {
	// --- Handling -----------------------------------------------------------------------------------------------------
	/** The propeller's push a tick at full throttle, on top of the paddle stroke vanilla adds (0.04). */
	public static final float THRUST = 0.05f;
	/** Its push backing off, the motor running. */
	public static final float REVERSE_THRUST = 0.012f;
	/** How much of its speed vanilla keeps a tick afloat. */
	private static final double WATER_KEEP = 0.9;
	/** How much of its sideways slide it keeps a tick afloat. */
	public static final double SIDE_KEEP = 0.94;
	/** On land its speed is cut to this before vanilla's ground friction, leaving about 0.05 blocks a tick. */
	public static final double LAND_KEEP = 0.35;
	public static final float THROTTLE_UP = 1.0f / 30.0f;
	public static final float THROTTLE_DOWN = 1.0f / 12.0f;
	/** How far up out of the water it hops, from its keel: a bank a block above the water is about 1.5 up. */
	public static final double HOP_HEIGHT = 1.65;
	/** It steps up this much on land. */
	private static final float LAND_STEP = 1.0f;
	/** Speed (blocks a tick) above which it rams what it hits: 9 m/s. */
	public static final double RAM_SPEED = 0.45;
	private static final float RAM_DAMAGE = 3.0f;
	private static final int RAM_COOLDOWN = 15;

	// --- Fuel -----------------------------------------------------------------------------------------------------------
	public static final int TANK = 12000;
	public static final int KEROSENE_UNITS = 3000;
	public static final int FISH_OIL_UNITS = 750;
	/** Units a tick at full throttle. */
	public static final float BURN = 1.0f;
	/** Units a tick idling. */
	public static final float IDLE_BURN = 0.05f;

	// --- Seats ----------------------------------------------------------------------------------------------------------
	/**
	 * Forward of the middle, in blocks: the driver at the back by the tiller up on the bath caddy, the passenger down in
	 * the tub towards the faucet (so the driver sees over their head).
	 */
	public static final float DRIVER_SEAT = -0.6875f;
	public static final float FRONT_SEAT = 0.26f;
	/** A seated rider's hips a pixel above the tub's floor, standing on land. */
	private static final double SEAT_HEIGHT = 9.0 / 16.0 - 0.15;
	/** The driver sits on the caddy across the rim, this much higher. */
	private static final double DRIVER_RAISE = 7.0 / 16.0;
	/** And the passenger sits down in the tub (their legs sunk in the floor, as if in the bathwater), this much lower. */
	private static final double FRONT_SINK = 2.0 / 16.0;
	/** Afloat it's drawn (and its riders sit) this much lower, the waterline just under the floor. */
	public static final double SINK = 2.0 / 16.0;

	private static final byte HONK_EVENT = 90;
	private static final int HONK_COOLDOWN = 6;

	private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(BathtubBoat.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(BathtubBoat.class, EntityDataSerializers.FLOAT);

	/** The driver's keys, as LocalPlayer hands them to every boat each tick (client). */
	private boolean inputUp;
	private boolean inputDown;
	/** The driver's own throttle, which their client drives the boat with (the server keeps its own for the fuel). */
	private float localThrottle;

	// Server.
	private float burnRemainder;
	private boolean wasRunning;
	private @Nullable Vec3 lastPos;
	private final Map<Integer, Long> rammed = new HashMap<>();
	private int honkCooldown;

	// Client: eased values for drawing it, each with last tick's.
	private double speed;
	private float lift;
	private float liftO;
	private float sink;
	private float sinkO;
	private float tilt;
	private float tiltO;
	private float steer;
	private float steerO;
	private float prop;
	private float propO;
	private float propSpeed;
	private float waddle;
	private float waddleO;
	private float waddleAmount;
	private float duck;
	private float duckO;

	public BathtubBoat(EntityType<? extends BathtubBoat> type, Level level) {
		super(type, level, () -> Bathtubs.BATHTUB_BOAT);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_FUEL, 0);
		entityData.define(DATA_THROTTLE, 0.0f);
	}

	// --- Fuel ----------------------------------------------------------------------------------------------------------

	public int fuel() {
		return entityData.get(DATA_FUEL);
	}

	public void setFuel(int fuel) {
		entityData.set(DATA_FUEL, Mth.clamp(fuel, 0, TANK));
	}

	/** The throttle, 0 to 1: the driver's client has its own, everyone else sees the server's. */
	public float throttle() {
		return level().isClientSide() && isLocalInstanceAuthoritative() ? localThrottle : entityData.get(DATA_THROTTLE);
	}

	private static boolean free(@Nullable LivingEntity driver) {
		return driver instanceof Player player && player.hasInfiniteMaterials();
	}

	/** Whether the motor's turning: someone at the tiller and fuel in the tank (creative drivers need none). */
	public boolean engineRunning() {
		LivingEntity driver = getControllingPassenger();
		return driver instanceof Player && (fuel() > 0 || free(driver));
	}

	/** Floating on the water (not sunk, not ashore). */
	public boolean afloat() {
		return isInWater() && !isUnderWater();
	}

	/**
	 * Pours the fuel the player is holding into the tank, if there's room. The empty canister or bottle goes back to them.
	 * Returns whether it went in (on the client, whether it will).
	 */
	public boolean refuel(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		int units = BathtubBoatItem.fuelUnits(held);
		if (units <= 0) {
			return false;
		}
		if (fuel() >= TANK) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("entity.tomyen.bathtub_boat.tank_full"));
			}
			return false;
		}
		if (!level().isClientSide()) {
			setFuel(fuel() + units);
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, BathtubBoatItem.emptied(held)));
			playSound(SoundEvents.BUCKET_FILL, 0.8f, 1.2f);
			player.sendOverlayMessage(Component.translatable("entity.tomyen.bathtub_boat.fuel", Math.round(100.0f * fuel() / TANK))
					.withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (BathtubBoatItem.fuelUnits(player.getItemInHand(hand)) > 0 && fuel() < TANK && !player.isSecondaryUseActive()) {
			return refuel(player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return super.interact(player, hand, location);
	}

	// --- Riding ---------------------------------------------------------------------------------------------------------

	@Override
	protected double rideHeight(EntityDimensions dimensions) {
		float sunk = level().isClientSide() ? sink : afloat() ? 1.0f : 0.0f;
		return SEAT_HEIGHT - SINK * sunk;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		boolean driver = getPassengers().indexOf(passenger) <= 0;
		return new Vec3(0.0, rideHeight(dimensions) + (driver ? DRIVER_RAISE : -FRONT_SINK), driver ? DRIVER_SEAT : FRONT_SEAT)
				.yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public void setInput(boolean left, boolean right, boolean up, boolean down) {
		super.setInput(left, right, up, down);
		inputUp = up;
		inputDown = down;
	}

	/** Afloat it can't step (it hops, see hopAshore); on land the claw feet climb a block at a time. */
	@Override
	public float maxUpStep() {
		return isInWater() ? 0.0f : LAND_STEP;
	}

	// --- Ticking ---------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		boolean wasAfloat = afloat();
		if (isLocalInstanceAuthoritative()) {
			drive(wasAfloat);
		}
		super.tick();
		if (level() instanceof ServerLevel serverLevel) {
			serverTick(serverLevel);
		} else {
			if (isLocalInstanceAuthoritative() && wasAfloat && horizontalCollision && inputUp) {
				hopAshore();
			}
			animate();
		}
	}

	private Vec3 forward() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/**
	 * Before vanilla's float and paddle step (wherever the boat is simulated): keep more of the sideways slide afloat,
	 * slow the waddle ashore, and on the driver's client open the throttle and push.
	 */
	private void drive(boolean afloat) {
		Vec3 v = getDeltaMovement();
		Vec3 fwd = forward();
		if (afloat) {
			double along = v.x * fwd.x + v.z * fwd.z;
			double sideX = v.x - along * fwd.x;
			double sideZ = v.z - along * fwd.z;
			// Vanilla then takes a tenth off all of it: this leaves the slide SIDE_KEEP of itself.
			double keep = SIDE_KEEP / WATER_KEEP;
			v = new Vec3(along * fwd.x + sideX * keep, v.y, along * fwd.z + sideZ * keep);
		} else if (onGround()) {
			v = new Vec3(v.x * LAND_KEEP, v.y, v.z * LAND_KEEP);
		}
		if (level().isClientSide() && getControllingPassenger() instanceof Player) {
			boolean motoring = afloat && engineRunning();
			float target = motoring && inputUp ? 1.0f : 0.0f;
			localThrottle = Mth.approach(localThrottle, target, target > localThrottle ? THROTTLE_UP : THROTTLE_DOWN);
			double push = THRUST * localThrottle - (motoring && inputDown ? REVERSE_THRUST : 0.0);
			v = v.add(fwd.x * push, 0.0, fwd.z * push);
		} else {
			localThrottle = 0.0f;
		}
		setDeltaMovement(v);
	}

	/** Run into a bank, it climbs out: up onto the first ledge (within HOP_HEIGHT) with room for it. */
	private void hopAshore() {
		Vec3 fwd = forward();
		for (double up = 0.25; up <= HOP_HEIGHT + 1.0e-6; up += 0.15) {
			AABB moved = getBoundingBox().move(fwd.x * 0.35, up, fwd.z * 0.35);
			if (level().noCollision(this, moved)) {
				setPos(getX(), getY() + up, getZ());
				setDeltaMovement(fwd.x * 0.12, 0.05, fwd.z * 0.12);
				level().playLocalSound(getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, getSoundSource(), 0.5f, 1.3f, false);
				return;
			}
		}
	}

	private void serverTick(ServerLevel level) {
		LivingEntity driver = getControllingPassenger();
		boolean driven = driver instanceof Player;
		boolean running = engineRunning();
		if (running && !wasRunning) {
			// The pull-cord.
			playSound(Bathtubs.MOTOR_START_SOUND, 0.9f, 0.85f + random.nextFloat() * 0.1f);
		} else if (!running && wasRunning && driven) {
			playSound(SoundEvents.FIRE_EXTINGUISH, 0.4f, 1.6f);
			if (driver instanceof Player player) {
				player.sendOverlayMessage(Component.translatable("entity.tomyen.bathtub_boat.out_of_fuel").withStyle(ChatFormatting.RED));
			}
		}
		wasRunning = running;
		// Both paddles going means they're holding forward.
		boolean forward = driven && getPaddleState(PADDLE_LEFT) && getPaddleState(PADDLE_RIGHT);
		float throttle = entityData.get(DATA_THROTTLE);
		float target = running && forward && afloat() ? 1.0f : 0.0f;
		throttle = Mth.approach(throttle, target, target > throttle ? THROTTLE_UP : THROTTLE_DOWN);
		entityData.set(DATA_THROTTLE, throttle);
		if (running && !free(driver)) {
			burnRemainder += throttle * BURN + IDLE_BURN;
			int whole = (int) burnRemainder;
			if (whole > 0) {
				burnRemainder -= whole;
				setFuel(fuel() - whole);
			}
		}
		Vec3 pos = position();
		if (lastPos != null && driver instanceof Player player) {
			Vec3 step = pos.subtract(lastPos);
			double speed = step.horizontalDistance();
			// (A jump of more than a few blocks is a teleport, not a ram.)
			if (speed > RAM_SPEED && speed < 3.0) {
				ram(level, player, step, speed);
			}
		}
		lastPos = pos;
		if (honkCooldown > 0) {
			honkCooldown--;
		}
	}

	/** Knocks whatever's in front flying, and hurts it a little: more the faster it's going. */
	private void ram(ServerLevel level, Player driver, Vec3 step, double speed) {
		Vec3 dir = new Vec3(step.x, 0.0, step.z).normalize();
		AABB zone = getBoundingBox().inflate(0.2, 0.0, 0.2).move(dir.scale(0.45)).expandTowards(0.0, 0.6, 0.0);
		long now = level.getGameTime();
		boolean hit = false;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, zone,
				e -> e.isAlive() && !e.isSpectator() && e != driver && !e.isPassenger())) {
			Long last = rammed.get(target.getId());
			if (last != null && now - last < RAM_COOLDOWN) {
				continue;
			}
			rammed.put(target.getId(), now);
			DamageSource source = damageSources().mobAttack(driver);
			target.hurtServer(level, source, RAM_DAMAGE + (float) (speed - RAM_SPEED) * 8.0f);
			target.knockback(0.8 + speed * 1.5, -dir.x, -dir.z, source, 0.0f);
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.15 + speed * 0.2, 0.0));
			target.needsSync = true;
			hit = true;
		}
		if (hit) {
			playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
			squeak();
		}
		if (rammed.size() > 16) {
			rammed.values().removeIf(at -> now - at >= RAM_COOLDOWN);
		}
	}

	/** Squeezes the rubber duck (the rider's honk key). */
	public void honk() {
		if (honkCooldown > 0) {
			return;
		}
		honkCooldown = HONK_COOLDOWN;
		squeak();
	}

	private void squeak() {
		playSound(Bathtubs.DUCK_SQUEAK, 1.0f, 0.9f + random.nextFloat() * 0.25f);
		level().broadcastEntityEvent(this, HONK_EVENT);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == HONK_EVENT) {
			duck = 1.0f;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected @Nullable SoundEvent getPaddleSound() {
		if (!isInWater()) {
			// Brass feet on the ground.
			return onGround() ? SoundEvents.COPPER_STEP : null;
		}
		return engineRunning() ? null : super.getPaddleSound();
	}

	// --- Client -----------------------------------------------------------------------------------------------------------

	private void animate() {
		double dx = getX() - xo;
		double dz = getZ() - zo;
		double moved = Math.sqrt(dx * dx + dz * dz);
		speed += (moved - speed) * 0.3;
		boolean afloat = afloat();
		boolean ashore = !isInWater();
		liftO = lift;
		lift = Mth.approach(lift, afloat ? (float) Mth.clamp((speed - 0.25) / 0.5, 0.0, 1.0) : 0.0f, 0.04f);
		sinkO = sink;
		sink = Mth.approach(sink, afloat || isUnderWater() ? 1.0f : 0.0f, 0.1f);
		tiltO = tilt;
		tilt = Mth.approach(tilt, ashore ? 1.0f : 0.0f, 0.08f);
		steerO = steer;
		float yawRate = Mth.wrapDegrees(getYRot() - yRotO);
		steer = Mth.approach(steer, Mth.clamp(-yawRate / 6.0f, -1.0f, 1.0f), 0.15f);
		propO = prop;
		boolean running = engineRunning();
		propSpeed = Mth.approach(propSpeed, running ? 0.45f + 1.6f * throttle() : 0.0f, 0.08f);
		prop += propSpeed;
		if (prop > Mth.TWO_PI * 64.0f) {
			prop -= Mth.TWO_PI * 64.0f;
			propO -= Mth.TWO_PI * 64.0f;
		}
		waddleO = waddle;
		boolean walking = ashore && onGround() && moved > 0.004;
		waddleAmount = Mth.approach(waddleAmount, walking ? 1.0f : 0.0f, 0.1f);
		if (walking) {
			waddle += (float) moved * 9.0f + 0.08f;
		}
		duckO = duck;
		duck = Math.max(0.0f, duck - 0.12f);
		if (afloat) {
			spray(running);
		}
	}

	/** Its wake: bubbles off the propeller, a churn off the stern quarters and spray off the bow, more the faster it goes. */
	private void spray(boolean running) {
		RandomSource r = random;
		Vec3 fwd = forward();
		Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
		double surface = getY() + 0.37;
		float throttle = throttle();
		if (running && throttle > 0.05f) {
			Vec3 prop = position().add(fwd.scale(-1.45));
			for (int i = 0; i < 1 + (int) (throttle * 3.0f); i++) {
				level().addParticle(ParticleTypes.BUBBLE, prop.x + r.nextGaussian() * 0.1, getY() + 0.05 + r.nextGaussian() * 0.08,
						prop.z + r.nextGaussian() * 0.1, -fwd.x * 0.3 * throttle, 0.02, -fwd.z * 0.3 * throttle);
			}
		}
		if (speed < 0.12) {
			return;
		}
		for (int side = -1; side <= 1; side += 2) {
			if (r.nextFloat() < speed * 1.6) {
				Vec3 p = position().add(fwd.scale(-1.1)).add(right.scale(side * 0.5));
				level().addParticle(ParticleTypes.FISHING, p.x, surface, p.z, right.x * side * 0.03, 0.0, right.z * side * 0.03);
			}
			int drops = (int) (speed * 4.0) + (r.nextFloat() < speed * 4.0 % 1.0 ? 1 : 0);
			for (int i = 0; i < drops; i++) {
				Vec3 p = position().add(fwd.scale(0.9 + r.nextFloat() * 0.2)).add(right.scale(side * (0.45 + r.nextFloat() * 0.1)));
				level().addParticle(ParticleTypes.SPLASH, p.x, surface + 0.05, p.z, right.x * side * 0.12 + fwd.x * speed * 0.5, 0.0,
						right.z * side * 0.12 + fwd.z * speed * 0.5);
			}
		}
	}

	public double speed() {
		return speed;
	}

	/** How far the bow's up at speed, 0 to 1. */
	public float lift(float partialTicks) {
		return Mth.lerp(partialTicks, liftO, lift);
	}

	/** How far down it sits in the water, 0 (ashore) to 1. */
	public float sink(float partialTicks) {
		return Mth.lerp(partialTicks, sinkO, sink);
	}

	/** How far the motor's tilted up out of the way ashore, 0 to 1. */
	public float tilt(float partialTicks) {
		return Mth.lerp(partialTicks, tiltO, tilt);
	}

	/** Which way it's turning, -1 (right) to 1 (left), for the tiller. */
	public float steer(float partialTicks) {
		return Mth.lerp(partialTicks, steerO, steer);
	}

	/** The propeller's turn, in radians. */
	public float propeller(float partialTicks) {
		return Mth.lerp(partialTicks, propO, prop);
	}

	/** The feet's step cycle, and how much they're stepping, 0 to 1. */
	public float waddle(float partialTicks) {
		return Mth.lerp(partialTicks, waddleO, waddle);
	}

	public float waddleAmount() {
		return waddleAmount;
	}

	/** The rubber duck squashed by a squeeze, 1 just squeezed. */
	public float duck(float partialTicks) {
		return Mth.lerp(partialTicks, duckO, duck);
	}

	// --- Saving and breaking --------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("fuel", fuel());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setFuel(input.getIntOr("fuel", 0));
	}

	/** Broken up, it drops as the item with its fuel still in it. */
	@Override
	protected void destroy(ServerLevel level, DamageSource source) {
		kill(level);
		if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			ItemStack stack = new ItemStack(Bathtubs.BATHTUB_BOAT);
			stack.set(DataComponents.CUSTOM_NAME, getCustomName());
			BathtubBoatItem.setFuel(stack, fuel());
			spawnAtLocation(level, stack);
		}
	}
}
