package com.jheinz.mymod.meteorite;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.level.Level;

/**
 * A spyglass with a star crystal lens. It zooms like a spyglass (PlayerStarSteelMixin makes it count as one), and by
 * night, while you look through it, it also shows the ores and creatures around you through walls, as a Neuralink's
 * X-Ray and ESP modules do (within their 50 blocks). By day it's just a spyglass.
 */
public class StarlightLensItem extends SpyglassItem {
	public StarlightLensItem(Item.Properties properties) {
		super(properties);
	}

	/** Whether this player is looking through a Starlight Lens at night. */
	public static boolean isRevealing(Player player) {
		return isScoping(player) && player.level().isDarkOutside();
	}

	public static boolean isScoping(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof StarlightLensItem;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isDarkOutside()) {
			player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
		}
		return super.use(level, player, hand);
	}
}
