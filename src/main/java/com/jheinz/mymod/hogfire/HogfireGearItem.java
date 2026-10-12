package com.jheinz.mymod.hogfire;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A piece of Hogfire gear: the flight harness, the wings or the rocket engine, fitted in that order. Using the
 * harness on an adult pig straps it in, turning it into a {@link Hogfire}; the Hogfire itself takes the rest (see
 * {@link Hogfire#fit}).
 */
public class HogfireGearItem extends Item {
	private final int stage;

	public HogfireGearItem(Properties properties, int stage) {
		super(properties);
		this.stage = stage;
	}

	/** Which piece this is: one of Hogfire's HARNESS, WINGS or ENGINE. */
	public int stage() {
		return stage;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (!(target instanceof Pig pig) || target instanceof Hogfire) {
			return InteractionResult.PASS;
		}
		if (stage != Hogfire.HARNESS || pig.isBaby()) {
			if (!player.level().isClientSide()) {
				player.sendOverlayMessage(Component.translatable(pig.isBaby() ? "entity.mymod.hogfire.too_small" : "entity.mymod.hogfire.harness_first")
						.withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		if (!player.level().isClientSide() && Hogfire.harness(pig) != null) {
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
