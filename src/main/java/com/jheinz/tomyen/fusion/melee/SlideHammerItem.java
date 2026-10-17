package com.jheinz.tomyen.fusion.melee;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.trombone.TromboneNotes;
import com.jheinz.tomyen.trombone.Trombones;

/**
 * The Slide Hammer (trombone + iron long sword, at a pond): a brass trombone bell for a hammer head on the long
 * sword's blade, the slide down the haft. A heavy weapon (12 at 1.0 a second) that bashes shields aside, and:
 * <ul>
 * <li>Hold use to wind up a slam. The slide works up the scale as you hold (the client plays the rising note,
 * MeleeFusionsClient), two octaves of Bb major in {@value #FULL_CHARGE} ticks; a ding at the top.
 * <li>Let go to slam: a shockwave round you from {@value #MIN_RADIUS} to {@value #MAX_RADIUS} blocks and
 * {@value #MIN_DAMAGE} to {@value #MAX_DAMAGE} damage by how long you held, flinging what it hits away and up.
 * A full charge ends in a fanfare.
 * </ul>
 */
public class SlideHammerItem extends Item {
	public static final int FULL_CHARGE = 40;
	public static final int MIN_CHARGE = 6;
	public static final double MIN_RADIUS = 2.5;
	public static final double MAX_RADIUS = 7.0;
	public static final float MIN_DAMAGE = 4.0f;
	public static final float MAX_DAMAGE = 16.0f;
	public static final int COOLDOWN = 30;
	private static final int BRASS = 0xE8B840;

	public SlideHammerItem(Properties properties) {
		super(properties);
	}

	/** How far the slam is wound up, 0 to 1, after holding for {@code ticks}. */
	public static float charge(int ticks) {
		return Mth.clamp((float) ticks / FULL_CHARGE, 0.0f, 1.0f);
	}

	/** The scale degree (0 to the top of two octaves) the slide has reached. */
	public static int degree(int ticks) {
		return Math.min(TromboneNotes.TOP, (int) (charge(ticks) * TromboneNotes.TOP + 0.001f));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		int ticks = getUseDuration(stack, user) - ticksRemaining;
		if (ticks == FULL_CHARGE) {
			server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7f, 2.0f);
			server.sendParticles(ParticleTypes.NOTE, user.getX(), user.getEyeY() + 0.6, user.getZ(), 0, 1.0, 0.0, 0.0, 1.0);
		} else if (ticks < FULL_CHARGE && ticks % 4 == 0) {
			// A note floats up as the slide climbs, its colour climbing with it (the note particle's colour is its x speed).
			server.sendParticles(ParticleTypes.NOTE, user.getX(), user.getEyeY() + 0.5, user.getZ(), 0, degree(ticks) / 24.0, 0.0, 0.0, 1.0);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingTime) {
		int ticks = getUseDuration(stack, user) - remainingTime;
		if (!(user instanceof Player player) || ticks < MIN_CHARGE) {
			return false;
		}
		float charge = charge(ticks);
		if (level instanceof ServerLevel server) {
			slam(server, player, charge, degree(ticks));
			stack.hurtAndBreak(1 + Math.round(3 * charge), player, EquipmentSlot.MAINHAND);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		if (!level.isClientSide()) {
			player.swing(InteractionHand.MAIN_HAND, stack.getAttackAnimation(), true);
		}
		return true;
	}

	private static void slam(ServerLevel level, Player player, float charge, int degree) {
		double radius = Mth.lerp(charge, MIN_RADIUS, MAX_RADIUS);
		float damage = Mth.lerp(charge, MIN_DAMAGE, MAX_DAMAGE);
		Vec3 centre = player.position();
		AABB box = player.getBoundingBox().inflate(radius, 1.5, radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> CrescentWaves.canHit(player, e))) {
			Vec3 away = new Vec3(target.getX() - centre.x, 0.0, target.getZ() - centre.z);
			double dist = away.length();
			if (dist > radius + target.getBbWidth() * 0.5) {
				continue;
			}
			// Full force at the hammer, half at the edge.
			float falloff = (float) (1.0 - 0.5 * Mth.clamp(dist / radius, 0.0, 1.0));
			if (target.hurtServer(level, level.damageSources().playerAttack(player), damage * falloff)) {
				Vec3 dir = dist > 1.0e-3 ? away.scale(1.0 / dist) : CrescentWaves.flatLook(player);
				double fling = (0.4 + 0.7 * charge) * falloff;
				target.push(dir.x * fling, 0.3 + 0.3 * charge, dir.z * fling);
				target.syncVelocity = true;
			}
		}

		// The shockwave: rings of earth thrown outward, brass dust, and the notes the slide played scattering.
		BlockPos below = player.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		BlockParticleOption dirt = ground.isAir() ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
		DustParticleOptions brass = new DustParticleOptions(BRASS, 1.3f);
		for (double r = 1.0; r <= radius; r += 1.0) {
			int points = (int) (r * 7);
			for (int i = 0; i < points; i++) {
				double a = 2.0 * Math.PI * i / points;
				double x = centre.x + Math.cos(a) * r;
				double z = centre.z + Math.sin(a) * r;
				if (dirt != null) {
					level.sendParticles(dirt, x, centre.y + 0.1, z, 2, 0.15, 0.05, 0.15, 0.15);
				}
				if (i % 2 == 0) {
					level.sendParticles(brass, x, centre.y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			}
		}
		for (int i = 0; i < 4 + degree; i++) {
			double a = 2.0 * Math.PI * i / (4 + degree);
			level.sendParticles(ParticleTypes.NOTE, centre.x + Math.cos(a) * 1.2, centre.y + 1.2, centre.z + Math.sin(a) * 1.2, 0,
					(degree + i) % 24 / 24.0, 0.0, 0.0, 1.0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.3, centre.z, 1 + Math.round(2 * charge), radius * 0.2, 0.1,
				radius * 0.2, 0.0);

		float pitch = Mth.clamp(TromboneNotes.pitch(TromboneNotes.semitone(degree)), 0.5f, 2.0f);
		level.playSound(null, centre.x, centre.y, centre.z, Trombones.ATTACK, SoundSource.PLAYERS, 1.2f, pitch);
		level.playSound(null, centre.x, centre.y, centre.z, charge >= 0.5f ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND,
				SoundSource.PLAYERS, 1.0f, 0.8f + 0.3f * charge);
		if (charge >= 1.0f) {
			level.playSound(null, centre.x, centre.y, centre.z, Trombones.FANFARE, SoundSource.PLAYERS, 1.0f, 1.0f);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		MeleeSwings.tooltip(tooltip, "item.tomyen.slide_hammer.charge", "item.tomyen.slide_hammer.slam", "item.tomyen.slide_hammer.shields");
	}
}
