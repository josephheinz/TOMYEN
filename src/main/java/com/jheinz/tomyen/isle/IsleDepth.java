package com.jheinz.tomyen.isle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import com.jheinz.tomyen.MyMod;

/**
 * How deep into the Isle of the Damned a hoard lies. The Isle is laid out in rings round the arrival shrine at the
 * origin: the Shore close in, the Hollows past it and the Abyss at the edge (the biomes follow the same rings, see
 * {@code data/tomyen/dimension/isle.json}). Dying on the Isle drags a hoard one depth deeper, where its Revenant comes
 * back tougher; the Abyss is as deep as it goes.
 *
 * <p>{@link #arenaDistance} is how far out from the origin a Revenant's arena is built at this depth, well inside the
 * ring so the arena always stands in its own biome.
 */
public enum IsleDepth {
	SHORE(0, "shore", 0, 175, 84, 1.0f, 0.6f, 0.0f),
	HOLLOWS(1, "hollows", 175, 380, 270, 1.5f, 0.75f, 0.1f),
	ABYSS(2, "abyss", 380, Integer.MAX_VALUE, 500, 2.0f, 0.9f, 0.2f);

	public static final IsleDepth DEEPEST = ABYSS;

	public final int index;
	public final String name;
	/** The ring this depth covers, in blocks from the origin (horizontally). */
	public final int innerRadius, outerRadius;
	public final int arenaDistance;
	/**
	 * A Revenant's health and damage are multiplied by these at this depth; its speed raised by this fraction. Its damage
	 * stays below its gear's full strength (its owner comes back to it with less than they died with), less so the deeper.
	 */
	public final float healthMultiplier, damageMultiplier, speedBonus;
	public final ResourceKey<Biome> biome;

	IsleDepth(int index, String name, int innerRadius, int outerRadius, int arenaDistance, float healthMultiplier, float damageMultiplier,
			float speedBonus) {
		this.index = index;
		this.name = name;
		this.innerRadius = innerRadius;
		this.outerRadius = outerRadius;
		this.arenaDistance = arenaDistance;
		this.healthMultiplier = healthMultiplier;
		this.damageMultiplier = damageMultiplier;
		this.speedBonus = speedBonus;
		this.biome = ResourceKey.create(Registries.BIOME, MyMod.id("isle_" + name));
	}

	public static IsleDepth of(int index) {
		return values()[Math.max(0, Math.min(index, DEEPEST.index))];
	}

	/** One depth further down; the Abyss stays the Abyss. */
	public IsleDepth deeper() {
		return of(index + 1);
	}

	public Component displayName() {
		return Component.translatable("isle.tomyen.depth." + name);
	}

	/** Which depth a spot on the Isle is in: by its biome, or (for a biome of some other pack) by its distance out. */
	public static IsleDepth at(Level level, BlockPos pos) {
		Holder<Biome> here = level.getBiome(pos);
		for (IsleDepth depth : values()) {
			if (here.is(depth.biome)) {
				return depth;
			}
		}
		return byDistance(pos);
	}

	public static IsleDepth byDistance(BlockPos pos) {
		double distance = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ());
		for (IsleDepth depth : values()) {
			if (distance < depth.outerRadius) {
				return depth;
			}
		}
		return DEEPEST;
	}
}
