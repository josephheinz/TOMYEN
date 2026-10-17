package com.jheinz.tomyen.meteorite;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.DamageTypeTags;

import com.jheinz.tomyen.GearNightVision;
import com.jheinz.tomyen.MyMod;

/**
 * Star Steel: meteoric iron alloyed with star crystal, a match for netherite that comes into its own at night.
 * <p>
 * Tools: netherite's mining level, speed and damage, with more durability (2200 against 2031) and enchantability
 * (18 against 15); by night they mine {@link #NIGHT_MINING_SPEED} times as fast (PlayerStarSteelMixin).
 * <p>
 * Armor: netherite's defence, toughness and knockback resistance, a little more durability and enchantability. A
 * full set gives Night Vision while worn and a faint shimmer of glowing motes, and by night the wearer takes
 * {@link #NIGHT_DAMAGE_TAKEN} of the damage they would (LivingEntityStarSteelMixin). Damage that ignores armor
 * invulnerability (the void, /kill) isn't softened. Both are fire resistant, like netherite.
 */
public final class StarSteel {
	/** Every Star Steel tool (data/tomyen/tags/item/star_steel_tools.json). */
	public static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, MyMod.id("star_steel_tools"));
	/** What repairs Star Steel: its ingot. */
	public static final TagKey<Item> REPAIR_MATERIALS = TagKey.create(Registries.ITEM, MyMod.id("star_steel_tool_materials"));

	public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2200, 9.0f, 4.0f, 18, REPAIR_MATERIALS);
	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(40, ArmorMaterials.makeDefense(3, 6, 8, 3, 19), 18,
			SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f, REPAIR_MATERIALS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id("star_steel")));

	public static final float NIGHT_MINING_SPEED = 1.5f;
	public static final float NIGHT_DAMAGE_TAKEN = 0.8f;

	private static final int EFFECT_INTERVAL = 10;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private StarSteel() {
	}

	public static boolean isNight(LivingEntity entity) {
		return entity.level().isDarkOutside();
	}

	public static boolean wearingFullSet(LivingEntity entity) {
		for (EquipmentSlot slot : ARMOR) {
			if (!entity.getItemBySlot(slot).is(armorFor(slot))) {
				return false;
			}
		}
		return true;
	}

	private static Item armorFor(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> Meteorites.STAR_STEEL_HELMET;
			case CHEST -> Meteorites.STAR_STEEL_CHESTPLATE;
			case LEGS -> Meteorites.STAR_STEEL_LEGGINGS;
			default -> Meteorites.STAR_STEEL_BOOTS;
		};
	}

	/** The player's mining speed on this block, quickened if it's night and they're using a Star Steel tool on it. */
	public static float destroySpeed(Player player, BlockState state, float speed) {
		ItemStack tool = player.getMainHandItem();
		if (tool.is(TOOLS) && tool.getDestroySpeed(state) > 1.0f && isNight(player)) {
			return speed * NIGHT_MINING_SPEED;
		}
		return speed;
	}

	/** Damage after a full set's night-time resistance. */
	public static float damageTaken(LivingEntity entity, DamageSource source, float damage) {
		if (damage > 0.0f && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && isNight(entity) && wearingFullSet(entity)) {
			return damage * NIGHT_DAMAGE_TAKEN;
		}
		return damage;
	}

	public static void initialize() {
		GearNightVision.source(StarSteel::wearingFullSet, true);
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EFFECT_INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				tick(level, player);
			}
		});
	}

	private static void tick(ServerLevel level, ServerPlayer player) {
		if (!wearingFullSet(player)) {
			return;
		}
		if (!player.isInvisible()) {
			level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 1,
					player.getBbWidth() * 0.6, player.getBbHeight() * 0.35, player.getBbWidth() * 0.6, 0.0);
		}
	}
}
