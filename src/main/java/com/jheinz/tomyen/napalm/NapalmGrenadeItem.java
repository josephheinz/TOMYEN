package com.jheinz.tomyen.napalm;

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

/** Thrown like a snowball but a little shorter (it's heavier), with a second's wait before the next. Dispensers lob them too. */
public class NapalmGrenadeItem extends Item implements ProjectileItem {
	private static final float THROW_SPEED = 1.2f;
	private static final int COOLDOWN_TICKS = 20;

	public NapalmGrenadeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.5f);
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation((server, thrower, item) -> new NapalmGrenade(server, thrower, item), serverLevel, stack,
					player, 0.0f, THROW_SPEED, 1.0f);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		NapalmGrenade grenade = new NapalmGrenade(Napalm.GRENADE, level);
		grenade.setPos(position.x(), position.y(), position.z());
		grenade.setItem(stack);
		return grenade;
	}
}
