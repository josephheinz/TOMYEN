package com.jheinz.mymod.fishing.fish;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import org.jspecify.annotations.Nullable;

/**
 * Something fished up that opens: right-click and it's used up, its loot table (a gift table, rolled for the player)
 * going into the inventory (or at their feet when it's full). The Tackle Box (parts and bait) and the Sunken Coin
 * Pouch (emeralds and gold) are these.
 */
public class LootBagItem extends Item {
	private final ResourceKey<LootTable> table;
	private final @Nullable String hint;

	public LootBagItem(Properties properties, ResourceKey<LootTable> table, @Nullable String hint) {
		super(properties);
		this.table = table;
		this.hint = hint;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			LootParams params = new LootParams.Builder(serverLevel)
					.withParameter(LootContextParams.ORIGIN, player.position())
					.withParameter(LootContextParams.THIS_ENTITY, player)
					.create(LootContextParamSets.GIFT);
			LootTable loot = serverLevel.getServer().reloadableRegistries().getLootTable(table);
			for (ItemStack drop : loot.getRandomItems(params)) {
				player.getInventory().placeItemBackInInventory(drop, Prediction.SERVER_ONLY);
			}
			stack.consume(1, player);
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f,
				0.9f + level.getRandom().nextFloat() * 0.2f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.mymod.loot_bag.open").withStyle(ChatFormatting.GRAY));
		if (hint != null) {
			tooltip.accept(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
