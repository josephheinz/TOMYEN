package com.jheinz.mymod.fishing.fish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

import com.jheinz.mymod.MyMod;

/**
 * One kind of living fish: its catch, size, school, voice, and where it spawns. Every kind shares the {@link ModFish}
 * entity class (the lava ones {@link LavaFish}), one renderer and one model class; {@link FishEntities} registers
 * each kind's entity type, bucket and spawn egg.
 *
 * <p>Where a kind spawns follows its fishing species file (data/mymod/fishing_species): the same biomes, dimension,
 * depth, time of day and weather, with weights kept modest beside vanilla's cod (10 in an ocean) and salmon (5 in a
 * river). Time is ignored in a dimension whose time stands still (the Feywild's endless dusk), where it never turns.
 */
public final class FishKind {
	/** Where in the water it spawns. */
	public enum Depth {
		/** In the top 13 blocks of water at or below sea level, like vanilla's fish. */
		SURFACE,
		/** Below that: deep-sea water. */
		DEEP,
		/** Any water at all (the Feywild's ponds sit well above its sea level). */
		ANY,
		/** Any water at or below {@link #maxY}: caves and aquifers. */
		LOW,
		/** In lava. */
		LAVA
	}

	public enum Time {
		ANY, DAY, NIGHT
	}

	public enum Weather {
		ANY, CLEAR, THUNDER
	}

	/** Which vanilla fish it sounds like. */
	public enum Voice {
		COD(SoundEvents.COD_AMBIENT, SoundEvents.COD_HURT, SoundEvents.COD_DEATH, SoundEvents.COD_FLOP),
		SALMON(SoundEvents.SALMON_AMBIENT, SoundEvents.SALMON_HURT, SoundEvents.SALMON_DEATH, SoundEvents.SALMON_FLOP),
		TROPICAL(SoundEvents.TROPICAL_FISH_AMBIENT, SoundEvents.TROPICAL_FISH_HURT, SoundEvents.TROPICAL_FISH_DEATH,
				SoundEvents.TROPICAL_FISH_FLOP),
		/** (The pufferfish has no ambient sound of its own; the cod's bubbling stands in.) */
		PUFFER(SoundEvents.COD_AMBIENT,SoundEvents.PUFFER_FISH_HURT, SoundEvents.PUFFER_FISH_DEATH, SoundEvents.PUFFER_FISH_FLOP);

		final SoundEvent ambient;
		final SoundEvent hurt;
		final SoundEvent death;
		final SoundEvent flop;

		Voice(SoundEvent ambient, SoundEvent hurt, SoundEvent death, SoundEvent flop) {
			this.ambient = ambient;
			this.hurt = hurt;
			this.death = death;
			this.flop = flop;
		}
	}

	private static final Map<EntityType<?>, FishKind> BY_TYPE = new HashMap<>();

