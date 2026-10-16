package com.jheinz.tomyen.fae;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A Fae Ring you carry. Hold use for {@value #USE_TICKS} ticks, at any hour, in the Overworld or the Feywild, and it
 * does what standing in a ring does: in, remembering where you were, or back out to there. Each trip costs a point of
 * durability, and it rests {@value #COOLDOWN} ticks after.
 */
public class FaeRingCharmItem extends Item {
	private static final int USE_TICKS = 60;
	private static final int COOLDOWN = 200;

	public FaeRingCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Feywild.is(level) && level.dimension() != Level.OVERWORLD) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.fae_ring_charm.wrong_world"));
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	/** A ring of sparkles draws in round the holder, and chimes rise, as it charges. */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		float progress = 1.0f - (float) ticksRemaining / USE_TICKS;
		if (level.isClientSide()) {
			for (int i = 0; i < 1 + (int) (progress * 4); i++) {
				float angle = level.getRandom().nextFloat() * Mth.TWO_PI;
				double r = 2.5 * (1.0 - 0.7 * progress);
				level.addParticle(Fae.SPARKLE, entity.getX() + Mth.cos(angle) * r, entity.getY() + level.getRandom().nextDouble() * 1.8 * progress,
						entity.getZ() + Mth.sin(angle) * r, 0.0, 0.02, 0.0);
			}
		} else if (ticksRemaining % 20 == 0) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), Fae.RING_CHIME, SoundSource.PLAYERS, 0.6f, 0.8f + progress);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			stack.hurtAndBreak(1, player, player.getUsedItemHand());
			FaeRings.travel(player);
		}
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.fae_ring_charm.use").withStyle(ChatFormatting.GRAY));
	}
}
