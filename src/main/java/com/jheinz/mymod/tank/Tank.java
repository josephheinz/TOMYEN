package com.jheinz.mymod.tank;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.bank.Collector;
import com.jheinz.mymod.bank.Collectors;

/**
 * A tank: tracks, a hull and a turret with a cannon firing {@link TankShell}s, which blow up without
 * breaking blocks. The hull turns to face where it's driving; the turret is its "head", so it swings
 * round on its own to aim.
 * <p>
 * Anyone can drive one (see {@link TankItem} to get one): use it to climb into the hatch, then W/S drive,
 * A/D turn the hull, the mouse aims the turret and attack fires the cannon every {@link #PLAYER_RELOAD_TICKS}.
 * Sneak to climb out. Iron ingots (or blocks) patch it up; sneak-use it with an empty hand at full health
 * to pack it back into an item. Destroyed, it leaves scrap.
 * <p>
 * The debt collectors use the same tank: with a {@link com.jheinz.mymod.bank.DebtCollector} in the hatch
 * it drives itself, closing to cannon range of the debtor and shelling them while the crew shoots. Their
 * tanks are bank property, so players can't board them and they drop nothing.
 */
public class Tank extends PathfinderMob implements RangedAttackMob, Collector {
	/** Entity event: the cannon fired, so the client kicks the barrel back and starts its reload readout. */
	private static final byte FIRE_EVENT = 4;
	public static final int RECOIL_TICKS = 8;
	/** Between shots when a player is firing. */
	public static final int PLAYER_RELOAD_TICKS = 60;
	/** Height of the turret's pivot, and how far forward of it the muzzle is. */
	private static final double TURRET_HEIGHT = 1.15;
	private static final double BARREL_LENGTH = 1.9;
	/** Where riders stand in the hatch: feet down in the hull, so only their top half shows. A seated player sits higher. */
	private static final double HATCH_HEIGHT = 0.55;
	private static final double PLAYER_SEAT_HEIGHT = 0.85;
	/** Degrees a tick the hull turns under A/D. */
	private static final float TURN_SPEED = 4.0f;
	/** How far the turret can aim down and up. */
	private static final float MIN_PITCH = -25.0f;
	private static final float MAX_PITCH = 15.0f;
	private static final float REPAIR_PER_INGOT = 10.0f;

	private @Nullable UUID debtor;
	private int reload;
	// Client side: ticks of barrel recoil left.
	private int recoil;

