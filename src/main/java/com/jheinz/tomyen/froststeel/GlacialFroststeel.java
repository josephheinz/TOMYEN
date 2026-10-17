package com.jheinz.tomyen.froststeel;

import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.exoskeleton.ExoSockets;
import com.jheinz.tomyen.metal.HintItem;
import com.jheinz.tomyen.snowwarden.SnowWarden;
import com.jheinz.tomyen.snowwarden.SnowWardens;

/**
 * Glacial Froststeel: froststeel armor hardened with a Snow Warden's Frozen Heart at a smithing table (a froststeel
 * piece and the heart, no template). Netherite's protection and toughness, a little knockback resistance, and it's
 * still froststeel: each piece counts toward froststeel's freeze resistance and full-set bonus (see
 * {@link Froststeel#piecesWorn}), so it can be mixed with plain froststeel.
 *
 * <p>And it bites back: whoever hits the wearer in melee is slowed and frosted over, more for each glacial piece worn
 * (Slowness II and enough frost to freeze at once with all four).
 */
public final class GlacialFroststeel {
	public static final String NAME = "glacial_froststeel";
	/** Frost a melee attacker takes per glacial piece the wearer has on. */
	private static final int THORNS_CHILL_PER_PIECE = 35;
	private static final int THORNS_SLOW_TICKS = 30;
	private static final int THORNS_SLOW_PER_PIECE = 15;

	public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(35,
			Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
			15, SoundEvents.ARMOR_EQUIP_DIAMOND, 3.0f, 0.05f, Froststeel.REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, MyMod.id(NAME)));

	private static final List<String> HINTS = List.of("item.tomyen.glacial_froststeel.counts", "item.tomyen.froststeel.armor_piece",
			"item.tomyen.froststeel.armor_set", "item.tomyen.glacial_froststeel.thorns");

	public static final Item HELMET = armor("helmet", ArmorType.HELMET);
	public static final Item CHESTPLATE = armor("chestplate", ArmorType.CHESTPLATE);
	public static final Item LEGGINGS = armor("leggings", ArmorType.LEGGINGS);
	public static final Item BOOTS = armor("boots", ArmorType.BOOTS);
	/** In slot order: head, chest, legs, feet (as {@link Froststeel#SLOTS}). */
	public static final List<Item> ARMOR = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

	private GlacialFroststeel() {
	}

	private static Item armor(String piece, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(NAME + "_" + piece));
		return Registry.register(BuiltInRegistries.ITEM, key, new HintItem(new Item.Properties().humanoidArmor(ARMOR_MATERIAL, type)
				.rarity(Rarity.UNCOMMON).setId(key), HINTS));
	}

	/** How many glacial pieces the entity wears, in their slots or socketed in a worn exoskeleton. */
	public static int piecesWorn(LivingEntity entity) {
		List<ItemStack> socketed = ExoSockets.socketedArmor(entity);
		int worn = 0;
		for (int i = 0; i < ARMOR.size(); i++) {
			Item piece = ARMOR.get(i);
			if (entity.getItemBySlot(Froststeel.SLOTS.get(i)).is(piece) || socketed.stream().anyMatch(stack -> stack.is(piece))) {
				worn++;
			}
		}
		return worn;
	}

	/** Frost thorns: a melee attacker is slowed and frosted over, the more so for each glacial piece. */
	private static void frostThorns(LivingEntity wearer, DamageSource source, float damage) {
		if (damage <= 0.0f || source.is(DamageTypeTags.IS_PROJECTILE) || !(source.getEntity() instanceof LivingEntity attacker)
				|| source.getDirectEntity() != attacker || attacker == wearer || !(wearer.level() instanceof ServerLevel level)) {
			return;
		}
		int pieces = piecesWorn(wearer);
		if (pieces == 0) {
			return;
		}
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, THORNS_SLOW_TICKS + THORNS_SLOW_PER_PIECE * pieces, pieces >= 4 ? 1 : 0),
				wearer);
		SnowWarden.chill(attacker, THORNS_CHILL_PER_PIECE * pieces);
		level.sendParticles(ParticleTypes.SNOWFLAKE, attacker.getX(), attacker.getY(0.6), attacker.getZ(), 4 + 3 * pieces, 0.3, 0.4, 0.3,
				0.03);
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SnowWardens.FROST_THORNS, SoundSource.PLAYERS,
				0.6f + 0.1f * pieces, 1.0f + level.getRandom().nextFloat() * 0.2f);
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!blocked) {
				frostThorns(entity, source, damage);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
				.register(output -> output.insertAfter(Froststeel.SET.boots, HELMET, CHESTPLATE, LEGGINGS, BOOTS));
	}
}
