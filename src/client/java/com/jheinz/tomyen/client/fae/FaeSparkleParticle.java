package com.jheinz.tomyen.client.fae;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * Fae glitter: vanilla's end rod twinkle (its sprites, see particles/fae_sparkle.json), small, full bright, in violet,
 * teal or pink, drifting and fading to white.
 */
public class FaeSparkleParticle extends SimpleAnimatedParticle {
	private static final int[] COLOURS = {0xC08CFF, 0x6FF0E0, 0xFF9EE6};

	private FaeSparkleParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
		super(level, x, y, z, sprites, -0.002f);
		xd = xa;
		yd = ya;
		zd = za;
		quadSize *= 0.5f;
		lifetime = 30 + random.nextInt(25);
		setColor(COLOURS[random.nextInt(COLOURS.length)]);
		setFadeColor(0xF6FFFE);
		setSpriteFromAge(sprites);
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux,
				double yAux, double zAux, RandomSource random) {
			return new FaeSparkleParticle(level, x, y, z, xAux, yAux, zAux, sprites);
		}
	}
}
