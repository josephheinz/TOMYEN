package com.jheinz.tomyen.crow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A crow's nest's hoard: {@value #SIZE} slots, opened as a two-row chest. */
public class CrowNestBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 18;
	private static final Component NAME = Component.translatable("container.tomyen.crow_nest");

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public CrowNestBlockEntity(BlockPos pos, BlockState state) {
		super(Crows.CROW_NEST_ENTITY, pos, state);
	}

	/** A crow drops its loot in: whatever doesn't fit comes back. */
	public ItemStack stash(ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int slot = 0; slot < SIZE && !rest.isEmpty(); slot++) {
			ItemStack in = items.get(slot);
			if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest)) {
				int moved = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
				in.grow(moved);
				rest.shrink(moved);
			}
		}
		for (int slot = 0; slot < SIZE && !rest.isEmpty(); slot++) {
			if (items.get(slot).isEmpty()) {
				items.set(slot, rest);
				rest = ItemStack.EMPTY;
			}
		}
		setChanged();
		return rest;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide()) {
			BlockState state = getBlockState();
			boolean loot = !isEmpty();
			if (state.hasProperty(CrowNestBlock.LOOT) && state.getValue(CrowNestBlock.LOOT) != loot) {
				level.setBlock(worldPosition, state.setValue(CrowNestBlock.LOOT, loot), 3);
			}
		}
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (!remove && level != null && !user.getLivingEntity().isSpectator()) {
			level.playSound(null, worldPosition, Crows.NEST_RUSTLE, SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	@Override
	public int getContainerSize() {
		return SIZE;
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
	protected Component getDefaultName() {
		return NAME;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x2, containerId, inventory, this, 2);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}
}
