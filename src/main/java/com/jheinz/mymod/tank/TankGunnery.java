package com.jheinz.mymod.tank;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.bank.Collectors;

/**
 * A {@link Tank}'s weapons, on the server: the main gun and its breech, the coaxial machine gun, the smoke
 * launchers and the fire control. The crew's readouts are synced through the tank's entity data.
 * <p>
 * <b>Main gun.</b> Loading is by hand: the gunner picks the next round type (steel, tungsten or depleted uranium
 * APFSDS, or HE; the selector skips types the rack has none of, unless it has none at all) and orders a reload,
 * which takes one round of that type from the {@link TankRack} and keeps the breech LOADING for
 * {@link #RELOAD_TICKS} (longer with a damaged gun): the breech opens, the round is rammed, the breech closes and
 * the loader calls "UP!". Only then can it fire, which empties the breech again. An empty rack means no reload.
 * <p>
 * <b>Coax.</b> Fires a 7.62 round every {@link #COAX_INTERVAL} ticks while the trigger is held, along the main
 * gun. Unlimited ammunition, but each shot heats the barrel; at full heat it jams until it cools to
 * {@link #COAX_RESUME}.
 * <p>
 * <b>Smoke.</b> {@link #MAX_SMOKE} salvos of six grenades, thrown in an arc ahead of the turret. Spent salvos are
 * reloaded, one every {@link #SMOKE_REFILL_TICKS}, from gunpowder in the rack ({@link #SMOKE_GUNPOWDER} a salvo).
 * <p>
 * <b>Fire control.</b> Lasing a target gives a range; the gun is then held above the sight line by however much
 * the round in the breech (or the next one) needs to drop onto it (see {@link ShellType#elevationFor}).
 * <p>
 * The bank's tanks skip all of this: they fire collector shells from an endless supply every few seconds.
 */
public final class TankGunnery {
	public static final int BREECH_EMPTY = 0;
	public static final int BREECH_LOADING = 1;
	public static final int BREECH_LOADED = 2;
	public static final int RELOAD_TICKS = 100;
	public static final int MAX_SMOKE = 4;
	static final int SMOKE_GUNPOWDER = 2;
	private static final int SMOKE_REFILL_TICKS = 100;
	private static final int SMOKE_GRENADES = 6;
	public static final int COAX_INTERVAL = 2;
	private static final float COAX_HEAT_PER_SHOT = 0.025f;
	private static final float COAX_COOLING = 0.008f;
	public static final float COAX_RESUME = 0.35f;
	private static final float COAX_DAMAGE = 4.0f;
	private static final float COAX_SPREAD = 0.6f;
	private static final double COAX_RANGE = 96.0;
	/** Ticks after a shot that the spent case stub drops onto the turret floor. */
	private static final int CASING_DELAY = 18;
	/** Between the bank's shots. */
	private static final int AI_RELOAD_MIN = 60;
	private static final int AI_RELOAD_MAX = 80;

	private final Tank tank;
	private final TankRack rack;
	private int breech = BREECH_EMPTY;
	private ShellType breechRound = ShellType.APFSDS;
	private ShellType selected = ShellType.APFSDS;
	private int loadLeft;
	private int loadTotal;
	private boolean coaxTrigger;
	private float coaxHeat;
	private boolean coaxJammed;
	private int coaxCooldown;
	private int casingTicks;
	private int smoke = MAX_SMOKE;
	private int smokeRefill;
	private double lasedRange;
	private int aiReload;

	TankGunnery(Tank tank) {
		this.tank = tank;
		this.rack = new TankRack(tank);
	}

	public TankRack rack() {
		return rack;
	}

	// --- Readouts (synced) --------------------------------------------------------------------------------------

	private void syncBreech() {
		// Two bits of breech state, then three bits for each round.
		tank.sync(Tank.BREECH, breech | breechRound.ordinal() << 2 | selected.ordinal() << 5);
		tank.sync(Tank.LOAD, loadLeft | loadTotal << 12);
	}

	private void syncCoax() {
		tank.sync(Tank.COAX, Math.round(coaxHeat * 1000.0f) | (coaxJammed ? 1 << 16 : 0));
	}

	/** Recounts the rack for the readouts. */
	void rackChanged() {
		if (tank.level().isClientSide()) {
			return;
		}
		int apfsds = Math.min(1023, rack.count(Tanks.APFSDS_SHELL));
		int he = Math.min(1023, rack.count(Tanks.HE_SHELL));
		int powder = Math.min(1023, rack.count(Items.GUNPOWDER));
		tank.sync(Tank.RACK, apfsds | he << 10 | powder << 20);
		int tungsten = Math.min(1023, rack.count(Tanks.TUNGSTEN_APFSDS_SHELL));
		int uranium = Math.min(1023, rack.count(Tanks.DU_APFSDS_SHELL));
		tank.sync(Tank.RACK_SPECIAL, tungsten | uranium << 10);
	}

