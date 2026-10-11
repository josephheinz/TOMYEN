package com.jheinz.mymod.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.jheinz.mymod.ModSounds;

/**
 * A sentient shopping cart. It rolls aimlessly around dark forests and deserts, day or night, until a
 * player comes within {@link #NOTICE_RANGE} blocks; then it gives chase, leaping and biting like a wolf
 * (its lower front panel is a jaw). It won't let go until the player gets {@code FOLLOW_RANGE} away.
 */
public class ShoppingCart extends Monster {
	/** How close a player has to get before the cart notices them. */
	private static final double NOTICE_RANGE = 8.0;
	/** Carts are left alone by the light, so cap how many crowd one area or they'd fill the monster cap by day. */
	private static final int MAX_NEARBY = 3;
	private static final double CROWD_RADIUS = 48.0;

	// Client side: how far into its pounce pose it is (0 rolling, 1 nose up with its jaw wide open).
	private float pounce;
	private float pounceO;

	public ShoppingCart(EntityType<? extends ShoppingCart> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 16.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new FloatGoal(this));
		goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4f));
		goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.3, true));
		// Always on the move: a fresh spot to roll to every second or so, whatever the light.
		goalSelector.addGoal(8, new RandomStrollGoal(this, 0.8, 20, false));
		goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new NoticePlayerGoal(this));
	}

	/** Spots players only within NOTICE_RANGE, but keeps chasing out to the full follow range. */
	private static class NoticePlayerGoal extends NearestAttackableTargetGoal<Player> {
		private boolean chasing;

		NoticePlayerGoal(ShoppingCart cart) {
			super(cart, Player.class, true);
		}

		@Override
		protected double getFollowDistance() {
			return chasing ? super.getFollowDistance() : NOTICE_RANGE;
		}

		@Override
		public void start() {
			chasing = true;
			super.start();
		}

		@Override
		public void stop() {
			chasing = false;
			super.stop();
		}
	}

	/** Spawns on the ground in any light, as long as the area isn't already full of carts. */
	public static boolean checkSpawnRules(EntityType<ShoppingCart> type, ServerLevelAccessor level, EntitySpawnReason reason,
			BlockPos pos, RandomSource random) {
		if (!checkMobSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		BlockState below = level.getBlockState(pos.below());
		if (!below.is(BlockTags.SAND) && !below.is(BlockTags.DIRT)) {
			return false;
		}
		return level.getEntitiesOfClass(ShoppingCart.class, new AABB(pos).inflate(CROWD_RADIUS, 16.0, CROWD_RADIUS)).size() < MAX_NEARBY;
	}

	// Daylight neither stops it wandering nor wears it down.
	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		return 0.0f;
	}

	@Override
	protected void updateNoActionTime() {
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			playSound(ModSounds.SHOPPING_CART_CHOMP, 1.0f, 0.9f + random.nextFloat() * 0.3f);
		}
		return hit;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return isAggressive() ? ModSounds.SHOPPING_CART_GROWL : ModSounds.SHOPPING_CART_RATTLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SHOPPING_CART_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.SHOPPING_CART_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(ModSounds.SHOPPING_CART_ROLL, 0.3f, 0.9f + random.nextFloat() * 0.2f);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			pounceO = pounce;
			pounce = Mth.approach(pounce, isPouncing() ? 1.0f : 0.0f, isPouncing() ? 0.35f : 0.2f);
		}
	}

	/** Whether it's mid-leap: in the air while it's after someone. */
	public boolean isPouncing() {
		return !onGround() && isAggressive();
	}

	public float pounce(float partialTicks) {
		return Mth.lerp(partialTicks, pounceO, pounce);
	}
}
