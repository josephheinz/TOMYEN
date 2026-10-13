package com.jheinz.mymod.client.napalm;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The shimmer of hot air over the stream: a faint, wavering ring that drifts up and spreads, fading as it goes.
 * Minecraft can't bend the view behind it, so it only hints at haze. Takes its starting drift in the aux arguments.
 */
public class HeatHazeParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	private HeatHazeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = xd;
		this.yd = yd + 0.03;
		this.zd = zd;
		gravity = -0.3f;
		friction = 0.9f;
		hasPhysics = false;
		lifetime = 14 + random.nextInt(10);
		quadSize = 0.3f + random.nextFloat() * 0.3f;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		if (!isAlive()) {
			return;
		}
		setSpriteFromAge(sprites);
		quadSize *= 1.04f;
		setAlpha(1.0f - age / (float) lifetime);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux,
				double yAux, double zAux, RandomSource random) {
			return new HeatHazeParticle(level, x, y, z, xAux, yAux, zAux, sprites);
		}
	}
}
