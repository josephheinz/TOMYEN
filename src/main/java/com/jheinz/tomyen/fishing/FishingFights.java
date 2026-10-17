package com.jheinz.tomyen.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.fishing.FishingNetworking.FightEnd;
import com.jheinz.tomyen.fishing.FishingNetworking.FightResult;
import com.jheinz.tomyen.fishing.FishingNetworking.FightStart;
import com.jheinz.tomyen.fishing.FishingNetworking.Outcome;
import com.jheinz.tomyen.hag.HagsCurse;
import com.jheinz.tomyen.uranium.Radiation;

/**
 * The server's side of a fight: it rolls the species, starts the minigame on the angler's client with
 * {@link FightParams}, and rules on the result the client sends back. A catch only counts if the fight is still the
 * one on, it lasted at least as long as a perfect player could manage ({@link FightParams#minTicks}), and the bobber
 * is still out in the water (or lava). Too slow (past {@link FightParams#maxTicks}) and the fish gets away by itself.
 *
 * <p>Rewards: the species' item (count rolled from its file), vanilla's 1-6 xp plus 2 per difficulty, and for a
 * perfect fight (no misses, or the needle kept in the green) 3 more xp per difficulty and a coin flip for one extra fish.
 *
 * <p>Weight: a species with a size has its fish's weight rolled when it bites ({@link FishSize#roll}, leaning heavier
 * with the rod's tier, bait, Luck of the Sea and luck), so a heavy fish can fight harder ({@link FightParams}). Landed
 * with a perfect fight it weighs in {@value #PERFECT_BONUS_PERCENT}% heavier (a fish brought in fresh and unharmed). The
 * weight goes on the action bar; a trophy ({@link FishSize#isTrophy}) comes out as a {@link TrophyFish}, the rest of
 * the catch as plain stackable fish. Beating the angler's heaviest of the species ({@link FishingRecords}) is
 * celebrated. One bait is eaten per fish landed.
 */
public final class FishingFights {
	/** Leeway on top of maxTicks for the result to arrive before the server calls it an escape. */
	private static final int TIMEOUT_GRACE = 60;
	private static final int SNAP_DAMAGE = 3;
	private static final int PERFECT_BONUS_PERCENT = 5;

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
	private static int nextId = 1;

	/** {@code weight}: the fish's rolled weight in lb, or 0 if its species isn't weighed. */
	private record Fight(ServerPlayer player, RodHook hook, FishingSpecies species, int count, FightParams params, int start, float weight) {
		boolean test() {
			return hook == null;
		}
	}

	/**
	 * A fish too big to land with a rod. Won on the line, it comes up out of the water as a creature instead of flying to the
	 * angler, and the fight goes on with weapons (the Giant Catfish: see GiantCatfishes).
	 */
	@FunctionalInterface
	public interface Surfacing {
		/** Server side, the fight just won: whether {@code species}' fish came up as a creature (then it isn't landed). */
		boolean surface(ServerLevel level, ServerPlayer player, RodHook hook, FishingSpecies species, float weight);
	}

	private static final List<Surfacing> SURFACING = new ArrayList<>();

	private FishingFights() {
	}

	public static void surfacing(Surfacing surfacing) {
		SURFACING.add(surfacing);
	}

	/** The species of fish swimming in to {@code hook} now, or null if no species file allows one here. */
	public static FishingSpecies roll(RodHook hook, ServerPlayer player) {
		ServerLevel level = (ServerLevel) hook.level();
		RodStats stats = hook.stats();
		List<FishingSpecies> eligible = FishingSpecies.eligible(
				new FishingSpecies.Where(level, hook.blockPosition(), hook.inLava(), stats.tier()), FishingSpecies.all());
		float bias = stats.rarityBias(level.isDarkOutside(), hook.luck() + Mth.floor(player.getLuck()));
		boolean lava = hook.inLava();
		return FishingSpecies.roll(eligible, bias, species -> stats.attraction(species, lava), hook.rng());
	}

	/** The species' difficulty on this rod: a silver hook takes cursed catches down a step. */
	public static int effectiveDifficulty(FishingSpecies species, RodStats stats) {
		int difficulty = species.difficulty();
		if (stats.mods().cursedBonus() && species.stack().is(Fishing.CURSED_CATCHES)) {
			difficulty--;
		}
		return Mth.clamp(difficulty, 1, 5);
	}

