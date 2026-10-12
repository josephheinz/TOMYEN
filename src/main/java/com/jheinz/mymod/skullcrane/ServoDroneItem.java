package com.jheinz.mymod.skullcrane;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A {@link ServoDrone} packed up: use it to send the drone out (one per player at a time). Its durability is the
 * drone's health, kept while it's packed up, and it's mended with iron.
 */
public class ServoDroneItem extends Item {
	/** How far off counts as already having a drone out. */
	private static final double ONE_PER_PLAYER = 256.0;

	public ServoDroneItem(Properties properties) {
		super(properties);
	}

	public static Item.Properties properties() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).durability((int) ServoDrone.MAX_HEALTH).repairable(Items.IRON_INGOT);
	}

	/** A packed-up drone with this much health. */
	static ItemStack withHealth(float health) {
		ItemStack stack = new ItemStack(Skullcranes.SERVO_DRONE);
		stack.setDamageValue(Mth.clamp(Math.round(ServoDrone.MAX_HEALTH - health), 0, (int) ServoDrone.MAX_HEALTH - 1));
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!serverLevel.getEntitiesOfClass(ServoDrone.class, new AABB(player.blockPosition()).inflate(ONE_PER_PLAYER),
				drone -> drone.isOwnedBy(player)).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("item.mymod.servo_drone.already_out").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		ServoDrone drone = Skullcranes.SERVO_DRONE_ENTITY.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
		if (drone == null) {
			return InteractionResult.FAIL;
		}
		// Just in front of them, short of anything in the way.
		Vec3 eye = player.getEyePosition();
		Vec3 wanted = eye.add(player.getViewVector(1.0f).scale(1.5));
		Vec3 at = serverLevel.clip(new ClipContext(eye, wanted, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
		at = at.subtract(player.getViewVector(1.0f).scale(0.3)).subtract(0.0, 0.2, 0.0);
		drone.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0f);
		drone.setOwner(player);
		drone.setHealth(ServoDrone.MAX_HEALTH - stack.getDamageValue());
		if (stack.has(DataComponents.CUSTOM_NAME)) {
			drone.setCustomName(stack.getHoverName());
		}
		serverLevel.addFreshEntity(drone);
		serverLevel.playSound(null, at.x, at.y, at.z, Skullcranes.LIFT, SoundSource.NEUTRAL, 0.7f, 1.6f);
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.servo_drone.deploy").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.servo_drone.duties").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mymod.servo_drone.recall").withStyle(ChatFormatting.GRAY));
	}
}
