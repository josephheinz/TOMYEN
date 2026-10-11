package com.jheinz.mymod;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A magazine-fed hitscan firearm. Each use fires one round from the inserted magazine; holding use
 * repeats at vanilla's right-click rate (every 4 ticks) in full-auto; semi and burst need a fresh
 * click. Punching with an empty gun reloads it.
 */
public class GunItem extends Item {
	/** How long thumbing a single round into a cylinder blocks firing. */
	private static final int SINGLE_ROUND_TICKS = 4;
	/** How long racking a gun after an empty reload takes. */
	private static final int RACK_TICKS = 20;
	/** How long working a bolt blocks firing. */
	private static final int BOLT_TICKS = 18;

	private final GunStats stats;
	private final List<MagazineItem> magazines;
	private final List<Ammo> ammo;

	/** @param magazines magazines the gun accepts; the first is its standard one */
	public GunItem(Properties properties, GunStats stats, List<MagazineItem> magazines, List<Ammo> ammo) {
		super(properties);
		this.stats = stats;
		this.magazines = magazines;
		this.ammo = ammo;
	}

	public GunStats stats() {
		return stats;
	}

	public List<MagazineItem> magazines() {
		return magazines;
	}

	/** The kind of magazine inserted in this gun (or that would be, for a gun with none). */
	public MagazineItem loadedMagazine(ItemStack gun) {
		Holder<Item> type = gun.get(ModComponents.LOADED_MAGAZINE_TYPE);
		if (type != null && type.value() instanceof MagazineItem magazine && magazines.contains(magazine)) {
			return magazine;
		}
		return magazines.getFirst();
	}

	public List<Ammo> ammo() {
		return ammo;
	}

	/** Firing, reloading and wear change the stack's data every shot; that shouldn't replay the equip animation. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	public FireMode fireMode(ItemStack gun) {
		FireMode mode = gun.get(ModComponents.FIRE_MODE);
		return mode != null && stats.fireModes().contains(mode) ? mode : stats.fireModes().getFirst();
	}

	/** Switches to the gun's next fire mode, if it has more than one. */
	public void cycleFireMode(ServerPlayer player, ItemStack gun) {
		List<FireMode> modes = stats.fireModes();
		if (modes.size() < 2) {
			return;
		}
		FireMode next = modes.get((modes.indexOf(fireMode(gun)) + 1) % modes.size());
		gun.set(ModComponents.FIRE_MODE, next);
		player.sendOverlayMessage(Component.translatable("item.mymod.gun.fire_mode", Component.translatable(next.translationKey())));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK,
				SoundSource.PLAYERS, 0.5f, 1.8f);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		boolean free = player.hasInfiniteMaterials();
		int available = free ? Integer.MAX_VALUE : loaded == null ? 0 : loaded.rounds();