	public static boolean fighting(ServerPlayer player) {
		return FIGHTS.containsKey(player.getUUID());
	}

	public static void start(ServerPlayer player, RodHook hook, FishingSpecies species, int count) {
		// A Hag's Curse makes every fight a step harder and the rod's numbers worse.
		RodStats stats = HagsCurse.fishing(player, hook.stats());
		ItemStack shown = stats.mods().sonar() ? species.stack() : ItemStack.EMPTY;
		float weight = 0.0f;
		FishSize size = species.size();
		if (size != null) {
			float bonus = stats.sizeBonus(species, hook.inLava(), hook.luck(), Mth.floor(player.getLuck()));
			weight = size.roll(hook.rng(), bonus);
		}
		boolean trophy = size != null && size.isTrophy(weight);
		begin(player, hook, species, count, species.behavior(),
				Math.min(5, difficulty(species, stats, weight) + HagsCurse.fightPenalty(player)), stats, shown, weight,
				FightStrength.of(species, weight), trophy);
		hook.setFighting(true);
		hook.level().playSound(null, hook.getX(), hook.getY(), hook.getZ(), Fishing.HOOKED, SoundSource.NEUTRAL, 1.0f, 1.0f);
	}

	/** The fight's difficulty step: the species' own (a silver hook easing cursed ones), one more for a trophy. */
	public static int difficulty(FishingSpecies species, RodStats stats, float weight) {
		int difficulty = effectiveDifficulty(species, stats);
		if (species.size() != null && species.size().isTrophy(weight)) {
			difficulty = Math.min(5, difficulty + 1);
		}
		return difficulty;
	}

	/** {@code /tomyen fishing test <difficulty> [behavior]}: a fight with no fish and no bobber, on the held rod's numbers. */
	public static FightParams startTest(ServerPlayer player, int difficulty, FishBehavior behavior) {
		return begin(player, null, null, 0, behavior, difficulty, RodStats.of(heldRod(player)), ItemStack.EMPTY, 0.0f,
				FightStrength.AVERAGE, false);
	}

	/** {@code /tomyen fishing test <species> <lb>}: that fish's fight, as if it had bitten the held rod (nothing is caught). */
	public static FightParams startTest(ServerPlayer player, FishingSpecies species, float weight) {
		RodStats stats = RodStats.of(heldRod(player));
		boolean trophy = species.size() != null && species.size().isTrophy(weight);
		return begin(player, null, null, 0, species.behavior(), difficulty(species, stats, weight), stats, species.stack(), 0.0f,
				FightStrength.of(species, weight), trophy);
	}

	private static ItemStack heldRod(ServerPlayer player) {
		return player.getMainHandItem().getItem() instanceof RodItem ? player.getMainHandItem() : player.getOffhandItem();
	}

	private static FightParams begin(ServerPlayer player, RodHook hook, FishingSpecies species, int count, FishBehavior behavior,
			int difficulty, RodStats stats, ItemStack shown, float weight, FightStrength strength, boolean trophy) {
		Fight old = FIGHTS.remove(player.getUUID());
		if (old != null) {
			end(old, Outcome.CANCELLED, false);
		}
		FightParams params = FightParams.create(nextId++, behavior, difficulty, player.getRandom().nextLong(), stats, shown, strength,
				trophy);
		FIGHTS.put(player.getUUID(), new Fight(player, hook, species, count, params, player.level().getServer().getTickCount(), weight));
		ServerPlayNetworking.send(player, new FightStart(params));
		return params;
	}

	/** The client's report on how the minigame went. */
	static void receive(ServerPlayer player, FightResult result) {
		Fight fight = FIGHTS.get(player.getUUID());
		if (fight == null || fight.params().id() != result.id()) {
			return;
		}
		FIGHTS.remove(player.getUUID());
		Outcome outcome = Outcome.byId(result.outcome());
		if (outcome == Outcome.CAUGHT) {
			int elapsed = player.level().getServer().getTickCount() - fight.start();
			if (elapsed < fight.params().minTicks()) {
				MyMod.LOGGER.warn("{} landed a fish in {} ticks (at least {} needed); it got away", player.getScoreboardName(), elapsed,
						fight.params().minTicks());
				outcome = Outcome.ESCAPED;
			} else if (!fight.test() && (fight.hook().isRemoved() || !fight.hook().inFishableFluid())) {
				outcome = Outcome.ESCAPED;
			}
		} else if (outcome == Outcome.CANCELLED) {
			outcome = Outcome.ESCAPED;
		}
		end(fight, outcome, result.perfect() && outcome == Outcome.CAUGHT);
	}

