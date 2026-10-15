package com.jheinz.mymod.detector;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;

import com.jheinz.mymod.werewolf.WerewolfCurse;

/**
 * A farmer's almanac with a silver clasp. Held, its HUD panel shows tonight's moon and the nights left to the full
 * moon, when cursed villagers turn (see {@link Moon}); and near a cursed villager, a vague unease (the server's
 * {@link DetectorFix#omen()}). Opened (used), it reads the same out into chat, with the unease if there is one.
 */
public class LunarAlmanacItem extends Item {
	private static final int COOLDOWN = 20;

	public LunarAlmanacItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.playSound(Detectors.ALMANAC_OPEN, 0.8f, 1.0f);
		if (level instanceof ServerLevel server) {
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN);
			if (!Moon.visible(level)) {
				player.sendSystemMessage(Component.translatable("detector.mymod.moon.none").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			MoonPhase phase = Moon.phase(level, player.position());
			player.sendSystemMessage(Component.translatable("item.mymod.lunar_almanac.reading", Moon.phaseName(phase),
					Moon.countdown(level, phase)).withStyle(Moon.nightsToFull(phase) == 0 ? ChatFormatting.RED : ChatFormatting.AQUA));
			boolean omen = !server.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Detectors.OMEN_RANGE),
					WerewolfCurse::isCursed).isEmpty();
			if (omen) {
				player.sendSystemMessage(Component.translatable("detector.mymod.almanac.omen")
						.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), Detectors.ALMANAC_OMEN, SoundSource.PLAYERS, 0.6f, 0.8f);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.lunar_almanac.hint").withStyle(ChatFormatting.GRAY));
	}
}
