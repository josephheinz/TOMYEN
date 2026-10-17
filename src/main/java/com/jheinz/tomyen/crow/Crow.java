package com.jheinz.tomyen.crow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.scarecrow.CropGuard;

/**
 * A crow: a glossy black bird with a blue-purple sheen, one of a murder ({@link CrowFlock}). Cowardly and light-fingered:
 * <ul>
 * <li>It flies as one of a loose flock (boids: it steers for the murder's goal and its own place in the flock, keeps
 * its distance from the birds next to it, drifts toward their middle and matches their heading) and comes down to sit
 * on fences, roofs and treetops, or hops about a field pecking.
 * <li>On a raid it takes a ripe crop of its own, pecks it to bits, and makes off with the produce (the seeds it shakes
 * loose, and any lying about, it eats).
 * <li>It goes for anything {@linkplain Crows#SHINY shiny} on the ground, and now and then a bold one snatches a shiny
 * thing from the hand of someone standing still nearby.
 * <li>Whatever it carries it takes to the murder's {@link CrowNestBlock}, building one in a treetop if there isn't one.
 * It never loses what it carries: killed, it drops it; and it doesn't despawn while it's holding anything.
 * <li>Hit one and the whole murder bursts into the air cawing. Rob their nest and a few dive at you.
 * </ul>
 * Movement is its own, not pathfinding: in the air it steers its velocity directly (with no gravity) and on the ground
 * it hops. The searches it makes (neighbours, items, crops, landing spots, the flock) are each throttled to once every
 * half second to two seconds.
 */
public class Crow extends PathfinderMob {
	public static final float WIDTH = 0.5f;
	public static final float HEIGHT = 0.5f;
	/** Client events: a peck (head down and up) and a caw (beak open, a bob). */
	public static final byte EVENT_PECK = 90;
	public static final byte EVENT_CAW = 91;

	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(Crow.class, EntityDataSerializers.BYTE);
	private static final int FLAG_FLYING = 1;
	private static final int FLAG_SLEEPING = 2;

	private static final double CRUISE = 0.32;
	private static final double FLEE_SPEED = 0.5;
	private static final double ACCEL = 0.06;
	private static final double SEPARATION = 1.6;
	/** Produce a crow can't take home it eats after this long. */
	private static final int EAT_PRODUCE_TICKS = 900;

	enum Task {
		NONE, PECK_CROP, EAT_SEED, GRAB, SNATCH, STASH, BUILD, DEFEND
	}

	// --- the murder
	private @Nullable UUID leaderId;
	private @Nullable Crow leaderCache;
	private int membershipCheck;
	final CrowFlock flock = new CrowFlock(this);
	private @Nullable BlockPos nest;
	private @Nullable BlockPos home;
	private int seenScatter;

	// --- flying in a flock
	private final List<Crow> neighbours = new ArrayList<>();
	private int neighbourCheck;
	private final float slotAngle;
	private final float slotRadius;
	private final float slotHeight;

	// --- what it's after
	private Task task = Task.NONE;
	private @Nullable BlockPos taskPos;
	private int taskEntity = -1;
	private @Nullable UUID taskPlayer;
	private int taskTicks;
	private int work;
	private int scanCooldown;
	private int buildCooldown;
	private int carryTicks;
	private int attackCooldown;
	private int retreatTicks;
	private int cawDelay;
	private boolean cawAlarm;

	// --- where it's going this tick
	private @Nullable Vec3 goal;
	private boolean goalLand;
	private double goalSpeed;
	private @Nullable Vec3 landing;
	private int landingTicks;
	private int landingRetry;
	private int idleTimer;
	private int hopCooldown;

	// --- client animation
	private int peckTicks;
	private int cawTicks;
	private float flyAmount;
	private float flyAmountO;
	private float flap;
	private float flapO;
	private float flapSpeed;
	private float flightPitch;
	private float flightPitchO;

	public Crow(EntityType<? extends Crow> type, Level level) {
		super(type, level);
		setCanPickUpLoot(false);
		slotAngle = random.nextFloat() * Mth.TWO_PI;
		slotRadius = 1.5f + random.nextFloat() * 3.5f;
		slotHeight = random.nextFloat() * 3.0f - 1.0f;
		scanCooldown = random.nextInt(40);
		neighbourCheck = random.nextInt(10);
		idleTimer = random.nextInt(40);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 6.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FLYING_SPEED, 0.4)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	/**
	 * Natural spawns: by day, under open sky, on grass or tilled ground; three times as readily near a village or a
	 * farm (a few columns sampled for crops or farmland) as anywhere else.
	 */
	public static boolean checkCrowSpawnRules(EntityType<Crow> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		ServerLevel server = level.getLevel();
		if (!server.isBrightOutside() || !level.canSeeSky(pos)) {
			return false;
		}
		BlockState below = level.getBlockState(pos.below());
		if (!below.is(BlockTags.ANIMALS_SPAWNABLE_ON) && !below.is(Blocks.FARMLAND)) {
			return false;
		}
		return server.isVillage(pos) || nearFarm(level, pos, random) || random.nextInt(3) == 0;
	}

