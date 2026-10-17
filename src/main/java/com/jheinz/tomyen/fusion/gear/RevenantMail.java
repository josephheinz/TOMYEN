package com.jheinz.tomyen.fusion.gear;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The Revenant Mail's death ward: once per in-game day, dying while wearing it keeps all your gear on you, as the
 * {@code keepInventory} game rule would but for you alone, instead of it spilling on the ground (or, once the Isle of
 * the Damned is in, being carried off to it). Your experience still drops as usual.
 *
 * <h2>The day</h2>
 * A day is the overworld clock's day ({@code getOverworldClockTime() / 24000}), so the ward comes back at each sunrise
 * (time 0). The day it was last spent is kept on the mail itself ({@link FusionGear#REVENANT_SPENT_DAY}), so it
 * travels with the mail; two mails are two wards.
 *
 * <h2>How it works today</h2>
 * PlayerRevenantMailMixin calls {@link #tryKeepGear} at the head of {@code Player.dropEquipment} (where vanilla checks
 * keepInventory) and, if it says yes, cancels the drop. The dead player is marked with {@link FusionGear#REVENANT_KEPT}
 * (saved, so a restart on the death screen doesn't lose anything), and at respawn ({@code ServerPlayerEvents.COPY_FROM})
 * the old inventory, armor and offhand included, is copied onto the new player, as vanilla does under keepInventory.
 * With keepInventory on, nothing is spent: vanilla is keeping everything anyway.
 *
 * <h2>For the Isle of the Damned (wire this in when it merges)</h2>
 * The Isle's death capture, wherever it takes a dying player's items (before it empties or copies the inventory), should
 * start with:
 * <pre>{@code
 * if (RevenantMail.tryKeepGear(player)) {
 *     return; // the mail keeps the gear on the player: capture nothing, and the respawn hands it all back
 * }
 * }</pre>
 * {@link #tryKeepGear} is idempotent within one death: the first call spends the day's charge and marks the player;
 * any later call during the same death (the Isle's, then this mod's own dropEquipment hook, in either order) answers
 * true again without spending anything. So the Isle can call it before or after vanilla's drop and both paths agree, and
 * nothing here needs removing. {@link #isKeepingGear} asks without spending. If the Isle captures in
 * {@code ServerPlayer.die} at HEAD, the gear is then still in the inventory when the dropEquipment hook cancels the drop,
 * and COPY_FROM restores it.
 */
public final class RevenantMail {
	private static final int ECTO = 0x7FF5D8;
	private static final int PALE = 0xE6FFF8;
	private static final int FUR = 0x8A8F96;

	private RevenantMail() {
	}

	static void initialize() {
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && isKeepingGear(oldPlayer) && !keepInventory(oldPlayer.level())) {
				newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && isKeepingGear(oldPlayer)) {
				rise(newPlayer);
			}
		});
	}

	/**
	 * Whether this dying player's gear stays on them: they wear the Revenant Mail in their chest slot and its ward is
	 * ready today (which spends it, with the effect and the message), or the ward was already spent on this death.
	 * Call it only for a player who is dying. False under the keepInventory game rule (nothing to keep).
	 */
	public static boolean tryKeepGear(ServerPlayer player) {
		if (isKeepingGear(player)) {
			return true;
		}
		ServerLevel level = player.level();
		ItemStack mail = player.getItemBySlot(EquipmentSlot.CHEST);
		if (keepInventory(level) || !mail.is(FusionGear.REVENANT_MAIL) || !isReady(mail, level)) {
			return false;
		}
		mail.set(FusionGear.REVENANT_SPENT_DAY, day(level));
		player.setAttached(FusionGear.REVENANT_KEPT, true);
		holdOn(level, player);
		return true;
	}

	/** Whether the mail already kept this (dead) player's gear: true from the moment it's spent until respawn. */
	public static boolean isKeepingGear(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(FusionGear.REVENANT_KEPT));
	}

	/** Whether this mail's ward is ready in this level's day. Works on either side. */
	public static boolean isReady(ItemStack mail, Level level) {
		Long spent = mail.get(FusionGear.REVENANT_SPENT_DAY);
		return spent == null || spent != day(level);
	}

	public static long day(Level level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	private static boolean keepInventory(ServerLevel level) {
		return level.getGameRules().get(GameRules.KEEP_INVENTORY);
	}

	/** At the death: a column of souls and ectoplasm, a grey ring of fur, the anchor's thrum. */
	private static void holdOn(ServerLevel level, ServerPlayer player) {
		double x = player.getX(), y = player.getY(), z = player.getZ();
		for (int i = 0; i < 24; i++) {
			float angle = i * Mth.TWO_PI / 24;
			level.sendParticles(new DustParticleOptions(i % 3 == 0 ? FUR : ECTO, 1.3f), x + Mth.cos(angle) * 0.9, y + 0.1,
					z + Mth.sin(angle) * 0.9, 1, 0.0, 0.05, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SOUL, x, y + 1.0, z, 30, 0.3, 1.0, 0.3, 0.06);
		level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.8, z, 12, 0.25, 0.6, 0.25, 0.04);
		level.sendParticles(new DustParticleOptions(PALE, 1.0f), x, y + 1.0, z, 30, 0.4, 0.9, 0.4, 0.0);
		level.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.6f);
		player.sendSystemMessage(Component.translatable("item.tomyen.revenant_mail.kept").withStyle(ChatFormatting.DARK_AQUA,
				ChatFormatting.ITALIC));
	}

	/** At respawn: the title, and the mail shimmering back to life around the wearer. */
	private static void rise(ServerPlayer player) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("item.tomyen.revenant_mail.title")
				.withStyle(ChatFormatting.AQUA)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("item.tomyen.revenant_mail.subtitle")
				.withStyle(ChatFormatting.GRAY)));
		ServerLevel level = player.level();
		Phasing.shimmer(level, player);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.5), player.getZ(), 16, 0.3, 0.6, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}
}
