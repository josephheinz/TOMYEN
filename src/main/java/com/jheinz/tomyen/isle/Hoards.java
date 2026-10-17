package com.jheinz.tomyen.isle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;

import org.jspecify.annotations.Nullable;

import com.jheinz.tomyen.MyMod;

/**
 * Every player's unrecovered hoard, kept with the server's own saved data (a Fabric global attachment), so it lasts
 * across restarts whether or not anyone is on the Isle and no chunk has to be loaded to read it.
 */
public final class Hoards {
	private static final AttachmentType<Map<UUID, Hoard>> HOARDS = AttachmentRegistry.<Map<UUID, Hoard>>builder()
			.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Hoard.CODEC))
			.buildAndRegister(MyMod.id("isle_hoards"));

	private Hoards() {
	}

	static void initialize() {
	}

	public static Map<UUID, Hoard> all(MinecraftServer server) {
		Map<UUID, Hoard> hoards = server.globalAttachments().getAttached(HOARDS);
		return hoards == null ? Map.of() : hoards;
	}

	public static @Nullable Hoard get(MinecraftServer server, UUID owner) {
		return all(server).get(owner);
	}

	public static void put(MinecraftServer server, Hoard hoard) {
		Map<UUID, Hoard> hoards = new HashMap<>(all(server));
		hoards.put(hoard.owner(), hoard);
		server.globalAttachments().setAttached(HOARDS, Map.copyOf(hoards));
	}

	public static void remove(MinecraftServer server, UUID owner) {
		Map<UUID, Hoard> hoards = new HashMap<>(all(server));
		if (hoards.remove(owner) != null) {
			server.globalAttachments().setAttached(HOARDS, Map.copyOf(hoards));
		}
	}
}
