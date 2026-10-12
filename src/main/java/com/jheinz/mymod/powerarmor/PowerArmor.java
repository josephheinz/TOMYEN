package com.jheinz.mymod.powerarmor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import com.jheinz.mymod.ModDamageTypes;
import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * The server side of power armor: getting in and out, wearing it, flying it and running it dry.
 *
 * <p><b>Getting in.</b> A complete suit (six pieces and a fusion core) on a station or parked, and the player
 * wearing an exoskeleton with nothing socketed (no module, armor or elytra) as its frame: sneak-use the station
 * with empty hands, or use the parked suit. The suit leaves the rack (or the parked suit vanishes) and is on the
 * player at once, but they're locked in place for {@link #ENTER_TICKS} while it closes around them. Taking the
 * exoskeleton off ejects them, leaving the suit parked.
 *
 * <p><b>Getting out.</b> Holding the Power Armor key asks to exit: the suit opens over {@link #EXIT_TICKS} and is
 * left parked where they stood, and they step out behind it. Using an empty station while wearing it docks
 * instead: they're walked onto the rack, and the suit is left on it. Dying in it leaves it parked where they fell.
 *
 * <p><b>Wearing it.</b> Hits land on a piece (by where they came from) and that piece absorbs its mark's share
 * and takes the wear; a broken piece protects nothing. No fall damage: a heavy landing sends out a shockwave
 * instead. No knockback, steps up full blocks, walks {@link #WALK_PENALTY} slower but sprints hard, sinks in
 * water and walks the bottom, breathing from the helmet's tank. The helmet is also a five-socket Neuralink.
 *
 * <p><b>Fuel.</b> The core drains walking ({@link #WALK_DRAIN} a tick), sprinting, flying and firing, all scaled
 * by the torso's efficiency. Empty, the suit can only lumber: heavy slowness and no flight or weapons until a
 * charged core is swapped in (the Swap Core key takes the fullest from the inventory). The exact charge is kept
 * here while worn and written back to the suit whenever the whole percentage changes.
 *
 * <p><b>Flight</b> ({@link PowerArmorFlight}) and <b>weapons</b> ({@link Repulsors}) have their own classes.
 */
public final class PowerArmor {
	public static final int ENTER_TICKS = 40;
	public static final int EXIT_TICKS = 30;
	/** Holding the Power Armor key this long asks to climb out. */
	public static final int EXIT_HOLD_TICKS = 20;

	// Core drain, in charge units a tick before the torso's efficiency. A full core is 72000.
	static final float WALK_DRAIN = 1.0f;
	static final float SPRINT_DRAIN = 3.0f;
	static final float LIFE_SUPPORT_DRAIN = 1.0f;

	static final double WALK_PENALTY = 0.2;
	private static final double SPRINT_BONUS = 0.5;
	private static final double EMPTY_PENALTY = 0.6;
	/** How far a fall has to be (in blocks) for the landing to send out a shockwave. */
	private static final double SHOCKWAVE_FALL = 3.0;
	private static final float LOW_FUEL = 0.15f;

	// Attribute modifiers, applied as transient modifiers while worn.
	private static final Identifier FRAME = MyMod.id("power_armor");
	private static final Identifier SPRINT = MyMod.id("power_armor_sprint");
	private static final Identifier EMPTY = MyMod.id("power_armor_empty");
	private static final Identifier LOCK = MyMod.id("power_armor_lock");
	static final Identifier THRUST = MyMod.id("power_armor_thrust");

	/** Everything tracked per wearer while the server is running. */
	static final class State {
		float fuel;
		int lastFlushedPercent = -1;
		// Thrusters.
		int burn;
		boolean overheated;
		int groundTicks;
		// Weapons.
		int repulsorCooldown;
		int repulsorCooldownMax = 1;
		boolean leftArmNext;
		int unibeamCooldown;
		/** Ticks the unibeam has been charging, or -1. */
		int unibeamCharge = -1;
		// Life support.
		int air;
		// Entering, exiting or docking.
		int sequence = PowerArmorMotion.NONE;
		int sequenceTicks;
		Vec3 anchor = Vec3.ZERO;
		float anchorYaw;
		@Nullable BlockPos dock;
		Vec3 lastPos;
		boolean warnedLowFuel;
		private PowerArmorNetworking.@Nullable Status lastSent;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private PowerArmor() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(PowerArmor::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && isWearing(player)) {
				diedInSuit(player);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			State state = STATES.remove(player.getUUID());
			if (state != null) {
				flush(player, state);
			}
		});
	}

	// ------------------------------------------------------------------------------------------------ queries (both sides)

	public static boolean isWearing(Entity entity) {
		return entity.hasAttached(PowerArmors.SUIT);
	}

	public static @Nullable PowerArmorSuit worn(Entity entity) {
		return entity.getAttached(PowerArmors.SUIT);
	}

	/** The helmet of the suit an entity wears, or empty: it's also a Neuralink. */
	public static ItemStack wornHelmet(Entity entity) {
		PowerArmorSuit suit = worn(entity);
		return suit == null ? ItemStack.EMPTY : suit.piece(PowerArmorPiece.HELMET);
	}

	public static PowerArmorMotion motion(Entity entity) {
		PowerArmorMotion motion = entity.getAttached(PowerArmors.MOTION);
		return motion == null ? PowerArmorMotion.IDLE : motion;
	}

	/** Whether an entity wears an exoskeleton with nothing socketed (no module, armor or elytra): power armor's frame. */
	public static boolean hasEmptyFrame(LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		return chest.getItem() instanceof ExoskeletonItem && ExoSockets.isEmpty(chest);
	}

	/**
	 * Which piece a hit lands on: by the height (and side) it came from, or, with no direction (fire, gas), a
	 * random piece weighted by size.
	 */
	public static PowerArmorPiece pieceHit(Entity frame, float yaw, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		RandomSource random = frame.getRandom();
		if (from == null) {
			int total = 0;
			for (PowerArmorPiece piece : PowerArmorPiece.values()) {
				total += piece.hitWeight();
			}
			int roll = random.nextInt(total);
			for (PowerArmorPiece piece : PowerArmorPiece.values()) {
				roll -= piece.hitWeight();
				if (roll < 0) {
					return piece;
				}
			}
			return PowerArmorPiece.TORSO;
		}
		double height = (from.y - frame.getY()) / frame.getBbHeight();
		// The wearer's right, from the way they face.
		float radians = yaw * Mth.DEG_TO_RAD;
		double side = (from.x - frame.getX()) * -Mth.cos(radians) + (from.z - frame.getZ()) * -Mth.sin(radians);
		double horizontal = Math.max(0.3, Math.sqrt(frame.distanceToSqr(from.x, frame.getY(), from.z)));
		double lean = side / horizontal;
		if (height > 0.78) {
			return PowerArmorPiece.HELMET;
		}
		if (height > 0.42) {
			if (Math.abs(lean) > 0.55) {
				return lean > 0 ? PowerArmorPiece.RIGHT_ARM : PowerArmorPiece.LEFT_ARM;
			}
			return PowerArmorPiece.TORSO;
		}
		if (Math.abs(lean) < 0.15) {
			return random.nextBoolean() ? PowerArmorPiece.RIGHT_LEG : PowerArmorPiece.LEFT_LEG;
		}
		return lean > 0 ? PowerArmorPiece.RIGHT_LEG : PowerArmorPiece.LEFT_LEG;
	}

	// ------------------------------------------------------------------------------------------------ getting in

	/** Why a player can't climb into a suit, or null if they can. */
	public static @Nullable Component refusal(ServerPlayer player, PowerArmorSuit suit) {
		if (isWearing(player)) {
			return Component.translatable("power_armor.mymod.already_wearing");
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof ExoskeletonItem)) {
			return Component.translatable("power_armor.mymod.needs_frame");
		}
		if (!ExoSockets.isEmpty(chest)) {
			return Component.translatable("power_armor.mymod.frame_not_empty");
		}
		List<Component> missing = new ArrayList<>();
		for (PowerArmorPiece piece : PowerArmorPiece.values()) {
			if (!(suit.piece(piece).getItem() instanceof PowerArmorPieceItem)) {
				missing.add(piece.displayName());
			}
		}
		if (!missing.isEmpty()) {
			return Component.translatable("power_armor.mymod.missing_pieces", ComponentUtils.formatList(missing, Function.identity()));
		}
		if (!suit.hasCore()) {
			return Component.translatable("power_armor.mymod.no_core");
		}
		return null;
	}

	private static boolean refuse(ServerPlayer player, @Nullable Component refusal) {
		if (refusal == null) {
			return false;
		}
		player.sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.RED));
		notify(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.DISPENSER_FAIL), 0.6f, 1.2f);
		return true;
	}

	/** A sound only the player hears. */
	static void notify(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), volume, pitch,
				player.getRandom().nextLong()));
	}

	/** Sneak-use on a station: climb into the suit on its rack. */
	static void enterFromStation(ServerPlayer player, BlockPos pos, PowerArmorStationBlockEntity station) {
		PowerArmorSuit suit = station.suit();
		if (refuse(player, refusal(player, suit))) {
			return;
		}
		station.setSuit(PowerArmorSuit.EMPTY);
		beginEntering(player, suit, PowerArmorStationBlock.anchor(pos), PowerArmorStationBlock.yaw(station.getBlockState()));
	}

	/** Use on a parked suit: climb back in. */
	static void enterParked(ServerPlayer player, ParkedPowerArmor parked) {
		PowerArmorSuit suit = parked.suit();
		if (refuse(player, refusal(player, suit))) {
			return;
		}
		Vec3 anchor = parked.position();
		float yaw = parked.getYRot();
		parked.discard();
		beginEntering(player, suit, anchor, yaw);
	}

	private static void beginEntering(ServerPlayer player, PowerArmorSuit suit, Vec3 anchor, float yaw) {
		ServerLevel level = player.level();
		player.setAttached(PowerArmors.SUIT, suit);
		player.setAttached(PowerArmors.MOTION, new PowerArmorMotion(PowerArmorMotion.OFF, PowerArmorMotion.ENTERING, level.getGameTime()));
		State state = new State();
		state.fuel = suit.charge();
		state.air = suit.stats().airTicks();
		state.sequence = PowerArmorMotion.ENTERING;
		state.anchor = anchor;
		state.anchorYaw = yaw;
		STATES.put(player.getUUID(), state);
		player.stopUsingItem();
		player.teleportTo(level, anchor.x, anchor.y, anchor.z, Set.of(), yaw, 0.0f, false);
		player.setYBodyRot(yaw);
		level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	// ------------------------------------------------------------------------------------------------ getting out

	/** The client held the Power Armor key: open up and step out, leaving the suit parked. */
	static void requestExit(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.sequence != PowerArmorMotion.NONE || !isWearing(player)) {
			return;
		}
		beginExiting(player, state, player.position(), player.getYRot(), null);
	}

	/** Use on a station while wearing a suit: if its rack is empty, climb out onto it. */
	static void dock(ServerPlayer player, BlockPos pos, PowerArmorStationBlockEntity station) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.sequence != PowerArmorMotion.NONE) {
			return;
		}
		if (!station.suit().isEmpty()) {
			refuse(player, Component.translatable("power_armor.mymod.rack_occupied"));
			return;
		}
		beginExiting(player, state, PowerArmorStationBlock.anchor(pos), PowerArmorStationBlock.yaw(station.getBlockState()), pos);
	}

	private static void beginExiting(ServerPlayer player, State state, Vec3 anchor, float yaw, @Nullable BlockPos dock) {
		ServerLevel level = player.level();
		PowerArmorFlight.disengage(player, false);
		state.unibeamCharge = -1;
		state.sequence = PowerArmorMotion.EXITING;
		state.sequenceTicks = 0;
		state.anchor = anchor;
		state.anchorYaw = yaw;
		state.dock = dock;
		player.setAttached(PowerArmors.MOTION, new PowerArmorMotion(PowerArmorMotion.OFF, PowerArmorMotion.EXITING, level.getGameTime()));
		player.teleportTo(level, anchor.x, anchor.y, anchor.z, Set.of(), yaw, player.getXRot(), false);
		player.setYBodyRot(yaw);
		level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	/** The suit has opened: leave it on the rack or standing, and step the player out of it. */
	private static void finishExiting(ServerPlayer player, State state) {
		ServerLevel level = player.level();
		PowerArmorSuit suit = takeOff(player, state);
		Vec3 forward = Vec3.directionFromRotation(0.0f, state.anchorYaw);
		if (state.dock != null && level.getBlockEntity(state.dock) instanceof PowerArmorStationBlockEntity station && station.suit().isEmpty()) {
			station.setSuit(suit);
			stepOut(player, forward);
		} else {
			ParkedPowerArmor.park(level, suit, state.anchor, state.anchorYaw);
			stepOut(player, forward.reverse());
		}
		level.playSound(null, state.anchor.x, state.anchor.y, state.anchor.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** Moves the player a block away from the suit, preferring {@code way}, then the sides, then the other way. */
	private static void stepOut(ServerPlayer player, Vec3 way) {
		Vec3 side = new Vec3(-way.z, 0.0, way.x);
		for (Vec3 step : List.of(way, side, side.reverse(), way.reverse())) {
			Vec3 offset = step.scale(1.0);
			if (player.level().noCollision(player, player.getBoundingBox().move(offset))) {
				player.teleportTo(player.level(), player.getX() + offset.x, player.getY(), player.getZ() + offset.z, Set.of(),
						player.getYRot(), player.getXRot(), false);
				return;
			}
		}
	}

	/** Takes the suit off the player at once (no sequence) and returns it, with its charge written back. */
	private static PowerArmorSuit takeOff(ServerPlayer player, State state) {
		flush(player, state);
		PowerArmorSuit suit = worn(player);
		player.removeAttached(PowerArmors.SUIT);
		player.removeAttached(PowerArmors.MOTION);
		STATES.remove(player.getUUID());
		clearModifiers(player);
		return suit == null ? PowerArmorSuit.EMPTY : suit;
	}

	/** The frame came off (or something else went wrong): the suit falls open where they stand. */
	private static void eject(ServerPlayer player, State state, Component reason) {
		PowerArmorSuit suit = takeOff(player, state);
		ParkedPowerArmor.park(player.level(), suit, player.position(), player.getYRot());
		player.sendOverlayMessage(reason.copy().withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	/** Dying in a suit leaves it standing where they fell (unless they fell out of the world). */
	private static void diedInSuit(ServerPlayer player) {
		State state = STATES.computeIfAbsent(player.getUUID(), uuid -> fresh(player));
		PowerArmorSuit suit = takeOff(player, state);
		ServerLevel level = player.level();
		if (player.getY() > level.getMinY() - 16) {
			Vec3 at = new Vec3(player.getX(), Math.max(player.getY(), level.getMinY()), player.getZ());
			ParkedPowerArmor.park(level, suit, at, player.getYRot());
		}
	}

	// ------------------------------------------------------------------------------------------------ the core

	private static State fresh(ServerPlayer player) {
		State state = new State();
		PowerArmorSuit suit = worn(player);
		state.fuel = suit == null ? 0 : suit.charge();
		state.air = suit == null ? 0 : suit.stats().airTicks();
		return state;
	}

	static @Nullable State state(ServerPlayer player) {
		return STATES.get(player.getUUID());
	}

	/** Takes charge from the core, scaled by the torso's efficiency. */
	static void drain(ServerPlayer player, State state, float amount) {
		PowerArmorSuit suit = worn(player);
		float efficiency = suit == null ? 1.0f : suit.stats().fuelEfficiency();
		state.fuel = Math.max(0.0f, state.fuel - amount * efficiency);
	}

	static boolean hasFuel(State state) {
		return state.fuel > 0.0f;
	}

	/** Writes the exact charge back into the worn suit (which syncs it). */
	private static void flush(ServerPlayer player, State state) {
		PowerArmorSuit suit = worn(player);
		if (suit != null && suit.hasCore()) {
			int charge = Math.round(state.fuel);
			if (suit.charge() != charge) {
				player.setAttached(PowerArmors.SUIT, suit.withCharge(charge));
			}
			state.lastFlushedPercent = percent(state.fuel);
		}
	}

	private static int percent(float fuel) {
		return Mth.ceil(fuel * 100.0f / FusionCoreItem.MAX_CHARGE);
	}

	/** The Swap Core key: trades the core for the fullest one in the inventory, if it's fuller. */
	static void swapCore(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		PowerArmorSuit suit = worn(player);
		if (state == null || suit == null || state.sequence != PowerArmorMotion.NONE) {
			return;
		}
		Inventory inventory = player.getInventory();
		int best = -1;
		int bestCharge = Math.round(state.fuel);
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof FusionCoreItem && FusionCoreItem.charge(stack) > bestCharge) {
				best = slot;
				bestCharge = FusionCoreItem.charge(stack);
			}
		}
		if (best < 0) {
			refuse(player, Component.translatable("power_armor.mymod.no_spare_core"));
			return;
		}
		flush(player, state);
		suit = worn(player);
		ItemStack fresh = inventory.getItem(best).copy();
		inventory.setItem(best, suit.core().copy());
		player.setAttached(PowerArmors.SUIT, suit.with(PowerArmorSuit.CORE, fresh));
		state.fuel = FusionCoreItem.charge(fresh);
		state.lastFlushedPercent = percent(state.fuel);
		state.warnedLowFuel = false;
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.6), player.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
		player.sendOverlayMessage(Component.translatable("power_armor.mymod.core_swapped", Mth.ceil(fresh.isEmpty() ? 0 : FusionCoreItem.fraction(fresh) * 100.0f))
				.withStyle(ChatFormatting.GREEN));
	}

	// ------------------------------------------------------------------------------------------------ damage

	/**
	 * A hit on a wearer, before vanilla armor: the piece it lands on absorbs its share and takes the wear.
	 * Returns what gets through, or -1 to leave the hit to vanilla (not in a suit, armor-bypassing damage, or a
	 * broken piece, which leaves only the exoskeleton underneath).
	 */
	public static float absorb(LivingEntity entity, DamageSource source, float damage) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return -1.0f;
		}
		PowerArmorSuit suit = worn(player);
		if (suit == null) {
			return -1.0f;
		}
		PowerArmorPiece piece = pieceHit(player, player.yBodyRot, source);
		float protection = suit.stats().protection(piece) * Math.min(1.0f, ModDamageTypes.armorMultiplier(source));
		if (protection <= 0.0f) {
			return -1.0f;
		}
		ItemStack worn = PowerArmorPieceItem.wear(suit.piece(piece), Mth.ceil(damage));
		player.setAttached(PowerArmors.SUIT, suit.with(piece.slot(), worn));
		if (PowerArmorPieceItem.condition(worn) <= 0.0f) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
			player.sendOverlayMessage(Component.translatable("power_armor.mymod.piece_broken", piece.displayName()).withStyle(ChatFormatting.RED));
		}
		return damage * (1.0f - protection);
	}

	/**
	 * A wearer landed. No fall damage in a suit; past {@link #SHOCKWAVE_FALL} blocks the landing knocks back and
	 * hurts everything round them instead, harder the further they fell and the better their legs. Returns
	 * whether the fall was handled (the caller then deals no damage).
	 */
	public static boolean land(LivingEntity entity, double fallDistance) {
		if (!isWearing(entity)) {
			return false;
		}
		if (entity instanceof ServerPlayer player && fallDistance >= 1.5) {
			if (fallDistance >= SHOCKWAVE_FALL) {
				shockwave(player, fallDistance);
			} else {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS, 1.0f, 0.6f);
			}
		}
		return true;
	}

	private static void shockwave(ServerPlayer player, double fallDistance) {
		ServerLevel level = player.level();
		PowerArmorSuit suit = worn(player);
		float multiplier = suit == null ? 1.0f : suit.stats().shockwaveMultiplier();
		double strength = Math.min(fallDistance, 40.0);
		double radius = Math.min(2.5 + strength * 0.18, 8.0) * (0.85 + 0.15 * multiplier);
		float damage = (float) Math.min(strength * 0.5 * multiplier, 30.0);
		DamageSource source = player.damageSources().source(PowerArmors.SHOCKWAVE, player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 2.0, radius),
				target -> target != player && target.isAlive() && !target.isSpectator())) {
			double distance = target.distanceTo(player);
			if (distance > radius) {
				continue;
			}
			float falloff = (float) (1.0 - distance / radius * 0.6);
			target.hurtServer(level, source, damage * falloff);
			Vec3 away = target.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(0.0, 0.0, 0.0) : away.normalize();
			double push = (0.5 + strength * 0.03) * falloff;
			target.push(away.x * push, 0.3 + strength * 0.01 * falloff, away.z * push);
		}
		// Dust thrown out in a ring from the ground underfoot.
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 24; i++) {
				float angle = Mth.TWO_PI * i / 24.0f;
				double r = radius * 0.6;
				level.sendParticles(dust, player.getX() + Mth.cos(angle) * r, player.getY() + 0.1, player.getZ() + Mth.sin(angle) * r, 4, 0.3, 0.05, 0.3, 0.1);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 30, radius * 0.4, 0.1, radius * 0.4, 0.08);
		if (strength >= 10.0) {
			level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.3, player.getZ(), 3, 0.6, 0.1, 0.6, 0.0);
		}
		SoundEvent smash = strength >= 10.0 ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), smash, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
		// The legs take the strain.
		if (suit != null) {
			int wear = (int) Math.ceil(strength / 4.0);
			for (PowerArmorPiece leg : List.of(PowerArmorPiece.LEFT_LEG, PowerArmorPiece.RIGHT_LEG)) {
				suit = suit.with(leg.slot(), PowerArmorPieceItem.wear(suit.piece(leg), wear));
			}
			player.setAttached(PowerArmors.SUIT, suit);
		}
	}

	// ------------------------------------------------------------------------------------------------ every tick

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PowerArmorSuit suit = worn(player);
			if (suit == null) {
				if (STATES.remove(player.getUUID()) != null) {
					clearModifiers(player);
				}
				continue;
			}
			State state = STATES.computeIfAbsent(player.getUUID(), uuid -> fresh(player));
			if (state.sequence != PowerArmorMotion.NONE) {
				tickSequence(player, state);
				if (!isWearing(player)) {
					continue;
				}
			} else if (!hasEmptyFrame(player)) {
				eject(player, state, Component.translatable("power_armor.mymod.frame_removed"));
				continue;
			} else if (player.isSpectator()) {
				continue;
			} else {
				tickWorn(player, state, suit);
			}
			if (isWearing(player)) {
				applyModifiers(player, state);
				if (percent(state.fuel) != state.lastFlushedPercent) {
					flush(player, state);
				}
				sync(player, state);
			}
		}
	}

	private static void tickWorn(ServerPlayer player, State state, PowerArmorSuit suit) {
		PowerArmorStats stats = suit.stats();
		Vec3 position = player.position();
		if (state.lastPos != null && player.onGround()) {
			double moved = position.subtract(state.lastPos).horizontalDistance();
			if (moved > 0.02 && moved < 2.0) {
				drain(player, state, player.isSprinting() ? SPRINT_DRAIN : WALK_DRAIN);
			}
		}
		state.lastPos = position;

		PowerArmorFlight.tick(player, state, stats);
		Repulsors.tick(player, state, stats);

		// Life support: the helmet's tank keeps the wearer breathing underwater while it lasts, and refills in air.
		int airMax = stats.airTicks();
		if (player.isEyeInFluid(FluidTags.WATER)) {
			if (state.air > 0 && hasFuel(state)) {
				state.air--;
				player.setAirSupply(player.getMaxAirSupply());
				drain(player, state, LIFE_SUPPORT_DRAIN);
			}
		} else {
			state.air = Math.min(airMax, state.air + 10);
		}
		state.air = Math.min(state.air, airMax);

		float fraction = state.fuel / FusionCoreItem.MAX_CHARGE;
		if (fraction < LOW_FUEL && fraction > 0.0f && !state.warnedLowFuel) {
			state.warnedLowFuel = true;
			notify(player, SoundEvents.NOTE_BLOCK_BIT, 0.8f, 0.6f);
		} else if (fraction >= LOW_FUEL) {
			state.warnedLowFuel = false;
		}
	}

	private static void tickSequence(ServerPlayer player, State state) {
		ServerLevel level = player.level();
		state.sequenceTicks++;
		// Held in the frame while the plates move.
		if (player.position().distanceToSqr(state.anchor) > 0.04) {
			player.teleportTo(level, state.anchor.x, state.anchor.y, state.anchor.z, Set.of(), state.anchorYaw, player.getXRot(), false);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		Vec3 back = state.anchor.add(Vec3.directionFromRotation(0.0f, state.anchorYaw).scale(-0.45)).add(0.0, 1.2, 0.0);
		int t = state.sequenceTicks;
		boolean entering = state.sequence == PowerArmorMotion.ENTERING;
		if (entering) {
			if (t == 8 || t == 16) {
				level.playSound(null, back.x, back.y, back.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.9f, 0.6f + t * 0.01f);
			}
			if (t == 22) {
				level.playSound(null, back.x, back.y, back.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.7f);
			}
			if (t == 30) {
				level.playSound(null, back.x, back.y, back.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.5f);
				level.playSound(null, back.x, back.y, back.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 0.6f);
			}
			if (t == 36) {
				level.playSound(null, back.x, back.y, back.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
			}
		} else {
			if (t == 2) {
				level.playSound(null, back.x, back.y, back.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.8f);
			}
			if (t == 10) {
				level.playSound(null, back.x, back.y, back.z, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.9f, 0.6f);
			}
		}
		// Steam from the seams while the hydraulics work.
		if (t > 4 && t < (entering ? ENTER_TICKS : EXIT_TICKS) - 4 && t % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 2, 0.25, 0.4, 0.1, 0.01);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, back.x, back.y - 0.4, back.z, 1, 0.3, 0.3, 0.1, 0.01);
		}
		if (entering && t >= ENTER_TICKS) {
			state.sequence = PowerArmorMotion.NONE;
			state.lastPos = player.position();
			player.setAttached(PowerArmors.MOTION, PowerArmorMotion.IDLE);
			player.sendOverlayMessage(Component.translatable("power_armor.mymod.online").withStyle(ChatFormatting.GOLD));
		} else if (!entering && t >= EXIT_TICKS) {
			finishExiting(player, state);
		}
	}

	// ------------------------------------------------------------------------------------------------ attributes

	private static void applyModifiers(ServerPlayer player, State state) {
		boolean locked = state.sequence != PowerArmorMotion.NONE;
		boolean fuelled = hasFuel(state);
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, FRAME, 1.0, AttributeModifier.Operation.ADD_VALUE, true);
		setModifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, FRAME, 1.0, AttributeModifier.Operation.ADD_VALUE, true);
		setModifier(player, Attributes.STEP_HEIGHT, FRAME, 0.4, AttributeModifier.Operation.ADD_VALUE, true);
		setModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, FRAME, 1.0, AttributeModifier.Operation.ADD_VALUE, true);
		setModifier(player, Attributes.MOVEMENT_SPEED, FRAME, -WALK_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
		setModifier(player, Attributes.MOVEMENT_SPEED, SPRINT, SPRINT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
				fuelled && player.isSprinting());
		setModifier(player, Attributes.MOVEMENT_SPEED, EMPTY, -EMPTY_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, !fuelled);
		setModifier(player, Attributes.JUMP_STRENGTH, EMPTY, -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, !fuelled);
		setModifier(player, Attributes.MOVEMENT_SPEED, LOCK, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, locked);
		setModifier(player, Attributes.JUMP_STRENGTH, LOCK, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, locked);
		setModifier(player, Attributes.GRAVITY, THRUST, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, motion(player).flying());
	}

	private static void clearModifiers(ServerPlayer player) {
		for (Holder<Attribute> attribute : List.of(Attributes.KNOCKBACK_RESISTANCE, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, Attributes.STEP_HEIGHT,
				Attributes.WATER_MOVEMENT_EFFICIENCY, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.GRAVITY)) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null) {
				for (Identifier id : List.of(FRAME, SPRINT, EMPTY, LOCK, THRUST)) {
					instance.removeModifier(id);
				}
			}
		}
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation, boolean on) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		// Only when it changes: re-adding would mark the attribute dirty and resend it every tick.
		if (on && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, amount, operation));
		} else if (!on && instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	// ------------------------------------------------------------------------------------------------ the wearer's HUD

	/** Sends the wearer's HUD what changed, rounded so a steady drain doesn't send a packet every tick. */
	private static void sync(ServerPlayer player, State state) {
		PowerArmorSuit suit = worn(player);
		PowerArmorStats stats = suit == null ? null : suit.stats();
		int maxBurn = stats == null ? 1 : stats.maxBurnTicks();
		PowerArmorNetworking.Status status = new PowerArmorNetworking.Status(
				Math.round(state.fuel / 36.0f) * 36,
				Math.round(state.burn * 50.0f / maxBurn) / 50.0f,
				state.overheated,
				Mth.positiveCeilDiv(state.repulsorCooldown, 2) * 2, state.repulsorCooldownMax,
				Mth.positiveCeilDiv(state.unibeamCooldown, 5) * 5,
				state.unibeamCharge,
				Mth.positiveCeilDiv(state.air, 10) * 10, stats == null ? 0 : stats.airTicks());
		if (!status.equals(state.lastSent)) {
			state.lastSent = status;
			ServerPlayNetworking.send(player, status);
		}
	}
}
