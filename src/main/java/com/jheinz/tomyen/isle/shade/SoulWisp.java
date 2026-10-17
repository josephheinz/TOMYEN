package com.jheinz.tomyen.isle.shade;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A Soul Wisp: a mote of soul fire that drifts about the Isle of the Damned on its own, low over the ground and the
 * black water, shying from anyone who comes close. The Isle's only natural spawn (ambient, a few at a time, in the open
 * air), and cheap: no goals or pathfinding, just a new drift target now and then. One hit puts it out; a player who
 * does so gets a Soul Wisp, more often than not.
 */
public class SoulWisp extends AmbientCreature {
	private static final double SHY_RANGE = 4.0;
	private @Nullable Vec3 drift;

	public SoulWisp(EntityType<? extends SoulWisp> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0);
	}

	/** In the open air on the Isle, over its ground or sea, and only a third of the times asked. */
	public static boolean checkSpawnRules(EntityType<SoulWisp> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
		return random.nextInt(3) == 0 && pos.getY() >= surface && pos.getY() <= surface + 6 && level.canSeeSky(pos);
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(getDeltaMovement().multiply(1.0, 0.6, 1.0));
		if (level().isClientSide() && random.nextInt(4) == 0) {
			level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getRandomX(0.3), getY(0.5) + random.nextGaussian() * 0.08, getRandomZ(0.3),
					0.0, 0.012, 0.0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Player near = level.getNearestPlayer(this, SHY_RANGE);
		if (near != null && !near.isSpectator()) {
			Vec3 away = position().subtract(near.position()).multiply(1.0, 0.0, 1.0);
			drift = position().add(away.normalize().scale(5.0)).add(0.0, 1.0, 0.0);
		} else if (drift == null || random.nextInt(60) == 0 || drift.distanceToSqr(position()) < 1.0) {
			double x = getX() + random.nextInt(9) - 4;
			double z = getZ() + random.nextInt(9) - 4;
			double surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			drift = new Vec3(x, surface + 1.0 + random.nextDouble() * 3.5, z);
		}
		Vec3 motion = getDeltaMovement();
		double speed = near != null ? 0.35 : 0.15;
		Vec3 to = drift.subtract(position());
		setDeltaMovement(motion.add((Math.signum(to.x) * speed - motion.x) * 0.08, (Math.signum(to.y) * 0.2 - motion.y) * 0.08,
				(Math.signum(to.z) * speed - motion.z) * 0.08));
		setYRot((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0f);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return IsleShades.SOUL_WISP_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return IsleShades.SOUL_WISP_DEATH;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return IsleShades.SOUL_WISP_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.3f;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}
}
