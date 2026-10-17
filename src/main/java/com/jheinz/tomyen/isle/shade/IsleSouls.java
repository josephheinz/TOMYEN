package com.jheinz.tomyen.isle.shade;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.jheinz.tomyen.MyMod;

/**
 * Everything the Isle of the Damned remembers of the world's dead, kept in the server's saved data
 * ({@code data/tomyen/isle_souls.dat}). Two kinds of memory, both of fixed size however many mobs die:
 *
 * <ul>
 * <li><b>Death records</b> of notable deaths ({@link DeathRecord}), in a ring buffer of {@value #REGION_CAPACITY} per
 * 512-block region of the world they fell in. Only the {@value #MAX_REGIONS} most recently written regions are kept
 * (the least recently written one is dropped, whole, when a new one starts). Bosses go in a ring of their own,
 * {@value #BOSS_CAPACITY} long, so a busy region can't push them out. Writing one is a map lookup and an array store.
 * Records fade: one older than {@link #FADE_TICKS} (a boss's, {@link #BOSS_FADE_TICKS}) is passed over when read and
 * cleared then; nothing ever scans for them.
 * <li><b>The Soul Tide</b>: a count of routine deaths per entity type, each capped at {@value #TIDE_TYPE_CAP}. It
 * drains as shades are killed on the Isle and by {@value #DECAY_PERCENT}% a minute on its own ({@link #decay}).
 * </ul>
 */
public final class IsleSouls extends SavedData {
	public static final int REGION_SHIFT = 9;
	public static final int MAX_REGIONS = 96;
	public static final int REGION_CAPACITY = 24;
	public static final int BOSS_CAPACITY = 12;
	/** Seven in-game days. */
	public static final long FADE_TICKS = 7 * 24000L;
	public static final long BOSS_FADE_TICKS = 14 * 24000L;
	public static final int TIDE_TYPE_CAP = 4000;
	public static final int DECAY_PERCENT = 3;
	/** The tide total at which it's half strength (see {@link #tideStrength}). */
	public static final float TIDE_HALF = 400.0f;

	public record RegionKey(Identifier dimension, int x, int z) {
	}

	/** A fixed-size ring of records, oldest overwritten first. A cleared slot is null. */
	public static final class Ring {
		private final DeathRecord[] slots;
		private int head;
		private int size;

		Ring(int capacity) {
			slots = new DeathRecord[capacity];
		}

		void add(DeathRecord record) {
			slots[head] = record;
			head = (head + 1) % slots.length;
			size = Math.min(size + 1, slots.length);
		}

		/** A random slot's record (null if that slot's empty): one array read. */
		@Nullable
		DeathRecord random(RandomSource random) {
			return size == 0 ? null : slots[(head - 1 - random.nextInt(size) + slots.length * 2) % slots.length];
		}

		boolean remove(long id) {
			for (int i = 0; i < slots.length; i++) {
				if (slots[i] != null && slots[i].id() == id) {
					slots[i] = null;
					return true;
				}
			}
			return false;
		}

		void clear(DeathRecord record) {
			remove(record.id());
		}

		/** Oldest first. */
		List<DeathRecord> list() {
			List<DeathRecord> out = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				DeathRecord record = slots[(head - size + i + slots.length) % slots.length];
				if (record != null) {
					out.add(record);
				}
			}
			return out;
		}

		public int size() {
			return size;
		}