	// --- Rivers and lakes ---------------------------------------------------------------------------------------
	public static final FishKind BROOK_TROUT = kind("brook_trout", Fishes.BROOK_TROUT, 0.47f, 0.41f, Voice.SALMON)
			.spawns(rivers().or(BiomeSelectors.tag(BiomeTags.IS_FOREST)).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA))
					.or(BiomeSelectors.includeByKey(Biomes.MEADOW, Biomes.CHERRY_GROVE)), 3, 1, 2, Depth.SURFACE);
	public static final FishKind MUD_CATFISH = kind("mud_catfish", Fishes.MUD_CATFISH, 0.56f, 0.37f, Voice.COD).health(5)
			.spawns(rivers().or(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
					Biomes.SAVANNA)), 3, 1, 2, Depth.SURFACE);
	public static final FishKind PIKE = kind("pike", Fishes.PIKE, 0.62f, 0.38f, Voice.SALMON).health(5)
			.spawns(rivers().or(BiomeSelectors.tag(BiomeTags.IS_FOREST)).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA)), 2, 1, 1,
					Depth.SURFACE);

	// --- Ocean ----------------------------------------------------------------------------------------------------
	public static final FishKind MACKEREL = kind("mackerel", Fishes.MACKEREL, 0.41f, 0.26f, Voice.COD).school(8)
			.spawns(BiomeSelectors.tag(BiomeTags.IS_OCEAN), 5, 3, 6, Depth.SURFACE);
	/** As long as a large salmon, and rare. */
	public static final FishKind SWORDFISH = kind("swordfish", Fishes.SWORDFISH, 1.19f, 0.79f, Voice.SALMON).health(10)
			.spawns(warmOcean().or(BiomeSelectors.includeByKey(Biomes.DEEP_OCEAN)), 1, 1, 1, Depth.SURFACE).time(Time.DAY);
	public static final FishKind ANGLERFISH = kind("anglerfish", Fishes.ANGLERFISH, 0.45f, 0.4f, Voice.PUFFER).health(4)
			.spawns(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN), 2, 1, 1, Depth.DEEP).time(Time.NIGHT);
	public static final FishKind GLOWFIN_TETRA = kind("glowfin_tetra", Fishes.GLOWFIN_TETRA, 0.3f, 0.21f, Voice.TROPICAL).school(8)
			.spawns(warmOcean().or(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE,
					Biomes.LUSH_CAVES)), 4, 3, 6, Depth.ANY);
	public static final FishKind PARROTFISH = kind("parrotfish", Fishes.PARROTFISH, 0.47f, 0.4f, Voice.TROPICAL)
			.spawns(warmOcean(), 4, 1, 3, Depth.SURFACE).time(Time.DAY);

	// --- Cold, swamps and caves -----------------------------------------------------------------------------------
	public static final FishKind ICE_CHAR = kind("ice_char", Fishes.ICE_CHAR, 0.47f, 0.43f, Voice.SALMON)
			.spawns(BiomeSelectors.includeByKey(Biomes.FROZEN_RIVER, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.COLD_OCEAN,
					Biomes.DEEP_COLD_OCEAN, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.ICE_SPIKES, Biomes.GROVE, Biomes.SNOWY_BEACH,
					Biomes.SNOWY_SLOPES), 4, 1, 3, Depth.SURFACE);
	public static final FishKind BOG_EEL = kind("bog_eel", Fishes.BOG_EEL, 0.69f, 0.28f, Voice.SALMON).health(4)
			.spawns(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP), 4, 1, 2, Depth.SURFACE);
	public static final FishKind CAVE_BLINDFISH = kind("cave_blindfish", Fishes.CAVE_BLINDFISH, 0.3f, 0.21f, Voice.COD).school(5)
			.spawns(BiomeSelectors.includeByKey(Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES), 6, 2, 4, Depth.LOW).maxY(40);
	public static final FishKind SCULK_GULPER = kind("sculk_gulper", Fishes.SCULK_GULPER, 0.47f, 0.29f, Voice.PUFFER).health(6)
			.spawns(BiomeSelectors.includeByKey(Biomes.DEEP_DARK), 5, 1, 1, Depth.LOW).maxY(-20);

	// --- The Feywild ----------------------------------------------------------------------------------------------
	public static final FishKind PIXIE_MINNOW = kind("pixie_minnow", Fishes.PIXIE_MINNOW, 0.3f, 0.24f, Voice.TROPICAL).school(8)
			.spawns(feywild(), 10, 3, 6, Depth.ANY);
	/** Night-only on the line, but the Feywild's dusk never ends: in its ponds it's always about. */
	public static final FishKind MOONSCALE_KOI = kind("moonscale_koi", Fishes.MOONSCALE_KOI, 0.53f, 0.38f, Voice.TROPICAL).health(5)
			.spawns(feywild(), 2, 1, 1, Depth.ANY).time(Time.NIGHT);

	// --- Lava -----------------------------------------------------------------------------------------------------
	public static final FishKind MAGMA_EEL = kind("magma_eel", Fishes.MAGMA_EEL, 0.69f, 0.26f, Voice.SALMON).health(6).lava()
			.spawns(BiomeSelectors.tag(BiomeTags.IS_NETHER), 6, 1, 2, Depth.LAVA);
	public static final FishKind EMBER_PUFFER = kind("ember_puffer", Fishes.EMBER_PUFFER, 0.45f, 0.4f, Voice.PUFFER).lava()
			.spawns(BiomeSelectors.includeByKey(Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.CRIMSON_FOREST), 5, 1, 2, Depth.LAVA);

	// --- The invasion ---------------------------------------------------------------------------------------------
	/** In the water down among the uranium, under any Overworld biome. */
	public static final FishKind GLOWING_MUTANT_CARP = kind("glowing_mutant_carp", Fishes.GLOWING_MUTANT_CARP, 0.5f, 0.41f, Voice.COD)
			.health(5).spawns(BiomeSelectors.foundInOverworld(), 2, 1, 2, Depth.LOW).maxY(0);
	public static final FishKind STARLIT_GAR = kind("starlit_gar", Fishes.STARLIT_GAR, 0.75f, 0.3f, Voice.SALMON).health(6)
			.spawns(rivers().or(BiomeSelectors.tag(BiomeTags.IS_OCEAN)), 1, 1, 1, Depth.SURFACE).time(Time.NIGHT).weather(Weather.CLEAR);
	public static final FishKind ABYSSAL_SQUIDLING = kind("abyssal_squidling", Fishes.ABYSSAL_SQUIDLING, 0.5f, 0.27f, Voice.PUFFER)
			.health(6).spawns(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN), 1, 1, 1, Depth.DEEP).time(Time.NIGHT);
	/** Only out on thundering nights, in any water near the surface. */
	public static final FishKind GHOULFISH = kind("ghoulfish", Fishes.GHOULFISH, 0.47f, 0.32f, Voice.COD).health(4)
			.spawns(BiomeSelectors.foundInOverworld(), 2, 1, 2, Depth.SURFACE).time(Time.NIGHT).weather(Weather.THUNDER);

	public static final List<FishKind> ALL = List.of(BROOK_TROUT, MUD_CATFISH, PIKE, MACKEREL, SWORDFISH, ANGLERFISH, GLOWFIN_TETRA,
			PARROTFISH, ICE_CHAR, BOG_EEL, CAVE_BLINDFISH, SCULK_GULPER, PIXIE_MINNOW, MOONSCALE_KOI, MAGMA_EEL, EMBER_PUFFER,
			GLOWING_MUTANT_CARP, STARLIT_GAR, ABYSSAL_SQUIDLING, GHOULFISH);

	private final String id;
	private final Item catchItem;
	private final float width;
	private final float height;
	private final Voice voice;
	private double health = 3.0;
	private int school = 1;
	private boolean lava;
	private Predicate<BiomeSelectionContext> biomes;
	private int weight;
	private int minGroup;
	private int maxGroup;
	private Depth depth;
	private int maxY = Integer.MAX_VALUE;
	private Time time = Time.ANY;
	private Weather weather = Weather.ANY;

	private EntityType<? extends ModFish> type;
	private Item bucket;
	private Item spawnEgg;

	private FishKind(String id, Item catchItem, float width, float height, Voice voice) {
		this.id = id;
		this.catchItem = catchItem;
		this.width = width;
		this.height = height;
		this.voice = voice;
	}

	private static FishKind kind(String id, Item catchItem, float width, float height, Voice voice) {
		return new FishKind(id, catchItem, width, height, voice);
	}

	private static Predicate<BiomeSelectionContext> rivers() {
		return BiomeSelectors.tag(BiomeTags.IS_RIVER);
	}

	private static Predicate<BiomeSelectionContext> warmOcean() {
		return BiomeSelectors.includeByKey(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN);
	}

	private static Predicate<BiomeSelectionContext> feywild() {
		return BiomeSelectors.includeByKey(biome("feywild_glade"), biome("feywild_mushroom_wilds"));
	}

	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, MyMod.id(name));
	}

	private FishKind health(double health) {
		this.health = health;
		return this;
	}

	/** Schools of up to this many, like cod and tropical fish. */
	private FishKind school(int size) {
		this.school = size;
		return this;
	}

	private FishKind lava() {
		this.lava = true;
		return this;
	}

	private FishKind spawns(Predicate<BiomeSelectionContext> biomes, int weight, int minGroup, int maxGroup, Depth depth) {
		this.biomes = biomes;
		this.weight = weight;
		this.minGroup = minGroup;
		this.maxGroup = maxGroup;
		this.depth = depth;
		return this;
	}

	private FishKind maxY(int maxY) {
		this.maxY = maxY;
		return this;
	}

	private FishKind time(Time time) {
		this.time = time;
		return this;
	}

	private FishKind weather(Weather weather) {
		this.weather = weather;
		return this;
	}

	void bind(EntityType<? extends ModFish> type, Item bucket, Item spawnEgg) {
		this.type = type;
		this.bucket = bucket;
		this.spawnEgg = spawnEgg;
		BY_TYPE.put(type, this);
	}

	/** The kind of a fish entity type (every {@link ModFish} has one). */
	public static FishKind of(EntityType<?> type) {
		return BY_TYPE.get(type);
	}

	/**
	 * Whether this fish may spawn at pos (the placement type has already checked it's in water, or lava): its depth,
	 * and its time of day and weather as its fishing species has them.
	 */
	public boolean canSpawnAt(ServerLevelAccessor level, BlockPos pos) {
		int seaLevel = level.getSeaLevel();
		boolean placed = switch (depth) {
			case SURFACE -> pos.getY() >= seaLevel - 13 && pos.getY() <= seaLevel && level.getFluidState(pos.below()).is(FluidTags.WATER)
					&& level.getBlockState(pos.above()).is(Blocks.WATER);
			case DEEP -> pos.getY() < seaLevel - 13 && level.getBlockState(pos.above()).is(Blocks.WATER);
			case ANY -> true;
			case LOW -> pos.getY() <= maxY;
			case LAVA -> level.getFluidState(pos).is(FluidTags.LAVA);
		};
		if (!placed) {
			return false;
		}
		ServerLevel world = level.getLevel();
		boolean timeOk = world.dimensionType().hasFixedTime() || switch (time) {
			case ANY -> true;
			case DAY -> world.isBrightOutside();
			case NIGHT -> world.isDarkOutside();
		};
		boolean weatherOk = switch (weather) {
			case ANY -> true;
			case CLEAR -> !world.isRaining();
			case THUNDER -> world.isThundering();
		};
		return timeOk && weatherOk;
	}

	public String id() {
		return id;
	}

	public Item catchItem() {
		return catchItem;
	}

	public float width() {
		return width;
	}

	public float height() {
		return height;
	}

	public double health() {
		return health;
	}

	/** The most that school together; 1 for loners. */
	public int schoolSize() {
		return school;
	}

	public boolean schools() {
		return school > 1;
	}

	public boolean isLava() {
		return lava;
	}

	public Predicate<BiomeSelectionContext> biomes() {
		return biomes;
	}

	public int weight() {
		return weight;
	}

	public int minGroup() {
		return minGroup;
	}

	public int maxGroup() {
		return maxGroup;
	}

	public Depth depth() {
		return depth;
	}

	public Voice voice() {
		return voice;
	}

	public EntityType<? extends ModFish> type() {
		return type;
	}

	public Item bucket() {
		return bucket;
	}

	public Item spawnEgg() {
		return spawnEgg;
	}
}
