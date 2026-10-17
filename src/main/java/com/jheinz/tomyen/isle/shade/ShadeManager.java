package com.jheinz.tomyen.isle.shade;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.isle.Isle;
import com.jheinz.tomyen.isle.IsleDepth;
import com.jheinz.tomyen.isle.IsleDimension;
import com.jheinz.tomyen.isle.IsleEvents;
import com.jheinz.tomyen.isle.Revenant;

/**
 * Raises shades round players on the Isle and dissolves them when the players go. Shades are never saved and exist
 * only here: at most {@value #PLAYER_CAP} round any one player and {@value #GLOBAL_CAP} in all, of which at most
 * {@value #ECHO_CAP} a player are echoes (from records) and {@value #BOSS_CAP} in all are bosses' shades.
 *
 * <p>Every {@value #SPAWN_INTERVAL} ticks, a player's turn (staggered, so players don't all go on the same tick):
 *
 * <ul>
 * <li>if their next Soul Tide wave is due and the tide is at least {@value #TIDE_THRESHOLD}, a wave of 1 to 8 tide
 * shades (more the higher it runs) rises in a knot {@value #MIN_SPAWN}-{@value #MAX_SPAWN} blocks off; the next one
 * comes 30 to 90 seconds later, sooner the higher the tide;
 * <li>otherwise, by a chance growing with depth, a record is drawn: half the time from the region they came down from,
 * else from anywhere. A boss's record (rarer still, deeper more often, one per player every five minutes) makes a boss
 * shade; a pet's makes its echo only for its owner.
 * </ul>
 *
 * Each second, a shade whose player has gone (logged off, left the Isle, or more than {@value #KEEP_RANGE} blocks off)
 * passes to another player close by, or dissolves. Nothing comes within {@value #SHRINE_SAFE} blocks of the arrival
 * shrine. Every minute the tide ebbs ({@link IsleSouls#decay}), and every two seconds its strength goes to the
 * players on the Isle for their fog, motes and HUD.
 */
