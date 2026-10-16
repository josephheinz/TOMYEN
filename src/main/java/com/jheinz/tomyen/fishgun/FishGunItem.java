package com.jheinz.tomyen.fishgun;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.GunItem;
import com.jheinz.tomyen.GunRecoil;
import com.jheinz.tomyen.GunStats;
import com.jheinz.tomyen.fishing.FishSize;
import com.jheinz.tomyen.fishing.TrophyFish;

/**
 * The Fish Gun: a pneumatic cannon that fires raw fish from the inventory ({@link FishAmmo}, {@link FishProjectile}).
 * One click, one fish; then it reloads for {@link FishAmmo#loadTicks} (a minnow's a fifth of a second, a trophy tuna's
 * more than two) while the next fish is stuffed in and the tank recharges. The fire-mode key picks which kind of fish
 * to fire ({@link #cycleFireMode}); punching just says what's loaded. Heavy fish kick (view recoil and a shove back on the
 * shooter) and are slung out slower and higher. It never takes magazines, so it's only a {@link GunItem} for the HUD,
 * the two-handed hold and the arm poses.
 */
public class FishGunItem extends GunItem {
	/** The shot leaves the bell aimed to cross the line of sight this far out. */
	private static final double CONVERGE = 30.0;

	public FishGunItem(Properties properties, GunStats stats) {
		super(properties, stats, List.of(), List.of());
	}

	@Override
	public int capacity(ItemStack gun) {
		LoadedFish loaded = gun.get(FishGuns.LOADED_FISH);
		return loaded == null ? 0 : loaded.count();
	}

	/** Kick grows with the loaded fish's weight. */
	@Override
	public GunStats stats(ItemStack gun, LivingEntity holder) {
		GunStats base = super.stats(gun, holder);
		LoadedFish loaded = gun.get(FishGuns.LOADED_FISH);
		return loaded == null ? base : base.withRecoil(FishAmmo.recoil(loaded.weight()));
	}

