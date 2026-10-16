package com.jheinz.tomyen.client.soul;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.soul.SoulVacuumItem;

/** Item model property tomyen:soul_fill: how full a Soul Vacuum's canister is, 0 to 1. Picks how much the nozzle glows. */
public record SoulFillProperty() implements RangeSelectItemModelProperty {
	public static final MapCodec<SoulFillProperty> MAP_CODEC = MapCodec.unit(new SoulFillProperty());

	@Override
	public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
		return SoulVacuumItem.canister(stack).fill();
	}

	@Override
	public MapCodec<SoulFillProperty> type() {
		return MAP_CODEC;
	}
}
