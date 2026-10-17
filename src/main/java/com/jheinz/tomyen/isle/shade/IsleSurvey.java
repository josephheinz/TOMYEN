package com.jheinz.tomyen.isle.shade;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import com.jheinz.tomyen.isle.IsleDepth;

/**
 * {@code /isle survey [step]}, for tuning the Isle's terrain: a map of the whole Isle from its generator alone (no
 * chunks are made), one pixel every {@code step} blocks, written to {@code isle_survey.png} in the server's folder,
 * with each ring's share of dry land and its highest and deepest points told in chat. Water is blue, darker the deeper;
 * land grey, lighter the higher, tinted by ring; the rings' edges are drawn in.
 */
final class IsleSurvey {
	private static final int RADIUS = 700;

	private IsleSurvey() {
	}

	static int survey(ServerLevel isle, int step, java.util.function.Consumer<Component> out) throws IOException {
		ChunkGenerator generator = isle.getChunkSource().getGenerator();
		RandomState random = isle.getChunkSource().randomState();
		int sea = isle.getSeaLevel();
		int size = RADIUS * 2 / step;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		int[] land = new int[3], total = new int[3], high = new int[3], low = {999, 999, 999};
		long start = System.nanoTime();
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				int x = -RADIUS + px * step, z = -RADIUS + pz * step;
				double distance = Math.sqrt((double) x * x + (double) z * z);
				if (distance > RADIUS) {
					image.setRGB(px, pz, 0x101010);
					continue;
				}
				int ring = distance < IsleDepth.SHORE.outerRadius ? 0 : distance < IsleDepth.HOLLOWS.outerRadius ? 1 : 2;
				int y = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, isle, random);
				total[ring]++;
				high[ring] = Math.max(high[ring], y);
				low[ring] = Math.min(low[ring], y);
				int colour;
				if (y <= sea) {
					int depth = Math.min(40, sea - y);
					int b = 150 - depth * 3;
					colour = (b / 5) << 16 | (b / 2) << 8 | b;
				} else {
					land[ring]++;
					int v = Math.min(255, 70 + (y - sea) * 5);
					int[] tint = ring == 0 ? new int[] {100, 100, 96} : ring == 1 ? new int[] {80, 100, 100} : new int[] {96, 84, 108};
					colour = (v * tint[0] / 100) << 16 | (v * tint[1] / 100) << 8 | (v * tint[2] / 100);
				}
				if (Math.abs(distance - IsleDepth.SHORE.outerRadius) < step * 0.6 || Math.abs(distance - IsleDepth.HOLLOWS.outerRadius) < step * 0.6) {
					colour = 0xC03030;
				}
				image.setRGB(px, pz, colour);
			}
		}
		Path path = isle.getServer().getServerDirectory().resolve("isle_survey.png");
		ImageIO.write(image, "png", path.toFile());
		long ms = (System.nanoTime() - start) / 1_000_000;
		String[] names = {"Shore", "Hollows", "Abyss"};
		for (int i = 0; i < 3; i++) {
			out.accept(Component.literal(String.format("%s: %.0f%% land, ground from y %d to %d", names[i], 100.0 * land[i] / Math.max(1, total[i]),
					low[i], high[i])));
		}
		out.accept(Component.literal("Survey written to " + path + " in " + ms + " ms"));
		return land[0] + land[1] + land[2];
	}
}
