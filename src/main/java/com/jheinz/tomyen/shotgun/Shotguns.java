package com.jheinz.tomyen.shotgun;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

import com.jheinz.tomyen.MyMod;

/**
 * The shotguns' shared pieces: the action state they keep on their stacks, their sounds, and the server tick that
 * pushes shells into a tube while loading. A shotgun that leaves the hand mid-load stops loading (shells already in
 * stay in), so loading never carries on in the background.
 */
public final class Shotguns {
	/** On a shotgun: what its action is doing (see {@link ShotgunAction}). Not saved: loading stops on logging out. */
	public static final DataComponentType<ShotgunAction> SHOTGUN_ACTION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MyMod.id("shotgun_action"),
			DataComponentType.<ShotgunAction>builder().networkSynchronized(ShotgunAction.STREAM_CODEC).build());

	public static final SoundEvent REMINGTON_870_SHOT = sound("remington_870_shot");
	public static final SoundEvent SPAS12_SHOT = sound("spas12_shot");
	public static final SoundEvent PUMP_BACK = sound("pump_back");
	public static final SoundEvent PUMP_FORWARD = sound("pump_forward");
	public static final SoundEvent SHELL_INSERT = sound("shell_insert");

	private record Held(ItemStack stack, int slot) {
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private Shotguns() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(Shotguns::tick));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HELD.remove(handler.getPlayer().getUUID()));
	}

	private static void tick(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		int slot = player.getInventory().getSelectedSlot();
		Held last = HELD.get(player.getUUID());
		boolean same = last != null && last.stack() == stack && last.slot() == slot;
		if (last != null && !same && ShotgunItem.loading(last.stack())) {
			last.stack().remove(SHOTGUN_ACTION);
		}
		if (!(stack.getItem() instanceof ShotgunItem shotgun)) {
			HELD.remove(player.getUUID());
			return;
		}
		HELD.put(player.getUUID(), new Held(stack, slot));
		ShotgunAction action = stack.get(SHOTGUN_ACTION);
		long now = player.level().getGameTime();
		if (action != null && action.kind() == ShotgunAction.Kind.LOAD && now >= action.end()) {
			shotgun.loadShell(player, stack, action, now);
		}
	}
}
