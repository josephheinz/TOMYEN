package com.jheinz.mymod.magmasteel;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import com.jheinz.mymod.starforge.InfusionEffects;

/**
 * Magmasteel tools smelt what they mine: every drop of a broken block that has a furnace recipe comes out as that
 * recipe's result (raw iron as iron ingots, sand as glass, cobblestone as stone), with the experience a furnace
 * would have given, a puff of flame and a hiss. Fortune multiplies the drops before they're smelted. Silk Touch
 * turns it off, so a magmasteel pickaxe can still take ore blocks whole. Tools and drills with the Star Forge's
 * Molten infusion do the same (see InfusionEffects).
 */
public final class AutoSmelt {
	private AutoSmelt() {
	}

	static void modifyDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (!context.hasParameter(LootContextParams.BLOCK_STATE) || drops.isEmpty()) {
			return;
		}
		ItemInstance tool = context.getOptional(LootContextParams.TOOL);
		if (tool == null || !Magmasteel.SET.minesWith(tool) && !InfusionEffects.autoSmelts(tool) || hasSilkTouch(tool)) {
			return;
		}
		ServerLevel level = context.getLevel();
		float experience = 0.0f;
		boolean smelted = false;
		for (int i = 0; i < drops.size(); i++) {
			ItemStack drop = drops.get(i);
			SingleRecipeInput input = new SingleRecipeInput(drop);
			Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
			if (recipe.isEmpty()) {
				continue;
			}
			ItemStack result = recipe.get().value().assemble(input);
			if (result.isEmpty()) {
				continue;
			}
			drops.set(i, result.copyWithCount(Math.min(result.getCount() * drop.getCount(), result.getMaxStackSize())));
			experience += recipe.get().value().experience() * drop.getCount();
			smelted = true;
		}
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		if (!smelted || origin == null) {
			return;
		}
		// Whole points as orbs, and what's left over as the chance of one more, as a furnace pays out.
		int points = Mth.floor(experience);
		if (context.getRandom().nextFloat() < experience - points) {
			points++;
		}
		if (points > 0) {
			ExperienceOrb.award(level, origin, points);
		}
		level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z, 6, 0.25, 0.25, 0.25, 0.01);
		level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + 0.2, origin.z, 3, 0.2, 0.2, 0.2, 0.01);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f,
				1.6f + context.getRandom().nextFloat() * 0.3f);
	}

	private static boolean hasSilkTouch(ItemInstance tool) {
		ItemEnchantments enchantments = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		return enchantments.keySet().stream().anyMatch(enchantment -> enchantment.is(Enchantments.SILK_TOUCH));
	}
}
