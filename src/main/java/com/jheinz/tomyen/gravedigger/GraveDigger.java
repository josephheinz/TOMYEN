package com.jheinz.tomyen.gravedigger;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Grave Digger: a gaunt zombie gravedigger with a big shovel, out at night (he's in {@code #minecraft:zombies}, so
 * undead, and in {@code #minecraft:burn_in_daylight}: his flat cap is no helmet, and the sun burns him like any zombie).
 * <p>
 * He can smell the living through walls. When he can't walk to his target (shut in a house, a walled base), he digs
 * down two or three blocks, tunnels under the walls and comes up through the floor beside them ({@link TunnelGoal}).
 * He can only dig soft ground ({@link GraveDiggers#DIGGABLE}) and, slowly, natural stone ({@link GraveDiggers#SOFT_STONE}),
 * never anything in {@link GraveDiggers#PROOF}, and only while {@code mobGriefing} is on. What he digs drops nothing.
 * Underground, he gives himself away: a muffled digging under the floor and cracks spreading through it where he's
 * about to come up.
 * <p>
 * In a fight he chops with the shovel; now and then he lands the flat of it with a clang that knocks his victim back.
 */
public class GraveDigger extends Monster {
	/** The chance a hit lands the flat of the shovel, with extra knockback and a clang. */
	private static final float WHACK_CHANCE = 0.35f;
	private static final float WHACK_KNOCKBACK = 0.7f;

	private static final EntityDataAccessor<Byte> DATA_DIG = SynchedEntityData.defineId(GraveDigger.class, EntityDataSerializers.BYTE);

	/** Which way he's digging, if he is (synced, for his animation). */
	public enum DigMode {
		NONE, DOWN, FORWARD, UP
	}

	/** Underground and moving himself (see TunnelGoal): no gravity while he does. Not saved. */
	private boolean tunnelling;
	/** This hit is a whack with the flat of the shovel. */
	private boolean whack;

	public GraveDigger(EntityType<? extends GraveDigger> type, Level level) {
		super(type, level);
		xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 26.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.15)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DIG, (byte) 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new TunnelGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// He smells the living through walls: the whole point of him.
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	public DigMode digMode() {
		int i = entityData.get(DATA_DIG);
		return i >= 0 && i < DigMode.values().length ? DigMode.values()[i] : DigMode.NONE;
	}

	void setDigMode(DigMode mode) {
		entityData.set(DATA_DIG, (byte) mode.ordinal());
	}

	void setTunnelling(boolean tunnelling) {
		this.tunnelling = tunnelling;
	}

	public boolean isTunnelling() {
		return tunnelling;
	}

	@Override
	protected double getDefaultGravity() {
		return tunnelling ? 0.0 : super.getDefaultGravity();
	}

	/** Underground he's wedged in his tunnel: nothing shoves him about. */
	@Override
	public boolean isPushable() {
		return !tunnelling && super.isPushable();
	}

	/** Most hits chop; some land the flat of the shovel with a clang and knock the victim back. */
	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		whack = random.nextFloat() < WHACK_CHANCE;
		boolean hit = super.doHurtTarget(level, target);
		if (hit && whack) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), GraveDiggers.CLANG, SoundSource.HOSTILE, 1.0f,
					0.9f + random.nextFloat() * 0.2f);
		}
		whack = false;
		return hit;
	}

	@Override
	protected float getKnockback(Entity target, DamageSource source) {
		return super.getKnockback(target, source) + (whack ? WHACK_KNOCKBACK : 0.0f);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return GraveDiggers.AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GraveDiggers.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GraveDiggers.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(GraveDiggers.STEP, 0.15f, 1.0f);
	}
}
