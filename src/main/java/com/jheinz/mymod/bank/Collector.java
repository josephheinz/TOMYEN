package com.jheinz.mymod.bank;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A debt collector: a mob sent after one particular player, the debtor (see {@link Collectors}). */
public interface Collector {
	@Nullable UUID debtor();

	void setDebtor(@Nullable UUID debtor);
}
