package com.jheinz.tomyen.energy;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.FireMode;
import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;

/**
 * The Laser Rifle: a uranium-fed hitscan beam. A Uranium Power Cell gives {@value #CELL_SHOTS} shots, kept as the
 * stack's loaded magazine so the ammo HUD and Smart-Link read it like any gun's. Each shot does
 * {@value #DAMAGE} damage, sets what it hits alight for a couple of seconds, and burns on through into one more target.
 * Semi-auto, or full-auto at five shots a second, it heats {@value #HEAT_PER_SHOT} a shot and cools quickly once the
 * trigger's let go, so about eleven shots back to back overheat it: then it vents, locked out until it's cooled (two
 * and a half seconds). Punching loads a cell into a flat rifle (sneak to swap out a part-used one; what was left in it
 * is lost). Creative needs no cells.
 */
public class LaserRifleItem extends EnergyWeaponItem {
	public static final int CELL_SHOTS = 30;
	static final float DAMAGE = 7.0f;
	private static final float HEAT_PER_SHOT = 9.0f;
	private static final float COOLING = 2.0f;
	private static final int COOLING_DELAY = 8;
	private static final int BEAM_COLOR = 0x40E8FF;

	public LaserRifleItem(Properties properties, GunStats stats) {
		super(properties, stats, List.of(new Ammo(EnergyWeapons.URANIUM_POWER_CELL, DAMAGE, EnergyWeapons.LASER, BEAM_COLOR, 1, 2.0f)));
	}

	@Override
	protected float heatPerShot() {
		return HEAT_PER_SHOT;
	}

	@Override
	protected float coolingPerTick() {
		return COOLING;
	}

	@Override
	protected int coolingDelay() {
		return COOLING_DELAY;
	}

	@Override
	public int beamColor() {
		return BEAM_COLOR;
	}

	@Override
	public int capacity(ItemStack gun) {
		return CELL_SHOTS;
	}

	public static int charge(ItemStack gun) {
		MagazineContents cell = gun.get(ModComponents.LOADED_MAGAZINE);
		return cell == null ? 0 : cell.rounds();
	}

	@Override
	public int displayRounds(ItemStack gun, long now) {
		return charge(gun);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		boolean free = player.hasInfiniteMaterials();
		long now = level.getGameTime();
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		if (!free && charge(gun) <= 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return InteractionResult.FAIL;
		}
		if (overheated(gun, now)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.tomyen.energy.overheated").withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.FAIL;
		}
		GunStats fitted = stats(gun, player);
		if (level.isClientSide()) {
			GunRecoil.queue(player, 1, fitted);
			return InteractionResult.CONSUME;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		Ammo round = ammo().getFirst();
		Vec3 from = player.getEyePosition();
		Vec3 end = trace(serverLevel, player, from, aim(player, spread(player, fitted)), fitted.range(), round.pierce(), 0, false,
				(target, at) -> {
					target.hurtServer(serverLevel, damage(serverLevel, round.damageType(), player), round.damage());
					target.igniteForSeconds(round.burnSeconds());
				});
		EnergyBeam.send(serverLevel, player, muzzle(player), end, BEAM_COLOR, 0.22f, 220);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.LASER_SHOT, SoundSource.PLAYERS, 0.9f,
				0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));
		if (addHeat(gun, now, HEAT_PER_SHOT)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.OVERHEAT, SoundSource.PLAYERS, 0.9f, 1.0f);
			player.sendOverlayMessage(Component.translatable("item.tomyen.energy.overheated").withStyle(ChatFormatting.GOLD));
		}
		if (!free) {
			gun.set(ModComponents.LOADED_MAGAZINE, gun.get(ModComponents.LOADED_MAGAZINE).withRounds(charge(gun) - 1));
			gun.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.CONSUME;
	}

	/**
	 * Punching loads a Uranium Power Cell into a flat rifle, or with sneak swaps out one that isn't. A spent cell is used
	 * up.
	 */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		int charge = charge(gun);
		if (charge >= CELL_SHOTS) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.laser_rifle.cell_full"));
			return;
		}
		if (charge > 0 && !player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.laser_rifle.cell_not_flat"));
			return;
		}
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(EnergyWeapons.URANIUM_POWER_CELL)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.laser_rifle.no_cells").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return;
		}
		ItemStack cell = player.getInventory().getItem(slot).split(1);
		gun.set(ModComponents.LOADED_MAGAZINE, new MagazineContents(cell.typeHolder(), CELL_SHOTS));
		player.getCooldowns().addCooldown(gun, Math.round(stats().reloadTicks() * reloadFactor(player)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.CELL_LOAD, SoundSource.PLAYERS, 0.9f, 1.0f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		if (!stack.has(ModComponents.LOADED_MAGAZINE)) {
			tooltip.accept(Component.translatable("item.tomyen.laser_rifle.no_cell").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.tomyen.laser_rifle.charge", charge(stack), CELL_SHOTS).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.tomyen.gun.fire_mode", Component.translatable(fireMode(stack).translationKey()))
				.withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.tomyen.gun.switch_fire_mode", Component.keybind("key.tomyen.fire_mode"))
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.laser_rifle.hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Semi-auto first, full-auto at five shots a second. */
	public static GunStats defaultStats() {
		return new GunStats(EnergyWeapons.LASER_SHOT, 0.8f, 72, 30, List.of(FireMode.SEMI, FireMode.AUTO)).withSpread(0.25f, 0.2f);
	}
}
