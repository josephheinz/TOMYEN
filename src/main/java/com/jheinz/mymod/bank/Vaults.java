package com.jheinz.mymod.bank;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

/**
 * Bank vaults: every player's own {@link #SIZE}-slot store, the same from every ATM, like an ender chest.
 * The contents are kept on the player (see {@link Banking#VAULT}), so they last through logouts and
 * restarts, and through death without ever being dropped.
 * <p>
 * While its owner is online a vault is one shared {@link VaultContainer}, so an open vault screen and a
 * debit card being spent somewhere else always see the same emeralds. That also means a debit card can
 * only be charged while its holder is online.
 */
public final class Vaults {
	public static final int SIZE = 27;

	/** The online players' vaults. */
	private static final Map<UUID, VaultContainer> LIVE = new HashMap<>();
	private static @Nullable MinecraftServer server;
	/** Client side: the local player's own vault balance when given their id, otherwise -1. Set by the client. */
	private static ToIntFunction<UUID> clientBalance = id -> -1;

	private Vaults() {
	}

	public static void setClientBalance(ToIntFunction<UUID> balance) {
		clientBalance = balance;
	}

	/** The vault balance of this player, if it's the local player on the client; -1 otherwise. */
	public static int clientBalance(UUID owner) {
		return clientBalance.applyAsInt(owner);
	}

	/** The player's vault. */
	public static VaultContainer of(ServerPlayer player) {
		VaultContainer vault = LIVE.get(player.getUUID());
		// A respawned player is a new entity, carrying the contents over from the old one.
		if (vault == null || vault.owner() != player) {
			vault = new VaultContainer(player);
			LIVE.put(player.getUUID(), vault);
		}
		return vault;
	}

	/** Server side: this player's vault if they're online, else null. */
	public static @Nullable VaultContainer find(UUID owner) {
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner);
		return player == null ? null : of(player);
	}

	/** Emeralds among these stacks, an emerald block counting as nine. */
	public static int emeralds(Iterable<ItemStack> stacks) {
		int total = 0;
		for (ItemStack stack : stacks) {
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			} else if (stack.is(Items.EMERALD_BLOCK)) {
				total += stack.getCount() * 9;
			}
		}
		return total;
	}

	/**
	 * Whether this player's vault holds at least this many emeralds. Exact on the server (and false while
	 * they're offline). The client only knows the local player's own vault; anyone else's is assumed to be
	 * good for it, and the server corrects that if not.
	 */
	public static boolean canWithdraw(UUID owner, int emeralds) {
		if (server != null && server.isSameThread()) {
			VaultContainer vault = find(owner);
			return vault != null && emeralds(vault) >= emeralds;
		}
		int known = clientBalance(owner);
		return known < 0 || known >= emeralds;
	}

	/**
	 * Takes this many emeralds out of the vault, which must hold them: loose emeralds first, then emerald
	 * blocks, with the change going back in as emeralds (or to the owner, if the vault is full).
	 */
	public static void withdraw(VaultContainer vault, int emeralds) {
		int left = emeralds;
		for (ItemStack stack : vault) {
			if (left > 0 && stack.is(Items.EMERALD)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
		for (ItemStack stack : vault) {
			if (left > 0 && stack.is(Items.EMERALD_BLOCK)) {
				int blocks = Math.min(stack.getCount(), (left + 8) / 9);
				stack.shrink(blocks);
				left -= blocks * 9;
			}
		}
		vault.setChanged();
		if (left < 0) {
			ItemStack rest = vault.addItem(new ItemStack(Items.EMERALD, -left));
			if (!rest.isEmpty()) {
				vault.owner().getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
			}
		}
	}

	/** A debit card payment, already checked: takes the emeralds from the holder's vault and tells everyone involved. */
	public static void charge(CardHolder holder, int emeralds, ServerPlayer payer) {
		VaultContainer vault = find(holder.id());
		if (vault == null) {
			return;
		}
		withdraw(vault, emeralds);
		int left = emeralds(vault);
		if (payer.getUUID().equals(holder.id())) {
			payer.sendOverlayMessage(Component.translatable("message.mymod.bank.debit_paid", emeralds, left).withStyle(ChatFormatting.GREEN));
		} else {
			payer.sendOverlayMessage(Component.translatable("message.mymod.bank.debit_paid_other", emeralds, holder.name())
					.withStyle(ChatFormatting.GREEN));
			vault.owner().sendOverlayMessage(Component.translatable("message.mymod.bank.debit_used", payer.getName(), emeralds, left)
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** The vault screen's "Pay debt" button: pays off as much of the player's debt as their vault's emeralds cover. */
	public static void payDebt(ServerPlayer player) {
		int owed = Debt.owed(player);
		if (owed == 0) {
			player.sendSystemMessage(Component.translatable("message.mymod.bank.nothing_owed").withStyle(ChatFormatting.GREEN));
			return;
		}
		VaultContainer vault = of(player);
		int paid = Math.min(owed, emeralds(vault));
		if (paid == 0) {
			player.sendSystemMessage(Component.translatable("message.mymod.bank.vault_no_emeralds").withStyle(ChatFormatting.RED));
			return;
		}
		withdraw(vault, paid);
		DebtCollection.repay(player, paid);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				paid >= owed ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.2f);
	}

	public static void onServerStarted(MinecraftServer started) {
		server = started;
	}

	public static void onLeave(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	public static void onServerStopped() {
		LIVE.clear();
		server = null;
	}
}
