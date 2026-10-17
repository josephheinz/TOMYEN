package com.jheinz.tomyen.isle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.starforge.InfusionEffects;

/**
 * What happens to a dying player's things (called from PlayerDeathHoardMixin, just before vanilla would drop them).
 *
 * <p>With keepInventory off, everything that would drop goes into the player's {@link Hoard} instead, and half their
 * experience with it (none drops as orbs). Soulbound gear is set aside first and comes back on respawn as always;
 * anything with Curse of Vanishing is gone, as it would be. A snapshot of what they wore and held is kept for their
 * Revenant to wear, and their profile for its face.
 *
 * <p>Dying anywhere but the Isle puts up a {@link GravestoneBlock} near the death spot (see
 * {@link GravestonePlacement}) and clears the stone of any earlier death: one hoard, one way down. Dying on the Isle
 * adds whatever they brought with them to the hoard and drags it one depth deeper ({@link IsleDepth#deeper}); its
 * Revenant re-forms down there. Their existing stone still leads to it; if there isn't one (they'd just won their last
 * hoard back, say), one goes up where they respawn.
 *
 * <p>An empty inventory leaves nothing behind: no hoard, no stone, no Revenant, and experience drops as usual.
 */
public final class DeathCapture {
	/** The share of a player's experience points that goes with their hoard; the rest is lost. */
	private static final float EXPERIENCE_SHARE = 0.5f;

	private DeathCapture() {
	}

	static void initialize() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				newPlayer.removeAttached(Isle.RETURN_POINT);
				ensureGravestone(newPlayer);
			}
		});
	}

	/** Called as the player dies, before vanilla drops their inventory: takes it all into their hoard. */
	public static void capture(ServerPlayer player, ServerLevel level) {
		if (level.getGameRules().get(GameRules.KEEP_INVENTORY) || player.isSpectator()) {
			return;
		}
		MinecraftServer server = level.getServer();
		Loadout loadout = Loadout.of(player);
		InfusionEffects.stashSoulbound(player, level);

		List<ItemStack> taken = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack removed = inventory.removeItemNoUpdate(slot);
			if (!EnchantmentHelper.has(removed, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				taken.add(removed);
			}
		}

		boolean onIsle = IsleDimension.is(level);
		Hoard existing = Hoards.get(server, player.getUUID());
		if (taken.isEmpty() && !(onIsle && existing != null)) {
			return;
		}

		int experience = 0;
		if (!taken.isEmpty()) {
			experience = Math.round(totalExperience(player) * EXPERIENCE_SHARE);
			player.skipDropExperience();
		}

		String name = player.getGameProfile().name();
		ResolvableProfile profile = ResolvableProfile.createResolved(player.getGameProfile());
		IsleDepth here = onIsle ? IsleDepth.at(level, player.blockPosition()) : IsleDepth.SHORE;
		Hoard hoard;
		if (existing == null) {
			hoard = new Hoard(UUID.randomUUID(), player.getUUID(), name, profile, List.copyOf(taken), experience, loadout,
					onIsle ? here.deeper() : IsleDepth.SHORE, Optional.empty(), Optional.empty());
		} else {
			List<ItemStack> items = new ArrayList<>(existing.items());
			items.addAll(taken);
			hoard = existing.withItems(items, existing.experience() + experience).withLoadout(existing.loadout().strongest(loadout))
					.withProfile(name, profile);
		}

		IsleDepth before = hoard.depth();
		if (onIsle && existing != null) {
			IsleDepth from = here.index > existing.depth().index ? here : existing.depth();
			hoard = hoard.withDepth(from.deeper());
		}
		RevenantManager.dismiss(server, player.getUUID());

		if (!onIsle) {
			hoard.gravestone().ifPresent(old -> clearGravestone(server, old));
			GlobalPos grave = GravestonePlacement.place(server, level, player.blockPosition(), player.getDirection().getOpposite(), player.getUUID(),
					name);
			hoard = hoard.withGravestone(Optional.of(grave));
			player.sendSystemMessage(Component.translatable("message.tomyen.isle.dragged_away").withStyle(ChatFormatting.DARK_AQUA));
			player.sendSystemMessage(Component.translatable("message.tomyen.isle.gravestone_at", grave.pos().getX(), grave.pos().getY(),
					grave.pos().getZ(), Component.translatableWithFallback(dimensionKey(grave), grave.dimension().identifier().toString())).withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.translatable("message.tomyen.isle.sinks", hoard.depth().displayName())
					.withStyle(ChatFormatting.DARK_AQUA));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), Isle.HOARD_SINKS, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		Hoards.put(server, hoard);
		MyMod.LOGGER.info("Isle of the Damned: {} died at {} in {}; hoard of {} stacks and {} xp now at depth {}", name,
				player.blockPosition().toShortString(), level.dimension().identifier(), hoard.items().size(), hoard.experience(),
				hoard.depth().name);
		if (onIsle && existing != null && hoard.depth() != before) {
			IsleEvents.HOARD_SANK.invoker().onHoardSank(player, hoard, before);
		}
	}

	/** After respawning: a hoard with no stone leading to it (lost on the Isle) gets one where they stand. */
	static void ensureGravestone(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Hoard hoard = Hoards.get(server, player.getUUID());
		if (hoard == null || hoard.gravestone().isPresent() || IsleDimension.is(player.level())) {
			return;
		}
		GlobalPos grave = GravestonePlacement.place(server, player.level(), player.blockPosition(), player.getDirection().getOpposite(),
				player.getUUID(), hoard.ownerName());
		Hoards.put(server, hoard.withGravestone(Optional.of(grave)));
		player.sendSystemMessage(Component.translatable("message.tomyen.isle.gravestone_at", grave.pos().getX(), grave.pos().getY(),
				grave.pos().getZ(), Component.translatableWithFallback(dimensionKey(grave), grave.dimension().identifier().toString())).withStyle(ChatFormatting.GRAY));
	}

	/** Takes down an older death's gravestone if its chunk is loaded; otherwise it goes by itself when next loaded. */
	static void clearGravestone(MinecraftServer server, GlobalPos grave) {
		ServerLevel level = server.getLevel(grave.dimension());
		if (level != null && level.isLoaded(grave.pos()) && level.getBlockState(grave.pos()).is(Isle.GRAVESTONE)) {
			GravestoneBlock.crumble(level, grave.pos(), level.getBlockState(grave.pos()));
		}
	}

	static String dimensionKey(GlobalPos pos) {
		return "dimension." + pos.dimension().identifier().getNamespace() + "." + pos.dimension().identifier().getPath();
	}

	/** All the experience points the player has right now, from their level and how far into the next they are. */
	static int totalExperience(ServerPlayer player) {
		int level = player.experienceLevel;
		long points;
		if (level <= 16) {
			points = (long) level * level + 6L * level;
		} else if (level <= 31) {
			points = Math.round(2.5 * level * level - 40.5 * level + 360);
		} else {
			points = Math.round(4.5 * level * level - 162.5 * level + 2220);
		}
		points += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
		return (int) Math.min(Integer.MAX_VALUE, points);
	}
}
