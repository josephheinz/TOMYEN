package com.jheinz.tomyen.lmg;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineItem;

/**
 * A belt-fed light machine gun. It fires faster than a whole number of ticks allows, so full-auto alternates one-
 * and two-tick gaps (800 rpm on average), and it's hard to hold on target from the hip: heavy recoil and fast bloom.
 * Crouching sets it down on its bipod (the client swings the legs down), which steadies it enormously. It slows
 * the holder (an attribute modifier on the item) and reloads slowly.
 */
public class MachineGunItem extends GunItem {
	/** On the bipod, recoil and spread are cut to this much... */
	private static final float BIPOD_RECOIL = 0.35f;
	private static final float BIPOD_SPREAD = 0.35f;

	/** Which gap the next round in a burst gets (client side: one shooter). */
	private boolean shortGap;

	public MachineGunItem(Properties properties, GunStats stats, List<MagazineItem> magazines, List<Ammo> ammo) {
		super(properties, stats, magazines, ammo);
	}

	/** Braced on the bipod while crouching. */
	public static boolean braced(LivingEntity holder) {
		return holder.isShiftKeyDown() && holder.onGround();
	}

	@Override
	public GunStats stats(ItemStack gun, LivingEntity holder) {
		GunStats fitted = super.stats(gun, holder);
		if (braced(holder)) {
			fitted = fitted.withRecoil(fitted.recoilDegrees() * BIPOD_RECOIL)
					.withSpread(fitted.spreadDegrees() * BIPOD_SPREAD, fitted.bloomPerShot() * BIPOD_SPREAD);
		}
		return fitted;
	}

	/** Two ticks then one, over and over: 1.5 ticks a round, 800 rpm. */
	@Override
	public int autoTicks(ItemStack gun, LivingEntity holder) {
		shortGap = !shortGap;
		return shortGap ? 1 : 2;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("item.tomyen.m249.bipod_hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
