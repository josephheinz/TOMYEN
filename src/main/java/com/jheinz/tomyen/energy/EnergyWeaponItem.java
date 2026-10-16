package com.jheinz.tomyen.energy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunSpread;
import com.jheinz.tomyen.GunStats;

/**
 * A gun that fires beams rather than bullets: a hitscan line that can carry on through several targets (and, for a
 * Railgun slug, through a few soft blocks), drawn for everyone as a glowing {@link EnergyBeam}. Each shot heats it up
 * by {@link #heatPerShot}; it sheds {@link #coolingPerTick} a tick once {@link #coolingDelay} ticks have passed since
 * the last one. At {@link #MAX_HEAT} it overheats and won't fire again until it has cooled right down. The heat is kept
 * on the stack as a {@link Heat} reading and worked out from that on either side, so the HUD and the glowing parts of
 * its model follow it without any more syncing.
 *
 * <p>It's a {@link GunItem} so the rest of the gun handling (aiming down the sights, the crosshair, recoil,
 * Smart-Link's steadier hip fire and the Overclock's faster reloads) works the same; what it loads is up to each kind.
 */
public abstract class EnergyWeaponItem extends GunItem {
	public static final float MAX_HEAT = 100.0f;
	/** Blocks no tougher than this (glass, leaves, dirt, sand, wool) a slug can punch through. */
	private static final float WEAK_BLOCK_HARDNESS = 1.0f;

	protected EnergyWeaponItem(Properties properties, GunStats stats, List<Ammo> ammo) {
		super(properties, stats, List.of(), ammo);
	}

	protected abstract float heatPerShot();

	protected abstract float coolingPerTick();

	/** Ticks after a shot before it starts to cool. */
	protected int coolingDelay() {
		return 0;
	}

	/** The colour of its beam, RGB. */
	public abstract int beamColor();

	/** What the Smart-Link HUD counts against its {@link #capacity}: shots or slugs left, or for the Ray Gun, charge. */
	public abstract int displayRounds(ItemStack gun, long now);

	// ------------------------------------------------------------------------------------------------ heat

	/** How hot it is at game time {@code now}, 0 to {@link #MAX_HEAT}. */
	public float heat(ItemStack gun, long now) {
		Heat heat = gun.get(EnergyWeapons.HEAT);
		if (heat == null) {
			return 0.0f;
		}
		long cooling = now - heat.time() - (heat.overheated() ? 0 : coolingDelay());
		return Mth.clamp(heat.level() - Math.max(0L, cooling) * coolingPerTick(), 0.0f, MAX_HEAT);
	}

	/** Whether it overheated and hasn't cooled down yet. */
	public boolean overheated(ItemStack gun, long now) {
		Heat heat = gun.get(EnergyWeapons.HEAT);
		return heat != null && heat.overheated() && heat(gun, now) > 0.0f;
	}

	/** Adds a shot's heat; true if that overheated it. */
	protected boolean addHeat(ItemStack gun, long now, float amount) {
		float level = Math.min(MAX_HEAT, heat(gun, now) + amount);
		boolean overheated = level >= MAX_HEAT;
		gun.set(EnergyWeapons.HEAT, new Heat(level, now, overheated));
		return overheated;
	}

	/** How brightly its glowing parts glow, 0 to 1 (see the tomyen:energy_glow item model property): its heat by default. */
	public float glow(ItemStack gun, LivingEntity holder, long now) {
		return heat(gun, now) / MAX_HEAT;
	}

	// ------------------------------------------------------------------------------------------------ firing

	/** Where a shot leaves the gun, roughly, for everyone else's view of the beam: low and off to the holding hand. */
	public static Vec3 muzzle(LivingEntity shooter) {
		return muzzle(shooter, shooter.getViewVector(1.0f));
	}

	/** Where a shot along {@code look} leaves the gun. */
	public static Vec3 muzzle(LivingEntity shooter, Vec3 look) {
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		double side = shooter.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
		return shooter.getEyePosition().add(look.scale(0.9)).add(right.scale(0.28 * side)).add(0.0, -0.22, 0.0);
	}

