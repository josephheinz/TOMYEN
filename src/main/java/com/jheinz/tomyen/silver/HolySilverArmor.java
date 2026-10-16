package com.jheinz.tomyen.silver;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.ModEffects;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.holy.HolyLight;

/**
 * Holy silver armor wards off evil damage: damage from an evil attacker ({@link HolyLight#EVIL}, or the evil owner of a
 * projectile or blast) or of an evil type (the {@code tomyen:evil} damage type tag: a vampire's Bleeding). Each piece
 * worn takes {@link #REDUCTION_PER_PIECE} off it, so the full set turns it aside entirely, with a golden flash. The
 * full set also smites an evil creature that strikes its wearer in melee, and cures Bleeding (and keeps it off).
 * Pieces in an exoskeleton's sockets count as worn. LivingEntityHolySilverMixin applies it to every hurt.
 */
public final class HolySilverArmor {
	/** Damage types that are evil whoever deals them. */
	public static final TagKey<DamageType> EVIL_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE, MyMod.id("evil"));
	public static final float REDUCTION_PER_PIECE = 0.25f;
	public static final int FULL_SET = 4;
	/** Holy damage an evil creature takes for striking the full set in melee (tripled on the holy-vulnerable). */
	public static final float SMITE_DAMAGE = 3.0f;
	private static final int CURE_INTERVAL = 10;
	private static final int GOLD = 0xFFD54A;

	private HolySilverArmor() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CURE_INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.hasEffect(ModEffects.BLEEDING) && fullSet(player)) {
					player.removeEffect(ModEffects.BLEEDING);
					flash(player.level(), player);
				}
			}
		});
	}

	public static boolean isPiece(ItemStack stack) {
		Item item = stack.getItem();
		return item == Silver.HOLY_SILVER_HELMET || item == Silver.HOLY_SILVER_CHESTPLATE || item == Silver.HOLY_SILVER_LEGGINGS
				|| item == Silver.HOLY_SILVER_BOOTS;
	}

	/** How many pieces the entity wears, worn or socketed in an exoskeleton. */
	public static int pieces(LivingEntity entity) {
		int pieces = 0;
		for (var slot : ExoSockets.ARMOR_SLOTS) {
			if (isPiece(entity.getItemBySlot(slot)) || isPiece(ExoSockets.worn(entity, slot))) {
				pieces++;
			}
		}
		return pieces;
	}

	public static boolean fullSet(LivingEntity entity) {
		return pieces(entity) >= FULL_SET;
	}

	public static boolean isEvil(DamageSource source) {
		return source.is(EVIL_DAMAGE) || HolyLight.isEvil(source.getEntity()) || HolyLight.isEvil(source.getDirectEntity());
	}

	/**
	 * What's left of a hit on this entity after its holy silver: 1 for anything but evil damage, down to 0 (warded
	 * off) for the full set. {@code /kill} and the void get through.
	 */
	public static float remaining(LivingEntity entity, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !isEvil(source)) {
			return 1.0f;
		}
		return Math.max(0.0f, 1.0f - REDUCTION_PER_PIECE * pieces(entity));
	}

	/** The full set has turned an evil hit aside: the flash, the smite on a melee attacker, and no more Bleeding. */
	public static void ward(ServerLevel level, LivingEntity wearer, DamageSource source) {
		flash(level, wearer);
		if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != wearer
				&& source.is(DamageTypes.MOB_ATTACK) && HolyLight.isEvil(attacker)) {
			HolyLight.smite(level, attacker, wearer, wearer, SMITE_DAMAGE);
		}
		wearer.removeEffect(ModEffects.BLEEDING);
	}

	/** A ring of golden light around the wearer, and a bright chime. */
	public static void flash(ServerLevel level, LivingEntity wearer) {
		double y = wearer.getY(0.55);
		double radius = wearer.getBbWidth() * 0.9 + 0.2;
		DustParticleOptions gold = new DustParticleOptions(GOLD, 1.1f);
		for (int i = 0; i < 16; i++) {
			float angle = i * Mth.TWO_PI / 16;
			level.sendParticles(gold, wearer.getX() + Mth.cos(angle) * radius, y, wearer.getZ() + Mth.sin(angle) * radius,
					1, 0.0, 0.25, 0.0, 0.0);
		}
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), wearer.getX(), y, wearer.getZ(),
				1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, wearer.getX(), y, wearer.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(), Silver.WARD, SoundSource.PLAYERS, 1.0f,
				0.95f + level.getRandom().nextFloat() * 0.1f);
	}
}