	/** Where the fish leaves the bell: ahead of the eye, a little right of and below it, as the model is held. */
	public static Vec3 muzzle(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(look);
		double side = player.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
		return eye.add(look.scale(1.0)).add(right.scale(0.28 * side)).add(up.scale(-0.12));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			if (!gun.has(FishGuns.LOADED_FISH) && !player.hasInfiniteMaterials()) {
				return InteractionResult.FAIL;
			}
			GunRecoil.queue(player, 1, stats(gun, player));
			return InteractionResult.CONSUME;
		}
		ServerPlayer shooter = (ServerPlayer) player;
		FishAmmo.Entry entry = FishAmmo.select(player, gun);
		if (entry == null) {
			boolean trophies = !FishAmmo.entries(player).isEmpty();
			shooter.sendOverlayMessage(Component.translatable(trophies ? "item.tomyen.fish_gun.only_trophies" : "item.tomyen.fish_gun.no_fish",
					Component.keybind("key.tomyen.fire_mode")).withStyle(ChatFormatting.RED));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
			return InteractionResult.FAIL;
		}
		fire((ServerLevel) level, shooter, gun, entry);
		return InteractionResult.CONSUME;
	}

	private void fire(ServerLevel level, ServerPlayer player, ItemStack gun, FishAmmo.Entry entry) {
		ItemStack fish;
		if (entry.slot() < 0) {
			fish = new ItemStack(entry.fish());
		} else {
			ItemStack source = player.getInventory().getItem(entry.slot());
			fish = source.copyWithCount(1);
			if (!player.hasInfiniteMaterials()) {
				source.shrink(1);
			}
		}
		float weight = entry.weight();
		float heft = FishAmmo.heft(weight);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 muzzle = muzzle(player);
		Vec3 direction = eye.add(look.scale(CONVERGE)).subtract(muzzle).normalize();
		// A little wobble: fish aren't aerodynamic.
		direction = direction.add(player.getRandom().triangle(0.0, 0.02), player.getRandom().triangle(0.0, 0.02),
				player.getRandom().triangle(0.0, 0.02)).normalize();

		FishProjectile round = new FishProjectile(level, player, muzzle, fish, weight, FishAmmo.average(fish));
		round.launch(direction.scale(FishAmmo.speed(weight)).add(player.getKnownMovement().scale(0.5)));
		level.addFreshEntity(round);

		// FWOOMP: the air blast (deeper and louder for a heavy fish) over the rush of the fish leaving the bell.
		float pitch = 1.15f - 0.4f * heft + player.getRandom().nextFloat() * 0.08f;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), FishGuns.FISH_GUN_FIRE, SoundSource.PLAYERS, 1.2f + heft, pitch);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), FishGuns.FISH_GUN_WHOOSH, SoundSource.PLAYERS, 0.7f + 0.5f * heft,
				pitch * 1.1f);
		level.sendParticles(ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 4 + Math.round(10 * heft), 0.08, 0.08, 0.08, 0.04);
		level.sendParticles(ParticleTypes.SPLASH, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.1);
		if (heft > 0.5f) {
			// A cannonball shoves the shooter back.
			Vec3 shove = look.scale(-0.6 * (heft - 0.5));
			player.push(shove.x, 0.0, shove.z);
			player.needsSync = true;
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		player.getCooldowns().addCooldown(gun, Math.round(FishAmmo.loadTicks(weight) * reloadFactor(player)));
		if (!player.hasInfiniteMaterials()) {
			gun.hurtAndBreak(heft > 0.6f ? 2 : 1, player, InteractionHand.MAIN_HAND);
		}
		refresh(player, gun);
	}

	/** Brings the HUD's loaded fish up to date with the inventory (server side; each tick while it's held). */
	public static void refresh(Player player, ItemStack gun) {
		FishAmmo.Entry entry = FishAmmo.select(player, gun);
		LoadedFish loaded = entry == null ? null : entry.loaded();
		if (loaded == null) {
			gun.remove(FishGuns.LOADED_FISH);
		} else if (!loaded.equals(gun.get(FishGuns.LOADED_FISH))) {
			gun.set(FishGuns.LOADED_FISH, loaded);
		}
	}

	/** The fire-mode key: load the next kind of fish (heaviest to lightest, trophies included). */
	@Override
	public void cycleFireMode(ServerPlayer player, ItemStack gun) {
		FishAmmo.Entry next = FishAmmo.cycle(player, gun);
		if (next == null) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.fish_gun.no_fish").withStyle(ChatFormatting.RED));
			return;
		}
		refresh(player, gun);
		player.sendOverlayMessage(Component.translatable("item.tomyen.fish_gun.selected", describe(next.fish().getDefaultInstance(),
				next.trophy(), next.weight())));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COD_FLOP, SoundSource.PLAYERS, 0.6f, 1.3f);
	}

	/** Punching says what's loaded (there's nothing to reload: fish go straight in from the inventory). */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		FishAmmo.Entry entry = FishAmmo.select(player, gun);
		player.sendOverlayMessage(entry == null ? Component.translatable("item.tomyen.fish_gun.no_fish").withStyle(ChatFormatting.RED)
				: Component.translatable("item.tomyen.fish_gun.loaded", describe(entry.fish().getDefaultInstance(), entry.trophy(), entry.weight())));
	}

	/** "Pike, 8.0 lb", or for a trophy "Trophy Pike, 31.7 lb" in gold. */
	public static Component describe(ItemStack fish, boolean trophy, float weight) {
		Component name = trophy ? Component.translatable("item.tomyen.trophy_fish", TrophyFish.speciesName(fish)) : TrophyFish.speciesName(fish);
		Component line = Component.translatable("hud.tomyen.fishing.weighed", name, FishSize.format(weight));
		return trophy ? line.copy().withStyle(TrophyFish.COLOR) : line;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		LoadedFish loaded = stack.get(FishGuns.LOADED_FISH);
		if (loaded == null) {
			tooltip.accept(Component.translatable("item.tomyen.fish_gun.empty").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.tomyen.fish_gun.loaded",
					describe(loaded.fish().value().getDefaultInstance(), loaded.trophy(), loaded.weight())).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.tomyen.fish_gun.hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.fish_gun.select_hint", Component.keybind("key.tomyen.fire_mode"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
