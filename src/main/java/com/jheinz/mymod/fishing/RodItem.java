package com.jheinz.mymod.fishing;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A mod fishing rod. Casting and waiting work like vanilla's rod (same bobber, same bite timing, Lure and Luck of the
 * Sea apply), but its bobber is a {@link RodHook}: when a fish from the species files bites, a quick-time minigame
 * starts instead of the vanilla "click in time" (see {@link FishingFights}). It's still a {@link FishingRodItem}, so
 * vanilla draws its line, and its cast model and enchantments work as they do for the vanilla rod.
 */
public class RodItem extends FishingRodItem {
	private final int tier;
	private final boolean lava;
	private final RodModifiers modifiers;

	public RodItem(Properties properties, int tier, boolean lava, RodModifiers modifiers) {
		super(properties);
		this.tier = tier;
		this.lava = lava;
		this.modifiers = modifiers;
	}

	public int tier() {
		return tier;
	}

	public boolean fishesLava() {
		return lava;
	}

	public RodModifiers modifiers() {
		return modifiers;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.fishing != null) {
			// Mid-fight the use key reels; the minigame has it, not the rod.
			if (player.fishing instanceof RodHook hook && hook.isFighting()) {
				return InteractionResult.CONSUME;
			}
			if (!level.isClientSide()) {
				int damage = player.fishing.retrieve(stack);
				stack.hurtAndBreak(damage, player, hand.asEquipmentSlot());
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
					1.0f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
			stack.causeUseVibration(player, GameEvent.ITEM_INTERACT_FINISH);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
					0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
			if (level instanceof ServerLevel serverLevel) {
				RodStats stats = RodStats.of(stack);
				int lureSpeed = (int) (EnchantmentHelper.getFishingTimeReduction(serverLevel, stack, player) * 20.0f)
						+ stats.biteTicks(serverLevel.isDarkOutside());
				int luck = EnchantmentHelper.getFishingLuckBonus(serverLevel, stack, player);
				Projectile.spawnProjectile(new RodHook(player, level, luck, lureSpeed, stats), serverLevel, stack);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			stack.causeUseVibration(player, GameEvent.ITEM_INTERACT_START);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.mymod.rod.tier", tier).withStyle(ChatFormatting.GRAY));
		if (lava) {
			tooltip.accept(Component.translatable("tooltip.mymod.rod.lava").withStyle(ChatFormatting.GOLD));
		}
		modifiers.describe().forEach(tooltip);
		RodParts parts = RodParts.of(stack);
		for (PartSlot slot : PartSlot.values()) {
			ItemStack part = parts.stack(slot);
			Component name = part.isEmpty() ? slot.stock().copy().withStyle(ChatFormatting.DARK_GRAY)
					: part.getHoverName().copy().withStyle(ChatFormatting.WHITE);
			tooltip.accept(Component.translatable("tooltip.mymod.rod.slot", slot.label(), name).withStyle(ChatFormatting.GRAY));
			if (part.getItem() instanceof RodPartItem partItem) {
				partItem.modifiers().describe().forEach(line -> tooltip.accept(Component.literal("  ").append(line)));
			}
		}
		if (parts.isEmpty()) {
			tooltip.accept(Component.translatable("tooltip.mymod.rod.customize").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
