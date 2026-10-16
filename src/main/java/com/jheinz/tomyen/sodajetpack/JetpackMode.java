package com.jheinz.tomyen.sodajetpack;

/**
 * How the jetpack flies on what's in its tank. Sugar is the kick and slime is the body: sugar alone climbs hardest but
 * burns the pressure fastest and sputters most; slime alone barely holds you up but its sticky fizz lasts much
 * longer and cushions a landing best; the two together (a Fizz Charge, or both carried loose) sit between. Every one
 * of them is far weaker than power armor's thrusters.
 *
 * <p>A full tank lasts at most {@code 1 / drain} thrusting ticks (sputters don't use any): slime's 60, under the 80
 * ticks a server lets a player hang in the air before kicking them for flying.
 *
 * <p>Shared by the client (which flies the player, see SodaJetpackClient) and the server (which burns the fuel and
 * pressure, see {@link SodaJetpackFlight}).
 *
 * @param lift          upward speed added each thrusting tick, blocks a tick (gravity takes 0.08)
 * @param maxRise       the fastest it will push you upward, blocks a tick
 * @param drain         pressure used each thrusting tick (a full tank is 1)
 * @param sputterChance the odds each {@link JetpackChaos#SPUTTER_WINDOW}-tick window that it coughs and gives nothing
 * @param spinChance    the odds each {@link JetpackChaos#SPIN_WINDOW}-tick window that it spins you out sideways
 * @param cushion       how much of the fall built up so far each thrusting tick keeps (lower is a softer landing)
 */
public enum JetpackMode {
	NONE(0.0, 0.0, 0.0f, 0.0f, 0.0f, 1.0f),
	SUGAR(0.115, 0.42, 1.0f / 40.0f, 0.14f, 0.10f, 0.7f),
	SLIME(0.092, 0.22, 1.0f / 60.0f, 0.05f, 0.03f, 0.4f),
	MIX(0.108, 0.36, 1.0f / 52.0f, 0.08f, 0.06f, 0.55f);

	public final double lift;
	public final double maxRise;
	public final float drain;
	public final float sputterChance;
	public final float spinChance;
	public final float cushion;

	JetpackMode(double lift, double maxRise, float drain, float sputterChance, float spinChance, float cushion) {
		this.lift = lift;
		this.maxRise = maxRise;
		this.drain = drain;
		this.sputterChance = sputterChance;
		this.spinChance = spinChance;
		this.cushion = cushion;
	}

	public static JetpackMode of(boolean sugar, boolean slime) {
		return sugar && slime ? MIX : sugar ? SUGAR : slime ? SLIME : NONE;
	}

	public static JetpackMode byId(int id) {
		JetpackMode[] values = values();
		return id >= 0 && id < values.length ? values[id] : NONE;
	}
}
