package com.jheinz.tomyen.fusion.melee;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.boar.Boars;

/**
 * The Antler Pike (tusk spear + moose antler, at a pond): a long ash shaft with the tusk point, a moose's palmate antler
 * crowning it as a broad guard of tines. A vanilla spear (jab, hold to charge) with a reach of 6, and:
 * <ul>
 * <li>Jab while sprinting and it becomes a boar's charge: you lower the pike and run {@value #CHARGE_TICKS} ticks
 * straight on, goring whatever you meet for the jab's damage plus {@value #GORE_DAMAGE} and flinging it away like a
 * boar does ({@value #GORE_KNOCKBACK} knockback, and up). The boar's own snort and impact. {@value #COOLDOWN} ticks'
 * cooldown.
 * </ul>
 * The sprint jab is caught at PiercingWeapon.attack's head (PiercingWeaponSprintMixin), before the jab spends the sprint.
 */
public class AntlerPikeItem extends Item {
	public static final int CHARGE_TICKS = 9;
	public static final double CHARGE_SPEED = 0.78;
	public static final float GORE_DAMAGE = 5.0f;
	public static final float GORE_KNOCKBACK = 1.8f;
	public static final int COOLDOWN = 40;
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();

	public AntlerPikeItem(Properties properties) {
		super(properties);
	}

	private static final class Charge {
		final ServerLevel level;
		final Vec3 dir;
		final Set<Integer> gored = new HashSet<>();
		int ticks;

		Charge(ServerLevel level, Vec3 dir) {
			this.level = level;
			this.dir = dir;
		}
	}

	/** A jab while sprinting: lower the pike and charge. */
	public static void sprintJab(ServerPlayer player, ItemStack stack) {
		if (player.getCooldowns().isOnCooldown(stack) || player.isPassenger() || player.isFallFlying() || player.isInWater()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 dir = CrescentWaves.flatLook(player);
		CHARGES.put(player.getUUID(), new Charge(level, dir));
		push(player, dir, true);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Boars.CHARGE, SoundSource.PLAYERS, 1.0f, 1.1f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Boars.ANGRY, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	private static void push(ServerPlayer player, Vec3 dir, boolean first) {
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(dir.x * CHARGE_SPEED, first && player.onGround() ? 0.12 : v.y, dir.z * CHARGE_SPEED);
		player.syncVelocity = true;
	}

	public static boolean charging(LivingEntity entity) {
		return CHARGES.containsKey(entity.getUUID());
	}

	static void tick(ServerLevel level) {
		Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Charge> entry = it.next();
			Charge charge = entry.getValue();
			if (charge.level != level) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != level || !(player.getMainHandItem().getItem() instanceof AntlerPikeItem)
					|| charge.ticks++ >= CHARGE_TICKS) {
				it.remove();
				continue;
			}
			if (charge.ticks > 2 && player.horizontalCollision) {
				// Into a wall: the charge stops dead, with a thud.
				level.playSound(null, player.getX(), player.getY(), player.getZ(), Boars.IMPACT, SoundSource.PLAYERS, 0.8f, 0.8f);
				it.remove();
				continue;
			}
			push(player, charge.dir, false);
			gore(level, player, charge);
			BlockState ground = level.getBlockState(player.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(), 6,
						0.3, 0.05, 0.3, 0.1);
			}
		}
	}

	/** Everything in the pike's path takes the gore: the jab's damage and more, and flies. */
	private static void gore(ServerLevel level, ServerPlayer player, Charge charge) {
		AABB path = player.getBoundingBox().expandTowards(charge.dir.scale(1.6)).inflate(0.5, 0.1, 0.5);
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + GORE_DAMAGE;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, path, e -> CrescentWaves.canHit(player, e))) {
			if (!charge.gored.add(target.getId())) {
				continue;
			}
			DamageSource source = level.damageSources().playerAttack(player);
			if (target.hurtServer(level, source, damage)) {
				fling(target, charge.dir, source);
				player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			}
			level.playSound(null, target.getX(), target.getY(), target.getZ(), Boars.IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
		}
	}

	private static void fling(LivingEntity target, Vec3 dir, DamageSource source) {
		target.knockback(GORE_KNOCKBACK, -dir.x, -dir.z, source, GORE_DAMAGE);
		target.push(0.0, 0.35, 0.0);
		target.syncVelocity = true;
	}

	/** The jab that started a charge throws its target too. */
	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		Charge charge = CHARGES.get(attacker.getUUID());
		if (charge != null && charge.gored.add(target.getId()) && attacker.level() instanceof ServerLevel level && attacker instanceof Player player) {
			// The jab only did a jab's damage; the charge behind it adds the gore's.
			DamageSource source = level.damageSources().playerAttack(player);
			target.damageCooldownTime = 0;
			target.hurtServer(level, source, GORE_DAMAGE);
			fling(target, charge.dir, source);
			attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(), Boars.IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
	}

	static void clear() {
		CHARGES.clear();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.antler_pike.reach", "item.tomyen.antler_pike.charge");
	}
}