	public static void tick(MinecraftServer server) {
		if (FIGHTS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (Fight fight : new ArrayList<>(FIGHTS.values())) {
			Outcome outcome = null;
			if (fight.player().isRemoved() || fight.player().hasDisconnected()) {
				FIGHTS.remove(fight.player().getUUID());
				continue;
			} else if (!fight.test() && fight.hook().isRemoved()) {
				// Reeled in some other way, put the rod away or walked off: the fish is gone.
				outcome = Outcome.ESCAPED;
			} else if (now - fight.start() > fight.params().maxTicks() + TIMEOUT_GRACE) {
				outcome = Outcome.ESCAPED;
			}
			if (outcome != null) {
				FIGHTS.remove(fight.player().getUUID());
				end(fight, outcome, false);
			}
		}
	}

	public static void disconnect(ServerPlayer player) {
		Fight fight = FIGHTS.remove(player.getUUID());
		if (fight != null && !fight.test() && !fight.hook().isRemoved()) {
			fight.hook().resetWait();
		}
	}

	private static void end(Fight fight, Outcome outcome, boolean perfect) {
		ServerPlayer player = fight.player();
		RodHook hook = fight.hook();
		ItemStack caught = ItemStack.EMPTY;
		int xp = 0;
		float weight = 0.0f;
		if (fight.test()) {
			int seconds10 = (player.level().getServer().getTickCount() - fight.start()) / 2;
			player.sendSystemMessage(Component.translatable("commands.tomyen.fishing.test.result",
					Component.translatable("hud.tomyen.fishing.outcome." + outcome.name().toLowerCase(Locale.ROOT)),
					String.format(Locale.ROOT, "%.1f", seconds10 / 10.0f), perfect ? "yes" : "no"));
		} else {
			ServerLevel level = (ServerLevel) hook.level();
			switch (outcome) {
				case CAUGHT -> {
					ItemStack spoiled = HagsCurse.spoiledCatch(level, player, hook, rodStack(player));
					if (!spoiled.isEmpty()) {
						// Cursed: it was junk on the line all along.
						caught = spoiled;
						xp = 1;
						land(level, player, hook, caught.copy(), xp);
						damageRod(player, 1);
						eatBait(player, hook);
						hook.discard();
						break;
					}
					xp = 1 + player.getRandom().nextInt(6) + 2 * fight.params().difficulty();
					if (surfaced(level, player, hook, fight, xp)) {
						caught = fight.species().stack();
						weight = fight.weight();
						break;
					}
					int count = fight.count();
					if (perfect && player.getRandom().nextBoolean()) {
						count++;
					}
					count = Math.min(count, fight.species().item().getDefaultMaxStackSize());
					caught = fight.species().stack().copyWithCount(count);
					xp = 1 + player.getRandom().nextInt(6) + 2 * fight.params().difficulty() + (perfect ? 3 * fight.params().difficulty() : 0);
					FishSize size = fight.species().size();
					if (size != null && fight.weight() > 0.0f) {
						weight = fight.weight();
						if (perfect) {
							weight = FishSize.round(size.clamp(weight * (1.0f + PERFECT_BONUS_PERCENT / 100.0f)));
						}
						if (size.isTrophy(weight)) {
							// The trophy is the fish that fought; any others that came up with it are ordinary.
							caught = TrophyFish.make(caught, new TrophyFish(weight, size.avg(), player.getScoreboardName(), TrophyFish.day(level)));
							if (count > 1) {
								toss(level, player, hook, fight.species().stack().copyWithCount(count - 1));
							}
						}
						weighIn(player, fight.species(), caught, weight, size.isTrophy(weight), TrophyFish.day(level));
					}
					land(level, player, hook, caught.copy(), xp);
					damageRod(player, 1);
					eatBait(player, hook);
					hook.discard();
				}
				case SNAPPED -> {
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), Fishing.LINE_SNAP, SoundSource.PLAYERS, 1.0f, 1.0f);
					damageRod(player, SNAP_DAMAGE);
					hook.discard();
				}
				default -> {
					if (!hook.isRemoved()) {
						level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), Fishing.ESCAPE, SoundSource.NEUTRAL, 1.0f, 1.0f);
						level.sendParticles(ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.3, hook.getZ(), 8, 0.2, 0.0, 0.2, 0.1);
						hook.resetWait();
					}
				}
			}
		}
		if (!player.hasDisconnected()) {
			ServerPlayNetworking.send(player, new FightEnd(fight.params().id(), outcome.ordinal(), caught, perfect, xp, weight));
		}
	}

	/** Whether the fish came up as a creature: the line takes the strain (the rod's worn), the bait's gone, and so is the bobber. */
	private static boolean surfaced(ServerLevel level, ServerPlayer player, RodHook hook, Fight fight, int xp) {
		for (Surfacing surfacing : SURFACING) {
			if (surfacing.surface(level, player, hook, fight.species(), fight.weight())) {
				level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5, player.getZ() + 0.5, xp));
				damageRod(player, SNAP_DAMAGE);
				eatBait(player, hook);
				hook.discard();
				return true;
			}
		}
		return false;
	}

	/** The weight on the action bar, and a fanfare for a new personal best. */
	private static void weighIn(ServerPlayer player, FishingSpecies species, ItemStack caught, float weight, boolean trophy, long day) {
		Component line = TrophyFish.describe(caught, weight);
		if (trophy) {
			line = Component.translatable("hud.tomyen.fishing.trophy", line).withStyle(TrophyFish.COLOR);
		}
		float previous = FishingRecords.submit(player, species.id().toString(), weight, day);
		if (previous > 0.0f) {
			line = Component.empty().append(line).append("  ").append(Component.translatable("hud.tomyen.fishing.personal_best",
					FishSize.format(previous)).withStyle(ChatFormatting.AQUA));
			player.sendSystemMessage(Component.translatable("chat.tomyen.fishing.personal_best", TrophyFish.speciesName(caught),
					FishSize.format(weight), FishSize.format(previous)).withStyle(ChatFormatting.AQUA));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
					0.7f, 1.3f);
		}
		player.sendOverlayMessage(line);
	}

	/** One bait eaten, and an irradiated one doses whoever's holding the rod. */
	private static void eatBait(ServerPlayer player, RodHook hook) {
		ItemStack rod = rodStack(player);
		RodBait loaded = RodBait.of(rod);
		if (!(rod.getItem() instanceof RodItem) || loaded == null) {
			return;
		}
		BaitItem bait = loaded.bait();
		RodBait.eat(rod);
		if (bait != null && bait.effect().radiation() > 0) {
			Radiation.irradiate(player, bait.effect().radiation());
		}
	}

	/** The catch flies out of the water to the angler, as vanilla's does. */
	private static void land(ServerLevel level, ServerPlayer player, RodHook hook, ItemStack caught, int xp) {
		ItemStack rod = rodStack(player);
		CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, List.of(caught));
		toss(level, player, hook, caught);
		level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5, player.getZ() + 0.5, xp));
		player.awardStat(Stats.FISH_CAUGHT, 1);
		level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), Fishing.CATCH_SPLASH, SoundSource.NEUTRAL, 1.0f, 1.0f);
		level.sendParticles(hook.inLava() ? ParticleTypes.LAVA : ParticleTypes.SPLASH, hook.getX(), hook.getY() + 0.3, hook.getZ(), 14,
				0.25, 0.1, 0.25, 0.2);
	}

	/** Throws {@code stack} from the bobber to the angler. */
	private static void toss(ServerLevel level, ServerPlayer player, RodHook hook, ItemStack stack) {
		// Out of lava it starts clear of the surface, so a catch that isn't fireproof doesn't burn before it's out.
		ItemEntity item = new ItemEntity(level, hook.getX(), hook.getY() + (hook.inLava() ? 0.9 : 0.0), hook.getZ(), stack);
		double dx = player.getX() - hook.getX();
		double dy = player.getY() - hook.getY();
		double dz = player.getZ() - hook.getZ();
		item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
		level.addFreshEntity(item);
	}

	private static ItemStack rodStack(ServerPlayer player) {
		return player.getMainHandItem().getItem() instanceof RodItem ? player.getMainHandItem() : player.getOffhandItem();
	}

	private static void damageRod(ServerPlayer player, int amount) {
		InteractionHand hand = player.getMainHandItem().getItem() instanceof RodItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		ItemStack rod = player.getItemInHand(hand);
		if (rod.getItem() instanceof RodItem) {
			rod.hurtAndBreak(amount, player, hand.asEquipmentSlot());
		}
	}
}
