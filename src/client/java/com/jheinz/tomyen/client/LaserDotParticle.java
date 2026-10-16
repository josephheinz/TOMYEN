package com.jheinz.tomyen.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A laser sight's dot: full bright, motionless, and gone after one tick (a new one replaces it each tick). */
public class LaserDotParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;

	private LaserDotParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		quadSize = 0.035f;
		lifetime = 0;
		gravity = 0.0f;
		hasPhysics = false;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return FULL_BRIGHT;
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux,
				double yAux, double zAux, RandomSource random) {
			return new LaserDotParticle(level, x, y, z, sprites);
		}
	}
}