	/** The shooter's aim, strayed somewhere within a cone {@code spread} degrees across (uniform over its disc). */
	public static Vec3 aim(LivingEntity shooter, float spread) {
		if (spread <= 0.0f) {
			return shooter.getViewVector(1.0f);
		}
		return aim(shooter, spread, shooter.getXRot(), shooter.getYRot());
	}

	/** An aim along the given rotation (degrees), strayed within a cone {@code spread} degrees across. */
	public static Vec3 aim(LivingEntity shooter, float spread, float xRot, float yRot) {
		if (spread <= 0.0f) {
			return Vec3.directionFromRotation(xRot, yRot);
		}
		float radius = spread * (float) Math.sqrt(shooter.getRandom().nextFloat());
		float angle = shooter.getRandom().nextFloat() * Mth.TWO_PI;
		return Vec3.directionFromRotation(xRot + radius * Mth.sin(angle), yRot + radius * Mth.cos(angle));
	}

	/** The spread for a shot this player fires now, with its bloom (see {@link GunSpread}). */
	public static float spread(Player player, GunStats stats) {
		return player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats) : stats.spreadDegrees();
	}

	/** A damage source of this type, from this shooter. */
	public static DamageSource damage(ServerLevel level, ResourceKey<DamageType> type, Entity shooter) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), shooter);
	}

	/**
	 * A hitscan line from {@code from} along {@code direction}: hits up to {@code 1 + pierce} entities in order (calling
	 * {@code onHit} with each and where it was struck), passing through up to {@code weakBlocks} soft blocks on the way
	 * (breaking them if {@code breakBlocks} and the mobGriefing rule allows) before the next solid one stops it.
	 * Returns where the line ended: the last thing it hit, or a block, or its full range.
	 */
	public static Vec3 trace(ServerLevel level, Entity shooter, Vec3 from, Vec3 direction, double range, int pierce, int weakBlocks,
			boolean breakBlocks, BiConsumer<Entity, Vec3> onHit) {
		Vec3 dir = direction.normalize();
		Vec3 end = from.add(dir.scale(range));
		Vec3 start = from;
		int punched = 0;
		boolean griefing = breakBlocks && level.getGameRules().get(GameRules.MOB_GRIEFING);
		// Out along the line, block by block, through whatever soft blocks it may punch through.
		while (true) {
			BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
			if (blockHit.getType() == HitResult.Type.MISS) {
				break;
			}
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			float hardness = state.getDestroySpeed(level, pos);
			Vec3 hit = blockHit.getLocation();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), hit.x, hit.y, hit.z, 10, 0.1, 0.1, 0.1, 0.15);
			if (punched >= weakBlocks || hardness < 0.0f || hardness > WEAK_BLOCK_HARDNESS) {
				end = hit;
				break;
			}
			punched++;
			if (griefing) {
				level.destroyBlock(pos, true, shooter);
			}
			// On past the block it went through.
			Vec3 past = hit;
			for (int i = 0; i < 40 && BlockPos.containing(past).equals(pos); i++) {
				past = past.add(dir.scale(0.05));
			}
			start = past;
		}

		// Everything along the line up to there, nearest first.
		List<Hit> hits = new ArrayList<>();
		AABB sweep = new AABB(from, end).inflate(1.0);
		for (Entity entity : level.getEntities(shooter, sweep, entity -> entity.isPickable() && !entity.isSpectator()
				&& entity != shooter.getVehicle() && !shooter.isPassengerOfSameVehicle(entity))) {
			Optional<Vec3> at = entity.getBoundingBox().inflate(entity.getPickRadius() + 0.1).clip(from, end);
			if (at.isPresent()) {
				hits.add(new Hit(entity, at.get(), from.distanceToSqr(at.get())));
			} else if (entity.getBoundingBox().contains(from)) {
				hits.add(new Hit(entity, from, 0.0));
			}
		}
		hits.sort(Comparator.comparingDouble(Hit::distanceSqr));
		for (int i = 0; i < hits.size() && i <= pierce; i++) {
			Hit hit = hits.get(i);
			onHit.accept(hit.entity(), hit.at());
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.at().x, hit.at().y, hit.at().z, 8, 0.1, 0.1, 0.1, 0.25);
			if (i == pierce) {
				end = hit.at();
			}
		}
		return end;
	}

	private record Hit(Entity entity, Vec3 at, double distanceSqr) {
	}
}
