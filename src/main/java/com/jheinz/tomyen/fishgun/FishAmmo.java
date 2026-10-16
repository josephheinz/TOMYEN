package com.jheinz.tomyen.fishgun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import com.jheinz.tomyen.ModEffects;
import com.jheinz.tomyen.fishing.FishSize;
import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.FishingSpecies;
import com.jheinz.tomyen.fishing.TrophyFish;
import com.jheinz.tomyen.fishing.fish.Fishes;
import com.jheinz.tomyen.uranium.Radiation;

/**
 * Fish as Fish Gun ammunition: which raw fish count, what one weighs, how a shot of that weight flies and hits, what the
 * odd species do to whatever they hit, and what's left of a fish afterwards.
 *
 * <p>Ammo is any fish with a weighed fishing species (its species file has a {@code size}): every mod fish and vanilla's
 * cod, salmon, pufferfish and tropical fish. A trophy weighs what its {@link TrophyFish} says; an ordinary catch weighs
 * its species' typical fish. Everything scales off that weight on a log curve, so a 0.006 lb glowfin tetra is a
 * pea-shooter (1 damage, a nudge), a 20 lb pike a solid thump (about 9 damage), a 150 lb tuna a haymaker and a 400 lb
 * trophy tuna a cannonball (about 19 damage, hurled back several blocks, bowling through three mobs and going off in a
 * block-safe shockwave). Heavy fish leave slower and drop harder (they're lobbed in an arc) and take longer to load.
 *
 * <p>Picking: the fire-mode key steps through the kinds the holder carries, heaviest first ({@link #cycle}); with nothing
 * picked the gun fires the heaviest ordinary fish. Trophies are their own entries and are only ever fired when picked.
 */
public final class FishAmmo {
	/** At or over this weight a fish is a cannonball: it pierces and lands in a shockwave. */
	public static final float CANNONBALL_LB = 100.0f;
	private static final double HEFT_SCALE = Math.log10(1501.0);
	private static final float BONE_MEAL_CHANCE = 0.2f;
	private static final float BRUISED_CHANCE = 0.35f;

	/** Fish that arrive on fire, and land cooked where they have a cooked form. */
	private static final Set<Item> FIERY = Set.of(Fishes.EMBER_PUFFER, Fishes.MAGMA_EEL, Fishes.COMETFISH);

	private FishAmmo() {
	}

	// ------------------------------------------------------------------------------------------------------ ballistics

	/** 0 for a minnow to 1 for a record 1500 lb tuna, on a log scale: how heavy a fish feels. */
	public static float heft(float weight) {
		return (float) Mth.clamp(Math.log10(1.0 + Math.max(0.0f, weight)) / HEFT_SCALE, 0.0, 1.0);
	}

	public static float damage(float weight) {
		return (float) Math.min(30.0, 1.0 + 2.5 * Math.log(1.0 + weight) + weight / 120.0);
	}

	/** Extra speed a hit adds to the target, along the fish's flight (blocks a tick, before knockback resistance). */
	public static float knockback(float weight) {
		return (float) Math.min(4.0, 0.15 + 0.11 * Math.sqrt(weight));
	}

	/** Muzzle velocity, blocks a tick. */
	public static float speed(float weight) {
		return 3.0f - 1.6f * heft(weight);
	}

	/** Gravity, blocks a tick squared: a minnow flies nearly flat, a cannonball arcs. */
	public static float gravity(float weight) {
		float h = heft(weight);
		return 0.02f + 0.06f * h * h;
	}

	/** Ticks to stuff the next fish down the barrel and recharge the tank. */
	public static int loadTicks(float weight) {
		return Math.round(4.0f + 52.0f * (float) Math.pow(heft(weight), 1.5));
	}

	/** Recoil, degrees of view kick. */
	public static float recoil(float weight) {
		return 1.0f + 10.0f * heft(weight);
	}

	/** How many more mobs a shot bowls on through after the first. */
	public static int pierce(Item fish, float weight) {
		int bowl = weight >= CANNONBALL_LB ? Math.min(4, 1 + (int) (weight / 150.0f)) : 0;
		return fish == Fishes.SWORDFISH ? bowl + 3 : bowl;
	}

	/** The radius of the shockwave a cannonball lands in, or 0. */
	public static float shockwave(float weight) {
		if (weight < CANNONBALL_LB) {
			return 0.0f;
		}
		return 2.0f + 2.5f * Mth.clamp((heft(weight) - 0.63f) / 0.37f, 0.0f, 1.0f);
	}

