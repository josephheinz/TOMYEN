package com.jheinz.mymod.schnozzle;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/** A Giant Booger: thrown like a snowball, it sticks to a mob and slows it (see {@link ThrownBooger}). Dispensers throw it too. */
public class GiantBoogerItem extends Item implements ProjectileItem {
	public GiantBoogerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.NEUTRAL, 0.6f,
				0.6f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(ThrownBooger::new, serverLevel, stack, player, 0.0f, 1.2f, 1.0f);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		return new ThrownBooger(level, position.x(), position.y(), position.z(), stack);
	}
}
