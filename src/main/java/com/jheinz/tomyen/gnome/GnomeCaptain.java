package com.jheinz.tomyen.gnome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fae.Feywild;
import com.jheinz.tomyen.scarecrow.CropGuard;

/**
 * The Gnome Captain: a tall red hat, a gold-trimmed coat and a tiny pitchfork. It's the only gnome that spawns
 * naturally; it musters a squad of {@value #MIN_TROOPS}-{@value #MAX_TROOPS} gnomes round itself, and its home is the
 * fae ring it came out of (any Fae Mushroom or Fae Grass within {@value #HOME_SEARCH} blocks), or else the spot it
 * appeared on.
 *
 * <p>Its orders, which its troops follow:
 * <ul>
 * <li>MUSTER: at home, the squad huddled round it. Troops stash anything they carried back. Every few seconds it
 * casts about for a farm: the nearest ripe crop no scarecrow guards ({@link CropRaids#findFarm}).
 * <li>MARCH: off to the farm, drumming, the squad behind it in a column or a V (its own, for life).
 * <li>RAID: at the farm, each empty-handed gnome pulls up a crop of its own (the captain hands them out, so no two go
 * for the same one) until every gnome has an armful, the farm's picked clean, a scarecrow goes up, or it's taken too
 * long.
 * <li>RETURN: back home in formation, with the loot.
 * <li>SCATTER, REGROUP, FIGHT: when any of the squad is hit, they scatter, then huddle back round the captain, then
 * all go for whoever hit them; when that's done (or the foe's gone), they get back to what they were doing.
 * </ul>
 * If the captain dies (or is shattered as a statue) the squad breaks and flees. Spawn rules: night (any time in the
 * Feywild), open sky, little block light, and no other captain within {@value #CROWD_RADIUS} blocks.
 */
public class GnomeCaptain extends Gnome {
	static final int MIN_TROOPS = 3;
	static final int MAX_TROOPS = 7;
	private static final int HOME_SEARCH = 24;
	private static final double CROWD_RADIUS = 64.0;
	/** How far from the farm's first crop the squad picks. */
	private static final int RAID_RADIUS = 6;
	private static final double MARCH_SPEED = 0.6;
	private static final double SQUAD_RANGE = 32.0;

	public enum Order {
		MUSTER, MARCH, RAID, RETURN, SCATTER, REGROUP, FIGHT
	}

	public enum Formation {
		COLUMN, WEDGE
	}

	private Order order = Order.MUSTER;
	private Order resume = Order.MUSTER;
	private int orderTicks;
	private @Nullable BlockPos farm;
	private @Nullable LivingEntity foe;
	private long lastScatter = -1000L;
	private Formation formation = Formation.COLUMN;
	private int searchCooldown = 40;
	private final List<Gnome> members = new ArrayList<>();
	private final List<BlockPos> ripe = new ArrayList<>();
	private final Set<BlockPos> claimed = new HashSet<>();

