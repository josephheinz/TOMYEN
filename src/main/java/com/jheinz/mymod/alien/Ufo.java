package com.jheinz.mymod.alien;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.energy.EnergyBeam;
import com.jheinz.mymod.energy.EnergyWeaponItem;
import com.jheinz.mymod.energy.EnergyWeapons;
import com.jheinz.mymod.energy.RayGunItem;

/**
 * A flying saucer, {@value #WIDTH} blocks across: a domed hull ringed with lights that chase round in colours, a glowing
 * emitter underneath. It cruises {@value #MIN_ALTITUDE}-{@value #MAX_ALTITUDE} blocks over the ground round whoever
 * it came to see (see {@link UfoSightings}), humming, and runs through these states:
 * <ul>
 * <li><b>Roaming</b>: wandering waypoints within {@value #ROAM_RADIUS} blocks of them. Every few seconds it looks for a
 * cow, sheep or pig (the {@code mymod:abductable} tag) out under the open sky within {@value #SEARCH_RADIUS} blocks,
 * never a named or tamed one, or one on a lead or being ridden.
 * <li><b>Abducting</b>: it hovers {@value #BEAM_HEIGHT} blocks over the animal and lifts it in a {@link TractorBeam},
 * slowly, spinning, until it disappears into the hull. {@value #BEAM_BREAK_DAMAGE} damage to the saucer while it's
 * beaming makes it lose its grip (the animal floats down), and so does putting the animal on a lead. After two or three
 * animals it leaves.
 * <li><b>Landing</b> and <b>landed</b>: on some nights it comes down in the open instead (see
 * {@link UfoSightings#land}): legs out, ramp down, light spilling out, and {@value #MIN_CREW}-{@value #MAX_CREW}
 * {@link Alien aliens} wander out. It calls them back and takes off when its time's up, at dawn, when one of them is
 * hurt, or once they've carried someone off (see {@link Abductions}). Aliens that don't make it back in time are
 * beamed aboard.
 * <li><b>Leaving</b>: up and away from everyone at speed, and gone once it's well out of sight. It leaves after
 * {@value #VISIT_TICKS} ticks of roaming too, or once it's down to {@value #FLEE_HEALTH} of its health.
 * <li><b>Crashing</b>: shot down, it doesn't just die. It spirals down trailing smoke and flame and crash-lands in an
 * explosion (blocks are only broken if mobGriefing is on), leaving its loot (an Alien Reactor and Alien Alloy scrap)
 * and 0-2 dazed survivors.
 * </ul>
 * Hit by someone, it gets angry and zaps them every few seconds from its emitter ({@value #ZAP_DAMAGE} damage, within
 * {@value #ZAP_RANGE} blocks). It takes every weapon there is: arrows, bullets, rockets, tank shells, lasers.
 */
