package com.jheinz.mymod.client.drill;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A spark struck off a drill bit: vanilla's firework spark (its sprites, see particles/drill_spark.json), tiny, full
 * bright and yellow-white, flying out, falling and cooling to orange.
 */
public class DrillSparkParticle extends SimpleAnimatedParticle {
	private DrillSparkParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
		super(level, x, y, z, sprites, 0.9f);
		xd = xa;
		yd = ya;
		zd = za;
		friction = 0.96f;
		quadSize *= 0.35f;
		lifetime = 6 + random.nextInt(8);
		setColor(0xFFE9A0);
		setFadeColor(0xFF6A1C);
		setSpriteFromAge(sprites);
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux,
				double yAux, double zAux, RandomSource random) {
			return new DrillSparkParticle(level, x, y, z, xAux, yAux, zAux, sprites);
		}
	}
}
