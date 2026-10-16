package com.jheinz.mymod.tvhelmet;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.cathode.CathodeWalkers;
import com.jheinz.mymod.exoskeleton.ExoSockets;

/**
 * The Cathode TV Helmet: a Cathode Walker's set worn as a helmet (drawn as a 3D television on the head by
 * TvHelmetRenderer, client side). Crafted from a Cathode Tube.
 *
 * <ul>
 * <li><b>Your face is the screen.</b> Other players see the emote you pick (the emote wheel: hold the Emote Wheel key
 * and point; tap it for the next), kept in the {@link #SCREEN} attachment, which is synced to everyone and saved. The
 * screen shows static while you're hurt, and colour bars once you've stood idle for {@value #IDLE_MILLIS} ms.
 * <li><b>Static night vision.</b> The TV Night Vision key switches it on and off with a click: night vision (no
 * particles, refreshed each second and dropped when you take the set off), and a faint scanline and static overlay
 * over your view.
 * <li><b>Heavy.</b> {@value #SPEED_PENALTY} slower.
 * <li><b>Not waterproof</b>, like the Cathode Walker it came out of. With its screen under water it shorts out: a zap
 * of short-circuit damage, a few seconds' blindness, and pure static (and no night vision) until it has dried out for
 * {@value #DRY_TICKS} ticks after you surface. Out in the rain the picture keeps breaking up into static, night vision
 * cuts out, and now and then a crackle blinds you for a moment.
 * </ul>
 * Everything works the same with the helmet socketed in an exoskeleton.
 */
public final class TvHelmets {
	/** Leather helmet's armor. */
	public static final double ARMOR = 1.0;
	/** Iron helmet's durability. */
	public static final int DURABILITY = ArmorType.HELMET.getDurability(15);
	public static final int ENCHANTABILITY = 10;
	/** Fraction of walking speed lost to the weight. */
	public static final double SPEED_PENALTY = 0.05;
	/** Night vision's duration, refreshed while it's on; over 200 so the screen never flickers as it runs low. */
	private static final int VISION_TICKS = 260;
	/** Ticks a shorted set takes to dry out once its screen is out of the water. */
	public static final int DRY_TICKS = 80;
	/** How long without input before the screen drifts to colour bars. */
	public static final long IDLE_MILLIS = 45_000L;
	private static final float SHORT_DAMAGE = 1.0f;
	private static final int SHORT_BLINDNESS = 50;
	/** In the rain, the odds each tick of a crackle that blinds for a moment. */
	private static final int RAIN_CRACKLE_ODDS = 240;

	public static final Item TV_HELMET = register("tv_helmet");

