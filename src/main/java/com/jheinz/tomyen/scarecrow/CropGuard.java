package com.jheinz.tomyen.scarecrow;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * What a scarecrow does for the crops around it, within {@value #RADIUS} blocks (and {@value #HEIGHT} up or down):
 * <ul>
 * <li>Mobs landing on farmland don't trample it back to dirt (players still can): FarmlandBlockScarecrowMixin.
 * <li>Rabbits don't go raiding carrots there: RabbitRaidGardenScarecrowMixin.
 * <li>Gnome raiding parties leave the farm alone: {@link #scaresGnomes}.
 * </ul>
 * <b>Crow Murder hook:</b> when crows arrive (the backlog's Crow Murder: flocks that steal crops and shiny items), their
 * crop-raiding goal should skip any target where {@link #scaresCrows} is true, and a flock that's already landed in
 * range should scatter. That's the scarecrow's real job; this class is where it plugs in.
 */
public final class CropGuard {
	public static final int RADIUS = 16;
	public static final int HEIGHT = 8;

	private CropGuard() {
	}

	/** Whether a standing scarecrow (not one being carried off) watches over this spot. */
	public static boolean isGuarded(Level level, BlockPos pos) {
		AABB area = new AABB(pos).inflate(RADIUS, HEIGHT, RADIUS);
		return !level.getEntitiesOfClass(Scarecrow.class, area, Scarecrow::isDecoyActive).isEmpty();
	}

	/**
	 * For the future Crow Murder mob: whether crows should keep off the crops at this spot. For now it's the same
	 * guarded area; a crow-specific twist (a wider radius, or a pumpkin-head's glow working at night) goes here.
	 */
	public static boolean scaresCrows(Level level, BlockPos pos) {
		return isGuarded(level, pos);
	}

	/**
	 * Whether a gnome raiding party keeps off the crops at this spot: they never pick a guarded farm, pull up a guarded
	 * crop, or stay on one once a scarecrow goes up (see the gnome package's CropRaids).
	 */
	public static boolean scaresGnomes(Level level, BlockPos pos) {
		return isGuarded(level, pos);
	}
}
