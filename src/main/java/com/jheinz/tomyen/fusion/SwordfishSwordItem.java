package com.jheinz.tomyen.fusion;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Swordfish Sword (swordfish + diamond sword, at a pond): a swordfish whose bill became a diamond blade. Netherite's
 * damage (8) and nearly its durability, and in the water it's at home:
 * <ul>
 * <li>Use it in water to lunge along your gaze ({@value #LUNGE_SPEED} blocks a tick), spinning like a riptide
 * trident and striking the first thing you hit; {@value #LUNGE_COOLDOWN} ticks' cooldown, one durability.
 * <li>+{@value #SUBMERGED_BONUS} damage while you're under water.
 * <li>Dolphin's Grace while it's in your hand in the water.
 * </ul>
 */
public class SwordfishSwordItem extends Item {
	public static final float SUBMERGED_BONUS = 4.0f;
	public static final float LUNGE_SPEED = 2.2f;
	public static final int LUNGE_COOLDOWN = 30;

	public SwordfishSwordItem(Properties properties) {
		super(properties);
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
		return source.getEntity() instanceof LivingEntity attacker && attacker.isUnderWater() ? SUBMERGED_BONUS : 0.0f;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isInWater() || player.isPassenger() || stack.nextDamageWillBreak()) {
			return InteractionResult.PASS;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float pitch = player.getXRot() * Mth.DEG_TO_RAD;
		Vec3 look = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		player.push(look.x * LUNGE_SPEED, look.y * LUNGE_SPEED, look.z * LUNGE_SPEED);
		player.startAutoSpinAttack(15, 9.0f, stack);
		player.getCooldowns().addCooldown(stack, LUNGE_COOLDOWN);
		if (level instanceof ServerLevel server) {
			stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS,
					1.0f, 1.2f);
			for (int i = 0; i < 8; i++) {
				Vec3 at = player.position().add(look.scale(i * 0.5)).add(0.0, 0.8, 0.0);
				server.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.05);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND && owner instanceof LivingEntity living && living.isInWater() && owner.tickCount % 10 == 0) {
			living.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 30, 0, true, false, true));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.swordfish_sword.lunge", "item.tomyen.swordfish_sword.submerged",
				"item.tomyen.swordfish_sword.swim");
	}
}
