package com.jheinz.tomyen.fusion.melee;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;

/**
 * The Moonblade (moonscale koi + fae silver sword, at a pond): a fae silver blade with the koi's moon-white scales
 * along its fuller, the koi's fan tail for a guard and a moonstone in the pommel. 7 damage at fae silver's quick 2.0
 * swings a second, and the moon in it:
 * <ul>
 * <li>+{@value #NIGHT_BONUS} damage at night, +{@value #FULL_MOON_BONUS} under a full moon.
 * <li>At night every charged swing, hit or miss, looses a crescent of moonlight that sweeps out
 * {@value #WAVE_REACH} blocks ({@value #FULL_MOON_REACH} under a full moon), hitting what it passes for
 * {@value #WAVE_SHARE} of the blade's damage.
 * </ul>
 */
public class MoonbladeItem extends Item implements MeleeSwings.SwingAbility {
	public static final float NIGHT_BONUS = 2.0f;
	public static final float FULL_MOON_BONUS = 4.0f;
	public static final double WAVE_REACH = 5.0;
	public static final double FULL_MOON_REACH = 7.0;
	public static final float WAVE_SHARE = 0.6f;

	public MoonbladeItem(Properties properties) {
		super(properties);
	}

	public static boolean night(Level level) {
		return level.isDarkOutside();
	}

	public static boolean fullMoon(Level level, Vec3 at) {
		return night(level) && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, at) == MoonPhase.FULL_MOON;
	}

	/** What the moon adds to a hit here and now. */
	public static float moonBonus(Level level, Vec3 at) {
		return !night(level) ? 0.0f : fullMoon(level, at) ? FULL_MOON_BONUS : NIGHT_BONUS;
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
		return moonBonus(victim.level(), victim.position());
	}

	@Override
	public void swung(ServerLevel level, ServerPlayer player, ItemStack stack) {
		if (!night(level)) {
			return;
		}
		boolean full = fullMoon(level, player.position());
		float damage = WAVE_SHARE * ((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) + moonBonus(level, player.position()));
		CrescentWaves.crescent(level, player, CrescentWaves.Kind.MOON, full ? FULL_MOON_REACH : WAVE_REACH, damage);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9f,
				full ? 1.5f : 1.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.moonblade.night", "item.tomyen.moonblade.wave");
	}
}
