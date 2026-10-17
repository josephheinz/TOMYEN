package com.jheinz.tomyen.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.fae.Fae;
import com.jheinz.tomyen.fae.Fairy;

/**
 * Hints, not a compendium: players find fusions by experimenting, nudged three ways.
 * <ul>
 * <li>Villagers now and then pass on a rumour, a fusion's riddle: one time in {@value #TRADE_ODDS} when you open
 * their trades, and now and then unprompted when you stand near one.
 * <li>Fairies whisper riddles too, when you're near one.
 * <li>Near a fae pond or a Fusion Reactor, an item you carry whose fusion partner you also carry gets a shimmer line
 * in its tooltip ("It hums near something you carry..."), never naming the partner. The server works out which items
 * every {@value #SHIMMER_TICKS} ticks and sends the list ({@link Fusions.Shimmer}); the client adds the line.
 * </ul>
 * A riddle is only ever told for a fusion that's loaded (so every one leads somewhere): half the time one the player
 * carries half of. One hint a player per {@value #HINT_COOLDOWN} ticks at most.
 */
public final class FusionHints {
	private static final int TRADE_ODDS = 4;
	private static final int CHECK_TICKS = 20;
	private static final int AMBIENT_VILLAGER_ODDS = 45;
	private static final int FAIRY_ODDS = 18;
	private static final int HINT_COOLDOWN = 1200;
	private static final int SHIMMER_TICKS = 40;
	private static final int REACTOR_REACH = 6;

	private static final Map<UUID, Long> NEXT_HINT = new HashMap<>();
	/** What each player's client was last told (weakly held: a rejoin or respawn is a new player object, told afresh). */
	private static final Map<ServerPlayer, Set<Identifier>> SHIMMERING = new WeakHashMap<>();

	private FusionHints() {
	}

	static void initialize() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && entity instanceof Villager villager
					&& !villager.isBaby() && server.getRandom().nextInt(TRADE_ODDS) == 0) {
				villagerRumour(serverPlayer, villager);
			}
			return InteractionResult.PASS;
		});
	}

	static void tick(ServerLevel level) {
		long time = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if ((time + player.getId()) % CHECK_TICKS == 0 && !player.isSpectator()) {
				ambient(level, player);
			}
			if ((time + player.getId()) % SHIMMER_TICKS == 0) {
				shimmer(level, player);
			}
		}
	}

	private static boolean ready(ServerPlayer player) {
		return NEXT_HINT.getOrDefault(player.getUUID(), 0L) <= player.level().getGameTime();
	}

	private static void ambient(ServerLevel level, ServerPlayer player) {
		if (!ready(player)) {
			return;
		}
		if (level.getRandom().nextInt(FAIRY_ODDS) == 0) {
			List<Fairy> fairies = level.getEntitiesOfClass(Fairy.class, player.getBoundingBox().inflate(8.0), LivingEntity::isAlive);
			if (!fairies.isEmpty()) {
				fairyWhisper(player, fairies.get(0));
				return;
			}
		}
		if (level.getRandom().nextInt(AMBIENT_VILLAGER_ODDS) == 0) {
			List<Villager> villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(6.0),
					villager -> villager.isAlive() && !villager.isBaby() && !villager.isSleeping());
			if (!villagers.isEmpty()) {
				villagerRumour(player, villagers.get(0));
			}
		}
	}

	private static Component riddle(ServerPlayer player) {
		FusionRecipe recipe = FusionRecipe.pickForHint(player.getInventory(), player.getRandom());
		return recipe == null ? null : Component.translatable(recipe.riddle());
	}

	private static void villagerRumour(ServerPlayer player, Villager villager) {
		if (!ready(player)) {
			return;
		}
		Component riddle = riddle(player);
		if (riddle == null) {
			return;
		}
		NEXT_HINT.put(player.getUUID(), player.level().getGameTime() + HINT_COOLDOWN);
		player.sendSystemMessage(Component.translatable("fusion.tomyen.hint.villager", villager.getDisplayName(),
				riddle.copy().withStyle(ChatFormatting.ITALIC)).withStyle(ChatFormatting.GRAY));
		villager.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0f, villager.getVoicePitch());
	}

	private static void fairyWhisper(ServerPlayer player, Fairy fairy) {
		Component riddle = riddle(player);
		if (riddle == null) {
			return;
		}
		NEXT_HINT.put(player.getUUID(), player.level().getGameTime() + HINT_COOLDOWN);
		player.sendSystemMessage(Component.translatable("fusion.tomyen.hint.fairy", riddle).withStyle(ChatFormatting.LIGHT_PURPLE,
				ChatFormatting.ITALIC));
		player.level().playSound(null, fairy.getX(), fairy.getY(), fairy.getZ(), Fae.FAIRY_GIGGLE, SoundSource.NEUTRAL, 0.6f, 1.4f);
	}

	// ------------------------------------------------------------------------------------------------ shimmer

	private static void shimmer(ServerLevel level, ServerPlayer player) {
		boolean reactor = nearReactor(level, player.blockPosition());
		boolean pond = reactor || FaePonds.nearPond(level, player.blockPosition());
		Set<Identifier> items = new LinkedHashSet<>();
		if (pond) {
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!stack.isEmpty() && FusionRecipe.hasPartner(stack, inventory, reactor)) {
					items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
				}
			}
		}
		Set<Identifier> last = SHIMMERING.get(player);
		if (!items.equals(last == null ? Set.of() : last)) {
			SHIMMERING.put(player, items);
			ServerPlayNetworking.send(player, new Fusions.Shimmer(new ArrayList<>(items)));
		}
	}

	private static boolean nearReactor(ServerLevel level, BlockPos pos) {
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-REACTOR_REACH, -3, -REACTOR_REACH), pos.offset(REACTOR_REACH, 3, REACTOR_REACH))) {
			if (level.getBlockState(at).is(Fusions.FUSION_REACTOR)) {
				return true;
			}
		}
		return false;
	}
}