	private static boolean nearFarm(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 6; i++) {
			int x = pos.getX() + random.nextInt(25) - 12;
			int z = pos.getZ() + random.nextInt(25) - 12;
			if (!level.hasChunkAt(x, z)) {
				continue;
			}
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
			BlockState state = level.getBlockState(top);
			if (state.is(Blocks.FARMLAND) || state.is(BlockTags.CROPS)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return 12;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		home = blockPosition();
		if (groupData instanceof Murder murder) {
			leaderId = murder.leader;
		} else {
			groupData = new Murder(getUUID());
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	/** The murder a natural spawn's crows all belong to: the first of them leads. */
	private record Murder(UUID leader) implements SpawnGroupData {
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLAGS, (byte) 0);
	}

	@Override
	protected void registerGoals() {
	}

	// ------------------------------------------------------------------------------------------------ state

	private boolean flag(int flag) {
		return (entityData.get(DATA_FLAGS) & flag) != 0;
	}

	private void setFlag(int flag, boolean on) {
		byte flags = entityData.get(DATA_FLAGS);
		entityData.set(DATA_FLAGS, (byte) (on ? flags | flag : flags & ~flag));
	}

	public boolean isFlying() {
		return flag(FLAG_FLYING);
	}

	public boolean isSleeping() {
		return flag(FLAG_SLEEPING);
	}

	public boolean hasLoot() {
		return !getMainHandItem().isEmpty();
	}

	public @Nullable BlockPos nest() {
		return nest;
	}

	BlockPos home() {
		return home != null ? home : blockPosition();
	}

	/** The murder's leader: itself if it leads (or hasn't found its leader yet). */
	public Crow leader() {
		if (leaderId == null) {
			return this;
		}
		Crow cached = leaderCache;
		return cached != null && cached.isAlive() && leaderId.equals(cached.getUUID()) ? cached : this;
	}

	/** Free to be sent on an errand: not carrying, not busy, not asleep. */
	boolean isFree() {
		return isAlive() && !hasLoot() && (task == Task.NONE || task == Task.EAT_SEED) && !isSleeping();
	}

	boolean canDefend() {
		return isAlive() && getHealth() > 3.0f && task != Task.DEFEND;
	}

	/** Brought down on someone robbing the nest. */
	void defend(Player robber) {
		startTask(Task.DEFEND);
		taskPlayer = robber.getUUID();
	}

	void snatchFrom(Player player) {
		startTask(Task.SNATCH);
		taskPlayer = player.getUUID();
	}

	void cawSoon(int ticks, boolean alarm) {
		cawDelay = ticks;
		cawAlarm = alarm;
	}

	private void setLoot(ItemStack stack) {
		setItemSlot(EquipmentSlot.MAINHAND, stack);
		setGuaranteedDrop(EquipmentSlot.MAINHAND);
		carryTicks = 0;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return hasLoot() || super.requiresCustomPersistence();
	}

	// ------------------------------------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (--membershipCheck <= 0) {
			membershipCheck = 40;
			updateMembership(level);
		}
		if (--neighbourCheck <= 0) {
			neighbourCheck = 10;
			updateNeighbours(level);
		}
		Crow leader = leader();
		if (leader == this) {
			flock.tick(level);
		} else {
			nest = leader.nest;
		}
		CrowFlock plan = leader.flock;
		if (attackCooldown > 0) {
			attackCooldown--;
		}
		if (buildCooldown > 0) {
			buildCooldown--;
		}
		if (hopCooldown > 0) {
			hopCooldown--;
		}
		if (cawDelay > 0 && --cawDelay == 0) {
			caw(level, cawAlarm);
		}
		goal = null;
		think(level, plan);
		if (isFlying()) {
			fly(level, plan);
		}
	}

