package com.jheinz.tomyen.bloomery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Working a bloom on an anvil: right-click any anvil holding blooms and each click is a hammer blow, a short beat
 * apart. {@link #BLOWS} blows drive the slag out of one bloom and leave a Bloom Steel Ingot. The blows so far ride on
 * the held stack ({@link Bloomery#BLOOM_HITS}), so they're kept if you look away.
 */
public final class BloomHammering {
	public static final int BLOWS = 4;
	/** Ticks between blows. */
	public static final int BEAT = 7;

	private BloomHammering() {
	}

	static InteractionResult useOnBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Bloomery.BLOOM) || player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(BlockTags.ANVIL)) {
			return InteractionResult.PASS;
		}
		// Either way the anvil's screen stays shut: this click is a blow, or a beat too soon for one.
		if (player.getCooldowns().isOnCooldown(held) || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		player.getCooldowns().addCooldown(held, BEAT);
		BlockPos pos = hit.getBlockPos();
		double x = hit.getLocation().x, y = pos.getY() + 1.02, z = hit.getLocation().z;
		int blows = held.getOrDefault(Bloomery.BLOOM_HITS, 0) + 1;
		server.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.08, 0.02, 0.08, 0.25);
		server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Bloomery.BLOOM), x, y, z, 3, 0.05, 0.02, 0.05, 0.08);
		if (blows < BLOWS) {
			held.set(Bloomery.BLOOM_HITS, blows);
			server.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.45f, 1.0f + blows * 0.12f);
			return InteractionResult.SUCCESS;
		}
		held.remove(Bloomery.BLOOM_HITS);
		held.shrink(1);
		ItemStack ingot = new ItemStack(Bloomery.SET.ingot);
		if (held.isEmpty()) {
			player.setItemInHand(hand, ingot);
		} else {
			player.getInventory().placeItemBackInInventory(ingot, Prediction.SERVER_ONLY);
		}
		server.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.0f);
		server.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.05, 0.0, 0.05, 0.0);
		return InteractionResult.SUCCESS;
	}
}
