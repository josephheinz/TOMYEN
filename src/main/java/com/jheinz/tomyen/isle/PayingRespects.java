package com.jheinz.tomyen.isle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import com.jheinz.tomyen.holy.CrucifixItem;
import com.jheinz.tomyen.isle.shade.IsleShades;
import com.jheinz.tomyen.silver.EvilBane;
import com.jheinz.tomyen.starforge.Infusion;

/**
 * Using a gravestone. Only its owner can; anyone else is told whose it is.
 *
 * <p><b>Paying respects:</b> the owner sneaks and holds use on the stone. Souls rise off it, thicker as it goes, to a
 * rising hum; after {@value #RESPECT_TICKS} ticks (five seconds) they're pulled down to the Isle. Letting go, or
 * looking away, starts it over. No item is needed, so nobody can be locked out of their own hoard.
 *
 * <p><b>An offering:</b> using the stone with something holy or soul-touched in hand (the crucifixes, holy silver, Soul
 * Charges, soul steel, anything Soulbound: see the {@code tomyen:isle_holy_offerings} and
 * {@code tomyen:isle_soul_offerings} item tags) takes them down at once, with Soul Ward for their first minute there.
 * It costs a little of the offering: one from a stack, a twentieth of a tool's durability, or a minute's cooldown for
 * a relic like the crucifix that's neither.
 *
 * <p><b>A Shade Heart</b> (a boss shade's drop) is checked before any other offering: it draws the hoard up a depth
 * and then takes its owner down as an offering would. It's only used up if the hoard could rise; at the Shore it stays
 * in hand and nobody goes anywhere.
 */
public final class PayingRespects {
	public static final int RESPECT_TICKS = 100;
	/** Use repeats every 4 ticks while held; a gap longer than this means it was let go. */
	private static final int GAP = 10;
	private static final int RELIC_COOLDOWN = 1200;

	private record Respects(BlockPos pos, long start, long last) {
	}

	private static final Map<UUID, Respects> PAYING = new HashMap<>();

	private PayingRespects() {
	}

	static void initialize() {
		UseBlockCallback.EVENT.register(PayingRespects::use);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PAYING.clear());
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(Isle.GRAVESTONE)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.CONSUME;
		}
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			useServer(serverPlayer, serverLevel, hit.getBlockPos());
		}
		return InteractionResult.CONSUME;
	}

	private static void useServer(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof GravestoneBlockEntity grave)) {
			return;
		}
		if (grave.owner() == null || !grave.owner().equals(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.not_your_grave", grave.ownerName()));
			return;
		}
		if (!grave.leadsToHoard(level)) {
			GravestoneBlock.crumble(level, pos, level.getBlockState(pos));
			return;
		}
		GlobalPos from = GlobalPos.of(level.dimension(), pos);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(IsleShades.SHADE_HEART)) {
				PAYING.remove(player.getUUID());
				if (!raiseHoard(player, level)) {
					level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
					return;
				}
				level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 0.7f);
				level.playSound(null, pos, Isle.OFFERING, SoundSource.PLAYERS, 1.0f, 0.8f);
				level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 40, 0.3, 0.5, 0.3, 0.08);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 30, 0.3, 0.4, 0.3,
						0.05);
				if (!player.hasInfiniteMaterials()) {
					held.shrink(1);
				}
				IsleDimension.sendIn(player, from, true);
				return;
			}
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (isOffering(held) && !player.getCooldowns().isOnCooldown(held)) {
				PAYING.remove(player.getUUID());
				level.playSound(null, pos, Isle.OFFERING, SoundSource.PLAYERS, 1.0f, 1.0f);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 30, 0.3, 0.4, 0.3,
						0.05);
				spend(player, held, hand);
				IsleDimension.sendIn(player, from, true);
				return;
			}
		}
		if (!player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.how_to_pay_respects"));
			return;
		}
		long now = level.getGameTime();
		Respects respects = PAYING.get(player.getUUID());
		if (respects == null || !respects.pos().equals(pos) || now - respects.last() > GAP) {
			respects = new Respects(pos.immutable(), now, now);
		}
		respects = new Respects(respects.pos(), respects.start(), now);
		PAYING.put(player.getUUID(), respects);
		long held = now - respects.start();
		float progress = Math.min(1.0f, held / (float) RESPECT_TICKS);

		int souls = 1 + (int) (progress * 6);
		level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, souls, 0.35, 0.35, 0.35, 0.015 + 0.03 * progress);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 1 + souls / 2, 0.4, 0.05, 0.4,
				0.01);
		level.playSound(null, pos, Isle.RESPECTS, SoundSource.PLAYERS, 0.5f + 0.5f * progress, 0.5f + progress);
		player.sendOverlayMessage(bar(progress));

		if (held >= RESPECT_TICKS) {
			PAYING.remove(player.getUUID());
			IsleDimension.sendIn(player, from, false);
		}
	}

	/**
	 * A Shade Heart offered at the stone draws the hoard up a depth (its Revenant re-forms up there) before the trip
	 * down. False, and nothing spent, if it's already as shallow as it goes.
	 */
	private static boolean raiseHoard(ServerPlayer player, ServerLevel level) {
		Hoard hoard = Hoards.get(level.getServer(), player.getUUID());
		if (hoard == null || hoard.depth().index == 0) {
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.heart_shallowest"));
			return false;
		}
		IsleDepth up = IsleDepth.of(hoard.depth().index - 1);
		RevenantManager.dismiss(level.getServer(), player.getUUID());
		Hoards.put(level.getServer(), hoard.withDepth(up));
		player.sendOverlayMessage(Component.translatable("message.tomyen.isle.heart_raises", up.displayName()).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** "Paying respects" over a bar of ten pips. */
	private static Component bar(float progress) {
		int filled = Math.round(progress * 10);
		return Component.translatable("message.tomyen.isle.paying_respects").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(" " + "▮".repeat(filled)).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("▯".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Whether this is holy or soul-touched enough to open the way at once. */
	public static boolean isOffering(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Isle.HOLY_OFFERINGS) || stack.is(Isle.SOUL_OFFERINGS) || stack.getItem() instanceof CrucifixItem) {
			return true;
		}
		EvilBane bane = EvilBane.of(stack);
		return bane != null && bane.holy() || Infusion.of(stack) == Infusion.SOULBOUND;
	}

	/** A little of the offering is used up. */
	private static void spend(ServerPlayer player, ItemStack stack, InteractionHand hand) {
		if (player.hasInfiniteMaterials()) {
			return;
		}
		if (stack.isDamageableItem()) {
			stack.hurtAndBreak(Math.max(1, stack.getMaxDamage() / 20), player, hand);
		} else if (stack.getMaxStackSize() > 1) {
			stack.shrink(1);
		} else {
			player.getCooldowns().addCooldown(stack, RELIC_COOLDOWN);
		}
	}
}