	void syncAll() {
		syncBreech();
		syncCoax();
		rackChanged();
		tank.sync(Tank.SMOKE, (byte) smoke);
	}

	// --- Ticking ---------------------------------------------------------------------------------------------

	void tick(ServerLevel level) {
		if (breech == BREECH_LOADING) {
			tickLoading(level);
		}
		if (casingTicks > 0 && --casingTicks == 0) {
			tank.crewSound(Tanks.CASING_SOUND, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
		}
		tickCoax(level);
		if (smoke < MAX_SMOKE && !tank.isCollectorTank() && ++smokeRefill >= SMOKE_REFILL_TICKS) {
			smokeRefill = 0;
			if (rack.count(Items.GUNPOWDER) >= SMOKE_GUNPOWDER) {
				rack.take(Items.GUNPOWDER, SMOKE_GUNPOWDER);
				smoke++;
				tank.sync(Tank.SMOKE, (byte) smoke);
			}
		}
		if (aiReload > 0) {
			aiReload--;
		}
	}

	private void tickLoading(ServerLevel level) {
		loadLeft--;
		int elapsed = loadTotal - loadLeft;
		if (elapsed == Math.round(loadTotal * 0.35f)) {
			playInside(level, Tanks.RAM_SOUND, 0.9f, 1.0f);
		} else if (elapsed == Math.round(loadTotal * 0.75f)) {
			playInside(level, Tanks.BREECH_SOUND, 1.0f, 1.15f);
		}
		if (loadLeft <= 0) {
			breech = BREECH_LOADED;
			loadLeft = 0;
			playInside(level, Tanks.LOADER_SOUND, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
			tank.crewMessage(Component.translatable("hud.mymod.tank.up").withStyle(ChatFormatting.GREEN));
		}
		syncBreech();
	}

	/** The loader's work is only heard inside the turret. */
	private void playInside(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		tank.crewSound(sound, volume, pitch);
	}

	// --- Main gun ----------------------------------------------------------------------------------------------

	public int breech() {
		return breech;
	}

	/** The round in the breech, or (empty) the one picked to load next: what the fire control lays the gun for. */
	ShellType laidFor() {
		return breech == BREECH_EMPTY ? selected : breechRound;
	}

	/** Degrees the fire control holds the gun above the sight line, for the last lased range. */
	float superelevation() {
		return lasedRange > 0.0 ? laidFor().elevationFor(lasedRange) : 0.0f;
	}

	void lase(double range) {
		lasedRange = range > 0.0 && range < 5000.0 ? range : 0.0;
	}

	/** Steps the selector to the next round type the rack holds, or with an empty rack simply the next type. */
	void cycleRound() {
		List<ShellType> order = ShellType.SELECTABLE;
		int at = Math.max(0, order.indexOf(selected));
		ShellType next = order.get((at + 1) % order.size());
		for (int i = 1; i <= order.size(); i++) {
			ShellType candidate = order.get((at + i) % order.size());
			if (rack.count(candidate.item()) > 0) {
				next = candidate;
				break;
			}
		}
		selected = next;
		syncBreech();
	}

	/** The gunner ordered a reload. */
	void reload(ServerPlayer gunner) {
		ServerLevel level = (ServerLevel) tank.level();
		if (breech != BREECH_EMPTY) {
			gunner.sendOverlayMessage(Component.translatable(breech == BREECH_LOADING ? "hud.mymod.tank.already_loading"
					: "hud.mymod.tank.already_loaded").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (rack.take(selected.item(), 1) == 0) {
			gunner.sendOverlayMessage(Component.translatable("hud.mymod.tank.none_in_rack", Component.translatable(selected.shortNameKey()))
					.withStyle(ChatFormatting.RED));
			playInside(level, SoundEvents.DISPENSER_FAIL, 0.6f, 0.8f);
			return;
		}
		breech = BREECH_LOADING;
		breechRound = selected;
		loadTotal = Math.round(RELOAD_TICKS * (1.0f + 0.8f * (1.0f - tank.partCondition(TankPart.GUN))));
		loadLeft = loadTotal;
		playInside(level, Tanks.BREECH_SOUND, 1.0f, 0.8f);
		syncBreech();
	}

	/** The gunner pulled the trigger. */
	void fire(ServerPlayer gunner) {
		ServerLevel level = (ServerLevel) tank.level();
		if (breech != BREECH_LOADED) {
			playInside(level, SoundEvents.DISPENSER_FAIL, 0.5f, 1.4f);
			return;
		}
		breech = BREECH_EMPTY;
		syncBreech();
		float spread = 0.1f + 1.5f * (1.0f - tank.partCondition(TankPart.GUN));
		launch(level, gunner, breechRound, tank.gunDirection(1.0f), spread);
	}

	/** A collector tank's shot at its target, if its gun has come round: an endless supply, reloading on its own. */
	public boolean aiFire(LivingEntity target, Vec3 direction) {
		if (aiReload > 0 || !(tank.level() instanceof ServerLevel level)) {
			return false;
		}
		float spread = SmokeScreens.obscured(level, tank.muzzle(1.0f), target.getEyePosition()) ? 12.0f : 3.0f;
		launch(level, tank, ShellType.COLLECTOR, direction, spread * (2.0f - tank.partCondition(TankPart.GUN)));
		aiReload = Mth.nextInt(level.getRandom(), AI_RELOAD_MIN, AI_RELOAD_MAX);
		return true;
	}

	public boolean aiReady() {
		return aiReload <= 0;
	}

	/** Sends a round off along {@code direction} with the muzzle blast, smoke, kick and noise that go with it. */
	private void launch(ServerLevel level, LivingEntity gunner, ShellType type, Vec3 direction, float spread) {
		Vec3 muzzle = tank.muzzle(1.0f);
		TankShell shell = new TankShell(level, gunner, muzzle, type);
		shell.shoot(direction.x, direction.y, direction.z, (float) type.speed(), spread);
		level.addFreshEntity(shell);
		level.broadcastEntityEvent(tank, Tank.FIRE_EVENT);
		blast(level, muzzle, direction.normalize(), type != ShellType.COLLECTOR);
		// Outside, a boom heard far off; inside, the crack and rumble through the turret.
		level.playSound(tank.crew(), muzzle.x, muzzle.y, muzzle.z, Tanks.CANNON_SOUND, SoundSource.NEUTRAL,
				type == ShellType.COLLECTOR ? 4.0f : 8.0f, 0.9f + level.getRandom().nextFloat() * 0.1f);
		tank.crewSound(Tanks.CANNON_CABIN_SOUND, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
		casingTicks = type == ShellType.COLLECTOR ? 0 : CASING_DELAY;
	}

	/** The muzzle blast: a fireball, a cone of smoke blown forward and dust kicked up all round the hull. */
	private void blast(ServerLevel level, Vec3 muzzle, Vec3 forward, boolean big) {
		RandomSource random = level.getRandom();
		Vec3 front = muzzle.add(forward.scale(0.8));
		level.sendParticles(big ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, front.x, front.y, front.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, front.x, front.y, front.z, 12, 0.25, 0.25, 0.25, 0.08);
		int puffs = big ? 30 : 12;
		for (int i = 0; i < puffs; i++) {
			// Count 0 makes the "spread" a velocity: smoke blown out along the barrel and fanning out.
			Vec3 v = forward.scale(0.15 + random.nextDouble() * 0.5).add(random.nextGaussian() * 0.08, random.nextGaussian() * 0.05,
					random.nextGaussian() * 0.08);
			level.sendParticles(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE, front.x, front.y, front.z, 0,
					v.x, v.y, v.z, 1.0);
		}
		if (big) {
			level.sendParticles(ParticleTypes.CLOUD, tank.getX(), tank.getY() + 0.3, tank.getZ(), 40, 2.5, 0.2, 2.5, 0.05);
		}
	}

	// --- Coax --------------------------------------------------------------------------------------------------

	void setCoaxTrigger(boolean down) {
		coaxTrigger = down;
	}

	private void tickCoax(ServerLevel level) {
		ServerPlayer gunner = tank.seatedCrew();
		boolean firing = coaxTrigger && gunner != null && !coaxJammed;
		if (coaxCooldown > 0) {
			coaxCooldown--;
		}
		float before = coaxHeat;
		boolean jammedBefore = coaxJammed;
		if (firing && coaxCooldown == 0) {
			fireCoax(level, gunner);
			coaxCooldown = COAX_INTERVAL;
			coaxHeat = Math.min(1.0f, coaxHeat + COAX_HEAT_PER_SHOT);
			if (coaxHeat >= 1.0f) {
				coaxJammed = true;
				playInside(level, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.6f);
				tank.crewMessage(Component.translatable("hud.mymod.tank.coax_overheat").withStyle(ChatFormatting.RED));
			}
		} else if (!firing) {
			coaxHeat = Math.max(0.0f, coaxHeat - COAX_COOLING);
			if (coaxJammed && coaxHeat <= COAX_RESUME) {
				coaxJammed = false;
			}
		}
		if (gunner == null) {
			coaxTrigger = false;
		}
		if (before != coaxHeat || jammedBefore != coaxJammed) {
			syncCoax();
		}
	}

	private void fireCoax(ServerLevel level, ServerPlayer gunner) {
		RandomSource random = level.getRandom();
		Vec3 from = tank.coaxMuzzle(1.0f);
		float radius = COAX_SPREAD * (float) Math.sqrt(random.nextFloat());
		float angle = random.nextFloat() * Mth.TWO_PI;
		Vec3 look = Vec3.directionFromRotation(tank.gunPitch(1.0f) + superelevation() + radius * Mth.sin(angle),
				tank.turretYaw(1.0f) + radius * Mth.cos(angle));
		Vec3 end = from.add(look.scale(COAX_RANGE));
		HitResult blockHit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB sweep = new AABB(from, end).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(tank, from, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable() && entity != gunner && !tank.hasPassenger(entity), from.distanceToSqr(end));
		if (entityHit != null) {
			end = entityHit.getLocation();
			Entity hit = entityHit.getEntity();
			DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET),
					tank, gunner);
			if (!(tank.isCollectorTank() && Collectors.isCollector(hit))) {
				hit.hurtServer(level, source, COAX_DAMAGE);
			}
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.2);
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.01);
		}
		level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 1, 0.02, 0.02, 0.02, 0.01);
		GunItem.drawTracer(level, from, end, false);
		level.playSound(null, from.x, from.y, from.z, Tanks.COAX_SOUND, SoundSource.NEUTRAL, 2.0f, 0.95f + random.nextFloat() * 0.1f);
	}

