package com.jheinz.tomyen.fishing;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
	private final boolean reel;
	private final RodCondition condition;
	private final RodModifiers bonus;

	public RodItem(Properties properties, int tier, boolean lava, RodModifiers modifiers) {
		this(properties, tier, lava, modifiers, true, RodCondition.NONE, RodModifiers.NONE);
	}

	/**
	 * {@code reel}: whether it has a reel to fit a reel part to (a plain pole doesn't); {@code bonus}: modifiers added
	 * on top of its own when it's cast where {@code condition} holds.
	 */
	public RodItem(Properties properties, int tier, boolean lava, RodModifiers modifiers, boolean reel, RodCondition condition,
			RodModifiers bonus) {
		super(properties);
		this.tier = tier;
		this.lava = lava;
		this.modifiers = modifiers;
		this.reel = reel;
		this.condition = condition;
		this.bonus = bonus;
	}

	public boolean hasReel() {
		return reel;
	}

	public RodCondition condition() {
		return condition;
	}

	public RodModifiers bonus() {
		return bonus;
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

	/** Difficulty steps every fight on this rod goes up by (a hexed rod's fish fight harder). */
	public int fightPenalty() {
		return 0;
	}

	/** Server side: a fish just landed on this rod, about to fly to the angler. What actually comes up (a hexed rod curses it). */
	public ItemStack landed(ServerLevel level, ServerPlayer angler, ItemStack caught) {
		return caught;
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
				RodStats stats = RodStats.of(stack, serverLevel, player.blockPosition());
				boolean wet = serverLevel.isRaining() || RodCondition.murky(serverLevel, player.blockPosition());
				int lureSpeed = (int) (EnchantmentHelper.getFishingTimeReduction(serverLevel, stack, player) * 20.0f)
						+ stats.biteTicks(serverLevel.isDarkOutside(), wet);
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
		tooltip.accept(Component.translatable("tooltip.tomyen.rod.tier", tier).withStyle(ChatFormatting.GRAY));
		if (lava) {
			tooltip.accept(Component.translatable("tooltip.tomyen.rod.lava").withStyle(ChatFormatting.GOLD));
		}
		modifiers.describe().forEach(tooltip);
		if (condition != RodCondition.NONE) {
			tooltip.accept(condition.label().copy().withStyle(ChatFormatting.GRAY));
			bonus.describe().forEach(line -> tooltip.accept(Component.literal(" ").append(line)));
		}
		RodParts parts = RodParts.of(stack);
		for (PartSlot slot : PartSlot.values()) {
			if (slot == PartSlot.REEL && !reel) {
				tooltip.accept(Component.translatable("tooltip.tomyen.rod.slot", slot.label(),
						Component.translatable("tooltip.tomyen.rod.no_reel").withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
				continue;
			}
			ItemStack part = parts.stack(slot);
			Component name = part.isEmpty() ? slot.stock().copy().withStyle(ChatFormatting.DARK_GRAY)
					: part.getHoverName().copy().withStyle(ChatFormatting.WHITE);
			tooltip.accept(Component.translatable("tooltip.tomyen.rod.slot", slot.label(), name).withStyle(ChatFormatting.GRAY));
			if (part.getItem() instanceof RodPartItem partItem) {
				partItem.modifiers().describe().forEach(line -> tooltip.accept(Component.literal("  ").append(line)));
			}
		}
		ItemStack bait = RodBait.stack(stack);
		Component baitName = bait.isEmpty() ? Component.translatable("tooltip.tomyen.rod_slot.bait.stock").withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("tooltip.tomyen.rod.bait_count", bait.getHoverName(), bait.getCount()).withStyle(ChatFormatting.WHITE);
		tooltip.accept(Component.translatable("tooltip.tomyen.rod.slot", Component.translatable("tooltip.tomyen.rod_slot.bait"), baitName)
				.withStyle(ChatFormatting.GRAY));
		if (parts.isEmpty() && bait.isEmpty()) {
			tooltip.accept(Component.translatable("tooltip.tomyen.rod.customize").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
