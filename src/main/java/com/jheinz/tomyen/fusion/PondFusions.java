package com.jheinz.tomyen.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fae.Fae;

/**
 * Fusing in a fae pond. Throw two items and one Fae Dust into a fae pond's water ({@link FaePonds}): as separate
 * dropped stacks, or a pair of the same item as one stack of two, all within {@value #GATHER} blocks of the dust.
 * They're gathered up and swirl round the dust, rising and sparkling, for {@value #SWIRL_TICKS} ticks; then:
 * <ul>
 * <li>A pair that fuses at a pond: the dust is used up, and {@value #SUCCESS_PERCENT}% of the time the pair is too: a
 * burst of light and a chime, and the result floats up out of the water and drifts to the thrower. Otherwise the
 * pond hisses and spits the pair back.
 * <li>A reactor-only pair: the pond strains ("it needs more power") and spits everything back, dust and all.
 * <li>A pair that makes nothing: it fizzles and spits everything back, dust and all.
 * </ul>
 * Only one of each stack is used; the rest are spat back. Players' items are searched for every
 * {@value #SCAN_TICKS} ticks within {@value #SCAN_RANGE} blocks of each player, so ponds nobody's near cost nothing.
 *
 * <p>The swirl steers the items by their velocity each tick (no gravity changes, so nothing is left odd if the
 * chunk unloads mid-way) and keeps them out of reach of pickup until they're released.
 */
public final class PondFusions {
	private static final int SCAN_TICKS = 10;
	private static final double SCAN_RANGE = 16.0;
	private static final double GATHER = 3.0;
	private static final int SWIRL_TICKS = 70;
	private static final int RISE_TICKS = 30;
	private static final int SUCCESS_PERCENT = 70;
	private static final int HOLD = SWIRL_TICKS + RISE_TICKS + 40;
	private static final int FAE_PINK = 0xF4A8F0;

	private static final List<Ritual> RITUALS = new ArrayList<>();
	private static final Set<UUID> BUSY = new HashSet<>();
	/** Items spat back, ignored (until the game time given) so they don't go straight round again: until picked up and re-thrown, in effect. */
	private static final Map<UUID, Long> SPAT = new HashMap<>();
	private static final long SPAT_TICKS = 6000;

	private PondFusions() {
	}

	private static final class Ritual {
		final ServerLevel level;
		final Vec3 centre;
		final ItemEntity dust;
		final ItemEntity first;
		/** The same entity as first when the pair is one stack of two. */
		final ItemEntity second;
		final @Nullable UUID thrower;
		final float phase;
		int ticks;
		@Nullable ItemEntity risen;

		Ritual(ServerLevel level, Vec3 centre, ItemEntity dust, ItemEntity first, ItemEntity second, @Nullable UUID thrower) {
			this.level = level;
			this.centre = centre;
			this.dust = dust;
			this.first = first;
			this.second = second;
			this.thrower = thrower;
			this.phase = level.getRandom().nextFloat() * Mth.TWO_PI;
		}

		List<ItemEntity> parts() {
			return first == second ? List.of(dust, first) : List.of(dust, first, second);
		}
	}

	static void tick(ServerLevel level) {
		for (Iterator<Ritual> it = RITUALS.iterator(); it.hasNext();) {
			Ritual ritual = it.next();
			if (ritual.level != level) {
				continue;
			}
			if (!step(ritual)) {
				it.remove();
				for (ItemEntity part : ritual.parts()) {
					BUSY.remove(part.getUUID());
				}
				if (ritual.risen != null) {
					BUSY.remove(ritual.risen.getUUID());
				}
			}
		}
		if (level.getGameTime() % SCAN_TICKS == 0) {
			long now = level.getGameTime();
			SPAT.values().removeIf(until -> until < now || until > now + SPAT_TICKS);
			for (ServerPlayer player : level.players()) {
				scan(level, player);
			}
		}
	}

