package com.jheinz.mymod.alien;

/**
 * Something a {@link Ufo} would rather abduct than livestock (the scarecrow, see
 * {@link com.jheinz.mymod.scarecrow.Scarecrow}). A roaming saucer picks the nearest decoy within its search radius that
 * {@link #attractsUfo attracts it} over any animal; its {@link TractorBeam} lifts a decoy at {@link #beamRise}; and once
 * the decoy's in the hull it's handed to {@link #takenAboard} instead of being discarded. It still counts as one of
 * the saucer's abductions.
 */
public interface AbductionDecoy {
	/** Whether this saucer would go for it right now. */
	boolean attractsUfo(Ufo ufo);

	/** How fast the beam can lift it, in blocks a tick (an animal goes up at {@value TractorBeam#ANIMAL_RISE}). */
	double beamRise();

	/** In the hull: up to the decoy what happens to it now. */
	void takenAboard(Ufo ufo);
}
