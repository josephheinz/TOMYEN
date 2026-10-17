package com.jheinz.tomyen.fusion.melee;

import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;

import java.util.Optional;

import com.jheinz.tomyen.MyMod;

/**
 * The melee fusions (batch 2): seven weapons fused at a fae pond or in the Fusion Reactor, each a real step up from
 * its inputs with its own trick. Their data files and art come from tools/gen_fusion_melee.py.
 * <ul>
 * <li>{@link SlideHammerItem} (trombone + iron long sword): charge a slam while the slide glides up the scale.
 * <li>{@link AntlerPikeItem} (tusk spear + moose antler): a long-reach spear; a sprinting jab becomes a boar's charge.
 * <li>{@link TuskaxeItem} (boar tusk + bloom steel axe): a sprinting chop fells a whole wild tree ({@link TreeFelling}).
 * <li>{@link MoonbladeItem} (moonscale koi + fae silver sword): stronger by night and full moon, swings loose crescents.
 * <li>{@link PufferbladeItem} (ember puffer + magmasteel sword): hits can puff the target up until it bursts in fire.
 * <li>{@link GnomeLanceItem} (garden gnome + tusk spear): a jousting lance that hits harder the faster you go.
 * <li>{@link LeviathanBladeItem} (swordfish sword + moonblade, reactor tier 2): the best sword in the mod.
 * </ul>
 */
public final class MeleeFusions {
	// --- Materials: each a tier past its inputs -----------------------------------------------------------------
	/** Iron long sword's brass-bound successor: diamond's bite, a long life. */
	public static final ToolMaterial SLIDE_BRASS = material(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1250, 6.0f, 3.0f, 14, "slide_hammer");
	/** The tusk spear (stone-ish) grown an antler crown: past diamond's point. */
	public static final ToolMaterial ANTLER = material(BlockTags.INCORRECT_FOR_IRON_TOOL, 900, 6.0f, 3.5f, 14, "antler_pike");
	/** Bloom steel (iron) with a tusk edge: diamond's mining and bite. */
	public static final ToolMaterial TUSK_STEEL = material(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1100, 8.5f, 3.0f, 14, "tuskaxe");
	/** Fae silver (iron, fast) with the moon in it: diamond's bite at fae silver's speed. */
	public static final ToolMaterial MOON_SILVER = material(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1000, 9.0f, 3.0f, 22, "moonblade");
	/** Magmasteel (Foundry) with a puffer's temper: half a heart past diamond. */
	public static final ToolMaterial EMBER = material(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1100, 8.0f, 3.5f, 20, "pufferblade");
	/** A tusk spear made a lance: diamond's point, and speed does the rest. */
	public static final ToolMaterial GNOME = material(BlockTags.INCORRECT_FOR_IRON_TOOL, 950, 6.0f, 3.0f, 15, "gnome_lance");
	/** Past the Exotic tier: star steel's durability and half again, two more damage than any sword before it. */
	public static final ToolMaterial LEVIATHAN = material(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3200, 10.0f, 6.0f, 20, "leviathan_blade");

	// --- Items --------------------------------------------------------------------------------------------------
	/** 12 damage at 1.0 a second (the iron long sword: 9 at 1.2); bashes shields like an axe. */
	public static final Item SLIDE_HAMMER = item("slide_hammer", SlideHammerItem::new, new Item.Properties()
			.sword(SLIDE_BRASS, 8.0f, -3.0f)
			.component(DataComponents.TOOL, new Tool(List.of(), 1.0f, 2, false))
			.component(DataComponents.WEAPON, new Weapon(1, 2.5f))
			.rarity(Rarity.RARE));
	/** A spear: 4.5 a jab at diamond-spear timing, a reach of 6 (vanilla spears 4.5), and a stronger charge. */
	public static final Item ANTLER_PIKE = item("antler_pike", AntlerPikeItem::new, new Item.Properties()
			.spear(ANTLER, 1.0f, 1.15f, 0.5f, 3.0f, 10.0f, 6.0f, 5.1f, 10.0f, 4.6f)
			.component(DataComponents.ATTACK_RANGE, new AttackRange(2.0f, 6.0f, 2.0f, 8.0f, 0.125f, 0.5f))
			.rarity(Rarity.RARE));
	/** 10 damage at 1.0 a second, diamond's mining (the bloom steel axe: 9 at 0.9, iron's). */
	public static final Item TUSKAXE = item("tuskaxe", TuskaxeItem::new, new Item.Properties()
			.axe(TUSK_STEEL, 6.0f, -3.0f).rarity(Rarity.RARE));
	/** 7 damage at 2.0 a second (the fae silver sword: 6), 9 by night and 11 under a full moon. */
	public static final Item MOONBLADE = item("moonblade", MoonbladeItem::new, new Item.Properties()
			.sword(MOON_SILVER, 3.0f, -2.0f).rarity(Rarity.RARE));
	/** 7.5 damage at 1.6 a second (the magmasteel sword: 6.5); sets what it hits alight. */
	public static final Item PUFFERBLADE = item("pufferblade", PufferbladeItem::new, new Item.Properties()
			.sword(EMBER, 3.0f, -2.4f).fireResistant().rarity(Rarity.RARE));
	/** A spear: 4 a jab and more for every block a second you're moving; unhorses riders. */
	public static final Item GNOME_LANCE = item("gnome_lance", GnomeLanceItem::new, new Item.Properties()
			.spear(GNOME, 1.0f, 1.3f, 0.5f, 3.0f, 9.0f, 6.0f, 5.1f, 9.0f, 4.6f)
			.component(DataComponents.PIERCING_WEAPON, new PiercingWeapon(true, true, Optional.of(SoundEvents.SPEAR_ATTACK),
					Optional.of(SoundEvents.SPEAR_HIT)))
			.component(DataComponents.ATTACK_RANGE, new AttackRange(2.0f, 5.0f, 2.0f, 7.0f, 0.25f, 0.5f))
			.rarity(Rarity.RARE));
	/** 10 damage at 1.6 a second, 14 when wet (the Dragonbone Long Sword: 11 at 1.2; star steel: 8 at 1.6). */
	public static final Item LEVIATHAN_BLADE = item("leviathan_blade", LeviathanBladeItem::new, new Item.Properties()
			.sword(LEVIATHAN, 3.0f, -2.4f).fireResistant().rarity(Rarity.EPIC));

	private MeleeFusions() {
	}

	private static ToolMaterial material(TagKey<net.minecraft.world.level.block.Block> incorrect, int durability, float speed, float damage,
			int enchantability, String name) {
		return new ToolMaterial(incorrect, durability, speed, damage, enchantability, TagKey.create(Registries.ITEM, MyMod.id("repairs_" + name)));
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MyMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			CrescentWaves.tick(level);
			AntlerPikeItem.tick(level);
			TreeFelling.tick(level);
			PufferbladeItem.tick(level);
			GnomeLanceItem.tick(level);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CrescentWaves.clear();
			AntlerPikeItem.clear();
			TreeFelling.clear();
			PufferbladeItem.clear();
			GnomeLanceItem.clear();
			MeleeSwings.clear();
		});
		TreeFelling.initialize();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_SWORD, MOONBLADE, PUFFERBLADE, LEVIATHAN_BLADE);
			output.insertAfter(Items.NETHERITE_AXE, TUSKAXE);
			output.insertAfter(Items.NETHERITE_SPEAR, ANTLER_PIKE, GNOME_LANCE);
			output.insertAfter(Items.MACE, SLIDE_HAMMER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.insertAfter(Items.NETHERITE_AXE, TUSKAXE));
	}
}
