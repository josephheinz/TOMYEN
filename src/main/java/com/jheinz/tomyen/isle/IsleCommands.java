package com.jheinz.tomyen.isle;

import java.util.Optional;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.isle.shade.ShadeCommands;

/**
 * {@code /isle}, for operators: looking into and steering the Isle of the Damned when testing it, or putting a lost
 * gravestone back.
 *
 * <ul>
 * <li>{@code /isle hoard [player]}: what's in their hoard, how deep, and where its stone and arena are.
 * <li>{@code /isle send [player]}: down to the Isle, as if they'd paid respects (they come back to where they stood).
 * <li>{@code /isle home [player]}: back from it, as by the Lantern of Return.
 * <li>{@code /isle arena [player]}: to the edge of their Revenant's arena (built if need be).
 * <li>{@code /isle sink [player]}: their hoard one depth deeper, as if they'd died on the Isle.
 * <li>{@code /isle gravestone [player]}: a new gravestone for their hoard where they stand (the old one crumbles).
 * <li>{@code /isle souls}: what the Isle remembers (records, regions, bosses), the Soul Tide and its biggest types,
 * the shades about now, and what recording a death costs.
 * <li>{@code /isle souls clear}: forgets every record; {@code /isle shades clear}: dissolves every shade.
 * <li>{@code /isle tide add <type> <count>}, {@code /isle tide clear}, {@code /isle tide wave [player]} (their next
 * wave now).
 * <li>{@code /isle survey [step]}: a map of the Isle's terrain from its generator, and each ring's share of land.
 * </ul>
 */
final class IsleCommands {
	private IsleCommands() {
	}

	static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("isle").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			root.then(withPlayer("hoard", IsleCommands::hoard));
			root.then(withPlayer("send", (source, player) -> {
				IsleDimension.sendIn(player, GlobalPos.of(player.level().dimension(), player.blockPosition()), false);
				return 1;
			}));
			root.then(withPlayer("home", (source, player) -> {
				IsleDimension.sendHome(player, false);
				return 1;
			}));
			root.then(withPlayer("arena", IsleCommands::arena));
			root.then(withPlayer("sink", IsleCommands::sink));
			root.then(withPlayer("gravestone", IsleCommands::gravestone));
			ShadeCommands.register(root);
			dispatcher.register(root);
		});
	}

	@FunctionalInterface
	private interface Action {
		int run(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> withPlayer(String name, Action action) {
		return Commands.literal(name)
				.executes(command -> action.run(command.getSource(), command.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(command -> action.run(command.getSource(), EntityArgument.getPlayer(command, "player"))));
	}

	private static int hoard(CommandSourceStack source, ServerPlayer player) {
		Hoard hoard = Hoards.get(source.getServer(), player.getUUID());
		if (hoard == null) {
			source.sendSystemMessage(Component.translatable("commands.tomyen.isle.no_hoard", player.getDisplayName()));
			return 0;
		}
		source.sendSystemMessage(Component.translatable("commands.tomyen.isle.hoard", player.getDisplayName(), hoard.items().size(),
				hoard.experience(), hoard.depth().displayName(),
				hoard.gravestone().map(g -> g.pos().toShortString() + " " + g.dimension().identifier()).orElse("-"),
				IsleArenas.target(source.getServer().getLevel(IsleDimension.LEVEL) != null ? source.getServer().getLevel(IsleDimension.LEVEL)
						: source.getServer().overworld(), hoard).toShortString(),
				String.format("%.2f", hoard.loadout().score())));
		return hoard.items().size();
	}

	private static int arena(CommandSourceStack source, ServerPlayer player) {
		ServerLevel isle = source.getServer().getLevel(IsleDimension.LEVEL);
		Hoard hoard = Hoards.get(source.getServer(), player.getUUID());
		if (isle == null || hoard == null) {
			source.sendSystemMessage(Component.translatable("commands.tomyen.isle.no_hoard", player.getDisplayName()));
			return 0;
		}
		if (!IsleDimension.is(player.level())) {
			player.setAttached(Isle.RETURN_POINT, GlobalPos.of(player.level().dimension(), player.blockPosition()));
		}
		BlockPos centre = IsleArenas.ensureBuilt(isle, hoard);
		BlockPos edge = centre.offset(0, 0, 8);
		player.teleport(new TeleportTransition(isle, Vec3.atBottomCenterOf(edge), Vec3.ZERO, 180.0f, 0.0f, TeleportTransition.DO_NOTHING));
		return 1;
	}

	private static int sink(CommandSourceStack source, ServerPlayer player) {
		Hoard hoard = Hoards.get(source.getServer(), player.getUUID());
		if (hoard == null) {
			source.sendSystemMessage(Component.translatable("commands.tomyen.isle.no_hoard", player.getDisplayName()));
			return 0;
		}
		RevenantManager.dismiss(source.getServer(), player.getUUID());
		Hoard sunk = hoard.withDepth(hoard.depth().deeper());
		Hoards.put(source.getServer(), sunk);
		IsleDimension.updateHint(player);
		IsleEvents.HOARD_SANK.invoker().onHoardSank(player, sunk, hoard.depth());
		return hoard(source, player);
	}

	private static int gravestone(CommandSourceStack source, ServerPlayer player) {
		Hoard hoard = Hoards.get(source.getServer(), player.getUUID());
		if (hoard == null || IsleDimension.is(player.level())) {
			source.sendSystemMessage(Component.translatable("commands.tomyen.isle.no_hoard", player.getDisplayName()));
			return 0;
		}
		hoard.gravestone().ifPresent(old -> DeathCapture.clearGravestone(source.getServer(), old));
		GlobalPos grave = GravestonePlacement.place(source.getServer(), player.level(), player.blockPosition(), player.getDirection().getOpposite(),
				player.getUUID(), hoard.ownerName());
		Hoards.put(source.getServer(), hoard.withGravestone(Optional.of(grave)));
		return hoard(source, player);
	}
}
