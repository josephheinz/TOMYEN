package com.jheinz.mymod.fae;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A fairy: a tiny glowing winged sprite, harmless but light-fingered. It flutters about, and now and then darts up to
 * a nearby player and, if it gets within reach, steals a random stack from their inventory: never the selected hotbar
 * slot, armour or the off hand. Then it flies off giggling, holding its prize, which it drops when killed or caught;
 * while it has it, it never despawns. It won't steal again for a minute, nor from anyone in creative.
 *
 * <p>A glass bottle used on it catches it (a Fairy in a Bottle), and it lets go of what it stole. One let out of a
 * bottle is grateful, and steals nothing for {@value #GRATITUDE_TICKS} ticks.
 *
 * <p>Ones called up by a Fae Mushroom in the Overworld vanish at daybreak, dropping anything they're holding.
 */
public class Fairy extends PathfinderMob {
	/** How close it has to get to pick a pocket. */
	private static final double REACH = 1.8;
	private static final int STEAL_COOLDOWN = 1200;
	private static final int GRATITUDE_TICKS = 6000;

	private int stealCooldown;
	/** Called up by a Fae Mushroom outside the Feywild: fades with the dawn. */
	private boolean nightBound;

	public Fairy(EntityType<? extends Fairy> type, Level level) {
		super(type, level);
		moveControl = new FlyingMoveControl<>(this, 20, true);
		stealCooldown = 200 + random.nextInt(400);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 4.0)
				.add(Attributes.FLYING_SPEED, 0.5)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	/** Natural spawns only in the Feywild (spawners and eggs anywhere). */
	public static boolean checkFairySpawnRules(EntityType<Fairy> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return EntitySpawnReason.isSpawner(reason) || Feywild.is(level.getLevel()) && level.getBlockState(pos).isAir();
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new FleeWithLootGoal());
		goalSelector.addGoal(2, new MischiefGoal());
		goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	public void travel(Vec3 input) {
		if (isAlive()) {
			travelFlying(input, getSpeed());
		} else {
			super.travel(input);
		}
	}

	@Override
	protected void checkFallDamage(double fallY, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	public boolean hasLoot() {
		return !getMainHandItem().isEmpty();
	}

	void markNightBound() {
		nightBound = true;
	}

	/** Let out of a bottle by this player: it steals from nobody for a good while. */
	void setFriend(Player player) {
		stealCooldown = GRATITUDE_TICKS;
		getLookControl().setLookAt(player);
	}

	/** A burst of sparkles, for arriving or vanishing. */
	void poof() {
		if (level() instanceof ServerLevel level) {
			level.sendParticles(Fae.SPARKLE, getX(), getY(0.5), getZ(), 12, 0.25, 0.25, 0.25, 0.02);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || !isAlive()) {
			return;
		}
		if (stealCooldown > 0) {
			stealCooldown--;
		}
		if (nightBound && level.isBrightOutside() && random.nextInt(100) == 0) {
			dropLoot(level);
			poof();
			discard();
			return;
		}
		if (stealCooldown == 0 && !hasLoot()) {
			Player victim = level.getNearestPlayer(this, REACH);
			if (canRob(victim) && random.nextInt(4) == 0) {
				steal(level, victim);
			}
		}
	}

	private boolean canRob(@Nullable Player player) {
		return player != null && player.isAlive() && !player.isSpectator() && !player.hasInfiniteMaterials();
	}

	/** Takes a random stack from the main inventory, skipping the selected hotbar slot. */
	private void steal(ServerLevel level, Player player) {
		Inventory inventory = player.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (slot != inventory.getSelectedSlot() && !inventory.getItem(slot).isEmpty()) {
				slots.add(slot);
			}
		}
		if (slots.isEmpty()) {
			return;
		}
		ItemStack stolen = inventory.removeItemNoUpdate(slots.get(random.nextInt(slots.size())));
		inventory.setChanged();
		setItemSlot(EquipmentSlot.MAINHAND, stolen);
		setGuaranteedDrop(EquipmentSlot.MAINHAND);
		setPersistenceRequired();
		stealCooldown = STEAL_COOLDOWN;
		level.playSound(null, getX(), getY(), getZ(), Fae.FAIRY_GIGGLE, SoundSource.NEUTRAL, 1.0f, 1.0f + random.nextFloat() * 0.2f);
		level.sendParticles(Fae.SPARKLE, getX(), getY(0.5), getZ(), 8, 0.2, 0.2, 0.2, 0.05);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("entity.mymod.fairy.stole", stolen.getHoverName()));
		}
	}

	private void dropLoot(ServerLevel level) {
		if (hasLoot()) {
			spawnAtLocation(level, getMainHandItem().copy());
			setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	/** Caught with a glass bottle. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.GLASS_BOTTLE) || !isAlive()) {
			return super.mobInteract(player, hand);
		}
		if (level() instanceof ServerLevel level) {
			dropLoot(level);
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Fae.FAIRY_IN_A_BOTTLE_ITEM)));
			level.playSound(null, getX(), getY(), getZ(), Fae.FAIRY_BOTTLED, SoundSource.NEUTRAL, 1.0f, 1.0f);
			discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive() && random.nextFloat() < (hasLoot() ? 0.5f : 0.2f)) {
			level().addParticle(Fae.SPARKLE, getRandomX(0.4), getY(0.3), getRandomZ(0.4), 0.0, -0.02, 0.0);
		}
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (deathTime == 1 && level().isClientSide()) {
			for (int i = 0; i < 10; i++) {
				level().addParticle(ParticleTypes.END_ROD, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0.0, 0.03, 0.0);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("steal_cooldown", stealCooldown);
		output.putBoolean("night_bound", nightBound);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		stealCooldown = input.getIntOr("steal_cooldown", 0);
		nightBound = input.getBooleanOr("night_bound", false);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return Fae.FAIRY_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Fae.FAIRY_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Fae.FAIRY_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6f;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	/** With its pockets empty and its cooldown over, it darts up to a nearby player to try its luck. */
	private class MischiefGoal extends Goal {
		private static final double NOTICE = 10.0;
		private @Nullable Player mark;
		private int ticks;

		MischiefGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (stealCooldown > 0 || hasLoot() || random.nextInt(40) != 0) {
				return false;
			}
			Player player = level().getNearestPlayer(Fairy.this, NOTICE);
			if (!canRob(player)) {
				return false;
			}
			mark = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return mark != null && canRob(mark) && !hasLoot() && stealCooldown == 0 && ticks < 200 && distanceToSqr(mark) < NOTICE * NOTICE * 2;
		}

		@Override
		public void start() {
			ticks = 0;
		}

		@Override
		public void stop() {
			mark = null;
			getNavigation().stop();
		}

		@Override
		public void tick() {
			ticks++;
			getLookControl().setLookAt(mark);
			if (ticks % 10 == 1) {
				getNavigation().moveTo(mark.getX(), mark.getY(0.6), mark.getZ(), 1.4);
			}
		}
	}

	/** Holding something it shouldn't: keeps well away from players, zipping off whenever one comes near. */
	private class FleeWithLootGoal extends Goal {
		private static final double SAFE = 14.0;
		private @Nullable Player chaser;

		FleeWithLootGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (!hasLoot()) {
				return false;
			}
			chaser = level().getNearestPlayer(Fairy.this, SAFE);
			return chaser != null && !chaser.isSpectator();
		}

		@Override
		public boolean canContinueToUse() {
			return hasLoot() && chaser != null && chaser.isAlive() && distanceToSqr(chaser) < SAFE * SAFE && getNavigation().isInProgress();
		}

		@Override
		public void start() {
			Vec3 away = position().subtract(chaser.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			float turn = (random.nextFloat() - 0.5f) * 1.2f;
			double x = away.x * Mth.cos(turn) - away.z * Mth.sin(turn);
			double z = away.x * Mth.sin(turn) + away.z * Mth.cos(turn);
			Vec3 target = position().add(x * 12.0, 2.0 + random.nextDouble() * 3.0, z * 12.0);
			getNavigation().moveTo(target.x, target.y, target.z, 1.8);
		}

		@Override
		public void stop() {
			chaser = null;
		}
	}
}
