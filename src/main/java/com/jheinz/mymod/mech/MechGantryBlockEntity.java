package com.jheinz.mymod.mech;

import java.util.List;

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
 * The parts fitted in a Mech Gantry: a slot each (see {@link MechLoadout}), one item to a slot. Every change is
 * sent to clients, which draw the walker in the frame as it's assembled.
 */
public class MechGantryBlockEntity extends BaseContainerBlockEntity {
	private static final Component TITLE = Component.translatable("container.mymod.mech_gantry");

	private NonNullList<ItemStack> items = NonNullList.withSize(MechLoadout.SIZE, ItemStack.EMPTY);

	public MechGantryBlockEntity(BlockPos pos, BlockState state) {
		super(Mechs.GANTRY_ENTITY, pos, state);
	}

	public List<ItemStack> items() {
		return items;
	}

	/** The look of what's fitted, for drawing (see {@link MechLoadout#look}). */
	public int look() {
		return MechLoadout.look(items);
	}

	/**
	 * Takes every part out: the walker has walked off with them. Unlike {@link #clearContent} (which only empties the
	 * list) this marks the gantry changed, so the emptied gantry is saved and sent to clients. Without it clients kept
	 * drawing the fitted walker, and a chunk saved before the walk-out brought the parts back on reload.
	 */
	void takeParts() {
		for (int i = 0; i < MechLoadout.SIZE; i++) {
			items.set(i, ItemStack.EMPTY);
		}
		setChanged();
	}

	void setParts(List<ItemStack> parts) {
		for (int i = 0; i < MechLoadout.SIZE; i++) {
			items.set(i, parts.get(i).copy());
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
		return MechLoadout.SIZE;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return MechLoadout.fits(slot, stack);
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MechGantryMenu(containerId, inventory, this);
	}
}
