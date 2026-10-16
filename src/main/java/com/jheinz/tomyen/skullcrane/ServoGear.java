package com.jheinz.tomyen.skullcrane;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.exoskeleton.ExoSockets;

/**
 * Gear made from skullcrane parts, worn in an armor slot or socketed in an exoskeleton (which counts as worn):
 * <ul>
 * <li>The Optic Monocle (head): hold the zoom key to zoom in like a spyglass, and every creature in sight shows a
 * health bar over its head (both client side, see client/skullcrane/MonocleHud).
 * <li>The Servo Arm Backpack (chest): a spine pack with {@value #ARMS} long mechanical arms over the shoulders. They
 * reach {@value #BACKPACK_REACH} blocks further for blocks, and fetch dropped items from {@value #PICKUP_RANGE} blocks
 * away ({@value #OVERCLOCKED_RANGE} once it's been crafted with a Leader Core): each free arm picks an item, reaches
 * out to it for {@value #REACH_TICKS} ticks and grabs it, as if the player had walked over it. Which arm's after which
 * item is sent to everyone in sight ({@link Reach}), so the client can draw the arms reaching.
 * </ul>
 */
public final class ServoGear {
	public static final double BACKPACK_REACH = 2.0;
	public static final double PICKUP_RANGE = 4.5;
	public static final double OVERCLOCKED_RANGE = 8.0;
	public static final int ARMS = 4;
	public static final int REACH_TICKS = 8;
	/** How long an item it couldn't pick up (a full inventory, say) is left alone. */
	private static final int GIVE_UP_TICKS = 40;
	private static final String OVERCLOCKED = "overclocked";

	/** Server side: each backpack wearer's arms. */
	private static final Map<ServerPlayer, PackArms> WEARERS = new WeakHashMap<>();

	private ServoGear() {
	}

	/** Sent to the wearer and everyone tracking them: arm {@code arm} is now reaching for this item (or -1: none). */
	public record Reach(int playerId, int arm, int itemId) implements CustomPacketPayload {
		public static final Type<Reach> TYPE = new Type<>(MyMod.id("servo_reach"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reach> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Reach::playerId,
				ByteBufCodecs.VAR_INT, Reach::arm,
				ByteBufCodecs.VAR_INT, Reach::itemId,
				Reach::new);

		@Override
		public Type<Reach> type() {
			return TYPE;
		}
	}

	/** One wearer's arms: the item each is after (-1 for none) and for how long, and items given up on until when. */
	private static final class PackArms {
		final int[] items = new int[ARMS];
		final int[] ticks = new int[ARMS];
		final Int2LongMap ignored = new Int2LongOpenHashMap();

		PackArms() {
			Arrays.fill(items, -1);
		}

		boolean reachingFor(int id) {
			for (int item : items) {
				if (item == id) {
					return true;
				}
			}
			return false;
		}
	}

