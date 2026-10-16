package com.jheinz.tomyen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent M4_SHOT = register("m4_shot");
	public static final SoundEvent GLOCK_SHOT = register("glock_shot");
	public static final SoundEvent AKM_SHOT = register("akm_shot");
	public static final SoundEvent AWP_SHOT = register("awp_shot");
	public static final SoundEvent GALIL_SHOT = register("galil_shot");
	public static final SoundEvent MAGNUM_SHOT = register("magnum_shot");
	public static final SoundEvent MP5_SHOT = register("mp5_shot");
	public static final SoundEvent MAC10_SHOT = register("mac10_shot");
	public static final SoundEvent M1911_SHOT = register("m1911_shot");
	public static final SoundEvent RACK_BACK = register("rack_back");
	public static final SoundEvent RACK_FORWARD = register("rack_forward");
	public static final SoundEvent BOLT_UP = register("bolt_up");
	public static final SoundEvent BOLT_BACK = register("bolt_back");
	public static final SoundEvent BOLT_FORWARD = register("bolt_forward");
	public static final SoundEvent BOLT_DOWN = register("bolt_down");
	public static final SoundEvent MAG_OUT = register("mag_out");
	public static final SoundEvent MAG_IN = register("mag_in");
	// Pistols' own handling (rack_* and mag_* are the rifles' charging handle and magazine).
	public static final SoundEvent PISTOL_SLIDE_BACK = register("pistol_slide_back");
	public static final SoundEvent PISTOL_SLIDE_FORWARD = register("pistol_slide_forward");
	public static final SoundEvent PISTOL_MAG_OUT = register("pistol_mag_out");
	public static final SoundEvent PISTOL_MAG_IN = register("pistol_mag_in");
	// The .357's cylinder and hammer.
	public static final SoundEvent REVOLVER_CYLINDER_OPEN = register("revolver_cylinder_open");
	public static final SoundEvent REVOLVER_LOAD = register("revolver_load");
	public static final SoundEvent REVOLVER_CYLINDER_CLOSE = register("revolver_cylinder_close");
	public static final SoundEvent REVOLVER_SPIN = register("revolver_spin");
	public static final SoundEvent REVOLVER_HAMMER = register("revolver_hammer");
	public static final SoundEvent SHOPPING_CART_RATTLE = register("shopping_cart_rattle");
	public static final SoundEvent SHOPPING_CART_GROWL = register("shopping_cart_growl");
	public static final SoundEvent SHOPPING_CART_ROLL = register("shopping_cart_roll");
	public static final SoundEvent SHOPPING_CART_CHOMP = register("shopping_cart_chomp");
	public static final SoundEvent SHOPPING_CART_HURT = register("shopping_cart_hurt");
	public static final SoundEvent SHOPPING_CART_DEATH = register("shopping_cart_death");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = MyMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
	}
}