	/** Who leads its murder: following the leader's own leader if it has one, and joining a nearby murder that sorts first. */
	private void updateMembership(ServerLevel level) {
		if (leaderId != null) {
			Entity entity = level.getEntity(leaderId);
			if (entity instanceof Crow crow && crow != this && crow.isAlive() && distanceToSqr(crow) < 64.0 * 64.0) {
				if (crow.leaderId == null) {
					leaderCache = crow;
				} else if (crow.leaderId.equals(getUUID())) {
					// A loop: this one leads.
					leaderId = null;
					leaderCache = null;
				} else {
					leaderId = crow.leaderId;
					leaderCache = null;
					membershipCheck = 1;
				}
			} else {
				leaderId = null;
				leaderCache = null;
			}
			return;
		}
		int followers = 0;
		Crow join = null;
		for (Crow other : level.getEntitiesOfClass(Crow.class, getBoundingBox().inflate(24.0), crow -> crow != this && crow.isAlive())) {
			Crow theirs = other.leader();
			if (theirs == this) {
				followers++;
			} else if (theirs.leaderId == null && theirs.getUUID().compareTo(getUUID()) < 0 && distanceToSqr(theirs) < 20.0 * 20.0
					&& (join == null || theirs.getUUID().compareTo(join.getUUID()) < 0)) {
				join = theirs;
			}
		}
		flock.size = followers + 1;
		if (join != null && join.flock.size + flock.size <= CrowFlock.MAX_SIZE) {
			leaderId = join.getUUID();
			leaderCache = join;
			if (join.nest == null) {
				join.nest = nest;
			}
			join.flock.size += flock.size;
		}
	}

	private void updateNeighbours(ServerLevel level) {
		neighbours.clear();
		List<Crow> near = level.getEntitiesOfClass(Crow.class, getBoundingBox().inflate(5.0), crow -> crow != this && crow.isAlive());
		near.sort((a, b) -> Double.compare(distanceToSqr(a), distanceToSqr(b)));
		for (int i = 0; i < Math.min(7, near.size()); i++) {
			neighbours.add(near.get(i));
		}
	}

	// ------------------------------------------------------------------------------------------------ deciding

	private void think(ServerLevel level, CrowFlock plan) {
		if (isInWater() && !isFlying()) {
			takeOff(level);
		}
		if (seenScatter != plan.scatterStamp()) {
			seenScatter = plan.scatterStamp();
			if (plan.mode() == CrowFlock.Mode.FLEE || plan.mode() == CrowFlock.Mode.MOB) {
				cawSoon(1 + random.nextInt(14), true);
			}
		}
		if (plan.mode() != CrowFlock.Mode.ROOST || isFlying()) {
			setFlag(FLAG_SLEEPING, false);
		}
		if (plan.mode() == CrowFlock.Mode.FLEE && task != Task.DEFEND) {
			if (task != Task.STASH && task != Task.BUILD) {
				clearTask();
			}
			landing = null;
			if (!isFlying()) {
				takeOff(level);
			}
			if (task == Task.NONE) {
				return;
			}
		}
		if (task != Task.NONE) {
			tickTask(level, plan);
			return;
		}
		if (hasLoot()) {
			startLootRun(level);
			if (task != Task.NONE) {
				return;
			}
		}
		findTask(level, plan);
		if (task == Task.NONE) {
			followPlan(level, plan);
		}
	}

