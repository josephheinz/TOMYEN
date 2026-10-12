package com.jheinz.mymod.powerarmor;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * A suit of power armor standing empty, its back open, where someone climbed out of it (or died in it). It
 * stays put: nothing pushes it, it only falls if the ground goes, and it's saved with the world.
 * <ul>
 * <li>Use it wearing an exoskeleton with nothing socketed: climb back in.
 * <li>Hit it: the blow wears down whichever piece it lands on.
 * <li>Sneak and hit it: break it down, dropping its pieces and core (or climb in and dock it at a station).
 * </ul>
 */
public class ParkedPowerArmor extends Entity {
	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(ParkedPowerArmor.class, EntityDataSerializers.INT);

	private PowerArmorSuit suit = PowerArmorSuit.EMPTY;

	public ParkedPowerArmor(EntityType<? extends ParkedPowerArmor> type, Level level) {
		super(type, level);
	}

	/** Leaves a suit standing at a spot, facing a way. */
	public static ParkedPowerArmor park(ServerLevel level, PowerArmorSuit suit, Vec3 pos, float yaw) {
		ParkedPowerArmor parked = new ParkedPowerArmor(PowerArmors.PARKED, level);
		parked.setSuit(suit);
		parked.snapTo(pos.x, pos.y, pos.z, yaw, 0.0f);
		parked.setYHeadRot(yaw);
		level.addFreshEntity(parked);
		return parked;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LOOK, 0);
	}

	public PowerArmorSuit suit() {
		return suit;
	}

	public void setSuit(PowerArmorSuit suit) {
		this.suit = suit;
		entityData.set(LOOK, suit.look().packed());
	}

	/** How it looks, on either side. */
	public SuitLook look() {
		return new SuitLook(entityData.get(LOOK));
	}

	@Override
	protected double getDefaultGravity() {
		return 0.08;
	}

	@Override
	public void tick() {
		super.tick();
		// Falls straight down if the ground goes; otherwise it doesn't budge.
		applyGravity();
		Vec3 motion = getDeltaMovement();
		setDeltaMovement(0.0, motion.y, 0.0);
		move(MoverType.SELF, getDeltaMovement());
		setDeltaMovement(0.0, onGround() ? 0.0 : getDeltaMovement().y * 0.98, 0.0);
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
			PowerArmor.enterParked(serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved()) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			breakDown(level);
			return true;
		}
		if (source.getDirectEntity() instanceof Player player && player.isSecondaryUseActive()) {
			breakDown(level);
			return true;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return false;
		}
		PowerArmorPiece piece = PowerArmor.pieceHit(this, getYRot(), source);
		ItemStack hit = suit.piece(piece);
		if (!hit.isEmpty()) {
			setSuit(suit.with(piece.slot(), PowerArmorPieceItem.wear(hit, (int) Math.ceil(damage))));
		}
		playSound(SoundEvents.ANVIL_LAND, 0.4f, 1.4f + random.nextFloat() * 0.3f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(1.0 - piece.ordinal() / 10.0), getZ(), 6, 0.3, 0.2, 0.3, 0.1);
		return true;
	}

	/** Takes the suit apart where it stands: every piece and the core drop. */
	public void breakDown(ServerLevel level) {
		for (ItemStack stack : suit.contents()) {
			spawnAtLocation(level, stack, 1.0f);
		}
		playSound(SoundEvents.IRON_GOLEM_DAMAGE, 1.0f, 0.7f);
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY(1.0), getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		suit = PowerArmorSuit.EMPTY;
		discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("suit", PowerArmorSuit.CODEC, suit);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setSuit(input.read("suit", PowerArmorSuit.CODEC).orElse(PowerArmorSuit.EMPTY));
	}
}
