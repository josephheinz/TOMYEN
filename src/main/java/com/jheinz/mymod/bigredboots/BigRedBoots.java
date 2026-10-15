package com.jheinz.mymod.bigredboots;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.MyMod;
import com.jheinz.mymod.exoskeleton.ExoSockets;
import com.jheinz.mymod.industry.Industry;

/**
 * Big Red Boots: comically oversized, glossy red rubber boots. Leather's armor, iron boots' durability, repaired with
 * rubber. Drawn as their own big 3D boot model on the feet (BigRedBootsRenderer, client side), worn or socketed in an
 * exoskeleton.
 *
 * <p>The big rubber feet soak up half of all fall damage, and a landing from {@link #STOMP_FALL} blocks or more
 * stomps: a little shockwave that shoves nearby mobs away and stings them (see {@link #land}). The wearer waddles
 * ({@link #SPEED_PENALTY} slower), never tramples farmland, and squeaks now and then as they walk ({@link #step}).
 * Everything here works the same with the boots socketed in an exoskeleton: the attributes carry over with the suit's
 * (ExoSockets.forEachModifier) and {@link #isWearing} looks in the sockets.
 */
public final class BigRedBoots {
	/** Leather boots' armor. */
	public static final double ARMOR = 1.0;
	/** Iron boots' durability: iron's multiplier (15) for boots. */
	public static final int DURABILITY = ArmorType.BOOTS.getDurability(15);
	/** Leather's enchantability. */
	public static final int ENCHANTABILITY = 15;
	/** Fraction of fall damage soaked up. */
	public static final double FALL_REDUCTION = 0.5;
	/** Fraction of walking speed lost to the waddle. */
	public static final double SPEED_PENALTY = 0.05;
	/** How far a fall has to be (in blocks) for the landing to stomp. */
	public static final double STOMP_FALL = 4.0;
	/** One step in this many squeaks. */
	private static final int SQUEAK_ODDS = 4;

	public static final Item BOOTS = register("big_red_boots");

	private BigRedBoots() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		Identifier id = MyMod.id(name);
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(id, ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
				.add(Attributes.FALL_DAMAGE_MULTIPLIER,
						new AttributeModifier(id, -FALL_REDUCTION, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.FEET)
				.add(Attributes.MOVEMENT_SPEED,
						new AttributeModifier(id, -SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.FEET)
				.build();
		// No equipment asset: the boots are drawn in code (BigRedBootsRenderer), not as an armor texture layer.
		Item.Properties properties = new Item.Properties().setId(key).stacksTo(1).durability(DURABILITY).enchantable(ENCHANTABILITY)
				.repairable(Industry.RUBBER).attributes(attributes)
				.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build());
		return Registry.register(BuiltInRegistries.ITEM, key, new BigRedBootsItem(properties));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.LEATHER_BOOTS, BOOTS));
	}

	/** Whether an entity has the boots on: worn on its feet, or socketed in the exoskeleton it wears. */
	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.FEET).is(BOOTS) || ExoSockets.hasSocketed(entity, BOOTS);
	}

	/** Whether an entity is wearing the boots (so it doesn't trample farmland). */
	public static boolean isWearing(Entity entity) {
		return entity instanceof LivingEntity living && isWearing(living);
	}

	/**
	 * A landing (vanilla's fall damage; acts on the server only): from {@link #STOMP_FALL} blocks or more in the boots, a stomp. A
	 * ring of dust and a rubbery thud, and every mob within a couple of blocks is shoved away and stung a little,
	 * more the further the fall (up to a cap), less the further from the wearer. Not the wearer's own pets or allies.
	 */
	public static void land(LivingEntity entity, double fallDistance) {
		if (fallDistance < STOMP_FALL || !(entity.level() instanceof ServerLevel level) || !isWearing(entity) || entity.isSpectator()) {
			return;
		}
		double strength = Math.min(fallDistance - STOMP_FALL, 12.0);
		double radius = 2.5 + strength * 0.1;
		float damage = (float) (2.0 + strength * 0.25);
		DamageSource source = entity instanceof Player player ? entity.damageSources().playerAttack(player) : entity.damageSources().mobAttack(entity);
		for (Mob target : level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(radius, 1.5, radius),
				target -> target.isAlive() && !target.isAlliedTo(entity) && !(target instanceof OwnableEntity pet && pet.getOwner() == entity)
						&& target != entity.getVehicle())) {
			double distance = target.distanceTo(entity);
			if (distance > radius) {
				continue;
			}
			float falloff = (float) (1.0 - distance / radius * 0.5);
			target.hurtServer(level, source, damage * falloff);
			Vec3 away = target.position().subtract(entity.position()).horizontal();
			away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
			double push = (0.6 + strength * 0.04) * falloff;
			target.push(away.x * push, 0.25 + 0.1 * falloff, away.z * push);
		}
		// Dust kicked out in a ring from the ground underfoot, and a puff.
		BlockState ground = level.getBlockState(entity.getOnPos());
		if (!ground.isAir()) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 16; i++) {
				float angle = Mth.TWO_PI * i / 16.0f;
				double r = radius * 0.5;
				level.sendParticles(dust, entity.getX() + Mth.cos(angle) * r, entity.getY() + 0.1, entity.getZ() + Mth.sin(angle) * r, 3, 0.2, 0.05,
						0.2, 0.1);
			}
		}
		level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.1, entity.getZ(), 12, radius * 0.3, 0.05, radius * 0.3, 0.05);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_BLOCK_FALL, entity.getSoundSource(), 1.2f, 0.6f);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_SQUISH, entity.getSoundSource(), 0.9f, 1.6f);
	}

	/**
	 * A footstep (Entity.walkingStepSound, on whichever side moves the entity): now and then the rubber squeaks, a
	 * slime squish or a honey step pitched up. Not while sneaking.
	 */
	public static void step(Entity entity) {
		if (!(entity instanceof LivingEntity living) || living.isCrouching() || !isWearing(living)
				|| living.getRandom().nextInt(SQUEAK_ODDS) != 0) {
			return;
		}
		float pitch = 1.5f + living.getRandom().nextFloat() * 0.4f;
		living.playSound(living.getRandom().nextBoolean() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.HONEY_BLOCK_STEP, 0.35f, pitch);
	}
}
