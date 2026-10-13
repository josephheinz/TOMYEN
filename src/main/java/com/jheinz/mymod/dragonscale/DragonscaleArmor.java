package com.jheinz.mymod.dragonscale;

import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.exoskeleton.ExoskeletonItem;

/**
 * What the dragonscale pieces do when worn, in an armor slot or socketed in a worn exoskeleton (the two count
 * the same everywhere here).
 * <ul>
 * <li>Each piece: {@link #FIRE_PROTECTION_PER_PIECE} protection points against fire, like a level of Fire
 * Protection (8% less fire damage), added in EnchantmentHelperDragonscaleMixin.</li>
 * <li>The full set: immune to fire and lava, as if the wearer were a fire-immune mob (EntityDragonscaleFireMixin):
 * they don't burn and fire, lava and magma blocks don't hurt them.</li>
 * <li>The full set while gliding: the jump key flaps the wings for an upward boost ({@link #flap}), at most once
 * every {@link #FLAP_COOLDOWN} ticks and {@link #FLAPS} times before landing again. The client asks for each flap
 * with a {@link Flap} packet, checked here.</li>
 * </ul>
 * Gliding itself is the chestplate's alone (it carries the glider component).
 */
public final class DragonscaleArmor {
	public static final float FIRE_PROTECTION_PER_PIECE = 2.0f;
	public static final int FLAPS = 3;
	/** 1.5 s between flaps. */
	public static final int FLAP_COOLDOWN = 30;
	/** Upward speed a flap adds (blocks a tick), and how fast it may leave you climbing at most. */
	private static final double FLAP_LIFT = 0.55;
	private static final double FLAP_MAX_CLIMB = 0.8;
	/** A little push the way you're looking, so a flap doesn't stall the glide. */
	private static final double FLAP_PUSH = 0.25;

	/** Flaps used since last on the ground, and when the last one was, per player (server only). */
	private static final Map<ServerPlayer, long[]> FLAPPING = new WeakHashMap<>();

	/** Sent when the player presses jump while gliding in the full set. */
	public record Flap() implements CustomPacketPayload {
		public static final Flap INSTANCE = new Flap();
		public static final Type<Flap> TYPE = new Type<>(MyMod.id("dragonscale_flap"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flap> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Flap> type() {
			return TYPE;
		}
	}

	private DragonscaleArmor() {
	}

	static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(Flap.TYPE, Flap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Flap.TYPE, (payload, context) -> flap(context.player()));
		// Landing (or swimming) rests the wings.
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			for (ServerPlayer player : level.players()) {
				if (player.onGround() || player.isInWater() || !player.isFallFlying() && player.getAbilities().flying) {
					FLAPPING.remove(player);
				}
			}
		});
	}

	/** The dragonscale piece made for a slot, or null for a slot without one. */
	private static Item pieceFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> Dragonscales.HELMET;
			case CHEST -> Dragonscales.CHESTPLATE;
			case LEGS -> Dragonscales.LEGGINGS;
			case FEET -> Dragonscales.BOOTS;
			default -> null;
		};
	}

	/** The dragonscale piece an entity wears in a slot, in the slot itself or socketed in its exoskeleton; or empty. */
	public static ItemStack worn(LivingEntity entity, EquipmentSlot slot) {
		Item piece = pieceFor(slot);
		if (piece == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = entity.getItemBySlot(slot);
		if (stack.is(piece)) {
			return stack;
		}
		ItemStack socketed = ExoSockets.worn(entity, slot);
		return socketed.is(piece) ? socketed : ItemStack.EMPTY;
	}

	/** How many dragonscale pieces an entity wears, worn or socketed. Asked every tick, so it reads the sockets once. */
	public static int pieces(LivingEntity entity) {
		ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
		int count = 0;
		if (chest.getItem() instanceof ExoskeletonItem) {
			// The suit fills every armor slot, so all the armor is in its sockets.
			for (ItemStack stack : ExoSockets.contents(chest)) {
				EquipmentSlot slot = ExoSockets.armorSlot(stack);
				if (slot != null && stack.is(pieceFor(slot))) {
					count++;
				}
			}
			return count;
		}
		for (EquipmentSlot slot : ExoSockets.ARMOR_SLOTS) {
			if (entity.getItemBySlot(slot).is(pieceFor(slot))) {
				count++;
			}
		}
		return count;
	}

	public static boolean hasFullSet(LivingEntity entity) {
		return pieces(entity) == ExoSockets.ARMOR_SLOTS.size();
	}

	/** Protection points the pieces add against a hit: only fire. */
	public static float fireProtection(LivingEntity entity, DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE) ? pieces(entity) * FIRE_PROTECTION_PER_PIECE : 0.0f;
	}

	/**
	 * A flap, asked for by the client when the jump key is pressed while gliding. The server sets the new motion and
	 * sends it back to the player and those watching, as a mace's impulse is.
	 */
	public static void flap(ServerPlayer player) {
		if (!player.isFallFlying() || !hasFullSet(player)) {
			return;
		}
		ServerLevel level = player.level();
		long[] state = FLAPPING.computeIfAbsent(player, p -> new long[] {0, Long.MIN_VALUE / 2});
		long now = level.getGameTime();
		if (state[0] >= FLAPS || now - state[1] < FLAP_COOLDOWN) {
			return;
		}
		state[0]++;
		state[1] = now;
		Vec3 look = player.getLookAngle();
		Vec3 motion = player.getDeltaMovement();
		double climb = Math.min(Math.max(motion.y, 0.0) + FLAP_LIFT, FLAP_MAX_CLIMB);
		player.setDeltaMovement(motion.x + look.x * FLAP_PUSH, climb, motion.z + look.z * FLAP_PUSH);
		player.syncVelocity = true;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), Dragonscales.WING_FLAP, SoundSource.PLAYERS, 1.0f,
				0.9f + player.getRandom().nextFloat() * 0.2f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.8, player.getZ(), 8, 0.6, 0.1, 0.6, 0.02);
		player.sendOverlayMessage(Component.translatable("message.mymod.dragonscale.flaps", FLAPS - state[0], FLAPS));
	}
}
