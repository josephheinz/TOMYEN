package com.jheinz.tomyen.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.client.Keyframes.Ease;
import com.jheinz.tomyen.client.Keyframes.Path;
import com.jheinz.tomyen.client.Keyframes.Track;
import com.jheinz.tomyen.gunsmith.GunsmithGunItem;
import com.jheinz.tomyen.lmg.MachineGunItem;
import com.jheinz.tomyen.shoddy.ShoddyGunItem;
import com.jheinz.tomyen.shotgun.ShotgunAction;
import com.jheinz.tomyen.shotgun.ShotgunItem;
import com.jheinz.tomyen.shotgun.Shotguns;

/**
 * Third-person arms for a player holding a gun in both hands while they work it: a simpler version of what the hands do
 * in first person, on the same timelines, so other players (and the player themselves in third-person view) see the
 * support arm drop to the magazine and away for a new one, slap it home, thumb shells into a tube, ride a pump, knock a
 * jam loose, or reach back to the tanks for fuel, with the gun dipping a little while it's worked. The shotguns and
 * homemade guns are read from the action on their stack; the rest from GunReloads. The RPG-7 has its own poses
 * (LauncherAnimation).
 *
 * <p>Paths are of arm angles (radians: x, y, z rotation of the support arm for a right-handed player), keyframed like
 * the first-person hands, and blended in from the vanilla two-handed hold.
 */
public final class GunArmPoses {
	// Support-arm angles for a right-handed player (x raises the arm forward when negative, y swings it across the body).
	private static final Vector3f HOLD = new Vector3f(-1.5f, 0.6f, 0.0f);
	private static final Vector3f MAG_WELL = new Vector3f(-1.1f, 0.45f, 0.0f);
	private static final Vector3f MAG_OUT = new Vector3f(-0.85f, 0.45f, 0.0f);
	private static final Vector3f POUCH = new Vector3f(-0.15f, 0.15f, -0.15f);
	private static final Vector3f SLAPPED = new Vector3f(-1.25f, 0.45f, 0.0f);
	private static final Vector3f TOP = new Vector3f(-1.85f, 0.4f, 0.0f);
	private static final Vector3f ABOVE = new Vector3f(-2.1f, 0.4f, 0.0f);
	private static final Vector3f BEHIND = new Vector3f(0.6f, 0.25f, -0.3f);
	private static final Vector3f PORT = new Vector3f(-1.25f, 0.55f, 0.0f);
	private static final Vector3f FORE_END_BACK = new Vector3f(-1.25f, 0.5f, 0.0f);

	/** A magazine swap, on GunAnimation's timeline: down to it, yanked, away for a new one, in, slapped home. */
	private static final Path MAGAZINE = Keyframes.path(HOLD).key(0.12f, MAG_WELL).key(0.3f, MAG_OUT, 0.0f, Ease.OUT)
			.key(0.42f, POUCH).hold(0.48f).key(0.58f, MAG_OUT).key(0.655f, new Vector3f(MAG_OUT).add(0.1f, 0.0f, 0.0f))
			.key(0.67f, SLAPPED, 0.0f, Ease.IN).key(0.75f, MAG_WELL).key(0.92f, HOLD).build();
	/** The M249's belt change, on M249Animation's timeline: cover up, belt off, box changed, belt laid, cover slammed. */
	private static final Path BELT = Keyframes.path(HOLD).key(0.06f, TOP).key(0.16f, ABOVE).key(0.3f, MAG_WELL)
			.hold(0.38f).key(0.44f, POUCH, 0.0f, Ease.OUT).key(0.5f, MAG_WELL).hold(0.58f).key(0.68f, TOP).key(0.72f, ABOVE)
			.key(0.8f, TOP, 0.0f, Ease.IN).key(0.94f, HOLD).build();
	/** A revolver round: to the cylinder, thumbed in, back. */
	private static final Path ROUND = Keyframes.path(HOLD).key(0.18f, MAG_OUT).key(0.3f, MAG_WELL)
			.key(0.4f, SLAPPED, 0.0f, Ease.IN).key(0.5f, MAG_OUT).key(1.0f, HOLD).build();
	/** A flamethrower refuel: back over the hip to the tanks, a while there, up to the valve, back to the grip. */
	private static final Path REFUEL = Keyframes.path(HOLD).key(0.24f, BEHIND).hold(0.52f).key(0.68f, TOP)
			.hold(0.76f).key(0.94f, HOLD).build();
	/** One shell into a tube or breech: down to the belt for it, up to the port, pushed in, as ShotgunAnimation. */
	private static final Path SHELL = Keyframes.path(PORT).key(0.3f, POUCH).key(0.55f, MAG_OUT)
			.key(0.72f, SLAPPED, 0.0f, Ease.IN).key(1.0f, PORT).build();
	/** A pump stroke: the fore-end ridden back and slammed home, as ShotgunAnimation. */
	private static final Path PUMP = Keyframes.path(HOLD).hold(0.25f).key(0.45f, FORE_END_BACK).hold(0.52f)
			.key(0.68f, HOLD, 0.0f, Ease.IN).build();
	/** A homemade gun's part worked by hand: over to it by the top of the gun, drawn back, home, back to the hold. */
	private static final Path WORK = Keyframes.path(HOLD).key(0.23f, TOP).key(0.45f, new Vector3f(TOP).add(0.25f, -0.1f, 0.0f))
			.hold(0.52f).key(0.68f, TOP).key(1.0f, HOLD).build();
	/** Clearing a jam: two knocks from above, then the part worked, as ShotgunAnimation. */
	private static final Path CLEAR = Keyframes.path(HOLD).key(0.13f, ABOVE).key(0.24f, TOP, 0.0f, Ease.IN)
			.key(0.31f, ABOVE, 0.0f, Ease.OUT).key(0.40f, TOP, 0.0f, Ease.IN).key(0.5f, TOP)
			.key(0.62f, new Vector3f(TOP).add(0.25f, -0.1f, 0.0f)).hold(0.7f).key(0.8f, TOP).key(0.95f, HOLD).build();

