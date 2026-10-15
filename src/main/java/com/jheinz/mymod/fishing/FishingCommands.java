package com.jheinz.mymod.fishing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.FloatArgumentType;
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
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/**
 * Testing aids for operators:
 * <ul>
 * <li>{@code /mymod fishing test <difficulty> <behavior>}: plays that minigame right away, on the held rod's numbers
 * (no fish is caught; the result goes to chat);</li>
 * <li>{@code /mymod fishing test <species> <lb>}: that fish's fight at that weight, with its {@link FightStrength};</li>
 * <li>{@code /mymod fishing species}: the species that could bite here, as if the held rod's bobber were at your feet
 * (in lava if you stand in or over it), with each one's odds;</li>
 * <li>{@code /mymod fishing give_trophy <species> <lb>}: a trophy of that species and weight, caught by you today.</li>
 * </ul>
 * And for everyone: {@code /mymod fishing records}, your heaviest catch of every species.
 */
public final class FishingCommands {
	private FishingCommands() {
	}

	static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("mymod")
				.then(Commands.literal("fishing")
						.then(Commands.literal("records").executes(FishingCommands::records))
						.then(Commands.literal("give_trophy").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("species", StringArgumentType.string())
										.suggests((command, builder) -> SharedSuggestionProvider.suggest(FishingSpecies.all().stream()
												.filter(species -> species.size() != null).map(species -> species.id().getPath()), builder))
										.then(Commands.argument("lb", FloatArgumentType.floatArg(0.001f, 100000.0f))
												.executes(FishingCommands::giveTrophy))))
						.then(Commands.literal("test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("species", StringArgumentType.word())
										.suggests((command, builder) -> SharedSuggestionProvider.suggest(FishingSpecies.all().stream()
												.map(species -> species.id().getPath()), builder))
										.then(Commands.argument("lb", FloatArgumentType.floatArg(0.001f, 100000.0f))
												.executes(FishingCommands::testSpecies)))
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
						.then(Commands.literal("species").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(FishingCommands::species)))));
	}

	private static int giveTrophy(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
		ServerPlayer player = command.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(command, "species");
		FishingSpecies species = FishingSpecies.byName(name);
		if (species == null || species.size() == null) {
			command.getSource().sendFailure(Component.translatable("commands.mymod.fishing.bad_species", name));
			return 0;
		}
		float weight = FishSize.round(FloatArgumentType.getFloat(command, "lb"));
		ItemStack trophy = TrophyFish.make(species.stack(), new TrophyFish(weight, species.size().avg(), player.getScoreboardName(),
				TrophyFish.day(player.level())));
		Component given = trophy.getHoverName();
		player.getInventory().placeItemBackInInventory(trophy, Prediction.SERVER_ONLY);
		command.getSource().sendSuccess(() -> Component.translatable("commands.mymod.fishing.give_trophy", given, FishSize.format(weight)),
				false);
		return 1;
	}

	private static int records(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
		CommandSourceStack source = command.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Map<String, FishingRecords.Best> bests = FishingRecords.of(player).bests();
		if (bests.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.records.none").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.records.header", bests.size()).withStyle(ChatFormatting.AQUA),
				false);
		List<Map.Entry<String, FishingRecords.Best>> sorted = new ArrayList<>(bests.entrySet());
		sorted.sort(Map.Entry.comparingByKey());
		for (Map.Entry<String, FishingRecords.Best> entry : sorted) {
			FishingSpecies species = FishingSpecies.byName(entry.getKey());
			Component name = species != null ? species.stack().getHoverName() : Component.literal(entry.getKey());
			FishingRecords.Best best = entry.getValue();
			boolean trophy = species != null && species.size() != null && species.size().isTrophy(best.weight());
			String record = species != null && species.size() != null ? FishSize.format(species.size().max()) : "?";
			source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.records.line", name, FishSize.format(best.weight()),
					best.day(), record).withStyle(trophy ? TrophyFish.COLOR : ChatFormatting.GRAY), false);
		}
		return bests.size();
	}

	private static int test(CommandContext<CommandSourceStack> command, FishBehavior behavior) throws CommandSyntaxException {
		ServerPlayer player = command.getSource().getPlayerOrException();
		int difficulty = IntegerArgumentType.getInteger(command, "difficulty");
		FightParams params = FishingFights.startTest(player, difficulty, behavior);
		command.getSource().sendSuccess(() -> Component.translatable("commands.mymod.fishing.test.started",
				Component.translatable("hud.mymod.fishing.game." + behavior.game().name().toLowerCase(Locale.ROOT)), behavior.id(), difficulty),
				false);
		reportStrength(command.getSource(), FightStrength.AVERAGE, params);
		return 1;
	}

	private static int testSpecies(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
		ServerPlayer player = command.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(command, "species");
		FishingSpecies species = FishingSpecies.byName(name);
		if (species == null) {
			command.getSource().sendFailure(Component.translatable("commands.mymod.fishing.bad_species", name));
			return 0;
		}
		float weight = FloatArgumentType.getFloat(command, "lb");
		if (species.size() != null) {
			weight = FishSize.round(species.size().clamp(weight));
		}
		FightParams params = FishingFights.startTest(player, species, weight);
		boolean trophy = species.size() != null && species.size().isTrophy(weight);
		Component what = species.size() != null ? TrophyFish.describe(species.stack(), weight) : species.stack().getHoverName();
		command.getSource().sendSuccess(() -> Component.translatable("commands.mymod.fishing.test.species", what,
				Component.translatable("hud.mymod.fishing.game." + params.game().name().toLowerCase(Locale.ROOT)), species.behavior().id(),
				params.difficulty(), trophy ? Component.translatable("commands.mymod.fishing.test.trophy") : Component.empty()), false);
		reportStrength(command.getSource(), FightStrength.of(species, weight), params);
		return 1;
	}

	/** "Fight strength 0.62 (weight 85%, rarity 31%, size 58%): 1.69x as long; at least 4.3 s, at most 31.0 s." */
	private static void reportStrength(CommandSourceStack source, FightStrength strength, FightParams params) {
		source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.test.strength",
				String.format(Locale.ROOT, "%.2f", strength.value()), Math.round(strength.percentile() * 100.0f),
				Math.round(strength.rarity() * 100.0f), Math.round(strength.size() * 100.0f),
				String.format(Locale.ROOT, "%.2f", strength.length()), String.format(Locale.ROOT, "%.1f", params.minTicks() / 20.0f),
				String.format(Locale.ROOT, "%.1f", params.maxTicks() / 20.0f),
				params.stamina() ? Component.translatable("commands.mymod.fishing.test.stamina") : Component.empty())
				.withStyle(ChatFormatting.GRAY), false);
	}

	private static int species(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
		CommandSourceStack source = command.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		ItemStack rod = player.getMainHandItem().getItem() instanceof RodItem ? player.getMainHandItem() : player.getOffhandItem();
		BlockPos pos = player.blockPosition();
		RodStats stats = RodStats.of(rod, level, pos);
		boolean lava = level.getFluidState(pos).is(FluidTags.LAVA) || level.getFluidState(pos.below()).is(FluidTags.LAVA);
		List<FishingSpecies> eligible = FishingSpecies.eligible(new FishingSpecies.Where(level, pos, lava, stats.tier()), FishingSpecies.all());
		source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.species.header", eligible.size(),
				FishingSpecies.all().size(), stats.tier(), lava ? "lava" : "water"), false);
		if (eligible.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.species.none").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		float bias = stats.rarityBias(level.isDarkOutside(), Mth.floor(player.getLuck()));
		double total = eligible.stream().mapToDouble(species -> species.biasedWeight(bias) * stats.attraction(species, lava)).sum();
		for (FishingSpecies species : eligible) {
			double chance = 100.0 * species.biasedWeight(bias) * stats.attraction(species, lava) / total;
			source.sendSuccess(() -> Component.translatable("commands.mymod.fishing.species.line", species.stack().getHoverName(),
					String.format(Locale.ROOT, "%.1f", chance), species.weight(), FishingFights.effectiveDifficulty(species, stats),
					species.behavior().id(), species.id().toString()).withStyle(ChatFormatting.GRAY), false);
		}
		return eligible.size();
	}
}
