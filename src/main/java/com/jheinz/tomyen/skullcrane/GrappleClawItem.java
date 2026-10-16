package com.jheinz.tomyen.skullcrane;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A servo arm's claw on a cable: use it to fire the claw (see {@link GrappleHook}), use it again (or sneak) to let
 * go. Each shot wears it a point; it's mended with iron.
 */
public class GrappleClawItem extends Item {
	public static final int DURABILITY = 192;
	private static final int COOLDOWN = 8;

	public GrappleClawItem(Properties properties) {
		super(properties);
	}

	public static Item.Properties properties() {
		return new Item.Properties().stacksTo(1).durability(DURABILITY).repairable(Items.IRON_INGOT);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			GrappleHook out = hookOut(serverLevel, player);
			if (out != null) {
				out.discard();
			} else {
				GrappleHook hook = new GrappleHook(serverLevel, player);
				hook.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, (float) GrappleHook.SPEED, 0.0f);
				serverLevel.addFreshEntity(hook);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), Skullcranes.RELEASE, SoundSource.PLAYERS, 0.8f, 1.6f);
				stack.hurtAndBreak(1, player, hand);
			}
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	/** The claw a player has out, if any. */
	private static @Nullable GrappleHook hookOut(ServerLevel level, Player player) {
		for (GrappleHook hook : level.getEntitiesOfClass(GrappleHook.class, new AABB(player.blockPosition()).inflate(GrappleHook.RANGE + 8.0),
				hook -> hook.getOwner() == player)) {
			return hook;
		}
		return null;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.grapple_claw.fire").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.grapple_claw.release").withStyle(ChatFormatting.GRAY));
	}
}
