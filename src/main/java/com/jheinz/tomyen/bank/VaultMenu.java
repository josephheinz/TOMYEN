package com.jheinz.tomyen.bank;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * A bank vault opened at an ATM: a three-row chest over the player's inventory, plus a "Pay debt" button
 * (button {@link #PAY_DEBT}) that pays the player's debt from the emeralds in the vault, on the server.
 * Closes when the player walks away from the ATM.
 */
public class VaultMenu extends ChestMenu {
	public static final int PAY_DEBT = 0;

	private final ContainerLevelAccess access;

	/** Client side: the slots are filled in by the server. */
	public VaultMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(Vaults.SIZE), ContainerLevelAccess.NULL);
	}

	public VaultMenu(int containerId, Inventory inventory, Container vault, ContainerLevelAccess access) {
		super(Banking.VAULT_MENU, containerId, inventory, vault, Vaults.SIZE / 9);
		this.access = access;
	}

	/** Emeralds in the vault, emerald blocks counting nine. */
	public int emeralds() {
		return Vaults.emeralds(getContainer());
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Banking.ATM);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId != PAY_DEBT) {
			return false;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			Vaults.payDebt(serverPlayer);
		}
		return true;
	}
}
