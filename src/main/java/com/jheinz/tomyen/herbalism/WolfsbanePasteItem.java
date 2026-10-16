package com.jheinz.tomyen.herbalism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Wolfsbane Paste: with a blade in the other hand, use it to rub {@value Wolfsbane#COATING_HITS} hits of wolfsbane
 * onto the blade (see {@link Wolfsbane}). Also crafts Wolfsbane Arrows.
 */
public class WolfsbanePasteItem extends HerbalItem {
	public WolfsbanePasteItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack paste = player.getItemInHand(hand);
		ItemStack blade = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (!Wolfsbane.canCoat(blade)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.tomyen.wolfsbane_paste.no_blade").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}
		if (Wolfsbane.coating(blade) >= Wolfsbane.MAX_COATING) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			blade.set(Herbalism.WOLFSBANE_COATING, Math.min(Wolfsbane.MAX_COATING, Wolfsbane.coating(blade) + Wolfsbane.COATING_HITS));
			paste.consume(1, player);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 0.8f, 0.7f);
			server.sendParticles(new DustParticleOptions(0x6A3FA0, 1.0f), player.getX(), player.getY(0.6), player.getZ(), 10,
					0.3, 0.3, 0.3, 0.0);
		}
		return InteractionResult.SUCCESS;
	}
}