		int live() {
			int n = 0;
			for (DeathRecord record : slots) {
				if (record != null) {
					n++;
				}
			}
			return n;
		}
	}

	// --- Saving -------------------------------------------------------------------------------------------------------
	private record SavedRegion(Identifier dimension, int x, int z, List<DeathRecord> records) {
		static final Codec<SavedRegion> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("dimension").forGetter(SavedRegion::dimension),
				Codec.INT.fieldOf("x").forGetter(SavedRegion::x),
				Codec.INT.fieldOf("z").forGetter(SavedRegion::z),
				DeathRecord.CODEC.listOf().fieldOf("records").forGetter(SavedRegion::records))
				.apply(i, SavedRegion::new));
	}

	public static final Codec<IsleSouls> CODEC = RecordCodecBuilder.create(i -> i.group(
			SavedRegion.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(IsleSouls::savedRegions),
			DeathRecord.CODEC.listOf().optionalFieldOf("bosses", List.of()).forGetter(souls -> souls.bosses.list()),
			Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("tide", Map.of()).forGetter(souls -> Map.copyOf(souls.tide)),
			Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(souls -> souls.nextId))
			.apply(i, IsleSouls::new));

	public static final SavedDataType<IsleSouls> TYPE = new SavedDataType<>(MyMod.id("isle_souls"), IsleSouls::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Least recently written first; the eldest is dropped past {@link #MAX_REGIONS}. */
	private final LinkedHashMap<RegionKey, Ring> regions = new LinkedHashMap<>(MAX_REGIONS * 2) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<RegionKey, Ring> eldest) {
			return size() > MAX_REGIONS;
		}
	};
	private final Ring bosses = new Ring(BOSS_CAPACITY);
	private final Object2IntOpenHashMap<Identifier> tide = new Object2IntOpenHashMap<>();
	private int tideTotal;
	private long nextId = 1;

	public IsleSouls() {
	}

	private IsleSouls(List<SavedRegion> savedRegions, List<DeathRecord> savedBosses, Map<Identifier, Integer> savedTide, long nextId) {
		for (SavedRegion saved : savedRegions) {
			Ring ring = new Ring(REGION_CAPACITY);
			saved.records().forEach(ring::add);
			regions.put(new RegionKey(saved.dimension(), saved.x(), saved.z()), ring);
		}
		savedBosses.forEach(bosses::add);
		savedTide.forEach((type, count) -> {
			int capped = Math.min(count, TIDE_TYPE_CAP);
			tide.put(type, capped);
			tideTotal += capped;
		});
		this.nextId = nextId;
	}

	private List<SavedRegion> savedRegions() {
		List<SavedRegion> out = new ArrayList<>(regions.size());
		regions.forEach((key, ring) -> out.add(new SavedRegion(key.dimension(), key.x(), key.z(), ring.list())));
		return out;
	}

	public static IsleSouls get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// --- Records ------------------------------------------------------------------------------------------------------

	public long nextId() {
		return nextId++;
	}

	public static RegionKey region(Identifier dimension, int blockX, int blockZ) {
		return new RegionKey(dimension, blockX >> REGION_SHIFT, blockZ >> REGION_SHIFT);
	}

	/** O(1): a map lookup (and, for a new region, the eviction of the least recently written one) and an array store. */
	public void record(RegionKey region, DeathRecord record) {
		// Taken out and put back, so the map's order stays the order regions were last written in.
		Ring ring = regions.remove(region);
		if (ring == null) {
			ring = new Ring(REGION_CAPACITY);
		}
		ring.add(record);
		regions.put(region, ring);
		setDirty();
	}

	public void recordBoss(DeathRecord record) {
		bosses.add(record);
		setDirty();
	}

	/** A random live record from the region, or null (an empty or faded slot: the faded one is cleared). */
	@Nullable
	public DeathRecord sample(RegionKey region, RandomSource random, long now) {
		Ring ring = regions.get(region);
		return ring == null ? null : live(ring, random, now, FADE_TICKS);
	}

	/** A record and the region it's from. */
	public record Sampled(DeathRecord record, RegionKey region) {
	}

	/** A random live record from any region: the walk to it is over at most {@value #MAX_REGIONS} map entries. */
	@Nullable
	public Sampled sampleAny(RandomSource random, long now) {
		if (regions.isEmpty()) {
			return null;
		}
		int skip = random.nextInt(regions.size());
		Iterator<Map.Entry<RegionKey, Ring>> it = regions.entrySet().iterator();
		for (int i = 0; i < skip; i++) {
			it.next();
		}
		Map.Entry<RegionKey, Ring> entry = it.next();
		DeathRecord record = live(entry.getValue(), random, now, FADE_TICKS);
		return record == null ? null : new Sampled(record, entry.getKey());
	}

	@Nullable
	public DeathRecord sampleBoss(RandomSource random, long now) {
		return live(bosses, random, now, BOSS_FADE_TICKS);
	}

	@Nullable
	private DeathRecord live(Ring ring, RandomSource random, long now, long fade) {
		DeathRecord record = ring.random(random);
		if (record != null && now - record.time() > fade) {
			ring.clear(record);
			setDirty();
			return null;
		}
		return record;
	}

	/** Lays a record to rest (its shade was beaten, or its pet said goodbye). */
	public void remove(@Nullable RegionKey region, DeathRecord record) {
		boolean removed = bosses.remove(record.id());
		if (!removed && region != null) {
			Ring ring = regions.get(region);
			removed = ring != null && ring.remove(record.id());
		}
		if (removed) {
			setDirty();
		}
	}

	public int regionCount() {
		return regions.size();
	}

	public int recordCount() {
		int n = 0;
		for (Ring ring : regions.values()) {
			n += ring.live();
		}
		return n;
	}

	public int bossCount() {
		return bosses.live();
	}

	public void clearRecords() {
		regions.clear();
		for (DeathRecord record : bosses.list()) {
			bosses.clear(record);
		}
		setDirty();
	}

	// --- The Soul Tide ------------------------------------------------------------------------------------------------

	/** O(1): one hash map increment. */
	public void addTide(Identifier type, int amount) {
		int before = tide.getInt(type);
		int after = Math.min(TIDE_TYPE_CAP, before + amount);
		if (after != before) {
			tide.put(type, after);
			tideTotal += after - before;
			setDirty();
		}
	}

	public void drainTide(Identifier type, int amount) {
		int before = tide.getInt(type);
		if (before <= 0) {
			return;
		}
		int after = Math.max(0, before - amount);
		if (after == 0) {
			tide.removeInt(type);
		} else {
			tide.put(type, after);
		}
		tideTotal -= before - after;
		setDirty();
	}

	/** Drains a share of every type's tide (a boss shade's fall). */
	public void drainTideShare(float share) {
		for (Identifier type : List.copyOf(tide.keySet())) {
			drainTide(type, Math.max(1, Math.round(tide.getInt(type) * share)));
		}
	}

	/** The slow ebb, once a minute: every type loses {@value #DECAY_PERCENT}% (at least one). */
	public void decay() {
		if (tide.isEmpty()) {
			return;
		}
		for (Iterator<Object2IntMap.Entry<Identifier>> it = tide.object2IntEntrySet().iterator(); it.hasNext();) {
			Object2IntMap.Entry<Identifier> entry = it.next();
			int before = entry.getIntValue();
			int after = before - Math.max(1, before * DECAY_PERCENT / 100);
			if (after <= 0) {
				it.remove();
				tideTotal -= before;
			} else {
				entry.setValue(after);
				tideTotal -= before - after;
			}
		}
		setDirty();
	}

	public int tideTotal() {
		return tideTotal;
	}

	public int tide(Identifier type) {
		return tide.getInt(type);
	}

	public int tideTypes() {
		return tide.size();
	}

	/** 0 when calm, a half at {@link #TIDE_HALF}, nearing 1 as it rises without end. */
	public float tideStrength() {
		return tideTotal / (tideTotal + TIDE_HALF);
	}

	/** A type drawn in proportion to its share of the tide (a walk over the types, once per wave), or null if it's calm. */
	@Nullable
	public Identifier drawTideType(RandomSource random) {
		if (tideTotal <= 0) {
			return null;
		}
		int pick = random.nextInt(tideTotal);
		for (Object2IntMap.Entry<Identifier> entry : tide.object2IntEntrySet()) {
			pick -= entry.getIntValue();
			if (pick < 0) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** The biggest few types, for the command. */
	public List<Map.Entry<Identifier, Integer>> topTide(int count) {
		List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>();
		for (Object2IntMap.Entry<Identifier> entry : tide.object2IntEntrySet()) {
			entries.add(Map.entry(entry.getKey(), entry.getIntValue()));
		}
		entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return entries.subList(0, Math.min(count, entries.size()));
	}

	public void clearTide() {
		tide.clear();
		tideTotal = 0;
		setDirty();
	}
}