	// --- Smoke -------------------------------------------------------------------------------------------------

	void fireSmoke(ServerPlayer gunner) {
		ServerLevel level = (ServerLevel) tank.level();
		if (smoke <= 0) {
			gunner.sendOverlayMessage(Component.translatable("hud.mymod.tank.no_smoke").withStyle(ChatFormatting.RED));
			playInside(level, SoundEvents.DISPENSER_FAIL, 0.6f, 1.0f);
			return;
		}
		smoke--;
		smokeRefill = 0;
		tank.sync(Tank.SMOKE, (byte) smoke);
		float yaw = tank.turretYaw(1.0f);
		for (int i = 0; i < SMOKE_GRENADES; i++) {
			// Three from each launcher, fanned across the front of the turret.
			float spreadDegrees = -50.0f + 100.0f * i / (SMOKE_GRENADES - 1);
			int side = i < SMOKE_GRENADES / 2 ? 1 : -1;
			Vec3 from = TankCrew.turretPoint(tank, 1.0f, side * 1.5, 0.45, 1.2);
			Vec3 to = TankCrew.framePoint(tank.position(), yaw + spreadDegrees, 0.0, 0.6, 15.0 + level.getRandom().nextFloat() * 3.0f);
			SmokeScreens.launch(level, from, to);
		}
		level.playSound(null, tank.getX(), tank.getY() + 2.0, tank.getZ(), Tanks.SMOKE_SOUND, SoundSource.NEUTRAL, 2.0f, 0.8f);
	}