	static Item.Properties monocleProperties() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(DataComponents.EQUIPPABLE,
				Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_CHAIN).build())
				.component(DataComponents.LORE, lore("item.tomyen.optic_monocle.zoom", "item.tomyen.optic_monocle.health",
						"item.tomyen.servo_gear.socket"));
	}

	static Item.Properties backpackProperties() {
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.BLOCK_INTERACTION_RANGE, new AttributeModifier(MyMod.id("servo_arm_backpack"), BACKPACK_REACH,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.build();
		return new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).attributes(attributes).component(DataComponents.EQUIPPABLE,
				Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).build())
				.component(DataComponents.LORE, lore("item.tomyen.servo_arm_backpack.pickup", "item.tomyen.servo_gear.socket"));
	}

	static ItemLore lore(String... keys) {
		return new ItemLore(Arrays.stream(keys)
				.<Component>map(key -> Component.translatable(key).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)))
				.toList());
	}

	/**
	 * What something wears in a slot or has socketed in the exoskeleton it wears, if it's this item. Socketed, it's gear
	 * (see {@link ExoSockets#GEAR}), so it can sit alongside a helmet or a chestplate.
	 */
	private static ItemStack worn(LivingEntity entity, EquipmentSlot slot, Item item) {
		ItemStack stack = entity.getItemBySlot(slot);
		return stack.is(item) ? stack : ExoSockets.socketed(entity, item);
	}

	public static boolean wearsMonocle(LivingEntity entity) {
		return !worn(entity, EquipmentSlot.HEAD, Skullcranes.OPTIC_MONOCLE).isEmpty();
	}

	public static boolean wearsBackpack(LivingEntity entity) {
		return !worn(entity, EquipmentSlot.CHEST, Skullcranes.SERVO_ARM_BACKPACK).isEmpty();
	}

	/** How far off the backpack someone wears fetches items from: further once it's been overclocked. */
	public static double pickupRange(LivingEntity entity) {
		ItemStack pack = worn(entity, EquipmentSlot.CHEST, Skullcranes.SERVO_ARM_BACKPACK);
		boolean overclocked = pack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(OVERCLOCKED, false);
		return overclocked ? OVERCLOCKED_RANGE : PICKUP_RANGE;
	}

	static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(Reach.TYPE, Reach.CODEC);
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			for (ServerPlayer player : level.players()) {
				tickArms(level, player);
			}
		});
	}

	private static void tickArms(ServerLevel level, ServerPlayer player) {
		PackArms arms = WEARERS.get(player);
		if (!player.isAlive() || player.isSpectator() || !wearsBackpack(player)) {
			if (arms != null) {
				for (int i = 0; i < ARMS; i++) {
					release(player, arms, i);
				}
				WEARERS.remove(player);
			}
			return;
		}
		if (arms == null) {
			arms = new PackArms();
			WEARERS.put(player, arms);
		}
		long now = level.getGameTime();
		arms.ignored.int2LongEntrySet().removeIf(entry -> entry.getLongValue() < now);
		double range = pickupRange(player);
		// Arms already reaching: grab once they're there, give up if the item's gone or out of reach.
		for (int i = 0; i < ARMS; i++) {
			if (arms.items[i] < 0) {
				continue;
			}
			Entity entity = level.getEntity(arms.items[i]);
			if (!(entity instanceof ItemEntity item) || !item.isAlive() || item.distanceToSqr(player) > (range + 3.0) * (range + 3.0)) {
				release(player, arms, i);
			} else if (++arms.ticks[i] >= REACH_TICKS) {
				item.playerTouch(player);
				if (item.isAlive()) {
					arms.ignored.put(item.getId(), now + GIVE_UP_TICKS);
				} else {
					level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), Skullcranes.CLAMP, SoundSource.PLAYERS, 0.25f, 1.8f);
				}
				release(player, arms, i);
			}
		}
		// Free arms pick the nearest items not already being fetched, leaving the ones close enough to walk over.
		if (level.getGameTime() % 2 != 0) {
			return;
		}
		AABB touch = player.getBoundingBox().inflate(1.0, 0.5, 1.0);
		PackArms wearer = arms;
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(range, 2.0, range),
				item -> item.isAlive() && !item.hasPickUpDelay() && !item.getBoundingBox().intersects(touch) && !wearer.reachingFor(item.getId())
						&& !wearer.ignored.containsKey(item.getId()));
		items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(player)));
		for (ItemEntity item : items) {
			int arm = pickArm(player, arms, item.position());
			if (arm < 0) {
				break;
			}
			arms.items[arm] = item.getId();
			arms.ticks[arm] = 0;
			send(player, new Reach(player.getId(), arm, item.getId()));
			level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), Skullcranes.AMBIENT, SoundSource.PLAYERS, 0.2f, 1.7f);
		}
	}

	/**
	 * The free arm best placed for a point: the upper pair (0 right, 1 left) for things about head height, the lower pair
	 * (2 right, 3 left) for things lower down, on the side it's on. Any free arm if that one's busy, or -1 if none is.
	 */
	private static int pickArm(ServerPlayer player, PackArms arms, Vec3 at) {
		Vec3 to = at.subtract(player.position());
		Vec3 right = Vec3.directionFromRotation(0.0f, player.yBodyRot + 90.0f);
		int side = to.dot(right) >= 0.0 ? 0 : 1;
		int preferred = side + (to.y < 0.8 ? 2 : 0);
		if (arms.items[preferred] < 0) {
			return preferred;
		}
		for (int i = 0; i < ARMS; i++) {
			if (arms.items[(preferred + i) % ARMS] < 0) {
				return (preferred + i) % ARMS;
			}
		}
		return -1;
	}

	private static void release(ServerPlayer player, PackArms arms, int arm) {
		if (arms.items[arm] >= 0) {
			arms.items[arm] = -1;
			send(player, new Reach(player.getId(), arm, -1));
		}
	}

	private static void send(ServerPlayer player, Reach reach) {
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, reach);
		}
		ServerPlayNetworking.send(player, reach);
	}
}
