package com.jheinz.tomyen.fusion.melee;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Leviathan Blade (Swordfish Sword + Moonblade, in the Fusion Reactor, tier 2): the best sword in the mod. A great
 * blade of deep-sea steel with a tidal crest along its back, a leviathan's jaws for the guard, fins and the moonstone.
 * 10 damage at a sword's 1.6 swings a second, past everything Exotic, and both its parents' gifts, grown:
 * <ul>
 * <li>+{@value #WET_BONUS} damage while you're in water or rain. Dolphin's Grace and Conduit Power in the water.
 * <li>Use it in water to lunge where you look, spinning like a riptide trident ({@value #LUNGE_COOLDOWN} ticks).
 * <li>Every charged swing looses a crescent wave of sea water ({@value #WAVE_REACH} blocks), moonlit and longer by
 * night ({@value #NIGHT_REACH}).
 * <li>On land, hold use to gather the tide and let go after {@value #SURGE_CHARGE} ticks: a tidal surge rings out
 * {@value #SURGE_REACH} blocks round you, {@value #SURGE_DAMAGE} damage, flinging and slowing what it hits and putting
 * out fires ({@value #SURGE_COOLDOWN} ticks' cooldown).
 * </ul>
 */
public class LeviathanBladeItem extends Item implements MeleeSwings.SwingAbility {
	public static final float WET_BONUS = 4.0f;
	public static final float LUNGE_SPEED = 2.6f;
	public static final int LUNGE_COOLDOWN = 25;
	public static final double WAVE_REACH = 6.0;
	public static final double NIGHT_REACH = 8.0;
	public static final float WAVE_SHARE = 0.6f;
	public static final int SURGE_CHARGE = 15;
	public static final double SURGE_REACH = 7.0;
	public static final float SURGE_DAMAGE = 10.0f;
	public static final int SURGE_COOLDOWN = 80;

	public LeviathanBladeItem(Properties properties) {
		super(properties);
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
		return source.getEntity() instanceof LivingEntity attacker && attacker.isInWaterOrRain() ? WET_BONUS : 0.0f;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isPassenger() || stack.nextDamageWillBreak() || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}
		if (player.isInWater()) {
			lunge(level, player, hand, stack);
			return InteractionResult.SUCCESS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** The Swordfish Sword's lunge, faster. */
	private static void lunge(Level level, Player player, InteractionHand hand, ItemStack stack) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		float pitch = player.getXRot() * Mth.DEG_TO_RAD;
		Vec3 look = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		player.push(look.x * LUNGE_SPEED, look.y * LUNGE_SPEED, look.z * LUNGE_SPEED);
		player.startAutoSpinAttack(18, 12.0f, stack);
		player.getCooldowns().addCooldown(stack, LUNGE_COOLDOWN);
		if (level instanceof ServerLevel server) {
			stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS,
					1.0f, 1.0f);
			for (int i = 0; i < 10; i++) {
				Vec3 at = player.position().add(look.scale(i * 0.5)).add(0.0, 0.8, 0.0);
				server.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 4, 0.25, 0.25, 0.25, 0.05);
			}
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		int ticks = getUseDuration(stack, user) - ticksRemaining;
		// The tide gathering round your feet.
		double r = 2.4 - Math.min(ticks, SURGE_CHARGE) * 0.1;
		for (int i = 0; i < 4; i++) {
			double a = (ticks * 0.6 + i * Math.PI / 2.0);
			server.sendParticles(ParticleTypes.SPLASH, user.getX() + Math.cos(a) * r, user.getY() + 0.2, user.getZ() + Math.sin(a) * r, 2,
					0.05, 0.05, 0.05, 0.0);
		}
		if (ticks == SURGE_CHARGE) {
			server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingTime) {
		int ticks = getUseDuration(stack, user) - remainingTime;
		if (!(user instanceof Player player) || ticks < SURGE_CHARGE) {
			return false;
		}
		if (level instanceof ServerLevel server) {
			CrescentWaves.ring(server, player, SURGE_REACH, SURGE_DAMAGE);
			player.clearFire();
			stack.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.2f,
					0.7f);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2f,
					0.8f);
			server.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(), 60, 1.0, 0.6, 1.0, 0.3);
		}
		player.getCooldowns().addCooldown(stack, SURGE_COOLDOWN);
		if (!level.isClientSide()) {
			player.swing(InteractionHand.MAIN_HAND, stack.getAttackAnimation(), true);
		}
		return true;
	}

	@Override
	public void swung(ServerLevel level, ServerPlayer player, ItemStack stack) {
		boolean night = MoonbladeItem.night(level);
		float damage = WAVE_SHARE * ((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + (player.isInWaterOrRain() ? WET_BONUS : 0.0f));
		CrescentWaves.crescent(level, player, night ? CrescentWaves.Kind.MOON_TIDE : CrescentWaves.Kind.TIDE, night ? NIGHT_REACH : WAVE_REACH,
				damage);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.7f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 0.8f);
		if (night) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f,
					1.6f);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND && owner instanceof LivingEntity living && living.isInWater() && owner.tickCount % 10 == 0) {
			living.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 30, 0, true, false, true));
			living.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 30, 0, true, false, true));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.leviathan_blade.wet", "item.tomyen.leviathan_blade.lunge", "item.tomyen.leviathan_blade.wave",
				"item.tomyen.leviathan_blade.surge");
	}
}
