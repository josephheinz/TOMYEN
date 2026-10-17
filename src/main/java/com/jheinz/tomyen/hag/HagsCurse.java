package com.jheinz.tomyen.hag;

import java.util.List;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fishing.RodStats;

/**
 * Hag's Curse, laid by a Bog Hag's hexes: {@value #CURSE_SECONDS} seconds of bad luck, and a second hex while it's on
 * deepens it to level II (and starts the clock again). Per level:
 * <ul>
 * <li><b>Fishing</b>: fish take {@value #BITE_DELAY_TICKS} ticks longer to come (any rod; FishingHookMixin); on a mod
 * rod every fight is a step harder, the green zone narrower, the fish quicker and the line and hook weaker
 * ({@link #fishing}); and {@value #JUNK_PERCENT}% of catches come up as junk, on any rod. A point less luck, too
 * ({@link HagsCurseEffect}), so treasure is rarer.</li>
 * <li><b>Mining</b>: {@value HagsCurseEffect#SLOW_MINING_PERCENT}% slower ({@link HagsCurseEffect}); an ore has a
 * {@value #ORE_PERCENT}% chance of crumbling (its drops halved, and a single drop gone to Bog Muck), and any other stone
 * or earth dug up a {@value #MUCK_PERCENT}% chance of coming up as Bog Muck.</li>
 * </ul>
 * Hexbreaker Tea lifts it (milk doesn't: see BogHags), and its Hex Ward turns new curses away.
 */
public final class HagsCurse {
	public static final int CURSE_SECONDS = 180;
	public static final int CURSE_TICKS = CURSE_SECONDS * 20;
	/** Level II at most. */
	public static final int MAX_AMPLIFIER = 1;
	/** How long Hexbreaker Tea's Hex Ward lasts. */
	public static final int WARD_TICKS = 5 * 60 * 20;
	public static final int BITE_DELAY_TICKS = 140;
	public static final int JUNK_PERCENT = 25;
	public static final int ORE_PERCENT = 30;
	public static final int MUCK_PERCENT = 6;
	private static final int CURSE_COLOR = 0x6E8C2E;
	private static final int WARD_COLOR = 0x9FE0C0;
	private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

	/** Set while Hexbreaker Tea lifts a curse: the one way (besides commands) it comes off early. */
	private static boolean lifting;

	private HagsCurse() {
	}

	/** The curse's level on this one: 0 if it isn't cursed. */
	public static int level(LivingEntity entity) {
		MobEffectInstance curse = entity.getEffect(BogHags.HAGS_CURSE);
		return curse == null ? 0 : curse.getAmplifier() + 1;
	}

	/**
	 * Lays the curse on {@code target}, or deepens one already on it. A Hex Ward turns it away instead, with a puff of
	 * pale motes. Returns whether it took.
	 */
	public static boolean curse(ServerLevel level, LivingEntity target, @Nullable Entity source) {
		double y = target.getY(0.6);
		if (target.hasEffect(BogHags.HEX_WARD)) {
			level.sendParticles(new DustParticleOptions(WARD_COLOR, 1.0f), target.getX(), y, target.getZ(), 12, 0.35, 0.5, 0.35, 0.0);
			level.playSound(null, target.getX(), y, target.getZ(), BogHags.CURSED, SoundSource.PLAYERS, 0.7f, 1.6f);
			if (target instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("hud.tomyen.hags_curse.warded").withStyle(ChatFormatting.AQUA));
			}
			return false;
		}
		int amplifier = Math.min(MAX_AMPLIFIER, level(target));
		if (!target.addEffect(new MobEffectInstance(BogHags.HAGS_CURSE, CURSE_TICKS, amplifier), source)) {
			return false;
		}
		level.sendParticles(ParticleTypes.WITCH, target.getX(), y, target.getZ(), 16, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(new DustParticleOptions(CURSE_COLOR, 1.2f), target.getX(), y, target.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
		level.playSound(null, target.getX(), y, target.getZ(), BogHags.CURSED, SoundSource.HOSTILE, 0.8f, 0.8f + amplifier * 0.15f);
		if (target instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable(amplifier > 0 ? "hud.tomyen.hags_curse.deepened" : "hud.tomyen.hags_curse.cursed")
					.withStyle(ChatFormatting.DARK_GREEN));
		}
		return true;
	}

	/** Lifts the curse off {@code entity} (Hexbreaker Tea). Returns whether there was one. */
	public static boolean lift(LivingEntity entity) {
		lifting = true;
		try {
			return entity.removeEffect(BogHags.HAGS_CURSE);
		} finally {
			lifting = false;
		}
	}

	static boolean lifting() {
		return lifting;
	}

	// --- Fishing --------------------------------------------------------------------------------------------------

