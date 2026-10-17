package com.jheinz.tomyen.accountant;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * The Accountant's loan office: no slots, just the offer. Button {@code i} takes loan {@link Loans#PRINCIPALS}[i]; the
 * screen works out from the player's synced account which of them he'll make. Closes when the player walks away from
 * him, or he's gone.
 */
public class LoanMenu extends AbstractContainerMenu {
	private static final double REACH = 8.0;

	/** Server side only: who's lending. */
	private final @Nullable Accountant accountant;

	/** Client side. */
	public LoanMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null);
	}

	public LoanMenu(int containerId, Inventory inventory, @Nullable Accountant accountant) {
		super(Accountants.LOAN_MENU, containerId);
		this.accountant = accountant;
	}

	@Override
	public boolean stillValid(Player player) {
		return accountant == null
				|| accountant.isAlive() && !accountant.isTiny() && player.distanceToSqr(accountant) <= REACH * REACH;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId < 0 || buttonId >= Loans.PRINCIPALS.length) {
			return false;
		}
		if (player instanceof ServerPlayer server) {
			Loans.borrow(server, buttonId);
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slot) {
		return ItemStack.EMPTY;
	}
}
