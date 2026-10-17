package com.jheinz.tomyen.isle;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;

/**
 * Whose grave this is. The name is sent to clients for the carving on its face (GravestoneRenderer).
 *
 * <p>A gravestone only leads anywhere while it's the one its owner's hoard points at: every couple of seconds it
 * checks, and crumbles away once the hoard is won back, or a newer death has put up a newer stone.
 */
public class GravestoneBlockEntity extends BlockEntity {
	private static final int CHECK_INTERVAL = 40;

	private @Nullable UUID owner;
	private String ownerName = "";

	public GravestoneBlockEntity(BlockPos pos, BlockState state) {
		super(Isle.GRAVESTONE_ENTITY, pos, state);
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public void setOwner(UUID owner, String name) {
		this.owner = owner;
		this.ownerName = name;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Whether this stone is still the way to its owner's hoard. */
	public boolean leadsToHoard(ServerLevel level) {
		if (owner == null) {
			return false;
		}
		Hoard hoard = Hoards.get(level.getServer(), owner);
		return hoard != null && hoard.gravestone().equals(Optional.of(GlobalPos.of(level.dimension(), worldPosition)));
	}

	static void serverTick(Level level, BlockPos pos, BlockState state, GravestoneBlockEntity grave) {
		if ((level.getGameTime() + pos.asLong()) % CHECK_INTERVAL != 0 || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!grave.leadsToHoard(serverLevel)) {
			GravestoneBlock.crumble(serverLevel, pos, state);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("owner_name", "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
		output.putString("owner_name", ownerName);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