	public GnomeCaptain(EntityType<? extends GnomeCaptain> type, Level level) {
		super(type, level);
		xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Gnome.createAttributes()
				.add(Attributes.MAX_HEALTH, 16.0)
				.add(Attributes.ATTACK_DAMAGE, 2.5)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new FallBackGoal());
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(3, new CommandGoal());
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new SquadTargetGoal(this));
	}

	/**
	 * Night (any time in the Feywild), open sky, no torchlight, and no other captain about. Spawners and eggs skip all
	 * of that.
	 */
	public static boolean checkSpawnRules(EntityType<GnomeCaptain> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return checkMobSpawnRules(type, level, reason, pos, random);
		}
		ServerLevel server = level.getLevel();
		return level.getDifficulty() != Difficulty.PEACEFUL && (Feywild.is(server) || server.isDarkOutside())
				&& checkMobSpawnRules(type, level, reason, pos, random) && level.canSeeSky(pos)
				&& level.getBrightness(LightLayer.BLOCK, pos) <= 7
				&& level.getEntitiesOfClass(GnomeCaptain.class, new AABB(pos).inflate(CROWD_RADIUS)).isEmpty();
	}

	/** Musters its squad round it (unless it's being converted or brought through a portal) and finds its home. */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		home = findHome(level, blockPosition());
		formation = random.nextBoolean() ? Formation.COLUMN : Formation.WEDGE;
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		if (reason != EntitySpawnReason.CONVERSION && reason != EntitySpawnReason.DIMENSION_TRAVEL) {
			int troops = MIN_TROOPS + random.nextInt(MAX_TROOPS - MIN_TROOPS + 1);
			for (int i = 0; i < troops; i++) {
				Gnome gnome = Gnomes.GNOME.create(level.getLevel(), EntitySpawnReason.MOB_SUMMONED);
				if (gnome == null) {
					continue;
				}
				float angle = i * Mth.TWO_PI / troops + random.nextFloat() * 0.4f;
				double r = 1.2 + random.nextDouble() * 0.6;
				gnome.snapTo(getX() + Mth.cos(angle) * r, getY(), getZ() + Mth.sin(angle) * r, getYRot(), 0.0f);
				if (!level.noCollision(gnome)) {
					gnome.snapTo(getX(), getY(), getZ(), getYRot(), 0.0f);
				}
				gnome.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
				gnome.join(this);
				level.addFreshEntity(gnome);
				members.add(gnome);
			}
		}
		return data;
	}

	/** The nearest Fae Mushroom or Fae Grass (the {@code tomyen:gnome_homes} tag) nearby, or here. */
	private static BlockPos findHome(ServerLevelAccessor level, BlockPos origin) {
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-HOME_SEARCH, -6, -HOME_SEARCH), origin.offset(HOME_SEARCH, 6, HOME_SEARCH))) {
			if (level.getBlockState(pos).is(Gnomes.HOMES) && (best == null || pos.distSqr(origin) < best.distSqr(origin))) {
				best = pos.immutable();
			}
		}
		return best == null ? origin : best.above();
	}

	// ------------------------------------------------------------------------------------------------ the squad

	public Order order() {
		return order;
	}

	public @Nullable LivingEntity foe() {
		return foe;
	}

	public boolean isMarching() {
		return order == Order.MARCH || order == Order.RETURN;
	}

	boolean hasRoom() {
		return members.size() < MAX_TROOPS;
	}

	void refreshMembers(ServerLevel level) {
		members.clear();
		members.addAll(level.getEntitiesOfClass(Gnome.class, getBoundingBox().inflate(SQUAD_RANGE),
				gnome -> gnome != this && gnome.isAlive() && gnome.follows(this)));
		members.sort(Comparator.comparingInt(Gnome::getId));
	}

	private void setOrder(Order next) {
		if (order == Order.RAID) {
			claimed.clear();
			ripe.clear();
		}
		order = next;
		orderTicks = 0;
	}

	private boolean anyCarrying() {
		return members.stream().anyMatch(Gnome::isCarrying);
	}

	/** Where in the formation this gnome belongs. */
	Vec3 formationSpot(Gnome gnome) {
		int slot = Math.max(0, members.indexOf(gnome));
		int count = Math.max(1, members.size());
		if (!isMarching()) {
			// A huddle round the captain.
			float angle = slot * Mth.TWO_PI / count + getId() * 0.7f;
			double r = 1.3 + (count > 5 ? 0.35 : 0.0);
			return new Vec3(getX() + Mth.cos(angle) * r, getY(), getZ() + Mth.sin(angle) * r);
		}
		double side;
		double back;
		int rank = slot / 2 + 1;
		int hand = slot % 2 == 0 ? -1 : 1;
		if (formation == Formation.COLUMN) {
			side = hand * 0.6;
			back = rank * 1.0;
		} else {
			side = hand * rank * 0.8;
			back = rank * 0.9;
		}
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		// Its right hand: forward turned a quarter clockwise, looking down.
		double rx = -fz;
		double rz = fx;
		return new Vec3(getX() + rx * side - fx * back, getY(), getZ() + rz * side - fz * back);
	}

	/** Hands out the nearest ripe crop at the farm that nobody's gone for yet, or null if there isn't one. */
	@Nullable BlockPos claimCrop(Gnome gnome) {
		BlockPos best = null;
		for (BlockPos pos : ripe) {
			if (!claimed.contains(pos) && CropRaids.isRipe(level().getBlockState(pos))
					&& (best == null || gnome.distanceToSqr(Vec3.atCenterOf(pos)) < gnome.distanceToSqr(Vec3.atCenterOf(best)))) {
				best = pos;
			}
		}
		if (best != null) {
			claimed.add(best);
		}
		return best;
	}

	void releaseCrop(BlockPos pos) {
		claimed.remove(pos);
	}

	/** One of the squad (or the captain) was hit by this. */
	@Override
	protected void alarm(LivingEntity attacker) {
		if (!attacker.isAlive() || attacker instanceof Gnome) {
			return;
		}
		long now = level().getGameTime();
		foe = attacker;
		if (order == Order.SCATTER || (order == Order.FIGHT || order == Order.REGROUP) && now - lastScatter < 200) {
			return;
		}
		if (order != Order.FIGHT && order != Order.REGROUP) {
			resume = anyCarrying() ? Order.RETURN : farm != null ? Order.MARCH : Order.MUSTER;
		}
		lastScatter = now;
		setOrder(Order.SCATTER);
		playSound(Gnomes.SCATTER, 1.0f, 0.9f);
		for (Gnome gnome : members) {
			gnome.playSound(Gnomes.SCATTER, 0.7f, 1.0f + random.nextFloat() * 0.3f);
		}
	}

	private boolean foeValid() {
		return foe != null && foe.isAlive() && distanceToSqr(foe) < 16.0 * 16.0 && canAttack(foe)
				&& !(foe instanceof Player player && (player.isSpectator() || player.isCreative()));
	}

	/** It's fallen: the squad breaks. */
	@Override
	protected void onLost(ServerLevel level) {
		refreshMembers(level);
		for (Gnome gnome : members) {
			gnome.rout();
		}
		if (!members.isEmpty()) {
			level.playSound(null, getX(), getY(), getZ(), Gnomes.ROUT, getSoundSource(), 1.0f, 1.0f);
		}
		members.clear();
	}

	@Override
	protected void tickSquad(ServerLevel level) {
		orderTicks++;
		if (tickCount % 20 == 0) {
			refreshMembers(level);
		}
		switch (order) {
			case MUSTER -> {
				if (home != null) {
					for (Gnome gnome : members) {
						if (gnome.isCarrying() && gnome.blockPosition().closerThan(home, 6.0)) {
							gnome.stashCarried(level);
						}
					}
				}
				if (--searchCooldown <= 0) {
					searchCooldown = 100 + random.nextInt(60);
					if (CropRaids.mayRaid(level)) {
						farm = CropRaids.findFarm(level, blockPosition());
						if (farm != null) {
							setOrder(Order.MARCH);
							playSound(Gnomes.CAPTAIN_ORDER, 1.0f, 1.0f);
						}
					}
				}
			}
			case MARCH -> {
				if (orderTicks % 20 == 0 && (farm == null || !CropRaids.isRipe(level.getBlockState(farm)) || CropGuard.scaresGnomes(level, farm))) {
					farm = CropRaids.findFarm(level, blockPosition());
					if (farm == null) {
						setOrder(Order.RETURN);
						break;
					}
				}
				if (farm != null && distanceToSqr(Vec3.atBottomCenterOf(farm)) < 3.0 * 3.0) {
					setOrder(Order.RAID);
					ripe.addAll(CropRaids.ripeAround(level, farm, RAID_RADIUS));
					playSound(Gnomes.CAPTAIN_ORDER, 1.0f, 1.3f);
				} else if (orderTicks > 1200) {
					setOrder(Order.RETURN);
				}
			}
			case RAID -> {
				if (orderTicks % 20 == 0) {
					ripe.removeIf(pos -> !CropRaids.isRipe(level.getBlockState(pos)));
					boolean guarded = farm == null || CropGuard.scaresGnomes(level, farm);
					boolean full = !members.isEmpty() && members.stream().allMatch(Gnome::isCarrying);
					if (guarded || full || orderTicks > 600 || ripe.isEmpty() && claimed.isEmpty()) {
						farm = null;
						setOrder(Order.RETURN);
						playSound(Gnomes.CAPTAIN_ORDER, 1.0f, 0.8f);
					}
				}
			}
			case RETURN -> {
				if (home == null || blockPosition().closerThan(home, 3.0)) {
					setOrder(Order.MUSTER);
				} else if (orderTicks > 1600) {
					// Lost the way: make a new home here.
					home = blockPosition();
					setOrder(Order.MUSTER);
				}
			}
			case SCATTER -> {
				if (orderTicks >= 50) {
					setOrder(Order.REGROUP);
				}
			}
			case REGROUP -> {
				if (orderTicks >= 50) {
					setOrder(foeValid() ? Order.FIGHT : resume);
				}
			}
			case FIGHT -> {
				if (!foeValid() || orderTicks > 400) {
					foe = null;
					setOrder(resume);
				}
			}
		}
		if (isMarching() && tickCount % 10 == 0 && getDeltaMovement().horizontalDistanceSqr() > 1.0e-4) {
			playSound(Gnomes.CAPTAIN_DRUM, 0.5f, tickCount % 20 == 0 ? 1.0f : 1.2f);
		}
	}

	@Override
	protected void updateMode() {
		if (order == Order.SCATTER) {
			setMode(MODE_PANIC);
		} else if (isMarching() && getDeltaMovement().horizontalDistanceSqr() > 1.0e-4) {
			setMode(MODE_MARCHING);
		} else {
			setMode(MODE_IDLE);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("formation", formation.name());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		formation = "WEDGE".equals(input.getStringOr("formation", "COLUMN")) ? Formation.WEDGE : Formation.COLUMN;
	}

	// ------------------------------------------------------------------------------------------------ goals

	/** Leads the squad: home to muster, to the farm, back home; stands its ground while they raid and regroup. */
	private class CommandGoal extends Goal {
		private int repath;

		CommandGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public void tick() {
			BlockPos to = switch (order) {
				case MARCH -> farm;
				case RETURN -> home;
				case MUSTER -> home != null && !blockPosition().closerThan(home, 4.0) ? home : null;
				default -> null;
			};
			if (to == null) {
				if (!getNavigation().isDone()) {
					getNavigation().stop();
				}
				return;
			}
			if (--repath <= 0 || getNavigation().isDone()) {
				repath = 20;
				// The squad keeps up best at a steady march; a captain that's left them behind waits.
				boolean straggling = members.stream().anyMatch(gnome -> gnome.distanceToSqr(GnomeCaptain.this) > 8.0 * 8.0);
				if (straggling) {
					getNavigation().stop();
				} else {
					getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, MARCH_SPEED);
				}
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** While the squad scatters, the captain backs off a few blocks from whoever hit them. */
	private class FallBackGoal extends Goal {
		FallBackGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return order == Order.SCATTER && foe != null;
		}

		@Override
		public void start() {
			Vec3 to = DefaultRandomPos.getPosAway(GnomeCaptain.this, 5, 3, foe.position());
			if (to != null) {
				getNavigation().moveTo(to.x, to.y, to.z, 1.2);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return order == Order.SCATTER && getNavigation().isInProgress();
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}
}
