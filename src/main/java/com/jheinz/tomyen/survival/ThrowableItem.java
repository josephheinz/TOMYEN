package com.jheinz.tomyen.survival;

import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A hand-thrown bomb (the Molotov and the pipe bomb): lobbed like a snowball, a little shorter, with a short wait between throws. Dispensers lob them too. */
public class ThrowableItem extends Item implements ProjectileItem {
	private final Projectile.ProjectileFactory<? extends ThrowableItemProjectile> thrown;
	private final Function<Level, ? extends ThrowableItemProjectile> dispensed;
	private final float speed;
	private final int cooldown;

	public ThrowableItem(Properties properties, Projectile.ProjectileFactory<? extends ThrowableItemProjectile> thrown,
			Function<Level, ? extends ThrowableItemProjectile> dispensed, float speed, int cooldown) {
		super(properties);
		this.thrown = thrown;
		this.dispensed = dispensed;
		this.speed = speed;
		this.cooldown = cooldown;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.55f);
		if (level instanceof ServerLevel server) {
			Projectile.spawnProjectileFromRotation(thrown, server, stack, player, 0.0f, speed, 1.0f);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		player.getCooldowns().addCooldown(stack, cooldown);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		ThrowableItemProjectile projectile = dispensed.apply(level);
		projectile.setPos(position.x(), position.y(), position.z());
		projectile.setItem(stack);
		return projectile;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		builder.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
	}
}
