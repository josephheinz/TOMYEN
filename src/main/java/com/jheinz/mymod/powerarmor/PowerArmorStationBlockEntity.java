package com.jheinz.mymod.powerarmor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The suit on a Power Armor Station's rack: a slot per piece (each takes only its own piece, of any mark) and
 * one for the fusion core. Every change is sent to clients, which draw the suit standing in the frame.
 */
public class PowerArmorStationBlockEntity extends BaseContainerBlockEntity {
	private static final Component TITLE = Component.translatable("container.mymod.power_armor_station");

	private NonNullList<ItemStack> items = NonNullList.withSize(PowerArmorSuit.SIZE, ItemStack.EMPTY);

	public PowerArmorStationBlockEntity(BlockPos pos, BlockState state) {
		super(PowerArmors.STATION_ENTITY, pos, state);
	}

	/** Whether a stack belongs in a rack slot: its own piece, or a fusion core in the core slot. */
	public static boolean fits(int slot, ItemStack stack) {
		if (slot == PowerArmorSuit.CORE) {
			return stack.getItem() instanceof FusionCoreItem;
		}
		return stack.getItem() instanceof PowerArmorPieceItem piece && piece.piece().slot() == slot;
	}

	public PowerArmorSuit suit() {
		return PowerArmorSuit.of(items);
	}

	public void setSuit(PowerArmorSuit suit) {
		for (int i = 0; i < PowerArmorSuit.SIZE; i++) {
			items.set(i, suit.get(i).copy());
		}
		setChanged();
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected Component getDefaultName() {
		return TITLE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return PowerArmorSuit.SIZE;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return fits(slot, stack);
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new PowerArmorStationMenu(containerId, inventory, this);
	}
}
