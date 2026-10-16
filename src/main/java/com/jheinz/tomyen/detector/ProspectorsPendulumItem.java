package com.jheinz.tomyen.detector;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A weighted string that swings toward ore. Tune it to one ore (vanilla's or the mod's) by right-clicking a piece of
 * it, ore block or what it drops, onto the pendulum in an inventory, or by using the pendulum with it in the other
 * hand; the ore isn't used up. Held, it leans and swings toward the nearest block of that ore within
 * {@value Detectors#PENDULUM_RANGE} blocks (the search and the swing are on the client: PendulumClient).
 */
public class ProspectorsPendulumItem extends Item {
	public ProspectorsPendulumItem(Properties properties) {
		super(properties);
	}

	public static @Nullable OreTarget tuning(ItemStack stack) {
		return OreTarget.byId(stack.get(Detectors.PENDULUM_TUNING));
	}

	private static boolean tune(ItemStack pendulum, ItemStack ore, Player player) {
		OreTarget target = OreTarget.tunedBy(ore);
		if (target == null) {
			return false;
		}
		pendulum.set(Detectors.PENDULUM_TUNING, target.id());
		player.playSound(Detectors.PENDULUM_TUNE, 0.8f, 1.0f);
		player.sendOverlayMessage(Component.translatable("item.tomyen.prospectors_pendulum.tuned", target.name()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		return action == ClickAction.SECONDARY && slot.allowModification(player) && tune(self, other, player);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack pendulum = player.getItemInHand(hand);
		ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (tune(pendulum, other, player)) {
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			OreTarget target = tuning(pendulum);
			player.sendOverlayMessage(target == null
					? Component.translatable("item.tomyen.prospectors_pendulum.untuned").withStyle(ChatFormatting.GRAY)
					: Component.translatable("item.tomyen.prospectors_pendulum.tuned", target.name()).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		OreTarget target = tuning(stack);
		if (target != null) {
			tooltip.accept(Component.translatable("item.tomyen.prospectors_pendulum.tooltip_tuned", target.name()).withStyle(ChatFormatting.GOLD));
		}
		tooltip.accept(Component.translatable("item.tomyen.prospectors_pendulum.hint").withStyle(ChatFormatting.GRAY));
	}
}
