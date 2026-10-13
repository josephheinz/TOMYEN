package com.jheinz.mymod.werewolf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Testing aids for operators: {@code /mymod werewolf curse|cure|check|transform|revert [targets]}. Without targets
 * they act on the villager or werewolf the player is looking at. {@code transform} starts a cursed (or newly cursed)
 * villager changing whatever the moon; {@code revert} turns a werewolf back whatever the hour.
 */
public final class WerewolfCommands {
	/** How far off the villager or werewolf being looked at may be. */
	private static final double REACH = 32.0;

	private WerewolfCommands() {
	}

	private interface Action {
		/** Does this to one entity; returns whether it applied to it. */
		boolean apply(CommandSourceStack source, ServerLevel level, Entity entity);
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("mymod")
				// The permission sits on "werewolf", so other /mymod subcommands can set their own.
				.then(Commands.literal("werewolf").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(action("curse", (source, level, entity) -> {
							if (!(entity instanceof Villager villager)) {
								return false;
							}
							WerewolfCurse.curse(villager);
							source.sendSuccess(() -> Component.translatable("commands.mymod.werewolf.cursed", villager.getDisplayName()), true);
							return true;
						}))
						.then(action("cure", (source, level, entity) -> {
							if (!WerewolfCurse.isCursed(entity)) {
								return false;
							}
							WerewolfCurse.cure(level, (Villager) entity, null);
							source.sendSuccess(() -> Component.translatable("commands.mymod.werewolf.cured", entity.getDisplayName()), true);
							return true;
						}))
						.then(action("check", (source, level, entity) -> {
							if (entity instanceof Werewolf) {
								source.sendSuccess(() -> Component.translatable("commands.mymod.werewolf.is_werewolf", entity.getDisplayName()), false);
							} else if (entity instanceof Villager villager) {
								String key = !villager.hasAttached(Werewolves.CURSED) ? "commands.mymod.werewolf.unrolled"
										: WerewolfCurse.isCursed(villager) ? "commands.mymod.werewolf.is_cursed" : "commands.mymod.werewolf.not_cursed";
								source.sendSuccess(() -> Component.translatable(key, villager.getDisplayName()), false);
							} else {
								return false;
							}
							return true;
						}))
						.then(action("transform", (source, level, entity) -> {
							if (!(entity instanceof Villager villager)) {
								return false;
							}
							WerewolfCurse.curse(villager);
							WerewolfCurse.startTransform(level, villager);
							source.sendSuccess(() -> Component.translatable("commands.mymod.werewolf.transforming", villager.getDisplayName()), true);
							return true;
						}))
						.then(action("revert", (source, level, entity) -> {
							if (!(entity instanceof Werewolf werewolf)) {
								return false;
							}
							Villager villager = WerewolfCurse.revert(level, werewolf);
							if (villager == null) {
								return false;
							}
							source.sendSuccess(() -> Component.translatable("commands.mymod.werewolf.reverted", villager.getDisplayName()), true);
							return true;
						})))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> action(String name, Action action) {
		return Commands.literal(name)
				.executes(command -> run(command, lookedAt(command.getSource()), action))
				.then(Commands.argument("targets", EntityArgument.entities())
						.executes(command -> run(command, new ArrayList<>(EntityArgument.getEntities(command, "targets")), action)));
	}

	private static int run(CommandContext<CommandSourceStack> command, Collection<? extends Entity> targets, Action action) {
		CommandSourceStack source = command.getSource();
		int applied = 0;
		for (Entity entity : targets) {
			if (entity.level() instanceof ServerLevel level && action.apply(source, level, entity)) {
				applied++;
			}
		}
		if (applied == 0) {
			source.sendFailure(Component.translatable("commands.mymod.werewolf.no_target"));
		}
		return applied;
	}

	/** The villager or werewolf the player is looking at, if any. */
	private static List<Entity> lookedAt(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Vec3 eye = player.getEyePosition();
		Vec3 reach = player.getViewVector(1.0f).scale(REACH);
		AABB area = player.getBoundingBox().expandTowards(reach).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(reach), area,
				entity -> entity instanceof Villager || entity instanceof Werewolf, REACH * REACH);
		return hit == null ? List.of() : List.of(hit.getEntity());
	}
}