	/** How far the gun arm dips while the gun is worked (radians), for each kind of handling, over its progress. */
	private static final Track RELOAD_DIP = Keyframes.track(0.0f).key(0.12f, 0.3f).hold(0.8f).key(0.95f, 0.0f).build();
	private static final Track SHORT_DIP = Keyframes.track(0.0f).key(0.2f, 0.15f).hold(0.7f).key(1.0f, 0.0f).build();

	/** How quickly an arm eases toward its pose (seconds), so starting and stopping never snaps. */
	private static final float SETTLE_SECONDS = 0.06f;

	/** Each player's support-arm angles and gun-arm dip as last drawn, and when. */
	private record Drawn(Vector3f arm, float dip, long millis) {
	}

	private static final Map<Integer, Drawn> DRAWN = new HashMap<>();

	private GunArmPoses() {
	}

	/** A pose to blend toward: the support arm's angles and how far the gun arm dips. */
	private record Target(Vector3f arm, float dip) {
	}

	/** Poses a two-handed gun holder's arms for what their gun is doing, after vanilla has set the two-handed hold. */
	public static void pose(HumanoidModel<?> model, AvatarRenderState state) {
		boolean right = state.mainArm == HumanoidArm.RIGHT;
		float side = right ? 1.0f : -1.0f;
		ModelPart main = right ? model.rightArm : model.leftArm;
		ModelPart off = right ? model.leftArm : model.rightArm;
		Vector3f vanilla = new Vector3f(off.xRot, off.yRot * side, off.zRot * side);

		Target target = target(state.getMainHandItemStack(), state.id);
		long now = Util.getMillis();
		Drawn last = DRAWN.get(state.id);
		Vector3f arm;
		float dip;
		if (target == null && (last == null || last.dip() < 0.001f && last.arm().distance(vanilla) < 0.01f)) {
			// Nothing going on and nothing to ease out of: leave vanilla's pose alone.
			DRAWN.remove(state.id);
			return;
		}
		// Toward the pose, from where the arm was drawn last (or vanilla's hold, starting out), the support arm following
		// the head's pitch while it works the gun under the muzzle.
		Vector3f goal = target == null ? vanilla
				: new Vector3f(target.arm()).add(model.head.xRot * 0.5f, model.head.yRot * side, 0.0f);
		float goalDip = target == null ? 0.0f : target.dip();
		if (last == null || now - last.millis() > 250L) {
			arm = last == null ? vanilla : goal;
			dip = 0.0f;
		} else {
			float k = 1.0f - (float) Math.exp(-(now - last.millis()) / 1000.0f / SETTLE_SECONDS);
			arm = new Vector3f(last.arm()).lerp(goal, k);
			dip = Mth.lerp(k, last.dip(), goalDip);
		}
		DRAWN.put(state.id, new Drawn(arm, dip, now));

		off.xRot = arm.x;
		off.yRot = arm.y * side;
		off.zRot = arm.z * side;
		main.xRot += dip;
		main.yRot += 0.4f * dip * side;
		if (DRAWN.size() > 64) {
			// Players who walked off while mid-reload.
			DRAWN.entrySet().removeIf(entry -> now - entry.getValue().millis() > 5000L);
		}
	}

	/** Where the support arm should be (head-relative angles before pitch) for what this gun is doing, if anything. */
	private static Target target(ItemStack stack, int holderId) {
		if (stack.getItem() instanceof ShotgunItem) {
			ShotgunAction action = stack.get(Shotguns.SHOTGUN_ACTION);
			Minecraft minecraft = Minecraft.getInstance();
			if (action == null || minecraft.level == null) {
				return null;
			}
			float p = action.progress(minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			// A homemade or Gunsmith gun's part is reached for and worked by hand, not ridden like a pump.
			boolean shoddy = stack.getItem() instanceof ShoddyGunItem || stack.getItem() instanceof GunsmithGunItem;
			return switch (action.kind()) {
				case LOAD -> new Target(SHELL.sample(p).point(), 0.2f);
				case PUMP -> p < 1.0f ? new Target((shoddy ? WORK : PUMP).sample(p).point(), SHORT_DIP.sample(p)) : null;
				case CLEAR -> p < 1.0f ? new Target(CLEAR.sample(p).point(), SHORT_DIP.sample(p)) : null;
				case CYCLE -> null;
			};
		}
		GunReloads.Reload reload = GunReloads.of(holderId);
		if (reload == null || reload.item() != stack.getItem()) {
			return null;
		}
		float p = reload.progress();
		return switch (reload.kind()) {
			case MAGAZINE -> new Target((stack.getItem() instanceof MachineGunItem ? BELT : MAGAZINE).sample(p).point(), RELOAD_DIP.sample(p));
			case ROUND -> new Target(ROUND.sample(p).point(), SHORT_DIP.sample(p));
			case REFUEL -> new Target(REFUEL.sample(p).point(), RELOAD_DIP.sample(p));
		};
	}
}
