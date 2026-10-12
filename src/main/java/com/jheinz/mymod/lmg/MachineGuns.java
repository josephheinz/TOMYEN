package com.jheinz.mymod.lmg;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import com.jheinz.mymod.MyMod;

/** The M249's sounds: its report, and the feed cover and belt handled during a reload. */
public final class MachineGuns {
	public static final SoundEvent M249_SHOT = sound("m249_shot");
	public static final SoundEvent FEED_COVER_OPEN = sound("feed_cover_open");
	public static final SoundEvent FEED_COVER_CLOSE = sound("feed_cover_close");
	public static final SoundEvent BELT_RATTLE = sound("belt_rattle");

	private MachineGuns() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		// Registration happens in the static initializer; this just forces the class to load.
	}
}
