package com.jheinz.mymod.shotgun;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.LivingEntity;
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

import com.jheinz.mymod.Ammo;
import com.jheinz.mymod.FireMode;
import com.jheinz.mymod.GunItem;
import com.jheinz.mymod.GunRecoil;
import com.jheinz.mymod.GunSpread;
import com.jheinz.mymod.GunStats;
import com.jheinz.mymod.MagazineContents;
import com.jheinz.mymod.ModComponents;
import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.attachment.AttachmentItem;
import com.jheinz.mymod.attachment.Attachments;
import com.jheinz.mymod.silver.EvilBane;

/**
 * A tube-fed shotgun. Shells go into the tube one at a time straight from the inventory (no magazines): a punch
 * starts loading, which pushes in a shell every {@link #SHELL_TICKS} (half while overclocked) until the tube is full
 * or the shells run out, and firing stops it. Loading an empty gun ends by working the pump to chamber a shell, and
 * firing before then just works the pump. Sneak and punch to swap the shell type: the tube is emptied back into the
 * inventory and the other shells go in.
 *
 * <p>Each shot throws a spread of pellets (or one slug) that lose damage with distance; the pellets that hit a
 * target land as one blow. In pump mode the pump is worked after every shot ({@link #PUMP_TICKS}), ejecting the hull;
 * a semi-auto gun cycles itself in its {@link Handling#cycleTicks()}. The action and loading are kept on the stack
 * ({@link Shotguns#SHOTGUN_ACTION}) so every client can animate them, and {@link Shotguns} runs loading from the
 * server tick.
 */
public class ShotgunItem extends GunItem {
	public static final int PUMP_TICKS = 12;
	public static final int SHELL_TICKS = 10;
	/** Pellets that draw a smoke trail (the rest would just make a mess). */
	private static final int TRACERS = 3;

	/**
	 * A kind of shell.
	 *
	 * @param pellets          projectiles per shot
	 * @param spreadDegrees    cone radius the pellets scatter within, around where the shot is aimed
	 * @param fullDamageRange  pellets do full damage out to here (blocks)...
	 * @param falloffRange     ...falling off linearly to {@code minDamageFactor} of it here, and staying there
	 * @param range            how far a pellet can reach at all
	 * @param color            RGB for this shell in the ammo HUD
	 */
	public record Shell(Item item, int pellets, float pelletDamage, float spreadDegrees, double fullDamageRange,
			double falloffRange, float minDamageFactor, double range, int color) {
		float damageAt(double distance) {
			double t = Math.clamp((distance - fullDamageRange) / (falloffRange - fullDamageRange), 0.0, 1.0);
			return pelletDamage * (float) (1.0 - t * (1.0 - minDamageFactor));
		}
	}

	/**
	 * How the gun shoots in one fire mode, relative to its shells and stats.
	 *
	 * @param cycleTicks ticks before it can fire again (the pump, for pump mode)
	 */
	public record Handling(float damage, float pelletSpread, float recoil, int cycleTicks) {
	}

	private final List<Shell> shells;
	private final int tubeCapacity;
	private final Map<FireMode, Handling> handling;

	/** @param handling for each fire mode the gun has */
	public ShotgunItem(Properties properties, GunStats stats, List<Shell> shells, int tubeCapacity, Map<FireMode, Handling> handling) {
		super(properties, stats, List.of(), shells.stream()
				.map(shell -> new Ammo(shell.item(), shell.pellets() * shell.pelletDamage(), ModDamageTypes.BULLET, shell.color()))
				.toList());
		this.shells = shells;
		this.tubeCapacity = tubeCapacity;
		this.handling = handling;
	}

	public List<Shell> shells() {
		return shells;
	}

	@Override
	public int capacity(ItemStack gun) {
		return tubeCapacity;
	}

	public Handling handling(ItemStack gun) {
		return handling.get(fireMode(gun));
	}

	/** Kicks harder (or softer) in some fire modes. */
	@Override
	public GunStats stats(ItemStack gun, LivingEntity holder) {
		GunStats fitted = super.stats(gun, holder);
		return fitted.withRecoil(fitted.recoilDegrees() * handling(gun).recoil());
	}

