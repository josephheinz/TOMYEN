package com.jheinz.mymod.banana;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Big Banana: a three-block banana on little legs, wandering the jungle. Neutral, like a polar bear: it minds its
 * own business, turns to look at (and bounces happily for) a player holding a banana or a peel, and only fights back
 * when hit, with a big, slow, open-handed slap that does little harm but sends whoever it lands on flying. Any other
 * Big Bananas nearby join in. It calms down after {@value #CALM_TICKS} ticks without being hit again.
 */
public class BigBanana extends PathfinderMob {
	/** Sent to clients when it bounces for joy. */
	public static final byte HAPPY_EVENT = 110;
	/** How long a happy bounce lasts (client side). */
	public static final int HAPPY_TICKS = 20;
	/** It forgets a grudge after this long without being hit again. */
	private static final int CALM_TICKS = 400;
	/** It notices players holding something banana-ish this close. */
	private static final double ADMIRE_RANGE = 8.0;
	/** A slap is twice the length of an ordinary swing: a big wind-up. */
	private static final SwingAnimation SLAP = new SwingAnimation(SwingAnimationType.WHACK, 12);

	private int angerTicks;
	// Client side: ticks left of a happy bounce.
	private int happyTicks;

	public BigBanana(EntityType<? extends BigBanana> type, Level level) {
		super(type, level);
		xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.ATTACK_KNOCKBACK, 2.5)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** On grass, dirt or moss in the light, and (for natural spawns) only by day. */
	public static boolean checkSpawnRules(EntityType<BigBanana> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		return level.getBlockState(pos.below()).is(BlockTags.DIRT) && level.getRawBrightness(pos, 0) > 8
				&& level.getLevel().isBrightOutside();
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true));
		goalSelector.addGoal(3, new AdmireGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity) {
			angerTicks = CALM_TICKS;
		}
		return hurt;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target);
		if (target != null && angerTicks <= 0) {
			// Alerted by a neighbour that was hit: as cross as it is.
			angerTicks = CALM_TICKS;
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide() && getTarget() != null && --angerTicks <= 0) {
			setTarget(null);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && happyTicks > 0) {
			happyTicks--;
		}
	}

	/** Bounces for joy: a hop, a boop and a few notes (also to a Trombone's tune). */
	public void bounce() {
		if (level() instanceof ServerLevel level) {
			jumpFromGround();
			level.broadcastEntityEvent(this, HAPPY_EVENT);
			playSound(Bananas.BOOP, 1.0f, 0.9f + random.nextFloat() * 0.3f);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == HAPPY_EVENT) {
			happyTicks = HAPPY_TICKS;
			for (int i = 0; i < 3; i++) {
				level().addParticle(ParticleTypes.NOTE, getX() + (random.nextDouble() - 0.5), getY() + 3.1, getZ() + (random.nextDouble() - 0.5),
						random.nextInt(25) / 24.0, 0.0, 0.0);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** How far through a happy bounce it is, 0 to 1 (0 when not bouncing; client side). */
	public float happyProgress(float partialTicks) {
		return happyTicks > 0 ? Mth.clamp((HAPPY_TICKS - happyTicks + partialTicks) / HAPPY_TICKS, 0.0f, 1.0f) : 0.0f;
	}

	@Override
	public boolean swing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity) {
		return super.swing(hand, SLAP, sendToSwingingEntity);
	}

	/** The slap sends its target up as well as back. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			target.push(0.0, 0.3, 0.0);
			target.needsSync = true;
		}
		return hit;
	}

	@Override
	protected void playAttackSound() {
		playSound(Bananas.SLAP, 1.2f, 0.9f + random.nextFloat() * 0.2f);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(Bananas.STEP, 0.5f, 1.0f);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return Bananas.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Bananas.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Bananas.DEATH;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.NEUTRAL;
	}

	// Rare enough to keep, as animals are.
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/**
	 * Turns to look at the nearest player holding a banana or a peel (see {@link Bananas#MAKES_BIG_BANANA_HAPPY}),
	 * stops to admire it, and bounces happily every couple of seconds.
	 */
	private static class AdmireGoal extends Goal {
		private final BigBanana banana;
		private @Nullable Player player;
		private int nextBounce;

		AdmireGoal(BigBanana banana) {
			this.banana = banana;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private static boolean holdsBanana(Player player) {
			return player.getMainHandItem().is(Bananas.MAKES_BIG_BANANA_HAPPY) || player.getOffhandItem().is(Bananas.MAKES_BIG_BANANA_HAPPY);
		}

		private @Nullable Player find() {
			Player best = null;
			double bestDistance = ADMIRE_RANGE * ADMIRE_RANGE;
			for (Player candidate : banana.level().players()) {
				double distance = candidate.distanceToSqr(banana);
				if (distance < bestDistance && !candidate.isSpectator() && holdsBanana(candidate) && banana.hasLineOfSight(candidate)) {
					best = candidate;
					bestDistance = distance;
				}
			}
			return best;
		}

		@Override
		public boolean canUse() {
			if (banana.getTarget() != null || banana.tickCount % 10 != 0) {
				return false;
			}
			player = find();
			return player != null;
		}

		@Override
		public boolean canContinueToUse() {
			return player != null && player.isAlive() && banana.getTarget() == null && holdsBanana(player)
					&& player.distanceToSqr(banana) < (ADMIRE_RANGE + 2.0) * (ADMIRE_RANGE + 2.0);
		}

		@Override
		public void start() {
			banana.getNavigation().stop();
			nextBounce = 10;
		}

		@Override
		public void stop() {
			player = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (player == null) {
				return;
			}
			banana.getLookControl().setLookAt(player, 30.0f, 30.0f);
			if (--nextBounce <= 0 && banana.onGround()) {
				banana.bounce();
				nextBounce = 40 + banana.getRandom().nextInt(40);
			}
		}
	}
}
