package com.jheinz.tomyen.voidsteel;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.metal.HintItem;

/**
 * The voidsteel sword (and its long sword): sneak and use it to blink up to {@link #BLINK_DISTANCE} blocks the way
 * you're looking, then wait {@link #COOLDOWN_TICKS} ticks to do it again. The blink stops short of walls (it never
 * passes through a block) and lands only where you fit, out of lava and fire, stepping up onto a ledge if the spot
 * itself is blocked; with nowhere to land it fails and costs nothing. Arriving clears any fall you were in the middle
 * of, so a blink breaks a fall.
 */
public class VoidsteelSwordItem extends HintItem {
	public static final double BLINK_DISTANCE = 6.0;
	public static final int COOLDOWN_TICKS = 60;
	/** The shortest blink worth making. */
	private static final double MIN_DISTANCE = 1.0;
	/** Room left between where the eye's line meets a wall and the body's middle. */
	private static final double WALL_CLEARANCE = 0.5;
	private static final double[] STEP_UPS = {0.0, 0.6, 1.1};

	public VoidsteelSwordItem(Properties properties, List<String> hints) {
		super(properties, Stream.concat(Stream.of("item.tomyen.voidsteel.blink"), hints.stream()).toList());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive()) {
			return super.use(level, player, hand);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (!blink(serverPlayer)) {
				return InteractionResult.FAIL;
			}
			player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN_TICKS);
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean blink(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(BLINK_DISTANCE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? BLINK_DISTANCE : wall.getLocation().distanceTo(eye) - WALL_CLEARANCE;
		Vec3 from = player.position();
		for (double distance = reach; distance >= MIN_DISTANCE; distance -= 0.25) {
			for (double up : STEP_UPS) {
				Vec3 to = from.add(look.scale(distance)).add(0.0, up, 0.0);
				AABB body = player.getBoundingBox().move(to.subtract(from));
				if (safe(level, player, body)) {
					arrive(level, player, from, to);
					return true;
				}
			}
		}
		return false;
	}

	/** Room to stand, and no lava or fire in it or in the block below. */
	private static boolean safe(ServerLevel level, Player player, AABB body) {
		return level.noCollision(player, body) && level.getBlockStatesIfLoaded(body.expandTowards(0.0, -1.0, 0.0))
				.noneMatch(state -> state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE));
	}

	private static void arrive(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		// A streak of portal motes along the way, a burst where you land.
		for (int i = 0; i <= 8; i++) {
			Vec3 at = from.lerp(to, i / 8.0).add(0.0, player.getBbHeight() * 0.5, 0.0);
			level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 3, 0.15, 0.25, 0.15, 0.1);
		}
		level.gameEvent(GameEvent.TELEPORT, from, GameEvent.Context.of(player));
		player.teleportTo(to.x, to.y, to.z);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + player.getBbHeight() * 0.5, to.z, 16, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
	}
}
