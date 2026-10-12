package com.jheinz.mymod.holy;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.ModNetworking;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.vampire.Vampire;

/**
 * Holy light from a crucifix: harmless to the living, it burns the undead and anything in the
 * {@code mymod:holy_vulnerable} entity tag (vampires, the Schnozzle), the tagged ones worst of all.
 */
public final class HolyLight {
	/** Holy light hurts what's in this tag even if it isn't undead, and hurts it {@link #VULNERABLE_MULTIPLIER} times as much. */
	public static final TagKey<EntityType<?>> HOLY_VULNERABLE = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("holy_vulnerable"));
	public static final float VULNERABLE_MULTIPLIER = 3.0f;
	public static final double BEAM_RANGE = 48.0;
	/** Beam damage at full charge. */
	public static final float BEAM_DAMAGE = 8.0f;
	/** Vampires this close to a beam's path are lit up by it. */
	private static final double BEAM_REVEAL_RADIUS = 5.0;

	private HolyLight() {
	}

	public static boolean hurts(Entity entity) {
		return entity instanceof LivingEntity living && (living.is(EntityTypeTags.UNDEAD) || living.is(HOLY_VULNERABLE));
	}

	/** Hurts the target if it's undead or holy-vulnerable (triple for the latter); does nothing to anything else. */
	public static boolean smite(ServerLevel level, Entity target, Entity direct, @Nullable Entity owner, float amount) {
		if (!hurts(target)) {
			return false;
		}
		if (target.is(HOLY_VULNERABLE)) {
			amount *= VULNERABLE_MULTIPLIER;
		}
		boolean hurt = target.hurtServer(level, level.damageSources().source(ModDamageTypes.HOLY_LIGHT, direct, owner), amount);
		if (hurt) {
			level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.5), target.getZ(), 12, 0.25, 0.5, 0.25, 0.08);
		}
		return hurt;
	}

	/** Fires a beam straight ahead from the player's eyes, through every creature in its path, to the first block. */
	public static void fireBeam(ServerLevel level, Player player, float power) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0f).scale(BEAM_RANGE));
		HitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		end = block.getLocation();

		double width = 0.2 + 0.4 * power;
		for (Entity entity : level.getEntities(player, new AABB(start, end).inflate(BEAM_REVEAL_RADIUS), Entity::isAlive)) {
			Optional<Vec3> hit = entity.getBoundingBox().inflate(width).clip(start, end);
			if (hit.isPresent()) {
				smite(level, entity, player, player, BEAM_DAMAGE * power);
			}
			if (entity instanceof Vampire vampire && distanceToSegment(vampire.position().add(0, 1, 0), start, end) < BEAM_REVEAL_RADIUS) {
				vampire.reveal();
			}
		}

		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, (int) (10 + 20 * power), 0.2, 0.2, 0.2, 0.15);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f,
				1.6f + 0.4f * power);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.6f);

		ModNetworking.LightBeam beam = new ModNetworking.LightBeam(player.getId(), handPosition(player), end, power);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, beam);
		}
		if (player instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, beam);
		}
	}

	/** Roughly where the crucifix is held, as seen by someone else. */
	public static Vec3 handPosition(Player player) {
		Vec3 look = player.getViewVector(1.0f);
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize().scale(side);
		return player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0.0, -0.35, 0.0);
	}

	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = Math.clamp(point.subtract(a).dot(ab) / Math.max(ab.lengthSqr(), 1.0E-6), 0.0, 1.0);
		return point.distanceTo(a.add(ab.scale(t)));
	}
}