	/** Extra ticks before a fish comes to this angler's bobber (any rod). */
	public static int biteDelay(@Nullable Player angler) {
		return angler == null ? 0 : level(angler) * BITE_DELAY_TICKS;
	}

	/** A mod rod's numbers for a fight, cursed: narrower zones, quicker fish, a weaker line and hook. */
	public static RodStats fishing(Player angler, RodStats stats) {
		int curse = level(angler);
		if (curse == 0) {
			return stats;
		}
		RodModifiers penalty = RodModifiers.builder().zone(-0.12f * curse).speed(0.12f * curse).line(-0.2f * curse)
				.escape(-0.15f * curse).build();
		return new RodStats(stats.tier(), stats.lava(), stats.mods().plus(penalty), stats.bait());
	}

	/** Difficulty steps a cursed angler's fights go up by. */
	public static int fightPenalty(Player angler) {
		return level(angler) > 0 ? 1 : 0;
	}

	/**
	 * A landed fish, cursed: now and then it's junk after all (vanilla's fishing junk table). Empty if the catch
	 * stands.
	 */
	public static ItemStack spoiledCatch(ServerLevel level, ServerPlayer angler, FishingHook hook, ItemStack rod) {
		if (!spoils(angler, angler.getRandom())) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> junk = junk(level, hook, rod);
		return junk.isEmpty() ? ItemStack.EMPTY : junk.getFirst();
	}

	private static boolean spoils(Player angler, RandomSource random) {
		int curse = level(angler);
		if (curse == 0 || random.nextInt(100) >= JUNK_PERCENT * curse) {
			return false;
		}
		if (angler instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("hud.tomyen.hags_curse.junk").withStyle(ChatFormatting.DARK_GREEN));
		}
		return true;
	}

	private static List<ItemStack> junk(ServerLevel level, FishingHook hook, ItemStack rod) {
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, hook.position())
				.withParameter(LootContextParams.TOOL, rod)
				.withParameter(LootContextParams.THIS_ENTITY, hook)
				.create(LootContextParamSets.FISHING);
		return level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_JUNK).getRandomItems(params);
	}

	// --- Drops ----------------------------------------------------------------------------------------------------

	/** Vanilla-style catches turned to junk, and cursed mining's crumbled ore and muck. */
	static void modifyDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (drops.isEmpty()) {
			return;
		}
		Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
		if (table.is(BuiltInLootTables.FISHING)) {
			if (entity instanceof FishingHook hook && hook.getPlayerOwner() instanceof ServerPlayer angler
					&& spoils(angler, context.getRandom())) {
				ItemStack rod = context.getOptional(LootContextParams.TOOL) instanceof ItemStack stack ? stack : ItemStack.EMPTY;
				List<ItemStack> junk = junk(context.getLevel(), hook, rod);
				if (!junk.isEmpty()) {
					drops.clear();
					drops.addAll(junk);
				}
			}
			return;
		}
		BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		if (state == null || origin == null || !(entity instanceof Player miner)) {
			return;
		}
		int curse = level(miner);
		if (curse == 0) {
			return;
		}
		RandomSource random = context.getRandom();
		if (state.is(ORES)) {
			if (random.nextInt(100) < ORE_PERCENT * curse) {
				for (int i = 0; i < drops.size(); i++) {
					ItemStack drop = drops.get(i);
					int left = drop.getCount() / 2;
					drops.set(i, left > 0 ? drop.copyWithCount(left) : new ItemStack(BogHags.BOG_MUCK));
				}
				spoil(context.getLevel(), origin, random);
			}
		} else if (!state.hasBlockEntity() && (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))
				&& random.nextInt(100) < MUCK_PERCENT * curse) {
			drops.clear();
			drops.add(new ItemStack(BogHags.BOG_MUCK));
			spoil(context.getLevel(), origin, random);
		}
	}

	/** A block's drops gone bad: a sickly puff and a squelch. */
	private static void spoil(ServerLevel level, Vec3 at, RandomSource random) {
		level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.02);
		level.sendParticles(new DustParticleOptions(CURSE_COLOR, 1.0f), at.x, at.y, at.z, 5, 0.25, 0.25, 0.25, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.7f, 0.6f + random.nextFloat() * 0.2f);
	}

	/** A consume effect ({@code tomyen:lift_hags_curse}): Hexbreaker Tea lifting the curse. */
	public record Lift() implements ConsumeEffect {
		public static final Lift INSTANCE = new Lift();
		public static final MapCodec<Lift> CODEC = MapCodec.unit(INSTANCE);
		public static final StreamCodec<RegistryFriendlyByteBuf, Lift> STREAM_CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public ConsumeEffect.Type<Lift> getType() {
			return BogHags.LIFT_HAGS_CURSE;
		}

		@Override
		public boolean apply(Level level, ItemStack stack, LivingEntity user) {
			return lift(user);
		}
	}
}
