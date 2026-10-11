package com.jheinz.mymod.holy;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Punch (attack) to send out a slow orb of light; hold use to charge, and let go to fire a beam of light
 * straight ahead. The longer the charge (up to {@link #FULL_CHARGE_TICKS}), the stronger the beam. Holy
 * light only hurts the undead. Vampires can't come within {@link #WARD_RADIUS} blocks of anyone holding one.
 */
public class CrucifixItem extends Item {
	public static final int MIN_CHARGE_TICKS = 10;
	public static final int FULL_CHARGE_TICKS = 40;
	private static final int ORB_COOLDOWN = 12;
	private static final int BEAM_COOLDOWN = 30;

	/** Vampires can't come closer than this to someone holding a crucifix. */
	public static final double WARD_RADIUS = 3.0;

	public CrucifixItem(Properties properties) {
		super(properties);
	}

	/** Whether the entity holds a crucifix in either hand. */
	public static boolean isHeldBy(LivingEntity entity) {
		return entity.getMainHandItem().getItem() instanceof CrucifixItem || entity.getOffhandItem().getItem() instanceof CrucifixItem;
	}

	/** Called on the server when the player punches with it (see ModNetworking.CrucifixPunch). */
	public void punch(ServerLevel level, Player player, ItemStack stack) {
		if (player.getCooldowns().isOnCooldown(stack)) {
			return;
		}
		// Straight down the line of sight: none of the player's own movement carries over, unlike a thrown item.
		Vec3 look = player.getViewVector(1.0f);
		LightOrb orb = new LightOrb(level, player);
		orb.shoot(look.x, look.y, look.z, LightOrb.SPEED, 0.0f);
		level.addFreshEntity(orb);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f,
				1.4f + level.getRandom().nextFloat() * 0.3f);
		player.getCooldowns().addCooldown(stack, ORB_COOLDOWN);
		// The puncher already swung on their own screen; show it to everyone else.
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		if (!level.isClientSide()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	public static float charge(int ticksHeld) {
		return Math.min(1.0f, (float) ticksHeld / FULL_CHARGE_TICKS);
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		int held = getUseDuration(stack, entity) - ticksRemaining;
		if (level.isClientSide()) {
			// Motes of light drawn in to the crucifix, more of them as it charges.
			if (entity instanceof Player player && level.getRandom().nextFloat() < 0.3f + 0.7f * charge(held)) {
				Vec3 hand = HolyLight.handPosition(player);
				Vec3 from = hand.add((level.getRandom().nextDouble() - 0.5) * 2.0, (level.getRandom().nextDouble() - 0.5) * 2.0,
						(level.getRandom().nextDouble() - 0.5) * 2.0);
				Vec3 pull = hand.subtract(from).scale(0.12);
				level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, pull.x, pull.y, pull.z);
			}
		} else if (held == FULL_CHARGE_TICKS) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.2f);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksRemaining) {
		int held = getUseDuration(stack, entity) - ticksRemaining;
		if (held < MIN_CHARGE_TICKS || !(entity instanceof Player player)) {
			return false;
		}
		if (level instanceof ServerLevel serverLevel) {
			HolyLight.fireBeam(serverLevel, player, charge(held));
		}
		player.getCooldowns().addCooldown(stack, BEAM_COOLDOWN);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.crucifix.punch").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.crucifix.charge").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.crucifix.undead").withStyle(ChatFormatting.GOLD));
	}
}
