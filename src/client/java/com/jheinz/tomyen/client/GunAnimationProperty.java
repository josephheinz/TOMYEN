package com.jheinz.tomyen.client;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/** Item model property tomyen:gun_animation: which frame of its racking or bolt animation a gun is on. */
public record GunAnimationProperty() implements RangeSelectItemModelProperty {
	public static final MapCodec<GunAnimationProperty> MAP_CODEC = MapCodec.unit(new GunAnimationProperty());

	@Override
	public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
		return GunAnimation.frame(stack, owner, Minecraft.getInstance().player);
	}

	@Override
	public MapCodec<GunAnimationProperty> type() {
		return MAP_CODEC;
	}
}
