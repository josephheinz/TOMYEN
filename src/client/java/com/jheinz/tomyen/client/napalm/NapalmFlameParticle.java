package com.jheinz.tomyen.client.napalm;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;

import com.jheinz.tomyen.napalm.FlameStream;

/**
 * A ball of burning napalm in the stream. It flies with exactly the physics of the server's globs ({@link FlameStream}:
 * the same drag and fall, stopping against blocks), swelling and cooling from white-hot to a dull smoky red as it
 * goes, glowing until it's nearly out. The spawner gives its velocity in the aux arguments, untouched.
 */
public class NapalmFlameParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private final float startSize;
	private final float endSize;

	private NapalmFlameParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		// Particle physics: fall 0.04 * gravity a tick, then slow by friction, as the globs do.
		gravity = (float) (FlameStream.GRAVITY / 0.04);
		friction = (float) FlameStream.DRAG;
		hasPhysics = true;
		lifetime = FlameStream.LIFETIME - 2 + random.nextInt(5);
		startSize = 0.1f + random.nextFloat() * 0.06f;
		endSize = 0.55f + random.nextFloat() * 0.25f;
		roll = oRoll = random.nextFloat() * (float) (Math.PI * 2.0);
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		if (!isAlive()) {
			return;
		}
		setSpriteFromAge(sprites);
		float t = age / (float) lifetime;
		setAlpha(t < 0.7f ? 1.0f : 1.0f - (t - 0.7f) / 0.3f);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = Math.min(1.0f, (age + partialTick) / lifetime);
		return startSize + (endSize - startSize) * (float) Math.sqrt(t);
	}

	@Override
	protected int getLightCoords(float partialTick) {
		float t = (age + partialTick) / lifetime;
		return t < 0.75f ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux,
				double yAux, double zAux, RandomSource random) {
			return new NapalmFlameParticle(level, x, y, z, xAux, yAux, zAux, sprites);
		}
	}
}
