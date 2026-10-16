package com.jheinz.tomyen.herbalism;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * What the herbal wards do, for the vampire and werewolf code to ask.
 * <ul>
 * <li>Garlic: a vampire won't come within {@value #GARLIC_RADIUS} blocks of a Garlic Braid (it's shoved back out,
 * hissing, like a crucifix's ward), and won't bite anyone with Garlic Breath (from eating raw garlic or garlic bread),
 * prowling round them instead.</li>
 * <li>Wolfsbane: a cursed villager within {@value #WOLFSBANE_RADIUS} blocks of a Wolfsbane Ward doesn't change on a
 * full moon; it shudders and the change passes.</li>
 * </ul>
 */
public final class HerbalWards {
	public static final int GARLIC_RADIUS = 8;
	public static final int WOLFSBANE_RADIUS = 16;
	private static final int WOLFSBANE_COLOR = 0x6A3FA0;

	private HerbalWards() {
	}

	private static Optional<BlockPos> nearest(ServerLevel level, ResourceKey<PoiType> type, BlockPos centre, int radius) {
		return level.getPoiManager().findClosest(holder -> holder.is(type), centre, radius, PoiManager.Occupancy.ANY);
	}

	/** The closest Garlic Braid whose ward this point is inside, if any. */
	public static Optional<BlockPos> garlicBraidAround(ServerLevel level, Vec3 point) {
		return nearest(level, Herbalism.GARLIC_BRAID_POI, BlockPos.containing(point), GARLIC_RADIUS + 1)
				.filter(braid -> Vec3.atCenterOf(braid).distanceToSqr(point) < GARLIC_RADIUS * GARLIC_RADIUS);
	}

	/** Whether a vampire leaves this one alone: Garlic Breath, or standing inside a Garlic Braid's ward. */
	public static boolean repelsVampires(LivingEntity target) {
		if (target.hasEffect(Herbalism.GARLIC_BREATH)) {
			return true;
		}
		return target.level() instanceof ServerLevel level && garlicBraidAround(level, target.position()).isPresent();
	}

	/**
	 * Shoves a vampire back out to the edge of any Garlic Braid's ward it has got inside, smoking and hissing now and
	 * then. Called every tick from the vampire.
	 */
	public static void keepVampireOut(LivingEntity vampire) {
		if (!(vampire.level() instanceof ServerLevel level)) {
			return;
		}
		Optional<BlockPos> found = garlicBraidAround(level, vampire.position());
		if (found.isEmpty()) {
			return;
		}
		Vec3 braid = Vec3.atCenterOf(found.get());
		Vec3 away = new Vec3(vampire.getX() - braid.x, 0.0, vampire.getZ() - braid.z);
		if (away.lengthSqr() < 1.0E-4) {
			float angle = vampire.getRandom().nextFloat() * Mth.TWO_PI;
			away = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
		}
		away = away.normalize();
		double depth = GARLIC_RADIUS - Math.sqrt(braid.distanceToSqr(vampire.position()));
		vampire.move(MoverType.SELF, away.scale(Math.min(depth, 1.0) + 0.05));
		vampire.setDeltaMovement(away.x * 0.3, vampire.getDeltaMovement().y, away.z * 0.3);
		if (vampire.tickCount % 20 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, vampire.getX(), vampire.getY(0.6), vampire.getZ(), 10, 0.25, 0.4, 0.25, 0.02);
			vampire.playSound(SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.6f + vampire.getRandom().nextFloat() * 0.2f);
		}
	}

	/**
	 * Whether a Wolfsbane Ward holds this cursed villager's change back tonight. Asked as it's about to start
	 * changing; if it's held back it shudders, a few violet motes rise off it and it stays as it is.
	 */
	public static boolean holdsBackCurse(ServerLevel level, Villager villager) {
		if (nearest(level, Herbalism.WOLFSBANE_WARD_POI, villager.blockPosition(), WOLFSBANE_RADIUS).isEmpty()) {
			return false;
		}
		level.sendParticles(new DustParticleOptions(WOLFSBANE_COLOR, 1.0f), villager.getX(), villager.getY(0.6), villager.getZ(), 8,
				0.3, 0.45, 0.3, 0.0);
		villager.playSound(SoundEvents.VILLAGER_HURT, 0.4f, 0.7f);
		return true;
	}
}
