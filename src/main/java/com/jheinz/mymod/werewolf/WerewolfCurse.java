package com.jheinz.mymod.werewolf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.herbalism.HerbalWards;
import com.jheinz.mymod.trombone.TromboneMoods;
import com.jheinz.mymod.holy.CrucifixItem;
import com.jheinz.mymod.silver.Silver;

/**
 * The werewolf curse on villagers. Each villager is rolled once, the first time the server sees it (spawned, bred,
 * generated with its village, or loaded from before this existed): {@link #CURSE_CHANCE} of them carry the curse.
 * Villagers cured of zombification are never rolled.
 * <p>
 * By day a cursed villager looks like any other, apart from the odd low growl and, at night, a glance up at the moon.
 * A crucifix held close, or a touch of crucifix light, shows the curse up: it recoils, hissing and smoking. A Holy
 * Silver Ingot used on it lifts the curse for good (the ingot is spent), and it thinks well of whoever did it.
 * <p>
 * On a full-moon night (moon phase 0, every 8 nights) each grown cursed villager shudders and smokes for
 * {@link #TRANSFORM_TICKS} ticks, howls, and becomes a {@link Werewolf} carrying the villager whole. At sunrise the
 * werewolf turns back into that same villager. The {@code mymod:werewolves} game rule stops both new curses and
 * transformations; there are none in peaceful.
 */
public final class WerewolfCurse {
	/** The chance a villager carries the curse: villages average around eight, so about one in every two or three. */
	public static final float CURSE_CHANCE = 0.05f;
	/** How long a transformation takes. */
	public static final int TRANSFORM_TICKS = 40;
	/** Night by the clock: transformations from dusk, back at sunrise. */
	public static final int DUSK = 13000;
	public static final int SUNRISE = 23000;
	/** Once it's time, each tick a villager starts changing with this chance in one, so they don't all go at once. */
	private static final int TRANSFORM_STAGGER = 60;
	/** One in this many ticks a cursed villager growls under its breath. */
	private static final int GROWL_CHANCE = 2400;
	/** One in this many ticks of night, an awake cursed villager looks up at the moon. */
	private static final int GLANCE_CHANCE = 900;
	private static final int GLANCE_TICKS = 60;
	/** A crucifix held this close shows the curse up. */
	public static final double CRUCIFIX_REVEAL_RADIUS = 4.0;
	private static final int REVEAL_COOLDOWN = 60;
	private static final int REVEAL_COLOR = 0x5A1010;
	private static final int CURE_COLOR = 0xFFD54A;

	/** Cursed villagers the server has loaded, in every level. */
	private static final Set<Villager> LOADED = new HashSet<>();
	/** Ticks left of each transformation under way (its villager carries {@link Werewolves#TRANSFORMING} meanwhile). */
	private static final Map<Villager, Integer> TRANSFORMS = new HashMap<>();
	/** Game time each villager last recoiled from something holy. */
	private static final Map<Villager, Long> LAST_REVEALED = new HashMap<>();

	private WerewolfCurse() {
	}

