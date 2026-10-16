package com.jheinz.mymod.blueprint;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import com.jheinz.mymod.blueprint.Blueprint.Tier;

/**
 * {@code mymod:random_blueprint}: stamps a Blueprint with one blueprint picked at random, optionally only from one tier
 * ({@code "tier": "workshop"} or {@code "foundry"}). Used by chest and mob loot and by the banker's trade.
 */
public class RandomBlueprintFunction extends LootItemConditionalFunction {
	public static final MapCodec<RandomBlueprintFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
			.and(Tier.CODEC.optionalFieldOf("tier").forGetter(f -> f.tier))
			.apply(i, RandomBlueprintFunction::new));

	private final Optional<Tier> tier;

	private RandomBlueprintFunction(Optional<Holder<LootItemCondition>> condition, Optional<Tier> tier) {
		super(condition);
		this.tier = tier;
	}

	@Override
	public MapCodec<RandomBlueprintFunction> codec() {
		return MAP_CODEC;
	}

	@Override
	protected ItemStack run(ItemStack stack, LootContext context) {
		List<Blueprint> pool = Blueprints.ofTier(tier.orElse(null));
		if (!pool.isEmpty()) {
			stack.set(Blueprints.BLUEPRINT, pool.get(context.getRandom().nextInt(pool.size())).id());
		}
		return stack;
	}

	public static Builder<?> randomBlueprint(Tier tier) {
		return simpleBuilder(condition -> new RandomBlueprintFunction(condition, Optional.ofNullable(tier)));
	}
}