	/** On a player: their TV settings and what the screen is doing (see {@link TvScreen}). Synced to all, saved. */
	public static final AttachmentType<TvScreen> SCREEN = AttachmentRegistry.<TvScreen>builder()
			.persistent(TvScreen.CODEC)
			.copyOnDeath()
			.initializer(() -> TvScreen.DEFAULT)
			.syncWith(TvScreen.STREAM_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(MyMod.id("tv_screen"));

	/** On a player whose set shorted out: ticks left drying. Server only, not saved. */
	public static final AttachmentType<Integer> DRYING = AttachmentRegistry.<Integer>builder().buildAndRegister(MyMod.id("tv_drying"));

	/** The set clicking on and off (night vision, or coming back on after drying out). */
	public static final SoundEvent TOGGLE = sound("tv_helmet_toggle");
	/** A channel change: a new emote. */
	public static final SoundEvent CHANNEL = sound("tv_helmet_channel");

	private TvHelmets() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Identifier id = MyMod.id(name);
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.MOVEMENT_SPEED,
						new AttributeModifier(id, -SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.HEAD)
				.build();
		// No equipment asset: the set is drawn in code (TvHelmetRenderer), not as an armor texture layer.
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(1).durability(DURABILITY).enchantable(ENCHANTABILITY)
				.repairable(Items.COPPER_INGOT).attributes(attributes)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC).build());
		return Registry.register(BuiltInRegistries.ITEM, key, new TvHelmetItem(properties));
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Sent when the wearer picks an emote on the wheel (or taps the key for the next). */
	public record SelectEmote(int emote) implements CustomPacketPayload {
		public static final Type<SelectEmote> TYPE = new Type<>(MyMod.id("tv_select_emote"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectEmote> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, SelectEmote::emote, SelectEmote::new);

		@Override
		public Type<SelectEmote> type() {
			return TYPE;
		}
	}

	/** Sent when the wearer presses the TV Night Vision key. */
	public record ToggleNightVision() implements CustomPacketPayload {
		public static final ToggleNightVision INSTANCE = new ToggleNightVision();
		public static final Type<ToggleNightVision> TYPE = new Type<>(MyMod.id("tv_toggle_night_vision"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ToggleNightVision> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<ToggleNightVision> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(SelectEmote.TYPE, SelectEmote.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleNightVision.TYPE, ToggleNightVision.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectEmote.TYPE, (payload, context) -> selectEmote(context.player(), payload.emote()));
		ServerPlayNetworking.registerGlobalReceiver(ToggleNightVision.TYPE, (payload, context) -> toggleNightVision(context.player()));
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			for (ServerPlayer player : level.players()) {
				tick(player);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.TURTLE_HELMET, TV_HELMET));
	}

	/** Whether an entity has the set on: worn on its head, or socketed in the exoskeleton it wears. */
	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(TV_HELMET) || ExoSockets.hasSocketed(entity, TV_HELMET);
	}

	public static TvScreen screen(LivingEntity entity) {
		return entity.getAttachedOrElse(SCREEN, TvScreen.DEFAULT);
	}

	private static void selectEmote(ServerPlayer player, int id) {
		if (id < 0 || id >= TvEmote.count()) {
			return;
		}
		TvScreen screen = screen(player);
		if (screen.emote() == id) {
			return;
		}
		player.setAttached(SCREEN, screen.withEmote(TvEmote.byId(id)));
		if (isWearing(player)) {
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), CHANNEL, SoundSource.PLAYERS, 0.6f,
					0.9f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	private static void toggleNightVision(ServerPlayer player) {
		if (!isWearing(player)) {
			return;
		}
		TvScreen screen = screen(player);
		boolean on = !screen.nightVision();
		player.setAttached(SCREEN, screen.withNightVision(on));
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), TOGGLE, SoundSource.PLAYERS, 0.7f, on ? 1.2f : 0.8f);
		player.sendOverlayMessage(Component.translatable(on ? "tv_helmet.mymod.night_vision.on" : "tv_helmet.mymod.night_vision.off")
				.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		if (!on) {
			dropNightVision(player);
		}
	}

	/** Every tick, for every player: the screen's mode, shorting out, and night vision. */
	private static void tick(ServerPlayer player) {
		TvScreen screen = screen(player);
		if (!isWearing(player)) {
			dropNightVision(player);
			if (screen.mode() != TvScreen.NORMAL) {
				player.setAttached(SCREEN, screen.withMode(TvScreen.NORMAL));
			}
			if (player.getAttachedOrElse(DRYING, 0) > 0) {
				player.removeAttached(DRYING);
			}
			return;
		}
		ServerLevel level = player.level();
		boolean submerged = player.isEyeInFluid(FluidTags.WATER);
		int drying = player.getAttachedOrElse(DRYING, 0);
		int dryingNow = submerged ? DRY_TICKS : Math.max(0, drying - 1);
		if (dryingNow != drying) {
			player.setAttached(DRYING, dryingNow);
		}
		boolean rain = !submerged && level.isRainingAt(BlockPos.containing(player.getEyePosition()));
		int mode = dryingNow > 0 ? TvScreen.SHORTED
				: rain ? TvScreen.RAIN
				: Util.getMillis() - player.getLastActionTime() > IDLE_MILLIS ? TvScreen.IDLE
				: TvScreen.NORMAL;
		if (mode != screen.mode()) {
			if (mode == TvScreen.SHORTED) {
				shortOut(level, player);
			} else if (screen.mode() == TvScreen.SHORTED) {
				// Dried out: the set clicks back on.
				level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), TOGGLE, SoundSource.PLAYERS, 0.7f, 1.0f);
			}
			screen = screen.withMode(mode);
			player.setAttached(SCREEN, screen);
		}
		if (mode == TvScreen.RAIN && level.getRandom().nextInt(RAIN_CRACKLE_ODDS) == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 15, 0, true, false, false));
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), CathodeWalkers.SHORT, SoundSource.PLAYERS, 0.3f, 1.4f);
		}
		if (screen.seeing()) {
			MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
			if (vision == null || vision.isAmbient() && vision.getDuration() < VISION_TICKS - 20) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, VISION_TICKS, 0, true, false, true));
			}
		} else {
			dropNightVision(player);
		}
	}

	/** Water got in: a zap, a moment's blindness, sparks and a fizz. */
	private static void shortOut(ServerLevel level, ServerPlayer player) {
		dropNightVision(player);
		player.hurtServer(level, player.damageSources().source(CathodeWalkers.SHORT_CIRCUIT), SHORT_DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SHORT_BLINDNESS, 0, true, false, false));
		Vec3 eye = player.getEyePosition();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y + 0.2, eye.z, 14, 0.3, 0.3, 0.3, 0.2);
		level.sendParticles(ParticleTypes.BUBBLE, eye.x, eye.y, eye.z, 10, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, eye.x, eye.y, eye.z, CathodeWalkers.SHORT, SoundSource.PLAYERS, 1.0f, 1.0f);
		player.sendOverlayMessage(Component.translatable("tv_helmet.mymod.shorted").withStyle(ChatFormatting.YELLOW));
	}

	/** Takes away the set's own night vision (ambient, no longer than it gives), leaving any from a potion alone. */
	private static void dropNightVision(ServerPlayer player) {
		MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
		if (vision != null && vision.isAmbient() && vision.getDuration() <= VISION_TICKS) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}
}
