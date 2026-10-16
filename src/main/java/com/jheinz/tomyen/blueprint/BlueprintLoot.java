package com.jheinz.tomyen.blueprint;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;

import com.jheinz.tomyen.MyMod;
import com.jheinz.tomyen.blueprint.Blueprint.Tier;

/**
 * Where blueprints turn up: a pool added to vanilla chests, the UFO's crash loot, the Fishing Tackle Box and the rogue
 * machines' drops. Each adds at most one blueprint. The banker sells them too (data/tomyen/villager_trade/banker/3).
 * <ul>
 * <li>Workshop paper where scavengers would leave it: mineshafts, toolsmiths, shipwreck supplies, tackle boxes.</li>
 * <li>Foundry paper where weapons are kept: weaponsmiths, the treasure of shipwrecks, crashed UFOs.</li>
 * <li>Either in dungeons, temples, ruined portals and the other village chests, and off machines.</li>
 * </ul>
 */
final class BlueprintLoot {
	/** A chance of one blueprint from a tier (null: any). */
	private record Drop(float chance, @Nullable Tier tier) {
	}

	private static final Map<ResourceKey<LootTable>, Drop> CHESTS = Map.ofEntries(
			Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, new Drop(0.25f, null)),
			Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, new Drop(0.20f, Tier.WORKSHOP)),
			Map.entry(BuiltInLootTables.SHIPWRECK_SUPPLY, new Drop(0.12f, Tier.WORKSHOP)),
			Map.entry(BuiltInLootTables.SHIPWRECK_TREASURE, new Drop(0.20f, Tier.FOUNDRY)),
			Map.entry(BuiltInLootTables.RUINED_PORTAL, new Drop(0.15f, null)),
			Map.entry(BuiltInLootTables.DESERT_PYRAMID, new Drop(0.20f, null)),
			Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, new Drop(0.30f, null)),
			Map.entry(BuiltInLootTables.VILLAGE_WEAPONSMITH, new Drop(0.30f, Tier.FOUNDRY)),
			Map.entry(BuiltInLootTables.VILLAGE_TOOLSMITH, new Drop(0.20f, Tier.WORKSHOP)),
			Map.entry(BuiltInLootTables.VILLAGE_ARMORER, new Drop(0.12f, null)),
			Map.entry(BuiltInLootTables.VILLAGE_CARTOGRAPHER, new Drop(0.15f, null)),
			// Crash loot (the UFO "dies" where it falls) and the tackle box.
			Map.entry(table("entities/ufo"), new Drop(0.50f, Tier.FOUNDRY)),
			Map.entry(table("gameplay/tackle_box"), new Drop(0.08f, Tier.WORKSHOP)));

	/** Machines carry them rarely, more with Looting, and only when a player brings them down. */
	private static final Map<ResourceKey<LootTable>, Drop> MACHINES = Map.of(
			table("entities/skullcrane"), new Drop(0.03f, null),
			table("entities/skullcrane_leader"), new Drop(0.15f, null),
			table("entities/cathode_walker"), new Drop(0.04f, null));

	private BlueprintLoot() {
	}

	private static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, MyMod.id(path));
	}

	static void initialize() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			Drop chest = CHESTS.get(key);
			if (chest != null) {
				table.withPool(LootPool.lootPool()
						.add(LootItem.lootTableItem(Blueprints.BLUEPRINT_ITEM).apply(RandomBlueprintFunction.randomBlueprint(chest.tier())))
						.when(LootItemRandomChanceCondition.randomChance(chest.chance())));
			}
			Drop machine = MACHINES.get(key);
			if (machine != null) {
				table.withPool(LootPool.lootPool()
						.add(LootItem.lootTableItem(Blueprints.BLUEPRINT_ITEM).apply(RandomBlueprintFunction.randomBlueprint(machine.tier())))
						.when(LootItemKilledByPlayerCondition.killedByPlayer())
						.when(LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
								registries.lookupOrThrow(Registries.ENCHANTMENT), machine.chance(), 0.01f)));
			}
		});
	}
}
