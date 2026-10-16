package com.jheinz.tomyen.energy;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
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
import com.jheinz.tomyen.ModEffects;

/**
 * The Railgun, built round an Alien Reactor, which powers it for good but needs cooling between shots. Hold use to
 * charge it: the coils glow brighter and the whine climbs for {@value #CHARGE_TICKS} ticks (two thirds of that while
 * overclocked), then it fires on its own, a hypersonic slug down a hitscan line: {@value #TUNGSTEN_DAMAGE} damage from a
 * tungsten slug, through up to five targets and three soft blocks; {@value #STEEL_DAMAGE} from a steel one, through
 * three targets and one block (blocks only break if mobGriefing is on). The kick throws the shooter back. Then the
 * reactor has to cool for {@value #COOL_TICKS} ticks. Let go early and nothing happens.
 *
 * <p>It holds {@value #MAGAZINE} slugs, all of one kind: punching loads as many of the kind already in it as will fit
 * (any kind if it's empty, tungsten first); sneak and punch to unload and switch to the other kind.
 */
public class RailgunItem extends EnergyWeaponItem {
	public static final int MAGAZINE = 5;
	public static final int CHARGE_TICKS = 30;
	private static final float OVERCLOCKED_CHARGE = 0.66f;
	static final int COOL_TICKS = 60;
	static final float TUNGSTEN_DAMAGE = 36.0f;
	static final float STEEL_DAMAGE = 26.0f;
	private static final float KICK = 0.6f;
	private static final int BEAM_COLOR = 0x9CC8FF;

	public RailgunItem(Properties properties, GunStats stats) {
		super(properties, stats, List.of(
				new Ammo(EnergyWeapons.TUNGSTEN_RAIL_SLUG, TUNGSTEN_DAMAGE, EnergyWeapons.RAILGUN_SLUG, 0x8A9BB0, 4, 0.0f),
				new Ammo(EnergyWeapons.STEEL_RAIL_SLUG, STEEL_DAMAGE, EnergyWeapons.RAILGUN_SLUG, 0xC8CCD2, 2, 0.0f)));
	}

	@Override
	protected float heatPerShot() {
		return MAX_HEAT;
	}

	@Override
	protected float coolingPerTick() {
		return MAX_HEAT / COOL_TICKS;
	}

	@Override
	public int beamColor() {
		return BEAM_COLOR;
	}

	@Override
	public int capacity(ItemStack gun) {
		return MAGAZINE;
	}