		if (available == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
					SoundSource.PLAYERS, 0.6f, 1.6f);
			return InteractionResult.FAIL;
		}
		if (gun.has(ModComponents.BOLT_OPEN)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("item.mymod.gun.work_bolt"));
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
					SoundSource.PLAYERS, 0.4f, 2.0f);
			return InteractionResult.FAIL;
		}

		FireMode mode = fireMode(gun);
		int shots = switch (mode) {
			case BURST -> Math.min(FireMode.BURST_SIZE, available);
			case AUTO -> Math.min(stats.shotsPerCycle(), available);
			default -> 1;
		};
		if (level.isClientSide()) {
			GunRecoil.queue(player, shots, stats);
			return InteractionResult.CONSUME;
		}

		fireRound((ServerLevel) level, player, gun, hand);
		if (shots > 1) {
			BurstFire.schedule(player, hand, shots - 1);
		}
		// A burst blocks the next click until it's out; a bolt or revolver action blocks it for its cycle time.
		int cooldown = Math.max(mode == FireMode.BURST ? shots + 1 : 0, stats.cycleTicks());
		if (cooldown > 0) {
			player.getCooldowns().addCooldown(gun, cooldown);
		}
		return InteractionResult.CONSUME;
	}

	/** Fires one round from the magazine. Returns false (and does nothing) if there's no round to fire. */
	public boolean fireRound(ServerLevel level, Player player, ItemStack gun, InteractionHand hand) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		boolean free = player.hasInfiniteMaterials();
		Ammo round = loaded == null || loaded.isEmpty() ? (free ? ammo.getFirst() : null) : Ammo.find(ammo, loaded.ammo());
		if (round == null) {
			return false;
		}

		float spread = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats) : 0.0f;
		fire(level, player, round, spread);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), stats.sound(), SoundSource.PLAYERS,
				1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));

		if (fireMode(gun) == FireMode.BOLT) {
			gun.set(ModComponents.BOLT_OPEN, true);
		}
		if (!free) {
			gun.set(ModComponents.LOADED_MAGAZINE, loaded.withRounds(loaded.rounds() - 1));
			if (gun.nextDamageWillBreak()) {
				ejectMagazine(player, gun);
			}
			gun.hurtAndBreak(1, player, hand);
		}
		return true;
	}

	/**
	 * Swaps the inserted magazine for the fullest one in the player's inventory. Only an empty (or
	 * missing) magazine is swapped unless the player is sneaking, which does a tactical reload.
	 */
	public void reload(ServerPlayer player, ItemStack gun) {
		// Punching a bolt gun with the bolt open works the bolt, chambering the next round. With the magazine
		// dry (or when sneaking for a tactical reload) it reloads instead, and the reload chambers a round.
		MagazineContents current = gun.get(ModComponents.LOADED_MAGAZINE);
		boolean roundToChamber = current != null && !current.isEmpty() || player.hasInfiniteMaterials();
		if (gun.has(ModComponents.BOLT_OPEN) && roundToChamber && !player.isShiftKeyDown()) {
			gun.remove(ModComponents.BOLT_OPEN);
			player.getCooldowns().addCooldown(gun, BOLT_TICKS);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END,
					SoundSource.PLAYERS, 0.9f, 0.7f);
			return;
		}
		if (stats.singleRounds()) {
			reloadCylinder(player, gun);
			return;
		}
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		int roundsLeft = loaded == null ? 0 : loaded.rounds();
		if (roundsLeft > 0 && !player.isShiftKeyDown()) {
			return;
		}

		int bestSlot = fullestMagazineSlot(player, roundsLeft);
		if (bestSlot < 0) {
			failReload(player, roundsLeft > 0 ? "item.mymod.gun.no_fuller_magazines" : "item.mymod.gun.no_magazines");
			return;
		}

		ItemStack fresh = player.getInventory().getItem(bestSlot).split(1);
		MagazineItem freshType = (MagazineItem) fresh.getItem();
		ejectMagazine(player, gun);
		gun.set(ModComponents.LOADED_MAGAZINE, fresh.get(ModComponents.MAGAZINE_CONTENTS));
		gun.set(ModComponents.LOADED_MAGAZINE_TYPE, fresh.typeHolder());
		gun.remove(ModComponents.BOLT_OPEN);
		// From empty, the gun also has to be racked (or its bolt worked) once the new magazine is in.
		player.getCooldowns().addCooldown(gun, stats.reloadTicks() + freshType.extraReloadTicks() + (roundsLeft == 0 ? RACK_TICKS : 0));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END,
				SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/**
	 * Tops up a cylinder-fed gun. A loaded speedloader refills an empty cylinder all at once (sneak to swap
	 * out a partly loaded one); otherwise each punch thumbs in one loose round matching what's already
	 * chambered, falling back to a speedloader when there are no loose rounds.
	 */
	private void reloadCylinder(ServerPlayer player, ItemStack gun) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		int rounds = loaded == null ? 0 : loaded.rounds();
		if (rounds >= loadedMagazine(gun).capacity()) {
			player.sendOverlayMessage(Component.translatable("item.mymod.gun.cylinder_full"));
			return;
		}

		int speedloader = fullestMagazineSlot(player, rounds);
		if (speedloader >= 0 && (rounds == 0 || player.isShiftKeyDown())) {
			useSpeedloader(player, gun, speedloader);
			return;
		}

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			boolean fits = rounds == 0 ? Ammo.find(ammo, stack.typeHolder()) != null : loaded.holds(stack);
			if (stack.isEmpty() || !fits) {
				continue;
			}
			MagazineContents next = rounds == 0 ? new MagazineContents(stack.typeHolder(), 1) : loaded.withRounds(rounds + 1);
			stack.shrink(1);
			gun.set(ModComponents.LOADED_MAGAZINE, next);
			player.getCooldowns().addCooldown(gun, SINGLE_ROUND_TICKS);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE,
					SoundSource.PLAYERS, 0.6f, 1.2f + 0.1f * next.rounds());
			return;
		}

		if (speedloader >= 0) {
			useSpeedloader(player, gun, speedloader);
		} else {
			failReload(player, rounds == 0 ? "item.mymod.magazine.no_ammo"
					: "item.mymod.magazine.no_matching_ammo");
		}
	}

	/** Empties a speedloader into the cylinder; it stays in the inventory, and leftover live rounds come back loose. */
	private void useSpeedloader(ServerPlayer player, ItemStack gun, int slot) {
		MagazineContents fresh = player.getInventory().getItem(slot).remove(ModComponents.MAGAZINE_CONTENTS);
		returnLooseRounds(player, gun);
		gun.set(ModComponents.LOADED_MAGAZINE, fresh);
		player.getCooldowns().addCooldown(gun, stats.reloadTicks());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END,
				SoundSource.PLAYERS, 1.0f, 0.9f);
	}

	private void returnLooseRounds(Player player, ItemStack gun) {
		MagazineContents loaded = gun.remove(ModComponents.LOADED_MAGAZINE);
		if (loaded != null && !loaded.isEmpty()) {
			player.getInventory().placeItemBackInInventory(new ItemStack(loaded.ammo(), loaded.rounds()), Prediction.SERVER_ONLY);
		}
	}

	/** The inventory slot of the accepted magazine holding the most rounds, if it holds more than {@code moreThan}. */
	private int fullestMagazineSlot(Player player, int moreThan) {
		int bestSlot = -1;
		int bestRounds = moreThan;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			int rounds = stack.getItem() instanceof MagazineItem magazine && magazines.contains(magazine)
					? MagazineItem.rounds(stack) : 0;
			if (rounds > bestRounds) {
				bestSlot = i;
				bestRounds = rounds;
			}
		}
		return bestSlot;
	}

	private static void failReload(ServerPlayer player, String messageKey) {
		player.sendOverlayMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
				SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	/** Returns the inserted magazine (or a cylinder's loose rounds), with whatever is left, to the player's inventory. */
	private void ejectMagazine(Player player, ItemStack gun) {
		if (stats.singleRounds()) {
			returnLooseRounds(player, gun);
			return;
		}
		MagazineContents loaded = gun.remove(ModComponents.LOADED_MAGAZINE);
		if (loaded == null) {
			return;
		}
		ItemStack spent = new ItemStack(loadedMagazine(gun));
		gun.remove(ModComponents.LOADED_MAGAZINE_TYPE);
		if (!loaded.isEmpty()) {
			spent.set(ModComponents.MAGAZINE_CONTENTS, loaded);
		}
		player.getInventory().placeItemBackInInventory(spent, Prediction.SERVER_ONLY);
	}

	/** @param spread cone radius in degrees the shot strays within */
	private void fire(ServerLevel level, Player shooter, Ammo round, float spread) {
		Vec3 eye = shooter.getEyePosition();
		Vec3 look = shooter.getViewVector(1.0f);
		if (spread > 0.0f) {
			// Uniform over the cone's disc, so shots cluster no more at the edge than the middle.
			float radius = spread * (float) Math.sqrt(shooter.getRandom().nextFloat());
			float angle = shooter.getRandom().nextFloat() * (float) (Math.PI * 2.0);
			look = Vec3.directionFromRotation(shooter.getXRot() + radius * (float) Math.sin(angle),
					shooter.getYRot() + radius * (float) Math.cos(angle));
		}
		Vec3 end = eye.add(look.scale(stats.range()));

		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}

		AABB sweep = shooter.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(shooter, eye, end, sweep,
				entity -> !entity.isSpectator() && entity.isPickable(), eye.distanceToSqr(end));

		Vec3 muzzle = eye.add(look.scale(1.2)).add(0, -0.15, 0);
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);

		if (entityHit != null) {
			Entity target = entityHit.getEntity();
			DamageSource source = new DamageSource(
					level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(round.damageType()), shooter);
			target.hurtServer(level, source, round.damage());
			Vec3 hit = entityHit.getLocation();
			level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 6, 0.1, 0.1, 0.1, 0.2);
			drawTracer(level, muzzle, hit);
		} else if (blockHit.getType() == HitResult.Type.BLOCK) {
			Vec3 hit = blockHit.getLocation();
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockHit.getBlockPos()));
			level.sendParticles(debris, hit.x, hit.y, hit.z, 8, 0.05, 0.05, 0.05, 0.1);
			level.sendParticles(ParticleTypes.SMOKE, hit.x, hit.y, hit.z, 2, 0.02, 0.02, 0.02, 0.01);
			drawTracer(level, muzzle, hit);
		} else {
			drawTracer(level, muzzle, end);
		}
	}

	private static void drawTracer(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		int steps = (int) (path.length() / 2.0);
		for (int i = 1; i < steps; i++) {
			Vec3 p = from.add(path.scale(i / (double) steps));
			level.sendParticles(ParticleTypes.WHITE_SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		if (loaded == null) {
			tooltip.accept(Component.translatable(stats.singleRounds() ? "item.mymod.gun.cylinder_empty"
					: "item.mymod.gun.no_magazine").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.mymod.magazine.rounds", loaded.rounds(), loadedMagazine(stack).capacity(),
					new ItemStack(loaded.ammo()).getHoverName()).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.gun.fire_mode", Component.translatable(fireMode(stack).translationKey()))
				.withStyle(ChatFormatting.GRAY));
		if (stats.fireModes().size() > 1) {
			tooltip.accept(Component.translatable("item.mymod.gun.switch_fire_mode", Component.keybind("key.mymod.fire_mode"))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (stats.fireModes().contains(FireMode.BOLT)) {
			tooltip.accept(Component.translatable("item.mymod.gun.bolt_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (stats.scoped()) {
			tooltip.accept(Component.translatable("item.mymod.gun.scope_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip.accept(Component.translatable(stats.singleRounds() ? "item.mymod.gun.cylinder_hint" : "item.mymod.gun.hint")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
