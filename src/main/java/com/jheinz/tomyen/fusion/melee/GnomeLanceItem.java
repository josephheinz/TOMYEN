package com.jheinz.tomyen.fusion.melee;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.gnome.Gnomes;

/**
 * The Gnome Lance (garden gnome + tusk spear, at a pond): a striped jousting lance, vamplate and all, with a little
 * garden gnome riding the tip as its figurehead. A vanilla spear that unhorses riders, and the faster you move the
 * harder it hits: every jab and charge adds {@value #PER_BLOCK} damage for each block a second you're moving past
 * {@value #FREE_SPEED} (on foot, on a horse or a moose, in the Bathtub Boat: whatever carries you), up to
 * +{@value #MAX_BONUS}. A big joust gets a giggle from the gnome.
 *
 * <p>Speed is measured here, from how far the player (or what they ride) moved each tick, since the rider of a mount
 * isn't the one the server sees moving. The bonus goes onto the stab in PlayerStabLanceMixin.
 */
public class GnomeLanceItem extends Item {
	public static final double FREE_SPEED = 3.0;
	public static final double PER_BLOCK = 1.25;
	public static final float MAX_BONUS = 20.0f;
	/** A joust this big makes the gnome giggle. */
	private static final float GIGGLE_BONUS = 7.0f;
	private static final Map<UUID, Tracker> SPEEDS = new HashMap<>();

	public GnomeLanceItem(Properties properties) {
		super(properties);
	}

	private static final class Tracker {
		ServerLevel level;
		Vec3 last;
		/** Blocks a second, smoothed over a few ticks. */
		double speed;
		long seen;
	}

	/** The extra damage the lance does for the player's speed now. */
	public static float speedBonus(Player player) {
		Tracker tracker = SPEEDS.get(player.getUUID());
		if (tracker == null) {
			return 0.0f;
		}
		return (float) Mth.clamp((tracker.speed - FREE_SPEED) * PER_BLOCK, 0.0, MAX_BONUS);
	}

	/** From the stab: the damage with the speed bonus, and the show of it. */
	public static float joust(Player player, float damage) {
		float bonus = speedBonus(player);
		if (bonus < 0.5f || !(player instanceof ServerPlayer serverPlayer)) {
			return damage;
		}
		ServerLevel level = serverPlayer.level();
		serverPlayer.sendOverlayMessage(Component.translatable("item.tomyen.gnome_lance.joust", String.format("%.1f", bonus))
				.withStyle(bonus >= GIGGLE_BONUS ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
		Vec3 tip = player.getEyePosition().add(player.getLookAngle().scale(2.5));
		level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 6 + (int) bonus, 0.3, 0.3, 0.3, 0.4);
		if (bonus >= GIGGLE_BONUS) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), Gnomes.GIGGLE, SoundSource.PLAYERS, 1.0f, 1.1f);
		}
		return damage + bonus;
	}

	/** Each tick, how fast every lance-holder (or whatever carries them) is going. */
	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (!(player.getMainHandItem().getItem() instanceof GnomeLanceItem)) {
				continue;
			}
			Entity carrier = player.getRootVehicle();
			Vec3 pos = carrier.position();
			Tracker tracker = SPEEDS.computeIfAbsent(player.getUUID(), id -> new Tracker());
			if (tracker.level != level || tracker.last == null || now - tracker.seen > 2) {
				tracker.level = level;
				tracker.speed = 0.0;
			} else {
				double moved = pos.subtract(tracker.last).horizontalDistance() * 20.0;
				// A teleport isn't speed.
				if (moved < 80.0) {
					tracker.speed = tracker.speed * 0.5 + moved * 0.5;
				}
			}
			tracker.last = pos;
			tracker.seen = now;
		}
		Iterator<Tracker> it = SPEEDS.values().iterator();
		while (it.hasNext()) {
			Tracker tracker = it.next();
			if (tracker.level == level && now - tracker.seen > 200) {
				it.remove();
			}
		}
	}

	static void clear() {
		SPEEDS.clear();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.gnome_lance.speed", "item.tomyen.gnome_lance.mounts", "item.tomyen.gnome_lance.unhorse");
	}
}
