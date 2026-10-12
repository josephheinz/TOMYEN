package com.jheinz.mymod.neuralink;

import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;

/**
 * Everything the Neuralink says over the network. The client only ever sends trigger presses; the server sends
 * each wearer what their modules see, and only what they need: senses are throttled and range-limited.
 */
public final class NeuralNetworking {
	private NeuralNetworking() {
	}

	/** The Neural Trigger was pressed or released, for the module the client has selected. */
	public record Trigger(int module, boolean pressed) implements CustomPacketPayload {
		public static final Type<Trigger> TYPE = new Type<>(MyMod.id("neural_trigger"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Trigger> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Trigger::module,
				ByteBufCodecs.BOOL, Trigger::pressed,
				Trigger::new);

		@Override
		public Type<Trigger> type() {
			return TYPE;
		}
	}

	/**
	 * The wearer's Neural Heat and actives, for the HUD.
	 *
	 * @param channel   ordinal of the module being channelled, or -1
	 * @param target    entity the channel is working on, or -1
	 * @param progress  how far along a Neural Hijack is, 0 to 1
	 * @param cooldowns ticks left per module, by ordinal
	 */
	public record Status(float heat, boolean overheated, int channel, int target, float progress, List<Integer> cooldowns)
			implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(MyMod.id("neural_status"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Status::heat,
				ByteBufCodecs.BOOL, Status::overheated,
				ByteBufCodecs.VAR_INT, Status::channel,
				ByteBufCodecs.VAR_INT, Status::target,
				ByteBufCodecs.FLOAT, Status::progress,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), Status::cooldowns,
				Status::new);

		@Override
		public Type<Status> type() {
			return TYPE;
		}
	}

	/** A player's optic lasers came on or went off; sent to everyone who can see them. */
	public record Beam(int playerId, boolean on) implements CustomPacketPayload {
		public static final Type<Beam> TYPE = new Type<>(MyMod.id("optic_beam"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Beam> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Beam::playerId,
				ByteBufCodecs.BOOL, Beam::on,
				Beam::new);

		@Override
		public Type<Beam> type() {
			return TYPE;
		}
	}

	/** A full-screen effect on the wearer's own screen. */
	public record Flash(int kind) implements CustomPacketPayload {
		public static final int BLINK = 0;
		/** Their Neuralink was knocked out by someone else's EMP. */
		public static final int EMP = 1;
		public static final int OVERHEAT = 2;
		public static final Type<Flash> TYPE = new Type<>(MyMod.id("neural_flash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flash> CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast()
				.map(Flash::new, Flash::kind);

		@Override
		public Type<Flash> type() {
			return TYPE;
		}
	}

	/** Smart-Link: one of the wearer's shots hit something. */
	public record HitMarker(boolean kill) implements CustomPacketPayload {
		public static final Type<HitMarker> TYPE = new Type<>(MyMod.id("hit_marker"));
		public static final StreamCodec<RegistryFriendlyByteBuf, HitMarker> CODEC = ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast()
				.map(HitMarker::new, HitMarker::kill);

		@Override
		public Type<HitMarker> type() {
			return TYPE;
		}
	}

	/** Mind Reader: what one mob is thinking. */
	public record Thought(int entityId, Component text) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Thought> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Thought::entityId,
				ComponentSerialization.STREAM_CODEC, Thought::text,
				Thought::new);
	}

	/** Mind Reader: the thoughts of the mobs around the wearer, replacing the last batch. */
	public record Thoughts(List<Thought> thoughts) implements CustomPacketPayload {
		public static final Type<Thoughts> TYPE = new Type<>(MyMod.id("mind_reader_thoughts"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Thoughts> CODEC = Thought.CODEC.apply(ByteBufCodecs.list(64))
				.map(Thoughts::new, Thoughts::thoughts);

		@Override
		public Type<Thoughts> type() {
			return TYPE;
		}
	}

	/** Bio-Monitor: the mobs currently hunting the wearer. */
	public record Threats(List<Integer> entityIds) implements CustomPacketPayload {
		public static final Type<Threats> TYPE = new Type<>(MyMod.id("bio_monitor_threats"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Threats> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64))
				.<RegistryFriendlyByteBuf>cast().map(Threats::new, Threats::entityIds);

		@Override
		public Type<Threats> type() {
			return TYPE;
		}
	}

	/** Hive Mind: something a teammate's ESP picked up. {@code kind} is 0 hostile, 1 passive, 2 player. */
	public record Contact(Vec3 pos, float width, float height, int kind) {
		public static final int HOSTILE = 0;
		public static final int PASSIVE = 1;
		public static final int PLAYER = 2;
		public static final StreamCodec<RegistryFriendlyByteBuf, Contact> CODEC = StreamCodec.composite(
				Vec3.STREAM_CODEC, Contact::pos,
				ByteBufCodecs.FLOAT, Contact::width,
				ByteBufCodecs.FLOAT, Contact::height,
				ByteBufCodecs.VAR_INT, Contact::kind,
				Contact::new);
	}

	/** Hive Mind: another wearer in range. */
	public record Mate(int entityId, String name, Vec3 pos) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Mate> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Mate::entityId,
				ByteBufCodecs.stringUtf8(64), Mate::name,
				Vec3.STREAM_CODEC, Mate::pos,
				Mate::new);
	}

	public record Hive(List<Mate> mates, List<Contact> contacts) implements CustomPacketPayload {
		public static final Type<Hive> TYPE = new Type<>(MyMod.id("hive_mind"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hive> CODEC = StreamCodec.composite(
				Mate.CODEC.apply(ByteBufCodecs.list(32)), Hive::mates,
				Contact.CODEC.apply(ByteBufCodecs.list(HiveMind.MAX_CONTACTS)), Hive::contacts,
				Hive::new);

		@Override
		public Type<Hive> type() {
			return TYPE;
		}
	}

	/** Recall: the wearer's last death and respawn point (the client isn't told the latter otherwise). */
	public record Recall(Optional<GlobalPos> death, Optional<GlobalPos> respawn) implements CustomPacketPayload {
		public static final Type<Recall> TYPE = new Type<>(MyMod.id("recall"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Recall> CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), Recall::death,
				ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), Recall::respawn,
				Recall::new);

		@Override
		public Type<Recall> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(Trigger.TYPE, Trigger.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Beam.TYPE, Beam.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Flash.TYPE, Flash.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HitMarker.TYPE, HitMarker.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Thoughts.TYPE, Thoughts.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Threats.TYPE, Threats.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Hive.TYPE, Hive.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Recall.TYPE, Recall.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Trigger.TYPE, (payload, context) -> {
			NeuralinkModule[] modules = NeuralinkModule.values();
			if (payload.module() >= 0 && payload.module() < modules.length) {
				NeuralCore.trigger(context.player(), modules[payload.module()], payload.pressed());
			}
		});
	}
}
