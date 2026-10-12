package com.jheinz.mymod.client.schnozzle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

/** Where a whip cracks: a white shock ring that bursts outward and fades over a few ticks, full bright. */
public class WhipCrackParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	private WhipCrackParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		lifetime = 5;
		quadSize = 0.3f;
		gravity = 0.0f;
		hasPhysics = false;
		roll = oRoll = random.nextFloat() * (float) Math.PI * 2.0f;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
			return;
		}
		setSpriteFromAge(sprites);
		quadSize *= 1.3f;
		setAlpha(1.0f - (float) age / lifetime);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux,
				double yAux, double zAux, RandomSource random) {
			return new WhipCrackParticle(level, x, y, z, sprites);
		}
	}
}
