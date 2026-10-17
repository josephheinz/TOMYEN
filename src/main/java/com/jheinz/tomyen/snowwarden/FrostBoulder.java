package com.jheinz.tomyen.snowwarden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Snow Warden's thrown snowball, as big as a person's head and packed hard, lobbed in an arc. It bursts on whatever
 * it hits: everything within {@value #RADIUS} blocks (the warden aside) takes up to {@value #DAMAGE} damage, less
 * toward the edge, is knocked away, slowed and chilled; and if mobs may grief, it leaves a dusting of snow where it
 * landed.
 */
public class FrostBoulder extends ThrowableItemProjectile {
	private static final double RADIUS = 2.5;
	private static final float DAMAGE = 8.0f;
	private static final int SLOW_TICKS = 60;
	private static final int CHILL_TICKS = 100;

	public FrostBoulder(EntityType<? extends FrostBoulder> type, Level level) {
		super(type, level);
	}

	public FrostBoulder(Level level, LivingEntity thrower) {
		super(SnowWardens.FROST_BOULDER, thrower, level, new ItemStack(Items.SNOWBALL));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SNOWBALL;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return entity != getOwner() && !(entity instanceof SnowWarden) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && !isRemoved() && random.nextInt(2) == 0) {
			level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.3, getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	public void handleEntityEvent(@EntityEvent.Value byte id) {
		if (id == EntityEvent.DEATH) {
			ItemParticleOption snow = new ItemParticleOption(ParticleTypes.ITEM, Items.SNOWBALL);
			for (int i = 0; i < 40; i++) {
				level().addParticle(snow, getX(), getY() + 0.3, getZ(), random.nextGaussian() * 0.25, 0.15 + random.nextFloat() * 0.2,
						random.nextGaussian() * 0.25);
			}
			for (int i = 0; i < 20; i++) {
				level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.3, getZ(), random.nextGaussian() * 0.15, 0.05,
						random.nextGaussian() * 0.15);
			}
			level().addParticle(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 0.0, 0.0, 0.0);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = result.getLocation();
		Entity owner = getOwner();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS),
				e -> e.isAlive() && !(e instanceof SnowWarden) && e.distanceToSqr(at) < RADIUS * RADIUS * 1.5)) {
			double distance = Math.sqrt(victim.distanceToSqr(at));
			float falloff = (float) Math.max(0.35, 1.0 - distance / (RADIUS + 1.0));
			DamageSource source = damageSources().thrown(this, owner);
			float damage = DAMAGE * falloff;
			if (victim.hurtServer(level, source, damage)) {
				Vec3 away = victim.position().subtract(at);
				away = new Vec3(away.x, 0.0, away.z);
				away = away.lengthSqr() < 1.0E-4 ? getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize() : away.normalize();
				victim.knockback(0.9 * falloff, -away.x, -away.z, source, damage);
				victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.25 * falloff, 0.0));
				victim.needsSync = true;
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1), owner);
				SnowWarden.chill(victim, (int) (CHILL_TICKS * falloff));
			}
		}
		if (level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			dustWithSnow(level, BlockPos.containing(at));
		}
		level.broadcastEntityEvent(this, EntityEvent.DEATH);
		level.playSound(null, at.x, at.y, at.z, SnowWardens.BOULDER_BURST, SoundSource.HOSTILE, 1.2f, 0.8f + random.nextFloat() * 0.3f);
		discard();
	}

	/** A thin layer of snow on the open ground round where it burst. */
	private void dustWithSnow(ServerLevel level, BlockPos centre) {
		BlockState snow = Blocks.SNOW.defaultBlockState();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
			if (random.nextInt(3) != 0 && level.getBlockState(pos).isAir() && snow.canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, snow);
			}
		}
	}
}
