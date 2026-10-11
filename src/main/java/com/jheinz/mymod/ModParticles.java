package com.jheinz.mymod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	// Where a laser sight's beam lands. Spawned client side, fresh every tick.
	public static final SimpleParticleType LASER_DOT =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("laser_dot"), FabricParticleTypes.simple(true));
	public static final SimpleParticleType GREEN_LASER_DOT =
			Registry.register(BuiltInRegistries.PARTICLE_TYPE, MyMod.id("green_laser_dot"), FabricParticleTypes.simple(true));

	private ModParticles() {
	}

	public static void initialize() {
	}
}
