package com.jheinz.tomyen.isle;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hooks into the Isle of the Damned for what comes after pass 1 (the Isle's shades, the Soul Tide). Everything here
 * fires on the server.
 *
 * <ul>
 * <li>{@link #ARRIVED}: a player has just been set down on the Isle (by paying respects, an offering, or a command).
 * <li>{@link #LEFT}: a player has just been sent home from it (by the Lantern of Return or a Return Rift).
 * <li>{@link #REVENANT_RISEN}: a Revenant has just formed in its arena for its owner; a good moment to call up shades
 * round it, scaled to {@link Revenant#depth()}.
 * <li>{@link #REVENANT_DEFEATED}: a Revenant has been beaten and its hoard spilled into Soul Caches at {@code caches}.
 * <li>{@link #HOARD_SANK}: a player died on the Isle and their hoard sank deeper.
 * </ul>
 *
 * Other useful pieces: {@link IsleDimension#LEVEL} and {@link IsleDimension#is}, {@link IsleDepth#at} (which depth a
 * spot is in), {@link IsleArenas#centre} (where a player's arena is at a depth), {@link Hoards} (every unrecovered
 * hoard), {@link Isle#ISLE_GROUND} (the block tag shades should spawn on) and the three Isle biomes, whose
 * {@code natural_mob_spawns} are empty and waiting.
 */
public final class IsleEvents {
	public static final Event<Arrived> ARRIVED = EventFactory.createArrayBacked(Arrived.class, listeners -> (player, depth, offering) -> {
		for (Arrived listener : listeners) {
			listener.onArrived(player, depth, offering);
		}
	});

	public static final Event<Left> LEFT = EventFactory.createArrayBacked(Left.class, listeners -> (player, victorious) -> {
		for (Left listener : listeners) {
			listener.onLeft(player, victorious);
		}
	});

	public static final Event<RevenantRisen> REVENANT_RISEN = EventFactory.createArrayBacked(RevenantRisen.class, listeners -> (level, revenant, hoard) -> {
		for (RevenantRisen listener : listeners) {
			listener.onRevenantRisen(level, revenant, hoard);
		}
	});

	public static final Event<RevenantDefeated> REVENANT_DEFEATED = EventFactory.createArrayBacked(RevenantDefeated.class,
			listeners -> (level, revenant, hoard, caches) -> {
				for (RevenantDefeated listener : listeners) {
					listener.onRevenantDefeated(level, revenant, hoard, caches);
				}
			});

	public static final Event<HoardSank> HOARD_SANK = EventFactory.createArrayBacked(HoardSank.class, listeners -> (player, hoard, from) -> {
		for (HoardSank listener : listeners) {
			listener.onHoardSank(player, hoard, from);
		}
	});

	private IsleEvents() {
	}

	@FunctionalInterface
	public interface Arrived {
		/** @param depth    the depth of the player's hoard, if they have one, else the Shore
		 * @param offering whether they came by offering (and have Soul Ward) */
		void onArrived(ServerPlayer player, IsleDepth depth, boolean offering);
	}

	@FunctionalInterface
	public interface Left {
		/** @param victorious whether they left through a Return Rift (having beaten their Revenant) */
		void onLeft(ServerPlayer player, boolean victorious);
	}

	@FunctionalInterface
	public interface RevenantRisen {
		void onRevenantRisen(ServerLevel level, Revenant revenant, Hoard hoard);
	}

	@FunctionalInterface
	public interface RevenantDefeated {
		void onRevenantDefeated(ServerLevel level, Revenant revenant, Hoard hoard, java.util.List<BlockPos> caches);
	}

	@FunctionalInterface
	public interface HoardSank {
		void onHoardSank(ServerPlayer player, Hoard hoard, IsleDepth from);
	}
}
