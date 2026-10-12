package com.jheinz.mymod.launcher;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.GunSpread;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.ModSounds;
import com.jheinz.mymod.attachment.AttachmentSlot;
import com.jheinz.mymod.attachment.Attachments;

/**
 * An RPG-7. It holds one PG-7 rocket at a time, loaded straight from the inventory by punching (no magazines;
 * creative needs none). Drawing it hoists it onto the shoulder over {@link #EQUIP_TICKS}, and a reload takes
 * {@link #RELOAD_TICKS} (half while overclocked); it can't fire during either. Putting it away cancels both, and
 * the rocket is only taken from the inventory once a reload completes. {@link LauncherHandling} runs that from
 * the server tick; the state lives on the stack ({@link Launchers#LAUNCHER_ACTION}) so every client can animate it.
 *
 * <p>A shot kicks hard and blasts anyone standing behind the shooter ({@link Backblast}). With the homing guidance
 * module fitted and a homing rocket seated, holding the sight on a living target locks it ({@link LockOn}) and the
 * rocket steers itself onto it; any other combination flies straight.
 *
 * <p>Reloading takes a homing rocket first when the module is fitted, and a plain one first otherwise, so the
 * expensive ones aren't spent where they can't home.
 */
public class RocketLauncherItem extends GunItem {
	public static final int EQUIP_TICKS = 45;
	public static final int RELOAD_TICKS = 75;
	private static final int FIRE_COOLDOWN_TICKS = 10;
	/** How fast a rocket leaves the tube, blocks per tick; its motor speeds it up from there. */
	private static final double LAUNCH_SPEED = 2.0;
	/** Rockets are aimed to cross the line of sight this far out, so the shoulder offset doesn't throw them wide. */
	private static final double CONVERGE = 40.0;

	public RocketLauncherItem(Properties properties, GunStats stats) {
		super(properties, stats, List.of(), List.of());
	}

	/** Shouldered and able to fire or reload. */
	public static boolean shouldered(ItemStack launcher, Level level) {
		LauncherAction action = launcher.get(Launchers.LAUNCHER_ACTION);
		return action != null && action.shouldered(level.getGameTime());
	}

	/** The rocket seated in it, or null. */
	public static Item loadedRocket(ItemStack launcher) {
		Holder<Item> rocket = launcher.get(Launchers.LOADED_ROCKET);
		return rocket == null ? null : rocket.value();
	}

	public static boolean hasGuidance(ItemStack launcher) {
		return Attachments.get(launcher, AttachmentSlot.GUIDANCE) != null;
	}

