package com.jheinz.tomyen.fusion.gear;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.detector.Moon;
import com.jheinz.tomyen.werewolf.WerewolfCurse;
import com.jheinz.tomyen.werewolf.Werewolves;

/**
 * The Wolfhide Carrier's (and the Revenant Mail's) moon blood. From dusk to sunrise (the werewolves' night) the hide
 * lends its wearer {@value #NIGHT_SPEED_PERCENT}% speed and +{@value #NIGHT_DAMAGE} attack damage; under a full moon
 * {@value #MOON_SPEED_PERCENT}% and +{@value #MOON_DAMAGE}, and a little higher jumps. They're attribute modifiers, not
 * potion effects, so nothing flashes in the corner and potions stack on top. When it wakes the wearer hears a distant
 * howl and the hide bristles with grey motes; it fades quietly at sunrise or when the hide comes off.
 */
public final class MoonHide {
	public static final int NIGHT_SPEED_PERCENT = 15;
	public static final int MOON_SPEED_PERCENT = 30;
	public static final double NIGHT_DAMAGE = 2.0;
	public static final double MOON_DAMAGE = 4.0;
	private static final double MOON_JUMP = 0.1;
	private static final int INTERVAL = 10;
	private static final Identifier ID = MyMod.id("moon_hide");
	private static final int FUR = 0x8A8F96;
	private static final int MOONLIGHT = 0xDDE8FF;

	/** 0 dormant, 1 night, 2 full moon, per player, as last applied. */
	private static final Map<UUID, Integer> STAGE = new HashMap<>();

	private MoonHide() {
	}

	static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				update(level, player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STAGE.remove(handler.player.getUUID()));
	}

	/** 0 dormant, 1 night, 2 full moon: what the hide gives this player now. */
	public static int stage(ServerLevel level, ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !FusionGear.moonlit(player) || !Moon.isNight(level)) {
			return 0;
		}
		return WerewolfCurse.isFullMoon(level) ? 2 : 1;
	}

	private static void update(ServerLevel level, ServerPlayer player) {
		int stage = stage(level, player);
		Integer was = STAGE.get(player.getUUID());
		// A respawned or re-joined player is a new entity: apply again whatever the map says.
		boolean applied = player.getAttribute(Attributes.MOVEMENT_SPEED) != null
				&& player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ID) != null;
		if (was != null && was == stage && applied == (stage > 0)) {
			return;
		}
		STAGE.put(player.getUUID(), stage);
		apply(player, stage);
		if (stage > (was == null ? 0 : was)) {
			wake(level, player, stage);
		}
	}

	private static void apply(ServerPlayer player, int stage) {
		set(player.getAttribute(Attributes.MOVEMENT_SPEED), stage == 0 ? 0.0
				: (stage == 2 ? MOON_SPEED_PERCENT : NIGHT_SPEED_PERCENT) / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player.getAttribute(Attributes.ATTACK_DAMAGE), stage == 0 ? 0.0 : stage == 2 ? MOON_DAMAGE : NIGHT_DAMAGE,
				AttributeModifier.Operation.ADD_VALUE);
		set(player.getAttribute(Attributes.JUMP_STRENGTH), stage == 2 ? MOON_JUMP : 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void set(AttributeInstance attribute, double amount, AttributeModifier.Operation operation) {
		if (attribute == null) {
			return;
		}
		if (amount == 0.0) {
			attribute.removeModifier(ID);
		} else {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(ID, amount, operation));
		}
	}

	private static void wake(ServerLevel level, ServerPlayer player, int stage) {
		double x = player.getX(), y = player.getY(0.6), z = player.getZ();
		level.sendParticles(new DustParticleOptions(FUR, 1.0f), x, y, z, 16, 0.35, 0.45, 0.35, 0.0);
		if (stage == 2) {
			level.sendParticles(new DustParticleOptions(MOONLIGHT, 1.2f), x, y + 0.4, z, 12, 0.3, 0.5, 0.3, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, x, y + 0.6, z, 4, 0.2, 0.3, 0.2, 0.01);
		}
		// Far off and only for the wearer: the pack answering.
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(Werewolves.HOWL), SoundSource.PLAYERS,
				x, player.getEyeY(), z, stage == 2 ? 0.6f : 0.35f, stage == 2 ? 0.9f : 1.05f, level.getRandom().nextLong()));
		player.sendOverlayMessage(Component.translatable(stage == 2 ? "item.tomyen.wolfhide_carrier.wakes_moon"
				: "item.tomyen.wolfhide_carrier.wakes").withStyle(stage == 2 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
	}
}