public class Ufo extends Mob {
	public static final float WIDTH = 5.5f;
	public static final float HEIGHT = 2.0f;
	/** How high its hull stands on its legs, landed. */
	public static final double LEG_HEIGHT = 1.25;
	static final int MIN_ALTITUDE = 20;
	static final int MAX_ALTITUDE = 40;
	static final int ROAM_RADIUS = 40;
	static final int SEARCH_RADIUS = 40;
	static final int BEAM_HEIGHT = 12;
	static final float BEAM_BREAK_DAMAGE = 16.0f;
	static final int MIN_CREW = 2;
	static final int MAX_CREW = 4;
	static final int VISIT_TICKS = 6000;
	static final float FLEE_HEALTH = 0.35f;
	static final float ZAP_DAMAGE = 6.0f;
	static final int ZAP_RANGE = 40;
	private static final double CRUISE_SPEED = 0.35;
	private static final double FAST_SPEED = 0.8;
	private static final int ANGER_TICKS = 400;
	/** Ticks the crew get to walk back aboard before the rest are beamed up. */
	private static final int RECALL_TICKS = 200;
	/** Ticks it stays down, at most. */
	private static final int MIN_STAY = 3600;
	private static final int MAX_STAY = 6000;
	public static final TagKey<EntityType<?>> ABDUCTABLE = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("abductable"));

	public static final int ROAMING = 0;
	public static final int ABDUCTING = 1;
	public static final int LANDING = 2;
	public static final int LANDED = 3;
	public static final int TAKING_OFF = 4;
	public static final int LEAVING = 5;
	public static final int CRASHING = 6;

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(Ufo.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_LEGS = SynchedEntityData.defineId(Ufo.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_DOOR = SynchedEntityData.defineId(Ufo.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BEAMING = SynchedEntityData.defineId(Ufo.class, EntityDataSerializers.BOOLEAN);

	private final int altitude;
	// Server side.
	private @Nullable UUID visiting;
	private Vec3 home = Vec3.ZERO;
	private @Nullable Vec3 waypoint;
	private int stateTicks;
	private int visitTicks;
	private int abductions;
	private final int maxAbductions;
	private int searchCooldown = 60;
	private @Nullable Animal prey;
	private @Nullable TractorBeam beam;
	private float beamDamage;
	private @Nullable LivingEntity attacker;
	private int angerTicks;
	private int zapCooldown;
	private @Nullable BlockPos landingSite;
	private int crewToSpawn;
	private int stayTicks;
	private boolean recalling;
	private int recallTicks;
	private @Nullable DamageSource crashSource;
	private boolean crashed;
	// Client side: legs and ramp out (0 to 1), and the hull's turn (degrees) and how fast it's turning.
	private float legs;
	private float legsO;
	private float door;
	private float doorO;
	private float spin;
	private float spinO;
	private float spinSpeed = 3.0f;

	public Ufo(EntityType<? extends Ufo> type, Level level) {
		super(type, level);
		xpReward = 40;
		altitude = MIN_ALTITUDE + random.nextInt(MAX_ALTITUDE - MIN_ALTITUDE + 1);
		maxAbductions = 2 + random.nextInt(2);
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 140.0)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, (byte) ROAMING);
		builder.define(DATA_LEGS, false);
		builder.define(DATA_DOOR, false);
		builder.define(DATA_BEAMING, false);
	}

	/** Spawned by an egg or a command, it visits whoever's nearest. */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		home = position();
		Player nearest = level.getNearestPlayer(this, 64.0);
		if (nearest != null) {
			visiting = nearest.getUUID();
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	/** Sent to visit this player (see {@link UfoSightings}). */
	void visit(Player player) {
		visiting = player.getUUID();
		home = position();
	}

	// ------------------------------------------------------------------------------------------------ state

	public int state() {
		return entityData.get(DATA_STATE);
	}

	private void setState(int state) {
		entityData.set(DATA_STATE, (byte) state);
		stateTicks = 0;
	}

	public boolean isLanded() {
		return state() == LANDED;
	}

	public boolean isCrashing() {
		return state() == CRASHING;
	}

	public boolean isBeaming() {
		return entityData.get(DATA_BEAMING);
	}

	/** Landed with its crew called back aboard. */
	public boolean isRecalling() {
		return recalling;
	}

	/** Where its crew come down the ramp and go back up it: just past the foot of the ramp. */
	public Vec3 rampFoot() {
		Vec3 facing = Vec3.directionFromRotation(0.0f, getYRot());
		return position().add(facing.scale(WIDTH * 0.5 + 0.6)).add(0.0, -LEG_HEIGHT, 0.0);
	}

	/** Its crew out and about. */
	public List<Alien> crew() {
		return level().getEntitiesOfClass(Alien.class, getBoundingBox().inflate(96.0), alien -> alien.ship() == this && alien.isAlive());
	}

	/** Comes down at {@code site} (the top of the ground there) with a crew. */
	public void land(BlockPos site) {
		landingSite = site;
		crewToSpawn = MIN_CREW + random.nextInt(MAX_CREW - MIN_CREW + 1);
		stayTicks = MIN_STAY + random.nextInt(MAX_STAY - MIN_STAY + 1);
		setYRot(random.nextFloat() * 360.0f);
		setState(LANDING);
	}

	/** One of its crew was hurt, or they've got what they came for: everyone back aboard, and off. */
	public void recall() {
		if (isLanded() && !recalling) {
			recalling = true;
			recallTicks = 0;
		}
	}

	/** One of its crew was hurt by this: they all come back, and it's after whoever did it. */
	void alarm(@Nullable LivingEntity by) {
		recall();
		if (by != null && !(by instanceof Alien) && by != this) {
			anger(by);
		}
	}

	private void anger(LivingEntity by) {
		if (by instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		attacker = by;
		angerTicks = ANGER_TICKS;
	}

	// ------------------------------------------------------------------------------------------------ moving

	@Override
	public void travel(Vec3 input) {
		if (isAlive()) {
			move(MoverType.SELF, getDeltaMovement());
		} else {
			super.travel(input);
		}
	}

	/** Eases its velocity toward {@code target} at up to {@code speed} blocks a tick, slowing as it arrives. */
	private void steer(Vec3 target, double speed) {
		Vec3 to = target.subtract(position());
		double distance = to.length();
		Vec3 wanted = distance < 1.0e-3 ? Vec3.ZERO : to.scale(Math.min(speed, distance * 0.08) / distance);
		setDeltaMovement(getDeltaMovement().lerp(wanted, 0.1));
	}

	/** The height it likes to cruise at over a point: well clear of whatever's highest, there or under it. */
	private double cruiseY(double x, double z) {
		int ground = Math.max(level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)),
				level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ()));
		return Math.min(level().getMaxY() - HEIGHT - 2, ground + altitude);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || !isAlive()) {
			return;
		}
		stateTicks++;
		zapCooldown = Math.max(0, zapCooldown - 1);
		if (state() == CRASHING) {
			crash(level);
			return;
		}
		if (angerTicks > 0) {
			angerTicks--;
			fightBack(level);
		}
		if (state() != LEAVING && state() != LANDED && state() != LANDING && getHealth() < getMaxHealth() * FLEE_HEALTH) {
			leave();
		}
		switch (state()) {
			case ROAMING -> roam(level);
			case ABDUCTING -> abduct(level);
			case LANDING -> descend(level);
			case LANDED -> stayLanded(level);
			case TAKING_OFF -> takeOff();
			default -> depart(level);
		}
	}

	/** Who it came to see, if they're still about. */
	private @Nullable Player visitor() {
		Player player = visiting == null ? null : level().getPlayerByUUID(visiting);
		return player != null && player.isAlive() && player.distanceToSqr(this) < 160.0 * 160.0 ? player : null;
	}

	private void roam(ServerLevel level) {
		Player visitor = visitor();
		if (++visitTicks > VISIT_TICKS || visiting != null && visitor == null) {
			leave();
			return;
		}
		Vec3 centre = visitor != null ? visitor.position() : home;
		if (waypoint == null || position().distanceToSqr(waypoint) < 16.0 || stateTicks % 300 == 0) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = random.nextDouble() * ROAM_RADIUS;
			double x = centre.x + Math.cos(angle) * distance;
			double z = centre.z + Math.sin(angle) * distance;
			waypoint = new Vec3(x, cruiseY(x, z), z);
		}
		steer(new Vec3(waypoint.x, cruiseY(waypoint.x, waypoint.z), waypoint.z), CRUISE_SPEED);
		if (--searchCooldown <= 0 && abductions < maxAbductions) {
			searchCooldown = 100;
			prey = findPrey(level);
			if (prey != null) {
				setState(ABDUCTING);
			}
		}
	}

	/** The nearest animal it may take, out under the open sky. */
	private @Nullable Animal findPrey(ServerLevel level) {
		AABB area = new AABB(getX() - SEARCH_RADIUS, getY() - 64.0, getZ() - SEARCH_RADIUS, getX() + SEARCH_RADIUS, getY() + 8.0,
				getZ() + SEARCH_RADIUS);
		Animal nearest = null;
		for (Animal animal : level.getEntitiesOfClass(Animal.class, area, Ufo::canAbduct)) {
			if (nearest == null || animal.distanceToSqr(this) < nearest.distanceToSqr(this)) {
				nearest = animal;
			}
		}
		return nearest;
	}

	/** Livestock only: never a pet, a named animal, one on a lead or one with someone riding it. */
	public static boolean canAbduct(Animal animal) {
		return animal.isAlive() && animal.is(ABDUCTABLE) && !animal.hasCustomName() && !animal.isLeashed()
				&& !animal.isVehicle() && !animal.isPassenger() && !(animal instanceof TamableAnimal tame && tame.isTame())
				&& animal.level().canSeeSky(animal.blockPosition().above());
	}

	private void abduct(ServerLevel level) {
		if (beam != null) {
			// Holding station over the beam as it lifts.
			setDeltaMovement(getDeltaMovement().scale(0.8));
			if (beam.isRemoved()) {
				beam = null;
				prey = null;
				entityData.set(DATA_BEAMING, false);
				setState(ROAMING);
			}
			return;
		}
		if (prey == null || !canAbduct(prey) || stateTicks > 600) {
			prey = null;
			setState(ROAMING);
			return;
		}
		Vec3 over = prey.position().add(0.0, BEAM_HEIGHT, 0.0);
		steer(over, CRUISE_SPEED);
		if (position().subtract(over).horizontalDistanceSqr() < 1.0 && Math.abs(getY() - over.y) < 2.0) {
			beam = TractorBeam.liftAnimal(level, prey, this);
			beamDamage = 0.0f;
			entityData.set(DATA_BEAMING, beam != null);
			if (beam == null) {
				prey = null;
				setState(ROAMING);
			}
		}
	}

	/** Its beam has lifted the animal right up into the hull: gone. */
	void tookAboard(LivingEntity animal) {
		abductions++;
		entityData.set(DATA_BEAMING, false);
		playSound(Aliens.UFO_TAKE, 2.0f, 1.0f);
		if (level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 20, 0.4, 0.1, 0.4, 0.05);
		}
		animal.discard();
		beam = null;
		prey = null;
		searchCooldown = 200;
		if (abductions >= maxAbductions) {
			leave();
		} else {
			setState(ROAMING);
		}
	}

	/** Its grip on whatever it was lifting fails: it lets go. */
	private void dropBeam() {
		if (beam != null) {
			beam.release();
			beam = null;
		}
		entityData.set(DATA_BEAMING, false);
		prey = null;
	}

	private void descend(ServerLevel level) {
		if (landingSite == null || stateTicks > 1200) {
			leave();
			return;
		}
		Vec3 site = Vec3.atBottomCenterOf(landingSite).add(0.0, LEG_HEIGHT, 0.0);
		double away = position().subtract(site).horizontalDistance();
		if (away > 1.0) {
			// Over the spot first, high enough to clear anything on the way.
			steer(new Vec3(site.x, Math.max(site.y + 12.0, cruiseY(site.x, site.z) - altitude + 12.0), site.z), CRUISE_SPEED);
			return;
		}
		steer(site, 0.12);
		if (getY() - site.y < 4.0 && !entityData.get(DATA_LEGS)) {
			entityData.set(DATA_LEGS, true);
			playSound(Aliens.UFO_LAND, 1.5f, 0.8f);
		}
		if (Math.abs(getY() - site.y) < 0.08 && away < 0.4) {
			setPos(site);
			setDeltaMovement(Vec3.ZERO);
			setState(LANDED);
		}
	}

	private void stayLanded(ServerLevel level) {
		setDeltaMovement(Vec3.ZERO);
		if (stateTicks == 30) {
			entityData.set(DATA_DOOR, true);
			playSound(Aliens.UFO_DOOR, 1.5f, 0.7f);
		}
		if (crewToSpawn > 0 && stateTicks >= 50 && stateTicks % 18 == 0 && !recalling) {
			crewToSpawn--;
			Alien alien = Aliens.ALIEN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (alien != null) {
				Vec3 foot = rampFoot();
				alien.snapTo(foot.x, foot.y, foot.z, getYRot(), 0.0f);
				alien.finalizeSpawn(level, level.getCurrentDifficultyAt(alien.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
				alien.joinCrew(this);
				level.addFreshEntity(alien);
			}
		}
		if (!recalling && (stateTicks > stayTicks || stateTicks > 400 && level.isBrightOutside())) {
			recall();
		}
		if (!recalling) {
			return;
		}
		crewToSpawn = 0;
		recallTicks++;
		List<Alien> crew = crew();
		if (recallTicks == RECALL_TICKS) {
			// Stragglers are beamed aboard.
			for (Alien alien : crew) {
				alien.beamAboard();
			}
		}
		if (crew.isEmpty() && entityData.get(DATA_DOOR)) {
			entityData.set(DATA_DOOR, false);
			playSound(Aliens.UFO_DOOR, 1.5f, 0.6f);
			recallTicks = Math.max(recallTicks, RECALL_TICKS);
		}
		if (crew.isEmpty() && recallTicks > RECALL_TICKS + 30) {
			entityData.set(DATA_LEGS, false);
			playSound(Aliens.UFO_LAND, 1.5f, 1.1f);
			setState(TAKING_OFF);
		}
	}

	private void takeOff() {
		steer(new Vec3(getX(), cruiseY(getX(), getZ()), getZ()), 0.25);
		if (stateTicks > 40 && cruiseY(getX(), getZ()) - getY() < 3.0 || stateTicks > 400) {
			leave();
		}
	}

	/** Off it goes. */
	void leave() {
		dropBeam();
		setState(LEAVING);
	}

	private void depart(ServerLevel level) {
		Player nearest = level.getNearestPlayer(this, -1.0);
		Vec3 away = nearest == null ? Vec3.directionFromRotation(0.0f, getYRot()) : position().subtract(nearest.position()).horizontal();
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		steer(position().add(away.scale(30.0)).add(0.0, 12.0, 0.0), FAST_SPEED);
		if (stateTicks > 200 && (nearest == null || nearest.distanceToSqr(this) > 96.0 * 96.0) || stateTicks > 800 || getY() > level.getMaxY() - 8) {
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFB4FF9A), getX(), getY() + 0.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			discard();
		}
	}

	/** Angry: zaps whoever hurt it whenever it has a clear shot. */
	private void fightBack(ServerLevel level) {
		LivingEntity target = attacker;
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != level) {
			attacker = null;
			angerTicks = 0;
			return;
		}
		if (zapCooldown > 0 || distanceToSqr(target) > ZAP_RANGE * ZAP_RANGE || !hasLineOfSight(target)) {
			return;
		}
		zapCooldown = 50 + random.nextInt(30);
		Vec3 from = position().add(0.0, 0.1, 0.0);
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 end = EnergyWeaponItem.trace(level, this, from, at.subtract(from), ZAP_RANGE + 4, 0, 0, false,
				(hit, point) -> hit.hurtServer(level, EnergyWeaponItem.damage(level, EnergyWeapons.ALIEN_RAY, this), ZAP_DAMAGE));
		EnergyBeam.send(level, this, from, end, RayGunItem.BEAM_COLOR, 0.4f, 350);
		playSound(EnergyWeapons.RAY_ZAP, 3.0f, 0.55f);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && !isCrashing()) {
			if (source.getEntity() instanceof LivingEntity by && by != this) {
				anger(by);
			}
			if (beam != null) {
				beamDamage += damage;
				if (beamDamage >= BEAM_BREAK_DAMAGE) {
					// It flinches and loses its grip.
					dropBeam();
					setState(ROAMING);
					searchCooldown = 200;
				}
			}
			if (isLanded()) {
				recall();
			}
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return isCrashing() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
	}

	// ------------------------------------------------------------------------------------------------ crashing

	/** Shot down: rather than dying on the spot it goes down in flames, and only dies when it hits the ground. */
	@Override
	public void die(DamageSource source) {
		if (crashed || !(level() instanceof ServerLevel) || isRemoved()) {
			super.die(source);
			return;
		}
		if (isCrashing()) {
			// Killed outright on the way down.
			impact((ServerLevel) level());
			return;
		}
		crashSource = source;
		setHealth(1.0f);
		dropBeam();
		for (Alien alien : crew()) {
			alien.shipLost();
		}
		entityData.set(DATA_LEGS, false);
		entityData.set(DATA_DOOR, false);
		boolean onTheGround = isLanded();
		setState(CRASHING);
		if (onTheGround) {
			impact((ServerLevel) level());
		}
	}

	private void crash(ServerLevel level) {
		// Keeps the kill (and its experience) with whoever shot it down.
		if (crashSource != null && crashSource.getEntity() instanceof Player player) {
			setLastHurtByPlayer(player, 100);
		}
		setYRot(getYRot() + 25.0f);
		double fall = Math.min(0.7, 0.15 + stateTicks * 0.01);
		float turn = stateTicks * 0.25f;
		setDeltaMovement(Mth.cos(turn) * 0.35, -fall, Mth.sin(turn) * 0.35);
		double angle = random.nextDouble() * Mth.TWO_PI;
		double x = getX() + Math.cos(angle) * WIDTH * 0.4;
		double z = getZ() + Math.sin(angle) * WIDTH * 0.4;
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, getY() + 0.6, z, 3, 0.2, 0.2, 0.2, 0.02);
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.FLAME, x, getY() + 0.5, z, 4, 0.2, 0.1, 0.2, 0.02);
		}
		if (stateTicks % 10 == 0) {
			playSound(EnergyWeapons.RAY_ZAP, 2.0f, 0.4f + random.nextFloat() * 0.2f);
		}
		if (stateTicks > 5 && (onGround() || verticalCollision || horizontalCollision) || stateTicks > 400 || isInWater()) {
			impact(level);
		}
	}

	/** Down: an explosion, its dazed survivors thrown clear, and then it's dead (and drops its loot) where it fell. */
	private void impact(ServerLevel level) {
		if (crashed) {
			return;
		}
		crashed = true;
		level.explode(this, getX(), getY() + 0.5, getZ(), 3.5f, true, Level.ExplosionInteraction.MOB);
		int survivors = random.nextInt(3);
		for (int i = 0; i < survivors; i++) {
			Alien alien = Aliens.ALIEN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (alien != null) {
				alien.snapTo(getX() + random.nextGaussian(), getY() + 0.5, getZ() + random.nextGaussian(), random.nextFloat() * 360.0f, 0.0f);
				alien.finalizeSpawn(level, level.getCurrentDifficultyAt(alien.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
				alien.daze();
				level.addFreshEntity(alien);
			}
		}
		setHealth(0.0f);
		die(crashSource != null ? crashSource : damageSources().generic());
	}

	@Override
	protected void tickDeath() {
		if (level() instanceof ServerLevel level && deathTime == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 40, WIDTH * 0.3, 0.4, WIDTH * 0.3, 0.03);
			level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 12, WIDTH * 0.25, 0.2, WIDTH * 0.25, 0.0);
		}
		if (++deathTime >= 2 && !level().isClientSide() && !isRemoved()) {
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (beam != null && !beam.isRemoved()) {
			beam.release();
		}
		super.remove(reason);
	}

	// ------------------------------------------------------------------------------------------------ client

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			return;
		}
		legsO = legs;
		doorO = door;
		spinO = spin;
		legs = Mth.approach(legs, entityData.get(DATA_LEGS) ? 1.0f : 0.0f, 0.04f);
		door = Mth.approach(door, entityData.get(DATA_DOOR) ? 1.0f : 0.0f, 0.03f);
		float wantedSpin = isCrashing() ? 18.0f : isLanded() ? 0.0f : state() == LEAVING ? 9.0f : 3.0f;
		spinSpeed = Mth.approach(spinSpeed, wantedSpin, 0.1f);
		spin += spinSpeed;
		if (isCrashing() && random.nextInt(2) == 0) {
			level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + random.nextGaussian(), getY() + 0.5, getZ() + random.nextGaussian(), 0.0, 0.0, 0.0);
		}
	}

	public float legs(float partialTicks) {
		return Mth.lerp(partialTicks, legsO, legs);
	}

	public float door(float partialTicks) {
		return Mth.lerp(partialTicks, doorO, door);
	}

	public float spin(float partialTicks) {
		return Mth.lerp(partialTicks, spinO, spin);
	}

	// ------------------------------------------------------------------------------------------------ the rest

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Aliens.UFO_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Aliens.UFO_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("UfoState", state() == CRASHING ? LEAVING : state());
		output.storeNullable("Visiting", UUIDUtil.CODEC, visiting);
		output.store("Home", Vec3.CODEC, home);
		output.putInt("VisitTicks", visitTicks);
		output.putInt("Abductions", abductions);
		output.storeNullable("LandingSite", BlockPos.CODEC, landingSite);
		output.putInt("Stay", stayTicks);
		output.putInt("Crew", crewToSpawn);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		int state = input.getIntOr("UfoState", ROAMING);
		visiting = input.read("Visiting", UUIDUtil.CODEC).orElse(null);
		home = input.read("Home", Vec3.CODEC).orElse(position());
		visitTicks = input.getIntOr("VisitTicks", 0);
		abductions = input.getIntOr("Abductions", 0);
		landingSite = input.read("LandingSite", BlockPos.CODEC).orElse(null);
		stayTicks = input.getIntOr("Stay", MIN_STAY);
		crewToSpawn = input.getIntOr("Crew", 0);
		// Mid-abduction, its beam's gone with the reload; landed, its crew still know it (and it waits for them).
		setState(state == ABDUCTING ? ROAMING : state);
		if (state == LANDED) {
			entityData.set(DATA_LEGS, true);
			entityData.set(DATA_DOOR, true);
			stateTicks = 60;
		}
	}
}
