package com.jheinz.tomyen.isle.worldgen;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import com.jheinz.tomyen.MyMod;

/** The Isle's feature types; the configured and placed features themselves are data ({@code worldgen/feature/isle_*.json}). */
public final class IsleFeatures {
	public static final MapCodec<IsleRuinFeature> ISLE_RUIN = Registry.register(BuiltInRegistries.FEATURE_TYPE, MyMod.id("isle_ruin"),
			IsleRuinFeature.CODEC);

	private IsleFeatures() {
	}

	public static void initialize() {
	}
}
