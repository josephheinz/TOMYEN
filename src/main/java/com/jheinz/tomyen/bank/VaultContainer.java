package com.jheinz.tomyen.bank;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A player's bank vault while they're online (see {@link Vaults#of}): loaded from the player, and written
 * back to them on every change, so what's saved is never behind what's in the vault.
 */
public class VaultContainer extends SimpleContainer {
	private final ServerPlayer owner;

	VaultContainer(ServerPlayer owner) {
		super(Vaults.SIZE);
		this.owner = owner;
		owner.getAttachedOrElse(Banking.VAULT, ItemContainerContents.EMPTY).copyInto(getItems());
	}

	public ServerPlayer owner() {
		return owner;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		ItemContainerContents contents = ItemContainerContents.fromItems(getItems());
		if (contents.equals(ItemContainerContents.EMPTY)) {
			owner.removeAttached(Banking.VAULT);
		} else {
			owner.setAttached(Banking.VAULT, contents);
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		super.stopOpen(user);
		setChanged();
	}
}
