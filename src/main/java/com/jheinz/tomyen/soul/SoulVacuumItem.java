package com.jheinz.tomyen.soul;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A handheld vacuum gun with a glass soul canister. Hold use to suck: the mob nearest the middle of an
 * {@link #RANGE}-block cone from the nozzle is dragged in, harder the longer it's held, and captured when it reaches
 * the nozzle or is badly hurt (see {@link SoulCapture}). Its soul goes in the canister, which holds
 * {@link SoulCanister#CAPACITY}; sneak-use lets them out again as Soul Charges, one a soul (two a weak soul).
 */
public class SoulVacuumItem extends Item {
	public static final double RANGE = 8.0;
	private static final int RELEASE_COOLDOWN = 10;
	private static final int BAR_COLOR = 0x4FE6F0;

	public SoulVacuumItem(Properties properties) {
		super(properties);
	}

	public static SoulCanister canister(ItemStack stack) {
		return stack.getOrDefault(Souls.SOUL_CANISTER, SoulCanister.EMPTY);
	}

	/** Where the nozzle is, as the world sees it: out in front of the holder's main hand. */
	public static Vec3 nozzle(LivingEntity holder) {
		Vec3 look = holder.getViewVector(1.0f);
		float side = holder.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize().scale(side);
		return holder.getEyePosition().add(look.scale(1.0)).add(right.scale(0.3)).add(0.0, -0.3, 0.0);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		SoulCanister canister = canister(stack);
		if (player.isSecondaryUseActive()) {
			return release(level, player, stack, canister);
		}
		if (canister.isFull()) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("item.tomyen.soul_vacuum.full").withStyle(ChatFormatting.AQUA));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	private InteractionResult release(Level level, Player player, ItemStack stack, SoulCanister canister) {
		int charges = canister.charges();
		if (charges == 0) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("item.tomyen.soul_vacuum.empty").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			stack.set(Souls.SOUL_CANISTER, canister.afterRelease());
			ItemStack released = new ItemStack(Souls.SOUL_CHARGE, charges);
			if (!player.addItem(released)) {
				player.drop(released, false, Prediction.SERVER_ONLY);
			}
			Vec3 nozzle = nozzle(player);
			serverLevel.sendParticles(ParticleTypes.SOUL, nozzle.x, nozzle.y, nozzle.z, 4 + charges, 0.2, 0.2, 0.2, 0.03);
			level.playSound(null, nozzle.x, nozzle.y, nozzle.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2f, 0.8f);
			level.playSound(null, nozzle.x, nozzle.y, nozzle.z, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 0.7f);
		}
		player.getCooldowns().addCooldown(stack, RELEASE_COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	/** How long the trigger's been held, in ticks. */
	public int heldTicks(ItemStack stack, LivingEntity user, int ticksRemaining) {
		return getUseDuration(stack, user) - ticksRemaining;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (!(entity instanceof Player player)) {
			return;
		}
		if (level instanceof ServerLevel serverLevel) {
			SoulCapture.suck(serverLevel, player, stack);
			return;
		}
		// A swirl of soul fire drawn up the cone into the nozzle, thicker as the motor spins up.
		RandomSource random = level.getRandom();
		float spinUp = Mth.clamp(heldTicks(stack, entity, ticksRemaining) / 20.0f, 0.2f, 1.0f);
		Vec3 nozzle = nozzle(player);
		Vec3 look = player.getViewVector(1.0f);
		for (int i = 0; i < 3; i++) {
			if (random.nextFloat() > spinUp) {
				continue;
			}
			double along = 1.5 + random.nextDouble() * (RANGE - 2.0);
			double spread = along * 0.25;
			Vec3 from = nozzle.add(look.scale(along)).add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread,
					(random.nextDouble() - 0.5) * spread);
			Vec3 pull = nozzle.subtract(from).scale(0.09 + 0.06 * spinUp);
			level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z,
					pull.x, pull.y, pull.z);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksRemaining) {
		if (entity instanceof Player player && !level.isClientSide()) {
			SoulCapture.stop(player);
		}
		return false;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return canister(stack).total() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0f * canister(stack).fill());
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		SoulCanister canister = canister(stack);
		tooltip.accept(Component.translatable("item.tomyen.soul_vacuum.souls", canister.total(), SoulCanister.CAPACITY)
				.withStyle(ChatFormatting.AQUA));
		if (canister.weakSouls() > 0) {
			tooltip.accept(Component.translatable("item.tomyen.soul_vacuum.weak_souls", canister.weakSouls()).withStyle(ChatFormatting.DARK_AQUA));
		}
		tooltip.accept(Component.translatable("item.tomyen.soul_vacuum.use").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.soul_vacuum.release").withStyle(ChatFormatting.GRAY));
	}
}
