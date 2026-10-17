package com.jheinz.tomyen.fusion.gear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.GearNightVision;
import com.jheinz.tomyen.alien.Ufo;
import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.meteorite.Meteor;

/**
 * The Astral Visor (a TV Helmet whose screen became a window on the stars, with the Starlight Lens built in):
 * <ul>
 * <li>Night vision, always, through {@link GearNightVision} (no icon, no particles, no flicker).
 * <li>Mobs within {@value #SIGHT_RANGE} blocks are drawn with a faint violet outline, through walls (client side:
 * AstralVisorClient and EntityRendererAstralSightMixin).
 * <li>Warnings: every {@value #SCAN_INTERVAL} ticks the server looks for meteors whose path comes down within
 * {@value #WARNING_RANGE} blocks of the wearer, and UFOs within that range, and sends them
 * ({@link FusionGear.AstralWarning}): for a meteor, where it will hit and how soon; for a UFO, where it is. The wearer's
 * HUD shows each with its distance and direction, and chimes when a new one appears.
 * </ul>
 * Works socketed in an exoskeleton too.
 */
public final class AstralVisor {
	public static final int SIGHT_RANGE = 32;
	public static final int WARNING_RANGE = 200;
	private static final int SCAN_INTERVAL = 10;
	private static final int MAX_THREATS = 4;
	/** How far ahead a meteor's path is followed looking for the ground, in ticks of flight. */
	private static final int MAX_FLIGHT_TICKS = 400;

	/** Players last sent a non-empty warning (so they get one empty one when the sky clears). */
	private static final Set<UUID> WARNED = new HashSet<>();

	private AstralVisor() {
	}

	static void initialize() {
		GearNightVision.source(AstralVisor::isWearing, false);
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % SCAN_INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				scan(level, player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WARNED.remove(handler.player.getUUID()));
	}

	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(FusionGear.ASTRAL_VISOR)
				|| ExoSockets.worn(entity, EquipmentSlot.HEAD).is(FusionGear.ASTRAL_VISOR);
	}

	private static void scan(ServerLevel level, ServerPlayer player) {
		List<FusionGear.Threat> threats = new ArrayList<>();
		if (isWearing(player) && !player.isSpectator()) {
			Vec3 at = player.position();
			// A meteor can start well over a hundred blocks up and off to one side, so look wide and test its landing.
			AABB wide = player.getBoundingBox().inflate(WARNING_RANGE + 160, 400, WARNING_RANGE + 160);
			for (Meteor meteor : level.getEntitiesOfClass(Meteor.class, wide, Meteor::isAlive)) {
				FusionGear.Threat threat = meteorThreat(level, meteor);
				if (threat != null && horizontal(at, threat.x(), threat.z()) < WARNING_RANGE) {
					threats.add(threat);
				}
			}
			for (Ufo ufo : level.getEntitiesOfClass(Ufo.class, player.getBoundingBox().inflate(WARNING_RANGE), Ufo::isAlive)) {
				threats.add(new FusionGear.Threat(ufo.getId(), FusionGear.Threat.UFO, ufo.getX(), ufo.getY(), ufo.getZ(), 0));
			}
			threats.sort(Comparator.comparingDouble(t -> horizontal(at, t.x(), t.z())));
			if (threats.size() > MAX_THREATS) {
				threats = new ArrayList<>(threats.subList(0, MAX_THREATS));
			}
		}
		if (!threats.isEmpty()) {
			WARNED.add(player.getUUID());
			ServerPlayNetworking.send(player, new FusionGear.AstralWarning(threats));
		} else if (WARNED.remove(player.getUUID())) {
			ServerPlayNetworking.send(player, new FusionGear.AstralWarning(List.of()));
		}
	}

	private static double horizontal(Vec3 at, double x, double z) {
		return Math.sqrt(Mth.square(at.x - x) + Mth.square(at.z - z));
	}

	/** Where a meteor will hit (following its straight path down to the ground) and in how many ticks, or null. */
	private static FusionGear.Threat meteorThreat(ServerLevel level, Meteor meteor) {
		Vec3 pos = meteor.position();
		Vec3 step = meteor.getDeltaMovement();
		if (step.lengthSqr() < 1.0e-6) {
			return null;
		}
		for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
			int x = Mth.floor(pos.x), z = Mth.floor(pos.z);
			if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
				// Never load a chunk to look: past the loaded world, call it unknown.
				pos = pos.add(step);
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (pos.y <= ground) {
				return new FusionGear.Threat(meteor.getId(), FusionGear.Threat.METEOR, pos.x, ground, pos.z, tick);
			}
			if (pos.y < level.getMinY()) {
				return null;
			}
			pos = pos.add(step);
		}
		return null;
	}
}
