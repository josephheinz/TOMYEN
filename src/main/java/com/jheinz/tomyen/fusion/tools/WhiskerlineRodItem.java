package com.jheinz.tomyen.fusion.tools;

import java.util.function.Consumer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.fishing.Fishing;
import com.jheinz.tomyen.fishing.RodHook;
import com.jheinz.tomyen.fishing.RodItem;
import com.jheinz.tomyen.fishing.RodModifiers;
import com.jheinz.tomyen.fusion.FusionTooltipItem;
import com.jheinz.tomyen.skullcrane.Skullcranes;

/**
 * The Whiskerline Rod (Giant Catfish + Grapple Claw, at a pond): a tier 3 rod strung with the river king's whisker and
 * tipped with the grapple's claw. It fishes like any mod rod, with a line and hold that big fish can't break, and its
 * cast flies {@value #CAST_BOOST}x as far. Cast at a creature or a boat and the claw takes hold (as a bobber does); then
 * each use, while it's more than {@value #LANDED} blocks off, reels it in a long way at once, with the grapple's yank
 * (heavier things come slower, a hooked creature is slowed on the line). Close enough, the next use lets it go.
 */
public class WhiskerlineRodItem extends RodItem {
	public static final float CAST_BOOST = 1.6f;
	public static final double LANDED = 3.5;
	private static final double REEL_PER_BLOCK = 0.13;
	private static final double REEL_MAX = 1.7;
	private static final int REEL_COOLDOWN = 8;

	public WhiskerlineRodItem(Properties properties, RodModifiers modifiers) {
		super(properties, 3, false, modifiers);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.fishing instanceof RodHook hook && !hook.isFighting() && hook.getHookedIn() != null) {
			Entity caught = hook.getHookedIn();
			if (caught.isAlive() && caught.distanceTo(player) > LANDED) {
				if (level instanceof ServerLevel server) {
					reel(server, player, caught);
					stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
				}
				player.getCooldowns().addCooldown(stack, REEL_COOLDOWN);
				return InteractionResult.SUCCESS;
			}
		}
		boolean casting = player.fishing == null;
		InteractionResult result = super.use(level, player, hand);
		if (casting && level instanceof ServerLevel && player.fishing instanceof RodHook hook) {
			hook.setDeltaMovement(hook.getDeltaMovement().scale(CAST_BOOST));
		}
		return result;
	}

	/** The grapple's yank, along the line: toward the angler, quicker the further, held back by weight. */
	private static void reel(ServerLevel level, Player player, Entity caught) {
		Vec3 toPlayer = player.position().subtract(caught.position());
		Vec3 flat = toPlayer.horizontal();
		double distance = flat.length();
		double resist = caught instanceof LivingEntity living ? 1.0 - Math.min(1.0, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) : 1.0;
		// Stop short of the angler: enough speed to cover the distance less a couple of blocks, in drag.
		double speed = Math.min((distance - 2.0) * REEL_PER_BLOCK, REEL_MAX) * Math.max(resist, 0.25);
		Vec3 along = distance < 1.0e-4 ? Vec3.ZERO : flat.scale(1.0 / distance);
		double lift = caught instanceof LivingEntity ? 0.3 + Math.max(0.0, toPlayer.y) * 0.08 : 0.05 + Math.max(0.0, toPlayer.y) * 0.05;
		caught.setDeltaMovement(along.scale(speed).add(0.0, lift, 0.0));
		caught.syncVelocity = true;
		if (caught instanceof LivingEntity living && !(caught instanceof Player)) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), player);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Fishing.REEL_CLICK, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, caught.getX(), caught.getY(), caught.getZ(), Skullcranes.CLAMP, SoundSource.PLAYERS, 0.8f, 1.2f);
		level.sendParticles(caught.isInWater() ? ParticleTypes.SPLASH : ParticleTypes.CRIT, caught.getX(), caught.getY(0.5), caught.getZ(), 10,
				0.3, 0.2, 0.3, 0.1);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		FusionTooltipItem.appendFusionLines(tooltip, "item.tomyen.whiskerline_rod.cast", "item.tomyen.whiskerline_rod.hook",
				"item.tomyen.whiskerline_rod.reel");
		super.appendHoverText(stack, context, display, tooltip, flag);
	}
}