	/** What a shot would fire: the seated rocket, or in creative whatever it would load. Null if there's nothing. */
	public static Item rocketToFire(ItemStack launcher, Player player) {
		Item loaded = loadedRocket(launcher);
		if (loaded == null && player.hasInfiniteMaterials()) {
			return hasGuidance(launcher) ? Launchers.PG7_HOMING_ROCKET : Launchers.PG7_ROCKET;
		}
		return loaded;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack launcher = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || !shouldered(launcher, level)) {
			return InteractionResult.FAIL;
		}
		Item rocket = rocketToFire(launcher, player);
		if (rocket == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
					SoundSource.PLAYERS, 0.6f, 1.2f);
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			GunRecoil.queue(player, 1, stats(launcher, player));
			return InteractionResult.CONSUME;
		}
		fire((ServerLevel) level, player, launcher, rocket);
		return InteractionResult.CONSUME;
	}

	private void fire(ServerLevel level, Player player, ItemStack launcher, Item rocketItem) {
		// Only a homing rocket guided by the module follows a lock; the lock is used up either way.
		Entity lock = LockOn.take(player);
		Entity target = rocketItem == Launchers.PG7_HOMING_ROCKET && hasGuidance(launcher) ? lock : null;

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		float spread = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats(launcher, player)) : 0.0f;
		if (spread > 0.0f && target == null) {
			float radius = spread * (float) Math.sqrt(player.getRandom().nextFloat());
			float angle = player.getRandom().nextFloat() * (float) (Math.PI * 2.0);
			look = Vec3.directionFromRotation(player.getXRot() + radius * (float) Math.sin(angle),
					player.getYRot() + radius * (float) Math.cos(angle));
		}
		Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
		Vec3 shoulder = eye.add(right.scale(0.3)).add(0.0, -0.12, 0.0);
		Vec3 muzzle = shoulder.add(look.scale(1.1));
		Vec3 direction = eye.add(look.scale(CONVERGE)).subtract(muzzle).normalize();

		PG7Rocket rocket = new PG7Rocket(level, player, muzzle, new ItemStack(rocketItem), target);
		rocket.launch(direction.scale(LAUNCH_SPEED));
		level.addFreshEntity(rocket);

		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.08, 0.08, 0.08, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.02);
		Backblast.blast(level, player, shoulder.subtract(look.scale(0.9)), look.scale(-1.0));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Launchers.RPG_LAUNCH, SoundSource.PLAYERS,
				4.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));

		if (!player.hasInfiniteMaterials()) {
			launcher.remove(Launchers.LOADED_ROCKET);
			launcher.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		}
		player.getCooldowns().addCooldown(launcher, FIRE_COOLDOWN_TICKS);
	}

	/** Punching starts a reload when it's shouldered and empty; the rocket goes in when it finishes. */
	@Override
	public void reload(ServerPlayer player, ItemStack launcher) {
		if (!shouldered(launcher, player.level())) {
			return;
		}
		if (launcher.has(Launchers.LOADED_ROCKET)) {
			player.sendOverlayMessage(Component.translatable("item.mymod.rpg7.already_loaded"));
			return;
		}
		Item rocket = preferredRocket(player, launcher);
		if (rocket == null) {
			fail(player, "item.mymod.rpg7.no_rockets");
			return;
		}
		int ticks = Math.round(RELOAD_TICKS * reloadFactor(player));
		launcher.set(Launchers.LAUNCHER_ACTION,
				LauncherAction.reload(player.level().getGameTime(), ticks, rocket == Launchers.PG7_HOMING_ROCKET));
		player.getCooldowns().addCooldown(launcher, ticks);
		// The shooter hears their own reload from the animation's cues; this is for everyone else.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
				SoundSource.PLAYERS, 0.8f, 0.7f);
	}

	/** Starts hoisting a launcher that has just come into the hand. */
	void startEquip(ServerPlayer player, ItemStack launcher, long now) {
		launcher.set(Launchers.LAUNCHER_ACTION, LauncherAction.equip(now, EQUIP_TICKS));
		player.getCooldowns().addCooldown(launcher, EQUIP_TICKS);
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
				SoundSource.PLAYERS, 0.7f, 0.6f);
	}

	/** Finishes a reload: takes the rocket chosen when it started (or the other kind if that's gone) and seats it. */
	void completeReload(ServerPlayer player, ItemStack launcher, LauncherAction action, long now) {
		launcher.set(Launchers.LAUNCHER_ACTION, LauncherAction.ready(now));
		Item wanted = action.homing() ? Launchers.PG7_HOMING_ROCKET : Launchers.PG7_ROCKET;
		Item loaded = player.hasInfiniteMaterials() ? wanted : null;
		for (Item type : List.of(wanted, wanted == Launchers.PG7_ROCKET ? Launchers.PG7_HOMING_ROCKET : Launchers.PG7_ROCKET)) {
			int slot = loaded == null ? findRocket(player, type) : -1;
			if (slot >= 0) {
				player.getInventory().getItem(slot).shrink(1);
				loaded = type;
			}
		}
		if (loaded == null) {
			fail(player, "item.mymod.rpg7.no_rockets");
			return;
		}
		launcher.set(Launchers.LOADED_ROCKET, loaded.builtInRegistryHolder());
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.MAG_IN, SoundSource.PLAYERS,
				0.9f, 0.55f);
	}

	private static Item preferredRocket(Player player, ItemStack launcher) {
		List<Item> order = hasGuidance(launcher) ? List.of(Launchers.PG7_HOMING_ROCKET, Launchers.PG7_ROCKET)
				: List.of(Launchers.PG7_ROCKET, Launchers.PG7_HOMING_ROCKET);
		for (Item type : order) {
			if (findRocket(player, type) >= 0) {
				return type;
			}
		}
		return player.hasInfiniteMaterials() ? order.getFirst() : null;
	}

	private static int findRocket(Player player, Item type) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(type)) {
				return i;
			}
		}
		return -1;
	}

	private static void fail(ServerPlayer player, String messageKey) {
		player.sendOverlayMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
				SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		Item loaded = loadedRocket(stack);
		tooltip.accept((loaded == null ? Component.translatable("item.mymod.rpg7.empty")
				: Component.translatable("item.mymod.rpg7.loaded", Component.translatable(loaded.getDescriptionId())))
				.withStyle(ChatFormatting.GRAY));
		boolean guided = hasGuidance(stack);
		if (guided) {
			tooltip.accept(Component.translatable("item.mymod.gun.attachment",
					Component.translatable(Launchers.HOMING_GUIDANCE_MODULE.getDescriptionId())).withStyle(ChatFormatting.BLUE));
		}
		tooltip.accept(Component.translatable("item.mymod.rpg7.scope_hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable(guided ? "item.mymod.rpg7.lock_hint" : "item.mymod.rpg7.module_hint")
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.mymod.rpg7.hint").withStyle(ChatFormatting.DARK_GRAY));
		if (guided) {
			tooltip.accept(Component.translatable("item.mymod.gun.remove_attachment_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
