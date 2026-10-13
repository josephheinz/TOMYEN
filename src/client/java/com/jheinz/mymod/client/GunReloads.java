package com.jheinz.mymod.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.launcher.RocketLauncherItem;
import com.jheinz.mymod.napalm.FlamethrowerItem;
import com.jheinz.mymod.shotgun.ShotgunItem;

/**
 * Who in the level is reloading a magazine gun, a revolver or a flamethrower, as every client can see it: a held gun's
 * rounds go up all at once when its magazine (or cylinder, or tank) is swapped, and its data is synced to everyone who
 * can see the holder. The shotguns, homemade guns and the RPG-7 keep their action on the stack instead, so their
 * reloads are already visible to all; this covers the rest, for third-person arm poses (GunArmPoses) and for the
 * magazine coming out of other players' guns. The local player is tracked the same way, for third-person view.
 */
public final class GunReloads {
	public enum Kind {
		/** A magazine, belt box or speedloader swapped in. */
		MAGAZINE,
		/** One round thumbed into a cylinder. */
		ROUND,
		/** A flamethrower's tank refilled. */
		REFUEL
	}

	public record Reload(Kind kind, Item item, long startMillis, long durationMillis) {
		public float progress() {
			return (Util.getMillis() - startMillis) / (float) durationMillis;
		}
	}

	/** As GunAnimation's single round, so a stream of them reads the same from outside. */
	private static final long ROUND_MILLIS = 500;

	private record Seen(Item item, int rounds) {
	}

	private static final Map<Integer, Seen> SEEN = new HashMap<>();
	private static final Map<Integer, Reload> RELOADS = new HashMap<>();

	private GunReloads() {
	}

	/** Called every client tick: compares each player's held gun with last tick's. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			SEEN.clear();
			RELOADS.clear();
			return;
		}
		Set<Integer> present = new HashSet<>();
		for (Player player : client.level.players()) {
			int id = player.getId();
			present.add(id);
			ItemStack held = player.getMainHandItem();
			Item item = held.getItem();
			if (!(item instanceof GunItem gun) || item instanceof ShotgunItem || item instanceof RocketLauncherItem) {
				SEEN.remove(id);
				continue;
			}
			MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
			int rounds = loaded == null ? 0 : loaded.rounds();
			Seen last = SEEN.put(id, new Seen(item, rounds));
			if (last == null || last.item() != item || rounds <= last.rounds()) {
				continue;
			}
			float factor = GunItem.reloadFactor(player);
			if (item instanceof FlamethrowerItem) {
				RELOADS.put(id, new Reload(Kind.REFUEL, item, Util.getMillis(), (long) (gun.stats().reloadTicks() * 50L * factor)));
			} else if (gun.stats().singleRounds() && rounds - last.rounds() == 1) {
				RELOADS.put(id, new Reload(Kind.ROUND, item, Util.getMillis(), ROUND_MILLIS));
			} else {
				int extra = gun.magazines().isEmpty() || gun.stats().singleRounds() ? 0 : gun.loadedMagazine(held).extraReloadTicks();
				RELOADS.put(id, new Reload(Kind.MAGAZINE, item, Util.getMillis(), (long) ((gun.stats().reloadTicks() + extra) * 50L * factor)));
			}
		}
		SEEN.keySet().retainAll(present);
		RELOADS.keySet().retainAll(present);
	}

	/** The reload this entity is in the middle of, if any. */
	public static Reload of(Entity entity) {
		return of(entity.getId());
	}

	public static Reload of(int entityId) {
		Reload reload = RELOADS.get(entityId);
		return reload != null && reload.progress() < 1.0f ? reload : null;
	}
}