	// --- Saving ------------------------------------------------------------------------------------------------

	public int smoke() {
		return smoke;
	}

	public void setSmoke(int smoke) {
		this.smoke = Mth.clamp(smoke, 0, MAX_SMOKE);
		tank.sync(Tank.SMOKE, (byte) this.smoke);
	}

	void save(ValueOutput output) {
		output.putInt("Breech", breech);
		output.putInt("BreechRound", breechRound.ordinal());
		output.putInt("NextRound", selected.ordinal());
		output.putInt("LoadLeft", loadLeft);
		output.putInt("LoadTotal", loadTotal);
		output.putInt("Smoke", smoke);
	}

	void load(ValueInput input) {
		breech = Mth.clamp(input.getIntOr("Breech", BREECH_EMPTY), BREECH_EMPTY, BREECH_LOADED);
		breechRound = ShellType.byOrdinal(input.getIntOr("BreechRound", 0));
		selected = ShellType.byOrdinal(input.getIntOr("NextRound", 0));
		if (!selected.loadable()) {
			selected = ShellType.APFSDS;
		}
		loadLeft = input.getIntOr("LoadLeft", 0);
		loadTotal = Math.max(1, input.getIntOr("LoadTotal", RELOAD_TICKS));
		smoke = Mth.clamp(input.getIntOr("Smoke", MAX_SMOKE), 0, MAX_SMOKE);
		syncAll();
	}
}