	public static void initialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) {
				onLoad(level, villager);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) {
				forget(villager);
			}
		});
		// A villager cured of zombification is never rolled for the curse.
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> {
			if (previous instanceof ZombieVillager && converted instanceof Villager villager) {
				villager.setAttached(Werewolves.CURSED, false);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(WerewolfCurse::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			TRANSFORMS.clear();
			LAST_REVEALED.clear();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Villager villager)) {
				return InteractionResult.PASS;
			}
			if (villager.hasAttached(Werewolves.TRANSFORMING)) {
				// No trading with something halfway to a wolf.
				return InteractionResult.SUCCESS;
			}
			ItemStack held = player.getItemInHand(hand);
			// The client can't tell who's cursed, so it lets the click through and the server decides.
			if (!held.is(Silver.HOLY_SILVER_INGOT) || !(level instanceof ServerLevel serverLevel) || !isCursed(villager)) {
				return InteractionResult.PASS;
			}
			cure(serverLevel, villager, player);
			held.consume(1, player);
			return InteractionResult.SUCCESS;
		});
	}

	private static void onLoad(ServerLevel level, Villager villager) {
		if (!villager.hasAttached(Werewolves.CURSED) && level.getGameRules().get(Werewolves.WEREWOLVES)) {
			villager.setAttached(Werewolves.CURSED, level.getRandom().nextFloat() < CURSE_CHANCE);
		}
		if (isCursed(villager)) {
			LOADED.add(villager);
		}
	}

	private static void forget(Villager villager) {
		LOADED.remove(villager);
		LAST_REVEALED.remove(villager);
		if (TRANSFORMS.remove(villager) != null) {
			villager.removeAttached(Werewolves.TRANSFORMING);
		}
	}

	public static boolean isCursed(Entity entity) {
		return entity instanceof Villager villager && Boolean.TRUE.equals(villager.getAttached(Werewolves.CURSED));
	}

	/** Night by this level's clock (false where time stands still). */
	public static boolean isNight(ServerLevel level) {
		if (level.dimensionType().hasFixedTime()) {
			return false;
		}
		long time = level.getDefaultClockTime() % 24000L;
		return time >= DUSK && time < SUNRISE;
	}

	public static boolean isFullMoon(ServerLevel level) {
		return level.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE) == MoonPhase.FULL_MOON;
	}

	private static void tick(ServerLevel level) {
		if (LOADED.isEmpty()) {
			return;
		}
		boolean night = isNight(level);
		boolean changing = night && isFullMoon(level) && level.getGameRules().get(Werewolves.WEREWOLVES)
				&& level.getDifficulty() != Difficulty.PEACEFUL;
		for (Villager villager : List.copyOf(LOADED)) {
			if (villager.level() != level || !villager.isAlive()) {
				continue;
			}
			Integer ticks = TRANSFORMS.get(villager);
			if (ticks != null) {
				tickTransform(level, villager, ticks - 1);
				continue;
			}
			if (changing && !villager.isBaby() && level.getRandom().nextInt(TRANSFORM_STAGGER) == 0
					&& !TromboneMoods.lulled(villager) && !HerbalWards.holdsBackCurse(level, villager)) {
				startTransform(level, villager);
				continue;
			}
			tells(level, villager, night);
		}
	}

	/** The little things that give a cursed villager away, for anyone paying attention. */
	private static void tells(ServerLevel level, Villager villager, boolean night) {
		if (level.getRandom().nextInt(GROWL_CHANCE) == 0) {
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), Werewolves.GROWL, SoundSource.NEUTRAL, 0.5f, 0.85f);
		}
		if (night && !villager.isSleeping() && level.getRandom().nextInt(GLANCE_CHANCE) == 0) {
			lookAtMoon(level, villager);
		}
		if ((level.getGameTime() + villager.getId()) % 10 == 0) {
			for (Player player : level.getEntitiesOfClass(Player.class, villager.getBoundingBox().inflate(CRUCIFIX_REVEAL_RADIUS))) {
				if (!player.isSpectator() && CrucifixItem.isHeldBy(player) && player.distanceTo(villager) <= CRUCIFIX_REVEAL_RADIUS) {
					reveal(level, villager);
					break;
				}
			}
		}
	}

	/** Has it look up at where the moon is: rising in the east at dusk, overhead at midnight, setting in the west. */
	private static void lookAtMoon(ServerLevel level, Villager villager) {
		double angle = ((level.getDefaultClockTime() % 24000L) - 18000L) / 24000.0 * Mth.TWO_PI;
		Vec3 moon = villager.getEyePosition().add(-Math.sin(angle) * 12.0, Math.max(Math.cos(angle), 0.3) * 12.0, 0.0);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		villager.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(BlockPos.containing(moon)), GLANCE_TICKS);
	}

	/** Shows the curse up: the villager recoils, shaking its head, smoking and hissing. Not more than every few seconds. */
	public static void reveal(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		Long last = LAST_REVEALED.get(villager);
		if (last != null && now - last < REVEAL_COOLDOWN) {
			return;
		}
		LAST_REVEALED.put(villager, now);
		villager.setUnhappyCounter(40);
		double y = villager.getY(0.6);
		level.sendParticles(ParticleTypes.SMOKE, villager.getX(), y, villager.getZ(), 14, 0.25, 0.45, 0.25, 0.02);
		level.sendParticles(new DustParticleOptions(REVEAL_COLOR, 1.2f), villager.getX(), y, villager.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), Werewolves.HISS, SoundSource.NEUTRAL, 1.0f, 1.0f);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.5f, 0.7f);
	}

	/** Crucifix light (an orb or a beam) has touched this entity: a cursed villager recoils. */
	public static void onHolyLight(ServerLevel level, Entity entity) {
		if (entity instanceof Villager villager && isCursed(villager)) {
			reveal(level, villager);
		}
	}

	/** Lays the curse on a villager (if it isn't on it already). */
	public static void curse(Villager villager) {
		villager.setAttached(Werewolves.CURSED, true);
		if (villager.level() instanceof ServerLevel && !villager.isRemoved()) {
			LOADED.add(villager);
		}
	}

	/** Lifts the curse: a golden burst and a choir, and the villager is grateful to whoever did it (if anyone). */
	public static void cure(ServerLevel level, Villager villager, @Nullable Player curer) {
		villager.setAttached(Werewolves.CURSED, false);
		forget(villager);
		double x = villager.getX(), y = villager.getY(0.5), z = villager.getZ();
		level.sendParticles(new DustParticleOptions(CURE_COLOR, 1.4f), x, y, z, 24, 0.35, 0.6, 0.35, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, 12, 0.3, 0.5, 0.3, 0.02);
		for (int i = 0; i < 12; i++) {
			level.sendParticles(ParticleTypes.END_ROD, x + (level.getRandom().nextDouble() - 0.5) * 0.7, villager.getY() + 0.2,
					z + (level.getRandom().nextDouble() - 0.5) * 0.7, 0, 0.0, 1.0, 0.0, 0.08 + level.getRandom().nextDouble() * 0.08);
		}
		level.playSound(null, x, y, z, Silver.CONSECRATE, SoundSource.NEUTRAL, 1.2f, 1.0f);
		level.playSound(null, x, y, z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 0.8f, 1.2f);
		villager.playCelebrateSound();
		if (curer != null) {
			villager.getGossips().add(curer.getUUID(), GossipType.MAJOR_POSITIVE, 10);
			villager.getGossips().add(curer.getUUID(), GossipType.MINOR_POSITIVE, 20);
			if (curer instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("message.mymod.werewolf.cured").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Starts a cursed villager changing: it stops dead, wakes, drops any trade, and shudders and smokes. */
	public static void startTransform(ServerLevel level, Villager villager) {
		if (TRANSFORMS.containsKey(villager)) {
			return;
		}
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		villager.setTradingPlayer(null);
		TRANSFORMS.put(villager, TRANSFORM_TICKS);
		villager.setAttached(Werewolves.TRANSFORMING, TRANSFORM_TICKS);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), Werewolves.GROWL, SoundSource.HOSTILE, 1.5f, 0.8f);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.WOLF_SHAKE, SoundSource.HOSTILE, 1.0f, 0.6f);
	}

	private static void tickTransform(ServerLevel level, Villager villager, int ticks) {
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		Vec3 motion = villager.getDeltaMovement();
		villager.setDeltaMovement(0.0, motion.y, 0.0);
		if (ticks % 3 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, villager.getX(), villager.getY(0.6), villager.getZ(), 4, 0.25, 0.5, 0.25, 0.01);
		}
		if (ticks > 0) {
			TRANSFORMS.put(villager, ticks);
		} else {
			transform(level, villager);
		}
	}

	/** Turns a cursed villager into a werewolf there and then, carrying it whole; null if it couldn't. */
	public static @Nullable Werewolf transform(ServerLevel level, Villager villager) {
		forget(villager);
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		villager.setTradingPlayer(null);
		CompoundTag saved = save(level, villager);
		Werewolf werewolf = villager.convertTo(Werewolves.WEREWOLF, ConversionParams.single(villager, false, false), wolf -> {
			wolf.setVillager(saved);
			wolf.setSawNight(isNight(level));
			wolf.setPersistenceRequired();
		});
		if (werewolf == null) {
			// Couldn't change (peaceful): it stays a villager, still cursed.
			if (villager.isAlive()) {
				LOADED.add(villager);
			}
			return null;
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, werewolf.getX(), werewolf.getY(0.5), werewolf.getZ(), 20, 0.4, 0.8, 0.4, 0.03);
		level.sendParticles(ParticleTypes.POOF, werewolf.getX(), werewolf.getY(0.5), werewolf.getZ(), 12, 0.4, 0.8, 0.4, 0.02);
		werewolf.howl();
		return werewolf;
	}

	/**
	 * Turns a werewolf back into the villager it was, where it stands (and still cursed). Its wounds stay with it: the
	 * villager comes back no healthier, as a share of its health, than the werewolf was. One that never was a villager
	 * becomes a new, cursed one. Null if it couldn't.
	 */
	public static @Nullable Villager revert(ServerLevel level, Werewolf werewolf) {
		CompoundTag saved = werewolf.villager();
		float health = werewolf.getHealth() / werewolf.getMaxHealth();
		Villager villager = werewolf.convertTo(EntityTypes.VILLAGER, ConversionParams.single(werewolf, false, false), human -> {
			if (saved != null) {
				human.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
				human.snapTo(werewolf.getX(), werewolf.getY(), werewolf.getZ(), werewolf.getYRot(), werewolf.getXRot());
				human.setDeltaMovement(Vec3.ZERO);
				human.setHealth(Math.max(1.0f, Math.min(human.getHealth(), human.getMaxHealth() * health)));
			} else {
				human.finalizeSpawn(level, level.getCurrentDifficultyAt(human.blockPosition()), EntitySpawnReason.CONVERSION, null);
			}
			human.setYHeadRot(werewolf.getYHeadRot());
			human.setYBodyRot(werewolf.yBodyRot);
			human.setAttached(Werewolves.CURSED, true);
		});
		if (villager != null) {
			level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY(0.5), villager.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.WOLF_SHAKE, SoundSource.NEUTRAL, 1.0f, 0.8f);
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.8f, 0.8f);
		}
		return villager;
	}

	/** A werewolf has died: the villager it was gives up its bed and workplace, as a villager does when it dies. */
	static void releaseVillagerPois(ServerLevel level, Werewolf werewolf) {
		CompoundTag saved = werewolf.villager();
		if (saved == null) {
			return;
		}
		Villager ghost = EntityTypes.VILLAGER.create(level, EntitySpawnReason.LOAD);
		if (ghost == null) {
			return;
		}
		ghost.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		ghost.releasePoi(MemoryModuleType.HOME);
		ghost.releasePoi(MemoryModuleType.JOB_SITE);
		ghost.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		ghost.releasePoi(MemoryModuleType.MEETING_POINT);
		ghost.discard();
	}

	private static CompoundTag save(ServerLevel level, Villager villager) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		villager.saveWithoutId(output);
		return output.buildResult();
	}
}