	/** On shutdown: let everything go, so nothing is saved swirling. */
	static void releaseAll() {
		for (Ritual ritual : RITUALS) {
			for (ItemEntity part : ritual.parts()) {
				if (part.isAlive()) {
					part.setPickUpDelay(10);
				}
			}
			if (ritual.risen != null && ritual.risen.isAlive()) {
				ritual.risen.setPickUpDelay(10);
			}
		}
		RITUALS.clear();
		BUSY.clear();
		SPAT.clear();
	}

	// ------------------------------------------------------------------------------------------------ starting

	private static void scan(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(SCAN_RANGE, 8.0, SCAN_RANGE);
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !BUSY.contains(e.getUUID()) && !SPAT.containsKey(e.getUUID()) && wet(e));
		for (ItemEntity dust : items) {
			if (!dust.getItem().is(Fae.FAE_DUST) || BUSY.contains(dust.getUUID())) {
				continue;
			}
			List<ItemEntity> near = new ArrayList<>();
			for (ItemEntity other : items) {
				if (other != dust && !other.getItem().is(Fae.FAE_DUST) && !BUSY.contains(other.getUUID())
						&& other.distanceToSqr(dust) < GATHER * GATHER) {
					near.add(other);
				}
			}
			ItemEntity[] pair = choosePair(near);
			if (pair == null) {
				continue;
			}
			BlockPos pos = dust.blockPosition();
			BlockPos water = FaePonds.isPondWater(level, pos) ? pos : FaePonds.isPondWater(level, pos.below()) ? pos.below() : null;
			if (water == null) {
				continue;
			}
			start(level, dust, pair[0], pair[1], water);
		}
	}

	/** Two items to fuse from what's near the dust: a pair that fuses if there is one, else any pair (to fizzle). */
	private static ItemEntity @Nullable [] choosePair(List<ItemEntity> near) {
		ItemEntity[] fallback = null;
		for (int i = 0; i < near.size(); i++) {
			ItemEntity a = near.get(i);
			if (a.getItem().getCount() >= 2) {
				if (FusionRecipe.find(a.getItem(), a.getItem()) != null) {
					return new ItemEntity[] {a, a};
				}
			}
			for (int j = i + 1; j < near.size(); j++) {
				ItemEntity b = near.get(j);
				if (FusionRecipe.find(a.getItem(), b.getItem()) != null) {
					return new ItemEntity[] {a, b};
				}
				if (fallback == null) {
					fallback = new ItemEntity[] {a, b};
				}
			}
		}
		return fallback;
	}

	private static boolean wet(ItemEntity item) {
		if (item.isInWater()) {
			return true;
		}
		// Bobbing just clear of the surface.
		BlockPos below = item.blockPosition().below();
		return !item.onGround() && item.level().getFluidState(below).is(FluidTags.WATER) && item.getY() - item.blockPosition().getY() < 0.6;
	}

	private static void start(ServerLevel level, ItemEntity dust, ItemEntity first, ItemEntity second, BlockPos water) {
		Vec3 centre = new Vec3(dust.getX(), water.getY() + 1.0, dust.getZ());
		Entity owner = dust.getOwner();
		UUID thrower = owner != null ? owner.getUUID() : null;
		Ritual ritual = new Ritual(level, centre, dust, first, second, thrower);
		for (ItemEntity part : ritual.parts()) {
			BUSY.add(part.getUUID());
			part.setPickUpDelay(HOLD);
		}
		RITUALS.add(ritual);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.NEUTRAL, 0.8f, 1.4f);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0f, 1.2f);
		level.sendParticles(ParticleTypes.SPLASH, centre.x, centre.y, centre.z, 20, 0.4, 0.05, 0.4, 0.1);
	}

	// ------------------------------------------------------------------------------------------------ swirling

	/** One tick of a ritual; false once it's over. */
	private static boolean step(Ritual ritual) {
		ServerLevel level = ritual.level;
		Vec3 c = ritual.centre;
		if (ritual.risen != null) {
			return rise(ritual);
		}
		for (ItemEntity part : ritual.parts()) {
			if (!part.isAlive() || part.getItem().isEmpty()) {
				release(ritual, false);
				return false;
			}
		}
		int t = ++ritual.ticks;
		float progress = (float) t / SWIRL_TICKS;
		double radius = 0.75 - 0.15 * progress;
		double height = 0.15 + 0.85 * progress;
		float angle = ritual.phase + t * (0.18f + 0.25f * progress);
		steer(ritual.dust, c.add(0.0, height + 0.25 + 0.08 * Mth.sin(t * 0.3f), 0.0));
		steer(ritual.first, c.add(Mth.cos(angle) * radius, height, Mth.sin(angle) * radius));
		if (ritual.second != ritual.first) {
			steer(ritual.second, c.add(-Mth.cos(angle) * radius, height, -Mth.sin(angle) * radius));
		}
		if (t % 2 == 0) {
			for (ItemEntity part : ritual.parts()) {
				level.sendParticles(Fae.SPARKLE, part.getX(), part.getY() + 0.2, part.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
			}
			level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.05, c.z, 3, radius, 0.02, radius, 0.02);
		}
		if (t % 20 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6f, 0.8f + progress);
		}
		if (t < SWIRL_TICKS) {
			return true;
		}
		return resolve(ritual);
	}

	/** Pulls an item toward a point through its velocity (gravity, or the water's lift, is cancelled out). */
	private static void steer(ItemEntity item, Vec3 target) {
		Vec3 to = target.subtract(item.position());
		double lift = item.isInWater() ? -5.0e-4 : 0.04;
		item.setDeltaMovement(to.x * 0.45, to.y * 0.45 + lift, to.z * 0.45);
		item.needsSync = true;
	}

	// ------------------------------------------------------------------------------------------------ outcomes

	private static boolean resolve(Ritual ritual) {
		ServerLevel level = ritual.level;
		Vec3 c = ritual.centre.add(0.0, 1.0, 0.0);
		ItemStack a = ritual.first.getItem();
		ItemStack b = ritual.second.getItem();
		FusionRecipe recipe = ritual.first == ritual.second ? (a.getCount() >= 2 ? FusionRecipe.find(a, a) : null) : FusionRecipe.find(a, b);
		if (recipe == null) {
			level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 16, 0.3, 0.2, 0.3, 0.02);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7f, 1.4f);
			tell(ritual, Component.translatable("fusion.tomyen.pond.fizzle"));
			release(ritual, true);
			return false;
		}
		if (!recipe.station().pond()) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.4, 0.3, 0.4, 0.2);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.9f, 1.3f);
			tell(ritual, Component.translatable("fusion.tomyen.pond.needs_power"));
			release(ritual, true);
			return false;
		}
		// The dust is spent whether or not the pond takes.
		ritual.dust.getItem().shrink(1);
		if (ritual.dust.getItem().isEmpty()) {
			ritual.dust.discard();
		}
		if (level.getRandom().nextInt(100) >= SUCCESS_PERCENT) {
			level.sendParticles(ParticleTypes.SPLASH, c.x, c.y - 0.5, c.z, 30, 0.4, 0.1, 0.4, 0.2);
			level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.02);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.NEUTRAL, 0.8f, 1.2f);
			tell(ritual, Component.translatable("fusion.tomyen.pond.failed"));
			release(ritual, true);
			return false;
		}
		if (ritual.first == ritual.second) {
			a.shrink(2);
		} else {
			a.shrink(1);
			b.shrink(1);
		}
		for (ItemEntity part : List.of(ritual.first, ritual.second)) {
			if (part.getItem().isEmpty()) {
				part.discard();
			}
		}
		// A burst of light and a chime.
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.05, 0.05, 0.05, 0.18);
		level.sendParticles(Fae.SPARKLE, c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.06);
		level.sendParticles(ParticleTypes.SPLASH, c.x, c.y - 0.8, c.z, 40, 0.6, 0.1, 0.6, 0.3);
		level.playSound(null, c.x, c.y, c.z, Fae.RING_CHIME, SoundSource.NEUTRAL, 1.2f, 1.2f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5f, 1.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6f, 1.6f);
		ItemEntity risen = new ItemEntity(level, c.x, ritual.centre.y + 0.1, c.z, recipe.resultStack(), 0.0, 0.1, 0.0);
		risen.setPickUpDelay(RISE_TICKS + 10);
		level.addFreshEntity(risen);
		BUSY.add(risen.getUUID());
		ritual.risen = risen;
		ritual.ticks = 0;
		release(ritual, true);
		return true;
	}

	/** The result floating up out of the water, sparkling, then drifting to whoever threw the dust. */
	private static boolean rise(Ritual ritual) {
		ItemEntity risen = ritual.risen;
		if (!risen.isAlive()) {
			return false;
		}
		int t = ++ritual.ticks;
		Vec3 c = ritual.centre;
		steer(risen, c.add(0.0, 0.3 + 1.4 * Math.min(1.0, t / (double) RISE_TICKS), 0.0));
		if (t % 2 == 0) {
			ritual.level.sendParticles(Fae.SPARKLE, risen.getX(), risen.getY() + 0.2, risen.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
			ritual.level.sendParticles(ParticleTypes.END_ROD, risen.getX(), risen.getY() + 0.2, risen.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
		}
		if (t < RISE_TICKS) {
			return true;
		}
		risen.setPickUpDelay(0);
		toss(ritual, risen, 0.6, 0.25);
		return false;
	}

	/** Lets go of whatever's left and throws it back toward the thrower. */
	private static void release(Ritual ritual, boolean spit) {
		for (ItemEntity part : ritual.parts()) {
			BUSY.remove(part.getUUID());
			if (!part.isAlive() || part.getItem().isEmpty()) {
				continue;
			}
			part.setPickUpDelay(spit ? 20 : 10);
			SPAT.put(part.getUUID(), ritual.level.getGameTime() + SPAT_TICKS);
			if (spit) {
				toss(ritual, part, 0.6, 0.4);
			}
		}
	}

	/**
	 * Lobs an item so it lands at the thrower's feet: lifted clear of the water first (water drag would swallow the
	 * throw), then given the horizontal speed that an item's air drag and gravity carry exactly that far.
	 */
	private static void toss(Ritual ritual, ItemEntity item, double maxSpeed, double up) {
		if (item.isInWater()) {
			item.setPos(item.getX(), ritual.centre.y + 0.4, item.getZ());
		}
		Player to = thrower(ritual);
		Vec3 v;
		if (to != null) {
			Vec3 d = to.position().subtract(item.position()).multiply(1.0, 0.0, 1.0);
			double reach = 0.0;
			double vy = up;
			double y = item.getY();
			for (int t = 0; t < 80 && (vy > 0.0 || y > to.getY() + 0.3); t++) {
				reach += Math.pow(0.98, t);
				y += vy;
				vy = (vy - 0.04) * 0.98;
			}
			v = d.lengthSqr() < 1.0e-4 || reach <= 0.0 ? Vec3.ZERO : d.scale(1.0 / reach);
			if (v.length() > maxSpeed) {
				v = v.normalize().scale(maxSpeed);
			}
		} else {
			float angle = ritual.level.getRandom().nextFloat() * Mth.TWO_PI;
			v = new Vec3(Mth.cos(angle) * 0.15, 0.0, Mth.sin(angle) * 0.15);
		}
		item.setDeltaMovement(v.x, up, v.z);
		item.needsSync = true;
	}

	private static @Nullable Player thrower(Ritual ritual) {
		if (ritual.thrower != null) {
			Player player = ritual.level.getPlayerByUUID(ritual.thrower);
			if (player != null && player.distanceToSqr(ritual.centre) < 32.0 * 32.0) {
				return player;
			}
		}
		return ritual.level.getNearestPlayer(ritual.centre.x, ritual.centre.y, ritual.centre.z, 16.0, false);
	}

	private static void tell(Ritual ritual, Component message) {
		Player player = thrower(ritual);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		}
	}
}
