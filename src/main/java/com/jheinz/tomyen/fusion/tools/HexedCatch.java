package com.jheinz.tomyen.fusion.tools;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;

import com.jheinz.tomyen.hag.HagsCurse;

/**
 * A consume effect ({@code tomyen:hexed_catch}) on a fish the {@link HexedRodItem} landed: eaten, it rolls one of
 * these, about three times in five a blessing and otherwise a hex.
 * <ul>
 * <li>Blessings: Strength II and Regeneration; Resistance II and Absorption III; Speed II and Haste II; the deep's
 * gifts (Water Breathing, Dolphin's Grace, Night Vision, five minutes); Fire Resistance and Regeneration II; or Luck II
 * and Health Boost II.</li>
 * <li>Hexes: a Hag's Curse; Poison II and Nausea; Levitation; Blindness and Slowness II; Hunger III and Weakness II;
 * or the Wither.</li>
 * </ul>
 */
public record HexedCatch() implements ConsumeEffect {
	public static final HexedCatch INSTANCE = new HexedCatch();
	public static final MapCodec<HexedCatch> CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, HexedCatch> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	/** Out of ten rolls, how many bless. */
	private static final int BLESSINGS_IN_TEN = 6;

	private record Outcome(String key, List<Effect> effects) {
	}

	private record Effect(Holder<MobEffect> effect, int seconds, int amplifier) {
	}

	private static final List<Outcome> BLESSINGS = List.of(
			new Outcome("might", List.of(new Effect(MobEffects.STRENGTH, 90, 1), new Effect(MobEffects.REGENERATION, 30, 0))),
			new Outcome("iron_scales", List.of(new Effect(MobEffects.RESISTANCE, 120, 1), new Effect(MobEffects.ABSORPTION, 120, 2))),
			new Outcome("quicksilver", List.of(new Effect(MobEffects.SPEED, 180, 1), new Effect(MobEffects.HASTE, 180, 1))),
			new Outcome("deep", List.of(new Effect(MobEffects.WATER_BREATHING, 300, 0), new Effect(MobEffects.DOLPHINS_GRACE, 300, 0),
					new Effect(MobEffects.NIGHT_VISION, 300, 0))),
			new Outcome("ember", List.of(new Effect(MobEffects.FIRE_RESISTANCE, 300, 0), new Effect(MobEffects.REGENERATION, 20, 1))),
			new Outcome("fortune", List.of(new Effect(MobEffects.LUCK, 600, 1), new Effect(MobEffects.HEALTH_BOOST, 300, 1))));
	private static final List<Outcome> HEXES = List.of(
			new Outcome("hag", List.of()),
			new Outcome("rot", List.of(new Effect(MobEffects.POISON, 15, 1), new Effect(MobEffects.NAUSEA, 12, 0))),
			new Outcome("float", List.of(new Effect(MobEffects.LEVITATION, 6, 1))),
			new Outcome("murk", List.of(new Effect(MobEffects.BLINDNESS, 20, 0), new Effect(MobEffects.SLOWNESS, 40, 1))),
			new Outcome("hollow", List.of(new Effect(MobEffects.HUNGER, 30, 2), new Effect(MobEffects.WEAKNESS, 60, 1))),
			new Outcome("wither", List.of(new Effect(MobEffects.WITHER, 10, 0))));

	@Override
	public ConsumeEffect.Type<HexedCatch> getType() {
		return FusionTools.HEXED_CATCH;
	}

	@Override
	public boolean apply(Level level, ItemStack stack, LivingEntity eater) {
		if (!(level instanceof ServerLevel server)) {
			return false;
		}
		boolean blessed = eater.getRandom().nextInt(10) < BLESSINGS_IN_TEN;
		List<Outcome> table = blessed ? BLESSINGS : HEXES;
		Outcome outcome = table.get(eater.getRandom().nextInt(table.size()));
		if (outcome.key().equals("hag")) {
			HagsCurse.curse(server, eater, null);
		}
		for (Effect effect : outcome.effects()) {
			eater.addEffect(new MobEffectInstance(effect.effect(), effect.seconds() * 20, effect.amplifier()));
		}
		double y = eater.getY(0.7);
		server.sendParticles(blessed ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WITCH, eater.getX(), y, eater.getZ(), 14, 0.35, 0.4, 0.35,
				0.02);
		server.playSound(null, eater.getX(), y, eater.getZ(), blessed ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.WITCH_CELEBRATE,
				SoundSource.PLAYERS, 1.0f, blessed ? 1.4f : 0.8f);
		if (eater instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("hud.tomyen.hexed_catch." + outcome.key())
					.withStyle(blessed ? ChatFormatting.AQUA : ChatFormatting.DARK_GREEN));
		}
		return true;
	}
}
