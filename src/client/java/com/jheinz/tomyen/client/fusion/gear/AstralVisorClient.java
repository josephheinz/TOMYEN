package com.jheinz.tomyen.client.fusion.gear;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import com.jheinz.tomyen.fusion.gear.AstralVisor;
import com.jheinz.tomyen.fusion.gear.FusionGear;

/**
 * The Astral Visor on its wearer's client: the faint violet outline on mobs within {@value AstralVisor#SIGHT_RANGE}
 * blocks (EntityRendererAstralSightMixin asks {@link #outline}), and the warning panel at the top of the screen for
 * meteors and UFOs the server says are coming: each line names it, how far off it is (or will hit) and which way, and a
 * meteor's seconds to impact; a chime sounds for each new one.
 */
public final class AstralVisorClient {
	/** The outline colour: a soft starlit violet. */
	public static final int OUTLINE = 0xB49CFF;
	/** Warnings older than this (the server sends twice a second while anything's coming) are dropped. */
	private static final long STALE_MILLIS = 1500L;

	private static List<FusionGear.Threat> threats = List.of();
	private static long received;
	private static final Set<Integer> SEEN = new HashSet<>();

	private AstralVisorClient() {
	}

	static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(FusionGear.AstralWarning.TYPE, (payload, context) -> {
			threats = payload.threats();
			received = Util.getMillis();
			boolean fresh = false;
			for (FusionGear.Threat threat : threats) {
				fresh |= SEEN.add(threat.entityId());
			}
			if (fresh) {
				context.client().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.0f));
				context.client().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.8f));
			}
			if (threats.isEmpty()) {
				SEEN.clear();
			}
		});
	}

	/** The outline colour for this entity as the local player sees it through a visor, or 0 for none. */
	public static int outline(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || entity == player || !(entity instanceof Mob) || !entity.isAlive() || !AstralVisor.isWearing(player)) {
			return 0;
		}
		return entity.distanceToSqr(player) <= AstralVisor.SIGHT_RANGE * AstralVisor.SIGHT_RANGE ? OUTLINE : 0;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || threats.isEmpty() || Util.getMillis() - received > STALE_MILLIS
				|| !AstralVisor.isWearing(player)) {
			return;
		}
		float pulse = 0.65f + 0.35f * Mth.sin(Util.getMillis() / 180.0f);
		int y = graphics.guiHeight() / 5;
		int width = graphics.guiWidth();
		for (FusionGear.Threat threat : threats) {
			double dx = threat.x() - player.getX(), dz = threat.z() - player.getZ();
			int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			Component where = Component.translatable("item.tomyen.astral_visor.dir." + direction(player, dx, dz));
			Component line = threat.kind() == FusionGear.Threat.METEOR
					? Component.translatable("item.tomyen.astral_visor.meteor", distance, where, Math.max(1, Math.round(threat.ticks() / 20.0f)))
					: Component.translatable("item.tomyen.astral_visor.ufo", distance, where);
			int textWidth = minecraft.font.width(line);
			int x = (width - textWidth) / 2;
			int accent = threat.kind() == FusionGear.Threat.METEOR ? 0xFFB04A : 0x7CF5D0;
			graphics.fill(x - 6, y - 3, x + textWidth + 6, y + 11, ARGB.color(150, 0x0C0A1E));
			graphics.fill(x - 6, y - 3, x - 4, y + 11, ARGB.color((int) (255 * pulse), accent));
			graphics.outline(x - 6, y - 3, textWidth + 12, 14, ARGB.color(120, 0xB49CFF));
			graphics.text(minecraft.font, line, x, y, ARGB.color(255, accent), true);
			y += 17;
		}
	}

	/** Which way something is from where the player's facing: ahead, ahead_left, left, behind_left, behind... */
	private static String direction(LocalPlayer player, double dx, double dz) {
		double bearing = Math.toDegrees(Math.atan2(-dx, dz));
		double relative = Mth.wrapDegrees(bearing - player.getYRot());
		String[] names = {"ahead", "ahead_right", "right", "behind_right", "behind", "behind_left", "left", "ahead_left"};
		int index = Math.floorMod((int) Math.round(relative / 45.0), 8);
		return names[index];
	}
}
