package com.jheinz.mymod.neuralink;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

import com.jheinz.mymod.mixin.MobAccessor;

/**
 * Tells a Mind Reader wearer what the mobs around them are thinking: twice a second, a line for each of the
 * nearest {@link #MAX_MOBS} mobs within {@link #RANGE} blocks, worked out from what its AI is doing right now
 * (its target, its brain's activity, or its running goals). Villagers have opinions.
 */
public final class MindReader {
	public static final double RANGE = 16.0;
	private static final int MAX_MOBS = 12;
	private static final int INTERVAL = 10;
	/** How many lines each villager activity has to pick from. */
	private static final int VILLAGER_VARIANTS = 4;
	/** A villager changes its mind about this often. */
	private static final int VILLAGER_MOOD_TICKS = 160;

	/** Wearers whose last batch had something in it, so an empty one is sent once to clear their screen. */
	private static final Set<UUID> SHOWING = new HashSet<>();

	private MindReader() {
	}

	static void tick(ServerPlayer player) {
		if ((player.tickCount + 3) % INTERVAL != 0) {
			return;
		}
		if (!NeuralinkItem.isActive(player, NeuralinkModule.MIND_READER)) {
			if (SHOWING.remove(player.getUUID())) {
				ServerPlayNetworking.send(player, new NeuralNetworking.Thoughts(List.of()));
			}
			return;
		}
		List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
				mob -> mob.isAlive() && mob.distanceToSqr(player) <= RANGE * RANGE);
		mobs.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
		List<NeuralNetworking.Thought> thoughts = new ArrayList<>();
		long time = player.level().getGameTime();
		for (Mob mob : mobs.subList(0, Math.min(MAX_MOBS, mobs.size()))) {
			thoughts.add(new NeuralNetworking.Thought(mob.getId(), thought(mob, player, time)));
		}
		if (!thoughts.isEmpty() || SHOWING.contains(player.getUUID())) {
			ServerPlayNetworking.send(player, new NeuralNetworking.Thoughts(thoughts));
		}
		if (thoughts.isEmpty()) {
			SHOWING.remove(player.getUUID());
		} else {
			SHOWING.add(player.getUUID());
		}
	}

	private static Component line(String key, Object... args) {
		return Component.translatable("thought.mymod." + key, args);
	}

	/** What the mob is up to, from the reader's point of view. */
	static Component thought(Mob mob, ServerPlayer reader, long time) {
		ServerPlayer owner = NeuralHijack.ownerOf(mob);
		if (owner != null) {
			return owner == reader ? line("serving_you") : line("serving", owner.getDisplayName());
		}
		if (mob.isSleeping()) {
			return line("sleeping");
		}
		if (mob instanceof Villager villager) {
			return villager(villager, time);
		}
		LivingEntity target = mob.getTarget();
		if (target != null) {
			return target == reader ? line("hunting_you") : line("hunting", target.getDisplayName());
		}
		if (mob instanceof PathfinderMob pathfinder && pathfinder.isPanicking()) {
			return line("panicking");
		}
		Optional<Activity> activity = mob.getBrain().getActiveNonCoreActivity();
		if (activity.isPresent() && activity.get() != Activity.IDLE) {
			String name = activity.get().getName();
			return Component.translatableWithFallback("thought.mymod.activity." + name, "Busy: " + name.replace('_', ' ') + ".");
		}
		Goal goal = mostTelling(mob);
		if (goal != null) {
			return goalLine(goal, mob, reader);
		}
		return line("idle");
	}

	/** The running goal that says most about the mob, by the order of {@link #rank}; null if none counts. */
	private static Goal mostTelling(Mob mob) {
		Goal best = null;
		int bestRank = Integer.MAX_VALUE;
		for (WrappedGoal wrapped : ((MobAccessor) mob).mymod$getGoalSelector().getAvailableGoals()) {
			if (!wrapped.isRunning()) {
				continue;
			}
			int rank = rank(wrapped.getGoal());
			if (rank < bestRank) {
				best = wrapped.getGoal();
				bestRank = rank;
			}
		}
		return best;
	}

	/** Lower is more interesting; MAX_VALUE for goals that say nothing. */
	private static int rank(Goal goal) {
		if (goal instanceof SwellGoal) return 0;
		if (goal instanceof AvoidEntityGoal<?>) return 1;
		if (goal instanceof FleeSunGoal) return 2;
		if (goal instanceof BreakDoorGoal) return 3;
		if (goal instanceof BreedGoal) return 4;
		if (goal instanceof TemptGoal) return 5;
		if (goal instanceof EatBlockGoal) return 6;
		if (goal instanceof FollowOwnerGoal) return 7;
		if (goal instanceof SitWhenOrderedToGoal) return 8;
		if (goal instanceof FollowParentGoal) return 9;
		if (goal instanceof MoveToBlockGoal) return 10;
		if (goal instanceof FloatGoal) return 11;
		if (goal instanceof RandomSwimmingGoal) return 12;
		if (goal instanceof RandomStrollGoal) return 13;
		if (goal instanceof LookAtPlayerGoal) return 14;
		if (goal instanceof RandomLookAroundGoal) return 15;
		return Integer.MAX_VALUE;
	}

	private static Component goalLine(Goal goal, Mob mob, ServerPlayer reader) {
		if (goal instanceof SwellGoal) return line("about_to_explode");
		if (goal instanceof AvoidEntityGoal<?>) return line("fleeing");
		if (goal instanceof FleeSunGoal) return line("avoiding_sun");
		if (goal instanceof BreakDoorGoal) return line("breaking_in");
		if (goal instanceof BreedGoal) return line("in_love");
		if (goal instanceof TemptGoal) return line("tempted");
		if (goal instanceof EatBlockGoal) return line("grazing");
		if (goal instanceof FollowOwnerGoal) return line("following_owner");
		if (goal instanceof SitWhenOrderedToGoal) return line("sitting");
		if (goal instanceof FollowParentGoal) return line("following_parent");
		if (goal instanceof MoveToBlockGoal) return line("looking_for_something");
		if (goal instanceof FloatGoal) return line("swimming_for_it");
		if (goal instanceof RandomSwimmingGoal) return line("swimming");
		if (goal instanceof RandomStrollGoal) return line("wandering");
		if (goal instanceof LookAtPlayerGoal) {
			return mob.getLookControl().isLookingAtTarget() && mob.hasLineOfSight(reader) ? line("staring_at_you") : line("people_watching");
		}
		return line("daydreaming");
	}

	/** Villagers think out loud, a line picked by what they're doing and changed every so often. */
	private static Component villager(Villager villager, long time) {
		if (villager.isPanicking()) {
			return line("villager.panic." + variant(villager, time));
		}
		String activity = villager.getBrain().getActiveNonCoreActivity().map(Activity::getName).orElse("idle").toLowerCase(Locale.ROOT);
		String kind = switch (activity) {
			case "work", "rest", "meet", "play", "panic", "hide" -> activity;
			case "raid", "pre_raid" -> "raid";
			default -> "idle";
		};
		if (kind.equals("work") && villager.getVillagerData().profession().unwrapKey()
				.map(key -> key.identifier().getPath().equals("nitwit")).orElse(false)) {
			return line("villager.nitwit");
		}
		return line("villager." + kind + "." + variant(villager, time));
	}

	private static int variant(Mob mob, long time) {
		return (int) Math.floorMod(mob.getId() * 7L + time / VILLAGER_MOOD_TICKS, VILLAGER_VARIANTS);
	}
}