	/** The HUD's word for a shot of this weight. */
	public static String weightClass(float weight) {
		if (weight < 1.0f) {
			return "pea";
		} else if (weight < 10.0f) {
			return "slap";
		} else if (weight < 50.0f) {
			return "thump";
		} else if (weight < CANNONBALL_LB) {
			return "haymaker";
		}
		return "cannonball";
	}

	// ----------------------------------------------------------------------------------------------------------- ammo

	/** The species file of a fish that can be fired, or null. Server side (species files are server data). */
	public static @Nullable FishSize size(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		FishingSpecies species = FishingSpecies.forItem(stack.getItem());
		return species == null ? null : species.size();
	}

	public static boolean isAmmo(ItemStack stack) {
		return size(stack) != null;
	}

	/** What this fish weighs: a trophy its own weight, anything else its species' typical weight. Server side. */
	public static float weight(ItemStack stack) {
		TrophyFish trophy = stack.get(Fishing.TROPHY);
		if (trophy != null) {
			return trophy.weight();
		}
		FishSize size = size(stack);
		return size == null ? 1.0f : size.avg();
	}

	/** The typical weight of this fish's species (a trophy's as recorded when it was caught). */
	public static float average(ItemStack stack) {
		TrophyFish trophy = stack.get(Fishing.TROPHY);
		if (trophy != null) {
			return trophy.averageWeight();
		}
		FishSize size = size(stack);
		return size == null ? 1.0f : size.avg();
	}

	/** One kind of ammo in an inventory: the slot of its heaviest fish, that fish's weight, and how many there are. */
	public record Entry(Item fish, boolean trophy, int slot, float weight, int count) {
		boolean is(LoadedFish.Choice choice) {
			return choice != null && choice.fish().value() == fish && choice.trophy() == trophy;
		}

		public LoadedFish loaded() {
			return new LoadedFish(fish.builtInRegistryHolder(), weight, trophy, count);
		}
	}

