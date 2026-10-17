package com.jheinz.tomyen.fishing.catfish;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.tomyen.fishing.FishMountBlockEntity;

/** The fish on a Giant Fish Mount, held by its master block: a Fish Mount's, under its own type. */
public class GiantFishMountBlockEntity extends FishMountBlockEntity {
	public GiantFishMountBlockEntity(BlockPos pos, BlockState state) {
		super(GiantCatfishes.GIANT_FISH_MOUNT_ENTITY, pos, state);
	}
}
