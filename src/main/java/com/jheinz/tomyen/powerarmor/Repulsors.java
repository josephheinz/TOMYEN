package com.jheinz.tomyen.powerarmor;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The suit's weapons, fired from the wearer's client and checked here.
 * <ul>
 * <li><b>Repulsors</b>: a blast from the port on one gauntlet (the arms take turns; a broken arm can't fire) straight along the
 * look, up to {@link #REPULSOR_RANGE} blocks, hurting and throwing back the first thing it hits. Damage,
 * knockback and cooldown come from the firing arm's mark; each costs {@link #REPULSOR_COST} fuel.
 * <li><b>Unibeam</b>: the recessed chest emitter charges for {@link #UNIBEAM_CHARGE_TICKS}, then fires a beam
 * {@link #UNIBEAM_RANGE} blocks long through everything in its path, punching through up to
 * {@link #UNIBEAM_MAX_BLOCKS} weak blocks (glass, leaves, dirt and the like, where the wearer may build). Its
 * damage comes from the torso's mark; it costs {@link #UNIBEAM_COST} fuel and needs {@link #UNIBEAM_COOLDOWN}
 * to recover.
 * </ul>
 */
public final class Repulsors {
	public static final int REPULSOR_COST = 240;
	public static final double REPULSOR_RANGE = 32.0;
	public static final int UNIBEAM_COST = 3600;
	public static final int UNIBEAM_CHARGE_TICKS = 20;
	public static final int UNIBEAM_COOLDOWN = 200;
	public static final double UNIBEAM_RANGE = 48.0;
	private static final int UNIBEAM_MAX_BLOCKS = 8;
	/** Blocks no harder than this give way to the unibeam (dirt is 0.5, glass 0.3). */
	private static final float UNIBEAM_BREAKS = 0.6f;

	private Repulsors() {
	}

	/** Where a gauntlet's repulsor port is: in front of the wearer at chest height, to one side. */
	private static Vec3 palm(ServerPlayer player, boolean left) {
		Vec3 look = player.getViewVector(1.0f);
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		return player.getEyePosition().add(look.scale(0.6)).add(right.scale(left ? -0.4 : 0.4)).add(0.0, -0.35, 0.0);
	}

	private static boolean ready(ServerPlayer player, PowerArmor.State state) {
		return state.sequence == PowerArmorMotion.NONE && player.isAlive() && !player.isSpectator();
	}

	static void fireRepulsor(ServerPlayer player) {
		PowerArmor.State state = PowerArmor.state(player);
		PowerArmorSuit suit = PowerArmor.worn(player);
		if (state == null || suit == null || !ready(player, state) || state.repulsorCooldown > 0) {
			return;
		}
		if (state.fuel < REPULSOR_COST * suit.stats().fuelEfficiency()) {
			player.sendOverlayMessage(Component.translatable("power_armor.tomyen.low_power").withStyle(ChatFormatting.RED));
			return;
		}
		PowerArmorStats stats = suit.stats();
		PowerArmorPiece arm = state.leftArmNext ? PowerArmorPiece.LEFT_ARM : PowerArmorPiece.RIGHT_ARM;
		if (!stats.repulsorWorks(arm)) {
			arm = arm == PowerArmorPiece.LEFT_ARM ? PowerArmorPiece.RIGHT_ARM : PowerArmorPiece.LEFT_ARM;
			if (!stats.repulsorWorks(arm)) {
				player.sendOverlayMessage(Component.translatable("power_armor.tomyen.repulsors_broken").withStyle(ChatFormatting.RED));
				return;
			}
		}
		state.leftArmNext = arm == PowerArmorPiece.RIGHT_ARM;
		state.repulsorCooldown = state.repulsorCooldownMax = stats.repulsorCooldown(arm);
		PowerArmor.drain(player, state, REPULSOR_COST);

		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(look.scale(REPULSOR_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		end = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, player.getBoundingBox().expandTowards(look.scale(REPULSOR_RANGE)).inflate(1.0),
				entity -> entity.isPickable() && !entity.isSpectator() && entity != player, 0.3f);
		DamageSource source = player.damageSources().source(PowerArmors.REPULSOR, player);
		if (hit != null) {
			end = hit.getLocation();
			Entity target = hit.getEntity();
			target.hurtServer(level, source, stats.repulsorDamage(arm));
			if (target instanceof LivingEntity living) {
				living.knockback(stats.repulsorKnockback(arm), -look.x, -look.z, source, stats.repulsorDamage(arm));
				living.push(0.0, 0.15, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.15, 0.15, 0.15, 0.3);
		level.sendParticles(ParticleTypes.POOF, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.05);
		Vec3 from = palm(player, arm == PowerArmorPiece.LEFT_ARM);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.6f + player.getRandom().nextFloat() * 0.2f);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.6f, 1.4f);
		beam(player, arm == PowerArmorPiece.LEFT_ARM ? PowerArmorNetworking.Beam.LEFT_REPULSOR : PowerArmorNetworking.Beam.RIGHT_REPULSOR, from, end);
	}

	static void chargeUnibeam(ServerPlayer player) {
		PowerArmor.State state = PowerArmor.state(player);
		PowerArmorSuit suit = PowerArmor.worn(player);
		if (state == null || suit == null || !ready(player, state) || state.unibeamCooldown > 0 || state.unibeamCharge >= 0) {
			return;
		}
		if (!suit.stats().unibeamWorks()) {
			player.sendOverlayMessage(Component.translatable("power_armor.tomyen.unibeam_broken").withStyle(ChatFormatting.RED));
			return;
		}
		if (state.fuel < UNIBEAM_COST * suit.stats().fuelEfficiency()) {
			player.sendOverlayMessage(Component.translatable("power_armor.tomyen.low_power").withStyle(ChatFormatting.RED));
			return;
		}
		state.unibeamCharge = 0;
		player.level().playSound(null, player.getX(), player.getY(1.0), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6f, 1.6f);
		player.level().playSound(null, player.getX(), player.getY(1.0), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.8f);
	}

	/** Counts cooldowns down and runs a charging unibeam. */
	static void tick(ServerPlayer player, PowerArmor.State state, PowerArmorStats stats) {
		state.repulsorCooldown = Math.max(0, state.repulsorCooldown - 1);
		state.unibeamCooldown = Math.max(0, state.unibeamCooldown - 1);
		if (state.unibeamCharge < 0) {
			return;
		}
		Vec3 chest = chest(player);
		ServerLevel level = player.level();
		if (state.unibeamCharge % 3 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z, 2, 0.25, 0.25, 0.25, 0.0);
		}
		if (++state.unibeamCharge >= UNIBEAM_CHARGE_TICKS) {
			state.unibeamCharge = -1;
			if (stats.unibeamWorks() && state.fuel >= UNIBEAM_COST * stats.fuelEfficiency()) {
				fireUnibeam(player, state, stats);
			}
		}
	}

	private static Vec3 chest(ServerPlayer player) {
		return player.getEyePosition().add(0.0, -0.55, 0.0).add(player.getViewVector(1.0f).scale(0.45));
	}

	private static void fireUnibeam(ServerPlayer player, PowerArmor.State state, PowerArmorStats stats) {
		ServerLevel level = player.level();
		state.unibeamCooldown = UNIBEAM_COOLDOWN;
		PowerArmor.drain(player, state, UNIBEAM_COST);
		Vec3 look = player.getViewVector(1.0f);
		Vec3 from = chest(player);
		Vec3 end = from.add(look.scale(UNIBEAM_RANGE));

		// March along the beam: weak blocks give way, anything sturdier stops it.
		int broken = 0;
		BlockPos last = null;
		for (double travelled = 0.0; travelled < UNIBEAM_RANGE; travelled += 0.25) {
			Vec3 point = from.add(look.scale(travelled));
			BlockPos pos = BlockPos.containing(point);
			if (pos.equals(last)) {
				continue;
			}
			last = pos;
			BlockState state2 = level.getBlockState(pos);
			if (state2.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			float hardness = state2.getDestroySpeed(level, pos);
			if (hardness >= 0.0f && hardness <= UNIBEAM_BREAKS && broken < UNIBEAM_MAX_BLOCKS && player.mayInteract(level, pos)
					&& player.mayBuild()) {
				level.destroyBlock(pos, true, player);
				broken++;
				continue;
			}
			end = point;
			break;
		}

		DamageSource source = player.damageSources().source(PowerArmors.UNIBEAM, player);
		AABB reach = new AABB(from, end).inflate(1.0);
		List<Entity> targets = level.getEntities(player, reach, entity -> entity.isPickable() && !entity.isSpectator());
		for (Entity target : targets) {
			if (target.getBoundingBox().inflate(0.5).clip(from, end).isEmpty()) {
				continue;
			}
			target.hurtServer(level, source, stats.unibeamDamage());
			if (target instanceof LivingEntity living) {
				living.knockback(1.6, -look.x, -look.z, source, stats.unibeamDamage());
			}
		}
		double length = from.distanceTo(end);
		for (double d = 1.0; d < length; d += 1.5) {
			Vec3 point = from.add(look.scale(d));
			level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0.02);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.5f);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.6f);
		beam(player, PowerArmorNetworking.Beam.UNIBEAM, from, end);
	}

	/** Tells the wearer and everyone who can see them to draw a beam. */
	private static void beam(ServerPlayer player, int kind, Vec3 from, Vec3 to) {
		PowerArmorNetworking.Beam beam = new PowerArmorNetworking.Beam(player.getId(), kind, from, to);
		ServerPlayNetworking.send(player, beam);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, beam);
			}
		}
	}
}
