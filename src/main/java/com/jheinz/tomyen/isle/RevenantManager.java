package com.jheinz.tomyen.isle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.jheinz.tomyen.MyMod;

/**
 * Raises Revenants when their owners come for them and lays them to rest when they go, so a Revenant only exists (and
 * ticks) while its owner is on the Isle near its arena. Revenants are never saved; this is the only thing that makes
 * them.
 *
 * <p>Twice a second: an owner on the Isle within {@value #RISE_RANGE} blocks of their arena (built then if it isn't
 * yet) finds their Revenant forming at its heart. One whose owner has logged off, left the Isle, died, or wandered
 * more than {@value #REST_RANGE} blocks off fades after {@value #REST_DELAY} ticks; it comes back at full health next
 * time.
 *
 * <p>When one is beaten, the hoard spills into Soul Caches at the arena's heart (54 stacks to a cache; the first holds
 * the experience), the hoard is cleared, its gravestone crumbles, and a Return Rift opens.
 */
public final class RevenantManager {
	private static final int INTERVAL = 10;
	public static final int RISE_RANGE = 40;
	private static final int REST_RANGE = 80;
	private static final int REST_DELAY = 100;

	private static final Map<UUID, Revenant> RISEN = new HashMap<>();
	private static final Map<UUID, Integer> AWAY = new HashMap<>();

