package com.jheinz.mymod.minigun;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.Ammo;
import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunSpread;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.ModEffects;
import com.jheinz.mymod.ModItems;
import com.jheinz.mymod.powerarmor.PowerArmor;
import com.jheinz.mymod.powerarmor.PowerArmorSuit;

/**
 * A six-barrelled rotary gun carried at the hip, far too heavy for anyone outside a powered suit of power armor. Worn
 * in one ({@link #canWield}), holding use spins it up and fires 5.56 straight from the inventory at 1200 rpm until the
 * trigger's let go, the rounds run out or it overheats ({@link RotaryGun} has the numbers); the suit soaks up most of
 * the kick. Out of one it won't fire, and holding it drags the holder down with heavy Slowness and a reminder of what
 * it needs.
 *
 * <p>The gun's works run on the server in {@link #inventoryTick}: whether use is held, the spin, heat and firing, kept
 * on the stack as {@link Miniguns#STATE} for every client to draw and hear. Firing slows its holder and stops them
 * sprinting (the stack's use effects). It has no magazine, so punching (reload) only says where its rounds come from.
 * A Neuralink Smart-Link tightens its spread like any gun's, and an Overclock spins it up twice as fast.
 */
public class MinigunItem extends GunItem {
	/** Slowness while it's held out of power armor: amplifier 3 is Slowness IV. */
	private static final int TOO_HEAVY_SLOWNESS = 3;
	/** One point of wear for every this many rounds. */
	private static final int ROUNDS_PER_WEAR = 4;
	private static final float OVERCLOCK_SPIN_UP = 2.0f;

	public MinigunItem(Properties properties, GunStats stats) {
		super(properties, stats, List.of(), ModItems.AMMO_556);
	}

	/** Whether this holder can carry and fire it: they're in power armor with charge in its core. */
	public static boolean canWield(LivingEntity holder) {
		PowerArmorSuit suit = PowerArmor.worn(holder);
		return suit != null && suit.charge() > 0;
	}

	public static MinigunState state(ItemStack gun) {
		return gun.getOrDefault(Miniguns.STATE, MinigunState.IDLE);
	}

	@Override
	public int capacity(ItemStack gun) {
		return 0;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		ItemStack gun = player.getItemInHand(hand);
		String refusal = !canWield(player) ? "item.mymod.minigun.too_heavy" : state(gun).overheated() ? "item.mymod.minigun.overheated" : null;
		if (refusal != null) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 0.8f);
			}
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public void inventoryTick(ItemStack gun, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!(owner instanceof Player player)) {
			return;
		}
		boolean held = slot == EquipmentSlot.MAINHAND;
		boolean wieldable = canWield(player);
		if (held && !wieldable && !player.isSpectator()) {
			tooHeavy(player);
		}
		boolean trigger = held && wieldable && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
		MinigunState before = state(gun);
		if (!trigger && before.equals(MinigunState.IDLE)) {
			return;
		}
		float spinUp = player.hasEffect(ModEffects.OVERCLOCKED) ? OVERCLOCK_SPIN_UP : 1.0f;
		boolean[] dry = {false};
		MinigunState next = RotaryGun.tick(before, trigger, spinUp, () -> {
			boolean fired = fireRotary(level, player, gun);
			dry[0] = !fired;
			return fired;
		});
		if (dry[0] && level.getGameTime() % 6 == 0) {
			// The barrels whirl round on an empty feed.
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.4f);
			player.sendOverlayMessage(Component.translatable("item.mymod.minigun.no_ammo").withStyle(ChatFormatting.RED));
		}
		if (next.overheated() && !before.overheated()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
			player.sendOverlayMessage(Component.translatable("item.mymod.minigun.overheated").withStyle(ChatFormatting.RED));
		}
		if (next.equals(MinigunState.IDLE)) {
			gun.remove(Miniguns.STATE);
		} else {
			gun.set(Miniguns.STATE, next);
		}
	}

	/** Out of power armor it's a dead weight: heavy Slowness while it's held, and a reminder now and then. */
	private static void tooHeavy(Player player) {
		if (player.tickCount % 5 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, TOO_HEAVY_SLOWNESS, false, false, true));
		}
		if (player.tickCount % 40 == 0) {
			player.sendOverlayMessage(Component.translatable("item.mymod.minigun.too_heavy").withStyle(ChatFormatting.RED));
		}
	}

	/** Fires one round from the hip, along the holder's look. Returns false (and does nothing) with no rounds left. */
	private boolean fireRotary(ServerLevel level, Player player, ItemStack gun) {
		Ammo round = RotaryGun.takeRound(player);
		if (round == null) {
			return false;
		}
		float spread = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats(gun, player)) : 0.0f;
		Vec3 look = player.getViewVector(1.0f);
		RotaryGun.shoot(level, player, player.getEyePosition(), RotaryGun.stray(look, spread, player.getRandom()), muzzle(player, 1.0f),
				round, null);
		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.hasInfiniteMaterials() && level.getGameTime() % ROUNDS_PER_WEAR == 0) {
			gun.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		}
		return true;
	}

	/** Where the barrels end: out in front at the hip, to the side of the shooting hand. */
	public static Vec3 muzzle(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
		Vec3 ahead = Vec3.directionFromRotation(Mth.clamp(player.getViewXRot(partialTick), -45.0f, 45.0f) * 0.5f, yaw);
		Vec3 right = new Vec3(-ahead.z, 0.0, ahead.x).normalize();
		double side = player.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
		return eye.add(ahead.scale(1.5)).add(right.scale(0.3 * side)).add(0.0, -0.75, 0.0);
	}

	/** Punching has nothing to reload: it says where the rounds come from instead. */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		player.sendOverlayMessage(Component.translatable("item.mymod.minigun.feed_hint").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mymod.minigun.power_armor_hint").withStyle(ChatFormatting.GOLD));
		tooltip.accept(Component.translatable("item.mymod.minigun.feed_hint").withStyle(ChatFormatting.GRAY));
		if (state(stack).overheated()) {
			tooltip.accept(Component.translatable("item.mymod.minigun.overheated").withStyle(ChatFormatting.RED));
		}
		tooltip.accept(Component.translatable("item.mymod.minigun.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
