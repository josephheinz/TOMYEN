package com.jheinz.tomyen.client.fishing;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.fishing.RodHook;

/**
 * Item model condition {@code tomyen:rod_fighting}: this rod is the one in the holder's casting hand and its bobber has
 * a fish fighting on it. The rods' item models swap to a bent rod while it's true.
 */
public record RodFighting() implements ConditionalItemModelProperty {
	public static final MapCodec<RodFighting> MAP_CODEC = MapCodec.unit(new RodFighting());

	@Override
	public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext context) {
		return owner instanceof Player player && player.fishing instanceof RodHook hook && hook.isFighting()
				&& player.getItemHeldByArm(FishingHookRenderer.getHoldingArm(player)) == stack;
	}

	@Override
	public MapCodec<RodFighting> type() {
		return MAP_CODEC;
	}
}
