package com.jheinz.tomyen.bank;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;

/**
 * An iron golem hired as muscle: none of the village-guarding, flower-giving or patrolling, it just walks
 * the debtor down and swings. A little lighter than a village golem, and it drops nothing (so a hunt
 * can't be farmed for iron).
 */
public class CollectorGolem extends IronGolem implements Collector {
	private @Nullable UUID debtor;

	public CollectorGolem(EntityType<? extends CollectorGolem> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return IronGolem.createAttributes()
				.add(Attributes.MAX_HEALTH, 60.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 64.0f));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	public @Nullable UUID debtor() {
		return debtor;
	}

	@Override
	public void setDebtor(@Nullable UUID debtor) {
		this.debtor = debtor;
	}

	@Override
	public void tick() {
		super.tick();
		Collectors.tick(this);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !Collectors.isFriendlyFire(source) && super.hurtServer(level, source, damage);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		Collectors.save(output, debtor);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		debtor = Collectors.load(input);
	}
}