	public Tank(EntityType<? extends Tank> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 80.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		// Only a collector tank ever has a target: it drives to within 22 blocks with a clear shot, then
		// sits and fires every 3 to 4 seconds.
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 60, 80, 22.0f));
	}

	/** The hull always faces the way the tracks point; it never swings round to where the turret aims. */
	@Override
	protected BodyRotationControl createBodyControl() {
		return new BodyRotationControl(this) {
			@Override
			public void clientTick() {
				yBodyRot = getYRot();
			}
		};
	}

	/** The turret goes all the way round. */
	@Override
	public int getMaxHeadYRot() {
		return 180;
	}

	@Override
	public int getHeadRotSpeed() {
		return 6;
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
		if (debtor != null) {
			Collectors.tick(this);
		}
		if (reload > 0) {
			reload--;
		}
		if (level().isClientSide() && recoil > 0) {
			recoil--;
		}
	}

	// --- Driving ------------------------------------------------------------------------------------------

	/** A player in the hatch drives; a collector crew only shoots, and the tank drives itself. */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected void tickRidden(Player driver, Vec3 input) {
		super.tickRidden(driver, input);
		// Tank steering: A/D turn the hull on the spot. Only the side that simulates the movement turns it; the
		// other gets the result.
		if (isLocalInstanceAuthoritative()) {
			float yaw = getYRot() - driver.xxa * TURN_SPEED;
			setYRot(yaw);
			yRotO = yaw;
			yBodyRot = yaw;
		}
		// The turret follows the driver's view.
		yHeadRot = driver.getYRot();
		setXRot(Mth.clamp(driver.getXRot(), MIN_PITCH, MAX_PITCH));
	}

	/** Forward and back only (reversing at half speed); strafing turns the hull instead. */
	@Override
	protected Vec3 getRiddenInput(Player driver, Vec3 selfInput) {
		float forward = driver.zza;
		return new Vec3(0.0, 0.0, forward < 0.0f ? forward * 0.5f : forward);
	}

	@Override
	protected float getRiddenSpeed(Player driver) {
		return (float) getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.25f;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (debtor != null) {
			if (!level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable("entity.mymod.tank.bank_property").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		ItemStack held = player.getItemInHand(hand);
		float patch = held.is(Items.IRON_INGOT) ? REPAIR_PER_INGOT : held.is(Items.IRON_BLOCK) ? REPAIR_PER_INGOT * 9 : 0.0f;
		if (patch > 0.0f && getHealth() < getMaxHealth()) {
			if (!level().isClientSide()) {
				heal(patch);
				held.consume(1, player);
				playSound(SoundEvents.ANVIL_USE, 0.6f, 1.2f);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive() && held.isEmpty() && !isVehicle()) {
			if (getHealth() < getMaxHealth()) {
				if (!level().isClientSide()) {
					player.sendOverlayMessage(Component.translatable("entity.mymod.tank.repair_first").withStyle(ChatFormatting.RED));
				}
				return InteractionResult.FAIL;
			}
			if (!level().isClientSide()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Tanks.TANK_ITEM), Prediction.SERVER_ONLY);
				playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 0.8f);
				discard();
			}
			return InteractionResult.SUCCESS;
		}
		if (!isVehicle() && !player.isSecondaryUseActive()) {
			if (!level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, (passenger instanceof Player ? PLAYER_SEAT_HEIGHT : HATCH_HEIGHT) * scale, 0.0);
	}

	// --- The cannon ---------------------------------------------------------------------------------------

	/** Where along the barrel the shell leaves, given where the turret is aimed. */
	private Vec3 muzzle() {
		Vec3 aim = Vec3.directionFromRotation(getXRot(), getYHeadRot());
		return position().add(0.0, TURRET_HEIGHT, 0.0).add(aim.scale(BARREL_LENGTH));
	}

	/** The driver pulled the trigger (sent from their client). Fires where they're looking, if it's reloaded. */
	public void fireForDriver(ServerPlayer driver) {
		if (reload > 0 || getControllingPassenger() != driver) {
			return;
		}
		Vec3 aim = Vec3.directionFromRotation(Mth.clamp(driver.getXRot(), MIN_PITCH, MAX_PITCH), driver.getYRot());
		fire((ServerLevel) level(), driver, aim, 2.2f, 0.5f);
		reload = PLAYER_RELOAD_TICKS;
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 muzzle = muzzle();
		double dx = target.getX() - muzzle.x;
		double dz = target.getZ() - muzzle.z;
		// Aim a little high to make up for the drop, more the further it has to go.
		double dy = target.getY(0.4) - muzzle.y + Math.sqrt(dx * dx + dz * dz) * 0.12;
		fire(level, this, new Vec3(dx, dy, dz), 1.6f, 3.0f);
	}

	private void fire(ServerLevel level, LivingEntity gunner, Vec3 direction, float speed, float inaccuracy) {
		Vec3 muzzle = muzzle();
		TankShell shell = new TankShell(level, gunner, muzzle);
		shell.shoot(direction.x, direction.y, direction.z, speed, inaccuracy);
		level.addFreshEntity(shell);
		level.broadcastEntityEvent(this, FIRE_EVENT);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.15, 0.15, 0.15, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.02);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.5f, 1.6f);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == FIRE_EVENT) {
			recoil = RECOIL_TICKS;
			reload = PLAYER_RELOAD_TICKS;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** How far back the barrel has kicked, 0 to 1. */
	public float recoil(float partialTicks) {
		return recoil <= 0 ? 0.0f : Mth.clamp((recoil - partialTicks) / RECOIL_TICKS, 0.0f, 1.0f);
	}

	/** How far through reloading the cannon is, 0 (just fired) to 1 (ready). On the client, an estimate. */
	public float reloadProgress() {
		return 1.0f - reload / (float) PLAYER_RELOAD_TICKS;
	}

	// --- Damage -------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// Collectors don't shell each other, and a driver can't hurt their own tank.
		if (debtor != null && Collectors.isFriendlyFire(source) || source.getEntity() != null && hasPassenger(source.getEntity())) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	/** Scrap from a player's tank; the bank's are written off. */
	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return debtor == null && super.shouldDropLoot(level);
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (deathTime == 1 && level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.8, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.8, 0.5, 0.8, 0.05);
		}
	}

	/** Sealed hull: it can drive along the bottom of a river. */
	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
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

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 0.6f, 0.5f + random.nextFloat() * 0.1f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.MINECART_RIDING;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6f;
	}

	@Override
	public float getVoicePitch() {
		return 0.5f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GENERIC_EXPLODE.value();
	}
}