	/** Loot in its beak: off to the nest with it, or to build one. Produce it can't take anywhere it eats in the end. */
	private void startLootRun(ServerLevel level) {
		if (CrowNests.isNest(level, nest) && nest.distSqr(blockPosition()) < 96 * 96) {
			startTask(Task.STASH);
			taskPos = nest;
			return;
		}
		if (CrowRaids.mayRaid(level) && buildCooldown <= 0) {
			BlockPos site = CrowNests.findSite(level, blockPosition(), random);
			if (site != null) {
				startTask(Task.BUILD);
				taskPos = site;
				return;
			}
			buildCooldown = 600;
		}
		if (!getMainHandItem().is(Crows.SHINY) && ++carryTicks > EAT_PRODUCE_TICKS) {
			eat(level, getMainHandItem());
			setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	private void findTask(ServerLevel level, CrowFlock plan) {
		if (plan.mode() == CrowFlock.Mode.RAID && plan.hasCrops() && !isSleeping()) {
			BlockPos crop = plan.claimCrop(this);
			if (crop != null) {
				startTask(Task.PECK_CROP);
				taskPos = crop;
				return;
			}
		}
		if (--scanCooldown > 0) {
			return;
		}
		scanCooldown = 30 + random.nextInt(20);
		if (!CrowRaids.mayRaid(level) || plan.mode() == CrowFlock.Mode.ROOST || plan.mode() == CrowFlock.Mode.FLEE) {
			return;
		}
		ItemEntity shiny = hasLoot() ? null : CrowRaids.findItem(level, this, 12.0, Crows.SHINY, plan);
		if (shiny != null) {
			startTask(Task.GRAB);
			taskEntity = shiny.getId();
			plan.claim(shiny, level.getGameTime() + 300);
			return;
		}
		if (plan.mode() == CrowFlock.Mode.FORAGE || plan.mode() == CrowFlock.Mode.RAID || plan.mode() == CrowFlock.Mode.PERCH) {
			ItemEntity seed = CrowRaids.findItem(level, this, 8.0, ItemTags.CHICKEN_FOOD, plan);
			if (seed != null) {
				startTask(Task.EAT_SEED);
				taskEntity = seed.getId();
				plan.claim(seed, level.getGameTime() + 200);
			}
		}
	}

	/** Nothing of its own to do: where the murder is, it goes. */
	private void followPlan(ServerLevel level, CrowFlock plan) {
		CrowFlock.Mode mode = plan.mode();
		if (!mode.landed()) {
			landing = null;
			if (!isFlying()) {
				takeOff(level);
			}
			return;
		}
		Vec3 anchor = plan.anchor();
		if (isFlying()) {
			landingTicks++;
			if ((landing == null || landingTicks > 200 || landing.distanceToSqr(anchor) > 14.0 * 14.0) && --landingRetry <= 0) {
				landingRetry = 10;
				CrowRaids.Want want = switch (mode) {
					case PERCH -> CrowRaids.Want.PERCH;
					case ROOST -> CrowRaids.Want.TREETOP;
					default -> CrowRaids.Want.GROUND;
				};
				landing = CrowRaids.findLanding(level, anchor, mode == CrowFlock.Mode.RAID ? 7.0 : 5.0, want, random);
				landingTicks = 0;
			}
			if (landing != null) {
				goTo(level, landing, true, CRUISE);
			}
			return;
		}
		if (distanceToSqr(anchor) > 16.0 * 16.0) {
			landing = null;
			takeOff(level);
			return;
		}
		idle(level, plan);
	}

	// ------------------------------------------------------------------------------------------------ tasks

	private void startTask(Task task) {
		this.task = task;
		taskTicks = 0;
		work = 0;
		taskPos = null;
		taskEntity = -1;
		taskPlayer = null;
		retreatTicks = 0;
		setFlag(FLAG_SLEEPING, false);
	}

	private void clearTask() {
		task = Task.NONE;
		taskPos = null;
		taskEntity = -1;
		taskPlayer = null;
	}

	private void tickTask(ServerLevel level, CrowFlock plan) {
		taskTicks++;
		switch (task) {
			case PECK_CROP -> peckCrop(level, plan);
			case EAT_SEED, GRAB -> pickUp(level);
			case SNATCH -> snatch(level);
			case STASH -> stash(level);
			case BUILD -> build(level);
			case DEFEND -> defend(level);
			case NONE -> {
			}
		}
	}

	/** Down by its crop, pecking it to bits: about two seconds of pecks, then off with the produce. */
	private void peckCrop(ServerLevel level, CrowFlock plan) {
		BlockPos crop = taskPos;
		if (crop == null || taskTicks > 400 || !level.isLoaded(crop) || !CrowRaids.isRipe(level.getBlockState(crop))) {
			clearTask();
			return;
		}
		Vec3 spot = CrowRaids.standPoint(level, crop);
		if (!arrived(spot, 0.75)) {
			goTo(level, spot, true, CRUISE);
			return;
		}
		lookAt(Vec3.atCenterOf(crop));
		if (++work % 9 == 1) {
			peck(level);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(crop)), crop.getX() + 0.5, crop.getY() + 0.4,
					crop.getZ() + 0.5, 3, 0.15, 0.15, 0.15, 0.02);
		}
		if (work >= 45) {
			if (CropGuard.scaresCrows(level, crop)) {
				plan.scatter(level, Vec3.atCenterOf(crop), 1.0);
				clearTask();
				return;
			}
			ItemStack produce = CrowRaids.harvest(level, crop, this);
			if (!produce.isEmpty()) {
				if (hasLoot()) {
					eat(level, produce);
				} else {
					setLoot(produce);
				}
			}
			clearTask();
		}
	}

