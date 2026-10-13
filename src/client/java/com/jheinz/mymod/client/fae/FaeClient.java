package com.jheinz.mymod.client.fae;

import java.util.List;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.state.BlockState;

import com.jheinz.mymod.fae.Fae;

/** The fairy's renderer and model, the fae sparkle, and the Fae Grass Block's tint: the biome's grass, brightened. */
public final class FaeClient {
	/** How far Fae Grass is pulled toward a fresh spring green. */
	private static final float BRIGHTEN = 0.35f;
	private static final int SPRING = 0xFFB8FF9A;

	private FaeClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(Fae.FAIRY, FairyRenderer::new);
		ModelLayerRegistry.registerModelLayer(FairyModel.LAYER, FairyModel::createBodyLayer);
		ParticleProviderRegistry.getInstance().register(Fae.SPARKLE, FaeSparkleParticle.Provider::new);
		BlockColorRegistry.register(List.of(new BlockTintSource() {
			@Override
			public int color(BlockState state) {
				return brighten(GrassColor.getDefaultColor());
			}

			@Override
			public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
				return brighten(BiomeColors.getAverageGrassColor(level, pos));
			}
		}), Fae.FAE_GRASS_BLOCK);
	}

	private static int brighten(int colour) {
		return ARGB.srgbLerp(BRIGHTEN, colour, SPRING);
	}
}
