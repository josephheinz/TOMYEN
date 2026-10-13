package com.jheinz.mymod.dragonscale;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * The Dragon Roar: with the full dragonscale set on (the helmet included, worn or socketed), the roar key breathes
 * a cone of fire {@link #RANGE} blocks long and {@link #HALF_ANGLE_DEGREES} degrees either side of where you look.
 * Everything living in it that you can see takes {@link #DAMAGE} fire damage and burns for {@link #BURN_SECONDS}
 * seconds; fire-immune mobs shrug it off. Then the helmet goes on a {@link #COOLDOWN} tick cooldown (shown on it
 * like any item cooldown). It sets nothing alight but creatures.
 * <p>
 * Also the two dragonscale packets: the roar key and the gliding flap (see DragonscaleArmor#flap), both sent by
 * the client and checked here.
 */
public final class DragonRoar {
	public static final double RANGE = 8.0;
	public static final double HALF_ANGLE_DEGREES = 35.0;
	public static final float DAMAGE = 6.0f;
	public static final float BURN_SECONDS = 5.0f;
	/** 10 s. */
	public static final int COOLDOWN = 200;
	private static final double MIN_DOT = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));
	private static final ParticleOptions BREATH = PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f);

	/** Sent when the player presses the roar key. */
	public record Roar() implements CustomPacketPayload {
		public static final Roar INSTANCE = new Roar();
		public static final Type<Roar> TYPE = new Type<>(MyMod.id("dragon_roar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Roar> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Roar> type() {
			return TYPE;
		}
	}

	/** Sent when the player presses jump while gliding in the full set. */
	public record Flap() implements CustomPacketPayload {
		public static final Flap INSTANCE = new Flap();
		public static final Type<Flap> TYPE = new Type<>(MyMod.id("dragonscale_flap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flap> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Flap> type() {
			return TYPE;
		}
	}

	private DragonRoar() {
	}

	static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(Roar.TYPE, Roar.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Flap.TYPE, Flap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Roar.TYPE, (payload, context) -> roar(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Flap.TYPE, (payload, context) -> DragonscaleArmor.flap(context.player()));
	}

	public static void roar(ServerPlayer player) {
		ItemStack helmet = DragonscaleArmor.worn(player, EquipmentSlot.HEAD);
		if (helmet.isEmpty() || !player.isAlive() || player.isSpectator() || !DragonscaleArmor.hasFullSet(player)
				|| player.getCooldowns().isOnCooldown(helmet)) {
			return;
		}
		player.getCooldowns().addCooldown(helmet, COOLDOWN);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Dragonscales.ROAR, SoundSource.PLAYERS, 1.5f,
				0.95f + player.getRandom().nextFloat() * 0.1f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
		breathe(level, eye.add(look.scale(0.6)), look, player.getRandom());

		DamageSource source = level.damageSources().source(Dragonscales.ROAR_DAMAGE, player);
		AABB reach = new AABB(eye, eye).inflate(RANGE);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach,
				entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > RANGE + target.getBbWidth() * 0.5 || distance > 0.5 && to.normalize().dot(look) < MIN_DOT
					|| !player.hasLineOfSight(target)) {
				continue;
			}
			if (!target.fireImmune()) {
				target.igniteForSeconds(BURN_SECONDS);
			}
			target.hurtServer(level, source, DAMAGE);
		}
	}

	/** Flame and dragon's breath jetting out along the cone, sent as moving particles. */
	private static void breathe(ServerLevel level, Vec3 mouth, Vec3 look, RandomSource random) {
		for (int i = 0; i < 70; i++) {
			// A random direction inside the cone, biased toward its axis.
			double spread = Math.toRadians(HALF_ANGLE_DEGREES) * Math.sqrt(random.nextDouble());
			Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
			Vec3 up = side.cross(look).normalize();
			double around = random.nextDouble() * Mth.TWO_PI;
			Vec3 direction = look.scale(Math.cos(spread))
					.add(side.scale(Math.sin(spread) * Math.cos(around)))
					.add(up.scale(Math.sin(spread) * Math.sin(around)));
			double speed = 0.35 + random.nextDouble() * 0.45;
			// A count of 0 makes the offsets a velocity.
			ParticleOptions particle = i % 3 == 0 ? BREATH : ParticleTypes.FLAME;
			level.sendParticles(particle, mouth.x, mouth.y, mouth.z, 0, direction.x, direction.y, direction.z, speed);
		}
	}
}