	private Shell shell(Holder<Item> item) {
		for (Shell shell : shells) {
			if (item.value() == shell.item()) {
				return shell;
			}
		}
		return null;
	}

	private static int rounds(ItemStack gun) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		return loaded == null ? 0 : loaded.rounds();
	}

	/** Whether shells are going into this gun's tube right now. */
	public static boolean loading(ItemStack gun) {
		ShotgunAction action = gun.get(Shotguns.SHOTGUN_ACTION);
		return action != null && action.kind() == ShotgunAction.Kind.LOAD;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		ShotgunAction action = gun.get(Shotguns.SHOTGUN_ACTION);
		long now = level.getGameTime();
		int rounds = rounds(gun);
		if (action != null && action.kind() == ShotgunAction.Kind.LOAD) {
			if (rounds == 0) {
				return InteractionResult.FAIL;
			}
			if (action.chambering()) {
				// Nothing chambered yet: stop loading and work the pump instead of firing.
				if (level instanceof ServerLevel) {
					pump(player, gun, action.shell(), now, false);
				}
				return InteractionResult.CONSUME;
			}
			if (level instanceof ServerLevel) {
				gun.remove(Shotguns.SHOTGUN_ACTION);
			}
		} else if (action != null && now < action.end()) {
			return InteractionResult.FAIL;
		}
		if (rounds == 0 && !player.hasInfiniteMaterials()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			GunRecoil.queue(player, 1, stats(gun, player));
			return InteractionResult.CONSUME;
		}

		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		Holder<Item> fired = loaded != null && !loaded.isEmpty() ? loaded.ammo() : BuiltInRegistries.ITEM.wrapAsHolder(shells.getFirst().item());
		if (!fireRound((ServerLevel) level, player, gun, hand)) {
			return InteractionResult.FAIL;
		}
		if (fireMode(gun) == FireMode.PUMP) {
			pump(player, gun, fired, now, true);
		} else {
			int cycle = handling(gun).cycleTicks();
			gun.set(Shotguns.SHOTGUN_ACTION, new ShotgunAction(ShotgunAction.Kind.CYCLE, now, cycle, fired, false));
			player.getCooldowns().addCooldown(gun, cycle);
		}
		return InteractionResult.CONSUME;
	}

	/**
	 * Works the pump, clearing the fired hull (or, with {@code afterShot} false, just chambering the first shell); the
	 * gun can fire once it's done.
	 */
	private void pump(Player player, ItemStack gun, Holder<Item> shell, long now, boolean afterShot) {
		int ticks = fireMode(gun) == FireMode.PUMP ? handling(gun).cycleTicks() : PUMP_TICKS;
		gun.set(Shotguns.SHOTGUN_ACTION, new ShotgunAction(ShotgunAction.Kind.PUMP, now, ticks, shell, !afterShot));
		player.getCooldowns().addCooldown(gun, ticks);
		// The shooter hears their own pump from the animation's cues; this is for everyone else.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), Shotguns.PUMP_BACK, SoundSource.PLAYERS, 0.8f, 1.0f);
	}

	/** Fires one shell's pellets. Returns false (and does nothing) if there's no shell to fire. */
	@Override
	public boolean fireRound(ServerLevel level, Player player, ItemStack gun, InteractionHand hand) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		boolean free = player.hasInfiniteMaterials();
		Shell shell = loaded == null || loaded.isEmpty() ? (free ? shells.getFirst() : null) : shell(loaded.ammo());
		if (shell == null) {
			return false;
		}
		Handling handled = handling(gun);
		float aim = player instanceof ServerPlayer serverPlayer ? GunSpread.serverShot(serverPlayer, stats(gun, player)) : 0.0f;
		float[] centre = scatter(player, player.getXRot(), player.getYRot(), aim);
		Vec3 eye = player.getEyePosition();
		Vec3 muzzle = eye.add(player.getViewVector(1.0f).scale(1.3)).add(0, -0.15, 0);
		DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.BULLET), player);

		// Every pellet that finds a target adds to one blow, so they aren't lost to the target's hurt cooldown.
		Map<Entity, Float> blows = new LinkedHashMap<>();
		Map<Entity, Vec3> hitAt = new LinkedHashMap<>();
		for (int i = 0; i < shell.pellets(); i++) {
			float[] dir = scatter(player, centre[0], centre[1], shell.spreadDegrees() * handled.pelletSpread());
			Vec3 look = Vec3.directionFromRotation(dir[0], dir[1]);
			Vec3 end = eye.add(look.scale(shell.range()));
			BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (blockHit.getType() != HitResult.Type.MISS) {
				end = blockHit.getLocation();
			}
			AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
			EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
					entity -> !entity.isSpectator() && entity.isPickable(), eye.distanceToSqr(end));
			Vec3 stop;
			if (entityHit != null) {
				stop = entityHit.getLocation();
				Entity target = entityHit.getEntity();
				blows.merge(target, shell.damageAt(eye.distanceTo(stop)) * handled.damage(), Float::sum);
				hitAt.putIfAbsent(target, stop);
				level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.08, 0.08, 0.08, 0.2);
			} else if (blockHit.getType() == HitResult.Type.BLOCK) {
				stop = blockHit.getLocation();
				BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockHit.getBlockPos()));
				level.sendParticles(debris, stop.x, stop.y, stop.z, 3, 0.04, 0.04, 0.04, 0.1);
				level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 1, 0.02, 0.02, 0.02, 0.01);
			} else {
				stop = end;
			}
			if (i < TRACERS) {
				GunItem.drawTracer(level, muzzle, stop, false);
			}
		}
		blows.forEach((target, damage) -> {
			target.hurtServer(level, source, damage * EvilBane.multiplier(shell.item(), target));
			EvilBane.afterHit(level, shell.item(), target);
		});

		// A shotgun's bark: a fat puff of smoke and a lick of flame that others see.
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.06, 0.06, 0.06, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), stats().sound(), SoundSource.PLAYERS,
				Attachments.loudness(gun) * 1.2f, 0.95f + player.getRandom().nextFloat() * 0.1f);
		player.awardStat(Stats.ITEM_USED.get(this));
		if (!free) {
			gun.set(ModComponents.LOADED_MAGAZINE, loaded.withRounds(loaded.rounds() - 1));
			if (gun.nextDamageWillBreak()) {
				returnShells(player, gun);
			}
			gun.hurtAndBreak(1, player, hand);
		}
		return true;
	}

	/** A direction (pitch, yaw) within {@code spread} degrees of the given one, uniform over the cone's disc. */
	private static float[] scatter(Player player, float pitch, float yaw, float spread) {
		if (spread <= 0.0f) {
			return new float[] {pitch, yaw};
		}
		float radius = spread * (float) Math.sqrt(player.getRandom().nextFloat());
		float angle = player.getRandom().nextFloat() * (float) (Math.PI * 2.0);
		return new float[] {pitch + radius * (float) Math.sin(angle), yaw + radius * (float) Math.cos(angle)};
	}

	/** Punching starts loading shells; sneaking swaps the type in the tube. Loading itself runs from {@link Shotguns}. */
	@Override
	public void reload(ServerPlayer player, ItemStack gun) {
		if (loading(gun)) {
			return;
		}
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		int rounds = loaded == null ? 0 : loaded.rounds();
		Holder<Item> type;
		if (player.isShiftKeyDown() && rounds > 0) {
			type = findShell(player, held -> !held.is(loaded.ammo()));
			if (type == null) {
				fail(player, Component.translatable("item.mymod.shotgun.no_other_shells"));
				return;
			}
			returnShells(player, gun);
			rounds = 0;
		} else if (rounds >= tubeCapacity) {
			player.sendOverlayMessage(Component.translatable("item.mymod.shotgun.tube_full"));
			return;
		} else if (rounds > 0) {
			// Topping up: more of what's already in the tube.
			type = loaded.ammo();
			if (!player.hasInfiniteMaterials() && slotOf(player, type) < 0) {
				fail(player, Component.translatable("item.mymod.magazine.no_matching_ammo", new ItemStack(type).getHoverName()));
				return;
			}
		} else {
			type = findShell(player, held -> true);
			if (type == null && player.hasInfiniteMaterials()) {
				type = BuiltInRegistries.ITEM.wrapAsHolder(shells.getFirst().item());
			}
			if (type == null) {
				fail(player, Component.translatable("item.mymod.shotgun.no_shells"));
				return;
			}
		}
		startLoading(player, gun, type, rounds == 0, player.level().getGameTime());
	}

	private void startLoading(ServerPlayer player, ItemStack gun, Holder<Item> type, boolean chambering, long now) {
		int ticks = Math.max(1, Math.round(SHELL_TICKS * reloadFactor(player)));
		gun.set(Shotguns.SHOTGUN_ACTION, new ShotgunAction(ShotgunAction.Kind.LOAD, now, ticks, type, chambering));
	}

	/** One shell's loading time is up: push it in (if there's still one to push) and carry on or stop. */
	void loadShell(ServerPlayer player, ItemStack gun, ShotgunAction action, long now) {
		MagazineContents loaded = gun.get(ModComponents.LOADED_MAGAZINE);
		int rounds = loaded == null ? 0 : loaded.rounds();
		boolean free = player.hasInfiniteMaterials();
		int slot = free ? -1 : slotOf(player, action.shell());
		if (rounds < tubeCapacity && (free || slot >= 0)) {
			if (slot >= 0) {
				player.getInventory().getItem(slot).shrink(1);
			}
			rounds++;
			gun.set(ModComponents.LOADED_MAGAZINE, new MagazineContents(action.shell(), rounds));
			player.level().playSound(player, player.getX(), player.getY(), player.getZ(), Shotguns.SHELL_INSERT, SoundSource.PLAYERS,
					0.7f, 0.95f + 0.04f * rounds);
		}
		if (rounds < tubeCapacity && (free || slotOf(player, action.shell()) >= 0)) {
			startLoading(player, gun, action.shell(), action.chambering(), now);
		} else if (action.chambering() && rounds > 0) {
			pump(player, gun, action.shell(), now, false);
		} else {
			gun.remove(Shotguns.SHOTGUN_ACTION);
		}
	}

	private Holder<Item> findShell(Player player, Predicate<ItemStack> accept) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && shell(stack.typeHolder()) != null && accept.test(stack)) {
				return stack.typeHolder();
			}
		}
		return null;
	}

	private static int slotOf(Player player, Holder<Item> shell) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(shell)) {
				return i;
			}
		}
		return -1;
	}

	/** Empties the tube back into the inventory. */
	private static void returnShells(Player player, ItemStack gun) {
		MagazineContents loaded = gun.remove(ModComponents.LOADED_MAGAZINE);
		if (loaded != null && !loaded.isEmpty() && !player.hasInfiniteMaterials()) {
			player.getInventory().placeItemBackInInventory(new ItemStack(loaded.ammo(), loaded.rounds()), Prediction.SERVER_ONLY);
		}
	}

	private static void fail(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
			TooltipFlag flag) {
		MagazineContents loaded = stack.get(ModComponents.LOADED_MAGAZINE);
		if (loaded == null || loaded.isEmpty()) {
			tooltip.accept(Component.translatable("item.mymod.shotgun.tube_empty", tubeCapacity).withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("item.mymod.magazine.rounds", loaded.rounds(), tubeCapacity,
					new ItemStack(loaded.ammo()).getHoverName()).withStyle(ChatFormatting.GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.gun.fire_mode", Component.translatable(fireMode(stack).translationKey()))
				.withStyle(ChatFormatting.GRAY));
		for (AttachmentItem attachment : Attachments.all(stack)) {
			tooltip.accept(Component.translatable("item.mymod.gun.attachment", Component.translatable(attachment.getDescriptionId()))
					.withStyle(ChatFormatting.BLUE));
		}
		if (stats(stack).fireModes().size() > 1) {
			tooltip.accept(Component.translatable("item.mymod.gun.switch_fire_mode", Component.keybind("key.mymod.fire_mode"))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip.accept(Component.translatable("item.mymod.shotgun.hint").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable("item.mymod.shotgun.swap_hint").withStyle(ChatFormatting.DARK_GRAY));
		if (!Attachments.all(stack).isEmpty()) {
			tooltip.accept(Component.translatable("item.mymod.gun.remove_attachment_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
