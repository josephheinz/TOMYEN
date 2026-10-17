package com.jheinz.tomyen.accountant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jheinz.tomyen.bank.Banking;
import com.jheinz.tomyen.bank.Debt;
import com.jheinz.tomyen.bank.DebtCollection;

/**
 * The Accountant's side of the bank: loans, audits and what he takes, and the bill for killing him. It all goes through
 * the player's ordinary account (see {@link Debt} and {@link DebtCollection}), so a loan is due with the rest of their
 * debt, the warnings count it, and the collectors come for it.
 * <ul>
 * <li>Loans of {@link #PRINCIPALS} emeralds, paid out at once; the principal plus {@link #INTEREST_PERCENT}% is added
 * to what's owed. A first loan opens the account (the usual five-day term); later ones don't move the deadline. No loan
 * may take the total owed past {@link #CAP}, and none is made to an account in collections.</li>
 * <li>An audit reads out what's owed and the time left. An overdue player (in collections) has every emerald and
 * emerald block on them taken toward the debt, change given in emeralds.</li>
 * <li>Killing him adds {@link #FINE} emeralds to your account ({@link #JUNIOR_FINE} for each junior). If you're already
 * in collections, killing the Accountant himself instead brings the next wave of collectors now.</li>
 * </ul>
 */
public final class Loans {
	/** The loans on offer, in emeralds paid out. */
	public static final int[] PRINCIPALS = {16, 32, 64};
	public static final int INTEREST_PERCENT = 25;
	/** He won't lend if it would put the total owed over this. */
	public static final int CAP = 200;
	public static final int FINE = 24;
	public static final int JUNIOR_FINE = 4;
	private static final int AUDIT_LINES = 4;

	private Loans() {
	}

	/** What a loan of this many emeralds adds to the debt. */
	public static int repayment(int principal) {
		return principal + principal * INTEREST_PERCENT / 100;
	}

	/** How much more may be added to this player's account before the cap. Works on both sides. */
	public static int headroom(Player player) {
		return Math.max(0, CAP - Debt.owed(player));
	}

	/** Whether the player may take loan option {@code option}. Works on both sides. */
	public static boolean canBorrow(Player player, int option) {
		return option >= 0 && option < PRINCIPALS.length && !Debt.inCollections(player)
				&& repayment(PRINCIPALS[option]) <= headroom(player);
	}

	/** In his voice: the speaker's name in grey, then the line. */
	public static MutableComponent voice(Component line) {
		return Component.empty().append(Component.translatable("entity.tomyen.accountant").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(line);
	}

	/** Pays out loan option {@code option}, adding it with interest to the player's debt. */
	public static void borrow(ServerPlayer player, int option) {
		if (option < 0 || option >= PRINCIPALS.length) {
			return;
		}
		int principal = PRINCIPALS[option];
		int owe = repayment(principal);
		if (Debt.inCollections(player)) {
			player.sendSystemMessage(voice(Component.translatable("message.tomyen.accountant.loan_frozen").withStyle(ChatFormatting.RED)));
			return;
		}
		if (owe > headroom(player)) {
			player.sendSystemMessage(voice(Component.translatable("message.tomyen.accountant.loan_declined", Debt.owed(player) + owe, CAP)
					.withStyle(ChatFormatting.RED)));
			return;
		}
		addDebt(player, owe);
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, principal), Prediction.SERVER_ONLY);
		player.sendSystemMessage(voice(Component.translatable("message.tomyen.accountant.loan", principal, owe, Debt.owed(player))
				.withStyle(ChatFormatting.GOLD)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), Accountants.SCRIBBLE, SoundSource.NEUTRAL, 0.9f, 1.0f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
				0.5f, 1.4f);
	}

	/** Adds emeralds to the player's debt, opening an account (with the full term to pay) if they had none. */
	static void addDebt(ServerPlayer player, int emeralds) {
		Debt debt = Debt.of(player);
		player.setAttached(Banking.DEBT, debt == null ? Debt.opened(emeralds) : debt.withOwed(debt.owed() + emeralds));
		if (debt == null) {
			player.sendSystemMessage(Component.translatable("message.tomyen.bank.account_opened", DebtCollection.formatTime(DebtCollection.TERM_TICKS))
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Reads the player their account, in his dry way. */
	public static void audit(ServerPlayer player, RandomSource random) {
		Debt debt = Debt.of(player);
		if (debt == null) {
			return;
		}
		Component line = debt.hunting()
				? Component.translatable("message.tomyen.accountant.overdue", debt.owed()).withStyle(ChatFormatting.RED)
				: Component.translatable("message.tomyen.accountant.audit." + random.nextInt(AUDIT_LINES), debt.owed(),
						DebtCollection.formatTime(debt.ticksLeft())).withStyle(ChatFormatting.YELLOW);
		player.sendSystemMessage(voice(line));
	}

	/**
	 * Takes every emerald (and emerald block, worth nine) the player carries, up to what they owe, and pays it toward
	 * their debt. Returns how many emeralds' worth he took.
	 */
	public static int seize(ServerPlayer player, Entity accountant) {
		int owed = Debt.owed(player);
		if (owed <= 0) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int taken = 0;
		for (int i = 0; i < inventory.getContainerSize() && taken < owed; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.EMERALD)) {
				int take = Math.min(stack.getCount(), owed - taken);
				stack.shrink(take);
				taken += take;
			}
		}
		int change = 0;
		for (int i = 0; i < inventory.getContainerSize() && taken < owed; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.EMERALD_BLOCK)) {
				int blocks = Math.min(stack.getCount(), (owed - taken + 8) / 9);
				stack.shrink(blocks);
				int paid = Math.min(blocks * 9, owed - taken);
				change += blocks * 9 - paid;
				taken += paid;
			}
		}
		if (change > 0) {
			inventory.placeItemBackInInventory(new ItemStack(Items.EMERALD, change), Prediction.SERVER_ONLY);
		}
		if (taken == 0) {
			player.sendSystemMessage(voice(Component.translatable("message.tomyen.accountant.broke").withStyle(ChatFormatting.GRAY)));
			return 0;
		}
		player.sendSystemMessage(voice(Component.translatable("message.tomyen.accountant.seized", taken).withStyle(ChatFormatting.RED)));
		accountant.level().playSound(null, accountant.getX(), accountant.getY(), accountant.getZ(), SoundEvents.ITEM_PICKUP,
				SoundSource.NEUTRAL, 0.8f, 0.8f);
		DebtCollection.repay(player, taken);
		return taken;
	}

	/** The bill for killing an Accountant (or one of his juniors). */
	public static void penalize(ServerPlayer player, boolean junior) {
		Debt debt = Debt.of(player);
		if (!junior && debt != null && debt.hunting()) {
			player.setAttached(Banking.DEBT, debt.withHunt(true, debt.wave(), 0));
			player.sendSystemMessage(Component.translatable("message.tomyen.accountant.escalated").withStyle(ChatFormatting.DARK_RED));
			return;
		}
		int fine = junior ? JUNIOR_FINE : FINE;
		addDebt(player, fine);
		player.sendSystemMessage(Component.translatable(junior ? "message.tomyen.accountant.fined_junior" : "message.tomyen.accountant.fined", fine)
				.withStyle(ChatFormatting.RED));
	}
}
