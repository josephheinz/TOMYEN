package com.jheinz.mymod.client.skullcrane;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.skullcrane.ServoGear;

/**
 * Client side of the Servo Arm Backpack's arms: for each wearer, what each arm is reaching for (the item the server
 * says it's after, or for your own arms, a block you're mining from further than you could reach unaided) and how far
 * it's got, easing out over {@value #OUT_TICKS} ticks so it arrives as the server grabs the item, and back in over
 * {@value #BACK_TICKS} once it's let go or the item's gone.
 */
public final class ServoArmsClient {
	private static final float OUT_TICKS = ServoGear.REACH_TICKS - 1;
	private static final float BACK_TICKS = 6.0f;
	/** Mining further off than this, an arm reaches for the block. */
	private static final double MINING_REACH = 3.0;

	private static final Int2ObjectMap<Arms> WEARERS = new Int2ObjectOpenHashMap<>();

	private ServoArmsClient() {
	}

	/** One wearer's arms. */
	public static final class Arms {
		final int[] items = new int[ServoArmRig.ARMS];
		final boolean[] mining = new boolean[ServoArmRig.ARMS];
		final Vec3[] targets = new Vec3[ServoArmRig.ARMS];
		final float[] progress = new float[ServoArmRig.ARMS];
		final float[] progressO = new float[ServoArmRig.ARMS];

		Arms() {
			Arrays.fill(items, -1);
		}

		/** Where arm {@code arm} is reaching, if anywhere. */
		public @Nullable Vec3 target(int arm) {
			return targets[arm];
		}

		public float progress(int arm, float partialTicks) {
			return Mth.lerp(partialTicks, progressO[arm], progress[arm]);
		}

		boolean idle() {
			for (int i = 0; i < items.length; i++) {
				if (items[i] >= 0 || mining[i] || progress[i] > 0.0f || progressO[i] > 0.0f) {
					return false;
				}
			}
			return true;
		}
	}

	public static @Nullable Arms get(int playerId) {
		return WEARERS.get(playerId);
	}

	static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(ServoGear.Reach.TYPE, (payload, context) -> {
			if (payload.arm() >= 0 && payload.arm() < ServoArmRig.ARMS) {
				Arms arms = WEARERS.computeIfAbsent(payload.playerId(), id -> new Arms());
				arms.items[payload.arm()] = payload.itemId();
				if (payload.itemId() >= 0) {
					arms.mining[payload.arm()] = false;
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ServoArmsClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			WEARERS.clear();
			return;
		}
		mining(minecraft);
		var iterator = WEARERS.int2ObjectEntrySet().iterator();
		while (iterator.hasNext()) {
			Arms arms = iterator.next().getValue();
			for (int i = 0; i < ServoArmRig.ARMS; i++) {
				arms.progressO[i] = arms.progress[i];
				Entity item = arms.items[i] >= 0 ? level.getEntity(arms.items[i]) : null;
				if (item != null && item.isAlive()) {
					arms.targets[i] = item.position().add(0.0, 0.15, 0.0);
				}
				boolean reaching = item != null && item.isAlive() || arms.mining[i];
				arms.progress[i] = Mth.approach(arms.progress[i], reaching ? 1.0f : 0.0f, 1.0f / (reaching ? OUT_TICKS : BACK_TICKS));
				if (arms.progress[i] <= 0.0f && arms.progressO[i] <= 0.0f) {
					arms.targets[i] = null;
				}
			}
			if (arms.idle()) {
				iterator.remove();
			}
		}
	}

	/** Your own arms: a free one reaches for the block you're mining, if it's further than you could reach unaided. */
	private static void mining(Minecraft minecraft) {
		var player = minecraft.player;
		Arms arms = player == null ? null : WEARERS.get(player.getId());
		Vec3 block = null;
		if (player != null && ServoGear.wearsBackpack(player) && minecraft.options.keyAttack.isDown()
				&& minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& hit.getLocation().distanceTo(player.getEyePosition()) > MINING_REACH) {
			block = hit.getLocation();
		}
		if (block == null) {
			if (arms != null) {
				Arrays.fill(arms.mining, false);
			}
			return;
		}
		if (arms == null) {
			arms = new Arms();
			WEARERS.put(player.getId(), arms);
		}
		for (int i = 0; i < ServoArmRig.ARMS; i++) {
			if (arms.mining[i]) {
				arms.targets[i] = block;
				return;
			}
		}
		for (int i = 0; i < ServoArmRig.ARMS; i++) {
			if (arms.items[i] < 0 && arms.progress[i] <= 0.0f) {
				arms.mining[i] = true;
				arms.targets[i] = block;
				return;
			}
		}
	}
}
