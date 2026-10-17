package com.jheinz.tomyen.gravedigger;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.tomyen.werewolf.WerewolfCurse;

/**
 * The Gravedigger's Shovel, off a dead grave digger: a sturdy shovel that works best on the night shift, digging
 * {@link #NIGHT_SPEED} times as fast after dark, and now and then turns up an old bone from the ground it digs.
 * <p>
 * Whether it's night is the server's to say, so the shovel marks itself ({@link GraveDiggers#NIGHT_SHIFT}) while it's
 * carried, and the mark reaches the client for its mining speed and tooltip.
 */
public class GravediggerShovelItem extends Item {
	/** How much faster it digs by night. */
	public static final float NIGHT_SPEED = 1.6f;
	/** The chance a block of soft ground it digs turns up a bone; twice this by night. */
	public static final float BONE_CHANCE = 0.04f;

	public GravediggerShovelItem(Properties properties) {
		super(properties);
	}

	public static boolean nightShift(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(GraveDiggers.NIGHT_SHIFT));
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner.tickCount % 20 != 0) {
			return;
		}
		boolean night = WerewolfCurse.isNight(level);
		if (night != nightShift(stack)) {
			if (night) {
				stack.set(GraveDiggers.NIGHT_SHIFT, true);
			} else {
				stack.remove(GraveDiggers.NIGHT_SHIFT);
			}
		}
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		float speed = super.getDestroySpeed(stack, state);
		return speed > 1.0f && nightShift(stack) ? speed * NIGHT_SPEED : speed;
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (level instanceof ServerLevel serverLevel && state.is(BlockTags.MINEABLE_WITH_SHOVEL)
				&& serverLevel.getRandom().nextFloat() < BONE_CHANCE * (nightShift(stack) ? 2.0f : 1.0f)) {
			Block.popResource(serverLevel, pos, new ItemStack(Items.BONE));
		}
		return super.mineBlock(stack, level, state, pos, owner);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable(nightShift(stack) ? "item.tomyen.gravedigger_shovel.night_now"
				: "item.tomyen.gravedigger_shovel.night").withStyle(nightShift(stack) ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.gravedigger_shovel.bones").withStyle(ChatFormatting.GRAY));
	}
}
