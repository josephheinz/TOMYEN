package com.jheinz.mymod.survival;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.uranium.Radiation;

/**
 * Field medicine: hold use to patch yourself up (slowed, as when eating, with the rustle of cloth), then heal over a
 * few seconds. Both stop Bleeding (a vampire's bite). The Bandage is a single quick wrap; the First Aid Kit takes longer,
 * heals more at once, also clears poison and nausea, and draws {@link Kind#radiationRelief} ticks off a Radiation
 * Sickness dose; it holds {@link Kind#uses} patch-ups.
 */
public class MedicalItem extends Item {
	/**
	 * What one kind of medicine does.
	 *
	 * @param useTicks how long the patch-up takes
	 * @param uses how many patch-ups one item holds (1: it's used up)
	 * @param heal health restored at once
	 * @param regenAmplifier the level of the Regeneration that follows
	 * @param regenTicks how long that Regeneration lasts
	 * @param curesPoison whether it also clears poison and nausea
	 * @param radiationRelief ticks of Radiation Sickness dose it draws off
	 */
	public record Kind(int useTicks, int uses, float heal, int regenAmplifier, int regenTicks, boolean curesPoison, int radiationRelief) {
	}

	public static final Kind BANDAGE = new Kind(30, 1, 0.0f, 1, 80, false, 0);
	public static final Kind FIRST_AID_KIT = new Kind(50, 4, 6.0f, 0, 160, true, Radiation.LEVEL_II);

	private final Kind kind;

	public MedicalItem(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BRUSH;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return kind.useTicks();
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!level.isClientSide() && ticksRemaining % 10 == 0) {
			level.playSound(null, user.getX(), user.getY(), user.getZ(), Survival.BANDAGE_WRAP, SoundSource.PLAYERS, 0.5f,
					0.9f + level.getRandom().nextFloat() * 0.25f);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (level instanceof ServerLevel server) {
			treat(server, user);
			if (kind.uses() > 1) {
				stack.hurtAndBreak(1, user, user.getUsedItemHand().asEquipmentSlot());
			} else {
				stack.consume(1, user);
			}
		}
		return stack;
	}

	private void treat(ServerLevel level, LivingEntity user) {
		user.removeEffect(ModEffects.BLEEDING);
		if (kind.curesPoison()) {
			user.removeEffect(MobEffects.POISON);
			user.removeEffect(MobEffects.NAUSEA);
		}
		if (kind.heal() > 0.0f) {
			user.heal(kind.heal());
		}
		user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, kind.regenTicks(), kind.regenAmplifier()));
		if (kind.radiationRelief() > 0) {
			MobEffectInstance sickness = user.getEffect(ModEffects.RADIATION_SICKNESS);
			if (sickness != null) {
				int dose = sickness.getDuration() - kind.radiationRelief();
				user.removeEffect(ModEffects.RADIATION_SICKNESS);
				if (dose > 0) {
					user.addEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, dose, Radiation.level(dose)));
				}
			}
		}
		level.playSound(null, user.getX(), user.getY(), user.getZ(), Survival.BANDAGE_WRAP, SoundSource.PLAYERS, 0.8f, 1.3f);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		String key = getDescriptionId() + ".desc";
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
	}
}