	/** After an item on the ground: a shiny one goes in the beak; seed gets eaten where it lies. */
	private void pickUp(ServerLevel level) {
		Entity entity = level.getEntity(taskEntity);
		boolean grab = task == Task.GRAB;
		if (!(entity instanceof ItemEntity item) || !item.isAlive() || taskTicks > 300 || grab && hasLoot()
				|| !item.getItem().is(grab ? Crows.SHINY : ItemTags.CHICKEN_FOOD)) {
			clearTask();
			return;
		}
		if (distanceToSqr(item) > 1.0) {
			goTo(level, item.position(), true, CRUISE);
			return;
		}
		lookAt(item.position());
		if (grab) {
			setLoot(item.getItem().copy());
			item.discard();
			playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.6f);
			cawSoon(4, false);
			clearTask();
			return;
		}
		if (++work % 8 == 1) {
			peck(level);
		}
		if (work >= 20) {
			ItemStack seed = item.getItem();
			seed.shrink(1 + random.nextInt(3));
			if (seed.isEmpty()) {
				item.discard();
			} else {
				item.setItem(seed);
			}
			eatParticles(level, seed.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS) : seed);
			clearTask();
		}
	}

	/** Diving at a player's hand to take a beakful of whatever shiny thing they're holding. */
	private void snatch(ServerLevel level) {
		Player player = taskPlayer == null ? null : level.getPlayerByUUID(taskPlayer);
		if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative() || taskTicks > 160 || hasLoot()
				|| !player.getMainHandItem().is(Crows.SHINY)) {
			clearTask();
			return;
		}
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.4)).add(0.0, -0.5, 0.0);
		goTo(level, hand, false, 0.55);
		lookAt(hand);
		if (distanceToSqr(hand) < 1.3 * 1.3) {
			ItemStack held = player.getMainHandItem();
			ItemStack stolen = held.split(Math.min(held.getCount(), 1 + random.nextInt(3)));
			player.getInventory().setChanged();
			setLoot(stolen);
			playSound(Crows.SNATCH, 1.0f, 1.0f);
			cawSoon(3, false);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("entity.tomyen.crow.snatched", stolen.getHoverName()));
			}
			// Away and up at once.
			Vec3 away = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			setDeltaMovement(away.scale(0.45).add(0.0, 0.35, 0.0));
			clearTask();
		}
	}

	/** Home to the nest to drop off its loot. A nest gone missing is forgotten (so the murder builds another), as is a full one. */
	private void stash(ServerLevel level) {
		BlockPos at = taskPos;
		if (!hasLoot() || taskTicks > 1200) {
			clearTask();
			return;
		}
		if (at == null || !CrowNests.isNest(level, at) || !(level.getBlockEntity(at) instanceof CrowNestBlockEntity box)) {
			forgetNest(at);
			clearTask();
			return;
		}
		Vec3 top = new Vec3(at.getX() + 0.5, at.getY() + CrowNestBlock.STAND_HEIGHT / 16.0, at.getZ() + 0.5);
		if (!arrived(top, 0.8)) {
			goTo(level, top, true, 0.4);
			return;
		}
		if (++work == 1) {
			peck(level);
		}
		if (work >= 10) {
			ItemStack rest = box.stash(getMainHandItem());
			setItemSlot(EquipmentSlot.MAINHAND, rest);
			level.playSound(null, at, Crows.NEST_RUSTLE, SoundSource.NEUTRAL, 0.6f, 1.2f);
			if (!rest.isEmpty()) {
				forgetNest(at);
			}
			clearTask();
		}
	}

	private void forgetNest(@Nullable BlockPos at) {
		if (at != null && at.equals(leader().nest)) {
			leader().nest = null;
		}
		if (at != null && at.equals(nest)) {
			nest = null;
		}
	}

	/** Building a nest in a treetop: down onto the crown, a few seconds' weaving, and there it is. */
	private void build(ServerLevel level) {
		BlockPos site = taskPos;
		if (!hasLoot() || taskTicks > 900 || site == null || !CrowNests.canBuildAt(level, site) || !CrowRaids.mayRaid(level)) {
			if (site == null || !CrowNests.isNest(level, site)) {
				buildCooldown = 200;
			}
			clearTask();
			return;
		}
		Vec3 top = CrowRaids.standPoint(level, site.above());
		if (!arrived(top, 0.8)) {
			goTo(level, top, true, 0.4);
			return;
		}
		if (++work % 10 == 1) {
			peck(level);
			level.playSound(null, site, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.NEUTRAL, 0.6f, 1.3f);
		}
		if (work >= 50) {
			level.setBlock(site, Crows.CROW_NEST.defaultBlockState(), 3);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), site.getX() + 0.5,
					site.getY() + 0.5, site.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
			setPos(getX(), site.getY() + CrowNestBlock.STAND_HEIGHT / 16.0 + 0.01, getZ());
			nest = site;
			leader().nest = site;
			clearTask();
		}
	}

	/** Diving at a nest robber: in for a peck, then up and away for a moment, and in again. */
	private void defend(ServerLevel level) {
		Player player = taskPlayer == null ? null : level.getPlayerByUUID(taskPlayer);
		if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative() || taskTicks > 260 || getHealth() <= 3.0f
				|| distanceToSqr(player) > 40.0 * 40.0) {
			clearTask();
			return;
		}
		if (retreatTicks > 0) {
			retreatTicks--;
			Vec3 away = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			goTo(level, player.position().add(away.scale(5.0)).add(0.0, 4.0, 0.0), false, 0.45);
			return;
		}
		Vec3 head = player.getEyePosition();
		goTo(level, head, false, 0.5);
		lookAt(head);
		if (attackCooldown <= 0 && distanceToSqr(player) < 1.8 * 1.8) {
			if (doHurtTarget(level, player)) {
				level.broadcastEntityEvent(this, EVENT_PECK);
			}
			attackCooldown = 30;
			retreatTicks = 25 + random.nextInt(15);
			cawSoon(2, true);
		}
	}

	// ------------------------------------------------------------------------------------------------ moving

	private boolean arrived(Vec3 spot, double radius) {
		double dx = spot.x - getX();
		double dz = spot.z - getZ();
		return !isFlying() && dx * dx + dz * dz < radius * radius && Math.abs(spot.y - getY()) < 0.9;
	}

	/**
	 * Heads for a point. In the air it's steered there by {@link #fly}; on the ground a short way it hops, further (or
	 * higher) it takes off. With land set it comes down on the point.
	 */
	private void goTo(ServerLevel level, Vec3 spot, boolean land, double speed) {
		goal = spot;
		goalLand = land;
		goalSpeed = speed;
		if (isFlying()) {
			return;
		}
		double dx = spot.x - getX();
		double dz = spot.z - getZ();
		double h = Math.sqrt(dx * dx + dz * dz);
		double dy = spot.y - getY();
		if (!land || h > 2.5 || dy > 0.9 || dy < -2.5) {
			takeOff(level);
		} else if (onGround() && hopCooldown <= 0 && h > 0.35) {
			hop(dx / h, dz / h, Math.min(0.22, 0.07 + h * 0.07));
			hopCooldown = 6 + random.nextInt(5);
		}
	}

	private void takeOff(ServerLevel level) {
		setFlag(FLAG_FLYING, true);
		setFlag(FLAG_SLEEPING, false);
		landing = null;
		landingTicks = 0;
		setDeltaMovement(getDeltaMovement().add(0.0, 0.38, 0.0));
		playSound(Crows.FLAP, 0.6f, 0.9f + random.nextFloat() * 0.25f);
	}

	private void land() {
		setFlag(FLAG_FLYING, false);
		Vec3 v = getDeltaMovement();
		setDeltaMovement(v.x * 0.3, -0.1, v.z * 0.3);
		idleTimer = 10 + random.nextInt(20);
	}

	private void hop(double dirX, double dirZ, double speed) {
		setDeltaMovement(dirX * speed, 0.3, dirZ * speed);
		face(dirX, dirZ, 1.0f);
		needsSync = true;
	}

	/** In the air: steer the velocity toward where it wants to go (or where the flock is going), then move. */
	private void fly(ServerLevel level, CrowFlock plan) {
		Vec3 want;
		Vec3 pos = position();
		if (goal != null) {
			Vec3 to = goal.subtract(pos);
			double h = Math.sqrt(to.x * to.x + to.z * to.z);
			if (goalLand) {
				// Come in from above, gliding down as it closes.
				double over = goal.y + Math.min(4.0, h * 0.6);
				to = new Vec3(to.x, over - pos.y, to.z);
				if (h < 0.6 && pos.y - goal.y > -0.3 && pos.y - goal.y < 2.0) {
					land();
					return;
				}
			}
			double speed = goalSpeed * Mth.clamp(to.length() / 3.0, 0.25, 1.0);
			want = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(speed);
			want = want.add(separation().scale(0.15));
			if (h > 4.0) {
				want = keepAboveGround(level, pos, want, 1.5);
			}
		} else {
			want = flocking(level, plan, pos);
		}
		Vec3 v = getDeltaMovement();
		Vec3 dv = want.subtract(v);
		if (dv.length() > ACCEL) {
			dv = dv.normalize().scale(ACCEL);
		}
		v = v.add(dv);
		if (horizontalCollision) {
			v = v.add(0.0, 0.12, 0.0);
		}
		setDeltaMovement(v);
		if (v.horizontalDistanceSqr() > 1.0E-4) {
			face(v.x, v.z, 0.4f);
		}
	}

	/** Boids: toward its place round the murder's goal, apart from close neighbours, toward their middle, along with them. */
	private Vec3 flocking(ServerLevel level, CrowFlock plan, Vec3 pos) {
		boolean fleeing = plan.mode() == CrowFlock.Mode.FLEE;
		// Its own place in the flock, wheeling slowly round the goal.
		float turn = slotAngle + tickCount * (plan.mode() == CrowFlock.Mode.MOB ? 0.05f : 0.015f);
		double radius = slotRadius * (plan.mode() == CrowFlock.Mode.MOB ? 1.4 : 1.0);
		Vec3 target = plan.anchor().add(Mth.cos(turn) * radius, slotHeight, Mth.sin(turn) * radius);
		Vec3 to = target.subtract(pos);
		double speed = (fleeing ? FLEE_SPEED : CRUISE) * Mth.clamp(to.length() / 5.0, 0.35, 1.0);
		Vec3 want = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(speed);
		Vec3 centre = Vec3.ZERO;
		Vec3 heading = Vec3.ZERO;
		int n = 0;
		for (Crow other : neighbours) {
			if (!other.isAlive() || !other.isFlying()) {
				continue;
			}
			centre = centre.add(other.position());
			heading = heading.add(other.getDeltaMovement());
			n++;
		}
		if (n > 0) {
			want = want.add(centre.scale(1.0 / n).subtract(pos).scale(0.02));
			want = want.add(heading.scale(1.0 / n).subtract(getDeltaMovement()).scale(0.25));
		}
		want = want.add(separation().scale(0.3));
		want = keepAboveGround(level, pos, want, 2.5);
		double max = fleeing ? FLEE_SPEED : CRUISE * 1.2;
		return want.length() > max ? want.normalize().scale(max) : want;
	}

	private Vec3 separation() {
		Vec3 push = Vec3.ZERO;
		for (Crow other : neighbours) {
			Vec3 diff = position().subtract(other.position());
			double d = diff.length();
			if (d > 1.0E-3 && d < SEPARATION) {
				push = push.add(diff.scale((SEPARATION - d) / (SEPARATION * d)));
			}
		}
		return push;
	}

	/** Climbs if the ground (or a tree) under it, or a little ahead, is closer than clearance. */
	private Vec3 keepAboveGround(ServerLevel level, Vec3 pos, Vec3 want, double clearance) {
		int x = Mth.floor(pos.x);
		int z = Mth.floor(pos.z);
		int ax = Mth.floor(pos.x + want.x * 10.0);
		int az = Mth.floor(pos.z + want.z * 10.0);
		double ground = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : pos.y;
		if (level.hasChunkAt(ax, az)) {
			ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, ax, az));
		}
		double short_ = ground + clearance - pos.y;
		return short_ > 0.0 ? new Vec3(want.x, Math.max(want.y, Math.min(0.35, short_ * 0.12)), want.z) : want;
	}

	private void face(double dx, double dz, float rate) {
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		setYRot(Mth.rotLerp(rate, getYRot(), yaw));
		yBodyRot = getYRot();
		yHeadRot = getYRot();
	}

	private void lookAt(Vec3 at) {
		getLookControl().setLookAt(at.x, at.y, at.z, 30.0f, 30.0f);
		if (!isFlying()) {
			face(at.x - getX(), at.z - getZ(), 0.3f);
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (isFlying() && isAlive()) {
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
			if (onGround() && goal == null && getDeltaMovement().y <= 0.0) {
				setDeltaMovement(getDeltaMovement().add(0.0, 0.2, 0.0));
			}
		} else {
			super.travel(input);
		}
	}

	/** Its body always faces the way it's going (the head turns on its own). */
	@Override
	protected void tickHeadTurn(float yBodyRotT) {
		yBodyRot = Mth.rotLerp(0.5f, yBodyRot, getYRot());
	}

	// ------------------------------------------------------------------------------------------------ on the ground

	/** Sitting about: the odd hop, peck, caw or look round; asleep at night. */
	private void idle(ServerLevel level, CrowFlock plan) {
		if (!onGround() || --idleTimer > 0) {
			return;
		}
		idleTimer = 15 + random.nextInt(35);
		if (plan.mode() == CrowFlock.Mode.ROOST) {
			setFlag(FLAG_SLEEPING, true);
			idleTimer = 100;
			return;
		}
		boolean perched = CrowRaids.isPerch(level.getBlockState(getBlockPosBelowThatAffectsMyMovement()));
		boolean feeding = plan.mode() == CrowFlock.Mode.FORAGE || plan.mode() == CrowFlock.Mode.RAID;
		float roll = random.nextFloat();
		if (roll < (feeding ? 0.45f : 0.15f) && !perched) {
			peck(level);
		} else if (roll < 0.75f && !perched) {
			Vec3 toward = plan.anchor().subtract(position());
			float angle = toward.horizontalDistanceSqr() > 25.0 ? (float) Mth.atan2(toward.z, toward.x) + (random.nextFloat() - 0.5f)
					: random.nextFloat() * Mth.TWO_PI;
			hop(Mth.cos(angle), Mth.sin(angle), 0.08 + random.nextFloat() * 0.1);
		} else if (roll < 0.85f) {
			caw(level, false);
		} else {
			setYRot(getYRot() + (random.nextFloat() - 0.5f) * 120.0f);
			yBodyRot = getYRot();
		}
	}

	private void peck(ServerLevel level) {
		level.broadcastEntityEvent(this, EVENT_PECK);
		playSound(Crows.PECK, 0.5f, 0.9f + random.nextFloat() * 0.3f);
	}

	private void caw(ServerLevel level, boolean alarm) {
		if (isSleeping()) {
			return;
		}
		level.broadcastEntityEvent(this, EVENT_CAW);
		playSound(alarm ? Crows.ALARM : Crows.CAW, alarm ? 1.4f : 1.1f, getVoicePitch());
	}

	private void eat(ServerLevel level, ItemStack food) {
		eatParticles(level, food);
		playSound(SoundEvents.PARROT_EAT, 0.8f, 0.9f + random.nextFloat() * 0.2f);
	}

	private void eatParticles(ServerLevel level, ItemStack food) {
		if (!food.isEmpty()) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(food)), getX(), getY() + 0.25, getZ(), 5, 0.1, 0.1, 0.1, 0.05);
		}
	}

	@Override
	public void playAmbientSound() {
		if (level() instanceof ServerLevel level) {
			caw(level, false);
		}
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	// ------------------------------------------------------------------------------------------------ getting hurt

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive()) {
			Entity attacker = source.getEntity();
			leader().flock.scatter(level, attacker != null ? attacker.position() : position(), 2.0);
			if (task != Task.DEFEND && task != Task.STASH && task != Task.BUILD) {
				clearTask();
			}
			if (!isFlying()) {
				takeOff(level);
			}
		}
		return hurt;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double fallY, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean isPushable() {
		return !isFlying() && super.isPushable();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return Crows.CAW;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Crows.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Crows.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.PARROT_STEP, 0.15f, 1.0f);
	}

	@Override
	public float getVoicePitch() {
		return 0.92f + random.nextFloat() * 0.16f;
	}

	// ------------------------------------------------------------------------------------------------ client

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_PECK) {
			peckTicks = 8;
		} else if (id == EVENT_CAW) {
			cawTicks = 12;
		} else {
			super.handleEntityEvent(id);
		}
	}

	private void clientTick() {
		if (peckTicks > 0) {
			peckTicks--;
		}
		if (cawTicks > 0) {
			cawTicks--;
		}
		flyAmountO = flyAmount;
		// Wings half out on a hop.
		flyAmount = Mth.approach(flyAmount, isFlying() ? 1.0f : onGround() ? 0.0f : 0.35f, isFlying() ? 0.25f : 0.15f);
		flapO = flap;
		double climb = getY() - yo;
		// Beating hard to climb, steady in level flight, barely at all gliding down; a quick flutter on a hop.
		float target = isFlying() ? (climb > 0.02 ? 1.0f : climb < -0.05 ? 0.25f : 0.7f) : onGround() ? 0.0f : 0.5f;
		flapSpeed += (target - flapSpeed) * 0.2f;
		flap += 0.25f + flapSpeed * 0.75f;
		// Nose up climbing, down diving.
		flightPitchO = flightPitch;
		double run = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
		float pitch = isFlying() ? (float) Mth.clamp(-Mth.atan2(climb, Math.max(run, 0.08)) * 0.8, -0.6, 0.6) : 0.0f;
		flightPitch += (pitch - flightPitch) * 0.25f;
	}

	public float flightPitch(float partialTicks) {
		return Mth.lerp(partialTicks, flightPitchO, flightPitch);
	}

	/** 0 sitting to 1 wings out in flight. */
	public float flyAmount(float partialTicks) {
		return Mth.lerp(partialTicks, flyAmountO, flyAmount);
	}

	public float flap(float partialTicks) {
		return Mth.lerp(partialTicks, flapO, flap);
	}

	public float flapStrength() {
		return flapSpeed;
	}

	/** Through a peck, 0 to 1. */
	public float peck(float partialTicks) {
		return peckTicks <= 0 ? 0.0f : 1.0f - (peckTicks - partialTicks) / 8.0f;
	}

	/** Through a caw, 0 to 1. */
	public float caw(float partialTicks) {
		return cawTicks <= 0 ? 0.0f : 1.0f - (cawTicks - partialTicks) / 12.0f;
	}

	// ------------------------------------------------------------------------------------------------ saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("leader", UUIDUtil.CODEC, leaderId);
		output.storeNullable("nest", BlockPos.CODEC, nest);
		output.storeNullable("home", BlockPos.CODEC, home);
		output.putBoolean("flying", isFlying());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		leaderId = input.read("leader", UUIDUtil.CODEC).orElse(null);
		nest = input.read("nest", BlockPos.CODEC).orElse(null);
		home = input.read("home", BlockPos.CODEC).orElse(null);
		setFlag(FLAG_FLYING, input.getBooleanOr("flying", false));
	}
}
