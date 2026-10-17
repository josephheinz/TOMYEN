package com.jheinz.tomyen.fusion.tools;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.jheinz.tomyen.accountant.Accountants;
import com.jheinz.tomyen.bank.Debt;
import com.jheinz.tomyen.bank.DebtCollection;
import com.jheinz.tomyen.fusion.FusionTooltipItem;
import com.jheinz.tomyen.hag.BogHags;
import com.jheinz.tomyen.hag.HagsCurse;

/**
 * The Writ of Absolution (Extension Form + Crucifix, at a pond): paperwork, blessed. Used, it absolves one thing, the
 * curse first:
 * <ul>
 * <li>Under a Hag's Curse: the curse is lifted, and a Hex Ward keeps the next off for {@value #WARD_SECONDS} seconds.</li>
 * <li>Otherwise, owing the bank: the deadline is pushed back {@value #EXTENSION_DAYS} in-game days (to no more than
 * {@value #CAP_DAYS} left, past the Extension Form's full term), and a collector hunt that's on is called off.</li>
 * </ul>
 * With nothing to absolve, it isn't spent.
 */
public class WritOfAbsolutionItem extends Item {
	public static final int WARD_SECONDS = 90;
	public static final int EXTENSION_DAYS = 3;
	public static final int CAP_DAYS = 7;
	private static final int DAY = 24000;

	public WritOfAbsolutionItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (HagsCurse.level(server) > 0) {
			HagsCurse.lift(server);
			server.addEffect(new MobEffectInstance(BogHags.HEX_WARD, WARD_SECONDS * 20));
			server.sendSystemMessage(Component.translatable("message.tomyen.writ_of_absolution.curse", WARD_SECONDS).withStyle(ChatFormatting.AQUA));
		} else if (Debt.of(server) != null) {
			Debt before = Debt.of(server);
			if (!before.hunting() && before.ticksLeft() >= CAP_DAYS * DAY) {
				server.sendOverlayMessage(Component.translatable("message.tomyen.writ_of_absolution.full").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			Debt after = DebtCollection.pushBack(server, EXTENSION_DAYS * DAY, CAP_DAYS * DAY);
			server.sendSystemMessage(Component.translatable(before.hunting() ? "message.tomyen.writ_of_absolution.called_off"
					: "message.tomyen.writ_of_absolution.extended", DebtCollection.formatTime(after.ticksLeft())).withStyle(ChatFormatting.GREEN));
			player.playSound(Accountants.SCRIBBLE, 0.9f, 1.1f);
		} else {
			server.sendOverlayMessage(Component.translatable("message.tomyen.writ_of_absolution.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		stack.consume(1, player);
		double y = player.getY(0.6);
		serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 24, 0.4, 0.6, 0.4, 0.03);
		serverLevel.sendParticles(ParticleTypes.ENCHANT, player.getX(), y, player.getZ(), 30, 0.5, 0.6, 0.5, 0.5);
		serverLevel.playSound(null, player.getX(), y, player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.4f);
		serverLevel.playSound(null, player.getX(), y, player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.9f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.writ_of_absolution.curse", "item.tomyen.writ_of_absolution.debt");
	}
}
