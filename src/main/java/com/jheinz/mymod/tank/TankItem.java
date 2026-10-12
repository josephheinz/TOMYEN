package com.jheinz.mymod.tank;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/** A tank, packed up. Use it on the ground to roll it out, facing the way you're looking. */
public class TankItem extends Item {
	public TankItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Tank tank = Tanks.TANK.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (tank == null) {
			return InteractionResult.FAIL;
		}
		Player player = context.getPlayer();
		tank.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player == null ? 0.0f : player.getYRot(), 0.0f);
		tank.setYHeadRot(tank.getYRot());
		tank.setYBodyRot(tank.getYRot());
		if (!level.noCollision(tank, tank.getBoundingBox())) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("item.mymod.tank.no_room").withStyle(ChatFormatting.RED));
			}
			return InteractionResult.FAIL;
		}
		level.addFreshEntity(tank);
		level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.tank.hint_place").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.tank.hint_drive").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.mymod.tank.hint_fire").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.mymod.tank.hint_repair").withStyle(ChatFormatting.DARK_GRAY));
	}
}
