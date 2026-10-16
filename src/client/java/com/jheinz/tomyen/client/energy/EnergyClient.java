package com.jheinz.tomyen.client.energy;

import com.mojang.serialization.MapCodec;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.energy.EnergyBeam;
import com.jheinz.tomyen.energy.EnergyWeaponItem;
import com.jheinz.tomyen.energy.LaserRifleItem;
import com.jheinz.tomyen.energy.RailgunAim;
import com.jheinz.tomyen.energy.RailgunItem;

/**
 * The energy weapons on the client: their beams, the tomyen:energy_glow item model property (how brightly a weapon's
 * glowing parts glow: a Laser Rifle's emitter and a Ray Gun's coil with heat, a Railgun's coils as it charges and
 * cools), and their lines in the ammo HUD: cell charge, slugs or Ray Gun charge, over a heat gauge.
 */
public final class EnergyClient {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int RED = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GAUGE = 40;

	private EnergyClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(EnergyBeam.TYPE, (payload, context) -> EnergyBeams.receive(payload));
		RailgunAim.sender = ClientPlayNetworking::send;
		LevelRenderEvents.COLLECT_SUBMITS.register(EnergyBeams::submit);
		RangeSelectItemModelProperties.ID_MAPPER.put(MyMod.id("energy_glow"), Glow.MAP_CODEC);
	}

	/** The held energy weapon's lines in the ammo HUD, at the HUD's usual spot. */
	public static void extractAmmo(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack held, EnergyWeaponItem gun, int x, int y) {
		long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		float heat = gun.heat(held, now) / EnergyWeaponItem.MAX_HEAT;
		boolean overheated = gun.overheated(held, now);
		if (gun instanceof LaserRifleItem) {
			if (!held.has(ModComponents.LOADED_MAGAZINE)) {
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.laser_rifle.no_cell"), x, y, RED, true);
				graphics.text(minecraft.font, Component.translatable("hud.tomyen.punch_to_reload"), x, y + 10, GRAY, true);
				return;
			}
			int charge = LaserRifleItem.charge(held);
			Component line = Component.translatable("hud.tomyen.laser_rifle.cell", charge, LaserRifleItem.CELL_SHOTS);
			graphics.text(minecraft.font, line, x, y, charge == 0 ? RED : WHITE, true);
			graphics.text(minecraft.font, Component.translatable(gun.fireMode(held).translationKey() + ".short"),
					x + minecraft.font.width(line) + 6, y, GRAY, true);
			heatGauge(graphics, x, y + 11, heat, overheated ? RED : heatColour(heat));
		} else if (gun instanceof RailgunItem) {
			MagazineContents loaded = held.get(ModComponents.LOADED_MAGAZINE);
			int slugs = RailgunItem.slugs(held);
			String count = slugs + " / " + RailgunItem.MAGAZINE;
			graphics.text(minecraft.font, count, x, y, slugs == 0 ? RED : WHITE, true);
			if (loaded != null && !loaded.isEmpty()) {
				Ammo slug = Ammo.find(gun.ammo(), loaded.ammo());
				graphics.text(minecraft.font, new ItemStack(loaded.ammo()).getHoverName(), x + minecraft.font.width(count) + 6, y,
						slug == null ? GRAY : 0xFF000000 | slug.color(), true);
			}
			float charge = minecraft.player == null ? 0.0f : RailgunItem.charge(held, minecraft.player);
			if (charge > 0.0f) {
				heatGauge(graphics, x, y + 11, charge, 0xFF9CC8FF);
			} else {
				heatGauge(graphics, x, y + 11, heat, heat > 0.0f ? 0xFFFFA040 : 0xFF9CC8FF);
			}
		} else {
			int percent = Math.round(100.0f * (1.0f - heat));
			graphics.text(minecraft.font, Component.translatable("hud.tomyen.ray_gun.charge", percent), x, y, overheated ? RED : WHITE, true);
			heatGauge(graphics, x, y + 11, heat, overheated ? RED : heatColour(heat));
		}
		if (overheated) {
			boolean blink = Util.getMillis() % 500 < 250;
			graphics.text(minecraft.font, Component.translatable(gun instanceof RailgunItem ? "hud.tomyen.railgun.cooling" : "hud.tomyen.energy.overheated"),
					x, y + 16, blink ? RED : GRAY, true);
		}
	}

	/** Cool cyan through amber to red. */
	private static int heatColour(float heat) {
		return heat < 0.5f ? ARGB.srgbLerp(heat * 2.0f, 0xFF40E8FF, 0xFFFFB000) : ARGB.srgbLerp((heat - 0.5f) * 2.0f, 0xFFFFB000, 0xFFFF4020);
	}

	private static void heatGauge(GuiGraphicsExtractor graphics, int x, int y, float fraction, int colour) {
		graphics.fill(x, y, x + GAUGE, y + 3, 0xFF202020);
		graphics.fill(x, y, x + Math.round(GAUGE * Math.min(1.0f, fraction)), y + 3, colour);
	}

	/** How brightly the held weapon's glowing parts glow, 0 to 1; just its heat when nobody's holding it. */
	public record Glow() implements RangeSelectItemModelProperty {
		public static final MapCodec<Glow> MAP_CODEC = MapCodec.unit(new Glow());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			if (!(stack.getItem() instanceof EnergyWeaponItem gun) || level == null) {
				return 0.0f;
			}
			long now = level.getGameTime();
			return owner instanceof LivingEntity holder ? gun.glow(stack, holder, now) : gun.heat(stack, now) / EnergyWeaponItem.MAX_HEAT;
		}

		@Override
		public MapCodec<Glow> type() {
			return MAP_CODEC;
		}
	}
}
