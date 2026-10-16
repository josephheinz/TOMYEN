package com.jheinz.tomyen.powerarmor;

import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

/**
 * The suit-wide numbers that come from its pieces' marks. Each piece governs its own systems:
 * <ul>
 * <li>Legs (the average of the two): thrust, and so top speed, and how hard a heavy landing hits. A broken leg
 * halves thrust.
 * <li>Arms (each its own): the repulsor port on that gauntlet, its damage, knockback and cooldown. A broken arm
 * can't fire.
 * <li>Torso: the unibeam's damage, how efficiently the suit burns fuel, and how long the thrusters can burn
 * before they overheat. A broken torso can't fire the unibeam.
 * <li>Helmet: the HUD's extras (Mk II reads out the target's health, Mk III adds a threat radar) and how long
 * its air supply lasts underwater. A broken helmet gives neither.
 * </ul>
 * Values between marks (two legs of different marks) are interpolated.
 */
public final class PowerArmorStats {
	// Per mark, I to III.
	/** Blocks a tick squared of forward thrust while cruising. */
	private static final float[] THRUST = {0.055f, 0.07f, 0.09f};
	/** Boosting multiplies thrust by this. */
	public static final float BOOST_MULTIPLIER = 1.8f;
	/**
	 * In the air a player keeps 91% of their speed each tick (the flight evens vertical drag out to match), so
	 * thrust tops out where it makes up the other 9%: at about ten times the thrust.
	 */
	public static final float DRAG = 0.91f;
	private static final float[] SHOCKWAVE = {1.0f, 1.4f, 1.8f};
	private static final float[] REPULSOR_DAMAGE = {6.0f, 8.0f, 11.0f};
	private static final int[] REPULSOR_COOLDOWN = {16, 12, 8};
	private static final float[] REPULSOR_KNOCKBACK = {0.8f, 1.0f, 1.3f};
	private static final float[] UNIBEAM_DAMAGE = {18.0f, 26.0f, 36.0f};
	private static final float[] FUEL_EFFICIENCY = {1.0f, 0.8f, 0.6f};
	private static final int[] MAX_BURN = {400, 700, 1200};
	private static final int[] AIR = {600, 1800, 4800};

	private final PowerArmorSuit suit;
	private final float legLevel;
	private final boolean legsDamaged;

	private PowerArmorStats(PowerArmorSuit suit) {
		this.suit = suit;
		PowerArmorTier left = suit.tier(PowerArmorPiece.LEFT_LEG);
		PowerArmorTier right = suit.tier(PowerArmorPiece.RIGHT_LEG);
		this.legLevel = ((left == null ? 0 : left.level()) + (right == null ? 0 : right.level())) / 2.0f;
		this.legsDamaged = !suit.works(PowerArmorPiece.LEFT_LEG) || !suit.works(PowerArmorPiece.RIGHT_LEG);
	}

	public static PowerArmorStats of(PowerArmorSuit suit) {
		return new PowerArmorStats(suit);
	}

	private static float lerp(float[] table, float level) {
		int low = Mth.clamp((int) Math.floor(level), 0, table.length - 1);
		int high = Math.min(low + 1, table.length - 1);
		return Mth.lerp(level - low, table[low], table[high]);
	}

	private int level(PowerArmorPiece piece) {
		PowerArmorTier tier = suit.tier(piece);
		return tier == null ? 0 : tier.level();
	}

	/** The average mark of the legs, 0 (Mk I) to 2 (Mk III). */
	public float legLevel() {
		return legLevel;
	}

	private float legHealth() {
		return legsDamaged ? 0.5f : 1.0f;
	}

	/** Forward thrust while cruising, in blocks a tick squared; boosting is {@link #BOOST_MULTIPLIER} times this. */
	public float cruiseThrust() {
		return lerp(THRUST, legLevel) * legHealth();
	}

	public float boostThrust() {
		return cruiseThrust() * BOOST_MULTIPLIER;
	}

	/** The speed thrust tops out at, in blocks a tick. */
	public static float topSpeed(float thrust) {
		return thrust * DRAG / (1.0f - DRAG);
	}

	public float shockwaveMultiplier() {
		return lerp(SHOCKWAVE, legLevel);
	}

	/** Whether this arm's repulsor can fire: the arm is on and not broken. */
	public boolean repulsorWorks(PowerArmorPiece arm) {
		return suit.works(arm);
	}

	public float repulsorDamage(PowerArmorPiece arm) {
		return REPULSOR_DAMAGE[level(arm)];
	}

	public int repulsorCooldown(PowerArmorPiece arm) {
		return REPULSOR_COOLDOWN[level(arm)];
	}

	public float repulsorKnockback(PowerArmorPiece arm) {
		return REPULSOR_KNOCKBACK[level(arm)];
	}

	public boolean unibeamWorks() {
		return suit.works(PowerArmorPiece.TORSO);
	}

	public float unibeamDamage() {
		return UNIBEAM_DAMAGE[level(PowerArmorPiece.TORSO)];
	}

	/** Every drain on the core is multiplied by this. */
	public float fuelEfficiency() {
		return FUEL_EFFICIENCY[level(PowerArmorPiece.TORSO)];
	}

	/** Ticks the thrusters can burn without a break before they overheat. */
	public int maxBurnTicks() {
		return MAX_BURN[level(PowerArmorPiece.TORSO)];
	}

	/** 0 for a broken (or Mk I) helmet's plain HUD, 1 adds target readouts (Mk II), 2 the threat radar (Mk III). */
	public int hudLevel() {
		return suit.works(PowerArmorPiece.HELMET) ? level(PowerArmorPiece.HELMET) : 0;
	}

	/** Ticks of air the helmet's tank holds; none if it's broken. */
	public int airTicks() {
		return suit.works(PowerArmorPiece.HELMET) ? AIR[level(PowerArmorPiece.HELMET)] : 0;
	}

	/** The share of a hit on this piece it absorbs: by its mark, or nothing if it's broken. */
	public float protection(PowerArmorPiece piece) {
		@Nullable PowerArmorTier tier = suit.tier(piece);
		return tier == null || !suit.works(piece) ? 0.0f : tier.protection();
	}

	/** The per-mark tables, for tooltips: the value for a mark. */
	/** Top speed in metres a second for legs of a mark, cruising or boosting. */
	public static float topSpeedFor(PowerArmorTier tier, boolean boost) {
		return topSpeed(THRUST[tier.level()] * (boost ? BOOST_MULTIPLIER : 1.0f)) * 20.0f;
	}

	public static float shockwaveFor(PowerArmorTier tier) {
		return SHOCKWAVE[tier.level()];
	}

	public static float repulsorDamageFor(PowerArmorTier tier) {
		return REPULSOR_DAMAGE[tier.level()];
	}

	public static int repulsorCooldownFor(PowerArmorTier tier) {
		return REPULSOR_COOLDOWN[tier.level()];
	}

	public static float unibeamDamageFor(PowerArmorTier tier) {
		return UNIBEAM_DAMAGE[tier.level()];
	}

	public static float fuelEfficiencyFor(PowerArmorTier tier) {
		return FUEL_EFFICIENCY[tier.level()];
	}

	public static int maxBurnFor(PowerArmorTier tier) {
		return MAX_BURN[tier.level()];
	}

	public static int airFor(PowerArmorTier tier) {
		return AIR[tier.level()];
	}
}
