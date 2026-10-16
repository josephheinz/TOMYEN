package com.jheinz.tomyen.napalm;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.Ammo;
import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.MagazineContents;
import com.jheinz.tomyen.ModComponents;
import com.jheinz.tomyen.industry.Industry;

/**
 * An M2 flamethrower. A Napalm Canister fills its tank with {@link #TANK} units of fuel, kept as the stack's loaded
 * magazine so the ammo HUD and Smart-Link read it like any gun's. Holding use sprays burning napalm
 * ({@link FlameStream}) at {@link #FUEL_PER_TICK} a tick, so a full tank lasts ten seconds; the first tick of a spray
 * clicks the igniter. It won't light with the nozzle underwater. Punching loads a canister into a dry tank (sneak to
 * swap out a part-used one; what was left in it is lost) and hands back the empty canister. Creative needs no fuel.
 *
 * <p>The game time of its last spray is kept on the stack ({@link Napalm#FLAME_FIRED_AT}), set on both sides, so every
 * client can draw the stream and play the roar for anyone holding one.
 */
public class FlamethrowerItem extends GunItem {
	public static final int TANK = 200;
	public static final int FUEL_PER_TICK = 1;
	/** Ticks after a spray that it still counts as spraying, bridging the gap between use packets. */
	public static final int FIRING_GRACE = 3;
	/** One point of wear for every this many units sprayed. */
	private static final int FUEL_PER_WEAR = 10;

	public FlamethrowerItem(Properties properties, GunStats stats) {
		super(properties, stats, List.of(),
				List.of(new Ammo(Industry.NAPALM_CANISTER, FlameStream.MAX_DAMAGE, Napalm.NAPALM, 0xFF7A1A)));
	}

	@Override
	public int capacity(ItemStack gun) {
		return TANK;
	}

	public static int fuel(ItemStack gun) {
		MagazineContents tank = gun.get(ModComponents.LOADED_MAGAZINE);
		return tank == null ? 0 : tank.rounds();
	}

	/** Whether this flamethrower sprayed within the last {@link #FIRING_GRACE} ticks of {@code gameTime}. */
	public static boolean firing(ItemStack gun, long gameTime) {
		Long firedAt = gun.get(Napalm.FLAME_FIRED_AT);
		return firedAt != null && gameTime - firedAt <= FIRING_GRACE && gameTime >= firedAt;
	}

	/**
	 * Where the stream leaves the nozzle: ahead of the eye, low and off to the side of the holding hand, about where
	 * the wand's nozzle shows in first person.
	 */
	public static Vec3 nozzle(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(look);
		double side = player.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
		return eye.add(look.scale(1.2)).add(right.scale(0.55 * side)).add(up.scale(-0.3));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		boolean free = player.hasInfiniteMaterials();
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		if (!free && fuel(gun) < FUEL_PER_TICK) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return InteractionResult.FAIL;
		}
		long now = level.getGameTime();
		if (level.getFluidState(BlockPos.containing(nozzle(player, 1.0f))).is(FluidTags.WATER)) {
			// Drowned: the pilot light's out and the gel won't catch.
			if (level instanceof ServerLevel serverLevel && now % 10 == 0) {
				Vec3 at = nozzle(player, 1.0f);
				serverLevel.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.05);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
			}
			return InteractionResult.FAIL;
		}
		boolean starting = !firing(gun, now);
		gun.set(Napalm.FLAME_FIRED_AT, now);
		if (level.isClientSide()) {
			GunRecoil.queue(player, 1, stats(gun, player));
			return InteractionResult.CONSUME;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		if (starting) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), Napalm.FLAMETHROWER_IGNITE, SoundSource.PLAYERS, 0.8f,
					0.95f + player.getRandom().nextFloat() * 0.1f);
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		FlameStream.spray(serverLevel, player, nozzle(player, 1.0f), stats(gun, player).spreadDegrees());
		if (!free) {
			int left = fuel(gun) - FUEL_PER_TICK;
			gun.set(ModComponents.LOADED_MAGAZINE, gun.get(ModComponents.LOADED_MAGAZINE).withRounds(left));
			if (left % FUEL_PER_WEAR == 0) {
				gun.hurtAndBreak(1, player, hand);
			}
		}
		return InteractionResult.CONSUME;
	}

	/**
	 * Punching loads a Napalm Canister into a dry tank, or with sneak swaps out one that isn't. The spent canister
	 * comes back empty.
	 */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		int fuel = fuel(gun);
		if (fuel >= TANK) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.flamethrower.tank_full"));
			return;
		}
		if (fuel > 0 && !player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.flamethrower.tank_not_empty"));
			return;
		}
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Industry.NAPALM_CANISTER)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.flamethrower.no_canisters").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
					SoundSource.PLAYERS, 0.6f, 1.6f);
			return;
		}
		ItemStack canister = player.getInventory().getItem(slot).split(1);
		if (gun.has(ModComponents.LOADED_MAGAZINE)) {
			player.getInventory().placeItemBackInInventory(new ItemStack(Industry.EMPTY_CANISTER), Prediction.SERVER_ONLY);
		}
		gun.set(ModComponents.LOADED_MAGAZINE, new MagazineContents(canister.typeHolder(), TANK));
		player.getCooldowns().addCooldown(gun, Math.round(stats().reloadTicks() * reloadFactor(player)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL_LAVA,
				SoundSource.PLAYERS, 0.8f, 0.7f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END,
				SoundSource.PLAYERS, 0.8f, 0.6f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		if (!stack.has(ModComponents.LOADED_MAGAZINE)) {
			tooltip.accept(Component.translatable("item.tomyen.flamethrower.no_tank").withStyle(ChatFormatting.GRAY));
		} else {
			int fuel = fuel(stack);
			tooltip.accept(Component.translatable("item.tomyen.flamethrower.fuel", Math.round(100.0f * fuel / TANK), fuel, TANK)
					.withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.tomyen.flamethrower.spray_hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.flamethrower.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
