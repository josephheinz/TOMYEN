package com.jheinz.mymod.fishing;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Testing aids for operators:
 * <ul>
 * <li>{@code /mymod fishing test <difficulty> <behavior>}: plays that minigame right away, on the held rod's numbers
 * (no fish is caught; the result goes to chat);</li>
 * <li>{@code /mymod fishing species}: the species that could bite here, as if the held rod's bobber were at your feet
 * (in lava if you stand in or over it), with each one's odds.</li>
 * </ul>
 */
public final class FishingCommands {
	private FishingCommands() {
	}

	static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("mymod")
				.then(Commands.literal("fishing").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("test")
								.then(Commands.argument("difficulty", IntegerArgumentType.integer(1, 5))
										.executes(command -> test(command, FishBehavior.STEADY))
										.then(Commands.argument("behavior", StringArgumentType.word())
												.suggests((command, builder) -> SharedSuggestionProvider.suggest(
														Arrays.stream(FishBehavior.values()).map(FishBehavior::id), builder))
												.executes(command -> {
													String name = StringArgumentType.getString(command, "behavior");
													FishBehavior behavior = FishBehavior.byId(name.toLowerCase(Locale.ROOT));
													if (behavior == null) {
														command.getSource().sendFailure(Component.translatable("commands.mymod.fishing.bad_behavior", name));
														return 0;
													}
													return test(command, behavior);
												}))))
						.then(Commands.literal("species").executes(FishingCommands::species)))));
	}

	private static int test(CommandContext<CommandSourceStack> command, FishBehavior behavior) throws CommandSyntaxException {
		ServerPlayer player = command.getSource().getPlayerOrException();
		int difficulty = IntegerArgumentType.getInteger(command, "difficulty");
		FishingFights.startTest(player, difficulty, behavior);
		command.getSource().sendSuccess(() -> Component.translatable("commands.mymod.fishing.test.started",
				Component.translatable("hud.mymod.fishing.game." + behavior.game().name().toLowerCase(Locale.ROOT)), behavior.id(), difficulty),
				false);
		return 1;
	}

	private static int species(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
		CommandSourceStack source = command.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		ItemStack rod = player.getMainHandItem().getItem() instanceof RodItem ? player.getMainHandItem() : player.getOffhandItem();
		RodStats stats = RodStats.of(rod);
		BlockPos pos = player.blockPosition();
		boolean lava = level.getFluidState(pos).is(FluidTags.LAVA) || level.getFluidState(pos.below()).is(FluidTags.LAVA);
		List<FishingSpecies> eligible = FishingSpecies.eligible(new FishingSpecies.Where(level, pos, lava, stats.tier()), FishingSpecies.all());
		source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.species.header", eligible.size(),
				FishingSpecies.all().size(), stats.tier(), lava ? "lava" : "water"), false);
		if (eligible.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.species.none").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		float bias = stats.rarityBias(level.isDarkOutside(), Mth.floor(player.getLuck()));
		double total = eligible.stream().mapToDouble(species -> species.biasedWeight(bias)).sum();
		for (FishingSpecies species : eligible) {
			double chance = 100.0 * species.biasedWeight(bias) / total;
			source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.species.line", species.stack().getHoverName(),
					String.format(Locale.ROOT, "%.1f", chance), species.weight(), FishingFights.effectiveDifficulty(species, stats),
					species.behavior().id(), species.id().toString()).withStyle(ChatFormatting.GRAY), false);
		}
		return eligible.size();
	}
}
