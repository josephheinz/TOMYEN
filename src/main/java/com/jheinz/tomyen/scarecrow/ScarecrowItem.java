package com.jheinz.tomyen.scarecrow;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PostSpawnProcessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Places a {@link Scarecrow} of its variant on the clicked block, facing whoever placed it (as an armor stand does). */
public class ScarecrowItem extends Item {
	private final Scarecrow.Variant variant;

	public ScarecrowItem(Item.Properties properties, Scarecrow.Variant variant) {
		super(properties);
		this.variant = variant;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getClickedFace() == Direction.DOWN) {
			return InteractionResult.FAIL;
		}
		Level level = context.getLevel();
		BlockPos pos = new BlockPlaceContext(context).getClickedPos();
		ItemStack stack = context.getItemInHand();
		Vec3 feet = Vec3.atBottomCenterOf(pos);
		AABB box = Scarecrows.SCARECROW.getDimensions().makeBoundingBox(feet.x(), feet.y(), feet.z());
		if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			PostSpawnProcessor<Scarecrow> config = EntityType.createDefaultStackConfig(serverLevel, stack, context.getPlayer());
			Scarecrow scarecrow = Scarecrows.SCARECROW.create(serverLevel, config, pos, EntitySpawnReason.SPAWN_ITEM_USE, true, true);
			if (scarecrow == null) {
				return InteractionResult.FAIL;
			}
			scarecrow.setVariant(variant);
			float yRot = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0f) + 22.5f) / 45.0f) * 45.0f;
			scarecrow.snapTo(scarecrow.getX(), scarecrow.getY(), scarecrow.getZ(), yRot, 0.0f);
			serverLevel.addFreshEntityWithPassengers(scarecrow);
			level.playSound(null, scarecrow.getX(), scarecrow.getY(), scarecrow.getZ(), SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
			scarecrow.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
		}
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.scarecrow.tooltip.ufo").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable(variant == Scarecrow.Variant.SCRAP ? "item.tomyen.scrap_scarecrow.tooltip.mobs"
				: "item.tomyen.scarecrow.tooltip.mobs").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.scarecrow.tooltip.crops").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.scarecrow.tooltip.repair").withStyle(ChatFormatting.DARK_GRAY));
	}
}
