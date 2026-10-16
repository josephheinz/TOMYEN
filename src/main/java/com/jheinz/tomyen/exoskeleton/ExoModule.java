package com.jheinz.tomyen.exoskeleton;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.jheinz.tomyen.MyMod;

/**
 * What a module installed in a worn exoskeleton does. Each module works once per suit. So far every module is
 * a passive set of attribute bonuses, applied while the suit is worn (see {@link ExoskeletonEffects}).
 */
public enum ExoModule {
	/** Extra armor plates bolted onto the frame: +4 armor, +2 toughness. */
	REINFORCED_PLATING("reinforced_plating", 0xB0BEC5,
			new Bonus(Attributes.ARMOR, 4.0),
			new Bonus(Attributes.ARMOR_TOUGHNESS, 2.0)),
	/** Powered knee and ankle servos: steps up full blocks, jumps higher and lands harder falls. */
	SERVO_LEGS("servo_legs", 0xFFB300,
			new Bonus(Attributes.STEP_HEIGHT, 0.5),
			new Bonus(Attributes.JUMP_STRENGTH, 0.1),
			new Bonus(Attributes.SAFE_FALL_DISTANCE, 3.0)),
	/** Werewolf hide padding the frame's joints: some of the beast's pace and strength. +10% speed, +1 attack damage. */
	FERAL_LINING("feral_lining", 0xB07A4A,
			new Bonus(Attributes.MOVEMENT_SPEED, 0.01),
			new Bonus(Attributes.ATTACK_DAMAGE, 1.0));

	/** A flat amount added to one of the wearer's attributes. */
	public record Bonus(Holder<Attribute> attribute, double amount) {
	}

	private final String id;
	private final int color;
	private final List<Bonus> bonuses;
	private final Identifier modifierId;

	ExoModule(String id, int color, Bonus... bonuses) {
		this.id = id;
		this.color = color;
		this.bonuses = List.of(bonuses);
		this.modifierId = MyMod.id("exo_module." + name().toLowerCase(Locale.ROOT));
	}

	public String id() {
		return id;
	}

	/** The module's accent colour (RGB) in tooltips and the interface. */
	public int color() {
		return color;
	}

	public List<Bonus> bonuses() {
		return bonuses;
	}

	/** The module's attribute modifiers, one per bonus; they share an id, each on its own attribute. */
	public AttributeModifier modifier(Bonus bonus) {
		return new AttributeModifier(modifierId, bonus.amount(), AttributeModifier.Operation.ADD_VALUE);
	}

	public Identifier modifierId() {
		return modifierId;
	}

	public Component displayName() {
		return Component.translatable("exo_module.tomyen." + id);
	}

	public Component description() {
		return Component.translatable("exo_module.tomyen." + id + ".description");
	}
}
