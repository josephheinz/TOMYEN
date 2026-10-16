package com.jheinz.tomyen.holy;

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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.ModDamageTypes;
import com.jheinz.tomyen.ModNetworking;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.silver.HolySilver;
import com.jheinz.tomyen.vampire.Vampire;
import com.jheinz.tomyen.werewolf.WerewolfCurse;

/**
 * Holy light from a crucifix: harmless to the living, it burns the evil ({@link #EVIL}: the undead and anything in the
 * {@code tomyen:holy_vulnerable} entity tag, such as vampires, the Schnozzle and skullcranes), the holy-vulnerable worst of all. It
 * also consecrates dropped silver it touches (see HolySilver).
 */
public final class HolyLight {
	/** Holy light hurts what's in this tag even if it isn't undead, and hurts it {@link #VULNERABLE_MULTIPLIER} times as much. */
	public static final TagKey<EntityType<?>> HOLY_VULNERABLE = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("holy_vulnerable"));
	public static final float VULNERABLE_MULTIPLIER = 3.0f;
	/**
	 * The evil: everything holy light burns, silver bites and holy silver armor wards off. {@code tomyen:evil} holds
	 * {@code #tomyen:holy_vulnerable} and {@code #minecraft:undead}.
	 */
	public static final TagKey<EntityType<?>> EVIL = TagKey.create(Registries.ENTITY_TYPE, MyMod.id("evil"));
	public static final double BEAM_RANGE = 48.0;
	/** Beam damage at full charge. */
	public static final float BEAM_DAMAGE = 8.0f;
	/** Vampires this close to a beam's path are lit up by it. */
	private static final double BEAM_REVEAL_RADIUS = 5.0;
	/** Dropped items this close to where the beam strikes are caught in its light. */
	private static final double ITEM_SPLASH = 0.75;

	private HolyLight() {
	}

	/** Whether the entity is evil: undead or holy-vulnerable. */
	public static boolean isEvil(@Nullable Entity entity) {
		return entity != null && entity.is(EVIL);
	}

	public static boolean hurts(Entity entity) {
		return entity instanceof LivingEntity && isEvil(entity);
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
			if (entity instanceof ItemEntity item) {
				// Dropped silver in the beam, or lying where it lands.
				if (hit.isPresent() || item.getBoundingBox().inflate(ITEM_SPLASH).contains(end)) {
					HolySilver.consecrate(level, item);
				}
			} else if (hit.isPresent()) {
				smite(level, entity, player, player, BEAM_DAMAGE * power);
				WerewolfCurse.onHolyLight(level, entity);
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
