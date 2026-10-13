package com.jheinz.mymod.hogfire;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Hogfire Gun Mount: a Minigun's works on a saddle plate. Using it on a Hogfire fits it (see {@link Hogfire}); a
 * plain pig needs its flight harness first.
 */
public class HogfireGunMountItem extends Item {
	public HogfireGunMountItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof Pig) || target instanceof Hogfire) {
			return InteractionResult.PASS;
		}
		if (!player.level().isClientSide()) {
			player.sendOverlayMessage(Component.translatable("entity.mymod.hogfire.harness_first").withStyle(ChatFormatting.RED));
		}
		return InteractionResult.FAIL;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.hogfire_gun_mount.hint").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.hogfire_gun_mount.fire_hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