	public static int slugs(ItemStack gun) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		return loaded == null ? 0 : loaded.rounds();
	}

	@Override
	public int displayRounds(ItemStack gun, long now) {
		return slugs(gun);
	}

	/** How long this holder takes to charge it. */
	public static int chargeTicks(LivingEntity holder) {
		return holder.hasEffect(ModEffects.OVERCLOCKED) ? Math.round(CHARGE_TICKS * OVERCLOCKED_CHARGE) : CHARGE_TICKS;
	}

	/** How far charged the holder's railgun is, 0 to 1 (0 unless they're charging this one). */
	public static float charge(ItemStack gun, LivingEntity holder) {
		if (!holder.isUsingItem() || holder.getUseItem() != gun) {
			return 0.0f;
		}
		return Math.min(1.0f, holder.getTicksUsingItem() / (float) chargeTicks(holder));
	}

	/** The coils glow up as it charges, and stay warm while it cools. */
	@Override
	public float glow(ItemStack gun, LivingEntity holder, long now) {
		return Math.max(charge(gun, holder), 0.6f * heat(gun, now) / MAX_HEAT);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		if (!player.hasInfiniteMaterials() && slugs(gun) <= 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return InteractionResult.FAIL;
		}
		if (heat(gun, level.getGameTime()) > 0.0f) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.tomyen.railgun.cooling").withStyle(ChatFormatting.AQUA));
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			RailgunAim.clear(serverPlayer);
		}
		if (!level.isClientSide()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.RAILGUN_CHARGE, SoundSource.PLAYERS, 0.9f,
					CHARGE_TICKS / (float) chargeTicks(player));
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack gun, int ticksRemaining) {
		if (!(entity instanceof Player player)) {
			return;
		}
		int ticks = getUseDuration(gun, entity) - ticksRemaining;
		if (level instanceof ServerLevel serverLevel && ticks % 3 == 0) {
			Vec3 at = muzzle(player);
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1 + ticks / 10, 0.08, 0.08, 0.08, 0.05);
		}
		if (ticks < chargeTicks(player)) {
			return;
		}
		player.stopUsingItem();
		if (level.isClientSide()) {
			// Report the aim before the kick turns the view: the kicked rotation reaches the server first otherwise.
			RailgunAim.sender.accept(new RailgunAim(player.getXRot(), player.getYRot()));
			GunRecoil.queue(player, 1, stats(gun, player));
		} else {
			fire((ServerLevel) level, player, gun);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		// Let go early: the charge bleeds off.
		return false;
	}

	private void fire(ServerLevel level, Player player, ItemStack gun) {
		boolean free = player.hasInfiniteMaterials();
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		Ammo slug = loaded == null || loaded.isEmpty() ? (free ? ammo().getFirst() : null) : Ammo.find(ammo(), loaded.ammo());
		if (slug == null) {
			return;
		}
		boolean tungsten = slug.item() == EnergyWeapons.TUNGSTEN_RAIL_SLUG;
		// Along the aim from the instant the charge completed, before the recoil kicked the view (see RailgunAim).
		RailgunAim shot = player instanceof ServerPlayer serverPlayer ? RailgunAim.take(serverPlayer)
				: new RailgunAim(player.getXRot(), player.getYRot());
		Vec3 look = aim(player, spread(player, stats(gun, player)), shot.xRot(), shot.yRot());
		Vec3 end = trace(level, player, player.getEyePosition(), look, stats().range(), slug.pierce(), tungsten ? 3 : 1, true,
				(target, at) -> {
					target.hurtServer(level, damage(level, slug.damageType(), player), slug.damage());
					if (target instanceof LivingEntity living) {
						living.push(look.x * 0.8, 0.15, look.z * 0.8);
					}
				});
		Vec3 muzzle = muzzle(player, Vec3.directionFromRotation(shot.xRot(), shot.yRot()));
		EnergyBeam.send(level, player, muzzle, end, BEAM_COLOR, 0.45f, 700);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFC8DCFF), muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.RAILGUN_SHOT, SoundSource.PLAYERS, 1.6f,
				0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));
		// The kick: back along the line of fire, and a little up off the ground.
		player.push(-look.x * KICK, Math.max(0.1, -look.y * KICK * 0.5), -look.z * KICK);
		player.syncVelocity = true;
		gun.set(EnergyWeapons.HEAT, new Heat(MAX_HEAT, level.getGameTime(), true));
		if (!free) {
			gun.set(ModComponents.LOADED_MAGAZINE, loaded.withRounds(loaded.rounds() - 1));
			gun.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		}
	}

	/** Loads as many slugs as fit, of the kind already loaded (or tungsten, then steel); sneaking unloads and switches. */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		Item previous = null;
		if (loaded != null && !loaded.isEmpty() && player.isShiftKeyDown()) {
			previous = loaded.ammo().value();
			player.getInventory().placeItemBackInInventory(new ItemStack(loaded.ammo(), loaded.rounds()), Prediction.SERVER_ONLY);
			gun.remove(ModComponents.LOADED_MAGAZINE);
			loaded = null;
		}
		int rounds = loaded == null ? 0 : loaded.rounds();
		if (rounds >= MAGAZINE) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.railgun.full"));
			return;
		}
		Item kind = rounds > 0 ? loaded.ammo().value() : pickKind(player, previous);
		if (kind == null) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.railgun.no_slugs").withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return;
		}
		int added = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && rounds + added < MAGAZINE; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(kind)) {
				int take = Math.min(stack.getCount(), MAGAZINE - rounds - added);
				stack.shrink(take);
				added += take;
			}
		}
		if (added == 0) {
			player.sendOverlayMessage(Component.translatable("item.tomyen.railgun.no_slugs").withStyle(ChatFormatting.RED));
			return;
		}
		gun.set(ModComponents.LOADED_MAGAZINE, new MagazineContents(kind.builtInRegistryHolder(), rounds + added));
		player.getCooldowns().addCooldown(gun, Math.round(stats().reloadTicks() * reloadFactor(player)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), EnergyWeapons.SLUG_LOAD, SoundSource.PLAYERS, 0.9f, 1.0f);
	}

	/** The kind of slug to load into an empty railgun: whichever there is (not the one just unloaded, if there's another). */
	private Item pickKind(Player player, Item notThis) {
		Item fallback = null;
		for (Ammo slug : ammo()) {
			if (player.getInventory().countItem(slug.item()) > 0) {
				if (slug.item() != notThis) {
					return slug.item();
				}
				fallback = slug.item();
			}
		}
		return fallback;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		if (loaded == null || loaded.isEmpty()) {
			tooltip.accept(Component.translatable("item.tomyen.railgun.empty").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.tomyen.magazine.rounds", loaded.rounds(), MAGAZINE, new ItemStack(loaded.ammo()).getHoverName())
					.withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.tomyen.railgun.charge_hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.gun.scope_hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.tomyen.railgun.hint").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** One shot per charge, a big kick, a scope. */
	public static GunStats defaultStats() {
		return new GunStats(EnergyWeapons.RAILGUN_SHOT, 10.0f, 160, 40, List.of(FireMode.SEMI)).withScope(0.3f).withSpread(0.6f, 0.0f);
	}
}
