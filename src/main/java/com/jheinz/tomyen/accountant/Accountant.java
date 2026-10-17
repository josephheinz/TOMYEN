package com.jheinz.tomyen.accountant;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.bank.Debt;

/**
 * The Accountant: a slime in a suit, glasses, tie and briefcase, who works for the bank. Neutral and harmless: he never
 * hurts anyone. He hops about in small, prim bounces near the ATM he was sent to (his home).
 * <ul>
 * <li>Use him to open the loan office (see {@link LoanMenu}, {@link Loans}).</li>
 * <li>Now and then he hops up to a player who owes the bank, adjusts his glasses and audits them: what they owe and the
 * time left, in chat. If they're overdue (in collections) he helps himself to the emeralds in their inventory toward the
 * debt. Then he hops off about his business.</li>
 * <li>Kill him and the bank bills you (see {@link Loans#penalize}); he drops the Ledger and paperwork, and splits into two
 * to four Junior Accountants, who are only interns: they won't lend or audit, and drop slime and scrap paper.</li>
 * </ul>
 * The audit's progress is synced so clients can play his pose: the arm stretching up to push the glasses back, then a
 * nod over the figures.
 */
public class Accountant extends AbstractCubeMob {
	/** His size (a block across); his juniors are 1. */
	public static final int SIZE = 2;
	/** How long an audit takes, in ticks: the glasses, the reading out, the reckoning. */
	public static final int AUDIT_TICKS = 60;
	/** When in the audit the figures are read out (the glasses are up), and when the overdue are relieved of emeralds. */
	private static final int READ_AT = AUDIT_TICKS - 18;
	private static final int SEIZE_AT = AUDIT_TICKS - 40;
	/** Before auditing the same player again: two minutes, or one if they're overdue. */
	private static final int AUDIT_COOLDOWN = 2400;
	private static final int OVERDUE_COOLDOWN = 1200;
	private static final double AUDIT_RANGE = 12.0;
	private static final double AUDIT_REACH = 2.5;
	/** He keeps within this of his ATM. */
	private static final double HOME_RANGE = 14.0;
	/** Ticks remaining in the current audit (0 for none), synced for the pose. */
	private static final EntityDataAccessor<Integer> AUDIT = SynchedEntityData.defineId(Accountant.class, EntityDataSerializers.INT);

	private @Nullable BlockPos home;
	/** Game time each player was last audited (not saved: a restart lets him have another look). */
	private final Map<UUID, Long> audited = new HashMap<>();
	/** Ticks before he'll mention being hit again. */
	private int noteCooldown;

	public Accountant(EntityType<? extends Accountant> type, Level level) {
		super(type, level);
		xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AgeableMob.createMobAttributes().add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(AUDIT, 0);
	}

	@Override
	protected void addBehaviourGoals() {
		goalSelector.addGoal(2, new AuditGoal());
		goalSelector.addGoal(3, new GoHomeGoal());
	}

	@Override
	protected void addTargetingGoals() {
	}

	public void setHome(@Nullable BlockPos home) {
		this.home = home;
	}