public final class ShadeManager {
	public static final int PLAYER_CAP = 20;
	public static final int GLOBAL_CAP = 40;
	public static final int ECHO_CAP = 6;
	public static final int BOSS_CAP = 2;
	private static final int INTERVAL = 20;
	private static final int SPAWN_INTERVAL = 60;
	private static final int MIN_SPAWN = 14;
	private static final int MAX_SPAWN = 28;
	private static final int KEEP_RANGE = 64;
	private static final int SHRINE_SAFE = 24;
	public static final int TIDE_THRESHOLD = 40;
	private static final int DECAY_INTERVAL = 1200;
	private static final int SYNC_INTERVAL = 40;
	private static final int BOSS_COOLDOWN = 6000;
	private static final int FIRST_WAVE_DELAY = 600;
	private static final float[] ECHO_CHANCE = {0.35f, 0.5f, 0.65f};
	private static final float[] BOSS_CHANCE = {0.05f, 0.1f, 0.18f};
	private static final List<EntityType<?>> FALLBACK_ECHOES = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.HUSK, EntityTypes.STRAY);

	private static final Set<Shade> TRACKED = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<UUID, Long> NEXT_WAVE = new HashMap<>();
	private static final Map<UUID, Long> BOSS_READY = new HashMap<>();

	private static long spawned, dissolved, killed;
	private static int peak;

	private ShadeManager() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ShadeManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRACKED.clear();
			NEXT_WAVE.clear();
			BOSS_READY.clear();
			spawned = dissolved = killed = 0;
			peak = 0;
		});
		IsleEvents.ARRIVED.register((player, depth, offering) -> NEXT_WAVE.put(player.getUUID(), now(player.level().getServer()) + FIRST_WAVE_DELAY));
		IsleEvents.LEFT.register((player, victorious) -> dissolveFor(player.getUUID()));
		IsleEvents.REVENANT_RISEN.register(ShadeManager::revenantRisen);
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static boolean isTracked(Shade shade) {
		return TRACKED.contains(shade);
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % DECAY_INTERVAL == 0) {
			IsleSouls.get(server).decay();
		}
		if (tick % SYNC_INTERVAL == 0) {
			syncTide(server);
		}
		ServerLevel isle = server.getLevel(IsleDimension.LEVEL);
		if (tick % INTERVAL == 0) {
			maintain(server, isle);
		}
		if (isle == null || isle.players().isEmpty()) {
			return;
		}
		long now = now(server);
		for (ServerPlayer player : List.copyOf(isle.players())) {
			if ((tick + player.getId() * 7) % SPAWN_INTERVAL == 0) {
				spawnFor(isle, player, now);
			}
		}
	}

	// --- Keeping count ------------------------------------------------------------------------------------------------

	private static void track(Shade shade) {
		TRACKED.add(shade);
		peak = Math.max(peak, TRACKED.size());
	}

	/** A shade the manager didn't raise (a /summon): it's taken in, anchored to whoever's nearest. */
	static void adopt(Shade shade) {
		track(shade);
		Player nearest = shade.level().getNearestPlayer(shade, KEEP_RANGE);
		shade.setAnchor(nearest == null ? null : nearest.getUUID());
	}

	private static void maintain(MinecraftServer server, @Nullable ServerLevel isle) {
		if (TRACKED.isEmpty()) {
			return;
		}
		for (Shade shade : List.copyOf(TRACKED)) {
			if (shade.isRemoved()) {
				TRACKED.remove(shade);
				continue;
			}
			if (shade.level() != isle) {
				dissolve(shade);
				continue;
			}
			ServerPlayer anchor = shade.anchor() == null ? null : server.getPlayerList().getPlayer(shade.anchor());
			if (keeps(anchor, shade)) {
				continue;
			}
			ServerPlayer other = shade.kind() == Shade.Kind.PET ? null : nearestPlayer(isle, shade);
			if (other != null) {
				shade.setAnchor(other.getUUID());
			} else {
				dissolve(shade);
			}
		}
	}

	private static boolean keeps(@Nullable ServerPlayer player, Shade shade) {
		return player != null && player.isAlive() && !player.isSpectator() && player.level() == shade.level()
				&& player.distanceToSqr(shade) < KEEP_RANGE * KEEP_RANGE;
	}

	private static @Nullable ServerPlayer nearestPlayer(ServerLevel isle, Shade shade) {
		ServerPlayer best = null;
		double bestDistance = KEEP_RANGE * KEEP_RANGE;
		for (ServerPlayer player : isle.players()) {
			double distance = player.distanceToSqr(shade);
			if (distance < bestDistance && player.isAlive() && !player.isSpectator()) {
				best = player;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Gone in a breath of souls. */
	public static void dissolve(Shade shade) {
		if (shade.level() instanceof ServerLevel level && !shade.isRemoved()) {
			level.sendParticles(ParticleTypes.SOUL, shade.getX(), shade.getY(0.5), shade.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			level.playSound(null, shade.getX(), shade.getY(), shade.getZ(), IsleShades.SHADE_DISSOLVE, SoundSource.HOSTILE, 0.5f, 1.0f);
		}
		shade.discard();
		TRACKED.remove(shade);
		dissolved++;
	}

	private static void dissolveFor(UUID player) {
		for (Shade shade : List.copyOf(TRACKED)) {
			if (player.equals(shade.anchor())) {
				dissolve(shade);
			}
		}
	}

	// --- Raising them -------------------------------------------------------------------------------------------------

	private static void spawnFor(ServerLevel isle, ServerPlayer player, long now) {
		if (!player.isAlive() || player.isSpectator() || TRACKED.size() >= GLOBAL_CAP) {
			return;
		}
		UUID id = player.getUUID();
		int near = 0, echoes = 0, bosses = 0;
		boolean ownBoss = false;
		for (Shade shade : TRACKED) {
			if (shade.kind() == Shade.Kind.BOSS) {
				bosses++;
			}
			if (id.equals(shade.anchor())) {
				near++;
				if (shade.kind() != Shade.Kind.TIDE) {
					echoes++;
				}
				ownBoss |= shade.kind() == Shade.Kind.BOSS;
			}
		}
		if (near >= PLAYER_CAP) {
			return;
		}
		int room = Math.min(PLAYER_CAP - near, GLOBAL_CAP - TRACKED.size());
		IsleSouls souls = IsleSouls.get(isle.getServer());
		IsleDepth depth = IsleDepth.at(isle, player.blockPosition());
		RandomSource random = isle.getRandom();

		Long due = NEXT_WAVE.get(id);
		if (due == null) {
			NEXT_WAVE.put(id, now + FIRST_WAVE_DELAY);
		} else if (now >= due) {
			float strength = souls.tideStrength();
			if (souls.tideTotal() >= TIDE_THRESHOLD) {
				wave(isle, player, depth, souls, room, strength);
				NEXT_WAVE.put(id, now + (long) Mth.lerp(strength, 1800.0f, 600.0f) + random.nextInt(200));
			} else {
				NEXT_WAVE.put(id, now + 400);
			}
			return;
		}

		if (echoes >= ECHO_CAP || random.nextFloat() >= ECHO_CHANCE[depth.index]) {
			return;
		}
		if (!ownBoss && bosses < BOSS_CAP && now >= BOSS_READY.getOrDefault(id, 0L) && random.nextFloat() < BOSS_CHANCE[depth.index]) {
			DeathRecord boss = souls.sampleBoss(random, now);
			if (boss != null && !active(boss)) {
				BOSS_READY.put(id, now + BOSS_COOLDOWN);
				raiseBoss(isle, player, depth, boss, now);
				return;
			}
		}
		for (int attempt = 0; attempt < 3; attempt++) {
			IsleSouls.Sampled sampled = sample(souls, player, random, now);
			if (sampled == null || active(sampled.record())) {
				continue;
			}
			DeathRecord record = sampled.record();
			EntityType<?> type = record.entityType().orElse(null);
			if (type == null) {
				continue;
			}
			if (record.isPet()) {
				if (record.owner().get().equals(id)) {
					raisePet(isle, player, type, depth, sampled, now);
					return;
				}
				continue;
			}
			Vec3 at = spot(isle, player, MIN_SPAWN, MAX_SPAWN);
			if (at == null) {
				return;
			}
			Shade shade = raise(isle, type, Shade.Kind.ECHO, depth, record, sampled.region(), vividness(record, now, IsleSouls.FADE_TICKS), at, id);
			if (shade != null) {
				announceEcho(player, record);
			}
			return;
		}
	}

	/** Half the time from the region they came down from (where they've been dying, and killing), else from anywhere. */
	private static IsleSouls.@Nullable Sampled sample(IsleSouls souls, ServerPlayer player, RandomSource random, long now) {
		GlobalPos home = player.getAttached(Isle.RETURN_POINT);
		if (home != null && random.nextBoolean()) {
			IsleSouls.RegionKey region = IsleSouls.region(home.dimension().identifier(), home.pos().getX(), home.pos().getZ());
			DeathRecord record = souls.sample(region, random, now);
			if (record != null) {
				return new IsleSouls.Sampled(record, region);
			}
		}
		return souls.sampleAny(random, now);
	}

	private static boolean active(DeathRecord record) {
		for (Shade shade : TRACKED) {
			if (shade.record() != null && shade.record().id() == record.id()) {
				return true;
			}
		}
		return false;
	}

	private static float vividness(DeathRecord record, long now, long fade) {
		return 1.0f - 0.6f * Mth.clamp((now - record.time()) / (float) fade, 0.0f, 1.0f);
	}

	private static void wave(ServerLevel isle, ServerPlayer player, IsleDepth depth, IsleSouls souls, int room, float strength) {
		int size = Math.min(room, Mth.clamp(Math.round(1 + strength * 8), 1, 8));
		Vec3 centre = spot(isle, player, MIN_SPAWN + 2, MAX_SPAWN);
		if (centre == null || size <= 0) {
			return;
		}
		RandomSource random = isle.getRandom();
		int raised = 0;
		for (int i = 0; i < size; i++) {
			Identifier typeId = souls.drawTideType(random);
			EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
			if (type == null || type.builtInRegistryHolder().is(IsleShades.SOULLESS)) {
				continue;
			}
			double x = centre.x + random.nextGaussian() * 2.5, z = centre.z + random.nextGaussian() * 2.5;
			double y = Math.max(centre.y, isle.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.5);
			if (raise(isle, type, Shade.Kind.TIDE, depth, null, null, 0.85f, new Vec3(x, y, z), player.getUUID()) != null) {
				raised++;
			}
		}
		if (raised >= 3) {
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.tide_surges").withStyle(ChatFormatting.DARK_AQUA));
			isle.playSound(null, player.getX(), player.getY(), player.getZ(), IsleShades.SOUL_TIDE_SURGE, SoundSource.HOSTILE, 1.0f, 1.0f);
		}
	}

	private static void raiseBoss(ServerLevel isle, ServerPlayer player, IsleDepth depth, DeathRecord record, long now) {
		EntityType<?> type = record.entityType().orElse(null);
		Vec3 at = spot(isle, player, 18, 26);
		if (type == null || at == null) {
			return;
		}
		Shade boss = raise(isle, type, Shade.Kind.BOSS, depth, record, null, vividness(record, now, IsleSouls.BOSS_FADE_TICKS), at, player.getUUID());
		if (boss == null) {
			return;
		}
		isle.playSound(null, at.x, at.y, at.z, IsleShades.BOSS_SHADE_RISE, SoundSource.HOSTILE, 4.0f, 1.0f);
		isle.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 1.0, at.z, 60, 1.2, 1.2, 1.2, 0.05);
		Component message = Component.translatable("message.tomyen.isle.boss_shade", boss.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE);
		for (ServerPlayer near : isle.players()) {
			if (near.distanceToSqr(at) < 96 * 96) {
				near.sendSystemMessage(message);
			}
		}
		MyMod.LOGGER.info("Isle of the Damned: a boss shade ({}) rose for {} at {} in the {}", record.type(), player.getName().getString(),
				boss.blockPosition().toShortString(), depth.name);
	}

	private static void raisePet(ServerLevel isle, ServerPlayer player, EntityType<?> type, IsleDepth depth, IsleSouls.Sampled sampled, long now) {
		Vec3 at = spot(isle, player, 6, 10);
		if (at == null) {
			return;
		}
		Shade pet = raise(isle, type, Shade.Kind.PET, depth, sampled.record(), sampled.region(), vividness(sampled.record(), now, IsleSouls.FADE_TICKS),
				at, player.getUUID());
		if (pet != null) {
			player.sendSystemMessage(Component.translatable("message.tomyen.isle.pet_echo", pet.getDisplayName()).withStyle(ChatFormatting.GRAY,
					ChatFormatting.ITALIC));
		}
	}

	/** A shade of that type, formed at {@code at} and anchored to the player. Null if it couldn't be made. */
	public static @Nullable Shade raise(ServerLevel isle, EntityType<?> type, Shade.Kind kind, IsleDepth depth, @Nullable DeathRecord record,
			IsleSouls.@Nullable RegionKey region, float vividness, Vec3 at, @Nullable UUID anchor) {
		Shade shade = IsleShades.SHADE.create(isle, EntitySpawnReason.EVENT);
		if (shade == null) {
			return null;
		}
		shade.setup(type, kind, depth, record, region, vividness);
		shade.snapTo(at.x, at.y, at.z, isle.getRandom().nextFloat() * 360.0f, 0.0f);
		shade.setAnchor(anchor);
		track(shade);
		isle.addFreshEntity(shade);
		isle.playSound(null, at.x, at.y, at.z, IsleShades.SHADE_FORM, SoundSource.HOSTILE, kind == Shade.Kind.TIDE ? 0.4f : 0.7f, 1.0f);
		spawned++;
		return shade;
	}

	/**
	 * Somewhere between {@code min} and {@code max} blocks from the player, over loaded ground or sea, not far above or
	 * below them and not by the shrine: a few tries, else null.
	 */
	private static @Nullable Vec3 spot(ServerLevel isle, ServerPlayer player, int min, int max) {
		RandomSource random = isle.getRandom();
		for (int i = 0; i < 8; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = min + random.nextDouble() * (max - min);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			if ((long) x * x + (long) z * z < SHRINE_SAFE * SHRINE_SAFE || !isle.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			int y = isle.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (y <= isle.getMinY() + 1 || Math.abs(y - player.getY()) > 20) {
				continue;
			}
			return new Vec3(x + 0.5, y + 0.5, z + 0.5);
		}
		return null;
	}

	private static void announceEcho(ServerPlayer player, DeathRecord record) {
		if (record.killer().isPresent() && record.killer().get().equals(player.getUUID())) {
			Component what = record.name().<Component>map(Component::literal)
					.orElseGet(() -> record.entityType().map(EntityType::getDescription).orElse(Component.literal("?")));
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.your_victim", what).withStyle(ChatFormatting.GRAY));
		} else if (record.name().isPresent()) {
			player.sendOverlayMessage(Component.translatable("message.tomyen.isle.named_shade", record.name().get(), origin(record))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/** "near 256, -768 in the Overworld". */
	public static Component origin(DeathRecord record) {
		String[] parts = record.origin().split(" ");
		if (parts.length != 3) {
			return Component.literal("?");
		}
		Identifier dimension = Identifier.tryParse(parts[0]);
		String key = dimension == null ? "" : "dimension." + dimension.getNamespace() + "." + dimension.getPath();
		return Component.translatable("message.tomyen.isle.origin", parts[1], parts[2], Component.translatableWithFallback(key, parts[0]));
	}

	// --- Shades' own doings -------------------------------------------------------------------------------------------

	/** A boss's shade at two-thirds and one-third health: echoes come up out of the ground round it. */
	static void summonEchoes(ServerLevel level, Shade boss, int count) {
		IsleSouls souls = IsleSouls.get(level.getServer());
		RandomSource random = level.getRandom();
		for (int i = 0; i < count && TRACKED.size() < GLOBAL_CAP; i++) {
			Identifier typeId = souls.drawTideType(random);
			EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
			if (type == null || type.builtInRegistryHolder().is(IsleShades.SOULLESS)) {
				type = FALLBACK_ECHOES.get(random.nextInt(FALLBACK_ECHOES.size()));
			}
			double angle = random.nextDouble() * Math.PI * 2;
			double x = boss.getX() + Math.cos(angle) * 3.0, z = boss.getZ() + Math.sin(angle) * 3.0;
			double y = Math.max(boss.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.5);
			raise(level, type, Shade.Kind.TIDE, boss.depth(), null, null, 0.85f, new Vec3(x, y, z), boss.anchor());
		}
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), IsleShades.BOSS_SHADE_RISE, SoundSource.HOSTILE, 1.5f, 1.4f);
	}

	/** A pet's shade has stayed its minute: it says goodbye, and its record is laid to rest. */
	static void petGoes(Shade pet) {
		if (pet.level() instanceof ServerLevel level) {
			ServerPlayer owner = pet.anchor() == null ? null : level.getServer().getPlayerList().getPlayer(pet.anchor());
			if (owner != null) {
				owner.sendSystemMessage(Component.translatable("message.tomyen.isle.pet_goes", pet.getDisplayName()).withStyle(ChatFormatting.GRAY,
						ChatFormatting.ITALIC));
			}
			if (pet.record() != null) {
				IsleSouls.get(level.getServer()).remove(pet.region(), pet.record());
			}
			level.sendParticles(ParticleTypes.WHITE_ASH, pet.getX(), pet.getY(0.5), pet.getZ(), 30, 0.4, 0.6, 0.4, 0.01);
		}
		dissolve(pet);
	}

	/** A shade was beaten: the tide ebbs, and a remembered one's record is laid to rest. */
	static void died(ServerLevel level, Shade shade, DamageSource source) {
		TRACKED.remove(shade);
		killed++;
		IsleSouls souls = IsleSouls.get(level.getServer());
		EntityType<?> type = shade.mimicType();
		Identifier typeId = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
		switch (shade.kind()) {
			case TIDE -> {
				if (typeId != null) {
					souls.drainTide(typeId, 6);
				}
			}
			case ECHO -> {
				if (typeId != null) {
					souls.drainTide(typeId, 2);
				}
				if (shade.record() != null) {
					souls.remove(shade.region(), shade.record());
					if (shade.record().name().isPresent() && source.getEntity() instanceof ServerPlayer player) {
						player.sendSystemMessage(Component.translatable("message.tomyen.isle.named_rests", shade.record().name().get())
								.withStyle(ChatFormatting.GRAY));
					}
				}
			}
			case BOSS -> {
				souls.drainTideShare(0.25f);
				if (shade.record() != null) {
					souls.remove(null, shade.record());
				}
				Component message = Component.translatable("message.tomyen.isle.boss_shade_rests", shade.getDisplayName())
						.withStyle(ChatFormatting.LIGHT_PURPLE);
				for (ServerPlayer near : level.players()) {
					if (near.distanceToSqr(shade) < 96 * 96) {
						near.sendSystemMessage(message);
					}
				}
			}
			case PET -> {
			}
		}
	}

	/** As a Revenant rises, the dead gather at its arena's edge: one shade, plus one a depth. */
	private static void revenantRisen(ServerLevel level, Revenant revenant, com.jheinz.tomyen.isle.Hoard hoard) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(hoard.owner());
		if (owner == null) {
			return;
		}
		IsleSouls souls = IsleSouls.get(level.getServer());
		RandomSource random = level.getRandom();
		IsleDepth depth = hoard.depth();
		long now = now(level.getServer());
		for (int i = 0; i <= depth.index && TRACKED.size() < GLOBAL_CAP; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double x = revenant.getHomePosition().getX() + 0.5 + Math.cos(angle) * 11.0;
			double z = revenant.getHomePosition().getZ() + 0.5 + Math.sin(angle) * 11.0;
			double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.5;
			IsleSouls.Sampled sampled = souls.sampleAny(random, now);
			if (sampled != null && !sampled.record().isPet() && !active(sampled.record()) && sampled.record().entityType().isPresent()) {
				raise(level, sampled.record().entityType().get(), Shade.Kind.ECHO, depth, sampled.record(), sampled.region(),
						vividness(sampled.record(), now, IsleSouls.FADE_TICKS), new Vec3(x, y, z), owner.getUUID());
			} else {
				Identifier typeId = souls.drawTideType(random);
				EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
				if (type == null || type.builtInRegistryHolder().is(IsleShades.SOULLESS)) {
					type = FALLBACK_ECHOES.get(random.nextInt(FALLBACK_ECHOES.size()));
				}
				raise(level, type, Shade.Kind.TIDE, depth, null, null, 0.85f, new Vec3(x, y, z), owner.getUUID());
			}
		}
	}

	/** Every two seconds: the tide's strength to the players on the Isle; off those who've left. */
	private static void syncTide(MinecraftServer server) {
		float strength = IsleSouls.get(server).tideStrength();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Float current = player.getAttached(IsleShades.TIDE);
			if (IsleDimension.is(player.level())) {
				if (current == null || Math.abs(current - strength) > 0.005f) {
					player.setAttached(IsleShades.TIDE, strength);
				}
			} else if (current != null) {
				player.removeAttached(IsleShades.TIDE);
			}
		}
	}

	// --- For /isle souls ----------------------------------------------------------------------------------------------

	public static int active() {
		return TRACKED.size();
	}

	public static long spawned() {
		return spawned;
	}

	public static long dissolved() {
		return dissolved;
	}

	public static long killed() {
		return killed;
	}

	public static int peak() {
		return peak;
	}

	/** Lays every shade to rest at once. */
	public static void clear() {
		for (Shade shade : List.copyOf(TRACKED)) {
			dissolve(shade);
		}
	}

	/** Makes their next wave due now. */
	public static void waveNow(ServerPlayer player) {
		NEXT_WAVE.put(player.getUUID(), 0L);
	}
}
