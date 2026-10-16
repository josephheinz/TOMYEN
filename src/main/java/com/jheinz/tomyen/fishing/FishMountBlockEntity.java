package com.jheinz.tomyen.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The fish on a {@link FishMountBlock}, and its species' typical and record weights, looked up from the species files
 * (server only) when it's mounted and sent to clients with it, so the renderer can size it and a comparator can read
 * it. Every change goes through {@link #setChanged()}, which saves it and sends the block update to clients.
 */
public class FishMountBlockEntity extends BlockEntity {
	private ItemStack fish = ItemStack.EMPTY;
	/** The species' typical and record weights in lb, or 0 when there's no species with a size for this fish. */
	private float averageWeight;
	private float maxWeight;

	public FishMountBlockEntity(BlockPos pos, BlockState state) {
		super(Fishing.FISH_MOUNT_ENTITY, pos, state);
	}

	public ItemStack fish() {
		return fish;
	}

	/** Puts {@code stack} up (or takes the fish down, with an empty stack); server side. */
	public void setFish(ItemStack stack) {
		fish = stack.copy();
		averageWeight = 0.0f;
		maxWeight = 0.0f;
		if (!fish.isEmpty()) {
			FishingSpecies species = FishingSpecies.forItem(fish.getItem());
			if (species != null && species.size() != null) {
				averageWeight = species.size().avg();
				maxWeight = species.size().max();
			}
			TrophyFish trophy = fish.get(Fishing.TROPHY);
			if (averageWeight <= 0.0f && trophy != null) {
				averageWeight = trophy.averageWeight();
			}
		}
		setChanged();
	}

	/** The species' typical weight in lb (0 if unknown): an unweighed fish is mounted at this. */
	public float averageWeight() {
		return averageWeight;
	}

	/** The mounted fish's weight: a trophy's own, else its species' typical weight (0 if unknown). */
	public float weight() {
		TrophyFish trophy = fish.get(Fishing.TROPHY);
		return trophy != null ? trophy.weight() : averageWeight;
	}

	/** Comparator: 0 empty, else 1 to 15 by how close the fish comes to its species' record. */
	public int signal() {
		if (fish.isEmpty()) {
			return 0;
		}
		if (maxWeight <= 0.0f) {
			return 1;
		}
		return Mth.clamp(1 + Math.round(14.0f * weight() / maxWeight), 1, 15);
	}

	/** What the plate says, for the action bar: "Trophy Pike, 31.7 lb, caught by Steve on day 12". */
	public Component describe() {
		TrophyFish trophy = fish.get(Fishing.TROPHY);
		if (trophy != null) {
			return Component.translatable("block.tomyen.fish_mount.trophy", fish.getHoverName(), FishSize.format(trophy.weight()),
					trophy.catcher(), trophy.day()).withStyle(TrophyFish.COLOR);
		}
		MutableComponent name = Component.empty().append(fish.getHoverName());
		return averageWeight > 0.0f ? Component.translatable("block.tomyen.fish_mount.plain", name, FishSize.format(averageWeight))
				: name.withStyle(ChatFormatting.WHITE);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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
		fish = input.read("fish", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		averageWeight = input.getFloatOr("average_lb", 0.0f);
		maxWeight = input.getFloatOr("max_lb", 0.0f);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!fish.isEmpty()) {
			output.store("fish", ItemStack.CODEC, fish);
			output.putFloat("average_lb", averageWeight);
			output.putFloat("max_lb", maxWeight);
		}
	}

	/** Breaking the plaque drops the fish, components and all (the plaque itself comes from the loot table). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !fish.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, fish);
			fish = ItemStack.EMPTY;
		}
	}
}
