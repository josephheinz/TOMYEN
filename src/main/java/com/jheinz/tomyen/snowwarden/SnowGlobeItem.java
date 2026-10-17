package com.jheinz.tomyen.snowwarden;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Snow Globe: a collectible for now (it has a part to play later). Shaken, it tinkles, and a little flurry of snow
 * swirls round whoever's holding it.
 */
public class SnowGlobeItem extends Item {
	private static final int SHAKE_COOLDOWN = 20;

	public SnowGlobeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			double y = player.getY(0.6);
			server.playSound(null, player.getX(), y, player.getZ(), SnowWardens.SNOW_GLOBE_SHAKE, SoundSource.PLAYERS, 1.0f,
					0.9f + player.getRandom().nextFloat() * 0.2f);
			server.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), y + 0.4, player.getZ(), 24, 0.6, 0.7, 0.6, 0.02);
			server.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), y + 0.6, player.getZ(), 12, 0.5, 0.5, 0.5, 0.0);
		}
		player.getCooldowns().addCooldown(stack, SHAKE_COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.tomyen.snow_globe.hint").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
	}
}