	private RevenantManager() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(RevenantManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RISEN.clear();
			AWAY.clear();
		});
	}

	public static boolean isTracked(Revenant revenant) {
		return revenant.owner() != null && RISEN.get(revenant.owner()) == revenant;
	}

	/** The owner's risen Revenant, if there is one. */
	public static Revenant get(UUID owner) {
		Revenant revenant = RISEN.get(owner);
		return revenant != null && !revenant.isRemoved() ? revenant : null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		ServerLevel isle = server.getLevel(IsleDimension.LEVEL);
		// Rest the ones whose owners have gone.
		for (Iterator<Map.Entry<UUID, Revenant>> it = RISEN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Revenant> entry = it.next();
			Revenant revenant = entry.getValue();
			if (revenant.isRemoved()) {
				it.remove();
				AWAY.remove(entry.getKey());
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			boolean near = owner != null && owner.isAlive() && owner.level() == revenant.level()
					&& owner.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(revenant.getHomePosition())) < REST_RANGE * REST_RANGE;
			if (near) {
				AWAY.remove(entry.getKey());
				continue;
			}
			int away = AWAY.merge(entry.getKey(), INTERVAL, Integer::sum);
			if (away >= REST_DELAY || owner == null) {
				rest(revenant);
				it.remove();
				AWAY.remove(entry.getKey());
			}
		}
		if (isle == null) {
			return;
		}
		// Raise the ones whose owners have come.
		for (ServerPlayer player : List.copyOf(isle.players())) {
			if (!player.isAlive() || player.isSpectator() || get(player.getUUID()) != null) {
				continue;
			}
			Hoard hoard = Hoards.get(server, player.getUUID());
			if (hoard == null) {
				continue;
			}
			BlockPos target = IsleArenas.target(isle, hoard);
			double dx = player.getX() - (target.getX() + 0.5), dz = player.getZ() - (target.getZ() + 0.5);
			if (dx * dx + dz * dz > RISE_RANGE * RISE_RANGE) {
				continue;
			}
			BlockPos centre = IsleArenas.ensureBuilt(isle, hoard);
			raise(isle, Hoards.get(server, player.getUUID()), centre, player);
		}
	}

	private static void raise(ServerLevel level, Hoard hoard, BlockPos centre, ServerPlayer owner) {
		Revenant revenant = Isle.REVENANT.create(level, EntitySpawnReason.EVENT);
		if (revenant == null) {
			return;
		}
		revenant.snapTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, owner.getYRot() + 180.0f, 0.0f);
		revenant.become(hoard, centre);
		RISEN.put(hoard.owner(), revenant);
		level.addFreshEntity(revenant);
		level.playSound(null, centre, Isle.REVENANT_FORM, SoundSource.HOSTILE, 2.0f, 1.0f);
		level.sendParticles(ParticleTypes.SOUL, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 40, 0.6, 0.8, 0.6, 0.05);
		owner.sendSystemMessage(Component.translatable("message.tomyen.isle.revenant_rises").withStyle(ChatFormatting.DARK_AQUA));
		MyMod.LOGGER.info("Isle of the Damned: {}'s Revenant rose at {} (depth {}, health {})", hoard.ownerName(), centre.toShortString(),
				hoard.depth().name, revenant.getMaxHealth());
		IsleEvents.REVENANT_RISEN.invoker().onRevenantRisen(level, revenant, hoard);
	}

	/** Lays it to rest: it fades in a breath of souls. Its hoard stays where it is. */
	private static void rest(Revenant revenant) {
		if (revenant.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SOUL, revenant.getX(), revenant.getY(1.0), revenant.getZ(), 20, 0.3, 0.8, 0.3, 0.03);
		}
		revenant.discard();
	}

	/** Lays the owner's Revenant to rest at once (its hoard has moved, say). */
	public static void dismiss(MinecraftServer server, UUID owner) {
		Revenant revenant = RISEN.remove(owner);
		AWAY.remove(owner);
		if (revenant != null && !revenant.isRemoved()) {
			rest(revenant);
		}
	}

	/** Its owner's hoard spills into Soul Caches, the hoard is cleared, and a Return Rift opens. */
	static void defeated(ServerLevel level, Revenant revenant) {
		UUID ownerId = revenant.owner();
		if (ownerId == null || !isTracked(revenant)) {
			return;
		}
		RISEN.remove(ownerId);
		AWAY.remove(ownerId);
		MinecraftServer server = level.getServer();
		Hoard hoard = Hoards.get(server, ownerId);
		if (hoard == null) {
			return;
		}
		BlockPos centre = revenant.getHomePosition();
		List<ItemStack> items = hoard.itemCopies();
		int caches = Math.max(1, (items.size() + SoulCacheBlockEntity.SIZE - 1) / SoulCacheBlockEntity.SIZE);
		List<BlockPos> spots = IsleStructures.cacheSpots(centre, caches);
		List<BlockPos> placed = new ArrayList<>();
		for (int i = 0; i < caches; i++) {
			BlockPos spot = spots.get(i);
			level.setBlock(spot, Isle.SOUL_CACHE.defaultBlockState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(spot) instanceof SoulCacheBlockEntity cache) {
				int from = i * SoulCacheBlockEntity.SIZE;
				cache.fill(ownerId, hoard.ownerName(), items.subList(from, Math.min(items.size(), from + SoulCacheBlockEntity.SIZE)),
						i == 0 ? hoard.experience() : 0);
				placed.add(spot);
			}
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
		}
		Hoards.remove(server, ownerId);
		hoard.gravestone().ifPresent(grave -> DeathCapture.clearGravestone(server, grave));
		BlockPos rift = IsleStructures.openRift(level, centre);
		level.playSound(null, rift, Isle.RIFT_OPENS, SoundSource.BLOCKS, 1.5f, 1.0f);

		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (owner != null) {
			owner.sendSystemMessage(Component.translatable("message.tomyen.isle.revenant_defeated").withStyle(ChatFormatting.AQUA));
			IsleDimension.updateHint(owner);
		}
		MyMod.LOGGER.info("Isle of the Damned: {}'s Revenant was beaten; {} stacks in {} soul cache(s), rift at {}", hoard.ownerName(), items.size(),
				placed.size(), rift.toShortString());
		IsleEvents.REVENANT_DEFEATED.invoker().onRevenantDefeated(level, revenant, hoard, placed);
	}
}
