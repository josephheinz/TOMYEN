package com.jheinz.tomyen.isle;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Soul Cache: up to {@value #SIZE} stacks of a beaten Revenant's hoard, and (in the first one) the experience that
 * went with it. Only its owner can open it; the experience is theirs the first time they do. Hoppers can't reach into
 * it (no slots are open to any side). Once its owner has emptied it and closed it, it fades away.
 */
public class SoulCacheBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int SIZE = 54;
	private static final int[] NO_SLOTS = new int[0];

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private @Nullable UUID owner;
	private String ownerName = "";
	private int experience;

	public SoulCacheBlockEntity(BlockPos pos, BlockState state) {
		super(Isle.SOUL_CACHE_ENTITY, pos, state);
	}

	public void fill(UUID owner, String ownerName, List<ItemStack> stacks, int experience) {
		this.owner = owner;
		this.ownerName = ownerName;
		this.experience = experience;
		for (int i = 0; i < SIZE; i++) {
			items.set(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
		setChanged();
	}

	public boolean isOwner(Player player) {
		return owner != null && owner.equals(player.getUUID());
	}

	public String ownerName() {
		return ownerName;
	}

	@Override
	public boolean canOpen(Player player) {
		return isOwner(player) && super.canOpen(player);
	}

	@Override
	public boolean stillValid(Player player) {
		return isOwner(player) && super.stillValid(player);
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (!(user.getLivingEntity() instanceof ServerPlayer player) || level == null) {
			return;
		}
		level.playSound(null, worldPosition, Isle.SOUL_CACHE_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
		if (experience > 0 && isOwner(player)) {
			player.giveExperiencePoints(experience);
			level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.8f);
			experience = 0;
			setChanged();
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (level instanceof ServerLevel serverLevel && isEmpty() && experience == 0) {
			SoulCacheBlock.fade(serverLevel, worldPosition);
		}
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.tomyen.soul_cache", ownerName);
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return ChestMenu.sixRows(containerId, inventory, this);
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> newItems) {
		items = newItems;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return NO_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return false;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("owner_name", "");
		experience = input.getIntOr("experience", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
		output.putString("owner_name", ownerName);
		output.putInt("experience", experience);
	}
}
