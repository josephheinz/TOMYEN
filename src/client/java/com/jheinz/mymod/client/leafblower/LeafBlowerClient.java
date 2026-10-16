package com.jheinz.mymod.client.leafblower;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.client.GunDisplay;
import com.jheinz.mymod.client.GunHands;
import com.jheinz.mymod.leafblower.LeafBlowerItem;
import com.jheinz.mymod.leafblower.LeafBlowers;
import com.jheinz.mymod.leafblower.Wind;

/**
 * Leaf blowers on the client. For everyone holding one nearby it runs the engine sound ({@link BlowerSound}, with the pull
 * start when it's drawn with fuel in), puffs two-stroke smoke out of the exhaust, and while it runs draws the air: streaks
 * of gust racing out along the cone when blowing, or drawn in from its far end when a Blower-Vac sucks. Also the HUD
 * (fuel, mode, bag) and the held pose (LeafBlowerRig, from the model generator).
 */
public final class LeafBlowerClient {
	private static final double RANGE = 48.0;
	private static final Map<Integer, BlowerSound> ENGINES = new HashMap<>();
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int RED = 0xFFFF5555;
	private static final int FUEL = 0xFFF0A830;

	private LeafBlowerClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				tick(client, client.level);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ENGINES.clear());
	}

	public static GunDisplay display(boolean vacuum) {
		float[] d = vacuum ? LeafBlowerRig.LEAF_VACUUM_DISPLAY : LeafBlowerRig.LEAF_BLOWER_DISPLAY;
		return new GunDisplay(new Vector3f(d[0], d[1], d[2]), d[3], d[4]);
	}

	public static GunHands.Anchors anchors(boolean vacuum) {
		return vacuum ? new GunHands.Anchors(LeafBlowerRig.LEAF_VACUUM_GRIP, LeafBlowerRig.LEAF_VACUUM_SUPPORT)
				: new GunHands.Anchors(LeafBlowerRig.LEAF_BLOWER_GRIP, LeafBlowerRig.LEAF_BLOWER_SUPPORT);
	}

	private static void tick(Minecraft minecraft, ClientLevel level) {
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		ENGINES.values().removeIf(BlowerSound::isStopped);
		for (AbstractClientPlayer player : level.players()) {
			ItemStack held = player.getMainHandItem();
			if (!(held.getItem() instanceof LeafBlowerItem) || player.position().distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			if (!BlowerSound.turning(player)) {
				continue;
			}
			ENGINES.computeIfAbsent(player.getId(), id -> {
				BlowerSound engine = new BlowerSound(player);
				minecraft.getSoundManager().play(engine);
				level.playLocalSound(player.getX(), player.getY(), player.getZ(), LeafBlowers.START, SoundSource.PLAYERS, 0.8f, 1.0f, false);
				return engine;
			});
			boolean running = LeafBlowerItem.running(held, now);
			Vec3 nozzle = LeafBlowerItem.nozzle(player, 1.0f);
			if (random.nextInt(running ? 2 : 6) == 0) {
				// Blue-grey two-stroke smoke out of the muffler, behind and right of the holder's hand.
				Vec3 back = player.getViewVector(1.0f).scale(-0.2);
				level.addParticle(ParticleTypes.SMOKE, player.getX() + back.x, player.getEyeY() - 0.75, player.getZ() + back.z, 0.0, 0.03, 0.0);
			}
			if (running) {
				air(level, player, nozzle, LeafBlowerItem.aim(player, nozzle, 1.0f), LeafBlowerItem.sucking(held), random);
			}
		}
	}

	private static void air(ClientLevel level, AbstractClientPlayer player, Vec3 nozzle, Vec3 dir, boolean suck, RandomSource random) {
		double range = suck ? Wind.SUCK_RANGE : Wind.BLOW_RANGE;
		double spread = Math.tan(Math.toRadians((suck ? Wind.SUCK_HALF_ANGLE : Wind.BLOW_HALF_ANGLE) * 0.8));
		for (int i = 0; i < 3; i++) {
			Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(spread * 0.5);
			Vec3 way = dir.add(jitter).normalize();
			if (suck) {
				Vec3 from = nozzle.add(way.scale(range * (0.5 + random.nextDouble() * 0.5)));
				Vec3 v = nozzle.subtract(from).scale(0.12);
				level.addParticle(ParticleTypes.POOF, from.x, from.y, from.z, v.x, v.y, v.z);
			} else {
				Vec3 v = way.scale(0.7 + random.nextDouble() * 0.5);
				level.addParticle(ParticleTypes.POOF, nozzle.x, nozzle.y, nozzle.z, v.x, v.y, v.z);
			}
		}
		if (random.nextInt(4) == 0) {
			Vec3 at = nozzle.add(dir.scale(range * (0.3 + random.nextDouble() * 0.5)));
			level.addParticle(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 0.0, 0.0, 0.0);
		}
	}

	/** "Fuel 64%" (then the mode and bag on a Blower-Vac) over the fuel bar. */
	public static void extractHud(GuiGraphicsExtractor graphics, Minecraft minecraft, ItemStack held, LeafBlowerItem blower, int x, int y) {
		float fill = LeafBlowerItem.fill(held);
		int percent = Math.round(fill * 100.0f);
		boolean free = minecraft.player != null && minecraft.player.hasInfiniteMaterials();
		Component fuel = percent == 0 && !free ? Component.translatable("hud.mymod.leaf_blower.empty")
				: Component.translatable("hud.mymod.leaf_blower.fuel", percent);
		graphics.text(minecraft.font, fuel, x, y, percent == 0 && !free ? RED : WHITE, true);
		int tx = x + minecraft.font.width(fuel) + 6;
		if (blower.vacuum()) {
			boolean suck = LeafBlowerItem.sucking(held);
			Component mode = Component.translatable(suck ? "hud.mymod.leaf_vacuum.suck" : "hud.mymod.leaf_vacuum.blow");
			graphics.text(minecraft.font, mode, tx, y, suck ? 0xFF55FFFF : 0xFFFFFF55, true);
			tx += minecraft.font.width(mode) + 6;
			graphics.text(minecraft.font, Component.translatable("hud.mymod.leaf_vacuum.bag", LeafBlowerItem.bagUsed(held),
					LeafBlowerItem.BAG_SIZE), tx, y, GRAY, true);
		}
		int width = 40;
		graphics.fill(x, y + 11, x + width, y + 14, 0xFF202020);
		graphics.fill(x, y + 11, x + Math.round(width * fill), y + 14, fill > 0.25f ? FUEL : RED);
		if (percent == 0 && !free) {
			graphics.text(minecraft.font, Component.translatable("hud.mymod.punch_to_reload"), x, y + 16, GRAY, true);
		}
	}
}
