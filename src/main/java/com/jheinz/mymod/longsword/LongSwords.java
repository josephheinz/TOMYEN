package com.jheinz.mymod.longsword;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import com.jheinz.mymod.MyMod;

/**
 * Long swords: three swords of a material crafted on the diagonal. Each hits 3 harder than its material's sword, swings
 * slower (1.2 attacks a second against 1.6), reaches a block further and lasts half as long again. They're in
 * {@code minecraft:swords}, so they sweep, mine cobwebs and take every sword enchantment; their sweeps reach a little
 * wider.
 *
 * <p>A long sword in the main hand takes both hands: whatever is in the off hand can't be used (no raising a shield,
 * drawing a bow, eating or placing blocks with it), and an off-hand use already under way when the long sword comes
 * out is dropped (PlayerLongSwordMixin, which also widens the sweep). The off hand can still hold things.
 */
public final class LongSwords {
	/** Every long sword (data/mymod/tags/item/long_swords.json). */
	public static final TagKey<Item> LONG_SWORDS = TagKey.create(Registries.ITEM, MyMod.id("long_swords"));

	// Vanilla swords: 3 attack damage plus the material's bonus (and the player's own 1), at -2.4 attack speed (1.6 a
	// second). Long swords hit 3 harder and swing at 1.2 a second.
	private static final float ATTACK_DAMAGE_BASELINE = 6.0f;
	private static final float ATTACK_SPEED_BASELINE = -2.8f;
	private static final double EXTRA_REACH = 1.0;
	private static final float DURABILITY_SCALE = 1.5f;

	/** How much further a long sword's sweep reaches past the target, and from the player (blocks; vanilla 1 and 3). */
	public static final double SWEEP_INFLATE = 1.5;
	public static final double SWEEP_RANGE = 4.0;

	private LongSwords() {
	}

	/** A long sword of this material: the sword's tool rules and enchantability, heavier stats and the extra reach. */
	public static Item.Properties properties(ToolMaterial material) {
		return new Item.Properties().sword(material, ATTACK_DAMAGE_BASELINE, ATTACK_SPEED_BASELINE)
				.durability(Math.round(material.durability() * DURABILITY_SCALE))
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
								ATTACK_DAMAGE_BASELINE + material.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED_BASELINE,
								AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(MyMod.id("long_sword_reach"), EXTRA_REACH,
								AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
						.build());
	}

	public static boolean isLongSword(ItemStack stack) {
		return stack.is(LONG_SWORDS);
	}

	/** Whether the player's off hand is tied up holding a long sword with both hands. */
	public static boolean offHandBusy(Player player, InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND && isLongSword(player.getMainHandItem());
	}

	public static void initialize() {
		// Both sides run these, so the client doesn't predict a use the server refuses.
		UseItemCallback.EVENT.register((player, level, hand) ->
				offHandBusy(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				offHandBusy(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				offHandBusy(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
	}
}