	/** Every kind of fish the player carries, heaviest first. */
	public static List<Entry> entries(Player player) {
		Map<String, Entry> byKind = new LinkedHashMap<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!isAmmo(stack)) {
				continue;
			}
			boolean trophy = stack.has(Fishing.TROPHY);
			float weight = weight(stack);
			String key = stack.getItem() + (trophy ? "#trophy" : "");
			Entry old = byKind.get(key);
			int count = (old == null ? 0 : old.count()) + stack.getCount();
			if (old == null || weight > old.weight()) {
				byKind.put(key, new Entry(stack.getItem(), trophy, i, weight, count));
			} else {
				byKind.put(key, new Entry(old.fish(), trophy, old.slot(), old.weight(), count));
			}
		}
		List<Entry> out = new ArrayList<>(byKind.values());
		out.sort(Comparator.comparingDouble(Entry::weight).reversed());
		return out;
	}

	/**
	 * What the gun fires next: the picked kind if the player still has one, else the heaviest ordinary fish. Null when
	 * there's nothing (or only trophies nobody picked). In creative, with no fish at all, a typical cod.
	 */
	public static @Nullable Entry select(Player player, ItemStack gun) {
		List<Entry> entries = entries(player);
		LoadedFish.Choice choice = gun.get(FishGuns.FISH_CHOICE);
		for (Entry entry : entries) {
			if (entry.is(choice)) {
				return entry;
			}
		}
		for (Entry entry : entries) {
			if (!entry.trophy()) {
				return entry;
			}
		}
		if (entries.isEmpty() && player.hasInfiniteMaterials()) {
			ItemStack cod = new ItemStack(Items.COD);
			return new Entry(Items.COD, false, -1, weight(cod), 0);
		}
		return null;
	}

	/** Steps the pick on to the next lighter kind the player carries (round to the heaviest after the lightest). */
	public static @Nullable Entry cycle(Player player, ItemStack gun) {
		List<Entry> entries = entries(player);
		if (entries.isEmpty()) {
			return null;
		}
		Entry current = select(player, gun);
		int at = current == null ? -1 : entries.indexOf(current);
		Entry next = entries.get((at + 1) % entries.size());
		gun.set(FishGuns.FISH_CHOICE, new LoadedFish.Choice(next.fish().builtInRegistryHolder(), next.trophy()));
		return next;
	}

	// -------------------------------------------------------------------------------------------------------- effects

	public static boolean fiery(Item fish) {
		return FIERY.contains(fish);
	}

	/** The species' party trick on whatever it hits. */
	public static void applyEffects(ServerLevel level, Item fish, Entity target) {
		if (fiery(fish)) {
			target.igniteForSeconds(fish == Fishes.COMETFISH ? 3.0f : 7.0f);
		}
		if (!(target instanceof LivingEntity living)) {
			return;
		}
		if (fish == Items.PUFFERFISH || fish == Fishes.EMBER_PUFFER) {
			living.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
			living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120));
			burst(level, target, ParticleTypes.ITEM_SLIME, 12);
		} else if (fish == Fishes.ANGLERFISH || fish == Fishes.STARLIT_GAR) {
			// The lure goes off like a flashbulb: the target glows for a while.
			living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240));
			burst(level, target, ParticleTypes.GLOW, 20);
		} else if (fish == Fishes.GLOWING_MUTANT_CARP) {
			if (living instanceof ServerPlayer player) {
				Radiation.irradiate(player, Radiation.MINING_DOSE / 2);
			} else {
				living.addEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, 200));
			}
			living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120));
			burst(level, target, ParticleTypes.HAPPY_VILLAGER, 14);
		} else if (fish == Fishes.GHOULFISH) {
			living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
			burst(level, target, ParticleTypes.SOUL, 10);
		} else if (fish == Fishes.CAVE_BLINDFISH) {
			living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80));
		} else if (fish == Fishes.ICE_CHAR) {
			living.setTicksFrozen(living.getTicksRequiredToFreeze() + 120);
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
			burst(level, target, ParticleTypes.SNOWFLAKE, 16);
		} else if (fish == Fishes.SCULK_GULPER) {
			living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120));
			burst(level, target, ParticleTypes.SCULK_SOUL, 8);
		} else if (fish == Fishes.PIXIE_MINNOW) {
			living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30));
		} else if (fish == Fishes.ABYSSAL_SQUIDLING) {
			living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80));
			burst(level, target, ParticleTypes.SQUID_INK, 16);
		} else if (fish == Fishes.BOG_EEL) {
			living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200));
		}
	}

	private static void burst(ServerLevel level, Entity target, net.minecraft.core.particles.ParticleOptions particle, int count) {
		level.sendParticles(particle, target.getX(), target.getY(0.6), target.getZ(), count, target.getBbWidth() * 0.4,
				target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.05);
	}

	// ---------------------------------------------------------------------------------------------------- what's left

	/**
	 * What's left of a fired fish where it lands. A trophy always comes back, bruised (a line of lore). A fiery fish, or one
	 * that hit something burning, arrives cooked if it has a cooked form. Otherwise it may bounce off bruised but whole
	 * ({@link #BRUISED_CHANCE}), leave its bones (bone meal), or just splat. Empty for a splat.
	 */
	public static ItemStack remains(ServerLevel level, ItemStack fish, boolean scorched) {
		if (fish.has(Fishing.TROPHY)) {
			return bruised(fish.copyWithCount(1));
		}
		if (scorched || fiery(fish.getItem())) {
			ItemStack cooked = cooked(level, fish);
			if (!cooked.isEmpty()) {
				return cooked;
			}
		}
		float roll = level.getRandom().nextFloat();
		if (roll < BRUISED_CHANCE) {
			return bruised(fish.copyWithCount(1));
		} else if (roll < BRUISED_CHANCE + BONE_MEAL_CHANCE) {
			return new ItemStack(Items.BONE_MEAL);
		}
		return ItemStack.EMPTY;
	}

	/** The fish with the "bruised" line of lore (once). */
	public static ItemStack bruised(ItemStack fish) {
		Component line = Component.translatable("item.tomyen.fish_gun.bruised").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
		ItemLore lore = fish.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		if (!lore.lines().contains(line)) {
			List<Component> lines = new ArrayList<>(lore.lines());
			lines.add(line);
			fish.set(DataComponents.LORE, new ItemLore(lines));
		}
		return fish;
	}

	private static ItemStack cooked(ServerLevel level, ItemStack fish) {
		SingleRecipeInput input = new SingleRecipeInput(fish.copyWithCount(1));
		return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
	}
}
