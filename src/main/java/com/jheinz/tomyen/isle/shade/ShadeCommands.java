package com.jheinz.tomyen.isle.shade;

import java.io.IOException;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import com.jheinz.tomyen.isle.IsleDimension;

/** The shade and Soul Tide parts of {@code /isle} (see IsleCommands), and {@code /isle survey} (see IsleSurvey). */
public final class ShadeCommands {
	private ShadeCommands() {
	}

	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("survey")
				.executes(command -> survey(command.getSource(), 4))
				.then(Commands.argument("step", IntegerArgumentType.integer(1, 16))
						.executes(command -> survey(command.getSource(), IntegerArgumentType.getInteger(command, "step")))));
		root.then(Commands.literal("souls")
				.executes(command -> souls(command.getSource()))
				.then(Commands.literal("clear").executes(command -> {
					IsleSouls.get(command.getSource().getServer()).clearRecords();
					return souls(command.getSource());
				})));
		root.then(Commands.literal("shades").then(Commands.literal("clear").executes(command -> {
			int count = ShadeManager.active();
			ShadeManager.clear();
			return count;
		})));
		root.then(Commands.literal("tide")
				.then(Commands.literal("add").then(Commands.argument("type", IdentifierArgument.id())
						.then(Commands.argument("count", IntegerArgumentType.integer(1, IsleSouls.TIDE_TYPE_CAP)).executes(command -> {
							Identifier type = IdentifierArgument.getId(command, "type");
							if (BuiltInRegistries.ENTITY_TYPE.getOptional(type).isEmpty()) {
								command.getSource().sendFailure(Component.literal("Unknown entity type " + type));
								return 0;
							}
							IsleSouls.get(command.getSource().getServer()).addTide(type, IntegerArgumentType.getInteger(command, "count"));
							return souls(command.getSource());
						}))))
				.then(Commands.literal("clear").executes(command -> {
					IsleSouls.get(command.getSource().getServer()).clearTide();
					return souls(command.getSource());
				}))
				.then(Commands.literal("wave")
						.executes(command -> {
							ShadeManager.waveNow(command.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.argument("player", EntityArgument.player()).executes(command -> {
							ShadeManager.waveNow(EntityArgument.getPlayer(command, "player"));
							return 1;
						}))));
	}

	private static int survey(CommandSourceStack source, int step) {
		ServerLevel isle = source.getServer().getLevel(IsleDimension.LEVEL);
		if (isle == null) {
			source.sendFailure(Component.translatable("message.tomyen.isle.missing"));
			return 0;
		}
		try {
			return IsleSurvey.survey(isle, step, source::sendSystemMessage);
		} catch (IOException e) {
			source.sendFailure(Component.literal("Couldn't write the survey: " + e.getMessage()));
			return 0;
		}
	}

	private static int souls(CommandSourceStack source) {
		IsleSouls souls = IsleSouls.get(source.getServer());
		StringBuilder top = new StringBuilder();
		for (Map.Entry<Identifier, Integer> entry : souls.topTide(5)) {
			if (!top.isEmpty()) {
				top.append(", ");
			}
			top.append(entry.getKey().getPath()).append(' ').append(entry.getValue());
		}
		source.sendSystemMessage(Component.translatable("commands.tomyen.isle.souls", souls.recordCount(), souls.regionCount(), souls.bossCount(),
				souls.tideTotal(), souls.tideTypes(), String.format("%.2f", souls.tideStrength()), top.isEmpty() ? "-" : top.toString()));
		source.sendSystemMessage(Component.translatable("commands.tomyen.isle.shades", ShadeManager.active(), ShadeManager.peak(),
				ShadeManager.spawned(), ShadeManager.killed(), ShadeManager.dissolved(), SoulRecorder.deaths(), SoulRecorder.averageNanos()));
		return souls.recordCount();
	}
}
