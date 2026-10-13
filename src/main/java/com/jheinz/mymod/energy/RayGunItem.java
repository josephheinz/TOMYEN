package com.jheinz.mymod.energy;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.GunStats;

/**
 * An alien's Ray Gun, a rare drop: a little sidearm that needs no ammo. Each zap does {@value #DAMAGE} damage within
 * {@value #RANGE} blocks and adds {@value #HEAT_PER_SHOT} heat, so five quick zaps overheat it; it cools by itself.
 * Small enough to fire well one-handed. Punching does nothing: there's nothing to load.
 */
public class RayGunItem extends EnergyWeaponItem {
	static final float DAMAGE = 4.0f;
	static final int RANGE = 24;
	private static final float HEAT_PER_SHOT = 20.0f;
	private static final float COOLING = 1.5f;
	private static final int COOLING_DELAY = 10;
	public static final int BEAM_COLOR = 0x7CFF4A;

	public RayGunItem(Properties properties) {
		super(properties, new GunStats(EnergyWeapons.RAY_ZAP, 1.2f, RANGE, 0, List.of(FireMode.SEMI)).withSpread(0.6f, 0.8f),
				List.of());
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
	public boolean isHandgun() {
		return true;
	}

	/** Its "magazine" is its charge, in percent: what's left before it overheats. */
	@Override
	public int capacity(ItemStack gun) {
		return 100;
	}

	@Override
	public int displayRounds(ItemStack gun, long now) {
		return Math.round(MAX_HEAT - heat(gun, now));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		long now = level.getGameTime();
		if (overheated(gun, now)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.mymod.energy.overheated").withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.FAIL;
		}
		GunStats fitted = stats(gun, player);
		if (level.isClientSide()) {
			GunRecoil.queue(player, 1, fitted);
			return InteractionResult.CONSUME;
		}
		ServerLevel serverLevel = (ServerLevel) level;
		Vec3 end = trace(serverLevel, player, player.getEyePosition(), aim(player, spread(player, fitted)), RANGE, 0, 0, false,
				(target, at) -> target.hurtServer(serverLevel, damage(serverLevel, EnergyWeapons.ALIEN_RAY, player), DAMAGE));
		EnergyBeam.send(serverLevel, player, muzzle(player), end, BEAM_COLOR, 0.14f, 250);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.RAY_ZAP, SoundSource.PLAYERS, 0.8f,
				1.0f + player.getRandom().nextFloat() * 0.15f);
		player.awardStat(Stats.ITEM_USED.get(this));
		if (addHeat(gun, now, HEAT_PER_SHOT)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.OVERHEAT, SoundSource.PLAYERS, 0.7f, 1.4f);
		}
		if (!player.hasInfiniteMaterials()) {
			gun.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		player.sendOverlayMessage(Component.translatable("item.mymod.ray_gun.no_reload"));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.ray_gun.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