	/** Progress through the audit pose, 0 to 1, or 0 when he isn't auditing. */
	public float auditProgress(float partialTicks) {
		int left = entityData.get(AUDIT);
		return left <= 0 ? 0.0f : Mth.clamp((AUDIT_TICKS - left + partialTicks) / AUDIT_TICKS, 0.0f, 1.0f);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			int audit = entityData.get(AUDIT);
			if (audit > 0) {
				entityData.set(AUDIT, audit - 1);
			}
			if (noteCooldown > 0) {
				noteCooldown--;
			}
		}
	}

	// --- Size, health and movement -----------------------------------------------------------------------------------

	@Override
	public void setSize(int size, boolean updateHealth) {
		super.setSize(size, updateHealth);
		// Prim and unhurried, slower than a slime.
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2 + 0.05 * getSize());
	}

	@Override
	protected void setCubeMobHealth(int actualSize) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(actualSize * actualSize * 4);
	}

	@Override
	protected void setSpawnSize(ServerLevelAccessor level, DifficultyInstance difficulty) {
		setSize(SIZE, true);
	}

	@Override
	protected boolean canBeABaby() {
		return false;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
			@Nullable SpawnGroupData groupData) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason,
				groupData == null ? new AgeableMob.AgeableMobGroupData(false) : groupData);
		setPersistenceRequired();
		return data;
	}

	/** Small bounces. */
	@Override
	protected float getJumpPower(float multiplier) {
		return super.getJumpPower(multiplier) * 0.7f;
	}

	/** A steady, regular hop. */
	@Override
	protected int getJumpDelay() {
		return 22 + random.nextInt(8);
	}

	/** He never hurts anyone. */
	@Override
	protected boolean canDealDamage() {
		return false;
	}

	/** Juniors wander off and despawn like any mob; the Accountant stays at his post. */
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return isTiny();
	}

	@Override
	protected Component getTypeName() {
		return isTiny() ? Component.translatable("entity.tomyen.junior_accountant") : super.getTypeName();
	}

	// --- Interaction, harm and death ---------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isAlive() || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			if (isTiny()) {
				server.sendSystemMessage(Component.translatable("message.tomyen.accountant.intern").withStyle(ChatFormatting.GRAY));
			} else if (entityData.get(AUDIT) <= 0) {
				server.openMenu(new SimpleMenuProvider((containerId, inventory, user) -> new LoanMenu(containerId, inventory, this),
						Component.translatable("container.tomyen.loan_office")));
				playSound(Accountants.PAPERS, 0.8f, 1.0f);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && noteCooldown <= 0 && source.getEntity() instanceof ServerPlayer player) {
			noteCooldown = 100;
			player.sendOverlayMessage(Loans.voice(Component.translatable("message.tomyen.accountant.assaulted")));
			playSound(Accountants.SCRIBBLE, 0.8f, 1.0f);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer player) {
			Loans.penalize(player, isTiny());
		}
	}

	// --- Sounds and particles ----------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getJumpSound() {
		return Accountants.HOP;
	}

	@Override
	protected SoundEvent getSquishSound() {
		return Accountants.SQUISH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Accountants.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Accountants.DEATH;
	}

	@Override
	public float getSoundPitch() {
		return (0.95f + random.nextFloat() * 0.1f) * (isTiny() ? 1.25f : 1.0f);
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.NEUTRAL;
	}

	@Override
	protected ParticleOptions getParticleType() {
		return ParticleTypes.ITEM_SLIME;
	}

	// --- Saving ------------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (home != null) {
			output.store("Home", BlockPos.CODEC, home);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		home = input.read("Home", BlockPos.CODEC).orElse(null);
	}

	// --- Goals -------------------------------------------------------------------------------------------------------

	private void face(Vec3 target) {
		if (getMoveControl() instanceof CubeMobMoveControl<?> control) {
			float yaw = (float) (Mth.atan2(target.z - getZ(), target.x - getX()) * Mth.RAD_TO_DEG) - 90.0f;
			control.setDirection(yaw, false);
		}
	}

	private void hop(double speed) {
		if (getMoveControl() instanceof CubeMobMoveControl<?> control) {
			control.setWantedMovement(speed);
		}
	}

	/** Back toward his ATM when he's strayed. */
	private class GoHomeGoal extends Goal {
		GoHomeGoal() {
			setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return home != null && onGround() && Vec3.atCenterOf(home).distanceToSqr(position()) > HOME_RANGE * HOME_RANGE;
		}

		@Override
		public void tick() {
			if (home != null) {
				face(Vec3.atCenterOf(home));
			}
		}
	}

	/**
	 * Hop up to a player who owes, stop in front of them, audit (the pose plays on clients), read the figures out, take
	 * what the overdue have on them, then hop off the other way for a few seconds.
	 */
	private class AuditGoal extends Goal {
		private static final int APPROACH_TIMEOUT = 200;
		private static final int LEAVE_TICKS = 60;

		private @Nullable ServerPlayer target;
		private int timer;
		private boolean auditing;
		/** The audit's done: he's hopping off (for {@link #leaving} more ticks). */
		private boolean finished;
		private int leaving;
		private float leaveYaw;

		AuditGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (isTiny() || isInLiquid() || !(level() instanceof ServerLevel level) || random.nextInt(reducedTickDelay(80)) != 0) {
				return false;
			}
			long now = level.getGameTime();
			ServerPlayer best = null;
			double bestDistance = AUDIT_RANGE * AUDIT_RANGE;
			for (ServerPlayer player : level.players()) {
				Debt debt = Debt.of(player);
				if (debt == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
					continue;
				}
				Long last = audited.get(player.getUUID());
				int cooldown = debt.hunting() ? OVERDUE_COOLDOWN : AUDIT_COOLDOWN;
				double distance = distanceToSqr(player);
				if ((last == null || now - last >= cooldown) && distance < bestDistance && hasLineOfSight(player)) {
					best = player;
					bestDistance = distance;
				}
			}
			target = best;
			return best != null;
		}

		@Override
		public void start() {
			timer = 0;
			auditing = false;
			finished = false;
			leaving = 0;
		}

		@Override
		public boolean canContinueToUse() {
			if (finished) {
				return leaving > 0;
			}
			return target != null && target.isAlive() && target.level() == level() && Debt.of(target) != null
					&& (auditing || timer < APPROACH_TIMEOUT && distanceToSqr(target) < (AUDIT_RANGE + 4) * (AUDIT_RANGE + 4));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			timer++;
			if (leaving > 0) {
				leaving--;
				if (getMoveControl() instanceof CubeMobMoveControl<?> control) {
					control.setDirection(leaveYaw, false);
				}
				hop(1.0);
				return;
			}
			if (target == null) {
				return;
			}
			face(target.position());
			if (!auditing) {
				if (distanceToSqr(target) > AUDIT_REACH * AUDIT_REACH) {
					hop(1.0);
				} else if (onGround()) {
					auditing = true;
					entityData.set(AUDIT, AUDIT_TICKS);
					playSound(Accountants.PAPERS, 0.9f, 1.0f);
				}
				return;
			}
			// Standing still in front of them (no hop) through the audit.
			int left = entityData.get(AUDIT);
			if (left == READ_AT) {
				Loans.audit(target, random);
				playSound(Accountants.SCRIBBLE, 0.9f, 1.0f);
			} else if (left == SEIZE_AT && Debt.inCollections(target)) {
				Loans.seize(target, Accountant.this);
			} else if (left <= 0) {
				finish();
			}
		}

		private void finish() {
			if (target != null) {
				audited.put(target.getUUID(), level().getGameTime());
				leaveYaw = (float) (Mth.atan2(getZ() - target.getZ(), getX() - target.getX()) * Mth.RAD_TO_DEG) - 90.0f
						+ (random.nextFloat() - 0.5f) * 60.0f;
			}
			auditing = false;
			finished = true;
			leaving = LEAVE_TICKS;
		}

		@Override
		public void stop() {
			// Cut short (they ran off, paid up, or he was knocked about): drop the pose and note the attempt.
			if (target != null && (auditing || finished)) {
				audited.put(target.getUUID(), level().getGameTime());
			}
			if (auditing) {
				entityData.set(AUDIT, 0);
			}
			target = null;
			auditing = false;
			leaving = 0;
		}
	}
}
