package com.jheinz.mymod.client.schnozzle;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import com.jheinz.mymod.schnozzle.NoseHairWhipItem;

/**
 * Where a whip hand is in its crack or snag: one timeline that the first-person hand, the third-person arm and the lash
 * all read, so they move as one.
 *
 * <p>A crack runs over the attack swing ({@link NoseHairWhipItem#ATTACK_SWING}): the hand winds up back over the shoulder
 * while the lash streams out behind it, then snaps forward and stops; the lash lags, so its loop is still rolling out
 * when the hand stops, and it cracks straight out at {@link #CRACK_AT}, after which hand and lash settle back. A snag
 * ({@link WhipLashes#snagProgress}) is a short throw: a little wind-up, a throw that the lash catches at
 * {@link #SNAG_CATCH}, a hold while it pulls, a yank back and the return.
 *
 * @param kind what the hand is doing
 * @param progress how far through it, 0 to 1
 */
public record WhipMotion(Kind kind, float progress) {
	public enum Kind {
		REST, CRACK, SNAG
	}

	/** The poses the hand passes through. Each user of the timeline (hand, arm, wrist) has its own values for them. */
	public enum Pose {
		REST, WINDUP, STRIKE, YANK
	}

	/** Where the motion is: {@code t} of the way (already eased) from one pose to the next. */
	public record Blend(Pose from, Pose to, float t) {
	}

	public static final WhipMotion RESTING = new WhipMotion(Kind.REST, 0.0f);

	/** The crack: wind-up until here, the hand's snap until {@link #CRACK_STRIKE}, the lash's turnover until {@link #CRACK_AT}. */
	public static final float CRACK_WINDUP = 0.2f;
	public static final float CRACK_STRIKE = 0.32f;
	public static final float CRACK_AT = 0.4f;
	/** The snag: wind-up, throw, the lash catching hold, the hold, and the yank. */
	public static final float SNAG_WINDUP = 0.1f;
	public static final float SNAG_STRIKE = 0.2f;
	public static final float SNAG_CATCH = 0.28f;
	public static final float SNAG_HOLD = 0.55f;
	public static final float SNAG_YANK = 0.7f;

	/** The motion of the whip in {@code arm} (the main arm only ever cracks or snags), from the holder's render state. */
	public static WhipMotion of(ArmedEntityRenderState state, HumanoidArm arm) {
		// Only players (and mannequins, harmlessly) lash; a mob or armor stand just holds it.
		if (!(state instanceof AvatarRenderState avatar) || arm != state.mainArm
				|| !(state.getMainHandItemStack().getItem() instanceof NoseHairWhipItem)) {
			return RESTING;
		}
		float snag = WhipLashes.snagProgress(avatar.id, avatar.ageInTicks);
		return snag >= 0.0f ? new WhipMotion(Kind.SNAG, snag) : crack(state.currentSwing, state.swingAnimation);
	}

	/** The crack the swing is making, if it is the whip's attack swing. */
	public static WhipMotion crack(LivingEntity.@Nullable SwingDescription swing, float swingAnimation) {
		if (swing != null && swing.hand() == InteractionHand.MAIN_HAND && swing.animation().equals(NoseHairWhipItem.ATTACK_SWING)) {
			return new WhipMotion(Kind.CRACK, Mth.clamp(swingAnimation, 0.0f, 1.0f));
		}
		return RESTING;
	}

	public boolean active() {
		return kind != Kind.REST;
	}

	/** The hand's keyframes: eased out of rest into the wind-up, accelerating into the strike, and smoothly back. */
	public Blend blend() {
		float p = progress;
		return switch (kind) {
			case REST -> new Blend(Pose.REST, Pose.REST, 0.0f);
			case CRACK -> p < CRACK_WINDUP ? new Blend(Pose.REST, Pose.WINDUP, easeOut(p / CRACK_WINDUP))
					: p < CRACK_STRIKE ? new Blend(Pose.WINDUP, Pose.STRIKE, easeIn((p - CRACK_WINDUP) / (CRACK_STRIKE - CRACK_WINDUP)))
					: new Blend(Pose.STRIKE, Pose.REST, easeInOut((p - CRACK_STRIKE) / (1.0f - CRACK_STRIKE)));
			case SNAG -> p < SNAG_WINDUP ? new Blend(Pose.REST, Pose.WINDUP, easeOut(p / SNAG_WINDUP))
					: p < SNAG_STRIKE ? new Blend(Pose.WINDUP, Pose.STRIKE, easeIn((p - SNAG_WINDUP) / (SNAG_STRIKE - SNAG_WINDUP)))
					: p < SNAG_HOLD ? new Blend(Pose.STRIKE, Pose.STRIKE, 0.0f)
					: p < SNAG_YANK ? new Blend(Pose.STRIKE, Pose.YANK, easeInOut((p - SNAG_HOLD) / (SNAG_YANK - SNAG_HOLD)))
					: new Blend(Pose.YANK, Pose.REST, easeInOut((p - SNAG_YANK) / (1.0f - SNAG_YANK)));
		};
	}

	static float easeOut(float t) {
		float u = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
		return 1.0f - u * u * u;
	}

	static float easeIn(float t) {
		float u = Mth.clamp(t, 0.0f, 1.0f);
		return u * u;
	}

	static float easeInOut(float t) {
		float u = Mth.clamp(t, 0.0f, 1.0f);
		return u * u * (3.0f - 2.0f * u);
	}
}
