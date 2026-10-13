package com.jheinz.mymod.client.drill;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.drill.DrillItem;
import com.jheinz.mymod.drill.Drills;
import com.jheinz.mymod.tungsten.Tungsten;

/**
 * The drills on the client. For everyone holding one nearby it keeps the bit's spin going ({@link DrillSpin}), and
 * while they drill into a block throws grit off it and, from stone and ore, sparks; the motor whirrs or the gears click
 * while the drill turns ({@link DrillSound}). Also the 3x3 outline ({@link DrillOutline}) and the spin property.
 */
public final class DrillClient {
	/** How far away another player's drill is seen and heard. */
	private static final double RANGE = 48.0;
	private static final Map<Integer, DrillSound> SOUNDS = new HashMap<>();

	private DrillClient() {
	}

	public static void initialize() {
		DrillSpin.register();
		DrillOutline.initialize();
		ParticleProviderRegistry.getInstance().register(Drills.DRILL_SPARK, DrillSparkParticle.Provider::new);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				tick(client, client.level);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SOUNDS.clear();
			DrillSpin.clear();
		});
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		SOUNDS.values().removeIf(DrillSound::isStopped);
		for (AbstractClientPlayer player : level.players()) {
			ItemStack held = player.getMainHandItem();
			double distance = player.position().distanceTo(camera);
			if (!(held.getItem() instanceof DrillItem drill) || distance > RANGE) {
				continue;
			}
			float spin = DrillSpin.update(player);
			boolean biting = false;
			if (spin > 0.3f && DrillSpin.drilling(player) && drill.running(held, player)
					&& player.pick(player.blockInteractionRange(), 1.0f, false) instanceof BlockHitResult hit
					&& hit.getType() == HitResult.Type.BLOCK) {
				BlockState state = level.getBlockState(hit.getBlockPos());
				if (!state.isAir()) {
					biting = true;
					grind(level, drill, state, hit, spin, level.getRandom());
				}
			}
			DrillSpin.setBiting(player, biting);
			if (spin > 0.0f && distance < DrillSound.RANGE) {
				SOUNDS.computeIfAbsent(player.getId(), id -> {
					DrillSound sound = sound(player, held);
					minecraft.getSoundManager().play(sound);
					return sound;
				});
			}
		}
	}

	private static DrillSound sound(AbstractClientPlayer player, ItemStack drill) {
		if (drill.is(Drills.HAND_DRILL)) {
			return new DrillSound(player, Drills.HAND_DRILL_CRANK, 1.0f, 0.5f);
		}
		// The heavy drill growls lower; the star drill sings.
		float pitch = drill.is(Tungsten.TUNGSTEN_CARBIDE_DRILL) ? 0.85f : drill.is(Drills.STAR_DRILL) ? 1.25f : 1.0f;
		return new DrillSound(player, Drills.DRILL_MOTOR, pitch, 0.8f);
	}

	/** Grit thrown off the face where the bit bites, and sparks off stone and ore (fewer from the Hand Drill). */
	private static void grind(ClientLevel level, DrillItem drill, BlockState state, BlockHitResult hit, float spin, RandomSource random) {
		Direction face = hit.getDirection();
		Vec3 at = hit.getLocation().add(face.getStepX() * 0.03, face.getStepY() * 0.03, face.getStepZ() * 0.03);
		boolean hand = drill.engine() == DrillItem.Engine.HAND_CRANK;
		if (random.nextFloat() < (hand ? 0.5f : 0.9f) * spin) {
			level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, outward(face, 0.1, random, 0.1),
					0.05 + face.getStepY() * 0.1, outwardZ(face, 0.1, random, 0.1));
		}
		if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return;
		}
		int sparks = hand ? (random.nextFloat() < 0.35f * spin ? 1 : 0) : (int) (spin * 2.0f + random.nextFloat());
		for (int i = 0; i < sparks; i++) {
			level.addParticle(Drills.DRILL_SPARK, at.x, at.y, at.z, outward(face, 0.18, random, 0.22), 0.08 + face.getStepY() * 0.18
					+ random.nextDouble() * 0.12, outwardZ(face, 0.18, random, 0.22));
		}
	}

	private static double outward(Direction face, double push, RandomSource random, double scatter) {
		return face.getStepX() * push + (random.nextDouble() - 0.5) * scatter;
	}

	private static double outwardZ(Direction face, double push, RandomSource random, double scatter) {
		return face.getStepZ() * push + (random.nextDouble() - 0.5) * scatter;
	}
}
