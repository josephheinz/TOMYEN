package com.jheinz.tomyen.fusion.gear;

/**
 * The Revenant Mail: a Wolfhide Carrier's plate slot and moon blood, the Phantasmal Chestplate's ghost and holy silver,
 * and once a day a death that doesn't take your gear ({@link RevenantMail}). Whether the ward is ready today is added
 * to the tooltip on the client (FusionGearClient), which knows the day.
 */
public class RevenantMailItem extends WolfhideCarrierItem {
	public RevenantMailItem(Properties properties) {
		super(properties, "item.tomyen.revenant_mail.ward", "item.tomyen.phantasmal_chestplate.phase",
				"item.tomyen.phantasmal_chestplate.holy", "item.tomyen.wolfhide_carrier.night", "item.tomyen.wolfhide_carrier.moon");
	}
}
